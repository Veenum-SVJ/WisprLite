package com.edib.openwhispr

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Explicit user-started updates; worker state survives Activity recreation. */
internal object AppUpdates {
    data class Ready(val file: File, val release: AvailableRelease)
    data class State(val checking: Boolean = false, val downloading: Boolean = false, val percent: Int = 0,
        val release: AvailableRelease? = null, val ready: Ready? = null,
        val message: String = "Check for a new version, then download and install it here.")
    @Volatile var state = State()
        private set
    private val listeners = CopyOnWriteArraySet<(State) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var initialized = false
    private var generation = 0L
    private var call: Call? = null
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES).followRedirects(false).followSslRedirects(false).build()
    fun observe(listener: (State) -> Unit) { listeners += listener; listener(state) }
    fun remove(listener: (State) -> Unit) { listeners -= listener }
    private fun publish(value: State) { state = value; main.post { listeners.forEach { it(state) } } }

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val folder = File(context.cacheDir, "app-updates")
        folder.listFiles()?.filter { it.name.matches(Regex("[0-9a-f-]{36}\\.part")) }?.forEach { it.delete() }
        val metadata = File(folder, "ready.json")
        runCatching {
            if (!metadata.isFile || metadata.length() > 4096) return@runCatching
            val item = JSONObject(metadata.readText())
            val tag = item.getString("tag")
            val version = requireNotNull(ReleaseVersion.parse(tag))
            val installed = ReleaseVersion.parse(context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty())
            if (installed != null && version <= installed) { File(folder, "update.apk").delete(); metadata.delete(); return@runCatching }
            val asset = ReleaseApk(item.getString("name"), item.getLong("size"), item.getString("sha256"))
            require(asset.size in 1..UpdateTransfer.MAX_BYTES && asset.sha256.matches(Regex("[0-9a-f]{64}")))
            val release = AvailableRelease(tag, version, asset)
            UpdateTransfer.url(release)
            val file = File(folder, "update.apk")
            if (file.isFile && file.length() == asset.size) publish(State(release = release, ready = Ready(file, release), message = "Update downloaded. Ready to install."))
        }
    }
    @Synchronized fun check(installedVersion: String) {
        if (state.checking || state.downloading) return
        val id = ++generation
        val cached = state.ready
        publish(state.copy(checking = true, message = "Checking for updates…"))
        thread(name = "OpenWisper-update-check") {
            try {
                val release = UpdateChecker.check()
                val installed = ReleaseVersion.parse(installedVersion)
                val newer = release != null && (installed == null || release.version > installed)
                finish(id, State(release = release.takeIf { newer },
                    ready = cached?.takeIf { newer && it.release == release && it.file.isFile }, message = when {
                    newer && release?.apk != null -> "${release.tag} is available · ${String.format(java.util.Locale.ROOT, "%.1f", release.apk.size / 1_000_000.0)} MB"
                    newer -> "${release?.tag} is available. Its verified APK is not ready yet; try again shortly."
                    else -> "You’re up to date. Installed: $installedVersion"
                }))
            } catch (_: Exception) { finish(id, State(message = "Couldn’t check for updates. Check your connection and try again.")) }
        }
    }
    @Synchronized fun download(context: Context, release: AvailableRelease) {
        if (state.checking || state.downloading || release.apk == null) return
        val app = context.applicationContext
        val id = ++generation
        publish(State(downloading = true, release = release, message = "Downloading update…"))
        thread(name = "OpenWisper-update-download") {
            val folder = File(app.cacheDir, "app-updates").apply { mkdirs() }
            val part = File(folder, "${java.util.UUID.randomUUID()}.part")
            try {
                if (folder.usableSpace < release.apk.size + 8L * 1024 * 1024) throw IOException("Not enough storage for the update.")
                var url = UpdateTransfer.url(release)
                var completed = false
                for (redirect in 0..5) {
                    val requestCall = client.newCall(Request.Builder().url(url).header("User-Agent", "OpenWisper-update-download").build())
                    synchronized(this) { if (generation != id) throw IOException("Download cancelled"); call = requestCall }
                    requestCall.execute().use { response ->
                        if (response.code in listOf(301, 302, 303, 307, 308)) {
                            val next = response.request.url.resolve(response.header("Location") ?: throw IOException("Update redirect is missing."))
                                ?: throw IOException("Invalid update redirect.")
                            if (next.scheme != "https" || next.host !in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) throw IOException("Untrusted update redirect.")
                            url = next.toString()
                        } else {
                            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code}). Please retry.")
                            val body = response.body ?: throw IOException("The download was empty.")
                            body.byteStream().use { input -> part.outputStream().use { output ->
                                UpdateTransfer.copy(input, output, release.apk, { synchronized(this) { generation != id } }) { percent ->
                                    synchronized(this) { if (generation == id) publish(state.copy(percent = percent, message = "Downloading update… $percent%")) }
                                }
                            } }
                            completed = true
                        }
                    }
                    if (completed) break
                }
                if (!completed) throw IOException("Too many download redirects. Please retry.")
                UpdateInstaller.validate(app, part, release)
                synchronized(this) {
                    if (generation != id) throw IOException("Download cancelled")
                    val file = File(folder, "update.apk")
                    if (file.exists() && !file.delete()) throw IOException("Couldn’t replace the previous download.")
                    if (!part.renameTo(file)) throw IOException("Couldn’t save the update. Please retry.")
                    val asset = release.apk
                    File(folder, "ready.json").writeText(JSONObject().put("tag", release.tag).put("name", asset.name)
                        .put("size", asset.size).put("sha256", asset.sha256).toString())
                    finish(id, State(release = release, ready = Ready(file, release), message = "Update verified. Ready to install."))
                }
            } catch (error: Exception) {
                finish(id, State(release = release, message = if (error is IOException) error.message ?: "Download failed. Please retry." else "Couldn’t verify this update. Please retry."))
            } finally { part.delete() }
        }
    }
    @Synchronized fun cancel() {
        ++generation; call?.cancel(); call = null
        publish(State(release = state.release, message = "Download cancelled. You can retry whenever you’re ready."))
    }
    @Synchronized fun invalidateReady(message: String) {
        state.ready?.file?.let { file -> file.delete(); File(file.parentFile, "ready.json").delete() }
        publish(State(release = state.release, message = message))
    }
    @Synchronized private fun finish(id: Long, value: State) { if (generation == id) { call = null; publish(value) } }
}
