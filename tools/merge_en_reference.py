#!/usr/bin/env python3
"""把官方英文版 PDF 的同名卡片文本并入中文 manifest，生成中英对照。

官方同时发布同一支小队的「中文版」和「英文版」规则 PDF，两者页面尺寸、
卡片切分位置完全一致，因此可以按（页码, 页内顺序, 裁切坐标）严格配对，
得到可信的中英对照，而不需要任何猜测式翻译。

用法：
  python3 tools/merge_en_reference.py <中文manifest.json> <英文pdf> [英文manifest]
"""
import argparse
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from detect_card_frames import analyze as analyze_frames  # noqa: E402
import ktpdf  # noqa: E402

RULE_CATEGORIES = {
    "阵营规则": "faction-rule",
    "战略计谋": "strategic-ploy",
    "交战计谋": "firefight-ploy",
    "阵营装备": "faction-equipment",
    "FACTION RULE": "faction-rule",
    "FACTION RULES": "faction-rule",
    "STRATEGIC PLOY": "strategic-ploy",
    "STRATEGY PLOY": "strategic-ploy",
    "FIREFIGHT PLOY": "firefight-ploy",
    "FACTION EQUIPMENT": "faction-equipment",
}


def card_text(doc: ktpdf.PdfDoc, page_no, box):
    return ktpdf.structured_card_text(doc.words(page_no), box)


def english_title(text, category):
    """从英文卡片文本里取标题。"""
    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
    if not lines:
        return None
    if category in ("faction-rule", "strategic-ploy", "firefight-ploy", "faction-equipment"):
        for idx, ln in enumerate(lines):
            if ln.upper() in RULE_CATEGORIES:
                # 标题在类别行的下一行
                for nxt in lines[idx + 1:]:
                    if nxt.upper() not in RULE_CATEGORIES:
                        return nxt
                return None
        return None
    if category == "team-selection":
        for ln in lines:
            if "KILL TEAM" in ln.upper():
                return "KILL TEAM"
        return None
    # 特工卡 / 其他参考卡：标题区里的大写名字（剥离可能同行的 APL/MOVE/SAVE/WOUNDS 表头）
    for ln in lines[:4]:
        cleaned = re.sub(r"\b(APL|MOVE|SAVE|WOUNDS)\b", "", ln).strip()
        if not cleaned:
            continue
        if any(k in cleaned for k in ("RULES CONTINUE", "CONTINUES ON", "NAME ATK")):
            continue
        if re.fullmatch(r"[A-Z0-9][A-Z0-9 \-'\.‘’&/()]+", cleaned) and re.search(r"[A-Z]{2,}", cleaned):
            return cleaned
    return None


def _base_title(title: str) -> str:
    return re.sub(r"-\d+$", "", title or "")


def _op_name(title: str) -> str:
    return re.sub(r"-(数据卡|能力卡)\d*$", "", _base_title(title))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("manifest_zh")
    ap.add_argument("pdf_en")
    args = ap.parse_args()

    with open(args.manifest_zh, encoding="utf-8") as fh:
        manifest = json.load(fh)

    frames = analyze_frames(args.pdf_en)
    en_cells = {}
    for pg in frames:
        for c in pg["cards"]:
            en_cells[(pg["page"], c["order"])] = c["bbox_pt"]

    missing = 0
    with ktpdf.PdfDoc(args.pdf_en) as doc:
        for card in manifest["cards"]:
            key = (card["source_page"], card["page_order"])
            box = en_cells.get(key)
            if box is None:
                print(f"  [跳过] 英文版无对应卡片：{card['image']}")
                missing += 1
                continue
            text = card_text(doc, key[0], box)
            card["text_en"] = text
            card["title_en"] = english_title(text, card["category"])

    # 特工能力卡统一沿用对应特工正面数据卡的英文特工名；同组续卡继承正面英文标题
    op_en_map = {}
    group_en_map = {}
    for card in manifest["cards"]:
        if card["category"] == "operative" and card.get("title_en"):
            op_en_map[_op_name(card["title_zh"])] = card["title_en"]
        gkey = (card["category"], _base_title(card["title_zh"]))
        if card.get("title_en") and gkey not in group_en_map:
            group_en_map[gkey] = card["title_en"]

    for card in manifest["cards"]:
        if card["category"] == "operative-ability":
            op_zh = _op_name(card["title_zh"])
            if op_zh in op_en_map:
                card["title_en"] = op_en_map[op_zh]
        elif not card.get("title_en"):
            gkey = (card["category"], _base_title(card["title_zh"]))
            if gkey in group_en_map:
                card["title_en"] = group_en_map[gkey]

    with open(args.manifest_zh, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)

    named = sum(1 for c in manifest["cards"] if c.get("title_en"))
    print(f"已并入英文文本 {len(manifest['cards']) - missing} 张，成功取到英文标题 {named} 张")
    for c in manifest["cards"]:
        print(f"  {c['title_zh']:22} -> {c.get('title_en')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
