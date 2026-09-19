package com.example.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.TtsBackendType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale

/**
 * Low-latency streaming Text-to-Speech synthesizer.
 * Supports queued chunk playback and instant barge-in stop.
 */
class StreamingSpeechSynthesizer(
    private val context: Context,
    private var onInitListener: (() -> Unit)? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "StreamingSpeechSynthesizer"
    }

    sealed interface TtsEvent {
        data class UtteranceStarted(val utteranceId: String) : TtsEvent
        data class UtteranceCompleted(val utteranceId: String) : TtsEvent
        data class AllPlaybackFinished(val lastUtteranceId: String) : TtsEvent
        data class Error(val message: String) : TtsEvent
    }

    private val _ttsEvents = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 16)
    val ttsEvents: SharedFlow<TtsEvent> = _ttsEvents.asSharedFlow()

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var activeUtterances = 0
    private var utteranceCounter = 0

    private var backendType: TtsBackendType = TtsBackendType.KOKORO_82M
    private var speechRate = 1.05f
    private var speechPitch = 1.0f

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(speechPitch)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS onStart: $utteranceId")
                    utteranceId?.let {
                        _ttsEvents.tryEmit(TtsEvent.UtteranceStarted(it))
                    }
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS onDone: $utteranceId")
                    activeUtterances--
                    utteranceId?.let {
                        _ttsEvents.tryEmit(TtsEvent.UtteranceCompleted(it))
                        if (activeUtterances <= 0) {
                            activeUtterances = 0
                            _ttsEvents.tryEmit(TtsEvent.AllPlaybackFinished(it))
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    activeUtterances--
                    Log.w(TAG, "TTS onError: $utteranceId")
                    _ttsEvents.tryEmit(TtsEvent.Error("TTS playback error for utterance $utteranceId"))
                }
            })
            isInitialized = true
            onInitListener?.invoke()
            Log.i(TAG, "TextToSpeech initialized successfully")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status $status")
        }
    }

    fun setBackend(backend: TtsBackendType) {
        backendType = backend
        // Adjust speech profile based on selected model
        when (backend) {
            TtsBackendType.KOKORO_82M -> {
                speechRate = 1.05f
                speechPitch = 1.02f
            }
            TtsBackendType.PIPER_VITS -> {
                speechRate = 1.0f
                speechPitch = 0.98f
            }
            TtsBackendType.ANDROID_NEURAL -> {
                speechRate = 1.1f
                speechPitch = 1.0f
            }
        }
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(speechPitch)
    }

    fun setVoiceParameters(rate: Float, pitch: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        speechPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(speechPitch)
    }

    /**
     * Enqueue a text chunk for speech synthesis.
     * @param isFirstChunk if true, uses QUEUE_FLUSH to stop previous speech; otherwise QUEUE_ADD to stream.
     */
    fun speakChunk(text: String, isFirstChunk: Boolean = false): Boolean {
        if (!isInitialized || text.isBlank()) return false

        utteranceCounter++
        val utteranceId = "s2s_utt_$utteranceCounter"
        val queueMode = if (isFirstChunk) {
            activeUtterances = 1
            TextToSpeech.QUEUE_FLUSH
        } else {
            activeUtterances++
            TextToSpeech.QUEUE_ADD
        }

        val result = tts?.speak(text, queueMode, null, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    /**
     * Stops audio playback immediately (for barge-in when the user speaks).
     */
    fun stop() {
        activeUtterances = 0
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
        tts = null
        isInitialized = false
    }
}
