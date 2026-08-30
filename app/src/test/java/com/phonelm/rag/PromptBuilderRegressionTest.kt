package com.phonelm.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TC-02 — PromptBuilder regression lock.
 * Zero contexts must be plain prompt; N contexts must assemble correctly;
 * no placeholder string may ever appear in any template.
 */
class PromptBuilderRegressionTest {

    @Test
    fun `TC-02 zero contexts returns plain prompt verbatim`() {
        val q = "What is 2+2?"
        assertEquals(q, PromptBuilder.buildRagPrompt(q, emptyList()))
        assertFalse(PromptBuilder.buildRagPrompt(q, emptyList()).contains("Title:"))
    }

    @Test
    fun `TC-02 N contexts assembled in order with separators`() {
        val q = "Explain"
        val docs = listOf(
            PromptBuilder.ContextDoc("a.pdf", "AAA"),
            PromptBuilder.ContextDoc("b.pdf", "BBB"),
            PromptBuilder.ContextDoc("c.pdf", "CCC")
        )
        val out = PromptBuilder.buildRagPrompt(q, docs)
        val iA = out.indexOf("Title: a.pdf")
        val iB = out.indexOf("Title: b.pdf")
        val iC = out.indexOf("Title: c.pdf")
        assertTrue(iA in 0 until iB)
        assertTrue(iB in 0 until iC)
        assertTrue(out.contains("Question: Explain"))
    }

    @Test
    fun `TC-02 no placeholder string can appear in any template`() {
        val forbidden = "Native inference placeholder"
        val cases = listOf(
            PromptBuilder.buildRagPrompt("Hello", emptyList()),
            PromptBuilder.buildRagPrompt("Q", listOf(PromptBuilder.ContextDoc("f", "c"))),
            PromptBuilder.buildRagPrompt("Q2", listOf(
                PromptBuilder.ContextDoc("x", "y"),
                PromptBuilder.ContextDoc("y", "z")
            ))
        )
        cases.forEach { assertFalse("Placeholder leaked into prompt: $it", it.contains(forbidden)) }
    }

    @Test
    fun `TC-02 template contains Context and Question headers when contexts present`() {
        val out = PromptBuilder.buildRagPrompt("Q", listOf(PromptBuilder.ContextDoc("f", "hello")))
        assertTrue(out.contains("Context:"))
        assertTrue(out.contains("Question: Q"))
        assertTrue(out.indexOf("Context:") < out.indexOf("Question:"))
    }
}
