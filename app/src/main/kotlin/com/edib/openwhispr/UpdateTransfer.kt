package com.edib.openwhispr

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.security.MessageDigest

internal object UpdateTransfer {
    const val MAX_BYTES = 500L * 1024 * 1024
    fun url(release: AvailableRelease): String {
        require(ReleaseVersion.parse(release.tag) == release.version)
        val apk = requireNotNull(release.apk)
        require(apk.name == "OpenWisper-${release.version.major}.${release.version.minor}.${release.version.patch}-android.apk")
        return "https://github.com/Veenum-SVJ/WisprLite/releases/download/${release.tag}/${apk.name}"
    }
    fun copy(input: InputStream, output: OutputStream, apk: ReleaseApk, cancelled: () -> Boolean, progress: (Int) -> Unit) {
        require(apk.size in 1..MAX_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        var last = -1
        while (true) {
            if (cancelled()) throw IOException("Download cancelled")
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > apk.size) throw IOException("Update size does not match the release. Check again.")
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
            val percent = (total * 100 / apk.size).toInt()
            if (percent != last) { progress(percent); last = percent }
        }
        if (total != apk.size) throw IOException("Download was interrupted. Please retry.")
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(apk.sha256, ignoreCase = true)) throw IOException("Update checksum did not match. Please retry.")
    }
}
