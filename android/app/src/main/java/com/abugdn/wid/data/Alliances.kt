package com.abugdn.wid.data

/**
 * Quem apoia quem: rede simplificada de países e grupos armados. Texto fixo até 2025, escrito à mão
 * e em tom neutro; alegações contestadas dizem quem acusa e quem nega.
 */
data class AllianceNode(val key: String, val name: String, val icon: String, val tag: String? = null)

/** type: "aliado", "apoio" (armas/dinheiro), "mediador", "rival" ou "guerra". */
data class AllianceEdge(val from: String, val to: String, val type: String, val note: String)

val ALLIANCE_TYPES = linkedMapOf(
    "guerra" to "em guerra ou trocando ataques",
    "rival" to "rivais",
    "apoio" to "apoia com armas ou dinheiro",
    "aliado" to "aliados",
    "mediador" to "mediador",
)

val ALLIANCE_NODES = listOf(
    AllianceNode("ira", "Irã", "🇮🇷", "ira"),
    AllianceNode("hezbollah", "Hezbollah", "🟨", "libano"),
    AllianceNode("houthis", "Houthis", "🟩", "iemen"),
    AllianceNode("hamas", "Hamas", "🟢", "gaza"),
    AllianceNode("milicias", "Milícias do Iraque", "⬛", "iraque"),
    AllianceNode("israel", "Israel", "🇮🇱", "israel"),
    AllianceNode("eua", "EUA", "🇺🇸", "eua"),
    AllianceNode("otan", "OTAN e Europa", "🇪🇺", "otan"),
    AllianceNode("ucrania", "Ucrânia", "🇺🇦", "ucrania_russia"),
    AllianceNode("russia", "Rússia", "🇷🇺", "ucrania_russia"),
    AllianceNode("belarus", "Belarus", "🇧🇾", "ucrania_russia"),
    AllianceNode("coreia", "Coreia do Norte", "🇰🇵", "asia"),
    AllianceNode("china", "China", "🇨🇳", "asia"),
    AllianceNode("arabia", "Arábia Saudita", "🇸🇦", "arabia"),
    AllianceNode("emirados", "Emirados", "🇦🇪", "emirados"),
    AllianceNode("catar", "Catar", "🇶🇦", "golfo"),
    AllianceNode("egito", "Egito", "🇪🇬", "egito"),
    AllianceNode("turquia", "Turquia", "🇹🇷", "turquia"),
    AllianceNode("siria", "Síria (novo governo)", "🇸🇾", "siria"),
    AllianceNode("saf", "Exército do Sudão", "🇸🇩", "sudao"),
    AllianceNode("rsf", "RSF (Sudão)", "🟫", "sudao"),
)

val ALLIANCE_EDGES = listOf(
    // Irã e aliados
    AllianceEdge("ira", "hezbollah", "apoio", "Armas, dinheiro e treinamento desde os anos 1980."),
    AllianceEdge("ira", "houthis", "apoio", "Mísseis, drones e peças, segundo a ONU e os EUA; o Irã nega mandar armas."),
    AllianceEdge("ira", "hamas", "apoio", "Dinheiro e armas ao longo dos anos; o Irã diz não ter sabido antes do 7 de outubro."),
    AllianceEdge("ira", "milicias", "apoio", "Financia e arma grupos como o Kataib Hezbollah."),
    AllianceEdge("ira", "russia", "apoio", "Drones Shahed e, segundo os EUA, mísseis para a guerra na Ucrânia; parceria assinada em 2025."),
    AllianceEdge("china", "ira", "apoio", "Maior compradora do petróleo iraniano, apesar das sanções dos EUA."),
    // Israel
    AllianceEdge("eua", "israel", "apoio", "Cerca de US$ 3,8 bilhões por ano em ajuda militar, além de defesa antimísseis."),
    AllianceEdge("israel", "hamas", "guerra", "Guerra em Gaza desde 7 de outubro de 2023."),
    AllianceEdge("israel", "hezbollah", "guerra", "Confronto de 2023 a 2024; cessar-fogo em novembro de 2024."),
    AllianceEdge("israel", "houthis", "guerra", "Troca de ataques com mísseis e drones desde 2023."),
    AllianceEdge("israel", "ira", "guerra", "Ataques diretos em 2024 e guerra de 12 dias em junho de 2025."),
    AllianceEdge("israel", "siria", "rival", "Ataques em território sírio e ocupação da zona-tampão no Golã em dezembro de 2024."),
    AllianceEdge("emirados", "israel", "aliado", "Relações normalizadas pelos Acordos de Abraão (2020)."),
    AllianceEdge("egito", "israel", "aliado", "Paz desde 1979; cooperação na fronteira de Gaza, com atritos na guerra."),
    // EUA
    AllianceEdge("eua", "ira", "rival", "Sanções desde 1979; bombardeio de instalações nucleares em junho de 2025."),
    AllianceEdge("eua", "houthis", "rival", "Bombardeios em 2024 e 2025; trégua sobre navios americanos em maio de 2025."),
    AllianceEdge("eua", "catar", "aliado", "Base aérea de Al Udeid, a maior dos EUA na região."),
    AllianceEdge("eua", "arabia", "aliado", "Grande comprador de armas americanas; parceria de segurança antiga."),
    // Golfo e mediação
    AllianceEdge("arabia", "houthis", "rival", "Liderou a coalizão contra os Houthis a partir de 2015; trégua de fato desde 2022."),
    AllianceEdge("arabia", "ira", "rival", "Rivais regionais; reataram relações em 2023, com mediação da China."),
    AllianceEdge("catar", "hamas", "mediador", "Abrigou o escritório político do Hamas; mediador com Egito e EUA."),
    AllianceEdge("egito", "hamas", "mediador", "Mediador nas tréguas e trocas de reféns; controla a passagem de Rafah."),
    // Ucrânia e Rússia
    AllianceEdge("russia", "ucrania", "guerra", "Invasão em grande escala desde 24 de fevereiro de 2022."),
    AllianceEdge("eua", "ucrania", "apoio", "Armas, inteligência e dinheiro desde 2022."),
    AllianceEdge("otan", "ucrania", "apoio", "Armas, dinheiro e sanções contra a Rússia; a Ucrânia não é membro."),
    AllianceEdge("otan", "russia", "rival", "Sanções, reforço militar no leste da Europa e acusações de sabotagem."),
    AllianceEdge("coreia", "russia", "apoio", "Soldados, munição e mísseis enviados em 2024 e 2025."),
    AllianceEdge("china", "russia", "apoio", "Compra petróleo e vende peças e eletrônicos de uso duplo; nega mandar armas."),
    AllianceEdge("belarus", "russia", "aliado", "Território usado na invasão de 2022; recebeu armas nucleares táticas russas."),
    AllianceEdge("turquia", "otan", "aliado", "Membro da OTAN, com o segundo maior exército da aliança."),
    AllianceEdge("turquia", "ucrania", "mediador", "Mediou o acordo de grãos (2022) e trocas de prisioneiros; vende drones à Ucrânia."),
    AllianceEdge("turquia", "siria", "apoio", "Apoia o novo governo sírio desde a queda de Assad (2024)."),
    // Sudão
    AllianceEdge("saf", "rsf", "guerra", "Guerra civil desde abril de 2023."),
    AllianceEdge("emirados", "rsf", "apoio", "Acusados por especialistas da ONU de armar as RSF; os Emirados negam."),
    AllianceEdge("egito", "saf", "apoio", "Apoia o exército sudanês."),
)
