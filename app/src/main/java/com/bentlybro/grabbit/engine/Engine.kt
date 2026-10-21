package com.bentlybro.grabbit.engine

import android.content.Context
import com.bentlybro.grabbit.data.EngineChannel
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

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
 *
 * Every yt-dlp run goes through [withEngine], and a self-update ([update]) waits until no run is
 * in flight: the library replaces the yt-dlp zip in place, and a Python process importing
 * from it mid-swap would crash.
 */
class Engine(private val context: Context) {

    private val initLock = Mutex()
    private val _state = MutableStateFlow<EngineState>(EngineState.Initializing)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    /** yt-dlp version string, e.g. "2026.10.10.233141"; null until known. */
    val versionFlow: StateFlow<String?> = _version.asStateFlow()

    private val gate = Mutex()
    private var activeRuns = 0
    private var updating = false
    private val updateLock = Mutex()

    /** yt-dlp's cache (player JS, solved challenges): much faster repeat YouTube extractions. */
    private val cacheDir: File get() = File(context.cacheDir, "yt-dlp-cache")

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

    /** Runs [block] as one yt-dlp "user", blocking while an update swaps the binary. */
    suspend fun <T> withEngine(block: suspend () -> T): T {
        ensureReady()
        while (true) {
            val admitted = gate.withLock { if (!updating) { activeRuns++; true } else false }
            if (admitted) break
            delay(250)
        }
        try {
            return block()
        } finally {
            gate.withLock { activeRuns-- }
        }
    }

    /** Options every request gets on top of the library's own (QuickJS runtime, ffmpeg path). */
    fun baseOptions(request: YoutubeDLRequest): YoutubeDLRequest = request
        .addOption("--cache-dir", cacheDir.absolutePath)
        // Self-updates are handled by the app, so silence yt-dlp's "older than 90 days" nag.
        .addOption("--no-update")

    suspend fun execute(
        request: YoutubeDLRequest,
        processId: String? = null,
        callback: ((Float, Long, String) -> Unit)? = null,
    ): YoutubeDLResponse = withEngine {
        baseOptions(request)
        runInterruptible(Dispatchers.IO) { YoutubeDL.execute(request, processId, callback) }
    }

    suspend fun fetchInfo(url: String, extractorArgs: String? = null): MediaInfo = withEngine {
        withContext(Dispatchers.IO) {
            val request = baseOptions(YoutubeDLRequest(url))
                .addOption("--no-playlist")
                .addOption("--socket-timeout", 20)
            extractorArgs?.let { request.addOption("--extractor-args", it) }
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

    /** yt-dlp version string, e.g. "2026.10.10.233141". */
    suspend fun version(): String? {
        ensureReady()
        val v = withContext(Dispatchers.IO) {
            YoutubeDL.version(context) ?: runCatching {
                // Bundled build: the library only records a version after its first update.
                val request = YoutubeDLRequest(emptyList<String>()).addOption("--version")
                withEngine { YoutubeDL.execute(request).out.trim().lines().firstOrNull() }
            }.getOrNull()
        }
        _version.value = v
        return v
    }

    enum class UpdateResult { UPDATED, ALREADY_UP_TO_DATE, BUSY }

    /**
     * Self-update yt-dlp from [channel]'s latest GitHub release. Waits up to [waitMs] for
     * in-flight runs to finish; returns [UpdateResult.BUSY] rather than killing them.
     */
    suspend fun update(channel: EngineChannel, waitMs: Long = 120_000): UpdateResult = updateLock.withLock {
        ensureReady()
        val deadline = System.currentTimeMillis() + waitMs
        while (true) {
            val claimed = gate.withLock { if (activeRuns == 0) { updating = true; true } else false }
            if (claimed) break
            if (System.currentTimeMillis() > deadline) return@withLock UpdateResult.BUSY
            delay(500)
        }
        try {
            withContext(Dispatchers.IO) {
                val libChannel = when (channel) {
                    EngineChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
                    EngineChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                }
                when (YoutubeDL.updateYoutubeDL(context, libChannel)) {
                    YoutubeDL.UpdateStatus.DONE -> UpdateResult.UPDATED
                    else -> UpdateResult.ALREADY_UP_TO_DATE
                }
            }.also {
                // A new yt-dlp may solve YouTube's challenges differently; drop the old cache.
                if (it == UpdateResult.UPDATED) cacheDir.deleteRecursively()
            }
        } finally {
            gate.withLock { updating = false }
        }
    }.also { if (it != UpdateResult.BUSY) version() }
}
