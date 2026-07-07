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
        // Keep home-screen widgets in sync with every state change.
        PactState.get(this).onChanged = { PactWidget.updateAll(this) }

        // Trust network events → quiet, actionable notifications.
        TrustNetwork.get(this).onEvent = { event, name, detail ->
            Notifications.showNetworkEvent(this, event, name, detail)
        }

        SyncWorker.schedule(this)
        ReceiptWorker.schedule(this)
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
