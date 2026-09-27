package com.abugdn.wid.data

/**
 * Fotos e bandeiras. Pessoas, grupos e armas usam a imagem principal do artigo da Wikipédia em
 * inglês (baixada uma vez e guardada); países usam a bandeira do Wikimedia Commons. Nada é
 * hospedado pelo Argos.
 */
val WIKI_TITLES = mapOf(
    // Pessoas
    "netanyahu" to "Benjamin_Netanyahu",
    "khamenei" to "Ali_Khamenei",
    "pezeshkian" to "Masoud_Pezeshkian",
    "trump" to "Donald_Trump",
    "putin" to "Vladimir_Putin",
    "zelensky" to "Volodymyr_Zelenskyy",
    "sinwar" to "Yahya_Sinwar",
    "haniyeh" to "Ismail_Haniyeh",
    "nasrallah" to "Hassan_Nasrallah",
    "qassem" to "Naim_Qassem",
    "abbas" to "Mahmoud_Abbas",
    "sharaa" to "Ahmed_al-Sharaa",
    // Grupos e instituições
    "hamas" to "Hamas",
    "hezbollah" to "Hezbollah",
    "houthis" to "Houthis",
    "idf" to "Israel_Defense_Forces",
    "irgc" to "Islamic_Revolutionary_Guard_Corps",
    "jihad" to "Palestinian_Islamic_Jihad",
    "ap" to "Palestinian_National_Authority",
    "unrwa" to "UNRWA",
    "otan" to "NATO",
    // Armas
    "domo" to "Iron_Dome",
    "funda" to "David's_Sling",
    "arrow" to "Arrow_(missile_family)",
    "thaad" to "Terminal_High_Altitude_Area_Defense",
    "patriot" to "MIM-104_Patriot",
    "shahed" to "HESA_Shahed_136",
    "himars" to "M142_HIMARS",
    "atacms" to "MGM-140_ATACMS",
    "storm" to "Storm_Shadow",
    "kinzhal" to "Kh-47M2_Kinzhal",
    "tomahawk" to "Tomahawk_(missile)",
    "gbu57" to "GBU-57A/B_MOP",
    "f35" to "Lockheed_Martin_F-35_Lightning_II",
    "fattah" to "Fattah_(missile)",
    "kheibar" to "Kheibar_Shekan",
    "fateh110" to "Fateh-110",
    "iskander" to "9K720_Iskander",
    "oreshnik" to "Oreshnik_(missile)",
    "lancet" to "ZALA_Lancet",
    "bayraktar" to "Baykar_Bayraktar_TB2",
    "kornet" to "9M133_Kornet",
)

/** Bandeiras por região (arquivos do Wikimedia Commons). */
val REGION_FLAGS = mapOf(
    "israel" to listOf("Flag_of_Israel.svg"),
    "gaza" to listOf("Flag_of_Palestine.svg"),
    "cisjordania" to listOf("Flag_of_Palestine.svg"),
    "libano" to listOf("Flag_of_Lebanon.svg"),
    "ira" to listOf("Flag_of_Iran.svg"),
    "iemen" to listOf("Flag_of_Yemen.svg"),
    "siria" to listOf("Flag_of_Syria.svg"),
    "iraque" to listOf("Flag_of_Iraq.svg"),
    "eua" to listOf("Flag_of_the_United_States.svg"),
    "ucrania_russia" to listOf("Flag_of_Ukraine.svg", "Flag_of_Russia.svg"),
    "sudao" to listOf("Flag_of_Sudan.svg"),
    "otan" to listOf("Flag_of_NATO.svg"),
)

/** PNG da bandeira na largura pedida (o Commons converte o SVG). */
fun flagUrl(file: String, width: Int = 120): String =
    "https://commons.wikimedia.org/wiki/Special:FilePath/$file?width=$width"

/** Título da Wikipédia para um nome de cargo em "quem manda" (sem parênteses; nada para listas). */
fun wikiTitleForName(name: String, personKey: String?): String? {
    personKey?.let { WIKI_TITLES[it] }?.let { return it }
    val clean = name.substringBefore(" (").trim()
    if (clean.contains(',') || clean.split(' ').size > 5) return null
    return clean.replace(' ', '_')
}
