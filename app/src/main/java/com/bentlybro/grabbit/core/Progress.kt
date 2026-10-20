package com.bentlybro.grabbit.core

/** What a running download is doing right now, derived from yt-dlp's output lines. */
enum class Phase { STARTING, DOWNLOADING, PROCESSING, SAVING }

object ProgressParser {

    private val processors = listOf(
        "[Merger]", "[ExtractAudio]", "[VideoRemuxer]", "[VideoConvertor]",
        "[FixupM3u8]", "[FixupM4a]", "[Metadata]", "[EmbedThumbnail]", "[ffmpeg]",
    )

    /** Phase for one yt-dlp output line, or null when the line says nothing new. */
    fun phaseOf(line: String?): Phase? {
        if (line.isNullOrBlank()) return null
        val l = line.trim()
        return when {
            processors.any { l.startsWith(it) } -> Phase.PROCESSING
            l.startsWith("[download]") -> Phase.DOWNLOADING
            else -> null
        }
    }

    /** yt-dlp reports -1 for unknown; clamp to 0..100. */
    fun percent(raw: Float): Float = if (raw.isNaN() || raw < 0f) 0f else raw.coerceAtMost(100f)

    fun eta(raw: Long): Long? = if (raw <= 0L) null else raw

    /** "1:05:09", "4:07", "0:09" style duration for UI and ETA. */
    fun formatDuration(seconds: Long?): String? {
        if (seconds == null || seconds <= 0) return null
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
