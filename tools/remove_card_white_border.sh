#!/usr/bin/env bash
set -euo pipefail

if ! command -v magick >/dev/null 2>&1; then
  echo "ImageMagick 7 (magick) is required." >&2
  exit 1
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

process_card() {
  local source="$1"
  local stem width height right bottom short radius
  stem="$(printf '%s' "$source" | shasum | cut -d' ' -f1)"

  # The extraction workflow used a 3 px safety margin on each edge.
  magick "$source" -shave 3x3 "$work_dir/${stem}-shaved.png"
  read -r width height < <(magick identify -format '%w %h\n' "$work_dir/${stem}-shaved.png")
  right=$((width - 1))
  bottom=$((height - 1))
  short="$width"
  if (( height < width )); then short="$height"; fi
  radius=$((short * 7 / 100))
  if (( radius < 24 )); then radius=24; fi
  if (( radius > 48 )); then radius=48; fi

  # Copy a rounded-rectangle mask into alpha. This removes only the outer
  # paper/crop area and never key-colours white rules text inside the card.
  magick "$work_dir/${stem}-shaved.png" -alpha on \
    \( -size "${width}x${height}" xc:none -fill white \
       -draw "roundrectangle 0,0 ${right},${bottom} ${radius},${radius}" \) \
    -compose CopyOpacity -composite "$work_dir/${stem}-final.png"
  mv "$work_dir/${stem}-final.png" "$source"
}

if (( $# > 0 )); then
  for card in "$@"; do process_card "$card"; done
else
  while IFS= read -r -d '' card; do process_card "$card"; done < <(
    find docs -mindepth 2 -maxdepth 2 -type f -name '*.png' -path '*-卡片提取/*' -print0
    find app/src/main/assets/team_rules app/src/main/assets/equipment -type f -name '*.png' -print0
  )
fi
