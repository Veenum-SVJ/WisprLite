package com.edib.openwhispr;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

/** Standalone test-package editor: Java avoids depending on the target app's Kotlin runtime. */
public class DictationTargetActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("webComposer", false)) {
            WebView browser = new WebView(this);
            browser.getSettings().setJavaScriptEnabled(true);
            browser.setContentDescription("Dictation web composer");
            browser.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    view.requestFocus();
                    view.evaluateJavascript("document.getElementById('composer').focus()", null);
                    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                            .showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                }
            });
            setContentView(browser);
            browser.loadDataWithBaseURL("https://wisprlite-test.invalid/",
                    "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>"
                    + "<body><p>WisprLite local web composer test</p>"
                    + "<div id='composer' contenteditable='true' role='textbox' aria-label='Dictation test field'"
                    + " style='min-height:200px;border:1px solid;padding:12px'></div></body></html>",
                    "text/html", "UTF-8", null);
            return;
        }
        EditText field = new EditText(this);
        field.setHint("WisprLite test field");
        field.setMinLines(8);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setContentDescription("Dictation test field");
        setContentView(field);
        field.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
}
