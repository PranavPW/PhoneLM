package com.phonelm.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TC-01 — Chunker edges. Locks audit CHUNKER invariant.
 * If any assertion fails, STOP and report — do not silently patch Chunker.
 */
class ChunkerEdgeTest {

    @Test
    fun `TC-01 empty input yields no chunks`() {
        assertTrue(Chunker.chunkText("").isEmpty())
        assertTrue(Chunker.chunkText("   ").isEmpty())
        assertTrue(Chunker.chunkText("\n\n   \n").isEmpty())
    }

    @Test
    fun `TC-01 whitespace-only yields no chunks`() {
        assertTrue(Chunker.chunkText(" \t \n \n ").isEmpty())
    }

    @Test
    fun `TC-01 exact-boundary length is one chunk not two`() {
        val text = "X".repeat(Chunker.DEFAULT_MAX_CHARS)
        val out = Chunker.chunkText(text)
        assertEquals(1, out.size)
        assertEquals(500, out[0].length)
    }

    @Test
    fun `TC-01 exact-boundary plus one wraps to two`() {
        val text = "X".repeat(Chunker.DEFAULT_MAX_CHARS + 1)
        val out = Chunker.chunkText(text)
        assertEquals(2, out.size)
        assertEquals(500, out[0].length)
        assertEquals(1, out[1].length)
    }

    @Test
    fun `TC-01 oversized doc splits deterministically`() {
        val huge = "A".repeat(2500)
        val out = Chunker.chunkText(huge, maxChars = 500)
        assertEquals(5, out.size)
        assertTrue(out.all { it.length == 500 })
        assertEquals(2500, out.sumOf { it.length })
    }

    @Test
    fun `TC-01 unicode emoji does not crash and preserves count`() {
        val unicode = "Hello 🌍 ".repeat(100) // mix of multi-code-unit chars
        val out = Chunker.chunkText(unicode, maxChars = 500)
        assertTrue(out.isNotEmpty())
        assertTrue(out.all { it.length <= 500 })
        // Total char count preserved (hard-wrap is char-based)
        assertEquals(unicode.trim().length.toLong(), out.joinToString("\n\n").replace("\n\n", "").length.toLong() + (out.size - 1) * 0) // at least no crash / no empty
        // Simpler: joined without separators contains same code points count as original trimmed
        val joined = out.joinToString("")
        assertEquals(unicode.replace("\n", "").replace(" ", "").length, joined.replace(" ", "").length)
    }
}
