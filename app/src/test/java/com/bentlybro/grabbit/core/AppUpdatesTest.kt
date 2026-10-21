package com.bentlybro.grabbit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {

    private val sha = "a".repeat(64)

    @Test fun versions() {
        assertEquals(listOf(0, 2, 0), AppUpdates.parseVersion("v0.2.0"))
        assertEquals(listOf(1, 10), AppUpdates.parseVersion("1.10-beta"))
        assertNull(AppUpdates.parseVersion("latest"))
        assertTrue(AppUpdates.compareVersions("0.10.0", "0.9.9") > 0)
        assertTrue(AppUpdates.compareVersions("v0.2.0", "0.2") == 0)
        assertTrue(AppUpdates.compareVersions("0.1.0", "0.2.0") < 0)
        assertTrue(AppUpdates.compareVersions("junk", "0.0.1") < 0)
    }

    @Test fun newerByNameThenCode() {
        assertTrue(AppUpdates.isNewer("0.3.0", null, "0.2.0", 2000))
        assertFalse(AppUpdates.isNewer("0.2.0", null, "0.2.0", 2000))
        assertFalse(AppUpdates.isNewer("0.1.9", 99999, "0.2.0", 2000))
        assertTrue(AppUpdates.isNewer("0.2.0", 2001, "0.2.0", 2000))
        assertFalse(AppUpdates.isNewer("0.2.0", 2000, "0.2.0", 2000))
    }

    @Test fun checksums() {
        val text = "$sha  grabbit-0.2.0-arm64-v8a.apk\n${"B".repeat(64)} *grabbit-0.2.0-x86_64.apk\n\ngarbage line\n"
        val m = AppUpdates.parseChecksums(text)
        assertEquals(sha, m["grabbit-0.2.0-arm64-v8a.apk"])
        assertEquals("b".repeat(64), m["grabbit-0.2.0-x86_64.apk"])
        assertEquals(2, m.size)
    }

    @Test fun picksApkForAbi() {
        val assets = listOf("SHA256SUMS", "grabbit-0.2.0-armeabi-v7a.apk", "grabbit-0.2.0-arm64-v8a.apk", "grabbit-0.2.0-x86_64.apk")
            .map { GhAsset(it, 1, "https://x/$it") }
        assertEquals("grabbit-0.2.0-arm64-v8a.apk", AppUpdates.pickApk(assets, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))?.name)
        assertEquals("grabbit-0.2.0-armeabi-v7a.apk", AppUpdates.pickApk(assets, listOf("armeabi-v7a", "armeabi"))?.name)
        assertEquals("grabbit-0.2.0-x86_64.apk", AppUpdates.pickApk(assets, listOf("x86_64", "arm64-v8a"))?.name)
        assertNull(AppUpdates.pickApk(assets, listOf("riscv64")))
    }

    @Test fun parsesGitHubRelease() {
        val json = """
            {"tag_name":"v0.2.0","name":"Grabbit 0.2.0","draft":false,"prerelease":false,
             "html_url":"https://github.com/o/r/releases/tag/v0.2.0","body":"Fixes\r\n\r\n**Full Changelog**: x",
             "author":{"login":"o"},
             "assets":[{"name":"update.json","size":60,"browser_download_url":"https://x/update.json","id":1}]}
        """.trimIndent()
        val r = AppUpdates.parseRelease(json)
        assertEquals("v0.2.0", r.tagName)
        assertEquals("https://x/update.json", r.assets.single().url)
        assertEquals("Fixes", AppUpdates.notesForDialog(r.body))
        assertEquals("No release notes.", AppUpdates.notesForDialog(null))
        val m = AppUpdates.parseManifest("""{"versionName":"0.2.0","versionCode":2000,"minSdk":29,"extra":1}""")
        assertEquals(2000, m.versionCode)
    }
}
