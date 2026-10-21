package com.bentlybro.grabbit

import android.content.Context
import com.bentlybro.grabbit.data.QueueRepository
import com.bentlybro.grabbit.data.SettingsRepository
import com.bentlybro.grabbit.download.DownloadScheduler
import com.bentlybro.grabbit.engine.Engine
import com.bentlybro.grabbit.engine.EngineUpdater
import com.bentlybro.grabbit.engine.MediaSaver
import com.bentlybro.grabbit.update.AppUpdater

/** Tiny manual DI: one instance of each long-lived object per process. */
object AppGraph {
    lateinit var engine: Engine; private set
    lateinit var engineUpdater: EngineUpdater; private set
    lateinit var queue: QueueRepository; private set
    lateinit var settings: SettingsRepository; private set
    lateinit var saver: MediaSaver; private set
    lateinit var scheduler: DownloadScheduler; private set
    lateinit var appUpdater: AppUpdater; private set

    @Volatile private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        engine = Engine(app)
        queue = QueueRepository(app)
        settings = SettingsRepository(app)
        engineUpdater = EngineUpdater(app, engine, settings)
        saver = MediaSaver(app)
        scheduler = DownloadScheduler(app, queue)
        appUpdater = AppUpdater(app, settings)
        initialized = true
    }
}
