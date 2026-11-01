#!/usr/bin/env python3
"""为小队卡片目录生成联系表，用于人工目视验收（方向、裁边、相邻内容污染）。

用法：
  python3 tools/make_contact_sheets.py docs/<名>-卡片提取 --out tmp/pdfs/<slug>/sheet.png
"""
from __future__ import annotations

import argparse
import json
import os

from PIL import Image, ImageDraw

CATEGORY_ORDER = [
    "operative", "operative-ability", "team-selection", "faction-rule",
    "strategic-ploy", "firefight-ploy", "faction-equipment", "other",
]
LABEL = {
    "operative": "特工数据卡", "operative-ability": "特工卡背面",
    "team-selection": "小队选择", "faction-rule": "阵营规则",
    "strategic-ploy": "战略计谋", "firefight-ploy": "交战计谋",
    "faction-equipment": "阵营装备", "other": "其他参考卡",
}


def sheet(card_dir: str, out_path: str, cols: int = 4, thumb_w: int = 420) -> str:
    with open(os.path.join(card_dir, "manifest.json"), encoding="utf-8") as fh:
        manifest = json.load(fh)
    cards = manifest["cards"]
    groups = []
    for cat in CATEGORY_ORDER:
        items = [c for c in cards if c["category"] == cat]
        if items:
            groups.append((cat, items))

    thumbs = []
    for cat, items in groups:
        for c in items:
            path = os.path.join(card_dir, c["image"])
            im = Image.open(path).convert("RGBA")
            ratio = thumb_w / im.width
            im = im.resize((thumb_w, max(1, int(im.height * ratio))), Image.LANCZOS)
            thumbs.append((cat, c, im))

    rows = []
    for cat, items in groups:
        rows.append((cat, len(items)))
    # 计算每类占的行数
    layout = []
    for cat, items in groups:
        n = len(items)
        r = (n + cols - 1) // cols
        layout.append((cat, n, r))

    cell_h = max((im.height for _, _, im in thumbs), default=100) + 26
    total_h = 0
    for cat, n, r in layout:
        total_h += 30 + r * cell_h
    img = Image.new("RGB", (cols * thumb_w + 20, total_h + 20), "white")
    draw = ImageDraw.Draw(img)
    y = 10
    for cat, n, r in layout:
        draw.text((10, y), f"{LABEL.get(cat, cat)}（{n} 张）", fill=(0, 0, 0))
        y += 26
        idx = 0
        for c_cat, c, im in thumbs:
            if c_cat != cat:
                continue
            x = 10 + (idx % cols) * thumb_w
            yy = y + (idx // cols) * cell_h
            img.paste(im, (x, yy), im)
            draw.rectangle([x, yy, x + im.width, yy + im.height], outline=(180, 180, 180))
            draw.text((x + 4, yy + im.height + 4), f"{c['sequence']:02d} {c['title_zh']}"[:60],
                      fill=(60, 60, 60))
            idx += 1
        y += r * cell_h + 4
    img.save(out_path)
    return out_path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("card_dir")
    ap.add_argument("--out", required=True)
    ap.add_argument("--cols", type=int, default=4)
    ap.add_argument("--width", type=int, default=420)
    args = ap.parse_args()
    print(sheet(os.path.abspath(args.card_dir), args.out, args.cols, args.width))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
