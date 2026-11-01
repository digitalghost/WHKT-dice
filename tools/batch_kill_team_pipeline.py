#!/usr/bin/env python3
"""48 支官方杀戮小队的批量整理流水线。

按阶段执行，每个阶段都可单独重跑：

  cards    从官方中文 PDF 提取独立卡图 + manifest.json + README.md
  en       并入官方英文版同位置卡片的文本，得到中英对照
  icons    扫描数据卡武器表的类型图标，产出全局图标库
  apply    依据图标库把「远程 / 近战 / 装备」写回各队 manifest
  docs     生成《<中文名>-完整规则资料.md》
  verify   逐队校验

用法：
  python3 tools/batch_kill_team_pipeline.py cards  [--team 虫刃] [--all]
  python3 tools/batch_kill_team_pipeline.py en
  python3 tools/batch_kill_team_pipeline.py icons
  python3 tools/batch_kill_team_pipeline.py apply
  python3 tools/batch_kill_team_pipeline.py docs
  python3 tools/batch_kill_team_pipeline.py verify
  python3 tools/batch_kill_team_pipeline.py all
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import shutil
import subprocess
import sys
import traceback

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))

from team_registry import load_teams  # noqa: E402

TOOLS = os.path.join(ROOT, "tools")
RENDER_DPI = 200


def run(cmd, **kw):
    return subprocess.run(cmd, check=True, capture_output=True, text=True, **kw)


def preserve_legacy(card_dir: str) -> None:
    """保留旧版（KTDash 口径）manifest，避免历史数据被覆盖丢失。"""
    old = os.path.join(card_dir, "manifest.json")
    if not os.path.exists(old):
        return
    try:
        with open(old, encoding="utf-8") as fh:
            data = json.load(fh)
    except (OSError, ValueError):
        return
    if not (data.get("rule_records") or data.get("operatives")):
        return
    legacy = os.path.join(card_dir, "manifest.legacy-ktdash.json")
    if os.path.exists(legacy):
        return
    shutil.copyfile(old, legacy)


def stage_cards(teams, force=False) -> dict:
    summary = {}
    for t in teams:
        target = os.path.join(t["card_dir"], "manifest.json")
        if os.path.exists(target) and not force:
            try:
                with open(target, encoding="utf-8") as fh:
                    old = json.load(fh)
                counts = old.get("counts")
                done = isinstance(counts, dict) and "total" in counts
            except (OSError, ValueError):
                done = False
            if done:
                summary[t["name_zh"]] = "跳过（已存在）"
                continue
        try:
            preserve_legacy(t["card_dir"])
            os.makedirs(t["card_dir"], exist_ok=True)
            # 清掉旧版命名风格的卡图
            for png in glob.glob(os.path.join(t["card_dir"], "*.png")):
                os.unlink(png)
            out = run([
                sys.executable, os.path.join(TOOLS, "extract_kill_team_cards.py"),
                t["zh_pdf"],
                "--out-dir", t["card_dir"],
                "--team-id", t["team_id"],
                "--team-name-zh", t["name_zh"],
                "--team-name-en", t["name_en"],
                "--render-dpi", str(RENDER_DPI),
            ]).stdout.strip().splitlines()
            summary[t["name_zh"]] = out[0] if out else "完成"
        except Exception as exc:
            summary[t["name_zh"]] = f"失败：{exc}"
            traceback.print_exc()
    return summary


def stage_retitle(teams) -> dict:
    """复用已有卡图，只重做卡片分类、命名与索引（用于修正标题口径）。"""
    summary = {}
    for t in teams:
        manifest = os.path.join(t["card_dir"], "manifest.json")
        if not os.path.exists(manifest):
            summary[t["name_zh"]] = "跳过（无 manifest）"
            continue
        try:
            out = run([
                sys.executable, os.path.join(TOOLS, "extract_kill_team_cards.py"),
                t["zh_pdf"],
                "--out-dir", t["card_dir"],
                "--team-id", t["team_id"],
                "--team-name-zh", t["name_zh"],
                "--team-name-en", t["name_en"],
                "--render-dpi", str(RENDER_DPI),
                "--reuse-images",
            ]).stdout.strip().splitlines()
            with open(os.path.join(t["card_dir"], "manifest.json"), encoding="utf-8") as fh:
                data = json.load(fh)
            pngs = [f for f in os.listdir(t["card_dir"]) if f.lower().endswith(".png")]
            need = data.get("counts", {}).get("total", 0)
            if len(pngs) != need:
                raise RuntimeError(f"卡图数量不符：磁盘 {len(pngs)} != manifest {need}")
            summary[t["name_zh"]] = out[0] if out else "完成"
        except Exception as exc:
            summary[t["name_zh"]] = f"失败：{exc}"
            traceback.print_exc()
    return summary


def stage_ids(teams) -> dict:
    """把各队 manifest 的记录 ID 规范化为 <team>-<类别前缀>-<序号>，保证唯一。"""
    summary = {}
    for t in teams:
        manifest = os.path.join(t["card_dir"], "manifest.json")
        if not os.path.exists(manifest):
            summary[t["name_zh"]] = "跳过（无 manifest）"
            continue
        with open(manifest, encoding="utf-8") as fh:
            data = json.load(fh)
        prefix = t["team_id"].lower()
        changed = 0
        for card in data["cards"]:
            name = card.get("image", "")
            parts = name.split("-")
            if len(parts) >= 2 and parts[0].isdigit() and parts[1].isdigit():
                new_id = f"{prefix}-{parts[0]}-{parts[1]}"
            else:
                new_id = f"{prefix}-{card['sequence']:02d}"
            if card["id"] != new_id:
                card["id"] = new_id
                changed += 1
        with open(manifest, "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        summary[t["name_zh"]] = f"改写 {changed} 条记录 ID"
    return summary


def stage_en(teams) -> dict:
    summary = {}
    for t in teams:
        manifest = os.path.join(t["card_dir"], "manifest.json")
        if not os.path.exists(manifest):
            summary[t["name_zh"]] = "跳过（无 manifest）"
            continue
        try:
            out = run([
                sys.executable, os.path.join(TOOLS, "merge_en_reference.py"),
                manifest, t["en_pdf"],
            ]).stdout.strip().splitlines()
            summary[t["name_zh"]] = out[0] if out else "完成"
        except Exception as exc:
            summary[t["name_zh"]] = f"失败：{exc}"
            traceback.print_exc()
    return summary


def stage_icons(teams) -> dict:
    summary = {}
    scans = []
    for t in teams:
        manifest = os.path.join(t["card_dir"], "manifest.json")
        if not os.path.exists(manifest):
            summary[t["name_zh"]] = "跳过（无 manifest）"
            continue
        os.makedirs(t["work_dir"], exist_ok=True)
        scan_path = os.path.join(t["work_dir"], "icons_scan.json")
        try:
            out = run([
                sys.executable, os.path.join(TOOLS, "extract_weapon_icons.py"), "scan",
                "--pdf", t["zh_pdf"], "--manifest", manifest, "--out", scan_path,
                "--icon-dir", os.path.join(t["work_dir"], "icons"),
            ]).stdout.strip()
            summary[t["name_zh"]] = out
            scans.append(scan_path)
        except Exception as exc:
            summary[t["name_zh"]] = f"失败：{exc}"
            traceback.print_exc()
    if scans:
        lib = os.path.join(TOOLS, "weapon_icon_library.json")
        # 图标库是全局的：必须合并全部小队的扫描结果，
        # 只用本次处理的队伍重建会把其他小队的形状条目清掉。
        all_scans = sorted(glob.glob(os.path.join(ROOT, "tmp", "pdfs", "*", "icons_scan.json")))
        merged = sorted(set(all_scans) | set(scans))
        out = run([
            sys.executable, os.path.join(TOOLS, "extract_weapon_icons.py"), "build",
            "--scan", *merged, "--out", lib,
        ]).stdout.strip()
        print(out)
    return summary


def stage_apply(teams) -> dict:
    summary = {}
    lib_path = os.path.join(TOOLS, "weapon_icon_library.json")
    if not os.path.exists(lib_path):
        return {"全部": "图标库不存在，先跑 icons"}
    for t in teams:
        manifest = os.path.join(t["card_dir"], "manifest.json")
        scan_path = os.path.join(t["work_dir"], "icons_scan.json")
        if not (os.path.exists(manifest) and os.path.exists(scan_path)):
            summary[t["name_zh"]] = "跳过"
            continue
        try:
            out = run([
                sys.executable, os.path.join(TOOLS, "extract_weapon_icons.py"), "apply",
                "--manifest", manifest, "--scan", scan_path, "--lib", lib_path,
            ]).stdout.strip()
            summary[t["name_zh"]] = out
        except Exception as exc:
            summary[t["name_zh"]] = f"失败：{exc}"
            traceback.print_exc()
    return summary


def stage_docs(teams) -> dict:
    summary = {}
    for t in teams:
        manifest = os.path.join(t["card_dir"], "manifest.json")
        if not os.path.exists(manifest):
            summary[t["name_zh"]] = "跳过（无 manifest）"
            continue
        try:
            out = run([
                sys.executable, os.path.join(TOOLS, "build_team_doc.py"),
                "--card-dir", t["card_dir"],
                "--pdf-zh", t["zh_pdf"],
                "--pdf-en", t["en_pdf"],
                "--out", t["doc_path"],
                "--team-name-zh", t["name_zh"],
                "--team-name-en", t["name_en"],
                "--official-date", t["official_date"],
                "--team-id", t["team_id"],
            ]).stdout.strip()
            summary[t["name_zh"]] = out
        except Exception as exc:
            summary[t["name_zh"]] = f"失败：{exc}"
            traceback.print_exc()
    return summary


def stage_verify(teams) -> dict:
    summary = {}
    for t in teams:
        manifest = os.path.join(t["card_dir"], "manifest.json")
        if not os.path.exists(manifest):
            summary[t["name_zh"]] = "跳过（无 manifest）"
            continue
        try:
            out = run([
                sys.executable, os.path.join(TOOLS, "verify_team_extract.py"),
                t["card_dir"], t["doc_path"],
            ])
            summary[t["name_zh"]] = out.stdout.strip().splitlines()[-1] if out.stdout.strip() else "无输出"
        except subprocess.CalledProcessError as exc:
            summary[t["name_zh"]] = f"未通过：{(exc.stdout or '').strip().splitlines()[-1:]}"
    return summary


STAGES = {
    "cards": stage_cards,
    "retitle": stage_retitle,
    "ids": stage_ids,
    "en": stage_en,
    "icons": stage_icons,
    "apply": stage_apply,
    "docs": stage_docs,
    "verify": stage_verify,
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("stage", choices=list(STAGES) + ["all"])
    ap.add_argument("--team", help="只处理指定小队（中文名）")
    ap.add_argument("--force", action="store_true", help="覆盖已存在的提取结果")
    ap.add_argument("--shard", help="分片执行，格式 i/n（第 i 片，共 n 片）")
    args = ap.parse_args()

    teams = load_teams(ROOT)
    if args.shard:
        i, n = (int(v) for v in args.shard.split("/"))
        teams = [t for idx, t in enumerate(teams) if idx % n == i]
        print(f"分片 {i}/{n}：{len(teams)} 支小队")
    if args.team:
        teams = [t for t in teams if t["name_zh"] == args.team]
        if not teams:
            raise SystemExit(f"未找到小队：{args.team}")

    order = ["cards", "retitle", "ids", "en", "icons", "apply", "docs", "verify"]
    if args.stage != "all":
        order = [args.stage]
    for stage in order:
        print(f"\n=== 阶段 {stage} ===", flush=True)
        fn = STAGES[stage]
        summary = fn(teams, force=args.force) if stage == "cards" else fn(teams)
        ok = 0
        for name, msg in summary.items():
            bad = isinstance(msg, str) and any(
                w in msg for w in ("失败", "未通过", "Traceback"))
            if bad:
                print(f"  ! {name}: {msg}")
            else:
                ok += 1
        print(f"  -> {ok}/{len(summary)} 正常")
    return 0


if __name__ == "__main__":
    sys.exit(main())
