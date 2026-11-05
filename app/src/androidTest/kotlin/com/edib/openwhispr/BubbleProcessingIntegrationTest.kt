package com.edib.openwhispr

import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BubbleProcessingIntegrationTest {
    @Test fun actualFloatingLoadingIndicatorKeepsSchedulingVisibleFrames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("openwhispr", 0)
        val previousMaster = prefs.all["service_master_enabled"]
        prefs.edit().putBoolean("service_master_enabled", true).commit()
        val deadline = SystemClock.uptimeMillis() + 45_000
        while (SystemClock.uptimeMillis() < deadline) {
            val current = WhisperAccessibilityService.instance
            if (current != null && current.javaClass.getDeclaredField("overlayView").apply { isAccessible = true }.get(current) != null) break
            SystemClock.sleep(100)
        }
        val service = requireNotNull(WhisperAccessibilityService.instance)
        fun field(name: String) = service.javaClass.getDeclaredField(name).apply { isAccessible = true }
        val logo = field("dottedLogo").get(service) as DottedVoiceDrawable
        val overlay = field("overlayView").get(service) as View
        val state = field("state")
        val frames = AtomicInteger()
        var original: Drawable.Callback? = null
        try {
            // Synthetic processing only: no recording, transcription or API call.
            instrumentation.runOnMainSync {
                state.set(service, state.type.enumConstants!!.first { it.toString() == "TRANSCRIBING" })
                service.javaClass.getDeclaredMethod("setRecordingLook", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(service, false)
                service.javaClass.getDeclaredMethod("updateOverlayVisibility").apply { isAccessible = true }.invoke(service)
            }
            val visibleDeadline = SystemClock.uptimeMillis() + 6_000
            while (SystemClock.uptimeMillis() < visibleDeadline) {
                var visible = false
                instrumentation.runOnMainSync { visible = overlay.visibility == View.VISIBLE && overlay.alpha > .1f }
                if (visible) break
                SystemClock.sleep(50)
            }
            instrumentation.runOnMainSync {
                assertEquals(View.VISIBLE, overlay.visibility)
                original = requireNotNull(logo.callback)
                logo.callback = object : Drawable.Callback {
                    override fun invalidateDrawable(who: Drawable) { original!!.invalidateDrawable(who) }
                    override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) {
                        frames.incrementAndGet(); original!!.scheduleDrawable(who, what, whenMillis)
                    }
                    override fun unscheduleDrawable(who: Drawable, what: Runnable) { original!!.unscheduleDrawable(who, what) }
                }
                logo.processing = true
            }
            SystemClock.sleep(1_000)
            assertTrue("The real visible loading drawable must schedule repeated frames, including with animator_duration_scale=0; scheduled=${frames.get()}", frames.get() >= 3)
        } finally {
            instrumentation.runOnMainSync {
                logo.processing = false
                if (original != null) logo.callback = original
                service.onInterrupt()
            }
            prefs.edit().apply { if (previousMaster is Boolean) putBoolean("service_master_enabled", previousMaster) else remove("service_master_enabled") }.commit()
        }
    }
}
