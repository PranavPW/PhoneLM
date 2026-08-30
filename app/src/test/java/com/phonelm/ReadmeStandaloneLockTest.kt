package com.phonelm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-09 — README standalone lock.
 */
class ReadmeStandaloneLockTest {

    private fun findFile(rel: String): File {
        val candidates = listOf(File(rel), File("PhoneLM_v2/$rel"), File("../$rel"), File(System.getProperty("user.dir"), rel), File("../../$rel"))
        return candidates.firstOrNull { it.exists() } ?: File(rel)
    }

    @Test
    fun `TC-09 README mermaid block contains no ForestControl OpenTrae Python`() {
        val readme = findFile("README.md")
        assertTrue("README.md not found at ${readme.absolutePath}", readme.exists())
        val text = readme.readText()
        val mermaid = Regex("```mermaid(.*?)```", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1) ?: ""
        assertTrue("No mermaid block found in README.md", mermaid.isNotBlank())
        assertFalse("Mermaid must not mention ForestControl", mermaid.contains("ForestControl", ignoreCase = true))
        assertFalse("Mermaid must not mention OpenTrae", mermaid.contains("OpenTrae", ignoreCase = true))
        assertFalse("Mermaid must not mention Python", mermaid.contains("Python", ignoreCase = true))
        // Also ensure required internal layers are present
        assertTrue("Mermaid must mention Kotlin/Compose UI", mermaid.contains("Kotlin/Compose UI"))
        assertTrue("Mermaid must mention LlamaEngine.kt", mermaid.contains("LlamaEngine.kt"))
        assertTrue("Mermaid must mention NativeBridge.cpp", mermaid.contains("NativeBridge.cpp"))
        assertTrue("Mermaid must mention vendored llama.cpp", mermaid.contains("vendored llama.cpp"))
    }
}
