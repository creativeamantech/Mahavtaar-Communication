package com.example.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * High-performance 16kHz PCM audio capture interface for Speech-to-Speech pipeline.
 */
class AudioRecordInput(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    companion object {
        private const val TAG = "AudioRecordInput"
        // 20ms chunk at 16kHz = 320 samples = 640 bytes
        const val FRAME_SIZE_SAMPLES = 320
        const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    data class AudioFrame(
        val pcmData: ShortArray,
        val rmsAmplitude: Float,
        val dbLevel: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _audioFrames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = 64)
    val audioFrames: SharedFlow<AudioFrame> = _audioFrames.asSharedFlow()

    private val _audioLevels = MutableSharedFlow<Pair<Float, Float>>(extraBufferCapacity = 64)
    val audioLevels: SharedFlow<Pair<Float, Float>> = _audioLevels.asSharedFlow()

    @Volatile
    private var isCapturing = false

    @SuppressLint("MissingPermission")
    fun startCapture(): Boolean {
        if (isCapturing) return true

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, FRAME_SIZE_BYTES * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isCapturing = true

            recordingJob = scope.launch {
                val buffer = ShortArray(FRAME_SIZE_SAMPLES)
                while (isActive && isCapturing) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readCount > 0) {
                        val frame = buffer.copyOf(readCount)
                        val rms = calculateRms(frame)
                        val db = if (rms > 1f) (20f * log10(rms.toDouble())).toFloat() else 0f
                        val normalized = (rms / 32768f).coerceIn(0f, 1f)

                        _audioLevels.tryEmit(Pair(db, normalized))
                        _audioFrames.tryEmit(
                            AudioFrame(
                                pcmData = frame,
                                rmsAmplitude = normalized,
                                dbLevel = db
                            )
                        )
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting audio recording", e)
            isCapturing = false
            return false
        }
    }

    fun stopCapture() {
        isCapturing = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audioRecord", e)
        }
        audioRecord = null
    }

    private fun calculateRms(samples: ShortArray): Float {
        var sumSquares = 0.0
        for (sample in samples) {
            sumSquares += (sample * sample).toDouble()
        }
        return sqrt(sumSquares / samples.size).toFloat()
    }
}
