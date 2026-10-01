#!/usr/bin/env python3
"""Extract one kill-team watermark vector from a source PDF into an app icon.

The source group must be chosen by visual inspection.  In the current eight
team PDFs, page 5 / source-14 is the large pale team watermark printed behind
the rules text.
"""

import argparse
import copy
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET


SVG_NS = "http://www.w3.org/2000/svg"
XLINK_NS = "http://www.w3.org/1999/xlink"


def require_command(name: str) -> None:
    if shutil.which(name) is None:
        raise SystemExit(f"Missing required command: {name}")


def command(*args: str) -> None:
    subprocess.run(args, check=True)


def build_standalone_svg(page_svg: Path, source_id: str, output: Path) -> None:
    ET.register_namespace("", SVG_NS)
    ET.register_namespace("xlink", XLINK_NS)
    source_root = ET.parse(page_svg).getroot()
    if not any(node.attrib.get("id") == source_id for node in source_root.iter()):
        raise SystemExit(f"SVG source group not found: {source_id}")

    svg = ET.Element(
        f"{{{SVG_NS}}}svg",
        {"width": "200pt", "height": "280pt", "viewBox": "0 0 200 280"},
    )
    for child in source_root:
        if child.tag == f"{{{SVG_NS}}}defs":
            svg.append(copy.deepcopy(child))
    ET.SubElement(svg, f"{{{SVG_NS}}}use", {f"{{{XLINK_NS}}}href": f"#{source_id}"})
    ET.ElementTree(svg).write(output, encoding="utf-8", xml_declaration=True)


def extract(args: argparse.Namespace, work_dir: Path) -> None:
    work_dir.mkdir(parents=True, exist_ok=True)
    page_svg = work_dir / "page.svg"
    mark_svg = work_dir / "mark.svg"
    raw_png = work_dir / "raw.png"

    command(
        "pdftocairo", "-svg", "-f", str(args.page), "-l", str(args.page),
        str(args.pdf), str(page_svg),
    )
    build_standalone_svg(page_svg, args.source_id, mark_svg)
    command("magick", "-density", "300", str(mark_svg), str(raw_png))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    command(
        "magick", str(raw_png),
        "-fuzz", "6%", "-transparent", "white",
        "-fill", args.color, "-colorize", "100%",
        "-trim", "+repage", "-resize", "416x416>",
        "-gravity", "center", "-background", "none", "-extent", "512x512",
        str(args.output),
    )
    dimensions = subprocess.run(
        ["identify", "-format", "%wx%h %[channels]", str(args.output)],
        check=True, capture_output=True, text=True,
    ).stdout
    if not dimensions.startswith("512x512 srgba"):
        raise SystemExit(f"Unexpected output format: {dimensions}")
    print(f"{args.output}: {dimensions}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("pdf", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--page", type=int, default=5)
    parser.add_argument("--source-id", default="source-14")
    parser.add_argument("--color", default="#ee7540")
    parser.add_argument(
        "--work-dir", type=Path,
        help="Keep the rendered page SVG, standalone mark SVG and raw PNG here",
    )
    args = parser.parse_args()
    if not args.pdf.is_file():
        raise SystemExit(f"PDF not found: {args.pdf}")
    for tool in ("pdftocairo", "magick", "identify"):
        require_command(tool)

    if args.work_dir:
        extract(args, args.work_dir)
    else:
        with tempfile.TemporaryDirectory(prefix="team-mark-") as folder:
            extract(args, Path(folder))


if __name__ == "__main__":
    main()
