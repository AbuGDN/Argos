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

## O projeto em uma frase

App Android pessoal de notícias de guerra (foco Israel/Oriente Médio, depois EUA e o mundo), em
português, para o dono e até 4 amigos, com **custo zero**: backend em Python rodando no GitHub Actions
que publica JSON na branch `gh-pages`; app Kotlin/Compose que lê esse JSON.

## Como o dono trabalha

- Conversa em **português**; textos de UI, notas de versão, commits e comentários em português.
- Ciclo típico: pede ideias → escolhe quais ("todas menos X") → tudo entra numa atualização. Para cada
  atualização do app: implementar, acrescentar entrada no `CHANGELOG` (`data/WhatsNew.kt`), push,
  conferir o build no Actions, responder com um resumo curto do que entrou.
- Descartou até agora: ler em voz alta (TTS), grupo/bot no Telegram, página web/PWA, tradução com LLM,
  mapa colorido por tendência, "baixar tudo offline", backup/exportação, mapa em time-lapse, modo
  "sala de situação". Não re-sugerir.
- Restrições firmes: custo zero, só Android, repo público.

## Mapa do código

```
backend/                 Python 3.11 (feedparser, httpx, PyYAML). Rodar: python -m wid.build --out ../site
  config/sources.yaml    ~33 veículos: url (ou lista de alternativas), lang pt|en|he|ar, origin, weight
  config/keywords.yaml   termos de guerra, tags de região, boost, termos de urgência
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
                         RSS oficiais/sanções/análises/checagens, CrisisWatch, Polymarket. Cada seção é
                         independente e guarda o último dado bom; baselines em stats/radar_state.json.
                         apply_signals() soma apagão/espaço aéreo fechado na tensão; link_factchecks()
  wid/build.py           orquestra; escreve feed.json (com "global" = Relógio do Argos), top.json,
                         history/, stats/daily.json (com pico de tensão do dia), sagas.json;
                         merge() guarda manchetes trocadas em article.edits
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
  data/Radar.kt          espelho do radar.json; Agenda.kt (agenda, "neste dia", relógios das capitais,
                         nascer/pôr do sol); Power.kt (quem manda em cada lado, linha do tempo dos reféns)
  ui/RadarScreen.kt      aba Radar: Sensores, Mercados, Números, Vozes, Análise, Contexto
  sync/                  SyncWorker (30 min), DigestWorker, Notifier, NotificationActionReceiver
  ui/                    Compose; MainActivity faz a navegação por estado (sem navigation-compose)
  widget/                Glance: TopWidget, CompactWidget, RegionWidget (+ configuração), ClockWidget
web/                     página de download (index.html, icon.svg); build.py copia para a gh-pages →
                         GitHub Pages em abugdn.github.io/Argos/. Busca o APK mais novo pela API
.github/workflows/
  update-feed.yml        coleta; ciclos de ~5 h (11 rodadas × 30 min); cada rodada pega o backend novo
  build-android.yml      compila e publica release v1.0.<run_number> a cada push em android/
```

## Armadilhas conhecidas (já custaram builds)

- **Não havia Android SDK na sessão em nuvem**: o app só era compilado no CI. No PC dá para compilar
  localmente; ainda assim confira o Actions depois do push.
- **Aspas dentro de strings Kotlin**: `"texto "entre aspas""` quebra o build. Use `\"` ou aspas
  tipográficas “ ”.
- **Compose × Glance**: `Text`, `padding`, `fillMaxSize`… existem nos dois. Não misture imports dos dois
  no mesmo arquivo (por isso `RegionWidgetConfigActivity.kt` é separado de `RegionWidget.kt`).
- **Lambda final**: em composables com `onDismiss` etc., o parâmetro de função precisa ser o último
  para aceitar `{ }` fora dos parênteses.
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
- **Radar**: Yahoo Finance responde 429 aos IPs do GitHub e o crisisgroup.org/crisiswatch dá 403 (há
  alternativas na config). Focos de calor precisam do secret `FIRMS_MAP_KEY`; OpenSky aceita
  `OPENSKY_CLIENT_ID/SECRET` opcionais. A cobertura do OpenSky no Oriente Médio é pequena (poucos aviões
  visíveis); por isso o status só sai depois de 3 dias de linha de base.
- **Regex em loop trava o app**: nunca compile `Regex(...)` dentro de funções chamadas por notícia
  (cartão de pessoa travava assim). Use `wordRegex()` (Settings.kt, com cache) e tire buscas no feed
  inteiro da thread principal (`produceState` + `Dispatchers.Default`).
- **Fotos**: `WIKI_TITLES`/`REGION_FLAGS` em `data/Images.kt`; Wikimedia exige o user-agent do Coil
  configurado em `WidApp.newImageLoader()`.
- Extração de números: idades ("14-year-old"), anos e porcentagens não são vítimas (há teste).
- Textos de contexto/pessoas/marcos vão até 2025 e mostram aviso de data; ao atualizar, mantenha o tom
  neutro e factual.

## Checklist de uma atualização

1. Backend mudou? `cd backend && python -m pytest -q` e, se possível, rode `wid.build` em cima de uma
   cópia da `gh-pages` para ver o resultado com dados reais.
2. App mudou? Nova entrada no topo de `CHANGELOG` (`WhatsNew.kt`) com emojis e frases curtas.
3. Commit em português, push, e confira os dois workflows no Actions.
4. Mudou o formato do `feed.json`? Campos novos no app sempre com valor padrão (o app antigo e o novo
   convivem com o mesmo JSON).
