package com.example.data.nativebridge

import android.util.Log
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class NativeLLMRuntime : Closeable {

    fun interface NativeTokenCallback {
        fun onToken(token: String, tokenId: Int): Boolean
    }

    companion object {
        private const val TAG = "NativeLLMRuntime"
        private val libraryLoaded = AtomicBoolean(false)

        init {
            try {
                System.loadLibrary("mahavtaar_native")
                libraryLoaded.set(true)
                Log.i(TAG, "Native library 'mahavtaar_native' loaded successfully for LLM.")
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
                Log.i(TAG, "Created NativeLLMRuntime with handle: 0x${java.lang.Long.toHexString(nativeHandle)}")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create native LLM runtime: ${e.message}")
            }
        }
    }

    fun loadModel(modelPath: String, contextLength: Int = 2048, threads: Int = 4): Boolean {
        if (nativeHandle == 0L || !libraryLoaded.get()) {
            Log.e(TAG, "Cannot load model: native runtime handle is invalid")
            return false
        }
        return try {
            val success = nativeLoadModel(nativeHandle, modelPath, contextLength, threads)
            Log.i(TAG, "nativeLoadModel returned $success for path: $modelPath")
            success
        } catch (e: Throwable) {
            Log.e(TAG, "nativeLoadModel threw exception: ${e.message}", e)
            false
        }
    }

    fun generate(
        prompt: String,
        maxTokens: Int = 128,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        callback: NativeTokenCallback? = null
    ): String {
        if (nativeHandle == 0L || !libraryLoaded.get()) {
            return ""
        }
        return try {
            nativeGenerate(nativeHandle, prompt, maxTokens, temperature, topP, callback) ?: ""
        } catch (e: Throwable) {
            Log.e(TAG, "nativeGenerate error: ${e.message}")
            ""
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
                Log.e(TAG, "Error releasing native LLM runtime: ${e.message}")
            }
            nativeHandle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeLoadModel(handle: Long, modelPath: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        callback: NativeTokenCallback?
    ): String?
    private external fun nativeCancel(handle: Long)
    private external fun nativeIsLoaded(handle: Long): Boolean
    private external fun nativeRelease(handle: Long)
}
