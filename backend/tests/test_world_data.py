import gzip
import io
from datetime import datetime, timezone
from pathlib import Path

import httpx

from wid import radar

NOW = datetime(2026, 9, 30, 12, 0, tzinfo=timezone.utc)


def test_refugee_years_and_hosts():
    items = [
        {"year": 2025, "refugees": 5224131, "asylum_seekers": 26583, "idps": 3712000, "coa_iso": "-"},
        {"year": 2024, "refugees": 5120036, "asylum_seekers": 44349, "idps": "0", "coa_iso": "-"},
    ]
    years = radar.refugee_years(items)
    assert [y["year"] for y in years] == [2024, 2025]
    assert years[1]["idps"] == 3712000 and years[0]["idps"] == 0
    hosts = radar.refugee_hosts([
        {"year": 2025, "coa_iso": "DEU", "coa_name": "Germany", "refugees": 1200000, "asylum_seekers": "0"},
        {"year": 2025, "coa_iso": "POL", "coa_name": "Poland", "refugees": 990000, "asylum_seekers": 100},
        {"year": 2024, "coa_iso": "CZE", "coa_name": "Czechia", "refugees": 9000000, "asylum_seekers": 0},
        {"year": 2025, "coa_iso": "-", "coa_name": "-", "refugees": 5, "asylum_seekers": 0},
    ])
    assert [h["iso"] for h in hosts] == ["DEU", "POL"]  # só o ano mais novo, sem a linha "-"
    assert hosts[1]["people"] == 990100


def _ipc(level, period, phase, pop, admin="X"):
    return {"admin_level": level, "admin1_code": admin, "ipc_type": "current", "ipc_phase": phase, "population_in_phase": pop,
            "reference_period_start": f"{period}T00:00:00", "reference_period_end": f"{period}T23:59:59"}


def test_ipc_summary_uses_one_admin_level_and_latest_period():
    rows = [
        _ipc(1, "2026-02-01", "3", 100, "A"), _ipc(1, "2026-02-01", "4", 50, "A"), _ipc(1, "2026-02-01", "5", 10, "A"),
        _ipc(1, "2026-02-01", "1", 840, "A"),
        _ipc(2, "2026-02-01", "3", 999999, "A1"),  # nível mais fino não soma junto
        _ipc(1, "2025-06-01", "3", 80, "A"), _ipc(1, "2025-06-01", "1", 920, "A"),
    ]
    s = radar.ipc_summary(rows)
    assert s["start"] == "2026-02-01"
    assert s["phase3plus"] == 160 and s["phase5"] == 10
    assert s["fraction"] == 0.16
    assert s["prev"] == {"start": "2025-06-01", "phase3plus": 80}


def test_price_changes_median_and_preference():
    rows = []
    for month, price in (("2025-08", 100), ("2026-08", 150)):
        for p in (price, price + 2, price - 2):
            rows.append({"commodity_name": "Wheat flour", "price": p, "price_type": "Retail", "unit": "KG",
                         "currency_code": "SDG", "reference_period_start": f"{month}-15"})
    rows.append({"commodity_name": "Soap", "price": 5, "price_type": "Retail", "reference_period_start": "2025-08-01"})
    rows.append({"commodity_name": "Soap", "price": 6, "price_type": "Retail", "reference_period_start": "2026-08-01"})
    rows.append({"commodity_name": "Wheat flour", "price": 1, "price_type": "Wholesale", "reference_period_start": "2026-08-01"})
    out = radar.price_changes(rows)
    assert out[0]["name"] == "Wheat flour" and out[0]["change"] == 50.0 and out[0]["unit"] == "SDG/KG"
    assert out[1]["name"] == "Soap"


def test_gas_storage_and_flows():
    st = radar.gas_storage([
        {"gasDayStart": "2026-09-28", "full": "71.33", "gasInStorage": "807.17", "trend": "0.17"},
        {"gasDayStart": "2026-09-27", "full": "71.17", "gasInStorage": "805.28", "trend": "0.29"},
    ])
    assert st["date"] == "2026-09-28" and st["full"] == 71.33 and st["series"][0] == ["2026-09-27", 71.17]
    fl = radar.gas_flows([
        {"periodFrom": "2025-09-01T06:00:00+02:00", "value": 538534916},
        {"periodFrom": "2025-09-02T06:00:00+02:00", "value": 541602955},
    ])
    assert fl["date"] == "2025-09-02" and fl["gwh"] == 541.6 and fl["avg30"] == 540.1


def test_press_summary_counts_and_tags():
    rows = [
        {"fullName": "A", "organizations": "TV", "country": "Israel and the Occupied Palestinian Territory",
         "startDisplay": "January 8, 2026", "type": "Journalist", "location": "Gaza"},
        {"fullName": "B", "organizations": "R", "country": "Ukraine", "startDisplay": "March 3, 2025", "type": "Journalist"},
        {"fullName": "A", "organizations": "TV", "country": "Israel and the Occupied Palestinian Territory",
         "startDisplay": "January 8, 2026"},  # repetido
    ]
    s = radar.press_summary(rows, 2026)
    assert s["killed_this_year"] == 1 and s["killed_last_year"] == 1
    assert s["recent"][0]["name"] == "A" and s["recent"][0]["tag"] == "gaza" and s["recent"][0]["date"] == "2026-01-08"
    assert {c["country"] for c in s["countries"]} == {"Israel and the Occupied Palestinian Territory", "Ukraine"}


# Formato real do targets.simple.csv (cabeçalho e títulos copiados da fonte em 30/09/2026). Um exemplo
# antigo usava códigos (us_ofac_sdn) que a fonte não tem: o teste passava e a coluna "quem" saía vazia.
CSV = (
    "id,schema,name,aliases,birth_date,countries,addresses,identifiers,sanctions,phones,emails,program_ids,"
    "dataset,first_seen,last_seen,last_change\n"
    'x1,Person,Ivan Petrov,"Иван Петров;I. Petrov",,ru,,,"x",,,US-RUS,"US OFAC Specially Designated Nationals (SDN) List;'
    'US Trade Consolidated Screening List (CSL);EU Financial Sanctions Files (FSF);UK FCDO Sanctions List",'
    "2022-03-01T00:00:00,,\n"
    "x2,Vessel,SEA\tSTAR,,,ir,,,,,,,UN Security Council 1718 Designated Vessels List,2023-01-02T00:00:00,,\n"
    "x3,Organization,Hamas,,,ps,,,,,,,Türkiye Asset Freezing Sanctions List (MASAK),2023-01-02T00:00:00,,\n"
    ",Company,,,,,,,,,,,US OFAC Specially Designated Nationals (SDN) List,,,\n"
)


def test_sanctions_rows():
    rows, by_auth = radar.sanctions_rows(io.StringIO(CSV))
    assert len(rows) == 3
    ivan = next(r for r in rows if r.startswith("Ivan")).split("\t")
    assert ivan[1] == "pessoa" and ivan[2] == "ru" and ivan[3] == "EUA, União Europeia, Reino Unido"  # 2 listas dos EUA = 1
    assert ivan[4] == "2022-03-01" and "Иван Петров" in ivan[5]
    ship = next(r for r in rows if r.startswith("SEA")).split("\t")
    assert ship[0] == "SEA STAR" and ship[1] == "navio" and ship[3] == "ONU"  # tab interno vira espaço
    assert next(r for r in rows if r.startswith("Hamas")).split("\t")[3] == "Turquia"
    assert by_auth["EUA"] == 1 and by_auth["ONU"] == 1


def test_collect_sanctionlist_writes_gzip(tmp_path: Path):
    big = CSV + "".join(f"y{i},Company,Empresa {i},,,,,,,,,,EU Financial Sanctions Files (FSF),2024-01-01,,\n"
                        for i in range(1200))

    def handler(req: httpx.Request):
        if req.url.path.endswith("index.json"):
            return httpx.Response(200, json={"updated_at": "2026-09-30T13:47:01", "resources": [
                {"name": "targets.simple.csv", "url": "https://data.opensanctions.org/a/targets.simple.csv"}]})
        return httpx.Response(200, text=big)

    client = httpx.Client(transport=httpx.MockTransport(handler))
    ctx = radar.Ctx(client, NOW, None, {}, None, {}, tmp_path)
    out = radar.collect_sanctionlist(ctx)
    assert out["count"] == 1203
    assert out["by_authority"][0] == {"name": "União Europeia", "count": 1201}  # vazio no ar até 30/09/2026
    lines = gzip.decompress((tmp_path / radar.SANCTIONS_FILE).read_bytes()).decode().splitlines()
    assert len(lines) == 1203 and lines == sorted(lines)


def _client(routes):
    def handler(req: httpx.Request):
        for key, body in routes:
            if key in str(req.url):
                return httpx.Response(200, json=body)
        return httpx.Response(404)
    return httpx.Client(transport=httpx.MockTransport(handler))


def _ctx(client, conf, prev=None):
    return radar.Ctx(client, NOW, None, conf, prev, {}, None)


def test_collectors_end_to_end():
    unhcr_tot = {"items": [{"year": 2025, "refugees": 100, "asylum_seekers": 5, "idps": 50, "coa_iso": "-"}]}
    unhcr_hosts = {"items": [{"year": 2025, "coa_iso": "DEU", "coa_name": "Germany", "refugees": 80, "asylum_seekers": 0}]}

    def handler(req: httpx.Request):
        url = str(req.url)
        if "unhcr" in url:
            return httpx.Response(200, json=unhcr_hosts if "coa_all" in url else unhcr_tot)
        return httpx.Response(404)
    out = radar.collect_refugees(_ctx(httpx.Client(transport=httpx.MockTransport(handler)), {"countries": [{"iso": "UKR", "name": "Ucrânia"}]}))
    assert out["countries"][0]["hosts"][0]["iso"] == "DEU"

    hunger = _client([
        ("food-security?", {"data": [_ipc(0, "2026-02-01", "3", 10), _ipc(0, "2026-02-01", "1", 90)]}),
        ("food-prices", {"data": []}),
    ])
    out = radar.collect_hunger(_ctx(hunger, {"countries": [{"iso": "SDN", "name": "Sudão"}]}))
    assert out["countries"][0]["ipc"]["phase3plus"] == 10

    gas = _client([
        ("agsi", {"data": [{"gasDayStart": "2026-09-28", "full": "71.3", "gasInStorage": "800", "trend": "0.2"}]}),
        ("entsog", {"operationaldatas": [{"periodFrom": "2026-09-28T06:00:00+02:00", "value": 5e8}]}),
    ])
    out = radar.collect_gas(_ctx(gas, {}))
    assert out["storage"]["full"] == 71.3 and out["storage"]["last_year"] == 71.3 and out["russia"]["gwh"] == 500.0

    press = _client([("cpj", {"pageCount": 1, "data": [{"fullName": "X", "country": "Sudan", "startDisplay": "May 1, 2026"}]})])
    out = radar.collect_press(_ctx(press, {}))
    assert out["killed_this_year"] == 1 and out["recent"][0]["tag"] == "sudao"
