package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Provision the known model archive and synthetic WAV into app-private files before running. */
@RunWith(AndroidJUnit4::class)
class OfflineSpeechSmokeTest {
    @Test fun multilingualBaseTranscribesSyntheticEnglishOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File(context.filesDir, "speech-model-test.tar.bz2")
        val audio = File(context.filesDir, "english-test.wav")
        assumeTrue("Requires explicitly provisioned synthetic speech and model fixture", archive.isFile && audio.isFile)
        val model = MODEL_CATALOG.single { it.archive == "sherpa-onnx-whisper-base" }
        if (!ModelDownloader.isInstalled(context, model)) {
            SafeModelArchive.install(archive, File(context.filesDir, "models"), model.archive, LocalTranscriber::isModelComplete)
        }
        val bytes = audio.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
        buffer.position(12)
        var pcm: ByteArray? = null
        while (buffer.remaining() >= 8) {
            val name = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            val size = buffer.int
            require(size >= 0 && size <= buffer.remaining())
            val start = buffer.position()
            if (name == "fmt ") {
                require(buffer.short.toInt() == 1 && buffer.short.toInt() == 1)
                require(buffer.int == 16000)
                buffer.position(start + 14)
                require(buffer.short.toInt() == 16)
            }
            if (name == "data") pcm = ByteArray(size).also { buffer.get(it) }
            buffer.position(start + size + size % 2)
        }
        val data = requireNotNull(pcm)
        val samples = FloatArray(data.size / 2) { index ->
            val sample = ((data[index * 2 + 1].toInt() shl 8) or (data[index * 2].toInt() and 255)).toShort()
            sample / 32768f
        }
        val start = System.nanoTime()
        val transcript = requireNotNull(LocalTranscriber.create(context, model.archive, "en")).use { it.transcribe(samples) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        println("Synthetic English / Whisper Base: ${samples.size / 16000.0}s audio, ${elapsedMs}ms processing; $transcript")
        val normalized = transcript.lowercase(java.util.Locale.ROOT)
        assertTrue(transcript, normalized.contains("english"))
        assertTrue(transcript, normalized.contains("document"))
        assertTrue(transcript, normalized.contains("tomorrow"))
    }
}
