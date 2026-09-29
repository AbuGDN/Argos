"""Filtro de relevância (é notícia de guerra?) e tags por região."""

import re
from dataclasses import dataclass, field
from pathlib import Path

import yaml

from .text import normalize_rtl


_RTL = re.compile(r"[\u0590-\u06ff]")
# Prefixos que o hebraico e o árabe colam na palavra ("בעזה", "والقدس").
_RTL_PREFIX = r"(?:[והבלמשכ]{0,3}|[وفبلك]{0,2})"


def _compile(terms: list[str]) -> list[tuple[str, re.Pattern]]:
    """Termos em minúsculas; "!TERMO" diferencia maiúsculas (casado no texto original)."""
    compiled = []
    for term in terms:
        if term.startswith("!"):
            core = term[1:]
            compiled.append((term, re.compile(r"(?<!\w)" + re.escape(core) + r"(?!\w)")))
            continue
        term = normalize_rtl(term.lower())
        if _RTL.search(term):
            core = re.escape(term.rstrip("*"))
            tail = r"\w*" if term.endswith("*") else r"(?!\w)"
            pattern = r"(?<!\w)" + _RTL_PREFIX + core + tail
        elif term.endswith("*"):
            pattern = r"(?<!\w)" + re.escape(term[:-1]) + r"\w*"
        else:
            pattern = r"(?<!\w)" + re.escape(term) + r"(?!\w)"
        compiled.append((term, re.compile(pattern)))
    return compiled


@dataclass
class Match:
    war_terms: set[str] = field(default_factory=set)
    tags: set[str] = field(default_factory=set)
    urgent: bool = False
    standalone: frozenset = frozenset()
    weak: frozenset = frozenset()

    @property
    def relevant(self) -> bool:
        if self.tags & self.standalone:
            return True
        if not self.war_terms:
            return False
        return bool(self.tags - self.weak) or len(self.war_terms) >= 2

    @property
    def score(self) -> int:
        return 2 * len(self.war_terms) + len(self.tags)


class Keywords:
    def __init__(self, config: dict):
        self.war = _compile(config["war_terms"])
        self.tags = {tag: _compile(terms) for tag, terms in config["tags"].items()}
        self.boost: dict[str, float] = config.get("boost", {})
        self.urgent = _compile(config.get("urgent_terms", []))
        self.topics = {name: _compile(terms) for name, terms in (config.get("topics") or {}).items()}
        self.standalone = frozenset(config.get("standalone_tags", []))
        self.weak = frozenset(config.get("weak_tags", []))
        # Fora do Relógio do Argos: não são frentes de guerra.
        self.not_war = self.standalone | self.weak

    @classmethod
    def load(cls, path: Path) -> "Keywords":
        return cls(yaml.safe_load(path.read_text(encoding="utf-8")))

    def match(self, title: str, summary: str) -> Match:
        raw = f"{title}\n{summary}"
        text = normalize_rtl(raw.lower())
        m = Match(standalone=self.standalone, weak=self.weak)
        for term, rx in self.war:
            if rx.search(text):
                m.war_terms.add(term.rstrip("*"))
        for tag, terms in self.tags.items():
            if any(rx.search(raw if t.startswith("!") else text) for t, rx in terms):
                m.tags.add(tag)
        lowered_title = normalize_rtl(title.lower())
        m.urgent = any(rx.search(lowered_title) for _, rx in self.urgent)
        return m

    def topics_for(self, text: str) -> set[str]:
        """Temas citados no texto (título, resumo, títulos dos veículos)."""
        low = normalize_rtl(text.lower())
        return {name for name, terms in self.topics.items() if any(rx.search(low) for _, rx in terms)}

    def boost_for(self, tags) -> float:
        return max((self.boost.get(t, 1.0) for t in tags), default=1.0)
