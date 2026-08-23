#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>

// Forward declarations found in llama.cpp headers (common.h, llama.h)
// We include them assuming the include paths are set up correctly by add_subdirectory(llama.cpp)
#include "llama.cpp/include/llama.h"
#include "llama.cpp/common/common.h"

#define TAG "PhoneLM_Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_phonelm_core_LlamaEngine_loadModel(JNIEnv* env, jobject, jstring path) {
    const char* modelPath = env->GetStringUTFChars(path, nullptr);
    LOGD("Loading model from %s", modelPath);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 99; // Try to offload all to GPU (Vulkan)

    g_model = llama_load_model_from_file(modelPath, model_params);
    
    env->ReleaseStringUTFChars(path, modelPath);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; // Default context window
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
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
    LOGD("Model unloaded");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_phonelm_core_LlamaEngine_generateCompletion(JNIEnv* env, jobject, jstring prompt) {
    if (!g_model || !g_ctx) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string result = "Thinking... (Native inference placeholder for: ";
    result += promptStr;
    result += ")";
    
    // REAL INFERENCE TODO:
    // 1. Tokenize prompt
    // 2. llama_decode
    // 3. Sample tokens
    // 4. Detokenize to string
    
    // For this step, we just return a placeholder to verify JNI linkage.
    // Implementing full llama loop requires ~100 lines of code.

    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_phonelm_core_LlamaEngine_getEmbeddings(JNIEnv* env, jobject, jstring text) {
    if (!g_model || !g_ctx) return nullptr;

    const char* textStr = env->GetStringUTFChars(text, nullptr);
    // TODO: Tokenize and run embedding inference
    // This is a placeholder returning a random vector of size 1024
    
    int dim = 1024;
    jfloatArray result = env->NewFloatArray(dim);
    if (!result) {
        env->ReleaseStringUTFChars(text, textStr);
        return nullptr;
    }

    std::vector<float> dummy(dim);
    for(int i=0; i<dim; i++) dummy[i] = 0.01f * i; // Dummy data

    env->SetFloatArrayRegion(result, 0, dim, dummy.data());
    
    env->ReleaseStringUTFChars(text, textStr);
    return result;
}


