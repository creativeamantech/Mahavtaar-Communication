#ifndef TTS_NEURAL_RUNTIME_HPP
#define TTS_NEURAL_RUNTIME_HPP

#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <cstdint>

struct SherpaOnnxOfflineTts;

namespace mahavtaar {

struct TtsVoiceHeader {
    uint32_t magic;
    uint32_t sample_rate;
    uint32_t num_channels;
    uint32_t num_speakers;
    uint32_t hidden_dim;
    uint32_t n_mel_channels;
};

struct TtsSynthesisResult {
    std::vector<int16_t> pcm_samples;
    int sample_rate;
    int64_t synthesis_time_ms;
    int64_t audio_duration_ms;
    float real_time_factor;
};

class TtsNeuralRuntime {
public:
    TtsNeuralRuntime();
    ~TtsNeuralRuntime();

    bool loadModel(const std::string& model_path);
    void release();
    bool isLoaded() const;
    void cancel();

    TtsSynthesisResult synthesize(const std::string& text, float speed);

    const std::string& getModelPath() const { return model_path_; }
    const TtsVoiceHeader& getHeader() const { return header_; }

private:
    mutable std::mutex mutex_;
    const SherpaOnnxOfflineTts* tts_{nullptr};
    bool is_loaded_{false};
    std::atomic<bool> is_cancelled_{false};
    std::string model_path_;
    TtsVoiceHeader header_{};
};

} // namespace mahavtaar

#endif // TTS_NEURAL_RUNTIME_HPP
