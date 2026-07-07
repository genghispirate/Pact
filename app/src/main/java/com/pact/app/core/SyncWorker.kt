package com.pact.app.core

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background sync: drains the outbox and fetches the inbox on a periodic
 * schedule so requests, approvals, and messages arrive even when the app is
 * closed. While the app is open, a fast in-app loop takes over (see PactApp).
 *
 * Honest limitation, by design: with no app-owned servers there is no push —
 * background delivery latency is the WorkManager interval (~15 min minimum).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { TrustNetwork.get(applicationContext).syncNow() }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "pact_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
