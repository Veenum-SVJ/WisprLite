package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Android's ICU-backed regex/character implementation rather than the host JDK. */
@RunWith(AndroidJUnit4::class)
class SnippetExpanderDeviceTest {
    @Test fun unicodeCanonicalMatchingAndProtectedRestorationUseAndroidCompatibleApis() {
        val source = "👩‍💻 cafe\u0301, Ẹ\u00a0KÁÀÁRỌ̀! xcafé."
        val prepared = SnippetExpander.prepare(source, listOf(
            Snippet("cafe", "CAFÉ", "https://example.org/Ẹ\nExact text"),
            Snippet("greet", "ẹ káàárọ̀", "Hello")))
        assertEquals("👩‍💻 https://example.org/Ẹ\nExact text, Hello! xcafé.", prepared.expanded)
        assertEquals(source, prepared.source)
        assertEquals(prepared.expanded, prepared.restore(prepared.protectedText))
        val saved = SnippetExpander.deserialize(prepared.serialize())
        assertEquals(prepared.expanded, saved.restore(saved.protectedText))
        assertNull(prepared.restore(prepared.protectedText + "⟦WISPR_SNIPPET_unknown⟧"))
        assertNull(prepared.restore(prepared.protectedText.lowercase()))
    }
}
