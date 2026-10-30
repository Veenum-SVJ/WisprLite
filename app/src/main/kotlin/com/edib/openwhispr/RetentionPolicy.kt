package com.edib.openwhispr

import java.io.File

object RetentionPolicy {
    const val DAY_MILLIS = 86400000L

    /** Only touches known audio files in this dedicated, app-private directory. */
    fun sweepAudio(directory: File, days: Int, now: Long): Int {
        require(days in setOf(0, 1, 7, 30)) { "Unsupported audio retention" }
        val root = directory.canonicalFile
        var deleted = 0
        root.listFiles()?.forEach { file ->
            if (file.isFile && file.canonicalFile.parentFile == root &&
                (file.extension == "tmp" || (file.extension == "wav" &&
                    (days == 0 || file.lastModified() <= now - days * DAY_MILLIS)))) {
                if (file.delete()) deleted++
            }
        }
        return deleted
    }
}
