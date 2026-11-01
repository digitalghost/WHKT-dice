#!/usr/bin/env python3
"""给全局武器图标库人工标注类型（远程 / 近战 / 装备）。

用法：
  python3 tools/label_weapon_icons.py --lib 图标库.json KEY=远程 KEY2=近战
  python3 tools/label_weapon_icons.py --lib 图标库.json --from 标注.json
"""
from __future__ import annotations

import argparse
import json
import os

LIB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "weapon_icon_library.json")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lib", default=LIB)
    ap.add_argument("--from", dest="from_file")
    ap.add_argument("pairs", nargs="*")
    args = ap.parse_args()

    with open(args.lib, encoding="utf-8") as fh:
        lib = json.load(fh)

    labels = {}
    if args.from_file:
        with open(args.from_file, encoding="utf-8") as fh:
            labels = json.load(fh)
    for pair in args.pairs:
        key, _, kind = pair.partition("=")
        labels[key] = kind

    unknown = [k for k in labels if k not in lib]
    for key, kind in labels.items():
        if key in lib:
            lib[key]["kind"] = kind
    with open(args.lib, "w", encoding="utf-8") as fh:
        json.dump(lib, fh, ensure_ascii=False, indent=2)
        fh.write("\n")

    unlabeled = sorted(k for k, v in lib.items() if not v.get("kind"))
    print(f"已标注 {len(labels)} 条；库中仍待标注 {len(unlabeled)} 种：{unlabeled}")
    if unknown:
        print(f"注意：{len(unknown)} 个 key 不在库里：{unknown}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
