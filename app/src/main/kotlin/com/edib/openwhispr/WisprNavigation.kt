package com.edib.openwhispr

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

/** Small native navigation shell: no web runtime or additional UI framework. */
class WisprNavigation(private val activity: MainActivity, private val settings: View, savedState: android.os.Bundle? = null, private val settingsBack: () -> Boolean = { false }) {
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs = activity.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
    private val frame = FrameLayout(activity)
    private val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val title = ModernUi.label(activity, "Home", 20f, strong = true).apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
    private val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var destination = savedState?.getString("destination") ?: "Home"
    private val homeState = savedState?.getBundle("home_state") ?: android.os.Bundle()
    private val dictionaryState = savedState?.getBundle("dictionary_state") ?: android.os.Bundle()
    private var home: HomeHistoryScreen? = null
    private var dictionary: DictionaryScreen? = null
    private var generation = 0
    private var drawerOverlay: View? = null
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 16f) = TextView(activity).apply {
        text = value; textSize = size; setPadding(dp(12), dp(12), dp(12), dp(12))
    }
    private fun <T> query(action: (HistoryRepository) -> T, result: (T) -> Unit) {
        val token = generation
        worker.execute {
            try {
                val value = HistoryRepository(activity).use(action)
                activity.runOnUiThread { if (!activity.isDestroyed && token == generation) result(value) }
            } catch (_: Exception) {
                activity.runOnUiThread { if (token == generation) content.addView(text("Couldn’t load this view. Open it again to retry.")) }
            }
        }
    }
    fun build(): View {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        content.setPadding(dp(16), dp(12), dp(16), dp(24))
        activity.onBackPressedDispatcher.addCallback(activity, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (closeDrawer()) return
                if (destination == "Settings" && settingsBack()) return
                if (destination != "Home") { show("Home"); return }
                isEnabled = false
                activity.onBackPressedDispatcher.onBackPressed()
            }
        })
        val header = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(4), dp(8), dp(4))
            addView(ModernUi.logo(activity, 24))
            addView(ModernUi.label(activity, "OpenWisper", 16f, strong = true),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
            addView(com.google.android.material.button.MaterialButton(activity).apply {
                contentDescription = "Open navigation"
                backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                elevation = 0f; stateListAnimator = null
                icon = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_ui_menu)
                iconTint = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, R.color.ink))
                iconPadding = 0; iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                setPadding(dp(12), 0, dp(12), 0); minWidth = 0
                setOnClickListener { drawer() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        body.addView(header)
        body.addView(title)
        body.addView(View(activity).apply { setBackgroundColor(ModernUi.color(activity, R.color.panel_outline)) }, LinearLayout.LayoutParams(-1, dp(1)))
        body.addView(ScrollView(activity).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(body, FrameLayout.LayoutParams(-1, -1))
        show(destination)
        return frame
    }
    fun refresh() { if (destination == "Home") home?.refresh() else show(destination) }
    fun openSettings() { show("Settings") }
    fun saveState(state: android.os.Bundle) {
        state.putString("destination", destination)
        state.putBundle("home_state", android.os.Bundle(homeState))
        state.putBundle("dictionary_state", android.os.Bundle(dictionaryState))
    }
    fun close() { generation++; home?.close(); dictionary?.close(); worker.shutdownNow() }
    private fun closeDrawer(): Boolean {
        val overlay = drawerOverlay ?: return false
        frame.removeView(overlay); drawerOverlay = null
        return true
    }
    private fun drawer() {
        closeDrawer()
        val cover = FrameLayout(activity).apply { setBackgroundColor(0x66000000) }
        drawerOverlay = cover
        cover.setOnClickListener { closeDrawer() }
        val panel = LinearLayout(activity).apply {
            tag = "navigation-panel"
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(20))
            val background = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.colorBackground, background, true)
            setBackgroundColor(ModernUi.color(activity, R.color.window_surface))
            isClickable = true
        }
        panel.addView(LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ModernUi.logo(activity, 36))
            addView(ModernUi.label(activity, "OpenWisper", 18f, strong = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
            setPadding(dp(8), 0, dp(8), dp(28))
        })
        val icons = mapOf("Home" to R.drawable.ic_ui_home, "Insights" to R.drawable.ic_ui_insights,
            "Dictionary" to R.drawable.ic_ui_dictionary, "Settings" to R.drawable.ic_ui_settings)
        fun navigationRow(name: String) = com.google.android.material.button.MaterialButton(activity).apply {
            text = name; isAllCaps = false; textSize = 14f
            elevation = 0f; stateListAnimator = null
            gravity = Gravity.START or Gravity.CENTER_VERTICAL; cornerRadius = dp(6)
            icon = androidx.core.content.ContextCompat.getDrawable(activity, icons.getValue(name))
            iconPadding = dp(10); iconSize = dp(18)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setTextColor(ModernUi.color(activity, R.color.ink))
            typeface = ReferenceTypography.face(activity, strong = destination == name)
            iconTint = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, if (destination == name) R.color.primary else R.color.ink_muted))
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (destination == name)
                ModernUi.color(activity, R.color.primary_container) else android.graphics.Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
            minHeight = dp(48)
            setOnClickListener { closeDrawer(); show(name) }
        }
        for (name in listOf("Home", "Insights", "Dictionary")) panel.addView(navigationRow(name))
        panel.addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
        panel.addView(navigationRow("Settings"))
        cover.addView(panel, FrameLayout.LayoutParams(dp(280), -1, Gravity.END))
        frame.addView(cover, FrameLayout.LayoutParams(-1, -1))
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            panel.translationX = dp(280).toFloat()
            panel.animate().translationX(0f).setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }
    private fun show(name: String) {
        home?.close(); home = null; dictionary?.close(); dictionary = null
        destination = name; generation++; title.text = name
        (settings.parent as? android.view.ViewGroup)?.removeView(settings)
        content.removeAllViews()
        when (name) {
            "Home" -> { home = HomeHistoryScreen(activity, homeState, { show("Settings") }); content.addView(home!!.build()) }
            "Insights" -> query({ it.events() }) { events -> content.addView(speakingInsightsPanel(activity, events, prefs.getInt("typing_wpm", 40).coerceAtLeast(1))) }
            "Dictionary" -> { dictionary = DictionaryScreen(activity, dictionaryState); content.addView(dictionary!!.build()) }
            "Settings" -> content.addView(settings)
        }
    }
}
