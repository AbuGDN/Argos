"""Dados fixos do app (Kotlin) convertidos para o painel web (painel/geo.json).

O app é a fonte da verdade: regiões, pontos no mapa, cidades, alcance de mísseis, contexto,
marcos e contadores de conflito ficam em arquivos Kotlin. Este módulo lê esses arquivos com
expressões regulares simples (são listas literais) para o site não precisar de cópia à mão.
"""
from __future__ import annotations

import re
from pathlib import Path

APP = Path(__file__).resolve().parent.parent.parent / "android" / "app" / "src" / "main" / "java" / "com" / "abugdn" / "wid"

_STR = r'"((?:[^"\\]|\\.)*)"'
_NUM = r"(-?\d+(?:\.\d+)?)"


def _unescape(s: str) -> str:
    return s.replace('\\"', '"').replace("\\$", "$").replace("\\n", "\n")


def _block(text: str, start: str) -> str:
    """Trecho de `start` até o parêntese que fecha o mapOf/listOf aberto nele."""
    i = text.find(start)
    if i < 0:
        return ""
    j = text.index("(", i + len(start) - 1) if not start.endswith("(") else i + len(start) - 1
    depth = 0
    in_str = False
    k = j
    while k < len(text):
        c = text[k]
        if in_str:
            if c == "\\":
                k += 2
                continue
            if c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return text[j + 1:k]
        k += 1
    return text[j + 1:]


def _concat_strings(expr: str) -> str:
    """Junta "a" + "b" + ... de uma expressão Kotlin."""
    return "".join(_unescape(m) for m in re.findall(_STR, expr))


def labels(app: Path = APP) -> dict[str, str]:
    body = _block((app / "data" / "Models.kt").read_text(encoding="utf-8"), "val TAG_LABELS = linkedMapOf(")
    return {k: _unescape(v) for k, v in re.findall(_STR + r"\s+to\s+" + _STR, body)}


def points(app: Path = APP) -> dict[str, list[float]]:
    body = _block((app / "ui" / "MapScreen.kt").read_text(encoding="utf-8"), "private val REGION_POINTS = mapOf(")
    return {k: [float(lon), float(lat)] for k, lat, lon in re.findall(_STR + r"\s+to\s+GeoPoint\(" + _NUM + r",\s*" + _NUM + r"\)", body)}


def cities(app: Path = APP) -> list[dict]:
    text = (app / "data" / "Cities.kt").read_text(encoding="utf-8")
    out = []
    pattern = r"City\(" + _STR + r",\s*" + _NUM + r",\s*" + _NUM + r",\s*" + _STR + r",\s*listOf\(([^)]*)\)\)"
    for name, lat, lon, tag, terms in re.findall(pattern, text):
        out.append({
            "name": _unescape(name), "lat": float(lat), "lon": float(lon), "tag": tag,
            "terms": [_unescape(t) for t in re.findall(_STR, terms)],
        })
    return out


def ranges(app: Path = APP) -> list[dict]:
    text = (app / "data" / "Ranges.kt").read_text(encoding="utf-8")
    pattern = (r"RangeArc\(\s*" + _STR + r",\s*" + _STR + r",\s*" + _STR + r",\s*" + _NUM + r",\s*" + _NUM
               + r",\s*(\d+),\s*defense\s*=\s*(true|false)")
    return [
        {"id": i, "label": _unescape(label), "detail": _unescape(detail), "lat": float(lat), "lon": float(lon),
         "km": int(km), "defense": d == "true"}
        for i, label, detail, lat, lon, km, d in re.findall(pattern, text, flags=re.S)
    ]


def context(app: Path = APP) -> dict[str, str]:
    body = _block((app / "data" / "Context.kt").read_text(encoding="utf-8"), "val REGION_CONTEXT = mapOf(")
    out = {}
    for m in re.finditer(_STR + r"\s+to\s+((?:" + _STR + r"\s*\+?\s*)+)", body):
        out[m.group(1)] = _concat_strings(m.group(2))
    return out


def milestones(app: Path = APP) -> dict[str, list[dict]]:
    out: dict[str, list[dict]] = {}
    current = None
    for line in (app / "data" / "Milestones.kt").read_text(encoding="utf-8").splitlines():
        head = re.match(r'\s*"(\w+)" to listOf\(', line)
        if head:
            current = head.group(1)
            out[current] = []
        for date, text in re.findall(r"Milestone\(" + _STR + r",\s*" + _STR + r"\)", line):
            if current:
                out[current].append({"date": _unescape(date), "text": _unescape(text)})
    return out


def conflicts(app: Path = APP) -> dict[str, dict]:
    text = (app / "data" / "Conflicts.kt").read_text(encoding="utf-8")
    pattern = _STR + r"\s+to\s+Conflict\(" + _STR + r",\s*LocalDate\.of\((\d+),\s*(\d+),\s*(\d+)\)\)"
    return {
        tag: {"label": _unescape(label), "start": f"{int(y):04d}-{int(m):02d}-{int(d):02d}"}
        for tag, label, y, m, d in re.findall(pattern, text)
    }


def flags(app: Path = APP) -> dict[str, list[str]]:
    body = _block((app / "data" / "Images.kt").read_text(encoding="utf-8"), "val REGION_FLAGS = mapOf(")
    return {tag: re.findall(_STR, files) for tag, files in re.findall(_STR + r"\s+to\s+listOf\(([^)]*)\)", body)}


def origins(app: Path = APP) -> dict[str, str]:
    body = _block((app / "data" / "Models.kt").read_text(encoding="utf-8"), "val ORIGIN_LABELS = linkedMapOf(")
    return {k: _unescape(v) for k, v in re.findall(_STR + r"\s+to\s+" + _STR, body)}


def geo(app: Path = APP) -> dict | None:
    """Tudo junto; None se os arquivos do app não estiverem aqui (ex.: cópia só do backend)."""
    if not (app / "data" / "Models.kt").exists():
        return None
    return {
        "labels": labels(app),
        "points": points(app),
        "cities": cities(app),
        "ranges": ranges(app),
        "context": context(app),
        "milestones": milestones(app),
        "conflicts": conflicts(app),
        "flags": flags(app),
        "origins": origins(app),
    }
