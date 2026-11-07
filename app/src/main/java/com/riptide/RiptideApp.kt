package com.riptide

import android.app.Application
import com.riptide.core.DiagLog
import com.riptide.core.SessionManager

class RiptideApp : Application() {
    lateinit var session: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        // v0.3.4 test build: a fresh "process started" line marks every
        // process start, so a restart is visible in the diagnostics log
        // instead of looking like a continuation.
        DiagLog.init(this)
        DiagLog.log("app", "process started")
        session = SessionManager(this)
    }
}
