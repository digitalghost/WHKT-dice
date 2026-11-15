#!/usr/bin/env python3
"""
Builds data/maps/volkus_25_0N.json from the TTS Killzone-Mod layout JSON + terrain OBJ meshes.

For each placed terrain piece we:
  1. load its OBJ, apply the placement rotation/scale/position (TTS units == inches, Y up),
  2. slice vertices into height bands and extract axis-aligned footprint rectangles per band,
  3. emit rule-level terrain entries the engine already understands:
       kind ∈ { heavy_wall, heavy_low, vantage, light }   rect (inches, board space, +Y = south)
     plus the visual placement (mesh path, transform) so killzone_view can render the real model.

Board space: 30" x 22", origin top-left, x → east, y → south (same as kt_data.gd today).
TTS space:   centred at (0, _, 0), X → east, +Z → NORTH. Verified against the official 2025
             map 1 (east/west deployment) and map 4 (north/south) tiles: board_y = 11 - tts_z.

Rule text / names are NOT taken from the external pack (only geometry). Chinese names come from
the official rulebook (docs/杀戮小队三版官方规则-v11.5-全文提取.md) terrain list.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXT = ROOT / "external-assets" / "killzones" / "volkus"
OUT_DIR = ROOT / "data" / "maps"
ASSET_DIR = ROOT / "assets" / "terrain" / "volkus"

BOARD_W = 30.0
BOARD_H = 22.0

# Official rulebook (p.40-42) names and rule traits per piece family.
PIECE_RULES = {
    "stronghold":   {"name_zh": "堡垒",     "heavy": True,  "vantage_levels": True,  "light": False},
    "large_ruin":   {"name_zh": "大型废墟", "heavy": True,  "vantage_levels": True,  "light": False},
    "small_ruin":   {"name_zh": "小型废墟", "heavy": True,  "vantage_levels": False, "light": False},
    "heavy_rubble": {"name_zh": "重型瓦砾", "heavy": True,  "vantage_levels": False, "light": False},
    "light_rubble": {"name_zh": "轻型瓦砾", "heavy": False, "vantage_levels": False, "light": True},
    "fire_steps":   {"name_zh": "射击踏台", "heavy": False, "vantage_levels": False, "light": False, "firestep": True},
}

# Pieces that ship only as Unity asset bundles (no OBJ). We substitute footprint boxes from the
# rulebook description; sizes are the TTS bundle bounds we measured from sibling OBJ variants.
FALLBACK_BOX = {
    "heavy_rubble":   (2.6, 1.9, 1.6),   # w, d, h  inches  (heavy, blocks LoS when > model height)
    "heavy_rubble_02": (2.6, 1.9, 1.6),
    "light_rubble":   (2.9, 1.7, 0.95),
    "light_rubble_03": (2.9, 1.7, 0.95),
    "fire_steps":     (2.3, 1.9, 0.5),
    "fire_steps_02":  (2.3, 1.9, 0.5),
}


def load_obj(path: Path):
    vs = []
    with open(path, errors="ignore") as f:
        for line in f:
            if line.startswith("v "):
                p = line.split()
                vs.append((float(p[1]), float(p[2]), float(p[3])))
    return vs


def rot_y(x, z, deg):
    # Unity left-handed, Y-up: positive Y rotation turns +Z toward +X.
    #   x' =  x cos + z sin ;  z' = -x sin + z cos
    # Verified against the official 2025 map 1 and map 4 tiles (overlay in /tmp during build).
    # Verified visually against the official 2025 map 1 / map 4 tiles (ground-band vertex overlay):
    # the TTS scene is mirrored in X relative to the printed maps, and a mirrored frame flips the
    # sense of rotation, so the piece yaw is applied as −θ here. Godot's build_terrain_model()
    # uses the same pair (scale.x = −s, rotation.y = −θ), so meshes and rule rects coincide.
    a = math.radians(-deg)
    c, s = math.cos(a), math.sin(a)
    return x * c + z * s, -x * s + z * c


def place(vs, placement):
    sx, sy, sz = placement["scale"]
    px, py, pz = placement["pos"]
    ry = placement["rot"][1]
    out = []
    for x, y, z in vs:
        x, y, z = x * sx, y * sy, z * sz
        x, z = rot_y(x, z, ry)
        out.append((x + px, y, z + pz))
    return out


def tts_to_board(x, z):
    # Fitted against the three objective markers on the official map-1 tile (rms 4.6 px) and
    # confirmed on map 4: the TTS scene is MIRRORED in X vs the printed map, +Z is south.
    #   board_x = 15 - tts_x        board_y = 11 + tts_z
    return BOARD_W * 0.5 - x, BOARD_H * 0.5 + z


def band_rects(pts, y_lo, y_hi, cell=0.5, min_cells=2):
    """Cluster the XZ footprint of vertices with y in [y_lo, y_hi) into axis-aligned rectangles.
    Greedy: rasterise to `cell` grid, then grow maximal rectangles."""
    cells = set()
    for x, y, z in pts:
        if y_lo <= y < y_hi:
            cells.add((math.floor(x / cell), math.floor(z / cell)))
    rects = []
    cells = set(cells)
    while cells:
        cx, cz = min(cells)
        # grow right
        w = 1
        while (cx + w, cz) in cells:
            w += 1
        # grow down while full row present
        h = 1
        while all((cx + i, cz + h) in cells for i in range(w)):
            h += 1
        for i in range(w):
            for j in range(h):
                cells.discard((cx + i, cz + j))
        if w * h >= min_cells:
            rects.append((cx * cell, cz * cell, w * cell, h * cell))
    return rects


def hull_rect(rects):
    """Bounding box of a rect list. Used for vantage decks: the rulebook treats a piece's whole
    upper level as one Vantage area, so one rectangle per deck is the correct granularity (and it
    stops the HUD from tagging every 0.5" sliver with its own '+3.0"' label)."""
    if not rects:
        return []
    x0 = min(r[0] for r in rects); z0 = min(r[1] for r in rects)
    x1 = max(r[0] + r[2] for r in rects); z1 = max(r[1] + r[3] for r in rects)
    return [(x0, z0, x1 - x0, z1 - z0)]


def merge_small(rects, eps=0.01):
    """Merge rectangles that share an edge exactly (reduces fragment count)."""
    changed = True
    rects = [list(r) for r in rects]
    while changed:
        changed = False
        for i in range(len(rects)):
            for j in range(i + 1, len(rects)):
                a, b = rects[i], rects[j]
                # same rows, adjacent in x
                if abs(a[1] - b[1]) < eps and abs(a[3] - b[3]) < eps and abs(a[0] + a[2] - b[0]) < eps:
                    a[2] += b[2]; rects.pop(j); changed = True; break
                if abs(a[1] - b[1]) < eps and abs(a[3] - b[3]) < eps and abs(b[0] + b[2] - a[0]) < eps:
                    a[0] = b[0]; a[2] += b[2]; rects.pop(j); changed = True; break
                # same cols, adjacent in z
                if abs(a[0] - b[0]) < eps and abs(a[2] - b[2]) < eps and abs(a[1] + a[3] - b[1]) < eps:
                    a[3] += b[3]; rects.pop(j); changed = True; break
                if abs(a[0] - b[0]) < eps and abs(a[2] - b[2]) < eps and abs(b[1] + b[3] - a[1]) < eps:
                    a[1] = b[1]; a[3] += b[3]; rects.pop(j); changed = True; break
            if changed:
                break
    return [tuple(r) for r in rects]


def piece_family(slug: str) -> str:
    for fam in PIECE_RULES:
        if slug.startswith(fam):
            return fam
    return slug


# Deployment zones + default spawn spots per official 2025 map (not in the TTS data).
# Map 1-3: 6" strips along the short (west/east) edges. Side A = west/grey, side B = east/orange.
# Map 4-6: 3" strips along the long (north/south) edges. Side A = south/grey, side B = north/orange.
# Spawn spots are placed inside the zone, in open ground, spread along it.
DEPLOY = {
    "volkus_25_01": {
        "drop_zone_a": [0.0, 0.0, 6.0, BOARD_H],
        "drop_zone_b": [BOARD_W - 6.0, 0.0, 6.0, BOARD_H],
        "spawn_a": [[2.2, 3.5], [2.2, 7.0], [4.3, 9.0], [2.2, 13.0], [4.3, 16.5], [2.2, 19.0]],
        "spawn_b": [[27.8, 3.5], [27.8, 7.0], [25.7, 9.0], [27.8, 13.0], [25.7, 16.5], [27.8, 19.0]],
    },
    "volkus_25_04": {
        "drop_zone_a": [0.0, BOARD_H - 3.0, BOARD_W, 3.0],
        "drop_zone_b": [0.0, 0.0, BOARD_W, 3.0],
        # Map 4 has large ruin C (x 0-6) and small ruin F (x 12.5-18.5) inside the south strip, and
        # small ruin E (x 2.5-7.5), heavy rubble (x 12-15) and large ruin D (x 18.5-25) inside the north
        # strip. Spots below are all ≥0.8" clear of every rule rectangle (checked by build output).
        "spawn_a": [[7.6, 20.6], [9.6, 20.6], [11.3, 20.6], [19.6, 20.6], [22.4, 20.6], [26.4, 20.6]],
        "spawn_b": [[9.0, 1.4], [10.6, 1.4], [16.4, 1.4], [26.6, 1.4], [28.2, 1.4], [1.2, 1.4]],
    },
}


def build_map(layout_path: Path, map_id: str, name_zh: str) -> dict:
    layout = json.loads(layout_path.read_text(encoding="utf-8"))
    terrains = []
    visuals = []
    idx = 0

    for pl in layout["terrain_placements"]:
        slug = pl["piece_slug"]
        if slug == "boundary_or_marker":
            continue
        fam = piece_family(slug)
        rules = PIECE_RULES.get(fam, {"name_zh": slug, "heavy": True, "vantage_levels": False, "light": False})
        piece_dir = EXT / "terrain_3d" / slug
        mesh = piece_dir / "model_00_root_mesh.obj"
        diffuse = piece_dir / "model_00_root_diffuse.jpg"
        px, py, pz = pl["pos"]
        bx, by = tts_to_board(px, pz)

        visual = {
            "piece_slug": slug,
            "family": fam,
            "mesh": f"res://assets/terrain/volkus/{slug}/model_00_root_mesh.obj" if mesh.exists() else "",
            "diffuse": f"res://assets/terrain/volkus/{slug}/model_00_root_diffuse.jpg" if diffuse.exists() else "",
            "pos_in": [round(bx, 3), round(by, 3)],
            "rot_y_deg": round(pl["rot"][1], 2),
            "scale": round(pl["scale"][0], 3),
        }
        visuals.append(visual)

        if mesh.exists():
            pts = place(load_obj(mesh), pl)
            top = max(p[1] for p in pts)
            # --- ground walls: everything between 0.3" and 1.9" that is solid ---
            if rules["vantage_levels"]:
                # Walls under the deck: band 0.3..2.3 ; deck slab: band 2.4..3.3 (vantage, +3.0")
                # Upper walls above the deck: band 3.4..top (heavy, elevation = top)
                deck_h = 3.0
                for (rx, rz, rw, rh) in merge_small(band_rects(pts, 0.3, 2.3, 0.5, 2)):
                    x0, y0 = tts_to_board(rx + rw, rz)  # x mirrors: far x edge becomes left; z keeps
                    terrains.append({
                        "id": f"{slug}_wall_{idx}", "name": f"{rules['name_zh']}·底层墙体",
                        "kind": "heavy_wall", "elevation": round(deck_h, 2), "blocks_los_ground": True,
                        "rect": [round(x0, 2), round(y0, 2), round(rw, 2), round(rh, 2)], "parent": slug,
                    }); idx += 1
                for (rx, rz, rw, rh) in hull_rect(band_rects(pts, 2.4, 3.3, 0.5, 3)):
                    x0, y0 = tts_to_board(rx + rw, rz)  # x mirrors: far x edge becomes left; z keeps
                    terrains.append({
                        "id": f"{slug}_deck_{idx}", "name": f"{rules['name_zh']}·二层制高点",
                        "kind": "vantage", "elevation": round(deck_h, 2),
                        "rect": [round(x0, 2), round(y0, 2), round(rw, 2), round(rh, 2)], "parent": slug,
                    }); idx += 1
                for (rx, rz, rw, rh) in merge_small(band_rects(pts, 3.4, top + 0.1, 0.5, 2)):
                    x0, y0 = tts_to_board(rx + rw, rz)  # x mirrors: far x edge becomes left; z keeps
                    terrains.append({
                        "id": f"{slug}_upper_{idx}", "name": f"{rules['name_zh']}·二层墙垛",
                        "kind": "heavy_wall" if fam == "stronghold" else "light",
                        "elevation": round(top, 2), "blocks_los_ground": fam == "stronghold",
                        "rect": [round(x0, 2), round(y0, 2), round(rw, 2), round(rh, 2)], "parent": slug,
                    }); idx += 1
            else:
                kind = "light" if rules["light"] else ("heavy_wall" if top >= 2.0 else "heavy_low")
                for (rx, rz, rw, rh) in merge_small(band_rects(pts, 0.2, top + 0.1, 0.5, 2)):
                    x0, y0 = tts_to_board(rx + rw, rz)  # x mirrors: far x edge becomes left; z keeps
                    terrains.append({
                        "id": f"{slug}_{idx}", "name": rules["name_zh"],
                        "kind": kind, "elevation": round(top, 2), "blocks_los_ground": kind == "heavy_wall",
                        "rect": [round(x0, 2), round(y0, 2), round(rw, 2), round(rh, 2)], "parent": slug,
                    }); idx += 1
        else:
            w, d, h = FALLBACK_BOX.get(slug, (2.0, 2.0, 1.0))
            w, d = w * pl["scale"][0], d * pl["scale"][0]
            if abs((pl["rot"][1] % 180) - 90) < 45:
                w, d = d, w
            kind = "light" if rules["light"] else ("vantage" if rules.get("firestep") else "heavy_low")
            terrains.append({
                "id": f"{slug}_{idx}", "name": rules["name_zh"],
                "kind": kind, "elevation": round(h, 2), "blocks_los_ground": False,
                "rect": [round(bx - w / 2, 2), round(by - d / 2, 2), round(w, 2), round(d, 2)], "parent": slug,
            }); idx += 1

    objectives = []
    for i, o in enumerate(layout["objectives"]):
        ox, oy = tts_to_board(o["pos"][0], o["pos"][2])
        objectives.append({"id": f"obj_{i+1}", "name": f"{i+1}号目标标识", "pos": [round(ox, 2), round(oy, 2)]})

    return {
        "id": map_id,
        "name": name_zh,
        "source": "认证行动任务包 2025 · 杀戮区：沃库斯 (地形编号 A–N 几何取自 TTS 模组模型，规则文字以官方规则书为准)",
        "width": BOARD_W, "height": BOARD_H,
        **DEPLOY[map_id],
        "objectives": objectives,
        "terrains": terrains,
        "visuals": visuals,
        "battlefield_mat": "res://assets/terrain/volkus/battlefield_mat_02.jpg",
    }


def main() -> None:
    which = sys.argv[1] if len(sys.argv) > 1 else "04"
    layout = EXT / "map_layouts" / "volkus_approved_ops_2025" / f"volkus_25_{which}.json"
    out = build_map(layout, f"volkus_25_{which}", f"杀戮区：沃库斯 · 认证行动 2025 地图 {int(which)}")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / f"volkus_25_{which}.json").write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"map {which}: {len(out['terrains'])} rule rects, {len(out['visuals'])} visual pieces, {len(out['objectives'])} objectives")
    for t in out["terrains"]:
        print(f"  {t['kind']:10s} h={t['elevation']:4.1f}  rect={t['rect']}  {t['name']}")


if __name__ == "__main__":
    main()
