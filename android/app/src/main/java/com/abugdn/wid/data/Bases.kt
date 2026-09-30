package com.abugdn.wid.data

/**
 * Bases militares estrangeiras mais citadas nas guerras que o Argos acompanha (fontes abertas, até 2025).
 * Posições aproximadas. Texto fixo: ao atualizar, mantenha o tom neutro e factual.
 */
data class MilitaryBase(
    val name: String,
    val operator: String,
    val host: String,
    val lat: Double,
    val lon: Double,
    val note: String,
)

/** Cor do marcador de cada país que opera a base (vermelho fica só para alertas). */
val BASE_COLORS = mapOf(
    "EUA" to 0xFFC9A227.toInt(),
    "Rússia" to 0xFFE8E2D0.toInt(),
    "China" to 0xFF6E8BA8.toInt(),
    "França" to 0xFF6E8B6A.toInt(),
    "Reino Unido" to 0xFF6E8B6A.toInt(),
    "Turquia" to 0xFFA87B4F.toInt(),
)

val BASE_FLAGS = mapOf("EUA" to "🇺🇸", "Rússia" to "🇷🇺", "China" to "🇨🇳", "França" to "🇫🇷", "Reino Unido" to "🇬🇧", "Turquia" to "🇹🇷")

val BASES = listOf(
    // EUA
    MilitaryBase("Al Udeid", "EUA", "Catar", 25.12, 51.31, "Maior base americana no Oriente Médio; sede avançada do Comando Central (CENTCOM)."),
    MilitaryBase("NSA Bahrein (Manama)", "EUA", "Bahrein", 26.21, 50.61, "Sede da 5ª Frota, que patrulha o Golfo, o Mar Vermelho e o Estreito de Ormuz."),
    MilitaryBase("Camp Arifjan", "EUA", "Kuwait", 28.93, 48.10, "Centro logístico do Exército americano na região."),
    MilitaryBase("Al Dhafra", "EUA", "Emirados Árabes", 24.25, 54.55, "Caças, aviões-radar e drones de vigilância; também usada pela França."),
    MilitaryBase("Muwaffaq Salti", "EUA", "Jordânia", 31.83, 36.78, "Caças americanos que ajudaram a derrubar drones e mísseis iranianos em 2024."),
    MilitaryBase("Incirlik", "EUA", "Turquia", 37.00, 35.43, "Base turca com presença americana e da OTAN, perto da Síria."),
    MilitaryBase("Camp Lemonnier", "EUA", "Djibuti", 11.55, 43.15, "Única base permanente dos EUA na África, na entrada do Mar Vermelho."),
    MilitaryBase("Diego Garcia", "EUA", "Território Britânico do Oceano Índico", -7.31, 72.41, "Bombardeiros de longo alcance; base anglo-americana no meio do Índico."),
    MilitaryBase("Souda Bay", "EUA", "Grécia (Creta)", 35.53, 24.15, "Porto e pista para navios e aviões no Mediterrâneo oriental."),
    MilitaryBase("Rota", "EUA", "Espanha", 36.64, -6.35, "Destróieres com o escudo antimísseis da OTAN."),
    MilitaryBase("Ramstein", "EUA", "Alemanha", 49.44, 7.60, "Maior base aérea americana na Europa; ponto de passagem da ajuda à Ucrânia."),
    MilitaryBase("Yokosuka", "EUA", "Japão", 35.29, 139.67, "Sede da 7ª Frota, a maior frota americana."),
    MilitaryBase("Kadena", "EUA", "Japão (Okinawa)", 26.35, 127.77, "Maior base aérea americana no Pacífico, perto de Taiwan."),
    MilitaryBase("Camp Humphreys", "EUA", "Coreia do Sul", 36.96, 127.03, "Maior base americana no exterior, diante da Coreia do Norte."),
    // Rússia
    MilitaryBase("Sebastopol", "Rússia", "Crimeia (ocupada, Ucrânia)", 44.62, 33.53, "Sede da Frota do Mar Negro, alvo frequente de drones e mísseis ucranianos."),
    MilitaryBase("Tartus", "Rússia", "Síria", 34.91, 35.87, "Único porto russo no Mediterrâneo; futuro incerto desde a queda de Assad (dez/2024)."),
    MilitaryBase("Hmeimim", "Rússia", "Síria", 35.40, 35.95, "Base aérea usada na guerra síria; futuro incerto desde a queda de Assad (dez/2024)."),
    MilitaryBase("Gyumri (102ª base)", "Rússia", "Armênia", 40.79, 43.85, "Base russa no Cáucaso; a Armênia se afastou de Moscou desde 2023."),
    MilitaryBase("Kant", "Rússia", "Quirguistão", 42.85, 74.85, "Base aérea da aliança militar liderada pela Rússia (OTSC)."),
    MilitaryBase("201ª base", "Rússia", "Tajiquistão", 38.56, 68.79, "Maior base terrestre russa no exterior, perto do Afeganistão."),
    MilitaryBase("Tiraspol", "Rússia", "Transnístria (Moldávia)", 46.84, 29.63, "Tropas russas na região separatista da Moldávia, vizinha da Ucrânia."),
    // China
    MilitaryBase("Djibuti (base de apoio)", "China", "Djibuti", 11.59, 43.06, "Primeira base militar chinesa no exterior (2017), na entrada do Mar Vermelho."),
    MilitaryBase("Ream", "China", "Camboja", 10.51, 103.61, "Base naval cambojana reformada com dinheiro chinês; navios chineses atracam lá."),
    // Europa
    MilitaryBase("Djibuti (França)", "França", "Djibuti", 11.53, 43.13, "Maior base francesa no exterior."),
    MilitaryBase("Camp de la Paix", "França", "Emirados Árabes", 24.52, 54.39, "Base naval e aérea francesa em Abu Dhabi."),
    MilitaryBase("Akrotiri", "Reino Unido", "Chipre", 34.59, 32.99, "Base aérea britânica usada contra alvos no Iêmen, na Síria e no Iraque."),
    MilitaryBase("HMS Jufair", "Reino Unido", "Bahrein", 26.20, 50.63, "Base naval britânica no Golfo."),
    // Turquia
    MilitaryBase("Tariq bin Ziyad", "Turquia", "Catar", 25.25, 51.43, "Base turca aberta em 2015; símbolo da aliança Turquia-Catar."),
    MilitaryBase("TURKSOM", "Turquia", "Somália", 2.02, 45.30, "Maior base turca no exterior; treina o exército somali."),
)
