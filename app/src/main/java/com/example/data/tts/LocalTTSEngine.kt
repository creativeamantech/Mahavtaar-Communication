package com.example.data.tts

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.data.model.ModelItem
import com.example.data.nativebridge.NativeTTSRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Standard contract for on-device neural Text-to-Speech engines.
 */
interface LocalTTSEngine {
    fun initialize(context: Context): Result<Unit>
    fun loadVoice(voiceModel: ModelItem): Result<Unit>
    fun unloadVoice()
    fun isLoaded(): Boolean
    fun getLoadedVoiceInfo(): ModelItem?

    suspend fun synthesizeChunk(
        textChunk: String,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f,
        isFirstChunk: Boolean = false
    ): Boolean

    fun stop()
    fun release()

    fun getLastSynthesizeLatencyMs(): Long
    val sampleRate: Int
}

/**
 * Real on-device Native Neural TTS engine (Kokoro / Piper JNI runtime).
 * Synthesizes 16kHz 16-bit PCM speech directly via native neural vocoder (libmahavtaar_native.so)
 * and streams into Android AudioTrack with sub-millisecond barge-in interruption.
 */
class KokoroPiperNeuralTTSEngine(
    override val sampleRate: Int = 16000
) : LocalTTSEngine {

    companion object {
        private const val TAG = "KokoroPiperTTS"
    }

    private var context: Context? = null
    private var loadedVoice: ModelItem? = null
    private val isVoiceLoaded = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    private val isInterrupted = AtomicBoolean(false)

    private var nativeRuntime: NativeTTSRuntime? = null
    private var audioTrack: AudioTrack? = null
    private var lastSynthLatencyMs = 0L

    private val minBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(sampleRate / 10)

    override fun initialize(context: Context): Result<Unit> {
        this.context = context.applicationContext
        initAudioTrack()
        return Result.success(Unit)
    }

    private fun initAudioTrack() {
        try {
            audioTrack?.release()
            audioTrack = AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    override fun loadVoice(voiceModel: ModelItem): Result<Unit> {
        val path = voiceModel.localFilePath ?: return Result.failure(
            IllegalStateException("Voice model file not found. Model must be downloaded and verified first.")
        )
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            return Result.failure(
                IllegalStateException("Voice model binary does not exist on disk: $path")
            )
        }

        return try {
            val startTime = System.currentTimeMillis()

            // Release any previously loaded native runtime
            nativeRuntime?.close()
            nativeRuntime = null

            // Instantiate native C++ neural TTS runtime
            val runtime = NativeTTSRuntime()
            val loadSuccess = runtime.loadModel(file.absolutePath)

            if (!loadSuccess || !runtime.isLoaded()) {
                runtime.close()
                isVoiceLoaded.set(false)
                loadedVoice = null
                return Result.failure(
                    IllegalStateException("Native TTS runtime failed to parse and load voice model from ${file.absolutePath}")
                )
            }

            nativeRuntime = runtime
            loadedVoice = voiceModel
            isVoiceLoaded.set(true)

            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "Native Neural Voice ${voiceModel.name} loaded in ${duration}ms (Vocoder active)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load voice model ${voiceModel.name}", e)
            nativeRuntime?.close()
            nativeRuntime = null
            isVoiceLoaded.set(false)
            loadedVoice = null
            Result.failure(e)
        }
    }

    override fun unloadVoice() {
        isVoiceLoaded.set(false)
        loadedVoice = null
        stop()
        try {
            nativeRuntime?.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing native TTS runtime: ${e.message}")
        }
        nativeRuntime = null
        Log.i(TAG, "Native Neural Voice unloaded and native memory freed")
    }

    override fun isLoaded(): Boolean {
        val runtime = nativeRuntime
        return isVoiceLoaded.get() && runtime != null && runtime.isLoaded()
    }

    override fun getLoadedVoiceInfo(): ModelItem? = loadedVoice

    override suspend fun synthesizeChunk(
        textChunk: String,
        speechRate: Float,
        pitch: Float,
        isFirstChunk: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val runtime = nativeRuntime
        if (!isLoaded() || runtime == null) {
            Log.e(TAG, "Cannot synthesize: Native voice model is not loaded")
            return@withContext false
        }

        if (textChunk.isBlank()) return@withContext true

        isInterrupted.set(false)
        isPlaying.set(true)
        val startTime = System.currentTimeMillis()

        // Real native neural vocoder synthesis
        val pcmAudio = runtime.synthesize(textChunk, speechRate)

        if (isFirstChunk) {
            lastSynthLatencyMs = System.currentTimeMillis() - startTime
        }

        if (isInterrupted.get() || pcmAudio.isEmpty()) {
            isPlaying.set(false)
            return@withContext false
        }

        // Stream synthesized PCM buffer directly into low-latency AudioTrack
        val track = audioTrack ?: run {
            initAudioTrack()
            audioTrack
        }

        track?.let { t ->
            if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                t.play()
            }
            var offset = 0
            val chunkSize = 512
            while (offset < pcmAudio.size && !isInterrupted.get()) {
                val toWrite = minOf(chunkSize, pcmAudio.size - offset)
                t.write(pcmAudio, offset, toWrite)
                offset += toWrite
            }
        }

        isPlaying.set(false)
        !isInterrupted.get()
    }

    /**
     * Immediate barge-in stop: halts AudioTrack playback, flushes buffers,
     * and signals native neural vocoder to cancel synthesis immediately.
     */
    override fun stop() {
        isInterrupted.set(true)
        isPlaying.set(false)
        nativeRuntime?.cancel()
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                    flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack", e)
        }
    }

    override fun getLastSynthesizeLatencyMs(): Long = lastSynthLatencyMs

    override fun release() {
        unloadVoice()
        audioTrack?.release()
        audioTrack = null
        context = null
    }
}
