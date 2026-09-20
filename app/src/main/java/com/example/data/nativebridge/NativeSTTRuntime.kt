package com.example.data.nativebridge

import android.util.Log
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class NativeSTTRuntime : Closeable {

    companion object {
        private const val TAG = "NativeSTTRuntime"
        private val libraryLoaded = AtomicBoolean(false)

        init {
            try {
                System.loadLibrary("mahavtaar_native")
                libraryLoaded.set(true)
                Log.i(TAG, "Native library 'mahavtaar_native' loaded successfully for STT.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load 'mahavtaar_native' library: ${e.message}")
            }
        }

        fun isNativeLibraryLoaded(): Boolean = libraryLoaded.get()
    }

    private var nativeHandle: Long = 0L

    init {
        if (libraryLoaded.get()) {
            try {
                nativeHandle = nativeCreate()
                Log.i(TAG, "Created NativeSTTRuntime with handle: 0x${java.lang.Long.toHexString(nativeHandle)}")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create native STT runtime: ${e.message}")
            }
        }
    }

    fun loadModel(modelPath: String): Boolean {
        if (nativeHandle == 0L || !libraryLoaded.get()) {
            Log.e(TAG, "Cannot load model: native runtime handle is invalid")
            return false
        }
        return try {
            val success = nativeLoadModel(nativeHandle, modelPath)
            Log.i(TAG, "nativeLoadModel returned $success for path: $modelPath")
            success
        } catch (e: Throwable) {
            Log.e(TAG, "nativeLoadModel threw exception: ${e.message}", e)
            false
        }
    }

    fun transcribe(audioPcm: FloatArray, sampleRate: Int = 16000): String {
        if (nativeHandle == 0L || !libraryLoaded.get()) {
            return ""
        }
        return try {
            nativeTranscribe(nativeHandle, audioPcm, sampleRate) ?: ""
        } catch (e: Throwable) {
            Log.e(TAG, "nativeTranscribe error: ${e.message}")
            ""
        }
    }

    fun isLoaded(): Boolean {
        if (nativeHandle == 0L || !libraryLoaded.get()) return false
        return try {
            nativeIsLoaded(nativeHandle)
        } catch (e: Throwable) {
            false
        }
    }

    fun cancel() {
        if (nativeHandle != 0L && libraryLoaded.get()) {
            try {
                nativeCancel(nativeHandle)
            } catch (e: Throwable) {
                Log.e(TAG, "nativeCancel error: ${e.message}")
            }
        }
    }

    override fun close() {
        if (nativeHandle != 0L) {
            try {
                nativeRelease(nativeHandle)
            } catch (e: Throwable) {
                Log.e(TAG, "Error releasing native STT runtime: ${e.message}")
            }
            nativeHandle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeLoadModel(handle: Long, modelPath: String): Boolean
    private external fun nativeTranscribe(handle: Long, audioPcm: FloatArray, sampleRate: Int): String?
    private external fun nativeCancel(handle: Long)
    private external fun nativeIsLoaded(handle: Long): Boolean
    private external fun nativeRelease(handle: Long)
}
