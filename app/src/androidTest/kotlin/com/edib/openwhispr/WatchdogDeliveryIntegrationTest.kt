package com.edib.openwhispr

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Tests actual worker delivery, not whether MIUI will schedule a killed process. */
@RunWith(AndroidJUnit4::class)
class WatchdogDeliveryIntegrationTest {
    @Test fun disconnectedWarningIsDeliveredAndClearedWhenPaused() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
        val hadMaster = prefs.contains("service_master_enabled")
        val master = prefs.getBoolean("service_master_enabled", true)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val manager = WorkManager.getInstance(context)
        val requests = mutableListOf<java.util.UUID>()
        fun check(whileRunning: (() -> Unit)? = null) {
            val request = OneTimeWorkRequestBuilder<ServiceWatchdog.Check>().build()
            requests += request.id
            manager.enqueue(request).result.get(10, TimeUnit.SECONDS)
            val deadline = android.os.SystemClock.uptimeMillis() + 30_000
            var state: WorkInfo.State? = null
            var actionApplied = false
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                state = manager.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)?.state
                if (state == WorkInfo.State.RUNNING && !actionApplied && whileRunning != null) {
                    whileRunning()
                    actionApplied = true
                }
                if (state?.isFinished == true) break
                Thread.sleep(100)
            }
            assertEquals("Recovery check must actually execute", WorkInfo.State.SUCCEEDED, state)
            if (whileRunning != null) assertTrue("Must exercise the in-flight worker", actionApplied)
        }
        fun warningIs(expected: Boolean, message: String) {
            val deadline = android.os.SystemClock.uptimeMillis() + 2_000
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                if (notifications.activeNotifications.any { it.id == 4201 } == expected) return
                Thread.sleep(50)
            }
            assertEquals(message, expected, notifications.activeNotifications.any { it.id == 4201 })
        }
        try {
            assertTrue("Requires the authorized service toggle enabled", ServiceWatchdog.isEnabledInSettings(context))
            assumeTrue("Disconnected-worker fixture is unavailable when the OS automatically rebinds", WhisperAccessibilityService.instance == null)
            assertTrue("Notification permission is required for delivery verification", notifications.areNotificationsEnabled())
            prefs.edit().putBoolean("service_master_enabled", true).commit()
            notifications.cancel(4201)
            check()
            warningIs(true, "Disconnected warning must be visible, not merely have a channel")
            prefs.edit().putBoolean("service_master_enabled", false).commit()
            check()
            warningIs(false, "Intentionally paused dictation must clear the stale recovery warning")
            prefs.edit().putBoolean("service_master_enabled", true).commit()
            check {
                // Let the worker enter its settle delay before the user pauses.
                Thread.sleep(200)
                prefs.edit().putBoolean("service_master_enabled", false).commit()
            }
            warningIs(false, "Pausing during the settle delay must not recreate the warning")
        } finally {
            try {
                for (id in requests) manager.cancelWorkById(id).result.get(5, TimeUnit.SECONDS)
            } finally {
                try { notifications.cancel(4201) } finally {
                    prefs.edit().apply { if (hadMaster) putBoolean("service_master_enabled", master) else remove("service_master_enabled") }.commit()
                }
            }
        }
    }
}
