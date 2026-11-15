#!/usr/bin/env python3
"""
Builds data/core/glossary.json: one entry per rule keyword / faction ability / ploy with
  - "short": a one-sentence effect (hand-written below; falls back to the first sentence of the source)
  - "full":  the full rule text from core_rules.json / team JSON
  - "kind":  weapon | faction | ability | ploy | tactic | status
  - "team":  "" for core rules, team id for faction content

Run after tools/sync_assets.py. Read-only on its inputs.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
OUT = DATA / "core" / "glossary.json"

# One-sentence effects. Keys are the bare keyword (no numbers). Numbers are substituted at display time.
SHORT: dict[str, str] = {
    # --- weapon rules (core) ---
    "精准": "可将最多 x 枚攻击骰不掷骰直接作为普通成功保留。",
    "平衡": "可重掷 1 枚攻击骰。",
    "爆炸": "对主目标射击后，再对 x\" 内每个次要目标各射击一次。",
    "残暴": "对手只能用暴击成功来抵挡。",
    "无休": "可重掷所有同一点数的攻击骰（例如全部的 2）。",
    "毁灭": "每枚保留的暴击立即额外造成 x 点伤害。",
    "重型": "本次激活移动过就不能用；用过后本次激活不能再移动。",
    "过热": "射击后掷 D6，低于命中值则自己受到结果 ×2 的伤害。",
    "致命": "x+ 的成功视为暴击（默认 6+）。",
    "有限": "本场战斗只能使用 x 次。",
    "穿刺": "防守方少拿 x 枚防御骰；“关键穿刺”需保留暴击才生效。",
    "关键穿刺": "保留了暴击时，防守方少拿 x 枚防御骰。",
    "重击": "保留了暴击时，可将 1 枚失败改为普通成功保留。",
    "范围": "只能射击 x\" 内的目标。",
    "毫不留情": "可重掷任意数量的攻击骰。",
    "撕裂": "保留了暴击时，可将 1 枚普通成功升为暴击。",
    "集中": "防守方不能保留掩护豁免。",
    "追踪": "目标不能借地形获得掩护（“追踪轻型”仅对轻型掩体）。",
    "严重": "没有暴击时，可将 1 枚普通成功升为暴击。",
    "震荡": "首次用暴击出击时，令对手舍弃 1 枚未结算的普通成功。",
    "安静": "持隐匿命令时也能用这件武器射击。",
    "晕眩": "保留了暴击时，目标 APL −1 直到其下次激活结束。",
    "眩晕": "保留了暴击时，目标 APL −1 直到其下次激活结束。",
    "洪流": "对首要目标 x\" 内的其他有效目标各射击一次。",
    "灵能": "灵能武器：每次激活不能重复使用同一件。",
    # --- statuses ---
    "受创": "耐伤低于一半时：移动 −2\"，命中弱化 1。",
    "毒素指示物": "持有者每次被激活时受到 1 点伤害。",
    "掩护": "防守方可将 1 枚防御骰不掷骰直接作为普通豁免保留。",
    "重型遮挡": "攻击方暴击全部降为普通成功，并额外舍弃 1 枚成功。",
    "制高点": "从 2\" 以上高处射击：精准 +1，且目标无法凭轻型掩体隐匿。",
    "隐匿命令": "在掩体后不会成为远程有效目标，但不能射击（安静武器除外）。",
    "交战命令": "可自由射击与冲锋，但随时可能被射击。",
    # --- IMP-AOD ---
    "阿斯塔特": "每次激活可执行两次射击或两次近战（第二次射击需爆矢武器）；任意命令下都可反应。",
    "英雄领袖": "每转折点一次，为该特工免费使用一项交战计谋或条令。",
    "战团老兵": "激活时可免费切换一次本转折点未使用过的战斗条令。",
    "条令战争": "该特工同时受两项战斗条令影响。",
    "钢铁光环": "每场战斗一次，完全无视一枚普通攻击骰的伤害。",
    "狙击手瞄准镜": "瞄准后下一次射击暴击+1 伤害。",
    "战斗条令": "选择毁灭(>6\")、战术(≤6\")或突击(近战)之一，对应行动获得【平衡】。",
    "他们无所畏惧": "本转折点己方特工忽略【受创】带来的移动与命中惩罚。",
    "灵活战术": "每次激活开始时可更换战斗条令。",
    "不屈不挠": "被射击掷出 2 枚以上防御失败时，将其中 2 枚转为 1 枚普通豁免。",
    "调整条令": "立即更换当前战斗条令。",
    "超人体格": "1 枚普通豁免升格为暴击豁免。",
    "震慑突袭": "冲锋后近战获得【震荡】，且首次出击伤害 +1。",
    "复仇之怒": "有己方特工阵亡后，本激活的攻击骰命中改善 1。",
    "纯洁印章": "每转折点一次，掷出两次失败时舍弃其一，另一次作为普通成功保留。",
    "凶刃圣物": "近战武器获得【撕裂】。",
    "偏移盾": "近战中可用 1 枚普通成功格挡暴击。",
    "占卜仪": "每转折点一次，射击时 8\" 内的敌方特工不能被遮挡，持续到本次激活结束。",
    "战团圣物": "对持交战命令的特工使用《复仇之怒》时消耗 0CP。",
    "偏移肩盾": "每转折点一次，近战中对手只有掷出 6 才能保留为关键成功。",
    # chapter tactics
    "猛攻": "近战武器获得【撕裂】。",
    "决斗家": "近战中可用 1 枚普通成功格挡对手暴击（对方武器无【残暴】时）。",
    "坚决": "忽略对 APL 的任何修正，免疫【震荡】。",
    "隐匿": "受射击享有掩护时可多保留 1 枚普通掩护或把掩护升为暴击。",
    "机动": "【后撤】只需 1AP，且可从敌方控制范围内发起【冲锋】。",
    "强健": "被射击时防御骰 5+ 即为暴击豁免。",
    "神射手": "本激活未移动时，爆矢武器获得【精准 1】与【严重】。",
    "攻城战专家": "远程武器获得【集中】，近战免疫敌方围攻协助。",
    # --- CHAOS-PM ---
    "毒素": "任意成功造成伤害后，目标获得毒素指示物（激活时掉 1 血）。",
    "剧毒": "目标已有毒素指示物时，普通与暴击伤害各 +1。",
    "恼人韧性": "每枚造成 3+ 伤害的攻击骰，掷 D6 4+ 则伤害 −1。",
    "慈父赐福": "队长：3\" 内友军享受恼人韧性时可自动成功。",
    "掷弹兵": "手雷武器命中改善 1。",
    "连枷": "连枷近战：对手只能用暴击格挡。",
    "传染": "3\" 内可见带毒敌军（或持徽手 3\" 内任意敌军）移动 −2\" 且命中弱化 1。",
    "慢步死亡": "本激活移动不超过 3\" 时，射击与近战武器获得【无休】。",
    "飞蝇云": "放置飞蝇云标记，2\" 内敌军视为处于重型遮挡。",
    "纳垢灵": "首次出击 1 伤害的攻击骰改为普通成功。",
    "致命毒素": "令 7\" 内可见最近的敌军立刻获得毒素指示物。",
    "剧毒破灭": "带毒目标受到的伤害 +1。",
    "恶心韧性": "本次激活恼人韧性自动成功。",
    "腐烂诅咒": "近战对手失去 1 枚普通成功。",
    "瘟疫之钟": "全军忽略【受创】惩罚。",
    "疫病手雷": "全队获得疫病手雷（4A · 4+ · 2/4，爆炸 2\"、严重、毒素），整场战斗限用 2 次。",
    "瘟疫弹药": "爆矢枪与爆矢手枪获得【毒素】与【严重】。",
    "排毒口": "敌方特工在己方特工 3\" 内被激活时：无毒素则掷 D3，掷出 3 获得毒素；已有毒素则受 D3 伤害。",
}

ALIAS = {"眩晕": "晕眩", "晕眩": "晕眩"}

ICON = {
    "weapon": "🔧", "status": "☣", "faction": "⚜", "ability": "✦", "ploy": "🃏", "tactic": "🛡", "equipment": "🎒",
}


def first_sentence(txt: str) -> str:
    txt = re.sub(r"\s+", " ", txt.strip())
    txt = re.sub(r"^[^：:]{1,12}[：:]", "", txt)  # strip "关键词：" prefix
    m = re.split(r"[。！？]", txt, maxsplit=1)
    return (m[0].strip() + "。") if m and m[0].strip() else txt[:60]


def main() -> None:
    entries: dict[str, dict] = {}

    def put(name: str, kind: str, full: str, team: str = "", short: str | None = None) -> None:
        key = ALIAS.get(name, name)
        if key in entries and entries[key]["full"]:
            return
        s = short or SHORT.get(key) or first_sentence(full)
        entries[key] = {"name": key, "kind": kind, "team": team, "short": s, "full": full.strip(), "icon": ICON.get(kind, "•")}

    core = json.loads((DATA / "core" / "core_rules.json").read_text(encoding="utf-8"))
    for w in core.get("weaponRules", []):
        put(w["keyword"], "weapon", w["text"])
    # Statuses / core concepts from topics
    topic_map = {"wounds": ["受创"], "obscured": ["重型遮挡"], "vantage": ["制高点"], "shooting": ["掩护"]}
    for t in core.get("topics", []):
        for name in topic_map.get(t["id"], []):
            put(name, "status", t["text"])
    for name in ["毒素指示物", "隐匿命令", "交战命令", "关键穿刺"]:
        put(name, "status", SHORT[name])

    for team_file in sorted((DATA / "teams").glob("*.json")):
        if team_file.name == "manifest.json":
            continue
        team = json.loads(team_file.read_text(encoding="utf-8"))
        tid = team["team_id"]
        for wr in team.get("weapon_rules", []):
            put(wr["name"], "weapon", wr["text"], tid)
        kind_by_cat = {"阵营完整规则": "faction", "战略计谋": "ploy", "交战计谋": "ploy", "阵营装备": "equipment"}
        for e in team.get("rule_entries", []):
            k = kind_by_cat.get(e.get("category", ""))
            if not k:
                continue
            title = re.sub(r"[（(].*?[)）]", "", e["title"]).strip()
            put(title, k, e.get("body", ""), tid)
        for op in team.get("operatives", []):
            for ab in op.get("abilities", []):
                if isinstance(ab, dict):
                    put(ab["name"], "faction" if ab.get("factionRule") else "ability", ab.get("text", ""), tid)
                else:
                    put(str(ab), "ability", SHORT.get(str(ab), ""), tid)

    # Chapter tactics come from kt_data.gd constants; mirror them here from SHORT
    for name in ["猛攻", "决斗家", "坚决", "隐匿", "机动", "强健", "神射手", "攻城战专家"]:
        put(name, "tactic", SHORT[name], "IMP-AOD")

    OUT.write_text(json.dumps({"entries": list(entries.values())}, ensure_ascii=False, indent=1), encoding="utf-8")
    missing = [e["name"] for e in entries.values() if not e["short"]]
    print(f"glossary: {len(entries)} entries -> {OUT.relative_to(ROOT)}")
    if missing:
        print("  no short text for:", missing)


if __name__ == "__main__":
    main()
