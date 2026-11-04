package com.edib.openwhispr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal object UpdateInstaller {
    /** Called on a worker, including when reopening a downloaded update. */
    fun validate(context: Context, file: File, release: AvailableRelease) {
        val apk = requireNotNull(release.apk)
        require(file.canonicalFile.parentFile == File(context.cacheDir, "app-updates").canonicalFile)
        if (file.length() != apk.size) throw IOException("Downloaded update is incomplete. Please download again.")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer = ByteArray(32 * 1024); while (true) {
            val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
        } }
        if (digest.digest().joinToString("") { "%02x".format(it) } != apk.sha256) throw IOException("Update checksum did not match. Please download again.")
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("This download is not a valid Android update.")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.packageName != context.packageName) throw IOException("This APK belongs to a different app.")
        if (archive.longVersionCode <= installed.longVersionCode) throw IOException("This update is not newer than the installed app. Check again.")
        fun signers(info: android.content.pm.PackageInfo) = info.signingInfo?.apkContentsSigners?.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }?.toSet().orEmpty()
        val expected = signers(installed)
        if (expected.isEmpty() || signers(archive) != expected) throw IOException("Update signature does not match OpenWisper.")
    }
    fun intent(context: Context, file: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", file), "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
