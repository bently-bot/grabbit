package com.bentlybro.grabbit.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import com.bentlybro.grabbit.BuildConfig
import com.bentlybro.grabbit.core.AppUpdates
import com.bentlybro.grabbit.core.AvailableUpdate
import com.bentlybro.grabbit.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What the update UI should show. One instance shared by the main screen and Settings. */
sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val version: String) : UpdateUi
    data class Available(val update: AvailableUpdate) : UpdateUi
    data class Downloading(val update: AvailableUpdate, val fraction: Float) : UpdateUi
    data class ReadyToInstall(val update: AvailableUpdate, val apk: File) : UpdateUi
    /** The user must allow "Install unknown apps" for Grabbit first. */
    data class NeedsInstallPermission(val update: AvailableUpdate, val apk: File) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

/**
 * Over-the-air app updates from GitHub Releases ([BuildConfig.UPDATE_REPO]):
 *
 * 1. `GET /repos/{repo}/releases/latest` (drafts and pre-releases are never "latest").
 * 2. Newer than us? (version name, then `update.json`'s versionCode as a tie-breaker.)
 * 3. Download the APK for this device's ABI into app cache, verify its SHA-256 against the
 *    release's `SHA256SUMS`, and check it is signed with the same key as this install.
 * 4. Hand it to the system package installer through a FileProvider URI.
 *
 * No token is used or embedded, so the repo's releases must be publicly readable.
 */
class AppUpdater(private val context: Context, private val settings: SettingsRepository) {

    companion object {
        const val AUTO_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000
        private const val API = "https://api.github.com"
    }

    private val _ui = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val ui: StateFlow<UpdateUi> = _ui.asStateFlow()

    val repo: String get() = BuildConfig.UPDATE_REPO
    val enabled: Boolean get() = repo.contains('/')

    private val updatesDir get() = File(context.cacheDir, "updates")

    fun dismiss() {
        _ui.value = UpdateUi.Idle
    }

    suspend fun skip(version: String) {
        settings.skipAppVersion(version)
        dismiss()
    }

    /** Quiet launch-time check: at most every 12h, ignores skipped versions, never shows errors. */
    suspend fun autoCheck() {
        if (!enabled || !BuildConfig.AUTO_UPDATE_CHECK) return
        val s = settings.current()
        val now = System.currentTimeMillis()
        if (now - s.lastAppUpdateCheck in 0 until AUTO_CHECK_INTERVAL_MS) return
        val update = runCatching { fetchLatest() }.getOrNull() ?: return
        settings.appUpdateChecked(now)
        if (update.versionName != s.skippedAppVersion && _ui.value == UpdateUi.Idle) {
            _ui.value = UpdateUi.Available(update)
        }
    }

    /** "Check for updates" in Settings: always reports a result. */
    suspend fun checkNow() {
        if (!enabled) {
            _ui.value = UpdateUi.Failed("Updates are switched off in this build.")
            return
        }
        _ui.value = UpdateUi.Checking
        _ui.value = try {
            val update = fetchLatest()
            settings.appUpdateChecked(System.currentTimeMillis())
            if (update == null) UpdateUi.UpToDate(BuildConfig.VERSION_NAME) else UpdateUi.Available(update)
        } catch (e: UpdateException) {
            UpdateUi.Failed(e.message ?: "Update check failed")
        } catch (e: IOException) {
            UpdateUi.Failed("Couldn't reach GitHub. Check your connection.")
        }
    }

    /** Null when we're already on the newest release. */
    suspend fun fetchLatest(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val (code, body) = get("$API/repos/$repo/releases/latest", accept = "application/vnd.github+json")
        when (code) {
            200 -> Unit
            404 -> throw UpdateException("No published releases found at github.com/$repo (is the repo public?).")
            403, 429 -> throw UpdateException("GitHub is rate-limiting update checks. Try again in a bit.")
            else -> throw UpdateException("Update check failed (HTTP $code).")
        }
        val release = AppUpdates.parseRelease(body)
        val remoteName = release.tagName.removePrefix("v")
        val manifest = release.assets.firstOrNull { it.name == AppUpdates.MANIFEST_ASSET }?.let { asset ->
            runCatching { AppUpdates.parseManifest(get(asset.url).second) }.getOrNull()
        }
        if (manifest?.minSdk != null && manifest.minSdk > Build.VERSION.SDK_INT) return@withContext null
        if (!AppUpdates.isNewer(remoteName, manifest?.versionCode, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) {
            return@withContext null
        }
        val apk = AppUpdates.pickApk(release.assets, Build.SUPPORTED_ABIS.toList())
            ?: throw UpdateException("Release $remoteName has no APK for this phone (${Build.SUPPORTED_ABIS.firstOrNull()}).")
        val sumsAsset = release.assets.firstOrNull { it.name == AppUpdates.CHECKSUMS_ASSET }
            ?: throw UpdateException("Release $remoteName has no ${AppUpdates.CHECKSUMS_ASSET}; refusing an unverifiable update.")
        val (sumsCode, sumsBody) = get(sumsAsset.url)
        if (sumsCode != 200) throw UpdateException("Couldn't fetch checksums (HTTP $sumsCode).")
        val sha = AppUpdates.parseChecksums(sumsBody)[apk.name]
            ?: throw UpdateException("${apk.name} isn't listed in ${AppUpdates.CHECKSUMS_ASSET}.")
        AvailableUpdate(
            versionName = remoteName,
            versionCode = manifest?.versionCode,
            notes = AppUpdates.notesForDialog(release.body),
            apk = apk,
            sha256 = sha,
            pageUrl = release.htmlUrl,
        )
    }

    /** Download + verify, then move to [UpdateUi.ReadyToInstall] (or the permission step). */
    suspend fun download(update: AvailableUpdate) {
        _ui.value = UpdateUi.Downloading(update, 0f)
        try {
            val file = withContext(Dispatchers.IO) { downloadVerified(update) }
            _ui.value = if (canInstall()) UpdateUi.ReadyToInstall(update, file) else UpdateUi.NeedsInstallPermission(update, file)
        } catch (e: UpdateException) {
            _ui.value = UpdateUi.Failed(e.message ?: "Update failed")
        } catch (e: IOException) {
            _ui.value = UpdateUi.Failed("Download failed: ${e.message ?: "network error"}")
        }
    }

    private suspend fun downloadVerified(update: AvailableUpdate): File {
        updatesDir.deleteRecursively()
        updatesDir.mkdirs()
        val target = File(updatesDir, update.apk.name)
        val part = File(updatesDir, update.apk.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(update.apk.url)
        try {
            if (conn.responseCode != 200) throw UpdateException("Download failed (HTTP ${conn.responseCode}).")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.apk.size
            var done = 0L
            var lastEmit = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastEmit > 200) {
                            lastEmit = now
                            _ui.value = UpdateUi.Downloading(update, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(update.sha256, ignoreCase = true)) {
            part.delete()
            throw UpdateException("Downloaded file failed its checksum check, so it wasn't installed. Try again.")
        }
        if (!part.renameTo(target)) throw UpdateException("Couldn't save the update.")
        if (!sameSigner(target)) {
            throw UpdateException(
                "This update is signed with a different key than the installed app, so Android would refuse it. " +
                    "Uninstall Grabbit once and install ${update.versionName} by hand; updates work from then on."
            )
        }
        return target
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Settings page where the user allows Grabbit to install apps. */
    fun installPermissionIntent(): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** After the user returns from the permission screen. */
    fun recheckPermission() {
        val s = _ui.value
        if (s is UpdateUi.NeedsInstallPermission && canInstall()) _ui.value = UpdateUi.ReadyToInstall(s.update, s.apk)
    }

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** True when [apk] is signed by the same certificate(s) as the running app (or we can't tell). */
    private fun sameSigner(apk: File): Boolean {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.getPackageArchiveInfo(apk.absolutePath, flags)
        }?.signingInfo ?: return true
        val installed = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, flags)
        }.signingInfo ?: return true
        val a = archive.apkContentsSigners.map { it.toByteArray().contentHashCode() }.toSet()
        val b = installed.apkContentsSigners.map { it.toByteArray().contentHashCode() }.toSet()
        if (a.isEmpty() || b.isEmpty()) return true
        // Key rotation: the new APK's lineage may include our current cert.
        if (archive.hasPastSigningCertificates()) {
            val history = archive.signingCertificateHistory.map { it.toByteArray().contentHashCode() }.toSet()
            if (b.all { it in history }) return true
        }
        return a == b
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Grabbit/${BuildConfig.VERSION_NAME} (Android)")
        }

    private fun get(url: String, accept: String? = null): Pair<Int, String> {
        val conn = open(url)
        accept?.let { conn.setRequestProperty("Accept", it) }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }
}

class UpdateException(message: String) : Exception(message)
