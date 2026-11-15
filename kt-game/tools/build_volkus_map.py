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
TTS space:   centred at (0, _, 0), Unity left-handed, Y up. See the "Coordinate conventions"
             block below — it was fitted numerically against all six official 2025 tiles.

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


# ---------------------------------------------------------------------------------------------
# Coordinate conventions (fitted against boards_and_mats/tile_volkus_approved_ops_2025_*.png by
# scoring, for every sign combination, how many mesh vertices land on terrain-coloured pixels;
# the combination below wins on all six maps and puts the ground walls exactly on the printed
# wall lines and the objectives on the printed markers):
#
#   1. The OBJ exports are right-handed, Unity is left-handed:   x_unity = −x_obj.
#   2. Piece yaw is a plain Unity Y rotation by +θ (clockwise seen from above):
#          x' =  x cos θ + z sin θ ;  z' = −x sin θ + z cos θ
#   3. Scene → printed map. The mod stores maps 1–3 and maps 4–6 half a turn apart relative to
#      the printed tiles:
#          maps 1–3:  board_x = 15 − tts_x ,  board_y = 11 + tts_z
#          maps 4–6:  board_x = 15 + tts_x ,  board_y = 11 − tts_z
#
# Neither step mirrors the piece, so Godot renders the mesh with a positive scale; the yaw Godot
# needs (rotation.y, counter-clockwise seen from above) is −θ for maps 1–3 and 180° − θ for
# maps 4–6. That value is what we write into visuals[].rot_y_deg.
# ---------------------------------------------------------------------------------------------
def scene_sign(map_num: int) -> int:
    """−1 for maps 1–3, +1 for maps 4–6 (see convention 3 above)."""
    return -1 if map_num <= 3 else 1


def tts_to_board(x, z, sign):
    return BOARD_W * 0.5 + sign * x, BOARD_H * 0.5 - sign * z


def godot_yaw(deg, sign):
    return (-deg if sign < 0 else 180.0 - deg) % 360.0


def place(vs, placement, sign):
    """OBJ vertices → board space. Returns (board_x, height, board_y) tuples."""
    sx, sy, sz = placement["scale"]
    px, _py, pz = placement["pos"]
    a = math.radians(placement["rot"][1])
    c, s = math.cos(a), math.sin(a)
    out = []
    for x, y, z in vs:
        x, y, z = -x * sx, y * sy, z * sz
        bx, by = tts_to_board(x * c + z * s + px, -x * s + z * c + pz, sign)
        out.append((bx, y, by))
    return out


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


# Deployment zones per official 2025 map (not in the TTS data; checked against the six official
# tiles boards_and_mats/tile_volkus_approved_ops_2025_volkus_25_0N.png):
#   Map 1-3: 6" strips along the short (west/east) edges. Side A = west/grey, side B = east/orange.
#   Map 4-6: 3" strips along the long (north/south) edges. Side A = south/grey, side B = north/orange.
def drop_zones(map_num: int) -> tuple[list, list]:
    if map_num <= 3:
        return [0.0, 0.0, 6.0, BOARD_H], [BOARD_W - 6.0, 0.0, 6.0, BOARD_H]
    return [0.0, BOARD_H - 3.0, BOARD_W, 3.0], [0.0, 0.0, BOARD_W, 3.0]


SPAWN_CLEAR = 0.85   # inches: largest base radius in data/teams (0.7874) + margin
SPAWN_GAP = 1.7      # minimum centre distance between two default spots


def _circle_hits_rect(cx, cy, r, rect) -> bool:
    x, y, w, h = rect
    qx = min(max(cx, x), x + w)
    qy = min(max(cy, y), y + h)
    return (cx - qx) ** 2 + (cy - qy) ** 2 < r * r


def spot_clearance(pos, terrains) -> float:
    """Distance from a point to the nearest terrain rule rectangle (0 when inside one)."""
    best = 99.0
    for t in terrains:
        x, y, w, h = t["rect"]
        qx = min(max(pos[0], x), x + w)
        qy = min(max(pos[1], y), y + h)
        best = min(best, math.hypot(pos[0] - qx, pos[1] - qy))
    return best


def find_spawns(zone, terrains, n=6) -> list:
    """Pick n default spawn spots inside `zone`: open ground (SPAWN_CLEAR from every terrain rule
    rectangle, vantage decks and stronghold courtyards included, and from the zone edge), spread evenly along the zone's
    long axis, preferring the zone's centre line. Blocked stretches fall back to the nearest
    free spot that still keeps SPAWN_GAP to the spots already chosen."""
    zx, zy, zw, zh = zone
    step = 0.1
    blocked = [t["rect"] for t in terrains]
    # Stronghold courtyards are legal ground, but a default spot walled in on four sides is a poor
    # starting suggestion: block each stronghold's whole footprint as well.
    for slug in sorted({t["parent"] for t in terrains if t["parent"].startswith("stronghold")}):
        rs = [t["rect"] for t in terrains if t["parent"] == slug]
        x0 = min(r[0] for r in rs); y0 = min(r[1] for r in rs)
        x1 = max(r[0] + r[2] for r in rs); y1 = max(r[1] + r[3] for r in rs)
        blocked.append([x0, y0, x1 - x0, y1 - y0])
    cands = []
    i = 0
    while zx + SPAWN_CLEAR + i * step <= zx + zw - SPAWN_CLEAR + 1e-6:
        x = zx + SPAWN_CLEAR + i * step
        j = 0
        while zy + SPAWN_CLEAR + j * step <= zy + zh - SPAWN_CLEAR + 1e-6:
            y = zy + SPAWN_CLEAR + j * step
            if not any(_circle_hits_rect(x, y, SPAWN_CLEAR, r) for r in blocked):
                cands.append((x, y))
            j += 1
        i += 1
    horizontal = zw >= zh
    length = zw if horizontal else zh
    origin = zx if horizontal else zy
    mid_depth = (zy + zh / 2.0) if horizontal else (zx + zw / 2.0)
    chosen = []
    for k in range(n):
        target = origin + length * (k + 0.5) / n
        best, best_cost = None, 1e9
        for c in cands:
            if any(math.hypot(c[0] - o[0], c[1] - o[1]) < SPAWN_GAP for o in chosen):
                continue
            along, depth = (c[0], c[1]) if horizontal else (c[1], c[0])
            cost = abs(along - target) + 0.3 * abs(depth - mid_depth)
            if cost < best_cost:
                best, best_cost = c, cost
        if best is None:
            raise SystemExit(f"find_spawns: no free spot left in zone {zone} (got {len(chosen)}/{n})")
        chosen.append(best)
    chosen.sort(key=lambda c: c[0] if horizontal else c[1])
    return [[round(c[0], 2), round(c[1], 2)] for c in chosen]


def deploy_data(map_id: str, terrains: list) -> dict:
    dz_a, dz_b = drop_zones(int(map_id[-2:]))
    return {
        "drop_zone_a": dz_a,
        "drop_zone_b": dz_b,
        "spawn_a": find_spawns(dz_a, terrains),
        "spawn_b": find_spawns(dz_b, terrains),
    }


def build_map(layout_path: Path, map_id: str, name_zh: str) -> dict:
    layout = json.loads(layout_path.read_text(encoding="utf-8"))
    sign = scene_sign(int(map_id[-2:]))
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
        bx, by = tts_to_board(px, pz, sign)

        visual = {
            "piece_slug": slug,
            "family": fam,
            "mesh": f"res://assets/terrain/volkus/{slug}/model_00_root_mesh.obj" if mesh.exists() else "",
            "diffuse": f"res://assets/terrain/volkus/{slug}/model_00_root_diffuse.jpg" if diffuse.exists() else "",
            "pos_in": [round(bx, 3), round(by, 3)],
            "rot_y_deg": round(godot_yaw(pl["rot"][1], sign), 2),
            "scale": round(pl["scale"][0], 3),
        }
        visuals.append(visual)

        if mesh.exists():
            pts = place(load_obj(mesh), pl, sign)
            top = max(p[1] for p in pts)
            # --- ground walls: everything between 0.3" and 1.9" that is solid ---
            if rules["vantage_levels"]:
                # Walls under the deck: band 0.3..2.3 ; deck slab: band 2.4..3.3 (vantage, +3.0")
                # Upper walls above the deck: band 3.4..top (heavy, elevation = top)
                deck_h = 3.0
                for (rx, rz, rw, rh) in merge_small(band_rects(pts, 0.3, 2.3, 0.5, 2)):
                    x0, y0 = rx, rz  # pts are already in board space
                    terrains.append({
                        "id": f"{slug}_wall_{idx}", "name": f"{rules['name_zh']}·底层墙体",
                        "kind": "heavy_wall", "elevation": round(deck_h, 2), "blocks_los_ground": True,
                        "rect": [round(x0, 2), round(y0, 2), round(rw, 2), round(rh, 2)], "parent": slug,
                    }); idx += 1
                for (rx, rz, rw, rh) in hull_rect(band_rects(pts, 2.4, 3.3, 0.5, 3)):
                    x0, y0 = rx, rz  # pts are already in board space
                    terrains.append({
                        "id": f"{slug}_deck_{idx}", "name": f"{rules['name_zh']}·二层制高点",
                        "kind": "vantage", "elevation": round(deck_h, 2),
                        "rect": [round(x0, 2), round(y0, 2), round(rw, 2), round(rh, 2)], "parent": slug,
                    }); idx += 1
                for (rx, rz, rw, rh) in merge_small(band_rects(pts, 3.4, top + 0.1, 0.5, 2)):
                    x0, y0 = rx, rz  # pts are already in board space
                    terrains.append({
                        "id": f"{slug}_upper_{idx}", "name": f"{rules['name_zh']}·二层墙垛",
                        "kind": "heavy_wall" if fam == "stronghold" else "light",
                        "elevation": round(top, 2), "blocks_los_ground": fam == "stronghold",
                        "rect": [round(x0, 2), round(y0, 2), round(rw, 2), round(rh, 2)], "parent": slug,
                    }); idx += 1
            elif rules.get("firestep"):
                # Rulebook (Volkus terrain list): fire steps are Vantage, Insignificant, Exposed —
                # one walkable deck per piece, same as the bundle-only fire steps below.
                for (rx, rz, rw, rh) in hull_rect(band_rects(pts, 0.0, top + 0.1, 0.5, 1)):
                    terrains.append({
                        "id": f"{slug}_{idx}", "name": rules["name_zh"],
                        "kind": "vantage", "elevation": round(top, 2), "blocks_los_ground": False,
                        "rect": [round(rx, 2), round(rz, 2), round(rw, 2), round(rh, 2)], "parent": slug,
                    }); idx += 1
            else:
                kind = "light" if rules["light"] else ("heavy_wall" if top >= 2.0 else "heavy_low")
                for (rx, rz, rw, rh) in merge_small(band_rects(pts, 0.2, top + 0.1, 0.5, 2)):
                    x0, y0 = rx, rz  # pts are already in board space
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
            # Keep the substitute box on the board (a few pieces sit flush against the edge).
            x0 = min(max(bx - w / 2, 0.0), BOARD_W - w)
            y0 = min(max(by - d / 2, 0.0), BOARD_H - d)
            terrains.append({
                "id": f"{slug}_{idx}", "name": rules["name_zh"],
                "kind": kind, "elevation": round(h, 2), "blocks_los_ground": False,
                "rect": [round(x0, 2), round(y0, 2), round(w, 2), round(d, 2)], "parent": slug,
            }); idx += 1

    objectives = []
    for i, o in enumerate(layout["objectives"]):
        ox, oy = tts_to_board(o["pos"][0], o["pos"][2], sign)
        objectives.append({"id": f"obj_{i+1}", "name": f"{i+1}号目标标识", "pos": [round(ox, 2), round(oy, 2)]})

    return {
        "id": map_id,
        "name": name_zh,
        "source": "认证行动任务包 2025 · 杀戮区：沃库斯 (地形编号 A–N 几何取自 TTS 模组模型，规则文字以官方规则书为准)",
        "width": BOARD_W, "height": BOARD_H,
        **deploy_data(map_id, terrains),
        "objectives": objectives,
        "terrains": terrains,
        "visuals": visuals,
        "battlefield_mat": "res://assets/terrain/volkus/battlefield_mat_02.jpg",
    }


def main() -> None:
    arg = sys.argv[1] if len(sys.argv) > 1 else "04"
    verbose = len(sys.argv) > 2 and sys.argv[2] == "-v"
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for which in (["01", "02", "03", "04", "05", "06"] if arg == "all" else [arg]):
        layout = EXT / "map_layouts" / "volkus_approved_ops_2025" / f"volkus_25_{which}.json"
        out = build_map(layout, f"volkus_25_{which}", f"杀戮区：沃库斯 · 认证行动 2025 地图 {int(which)}")
        (OUT_DIR / f"volkus_25_{which}.json").write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"map {which}: {len(out['terrains'])} rule rects, {len(out['visuals'])} visual pieces, {len(out['objectives'])} objectives")
        for key in ("spawn_a", "spawn_b"):
            clr = min(spot_clearance(p, out["terrains"]) for p in out[key])
            print(f"  {key}: {out[key]}  (min terrain clearance {clr:.2f}\")")
        if verbose or arg != "all":
            for t in out["terrains"]:
                print(f"  {t['kind']:10s} h={t['elevation']:4.1f}  rect={t['rect']}  {t['name']}")


if __name__ == "__main__":
    main()
