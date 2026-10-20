package com.bentlybro.grabbit.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bentlybro.grabbit.AppGraph
import com.bentlybro.grabbit.core.DownloadFormat
import com.bentlybro.grabbit.data.ErrorText
import com.bentlybro.grabbit.ui.components.PreviewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareState(
    val url: String? = null,
    val preview: PreviewState = PreviewState.Idle,
    val format: DownloadFormat = DownloadFormat.DEFAULT,
    val formatLoaded: Boolean = false,
)

class ShareViewModel : ViewModel() {
    private val _state = MutableStateFlow(ShareState())
    val state: StateFlow<ShareState> = _state.asStateFlow()
    private var job: Job? = null
    private var userPicked = false

    /** Idempotent: survives rotation without refetching. */
    fun start(url: String) {
        if (_state.value.url == url) return
        _state.update { it.copy(url = url) }
        viewModelScope.launch {
            val f = AppGraph.settings.current().defaultFormat
            _state.update { if (userPicked) it.copy(formatLoaded = true) else it.copy(format = f, formatLoaded = true) }
        }
        fetch()
    }

    fun select(format: DownloadFormat) {
        userPicked = true
        _state.update { it.copy(format = format) }
    }

    fun fetch() {
        val url = _state.value.url ?: return
        job?.cancel()
        _state.update { it.copy(preview = PreviewState.Loading(url)) }
        job = viewModelScope.launch {
            val r = runCatching { AppGraph.engine.fetchInfo(url) }
            _state.update {
                it.copy(
                    preview = r.fold(
                        { info -> PreviewState.Loaded(info.copy(url = url)) },
                        { e -> PreviewState.Error(url, ErrorText.from(e.message)) },
                    )
                )
            }
        }
    }

    /** Enqueue with whatever we know; the worker fills in missing info itself. */
    fun download(): String? {
        val s = _state.value
        val url = s.url ?: return null
        val info = (s.preview as? PreviewState.Loaded)?.info
        job?.cancel()
        return AppGraph.scheduler.enqueue(url, s.format, info)
    }
}
