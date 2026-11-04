package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {
    private fun version(value: String) = requireNotNull(ReleaseVersion.parse(value))
    @Test fun semanticOrderIncludesPreviewsNumericIdentifiersAndStableRelease() {
        val ordered = listOf("v0.1.0", "0.1.1-alpha", "0.1.1-alpha.2", "0.1.1-alpha.10", "0.1.1-beta", "0.1.1-rc.1", "v0.1.1", "0.2.0", "1.0.0")
        assertEquals(ordered.map(::version), ordered.reversed().map(::version).sorted())
        assertEquals(0, version("1.0.0+build.4").compareTo(version("v1.0.0+other")))
    }
    @Test fun invalidOrUnsafeReleaseTagsAreRejected() {
        listOf("https://evil.test/1.0.0", "1.0.0?x=1", "1.0.0#fragment", "../1.0.0", "1.0.0/asset", "1.0", "01.2.3", "1.0.0-preview.01", "1.0.0-", "1.0.0\n").forEach {
            assertNull(it, ReleaseVersion.parse(it))
        }
    }
    @Test fun parserIncludesPrereleasesIgnoresDraftsAndConstructsTrustedDownloadUrl() {
        val metadata = """[
            {"tag_name":"v1.0.0","draft":false},
            {"tag_name":"v2.0.0-preview.3","prerelease":true,"html_url":"https://evil.test"},
            {"tag_name":"v3.0.0","draft":true},
            {"tag_name":"https://evil.test","draft":false}
        ]"""
        val release = requireNotNull(UpdateChecker.latest(metadata))
        assertEquals("v2.0.0-preview.3", release.tag)
        assertEquals("https://github.com/Veenum-SVJ/WisprLite/releases/tag/v2.0.0-preview.3", release.url)
        assertNull(UpdateChecker.latest("[]"))
    }
    @Test fun parserRejectsBrokenMetadata() {
        assertThrows(Exception::class.java) { UpdateChecker.latest("not json") }
    }
    @Test fun directInstallRequiresTheExpectedUploadedApkWithSizeAndSha256() {
        val digest = "ab".repeat(32)
        fun metadata(name: String = "OpenWisper-1.2.3-android.apk", hash: String = "sha256:$digest", size: Long = 100, state: String = "uploaded") =
            """[{"tag_name":"v1.2.3-preview","assets":[{"name":"$name","size":$size,"digest":"$hash","state":"$state","browser_download_url":"https://other.test/payload"}]}]"""
        val release = requireNotNull(UpdateChecker.latest(metadata()))
        assertEquals(digest, release.apk?.sha256)
        assertTrue(UpdateTransfer.url(release).startsWith("https://github.com/Veenum-SVJ/WisprLite/releases/download/"))
        assertNull(UpdateChecker.latest(metadata(name = "other.apk"))?.apk)
        assertNull(UpdateChecker.latest(metadata(hash = "sha1:$digest"))?.apk)
        assertNull(UpdateChecker.latest(metadata(size = 0))?.apk)
        assertNull(UpdateChecker.latest(metadata(size = UpdateTransfer.MAX_BYTES + 1))?.apk)
        assertNull(UpdateChecker.latest(metadata(state = "new"))?.apk)
    }
}
