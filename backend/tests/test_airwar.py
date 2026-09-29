"""Placar aéreo da Ucrânia."""

from datetime import datetime, timezone

from wid.airwar import parse_title, update_airwar
from wid.fetch import iso

NOW = datetime(2026, 9, 29, 12, 0, tzinfo=timezone.utc)


def test_parse_title():
    assert parse_title("Russia launches 479 drones, 20 missiles against Ukraine overnight; 460 downed") == {"drones": 479, "missiles": 20, "downed": 460}
    assert parse_title("Ukraine's air force shoots down 50 of 70 drones launched by Russia overnight") == {"drones": 70, "missiles": 0, "downed": 50}
    assert parse_title("Rússia lança 300 drones e 12 mísseis contra a Ucrânia; 280 foram abatidos") == {"drones": 300, "missiles": 12, "downed": 280}
    assert parse_title("Zelensky: Russia launched over 1,500 drones this week") is None
    assert parse_title("Ukraine to produce 4 million drones") is None


def test_update_keeps_the_biggest_report_per_day(tmp_path):
    items = [
        {"id": "a", "title": "Russia launches 100 drones overnight, 80 shot down", "lang": "en", "tags": ["ucrania_russia"],
         "published": iso(NOW), "articles": [{"title": "Russia attacks Ukraine with 120 drones overnight", "lang": "en"}]},
        {"id": "b", "title": "Israel strikes 30 drones", "lang": "en", "tags": ["israel"], "published": iso(NOW), "articles": []},
    ]
    data = update_airwar(tmp_path, items, NOW)
    assert len(data["days"]) == 1
    d = data["days"][0]
    assert d["drones"] == 120 and d["downed"] == 80 and d["cluster_id"] == "a"
