package com.bentlybro.grabbit.data

import com.bentlybro.grabbit.core.DownloadFormat
import com.bentlybro.grabbit.core.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueReducerTest {

    private fun item(id: String, status: Status = Status.QUEUED, workId: String? = "w-$id") =
        QueueItem(id = id, url = "https://youtu.be/$id", format = DownloadFormat.BEST, status = status, workId = workId)

    @Test fun addPutsNewestFirstAndDeduplicates() {
        val q = QueueReducer.add(QueueReducer.add(emptyList(), item("a")), item("b"))
        assertEquals(listOf("b", "a"), q.map { it.id })
        assertEquals(2, QueueReducer.add(q, item("a")).size)
    }

    @Test fun lifecycleHappyPath() {
        var q = listOf(item("a"))
        q = QueueReducer.started(q, "a")
        assertEquals(Status.RUNNING, q[0].status)
        q = QueueReducer.progress(q, "a", 42f, 10, Phase.DOWNLOADING)
        assertEquals(42f, q[0].progress)
        assertEquals(10L, q[0].etaSec)
        q = QueueReducer.phase(q, "a", Phase.PROCESSING)
        assertEquals(Phase.PROCESSING, q[0].phase)
        assertNull(q[0].etaSec)
        q = QueueReducer.done(q, "a", "content://x/1", "a.mp4", "video/mp4")
        assertEquals(Status.DONE, q[0].status)
        assertEquals(100f, q[0].progress)
        assertEquals("content://x/1", q[0].outputUri)
    }

    @Test fun progressIsClampedAndIgnoredWhenNotRunning() {
        var q = QueueReducer.started(listOf(item("a")), "a")
        q = QueueReducer.progress(q, "a", 250f, null, null)
        assertEquals(100f, q[0].progress)
        q = QueueReducer.cancelled(q, "a")
        q = QueueReducer.progress(q, "a", 10f, 5, Phase.DOWNLOADING)
        assertEquals(Status.CANCELLED, q[0].status)
        assertEquals(100f, q[0].progress)
    }

    @Test fun cancelWinsOverLateFailureAndStart() {
        var q = QueueReducer.cancelled(listOf(item("a")), "a")
        q = QueueReducer.failed(q, "a", "boom")
        assertEquals(Status.CANCELLED, q[0].status)
        q = QueueReducer.started(q, "a")
        assertEquals(Status.CANCELLED, q[0].status)
    }

    @Test fun cancelDoesNotUndoDone() {
        var q = QueueReducer.done(listOf(item("a", Status.RUNNING)), "a", "u", "n", "m")
        q = QueueReducer.cancelled(q, "a")
        assertEquals(Status.DONE, q[0].status)
    }

    @Test fun retryResets() {
        var q = QueueReducer.failed(QueueReducer.started(listOf(item("a")), "a"), "a", "HTTP 403")
        q = QueueReducer.retried(q, "a", "w2")
        assertEquals(Status.QUEUED, q[0].status)
        assertEquals("w2", q[0].workId)
        assertNull(q[0].error)
        assertEquals(0f, q[0].progress)
    }

    @Test fun infoOnlyFillsMissingFields() {
        val q = QueueReducer.info(listOf(item("a").copy(title = "Mine")), "a", "Theirs", "Up", "t.jpg", 30)
        assertEquals("Mine", q[0].title)
        assertEquals("Up", q[0].uploader)
        assertEquals(30L, q[0].durationSec)
    }

    @Test fun clearFinishedKeepsActiveAndFailed() {
        val q = listOf(
            item("q"), item("r", Status.RUNNING), item("d", Status.DONE),
            item("f", Status.FAILED), item("c", Status.CANCELLED),
        )
        assertEquals(listOf("q", "r", "f"), QueueReducer.clearFinished(q).map { it.id })
    }

    @Test fun reconcileMarksOrphansFailed() {
        val q = listOf(item("alive", Status.RUNNING), item("dead", Status.RUNNING), item("noWork", workId = null), item("done", Status.DONE))
        val r = QueueReducer.reconcile(q) { it == "w-alive" }
        assertEquals(Status.QUEUED, r[0].status)
        assertEquals(Status.FAILED, r[1].status)
        assertEquals("Interrupted", r[1].error)
        assertEquals(Status.FAILED, r[2].status)
        assertEquals(Status.DONE, r[3].status)
    }

    @Test fun errorText() {
        assertEquals("Download failed", ErrorText.from(null))
        assertEquals(
            "This site isn't supported.",
            ErrorText.from("WARNING: something\nERROR: Unsupported URL: https://example.com\n"),
        )
        assertEquals("Video unavailable.", ErrorText.from("ERROR: [youtube] abc: Video unavailable"))
        assertEquals(
            ErrorText.YOUTUBE_BOT_CHECK,
            ErrorText.from(
                "ERROR: [youtube] jNQXAC9IVRw: Sign in to confirm you\u2019re not a bot. Use --cookies-from-browser " +
                    "or --cookies for the authentication."
            ),
        )
        assertEquals("Too many requests. Wait a bit and try again.", ErrorText.from("ERROR: HTTP Error 429: Too Many Requests"))
        // Unknown errors still get the generic cleanup.
        assertEquals("Something odd happened", ErrorText.from("ERROR: [generic] x: Something odd happened"))
    }

    @Test fun displayTitleFallsBackToUrl() {
        assertEquals("https://youtu.be/a", item("a").displayTitle)
        assertEquals("T", item("a").copy(title = "T").displayTitle)
    }

    @Test fun forbiddenMapsToFriendlyEngineHint() {
        assertEquals(
            ErrorText.FORBIDDEN,
            ErrorText.from("ERROR: unable to download video data: HTTP Error 403: Forbidden"),
        )
        assertEquals(
            "ERROR: unable to download video data: HTTP Error 403: Forbidden",
            ErrorText.lastErrorLine("[youtube] x\nERROR: unable to download video data: HTTP Error 403: Forbidden\n"),
        )
    }

    @Test fun finalErrorLineBeatsEarlierWarnings() {
        // What the Reddit run on the CI emulator produced.
        val raw = "WARNING: [Reddit] HTTP Error 403: Forbidden\nERROR: [Reddit] 6rrwyj: Account authentication is required."
        assertEquals("This site needs a login to download that.", ErrorText.from(raw))
    }

    @Test fun failedKeepsRawDetail() {
        val q = QueueReducer.failed(QueueReducer.started(listOf(item("a")), "a"), "a", "nice", "ERROR: raw")
        assertEquals("ERROR: raw", q.single().errorDetail)
        val r = QueueReducer.attempt(q, "a", 2, "2026.10.10")
        assertEquals(2, r.single().autoRetries)
        assertEquals("2026.10.10", r.single().engineVersion)
    }
}
