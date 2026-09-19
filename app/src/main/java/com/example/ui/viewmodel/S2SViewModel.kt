package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.ModelDownloadManager
import com.example.data.engine.S2SEngine
import com.example.data.hardware.DeviceHardwareProfile
import com.example.data.hardware.HardwareDetector
import com.example.data.model.ConversationMessage
import com.example.data.model.DiagnosticsInfo
import com.example.data.model.LatencyMetrics
import com.example.data.model.ModelItem
import com.example.data.model.ModelStatusSummary
import com.example.data.model.ModelType
import com.example.data.model.S2SConfig
import com.example.data.model.S2SEvent
import com.example.data.model.S2SState
import com.example.data.model.SenderType
import com.example.data.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class S2SUiState(
    val engineState: S2SState = S2SState.IDLE,
    val isRecording: Boolean = false,
    val isSpeaking: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val audioAmplitude: Float = 0f,
    val audioDb: Float = 0f,
    val currentUserTranscript: String = "",
    val currentAssistantDelta: String = "",
    val conversationMessages: List<ConversationMessage> = emptyList(),
    val currentMetrics: LatencyMetrics = LatencyMetrics(isReal = false),
    val config: S2SConfig = S2SConfig(),
    val modelStatusSummary: ModelStatusSummary = ModelStatusSummary(),
    val models: Map<String, ModelItem> = emptyMap(),
    val storageUsedBytes: Long = 0L,
    val storageAvailableBytes: Long = 0L,
    val hardwareProfile: DeviceHardwareProfile? = null,
    val diagnosticsInfo: DiagnosticsInfo = DiagnosticsInfo(),
    val showSettingsSheet: Boolean = false,
    val showModelManagerSheet: Boolean = false,
    val showDiagnosticsSheet: Boolean = false,
    val errorMessage: String? = null
)

class S2SViewModel(application: Application) : AndroidViewModel(application) {

    private val storageManager = StorageManager(application)
    val downloadManager = ModelDownloadManager(application, storageManager)
    private val hardwareDetector = HardwareDetector(application)
    val engine = S2SEngine(application)

    private val _uiState = MutableStateFlow(S2SUiState())
    val uiState: StateFlow<S2SUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val hw = hardwareDetector.getHardwareProfile()
            _uiState.update {
                it.copy(
                    hardwareProfile = hw,
                    storageUsedBytes = storageManager.getUsedByModelsBytes(),
                    storageAvailableBytes = storageManager.getAvailableStorageBytes()
                )
            }

            engine.initialize(_uiState.value.config)
            observeEngineEvents()
            observeModelsState()
        }
    }

    private fun observeModelsState() {
        viewModelScope.launch {
            downloadManager.modelsState.collect { modelsMap ->
                _uiState.update {
                    it.copy(
                        models = modelsMap,
                        storageUsedBytes = storageManager.getUsedByModelsBytes(),
                        storageAvailableBytes = storageManager.getAvailableStorageBytes()
                    )
                }
            }
        }
    }

    private fun observeEngineEvents() {
        viewModelScope.launch {
            engine.events.collect { event ->
                when (event) {
                    is S2SEvent.StateChanged -> {
                        _uiState.update { it.copy(engineState = event.state) }
                    }
                    is S2SEvent.AudioLevel -> {
                        _uiState.update {
                            it.copy(
                                audioDb = event.dbLevel,
                                audioAmplitude = event.normalizedAmplitude
                            )
                        }
                    }
                    is S2SEvent.UserTranscript -> {
                        if (event.isFinal) {
                            val userMsg = ConversationMessage(
                                sender = SenderType.USER,
                                text = event.text,
                                timestamp = event.timestamp
                            )
                            _uiState.update {
                                it.copy(
                                    currentUserTranscript = "",
                                    conversationMessages = it.conversationMessages + userMsg
                                )
                            }
                        } else {
                            _uiState.update { it.copy(currentUserTranscript = event.text) }
                        }
                    }
                    is S2SEvent.AssistantDelta -> {
                        if (event.isFinished) {
                            val assistantMsg = ConversationMessage(
                                sender = SenderType.ASSISTANT,
                                text = event.fullText,
                                metrics = _uiState.value.currentMetrics
                            )
                            _uiState.update {
                                it.copy(
                                    currentAssistantDelta = "",
                                    conversationMessages = it.conversationMessages + assistantMsg
                                )
                            }
                        } else {
                            _uiState.update { it.copy(currentAssistantDelta = event.fullText) }
                        }
                    }
                    is S2SEvent.Metrics -> {
                        _uiState.update { it.copy(currentMetrics = event.metrics) }
                    }
                    is S2SEvent.SpeakingState -> {
                        _uiState.update { it.copy(isSpeaking = event.isSpeaking) }
                    }
                    is S2SEvent.ModelStatusChanged -> {
                        _uiState.update { it.copy(modelStatusSummary = event.summary) }
                    }
                    is S2SEvent.Error -> {
                        _uiState.update { it.copy(errorMessage = event.message) }
                    }
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(isPermissionGranted = granted) }
        if (granted && _uiState.value.modelStatusSummary.allLoaded) {
            startConversation()
        }
    }

    fun toggleConversation() {
        if (_uiState.value.isRecording) {
            stopConversation()
        } else {
            startConversation()
        }
    }

    fun startConversation() {
        if (!_uiState.value.isPermissionGranted) {
            _uiState.update { it.copy(errorMessage = "Microphone permission is required for voice conversation.") }
            return
        }

        if (!_uiState.value.modelStatusSummary.allLoaded) {
            _uiState.update {
                it.copy(
                    errorMessage = "Models not loaded. Please download & load STT, LLM, and TTS models in Model Manager.",
                    showModelManagerSheet = true
                )
            }
            return
        }

        _uiState.update { it.copy(isRecording = true, errorMessage = null) }
        engine.start()
    }

    fun stopConversation() {
        _uiState.update { it.copy(isRecording = false, currentUserTranscript = "", currentAssistantDelta = "") }
        engine.stop()
    }

    fun interrupt() {
        engine.interrupt()
    }

    fun sendQuickPrompt(promptText: String) {
        if (!_uiState.value.modelStatusSummary.allLoaded) {
            _uiState.update {
                it.copy(
                    errorMessage = "Models not loaded. Tap 'Model Manager' to load on-device models.",
                    showModelManagerSheet = true
                )
            }
            return
        }
        engine.sendTextQuery(promptText)
    }

    fun clearConversation() {
        engine.clearConversation()
        _uiState.update {
            it.copy(
                conversationMessages = emptyList(),
                currentUserTranscript = "",
                currentAssistantDelta = ""
            )
        }
    }

    // --- Model Management Actions ---

    fun loadModel(modelId: String) {
        val model = _uiState.value.models[modelId] ?: return
        val startTime = System.currentTimeMillis()

        val result = when (model.type) {
            ModelType.STT -> engine.loadSttModel(model)
            ModelType.LLM -> engine.loadLlmModel(model)
            ModelType.TTS, ModelType.VOICE -> engine.loadTtsVoice(model)
        }

        val elapsed = System.currentTimeMillis() - startTime
        if (result.isSuccess) {
            downloadManager.setModelLoaded(modelId, true, elapsed)
            _uiState.update { it.copy(modelStatusSummary = engine.getModelStatusSummary()) }
        } else {
            val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to load model $modelId"
            _uiState.update { it.copy(errorMessage = err) }
        }
    }

    fun unloadModel(modelId: String) {
        val model = _uiState.value.models[modelId] ?: return
        when (model.type) {
            ModelType.STT -> engine.unloadSttModel()
            ModelType.LLM -> engine.unloadLlmModel()
            ModelType.TTS, ModelType.VOICE -> engine.unloadTtsVoice()
        }
        downloadManager.setModelLoaded(modelId, false)
        _uiState.update { it.copy(modelStatusSummary = engine.getModelStatusSummary()) }
    }

    fun loadAllModels() {
        val models = _uiState.value.models
        val stt = models["stt_whisper_tiny_q8"] ?: models.values.find { it.type == ModelType.STT }
        val llm = models["llm_smollm_135m_q4"] ?: models.values.find { it.type == ModelType.LLM }
        val tts = models["tts_kokoro_82m"] ?: models.values.find { it.type == ModelType.TTS }

        if (stt == null || llm == null || tts == null) {
            _uiState.update { it.copy(errorMessage = "Required models not found in registry") }
            return
        }

        // Install bundled pack if not yet installed
        if (!storageManager.isModelInstalled(stt)) downloadManager.installBundledModel(stt.id)
        if (!storageManager.isModelInstalled(llm)) downloadManager.installBundledModel(llm.id)
        if (!storageManager.isModelInstalled(tts)) downloadManager.installBundledModel(tts.id)

        val updatedModels = downloadManager.modelsState.value
        val readyStt = updatedModels[stt.id] ?: stt
        val readyLlm = updatedModels[llm.id] ?: llm
        val readyTts = updatedModels[tts.id] ?: tts

        val res = engine.loadAllModels(readyStt, readyLlm, readyTts)
        if (res.isSuccess) {
            downloadManager.setModelLoaded(stt.id, true)
            downloadManager.setModelLoaded(llm.id, true)
            downloadManager.setModelLoaded(tts.id, true)
            _uiState.update {
                it.copy(
                    modelStatusSummary = engine.getModelStatusSummary(),
                    errorMessage = null
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to load models") }
        }
    }

    fun unloadAllModels() {
        engine.unloadAllModels()
        _uiState.value.models.keys.forEach { id ->
            downloadManager.setModelLoaded(id, false)
        }
        _uiState.update { it.copy(modelStatusSummary = engine.getModelStatusSummary()) }
    }

    fun startDownload(modelId: String) {
        downloadManager.startDownload(modelId)
    }

    fun pauseDownload(modelId: String) {
        downloadManager.pauseDownload(modelId)
    }

    fun cancelDownload(modelId: String) {
        downloadManager.cancelDownload(modelId)
    }

    fun deleteModel(modelId: String) {
        unloadModel(modelId)
        downloadManager.deleteModel(modelId)
        refreshStorage()
    }

    fun deleteAllModels() {
        unloadAllModels()
        storageManager.deleteAllModels()
        downloadManager.modelsState.value.keys.forEach { id ->
            downloadManager.deleteModel(id)
        }
        refreshStorage()
    }

    fun installBundledModel(modelId: String) {
        downloadManager.installBundledModel(modelId)
        refreshStorage()
    }

    fun installAllRecommendedModels() {
        downloadManager.installAllRecommendedModels()
        refreshStorage()
        loadAllModels()
    }

    private fun refreshStorage() {
        _uiState.update {
            it.copy(
                storageUsedBytes = storageManager.getUsedByModelsBytes(),
                storageAvailableBytes = storageManager.getAvailableStorageBytes()
            )
        }
    }

    fun openModelManager() {
        refreshStorage()
        _uiState.update { it.copy(showModelManagerSheet = true) }
    }

    fun closeModelManager() {
        _uiState.update { it.copy(showModelManagerSheet = false) }
    }

    fun openDiagnostics() {
        val diag = engine.getDiagnostics()
        _uiState.update { it.copy(diagnosticsInfo = diag, showDiagnosticsSheet = true) }
    }

    fun closeDiagnostics() {
        _uiState.update { it.copy(showDiagnosticsSheet = false) }
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettingsSheet = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettingsSheet = false) }
    }

    fun updateConfig(newConfig: S2SConfig) {
        _uiState.update { it.copy(config = newConfig) }
        engine.updateConfig(newConfig)
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
    }
}
