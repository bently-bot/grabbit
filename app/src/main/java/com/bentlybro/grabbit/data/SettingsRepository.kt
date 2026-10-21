package com.bentlybro.grabbit.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bentlybro.grabbit.core.DownloadFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Which yt-dlp build to track. Nightly is the default: YouTube breaks things often and
 * yt-dlp ships the fixes to nightly first (often days or weeks before a stable release).
 */
enum class EngineChannel(val label: String) {
    NIGHTLY("Nightly"),
    STABLE("Stable");

    companion object {
        val DEFAULT = NIGHTLY
        fun fromName(name: String?): EngineChannel = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

data class Settings(
    val defaultFormat: DownloadFormat = DownloadFormat.DEFAULT,
    val instantOnShare: Boolean = false,
    val offerClipboard: Boolean = true,
    val engineChannel: EngineChannel = EngineChannel.DEFAULT,
    val autoUpdateEngine: Boolean = true,
    /** Wall-clock ms of the last successful engine update check (0 = never). */
    val lastEngineCheck: Long = 0L,
    /** Channel the installed engine came from, so switching channel forces an update. */
    val installedEngineChannel: EngineChannel? = null,
    val lastAppUpdateCheck: Long = 0L,
    val skippedAppVersion: String? = null,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val format = stringPreferencesKey("default_format")
        val instant = booleanPreferencesKey("instant_on_share")
        val clipboard = booleanPreferencesKey("offer_clipboard")
        val channel = stringPreferencesKey("engine_channel")
        val autoEngine = booleanPreferencesKey("auto_update_engine")
        val lastEngineCheck = longPreferencesKey("last_engine_check")
        val installedChannel = stringPreferencesKey("installed_engine_channel")
        val lastAppCheck = longPreferencesKey("last_app_update_check")
        val skipped = stringPreferencesKey("skipped_app_version")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            defaultFormat = DownloadFormat.fromName(p[Keys.format]),
            instantOnShare = p[Keys.instant] ?: false,
            offerClipboard = p[Keys.clipboard] ?: true,
            engineChannel = EngineChannel.fromName(p[Keys.channel]),
            autoUpdateEngine = p[Keys.autoEngine] ?: true,
            lastEngineCheck = p[Keys.lastEngineCheck] ?: 0L,
            installedEngineChannel = p[Keys.installedChannel]?.let(EngineChannel::fromName),
            lastAppUpdateCheck = p[Keys.lastAppCheck] ?: 0L,
            skippedAppVersion = p[Keys.skipped],
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setDefaultFormat(format: DownloadFormat) {
        context.dataStore.edit { it[Keys.format] = format.name }
    }

    suspend fun setInstantOnShare(enabled: Boolean) {
        context.dataStore.edit { it[Keys.instant] = enabled }
    }

    suspend fun setOfferClipboard(enabled: Boolean) {
        context.dataStore.edit { it[Keys.clipboard] = enabled }
    }

    suspend fun setEngineChannel(channel: EngineChannel) {
        context.dataStore.edit { it[Keys.channel] = channel.name }
    }

    suspend fun setAutoUpdateEngine(enabled: Boolean) {
        context.dataStore.edit { it[Keys.autoEngine] = enabled }
    }

    suspend fun engineChecked(at: Long, channel: EngineChannel) {
        context.dataStore.edit {
            it[Keys.lastEngineCheck] = at
            it[Keys.installedChannel] = channel.name
        }
    }

    suspend fun appUpdateChecked(at: Long) {
        context.dataStore.edit { it[Keys.lastAppCheck] = at }
    }

    suspend fun skipAppVersion(version: String?) {
        context.dataStore.edit { if (version == null) it.remove(Keys.skipped) else it[Keys.skipped] = version }
    }
}
