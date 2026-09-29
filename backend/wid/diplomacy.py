"""Termômetro diplomático: eventos diplomáticos nas manchetes (7 dias), por região.

Por regras sobre o título (português e inglês): expulsão ou convocação de embaixador, rompimento,
sanções, fronteira fechada, reunião de emergência e ameaças esquentam; negociação, acordo,
encontros e reaproximação esfriam. Não é opinião: é a conta desses eventos, e erra às vezes.
"""

import json
import re
from datetime import datetime, timedelta
from pathlib import Path

from .fetch import iso, parse_iso

KEEP = timedelta(days=7)
MIN_EVENTS = 3
MAX_EVENTS = 15

# categoria -> (peso: positivo = hostil, negativo = cooperativo; rótulo; padrão)
CATEGORIES = {
    "embaixador": (3, "embaixador expulso ou convocado",
                   r"(?:expels?|expelled|expulsa|expulsou|expulsão|recalls?|recalled|summons?|summoned|convoca|convocou|chama de volta)"
                   r"\b.{0,40}\b(?:ambassador|embaixador|embaixadora|envoy|diplomats?|diplomatas?|encarregad[oa] de negócios)"),
    "ruptura": (4, "rompimento de relações",
                r"(?:cuts?|severs?|severed|suspends?|rompe|rompeu|corta|cortou|suspende|suspendeu)\s+(?:diplomatic\s+|as\s+)?(?:ties|relations|relações)"),
    "sancoes": (2, "sanções", r"\b(?:sanctions?|sanções|sanção|sanciona|sancionou|sancionar)\b"),
    "fronteira": (2, "fronteira ou passagem fechada",
                  r"(?:closes?|closed|shuts?|fecha|fechou|fechamento|fechada)\b.{0,25}\b(?:border|fronteira|crossing|passagem|airspace|espaço aéreo)"),
    "emergencia": (2, "reunião de emergência",
                   r"(?:emergency (?:meeting|session|summit)|reunião de emergência|sessão de emergência|cúpula de emergência)"),
    "ameaca": (1, "ameaça ou ultimato", r"\b(?:threatens?|threatened|ameaça|ameaçou|ameaçam|ultimato|ultimatum)\b"),
    "negociacao": (-2, "negociação ou mediação",
                   r"\b(?:talks|negotiations?|negotiators?|negociações|negociação|negociadores|mediation|mediação|mediadores?|mediates)\b"),
    "acordo": (-3, "acordo ou trégua",
               r"\b(?:agreement|deal|accord|acordo|truce|trégua|ceasefire deal|acordo de cessar-fogo|peace plan|plano de paz)\b"),
    "encontro": (-1, "encontro, visita ou telefonema",
                 r"\b(?:meets?|met with|phone call|calls? with|telefonema|conversa por telefone|reúne-se|se reúne|encontra|visits?|visita|visitou)\b"),
    "reaproximacao": (-3, "reaproximação",
                      r"(?:restores?|restored|resumes?|restabelece|restabeleceu|reata|reatou|retoma|retomou)\s+(?:diplomatic\s+|as\s+)?(?:ties|relations|relações)"
                      r"|\bnormali[sz]ation\b|\bnormalização\b"),
}
_RX = {k: re.compile(p, re.IGNORECASE) for k, (_, _, p) in CATEGORIES.items()}
LABELS = {k: v[1] for k, v in CATEGORIES.items()}


def classify(title: str) -> list[str]:
    """Categorias diplomáticas citadas no título."""
    found = [k for k, rx in _RX.items() if rx.search(title)]
    # "Reunião de emergência" também casa "encontro": fica só a de emergência.
    if "emergencia" in found and "encontro" in found:
        found.remove("encontro")
    return found


def region_summary(events: list[dict]) -> dict:
    hostile = sum(CATEGORIES[c][0] for e in events for c in e["cats"] if CATEGORIES[c][0] > 0)
    coop = -sum(CATEGORIES[c][0] for e in events for c in e["cats"] if CATEGORIES[c][0] < 0)
    counts: dict[str, int] = {}
    for e in events:
        for c in e["cats"]:
            counts[c] = counts.get(c, 0) + 1
    total = hostile + coop
    index = int(round(50 + 50 * (hostile - coop) / total)) if total else 50
    if len(events) < MIN_EVENTS:
        label = "poucos eventos"
    elif index >= 65:
        label = "hostil"
    elif index <= 35:
        label = "cooperativo"
    else:
        label = "misto"
    events = sorted(events, key=lambda e: e["published"], reverse=True)
    return {"index": index, "label": label, "hostile": hostile, "coop": coop, "counts": counts,
            "events": events[:MAX_EVENTS], "total": len(events)}


def update_diplomacy(out: Path, items: list[dict], now: datetime) -> dict:
    """Junta os eventos novos aos dos últimos 7 dias e grava diplomacy.json."""
    path = out / "diplomacy.json"
    try:
        known = json.loads(path.read_text(encoding="utf-8"))["events"]
    except (OSError, ValueError, KeyError):
        known = []
    by_id = {e["cluster_id"]: e for e in known}
    for c in items:
        titles = [c["title"]] + [a["title"] for a in c.get("articles", []) if a.get("lang") in ("pt", "en")]
        cats = sorted({cat for t in titles for cat in classify(t)})
        if not cats or not c.get("tags"):
            continue
        by_id[c["id"]] = {"cluster_id": c["id"], "title": c["title"], "lang": c["lang"], "published": c["published"],
                          "tags": c["tags"], "cats": cats}
    events = [e for e in by_id.values() if now - parse_iso(e["published"]) <= KEEP]
    regions: dict[str, dict] = {}
    for tag in sorted({t for e in events for t in e["tags"]}):
        regions[tag] = region_summary([e for e in events if tag in e["tags"]])
    data = {"version": 1, "generated_at": iso(now), "labels": LABELS, "regions": regions, "events": events}
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return data
