#!/usr/bin/env python3
"""把全局武器图标库渲染成联系表，供人工标注「远程 / 近战 / 装备」。

用法：
  python3 tools/render_icon_sheet.py --icons tmp/pdfs/*/icons [--lib 图标库.json] [输出.png]
"""
from __future__ import annotations

import argparse
import glob
import json
import os

from PIL import Image, ImageDraw

LIB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "weapon_icon_library.json")
CELL = 210
COLS = 6


def render(lib: dict, icon_dirs: list[str], out_path: str) -> str:
    shots = {}
    for d in icon_dirs:
        for png in glob.glob(os.path.join(d, "*.png")):
            shots.setdefault(os.path.splitext(os.path.basename(png))[0], png)
    keys = [k for k in sorted(lib, key=lambda k: -lib[k].get("count", 0)) if k in shots]
    rows = max(1, (len(keys) + COLS - 1) // COLS)
    img = Image.new("RGB", (COLS * CELL, rows * CELL), "white")
    draw = ImageDraw.Draw(img)
    for i, key in enumerate(keys):
        rec = lib[key]
        cx = (i % COLS) * CELL
        cy = (i // COLS) * CELL
        icon = Image.open(shots[key]).convert("RGB")
        icon = icon.resize((min(icon.width, 150), min(icon.height, 110)), Image.NEAREST)
        img.paste(icon, (cx + 10, cy + 10))
        draw.rectangle([cx + 6, cy + 6, cx + 164, cy + 124], outline=(190, 190, 190))
        draw.text((cx + 8, cy + 130), key, fill=(0, 0, 0))
        draw.text((cx + 8, cy + 144), f"n={rec.get('count', 0)}  {rec.get('kind', '?')}",
                  fill=(150, 0, 0))
        draw.text((cx + 8, cy + 160), (rec.get("samples") or [""])[0][:28], fill=(90, 90, 90))
    img.save(out_path)
    return out_path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--icons", nargs="+", required=True)
    ap.add_argument("--lib", default=LIB)
    ap.add_argument("out", nargs="?", default=os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "weapon_icon_sheet.png"))
    args = ap.parse_args()
    dirs = []
    for pat in args.icons:
        dirs.extend(sorted(glob.glob(pat)))
    with open(args.lib, encoding="utf-8") as fh:
        lib = json.load(fh)
    print(render(lib, dirs, args.out))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
