# WID — Plano do app de notícias de guerra

> Documento histórico: o plano original do projeto (quando ainda se chamava WID). O estado atual está no
> [`CLAUDE.md`](CLAUDE.md) (seção "Onde estamos") e o passo a passo de uso no [`README.md`](README.md).

App mobile de notícias sobre conflitos no Oriente Médio e no mundo, com foco em Israel.
Público: 1 dono + até 4 amigos. Requisitos: **custo zero**, **mobile**, **widget com a notícia principal do dia**.

## Decisões tomadas

| Pergunta | Decisão |
|---|---|
| Plataforma | **Android** (todos) |
| Repo público | **Sim** — `AbuGDN/Argos` (antigo WID) já é público |
| Guardar notícias | **Sim, texto completo** — guardado **no celular** (ver §5) |
| Notificações | **Sim** — geradas pelo próprio app (sem serviço externo) |
| Idioma | **Português**; notícias estrangeiras traduzidas no celular |
| Escopo | **Notícias de guerra em geral**, com peso extra para Israel/Oriente Médio |

---

## 1. Decisão mais importante: Android vs iPhone

| | Android | iPhone |
|---|---|---|
| Instalar sem loja | APK direto, grátis | Precisa conta Apple Developer (**US$ 99/ano**) ou conta grátis que **expira a cada 7 dias** e exige Mac + Xcode |
| Widget | Grátis, sem restrição | WidgetKit (Swift), mesmo problema de assinatura |
| Custo zero | ✅ | ❌ na prática |

**Recomendação:** Android nativo. Se algum amigo tiver iPhone, a alternativa é ele usar a versão web (PWA) do mesmo feed, sem widget.

---

## 2. Arquitetura (tudo no free tier)

```
 ┌─────────────────────────── GitHub (grátis) ───────────────────────────┐
 │                                                                        │
 │  GitHub Actions (cron a cada 30 min)                                   │
 │     └─ script Python                                                   │
 │          1. baixa ~15 feeds RSS                                        │
 │          2. filtra por palavras-chave (Israel, Gaza, Irã, Hezbollah…)  │
 │          3. remove duplicatas / agrupa a mesma história                │
 │          4. calcula "notícia principal do dia"                         │
 │          5. grava feed.json + top.json                                 │
 │                                                                        │
 │  branch gh-pages → raw.githubusercontent.com/AbuGDN/Argos/gh-pages/…     │
 │  GitHub Releases → APK do app (build automático)                       │
 └────────────────────────────────────────────────────────────────────────┘
                 │ HTTPS (JSON estático)
                 ▼
 ┌──────────── Celular Android ────────────┐
 │  App (Kotlin + Jetpack Compose)          │
 │  Widget (Jetpack Glance)                 │
 │  WorkManager atualiza a cada 30–60 min   │
 │  Baixa o texto completo e guarda (Room)  │
 │  Traduz EN→PT no aparelho (ML Kit)       │
 │  Notificação local p/ notícia "urgente"  │
 └──────────────────────────────────────────┘
```

Por que um "backend" em vez do app ler os RSS direto: o processamento (dedupe, ranking) fica num lugar só, os 5 celulares veem exatamente a mesma "principal do dia", e o widget só precisa baixar um JSON pequeno (economiza bateria).

### Custos

| Peça | Serviço | Custo |
|---|---|---|
| Coleta/processamento | GitHub Actions | R$ 0 (repo público: ilimitado; privado: 2.000 min/mês — cron de 30 min usa ~1.450) |
| Hospedagem do JSON | branch `gh-pages` via raw.githubusercontent.com (ou GitHub Pages) | R$ 0 |
| Distribuição do app | GitHub Releases + [Obtainium](https://github.com/ImranR98/Obtainium) p/ auto‑update | R$ 0 |
| Notificações | Locais, disparadas pelo WorkManager | R$ 0 |
| Tradução | ML Kit Translation on‑device (offline, ilimitado) | R$ 0 |
| Texto completo | Extraído no celular (Readability) | R$ 0 |

---

## 3. Fontes (RSS)

Misturar perspectivas para não ficar enviesado. Lista real em `backend/config/sources.yaml`; a saúde de cada fonte é publicada em `sources_status.json` a cada rodada, para podar as que quebrarem.

- **Israel:** Times of Israel, Jerusalem Post, Ynetnews, Haaretz (manchetes)
- **Mundo árabe:** Al Jazeera English, Al‑Monitor
- **Internacionais:** BBC Middle East, The Guardian (Middle East / World), AP (via Google News), DW
- **Em português:** G1 Mundo, Folha Mundo, CNN Brasil Internacional
- **Guerras em geral:** Kyiv Independent + feeds "mundo" dos veículos acima, filtrados por termos de guerra
- Google News RSS **não** é usado: os links são redirecionamentos que impedem baixar o texto completo.

---

## 4. Processamento (script Python) — ✅ implementado em `backend/`

1. **Coleta** — `feedparser` + `httpx`, timeout curto; fonte que falhar é ignorada.
2. **Filtro** (`config/keywords.yaml`) — entra se tiver ≥1 termo de guerra (PT/EN) **e** (uma região/ator **ou** ≥2 termos de guerra). Assim "ataque" no futebol ou a bolsa de Tel Aviv ficam de fora. Cada notícia recebe tags: israel, gaza, cisjordania, libano, ira, iemen, siria, iraque, ucrania_russia, sudao, asia, africa, otan.
3. **Agrupamento** — tokens do título + dicionário PT↔EN (Beirute→beirut, ataca→attack…), para a mesma história em português e inglês virar 1 grupo.
4. **Ranking**
   - `day_score = soma dos pesos das fontes × relevância × bônus da região` (Israel 1.5, Gaza 1.4…)
   - `score` (feed) = `day_score` com meia-vida de 6h
   - **Principal do dia** = maior `day_score` das últimas 24h
   - Título do grupo: prefere a versão **em português** quando algum veículo brasileiro cobriu
5. **Urgente** — grupo novo com ≥5 veículos em 2h (ou "urgente/breaking" no título + 2 veículos) → `urgent: true`, o app notifica.
6. **Saída** (branch `gh-pages`)
   - `feed.json` — últimas 48h, agrupadas, com tags e todos os links
   - `top.json` — principal + 4 secundárias (o que o widget baixa)
   - `history/AAAA-MM-DD.json` + `history/index.json` — principal de cada dia
   - `sources_status.json` — quais fontes funcionaram na última rodada

---

## 5. App Android

- **Stack:** Kotlin, Jetpack Compose, Ktor/OkHttp, kotlinx.serialization, Room, WorkManager, **Glance** (widget), Coil, **ML Kit Translation**, Readability4J.
- **Texto completo:** ao sincronizar, o app baixa a página de cada notícia, extrai o texto (Readability4J) e guarda no Room. Fica no celular (não no repo público, que seria republicar matérias abertamente). Sites com paywall (Haaretz, parte da Folha) ficam só com o resumo.
- **Tradução:** título, resumo e texto em inglês são traduzidos para PT pelo ML Kit, offline, sem limite. O modelo EN→PT (~30 MB) baixa uma vez só, no Wi‑Fi. Botão "ver original" em cada notícia.
- **Notificações:** a cada sincronização, grupos com `urgent: true` ainda não vistos viram notificação (título já traduzido). Opção de receber também "principal do dia" às 8h. Sem servidor de push: atraso de até ~30 min, suficiente para o uso.
- **Telas**
  1. **Hoje** — card grande com a principal do dia + lista agrupada
  2. **Filtros** — chips por conflito (Israel/Gaza, Líbano, Irã, Iêmen, Ucrânia, Outros)
  3. **Detalhe** — texto completo traduzido + lista de veículos que cobriram + "abrir no site"
  4. **Arquivo** — principal de cada dia anterior
- **Widget** (2 tamanhos)
  - Pequeno: título da principal + horário
  - Médio: título + fonte + nº de veículos + 2 manchetes secundárias
  - Toque abre o app no detalhe. Atualização via WorkManager (mínimo do Android é 15 min; usar 30–60).
- **Idioma:** tudo em português por padrão.

---

## 6. Distribuição

1. Workflow `build-apk.yml` compila e assina o APK a cada tag `v*` e publica no GitHub Releases.
2. Cada amigo instala o Obtainium uma vez, aponta para o repo, e recebe atualizações automáticas.
3. Keystore de assinatura guardada em GitHub Secrets.

---

## 7. Pontos de atenção

- **Repo público:** o que é publicado é só título, resumo do RSS e link. O texto completo fica apenas nos celulares.
- **Cron do GitHub** atrasa às vezes (5–15 min). O GitHub também desliga crons de repos parados há 60 dias; o push na `gh-pages` a cada rodada conta como atividade.
- **A `gh-pages` é recriada com 1 commit a cada rodada** para o repositório não crescer infinitamente.
- **Crons só rodam na branch padrão.** Hoje a branch padrão é `claude/adoring-hamilton-kr5stn`; se mudar para `main`, o cron segue a `main`.
- **Qualidade da tradução do ML Kit** é boa para manchetes, razoável para texto longo. Se incomodar, dá para testar um LLM com free tier só nos títulos.
- **Imagens:** usar a URL que vem no RSS; sem re-hospedar.

---

## 8. Fases

| Fase | Entrega | Pronto quando |
|---|---|---|
| 0 | Repo, `sources.yaml`, decisão público/privado | — |
| 1 ✅ | Script Python + Actions publicando `feed.json`/`top.json` | JSON atualiza sozinho a cada 30 min |
| 2 ✅ | App Android: tela Hoje + detalhe + texto completo + tradução | Lê e traduz o feed no celular |
| 3 ✅ | Widget Glance + notificações | Principal do dia na home; alerta de urgente |
| 4 ✅ | Build automático + Releases (Obtainium) | Amigos instalam e atualizam sozinhos |
| 5 ✅ | Arquivo, mapa, salvos/compartilhar, linha do tempo, ajustes de notificação, resumo diário, tema claro, widget compacto, limpeza de resumos, Times of Israel via Google News | — |
| 6 | (fora do escopo por ora) tradução com LLM free tier no servidor | — |

---

## 9. Como instalar o app (você e os amigos)

1. Baixe o `.apk` mais recente em https://github.com/AbuGDN/Argos/releases e abra (o app não está na Play Store).
   O Android vai pedir para permitir "instalar apps desconhecidos" pelo navegador — permita.
2. Para atualizar, basta baixar e abrir o APK novo por cima (os dados ficam).
   Opcional: o **Obtainium** (também fora da Play Store: https://github.com/ImranR98/Obtainium/releases)
   acompanha este repositório e avisa/instala as versões novas sozinho.
3. Abra o WID uma vez, aceite as notificações e puxe para baixo para carregar.
4. Widget: na tela Hoje, toque em **Adicionar widget** (funciona mesmo se o launcher não listar o widget).
   Ou pela tela inicial: segure → Widgets → WID → "Principal do dia". Aumente a altura para ver 2 secundárias.
5. Recomendado: em Configurações → Apps → WID → Bateria, deixe **sem restrição**, para as notificações não atrasarem.

A cada mudança em `android/`, o GitHub compila um APK novo e publica em Releases; o Obtainium avisa e atualiza.
A chave de assinatura (`android/app/wid.jks`) fica no repositório de propósito: toda build tem a mesma assinatura e atualiza por cima. Só quem tem acesso de escrita ao repo consegue publicar releases.

## 10. Como rodar o backend localmente

```bash
cd backend
pip install -r requirements.txt -r requirements-dev.txt
python -m pytest -q                 # testes com feeds de exemplo
python -m wid.build --out ../site   # baixa os feeds de verdade e gera os JSONs
```
