package com.phonelm

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-08 — GGUF-not-in-git check (also run as shell check in TEST_REPORT).
 */
class GgufNotInGitTest {

    @Test
    fun `TC-08 git ls-files contains no gguf and no assets models`() {
        // Run git ls-files via ProcessBuilder to check tracked files
        val pb = ProcessBuilder("git", "ls-files")
        pb.redirectErrorStream(true)
        // Try repo root candidates
        val candidates = listOf(File("."), File("PhoneLM_v2"), File(".."), File(System.getProperty("user.dir") ?: "."))
        var output: String? = null
        var dir: File? = null
        for (c in candidates) {
            if (File(c, ".git").exists() || File(c, "app/build.gradle.kts").exists()) {
                pb.directory(c)
                dir = c
                try {
                    val proc = pb.start()
                    output = proc.inputStream.bufferedReader().readText()
                    proc.waitFor()
                    break
                } catch (_: Exception) { }
            }
        }
        assertTrue("Could not run git ls-files from $dir", output != null)
        val lines = output!!.lines()
        val ggufs = lines.filter { it.endsWith(".gguf", ignoreCase = true) }
        assertTrue("git ls-files must contain no *.gguf, found: $ggufs", ggufs.isEmpty())
        val assetModels = lines.filter { it.contains("app/src/main/assets/models") }
        assertTrue("git ls-files must contain nothing under app/src/main/assets/models, found: $assetModels", assetModels.isEmpty())
    }
}
