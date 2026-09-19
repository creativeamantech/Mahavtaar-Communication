package com.example.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log

/**
 * Clean audio abstraction separating audio capture/playback from the inference pipeline.
 * Designed for on-device mic/speaker, extensible for GSM/SIP RTP streams in future iterations.
 */
interface AudioTransport {
    fun startInput(): Boolean
    fun readInput(targetBuffer: ShortArray): Int
    fun stopInput()

    fun writeOutput(pcmAudio: ShortArray): Int
    fun stopOutput()
    fun release()
}

class LocalDeviceAudioTransport(
    private val sampleRate: Int = 16000
) : AudioTransport {

    companion object {
        private const val TAG = "LocalDeviceAudio"
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val minInputBufferSize: Int = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(sampleRate / 10)

    private val minOutputBufferSize: Int = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(sampleRate / 10)

    @SuppressLint("MissingPermission")
    override fun startInput(): Boolean {
        try {
            stopInput()
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minInputBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_COMMUNICATION is unavailable
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minInputBufferSize * 2
                )
            }

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                return true
            }
            Log.e(TAG, "AudioRecord failed to initialize")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioRecord", e)
            return false
        }
    }

    override fun readInput(targetBuffer: ShortArray): Int {
        val record = audioRecord ?: return -1
        return try {
            record.read(targetBuffer, 0, targetBuffer.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read audio input", e)
            -1
        }
    }

    override fun stopInput() {
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    override fun writeOutput(pcmAudio: ShortArray): Int {
        if (audioTrack == null) {
            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minOutputBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack?.play()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create AudioTrack", e)
                return -1
            }
        }

        val track = audioTrack ?: return -1
        return try {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
            track.write(pcmAudio, 0, pcmAudio.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to AudioTrack", e)
            -1
        }
    }

    override fun stopOutput() {
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                    flush()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }

    override fun release() {
        stopInput()
        stopOutput()
    }
}
