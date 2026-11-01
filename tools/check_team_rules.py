#!/usr/bin/env python3
"""只读校验：每支小队的规则卡图条目是否都在 rules/<teamId>.json 中有对应、正文非空的 entry。

用法：python3 -I tools/check_team_rules.py [项目根目录]

校验来源：
- app/src/main/assets/team_rules/<TEAM>--NN-<分类>-<标题>.png
- app/src/main/assets/equipment/<TEAM>--<标题>.png（视为“阵营装备”）
被校验的卡图分类及其对应的 JSON category：
  阵营规则 -> 阵营完整规则
  战略计谋 -> 战略计谋
  交战计谋 -> 交战计谋
  阵营装备 -> 阵营装备
  小队选择 -> 编成与武器选择
匹配规则（任一成立即视为已覆盖）：
  1. 某条 entry 的 cards 含该卡图文件名；
  2. 同 category 的 entry 标题与卡图标题规范化后相等（去括注、去标点、去空格）；
     卡图标题带分页后缀（如“狂暴-1”“战团战术-说明”）时，逐级去掉最后一个“-段”再比较；
  3. 小队选择：存在任一 category 为“编成与武器选择”的 entry。
被匹配到的 entry 的 body 必须非空。任何缺失时退出码为 1。
"""
import json
import os
import re
import sys
import unicodedata

CATEGORY_MAP = {
    "阵营规则": "阵营完整规则",
    "战略计谋": "战略计谋",
    "交战计谋": "交战计谋",
    "阵营装备": "阵营装备",
    "小队选择": "编成与武器选择",
}
REQUIRED_KEYS = ("id", "category", "title", "body", "cards")
# 小队 ID：既有 APP ID（如 CHAOS-PM）或 tools/team_registry.py 生成的 KT-<SLUG>（如 KT-BROOD_BROTHERS）
TEAM_ID_PATTERN = r"[A-Z]+(?:-[A-Z0-9_]+)?"
CARD_RE = re.compile(rf"^(?P<team>{TEAM_ID_PATTERN})--(?P<num>\d+(?:-\d+)?)-(?P<cat>[^-\d][^-]*)-(?P<title>.+)\.(?:png|webp)$")
EQUIP_RE = re.compile(rf"^(?P<team>{TEAM_ID_PATTERN})--(?P<title>.+)\.(?:png|webp)$")


def normalize(text):
    text = re.sub(r"[（(][^）)]*[）)]", "", text)
    return "".join(
        ch for ch in text
        if not ch.isspace() and not unicodedata.category(ch).startswith(("P", "S"))
    )


def candidates(title):
    """标题本身，以及逐级去掉末尾“-段”的前缀。"""
    parts = title.split("-")
    return [normalize("-".join(parts[:i])) for i in range(len(parts), 0, -1)]


def main():
    # Arguments: optional project root (a path) and/or TEAM_IDs to check only those teams.
    args = sys.argv[1:]
    paths = [a for a in args if os.sep in a or a.startswith(".")]
    only = {a for a in args if a not in paths}
    root = os.path.abspath(paths[0] if paths else os.path.join(os.path.dirname(__file__), ".."))
    assets = os.path.join(root, "app", "src", "main", "assets")
    rules_dir = os.path.join(assets, "rules")
    cards_dir = os.path.join(assets, "team_rules")
    equip_dir = os.path.join(assets, "equipment")

    cards = {}  # team -> list[(category, title, filename, source)]
    for name in sorted(os.listdir(cards_dir)):
        m = CARD_RE.match(name)
        if m and m.group("cat") in CATEGORY_MAP:
            cards.setdefault(m.group("team"), []).append((m.group("cat"), m.group("title"), name, "team_rules"))
    if os.path.isdir(equip_dir):
        for name in sorted(os.listdir(equip_dir)):
            m = EQUIP_RE.match(name)
            if not m:
                continue
            team, title = m.group("team"), m.group("title")
            already = any(c == "阵营装备" and normalize(t) == normalize(title) for c, t, _, _ in cards.get(team, []))
            if not already:
                cards.setdefault(team, []).append(("阵营装备", title, name, "equipment"))

    teams = sorted(set(cards) | {f[:-5] for f in os.listdir(rules_dir) if f.endswith(".json")})
    if only:
        teams = [t for t in teams if t in only]
    failed = False
    for team in teams:
        problems = []
        path = os.path.join(rules_dir, f"{team}.json")
        entries = []
        if not os.path.exists(path):
            problems.append(f"缺少 JSON 文件 rules/{team}.json")
        else:
            try:
                with open(path, encoding="utf-8") as fh:
                    data = json.load(fh)
                entries = data.get("entries", [])
                if data.get("teamId", data.get("team_id")) != team:
                    problems.append("JSON 的 teamId 与文件名不一致")
                for i, e in enumerate(entries):
                    missing = [k for k in REQUIRED_KEYS if k not in e]
                    if missing:
                        problems.append(f"entries[{i}] 缺少字段 {missing}")
                    elif not str(e["body"]).strip():
                        problems.append(f"entries[{i}]「{e['title']}」body 为空")
                    else:
                        for c in e.get("cards", []):
                            if not any(os.path.exists(os.path.join(cards_dir, os.path.splitext(c)[0] + ext)) for ext in (".png", ".webp")):
                                problems.append(f"entries[{i}]「{e['title']}」引用的卡图不存在：{c}")
            except (ValueError, OSError) as exc:
                problems.append(f"JSON 解析失败：{exc}")
                entries = []
        entries = [e for e in entries if all(k in e for k in REQUIRED_KEYS)]

        counts = {}
        for e in entries:
            counts[e["category"]] = counts.get(e["category"], 0) + 1

        for cat, title, filename, source in cards.get(team, []):
            json_cat = CATEGORY_MAP[cat]
            stem = os.path.splitext(filename)[0]
            hit = [e for e in entries if any(os.path.splitext(c)[0] == stem for c in e["cards"])]
            if not hit:
                keys = candidates(title)
                same_cat = [e for e in entries if e["category"] == json_cat]
                for key in keys:
                    hit = [e for e in same_cat if normalize(e["title"]) == key]
                    if hit:
                        break
            if not hit:
                # Card files keep the printed title; the 2026-08 text renamed a few items.
                renamed = {"凶刃圣物": "战团圣物", "偏移盾": "偏移肩盾", "纳米甲虫匣": "纳米甲虫闸", "自动鞭笞装置": "自动鞭策装置"}.get(title)
                if renamed:
                    hit = [e for e in entries if e["category"] == json_cat and normalize(e["title"]).startswith(normalize(renamed))]
            if not hit and cat == "小队选择":
                hit = [e for e in entries if e["category"] == json_cat]
            if not hit:
                problems.append(f"缺少条目 [{cat}] {title}（{source}/{filename}）")
            elif not any(str(e["body"]).strip() for e in hit):
                problems.append(f"条目正文为空 [{cat}] {title}（{source}/{filename}）")

        summary = "、".join(f"{k} {v}" for k, v in sorted(counts.items()))
        if problems:
            failed = True
            print(f"[失败] {team}  （{summary or '无条目'}）")
            for p in problems:
                print(f"    - {p}")
        else:
            print(f"[通过] {team}  卡图 {len(cards.get(team, []))} 张已全部对应；{summary}")

    print("结果：", "存在缺失" if failed else "全部通过")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
