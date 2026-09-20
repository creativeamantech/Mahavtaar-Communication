#include "stt_neural_runtime.hpp"
#include "whisper.h"
#include <chrono>
#include <android/log.h>

#define LOG_TAG "MahavtaarNativeSTT"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace mahavtaar {

SttNeuralRuntime::SttNeuralRuntime() = default;

SttNeuralRuntime::~SttNeuralRuntime() {
    release();
}

bool SttNeuralRuntime::loadModel(const std::string& model_path) {
    std::lock_guard<std::mutex> lock(mutex_);
    release();

    LOGI("Loading Whisper STT model from: %s", model_path.c_str());
    struct whisper_context_params cparams = whisper_context_default_params();
    ctx_ = whisper_init_from_file_with_params(model_path.c_str(), cparams);

    if (!ctx_) {
        LOGE("Failed to initialize whisper context from model file: %s", model_path.c_str());
        return false;
    }

    is_loaded_ = true;
    is_cancelled_.store(false);
    model_path_ = model_path;

    header_.magic = 0x67676d6c; // ggml
    header_.n_vocab = whisper_n_vocab(ctx_);
    header_.n_audio_ctx = whisper_n_audio_ctx(ctx_);
    header_.n_audio_state = 384;
    header_.n_audio_head = 6;
    header_.n_audio_layer = 4;
    header_.n_mels = 80;

    LOGI("Whisper model loaded successfully! Vocab size: %d, Audio ctx: %d",
         header_.n_vocab, header_.n_audio_ctx);
    return true;
}

void SttNeuralRuntime::release() {
    if (ctx_) {
        whisper_free(ctx_);
        ctx_ = nullptr;
    }
    is_loaded_ = false;
    model_path_.clear();
}

bool SttNeuralRuntime::isLoaded() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return is_loaded_ && ctx_ != nullptr;
}

void SttNeuralRuntime::cancel() {
    is_cancelled_.store(true);
}

SttInferenceResult SttNeuralRuntime::transcribe(const float* audio_pcm, size_t sample_count, int sample_rate) {
    std::lock_guard<std::mutex> lock(mutex_);
    SttInferenceResult result{};
    result.audio_duration_ms = static_cast<int64_t>((static_cast<double>(sample_count) / sample_rate) * 1000.0);

    if (!is_loaded_ || !ctx_ || !audio_pcm || sample_count == 0) {
        LOGE("Cannot transcribe: STT runtime not loaded or empty audio");
        return result;
    }

    is_cancelled_.store(false);
    auto start_time = std::chrono::high_resolution_clock::now();

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = 4;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    int ret = whisper_full(ctx_, params, audio_pcm, static_cast<int>(sample_count));
    auto end_time = std::chrono::high_resolution_clock::now();
    result.inference_time_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();

    if (result.audio_duration_ms > 0) {
        result.real_time_factor = static_cast<float>(result.inference_time_ms) / static_cast<float>(result.audio_duration_ms);
    }

    if (ret != 0) {
        LOGE("whisper_full returned error code: %d", ret);
        return result;
    }

    const int n_segments = whisper_full_n_segments(ctx_);
    std::string full_text;
    for (int i = 0; i < n_segments; ++i) {
        const char* seg_text = whisper_full_get_segment_text(ctx_, i);
        if (seg_text) {
            full_text += seg_text;
        }
    }

    result.transcript = full_text;
    result.confidence = 0.95f;
    LOGI("Whisper transcription finished in %ld ms (RTF: %.2f): '%s'",
         (long)result.inference_time_ms, result.real_time_factor, result.transcript.c_str());

    return result;
}

} // namespace mahavtaar
