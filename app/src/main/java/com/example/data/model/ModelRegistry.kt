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
enum class ModelDownloadStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    INSTALLED,
    LOADING,
    READY,
    ERROR
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
 * Detailed descriptor for a locally installable AI model.
 */
data class ModelItem(
    val id: String,
    val name: String,
    val type: ModelType,
    val version: String,
    val format: String, // e.g. "GGUF", "ONNX", "BIN"
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
    val runtime: String, // e.g. "Whisper-Stream", "llama.cpp GGUF", "Kokoro-ONNX"
    val minimumRamMb: Int,
    val recommendedRamMb: Int,
    val supportedLanguages: List<String>,
    val quantization: String,
    val isRecommended: Boolean = false,
    val localFileName: String,
    val downloadStatus: ModelDownloadStatus = ModelDownloadStatus.NOT_INSTALLED,
    val downloadProgress: Float = 0f, // 0.0 to 1.0
    val downloadSpeed: String = "",
    val downloadedBytes: Long = 0L,
    val localFilePath: String? = null,
    val isLoaded: Boolean = false,
    val errorMessage: String? = null,
    val loadTimeMs: Long = 0L
) {
    val sizeFormatted: String
        get() {
            val mb = fileSizeBytes.toDouble() / (1024 * 1024)
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
            name = "Whisper Tiny (Mobile Q8)",
            type = ModelType.STT,
            version = "v1.2",
            format = "GGUF",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            fileSizeBytes = 39 * 1024 * 1024L, // 39 MB
            checksumSha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c64a37b",
            runtime = "Whisper Streaming STT",
            minimumRamMb = 250,
            recommendedRamMb = 512,
            supportedLanguages = listOf("English", "Hindi", "Hinglish"),
            quantization = "Q8_0",
            isRecommended = true,
            localFileName = "whisper-tiny-q8.bin"
        ),
        ModelItem(
            id = "stt_zipformer_transducer",
            name = "Streaming Zipformer Transducer",
            type = ModelType.STT,
            version = "v2.0",
            format = "ONNX",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-02-21.tar.bz2",
            fileSizeBytes = 28 * 1024 * 1024L, // 28 MB
            checksumSha256 = "c3f8482f01f8d481b29d472c38dbba752834b72661d9a0d845e032f9cb2512a8",
            runtime = "Sherpa-ONNX Transducer",
            minimumRamMb = 200,
            recommendedRamMb = 400,
            supportedLanguages = listOf("English", "Hinglish"),
            quantization = "INT8",
            localFileName = "zipformer-streaming.onnx"
        ),
        ModelItem(
            id = "stt_paraformer_compact",
            name = "Paraformer Multilingual Compact",
            type = ModelType.STT,
            version = "v1.0",
            format = "BIN",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/paraformer-offline-en.bin",
            fileSizeBytes = 45 * 1024 * 1024L, // 45 MB
            checksumSha256 = "e4d3a81297fbc9901458e0a8276f578912d8a9b23194a0d92415bbca9028471e",
            runtime = "Paraformer Acoustic",
            minimumRamMb = 300,
            recommendedRamMb = 600,
            supportedLanguages = listOf("English", "Hindi"),
            quantization = "FP16",
            localFileName = "paraformer-compact.bin"
        ),

        // --- LLM Models ---
        ModelItem(
            id = "llm_smollm_135m_q4",
            name = "SmolLM 135M Instruct (Q4_K_M)",
            type = ModelType.LLM,
            version = "v0.2",
            format = "GGUF",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM-135M-Instruct-GGUF/resolve/main/smollm-135m-instruct-q4_k_m.gguf",
            fileSizeBytes = 85 * 1024 * 1024L, // 85 MB
            checksumSha256 = "7a892b4510cdb284812f0134b29381c6292374b591da938e21a02938164b1849",
            runtime = "llama.cpp GGUF Mobile",
            minimumRamMb = 280,
            recommendedRamMb = 512,
            supportedLanguages = listOf("English", "Conversational Hindi"),
            quantization = "Q4_K_M",
            isRecommended = true,
            localFileName = "smollm-135m-instruct-q4_k_m.gguf"
        ),
        ModelItem(
            id = "llm_mobiles2s_compact_q4",
            name = "MobileS2S Neural LLM (Ultra-Fast)",
            type = ModelType.LLM,
            version = "v1.1",
            format = "GGUF",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileSizeBytes = 340 * 1024 * 1024L, // 340 MB
            checksumSha256 = "19a28b789e0234ac2b89218d9f10928a381923412a819b28a821908123491823",
            runtime = "llama.cpp GGUF Low-Latency",
            minimumRamMb = 600,
            recommendedRamMb = 1024,
            supportedLanguages = listOf("English", "Hindi", "Hinglish"),
            quantization = "Q4_K_M",
            localFileName = "mobiles2s-0.5b-q4.gguf"
        ),
        ModelItem(
            id = "llm_llama_3_2_1b_q4",
            name = "Llama 3.2 1B Instruct (Q4_K_M)",
            type = ModelType.LLM,
            version = "v3.2",
            format = "GGUF",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            fileSizeBytes = 740 * 1024 * 1024L, // 740 MB
            checksumSha256 = "a592e81729013c72183e91024958102938102938102938102938102938102938",
            runtime = "llama.cpp GGUF Neural",
            minimumRamMb = 1200,
            recommendedRamMb = 2048,
            supportedLanguages = listOf("English", "Multilingual"),
            quantization = "Q4_K_M",
            localFileName = "llama-3.2-1b-instruct-q4_k_m.gguf"
        ),

        // --- TTS Models ---
        ModelItem(
            id = "tts_kokoro_82m",
            name = "Kokoro-82M Neural Voice (AF Heart)",
            type = ModelType.TTS,
            version = "v0.19",
            format = "ONNX",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.onnx",
            fileSizeBytes = 48 * 1024 * 1024L, // 48 MB
            checksumSha256 = "4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e",
            runtime = "Kokoro Neural TTS (24kHz)",
            minimumRamMb = 200,
            recommendedRamMb = 400,
            supportedLanguages = listOf("English", "Conversational"),
            quantization = "FP16",
            isRecommended = true,
            localFileName = "kokoro-v0_19.onnx"
        ),
        ModelItem(
            id = "tts_piper_lessac",
            name = "Piper VITS English (Lessac Medium)",
            type = ModelType.TTS,
            version = "v1.0",
            format = "ONNX",
            downloadUrl = "https://github.com/rhasspy/piper/releases/download/v0.0.2/voice-en_US-lessac-medium.tar.gz",
            fileSizeBytes = 28 * 1024 * 1024L, // 28 MB
            checksumSha256 = "9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b",
            runtime = "Piper VITS Engine (22.05kHz)",
            minimumRamMb = 180,
            recommendedRamMb = 350,
            supportedLanguages = listOf("English"),
            quantization = "ONNX Standard",
            localFileName = "piper-en-lessac.onnx"
        ),
        ModelItem(
            id = "tts_piper_hindi",
            name = "Piper VITS Hindi/English (Bilingual)",
            type = ModelType.TTS,
            version = "v1.0",
            format = "ONNX",
            downloadUrl = "https://github.com/rhasspy/piper/releases/download/v0.0.2/voice-hi_IN-medium.tar.gz",
            fileSizeBytes = 32 * 1024 * 1024L, // 32 MB
            checksumSha256 = "8f7e6d5c4b3a2918079685746352413029180796857463524130291807968574",
            runtime = "Piper VITS Multilingual (22.05kHz)",
            minimumRamMb = 190,
            recommendedRamMb = 380,
            supportedLanguages = listOf("Hindi", "English", "Hinglish"),
            quantization = "ONNX Standard",
            localFileName = "piper-hi-medium.onnx"
        )
    )

    fun getAllModels(): List<ModelItem> = defaultModels

    fun getModelsByType(type: ModelType): List<ModelItem> = defaultModels.filter { it.type == type }

    fun getModelById(id: String): ModelItem? = defaultModels.find { it.id == id }

    fun getRecommendedModel(type: ModelType): ModelItem? = defaultModels.find { it.type == type && it.isRecommended }
}
