package com.bentlybro.grabbit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlExtractorTest {

    @Test fun plainLink() =
        assertEquals("https://youtu.be/dQw4w9WgXcQ", UrlExtractor.extract("https://youtu.be/dQw4w9WgXcQ"))

    @Test fun surroundingWhitespaceAndNewlines() =
        assertEquals("https://youtu.be/abc", UrlExtractor.extract("\n  https://youtu.be/abc  \n"))

    @Test fun tiktokShareTextWithExtraWords() = assertEquals(
        "https://vm.tiktok.com/ZMabc123/",
        UrlExtractor.extract("Check out this video! https://vm.tiktok.com/ZMabc123/ #fyp #foryou"),
    )

    @Test fun tiktokLongShareText() = assertEquals(
        "https://www.tiktok.com/@someone/video/7312345678901234567?_t=8abc&_r=1",
        UrlExtractor.extract(
            "@someone posted a video on TikTok, have a look! " +
                "https://www.tiktok.com/@someone/video/7312345678901234567?_t=8abc&_r=1 Download TikTok now."
        ),
    )

    @Test fun instagramMultilineShare() = assertEquals(
        "https://www.instagram.com/reel/C1a2b3c4d5e/?igsh=MWQ1ZGUxMzBkMA==",
        UrlExtractor.extract("Watch this reel by @creator\non Instagram\nhttps://www.instagram.com/reel/C1a2b3c4d5e/?igsh=MWQ1ZGUxMzBkMA=="),
    )

    @Test fun trailingPunctuationIsTrimmed() {
        assertEquals("https://x.com/user/status/123", UrlExtractor.extract("look: https://x.com/user/status/123."))
        assertEquals("https://x.com/user/status/123", UrlExtractor.extract("(https://x.com/user/status/123)"))
        assertEquals("https://x.com/user/status/123", UrlExtractor.extract("\"https://x.com/user/status/123\"!"))
        assertEquals("https://youtu.be/abc", UrlExtractor.extract("wow https://youtu.be/abc…"))
    }

    @Test fun balancedParenthesesInUrlAreKept() = assertEquals(
        "https://en.wikipedia.org/wiki/Rabbit_(disambiguation)",
        UrlExtractor.extract("see https://en.wikipedia.org/wiki/Rabbit_(disambiguation)"),
    )

    @Test fun prefersKnownVideoHostOverOtherLinks() = assertEquals(
        "https://www.reddit.com/r/videos/comments/abc123/title/",
        UrlExtractor.extract("via https://example.com/blog and https://www.reddit.com/r/videos/comments/abc123/title/"),
    )

    @Test fun fallsBackToFirstUnknownLink() =
        assertEquals("https://example.com/v/1", UrlExtractor.extract("go https://example.com/v/1 or https://other.org/2"))

    @Test fun textBeatsSubject() = assertEquals(
        "https://youtu.be/fromText",
        UrlExtractor.extract("https://youtu.be/fromText", "https://youtu.be/fromSubject"),
    )

    @Test fun usesSubjectWhenTextHasNoLink() = assertEquals(
        "https://youtu.be/fromSubject",
        UrlExtractor.extract("Shared from the app", "Great video https://youtu.be/fromSubject"),
    )

    @Test fun knownHostInSubjectBeatsUnknownInText() = assertEquals(
        "https://youtu.be/abc",
        UrlExtractor.extract("https://example.com/tracking", "https://youtu.be/abc"),
    )

    @Test fun bareLinkWithoutSchemeGetsHttps() {
        assertEquals("https://youtu.be/abc", UrlExtractor.extract("youtu.be/abc"))
        assertEquals("https://www.youtube.com/watch?v=abc", UrlExtractor.extract("www.youtube.com/watch?v=abc"))
    }

    @Test fun ordinaryProseIsNotALink() {
        assertNull(UrlExtractor.extract("hello there. this is fine."))
        assertNull(UrlExtractor.extract("email me at someone@example.com"))
        assertNull(UrlExtractor.extract(""))
        assertNull(UrlExtractor.extract(null))
    }

    @Test fun uppercaseScheme() =
        assertEquals("HTTPS://YOUTU.BE/abc", UrlExtractor.extract("HTTPS://YOUTU.BE/abc"))

    @Test fun hostAndKnownHostChecks() {
        assertEquals("m.youtube.com", UrlExtractor.host("https://m.youtube.com/watch?v=1"))
        assert(UrlExtractor.isKnownVideoHost("https://m.youtube.com/watch?v=1"))
        assert(UrlExtractor.isKnownVideoHost("https://vm.tiktok.com/x"))
        assert(!UrlExtractor.isKnownVideoHost("https://notyoutube.com.evil.io/x"))
        assert(!UrlExtractor.isKnownVideoHost("https://box.com/x"))
    }
}
