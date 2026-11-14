#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1. Auto-sync official portraits, cards, and UI assets from ../app if not present yet
if [ ! -f "assets/portraits/aod_captain.png" ]; then
  echo "📦 正在从辅助 App 同步高清规则卡图与圆形棋子头像..."
  python3 tools/sync_assets.py
fi

# 2. Locate Godot 4 binary on macOS / Linux
GODOT_BIN=""
if command -v godot >/dev/null 2>&1; then
  GODOT_BIN="$(command -v godot)"
elif [ -x "/opt/homebrew/bin/godot" ]; then
  GODOT_BIN="/opt/homebrew/bin/godot"
elif [ -x "/Applications/Godot.app/Contents/MacOS/Godot" ]; then
  GODOT_BIN="/Applications/Godot.app/Contents/MacOS/Godot"
else
  echo "❌ 未检测到 Godot 4 可执行文件。请运行: brew install --cask godot"
  exit 1
fi

echo "🚀 启动《战锤 40,000：杀戮小队 · 电子战术版》原型..."
exec "$GODOT_BIN" --path "$SCRIPT_DIR" "$@"
