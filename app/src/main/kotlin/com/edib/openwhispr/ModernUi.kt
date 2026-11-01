package com.edib.openwhispr

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Shared native surfaces and typography; no extra runtime or downloaded fonts. */
internal object ModernUi {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun color(context: Context, resource: Int) = ContextCompat.getColor(context, resource)
    fun surface(context: Context, muted: Boolean = false, radius: Int = 14): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(context, radius).toFloat()
        setColor(color(context, if (muted) R.color.panel_muted else R.color.panel))
        setStroke(dp(context, 1), color(context, R.color.panel_outline))
    }
    fun clickableSurface(context: Context) = RippleDrawable(
        ColorStateList.valueOf((color(context, R.color.primary) and 0x00ffffff) or 0x18000000), surface(context), null)
    fun label(context: Context, value: String, size: Float = 16f, strong: Boolean = false, muted: Boolean = false) = TextView(context).apply {
        text = value; textSize = size
        typeface = Typeface.create(if (strong) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        setTextColor(color(context, if (muted) R.color.ink_muted else R.color.ink))
        setLineSpacing(dp(context, 2).toFloat(), 1.08f)
    }
    fun logo(context: Context, size: Int = 32) = ImageView(context).apply {
        setImageResource(R.drawable.ic_app_logo)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color(context, R.color.brand_blue)) }
        setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2))
        layoutParams = LinearLayout.LayoutParams(dp(context, size), dp(context, size))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun icon(context: Context, resource: Int, size: Int = 22) = ImageView(context).apply {
        setImageResource(resource); imageTintList = ColorStateList.valueOf(color(context, R.color.primary))
        layoutParams = LinearLayout.LayoutParams(dp(context, size), dp(context, size))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun settingsIcon(label: String): Int = when {
        label.startsWith("Preferences") -> R.drawable.ic_ui_appearance
        label.startsWith("Tone & Style") -> R.drawable.ic_ui_cleanup
        label.startsWith("Speech-to-Text") -> R.drawable.ic_ui_audio_retention
        label.startsWith("Language Models") -> R.drawable.ic_ui_provider
        label.startsWith("Privacy & Data") -> R.drawable.ic_ui_text_retention
        label.startsWith("System") -> R.drawable.ic_ui_info
        label.startsWith("Providers and cleanup") -> R.drawable.ic_ui_provider
        label.startsWith("Recording and bubble") -> R.drawable.ic_ui_audio_retention
        label.startsWith("Storage and appearance") -> R.drawable.ic_ui_appearance
        label.startsWith("Backup and data") -> R.drawable.ic_ui_copy
        label.startsWith("About") -> R.drawable.ic_ui_info
        label.startsWith("Transcription") || label.startsWith("Saved providers") || label.startsWith("Test connection") -> R.drawable.ic_ui_provider
        label.startsWith("Language help") -> R.drawable.ic_ui_info
        label.startsWith("Language") -> R.drawable.ic_ui_globe
        label.startsWith("Appearance") -> R.drawable.ic_ui_appearance
        label.startsWith("Keep audio") -> R.drawable.ic_ui_audio_retention
        label.startsWith("Text history retention") -> R.drawable.ic_ui_text_retention
        label.startsWith("Typing") || label.startsWith("Keyboard") -> R.drawable.ic_ui_typing
        label.startsWith("Local models") -> R.drawable.ic_ui_provider
        label.startsWith("Clear") -> R.drawable.ic_ui_delete
        label.startsWith("Reset") -> R.drawable.ic_ui_insights
        else -> R.drawable.ic_ui_clock
    }
    fun settingRow(context: Context, label: String, action: () -> Unit, iconResource: Int = settingsIcon(label)): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(context, 56)
        setPadding(dp(context, 14), dp(context, 12), dp(context, 12), dp(context, 12))
        background = clickableSurface(context)
        isFocusable = true; isClickable = true
        addView(icon(context, iconResource))
        val parts = label.split(" · ", limit = 2)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(ModernUi.label(context, parts[0], 15f, strong = true))
            if (parts.size > 1) addView(ModernUi.label(context, parts[1], 13f, muted = true).apply { tag = "setting-subtitle" })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(context, 14); marginEnd = dp(context, 8) })
        addView(icon(context, R.drawable.ic_ui_chevron, 18))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 8) }
        setOnClickListener { action() }
    }
}
