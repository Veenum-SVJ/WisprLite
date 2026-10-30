package com.edib.openwhispr

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText

/** A separate package's empty field for cross-app insertion tests without opening personal apps. */
class DictationTargetActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val field = EditText(this).apply {
            hint = "WisprLite test field"; minLines = 8
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            contentDescription = "Dictation test field"
        }
        setContentView(field)
        field.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
}
