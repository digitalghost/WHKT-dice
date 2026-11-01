#!/usr/bin/env python3
"""官方杀戮小队 PDF 的结构化解析共享库。

提供的能力：
  * 按卡框 bbox 取词、按视觉行聚类
  * 从特工数据卡读取 APL / 移动 / 豁免 / 耐伤
  * 从数据卡读取武器表（名称 / 攻击 / 命中 / 伤害 / 武器规则 / 档案）
  * 读取卡面页脚（小队 / 阵营 / 教派 / 类别 / 位置）
  * 通过矢量图标位图指纹识别武器类型（远程 / 近战 / 装备）

所有解析均基于 PDF 文本层与渲染像素，不依赖固定页码或版式假设。
"""
from __future__ import annotations

import functools
import hashlib
import re
import subprocess
import tempfile
from dataclasses import dataclass, field

import pdfplumber
from PIL import Image, ImageChops

# ---------------------------------------------------------------- 文档访问


class PdfDoc:
    """带缓存的 PDF 访问器（词层 + 渲染）。"""

    def __init__(self, path: str):
        self.path = path
        self._doc = pdfplumber.open(path)
        self.pages = self._doc.pages
        self._words: dict[int, list[dict]] = {}

    def close(self):
        try:
            self._doc.close()
        except Exception:
            pass

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()

    @property
    def page_count(self) -> int:
        return len(self.pages)

    def words(self, page_no: int) -> list[dict]:
        """某页全部词（带 x0/x1/top/bottom）。"""
        if page_no not in self._words:
            self._words[page_no] = self.pages[page_no - 1].extract_words(
                keep_blank_chars=False, use_text_flow=False, x_tolerance=1.5
            )
        return self._words[page_no]

    def size(self, page_no: int) -> tuple[float, float]:
        p = self.pages[page_no - 1]
        return float(p.width), float(p.height)

    # -- 渲染 ---------------------------------------------------------
    def render_region(self, page_no: int, bbox_pt, dpi: int) -> Image.Image:
        """渲染指定 PDF 区域（pt）并返回 RGB 图像。"""
        x0, top, x1, bottom = bbox_pt
        scale = dpi / 72.0
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
            base = tmp.name[:-4]
        try:
            subprocess.run(
                [
                    "pdftoppm", "-f", str(page_no), "-l", str(page_no),
                    "-r", str(dpi), "-png", "-singlefile",
                    "-x", str(int(round(x0 * scale))), "-y", str(int(round(top * scale))),
                    "-W", str(int(round((x1 - x0) * scale))),
                    "-H", str(int(round((bottom - top) * scale))),
                    self.path, base,
                ],
                check=True, capture_output=True,
            )
            return Image.open(base + ".png").convert("RGB")
        finally:
            for suffix in (".png", "-1.png"):
                try:
                    import os
                    os.unlink(base + suffix)
                except OSError:
                    pass


# ---------------------------------------------------------------- 基础几何


def bbox_xyxy(bbox) -> tuple[float, float, float, float]:
    """manifest 里的 bbox_pt 是 [x0, y0, w, h]，这里转成 [x0, y0, x1, y1]。"""
    x0, y0, w, h = bbox
    return x0, y0, x0 + w, y0 + h


def words_in_bbox(words, bbox):
    """取中心点落在 bbox 内的词。bbox 为 [x0, y0, w, h]。"""
    x0, top, x1, bottom = bbox_xyxy(bbox)
    out = []
    for w in words:
        cx = (w["x0"] + w["x1"]) / 2
        cy = (w["top"] + w["bottom"]) / 2
        if x0 - 1 <= cx <= x1 + 1 and top - 1 <= cy <= bottom + 1:
            out.append(w)
    return out


def cluster_rows(words, tol: float = 2.0):
    """按 y 坐标把词聚成视觉行，返回 [(top, bottom, words)]，已按 y 排序。"""
    if not words:
        return []
    buckets: dict[int, list[dict]] = {}
    for w in words:
        buckets.setdefault(round(w["top"]), []).append(w)
    keys = sorted(buckets)
    groups: list[list[int]] = []
    for k in keys:
        if groups and k - groups[-1][-1] <= tol:
            groups[-1].append(k)
        else:
            groups.append([k])
    rows = []
    for grp in groups:
        ws = []
        for k in grp:
            ws.extend(buckets[k])
        rows.append(
            (min(w["top"] for w in ws), max(w["bottom"] for w in ws),
             sorted(ws, key=lambda z: z["x0"]))
        )
    return rows


def row_text(words) -> str:
    return " ".join(w["text"] for w in words)


# ---------------------------------------------------------------- 属性块

APL_RE = re.compile(r"^\d+$")
MOVE_RE = re.compile(r"^\d+(\.\d+)?\"$")
SAVE_RE = re.compile(r"^\d+\+$")
WOUND_RE = re.compile(r"^\d+$")


@dataclass
class Stats:
    apl: str = "—"
    move: str = "—"
    save: str = "—"
    wounds: str = "—"

    def as_dict(self):
        return {"apl": self.apl, "move": self.move, "save": self.save, "wounds": self.wounds}


def find_stats(words) -> Stats | None:
    """在词层里找 APL / 移动 / 豁免 / 耐伤 那一行数值。

    策略：找到形如 `<数字> <N"> <N+> <N>` 的相邻四词组合。
    """
    ws = sorted(words, key=lambda z: (z["top"], z["x0"]))
    for i in range(len(ws) - 3):
        a, b, c, d = ws[i], ws[i + 1], ws[i + 2], ws[i + 3]
        if abs(a["top"] - b["top"]) > 2.5 or abs(b["top"] - c["top"]) > 2.5:
            continue
        if abs(c["top"] - d["top"]) > 2.5:
            continue
        if not (APL_RE.match(a["text"]) and MOVE_RE.match(b["text"])
                and SAVE_RE.match(c["text"]) and WOUND_RE.match(d["text"])):
            continue
        # 必须是升序排列的一行
        if not (a["x0"] < b["x0"] < c["x0"] < d["x0"]):
            continue
        return Stats(a["text"], b["text"], c["text"], d["text"])
    return None


# ---------------------------------------------------------------- 武器表

PROFILE_SUFFIX_RE = re.compile(r"[（(]([^（）()]+)[）)]\s*[\d\*]*\s*$")


@dataclass
class Weapon:
    name: str
    atk: str = "—"
    hit: str = "—"
    dmg: str = "—"
    wr: str = "—"
    profile: str = "—"
    icon_key: str = ""
    icon_kind: str = ""
    icon_dims: tuple = ()
    icon_ratio: float = 0.0
    icon_bitmap: str = ""
    icon_bbox: tuple = ()
    top: float = 0.0
    bottom: float = 0.0

    @property
    def base_name(self) -> str:
        return PROFILE_SUFFIX_RE.sub("", self.name).strip() or self.name

    @property
    def profile_name(self) -> str:
        m = PROFILE_SUFFIX_RE.search(self.name)
        return m.group(1).strip() if m else "—"

    def as_dict(self):
        return {
            "name": self.name, "base_name": self.base_name, "profile": self.profile_name,
            "atk": self.atk, "hit": self.hit, "dmg": self.dmg, "wr": self.wr,
            "icon_key": self.icon_key, "icon_kind": self.icon_kind,
        }


ATK_RE = re.compile(r"^\d+$")
HIT_RE = re.compile(r"^\d+\+$")
DMG_RE = re.compile(r"^\d+/\d+$")
CONT_RE = re.compile(
    r"请继续阅读|剩余规则请见|详见卡背|详见其他卡片|在卡背继续|RULES CONTINUE|CONTINUES ON OTHER SIDE",
    re.I,
)


def _header_cols(ws) -> dict:
    cols = {}
    for w in ws:
        t = w["text"]
        if "名称" in t or t.upper() == "NAME":
            cols["name"] = w["x0"]
        elif "攻击" in t or t.upper() in ("ATK", "A"):
            cols["atk"] = w["x0"]
        elif "命中" in t or t.upper() == "HIT":
            cols["hit"] = w["x0"]
        elif "伤害" in t or t.upper() in ("DMG", "D"):
            cols["dmg"] = w["x0"]
        elif "武器规则" in t or t.upper() == "WR":
            cols["wr"] = w["x0"]
    return cols


def _is_rules_label(ws) -> bool:
    text = "".join(w["text"] for w in ws).strip()
    return text in ("武器规则", "WEAPON RULE", "WEAPON RULES")


def find_weapon_table(words) -> list[Weapon]:
    """解析数据卡上的武器表。

    支持两种官方版式：
      * 横版 —— 一张表，武器规则在表格最右侧的「武器规则」列里；
      * 竖版 —— 每件武器一张小表，表头下方先列武器行，再由「武器规则」
        标签引出该武器的规则文字。
    """
    rows = cluster_rows(words)
    weapons: list[Weapon] = []
    cols: dict = {}
    mode = "outside"
    for top, bottom, ws in rows:
        row_text = " ".join(w["text"] for w in ws)
        if CONT_RE.search(row_text):
            break
        if _header_cols(ws) and any(
            "名称" in w["text"] or w["text"].upper() == "NAME" for w in ws
        ):
            cols = _header_cols(ws)
            mode = "table"
            continue
        if mode == "outside":
            continue
        if _is_rules_label(ws):
            mode = "rules"
            continue
        if mode == "rules":
            if not weapons:
                mode = "outside"
                continue
            txt = join_words(ws)
            if not txt:
                continue
            last = weapons[-1]
            last.wr = (last.wr + " " + txt) if last.wr != "—" else txt
            last.bottom = max(last.bottom, bottom)
            continue

        c_name = cols.get("name", 0.0)
        c_atk = cols.get("atk", c_name + 90.0)
        c_hit = cols.get("hit", c_atk + 20.0)
        c_dmg = cols.get("dmg", c_hit + 20.0)
        c_wr = cols.get("wr", c_dmg + 25.0)

        dmg = hit = atk = None
        # 按 x 降序依次赋值：最右的命中格式当伤害，再往左是命中、攻击。
        # 官方表格里数值列常常整体右移或微幅左移，放宽 8pt 容差。
        for w in sorted(ws, key=lambda z: -z["x0"]):
            t = w["text"]
            if dmg is None and DMG_RE.match(t) and w["x0"] >= c_dmg - 8:
                dmg = w
            elif dmg is not None and hit is None and HIT_RE.match(t) and w["x0"] >= c_hit - 8:
                hit = w
            elif hit is not None and atk is None and ATK_RE.match(t) and w["x0"] >= c_atk - 8:
                atk = w
        # 少数武器没有伤害值（官方印作「-」，如脉冲飞弹），落在伤害列里；
        # 此时命中与攻击取该「-」左侧最近的两个数值。
        if dmg is None:
            dash = None
            for w in ws:
                if w["text"] in ("-", "—") and c_dmg - 8 <= w["x0"] < c_wr - 3:
                    dash = w
                    break
            if dash is not None:
                left = [w for w in ws if w["x0"] < dash["x0"]]
                for w in sorted(left, key=lambda z: -z["x0"]):
                    if hit is None and HIT_RE.match(w["text"]) and w["x0"] >= c_hit - 8:
                        hit = w
                    elif hit is not None and atk is None and ATK_RE.match(w["text"]) \
                            and w["x0"] >= c_atk - 8:
                        atk = w
                if hit is not None and atk is not None:
                    dmg = dash
        if atk and hit and dmg:
            mode = "table"
            name = join_words([w for w in ws if w["x1"] <= atk["x0"] + 0.5])
            # 数值列（右对齐）可能伸进武器规则列的左边界，需排除掉
            wr_words = [w for w in ws
                        if w["x0"] >= c_wr - 5 and w is not dmg and w is not hit and w is not atk]
            weapons.append(
                Weapon(name=name, atk=atk["text"], hit=hit["text"], dmg=dmg["text"],
                       wr=join_words(wr_words) or "—", top=top, bottom=bottom)
            )
            continue
        # 武器规则列换行溢出 → 仅当处于 table 阶段且整行都落在武器规则列时才并入上一行
        if mode == "table" and weapons and all(w["x0"] >= c_wr - 8 for w in ws):
            last = weapons[-1]
            overflow = join_words(ws)
            if overflow:
                last.wr = overflow if last.wr == "—" else f"{last.wr} {overflow}"
                last.bottom = max(last.bottom, bottom)
            continue
        # 武器名称/子档案换行溢出（如脉冲卡宾枪换行后紧跟「（破片）」或英文小写/括号续行）
        if mode == "table" and weapons and (top - weapons[-1].bottom <= 6.5) and all(
            w["x1"] <= c_atk + 5 for w in ws
        ):
            overflow_name = join_words(ws)
            if overflow_name and (
                overflow_name.startswith(("（", "("))
                or overflow_name.endswith(("）", ")"))
                or re.match(r"^[a-z]", overflow_name)
                or re.search(r"[（(][^（）()]+[）)]\s*[\d\*]*$", overflow_name)
            ):
                last = weapons[-1]
                if CJK_RE.search(last.name[-1:]) or overflow_name.startswith("（"):
                    last.name = f"{last.name}{overflow_name}"
                else:
                    last.name = f"{last.name} {overflow_name}"
                last.bottom = max(last.bottom, bottom)
                continue
        mode = "after_table"
    return weapons


def weapon_table_region(words) -> tuple[float, float] | None:
    """返回武器表的纵向范围 (top, bottom)（含所有子表与换行）。"""
    rows = cluster_rows(words)
    top = bottom = None
    for t, b, ws in rows:
        row_text = " ".join(w["text"] for w in ws)
        if CONT_RE.search(row_text):
            break
        is_header = any("名称" in w["text"] or w["text"].upper() == "NAME" for w in ws) \
            and any("攻击" in w["text"] or w["text"].upper() in ("ATK", "A") for w in ws)
        is_weapon = any(
            ATK_RE.match(w["text"]) and HIT_RE.match(next(
                (x["text"] for x in ws if x["x0"] > w["x0"] and HIT_RE.match(x["text"])), ""))
            for w in ws)
        if is_header or is_weapon:
            if top is None:
                top = t
            bottom = b
    weapons = find_weapon_table(words)
    for w in weapons:
        if top is None or w.top < top:
            top = w.top
        if bottom is None or w.bottom > bottom:
            bottom = w.bottom
    if top is None or bottom is None or bottom <= top:
        return None
    return top, bottom


# ---------------------------------------------------------------- 页脚

CJK_RE = re.compile(r"[\u3000-\u303f\u3400-\u9fff\uff00-\uffef]")


def join_words(words) -> str:
    """中文词间不加空格，中英之间加空格；紧贴的西文片段视为同一个词。"""
    out = ""
    prev = None
    for w in words:
        t = w["text"]
        if not out:
            out = t
            prev = w
            continue
        gap = w["x0"] - prev["x1"]
        if CJK_RE.search(out[-1]) and CJK_RE.match(t):
            out += t
        elif gap <= 0.6 and re.match(r"[A-Za-z]", out[-1]) and re.match(r"[A-Za-z]", t):
            out += t
        else:
            out += " " + t
        prev = w
    return out.strip()


def find_footer(words) -> list[str]:
    """读卡面页脚：中文卡为「小队、阵营、教派、类别、位置」，英文卡用逗号分隔。"""
    if not words:
        return []
    rows = cluster_rows(words)
    for top, bottom, ws in reversed(rows):
        joined = "".join(w["text"] for w in ws)
        if joined.count("、") >= 2 or joined.count(",") >= 2 or joined.count("，") >= 2:
            toks = [w for w in ws if not re.fullmatch(r"\d{1,3}(?:x\d{1,3})?", w["text"], re.I)]
            joined = "".join(w["text"] for w in toks)
            if "、" in joined:
                return [p.strip() for p in joined.split("、") if p.strip()]
            if "，" in joined:
                return [p.strip() for p in joined.split("，") if p.strip()]
            if "," in joined:
                return [p.strip() for p in " ".join(w["text"] for w in toks).split(",") if p.strip()]
    return []


def find_base_size(words, bbox) -> str | None:
    """从特工数据卡右下角圆圈读取底座尺寸（如 32mm、25mm、40mm、75x42mm）。"""
    if not words:
        return None
    x0, y0, w, h = bbox
    cands = []
    for wd in words:
        if wd["x0"] >= x0 + w - 50 and wd["top"] >= y0 + h - 32:
            t = wd["text"].strip()
            if re.fullmatch(r"\d{2,3}", t) and 20 <= int(t) <= 120:
                cands.append((wd["top"], wd["x0"], f"{t}mm"))
            elif re.fullmatch(r"\d{2,3}x\d{2,3}", t, re.I):
                cands.append((wd["top"], wd["x0"], f"{t.lower()}mm"))
            elif re.fullmatch(r"\d{2,3}\s*mm", t, re.I):
                num = re.findall(r"\d+", t)[0]
                cands.append((wd["top"], wd["x0"], f"{num}mm"))
    if not cands:
        return None
    cands.sort()
    return cands[-1][2]


def _is_footer_row(ws, bbox) -> bool:
    """判断一行是否为页脚关键词栏、底角底座圆圈数值或续页提示栏。"""
    if not ws:
        return True
    x0, y0, w, h = bbox
    txt = join_words(ws)
    if CONT_RE.search(txt):
        return True
    joined = "".join(wd["text"] for wd in ws)
    if (joined.count("、") >= 2 or joined.count(",") >= 2 or joined.count("，") >= 2) and min(wd["top"] for wd in ws) >= y0 + h - 32:
        return True
    if all(wd["x0"] >= x0 + w - 50 and wd["top"] >= y0 + h - 32 and re.fullmatch(r"\d{2,3}(?:x\d{2,3})?", wd["text"], re.I) for wd in ws):
        return True
    return False


def _find_gutter_x(words, min_x: float, max_x: float) -> float | None:
    """在横版卡 [25%, 75%] 寻找两栏分界走廊的中心 x 坐标（兼容 1/3+2/3、1/2+1/2、2/3+1/3 分栏）。"""
    if len(words) < 4:
        return None
    span = max_x - min_x
    mid_min = min_x + span * 0.25
    mid_max = min_x + span * 0.75
    rows = cluster_rows(words)
    best_x = None
    best_gap = -10**9
    gx = mid_min
    while gx <= mid_max:
        # 排除通栏行动标题行（右侧仅有 1AP / 2AP / 0AP）对跨栏的干扰
        crossings = 0
        left_x1s = []
        right_x0s = []
        for _, _, ws in rows:
            r_ws = [w for w in ws if w["x0"] >= gx - 1.0]
            l_ws = [w for w in ws if w["x1"] <= gx + 1.0]
            if l_ws and r_ws and re.fullmatch(r"\d+\s*AP", join_words(r_ws)):
                continue
            for w in ws:
                if w["x0"] < gx - 1.0 and w["x1"] > gx + 1.0:
                    crossings += 1
                elif w["x1"] <= gx + 1.0:
                    left_x1s.append(w["x1"])
                else:
                    right_x0s.append(w["x0"])
        if crossings == 0 and left_x1s and right_x0s:
            max_l = max(left_x1s)
            min_r = min(right_x0s)
            gap = min_r - max_l
            if gap > best_gap:
                best_gap = gap
                best_x = (max_l + min_r) / 2.0
        gx += 1.5
    if best_x is None or best_gap < 3.5:
        return None
    return best_x


def structured_body_text(words, min_x: float, max_x: float, is_two_col: bool = True) -> list[str]:
    """按分栏阅读顺序抽取正文行列表（左栏读完 → 右栏读完，保留通栏行动标题）。"""
    if not words:
        return []
    rows = cluster_rows(words)
    if not is_two_col:
        return [join_words(ws) for _, _, ws in rows if join_words(ws)]

    best_x = _find_gutter_x(words, min_x, max_x)
    if best_x is None:
        return [join_words(ws) for _, _, ws in rows if join_words(ws)]

    # 识别通栏行动标题行（左半有行动名，右半仅有 N AP）作为纵向分段点
    segments: list[tuple[str, list]] = []
    cur_words: list[dict] = []
    for top, bottom, ws in rows:
        l_ws = [w for w in ws if w["x1"] <= best_x + 1.0]
        r_ws = [w for w in ws if w["x0"] >= best_x - 1.0]
        if l_ws and r_ws and re.fullmatch(r"\d+\s*AP", join_words(r_ws)):
            if cur_words:
                segments.append(("cols", cur_words))
                cur_words = []
            segments.append(("full", ws))
        else:
            cur_words.extend(ws)
    if cur_words:
        segments.append(("cols", cur_words))

    lines: list[str] = []
    for kind, seg in segments:
        if kind == "full":
            txt = join_words(seg)
            if txt:
                lines.append(txt)
        else:
            left_w = [w for w in seg if w["x1"] <= best_x + 1.0]
            right_w = [w for w in seg if w["x0"] >= best_x - 1.0]
            for _, _, ws in cluster_rows(left_w):
                txt = join_words(ws)
                if txt:
                    lines.append(txt)
            for _, _, ws in cluster_rows(right_w):
                txt = join_words(ws)
                if txt:
                    lines.append(txt)
    return lines


def find_operative_abilities(words, bbox) -> str:
    """抽取特工数据卡（武器表下方）或特工能力卡上的专属能力/行动/*武器规则全文。"""
    if not words:
        return ""
    x0, y0, w, h = bbox
    is_horiz = w > h
    wt = weapon_table_region(words)
    if wt is not None:
        top_cut = wt[1] + 1.0
    else:
        st = find_stats(words)
        if st is not None:
            # 能力卡顶部重复了特工名与属性块，跳过该头部区域
            stat_words = [
                wd for wd in words
                if wd["top"] <= y0 + 55.0 and (
                    wd["text"] in ("APL", "移动", "豁免", "耐伤", "MOVE", "SAVE", "WOUNDS")
                    or wd["text"] in (st.apl, st.move, st.save, st.wounds)
                )
            ]
            top_cut = (max(wd["bottom"] for wd in stat_words) + 1.5) if stat_words else (y0 + 38.0)
        else:
            # 无属性块的能力卡（如冥工之环/神圣技师之环的技师行动卡）：保留顶部标题栏作为第一行
            top_cut = y0 + 4.0

    # 过滤掉页脚行、底座尺寸圆圈与续页提示
    body_words = []
    for _, _, ws in cluster_rows(words):
        if max(wd["bottom"] for wd in ws) <= top_cut:
            continue
        if _is_footer_row(ws, bbox):
            continue
        body_words.extend(ws)

    if not body_words:
        return ""

    # 若是无属性块且无武器表的横版能力卡，顶部黑底标题栏（y0..y0+32）单列先行输出
    header_lines: list[str] = []
    if wt is None and find_stats(words) is None and is_horiz:
        hdr_w = [wd for wd in body_words if wd["top"] <= y0 + 32.0]
        rest_w = [wd for wd in body_words if wd["top"] > y0 + 32.0]
        if hdr_w:
            for _, _, ws in cluster_rows(hdr_w):
                t = join_words(ws)
                if t:
                    header_lines.append(t)
            body_words = rest_w

    lines = header_lines + structured_body_text(body_words, x0, x0 + w, is_two_col=is_horiz)
    return "\n".join(lines).strip()


def structured_card_text(words_on_page, bbox) -> str:
    """按卡片版式（竖版单栏 / 横版通栏表头+双栏正文+通栏页脚）抽取整卡可读文本。"""
    words = words_in_bbox(words_on_page, bbox)
    if not words:
        return ""
    x0, y0, w, h = bbox
    rows = cluster_rows(words)
    if h >= w:
        return "\n".join(join_words(ws) for _, _, ws in rows if join_words(ws)).strip()

    # 横版卡片（w > h）
    wt = weapon_table_region(words)
    footer_words: list[dict] = []
    main_words: list[dict] = []
    for _, _, ws in rows:
        if _is_footer_row(ws, bbox):
            footer_words.extend(ws)
        else:
            main_words.extend(ws)

    lines: list[str] = []
    if wt is not None:
        top_w = [wd for wd in main_words if wd["bottom"] <= wt[1] + 1.0]
        bot_w = [wd for wd in main_words if wd["bottom"] > wt[1] + 1.0]
        for _, _, ws in cluster_rows(top_w):
            t = join_words(ws)
            if t:
                lines.append(t)
        lines.extend(structured_body_text(bot_w, x0, x0 + w, is_two_col=True))
    else:
        st = find_stats(main_words)
        if st is not None:
            hdr_cut = y0 + 42.0
        else:
            # 找第一个出现右半栏文字（且非单纯 N AP）的行，其上方视为通栏标题区
            hdr_cut = y0 + 34.0
            for r_top, _, ws in cluster_rows(main_words):
                if r_top > y0 + 68.0:
                    break
                r_ws = [wd for wd in ws if (wd["x0"] + wd["x1"]) / 2 >= x0 + w * 0.48]
                l_ws = [wd for wd in ws if (wd["x0"] + wd["x1"]) / 2 < x0 + w * 0.48]
                if l_ws and r_ws and not re.fullmatch(r"\d+\s*AP", join_words(r_ws)):
                    hdr_cut = r_top - 1.0
                    break
        hdr_w = [wd for wd in main_words if wd["top"] <= hdr_cut]
        bot_w = [wd for wd in main_words if wd["top"] > hdr_cut]
        for _, _, ws in cluster_rows(hdr_w):
            t = join_words(ws)
            if t:
                lines.append(t)
        lines.extend(structured_body_text(bot_w, x0, x0 + w, is_two_col=True))

    for _, _, ws in cluster_rows(footer_words):
        t = join_words(ws)
        if t:
            lines.append(t)
    return "\n".join(lines).strip()


# ---------------------------------------------------------------- 图标指纹

ORANGE_MIN_R = 120
ORANGE_MIN_DIFF = 45
CHROMA_MIN = 40  # 彩色图标与中性灰底/黑字的最小色度差


def orange_mask(img: Image.Image) -> Image.Image:
    """返回橙色像素的二值掩膜（255=橙）。"""
    r, g, b = img.convert("RGB").split()
    dg = ImageChops.subtract(r, g)
    db = ImageChops.subtract(r, b)
    mg = dg.point(lambda v: 255 if v > ORANGE_MIN_DIFF else 0)
    mb = db.point(lambda v: 255 if v > ORANGE_MIN_DIFF else 0)
    m = ImageChops.multiply(mg, mb)
    m = ImageChops.multiply(m, r.point(lambda v: 255 if v > ORANGE_MIN_R else 0))
    return m


def chromatic_mask(img: Image.Image) -> Image.Image:
    """返回彩色像素的二值掩膜（255=彩色）。

    不同队伍的武器类型图标用不同颜色（橙红、青、黄……），
    这里不限定色相，只要求「明显带彩」：max(R,G,B) - min(R,G,B) 超阈值。
    黑字与灰底（色度 0）因此被排除。
    """
    r, g, b = img.convert("RGB").split()
    mx = ImageChops.lighter(ImageChops.lighter(r, g), b)
    mn = ImageChops.darker(ImageChops.darker(r, g), b)
    chroma = ImageChops.subtract(mx, mn)
    return chroma.point(lambda v: 255 if v > CHROMA_MIN else 0)


def connected_blobs(mask: Image.Image, min_pixels: int = 12):
    """在二值掩膜里做连通域，返回 [(x0,y0,x1,y1,npix)]。

    先逐行用 C 层 find 抽出连续区间，再按纵向重叠合并成连通域。
    """
    W, H = mask.size
    rows: list[list[tuple[int, int]]] = []
    for y in range(H):
        raw = mask.crop((0, y, W, y + 1)).tobytes()
        runs = []
        pos = 0
        while True:
            i = raw.find(255, pos)
            if i < 0:
                break
            j = raw.find(0, i)
            if j < 0:
                j = len(raw)
            runs.append((i, j - 1))
            pos = j
        rows.append(runs)

    blobs = []
    active: list[dict] = []
    for y, runs in enumerate(rows):
        merged = []
        for r in runs:
            placed = False
            for b in active:
                if r[0] <= b["x1"] and b["x0"] <= r[1]:
                    b["x0"] = min(b["x0"], r[0])
                    b["x1"] = max(b["x1"], r[1])
                    b["y1"] = y
                    b["n"] += r[1] - r[0] + 1
                    placed = True
                    break
            if not placed:
                merged.append({"x0": r[0], "x1": r[1], "y0": y, "y1": y, "n": r[1] - r[0] + 1})
        still = []
        for b in active:
            if b["y1"] >= y - 1:
                still.append(b)
            else:
                blobs.append(b)
        active = still + merged
    for b in active:
        blobs.append(b)
    return [(b["x0"], b["y0"], b["x1"], b["y1"], b["n"]) for b in blobs if b["n"] >= min_pixels]


def _resize_binary(mask_crop: Image.Image, size: int = 16) -> bytes:
    """双线性重采样到 size×size 后二值化，减弱 ±1px 抗锯齿抖动。"""
    small = mask_crop.convert("L").resize((size, size), Image.BILINEAR)
    small = small.point(lambda v: 255 if v >= 110 else 0)
    return small.tobytes()


def fingerprint(mask: Image.Image, bbox, size: int = 16) -> tuple[str, float, tuple, str]:
    """对橙色连通域生成归一化指纹。

    返回 (key, 宽高比, bbox, 位图十六进制)；key = sha1(位图 + 宽高比分桶)。
    """
    x0, y0, x1, y1 = bbox
    w = x1 - x0 + 1
    h = y1 - y0 + 1
    crop = mask.crop((x0, y0, x1 + 1, y1 + 1))
    bits = _resize_binary(crop, size)
    ratio = w / h if h else 0.0
    bucket = int(round(ratio * 4))
    key = hashlib.sha1(bits + bucket.to_bytes(2, "big")).hexdigest()[:12]
    return key, ratio, (w, h), bits.hex()


def weapon_table_columns(words) -> dict:
    """返回武器表表头的各列 x 边界（含 name / atk / hit / dmg / wr）。"""
    rows = cluster_rows(words)
    cols = {}
    for top, bottom, ws in rows:
        texts = [w["text"] for w in ws]
        if (any("名称" in t for t in texts) and any("攻击" in t for t in texts)) or \
           (any(t.upper() == "NAME" for t in texts) and any(t.upper() in ("ATK", "A") for t in texts)):
            for w in ws:
                t = w["text"]
                if "名称" in t or t.upper() == "NAME":
                    cols["name"] = w["x0"]
                elif "攻击" in t or t.upper() in ("ATK", "A"):
                    cols["atk"] = w["x0"]
                elif "命中" in t or t.upper() == "HIT":
                    cols["hit"] = w["x0"]
                elif "伤害" in t or t.upper() in ("DMG", "D"):
                    cols["dmg"] = w["x0"]
                elif "武器规则" in t or t.upper() == "WR":
                    cols["wr"] = w["x0"]
            break
    return cols


def find_weapon_icons(strip: Image.Image, weapons, region_pt, dpi: int, name_col_x: float | None = None):
    """在武器表条带图里定位每行武器的类型图标。

    strip      : 武器表区域的渲染图
    weapons    : Weapon 列表（需带 top / bottom）
    region_pt  : (x0, top, x1, bottom) 的 PDF 点坐标，与 strip 对应
    dpi        : strip 的渲染 DPI
    name_col_x : 武器名列的 PDF x；图标位于其左侧
    """
    rx0, rtop, rx1, rbottom = region_pt
    scale = dpi / 72.0
    strip_w = strip.size[0]

    # 多数队伍的图标是橙红色；少数队伍用其他颜色（青、黄……），用彩色掩膜兜底。
    masks = []
    for m in (orange_mask(strip), chromatic_mask(strip)):
        blobs = connected_blobs(m)
        # 横向长条是表格分隔线，不是图标
        icons = [b for b in blobs if (b[2] - b[0] + 1) < strip_w * 0.2]
        masks.append((m, icons))

    for w in weapons:
        # 图标行带：略高于文字行顶，向下延伸到该行中段
        y0 = (w.top - 3.0 - rtop) * scale
        y1 = (w.top + 9.0 - rtop) * scale
        mask = None
        cand = []
        for m, icons in masks:
            found = []
            for b in icons:
                cy = (b[1] + b[3]) / 2
                if not (y0 <= cy <= y1):
                    continue
                if name_col_x is not None:
                    bx1 = rx0 + b[2] / scale
                    if bx1 > name_col_x - 2:
                        continue
                found.append(b)
            if found:
                mask, cand = m, found
                break
        if not cand and name_col_x is not None:
            # 兜底：不限制左右，取该行带内最靠左的一组
            for m, icons in masks:
                found = [b for b in icons if y0 <= (b[1] + b[3]) / 2 <= y1]
                if found:
                    mask = m
                    found.sort(key=lambda b: b[0])
                    cand = found[:1]
                    break
        if not cand:
            w.icon_key = ""
            continue
        bx0 = min(b[0] for b in cand)
        by0 = min(b[1] for b in cand)
        bx1 = max(b[2] for b in cand)
        by1 = max(b[3] for b in cand)
        key, ratio, dims, bits = fingerprint(mask, (bx0, by0, bx1, by1))
        w.icon_key = key
        w.icon_dims = dims
        w.icon_ratio = ratio
        w.icon_bitmap = bits
        w.icon_bbox = (bx0, by0, bx1, by1)
    return weapons


# ---------------------------------------------------------------- 页文本

def page_text(pdf_path: str, page_no: int) -> str:
    out = subprocess.run(
        ["pdftotext", "-f", str(page_no), "-l", str(page_no), "-layout", "-nopgbrk", pdf_path, "-"],
        check=True, capture_output=True, text=True,
    ).stdout
    return "\n".join(ln.rstrip() for ln in out.splitlines() if ln.strip())


def page_count(pdf_path: str) -> int:
    out = subprocess.run(["pdfinfo", pdf_path], check=True, capture_output=True, text=True).stdout
    m = re.search(r"^Pages:\s+(\d+)", out, re.M)
    return int(m.group(1)) if m else 0
