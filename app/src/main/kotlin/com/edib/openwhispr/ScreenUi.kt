package com.edib.openwhispr

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton

internal fun EditText.onChange(action: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun afterTextChanged(s: Editable?) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { action(s.toString()) }
    })
}

internal fun screenButton(context: Context, label: String, action: () -> Unit) = MaterialButton(context, null,
    com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
    text = label; isAllCaps = false; textSize = 13f
    typeface = ReferenceTypography.face(context)
    minHeight = ModernUi.dp(context, 48); minWidth = 0; cornerRadius = ModernUi.dp(context, 6)
    insetTop = 0; insetBottom = 0
    setPadding(ModernUi.dp(context, 10), ModernUi.dp(context, 6), ModernUi.dp(context, 10), ModernUi.dp(context, 6))
    strokeColor = ColorStateList.valueOf(ModernUi.color(context, R.color.panel_outline))
    backgroundTintList = ColorStateList.valueOf(ModernUi.color(context, R.color.surface_subtle))
    setTextColor(ModernUi.color(context, R.color.ink))
    setOnClickListener { action() }
}

internal fun screenInput(context: Context, label: String) = EditText(context).apply {
    hint = label; contentDescription = label; textSize = 14f
    typeface = ReferenceTypography.face(context)
    setTextColor(ModernUi.color(context, R.color.ink))
    setHintTextColor(ModernUi.color(context, R.color.ink_muted))
    background = ModernUi.surface(context, radius = 6)
    setPadding(ModernUi.dp(context, 14), ModernUi.dp(context, 12), ModernUi.dp(context, 14), ModernUi.dp(context, 12))
    minimumHeight = ModernUi.dp(context, 48)
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ModernUi.dp(context, 8) }
}
