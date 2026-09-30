package com.abugdn.wid.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

// Seções do radar.json com dados do mundo fora da imprensa: deslocados (ACNUR), fome (IPC/HAPI),
// gás na Europa (AGSI+/ENTSOG), jornalistas mortos (CPJ) e a lista de sanções (OpenSanctions).

@Serializable
data class RefugeesSection(val updated: String = "", val countries: List<RefugeeCountry> = emptyList())

@Serializable
data class RefugeeCountry(
    val iso: String = "",
    val name: String = "",
    val tag: String? = null,
    val years: List<RefugeeYear> = emptyList(),
    val hosts: List<RefugeeHost> = emptyList(),
) {
    val latest: RefugeeYear? get() = years.lastOrNull()
    /** Refugiados + solicitantes de refúgio fora do país. */
    fun abroad(y: RefugeeYear) = y.refugees + y.asylum
}

@Serializable
data class RefugeeYear(val year: Int = 0, val refugees: Long = 0, val asylum: Long = 0, val idps: Long = 0)

@Serializable
data class RefugeeHost(val iso: String = "", val name: String = "", val year: Int = 0, val people: Long = 0)

@Serializable
data class HungerSection(val updated: String = "", val countries: List<HungerCountry> = emptyList())

@Serializable
data class HungerCountry(
    val iso: String = "",
    val name: String = "",
    val tag: String? = null,
    val ipc: IpcSummary? = null,
    val prices: List<FoodPrice> = emptyList(),
)

@Serializable
data class IpcSummary(
    val start: String = "",
    val end: String = "",
    @SerialName("phase3plus") val phase3plus: Long = 0,
    val phase4: Long = 0,
    val phase5: Long = 0,
    val fraction: Double? = null,
    val prev: IpcPrev? = null,
)

@Serializable
data class IpcPrev(val start: String = "", @SerialName("phase3plus") val phase3plus: Long = 0)

@Serializable
data class FoodPrice(
    val name: String = "",
    val unit: String = "",
    val from: String = "",
    val to: String = "",
    val price: Double = 0.0,
    val change: Double = 0.0,
)

@Serializable
data class GasSection(val updated: String = "", val storage: GasStorage? = null, val russia: GasFlow? = null)

@Serializable
data class GasStorage(
    val date: String = "",
    val full: Double = 0.0,
    val twh: Double = 0.0,
    val trend: Double = 0.0,
    @SerialName("last_year") val lastYear: Double? = null,
    val series: List<List<JsonElement>> = emptyList(),
)

@Serializable
data class GasFlow(
    val date: String = "",
    val gwh: Double = 0.0,
    val avg30: Double = 0.0,
    val point: String = "",
    val series: List<List<JsonElement>> = emptyList(),
)

@Serializable
data class PressSection(
    val updated: String = "",
    val year: Int = 0,
    @SerialName("killed_this_year") val killedThisYear: Int = 0,
    @SerialName("killed_last_year") val killedLastYear: Int = 0,
    val countries: List<PressCountry> = emptyList(),
    val recent: List<PressVictim> = emptyList(),
)

@Serializable
data class PressCountry(
    val country: String = "",
    val tag: String? = null,
    @SerialName("this_year") val thisYear: Int = 0,
    @SerialName("last_year") val lastYear: Int = 0,
)

@Serializable
data class PressVictim(
    val name: String = "",
    val outlet: String = "",
    val country: String = "",
    val place: String = "",
    val date: String = "",
    val type: String = "",
    val tag: String? = null,
)

@Serializable
data class SanctionListSection(
    val updated: String = "",
    val count: Int = 0,
    val file: String = "sanctions.tsv.gz",
    @SerialName("source_updated") val sourceUpdated: String = "",
    @SerialName("by_authority") val byAuthority: List<SanctionAuthority> = emptyList(),
)

@Serializable
data class SanctionAuthority(val name: String = "", val count: Int = 0)

/** Série [[data, valor], ...] do radar.json como números. */
fun seriesValues(series: List<List<JsonElement>>): List<Double?> =
    series.map { p -> p.getOrNull(1)?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() } }

/** Uma linha de sanctions.tsv.gz: nome, tipo, países, quem sancionou, desde, apelidos. */
data class SanctionEntry(
    val name: String,
    val kind: String,
    val countries: String,
    val authorities: String,
    val since: String,
    val aliases: String,
) {
    companion object {
        fun parse(line: String): SanctionEntry? {
            val f = line.split('\t')
            if (f.isEmpty() || f[0].isBlank()) return null
            fun at(i: Int) = f.getOrNull(i).orEmpty()
            return SanctionEntry(at(0), at(1), at(2), at(3), at(4), at(5))
        }
    }
}

/** Nomes em português dos países mais comuns nos dados do ACNUR e do CPJ. */
val COUNTRY_PT = mapOf(
    "Germany" to "Alemanha", "Poland" to "Polônia", "Czechia" to "Tchéquia", "United Kingdom of Great Britain and Northern Ireland" to "Reino Unido",
    "United States of America" to "EUA", "Russian Federation" to "Rússia", "Türkiye" to "Turquia", "Lebanon" to "Líbano",
    "Jordan" to "Jordânia", "Iraq" to "Iraque", "Egypt" to "Egito", "Chad" to "Chade", "South Sudan" to "Sudão do Sul",
    "Ethiopia" to "Etiópia", "Kenya" to "Quênia", "Uganda" to "Uganda", "Iran (Islamic Rep. of)" to "Irã", "Pakistan" to "Paquistão",
    "Libya" to "Líbia", "Central African Rep." to "Rep. Centro-Africana", "Sweden" to "Suécia", "Austria" to "Áustria",
    "Netherlands (Kingdom of the)" to "Países Baixos", "France" to "França", "Italy" to "Itália", "Spain" to "Espanha",
    "Canada" to "Canadá", "Rep. of Moldova" to "Moldávia", "Romania" to "Romênia", "Slovakia" to "Eslováquia", "Bulgaria" to "Bulgária",
    "Djibouti" to "Djibuti", "Saudi Arabia" to "Arábia Saudita", "Yemen" to "Iêmen", "Somalia" to "Somália", "Syrian Arab Rep." to "Síria",
    "Afghanistan" to "Afeganistão", "Tajikistan" to "Tajiquistão", "India" to "Índia", "Switzerland" to "Suíça", "Belgium" to "Bélgica",
    "Norway" to "Noruega", "Denmark" to "Dinamarca", "Ireland" to "Irlanda", "Lithuania" to "Lituânia", "Latvia" to "Letônia",
    "Estonia" to "Estônia", "Brazil" to "Brasil", "Greece" to "Grécia", "Cyprus" to "Chipre", "Armenia" to "Armênia", "Georgia" to "Geórgia",
    "Israel and the Occupied Palestinian Territory" to "Israel e Territórios Palestinos", "Ukraine" to "Ucrânia", "Russia" to "Rússia",
    "Sudan" to "Sudão", "Syria" to "Síria", "Mexico" to "México", "Philippines" to "Filipinas", "Bangladesh" to "Bangladesh",
    "Haiti" to "Haiti", "Honduras" to "Honduras", "Colombia" to "Colômbia", "Democratic Republic of the Congo" to "RD Congo",
    "Myanmar" to "Mianmar", "Iran" to "Irã",
)

fun countryPt(name: String) = COUNTRY_PT[name] ?: name
