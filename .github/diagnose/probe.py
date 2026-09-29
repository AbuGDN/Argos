"""Baixa cada URL (url<TAB>regex) e mostra código, tamanho e trechos em volta do regex."""
import gzip
import re
import sys
import urllib.request

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36"
for line in open(sys.argv[1], encoding="utf-8"):
    url, _, rest = line.rstrip("\n").partition("\t")
    pattern, _, after = rest.partition("\t")
    after = int(after or 450)
    if not url:
        continue
    print("=" * 20, url)
    try:
        # Origin: para ver se o serviço libera uso direto no navegador (CORS), como no painel web.
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Origin": "https://abugdn.github.io"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            print("CORS:", resp.headers.get("Access-Control-Allow-Origin"), "| tipo:", resp.headers.get("Content-Type"))
            raw = resp.read()
            if raw[:2] == b"\x1f\x8b":
                raw = gzip.decompress(raw)
            body = raw.decode("utf-8", "replace")
            print("HTTP", resp.status, len(body), "bytes")
    except Exception as exc:
        print("ERRO", type(exc).__name__, exc)
        continue
    print(body[:300].replace("\n", " "))
    if pattern:
        hits = list(re.finditer(pattern, body))
        print(f"{len(hits)} ocorrências de {pattern!r}")
        for m in hits[:2] + hits[-3:]:
            print("  …", re.sub(r"\s+", " ", body[max(0, m.start() - 150):m.end() + after]))
