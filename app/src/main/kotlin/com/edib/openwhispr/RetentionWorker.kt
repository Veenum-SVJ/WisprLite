package com.edib.openwhispr

import android.content.Context
import androidx.work.*
import java.io.File
import java.util.concurrent.TimeUnit

class RetentionWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try {
        val prefs = applicationContext.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
        RetentionPolicy.sweepAudio(File(applicationContext.filesDir, "recordings"), prefs.getInt("audio_retention_days", 0), System.currentTimeMillis())
        val days = prefs.getInt("text_retention_days", 0)
        if (days in listOf(7,30,90)) HistoryRepository(applicationContext).use { it.expireText(System.currentTimeMillis() - days * RetentionPolicy.DAY_MILLIS) }
        Result.success()
    } catch (_: Exception) { Result.retry() }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("wisprlite-retention", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build())
            runSoon(context)
        }
        fun runSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("wisprlite-retention-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RetentionWorker>().build())
        }
    }
}
