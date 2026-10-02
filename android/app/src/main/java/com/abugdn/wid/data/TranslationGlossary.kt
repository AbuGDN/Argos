package com.abugdn.wid.data

/**
 * Glossário aplicado em volta do tradutor do aparelho (ML Kit), que erra termos de guerra,
 * siglas e nomes de países.
 *
 * - [preprocess] age no inglês ANTES de traduzir: siglas e palavras ambíguas viram a forma
 *   por extenso que o tradutor acerta, com gramática certa ("US troops" -> "United States
 *   troops" -> "tropas dos Estados Unidos"; sem isso, "US" vira o pronome "nós").
 * - [postprocess] age no português DEPOIS: corrige termos militares, formas de Portugal
 *   (o ML Kit mistura pt-PT), nomes que ficaram em inglês e artigos.
 *
 * Ao mudar as regras (ou o [NameGuard]), aumente [VERSION]: o app descarta as traduções guardadas
 * e retraduz.
 */
object TranslationGlossary {
    const val VERSION = 2

    private class Rule(val rx: Regex, val replace: (MatchResult) -> String)

    /** Troca literal (com grupos $1 etc.). */
    private fun lit(pattern: String, replacement: String, ignoreCase: Boolean = false) = Rule(
        Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()),
    ) { m -> expand(replacement, m) }

    /** Troca de palavra comum mantendo a maiúscula inicial ("Greve" -> "Ataque"). */
    private fun word(pattern: String, replacement: String) = Rule(Regex(pattern, RegexOption.IGNORE_CASE)) { m ->
        val out = expand(replacement, m)
        if (m.value.first().isUpperCase()) out.replaceFirstChar { it.uppercaseChar() } else out
    }

    private fun expand(template: String, m: MatchResult): String =
        Regex("\\$(\\d)").replace(template) { g -> m.groupValues.getOrElse(g.groupValues[1].toInt()) { "" } }

    private fun apply(text: String, rules: List<Rule>): String =
        rules.fold(text) { acc, rule -> rule.rx.replace(acc) { rule.replace(it) } }

    // Fronteiras que funcionam com acentos: "não é letra/dígito".
    private const val B = "(?<![\\p{L}\\d])"
    private const val E = "(?![\\p{L}\\d])"

    // ------------------------------------------------------------------ inglês (antes)

    private val PRE: List<Rule> = listOf(
        // Siglas (sempre em maiúsculas; "us" minúsculo é o pronome e fica).
        lit("${B}U\\.S\\.A?\\.?(?=[\\s,;:'’\")\\-]|$)", "United States"),
        lit("${B}(?:US|USA)$E", "United States"),
        lit("${B}U\\.K\\.(?=[\\s,;:'’\")\\-]|$)", "United Kingdom"),
        lit("${B}UK$E", "United Kingdom"),
        lit("${B}UNSC$E", "United Nations Security Council"),
        lit("${B}U\\.N\\.(?=[\\s,;:'’\")\\-]|$)", "United Nations"),
        lit("${B}UN$E", "United Nations"),
        lit("${B}UNGA$E", "United Nations General Assembly"),
        lit("${B}EU$E", "European Union"),
        lit("${B}UAE$E", "United Arab Emirates"),
        lit("${B}IDF$E", "Israel Defense Forces"),
        lit("${B}IRGC$E", "Islamic Revolutionary Guard Corps"),
        lit("${B}(?:ISIS|ISIL)$E", "Islamic State"),
        lit("${B}DRC$E", "Democratic Republic of the Congo"),
        lit("${B}RSF$E", "Rapid Support Forces"),
        lit("(?<!\\d)(?<!\\d )${B}PM$E", "Prime Minister"),
        lit("${B}FM$E", "Foreign Minister"),
        lit("${B}ICC$E", "International Criminal Court"),
        lit("${B}ICJ$E", "International Court of Justice"),
        lit("${B}IAEA$E", "International Atomic Energy Agency"),
        lit("${B}WHO$E", "World Health Organization"),
        // Países e lugares que o tradutor confunde com palavras comuns ou traduz ao pé da letra.
        lit("${B}Turkey(?=[’']s$E)", "Türkiye"),
        lit("${B}Turkey$E", "Türkiye"),
        lit("${B}Chad$E", "Chade"),
        lit("${B}West Bank$E", "Cisjordânia"),
        lit("${B}Golan Heights$E", "Colinas de Golã"),
        lit("${B}Strait of Hormuz$E", "Estreito de Ormuz"),
        // Termos militares ambíguos.
        word("${B}air ?strikes$E", "air attacks"),
        word("${B}air ?strike$E", "air attack"),
        word("(?<!hunger |general |labor |labour |workers' |workers’ )${B}strikes$E", "attacks"),
        word("(?<!hunger |general |labor |labour |workers' |workers’ )${B}strike$E", "attack"),
        word("${B}striking$E", "attacking"),
        word("${B}struck$E", "hit"),
        word("${B}shelling$E", "artillery fire"),
        word("${B}shelled$E", "bombarded"),
        word("(?<!artillery |tank )${B}shells$E", "artillery shells"),
        word("${B}barrages$E", "volleys"),
        word("${B}barrage$E", "volley"),
        word("(?<!aircraft |air )${B}carrier$E", "aircraft carrier"),
        word("(?<!air )${B}raids$E", "incursions"),
        word("(?<!air )${B}raid$E", "incursion"),
        word("${B}rounds of fire$E", "shots"),
        word("${B}gunmen$E", "armed men"),
        word("${B}ordnance$E", "munitions"),
    )

    // ------------------------------------------------------------------ português (depois)

    private val POST: List<Rule> = listOf(
        // Nomes que ficaram em inglês (primeiro os compostos).
        lit("${B}United States$E", "Estados Unidos"),
        lit("${B}United Kingdom$E", "Reino Unido"),
        lit("${B}United Nations$E", "Nações Unidas"),
        lit("${B}Jordan River|River Jordan$E", "rio Jordão"),
        lit("${B}Jordan Valley$E", "Vale do Jordão"),
        lit("${B}Saudi Arabia$E", "Arábia Saudita"),
        lit("${B}North Korea$E", "Coreia do Norte"),
        lit("${B}South Korea$E", "Coreia do Sul"),
        lit("${B}South Sudan$E", "Sudão do Sul"),
        lit("${B}Red Sea$E", "Mar Vermelho"),
        lit("${B}Black Sea$E", "Mar Negro"),
        lit("${B}Persian Gulf$E", "Golfo Pérsico"),
        lit("${B}Gulf of Aden$E", "Golfo de Áden"),
        lit("${B}Middle East$E", "Oriente Médio"),
        lit("${B}White House$E", "Casa Branca"),
        lit("${B}Gaza Strip$E", "Faixa de Gaza"),
        *listOf(
            "Lebanon" to "Líbano", "Syria" to "Síria", "Iraq" to "Iraque", "Yemen" to "Iêmen",
            "Jordan" to "Jordânia", "Egypt" to "Egito", "Qatar" to "Catar", "Russia" to "Rússia",
            "Ukraine" to "Ucrânia", "Pakistan" to "Paquistão", "Afghanistan" to "Afeganistão", "India" to "Índia",
            "Sudan" to "Sudão", "Libya" to "Líbia", "Somalia" to "Somália", "Ethiopia" to "Etiópia",
            "Eritrea" to "Eritreia", "Nigeria" to "Nigéria", "Azerbaijan" to "Azerbaijão", "Armenia" to "Armênia",
            "Bahrain" to "Bahrein", "Oman" to "Omã", "Cyprus" to "Chipre", "Germany" to "Alemanha",
            "France" to "França", "Britain" to "Grã-Bretanha", "Poland" to "Polônia", "Greece" to "Grécia",
            "Türkiye" to "Turquia", "Turkiye" to "Turquia", "Tehran" to "Teerã", "Beirut" to "Beirute",
            "Damascus" to "Damasco", "Baghdad" to "Bagdá", "Riyadh" to "Riad", "Moscow" to "Moscou",
            "Jerusalem" to "Jerusalém", "Aleppo" to "Alepo", "Hodeidah" to "Hodeida", "Tyre" to "Tiro",
            "Sidon" to "Sídon", "Odesa" to "Odessa", "Pentagon" to "Pentágono", "Kyiv" to "Kiev",
        ).map { (en, pt) -> lit("$B$en$E", pt) }.toTypedArray(),

        // Português de Portugal -> do Brasil.
        lit("${B}Irão$E", "Irã"),
        lit("${B}Teerão$E", "Teerã"),
        lit("${B}Iémen$E", "Iêmen"),
        lit("${B}Moscovo$E", "Moscou"),
        lit("${B}Bagdade$E", "Bagdá"),
        lit("${B}Médio Oriente$E", "Oriente Médio"),
        lit("${B}Próximo Oriente$E", "Oriente Médio"),
        lit("${B}Polónia$E", "Polônia"),
        lit("${B}Arménia$E", "Armênia"),
        lit("${B}Quénia$E", "Quênia"),
        lit("${B}Vietname$E", "Vietnã"),
        lit("${B}Estónia$E", "Estônia"),
        lit("${B}Letónia$E", "Letônia"),
        lit("${B}Bielorrússia$E", "Belarus"),
        lit("${B}Hezbolá$E", "Hezbollah"),
        lit("${B}Hizb(?:ollah|ullah)$E", "Hezbollah"),
        lit("${B}Hamás$E", "Hamas"),
        word("${B}palestinian(o|a|os|as)$E", "palestin$1"),
        word("${B}israelitas$E", "israelenses"),
        word("${B}israelita$E", "israelense"),
        word("${B}equipa$E", "equipe"),
        word("${B}equipas$E", "equipes"),
        word("${B}facto$E", "fato"),
        word("${B}factos$E", "fatos"),
        word("${B}contacto$E", "contato"),
        word("${B}registo$E", "registro"),
        word("${B}autocarro$E", "ônibus"),
        word("${B}telemóve(l|is)$E", "celular"),
        word("${B}ecrã$E", "tela"),
        word("${B}utilizador(es)?$E", "usuário$1"),
        word("${B}actual$E", "atual"),
        word("${B}actualmente$E", "atualmente"),
        word("${B}acção$E", "ação"),
        word("${B}acções$E", "ações"),
        word("${B}direcção$E", "direção"),
        word("${B}objectivo(s)?$E", "objetivo$1"),
        // "está a atacar" (Portugal) -> "está atacando".
        Rule(Regex("${B}(est(?:á|ão|ava|avam|iveram|ive|eve)) a (\\p{L}+?)(ar|er|ir)$E", RegexOption.IGNORE_CASE)) { m ->
            val (aux, stem, end) = m.destructured
            "$aux $stem" + when (end) { "ar" -> "ando"; "er" -> "endo"; else -> "indo" }
        },

        // Termos militares mal traduzidos.
        word("${B}greves aéreas$E", "ataques aéreos"),
        word("${B}greve aérea$E", "ataque aéreo"),
        word("${B}greves(?! de fome| gerais)$E", "ataques"),
        word("${B}greve(?! de fome| geral)$E", "ataque"),
        word("${B}bombardeamentos$E", "bombardeios"),
        word("${B}bombardeamento$E", "bombardeio"),
        word("${B}descascamento$E", "bombardeio de artilharia"),
        word("${B}conchas de artilharia$E", "projéteis de artilharia"),
        word("${B}conchas$E", "projéteis"),
        word("${B}barragem de (mísseis|foguetes|drones|ataques)$E", "saraivada de $1"),
        word("${B}(?:transportadora(?:s)? de aeronaves|transportador(?:es)? de aeronaves|porta-aeronaves)$E", "porta-aviões"),
        lit("${B}Força(?:s)? de Defesa (?:de Israel|Israelense(?:s)?|Israelita(?:s)?)$E", "Forças de Defesa de Israel"),
        lit("${B}IDF$E", "FDI"),
        lit("${B}NATO$E", "OTAN"),
        lit("${B}Organização Mundial de Saúde$E", "Organização Mundial da Saúde"),
        lit("${B}Estado Islâmico do Iraque e (?:do )?Levante$E", "Estado Islâmico"),

        // Concordância com "Estados Unidos" (plural).
        lit("${B}([Oo]) Estados Unidos$E", "$1s Estados Unidos"),
        lit("${B}([Nn])o Estados Unidos$E", "$1os Estados Unidos"),
        lit("${B}([Dd])o Estados Unidos$E", "$1os Estados Unidos"),
        lit("${B}([Aa])o Estados Unidos$E", "$1os Estados Unidos"),
        lit("${B}([Pp])elo Estados Unidos$E", "$1elos Estados Unidos"),
        lit("${B}([Uu])m Estados Unidos$E", "$1ns Estados Unidos"),
        lit("${B}por Estados Unidos$E", "pelos Estados Unidos"),
        lit("${B}de Estados Unidos$E", "dos Estados Unidos"),
        lit("${B}em Estados Unidos$E", "nos Estados Unidos"),
        // Espaços duplicados que as trocas possam deixar.
        lit(" {2,}", " "),
    )

    fun preprocess(english: String): String = apply(english, PRE)

    fun postprocess(portuguese: String): String = apply(portuguese, POST).trim()
}
