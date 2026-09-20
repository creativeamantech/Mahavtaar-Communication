#ifndef LLM_GGUF_RUNTIME_HPP
#define LLM_GGUF_RUNTIME_HPP

#include <string>
#include <vector>
#include <functional>
#include <mutex>
#include <atomic>
#include <cstdint>

struct llama_model;
struct llama_context;

namespace mahavtaar {

struct GgufHeader {
    uint32_t magic;
    uint32_t version;
    uint64_t tensor_count;
    uint64_t metadata_kv_count;
    uint32_t n_embd;
    uint32_t n_head;
    uint32_t n_layer;
    uint32_t n_vocab;
    uint32_t n_ctx;
};

struct LlmGenerationMetrics {
    int64_t ttft_ms;
    int64_t total_time_ms;
    int32_t tokens_generated;
    float tokens_per_sec;
};

using TokenCallback = std::function<bool(const std::string& token, int token_id)>;

class LlmGgufRuntime {
public:
    LlmGgufRuntime();
    ~LlmGgufRuntime();

    bool loadModel(const std::string& model_path, int n_ctx, int n_threads);
    void release();
    bool isLoaded() const;
    void cancel();

    std::string generate(
        const std::string& prompt,
        int max_tokens,
        float temperature,
        float top_p,
        TokenCallback token_callback,
        LlmGenerationMetrics& metrics
    );

    const std::string& getModelPath() const { return model_path_; }
    const GgufHeader& getHeader() const { return header_; }

private:
    mutable std::mutex mutex_;
    llama_model* model_{nullptr};
    llama_context* ctx_{nullptr};
    bool is_loaded_{false};
    std::atomic<bool> is_cancelled_{false};
    std::string model_path_;
    GgufHeader header_{};
};

} // namespace mahavtaar

#endif // LLM_GGUF_RUNTIME_HPP
