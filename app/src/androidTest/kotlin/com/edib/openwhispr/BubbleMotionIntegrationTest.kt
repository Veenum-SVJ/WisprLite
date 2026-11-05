package com.edib.openwhispr

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BubbleMotionIntegrationTest {
    @Test fun monochromeLogoMorphsFromEitherEdgeAndHoldAndProcessingAreDistinct() {
        val drawable = DottedVoiceDrawable().apply { setBounds(0, 0, 320, 160) }
        val images = mutableListOf<Bitmap>()
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "bubble-motion").apply { mkdirs() }
        fun capture(name: String): Bitmap = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888).also {
            drawable.draw(Canvas(it)); images += it
            File(output, "$name.png").outputStream().use { stream -> it.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        }
        fun occupied(bitmap: Bitmap, from: Int, until: Int): Int = (from until until).sumOf { x ->
            (0 until bitmap.height).count { y -> Color.alpha(bitmap.getPixel(x, y)) > 10 }
        }
        try {
            val idle = capture("idle-left")
            assertEquals("Idle logo stays at its docked edge", 0, occupied(idle, 160, 320))
            for (x in 0 until idle.width) for (y in 0 until idle.height) {
                val pixel = idle.getPixel(x, y)
                assertEquals("Floating logo must be monochrome", Color.red(pixel), Color.green(pixel))
                assertTrue(kotlin.math.abs(Color.red(pixel) - Color.blue(pixel)) <= 2)
            }
            drawable.expansion = .5f
            val halfway = capture("expanding-left")
            assertTrue("Surface must grow continuously from the original circle", occupied(halfway, 160, 320) > 0)
            drawable.expansion = 1f; drawable.recording = true; drawable.voiceLevel = .65f
            val tap = capture("tap-recording")
            assertTrue(occupied(tap, 240, 320) > occupied(halfway, 240, 320))
            drawable.holdRecording = true
            val hold = capture("hold-recording")
            assertFalse("Hold must have a visually distinct outline and release cue", tap.sameAs(hold))
            assertEquals("Hold uses a circular live-logo silhouette, not the tap capsule", 0, occupied(hold, 160, 320))
            drawable.recording = false; drawable.expansion = 0f; drawable.processing = true
            val processing = capture("processing")
            assertFalse("Processing changes the waveform inside the logo", idle.sameAs(processing))
            assertEquals("Processing must remain inside the circular logo", 0, occupied(processing, 160, 320))
            SystemClock.sleep(230)
            assertFalse("Processing must remain animated even when system transitions are disabled", processing.sameAs(capture("processing-next")))
            drawable.processing = false; drawable.dockedLeft = false
            val right = capture("idle-right")
            assertEquals("Right dock must not jump to the left during collapse", 0, occupied(right, 0, 160))
        } finally { drawable.processing = false; images.forEach { it.recycle() } }
    }
}
