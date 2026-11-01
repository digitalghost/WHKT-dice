#!/usr/bin/env python3
"""Design the Kill Team inch-gauge sheet: three merged tools on one A4 landscape page.

The previous sheet shipped eight separate straight rulers (1/2/3/4/5/6/8/10 inch)
on top of a heavy sci-fi background.  This redesign keeps every distance that the
rules actually ask you to measure, but merges them into three physical tools:

    1. MASTER RULER  0-11 inch, quarter-inch ticks, millimetre edge on top
                      -> covers every common distance in one straight edge.
    2. 3-4-5 TRIANGLE  legs 3" and 4", hypotenuse 5"
                      -> merges the sprint / jump / heavy-move lengths.
    3. CONCENTRIC TEMPLATE  radius 1" and 2"
                      -> merges control range and reaction-move / deployment
                         clearance into one card.

Everything is drawn as vectors in millimetre space, so the printed gauge is
exact as long as the page is printed at 100% / actual size.
"""

from __future__ import annotations

import math
from pathlib import Path

import fitz

MM_TO_PT = 72.0 / 25.4
INCH_MM = 25.4
PAGE_W_MM = 297.0
PAGE_H_MM = 210.0

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "pdf" / "杀戮小队_A4英寸尺组_打印版.pdf"

CJK_FONT_CANDIDATES = (
    Path("/System/Library/Fonts/STHeiti Medium.ttc"),
    Path("/System/Library/Fonts/Hiragino Sans GB.ttc"),
    Path("/Library/Fonts/Arial Unicode.ttf"),
)
CJK_FONT_NAME = "KtCJK"
LATIN_FONT = "helv"          # PDF base-14, crisp technical numerals
LATIN_BOLD = "hebo"

# --- palette -------------------------------------------------------------
PAPER = (0.965, 0.957, 0.937)       # warm ivory sheet
GRID_MINOR = (0.898, 0.909, 0.922)   # 5 mm blueprint grid
GRID_MAJOR = (0.808, 0.833, 0.855)   # 1 inch blueprint grid
CARD = (1.0, 1.0, 1.0)
CARD_TINT = (0.980, 0.972, 0.957)   # warm card (triangle)
CARD_COOL = (0.973, 0.980, 0.984)   # cool card (template)
CARD_LINE = (0.804, 0.827, 0.851)
INK = (0.102, 0.129, 0.161)
INK_SOFT = (0.353, 0.392, 0.439)
MUTED = (0.549, 0.584, 0.620)
SLATE = (0.129, 0.161, 0.196)
SLATE_SOFT = (0.290, 0.333, 0.376)
ORANGE = (0.878, 0.325, 0.086)
ORANGE_SOFT = (0.945, 0.612, 0.400)
STEEL = (0.208, 0.408, 0.514)
STEEL_SOFT = (0.694, 0.780, 0.824)
SHADOW = (0.055, 0.070, 0.086)

# --- sheet layout (millimetres) -----------------------------------------
MARGIN = 8.0
FRAME = (4.5, 4.5, PAGE_W_MM - 4.5, PAGE_H_MM - 4.5)

HEADER = (MARGIN, 7.0, PAGE_W_MM - MARGIN, 28.5)
INSTR = (MARGIN, 30.8, PAGE_W_MM - MARGIN, 37.0)

MASTER_X = 8.8
MASTER_Y = 40.0
MASTER_W = 9 * INCH_MM           # 228.6 mm: longest distance the rules ever ask for
MASTER_H = 18.0                 # slim strip, no title band

KIT_X = 242.0
KIT_W = PAGE_W_MM - MARGIN - 242.0
KIT_H = MASTER_H

ROW_Y = 62.0
ROW_H = 123.0
TRI_X, TRI_W = 8.8, 87.6
CIRC_X, CIRC_W = 100.0, 114.0
REF_X, REF_W = 218.0, PAGE_W_MM - MARGIN - 218.0

FOOTER = (MARGIN, 187.0, PAGE_W_MM - MARGIN, 203.0)

# What the strip's inch marks mean in game terms - the ruler explains itself.
STRIP_NOTES = (
    (1, "控制范围"),
    (2, "反应移动"),
    (3, "冲刺"),
    (4, "跳跃"),
    (6, "移动"),
    (8, "近程射程"),
    (9, "远射程"),
)


def pt(value: float) -> float:
    return value * MM_TO_PT


_CJK_METRIC: fitz.Font | None = None


def P(x: float, y: float) -> fitz.Point:
    return fitz.Point(pt(x), pt(y))


def R(x: float, y: float, w: float, h: float) -> fitz.Rect:
    return fitz.Rect(pt(x), pt(y), pt(x + w), pt(y + h))


def line(page, x1, y1, x2, y2, color, width=0.2) -> None:
    page.draw_line(P(x1, y1), P(x2, y2), color=color, width=pt(width), overlay=True)


def rect(page, x, y, w, h, color=None, fill=None, width=0.2, fill_opacity=1.0) -> None:
    page.draw_rect(
        R(x, y, w, h),
        color=color,
        fill=fill,
        width=pt(width),
        fill_opacity=fill_opacity,
        overlay=True,
    )


def circle(page, cx, cy, radius, color=None, fill=None, width=0.2, fill_opacity=1.0) -> None:
    page.draw_circle(
        P(cx, cy),
        pt(radius),
        color=color,
        fill=fill,
        width=pt(width),
        fill_opacity=fill_opacity,
        overlay=True,
    )


def text_width(txt: str, size: float, font: str = CJK_FONT_NAME) -> float:
    """Width of a text run in millimetres."""
    if font == CJK_FONT_NAME:
        points = _CJK_METRIC.text_length(txt, fontsize=size)
    else:
        points = fitz.get_text_length(txt, fontname=font, fontsize=size)
    return points * 25.4 / 72.0


def resolve_font(txt: str, font: str) -> str:
    """Base-14 Helvetica has no CJK / double-prime glyphs: fall back when needed."""
    if font in (LATIN_FONT, LATIN_BOLD) and any(ord(ch) > 0x7F for ch in txt):
        return CJK_FONT_NAME
    return font


def label(
    page,
    x: float,
    y: float,
    txt: str,
    size: float = 6.0,
    font: str = CJK_FONT_NAME,
    color=INK,
    align: str = "left",
    rotate: int = 0,
    morph=None,
    limit: float | None = None,
) -> float:
    """Draw a single line of text with its baseline at (x, y).  Returns its width."""
    font = resolve_font(txt, font)
    width = text_width(txt, size, font)
    if limit is not None and width > limit:
        raise RuntimeError(f"Text overflows ({width:.1f} > {limit:.1f} mm): {txt!r}")
    if align == "right":
        x -= width
    elif align == "center":
        x -= width / 2.0
    page.insert_text(
        P(x, y),
        txt,
        fontname=font,
        fontsize=size,
        color=color,
        rotate=rotate,
        morph=morph,
        overlay=True,
    )
    return width


def label_vertical(page, x_baseline: float, y_center: float, txt: str, size: float, color=INK) -> None:
    """Rotated-90 label: ink sits to the right of the baseline and runs upward."""
    width = text_width(txt, size, LATIN_FONT)
    label(page, x_baseline, y_center + width / 2.0, txt, size, font=LATIN_FONT, color=color, rotate=90)


# --------------------------------------------------------------------------
# background
# --------------------------------------------------------------------------
def draw_paper(page) -> None:
    rect(page, 0, 0, PAGE_W_MM, PAGE_H_MM, fill=PAPER)

    # gentle top-lit paper gradient (stacked hairlines keep it print friendly)
    for index in range(14):
        band = PAGE_H_MM / 14.0
        rect(page, 0, index * band, PAGE_W_MM, band, fill=(1, 1, 1), fill_opacity=0.013 * (1 - index / 14.0))

    for i in range(1, int(PAGE_W_MM)):
        major = abs(i / INCH_MM - round(i / INCH_MM)) < 1e-6
        if major:
            line(page, i, 0, i, PAGE_H_MM, GRID_MAJOR, 0.22)
        elif i % 5 == 0:
            line(page, i, 0, i, PAGE_H_MM, GRID_MINOR, 0.12)

    for j in range(1, int(PAGE_H_MM)):
        major = abs(j / INCH_MM - round(j / INCH_MM)) < 1e-6
        if major:
            line(page, 0, j, PAGE_W_MM, j, GRID_MAJOR, 0.22)
        elif j % 5 == 0:
            line(page, 0, j, PAGE_W_MM, j, GRID_MINOR, 0.12)

    # technical sheet border + corner registration crosses
    x0, y0, x1, y1 = FRAME
    page.draw_rect(
        fitz.Rect(pt(x0), pt(y0), pt(x1), pt(y1)),
        color=ORANGE,
        width=pt(0.3),
        overlay=True,
    )
    for cx, cy, sx, sy in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        line(page, cx, cy, cx + 3.0 * sx, cy, ORANGE, 0.3)
        line(page, cx, cy, cx, cy + 3.0 * sy, ORANGE, 0.3)


def card(page, x, y, w, h, fill=CARD, border=CARD_LINE, shadow=True) -> None:
    if shadow:
        rect(page, x + 0.45, y + 0.5, w, h, fill=SHADOW, fill_opacity=0.07)
    rect(page, x, y, w, h, color=border, fill=fill, width=0.22)


def crop_marks(page, x, y, w, h, size=2.6, color=ORANGE) -> None:
    """Corner crop marks so the sheet can be trimmed cleanly."""
    for cx, cy, sx, sy in (
        (x, y, 1, 1),
        (x + w, y, -1, 1),
        (x, y + h, 1, -1),
        (x + w, y + h, -1, -1),
    ):
        line(page, cx + 0.9 * sx, cy, cx + (0.9 + size) * sx, cy, color, 0.35)
        line(page, cx, cy + 0.9 * sy, cx, cy + (0.9 + size) * sy, color, 0.35)


# --------------------------------------------------------------------------
# header + instruction strip
# --------------------------------------------------------------------------
def draw_header(page) -> None:
    x, y, w, h = HEADER
    rect(page, x + 0.45, y + 0.5, w, h, fill=SHADOW, fill_opacity=0.09)
    rect(page, x, y, w, h, fill=SLATE)
    rect(page, x, y, 2.4, h, fill=ORANGE)

    # reticle mark
    cx, cy = 24.0, y + h / 2
    circle(page, cx, cy, 4.6, color=ORANGE, width=0.35)
    circle(page, cx, cy, 1.05, fill=ORANGE)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        line(page, cx + 5.4 * dx, cy + 5.4 * dy, cx + 7.6 * dx, cy + 7.6 * dy, ORANGE, 0.35)

    # viewfinder brackets in the header band
    for bx, by, sx, sy in ((x + 8.6, y + 3.0, 1, 1), (x + w - 3.0, y + h - 3.0, -1, -1)):
        line(page, bx, by, bx + 4.0 * sx, by, SLATE_SOFT, 0.25)
        line(page, bx, by, bx, by + 4.0 * sy, SLATE_SOFT, 0.25)

    label(page, 36.0, y + 9.6, "杀戮小队 · 英寸尺组", 15.5, color=(1, 1, 1))
    label(page, 36.6, y + 16.6, "KILL TEAM FIELD GAUGES · 三件套 · 1″ = 25.4 mm", 5.9, color=ORANGE_SOFT)

    rx = x + w - 12.0
    label(page, rx, y + 9.6, "打印：实际大小 / 100%", 8.6, color=(1, 1, 1), align="right")
    label(page, rx, y + 16.4, "关闭『适应页面』与自动缩放 · A4 横向", 5.4, color=STEEL_SOFT, align="right")


def draw_instruction(page) -> None:
    x, y, w, h = INSTR
    card(page, x, y, w, h, shadow=False)
    rect(page, x, y, 1.3, h, fill=ORANGE)
    label(
        page,
        x + 5.0,
        y + 4.3,
        "沿四角橙色标记剪下三件工具  ·  先用右下角 50 mm 校准线核对打印比例  ·  "
        "1″–9″ 覆盖全部常用距离  ·  三角尺合并 3″/4″/5″  ·  圆模板合并 1″/2″",
        5.4,
        color=INK_SOFT,
        limit=w - 9.0,
    )


# --------------------------------------------------------------------------
# tool 1: master ruler
# --------------------------------------------------------------------------
def draw_master_ruler(page) -> None:
    x, y, w, h = MASTER_X, MASTER_Y, MASTER_W, MASTER_H
    card(page, x, y, w, h)
    crop_marks(page, x, y, w, h)

    top, bottom = y, y + h

    # ---- millimetre edge (top) -----------------------------------------
    for mm in range(0, int(w) + 1):
        px = x + mm
        if mm % 10 == 0:
            line(page, px, top, px, top + 2.4, STEEL, 0.28)
        elif mm % 5 == 0:
            line(page, px, top, px, top + 1.6, STEEL, 0.2)
        else:
            line(page, px, top, px, top + 0.9, STEEL_SOFT, 0.12)
    for mm in range(0, int(w) + 1, 20):
        if mm == 0:
            label(page, x + 1.0, top + 4.3, "0", 4.4, font=LATIN_FONT, color=STEEL)
        else:
            label(page, x + mm, top + 4.3, str(mm), 4.4, font=LATIN_FONT, color=STEEL)
    label(page, x + w - 1.0, top + 4.3, f"{w:.1f}", 4.4, font=LATIN_FONT, color=ORANGE, align="right")

    # ---- meaning band: every marked inch states what it is used for -----
    label(page, x + 2.0, top + 8.1, "1″ = 25.4 mm", 5.0, font=LATIN_FONT, color=INK_SOFT)
    for inch, note in STRIP_NOTES:
        px = x + inch * INCH_MM
        if inch == 9:
            label(page, px - 1.2, top + 8.1, note, 4.8, color=ORANGE_SOFT, align="right")
        else:
            label(page, px, top + 8.1, note, 4.8, color=ORANGE_SOFT, align="center")

    # ---- inch edge (bottom) --------------------------------------------
    quarters = int(round(w / INCH_MM * 4))
    for i in range(quarters + 1):
        px = x + i * (INCH_MM / 4.0)
        whole = i % 4 == 0
        if whole:
            length, width, color = 5.4, 0.42, ORANGE if i in (0, quarters) else INK
        elif i % 2 == 0:
            length, width, color = 3.4, 0.26, INK_SOFT
        else:
            length, width, color = 2.0, 0.18, INK_SOFT
        line(page, px, bottom, px, bottom - length, color, width)
    for inch in range(10):
        px = x + inch * INCH_MM
        if inch == 0:
            anchor, align = px + 1.0, "left"
        elif inch == 9:
            anchor, align = px - 1.0, "right"
        else:
            anchor, align = px, "center"
        label(
            page,
            anchor,
            bottom - 6.6,
            str(inch),
            6.2,
            font=LATIN_FONT,
            color=ORANGE if inch in (0, 9) else INK,
            align=align,
        )


def draw_kit_list(page) -> None:
    """Miniature legend: a symbol for each tool plus what it covers."""
    x, y, w, h = KIT_X, MASTER_Y, KIT_W, KIT_H
    card(page, x, y, w, h, fill=CARD_COOL, shadow=False)

    rows = (
        ("ruler", "9″ 主尺", "0–9″ · ¼″ 刻度"),
        ("triangle", "3-4-5 三角尺", "3″ / 4″ / 5″"),
        ("rings", "同心圆模板", "R 1″ / R 2″"),
    )
    for index, (kind, name, detail) in enumerate(rows):
        row_y = y + 5.2 + index * 4.9
        gx, gy = x + 5.4, row_y - 1.3
        if kind == "ruler":
            rect(page, gx - 4.2, gy - 1.2, 8.4, 2.4, color=INK_SOFT, fill=None, width=0.2)
            for k in range(5):
                line(page, gx - 4.2 + k * 2.1, gy - 1.2, gx - 4.2 + k * 2.1, gy + 0.1, INK_SOFT, 0.15)
        elif kind == "triangle":
            page.draw_polyline(
                [P(gx - 3.4, gy + 1.2), P(gx + 3.4, gy + 1.2), P(gx - 3.4, gy - 2.4)],
                color=INK_SOFT,
                width=pt(0.25),
                closePath=True,
                overlay=True,
            )
        else:
            circle(page, gx, gy - 0.4, 2.5, color=INK_SOFT, width=0.22)
            circle(page, gx, gy - 0.4, 1.25, color=ORANGE, width=0.25)
        label(page, x + 11.4, row_y, name, 5.2, color=INK)
        label(page, x + w - 3.4, row_y - 0.1, detail, 4.8, color=MUTED, align="right")


def clip_segment(p0: tuple[float, float], d: tuple[float, float], box: tuple[float, float, float, float]):
    """Liang-Barsky clip of an infinite line to a rectangle (Liang-Barsky box)."""
    x_min, y_min, x_max, y_max = box
    t0, t1 = 0.0, float("inf")
    for p, q in (
        (-d[0], p0[0] - x_min),
        (d[0], x_max - p0[0]),
        (-d[1], p0[1] - y_min),
        (d[1], y_max - p0[1]),
    ):
        if p == 0:
            if q < 0:
                return None
        else:
            r = q / p
            if p < 0:
                if r > t1:
                    return None
                t0 = max(t0, r)
            else:
                if r < t0:
                    return None
                t1 = min(t1, r)
    if t0 > t1:
        return None
    return (p0[0] + d[0] * t0, p0[1] + d[1] * t0, p0[0] + d[0] * t1, p0[1] + d[1] * t1)


# --------------------------------------------------------------------------
# tool 2: 3-4-5 triangle
# --------------------------------------------------------------------------
def draw_triangle(page) -> None:
    x, y, w, h = TRI_X, ROW_Y, TRI_W, ROW_H
    card(page, x, y, w, h, fill=CARD_TINT)
    crop_marks(page, x, y, w, h)

    ax, ay = x + 7.7, y + h - 9.5          # right angle corner (origin)
    bx, by = ax + 3 * INCH_MM, ay          # 3" along the bottom
    cx, cy = ax, ay - 4 * INCH_MM          # 4" up the left edge
    hyp = math.hypot(bx - cx, by - cy)     # == 5"
    slope = (ay - cy) / (bx - ax)          # 4" rise over 3" run

    # The gauge face itself stays completely empty: the drafting hatch only
    # fills the open corner *outside* the hypotenuse, so nothing can ever be
    # printed on top of the ruler.
    ang = math.atan2(by - cy, bx - cx)
    cos_a, sin_a = math.cos(ang), math.sin(ang)
    outx, outy = sin_a, -cos_a             # outward normal (up-right)
    for k in range(1, 6):
        offset = k * (INCH_MM / 2.0)
        seg = clip_segment(
            (cx + outx * offset, cy + outy * offset),
            (cos_a, sin_a),
            (x, y + 24.0, x + w, y + h - 13.0),
        )
        if seg:
            line(page, seg[0], seg[1], seg[2], seg[3], GRID_MINOR, 0.12)

    # triangle body
    page.draw_polyline(
        [P(ax, ay), P(bx, by), P(cx, cy)],
        color=INK,
        width=pt(0.4),
        closePath=True,
        overlay=True,
    )
    page.draw_polyline(
        [P(ax, ay), P(bx, by), P(cx, cy)],
        color=(0.90, 0.86, 0.79),
        fill=(0.996, 0.988, 0.972),
        width=pt(0.4),
        closePath=True,
        fill_opacity=1.0,
        overlay=True,
    )

    # ---- 3" edge (bottom) ----------------------------------------------
    for q in range(13):
        px = ax + q * (INCH_MM / 4.0)
        if q % 4 == 0:
            length, width, color = 4.6, 0.34, ORANGE if q in (0, 12) else INK
        elif q % 2 == 0:
            length, width, color = 2.8, 0.22, INK_SOFT
        else:
            length, width, color = 1.6, 0.16, INK_SOFT
        line(page, px, ay, px, ay - length, color, width)
    for half in range(0, 7):
        label(
            page,
            ax + half * (INCH_MM / 2.0),
            ay + 3.6,
            f"{half / 2:g}",
            5.4,
            font=LATIN_FONT,
            color=ORANGE if half in (0, 6) else INK,
            align="center",
        )

    # ---- 4" edge (left, labels rotated to read bottom-to-top) ----------
    for q in range(17):
        py = ay - q * (INCH_MM / 4.0)
        if q % 4 == 0:
            length, width, color = 4.6, 0.34, ORANGE if q in (0, 16) else INK
        elif q % 2 == 0:
            length, width, color = 2.8, 0.22, INK_SOFT
        else:
            length, width, color = 1.6, 0.16, INK_SOFT
        line(page, ax, py, ax + length, py, color, width)
    for half in range(1, 9):
        label_vertical(
            page,
            ax - 2.5,
            ay - half * (INCH_MM / 2.0),
            f"{half / 2:g}",
            5.4,
            color=ORANGE if half == 8 else INK,
        )

    # ---- 5" hypotenuse -------------------------------------------------
    morph = fitz.Matrix(cos_a, sin_a, -sin_a, cos_a, 0, 0)
    inx, iny = -sin_a, cos_a               # inward normal (down-left)
    for q in range(21):
        t = q / 20.0
        px = cx + (bx - cx) * t
        py = cy + (by - cy) * t
        if q % 4 == 0:
            length, width, color = 4.2, 0.32, ORANGE if q in (0, 20) else INK
        elif q % 2 == 0:
            length, width, color = 2.6, 0.2, INK_SOFT
        else:
            length, width, color = 1.5, 0.15, INK_SOFT
        line(page, px, py, px + inx * length, py + iny * length, color, width)
    for inch in range(1, 5):
        t = inch / 5.0
        px = cx + (bx - cx) * t
        py = cy + (by - cy) * t
        bx_lab, by_lab = px + inx * 7.0, py + iny * 7.0
        pivot = fitz.Point(pt(bx_lab), pt(by_lab))
        label(
            page,
            bx_lab,
            by_lab + 0.8,
            str(inch),
            5.8,
            font=LATIN_FONT,
            color=INK,
            align="center",
            morph=(pivot, morph),
        )

    # ---- captions ------------------------------------------------------
    label(page, x + w - 4.0, y + 7.0, "3-4-5 直角三角尺", 9.0, color=INK, align="right")
    label(page, x + w - 4.0, y + 10.6, "TRIANGLE · 3″ 4″ 5″", 5.4, font=LATIN_FONT, color=ORANGE, align="right")
    label(
        page,
        x + w - 4.0,
        y + 15.4,
        "三条边都可量：底边 3″、左边 4″、斜边 5″",
        5.0,
        color=MUTED,
        align="right",
        limit=w - 10.0,
    )
    label(
        page,
        x + w - 4.0,
        y + 20.2,
        "沿三角形三边读数 · 四角橙标为裁切线",
        5.0,
        color=MUTED,
        align="right",
        limit=w - 10.0,
    )


# --------------------------------------------------------------------------
# tool 3: concentric radius template
# --------------------------------------------------------------------------
def draw_template(page) -> None:
    x, y, w, h = CIRC_X, ROW_Y, CIRC_W, ROW_H
    card(page, x, y, w, h, fill=CARD_COOL)
    crop_marks(page, x, y, w, h)

    label(page, x + 11.0, y + 6.2, "同心圆模板 · 1″ / 2″", 9.0, color=INK)
    label(page, x + w - 4.0, y + 6.0, "RADIUS TEMPLATE", 5.4, font=LATIN_FONT, color=ORANGE, align="right")
    line(page, x + 3.0, y + 8.4, x + w - 3.0, y + 8.4, GRID_MAJOR, 0.2)

    ccx = x + w / 2.0
    ccy = y + 62.0
    r_out = 2 * INCH_MM
    r_in = INCH_MM

    # faint construction rings
    for r in (0.5 * INCH_MM, 1.5 * INCH_MM):
        circle(page, ccx, ccy, r, color=STEEL_SOFT, width=0.16)

    # crosshair
    line(page, ccx - r_out - 1.8, ccy, ccx + r_out + 1.8, ccy, STEEL_SOFT, 0.16)
    line(page, ccx, ccy - r_out - 1.8, ccx, ccy + r_out + 1.8, STEEL_SOFT, 0.16)

    # radial tick ladder on the right-hand arm
    for half in range(1, 5):
        r = half * (INCH_MM / 2.0)
        line(page, ccx + r, ccy - 1.5, ccx + r, ccy + 1.5, STEEL, 0.2)
        if half % 2 == 0:
            label(page, ccx + r, ccy - 2.6, f"{half // 2}", 4.6, font=LATIN_FONT, color=STEEL, align="center")

    # measuring circles
    circle(page, ccx, ccy, r_out, color=INK, width=0.42)
    circle(page, ccx, ccy, r_in, color=ORANGE, width=0.46)
    line(page, ccx - 1.3, ccy, ccx + 1.3, ccy, INK, 0.25)
    line(page, ccx, ccy - 1.3, ccx, ccy + 1.3, INK, 0.25)

    # inner label: 1" control radius
    label(page, ccx, ccy - 4.6, "1″", 11.0, color=ORANGE, align="center")
    label(page, ccx, ccy + 3.2, "控制范围", 5.4, color=INK, align="center")
    label(page, ccx, ccy + 6.2, "CONTROL RADIUS", 4.4, font=LATIN_FONT, color=MUTED, align="center")

    # outer label: 2" reaction / deployment clearance
    label(page, ccx, ccy + 32.0, "2″", 10.0, color=INK, align="center")
    label(page, ccx, ccy + 36.6, "反应移动上限 · 冲锋增量", 5.2, color=INK_SOFT, align="center")
    label(page, ccx, ccy + 40.2, "装备部署净空（门 / 墙 / 铁丝网 / 地雷）", 5.0, color=MUTED, align="center")
    label(page, ccx, ccy + 45.4, "圆心对准特工底座中心 · 圆周即 1″ / 2″ 边界", 5.0, color=MUTED, align="center")


# --------------------------------------------------------------------------
# quick reference column
# --------------------------------------------------------------------------
REFERENCE_ROWS = (
    ("1″", "控制范围 · 争夺标识", False),
    ("2″", "反应移动上限 · 冲锋增量", False),
    ("2″", "装备部署净空（门 / 墙 / 铁丝网）", True),
    ("3″", "冲刺附加距离 · 攀爬触及上限", False),
    ("4″", "跳跃距离上限 · 最低移动量", False),
    ("5″", "重装角色移动距离", False),
    ("6″", "常见移动距离 · 信标间距", False),
    ("8″", "常见近程射程", False),
    ("9″", "部分武器远射程", False),
)


def draw_reference(page) -> None:
    x, y, w, h = REF_X, ROW_Y, REF_W, ROW_H
    card(page, x, y, w, h)
    crop_marks(page, x, y, w, h)

    label(page, x + 11.0, y + 6.2, "常用距离速查", 9.0, color=INK)
    label(page, x + w - 4.0, y + 6.0, "QUICK REF", 5.4, font=LATIN_FONT, color=ORANGE, align="right")
    line(page, x + 3.0, y + 8.4, x + w - 3.0, y + 8.4, GRID_MAJOR, 0.2)

    row_y = y + 13.6
    row_h = 8.4
    for index, (value, desc, muted) in enumerate(REFERENCE_ROWS):
        if value:
            label(page, x + 4.6, row_y + 4.0, value, 7.4, color=MUTED if muted else ORANGE)
        label(page, x + 14.6, row_y + 3.8, desc, 5.2, color=INK_SOFT if muted else INK, limit=w - 18.0)
        if index < len(REFERENCE_ROWS) - 1:
            line(page, x + 4.0, row_y + row_h, x + w - 4.0, row_y + row_h, GRID_MINOR, 0.14)
        row_y += row_h

    note_y = row_y + 3.0
    rect(page, x + 3.6, note_y, w - 7.2, 25.0, fill=(0.972, 0.980, 0.986))
    rect(page, x + 3.6, note_y, 1.0, 25.0, fill=STEEL)
    label(page, x + 6.4, note_y + 5.4, "底座尺寸", 5.6, color=INK)
    label(page, x + 6.4, note_y + 9.8, "25 / 32 / 40 mm → 主尺上沿毫米刻度", 5.0, color=INK_SOFT, limit=w - 11.0)
    label(page, x + 6.4, note_y + 15.2, "判定口径", 5.6, color=INK)
    label(page, x + 6.4, note_y + 19.6, "位于 = 任一部分在圈内；完全位于 = 全部在圈内", 5.0, color=INK_SOFT, limit=w - 11.0)


# --------------------------------------------------------------------------
# footer
# --------------------------------------------------------------------------
def draw_footer(page) -> None:
    x, y, w, h = FOOTER
    card(page, x, y, w, h, shadow=False)
    rect(page, x, y, 2.4, h, fill=ORANGE)

    label(page, x + 6.0, y + 6.4, "打印：实际大小 / 100%  ·  关闭『适应页面』与自动缩放  ·  建议使用普通 A4 纸", 5.4, color=INK)
    label(page, x + 6.0, y + 12.0, "非官方玩家辅助工具，以当前规则文本与任务包为准  ·  1″ = 25.4 mm  ·  2026-10", 5.0, color=MUTED)

    # calibration bars
    cal_x = x + 148.0
    label(page, cal_x, y + 5.6, "打印校准线", 5.2, color=INK)
    line(page, cal_x, y + 11.4, cal_x + 50.0, y + 11.4, INK, 0.4)
    for end in (cal_x, cal_x + 50.0):
        line(page, end, y + 8.8, end, y + 14.0, INK, 0.4)
    label(page, cal_x + 25.0, y + 17.4, "50 mm（应为 50 mm）", 4.8, color=MUTED, align="center")

    cal2_x = cal_x + 58.0
    label(page, cal2_x, y + 5.6, "1 英寸", 5.2, color=INK)
    line(page, cal2_x, y + 11.4, cal2_x + INCH_MM, y + 11.4, INK, 0.4)
    for end in (cal2_x, cal2_x + INCH_MM):
        line(page, end, y + 8.8, end, y + 14.0, INK, 0.4)
    label(page, cal2_x + INCH_MM / 2, y + 17.4, "25.4 mm", 4.8, color=MUTED, align="center")


# --------------------------------------------------------------------------
def build() -> Path:
    global _CJK_METRIC

    font_file = next((p for p in CJK_FONT_CANDIDATES if p.exists()), None)
    if font_file is None:
        raise FileNotFoundError("No CJK font available")
    _CJK_METRIC = fitz.Font(fontfile=str(font_file))

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    document = fitz.open()
    page = document.new_page(width=pt(PAGE_W_MM), height=pt(PAGE_H_MM))
    page.insert_font(fontname=CJK_FONT_NAME, fontfile=str(font_file))

    draw_paper(page)
    draw_header(page)
    draw_instruction(page)
    draw_master_ruler(page)
    draw_kit_list(page)
    draw_triangle(page)
    draw_template(page)
    draw_reference(page)
    draw_footer(page)

    document.set_metadata(
        {
            "title": "杀戮小队 英寸尺组（A4 打印版）",
            "subject": "A4 landscape exact-size gauges: master ruler, 3-4-5 triangle, radius template",
            "keywords": "杀戮小队, Kill Team, 英寸尺, A4, 打印, 三角尺, 模板",
            "creator": "PyMuPDF",
        }
    )
    document.subset_fonts(verbose=False)
    document.save(OUTPUT, garbage=4, deflate=True, clean=True)
    document.close()
    return OUTPUT


if __name__ == "__main__":
    print(build())
