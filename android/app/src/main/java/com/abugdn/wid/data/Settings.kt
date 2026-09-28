package com.abugdn.wid.data

import android.content.SharedPreferences
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

data class Settings(
    val notifyUrgent: Boolean = true,
    val notifyTop: Boolean = true,
    val dailyDigest: Boolean = true,
    val digestHour: Int = 8,
    /** Regiões (tags) que geram notificação. Vazio = todas. */
    val regions: Set<String> = emptySet(),
    val quietHours: Boolean = true,
    val quietStart: Int = 22,
    val quietEnd: Int = 7,
    /** Identidade Argos: escuro por padrão. */
    val theme: ThemeMode = ThemeMode.DARK,
    /** Termos que sempre geram notificação quando aparecem numa notícia. */
    val watchWords: Set<String> = emptySet(),
    /** Multiplicador do tamanho do texto em todo o app. */
    val textScale: Float = 1f,
    val weeklyDigest: Boolean = true,
    /** Veículos escondidos do app inteiro. */
    val hiddenSources: Set<String> = emptySet(),
    /** Veículos preferidos: dão o título do grupo e sobem no ranking. */
    val preferredSources: Set<String> = emptySet(),
    /** Sem imagens; textos completos e modelo de tradução só no Wi-Fi. */
    val dataSaver: Boolean = false,
    /** Modo leitura: fonte serifada e espaçamento maior no texto completo. */
    val readerSerif: Boolean = false,
    val readerWide: Boolean = false,
    /** Borra fotos de notícias com mortos/feridos até tocar. */
    val blurSensitive: Boolean = true,
    /** Aviso do Radar: apagão de internet ou espaço aéreo fechado. */
    val notifyRadar: Boolean = true,
    /** Blocos da tela Hoje na ordem escolhida (ids de [HOME_BLOCKS]) e os escondidos. */
    val homeOrder: List<String> = HOME_BLOCKS.keys.toList(),
    val homeHidden: Set<String> = emptySet(),
    /** Mini-cartões do painel da tela Hoje (ids de [PANEL_ITEMS]) e os escondidos. */
    val panelOrder: List<String> = PANEL_ITEMS.keys.toList(),
    val panelHidden: Set<String> = emptySet(),
    /** Desliga as animações do Argos mesmo com as do Android ligadas. */
    val reduceMotion: Boolean = false,
) {
    /** Faixa de manchetes rolando no topo da tela Hoje. */
    val showTicker: Boolean get() = "ticker" !in homeHidden

    fun matchesRegion(tags: List<String>) = regions.isEmpty() || tags.any { it in regions }

    fun isQuiet(now: LocalTime = LocalTime.now()): Boolean {
        if (!quietHours) return false
        val h = now.hour
        return if (quietStart > quietEnd) h >= quietStart || h < quietEnd else h in quietStart until quietEnd
    }
}

class SettingsStore(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        prefs.edit()
            .putBoolean("s_notify_urgent", next.notifyUrgent)
            .putBoolean("s_notify_top", next.notifyTop)
            .putBoolean("s_daily_digest", next.dailyDigest)
            .putInt("s_digest_hour", next.digestHour)
            .putStringSet("s_regions", next.regions)
            .putBoolean("s_quiet", next.quietHours)
            .putInt("s_quiet_start", next.quietStart)
            .putInt("s_quiet_end", next.quietEnd)
            .putString("s_theme", next.theme.name)
            .putStringSet("s_watch", next.watchWords)
            .putFloat("s_text_scale", next.textScale)
            .putBoolean("s_weekly", next.weeklyDigest)
            .putStringSet("s_hidden_sources", next.hiddenSources)
            .putStringSet("s_preferred_sources", next.preferredSources)
            .putBoolean("s_data_saver", next.dataSaver)
            .putBoolean("s_reader_serif", next.readerSerif)
            .putBoolean("s_reader_wide", next.readerWide)
            .putBoolean("s_blur", next.blurSensitive)
            .putBoolean("s_notify_radar", next.notifyRadar)
            .putString("s_home_order", next.homeOrder.joinToString(","))
            .putStringSet("s_home_hidden", next.homeHidden)
            .putString("s_panel_order", next.panelOrder.joinToString(","))
            .putStringSet("s_panel_hidden", next.panelHidden)
            .putBoolean("s_reduce_motion", next.reduceMotion)
            .apply()
        _state.value = next
        onChange?.invoke(next)
    }

    /** Avisado a cada mudança (o Repository reaplica os filtros de veículos). */
    var onChange: ((Settings) -> Unit)? = null

    private fun load() = Settings(
        notifyUrgent = prefs.getBoolean("s_notify_urgent", true),
        notifyTop = prefs.getBoolean("s_notify_top", true),
        dailyDigest = prefs.getBoolean("s_daily_digest", true),
        digestHour = prefs.getInt("s_digest_hour", 8),
        regions = prefs.getStringSet("s_regions", emptySet())!!.toSet(),
        quietHours = prefs.getBoolean("s_quiet", true),
        quietStart = prefs.getInt("s_quiet_start", 22),
        quietEnd = prefs.getInt("s_quiet_end", 7),
        theme = runCatching { ThemeMode.valueOf(prefs.getString("s_theme", null)!!) }.getOrDefault(ThemeMode.DARK),
        watchWords = prefs.getStringSet("s_watch", emptySet())!!.toSet(),
        textScale = prefs.getFloat("s_text_scale", 1f),
        weeklyDigest = prefs.getBoolean("s_weekly", true),
        hiddenSources = prefs.getStringSet("s_hidden_sources", emptySet())!!.toSet(),
        preferredSources = prefs.getStringSet("s_preferred_sources", emptySet())!!.toSet(),
        dataSaver = prefs.getBoolean("s_data_saver", false),
        readerSerif = prefs.getBoolean("s_reader_serif", false),
        readerWide = prefs.getBoolean("s_reader_wide", false),
        blurSensitive = prefs.getBoolean("s_blur", true),
        notifyRadar = prefs.getBoolean("s_notify_radar", true),
        homeOrder = orderOf(prefs.getString("s_home_order", null), HOME_BLOCKS.keys),
        // Quem desligou a faixa de manchetes antes (ajuste antigo) continua sem ela.
        homeHidden = prefs.getStringSet("s_home_hidden", null)?.toSet()
            ?: if (prefs.getBoolean("s_ticker", true)) emptySet() else setOf("ticker"),
        panelOrder = orderOf(prefs.getString("s_panel_order", null), PANEL_ITEMS.keys),
        panelHidden = prefs.getStringSet("s_panel_hidden", emptySet())!!.toSet(),
        reduceMotion = prefs.getBoolean("s_reduce_motion", false),
    )

    /** Ordem salva + blocos novos que surgirem em versões futuras (no fim). */
    private fun orderOf(saved: String?, all: Set<String>): List<String> {
        val list = saved?.split(',')?.filter { it in all }.orEmpty()
        return list + all.filter { it !in list }
    }
}

/** Blocos que dá para mover e esconder na tela Hoje. */
val HOME_BLOCKS = linkedMapOf(
    "ticker" to "Faixa de manchetes",
    "panel" to "Painel (Relógio, alertas e atalhos)",
    "filters" to "Filtros por região",
    "top" to "Principal do dia",
)

/** Mini-cartões do painel da tela Hoje. */
val PANEL_ITEMS = linkedMapOf(
    "clock" to "Relógio do Argos",
    "radar" to "Alertas do Radar",
    "story" to "O dia em 1 minuto",
    "truce" to "Trégua",
    "agenda" to "Próxima data da agenda",
    "vigil" to "Último registro da vigília",
    "tools" to "Ferramentas",
)

/** Move [id] uma posição para cima (-1) ou para baixo (+1). */
fun List<String>.moved(id: String, by: Int): List<String> {
    val i = indexOf(id)
    val j = i + by
    if (i < 0 || j !in indices) return this
    return toMutableList().also { it[i] = it[j]; it[j] = id }
}

private val MARKS = Regex("\\p{M}+")

/** Minúsculas e sem acento, para comparar "Irã" com "ira" e "Líbano" com "libano". */
fun normalize(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase()

private val wordRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

/**
 * Regex de palavra inteira para um termo já normalizado, compilada uma vez só e reaproveitada.
 * Compilar a cada chamada (centenas de termos × centenas de notícias) travava o app.
 * [suffix] vai depois do termo antes da fronteira final ("s?" aceita plural); [open] não exige
 * fronteira no fim (casa o começo da palavra).
 */
fun wordRegex(term: String, suffix: String = "", open: Boolean = false, digits: Boolean = true): Regex =
    wordRegexCache.getOrPut("$term|$suffix|$open|$digits") {
        val boundary = if (digits) "[\\p{L}\\d]" else "[\\p{L}]"
        Regex("(?<!$boundary)" + Regex.escape(term) + suffix + if (open) "" else "(?!$boundary)")
    }

/** Primeiro termo vigiado que aparece na notícia (no original ou na tradução). */
fun Cluster.matchWatchWord(words: Set<String>, translated: (String) -> String): String? {
    if (words.isEmpty()) return null
    val haystack = normalize(
        buildString {
            append(title).append('\n').append(summary).append('\n').append(translated(title))
            articles.forEach { append('\n').append(it.title) }
        }
    )
    // Casa no início de uma palavra: "hezbollah" acha "Hezbollah's", mas "ira" não acha "mira".
    return words.firstOrNull { word ->
        val w = normalize(word.trim())
        w.isNotEmpty() && wordRegex(w, open = true).containsMatchIn(haystack)
    }
}

private val SENSITIVE_TERMS = listOf(
    "morto", "mortos", "morta", "mortas", "morte", "mortes", "matou", "matam", "mata", "massacre", "chacina",
    "ferido", "feridos", "feridas", "vítima", "vítimas", "corpo", "corpos", "cadáver",
    "killed", "kill", "kills", "dead", "death", "deaths", "dies", "died", "wounded", "injured",
    "casualties", "bodies", "body", "massacre", "slaughter", "toll",
)

private val SENSITIVE_NORMALIZED by lazy { SENSITIVE_TERMS.map(::normalize).distinct() }

/** Notícia que fala de mortos ou feridos: a foto aparece borrada até tocar. */
fun Cluster.isSensitive(translated: (String) -> String): Boolean {
    val text = normalize("$title $summary ${translated(title)}")
    return SENSITIVE_NORMALIZED.any { wordRegex(it, digits = false).containsMatchIn(text) }
}
