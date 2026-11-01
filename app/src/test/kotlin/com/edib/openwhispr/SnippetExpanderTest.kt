package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class SnippetExpanderTest {
    @Test fun `longest triggers expand once without recursive expansion`() {
        val prepared = SnippetExpander.prepare("Use MY   ADDRESS or my address now; address.", listOf(
            Snippet("short", "address", "SHORT"),
            Snippet("long", "my address", "address\nhttps://example.org/?a=1&b=Ẹ")))
        assertEquals("Use address\nhttps://example.org/?a=1&b=Ẹ or address\nhttps://example.org/?a=1&b=Ẹ now; SHORT.", prepared.expanded)
        assertEquals(prepared.expanded, prepared.restore(prepared.protectedText))
        assertEquals("Use MY   ADDRESS or my address now; address.", prepared.source)
    }

    @Test fun `Unicode word boundaries and disabled snippets are respected`() {
        val prepared = SnippetExpander.prepare("ẹ káàárọ̀, Ẹ\u00a0KÁÀÁRỌ̀! xẹ káàárọ̀ ẹ káàárọ̀x café.", listOf(
            Snippet("yoruba", "Ẹ káàárọ̀", "Hello!"), Snippet("off", "café", "OFF", false)))
        assertEquals("Hello!, Hello!! xẹ káàárọ̀ ẹ káàárọ̀x café.", prepared.expanded)
    }

    @Test fun `cleanup must preserve all tokens once in original order`() {
        val prepared = SnippetExpander.prepare("first then second", listOf(
            Snippet("1", "first", "Line 1\nLine 2"), Snippet("2", "second", "https://example.org/Ẹ")))
        val tokens = Regex("⟦[^⟧]+⟧").findAll(prepared.protectedText).map { it.value }.toList()
        assertEquals("Please: Line 1\nLine 2 then https://example.org/Ẹ.", prepared.restore("Please: ${prepared.protectedText}."))
        assertNull(prepared.restore(prepared.protectedText.replace(tokens[0], "")))
        assertNull(prepared.restore(prepared.protectedText + tokens[0]))
        assertNull(prepared.restore("${tokens[1]} then ${tokens[0]}"))
        assertNull(prepared.restore(prepared.protectedText + "⟦WISPR_SNIPPET_unknown⟧"))
        assertNull(prepared.restore(prepared.protectedText + "⟦WISPR_SNIPPET_broken"))
        assertNull(prepared.restore(prepared.protectedText + "⟦wispr_snippet_unknown⟧"))
        assertNull(prepared.restore(prepared.protectedText.replace(tokens[0], tokens[0].lowercase())))
    }

    @Test fun `canonical Unicode matching preserves source offsets around decomposed and emoji text`() {
        val source = "👍🏾 cafe\u0301 👩‍💻 cafe\u0301!"
        val prepared = SnippetExpander.prepare(source, listOf(Snippet("cafe", "CAFÉ", "Exact\nẸ")))
        assertEquals("👍🏾 Exact\nẸ 👩‍💻 Exact\nẸ!", prepared.expanded)
        assertEquals(source, prepared.source)
        assertEquals(prepared.expanded, prepared.restore(prepared.protectedText))
    }

    @Test fun `case normalization supports dotted I and Unicode whitespace`() {
        val prepared = SnippetExpander.prepare("İstanbul\u0085address", listOf(Snippet("i", "İSTANBUL address", "saved")))
        assertEquals("saved", prepared.expanded)
        assertEquals("saved", prepared.restore(prepared.protectedText))
    }

    @Test fun `Unicode case equivalents use identical matching and uniqueness rules`() {
        assertEquals(SnippetValidation.normalizeTrigger("ΟΣ"), SnippetValidation.normalizeTrigger("οσ"))
        assertEquals(SnippetValidation.normalizeTrigger("İ"), SnippetValidation.normalizeTrigger("i"))
        for ((first, second) in listOf("ΟΣ" to "οσ", "İ" to "i")) {
            assertThrows(IllegalArgumentException::class.java) {
                SnippetValidation.validateAll(listOf(Snippet("a", first, "one"), Snippet("b", second, "two")))
            }
        }
        val prepared = SnippetExpander.prepare("ΟΣ ος οσ! 𐐀 and 𐐨", listOf(
            Snippet("sigma", "οσ", "sigma"), Snippet("deseret", "𐐨", "letter")))
        assertEquals("sigma sigma sigma! letter and letter", prepared.expanded)
        assertEquals(prepared.expanded, prepared.restore(prepared.protectedText))
    }

    @Test fun `input cannot inject placeholders and duplicate triggers fail validation`() {
        assertThrows(IllegalArgumentException::class.java) {
            SnippetExpander.prepare("⟦WISPR_SNIPPET_fake⟧", emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SnippetValidation.validateAll(listOf(Snippet("1", "  HELLO\u00a0world ", "a"), Snippet("2", "hello world", "b")))
        }
        assertThrows(IllegalArgumentException::class.java) { SnippetValidation.validate(Snippet("", "test", "ok")) }
        assertThrows(IllegalArgumentException::class.java) { SnippetValidation.validate(Snippet("1", "\u00a0", "ok")) }
        assertThrows(IllegalArgumentException::class.java) { SnippetValidation.validate(Snippet("1", "test", "\n")) }
    }

    @Test fun `dictation without matches still rejects unexpected placeholder output`() {
        val prepared = SnippetExpander.prepare("Nothing to expand", emptyList())
        assertEquals(prepared.source, prepared.expanded)
        assertEquals("Nothing to expand.", prepared.restore("Nothing to expand."))
        assertNull(prepared.restore("⟦WISPR_SNIPPET_unknown⟧"))
    }

    @Test fun `saved state keeps original payloads after snippet definitions change`() {
        val original = SnippetExpander.prepare("my address now", listOf(Snippet("1", "my address", "Old\nhttps://example.org/Ẹ")))
        val saved = original.serialize()
        SnippetExpander.prepare("my address now", listOf(Snippet("1", "my address", "NEW")))
        val restored = SnippetExpander.deserialize(saved)
        assertEquals(original.source, restored.source)
        assertEquals(original.expanded, restored.expanded)
        assertEquals(original.protectedText, restored.protectedText)
        assertEquals("Please: Old\nhttps://example.org/Ẹ now.", restored.restore("Please: ${restored.protectedText}."))
        val plain = SnippetExpander.prepare("nothing", emptyList())
        assertEquals("nothing", SnippetExpander.deserialize(plain.serialize()).expanded)
    }

    @Test fun `saved state rejects corrupt schema tokens payloads and roundtrip`() {
        val prepared = SnippetExpander.prepare("address and email", listOf(
            Snippet("1", "address", "First"), Snippet("2", "email", "Second")))
        fun rejects(edit: (JSONObject) -> Unit) {
            val json = JSONObject(prepared.serialize()).apply(edit)
            assertThrows(IllegalArgumentException::class.java) { SnippetExpander.deserialize(json.toString()) }
        }
        assertThrows(IllegalArgumentException::class.java) { SnippetExpander.deserialize("not JSON") }
        rejects { it.put("version", 99) }
        rejects { it.put("version", "1") }
        rejects { it.remove("source") }
        rejects { it.put("source", 100) }
        rejects { it.put("source", "⟦WISPR_SNIPPET_fake⟧") }
        rejects { it.put("expanded", "Changed") }
        rejects { it.put("protectedText", prepared.protectedText + "⟦WISPR_SNIPPET_unknown⟧") }
        rejects { it.put("tokens", JSONArray(listOf("fake", "fake"))) }
        rejects { it.put("payloads", JSONArray(listOf("First"))) }
        rejects { it.put("payloads", JSONArray(listOf("", "Second"))) }
        rejects { it.put("payloads", JSONArray(listOf(123, "Second"))) }
        rejects { it.put("tokens", JSONArray(it.getJSONArray("tokens").let { tokens -> listOf(tokens.getString(1), tokens.getString(0)) })) }
        val plain = JSONObject(SnippetExpander.prepare("plain", emptyList()).serialize()).put("expanded", "different")
        assertThrows(IllegalArgumentException::class.java) { SnippetExpander.deserialize(plain.toString()) }
    }
}
