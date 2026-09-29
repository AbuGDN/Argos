"""Conselho de Segurança da ONU e tempo nas zonas de conflito."""

import time
from pathlib import Path

from wid.keywords import Keywords
from wid.radar import unsc_item, weather_flags

KW = Keywords.load(Path(__file__).resolve().parent.parent / "config" / "keywords.yaml")
T = time.gmtime(1790700000)


def test_unsc_veto_with_country():
    e = {"title": "Security Council Fails to Adopt Resolution on Gaza Ceasefire",
         "summary": "The draft was not adopted owing to the negative vote of the United States, a permanent member.",
         "link": "https://press.un.org/x", "published_parsed": T}
    item = unsc_item(e, "press.un.org", KW)
    assert item["kind"] == "veto" and item["veto_by"] == ["EUA"] and "gaza" in item["tags"]


def test_unsc_kinds_and_filter():
    adopt = {"title": "Security Council Unanimously Adopts Resolution 2800 (2026), Extending Mandate in Lebanon", "link": "u1", "published_parsed": T}
    assert unsc_item(adopt, "press.un.org", KW)["kind"] == "aprovada"
    meeting = {"title": "Security Council, 10231st Meeting (AM) Haiti", "link": "u2", "published_parsed": T}
    assert unsc_item(meeting, "press.un.org", KW)["kind"] == "reuniao"
    other = {"title": "Secretary-General Hails World Tourism Day", "link": "u3", "published_parsed": T}
    assert unsc_item(other, "press.un.org", KW) is None
    blue = {"title": "Iran: Vote on a Draft Resolution*", "link": "u4", "published_parsed": T}
    assert unsc_item(blue, "Security Council Report", KW)["kind"] == "votacao"


def test_weather_flags():
    assert weather_flags({"wind_gusts_10m": 70, "weather_code": 95, "visibility": 1500}, 900) == [
        "vento forte", "trovoada", "baixa visibilidade", "tempestade de areia"]
    assert weather_flags({"wind_gusts_10m": 10, "weather_code": 0, "visibility": 30000}, 5) == []
