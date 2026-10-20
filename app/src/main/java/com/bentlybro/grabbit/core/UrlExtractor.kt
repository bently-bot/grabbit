package com.bentlybro.grabbit.core

/**
 * Pulls the video link out of whatever an app shares or the clipboard holds.
 *
 * Share text is messy: TikTok sends "Check out this video! https://vm.tiktok.com/x/ #fyp",
 * Instagram and X wrap links in prose, some apps put the link in EXTRA_SUBJECT, and people
 * paste links without a scheme. The rules, in order:
 *
 * 1. Collect every http(s) URL across all the given texts (TEXT first, then SUBJECT).
 * 2. Trim trailing punctuation and unbalanced closing brackets/quotes.
 * 3. Prefer the first URL whose host is a known video site; otherwise the first URL.
 * 4. If there is no http(s) URL at all, accept a bare `host.tld/path` and add https://.
 */
object UrlExtractor {

    private val schemeUrl = Regex("""(?i)\bhttps?://[^\s<>"'`]+""")

    // A bare link like "youtu.be/abc" or "www.tiktok.com/@a/video/1": needs a dot in the host
    // and either a path or a www. prefix, so ordinary words with a full stop don't match.
    private val bareUrl = Regex(
        """(?i)(?<![@\w.])((?:www\.)?(?:[a-z0-9-]+\.)+[a-z]{2,}(?:/[^\s<>"'`]*)?)"""
    )

    private val knownHosts = listOf(
        "youtube.com", "youtu.be", "tiktok.com", "instagram.com", "x.com", "twitter.com",
        "reddit.com", "redd.it", "facebook.com", "fb.watch", "vimeo.com", "twitch.tv",
        "soundcloud.com", "dailymotion.com", "bsky.app", "threads.net", "pinterest.com",
        "pin.it", "streamable.com", "bilibili.com", "snapchat.com", "tumblr.com", "kick.com",
    )

    /** The best URL found in [texts], or null when none of them contains a link. */
    fun extract(vararg texts: String?): String? {
        val candidates = texts.filterNotNull().flatMap { text ->
            schemeUrl.findAll(text).map { clean(it.value) }.filter { it.isNotEmpty() }.toList()
        }
        if (candidates.isNotEmpty()) {
            return candidates.firstOrNull { isKnownVideoHost(it) } ?: candidates.first()
        }
        val bare = texts.filterNotNull().flatMap { text ->
            bareUrl.findAll(text).map { clean(it.groupValues[1]) }
                .filter { looksLikeLink(it) }.toList()
        }
        val pick = bare.firstOrNull { isKnownVideoHost("https://$it") } ?: bare.firstOrNull()
        return pick?.let { "https://$it" }
    }

    /** True when [text] contains something [extract] would return. */
    fun containsUrl(text: String?): Boolean = extract(text) != null

    fun host(url: String): String? =
        Regex("""(?i)^https?://([^/:?#]+)""").find(url)?.groupValues?.get(1)?.lowercase()

    fun isKnownVideoHost(url: String): Boolean {
        val host = host(url) ?: return false
        return knownHosts.any { host == it || host.endsWith(".$it") }
    }

    private fun looksLikeLink(s: String): Boolean {
        if (s.startsWith("www.", ignoreCase = true)) return true
        val host = s.substringBefore('/')
        return '/' in s && host.contains('.') && knownHosts.any { host.lowercase().endsWith(it) } ||
            '/' in s && s.substringAfter('/').isNotEmpty() && host.substringAfterLast('.').length in 2..6 &&
            host.substringAfterLast('.').all { it.isLetter() }
    }

    internal fun clean(raw: String): String {
        var s = raw.trim()
        val trailing = ".,;:!?…'\"’”»"
        while (s.isNotEmpty()) {
            val last = s.last()
            s = when {
                last in trailing -> s.dropLast(1)
                last == ')' && s.count { it == '(' } < s.count { it == ')' } -> s.dropLast(1)
                last == ']' && s.count { it == '[' } < s.count { it == ']' } -> s.dropLast(1)
                last == '}' && s.count { it == '{' } < s.count { it == '}' } -> s.dropLast(1)
                else -> return s
            }
        }
        return s
    }
}
