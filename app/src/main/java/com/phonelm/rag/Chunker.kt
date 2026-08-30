package com.phonelm.rag

/**
 * Pure chunking logic (JVM-testable).
 * Paragraph-aware merging with hard wrap for oversized single paragraphs.
 * Replaces the blind String.chunked(500) that cut sentences mid-word.
 */
object Chunker {

    const val DEFAULT_MAX_CHARS = 500
    private const val PARAGRAPH_SEP = "\n\n"

    fun chunkText(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<String> {
        require(maxChars > 0) { "maxChars must be positive" }
        if (text.isBlank()) return emptyList()

        val result = ArrayList<String>()
        var current = StringBuilder()

        fun flush() {
            if (current.isNotBlank()) result.add(current.toString().trim())
            current = StringBuilder()
        }

        for (paragraph in text.split(PARAGRAPH_SEP)) {
            val p = paragraph.trim()
            if (p.isEmpty()) continue
            when {
                p.length > maxChars -> {
                    flush()
                    p.chunked(maxChars).forEach { result.add(it) }
                }
                current.isNotEmpty() && current.length + PARAGRAPH_SEP.length + p.length > maxChars -> {
                    flush()
                    current.append(p)
                }
                else -> {
                    if (current.isNotEmpty()) current.append(PARAGRAPH_SEP)
                    current.append(p)
                }
            }
        }
        flush()
        return result
    }
}
