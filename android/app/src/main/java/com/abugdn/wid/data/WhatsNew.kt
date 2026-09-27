package com.abugdn.wid.data

/**
 * Histórico de novidades por versão (versionCode = número da build no CI).
 *
 * O pop-up mostra todas as versões mais novas que a última que a pessoa confirmou com
 * "não mostrar de novo" — quem pulou versões vê as notas de cada uma. A cada versão com
 * novidades, acrescente uma entrada no topo com o versionCode esperado da próxima build.
 */
data class ChangelogEntry(val versionCode: Long, val items: List<String>)

val CHANGELOG = listOf(
    ChangelogEntry(
        21,
        listOf(
            "🐞 Corrigido o travamento ao abrir o cartão de uma pessoa, grupo ou arma. A lista de notícias também ficou mais rápida.",
            "🖼 Fotos nos cartões de pessoas, grupos e armas (da Wikipédia) e nos chips de pessoas dentro da notícia.",
            "🏳 Bandeiras nos cartões e páginas de países e regiões, nos chips da notícia e em “Quem manda” (Radar → Contexto), agora com a foto de cada um.",
            "🔥 Focos de calor por satélite ligados: veja em Radar → Sensores e no botão 🔥 Focos do Mapa.",
            "📶 Com a economia de dados ligada, fotos e bandeiras não são baixadas.",
        ),
    ),
    ChangelogEntry(
        19,
        listOf(
            "📡 Aba nova: Radar. Dados de fora da imprensa em 6 categorias: Sensores, Mercados, Números, Vozes, Análise e Contexto.",
            "🌐 Apagão de internet em Gaza, Irã, Líbano, Iêmen, Ucrânia e outros (IODA), com gráfico das últimas 24 h.",
            "✈ Espaço aéreo: aviões no ar sobre Israel, Líbano, Síria, Jordânia, Iraque e Irã comparados com o normal. Fechou? O Argos avisa.",
            "🔥 Focos de calor por satélite (NASA) em Gaza, Líbano, Ucrânia, Sudão e Iêmen, também no Mapa (botão 🔥 Focos).",
            "🚢 Navios em Bab el-Mandeb, Suez, Ormuz e Bósforo; 🛢 petróleo, ouro, shekel, rublo, hryvnia e dólar.",
            "🩸 Números de Gaza e da Cisjordânia, deslocados por país, 🎗 linha do tempo dos reféns e 🇺🇦 perdas russas segundo a Ucrânia.",
            "🏛 Fontes oficiais (CENTCOM, Pentágono, FDI, ONU, Kremlin, Irã...) e ⛔ sanções dos EUA e da UE.",
            "🧠 Análises do ISW e Crisis Group, 📉 CrisisWatch do mês e ✅ checagens: quando um boato ligado a uma notícia foi desmentido, aparece um aviso nela.",
            "🎲 O que os apostadores acham: probabilidades do Polymarket sobre cessar-fogo e ataques.",
            "📅 Agenda de datas sensíveis, 🕰 hora e dia/noite nas capitais e 🧭 quem manda em cada lado.",
            "🌡 Apagão de internet e espaço aéreo fechado agora somam no índice de tensão e ficam no registro de vigília. Aviso desligável em Ajustes.",
        ),
    ),
    ChangelogEntry(
        18,
        listOf(
            "👁 Relógio do Argos: tensão global de 0 a 100 no topo da tela Hoje, com tela própria, widget novo e aviso quando sobe para alta ou crítica.",
            "📈 Histórico de tensão: gráfico de 30 dias por região e do relógio, com os picos da vigília marcados.",
            "✏ Manchete alterada: quando um veículo troca o título depois de publicar, o Argos mostra o antes e o depois.",
            "📍 Mapa por cidade: Rafah, Khan Younis, Beirute, Isfahan, Kharkiv, Sanaa... com as notícias de cada uma.",
            "🔫 Ficha de armamentos: origem, tipo, alcance e onde foi usada, com atalho para o círculo de alcance no mapa. 10 armas novas no glossário.",
            "💬 Quem disse o quê: frases entre aspas de Netanyahu, Khamenei, Trump, Putin e outros, na ficha de cada pessoa.",
            "🔍 Checar imagem: busca reversa da foto da notícia (Google Lens), para ver se ela é antiga ou de outro lugar.",
            "🕊 Contador de trégua: dias de cessar-fogo em Gaza, no Líbano e entre Israel e Irã, com as violações relatadas. Dá para esconder uma trégua que já acabou.",
        ),
    ),
    ChangelogEntry(
        17,
        listOf(
            "🖼 Cartão de compartilhar mais limpo: só o nome ARGOS em cima e o ícone do olho embaixo, sem slogan.",
            "🗞 O boletim semanal também perdeu o slogan do rodapé.",
        ),
    ),
    ChangelogEntry(
        16,
        listOf(
            "🤝 Selo \"lados opostos\": quando a imprensa árabe e a israelense (ou americana) contam a mesma história. E ⚠ \"só um lado\" quando uma história grande saiu só de um lado.",
            "🇮🇱🇸🇦 Imprensa local no idioma original: Ynet, Walla, Maariv e Haaretz em hebraico; Al Jazeera, Al Arabiya e Asharq Al-Awsat em árabe, traduzidos no aparelho.",
            "🎯 Alcance de mísseis no Mapa: Irã, houthis, Hezbollah, Hamas, ATACMS e Iskander em vermelho; Domo de Ferro e THAAD em ouro. Toque na legenda para ir até o círculo.",
            "📜 Registro de vigília (no Arquivo): todo alerta urgente, alta incomum, número divergente e tensão crítica fica guardado com data e hora.",
            "🗞 Boletim semanal: todo domingo, uma imagem preta e dourada com as 5 da semana, a região mais tensa, o maior pico de alerta e quem noticiou primeiro. Pronta para compartilhar.",
            "ℹ Na 1ª vez que aparecer uma notícia em hebraico ou árabe, o app baixa o tradutor daquele idioma (~30 MB cada).",
        ),
    ),
    ChangelogEntry(
        15,
        listOf(
            "👁 O WID agora é ARGOS: o gigante de cem olhos que nunca dormia. Nenhuma guerra escapa.",
            "🔺 Ícone novo: o olho no triângulo, em ouro sobre preto (também nas notificações e atalhos).",
            "⚫ Paleta \"Ordem\": preto profundo, ouro antigo, osso e vermelho-sangue só para alertas. Títulos em fonte serifada.",
            "🎨 Escuro é o padrão agora; o tema claro virou \"pergaminho\". Troque em Ajustes → Aparência.",
            "🖼 Widgets e o cartão de compartilhar com a identidade nova.",
        ),
    ),
    ChangelogEntry(
        14,
        listOf(
            "🌐 Tradução afinada para guerra: siglas agora saem certas (US → Estados Unidos, e não \"nós\"; IDF → Forças de Defesa de Israel; UN → Nações Unidas; IRGC, ISIS, UAE, PM...).",
            "🗺 Países e cidades no português do Brasil: Irã, Iêmen, Teerã, Moscou, Oriente Médio, Cisjordânia, Turquia (e não \"peru\")...",
            "💥 Termos militares corrigidos: strike vira ataque (não greve), shelling vira fogo de artilharia, carrier vira porta-aviões, barrage vira saraivada.",
            "🔁 As traduções antigas são refeitas automaticamente com as regras novas.",
        ),
    ),
    ChangelogEntry(
        13,
        listOf(
            "🌡 Índice de tensão (0–100) por região: volume, palavras de escalada, urgência e cobertura comparados com o normal. Na página da região, na lista do Mapa e no widget por região.",
            "⚠ Alerta de alta incomum: notificação quando uma região passa de 3× o ritmo normal de notícias.",
            "📚 Sagas: histórias de dias diferentes sobre o mesmo assunto viram capítulos (\"Capítulo 3 de 5\").",
            "🔢 Números divergentes: mortos e feridos citados por cada veículo, com alerta quando não batem.",
            "🗣 Palavras de cada lado: como cada imprensa chama a mesma coisa (terroristas × combatentes, operação × ataque...).",
            "🏁 Quem noticia primeiro: ranking de 30 dias no Arquivo.",
            "📅 Contador dos conflitos: \"Dia N\" da guerra na página da região.",
        ),
    ),
    ChangelogEntry(
        12,
        listOf(
            "📥 Aba Lidas: as notícias que você já abriu saem da lista principal e ficam em \"Lidas\" (tela Hoje).",
            "🔄 O app procura versão nova toda vez que é aberto; não precisa mais ir aos Ajustes.",
            "📰 Principal do dia mais atual: histórias de ontem perdem peso com o tempo e o servidor volta a atualizar a cada 30 minutos.",
        ),
    ),
    ChangelogEntry(
        11,
        listOf(
            "📜 Novidades acumuladas: se você pular versões, este aviso mostra as notas de todas as que perdeu.",
            "⏪ O que você perdeu: depois de mais de 24 h sem abrir o app, um resumo das principais do período.",
            "🌍 Página da região: toque numa região (mapa, filtro ou chip) para ver contexto, marcos, tendência, notícias atuais e as principais dos últimos 30 dias.",
            "🖼 Compartilhar como imagem: cartão com título em português, veículo e número de veículos, pronto para WhatsApp/story.",
            "📖 Modo leitura: tempo estimado de leitura, fonte serifada e espaçamento maior (botão Aa na notícia).",
            "🫥 Imagens sensíveis borradas até você tocar (em notícias com mortos ou feridos). Desligável nos Ajustes.",
            "🔕 Notificações agrupadas: várias notícias juntas viram um grupo só.",
            "📊 Sua semana: quantas notícias você leu, regiões que mais acompanhou e histórias seguidas ainda ativas (aba Arquivo).",
        ),
    ),
    ChangelogEntry(
        10,
        listOf(
            "🗺 Mapa corrigido: não desenha mais por cima do resto da tela ao arrastar. Marcadores agora são círculos com o número de histórias.",
            "🆕 Novo desde a sua leitura: notícias que você já leu mostram quantos veículos chegaram depois, marcados como NOVO.",
            "👤 Pessoas: Netanyahu, Khamenei, Trump, Putin, Zelensky, líderes do Hamas e do Hezbollah e outros, com notícias recentes que os citam.",
            "📰 Perfil dos veículos: toque no nome de um veículo (ⓘ) para ver país, dono e linha editorial.",
            "🔔 Notificações com botões Salvar e Seguir.",
            "📌 Atalhos: segure o ícone do app para O dia em 1 minuto, Buscar e Salvos.",
            "📶 Economia de dados (Ajustes): sem imagens e downloads grandes só no Wi-Fi.",
            "📁 Pastas e notas nos Salvos: toque no cartão \"Salva\" dentro da notícia.",
            "⚫ Tema preto AMOLED em Ajustes → Aparência.",
        ),
    ),
    ChangelogEntry(
        9,
        listOf(
            "🇺🇸 Estados Unidos: nova região (filtro, mapa, tendência e notificações) e veículos americanos (NPR, CNN, Fox News, Washington Post, Defense News).",
            "▶ O dia em 1 minuto: as 5 principais do dia em cartões de tela cheia.",
            "🛡 Glossário militar: Domo de Ferro, THAAD, Shahed e outros na notícia.",
            "📜 Marcos históricos no contexto de cada região.",
            "🧩 Widget por região.",
        ),
    ),
)

/** Ids usados antes do histórico por versão (um único aviso por vez) -> versão equivalente. */
val LEGACY_WHATS_NEW_IDS = mapOf("2026-09-25-eua" to 9L, "2026-09-25-pessoas" to 10L)
