package com.edib.openwhispr

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

enum class SettingsDestination(val title: String, val group: String, val summary: String) {
    PREFERENCES("Preferences", "APP", "Appearance, language and recording bubble"),
    TONE("Tone & Style", "APP", "Category tones and app preferences"),
    SPEECH("Speech-to-Text", "AI MODELS", "Recognition providers and local models"),
    LANGUAGE("Language Models", "AI MODELS", "AI cleanup, providers and prompts"),
    PRIVACY("Privacy & Data", "SYSTEM", "Storage, retention and encrypted backups"),
    SYSTEM("System", "SYSTEM", "Setup, diagnostics and updates")
}

/** Keeps each live editor in one parent. Refreshing never rebuilds a detail page. */
class SettingsRouter(private val context: Context, savedState: Bundle? = null) {
    val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; tag = "wispr-settings" }
    private val index = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; tag = "settings-index" }
    private val detail = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; tag = "settings-detail" }
    private val pages = linkedMapOf<SettingsDestination, View>()
    private var destination = savedState?.getString("settings-detail")?.let { runCatching { SettingsDestination.valueOf(it) }.getOrNull() }
    private val title = ModernUi.label(context, "", 20f, strong = true).apply { tag = "settings-detail-title" }
    private val readiness = ModernUi.settingRow(context, "Dictation readiness · System setup", { open(SettingsDestination.SYSTEM) }).apply { tag = "settings-readiness" }
    init {
        index.addView(readiness)
        var group = ""
        SettingsDestination.entries.forEach { target ->
            if (target.group != group) {
                group = target.group
                index.addView(ModernUi.label(context, group, 12f, strong = true, muted = true).apply { setPadding(4, 20, 4, 8) })
            }
            index.addView(ModernUi.navigationRow(context, "${target.title} · ${target.summary}", { open(target) }).apply { tag = "settings-open-${target.name}" })
        }
        detail.addView(ModernUi.settingRow(context, "Back to Settings", { handleBack() }).apply { tag = "settings-detail-back" })
        detail.addView(title)
        view.addView(index); view.addView(detail)
    }
    fun page(target: SettingsDestination, content: View) {
        pages[target] = content; detail.addView(content); render()
    }
    fun open(target: SettingsDestination) { destination = target; render(); view.post { title.requestRectangleOnScreen(android.graphics.Rect(0, 0, title.width, title.height), false) } }
    fun handleBack(): Boolean {
        if (destination == null) return false
        destination = null; render(); view.post { index.requestRectangleOnScreen(android.graphics.Rect(0, 0, index.width, 1), false) }; return true
    }
    fun saveState(out: Bundle) { out.putString("settings-detail", destination?.name) }
    fun readiness(text: String) { readiness.findViewWithTag<TextView>("setting-subtitle")?.text = text }
    private fun render() {
        index.visibility = if (destination == null) View.VISIBLE else View.GONE
        detail.visibility = if (destination == null) View.GONE else View.VISIBLE
        title.text = destination?.title.orEmpty()
        pages.forEach { (key, page) -> page.visibility = if (key == destination) View.VISIBLE else View.GONE }
    }
}
