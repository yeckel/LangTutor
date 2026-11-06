#!/usr/bin/env python3
"""Build tiered vocabulary assets for the Tutor app.

Downloads open frequency lists (OpenSubtitles top-50k per language, via the
hermitdave/FrequencyWords repository) and writes cumulative JSON tiers used
as the learner's "known words":
    app/src/main/assets/vocab/<lang>/<LEVEL>.json

Tiers (Duolingo-course-like progression):
    A0 = top 300    A1 = top 900    A2 = top 2000    B1 = top 4000

Filtering keeps lowercase alphabetic tokens (including accented letters),
drops proper nouns and very long junk tokens. A natural next step is to
overlay community-extracted Duolingo course decks onto these tiers
(marked TODO below), because Duolingo does not publish official vocab.

Usage:  python3 tools/build_vocab.py            # es en de fr it
        python3 tools/build_vocab.py es de      # specific languages
"""
import json
import sys
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ASSETS = REPO_ROOT / "app" / "src" / "main" / "assets" / "vocab"

TIERS = {"A0": 300, "A1": 900, "A2": 2000, "B1": 4000}

# URL patterns tried in order (the repo mixes layouts across languages).
URL_PATTERNS = [
    "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/{lang}/{lang}_50k.txt",
    "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/{lang}/{lang}_50k.txt",
    "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2016/{lang}/{lang}_50k.txt",
]

DEFAULT_LANGUAGES = ["es", "en", "de", "fr", "it"]


def fetch(lang: str) -> str:
    for pattern in URL_PATTERNS:
        url = pattern.format(lang=lang)
        try:
            with urllib.request.urlopen(url, timeout=30) as resp:
                data = resp.read().decode("utf-8")
            if data.strip():
                return data
        except Exception as e:  # noqa: BLE001 - probe each pattern in turn
            print(f"  miss {url.rsplit('/', 1)[-1]} ({e.__class__.__name__})")
    raise RuntimeError(f"no frequency list found for language {lang!r}")


def clean_tokens(raw: str) -> list[str]:
    allowed_extra = set("áàâäéèêëíìîïóòôöúùûüñçß")
    tokens: list[str] = []
    seen: set[str] = set()
    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        token = line.split(" ")[0].lower().strip()
        if not token.isalpha():
            continue
        if len(token) > 18:
            continue
        if any(ch.isupper() for ch in line.split(" ")[0]):
            continue  # drop proper nouns and acronyms
        if token in seen:
            continue
        # keep ascii letters and common latin accented letters only
        if not all(ch.isascii() and ch.islower() or ch in allowed_extra for ch in token):
            continue
        seen.add(token)
        tokens.append(token)
    return tokens


def write_tiers(lang: str, tokens: list[str]) -> list[Path]:
    out_paths = []
    for level, count in TIERS.items():
        words = tokens[:count]
        out = ASSETS / lang / f"{level}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        payload = {"level": level, "count": len(words), "words": words}
        out.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        out_paths.append(out)
    return out_paths


def main() -> None:
    languages = sys.argv[1:] or DEFAULT_LANGUAGES
    for lang in languages:
        print(f"[{lang}] downloading frequency list…")
        raw = fetch(lang)
        tokens = clean_tokens(raw)
        if len(tokens) < TIERS["B1"]:
            print(f"  warning: only {len(tokens)} usable tokens for {lang}")
        paths = write_tiers(lang, tokens)
        print(f"  {len(tokens)} tokens total; wrote {len(paths)} tiers:")
        for level, count in TIERS.items():
            print(f"    {level}: {min(count, len(tokens))} words")


if __name__ == "__main__":
    main()
