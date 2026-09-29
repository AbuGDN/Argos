import json
from datetime import datetime, timezone
from pathlib import Path

from wid.build import CONFIG_DIR, build
from wid.fetch import canonical_url, parse_feed
from wid.keywords import Keywords

FIXTURES = Path(__file__).parent / "fixtures"
NOW = datetime(2026, 9, 24, 10, 0, tzinfo=timezone.utc)
SOURCES = [
    {"name": "IL News", "url": "x", "lang": "en", "weight": 1.2, "origin": "israel", "file": "israel_en.xml"},
    {"name": "Mundo BR", "url": "x", "lang": "pt", "weight": 1.0, "origin": "brasil", "file": "mundo_pt.xml"},
    {"name": "Intl", "url": "x", "lang": "en", "weight": 1.0, "file": "intl_en.xml"},
]


def kw():
    return Keywords.load(CONFIG_DIR / "keywords.yaml")


def fetched():
    out = []
    for s in SOURCES:
        out += parse_feed((FIXTURES / s["file"]).read_bytes(), s, NOW)
    return out


def test_parse_feed_extracts_fields():
    arts = parse_feed((FIXTURES / "israel_en.xml").read_bytes(), SOURCES[0], NOW)
    first = arts[0]
    assert first.title == "Israel strikes Hezbollah targets in Beirut"
    assert first.url == "https://example-il.com/beirut-strike"
    assert first.summary.startswith("The IDF said")
    assert first.image == "https://example-il.com/img/beirut.jpg"
    pt = parse_feed((FIXTURES / "mundo_pt.xml").read_bytes(), SOURCES[1], NOW)
    assert pt[0].image == "https://example-br.com/f.jpg"


def test_canonical_url_keeps_real_params():
    assert canonical_url("https://a.com/x?utm_source=r&id=5&fbclid=z#frag") == "https://a.com/x?id=5"


def test_relevance_filter():
    k = kw()
    assert k.match("Israel strikes Hezbollah targets in Beirut", "").relevant
    assert not k.match("Tel Aviv stock exchange closes higher", "Markets rallied").relevant
    # "ataque" no futebol não tem região nem outro termo de guerra
    assert not k.match("Seleção vence amistoso em São Paulo", "Ataque brilhou no segundo tempo.").relevant
    m = k.match("Mísseis do Irã atingem Tel Aviv", "")
    assert m.relevant and {"ira", "israel"} <= m.tags


def test_build_clusters_across_languages_and_picks_portuguese_title(tmp_path):
    feed = build(tmp_path, NOW, SOURCES, kw(), fetched(), {})
    titles = [c["title"] for c in feed["clusters"]]
    assert "Tel Aviv stock exchange closes higher" not in titles
    assert "Seleção vence amistoso em São Paulo" not in titles
    assert len(feed["clusters"]) == 2

    top = feed["top_of_day"]
    assert top["sources_count"] == 3
    assert top["title"] == "Israel ataca alvos do Hezbollah em Beirute"
    assert top["lang"] == "pt"
    assert {"israel", "libano"} <= set(top["tags"])

    written = json.loads((tmp_path / "top.json").read_text())
    assert written["top"]["id"] == top["id"]
    assert written["secondary"][0]["title"].startswith("Ukraine says")
    assert (tmp_path / "history" / "2026-09-24.json").exists()
    assert json.loads((tmp_path / "history" / "index.json").read_text())["days"] == ["2026-09-24"]


def test_previous_articles_survive_next_run(tmp_path):
    build(tmp_path, NOW, SOURCES, kw(), fetched(), {})
    # Próxima rodada: feeds não trazem mais nada, mas as últimas 48h continuam.
    feed = build(tmp_path, NOW, SOURCES, kw(), [], {})
    assert feed["top_of_day"]["sources_count"] == 3
    assert len(feed["clusters"]) == 2


def test_urgent_flag(tmp_path):
    k = kw()
    arts = []
    for i in range(5):
        src = {"name": f"S{i}", "lang": "en", "weight": 1.0}
        xml = f"""<rss><channel><item><title>Iran fires missiles at Israel, sirens across Tel Aviv</title>
        <link>https://s{i}.com/a</link><pubDate>Thu, 24 Sep 2026 09:{10 + i}:00 GMT</pubDate></item></channel></rss>"""
        arts += parse_feed(xml.encode(), src, NOW)
    feed = build(tmp_path, NOW, [], k, arts, {})
    assert feed["clusters"][0]["urgent"] is True


def test_unwraps_redirector_urls():
    wrapped = "https://redir.folha.com.br/redir/online/mundo/rss091/*https://www1.folha.uol.com.br/mundo/x.shtml"
    assert canonical_url(wrapped) == "https://www1.folha.uol.com.br/mundo/x.shtml"


def test_same_story_pt_en_shares_tokens():
    from wid.text import tokens

    en = tokens("Son of Israeli Ambassador to U.S. Critically Hurt in West Bank Attack")
    pt = tokens("Filho de embaixador de Israel nos EUA fica gravemente ferido em ataque na Cisjordânia")
    assert {"son", "ambassador", "israel", "usa", "westbank", "attack", "hurt"} <= en & pt


def test_clean_summary_removes_feed_junk():
    from wid.text import clean_summary

    g1 = "O filho ✅ Siga o canal de notícias internacionais do g1 no WhatsApp ➡️ Neria Leiter é reservista"
    assert clean_summary(g1) == "O filho Neria Leiter é reservista"
    folha = "Estado grave após ataque. Leia mais (09/24/2026 - 18h05)"
    assert clean_summary(folha) == "Estado grave após ataque."
    wp = "Troops entered the town. The post Troops enter town appeared first on Israel Hayom."
    assert clean_summary(wp) == "Troops entered the town."


def test_google_news_titles_lose_publisher_suffix():
    from wid.fetch import _from_google_news

    xml = b"""<rss><channel><item><title>IDF strikes Hezbollah in Lebanon - The Times of Israel</title>
    <link>https://news.google.com/rss/articles/abc</link><description>&lt;a href="x"&gt;links&lt;/a&gt;</description>
    <pubDate>Thu, 24 Sep 2026 09:00:00 GMT</pubDate></item></channel></rss>"""
    arts = parse_feed(xml, {"name": "Times of Israel", "lang": "en"}, NOW)
    _from_google_news(arts)
    assert arts[0].title == "IDF strikes Hezbollah in Lebanon"
    assert arts[0].summary == ""


def test_origin_and_daily_stats(tmp_path):
    feed = build(tmp_path, NOW, SOURCES, kw(), fetched(), {})
    origins = {a["source"]: a["origin"] for a in feed["top_of_day"]["articles"]}
    assert origins == {"IL News": "israel", "Mundo BR": "brasil", "Intl": "internacional"}

    stats = json.loads((tmp_path / "stats" / "daily.json").read_text())
    today = stats["days"][-1]
    assert today["date"] == "2026-09-24"
    assert today["total"] == 2
    assert today["counts"]["israel"] == 1 and today["counts"]["ucrania_russia"] == 1

    # Uma segunda rodada no mesmo dia sobrescreve o dia, não duplica.
    build(tmp_path, NOW, SOURCES, kw(), [], {})
    assert len(json.loads((tmp_path / "stats" / "daily.json").read_text())["days"]) == 1


def test_usa_tag_and_tokens():
    from wid.text import tokens

    m = kw().match("Pentagon sends second carrier to the Middle East after Houthi missile attack", "")
    assert m.relevant and {"eua", "iemen"} <= m.tags
    # Política interna sem termo de guerra fica de fora.
    assert not kw().match("Trump signs executive order on tariffs", "").relevant
    assert {"usa", "whitehouse"} <= tokens("Casa Branca diz que militares americanos ficam")
    assert "usa" in tokens("Casa Branca diz que militares americanos ficam")
    assert "whitehouse" in tokens("White House says US troops stay")


def test_top_of_day_prefers_fresh_story(tmp_path):
    from datetime import timedelta

    k = kw()
    def story(title, n, hours_ago):
        arts = []
        for i in range(n):
            t = (NOW - timedelta(hours=hours_ago, minutes=i)).strftime("%a, %d %b %Y %H:%M:%S GMT")
            xml = f"<rss><channel><item><title>{title}</title><link>https://s{i}.com/{hash(title)}</link><pubDate>{t}</pubDate></item></channel></rss>"
            arts += parse_feed(xml.encode(), {"name": f"S{i}", "lang": "en", "weight": 1.0}, NOW)
        return arts
    # Ontem: muitos veículos, mas parada há 20 h. Hoje: menos veículos, porém recente.
    old = story("Israel strikes Hezbollah targets in Beirut overnight", 6, 20)
    new = story("Iran proposes plan to end war with Israel", 4, 1)
    feed = build(tmp_path, NOW, [], k, old + new, {})
    assert feed["top_of_day"]["title"].startswith("Iran proposes")


def test_hebrew_and_arabic_are_relevant_and_share_tokens_with_english():
    from wid.text import tokens

    k = kw()
    he = 'צה"ל תקף בעזה: 5 הרוגים בתקיפה ברפיח'
    ar = "غارات إسرائيلية على غزة تقتل 20 فلسطينيا"
    assert k.match(he, "").relevant and {"gaza", "israel"} <= k.match(he, "").tags
    assert k.match(ar, "").relevant and {"gaza", "israel"} <= k.match(ar, "").tags
    assert not k.match("מזג האוויר: גשם בצפון", "").relevant
    assert not k.match("أسعار النفط ترتفع", "").relevant
    assert k.match("عاجل: قصف على بيروت", "").urgent

    en = tokens("Israeli strikes on Gaza kill 20 Palestinians")
    assert {"attack", "gaza", "israel", "killed", "palestinian"} <= tokens(ar) & en
    assert {"attack", "gaza", "killed", "idf"} <= tokens(he)
    assert tokens("وقف إطلاق النار في لبنان وحزب الله") >= {"ceasefire", "lebanon", "hezbollah"}


def test_changed_headline_is_kept_as_edit():
    from datetime import timedelta

    from wid.build import merge
    from wid.fetch import Article

    now = datetime(2026, 9, 25, 12, tzinfo=timezone.utc)
    old = Article("a1", "Israel strikes Gaza hospital", "", "https://x/a", "X", "en", 1.0, now - timedelta(hours=2))
    same = Article("a1", "Israel strikes Gaza hospital.", "", "https://x/a", "X", "en", 1.0, now)
    new = Article("a1", "Israel says it struck Hamas site near Gaza hospital", "", "https://x/a", "X", "en", 1.0, now)

    kept = merge([old], [same], now)[0]
    assert kept.edits == [] and kept.title == "Israel strikes Gaza hospital"
    changed = merge([old], [new], now)[0]
    assert changed.title.startswith("Israel says")
    assert changed.edits == [{"title": "Israel strikes Gaza hospital", "at": "2026-09-25T12:00:00Z"}]
    assert changed.published == old.published
    assert changed.to_json()["edits"][0]["title"] == "Israel strikes Gaza hospital"


def test_global_clock_and_daily_tension_peaks(tmp_path):
    from wid.build import global_index, record_tension, write_stats

    regions = {"gaza": {"tension": 80}, "libano": {"tension": 60}, "iemen": {"tension": 40}, "siria": {"tension": 10}}
    clock = global_index(regions)
    assert clock == {"index": 72, "level": "alta", "leader": "gaza"}
    assert global_index({}) is None

    today = datetime(2026, 9, 25).date()
    days = write_stats(tmp_path, today, [])
    record_tension(tmp_path, days, regions, clock)
    # Rodada seguinte, mais calma: o pico do dia continua.
    days = write_stats(tmp_path, today, [])
    record_tension(tmp_path, days, {"gaza": {"tension": 30}}, {"index": 30})
    saved = json.loads((tmp_path / "stats" / "daily.json").read_text())["days"][-1]
    assert saved["tension"]["gaza"] == 80 and saved["global"] == 72


def test_download_page_is_copied_to_site(tmp_path):
    from wid.build import WEB_DIR, copy_web

    copy_web(tmp_path)
    assert (tmp_path / "index.html").read_text(encoding="utf-8") == (WEB_DIR / "index.html").read_text(encoding="utf-8")
    assert (tmp_path / "icon.svg").exists()
    # O painel vai numa subpasta, com os dados fixos do app em geo.json.
    assert (tmp_path / "painel" / "index.html").exists()
    assert (tmp_path / "painel" / "vendor" / "maplibre-gl.js").exists()
    geo = json.loads((tmp_path / "painel" / "geo.json").read_text(encoding="utf-8"))
    assert geo["labels"]["israel"] == "Israel"
    assert geo["points"]["gaza"] == [33.8, 30.7]


def test_webgeo_reads_app_data():
    from wid import webgeo

    g = webgeo.geo()
    assert len(g["cities"]) >= 50 and all(c["terms"] for c in g["cities"])
    tel_aviv = next(c for c in g["cities"] if c["name"] == "Tel Aviv")
    assert tel_aviv["tag"] == "israel" and abs(tel_aviv["lat"] - 32.08) < 0.01
    assert {"iran", "hezbollah"} <= {r["id"] for r in g["ranges"]}
    assert all(r["km"] > 0 for r in g["ranges"])
    assert g["context"]["gaza"].startswith("Faixa costeira")
    assert g["conflicts"]["gaza"]["start"] == "2023-10-07"
    assert g["milestones"]["israel"][0]["date"] == "1948"
    assert g["origins"]["arabe"] == "Imprensa árabe"
    assert any(c["city"] == "Teerã" and c["zone"] == "Asia/Tehran" for c in g["capitals"])
    israel = next(p for p in g["power"] if p["tag"] == "israel")
    assert israel["roles"][0] == {"role": "Primeiro-ministro", "name": "Benjamin Netanyahu", "person": "netanyahu"}
    assert g["wiki"]["netanyahu"] == "Benjamin_Netanyahu"
    # Todas as regiões com nome têm ponto no mapa.
    assert set(g["labels"]) == set(g["points"])
