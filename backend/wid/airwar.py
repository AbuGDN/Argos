"""Placar aéreo da Ucrânia: drones e mísseis lançados pela Rússia por noite e quantos foram abatidos.

Lido das manchetes (\"Russia launches 479 drones, 20 missiles overnight; 460 downed\"). Guarda 30 dias
em airwar.json; em cada dia fica o maior número citado (as manchetes repetem o balanço da Força Aérea).
"""

import json
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path

from .fetch import iso, parse_iso

KEEP_DAYS = 30
KYIV = timezone(timedelta(hours=3))
_N = r"(\d{1,4}(?:[.,]\d{3})?)"
_WORD = r"(?:[^\W\d_][\w-]*\s+)"  # palavra sem número ("50 of 70 drones" conta 70, não 50)
DRONES = re.compile(_N + r"\s+" + _WORD + r"{0,3}?(?:drones|uavs)\b", re.I)
MISSILES = re.compile(_N + r"\s+" + _WORD + r"{0,3}?(?:missiles|mísseis)\b", re.I)
# Balanços da semana, do mês ou do ano não são de uma noite.
EXCLUDE = re.compile(r"\b(?:week|weeks|month|months|year|since|semana|semanas|mês|meses|ano|desde)\b", re.I)
DOWNED = [
    re.compile(_N + r"\s+(?:[\w-]+\s+){0,4}?(?:were\s+|foram\s+)?(?:shot down|downed|intercepted|neutrali[sz]ed|destroyed|abatid[oa]s|derrubad[oa]s|interceptad[oa]s)", re.I),
    re.compile(r"(?:shoots? down|shot down|downs|intercepts?|intercepted|derruba|derrubou|abate|abateu|intercepta|interceptou)\s+" + _N, re.I),
]
CONTEXT = re.compile(r"\b(?:overnight|night|attack|launch|launches|launched|barrage|ataque|lança|lançou|noite|madrugada)\b", re.I)


def _num(raw: str) -> int:
    return int(raw.replace(",", "").replace(".", ""))


def parse_title(title: str) -> dict | None:
    """{"drones", "missiles", "downed"} citados num título sobre ataque aéreo russo; None se não parece um."""
    if not CONTEXT.search(title) or EXCLUDE.search(title):
        return None
    drones = [_num(m.group(1)) for m in DRONES.finditer(title)]
    missiles = [_num(m.group(1)) for m in MISSILES.finditer(title)]
    if not drones and not missiles:
        return None
    downed = [_num(m.group(1)) for rx in DOWNED for m in rx.finditer(title)]
    out = {"drones": max(drones, default=0), "missiles": max(missiles, default=0)}
    launched = out["drones"] + out["missiles"]
    # "Abatidos" maior que o total lançado é outro número (ex.: total da semana): ignora.
    valid = [d for d in downed if d <= launched]
    if valid:
        out["downed"] = max(valid)
    if launched > 2000:
        return None
    return out


def update_airwar(out: Path, items: list[dict], now: datetime) -> dict:
    path = out / "airwar.json"
    try:
        days = {d["date"]: d for d in json.loads(path.read_text(encoding="utf-8"))["days"]}
    except (OSError, ValueError, KeyError):
        days = {}
    for c in items:
        if "ucrania_russia" not in c.get("tags", []):
            continue
        titles = [c["title"]] + [a["title"] for a in c.get("articles", []) if a.get("lang") in ("pt", "en")]
        for t in titles:
            found = parse_title(t)
            if not found:
                continue
            day = parse_iso(c["published"]).astimezone(KYIV).date().isoformat()
            d = days.setdefault(day, {"date": day, "drones": 0, "missiles": 0})
            changed = False
            for k in ("drones", "missiles", "downed"):
                if found.get(k, 0) > d.get(k, 0):
                    d[k] = found[k]
                    changed = True
            if changed:
                d["cluster_id"], d["title"], d["lang"] = c["id"], c["title"], c["lang"]
    cutoff = (now.astimezone(KYIV) - timedelta(days=KEEP_DAYS)).date().isoformat()
    ordered = [days[k] for k in sorted(days) if k >= cutoff]
    data = {"version": 1, "generated_at": iso(now), "days": ordered}
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return data
