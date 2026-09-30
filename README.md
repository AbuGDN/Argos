# Argos — cem olhos sobre a guerra

> Antes se chamava **WID**. O nome do pacote Android (`com.abugdn.wid`), a pasta do código e a variável
> `WID_VERSION_CODE` mantêm o nome antigo de propósito: trocar o pacote obrigaria todo mundo a desinstalar.

App Android pessoal (para até 5 pessoas) com notícias de guerra, foco em Israel/Oriente Médio, tudo em
português, com widget, notificações e **custo zero**. O "servidor" é o próprio GitHub: um workflow coleta
~33 feeds RSS a cada 30 min, além de dezenas de sensores e bases de dados abertas (o "Radar"),, agrupa e analisa as notícias e publica JSONs na branch `gh-pages`; o app lê
esses JSONs, traduz no próprio celular e guarda tudo offline.

- Repositório: https://github.com/AbuGDN/Argos (público)
- Releases (APK): https://github.com/AbuGDN/Argos/releases
- Página de download: https://abugdn.github.io/Argos/
- Dados publicados: `https://raw.githubusercontent.com/AbuGDN/Argos/gh-pages/feed.json` (e os outros arquivos da tabela abaixo)
- Painel para PC: https://abugdn.github.io/Argos/painel/ · Status das fontes: https://abugdn.github.io/Argos/status.html
- Versão atual do app: **1.0.44** (30/09/2026). Onde o trabalho parou e como seguir: [`CLAUDE.md`](CLAUDE.md), seção "Onde estamos"
- Histórico de decisões e fases: [`PLANO.md`](PLANO.md)
- Contexto para o Claude Code: [`CLAUDE.md`](CLAUDE.md)

---

## Levar o projeto para o seu PC

O projeto inteiro já está no GitHub; "transferir" é só clonar. Nada na nuvem depende desta sessão: a
coleta de notícias e a compilação do APK continuam rodando no GitHub Actions.

### 1. Instalar as ferramentas

| Para quê | O quê | Observação |
|---|---|---|
| Tudo | [Git](https://git-scm.com/downloads) | |
| Servidor (backend) | [Python 3.11+](https://www.python.org/downloads/) | No Windows, marque "Add python.exe to PATH" |
| App Android | [Android Studio](https://developer.android.com/studio) | Já traz o JDK 17 e o Android SDK. Instale o **SDK Platform 35** pelo SDK Manager |
| Assistente | [Claude Code](https://docs.anthropic.com/en/docs/claude-code) | Opcional: `npm install -g @anthropic-ai/claude-code` (precisa de Node 18+) |

### 2. Clonar

```bash
git clone https://github.com/AbuGDN/Argos.git
cd Argos
git checkout claude/adoring-hamilton-kr5stn   # branch onde está todo o código (é a padrão hoje)
```

> **Recomendado:** criar uma branch `main` e torná-la a padrão (veja [Branches](#branches)).

### 3. Rodar o servidor (backend) localmente

```bash
cd backend
python -m venv .venv
# Linux/macOS:
source .venv/bin/activate
# Windows (PowerShell):
.venv\Scripts\Activate.ps1

pip install -r requirements.txt -r requirements-dev.txt
python -m pytest -q                  # testes com feeds de exemplo (sem internet)
python -m wid.build --out ../site    # baixa os feeds de verdade e gera os JSONs em ../site
```

A pasta `site/` é ignorada pelo Git. Para testar em cima dos dados reais já publicados:
`git fetch origin gh-pages && git worktree add site origin/gh-pages` antes de rodar o `wid.build`.

### 4. Abrir e rodar o app

1. Android Studio → **Open** → escolha a pasta `android/` (não a raiz do repositório).
2. Espere o Gradle sincronizar (a primeira vez baixa ~1 GB de dependências).
3. Rode num emulador ou no celular (ative "Depuração USB" nas Opções do desenvolvedor).

Pela linha de comando:

```bash
cd android
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
# APK em android/app/build/outputs/apk/release/app-release.apk
```

> ⚠️ **Número da versão em builds locais.** O `versionCode` vem da variável `WID_VERSION_CODE` (no CI é o
> número da execução do workflow "App Android"). Sem ela, a build local sai como versão 1 e o Android
> **recusa instalar por cima** de uma versão maior. Para testar no seu celular sem desinstalar, use o
> mesmo número da última release: `WID_VERSION_CODE=44 ./gradlew installRelease`
> (PowerShell: `$env:WID_VERSION_CODE=44; .\gradlew.bat installRelease`).
> **Nunca** instale um número maior que o da próxima release do CI, senão as atualizações automáticas
> param de instalar (o Android não aceita "voltar" de versão).

### 5. Publicar uma versão nova do app

Não precisa compilar no PC: **todo push que mexe em `android/` compila e publica uma release** sozinho
(workflow `build-android.yml`). O app de cada pessoa detecta a versão nova ao abrir e oferece atualizar.

Antes do push, acrescente as novidades no topo de `CHANGELOG` em
`android/app/src/main/java/com/abugdn/wid/data/WhatsNew.kt`, com o `versionCode` **esperado**:
o número da última execução de "App Android" em https://github.com/AbuGDN/Argos/actions + 1
(se uma build falhar, o número pula; o app aceita isso, só o rótulo da versão antiga fica diferente).

---

## Como funciona

```
GitHub Actions: update-feed.yml (ciclos de ~5 h, uma coleta a cada 30 min)
  backend/ (Python)
    fetch.py     baixa os RSS (config/sources.yaml), limpa resumos, trata Google News
    keywords.py  filtro "é notícia de guerra?" + tags de região (config/keywords.yaml)
    cluster.py   agrupa a mesma história entre veículos e idiomas (PT↔EN), scores
    analysis.py  números divergentes, enquadramento, tensão/anomalias, sagas, quem noticia primeiro
    build.py     monta e grava tudo na branch gh-pages (1 commit, force-push)
        │
        ▼  raw.githubusercontent.com/AbuGDN/Argos/gh-pages/*.json
App Android (Kotlin + Jetpack Compose)                       build-android.yml → Releases (APK)
  data/    Repository (baixa feed, traduz com ML Kit, texto completo com Readability4J),
           Storage (JSON na pasta privada), Settings, Updater (Releases do GitHub), conteúdo fixo
           (Context/Milestones/Conflicts/WhatsNew)
  sync/    SyncWorker (30 min), DigestWorker (resumo diário/semanal), Notifier, ações de notificação
  ui/      telas Compose (Hoje, Mapa, Arquivo, Salvos, notícia, região, ajustes...)
  widget/  widgets Glance (principal, compacto, por região, relógio, próximo prazo, sirenes)
web/       página de download, status.html, painel/ (globo 3D para PC) e carrossel/ (ferramenta pessoal)
```

### Arquivos publicados na `gh-pages`

| Arquivo | Conteúdo |
|---|---|
| `feed.json` | Histórias das últimas 48 h (grupos com todos os artigos), principal do dia, `regions` (tensão/anomalias) |
| `top.json` | Principal do dia + 4 secundárias |
| `history/AAAA-MM-DD.json`, `history/index.json` | Principal de cada dia |
| `stats/daily.json` | Histórias iniciadas por dia e região (30 dias) |
| `stats/first.json` | Ranking de quem publicou primeiro |
| `sagas.json` | Estado das sagas (histórias ligadas entre dias) |
| `sources_status.json` | Quais feeds funcionaram na última coleta |
| `radar.json` | Aba Radar: internet, espaço aéreo, focos de calor, estreitos, cotações, números humanitários, perdas, fontes oficiais, sanções, análises, CrisisWatch, checagens, previsões, aviões militares, porta-aviões, frente, sirenes, sismos, alertas de viagem, ONU, tempo, deslocados, fome, gás, imprensa e lista de sanções (status de cada seção em `status`) |
| `frontline.json` | Polígonos da linha de frente na Ucrânia (DeepStateMap), baixados pelo app só com a camada ligada |
| `sirens_cities.json` | Dicionário de cidades das sirenes em Israel |
| `airwar.json`, `deadlines.json`, `diplomacy.json` | Placar aéreo da Ucrânia, ultimatos e termômetro diplomático |
| `sanctions.tsv.gz` | Lista consolidada de sanções (OpenSanctions), baixada pelo app só na busca |
| `painel/`, `carrossel/`, `status.html`, `index.html`, `app-config.json` | Site (copiado de `web/`); `app-config.json` tem o código do GoatCounter |

A `gh-pages` é **gerada**: nunca edite à mão (é recriada com force-push a cada coleta).

---

## Tarefas comuns

| Quero… | Onde |
|---|---|
| Adicionar/remover um veículo | `backend/config/sources.yaml` (+ perfil em `SOURCE_PROFILES`, `data/Context.kt`) |
| Ajustar o que conta como notícia de guerra ou uma região | `backend/config/keywords.yaml` (+ `TAG_LABELS` em `Models.kt`, ponto em `MapScreen.kt`) |
| Mudar textos de contexto, pessoas, glossário | `android/.../data/Context.kt`, `Milestones.kt`, `Conflicts.kt` |
| Mudar países, zonas e fontes do Radar | `backend/config/radar.yaml` |
| Testar se uma fonte nova responde dos servidores do GitHub | `.github/diagnose/urls.txt` + push (workflow "Diagnóstico de fontes") |
| Ligar os focos de calor (NASA) | Crie a chave grátis em firms.modaps.eosdis.nasa.gov/api/map_key e salve como secret `FIRMS_MAP_KEY` (Settings → Secrets and variables → Actions) |
| Forçar uma coleta agora | Actions → "Atualizar feed" → **Run workflow** |
| Ver se os feeds estão ok | `sources_status.json` na `gh-pages` |

---

## Branches

- `claude/adoring-hamilton-kr5stn` — todo o código. **Hoje é a branch padrão** do repositório, e o
  agendamento (`schedule`) do GitHub só roda na branch padrão.
- `gh-pages` — dados gerados (não mexer).

Para usar `main` (recomendado):

```bash
git checkout -b main claude/adoring-hamilton-kr5stn
git push -u origin main
```

Depois, em GitHub → Settings → General → **Default branch**, troque para `main`. A partir daí o
agendamento roda na `main`. Os workflows rodam em push para qualquer branch (menos `gh-pages`), então
nada mais precisa mudar.

---

## Custos e limites

- Tudo grátis: repositório público = minutos de Actions ilimitados; GitHub Pages/raw grátis; tradução
  ML Kit no aparelho; mapa OpenStreetMap.
- O cron do GitHub atrasa em repositórios pequenos; por isso cada execução agendada fica ~5 h no ar
  coletando a cada 30 min (ver comentário em `update-feed.yml`).
- A chave de assinatura (`android/app/wid.jks`, senha `widnews`) está no repositório de propósito
  (app pessoal; toda build instala por cima). Só quem tem escrita no repo publica releases.
