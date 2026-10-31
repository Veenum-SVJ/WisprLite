package com.edib.openwhispr

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ModelStorageTest {
    private fun fixture(action: (File) -> Unit) {
        val directory = Files.createTempDirectory("whisper-staging-test").toFile()
        try { action(directory) } finally { directory.deleteRecursively() }
    }
    @Test fun completeQuantizedPairRetainsItsWeightsTokensAndOtherFiles() = fixture { dir ->
        for (name in listOf("tiny-encoder.int8.onnx", "tiny-decoder.int8.onnx", "tiny-tokens.txt", "keep.txt")) File(dir, name).writeText(name)
        File(dir, "tiny-encoder.onnx").writeBytes(ByteArray(11))
        File(dir, "tiny-decoder.onnx").writeBytes(ByteArray(17))
        assertEquals(28L, ModelStorage.compactQuantizedWhisperPack(dir))
        assertFalse(File(dir, "tiny-encoder.onnx").exists())
        assertFalse(File(dir, "tiny-decoder.onnx").exists())
        assertEquals("tiny-encoder.int8.onnx", File(dir, "tiny-encoder.int8.onnx").readText())
        assertEquals("tiny-decoder.int8.onnx", File(dir, "tiny-decoder.int8.onnx").readText())
        assertEquals("tiny-tokens.txt", File(dir, "tiny-tokens.txt").readText())
        assertEquals("keep.txt", File(dir, "keep.txt").readText())
    }
    @Test fun missingOrEmptyQuantizedFileKeepsAllFallbackWeights() = fixture { dir ->
        File(dir, "tiny-encoder.int8.onnx").writeText("quantized")
        File(dir, "tiny-encoder.onnx").writeText("encoder fallback")
        File(dir, "tiny-decoder.onnx").writeText("decoder fallback")
        assertEquals(0L, ModelStorage.compactQuantizedWhisperPack(dir))
        File(dir, "tiny-decoder.int8.onnx").writeBytes(byteArrayOf())
        assertEquals(0L, ModelStorage.compactQuantizedWhisperPack(dir))
        assertEquals("encoder fallback", File(dir, "tiny-encoder.onnx").readText())
        assertEquals("decoder fallback", File(dir, "tiny-decoder.onnx").readText())
    }
    @Test fun mismatchedQuantizedPrefixesDoNotPruneFallbackFiles() = fixture { dir ->
        for (name in listOf("tiny-encoder.int8.onnx", "base-decoder.int8.onnx", "tiny-encoder.onnx", "base-decoder.onnx")) File(dir, name).writeText(name)
        assertEquals(0L, ModelStorage.compactQuantizedWhisperPack(dir))
        assertEquals(4, dir.listFiles()!!.size)
    }
}
