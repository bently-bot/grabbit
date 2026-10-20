package com.bentlybro.grabbit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatSelectorTest {

    private fun value(args: List<String>, flag: String) = args[args.indexOf(flag) + 1]

    @Test fun bestMergesToMp4() {
        val a = FormatSelector.args(DownloadFormat.BEST)
        assertEquals("bv*+ba/b", value(a, "-f"))
        assertEquals("mp4", value(a, "--merge-output-format"))
        assertFalse("-x" in a)
    }

    @Test fun heightCapsFallBackToBest() {
        for ((fmt, h) in listOf(DownloadFormat.P1080 to 1080, DownloadFormat.P720 to 720, DownloadFormat.P480 to 480)) {
            val a = FormatSelector.args(fmt)
            val f = value(a, "-f")
            assertTrue(f.startsWith("bv*[height<=$h]+ba/b[height<=$h]"))
            assertTrue("$fmt must fall back for sites without heights", f.endsWith("/bv*+ba/b"))
            assertEquals("res:$h,ext:mp4:m4a", value(a, "-S"))
            assertEquals("mp4", value(a, "--merge-output-format"))
        }
    }

    @Test fun audioExtractsMp3() {
        val a = FormatSelector.args(DownloadFormat.AUDIO_MP3)
        assertEquals("ba/b", value(a, "-f"))
        assertTrue("-x" in a)
        assertEquals("mp3", value(a, "--audio-format"))
        assertFalse("--merge-output-format" in a)
    }

    @Test fun everyFormatHasASelector() {
        DownloadFormat.entries.forEach { assertTrue(FormatSelector.selector(it).isNotBlank()) }
    }

    @Test fun commonOptions() {
        val a = FormatSelector.common("/tmp/x/%(title)s.%(ext)s")
        assertTrue("--no-playlist" in a)
        assertEquals("/tmp/x/%(title)s.%(ext)s", value(a, "-o"))
    }

    @Test fun fromNameFallsBackToDefault() {
        assertEquals(DownloadFormat.P720, DownloadFormat.fromName("P720"))
        assertEquals(DownloadFormat.BEST, DownloadFormat.fromName("nope"))
        assertEquals(DownloadFormat.BEST, DownloadFormat.fromName(null))
    }
}
