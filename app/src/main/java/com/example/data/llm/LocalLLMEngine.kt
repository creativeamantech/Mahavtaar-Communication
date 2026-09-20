package com.example.data.llm

import android.content.Context
import android.util.Log
import com.example.data.model.ModelItem
import com.example.data.nativebridge.NativeLLMRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Standard contract for on-device LLM inference engines.
 */
interface LocalLLMEngine {
    fun initialize(context: Context): Result<Unit>
    fun loadModel(model: ModelItem): Result<Unit>
    fun unloadModel()
    fun isLoaded(): Boolean
    fun getLoadedModelInfo(): ModelItem?

    fun streamTokens(
        userMessage: String,
        systemPrompt: String = "You are Mahavtaar, a responsive real-time voice assistant.",
        history: List<Pair<String, String>> = emptyList(),
        temperature: Float = 0.7f,
        maxTokens: Int = 128
    ): Flow<String>

    fun cancelGeneration()
    fun release()

    // Real measured metrics from current/latest turn
    fun getLastTtftMs(): Long
    fun getLastTokensPerSec(): Float
}

/**
 * Real on-device Native GGUF LLM engine (ARM64 / x86_64 JNI runtime).
 * Loads GGUF model binaries into native memory, allocates context and KV cache,
 * and performs real transformer autoregressive token decoding.
 */
class GgufOnDeviceLLMEngine : LocalLLMEngine {

    companion object {
        private const val TAG = "GgufOnDeviceLLM"
    }

    private var context: Context? = null
    private var loadedModel: ModelItem? = null
    private val isModelLoaded = AtomicBoolean(false)
    private val isCancelling = AtomicBoolean(false)

    private var nativeRuntime: NativeLLMRuntime? = null

    private var lastTtftMs = 0L
    private var lastTokensPerSec = 0f

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
                IllegalStateException("GGUF model binary does not exist on disk: $path")
            )
        }

        return try {
            val startTime = System.currentTimeMillis()

            // Release any previously loaded native runtime
            nativeRuntime?.close()
            nativeRuntime = null

            // Instantiate native C++ GGUF transformer runtime
            val runtime = NativeLLMRuntime()
            val loadSuccess = runtime.loadModel(file.absolutePath, contextLength = 2048, threads = 4)

            if (!loadSuccess || !runtime.isLoaded()) {
                runtime.close()
                isModelLoaded.set(false)
                loadedModel = null
                return Result.failure(
                    IllegalStateException("Native GGUF runtime failed to parse and initialize model from ${file.absolutePath}")
                )
            }

            nativeRuntime = runtime
            loadedModel = model
            isModelLoaded.set(true)

            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "Native GGUF Model ${model.name} loaded in ${duration}ms (Context active)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load GGUF model ${model.name}", e)
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
        cancelGeneration()
        try {
            nativeRuntime?.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing native LLM runtime: ${e.message}")
        }
        nativeRuntime = null
        Log.i(TAG, "Native GGUF Model unloaded and native context released")
    }

    override fun isLoaded(): Boolean {
        val runtime = nativeRuntime
        return isModelLoaded.get() && runtime != null && runtime.isLoaded()
    }

    override fun getLoadedModelInfo(): ModelItem? = loadedModel

    override fun streamTokens(
        userMessage: String,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = callbackFlow {
        val runtime = nativeRuntime
        if (!isLoaded() || runtime == null) {
            close(IllegalStateException("Native LLM Runtime is not loaded. Please download & load a GGUF model."))
            return@callbackFlow
        }

        isCancelling.set(false)
        val requestStartTime = System.currentTimeMillis()
        var firstTokenEmitted = false
        var tokenCount = 0

        // Format prompt using standard ChatML notation
        val prompt = buildChatPrompt(userMessage, systemPrompt, history)
        Log.d(TAG, "Streaming native GGUF tokens for prompt length: ${prompt.length}")

        try {
            runtime.generate(
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = 0.9f,
                callback = { token, _ ->
                    if (isCancelling.get()) {
                        false // Stop native generation
                    } else {
                        if (!firstTokenEmitted) {
                            lastTtftMs = System.currentTimeMillis() - requestStartTime
                            firstTokenEmitted = true
                        }
                        tokenCount++
                        trySend(token)
                        true // Continue native generation
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Native GGUF generation error: ${e.message}", e)
        }

        val totalTimeMs = System.currentTimeMillis() - requestStartTime
        if (totalTimeMs > 0 && tokenCount > 0) {
            lastTokensPerSec = (tokenCount.toFloat() / totalTimeMs) * 1000f
        }

        channel.close()
        awaitClose {
            if (isCancelling.get()) {
                runtime.cancel()
            }
        }
    }.flowOn(Dispatchers.Default)

    override fun cancelGeneration() {
        isCancelling.set(true)
        nativeRuntime?.cancel()
    }

    override fun getLastTtftMs(): Long = lastTtftMs

    override fun getLastTokensPerSec(): Float = lastTokensPerSec

    private fun buildChatPrompt(
        userMessage: String,
        systemPrompt: String,
        history: List<Pair<String, String>>
    ): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n")
        history.takeLast(4).forEach { (user, assistant) ->
            sb.append("<|im_start|>user\n").append(user).append("<|im_end|>\n")
            sb.append("<|im_start|>assistant\n").append(assistant).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>user\n").append(userMessage).append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    override fun release() {
        unloadModel()
        context = null
    }
}
