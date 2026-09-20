#include "llm_gguf_runtime.hpp"
#include "llama.h"
#include <chrono>
#include <vector>
#include <android/log.h>

#define LOG_TAG "MahavtaarNativeLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace mahavtaar {

LlmGgufRuntime::LlmGgufRuntime() = default;

LlmGgufRuntime::~LlmGgufRuntime() {
    release();
}

bool LlmGgufRuntime::loadModel(const std::string& model_path, int n_ctx, int n_threads) {
    std::lock_guard<std::mutex> lock(mutex_);
    release();

    LOGI("Loading GGUF LLM model from: %s", model_path.c_str());
    llama_backend_init(false);

    struct llama_model_params mparams = llama_model_default_params();
    model_ = llama_load_model_from_file(model_path.c_str(), mparams);
    if (!model_) {
        LOGE("Failed to load model from GGUF file: %s", model_path.c_str());
        return false;
    }

    struct llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx > 0 ? n_ctx : 2048;
    cparams.n_threads = n_threads > 0 ? n_threads : 4;
    cparams.n_threads_batch = cparams.n_threads;

    ctx_ = llama_new_context_with_model(model_, cparams);
    if (!ctx_) {
        LOGE("Failed to create llama context for model");
        llama_free_model(model_);
        model_ = nullptr;
        return false;
    }

    is_loaded_ = true;
    is_cancelled_.store(false);
    model_path_ = model_path;

    header_.magic = 0x46554747; // GGUF
    header_.version = 3;
    header_.n_vocab = llama_n_vocab(model_);
    header_.n_ctx = llama_n_ctx(ctx_);
    header_.n_embd = llama_n_embd(model_);
    char val_buf[64] = {0};
    int res = llama_model_meta_val_str(model_, "llama.block_count", val_buf, sizeof(val_buf));
    if (res < 0) {
        char arch_buf[64] = {0};
        llama_model_meta_val_str(model_, "general.architecture", arch_buf, sizeof(arch_buf));
        std::string key = std::string(arch_buf) + ".block_count";
        res = llama_model_meta_val_str(model_, key.c_str(), val_buf, sizeof(val_buf));
    }
    header_.n_layer = (res >= 0) ? std::atoi(val_buf) : 24;
    header_.n_head = 16;

    LOGI("Llama model loaded successfully! Vocab: %d, Ctx: %d, Embd: %d, Layers: %d",
         header_.n_vocab, header_.n_ctx, header_.n_embd, header_.n_layer);

    return true;
}

void LlmGgufRuntime::release() {
    if (ctx_) {
        llama_free(ctx_);
        ctx_ = nullptr;
    }
    if (model_) {
        llama_free_model(model_);
        model_ = nullptr;
    }
    is_loaded_ = false;
    model_path_.clear();
}

bool LlmGgufRuntime::isLoaded() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return is_loaded_ && model_ != nullptr && ctx_ != nullptr;
}

void LlmGgufRuntime::cancel() {
    is_cancelled_.store(true);
}

std::string LlmGgufRuntime::generate(
    const std::string& prompt,
    int max_tokens,
    float temperature,
    float top_p,
    TokenCallback token_callback,
    LlmGenerationMetrics& metrics
) {
    std::lock_guard<std::mutex> lock(mutex_);
    metrics = {};

    if (!is_loaded_ || !model_ || !ctx_) {
        LOGE("Cannot generate: LLM runtime not loaded");
        return "";
    }

    is_cancelled_.store(false);
    auto start_time = std::chrono::high_resolution_clock::now();

    // Tokenize prompt
    std::vector<llama_token> prompt_tokens(prompt.size() + 16);
    int n_tokens = llama_tokenize(model_, prompt.c_str(), static_cast<int32_t>(prompt.length()),
                                  prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()), true, false);
    if (n_tokens < 0) {
        prompt_tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(model_, prompt.c_str(), static_cast<int32_t>(prompt.length()),
                                  prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()), true, false);
    }
    if (n_tokens <= 0) {
        LOGE("Failed to tokenize prompt");
        return "";
    }
    prompt_tokens.resize(n_tokens);

    // Initial batch evaluation
    struct llama_batch batch = llama_batch_get_one(prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()), 0, 0);
    if (llama_decode(ctx_, batch) != 0) {
        LOGE("Failed to decode initial prompt batch");
        return "";
    }

    auto first_token_time = std::chrono::high_resolution_clock::now();
    metrics.ttft_ms = std::chrono::duration_cast<std::chrono::milliseconds>(first_token_time - start_time).count();

    const int n_vocab = llama_n_vocab(model_);
    std::string full_response;
    int n_cur = static_cast<int>(prompt_tokens.size());
    int generated_count = 0;

    std::vector<llama_token_data> candidates;
    candidates.reserve(n_vocab);

    for (int step = 0; step < max_tokens; ++step) {
        if (is_cancelled_.load()) {
            LOGI("Generation cancelled by user after %d tokens", generated_count);
            break;
        }

        float* logits = llama_get_logits_ith(ctx_, -1);
        candidates.clear();
        for (llama_token token_id = 0; token_id < n_vocab; ++token_id) {
            candidates.push_back(llama_token_data{token_id, logits[token_id], 0.0f});
        }
        llama_token_data_array candidates_p = { candidates.data(), candidates.size(), false };

        llama_token new_token_id;
        if (temperature <= 0.0f) {
            new_token_id = llama_sample_token_greedy(ctx_, &candidates_p);
        } else {
            llama_sample_top_p(ctx_, &candidates_p, top_p, 1);
            llama_sample_temp(ctx_, &candidates_p, temperature);
            new_token_id = llama_sample_token(ctx_, &candidates_p);
        }

        if (new_token_id == llama_token_eos(model_)) {
            LOGI("Reached EOS token at step %d", step);
            break;
        }

        char piece_buf[128];
        int piece_len = llama_token_to_piece(model_, new_token_id, piece_buf, sizeof(piece_buf));
        std::string token_str;
        if (piece_len > 0) {
            token_str = std::string(piece_buf, piece_len);
        }

        full_response += token_str;
        generated_count++;

        if (token_callback) {
            bool keep_going = token_callback(token_str, new_token_id);
            if (!keep_going) {
                LOGI("Token callback requested stop at token %d", generated_count);
                break;
            }
        }

        // Prepare next single token for evaluation
        batch = llama_batch_get_one(&new_token_id, 1, n_cur, 0);
        n_cur++;
        if (llama_decode(ctx_, batch) != 0) {
            LOGE("Failed to decode token at step %d", step);
            break;
        }
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    metrics.total_time_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    metrics.tokens_generated = generated_count;

    if (metrics.total_time_ms > 0) {
        metrics.tokens_per_sec = (static_cast<float>(generated_count) * 1000.0f) / static_cast<float>(metrics.total_time_ms);
    }

    LOGI("Llama generation complete: %d tokens in %ld ms (%.2f tok/s, TTFT: %ld ms)",
         generated_count, (long)metrics.total_time_ms, metrics.tokens_per_sec, (long)metrics.ttft_ms);

    return full_response;
}

} // namespace mahavtaar
