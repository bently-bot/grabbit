package com.bentlybro.grabbit.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bentlybro.grabbit.core.DownloadFormat
import com.bentlybro.grabbit.data.QueueItem
import com.bentlybro.grabbit.data.QueueRepository
import com.bentlybro.grabbit.engine.MediaInfo
import com.yausername.youtubedl_android.YoutubeDL
import java.util.UUID
import java.util.concurrent.TimeUnit

/** The only place that talks to WorkManager. */
class DownloadScheduler(private val context: Context, private val queue: QueueRepository) {

    private val workManager get() = WorkManager.getInstance(context)

    /** Adds a download to the queue and starts it in the background. Returns the item id. */
    fun enqueue(url: String, format: DownloadFormat, info: MediaInfo? = null): String {
        val id = UUID.randomUUID().toString()
        val request = buildRequest(id)
        queue.add(
            QueueItem(
                id = id,
                url = url,
                format = format,
                title = info?.title,
                uploader = info?.uploader,
                thumbnail = info?.thumbnail,
                durationSec = info?.durationSec,
                createdAt = System.currentTimeMillis(),
                workId = request.id.toString(),
            )
        )
        workManager.enqueue(request)
        return id
    }

    fun cancel(itemId: String) {
        val item = queue.get(itemId) ?: return
        // Mark first so the UI reacts instantly and the worker's late updates are ignored.
        queue.cancelled(itemId)
        YoutubeDL.destroyProcessById(itemId)
        item.workId?.let { workManager.cancelWorkById(UUID.fromString(it)) }
    }

    fun retry(itemId: String) {
        val item = queue.get(itemId) ?: return
        if (item.isActive) return
        val request = buildRequest(itemId)
        queue.retried(itemId, request.id.toString())
        workManager.enqueue(request)
    }

    fun remove(itemId: String) {
        val item = queue.get(itemId) ?: return
        if (item.isActive) cancel(itemId)
        queue.remove(itemId)
    }

    fun clearFinished() = queue.clearFinished()

    /** Called once per process start; see [com.bentlybro.grabbit.data.QueueReducer.reconcile]. */
    fun reconcile() {
        queue.reconcile { workId ->
            runCatching {
                val info = workManager.getWorkInfoById(UUID.fromString(workId)).get(5, TimeUnit.SECONDS)
                info != null && !info.state.isFinished && info.state != WorkInfo.State.CANCELLED
            }.getOrDefault(false)
        }
    }

    private fun buildRequest(itemId: String) = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setInputData(workDataOf(DownloadWorker.KEY_ITEM_ID to itemId))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
        .addTag(DownloadWorker.TAG)
        .build()

}
