"""Gera android/.../ui/ArgosIconPaths.kt: os ícones que substituem emojis usados como ícone.

    cd backend && .venv/Scripts/python tools/gerar_icones.py

Desenhos do Material Symbols (Google, licença Apache-2.0), estilo "outlined", baixados de
github.com/google/material-design-icons. Só entram os ícones da tabela abaixo: o pacote
material-icons-extended inteiro pesaria vários MB num APK que não é encolhido (isMinifyEnabled = false).

Emoji novo como ícone num título de cartão, botão ou ferramenta? Acrescente aqui (nomes possíveis, na
ordem de preferência; vale o primeiro que existir) e rode de novo. Emoji sem ícone continua aparecendo
como emoji — nada some.
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "android/app/src/main/java/com/abugdn/wid/ui/ArgosIconPaths.kt"
URL = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{n}/materialsymbolsoutlined/{n}_24px.svg"

ICONS = {
    "⚠": ["warning"], "✈": ["flight"], "📡": ["radar"], "👁": ["visibility"], "📅": ["calendar_month"],
    "🎯": ["target", "my_location"], "🔥": ["local_fire_department"], "⚓": ["anchor"], "🗺": ["map"],
    "⚖": ["balance"], "🚢": ["directions_boat"], "🌐": ["language"], "📍": ["location_on"],
    "🚨": ["e911_emergency", "emergency_home", "notifications_active"], "🔫": ["swords", "military_tech"],
    "★": ["star"], "🛢": ["oil_barrel"], "🕊": ["handshake"], "🗂": ["folder_copy"], "🔔": ["notifications"],
    "📊": ["bar_chart"], "👤": ["person"], "🔍": ["search"], "🌍": ["public"], "📰": ["newspaper"],
    "🌡": ["thermostat"], "🌾": ["grass"], "⛽": ["local_gas_station"], "🤝": ["handshake"],
    "📉": ["trending_down"], "✅": ["check_circle"], "🧩": ["extension"], "🎲": ["casino"],
    "🩸": ["bloodtype"], "🏚": ["house"], "🏛": ["account_balance"], "📜": ["history_edu"],
    "🌋": ["earthquake", "landslide"], "⏳": ["hourglass_top", "hourglass"], "🗓": ["event_note"],
    "✓": ["check"], "🕰": ["history"], "✏": ["edit"], "🗣": ["record_voice_over"], "🛰": ["satellite_alt"],
    "🪖": ["military_tech"], "🔁": ["repeat"], "🔎": ["manage_search"], "🧠": ["psychology"], "⚑": ["flag"],
    "✕": ["close"], "📁": ["folder"], "🌦": ["partly_cloudy_day"], "🎓": ["school"], "💬": ["chat"],
    "💵": ["payments"], "🧳": ["luggage"], "🐔": ["egg"], "🍞": ["bakery_dining"], "🔢": ["pin"],
    "📚": ["library_books"], "📈": ["trending_up"], "📥": ["inbox"], "🧲": ["join", "hub"], "❌": ["cancel"],
    "🎗": ["volunteer_activism"], "⛔": ["block"], "☀": ["light_mode"], "☾": ["dark_mode"], "🧭": ["explore"],
    "🏠": ["home"], "📖": ["menu_book"], "🎨": ["palette"], "📶": ["signal_cellular_alt"], "🗞": ["feed"],
    "📏": ["straighten"], "👀": ["groups"], "🕯": ["heart_broken"], "💰": ["savings"],
    "☢": ["bomb", "explosion"], "🕸": ["hub"], "🔬": ["science"], "🚫": ["block"],
    "🟢": ["radio_button_checked"], "⚙": ["settings"], "🧰": ["handyman"], "✔": ["check"],
    "🧮": ["calculate"], "🕘": ["schedule"], "🛒": ["shopping_cart"], "📦": ["inventory_2"],
    "🧾": ["receipt_long"], "📱": ["smartphone"], "🖥": ["computer"],
    "▶": ["play_circle"],
    # Temas da tela Hoje (TOPIC_LABELS em Models.kt).
    "🛸": ["drone", "flight"], "🚀": ["rocket_launch"], "🩹": ["healing"], "💻": ["laptop", "computer"],
}


def fetch(name: str) -> str | None:
    r = subprocess.run(["curl", "-sfL", "--max-time", "60", URL.format(n=name)], capture_output=True)
    if r.returncode != 0:
        return None
    paths = re.findall(r'<path d="([^"]+)"', r.stdout.decode("utf-8"))
    return " ".join(paths) if paths else None


def main() -> None:
    lines, cache = [], {}
    for emoji, names in ICONS.items():
        for n in names:
            if n not in cache:
                cache[n] = fetch(n)
            if cache[n]:
                lines.append(f'    "{emoji}" to "{cache[n]}", // {n}')
                break
        else:
            sys.exit(f"nenhum ícone existe para {emoji}: {names}")
    OUT.write_text(
        "package com.abugdn.wid.ui\n\n"
        "// GERADO por backend/tools/gerar_icones.py (Material Symbols outlined, Google, Apache-2.0).\n"
        "// Não edite à mão. Caminhos SVG na caixa 0 -960 960 960.\n\n"
        "internal val EMOJI_ICON_PATHS: Map<String, String> = mapOf(\n" + "\n".join(lines) + "\n)\n",
        encoding="utf-8", newline="\n")
    print(f"escrito {OUT} ({len(lines)} ícones)")


if __name__ == "__main__":
    main()
