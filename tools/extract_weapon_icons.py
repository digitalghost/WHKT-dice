#!/usr/bin/env python3
"""武器类型图标识别。

杀戮小队数据卡上的武器类型（远程 / 近战 / 装备）以橙色矢量小图标表示，
不在 PDF 文本层内。本工具：

  scan   渲染数据卡的武器表条带，定位每行武器左侧的图标并计算归一化指纹，
         输出该小队的图标清单；
  build  合并多个小队的扫描结果，生成全局图标库（每种形状一个 key）；
  apply  依据全局图标库把类型写回 manifest.json。

用法：
  python3 tools/extract_weapon_icons.py scan  --pdf X.pdf --manifest M.json --out S.json
  python3 tools/extract_weapon_icons.py build --scan S1.json S2.json ... --out LIB.json
  python3 tools/extract_weapon_icons.py apply --manifest M.json --scan S.json --lib LIB.json
"""
from __future__ import annotations

import argparse
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ktpdf  # noqa: E402

RENDER_DPI = 400
LIB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "weapon_icon_library.json")


def scan_team(pdf_path: str, manifest_path: str, icon_dir: str | None = None) -> dict:
    with open(manifest_path, encoding="utf-8") as fh:
        manifest = json.load(fh)
    doc = ktpdf.PdfDoc(pdf_path)
    if icon_dir is None:
        icon_dir = os.path.join(os.path.dirname(os.path.abspath(manifest_path)), "_icons")
    os.makedirs(icon_dir, exist_ok=True)
    out = {"team": manifest.get("team_name_zh", ""), "cards": {}}
    for card in manifest["cards"]:
        if card["category"] != "operative":
            continue
        page = card["source_page"]
        words = ktpdf.words_in_bbox(doc.words(page), card["bbox_pt"])
        weapons = ktpdf.find_weapon_table(words)
        if not weapons:
            continue
        region = ktpdf.weapon_table_region(words)
        if not region:
            continue
        cols = ktpdf.weapon_table_columns(words)
        name_col_x = cols.get("name")
        x0, y0, w, h = card["bbox_pt"]
        band_top = region[0] - 3
        band_bottom = region[1] + 3
        try:
            img = doc.render_region(page, (x0, band_top, x0 + w, band_bottom), RENDER_DPI)
        except Exception as exc:  # pragma: no cover
            print(f"  ! 渲染失败 {card['id']}: {exc}", file=sys.stderr)
            continue
        ktpdf.find_weapon_icons(
            img, weapons, (x0, band_top, x0 + w, band_bottom), RENDER_DPI, name_col_x)
        rows = []
        for wp in weapons:
            if wp.icon_key and wp.icon_bbox:
                shot = os.path.join(icon_dir, wp.icon_key + ".png")
                if not os.path.exists(shot):
                    x0, y0, x1, y1 = wp.icon_bbox
                    crop = img.crop((x0, y0, x1 + 1, y1 + 1))
                    crop = crop.resize((crop.width * 6, crop.height * 6), Image.NEAREST)
                    crop.save(shot)
            rows.append({
                "name": wp.name,
                "icon_key": wp.icon_key,
                "icon_dims": list(wp.icon_dims),
                "icon_ratio": round(wp.icon_ratio, 3),
                "icon_bitmap": wp.icon_bitmap,
            })
        out["cards"][card["id"]] = {
            "title_zh": card["title_zh"],
            "weapons": rows,
        }
    doc.close()
    return out


def _hamming(a_hex: str, b_hex: str) -> int:
    if not a_hex or not b_hex or len(a_hex) != len(b_hex):
        return 10_000
    a = bytes.fromhex(a_hex)
    b = bytes.fromhex(b_hex)
    return sum(bin(x ^ y).count("1") for x, y in zip(a, b))


def match_kind(lib: dict, bits: str, ratio: float,
               hamming: int = 250, ratio_tol: float = 0.32) -> tuple[str, str]:
    """在图标库里找与该位图最接近的条目，返回 (key, kind)。"""
    if not bits:
        return "", ""
    for k, rec in lib.items():
        if not rec.get("bitmap"):
            continue
        if abs(rec.get("ratio", 0.0) - ratio) > ratio_tol:
            continue
        if _hamming(rec["bitmap"], bits) <= hamming:
            return k, rec.get("kind", "")
    return "", ""


def build_library(scan_paths: list[str], hamming: int = 250, ratio_tol: float = 0.32) -> dict:
    """合并扫描结果，按位图近似度把图标聚成若干「形状」。"""
    lib: dict = {}
    for path in scan_paths:
        with open(path, encoding="utf-8") as fh:
            scan = json.load(fh)
        for card_id, card in scan["cards"].items():
            for w in card["weapons"]:
                key = w.get("icon_key")
                bits = w.get("icon_bitmap")
                if not key or not bits:
                    continue
                target = None
                for k, rec in lib.items():
                    if not rec.get("bitmap"):
                        continue
                    if abs(rec.get("ratio", 0.0) - w.get("icon_ratio", 0.0)) > ratio_tol:
                        continue
                    if _hamming(rec["bitmap"], bits) <= hamming:
                        target = k
                        break
                if target is None:
                    target = key
                    lib.setdefault(target, {
                        "kind": "", "count": 0, "dims": w["icon_dims"],
                        "ratio": w["icon_ratio"], "bitmap": bits, "samples": [],
                    })
                rec = lib[target]
                rec["count"] = rec.get("count", 0) + 1
                sample = f"{scan['team']}/{card_id}/{w['name']}"
                if sample not in rec.get("samples", []) and len(rec.get("samples", [])) < 6:
                    rec.setdefault("samples", []).append(sample)
    return lib


def apply_library(manifest_path: str, scan_path: str, lib: dict) -> int:
    with open(scan_path, encoding="utf-8") as fh:
        scan = json.load(fh)
    with open(manifest_path, encoding="utf-8") as fh:
        manifest = json.load(fh)
    n = 0
    for card in manifest["cards"]:
        rec = scan["cards"].get(card["id"])
        if not rec:
            continue
        kinds = {}
        for w in rec["weapons"]:
            _, kind = match_kind(lib, w.get("icon_bitmap", ""), w.get("icon_ratio", 0.0))
            kinds[w["name"]] = kind
        card["weapon_types"] = kinds
        n += len(kinds)
    with open(manifest_path, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    return n


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("scan")
    p.add_argument("--pdf", required=True)
    p.add_argument("--manifest", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--icon-dir")

    p = sub.add_parser("build")
    p.add_argument("--scan", nargs="+", required=True)
    p.add_argument("--out", default=LIB_PATH)

    p = sub.add_parser("apply")
    p.add_argument("--manifest", required=True)
    p.add_argument("--scan", required=True)
    p.add_argument("--lib", default=LIB_PATH)

    args = ap.parse_args()
    if args.cmd == "scan":
        result = scan_team(args.pdf, args.manifest, args.icon_dir)
        with open(args.out, "w", encoding="utf-8") as fh:
            json.dump(result, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        keys = {w["icon_key"] for c in result["cards"].values() for w in c["weapons"]}
        print(f"{result['team']}: {len(result['cards'])} 张数据卡，图标指纹 {len(keys)} 种")
    elif args.cmd == "build":
        old = {}
        if os.path.exists(args.out):
            with open(args.out, encoding="utf-8") as fh:
                old = json.load(fh)
        lib = build_library(args.scan)
        # 继承旧库中已标注的类别
        for k, rec in lib.items():
            if old.get(k, {}).get("kind"):
                rec["kind"] = old[k]["kind"]
                continue
            for ok, orec in old.items():
                if orec.get("kind") and orec.get("bitmap") and _hamming(orec["bitmap"], rec["bitmap"]) <= 250 \
                        and abs(orec.get("ratio", 0.0) - rec.get("ratio", 0.0)) <= 0.32:
                    rec["kind"] = orec["kind"]
                    break
        with open(args.out, "w", encoding="utf-8") as fh:
            json.dump(lib, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        unk = sum(1 for v in lib.values() if not v.get("kind"))
        print(f"图标库 {args.out}：{len(lib)} 种形状，其中 {unk} 种待标注")
    elif args.cmd == "apply":
        with open(args.lib, encoding="utf-8") as fh:
            lib = json.load(fh)
        n = apply_library(args.manifest, args.scan, lib)
        print(f"{args.manifest}: 写入 {n} 条武器类型")
    return 0


if __name__ == "__main__":
    sys.exit(main())
