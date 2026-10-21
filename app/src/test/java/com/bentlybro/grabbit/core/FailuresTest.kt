package com.bentlybro.grabbit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailuresTest {

    @Test fun classifiesTheReported403() {
        // Exactly what Bently's phone showed on v0.1.0.
        val raw = "ERROR: unable to download video data: HTTP Error 403: Forbidden"
        assertEquals(FailureKind.HTTP_403, FailureClassifier.classify(raw))
    }

    @Test fun botCheckWinsOverA403() {
        val raw = "WARNING: HTTP Error 403: Forbidden\nERROR: [youtube] x: Sign in to confirm you're not a bot"
        assertEquals(FailureKind.BOT_CHECK, FailureClassifier.classify(raw))
    }

    @Test fun blockedKinds() {
        assertEquals(FailureKind.IP_BLOCKED, FailureClassifier.classify("ERROR: [TikTok] 1: Your IP address is blocked from accessing this post"))
        assertEquals(FailureKind.LOGIN_REQUIRED, FailureClassifier.classify("ERROR: [vimeo] 1: The web client only works when logged-in. Use --cookies"))
        assertEquals(FailureKind.LOGIN_REQUIRED, FailureClassifier.classify("ERROR: [Reddit] 1: Account authentication is required."))
        assertEquals(FailureKind.RATE_LIMITED, FailureClassifier.classify("ERROR: HTTP Error 429: Too Many Requests"))
        assertTrue(FailureKind.IP_BLOCKED.isBlocked)
        assertFalse(FailureKind.HTTP_403.isBlocked)
    }

    @Test fun otherKinds() {
        assertEquals(FailureKind.UNSUPPORTED, FailureClassifier.classify("ERROR: Unsupported URL: https://example.com"))
        assertEquals(FailureKind.UNAVAILABLE, FailureClassifier.classify("ERROR: [youtube] x: This video is unavailable"))
        assertEquals(FailureKind.OTHER, FailureClassifier.classify("ERROR: something odd"))
        assertEquals(FailureKind.OTHER, FailureClassifier.classify(null))
    }

    @Test fun retryPolicyFor403OnYouTube() {
        val first = RetryPolicy.next(0, FailureKind.HTTP_403, isYouTube = true)!!
        assertTrue(first.updateEngine)
        assertNull(first.extractorArgs)
        val second = RetryPolicy.next(1, FailureKind.HTTP_403, isYouTube = true)!!
        assertFalse(second.updateEngine)
        assertEquals(RetryPolicy.YOUTUBE_FALLBACK_ARGS, second.extractorArgs)
        assertNull(RetryPolicy.next(2, FailureKind.HTTP_403, isYouTube = true))
    }

    @Test fun retryPolicyElsewhere() {
        assertTrue(RetryPolicy.next(0, FailureKind.HTTP_403, isYouTube = false)!!.updateEngine)
        assertNull(RetryPolicy.next(1, FailureKind.HTTP_403, isYouTube = false))
        assertNull(RetryPolicy.next(0, FailureKind.BOT_CHECK, isYouTube = true))
        assertNull(RetryPolicy.next(0, FailureKind.OTHER, isYouTube = true))
    }

    @Test fun youTubeHosts() {
        assertTrue(RetryPolicy.isYouTube("https://www.youtube.com/watch?v=jNQXAC9IVRw"))
        assertTrue(RetryPolicy.isYouTube("https://youtu.be/jNQXAC9IVRw?si=x"))
        assertTrue(RetryPolicy.isYouTube("https://m.youtube.com/shorts/abc"))
        assertTrue(RetryPolicy.isYouTube("https://music.youtube.com/watch?v=abc"))
        assertFalse(RetryPolicy.isYouTube("https://notyoutube.com/watch"))
        assertFalse(RetryPolicy.isYouTube("https://www.tiktok.com/@a/video/1"))
    }
}
