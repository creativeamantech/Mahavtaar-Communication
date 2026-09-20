package com.example.data.nativebridge

import android.util.Log
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class NativeTTSRuntime : Closeable {

    companion object {
        private const val TAG = "NativeTTSRuntime"
        private val libraryLoaded = AtomicBoolean(false)

        init {
            try {
                System.loadLibrary("mahavtaar_native")
                libraryLoaded.set(true)
                Log.i(TAG, "Native library 'mahavtaar_native' loaded successfully for TTS.")
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
                Log.i(TAG, "Created NativeTTSRuntime with handle: 0x${java.lang.Long.toHexString(nativeHandle)}")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create native TTS runtime: ${e.message}")
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

    fun synthesize(text: String, speed: Float = 1.0f): ShortArray {
        if (nativeHandle == 0L || !libraryLoaded.get()) {
            return ShortArray(0)
        }
        return try {
            nativeSynthesize(nativeHandle, text, speed) ?: ShortArray(0)
        } catch (e: Throwable) {
            Log.e(TAG, "nativeSynthesize error: ${e.message}")
            ShortArray(0)
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

    fun isLoaded(): Boolean {
        if (nativeHandle == 0L || !libraryLoaded.get()) return false
        return try {
            nativeIsLoaded(nativeHandle)
        } catch (e: Throwable) {
            false
        }
    }

    override fun close() {
        if (nativeHandle != 0L) {
            try {
                nativeRelease(nativeHandle)
            } catch (e: Throwable) {
                Log.e(TAG, "Error releasing native TTS runtime: ${e.message}")
            }
            nativeHandle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeLoadModel(handle: Long, modelPath: String): Boolean
    private external fun nativeSynthesize(handle: Long, text: String, speed: Float): ShortArray?
    private external fun nativeCancel(handle: Long)
    private external fun nativeIsLoaded(handle: Long): Boolean
    private external fun nativeRelease(handle: Long)
}
