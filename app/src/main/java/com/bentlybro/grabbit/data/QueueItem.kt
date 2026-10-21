package com.bentlybro.grabbit.data

import com.bentlybro.grabbit.core.DownloadFormat
import com.bentlybro.grabbit.core.Phase
import kotlinx.serialization.Serializable

enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

/** One entry in the download queue. Persisted as JSON; progress fields are live-only. */
@Serializable
data class QueueItem(
    val id: String,
    val url: String,
    val format: DownloadFormat,
    val title: String? = null,
    val uploader: String? = null,
    val thumbnail: String? = null,
    val durationSec: Long? = null,
    val status: Status = Status.QUEUED,
    val createdAt: Long = 0L,
    val workId: String? = null,
    val progress: Float = 0f,
    val etaSec: Long? = null,
    val phase: Phase = Phase.STARTING,
    val error: String? = null,
    /** The raw last yt-dlp error line, for bug reports and the CI test matrix. */
    val errorDetail: String? = null,
    /** yt-dlp version that ran the last attempt. */
    val engineVersion: String? = null,
    /** How many automatic retries the last run used (403 recovery). */
    val autoRetries: Int = 0,
    val outputUri: String? = null,
    val outputName: String? = null,
    val mimeType: String? = null,
) {
    val isActive: Boolean get() = status == Status.QUEUED || status == Status.RUNNING
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: url
}

/**
 * Pure state transitions for the queue, kept separate from storage and Android so they can
 * be unit tested. Every function returns a new list; unknown ids are left untouched.
 */
object QueueReducer {

    fun add(items: List<QueueItem>, item: QueueItem): List<QueueItem> =
        listOf(item) + items.filterNot { it.id == item.id }

    fun update(items: List<QueueItem>, id: String, f: (QueueItem) -> QueueItem): List<QueueItem> =
        items.map { if (it.id == id) f(it) else it }

    fun started(items: List<QueueItem>, id: String) = update(items, id) {
        if (it.status == Status.CANCELLED) it
        else it.copy(status = Status.RUNNING, progress = 0f, etaSec = null, phase = Phase.STARTING, error = null)
    }

    /**
     * yt-dlp downloads video and audio as two streams, so percent goes 0→100 twice; we show
     * it as-is but never let a stale update pull a finished/cancelled item back to RUNNING.
     */
    fun progress(items: List<QueueItem>, id: String, percent: Float, eta: Long?, phase: Phase?) =
        update(items, id) {
            if (it.status != Status.RUNNING) it
            else it.copy(progress = percent.coerceIn(0f, 100f), etaSec = eta, phase = phase ?: it.phase)
        }

    fun phase(items: List<QueueItem>, id: String, phase: Phase) = update(items, id) {
        if (it.status != Status.RUNNING) it else it.copy(phase = phase, etaSec = null)
    }

    fun info(items: List<QueueItem>, id: String, title: String?, uploader: String?, thumbnail: String?, duration: Long?) =
        update(items, id) {
            it.copy(
                title = it.title ?: title,
                uploader = it.uploader ?: uploader,
                thumbnail = it.thumbnail ?: thumbnail,
                durationSec = it.durationSec ?: duration,
            )
        }

    fun done(items: List<QueueItem>, id: String, uri: String, name: String, mime: String) = update(items, id) {
        it.copy(status = Status.DONE, progress = 100f, etaSec = null, outputUri = uri, outputName = name, mimeType = mime, error = null)
    }

    fun failed(items: List<QueueItem>, id: String, error: String, detail: String? = null) = update(items, id) {
        if (it.status == Status.CANCELLED) it
        else it.copy(status = Status.FAILED, etaSec = null, error = error, errorDetail = detail)
    }

    fun attempt(items: List<QueueItem>, id: String, retries: Int, engineVersion: String?) = update(items, id) {
        it.copy(autoRetries = retries, engineVersion = engineVersion ?: it.engineVersion)
    }

    fun cancelled(items: List<QueueItem>, id: String) = update(items, id) {
        if (it.status == Status.DONE) it else it.copy(status = Status.CANCELLED, etaSec = null)
    }

    /** Back to the queue with a fresh work id, keeping the preview info. */
    fun retried(items: List<QueueItem>, id: String, newWorkId: String) = update(items, id) {
        it.copy(status = Status.QUEUED, workId = newWorkId, progress = 0f, etaSec = null, phase = Phase.STARTING, error = null)
    }

    fun remove(items: List<QueueItem>, id: String) = items.filterNot { it.id == id }

    fun clearFinished(items: List<QueueItem>) = items.filter { it.isActive || it.status == Status.FAILED }

    /**
     * After a process restart, items that claim to be active but whose work is gone (the
     * worker died with the process and WorkManager won't re-run it) are marked failed so
     * they can be retried instead of spinning forever.
     */
    fun reconcile(items: List<QueueItem>, isWorkAlive: (String) -> Boolean) = items.map {
        if (it.isActive && (it.workId == null || !isWorkAlive(it.workId))) {
            it.copy(status = Status.FAILED, error = "Interrupted", etaSec = null)
        } else if (it.status == Status.RUNNING) {
            it.copy(status = Status.QUEUED)
        } else it
    }
}

/** Short, human error message from a yt-dlp stderr dump. */
object ErrorText {
    const val YOUTUBE_BOT_CHECK =
        "YouTube blocked this request. Try again later, or update the engine in Settings."

    const val FORBIDDEN =
        "The site refused the download (HTTP 403), even after updating the engine. " +
            "Try again later, or tap Update engine now in Settings."

    /** Well-known failures mapped to plain English; checked before the generic cleanup. */
    private val friendly = listOf(
        Regex("(?i)confirm you.?re not a bot|sign in to confirm your age") to YOUTUBE_BOT_CHECK,
        Regex("(?i)unsupported url") to "This site isn't supported.",
        Regex("(?i)private video") to "This video is private.",
        Regex("(?i)video unavailable|has been removed|no longer available") to "Video unavailable.",
        Regex("(?i)http error 429|too many requests") to "Too many requests. Wait a bit and try again.",
        Regex("(?i)ip address is blocked|blocked from accessing") to "The site is blocking this network. Try another connection.",
        Regex("(?i)http error 403|403: forbidden") to FORBIDDEN,
        Regex("(?i)unable to download webpage|failed to resolve|network is unreachable|timed out") to
            "Couldn't reach the site. Check your connection.",
        Regex("(?i)login required|requires authentication|account authentication is required|only works when logged.?in|log in to") to
            "This site needs a login to download that.",
    )

    /** The last `ERROR:` line (or last line) of a yt-dlp dump, uncleaned, capped. */
    fun lastErrorLine(raw: String?): String? {
        val lines = raw?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        return (lines.lastOrNull { it.startsWith("ERROR:") } ?: lines.lastOrNull())?.take(400)
    }

    fun from(raw: String?): String {
        if (raw.isNullOrBlank()) return "Download failed"
        // The final ERROR line is the real cause; earlier lines can be retried warnings
        // (e.g. a 403 on one client before a login wall), so match it first.
        val last = lastErrorLine(raw)
        if (last != null) friendly.firstOrNull { it.first.containsMatchIn(last) }?.let { return it.second }
        friendly.firstOrNull { it.first.containsMatchIn(raw) }?.let { return it.second }
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val err = lines.lastOrNull { it.startsWith("ERROR:") } ?: lines.lastOrNull() ?: return "Download failed"
        return err.removePrefix("ERROR:").trim()
            .replace(Regex("""^\[[^\]]+]\s*(?:[\w-]+:\s+)?"""), "")
            .let { if (it.length > 200) it.take(197) + "..." else it }
            .ifBlank { "Download failed" }
    }
}
