package com.bentlybro.grabbit.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bentlybro.grabbit.AppGraph
import com.bentlybro.grabbit.core.DownloadFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * adb hook for the real-download CI job (debug builds only):
 *
 *   am broadcast -n com.bentlybro.grabbit/.debug.DebugCommandReceiver \
 *       -a com.bentlybro.grabbit.debug.ENQUEUE --es url <url> --es tag youtube [--es format BEST]
 *   ... -a com.bentlybro.grabbit.debug.UPDATE_ENGINE   (writes files/debug/engine.txt)
 *   ... -a com.bentlybro.grabbit.debug.ENGINE_INFO     (writes files/debug/engine.txt)
 *
 * ENQUEUE goes through exactly the same scheduler, worker, engine and MediaStore saver as a
 * share or a tap on Download. The test reads the outcome from files/queue.json via run-as,
 * and files/debug/tags.txt maps its tag to the queue item id.
 */
class DebugCommandReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        AppGraph.init(context)
        val dir = File(context.filesDir, "debug").apply { mkdirs() }
        // An engine update can outlast a broadcast's time limit, so don't hold the broadcast
        // for it; CI keeps MainActivity open, which keeps the process alive.
        val pending = if (intent.action == ACTION_UPDATE_ENGINE) null else goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_ENQUEUE -> {
                        val url = intent.getStringExtra("url") ?: return@launch
                        val tag = intent.getStringExtra("tag") ?: url
                        val format = DownloadFormat.fromName(intent.getStringExtra("format"))
                        val id = AppGraph.scheduler.enqueue(url, format)
                        File(dir, "tags.txt").appendText("$tag\t$id\n")
                        Log.i(TAG, "enqueued $tag -> $id")
                    }
                    ACTION_UPDATE_ENGINE -> {
                        val result = runCatching { AppGraph.engineUpdater.updateNow() }
                        writeEngine(dir, result.fold({ it.name }, { "FAILED: ${it.message}" }))
                    }
                    ACTION_ENGINE_INFO -> writeEngine(dir, "INFO")
                }
            } finally {
                pending?.finish()
            }
        }
    }

    private suspend fun writeEngine(dir: File, result: String) {
        val version = runCatching { AppGraph.engine.version() }.getOrNull()
        val s = AppGraph.settings.current()
        File(dir, "engine.txt").writeText(
            "result=$result\nversion=$version\nchannel=${s.engineChannel}\nlastCheck=${s.lastEngineCheck}\n"
        )
        Log.i(TAG, "engine: $result $version")
    }

    companion object {
        private const val TAG = "GrabbitDebug"
        const val ACTION_ENQUEUE = "com.bentlybro.grabbit.debug.ENQUEUE"
        const val ACTION_UPDATE_ENGINE = "com.bentlybro.grabbit.debug.UPDATE_ENGINE"
        const val ACTION_ENGINE_INFO = "com.bentlybro.grabbit.debug.ENGINE_INFO"
    }
}
