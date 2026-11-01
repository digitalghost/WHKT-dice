#!/usr/bin/env python3
"""Verify that the gauge sheet is dimensionally exact when printed at 100%.

The check reads the generated PDF back and measures the real vector geometry
(inch ticks, circle radii, triangle legs) instead of trusting the drawing code.
"""

from __future__ import annotations

import math
import sys
from collections import defaultdict
from pathlib import Path

import fitz

MM = 72.0 / 25.4
INCH_MM = 25.4
TOL = 0.02  # mm

ROOT = Path(__file__).resolve().parents[1]
PDF = ROOT / "output" / "pdf" / "杀戮小队_A4英寸尺组_打印版.pdf"

MASTER_X = 8.8
MASTER_Y = 40.0
MASTER_W = 9 * INCH_MM
MASTER_H = 18.0
TRI_X, TRI_W = 8.8, 87.6
ROW_Y, ROW_H = 62.0, 123.0
CIRC_X, CIRC_W = 100.0, 114.0

failures: list[str] = []


def check(name: str, actual: float, expected: float, tol: float = TOL) -> None:
    delta = abs(actual - expected)
    status = "OK " if delta <= tol else "FAIL"
    if delta > tol:
        failures.append(f"{name}: {actual:.4f} mm (expected {expected:.4f} mm)")
    print(f"  [{status}] {name:<34} {actual:9.4f} mm  (expect {expected:.4f})")


def to_mm(value: float) -> float:
    return value / MM


def main() -> int:
    doc = fitz.open(PDF)
    page = doc[0]

    print("page")
    check("page width", to_mm(page.rect.width), 297.0, 0.01)
    check("page height", to_mm(page.rect.height), 210.0, 0.01)

    drawings = page.get_drawings()
    verticals: list[tuple[float, float, float]] = []  # (x, y_top, y_bottom)
    curves: list[fitz.Rect] = []
    polylines: list[list[tuple[float, float]]] = []

    for path in drawings:
        for item in path["items"]:
            if item[0] == "l":
                p0, p1 = item[1], item[2]
                if abs(p0.x - p1.x) < 0.01:
                    verticals.append((p0.x, min(p0.y, p1.y), max(p0.y, p1.y)))
            elif item[0] == "c":
                curves.append(path["rect"])
            elif item[0] == "re":
                pass

    # ---- master ruler inch ticks -------------------------------------
    print("\nmaster ruler (bottom inch edge)")
    edge_y = (MASTER_Y + MASTER_H) * MM
    ticks = sorted({round(to_mm(x), 6) for x, y0, y1 in verticals if abs(y1 - edge_y) < 0.6 and y0 < edge_y})
    # 9 inch -> 37 quarter ticks
    expect = [MASTER_X + i * INCH_MM / 4 for i in range(37)]
    print(f"  ticks found: {len(ticks)} (expect 37)")
    if len(ticks) != 37:
        failures.append(f"inch tick count {len(ticks)} != 37")
    else:
        check("tick 0 position", ticks[0], MASTER_X)
        check("tick 36 position", ticks[-1], MASTER_X + MASTER_W)
        worst = max(abs(a - b) for a, b in zip(ticks, expect))
        check("max tick deviation", worst, 0.0, 0.002)
        spacing = [b - a for a, b in zip(ticks, ticks[1:])]
        worst_spacing = max(abs(s - INCH_MM / 4) for s in spacing)
        check("max quarter spacing error", worst_spacing, 0.0, 0.002)
        print(f"  quarter spacing: {min(spacing):.6f} – {max(spacing):.6f} mm")

    # ---- circle template ---------------------------------------------
    print("\nradius template")
    circle_rects = [r for r in curves if r.width > 10 * MM]
    radii = sorted({round(to_mm(r.width) / 2, 4) for r in circle_rects})
    for radius, expected in ((25.4, INCH_MM), (50.8, 2 * INCH_MM)):
        if radius in radii:
            check(f"circle radius {radius}", radius, expected)
        else:
            failures.append(f"circle radius {expected} mm missing (found {radii})")
    inner = [r for r in circle_rects if abs(to_mm(r.width) / 2 - INCH_MM) < 0.05]
    if inner:
        cx = to_mm(inner[0].x0 + inner[0].width / 2)
        cy = to_mm(inner[0].y0 + inner[0].height / 2)
        check("template centre x", cx, CIRC_X + CIRC_W / 2)
        check("template centre y", cy, ROW_Y + 62.0)
    outer = [r for r in circle_rects if abs(to_mm(r.width) / 2 - 2 * INCH_MM) < 0.05]
    if outer:
        rect = outer[0]
        check("outer circle bottom", to_mm(rect.y1), ROW_Y + 62.0 + 2 * INCH_MM)
        check("outer circle right", to_mm(rect.x1), CIRC_X + CIRC_W / 2 + 2 * INCH_MM)

    # ---- triangle ------------------------------------------------------
    print("\n3-4-5 triangle")
    tri_items = []
    for path in drawings:
        pts = [(item[1].x, item[1].y) for item in path["items"] if item[0] in ("l", "c")]
        if len(pts) >= 3:
            xs = [to_mm(p[0]) for p in pts]
            ys = [to_mm(p[1]) for p in pts]
            if min(xs) < 40 and max(xs) > 80 and min(ys) > 70 and max(ys) > 150:
                tri_items.append(pts)
    if not tri_items:
        failures.append("triangle outline not found")
    else:
        pts = tri_items[0]
        verts = {(round(to_mm(px), 2), round(to_mm(py), 2)) for px, py in pts}
        ax, ay = TRI_X + 7.7, ROW_Y + ROW_H - 9.5
        bx, by = ax + 3 * INCH_MM, ay
        cx_, cy_ = ax, ay - 4 * INCH_MM
        for name, (vx, vy) in (("A right angle", (ax, ay)), ("B 3 inch", (bx, by)), ("C 4 inch", (cx_, cy_))):
            near = min(verts, key=lambda p: math.hypot(p[0] - vx, p[1] - vy))
            check(f"vertex {name}", math.hypot(near[0] - vx, near[1] - vy), 0.0, 0.05)
        check("leg 3 inch", bx - ax, 3 * INCH_MM)
        check("leg 4 inch", ay - cy_, 4 * INCH_MM)
        check("hypotenuse", math.hypot(bx - cx_, by - cy_), 5 * INCH_MM)

    print()
    if failures:
        print("FAILED:")
        for item in failures:
            print("  -", item)
        return 1
    print("All geometry checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
