package com.edib.openwhispr

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.concurrent.thread

internal class AppUpdatesUi(private val activity: MainActivity) {
    private val installed = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
    private var offerOnResult = false
    private var installAfterDownload = false
    private var verifying = false
    private val status = ModernUi.label(activity, "", 14f, muted = true).apply { tag = "settings-update-status" }
    private val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
    private val check = ModernUi.settingRow(activity, "Check for Updates · Download and install here", {
        offerOnResult = true; AppUpdates.check(installed)
    }).apply { tag = "settings-check-updates" }
    private val action = screenButton(activity, "Download and install") { offerDownload() }.apply { tag = "settings-install-update"; visibility = View.GONE }
    private val cancel = screenButton(activity, "Cancel download") { installAfterDownload = false; AppUpdates.cancel() }.apply { visibility = View.GONE }
    val view = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(status); addView(progress, LinearLayout.LayoutParams(-1, -2)); addView(check); addView(action); addView(cancel)
    }
    private val permission = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (activity.packageManager.canRequestPackageInstalls()) installReady()
        else status.text = "Installation permission was not granted. Tap Install update to try again."
    }
    private val listener: (AppUpdates.State) -> Unit = { render(it) }
    init {
        AppUpdates.initialize(activity)
        AppUpdates.observe(listener)
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) AppUpdates.remove(listener)
            else if (event == Lifecycle.Event.ON_RESUME) render(AppUpdates.state)
        })
    }
    private fun render(state: AppUpdates.State) {
        if (activity.isDestroyed) return
        status.text = state.message
        check.isEnabled = !state.checking && !state.downloading && !verifying
        progress.visibility = if (state.downloading || state.checking || verifying) View.VISIBLE else View.GONE
        progress.isIndeterminate = !state.downloading
        progress.progress = state.percent
        action.visibility = if (!state.downloading && !state.checking && (state.ready != null || state.release?.apk != null)) View.VISIBLE else View.GONE
        action.text = if (state.ready != null) "Install update" else "Download and install"
        action.isEnabled = !verifying
        cancel.visibility = if (state.downloading) View.VISIBLE else View.GONE
        if (state.ready != null && installAfterDownload && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            installAfterDownload = false; installReady()
        } else if (offerOnResult && !state.checking && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            offerOnResult = false
            if (state.release?.apk != null) offerDownload()
        }
    }
    private fun offerDownload() {
        val state = AppUpdates.state
        if (state.ready != null) { installReady(); return }
        val release = state.release ?: return
        val apk = release.apk ?: return
        AlertDialog.Builder(activity).setTitle("Update available")
            .setMessage("${release.tag} · ${android.text.format.Formatter.formatFileSize(activity, apk.size)}\nDownload the update here, then confirm installation with Android. Your settings and history are kept.")
            .setPositiveButton("Download and install") { _, _ ->
                installAfterDownload = true; AppUpdates.download(activity, release)
            }.setNegativeButton("Later", null).show()
    }
    private fun installReady() {
        val ready = AppUpdates.state.ready ?: return
        if (verifying) return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setTitle("Allow OpenWisper updates")
                .setMessage("Android needs you to allow installations from OpenWisper. Enable “Allow from this source”, then return here to continue. Each update still asks for installation confirmation.")
                .setPositiveButton("Open settings") { _, _ ->
                    try { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
                    catch (_: Exception) { status.text = "Couldn’t open Android’s installation settings." }
                }.setNegativeButton("Later", null).show()
            return
        }
        verifying = true; render(AppUpdates.state); status.text = "Verifying update…"
        thread(name = "OpenWisper-update-verify") {
            try {
                UpdateInstaller.validate(activity.applicationContext, ready.file, ready.release)
                activity.runOnUiThread {
                    verifying = false
                    if (!activity.isDestroyed && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        render(AppUpdates.state)
                        try { activity.startActivity(UpdateInstaller.intent(activity, ready.file)) }
                        catch (_: Exception) { status.text = "Couldn’t open Android’s installer. Tap Install update to retry." }
                    }
                }
            } catch (error: Exception) {
                AppUpdates.invalidateReady(error.message ?: "Couldn’t verify this update. Please download again.")
                activity.runOnUiThread { verifying = false; if (!activity.isDestroyed) render(AppUpdates.state) }
            }
        }
    }
}
