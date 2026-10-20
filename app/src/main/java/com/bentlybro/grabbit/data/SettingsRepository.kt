package com.bentlybro.grabbit.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bentlybro.grabbit.core.DownloadFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val defaultFormat: DownloadFormat = DownloadFormat.DEFAULT,
    val instantOnShare: Boolean = false,
    val offerClipboard: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val format = stringPreferencesKey("default_format")
        val instant = booleanPreferencesKey("instant_on_share")
        val clipboard = booleanPreferencesKey("offer_clipboard")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            defaultFormat = DownloadFormat.fromName(p[Keys.format]),
            instantOnShare = p[Keys.instant] ?: false,
            offerClipboard = p[Keys.clipboard] ?: true,
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
}
