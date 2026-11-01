package com.example.helloworld

import android.app.Application

class KillTeamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TeamDataLoader.ensureLoaded(this)
    }
}
