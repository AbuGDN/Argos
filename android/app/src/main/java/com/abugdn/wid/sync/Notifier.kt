package com.abugdn.wid.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abugdn.wid.R
import com.abugdn.wid.data.AppUpdate
import com.abugdn.wid.data.Cluster
import com.abugdn.wid.data.Feed
import com.abugdn.wid.data.HistoryDay
import com.abugdn.wid.data.RadarData
import com.abugdn.wid.data.weekTop
import com.abugdn.wid.data.matchWatchWord
import com.abugdn.wid.repository
import com.abugdn.wid.data.TAG_LABELS
import com.abugdn.wid.ui.EXTRA_CLUSTER_ID
import com.abugdn.wid.ui.EXTRA_SHORTCUT
import com.abugdn.wid.ui.EXTRA_REGION
import com.abugdn.wid.ui.MainActivity
import java.time.Instant

object Notifier {
    private const val CHANNEL_URGENT = "urgent"
    private const val CHANNEL_TOP = "top"
    private const val CHANNEL_DIGEST = "digest"
    private const val DIGEST_ID = 8_000
    private const val CHANNEL_UPDATE = "update"
    private const val CHANNEL_WATCH = "watch"
    private const val CHANNEL_FOLLOW = "follow"
    private const val CHANNEL_SPIKE = "spike"
    private const val SPIKE_INTERVAL_MS = 12 * 60 * 60 * 1000L
    private const val WEEKLY_ID = 8_002
    private const val SUMMARY_ID = 8_003
    /** Todas as notícias ficam num grupo só na barra de notificações. */
    private const val GROUP = "com.abugdn.wid.NEWS"
    private const val UPDATE_ID = 8_001
    private const val TOP_MIN_INTERVAL_MS = 4 * 60 * 60 * 1000L

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_URGENT, context.getString(R.string.channel_urgent), NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TOP, context.getString(R.string.channel_top), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DIGEST, context.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCH, context.getString(R.string.channel_watch), NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SPIKE, context.getString(R.string.channel_spike), NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FOLLOW, context.getString(R.string.channel_follow), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATE, context.getString(R.string.channel_update), NotificationManager.IMPORTANCE_LOW)
        )
    }

    /**
     * Notifica histórias urgentes ainda não vistas e a troca da principal do dia
     * (no máximo a cada 4 h), respeitando regiões e horário silencioso dos ajustes.
     * Na primeira sincronização só marca tudo como visto.
     */
    fun handle(context: Context, feed: Feed) {
        val repo = context.repository
        val settings = repo.settings.value
        val prefs = repo.storage.prefs
        val notified = prefs.getStringSet("notified", emptySet())!!.toMutableSet()
        val firstRun = !prefs.getBoolean("initialized", false)
        // No horário silencioso nada toca; o que surgir fica para o resumo diário.
        val silent = firstRun || settings.isQuiet()
        val urgent = feed.clusters.filter { it.urgent && it.id !in notified }

        if (!silent && settings.notifyUrgent) {
            urgent.filter { settings.matchesRegion(it.tags) }
                .forEach { notify(context, CHANNEL_URGENT, "Urgente", it) }
        }
        notified += urgent.map { it.id }

        // Palavras vigiadas: avisa uma vez por história, independente da região.
        val translate = { t: String -> repo.translator.cached(t) }
        val watched = feed.clusters
            .filter { "w:${it.id}" !in notified }
            .mapNotNull { c -> c.matchWatchWord(settings.watchWords, translate)?.let { c to it } }
        if (!silent) {
            watched.filter { (c, _) -> c.id !in urgent.map { it.id } }.take(3)
                .forEach { (c, word) -> notify(context, CHANNEL_WATCH, "“$word”", c) }
        }
        notified += watched.map { "w:${it.first.id}" }

        val ids = feed.clusters.map { it.id }.toSet()
        notified.retainAll { it.removePrefix("w:") in ids }

        val top = feed.topOfDay
        val editor = prefs.edit().putStringSet("notified", notified).putBoolean("initialized", true)
        if (top != null && top.id != prefs.getString("top_id", null)) {
            val now = System.currentTimeMillis()
            val due = now - prefs.getLong("top_at", 0) >= TOP_MIN_INTERVAL_MS
            if (!silent && due && settings.notifyTop && settings.matchesRegion(top.tags) && top.id !in urgent.map { it.id }) {
                notify(context, CHANNEL_TOP, "Principal do dia", top)
            }
            editor.putString("top_id", top.id).putLong("top_at", now)
        }
        editor.apply()
    }

    /**
     * Anomalia: região com ritmo de notícias muito acima do normal. No máximo um aviso
     * por região a cada 12 h, respeitando regiões escolhidas e o não perturbe.
     */
    @SuppressLint("MissingPermission") // checado em canNotify
    fun spikes(context: Context, feed: Feed) {
        if (!canNotify(context)) return
        val repo = context.repository
        val settings = repo.settings.value
        if (settings.isQuiet() || !repo.storage.prefs.getBoolean("initialized", false)) return
        val prefs = repo.storage.prefs
        val now = System.currentTimeMillis()
        feed.regions.filter { (tag, r) -> r.spike && settings.matchesRegion(listOf(tag)) }.forEach { (tag, r) ->
            val key = "spike_at_$tag"
            if (now - prefs.getLong(key, 0) < SPIKE_INTERVAL_MS) return@forEach
            prefs.edit().putLong(key, now).apply()
            val label = TAG_LABELS[tag] ?: tag
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_REGION, tag)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context, ("spike" + tag).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = "Ritmo de notícias ${"%.1f".format(r.spikeRatio)}× o normal nas últimas 6 h · tensão ${r.tension} (${r.level})"
            val notification = NotificationCompat.Builder(context, CHANNEL_SPIKE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⚠ Alta incomum: $label")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setGroup(GROUP)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(("spike" + tag).hashCode(), notification)
        }
    }

    private val CLOCK_LEVELS = listOf("baixa", "moderada", "alta", "crítica")
    private const val CLOCK_ID = 8_004
    private const val CLOCK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** Relógio do Argos subiu para alta ou crítica (no máximo um aviso a cada 6 h). */
    @SuppressLint("MissingPermission") // checado em canNotify
    fun clock(context: Context, feed: Feed) {
        val clock = feed.global ?: return
        val prefs = context.repository.storage.prefs
        val rank = CLOCK_LEVELS.indexOf(clock.level).coerceAtLeast(0)
        val previous = prefs.getInt("clock_rank", -1)
        prefs.edit().putInt("clock_rank", rank).apply()
        if (previous < 0 || rank <= previous || rank < 2) return
        if (!canNotify(context) || context.repository.settings.value.isQuiet()) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("clock_notified_at", 0) < CLOCK_INTERVAL_MS) return
        prefs.edit().putLong("clock_notified_at", now).apply()
        val leader = TAG_LABELS[clock.leader] ?: clock.leader
        val notification = NotificationCompat.Builder(context, CHANNEL_SPIKE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("👁 Relógio do Argos subiu para ${clock.level}")
            .setContentText("Tensão global ${clock.index} de 100 · puxada por $leader")
            .setContentIntent(shortcutIntent(context, "clock"))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(CLOCK_ID, notification)
    }

    private const val RADAR_ID = 8_005

    /**
     * Radar: apagão de internet ou espaço aéreo fechado. Um aviso por episódio (a chave inclui
     * desde quando), respeitando as regiões escolhidas e o não perturbe.
     */
    @SuppressLint("MissingPermission") // checado em canNotify
    fun radar(context: Context, radar: RadarData?) {
        if (radar == null || !canNotify(context)) return
        val repo = context.repository
        val settings = repo.settings.value
        if (!settings.notifyRadar || settings.isQuiet()) return
        val prefs = repo.storage.prefs
        val seen = prefs.getStringSet("radar_notified", emptySet())!!
        val alerts = buildList {
            radar.internet?.countries.orEmpty()
                .filter { it.status == "apagao" && settings.matchesRegion(listOf(it.tag)) }
                .forEach { add("internet:${it.code}:${it.since}" to "🌐 Apagão de internet: ${it.name} (${(it.ratio * 100).toInt()}% do normal)") }
            radar.airspace?.zones.orEmpty()
                .filter { it.status == "fechado" && settings.matchesRegion(listOf(it.tag)) }
                .forEach { add("airspace:${it.id}:${it.since}" to "✈ Espaço aéreo fechado: ${it.name} (${it.flights} aviões no ar)") }
            radar.quakes?.items.orEmpty()
                .filter { it.alert && settings.matchesRegion(listOfNotNull(it.tag)) }
                .forEach { q ->
                    val kind = com.abugdn.wid.data.QUAKE_TYPES[q.type] ?: q.type
                    add("quake:${q.id}" to "🌋 Sismo suspeito: $kind de magnitude ${q.mag ?: "?"} em ${q.zone}" + (q.site?.let { " (a ${it.km} km de ${it.name})" } ?: ""))
                }
            radar.travel?.changes.orEmpty()
                .filter { it.to == 4 && it.from < 4 && settings.matchesRegion(listOfNotNull(it.tag)) }
                .forEach { add("travel:${it.country}:${it.date}" to "✈ EUA: “não viaje” para ${it.name} (era nível ${it.from})") }
            repo.deadlines.value
                .filter { d ->
                    val due = d.dueInstant() ?: return@filter false
                    val left = java.time.Duration.between(java.time.Instant.now(), due)
                    !left.isNegative && left.toHours() < 3 && settings.matchesRegion(d.tags)
                }
                .forEach { add("deadline:${it.id}" to "⏳ Prazo vence ${com.abugdn.wid.data.countdown(it.dueInstant()!!).removePrefix("faltam ").let { t -> "em $t" }}: ${repo.translator.cached(it.title)}") }
        }
        // A primeira leitura só registra o que já estava acontecendo, sem avisar.
        val first = !prefs.getBoolean("radar_initialized", false)
        val fresh = alerts.filter { it.first !in seen }
        prefs.edit()
            .putStringSet("radar_notified", (seen + alerts.map { it.first }).toList().takeLast(200).toSet())
            .putBoolean("radar_initialized", true)
            .apply()
        if (first || fresh.isEmpty()) return
        val text = fresh.joinToString("\n") { it.second }
        val notification = NotificationCompat.Builder(context, CHANNEL_SPIKE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (fresh.size == 1) "📡 Radar: sinal de alerta" else "📡 Radar: ${fresh.size} sinais de alerta")
            .setContentText(fresh.first().second)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(shortcutIntent(context, "radar"))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(RADAR_ID, notification)
    }

    private const val SIRENS_ID = 8_007

    /** Resumo das sirenes novas desde a última sincronização (Tzeva Adom, pelo radar.json). */
    @SuppressLint("MissingPermission") // checado em canNotify
    fun sirens(context: Context, radar: RadarData?) {
        val section = radar?.sirens ?: return
        if (!canNotify(context)) return
        val repo = context.repository
        val settings = repo.settings.value
        if (!settings.notifySirens || settings.isQuiet()) return
        val prefs = repo.storage.prefs
        val lastSeen = prefs.getString("sirens_seen", null)
        val newest = section.events.firstOrNull()?.time ?: return
        prefs.edit().putString("sirens_seen", newest).apply()
        if (lastSeen == null) return // primeira leitura: só registra
        val fresh = section.events.filter { it.time > lastSeen }
        if (fresh.isEmpty()) return
        val places = fresh.flatMap { e -> e.cities.map { it.name } }.distinct()
        val threats = fresh.map { it.threat }.distinct().joinToString(", ")
        val text = places.take(12).joinToString(", ") + if (places.size > 12) " e mais ${places.size - 12}" else ""
        val notification = NotificationCompat.Builder(context, CHANNEL_SPIKE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🚨 Sirenes em Israel: ${places.size} ${if (places.size == 1) "local" else "locais"} ($threats)")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(shortcutIntent(context, "sirens"))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(SIRENS_ID, notification)
    }

    private const val PREDICTION_ID = 8_006

    /** Previsões que chegaram na data: hora de conferir se acertou. */
    @SuppressLint("MissingPermission") // checado em canNotify
    fun predictions(context: Context, due: List<com.abugdn.wid.data.Prediction>) {
        if (due.isEmpty() || !canNotify(context)) return
        val text = due.joinToString("\n") { "• ${it.text}" }
        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (due.size == 1) "🎯 Hora de conferir sua previsão" else "🎯 ${due.size} previsões para conferir")
            .setContentText(due.first().text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(shortcutIntent(context, "predictions"))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(PREDICTION_ID, notification)
    }

    /** História seguida ganhou veículos. Toca mesmo fora das regiões escolhidas (o usuário pediu). */
    fun followed(context: Context, updates: List<Pair<Cluster, Int>>) {
        if (context.repository.settings.value.isQuiet()) return
        updates.forEach { (c, added) ->
            notify(context, CHANNEL_FOLLOW, "Seguindo · +$added ${if (added == 1) "veículo" else "veículos"}", c)
        }
    }

    /** Resumo da semana: as 5 principais dos últimos 7 dias; tocar abre o boletim em imagem. */
    @SuppressLint("MissingPermission") // checado em canNotify
    fun weekly(context: Context, days: List<HistoryDay>) {
        if (!canNotify(context) || days.isEmpty()) return
        val repo = context.repository
        val best = weekTop(days)
        val style = NotificationCompat.InboxStyle()
        best.forEach { style.addLine("• " + repo.translator.display(it.top.title, it.top.lang)) }
        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🗞 Boletim semanal do Argos pronto")
            .setContentText(repo.translator.display(best.first().top.title, best.first().top.lang))
            .setStyle(style.setSummaryText("Toque para ver e compartilhar a imagem da semana"))
            .setContentIntent(shortcutIntent(context, "bulletin"))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(WEEKLY_ID, notification)
    }

    /** Resumo diário: as 3 histórias de maior peso das últimas 24 h. */
    @SuppressLint("MissingPermission") // checado em canNotify
    fun digest(context: Context, feed: Feed) {
        if (!canNotify(context)) return
        val repo = context.repository
        val settings = repo.settings.value
        val cutoff = Instant.now().minusSeconds(24 * 3600)
        val best = feed.clusters
            .filter { runCatching { Instant.parse(it.updated).isAfter(cutoff) }.getOrDefault(true) }
            .filter { settings.matchesRegion(it.tags) }
            .sortedByDescending { it.dayScore }
            .take(3)
        if (best.isEmpty()) return

        val titles = best.map { repo.translator.display(it.title, it.lang) }
        val style = NotificationCompat.InboxStyle()
        titles.forEach { style.addLine("• $it") }
        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Resumo do dia")
            .setContentText(titles.first())
            .setStyle(style)
            .setContentIntent(openIntent(context, best.first().id))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(DIGEST_ID, notification)
    }

    /** Versão nova do app no GitHub; tocar abre o app, que mostra o botão de atualizar. */
    @SuppressLint("MissingPermission") // checado em canNotify
    fun update(context: Context, update: AppUpdate) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, UPDATE_ID, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Argos ${update.versionName} disponível")
            .setContentText("Toque para abrir o app e atualizar.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(UPDATE_ID, notification)
    }

    private fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openIntent(context: Context, clusterId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_CLUSTER_ID, clusterId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, clusterId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun shortcutIntent(context: Context, shortcut: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SHORTCUT, shortcut)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, ("shortcut:$shortcut").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission") // checado em canNotify
    private fun notify(context: Context, channel: String, label: String, cluster: Cluster) {
        if (!canNotify(context)) return
        val title = context.repository.translator.display(cluster.title, cluster.lang)
        val pending = openIntent(context, cluster.id)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setGroup(GROUP)
            .addAction(R.drawable.ic_notification, "Salvar", NotificationActionReceiver.intent(context, ACTION_SAVE, cluster.id))
            .addAction(R.drawable.ic_notification, "Seguir", NotificationActionReceiver.intent(context, ACTION_FOLLOW, cluster.id))
            .setContentTitle("$label · ${cluster.sourcesCount} veículos")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val manager = NotificationManagerCompat.from(context)
        manager.notify(cluster.id.hashCode(), notification)
        // Resumo do grupo: silencioso, só junta as notificações acima.
        val summary = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Argos")
            .setContentText("Novas notícias")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("notícias de guerra"))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }
}
