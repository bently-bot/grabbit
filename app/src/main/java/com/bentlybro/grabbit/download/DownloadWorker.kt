package com.bentlybro.grabbit.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bentlybro.grabbit.AppGraph
import com.bentlybro.grabbit.core.FormatSelector
import com.bentlybro.grabbit.core.Phase
import com.bentlybro.grabbit.core.ProgressParser
import com.bentlybro.grabbit.data.ErrorText
import com.bentlybro.grabbit.data.QueueItem
import com.bentlybro.grabbit.data.Status
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import android.util.Log
import com.bentlybro.grabbit.core.FailureClassifier
import com.bentlybro.grabbit.core.RetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs one queue item: optional info fetch (for instant shares), yt-dlp into private
 * scratch space, then a MediaStore copy. Runs as a foreground `dataSync` service so it keeps
 * going after the user leaves the app.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "grabbit-download"
        const val KEY_ITEM_ID = "item_id"

        /** Two at a time: enough to overlap network waits without starving either. */
        private val slots = Semaphore(2)
    }

    private val itemId: String get() = inputData.getString(KEY_ITEM_ID).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(AppGraph.queue.get(itemId), Phase.STARTING, 0f, null)

    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)
        val queue = AppGraph.queue
        val start = queue.get(itemId) ?: return Result.failure()
        if (start.status == Status.CANCELLED || start.status == Status.DONE) return Result.success()

        runCatching { setForeground(foregroundInfo(start, Phase.STARTING, 0f, null)) }

        return slots.withPermit {
            if (queue.get(itemId)?.status == Status.CANCELLED) return@withPermit Result.success()
            queue.started(itemId)
            val scratch = File(applicationContext.cacheDir, "dl/$itemId")
            try {
                AppGraph.engine.ensureReady()
                // Cheap when fresh: only hits the network if the last check is >24h old.
                AppGraph.engineUpdater.updateIfDue()
                fillInfoIfMissing()
                downloadWithRecovery(scratch)
                val output = pickOutput(scratch) ?: error("yt-dlp finished but produced no file")
                queue.phase(itemId, Phase.SAVING)
                updateNotification(Phase.SAVING, 100f, null)
                val saved = withContext(Dispatchers.IO) { AppGraph.saver.save(output) }
                queue.done(itemId, saved.uri.toString(), saved.displayName, saved.mimeType)
                Notifications.done(applicationContext, itemId, title(), saved.uri, saved.mimeType)
                Result.success()
            } catch (e: CancellationException) {
                YoutubeDL.destroyProcessById(itemId)
                // Cancelled by the user (in-app or the notification) vs. stopped by the
                // system (e.g. network lost), in which case WorkManager will run it again.
                val byUser = Build.VERSION.SDK_INT < 31 ||
                    stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP
                if (byUser) queue.cancelled(itemId) else queue.retried(itemId, id.toString())
                throw e
            } catch (e: YoutubeDL.CanceledException) {
                queue.cancelled(itemId)
                Result.success()
            } catch (t: Throwable) {
                if (queue.get(itemId)?.status == Status.CANCELLED || isStopped) {
                    queue.cancelled(itemId)
                    return@withPermit Result.success()
                }
                val message = ErrorText.from(t.message)
                queue.failed(itemId, message, ErrorText.lastErrorLine(t.message))
                Notifications.failed(applicationContext, itemId, title(), message)
                // Failures are shown with a Retry button; no automatic WorkManager retry.
                Result.failure()
            } finally {
                scratch.deleteRecursively()
            }
        }
    }

    private suspend fun fillInfoIfMissing() {
        val item = AppGraph.queue.get(itemId) ?: return
        if (item.title != null) return
        runCatching { AppGraph.engine.fetchInfo(item.url) }.onSuccess {
            AppGraph.queue.info(itemId, it.title, it.uploader, it.thumbnail, it.durationSec)
        }
    }

    /**
     * Runs yt-dlp, recovering from HTTP 403s per [RetryPolicy]: update the engine and retry,
     * then (YouTube only) retry with alternate player clients. Other failures propagate.
     */
    private suspend fun downloadWithRecovery(scratch: File) {
        val url = AppGraph.queue.get(itemId)?.url ?: return
        var attempt = 0
        var extractorArgs: String? = null
        while (true) {
            AppGraph.queue.attempt(itemId, attempt, AppGraph.engine.versionFlow.value)
            try {
                download(scratch, extractorArgs)
                return
            } catch (e: YoutubeDLException) {
                if (isStopped || AppGraph.queue.get(itemId)?.status == Status.CANCELLED) throw e
                val kind = FailureClassifier.classify(e.message)
                val step = RetryPolicy.next(attempt, kind, RetryPolicy.isYouTube(url)) ?: throw e
                Log.w(TAG, "attempt $attempt failed ($kind); retrying with $step")
                queue().phase(itemId, Phase.STARTING)
                if (step.updateEngine) runCatching { AppGraph.engineUpdater.updateNow() }
                extractorArgs = step.extractorArgs
                attempt++
            }
        }
    }

    private fun queue() = AppGraph.queue

    private suspend fun download(scratch: File, extractorArgs: String?) {
        val item = AppGraph.queue.get(itemId) ?: return
        scratch.deleteRecursively()
        scratch.mkdirs()
        val template = File(scratch, "%(title).80B [%(id)s].%(ext)s").absolutePath
        val request = YoutubeDLRequest(item.url)
        request.addCommands(FormatSelector.common(template) + FormatSelector.args(item.format))
        extractorArgs?.let { request.addOption("--extractor-args", it) }
        var lastUi = 0L
        var lastNotify = 0L
        var phase = Phase.STARTING
        AppGraph.engine.execute(request, itemId) { rawPercent, rawEta, line ->
                ProgressParser.phaseOf(line)?.let { phase = it }
                val percent = ProgressParser.percent(rawPercent)
                val eta = ProgressParser.eta(rawEta)
                val now = System.currentTimeMillis()
                if (now - lastUi > 250) {
                    lastUi = now
                    AppGraph.queue.progress(itemId, percent, eta, phase)
                }
                if (now - lastNotify > 1000) {
                    lastNotify = now
                    updateNotificationBlocking(phase, percent, eta)
                }
        }
    }

    /** The finished file: the biggest thing left that isn't a yt-dlp temp/part file. */
    private fun pickOutput(dir: File): File? = dir.walkTopDown()
        .filter { it.isFile }
        .filterNot { f -> listOf(".part", ".ytdl", ".temp", ".tmp").any { f.name.endsWith(it) } || ".part-Frag" in f.name }
        .maxByOrNull { it.length() }

    private fun title(): String = AppGraph.queue.get(itemId)?.displayTitle ?: "Download"

    private fun foregroundInfo(item: QueueItem?, phase: Phase, percent: Float, eta: Long?): ForegroundInfo {
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val n = Notifications.progress(applicationContext, item?.displayTitle ?: "Grabbing…", phase, percent, eta, cancel)
        return ForegroundInfo(Notifications.progressId(itemId), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private suspend fun updateNotification(phase: Phase, percent: Float, eta: Long?) {
        runCatching { setForeground(foregroundInfo(AppGraph.queue.get(itemId), phase, percent, eta)) }
    }

    private fun updateNotificationBlocking(phase: Phase, percent: Float, eta: Long?) {
        runCatching { setForegroundAsync(foregroundInfo(AppGraph.queue.get(itemId), phase, percent, eta)) }
    }
}
