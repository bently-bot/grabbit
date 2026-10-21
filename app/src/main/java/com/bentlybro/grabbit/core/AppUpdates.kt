package com.bentlybro.grabbit.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The bits of GitHub's `GET /repos/{owner}/{repo}/releases/latest` response we use. */
@Serializable
data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
data class GhAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val url: String,
)

/** `update.json`, published next to the APKs by the release workflow. Optional. */
@Serializable
data class UpdateManifest(
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int? = null,
)

data class AvailableUpdate(
    val versionName: String,
    val versionCode: Int?,
    val notes: String,
    val apk: GhAsset,
    val sha256: String,
    val pageUrl: String?,
)

object AppUpdates {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    const val CHECKSUMS_ASSET = "SHA256SUMS"
    const val MANIFEST_ASSET = "update.json"

    fun parseRelease(text: String): GhRelease = json.decodeFromString(GhRelease.serializer(), text)
    fun parseManifest(text: String): UpdateManifest = json.decodeFromString(UpdateManifest.serializer(), text)

    /** "v1.2.3" / "1.2.3-beta" -> [1, 2, 3]; null if it isn't a version. */
    fun parseVersion(raw: String): List<Int>? {
        val core = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.isEmpty() || parts.size > 4) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    /** Numeric compare of dotted versions; missing parts count as 0. Unparseable sorts lowest. */
    fun compareVersions(a: String, b: String): Int {
        val va = parseVersion(a) ?: return if (parseVersion(b) == null) 0 else -1
        val vb = parseVersion(b) ?: return 1
        for (i in 0 until maxOf(va.size, vb.size)) {
            val c = va.getOrElse(i) { 0 }.compareTo(vb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /**
     * Is [remoteName]/[remoteCode] newer than what's installed? The name decides; the code
     * (when the release publishes one) breaks ties, e.g. a re-cut build of the same version.
     */
    fun isNewer(remoteName: String, remoteCode: Int?, installedName: String, installedCode: Int): Boolean {
        val c = compareVersions(remoteName, installedName)
        if (c != 0) return c > 0
        return remoteCode != null && remoteCode > installedCode
    }

    /** `sha256sum` output ("<hex>  <name>" or "<hex> *<name>") -> name to lowercase hex. */
    fun parseChecksums(text: String): Map<String, String> = text.lines()
        .mapNotNull { line ->
            val m = Regex("""^([0-9a-fA-F]{64})\s+\*?(.+?)\s*$""").find(line.trim()) ?: return@mapNotNull null
            m.groupValues[2] to m.groupValues[1].lowercase()
        }.toMap()

    /**
     * The APK for this device: release assets are named `grabbit-<version>-<abi>.apk`, and
     * [supportedAbis] is `Build.SUPPORTED_ABIS` (most preferred first). A universal APK, if
     * a release ever ships one, is the last resort.
     */
    fun pickApk(assets: List<GhAsset>, supportedAbis: List<String>): GhAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        for (abi in supportedAbis) {
            apks.firstOrNull { it.name.endsWith("-$abi.apk", ignoreCase = true) }?.let { return it }
        }
        return apks.firstOrNull { it.name.endsWith("-universal.apk", ignoreCase = true) }
    }

    /** Release notes trimmed for a dialog: drop GitHub's "Full Changelog" footer, cap length. */
    fun notesForDialog(body: String?, max: Int = 1500): String {
        val text = body.orEmpty()
            .lines()
            .filterNot { it.trim().startsWith("**Full Changelog**") }
            .joinToString("\n")
            .trim()
        return when {
            text.isEmpty() -> "No release notes."
            text.length > max -> text.take(max - 1).trimEnd() + "…"
            else -> text
        }
    }
}
