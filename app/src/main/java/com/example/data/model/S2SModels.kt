package com.example.data.model

/**
 * State enum representing the active lifecycle phase of the Speech-to-Speech pipeline.
 */
enum class S2SState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

/**
 * Backend models configuration for the on-device pipeline stages.
 */
enum class SttBackendType(val displayName: String, val modelName: String, val latencyCategory: String) {
    ZIPFORMER_TRANSDUCER("Streaming Zipformer", "sherpa-onnx-streaming-zipformer-en", "~180ms"),
    WHISPER_TINY("Whisper Tiny (ONNX)", "whisper-tiny-en-q8", "~240ms"),
    PARAFORMER("Paraformer", "paraformer-offline-en", "~210ms"),
    ANDROID_ON_DEVICE("Android On-Device STT", "system-speech-service", "~190ms")
}

enum class LlmBackendType(val displayName: String, val modelName: String, val latencyCategory: String) {
    LLAMA_3_2_1B("Llama 3.2 1B (llama.cpp)", "llama-3.2-1b-instruct-q4_k_m.gguf", "~140ms TTFT"),
    SMOLLM_135M("SmolLM 135M (Llamatik)", "smollm-135m-instruct-q4.gguf", "~75ms TTFT"),
    GEMINI_STREAMING("Gemini Real-Time (Cloud/Server)", "gemini-flash-streaming", "~160ms TTFT"),
    LOCAL_NEURAL_S2S("Instant S2S Engine (Ultra-Fast)", "s2s-neural-compact", "~90ms TTFT")
}

enum class TtsBackendType(val displayName: String, val modelName: String, val sampleRate: Int) {
    KOKORO_82M("Kokoro-82M Neural TTS", "kokoro-v0_19.onnx", 24000),
    PIPER_VITS("Piper VITS Voice", "en_US-lessac-medium.onnx", 22050),
    ANDROID_NEURAL("Android System Neural TTS", "system-tts-engine", 24000)
}

/**
 * File paths for model weights (e.g. on internal storage or assets).
 */
data class ModelPaths(
    val asrModelPath: String = "models/zipformer/encoder.onnx",
    val asrTokensPath: String = "models/zipformer/tokens.txt",
    val llmModelPath: String = "models/llm/llama-3.2-1b-q4.gguf",
    val ttsModelPath: String = "models/tts/kokoro-v0_19.onnx",
    val ttsVoicesPath: String = "models/tts/voices.bin",
    val vadModelPath: String = "models/vad/silero_vad.onnx"
)

/**
 * Configuration options for S2SEngine.
 */
data class S2SConfig(
    val models: ModelPaths = ModelPaths(),
    val sttBackend: SttBackendType = SttBackendType.ZIPFORMER_TRANSDUCER,
    val llmBackend: LlmBackendType = LlmBackendType.LOCAL_NEURAL_S2S,
    val ttsBackend: TtsBackendType = TtsBackendType.KOKORO_82M,
    val sampleRate: Int = 16000,
    val vadSensitivity: Float = 0.55f,
    val silenceHangoverMs: Long = 650L,
    val continuousConversation: Boolean = true,
    val allowBargeIn: Boolean = true,
    val speechRate: Float = 1.05f,
    val speechPitch: Float = 1.0f,
    val voiceName: String = "af_heart",
    val systemPrompt: String = "You are a real-time conversational assistant. Keep answers concise, natural, and under 2 sentences."
)

/**
 * Real-time latency tracking metrics for each stage of the conversational loop.
 */
data class LatencyMetrics(
    val sttLatencyMs: Long = 0L,
    val ttftMs: Long = 0L, // Time to First Token
    val ttsLatencyMs: Long = 0L, // Time to First Audio Playback
    val totalLatencyMs: Long = 0L,
    val targetMet: Boolean = true // target < 800ms
) {
    companion object {
        const val LATENCY_TARGET_MS = 800L
    }
}

/**
 * Pipeline events emitted by S2SEngine.
 */
sealed interface S2SEvent {
    data class StateChanged(val state: S2SState) : S2SEvent
    data class AudioLevel(val dbLevel: Float, val normalizedAmplitude: Float) : S2SEvent
    data class UserTranscript(val text: String, val isFinal: Boolean, val timestamp: Long = System.currentTimeMillis()) : S2SEvent
    data class AssistantDelta(val deltaText: String, val fullText: String, val isFinished: Boolean) : S2SEvent
    data class Metrics(val metrics: LatencyMetrics) : S2SEvent
    data class SpeakingState(val isSpeaking: Boolean) : S2SEvent
    data class Error(val message: String, val cause: Throwable? = null) : S2SEvent
}

/**
 * Message entry for the conversation transcript list.
 */
enum class SenderType {
    USER,
    ASSISTANT
}

data class ConversationMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: SenderType,
    val text: String,
    val isPartial: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val metrics: LatencyMetrics? = null
)
