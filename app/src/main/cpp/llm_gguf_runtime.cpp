#include "llm_gguf_runtime.hpp"
#include "llama.h"
#include <chrono>
#include <vector>
#include <string>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <cstring>
#include <sys/stat.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "MahavtaarNativeLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace mahavtaar {

static std::string g_llama_last_error;
static std::mutex g_llama_log_mutex;

static void llama_log_capture_callback(ggml_log_level level, const char * text, void * user_data) {
    (void)user_data;
    if (!text) return;

    if (level == GGML_LOG_LEVEL_ERROR || level == GGML_LOG_LEVEL_WARN) {
        std::lock_guard<std::mutex> lock(g_llama_log_mutex);
        if (g_llama_last_error.size() < 2048) {
            g_llama_last_error += text;
        }
    }

    if (level == GGML_LOG_LEVEL_ERROR) {
        LOGE("[llama.cpp] %s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        LOGW("[llama.cpp] %s", text);
    } else {
        LOGI("[llama.cpp] %s", text);
    }
}

// Simple SHA-256 for native diagnostics
static std::string compute_native_sha256(const std::string& filepath) {
    FILE* f = fopen(filepath.c_str(), "rb");
    if (!f) return "FILE_UNREADABLE";

    // Fast SHA-256 implementation using standard chunking
    uint32_t h[8] = {
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    };
    static const uint32_t k[64] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0x0bef9a3f, 0xc67178f2
    };

    auto ror = [](uint32_t v, int s) -> uint32_t { return (v >> s) | (v << (32 - s)); };

    auto process_block = [&](const uint8_t chunk[64]) {
        uint32_t w[64];
        for (int i = 0; i < 16; ++i) {
            w[i] = (static_cast<uint32_t>(chunk[i * 4]) << 24) |
                   (static_cast<uint32_t>(chunk[i * 4 + 1]) << 16) |
                   (static_cast<uint32_t>(chunk[i * 4 + 2]) << 8) |
                   (static_cast<uint32_t>(chunk[i * 4 + 3]));
        }
        for (int i = 16; i < 64; ++i) {
            uint32_t s0 = ror(w[i - 15], 7) ^ ror(w[i - 15], 18) ^ (w[i - 15] >> 3);
            uint32_t s1 = ror(w[i - 2], 17) ^ ror(w[i - 2], 19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3];
        uint32_t e = h[4], f = h[5], g = h[6], h_val = h[7];
        for (int i = 0; i < 64; ++i) {
            uint32_t S1 = ror(e, 6) ^ ror(e, 11) ^ ror(e, 25);
            uint32_t ch = (e & f) ^ ((~e) & g);
            uint32_t temp1 = h_val + S1 + ch + k[i] + w[i];
            uint32_t S0 = ror(a, 2) ^ ror(a, 13) ^ ror(a, 22);
            uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
            uint32_t temp2 = S0 + maj;
            h_val = g; g = f; f = e; e = d + temp1;
            d = c; c = b; b = a; a = temp1 + temp2;
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d;
        h[4] += e; h[5] += f; h[6] += g; h[7] += h_val;
    };

    uint8_t buffer[64 * 1024];
    uint64_t total_bytes = 0;
    size_t bytes_read = 0;
    uint8_t leftover[64];
    size_t leftover_len = 0;

    while ((bytes_read = fread(buffer, 1, sizeof(buffer), f)) > 0) {
        total_bytes += bytes_read;
        size_t offset = 0;
        if (leftover_len > 0) {
            size_t needed = 64 - leftover_len;
            if (bytes_read >= needed) {
                memcpy(leftover + leftover_len, buffer, needed);
                process_block(leftover);
                offset = needed;
                leftover_len = 0;
            } else {
                memcpy(leftover + leftover_len, buffer, bytes_read);
                leftover_len += bytes_read;
                continue;
            }
        }
        while (offset + 64 <= bytes_read) {
            process_block(buffer + offset);
            offset += 64;
        }
        if (offset < bytes_read) {
            leftover_len = bytes_read - offset;
            memcpy(leftover, buffer + offset, leftover_len);
        }
    }
    fclose(f);

    // Padding
    uint8_t final_block[128];
    memset(final_block, 0, sizeof(final_block));
    if (leftover_len > 0) {
        memcpy(final_block, leftover, leftover_len);
    }
    final_block[leftover_len] = 0x80;
    size_t pad_block_len = (leftover_len < 56) ? 64 : 128;
    uint64_t bit_len = total_bytes * 8;
    for (int i = 0; i < 8; ++i) {
        final_block[pad_block_len - 1 - i] = static_cast<uint8_t>((bit_len >> (i * 8)) & 0xFF);
    }
    process_block(final_block);
    if (pad_block_len == 128) {
        process_block(final_block + 64);
    }

    std::ostringstream oss;
    for (int i = 0; i < 8; ++i) {
        oss << std::hex << std::setw(8) << std::setfill('0') << h[i];
    }
    return oss.str();
}

GgufDiagnosticResult LlmGgufRuntime::inspectModelFile(const std::string& model_path) {
    GgufDiagnosticResult res{};
    res.llama_revision = "llama.cpp b2048+ (GGUF v2/v3 support)";

    FILE* f = fopen(model_path.c_str(), "rb");
    if (!f) {
        res.file_exists = false;
        res.exact_error = "File does not exist or permission denied at path: " + model_path;
        return res;
    }
    res.file_exists = true;

    fseek(f, 0, SEEK_END);
    res.file_size = ftell(f);
    fseek(f, 0, SEEK_SET);

    LOGI("[LLM][MODEL_OPEN] path=%s", model_path.c_str());
    LOGI("[LLM][MODEL_SIZE] bytes=%lld", (long long)res.file_size);

    if (res.file_size < 24) {
        fclose(f);
        res.exact_error = "File too small for GGUF header (< 24 bytes)";
        return res;
    }

    // Read GGUF header
    uint32_t magic = 0;
    uint32_t version = 0;
    uint64_t tensor_count = 0;
    uint64_t metadata_kv_count = 0;

    fread(&magic, sizeof(uint32_t), 1, f);
    fread(&version, sizeof(uint32_t), 1, f);
    fread(&tensor_count, sizeof(uint64_t), 1, f);
    fread(&metadata_kv_count, sizeof(uint64_t), 1, f);
    fclose(f);

    res.magic_valid = (magic == 0x46554747); // "GGUF" in little endian
    res.version = version;
    res.tensor_count = tensor_count;
    res.metadata_kv_count = metadata_kv_count;

    // Compute exact SHA-256
    res.sha256 = compute_native_sha256(model_path);
    LOGI("[LLM][MODEL_SHA256] sha256=%s", res.sha256.c_str());

    if (!res.magic_valid) {
        char magic_str[5] = {0};
        memcpy(magic_str, &magic, 4);
        res.exact_error = "Invalid GGUF magic header: 0x" + std::to_string(magic) + " ('" + magic_str + "'), expected 'GGUF'";
        LOGE("[LLM][LLAMA_LOAD_FAILURE] %s", res.exact_error.c_str());
        return res;
    }

    // Check GGUF version
    if (version < 2 || version > 3) {
        res.exact_error = "Unsupported GGUF version: " + std::to_string(version) + " (supported: 2, 3)";
        LOGE("[LLM][LLAMA_LOAD_FAILURE] %s", res.exact_error.c_str());
        return res;
    }

    // Test llama_load_model_from_file safely
    {
        std::lock_guard<std::mutex> lock(g_llama_log_mutex);
        g_llama_last_error.clear();
    }
    llama_log_set(llama_log_capture_callback, nullptr);

    LOGI("[LLM][LLAMA_LOAD_START] Calling llama_load_model_from_file on %s", model_path.c_str());
    struct llama_model_params mparams = llama_model_default_params();
    llama_model* test_model = llama_load_model_from_file(model_path.c_str(), mparams);

    if (test_model) {
        res.llama_load_success = true;
        char arch_buf[64] = {0};
        llama_model_meta_val_str(test_model, "general.architecture", arch_buf, sizeof(arch_buf));
        res.architecture = arch_buf;
        LOGI("[LLM][LLAMA_LOAD_SUCCESS] Architecture: %s, Vocab: %d", arch_buf, llama_n_vocab(test_model));
        llama_free_model(test_model);
    } else {
        res.llama_load_success = false;
        std::lock_guard<std::mutex> lock(g_llama_log_mutex);
        res.exact_error = g_llama_last_error.empty() ?
            "llama_load_model_from_file returned NULL. Check memory or tensor quantization format." :
            g_llama_last_error;
        LOGE("[LLM][LLAMA_LOAD_FAILURE] Exact error: %s", res.exact_error.c_str());
    }

    return res;
}

LlmGgufRuntime::LlmGgufRuntime() = default;

LlmGgufRuntime::~LlmGgufRuntime() {
    release();
}

bool LlmGgufRuntime::loadModel(const std::string& model_path, int n_ctx, int n_threads) {
    std::lock_guard<std::mutex> lock(mutex_);
    release();

    last_error_.clear();
    {
        std::lock_guard<std::mutex> log_lock(g_llama_log_mutex);
        g_llama_last_error.clear();
    }
    llama_log_set(llama_log_capture_callback, nullptr);

    LOGI("[LLM][MODEL_OPEN] path=%s", model_path.c_str());
    struct stat st{};
    if (stat(model_path.c_str(), &st) == 0) {
        LOGI("[LLM][MODEL_SIZE] bytes=%lld", (long long)st.st_size);
    }

    llama_backend_init(false);

    LOGI("[LLM][LLAMA_LOAD_START] Invoking llama_load_model_from_file for: %s", model_path.c_str());
    struct llama_model_params mparams = llama_model_default_params();
    model_ = llama_load_model_from_file(model_path.c_str(), mparams);
    if (!model_) {
        std::lock_guard<std::mutex> log_lock(g_llama_log_mutex);
        last_error_ = g_llama_last_error.empty() ? "llama_load_model_from_file returned NULL" : g_llama_last_error;
        LOGE("[LLM][LLAMA_LOAD_FAILURE] Failed to load GGUF file: %s | Error: %s", model_path.c_str(), last_error_.c_str());
        return false;
    }

    LOGI("[LLM][LLAMA_LOAD_SUCCESS] Model weights loaded successfully from: %s", model_path.c_str());

    struct llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx > 0 ? n_ctx : 2048;
    cparams.n_threads = n_threads > 0 ? n_threads : 4;
    cparams.n_threads_batch = cparams.n_threads;

    ctx_ = llama_new_context_with_model(model_, cparams);
    if (!ctx_) {
        std::lock_guard<std::mutex> log_lock(g_llama_log_mutex);
        last_error_ = "Failed to allocate llama context for model: " + g_llama_last_error;
        LOGE("%s", last_error_.c_str());
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
        header_.architecture = arch_buf;
        std::string key = std::string(arch_buf) + ".block_count";
        res = llama_model_meta_val_str(model_, key.c_str(), val_buf, sizeof(val_buf));
    } else {
        header_.architecture = "llama";
    }
    header_.n_layer = (res >= 0) ? std::atoi(val_buf) : 24;
    header_.n_head = 16;

    LOGI("Llama model initialized! Arch: %s, Vocab: %d, Ctx: %d, Embd: %d, Layers: %d",
         header_.architecture.c_str(), header_.n_vocab, header_.n_ctx, header_.n_embd, header_.n_layer);

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
