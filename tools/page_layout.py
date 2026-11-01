#!/usr/bin/env python3
"""更新日志 / 勘误页的结构化文本提取。

官方 PDF 的更新日志页是「通栏标题 + 若干两栏区块」：

    30.5pt  页面标题        流亡驭龙师：更新日志
    17.0pt  分节标题        勘误                2026 年 8 月
     9.5pt  通栏导语
    12.0pt  栏内小标题      墓穴爬行者特工，维度放逐武器规则
     8.5pt  正文（左右两栏）

pdftotext -layout 只能保留视觉上的并排，落到 Markdown 里就变成两栏交错、
无法阅读。本模块在字符级切分出「行内片段」，按字号分节、按 x 位置判定
左右栏，再按「左栏读完 → 右栏读完」的顺序输出。

注意：中文没有空格，pdfplumber 的 extract_words 会把同一视觉行里的左右栏
文字合并成一个「词」，因此这里一律用 chars 自己切。

用法：
    python3 tools/page_layout.py <pdf> <页码>
"""
from __future__ import annotations

import re
import sys

import pdfplumber

CJK_RE = re.compile(r"[\u3000-\u303f\u3400-\u9fff\uff00-\uffef]")
DATE_RE = re.compile(r"\d{4}\s*年\s*\d{1,2}\s*月")
LOG_HEAD_RE = re.compile(r"勘误|规则注解|更新日志")

TITLE_SIZE = 25.0    # >= 页面标题
SECTION_SIZE = 15.0  # >= 分节标题（勘误 / 规则注解）
SUB_SIZE = 11.0      # >= 栏内小标题
BODY_SIZE = 9.9      # > 通栏导语

SEG_GAP = 6.0        # 行内片段的最大字符间距
MIN_SPLIT_SCORE = 0.85


def _is_latin(ch: str) -> bool:
    return bool(re.match(r"[A-Za-z0-9]", ch))


def _seg_text(chars) -> str:
    """把一个片段的字符拼成文本（中文不加空格，中英之间加空格）。"""
    out = ""
    prev = None
    for c in chars:
        t = c["text"]
        if not out:
            out = t
            prev = c
            continue
        gap = c["x0"] - prev["x1"]
        if gap > 0.6 and not (CJK_RE.search(out[-1]) and CJK_RE.match(t)):
            out += " " + t
        elif gap > 0.6 and CJK_RE.search(out[-1]) and CJK_RE.match(t):
            out += t
        else:
            out += t
        prev = c
    return out


def _join_parts(parts):
    """把同一行内的若干片段拼起来。"""
    out = ""
    for t in parts:
        if not out:
            out = t
            continue
        if CJK_RE.search(out[-1]) and CJK_RE.match(t):
            out += t
        else:
            out += " " + t
    return out


def _group_lines(chars):
    """字符 → 行（按纵向重叠聚类）。"""
    rows: list[list[dict]] = []
    for c in sorted(chars, key=lambda z: (z["top"], z["x0"])):
        h = max(c["bottom"] - c["top"], 0.1)
        placed = False
        for row in rows:
            last = row[-1]
            lh = max(last["bottom"] - last["top"], 0.1)
            overlap = min(c["bottom"], last["bottom"]) - max(c["top"], last["top"])
            if overlap > 0.5 * min(h, lh):
                row.append(c)
                placed = True
                break
        if not placed:
            rows.append([c])
    lines = []
    for row in rows:
        row.sort(key=lambda z: z["x0"])
        segs = []
        cur = [row[0]]
        for c in row[1:]:
            if c["x0"] - cur[-1]["x1"] > SEG_GAP:
                segs.append(cur)
                cur = [c]
            else:
                cur.append(c)
        segs.append(cur)
        line = {
            "top": min(c["top"] for c in row),
            "bottom": max(c["bottom"] for c in row),
            "size": max(c.get("size", 0) for c in row),
            "segs": [{
                "x0": min(c["x0"] for c in s),
                "x1": max(c["x1"] for c in s),
                "size": max(c.get("size", 0) for c in s),
                "text": _seg_text(s),
            } for s in segs if _seg_text(s).strip()],
        }
        lines.append(line)
    lines.sort(key=lambda z: z["top"])
    return lines


def _best_split(segs, width):
    """找把片段分成左右两栏的竖直切分线。

    指标 = (完全在左 + 完全在右) / 总数。通栏导语会横跨切分线，
    所以用「有多少片段不被切开」而不是「左右是否均衡」。
    """
    if len(segs) < 4:
        return None
    # 用精确坐标做候选，避免四舍五入把切分线挪到片段边缘之外
    cands = sorted({s["x0"] for s in segs} | {s["x1"] for s in segs})
    best = (0.0, None)
    for s in cands:
        if not (width * 0.18 <= s <= width * 0.82):
            continue
        left = sum(1 for g in segs if g["x1"] <= s + 0.01)
        right = sum(1 for g in segs if g["x0"] >= s - 0.01)
        score = (left + right) / len(segs)
        if score > best[0]:
            best = (score, s)
    if best[1] is None or best[0] < MIN_SPLIT_SCORE:
        return None
    s = best[1]
    eps = 0.01
    left = [g for g in segs if g["x1"] <= s + eps]
    right = [g for g in segs if g["x0"] >= s - eps]
    if len(left) < 2 or len(right) < 2:
        return None
    # 把切分线放到栏间空隙的正中，保证没有任何片段跨界
    gap_lo = max(g["x1"] for g in left)
    gap_hi = min(g["x0"] for g in right)
    if gap_hi > gap_lo:
        return (gap_lo + gap_hi) / 2
    return s


def extract_log_page(pdf_path: str, page_no: int):
    """返回该页的结构化块列表；无法解析时返回 None。"""
    with pdfplumber.open(pdf_path) as doc:
        page = doc.pages[page_no - 1]
        chars = [c for c in page.chars if c["top"] < page.height - 35]
        width, height = page.width, page.height
    if not chars:
        return None
    lines = _group_lines(chars)
    if not lines:
        return None

    blocks = []
    cur = {"heading": None, "lines": []}
    for ln in lines:
        if SECTION_SIZE <= ln["size"] < TITLE_SIZE:
            if cur["heading"] or cur["lines"]:
                blocks.append(cur)
            cur = {"heading": ln, "lines": []}
        else:
            cur["lines"].append(ln)
    if cur["heading"] or cur["lines"]:
        blocks.append(cur)

    # 整页都没有「勘误 / 规则注解」分节 → 这不是更新日志页
    def _head_name(blk):
        if blk["heading"] is None:
            return ""
        parts = [s["text"] for s in blk["heading"]["segs"]
                 if not (s["x0"] >= width * 0.60 and DATE_RE.search(s["text"]))]
        return _join_parts(parts)

    if not any(LOG_HEAD_RE.search(_head_name(b)) for b in blocks):
        return None

    out = []
    for blk in blocks:
        heading = blk["heading"]
        body = blk["lines"]
        if heading is None:
            out.append({
                "kind": "lead",
                "text": "\n".join(_join_parts([s["text"] for s in l["segs"]]) for l in body),
            })
            continue
        # 分节标题：左侧是名称，右侧（页面 60% 之后）是日期
        date_txt = ""
        name_parts = []
        for s in heading["segs"]:
            if s["x0"] >= width * 0.60 and DATE_RE.search(s["text"]):
                date_txt = s["text"]
            else:
                name_parts.append(s["text"])
        name = _join_parts(name_parts)
        if not LOG_HEAD_RE.search(name):
            # 不是勘误 / 规则注解类的分节标题，说明这页根本不是更新日志页
            continue
        # 正文分栏：按「片段」判定归属（同一视觉行里左右栏各有一段）
        body_segs = [s for l in body for s in l["segs"] if s["size"] < SUB_SIZE]
        split = _best_split(body_segs, width)
        left_segs, right_segs = [], []
        for idx, l in enumerate(body):
            for s in l["segs"]:
                if s["size"] >= SUB_SIZE:
                    # 栏内小标题：按起点归栏
                    tgt = left_segs if (split is None or s["x0"] < split) else right_segs
                elif split is None:
                    tgt = left_segs
                elif s["x1"] <= split:
                    tgt = left_segs
                elif s["x0"] >= split:
                    tgt = right_segs
                else:
                    continue  # 横跨切分线的通栏内容不归入任何一栏
                tgt.append((idx, s))

        def _rebuild(segs):
            """把某栏的片段按原行号还原成行。"""
            rows = []
            cur_idx = None
            buf = []
            for idx, s in segs:
                if idx != cur_idx:
                    if buf:
                        rows.append(_join_parts(buf))
                    cur_idx = idx
                    buf = []
                buf.append(s["text"])
            if buf:
                rows.append(_join_parts(buf))
            return rows

        out.append({
            "kind": "section",
            "name": name,
            "date": date_txt,
            "columns": [_rebuild(left_segs), _rebuild(right_segs)],
            "split": split,
        })
    return out


def render_log_page(blocks) -> str:
    """把结构化块渲染成 Markdown。"""
    lines = []
    for blk in blocks:
        if blk["kind"] == "lead":
            for para in blk["text"].splitlines():
                if para.strip():
                    lines.append(para.strip())
            if blk["text"].strip():
                lines.append("")
            continue
        title = blk["name"].strip()
        if blk["date"]:
            title = f"{title}（{blk['date'].strip()}）"
        lines.append(f"**{title}**")
        lines.append("")
        for col in blk["columns"]:
            for para in col:
                if para.strip():
                    lines.append(para.strip())
            if any(p.strip() for p in col):
                lines.append("")
    while lines and not lines[-1]:
        lines.pop()
    return "\n".join(lines)


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    pdf, page_no = sys.argv[1], int(sys.argv[2])
    blocks = extract_log_page(pdf, page_no)
    if blocks is None:
        print("（无法解析）")
        return 1
    print(render_log_page(blocks))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
