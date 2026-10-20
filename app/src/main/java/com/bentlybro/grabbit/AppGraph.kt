package com.bentlybro.grabbit

import android.content.Context
import com.bentlybro.grabbit.data.QueueRepository
import com.bentlybro.grabbit.data.SettingsRepository
import com.bentlybro.grabbit.download.DownloadScheduler
import com.bentlybro.grabbit.engine.Engine
import com.bentlybro.grabbit.engine.MediaSaver

/** Tiny manual DI: one instance of each long-lived object per process. */
object AppGraph {
    lateinit var engine: Engine; private set
    lateinit var queue: QueueRepository; private set
    lateinit var settings: SettingsRepository; private set
    lateinit var saver: MediaSaver; private set
    lateinit var scheduler: DownloadScheduler; private set

    @Volatile private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        engine = Engine(app)
        queue = QueueRepository(app)
        settings = SettingsRepository(app)
        saver = MediaSaver(app)
        scheduler = DownloadScheduler(app, queue)
        initialized = true
    }
}
