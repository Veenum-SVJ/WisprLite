package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RetentionDeviceTest {
    @Test fun audioExpiryOnAndroidKeepsUnexpiredAndUnrelatedFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "retention-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val now = System.currentTimeMillis()
        fun fixture(name: String, age: Long) = File(directory, name).apply {
            writeText("synthetic fixture")
            assertTrue(setLastModified(now - age))
        }
        try {
            val old = fixture("old.wav", 8 * RetentionPolicy.DAY_MILLIS)
            val recent = fixture("recent.wav", RetentionPolicy.DAY_MILLIS)
            val abandoned = fixture("abandoned.tmp", 0)
            val unrelated = fixture("keep.txt", 30 * RetentionPolicy.DAY_MILLIS)
            assertEquals(2, RetentionPolicy.sweepAudio(directory, 7, now))
            assertFalse(old.exists())
            assertFalse(abandoned.exists())
            assertTrue(recent.exists())
            assertTrue(unrelated.exists())
            assertEquals(1, RetentionPolicy.sweepAudio(directory, 0, now))
            assertFalse(recent.exists())
            assertTrue(unrelated.exists())
        } finally { directory.listFiles().orEmpty().forEach { it.delete() }; directory.delete() }
    }
}
