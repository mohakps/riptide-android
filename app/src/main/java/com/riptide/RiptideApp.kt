package com.riptide

import android.app.Application
import com.riptide.core.SessionManager

class RiptideApp : Application() {
    lateinit var session: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        session = SessionManager(this)
    }
}
