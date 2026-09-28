"""Radar: dados de fora da imprensa (sensores, cotações, números, vozes oficiais, análises,
checagens e mercados de previsão), publicados em radar.json para a aba Radar do app.

Cada seção é independente: tem seu intervalo mínimo entre coletas (config/radar.yaml) e,
se falhar, mantém o último dado bom. Nada aqui pode derrubar a coleta de notícias.
Estado que o app não precisa (linhas de base) fica em stats/radar_state.json.
"""

import base64
import csv
import gzip
import io
import json
import logging
import math
import os
import re
import statistics
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

import httpx
import yaml

from .analysis import level_for
from .fetch import TIMEOUT, USER_AGENT, fetch_source, iso, parse_iso
from .keywords import Keywords
from .text import clean_html, tokens, truncate

log = logging.getLogger("wid.radar")

CONFIG_PATH = Path(__file__).resolve().parent.parent / "config" / "radar.yaml"
RETRY_FAILED = timedelta(minutes=60)
# Dado de sensor mais velho que isso não mexe no índice de tensão.
SIGNAL_MAX_AGE = timedelta(hours=3)


class MissingKey(Exception):
    """A seção precisa de uma chave (secret) que não foi configurada."""


@dataclass
class Ctx:
    client: httpx.Client
    now: datetime
    kw: Keywords
    conf: dict
    prev: dict | None
    state: dict


def load_config(path: Path = CONFIG_PATH) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def _read(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _get_json(client: httpx.Client, url: str, **kw):
    resp = client.get(url, **kw)
    resp.raise_for_status()
    return resp.json()


def _track_since(state: dict, key: str, status: str, now: datetime) -> str:
    """Desde quando o item está no status atual (muda quando o status muda)."""
    entry = state.get(key)
    if not entry or entry.get("status") != status:
        entry = {"status": status, "since": iso(now)}
        state[key] = entry
    return entry["since"]


# ---------------------------------------------------------------------------
# Internet (IODA)
# ---------------------------------------------------------------------------

IODA_URL = "https://api.ioda.inetintel.cc.gatech.edu/v2/signals/raw/country/{code}"
IODA_SOURCES = ("ping-slash24", "bgp")


def _ioda_series(payload) -> dict[str, dict]:
    """Acha as séries ({datasource, from, step, values}) em qualquer nível da resposta."""
    found: dict[str, dict] = {}

    def walk(x):
        if isinstance(x, dict):
            if "datasource" in x and isinstance(x.get("values"), list):
                found.setdefault(x["datasource"], x)
                return
            for v in x.values():
                walk(v)
        elif isinstance(x, list):
            for v in x:
                walk(v)

    walk(payload)
    return found


def ioda_status(payload, now: datetime) -> dict:
    """Sinal da última hora comparado com a mediana das 24 h anteriores.

    ratio < 0,5 = apagão; < 0,85 = queda. Usa o pior entre sondagem ativa e rotas BGP.
    """
    series = _ioda_series(payload)
    t_now = now.timestamp()
    ratios: dict[str, float] = {}
    spark: list[float | None] = []
    for ds in IODA_SOURCES:
        s = series.get(ds)
        if not s:
            continue
        start, step = float(s.get("from") or 0), float(s.get("step") or 300)
        points = [(start + i * step, float(v)) for i, v in enumerate(s["values"]) if isinstance(v, (int, float))]
        if len(points) < 12:
            continue
        # A IODA atrasa alguns minutos: sem pontos suficientes na última hora, usa os 6 últimos.
        recent = [v for t, v in points if t >= t_now - 3600]
        if len(recent) < 3:
            recent = [v for _, v in points[-6:]]
        base = [v for t, v in points if t < t_now - 3 * 3600]
        if not base:
            continue
        med = statistics.median(base)
        if med <= 0:
            continue
        ratios[ds] = sum(recent) / len(recent) / med
        if not spark:
            for h in range(24):
                lo, hi = t_now - (24 - h) * 3600, t_now - (23 - h) * 3600
                bucket = [v for t, v in points if lo <= t < hi]
                spark.append(round(min(sum(bucket) / len(bucket) / med, 1.5), 2) if bucket else None)
    if not ratios:
        raise ValueError("sem sinais utilizáveis")
    ratio = min(ratios.values())
    status = "apagao" if ratio < 0.5 else "queda" if ratio < 0.85 else "normal"
    return {"ratio": round(ratio, 2), "status": status, "spark": spark}


def collect_internet(ctx: Ctx) -> dict:
    until = int(ctx.now.timestamp())
    countries, errors = [], 0
    for c in ctx.conf["countries"]:
        entry = {"code": c["code"], "name": c["name"], "tag": c["tag"]}
        try:
            payload = _get_json(ctx.client, IODA_URL.format(code=c["code"]), params={"from": until - 27 * 3600, "until": until})
            entry.update(ioda_status(payload, ctx.now))
            entry["since"] = _track_since(ctx.state, c["code"], entry["status"], ctx.now)
        except Exception as exc:
            errors += 1
            log.info("IODA %s: %s", c["code"], exc)
            entry["status"] = "sem_dados"
        countries.append(entry)
    if errors == len(countries):
        raise RuntimeError("IODA não respondeu para nenhum país")
    return {"countries": countries}


# ---------------------------------------------------------------------------
# Espaço aéreo (OpenSky)
# ---------------------------------------------------------------------------

OPENSKY_URL = "https://opensky-network.org/api/states/all"
OPENSKY_TOKEN_URL = "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token"
AIR_KEEP_DAYS = 14


def count_airborne(payload: dict) -> int:
    """Aeronaves no ar (índice 8 do vetor de estado = on_ground)."""
    return sum(1 for s in payload.get("states") or [] if len(s) > 8 and not s[8])


def airspace_status(flights: int, baseline: float | None, samples: int) -> str:
    if baseline is None or samples < 3:
        return "coletando"
    if baseline < 6:
        return "pouco_trafego"
    ratio = flights / baseline
    if ratio < 0.25:
        return "fechado"
    if ratio < 0.6:
        return "reduzido"
    return "normal"


def _opensky_headers(client: httpx.Client) -> dict:
    cid, secret = os.environ.get("OPENSKY_CLIENT_ID"), os.environ.get("OPENSKY_CLIENT_SECRET")
    if not cid or not secret:
        return {}
    resp = client.post(OPENSKY_TOKEN_URL, data={"grant_type": "client_credentials", "client_id": cid, "client_secret": secret})
    resp.raise_for_status()
    return {"Authorization": f"Bearer {resp.json()['access_token']}"}


def collect_airspace(ctx: Ctx) -> dict:
    try:
        headers = _opensky_headers(ctx.client)
    except Exception as exc:
        log.info("OpenSky token: %s (seguindo anônimo)", exc)
        headers = {}
    hour, today = str(ctx.now.hour), ctx.now.date().isoformat()
    history = ctx.state.setdefault("history", {})
    zones, errors = [], 0
    for z in ctx.conf["zones"]:
        s, w, n, e = z["box"]
        entry = {"id": z["id"], "name": z["name"], "tag": z["tag"]}
        try:
            payload = _get_json(ctx.client, OPENSKY_URL, params={"lamin": s, "lomin": w, "lamax": n, "lomax": e}, headers=headers)
            flights = count_airborne(payload)
            by_date = history.setdefault(z["id"], {}).setdefault(hour, {})
            past = [v for d, v in by_date.items() if d != today]
            baseline = statistics.median(past) if past else None
            by_date[today] = flights
            for d in sorted(by_date)[:-AIR_KEEP_DAYS]:
                del by_date[d]
            status = airspace_status(flights, baseline, len(past))
            entry.update({"flights": flights, "baseline": round(baseline, 1) if baseline is not None else None, "status": status})
            entry["since"] = _track_since(ctx.state.setdefault("since", {}), z["id"], status, ctx.now)
        except Exception as exc:
            errors += 1
            log.info("OpenSky %s: %s", z["id"], exc)
            entry["status"] = "sem_dados"
        zones.append(entry)
    if errors == len(zones):
        raise RuntimeError("OpenSky não respondeu para nenhuma zona")
    return {"zones": zones}


# ---------------------------------------------------------------------------
# Focos de calor (NASA FIRMS)
# ---------------------------------------------------------------------------

FIRMS_URL = "https://firms.modaps.eosdis.nasa.gov/api/area/csv/{key}/{sensor}/{box}/1"
FIRMS_SENSORS = ("VIIRS_NOAA20_NRT", "VIIRS_SNPP_NRT")


def parse_firms(text: str) -> list[tuple[float, float, float]]:
    """CSV do FIRMS -> [(lat, lon, frp)]. Levanta erro se não for o CSV esperado."""
    if "latitude" not in text[:300]:
        raise ValueError(text[:120].strip() or "resposta vazia")
    points = []
    for row in csv.DictReader(io.StringIO(text)):
        try:
            points.append((float(row["latitude"]), float(row["longitude"]), float(row.get("frp") or 0)))
        except (KeyError, ValueError):
            continue
    return points


def collect_fires(ctx: Ctx) -> dict:
    key = os.environ.get("FIRMS_MAP_KEY", "").strip()
    if not key:
        return {"missing_key": True, "zones": []}
    today = ctx.now.date().isoformat()
    max_points = int(ctx.conf.get("max_points", 250))
    zones, errors = [], 0
    for z in ctx.conf["zones"]:
        entry = {"id": z["id"], "name": z["name"], "tag": z["tag"]}
        try:
            unique: dict[tuple[float, float], float] = {}
            for sensor in FIRMS_SENSORS:
                url = FIRMS_URL.format(key=key, sensor=sensor, box=",".join(str(v) for v in z["box"]))
                resp = ctx.client.get(url)
                resp.raise_for_status()
                for lat, lon, frp in parse_firms(resp.text):
                    k = (round(lat, 2), round(lon, 2))
                    unique[k] = max(unique.get(k, 0.0), frp)
            count = len(unique)
            days = ctx.state.setdefault(z["id"], {})
            past = [v for d, v in days.items() if d != today]
            days[today] = max(days.get(today, 0), count)
            for d in sorted(days)[:-8]:
                del days[d]
            strongest = sorted(unique.items(), key=lambda kv: kv[1], reverse=True)[:max_points]
            entry.update({
                "count": count,
                "baseline": round(sum(past) / len(past), 1) if past else None,
                "points": [[lat, lon, round(frp, 1)] for (lat, lon), frp in strongest],
            })
        except Exception as exc:
            errors += 1
            log.info("FIRMS %s: %s", z["id"], exc)
            entry["error"] = True
        zones.append(entry)
    if errors == len(zones):
        raise RuntimeError("FIRMS não respondeu (chave inválida?)")
    return {"zones": zones}


# ---------------------------------------------------------------------------
# Estreitos (IMF PortWatch)
# ---------------------------------------------------------------------------

PORTWATCH_URL = (
    "https://services9.arcgis.com/weJ1QsnbMYJlCHdG/arcgis/rest/services/"
    "Daily_Chokepoints_Data/FeatureServer/0/query"
)


def _pw_date(value) -> str:
    if isinstance(value, (int, float)):
        return datetime.fromtimestamp(value / 1000, tz=timezone.utc).date().isoformat()
    return str(value)[:10]


def strait_summary(features: list[dict]) -> dict:
    """Média de navios/dia na última semana, nos 90 dias antes dela e um ano antes."""
    series = sorted(
        (_pw_date(f["attributes"]["date"]), float(f["attributes"].get("n_total") or 0))
        for f in features
        if f.get("attributes", {}).get("date") is not None
    )
    if len(series) < 7:
        raise ValueError("série curta")
    last = datetime.fromisoformat(series[-1][0]).date()

    def avg(lo: int, hi: int) -> float | None:
        vals = [v for d, v in series if lo <= (last - datetime.fromisoformat(d).date()).days < hi]
        return round(sum(vals) / len(vals), 1) if vals else None

    week, before, year = avg(0, 7), avg(7, 97), avg(358, 372)
    return {
        "date": series[-1][0],
        "avg7": week,
        "avg90": before,
        "year_ago": year,
        "spark": [v for _, v in series[-60:]],
    }


def collect_straits(ctx: Ctx) -> dict:
    items, errors = [], 0
    for s in ctx.conf["items"]:
        entry = {"id": s["id"], "name": s["name"], "tag": s["tag"]}
        try:
            payload = _get_json(ctx.client, PORTWATCH_URL, params={
                "where": f"portname LIKE '%{s['match']}%'",
                "outFields": "date,portname,n_total",
                "orderByFields": "date DESC",
                "resultRecordCount": 400,
                "f": "json",
            })
            if "error" in payload:
                raise ValueError(str(payload["error"])[:120])
            entry.update(strait_summary(payload.get("features") or []))
        except Exception as exc:
            errors += 1
            log.info("PortWatch %s: %s", s["id"], exc)
            entry["error"] = True
        items.append(entry)
    if errors == len(items):
        raise RuntimeError("PortWatch não respondeu")
    return {"items": items}


# ---------------------------------------------------------------------------
# Cotações (Yahoo Finance)
# ---------------------------------------------------------------------------

YAHOO_URLS = (
    "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}",
    "https://query2.finance.yahoo.com/v8/finance/chart/{symbol}",
)
STOOQ_URL = "https://stooq.com/q/d/l/"
FRED_URL = "https://fred.stlouisfed.org/graph/fredgraph.csv"
EIA_URL = "https://www.eia.gov/dnav/pet/hist/LeafHandler.ashx"
FX_URL = "https://open.er-api.com/v6/latest/USD"
MARKET_KEEP_DAYS = 40


def quote_summary(payload: dict) -> dict:
    res = payload["chart"]["result"][0]
    meta = res["meta"]
    closes = res["indicators"]["quote"][0]["close"]
    pairs = [(t, c) for t, c in zip(res.get("timestamp") or [], closes) if c is not None]
    if not pairs:
        raise ValueError("sem cotações")
    price = float(meta.get("regularMarketPrice") or pairs[-1][1])
    market_day = datetime.fromtimestamp(meta.get("regularMarketTime") or pairs[-1][0], tz=timezone.utc).date()
    last_day = datetime.fromtimestamp(pairs[-1][0], tz=timezone.utc).date()
    # A última barra é a de hoje: o fechamento anterior é a penúltima.
    history = pairs[:-1] if last_day >= market_day else pairs
    prev = history[-1][1] if history else price
    week = history[-5][1] if len(history) >= 5 else None
    return {
        "price": price,
        "change_pct": round((price - prev) / prev * 100, 2) if prev else 0.0,
        "change_week_pct": round((price - week) / week * 100, 2) if week else None,
        "time": iso(datetime.fromtimestamp(meta.get("regularMarketTime") or pairs[-1][0], tz=timezone.utc)),
        "spark": [round(c, 4) for _, c in history[-21:]] + [round(price, 4)],
    }


def series_summary(series: list[tuple[str, float]]) -> dict:
    """[(aaaa-mm-dd, fechamento)] em ordem -> preço, variação do dia e da semana."""
    if not series:
        raise ValueError("sem cotações")
    price = series[-1][1]
    prev = series[-2][1] if len(series) >= 2 else None
    week = series[-6][1] if len(series) >= 6 else None
    return {
        "price": price,
        "change_pct": round((price - prev) / prev * 100, 2) if prev else None,
        "change_week_pct": round((price - week) / week * 100, 2) if week else None,
        "time": series[-1][0] + "T00:00:00Z",
        "spark": [round(v, 4) for _, v in series[-22:]],
    }


def parse_stooq(text: str) -> list[tuple[str, float]]:
    if not text.lstrip().lower().startswith("date"):
        raise ValueError(text[:80].strip() or "resposta vazia")
    out = []
    for row in csv.DictReader(io.StringIO(text)):
        try:
            out.append((row["Date"][:10], float(row["Close"])))
        except (KeyError, ValueError, TypeError):
            continue
    if not out:
        raise ValueError("sem linhas")
    return out


_EIA_ROW = re.compile(r"<td class='B6'>(?:&nbsp;)*\s*(\d{4}) (\w{3})-\s*(\d{1,2}) to [^<]*</td>((?:\s*<td class='B3'>[^<]*</td>){1,5})")
_EIA_CELL = re.compile(r"<td class='B3'>([^<]*)</td>")


def parse_eia(html: str) -> list[tuple[str, float]]:
    """Tabela diária do EIA (uma linha por semana, seg a sex) -> [(aaaa-mm-dd, valor)]."""
    out = []
    for m in _EIA_ROW.finditer(html):
        year, mon, day, cells = m.groups()
        try:
            monday = datetime.strptime(f"{year} {mon} {day}", "%Y %b %d")
        except ValueError:
            continue
        for i, cell in enumerate(_EIA_CELL.findall(cells)):
            try:
                out.append(((monday + timedelta(days=i)).date().isoformat(), float(cell.strip())))
            except ValueError:
                continue
    if not out:
        raise ValueError("tabela do EIA não encontrada")
    return out[-60:]


def parse_fred(text: str) -> list[tuple[str, float]]:
    """CSV do FRED (data, valor; "." = sem dado) -> [(aaaa-mm-dd, valor)]."""
    rows = list(csv.reader(io.StringIO(text)))
    if not rows or len(rows[0]) < 2:
        raise ValueError(text[:80].strip() or "resposta vazia")
    out = []
    for row in rows[1:]:
        try:
            out.append((row[0][:10], float(row[1])))
        except (IndexError, ValueError):
            continue
    if not out:
        raise ValueError("sem linhas")
    return out


def _quote(ctx: Ctx, m: dict, fx: dict) -> dict:
    """Tenta Stooq, EIA, FRED, Yahoo e por fim uma fonte só de preço (câmbio aberto ou JSON)."""
    errors = []
    if m.get("stooq"):
        try:
            d1 = (ctx.now - timedelta(days=45)).strftime("%Y%m%d")
            resp = ctx.client.get(STOOQ_URL, params={"s": m["stooq"], "i": "d", "d1": d1, "d2": ctx.now.strftime("%Y%m%d")})
            resp.raise_for_status()
            return series_summary(parse_stooq(resp.text))
        except Exception as exc:
            errors.append(f"stooq: {exc}")
    if m.get("eia"):
        try:
            resp = ctx.client.get(EIA_URL, params={"n": "PET", "s": m["eia"], "f": "D"}, timeout=40)
            resp.raise_for_status()
            return series_summary(parse_eia(resp.text))
        except Exception as exc:
            errors.append(f"eia: {exc}")
    if m.get("fred"):
        try:
            cosd = (ctx.now - timedelta(days=60)).date().isoformat()
            resp = ctx.client.get(FRED_URL, params={"id": m["fred"], "cosd": cosd})
            resp.raise_for_status()
            return series_summary(parse_fred(resp.text))
        except Exception as exc:
            errors.append(f"fred: {exc}")
    if m.get("symbol"):
        for url in YAHOO_URLS:
            try:
                payload = _get_json(ctx.client, url.format(symbol=m["symbol"]), params={"range": "1mo", "interval": "1d"})
                return quote_summary(payload)
            except Exception as exc:
                errors.append(f"yahoo: {type(exc).__name__}")
    price = None
    if m.get("fx") and fx.get(m["fx"]):
        price = float(fx[m["fx"]])
    elif m.get("json"):
        try:
            price = float(_path(_get_json(ctx.client, m["json"]["url"]), m["json"]["path"]))
        except Exception as exc:
            errors.append(f"json: {exc}")
    if price is None:
        raise RuntimeError("; ".join(errors)[:200])
    # Só o preço de agora: a variação sai do histórico que o próprio Argos guarda.
    days = ctx.state.setdefault("history", {}).setdefault(m["id"], {})
    days[ctx.now.date().isoformat()] = price
    for d in sorted(days)[:-MARKET_KEEP_DAYS]:
        del days[d]
    return series_summary(sorted(days.items()))


def collect_markets(ctx: Ctx) -> dict:
    fx: dict = {}
    if any(m.get("fx") for m in ctx.conf["items"]):
        try:
            fx = _get_json(ctx.client, FX_URL).get("rates") or {}
        except Exception as exc:
            log.info("câmbio: %s", exc)
    items, errors = [], 0
    for m in ctx.conf["items"]:
        entry = {"id": m["id"], "name": m["name"], "unit": m.get("unit", ""), "digits": int(m.get("digits", 2))}
        try:
            entry.update(_quote(ctx, m, fx))
        except Exception as exc:
            errors += 1
            log.info("cotação %s: %s", m["id"], exc)
            entry["error"] = True
        items.append(entry)
    if errors == len(items):
        raise RuntimeError("cotações indisponíveis")
    return {"items": items}


# ---------------------------------------------------------------------------
# Números humanitários (Tech for Palestine + HDX HAPI) e perdas declaradas (Ucrânia)
# ---------------------------------------------------------------------------

TFP_URLS = (
    "https://data.techforpalestine.org/api/v3/summary.json",
    "https://data.techforpalestine.org/api/v2/summary.json",
)
# (região, rótulo, caminhos possíveis no JSON)
TFP_FIELDS = [
    ("gaza", "Mortos", ["gaza.killed.total", "killed.total"]),
    ("gaza", "Crianças mortas", ["gaza.killed.children"]),
    ("gaza", "Mulheres mortas", ["gaza.killed.women"]),
    ("gaza", "Feridos", ["gaza.injured.total", "injured.total"]),
    ("gaza", "Jornalistas mortos", ["gaza.killed.press", "gaza.killed.journalists", "known_press_killed_in_gaza"]),
    ("gaza", "Profissionais de saúde mortos", ["gaza.killed.medical", "gaza.killed.health_workers"]),
    ("gaza", "Socorristas mortos", ["gaza.killed.civil_defence", "gaza.killed.civil_defense"]),
    ("gaza", "Mortes por fome", ["gaza.famine.total", "gaza.killed.starvation", "gaza.killed.famine", "gaza.famine"]),
    ("cisjordania", "Mortos", ["west_bank.killed.total"]),
    ("cisjordania", "Crianças mortas", ["west_bank.killed.children"]),
    ("cisjordania", "Feridos", ["west_bank.injured.total"]),
    ("cisjordania", "Ataques de colonos", ["west_bank.settler_attacks"]),
]
TFP_DATES = {"gaza": ["gaza.last_update"], "cisjordania": ["west_bank.last_update"]}

HAPI_URL = "https://hapi.humdata.org/api/v2/affected-people/idps"
HAPI_APP = base64.b64encode(b"argos:argos-app@users.noreply.github.com").decode()


def _path(data, path: str):
    for part in path.split("."):
        if not isinstance(data, dict) or part not in data:
            return None
        data = data[part]
    return data


def tfp_figures(payload: dict) -> list[dict]:
    out = []
    for region, label, paths in TFP_FIELDS:
        for p in paths:
            value = _path(payload, p)
            if isinstance(value, dict):
                value = value.get("total")
            if isinstance(value, (int, float)) and not isinstance(value, bool) and value > 0:
                date = next((d for d in (_path(payload, q) for q in TFP_DATES[region]) if isinstance(d, str)), "")
                out.append({"region": region, "label": label, "value": int(value), "date": date[:10]})
                break
    return out


def idps_total(rows: list[dict]) -> tuple[int, str]:
    """Total de deslocados no período mais recente. Linhas nacionais valem mais que a soma das províncias."""
    rows = [r for r in rows if isinstance(r.get("population"), (int, float))]
    if not rows:
        raise ValueError("sem linhas")
    latest = max(str(r.get("reference_period_end") or r.get("reference_period_start") or "") for r in rows)
    current = [r for r in rows if str(r.get("reference_period_end") or r.get("reference_period_start") or "") == latest]
    national = [r for r in current if not r.get("admin1_code") and not r.get("admin1_name")]
    if national:
        return int(max(r["population"] for r in national)), latest[:10]
    by_admin1: dict[str, float] = {}
    for r in current:
        k = str(r.get("admin1_code") or r.get("admin1_name"))
        by_admin1[k] = max(by_admin1.get(k, 0), r["population"])
    return int(sum(by_admin1.values())), latest[:10]


def collect_humanitarian(ctx: Ctx) -> dict:
    result: dict = {}
    for url in TFP_URLS:
        try:
            figures = tfp_figures(_get_json(ctx.client, url))
            if figures:
                result["palestine"] = figures
                break
        except Exception as exc:
            log.info("Tech for Palestine %s: %s", url, exc)
    idps = []
    for loc in ctx.conf.get("idps", []):
        try:
            payload = _get_json(ctx.client, HAPI_URL, params={
                "location_code": loc["location"], "output_format": "json", "limit": 10000, "app_identifier": HAPI_APP,
            })
            value, date = idps_total(payload.get("data") or [])
            idps.append({"location": loc["location"], "name": loc["name"], "tag": loc["tag"], "value": value, "date": date})
        except Exception as exc:
            log.info("HAPI %s: %s", loc["location"], exc)
    if idps:
        result["idps"] = idps
    if not result:
        raise RuntimeError("nenhuma fonte humanitária respondeu")
    # Se só uma parte respondeu, a outra fica com o dado anterior.
    for part in ("palestine", "idps"):
        if part not in result and ctx.prev and part in ctx.prev:
            result[part] = ctx.prev[part]
    return result


LOSSES_URL = "https://russianwarship.rip/api/v2/statistics/latest"
LOSS_LABELS = {
    "personnel_units": "Militares",
    "tanks": "Tanques",
    "armoured_fighting_vehicles": "Blindados",
    "artillery_systems": "Sistemas de artilharia",
    "mlrs": "Lança-foguetes múltiplos",
    "aa_warfare_systems": "Defesa antiaérea",
    "planes": "Aviões",
    "helicopters": "Helicópteros",
    "uav_systems": "Drones",
    "cruise_missiles": "Mísseis de cruzeiro",
    "warships_cutters": "Navios e lanchas",
    "submarines": "Submarinos",
    "vehicles_fuel_tanks": "Veículos e caminhões-tanque",
    "special_military_equip": "Equipamento especial",
}


def losses_summary(payload: dict) -> dict:
    data = payload.get("data") or payload
    stats, inc = data["stats"], data.get("increase") or {}
    items = [
        {"key": k, "label": label, "total": int(stats[k]), "increase": int(inc.get(k) or 0)}
        for k, label in LOSS_LABELS.items()
        if isinstance(stats.get(k), (int, float))
    ]
    if not items:
        raise ValueError("sem números")
    return {"date": str(data.get("date", ""))[:10], "day": data.get("day"), "items": items}


def collect_losses(ctx: Ctx) -> dict:
    return losses_summary(_get_json(ctx.client, LOSSES_URL))


# ---------------------------------------------------------------------------
# Vozes: fontes oficiais, sanções, análises e checagens (RSS)
# ---------------------------------------------------------------------------

# Tags amplas demais para, sozinhas, pôr uma checagem ou comunicado no Radar (Trump, OTAN...).
BROAD_TAGS = {"eua", "otan", "asia", "africa"}


def _keep(section: str, source: dict, m, text: str) -> bool:
    """Filtro por seção: `all` aceita tudo; `match` (regex) aceita o que citar o termo."""
    if source.get("all"):
        return True
    if source.get("match") and re.search(source["match"], text, re.I):
        return True
    specific = bool(m.tags - BROAD_TAGS)
    if section == "official":
        return m.relevant and (specific or len(m.war_terms) >= 2)
    return m.relevant or specific


RSS_WINDOW = {"official": 7, "sanctions": 14, "analysis": 10, "factcheck": 21}
RSS_MAX = 40


def rss_items(ctx: Ctx, section: str) -> dict:
    window = timedelta(days=RSS_WINDOW.get(section, 7))
    by_id: dict[str, dict] = {}
    for item in (ctx.prev or {}).get("items", []):
        if ctx.now - parse_iso(item["published"]) <= window:
            by_id[item["id"]] = item
    sources, ok = {}, 0
    for source in ctx.conf["sources"]:
        arts, error = fetch_source(ctx.client, source, ctx.now)
        sources[source["name"]] = error is None
        ok += error is None
        for a in arts:
            if ctx.now - a.published > window:
                continue
            m = ctx.kw.match(a.title, a.summary)
            if not _keep(section, source, m, f"{a.title} {a.summary}"):
                continue
            old = by_id.get(a.id)
            by_id[a.id] = {
                "id": a.id,
                "title": a.title,
                "summary": truncate(a.summary, 320),
                "url": a.url,
                "source": a.source,
                "lang": a.lang,
                "published": old["published"] if old else iso(a.published),
                "tags": sorted(m.tags),
            }
    if ok == 0:
        raise RuntimeError("nenhuma fonte respondeu")
    items = sorted(by_id.values(), key=lambda i: i["published"], reverse=True)[:RSS_MAX]
    return {"items": items, "sources": sources}


def collect_official(ctx: Ctx) -> dict:
    return rss_items(ctx, "official")


def collect_sanctions(ctx: Ctx) -> dict:
    return rss_items(ctx, "sanctions")


def collect_analysis(ctx: Ctx) -> dict:
    return rss_items(ctx, "analysis")


def collect_factcheck(ctx: Ctx) -> dict:
    return rss_items(ctx, "factcheck")


# ---------------------------------------------------------------------------
# CrisisWatch (Crisis Group)
# ---------------------------------------------------------------------------

CW_HEADINGS = {
    "deteriorated": r"Deteriorated\s+Situations?",
    "improved": r"Improved\s+Situations?",
    "risk": r"Conflict\s+Risk\s+Alerts?",
    "resolution": r"Resolution\s+Opportunit(?:y|ies)",
}
_SCRIPT_RE = re.compile(r"<(script|style)\b.*?</\1>", re.S | re.I)
_MONTH_RE = re.compile(r"\b(January|February|March|April|May|June|July|August|September|October|November|December)\s+(20\d\d)\b")
MONTHS_PT = {
    "January": "janeiro", "February": "fevereiro", "March": "março", "April": "abril", "May": "maio", "June": "junho",
    "July": "julho", "August": "agosto", "September": "setembro", "October": "outubro", "November": "novembro",
    "December": "dezembro",
}


def crisiswatch_trends(html: str, countries: dict[str, str]) -> dict:
    """Lê as listas "Deteriorated/Improved Situations" etc. da página do CrisisWatch."""
    text = clean_html(_SCRIPT_RE.sub(" ", html))
    marks = []
    for key, rx in CW_HEADINGS.items():
        m = re.search(rx, text, re.I)
        if m:
            marks.append((m.start(), m.end(), key))
    if not marks:
        raise ValueError("estrutura da página mudou")
    marks.sort()
    result: dict = {k: [] for k in CW_HEADINGS}
    for i, (_, end, key) in enumerate(marks):
        stop = marks[i + 1][0] if i + 1 < len(marks) else end + 800
        segment = text[end:min(stop, end + 800)]
        seen = set()
        for name, tag in countries.items():
            if re.search(r"(?<!\w)" + re.escape(name) + r"(?!\w)", segment) and tag not in seen:
                seen.add(tag)
                result[key].append({"name": name, "tag": tag})
    month = _MONTH_RE.search(text)
    result["month"] = f"{MONTHS_PT[month.group(1)]} de {month.group(2)}" if month else ""
    return result


MONTHS_EN = list(MONTHS_PT)
WAYBACK_API = "https://archive.org/wayback/available"
_CW_ENTRY = re.compile(
    r'<div title="(\w+ \d{4})" class="o-state-entry[^"]*">\s*<a href="[^"#]*#([a-z0-9-]+)">((?:\s*<span class="state-[a-z-]+"></span>)+)'
)
_CW_STATE = re.compile(r'state-([a-z-]+)')
_CW_BUCKET = {"deteriorated": "deteriorated", "improved": "improved", "risk-alert": "risk", "resolution-opportunity": "resolution"}


def _month_key(label: str) -> tuple[int, int]:
    name, year = label.split()
    return int(year), MONTHS_EN.index(name) + 1 if name in MONTHS_EN else 0


def crisiswatch_entries(html: str, countries: dict[str, str]) -> dict:
    """Página do CrisisWatch com as entradas por país (div.o-state-entry, uma por mês).

    Usa só o mês mais recente da página; o país vem da âncora do link (#yemen, #israel-palestine).
    """
    entries = [(month, slug, _CW_STATE.findall(spans)) for month, slug, spans in _CW_ENTRY.findall(html)]
    entries = [e for e in entries if e[0].split()[0] in MONTHS_EN]
    if not entries:
        raise ValueError("entradas do CrisisWatch não encontradas")
    latest = max((e[0] for e in entries), key=_month_key)
    result: dict = {k: [] for k in CW_HEADINGS}
    seen: set[tuple[str, str]] = set()
    for month, slug, states in entries:
        if month != latest:
            continue
        for name, tag in countries.items():
            if name.lower().replace(" ", "-") not in slug:
                continue
            for state in states:
                bucket = _CW_BUCKET.get(state)
                if bucket and (bucket, tag) not in seen:
                    seen.add((bucket, tag))
                    result[bucket].append({"name": name, "tag": tag})
    name, year = latest.split()
    result["month"] = f"{MONTHS_PT[name]} de {year}"
    return result


def _wayback_html(client: httpx.Client, url: str) -> str:
    """Cópia mais recente da página no Internet Archive (o site bloqueia o GitHub)."""
    info = _get_json(client, WAYBACK_API, params={"url": url.split("://", 1)[-1]})
    snap = ((info.get("archived_snapshots") or {}).get("closest") or {})
    if not snap.get("available") or not snap.get("timestamp"):
        raise ValueError("sem cópia no Internet Archive")
    resp = client.get(f"https://web.archive.org/web/{snap['timestamp']}id_/{url}", timeout=60)
    resp.raise_for_status()
    raw = resp.content
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return raw.decode("utf-8", "replace")


def _month_pages(now: datetime) -> list[str]:
    """Páginas da edição deste mês e do anterior (ex.: /crisiswatch/september-2026)."""
    first = now.replace(day=1)
    prev = (first - timedelta(days=1)).replace(day=1)
    return [f"https://www.crisisgroup.org/crisiswatch/{MONTHS_EN[d.month - 1].lower()}-{d.year}" for d in (first, prev)]


def collect_crisiswatch(ctx: Ctx) -> dict:
    urls = ctx.conf["url"] if isinstance(ctx.conf["url"], list) else [ctx.conf["url"]]
    urls = urls + _month_pages(ctx.now)
    last: Exception = RuntimeError("sem URL")
    for url in urls:
        try:
            resp = ctx.client.get(url)
            resp.raise_for_status()
            data = crisiswatch_trends(resp.text, ctx.conf["countries"])
            data["url"] = url
            return data
        except Exception as exc:
            last = exc
    # Cópia do Internet Archive da página principal (tem as entradas por país).
    try:
        data = crisiswatch_entries(_wayback_html(ctx.client, urls[0]), ctx.conf["countries"])
        data["url"] = urls[0]
        return data
    except Exception as exc:
        log.info("CrisisWatch pelo Internet Archive: %s", exc)
        last = exc
    # A página bloqueia robôs às vezes: procura a edição do mês no RSS do Crisis Group.
    for source in ctx.conf.get("feeds", []):
        arts, _ = fetch_source(ctx.client, source, ctx.now)
        for a in sorted(arts, key=lambda a: a.published, reverse=True):
            # Só a edição do mês ("CrisisWatch September 2026"), não páginas genéricas.
            if "crisiswatch" not in a.title.lower() or not _MONTH_RE.search(a.title):
                continue
            try:
                data = crisiswatch_trends(f"{a.title} {a.summary}", ctx.conf["countries"])
            except ValueError:
                data = {k: [] for k in CW_HEADINGS}
                month = _MONTH_RE.search(a.title)
                data["month"] = f"{MONTHS_PT[month.group(1)]} de {month.group(2)}" if month else ""
            data.update({"url": a.url, "title": a.title, "summary": truncate(a.summary, 600)})
            return data
    raise last


# ---------------------------------------------------------------------------
# Mercados de previsão (Polymarket)
# ---------------------------------------------------------------------------

POLYMARKET_URL = "https://gamma-api.polymarket.com/events"


def _as_list(value) -> list:
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except ValueError:
            return []
    return value if isinstance(value, list) else []


def prediction_events(events: list[dict], kw: Keywords, limit: int) -> list[dict]:
    out = []
    for ev in events:
        title = ev.get("title") or ""
        m = kw.match(title, "")
        if not m.tags or ev.get("closed"):
            continue
        markets = []
        for mk in ev.get("markets") or []:
            if mk.get("closed"):
                continue
            prices, outcomes = _as_list(mk.get("outcomePrices")), _as_list(mk.get("outcomes"))
            if not prices:
                continue
            idx = outcomes.index("Yes") if "Yes" in outcomes else 0
            try:
                prob = float(prices[idx])
            except (ValueError, IndexError):
                continue
            change = mk.get("oneDayPriceChange")
            markets.append({
                "label": mk.get("groupItemTitle") or mk.get("question") or title,
                "prob": round(prob, 3),
                "change": round(float(change), 3) if isinstance(change, (int, float)) else None,
                "volume": float(mk.get("volume") or 0),
            })
        if not markets:
            continue
        # Os 4 de maior volume, na ordem original (costuma ser a cronológica).
        top = {id(mk) for mk in sorted(markets, key=lambda x: x["volume"], reverse=True)[:4]}
        markets = [mk for mk in markets if id(mk) in top]
        out.append({
            "id": str(ev.get("id") or ev.get("slug")),
            "title": title,
            "url": f"https://polymarket.com/event/{ev.get('slug', '')}",
            "volume": float(ev.get("volume") or 0),
            "volume24": float(ev.get("volume24hr") or 0),
            "end": str(ev.get("endDate") or "")[:10],
            "tags": sorted(m.tags),
            "markets": [{k: v for k, v in mk.items() if k != "volume"} for mk in markets],
        })
    out.sort(key=lambda e: (e["volume24"], e["volume"]), reverse=True)
    return out[:limit]


def collect_predictions(ctx: Ctx) -> dict:
    events: list[dict] = []
    for params in ({"tag_slug": "geopolitics"}, {}):
        base = {"closed": "false", "active": "true", "limit": 200, "order": "volume24hr", "ascending": "false"}
        payload = _get_json(ctx.client, POLYMARKET_URL, params={**base, **params})
        events = payload if isinstance(payload, list) else payload.get("data") or []
        found = prediction_events(events, ctx.kw, int(ctx.conf.get("max_events", 12)))
        if found:
            return {"events": found}
    raise RuntimeError("nenhum mercado sobre as guerras")


# ---------------------------------------------------------------------------
# Aviões militares (adsb.lol)
# ---------------------------------------------------------------------------

MIL_URL = "https://api.adsb.lol/v2/mil"
# Tipo ICAO -> categoria. Os que mais dizem algo antes de um ataque: reabastecedores,
# aviões-radar, espionagem/drones e bombardeiros.
MIL_TYPES = {
    "reabastecedor": ["K35R", "K35E", "KC46", "K46", "DC10", "KC10", "A332", "A310", "B762", "K767", "IL78"],
    "radar": ["E3TF", "E3CF", "E767", "E7", "E737", "A50", "E2", "GLEX", "SB3"],
    "espionagem": ["R135", "P8", "EP3", "U2", "Q4", "RQ4", "MQ9", "Q9", "HRON", "MQ4C", "G550", "CL60", "B350", "PC12"],
    "bombardeiro": ["B52", "B1", "B2", "TU95", "T160", "TU22"],
    "caça": ["F15", "F16", "F18", "F22", "F35", "EUFI", "RFAL", "SU27", "SU30", "SU34", "SU35", "MG29", "MG31"],
    "transporte": ["C17", "C5M", "C30J", "C130", "A400", "IL76", "C2", "KC390"],
}
MIL_LABELS = {
    "reabastecedor": "Reabastecedor", "radar": "Avião-radar", "espionagem": "Espionagem/drone",
    "bombardeiro": "Bombardeiro", "caça": "Caça", "transporte": "Transporte", "outro": "Outro militar",
}
MIL_KEEP_DAYS = 14


def mil_category(ac_type: str) -> str:
    t = (ac_type or "").upper()
    for cat, types in MIL_TYPES.items():
        if t in types:
            return cat
    return "outro"


def military_in_zones(payload: dict, zones: list[dict]) -> dict[str, list[dict]]:
    """Aeronaves com posição dentro de cada zona."""
    out: dict[str, list[dict]] = {z["id"]: [] for z in zones}
    for ac in payload.get("ac") or []:
        lat, lon = ac.get("lat"), ac.get("lon")
        if not isinstance(lat, (int, float)) or not isinstance(lon, (int, float)):
            continue
        for z in zones:
            s, w, n, e = z["box"]
            if s <= lat <= n and w <= lon <= e:
                alt = ac.get("alt_baro")
                out[z["id"]].append({
                    "hex": ac.get("hex", ""),
                    "callsign": (ac.get("flight") or "").strip(),
                    "type": ac.get("t") or "",
                    "reg": ac.get("r") or "",
                    "category": mil_category(ac.get("t") or ""),
                    "lat": round(lat, 3),
                    "lon": round(lon, 3),
                    "alt": alt if isinstance(alt, (int, float)) else 0,
                    "track": round(ac["track"]) if isinstance(ac.get("track"), (int, float)) else None,
                })
                break
    return out


def collect_military(ctx: Ctx) -> dict:
    payload = _get_json(ctx.client, MIL_URL)
    found = military_in_zones(payload, ctx.conf["zones"])
    today = ctx.now.date().isoformat()
    zones = []
    for z in ctx.conf["zones"]:
        aircraft = found[z["id"]]
        counts: dict[str, int] = {}
        for a in aircraft:
            counts[a["category"]] = counts.get(a["category"], 0) + 1
        # Linha de base: média do máximo diário de "aviões que importam" (sem caças/transporte/outros).
        key = sum(v for k, v in counts.items() if k in ("reabastecedor", "radar", "espionagem", "bombardeiro"))
        days = ctx.state.setdefault(z["id"], {})
        past = [v for d, v in days.items() if d != today]
        days[today] = max(days.get(today, 0), key)
        for d in sorted(days)[:-MIL_KEEP_DAYS]:
            del days[d]
        baseline = round(sum(past) / len(past), 1) if past else None
        zones.append({
            "id": z["id"], "name": z["name"], "tag": z["tag"],
            "count": len(aircraft), "key": key, "baseline": baseline,
            "unusual": baseline is not None and len(past) >= 3 and key >= max(4, 2 * baseline),
            "counts": counts,
            "aircraft": sorted(aircraft, key=lambda a: list(MIL_LABELS).index(a["category"]))[:80],
        })
    return {"zones": zones, "labels": MIL_LABELS}


# ---------------------------------------------------------------------------
# Porta-aviões (USNI News Fleet Tracker)
# ---------------------------------------------------------------------------

CARRIER_NAMES = {
    68: "USS Nimitz", 69: "USS Dwight D. Eisenhower", 70: "USS Carl Vinson", 71: "USS Theodore Roosevelt",
    72: "USS Abraham Lincoln", 73: "USS George Washington", 74: "USS John C. Stennis", 75: "USS Harry S. Truman",
    76: "USS Ronald Reagan", 77: "USS George H.W. Bush", 78: "USS Gerald R. Ford", 79: "USS John F. Kennedy",
}
# Nome do lugar (inglês, como a USNI escreve) -> (nome em português, lat, lon). Os mais específicos primeiro.
CARRIER_PLACES = [
    ("Eastern Mediterranean", "Mediterrâneo Oriental", 33.8, 32.5), ("Mediterranean", "Mediterrâneo", 36.0, 18.0),
    ("Red Sea", "Mar Vermelho", 20.5, 38.5), ("Gulf of Aden", "Golfo de Áden", 12.5, 47.5),
    ("Arabian Sea", "Mar Arábico", 16.0, 63.0), ("Gulf of Oman", "Golfo de Omã", 24.5, 58.5),
    ("Persian Gulf", "Golfo Pérsico", 27.0, 51.5), ("Arabian Gulf", "Golfo Pérsico", 27.0, 51.5),
    ("North Arabian Sea", "Mar Arábico", 20.0, 63.0), ("Indian Ocean", "Oceano Índico", -5.0, 75.0),
    ("Adriatic", "Mar Adriático", 42.5, 16.0), ("North Sea", "Mar do Norte", 56.0, 3.0),
    ("Norwegian Sea", "Mar da Noruega", 68.0, 5.0), ("Baltic", "Mar Báltico", 57.0, 19.0),
    ("Caribbean", "Caribe", 15.0, -72.0), ("South China Sea", "Mar do Sul da China", 12.0, 114.0),
    ("East China Sea", "Mar da China Oriental", 29.0, 125.0), ("Philippine Sea", "Mar das Filipinas", 20.0, 130.0),
    ("Sea of Japan", "Mar do Japão", 40.0, 135.0), ("Western Pacific", "Pacífico Ocidental", 15.0, 140.0),
    ("Eastern Pacific", "Pacífico Oriental", 22.0, -125.0), ("Atlantic", "Atlântico", 35.0, -45.0),
    ("Pacific", "Pacífico", 20.0, -150.0), ("San Diego", "San Diego (base)", 32.7, -117.2),
    ("Norfolk", "Norfolk (base)", 36.9, -76.3), ("Newport News", "Newport News (estaleiro)", 36.98, -76.43),
    ("Bremerton", "Bremerton (base)", 47.56, -122.63), ("Everett", "Everett (base)", 47.98, -122.22),
    ("Yokosuka", "Yokosuka (Japão)", 35.28, 139.67), ("Pearl Harbor", "Pearl Harbor", 21.35, -157.95),
    ("Guam", "Guam", 13.44, 144.66), ("Souda Bay", "Baía de Souda (Creta)", 35.48, 24.12),
    ("Duqm", "Duqm (Omã)", 19.67, 57.7), ("Bahrain", "Bahrein", 26.2, 50.6),
]
_CVN = re.compile(r"\(CVN[- ]?(\d{2})\)")
_SENT = re.compile(r"(?<=[.!?])\s+")


def carriers_from_tracker(html: str) -> list[dict]:
    """Posição de cada porta-aviões citado no texto do Fleet Tracker (frase do casco + lugar)."""
    text = clean_html(_SCRIPT_RE.sub(" ", html))
    sentences = _SENT.split(text)
    found: dict[int, dict] = {}
    for i, sent in enumerate(sentences):
        for m in _CVN.finditer(sent):
            hull = int(m.group(1))
            if hull in found or hull not in CARRIER_NAMES:
                continue
            window = " ".join(sentences[i:i + 2])
            place = next(((pt, lat, lon) for en, pt, lat, lon in CARRIER_PLACES if en in window), None)
            if place is None:
                continue
            status = "no porto" if re.search(r"\b(in port|pierside|homeport|shipyard|maintenance|RCOH)\b", window, re.I) else "no mar"
            found[hull] = {
                "hull": f"CVN-{hull}", "name": CARRIER_NAMES[hull], "place": place[0],
                "lat": place[1], "lon": place[2], "status": status, "text": truncate(sent, 260),
            }
    return sorted(found.values(), key=lambda c: c["hull"])


def collect_carriers(ctx: Ctx) -> dict:
    resp = ctx.client.get(ctx.conf["url"])
    resp.raise_for_status()
    import feedparser  # já é dependência do fetch

    feed = feedparser.parse(resp.content)
    for entry in feed.entries:
        if "tracker" not in (entry.get("title") or "").lower():
            continue
        html = "".join(c.get("value", "") for c in entry.get("content") or []) or entry.get("summary", "")
        ships = carriers_from_tracker(html)
        if ships:
            return {"title": entry.get("title", ""), "url": entry.get("link", ""), "ships": ships}
    raise RuntimeError("Fleet Tracker sem porta-aviões reconhecidos")


# ---------------------------------------------------------------------------
# Linha de frente na Ucrânia (DeepStateMap)
# ---------------------------------------------------------------------------

DSM_LAST = "https://deepstatemap.live/api/history/last"
DSM_HISTORY = "https://deepstatemap.live/api/history/public"
DSM_OCCUPIED = ("geoJSON.status.occupied", "geoJSON.territories.crimea", "geoJSON.territories.ordlo", "geoJSON.territories.tuzla")
DSM_GREY = ("geoJSON.status.unknown",)
DSM_TOLERANCE = 0.01  # graus (~1 km): simplificação das bordas para o app
DSM_MAX_POINTS = 9000
_TAG_RE = re.compile(r"<[^>]+>")


def _rings(geometry: dict) -> list[list[list[list[float]]]]:
    """Polygon/MultiPolygon -> lista de polígonos, cada um uma lista de anéis [[lon, lat], ...]."""
    coords = geometry.get("coordinates") or []
    if geometry.get("type") == "Polygon":
        return [coords]
    if geometry.get("type") == "MultiPolygon":
        return list(coords)
    return []


def ring_area_km2(ring: list) -> float:
    """Área de um anel (lon/lat) em km², projeção equirretangular local (boa para a escala da frente)."""
    if len(ring) < 3:
        return 0.0
    lat0 = math.radians(sum(p[1] for p in ring) / len(ring))
    kx, ky = 111.32 * math.cos(lat0), 110.57
    total = 0.0
    for (x1, y1, *_), (x2, y2, *_) in zip(ring, ring[1:] + ring[:1]):
        total += (x1 * kx) * (y2 * ky) - (x2 * kx) * (y1 * ky)
    return abs(total) / 2


def simplify(points: list, tol: float) -> list:
    """Ramer–Douglas–Peucker iterativo (em graus)."""
    if len(points) < 4:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (ax, ay), (bx, by) = points[a][:2], points[b][:2]
        dx, dy = bx - ax, by - ay
        norm = math.hypot(dx, dy)
        best, idx = 0.0, -1
        for i in range(a + 1, b):
            px, py = points[i][:2]
            # Anel fechado (início = fim): usa a distância até o ponto, não até a reta.
            d = abs(dy * px - dx * py + bx * ay - by * ax) / norm if norm > 1e-12 else math.hypot(px - ax, py - ay)
            if d > best:
                best, idx = d, i
        if best > tol and idx > 0:
            keep[idx] = True
            stack += [(a, idx), (idx, b)]
    return [p for p, k in zip(points, keep) if k]


def frontline_summary(data: dict) -> dict:
    """Área ocupada e cinzenta (km²) e os polígonos simplificados ([lat, lon]) para o mapa."""
    occupied, grey, total_occ, total_grey = [], [], 0.0, 0.0
    for f in (data.get("map") or {}).get("features") or []:
        name = (f.get("properties") or {}).get("name", "")
        is_occ = any(k in name for k in DSM_OCCUPIED)
        is_grey = any(k in name for k in DSM_GREY)
        if not (is_occ or is_grey):
            continue
        for poly in _rings(f.get("geometry") or {}):
            if not poly:
                continue
            area = ring_area_km2(poly[0]) - sum(ring_area_km2(h) for h in poly[1:])
            rings = []
            for ring in poly:
                pts = simplify([p[:2] for p in ring], DSM_TOLERANCE)
                if len(pts) >= 4:
                    rings.append([[round(lat, 3), round(lon, 3)] for lon, lat in pts])
            if not rings:
                continue
            if is_occ:
                total_occ += area
                occupied.append(rings)
            else:
                total_grey += area
                grey.append(rings)
    if not occupied:
        raise ValueError("nenhum polígono ocupado")
    # Limite de pontos: corta os polígonos menores primeiro.
    def points(polys):
        return sum(len(r) for p in polys for r in p)
    occupied.sort(key=lambda p: -len(p[0]))
    while points(occupied) + points(grey) > DSM_MAX_POINTS and (grey or len(occupied) > 1):
        (grey if grey else occupied).pop()
    return {
        "occupied_km2": round(total_occ),
        "grey_km2": round(total_grey),
        "occupied": occupied,
        "grey": grey,
    }


def dsm_changes(history: list[dict], limit: int = 5) -> list[dict]:
    """Últimas mudanças descritas pelo DeepStateMap (em inglês, quando houver)."""
    out = []
    for h in sorted(history, key=lambda h: h.get("createdAt", ""), reverse=True):
        text = _TAG_RE.sub("", h.get("descriptionEn") or "").strip()
        if text:
            out.append({"text": truncate(text, 240), "at": h.get("createdAt", "")[:19] + "Z"})
        if len(out) >= limit:
            break
    return out


def collect_frontline(ctx: Ctx) -> dict:
    data = _get_json(ctx.client, DSM_LAST, timeout=60)
    result = frontline_summary(data)
    today = ctx.now.date().isoformat()
    days = ctx.state.setdefault("occupied", {})
    days[today] = result["occupied_km2"]
    for d in sorted(days)[:-40]:
        del days[d]
    week_ago = (ctx.now.date() - timedelta(days=7)).isoformat()
    older = [d for d in sorted(days) if d <= week_ago]
    result["change_7d_km2"] = result["occupied_km2"] - days[older[-1]] if older else None
    result["history"] = [[d, days[d]] for d in sorted(days)[-30:]]
    try:
        result["changes"] = dsm_changes(_get_json(ctx.client, DSM_HISTORY, timeout=60))
    except Exception as exc:
        log.info("DeepStateMap histórico: %s", exc)
    return result


# ---------------------------------------------------------------------------
# Orquestração
# ---------------------------------------------------------------------------

# Seções que dependem de um secret: se ele aparecer, a seção coleta na hora.
MISSING_KEY_ENV = {"fires": "FIRMS_MAP_KEY"}

COLLECTORS = {
    "internet": collect_internet,
    "airspace": collect_airspace,
    "fires": collect_fires,
    "straits": collect_straits,
    "markets": collect_markets,
    "humanitarian": collect_humanitarian,
    "losses": collect_losses,
    "official": collect_official,
    "sanctions": collect_sanctions,
    "analysis": collect_analysis,
    "crisiswatch": collect_crisiswatch,
    "factcheck": collect_factcheck,
    "predictions": collect_predictions,
    "military": collect_military,
    "carriers": collect_carriers,
    "frontline": collect_frontline,
}


def collect(out: Path, now: datetime, kw: Keywords, config: dict | None = None, client: httpx.Client | None = None) -> dict:
    """Roda as seções vencidas; as outras repetem o último dado. Devolve o radar (sem gravar)."""
    config = config if config is not None else load_config()
    prev = _read(out / "radar.json")
    prev_status = prev.get("status", {})
    state_path = out / "stats" / "radar_state.json"
    state = _read(state_path)
    radar: dict = {"version": 1, "generated_at": iso(now)}
    status: dict = {}
    due = []
    for name in COLLECTORS:
        conf = config.get(name)
        if conf is None:
            continue
        st = prev_status.get(name, {})
        interval = timedelta(minutes=float(conf.get("interval", 60)))
        wait = interval if st.get("ok") else min(interval, RETRY_FAILED)
        checked = st.get("checked")
        # A chave (secret) acabou de ser configurada: não espera o intervalo.
        key_added = (prev.get(name) or {}).get("missing_key") and os.environ.get(MISSING_KEY_ENV.get(name, ""), "").strip()
        # CrisisWatch salvo sem a edição do mês (versão anterior pegava páginas genéricas): refaz.
        bad_cw = name == "crisiswatch" and name in prev and not (prev[name] or {}).get("month")
        if checked and now - parse_iso(checked) < wait and not key_added and not bad_cw:
            if name in prev:
                radar[name] = prev[name]
            status[name] = st
            continue
        due.append(name)

    own_client = client is None
    if own_client:
        client = httpx.Client(headers={"User-Agent": USER_AGENT}, timeout=TIMEOUT, follow_redirects=True)
    try:
        def run(name: str):
            ctx = Ctx(client, now, kw, config[name], prev.get(name), state.setdefault(name, {}))
            try:
                return name, COLLECTORS[name](ctx), None
            except Exception as exc:  # uma seção quebrada não derruba as outras
                return name, None, exc

        with ThreadPoolExecutor(max_workers=6) as pool:
            for name, data, exc in pool.map(run, due):
                if exc is None:
                    data["updated"] = iso(now)
                    radar[name] = data
                    status[name] = {"ok": True, "checked": iso(now)}
                else:
                    log.warning("radar/%s: %s", name, exc)
                    if name in prev and not (name == "crisiswatch" and not (prev[name] or {}).get("month")):
                        radar[name] = prev[name]
                    status[name] = {"ok": False, "checked": iso(now), "error": f"{type(exc).__name__}: {exc}"[:300]}
    finally:
        if own_client:
            client.close()

    radar["status"] = status
    state_path.parent.mkdir(parents=True, exist_ok=True)
    state_path.write_text(json.dumps(state, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    ok = sum(1 for s in status.values() if s.get("ok"))
    log.info("radar: %d/%d seções ok (%d coletadas agora)", ok, len(status), len(due))
    return radar


# ---------------------------------------------------------------------------
# Ligações com o feed
# ---------------------------------------------------------------------------

SIGNAL_BOOST = {"apagao": 10, "queda": 5, "fechado": 10, "reduzido": 5}


def _fresh(section: dict | None, now: datetime) -> bool:
    return bool(section) and "updated" in section and now - parse_iso(section["updated"]) <= SIGNAL_MAX_AGE


def apply_signals(regions: dict, radar: dict, now: datetime) -> None:
    """Apagão de internet e espaço aéreo fechado sobem o índice de tensão da região."""
    found: dict[str, dict[str, dict]] = {}
    internet, airspace = radar.get("internet"), radar.get("airspace")
    if _fresh(internet, now):
        for c in internet.get("countries", []):
            if c.get("status") in ("apagao", "queda"):
                found.setdefault(c["tag"], {}).setdefault("internet", {"kind": "internet", "status": c["status"], "name": c["name"]})
    if _fresh(airspace, now):
        for z in airspace.get("zones", []):
            if z.get("status") in ("fechado", "reduzido"):
                found.setdefault(z["tag"], {}).setdefault("airspace", {"kind": "airspace", "status": z["status"], "name": z["name"]})
    for tag, signals in found.items():
        region = regions.get(tag)
        if region is None:
            continue
        region["signals"] = list(signals.values())
        boost = sum(SIGNAL_BOOST.get(s["status"], 0) for s in signals.values())
        region["tension"] = min(100, region["tension"] + boost)
        region["level"] = level_for(region["tension"])


FC_MIN_SHARED = 3
FC_MIN_OVERLAP = 0.3
FC_COMMON = 0.12


def link_factchecks(radar: dict, items: list[dict]) -> None:
    """Liga cada checagem às histórias do feed que falam da mesma coisa (palavras raras em comum)."""
    checks = (radar.get("factcheck") or {}).get("items") or []
    if not checks or not items:
        return
    ctoks = {
        c["id"]: tokens(" ".join([c["title"]] + [a["title"] for a in c.get("articles", [])[:6]]))
        for c in items
    }
    df: dict[str, int] = {}
    for toks in ctoks.values():
        for t in toks:
            df[t] = df.get(t, 0) + 1
    limit = max(3, FC_COMMON * len(items))
    rare = lambda toks: {t for t in toks if df.get(t, 0) <= limit}  # noqa: E731
    for fc in checks:
        ft = rare(tokens(f"{fc['title']} {fc.get('summary', '')}"))
        matches = []
        for cid, ct in ctoks.items():
            shared = ft & rare(ct)
            if len(shared) >= FC_MIN_SHARED and len(shared) / max(1, min(len(ft), len(ct))) >= FC_MIN_OVERLAP:
                matches.append((len(shared), cid))
        fc["clusters"] = [cid for _, cid in sorted(matches, reverse=True)[:5]]
