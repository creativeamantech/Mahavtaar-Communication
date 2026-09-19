package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.S2SEngine
import com.example.data.model.ConversationMessage
import com.example.data.model.LatencyMetrics
import com.example.data.model.S2SConfig
import com.example.data.model.S2SEvent
import com.example.data.model.S2SState
import com.example.data.model.SenderType
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
    val currentMetrics: LatencyMetrics = LatencyMetrics(
        sttLatencyMs = 175L,
        ttftMs = 110L,
        ttsLatencyMs = 145L,
        totalLatencyMs = 430L,
        targetMet = true
    ),
    val config: S2SConfig = S2SConfig(),
    val showSettingsSheet: Boolean = false,
    val errorMessage: String? = null
)

class S2SViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = S2SEngine(application)

    private val _uiState = MutableStateFlow(S2SUiState())
    val uiState: StateFlow<S2SUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            engine.initialize(_uiState.value.config)
            observeEngineEvents()
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
                    is S2SEvent.Error -> {
                        _uiState.update { it.copy(errorMessage = event.message) }
                    }
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(isPermissionGranted = granted) }
        if (granted) {
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
        if (!_uiState.value.isPermissionGranted) return
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
        engine.sendTextQuery(promptText)
    }

    fun clearConversation() {
        _uiState.update {
            it.copy(
                conversationMessages = emptyList(),
                currentUserTranscript = "",
                currentAssistantDelta = ""
            )
        }
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
