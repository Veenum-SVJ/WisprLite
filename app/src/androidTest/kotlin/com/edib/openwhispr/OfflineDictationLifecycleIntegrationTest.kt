package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit synthetic-fixture trial through the actual service, recorder and native model. */
@RunWith(AndroidJUnit4::class)
class OfflineDictationLifecycleIntegrationTest {
    @Test fun offlineSuccessAndNoSpeechFailurePersistNoAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val readyName = requireNotNull(InstrumentationRegistry.getArguments().getString("runnerReadyFile")) { "Use test-device.ps1 -IncludeOfflineFixture" }
        require(readyName.matches(Regex("offline-ready-[a-f0-9]{32}\\.tmp")))
        val ready = File(context.cacheDir, readyName)
        val readyDeadline = System.currentTimeMillis() + 45_000
        while (!ready.isFile && System.currentTimeMillis() < readyDeadline) Thread.sleep(100)
        assertTrue("Runner must finish service rebinding and foreground the test field", ready.isFile)
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val model = MODEL_CATALOG.single { it.archive == "sherpa-onnx-whisper-tiny.en" }
        assertTrue("Provision the Tiny English fixture first", ModelDownloader.isInstalled(context, model))
        val audio = File(context.filesDir, "english-test.wav")
        assertTrue("Provision the known synthetic English fixture first", audio.isFile)
        val audioDirectory = File(context.filesDir, "recordings")
        assertTrue("Do not change retention while personal audio is present", audioDirectory.listFiles().orEmpty().none { it.extension in setOf("wav", "tmp") })
        val bytes = audio.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
        buffer.position(12)
        var pcm: ByteArray? = null
        while (buffer.remaining() >= 8) {
            val kind = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            val size = buffer.int
            require(size >= 0 && size <= buffer.remaining())
            val start = buffer.position()
            if (kind == "data") pcm = ByteArray(size).also { buffer.get(it) }
            buffer.position(start + size + size % 2)
        }
        val samples = requireNotNull(pcm)
        val keys = listOf("use_local", "use_post_processing", "audio_retention_days", "service_master_enabled", "language_profile", "model_name", "recording_sound")
        val original = prefs.all.filterKeys { it in keys }
        val deadline = System.currentTimeMillis() + 45_000
        while (System.currentTimeMillis() < deadline) {
            val current = WhisperAccessibilityService.instance
            var targetReady = false
            instrumentation.runOnMainSync {
                targetReady = current?.windows?.mapNotNull { it.root }?.any { root ->
                    try { root.packageName?.toString() == "com.veenum.wisprlite.test" } finally { root.recycle() }
                } == true
            }
            // The runner rebinds before opening the separate field. Waiting for
            // that field avoids holding an instance from before MIUI rebinding.
            if (targetReady && current === WhisperAccessibilityService.instance) break
            Thread.sleep(100)
        }
        val service = requireNotNull(WhisperAccessibilityService.instance) { "Enable the authorized service and foreground the empty test field" }
        fun field(name: String) = service.javaClass.getDeclaredField(name).apply { isAccessible = true }
        val before = HistoryRepository(context).use { it.totals() }
        val started = System.currentTimeMillis()
        var ownId: String? = null
        var retainedFixture: File? = null
        fun ownRecord(): DictationRecord? = HistoryRepository(context).use { repository ->
            repository.listBefore(limit = 100).singleOrNull { record ->
                record.createdAt >= started && record.original.lowercase().let { "short english dictation test" in it && "document" in it && "tomorrow" in it }
            }
        }
        try {
            prefs.edit().putBoolean("use_local", true).putBoolean("use_post_processing", false)
                .putInt("audio_retention_days", 0).putBoolean("service_master_enabled", true)
                .putBoolean("recording_sound", false).putString("language_profile", "ENGLISH").putString("model_name", model.archive).commit()
            // Synchronous initialization on the test worker thread; production loading is background work.
            service.javaClass.getDeclaredMethod("initLocalModel").apply { isAccessible = true }.invoke(service)
            assertNotNull(field("localTranscriber").get(service))
            instrumentation.runOnMainSync {
                service.javaClass.getDeclaredMethod("startRecording").apply { isAccessible = true }.invoke(service)
                assertNotNull(field("audioRecord").get(service))
                assertSame(WhisperAccessibilityService.instance, service)
                assertEquals("RECORDING", field("state").get(service).toString())
                // Recorder thread keeps its original stream. Only the known synthetic PCM is decoded.
                field("pcmStream").set(service, ByteArrayOutputStream().apply { write(samples) })
                service.javaClass.getDeclaredMethod("stopAndTranscribe").apply { isAccessible = true }.invoke(service)
                assertEquals("TRANSCRIBING", field("state").get(service).toString())
            }
            val resultDeadline = System.currentTimeMillis() + 60_000
            var record: DictationRecord? = null
            while (System.currentTimeMillis() < resultDeadline) {
                record = ownRecord()
                if (record != null) { ownId = record.id; break }
                Thread.sleep(100)
            }
            val result = requireNotNull(record) { "The actual local service did not persist its synthetic transcript" }
            assertEquals("Direct", result.mode)
            assertNull(result.cleaned)
            assertEquals(8_055L, result.speakingMillis)
            val idleDeadline = System.currentTimeMillis() + 10_000
            while (field("state").get(service).toString() != "IDLE" && System.currentTimeMillis() < idleDeadline) Thread.sleep(100)
            assertEquals("IDLE", field("state").get(service).toString())
            fun contains(node: android.view.accessibility.AccessibilityNodeInfo): Boolean =
                node.text?.toString()?.contains(result.original) == true || (0 until node.childCount).any { index ->
                    node.getChild(index)?.let { child -> try { contains(child) } finally { child.recycle() } } == true
                }
            var inserted = false
            val insertionDeadline = System.currentTimeMillis() + 5_000
            while (!inserted && System.currentTimeMillis() < insertionDeadline) {
                instrumentation.runOnMainSync {
                    inserted = service.windows.mapNotNull { it.root }.any { root ->
                        try { root.packageName?.toString() == "com.veenum.wisprlite.test" && contains(root) } finally { root.recycle() }
                    }
                }
                if (!inserted) Thread.sleep(100)
            }
            assertTrue("The actual decoded transcript must reach the separate test field", inserted)
            assertEquals(before.recordings + 1, HistoryRepository(context).use { it.totals().recordings })
            assertTrue("Successful offline dictation must not save audio", audioDirectory.listFiles().orEmpty().none { it.extension in setOf("wav", "tmp") })
            instrumentation.runOnMainSync {
                service.javaClass.getDeclaredMethod("startRecording").apply { isAccessible = true }.invoke(service)
                field("pcmStream").set(service, ByteArrayOutputStream())
                service.javaClass.getDeclaredMethod("stopAndTranscribe").apply { isAccessible = true }.invoke(service)
                assertNull(field("audioRecord").get(service))
                assertNull(field("pcmStream").get(service))
                assertEquals("IDLE", field("state").get(service).toString())
            }
            assertEquals(before.recordings + 1, HistoryRepository(context).use { it.totals().recordings })
            assertTrue("No-speech failure must not save audio", audioDirectory.listFiles().orEmpty().none { it.extension in setOf("wav", "tmp") })
            // Force a controlled engine failure after opting into audio saving.
            // This checks error recovery and the actual save path without a cloud call.
            (field("localTranscriber").get(service) as LocalTranscriber).close()
            prefs.edit().putInt("audio_retention_days", 1).commit()
            instrumentation.runOnMainSync {
                service.javaClass.getDeclaredMethod("startRecording").apply { isAccessible = true }.invoke(service)
                assertEquals("RECORDING", field("state").get(service).toString())
                field("pcmStream").set(service, ByteArrayOutputStream().apply { write(samples) })
                service.javaClass.getDeclaredMethod("stopAndTranscribe").apply { isAccessible = true }.invoke(service)
            }
            val encoded = WavWriter.encode(samples)
            val saveDeadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < saveDeadline) {
                retainedFixture = audioDirectory.listFiles().orEmpty().singleOrNull { it.extension == "wav" }
                if (retainedFixture?.length() == encoded.size.toLong() && field("state").get(service).toString() == "IDLE") break
                Thread.sleep(100)
            }
            assertEquals("Failed engine must recover to idle", "IDLE", field("state").get(service).toString())
            assertArrayEquals("Opt-in audio must contain only the synthetic capture", encoded, requireNotNull(retainedFixture).readBytes())
            assertEquals("Engine failure must not create a successful event", before.recordings + 1, HistoryRepository(context).use { it.totals().recordings })
            println("OFFLINE_SERVICE_RETENTION_VERIFIED: local success and insertion, no-speech failure, no default audio, opt-in synthetic WAV, native error recovery, one history event")
        } finally {
            try {
            instrumentation.runOnMainSync { service.onInterrupt() }
            retainedFixture?.let { assertTrue("Remove only the retained synthetic fixture", it.delete()) }
            ownId = ownId ?: ownRecord()?.id
            ownId?.let { id ->
                HistoryDatabase(context).use { helper ->
                    helper.writableDatabase.beginTransaction()
                    try {
                        helper.writableDatabase.delete("transcripts", "id=?", arrayOf(id))
                        helper.writableDatabase.delete("events", "id=?", arrayOf(id))
                        helper.writableDatabase.setTransactionSuccessful()
                    } finally { helper.writableDatabase.endTransaction() }
                }
            }
            } finally {
            prefs.edit().apply {
                keys.forEach { remove(it) }
                original.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                } }
            }.commit()
            service.reloadModel()
            }
        }
        assertEquals("Synthetic record cleanup must preserve prior counters", before, HistoryRepository(context).use { it.totals() })
    }
}
