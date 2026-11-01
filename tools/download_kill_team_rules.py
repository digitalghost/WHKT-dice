#!/usr/bin/env python3
"""自动下载 Warhammer Community 上 Kill Team（杀戮小队）各小队的中文专属规则 PDF。

数据来源
    官方下载页 https://www.warhammer-community.com/en-gb/downloads/kill-team/
    是 Next.js 应用，SSR HTML 里不含下载列表；列表由前端调用
    POST https://www.warhammer-community.com/api/search/downloads/ 实时获取。
    本脚本直接复刻该接口调用，避免解析 DOM。

过滤策略（v2：只保留「小队自己的规则」，任务包一律剔除）
    (a) 只下载中文版：接口参数 language=chinese（简体中文）。
    (b) 只下载官方分类为「小队规则」的文档：
          team-rules     —— 各杀戮小队自己的规则（APL 数据表 / 特工 / 武器 / 战术指令）
          faction-packs  —— 各阵营自己的规则包（Kill Team 目前为空，保留以适配官方调整）
        mission-packs（任务包/战役系统）、key-downloads（核心规则、简化规则、通用装备、
        各类更新日志）等通用内容一律排除，可用 --include-general 作为逃生舱手动纳入。

输出
    docs/最新杀戮小队规则更新/<中文名> <英文名> 杀戮小队规则.pdf
    docs/最新杀戮小队规则更新/manifest.json  （记录来源、版本、校验信息、排除清单）

用法
    python3 tools/download_kill_team_rules.py                # 下载/增量更新
    python3 tools/download_kill_team_rules.py --list         # 只列清单不下载
    python3 tools/download_kill_team_rules.py --force        # 强制重下
    python3 tools/download_kill_team_rules.py --include-general  # 连任务包/通用规则一起下
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib import request

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT_DIR = ROOT / "docs" / "最新杀戮小队规则更新"

SEARCH_URL = "https://www.warhammer-community.com/api/search/downloads/"
ASSET_BASE = "https://assets.warhammer-community.com/"
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)
DEFAULT_GAME_SYSTEM = "kill-team"
DEFAULT_LANGUAGE = "chinese"

# 只有这两个官方分类算「某个小队/阵营自己的规则」，其余（任务包、核心规则、通用装备、
# 更新日志、索引、FAQ、地形等）都是通用内容，一律排除。
TEAM_CATEGORIES = {"team-rules", "faction-packs"}

# 文件名后缀：按官方分类区分
FILENAME_SUFFIX = {
    "team-rules": "杀戮小队规则",
    "faction-packs": "阵营规则",
}
DEFAULT_SUFFIX = "杀戮小队规则"

# 通用内容的典型措辞（仅用于日志提示 / manifest 备注，不参与判定）
GENERAL_HINTS = ("更新日志", "通用", "核心规则", "任务包", "任务手册", "战役", "杀戮区", "地形")


# --------------------------------------------------------------------------- #
# 数据模型
# --------------------------------------------------------------------------- #
@dataclass
class DownloadItem:
    """一条官方下载记录。"""

    slug: str
    title_zh: str
    title_en: str
    categories: list[str]
    source_file: str
    file_size_text: str
    last_updated: str
    created_at: str
    url: str
    language: str

    @property
    def primary_category(self) -> str:
        for cat in TEAM_CATEGORIES:
            if cat in self.categories:
                return cat
        return self.categories[0] if self.categories else ""

    def is_team_rule(self) -> bool:
        """是否属于「某个小队/阵营自己的规则」。"""
        return any(cat in TEAM_CATEGORIES for cat in self.categories)

    def local_name(self) -> str:
        suffix = FILENAME_SUFFIX.get(self.primary_category, DEFAULT_SUFFIX)
        parts = [p for p in (self.title_zh.strip(), self.title_en.strip()) if p]
        if not parts:
            parts = [self.slug]
        elif len(parts) == 2 and parts[0] == parts[1]:
            parts = [parts[0]]
        name = " ".join(parts) + f" {suffix}.pdf"
        return sanitize_filename(name)

    def to_dict(self) -> dict[str, Any]:
        return {
            "slug": self.slug,
            "title_zh": self.title_zh,
            "title_en": self.title_en,
            "categories": self.categories,
            "source_file": self.source_file,
            "file_size": self.file_size_text,
            "last_updated": self.last_updated,
            "created_at": self.created_at,
            "source_url": self.url,
            "language": self.language,
        }


@dataclass
class Manifest:
    """输出目录中的清单文件。"""

    generated_at: str
    game_system: str
    language: str
    api_params: dict[str, Any]
    files: list[dict[str, Any]] = field(default_factory=list)
    skipped: list[dict[str, Any]] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "generated_at": self.generated_at,
            "game_system": self.game_system,
            "language": self.language,
            "api_params": self.api_params,
            "counts": {"downloaded": len(self.files), "skipped": len(self.skipped)},
            "files": self.files,
            "skipped": self.skipped,
        }


# --------------------------------------------------------------------------- #
# 基础工具
# --------------------------------------------------------------------------- #
def sanitize_filename(name: str) -> str:
    """去掉文件名里的非法字符，保留中文。"""
    cleaned = re.sub(r'[\\/:*?"<>|\r\n\t]', " ", name)
    cleaned = re.sub(r"\s+", " ", cleaned).strip(" .")
    return cleaned or "download.pdf"


def now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def parse_size_text(text: str) -> int | None:
    """把官方 file_size 文本（如 "31.73 MB"）解析成字节数。"""
    m = re.search(r"([\d.]+)\s*(KB|MB|GB|B)", text or "", re.IGNORECASE)
    if not m:
        return None
    value = float(m.group(1))
    unit = m.group(2).upper()
    factor = {"B": 1, "KB": 1024, "MB": 1024**2, "GB": 1024**3}[unit]
    return int(value * factor)


def http_json(url: str, payload: dict[str, Any] | None = None, retries: int = 4) -> Any:
    """带重试的 HTTP GET/POST JSON 请求。"""
    last_exc: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            data = json.dumps(payload).encode("utf-8") if payload is not None else None
            req = request.Request(url, data=data, method="POST" if data else "GET")
            req.add_header("User-Agent", USER_AGENT)
            req.add_header("Accept", "application/json")
            if data:
                req.add_header("Content-Type", "application/json")
            with request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as exc:  # noqa: BLE001 - 网络层统一重试
            last_exc = exc
            if attempt < retries:
                time.sleep(1.5 * attempt)
    raise RuntimeError(f"请求失败 {url}: {last_exc}")


def download_to(url: str, dest: Path, retries: int = 3, expected: int | None = None) -> tuple[int, str]:
    """下载单个文件到 dest（原子替换），返回 (字节数, sha256)。

    会校验 Content-Length 与官方 file_size：CDN 偶发提前断流时文件会被截断
    （表现为 PDF xref 表损坏），这里直接判定失败并重试。
    """
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    last_exc: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            req = request.Request(url, method="GET")
            req.add_header("User-Agent", USER_AGENT)
            digest = hashlib.sha256()
            total = 0
            declared: int | None = None
            with request.urlopen(req, timeout=180) as resp:
                raw_len = resp.headers.get("Content-Length")
                declared = int(raw_len) if raw_len and raw_len.isdigit() else None
                with open(tmp, "wb") as fh:
                    while True:
                        chunk = resp.read(1 << 18)
                        if not chunk:
                            break
                        fh.write(chunk)
                        digest.update(chunk)
                        total += len(chunk)
            if declared is not None and total != declared:
                raise RuntimeError(f"文件被截断（{total} / {declared} bytes）")
            if expected and total < expected * 0.98:
                raise RuntimeError(f"文件小于官方标注（{total} / 约 {expected} bytes）")
            with open(tmp, "rb") as fh:
                magic = fh.read(4)
            if total < 1024 or magic != b"%PDF":
                raise RuntimeError(f"下载内容不是有效 PDF（{total} bytes）")
            os.replace(tmp, dest)
            return total, digest.hexdigest()
        except Exception as exc:  # noqa: BLE001
            last_exc = exc
            tmp.unlink(missing_ok=True)
            if attempt < retries:
                time.sleep(2 * attempt)
    raise RuntimeError(f"下载失败 {url}: {last_exc}")


def remote_size(url: str) -> int | None:
    """取回远端文件声明的字节数（HEAD）。失败返回 None。"""
    try:
        req = request.Request(url, method="HEAD")
        req.add_header("User-Agent", USER_AGENT)
        with request.urlopen(req, timeout=60) as resp:
            raw = resp.headers.get("Content-Length")
            return int(raw) if raw and raw.isdigit() else None
    except Exception:  # noqa: BLE001
        return None


# --------------------------------------------------------------------------- #
# API 封装
# --------------------------------------------------------------------------- #
def fetch_downloads(game_system: str, language: str) -> tuple[list[DownloadItem], dict[str, Any]]:
    """调用官方搜索接口，取回某游戏系统 + 某语言的全部下载条目。"""
    payload = {
        "index": "downloads_v2",
        "searchTerm": "",
        "gameSystem": game_system,
        "language": language,
    }
    raw = http_json(SEARCH_URL, payload)
    hits = (raw or {}).get("hits") or []
    items: list[DownloadItem] = []
    for hit in hits:
        doc = hit.get("id") or {}
        src = doc.get("file") or ""
        if not src:
            continue
        items.append(
            DownloadItem(
                slug=doc.get("slug") or hit.get("objectID", ""),
                title_zh=(hit.get("title") or "").strip(),
                title_en="",
                categories=[c for c in (hit.get("download_categories") or []) if c],
                source_file=src,
                file_size_text=doc.get("file_size") or "",
                last_updated=doc.get("last_updated") or "",
                created_at=doc.get("created_at") or "",
                url=ASSET_BASE + src,
                language=hit.get("download_languages") or language,
            )
        )
    params = (raw or {}).get("params") or {}
    return items, params


def enrich_english_titles(items: list[DownloadItem], game_system: str) -> None:
    """用英文条目补全英文标题（用于文件命名），失败不影响主流程。"""
    try:
        english, _ = fetch_downloads(game_system, "english")
    except Exception as exc:  # noqa: BLE001
        print(f"  ! 英文标题补全失败（不影响下载）：{exc}")
        return
    mapping = {item.slug: item.title_zh for item in english}
    for item in items:
        item.title_en = mapping.get(item.slug, "")


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #
def load_manifest(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}


def save_manifest(path: Path, manifest: Manifest) -> None:
    path.write_text(
        json.dumps(manifest.to_dict(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="自动下载 Warhammer Community 上各杀戮小队的中文专属规则 PDF（不含任务包）",
    )
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR, help="输出目录")
    parser.add_argument("--game-system", default=DEFAULT_GAME_SYSTEM, help="游戏系统 slug")
    parser.add_argument("--language", default=DEFAULT_LANGUAGE, help="语言（chinese/english/...）")
    parser.add_argument(
        "--include-general",
        action="store_true",
        help="连任务包、核心规则等通用内容一起下载（默认只下各小队自己的规则）",
    )
    parser.add_argument("--force", action="store_true", help="忽略本地已有文件，强制重新下载")
    parser.add_argument("--list", action="store_true", help="只输出清单，不下载")
    parser.add_argument("--workers", type=int, default=4, help="并发下载线程数")
    parser.add_argument("--no-english-titles", action="store_true", help="不补英文标题")
    args = parser.parse_args(argv)

    out_dir: Path = args.out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = out_dir / "manifest.json"
    previous = load_manifest(manifest_path)
    previous_by_slug = {row["slug"]: row for row in previous.get("files", [])}

    print(f"→ 查询官方接口：{SEARCH_URL}")
    print(f"  gameSystem={args.game_system} language={args.language}")
    items, api_params = fetch_downloads(args.game_system, args.language)
    if not items:
        print("! 未获取到任何条目，终止。", file=sys.stderr)
        return 2
    print(f"  共 {len(items)} 条官方记录")

    if not args.no_english_titles and args.language != "english":
        print("→ 补全英文标题（用于文件命名）")
        enrich_english_titles(items, args.game_system)

    # 过滤：只保留官方分类为「小队/阵营自己的规则」的文档
    selected: list[DownloadItem] = []
    general: list[DownloadItem] = []
    for item in items:
        (selected if item.is_team_rule() else general).append(item)
    print(
        f"→ 过滤：小队专属规则 {len(selected)} 条；"
        f"通用内容（任务包/核心规则等）{len(general)} 条已排除"
    )

    if args.list:
        print("\n【小队专属规则（会下载）】")
        for item in sorted(selected, key=lambda i: i.title_zh):
            print(
                f"  ✔ {item.title_zh:28s} | {item.last_updated:12s} | "
                f"{item.primary_category:14s} | {item.local_name()}"
            )
        print("\n【通用内容（不下载）】")
        for item in sorted(general, key=lambda i: i.title_zh):
            hint = " ".join(w for w in GENERAL_HINTS if w in item.title_zh)
            print(f"  ✘ {item.title_zh:28s} | {item.primary_category:14s} {hint}")
        return 0

    if args.include_general:
        selected += general
        print("  已启用 --include-general，通用内容一并下载")

    manifest = Manifest(
        generated_at=now_iso(),
        game_system=args.game_system,
        language=args.language,
        api_params=api_params,
    )

    print(f"→ 开始下载到 {out_dir}")
    results: list[dict[str, Any]] = []

    def run(item: DownloadItem) -> dict[str, Any]:
        local = out_dir / item.local_name()
        old = previous_by_slug.get(item.slug)
        old_name = (old or {}).get("local_name")
        old_source = (old or {}).get("source_file")
        local_ok = local.exists() and local.stat().st_size > 1024
        unchanged = not args.force and old_source == item.source_file and local_ok
        if unchanged:
            # 自检：与远端声明长度不符说明本地文件曾被截断，需要重下
            declared = remote_size(item.url)
            if declared and declared != local.stat().st_size:
                print(f"  ! 本地文件不完整（{local.stat().st_size} / {declared}），重新下载")
                unchanged = False
        if unchanged:
            print(f"  = 已是最新，跳过：{local.name}")
            row = dict(old or {})
            row.update(
                {
                    "local_name": local.name,
                    "source_file": item.source_file,
                    "last_updated": item.last_updated,
                    "source_url": item.url,
                    "classification": f"category:{item.primary_category}",
                    "status": "unchanged",
                }
            )
            return row
        try:
            size, sha = download_to(
                item.url, local, expected=parse_size_text(item.file_size_text)
            )
        except Exception as exc:  # noqa: BLE001
            print(f"  ! 下载失败：{item.title_zh} — {exc}")
            return {
                **item.to_dict(),
                "local_name": local.name,
                "status": "failed",
                "error": str(exc),
                "classification": f"category:{item.primary_category}",
            }
        if old_name and old_name != local.name:
            stale = out_dir / old_name
            if stale.exists() and stale != local:
                stale.unlink(missing_ok=True)
        print(f"  ✔ {local.name}  ({size / 1024 / 1024:.1f} MB)")
        return {
            **item.to_dict(),
            "local_name": local.name,
            "bytes": size,
            "sha256": sha,
            "classification": f"category:{item.primary_category}",
            "status": "downloaded",
            "downloaded_at": now_iso(),
        }

    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as pool:
        futures = [pool.submit(run, item) for item in selected]
        for fut in futures:
            results.append(fut.result())

    results.sort(key=lambda r: (r.get("last_updated", ""), r.get("title_zh", "")), reverse=True)
    manifest.files = results

    selected_slugs = {item.slug for item in selected}
    manifest.skipped = [
        {
            **item.to_dict(),
            "classification": f"category:{item.primary_category}",
            "hint": [w for w in GENERAL_HINTS if w in item.title_zh],
        }
        for item in general
        if item.slug not in selected_slugs
    ]

    # 清理：上一轮下载过、但本轮已不属于「小队专属规则」的本地文件（例如任务包）
    keep = {row.get("local_name") for row in results}
    removed = []
    for row in previous.get("files", []):
        name = row.get("local_name")
        if not name or name in keep:
            continue
        stale = out_dir / name
        if stale.exists():
            stale.unlink()
            removed.append(name)
    for stale in out_dir.glob("*.pdf.part"):
        stale.unlink(missing_ok=True)
    if removed:
        print(f"  🧹 已移除不再符合筛选条件的文件 {len(removed)} 份：")
        for name in removed:
            print(f"     - {name}")

    save_manifest(manifest_path, manifest)

    ok = sum(1 for r in results if r.get("status") in {"downloaded", "unchanged"})
    failed = [r for r in results if r.get("status") == "failed"]
    print(f"\n完成：成功 {ok} 条，失败 {len(failed)} 条；清单写入 {manifest_path}")
    if failed:
        for r in failed:
            print(f"  ! {r.get('title_zh')}: {r.get('error')}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
