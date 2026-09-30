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
        43,
        listOf(
            "🛠 Corrigido o fechamento do app ao rolar a tela Ferramentas (e a busca), que tinha duas ferramentas levando ao mesmo lugar.",
        ),
    ),
    ChangelogEntry(
        42,
        listOf(
            "🇺🇦 Placar aéreo da Ucrânia: drones e mísseis lançados pela Rússia por noite e quantos foram abatidos, com gráfico de 30 dias (Radar → Números e página da região).",
            "🇺🇳 Conselho de Segurança da ONU: reuniões, resoluções aprovadas, vetos (e quem vetou) e votações previstas (Radar → Vozes).",
            "🌦 Tempo nas zonas de conflito: vento, chuva, neve, neblina e tempestade de areia em Gaza, Kiev, Cartum e outros pontos (Radar → Sensores).",
            "⏰ Sirenes por hora do dia: em que horários os alertas mais tocaram em Israel na semana (tela Sirenes).",
            "🏷 Temas: nuclear, drones, mísseis, reféns, humanitário, navios e ciberataques, com página própria (chips na tela Hoje).",
            "📰 Modo manchetes: a lista mostra só os títulos (Ajustes → Lista de notícias).",
            "🎓 Entenda o contexto: na notícia, atalho para a lição do Curso e os marcos da região.",
            "✔ Aviso quando uma história que você segue é confirmada por várias fontes ou fica com informações conflitantes.",
            "🧩 Dois widgets novos: próximo ultimato (contagem regressiva) e sirenes em Israel.",
            "🗓 Boletim mensal: os últimos 30 dias em imagem para compartilhar (Ferramentas → Resumos).",
        ),
    ),
    ChangelogEntry(
        41,
        listOf(
            "🔍 Busca com filtros: região:irã, fonte:g1, lado:árabe, depois:01/09, antes:15/09, tipo:urgente (ou conflito, confirmado, umlado, alterada). Agora ela procura também no Arquivo.",
            "📤 Compartilhar para o Argos: mande um link de notícia de outro app para o Argos e ele abre a mesma história, com a cobertura de todos os lados.",
            "🎯 Nota de calibração (Brier) nas previsões: mostra se a sua certeza bate com os acertos, por faixa de confiança.",
            "🔔 Regras de alerta suas: \u201cse Ormuz entrar em alerta e o petróleo passar de 90, me avise\u201d (Ajustes → Notificações ou Ferramentas).",
            "🧹 Menos notificações repetidas: um aviso por assunto a cada 3 h, e as histórias seguidas juntam os veículos novos num aviso só.",
            "🔤 Original e tradução lado a lado no texto completo, e 15 termos difíceis de traduzir explicados (intifada, nakba, shahid, hasbara, sumud...).",
            "⚖ Comparar (Ferramentas): duas regiões lado a lado, ou a mesma região hoje e há 1, 7, 14 ou 30 dias.",
            "🟢 Status das fontes: quais veículos e sensores estão funcionando agora (também em abugdn.github.io/Argos/status.html).",
            "🕸 Quem apoia quem: rede de alianças, apoios, rivalidades e guerras entre países e grupos, com o porquê de cada ligação.",
            "🌡 Termômetro diplomático: embaixadores, sanções, fronteiras e negociações de cada região em 7 dias, também na página da região.",
            "🇧🇷 E o Brasil?: na página da região, por onde a crise pode chegar aqui (combustível, dólar, fertilizantes, frete, brasileiros), com as cotações.",
        ),
    ),
    ChangelogEntry(
        40,
        listOf(
            "📊 O Argos agora conta, de forma anônima, quantas pessoas usam o app: uma vez por dia ele avisa que foi aberto, só com a versão. Nada pessoal é enviado (GoatCounter, que não guarda IP).",
            "🔒 Não quer entrar na conta? Desligue em Ajustes → Privacidade → Contar meu uso.",
        ),
    ),
    ChangelogEntry(
        39,
        listOf(
            "🔎 Nível de confiança em cada notícia (aba Cobertura): várias fontes independentes, só uma fonte, só um lado, versão oficial ou informações conflitantes, com os motivos.",
            "🌳 Árvore de fontes: quando vários veículos só repetem a mesma agência (Reuters, AP, AFP, WAFA, IRNA...), eles contam como uma fonte só.",
            "🌐 Como a notícia se espalhou: em que imprensa ela saiu primeiro e quanto tempo depois chegou nas outras.",
            "⚖ Contradições ao vivo (Ferramentas): números que não batem, palavras diferentes, histórias de um lado só e muitos veículos com uma fonte só.",
            "🧮 De onde vem a tensão: na página da região, cada parte do índice com os números por trás, e o porquê de um alerta de alta incomum.",
            "🧩 Sinais coincidentes no Radar: sirenes, apagão, espaço aéreo, aviões militares, focos de calor, sismo e disparo de notícias na mesma região viram um incidente só (com aviso).",
            "🕘 Desde sua última visita: a página da região mostra o que mudou desde a última vez que você abriu (tensão, alertas e histórias novas).",
            "🔬 Como sabemos? (Ferramentas → Aprender): a metodologia inteira do Argos em linguagem simples.",
        ),
    ),
    ChangelogEntry(
        38,
        listOf(
            "🌍 10 regiões novas: Egito, Jordânia, Arábia Saudita, Emirados Árabes, Catar e Golfo, Turquia, Somália, Mediterrâneo, ICE (imigração dos EUA) e Brasil — com mapa, bandeira, contexto e cidades.",
            "🚨 Sirenes em Israel: alertas de foguete e drone ao vivo, no mapa, com as últimas 24 h e os últimos 7 dias. Aviso opcional em Ajustes → Notificações.",
            "🌋 Sismógrafo no Radar: tremores perto do Irã, da Coreia do Norte e de outras zonas; explosão ou tremor raso perto de local nuclear vira alerta.",
            "✈ Alertas de viagem dos EUA (níveis 1 a 4) no Radar → Contexto, com aviso quando um país sobe de nível.",
            "⏳ Ultimatos e prazos: “48 horas para…” nas manchetes vira contagem regressiva, e depois o Argos mostra o que aconteceu.",
            "📏 E se fosse no Brasil?: Gaza, a área ocupada na Ucrânia ou o alcance de um míssil em cima da sua cidade.",
            "🎓 Curso rápido: lições curtas para entender cada guerra (em Ferramentas → Aprender).",
        ),
    ),
    ChangelogEntry(
        32,
        listOf(
            "🗂 App mais organizado:",
            "🏠 Tela Hoje mais enxuta: um painel de mini-cartões (Relógio, Radar, O dia em 1 minuto, trégua, agenda, vigília) que rola de lado, e as notícias logo depois.",
            "🧩 Monte a tela Hoje: em Ajustes → Tela Hoje, ligue, desligue e mude a ordem dos blocos e dos mini-cartões.",
            "📰 Notícia em três abas: Texto · Cobertura (lados, números, manchetes alteradas, linha do tempo) · Contexto (pessoas, saga, Radar da região).",
            "📚 Arquivo e Salvos viraram a aba Biblioteca: Salvos, Dossiês, Previsões, Arquivo e Lidas.",
            "🧰 Ferramentas: tudo do Argos num lugar só (botão 🔧 na tela Hoje). A busca também acha telas: experimente “petróleo” ou “reféns”.",
            "📡 Radar por prioridade: “o que mudou desde a última visita” no topo, cartões em alerta primeiro e os calmos recolhidos.",
            "🌍 Página da região virou a central: atalhos para o mapa e o Radar, quem manda, reféns, agenda, apostas e vozes oficiais.",
            "⚙ Ajustes em categorias, com busca. Novo: Aparência → Reduzir animações.",
            "ⓘ Cartões padronizados: mesmo cabeçalho com fonte e hora, e a explicação no ⓘ.",
            "📜 Todas as notas de versão ficam em Ajustes → Sobre e atualização, em cartões.",
        ),
    ),
    ChangelogEntry(
        30,
        listOf(
            "👁 Abertura com o olho do Argos se abrindo.",
            "✨ Notícia abre “crescendo” a partir do cartão, com foto e título indo para o lugar; abas deslizam de lado.",
            "🆕 Histórias novas entram na lista com um brilho dourado; puxar para atualizar mostra o olho girando.",
            "🔢 Números sobem contando (tensão, Relógio, trégua, área ocupada) e gráficos se desenham na hora.",
            "🔴 Cartões em alerta “respiram” com borda vermelha; o ícone do Radar tem varredura.",
            "🗺 No Mapa, ondas de sonar sobre as regiões em alerta e aviões militares piscando.",
            "🗂 Carimbo “SEGUINDO”/“ARQUIVADO” ao seguir ou salvar uma notícia.",
            "♿ Se o celular estiver com as animações desligadas (Opções do desenvolvedor ou Acessibilidade), o Argos também desliga as dele.",
        ),
    ),
    ChangelogEntry(
        28,
        listOf(
            "🐞 Corrigido: o app fechava ao voltar para o Mapa com a linha de frente (ou outra camada) ligada.",
        ),
    ),
    ChangelogEntry(
        26,
        listOf(
            "✈ Aviões militares no ar agora: reabastecedores, aviões-radar, drones de espionagem e bombardeiros sobre o Oriente Médio e o Mar Negro, com aviso quando passam do normal. No Radar e no Mapa.",
            "⚓ Onde estão os porta-aviões americanos: posição de cada um pelo acompanhamento semanal da frota, e quantos estão perto do Oriente Médio.",
            "🗺 Linha de frente na Ucrânia: área ocupada desenhada no Mapa, quanto avançou ou recuou na semana e as últimas mudanças.",
            "🛰 Mapa em modo satélite (botão Satélite).",
            "◉ Faixa de manchetes urgentes rolando no topo da tela Hoje. Dá para desligar em Ajustes → Tela Hoje.",
            "👁 O olho do Argos reage à tensão: meio fechado quando está calmo, vermelho e pulsando na crítica. Toque nele para abrir o Relógio.",
            "🗂 Cartão de compartilhar e boletim com cara de dossiê: número do dossiê, letra de máquina de escrever e carimbo CONFIDENCIAL.",
            "🗂 Dossiês (aba Salvos): acompanhe um assunto e o Argos monta sozinho a linha do tempo com as notícias, com espaço para notas.",
            "🎯 Minhas previsões (aba Salvos): registre um palpite, o Argos avisa na data e mostra seu placar de acertos.",
        ),
    ),
    ChangelogEntry(
        24,
        listOf(
            "🐞 Corrigido: o app fechava sozinho ao carregar fotos e bandeiras (cartões de pessoas, regiões e “Quem manda”).",
            "🧯 Se o Argos fechar sozinho, na próxima abertura aparece o erro com um botão para compartilhar. Ajuda a corrigir rápido.",
        ),
    ),
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
