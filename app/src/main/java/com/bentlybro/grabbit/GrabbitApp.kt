package com.bentlybro.grabbit

import android.app.Application
import com.bentlybro.grabbit.download.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GrabbitApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        Notifications.createChannels(this)
        scope.launch {
            // Unpack Python/yt-dlp early so the first preview isn't also the slowest, then
            // bring yt-dlp up to date: the copy bundled in the APK is months old by the time
            // anyone installs it, and YouTube in particular rejects stale clients (HTTP 403).
            runCatching { AppGraph.engine.ensureReady() }
            runCatching { AppGraph.engine.version() }
            AppGraph.engineUpdater.updateIfDue()
        }
        AppGraph.engineUpdater.schedulePeriodic()
        scope.launch(Dispatchers.IO) { AppGraph.scheduler.reconcile() }
    }
}
