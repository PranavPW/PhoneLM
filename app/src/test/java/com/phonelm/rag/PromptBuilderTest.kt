package com.phonelm.rag

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `no contexts returns the raw question`() {
        val q = "What is the capital of France?"
        assertEquals(q, PromptBuilder.buildRagPrompt(q, emptyList()))
    }

    @Test
    fun `single context produces context block and question`() {
        val out = PromptBuilder.buildRagPrompt(
            "Summarize",
            listOf(PromptBuilder.ContextDoc("report.pdf", "Revenue grew 10%."))
        )
        assert(out.contains("Use the following context"))
        assert(out.contains("Title: report.pdf"))
        assert(out.contains("Content: Revenue grew 10%."))
        assert(out.contains("Question: Summarize"))
        // Question must come after the context
        assert(out.indexOf("Question:") > out.indexOf("Context:"))
    }

    @Test
    fun `multiple contexts are separated by blank line and keep order`() {
        val out = PromptBuilder.buildRagPrompt(
            "q",
            listOf(
                PromptBuilder.ContextDoc("a.pdf", "AAA"),
                PromptBuilder.ContextDoc("b.pdf", "BBB")
            )
        )
        val idxA = out.indexOf("Title: a.pdf")
        val idxB = out.indexOf("Title: b.pdf")
        assertEquals(true, idxA in 0 until idxB)
        assert(out.contains("AAA"))
        assert(out.contains("BBB"))
    }

    @Test
    fun `context text containing newlines is embedded verbatim`() {
        val multiline = "line1\nline2\n\nline3"
        val out = PromptBuilder.buildRagPrompt(
            "q",
            listOf(PromptBuilder.ContextDoc("m.txt", multiline))
        )
        assert(out.contains(multiline))
    }

    @Test
    fun `question with special characters passes through unchanged`() {
        val tricky = "What does {\"json\": true} <tag> mean? 100% & more?"
        assertEquals(tricky, PromptBuilder.buildRagPrompt(tricky, emptyList()))
    }
}
