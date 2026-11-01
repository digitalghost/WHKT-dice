#!/usr/bin/env python3
"""从 PDF 矢量几何中识别独立卡片边界。

原理
----
杀戮小队 PDF 的每张卡片都由「虚线裁切标记」围合：

  * 竖直虚线位于卡片左右外缘（裁切定位线的竖向部分）；
  * 水平虚线位于卡片上下外缘约 5.6 pt 处（裁切定位线的横向部分）；
  * 竖直虚线的 y 范围 = 卡片实体框高度。

版式由此自然分解为「列带 × 行带」：

  * 唯一竖直虚线 x 坐标 -> 相邻两两配对 = 列带（1x1、1xN、2x2 都适用）；
  * 每列带内、且落在该列 x 区间内的水平虚线 y 坐标 -> 行带边界。

因此不需要猜测文本包围框，也不需要「每页固定四格」之类的经验假设。
输出精确到 PDF 点坐标的 bbox_pt 与跨 DPI 可复现的 bbox_norm。

用法：
  python3 tools/detect_card_frames.py <pdf> [--json out.json]
"""
import argparse
import json
import sys

import pdfplumber

MIN_BAND_WIDTH = 60.0   # 小于此宽度视为装饰线，不做列带
MIN_BAND_HEIGHT = 40.0  # 小于此高度视为装饰线，不做行带
CUT_LINE_INSET = 14.0   # 水平裁切线相对列带的允许内缩量


def is_dashed(obj):
    dash = obj.get("dash")
    if not dash:
        return False
    pattern = dash[0] if isinstance(dash, (list, tuple)) and dash else dash
    try:
        pattern = [float(v) for v in pattern]
    except (TypeError, ValueError):
        return False
    return len(pattern) >= 2 and sum(pattern) > 0


def collect(page):
    """返回 (竖直虚线, 水平虚线)。"""
    verticals, horizontals = [], []
    for obj in page.lines:
        if not is_dashed(obj):
            continue
        x0, x1 = sorted((obj["x0"], obj["x1"]))
        y0, y1 = sorted((obj["y0"], obj["y1"]))
        if abs(x1 - x0) < 0.5 and abs(y1 - y0) > 20:
            verticals.append((round(x0, 1), round(y0, 1), round(y1, 1)))
        elif abs(y1 - y0) < 0.5 and abs(x1 - x0) > 20:
            horizontals.append((round(y0, 1), round(x0, 1), round(x1, 1)))
    return verticals, horizontals


def is_valid_card_step(d: float) -> bool:
    """杀戮小队标准塔罗牌尺寸：短边 ~198.4pt (70mm)，长边 ~343.0pt (121mm)。"""
    return (196.5 <= d <= 200.5) or (341.0 <= d <= 345.0)


def chain_coords(coords) -> list[float]:
    """在候选虚线坐标中找出以标准卡片宽高步进的最长坐标链，过滤卡内表格/图示虚线。"""
    coords = sorted(set(coords))
    best: list[float] = []
    for i, start in enumerate(coords):
        cur = [start]
        for x in coords[i + 1 :]:
            if is_valid_card_step(x - cur[-1]):
                cur.append(x)
        if len(cur) > len(best):
            best = cur
    return best if len(best) >= 2 else []


def column_bands(verticals):
    """唯一竖直虚线 x -> 相邻列带 [(x0, x1), ...]。"""
    xs = chain_coords([x for x, _, _ in verticals])
    return [(xs[i], xs[i + 1]) for i in range(len(xs) - 1)]


def row_bands(horizontals, band):
    """某列带内的水平裁切线 y -> 行边界列表。"""
    x0, x1 = band
    ys = chain_coords(
        [
            y
            for y, lx0, lx1 in horizontals
            if lx1 >= x0 - CUT_LINE_INSET and lx0 <= x1 + CUT_LINE_INSET
        ]
    )
    return ys


def analyze(pdf_path):
    result = []
    with pdfplumber.open(pdf_path) as pdf:
        for pno, page in enumerate(pdf.pages, 1):
            verticals, horizontals = collect(page)
            bands = column_bands(verticals)
            cards = []
            for x0, x1 in bands:
                ys = row_bands(horizontals, (x0, x1))
                for i in range(len(ys) - 1):
                    y0, y1 = ys[i], ys[i + 1]
                    if y1 - y0 < MIN_BAND_HEIGHT:
                        continue
                    cards.append({"x0": x0, "x1": x1, "y0": y0, "y1": y1})
            cards.sort(key=lambda c: (round(c["y0"], 1), c["x0"]))
            for idx, card in enumerate(cards, 1):
                w = card["x1"] - card["x0"]
                h = card["y1"] - card["y0"]
                card.update(
                    page=pno,
                    order=idx,
                    width=round(w, 2),
                    height=round(h, 2),
                    bbox_pt=[card["x0"], card["y0"], round(w, 2), round(h, 2)],
                    bbox_norm=[
                        round(card["x0"] / page.width, 4),
                        round(card["y0"] / page.height, 4),
                        round(w / page.width, 4),
                        round(h / page.height, 4),
                    ],
                )
            result.append({"page": pno, "width": page.width, "height": page.height, "cards": cards})
    return result


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf")
    ap.add_argument("--json", dest="json_out")
    args = ap.parse_args()

    pages = analyze(args.pdf)
    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as fh:
            json.dump(pages, fh, ensure_ascii=False, indent=2)

    total = 0
    for page in pages:
        cards = page["cards"]
        total += len(cards)
        sizes = sorted({(c["width"], c["height"]) for c in cards})
        print(f"第 {page['page']:>2} 页  {page['width']:.1f}x{page['height']:.1f}pt  卡片 {len(cards)} 张  尺寸 {sizes}")
        for c in cards:
            print(f"      #{c['order']} ({c['x0']:.1f},{c['y0']:.1f}) {c['width']:.1f}x{c['height']:.1f}pt  纵横比 {c['width']/c['height']:.2f}")
    print(f"合计 {total} 张卡片")


if __name__ == "__main__":
    sys.exit(main())
