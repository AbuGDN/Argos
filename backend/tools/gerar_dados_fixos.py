"""Gera os dados fixos do app a partir das fontes originais (nada digitado à mão).

    cd backend
    .venv/Scripts/pip install -r tools/requirements.txt      (o mesmo .venv do backend, ver README)
    .venv/Scripts/python tools/gerar_dados_fixos.py                 # baixa as fontes e reescreve os .kt

Saídas (android/app/src/main/java/com/abugdn/wid/data/):
  WarDeaths.kt          mortes em combate por guerra (UCDP Battle-Related Deaths, conflitos de
                        backend/config/radar.yaml -> attention.conflicts[].ucdp)
  MilitarySpending.kt   gastos militares (SIPRI Military Expenditure Database)
  NuclearForces.kt      ogivas nucleares por país (FAS, Status of World Nuclear Forces)

Site fora do ar ou recusando a conexão? Baixe o arquivo pelo navegador e passe o caminho:
    --milex arquivo.xlsx   --ucdp arquivo.zip   --fas pagina.html
O cabeçalho de cada .kt grava o SHA-256 do arquivo usado.

Saiu edição nova de uma fonte? Troque a URL abaixo, rode de novo e confira o diff.
País novo numa lista sem nome em português em COUNTRIES: o script para e diz qual falta.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import re
import subprocess
import sys
import zipfile
from html.parser import HTMLParser
from pathlib import Path

import openpyxl
import yaml

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "android/app/src/main/java/com/abugdn/wid/data"
RADAR_YAML = ROOT / "backend/config/radar.yaml"

UCDP_URL = "https://ucdp.uu.se/downloads/brd/ucdp-brd-conf-261-csv.zip"
UCDP_VERSION = "26.1"
MILEX_URL = "https://www.sipri.org/sites/default/files/SIPRI-Milex-data-1949-2025_v1.2.xlsx"
FAS_URL = "https://fas.org/initiative/status-world-nuclear-forces/"
FAS_AS_OF = "início de 2026"  # a página diz "as of the beginning of 2026"; conferir ao atualizar
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36"}

# Nome na fonte (SIPRI/FAS) -> (nome em português, código ISO de 2 letras para a bandeira).
COUNTRIES = {
    "United States of America": ("EUA", "US"), "United States": ("EUA", "US"), "China": ("China", "CN"),
    "Russia": ("Rússia", "RU"), "Germany": ("Alemanha", "DE"), "India": ("Índia", "IN"),
    "United Kingdom": ("Reino Unido", "GB"), "Saudi Arabia": ("Arábia Saudita", "SA"), "Ukraine": ("Ucrânia", "UA"),
    "France": ("França", "FR"), "Japan": ("Japão", "JP"), "Korea, South": ("Coreia do Sul", "KR"),
    "Israel": ("Israel", "IL"), "Poland": ("Polônia", "PL"), "Italy": ("Itália", "IT"), "Australia": ("Austrália", "AU"),
    "Canada": ("Canadá", "CA"), "Türkiye": ("Turquia", "TR"), "Spain": ("Espanha", "ES"), "Netherlands": ("Países Baixos", "NL"),
    "Brazil": ("Brasil", "BR"), "Iran": ("Irã", "IR"), "Pakistan": ("Paquistão", "PK"), "Algeria": ("Argélia", "DZ"),
    "Kuwait": ("Kuwait", "KW"), "Oman": ("Omã", "OM"), "Qatar": ("Catar", "QA"), "Jordan": ("Jordânia", "JO"),
    "Lithuania": ("Lituânia", "LT"), "Latvia": ("Letônia", "LV"), "Estonia": ("Estônia", "EE"), "Armenia": ("Armênia", "AM"),
    "Azerbaijan": ("Azerbaijão", "AZ"), "Morocco": ("Marrocos", "MA"), "Myanmar": ("Mianmar", "MM"), "Mali": ("Mali", "ML"),
    "Burkina Faso": ("Burkina Faso", "BF"), "Lebanon": ("Líbano", "LB"), "Greece": ("Grécia", "GR"), "Singapore": ("Singapura", "SG"),
    "Taiwan": ("Taiwan", "TW"), "Norway": ("Noruega", "NO"), "Sweden": ("Suécia", "SE"), "Finland": ("Finlândia", "FI"),
    "Denmark": ("Dinamarca", "DK"), "Egypt": ("Egito", "EG"), "Mexico": ("México", "MX"), "Colombia": ("Colômbia", "CO"),
    "United Arab Emirates": ("Emirados Árabes", "AE"), "Iraq": ("Iraque", "IQ"), "Belarus": ("Belarus", "BY"),
    "Korea, North": ("Coreia do Norte", "KP"), "North Korea": ("Coreia do Norte", "KP"), "Romania": ("Romênia", "RO"),
    "Indonesia": ("Indonésia", "ID"), "Sudan": ("Sudão", "SD"), "Ethiopia": ("Etiópia", "ET"), "Nigeria": ("Nigéria", "NG"),
    "Belgium": ("Bélgica", "BE"), "Switzerland": ("Suíça", "CH"), "Chile": ("Chile", "CL"), "Argentina": ("Argentina", "AR"),
    "Mongolia": ("Mongólia", "MN"), "Sri Lanka": ("Sri Lanka", "LK"), "Chad": ("Chade", "TD"), "Niger": ("Níger", "NE"),
    "Guinea-Bissau": ("Guiné-Bissau", "GW"), "Libya": ("Líbia", "LY"), "Bahrain": ("Bahrein", "BH"), "Brunei": ("Brunei", "BN"),
    "South Sudan": ("Sudão do Sul", "SS"), "Hungary": ("Hungria", "HU"),
}
# Países sempre mostrados na aba "Guerras e Brasil", com a série desde MILEX_FROM.
MILEX_FOCUS = ["Brazil", "United States of America", "China", "Russia", "Ukraine", "Israel", "Iran", "Saudi Arabia",
               "India", "Pakistan", "Germany", "Poland"]
MILEX_FROM = 2000
TOP = 15


LOCAL: dict[str, Path] = {}  # url -> arquivo baixado à mão (--milex/--ucdp/--fas)
USED: dict[str, str] = {}    # url -> sha256 do conteúdo usado


def download(url: str) -> bytes:
    if url in LOCAL:
        data = LOCAL[url].read_bytes()
    else:
        data = _curl(url)
    USED[url] = hashlib.sha256(data).hexdigest()
    return data


def _curl(url: str) -> bytes:
    """Baixa com o curl: o site do SIPRI derruba conexões vindas do Python (medido em 01/10/2026:
    httpx e urllib levam "conexão cancelada pelo host"; o curl passa)."""
    return subprocess.run(["curl", "-sfL", "--max-time", "300", "-A", UA["User-Agent"], url],
                          check=True, capture_output=True).stdout


def flag(iso2: str) -> str:
    return "".join(chr(0x1F1E6 + ord(c) - ord("A")) for c in iso2.upper())


def pt(name: str) -> tuple[str, str]:
    if name not in COUNTRIES:
        sys.exit(f"Falta nome em português para {name!r} em COUNTRIES")
    return COUNTRIES[name]


def kstr(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def knum(v: float | None, digits: int = 1) -> str:
    return "null" if v is None else f"{round(v, digits)}"


def header(source: str) -> str:
    return (f"package com.abugdn.wid.data\n\n// GERADO por backend/tools/gerar_dados_fixos.py a partir de {source}\n"
            f"// (sha256 do arquivo usado: {USED[source]})\n"
            "// Não edite à mão: rode o gerador de novo.\n\n")


# --- UCDP: mortes em combate por guerra ----------------------------------------------------

def gen_war_deaths() -> str:
    conflicts = yaml.safe_load(RADAR_YAML.read_text(encoding="utf-8"))["attention"]["conflicts"]
    z = zipfile.ZipFile(io.BytesIO(download(UCDP_URL)))
    rows = list(csv.DictReader(io.TextIOWrapper(z.open(next(n for n in z.namelist() if n.endswith(".csv"))), encoding="utf-8")))
    by_id: dict[str, dict[int, int]] = {}
    for r in rows:
        by_id.setdefault(r["conflict_id"], {})[int(r["year"])] = int(r["bd_best"])
    last = max(int(r["year"]) for r in rows)
    years = [last - 2, last - 1, last]
    lines = []
    for c in conflicts:
        missing = [i for i in c["ucdp"] if str(i) not in by_id]
        if missing:
            sys.exit(f"UCDP sem os conflitos {missing} ({c['id']})")
        deaths = [sum(by_id[str(i)].get(y, 0) for i in c["ucdp"]) for y in years]
        tag = kstr(c["tag"]) if c.get("tag") else "null"
        lines.append(f"    WarDeaths({kstr(c['id'])}, {kstr(c['name'])}, {tag}, listOf({', '.join(map(str, deaths))})),")
    return (header(UCDP_URL) +
            "/** Mortes em combate (estimativa \"best\" do UCDP) de cada guerra em [WAR_DEATHS_YEARS]. */\n"
            "data class WarDeaths(val id: String, val name: String, val tag: String?, val deaths: List<Int>) {\n"
            "    val latest: Int get() = deaths.lastOrNull() ?: 0\n}\n\n"
            f"const val UCDP_VERSION = {kstr(UCDP_VERSION)}\n"
            f"val WAR_DEATHS_YEARS = listOf({', '.join(map(str, years))})\n\n"
            "val WAR_DEATHS = listOf(\n" + "\n".join(lines) + "\n)\n")


# --- SIPRI: gastos militares ----------------------------------------------------------------

def _sheet(wb, name: str) -> dict[str, dict[int, float]]:
    """Planilha do SIPRI -> {país: {ano: valor}} (só números; ". ." e "xxx" ficam de fora)."""
    rows = list(wb[name].iter_rows(values_only=True))
    head = next(i for i, r in enumerate(rows) if r and r[0] == "Country")
    cols = {i: v for i, v in enumerate(rows[head]) if isinstance(v, int)}
    out = {}
    for r in rows[head + 1:]:
        if not r or not isinstance(r[0], str):
            continue
        vals = {cols[i]: float(v) for i, v in enumerate(r) if i in cols and isinstance(v, (int, float))}
        if vals:
            out[r[0].strip()] = vals
    return out


def gen_milex() -> str:
    wb = openpyxl.load_workbook(io.BytesIO(download(MILEX_URL)), read_only=True, data_only=True)
    cur, gdp, const = _sheet(wb, "Current US$"), _sheet(wb, "Share of GDP"), _sheet(wb, "Constant (2024) US$")
    year = max(y for v in cur.values() for y in v)
    world = sum(v[year] for v in cur.values() if year in v)

    def entry(name: str, series: bool = False) -> str:
        pt_name, iso = pt(name)
        usd = cur.get(name, {}).get(year)
        g = gdp.get(name, {}).get(year)
        c = const.get(name, {})
        change = (c[year] / c[year - 10] - 1) * 100 if year in c and c.get(year - 10) else None
        s = ""
        if series:
            vals = [c.get(y) for y in range(MILEX_FROM, year + 1)]
            s = ", series = listOf(" + ", ".join(knum(v / 1000 if v is not None else None, 2) for v in vals) + ")"
        return (f"    MilexCountry({kstr(pt_name)}, {kstr(flag(iso))}, {knum(usd / 1000 if usd else None, 1)}, "
                f"{knum(g * 100 if g is not None else None, 1)}, {knum(change, 0)}{s}),")

    top_usd = sorted((n for n in cur if year in cur[n]), key=lambda n: -cur[n][year])[:TOP]
    top_gdp = sorted((n for n in gdp if year in gdp[n]), key=lambda n: -gdp[n][year])[:TOP]
    return (header(MILEX_URL) +
            "/** Gasto militar de um país em [MILEX_YEAR]: US$ bilhões (valores correntes), % do PIB e variação real\n"
            " * em 10 anos (dólares constantes de 2024). [series]: US$ bilhões constantes de [MILEX_FROM] a [MILEX_YEAR]. */\n"
            "data class MilexCountry(\n    val country: String,\n    val flag: String,\n    val usdBn: Double?,\n"
            "    val gdpPct: Double?,\n    val change10y: Double?,\n    val series: List<Double?> = emptyList(),\n)\n\n"
            f"const val MILEX_YEAR = {year}\nconst val MILEX_FROM = {MILEX_FROM}\n"
            f"/** Soma dos países com dado em {year}, US$ bilhões correntes. */\nconst val MILEX_WORLD_BN = {knum(world / 1000, 0)}\n\n"
            "val MILEX_TOP = listOf(\n" + "\n".join(entry(n) for n in top_usd) + "\n)\n\n"
            "val MILEX_GDP_TOP = listOf(\n" + "\n".join(entry(n) for n in top_gdp) + "\n)\n\n"
            "val MILEX_FOCUS = listOf(\n" + "\n".join(entry(n, series=True) for n in MILEX_FOCUS) + "\n)\n")


# --- FAS: arsenais nucleares ----------------------------------------------------------------

class _Table(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rows: list[list[str]] = []
        self.cell: list[str] | None = None

    def handle_starttag(self, tag, attrs):
        if tag == "tr":
            self.rows.append([])
        elif tag in ("td", "th"):
            self.cell = []

    def handle_endtag(self, tag):
        if tag in ("td", "th") and self.cell is not None and self.rows:
            self.rows[-1].append(" ".join("".join(self.cell).split()))
            self.cell = None

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)


def _int(cell: str) -> int | None:
    m = re.match(r"~?([\d,]+)", cell.strip())
    return int(m.group(1).replace(",", "")) if m else None


def gen_nuclear() -> str:
    p = _Table()
    p.feed(download(FAS_URL).decode("utf-8"))
    expected = ["Country", "Deployed Strategic", "Deployed Nonstrategic", "Reserve/Nondeployed", "Military Stockpile", "Total Inventory"]
    head = next((i for i, r in enumerate(p.rows) if r and r[0] == "Country"), None)
    if head is None or [re.sub(r"\(.*", "", c).strip() for c in p.rows[head][:6]] != expected:
        sys.exit("Tabela da FAS mudou de formato: confira as colunas")
    lines = []
    for r in p.rows[head + 1:]:
        if len(r) < 6 or not _int(r[5]) or r[0].startswith("Total"):
            continue
        name = re.sub(r"\(.*", "", r[0]).strip()
        pt_name, iso = pt(name)
        v = [_int(c) for c in r[1:6]]
        lines.append(f"    NuclearForce({kstr(pt_name)}, {kstr(flag(iso))}, "
                     + ", ".join("null" if x is None else str(x) for x in v) + "),")
    if len(lines) < 9:
        sys.exit(f"FAS: só {len(lines)} países")
    return (header(FAS_URL) +
            "/** Ogivas por país. [stockpile] = estoque militar (prontas ou guardadas para uso);\n"
            " * [total] = estoque + aposentadas à espera de desmonte. null = a FAS não informa. */\n"
            "data class NuclearForce(\n    val country: String,\n    val flag: String,\n    val deployedStrategic: Int?,\n"
            "    val deployedNonstrategic: Int?,\n    val reserve: Int?,\n    val stockpile: Int?,\n    val total: Int?,\n)\n\n"
            f"const val NUCLEAR_AS_OF = {kstr(FAS_AS_OF)}\n\n"
            "val NUCLEAR_FORCES = listOf(\n" + "\n".join(lines) + "\n)\n")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--milex", type=Path)
    ap.add_argument("--ucdp", type=Path)
    ap.add_argument("--fas", type=Path)
    args = ap.parse_args()
    for url, path in ((MILEX_URL, args.milex), (UCDP_URL, args.ucdp), (FAS_URL, args.fas)):
        if path:
            LOCAL[url] = path
    files = {"WarDeaths.kt": gen_war_deaths(), "MilitarySpending.kt": gen_milex(), "NuclearForces.kt": gen_nuclear()}
    for name, text in files.items():
        (OUT / name).write_text(text, encoding="utf-8", newline="\n")
        print("escrito", OUT / name)


if __name__ == "__main__":
    main()
