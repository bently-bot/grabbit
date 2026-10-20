package com.bentlybro.grabbit.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Preview data for a link, from `yt-dlp --dump-json`. */
data class MediaInfo(
    val url: String,
    val title: String?,
    val uploader: String?,
    val durationSec: Long?,
    val thumbnail: String?,
    val extractor: String?,
)

sealed interface EngineState {
    data object Initializing : EngineState
    data object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

/**
 * Thin, coroutine-friendly wrapper around youtubedl-android. The first [ensureReady] unpacks
 * the bundled Python and yt-dlp into app storage (a few seconds on first launch), so it is
 * always called off the main thread and guarded by a mutex: the UI, the share sheet and a
 * worker may all race to it after a cold start.
 */
class Engine(private val context: Context) {

    private val initLock = Mutex()
    private val _state = MutableStateFlow<EngineState>(EngineState.Initializing)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    suspend fun ensureReady() = initLock.withLock {
        if (_state.value == EngineState.Ready) return@withLock
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.init(context)
                FFmpeg.init(context)
                _state.value = EngineState.Ready
            } catch (t: Throwable) {
                _state.value = EngineState.Failed(t.message ?: t.javaClass.simpleName)
                throw t
            }
        }
    }

    suspend fun fetchInfo(url: String): MediaInfo {
        ensureReady()
        return withContext(Dispatchers.IO) {
            val request = YoutubeDLRequest(url)
                .addOption("--no-playlist")
                .addOption("--socket-timeout", 20)
            val info = YoutubeDL.getInfo(request)
            MediaInfo(
                url = info.webpageUrl ?: url,
                title = info.title ?: info.fulltitle,
                uploader = info.uploader ?: info.uploaderId,
                durationSec = info.duration.toLong().takeIf { it > 0 },
                thumbnail = info.thumbnail ?: info.thumbnails?.lastOrNull()?.url,
                extractor = info.extractorKey ?: info.extractor,
            )
        }
    }

    /** yt-dlp version string, e.g. "2025.11.12". */
    suspend fun version(): String? {
        ensureReady()
        return withContext(Dispatchers.IO) {
            YoutubeDL.version(context) ?: runCatching {
                val request = YoutubeDLRequest(emptyList<String>()).addOption("--version")
                YoutubeDL.execute(request).out.trim().lines().firstOrNull()
            }.getOrNull()
        }
    }

    enum class UpdateResult { UPDATED, ALREADY_UP_TO_DATE }

    suspend fun updateYtDlp(): UpdateResult {
        ensureReady()
        return withContext(Dispatchers.IO) {
            when (YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)) {
                YoutubeDL.UpdateStatus.DONE -> UpdateResult.UPDATED
                else -> UpdateResult.ALREADY_UP_TO_DATE
            }
        }
    }
}
