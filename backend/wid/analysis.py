"""Análises sobre o feed: números divergentes, enquadramento, tensão por região,
anomalias, sagas (a mesma história ao longo dos dias) e quem noticia primeiro."""

import json
import re
from datetime import datetime, timedelta
from pathlib import Path

from .fetch import iso, parse_iso
from .text import normalize_rtl, strip_accents, tokens

# ---------------------------------------------------------------------------
# Números de mortos e feridos
# ---------------------------------------------------------------------------

_NUM = r"(\d{1,3}(?:[.,]\d{3})+|\d+)"
_KILLED_WORDS = r"(?:mortos?|mortas?|mortes|killed|dead|deaths|people killed|pessoas mortas)"
_INJURED_WORDS = r"(?:feridos?|feridas?|wounded|injured|injuries)"
# Depois do número: não pode ser idade ("14-year-old", "14 anos"), porcentagem ou distância.
_NOT_AGE = r"\b(?!\s*(?:%|anos|years?\b|km)|-year|-ano)"
_LEAD = r"(?:at least|pelo menos|ao menos|mais de|more than|over|some|cerca de|about|nearly|quase)?\s*"

FIGURE_PATTERNS = {
    "killed": [
        re.compile(_NUM + _NOT_AGE + r"\s+(?:\w+\s+){0,2}?" + _KILLED_WORDS, re.I),
        re.compile(r"(?:kills?|killing|killed|mata|matou|matam|deixa|deixou)\s+" + _LEAD + _NUM + _NOT_AGE, re.I),
        re.compile(r"(?:death toll|número de mortos|mortos sobe para|toll rises to)\D{0,20}" + _NUM, re.I),
    ],
    "injured": [
        re.compile(_NUM + r"\s+(?:\w+\s+){0,2}?" + _INJURED_WORDS, re.I),
        re.compile(r"(?:injur(?:e|es|ed|ing)|wounds?|wounding|fere|feriu)\s+" + _LEAD + _NUM, re.I),
    ],
}


def _to_int(raw: str) -> int | None:
    digits = re.sub(r"[.,]", "", raw)
    if not digits.isdigit():
        return None
    n = int(digits)
    # Anos (2026) e números absurdos não são contagem de vítimas.
    if 1900 <= n <= 2100 or n == 0 or n > 500_000:
        return None
    return n


def extract_figures(text: str) -> dict[str, int]:
    """Maior número de mortos/feridos citado no texto (o balanço mais recente costuma ser o maior)."""
    found: dict[str, int] = {}
    for kind, patterns in FIGURE_PATTERNS.items():
        for rx in patterns:
            for m in rx.finditer(text):
                n = _to_int(m.group(1))
                if n is not None:
                    found[kind] = max(found.get(kind, 0), n)
    return found


def cluster_figures(cluster: dict) -> dict | None:
    """Números por veículo e se eles divergem. Só o título e o resumo de cada artigo."""
    by_kind: dict[str, dict[str, int]] = {}
    for a in cluster["articles"]:
        figs = extract_figures(f"{a['title']}. {a.get('summary', '')}")
        for kind, n in figs.items():
            by_kind.setdefault(kind, {})
            by_kind[kind][a["source"]] = max(by_kind[kind].get(a["source"], 0), n)
    if not by_kind:
        return None
    result = {}
    for kind, per_source in by_kind.items():
        values = set(per_source.values())
        result[kind] = {
            "by_source": per_source,
            "divergent": len(per_source) >= 2 and len(values) >= 2,
        }
    return result


# ---------------------------------------------------------------------------
# Enquadramento: palavras que cada lado usa para a mesma coisa
# ---------------------------------------------------------------------------

FRAMES = {
    "Como chamam os grupos armados": {
        "terroristas": ["terrorist", "terrorists", "terrorista", "terroristas", "terror group", "grupo terrorista"],
        "militantes": ["militant", "militants", "militante", "militantes"],
        "combatentes": ["fighters", "combatentes", "gunmen", "atiradores"],
        "resistência": ["resistance", "resistência"],
    },
    "Como chamam a ação militar": {
        "operação": ["operation", "operação", "operações", "operations"],
        "ataque": ["attack", "attacks", "ataque", "ataques", "strike", "strikes", "bombardeio", "bombing"],
        "massacre": ["massacre", "massacres", "chacina", "slaughter"],
        "agressão": ["aggression", "agressão"],
    },
    "Como chamam os territórios": {
        "ocupado(a)": ["occupied", "ocupada", "ocupado", "ocupação", "occupation"],
        "disputado(a)": ["disputed", "disputada", "disputado"],
        "Judeia e Samaria": ["judea and samaria", "judeia e samaria"],
    },
    "Como chamam as pessoas detidas": {
        "reféns": ["hostage", "hostages", "refém", "reféns"],
        "sequestrados": ["abducted", "kidnapped", "sequestrados", "sequestrado"],
        "prisioneiros": ["prisoners", "prisoner", "prisioneiros", "detidos", "detainees"],
    },
    "Como chamam o governo iraniano": {
        "regime": ["iranian regime", "regime iraniano", "regime in tehran", "regime de teerã", "ayatollah regime"],
        "governo": ["iranian government", "governo iraniano", "government in tehran", "governo de teerã"],
    },
    "Como descrevem a campanha em Gaza": {
        "genocídio": ["genocide", "genocídio"],
        "guerra": ["war in gaza", "guerra em gaza", "gaza war", "guerra de gaza"],
    },
}

_FRAME_RX = {
    group: {label: [re.compile(r"(?<!\w)" + re.escape(strip_accents(t.lower())) + r"(?!\w)") for t in terms]
            for label, terms in labels.items()}
    for group, labels in FRAMES.items()
}


def cluster_framing(cluster: dict) -> list[dict]:
    """Grupos de palavras em que origens diferentes usaram termos diferentes."""
    result = []
    for group, labels in _FRAME_RX.items():
        by_origin: dict[str, set[str]] = {}
        for a in cluster["articles"]:
            text = strip_accents(f"{a['title']} {a.get('summary', '')}".lower())
            for label, patterns in labels.items():
                if any(rx.search(text) for rx in patterns):
                    by_origin.setdefault(a.get("origin", "internacional"), set()).add(label)
        # Só é enquadramento diferente se duas origens usaram termos sem nenhum em comum.
        origins = list(by_origin)
        differs = any(
            not (by_origin[a] & by_origin[b]) for i, a in enumerate(origins) for b in origins[i + 1:]
        )
        if differs:
            result.append({"group": group, "by_origin": {o: sorted(ls) for o, ls in sorted(by_origin.items())}})
    return result


# ---------------------------------------------------------------------------
# Lados opostos
# ---------------------------------------------------------------------------

ONE_SIDE_MIN_SOURCES = 3


def cluster_sides(cluster: dict) -> str | None:
    """"opostos" quando a imprensa árabe e a israelense (ou americana) contam a mesma história;
    "um_lado" quando uma história grande (3+ veículos) só saiu na imprensa de um dos lados."""
    origins = {a.get("origin", "internacional") for a in cluster["articles"]}
    if "arabe" in origins and origins & {"israel", "eua"}:
        return "opostos"
    sources = {a["source"] for a in cluster["articles"]}
    if len(sources) >= ONE_SIDE_MIN_SOURCES and len(origins) == 1 and origins <= {"israel", "arabe"}:
        return "um_lado"
    return None


# ---------------------------------------------------------------------------
# Violação de trégua
# ---------------------------------------------------------------------------

_TRUCE_RX = [
    re.compile(p) for p in (
        r"violat\w*\W+(?:\w+\W+){0,3}?(?:ceasefire|truce)",
        r"(?:ceasefire|truce)\W+(?:\w+\W+){0,2}?(?:violation|breach)",
        r"breach\w*\W+(?:\w+\W+){0,3}?(?:ceasefire|truce)",
        r"viol\w*\W+(?:\w+\W+){0,3}?(?:cessar-fogo|tregua)",
        r"(?:cessar-fogo|tregua)\W+(?:\w+\W+){0,2}?violad",
        r"הפר\w*\W+(?:\w+\W+){0,2}?הפסקת\W+(?:ה)?אש",
        r"(?:خرق|انتهاك|انتهاكات|خروقات)\W+(?:\w+\W+){0,2}?(?:[لبو]?وقف\W+اطلاق\W+النار|\w{0,3}هدنة)",
    )
]


def truce_violation(cluster: dict) -> bool:
    """Algum veículo do grupo relata violação de cessar-fogo/trégua."""
    for a in cluster["articles"]:
        text = normalize_rtl(strip_accents(f"{a['title']} {a.get('summary', '')}".lower()))
        if any(rx.search(text) for rx in _TRUCE_RX):
            return True
    return False


# ---------------------------------------------------------------------------
# Tensão por região e anomalias
# ---------------------------------------------------------------------------

HEAVY_TERMS = [
    "missil", "misseis", "missile", "balistico", "ballistic", "nuclear", "invasao", "invasion", "invade",
    "ofensiva", "offensive", "mobilizacao", "mobilization", "declara guerra", "declares war",
    "bombardeio", "airstrike", "air strike", "porta-avioes", "aircraft carrier", "escalada", "escalation",
    "ataque em massa", "massive attack", "barrage", "retaliation", "retaliacao",
]
_HEAVY_RX = [re.compile(r"(?<!\w)" + re.escape(t)) for t in HEAVY_TERMS]
BASELINE_DAYS = 14


def level_for(score: int) -> str:
    if score >= 75:
        return "crítica"
    if score >= 50:
        return "alta"
    if score >= 25:
        return "moderada"
    return "baixa"


def region_tension(items: list[dict], stats_days: list[dict], today: str, now: datetime) -> dict:
    """Índice 0–100 por região e alerta de anomalia (ritmo das últimas 6 h muito acima do normal)."""
    history = [d for d in stats_days if d["date"] < today][-BASELINE_DAYS:]
    tags = {t for c in items for t in c["tags"]}
    result = {}
    for tag in sorted(tags):
        in_tag = [c for c in items if tag in c["tags"]]
        last24 = [c for c in in_tag if now - parse_iso(c["published"]) <= timedelta(hours=24)]
        last6 = [c for c in in_tag if now - parse_iso(c["published"]) <= timedelta(hours=6)]
        baseline = sum(d["counts"].get(tag, 0) for d in history) / len(history) if history else 0.0
        # Sem histórico ainda: compara com o próprio dia (razão 1, sem alarme).
        normal = baseline if history else max(len(last24), 1)
        ratio = len(last24) / max(normal, 1.0)

        heavy = 0
        for c in last24:
            text = strip_accents(f"{c['title']} {c.get('summary', '')}".lower())
            heavy += sum(1 for rx in _HEAVY_RX if rx.search(text))
        urgent = sum(1 for c in last24 if c.get("urgent"))
        coverage = sum(c["sources_count"] for c in last24) / len(last24) if last24 else 0

        # Partes do índice (mostradas no app em "de onde vem a tensão").
        parts = {
            "volume": round(min(ratio / 3, 1) * 40, 1),
            "escalada": round(min(heavy / 5, 1) * 25, 1),
            "urgencia": round(min(urgent / 2, 1) * 20, 1),
            "cobertura": round(min(coverage / 6, 1) * 15, 1),
        }
        score = int(round(sum(parts.values())))
        # Anomalia: o ritmo das últimas 6 h, projetado para 24 h, passa de 3× o normal.
        spike_ratio = (len(last6) * 4) / max(normal, 1.0) if history else 0.0
        result[tag] = {
            "tension": score,
            "level": level_for(score),
            "last24": len(last24),
            "baseline": round(baseline, 1),
            "spike": bool(history) and len(last6) >= 3 and spike_ratio >= 3,
            "spike_ratio": round(spike_ratio, 1),
            "parts": parts,
            "why": [
                f"{len(last24)} histórias em 24 h" + (f" ({ratio:.1f}× o normal de {baseline:.1f}/dia)" if history else " (ainda sem histórico)"),
                f"{heavy} termos de escalada (mortos, míssil, invasão...)",
                f"{urgent} histórias urgentes",
                f"{coverage:.1f} veículos por história, em média",
            ],
            "last6": len(last6),
        }
    return result


# ---------------------------------------------------------------------------
# Sagas: histórias de dias diferentes sobre o mesmo assunto
# ---------------------------------------------------------------------------

SAGA_KEEP_DAYS = 30
SAGA_GAP_DAYS = 7
SAGA_MIN_SHARED = 3
SAGA_MIN_OVERLAP = 0.35


def _cluster_tokens(c: dict) -> set[str]:
    toks = set()
    for a in c["articles"][:6]:
        toks |= tokens(a["title"])
    return toks


def update_sagas(out: Path, items: list[dict], now: datetime) -> None:
    """Liga cada história a uma saga (persistida em sagas.json) e anota `saga` no item."""
    path = out / "sagas.json"
    try:
        sagas = json.loads(path.read_text(encoding="utf-8"))["sagas"]
    except (OSError, ValueError, KeyError):
        sagas = []
    cutoff = now - timedelta(days=SAGA_KEEP_DAYS)
    sagas = [s for s in sagas if parse_iso(s["chapters"][-1]["published"]) >= cutoff]
    by_cluster = {ch["cluster_id"]: s for s in sagas for ch in s["chapters"]}

    # Das histórias mais antigas para as mais novas, para os capítulos saírem em ordem.
    for c in sorted(items, key=lambda c: c["published"]):
        if c["sources_count"] < 2:
            continue
        toks = _cluster_tokens(c)
        chapter = {
            "cluster_id": c["id"], "title": c["title"], "lang": c["lang"], "source": c["source"],
            "url": c["url"], "published": c["published"], "sources_count": c["sources_count"],
        }
        saga = by_cluster.get(c["id"])
        if saga is not None:
            for i, ch in enumerate(saga["chapters"]):
                if ch["cluster_id"] == c["id"]:
                    saga["chapters"][i] = chapter
            continue
        best, best_overlap = None, 0.0
        for s in sagas:
            last = parse_iso(s["chapters"][-1]["published"])
            if parse_iso(c["published"]) - last > timedelta(days=SAGA_GAP_DAYS):
                continue
            stoks = set(s["tokens"])
            shared = len(toks & stoks)
            overlap = shared / max(min(len(toks), len(stoks)), 1)
            if shared >= SAGA_MIN_SHARED and overlap >= SAGA_MIN_OVERLAP and overlap > best_overlap:
                best, best_overlap = s, overlap
        if best is None:
            best = {"id": c["id"], "title": c["title"], "lang": c["lang"], "chapters": [], "tokens": []}
            sagas.append(best)
        best["chapters"].append(chapter)
        best["chapters"].sort(key=lambda ch: ch["published"])
        # Tokens dos capítulos recentes: a saga acompanha o assunto conforme ele evolui.
        recent = best["chapters"][-3:]
        recent_toks: set[str] = set()
        for ch in recent:
            recent_toks |= tokens(ch["title"])
        best["tokens"] = sorted(recent_toks | toks)
        by_cluster[c["id"]] = best

    # Só vira saga com capítulos em pelo menos 2 dias diferentes.
    for c in items:
        s = by_cluster.get(c["id"])
        if not s:
            continue
        days = {ch["published"][:10] for ch in s["chapters"]}
        if len(s["chapters"]) >= 2 and len(days) >= 2:
            ids = [ch["cluster_id"] for ch in s["chapters"]]
            c["saga"] = {
                "id": s["id"],
                "title": s["title"],
                "lang": s["lang"],
                "chapter": ids.index(c["id"]) + 1,
                "total": len(s["chapters"]),
                # Os últimos 12 capítulos bastam para a tela; mantém o feed leve.
                "chapters": s["chapters"][-12:],
            }
    path.write_text(json.dumps({"generated_at": iso(now), "sagas": sagas}, ensure_ascii=False), encoding="utf-8")


# ---------------------------------------------------------------------------
# Quem noticia primeiro
# ---------------------------------------------------------------------------

FIRST_MIN_SOURCES = 3
FIRST_KEEP_DAYS = 30


def update_first(out: Path, items: list[dict], now: datetime) -> dict:
    """Para histórias grandes (3+ veículos), registra quem publicou primeiro. Janela de 30 dias."""
    path = out / "stats" / "first.json"
    try:
        records = json.loads(path.read_text(encoding="utf-8"))["records"]
    except (OSError, ValueError, KeyError):
        records = {}
    cutoff = iso(now - timedelta(days=FIRST_KEEP_DAYS))
    records = {k: v for k, v in records.items() if v["published"] >= cutoff}
    for c in items:
        if c["sources_count"] < FIRST_MIN_SOURCES:
            continue
        ordered = sorted(c["articles"], key=lambda a: a["published"])
        first = ordered[0]
        # Minutos de vantagem sobre o segundo veículo diferente.
        second = next((a for a in ordered if a["source"] != first["source"]), None)
        lead = (parse_iso(second["published"]) - parse_iso(first["published"])).total_seconds() / 60 if second else 0
        records[c["id"]] = {
            "first": first["source"],
            "sources": sorted({a["source"] for a in c["articles"]}),
            "published": c["published"],
            "lead_min": round(lead),
        }
    ranking: dict[str, dict] = {}
    for r in records.values():
        for s in r["sources"]:
            ranking.setdefault(s, {"source": s, "firsts": 0, "stories": 0, "lead_min": 0})
            ranking[s]["stories"] += 1
        ranking[r["first"]]["firsts"] += 1
        ranking[r["first"]]["lead_min"] += r["lead_min"]
    board = []
    for v in ranking.values():
        board.append({
            **v,
            "lead_min": round(v["lead_min"] / v["firsts"]) if v["firsts"] else 0,
            "rate": round(v["firsts"] / v["stories"], 3) if v["stories"] else 0,
        })
    board.sort(key=lambda v: (v["firsts"], v["rate"]), reverse=True)
    data = {"generated_at": iso(now), "big_stories": len(records), "ranking": board, "records": records}
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data

