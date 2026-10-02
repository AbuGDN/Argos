package com.abugdn.wid.data

/**
 * Protege nomes de pessoas e siglas do tradutor do aparelho, que traduz palavra por palavra:
 * "Renee Good" virava "rene bom" e "ICE" virava "gelo". Antes de traduzir, cada nome e sigla vira
 * um marcador que o ML Kit não mexe ([MARKERS]); depois, o original volta no lugar.
 *
 * Os nomes são aprendidos dos próprios textos ([learn]): sequências de duas ou mais palavras com
 * maiúscula no meio de uma frase normal ("the family of Renee Good files…"). Manchetes Com Toda
 * Palavra Em Maiúscula não servem de prova, mas são protegidas com o que se aprendeu nos resumos.
 * Só inglês: hebraico e árabe não têm maiúsculas.
 *
 * Mexe só onde o tradutor erra. Medido no emulador em 01/10/2026 (19 frases reais, 5 técnicas):
 * nomes sem palavra comum ("Elon Musk", "Pete Hegseth") e siglas conhecidas ("BBC", "CIA") já
 * saíam certos, e trocá-los piorava a frase ("diz ao BBC" em vez de "à BBC").
 */
object NameGuard {
    /**
     * Marcadores. Medido: o código sobrevive em todas as frases (às vezes em minúsculas, por isso a
     * volta ignora a caixa); sobrenome inventado ("Kowalski") bagunçava frases inteiras, e "X1"
     * deixava palavras sem traduzir.
     */
    internal val MARKERS = listOf("ZQXA", "ZQXB", "ZQXC", "ZQXD", "ZQXE", "ZQXF", "ZQXG", "ZQXH")

    /** Siglas que o tradutor já acerta sozinho: trocar só estraga o artigo ("ao BBC"). */
    private val KNOWN_ACRONYMS = setOf(
        "BBC", "CNN", "CIA", "FBI", "NSA", "NASA", "NATO", "OTAN", "CEO", "GDP", "TV", "AI", "IA", "OK",
        "UNICEF", "UNESCO", "OPEC", "FIFA", "DNA", "HIV", "COVID", "GPS", "AP", "AFP", "RAF",
    )

    /**
     * Sobrenomes sozinhos que são palavra do inglês e estão sempre nas notícias. Medido: "Top Trump
     * Officials" saía "top trunfats" (trump = trunfo).
     */
    private val ALWAYS = setOf("Trump")

    private const val B = "(?<![\\p{L}\\d])"
    private const val E = "(?![\\p{L}\\d])"

    /** Palavras com maiúscula que abrem ou acompanham um nome mas não fazem parte dele. */
    private val TITLES = setOf(
        "President", "Prime", "Minister", "Secretary", "General", "Gen", "Defense", "Foreign", "Senator", "Sen",
        "Rep", "Representative", "Gov", "Governor", "Mayor", "Pope", "King", "Queen", "Prince", "Princess", "Sheikh",
        "Ayatollah", "Colonel", "Col", "Captain", "Capt", "Lt", "Commander", "Chief", "Spokesman", "Spokeswoman",
        "Ambassador", "Judge", "Justice", "Dr", "Mr", "Mrs", "Ms", "Former", "Ex", "Vice", "Deputy", "Speaker",
        "Leader", "Chancellor", "Emir", "Crown", "Sultan", "Lord", "Sir", "Rabbi", "Imam", "Bishop", "Cardinal",
        "Rev", "Officer", "Agent", "Sgt", "Sergeant", "Maj", "Major", "Adm", "Admiral", "Brig", "Envoy", "Chair",
        "Chairman", "Director", "Professor", "Prof", "Analyst", "Reporter", "Correspondent", "Editor",
        "Secretary-General", "Spokesperson", "Spokesman's", "Senior", "Retired",
    )

    /**
     * Palavras que fazem a sequência ser instituição, lugar, data ou começo de frase, não pessoa.
     * Essas o tradutor acerta ("Red Cross" -> "Cruz Vermelha"), então ficam de fora.
     */
    private val NOT_PERSON = setOf(
        "Court", "House", "Council", "Sea", "Ministry", "Forces", "Force", "Army", "Party", "Bank", "Strip", "Union",
        "Nations", "States", "Department", "Agency", "Committee", "Senate", "Congress", "Guard", "Guards", "Corps",
        "Command", "University", "Hospital", "News", "Times", "Post", "Gulf", "Ocean", "River", "Valley", "Heights",
        "Street", "Square", "Airport", "Republic", "Kingdom", "Office", "Service", "Services", "Authority",
        "Authorities", "Group", "Organization", "Organisation", "Movement", "Front", "Brigade", "Brigades", "Navy",
        "Air", "Airlines", "Airways", "Company", "Corp", "Inc", "Fund", "Bureau", "Center", "Centre", "Institute",
        "Foundation", "Commission", "Assembly", "Parliament", "Cabinet", "Government", "Administration", "Police",
        "Security", "Intelligence", "Mission", "Program", "Programme", "Project", "Treaty", "Accord", "Agreement",
        "Accords", "War", "Wars", "Day", "Week", "Cup", "Games", "Island", "Islands", "Mountains", "Desert", "Canal",
        "Bridge", "Dam", "Prison", "Camp", "Base", "Port", "City", "County", "Province", "Region", "District",
        "Territory", "Coast", "East", "West", "North", "South", "Middle", "Northern", "Southern", "Eastern",
        "Western", "Central", "Global", "International", "National", "Federal", "Supreme", "High", "Royal",
        "Islamic", "United", "Democratic", "Republican", "Cross", "Crescent", "Act", "Law", "Bill", "Contest",
        "Festival", "Prize", "Award", "Forum", "Summit", "Conference", "Operation", "Maps", "Enforcement",
        "Complex", "Directorate", "Media", "Network", "Channel", "Radio", "Television", "Journal", "Magazine",
        "Gazette", "Herald", "Tribune", "Review", "Independent", "Partners", "Analytics", "Financing", "Terror",
        "Terrorism", "Shield", "Marine", "Marines", "Peshmerga", "Allah", "Hezbollah", "Hamas",
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
        "November", "December", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        "The", "A", "An", "In", "On", "At", "Of", "For", "And", "But", "With", "To", "From", "By", "As", "After",
        "Before", "Over", "Under", "Off", "Why", "How", "What", "Who", "When", "Where", "Is", "Are", "Was", "Were",
        "Will", "Can", "Could", "Would", "Should", "Not", "No", "New", "Old", "First", "Second", "Third", "Last",
        "Top", "Big", "Inside", "Live", "Watch", "Opinion", "Analysis", "Exclusive", "Breaking", "Update",
        "Report", "Reports", "Says", "Said", "Video", "Photos", "One", "Two", "Three", "Four", "Five", "Great",
        "God", "Christmas", "Easter", "Ramadan", "Eid", "Hanukkah", "Passover", "Yom", "Kippur",
        // Vistas grudadas em nomes no feed de 01/10/2026 ("Ukraine Missile", "Returning Russia").
        // Começo de frase seguido de gentílico ("Most Americans", "Several Palestinians").
        "Most", "Several", "Some", "Many", "Few", "All", "Both", "Other", "Others", "Thousands", "Hundreds",
        "Dozens", "Millions", "Experts", "Officials", "Residents", "Families", "Witnesses", "Critics",
        "Meanwhile", "Returning", "Briefing", "Sept", "Hero", "Defence", "People", "Missile", "Missiles", "Pact",
        "Platform", "Systems", "System", "Affairs", "Airbase", "Operations", "Data", "Trade", "Maritime",
        "Hostages", "Ceasefire", "Peace", "Plan", "Deal", "Talks", "Strike", "Attack", "Attacks", "Drone", "Drones",
        // Países e regiões das notícias: sequência com eles é lugar ou manchete, não pessoa.
        "Ukraine", "Russia", "Israel", "Iran", "Gaza", "Lebanon", "Syria", "Iraq", "Yemen", "Egypt", "Jordan",
        "Qatar", "Turkey", "China", "Taiwan", "America", "Europe", "Africa", "Asia", "Tigray", "Sudan", "Libya",
        "Somalia", "Ethiopia", "Eritrea", "Myanmar", "Afghanistan", "Pakistan", "India", "Kashmir", "Crimea",
        "Donbas", "Kharkiv", "Kyiv", "Moscow", "Tehran", "Beirut", "Damascus", "Baghdad", "Jerusalem", "Washington",
        "London", "Paris", "Berlin", "Brussels", "Dubai", "Flydubai", "FlyDubai", "Euronews",
    )

    private val WORD = Regex("\\p{Lu}[\\p{Ll}]+(?:[-'’]\\p{Lu}?\\p{Ll}+)*")
    private val TOKEN = Regex("[\\p{L}][\\p{L}'’.\\-]*|[^\\s\\p{L}]")
    private val SENTENCE = Regex("(?<=[.!?:;—–])\\s+")
    // Até 4 letras: com mais, costuma ser palavra gritada ("PRESS", "FRANCE"), não sigla.
    private val ACRONYM = Regex("$B\\p{Lu}{2,4}$E")
    private val LATIN = Regex("[A-Za-z]")
    private val NON_LATIN = Regex("[\\u0590-\\u06ff]")

    /** Texto Com Quase Toda Palavra Em Maiúscula (manchete no estilo americano). */
    internal fun isTitleCase(text: String): Boolean {
        // Nesse estilo só as palavrinhas ("of", "the", "with") ficam minúsculas. Uma frase normal,
        // mesmo cheia de nomes ("Former President George Bush met Condoleezza Rice…"), tem algum
        // verbo ou substantivo em minúscula.
        val words = text.split(' ').map { w -> w.trim { !it.isLetter() } }.filter { it.length >= 3 }
        if (words.any { it.first().isLowerCase() && it.lowercase() !in SMALL_WORDS }) return false
        return words.count { it.length >= 4 && it.first().isUpperCase() } >= 4
    }

    private val SMALL_WORDS = setOf(
        "the", "and", "for", "but", "nor", "yet", "with", "from", "into", "onto", "over", "upon", "than", "via",
        "per", "off", "out", "amid", "after", "about", "as", "its", "his", "her", "their",
    )

    private fun isEnglish(text: String) = LATIN.containsMatchIn(text) && !NON_LATIN.containsMatchIn(text)

    /**
     * Nomes de pessoas (duas ou mais palavras) vistos no meio de frases normais, só os que têm uma
     * palavra comum do inglês: a que também aparece em minúsculas nos textos ("Renee Good" porque
     * "good" aparece; "Elon Musk" o tradutor já mantém).
     */
    fun learn(texts: Collection<String>): Set<String> {
        val english = texts.filter(::isEnglish)
        val lower = english.flatMapTo(HashSet(SURNAME_WORDS)) { t -> LOWER_WORD.findAll(t).map { it.value } }
        return people(english).filterTo(HashSet()) { name -> name.split(' ').any { it.lowercase() in lower } }
    }

    private val LOWER_WORD = Regex("$B\\p{Ll}+$E")

    /** Sobrenomes que são palavras do inglês, para quando a palavra não aparece nos textos do dia. */
    private val SURNAME_WORDS = setOf(
        "good", "rice", "bush", "price", "black", "white", "brown", "green", "young", "king", "hill", "wood",
        "cook", "fox", "hunt", "rose", "hope", "bill", "long", "little", "strong", "rich", "wise", "gold",
        "stone", "bird", "bell", "frost", "snow", "love", "sharp", "lamb", "bishop", "baker", "mason", "cash",
        "banks", "page", "ford", "park", "case", "field", "march", "burns", "waters", "rivers", "woods", "lane",
        "pence", "may", "graham", "walker", "hunter", "fisher", "carpenter", "cooper", "shepherd", "porter",
    )

    private fun people(texts: List<String>): Set<String> = buildSet {
        for (text in texts) {
            if (isTitleCase(text)) continue
            for (sentence in text.split(SENTENCE)) {
                val run = mutableListOf<String>()
                fun close() {
                    if (run.size >= 2 && run.none { it in NOT_PERSON || it in DEMONYMS || it.removeSuffix("s") in DEMONYMS }) {
                        add(run.joinToString(" "))
                    }
                    run.clear()
                }
                for (raw in TOKEN.findAll(sentence).map { it.value }) {
                    val w = raw.trimEnd('.').removeSuffix("'s").removeSuffix("’s")
                    val possessive = w.length != raw.trimEnd('.').length
                    // A 1ª palavra da frase também conta ("…death. Becca Good said…"): sozinha ela
                    // não forma sequência, e "The"/"Israeli" no começo caem em NOT_PERSON/DEMONYMS.
                    if (WORD.matches(w) && w !in TITLES) {
                        run += w
                        if (possessive || raw.endsWith('.')) close()
                    } else {
                        close()
                    }
                }
                close()
            }
        }
    }

    /** Gentílicos ficam de fora: "Israeli Army" o tradutor acerta. */
    private val DEMONYMS = setOf(
        "Israeli", "Iranian", "American", "Palestinian", "Russian", "Lebanese", "Syrian", "Ukrainian", "Chinese",
        "British", "French", "German", "Turkish", "Saudi", "Iraqi", "Yemeni", "Egyptian", "Qatari", "Emirati",
        "Jordanian", "Kuwaiti", "Houthi", "Afghan", "Pakistani", "Indian", "Sudanese", "Libyan", "Somali",
        "Ethiopian", "Kurdish", "Australian", "Canadian", "Italian", "Spanish", "Polish", "European", "African",
        "Asian", "Arabian", "Persian", "Japanese", "Korean", "Taiwanese", "Brazilian", "Mexican", "Venezuelan",
        "Armenian", "Azerbaijani", "Georgian", "Belarusian", "Hungarian", "Romanian", "Bahraini", "Omani",
        "Tunisian", "Algerian", "Moroccan", "Nigerian", "Malaysian", "Indonesian", "Burmese", "Iranian-backed",
    )

    /** Texto pronto para o tradutor e o que volta no lugar de cada marcador. */
    class Guarded(val text: String, val originals: List<String>)

    /**
     * Troca nomes conhecidos e siglas por marcadores. Recebe o inglês já passado pelo glossário
     * (as siglas que ele expande, como "US" e "IDF", já viraram nome por extenso).
     */
    fun protect(text: String, names: Set<String>): Guarded {
        val originals = mutableListOf<String>()
        fun mark(found: String): String {
            val i = originals.indexOf(found).takeIf { it >= 0 } ?: originals.size.also { originals += found }
            return MARKERS.getOrNull(i) ?: found
        }
        var out = text
        // Nomes mais longos primeiro ("Renee Good" antes de um possível "Good").
        for (name in (names + ALWAYS).sortedByDescending { it.length }) {
            if (originals.size >= MARKERS.size) break
            if (!out.contains(name)) continue
            out = Regex("$B${Regex.escape(name)}$E").replace(out) { mark(it.value) }
        }
        // Siglas, menos em texto TODO EM MAIÚSCULAS (aí não dá para saber o que é sigla).
        val letters = text.filter { it.isLetter() }
        if (letters.count { it.isUpperCase() } < letters.length * 0.6) {
            out = ACRONYM.replace(out) { m ->
                val free = originals.size < MARKERS.size || m.value in originals
                if (free && m.value !in KNOWN_ACRONYMS && m.value !in MARKERS) mark(m.value) else m.value
            }
        }
        return Guarded(out, originals)
    }

    /** Põe os originais de volta no lugar dos marcadores. */
    fun restore(translated: String, guarded: Guarded): String =
        guarded.originals.foldIndexed(translated) { i, acc, original ->
            Regex("$B${MARKERS[i]}$E", RegexOption.IGNORE_CASE).replace(acc, Regex.escapeReplacement(original))
        }
}
