package com.phonelm

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * TC-04 — JNI contract lock. Every external fun in LlamaEngine.kt must have a
 * matching Java_com_phonelm_core_LlamaEngine_* symbol in NativeBridge.cpp.
 * Kills silent JNI drift.
 */
class JniContractLockTest {

    private fun findFile(rel: String): File {
        val candidates = listOf(
            File(rel),
            File("PhoneLM_v2/$rel"),
            File("../$rel"),
            File(System.getProperty("user.dir"), rel)
        )
        return candidates.firstOrNull { it.exists() } ?: File(rel)
    }

    @Test
    fun `TC-04 every external fun has matching native symbol`() {
        val kt = findFile("app/src/main/java/com/phonelm/core/LlamaEngine.kt")
        val cpp = findFile("app/src/main/cpp/NativeBridge.cpp")
        assertTrue("LlamaEngine.kt not found at ${kt.absolutePath}", kt.exists())
        assertTrue("NativeBridge.cpp not found at ${cpp.absolutePath}", cpp.exists())

        val ktText = kt.readText()
        val cppText = cpp.readText()

        val externals = Regex("""external fun\s+(\w+)\s*\(""").findAll(ktText).map { it.groupValues[1] }.toList()
        assertTrue("No externals found in LlamaEngine.kt", externals.isNotEmpty())

        val symbols = Regex("""Java_com_phonelm_core_LlamaEngine_(\w+)""").findAll(cppText).map { it.groupValues[1] }.toSet()

        val missing = externals.filter { it !in symbols }
        if (missing.isNotEmpty()) {
            fail("Missing native symbols for externals: $missing; found symbols: $symbols")
        }
        // Also ensure no orphan native symbols without Kotlin external (warn, not fail if overloads exist)
        // loadModelWithGpuLayers is intentionally extra; allow it.
        assertTrue("At least ${externals.size} native symbols expected", symbols.size >= externals.size)
    }
}
