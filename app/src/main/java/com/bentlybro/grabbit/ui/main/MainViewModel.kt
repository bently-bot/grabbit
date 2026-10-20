package com.bentlybro.grabbit.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bentlybro.grabbit.AppGraph
import com.bentlybro.grabbit.core.DownloadFormat
import com.bentlybro.grabbit.core.UrlExtractor
import com.bentlybro.grabbit.data.ErrorText
import com.bentlybro.grabbit.data.QueueItem
import com.bentlybro.grabbit.data.Settings
import com.bentlybro.grabbit.engine.EngineState
import com.bentlybro.grabbit.ui.components.PreviewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val input: String = "",
    val preview: PreviewState = PreviewState.Idle,
    val format: DownloadFormat = DownloadFormat.DEFAULT,
    /** A link on the clipboard we haven't offered/used yet. */
    val clipboardOffer: String? = null,
)

class MainViewModel : ViewModel() {

    private val scheduler = AppGraph.scheduler
    private val engine = AppGraph.engine

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    val queue: StateFlow<List<QueueItem>> = AppGraph.queue.items
    val engineState: StateFlow<EngineState> = engine.state
    val settings: StateFlow<Settings> = AppGraph.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private var fetchJob: Job? = null
    private val dismissedClips = mutableSetOf<String>()
    private var formatTouched = false

    init {
        viewModelScope.launch {
            val s = AppGraph.settings.settings.first()
            if (!formatTouched) _home.update { it.copy(format = s.defaultFormat) }
        }
    }

    fun onInputChange(text: String) {
        _home.update { it.copy(input = text) }
        val url = UrlExtractor.extract(text)
        val current = (home.value.preview as? PreviewState.Loaded)?.info?.url
        if (url == null) {
            fetchJob?.cancel()
            _home.update { it.copy(preview = PreviewState.Idle) }
        } else if (url != current && url != loadingUrl()) {
            fetch(url)
        }
    }

    /** Text arriving from the Paste button, a share intent or the clipboard offer. */
    fun useText(text: String) {
        val url = UrlExtractor.extract(text) ?: text.trim()
        dismissedClips += url
        _home.update { it.copy(input = url, clipboardOffer = null) }
        if (UrlExtractor.extract(url) != null) fetch(url)
    }

    fun onClipboard(text: String?) {
        if (!settings.value.offerClipboard) return
        val url = UrlExtractor.extract(text) ?: return
        if (url in dismissedClips || url == UrlExtractor.extract(home.value.input)) return
        if (queue.value.any { it.url == url }) return
        _home.update { it.copy(clipboardOffer = url) }
    }

    fun dismissClipboard() {
        home.value.clipboardOffer?.let { dismissedClips += it }
        _home.update { it.copy(clipboardOffer = null) }
    }

    fun selectFormat(format: DownloadFormat) {
        formatTouched = true
        _home.update { it.copy(format = format) }
    }

    fun retryPreview() {
        UrlExtractor.extract(home.value.input)?.let { fetch(it) }
    }

    fun clearInput() {
        fetchJob?.cancel()
        _home.update { it.copy(input = "", preview = PreviewState.Idle) }
    }

    /** Queue the current link. Works even if the preview failed: yt-dlp may still manage. */
    fun download() {
        val url = UrlExtractor.extract(home.value.input) ?: return
        val info = (home.value.preview as? PreviewState.Loaded)?.info
        scheduler.enqueue(info?.url ?: url, home.value.format, info)
        fetchJob?.cancel()
        _home.update { it.copy(input = "", preview = PreviewState.Idle) }
    }

    fun cancel(id: String) = scheduler.cancel(id)
    fun retry(id: String) = scheduler.retry(id)
    fun remove(id: String) = scheduler.remove(id)
    fun clearFinished() = scheduler.clearFinished()

    private fun loadingUrl() = (home.value.preview as? PreviewState.Loading)?.url

    private fun fetch(url: String) {
        fetchJob?.cancel()
        _home.update { it.copy(preview = PreviewState.Loading(url)) }
        fetchJob = viewModelScope.launch {
            val result = runCatching { engine.fetchInfo(url) }
            _home.update { state ->
                if (state.preview != PreviewState.Loading(url)) state
                else state.copy(
                    preview = result.fold(
                        { PreviewState.Loaded(it.copy(url = url)) },
                        { PreviewState.Error(url, ErrorText.from(it.message)) },
                    )
                )
            }
        }
    }
}
