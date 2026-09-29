package com.abugdn.wid.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.abugdn.wid.data.Deadline
import com.abugdn.wid.data.SirensSection
import com.abugdn.wid.data.countdown
import com.abugdn.wid.repository
import com.abugdn.wid.ui.EXTRA_SHORTCUT
import com.abugdn.wid.ui.MainActivity
import java.time.Instant

private val INK = Color(0xFF050505)
private val GOLD = Color(0xFFC9A227)
private val BONE = Color(0xFFE8E2D0)
private val ASH = Color(0xFF8A8578)
private val BLOOD = Color(0xFFB3122E)

/** Widget: o próximo ultimato das manchetes, com contagem regressiva (atualiza a cada sincronização). */
class DeadlineWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.repository
        val now = Instant.now()
        val next = repo.deadlines.value.filter { !it.expired(now) }.minByOrNull { it.due }
        val title = next?.let { repo.translator.display(it.title, it.lang) }
        provideContent { DeadlineBody(next, title, now) }
    }
}

class DeadlineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeadlineWidget()
}

@Composable
private fun DeadlineBody(d: Deadline?, title: String?, now: Instant) {
    val action = actionStartActivity<MainActivity>(actionParametersOf(ActionParameters.Key<String>(EXTRA_SHORTCUT) to "deadlines"))
    Column(GlanceModifier.fillMaxSize().background(INK).cornerRadius(16.dp).padding(12.dp).clickable(action)) {
        Text("⏳ PRÓXIMO PRAZO", style = TextStyle(color = ColorProvider(GOLD), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        val due = d?.dueInstant()
        if (d == null || due == null) {
            Text("Nenhum ultimato correndo agora.", style = TextStyle(color = ColorProvider(ASH), fontSize = 12.sp))
        } else {
            Text(countdown(due, now), style = TextStyle(color = ColorProvider(BLOOD), fontSize = 20.sp, fontWeight = FontWeight.Bold))
            Text(title ?: d.title, style = TextStyle(color = ColorProvider(BONE), fontSize = 12.sp), maxLines = 3)
        }
    }
}

/** Widget: último alerta de sirene em Israel e quantos locais tiveram sirene em 24 h. */
class SirensWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.repository
        val radar = repo.radar.value ?: repo.storage.loadRadar()
        provideContent { SirensBody(radar?.sirens, Instant.now()) }
    }
}

class SirensWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SirensWidget()
}

@Composable
private fun SirensBody(s: SirensSection?, now: Instant) {
    val action = actionStartActivity<MainActivity>(actionParametersOf(ActionParameters.Key<String>(EXTRA_SHORTCUT) to "sirens"))
    Column(GlanceModifier.fillMaxSize().background(INK).cornerRadius(16.dp).padding(12.dp).clickable(action)) {
        Text("🚨 SIRENES EM ISRAEL", style = TextStyle(color = ColorProvider(GOLD), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        val last = s?.events?.firstOrNull()
        if (s == null) {
            Text("Abra o Argos", style = TextStyle(color = ColorProvider(ASH), fontSize = 12.sp))
        } else if (last == null) {
            Text("Nenhuma sirene em 24 h", style = TextStyle(color = ColorProvider(BONE), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        } else {
            val ago = runCatching { java.time.Duration.between(Instant.parse(last.time), now).toMinutes() }.getOrDefault(0L)
            val agoText = when {
                ago < 60 -> "há $ago min"
                ago < 1440 -> "há ${ago / 60} h"
                else -> "há ${ago / 1440} d"
            }
            Text("${s.count24h} locais em 24 h", style = TextStyle(color = ColorProvider(if (ago < 180) BLOOD else BONE), fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Text("Último $agoText: ${last.threat}", style = TextStyle(color = ColorProvider(BONE), fontSize = 11.sp), maxLines = 1)
            Text(last.cities.take(4).joinToString(", ") { it.name }, style = TextStyle(color = ColorProvider(ASH), fontSize = 11.sp), maxLines = 2)
        }
    }
}
