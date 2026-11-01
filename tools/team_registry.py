#!/usr/bin/env python3
"""48 支官方杀戮小队的登记表。

按 slug 把官方中文 PDF 与官方英文 PDF 配对，并给出：
  * 本地小队 ID（已有 APP 队伍的沿用原 ID，其余用 KT-<SLUG> 暂定）
  * 中文显示名（用于目录名与文件名）与官方条目名
  * 中英文 PDF 路径、官方更新日期
  * 卡片目录与规则资料文档路径
"""
from __future__ import annotations

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ZH_DIR = os.path.join(ROOT, "docs", "最新杀戮小队规则更新")
EN_DIR = os.path.join(ROOT, "docs", "最新杀戮小队规则更新_EN")
DOCS_DIR = os.path.join(ROOT, "docs")

# 已在 APP 中使用的小队沿用原 ID；其余为本地暂定 ID（待 KTDash 回填官方 ID）
EXISTING_TEAM_IDS = {
    "angels-of-death": "IMP-AOD",
    "plague-marines": "CHAOS-PM",
    "warpcoven": "CHAOS-WC",
    "fellgor-ravagers": "CHAOS-FELL",
    "celestian-insidiants": "IMP-CI",
    "wolf-scouts": "IMP-WS",
    "canoptek-circle": "NEC-CAN",
    "raveners": "TYR-RAV",
    "wyrmblade": "WYRM",
}


def _display_name(title_zh: str) -> str:
    """目录 / 文件名用的中文名：去掉结尾的「杀戮小队」。"""
    name = title_zh.strip()
    for suffix in ("杀戮小队",):
        if name.endswith(suffix) and len(name) > len(suffix):
            name = name[: -len(suffix)].strip()
    return name or title_zh.strip()


def load_teams(root: str = ROOT) -> list[dict]:
    with open(os.path.join(ZH_DIR, "manifest.json"), encoding="utf-8") as fh:
        zh = json.load(fh)
    with open(os.path.join(EN_DIR, "manifest.json"), encoding="utf-8") as fh:
        en = json.load(fh)
    en_by_slug = {f["slug"]: f for f in en["files"]}

    teams = []
    for f in zh["files"]:
        slug = f["slug"]
        e = en_by_slug.get(slug)
        if e is None:
            raise RuntimeError(f"英文版缺少 {slug}")
        name_zh = _display_name(f["title_zh"])
        team_id = EXISTING_TEAM_IDS.get(slug, "KT-" + slug.upper().replace("-", "_"))
        teams.append({
            "slug": slug,
            "team_id": team_id,
            "name_zh": name_zh,
            "title_zh_official": f["title_zh"],
            "name_en": (f.get("title_en") or e.get("title_zh") or "").strip(),
            "zh_pdf": os.path.join(ZH_DIR, f["local_name"]),
            "en_pdf": os.path.join(EN_DIR, e["local_name"]),
            "official_date": f.get("last_updated", ""),
            "card_dir": os.path.join(DOCS_DIR, f"{name_zh}-卡片提取"),
            "doc_path": os.path.join(DOCS_DIR, f"{name_zh}-完整规则资料.md"),
            "work_dir": os.path.join(ROOT, "tmp", "pdfs", slug),
        })
    teams.sort(key=lambda t: t["name_zh"])
    return teams


if __name__ == "__main__":
    teams = load_teams()
    print(f"共 {len(teams)} 支小队")
    for t in teams:
        en_ok = "OK" if os.path.exists(t["en_pdf"]) else "缺"
        zh_ok = "OK" if os.path.exists(t["zh_pdf"]) else "缺"
        print(f"  {t['team_id']:<22} {t['name_zh']:<12} {t['name_en']:<26} zh={zh_ok} en={en_ok} {t['official_date']}")
