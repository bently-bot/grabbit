package com.bentlybro.grabbit.engine

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bentlybro.grabbit.AppGraph
import com.bentlybro.grabbit.data.EngineChannel
import com.bentlybro.grabbit.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Decides *when* to self-update yt-dlp; [Engine.update] does the work. */
class EngineUpdater(
    private val context: Context,
    private val engine: Engine,
    private val settings: SettingsRepository,
) {
    companion object {
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        private const val PERIODIC_WORK = "grabbit-engine-update"
        private const val TAG = "GrabbitEngine"

        /** Pure: is an update check due? Exposed for tests. */
        fun isDue(now: Long, lastCheck: Long, desired: EngineChannel, installed: EngineChannel?, maxAgeMs: Long = MAX_AGE_MS) =
            installed != desired || lastCheck <= 0 || now - lastCheck >= maxAgeMs || now < lastCheck
    }

    data class Status(val running: Boolean = false, val lastMessage: String? = null)

    private val lock = Mutex()
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Update if the last check is older than [maxAgeMs] (or the channel changed). Never throws. */
    suspend fun updateIfDue(maxAgeMs: Long = MAX_AGE_MS, timeoutMs: Long = 90_000): Engine.UpdateResult? {
        val s = settings.current()
        if (!s.autoUpdateEngine && s.installedEngineChannel == s.engineChannel) return null
        if (!isDue(System.currentTimeMillis(), s.lastEngineCheck, s.engineChannel, s.installedEngineChannel, maxAgeMs)) return null
        return withTimeoutOrNull(timeoutMs) { runCatching { updateNow() }.getOrNull() }
    }

    /** Update now regardless of age. Throws on network/IO failure. */
    suspend fun updateNow(): Engine.UpdateResult = lock.withLock {
        val channel = settings.current().engineChannel
        _status.value = Status(running = true)
        try {
            val result = engine.update(channel)
            if (result != Engine.UpdateResult.BUSY) settings.engineChecked(System.currentTimeMillis(), channel)
            Log.i(TAG, "yt-dlp update ($channel): $result -> ${engine.versionFlow.value}")
            _status.value = Status(lastMessage = when (result) {
                Engine.UpdateResult.UPDATED -> "Updated to ${engine.versionFlow.value}"
                Engine.UpdateResult.ALREADY_UP_TO_DATE -> "Already up to date"
                Engine.UpdateResult.BUSY -> "Busy downloading; will update when it's done"
            })
            result
        } catch (t: Throwable) {
            Log.w(TAG, "yt-dlp update failed", t)
            _status.value = Status(lastMessage = "Update failed: ${t.message ?: t.javaClass.simpleName}")
            throw t
        }
    }

    /** Daily background check, on any network. Safe to call on every launch. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<EngineUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class EngineUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)
        // Slightly under a day so a worker that fires a little early still updates.
        AppGraph.engineUpdater.updateIfDue(maxAgeMs = 20L * 60 * 60 * 1000)
        return Result.success()
    }
}
