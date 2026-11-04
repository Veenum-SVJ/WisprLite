package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest

class UpdateTransferTest {
    private val bytes = ByteArray(100_000) { (it % 251).toByte() }
    private fun asset() = ReleaseApk("OpenWisper-1.2.3-android.apk", bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    @Test fun completeDownloadPreservesBytesAndReportsProgress() {
        val output = ByteArrayOutputStream(); val progress = mutableListOf<Int>()
        UpdateTransfer.copy(ByteArrayInputStream(bytes), output, asset(), { false }, { progress += it })
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(100, progress.last())
        assertEquals(progress.sorted().distinct(), progress)
    }
    @Test fun incompleteOversizedCorruptAndCancelledDownloadsCannotSucceed() {
        val output = ByteArrayOutputStream()
        assertThrows(IOException::class.java) { UpdateTransfer.copy(ByteArrayInputStream(bytes.dropLast(1).toByteArray()), output, asset(), { false }, {}) }
        assertThrows(IOException::class.java) { UpdateTransfer.copy(ByteArrayInputStream(bytes + byteArrayOf(1)), output, asset(), { false }, {}) }
        assertThrows(IOException::class.java) { UpdateTransfer.copy(ByteArrayInputStream(bytes), output, asset().copy(sha256 = "0".repeat(64)), { false }, {}) }
        assertThrows(IOException::class.java) { UpdateTransfer.copy(ByteArrayInputStream(bytes), output, asset(), { true }, {}) }
    }
    @Test fun assetUrlAlwaysUsesTheFixedRepositoryAndRejectsInjectedNames() {
        val release = AvailableRelease("v1.2.3-preview", requireNotNull(ReleaseVersion.parse("v1.2.3-preview")), asset())
        assertEquals("https://github.com/Veenum-SVJ/WisprLite/releases/download/v1.2.3-preview/OpenWisper-1.2.3-android.apk", UpdateTransfer.url(release))
        assertThrows(IllegalArgumentException::class.java) { UpdateTransfer.url(release.copy(apk = asset().copy(name = "../../other.apk"))) }
        assertThrows(IllegalArgumentException::class.java) { UpdateTransfer.url(release.copy(tag = "https://other.test")) }
    }
}
