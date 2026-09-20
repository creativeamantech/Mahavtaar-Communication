#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <sstream>
#include <android/log.h>
#include "stt_neural_runtime.hpp"
#include "llm_gguf_runtime.hpp"
#include "tts_neural_runtime.hpp"

#define LOG_TAG "MahavtaarJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace mahavtaar;

static std::string jstring2string(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string str(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return str;
}

extern "C" {

// ============================================================================
// STT Native Bridge
// ============================================================================

JNIEXPORT jlong JNICALL
Java_com_example_data_nativebridge_NativeSTTRuntime_nativeCreate(
    JNIEnv* env,
    jobject thiz
) {
    auto* runtime = new (std::nothrow) SttNeuralRuntime();
    return reinterpret_cast<jlong>(runtime);
}

JNIEXPORT jboolean JNICALL
Java_com_example_data_nativebridge_NativeSTTRuntime_nativeLoadModel(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jstring model_path
) {
    auto* runtime = reinterpret_cast<SttNeuralRuntime*>(handle);
    if (!runtime) return JNI_FALSE;

    std::string path = jstring2string(env, model_path);
    bool success = runtime->loadModel(path);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_example_data_nativebridge_NativeSTTRuntime_nativeTranscribe(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jfloatArray audio_pcm,
    jint sample_rate
) {
    auto* runtime = reinterpret_cast<SttNeuralRuntime*>(handle);
    if (!runtime || !runtime->isLoaded() || !audio_pcm) {
        return env->NewStringUTF("");
    }

    jsize len = env->GetArrayLength(audio_pcm);
    jfloat* pcm_data = env->GetFloatArrayElements(audio_pcm, nullptr);

    SttInferenceResult result = runtime->transcribe(pcm_data, static_cast<size_t>(len), sample_rate);

    env->ReleaseFloatArrayElements(audio_pcm, pcm_data, JNI_ABORT);
    return env->NewStringUTF(result.transcript.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_example_data_nativebridge_NativeSTTRuntime_nativeIsLoaded(
    JNIEnv* env,
    jobject thiz,
    jlong handle
) {
    auto* runtime = reinterpret_cast<SttNeuralRuntime*>(handle);
    return (runtime && runtime->isLoaded()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_data_nativebridge_NativeSTTRuntime_nativeCancel(
    JNIEnv* env,
    jobject thiz,
    jlong handle
) {
    auto* runtime = reinterpret_cast<SttNeuralRuntime*>(handle);
    if (runtime) {
        runtime->cancel();
    }
}

JNIEXPORT void JNICALL
Java_com_example_data_nativebridge_NativeSTTRuntime_nativeRelease(
    JNIEnv* env,
    jobject thiz,
    jlong handle
) {
    auto* runtime = reinterpret_cast<SttNeuralRuntime*>(handle);
    if (runtime) {
        delete runtime;
    }
}

// ============================================================================
// LLM Native Bridge
// ============================================================================

JNIEXPORT jlong JNICALL
Java_com_example_data_nativebridge_NativeLLMRuntime_nativeCreate(
    JNIEnv* env,
    jobject thiz
) {
    auto* runtime = new (std::nothrow) LlmGgufRuntime();
    return reinterpret_cast<jlong>(runtime);
}

JNIEXPORT jboolean JNICALL
Java_com_example_data_nativebridge_NativeLLMRuntime_nativeLoadModel(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jstring model_path,
    jint n_ctx,
    jint n_threads
) {
    auto* runtime = reinterpret_cast<LlmGgufRuntime*>(handle);
    if (!runtime) return JNI_FALSE;

    std::string path = jstring2string(env, model_path);
    bool success = runtime->loadModel(path, n_ctx, n_threads);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_example_data_nativebridge_NativeLLMRuntime_nativeGenerate(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jstring prompt,
    jint max_tokens,
    jfloat temperature,
    jfloat top_p,
    jobject callback
) {
    auto* runtime = reinterpret_cast<LlmGgufRuntime*>(handle);
    if (!runtime || !runtime->isLoaded()) {
        return env->NewStringUTF("");
    }

    std::string prompt_str = jstring2string(env, prompt);
    LlmGenerationMetrics metrics{};

    jclass callback_class = callback ? env->GetObjectClass(callback) : nullptr;
    jmethodID on_token_method = callback_class ?
        env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;I)Z") : nullptr;

    TokenCallback cb = nullptr;
    if (callback && on_token_method) {
        cb = [env, callback, on_token_method](const std::string& token, int token_id) -> bool {
            jstring jtoken = env->NewStringUTF(token.c_str());
            jboolean cont = env->CallBooleanMethod(callback, on_token_method, jtoken, token_id);
            env->DeleteLocalRef(jtoken);
            return cont == JNI_TRUE;
        };
    }

    std::string result = runtime->generate(prompt_str, max_tokens, temperature, top_p, cb, metrics);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_example_data_nativebridge_NativeLLMRuntime_nativeCancel(
    JNIEnv* env,
    jobject thiz,
    jlong handle
) {
    auto* runtime = reinterpret_cast<LlmGgufRuntime*>(handle);
    if (runtime) {
        runtime->cancel();
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_data_nativebridge_NativeLLMRuntime_nativeIsLoaded(
    JNIEnv* env,
    jobject thiz,
    jlong handle
) {
    auto* runtime = reinterpret_cast<LlmGgufRuntime*>(handle);
    return (runtime && runtime->isLoaded()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_example_data_nativebridge_NativeLLMRuntime_nativeGetLastError(
    JNIEnv* env,
    jobject thiz,
    jlong handle
) {
    auto* runtime = reinterpret_cast<LlmGgufRuntime*>(handle);
    if (!runtime) return env->NewStringUTF("Runtime handle is null");
    return env->NewStringUTF(runtime->getLastError().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_data_nativebridge_NativeLLMRuntime_nativeInspectModel(
    JNIEnv* env,
    jobject thiz,
    jstring model_path
) {
    std::string path = jstring2string(env, model_path);
    auto diag = LlmGgufRuntime::inspectModelFile(path);

    std::ostringstream oss;
    oss << "File: " << path << "\n"
        << "Exists: " << (diag.file_exists ? "PASS" : "FAIL") << "\n"
        << "Size: " << diag.file_size << " bytes\n"
        << "SHA-256: " << diag.sha256 << "\n"
        << "GGUF Magic: " << (diag.magic_valid ? "PASS" : "FAIL") << "\n"
        << "GGUF Version: " << diag.version << "\n"
        << "Tensor Count: " << diag.tensor_count << "\n"
        << "Metadata Count: " << diag.metadata_kv_count << "\n"
        << "Architecture: " << (diag.architecture.empty() ? "N/A" : diag.architecture) << "\n"
        << "llama.cpp Revision: " << diag.llama_revision << "\n"
        << "llama_load_model_from_file: " << (diag.llama_load_success ? "PASS" : "FAIL") << "\n"
        << "Exact Error: " << (diag.exact_error.empty() ? "NONE" : diag.exact_error);

    return env->NewStringUTF(oss.str().c_str());
}

JNIEXPORT void JNICALL
Java_com_example_data_nativebridge_NativeLLMRuntime_nativeRelease(
    JNIEnv* env,
    jobject thiz,
    jlong handle
) {
    auto* runtime = reinterpret_cast<LlmGgufRuntime*>(handle);
    if (runtime) {
        delete runtime;
    }
}

// ============================================================================
// TTS Native Bridge
// ============================================================================

JNIEXPORT jlong JNICALL
Java_com_example_data_nativebridge_NativeTTSRuntime_nativeCreate(
    JNIEnv* env,
    jobject thiz
) {
    auto* runtime = new (std::nothrow) TtsNeuralRuntime();
    return reinterpret_cast<jlong>(runtime);
}

JNIEXPORT jboolean JNICALL
Java_com_example_data_nativebridge_NativeTTSRuntime_nativeLoadModel(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jstring model_path
) {
    auto* runtime = reinterpret_cast<TtsNeuralRuntime*>(handle);
    if (!runtime) return JNI_FALSE;

    std::string path = jstring2string(env, model_path);
    bool success = runtime->loadModel(path);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jshortArray JNICALL
Java_com_example_data_nativebridge_NativeTTSRuntime_nativeSynthesize(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jstring text,
    jfloat speed
) {
    auto* runtime = reinterpret_cast<TtsNeuralRuntime*>(handle);
    if (!runtime || !runtime->isLoaded()) {
        jshortArray empty = env->NewShortArray(0);
        return empty;
    }

    std::string text_str = jstring2string(env, text);
    TtsSynthesisResult result = runtime->synthesize(text_str, speed);

    jshortArray jarr = env->NewShortArray(static_cast<jsize>(result.pcm_samples.size()));
    if (jarr && !result.pcm_samples.empty()) {
        env->SetShortArrayRegion(jarr, 0, static_cast<jsize>(result.pcm_samples.size()),
                                reinterpret_cast<const jshort*>(result.pcm_samples.data()));
    }
    return jarr;
}

JNIEXPORT void JNICALL
Java_com_example_data_nativebridge_NativeTTSRuntime_nativeCancel(
    JNIEnv* env,
    jobject thiz,
    jlong handle
) {
    auto* runtime = reinterpret_cast<TtsNeuralRuntime*>(handle);
    if (runtime) {
        runtime->cancel();
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_data_nativebridge_NativeTTSRuntime_nativeIsLoaded(
    JNIEnv* env,
    jobject thiz,
    jlong handle
) {
    auto* runtime = reinterpret_cast<TtsNeuralRuntime*>(handle);
    return (runtime && runtime->isLoaded()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_data_nativebridge_NativeTTSRuntime_nativeRelease(
    JNIEnv* env,
    jobject thiz,
    jlong handle
) {
    auto* runtime = reinterpret_cast<TtsNeuralRuntime*>(handle);
    if (runtime) {
        delete runtime;
    }
}

} // extern "C"
