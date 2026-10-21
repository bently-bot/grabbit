package com.bentlybro.grabbit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bentlybro.grabbit.AppGraph
import com.bentlybro.grabbit.core.DownloadFormat
import com.bentlybro.grabbit.data.EngineChannel
import com.bentlybro.grabbit.data.ErrorText
import com.bentlybro.grabbit.data.Settings
import com.bentlybro.grabbit.engine.EngineUpdater
import com.bentlybro.grabbit.update.UpdateUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val repo = AppGraph.settings
    private val engine = AppGraph.engine
    private val engineUpdater = AppGraph.engineUpdater
    private val appUpdater = AppGraph.appUpdater

    val settings: StateFlow<Settings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val engineVersion: StateFlow<String?> = engine.versionFlow
    val engineStatus: StateFlow<EngineUpdater.Status> = engineUpdater.status
    val appUpdate: StateFlow<UpdateUi> = appUpdater.ui
    val updatesEnabled: Boolean get() = appUpdater.enabled
    val updateRepo: String get() = appUpdater.repo

    init {
        viewModelScope.launch { runCatching { engine.version() } }
    }

    fun setFormat(f: DownloadFormat) = viewModelScope.launch { repo.setDefaultFormat(f) }
    fun setInstant(v: Boolean) = viewModelScope.launch { repo.setInstantOnShare(v) }
    fun setClipboard(v: Boolean) = viewModelScope.launch { repo.setOfferClipboard(v) }
    fun setAutoEngine(v: Boolean) = viewModelScope.launch { repo.setAutoUpdateEngine(v) }

    /** Switching channel installs that channel's latest straight away. */
    fun setChannel(c: EngineChannel) = viewModelScope.launch {
        if (settings.value.engineChannel == c) return@launch
        repo.setEngineChannel(c)
        updateEngine()
    }

    fun updateEngine() {
        if (engineStatus.value.running) return
        viewModelScope.launch {
            runCatching { engineUpdater.updateNow() }.onFailure { ErrorText.from(it.message) }
        }
    }

    fun checkForAppUpdate() = viewModelScope.launch { appUpdater.checkNow() }
}
