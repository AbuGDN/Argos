package com.abugdn.wid.data

import android.content.Context
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Repositório de dados. O projeto foi renomeado de WID para Argos: tenta o nome novo e, se
 * não achar (repositório ainda não renomeado), o antigo. O GitHub redireciona o antigo depois.
 */
val DATA_URLS = listOf(
    "https://raw.githubusercontent.com/AbuGDN/Argos/gh-pages",
    "https://raw.githubusercontent.com/AbuGDN/WID/gh-pages",
)

/**
 * A Wikimedia pede um user-agent que identifique o app. Só ASCII: o OkHttp recusa acentos em
 * cabeçalhos com uma exceção que derrubava o app (o "í" de "notícias" já fez isso).
 */
const val WIKI_USER_AGENT = "Argos/1.0 (https://github.com/AbuGDN/Argos; personal news app)"

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"

class Repository(context: Context) {
    val storage = Storage(context)
    val translator = Translator(storage)
    val settings = SettingsStore(storage.prefs)
    val updater = Updater(context.applicationContext)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Feed como veio do servidor; [feed] é ele com os veículos escondidos/preferidos aplicados. */
    private var raw: Feed? = storage.loadFeed()
    private val _feed = MutableStateFlow(applySourcePrefs(raw, settings.value))
    val feed: StateFlow<Feed?> = _feed.asStateFlow()

    init {
        // Regras de tradução mudaram: descarta títulos/resumos traduzidos com as regras antigas.
        if (storage.prefs.getInt("glossary_version", 0) != TranslationGlossary.VERSION) {
            storage.translations.clear()
            storage.saveTranslations()
            storage.prefs.edit().putInt("glossary_version", TranslationGlossary.VERSION).apply()
        }
        translator.wifiOnly = settings.value.dataSaver
        settings.onChange = {
            _feed.value = applySourcePrefs(raw, it)
            translator.wifiOnly = it.dataSaver
        }
    }

    private val _snapshots = MutableStateFlow(storage.loadSnapshots())
    /** id da história -> ids dos artigos que ela tinha quando foi lida pela última vez. */
    val snapshots: StateFlow<Map<String, Set<String>>> = _snapshots.asStateFlow()

    private val _savedMeta = MutableStateFlow(storage.loadSavedMeta())
    val savedMeta: StateFlow<Map<String, SavedMeta>> = _savedMeta.asStateFlow()

    private val _followed = MutableStateFlow(loadFollowed())
    /** id -> quantos veículos a história tinha na última vez que avisamos. */
    val followed: StateFlow<Map<String, Int>> = _followed.asStateFlow()

    private val _first = MutableStateFlow<FirstStats?>(null)
    val first: StateFlow<FirstStats?> = _first.asStateFlow()

    suspend fun loadFirst(): Result<FirstStats> = withContext(Dispatchers.IO) {
        runCatching {
            json.decodeFromString<FirstStats>(getData("stats/first.json?t=${System.currentTimeMillis() / 600_000}"))
                .also { _first.value = it }
        }
    }

    private val _vigil = MutableStateFlow(storage.loadVigil())
    /** Registro de vigília: todos os alertas vistos, com data e hora (mais recentes primeiro). */
    val vigil: StateFlow<List<VigilEvent>> = _vigil.asStateFlow()

    @Synchronized
    private fun recordVigil(feed: Feed, radar: RadarData?) {
        val now = System.currentTimeMillis()
        val day = java.time.LocalDate.now().toString()
        val next = Vigil.merge(_vigil.value, Vigil.detect(feed, now, day) + Vigil.detectRadar(radar, now, day))
        if (next !== _vigil.value) {
            storage.saveVigil(next)
            _vigil.value = next
        }
    }

    private val _quotes = MutableStateFlow(storage.loadQuotes())
    /** "Quem disse o quê": frases entre aspas de pessoas-chave, acumuladas com o tempo. */
    val quotes: StateFlow<List<QuoteEntry>> = _quotes.asStateFlow()

    @Synchronized
    private fun recordQuotes(feed: Feed) {
        val found = Quotes.fromFeed(feed.clusters) { text, lang -> if (lang == "pt") text else storage.translations[text] ?: "" }
        val next = Quotes.merge(_quotes.value, found)
        if (next !== _quotes.value) {
            storage.saveQuotes(next)
            _quotes.value = next
        }
    }

    private val _radar = MutableStateFlow(storage.loadRadar())
    /** Radar (radar.json): sensores, cotações, números, vozes oficiais, análises e previsões. */
    val radar: StateFlow<RadarData?> = _radar.asStateFlow()

    /** Baixa o radar.json. Falhar aqui nunca impede o feed de atualizar. */
    private fun fetchRadar(): RadarData? = runCatching {
        val raw = getData("radar.json?t=${System.currentTimeMillis() / 60_000}")
        json.decodeFromString<RadarData>(raw).also { storage.saveRadar(raw) }
    }.getOrNull()

    /** Checagens (Radar) ligadas a uma história do feed. */
    fun factchecksFor(clusterId: String): List<RadarItem> =
        _radar.value?.factcheck?.items.orEmpty().filter { clusterId in it.clusters }

    /** Círculo de alcance que o Mapa deve mostrar ao abrir (ficha de armamento → "ver no mapa"). */
    val mapFocus = MutableStateFlow<String?>(null)

    private val _endedTruces = MutableStateFlow(storage.prefs.getStringSet("ended_truces", emptySet())!!.toSet())
    val endedTruces: StateFlow<Set<String>> = _endedTruces.asStateFlow()

    fun setTruceEnded(key: String, ended: Boolean) {
        val next = if (ended) _endedTruces.value + key else _endedTruces.value - key
        storage.prefs.edit().putStringSet("ended_truces", next).apply()
        _endedTruces.value = next
    }

    private val _stats = MutableStateFlow<DailyStats?>(null)
    val stats: StateFlow<DailyStats?> = _stats.asStateFlow()

    private val _saved = MutableStateFlow(storage.loadSaved())
    val saved: StateFlow<List<Cluster>> = _saved.asStateFlow()

    private val _read = MutableStateFlow(storage.prefs.getStringSet("read_ids", emptySet())!!.toSet())
    val read: StateFlow<Set<String>> = _read.asStateFlow()

    /** Horário da visita anterior: histórias que começaram depois disso são "novas". */
    var previousVisit: Long = storage.prefs.getLong("last_visit", 0)
        private set

    /** Guarda quais veículos a história tinha agora, para marcar os que chegarem depois. */
    fun markRead(cluster: Cluster) {
        val keep = (_feed.value?.clusters.orEmpty() + _saved.value).map { it.id }.toSet() + cluster.id
        val snaps = (_snapshots.value + (cluster.id to cluster.articles.map { it.id }.toSet())).filterKeys { it in keep }
        storage.saveSnapshots(snaps)
        _snapshots.value = snaps
        logRead(cluster)
        markRead(cluster.id)
    }

    fun markRead(id: String) {
        if (id in _read.value) return
        val keep = (_feed.value?.clusters.orEmpty() + _saved.value).map { it.id }.toSet()
        val next = (_read.value + id).filter { it in keep || it == id }.toSet()
        storage.prefs.edit().putStringSet("read_ids", next).apply()
        _read.value = next
    }

    /** Chamado quando o app vai para o fundo: a próxima abertura conta novidades a partir daqui. */
    fun endVisit() {
        storage.prefs.edit().putLong("last_visit", System.currentTimeMillis()).apply()
    }

    fun startVisit() {
        previousVisit = storage.prefs.getLong("last_visit", 0)
    }

    private val _archive = MutableStateFlow<List<HistoryDay>?>(null)
    val archive: StateFlow<List<HistoryDay>?> = _archive.asStateFlow()

    /** Baixa o feed, traduz títulos/resumos em inglês e só então publica para a UI. */
    suspend fun refresh(): Result<Feed> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = getData("feed.json?t=${System.currentTimeMillis() / 60_000}")
            val feed = json.decodeFromString<Feed>(raw)
            val radar = fetchRadar() ?: _radar.value
            recordVigil(feed, radar)
            val texts = feedTexts(feed) + textsOf(_saved.value) + textsOf(_archive.value.orEmpty().map { it.top }) +
                _vigil.value.filter { it.lang != "pt" }.map { it.title } + radar?.foreignTexts().orEmpty()
            translator.translateAll(texts)
            storage.saveTranslations(keep = texts)
            _radar.value = radar
            recordQuotes(feed)
            storage.saveFeed(raw)
            this@Repository.raw = feed
            applySourcePrefs(feed, settings.value).also { _feed.value = it }!!
        }
    }

    private fun feedTexts(feed: Feed): Set<String> = textsOf(feed.clusters)

    private fun textsOf(clusters: List<Cluster>): Set<String> = buildSet {
        for (c in clusters) {
            if (c.lang != "pt") {
                add(c.title)
                if (c.summary.isNotBlank()) add(c.summary)
            }
            c.articles.filter { it.lang != "pt" }.forEach { a ->
                add(a.title)
                a.edits.forEach { add(it.title) }
            }
            c.saga?.chapters?.filter { it.lang != "pt" }?.forEach { add(it.title) }
        }
    }

    /** Procura no feed atual, depois nas salvas e no arquivo. */
    fun cluster(id: String): Cluster? =
        _feed.value?.clusters?.firstOrNull { it.id == id }
            ?: _saved.value.firstOrNull { it.id == id }
            ?: _archive.value?.firstOrNull { it.top.id == id }?.top

    /** Todos os veículos que aparecem no feed (para a tela de escolher fontes). */
    fun knownSources(): List<String> =
        (raw?.clusters.orEmpty().flatMap { c -> c.articles.map { it.source } + c.source } + settings.value.hiddenSources)
            .distinct().sorted()

    private fun loadFollowed(): Map<String, Int> =
        storage.prefs.getStringSet("followed", emptySet())!!.mapNotNull { entry ->
            val (id, count) = entry.split('|').takeIf { it.size == 2 } ?: return@mapNotNull null
            id to (count.toIntOrNull() ?: 0)
        }.toMap()

    private fun saveFollowed(map: Map<String, Int>) {
        storage.prefs.edit().putStringSet("followed", map.map { (id, n) -> "$id|$n" }.toSet()).apply()
        _followed.value = map
    }

    fun toggleFollow(cluster: Cluster) {
        val map = _followed.value
        saveFollowed(if (cluster.id in map) map - cluster.id else map + (cluster.id to cluster.sourcesCount))
    }

    /**
     * Histórias seguidas que ganharam veículos desde o último aviso: devolve (história, quantos novos).
     * Histórias que saíram do feed (mais de 48 h) deixam de ser seguidas.
     */
    fun followUpdates(feed: Feed): List<Pair<Cluster, Int>> {
        val map = _followed.value
        if (map.isEmpty()) return emptyList()
        val byId = feed.clusters.associateBy { it.id }
        val updates = map.mapNotNull { (id, count) ->
            byId[id]?.takeIf { it.sourcesCount > count }?.let { it to it.sourcesCount - count }
        }
        saveFollowed(map.filterKeys { it in byId }.mapValues { (id, n) -> byId[id]?.sourcesCount ?: n })
        return updates
    }

    suspend fun loadStats(): Result<DailyStats> = withContext(Dispatchers.IO) {
        runCatching {
            json.decodeFromString<DailyStats>(getData("stats/daily.json?t=${System.currentTimeMillis() / 600_000}"))
                .also { _stats.value = it }
        }
    }

    /** Última versão cujas novidades a pessoa confirmou com "não mostrar de novo". */
    private fun changelogSeen(): Long {
        val prefs = storage.prefs
        if (prefs.contains("changelog_seen")) return prefs.getLong("changelog_seen", 0)
        // Quem confirmou o aviso antigo (antes do histórico) continua de onde parou.
        LEGACY_WHATS_NEW_IDS[prefs.getString("whats_new_dismissed", null)]?.let { return it }
        // Instalação nova (ou vinda de antes do aviso existir): só a versão atual.
        return (CHANGELOG.firstOrNull { it.versionCode <= updater.installedCode }?.versionCode ?: 0) - 1
    }

    /** Novidades pendentes, da versão mais nova para a mais antiga. */
    fun pendingChangelog(): List<ChangelogEntry> {
        val seen = changelogSeen()
        return CHANGELOG.filter { it.versionCode > seen && it.versionCode <= updater.installedCode }
    }

    /** "Não mostrar de novo até a próxima atualização". */
    fun dismissWhatsNew() {
        storage.prefs.edit().putLong("changelog_seen", updater.installedCode).apply()
    }

    /** Registro de leituras (últimos 30 dias) para o "Sua semana". */
    private val _readLog = MutableStateFlow(storage.loadReadLog())
    val readLog: StateFlow<List<ReadEvent>> = _readLog.asStateFlow()

    private fun logRead(cluster: Cluster) {
        val today = java.time.LocalDate.now().toEpochDay()
        if (_readLog.value.any { it.id == cluster.id && it.day == today }) return
        val next = (_readLog.value + ReadEvent(today, cluster.id, cluster.tags)).filter { it.day > today - 30 }
        storage.saveReadLog(next)
        _readLog.value = next
    }

    fun isSaved(id: String) = _saved.value.any { it.id == id }

    /** Salva (sem prazo de validade) ou remove dos salvos. */
    fun toggleSaved(cluster: Cluster) {
        val list = _saved.value
        val next = if (list.any { it.id == cluster.id }) list.filter { it.id != cluster.id } else listOf(cluster) + list
        storage.saveSaved(next)
        _saved.value = next
        if (next.none { it.id == cluster.id } && cluster.id in _savedMeta.value) {
            saveMeta(_savedMeta.value - cluster.id)
        }
    }

    private fun saveMeta(meta: Map<String, SavedMeta>) {
        storage.saveSavedMeta(meta)
        _savedMeta.value = meta
    }

    /** Pasta (null = sem pasta) e nota de uma notícia salva. */
    fun updateSavedMeta(id: String, folder: String?, note: String?) {
        val clean = SavedMeta(folder?.trim()?.ifEmpty { null }, note?.trim()?.ifEmpty { null })
        saveMeta(if (clean == SavedMeta()) _savedMeta.value - id else _savedMeta.value + (id to clean))
    }

    fun folders(): List<String> = _savedMeta.value.values.mapNotNull { it.folder }.distinct().sorted()

    /** Principal de cada um dos últimos [days] dias (history/ no servidor). */
    suspend fun loadArchive(days: Int = 60, publish: Boolean = true): Result<List<HistoryDay>> = withContext(Dispatchers.IO) {
        runCatching {
            val index = json.decodeFromString<HistoryIndex>(getData("history/index.json?t=${System.currentTimeMillis() / 600_000}"))
            val list = coroutineScope {
                index.days.take(days).map { day ->
                    async { runCatching { json.decodeFromString<HistoryDay>(getData("history/$day.json")) }.getOrNull() }
                }.awaitAll().filterNotNull()
            }
            translator.translateAll(textsOf(list.map { it.top }))
            if (publish) _archive.value = list
            list
        }
    }

    /**
     * Texto completo da história. Prefere um veículo em português (não precisa
     * traduzir); senão baixa o principal e traduz parágrafo por parágrafo.
     */
    suspend fun fullText(cluster: Cluster, translate: Boolean = true): Result<FullText> =
        withContext(Dispatchers.IO) {
            storage.loadFullText(cluster.id)?.let { cached ->
                val upToDate = cached.translated != null && cached.glossary >= TranslationGlossary.VERSION
                if (cached.lang == "pt" || upToDate || !translate) {
                    return@withContext Result.success(cached)
                }
                return@withContext Result.success(addTranslation(cluster.id, cached))
            }
            val candidates = buildList {
                addAll(cluster.articles.filter { it.lang == "pt" }.map { Triple(it.url, it.source, it.lang) })
                add(Triple(cluster.url, cluster.source, cluster.lang))
                addAll(cluster.articles.filter { it.lang != "pt" }.map { Triple(it.url, it.source, it.lang) })
            }.distinctBy { it.first }

            var lastError: Throwable = IOException("sem artigos")
            for ((url, source, lang) in candidates.take(4)) {
                val result = runCatching { extract(url) }
                val paragraphs = result.getOrNull()
                if (paragraphs != null && paragraphs.joinToString("").length >= 400) {
                    var text = FullText(url = url, source = source, lang = lang, paragraphs = paragraphs)
                    storage.saveFullText(cluster.id, text)
                    if (lang != "pt" && translate) text = addTranslation(cluster.id, text)
                    return@withContext Result.success(text)
                }
                lastError = result.exceptionOrNull() ?: IOException("texto curto demais (paywall?)")
            }
            Result.failure(lastError)
        }

    private suspend fun addTranslation(id: String, text: FullText): FullText {
        val translated = translator.translateList(text.paragraphs) ?: return text
        return text.copy(translated = translated, glossary = TranslationGlossary.VERSION)
            .also { storage.saveFullText(id, it) }
    }

    /** Baixa antecipadamente o texto das principais histórias, para ler offline. */
    suspend fun prefetch(feed: Feed, limit: Int = 10) {
        val top = listOfNotNull(feed.topOfDay) + feed.clusters.take(limit)
        for (c in top.distinctBy { it.id }) fullText(c)
        storage.pruneFullTexts(keep = _saved.value.map { it.id }.toSet())
    }

    private val wikiImages = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Foto principal do artigo da Wikipédia (em inglês) [title]. Guardada nas preferências: cada
     * foto é procurada uma vez só. Null se o artigo não tem foto ou se não deu para buscar agora.
     */
    suspend fun wikiImage(title: String): String? = withContext(Dispatchers.IO) {
        wikiImages[title]?.let { return@withContext it.ifEmpty { null } }
        val prefKey = "wimg:$title"
        storage.prefs.getString(prefKey, null)?.let {
            wikiImages[title] = it
            return@withContext it.ifEmpty { null }
        }
        val url = runCatching {
            val encoded = java.net.URLEncoder.encode(title, "UTF-8").replace("+", "_")
            val request = Request.Builder()
                .url("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
                .header("User-Agent", WIKI_USER_AGENT)
                .build()
            http.newCall(request).execute().use { resp ->
                when {
                    resp.code == 404 -> ""
                    !resp.isSuccessful -> null
                    else -> {
                        val body = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                        body["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content.orEmpty()
                    }
                }
            }
        }.getOrNull() ?: return@withContext null
        wikiImages[title] = url
        storage.prefs.edit().putString(prefKey, url).apply()
        url.ifEmpty { null }
    }

    /** Baixa um arquivo da gh-pages, tentando cada endereço do repositório. */
    private fun getData(path: String): String {
        var last: Exception = IOException("sem endereço")
        for (base in DATA_URLS) {
            try {
                return get("$base/$path")
            } catch (e: IOException) {
                last = e
            }
        }
        throw last
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw IOException("resposta vazia")
        }
    }

    private fun extract(url: String): List<String> {
        val html = get(url)
        val article = Readability4JExtended(url, html).parse()
        val content = article.content ?: return emptyList()
        return Jsoup.parse(content).select("p, li")
            .map { it.text().trim() }
            .filter { it.length >= 40 }
            .distinct()
    }
}

/**
 * Aplica os veículos escondidos (somem do grupo; o grupo some se ficar vazio) e os
 * preferidos (dão o título do grupo e sobem 30% no ranking).
 */
fun applySourcePrefs(feed: Feed?, s: Settings): Feed? {
    if (feed == null || (s.hiddenSources.isEmpty() && s.preferredSources.isEmpty())) return feed

    fun adjust(c: Cluster): Cluster? {
        val articles = c.articles.filter { it.source !in s.hiddenSources }
        if (articles.isEmpty() && c.articles.isNotEmpty()) return null
        if (c.articles.isEmpty() && c.source in s.hiddenSources) return null
        val sources = articles.map { it.source }.toSet().ifEmpty { setOf(c.source) }
        val preferred = articles.firstOrNull { it.source in s.preferredSources }
        val lead = when {
            preferred != null && c.source !in s.preferredSources -> preferred
            c.source in s.hiddenSources -> articles.firstOrNull { it.lang == "pt" } ?: articles.first()
            else -> null
        }
        val base = if (lead == null) c else c.copy(
            title = lead.title, summary = lead.summary, url = lead.url, source = lead.source,
            lang = lead.lang, image = lead.image ?: c.image,
        )
        val boost = if (sources.any { it in s.preferredSources }) 1.3 else 1.0
        return base.copy(
            articles = articles, sourcesCount = sources.size,
            score = c.score * boost, dayScore = c.dayScore * boost,
        )
    }

    val clusters = feed.clusters.mapNotNull(::adjust).sortedByDescending { it.score }
    val top = feed.topOfDay?.let { t -> clusters.firstOrNull { it.id == t.id } }
        ?: clusters.maxByOrNull { it.dayScore }
    return feed.copy(clusters = clusters, topOfDay = top)
}
