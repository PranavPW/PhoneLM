package com.phonelm.data

import com.phonelm.rag.EmbeddingGenerator
import io.objectbox.Box
import io.objectbox.query.QueryBuilder

class VectorStore(private val embeddingGenerator: EmbeddingGenerator) {

    private val box: Box<VectorEntity> = ObjectBox.store.boxFor(VectorEntity::class.java)

    /**
     * Adds a document to the vector store.
     * Generates embedding and saves entity.
     */
    fun addDocument(text: String, fileName: String, page: Int) {
        val embedding = embeddingGenerator.generateEmbedding(text)
        
        // Remove old entries for this page to avoid duplicates
        // (Optional: depending on requirements, might want to append or version)
        // For now, simple append or overwrite logic:
        // We'll just add new one.
        
        val entity = VectorEntity(
            text = text,
            fileName = fileName,
            pageNumber = page,
            embedding = embedding
        )
        box.put(entity)
    }

    /**
     * Searches for similar documents using vector similarity.
     */
    fun searchSimilar(query: String, topK: Int): List<VectorEntity> {
        val queryEmbedding = embeddingGenerator.generateEmbedding(query)
        
        // ObjectBox Vector Search
        // Using find with nearest neighbor
        return box.query(VectorEntity_.embedding.nearestNeighbor(queryEmbedding, topK))
            .build()
            .find()
    }
}
