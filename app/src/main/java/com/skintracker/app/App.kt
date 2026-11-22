package com.skintracker.app

import android.app.Application
import com.skintracker.app.data.Repo
import com.skintracker.app.work.Scheduler

class App : Application() {
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repo(this)
        // Фоновый мониторинг работает всегда: он копирует историю цен и шлёт сигналы.
        Scheduler.schedule(this, repo.state.value.settings.everyHours, replace = false)
    }
}
