package com.abugdn.wid.data

import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Data que costuma mexer com as guerras. [approx] = depende da lua ou do calendário local. */
data class AgendaEvent(val date: LocalDate, val text: String, val tag: String? = null, val approx: Boolean = false)

/** Aniversários que se repetem todo ano (dia/mês). */
private data class Anniversary(val day: MonthDay, val text: String, val tag: String?)

private val ANNIVERSARIES = listOf(
    Anniversary(MonthDay.of(1, 3), "Aniversário da morte de Qassem Soleimani (2020)", "ira"),
    Anniversary(MonthDay.of(2, 11), "Aniversário da Revolução Islâmica no Irã (1979)", "ira"),
    Anniversary(MonthDay.of(2, 24), "Aniversário da invasão russa da Ucrânia (2022)", "ucrania_russia"),
    Anniversary(MonthDay.of(3, 21), "Nowruz, ano-novo persa", "ira"),
    Anniversary(MonthDay.of(4, 15), "Aniversário da guerra civil no Sudão (2023)", "sudao"),
    Anniversary(MonthDay.of(5, 9), "Dia da Vitória na Rússia, com desfile militar em Moscou", "ucrania_russia"),
    Anniversary(MonthDay.of(5, 15), "Dia da Nakba, lembrado pelos palestinos", "gaza"),
    Anniversary(MonthDay.of(6, 13), "Aniversário da guerra de 12 dias entre Israel e Irã (2025)", "ira"),
    Anniversary(MonthDay.of(8, 24), "Dia da Independência da Ucrânia", "ucrania_russia"),
    Anniversary(MonthDay.of(9, 17), "Aniversário da explosão dos pagers do Hezbollah (2024)", "libano"),
    Anniversary(MonthDay.of(10, 7), "Aniversário do ataque do Hamas a Israel (2023)", "israel"),
    Anniversary(MonthDay.of(10, 10), "Aniversário do cessar-fogo em Gaza (2025)", "gaza"),
    Anniversary(MonthDay.of(11, 27), "Aniversário do cessar-fogo entre Israel e Hezbollah (2024)", "libano"),
    Anniversary(MonthDay.of(12, 8), "Aniversário da queda de Bashar al-Assad (2024)", "siria"),
)

/** Datas únicas (eleições, feriados religiosos com data móvel). */
private val ONE_OFF = listOf(
    AgendaEvent(LocalDate.of(2026, 10, 27), "Prazo legal para a eleição do Knesset (Parlamento de Israel)", "israel"),
    AgendaEvent(LocalDate.of(2026, 11, 3), "Eleições de meio de mandato nos EUA (Congresso)", "eua"),
    AgendaEvent(LocalDate.of(2026, 12, 4), "Começa o Hanucá (festa judaica de 8 dias), ao pôr do sol", "israel"),
    AgendaEvent(LocalDate.of(2027, 2, 8), "Começa o Ramadã", null, approx = true),
    AgendaEvent(LocalDate.of(2027, 3, 5), "Dia de Al-Quds (Jerusalém), com atos no Irã e no Líbano", "ira", approx = true),
    AgendaEvent(LocalDate.of(2027, 3, 10), "Eid al-Fitr, fim do Ramadã", null, approx = true),
    AgendaEvent(LocalDate.of(2027, 4, 22), "Pessach (Páscoa judaica), começa na noite anterior", "israel"),
    AgendaEvent(LocalDate.of(2027, 5, 17), "Eid al-Adha (Festa do Sacrifício)", null, approx = true),
    AgendaEvent(LocalDate.of(2027, 6, 15), "Ashura, data central para os xiitas (Irã, Iraque, Hezbollah)", "ira", approx = true),
    AgendaEvent(LocalDate.of(2027, 10, 2), "Rosh Hashaná (ano-novo judaico)", "israel"),
    AgendaEvent(LocalDate.of(2027, 10, 11), "Yom Kipur (Dia do Perdão)", "israel"),
)

/** Próximos [days] dias de agenda, em ordem. */
fun upcomingAgenda(today: LocalDate = LocalDate.now(), days: Long = 120): List<AgendaEvent> {
    val end = today.plusDays(days)
    val anniversaries = (today.year..end.year).flatMap { year ->
        ANNIVERSARIES.map { AgendaEvent(it.day.atYear(year), it.text, it.tag) }
    }
    return (anniversaries + ONE_OFF).filter { !it.date.isBefore(today) && !it.date.isAfter(end) }.sortedBy { it.date }
}

/** Marcos com data completa (d/m/aaaa) que fazem aniversário hoje. */
fun onThisDay(today: LocalDate = LocalDate.now()): List<Pair<String, Milestone>> =
    MILESTONES.flatMap { (tag, list) ->
        list.mapNotNull { m ->
            val parts = m.date.split("/").mapNotNull { it.toIntOrNull() }
            if (parts.size == 3 && parts[0] == today.dayOfMonth && parts[1] == today.monthValue) tag to m else null
        }
    }

/** Cidade para o relógio das capitais. */
data class CapitalClock(val city: String, val zone: String, val lat: Double, val lon: Double)

val CAPITALS = listOf(
    CapitalClock("Jerusalém", "Asia/Jerusalem", 31.77, 35.21),
    CapitalClock("Gaza", "Asia/Gaza", 31.50, 34.47),
    CapitalClock("Beirute", "Asia/Beirut", 33.89, 35.50),
    CapitalClock("Damasco", "Asia/Damascus", 33.51, 36.29),
    CapitalClock("Bagdá", "Asia/Baghdad", 33.31, 44.36),
    CapitalClock("Teerã", "Asia/Tehran", 35.69, 51.39),
    CapitalClock("Sanaa", "Asia/Aden", 15.37, 44.19),
    CapitalClock("Cartum", "Africa/Khartoum", 15.50, 32.56),
    // "Europe/Kiev" é o nome antigo, presente em todas as versões do Android.
    CapitalClock("Kiev", "Europe/Kiev", 50.45, 30.52),
    CapitalClock("Moscou", "Europe/Moscow", 55.75, 37.62),
    CapitalClock("Washington", "America/New_York", 38.90, -77.04),
    CapitalClock("Brasília", "America/Sao_Paulo", -15.79, -47.88),
)

/**
 * Nascer e pôr do sol (aproximação da NOAA, erro de ~1–2 min). Null em dia/noite polar.
 */
fun sunTimes(date: LocalDate, lat: Double, lon: Double): Pair<Instant, Instant>? {
    val g = 2 * PI / 365 * (date.dayOfYear - 1)
    val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
    val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
        0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
    val latR = Math.toRadians(lat)
    val cosHa = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
    if (cosHa < -1 || cosHa > 1) return null
    val ha = Math.toDegrees(acos(cosHa))
    val midnight = date.atStartOfDay(ZoneId.of("UTC")).toInstant()
    val rise = midnight.plusSeconds(((720 - 4 * (lon + ha) - eqTime) * 60).toLong())
    val set = midnight.plusSeconds(((720 - 4 * (lon - ha) - eqTime) * 60).toLong())
    return rise to set
}
