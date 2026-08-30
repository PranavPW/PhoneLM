package com.phonelm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.phonelm.core.LlamaEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * M1 JNI smoke test (MANUAL_VERIFY gate). Run by the USER on an emulator:
 *   .\gradlew.bat connectedDebugAndroidTest
 *
 * Requires the bundled GGUF to be present (scripts/fetch_model.ps1 run before
 * assembleDebug). Skips (not fails) if the model was not bundled, so the test
 * suite stays usable in model-less builds.
 */
@RunWith(AndroidJUnit4::class)
class JniSmokeTest {

    private fun materializeBundledModel(): File? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val names = context.assets.list("models")?.filter { it.endsWith(".gguf") } ?: emptyList()
        assumeTrue("No bundled GGUF in assets/models/ — run scripts/fetch_model.ps1 first", names.isNotEmpty())
        // Deterministic choice: largest asset.
        val name = names.maxByOrNull {
            context.assets.open("models/$it").use { s -> s.available().toLong() }
        }!!
        val out = File(context.filesDir, name)
        if (!out.exists() || out.length() == 0L) {
            context.assets.open("models/$name").use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
        }
        return out
    }

    @Test
    fun jniLoad_generate_unload_realOutput() {
        val modelPath = materializeBundledModel()
        assertNotNull(modelPath)

        val loaded = LlamaEngine.loadModel(modelPath!!.absolutePath)
        assertTrue("loadModel returned false — native load failed", loaded)
        try {
            val response = LlamaEngine.generateCompletion("Say hello in one short sentence.")
            // THE anti-placeholder gate: kills the fake engine permanently.
            assertFalse(
                "Engine still returns the fake placeholder: $response",
                response.contains("Native inference placeholder")
            )
            assertFalse("Engine returned empty completion", response.isBlank())
            assertTrue(
                "Engine error string leaked into completion: $response",
                !response.startsWith("Error:")
            )
        } finally {
            LlamaEngine.unloadModel()
        }
    }

    @Test
    fun generate_withoutModel_returnsErrorNotCrash() {
        LlamaEngine.unloadModel()
        val response = LlamaEngine.generateCompletion("Hello")
        assertTrue(response.startsWith("Error:"))
    }
}
