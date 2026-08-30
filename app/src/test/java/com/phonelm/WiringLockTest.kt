package com.phonelm

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-06 — Pure-class wiring lock. Locks audit PL5: no orphans.
 */
class WiringLockTest {

    private fun findFile(rel: String): File {
        val candidates = listOf(File(rel), File("PhoneLM_v2/$rel"), File("../$rel"), File(System.getProperty("user.dir"), rel))
        return candidates.firstOrNull { it.exists() } ?: File(rel)
    }

    @Test
    fun `TC-06 ChatViewModel references PromptBuilder and HomeScreen references ModelLocator`() {
        val vm = findFile("app/src/main/java/com/phonelm/viewmodel/ChatViewModel.kt")
        val home = findFile("app/src/main/java/com/phonelm/ui/HomeScreen.kt")
        assertTrue("ChatViewModel.kt not found", vm.exists())
        assertTrue("HomeScreen.kt not found", home.exists())
        assertTrue("ChatViewModel must reference PromptBuilder.buildRagPrompt (or orphan)", vm.readText().contains("PromptBuilder.buildRagPrompt"))
        assertTrue("HomeScreen must reference ModelLocator.resolveModel (or orphan)", home.readText().contains("ModelLocator.resolveModel"))
    }
}
