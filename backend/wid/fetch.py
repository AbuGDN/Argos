"""Download e parsing dos feeds RSS."""

import calendar
import hashlib
import logging
import re
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timezone
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

import feedparser
import httpx

from .text import clean_html, clean_summary, truncate

log = logging.getLogger(__name__)

# Vários sites (Times of Israel, Al-Monitor) recusam user-agents de robô.
USER_AGENT = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/128.0 Safari/537.36"
)
TIMEOUT = 20.0
_IMG_RE = re.compile(r"<img[^>]+src=[\"']([^\"']+)[\"']", re.I)
_TRACKING_PARAMS = re.compile(r"^(utm_|at_|cmpid$|ocid$|fbclid$|gclid$)")


@dataclass
class Article:
    id: str
    title: str
    summary: str
    url: str
    source: str
    lang: str
    weight: float
    published: datetime
    image: str | None = None
    origin: str = "internacional"
    # Manchetes anteriores deste mesmo link: [{"title": antiga, "at": quando mudou}].
    edits: list[dict] = field(default_factory=list)

    def to_json(self) -> dict:
        data = {
            "id": self.id,
            "title": self.title,
            "summary": self.summary,
            "url": self.url,
            "source": self.source,
            "lang": self.lang,
            "published": iso(self.published),
            "image": self.image,
            "origin": self.origin,
        }
        if self.edits:
            data["edits"] = self.edits
        return data

    @classmethod
    def from_json(cls, data: dict, weight: float = 1.0, origin: str | None = None) -> "Article":
        url = canonical_url(data["url"])
        return cls(
            id=article_id(url),
            title=data["title"],
            summary=clean_summary(data.get("summary", "")),
            url=url,
            source=data["source"],
            lang=data.get("lang", "en"),
            weight=weight,
            published=parse_iso(data["published"]),
            image=data.get("image"),
            origin=origin or data.get("origin", "internacional"),
            edits=data.get("edits", []),
        )


def iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_iso(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def canonical_url(url: str) -> str:
    # Redirecionadores que embrulham o link real, ex. redir.folha.com.br/.../*https://www1.folha...
    wrapped = url.find("/*http")
    if wrapped != -1:
        url = url[wrapped + 2:]
    parts = urlsplit(url.strip())
    query = [(k, v) for k, v in parse_qsl(parts.query, keep_blank_values=True) if not _TRACKING_PARAMS.match(k)]
    return urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(query), ""))


def article_id(url: str) -> str:
    return hashlib.sha1(canonical_url(url).encode()).hexdigest()[:12]


def _entry_time(entry, now: datetime) -> datetime:
    for key in ("published_parsed", "updated_parsed"):
        parsed = entry.get(key)
        if parsed:
            dt = datetime.fromtimestamp(calendar.timegm(parsed), tz=timezone.utc)
            # Alguns feeds publicam horário no futuro (fuso errado).
            return min(dt, now)
    return now


# --- Fotos: tamanho certo --------------------------------------------------------------------
# Medido em 01/10/2026 baixando as fotos do feed publicado (largura real de cada arquivo):
#   pequenas, borravam em tela cheia: The Guardian 140 px, CNN Brasil 200, BBC 240, Ynet 320;
#   pesadas: Defense News 4-5 mil px (até 12 MB), NPR e G1 até 6 mil px;
#   quebradas: Estadão (&amp; no endereço -> HTTP 400), Walla (403 para qualquer cliente), NPR às vezes
#   manda o pixel de rastreamento 1x1 ou o texto "undefined";
#   sem foto no RSS: Al Jazeera, Folha, DW, Poder360, boa parte do Middle East Eye.
# O que tem conserto no endereço vai em better_image; o resto, pela foto da página (page_image).
# G1 continua pesado: o endereço é assinado (thumbor) e a página não traz versão menor.
_BBC_SIZE = re.compile(r"(ichef\.bbci\.co\.uk/(?:ace/)?(?:standard|ws)/)(\d+)(/)")
_NPR_SIZE = re.compile(r"(brightspotcdn\.com/.*/resize/)(\d+)x(\d+)!?(/)")
_CNN_BR_SIZE = re.compile(r"^(https://admin\.cnnbrasil\.com\.br/.*[?&]w=)(\d+)")
_YNET_SIZE = re.compile(r"^(https://ynet-pic\d*\.yit\.co\.il/.*_)(small|medium|large)(\.\w+)$")
_GUARDIAN_THUMB = re.compile(r"^https://i\.guim\.co\.uk/img/media/([0-9a-f]+)/[^?]*\?(?:.*&)?width=(\d+)")
_GUARDIAN_SRC = re.compile(r"https://i\.guim\.co\.uk/img/media/([0-9a-f]+)/[^\"'\s]+")
# Fotos que não abrem fora do site (403) ou que não são foto.
_DEAD_IMAGE = re.compile(r"^https?://images\.wcdn\.co\.il/|/tracking/|rss-pixel")
# Originais gigantes sem tamanho no endereço: melhor a foto da página (Defense News: 1200 px, ~100 KB).
_HUGE_IMAGE = re.compile(r"^https://cloudfront-[\w-]+\.images\.arcpublishing\.com/")
# Página que não ajuda: link do Google News (a "foto" é o logo dele) e sites que não respondem a robô.
_NO_PAGE = re.compile(r"^https?://(?:news\.google\.com|(?:www\.)?washingtonpost\.com)/")
IMAGE_WIDTH = 976  # largura pedida quando o servidor aceita escolher (o app mostra até a largura da tela)


def better_image(url: str | None) -> str | None:
    """Conserta o endereço da foto quando dá: miniatura -> foto grande, original gigante -> tamanho de tela,
    endereço quebrado -> certo, foto que não abre -> None. Guardian é assinado: ver guardian_large_image."""
    if not url:
        return None
    url = url.strip().replace("&amp;", "&")
    if not url.startswith("http") or _DEAD_IMAGE.search(url):
        return None
    m = _BBC_SIZE.search(url)
    if m and int(m.group(2)) < IMAGE_WIDTH:
        return url[:m.start(2)] + str(IMAGE_WIDTH) + url[m.end(2):]
    m = _NPR_SIZE.search(url)
    if m and int(m.group(2)) > 1600:
        return url[:m.start(2)] + "1200" + url[m.end(4) - 1:]
    m = _CNN_BR_SIZE.search(url)
    if m and int(m.group(2)) < IMAGE_WIDTH:
        return url[:m.start(2)] + str(IMAGE_WIDTH) + url[m.end(2):]
    m = _YNET_SIZE.match(url)
    if m:
        return m.group(1) + "x-large" + m.group(3)
    return url


def is_guardian_thumb(url: str | None) -> bool:
    m = _GUARDIAN_THUMB.match(url or "")
    return bool(m) and int(m.group(2)) < 400


def guardian_large_image(html: str, thumb: str) -> str | None:
    """Na página da notícia, a mesma foto (mesmo id de mídia) em versão grande, já assinada pelo site e
    sem o selo do jornal (o og:image vem com o logo por cima): a maior com width entre 600 e 1300."""
    m = _GUARDIAN_THUMB.match(thumb)
    if not m:
        return None
    best, best_w = None, 0
    for found in _GUARDIAN_SRC.finditer(html.replace("&amp;", "&")):
        src = found.group(0)
        w = re.search(r"[?&]width=(\d+)", src)
        if found.group(1) != m.group(1) or not w or "overlay" in src or "fit=max" not in src:
            continue
        width = int(w.group(1))
        if 600 <= width <= 1300 and width > best_w:
            best, best_w = src, width
    return best


_META = re.compile(r"<meta\b[^>]*>", re.I)


def page_image(html: str) -> str | None:
    """Foto que a página declara para compartilhamento (og:image, ou twitter:image), em qualquer ordem
    de atributos (a DW escreve content antes de property)."""
    found = {}
    for tag in _META.findall(html):
        key = re.search(r"(?:property|name)\s*=\s*[\"']([^\"']+)", tag, re.I)
        val = re.search(r"content\s*=\s*[\"']([^\"']+)", tag, re.I)
        if key and val and key.group(1).lower() in ("og:image", "og:image:url", "twitter:image"):
            found.setdefault(key.group(1).lower(), val.group(1))
    url = found.get("og:image") or found.get("og:image:url") or found.get("twitter:image")
    return better_image(url)


def needs_page_image(art: "Article") -> bool:
    if _NO_PAGE.match(art.url or ""):
        return False
    return not art.image or is_guardian_thumb(art.image) or bool(_HUGE_IMAGE.match(art.image or ""))


def upgrade_images(articles: list["Article"], get_html, checked: dict | None = None, now: datetime | None = None,
                   limit: int = 40) -> int:
    """Busca na página da notícia a foto que faltou ou veio ruim (no máximo [limit] páginas por rodada).
    [checked] guarda {url: quando} das páginas já tentadas, para não baixar de novo a cada 30 min; o
    registro da notícia também é mantido entre rodadas, então a foto achada fica."""
    checked = checked if checked is not None else {}
    todo = [a for a in articles if needs_page_image(a) and a.url not in checked][:limit]

    def one(art):
        try:
            html = get_html(art.url)
        except Exception as exc:
            log.info("foto da página (%s): %s", art.url, exc)
            return art, None
        if is_guardian_thumb(art.image):
            return art, guardian_large_image(html, art.image)
        return art, page_image(html)

    stamp = iso(now) if now else ""
    with ThreadPoolExecutor(max_workers=6) as pool:
        for art, found in pool.map(one, todo):
            checked[art.url] = stamp
            if found:
                art.image = found
    return len(todo)


def _entry_image(entry) -> str | None:
    return better_image(_entry_image_raw(entry))


def _entry_image_raw(entry) -> str | None:
    for key in ("media_content", "media_thumbnail"):
        for media in entry.get(key) or []:
            if media.get("url") and media.get("medium", "image") == "image":
                return media["url"]
    for link in entry.get("links") or []:
        if link.get("rel") == "enclosure" and str(link.get("type", "")).startswith("image"):
            return link.get("href")
    raw = entry.get("summary", "")
    for content in entry.get("content") or []:
        raw += content.get("value", "")
    m = _IMG_RE.search(raw)
    return m.group(1) if m else None


def parse_feed(data: bytes, source: dict, now: datetime) -> list[Article]:
    feed = feedparser.parse(data)
    articles = []
    for entry in feed.entries:
        url = entry.get("link")
        title = clean_html(entry.get("title"))
        if not url or not title:
            continue
        summary = clean_summary(clean_html(entry.get("summary") or entry.get("description")))
        if summary.startswith(title):
            summary = summary[len(title):].strip(" -–:")
        articles.append(
            Article(
                id=article_id(url),
                title=title,
                summary=truncate(summary, 500),
                url=canonical_url(url),
                source=source["name"],
                lang=source.get("lang", "en"),
                weight=float(source.get("weight", 1.0)),
                published=_entry_time(entry, now),
                image=_entry_image(entry),
                origin=source.get("origin", "internacional"),
            )
        )
    return articles


def _from_google_news(articles: list[Article]) -> None:
    """Google News põe " - Nome do Veículo" no fim do título e o resumo é só links."""
    for art in articles:
        head, sep, _ = art.title.rpartition(" - ")
        if sep and head:
            art.title = head
        art.summary = ""


def fetch_source(client: httpx.Client, source: dict, now: datetime) -> tuple[list[Article], str | None]:
    """Tenta cada URL da fonte (`url` pode ser uma lista de alternativas)."""
    urls = source["url"] if isinstance(source["url"], list) else [source["url"]]
    errors = []
    for url in urls:
        try:
            resp = client.get(url)
            resp.raise_for_status()
            articles = parse_feed(resp.content, source, now)
            if "news.google.com" in url:
                _from_google_news(articles)
            if articles:
                return articles, None
            errors.append(f"{url}: feed vazio ou inválido")
        except Exception as exc:  # uma fonte quebrada não pode derrubar as outras
            errors.append(f"{url}: {type(exc).__name__} {getattr(getattr(exc, 'response', None), 'status_code', '')}".strip())
    log.warning("falha em %s: %s", source["name"], errors)
    return [], " | ".join(errors)[:400]


def fetch_all(sources: list[dict], now: datetime) -> tuple[list[Article], dict]:
    status = {}
    articles: list[Article] = []
    headers = {"User-Agent": USER_AGENT, "Accept": "application/rss+xml, application/xml, text/xml, */*"}
    with httpx.Client(headers=headers, timeout=TIMEOUT, follow_redirects=True) as client:
        with ThreadPoolExecutor(max_workers=8) as pool:
            results = pool.map(lambda s: fetch_source(client, s, now), sources)
            for source, (items, error) in zip(sources, results):
                articles.extend(items)
                status[source["name"]] = {"ok": error is None, "items": len(items), "error": error}
    return articles, status
