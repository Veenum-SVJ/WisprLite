package com.edib.openwhispr

import android.os.Debug
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit research fixture; never installs/activates a catalog model or changes preferences. */
@RunWith(AndroidJUnit4::class)
class LyngualNativeEvaluationTest {
    @Test fun measurePinnedInt8ModelWithExistingAndroidRuntime() {
        require(InstrumentationRegistry.getArguments().getString("confirmedLocalTrial") == "lyngual_public_int8")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "lyngual-eval-pinned")
        val hashes = mapOf(
            "lyngual-small-yoruba-encoder.int8.onnx" to "2aa6f2e1fd80051097a60d11b6d8716dafb92fe6c0e71b8498db58649062f794",
            "lyngual-small-yoruba-decoder.int8.onnx" to "3d493a26e5690d31e616fdc0e41fae9308f8847df5bbac80bb11617af738b9a1",
            "lyngual-small-yoruba-tokens.txt" to "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
            "yoruba.wav" to "f99f74cd579a9e63dfdde864856b6111426019dbd345557584c8519bc95306bb",
            "pidgin.wav" to "35d76258b77a840c166d698696d08144e2e1ac9ca1f28c738a1b42f636ff47e4",
        )
        for ((name, expected) in hashes) {
            val file = File(directory, name)
            assertTrue("Provision the pinned research fixture $name", file.isFile)
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val chunk = ByteArray(65536)
                while (true) { val count = input.read(chunk); if (count < 0) break; digest.update(chunk, 0, count) }
            }
            assertEquals("Research fixture must match its verified conversion", expected,
                digest.digest().joinToString("") { "%02x".format(it) })
        }
        assertTrue("Existing local runtime must be available", LocalTranscriber.nativeEngineAvailable)
        val results = JSONArray()
        val power = context.getSystemService(PowerManager::class.java)
        for ((name, language) in listOf("yoruba.wav" to "yo", "pidgin.wav" to "en")) {
            val samples = samples(File(directory, name))
            val beforeThermal = power.currentThermalStatus
            val started = android.os.SystemClock.elapsedRealtime()
            val config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(directory, "lyngual-small-yoruba-encoder.int8.onnx").absolutePath,
                    decoder = File(directory, "lyngual-small-yoruba-decoder.int8.onnx").absolutePath,
                ).apply { this.language = language },
                tokens = File(directory, "lyngual-small-yoruba-tokens.txt").absolutePath,
                numThreads = 2,
                modelType = "whisper",
            ))
            val recognizer = OfflineRecognizer(assetManager = null, config = config)
            try {
                val loadMillis = android.os.SystemClock.elapsedRealtime() - started
                val loadedPss = Debug.getPss()
                val stream = recognizer.createStream()
                try {
                    val decodeStarted = android.os.SystemClock.elapsedRealtime()
                    stream.acceptWaveform(samples, 16000)
                    recognizer.decode(stream)
                    val text = recognizer.getResult(stream).text.trim()
                    val decodeMillis = android.os.SystemClock.elapsedRealtime() - decodeStarted
                    results.put(JSONObject().put("fixture", name).put("sha256", hashes.getValue(name))
                        .put("language", if (language == "yo") "YORUBA" else "PIDGIN")
                        .put("model", "LyngualLabs/whisper-small-yoruba int8 @ ac05aac79a0a6fa522f75055a6af1523eb7952b6")
                        .put("elapsed_ms", decodeMillis).put("load_ms", loadMillis)
                        .put("pss_kb_after_load", loadedPss).put("thermal_before", beforeThermal)
                        .put("thermal_after", power.currentThermalStatus)
                        .put("error", JSONObject.NULL).put("text", text))
                    File(directory, "native-results.json").writeText(
                        JSONObject().put("provider", "lyngual-int8-android").put("results", results).toString(), Charsets.UTF_8)
                    assertTrue("Native runtime returned no text for public speech", text.isNotBlank())
                } finally { stream.release() }
            } finally { recognizer.release() }
        }
        assertEquals(2, results.length())
        println("LYNGUAL_NATIVE_EVALUATION_RECORDED: two public clips; accuracy requires separate scoring")
    }

    private fun samples(file: File): FloatArray {
        val bytes = file.readBytes()
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(12)
        var pcm: ByteArray? = null
        var formatValid = false
        while (buffer.remaining() >= 8) {
            val kind = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            val size = buffer.int
            require(size >= 0 && size <= buffer.remaining())
            val start = buffer.position()
            if (kind == "fmt ") {
                require(size >= 16)
                formatValid = buffer.short.toInt() == 1 && buffer.short.toInt() == 1 && buffer.int == 16000
                buffer.position(start + 14)
                formatValid = formatValid && buffer.short.toInt() == 16
            }
            if (kind == "data") pcm = ByteArray(size).also { buffer.get(it) }
            buffer.position(start + size + size % 2)
        }
        val data = requireNotNull(pcm)
        require(formatValid && data.size % 2 == 0)
        val audio = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(data.size / 2) { audio.short / 32768f }
    }
}
