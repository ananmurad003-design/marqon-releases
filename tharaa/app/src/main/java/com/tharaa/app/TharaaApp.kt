package com.tharaa.app

import android.app.Application
import com.tharaa.app.data.AppDatabase
import com.tharaa.app.data.Repository
import com.tharaa.app.work.Notifier
import com.tharaa.app.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TharaaApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var repo: Repository
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = AppDatabase.build(this)
        repo = Repository(db)
        Notifier.createChannel(this)
        appScope.launch {
            val s = repo.settingsNow()
            Scheduler.scheduleReminder(this@TharaaApp, s.reminderHour, s.remindersOn)
            Scheduler.scheduleBackup(this@TharaaApp)
        }
    }

    companion object {
        lateinit var instance: TharaaApp
            private set
    }
}
