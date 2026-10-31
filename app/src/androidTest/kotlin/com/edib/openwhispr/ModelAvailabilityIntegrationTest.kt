package com.edib.openwhispr

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ModelAvailabilityIntegrationTest {
    @Suppress("UNCHECKED_CAST") private fun activeDownloads(): MutableSet<String> =
        ModelDownloader.javaClass.getDeclaredField("activeDownloads").apply { isAccessible = true }.get(ModelDownloader) as MutableSet<String>

    @Test fun insufficientSpaceFailsBeforeDownloadingAndReleasesBusyState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "download-space-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val fakeFiles = object : File(root, "files") { override fun getUsableSpace(): Long = 0L }
        val limited = object : ContextWrapper(context) {
            override fun getFilesDir(): File = fakeFiles
            override fun getCacheDir(): File = root
        }
        val model = MODEL_CATALOG.single { it.archive == "sherpa-onnx-whisper-small" }
        val states = java.util.concurrent.CopyOnWriteArrayList<DownloadState>()
        val finished = CountDownLatch(1)
        try {
            ModelDownloader.download(limited, model) { states.add(it); if (it is DownloadState.Error) finished.countDown() }
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(1, states.size)
            assertTrue((states.single() as DownloadState.Error).message.contains("storage", ignoreCase = true))
            val deadline = System.currentTimeMillis() + 2_000
            while (model.archive in activeDownloads() && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertFalse(model.archive in activeDownloads())
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally { root.listFiles().orEmpty().forEach { it.delete() }; root.delete() }
    }

    @Test fun repeatedDownloadTapIsRejectedWithoutStartingAnotherTransfer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = Model("Test only", "wisprlite-lock-fixture", 1, 0, ModelArchitecture.WHISPER)
        val active = activeDownloads()
        assertTrue(active.add(model.archive))
        try {
            var state: DownloadState? = null
            ModelDownloader.download(context, model) { state = it }
            assertTrue(state is DownloadState.Error)
            assertTrue((state as DownloadState.Error).message.contains("already downloading"))
        } finally { active.remove(model.archive) }
    }

    @Test fun missingLocalModelDoesNotStartMicrophoneOrFallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val oldModel = prefs.getString("model_name", null)
        val oldBooleans = listOf("use_local", "service_master_enabled").associateWith { key ->
            if (prefs.contains(key)) prefs.getBoolean(key, true) else null
        }
        val deadline = System.currentTimeMillis() + 45_000
        while (WhisperAccessibilityService.instance == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val service = requireNotNull(WhisperAccessibilityService.instance)
        fun field(name: String) = service.javaClass.getDeclaredField(name).apply { isAccessible = true }
        try {
            prefs.edit().putBoolean("use_local", true).putBoolean("service_master_enabled", true)
                .putString("model_name", "wisprlite-missing-model-fixture").commit()
            service.reloadModel()
            val loadDeadline = System.currentTimeMillis() + 30_000
            while ((field("localModelLoading").getBoolean(service) || field("localTranscriber").get(service) != null) &&
                System.currentTimeMillis() < loadDeadline) Thread.sleep(100)
            assertNull(field("localTranscriber").get(service))
            instrumentation.runOnMainSync {
                service.javaClass.getDeclaredMethod("startRecording").apply { isAccessible = true }.invoke(service)
                assertNull(field("audioRecord").get(service))
                assertNull(field("transcriptionCall").get(service))
                assertEquals("IDLE", field("state").get(service)?.toString())
            }
        } finally {
            instrumentation.runOnMainSync { service.onInterrupt() }
            val edit = prefs.edit()
            if (oldModel == null) edit.remove("model_name") else edit.putString("model_name", oldModel)
            for ((key, value) in oldBooleans) if (value == null) edit.remove(key) else edit.putBoolean(key, value)
            edit.commit()
            service.reloadModel()
        }
    }
}
