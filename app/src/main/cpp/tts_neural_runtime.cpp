#include "tts_neural_runtime.hpp"
#include "c-api.h"
#include <chrono>
#include <cstring>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "MahavtaarNativeTTS"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace mahavtaar {

TtsNeuralRuntime::TtsNeuralRuntime() = default;

TtsNeuralRuntime::~TtsNeuralRuntime() {
    release();
}

bool TtsNeuralRuntime::loadModel(const std::string& model_path) {
    std::lock_guard<std::mutex> lock(mutex_);
    release();

    LOGI("Loading neural TTS model via sherpa-onnx: %s", model_path.c_str());

    std::string base_dir;
    size_t last_slash = model_path.find_last_of("/\\");
    if (last_slash != std::string::npos) {
        base_dir = model_path.substr(0, last_slash);
    } else {
        base_dir = ".";
    }

    std::string tokens_path = base_dir + "/tokens.txt";
    std::string data_dir = base_dir + "/espeak-ng-data";

    SherpaOnnxOfflineTtsConfig config;
    memset(&config, 0, sizeof(config));
    config.model.num_threads = 2;
    config.model.provider = "cpu";
    config.model.debug = 0;

    bool is_kokoro = (model_path.find("kokoro") != std::string::npos);
    if (is_kokoro) {
        std::string voices_path = base_dir + "/voices.bin";
        config.model.kokoro.model = model_path.c_str();
        config.model.kokoro.voices = voices_path.c_str();
        config.model.kokoro.tokens = tokens_path.c_str();
        config.model.kokoro.data_dir = data_dir.c_str();
        config.model.kokoro.length_scale = 1.0f;
    } else {
        // VITS / Piper default
        config.model.vits.model = model_path.c_str();
        config.model.vits.tokens = tokens_path.c_str();
        config.model.vits.data_dir = data_dir.c_str();
        config.model.vits.length_scale = 1.0f;
        config.model.vits.noise_scale = 0.667f;
        config.model.vits.noise_scale_w = 0.8f;
    }

    tts_ = SherpaOnnxCreateOfflineTts(&config);
    if (!tts_) {
        LOGE("Failed to create SherpaOnnxOfflineTts from model: %s", model_path.c_str());
        return false;
    }

    is_loaded_ = true;
    is_cancelled_.store(false);
    model_path_ = model_path;

    header_.magic = 0x54545331; // "TTS1"
    header_.sample_rate = 22050; // standard Piper sample rate
    header_.num_channels = 1;
    header_.num_speakers = 1;

    LOGI("Neural TTS model loaded successfully via sherpa-onnx!");
    return true;
}

void TtsNeuralRuntime::release() {
    if (tts_) {
        SherpaOnnxDestroyOfflineTts(tts_);
        tts_ = nullptr;
    }
    is_loaded_ = false;
    model_path_.clear();
}

bool TtsNeuralRuntime::isLoaded() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return is_loaded_ && tts_ != nullptr;
}

void TtsNeuralRuntime::cancel() {
    is_cancelled_.store(true);
}

TtsSynthesisResult TtsNeuralRuntime::synthesize(const std::string& text, float speed) {
    std::lock_guard<std::mutex> lock(mutex_);
    TtsSynthesisResult result{};
    result.sample_rate = 22050;

    if (!is_loaded_ || !tts_ || text.empty()) {
        LOGE("Cannot synthesize: TTS runtime not loaded or empty text");
        return result;
    }

    is_cancelled_.store(false);
    auto start_time = std::chrono::high_resolution_clock::now();

    float effective_speed = (speed > 0.1f && speed < 3.0f) ? speed : 1.0f;
    SherpaOnnxGenerationConfig gen_cfg;
    memset(&gen_cfg, 0, sizeof(gen_cfg));
    gen_cfg.sid = 0;
    gen_cfg.speed = effective_speed;
    gen_cfg.silence_scale = 0.2f;

    const SherpaOnnxGeneratedAudio* audio = SherpaOnnxOfflineTtsGenerateWithConfig(tts_, text.c_str(), &gen_cfg, nullptr, nullptr);

    auto end_time = std::chrono::high_resolution_clock::now();
    result.synthesis_time_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();

    if (!audio || audio->n <= 0 || !audio->samples) {
        LOGE("SherpaOnnxOfflineTtsGenerate returned empty or null audio");
        if (audio) {
            SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);
        }
        return result;
    }

    result.sample_rate = audio->sample_rate;
    result.pcm_samples.resize(audio->n);

    // Convert float samples [-1.0f, 1.0f] to 16-bit PCM
    for (int i = 0; i < audio->n; ++i) {
        float s = std::max(-1.0f, std::min(1.0f, audio->samples[i]));
        result.pcm_samples[i] = static_cast<int16_t>(s * 32767.0f);
    }

    result.audio_duration_ms = static_cast<int64_t>((static_cast<double>(audio->n) / result.sample_rate) * 1000.0);
    if (result.audio_duration_ms > 0) {
        result.real_time_factor = static_cast<float>(result.synthesis_time_ms) / static_cast<float>(result.audio_duration_ms);
    }

    LOGI("TTS synthesized %d samples in %ld ms (RTF: %.2f) at %d Hz",
         audio->n, (long)result.synthesis_time_ms, result.real_time_factor, result.sample_rate);

    SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);
    return result;
}

} // namespace mahavtaar
