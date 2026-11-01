#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成数据驱动小队的骨架 JSON（app/src/main/assets/teams/<TEAM_ID>.json），并复制卡图。

用法：python3 -I tools/gen_team_skeleton.py <小队中文名或 TEAM_ID> [--force]

从 docs/<小队>-卡片提取/manifest.json 的卡面文字层机械提取：
  * 每张特工数据卡 → operative：名称、APL/移动/豁免/耐伤、武器档案（攻击/命中/伤害/规则）、
    远程/近战（manifest weapon_types）、卡面关键字行、数据卡与能力卡文件名。
  * 武器规则里带 * 的名称 → weaponRules 占位（text 为空，需人工按卡图补全）。
  * abilities 留空列表，需人工按卡图转录（两栏混排的文字层不可直接使用）。
同时把该队全部卡图复制到 app/src/main/assets/team_rules/<TEAM_ID>--<原文件名>。

产出是“骨架”：数值来自文字层，必须再用 tools/check_team_data.py 校验，并逐张对照卡图补全
abilities、weaponRules、role、archetypes。已存在的 JSON 不会被覆盖（除非 --force）。
"""
import json
import os
import re
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import team_registry  # noqa: E402

ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
STAT = re.compile(r"^\s*(\d)\s+(\d+)\s*[\"″”]\s+(\d)\s*\+\s+(\d+)\s*$")
WEAPON = re.compile(r"^\s{0,8}(\S.*?)\s+(\d+)\s+(\d)\s*\+\s+(\d+)\s*/\s*(\d+)(?:\s+(.*))?$")


def find_team(key):
    for t in team_registry.load_teams(ROOT):
        if key in (t["team_id"], t["name_zh"]):
            return t
    raise SystemExit(f"未找到小队：{key}")


def slug(team_id):
    return team_id.lower().replace("kt-", "").replace("-", "_")


def clean_rules(text):
    text = text.replace("\u0007", "").strip()
    text = re.split(r"\s{3,}", text)[0].strip()
    if text in ("", "-", "—"):
        return []
    parts = [p.strip() for p in re.split(r"[、，,]", text) if p.strip()]
    return [re.sub(r"\s+", " ", p).replace("”", "\"").replace("″", "\"") for p in parts]


def parse_card(card):
    text = re.sub(r"(\d)\s*\n\s*\+\s+(\d+)", r"\1+   \2", card["text"])
    lines = text.split("\n")
    stats = None
    weapons = []
    for line in lines:
        if stats is None:
            m = STAT.match(line)
            if m:
                stats = m.groups()
                continue
        m = WEAPON.match(line)
        if m and stats:
            name = re.sub(r"\s+", "", m.group(1))
            if name in ("名称",):
                continue
            weapons.append(dict(name=name, a=int(m.group(2)), hit=m.group(3), dmg=f"{m.group(4)}/{m.group(5)}",
                                rules=clean_rules(m.group(6) or "")))
    keyword_line = ""
    base_size_mm = None
    for line in reversed(lines):
        s = line.strip()
        m_kw = re.search(r"^(.*、.*?)\s+(\d+)\s*$", s)
        if m_kw:
            keyword_line = m_kw.group(1)
            base_size_mm = int(m_kw.group(2))
            break
    return stats, weapons, [k.strip() for k in keyword_line.replace(" ", "").split("、") if k.strip()], base_size_mm


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        raise SystemExit(__doc__)
    team = find_team(args[0])
    tid = team["team_id"]
    out = os.path.join(ASSETS, "teams", f"{tid}.json")
    if os.path.exists(out) and "--force" not in sys.argv:
        raise SystemExit(f"{out} 已存在；如需重建请加 --force")
    manifest = json.load(open(os.path.join(team["card_dir"], "manifest.json"), encoding="utf-8"))
    cards = manifest["cards"]
    # copy every card image with the team prefix
    os.makedirs(os.path.join(ASSETS, "team_rules"), exist_ok=True)
    cwebp = shutil.which("cwebp") or ("/opt/homebrew/bin/cwebp" if os.path.exists("/opt/homebrew/bin/cwebp") else None)
    for c in cards:
        src = os.path.join(team["card_dir"], c["image"])
        dst_png = os.path.join(ASSETS, "team_rules", f"{tid}--{c['image']}")
        dst_webp = re.sub(r"\.png$", ".webp", dst_png)
        if os.path.exists(src) and (not os.path.exists(dst_png) and not os.path.exists(dst_webp)):
            if cwebp and dst_webp.endswith(".webp"):
                import subprocess
                subprocess.run([cwebp, "-quiet", "-q", "82", src, "-o", dst_webp], check=True)
            else:
                shutil.copyfile(src, dst_png)

    s = slug(tid)
    operatives = []
    weapon_rules = {}
    pending_ability_cards = []
    seen = {}
    for c in cards:
        if c["category"] == "operative-ability" and operatives:
            operatives[-1]["cards"].append(f"{tid}--{c['image']}")
            continue
        if c["category"] != "operative" or c.get("is_back"):
            continue
        name = c["title_zh"].rsplit("-", 1)[0]
        stats, weapons, keywords, base_size_mm = parse_card(c)
        types = c.get("weapon_types") or {}
        base = f"{s}_{len(operatives) + 1:02d}"
        ws = []
        for i, w in enumerate(weapons, 1):
            kind = "melee" if types.get(w["name"]) == "近战" else "ranged"
            for r in w["rules"]:
                if r.endswith(("*", "¹")):
                    rbase = re.sub(r"\s*\d.*$", "", r.rstrip("*¹")).strip()
                    weapon_rules.setdefault(rbase, "")
            rules = [r.rstrip("*¹") for r in w["rules"]]
            ws.append({"id": f"{base}_w{i:02d}", "name": w["name"], "kind": kind,
                       "profile": f"{w['a']}A · {w['hit']}+ · {w['dmg']}" + (f" · {'、'.join(rules)}" if rules else "")})
        key = name
        seen[key] = seen.get(key, 0) + 1
        operatives.append({
            "id": base,
            "name": name if seen[key] == 1 else f"{name}（{seen[key]}）",
            "role": "",
            "apl": int(stats[0]) if stats else 0,
            "move": f"{stats[1]}\"" if stats else "",
            "save": f"{stats[2]}+" if stats else "",
            "wounds": int(stats[3]) if stats else 0,
            "baseSizeMm": base_size_mm,
            "keywords": keywords,
            "cards": [f"{tid}--{c['image']}"],
            "weapons": ws,
            "defaultWeaponIds": [w["id"] for w in ws],
            "abilities": [],
        })
    all_text = "\n\n".join(c.get("text", "") for c in cards)
    for rname in list(weapon_rules.keys()):
        pat = re.compile(
            r"[*¹1]\s*" + re.escape(rname) + r"[^：:\n]*[：:]\s*(.*?)(?=\n\S+?[：:]|\n\S+?\s+\d\s*AP|\n[^\n]*、[^\n]*\d+\s*$|\n\n|\Z)",
            re.S,
        )
        m_wr = pat.search(all_text)
        if m_wr:
            weapon_rules[rname] = re.sub(r"\s*\n\s*", "", m_wr.group(1).strip())
    data = {
        "teamId": tid,
        "draft": True,
        "name": team["name_zh"],
        "subtitle": "",
        "archetypes": [],
        "source": f"官方中文 PDF {team['official_date']}",
        "operatives": operatives,
        "weaponRules": [{"name": n, "text": t} for n, t in weapon_rules.items()],
    }
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=1)
    missing = [o["name"] for o in operatives if not o["weapons"] or not o["apl"]]
    print(f"{tid} {team['name_zh']}：特工 {len(operatives)}，武器 {sum(len(o['weapons']) for o in operatives)}，"
          f"专属武器规则占位 {len(weapon_rules)}，卡图 {len(cards)}" + (f"；需人工补数值：{missing}" if missing else ""))


if __name__ == "__main__":
    main()
