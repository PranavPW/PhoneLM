package com.phonelm.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LlamaEngine {
    init {
        try {
            System.loadLibrary("phonelm")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    external fun loadModel(path: String): Boolean
    external fun loadModelWithGpuLayers(path: String, nGpuLayers: Int): Boolean
    external fun unloadModel()
    external fun generateCompletion(prompt: String): String
    external fun getEmbeddings(text: String): FloatArray?


    suspend fun loadModelSafe(path: String): Boolean = withContext(Dispatchers.IO) {
        loadModel(path)
    }

    /** CPU-only by default (n_gpu_layers=0, DECISIONS.md D6). */
    suspend fun loadModelSafe(path: String, nGpuLayers: Int): Boolean = withContext(Dispatchers.IO) {
        loadModelWithGpuLayers(path, nGpuLayers)
    }

    suspend fun generate(prompt: String): Flow<String> = flow {
        // For now, we return a single string, but in future we should stream tokens
        // This requires a callback setup in JNI which is complex.
        // We will fake streaming from the single result for UI testing if needed, 
        // or implement true streaming later.
        val result = withContext(Dispatchers.IO) {
            generateCompletion(prompt)
        }
        emit(result)
    }
}
