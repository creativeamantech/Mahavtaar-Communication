package com.example.data.vad

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Real-time Voice Activity Detection (VAD) for detecting speech starts and silence pauses.
 */
class VoiceActivityDetector(
    private var sensitivity: Float = 0.55f,
    private var silenceHangoverMs: Long = 650L
) {
    sealed interface VadEvent {
        object SpeechStarted : VadEvent
        data class SpeechEnded(val durationMs: Long) : VadEvent
    }

    private val _vadEvents = MutableSharedFlow<VadEvent>(extraBufferCapacity = 16)
    val vadEvents: SharedFlow<VadEvent> = _vadEvents.asSharedFlow()

    @Volatile
    private var isSpeechActive = false
    private var speechStartTime = 0L
    private var lastSpeechTime = 0L

    // Sensitivity maps to RMS amplitude threshold:
    // sensitivity 0.0 -> threshold 0.15 (harder to trigger)
    // sensitivity 1.0 -> threshold 0.015 (very sensitive)
    private val threshold: Float
        get() = (0.12f - (sensitivity * 0.095f)).coerceAtLeast(0.012f)

    fun updateConfig(newSensitivity: Float, newSilenceHangoverMs: Long) {
        sensitivity = newSensitivity.coerceIn(0.1f, 0.95f)
        silenceHangoverMs = newSilenceHangoverMs.coerceIn(300L, 2000L)
    }

    fun processFrame(normalizedRms: Float, timestamp: Long = System.currentTimeMillis()) {
        val isVoiceFrame = normalizedRms >= threshold

        if (isVoiceFrame) {
            lastSpeechTime = timestamp
            if (!isSpeechActive) {
                isSpeechActive = true
                speechStartTime = timestamp
                _vadEvents.tryEmit(VadEvent.SpeechStarted)
            }
        } else {
            if (isSpeechActive) {
                val silenceDuration = timestamp - lastSpeechTime
                if (silenceDuration >= silenceHangoverMs) {
                    isSpeechActive = false
                    val speechDuration = lastSpeechTime - speechStartTime
                    _vadEvents.tryEmit(VadEvent.SpeechEnded(speechDuration))
                }
            }
        }
    }

    fun reset() {
        isSpeechActive = false
        speechStartTime = 0L
        lastSpeechTime = 0L
    }
}
