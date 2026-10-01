#!/usr/bin/env python3
"""Create an exact-size, image-backed A4 ruler sheet for tabletop play."""

from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
from pathlib import Path

import fitz
from PIL import Image, ImageEnhance


MM_TO_PT = 72.0 / 25.4
INCH_MM = 25.4
PAGE_W_MM = 297.0
PAGE_H_MM = 210.0
BACKGROUND_DPI = 200

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "pdf" / "杀戮小队_A4英寸尺_打印版.pdf"
ASSET_DIR = ROOT / "assets" / "ruler-backgrounds"

FONT_CANDIDATES = (
    Path("/Library/Fonts/Arial Unicode.ttf"),
    Path("/System/Library/Fonts/PingFang.ttc"),
)
FONT_NAME = "RulerCJK"

INK = (0.055, 0.063, 0.070)
PANEL_2 = (0.115, 0.122, 0.128)
ORANGE = (0.95, 0.27, 0.055)
ORANGE_SOFT = (0.94, 0.44, 0.16)
IVORY = (0.96, 0.94, 0.88)
MUTED = (0.55, 0.57, 0.58)
WHITE = (1.0, 1.0, 1.0)

THEMES = {
    "general": ASSET_DIR / "general-command.png",
    "shooting": ASSET_DIR / "long-range-targeting.png",
    "movement": ASSET_DIR / "movement-lane.png",
    "sprint": ASSET_DIR / "sprint-jump.png",
    "control": ASSET_DIR / "close-control.png",
}


@dataclass(frozen=True)
class RulerSpec:
    x_mm: float
    y_mm: float
    inches: int
    height_mm: float
    title: str
    purpose: str
    theme: str
    category: str


RULERS = (
    RulerSpec(21.5, 35.0, 10, 28.0, "10″ 综合直尺", "¼″ 刻度 · 综合测距", "general", "综合"),
    RulerSpec(17.5, 69.5, 8, 27.0, "8″ 射程尺", "常用近程射程", "shooting", "射程"),
    RulerSpec(228.7, 69.5, 2, 27.0, "2″ 反应移动", "反应移动上限 / 冲锋增量", "control", "控制"),
    RulerSpec(17.5, 103.0, 6, 27.0, "6″ 常用移动尺", "标准移动距离", "movement", "移动"),
    RulerSpec(177.9, 103.0, 4, 27.0, "4″ 跳跃尺", "跳跃距离上限", "sprint", "跳跃"),
    RulerSpec(26.2, 136.5, 5, 27.0, "5″ 距离尺", "中距离参考", "general", "综合"),
    RulerSpec(161.2, 136.5, 3, 27.0, "3″ 冲刺尺", "冲刺附加距离", "sprint", "冲刺"),
    RulerSpec(245.4, 136.5, 1, 27.0, "1″ 控制范围", "近距离控制", "control", "控制"),
)


def pt(mm_value: float) -> float:
    return mm_value * MM_TO_PT


def rect_mm(x: float, y: float, width: float, height: float) -> fitz.Rect:
    return fitz.Rect(pt(x), pt(y), pt(x + width), pt(y + height))


def draw_textbox(
    page: fitz.Page,
    x: float,
    y: float,
    width: float,
    height: float,
    text: str,
    size: float,
    color=IVORY,
    align=fitz.TEXT_ALIGN_LEFT,
) -> None:
    remaining = page.insert_textbox(
        rect_mm(x, y, width, height),
        text,
        fontname=FONT_NAME,
        fontsize=size,
        color=color,
        align=align,
        lineheight=1.05,
        overlay=True,
    )
    if remaining < -0.25:
        raise RuntimeError(f"Text did not fit: {text!r} ({remaining:.2f})")


def cover_crop_bytes(path: Path, target_aspect: float, width_mm: float, height_mm: float) -> bytes:
    """Return a quiet, print-ready JPEG crop without distorting the source."""
    with Image.open(path) as source:
        image = source.convert("RGB")
        src_aspect = image.width / image.height
        if src_aspect > target_aspect:
            width = round(image.height * target_aspect)
            left = (image.width - width) // 2
            image = image.crop((left, 0, left + width, image.height))
        elif src_aspect < target_aspect:
            height = round(image.width / target_aspect)
            top = (image.height - height) // 2
            image = image.crop((0, top, image.width, top + height))

        target_size = (
            round(width_mm / INCH_MM * BACKGROUND_DPI),
            round(height_mm / INCH_MM * BACKGROUND_DPI),
        )
        image = image.resize(target_size, Image.Resampling.LANCZOS)
        image = ImageEnhance.Contrast(image).enhance(0.86)
        image = ImageEnhance.Brightness(image).enhance(0.76)
        stream = BytesIO()
        image.save(stream, format="JPEG", quality=88, optimize=True, progressive=True)
        return stream.getvalue()


def draw_cut_corner(page: fitz.Page, x: float, y: float, flip: int = 1) -> None:
    page.draw_line(
        fitz.Point(pt(x), pt(y)),
        fitz.Point(pt(x + 2.0 * flip), pt(y)),
        color=ORANGE,
        width=0.8,
        overlay=True,
    )
    page.draw_line(
        fitz.Point(pt(x), pt(y)),
        fitz.Point(pt(x), pt(y + 2.0)),
        color=ORANGE,
        width=0.8,
        overlay=True,
    )


def draw_title_plate(page: fitz.Page, spec: RulerSpec, width_mm: float) -> None:
    title_width = min(width_mm - 5.0, max(21.0, 8.5 + len(spec.title) * 3.25))
    title_height = 7.2
    center_x = spec.x_mm + width_mm / 2.0
    x = center_x - title_width / 2.0
    y = spec.y_mm + 2.0
    chamfer = 2.1
    points = (
        fitz.Point(pt(x + chamfer), pt(y)),
        fitz.Point(pt(x + title_width - chamfer), pt(y)),
        fitz.Point(pt(x + title_width), pt(y + chamfer)),
        fitz.Point(pt(x + title_width), pt(y + title_height)),
        fitz.Point(pt(x), pt(y + title_height)),
        fitz.Point(pt(x), pt(y + chamfer)),
    )
    page.draw_polyline(
        points,
        color=ORANGE,
        fill=INK,
        width=0.75,
        closePath=True,
        fill_opacity=0.94,
        overlay=True,
    )
    title_size = 6.7 if spec.inches == 1 else 7.2
    draw_textbox(page, x + 1.0, y + 1.25, title_width - 2.0, 4.7, spec.title, title_size, IVORY, fitz.TEXT_ALIGN_CENTER)


def draw_ruler(page: fitz.Page, spec: RulerSpec, image_cache: dict[tuple[str, int], bytes]) -> None:
    width_mm = spec.inches * INCH_MM
    outer = rect_mm(spec.x_mm, spec.y_mm, width_mm, spec.height_mm)
    cache_key = (spec.theme, spec.inches)
    if cache_key not in image_cache:
        image_cache[cache_key] = cover_crop_bytes(
            THEMES[spec.theme],
            width_mm / spec.height_mm,
            width_mm,
            spec.height_mm,
        )
    page.insert_image(outer, stream=image_cache[cache_key], keep_proportion=False, overlay=True)

    # A restrained global veil keeps every source image inside the same visual system.
    page.draw_rect(outer, color=None, fill=INK, fill_opacity=0.13, overlay=True)

    # Small category chip: useful orientation, no competition with measurements.
    chip_w = min(max(13.0, 5.0 + len(spec.category) * 3.2), max(13.0, width_mm - 4.0))
    page.draw_rect(
        rect_mm(spec.x_mm + 2.0, spec.y_mm + 2.0, chip_w, 5.3),
        color=None,
        fill=INK,
        fill_opacity=0.78,
        overlay=True,
    )
    draw_textbox(page, spec.x_mm + 3.0, spec.y_mm + 2.65, chip_w - 2.0, 3.5, spec.category, 5.2, ORANGE_SOFT)

    draw_title_plate(page, spec, width_mm)

    # Purpose caption is omitted on the 1-inch tool where the title already says it all.
    if spec.inches >= 2:
        note_w = min(width_mm - 5.0, max(28.0, 7.0 + len(spec.purpose) * 2.75))
        note_x = spec.x_mm + width_mm - note_w - 2.0
        note_y = spec.y_mm + 10.2
        page.draw_rect(
            rect_mm(note_x, note_y, note_w, 5.0),
            color=None,
            fill=INK,
            fill_opacity=0.72,
            overlay=True,
        )
        draw_textbox(page, note_x + 1.1, note_y + 0.65, note_w - 2.2, 3.4, spec.purpose, 4.8, IVORY, fitz.TEXT_ALIGN_CENTER)

    # Dedicated high-contrast readout layer.
    band_h = 11.7
    band_y = spec.y_mm + spec.height_mm - band_h
    page.draw_rect(
        rect_mm(spec.x_mm, band_y, width_mm, band_h),
        color=None,
        fill=INK,
        fill_opacity=0.94,
        overlay=True,
    )
    page.draw_line(
        fitz.Point(pt(spec.x_mm), pt(band_y)),
        fitz.Point(pt(spec.x_mm + width_mm), pt(band_y)),
        color=ORANGE_SOFT,
        width=0.65,
        overlay=True,
    )

    subdivisions = spec.inches * 4
    for index in range(subdivisions + 1):
        x = spec.x_mm + index * (INCH_MM / 4.0)
        quarter = index % 4
        if quarter == 0:
            tick_len = 6.8
            line_width = 1.05
            tick_color = ORANGE if index in (0, subdivisions) else IVORY
        elif quarter == 2:
            tick_len = 4.8
            line_width = 0.75
            tick_color = IVORY
        else:
            tick_len = 3.25
            line_width = 0.48
            tick_color = IVORY
        page.draw_line(
            fitz.Point(pt(x), pt(spec.y_mm + spec.height_mm)),
            fitz.Point(pt(x), pt(spec.y_mm + spec.height_mm - tick_len)),
            color=tick_color,
            width=line_width,
            overlay=True,
        )

    label_w = min(7.2, width_mm / max(1, spec.inches + 0.3))
    for inch in range(spec.inches + 1):
        center = spec.x_mm + inch * INCH_MM
        label_x = max(spec.x_mm + 0.35, min(center - label_w / 2.0, spec.x_mm + width_mm - label_w - 0.35))
        label_color = ORANGE_SOFT if inch in (0, spec.inches) else IVORY
        draw_textbox(
            page,
            label_x,
            band_y + 0.75,
            label_w,
            3.8,
            str(inch),
            5.9,
            label_color,
            fitz.TEXT_ALIGN_CENTER,
        )

    # The orange centerline of this outline is the exact cut path and exact gauge extent.
    page.draw_rect(outer, color=ORANGE, width=0.9, overlay=True)
    draw_cut_corner(page, spec.x_mm, spec.y_mm)
    draw_cut_corner(page, spec.x_mm + width_mm, spec.y_mm, -1)


def draw_header(page: fitz.Page) -> None:
    page.draw_rect(rect_mm(10.0, 8.0, 277.0, 19.0), color=None, fill=INK, overlay=True)
    page.draw_rect(rect_mm(232.0, 8.0, 55.0, 19.0), color=None, fill=ORANGE, overlay=True)
    page.draw_circle(fitz.Point(pt(20.0), pt(17.5)), pt(5.2), color=ORANGE, width=1.0, overlay=True)
    page.draw_circle(fitz.Point(pt(20.0), pt(17.5)), pt(1.15), color=IVORY, fill=IVORY, width=0, overlay=True)
    for angle in (0, 90, 180, 270):
        if angle == 0:
            start, end = (21.8, 17.5), (25.4, 17.5)
        elif angle == 90:
            start, end = (20.0, 15.7), (20.0, 12.1)
        elif angle == 180:
            start, end = (18.2, 17.5), (14.6, 17.5)
        else:
            start, end = (20.0, 19.3), (20.0, 22.9)
        page.draw_line(
            fitz.Point(pt(start[0]), pt(start[1])),
            fitz.Point(pt(end[0]), pt(end[1])),
            color=ORANGE,
            width=0.8,
            overlay=True,
        )

    draw_textbox(page, 29.0, 9.8, 150.0, 8.2, "杀戮小队 · A4 英寸尺", 16.0, WHITE)
    draw_textbox(page, 29.0, 18.0, 180.0, 4.0, "KILL TEAM / FIELD GAUGES · ¼″ READOUT", 5.7, ORANGE_SOFT)
    draw_textbox(page, 29.0, 22.1, 180.0, 3.5, "A4 横向 297 × 210 mm  |  1″ = 25.4 mm", 5.2, (0.78, 0.80, 0.81))
    draw_textbox(page, 235.0, 11.0, 49.0, 5.0, "打印：实际大小 / 100%", 8.7, WHITE, fitz.TEXT_ALIGN_CENTER)
    draw_textbox(page, 236.0, 19.0, 47.0, 3.8, "关闭适应页面与自动缩放", 5.0, WHITE, fitz.TEXT_ALIGN_CENTER)
    draw_textbox(page, 10.0, 29.0, 277.0, 4.2, "沿橙色外框中心裁切；请先用底部 50 mm 校准线核对打印比例。", 6.0, MUTED, fitz.TEXT_ALIGN_CENTER)


def draw_footer(page: fitz.Page) -> None:
    y = 170.0
    page.draw_rect(rect_mm(10.0, y, 277.0, 31.5), color=PANEL_2, fill=(0.965, 0.965, 0.955), width=0.6, overlay=True)
    page.draw_rect(rect_mm(10.0, y, 3.0, 31.5), color=None, fill=ORANGE, overlay=True)
    draw_textbox(page, 17.0, y + 3.0, 170.0, 4.2, "规则速记", 7.0, INK)
    draw_textbox(page, 17.0, y + 8.0, 185.0, 4.2, "1″ 控制范围  ·  2″ 反应移动上限 / 冲锋增量  ·  3″ 冲刺", 6.2, INK)
    draw_textbox(page, 17.0, y + 13.5, 185.0, 4.2, "4″ 跳跃上限  ·  6″ 常见移动  ·  8″ 常见近程射程", 6.2, INK)
    draw_textbox(page, 17.0, y + 23.5, 185.0, 3.8, "非官方玩家辅助工具；以当前规则文本与任务包为准。", 5.1, MUTED)

    cal_x = 226.0
    cal_y = y + 20.0
    cal_len = 50.0
    draw_textbox(page, 216.0, y + 3.0, 70.0, 4.0, "打印校准线：50 mm", 6.4, INK, fitz.TEXT_ALIGN_CENTER)
    page.draw_line(
        fitz.Point(pt(cal_x), pt(cal_y)),
        fitz.Point(pt(cal_x + cal_len), pt(cal_y)),
        color=INK,
        width=1.0,
        overlay=True,
    )
    for end_x in (cal_x, cal_x + cal_len):
        page.draw_line(
            fitz.Point(pt(end_x), pt(cal_y - 2.5)),
            fitz.Point(pt(end_x), pt(cal_y + 2.5)),
            color=INK,
            width=1.0,
            overlay=True,
        )
    draw_textbox(page, 214.0, y + 24.1, 74.0, 3.5, "不是 50 mm 时请改用 100% / 实际大小打印", 4.8, MUTED, fitz.TEXT_ALIGN_CENTER)


def validate_inputs() -> Path:
    font_file = next((path for path in FONT_CANDIDATES if path.exists()), None)
    if font_file is None:
        raise FileNotFoundError("No compatible CJK font found")
    missing = [str(path) for path in THEMES.values() if not path.exists()]
    if missing:
        raise FileNotFoundError(f"Missing ruler backgrounds: {missing}")
    return font_file


def create_pdf() -> Path:
    font_file = validate_inputs()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    document = fitz.open()
    page = document.new_page(width=pt(PAGE_W_MM), height=pt(PAGE_H_MM))
    page.insert_font(fontname=FONT_NAME, fontfile=str(font_file))
    document.set_metadata(
        {
            "title": "杀戮小队 A4 英寸尺（图像增强打印版）",
            "subject": "A4 landscape exact-size quarter-inch tabletop measuring rulers",
            "keywords": "杀戮小队, Kill Team, 英寸尺, A4, 1/4 inch, print",
            "author": "OpenAI Codex",
            "creator": "PyMuPDF",
        }
    )

    draw_header(page)
    image_cache: dict[tuple[str, int], bytes] = {}
    for spec in RULERS:
        draw_ruler(page, spec, image_cache)
    draw_footer(page)

    document.subset_fonts(verbose=False)
    document.save(OUTPUT, garbage=4, deflate=True, clean=True)
    document.close()
    return OUTPUT


if __name__ == "__main__":
    print(create_pdf())
