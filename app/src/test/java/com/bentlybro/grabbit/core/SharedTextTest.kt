package com.bentlybro.grabbit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextTest {

    @Test fun youtubeShareTextOnly() =
        assertEquals("https://youtu.be/abc?si=xyz", SharedText(text = "https://youtu.be/abc?si=xyz").url())

    @Test fun linkOnlyInSubject() = assertEquals(
        "https://www.instagram.com/p/abc/",
        SharedText(text = "Look at this", subject = "https://www.instagram.com/p/abc/").url(),
    )

    @Test fun linkInClipData() = assertEquals(
        "https://redd.it/abc",
        SharedText(text = null, clipTexts = listOf("shared post", "https://redd.it/abc")).url(),
    )

    @Test fun dataUri() =
        assertEquals("https://vimeo.com/123", SharedText(dataUri = "https://vimeo.com/123").url())

    @Test fun xShareWithTitleAndHandle() = assertEquals(
        "https://x.com/user/status/1234567890",
        SharedText(text = "Funny clip 😂\n\nhttps://x.com/user/status/1234567890", subject = "Post by @user").url(),
    )

    @Test fun nothing() = assertNull(SharedText(text = "no links here", subject = "just a title").url())
}
