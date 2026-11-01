#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""只读校验：数据驱动小队 JSON（app/src/main/assets/teams/<TEAM_ID>.json）。

用法：python3 -I tools/check_team_data.py <TEAM_ID> [<TEAM_ID> ...]
      python3 -I tools/check_team_data.py --all     # 校验 assets/teams/ 下全部 JSON

检查项（见 docs/小队数据JSON格式.md “校验”一节）：
  1. 结构：必填字段、类型；特工 id / 武器 id 在小队内唯一；kind 只能是 ranged / melee；
     defaultWeaponIds 都指向本特工武器；能力 ap 为整数或 null。
  2. 每张特工数据卡（manifest category=operative）都有对应 operative（按卡名匹配 name）。
  3. 属性（APL/移动/豁免/耐伤）与 manifest 卡面文字层一致。
  4. 每件武器：名称出现在卡面文字层；攻击/命中/伤害与卡面同一行一致；
     manifest weapon_types（若有）记录的远程/近战与 kind 一致。
  5. 所有 profile 能按 `攻击A · 命中+ · 普通/暴击[ · 规则、规则]` 解析。
  6. 武器规则里的每个规则名：要么是核心规则（core_rules.json weaponRules），
     要么在本队 weaponRules 中有定义；卡面带 * 的规则必须在 weaponRules 中定义。
  7. cards 指向的文件存在于 app/src/main/assets/team_rules/，且以 <TEAM_ID>-- 开头；
     manifest 中每张特工能力卡都被某个 operative 引用。
任何问题退出码为 1。
"""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
TEAMS_DIR = os.path.join(ASSETS, "teams")
CARDS_DIR = os.path.join(ASSETS, "team_rules")

PROFILE_RE = re.compile(r"^(?P<a>\d+)A · (?P<hit>[2-6])\+ · (?P<n>\d+)/(?P<c>\d+)(?: · (?P<rules>.+))?$")
# 规则名称：去掉数值 / 括注 / 距离，如“爆炸 2\"”→“爆炸”，“重型（仅限转移）”→“重型”，“致命 5+”→“致命”
RULE_NAME_RE = re.compile(r"^([^\d\s（(\"]+)")


def load_registry():
    try:
        import team_registry  # noqa: WPS433
        return {t["team_id"]: t for t in team_registry.load_teams(ROOT)}
    except Exception as exc:  # registry 缺失时仍可校验结构
        print(f"[警告] 无法读取 team_registry：{exc}")
        return {}


def core_rule_names():
    with open(os.path.join(ASSETS, "core_rules.json"), encoding="utf-8") as fh:
        data = json.load(fh)
    names = {r["keyword"] for r in data.get("weaponRules", [])}
    names.add("灵能")  # 核心规则书的灵能（PSYCHIC）标签，APP RuleGlossary 内置
    # Core weapon rules whose names core_rules.json keys under a shorter keyword
    names.update({"关键穿刺", "穿刺暴击", "眩晕", "均衡", "平衡", "追踪轻型", "距离"})
    return names


def norm(s):
    return re.sub(r"\s+", "", s).replace("(", "（").replace(")", "）")


def rule_name(token):
    token = token.strip().rstrip("*")
    # “2" 毁灭 1” — 带距离前缀的核心规则（毁灭 x 的范围版）
    token = re.sub(r'^\d+\s*["″”]\s*', "", token)
    m = RULE_NAME_RE.match(token)
    return m.group(1) if m else token


def check_team(team_id, registry, core_rules):
    problems = []
    path = os.path.join(TEAMS_DIR, f"{team_id}.json")
    if not os.path.exists(path):
        return [f"缺少 teams/{team_id}.json"], ""
    try:
        with open(path, encoding="utf-8") as fh:
            team = json.load(fh)
    except ValueError as exc:
        return [f"JSON 解析失败：{exc}"], ""

    for key in ("teamId", "name", "operatives"):
        if key not in team:
            problems.append(f"缺少顶层字段 {key}")
    if team.get("teamId") != team_id:
        problems.append(f"teamId={team.get('teamId')!r} 与文件名不一致")
    if registry and team_id not in registry:
        problems.append("teamId 不在 tools/team_registry.py 登记表中")

    # manifest
    manifest_cards = []
    reg = registry.get(team_id)
    if reg:
        mpath = os.path.join(reg["card_dir"], "manifest.json")
        if os.path.exists(mpath):
            with open(mpath, encoding="utf-8") as fh:
                manifest_cards = json.load(fh).get("cards", [])
        else:
            problems.append(f"找不到 manifest：{os.path.relpath(mpath, ROOT)}")

    def card_title(c):
        return c.get("title_zh", "").rsplit("-", 1)[0]

    datacards = {card_title(c): c for c in manifest_cards if c.get("category") == "operative"}
    ability_cards = [c for c in manifest_cards if c.get("category") == "operative-ability"]

    team_rule_defs = {r.get("name", ""): r for r in team.get("weaponRules", [])}
    for name, r in team_rule_defs.items():
        if not str(r.get("text", "")).strip():
            problems.append(f"weaponRules「{name}」text 为空")
        if name.endswith("*"):
            problems.append(f"weaponRules「{name}」名称不应带星号")

    op_ids, weapon_ids, all_cards = set(), set(), []
    used_team_rules = set()
    weapons_total = abilities_total = 0
    for o in team.get("operatives", []):
        oname = o.get("name", "?")
        tag = f"特工「{oname}」"
        for key, typ in (("id", str), ("name", str), ("role", str), ("apl", int), ("move", str),
                         ("save", str), ("wounds", int), ("weapons", list), ("cards", list)):
            if not isinstance(o.get(key), typ):
                problems.append(f"{tag} 字段 {key} 缺失或类型错误")
        if o.get("id") in op_ids:
            problems.append(f"{tag} id 重复：{o.get('id')}")
        op_ids.add(o.get("id"))

        card = datacards.get(oname)
        if card is None and o.get("verifiedAgainstImage"):
            # Name or numbers differ from the text layer; a proofreader confirmed them on the card image.
            card = next((c for c in manifest_cards if c.get("category") == "operative"
                         and any(x.endswith(c.get("image", "\0")) for x in o.get("cards", []))), None)
            if card is None and not o.get("weapons"):
                # Operatives without a datacard (drones, skulls) print their stats on an ability card.
                card = next((c for c in manifest_cards if c.get("category") == "operative-ability"
                             and any(x.endswith(c.get("image", "\0")) for x in o.get("cards", []))), None)
        verified = bool(o.get("verifiedAgainstImage"))
        text = norm(card.get("text", "")) if card else ""
        if manifest_cards and not card:
            problems.append(f"{tag} 在 manifest 中没有同名数据卡")
        if card:
            stats = f"{o.get('apl')}{o.get('move')}{o.get('save')}{o.get('wounds')}"
            if norm(stats) not in text and not verified:
                problems.append(f"{tag} 属性 APL/移动/豁免/耐伤={stats} 与卡面文字层不一致")

        my_weapons = set()
        weapon_types = (card or {}).get("weapon_types") or {}
        for w in o.get("weapons", []):
            weapons_total += 1
            wtag = f"{tag} 武器「{w.get('name')}」"
            if w.get("id") in weapon_ids:
                problems.append(f"{wtag} id 重复：{w.get('id')}")
            weapon_ids.add(w.get("id"))
            my_weapons.add(w.get("id"))
            if w.get("kind") not in ("ranged", "melee"):
                problems.append(f"{wtag} kind 必须是 ranged/melee")
            m = PROFILE_RE.match(w.get("profile", ""))
            if not m:
                problems.append(f"{wtag} profile 无法解析：{w.get('profile')!r}")
                continue
            if card:
                row = norm(f"{w['name']}{m['a']}{m['hit']}+{m['n']}/{m['c']}")
                if row not in text and not o.get("verifiedAgainstImage"):
                    problems.append(f"{wtag} 名称/攻击/命中/伤害与卡面文字层不一致（{w['profile']}）")
            declared = weapon_types.get(w.get("name"))
            expected = {"ranged": "远程", "melee": "近战"}.get(w.get("kind"))
            if declared and declared != expected:
                problems.append(f"{wtag} kind={w.get('kind')} 与 manifest weapon_types={declared} 不一致")
            for token in (m["rules"] or "").split("、"):
                if not token.strip():
                    continue
                if token.strip().endswith("*"):
                    problems.append(f"{wtag} 规则「{token}」应去掉星号，定义放 weaponRules")
                name = rule_name(token)
                if name in team_rule_defs:
                    used_team_rules.add(name)
                elif name not in core_rules:
                    problems.append(f"{wtag} 规则「{name}」既不是核心规则，也不在 weaponRules 中定义")
            if card and m["rules"]:
                # Look for "规则*" only on the card line of this weapon; a footnote on the next
                # line that starts with "*" would otherwise glue onto the last rule.
                wline = next((norm(l) for l in card.get("text", "").split("\n") if norm(w["name"]) in norm(l)), "")
                for token in m["rules"].split("、"):
                    if norm(token.strip() + "*") in wline and rule_name(token) not in team_rule_defs:
                        problems.append(f"{wtag} 卡面带 * 的规则「{token}」未在 weaponRules 定义")
        for d in o.get("defaultWeaponIds", []):
            if d not in my_weapons:
                problems.append(f"{tag} defaultWeaponIds 中的 {d} 不是本特工的武器")

        for a in o.get("abilities", []):
            abilities_total += 1
            if not str(a.get("name", "")).strip() or not str(a.get("text", "")).strip():
                problems.append(f"{tag} 能力缺少 name/text")
            ap = a.get("ap", "missing")
            if ap is not None and not isinstance(ap, int):
                problems.append(f"{tag} 能力「{a.get('name')}」ap 必须是整数或 null")
            if card and not verified and not a.get("factionRule") and norm(a.get("name", "")) not in text and not any(
                    norm(a.get("name", "")) in norm(c.get("text", "")) for c in ability_cards):
                problems.append(f"{tag} 能力「{a.get('name')}」名称在卡面文字层中找不到")

        for c in o.get("cards", []):
            all_cards.append(c)
            if not c.startswith(f"{team_id}--"):
                problems.append(f"{tag} 卡图 {c} 未以 {team_id}-- 开头")
            if not any(os.path.exists(os.path.join(CARDS_DIR, os.path.splitext(c)[0] + ext)) for ext in (".png", ".webp")):
                problems.append(f"{tag} 卡图不存在：team_rules/{c}")

    # A datacard is covered when an operative has its name or references its image
    # (several cards may share a short title such as “XV26”).
    referenced = {os.path.splitext(c.split("--", 1)[-1])[0] for o in team.get("operatives", []) for c in o.get("cards", [])}
    names = {o.get("name") for o in team.get("operatives", [])}
    for c in manifest_cards:
        if c.get("category") != "operative" or c.get("is_back"):
            continue
        if os.path.splitext(c.get("image", ""))[0] not in referenced and card_title(c) not in names:
            problems.append(f"manifest 数据卡 {c.get('image')} 没有对应 operative")
    for c in ability_cards:
        suffix = c["image"].split("-", 2)[2]
        if not any(x.endswith(suffix) for x in all_cards):
            problems.append(f"manifest 能力卡 {c['image']} 未被任何 operative 的 cards 引用")
    for name in team_rule_defs:
        if name not in used_team_rules:
            problems.append(f"weaponRules「{name}」未被任何武器引用")

    summary = (f"特工 {len(team.get('operatives', []))}、武器档案 {weapons_total}、"
               f"能力/独特行动 {abilities_total}、专属武器规则 {len(team_rule_defs)}、卡图 {len(all_cards)}")
    return problems, summary


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    if argv == ["--all"]:
        argv = sorted(f[:-5] for f in os.listdir(TEAMS_DIR) if f.endswith(".json")) if os.path.isdir(TEAMS_DIR) else []
    registry = load_registry()
    core_rules = core_rule_names()
    failed = False
    drafts = []
    for team_id in argv:
        problems, summary = check_team(team_id, registry, core_rules)
        if problems:
            failed = True
            print(f"[失败] {team_id}  {summary}")
            for p in problems:
                print(f"    - {p}")
        else:
            print(f"[通过] {team_id}  {summary}")
        # Draft skeletons are not loaded by the app; report them so a pass is not read as “done”.
        try:
            with open(os.path.join(TEAMS_DIR, f"{team_id}.json"), encoding="utf-8") as fh:
                data = json.load(fh)
        except (OSError, ValueError):
            data = {}
        if data.get("draft"):
            drafts.append(team_id)
            print("    · 草稿（draft），尚未校对，APP 不加载")
        elif data and not any(o.get("abilities") for o in data.get("operatives", [])):
            print("    · 警告：全队没有任何能力条目，请确认是否漏转录")
    if drafts:
        print(f"草稿 {len(drafts)} 支：{' '.join(drafts)}")
    print("结果：", "存在问题" if failed else "全部通过")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
