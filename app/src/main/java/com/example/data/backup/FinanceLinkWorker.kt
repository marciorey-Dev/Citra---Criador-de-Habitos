package com.example.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.PreferencesManager
import java.util.concurrent.TimeUnit

class FinanceLinkWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        if (!prefs.financesEnabled.value) {
            return Result.success()
        }
        return try {
            FinanceLinkExecutor.runDueLinks(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "citra_finance_note_links_periodic"

        fun syncSchedule(context: Context, financesEnabled: Boolean) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!financesEnabled) {
                wm.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<FinanceLinkWorker>(
                6, TimeUnit.HOURS
            ).build()
            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
