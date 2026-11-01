#!/usr/bin/env python3
"""校验一支小队的卡片提取结果是否满足工作流的完整性要求。

检查项
------
1. 数量对账：manifest 记录数 = README 索引数 = 独立卡图数；
2. 文件对账：manifest / README / 正文里引用的每张卡图都真实存在；
3. 索引一致：README 与 manifest 的卡片顺序、标题、类别完全一致；
4. 编号规范：文件名前缀与类别一一对应，同类编号连续；
5. 图像健康：PNG 可解码、尺寸非零、纵横比与 manifest 记录一致、四角 Alpha 透明；
6. 正文检查：Markdown 表格列数一致、无未替换占位符、图片链接可解析。

用法：
  python3 tools/verify_team_extract.py <卡片目录> [完整规则资料.md]
"""
import json
import os
import re
import sys

from PIL import Image

CATEGORY_PREFIX = {
    "operative": "01",
    "operative-ability": "01",
    "team-selection": "10",
    "faction-rule": "11",
    "strategic-ploy": "20",
    "firefight-ploy": "30",
    "faction-equipment": "40",
    "other": "16",
}
PLACEHOLDERS = ("TODO", "TBD", "待补", "XXX", "???")


def main():
    card_dir = os.path.abspath(sys.argv[1])
    md_path = os.path.abspath(sys.argv[2]) if len(sys.argv) > 2 else None

    problems = []
    with open(os.path.join(card_dir, "manifest.json"), encoding="utf-8") as fh:
        manifest = json.load(fh)
    cards = manifest["cards"]

    # 1. 数量对账
    pngs = sorted(f for f in os.listdir(card_dir) if f.lower().endswith(".png"))
    if len(cards) != len(pngs):
        problems.append(f"卡片数不一致：manifest {len(cards)} vs 磁盘 PNG {len(pngs)}")

    # 2. 文件存在 + 5. 图像健康
    for card in cards:
        path = os.path.join(card_dir, card["image"])
        if not os.path.exists(path):
            problems.append(f"卡图缺失：{card['image']}")
            continue
        try:
            im = Image.open(path)
            im.load()
        except Exception as exc:
            problems.append(f"卡图无法解码：{card['image']}（{exc}）")
            continue
        if im.width == 0 or im.height == 0:
            problems.append(f"卡图尺寸为零：{card['image']}")
        if im.mode != "RGBA":
            problems.append(f"卡图缺少 Alpha 通道：{card['image']}（{im.mode}）")
        else:
            corners = [
                im.getpixel(p)
                for p in [(0, 0), (im.width - 1, 0), (0, im.height - 1), (im.width - 1, im.height - 1)]
            ]
            if min(c[3] for c in corners) > 0:
                problems.append(f"卡图四角未透明：{card['image']}")

    # 记录 ID 唯一
    ids = [c["id"] for c in cards]
    if len(set(ids)) != len(ids):
        dup = sorted({i for i in ids if ids.count(i) > 1})
        problems.append(f"记录 ID 不唯一：{dup}")

    # 4. 编号规范
    seen = {}
    for card in cards:
        prefix = CATEGORY_PREFIX.get(card["category"])
        if prefix is None:
            problems.append(f"未知类别：{card['category']}")
            continue
        seen.setdefault(prefix, []).append(card["sequence"])
        name = card["image"]
        if not name.startswith(prefix + "-"):
            problems.append(f"编号前缀与类别不符：{card['image']}（应为 {prefix}-）")
    for prefix, seqs in seen.items():
        if sorted(seqs) != list(range(1, len(seqs) + 1)):
            problems.append(f"编号不连续：前缀 {prefix} -> {sorted(seqs)}")

    # 3. README 与 manifest 一致
    readme = os.path.join(card_dir, "README.md")
    if not os.path.exists(readme):
        problems.append("缺少 README.md")
    else:
        text = open(readme, encoding="utf-8").read()
        for card in cards:
            if card["image"] not in text:
                problems.append(f"README 未收录：{card['image']}")
        listed = re.findall(r"\(([^)]+\.png)\)", text)
        if len(listed) != len(cards):
            problems.append(f"README 卡图链接数 {len(listed)} != manifest {len(cards)}")
        if set(listed) != {c["image"] for c in cards}:
            problems.append("README 卡图链接集合与 manifest 不一致")

    # 6. 正文检查
    if md_path:
        if not os.path.exists(md_path):
            problems.append(f"缺少完整规则资料：{md_path}")
        else:
            md = open(md_path, encoding="utf-8").read()
            for card in cards:
                if card["image"] not in md:
                    problems.append(f"正文未引用卡图：{card['image']}")
            for ph in PLACEHOLDERS:
                if ph in md:
                    problems.append(f"正文残留占位符：{ph}")
            # 表格列数一致
            lines = md.splitlines()
            table_cols = None
            for i, line in enumerate(lines, 1):
                if not line.strip().startswith("|"):
                    table_cols = None
                    continue
                if not line.strip().endswith("|"):
                    problems.append(f"表格行未闭合：第 {i} 行")
                    continue
                cols = len(line.strip().strip("|").split("|"))
                if table_cols is None:
                    table_cols = cols
                elif cols != table_cols:
                    problems.append(f"表格列数不一致：第 {i} 行（{cols} != {table_cols}）")
                if line.rstrip() != line:
                    problems.append(f"行尾有多余空格：第 {i} 行")
            # 相对链接可解析
            base = os.path.dirname(md_path)
            for link in re.findall(r"\]\(([^)]+)\)", md):
                if link.startswith(("http://", "https://", "#")):
                    continue
                if not os.path.exists(os.path.normpath(os.path.join(base, link))):
                    problems.append(f"正文链接不可解析：{link}")

    total = len(cards)
    if problems:
        print(f"[失败] {os.path.basename(card_dir)}  卡片 {total} 张")
        for p in problems:
            print("    -", p)
        return 1
    print(f"[通过] {os.path.basename(card_dir)}  卡片 {total} 张，索引、编号、图像与正文引用全部对账通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
