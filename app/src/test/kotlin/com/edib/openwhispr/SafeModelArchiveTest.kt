package com.edib.openwhispr

import org.apache.commons.compress.archivers.tar.*
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SafeModelArchiveTest {
    private fun archive(file: File, entries: Map<String, String>) {
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { tar ->
            entries.forEach { (name, text) ->
                val bytes = text.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
                tar.write(bytes); tar.closeArchiveEntry()
            }
        }
    }
    @Test fun `rejects sibling prefix traversal`() {
        val root = Files.createTempDirectory("wispr-archive").toFile()
        try {
            val input = File(root, "test.tar.bz2")
            archive(input, mapOf("../models-evil/escaped.txt" to "bad"))
            assertThrows(IllegalArgumentException::class.java) { SafeModelArchive.extract(input, File(root, "models")) }
            assertFalse(File(root, "models-evil/escaped.txt").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun `failed validation never publishes partial model`() {
        val root = Files.createTempDirectory("wispr-install").toFile()
        try {
            val input = File(root, "test.tar.bz2")
            archive(input, mapOf("model/tokens.txt" to "tokens"))
            assertThrows(IllegalArgumentException::class.java) {
                SafeModelArchive.install(input, File(root, "models"), "model") { File(it, "encoder.onnx").isFile }
            }
            assertFalse(File(root, "models/model").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun `complete models are promoted after validation`() {
        val root = Files.createTempDirectory("wispr-install").toFile()
        try {
            val input = File(root, "test.tar.bz2")
            archive(input, mapOf("model/tokens.txt" to "tokens", "model/encoder.onnx" to "model"))
            SafeModelArchive.install(input, File(root, "models"), "model") { File(it, "encoder.onnx").isFile }
            assertEquals("model", File(root, "models/model/encoder.onnx").readText())
            assertFalse(File(root, "models").listFiles().orEmpty().any { it.name.startsWith(".staging-") })
        } finally { root.deleteRecursively() }
    }
}
