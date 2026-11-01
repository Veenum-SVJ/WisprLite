package com.edib.openwhispr

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch

/** App discovery and all rules stay on this device. Only a chosen tone reaches cleanup. */
class ToneStyleSettings(private val activity: MainActivity) {
    private val prefs = activity.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
    private val rules = ToneRules(prefs)
    val view = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; tag = "tone-style-settings" }
    fun refresh() {
        view.removeAllViews()
        val config = rules.configuration()
        view.addView(MaterialSwitch(activity).apply {
            text = "Enable app tone rules"; tag = "tone-enabled"; isChecked = config.enabled
            setOnCheckedChangeListener { _, enabled -> rules.save(rules.configuration().copy(enabled = enabled)); refresh() }
        })
        view.addView(ModernUi.label(activity, if (prefs.getBoolean("use_post_processing", false))
            "Tone changes apply during AI Cleanup. Direct dictation preserves your words."
            else "AI Cleanup is off. Enable it in Language Models to apply tones; tone rules never enable cleanup automatically.", 14f, muted = true))
        ToneCategory.entries.forEach { category ->
            val tone = config.categories[category] ?: category.defaultTone
            view.addView(ModernUi.settingRow(activity, "${category.title} · ${tone.title} (default: ${category.defaultTone.title})", {
                chooseTone(category.title, tone) { selected ->
                    val current = rules.configuration()
                    rules.save(current.copy(categories = current.categories + (category to selected))); refresh()
                }
            }).apply { tag = "tone-category-${category.name}" })
        }
        view.addView(ModernUi.settingRow(activity, "Assign an app · Choose an installed launcher app", { apps() }).apply { tag = "tone-add-app" })
        config.apps.toSortedMap().forEach { (pkg, rule) ->
            val label = appLabel(pkg)
            view.addView(ModernUi.settingRow(activity, "$label · ${rule.category.title}, ${rule.tone?.title ?: "category tone"}", { editApp(pkg, label, rule) }))
        }
        view.addView(ModernUi.settingRow(activity, "Next dictation · ${rules.nextOverride()?.title ?: "Use app rules"}", {
            AlertDialog.Builder(activity).setTitle("Tone for the next dictation")
                .setItems((listOf("Use app rules") + Tone.entries.map { it.title }).toTypedArray()) { _, index ->
                    rules.overrideNext(if (index == 0) null else Tone.entries[index - 1]); refresh()
                }.setNegativeButton("Cancel", null).show()
        }).apply { tag = "tone-next-override" })
        view.addView(ModernUi.label(activity, "A manual override applies once, then returns to app rules. Unknown apps use Other apps. App assignments and your installed app list remain local; they are never sent to a provider.", 13f, muted = true))
    }
    private fun chooseTone(title: String, current: Tone, select: (Tone) -> Unit) {
        AlertDialog.Builder(activity).setTitle(title).setSingleChoiceItems(Tone.entries.map { it.title }.toTypedArray(), Tone.entries.indexOf(current)) { dialog, index ->
            select(Tone.entries[index]); dialog.dismiss()
        }.setNegativeButton("Cancel", null).show()
    }
    private fun appLabel(pkg: String): String = runCatching { activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    private fun apps() {
        val installed = activity.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(activity.packageManager).toString().lowercase() }
        if (installed.isEmpty()) { Toast.makeText(activity, "No launcher apps found", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(activity).setTitle("Choose an app").setItems(installed.map { it.loadLabel(activity.packageManager).toString() }.toTypedArray()) { _, index ->
            val app = installed[index]
            assignCategory(app.activityInfo.packageName, app.loadLabel(activity.packageManager).toString())
        }.setNegativeButton("Cancel", null).show()
    }
    private fun assignCategory(pkg: String, label: String) {
        AlertDialog.Builder(activity).setTitle("Category for $label").setItems(ToneCategory.entries.map { it.title }.toTypedArray()) { _, index ->
            val config = rules.configuration(); val old = config.apps[pkg]
            rules.save(config.copy(apps = config.apps + (pkg to AppToneRule(ToneCategory.entries[index], old?.tone)))); refresh()
        }.setNegativeButton("Cancel", null).show()
    }
    private fun editApp(pkg: String, label: String, rule: AppToneRule) {
        AlertDialog.Builder(activity).setTitle(label).setItems(arrayOf("Change category", "Set specific tone", "Use category tone", "Remove app assignment")) { _, index ->
            when (index) {
                0 -> assignCategory(pkg, label)
                1 -> chooseTone(label, rule.tone ?: rules.configuration().toneFor(pkg)) { tone ->
                    val config = rules.configuration(); rules.save(config.copy(apps = config.apps + (pkg to rule.copy(tone = tone)))); refresh()
                }
                2 -> { val config = rules.configuration(); rules.save(config.copy(apps = config.apps + (pkg to rule.copy(tone = null)))); refresh() }
                3 -> { val config = rules.configuration(); rules.save(config.copy(apps = config.apps - pkg)); refresh() }
            }
        }.setNegativeButton("Close", null).show()
    }
}
