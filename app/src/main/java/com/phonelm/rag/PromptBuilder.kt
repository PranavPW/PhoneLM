package com.phonelm.rag

/**
 * Pure prompt-assembly logic (JVM-testable). Extracted from ChatViewModel
 * so retrieval formatting is testable without Android.
 */
object PromptBuilder {

    data class ContextDoc(val fileName: String, val text: String)

    fun buildRagPrompt(question: String, contexts: List<ContextDoc>): String {
        if (contexts.isEmpty()) return question
        val contextBlock = contexts.joinToString("\n\n") { doc ->
            "Title: ${doc.fileName}\nContent: ${doc.text}"
        }
        return """
            Use the following context to answer the user's question.
            Context:
            $contextBlock

            Question: $question
        """.trimIndent()
    }
}
