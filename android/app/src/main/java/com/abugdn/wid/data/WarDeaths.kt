package com.abugdn.wid.data

// GERADO por backend/tools/gerar_dados_fixos.py a partir de https://ucdp.uu.se/downloads/brd/ucdp-brd-conf-261-csv.zip
// (sha256 do arquivo usado: a88c81fb727f10a689ff0084bacf78e6fbfaadbbe925a608f6e7be2f44a6141e)
// Não edite à mão: rode o gerador de novo.

/** Mortes em combate (estimativa "best" do UCDP) de cada guerra em [WAR_DEATHS_YEARS]. */
data class WarDeaths(val id: String, val name: String, val tag: String?, val deaths: List<Int>) {
    val latest: Int get() = deaths.lastOrNull() ?: 0
}

const val UCDP_VERSION = "26.1"
val WAR_DEATHS_YEARS = listOf(2023, 2024, 2025)

val WAR_DEATHS = listOf(
    WarDeaths("ucrania", "Ucrânia", "ucrania_russia", listOf(76254, 102120, 94741)),
    WarDeaths("gaza", "Gaza", "gaza", listOf(26789, 21595, 14434)),
    WarDeaths("sudao", "Sudão", "sudao", listOf(11760, 4684, 12269)),
    WarDeaths("congo", "RD Congo", "africa", listOf(550, 1059, 4464)),
    WarDeaths("etiopia", "Etiópia", "africa", listOf(1719, 2876, 4312)),
    WarDeaths("somalia", "Somália", "somalia", listOf(3859, 2191, 3095)),
    WarDeaths("sahel", "Sahel (Burkina Faso, Mali, Níger)", "africa", listOf(6339, 4135, 5090)),
    WarDeaths("paquistao", "Paquistão", "asia", listOf(1068, 1623, 3091)),
    WarDeaths("nigeria", "Nigéria", "africa", listOf(2125, 1579, 1905)),
    WarDeaths("mianmar", "Mianmar", "asia", listOf(2453, 2334, 2083)),
    WarDeaths("ira_israel", "Irã × Israel", "ira", listOf(160, 549, 1222)),
    WarDeaths("haiti", "Haiti", null, listOf(0, 217, 1211)),
    WarDeaths("iemen", "Iêmen", "iemen", listOf(808, 909, 1414)),
)
