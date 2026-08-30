package com.phonelm.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {

    @Test
    fun `blank text yields no chunks`() {
        assertTrue(Chunker.chunkText("").isEmpty())
        assertTrue(Chunker.chunkText("   \n\n  ").isEmpty())
    }

    @Test
    fun `short text is a single chunk`() {
        val out = Chunker.chunkText("Hello world.")
        assertEquals(listOf("Hello world."), out)
    }

    @Test
    fun `paragraphs merge up to max without exceeding it`() {
        val text = "AAAA\n\nBBBB\n\nCCCC"
        val out = Chunker.chunkText(text, maxChars = 10)
        // Each paragraph is 4 chars; two paragraphs + separator = 10 chars, fits.
        assertEquals(2, out.size)
        assertTrue(out.all { it.length <= 10 })
        assertEquals("AAAA\n\nBBBB", out[0])
        assertEquals("CCCC", out[1])
    }

    @Test
    fun `oversized paragraph is hard-wrapped at maxChars`() {
        val long = "X".repeat(1200)
        val out = Chunker.chunkText(long, maxChars = 500)
        assertEquals(3, out.size)
        assertEquals(500, out[0].length)
        assertEquals(500, out[1].length)
        assertEquals(200, out[2].length)
    }

    @Test
    fun `no chunk ever exceeds maxChars`() {
        val text = buildString {
            repeat(20) { i -> append("Paragraph number $i with some filler content.\n\n") }
            append("Z".repeat(1100))
        }
        val out = Chunker.chunkText(text, maxChars = 500)
        assertTrue(out.isNotEmpty())
        assertTrue(out.none { it.length > 500 })
    }

    @Test
    fun `content is preserved in order`() {
        val text = "First paragraph content.\n\nSecond paragraph content.\n\nThird paragraph."
        val out = Chunker.chunkText(text, maxChars = 500)
        assertEquals(listOf(
            "First paragraph content.\n\nSecond paragraph content.\n\nThird paragraph."
        ), out)
    }

    @Test
    fun `hard-wrapped output preserves total character count`() {
        val long = "X".repeat(1200)
        val out = Chunker.chunkText(long, maxChars = 500)
        assertEquals(1200L, out.sumOf { it.length.toLong() })
    }
}
