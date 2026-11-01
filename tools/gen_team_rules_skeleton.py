#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成小队规则 JSON 骨架（app/src/main/assets/rules/<TEAM_ID>.json）。

用法：python3 -I tools/gen_team_rules_skeleton.py <小队中文名或 TEAM_ID> [--force]

条目来自 docs/<小队>-卡片提取/manifest.json 的卡片清单（阵营规则 / 战略计谋 / 交战计谋 /
阵营装备 / 小队选择 / 标识指南），标题取卡图文件名；正文取 docs/<小队>-完整规则资料.md 中对应
小节的“中文全文”，并把 PDF 换行合并成段落。找不到小节时正文留空，需人工按卡图补全。
跨两张卡的同一规则（文件名以 -1/-2 结尾）合并为一个条目。

产出带 "draft": true，必须逐条对照卡图校对、并入勘误（资料第 13 节）后删除 draft，
再运行 tools/check_team_rules.py。已存在的 JSON 不覆盖（除非 --force）。
"""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import team_registry  # noqa: E402

CATEGORY = {
    "faction-rule": "阵营完整规则",
    "strategic-ploy": "战略计谋",
    "firefight-ploy": "交战计谋",
    "faction-equipment": "阵营装备",
    "team-selection": "编成与武器选择",
}
DOC_SECTION = {"阵营完整规则": "阵营规则", "战略计谋": "战略计谋", "交战计谋": "交战计谋", "阵营装备": "阵营装备"}


def find_team(key):
    for t in team_registry.load_teams(ROOT):
        if key in (t["team_id"], t["name_zh"]):
            return t
    raise SystemExit(f"未找到小队：{key}")


def clean(text):
    lines = [l.rstrip() for l in text.strip("\n").split("\n")]
    out = []
    for l in lines:
        s = l.strip()
        if not s:
            out.append("")
            continue
        table = lambda x: bool(re.search(r"\s{3,}|^名称|攻击\s*命中|^武器规则", x))  # noqa: E731
        if table(l) or (out and out[-1] and table(out[-1])):
            out.append(re.sub(r"\s{2,}", "  ", s))
            continue
        if out and out[-1] and not re.match(r"^[•▶◆\-·○\d]", s) and not out[-1].endswith(("：", ":", "。", "）", ")")):
            out[-1] = out[-1] + s
        else:
            out.append(s)
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(out))
    text = "\n".join(l if "  " in l else re.sub(r"(?<=[一-鿿])\s+(?=[一-鿿])", "", l) for l in text.split("\n"))
    return text.replace("\u0007", "").strip()


def doc_bodies(path):
    """{(category, title-key): body} from the '中文全文' blocks of the rules doc."""
    if not os.path.exists(path):
        return {}
    doc = open(path, encoding="utf-8").read()
    found = {}
    for sec in re.split(r"\n## ", doc):
        head = sec.split("\n", 1)[0]
        cat = next((c for c, h in DOC_SECTION.items() if h in head), None)
        if not cat:
            continue
        for block in re.split(r"\n### ", sec)[1:]:
            title = re.sub(r"^\d+\.\s*", "", block.split("\n", 1)[0].strip())
            m = re.search(r"\*\*中文全文\*\*\s*\n(.*?)(?:\n\*\*English full text\*\*|\Z)", block, re.S)
            if m:
                found[(cat, key(title))] = clean(m.group(1))
    return found


def key(title):
    title = re.sub(r"[（(][^）)]*[A-Za-z][^）)]*[）)]", "", title)
    return re.sub(r"[\s，,。、·（）()「」“”\"\-]", "", title)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        raise SystemExit(__doc__)
    team = find_team(args[0])
    tid = team["team_id"]
    out = os.path.join(ROOT, "app", "src", "main", "assets", "rules", f"{tid}.json")
    if os.path.exists(out) and "--force" not in sys.argv:
        raise SystemExit(f"{out} 已存在；如需重建请加 --force")
    manifest = json.load(open(os.path.join(team["card_dir"], "manifest.json"), encoding="utf-8"))
    bodies = doc_bodies(team["doc_path"])
    entries, by_title = [], {}
    for c in manifest["cards"]:
        cat = CATEGORY.get(c["category"])
        if c["category"] == "other" and "指示物" in c["title_zh"]:
            cat = "标识指南"
        if not cat:
            continue
        title = re.sub(r"-\d+$", "", c["title_zh"])
        image = f"{tid}--{c['image']}"
        if (cat, title) in by_title:
            by_title[(cat, title)]["cards"].append(image)
            continue
        body = bodies.get((cat, key(title)), "")
        if not body and cat in ("编成与武器选择", "标识指南"):
            body = c.get("text", "").strip()
        e = {"id": f"{tid}-{len(entries) + 1:02d}", "category": cat, "title": title, "body": body, "cards": [image]}
        by_title[(cat, title)] = e
        entries.append(e)
    data = {"teamId": tid, "draft": True,
            "version": f"{team['official_date']} 官方中文 PDF（{os.path.basename(team['zh_pdf'])}）；正文按卡面原文",
            "entries": entries}
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=1)
    empty = [e["title"] for e in entries if not e["body"]]
    print(f"{tid} {team['name_zh']}：条目 {len(entries)}" + (f"；正文待补：{empty}" if empty else ""))


if __name__ == "__main__":
    main()
