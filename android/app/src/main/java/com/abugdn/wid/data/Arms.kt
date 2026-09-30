package com.abugdn.wid.data

/**
 * "Quem arma quem": comércio de armas pesadas do SIPRI (Stockholm International Peace Research Institute),
 * período 2020–2024, fact sheet de março de 2025. Percentuais são a parte de cada país no total mundial
 * (ou no total que um país comprou), medidos pelo "valor indicador de tendência" do SIPRI, não em dólares.
 * Texto fixo: ao atualizar, troque o período e mantenha o tom neutro.
 */
data class ArmsShare(val country: String, val share: Double, val flag: String = "")

data class ArmsBuyer(val country: String, val flag: String, val tag: String?, val suppliers: List<ArmsShare>, val note: String)

const val ARMS_PERIOD = "2020–2024"

val ARMS_EXPORTERS = listOf(
    ArmsShare("EUA", 43.0, "🇺🇸"),
    ArmsShare("França", 9.6, "🇫🇷"),
    ArmsShare("Rússia", 7.8, "🇷🇺"),
    ArmsShare("China", 5.9, "🇨🇳"),
    ArmsShare("Alemanha", 5.6, "🇩🇪"),
    ArmsShare("Itália", 4.8, "🇮🇹"),
    ArmsShare("Reino Unido", 3.6, "🇬🇧"),
    ArmsShare("Israel", 3.1, "🇮🇱"),
    ArmsShare("Espanha", 3.0, "🇪🇸"),
    ArmsShare("Coreia do Sul", 2.2, "🇰🇷"),
)

val ARMS_IMPORTERS = listOf(
    ArmsShare("Ucrânia", 8.8, "🇺🇦"),
    ArmsShare("Índia", 8.3, "🇮🇳"),
    ArmsShare("Catar", 6.8, "🇶🇦"),
    ArmsShare("Arábia Saudita", 6.8, "🇸🇦"),
    ArmsShare("Paquistão", 4.6, "🇵🇰"),
    ArmsShare("Japão", 3.9, "🇯🇵"),
    ArmsShare("Austrália", 3.5, "🇦🇺"),
    ArmsShare("Egito", 3.3, "🇪🇬"),
    ArmsShare("EUA", 3.1, "🇺🇸"),
    ArmsShare("Kuwait", 2.9, "🇰🇼"),
)

val ARMS_BUYERS = listOf(
    ArmsBuyer(
        "Ucrânia", "🇺🇦", "ucrania_russia",
        listOf(ArmsShare("EUA", 45.0, "🇺🇸"), ArmsShare("Alemanha", 12.0, "🇩🇪"), ArmsShare("Polônia", 11.0, "🇵🇱")),
        "Maior importador do mundo no período: quase tudo doado por ao menos 35 países depois da invasão russa de 2022.",
    ),
    ArmsBuyer(
        "Israel", "🇮🇱", "israel",
        listOf(ArmsShare("EUA", 66.0, "🇺🇸"), ArmsShare("Alemanha", 33.0, "🇩🇪")),
        "Recebe caças, bombas guiadas e munição dos EUA e submarinos e navios da Alemanha. Também é grande exportador.",
    ),
    ArmsBuyer(
        "Arábia Saudita", "🇸🇦", "golfo",
        listOf(ArmsShare("EUA", 74.0, "🇺🇸")),
        "Um dos maiores compradores do mundo; armas usadas na guerra do Iêmen foram alvo de embargos parciais no Ocidente.",
    ),
    ArmsBuyer(
        "Índia", "🇮🇳", "asia",
        listOf(ArmsShare("Rússia", 36.0, "🇷🇺"), ArmsShare("França", 33.0, "🇫🇷"), ArmsShare("Israel", 13.0, "🇮🇱")),
        "Ainda depende da Rússia, mas a fatia russa caiu e a da França e dos EUA subiu.",
    ),
    ArmsBuyer(
        "Paquistão", "🇵🇰", "asia",
        listOf(ArmsShare("China", 81.0, "🇨🇳")),
        "Rival da Índia; quase todas as armas importadas vêm da China.",
    ),
)

/** Fornecimentos relatados em fontes abertas que não entram (ou entram pouco) nos números do SIPRI. */
val ARMS_REPORTED = listOf(
    "🇮🇷 → 🇷🇺 Drones Shahed e mísseis de curto alcance para a Rússia; a Rússia passou a fabricar os drones em casa.",
    "🇰🇵 → 🇷🇺 Milhões de projéteis de artilharia e mísseis balísticos da Coreia do Norte, além de soldados enviados a Kursk (2024).",
    "🇮🇷 → Houthis, Hezbollah e milícias no Iraque: foguetes, mísseis e drones, apesar de embargos da ONU.",
    "🇨🇳 → 🇷🇺 Componentes de uso duplo (eletrônicos, máquinas e peças de drones), segundo EUA e UE; a China nega vender armas.",
    "🇺🇸 → 🇮🇱 Pacotes de ajuda militar aprovados pelo Congresso desde 2023, além dos US\$ 3,8 bilhões anuais já previstos.",
)
