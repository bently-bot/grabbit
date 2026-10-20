package com.bentlybro.grabbit.core

/** What the user wants out of a link. Stored by [name] in settings and the queue. */
enum class DownloadFormat(val label: String, val isAudio: Boolean) {
    BEST("Best", false),
    P1080("1080p", false),
    P720("720p", false),
    P480("480p", false),
    AUDIO_MP3("MP3", true);

    companion object {
        val DEFAULT = BEST
        fun fromName(name: String?): DownloadFormat =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Maps a [DownloadFormat] to yt-dlp arguments.
 *
 * Every video choice ends in a plain `/b` fallback because many sites (TikTok, X, Reddit)
 * don't report heights or only offer a single muxed file; a height filter alone would fail
 * there instead of grabbing the best thing available. Video is always remuxed/merged into
 * mp4 so it plays in any gallery app.
 */
object FormatSelector {

    fun args(format: DownloadFormat): List<String> = when (format) {
        DownloadFormat.BEST -> video(null)
        DownloadFormat.P1080 -> video(1080)
        DownloadFormat.P720 -> video(720)
        DownloadFormat.P480 -> video(480)
        DownloadFormat.AUDIO_MP3 -> listOf(
            "-f", "ba/b",
            "-x",
            "--audio-format", "mp3",
            "--audio-quality", "0",
            "--embed-metadata",
        )
    }

    fun selector(format: DownloadFormat): String = args(format)[1]

    private fun video(maxHeight: Int?): List<String> {
        val selector = if (maxHeight == null) {
            "bv*+ba/b"
        } else {
            "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]/bv*+ba/b"
        }
        val sort = if (maxHeight == null) "res,ext:mp4:m4a" else "res:$maxHeight,ext:mp4:m4a"
        return listOf(
            "-f", selector,
            "-S", sort,
            "--merge-output-format", "mp4",
            "--remux-video", "mp4",
        )
    }

    /** Options shared by every download, independent of format. */
    fun common(outputTemplate: String): List<String> = listOf(
        "--no-playlist",
        "--no-mtime",
        "--newline",
        "-o", outputTemplate,
    )
}
