# CLAUDE.md — contexto do projeto Argos (antigo WID)

Leia o `README.md` para a visão geral e o setup. Este arquivo guarda o que um assistente precisa saber
para continuar o trabalho sem redescobrir tudo.

## Identidade

- Nome **Argos** (o gigante de cem olhos), slogan "Cem olhos sobre a guerra". Visual "conspiracionista":
  logo = olho no triângulo (Olho da Providência), paleta "Ordem" em `ui/Theme.kt`: preto `#050505`,
  ouro antigo `Accent #C9A227`, osso `#E8E2D0`, cinza `#8A8578`, vermelho-sangue `Alert #B3122E`
  **só** para alertas. Títulos em serifa. Tema escuro é o padrão.
- O pacote `com.abugdn.wid`, a pasta `backend/wid` e `WID_VERSION_CODE` ficam com o nome antigo de
  propósito (mudar o applicationId quebraria as atualizações).
- O repositório foi renomeado para **`AbuGDN/Argos`** (26/09/2026). App e página ainda têm `AbuGDN/WID`
  como segunda opção (o GitHub redireciona o nome antigo); pode ficar.
- Página de download: **https://abugdn.github.io/Argos/** (GitHub Pages da branch `gh-pages`).

## Onde estamos (30/09/2026)

- App na **versão 1.0.44** (versionCode = run_number do workflow "App Android"; a próxima entrada do
  CHANGELOG é **45**). Branch padrão/base: `claude/adoring-hamilton-kr5stn` (ainda não virou `main`).
- Fluxo usado nas sessões em nuvem: trabalho na branch `claude/app-information-suggestions-8g3ca9`
  (recriada a partir da base a cada rodada) → PR para a base → build "App Android" verde → merge com
  **`[skip ci]` no título do merge** (senão o merge gera uma segunda release igual). Mudanças só em
  `backend/`/`web/` não compilam app: merge direto. No PC, commitar direto na base também funciona:
  todo push que mexe em `android/` publica release.
- Últimas versões: 38–41 (sirenes Tzeva Adom, sismos, alertas de viagem, ultimatos, curso rápido,
  regras de alerta, busca com filtros, compartilhar para o Argos, comparar, status das fontes, quem apoia
  quem, termômetro diplomático, "E o Brasil?", contagem de uso GoatCounter); 42 (placar aéreo da Ucrânia,
  Conselho de Segurança, tempo, temas, modo manchetes, widgets de prazo e sirenes, boletim mensal);
  43 (correção: crash ao rolar Ferramentas, chave repetida `radar:2`); 44 (fome/IPC, deslocados/ACNUR,
  imprensa/CPJ, gás na Europa, busca de sanções, quem arma quem, bases no mapa, verificar imagem,
  antes e depois por satélite).
- Pendências conhecidas: os percentuais de `data/Arms.kt` foram escritos de memória (fact sheet do
  SIPRI de mar/2025) e precisam ser conferidos na fonte; as seções novas do Radar (refugees, hunger,
  gas, press, sanctionlist) rodaram pela primeira vez em 30/09 — conferir `status` no radar.json.
- Fora do app: `web/carrossel/` (slides do Instagram, só para o dono) e um vídeo de apresentação de 30 s
  (vertical, feito com a skill "motion-graphics-video" do próprio dono; o vídeo e o código dele não
  estão no repositório).

## O projeto em uma frase

App Android pessoal de notícias de guerra (foco Israel/Oriente Médio, depois EUA e o mundo), em
português, para o dono e até 4 amigos, com **custo zero**: backend em Python rodando no GitHub Actions
que publica JSON na branch `gh-pages`; app Kotlin/Compose que lê esse JSON.

## Como o dono trabalha

- Conversa em **português**; textos de UI, notas de versão, commits e comentários em português.
- Ciclo típico: pede ideias → escolhe quais ("todas menos X") → tudo entra numa atualização. Para cada
  atualização do app: implementar, acrescentar entrada no `CHANGELOG` (`data/WhatsNew.kt`), push,
  conferir o build no Actions, responder com um resumo curto do que entrou.
- Descartou até agora: ler em voz alta (TTS), grupo/bot no Telegram ou Discord, PWA/app web, tradução
  ou textos com LLM/IA, mapa colorido por tendência, "baixar tudo offline", backup/exportação, mapa em
  time-lapse/replay, modo "sala de situação"/Modo Evento/TV, sons, quiz, podcasts/vídeos no app, QR
  "continuar no PC", luzes da noite por satélite no app, igualar o painel web ao app, "dieta de notícias"
  (tempo de leitura/pausas). Não re-sugerir.
- Pediu sugestões de **features novas**, não complementos de coisas que já existem.
- Autorizou sem perguntar: abrir PR, conferir o build e fazer o merge. Teste de fumaça só em mudança
  grande de UI (não rodar em correção pequena).
- Restrições firmes: custo zero, só Android, repo público.

## Mapa do código

```
backend/                 Python 3.11 (feedparser, httpx, PyYAML). Rodar: python -m wid.build --out ../site
  config/sources.yaml    ~33 veículos: url (ou lista de alternativas), lang pt|en|he|ar, origin, weight
  config/keywords.yaml   termos de guerra, tags de região, topics (temas: nuclear, drones... → cluster.topics), boost, termos de urgência; standalone_tags (ICE entra
                         sem termo de guerra) e weak_tags (Brasil não conta como região no filtro); nenhuma das
                         duas entra no Relógio; "!TERMO" diferencia maiúsculas (ICE ≠ ice)
  wid/fetch.py           download/parse RSS, canonical_url, limpeza de resumos, Google News
  wid/text.py            tokens() com dicionário PT→EN (CANON/PHRASES) e hebraico/árabe→EN (CANON_RTL,
                         prefixos colados, normalize_rtl) para agrupar entre idiomas
  wid/keywords.py        Match: relevante se ≥1 termo de guerra e (região ou ≥2 termos)
  wid/cluster.py         agrupamento guloso por sobreposição de tokens (janela 18 h), scores, lead()
  wid/analysis.py        figuras (mortos/feridos), enquadramento, lados ("opostos"/"um_lado"),
                         violação de trégua, tensão/anomalia, sagas, first.json
  config/radar.yaml      Radar: países/zonas/fontes de cada seção e intervalo mínimo entre coletas
  wid/radar.py           Radar (radar.json): IODA, OpenSky, NASA FIRMS, PortWatch, cotações (Stooq →
                         Yahoo → câmbio aberto), Tech for Palestine + HDX HAPI, perdas (russianwarship.rip),
                         RSS oficiais/sanções/análises/checagens, CrisisWatch, Polymarket, aviões
                         militares (adsb.lol), porta-aviões (USNI Fleet Tracker), frente (DeepStateMap), sirenes
                         (Tzeva Adom; oref.org.il dá 403 no GitHub; dicionário de cidades em sirens_cities.json),
                         sismos (USGS), alertas de viagem (Departamento de Estado dos EUA), Conselho de Segurança
                         (press.un.org + Security Council Report: reuniões, aprovadas, vetos) e tempo (Open-Meteo,
                         com poeira do serviço de qualidade do ar), deslocados (ACNUR, anual), fome e preços
                         (IPC/HDX HAPI), gás na Europa (AGSI+ sem chave, ENTSOG/TurkStream), jornalistas mortos (CPJ) e
                         lista de sanções (OpenSanctions → sanctions.tsv.gz à parte, baixada pelo app só na busca). Cada seção é
                         independente e guarda o último dado bom; baselines em stats/radar_state.json.
                         apply_signals() soma apagão/espaço aéreo fechado na tensão (parts.sensores);
                         correlate() junta sinais de tipos diferentes na mesma região (radar.incidents); link_factchecks()
  wid/build.py           orquestra; escreve feed.json (com "global" = Relógio do Argos), top.json,
                         history/, stats/daily.json (com pico de tensão do dia), sagas.json;
                         merge() guarda manchetes trocadas em article.edits
  wid/trust.py           por história: wires (árvore de fontes: agências citadas no título/resumo, com maiúsculas),
                         confidence (alta/media/baixa/conflito + motivos) e spread (1ª aparição por origem)
  wid/diplomacy.py       termômetro diplomático: categorias por regex no título (pt/en), 7 dias → diplomacy.json
  wid/airwar.py          placar aéreo da Ucrânia (drones/mísseis/abatidos por noite, regex nas manchetes) → airwar.json
  wid/deadlines.py       ultimatos ("48 horas para…") nas manchetes → deadlines.json, com "o que aconteceu depois"
  tests/                 pytest com fixtures; rode sempre antes do push
android/app/src/main/java/com/abugdn/wid/
  WidApp.kt              Application: Repository, canais, agenda workers
  data/Models.kt         espelho do feed.json (kotlinx.serialization, ignoreUnknownKeys) + TAG_LABELS
  data/Repository.kt     refresh (baixa + traduz), fullText (Readability4J + ML Kit), salvos, lidas,
                         snapshots, arquivo, stats, first, changelog; applySourcePrefs (veículos)
  data/Settings.kt       SettingsStore em SharedPreferences ("s_*"); normalize(); detecção de sensível
  data/Updater.kt        consulta Releases do GitHub, baixa APK via DownloadManager
  data/Context.kt        textos fixos: ACTORS, PEOPLE, GLOSSARY, REGION_CONTEXT, SOURCE_PROFILES
  data/Milestones.kt, Conflicts.kt, WhatsNew.kt (CHANGELOG por versionCode)
  data/Translator.kt     ML Kit en/he/ar→pt; idioma detectado pelo alfabeto; um modelo por idioma
  data/Vigil.kt          registro de vigília (alertas com data/hora, vigil.json); Bulletin.kt: boletim semanal
  data/Ranges.kt         alcance de mísseis/defesas desenhado no mapa; Weapons.kt: fichas das armas
  data/Cities.kt         cidades para o mapa "por cidade"; Truces.kt: contador de tréguas
  data/Quotes.kt         "quem disse o quê" (aspas + verbo de fala + uma pessoa-chave, quotes.json)
  data/Personal.kt       Dossiês (assunto + termos → linha do tempo automática) e Minhas previsões
  ui/PersonalScreens.kt  sub-abas Dossiês e Previsões da aba Salvos; ui/Watch.kt: olho animado e ticker
  data/Radar.kt          espelho do radar.json (+ military, carriers, frontline; polígonos em frontline.json); Agenda.kt (agenda, "neste dia", relógios das capitais,
                         nascer/pôr do sol); Power.kt (quem manda em cada lado, linha do tempo dos reféns)
  data/RadarChanges.kt   "o que mudou desde a última visita" no Radar (retrato salvo ao sair da aba)
  data/Alerts.kt         sirenes (ao vivo: parseLiveSirens), sismos, alertas de viagem, ultimatos (Deadline)
  data/Usage.kt          contagem anônima de uso (GoatCounter /count, 1×/dia, user-agent de navegador; desligável
                         em Ajustes → Privacidade). O código da conta fica em web/app-config.json (vai para o site;
                         vazio = não conta). Painel e página de download contam com o mesmo código
  data/Course.kt         Curso rápido (lições); ui/CourseScreen.kt
  data/WorldData.kt      espelho de refugees/hunger/gas/press/sanctionlist do radar.json; ui/WorldDataCards.kt: cartões
                         (Números: fome, deslocados, imprensa; Mercados: gás). data/Arms.kt (SIPRI 2020–24, fixo) e
                         data/Bases.kt (bases estrangeiras, camada "Bases" do mapa). ui/WorldScreens.kt: Quem está
                         sancionado?, Quem arma quem, Verificar imagem (compartilhar foto → busca reversa + EXIF) e
                         Antes e depois por satélite (WMS da NASA GIBS: HLS 30 m e VIIRS)
  ui/AlertScreens.kt     telas Sirenes (consulta a cada 5 s só com o app na frente) e Ultimatos; ui/AlertCards.kt:
                         cartões do Radar (sirenes, sismógrafo, viagem) e LocalGo (abre rotas de qualquer tela)
  ui/Trust.kt            ConfidenceCard (+ árvore de fontes), SpreadCard, TensionBreakdown (regions.parts/why),
                         SinceLastVisitCard (visita gravada em prefs "visit_<tag>"), IncidentsCard
  ui/MethodScreens.kt    Contradições ao vivo e "Como sabemos?" (metodologia: mudou uma regra, atualize o texto)
  data/Rules.kt          regras de alerta da pessoa (tensão, Radar em alerta, mercado, palavra; avisa na virada),
                         radarAlertTags(); ui/RulesScreen.kt. data/Alliances.kt: "quem apoia quem" (fixo até 2025)
  ui/WorldCards.kt       cartões ONU, tempo, placar aéreo e sirenes por hora; ui/MonthlyBulletin.kt: boletim mensal
  widget/AlertWidgets.kt widgets Próximo prazo e Sirenes (Glance)
  ui/ExtraScreens.kt     TopicScreen (tema), Comparar (regiões/datas), Status das fontes, Quem apoia quem (grafo em Canvas),
                         Termômetro diplomático (+ DiplomacyCard) e BrazilImpactCard ("E o Brasil?")
  ui/HomeScreen.kt       search()/parseSearch(): filtros região:, fonte:, lado:, antes:, depois:, tipo:
  Compartilhar           intent SEND text/plain → Repository.findShared (link, depois palavras do título/og:title)
  ui/ScaleScreen.kt      "E se fosse no Brasil?"; ui/SmallMap.kt: mapa osmdroid com as proteções do MapScreen
  ui/RadarScreen.kt      aba Radar: Sensores, Mercados, Números, Vozes, Análise, Contexto; em Sensores os
                         cartões em alerta sobem e os calmos vêm recolhidos
  ui/Cards.kt            ArgosCard: cartão padrão (título, "fonte · atualizado há X", ⓘ com a explicação,
                         recolhível). Cartão novo usa ele; texto longo de fonte vai no `info`, não no corpo
  ui/Tools.kt            Ferramentas (TOOL_GROUPS) e painel da tela Hoje. Navegação por rotas em
                         MainActivity.go(): "clock", "radar:N", "map:trend", "library:N", "region:TAG"...
                         (mesmos nomes dos atalhos do ícone/notificações)
  ui/SavedScreen.kt      aba Biblioteca: Salvos, Dossiês, Previsões, Arquivo (ArchiveScreen.kt), Lidas
  sync/                  SyncWorker (30 min), DigestWorker, Notifier, NotificationActionReceiver
  ui/                    Compose; MainActivity faz a navegação por estado (sem navigation-compose)
  widget/                Glance: TopWidget, CompactWidget, RegionWidget (+ configuração), ClockWidget
web/                     página de download (index.html, icon.svg), status.html (fontes e sensores),
                         carrossel/ (ferramenta pessoal do dono, sem link em lugar nenhum e noindex: escolhe uma
                         história do feed.json e gera 4 slides 1080×1350 editáveis em Canvas para o Instagram
                         (notícia, o que aconteceu, "siga para mais" com texto salvo no navegador, fontes; o dono
                         pediu curto: não voltar a pôr números/lados/contexto/mapa); *asteriscos* = destaque dourado
                         (layout()/fitText), selo da capa, frase-chave, textura, barra de progresso, botão Legenda;
                         imagens: Wikimedia Commons (API com origin=*, crédito e licença desenhados), arquivo do
                         aparelho ou foto da notícia só se o site mandar CORS (senão o canvas não exporta); tradução pelo Translator do Chrome com o cache do painel); build.py copia para a gh-pages →
                         GitHub Pages em abugdn.github.io/Argos/. Busca o APK mais novo pela API
  painel/                painel para PC (abugdn.github.io/Argos/painel/): globo 3D (MapLibre 5, em
                         vendor/, sem CDN) com regiões/tensão, cidades, focos, aviões, porta-aviões,
                         frente e alcances; lê os mesmos JSON do app. Tradução pela API Translator do
                         Chrome (no computador). geo.json é gerado por backend/wid/webgeo.py a partir dos
                         arquivos Kotlin do app (Models, Cities, Ranges, Context, Milestones, Conflicts,
                         Images, MapScreen.REGION_POINTS, Agenda.CAPITALS, Power): região nova no app (TAG_LABELS,
                         REGION_POINTS, REGION_FLAGS, REGION_CONTEXT) aparece no painel; o mapa país→região é
                         COUNTRY_TAG em features.js. Mudou esses dados no
                         app, o site acompanha. app.js = globo, painéis, busca; features.js = dia/noite,
                         arcos entre regiões, estreitos, ficha do país (Wikidata/Wikipédia no navegador),
                         comparar, arquivo, atalhos, mercados e imagem para compartilhar. Marcadores HTML
                         atrás do globo são escondidos à mão (hideBackside), o MapLibre só os esmaece.
                         detail.js = detalhe grátis e sem chave: relevo 3D (AWS terrarium), prédios/ruas/
                         nomes (OpenFreeMap), nuvens (EUMETSAT WMS, 15 min), luzes das cidades só na noite
                         (lights.geojson + filtro "within"), fundo "NASA de ontem" (GIBS); "Alta qualidade"
                         liga relevo+prédios, de fábrica só em PC forte. Cidades 3D realistas (Google/Cesium)
                         exigem chave: não usar.
                         motion.js = animações (abertura, flyCam com inclinação, varredura, ping de notícia
                         nova, arcos que se desenham, contorno do país sob o mouse, contagens, cascata,
                         fogo, frente, alvorada, retícula, urgente datilografado); envolve funções de
                         app.js/features.js reatribuindo o nome global (load = ..., showDetail = ...).
                         Voo de câmera: use flyCam(), não map.flyTo
.github/workflows/
  update-feed.yml        coleta; ciclos de ~5 h (11 rodadas × 30 min); cada rodada pega o backend novo
  build-android.yml      compila e publica release v1.0.<run_number> a cada push em android/
```

## Armadilhas conhecidas (já custaram builds)

- **Não havia Android SDK na sessão em nuvem**: o app só era compilado no CI. No PC dá para compilar
  localmente (`cd android && ./gradlew assembleDebug`); ainda assim confira o Actions depois do push.
- **Sessão em nuvem sem acesso à maioria dos sites** (o proxy recusa): para saber se uma fonte funciona,
  use o workflow "Diagnóstico de fontes" (edite `.github/diagnose/urls.txt` e dê push; o log mostra
  código HTTP, CORS e trechos). No PC com internet dá para testar direto com curl.
- **Aspas dentro de strings Kotlin**: `"texto "entre aspas""` quebra o build. Use `\"` ou aspas
  tipográficas “ ”.
- **Compose × Glance**: `Text`, `padding`, `fillMaxSize`… existem nos dois. Não misture imports dos dois
  no mesmo arquivo (por isso `RegionWidgetConfigActivity.kt` é separado de `RegionWidget.kt`).
- **Lambda final**: em composables com `onDismiss` etc., o parâmetro de função precisa ser o último
  para aceitar `{ }` fora dos parênteses.
- **osmdroid se destrói ao sair da tela** (`destroyMode` padrão): o `MapView` guardado no `remember`
  voltava quebrado e criar `Polygon`/`Marker` nele derrubava o app. Fica `setDestroyMode(false)` e o
  `onDetach()` só no `DisposableEffect`; o `update` do mapa roda dentro de `runCatching`.
- **osmdroid (mapa)** desenha fora dos próprios limites; o `MapView` fica dentro de um `FrameLayout`
  com `clipChildren` + `Modifier.clipToBounds()`. Não remova.
- **versionCode = run_number do workflow "App Android"**. A entrada nova do `CHANGELOG` usa o número
  esperado (última execução + 1). Build local sem `WID_VERSION_CODE` sai como 1.
- **Cron do GitHub é pouco confiável** (rodava a cada ~6 h). O ciclo longo em `update-feed.yml` resolve;
  se mudar o backend, o ciclo em andamento pega o código novo na rodada seguinte.
- **Times of Israel** bloqueia os IPs do GitHub (403); cai para Google News (links de redirecionamento,
  sem resumo). `lead()` evita usar esses links como título do grupo.
- **Tradução**: `data/TranslationGlossary.kt` corrige o ML Kit (pré: siglas/termos ambíguos em inglês
  viram a forma por extenso; pós: pt-PT→pt-BR, termos militares, nomes em inglês, concordância com
  "Estados Unidos"). Testes em `android/app/src/test/` (rodam no CI). Ao mudar regras, aumente
  `VERSION` — o app descarta e refaz as traduções guardadas.
- **Radar**: do GitHub, Yahoo dá 429, Stooq pede JavaScript, FRED não responde (Brent vem da tabela do
  EIA) e crisisgroup.org dá 403 (CrisisWatch vem da cópia do Internet Archive). Para testar uma fonte
  nova a partir dos servidores do GitHub: `.github/diagnose/urls.txt` + workflow "Diagnóstico de fontes". Focos de calor precisam do secret `FIRMS_MAP_KEY`; OpenSky aceita
  `OPENSKY_CLIENT_ID/SECRET` opcionais. A cobertura do OpenSky no Oriente Médio é pequena (poucos aviões
  visíveis); por isso o status só sai depois de 3 dias de linha de base.
- **Regex em loop trava o app**: nunca compile `Regex(...)` dentro de funções chamadas por notícia
  (cartão de pessoa travava assim). Use `wordRegex()` (Settings.kt, com cache) e tire buscas no feed
  inteiro da thread principal (`produceState` + `Dispatchers.Default`).
- **Fotos**: `WIKI_TITLES`/`REGION_FLAGS` em `data/Images.kt`; Wikimedia exige o user-agent do Coil
  configurado em `WidApp.newImageLoader()`. **Cabeçalhos HTTP só em ASCII**: um acento no User-Agent
  fez o OkHttp derrubar o app a cada foto (há teste).
- **Teste de fumaça** (`.github/workflows/smoke.yml` + `.github/smoke/`): emulador no Actions que navega
  pelo app e falha se houver crash. Rode (Actions → Teste de fumaça → Run workflow) antes de publicar
  mudanças grandes de UI. Crashes no celular aparecem na próxima abertura (`CrashLog`).
- **Chave de LazyColumn única**: várias ferramentas levam à mesma rota (ex.: radar:2); a lista usa rota + nome.
  Chave repetida derruba o app ao rolar (crash da 1.0.42). Na dúvida, não use chave.
- Extração de números: idades ("14-year-old"), anos e porcentagens não são vítimas (há teste).
- **Tela Hoje configurável**: blocos em `HOME_BLOCKS`/`PANEL_ITEMS` (Settings.kt), ordem e ocultos salvos
  em `s_home_order`/`s_home_hidden`/`s_panel_*`. Bloco novo: acrescente no mapa (entra no fim da ordem de
  quem já tinha configurado) e trate o id em `HomeScreen`/`HomePanel`.
- **Painel web**: teste local servindo uma cópia da gh-pages + `copy_web()` com `python -m http.server`
  e Playwright (Chromium com `--use-angle=swiftshader` para o WebGL). Os blocos de mapa (Esri; o CARTO passou a exigir chave e mostra "API KEY REQUIRED")
  não carregam na sessão em nuvem; o fundo vetorial (countries.geojson) aparece mesmo assim.
- Textos de contexto/pessoas/marcos vão até 2025 e mostram aviso de data; ao atualizar, mantenha o tom
  neutro e factual.

## Checklist de uma atualização

1. Backend mudou? `cd backend && python -m pytest -q` e, se possível, rode `wid.build` em cima de uma
   cópia da `gh-pages` para ver o resultado com dados reais.
2. App mudou? Nova entrada no topo de `CHANGELOG` (`WhatsNew.kt`) com emojis e frases curtas.
3. Commit em português, push, e confira os dois workflows no Actions.
4. Mudou o formato do `feed.json`? Campos novos no app sempre com valor padrão (o app antigo e o novo
   convivem com o mesmo JSON).
