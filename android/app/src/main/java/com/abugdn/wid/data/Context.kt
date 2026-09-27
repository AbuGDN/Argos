package com.abugdn.wid.data

/**
 * Cartões de contexto sobre atores e regiões que aparecem nas notícias.
 * Texto fixo, escrito para dar o pano de fundo; não acompanha os fatos mais recentes.
 */
data class Actor(val key: String, val name: String, val terms: List<String>, val text: String)

const val CONTEXT_DISCLAIMER = "Contexto geral, escrito à mão. Pode não refletir os acontecimentos mais recentes."

val ACTORS = listOf(
    Actor(
        "hamas", "Hamas", listOf("hamas"),
        "Movimento islamista palestino fundado em 1987, durante a Primeira Intifada. Governa a Faixa de Gaza desde 2007, " +
            "depois de vencer as eleições de 2006 e expulsar o Fatah do território. Tem um braço armado (Brigadas al-Qassam) e é " +
            "considerado organização terrorista por Israel, EUA, União Europeia e outros. Comandou o ataque de 7 de outubro de 2023 " +
            "contra Israel, que deu início à guerra em Gaza.",
    ),
    Actor(
        "hezbollah", "Hezbollah", listOf("hezbollah", "hizbollah", "hizbullah", "hezbola"),
        "Partido político e milícia xiita do Líbano, criado nos anos 1980 com apoio do Irã, durante a ocupação israelense do sul " +
            "do país. Tem deputados no Parlamento libanês e um grande arsenal de foguetes e mísseis. Lutou uma guerra contra Israel " +
            "em 2006 e voltou a trocar ataques com Israel a partir de outubro de 2023, com forte escalada em 2024. EUA, Israel e " +
            "outros países o classificam como organização terrorista.",
    ),
    Actor(
        "houthis", "Houthis", listOf("houthi", "huthi", "ansar allah"),
        "Movimento armado xiita zaidita do Iêmen (nome oficial: Ansar Allah). Controla a capital, Sanaa, e boa parte do norte do " +
            "país desde 2014–2015, e enfrentou uma coalizão liderada pela Arábia Saudita na guerra civil. Recebe apoio do Irã. Desde " +
            "o fim de 2023 ataca navios no Mar Vermelho e lança mísseis e drones contra Israel, dizendo agir em solidariedade a Gaza.",
    ),
    Actor(
        "idf", "Forças de Defesa de Israel (FDI/IDF)",
        listOf("idf", "fdi", "forças de defesa de israel", "exército israelense", "exército de israel", "israeli military", "israel defense forces", "israeli army"),
        "As forças armadas de Israel (Tzahal, em hebraico), que reúnem exército, força aérea e marinha. O serviço militar é " +
            "obrigatório para a maioria dos cidadãos judeus e drusos, e centenas de milhares de reservistas são convocados em " +
            "tempos de guerra. \"Exército israelense\", \"FDI\" e \"IDF\" nas notícias se referem a elas.",
    ),
    Actor(
        "irgc", "Guarda Revolucionária do Irã",
        listOf("guarda revolucionária", "revolutionary guard", "irgc", "força quds", "quds force"),
        "Força militar criada após a Revolução Islâmica de 1979 para proteger o regime, separada do exército regular. Controla o " +
            "programa de mísseis e tem grande peso político e econômico no Irã. Seu braço externo, a Força Quds, coordena o apoio " +
            "iraniano a aliados como Hezbollah, Houthis e milícias no Iraque.",
    ),
    Actor(
        "jihad", "Jihad Islâmica Palestina", listOf("jihad islâmica", "islamic jihad"),
        "Grupo armado islamista palestino, menor que o Hamas, atuante em Gaza e na Cisjordânia (com força em Jenin). Recebe apoio " +
            "do Irã, não disputa eleições e costuma combater Israel ao lado do Hamas.",
    ),
    Actor(
        "ap", "Autoridade Palestina", listOf("autoridade palestina", "palestinian authority", "fatah"),
        "Governo palestino criado pelos Acordos de Oslo (1993–1995). Administra partes da Cisjordânia e é dominado pelo Fatah, " +
            "partido de Mahmoud Abbas. Perdeu Gaza para o Hamas em 2007. Coopera com Israel em segurança, o que é criticado por " +
            "muitos palestinos.",
    ),
    Actor(
        "unrwa", "UNRWA", listOf("unrwa"),
        "Agência da ONU para refugiados palestinos, criada em 1949. Mantém escolas, clínicas e ajuda humanitária em Gaza, " +
            "Cisjordânia, Jordânia, Líbano e Síria. Israel acusa funcionários de ligação com o Hamas e aprovou leis restringindo sua " +
            "atuação; a agência e vários países contestam as acusações.",
    ),
    Actor(
        "otan", "OTAN", listOf("otan", "nato"),
        "Aliança militar ocidental criada em 1949, com mais de 30 países, incluindo EUA, Reino Unido, França e Alemanha. O artigo 5 " +
            "diz que um ataque a um membro é um ataque a todos. Apoia a Ucrânia com armas e treinamento, sem combater diretamente.",
    ),
)

val REGION_CONTEXT = mapOf(
    "israel" to "Estado criado em 1948, com cerca de 10 milhões de habitantes. Enfrenta conflitos com palestinos e com grupos " +
        "apoiados pelo Irã em várias frentes (Gaza, Líbano, Iêmen, Síria, Iraque). Benjamin Netanyahu liderou o governo na maior " +
        "parte dos últimos 15 anos.",
    "gaza" to "Faixa costeira de cerca de 365 km² entre Israel e o Egito, com mais de 2 milhões de habitantes, muitos descendentes " +
        "de refugiados de 1948. Israel retirou colonos e tropas em 2005; desde 2007 é governada pelo Hamas, sob bloqueio de Israel " +
        "e do Egito. A guerra iniciada em outubro de 2023 causou destruição e uma crise humanitária enormes.",
    "cisjordania" to "Território entre Israel e a Jordânia, ocupado por Israel desde a Guerra dos Seis Dias (1967). Tem cerca de 3 " +
        "milhões de palestinos e centenas de milhares de colonos israelenses em assentamentos considerados ilegais pela maior parte " +
        "do direito internacional. A Autoridade Palestina administra parte das cidades; operações militares e violência de colonos " +
        "são frequentes.",
    "libano" to "Vizinho ao norte de Israel, com um sistema político dividido entre comunidades religiosas. O Hezbollah domina o sul " +
        "e partes de Beirute; a fronteira com Israel tem a missão de paz da ONU (UNIFIL). Vive grave crise econômica desde 2019.",
    "ira" to "República Islâmica desde 1979, comandada por um líder supremo (o aiatolá Ali Khamenei desde 1989). Rival de Israel, " +
        "EUA e Arábia Saudita, apoia uma rede de aliados conhecida como \"Eixo da Resistência\". Seu programa nuclear é alvo de " +
        "sanções e negociações, e o país trocou ataques diretos com Israel em 2024 e 2025.",
    "iemen" to "O país mais pobre da Península Arábica, em guerra civil desde 2014 entre os Houthis, que controlam o norte, e o " +
        "governo reconhecido internacionalmente, apoiado pela Arábia Saudita. Vive uma das piores crises humanitárias do mundo.",
    "siria" to "Mergulhou em guerra civil a partir de 2011. Em dezembro de 2024, uma ofensiva rebelde liderada pelo grupo HTS " +
        "derrubou Bashar al-Assad, e Ahmed al-Sharaa assumiu um governo de transição. Israel ocupou uma zona-tampão no sul e ataca " +
        "alvos militares no país.",
    "iraque" to "Depois da invasão americana de 2003 e da guerra contra o Estado Islâmico (2014–2017), convive com milícias xiitas " +
        "ligadas ao Irã, algumas das quais atacaram Israel e bases americanas a partir de 2023.",
    "ucrania_russia" to "A Rússia anexou a Crimeia em 2014 e apoiou separatistas no Donbas; em fevereiro de 2022 lançou uma invasão em " +
        "larga escala da Ucrânia. Os combates se concentram no leste e no sul, com uso intenso de drones, mísseis e artilharia. " +
        "A Ucrânia recebe armas e ajuda de países ocidentais.",
    "eua" to "Principal aliado militar de Israel, a quem envia cerca de US$ 3,8 bilhões por ano em ajuda militar, além de " +
        "armas e defesa antimísseis em tempos de guerra. Mantém bases no Golfo (Catar, Bahrein, Emirados, Kuwait), porta-aviões " +
        "na região e o Comando Central (CENTCOM), que coordena as operações no Oriente Médio. Liderou as guerras no Afeganistão e " +
        "no Iraque, a coalizão contra o Estado Islâmico, ataques aos Houthis e, em junho de 2025, bombardeou instalações nucleares " +
        "do Irã. Também é um dos maiores fornecedores de armas à Ucrânia.",
    "sudao" to "Em guerra desde abril de 2023 entre o exército (SAF) e as Forças de Apoio Rápido (RSF), paramilitares. O conflito, " +
        "centrado em Cartum e Darfur, gerou uma das maiores crises de deslocados do mundo e denúncias de atrocidades étnicas.",
)

/** Glossário de armas e sistemas que aparecem com frequência. */
val GLOSSARY = listOf(
    Actor(
        "domo", "Domo de Ferro", listOf("domo de ferro", "cúpula de ferro", "iron dome"),
        "Sistema israelense de defesa contra foguetes e projéteis de curto alcance (4 a 70 km), em operação desde 2011. Calcula a " +
            "trajetória e só dispara interceptadores contra o que vai cair em área habitada. É a primeira camada da defesa aérea de Israel.",
    ),
    Actor(
        "funda", "Funda de Davi", listOf("funda de davi", "david's sling", "davids sling"),
        "Camada intermediária da defesa aérea israelense, feita com os EUA, para foguetes pesados, mísseis de cruzeiro e " +
            "mísseis balísticos de médio alcance (até cerca de 300 km).",
    ),
    Actor(
        "arrow", "Arrow (Hetz)", listOf("arrow 2", "arrow 3", "arrow-2", "arrow-3", "sistema arrow", "arrow system"),
        "Camada superior da defesa israelense contra mísseis balísticos de longo alcance, como os lançados pelo Irã e pelos " +
            "Houthis. O Arrow 3 intercepta fora da atmosfera.",
    ),
    Actor(
        "thaad", "THAAD", listOf("thaad"),
        "Sistema americano de defesa contra mísseis balísticos na fase final do voo, dentro e logo acima da atmosfera. Os EUA " +
            "instalaram uma bateria em Israel em 2024 para reforçar a defesa contra o Irã.",
    ),
    Actor(
        "patriot", "Patriot", listOf("patriot"),
        "Sistema americano de defesa aérea contra aviões, mísseis de cruzeiro e balísticos. É usado pela Ucrânia contra mísseis " +
            "russos, inclusive os hipersônicos Kinzhal, e por vários países do Golfo e da Europa.",
    ),
    Actor(
        "shahed", "Drones Shahed", listOf("shahed", "geran"),
        "Drones de ataque iranianos de baixo custo, que voam até o alvo e explodem (“drones kamikaze”). A Rússia os usa em " +
            "massa contra a Ucrânia (fabricados localmente como Geran-2), e o Irã e os Houthis contra Israel e navios.",
    ),
    Actor(
        "himars", "HIMARS", listOf("himars"),
        "Lançador de foguetes americano montado em caminhão, com alcance de cerca de 80 km (ou 300 km com mísseis ATACMS). " +
            "Ficou conhecido pelo uso ucraniano contra depósitos e comandos russos desde 2022.",
    ),
    Actor(
        "atacms", "ATACMS", listOf("atacms"),
        "Míssil balístico tático americano, com alcance de até cerca de 300 km, lançado por HIMARS. O uso ucraniano contra o " +
            "território russo foi autorizado pelos EUA no fim de 2024.",
    ),
    Actor(
        "storm", "Storm Shadow / SCALP", listOf("storm shadow", "scalp"),
        "Míssil de cruzeiro anglo-francês lançado de aviões, com alcance de mais de 250 km, fornecido à Ucrânia.",
    ),
    Actor(
        "kinzhal", "Kinzhal", listOf("kinzhal"),
        "Míssil balístico russo lançado de aviões, divulgado pela Rússia como hipersônico (muito acima de 5 vezes a velocidade do " +
            "som). Usado contra alvos na Ucrânia.",
    ),
    Actor(
        "tomahawk", "Tomahawk", listOf("tomahawk"),
        "Míssil de cruzeiro americano de longo alcance (mais de 1.500 km), lançado de navios e submarinos. Usado contra alvos " +
            "na Síria, no Iêmen e em outros conflitos.",
    ),
    Actor(
        "gbu57", "Bomba antibunker GBU-57", listOf("gbu-57", "bunker buster", "bunker-buster", "antibunker", "destruidora de bunkers"),
        "Bomba americana de cerca de 13,6 toneladas feita para destruir alvos subterrâneos fortificados. Só o bombardeiro B-2 a " +
            "carrega. Foi usada pela primeira vez em junho de 2025 contra a instalação nuclear iraniana de Fordow.",
    ),
    Actor(
        "f35", "F-35", listOf("f-35", "f35"),
        "Caça furtivo americano de quinta geração, usado por Israel (versão F-35I “Adir”) e por vários aliados. Teve papel " +
            "central nos ataques israelenses ao Irã.",
    ),
    Actor(
        "ira_balisticos", "Mísseis balísticos do Irã", listOf("shahab-3", "shahab 3", "sejjil", "emad missile", "míssil emad", "khorramshahr-4", "ghadr"),
        "Família de mísseis balísticos de médio alcance do Irã (Shahab-3, Ghadr, Emad, Sejjil, Khorramshahr), com alcance de cerca " +
            "de 2.000 km. Foram a base das salvas iranianas contra Israel em abril e outubro de 2024 e em junho de 2025.",
    ),
    Actor(
        "fattah", "Fattah", listOf("fattah"),
        "Míssil balístico iraniano apresentado em 2023 e chamado pelo Irã de hipersônico, com alcance de cerca de 1.400 km. " +
            "O Irã disse tê-lo usado contra Israel em 2024 e 2025.",
    ),
    Actor(
        "kheibar", "Kheibar Shekan", listOf("kheibar shekan", "kheibar-shekan", "khaibar shekan", "kheibar"),
        "Míssil balístico iraniano de combustível sólido, com alcance de cerca de 1.450 km, usado nos ataques contra Israel.",
    ),
    Actor(
        "fateh110", "Fateh-110 / M-600", listOf("fateh-110", "fateh 110", "m-600", "zolfaghar"),
        "Míssil balístico iraniano de curto alcance (cerca de 300 km) e precisão relativamente alta. Versões foram repassadas ao " +
            "Hezbollah (M-600, fabricado na Síria) e a outros aliados do Irã.",
    ),
    Actor(
        "burkan", "Burkan / Palestina-2", listOf("burkan", "borkan", "palestine-2", "palestine 2", "palestina-2", "palestina 2"),
        "Mísseis balísticos dos houthis, derivados de modelos iranianos (Qiam). As versões de longo alcance chegam a Israel a partir " +
            "do Iêmen, a quase 2.000 km. Antes, foram usados contra a Arábia Saudita.",
    ),
    Actor(
        "iskander", "Iskander-M", listOf("iskander"),
        "Míssil balístico russo de curto alcance (cerca de 500 km), lançado de caminhões. Muito usado contra cidades e bases na Ucrânia.",
    ),
    Actor(
        "oreshnik", "Oreshnik", listOf("oreshnik"),
        "Míssil balístico russo de alcance intermediário, com várias ogivas. Usado uma vez contra Dnipro, na Ucrânia, em novembro de " +
            "2024; o alcance estimado passa de 3.000 km.",
    ),
    Actor(
        "lancet", "Drone Lancet", listOf("lancet drone", "lancet drones", "drone lancet", "drones lancet", "zala lancet"),
        "Drone russo de ataque (munição vagante) de alcance curto, usado para caçar tanques, artilharia e defesas antiaéreas perto " +
            "da linha de frente na Ucrânia.",
    ),
    Actor(
        "bayraktar", "Bayraktar TB2", listOf("bayraktar", "tb2"),
        "Drone armado turco de média altitude e longa autonomia. Ficou famoso na guerra de Nagorno-Karabakh (2020) e no início da " +
            "invasão russa da Ucrânia (2022); também usado na Líbia e na Síria.",
    ),
    Actor(
        "kornet", "Kornet", listOf("kornet"),
        "Míssil antitanque guiado russo, com alcance de 5 a 10 km. Muito usado pelo Hezbollah e pelo Hamas contra blindados e " +
            "posições israelenses.",
    ),
)

/** Pessoas-chave. Texto fixo até 2025: cargos podem ter mudado depois. */
val PEOPLE = listOf(
    Actor(
        "netanyahu", "Benjamin Netanyahu", listOf("netanyahu", "netanyahou"),
        "Primeiro-ministro de Israel pelo Likud, o mais longevo da história do país (1996–1999, 2009–2021 e de novo desde o fim " +
            "de 2022, com uma coalizão de partidos religiosos e de extrema direita). Comandou o país na guerra após o 7 de outubro. " +
            "Responde a processos por corrupção em Israel e é alvo de mandado de prisão do Tribunal Penal Internacional (2024), " +
            "que Israel não reconhece.",
    ),
    Actor(
        "khamenei", "Ali Khamenei", listOf("khamenei", "khamanei"),
        "Líder supremo do Irã desde 1989, a mais alta autoridade política e religiosa do país, acima do presidente eleito. " +
            "Comanda as Forças Armadas e a Guarda Revolucionária e tem a palavra final sobre o programa nuclear.",
    ),
    Actor(
        "pezeshkian", "Masoud Pezeshkian", listOf("pezeshkian"),
        "Presidente do Irã desde julho de 2024, considerado reformista. Chefia o governo, mas as decisões de segurança e política " +
            "externa cabem ao líder supremo.",
    ),
    Actor(
        "trump", "Donald Trump", listOf("trump"),
        "Presidente dos EUA pelo Partido Republicano (2017–2021 e desde janeiro de 2025). No primeiro mandato mudou a embaixada " +
            "americana para Jerusalém e patrocinou os Acordos de Abraão; no segundo, ordenou o bombardeio de instalações nucleares " +
            "do Irã (junho de 2025) e tentou mediar acordos em Gaza e na Ucrânia.",
    ),
    Actor(
        "putin", "Vladimir Putin", listOf("putin", "poutine"),
        "Presidente da Rússia, no poder desde 2000 (como presidente ou primeiro-ministro). Ordenou a anexação da Crimeia (2014) e " +
            "a invasão da Ucrânia (2022). É alvo de mandado de prisão do Tribunal Penal Internacional pela deportação de crianças ucranianas.",
    ),
    Actor(
        "zelensky", "Volodymyr Zelensky", listOf("zelensky", "zelenskyy", "zelenski"),
        "Presidente da Ucrânia desde 2019, ex-ator e comediante. Lidera o país desde a invasão russa de 2022 e busca armas e " +
            "garantias de segurança do Ocidente.",
    ),
    Actor(
        "sinwar", "Yahya Sinwar", listOf("yahya sinwar", "sinwar"),
        "Líder do Hamas em Gaza e mentor do ataque de 7 de outubro de 2023. Assumiu a chefia do grupo após a morte de Ismail " +
            "Haniyeh e foi morto por tropas israelenses em Rafah em outubro de 2024. O irmão, Mohammed Sinwar, também comandante, " +
            "foi dado como morto por Israel em 2025.",
    ),
    Actor(
        "haniyeh", "Ismail Haniyeh", listOf("haniyeh", "haniya", "haniyé"),
        "Chefe do escritório político do Hamas, baseado no Catar e peça central nas negociações de cessar-fogo. Foi morto em " +
            "Teerã em julho de 2024, em ataque atribuído a Israel.",
    ),
    Actor(
        "nasrallah", "Hassan Nasrallah", listOf("nasrallah"),
        "Secretário-geral do Hezbollah de 1992 a 2024 e figura mais conhecida do grupo. Foi morto em um bombardeio israelense em " +
            "Beirute em setembro de 2024 e sucedido por Naim Qassem.",
    ),
    Actor(
        "qassem", "Naim Qassem", listOf("naim qassem", "naim kassem", "naim qasem"),
        "Secretário-geral do Hezbollah desde outubro de 2024, antes vice de Hassan Nasrallah por mais de três décadas.",
    ),
    Actor(
        "abbas", "Mahmoud Abbas", listOf("mahmoud abbas", "abu mazen"),
        "Presidente da Autoridade Palestina e líder do Fatah desde 2005. Defende negociações e a solução de dois Estados; seu " +
            "mandato nunca foi renovado em eleições, o que alimenta críticas entre palestinos.",
    ),
    Actor(
        "sharaa", "Ahmed al-Sharaa", listOf("al-sharaa", "sharaa", "jolani", "julani"),
        "Presidente de transição da Síria desde o início de 2025. Ex-comandante jihadista (conhecido como Abu Mohammed al-Jolani), " +
            "liderou o grupo HTS na ofensiva que derrubou Bashar al-Assad em dezembro de 2024.",
    ),
)

/** Perfil dos veículos: país, dono e linha editorial (avaliação aproximada). */
val SOURCE_PROFILES = mapOf(
    "Times of Israel" to "Israel · site independente em inglês, fundado em 2012. Linha centrista, muito usado por correspondentes estrangeiros.",
    "Jerusalem Post" to "Israel · jornal em inglês fundado em 1932. Linha de centro-direita.",
    "Ynetnews" to "Israel · edição em inglês do Ynet, do grupo Yedioth Ahronoth, o maior do país. Linha centrista e popular.",
    "Israel Hayom" to "Israel · jornal gratuito criado em 2007 pelo bilionário americano Sheldon Adelson. Linha de direita, historicamente próximo de Netanyahu.",
    "Al Jazeera" to "Catar · rede financiada pelo governo do Catar. Forte cobertura de Gaza e do mundo árabe; Israel proibiu suas operações no país em 2024.",
    "Middle East Eye" to "Reino Unido · site sobre o Oriente Médio fundado em 2014. Crítico de Israel e dos governos do Golfo; críticos apontam proximidade com o Catar, o que o site nega.",
    "Ynet (hebraico)" to "Israel · o site mais lido do país, do grupo Yedioth Ahronoth, na versão original em hebraico. Linha centrista e popular; traduzido no aparelho.",
    "Walla (hebraico)" to "Israel · portal de notícias em hebraico, do grupo Bezeq. Linha centrista; traduzido no aparelho.",
    "Maariv (hebraico)" to "Israel · jornal fundado em 1948, hoje do grupo Jerusalem Post. Linha de centro-direita; traduzido no aparelho.",
    "Haaretz (hebraico)" to "Israel · jornal mais antigo em circulação (1918). Linha de esquerda liberal, crítico do governo Netanyahu; traduzido no aparelho.",
    "Al Jazeera (árabe)" to "Catar · canal original em árabe da rede, financiado pelo governo do Catar. Tom mais duro que a edição em inglês; traduzido no aparelho.",
    "Al Arabiya (árabe)" to "Arábia Saudita · rede de Dubai controlada por capital saudita. Hostil ao Irã, ao Hezbollah e aos houthis; traduzido no aparelho.",
    "Asharq Al-Awsat (árabe)" to "Arábia Saudita · jornal pan-árabe com sede em Londres, ligado à família real saudita. Linha pró-governo saudita; traduzido no aparelho.",
    "BBC Middle East" to "Reino Unido · emissora pública britânica. Busca imparcialidade e recebe críticas dos dois lados do conflito.",
    "BBC World" to "Reino Unido · emissora pública britânica. Busca imparcialidade e recebe críticas dos dois lados do conflito.",
    "The Guardian" to "Reino Unido · jornal controlado por uma fundação (Scott Trust). Linha de centro-esquerda.",
    "DW" to "Alemanha · emissora pública internacional financiada pelo governo alemão.",
    "France 24" to "França · canal público internacional (France Médias Monde).",
    "Kyiv Independent" to "Ucrânia · site em inglês fundado em 2021 por ex-jornalistas do Kyiv Post. Perspectiva ucraniana.",
    "NYT World" to "EUA · The New York Times, jornal de referência. Reportagem factual; opinião de centro-esquerda.",
    "NPR" to "EUA · rádio pública sem fins lucrativos. Conservadores a veem como de centro-esquerda.",
    "CNN" to "EUA · canal de notícias 24 h da Warner Bros. Discovery. Linha de centro a centro-esquerda.",
    "Fox News" to "EUA · canal da Fox Corporation (família Murdoch). Linha conservadora, próxima do Partido Republicano.",
    "Washington Post" to "EUA · jornal de Washington, de Jeff Bezos desde 2013. Linha de centro a centro-esquerda.",
    "Defense News" to "EUA · publicação especializada em defesa e indústria militar (Sightline Media).",
    "G1" to "Brasil · portal de notícias do Grupo Globo.",
    "Folha" to "Brasil · Folha de S.Paulo, do Grupo Folha. Linha pluralista, de centro.",
    "BBC Brasil" to "Reino Unido · serviço em português da BBC.",
    "DW Brasil" to "Alemanha · serviço em português da DW.",
    "CNN Brasil" to "Brasil · canal brasileiro que licencia a marca CNN.",
    "Estadão" to "Brasil · O Estado de S. Paulo, jornal tradicional. Linha liberal, de centro-direita.",
    "RFI Brasil" to "França · serviço em português da Rádio França Internacional, pública.",
    "Poder360" to "Brasil · jornal digital de Brasília focado em política e poder.",
)

const val SOURCE_DISCLAIMER = "Resumo geral; a linha editorial é uma avaliação aproximada."

/** Termos do ator já normalizados (calculados uma vez). */
private val normalizedTerms = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

private fun Actor.normTerms(): List<String> = normalizedTerms.getOrPut(key) { terms.map(::normalize).distinct() }

/** O ator aparece no texto (já normalizado)? Aceita plural simples ("houthis"). */
fun Actor.mentionedIn(normText: String): Boolean = normTerms().any { wordRegex(it, suffix = "s?").containsMatchIn(normText) }

private fun Cluster.normText(translated: (String) -> String) =
    normalize("$title\n$summary\n${translated(title)}\n${translated(summary)}")

/** Atores e armas citados na notícia (título, resumo e tradução). */
fun Cluster.actors(translated: (String) -> String): List<Actor> {
    val text = normText(translated)
    return (PEOPLE + ACTORS + GLOSSARY).filter { it.mentionedIn(text) }
}

/** Histórias do feed que citam o ator/pessoa (para "notícias recentes"); para nas 5 primeiras. */
fun Actor.related(clusters: List<Cluster>, translated: (String) -> String, exclude: String? = null): List<Cluster> =
    clusters.asSequence().filter { it.id != exclude && mentionedIn(it.normText(translated)) }.take(5).toList()

fun isPerson(actor: Actor) = PEOPLE.any { it.key == actor.key }
