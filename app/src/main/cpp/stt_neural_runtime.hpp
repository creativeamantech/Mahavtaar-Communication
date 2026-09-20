#ifndef STT_NEURAL_RUNTIME_HPP
#define STT_NEURAL_RUNTIME_HPP

#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <atomic>
#include <cstdint>

struct whisper_context;

namespace mahavtaar {

struct SttModelHeader {
    uint32_t magic;
    uint32_t version;
    uint32_t n_vocab;
    uint32_t n_audio_ctx;
    uint32_t n_audio_state;
    uint32_t n_audio_head;
    uint32_t n_audio_layer;
    uint32_t n_mels;
    uint32_t ftype;
};

struct SttInferenceResult {
    std::string transcript;
    float confidence;
    int64_t inference_time_ms;
    int64_t audio_duration_ms;
    float real_time_factor;
};

class SttNeuralRuntime {
public:
    SttNeuralRuntime();
    ~SttNeuralRuntime();

    bool loadModel(const std::string& model_path);
    void release();
    bool isLoaded() const;
    void cancel();

    SttInferenceResult transcribe(const float* audio_pcm, size_t sample_count, int sample_rate);

    const std::string& getModelPath() const { return model_path_; }
    const SttModelHeader& getHeader() const { return header_; }

private:
    mutable std::mutex mutex_;
    whisper_context* ctx_{nullptr};
    bool is_loaded_{false};
    std::atomic<bool> is_cancelled_{false};
    std::string model_path_;
    SttModelHeader header_{};
};

} // namespace mahavtaar

#endif // STT_NEURAL_RUNTIME_HPP
