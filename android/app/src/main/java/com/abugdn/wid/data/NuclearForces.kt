package com.abugdn.wid.data

// GERADO por backend/tools/gerar_dados_fixos.py a partir de https://fas.org/initiative/status-world-nuclear-forces/
// (sha256 do arquivo usado: b86abcce98db604e01e0e314335a387acd3cc3eff6793e2f71e69d5aab334ba4)
// Não edite à mão: rode o gerador de novo.

/** Ogivas por país. [stockpile] = estoque militar (prontas ou guardadas para uso);
 * [total] = estoque + aposentadas à espera de desmonte. null = a FAS não informa. */
data class NuclearForce(
    val country: String,
    val flag: String,
    val deployedStrategic: Int?,
    val deployedNonstrategic: Int?,
    val reserve: Int?,
    val stockpile: Int?,
    val total: Int?,
)

const val NUCLEAR_AS_OF = "início de 2026"

val NUCLEAR_FORCES = listOf(
    NuclearForce("Rússia", "🇷🇺", 1796, 0, 2604, 4400, 5420),
    NuclearForce("EUA", "🇺🇸", 1670, 100, 1930, 3700, 5042),
    NuclearForce("França", "🇫🇷", 280, null, 10, 290, 370),
    NuclearForce("China", "🇨🇳", 34, null, 586, 620, 620),
    NuclearForce("Reino Unido", "🇬🇧", 120, null, 105, 225, 225),
    NuclearForce("Israel", "🇮🇱", 0, null, 90, 90, 90),
    NuclearForce("Paquistão", "🇵🇰", 0, null, 170, 170, 170),
    NuclearForce("Índia", "🇮🇳", 0, null, 178, 190, 190),
    NuclearForce("Coreia do Norte", "🇰🇵", 0, null, 60, 60, 60),
)
