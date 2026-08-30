package com.phonelm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-05 — Anti-placeholder static lock. Complements runtime JniSmokeTest.
 */
class AntiPlaceholderLockTest {

    private fun findFile(rel: String): File {
        val candidates = listOf(File(rel), File("PhoneLM_v2/$rel"), File("../$rel"), File(System.getProperty("user.dir"), rel))
        return candidates.firstOrNull { it.exists() } ?: File(rel)
    }

    @Test
    fun `TC-05 NativeBridge has zero placeholder and contains sampler`() {
        val cpp = findFile("app/src/main/cpp/NativeBridge.cpp")
        assertTrue("NativeBridge.cpp not found", cpp.exists())
        val text = cpp.readText()
        val placeholderCount = Regex("Native inference placeholder").findAll(text).count()
        assertEquals("NativeBridge.cpp must contain ZERO 'Native inference placeholder' (fake still present)", 0, placeholderCount)
        assertTrue("NativeBridge.cpp must contain llama_sampler_sample (real decode loop)", text.contains("llama_sampler_sample"))
    }
}
