package com.edib.openwhispr

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run separately after ADB revokes microphone permission; test-device.ps1 restores the grant. */
@RunWith(AndroidJUnit4::class)
class PermissionDeniedIntegrationTest {
    @Test fun deniedMicrophoneLeavesServiceIdleWithoutAudioOrTranscript() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        val deadline = System.currentTimeMillis() + 45_000
        while (WhisperAccessibilityService.instance == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val service = requireNotNull(WhisperAccessibilityService.instance) { "Rebind accessibility service through ADB" }
        val before = HistoryRepository(context).use { it.totals(0).recordings }
        instrumentation.runOnMainSync {
            service.javaClass.getDeclaredMethod("startRecording").apply { isAccessible = true }.invoke(service)
            for (name in listOf("audioRecord", "pcmStream")) {
                assertNull(name, service.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(service))
            }
            assertEquals("IDLE", service.javaClass.getDeclaredField("state").apply { isAccessible = true }.get(service)?.toString())
        }
        assertEquals(before, HistoryRepository(context).use { it.totals(0).recordings })
    }
}
