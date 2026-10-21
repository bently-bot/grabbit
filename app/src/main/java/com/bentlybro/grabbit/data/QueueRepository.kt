package com.bentlybro.grabbit.data

import android.content.Context
import com.bentlybro.grabbit.core.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The queue lives in memory as a StateFlow (the UI and workers share the process) and is
 * written to a small JSON file on every structural change. Progress ticks are not
 * persisted: they change many times a second and are meaningless after a restart.
 */
class QueueRepository(context: Context) {

    private val file = File(context.filesDir, "queue.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    fun get(id: String): QueueItem? = _items.value.firstOrNull { it.id == id }

    fun add(item: QueueItem) = mutate { QueueReducer.add(it, item) }
    fun started(id: String) = mutate { QueueReducer.started(it, id) }
    fun progress(id: String, percent: Float, eta: Long?, phase: Phase?) =
        mutate(persist = false) { QueueReducer.progress(it, id, percent, eta, phase) }
    fun phase(id: String, phase: Phase) = mutate(persist = false) { QueueReducer.phase(it, id, phase) }
    fun info(id: String, title: String?, uploader: String?, thumb: String?, duration: Long?) =
        mutate { QueueReducer.info(it, id, title, uploader, thumb, duration) }
    fun done(id: String, uri: String, name: String, mime: String) = mutate { QueueReducer.done(it, id, uri, name, mime) }
    fun failed(id: String, error: String, detail: String? = null) = mutate { QueueReducer.failed(it, id, error, detail) }
    fun attempt(id: String, retries: Int, engineVersion: String?) =
        mutate { QueueReducer.attempt(it, id, retries, engineVersion) }
    fun cancelled(id: String) = mutate { QueueReducer.cancelled(it, id) }
    fun retried(id: String, workId: String) = mutate { QueueReducer.retried(it, id, workId) }
    fun remove(id: String) = mutate { QueueReducer.remove(it, id) }
    fun clearFinished() = mutate { QueueReducer.clearFinished(it) }
    fun reconcile(isWorkAlive: (String) -> Boolean) = mutate { QueueReducer.reconcile(it, isWorkAlive) }

    private fun mutate(persist: Boolean = true, f: (List<QueueItem>) -> List<QueueItem>) {
        _items.update(f)
        if (persist) save()
    }

    private fun save() {
        val snapshot = _items.value
        scope.launch {
            writeLock.withLock {
                runCatching {
                    val tmp = File(file.parentFile, "queue.json.tmp")
                    tmp.writeText(json.encodeToString(snapshot))
                    tmp.renameTo(file)
                }
            }
        }
    }

    private fun load(): List<QueueItem> = runCatching {
        if (file.exists()) json.decodeFromString<List<QueueItem>>(file.readText()) else emptyList()
    }.getOrDefault(emptyList())
}
