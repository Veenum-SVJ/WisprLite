package com.edib.openwhispr

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

/** Licensed build assets; public-source builds fall back to the Android font. */
internal object ReferenceTypography {
    fun face(context: Context, strong: Boolean = false, display: Boolean = false): Typeface {
        val name = if (display) "reference_display" else "reference_sans"
        val id = context.resources.getIdentifier(name, "font", context.packageName)
        val family = if (id == 0) Typeface.SANS_SERIF else ResourcesCompat.getFont(context, id) ?: Typeface.SANS_SERIF
        return Typeface.create(family, if (strong) 500 else 400, false)
    }
}
