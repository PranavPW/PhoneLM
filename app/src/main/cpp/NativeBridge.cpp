#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>

// PhoneLM native bridge over vendored llama.cpp.
// Decode loop ported from examples/simple/simple.cpp (see docs/PROGRESS.md
// "Step 5 Decode Plan" and docs/RESEARCH_NOTES.md R1).

#include "llama.cpp/include/llama.h"

#define TAG "PhoneLM_Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static const llama_vocab* g_vocab = nullptr;

static constexpr int kDefaultCtxTokens = 2048;
static constexpr int kThreads = 4;
static constexpr int kMaxNewTokens = 256;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_phonelm_core_LlamaEngine_loadModelWithGpuLayers(JNIEnv* env, jobject, jstring path, jint n_gpu_layers);

extern "C" JNIEXPORT jboolean JNICALL
Java_com_phonelm_core_LlamaEngine_loadModel(JNIEnv* env, jobject, jstring path) {
    return Java_com_phonelm_core_LlamaEngine_loadModelWithGpuLayers(env, nullptr, path, 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_phonelm_core_LlamaEngine_loadModelWithGpuLayers(JNIEnv* env, jobject, jstring path, jint n_gpu_layers) {
    if (g_model) {
        LOGE("Model already loaded; unload first");
        return JNI_FALSE;
    }

    const char* modelPath = env->GetStringUTFChars(path, nullptr);
    LOGD("Loading model from %s (n_gpu_layers=%d)", modelPath, n_gpu_layers);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers; // 0 = CPU-only fallback (D6)

    g_model = llama_model_load_from_file(modelPath, model_params);
    env->ReleaseStringUTFChars(path, modelPath);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }
    g_vocab = llama_model_get_vocab(g_model);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = kDefaultCtxTokens;
    ctx_params.n_threads = kThreads;
    ctx_params.n_threads_batch = kThreads;
    ctx_params.no_perf = false;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    LOGD("Model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_phonelm_core_LlamaEngine_unloadModel(JNIEnv* env, jobject) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
    LOGD("Model unloaded");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_phonelm_core_LlamaEngine_generateCompletion(JNIEnv* env, jobject, jstring prompt) {
    if (!g_model || !g_ctx || !g_vocab) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);

    // Fresh KV state per generation so consecutive calls are independent.
    llama_memory_clear(llama_get_memory(g_ctx), true);

    // Two-pass tokenize (simple.cpp pattern).
    const int n_prompt = -llama_tokenize(g_vocab, promptStr, strlen(promptStr), NULL, 0, true, true);
    if (n_prompt <= 0) {
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("Error: empty prompt");
    }
    std::vector<llama_token> prompt_tokens(n_prompt);
    if (llama_tokenize(g_vocab, promptStr, promptStr ? strlen(promptStr) : 0,
                       prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("Error: tokenization failed");
    }
    env->ReleaseStringUTFChars(prompt, promptStr);

    if ((size_t)(n_prompt + kMaxNewTokens) > kDefaultCtxTokens) {
        LOGD("Prompt+%d exceeds ctx %d; output will be truncated", n_prompt, kDefaultCtxTokens);
    }

    // Greedy sampler chain — deterministic for M1 tests (Decode Plan bullet 3).
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = false;
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    std::string result;
    result.reserve(1024);
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());

    for (int n_pos = 0; n_pos + batch.n_tokens < n_prompt + kMaxNewTokens; ) {
        if (llama_decode(g_ctx, batch)) {
            LOGE("decode failed at pos %d", n_pos);
            break;
        }
        n_pos += batch.n_tokens;

        llama_token new_token_id = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(g_vocab, new_token_id)) break;

        char buf[128];
        int n = llama_token_to_piece(g_vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n < 0) {
            LOGE("token_to_piece failed for token %d", new_token_id);
            break;
        }
        result.append(buf, n);
        batch = llama_batch_get_one(&new_token_id, 1);
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_phonelm_core_LlamaEngine_getEmbeddings(JNIEnv* env, jobject, jstring text) {
    // FAKE until M2 decision D2 resolves its fate (ONNX MiniLM is the real
    // embedding runtime). Not called by any RAG code path.
    if (!g_model || !g_ctx) return nullptr;

    const char* textStr = env->GetStringUTFChars(text, nullptr);
    env->ReleaseStringUTFChars(text, textStr);

    int dim = 1024;
    jfloatArray result = env->NewFloatArray(dim);
    if (!result) return nullptr;

    std::vector<float> dummy(dim, 0.0f); // deterministic zero vector, not a ramp
    env->SetFloatArrayRegion(result, 0, dim, dummy.data());
    return result;
}
