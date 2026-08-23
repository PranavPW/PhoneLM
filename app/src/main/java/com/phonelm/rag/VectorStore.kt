package com.phonelm.rag

import kotlin.math.sqrt

data class DocumentChunk(
    val id: String,
    val text: String,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DocumentChunk

        if (id != other.id) return false
        if (text != other.text) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

class VectorStore {
    private val chunks = ArrayList<DocumentChunk>()

    fun addChunk(chunk: DocumentChunk) {
        chunks.add(chunk)
    }

    fun clear() {
        chunks.clear()
    }

    fun search(queryEmbedding: FloatArray, topK: Int = 3): List<DocumentChunk> {
        if (chunks.isEmpty()) return emptyList()
        
        return chunks.map { chunk ->
            chunk to cosineSimilarity(chunk.embedding, queryEmbedding)
        }
        .sortedByDescending { it.second }
        .take(topK)
        .map { it.first }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
