package com.newkick.app

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Date

/** Guarda a causa do último fechamento inesperado em um arquivo local (nada é enviado). */
object CrashLogger {
    private fun file(context: Context) = File(context.filesDir, "crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
                file(app).writeText("BetterKick $version • ${Date()}\nthread: ${thread.name}\n${Log.getStackTraceString(error)}")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }
}