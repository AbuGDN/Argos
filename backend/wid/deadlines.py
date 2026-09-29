"""Ultimatos e prazos: "Israel dá 48 horas para...", "Trump gives Iran two weeks to...".

Cada prazo achado nas manchetes vira uma contagem regressiva (deadlines.json). Depois que vence,
o Argos procura a principal notícia da mesma região logo depois do prazo ("o que aconteceu").
"""

import json
import re
from datetime import datetime, timedelta
from pathlib import Path

from .fetch import iso, parse_iso

KEEP_AFTER_DUE = timedelta(days=21)
MAX_DURATION = timedelta(days=60)
# Notícia "do depois": publicada entre 6 h antes e 3 dias depois do vencimento.
AFTER_WINDOW = (timedelta(hours=-6), timedelta(days=3))

NUMBERS = {
    "um": 1, "uma": 1, "one": 1, "a": 1, "an": 1, "dois": 2, "duas": 2, "two": 2, "três": 3, "tres": 3, "three": 3,
    "quatro": 4, "four": 4, "cinco": 5, "five": 5, "seis": 6, "six": 6, "sete": 7, "seven": 7, "dez": 10, "ten": 10,
    "doze": 12, "twelve": 12, "quinze": 15, "fifteen": 15, "trinta": 30, "thirty": 30,
    "vinte e quatro": 24, "twenty-four": 24, "quarenta e oito": 48, "forty-eight": 48, "setenta e duas": 72,
    "seventy-two": 72,
}
UNITS = {
    "hora": 1, "horas": 1, "hour": 1, "hours": 1, "dia": 24, "dias": 24, "day": 24, "days": 24,
    "semana": 168, "semanas": 168, "week": 168, "weeks": 168,
}
_NUM = r"(\d{1,3}|" + "|".join(sorted(map(re.escape, NUMBERS), key=len, reverse=True)) + r")"
_UNIT = r"(" + "|".join(sorted(UNITS, key=len, reverse=True)) + r")"
DURATION = re.compile(r"(?<!\w)" + _NUM + r"[\s-]+" + _UNIT + r"(?!\w)")
# O prazo precisa vir com cara de ultimato: "48 horas para", "two weeks to", "prazo", "deadline"...
AFTER_DURATION = re.compile(r"^\s*(para|to|de prazo|deadline|ultimatum|ultimato)\b")
TRIGGER = re.compile(r"(?<!\w)(ultimato|ultimatum|prazo|deadline|dá|dão|deu|gives|gave|given|sets|set|estabelece)(?!\w)")


def find_deadline(title: str) -> tuple[str, timedelta] | None:
    """Duração do prazo citado no título (e o trecho, "48 horas"), ou None."""
    text = title.lower()
    for m in DURATION.finditer(text):
        n = int(m.group(1)) if m.group(1).isdigit() else NUMBERS[m.group(1)]
        dur = timedelta(hours=n * UNITS[m.group(2)])
        if not timedelta(hours=1) <= dur <= MAX_DURATION:
            continue
        tail = text[m.end():]
        if AFTER_DURATION.match(tail) or (TRIGGER.search(text) and re.match(r"^\s*(para|to|de)\b", tail)):
            return m.group(0), dur
    return None


def _after(item: dict, items: list[dict]) -> dict | None:
    due = parse_iso(item["due"])
    lo, hi = due + AFTER_WINDOW[0], due + AFTER_WINDOW[1]
    tags = set(item.get("tags") or [])
    best = None
    for c in items:
        if c["id"] == item["cluster_id"] or not tags & set(c.get("tags") or []):
            continue
        if lo <= parse_iso(c["published"]) <= hi and (best is None or c["score"] > best["score"]):
            best = c
    if best is None:
        return None
    return {"cluster_id": best["id"], "title": best["title"], "lang": best["lang"], "published": best["published"]}


def update_deadlines(out: Path, items: list[dict], now: datetime) -> list[dict]:
    """Acha prazos novos, preenche "o que aconteceu" nos vencidos e grava deadlines.json."""
    path = out / "deadlines.json"
    try:
        known = json.loads(path.read_text(encoding="utf-8"))["deadlines"]
    except (OSError, ValueError, KeyError):
        known = []
    by_cluster = {d["cluster_id"]: d for d in known}
    for c in items:
        if c["id"] in by_cluster:
            continue
        titles = [(c["title"], c["lang"])] + [(a["title"], a.get("lang", "")) for a in c.get("articles", [])]
        for title, lang in titles:
            if lang not in ("pt", "en"):
                continue
            found = find_deadline(title)
            if found is None:
                continue
            span, dur = found
            start = parse_iso(c["published"])
            d = {
                "id": f"{c['id']}-{int(dur.total_seconds() // 3600)}",
                "cluster_id": c["id"], "title": c["title"], "lang": c["lang"], "quote": title, "span": span,
                "start": iso(start), "due": iso(start + dur), "tags": c.get("tags", []),
            }
            # O mesmo ultimato noticiado de novo (mesma região, vence na mesma janela de 12 h) não duplica.
            if any(set(k["tags"]) & set(d["tags"]) and abs(parse_iso(k["due"]) - parse_iso(d["due"])) <= timedelta(hours=12)
                   for k in known):
                break
            known.append(d)
            by_cluster[c["id"]] = d
            break
    keep = []
    for d in known:
        due = parse_iso(d["due"])
        if now - due > KEEP_AFTER_DUE:
            continue
        if now >= due + AFTER_WINDOW[0] and not d.get("after"):
            if after := _after(d, items):
                d["after"] = after
        keep.append(d)
    keep.sort(key=lambda d: d["due"])
    path.write_text(json.dumps({"version": 1, "generated_at": iso(now), "deadlines": keep}, ensure_ascii=False,
                               separators=(",", ":")), encoding="utf-8")
    return keep
