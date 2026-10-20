package com.bentlybro.grabbit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bentlybro.grabbit.AppGraph
import com.bentlybro.grabbit.core.DownloadFormat
import com.bentlybro.grabbit.data.ErrorText
import com.bentlybro.grabbit.data.Settings
import com.bentlybro.grabbit.engine.Engine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EngineInfo(
    val version: String? = null,
    val updating: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel : ViewModel() {
    private val repo = AppGraph.settings
    private val engine = AppGraph.engine

    val settings: StateFlow<Settings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _engineInfo = MutableStateFlow(EngineInfo())
    val engineInfo: StateFlow<EngineInfo> = _engineInfo.asStateFlow()

    init {
        refreshVersion()
    }

    fun setFormat(f: DownloadFormat) = viewModelScope.launch { repo.setDefaultFormat(f) }
    fun setInstant(v: Boolean) = viewModelScope.launch { repo.setInstantOnShare(v) }
    fun setClipboard(v: Boolean) = viewModelScope.launch { repo.setOfferClipboard(v) }

    private fun refreshVersion() = viewModelScope.launch {
        val v = runCatching { engine.version() }.getOrNull()
        _engineInfo.update { it.copy(version = v ?: "unknown") }
    }

    fun updateYtDlp() {
        if (_engineInfo.value.updating) return
        _engineInfo.update { it.copy(updating = true, message = null) }
        viewModelScope.launch {
            val result = runCatching { engine.updateYtDlp() }
            val version = runCatching { engine.version() }.getOrNull()
            _engineInfo.update {
                it.copy(
                    updating = false,
                    version = version ?: it.version,
                    message = result.fold(
                        { r -> if (r == Engine.UpdateResult.UPDATED) "Updated to $version" else "Already up to date" },
                        { e -> "Update failed: ${ErrorText.from(e.message)}" },
                    ),
                )
            }
        }
    }
}
