"""Sirenes, sismógrafo, alertas de viagem e ultimatos."""

import json
from datetime import datetime, timedelta, timezone

from wid.deadlines import find_deadline, update_deadlines
from wid.fetch import iso
from wid.radar import quake_items, siren_cities, siren_summary, travel_levels, travel_summary

NOW = datetime(2026, 9, 29, 12, 0, tzinfo=timezone.utc)


def test_sirens_last_24h_with_english_names_and_no_drills():
    cities = siren_cities({"cities": {"נחל עוז": {"en": "Nahal Oz", "lat": 31.47, "lng": 34.49}, "x": {"en": "X"}}})
    assert cities == {"נחל עוז": ["Nahal Oz", 31.47, 34.49]}
    t = int((NOW - timedelta(hours=1)).timestamp())
    old = int((NOW - timedelta(days=2)).timestamp())
    history = [
        {"id": 2, "alerts": [{"time": t, "cities": ["נחל עוז", "מקום"], "threat": 0, "isDrill": False}]},
        {"id": 1, "alerts": [{"time": t, "cities": ["נחל עוז"], "threat": 5, "isDrill": True},
                             {"time": old, "cities": ["נחל עוז"], "threat": 5, "isDrill": False}]},
    ]
    s = siren_summary(history, cities, NOW)
    assert s["count_24h"] == 2
    assert s["events"][0]["threat"] == "foguetes e mísseis"
    assert s["events"][0]["cities"][0] == {"name": "Nahal Oz", "lat": 31.47, "lon": 34.49}
    assert s["events"][0]["cities"][1]["lat"] is None  # cidade fora do dicionário fica com o nome original
    assert sum(d["count"] for d in s["days"]) == 3


def _quake(lat, lon, depth, kind="earthquake"):
    return {"id": f"q{lat}", "properties": {"mag": 4.1, "place": "x", "time": NOW.timestamp() * 1000, "type": kind},
            "geometry": {"coordinates": [lon, lat, depth]}}


def test_quakes_in_zones_and_nuclear_site_alerts():
    zones = [{"name": "Irã", "tag": "ira", "box": [25.0, 44.0, 39.8, 63.3]}]
    sites = [{"name": "Fordow", "lat": 34.88, "lon": 50.99}]
    payload = {"features": [_quake(34.9, 51.0, 2), _quake(30.0, 55.0, 30), _quake(10.0, 10.0, 1), _quake(30.0, 50.0, 10, "explosion")]}
    items = quake_items(payload, zones, sites)
    assert len(items) == 3  # o de fora das zonas fica de fora
    near = next(q for q in items if q["lat"] == 34.9)
    assert near["alert"] and near["shallow"] and near["site"]["name"] == "Fordow"
    assert next(q for q in items if q["type"] == "explosion")["alert"]
    deep = next(q for q in items if q["depth"] == 30)
    assert not deep["alert"] and deep["site"] is None


def test_travel_levels_and_changes():
    xml = b"""<?xml version="1.0"?><rss><channel>
      <item><title>Israel, the West Bank and Gaza - Level 3: Reconsider Travel</title></item>
      <item><title>Lebanon - Level 4: Do Not Travel</title></item>
      <item><title>Brazil - Level 2: Exercise Increased Caution</title></item>
    </channel></rss>"""
    levels = travel_levels(xml)
    assert levels["Lebanon"] == 4 and levels["Israel, the West Bank and Gaza"] == 3
    watch = [{"country": "Lebanon", "name": "Líbano", "tag": "libano"}, {"country": "Brazil", "name": "Brasil"}]
    state = {}
    first = travel_summary(levels, state, watch, NOW)
    assert first["changes"] == [] and first["items"][0]["name"] == "Líbano"
    levels2 = dict(levels, Brazil=3)
    second = travel_summary(levels2, state, watch, NOW + timedelta(hours=6))
    assert second["changes"][0] == {"country": "Brazil", "from": 2, "to": 3, "date": iso(NOW + timedelta(hours=6)),
                                    "name": "Brasil", "tag": None}


def test_find_deadline():
    assert find_deadline("Israel dá 48 horas para Hamas aceitar acordo")[1] == timedelta(hours=48)
    assert find_deadline("Trump gives Iran two weeks to decide")[1] == timedelta(days=14)
    assert find_deadline("Hezbollah given 72-hour deadline to withdraw")[1] == timedelta(hours=72)
    assert find_deadline("At least 30 killed in 24 hours in Gaza") is None
    assert find_deadline("Ataques em 24 horas deixam 50 mortos") is None


def _cluster(cid, title, published, tags=("ira",), score=1.0):
    return {"id": cid, "title": title, "lang": "pt", "published": iso(published), "tags": list(tags), "score": score,
            "articles": []}


def test_update_deadlines_tracks_and_links_what_happened(tmp_path):
    start = NOW - timedelta(days=3)
    items = [_cluster("a", "Trump dá 48 horas para o Irã responder", start),
             _cluster("b", "Mesmo ultimato: EUA dão 48 horas para o Irã", start + timedelta(hours=2)),
             _cluster("c", "Irã rejeita proposta e EUA atacam", start + timedelta(hours=50), score=5.0),
             _cluster("d", "Outra região", start + timedelta(hours=50), tags=("sudao",), score=9.0)]
    out = update_deadlines(tmp_path, items, NOW)
    assert len(out) == 1
    d = out[0]
    assert d["span"] == "48 horas" and d["due"] == iso(start + timedelta(hours=48))
    assert d["after"]["cluster_id"] == "c"
    saved = json.loads((tmp_path / "deadlines.json").read_text(encoding="utf-8"))
    assert saved["deadlines"][0]["id"] == d["id"]
