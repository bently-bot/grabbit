package com.bentlybro.grabbit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressAndMediaTest {

    @Test fun phases() {
        assertEquals(Phase.DOWNLOADING, ProgressParser.phaseOf("[download]  42.0% of 10.00MiB at 1.00MiB/s ETA 00:06"))
        assertEquals(Phase.PROCESSING, ProgressParser.phaseOf("[Merger] Merging formats into \"x.mp4\""))
        assertEquals(Phase.PROCESSING, ProgressParser.phaseOf("[ExtractAudio] Destination: x.mp3"))
        assertNull(ProgressParser.phaseOf("[youtube] abc: Downloading webpage"))
        assertNull(ProgressParser.phaseOf(null))
    }

    @Test fun percentAndEta() {
        assertEquals(0f, ProgressParser.percent(-1f))
        assertEquals(0f, ProgressParser.percent(Float.NaN))
        assertEquals(100f, ProgressParser.percent(140f))
        assertEquals(42.5f, ProgressParser.percent(42.5f))
        assertNull(ProgressParser.eta(-1))
        assertNull(ProgressParser.eta(0))
        assertEquals(12L, ProgressParser.eta(12))
    }

    @Test fun durations() {
        assertEquals("0:09", ProgressParser.formatDuration(9))
        assertEquals("4:07", ProgressParser.formatDuration(247))
        assertEquals("1:05:09", ProgressParser.formatDuration(3909))
        assertNull(ProgressParser.formatDuration(0))
        assertNull(ProgressParser.formatDuration(null))
    }

    @Test fun mediaKinds() {
        assertEquals(MediaKind.VIDEO, MediaKind.forFileName("clip [abc].mp4"))
        assertEquals(MediaKind.AUDIO, MediaKind.forFileName("song.MP3"))
        assertEquals(MediaKind.OTHER, MediaKind.forFileName("pic.jpg"))
        assertEquals("Movies/Grabbit", MediaKind.VIDEO.relativeDir)
        assertEquals("Music/Grabbit", MediaKind.AUDIO.relativeDir)
        assertEquals("video/mp4", MediaKind.mimeType("a.mp4"))
        assertEquals("audio/mpeg", MediaKind.mimeType("a.mp3"))
        assertEquals("application/octet-stream", MediaKind.mimeType("a.bin"))
    }

    @Test fun sanitizedNames() {
        assertEquals("a_b_c.mp4", FileNames.sanitize("a/b:c.mp4"))
        assertEquals("grabbit", FileNames.sanitize("..."))
        val long = FileNames.sanitize("x".repeat(300) + ".mp4", maxLength = 50)
        assertEquals(50, long.length)
        assertTrue(long.endsWith(".mp4"))
    }
}
