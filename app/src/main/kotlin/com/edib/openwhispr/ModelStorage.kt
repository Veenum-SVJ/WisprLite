package com.edib.openwhispr

import java.io.File
import java.io.IOException

internal object ModelStorage {
    /** Only for a freshly validated staging directory; never compact the user's active model. */
    fun compactQuantizedWhisperPack(directory: File): Long {
        val files = directory.listFiles().orEmpty().filter { it.isFile && it.length() > 0 }
        val encoder = files.singleOrNull { it.name.endsWith("encoder.int8.onnx") } ?: return 0
        val decoder = files.singleOrNull { it.name.endsWith("decoder.int8.onnx") } ?: return 0
        if (encoder.name.removeSuffix("encoder.int8.onnx") != decoder.name.removeSuffix("decoder.int8.onnx")) return 0
        var removed = 0L
        for (quantized in listOf(encoder, decoder)) {
            val unused = File(directory, quantized.name.removeSuffix(".int8.onnx") + ".onnx")
            if (unused.isFile) {
                val bytes = unused.length()
                if (!unused.delete()) throw IOException("Couldn’t remove an unused model precision copy")
                removed += bytes
            }
        }
        return removed
    }
}
