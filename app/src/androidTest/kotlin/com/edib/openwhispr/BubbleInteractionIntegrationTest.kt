package com.edib.openwhispr

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BubbleInteractionIntegrationTest {
    @Test fun tapHoldCancellationAndEdgeDockingUseTheRealBubbleListener() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val keys = listOf("use_local", "service_master_enabled", BubbleSize.X_FRACTION_KEY, BubbleSize.Y_FRACTION_KEY)
        val previous = keys.associateWith { prefs.all[it] }
        val deadline = SystemClock.uptimeMillis() + 45_000
        while (SystemClock.uptimeMillis() < deadline) {
            val current = WhisperAccessibilityService.instance
            if (current != null && current.javaClass.getDeclaredField("overlayView").apply { isAccessible = true }.get(current) != null) break
            SystemClock.sleep(100)
        }
        val service = requireNotNull(WhisperAccessibilityService.instance)
        fun field(name: String) = service.javaClass.getDeclaredField(name).apply { isAccessible = true }
        fun state() = field("state").get(service).toString()
        val overlay = field("overlayView").get(service) as View
        fun touch(action: Int, x: Float = 20f, y: Float = 20f) {
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, action, x, y, 0)
                try { assertTrue("Bubble rejected touch action $action", overlay.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
        }
        val before = HistoryRepository(context).use { it.totals().recordings }
        try {
            // All captures are interrupted, never stopped for transcription: no API upload.
            prefs.edit().putBoolean("use_local", false).putBoolean("service_master_enabled", true).commit()
            touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
            assertEquals("RECORDING", state())
            instrumentation.waitForIdleSync()
            val recordingParams = field("layoutParams").get(service) as WindowManager.LayoutParams
            assertTrue("Recording expands the logo into a compact capsule", recordingParams.width > recordingParams.height)
            instrumentation.runOnMainSync { service.onInterrupt() }
            instrumentation.waitForIdleSync()
            touch(MotionEvent.ACTION_DOWN)
            val holdDeadline = SystemClock.uptimeMillis() + 3000
            while (state() != "RECORDING" && SystemClock.uptimeMillis() < holdDeadline) SystemClock.sleep(50)
            assertEquals("RECORDING", state())
            touch(MotionEvent.ACTION_CANCEL)
            assertEquals("IDLE", state())
            touch(MotionEvent.ACTION_DOWN)
            instrumentation.runOnMainSync { service.onInterrupt() }
            SystemClock.sleep(450)
            assertEquals("Interrupted pending hold must not start the microphone later", "IDLE", state())
            touch(MotionEvent.ACTION_DOWN)
            touch(MotionEvent.ACTION_MOVE, -100f, -100f)
            val dropped = field("layoutParams").get(service) as WindowManager.LayoutParams
            val expectedX = BubbleSize.snappedX(dropped.x, dropped.width, context.resources.displayMetrics.widthPixels, (8 * context.resources.displayMetrics.density).toInt())
            val expectedY = dropped.y
            touch(MotionEvent.ACTION_UP, -100f, -100f)
            assertEquals("Drag release docks to the nearest horizontal edge", expectedX, dropped.x)
            assertEquals(expectedY, dropped.y)
            assertTrue(prefs.contains(BubbleSize.X_FRACTION_KEY))
            assertTrue(prefs.contains(BubbleSize.Y_FRACTION_KEY))
            assertEquals("IDLE", state())
            assertEquals(before, HistoryRepository(context).use { it.totals().recordings })
        } finally {
            instrumentation.runOnMainSync { service.onInterrupt() }
            prefs.edit().apply {
                for ((key, value) in previous) when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    else -> remove(key)
                }
            }.commit()
            service.refreshPosition()
        }
    }
}
