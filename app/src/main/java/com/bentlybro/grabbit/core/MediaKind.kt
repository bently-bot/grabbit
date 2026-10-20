package com.bentlybro.grabbit.core

/** Where a finished file lands in shared storage, picked from its extension. */
enum class MediaKind(val relativeDir: String) {
    VIDEO("Movies/Grabbit"),
    AUDIO("Music/Grabbit"),
    OTHER("Download/Grabbit");

    companion object {
        private val video = mapOf(
            "mp4" to "video/mp4", "m4v" to "video/mp4", "webm" to "video/webm",
            "mkv" to "video/x-matroska", "mov" to "video/quicktime", "3gp" to "video/3gpp",
        )
        private val audio = mapOf(
            "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac",
            "opus" to "audio/ogg", "ogg" to "audio/ogg", "wav" to "audio/wav", "flac" to "audio/flac",
        )
        private val other = mapOf(
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp",
        )

        fun forFileName(name: String): MediaKind {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                in video -> VIDEO
                in audio -> AUDIO
                else -> OTHER
            }
        }

        fun mimeType(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return video[ext] ?: audio[ext] ?: other[ext] ?: "application/octet-stream"
        }
    }
}

/** File names safe for MediaStore display names: no path separators or control chars. */
object FileNames {
    fun sanitize(name: String, maxLength: Int = 120): String {
        val cleaned = name.map { c -> if (c.isISOControl() || c in "/\\:*?\"<>|") '_' else c }
            .joinToString("").trim().trimStart('.')
        if (cleaned.isEmpty()) return "grabbit"
        if (cleaned.length <= maxLength) return cleaned
        val ext = cleaned.substringAfterLast('.', "")
        val base = cleaned.substringBeforeLast('.')
        return if (ext.isNotEmpty() && ext.length <= 5) {
            base.take(maxLength - ext.length - 1).trimEnd() + "." + ext
        } else {
            cleaned.take(maxLength)
        }
    }
}
