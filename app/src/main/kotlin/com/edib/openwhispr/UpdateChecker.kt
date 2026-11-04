package com.edib.openwhispr

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/** SemVer ordering includes preview identifiers; build metadata does not affect precedence. */
data class ReleaseVersion(val major: Long, val minor: Long, val patch: Long, val pre: List<String>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        listOf(major.compareTo(other.major), minor.compareTo(other.minor), patch.compareTo(other.patch)).firstOrNull { it != 0 }?.let { return it }
        if (pre.isEmpty() || other.pre.isEmpty()) return if (pre.isEmpty() && other.pre.isEmpty()) 0 else if (pre.isEmpty()) 1 else -1
        for (i in 0 until minOf(pre.size, other.pre.size)) {
            val a = pre[i]; val b = other.pre[i]
            val numericA = a.all { it.isDigit() }; val numericB = b.all { it.isDigit() }
            val order = when {
                numericA && numericB -> a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
                numericA -> -1
                numericB -> 1
                else -> a.compareTo(b)
            }
            if (order != 0) return order
        }
        return pre.size.compareTo(other.pre.size)
    }
    companion object {
        private val pattern = Regex("^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$")
        fun parse(tag: String): ReleaseVersion? {
            if (tag.length > 160) return null
            val match = pattern.matchEntire(tag) ?: return null
            val parts = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if (parts.any { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
            return ReleaseVersion(match.groupValues[1].toLongOrNull() ?: return null, match.groupValues[2].toLongOrNull() ?: return null,
                match.groupValues[3].toLongOrNull() ?: return null, parts)
        }
    }
}
data class ReleaseApk(val name: String, val size: Long, val sha256: String)

data class AvailableRelease(val tag: String, val version: ReleaseVersion, val apk: ReleaseApk? = null) {
    val url: String get() = "https://github.com/Veenum-SVJ/WisprLite/releases/tag/$tag"
}
object UpdateChecker {
    const val API = "https://api.github.com/repos/Veenum-SVJ/WisprLite/releases?per_page=100"
    fun latest(json: String): AvailableRelease? {
        val entries = JSONArray(json)
        return (0 until entries.length()).mapNotNull { index ->
            val item = entries.optJSONObject(index) ?: return@mapNotNull null
            if (item.optBoolean("draft", false)) return@mapNotNull null
            val tag = item.optString("tag_name")
            // Never trust html_url from metadata; construct the fixed repository URL ourselves.
            // '+' is safe in the URL path, while slashes, query and fragment characters fail SemVer.
            ReleaseVersion.parse(tag)?.let { version ->
                val expected = "OpenWisper-${version.major}.${version.minor}.${version.patch}-android.apk"
                val assets = item.optJSONArray("assets") ?: JSONArray()
                val apk = (0 until assets.length()).mapNotNull assetMap@ { assetIndex ->
                    val asset = assets.optJSONObject(assetIndex) ?: return@assetMap null
                    val publishedDigest = asset.optString("digest")
                    val digest = publishedDigest.removePrefix("sha256:")
                    val size = asset.optLong("size", 0)
                    if (asset.optString("name") == expected && asset.optString("state") == "uploaded" &&
                        publishedDigest.startsWith("sha256:") && digest.matches(Regex("[0-9a-fA-F]{64}")) && size in 1..UpdateTransfer.MAX_BYTES) {
                        ReleaseApk(expected, size, digest.lowercase(java.util.Locale.ROOT))
                    } else null
                }.firstOrNull()
                AvailableRelease(tag, version, apk)
            }
        }.maxByOrNull { it.version }
    }
    fun check(): AvailableRelease? {
        val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
        client.newCall(Request.Builder().url(API).header("Accept", "application/vnd.github+json").header("User-Agent", "OpenWisper-update-check").build()).execute().use { response ->
            if (response.code == 403 || response.code == 429) throw IOException("GitHub rate limit reached. Try again later.")
            if (!response.isSuccessful) throw IOException("GitHub returned HTTP ${response.code}. Try again later.")
            return latest(response.body?.string() ?: throw IOException("GitHub returned an empty response."))
        }
    }
}
