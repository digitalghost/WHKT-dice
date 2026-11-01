#!/usr/bin/env python3
"""按杀戮小队 PDF 提取独立卡图、卡片全文与卡片索引。

流程
----
1. 用 tools/detect_card_frames.py 的虚线裁切标记几何，定位每页的候选格；
2. 读取 PDF 文本层，按「标题区字号 / 笔记标签」区分
   - 真正的卡片（有卡片标题栏）
   - 背面续栏（同一张卡的反面，文字在右栏）
   - 笔记块（印有「笔记：」，不是卡片，不出图）
3. 以 200 DPI 渲染整页，按 PDF 点坐标裁切出每张独立卡图；
4. 用 PIL 削 3 px 并按圆角矩形写入 alpha，去掉裁切定位线留下的白边；
5. 逐卡抽取 PDF 文本层局部文字，作为卡片的可搜索全文；
6. 写出 manifest.json（程序化索引）与 README.md（人工浏览索引）。

用法：
  python3 tools/extract_kill_team_cards.py <pdf> \
      --out-dir docs/<中文名>-卡片提取 \
      --team-id <TEAM_ID> --team-name-zh <中文名> --team-name-en <English> \
      [--render-dpi 200] [--no-border-clean]
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import date

import pdfplumber
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from detect_card_frames import analyze as analyze_frames  # noqa: E402
import ktpdf  # noqa: E402

CATEGORY_BY_HEADER = {
    "阵营规则": "faction-rule",
    "战略计谋": "strategic-ploy",
    "交战计谋": "firefight-ploy",
    "阵营装备": "faction-equipment",
    "小队选择": "team-selection",
}
TEAM_SELECTION_SUFFIX = "杀戮小队"
CATEGORY_LABEL = {
    "operative": "特工",
    "operative-ability": "特工能力",
    "team-selection": "小队选择",
    "faction-rule": "阵营规则",
    "strategic-ploy": "战略计谋",
    "firefight-ploy": "交战计谋",
    "faction-equipment": "阵营装备",
    "other": "其他",
}
# 编号前缀按大类共用计数器（特工类共用 01），与既有小队目录保持一致
SEQ_PREFIX = {
    "operative": "01",
    "operative-ability": "01",
    "team-selection": "10",
    "faction-rule": "11",
    "strategic-ploy": "20",
    "firefight-ploy": "30",
    "faction-equipment": "40",
    "other": "16",
}
CARD_TITLE_MIN_SIZE = 13.0   # 卡片标题栏字号下限
NOTES_LABELS = ("笔记", "备注")
WEAPON_TABLE_HEADERS = ("名称", "攻击", "命中", "伤害", "武器规则")
NUMERIC_RE = re.compile(r"^[\d\.\+\"']+$")


def run(cmd, **kw):
    return subprocess.run(cmd, check=True, capture_output=True, text=True, **kw)


def page_words(pdf_path):
    """每页的单词列表（含字号）。"""
    result = {}
    with pdfplumber.open(pdf_path) as pdf:
        for pno, page in enumerate(pdf.pages, 1):
            result[pno] = page.extract_words(extra_attrs=["size"])
    return result


def _operative_name(header, x0):
    """从标题区里取特工名：字号最大、且不是纯数值的词。"""
    cands = [
        wd for wd in header
        if 10 <= wd["size"] <= 19 and wd["x0"] < x0 + 210 and not NUMERIC_RE.match(wd["text"])
    ]
    if not cands:
        return None
    cands.sort(key=lambda wd: (-round(wd["size"], 1), round(wd["top"], 1), wd["x0"]))
    return cands[0]["text"]


def _rule_title(inside, cat_word):
    """规则卡标题：类别字下方字号最大的那一行。

    官方版式常在类别字与规则名之间夹一行小字（阵营关键词 / 副标题），
    直接取「第一行」会把关键词误当标题，因此按字号取真正的规则名。
    """
    after = [w for w in inside if w["top"] > cat_word["top"] + 8 and w["size"] >= 10]
    if not after:
        return None
    max_size = max(w["size"] for w in after)
    cands = [w for w in after if w["size"] >= max_size - 0.5]
    top = min(w["top"] for w in cands)
    line = sorted([w for w in cands if w["top"] <= top + 1.5], key=lambda z: z["x0"])
    return "".join(w["text"] for w in line) or None


def _has_stats(inside) -> bool:
    """是否带有 APL / 移动 / 豁免 / 耐伤 属性栏。"""
    texts = {wd["text"] for wd in inside}
    if {"APL", "耐伤"} <= texts or {"MOVE", "WOUNDS"} <= texts:
        return True
    return bool({"移动", "豁免"} <= texts)


def classify_cell(words, box):
    """判定一个候选格的性质。

    卡片版式（官方杀戮小队 PDF）：
      * 阵营规则 / 战略计谋 / 交战计谋 / 阵营装备 —— 类别字 + 规则标题；
      * 小队选择 —— 标题为「<中文名>杀戮小队」；
      * 特工数据卡 —— 带「名称/攻击/命中/伤害/武器规则」武器表；
      * 特工卡背面 —— 重复属性栏但没有武器表，内容是特工专属规则；
      * 参考卡（如「标识/指示物指南」）—— 只有一个大标题。

    返回 dict：kind(card/back/notes)、title、category、name。
    """
    x0, y0, w, h = box
    x1, y1 = x0 + w, y0 + h
    inside = [
        wd for wd in words
        if wd["x0"] >= x0 - 2 and wd["x1"] <= x1 + 2 and y0 - 4 <= wd["top"] <= y1 + 4
    ]
    if not inside:
        return {"kind": "empty", "title": None, "category": None, "name": None}

    # 笔记块：格内出现「笔记：」「备注：」标签
    for wd in inside:
        if any(lbl in wd["text"] for lbl in NOTES_LABELS) and wd["size"] < 12:
            return {"kind": "notes", "title": None, "category": None, "name": None}

    header = sorted(
        [wd for wd in inside if wd["top"] <= y0 + 72],
        key=lambda wd: (round(wd["top"], 1), wd["x0"]),
    )
    has_table = any(wd["text"] in WEAPON_TABLE_HEADERS for wd in inside)

    # 1) 规则卡：标题区里出现类别字
    for wd in header:
        if wd["text"] in CATEGORY_BY_HEADER:
            return {"kind": "card", "title": _rule_title(inside, wd),
                    "category": CATEGORY_BY_HEADER[wd["text"]], "name": None}

    # 2) 小队选择卡：标题为「<中文名>杀戮小队」
    for wd in header:
        if wd["size"] >= 14 and wd["text"].endswith(TEAM_SELECTION_SUFFIX):
            return {"kind": "card", "title": wd["text"],
                    "category": "team-selection", "name": None}

    # 3) 特工数据卡：同时具备武器表与 APL/移动/豁免/耐伤 属性栏
    #    （阵营规则卡的背面也可能带武器表，只有属性栏能把它区分出来）
    if has_table and _has_stats(inside):
        return {
            "kind": "card", "title": None, "category": "operative",
            "name": _operative_name(header, x0), "sub": "数据卡",
        }

    # 4) 特工卡背面（重复属性栏、无武器表）
    if _has_stats(inside):
        name = _operative_name(header, x0)
        if name:
            return {
                "kind": "card", "title": None, "category": "operative-ability",
                "name": name, "sub": "能力卡",
            }

    # 5) 参考卡：只有一个大标题
    big = [wd for wd in header if wd["size"] >= 14 and not NUMERIC_RE.match(wd["text"])]
    if big:
        max_size = max(wd["size"] for wd in big)
        t0 = sorted(
            [wd for wd in big if wd["size"] >= max_size - 0.5],
            key=lambda wd: (round(wd["top"], 1), wd["x0"]),
        )[0]
        return {"kind": "card", "title": t0["text"], "category": "other", "name": None}

    # 6) 其余：正文续栏（卡片背面）
    return {"kind": "back", "title": None, "category": None, "name": None}


def card_text(doc: ktpdf.PdfDoc, page_no, box):
    """按裁切区域与分栏版式抽取该卡的 PDF 文本层。"""
    return ktpdf.structured_card_text(doc.words(page_no), box)


def crop_cards(pdf_path, out_dir, cards, dpi):
    """按 bbox 渲染裁切卡图。整页只渲染一次，再用 PIL 裁切。"""
    pages = {}
    for card in cards:
        pno = card["source_page"]
        if pno not in pages:
            pattern = os.path.join(tempfile.mkdtemp(prefix="ktcard-"), "p")
            run([
                "pdftoppm", "-png", "-r", str(dpi),
                "-f", str(pno), "-l", str(pno), "-singlefile",
                pdf_path, pattern,
            ])
            pages[pno] = Image.open(pattern + ".png").convert("RGB")
        src = pages[pno]
        x0, y0, w, h = card["bbox_pt"]
        scale = dpi / 72.0
        px = max(0, int(round(x0 * scale)))
        py = max(0, int(round(y0 * scale)))
        pw = max(1, min(src.width - px, int(round(w * scale))))
        ph = max(1, min(src.height - py, int(round(h * scale))))
        dst = os.path.join(out_dir, card["image"])
        src.crop((px, py, px + pw, py + ph)).save(dst)


def clean_card_border(path):
    """去掉裁切定位线留下的圆角白边：削 3 px，再按圆角矩形写入 alpha。"""
    im = Image.open(path).convert("RGBA")
    if im.width > 6 and im.height > 6:
        im = im.crop((3, 3, im.width - 3, im.height - 3))
    w, h = im.size
    short = w if w < h else h
    radius = short * 7 // 100
    radius = max(24, min(48, radius))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)
    im.putalpha(mask)
    im.save(path)


def safe_name(title: str) -> str:
    """把标题里的文件系统非法字符换成短横线。"""
    out = re.sub(r"[\\/:*?\"<>|]", "-", title).strip()
    return out or "未命名"


def build_names(cards, team_name_zh="虫刃"):
    """按类别 + 中文名 + 跨页后缀生成稳定文件名与编号。

    * 特工类共用 01 计数器；能力卡同名特工多于一张时追加序号；
    * 逻辑卡组（正面 + 背面）多于一张时追加 -1 / -2 后缀；
    * 其余类别沿用既有小队目录的命名。
    """
    counters = {}
    ability_counts = {}
    for card in cards:
        cat = card["category"]
        if cat == "operative-ability":
            ability_counts[card["name"]] = ability_counts.get(card["name"], 0) + 1
    ability_seen = {}
    for card in cards:
        cat = card["category"]
        prefix = SEQ_PREFIX[cat]
        counters[prefix] = counters.get(prefix, 0) + 1
        seq = counters[prefix]
        card["seq"] = seq

        if cat == "operative":
            title = f"{card['name']}-数据卡"
        elif cat == "operative-ability":
            ability_seen[card["name"]] = ability_seen.get(card["name"], 0) + 1
            n = ability_seen[card["name"]]
            total = ability_counts[card["name"]]
            title = f"{card['name']}-能力卡{n}" if total > 1 else f"{card['name']}-能力卡"
        elif cat == "team-selection":
            title = f"{team_name_zh}杀戮小队"
        else:
            title = card["title"] or "未命名"

        group = card.get("group_members") or []
        if len(group) > 1:
            ordered = sorted(group, key=lambda c: (c["source_page"], c["page_order"]))
            title = f"{title}-{ordered.index(card) + 1}"

        card["title_display"] = title
        card["image"] = f"{prefix}-{seq:02d}-{CATEGORY_LABEL[cat]}-{safe_name(title)}.png"
    return cards


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf")
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--team-id", required=True)
    ap.add_argument("--team-name-zh", required=True)
    ap.add_argument("--team-name-en", required=True)
    ap.add_argument("--render-dpi", type=int, default=200)
    ap.add_argument("--no-border-clean", action="store_true")
    ap.add_argument("--reuse-images", action="store_true",
                    help="复用已有卡图（只重做分类、命名与索引），用于标题修正")
    args = ap.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)

    old_images = {}
    old_weapon_types = {}
    old_manifest = os.path.join(args.out_dir, "manifest.json")
    if args.reuse_images and os.path.exists(old_manifest):
        with open(old_manifest, encoding="utf-8") as fh:
            old_data = json.load(fh)
        for c in old_data.get("cards", []):
            key = (c["source_page"], tuple(round(v, 2) for v in c["bbox_pt"]))
            old_images[key] = c["image"]
            if c.get("weapon_types"):
                old_weapon_types[key] = c["weapon_types"]

    frames = analyze_frames(args.pdf)
    words = page_words(args.pdf)

    cards = []
    for page in frames:
        for cell in page["cards"]:
            box = cell["bbox_pt"]
            info = classify_cell(words[page["page"]], box)
            if info["kind"] == "notes":
                continue
            if info["kind"] == "empty":
                continue
            cards.append(
                {
                    "source_page": page["page"],
                    "page_order": cell["order"],
                    "bbox_pt": box,
                    "bbox_norm": cell["bbox_norm"],
                    "kind": info["kind"],
                    "category": info["category"],
                    "name": info["name"],
                    "sub": info.get("sub"),
                    "title": info["title"],
                }
            )

    # 背面续栏 / 无属性块特工能力卡：按阅读顺序归并到最近的前序主卡
    for idx, card in enumerate(cards):
        if card["kind"] != "back":
            continue
        host = None
        for other in reversed(cards[:idx]):
            if other["kind"] == "card":
                host = other
                break
        if host is None:
            card["kind"] = "orphan"
            continue
        if host["category"] in ("operative", "operative-ability"):
            card["kind"] = "card"
            card["category"] = "operative-ability"
            card["name"] = host["name"]
            card["sub"] = "能力卡"
        else:
            card["category"] = host["category"]
            card["name"] = host["name"]
            card["sub"] = host["sub"]
            card["title"] = host["title"]

    cards = [c for c in cards if c["kind"] != "orphan"]

    # 同一小队内同类别、同标题的多张规则/选择/参考卡归入同一逻辑卡组并按 (页码, 页内顺序) 编号
    by_group: dict[tuple, list] = {}
    for card in cards:
        if card["category"] in ("operative", "operative-ability"):
            continue
        gkey = (card["category"], card["title"] or "未命名")
        by_group.setdefault(gkey, []).append(card)
    for members in by_group.values():
        if len(members) > 1:
            ordered = sorted(members, key=lambda c: (c["source_page"], c["page_order"]))
            for m in ordered:
                m["group_members"] = ordered

    build_names(cards, args.team_name_zh)

    if args.reuse_images:
        # 两阶段改名 + 增量补裁：
        # 1) 把 bbox 未变的已有卡图先移入临时目录；
        # 2) 清理目录内残留的作废卡图（如旧版被误切碎的半张卡）；
        # 3) 将复用的卡图落到新文件名，并对新增/重裁的卡片执行渲染裁切与圆角清理。
        staged = []
        need_crop = []
        with tempfile.TemporaryDirectory(prefix="ktrename-") as td:
            for i, card in enumerate(cards):
                key = (card["source_page"], tuple(round(v, 2) for v in card["bbox_pt"]))
                old = old_images.get(key)
                src = os.path.join(args.out_dir, old) if old else None
                if src and os.path.exists(src):
                    tmp = os.path.join(td, f"{i:05d}.png")
                    shutil.move(src, tmp)
                    staged.append((tmp, os.path.join(args.out_dir, card["image"])))
                else:
                    need_crop.append(card)
            for fname in os.listdir(args.out_dir):
                if fname.lower().endswith(".png"):
                    os.unlink(os.path.join(args.out_dir, fname))
            for tmp, dst in staged:
                shutil.move(tmp, dst)
        if need_crop:
            crop_cards(args.pdf, args.out_dir, need_crop, args.render_dpi)
            if not args.no_border_clean:
                for card in need_crop:
                    clean_card_border(os.path.join(args.out_dir, card["image"]))
    else:
        for fname in os.listdir(args.out_dir):
            if fname.lower().endswith(".png"):
                os.unlink(os.path.join(args.out_dir, fname))
        crop_cards(args.pdf, args.out_dir, cards, args.render_dpi)

        if not args.no_border_clean:
            for card in cards:
                clean_card_border(os.path.join(args.out_dir, card["image"]))

    # 文本
    with ktpdf.PdfDoc(args.pdf) as doc:
        for card in cards:
            card["text"] = card_text(doc, card["source_page"], card["bbox_pt"])

    records = []
    for card in cards:
        key = (card["source_page"], tuple(round(v, 2) for v in card["bbox_pt"]))
        rec = {
            "id": f"{args.team_id.lower()}-{SEQ_PREFIX[card['category']]}-{card['seq']:02d}",
            "sequence": card["seq"],
            "category": card["category"],
            "title_zh": card["title_display"],
            "title_en": None,
            "source_page": card["source_page"],
            "page_order": card["page_order"],
            "bbox_pt": card["bbox_pt"],
            "bbox_norm": card["bbox_norm"],
            "image": card["image"],
            "is_back": card["kind"] == "back",
            "text": card["text"],
        }
        if key in old_weapon_types:
            rec["weapon_types"] = old_weapon_types[key]
        records.append(rec)

    counts = {}
    for rec in records:
        counts[rec["category"]] = counts.get(rec["category"], 0) + 1

    manifest = {
        "schema_version": 1,
        "team_id": args.team_id,
        "team_name_zh": args.team_name_zh,
        "team_name_en": args.team_name_en,
        "source_pdf": os.path.relpath(args.pdf, os.path.dirname(args.out_dir.rstrip("/"))),
        "source_pdf_sha256": run(["shasum", "-a", "256", args.pdf]).stdout.split()[0],
        "extracted_at": date.today().isoformat(),
        "render_dpi": args.render_dpi,
        "source_data": "官方中文 PDF（唯一信源）",
        "counts": {"total": len(records), **counts},
        "cards": records,
    }
    with open(os.path.join(args.out_dir, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)

    write_readme(args.out_dir, manifest)
    print(f"卡片 {len(records)} 张 -> {args.out_dir}")
    for cat, n in sorted(counts.items()):
        print(f"  {cat}: {n}")
    return 0


def write_readme(out_dir, manifest):
    lines = [
        f"# {manifest['team_name_zh']}（{manifest['team_name_en']}）卡片索引",
        "",
        f"- 小队 ID：`{manifest['team_id']}`",
        f"- 来源 PDF：`{manifest['source_pdf']}`",
        f"- 提取日期：{manifest['extracted_at']}",
        f"- 数据来源：{manifest['source_data']}",
        f"- 独立卡图总数：**{manifest['counts']['total']}**（总览图不计入）",
        "",
        "## 分类计数",
        "",
        "| 类别 | 数量 |",
        "|---|---:|",
    ]
    for cat, n in sorted(manifest["counts"].items()):
        if cat == "total":
            continue
        lines.append(f"| {cat} | {n} |")
    lines.append("")
    order = [
        "operative", "operative-ability", "team-selection",
        "faction-rule", "strategic-ploy", "firefight-ploy", "faction-equipment", "other",
    ]
    for cat in order:
        items = [c for c in manifest["cards"] if c["category"] == cat]
        if not items:
            continue
        lines.append(f"## {CATEGORY_LABEL.get(cat, cat)}（{len(items)} 张）")
        lines.append("")
        lines.append("| # | 标题 | 源页 | 卡图 |")
        lines.append("|---:|---|---:|---|")
        for c in items:
            lines.append(
                f"| {c['sequence']} | {c['title_zh']} | 第 {c['source_page']} 页 | "
                f"[{c['image']}]({c['image']}) |"
            )
        lines.append("")
    with open(os.path.join(out_dir, "README.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))


if __name__ == "__main__":
    sys.exit(main())
