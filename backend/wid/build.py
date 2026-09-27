"""Gera os JSONs publicados: feed.json, top.json, history/, sources_status.json e radar.json.

Uso: python -m wid.build --out ../site
"""

import argparse
import json
import re
import logging
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

import yaml

from .analysis import (
    cluster_figures,
    cluster_framing,
    cluster_sides,
    level_for,
    region_tension,
    truce_violation,
    update_first,
    update_sagas,
)
from .cluster import build_clusters, cluster_json, is_urgent
from .fetch import Article, fetch_all, iso, parse_iso
from .keywords import Keywords
from . import radar as radar_mod

log = logging.getLogger("wid")

CONFIG_DIR = Path(__file__).resolve().parent.parent / "config"
# Página de download (web/ na raiz do repositório), publicada junto do feed no GitHub Pages.
WEB_DIR = Path(__file__).resolve().parent.parent.parent / "web"
LOCAL_TZ = ZoneInfo("America/Sao_Paulo")
KEEP_WINDOW = timedelta(hours=48)
TOP_WINDOW = timedelta(hours=24)
# A principal do dia perde metade do peso a cada 12 h sem notícia nova, para uma história
# de ontem não ficar no topo o dia inteiro só por ter tido muitos veículos.
TOP_HALF_LIFE_H = 12.0
URGENT_WINDOW = timedelta(hours=2)
URGENT_MIN_SOURCES = 5
SECONDARY_COUNT = 4
STATS_DAYS = 30


def load_previous(out: Path, weights: dict[str, float], origins: dict[str, str]) -> list[Article]:
    path = out / "feed.json"
    if not path.exists():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        log.warning("feed.json anterior ilegível: %s", exc)
        return []
    return [
        Article.from_json(a, weights.get(a["source"], 1.0), origins.get(a["source"]))
        for c in data.get("clusters", [])
        for a in c.get("articles", [])
    ]


MAX_EDITS = 5


def _same_title(a: str, b: str) -> bool:
    """Ignora mudanças só de maiúsculas, pontuação ou espaços."""
    norm = lambda t: re.sub(r"\W+", " ", t.lower()).strip()  # noqa: E731
    return norm(a) == norm(b)


def merge(previous: list[Article], fresh: list[Article], now: datetime | None = None) -> list[Article]:
    """Une por id. O registro antigo vence, para o horário de publicação ficar estável; mas se o
    veículo trocou a manchete do mesmo link, fica a nova e a antiga vai para `edits`."""
    by_id: dict[str, Article] = {}
    for art in fresh:
        if art.id not in by_id or art.weight > by_id[art.id].weight:
            by_id[art.id] = art
    for art in previous:
        new = by_id.get(art.id)
        if new is not None and now is not None and new.title and not _same_title(new.title, art.title):
            art.edits = (art.edits + [{"title": art.title, "at": iso(now)}])[-MAX_EDITS:]
            art.title = new.title
        by_id[art.id] = art
    return list(by_id.values())


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    tmp.replace(path)


def top_score(c: dict, now: datetime) -> float:
    age_h = (now - parse_iso(c["updated"])).total_seconds() / 3600
    return c["day_score"] * 0.5 ** (age_h / TOP_HALF_LIFE_H)


def write_stats(out: Path, today, started_today: list[dict]) -> list[dict]:
    """stats/daily.json: quantas histórias começaram em cada dia, por região (últimos 30 dias)."""
    path = out / "stats" / "daily.json"
    try:
        days = {d["date"]: d for d in json.loads(path.read_text(encoding="utf-8"))["days"]}
    except (OSError, ValueError, KeyError):
        days = {}
    counts: dict[str, int] = {}
    for c in started_today:
        for tag in c["tags"]:
            counts[tag] = counts.get(tag, 0) + 1
    entry = {"date": today.isoformat(), "total": len(started_today), "counts": counts}
    # O pico de tensão do dia é gravado depois (record_tension); não perde entre as rodadas.
    for key in ("tension", "global"):
        if key in days.get(today.isoformat(), {}):
            entry[key] = days[today.isoformat()][key]
    days[today.isoformat()] = entry
    keep = sorted(days)[-STATS_DAYS:]
    ordered = [days[d] for d in keep]
    write_json(path, {"days": ordered})
    return ordered


def global_index(regions: dict) -> dict | None:
    """Relógio do Argos: 60% a região mais tensa + 40% a média das 3 mais tensas."""
    if not regions:
        return None
    ranked = sorted(regions.items(), key=lambda kv: kv[1]["tension"], reverse=True)
    top3 = [r["tension"] for _, r in ranked[:3]]
    index = int(round(0.6 * top3[0] + 0.4 * sum(top3) / len(top3)))
    return {"index": index, "level": level_for(index), "leader": ranked[0][0]}


def record_tension(out: Path, stats_days: list[dict], regions: dict, clock: dict | None) -> None:
    """Guarda no dia de hoje o maior índice de tensão visto por região (e o global)."""
    if not stats_days:
        return
    today = stats_days[-1]
    peaks = dict(today.get("tension", {}))
    for tag, r in regions.items():
        peaks[tag] = max(peaks.get(tag, 0), r["tension"])
    today["tension"] = peaks
    if clock:
        today["global"] = max(today.get("global", 0), clock["index"])
    write_json(out / "stats" / "daily.json", {"days": stats_days})


def copy_web(out: Path, web: Path = WEB_DIR) -> None:
    """Copia a página de download para a raiz do site (sem subpastas)."""
    if not web.is_dir():
        return
    for f in web.iterdir():
        if f.is_file():
            (out / f.name).write_bytes(f.read_bytes())


def build(
    out: Path, now: datetime, sources: list[dict], kw: Keywords, fetched: list[Article], status: dict,
    radar: dict | None = None,
) -> dict:
    weights = {s["name"]: float(s.get("weight", 1.0)) for s in sources}
    origins = {s["name"]: s["origin"] for s in sources if "origin" in s}
    articles = merge(load_previous(out, weights, origins), fetched, now)
    articles = [
        a for a in articles
        if now - a.published <= KEEP_WINDOW and kw.match(a.title, a.summary).relevant
    ]

    clusters = build_clusters(articles, kw)
    items = [
        cluster_json(c, kw, now, is_urgent(c, now, URGENT_MIN_SOURCES, URGENT_WINDOW))
        for c in clusters
    ]
    items.sort(key=lambda c: c["score"], reverse=True)

    for c in items:
        if figures := cluster_figures(c):
            c["figures"] = figures
        if framing := cluster_framing(c):
            c["framing"] = framing
        if sides := cluster_sides(c):
            c["sides"] = sides
        if truce_violation(c):
            c["truce_violation"] = True
    update_sagas(out, items, now)

    # Principal de cada dia e estatística diária (fuso de Brasília), antes do feed, porque
    # a tensão por região compara o dia de hoje com a média dos anteriores.
    today = now.astimezone(LOCAL_TZ).date()
    started_today = [c for c in items if parse_iso(c["published"]).astimezone(LOCAL_TZ).date() == today]
    stats_days = write_stats(out, today, started_today)
    regions = region_tension(items, stats_days, today.isoformat(), now)
    if radar is not None:
        # Radar (radar.json): sensores sobem a tensão; checagens se ligam às histórias.
        radar_mod.apply_signals(regions, radar, now)
        radar_mod.link_factchecks(radar, items)
        write_json(out / "radar.json", radar)
    clock = global_index(regions)
    record_tension(out, stats_days, regions, clock)
    update_first(out, items, now)

    recent = [c for c in items if now - parse_iso(c["updated"]) <= TOP_WINDOW]
    recent.sort(key=lambda c: top_score(c, now), reverse=True)
    top = recent[0] if recent else None

    feed = {"version": 1, "generated_at": iso(now), "top_of_day": top, "regions": regions, "global": clock, "clusters": items}
    write_json(out / "feed.json", feed)
    write_json(out / "top.json", {
        "version": 1,
        "generated_at": iso(now),
        "top": top,
        "secondary": [
            {k: c[k] for k in ("id", "title", "source", "lang", "sources_count", "published", "url")}
            for c in recent[1:1 + SECONDARY_COUNT]
        ],
    })

    # Principal de cada dia. Reescrito a cada rodada; a última do dia fica.
    if started_today:
        best = max(started_today, key=lambda c: c["day_score"])
        write_json(out / "history" / f"{today.isoformat()}.json", {"date": today.isoformat(), "top": best})
    days = sorted((p.stem for p in (out / "history").glob("*.json") if p.stem != "index"), reverse=True)
    write_json(out / "history" / "index.json", {"days": days})

    write_json(out / "sources_status.json", {"generated_at": iso(now), "sources": status})
    (out / ".nojekyll").touch()
    copy_web(out)
    return feed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True, help="pasta do site (branch gh-pages)")
    parser.add_argument("--config", type=Path, default=CONFIG_DIR)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    sources = yaml.safe_load((args.config / "sources.yaml").read_text(encoding="utf-8"))["sources"]
    kw = Keywords.load(args.config / "keywords.yaml")
    now = datetime.now(timezone.utc).replace(microsecond=0)

    fetched, status = fetch_all(sources, now)
    ok = sum(1 for s in status.values() if s["ok"])
    log.info("%d/%d fontes ok, %d artigos baixados", ok, len(sources), len(fetched))
    if ok == 0:
        raise SystemExit("nenhuma fonte respondeu; mantendo o feed anterior")

    args.out.mkdir(parents=True, exist_ok=True)
    try:
        radar = radar_mod.collect(args.out, now, kw)
    except Exception as exc:  # o Radar nunca derruba a coleta de notícias
        log.warning("radar falhou: %s", exc)
        radar = None
    feed = build(args.out, now, sources, kw, fetched, status, radar)
    top = feed["top_of_day"]
    log.info("%d histórias; principal: %s", len(feed["clusters"]), top["title"] if top else "—")


if __name__ == "__main__":
    main()
