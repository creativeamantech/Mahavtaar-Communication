package com.example.data.model

import java.io.File

/**
 * Categories of models used in the on-device Speech-to-Speech pipeline.
 */
enum class ModelType(val title: String, val categoryLabel: String) {
    STT("Speech-to-Text", "Speech Recognition"),
    LLM("Language Model", "On-Device LLM"),
    TTS("Text-to-Speech", "Neural Synthesis"),
    VOICE("Voice Model", "Acoustic Speaker")
}

/**
 * State of model files in the local lifecycle.
 */
enum class ModelDownloadStatus(val displayLabel: String) {
    NOT_DOWNLOADED("Not Downloaded"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    VERIFYING("Verifying"),
    VERIFIED("Verified"),
    LOADING("Loading"),
    READY("Ready"),
    FAILED("Failed"),
    FAILED_VERIFICATION("Verification Failed"),
    UNLOADING("Unloading");

    companion object {
        val NOT_INSTALLED get() = NOT_DOWNLOADED
        val INSTALLED get() = VERIFIED
        val ERROR get() = FAILED
    }
}

/**
 * Hardware compatibility verdict for a model on the host device.
 */
enum class CompatibilityVerdict(val label: String) {
    COMPATIBLE("Compatible"),
    RECOMMENDED("Recommended"),
    NOT_RECOMMENDED("Not Recommended"),
    UNSUPPORTED("Unsupported")
}

/**
 * Companion asset required for multi-file model packages (e.g. Kokoro TTS voices.bin, tokens.txt).
 */
data class CompanionAsset(
    val filename: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val checksumSha256: String = "",
    val isRequired: Boolean = true
)

/**
 * Detailed descriptor for a locally installable AI model.
 */
data class ModelItem(
    val id: String,
    val name: String,
    val type: ModelType,
    val version: String,
    val format: String, // e.g. "GGUF", "ONNX", "BIN"
    val description: String = "",
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
    val runtime: String, // e.g. "Whisper-Stream", "llama.cpp GGUF", "Kokoro-ONNX"
    val minimumRamMb: Int,
    val recommendedRamMb: Int,
    val minimumStorageMb: Int = (fileSizeBytes / (1024 * 1024) + 50).toInt(),
    val supportedLanguages: List<String>,
    val quantization: String,
    val isRecommended: Boolean = false,
    val isDownloadable: Boolean = true,
    val nonDownloadableReason: String? = null,
    val localFileName: String,
    val companionAssets: List<CompanionAsset> = emptyList(),
    val downloadStatus: ModelDownloadStatus = ModelDownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f, // 0.0 to 1.0
    val downloadSpeed: String = "",
    val downloadedBytes: Long = 0L,
    val remainingBytes: Long = fileSizeBytes,
    val etaSeconds: Long = 0L,
    val localFilePath: String? = null,
    val isLoaded: Boolean = false,
    val errorMessage: String? = null,
    val loadTimeMs: Long = 0L
) {
    val totalPackageSizeBytes: Long
        get() = fileSizeBytes + companionAssets.sumOf { it.fileSizeBytes }

    val expectedSizeBytes: Long get() = totalPackageSizeBytes
    val minimumStorageBytes: Long get() = minimumStorageMb * 1024 * 1024L

    val sizeFormatted: String
        get() {
            val mb = totalPackageSizeBytes.toDouble() / (1024 * 1024)
            return if (mb >= 1000) {
                String.format("%.2f GB", mb / 1024)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}

/**
 * Registry containing available local models for STT, LLM, and TTS.
 */
object ModelRegistry {

    private val defaultModels = listOf(
        // --- STT Models ---
        ModelItem(
            id = "stt_whisper_tiny_q8",
            name = "Whisper Tiny (Mobile)",
            type = ModelType.STT,
            version = "v1.0",
            format = "GGUF",
            description = "Lightweight on-device speech recognition model optimized for low-latency voice capture.",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            fileSizeBytes = 77691713L, // 74.1 MB exact
            checksumSha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
            runtime = "Whisper Streaming STT",
            minimumRamMb = 250,
            recommendedRamMb = 512,
            supportedLanguages = listOf("English", "Hindi", "Multilingual"),
            quantization = "FP16/Q8",
            isRecommended = true,
            isDownloadable = true,
            localFileName = "whisper-tiny.bin"
        ),
        ModelItem(
            id = "stt_zipformer_transducer",
            name = "Streaming Zipformer Transducer",
            type = ModelType.STT,
            version = "v2.0",
            format = "ONNX",
            description = "Sherpa-ONNX streaming transducer. Multi-file archive requiring offline unpacking.",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-02-21.tar.bz2",
            fileSizeBytes = 397939030L,
            checksumSha256 = "c3f8482f01f8d481b29d472c38dbba752834b72661d9a0d845e032f9cb2512a8",
            runtime = "Sherpa-ONNX Transducer",
            minimumRamMb = 300,
            recommendedRamMb = 512,
            supportedLanguages = listOf("English"),
            quantization = "INT8",
            isDownloadable = false,
            nonDownloadableReason = "Multi-file tar.bz2 archive requiring offline extraction before single-file mobile loading.",
            localFileName = "zipformer-streaming.onnx"
        ),
        ModelItem(
            id = "stt_paraformer_compact",
            name = "Paraformer Multilingual Compact",
            type = ModelType.STT,
            version = "v1.0",
            format = "BIN",
            description = "Paraformer offline acoustic model.",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/paraformer-offline-en.bin",
            fileSizeBytes = 45 * 1024 * 1024L,
            checksumSha256 = "e4d3a81297fbc9901458e0a8276f578912d8a9b23194a0d92415bbca9028471e",
            runtime = "Paraformer Acoustic",
            minimumRamMb = 300,
            recommendedRamMb = 600,
            supportedLanguages = listOf("English", "Hindi"),
            quantization = "FP16",
            isDownloadable = false,
            nonDownloadableReason = "Requires accompanying vocabulary token mapping dictionary for acoustic decoding.",
            localFileName = "paraformer-compact.bin"
        ),

        // --- LLM Models ---
        ModelItem(
            id = "llm_smollm_135m_q4",
            name = "SmolLM2 135M Instruct (Q4_K_M)",
            type = ModelType.LLM,
            version = "v2.0",
            format = "GGUF",
            description = "Official HuggingFaceTB SmolLM2-135M-Instruct on-device model with ultra-fast TTFT and low memory footprint.",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-GGUF/resolve/main/smollm2-135m-instruct-q4_k_m.gguf",
            fileSizeBytes = 96654720L, // 92.2 MB exact
            checksumSha256 = "63b827e85741639c086435d8eefc4a169b5fa088d8b6da44b62d35ebec8c75ff",
            runtime = "llama.cpp GGUF Mobile",
            minimumRamMb = 280,
            recommendedRamMb = 512,
            supportedLanguages = listOf("English", "Conversational Hindi"),
            quantization = "Q4_K_M",
            isRecommended = true,
            isDownloadable = true,
            localFileName = "smollm2-135m-instruct-q4_k_m.gguf"
        ),
        ModelItem(
            id = "llm_mobiles2s_compact_q4",
            name = "Qwen2.5 0.5B Instruct (Q4_K_M)",
            type = ModelType.LLM,
            version = "v2.5",
            format = "GGUF",
            description = "High-intelligence small language model supporting English, Hindi, and code comprehension.",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileSizeBytes = 491400032L, // 468.6 MB exact
            checksumSha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            runtime = "llama.cpp GGUF Low-Latency",
            minimumRamMb = 600,
            recommendedRamMb = 1024,
            supportedLanguages = listOf("English", "Hindi", "Hinglish"),
            quantization = "Q4_K_M",
            isDownloadable = true,
            localFileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        ),
        ModelItem(
            id = "llm_llama_3_2_1b_q4",
            name = "Llama 3.2 1B Instruct (Q4_K_M)",
            type = ModelType.LLM,
            version = "v3.2",
            format = "GGUF",
            description = "Meta Llama 3.2 1B edge model with strong conversational reasoning and natural responses.",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            fileSizeBytes = 807694464L, // 770.3 MB exact
            checksumSha256 = "6f85a640a97cf2bf5b8e764087b1e83da0fdb51d7c9fab7d0fece9385611df83",
            runtime = "llama.cpp GGUF Neural",
            minimumRamMb = 1200,
            recommendedRamMb = 2048,
            supportedLanguages = listOf("English", "Multilingual"),
            quantization = "Q4_K_M",
            isDownloadable = true,
            localFileName = "llama-3.2-1b-instruct-q4_k_m.gguf"
        ),

        // --- TTS Models ---
        ModelItem(
            id = "tts_kokoro_82m",
            name = "Kokoro-82M Neural Voice (Quantized Package)",
            type = ModelType.TTS,
            version = "v1.0",
            format = "ONNX",
            description = "State-of-the-art neural speech synthesis package with expressive human intonation, tokens, and multi-speaker voices.",
            downloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_quantized.onnx",
            fileSizeBytes = 92361116L, // 88.1 MB exact
            checksumSha256 = "fbae9257e1e05ffc727e951ef9b9c98418e6d79f1c9b6b13bd59f5c9028a1478",
            runtime = "Kokoro Neural TTS (24kHz)",
            minimumRamMb = 200,
            recommendedRamMb = 400,
            supportedLanguages = listOf("English", "Conversational"),
            quantization = "INT8/Quantized",
            isRecommended = true,
            isDownloadable = true,
            localFileName = "kokoro-82m-quantized.onnx",
            companionAssets = listOf(
                CompanionAsset(
                    filename = "tokens.txt",
                    downloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/tokens.txt",
                    fileSizeBytes = 12000L,
                    isRequired = true
                ),
                CompanionAsset(
                    filename = "voices.bin",
                    downloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/voices.bin",
                    fileSizeBytes = 25000000L,
                    isRequired = true
                ),
                CompanionAsset(
                    filename = "config.json",
                    downloadUrl = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/config.json",
                    fileSizeBytes = 4096L,
                    isRequired = false
                )
            )
        ),
        ModelItem(
            id = "tts_piper_lessac",
            name = "Piper VITS English (Lessac Medium)",
            type = ModelType.TTS,
            version = "v1.0",
            format = "ONNX",
            description = "Fast neural VITS English voice generator with low computational overhead.",
            downloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
            fileSizeBytes = 63201294L, // 60.3 MB exact
            checksumSha256 = "5efe09e69902187827af646e1a6e9d269dee769f9877d17b16b1b46eeaaf019f",
            runtime = "Piper VITS Engine (22.05kHz)",
            minimumRamMb = 180,
            recommendedRamMb = 350,
            supportedLanguages = listOf("English"),
            quantization = "ONNX Medium",
            isDownloadable = true,
            localFileName = "piper-en-lessac.onnx"
        ),
        ModelItem(
            id = "tts_piper_hindi",
            name = "Piper VITS Hindi/English (Pratham)",
            type = ModelType.TTS,
            version = "v1.0",
            format = "ONNX",
            description = "Bilingual Hindi/English neural voice synthesis engine for Indian English and Hindi.",
            downloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx",
            fileSizeBytes = 63516050L, // 60.6 MB exact
            checksumSha256 = "169964b0871667f6793416d4b35e97357a68ba1ad01df8580c28048989ee7693",
            runtime = "Piper VITS Multilingual (22.05kHz)",
            minimumRamMb = 190,
            recommendedRamMb = 380,
            supportedLanguages = listOf("Hindi", "English", "Hinglish"),
            quantization = "ONNX Medium",
            isDownloadable = true,
            localFileName = "piper-hi-pratham.onnx"
        )
    )

    fun getAllModels(): List<ModelItem> = defaultModels

    fun getModelsByType(type: ModelType): List<ModelItem> = defaultModels.filter { it.type == type }

    fun getModelById(id: String): ModelItem? = defaultModels.find { it.id == id }

    fun getRecommendedModel(type: ModelType): ModelItem? = defaultModels.find { it.type == type && it.isRecommended }
}
