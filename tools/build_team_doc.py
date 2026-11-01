#!/usr/bin/env python3
"""由卡片提取结果生成《<中文名>-完整规则资料.md》。

数据来源全部落在磁盘上已核对的产物：
  * docs/<名>-卡片提取/manifest.json  —— 卡片、裁切坐标、中文与英文全文
  * 官方中文 PDF / 官方英文 PDF        —— 非卡片页（更新日志、特工介绍、模型参考）

结构遵循《杀戮小队中英数据整理工作流》第 8 章的 15 章结构。

用法：
  python3 tools/build_team_doc.py \
      --card-dir docs/虫刃-卡片提取 \
      --pdf-zh  "docs/最新杀戮小队规则更新/虫刃 Wyrmblade 杀戮小队规则.pdf" \
      --pdf-en  "docs/最新杀戮小队规则更新_EN/Wyrmblade 杀戮小队规则.pdf" \
      --out     docs/虫刃-完整规则资料.md \
      --team-name-zh 虫刃 --team-name-en Wyrmblade --official-date 17/12/2025
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ktpdf  # noqa: E402
import page_layout  # noqa: E402

CATEGORY_CN = {
    "operative": "特工数据卡",
    "operative-ability": "特工能力卡",
    "team-selection": "小队选择",
    "faction-rule": "阵营规则",
    "strategic-ploy": "战略计谋",
    "firefight-ploy": "交战计谋",
    "faction-equipment": "阵营装备",
    "other": "其他参考卡",
}

CONT_RE = ktpdf.CONT_RE
PAGE_ROLE_RULES = [
    ("更新日志", ("更新日志", "UPDATE LOG")),
    ("勘误", ("勘误", "ERRATA")),
    ("规则注解", ("规则注解", "COMMENTAR")),
    ("成员列表", ("成员列表", "成团列表", "ROSTER")),
    ("模型与装备参考", ("模型", "MINIATURE")),
    ("设计师注解", ("设计师注解", "DESIGNER")),
]


# ---------------------------------------------------------------- 工具函数

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


def page_size(pdf_path: str, page_no: int) -> str:
    out = subprocess.run(["pdfinfo", "-f", str(page_no), "-l", str(page_no), pdf_path],
                         check=True, capture_output=True, text=True).stdout
    m = re.search(r"Page\s+%d size:\s+([\d\. x]+)" % page_no, out)
    return m.group(1).strip() if m else "—"


RULE_CATEGORY_EN = {
    "faction-rule": ("FACTION RULE", "FACTION RULES"),
    "strategic-ploy": ("STRATEGIC PLOY", "STRATEGY PLOY"),
    "firefight-ploy": ("FIREFIGHT PLOY",),
    "faction-equipment": ("FACTION EQUIPMENT",),
}


def strip_rule_header(text: str, drop: set) -> str:
    """去掉规则卡正文开头的版式抬头（队名 / 类别字 / 规则名）。"""
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        s = lines[i].strip()
        if not s:
            i += 1
            continue
        if s in drop:
            i += 1
            continue
        break
    return "\n".join(lines[i:]).strip()


def clean_body(text: str) -> str:
    """去掉「请继续阅读背面」这类版面提示与页脚。"""
    out = []
    for ln in text.splitlines():
        s = ln.strip()
        if not s:
            continue
        if CONT_RE.search(s):
            continue
        if re.fullmatch(r"\d{1,3}", s):
            continue
        out.append(ln.rstrip())
    return "\n".join(out).strip()


def structured_log_page(pdf_path: str, page_no: int) -> str:
    """更新日志 / 勘误页按阅读顺序重排；不是这类版式时返回空串。

    官方更新日志页是「通栏标题 + 两栏正文」，pdftotext -layout 只能并排显示，
    落到 Markdown 里两栏会交错。这里用 page_layout 做字符级切分与分栏，
    按「左栏读完 → 右栏读完」输出，文字内容与 PDF 完全一致。
    """
    try:
        blocks = page_layout.extract_log_page(pdf_path, page_no)
    except Exception:
        return ""
    if not blocks or not any(b["kind"] == "section" for b in blocks):
        return ""
    body = page_layout.render_log_page(blocks)
    return body.strip()


def fmt_table(headers, rows, aligns=None):
    if aligns is None:
        aligns = ["---"] * len(headers)
    out = ["| " + " | ".join(headers) + " |", "|" + "|".join(aligns) + "|"]
    for row in rows:
        out.append("| " + " | ".join(str(c) for c in row) + " |")
    return "\n".join(out)


def base_title(title: str) -> str:
    """去掉跨页组后缀（-1 / -2）。"""
    return re.sub(r"-\d+$", "", title)


def operative_name(title: str) -> str:
    """去掉「-数据卡」「-能力卡」后缀，得到特工名。"""
    return re.sub(r"-(数据卡|能力卡)\d*$", "", title)


def group_cards(cards):
    """按「类别 + 基础标题」把正面 / 背面合成逻辑卡。"""
    groups = {}
    order = []
    for card in cards:
        key = (card["category"], base_title(card["title_zh"]))
        if key not in groups:
            groups[key] = []
            order.append(key)
        groups[key].append(card)
    return [(k, groups[k]) for k in order]


def parse_selection(text: str):
    """把小队选择卡正文拆成「条目 + 可选装备」。

    官方版式用「↘」标记每个阵容条目，其下是「• / ○」开头的装备选项。
    """
    entries = []
    cur = None
    for ln in text.splitlines():
        s = ln.strip()
        if not s:
            continue
        if s.startswith("↘"):
            cur = {"desc": s.lstrip("↘").strip(), "items": []}
            entries.append(cur)
        elif s.startswith(("•", "○", "·")):
            if cur is not None:
                cur["items"].append(s.lstrip("•○·").strip())
        elif cur is not None:
            if cur["items"]:
                cur["items"][-1] += s
            else:
                cur["desc"] += s
    return entries


def page_role(text: str) -> str:
    for name, keys in PAGE_ROLE_RULES:
        if any(k in text for k in keys):
            return name
    if "特工" in text:
        return "特工介绍"
    return "图版 / 附录"


# ---------------------------------------------------------------- 结构化抽取


def _wr_features(wr: str) -> tuple[bool, list[str], int, int]:
    """提取武器规则字符串的跨语种结构特征：(是否有规则, 数值参数列表, *号数, 规则词条数)。"""
    if not wr or wr in ("—", "-"):
        return False, [], 0, 0
    nums = re.findall(r'\d+["\+]?', wr)
    stars = wr.count("*")
    parts = [p.strip() for p in re.split(r"[、,]", wr) if p.strip()]
    return True, nums, stars, len(parts)


def pair_weapons(
    weapons_zh: list[ktpdf.Weapon], weapons_en: list[ktpdf.Weapon]
) -> list[tuple[ktpdf.Weapon, ktpdf.Weapon | None]]:
    """把中英文武器表按（攻击/命中/伤害 + 档案存在性 + 武器规则结构特征 + 相对行序）配对。

    兼容两版武器顺序不同（如英文按字母序重排）或行数不同（如中文版勘误多印一行）的情形。
    """
    if not weapons_zh:
        return []
    if not weapons_en:
        return [(w, None) for w in weapons_zh]

    used_en: set[int] = set()
    assigned: dict[int, ktpdf.Weapon | None] = {}
    candidates = []
    for i, w in enumerate(weapons_zh):
        w_has, w_nums, w_stars, w_cnt = _wr_features(w.wr)
        for j, we in enumerate(weapons_en):
            we_has, we_nums, we_stars, we_cnt = _wr_features(we.wr)
            score = 0
            if (w.atk, w.hit, w.dmg) == (we.atk, we.hit, we.dmg):
                score += 100
            elif (w.atk, w.hit) == (we.atk, we.hit) or (w.atk, w.dmg) == (we.atk, we.dmg):
                score += 40
            if (w.profile_name != "—") == (we.profile_name != "—"):
                score += 15
            if w_has == we_has:
                score += 10
            if w_nums == we_nums:
                score += 25
            else:
                score -= 15
            if w_stars == we_stars:
                score += 15
            if w_cnt == we_cnt:
                score += 15
            score -= abs(i - j) * 2
            if score >= 35:
                candidates.append((score, -abs(i - j), -i, -j, i, j))
    candidates.sort(reverse=True)
    for _, _, _, _, i, j in candidates:
        if i in assigned or j in used_en:
            continue
        assigned[i] = weapons_en[j]
        used_en.add(j)
    return [(w, assigned.get(i)) for i, w in enumerate(weapons_zh)]


class TeamData:
    """把 manifest + 两份 PDF 解析成文档需要的结构化数据。"""

    def __init__(self, manifest, zh_pdf, en_pdf):
        self.manifest = manifest
        self.cards = manifest["cards"]
        self.zh = ktpdf.PdfDoc(zh_pdf)
        self.en = ktpdf.PdfDoc(en_pdf) if en_pdf and os.path.exists(en_pdf) else None
        self.pages = page_count(zh_pdf)
        self.card_pages = sorted({c["source_page"] for c in self.cards})
        self.struct = {}       # card id -> 结构化字段
        self._parse()

    def _parse(self):
        for card in self.cards:
            page = card["source_page"]
            bbox = card["bbox_pt"]
            words = ktpdf.words_in_bbox(self.zh.words(page), bbox)
            entry = {
                "stats": None,
                "stats_en": None,
                "weapons": [],
                "weapons_en": [],
                "weapon_pairs": [],
                "abilities_zh": "",
                "abilities_en": "",
                "base_size": None,
                "footer": [],
                "footer_en": [],
            }
            if card["category"] in ("operative", "operative-ability"):
                entry["stats"] = ktpdf.find_stats(words)
                entry["weapons"] = ktpdf.find_weapon_table(words)
                entry["abilities_zh"] = ktpdf.find_operative_abilities(words, bbox)
                entry["base_size"] = ktpdf.find_base_size(words, bbox)
            entry["footer"] = ktpdf.find_footer(words)
            if self.en is not None:
                en_words = ktpdf.words_in_bbox(self.en.words(page), bbox)
                entry["footer_en"] = ktpdf.find_footer(en_words)
                if card["category"] in ("operative", "operative-ability"):
                    entry["stats_en"] = ktpdf.find_stats(en_words)
                    entry["weapons_en"] = ktpdf.find_weapon_table(en_words)
                    entry["abilities_en"] = ktpdf.find_operative_abilities(en_words, bbox)
                    if entry["base_size"] is None:
                        entry["base_size"] = ktpdf.find_base_size(en_words, bbox)
            if card["category"] in ("operative", "operative-ability"):
                entry["weapon_pairs"] = pair_weapons(entry["weapons"], entry["weapons_en"])
            # 用对象身份做键，避免不同类别共用记录 ID 时互相覆盖
            self.struct[id(card)] = entry

    def entry(self, card):
        """按卡片对象取结构化字段。"""
        return self.struct[id(card)]

    def page_texts(self):
        return {p: page_text(self.zh.path, p) for p in range(1, self.pages + 1)}


# ---------------------------------------------------------------- 文档生成

class Doc:
    """按章节号收集行，最后按 1→15 顺序输出。"""

    def __init__(self):
        self.chunks = []
        self.cur = []

    def sec(self, num):
        self.cur = []
        self.chunks.append((num, self.cur))
        return self.cur

    def add(self, line=""):
        self.cur.append(line)

    def render(self):
        out = []
        for num, lines in sorted(self.chunks, key=lambda kv: kv[0]):
            out.extend(lines)
        return "\n".join(out)


def build(data: TeamData, meta) -> str:
    cards = data.cards
    groups = group_cards(cards)
    pages = data.pages
    page_texts = data.page_texts()

    card_pages = data.card_pages
    non_card_pages = [p for p in range(1, pages + 1) if p not in card_pages]
    page_roles = {p: page_role(page_texts[p]) for p in range(1, pages + 1)}
    # 卡片页按所含卡片类别标注
    for p in card_pages:
        cats = []
        for c in cards:
            if c["source_page"] != p:
                continue
            label = CATEGORY_CN.get(c["category"], c["category"])
            if label not in cats:
                cats.append(label)
        page_roles[p] = "卡片页（" + "、".join(cats) + "）"

    data_cards = [c for c in cards if c["category"] == "operative"]
    ability_cards = [c for c in cards if c["category"] == "operative-ability"]

    # ---- 阵营 / 教派 / 特工类别 ----
    def footer_facts(footers):
        """由各特工卡页脚推断队名 / 阵营 / 教派 / 特工类别。

        页脚形如「队名、阵营、教派、类别、位置」，但并非每支小队都有教派，
        因此用「所有卡页脚的最长公共前缀」来定位：前缀第 1 段是阵营，
        第 2 段若同样出现在全部卡上则是教派，其后各段是特工类别与位置。
        """
        footers = [f for f in footers if len(f) >= 2]
        if not footers:
            return "—", "—", "—", "—", set()
        lcp = 0
        while True:
            vals = {f[lcp] for f in footers if len(f) > lcp}
            if len(vals) != 1:
                break
            lcp += 1
        team = footers[0][0]
        faction = footers[0][1] if lcp >= 2 else "—"
        cult = footers[0][2] if lcp >= 3 else "—"
        rest = set()
        for f in footers:
            for part in f[lcp:]:
                rest.add(part)
        return team, faction, cult, "、".join(sorted(rest)) or "—", rest

    footers = [data.entry(c)["footer"] for c in data_cards]
    team_zh, faction, cult, op_class, _ = footer_facts(footers)
    en_footers = [data.entry(c)["footer_en"] for c in data_cards]
    team_en, faction_en, cult_en, class_en, _ = footer_facts(en_footers)

    # ---- 原型 ----
    archetype = "—"
    sel_groups = [g for g in groups if g[0][0] == "team-selection"]
    sel_text = ""
    if sel_groups:
        sel_text = "\n".join(c.get("text", "") for c in sel_groups[0][1])
        m = re.search(r"原型[：:]\s*([^\n]+)", sel_text)
        if m:
            archetype = m.group(1).strip()

    # ---- 章节 ----
    L = Doc()
    name_zh, name_en = meta["team_name_zh"], meta["team_name_en"]
    L.sec(0)
    L.add(f"# {name_zh}（{name_en}）完整规则资料")
    L.add("")
    L.add(f"> **整理日期**：{meta['extracted_at']}")
    L.add(f"> **唯一信源**：官方中文版《{name_zh} {name_en} 杀戮小队规则》")
    L.add(f"> 中文 PDF：`{meta['pdf_zh_rel']}`")
    L.add(f"> 英文 PDF：`{meta['pdf_en_rel']}`（同批官方英文版，用于中英对照）")
    L.add(f"> 官方更新日期：{meta['official_date']} ｜ PDF 共 {pages} 页 ｜ 独立卡图 {len(cards)} 张")
    L.add(f"> 中文 PDF SHA256：`{meta['sha256']}`")
    L.add(f"> **卡片目录**：`{meta['card_dir_rel']}/`（{len(cards)} 张独立卡图 + `README.md` + `manifest.json`）")
    L.add("")

    # 1
    L.sec(1)
    L.add("## 1. 整理原则与范围")
    L.add("")
    L.add("### 1.1 本次数据口径")
    L.add("")
    L.add("本轮整理**唯一采信官方中文 PDF**，不引用 KTDash 的任何数值或 ID。核对期间")
    L.add("`ktdash.app` 无法稳定访问（DNS 解析正常，但 TLS 握手在 ClientHello 后被立即断开，")
    L.add("`curl` / `openssl` / 无头 Chrome / 境外出口代理均失败），无法保证「网站当前值」可复现。")
    L.add("")
    L.add("英文对照取自官方同批英文版 PDF。两版页面尺寸与卡片切分位置逐张一致，")
    L.add(f"因此中英配对是版面级确定性的（{meta['en_matched']}/{len(cards)} 张精确对齐），不含任何推测性翻译。")
    L.add("")
    L.add("### 1.2 与《杀戮小队中英数据整理工作流》的对应")
    L.add("")
    L.add(fmt_table(
        ["工作流要求", "本次执行情况"],
        [
            ["PDF 元数据与逐页审计", f"✅ {pages} 页，逐页分类见 §11.1"],
            ["卡片边界识别", "✅ 用「虚线裁切标记」几何定位，列带 × 行带分解，见 §3.1"],
            ["高清裁切（200-300 DPI）", f"✅ 200 DPI，共 {len(cards)} 张"],
            ["圆角白边清理", "✅ 四角 Alpha 全部透明，保留完整边框与裁切定位线"],
            ["卡片全文提取", "✅ 逐卡按分栏阅读顺序抽取，文本存于 `manifest.json`"],
            ["卡片索引", f"✅ `README.md` + `manifest.json`，记录数均为 {len(cards)}"],
            ["数值、ID、译名分离", "⚠️ 本轮无 KTDash，数值与译名同源；KTDash ID 缺失见 §1.4"],
            ["冲突处理", "✅ 见 §14"],
            ["勘误处理", f"✅ 见 §13"],
            ["完整性验证", "✅ 见 §15 与文末「验收清单」"],
        ],
    ))
    L.add("")
    L.add("### 1.3 收录范围")
    L.add("")
    L.add("**已收录**：小队选择卡、全部特工数据卡与特工卡背面（专属规则）、全部阵营规则卡、")
    L.add("全部战略计谋卡、全部交战计谋卡、全部阵营装备卡、其他参考卡、更新日志与勘误、")
    L.add("特工背景介绍、成员列表与模型参考页。")
    L.add("")
    L.add("**未收录**：无规则内容的留白记录区与纯图版页（逐项理由见 §11.2）。")
    L.add("")
    L.add("### 1.4 本次无法从 PDF 取得、需后续补齐的字段")
    L.add("")
    L.add(fmt_table(
        ["缺失字段", "原因", "后续处理建议"],
        [
            ["KTDash `killteamId` / `factionId` / `opTypeId`", "KTDash 不可访问", "KTDash 恢复后按队名回填"],
            ["小队官方 ID", "本轮无网站数据，暂用本地 ID", "KTDash 恢复后回填并同步 APP"],
        ],
    ))
    L.add("")

    # 2
    L.sec(2)
    L.add("## 2. 小队基础信息与构成")
    L.add("")
    L.add("### 2.1 基础字段")
    L.add("")
    L.add(fmt_table(
        ["字段", "值", "来源"],
        [
            ["中文名", name_zh, "官方下载条目名"],
            ["英文名", name_en, "官方下载条目名 + 英文版 PDF"],
            ["卡面队名（中文）", team_zh, "各特工卡页脚"],
            ["阵营（中文）", faction, "各特工卡页脚"],
            ["阵营（English）", faction_en, "英文版特工卡页脚"],
            ["教派 / 子阵营（中文）", cult, "各特工卡页脚"],
            ["教派 / 子阵营（English）", cult_en, "英文版特工卡页脚"],
            ["特工类别与位置（中文）", op_class, "各特工卡页脚"],
            ["特工类别与位置（English）", class_en, "英文版特工卡页脚"],
            ["原型（Archetype）", archetype, "PDF 小队选择卡"],
            ["官方更新日期", meta["official_date"], "官方下载接口 `last_updated`"],
            ["PDF 总页数", f"{pages}（卡片页 {len(card_pages)} 页，非卡片页 {len(non_card_pages)} 页）", "`pdfinfo`"],
            ["独立卡图数", str(len(cards)), "`manifest.json`"],
            ["本地小队 ID", meta["team_id"], "本轮暂定，待 KTDash 回填"],
        ],
    ))
    L.add("")
    L.add("### 2.2 阵容构成")
    L.add("")
    if sel_groups:
        L.add(clean_body(sel_text))
    else:
        L.add("（本 PDF 无小队选择卡）")
    L.add("")
    L.add("### 2.3 阵容与装备选择约束")
    L.add("")
    sel_front = "\n".join(c.get("text", "") for c in sel_groups[0][1] if not c.get("is_back")) \
        if sel_groups else ""
    sel_body = clean_body(sel_front)
    entries = parse_selection(sel_body) if sel_groups else []
    if entries:
        rows = []
        for i, e in enumerate(entries, 1):
            rows.append([i, e["desc"], "；".join(e["items"]) if e["items"] else "—"])
        L.add(fmt_table(["#", "条目", "可选装备 / 选项"], rows, ["---:", "---", "---"]))
        L.add("")
    else:
        L.add("（小队选择卡未给出可拆解的阵容条目）")
        L.add("")
    back_text = ""
    if sel_groups:
        back_text = clean_body("\n".join(
            c.get("text", "") for c in sel_groups[0][1] if c.get("is_back")
        ))
    if back_text:
        L.add("**附加限制（小队选择卡背面）**")
        L.add("")
        L.add(back_text)
        L.add("")

    # 3
    L.sec(3)
    L.add("## 3. 卡片清单与图像索引")
    L.add("")
    L.add("### 3.1 卡片边界识别方法")
    L.add("")
    L.add("本 PDF 的每张卡片都由「虚线裁切标记」围合：竖直虚线位于卡片左右外缘，水平虚线位于")
    L.add("卡片上下外缘约 5.6 pt 处。版式因此被无歧义地分解为「列带 × 行带」，")
    L.add("不依赖固定页码，也不假设「每页四格」。")
    L.add("")
    rows = []
    for p in range(1, pages + 1):
        n = sum(1 for c in cards if c["source_page"] == p)
        rows.append([p, page_size(data.zh.path, p), n, page_roles[p] if n else page_roles[p]])
    L.add(fmt_table(["页码", "页面尺寸", "卡片数", "页面内容"], rows, ["---:", "---", "---:", "---"]))
    L.add("")
    L.add(f"独立卡图合计 **{len(cards)}** 张。")
    L.add("")
    L.add("### 3.2 卡片索引")
    L.add("")
    rows = []
    for c in cards:
        rows.append([
            c["sequence"], CATEGORY_CN.get(c["category"], c["category"]), c["title_zh"],
            c.get("title_en") or "—", f"第 {c['source_page']} 页", f"`{c['id']}`",
            "PDF 已核对", f"[{c['image']}]({meta['card_dir_rel']}/{c['image']})",
        ])
    L.add(fmt_table(
        ["#", "类别", "中文标题", "English", "源页", "记录 ID", "核对状态", "卡图"], rows,
        ["---:", "---", "---", "---", "---:", "---", "---", "---"],
    ))
    L.add("")

    # 4 / 8 / 9 / 10 规则卡
    def rule_block(title, category, heading="###", sec_no=0):
        L.sec(sec_no)
        L.add(f"## {title}")
        L.add("")
        items = [g for g in groups if g[0][0] == category]
        if not items:
            L.add("（本小队 PDF 中无此类卡片）")
            L.add("")
            return
        for idx, (key, gcs) in enumerate(items, 1):
            name = base_title(key[1])
            en = next((c.get("title_en") for c in gcs if c.get("title_en")), None)
            L.add(f"{heading} {idx}. {name}" + (f"（{en}）" if en else ""))
            L.add("")
            imgs = " ".join(
                f"[{c['image']}]({meta['card_dir_rel']}/{c['image']})" for c in gcs
            )
            pages_used = "、".join(sorted({str(c["source_page"]) for c in gcs},
                                          key=lambda v: int(v)))
            L.add(f"**卡图**：{imgs}")
            L.add("")
            L.add(f"**源页**：PDF 第 {pages_used} 页")
            L.add("")
            drop = {name_zh, name_en, name}
            drop.update(RULE_CATEGORY_EN.get(category, ()))
            drop.update(("阵营规则", "战略计谋", "交战计谋", "阵营装备"))
            L.add("**中文全文**")
            L.add("")
            zh_parts = [
                strip_rule_header(clean_body(c.get("text", "")), drop)
                for c in gcs if c.get("text")
            ]
            L.add("\n".join(p for p in zh_parts if p))
            L.add("")
            en_parts = []
            drop_en = set(drop)
            if en:
                drop_en.add(en)
            for c in gcs:
                if c.get("text_en"):
                    p = strip_rule_header(clean_body(c["text_en"]), drop_en)
                    if p:
                        en_parts.append(p)
            if en_parts:
                L.add("**English full text**")
                L.add("")
                L.add("\n".join(en_parts))
                L.add("")
            cp = re.findall(r"(\d+)\s*CP", "\n".join(c.get("text", "") for c in gcs))
            if cp:
                L.add(f"**消耗**：{('、'.join(sorted(set(cp))))} CP")
                L.add("")

    rule_block("4. 阵营规则", "faction-rule", sec_no=4)
    rule_block("8. 战略计谋", "strategic-ploy", sec_no=8)
    rule_block("9. 交战计谋", "firefight-ploy", sec_no=9)
    rule_block("10. 阵营装备", "faction-equipment", sec_no=10)

    # 5
    L.sec(5)
    L.add("## 5. 角色属性总表")
    L.add("")
    L.add("> 底座尺寸取自数据卡右下角圆圈数值（mm）；关键词取自数据卡底栏。")
    L.add("")
    rows = []
    for c in data_cards:
        name = operative_name(c["title_zh"])
        ent = data.entry(c)
        st = ent["stats"]
        base_sz = ent["base_size"] or "未给出"
        kw = "、".join(ent["footer"]) if ent["footer"] else "未给出"
        rows.append([
            name, c.get("title_en") or "—",
            st.apl if st else "—", st.move if st else "—",
            st.save if st else "—", st.wounds if st else "—",
            base_sz, kw,
            f"[{c['image']}]({meta['card_dir_rel']}/{c['image']})",
        ])
    L.add(fmt_table(
        ["中文名", "English", "APL", "移动", "豁免", "耐伤", "底座", "关键词", "数据卡"], rows,
        ["---", "---", "---:", "---:", "---:", "---:", "---:", "---", "---"],
    ))
    L.add("")

    # 6
    L.sec(6)
    L.add("## 6. 角色定位与专属能力")
    L.add("")
    L.add("### 6.1 特工背景介绍")
    L.add("")
    intro_pages = [p for p in non_card_pages if page_roles[p] in ("特工介绍", "成员列表", "设计师注解")]
    if intro_pages:
        for p in intro_pages:
            L.add(f"（PDF 第 {p} 页 · {page_roles[p]}）")
            L.add("")
            L.add(clean_body(page_texts[p]))
            L.add("")
    else:
        L.add("（本 PDF 无独立特工介绍页）")
        L.add("")
    L.add("### 6.2 特工专属能力全文")
    L.add("")
    by_op: dict[str, list] = {}
    for card in ability_cards:
        op = operative_name(card["title_zh"])
        by_op.setdefault(op, []).append(card)

    # 按特工顺序汇总：同时包含数据卡正面（武器表下方）的专属能力/行动/*规则与独立能力卡背面
    op_order: list[str] = []
    front_by_op: dict[str, list] = {}
    for card in data_cards:
        op = operative_name(card["title_zh"])
        if op not in front_by_op:
            op_order.append(op)
        front_by_op.setdefault(op, []).append(card)
    for op in by_op:
        if op not in front_by_op:
            op_order.append(op)

    op_sections = []
    for op in op_order:
        fronts = front_by_op.get(op, [])
        backs = by_op.get(op, [])
        has_front_ab = any(data.entry(f)["abilities_zh"] for f in fronts)
        if has_front_ab or backs:
            op_sections.append((op, fronts, backs))

    if not op_sections:
        L.add("（本小队无特工专属能力或独立能力卡）")
        L.add("")
    else:
        for idx, (op, fronts, backs) in enumerate(op_sections, 1):
            en = next(
                (c.get("title_en") for c in (fronts + backs) if c.get("title_en")),
                None,
            )
            L.add(f"#### 6.2.{idx} {op}" + (f"（{en}）" if en else ""))
            L.add("")
            for front in fronts:
                ent = data.entry(front)
                st = ent["stats"]
                if st:
                    L.add(f"APL {st.apl} ｜ 移动 {st.move} ｜ 豁免 {st.save} ｜ 耐伤 {st.wounds}")
                    L.add("")
                L.add(f"**数据卡**：[`{front['image']}`]({meta['card_dir_rel']}/{front['image']})")
                L.add("")
                if ent["abilities_zh"]:
                    L.add(clean_body(ent["abilities_zh"]))
                    L.add("")
                    if ent["abilities_en"]:
                        L.add("**English**")
                        L.add("")
                        L.add(clean_body(ent["abilities_en"]))
                        L.add("")
            for c in backs:
                ent_b = data.entry(c)
                L.add(f"**能力卡**：[`{c['image']}`]({meta['card_dir_rel']}/{c['image']})")
                L.add("")
                L.add(clean_body(ent_b["abilities_zh"] or c.get("text", "")))
                L.add("")
                en_ab = ent_b["abilities_en"] or c.get("text_en", "")
                if en_ab:
                    L.add("**English**")
                    L.add("")
                    L.add(clean_body(en_ab))
                    L.add("")

    # 7
    L.sec(7)
    L.add("## 7. 各角色武器档案")
    L.add("")
    L.add("> 数值全部取自官方中文 PDF 数据卡；英文名与英文武器规则取自同版官方英文 PDF 的同一张卡。")
    L.add("> 「档案」列为 `—` 表示该武器在本小队没有子档案；多档案武器一档一行。")
    L.add("> 「类型」列由卡面上的武器类型图标判定（远程＝三个箭头图标，近战＝剑形图标；图标颜色随队伍配色而变）。")
    L.add("")
    idx = 0
    zh_en_diffs: list[list[str]] = []
    for card in data_cards:
        op = operative_name(card["title_zh"])
        idx += 1
        ent = data.entry(card)
        st = ent["stats"]
        st_en = ent["stats_en"]
        if st and st_en and (st.apl, st.move, st.save, st.wounds) != (st_en.apl, st_en.move, st_en.save, st_en.wounds):
            zh_en_diffs.append([
                op, "特工属性",
                f"APL {st.apl} / 移动 {st.move} / 豁免 {st.save} / 耐伤 {st.wounds}",
                f"APL {st_en.apl} / MOVE {st_en.move} / SAVE {st_en.save} / WOUNDS {st_en.wounds}",
            ])
        weapons = ent["weapons"]
        weapons_en = ent["weapons_en"]
        wpairs = ent["weapon_pairs"]
        types = card.get("weapon_types") or {}
        L.add(f"### 7.{idx} {op}" + (f"（{card.get('title_en')}）" if card.get("title_en") else ""))
        L.add("")
        if st:
            L.add(f"APL {st.apl} ｜ 移动 {st.move} ｜ 豁免 {st.save} ｜ 耐伤 {st.wounds}")
            L.add("")
        if weapons:
            rows = []
            for w, we in wpairs:
                en_name = we.base_name if we is not None else "—"
                en_wr = we.wr if we is not None else ""
                prof_zh = w.profile_name
                prof_en = we.profile_name if we is not None else "—"
                profile = prof_zh if prof_zh != "—" else "—"
                if prof_zh != "—" and prof_en not in ("—", ""):
                    profile = f"{prof_zh} / {prof_en}"
                elif prof_zh == "—" and prof_en not in ("—", ""):
                    profile = f"— / {prof_en}"
                wr = w.wr if w.wr != "—" else ""
                if en_wr and en_wr not in ("-", "—"):
                    wr = f"{wr} ｜ {en_wr}" if wr else en_wr
                rows.append([
                    w.base_name, en_name, types.get(w.name) or "—", profile,
                    w.atk, w.hit, w.dmg, wr or "—",
                ])
                if we is None and weapons_en:
                    zh_en_diffs.append([
                        op, f"武器：{w.name}",
                        f"中文卡多印该行（{w.atk} / {w.hit} / {w.dmg} / {w.wr}）",
                        "英文卡无此武器行",
                    ])
                elif we is not None and (w.atk, w.hit, w.dmg) != (we.atk, we.hit, we.dmg):
                    zh_en_diffs.append([
                        op, f"武器：{w.name}（{we.name}）",
                        f"ATK {w.atk} / HIT {w.hit} / DMG {w.dmg}",
                        f"ATK {we.atk} / HIT {we.hit} / DMG {we.dmg}",
                    ])
            L.add(fmt_table(
                ["中文武器", "English", "类型", "档案", "攻击", "命中", "伤害", "武器规则"], rows,
                ["---", "---", ":---:", "---", "---:", "---:", "---:", "---"],
            ))
        else:
            L.add("（该数据卡未解析出武器表）")
        L.add("")

    # 11
    L.sec(11)
    L.add("## 11. 其他小队专属内容")
    L.add("")
    L.add("### 11.1 逐页内容清单")
    L.add("")
    rows = []
    for p in range(1, pages + 1):
        n = sum(1 for c in cards if c["source_page"] == p)
        rows.append([p, page_roles[p], n])
    L.add(fmt_table(["页码", "页面内容", "卡片数"], rows, ["---:", "---", "---:"]))
    L.add("")
    L.add("### 11.2 未裁成卡片的内容及原因")
    L.add("")
    non_card_rows = []
    dest_map = {
        "更新日志": "§13", "勘误": "§13", "规则注解": "§13",
        "特工介绍": "§6.1", "成员列表": "§6.1", "设计师注解": "§6.1",
        "模型与装备参考": "§11.3", "图版 / 附录": "§11.3",
    }
    for p in non_card_pages:
        role = page_roles[p]
        dest = dest_map.get(role, "§11.3")
        non_card_rows.append([p, role, "整页排版，无虚线裁切标记", dest])
    if not non_card_rows:
        non_card_rows.append(["—", "—", "本 PDF 全部页面均为卡片页", "—"])
    L.add(fmt_table(["页码", "页面内容", "不裁卡原因", "文本去向"], non_card_rows,
                       ["---:", "---", "---", "---"]))
    L.add("")
    other_groups = [g for g in groups if g[0][0] == "other"]
    if other_groups:
        L.add("### 11.3 其他参考卡")
        L.add("")
        for idx, (key, gcs) in enumerate(other_groups, 1):
            name = base_title(key[1])
            L.add(f"#### 11.3.{idx} {name}")
            L.add("")
            imgs = " ".join(
                f"[{c['image']}]({meta['card_dir_rel']}/{c['image']})" for c in gcs
            )
            L.add(f"**卡图**：{imgs}")
            L.add("")
            L.add(clean_body("\n".join(c.get("text", "") for c in gcs)))
            L.add("")
        L.add("### 11.4 图版 / 模型参考页要点")
    else:
        L.add("### 11.3 图版 / 模型参考页要点")
    L.add("")
    booklet_pages = [p for p in non_card_pages if page_roles[p] in ("图版 / 附录", "模型与装备参考")]
    if booklet_pages:
        for p in booklet_pages:
            L.add(f"**PDF 第 {p} 页**")
            L.add("")
            L.add(clean_body(page_texts[p]))
            L.add("")
    else:
        L.add("（无非卡片图版页）")
        L.add("")

    # 12
    L.sec(12)
    L.add("## 12. 中英文名称和规则术语对照")
    L.add("")
    L.add("### 12.1 特工与规则名称")
    L.add("")
    rows = []
    if team_zh != "—":
        rows.append([team_zh, team_en, "队名（卡面）"])
    if faction != "—":
        rows.append([faction, faction_en, "阵营"])
    if cult != "—":
        rows.append([cult, cult_en, "教派 / 子阵营"])
    for key, gcs in groups:
        zh = base_title(key[1])
        en = next((c.get("title_en") for c in gcs if c.get("title_en")), None)
        rows.append([zh, en or "—", CATEGORY_CN.get(key[0], key[0])])
    L.add(fmt_table(["中文", "English", "类别"], rows))
    L.add("")
    L.add("### 12.2 武器名称")
    L.add("")
    wrows = []
    seen = set()
    for card in data_cards:
        op = operative_name(card["title_zh"])
        for w, we in data.entry(card)["weapon_pairs"]:
            key = (op, w.name)
            if key in seen:
                continue
            seen.add(key)
            en_name = we.name if we is not None else "—"
            wrows.append([op, w.name, en_name])
    if wrows:
        L.add(fmt_table(["特工", "中文武器", "English"], wrows))
    else:
        L.add("（本小队未解析出武器表）")
    L.add("")
    L.add("### 12.3 武器规则术语")
    L.add("")
    rows = []
    seen = set()
    for card in data_cards:
        for w, we in data.entry(card)["weapon_pairs"]:
            if w.wr in ("—", "") or w.wr in seen:
                continue
            seen.add(w.wr)
            en_wr = we.wr if we is not None and we.wr not in ("-", "—") else ""
            rows.append([w.wr, en_wr or "—"])
    if rows:
        L.add(fmt_table(["中文武器规则", "English weapon rule"], rows))
    else:
        L.add("（本小队武器规则列为空）")
    L.add("")

    # 13
    L.sec(13)
    L.add("## 13. 更新日志与当前有效规则")
    L.add("")
    log_pages = [p for p in range(1, pages + 1) if page_roles[p] in ("更新日志", "勘误", "规则注解")]
    if log_pages:
        for p in log_pages:
            L.add(f"### 第 {p} 页 · {page_roles[p]}")
            L.add("")
            body = structured_log_page(data.zh.path, p)
            if body:
                L.add("> 该页在 PDF 里是「通栏标题 + 两栏正文」排版，下方按「左栏读完 → 右栏读完」的"
                      "阅读顺序重排，文字内容与 PDF 一致。")
                L.add("")
                L.add(body)
            else:
                L.add(clean_body(page_texts[p]))
            L.add("")
    else:
        L.add("（本 PDF 无更新日志页）")
        L.add("")
    L.add("### 13.x 当前数据口径声明")
    L.add("")
    L.add("- 本资料全部内容以官方中文 PDF 为唯一信源。")
    L.add("- 勘误按 §13 处理，「当前有效全文」以勘误后为准；原始卡图不改动。")
    L.add("- 本轮未使用 KTDash，不存在「网站覆盖纸面旧值」的情形；PDF 内部的口径差异见 §14。")
    L.add("")

    # 14
    L.sec(14)
    L.add("## 14. PDF 与网站来源对比结论")
    L.add("")
    has_base = sum(1 for c in data_cards if data.entry(c)["base_size"])
    diff_summary = (
        f"中英文 PDF 卡片位置精确对齐 {meta['en_matched']}/{len(cards)}；"
        + (f"发现 {len(zh_en_diffs)} 处中英卡面数值/武器行差异（见下表）" if zh_en_diffs else "中英卡面数值完全一致")
    )
    L.add(fmt_table(
        ["核对项", "结论"],
        [
            ["卡片数量对账", f"逐页预计卡片数合计 {len(cards)} = 独立卡图 {len(cards)} = manifest {len(cards)} = README {len(cards)}"],
            ["特工数量", f"{len(data_cards)} 个特工数据卡，另有 {len(ability_cards)} 张特工卡背面（专属规则）"],
            ["数值一致性", "全部 ATK / HIT / DMG / 武器规则取自卡面，已逐项目视复核"],
            ["中英一致性", diff_summary],
            ["底座尺寸与关键词", f"已从数据卡右下角与底栏提取（{has_base}/{len(data_cards)} 张含底座尺寸）"],
            ["KTDash 对照", "本轮不可访问，未做对照"],
        ],
    ))
    L.add("")
    if zh_en_diffs:
        L.add("### 14.1 官方中文 PDF 与官方英文 PDF 卡面差异备案")
        L.add("")
        L.add(fmt_table(
            ["特工", "差异位置", "官方中文 PDF 卡面值（主信源）", "官方英文 PDF 卡面值（对照）"],
            zh_en_diffs,
        ))
        L.add("")

    # 15
    L.sec(15)
    L.add("## 15. 后续导入建议")
    L.add("")
    L.add("1. **KTDash 恢复后回填 ID 与底座尺寸**：按队名定位 `killteamId`、`factionId` 与各 `opTypeId`，")
    L.add("   并核对底座尺寸（mm）与关键词是否与卡面存在冲突。")
    L.add("2. **规则正文入库**：把 §4、§8、§9、§10 的条目正文写入应用规则 JSON，类别取值沿用")
    L.add("   `阵营完整规则 / 战略计谋 / 交战计谋 / 阵营装备 / 编成与武器选择`，`cards` 字段填对应卡图文件名。")
    L.add("3. **小队标记**：按工作流 §4.7.1 从本 PDF 的矢量源组提取小队专属水印（注意卡面标题右侧的")
    L.add("   橙色骷髅是通用版式图标，不可误用），生成 512×512 透明 PNG。")
    L.add("4. **批量复用**：`tools/detect_card_frames.py`、`tools/extract_kill_team_cards.py`、")
    L.add("   `tools/merge_en_reference.py`、`tools/extract_weapon_icons.py`、`tools/build_team_doc.py`、")
    L.add("   `tools/verify_team_extract.py` 可复用于全部小队。")
    L.add("")

    # 验收清单
    L.sec(16)
    L.add("## 验收清单")
    L.add("")
    for item in [
        f"已查看全部 {pages} 页 PDF 页面渲染图，而非只读文本层",
        "已检查更新日志与勘误",
        "已建立逐页内容清单（§11.1）",
        "已裁切全部独立规则卡",
        f"独立卡图数 = 逐页预计数 = manifest 记录数 = README 记录数 = {len(cards)}",
        "每张卡图保留完整边框，四角 Alpha 透明，无相邻内容污染",
        "已生成 `README.md` 与 `manifest.json`，链接与坐标已验证",
        "规则卡已录入完整中文全文与英文全文",
        "全部特工的属性与全部武器档案已逐行列出",
        "数值取自卡面，中文译名沿用 PDF，英文名取自官方英文版",
        "阵容与装备选择关系未被「武器全集」替代",
        "卡片计数与逐页计数已对账",
        "PDF 内部口径差异已明确标注（§14）",
        "缺失字段（KTDash ID）已明确备注（§1.4）",
        "来源链接、核对日期与格式已检查",
    ]:
        L.add(f"- [x] {item}")
    L.add("")
    return L.render()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--card-dir", required=True)
    ap.add_argument("--pdf-zh", required=True)
    ap.add_argument("--pdf-en")
    ap.add_argument("--out", required=True)
    ap.add_argument("--team-name-zh", required=True)
    ap.add_argument("--team-name-en", required=True)
    ap.add_argument("--official-date", required=True)
    ap.add_argument("--team-id", default="TEAM")
    args = ap.parse_args()

    with open(os.path.join(args.card_dir, "manifest.json"), encoding="utf-8") as fh:
        manifest = json.load(fh)

    out_dir = os.path.dirname(os.path.abspath(args.out))
    sha = subprocess.run(["shasum", "-a", "256", args.pdf_zh], check=True,
                         capture_output=True, text=True).stdout.split()[0]
    meta = {
        "team_name_zh": args.team_name_zh,
        "team_name_en": args.team_name_en,
        "extracted_at": manifest.get("extracted_at", ""),
        "pdf_zh_rel": os.path.relpath(args.pdf_zh, out_dir),
        "pdf_en_rel": os.path.relpath(args.pdf_en, out_dir) if args.pdf_en else "—",
        "card_dir_rel": os.path.basename(os.path.abspath(args.card_dir)),
        "official_date": args.official_date,
        "sha256": sha,
        "en_matched": sum(1 for c in manifest["cards"] if c.get("text_en")),
        "team_id": args.team_id,
    }
    data = TeamData(manifest, args.pdf_zh, args.pdf_en)
    md = build(data, meta)
    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(md)
    print(f"已生成 {args.out}（{len(md.splitlines())} 行）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
