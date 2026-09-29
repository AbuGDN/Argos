package com.abugdn.wid.data

import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** app-config.json (no site): configurações que mudam sem precisar de versão nova do app. */
@Serializable
data class AppConfig(val goatcounter: String = "")

/**
 * Contagem anônima de uso pelo GoatCounter: no máximo um aviso por dia por aparelho, só com a
 * versão do app. Nada pessoal é enviado (o GoatCounter não guarda IP). Desligável em Ajustes.
 * Sem código configurado em app-config.json, não manda nada.
 */
object Usage {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    fun ping(context: Context, code: String, enabled: Boolean) {
        if (!enabled || !Regex("[a-z0-9-]{2,50}").matches(code)) return
        val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString("last_ping", null) == today) return
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val version = info.versionName ?: "?"
        val metrics = context.resources.displayMetrics
        // O GoatCounter ignora o que parece robô: o user-agent tem cara de navegador Android (só ASCII).
        val agent = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE.filter { it.code in 0x20..0x7e }}; Mobile) Argos/$version"
        val first = !prefs.getBoolean("installed_sent", false)
        val hits = buildList {
            add("/app" to "Argos $version")
            if (first) add("/app/instalacao" to "Primeira abertura")
        }
        var ok = true
        for ((path, title) in hits) {
            val url = "https://$code.goatcounter.com/count".toHttpUrl().newBuilder()
                .addQueryParameter("p", path)
                .addQueryParameter("t", title)
                .addQueryParameter("s", "${metrics.widthPixels},${metrics.heightPixels},${metrics.density}")
                .addQueryParameter("rnd", System.nanoTime().toString())
                .apply { if (path != "/app") addQueryParameter("e", "true") }
                .build()
            ok = ok && runCatching {
                http.newCall(Request.Builder().url(url).header("User-Agent", agent).build()).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
        if (ok) prefs.edit().putString("last_ping", today).putBoolean("installed_sent", true).apply()
    }
}
