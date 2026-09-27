package com.abugdn.wid

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Guarda o erro de um fechamento inesperado em crash.txt, para a próxima abertura mostrar
 * (e a pessoa poder compartilhar). Depois repassa ao tratador padrão do Android.
 */
object CrashLog {
    private const val FILE = "crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull()
                File(app.filesDir, FILE).writeText(
                    "Argos $version · Android ${Build.VERSION.RELEASE} (${Build.MODEL})\n" +
                        "${Instant.now()} · thread ${thread.name}\n\n" + trace.take(12_000)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Erro do último fechamento, se houver (e ainda não visto). */
    fun pending(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
