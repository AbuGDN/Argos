package com.abugdn.wid.data

/** Um cargo e quem o ocupa. [person] liga à ficha em PEOPLE (Context.kt), quando existe. */
data class PowerRole(val role: String, val name: String, val person: String? = null)

/** Quem manda em cada lado: governo, militares e grupos armados. Conteúdo fixo, até 2025. */
data class PowerSide(val title: String, val tag: String, val roles: List<PowerRole>)

const val POWER_DISCLAIMER =
    "Cargos até o fim de 2025. Mudanças posteriores podem não aparecer aqui; confira nas notícias."

val POWER = listOf(
    PowerSide(
        "Israel", "israel",
        listOf(
            PowerRole("Primeiro-ministro", "Benjamin Netanyahu", "netanyahu"),
            PowerRole("Presidente (cargo cerimonial)", "Isaac Herzog"),
            PowerRole("Ministro da Defesa", "Israel Katz"),
            PowerRole("Ministro das Relações Exteriores", "Gideon Sa'ar"),
            PowerRole("Chefe do Estado-Maior das FDI", "Eyal Zamir"),
            PowerRole("Diretor do Mossad", "David Barnea"),
            PowerRole("Diretor do Shin Bet", "David Zini"),
        ),
    ),
    PowerSide(
        "Hamas", "gaza",
        listOf(
            PowerRole("Chefe em Gaza e negociador (no exterior)", "Khalil al-Hayya"),
            PowerRole("Comandante militar em Gaza (Brigadas Qassam)", "Izz al-Din al-Haddad"),
            PowerRole("Chefe do Conselho da Shura", "Mohammed Darwish"),
            PowerRole("Ex-líderes mortos", "Yahya Sinwar (2024), Ismail Haniyeh (2024)", "sinwar"),
        ),
    ),
    PowerSide(
        "Autoridade Palestina (Cisjordânia)", "cisjordania",
        listOf(
            PowerRole("Presidente", "Mahmoud Abbas", "abbas"),
            PowerRole("Vice-presidente", "Hussein al-Sheikh"),
            PowerRole("Primeiro-ministro", "Mohammad Mustafa"),
        ),
    ),
    PowerSide(
        "Líbano e Hezbollah", "libano",
        listOf(
            PowerRole("Secretário-geral do Hezbollah", "Naim Qassem", "qassem"),
            PowerRole("Presidente do Líbano", "Joseph Aoun"),
            PowerRole("Primeiro-ministro do Líbano", "Nawaf Salam"),
            PowerRole("Presidente do Parlamento (aliado do Hezbollah)", "Nabih Berri"),
        ),
    ),
    PowerSide(
        "Irã", "ira",
        listOf(
            PowerRole("Líder supremo (palavra final)", "Ali Khamenei", "khamenei"),
            PowerRole("Presidente", "Masoud Pezeshkian", "pezeshkian"),
            PowerRole("Chanceler", "Abbas Araghchi"),
            PowerRole("Secretário do Conselho Supremo de Segurança Nacional", "Ali Larijani"),
            PowerRole("Comandante da Guarda Revolucionária", "Mohammad Pakpour"),
            PowerRole("Chefe do Estado-Maior das Forças Armadas", "Abdolrahim Mousavi"),
        ),
    ),
    PowerSide(
        "Houthis (Iêmen)", "iemen",
        listOf(
            PowerRole("Líder do movimento", "Abdul-Malik al-Houthi"),
            PowerRole("Presidente do Conselho Político Supremo", "Mahdi al-Mashat"),
            PowerRole("Governo reconhecido pela ONU (Áden)", "Conselho de Liderança Presidencial, chefiado por Rashad al-Alimi"),
        ),
    ),
    PowerSide(
        "Síria", "siria",
        listOf(
            PowerRole("Presidente (transição)", "Ahmed al-Sharaa", "sharaa"),
            PowerRole("Chanceler", "Asaad al-Shaibani"),
        ),
    ),
    PowerSide(
        "Estados Unidos", "eua",
        listOf(
            PowerRole("Presidente", "Donald Trump", "trump"),
            PowerRole("Vice-presidente", "JD Vance"),
            PowerRole("Secretário de Estado", "Marco Rubio"),
            PowerRole("Secretário de Defesa", "Pete Hegseth"),
            PowerRole("Enviado especial para negociações", "Steve Witkoff"),
            PowerRole("Chefe do Estado-Maior Conjunto", "Dan Caine"),
            PowerRole("Comandante do CENTCOM (Oriente Médio)", "Brad Cooper"),
        ),
    ),
    PowerSide(
        "Rússia", "ucrania_russia",
        listOf(
            PowerRole("Presidente", "Vladimir Putin", "putin"),
            PowerRole("Chanceler", "Sergei Lavrov"),
            PowerRole("Ministro da Defesa", "Andrei Belousov"),
            PowerRole("Chefe do Estado-Maior", "Valery Gerasimov"),
        ),
    ),
    PowerSide(
        "Ucrânia", "ucrania_russia",
        listOf(
            PowerRole("Presidente", "Volodymyr Zelensky", "zelensky"),
            PowerRole("Primeira-ministra", "Yulia Svyrydenko"),
            PowerRole("Ministro da Defesa", "Denys Shmyhal"),
            PowerRole("Comandante-chefe das Forças Armadas", "Oleksandr Syrskyi"),
        ),
    ),
    PowerSide(
        "Sudão", "sudao",
        listOf(
            PowerRole("Chefe do Exército e do Conselho Soberano", "Abdel Fattah al-Burhan"),
            PowerRole("Chefe das Forças de Apoio Rápido (RSF)", "Mohamed Hamdan Dagalo (Hemedti)"),
        ),
    ),
)

/** Reféns do 7 de outubro: linha do tempo com números até o fim de 2025. */
data class HostageStep(val date: String, val text: String)

const val HOSTAGES_TAKEN = 251

val HOSTAGE_TIMELINE = listOf(
    HostageStep("7/10/2023", "251 pessoas são levadas para Gaza no ataque do Hamas, entre vivos e mortos, de mais de 20 nacionalidades."),
    HostageStep("nov/2023", "Primeira trégua: 105 libertados (81 israelenses e com dupla nacionalidade, 23 tailandeses e 1 filipino)."),
    HostageStep("2023–2024", "8 reféns vivos resgatados em operações militares, entre elas a de Nuseirat (junho de 2024, 4 resgatados)."),
    HostageStep("jan–mar/2025", "Segundo cessar-fogo: 33 israelenses (25 vivos e 8 corpos) e 5 tailandeses libertados."),
    HostageStep("mai/2025", "Edan Alexander, israelense-americano, libertado após negociação direta com os EUA."),
    HostageStep("13/10/2025", "Pelo acordo de outubro de 2025, os 20 últimos reféns vivos são libertados; os corpos restantes passam a ser devolvidos aos poucos."),
)

const val HOSTAGES_DISCLAIMER =
    "Números de fontes abertas até o fim de 2025. A devolução dos corpos continuou depois disso; veja as notícias abaixo."

/** Termos para achar notícias sobre reféns no feed (pt/en/he/ar). */
val HOSTAGE_TERMS = listOf("refém", "reféns", "sequestrad", "hostage", "captive", "חטוף", "חטופים", "أسرى", "رهائن")
