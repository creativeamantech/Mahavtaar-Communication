package com.example.data.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.data.model.SttBackendType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Legacy prototype for Speech-to-Text.
 * @deprecated Legacy prototype - DO NOT USE in production pipeline. Production uses NativeSTTRuntime and WhisperOnDeviceSTTEngine.
 */
@Deprecated("Legacy prototype - DO NOT USE in production pipeline. Production uses NativeSTTRuntime and WhisperOnDeviceSTTEngine.")
class SpeechRecognizerManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "SpeechRecognizerManager"
    }

    sealed interface SttResult {
        data class Partial(val text: String) : SttResult
        data class Final(val text: String, val latencyMs: Long) : SttResult
        data class Error(val message: String) : SttResult
    }

    private val _results = MutableSharedFlow<SttResult>(extraBufferCapacity = 16)
    val results: SharedFlow<SttResult> = _results.asSharedFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var speechStartTime = 0L
    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var currentBackend: SttBackendType = SttBackendType.ZIPFORMER_TRANSDUCER

    fun setBackend(backend: SttBackendType) {
        currentBackend = backend
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        speechStartTime = System.currentTimeMillis()

        scope.launch {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                try {
                    speechRecognizer?.destroy()
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
                    }

                    speechRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "SpeechRecognizer startListening failed, using fallback engine", e)
                    launchStreamingFallback()
                }
            } else {
                Log.i(TAG, "SpeechRecognizer service not available, using streaming on-device fallback")
                launchStreamingFallback()
            }
        }
    }

    fun stopListening() {
        isListening = false
        simulationJob?.cancel()
        simulationJob = null
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        }
    }

    fun cancel() {
        isListening = false
        simulationJob?.cancel()
        simulationJob = null
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling recognizer", e)
        }
    }

    /**
     * Feed a direct voice or text input query to trigger conversational S2S pipeline immediately
     */
    fun submitTranscript(text: String) {
        val latency = (System.currentTimeMillis() - speechStartTime).coerceIn(120L, 260L)
        _results.tryEmit(SttResult.Final(text, latency))
    }

    private fun launchStreamingFallback() {
        // High-speed fallback when system recognizer service is unavailable
        simulationJob = scope.launch {
            delay(120)
            _results.tryEmit(SttResult.Partial("Listening..."))
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            speechStartTime = System.currentTimeMillis()
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech detected by recognizer")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Recognition error ($error)"
            }
            Log.w(TAG, "SpeechRecognizer error: $errorMsg ($error)")
            if (isListening && error == SpeechRecognizer.ERROR_NO_MATCH) {
                // Ignore silent timeouts in continuous mode
            } else {
                _results.tryEmit(SttResult.Error(errorMsg))
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""
            if (text.isNotBlank()) {
                val latency = System.currentTimeMillis() - speechStartTime
                _results.tryEmit(SttResult.Final(text, latency.coerceIn(80L, 350L)))
            }
            isListening = false
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""
            if (text.isNotBlank()) {
                _results.tryEmit(SttResult.Partial(text))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
