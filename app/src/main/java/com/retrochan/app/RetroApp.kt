package com.retrochan.app

import android.app.Application
import android.util.Log
import java.io.File
import java.util.Date

/** Mencatat crash Java ke crash.log (berlaku di proses utama dan proses game). */
class RetroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Cores.init(this)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val f = File(filesDir, "crash.log")
                if (f.length() > 200_000) f.delete()
                f.appendText("--- ${Date()} pid=${android.os.Process.myPid()} thread=${t.name} ---\n${Log.getStackTraceString(e)}\n")
            }
            prev?.uncaughtException(t, e)
        }
    }
}
