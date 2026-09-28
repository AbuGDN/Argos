"""Resume o GeoJSON do DeepStateMap: tipos de geometria e nomes/estilos dos polígonos."""
import collections
import json
import urllib.request

UA = "Mozilla/5.0 (X11; Linux x86_64) Chrome/128.0 Safari/537.36"
req = urllib.request.Request("https://deepstatemap.live/api/history/last", headers={"User-Agent": UA})
data = json.load(urllib.request.urlopen(req, timeout=60))
print("chaves:", list(data)[:10], "id:", data.get("id"), "datetime:", data.get("datetime"))
feats = data["map"]["features"]
kinds = collections.Counter()
for f in feats:
    g = f["geometry"]["type"]
    p = f.get("properties", {})
    if g != "Point":
        kinds[(g, p.get("name", "")[:90], p.get("styleUrl", ""), p.get("fill", ""))] += 1
for (g, name, style, fill), n in kinds.most_common(40):
    npts = 0
    print(n, g, "|", name, "|", style, "|", fill)
poly = next(f for f in feats if f["geometry"]["type"] != "Point")
print("props exemplo:", {k: v for k, v in poly["properties"].items() if k != "description"})
