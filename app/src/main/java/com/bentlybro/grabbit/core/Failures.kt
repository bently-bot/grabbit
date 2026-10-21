package com.bentlybro.grabbit.core

/** Why a yt-dlp run failed, as far as we can tell from its stderr. */
enum class FailureKind {
    /** The media server refused the stream. On YouTube: stale engine / client mismatch. */
    HTTP_403,
    BOT_CHECK,
    RATE_LIMITED,
    IP_BLOCKED,
    LOGIN_REQUIRED,
    UNAVAILABLE,
    UNSUPPORTED,
    NETWORK,
    OTHER;

    /** The site is refusing *us* (IP, account, rate), not a bug in the app or engine. */
    val isBlocked: Boolean get() = this == BOT_CHECK || this == RATE_LIMITED || this == IP_BLOCKED || this == LOGIN_REQUIRED
}

object FailureClassifier {

    // Order matters: a bot check often comes back alongside a 403, and it is the real cause.
    private val rules = listOf(
        FailureKind.BOT_CHECK to Regex("(?i)confirm you.?re not a bot|sign in to confirm"),
        FailureKind.RATE_LIMITED to Regex("(?i)http error 429|too many requests|rate.?limit"),
        FailureKind.IP_BLOCKED to Regex("(?i)ip address is blocked|blocked from accessing|your ip"),
        FailureKind.LOGIN_REQUIRED to Regex(
            "(?i)login required|requires authentication|account authentication is required|" +
                "only works when logged.?in|log in to|use --cookies|age.?restricted"
        ),
        FailureKind.HTTP_403 to Regex("(?i)http error 403|403: forbidden"),
        FailureKind.UNAVAILABLE to Regex(
            "(?i)private video|video unavailable|this video is unavailable|has been removed|" +
                "no longer available|no video could be found|does not exist|not found"
        ),
        FailureKind.UNSUPPORTED to Regex("(?i)unsupported url"),
        FailureKind.NETWORK to Regex("(?i)unable to download webpage|failed to resolve|network is unreachable|timed out|connection reset"),
    )

    fun classify(raw: String?): FailureKind {
        if (raw.isNullOrBlank()) return FailureKind.OTHER
        return rules.firstOrNull { it.second.containsMatchIn(raw) }?.first ?: FailureKind.OTHER
    }
}

/** What to change before trying a failed download again. */
data class RetryStep(
    /** Force a yt-dlp self-update before the next attempt. */
    val updateEngine: Boolean,
    /** Extra `--extractor-args` value for the next attempt, or null for yt-dlp's defaults. */
    val extractorArgs: String?,
)

/**
 * Automatic recovery for HTTP 403s. A 403 on the media URL almost always means the engine's
 * idea of YouTube's clients is out of date, so: update yt-dlp and retry once with its
 * defaults; on YouTube, if that still 403s, retry once more pinned to alternate player
 * clients that return plain (non-SABR) URLs without a PO token. Anything else fails fast.
 */
object RetryPolicy {

    /** Tried after the post-update retry, see yt-dlp's PO Token Guide for the client table. */
    const val YOUTUBE_FALLBACK_ARGS = "youtube:player_client=android,web_embedded"

    const val MAX_RETRIES = 2

    /** [failedAttempt] is 0 for the first try. Returns null when it's time to give up. */
    fun next(failedAttempt: Int, kind: FailureKind, isYouTube: Boolean): RetryStep? {
        if (kind != FailureKind.HTTP_403) return null
        return when (failedAttempt) {
            0 -> RetryStep(updateEngine = true, extractorArgs = null)
            1 -> if (isYouTube) RetryStep(updateEngine = false, extractorArgs = YOUTUBE_FALLBACK_ARGS) else null
            else -> null
        }
    }

    private val youtubeHost = Regex("""(?i)^https?://([a-z0-9-]+\.)*(youtube\.com|youtu\.be|youtube-nocookie\.com)(/|$)""")

    fun isYouTube(url: String): Boolean = youtubeHost.containsMatchIn(url.trim())
}
