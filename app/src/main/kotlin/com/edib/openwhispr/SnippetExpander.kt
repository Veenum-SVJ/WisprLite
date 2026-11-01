package com.edib.openwhispr

import java.text.BreakIterator
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONObject

/** A prepared dictation captures its payloads so later snippet edits cannot change delivery. */
object SnippetExpander {
    private const val PREFIX = "⟦WISPR_SNIPPET_"
    private const val SUFFIX = "⟧"

    class Prepared internal constructor(
        val source: String,
        val expanded: String,
        val protectedText: String,
        private val tokens: List<String>,
        private val payloads: List<String>
    ) {
        fun serialize(): String = JSONObject().apply {
            put("version", 1); put("source", source); put("expanded", expanded); put("protectedText", protectedText)
            put("tokens", JSONArray(tokens)); put("payloads", JSONArray(payloads))
        }.toString()

        /** Reject missing, reordered, repeated, unknown or malformed placeholders. */
        fun restore(cleaned: String): String? {
            val result = StringBuilder()
            var position = 0
            var index = 0
            while (true) {
                val start = cleaned.indexOf(PREFIX, position, ignoreCase = true)
                if (start < 0) break
                if (index >= tokens.size || !cleaned.startsWith(tokens[index], start)) return null
                result.append(cleaned, position, start).append(payloads[index])
                position = start + tokens[index].length
                index++
            }
            if (index != tokens.size) return null
            return result.append(cleaned, position, cleaned.length).toString()
        }
    }

    /** Restores an immutable capture; corrupt state never becomes a new cleanup input. */
    fun deserialize(state: String): Prepared = try {
        val json = JSONObject(state)
        require(json.get("version") == 1)
        val source = json.get("source") as String
        val expanded = json.get("expanded") as String
        val protectedText = json.get("protectedText") as String
        val tokenArray = json.getJSONArray("tokens")
        val payloadArray = json.getJSONArray("payloads")
        require(tokenArray.length() == payloadArray.length())
        val tokens = List(tokenArray.length()) { tokenArray.get(it) as String }
        val payloads = List(payloadArray.length()) { payloadArray.get(it) as String }
        require(!source.contains(PREFIX, ignoreCase = true))
        require(payloads.all { it.isNotBlank() })
        if (tokens.isNotEmpty()) {
            val nonce = Regex("^⟦WISPR_SNIPPET_([a-f0-9]{32})_0⟧$").matchEntire(tokens.first())?.groupValues?.get(1)
            require(nonce != null)
            require(tokens.withIndex().all { (index, token) -> token == "$PREFIX${nonce}_$index$SUFFIX" })
        } else {
            require(protectedText == source && expanded == source)
        }
        Prepared(source, expanded, protectedText, tokens, payloads).also {
            require(it.restore(protectedText) == expanded)
        }
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid saved snippet state")
    }

    fun prepare(text: String, snippets: List<Snippet>): Prepared {
        require(!text.contains(PREFIX, ignoreCase = true)) { "Source contains a reserved snippet placeholder" }
        SnippetValidation.validateAll(snippets)
        val active = snippets.filter { it.enabled }
        if (active.isEmpty()) return Prepared(text, text, text, emptyList(), emptyList())
        // Android does not support Pattern.CANON_EQ. Normalize character clusters
        // while retaining offsets into the untouched recognition text.
        val canonical = StringBuilder()
        val starts = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        val characters = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        var clusterStart = characters.first()
        var clusterEnd = characters.next()
        while (clusterEnd != BreakIterator.DONE) {
            val original = text.substring(clusterStart, clusterEnd)
            val cluster = SnippetValidation.normalizeCase(original)
            canonical.append(cluster)
            repeat(cluster.length) { offset ->
                // Unchanged clusters (including joined emoji) retain exact offsets
                // even if a punctuation-only trigger matches just part of one.
                starts += if (cluster == original) clusterStart + offset else clusterStart
                ends += if (cluster == original) clusterStart + offset + 1 else clusterEnd
            }
            clusterStart = clusterEnd
            clusterEnd = characters.next()
        }
        data class Match(val start: Int, val end: Int, val payload: String)
        val matches = active.flatMap { snippet ->
            val trigger = SnippetValidation.normalizeTrigger(snippet.trigger)
            val words = trigger.split(' ').joinToString("[\\s\\p{Z}\\u0085]+") { Pattern.quote(it) }
            val boundary = "[\\p{L}\\p{M}\\p{N}\\p{Pc}]"
            val matcher = Pattern.compile("(?<!$boundary)$words(?!$boundary)").matcher(canonical)
            buildList {
                while (matcher.find()) add(Match(starts[matcher.start()], ends[matcher.end() - 1], snippet.replacement))
            }
        }.sortedWith(compareBy<Match> { it.start }.thenByDescending { it.end - it.start })

        val expanded = StringBuilder()
        val protectedBuilder = StringBuilder()
        val tokens = mutableListOf<String>()
        val payloads = mutableListOf<String>()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        var position = 0
        for (match in matches) {
            if (match.start < position) continue
            val token = "$PREFIX${nonce}_${tokens.size}$SUFFIX"
            expanded.append(text, position, match.start).append(match.payload)
            protectedBuilder.append(text, position, match.start).append(token)
            tokens += token
            payloads += match.payload
            position = match.end
        }
        expanded.append(text, position, text.length)
        protectedBuilder.append(text, position, text.length)
        return Prepared(text, expanded.toString(), protectedBuilder.toString(), tokens.toList(), payloads.toList())
    }
}
