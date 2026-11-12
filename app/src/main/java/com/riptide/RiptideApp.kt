package com.riptide

import android.app.Application
import com.riptide.core.DiagLog
import com.riptide.core.SessionManager

class RiptideApp : Application() {
    lateinit var session: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        // A fresh "process started" line marks every process start, so
        // a restart is visible in the diagnostics log instead of
        // looking like a continuation. The version is in the line so
        // a shared log always identifies the build that produced it.
        DiagLog.log("app", "process started Riptide Android ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        session = SessionManager(this)
    }
}
