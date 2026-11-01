#!/usr/bin/env python3
"""卡图内容完整性校验。

按 manifest 里每条记录的 bbox，用与提取工具完全相同的 pdftoppm 渲染路径
重新裁出参考图（并施加同样的去圆角白边变换），再与磁盘上实际保存的 PNG
做降采样灰度比对，输出内容不匹配或文件缺失的记录。

用法：
    python3 tools/check_card_images.py                     # 全量
    python3 tools/check_card_images.py --team 流亡驭龙师     # 单队
    python3 tools/check_card_images.py --threshold 3
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
from concurrent.futures import ProcessPoolExecutor

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw  # noqa: E402

from team_registry import load_teams  # noqa: E402

SAMPLE = 64  # 降采样到 64x64 做比对


def clean_card_border(im: Image.Image) -> Image.Image:
    """与 extract_kill_team_cards.clean_card_border 保持一致的变换。"""
    im = im.convert("RGBA")
    if im.width > 6 and im.height > 6:
        im = im.crop((3, 3, im.width - 3, im.height - 3))
    w, h = im.size
    short = w if w < h else h
    radius = short * 7 // 100
    radius = max(24, min(48, radius))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)
    im.putalpha(mask)
    return im


def _compare(ref: Image.Image, got: Image.Image) -> float:
    """降采样灰度平均绝对差。"""
    a = ref.convert("L").resize((SAMPLE, SAMPLE), Image.BILINEAR)
    b = got.convert("L").resize((SAMPLE, SAMPLE), Image.BILINEAR)
    pa = list(a.getdata())
    pb = list(b.getdata())
    return sum(abs(x - y) for x, y in zip(pa, pb)) / float(len(pa))


def check_team(team: dict, dpi: int, threshold: float) -> list:
    manifest_path = os.path.join(team["card_dir"], "manifest.json")
    if not os.path.exists(manifest_path):
        return [{"team": team["name_zh"], "id": None, "image": None,
                 "issue": "缺少 manifest", "mad": None}]
    with open(manifest_path, encoding="utf-8") as fh:
        data = json.load(fh)

    scale = dpi / 72.0
    pages = {}
    problems = []
    with tempfile.TemporaryDirectory(prefix="ktimgchk-") as td:
        for card in data.get("cards", []):
            img_path = os.path.join(team["card_dir"], card["image"])
            if not os.path.exists(img_path):
                problems.append({
                    "team": team["name_zh"], "id": card["id"], "image": card["image"],
                    "issue": "文件缺失", "mad": None,
                })
                continue
            pno = card["source_page"]
            if pno not in pages:
                pattern = os.path.join(td, f"p{pno}")
                subprocess.run(
                    ["pdftoppm", "-png", "-r", str(dpi), "-f", str(pno), "-l", str(pno),
                     "-singlefile", team["zh_pdf"], pattern],
                    check=True, capture_output=True,
                )
                pages[pno] = Image.open(pattern + ".png").convert("RGB")
            src = pages[pno]

            x0, y0, w, h = card["bbox_pt"]
            px = min(max(0, int(round(x0 * scale))), src.width)
            py = min(max(0, int(round(y0 * scale))), src.height)
            pw = max(1, min(src.width - px, int(round(w * scale))))
            ph = max(1, min(src.height - py, int(round(h * scale))))
            ref = clean_card_border(src.crop((px, py, px + pw, py + ph)))

            with Image.open(img_path) as got:
                got.load()
                mad = _compare(ref, got)
            if mad > threshold:
                problems.append({
                    "team": team["name_zh"], "id": card["id"], "image": card["image"],
                    "issue": "内容与 bbox 不符", "mad": round(mad, 2),
                })
    return problems


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--team", action="append", default=None)
    ap.add_argument("--dpi", type=int, default=200)
    ap.add_argument("--threshold", type=float, default=3.0)
    ap.add_argument("--jobs", type=int, default=8)
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    teams = load_teams()
    if args.team:
        wanted = set(args.team)
        teams = [t for t in teams if t["name_zh"] in wanted or t["slug"] in wanted]
    if not teams:
        print("没有匹配的小队")
        return 1

    all_problems = []
    with ProcessPoolExecutor(max_workers=args.jobs) as pool:
        futures = {pool.submit(check_team, t, args.dpi, args.threshold): t for t in teams}
        for fut, t in futures.items():
            res = fut.result()
            all_problems.extend(res)
            status = "OK" if not res else f"{len(res)} 处异常"
            print(f"{t['name_zh']}: {status}", flush=True)

    print("\n==== 异常明细 ====")
    for p in all_problems:
        print(json.dumps(p, ensure_ascii=False))
    print(f"\n合计异常 {len(all_problems)} 项 / 检查 {len(teams)} 队")

    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            json.dump(all_problems, fh, ensure_ascii=False, indent=2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
