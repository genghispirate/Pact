package com.pact.app.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Fires the weekly "receipt" nudge. Runs daily and only actually notifies on
 * Sunday, once per week — so the drop lands like clockwork without a precise
 * alarm. (WorkManager can't guarantee exact times; a daily check is plenty.)
 */
class ReceiptWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            val prefs = applicationContext.getSharedPreferences("pact_receipt", Context.MODE_PRIVATE)
            val week = cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.WEEK_OF_YEAR)
            if (prefs.getInt("last_week", 0) != week) {
                prefs.edit().putInt("last_week", week).apply()
                runCatching { Notifications.showWeeklyReceipt(applicationContext) }
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReceiptWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "pact_receipt",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
