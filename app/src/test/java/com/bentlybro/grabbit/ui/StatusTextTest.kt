package com.bentlybro.grabbit.ui

import com.bentlybro.grabbit.core.DownloadFormat
import com.bentlybro.grabbit.core.Phase
import com.bentlybro.grabbit.data.QueueItem
import com.bentlybro.grabbit.data.Status
import com.bentlybro.grabbit.ui.components.statusText
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusTextTest {
    private val base = QueueItem(id = "a", url = "u", format = DownloadFormat.BEST)

    @Test fun texts() {
        assertEquals("Waiting…", statusText(base))
        assertEquals(
            "42% · 1:05 left",
            statusText(base.copy(status = Status.RUNNING, phase = Phase.DOWNLOADING, progress = 42f, etaSec = 65)),
        )
        assertEquals("Merging…", statusText(base.copy(status = Status.RUNNING, phase = Phase.PROCESSING)))
        assertEquals(
            "Converting to MP3…",
            statusText(base.copy(format = DownloadFormat.AUDIO_MP3, status = Status.RUNNING, phase = Phase.PROCESSING)),
        )
        assertEquals("Saved · a.mp4", statusText(base.copy(status = Status.DONE, outputName = "a.mp4")))
        assertEquals("HTTP 403", statusText(base.copy(status = Status.FAILED, error = "HTTP 403")))
    }
}
