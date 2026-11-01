package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Opt-in measurement, excluded from the default device suite. An OK result means
 * the evaluation completed, not that a provider passed a language accuracy gate.
 * Only the two hash-pinned public clips can leave the device. No settings change.
 */
@RunWith(AndroidJUnit4::class)
class PublicCorpusEvaluationTest {
    @Test fun measureSavedProviderOnPinnedPublicSpeech() {
        val arguments = InstrumentationRegistry.getArguments()
        val provider = arguments.getString("publicCorpusProvider")
        require(provider in setOf("groq", "google"))
        val lowReasoning = arguments.getString("googleReasoningLow") == "true"
        require(!lowReasoning || provider == "google")
        val trial = if (lowReasoning) "${provider}_low" else provider
        require(arguments.getString("confirmedCloudTrial") == "public_corpus_$trial")
        val host = if (provider == "groq") "api.groq.com" else "generativelanguage.googleapis.com"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selected = ProviderProfiles.locked {
            val profile = ProviderProfiles(context).all().single { java.net.URI(it.baseUrl).host == host }
            ProviderProfiles.SpeechSelection(
                ProviderConfig(profile.baseUrl, profile.speechModel, apiFormat = profile.speechFormat),
                SecureKeyStorage.profileApiKey(context, profile.id),
            )
        }
        assertTrue("Configure the saved provider key in WisprLite", selected.key.isNotBlank())
        val fixtures = listOf(
            Triple("yoruba.wav", DictationLanguage.YORUBA, "f99f74cd579a9e63dfdde864856b6111426019dbd345557584c8519bc95306bb"),
            Triple("pidgin.wav", DictationLanguage.PIDGIN, "35d76258b77a840c166d698696d08144e2e1ac9ca1f28c738a1b42f636ff47e4"),
        )
        val directory = File(context.cacheDir, "public-corpus-eval")
        // Validate every fixture before making any paid request.
        val audio = fixtures.map { (name, _, hash) ->
            val file = File(directory, name)
            assertTrue("Provision the pinned public fixture $name", file.isFile && file.length() in 1..2_000_000)
            file.readBytes().also { bytes ->
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                assertEquals("Only the pinned public audio may be uploaded", hash, actual)
            }
        }
        val results = JSONArray()
        for ((index, fixture) in fixtures.withIndex()) {
            val (name, language, hash) = fixture
            val completed = CountDownLatch(1)
            val response = AtomicReference<TranscriberClient.Result>()
            val started = System.nanoTime()
            val config = selected.config.copy(recognitionHint = language.recognitionHint)
            val call = if (lowReasoning) {
                // Explicit experiment only: never changes production requests or saved profiles.
                val original = TranscriberClient.transcriptionRequest(audio[index], selected.key, emptyList(),
                    setOf(requireNotNull(language.languageCode)), config)
                val buffer = okio.Buffer()
                requireNotNull(original.body).writeTo(buffer)
                val body = JSONObject(buffer.readUtf8()).put("reasoning_effort", "low").toString()
                val request = original.newBuilder().post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
                val pending = okhttp3.OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build().newCall(request)
                try {
                    pending.execute().use { reply ->
                        response.set(if (reply.isSuccessful) TranscriberClient.parseResponse(reply.body?.string().orEmpty(), config.apiFormat)
                            else TranscriberClient.Result(null, "Transcription provider returned HTTP ${reply.code}"))
                    }
                } catch (_: java.io.IOException) { response.set(TranscriberClient.Result(null, "request_failed")) }
                completed.countDown()
                pending
            } else TranscriberClient.transcribe(audio[index], selected.key, emptyList(),
                setOf(requireNotNull(language.languageCode)),
                config) {
                response.set(it); completed.countDown()
            }
            try {
                val finished = completed.await(100, TimeUnit.SECONDS)
                val result = response.get()
                val error = if (!finished) "evaluation_deadline" else result?.error?.let {
                    if (it.matches(Regex("Transcription provider returned HTTP [0-9]{3}"))) it else "request_failed"
                }
                results.put(JSONObject().put("fixture", name).put("sha256", hash)
                    .put("language", language.name).put("model", selected.config.model)
                    .put("elapsed_ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    .put("error", error ?: JSONObject.NULL)
                    .put("text", result?.text ?: JSONObject.NULL))
                // Persist after each clip so a later interruption doesn't erase evidence.
                File(directory, "$trial-results.json").writeText(
                    JSONObject().put("provider", trial).put("results", results).toString(), Charsets.UTF_8)
            } finally { call?.cancel() }
        }
        assertEquals(2, results.length())
        println("PUBLIC_CORPUS_EVALUATION_RECORDED: provider=$provider; clips=2; accuracy requires reference scoring")
    }
}
