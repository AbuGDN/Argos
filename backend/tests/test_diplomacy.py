"""Termômetro diplomático."""

import json
from datetime import datetime, timedelta, timezone

from wid.diplomacy import classify, update_diplomacy
from wid.fetch import iso

NOW = datetime(2026, 9, 29, 12, 0, tzinfo=timezone.utc)


def test_classify_hostile_and_cooperative():
    assert classify("Seoul summons Ukraine envoy over prisoner row") == ["embaixador"]
    assert classify("Turquia rompe relações com Israel") == ["ruptura"]
    assert classify("UN Security Council holds emergency meeting on Gaza") == ["emergencia"]
    assert "negociacao" in classify("Iran and US resume talks in Oman")
    assert classify("Egito fecha passagem de Rafah") == ["fronteira"]
    assert classify("Weather forecast for Tel Aviv") == []


def _c(cid, title, hours, tags=("ira",)):
    return {"id": cid, "title": title, "lang": "en", "published": iso(NOW - timedelta(hours=hours)), "tags": list(tags), "articles": []}


def test_update_diplomacy_keeps_seven_days_and_scores(tmp_path):
    items = [_c("a", "Iran expels German ambassador", 2), _c("b", "EU imposes new sanctions on Iran", 5),
             _c("c", "Iran threatens to close Hormuz", 8)]
    data = update_diplomacy(tmp_path, items, NOW)
    r = data["regions"]["ira"]
    assert r["label"] == "hostil" and r["index"] == 100 and r["total"] == 3
    # Rodada seguinte: evento antigo continua; um de 8 dias atrás sai.
    later = [_c("d", "Iran and EU hold talks in Geneva", 1)]
    data2 = update_diplomacy(tmp_path, later, NOW + timedelta(days=1))
    assert data2["regions"]["ira"]["total"] == 4
    saved = json.loads((tmp_path / "diplomacy.json").read_text(encoding="utf-8"))
    assert {e["cluster_id"] for e in saved["events"]} == {"a", "b", "c", "d"}
    data3 = update_diplomacy(tmp_path, [], NOW + timedelta(days=8))
    assert "ira" not in data3["regions"]
