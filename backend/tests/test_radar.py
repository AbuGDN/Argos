import json
from datetime import datetime, timedelta, timezone

import httpx

from wid import radar
from wid.build import CONFIG_DIR, build
from wid.keywords import Keywords

NOW = datetime(2026, 9, 24, 10, 0, tzinfo=timezone.utc)


def kw():
    return Keywords.load(CONFIG_DIR / "keywords.yaml")


def ioda_payload(level_now: float, base: float = 100.0):
    start = int(NOW.timestamp()) - 27 * 3600
    step = 300
    n = 27 * 12
    values = [base] * (n - 12) + [level_now] * 12
    return {"data": [[{"datasource": "ping-slash24", "from": start, "step": step, "values": values},
                      {"datasource": "bgp", "from": start, "step": step, "values": [base] * n}]]}


def test_ioda_status_detects_blackout():
    out = radar.ioda_status(ioda_payload(20), NOW)
    assert out["status"] == "apagao" and out["ratio"] == 0.2
    assert len(out["spark"]) == 24
    assert radar.ioda_status(ioda_payload(70), NOW)["status"] == "queda"
    assert radar.ioda_status(ioda_payload(98), NOW)["status"] == "normal"


def test_airspace_status_needs_history():
    assert radar.airspace_status(10, None, 0) == "coletando"
    assert radar.airspace_status(5, 40, 5) == "fechado"
    assert radar.airspace_status(20, 40, 5) == "reduzido"
    assert radar.airspace_status(38, 40, 5) == "normal"
    assert radar.airspace_status(0, 3, 5) == "pouco_trafego"
    payload = {"states": [["a", "", "", 0, 0, 0, 0, 0, False], ["b", "", "", 0, 0, 0, 0, 0, True]]}
    assert radar.count_airborne(payload) == 1


def test_parse_firms():
    text = "latitude,longitude,bright_ti4,frp\n31.5,34.45,330.1,12.5\n31.51,34.46,320,3\n"
    assert radar.parse_firms(text) == [(31.5, 34.45, 12.5), (31.51, 34.46, 3.0)]
    try:
        radar.parse_firms("Invalid MAP_KEY.")
    except ValueError as exc:
        assert "Invalid" in str(exc)
    else:
        raise AssertionError("deveria falhar")


def test_strait_summary():
    last = datetime(2026, 9, 20, tzinfo=timezone.utc)
    feats = []
    for i in range(400):
        d = last - timedelta(days=i)
        n = 20 if i < 7 else 40 if i < 97 else 70
        feats.append({"attributes": {"date": int(d.timestamp() * 1000), "n_total": n}})
    s = radar.strait_summary(feats)
    assert s["date"] == "2026-09-20"
    assert s["avg7"] == 20 and s["avg90"] == 40 and s["year_ago"] == 70
    assert len(s["spark"]) == 60


def test_quote_summary_uses_previous_close():
    day = 86400
    t0 = int(datetime(2026, 9, 1, 20, tzinfo=timezone.utc).timestamp())
    closes = [70.0, 71.0, 72.0, 73.0, 74.0, 75.0, 80.0]
    payload = {"chart": {"result": [{
        "meta": {"regularMarketPrice": 80.0, "regularMarketTime": t0 + 6 * day},
        "timestamp": [t0 + i * day for i in range(7)],
        "indicators": {"quote": [{"close": closes}]},
    }]}}
    q = radar.quote_summary(payload)
    assert q["price"] == 80.0
    assert q["change_pct"] == round((80 - 75) / 75 * 100, 2)
    assert q["change_week_pct"] == round((80 - 71) / 71 * 100, 2)


def test_tfp_figures_and_idps():
    payload = {"gaza": {"killed": {"total": 1000, "children": 300}, "injured": {"total": 2000}, "last_update": "2026-09-20"},
               "west_bank": {"killed": {"total": 50}, "last_update": "2026-09-19"}}
    figs = radar.tfp_figures(payload)
    assert {"region": "gaza", "label": "Mortos", "value": 1000, "date": "2026-09-20"} in figs
    assert any(f["region"] == "cisjordania" and f["value"] == 50 for f in figs)
    rows = [
        {"admin1_code": "A", "population": 10, "reference_period_end": "2026-06-30"},
        {"admin1_code": "B", "population": 20, "reference_period_end": "2026-06-30"},
        {"admin1_code": "A", "population": 99, "reference_period_end": "2025-01-01"},
    ]
    assert radar.idps_total(rows) == (30, "2026-06-30")
    rows.append({"admin1_code": "", "population": 35, "reference_period_end": "2026-06-30"})
    assert radar.idps_total(rows) == (35, "2026-06-30")


def test_losses_summary():
    payload = {"data": {"date": "2026-09-24", "day": 1674, "stats": {"personnel_units": 1000, "tanks": 10},
                        "increase": {"personnel_units": 900}}}
    s = radar.losses_summary(payload)
    assert s["day"] == 1674
    assert s["items"][0] == {"key": "personnel_units", "label": "Militares", "total": 1000, "increase": 900}


def test_crisiswatch_trends():
    html = """<html><script>var x = 'Iran';</script><h1>CrisisWatch September 2026</h1>
    <h3>Deteriorated Situations</h3><a>Lebanon</a>, <a>Sudan</a>
    <h3>Improved Situations</h3><a>Syria</a>
    <h3>Conflict Risk Alerts</h3><a>Iran</a></html>"""
    countries = {"Lebanon": "libano", "Sudan": "sudao", "Syria": "siria", "Iran": "ira"}
    t = radar.crisiswatch_trends(html, countries)
    assert [c["tag"] for c in t["deteriorated"]] == ["libano", "sudao"]
    assert [c["tag"] for c in t["improved"]] == ["siria"]
    assert [c["tag"] for c in t["risk"]] == ["ira"]
    assert t["month"] == "setembro de 2026"


def test_prediction_events_filters_war_markets():
    events = [
        {"id": 1, "slug": "israel-hamas", "title": "Israel x Hamas ceasefire by December?", "volume": 5, "volume24hr": 3,
         "markets": [{"question": "Ceasefire by December?", "outcomes": '["Yes","No"]', "outcomePrices": '["0.31","0.69"]',
                      "oneDayPriceChange": 0.02, "volume": "100"}]},
        {"id": 2, "slug": "nba", "title": "NBA champion 2027", "volume": 50, "markets": []},
    ]
    out = radar.prediction_events(events, kw(), 10)
    assert len(out) == 1 and out[0]["markets"][0]["prob"] == 0.31
    assert out[0]["url"] == "https://polymarket.com/event/israel-hamas"
    assert {"israel", "gaza"} <= set(out[0]["tags"])


def test_apply_signals_raises_tension():
    regions = {"ira": {"tension": 40, "level": "moderada"}}
    data = {"internet": {"updated": "2026-09-24T09:30:00Z", "countries": [
        {"code": "IR", "name": "Irã", "tag": "ira", "status": "apagao"}]}}
    radar.apply_signals(regions, data, NOW)
    assert regions["ira"]["tension"] == 50 and regions["ira"]["level"] == "alta"
    assert regions["ira"]["signals"][0]["kind"] == "internet"
    # Dado velho não conta.
    regions = {"ira": {"tension": 40, "level": "moderada"}}
    data["internet"]["updated"] = "2026-09-24T01:00:00Z"
    radar.apply_signals(regions, data, NOW)
    assert "signals" not in regions["ira"]


def test_link_factchecks():
    items = [
        {"id": "c1", "title": "Video of Hezbollah drone swarm over Haifa port goes viral", "articles": []},
        {"id": "c2", "title": "Israel strikes Beirut", "articles": []},
        {"id": "c3", "title": "Gaza talks resume in Cairo", "articles": []},
    ]
    data = {"factcheck": {"items": [{"title": "Viral video of drone swarm over Haifa port is from a 2019 game", "summary": ""}]}}
    radar.link_factchecks(data, items)
    assert data["factcheck"]["items"][0]["clusters"] == ["c1"]


def mock_client(routes: dict):
    calls = []

    def handler(request: httpx.Request):
        calls.append(request.url.host)
        for host, body in routes.items():
            if host in request.url.host:
                if isinstance(body, int):
                    return httpx.Response(body)
                return httpx.Response(200, json=body)
        return httpx.Response(404)

    return httpx.Client(transport=httpx.MockTransport(handler)), calls


def test_collect_isolates_failures_and_throttles(tmp_path):
    config = {
        "internet": {"interval": 30, "countries": [{"code": "IR", "name": "Irã", "tag": "ira"}]},
        "losses": {"interval": 180},
        "markets": {"interval": 60, "items": [{"id": "brent", "name": "Brent", "symbol": "BZ=F"}]},
    }
    losses = {"data": {"date": "2026-09-24", "day": 1, "stats": {"tanks": 5}, "increase": {}}}
    client, calls = mock_client({"ioda": ioda_payload(10), "russianwarship": losses, "yahoo": 500})
    data = radar.collect(tmp_path, NOW, kw(), config, client)
    assert data["internet"]["countries"][0]["status"] == "apagao"
    assert data["losses"]["items"][0]["total"] == 5
    assert "markets" not in data and data["status"]["markets"]["ok"] is False
    assert (tmp_path / "stats" / "radar_state.json").exists()

    # Publica e roda de novo 20 min depois: nada vence, nenhuma chamada.
    (tmp_path / "radar.json").write_text(json.dumps(data))
    client2, calls2 = mock_client({})
    later = radar.collect(tmp_path, NOW + timedelta(minutes=20), kw(), config, client2)
    assert calls2 == []
    assert later["losses"] == data["losses"]

    # 40 min depois: internet vence (30 min); losses não (180); markets (falhou) só depois de 1 h.
    client3, calls3 = mock_client({"ioda": 503})
    again = radar.collect(tmp_path, NOW + timedelta(minutes=40), kw(), config, client3)
    assert set(calls3) == {"api.ioda.inetintel.cc.gatech.edu"}
    assert again["internet"] == data["internet"]  # mantém o último dado bom
    assert again["status"]["internet"]["ok"] is False


def test_fires_without_key_is_reported(tmp_path, monkeypatch):
    monkeypatch.delenv("FIRMS_MAP_KEY", raising=False)
    client, calls = mock_client({})
    data = radar.collect(tmp_path, NOW, kw(), {"fires": {"interval": 180, "zones": []}}, client)
    assert data["fires"]["missing_key"] is True and calls == []


def test_build_writes_radar(tmp_path):
    data = {"version": 1, "generated_at": "2026-09-24T10:00:00Z", "status": {}}
    build(tmp_path, NOW, [], kw(), [], {}, data)
    assert json.loads((tmp_path / "radar.json").read_text())["version"] == 1


def test_stooq_and_price_only_fallback(tmp_path):
    text = "Date,Open,High,Low,Close,Volume\n" + "\n".join(f"2026-09-{d:02d},1,1,1,{60 + d},0" for d in range(10, 22))
    s = radar.series_summary(radar.parse_stooq(text))
    assert s["price"] == 81 and s["change_pct"] == round(1 / 80 * 100, 2)
    try:
        radar.parse_stooq("Exceeded the daily hits limit")
    except ValueError:
        pass
    else:
        raise AssertionError("deveria falhar")
    # Stooq e Yahoo fora: usa o câmbio aberto e guarda o histórico para a variação.
    config = {"markets": {"interval": 60, "items": [{"id": "shekel", "name": "Shekel", "stooq": "usdils", "symbol": "ILS=X", "fx": "ILS"}]}}
    client, _ = mock_client({"stooq": 403, "yahoo": 429, "er-api": {"rates": {"ILS": 3.7}}})
    first = radar.collect(tmp_path, NOW, kw(), config, client)
    assert first["markets"]["items"][0]["price"] == 3.7
    (tmp_path / "radar.json").write_text(json.dumps(first))
    client, _ = mock_client({"stooq": 403, "yahoo": 429, "er-api": {"rates": {"ILS": 3.8}}})
    second = radar.collect(tmp_path, NOW + timedelta(days=1), kw(), config, client)
    q = second["markets"]["items"][0]
    assert q["price"] == 3.8 and q["change_pct"] == round(0.1 / 3.7 * 100, 2)


def test_keep_filters_by_section():
    k = kw()
    src = {}
    assert not radar._keep("official", src, k.match("Iranian FM meets UN General Assembly President", ""), "")
    assert radar._keep("official", src, k.match("CENTCOM forces strike Houthi missile sites in Yemen", ""), "")
    assert not radar._keep("factcheck", src, k.match("14 rumors about US-China relations", ""), "")
    assert radar._keep("sanctions", {"match": "sanction"}, k.match("Treasury sanctions oil network", ""), "Treasury sanctions oil network")


def test_fires_collects_as_soon_as_key_is_added(tmp_path, monkeypatch):
    monkeypatch.delenv("FIRMS_MAP_KEY", raising=False)
    config = {"fires": {"interval": 180, "zones": [{"id": "gaza", "name": "Gaza", "tag": "gaza", "box": [34.2, 31.2, 34.6, 31.6]}]}}
    client, _ = mock_client({})
    first = radar.collect(tmp_path, NOW, kw(), config, client)
    (tmp_path / "radar.json").write_text(json.dumps(first))
    monkeypatch.setenv("FIRMS_MAP_KEY", "abc")

    def handler(request):
        return httpx.Response(200, text="latitude,longitude,frp\n31.5,34.45,10\n")

    client = httpx.Client(transport=httpx.MockTransport(handler))
    later = radar.collect(tmp_path, NOW + timedelta(minutes=30), kw(), config, client)
    assert later["fires"]["zones"][0]["count"] == 1


def test_parse_fred_skips_missing():
    rows = radar.parse_fred("observation_date,DCOILBRENTEU\n2026-09-18,70.1\n2026-09-19,.\n2026-09-22,71.5\n")
    assert rows == [("2026-09-18", 70.1), ("2026-09-22", 71.5)]


def test_crisiswatch_month_pages():
    pages = radar._month_pages(datetime(2026, 9, 27, tzinfo=timezone.utc))
    assert pages == ["https://www.crisisgroup.org/crisiswatch/september-2026",
                     "https://www.crisisgroup.org/crisiswatch/august-2026"]


def test_parse_eia_weekly_rows():
    html = """<tr>
 <td class='B6'>&nbsp;&nbsp;2026 Sep-14 to Sep-18</td>
 <td class='B3'>110.00</td>
 <td class='B3'>111.50</td>
 <td class='B3'></td>
 <td class='B3'>112.00</td>
 <td class='B3'>113.25</td>
 </tr>
 <tr>
 <td class='B6'>&nbsp;&nbsp;2026 Sep-21 to Sep-25</td>
 <td class='B3'>116.15</td>
 <td class='B3'>114.89</td>
 <td class='B3'></td>
 <td class='B3'></td>
 <td class='B3'></td>
 </tr>"""
    rows = radar.parse_eia(html)
    assert rows[0] == ("2026-09-14", 110.0)
    assert ("2026-09-17", 112.0) in rows
    assert rows[-1] == ("2026-09-22", 114.89)
    s = radar.series_summary(rows)
    assert s["price"] == 114.89 and s["change_pct"] == round((114.89 - 116.15) / 116.15 * 100, 2)


def test_crisiswatch_entries_from_country_blocks():
    def entry(month, slug, *states):
        spans = " ".join(f'<span class="state-{s}"></span>' for s in states)
        return f'<div title="{month}" class="o-state-entry u-df u-pr"> <a href="/crisiswatch/x#{slug}"> {spans} <aside>'
    html = "".join([
        entry("July 2026", "lebanon", "deteriorated"),
        entry("August 2026", "lebanon", "unchanged"),
        entry("August 2026", "yemen", "unchanged", "risk-alert"),
        entry("August 2026", "israel-palestine", "deteriorated"),
        entry("August 2026", "sudan", "improved"),
        entry("August 2026", "benin", "deteriorated"),
    ])
    countries = {"Lebanon": "libano", "Yemen": "iemen", "Israel": "israel", "Palestine": "gaza", "Sudan": "sudao"}
    t = radar.crisiswatch_entries(html, countries)
    assert t["month"] == "agosto de 2026"
    assert {c["tag"] for c in t["deteriorated"]} == {"israel", "gaza"}
    assert [c["tag"] for c in t["risk"]] == ["iemen"]
    assert [c["tag"] for c in t["improved"]] == ["sudao"]


def test_military_in_zones_and_category():
    payload = {"ac": [
        {"hex": "a1", "flight": "QID71 ", "t": "K35R", "lat": 30.0, "lon": 40.0, "alt_baro": 30000, "track": 90},
        {"hex": "a2", "t": "RQ4", "lat": 44.0, "lon": 33.0, "alt_baro": 55000},
        {"hex": "a3", "t": "C17", "lat": 38.0, "lon": -77.0},
        {"hex": "a4", "t": "E3TF"},
    ]}
    zones = [{"id": "me", "box": [12, 25, 42, 63]}, {"id": "bs", "box": [40, 22, 56.5, 45]}]
    out = radar.military_in_zones(payload, zones)
    assert [a["hex"] for a in out["me"]] == ["a1"] and out["me"][0]["category"] == "reabastecedor"
    assert out["me"][0]["callsign"] == "QID71"
    assert out["bs"][0]["category"] == "espionagem"


def test_carriers_from_tracker():
    html = """<p>Aircraft carrier USS <em>Gerald R. Ford</em> (CVN-78) is operating in the Eastern Mediterranean.</p>
    <p>Aircraft carrier USS <em>George H.W. Bush</em> (CVN-77) is underway in the Atlantic Ocean.</p>
    <p>USS <em>Carl Vinson</em> (CVN-70) is in port in San Diego.</p>"""
    ships = radar.carriers_from_tracker(html)
    by = {s["hull"]: s for s in ships}
    assert by["CVN-78"]["place"] == "Mediterrâneo Oriental" and by["CVN-78"]["status"] == "no mar"
    assert by["CVN-77"]["place"] == "Atlântico"
    assert by["CVN-70"]["status"] == "no porto"


def test_frontline_summary_area_and_filter():
    square = [[37.0, 48.0, 0], [38.0, 48.0, 0], [38.0, 49.0, 0], [37.0, 49.0, 0], [37.0, 48.0, 0]]
    data = {"map": {"features": [
        {"type": "Feature", "geometry": {"type": "Polygon", "coordinates": [square]},
         "properties": {"name": "Окуповано /// Occupied /// geoJSON.status.occupied"}},
        {"type": "Feature", "geometry": {"type": "Polygon", "coordinates": [square]},
         "properties": {"name": "Karelia joke /// geoJSON.territories.karelia"}},
        {"type": "Feature", "geometry": {"type": "Point", "coordinates": [37, 48, 0]}, "properties": {"name": "x"}},
    ]}}
    s = radar.frontline_summary(data)
    # 1° x 1° a 48,5°N ≈ 73,8 km × 110,6 km ≈ 8.150 km²
    assert 7900 < s["occupied_km2"] < 8400
    assert len(s["occupied"]) == 1 and s["occupied"][0][0][0] == [48.0, 37.0]
    assert s["grey"] == []


def test_simplify_keeps_corners():
    line = [[0, 0], [1, 0.001], [2, 0], [2, 2]]
    assert radar.simplify(line, 0.01) == [[0, 0], [2, 0], [2, 2]]


def test_build_splits_frontline_polygons(tmp_path):
    data = {"version": 1, "status": {}, "frontline": {"occupied_km2": 10, "occupied": [[[[1, 2]]]], "grey": [], "updated": "x"}}
    build(tmp_path, NOW, [], kw(), [], {}, data)
    radar_json = json.loads((tmp_path / "radar.json").read_text())
    assert "occupied" not in radar_json["frontline"] and radar_json["frontline"]["occupied_km2"] == 10
    assert json.loads((tmp_path / "frontline.json").read_text())["occupied"] == [[[[1, 2]]]]
