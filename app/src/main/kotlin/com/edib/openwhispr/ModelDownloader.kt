package com.edib.openwhispr

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.*
import java.util.concurrent.TimeUnit

enum class ModelArchitecture { NEMO_CTC, WHISPER, NEMO_TRANSDUCER, MOONSHINE }

data class Model(
    val name: String,
    val archive: String,
    val sizeMb: Int,
    val qualityResId: Int,
    val architecture: ModelArchitecture,
    val hotwordModelingUnit: String? = null,
    val recommended: Boolean = false,
    /** Language codes the model transcribes. English-only models list just "en". */
    val languages: Set<String> = setOf("en"),
    val downloadBytes: Long = sizeMb * 1_000_000L,
) {
    val publisher: String get() = when (architecture) {
        ModelArchitecture.WHISPER -> "OpenAI"
        ModelArchitecture.NEMO_CTC, ModelArchitecture.NEMO_TRANSDUCER -> "NVIDIA"
        ModelArchitecture.MOONSHINE -> "Moonshine"
    }
    val brandLogo: Int get() = when (architecture) {
        ModelArchitecture.WHISPER -> R.drawable.model_brand_openai
        ModelArchitecture.NEMO_CTC, ModelArchitecture.NEMO_TRANSDUCER -> R.drawable.model_brand_nvidia
        ModelArchitecture.MOONSHINE -> R.drawable.model_brand_moonshine
    }
}

private val PARAKEET_V3_LANGUAGES = setOf(
    "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "hu", "it", "lv", "lt",
    "mt", "pl", "pt", "ro", "sk", "sl", "es", "sv", "ru", "uk",
)

private val WHISPER_LANGUAGES = TranscriptionLanguages.CODES.toSet()

val MODEL_CATALOG = listOf(
    Model("Parakeet 110M", "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8",
        105, R.string.model_quality_best_value, ModelArchitecture.NEMO_CTC, downloadBytes = 104337827),
    Model("Whisper Base (English)", "sherpa-onnx-whisper-base.en",
        209, R.string.model_quality_stars, ModelArchitecture.WHISPER, downloadBytes = 208576005),
    Model("Parakeet 0.6B", "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
        488, R.string.model_quality_best, ModelArchitecture.NEMO_TRANSDUCER, hotwordModelingUnit = "cjkchar",
        languages = PARAKEET_V3_LANGUAGES, downloadBytes = 487170055),
    Model("Moonshine Tiny", "sherpa-onnx-moonshine-tiny-en-int8",
        108, R.string.model_quality_fast, ModelArchitecture.MOONSHINE, downloadBytes = 107600538),
    Model("Whisper Base (multilingual)", "sherpa-onnx-whisper-base",
        208, R.string.model_quality_stars, ModelArchitecture.WHISPER, recommended = true,
        languages = WHISPER_LANGUAGES, downloadBytes = 207557382),
    Model("Whisper Tiny (multilingual)", "sherpa-onnx-whisper-tiny",
        117, R.string.model_quality_fast, ModelArchitecture.WHISPER,
        languages = WHISPER_LANGUAGES, downloadBytes = 116204861),
    Model("Whisper Tiny (English)", "sherpa-onnx-whisper-tiny.en",
        119, R.string.model_quality_fast, ModelArchitecture.WHISPER, downloadBytes = 118071777),
    Model("Whisper Small (multilingual)", "sherpa-onnx-whisper-small",
        640, R.string.model_quality_best, ModelArchitecture.WHISPER, languages = WHISPER_LANGUAGES,
        downloadBytes = 639387718),
)

sealed class DownloadState {
    data class Downloading(val progress: Float) : DownloadState()
    object Extracting : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object ModelDownloader {
    private val activeDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private const val BASE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"
    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS).build()

    fun modelDir(ctx: Context, model: Model) =
        File(ctx.filesDir, "models/${model.archive}")

    fun isInstalled(ctx: Context, model: Model) =
        LocalTranscriber.isModelComplete(modelDir(ctx, model))

    // Known packs contain float and int8 variants: peak usage includes the
    // compressed download plus staged extraction, not just the active weights.
    internal fun minimumFreeBytes(model: Model): Long = model.downloadBytes * 4L + 64L * 1024 * 1024

    /** Download and extract model. Callbacks fire on background thread. */
    fun download(ctx: Context, model: Model, onState: (DownloadState) -> Unit) {
        if (!activeDownloads.add(model.archive)) { onState(DownloadState.Error("This model is already downloading")); return }
        val url = "$BASE_URL/${model.archive}.tar.bz2"
        val tmpFile = File(ctx.cacheDir, "${model.archive}.tar.bz2")
        val outDir = File(ctx.filesDir, "models")

        Thread {
            try {
                val requiredSpace = minimumFreeBytes(model)
                require(ctx.filesDir.usableSpace > requiredSpace) {
                    "Not enough storage. Keep at least ${android.text.format.Formatter.formatFileSize(ctx, requiredSpace)} free to download and unpack this model."
                }
                downloadFile(url, tmpFile, ctx.getString(R.string.empty_download_response), onState)
                onState(DownloadState.Extracting)
                installArchive(tmpFile, outDir, model)
                onState(DownloadState.Done)
            } catch (e: Exception) {
                onState(DownloadState.Error(e.message ?: ctx.getString(R.string.unknown_error)))
            } finally {
                tmpFile.delete()
                activeDownloads.remove(model.archive)
            }
        }.start()
    }

    internal fun installArchive(archive: File, modelsRoot: File, model: Model) {
        SafeModelArchive.install(archive, modelsRoot, model.archive) { candidate ->
            if (!LocalTranscriber.isModelComplete(candidate)) false
            else {
                if (model.architecture == ModelArchitecture.WHISPER) ModelStorage.compactQuantizedWhisperPack(candidate)
                LocalTranscriber.isModelComplete(candidate)
            }
        }
    }

    fun delete(ctx: Context, model: Model) =
        modelDir(ctx, model).deleteRecursively()

    internal fun downloadFile(
        url: String, dest: File, emptyResponseMessage: String, onState: (DownloadState) -> Unit
    ) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val body = response.body ?: throw IOException(emptyResponseMessage)
        val total = body.contentLength()
        var downloaded = 0L

        body.byteStream().use { src ->
            FileOutputStream(dest).use { dst ->
                val buf = ByteArray(16384)
                var n: Int
                while (src.read(buf).also { n = it } != -1) {
                    dst.write(buf, 0, n)
                    downloaded += n
                    if (total > 0)
                        onState(DownloadState.Downloading(downloaded.toFloat() / total))
                }
            }
        }
        require(total < 0 || downloaded == total) { "Incomplete model download" }
        }
    }

    /** Extract tar.bz2 to outDir. Validates paths to prevent traversal. */
    fun extractTarBz2(archive: File, outDir: File) {
        SafeModelArchive.extract(archive, outDir)
    }
}
