package com.phonelm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelLocatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeGguf(name: String, sizeBytes: Long): File {
        val f = tmp.root.resolve(name)
        f.writeBytes(ByteArray(sizeBytes.toInt()))
        return f
    }

    @Test
    fun `valid gguf recognized by extension and nonzero size`() {
        assertTrue(ModelLocator.isValidGguf(makeGguf("model.gguf", 1024)))
    }

    @Test
    fun `empty file rejected`() {
        assertFalse(ModelLocator.isValidGguf(makeGguf("empty.gguf", 0)))
    }

    @Test
    fun `non-gguf extension rejected`() {
        assertFalse(ModelLocator.isValidGguf(makeGguf("model.bin", 1024)))
    }

    @Test
    fun `directory named gguf rejected`() {
        val dir = tmp.newFolder("fake.gguf")
        assertFalse(ModelLocator.isValidGguf(dir))
    }

    @Test
    fun `no candidates resolves to null`() {
        assertNull(ModelLocator.resolveModel(emptyList()))
        assertNull(ModelLocator.resolveModel(listOf(makeGguf("x.bin", 5))))
    }

    @Test
    fun `largest valid gguf wins regardless of order`() {
        val small = makeGguf("small.gguf", 100)
        val big = makeGguf("big.gguf", 900_000)
        assertEquals(big, ModelLocator.resolveModel(listOf(small, big)))
        assertEquals(big, ModelLocator.resolveModel(listOf(big, small)))
    }

    @Test
    fun `bundled copy dir lives under filesDir models`() {
        val filesDir = tmp.newFolder("files")
        assertEquals(File(filesDir, "models"), ModelLocator.bundledCopyDir(filesDir))
    }
}
