package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RetentionPolicyTest {
    @Test fun `disabled audio retention removes recordings but not unrelated files`() {
        val root = Files.createTempDirectory("wisprlite-retention").toFile()
        try {
            val audio = File(root, "recording.wav").apply { writeText("audio") }
            val other = File(root, "preferences.json").apply { writeText("settings") }
            RetentionPolicy.sweepAudio(root, 0, System.currentTimeMillis())
            assertFalse(audio.exists())
            assertTrue(other.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun `expiry keeps recent files and removes abandoned temporary audio`() {
        val root = Files.createTempDirectory("wisprlite-retention").toFile()
        try {
            val now = 1000000000L
            val old = File(root, "old.wav").apply { writeText("audio"); setLastModified(now - 8 * 86400000L) }
            val recent = File(root, "recent.wav").apply { writeText("audio"); setLastModified(now) }
            val partial = File(root, "abandoned.tmp").apply { writeText("audio") }
            RetentionPolicy.sweepAudio(root, 7, now)
            assertFalse(old.exists())
            assertFalse(partial.exists())
            assertTrue(recent.exists())
        } finally { root.deleteRecursively() }
    }
}
