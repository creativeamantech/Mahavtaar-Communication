package com.example.data.engine

import android.content.Context
import android.util.Log
import com.example.data.audio.AudioRecordInput
import com.example.data.audio.AudioTransport
import com.example.data.audio.LocalDeviceAudioTransport
import com.example.data.hardware.HardwareDetector
import com.example.data.llm.GgufOnDeviceLLMEngine
import com.example.data.llm.LocalLLMEngine
import com.example.data.model.DiagnosticsInfo
import com.example.data.model.LatencyMetrics
import com.example.data.model.ModelItem
import com.example.data.model.ModelStatusSummary
import com.example.data.model.S2SConfig
import com.example.data.model.S2SEvent
import com.example.data.model.S2SState
import com.example.data.stt.LocalSTTEngine
import com.example.data.stt.LocalSttResult
import com.example.data.stt.WhisperOnDeviceSTTEngine
import com.example.data.tts.KokoroPiperNeuralTTSEngine
import com.example.data.tts.LocalTTSEngine
import com.example.data.vad.VoiceActivityDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 100% On-Device Real-Time Speech-to-Speech Engine for Mobile.
 * Sub-800ms pipeline coordinating VAD, Local STT, streaming Local LLM, and Local Neural TTS.
 */
class S2SEngine(
    private val context: Context,
    val sttEngine: LocalSTTEngine = WhisperOnDeviceSTTEngine(),
    val llmEngine: LocalLLMEngine = GgufOnDeviceLLMEngine(),
    val ttsEngine: LocalTTSEngine = KokoroPiperNeuralTTSEngine(),
    val audioTransport: AudioTransport = LocalDeviceAudioTransport()
) {
    companion object {
        private const val TAG = "S2SEngine"
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    // Pipeline Subsystems
    private val audioInput = AudioRecordInput()
    private val vad = VoiceActivityDetector()
    private val textChunker = TextChunker()
    private val hardwareDetector = HardwareDetector(context)

    // State & Event streams
    private val _events = MutableSharedFlow<S2SEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<S2SEvent> = _events.asSharedFlow()

    private val _currentState = MutableStateFlow(S2SState.IDLE)
    val currentState: StateFlow<S2SState> = _currentState.asStateFlow()

    private val _currentMetrics = MutableStateFlow(LatencyMetrics(isReal = false))
    val currentMetrics: StateFlow<LatencyMetrics> = _currentMetrics.asStateFlow()

    private var config = S2SConfig()
    private var isEngineRunning = false
    private var isInitialized = false

    // Timing measurements for sub-800ms tracking
    private var speechEndedTimestamp = 0L
    private var sttCompletedTimestamp = 0L
    private var firstTokenTimestamp = 0L
    private var firstAudioChunkTimestamp = 0L

    private var activeLlmJob: Job? = null
    private var activeVadJob: Job? = null
    private var activeAudioJob: Job? = null
    private var activeSttJob: Job? = null

    // Conversation history cache for LLM context
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    /**
     * Initializes the engine and subsystems.
     */
    suspend fun initialize(initialConfig: S2SConfig = S2SConfig()): Result<Unit> = runCatching {
        config = initialConfig
        vad.updateConfig(config.vadSensitivity, config.silenceHangoverMs)

        sttEngine.initialize(context)
        llmEngine.initialize(context)
        ttsEngine.initialize(context)

        setupPipelineCollectors()
        isInitialized = true
        emitModelStatus()
        Log.i(TAG, "S2SEngine initialized successfully")
    }

    private fun setupPipelineCollectors() {
        // Collect Audio Levels for visualizer and feed to VAD + STT
        activeAudioJob?.cancel()
        activeAudioJob = scope.launch {
            audioInput.audioFrames.collect { frame ->
                _events.tryEmit(S2SEvent.AudioLevel(frame.dbLevel, frame.rmsAmplitude))
                if (isEngineRunning) {
                    vad.processFrame(frame.rmsAmplitude)
                    if (sttEngine.isLoaded()) {
                        sttEngine.acceptAudio(frame.pcmData)
                    }
                }
            }
        }

        // Collect VAD events
        activeVadJob?.cancel()
        activeVadJob = scope.launch {
            vad.vadEvents.collect { vadEvent ->
                when (vadEvent) {
                    is VoiceActivityDetector.VadEvent.SpeechStarted -> {
                        handleSpeechStarted()
                    }
                    is VoiceActivityDetector.VadEvent.SpeechEnded -> {
                        handleSpeechEnded(vadEvent.durationMs)
                    }
                }
            }
        }

        // Collect STT Recognition results
        activeSttJob?.cancel()
        activeSttJob = scope.launch {
            sttEngine.recognitionEvents.collect { result ->
                when (result) {
                    is LocalSttResult.Partial -> {
                        _events.tryEmit(S2SEvent.UserTranscript(result.transcript, isFinal = false))
                    }
                    is LocalSttResult.Final -> {
                        if (result.transcript.isNotBlank()) {
                            handleSttFinal(result.transcript, result.latencyMs)
                        } else if (_currentState.value == S2SState.LISTENING && config.continuousConversation) {
                            // Empty transcript on silence; keep listening in continuous mode
                        }
                    }
                    is LocalSttResult.Error -> {
                        Log.w(TAG, "Local STT Error: ${result.message}")
                        _events.tryEmit(S2SEvent.Error(result.message))
                    }
                }
            }
        }
    }

    fun start() {
        if (!isInitialized) return
        val status = getModelStatusSummary()
        if (!status.allLoaded) {
            val missing = buildList {
                if (!status.sttLoaded) add("STT")
                if (!status.llmLoaded) add("LLM")
                if (!status.ttsLoaded) add("TTS")
            }.joinToString(", ")
            _events.tryEmit(S2SEvent.Error("Models not loaded: $missing. Please load models in Model Manager."))
            return
        }

        isEngineRunning = true
        audioInput.startCapture()
        transitionTo(S2SState.LISTENING)
        sttEngine.startStreaming()
        Log.i(TAG, "S2SEngine started with all local models active")
    }

    fun stop() {
        isEngineRunning = false
        audioInput.stopCapture()
        sttEngine.stopStreaming()
        ttsEngine.stop()
        activeLlmJob?.cancel()
        vad.reset()
        transitionTo(S2SState.IDLE)
        Log.i(TAG, "S2SEngine stopped")
    }

    /**
     * Barge-in interruption: halts TTS playback immediately, cancels LLM generation,
     * flushes buffers, and transitions back to listening.
     */
    fun interrupt() {
        if (_currentState.value == S2SState.SPEAKING || _currentState.value == S2SState.THINKING) {
            ttsEngine.stop()
            llmEngine.cancelGeneration()
            activeLlmJob?.cancel()
            textChunker.clear()
            _events.tryEmit(S2SEvent.SpeakingState(isSpeaking = false))
            transitionTo(S2SState.LISTENING)
            sttEngine.startStreaming()
            Log.d(TAG, "Barge-in triggered: assistant speech halted instantly")
        }
    }

    fun sendTextQuery(query: String) {
        if (query.isBlank()) return
        val status = getModelStatusSummary()
        if (!status.allLoaded) {
            _events.tryEmit(S2SEvent.Error("Models not loaded. Please download & load models in Model Manager."))
            return
        }

        interrupt()
        speechEndedTimestamp = System.currentTimeMillis()
        handleSttFinal(query, sttLatencyMs = 40L)
    }

    private fun handleSpeechStarted() {
        if (_currentState.value == S2SState.SPEAKING && config.allowBargeIn) {
            interrupt()
        } else if (_currentState.value == S2SState.IDLE && config.continuousConversation && getModelStatusSummary().allLoaded) {
            start()
        } else if (_currentState.value == S2SState.LISTENING) {
            sttEngine.startStreaming()
        }
    }

    private fun handleSpeechEnded(durationMs: Long) {
        speechEndedTimestamp = System.currentTimeMillis()
        Log.d(TAG, "VAD speech ended (duration: ${durationMs}ms)")
        sttEngine.stopStreaming()
    }

    private fun handleSttFinal(userText: String, sttLatencyMs: Long) {
        sttCompletedTimestamp = System.currentTimeMillis()
        if (speechEndedTimestamp == 0L) {
            speechEndedTimestamp = sttCompletedTimestamp - sttLatencyMs
        }

        _events.tryEmit(S2SEvent.UserTranscript(userText, isFinal = true))
        transitionTo(S2SState.THINKING)

        // Reset stage timestamps for accurate latency computation
        firstTokenTimestamp = 0L
        firstAudioChunkTimestamp = 0L
        textChunker.clear()

        // Launch Streaming Local LLM Generation & Pipelined Neural TTS
        activeLlmJob?.cancel()
        activeLlmJob = scope.launch {
            val responseAccumulator = StringBuilder()
            var isFirstChunk = true

            try {
                llmEngine.streamTokens(
                    userMessage = userText,
                    systemPrompt = config.systemPrompt,
                    history = conversationHistory
                ).collect { token ->
                    if (!isActive) return@collect

                    if (firstTokenTimestamp == 0L) {
                        firstTokenTimestamp = System.currentTimeMillis()
                    }

                    responseAccumulator.append(token)
                    val fullResponse = responseAccumulator.toString()
                    _events.tryEmit(
                        S2SEvent.AssistantDelta(
                            deltaText = token,
                            fullText = fullResponse,
                            isFinished = false
                        )
                    )

                    // Feed to TextChunker for immediate clause-based TTS streaming
                    val chunk = textChunker.appendToken(token)
                    if (chunk != null) {
                        if (isFirstChunk) {
                            transitionTo(S2SState.SPEAKING)
                            _events.tryEmit(S2SEvent.SpeakingState(isSpeaking = true))
                        }
                        val success = ttsEngine.synthesizeChunk(
                            textChunk = chunk,
                            speechRate = config.speechRate,
                            pitch = config.speechPitch,
                            isFirstChunk = isFirstChunk
                        )
                        if (isFirstChunk && success) {
                            firstAudioChunkTimestamp = System.currentTimeMillis()
                            calculateAndEmitMetrics()
                            isFirstChunk = false
                        }
                    }
                }

                // Flush remaining text from chunker
                val remainingChunk = textChunker.flush()
                if (remainingChunk != null && isActive) {
                    if (isFirstChunk) {
                        transitionTo(S2SState.SPEAKING)
                        _events.tryEmit(S2SEvent.SpeakingState(isSpeaking = true))
                    }
                    val success = ttsEngine.synthesizeChunk(
                        textChunk = remainingChunk,
                        speechRate = config.speechRate,
                        pitch = config.speechPitch,
                        isFirstChunk = isFirstChunk
                    )
                    if (isFirstChunk && success) {
                        firstAudioChunkTimestamp = System.currentTimeMillis()
                        calculateAndEmitMetrics()
                    }
                }

                val finalAssistantResponse = responseAccumulator.toString()
                _events.tryEmit(
                    S2SEvent.AssistantDelta(
                        deltaText = "",
                        fullText = finalAssistantResponse,
                        isFinished = true
                    )
                )

                // Append to conversation history cache
                conversationHistory.add(Pair(userText, finalAssistantResponse))
                if (conversationHistory.size > 8) {
                    conversationHistory.removeAt(0)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Inference loop error", e)
                _events.tryEmit(S2SEvent.Error(e.localizedMessage ?: "Inference failure"))
            } finally {
                _events.tryEmit(S2SEvent.SpeakingState(isSpeaking = false))
                if (isEngineRunning && config.continuousConversation) {
                    transitionTo(S2SState.LISTENING)
                    sttEngine.startStreaming()
                } else {
                    transitionTo(S2SState.IDLE)
                }
            }
        }
    }

    /**
     * Computes genuine latency metrics from actual system timestamps.
     */
    private fun calculateAndEmitMetrics() {
        val sttLatency = if (sttCompletedTimestamp >= speechEndedTimestamp) {
            sttCompletedTimestamp - speechEndedTimestamp
        } else 0L

        val ttft = if (firstTokenTimestamp >= sttCompletedTimestamp) {
            firstTokenTimestamp - sttCompletedTimestamp
        } else 0L

        val ttsLatency = if (firstAudioChunkTimestamp >= firstTokenTimestamp) {
            firstAudioChunkTimestamp - firstTokenTimestamp
        } else 0L

        val totalRoundtrip = if (firstAudioChunkTimestamp >= speechEndedTimestamp) {
            firstAudioChunkTimestamp - speechEndedTimestamp
        } else (sttLatency + ttft + ttsLatency)

        val metrics = LatencyMetrics(
            sttLatencyMs = sttLatency,
            ttftMs = ttft,
            ttsLatencyMs = ttsLatency,
            totalLatencyMs = totalRoundtrip,
            targetMet = totalRoundtrip in 1L..LatencyMetrics.LATENCY_TARGET_MS,
            isReal = true
        )

        _currentMetrics.value = metrics
        _events.tryEmit(S2SEvent.Metrics(metrics))
        Log.i(TAG, "REAL Latency: STT=${sttLatency}ms, TTFT=${ttft}ms, TTS=${ttsLatency}ms, Total=${totalRoundtrip}ms")
    }

    fun loadSttModel(model: ModelItem): Result<Unit> {
        val res = sttEngine.loadModel(model)
        emitModelStatus()
        return res
    }

    fun unloadSttModel() {
        sttEngine.unloadModel()
        emitModelStatus()
    }

    fun loadLlmModel(model: ModelItem): Result<Unit> {
        val res = llmEngine.loadModel(model)
        emitModelStatus()
        return res
    }

    fun unloadLlmModel() {
        llmEngine.unloadModel()
        emitModelStatus()
    }

    fun loadTtsVoice(model: ModelItem): Result<Unit> {
        val res = ttsEngine.loadVoice(model)
        emitModelStatus()
        return res
    }

    fun unloadTtsVoice() {
        ttsEngine.unloadVoice()
        emitModelStatus()
    }

    fun loadAllModels(stt: ModelItem, llm: ModelItem, tts: ModelItem): Result<Unit> {
        val sttRes = loadSttModel(stt)
        if (sttRes.isFailure) return sttRes

        val llmRes = loadLlmModel(llm)
        if (llmRes.isFailure) return llmRes

        val ttsRes = loadTtsVoice(tts)
        if (ttsRes.isFailure) return ttsRes

        return Result.success(Unit)
    }

    fun unloadAllModels() {
        unloadSttModel()
        unloadLlmModel()
        unloadTtsVoice()
    }

    fun getModelStatusSummary(): ModelStatusSummary {
        return ModelStatusSummary(
            sttLoaded = sttEngine.isLoaded(),
            sttModelName = sttEngine.getLoadedModelInfo()?.name ?: "None",
            llmLoaded = llmEngine.isLoaded(),
            llmModelName = llmEngine.getLoadedModelInfo()?.name ?: "None",
            ttsLoaded = ttsEngine.isLoaded(),
            ttsModelName = ttsEngine.getLoadedVoiceInfo()?.name ?: "None"
        )
    }

    private fun emitModelStatus() {
        val summary = getModelStatusSummary()
        _events.tryEmit(S2SEvent.ModelStatusChanged(summary))
    }

    fun clearConversation() {
        conversationHistory.clear()
        textChunker.clear()
    }

    fun getDiagnostics(): DiagnosticsInfo {
        val profile = hardwareDetector.getHardwareProfile()
        val sttInfo = sttEngine.getLoadedModelInfo()
        val llmInfo = llmEngine.getLoadedModelInfo()
        val ttsInfo = ttsEngine.getLoadedVoiceInfo()

        return DiagnosticsInfo(
            sttModelName = sttInfo?.name ?: "Not Loaded",
            sttStatus = if (sttEngine.isLoaded()) "Active (${sttInfo?.quantization ?: "Local"})" else "Not Loaded",
            sttLoadTimeMs = sttInfo?.loadTimeMs ?: 0L,
            llmModelName = llmInfo?.name ?: "Not Loaded",
            llmStatus = if (llmEngine.isLoaded()) "Active (${llmInfo?.quantization ?: "GGUF"})" else "Not Loaded",
            llmLoadTimeMs = llmInfo?.loadTimeMs ?: 0L,
            llmTtftMs = llmEngine.getLastTtftMs(),
            llmTokensPerSec = llmEngine.getLastTokensPerSec(),
            ttsModelName = ttsInfo?.name ?: "Not Loaded",
            ttsStatus = if (ttsEngine.isLoaded()) "Active (${ttsEngine.sampleRate}Hz)" else "Not Loaded",
            ttsLoadTimeMs = ttsInfo?.loadTimeMs ?: 0L,
            ttsSampleRate = ttsEngine.sampleRate,
            audioTransport = "Local AudioRecord/AudioTrack (16kHz PCM mono)",
            totalDeviceRamMb = profile.totalRamMb,
            availDeviceRamMb = profile.availableRamMb,
            cpuArch = profile.cpuArch,
            availableStorageMb = profile.availableStorageMb
        )
    }

    private fun transitionTo(newState: S2SState) {
        _currentState.value = newState
        _events.tryEmit(S2SEvent.StateChanged(newState))
    }

    fun updateConfig(newConfig: S2SConfig) {
        config = newConfig
        vad.updateConfig(config.vadSensitivity, config.silenceHangoverMs)
    }

    fun release() {
        stop()
        audioInput.stopCapture()
        sttEngine.release()
        llmEngine.release()
        ttsEngine.release()
        audioTransport.release()
    }
}
