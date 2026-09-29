package com.abugdn.wid.data

/**
 * Curso rápido: lições curtas para quem está chegando agora. Cada lição tem poucas páginas de
 * um parágrafo e termina com as regiões do app para seguir. Textos até 2025, tom neutro.
 */
data class Lesson(val id: String, val icon: String, val title: String, val minutes: Int, val pages: List<String>, val tags: List<String>)

val COURSE = listOf(
    Lesson(
        "israel_palestina", "🕊", "Israel e Palestina em 5 minutos", 5,
        listOf(
            "Judeus e árabes disputam a mesma terra entre o rio Jordão e o Mediterrâneo desde o fim do Império Otomano. Em 1947, " +
                "a ONU propôs dividir o território em dois Estados. Israel declarou independência em 1948; os países árabes vizinhos " +
                "atacaram e perderam. Cerca de 700 mil palestinos fugiram ou foram expulsos: é a Nakba (“catástrofe”), " +
                "e seus descendentes ainda são refugiados.",
            "Na Guerra dos Seis Dias (1967), Israel tomou a Cisjordânia e Jerusalém Oriental (da Jordânia), a Faixa de Gaza e o " +
                "Sinai (do Egito) e as Colinas de Golã (da Síria). O Sinai foi devolvido em troca da paz com o Egito (1979). Os " +
                "assentamentos israelenses na Cisjordânia cresceram desde então e hoje reúnem mais de 500 mil colonos; a maior " +
                "parte dos países os considera ilegais pelo direito internacional.",
            "Os Acordos de Oslo (1993) criaram a Autoridade Palestina, que governa partes da Cisjordânia, com a promessa de " +
                "negociar um Estado palestino. As negociações fracassaram, houve duas intifadas (revoltas) e a confiança dos dois " +
                "lados despencou. Os pontos mais difíceis: fronteiras, Jerusalém, refugiados, assentamentos e segurança.",
            "Israel saiu de Gaza em 2005. Em 2007 o Hamas tomou o controle da faixa, e Israel e Egito impuseram um bloqueio. Seguiram " +
                "guerras em 2008, 2012, 2014 e 2021, até o ataque de 7 de outubro de 2023 (veja a lição sobre o Hamas).",
            "Quando você lê “solução de dois Estados”, é a ideia de um Estado palestino ao lado de Israel, apoiada pela ONU " +
                "e pelo Brasil. No Argos, as regiões Israel, Gaza e Cisjordânia acompanham esse conflito.",
        ),
        listOf("israel", "gaza", "cisjordania"),
    ),
    Lesson(
        "hamas", "⚔", "O Hamas e o 7 de outubro", 4,
        listOf(
            "O Hamas é um movimento islamista palestino criado em 1987, com braço armado (as Brigadas al-Qassam). Não reconhece " +
                "Israel e é classificado como organização terrorista pelos EUA, pela União Europeia e por outros países. Venceu as " +
                "eleições palestinas de 2006 e governa Gaza desde 2007.",
            "Em 7 de outubro de 2023, milhares de combatentes do Hamas e de outros grupos invadiram o sul de Israel. Cerca de 1.200 " +
                "pessoas foram mortas, a maioria civis, e 251 foram levadas como reféns para Gaza. Foi o dia mais letal da história " +
                "de Israel.",
            "Israel respondeu com bombardeios e uma invasão terrestre de Gaza. Segundo o Ministério da Saúde de Gaza (controlado pelo " +
                "Hamas, números usados pela ONU), dezenas de milhares de palestinos morreram; a maior parte da população foi deslocada " +
                "e há crise de fome. Israel diz que o Hamas usa áreas civis e túneis; os números são disputados pelos dois lados.",
            "Houve tréguas com troca de reféns por presos palestinos (novembro de 2023 e início de 2025), mediadas por Catar, Egito e " +
                "EUA. Em outubro de 2025, um cessar-fogo mediado pelos EUA levou à libertação dos reféns vivos. O futuro de Gaza, " +
                "quem a governa e a reconstrução, seguia em aberto.",
        ),
        listOf("gaza", "israel"),
    ),
    Lesson(
        "hezbollah", "🇱🇧", "Hezbollah e Líbano", 3,
        listOf(
            "O Hezbollah (“Partido de Deus”) é um grupo xiita libanês criado em 1982, durante a ocupação israelense do sul do " +
                "Líbano, com apoio do Irã. Tem partido político, deputados, serviços sociais e uma milícia mais forte que o próprio " +
                "exército libanês.",
            "Em 2006, Israel e Hezbollah fizeram uma guerra de 34 dias. A partir de 8 de outubro de 2023, o grupo passou a disparar " +
                "contra o norte de Israel “em solidariedade a Gaza”, e dezenas de milhares de pessoas deixaram suas casas dos " +
                "dois lados da fronteira.",
            "Em setembro de 2024, Israel explodiu pagers e rádios de membros do grupo, matou o líder Hassan Nasrallah e invadiu o sul " +
                "do Líbano. Um cessar-fogo em novembro de 2024 previa a saída do Hezbollah do sul e o envio do exército libanês. O " +
                "desarmamento do grupo virou o grande debate da política libanesa.",
        ),
        listOf("libano", "israel"),
    ),
    Lesson(
        "ira", "🇮🇷", "O Irã e o “eixo da resistência”", 4,
        listOf(
            "Desde a Revolução Islâmica de 1979, o Irã é governado por clérigos xiitas; o Líder Supremo (Ali Khamenei desde 1989) " +
                "tem a palavra final. O regime trata Israel e os EUA como inimigos e financia uma rede de aliados armados: o chamado " +
                "“eixo da resistência”.",
            "O eixo inclui o Hezbollah (Líbano), os Houthis (Iêmen), milícias xiitas no Iraque e o Hamas e a Jihad Islâmica (Gaza). " +
                "O governo de Assad, na Síria, era a ponte terrestre até o Líbano, até cair em dezembro de 2024.",
            "O programa nuclear é o centro da disputa. O acordo de 2015 limitava o enriquecimento de urânio em troca do fim de " +
                "sanções; os EUA saíram dele em 2018 e o Irã passou a enriquecer a níveis próximos do usado em armas. O Irã nega " +
                "querer a bomba.",
            "Em 2024, Irã e Israel trocaram ataques diretos pela primeira vez. Em junho de 2025, Israel bombardeou instalações " +
                "nucleares e militares iranianas durante 12 dias, os EUA atacaram as usinas de Fordow, Natanz e Isfahan, e o Irã " +
                "respondeu com mísseis contra Israel e contra uma base americana no Catar.",
        ),
        listOf("ira", "israel", "eua"),
    ),
    Lesson(
        "houthis", "🚢", "Houthis e o Mar Vermelho", 3,
        listOf(
            "Os Houthis (movimento Ansar Allah) são um grupo armado do norte do Iêmen, de origem xiita zaidita. Em 2014 tomaram a " +
                "capital, Sanaa. Uma coalizão liderada pela Arábia Saudita interveio em 2015, e a guerra gerou uma das piores crises " +
                "humanitárias do mundo.",
            "Desde novembro de 2023, dizendo agir por Gaza, os Houthis atacam navios no Mar Vermelho e no estreito de Bab el-Mandeb " +
                "e lançam mísseis e drones contra Israel. Muitas empresas passaram a contornar a África, e o Canal de Suez perdeu " +
                "boa parte do tráfego.",
            "EUA, Reino Unido e Israel bombardearam alvos houthis várias vezes. Em maio de 2025, os EUA fizeram uma trégua com o " +
                "grupo sobre os ataques a navios americanos; os ataques contra Israel continuaram.",
        ),
        listOf("iemen", "egito"),
    ),
    Lesson(
        "ucrania", "🇺🇦", "Por que a Rússia invadiu a Ucrânia", 5,
        listOf(
            "A Ucrânia se tornou independente com o fim da União Soviética, em 1991. Desde então, oscilou entre governos mais " +
                "próximos da Rússia e da Europa. Em 2014, protestos derrubaram um presidente pró-Rússia; em seguida, a Rússia anexou " +
                "a Crimeia e apoiou separatistas armados no Donbas, no leste.",
            "Em 24 de fevereiro de 2022, a Rússia lançou uma invasão em grande escala. Putin citou a expansão da OTAN, a " +
                "“desnazificação” e a proteção de russófonos; a Ucrânia e o Ocidente chamam de guerra de conquista, contrária " +
                "ao direito internacional.",
            "A ofensiva sobre Kiev fracassou, e a Ucrânia retomou territórios em 2022. Desde então, a guerra virou uma frente longa " +
                "de trincheiras, com uso massivo de drones dos dois lados. A Rússia ocupa cerca de um quinto do território ucraniano.",
            "EUA e Europa enviaram armas e dinheiro à Ucrânia e impuseram sanções à Rússia. A Coreia do Norte mandou soldados e " +
                "munição à Rússia, e o Irã, drones. Os mortos e feridos somam centenas de milhares; os números exatos são segredo " +
                "dos dois governos.",
            "Em 2025, o governo Trump pressionou por um acordo de paz. Os pontos de disputa: quais territórios ficam com quem, " +
                "garantias de segurança para a Ucrânia e a entrada ou não na OTAN. No Argos, acompanhe em Ucrânia/Rússia e na " +
                "Linha de frente do Mapa.",
        ),
        listOf("ucrania_russia", "otan"),
    ),
    Lesson(
        "sudao", "🇸🇩", "A guerra esquecida do Sudão", 3,
        listOf(
            "Em abril de 2023, os dois generais que dividiam o poder no Sudão entraram em guerra: o exército (SAF), de Abdel Fattah " +
                "al-Burhan, e as Forças de Apoio Rápido (RSF), paramilitares de Mohamed Hamdan Dagalo, o “Hemedti”.",
            "A guerra destruiu boa parte da capital, Cartum, e voltou a Darfur, onde as RSF são acusadas de massacres étnicos. Mais " +
                "de 12 milhões de pessoas deixaram suas casas: a maior crise de deslocados do mundo, com fome declarada em várias áreas.",
            "Países de fora apoiam lados diferentes, segundo investigações da ONU e da imprensa (Emirados, Egito, Irã, Rússia e " +
                "outros são citados). Em 2025, o exército retomou Cartum e as RSF avançaram em Darfur, com a queda de El Fasher.",
        ),
        listOf("sudao", "emirados"),
    ),
    Lesson(
        "argos", "👁", "Como ler o Argos", 3,
        listOf(
            "O Argos junta mais de 30 veículos de vários lados (Israel, mundo árabe, EUA, Europa, Brasil) e agrupa as notícias do " +
                "mesmo fato. Quanto mais fontes contam a mesma história, mais alto ela aparece.",
            "A tensão de cada região (0 a 100) mede o volume e a gravidade das notícias em relação ao normal daquela região, somada " +
                "a sensores como apagões de internet e espaço aéreo fechado. O Relógio do Argos resume as regiões mais tensas num " +
                "número só. É um termômetro do noticiário, não uma previsão.",
            "Na notícia, a aba Cobertura mostra como cada lado contou a história, os números citados (e quando divergem) e as " +
                "manchetes que foram alteradas depois de publicadas. Desconfie de número de uma fonte só.",
        ),
        emptyList(),
    ),
)
