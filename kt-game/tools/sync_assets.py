#!/usr/bin/env python3
"""
Read-only asset & rule synchronization pipeline for kt-game.
Extracts structured team data, parses weapon profiles, copies UI/audio assets,
and crops circular operative portraits from ../app/src/main/ without modifying
anything in the parent Android helper app repository.
"""

from __future__ import annotations

import argparse
from collections import deque
import json
import os
import re
import shutil
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter

KT_GAME_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = KT_GAME_ROOT.parent
ANDROID_MAIN = REPO_ROOT / "app" / "src" / "main"
ANDROID_ASSETS = ANDROID_MAIN / "assets"
ANDROID_RES = ANDROID_MAIN / "res"

DEFAULT_TEAMS = ["IMP-AOD", "CHAOS-PM"]

CANONICAL_ROSTERS = {
    "IMP-AOD": [
        "aod_captain",
        "aod_grenadier",
        "aod_sniper",
        "aod_heavy_gunner",
        "aod_gunner",
        "aod_assault_warrior",
    ],
    "CHAOS-PM": [
        "pm_champion",
        "pm_bombardier",
        "pm_fighter",
        "pm_heavy_gunner",
        "pm_icon_bearer",
        "pm_plaguecaster",
    ],
}


def parse_inches(text: str, default: float = 6.0) -> float:
    m = re.search(r"(\d+(?:\.\d+)?)", str(text))
    return float(m.group(1)) if m else default


def parse_int_plus(text: str, default: int = 3) -> int:
    m = re.search(r"(\d+)", str(text))
    return int(m.group(1)) if m else default


def parse_weapon_profile(profile: str) -> dict:
    """
    Parse e.g. '4A · 3+ · 4/5 · 范围 8"、过热、致命 5+、穿刺 1'
    into clean structured stats & rule dictionary.
    """
    parts = [p.strip() for p in profile.split("·")]
    attacks = parse_int_plus(parts[0], 4) if len(parts) > 0 else 4
    hit = parse_int_plus(parts[1], 3) if len(parts) > 1 else 3
    normal_dmg, crit_dmg = 3, 4
    if len(parts) > 2 and "/" in parts[2]:
        d_parts = parts[2].split("/")
        normal_dmg = parse_int_plus(d_parts[0], 3)
        crit_dmg = parse_int_plus(d_parts[1], 4)

    raw_keywords: list[str] = []
    if len(parts) > 3:
        raw_keywords = [
            k.strip()
            for k in re.split(r"[、,，]", " · ".join(parts[3:]))
            if k.strip()
        ]

    rules = {
        "range": 0.0,           # 0.0 means unlimited board range
        "lethal": 6,            # e.g. 5 for 致命 5+
        "piercing": 0,          # 穿刺 X
        "piercing_crits": 0,    # 关键穿刺 X
        "devastating": 0,       # 毁灭 X
        "blast": 0.0,           # 爆炸 X"
        "torrent": 0.0,         # 洪流 X"
        "accurate": 0,          # 精准 X
        "saturate": False,      # 集中
        "balanced": False,      # 平衡
        "ceaseless": False,     # 无休
        "relentless": False,    # 毫不留情
        "rending": False,       # 撕裂
        "punishing": False,     # 重击
        "severe": False,        # 严重
        "hot": False,           # 过热
        "brutal": False,        # 残暴
        "shock": False,         # 震荡
        "stun": False,          # 晕眩 / 眩晕
        "silent": False,        # 安静
        "seek_light": False,    # 追踪轻型
        "seek": False,          # 追踪
        "heavy": False,         # 重型
        "heavy_dash_only": False,
        "psychic": False,       # 灵能
        "poison": False,        # 毒素 (CHAOS-PM)
        "toxic": False,         # 剧毒 (CHAOS-PM)
    }

    for kw in raw_keywords:
        if kw.startswith("范围"):
            rules["range"] = parse_inches(kw, 0.0)
        elif kw.startswith("致命"):
            rules["lethal"] = parse_int_plus(kw, 5)
        elif kw.startswith("关键穿刺"):
            rules["piercing_crits"] = parse_int_plus(kw, 1)
        elif kw.startswith("穿刺"):
            rules["piercing"] = parse_int_plus(kw, 1)
        elif kw.startswith("毁灭"):
            rules["devastating"] = parse_int_plus(kw, 1)
        elif kw.startswith("爆炸"):
            rules["blast"] = parse_inches(kw, 2.0)
        elif kw.startswith("洪流"):
            rules["torrent"] = parse_inches(kw, 1.0)
        elif kw.startswith("精准"):
            rules["accurate"] = parse_int_plus(kw, 1)
        elif kw == "集中":
            rules["saturate"] = True
        elif kw in ("平衡", "均衡"):
            rules["balanced"] = True
        elif kw == "无休":
            rules["ceaseless"] = True
        elif kw == "毫不留情":
            rules["relentless"] = True
        elif kw == "撕裂":
            rules["rending"] = True
        elif kw == "重击":
            rules["punishing"] = True
        elif kw == "严重":
            rules["severe"] = True
        elif kw == "过热":
            rules["hot"] = True
        elif kw == "残暴":
            rules["brutal"] = True
        elif kw == "震荡":
            rules["shock"] = True
        elif kw in ("晕眩", "眩晕"):
            rules["stun"] = True
        elif kw == "安静":
            rules["silent"] = True
        elif kw == "追踪轻型":
            rules["seek_light"] = True
        elif kw == "追踪":
            rules["seek"] = True
        elif kw.startswith("重型"):
            rules["heavy"] = True
            if "仅限冲刺" in kw or "冲刺" in kw:
                rules["heavy_dash_only"] = True
        elif kw == "灵能":
            rules["psychic"] = True
        elif kw == "毒素":
            rules["poison"] = True
        elif kw == "剧毒":
            rules["toxic"] = True

    return {
        "attacks": attacks,
        "hit": hit,
        "normal_dmg": normal_dmg,
        "crit_dmg": crit_dmg,
        "keywords": raw_keywords,
        "rules": rules,
    }


def resolve_card_file(card_name: str) -> Path | None:
    team_rules_dir = ANDROID_ASSETS / "team_rules"
    exact = team_rules_dir / card_name
    if exact.is_file():
        return exact
    stem = Path(card_name).stem
    for ext in (".webp", ".png", ".jpg"):
        candidate = team_rules_dir / f"{stem}{ext}"
        if candidate.is_file():
            return candidate
    return None


def make_circular_portrait(card_path: Path, out_path: Path, size_px: int = 256) -> None:
    """
    Uses the verified datacard portrait region from AvatarImages.kt and masks
    into a clean anti-aliased circular token PNG.
    """
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(card_path) as raw_img:
        img = raw_img.convert("RGBA")
        w, h = img.size
        if w > h * 1.2:
            # Official KT24 horizontal datacard header bust sits in top dark banner
            # around x = 38%..65% width, y = 0..16.5% height.
            crop_size = max(1, int(h * 0.165))
            cx = int(w * 0.525)
            x = max(0, min(w - crop_size, cx - crop_size // 2))
            y = 0
            square = img.crop((x, y, x + crop_size, y + crop_size))
        else:
            edge = min(w, h)
            square = img.crop(((w - edge) // 2, (h - edge) // 2, (w + edge) // 2, (h + edge) // 2))

        square = square.resize((size_px, size_px), Image.Resampling.LANCZOS)

        # Supersampled circular alpha mask
        ss = size_px * 2
        mask = Image.new("L", (ss, ss), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((4, 4, ss - 5, ss - 5), fill=255)
        mask = mask.resize((size_px, size_px), Image.Resampling.LANCZOS)

        out = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
        out.paste(square, (0, 0), mask)
        out.save(out_path, "PNG")


def extract_clean_miniature(card_path: Path) -> Image.Image:
    """
    Extracts a spotless, scanline-free painted miniature silhouette from the official
    KT24 horizontal datacard header banner.
    """
    with Image.open(card_path) as raw_im:
        im = raw_im.convert("RGBA")
        # Scale proportionally if card resolution differs from canonical 947x545
        sx = im.width / 947.0
        sy = im.height / 545.0
        crop = im.crop((int(285 * sx), 0, int(612 * sx), max(16, int(89 * sy))))
        if crop.size != (327, 89):
            crop = crop.resize((327, 89), Image.Resampling.LANCZOS)

    w, h = crop.size
    px = crop.load()

    # 1. Mask out left-side title text before the empty charcoal separator
    empty_x = 0
    for x in range(85):
        if all((max(px[x, y][:3]) < 68 and (max(px[x, y][:3]) - min(px[x, y][:3])) <= 22) for y in range(h)):
            empty_x = x
        elif empty_x > 6:
            break
    for x in range(empty_x + 1):
        for y in range(h):
            px[x, y] = (14, 16, 18, 255)

    # 2. Exterior flood fill of dark low-saturation studio scanline backdrop
    def is_bg(x: int, y: int) -> bool:
        r, g, b, _ = px[x, y]
        mx, mn = max(r, g, b), min(r, g, b)
        sat = mx - mn
        r1, g1, b1, _ = px[max(0, x - 2), y]
        r2, g2, b2, _ = px[min(w - 1, x + 2), y]
        h_grad = abs((r2 + g2 + b2) - (r1 + g1 + b1)) / 3.0
        if mx <= 62 and sat <= 21 and h_grad < 14.0:
            return True
        if mx <= 36 and sat <= 14 and h_grad < 18.0:
            return True
        return False

    ext = [[False] * h for _ in range(w)]
    q = deque()
    for x in range(w):
        if is_bg(x, 0):
            ext[x][0] = True
            q.append((x, 0))
    for y in range(h):
        for bx in (0, w - 1):
            if is_bg(bx, y) and not ext[bx][y]:
                ext[bx][y] = True
                q.append((bx, y))

    while q:
        cx, cy = q.popleft()
        for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
            if 0 <= nx < w and 0 <= ny < h and not ext[nx][ny]:
                if is_bg(nx, ny):
                    ext[nx][ny] = True
                    q.append((nx, ny))

    # 3. Vertical opening eliminates 1-2px horizontal scanline fingers while preserving weapons
    fg = [[not ext[x][y] for y in range(h)] for x in range(w)]
    eroded = [[False] * h for _ in range(w)]
    for x in range(w):
        for y in range(h):
            if fg[x][y] and fg[x][max(0, y - 1)] and fg[x][min(h - 1, y + 1)]:
                eroded[x][y] = True
            elif fg[x][y] and max(px[x, y][:3]) > 85:
                eroded[x][y] = True

    opened = [[False] * h for _ in range(w)]
    for x in range(w):
        for y in range(h):
            if fg[x][y] and (eroded[x][y] or eroded[x][max(0, y - 1)] or eroded[x][min(h - 1, y + 1)]):
                opened[x][y] = True

    # Drop tiny isolated specks (< 16px) BEFORE bridging nearby weapon parts
    speck_vis = [[False] * h for _ in range(w)]
    for x in range(w):
        for y in range(h):
            if opened[x][y] and not speck_vis[x][y]:
                sc = []
                sq = deque([(x, y)])
                speck_vis[x][y] = True
                while sq:
                    ux, uy = sq.popleft()
                    sc.append((ux, uy))
                    for vx, vy in ((ux - 1, uy), (ux + 1, uy), (ux, uy - 1), (ux, uy + 1)):
                        if 0 <= vx < w and 0 <= vy < h and not speck_vis[vx][vy] and opened[vx][vy]:
                            speck_vis[vx][vy] = True
                            sq.append((vx, vy))
                if len(sc) < 16:
                    for ux, uy in sc:
                        opened[ux][uy] = False

    # 4. Keep main figure component + nearby weapon parts (dilated connectivity) to drop stray specks
    dil = Image.new("L", (w, h), 0)
    dpx = dil.load()
    for x in range(w):
        for y in range(h):
            if opened[x][y]:
                dpx[x, y] = 255
    dil_near = dil.filter(ImageFilter.MaxFilter(9))
    npx = dil_near.load()

    visited = [[False] * h for _ in range(w)]
    comps: list[list[tuple[int, int]]] = []
    for x in range(w):
        for y in range(h):
            if npx[x, y] > 128 and not visited[x][y]:
                comp = []
                cq = deque([(x, y)])
                visited[x][y] = True
                while cq:
                    ux, uy = cq.popleft()
                    comp.append((ux, uy))
                    for vx, vy in ((ux - 1, uy), (ux + 1, uy), (ux, uy - 1), (ux, uy + 1)):
                        if 0 <= vx < w and 0 <= vy < h and not visited[vx][vy] and npx[vx, vy] > 128:
                            visited[vx][vy] = True
                            cq.append((vx, vy))
                comps.append(comp)

    clean_fg = [[False] * h for _ in range(w)]
    if comps:
        largest = max(comps, key=len)
        for ux, uy in largest:
            if opened[ux][uy]:
                clean_fg[ux][uy] = True

    # Fill enclosed interior holes (dark gunmetal / eye lenses / crevices)
    hole_ext = [[False] * h for _ in range(w)]
    hq = deque()
    for x in range(w):
        if not clean_fg[x][0]:
            hole_ext[x][0] = True
            hq.append((x, 0))
    for y in range(h):
        for bx in (0, w - 1):
            if not clean_fg[bx][y] and not hole_ext[bx][y]:
                hole_ext[bx][y] = True
                hq.append((bx, y))
    while hq:
        ux, uy = hq.popleft()
        for vx, vy in ((ux - 1, uy), (ux + 1, uy), (ux, uy - 1), (ux, uy + 1)):
            if 0 <= vx < w and 0 <= vy < h and not clean_fg[vx][vy] and not hole_ext[vx][vy]:
                hole_ext[vx][vy] = True
                hq.append((vx, vy))
    for x in range(w):
        for y in range(h):
            if not hole_ext[x][y]:
                clean_fg[x][y] = True

    alpha = Image.new("L", (w, h), 0)
    apx = alpha.load()
    for x in range(w):
        for y in range(h):
            if clean_fg[x][y]:
                apx[x, y] = 255

    scale = 4
    up_rgb = crop.convert("RGB").resize((w * scale, h * scale), Image.Resampling.LANCZOS)
    up_rgb = ImageEnhance.Sharpness(up_rgb).enhance(1.65)
    up_rgb = ImageEnhance.Contrast(up_rgb).enhance(1.12)

    up_a = alpha.resize((w * scale, h * scale), Image.Resampling.BILINEAR)
    up_a = up_a.filter(ImageFilter.MedianFilter(5))
    up_a = up_a.point(lambda v: 255 if v > 125 else 0)

    out = Image.new("RGBA", up_rgb.size, (0, 0, 0, 0))
    out.paste(up_rgb, (0, 0), up_a)
    bbox = out.getbbox()
    return out.crop(bbox) if bbox else out


# Acrylic standee token canvas (portrait orientation). The name plate is NOT baked in;
# killzone_view renders it with Label3D so fonts/localisation stay consistent in-engine.
TOKEN_W = 560
TOKEN_H = 560
TOKEN_CORNER = 34
TOKEN_FRAME = 22          # outer faction frame thickness
TOKEN_PLATE_H = 84        # bottom plate band reserved for the Label3D name
TOKEN_PEG_H = 44          # tab that slots into the 3D base


def _rounded_mask(size: tuple[int, int], radius: int, inset: int = 0) -> Image.Image:
    ss = 2
    m = Image.new("L", (size[0] * ss, size[1] * ss), 0)
    ImageDraw.Draw(m).rounded_rectangle(
        (inset * ss, inset * ss, size[0] * ss - 1 - inset * ss, size[1] * ss - 1 - inset * ss),
        radius=radius * ss, fill=255,
    )
    return m.resize(size, Image.Resampling.LANCZOS)


def _vertical_gradient(size: tuple[int, int], top: tuple, bottom: tuple) -> Image.Image:
    w, h = size
    strip = Image.new("RGBA", (1, h))
    sp = strip.load()
    for y in range(h):
        t = y / max(1, h - 1)
        sp[0, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(4))
    return strip.resize((w, h), Image.Resampling.BILINEAR)


def make_hd2d_standee(card_path: Path, out_path: Path, team_id: str) -> None:
    """
    Generates a portrait-orientation acrylic standee token (480x600):
    faction-coloured bevelled frame, dark smoked-glass backdrop with a radial glow,
    the datacard bust centred inside, a bottom plate band left empty for the in-engine
    Label3D name, and a short peg at the very bottom that sits inside the 3D base.
    """
    out_path.parent.mkdir(parents=True, exist_ok=True)
    cut = extract_clean_miniature(card_path)
    is_imp = team_id.startswith("IMP")

    frame_col = (74, 128, 214, 255) if is_imp else (112, 160, 54, 255)
    frame_hi = (142, 190, 255, 255) if is_imp else (176, 226, 104, 255)
    frame_lo = (28, 58, 118, 255) if is_imp else (48, 78, 24, 255)
    glow_col = (60, 110, 200) if is_imp else (96, 150, 48)
    plate_col = (16, 18, 24, 255)
    brass = (196, 158, 82, 255)

    W, H = TOKEN_W, TOKEN_H
    body_h = H - TOKEN_PEG_H
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    # --- 1. Outer bevelled frame (gradient so it reads as thick acrylic edge) ---
    frame = _vertical_gradient((W, body_h), frame_hi, frame_lo)
    canvas.paste(frame, (0, 0), _rounded_mask((W, body_h), TOKEN_CORNER))
    mid = Image.new("RGBA", (W, body_h), frame_col)
    canvas.paste(mid, (0, 0), _rounded_mask((W, body_h), TOKEN_CORNER - 4, inset=5))

    # --- 2. Smoked-glass interior with radial faction glow behind the bust ---
    ix0, iy0 = TOKEN_FRAME, TOKEN_FRAME
    iw, ih = W - TOKEN_FRAME * 2, body_h - TOKEN_FRAME * 2
    inner = _vertical_gradient((iw, ih), (44, 50, 64, 255), (18, 20, 28, 255))
    glow = Image.new("RGBA", (iw, ih), (0, 0, 0, 0))
    gp = glow.load()
    gcx, gcy = iw * 0.5, ih * 0.42
    grad_r = ih * 0.55
    for y in range(ih):
        for x in range(iw):
            d = ((x - gcx) ** 2 + (y - gcy) ** 2) ** 0.5 / grad_r
            a = max(0.0, 1.0 - d)
            a = a * a
            gp[x, y] = (*glow_col, int(150 * a))
    inner.alpha_composite(glow)
    # fine horizontal scanlines for a holo-print feel
    sl = inner.load()
    for y in range(0, ih, 4):
        for x in range(iw):
            r, g, b, a = sl[x, y]
            sl[x, y] = (max(0, r - 10), max(0, g - 10), max(0, b - 10), a)
    canvas.paste(inner, (ix0, iy0), _rounded_mask((iw, ih), TOKEN_CORNER - TOKEN_FRAME))

    # --- 3. Bust, scaled to fit the picture area above the plate band ---
    pic_top = iy0 + 10
    pic_bot = body_h - TOKEN_FRAME - TOKEN_PLATE_H
    pic_h = pic_bot - pic_top
    pic_w = iw - 16
    # Fill the picture area by height; very wide busts (long rifles) may overflow sideways and get clipped
    # by the inner glass mask, which reads naturally as the weapon leaving the frame.
    ratio = min((pic_h * 1.08) / max(1, cut.height), (pic_w * 1.45) / max(1, cut.width))
    nw, nh = max(1, int(cut.width * ratio)), max(1, int(cut.height * ratio))
    fig = cut.resize((nw, nh), Image.Resampling.LANCZOS)
    # Centre on the torso mass rather than the bounding box so helmets line up across the roster
    fpx = fig.load()
    mass_xs = [x for x in range(nw) for y in range(int(nh * 0.2), int(nh * 0.9), 3) if fpx[x, y][3] > 128]
    mass_cx = (sum(mass_xs) // len(mass_xs)) if mass_xs else nw // 2
    fx = W // 2 - mass_cx
    fy = pic_bot - nh + 4  # bust sits on the plate band like a cut-out standing in a holder

    pad = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    pad.paste(fig, (fx, fy))
    glass_clip = Image.new("L", (W, H), 0)
    glass_clip.paste(_rounded_mask((iw, pic_bot - iy0), TOKEN_CORNER - TOKEN_FRAME), (ix0, iy0))
    pad.putalpha(ImageChops.multiply(pad.getchannel("A"), glass_clip))
    pa = pad.getchannel("A")
    ink = Image.new("RGBA", (W, H), (6, 7, 10, 230))
    canvas.paste(ink, (0, 0), pa.filter(ImageFilter.MaxFilter(7)))
    rim = Image.new("RGBA", (W, H), (*frame_hi[:3], 170))
    canvas.paste(rim, (0, 0), pa.filter(ImageFilter.MaxFilter(3)))
    canvas.alpha_composite(pad)

    # --- 4. Bottom plate band (empty; Label3D draws the name) with brass divider ---
    plate_y0 = pic_bot
    inner_mask = Image.new("L", (W, H), 0)
    inner_mask.paste(_rounded_mask((iw, ih), TOKEN_CORNER - TOKEN_FRAME), (ix0, iy0))
    band_mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(band_mask).rectangle((0, plate_y0, W, body_h), fill=255)
    plate_mask = ImageChops.multiply(inner_mask, band_mask)
    plate_img = Image.new("RGBA", (W, H), plate_col)
    ImageDraw.Draw(plate_img).rectangle((ix0 + 18, plate_y0, ix0 + iw - 19, plate_y0 + 3), fill=brass)
    canvas.paste(plate_img, (0, 0), plate_mask)

    # --- 5. Peg that slots into the 3D base ---
    peg_w = 150
    px0 = W // 2 - peg_w // 2
    d = ImageDraw.Draw(canvas)
    d.polygon([
        (px0, body_h - 2), (px0 + peg_w, body_h - 2),
        (px0 + peg_w - 16, H - 1), (px0 + 16, H - 1),
    ], fill=(12, 13, 18, 255))
    d.polygon([
        (px0 + 6, body_h - 2), (px0 + peg_w - 6, body_h - 2),
        (px0 + peg_w - 20, H - 7), (px0 + 20, H - 7),
    ], fill=brass)

    # --- 6. Glossy acrylic highlight sweep across the top-left ---
    gloss = Image.new("RGBA", (W, body_h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gloss)
    gd.polygon([(20, 18), (W * 0.62, 18), (W * 0.30, body_h * 0.46), (20, body_h * 0.36)], fill=(255, 255, 255, 22))
    gloss_masked = Image.new("RGBA", (W, body_h), (0, 0, 0, 0))
    gloss_masked.paste(gloss, (0, 0), _rounded_mask((W, body_h), TOKEN_CORNER - 2, inset=3))
    canvas.alpha_composite(gloss_masked, (0, 0))

    canvas.save(out_path, "PNG")


def copy_card_png(src_path: Path, dest_path: Path, max_width: int = 1200) -> None:
    dest_path.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(src_path) as im:
        im = im.convert("RGBA")
        if im.width > max_width:
            ratio = max_width / float(im.width)
            im = im.resize((max_width, int(im.height * ratio)), Image.Resampling.LANCZOS)
        im.save(dest_path, "PNG")


def sync_common_assets() -> None:
    ui_dir = KT_GAME_ROOT / "assets" / "ui"
    audio_dir = KT_GAME_ROOT / "assets" / "audio"
    fonts_dir = KT_GAME_ROOT / "assets" / "fonts"
    for d in (ui_dir, audio_dir, fonts_dir):
        d.mkdir(parents=True, exist_ok=True)

    nodpi = ANDROID_RES / "drawable-nodpi"
    ui_files = [
        "dice_tray_attack.png",
        "dice_tray_defence.png",
        "dice_tray_industrial.png",
        "header_winged_skull.png",
        "kt_battlefield.jpg",
        "order_conceal_ready.png",
        "order_conceal_spent.png",
        "order_engage_ready.png",
        "order_engage_spent.png",
        "team_mark_imp_aod.png",
        "team_mark_chaos_pm.png",
        "kt_aod.webp",
        "kt_pm.webp",
    ]
    for fname in ui_files:
        src = nodpi / fname
        if src.is_file():
            out_name = Path(fname).stem + ".png"
            with Image.open(src) as im:
                if max(im.size) > 1400:
                    ratio = 1400.0 / max(im.size)
                    im = im.resize((int(im.width * ratio), int(im.height * ratio)), Image.Resampling.LANCZOS)
                im.convert("RGBA").save(ui_dir / out_name, "PNG")

    ogg = ANDROID_RES / "raw" / "dice_roll.ogg"
    if ogg.is_file():
        shutil.copy2(ogg, audio_dir / "dice_roll.ogg")

    for font_file in ("teko_variable.ttf", "pirata_one_regular.ttf"):
        fsrc = ANDROID_RES / "font" / font_file
        if fsrc.is_file():
            shutil.copy2(fsrc, fonts_dir / font_file)

    core_src = ANDROID_ASSETS / "core_rules.json"
    core_dst = KT_GAME_ROOT / "data" / "core" / "core_rules.json"
    core_dst.parent.mkdir(parents=True, exist_ok=True)
    if core_src.is_file():
        shutil.copy2(core_src, core_dst)


def sync_team(team_id: str) -> dict:
    team_src = ANDROID_ASSETS / "teams" / f"{team_id}.json"
    rules_src = ANDROID_ASSETS / "rules" / f"{team_id}.json"
    if not team_src.is_file():
        raise FileNotFoundError(f"Missing team file: {team_src}")

    team_raw = json.loads(team_src.read_text(encoding="utf-8"))
    rules_raw = json.loads(rules_src.read_text(encoding="utf-8")) if rules_src.is_file() else {"entries": []}

    cards_out_dir = KT_GAME_ROOT / "assets" / "cards" / team_id
    portraits_dir = KT_GAME_ROOT / "assets" / "portraits"
    standees_dir = KT_GAME_ROOT / "assets" / "standees"

    operatives_out = []
    for op in team_raw.get("operatives", []):
        op_id = op["id"]
        op_cards_res: list[str] = []
        portrait_res = ""
        standee_res = ""

        for idx, card_ref in enumerate(op.get("cards", [])):
            resolved = resolve_card_file(card_ref)
            if resolved:
                png_name = Path(card_ref).stem + ".png"
                copy_card_png(resolved, cards_out_dir / png_name)
                op_cards_res.append(f"res://assets/cards/{team_id}/{png_name}")
                if idx == 0:
                    portrait_file = portraits_dir / f"{op_id}.png"
                    make_circular_portrait(resolved, portrait_file)
                    portrait_res = f"res://assets/portraits/{op_id}.png"
                    standee_file = standees_dir / f"{op_id}.png"
                    make_hd2d_standee(resolved, standee_file, team_id)
                    standee_res = f"res://assets/standees/{op_id}.png"

        weapons_out = []
        for w in op.get("weapons", []):
            parsed = parse_weapon_profile(w["profile"])
            weapons_out.append({
                "id": w["id"],
                "name": w["name"],
                "kind": w["kind"],
                "profile": w["profile"],
                **parsed,
            })

        base_mm = int(op.get("baseSizeMm", 32))
        operatives_out.append({
            "id": op_id,
            "name": op["name"],
            "role": op.get("role", ""),
            "apl": int(op.get("apl", 2)),
            "move_inches": parse_inches(op.get("move", '6"'), 6.0),
            "save": parse_int_plus(op.get("save", "3+"), 3),
            "wounds": int(op.get("wounds", 12)),
            "base_mm": base_mm,
            "base_radius_inches": round((base_mm / 25.4) * 0.5, 4),
            "keywords": op.get("keywords", []),
            "portrait": portrait_res,
            "standee": standee_res,
            "cards": op_cards_res,
            "weapons": weapons_out,
            "default_weapon_ids": op.get("defaultWeaponIds", []),
            "abilities": op.get("abilities", []),
        })

    entries_out = []
    for entry in rules_raw.get("entries", []):
        entry_cards: list[str] = []
        for card_ref in entry.get("cards", []):
            resolved = resolve_card_file(card_ref)
            if resolved:
                png_name = Path(card_ref).stem + ".png"
                copy_card_png(resolved, cards_out_dir / png_name)
                entry_cards.append(f"res://assets/cards/{team_id}/{png_name}")
        entries_out.append({
            "id": entry.get("id", ""),
            "category": entry.get("category", ""),
            "title": entry.get("title", ""),
            "body": entry.get("body", ""),
            "cards": entry_cards,
        })

    bundle = {
        "team_id": team_id,
        "name": team_raw.get("name", team_id),
        "name_en": team_raw.get("nameEn", team_id),
        "subtitle": team_raw.get("subtitle", ""),
        "archetypes": team_raw.get("archetypes", []),
        "composition_hint": team_raw.get("compositionHint", ""),
        "canonical_roster": CANONICAL_ROSTERS.get(team_id, [o["id"] for o in operatives_out[:6]]),
        "operatives": operatives_out,
        "weapon_rules": team_raw.get("weaponRules", []),
        "rule_entries": entries_out,
    }

    out_json = KT_GAME_ROOT / "data" / "teams" / f"{team_id}.json"
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(bundle, ensure_ascii=False, indent=2), encoding="utf-8")
    return bundle


def main() -> None:
    parser = argparse.ArgumentParser(description="Sync Kill Team assets into kt-game")
    parser.add_argument("--all", action="store_true", help="Sync all 48 teams instead of launch teams")
    parser.add_argument("--teams", nargs="*", help="Specific team IDs to sync")
    args = parser.parse_args()

    sync_common_assets()

    if args.all:
        team_ids = sorted(p.stem for p in (ANDROID_ASSETS / "teams").glob("*.json"))
    elif args.teams:
        team_ids = args.teams
    else:
        team_ids = DEFAULT_TEAMS

    summary = []
    for tid in team_ids:
        b = sync_team(tid)
        summary.append(f"{tid} ({b['name']}): {len(b['operatives'])} operatives, {len(b['rule_entries'])} rule cards")

    manifest_path = KT_GAME_ROOT / "data" / "teams" / "manifest.json"
    manifest_path.write_text(json.dumps({"teams": team_ids}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Synced {len(team_ids)} team(s) successfully:")
    for line in summary:
        print("  -", line)


if __name__ == "__main__":
    main()
