package com.edib.openwhispr

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class UpdateInstallerDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun release(file: File): AvailableRelease {
        val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(file.path, 0))
        val version = requireNotNull(ReleaseVersion.parse(archive.versionName.orEmpty()))
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer = ByteArray(32768); while (true) {
            val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
        } }
        return AvailableRelease("v${archive.versionName}", version, ReleaseApk("OpenWisper-${version.major}.${version.minor}.${version.patch}-android.apk",
            file.length(), digest.digest().joinToString("") { "%02x".format(it) }))
    }
    @Test fun sameVersionIsRejectedAndInstallerOnlyReceivesANarrowContentUri() {
        val folder = File(context.cacheDir, "app-updates").apply { mkdirs() }
        val fixture = File(folder, "fixture-current.apk")
        val outside = File(context.filesDir, "updater-outside-fixture.txt")
        try {
            File(context.applicationInfo.sourceDir).copyTo(fixture, overwrite = true)
            val error = assertThrows(IOException::class.java) { UpdateInstaller.validate(context, fixture, release(fixture)) }
            assertTrue(error.message.orEmpty().contains("not newer"))
            val intent = UpdateInstaller.intent(context, fixture)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("content", intent.data?.scheme)
            assertEquals("${context.packageName}.updates", intent.data?.authority)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
            context.contentResolver.openInputStream(requireNotNull(intent.data)).use { assertEquals('P'.code, it?.read()) }
            outside.writeText("isolated fixture")
            assertThrows(IllegalArgumentException::class.java) { UpdateInstaller.intent(context, outside) }
        } finally { fixture.delete(); outside.delete() }
    }
    @Test fun preparedNewerSignedApkIsAcceptedAndWrongSignerIsRejected() {
        val args = InstrumentationRegistry.getArguments()
        val valid = args.getString("verifiedUpdateFixture")
        val forged = args.getString("wrongSignerUpdateFixture")
        assumeTrue("Optional higher-version APK fixtures are prepared after the release build", valid != null && forged != null)
        val goodFile = File(requireNotNull(valid)); val badFile = File(requireNotNull(forged))
        UpdateInstaller.validate(context, goodFile, release(goodFile))
        val error = assertThrows(IOException::class.java) { UpdateInstaller.validate(context, badFile, release(badFile)) }
        assertTrue(error.message.orEmpty().contains("signature"))
    }
}
