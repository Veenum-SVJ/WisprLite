package com.edib.openwhispr

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** A labelled native accordion with a persistent expanded state and no animation dependency. */
internal class CollapsibleSettingsSection(context: Context, key: String, title: String, summary: String, val body: LinearLayout) : LinearLayout(context) {
    private val prefs = context.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
    private val preference = "settings_expanded_$key"
    private val header = ModernUi.settingRow(context, "$title · $summary", { setExpanded(body.visibility != View.VISIBLE) })

    init {
        orientation = VERTICAL
        tag = "settings-section-$key"
        header.tag = "settings-toggle-$key"
        body.tag = "settings-body-$key"
        body.setPadding(ModernUi.dp(context, 8), 0, ModernUi.dp(context, 8), ModernUi.dp(context, 12))
        addView(header)
        addView(body)
        setExpanded(prefs.getBoolean(preference, false), false)
    }

    fun setExpanded(expanded: Boolean, save: Boolean = true) {
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        header.stateDescription = if (expanded) "Expanded" else "Collapsed"
        (header as LinearLayout).let { (it.getChildAt(it.childCount - 1) as? ImageView)?.rotation = if (expanded) 90f else 0f }
        if (save) prefs.edit().putBoolean(preference, expanded).apply()
    }
}
