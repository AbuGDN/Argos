"""Transparência e verificação de cada história.

- Árvore de fontes: quantos veículos só repetem uma agência (Reuters, AP, AFP...).
- Nível de confiança: fontes independentes, lados, números em conflito, fonte oficial.
- Propagação: em que imprensa e idioma a história apareceu primeiro, e depois.

Tudo por regras simples sobre título e resumo (o texto completo só existe no aparelho).
"""

import re

from .fetch import parse_iso

# Agência -> padrão (diferencia maiúsculas: "AP" e "EFE" em minúsculas são outras coisas).
AGENCIES = {
    "Reuters": r"\bReuters\b",
    "AP": r"\(AP\)|\bAssociated Press\b|\bAP reported\b|\bAP news agency\b|\bagência AP\b",
    "AFP": r"\bAFP\b|Agence France-Presse|\bFrance-Presse\b|\bFrance Presse\b",
    "EFE": r"\bEFE\b",
    "dpa": r"\bdpa\b|\bDPA\b",
    "Lusa": r"\bLusa\b",
    "Agência Brasil": r"Agência Brasil",
    "Anadolu": r"\bAnadolu\b",
    "WAFA": r"\bWAFA\b|\bWafa\b",
    "IRNA": r"\bIRNA\b",
    "Tasnim": r"\bTasnim\b",
    "Fars": r"\bFars news\b|\bFars News\b|\bagência Fars\b",
    "TASS": r"\bTASS\b|\bTass\b",
    "RIA Novosti": r"\bRIA Novosti\b|\bRIA\b",
    "Interfax": r"\bInterfax\b",
    "Xinhua": r"\bXinhua\b",
    "SANA": r"\bSANA\b",
    "Ukrinform": r"\bUkrinform\b",
    "KCNA": r"\bKCNA\b",
}
_AGENCY_RX = {name: re.compile(p) for name, p in AGENCIES.items()}

# "segundo o exército", "the IDF said"...: a história se apoia na versão oficial de uma das partes.
_OFFICIAL_WHO = (
    r"(?:idf|israeli military|israeli army|military|army|ministry|health ministry|ministério|exército|fdi|forças de defesa"
    r"|hamas|hezbollah|hizbollah|kremlin|pentagon|pentágono|houthis?|irgc|guarda revolucionária|russian defen[cs]e ministry"
    r"|ukrainian military|gaza health|governo|government|officials?|autoridades)"
)
_OFFICIAL_SAYS = r"(?:said|says|claims?|claimed|diz|disse|dizem|afirma|afirmou|alega|alegou)"
_OFFICIAL_RX = re.compile(
    r"(?:(?:according to|segundo|" + _OFFICIAL_SAYS[3:-1] + r")\s+(?:the\s+|o\s+|a\s+|os\s+|as\s+)?" + _OFFICIAL_WHO + r")"
    r"|(?:\b" + _OFFICIAL_WHO + r"\s+" + _OFFICIAL_SAYS + r"\b)",
    re.IGNORECASE,
)

ORIGIN_LABELS = {"israel": "Israel", "arabe": "Mundo árabe", "eua": "EUA", "internacional": "Internacional", "brasil": "Brasil"}


def article_agency(article: dict) -> str | None:
    """Primeira agência citada no título ou no resumo."""
    text = f"{article.get('title', '')}\n{article.get('summary', '')}"
    for name, rx in _AGENCY_RX.items():
        if rx.search(text):
            return name
    return None


def source_tree(cluster: dict) -> dict | None:
    """{"agencies": {agência: [veículos]}, "independent": n, "total": veículos}; None se ninguém cita agência."""
    agencies: dict[str, set[str]] = {}
    own: set[str] = set()
    for a in cluster["articles"]:
        ag = article_agency(a)
        if ag:
            agencies.setdefault(ag, set()).add(a["source"])
        else:
            own.add(a["source"])
    if not agencies:
        return None
    own -= {s for group in agencies.values() for s in group}  # o mesmo veículo em dois artigos
    total = len({a["source"] for a in cluster["articles"]})
    return {
        "agencies": {k: sorted(v) for k, v in sorted(agencies.items(), key=lambda kv: -len(kv[1]))},
        "independent": len(agencies) + len(own),
        "total": total,
    }


def confidence(cluster: dict) -> dict:
    """Nível de confiança da história, com os motivos. Não diz se é verdade: diz quão apoiada está."""
    sources = {a["source"] for a in cluster["articles"]}
    tree = cluster.get("wires")
    independent = tree["independent"] if tree else len(sources)
    reasons = []
    figures = cluster.get("figures") or {}
    divergent = [k for k, v in figures.items() if v.get("divergent")]
    official = sum(1 for a in cluster["articles"] if _OFFICIAL_RX.search(f"{a['title']} {a.get('summary', '')}"))
    mostly_official = official * 2 >= len(cluster["articles"]) and official > 0

    if divergent:
        level, label = "conflito", "Informações conflitantes"
        names = {"killed": "mortos", "injured": "feridos"}
        reasons.append("os números de " + " e ".join(names.get(k, k) for k in divergent) + " não batem entre os veículos")
    elif independent >= 3:
        level, label = "alta", "Confirmado por várias fontes independentes"
    elif independent == 2:
        level, label = "media", "Duas fontes independentes"
    else:
        level, label = "baixa", "Uma fonte só" if len(sources) <= 1 else "Todos repetem a mesma fonte"

    if len(sources) == 1:
        reasons.append("só um veículo noticiou até agora")
    elif tree and tree["independent"] < len(sources):
        biggest, users = next(iter(tree["agencies"].items()))
        reasons.append(f"{len(users)} de {len(sources)} veículos citam a {biggest}")
    if cluster.get("sides") == "opostos":
        reasons.append("imprensa dos dois lados noticiou")
    elif cluster.get("sides") == "um_lado":
        reasons.append("só a imprensa de um lado noticiou")
        if level == "alta":
            level, label = "media", "Várias fontes, mas de um lado só"
    if mostly_official:
        reasons.append("a maior parte se apoia na versão oficial de uma das partes")
        if level == "alta":
            level, label = "media", "Várias fontes, apoiadas em versão oficial"
    return {"level": level, "label": label, "independent": independent, "reasons": reasons}


def spread(cluster: dict) -> list[dict] | None:
    """Primeira aparição da história em cada imprensa (origem), em ordem; None se só uma origem."""
    first: dict[str, dict] = {}
    for a in sorted(cluster["articles"], key=lambda a: a["published"]):
        origin = a.get("origin") or "internacional"
        if origin not in first:
            first[origin] = {"origin": origin, "label": ORIGIN_LABELS.get(origin, origin), "source": a["source"],
                             "lang": a.get("lang", ""), "time": a["published"]}
    if len(first) < 2:
        return None
    steps = sorted(first.values(), key=lambda s: s["time"])
    t0 = parse_iso(steps[0]["time"])
    for s in steps:
        s["after_min"] = int((parse_iso(s["time"]) - t0).total_seconds() // 60)
    return steps


def annotate(cluster: dict) -> None:
    """Anota wires, confidence e spread no item do feed."""
    if tree := source_tree(cluster):
        cluster["wires"] = tree
    cluster["confidence"] = confidence(cluster)
    if steps := spread(cluster):
        cluster["spread"] = steps
