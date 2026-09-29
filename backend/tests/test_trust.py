"""Árvore de fontes, nível de confiança, propagação, partes da tensão e incidentes do Radar."""

from datetime import datetime, timedelta, timezone

from wid.analysis import region_tension
from wid.fetch import iso
from wid.radar import correlate
from wid.trust import annotate, article_agency, source_tree

NOW = datetime(2026, 9, 29, 12, 0, tzinfo=timezone.utc)


def art(source, title, summary="", origin="internacional", minutes=0, lang="en"):
    return {"source": source, "title": title, "summary": summary, "origin": origin, "lang": lang,
            "published": iso(NOW + timedelta(minutes=minutes))}


def test_agency_detection_is_case_sensitive():
    assert article_agency(art("G1", "Israel ataca Beirute", "Segundo a agência Reuters, ...")) == "Reuters"
    assert article_agency(art("X", "Strike hits port (AP)")) == "AP"
    assert article_agency(art("X", "Pressure on the ap region")) is None


def test_source_tree_collapses_outlets_that_repeat_one_agency():
    c = {"articles": [art("G1", "a", "(Reuters)"), art("Folha", "b", "Reuters informou"),
                      art("CNN", "c", "Reuters reported"), art("BBC", "d", "our correspondent")]}
    tree = source_tree(c)
    assert tree == {"agencies": {"Reuters": ["CNN", "Folha", "G1"]}, "independent": 2, "total": 4}


def test_confidence_levels():
    many = {"articles": [art(s, "t", origin=o) for s, o in [("A", "israel"), ("B", "arabe"), ("C", "eua")]],
            "sides": "opostos"}
    annotate(many)
    assert many["confidence"]["level"] == "alta"
    assert "imprensa dos dois lados noticiou" in many["confidence"]["reasons"]

    one = {"articles": [art("A", "t")]}
    annotate(one)
    assert one["confidence"]["level"] == "baixa" and one["confidence"]["label"] == "Uma fonte só"

    wire = {"articles": [art(s, "t", "(Reuters)") for s in ("A", "B", "C")]}
    annotate(wire)
    assert wire["confidence"]["label"] == "Todos repetem a mesma fonte"

    conflict = {"articles": [art("A", "t"), art("B", "t")],
                "figures": {"killed": {"by_source": {"A": 10, "B": 20}, "divergent": True}}}
    annotate(conflict)
    assert conflict["confidence"]["level"] == "conflito"

    official = {"articles": [art(s, "Strike in Gaza, the IDF said", origin=o) for s, o in [("A", "israel"), ("B", "eua"), ("C", "internacional")]]}
    annotate(official)
    assert official["confidence"]["level"] == "media"


def test_spread_orders_first_appearance_by_origin():
    c = {"articles": [art("Ynet", "t", origin="israel", minutes=0, lang="he"), art("G1", "t", origin="brasil", minutes=90, lang="pt"),
                      art("Al Jazeera", "t", origin="arabe", minutes=20), art("Maariv", "t", origin="israel", minutes=5)]}
    annotate(c)
    assert [s["origin"] for s in c["spread"]] == ["israel", "arabe", "brasil"]
    assert [s["after_min"] for s in c["spread"]] == [0, 20, 90]
    single = {"articles": [art("Ynet", "t", origin="israel")]}
    annotate(single)
    assert "spread" not in single


def test_tension_parts_add_up():
    items = [{"id": str(i), "title": "Missile strike kills 5", "summary": "", "tags": ["ira"], "published": iso(NOW - timedelta(hours=1)),
              "sources_count": 4, "urgent": i == 0} for i in range(4)]
    r = region_tension(items, [], "2026-09-29", NOW)["ira"]
    assert abs(sum(r["parts"].values()) - r["tension"]) <= 1
    assert len(r["why"]) == 4


def test_correlate_needs_two_kinds_in_the_same_region():
    radar = {
        "sirens": {"updated": iso(NOW), "last": iso(NOW - timedelta(minutes=30)), "count_24h": 12},
        "military": {"updated": iso(NOW), "zones": [{"name": "Oriente Médio", "tag": "ira", "unusual": True}]},
    }
    regions = {"israel": {"signals": [{"kind": "airspace", "status": "fechado", "name": "Israel"}]}, "ira": {}}
    incidents = correlate(regions, radar, NOW)
    assert len(incidents) == 1 and incidents[0]["tag"] == "israel"
    assert {s["kind"] for s in incidents[0]["signals"]} == {"sirens", "airspace"}
