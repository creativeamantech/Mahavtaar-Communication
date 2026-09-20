package com.example.data.stt

import android.content.Context
import android.util.Log
import com.example.data.model.ModelItem
import com.example.data.nativebridge.NativeSTTRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result representation for streaming and final speech recognition.
 */
sealed class LocalSttResult {
    data class Partial(val transcript: String, val latencyMs: Long) : LocalSttResult()
    data class Final(val transcript: String, val latencyMs: Long) : LocalSttResult()
    data class Error(val message: String) : LocalSttResult()
}

/**
 * Standard contract for on-device STT engines.
 */
interface LocalSTTEngine {
    fun initialize(context: Context): Result<Unit>
    fun loadModel(model: ModelItem): Result<Unit>
    fun unloadModel()
    fun isLoaded(): Boolean
    fun getLoadedModelInfo(): ModelItem?
    fun startStreaming()
    fun acceptAudio(pcm16: ShortArray)
    fun stopStreaming()
    fun transcribe(audioData: ShortArray): Result<String>
    val recognitionEvents: Flow<LocalSttResult>
    fun release()
}

/**
 * Real on-device Native Neural STT engine (Whisper / Zipformer JNI runtime).
 * Loads model weights into native C++ runtime (libmahavtaar_native.so),
 * executes Mel-spectrogram extraction and neural acoustic inference.
 */
class WhisperOnDeviceSTTEngine : LocalSTTEngine {

    companion object {
        private const val TAG = "WhisperSTTEngine"
        private const val SAMPLE_RATE = 16000
    }

    private var context: Context? = null
    private var loadedModel: ModelItem? = null
    private val isModelLoaded = AtomicBoolean(false)
    private val isStreaming = AtomicBoolean(false)

    private var nativeRuntime: NativeSTTRuntime? = null

    private val _events = MutableSharedFlow<LocalSttResult>(extraBufferCapacity = 64)
    override val recognitionEvents: Flow<LocalSttResult> = _events.asSharedFlow()

    private val audioBuffer = mutableListOf<Short>()
    private var streamStartTime = 0L

    override fun initialize(context: Context): Result<Unit> {
        this.context = context.applicationContext
        return Result.success(Unit)
    }

    override fun loadModel(model: ModelItem): Result<Unit> {
        val path = model.localFilePath ?: return Result.failure(
            IllegalStateException("Model file path not found. Model must be downloaded and verified first.")
        )
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            return Result.failure(
                IllegalStateException("Model binary file does not exist on disk: $path")
            )
        }

        return try {
            val startTime = System.currentTimeMillis()

            // Close existing native runtime if any
            nativeRuntime?.close()
            nativeRuntime = null

            // Create and initialize native C++ neural STT runtime
            val runtime = NativeSTTRuntime()
            val loadSuccess = runtime.loadModel(file.absolutePath)

            if (!loadSuccess || !runtime.isLoaded()) {
                runtime.close()
                isModelLoaded.set(false)
                loadedModel = null
                return Result.failure(
                    IllegalStateException("Native STT runtime failed to parse and initialize model from ${file.absolutePath}")
                )
            }

            nativeRuntime = runtime
            loadedModel = model
            isModelLoaded.set(true)

            val loadDuration = System.currentTimeMillis() - startTime
            Log.i(TAG, "Native STT Neural Model ${model.name} loaded in ${loadDuration}ms (Native Handle active)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load STT model ${model.name}", e)
            nativeRuntime?.close()
            nativeRuntime = null
            isModelLoaded.set(false)
            loadedModel = null
            Result.failure(e)
        }
    }

    override fun unloadModel() {
        isModelLoaded.set(false)
        loadedModel = null
        stopStreaming()
        try {
            nativeRuntime?.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing native STT runtime: ${e.message}")
        }
        nativeRuntime = null
        Log.i(TAG, "Native STT Model unloaded and native memory freed")
    }

    override fun isLoaded(): Boolean {
        val runtime = nativeRuntime
        return isModelLoaded.get() && runtime != null && runtime.isLoaded()
    }

    override fun getLoadedModelInfo(): ModelItem? = loadedModel

    override fun startStreaming() {
        if (!isLoaded()) {
            _events.tryEmit(LocalSttResult.Error("STT Neural Model is not loaded. Download & load a verified model first."))
            return
        }
        audioBuffer.clear()
        streamStartTime = System.currentTimeMillis()
        isStreaming.set(true)
    }

    override fun acceptAudio(pcm16: ShortArray) {
        if (!isStreaming.get() || !isLoaded()) return

        synchronized(audioBuffer) {
            pcm16.forEach { audioBuffer.add(it) }
        }

        // When 500ms of audio has accumulated, perform streaming neural evaluation
        if (audioBuffer.size >= SAMPLE_RATE / 2) {
            val audioSnapshot: ShortArray
            synchronized(audioBuffer) {
                audioSnapshot = audioBuffer.takeLast(SAMPLE_RATE).toShortArray()
            }
            val elapsed = System.currentTimeMillis() - streamStartTime
            val partial = runNativeTranscription(audioSnapshot)
            if (partial.isNotBlank()) {
                _events.tryEmit(LocalSttResult.Partial(partial, elapsed))
            }
        }
    }

    override fun stopStreaming() {
        if (!isStreaming.getAndSet(false)) return

        if (!isLoaded()) {
            _events.tryEmit(LocalSttResult.Error("STT Model is not loaded."))
            return
        }

        val elapsed = System.currentTimeMillis() - streamStartTime
        val fullAudio: ShortArray
        synchronized(audioBuffer) {
            fullAudio = audioBuffer.toShortArray()
            audioBuffer.clear()
        }

        if (fullAudio.size < SAMPLE_RATE / 4) { // less than 250ms of audio
            _events.tryEmit(LocalSttResult.Final("", elapsed))
            return
        }

        val finalTranscript = runNativeTranscription(fullAudio)
        _events.tryEmit(LocalSttResult.Final(finalTranscript, elapsed))
    }

    override fun transcribe(audioData: ShortArray): Result<String> {
        if (!isLoaded()) {
            return Result.failure(IllegalStateException("Native STT Neural Model is not loaded."))
        }
        val text = runNativeTranscription(audioData)
        return Result.success(text)
    }

    /**
     * Executes real neural transcription by converting PCM to normalized float samples
     * and passing directly to the native C++ inference engine.
     */
    private fun runNativeTranscription(audio: ShortArray): String {
        val runtime = nativeRuntime ?: return ""
        if (!runtime.isLoaded() || audio.isEmpty()) return ""

        val floatPcm = FloatArray(audio.size)
        for (i in audio.indices) {
            floatPcm[i] = audio[i] / 32768.0f
        }

        return runtime.transcribe(floatPcm, SAMPLE_RATE)
    }

    override fun release() {
        unloadModel()
        context = null
    }
}
