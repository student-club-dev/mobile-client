#!/usr/bin/env python3
"""Fluent Emoji 3D jadvalini qayta generatsiya qiladi.

    python3 dev/tools/fluent-emoji/generate.py [--sha <commit>]

Natija — `FluentEmojiAssets.kt`. Uni **qo'lda tahrirlamang**, shu skriptni qayta ishga
tushiring.

Nega bunday qurilgan:

* **Glif bo'yicha birlashtiriladi, nom bo'yicha emas.** CLDR nomlari Unicode chiqishlari
  orasida o'zgaradi — 🤗 Fluent'da "Hugging face", yangi Unicode'da esa "smiling face with
  open hands". Nom bo'yicha moslasak, bunday emojilar jimgina yo'qolardi (birinchi urinishda
  1914 tadan 370 tasi yo'qolgan edi). Har bir papkaning `metadata.json` idagi `glyph` — aniq
  manba.
* **Fayl nomi qoidasi tekshiriladi.** URL ish vaqtida papka nomidan yig'iladi
  (kichik harf + bo'sh joy → `_`, chiziqcha saqlanadi). Qoidaga mos kelmagan papka jadvalga
  **kiritilmaydi**, ya'ni 404 beradigan URL hosil bo'lishi mumkin emas.
* **Commit'ga qadaladi.** `@main` bilan CDN ertaga jimgina boshqa rasm bera boshlardi.

Litsenziya: MIT — tijoriy ishlatishga ruxsat beradi va atribut talab qilmaydi.
"""

from __future__ import annotations  # macOS'dagi tizim Python'i 3.10 dan eski bo'lishi mumkin

import argparse
import collections
import json
import pathlib
import re
import sys
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

REPO = "microsoft/fluentui-emoji"
EMOJI_TEST = "https://unicode.org/Public/emoji/latest/emoji-test.txt"
OUT = (
    pathlib.Path(__file__).resolve().parents[2]  # .../StudentClubs/dev
    / "feature/chat/domain/src/commonMain/kotlin/dev/feature/chat/domain/model/FluentEmojiAssets.kt"
)

# Unicode guruhi -> generatsiya qilinadigan konstanta nomi. `Flags` ataylab yo'q: Fluent'da
# davlat bayroqlari umuman chizilmagan (faqat "Black flag" kabi belgilar bor).
GROUPS = [
    ("Smileys & Emotion", "SMILEYS"),
    ("People & Body", "PEOPLE"),
    ("Animals & Nature", "NATURE"),
    ("Food & Drink", "FOOD"),
    ("Activities", "ACTIVITIES"),
    ("Travel & Places", "TRAVEL"),
    ("Objects", "OBJECTS"),
    ("Symbols", "SYMBOLS"),
]

EMOJI_TEST_LINE = re.compile(r"^([0-9A-F ]+);\s*(\S+)\s*#\s*(\S+)\s+E[\d.]+\s+(.+)$")


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


def slug(name: str) -> str:
    """Papka nomidan fayl nomi — repo'dagi haqiqiy yo'llarga qarab aniqlangan qoida."""
    return name.lower().replace(" ", "_")


def resolve_sha(sha: str | None) -> str:
    if sha:
        return sha
    commit = json.loads(fetch(f"https://api.github.com/repos/{REPO}/commits/main"))
    return commit["sha"]


def usable_folders(sha: str) -> dict[str, bool]:
    """Papka -> teri rangi variantlari bormi. Qoidaga mos kelmaganlari tashlab yuboriladi."""
    tree = json.loads(fetch(f"https://api.github.com/repos/{REPO}/git/trees/{sha}?recursive=1"))
    if tree.get("truncated"):
        sys.exit("repo daraxti qirqilgan — GitHub API javobi to'liq emas")

    folders: dict[str, bool] = {}
    for entry in tree["tree"]:
        path = entry["path"]
        if entry["type"] != "blob" or "/3D/" not in path or not path.endswith(".png"):
            continue
        parts = path.split("/")
        if len(parts) == 4 and parts[3] == f"{slug(parts[1])}_3d.png":
            folders.setdefault(parts[1], False)
        elif len(parts) == 5 and parts[2] == "Default" and parts[4] == f"{slug(parts[1])}_3d_default.png":
            folders.setdefault(parts[1], True)
    return folders


def glyph_to_folder(sha: str, folders: dict[str, bool]) -> dict[str, str]:
    """Har bir papkaning `metadata.json` idan glifni o'qiydi (1595 ta kichik so'rov)."""
    base = f"https://raw.githubusercontent.com/{REPO}/{sha}/assets/"

    def one(folder: str) -> tuple[str, str | None]:
        url = base + urllib.parse.quote(f"{folder}/metadata.json")
        for _ in range(3):
            try:
                return folder, json.loads(fetch(url)).get("glyph")
            except Exception:  # noqa: BLE001 — qayta urinamiz, oxirida None qaytadi
                continue
        return folder, None

    with ThreadPoolExecutor(max_workers=24) as pool:
        results = list(pool.map(one, folders))

    failed = [folder for folder, glyph in results if glyph is None]
    if failed:
        sys.exit(f"{len(failed)} ta papkaning metadata'si o'qilmadi, masalan: {failed[:5]}")

    mapping: dict[str, str] = {}
    for folder, glyph in results:
        mapping.setdefault(glyph, folder)
    return mapping


def catalog(folders: dict[str, bool], by_glyph: dict[str, str]) -> collections.OrderedDict:
    """Unicode tartibida guruhlarga ajratilgan qatorlar."""
    constant_of = dict(GROUPS)
    buckets = collections.OrderedDict((constant, []) for _, constant in GROUPS)
    group, seen = None, set()

    for raw in fetch(EMOJI_TEST).decode("utf-8").splitlines():
        if raw.startswith("# group:"):
            group = raw.split(":", 1)[1].strip()
            continue
        if not raw.strip() or raw.startswith("#"):
            continue
        match = EMOJI_TEST_LINE.match(raw)
        if not match or match.group(2) != "fully-qualified":
            continue
        glyph, name = match.group(3), match.group(4)
        # Teri rangi variantlari alohida emoji sifatida keladi — katalogda bitta,
        # neytral shakli qoladi, aks holda bitta yuz olti marta chiqardi.
        if "skin tone" in name or glyph in seen:
            continue
        folder = by_glyph.get(glyph)
        constant = constant_of.get(group)
        if folder and constant:
            seen.add(glyph)
            buckets[constant].append(f"{glyph}\t{'*' if folders[folder] else ''}{folder}")
    return buckets


def render(sha: str, buckets: collections.OrderedDict) -> str:
    header = f'''package dev.feature.chat.domain.model

/**
 * Microsoft Fluent Emoji 3D — **generatsiya qilingan jadval, qo'lda tahrirlanmasin.**
 *
 * Manba: <https://github.com/{REPO}> (MIT — tijoriy ishlatishga ruxsat,
 * atribut talab qilmaydi). Telegram stikerlaridan farqli o'laroq bularni tarqatish
 * huquqiy jihatdan xavfsiz (`CHAT_MEDIA_AND_CALLS_BACKEND.md` §4.4).
 *
 * Har bir guruh — `"<emoji>\\t<papka>"` qatorlari, Unicode tartibida. Papka oldidagi `*`
 * — emojining teri rangi variantlari bor, ya'ni yo'lda qo'shimcha `Default` bo'g'ini
 * bo'ladi. URL [FluentEmoji] da yig'iladi.
 *
 * Jadval **tekshirilgan**: faqat fayl nomi hosil qilish qoidasiga mos keladigan (va shu
 * sababli URL'i albatta mavjud) papkalar kiritilgan.
 *
 * Yangilash: `dev/tools/fluent-emoji/generate.py`.
 */
internal object FluentEmojiAssets {{

    /** Aynan shu commit'ga qadab qo'yilgan — `@main` ertaga jimgina o'zgarib ketardi. */
    const val CDN: String = "https://cdn.jsdelivr.net/gh/{REPO}@{sha}/assets"
'''
    parts = [header]
    for _, constant in GROUPS:
        parts.append(f'\n    const val {constant}: String = """' + "\n".join(buckets[constant]) + '"""\n')
    parts.append("}\n")
    return "".join(parts)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sha", help="fluentui-emoji commit'i (default: hozirgi main)")
    args = parser.parse_args()

    sha = resolve_sha(args.sha)
    folders = usable_folders(sha)
    by_glyph = glyph_to_folder(sha, folders)
    buckets = catalog(folders, by_glyph)

    total = sum(len(rows) for rows in buckets.values())
    print(f"commit: {sha}")
    print(f"ishlatish mumkin bo'lgan papkalar: {len(folders)}")
    for constant, rows in buckets.items():
        print(f"  {constant:<11} {len(rows):4d}")
    print(f"jami: {total}")

    OUT.write_text(render(sha, buckets), encoding="utf-8")
    print(f"yozildi: {OUT}")


if __name__ == "__main__":
    main()
