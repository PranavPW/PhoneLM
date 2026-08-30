package com.phonelm.core

import java.io.File

/**
 * Pure model-resolution logic (JVM-testable).
 * Resolution order preference: bundled asset copy > Downloads fallback;
 * among equals, the largest valid GGUF wins (deterministic).
 */
object ModelLocator {

    const val ASSETS_MODEL_DIR = "models"

    fun isValidGguf(file: File): Boolean =
        file.isFile && file.name.endsWith(".gguf", ignoreCase = true) && file.length() > 0

    fun resolveModel(candidates: List<File>): File? =
        candidates.filter { isValidGguf(it) }.maxByOrNull { it.length() }

    /** Bundled asset copies land in filesDir/models at first launch. */
    fun bundledCopyDir(filesDir: File): File = File(filesDir, ASSETS_MODEL_DIR)
}
