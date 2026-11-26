package com.marrdev.gymcoachai

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Guarda el último cierre inesperado en un archivo para mostrarlo al abrir la app de nuevo.
 * Así puedes copiar el error sin necesitar adb ni logcat.
 */
object CrashLogger {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(app).writeText("${Date()}\nHilo: ${thread.name}\n${Log.getStackTraceString(error)}") }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
