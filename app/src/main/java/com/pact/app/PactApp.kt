package com.pact.app

import android.app.Application
import com.pact.app.core.Notifications
import com.pact.app.core.PactState
import com.pact.app.core.ReceiptWorker
import com.pact.app.core.SyncWorker
import com.pact.app.core.TrustNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PactApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var liveSync: Job? = null

    override fun onCreate() {
        super.onCreate()

        // Safety net: on ANY crash, record the trace and relaunch straight into
        // MainActivity, which shows it on screen (instead of a silent close).
        // This catches async Compose crashes too, which a passive handler can't.
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runCatching {
                java.io.File(filesDir, "last_crash.txt").writeText(
                    android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL +
                        " · Android " + android.os.Build.VERSION.SDK_INT + "\n\n" +
                        android.util.Log.getStackTraceString(throwable)
                )
            }
            runCatching {
                startActivity(
                    android.content.Intent(this, MainActivity::class.java)
                        .addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                )
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        }

        // Nothing here is allowed to take the app down. If any of it fails, we
        // record why and carry on — the UI can still start, and MainActivity
        // surfaces the recorded reason on this very launch instead of a silent
        // close. (Application.onCreate runs before any Activity, so a crash here
        // would otherwise never reach the on-screen crash reporter.)
        try {
            // Keep home-screen widgets in sync with every state change.
            PactState.get(this).onChanged = { PactWidget.updateAll(this) }

            // Trust network events → quiet, actionable notifications.
            TrustNetwork.get(this).onEvent = { event, name, detail ->
                Notifications.showNetworkEvent(this, event, name, detail)
            }

            runCatching { SyncWorker.schedule(this) }
            runCatching { ReceiptWorker.schedule(this) }
        } catch (t: Throwable) {
            runCatching {
                java.io.File(filesDir, "last_crash.txt").writeText(
                    "Startup init failed:\n\n" + android.util.Log.getStackTraceString(t)
                )
            }
        }
    }

    /**
     * Fast sync loop while anything is on screen (main activity or the lock
     * wall overlay). Refcounted so the wall and the activity don't fight.
     */
    private var liveSyncHolds = 0

    @Synchronized
    fun acquireLiveSync() {
        liveSyncHolds++
        if (liveSync?.isActive == true) return
        liveSync = scope.launch {
            while (true) {
                runCatching { TrustNetwork.get(this@PactApp).syncNow() }
                delay(5_000L)
            }
        }
    }

    @Synchronized
    fun releaseLiveSync() {
        liveSyncHolds = (liveSyncHolds - 1).coerceAtLeast(0)
        if (liveSyncHolds == 0) {
            liveSync?.cancel()
            liveSync = null
        }
    }
}
