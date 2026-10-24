"""Extract verbatim core-rule passages from the v11.5 full-text Markdown into an app asset.

Usage: python3 -I tools/extract_core_rules.py <full-text.md> <out.json>

Text is copied verbatim (only Markdown page scaffolding is removed), so the app can show
the original rule wording with its PDF page numbers. Never summarise here.
"""
import json
import re
import sys

PAGES = {
    "turn_sequence": ("战略阶段与交战阶段", [8, 9]),
    "shooting": ("射击行动", [15, 16]),
    "melee": ("近战行动", [17, 18]),
    "wounds": ("耐伤、受创与残废", [21]),
    "obscured": ("遮挡", [26, 27]),
    "ploys": ("计谋与指挥重掷", [28]),
    "vantage": ("制高点", [36]),
    "weapon_rules": ("武器规则", [92, 93, 94, 95]),
    "movable_cover": ("可移动掩体", [102]),
    "smoke": ("烟雾手雷", [103]),
    "initiative": ("决定先手权与转折点", [106]),
    "kill_op": ("结束战斗与击杀行动", [107]),
}

# Weapon keyword -> heading text at the start of its entry on pages 92-95.
WEAPON_KEYWORDS = [
    "精准", "平衡", "爆炸", "残暴", "无休", "毁灭", "重型", "过热", "致命", "有限",
    "穿刺", "重击", "范围", "毫不留情", "撕裂", "集中", "追踪", "严重", "震荡", "安静",
    "晕眩", "洪流",
]


def page_lines(lines, page):
    anchor = f'<a id="pdf-{page:03d}"></a>'
    nxt = f'<a id="pdf-{page + 1:03d}"></a>'
    start = lines.index(anchor)
    end = lines.index(nxt) if nxt in lines else len(lines)
    out = []
    for raw in lines[start:end]:
        s = raw.rstrip()
        if s.startswith("```") or s.startswith("<a id") or s.startswith("## PDF"):
            continue
        if re.fullmatch(r"\d{1,3}", s.strip()):
            continue
        out.append(s)
    return out


def clean(block):
    text = "\n".join(block)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def main(src, dst):
    with open(src, encoding="utf-8") as fh:
        lines = fh.read().split("\n")
    topics = []
    for key, (title, pages) in PAGES.items():
        block = []
        for p in pages:
            block += page_lines(lines, p)
        topics.append({"id": key, "title": title, "pages": pages, "text": clean(block)})

    weapon_block = []
    for p in PAGES["weapon_rules"][1]:
        weapon_block += page_lines(lines, p)
    heading = re.compile(r"^(" + "|".join(sorted(WEAPON_KEYWORDS, key=len, reverse=True)) + r")[ x+]*[：:]")
    entries = {}
    current = None
    for line in weapon_block:
        m = heading.match(line)
        if m and m.group(1) not in entries:
            current = m.group(1)
            entries[current] = []
        if current:
            entries[current].append(line)
    keywords = [{"keyword": k, "pages": PAGES["weapon_rules"][1], "text": clean(v)} for k, v in entries.items()]
    missing = [k for k in WEAPON_KEYWORDS if k not in entries]
    if missing:
        print("missing weapon keywords:", missing, file=sys.stderr)
    with open(dst, "w", encoding="utf-8") as fh:
        json.dump({"source": "杀戮小队 三版 官中修订版 v11.5", "topics": topics, "weaponRules": keywords},
                  fh, ensure_ascii=False, indent=1)
    print(f"{len(topics)} topics, {len(keywords)} weapon rules -> {dst}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
