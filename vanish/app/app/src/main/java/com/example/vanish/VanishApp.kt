package com.example.vanish

import android.app.Application
import kotlinx.coroutines.*

class VanishApp : Application() {
    lateinit var repo: ChatRepository
    override fun onCreate() {
        super.onCreate()
        ClockSync.init(this)
        repo = ChatRepository(this)
        // Cold start (e.g. after the app was killed mid-countdown): purge expired rows right away.
        CoroutineScope(Dispatchers.IO).launch { repo.sweepOnce() }
        if (repo.hasName()) repo.start()
    }
}
