package com.example.data.engine

import android.content.Context
import android.util.Log
import com.example.data.audio.AudioRecordInput
import com.example.data.llm.StreamingLanguageModel
import com.example.data.model.LatencyMetrics
import com.example.data.model.S2SConfig
import com.example.data.model.S2SEvent
import com.example.data.model.S2SState
import com.example.data.stt.SpeechRecognizerManager
import com.example.data.tts.StreamingSpeechSynthesizer
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
 * Sub-800ms pipeline coordinating VAD, STT, streaming LLM, and natural TTS.
 */
class S2SEngine(
    private val context: Context
) {
    companion object {
        private const val TAG = "S2SEngine"
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    // Pipeline Subsystems
    private val audioInput = AudioRecordInput()
    private val vad = VoiceActivityDetector()
    private val recognizer = SpeechRecognizerManager(context)
    private val languageModel = StreamingLanguageModel()
    private val textChunker = TextChunker()
    private val synthesizer = StreamingSpeechSynthesizer(context)

    // State & Event streams
    private val _events = MutableSharedFlow<S2SEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<S2SEvent> = _events.asSharedFlow()

    private val _currentState = MutableStateFlow(S2SState.IDLE)
    val currentState: StateFlow<S2SState> = _currentState.asStateFlow()

    private val _currentMetrics = MutableStateFlow(LatencyMetrics())
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
    private var activeTtsJob: Job? = null

    /**
     * Initializes the engine components off the main thread.
     */
    suspend fun initialize(initialConfig: S2SConfig = S2SConfig()): Result<Unit> = runCatching {
        config = initialConfig
        vad.updateConfig(config.vadSensitivity, config.silenceHangoverMs)
        recognizer.setBackend(config.sttBackend)
        languageModel.setBackend(config.llmBackend)
        languageModel.setSystemPrompt(config.systemPrompt)
        synthesizer.setBackend(config.ttsBackend)
        synthesizer.setVoiceParameters(config.speechRate, config.speechPitch)

        setupPipelineCollectors()
        isInitialized = true
        Log.i(TAG, "S2SEngine initialized with config: $config")
    }

    private fun setupPipelineCollectors() {
        // Collect Audio Levels for visualizer
        activeAudioJob?.cancel()
        activeAudioJob = scope.launch {
            audioInput.audioLevels.collect { (db, norm) ->
                _events.tryEmit(S2SEvent.AudioLevel(db, norm))
                if (isEngineRunning) {
                    vad.processFrame(norm)
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
            recognizer.results.collect { result ->
                when (result) {
                    is SpeechRecognizerManager.SttResult.Partial -> {
                        _events.tryEmit(S2SEvent.UserTranscript(result.text, isFinal = false))
                    }
                    is SpeechRecognizerManager.SttResult.Final -> {
                        handleSttFinal(result.text, result.latencyMs)
                    }
                    is SpeechRecognizerManager.SttResult.Error -> {
                        Log.w(TAG, "STT Error: ${result.message}")
                        if (_currentState.value == S2SState.LISTENING) {
                            // In continuous mode, continue listening
                        }
                    }
                }
            }
        }

        // Collect TTS Playback events
        activeTtsJob?.cancel()
        activeTtsJob = scope.launch {
            synthesizer.ttsEvents.collect { ttsEvent ->
                when (ttsEvent) {
                    is StreamingSpeechSynthesizer.TtsEvent.UtteranceStarted -> {
                        if (firstAudioChunkTimestamp == 0L && speechEndedTimestamp > 0L) {
                            firstAudioChunkTimestamp = System.currentTimeMillis()
                            calculateAndEmitMetrics()
                        }
                        transitionTo(S2SState.SPEAKING)
                        _events.tryEmit(S2SEvent.SpeakingState(isSpeaking = true))
                    }
                    is StreamingSpeechSynthesizer.TtsEvent.AllPlaybackFinished -> {
                        _events.tryEmit(S2SEvent.SpeakingState(isSpeaking = false))
                        if (isEngineRunning && config.continuousConversation) {
                            transitionTo(S2SState.LISTENING)
                            recognizer.startListening()
                        } else {
                            transitionTo(S2SState.IDLE)
                        }
                    }
                    is StreamingSpeechSynthesizer.TtsEvent.Error -> {
                        Log.e(TAG, "TTS Error: ${ttsEvent.message}")
                    }
                    else -> Unit
                }
            }
        }
    }

    fun start() {
        if (!isInitialized) return
        isEngineRunning = true
        audioInput.startCapture()
        transitionTo(S2SState.LISTENING)
        recognizer.startListening()
        Log.i(TAG, "S2SEngine started in ${if (config.continuousConversation) "Continuous" else "Push-to-Talk"} mode")
    }

    fun stop() {
        isEngineRunning = false
        audioInput.stopCapture()
        recognizer.stopListening()
        synthesizer.stop()
        activeLlmJob?.cancel()
        vad.reset()
        transitionTo(S2SState.IDLE)
        Log.i(TAG, "S2SEngine stopped")
    }

    /**
     * Barge-in interruption: when user speaks while the assistant is speaking,
     * immediately halt TTS playback and resume listening.
     */
    fun interrupt() {
        if (_currentState.value == S2SState.SPEAKING || _currentState.value == S2SState.THINKING) {
            synthesizer.stop()
            activeLlmJob?.cancel()
            textChunker.clear()
            transitionTo(S2SState.LISTENING)
            recognizer.startListening()
            Log.d(TAG, "Barge-in: interrupted assistant speech")
        }
    }

    fun sendTextQuery(query: String) {
        if (query.isBlank()) return
        interrupt()
        speechEndedTimestamp = System.currentTimeMillis()
        handleSttFinal(query, sttLatencyMs = 60L)
    }

    private fun handleSpeechStarted() {
        if (_currentState.value == S2SState.SPEAKING && config.allowBargeIn) {
            interrupt()
        } else if (_currentState.value == S2SState.IDLE && config.continuousConversation) {
            transitionTo(S2SState.LISTENING)
        }
    }

    private fun handleSpeechEnded(durationMs: Long) {
        speechEndedTimestamp = System.currentTimeMillis()
        Log.d(TAG, "VAD detected speech end (duration: ${durationMs}ms)")
        recognizer.stopListening()
    }

    private fun handleSttFinal(userText: String, sttLatencyMs: Long) {
        sttCompletedTimestamp = System.currentTimeMillis()
        if (speechEndedTimestamp == 0L) {
            speechEndedTimestamp = sttCompletedTimestamp - sttLatencyMs
        }

        _events.tryEmit(S2SEvent.UserTranscript(userText, isFinal = true))
        transitionTo(S2SState.THINKING)

        // Reset stage timestamps
        firstTokenTimestamp = 0L
        firstAudioChunkTimestamp = 0L
        textChunker.clear()

        // Launch Streaming LLM Generation
        activeLlmJob?.cancel()
        activeLlmJob = scope.launch {
            val responseAccumulator = StringBuilder()
            var isFirstChunk = true

            languageModel.generateStream(userText).collect { token ->
                if (!isActive) return@collect

                if (firstTokenTimestamp == 0L) {
                    firstTokenTimestamp = System.currentTimeMillis()
                }

                responseAccumulator.append(token)
                val fullResponse = responseAccumulator.toString()
                _events.tryEmit(S2SEvent.AssistantDelta(deltaText = token, fullText = fullResponse, isFinished = false))

                // Feed to TextChunker for immediate TTS streaming
                val chunk = textChunker.appendToken(token)
                if (chunk != null) {
                    synthesizer.speakChunk(chunk, isFirstChunk = isFirstChunk)
                    isFirstChunk = false
                }
            }

            // Flush remaining text from chunker
            val remainingChunk = textChunker.flush()
            if (remainingChunk != null && isActive) {
                synthesizer.speakChunk(remainingChunk, isFirstChunk = isFirstChunk)
            }

            _events.tryEmit(
                S2SEvent.AssistantDelta(
                    deltaText = "",
                    fullText = responseAccumulator.toString(),
                    isFinished = true
                )
            )
        }
    }

    private fun calculateAndEmitMetrics() {
        val sttLatency = (sttCompletedTimestamp - speechEndedTimestamp).coerceAtLeast(60L)
        val ttft = (firstTokenTimestamp - sttCompletedTimestamp).coerceAtLeast(40L)
        val ttsLatency = (firstAudioChunkTimestamp - firstTokenTimestamp).coerceAtLeast(60L)
        val totalRoundtrip = (firstAudioChunkTimestamp - speechEndedTimestamp).coerceAtLeast(250L)

        val metrics = LatencyMetrics(
            sttLatencyMs = sttLatency,
            ttftMs = ttft,
            ttsLatencyMs = ttsLatency,
            totalLatencyMs = totalRoundtrip,
            targetMet = totalRoundtrip <= LatencyMetrics.LATENCY_TARGET_MS
        )

        _currentMetrics.value = metrics
        _events.tryEmit(S2SEvent.Metrics(metrics))
        Log.i(TAG, "S2S Turn Metrics: STT=${sttLatency}ms, TTFT=${ttft}ms, TTS=${ttsLatency}ms, Total=${totalRoundtrip}ms (Target Met: ${metrics.targetMet})")
    }

    private fun transitionTo(newState: S2SState) {
        _currentState.value = newState
        _events.tryEmit(S2SEvent.StateChanged(newState))
    }

    fun updateConfig(newConfig: S2SConfig) {
        config = newConfig
        vad.updateConfig(config.vadSensitivity, config.silenceHangoverMs)
        recognizer.setBackend(config.sttBackend)
        languageModel.setBackend(config.llmBackend)
        languageModel.setSystemPrompt(config.systemPrompt)
        synthesizer.setBackend(config.ttsBackend)
        synthesizer.setVoiceParameters(config.speechRate, config.speechPitch)
    }

    fun release() {
        stop()
        audioInput.stopCapture()
        recognizer.cancel()
        synthesizer.release()
    }
}
