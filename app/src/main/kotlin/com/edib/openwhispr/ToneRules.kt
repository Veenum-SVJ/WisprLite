package com.edib.openwhispr

import android.content.SharedPreferences
import org.json.JSONObject

enum class Tone(val title: String, val instruction: String) {
    NEUTRAL("Neutral", "Preserve the speaker's register and wording; do not restyle."),
    FORMAL("Formal", "Use a clear professional register, preserving the exact meaning, names, numbers and intent. Do not add facts or draft new content."),
    CASUAL("Casual", "Use a natural conversational register. Preserve meaning, names, numbers and intent; do not add new content."),
    VERY_CASUAL("Very casual", "Use a relaxed conversational register without inventing slang or facts; preserve names, numbers and intent."),
    EXCITED("Enthusiastic", "Use an upbeat register with restrained punctuation. Preserve the original facts, names, numbers and intent.")
}
enum class ToneCategory(val title: String, val defaultTone: Tone) {
    PERSONAL("Personal messages", Tone.CASUAL), WORK("Work messages", Tone.FORMAL), EMAIL("Emails", Tone.FORMAL), OTHER("Other apps", Tone.NEUTRAL)
}
data class AppToneRule(val category: ToneCategory, val tone: Tone? = null)
data class ToneConfiguration(val enabled: Boolean = false, val categories: Map<ToneCategory, Tone> = emptyMap(), val apps: Map<String, AppToneRule> = emptyMap()) {
    fun categoryFor(packageName: String?): ToneCategory = apps[packageName]?.category ?: when (packageName) {
        "com.whatsapp", "org.thoughtcrime.securesms", "org.telegram.messenger", "com.google.android.apps.messaging" -> ToneCategory.PERSONAL
        "com.Slack", "com.microsoft.teams" -> ToneCategory.WORK
        "com.google.android.gm", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail" -> ToneCategory.EMAIL
        else -> ToneCategory.OTHER
    }
    fun toneFor(packageName: String?): Tone = if (!enabled) Tone.NEUTRAL else
        apps[packageName]?.tone ?: categoryFor(packageName).let { categories[it] ?: it.defaultTone }
}
object ToneRuleCodec {
    fun encode(value: ToneConfiguration): String = JSONObject().put("version", 1).put("enabled", value.enabled)
        .put("categories", JSONObject().apply { value.categories.forEach { (k,v) -> put(k.name,v.name) } })
        .put("apps", JSONObject().apply { value.apps.forEach { (k,v) -> put(k, JSONObject().put("category",v.category.name).put("tone",v.tone?.name)) } }).toString()
    fun decode(value: String?): ToneConfiguration {
        if (value.isNullOrBlank()) return ToneConfiguration()
        val obj = JSONObject(value)
        require(obj.getInt("version") == 1)
        val categories = obj.getJSONObject("categories")
        val apps = obj.getJSONObject("apps")
        require(apps.length() <= 5000)
        return ToneConfiguration(obj.getBoolean("enabled"), categories.keys().asSequence().associate { key ->
            ToneCategory.valueOf(key) to Tone.valueOf(categories.getString(key))
        }, apps.keys().asSequence().associate { key ->
            require(key.length <= 255 && key.matches(Regex("[A-Za-z][A-Za-z0-9_.]*")))
            val rule = apps.getJSONObject(key)
            key to AppToneRule(ToneCategory.valueOf(rule.getString("category")), if (rule.isNull("tone")) null else Tone.valueOf(rule.getString("tone")))
        })
    }
}
class ToneRules(private val prefs: SharedPreferences) {
    companion object { const val KEY = "tone_rules_v1"; const val NEXT_KEY = "tone_next_override" }
    fun configuration(): ToneConfiguration = runCatching { ToneRuleCodec.decode(prefs.getString(KEY,null)) }.getOrDefault(ToneConfiguration())
    fun save(value: ToneConfiguration) { prefs.edit().putString(KEY,ToneRuleCodec.encode(value)).apply() }
    fun overrideNext(tone: Tone?) { prefs.edit().apply { if(tone==null) remove(NEXT_KEY) else putString(NEXT_KEY,tone.name) }.apply() }
    fun nextOverride(): Tone? = prefs.getString(NEXT_KEY,null)?.let { runCatching { Tone.valueOf(it) }.getOrNull() }
    fun consumeFor(packageName: String?): Tone {
        val next = nextOverride(); prefs.edit().remove(NEXT_KEY).apply()
        return next ?: configuration().toneFor(packageName)
    }
    fun setAppCategory(packageName: String, category: ToneCategory) {
        val config=configuration(); save(config.copy(apps=config.apps + (packageName to AppToneRule(category))))
    }
}
