#!/usr/bin/env python3
"""Trace the 白い熊 音声 launcher icon from upstream's launcher artwork.

Upstream ships Android Studio's template icon: the Android robot head (a white vector path in
app/src/main/res/drawable/ic_launcher_foreground.xml) on a green grid. Nothing is drawn freehand —
the head is upstream's own path data, re-drawn in the house black-yellow line-art style:

  - the head's silhouette (dome + antennae) becomes a yellow outline,
  - the two eyes stay as yellow dots,
  - 音 (Noto Serif CJK JP Black, as a glyph outline — no font needed at render time) is our mark,
  - three sound arcs to the right of the head say what the app does.

Writes shiroikuma/icon/onse-icon.svg (108×108 adaptive-icon viewport, black ground, everything
inside the 66-unit safe circle) and, with --png, a preview PNG rendered by rsvg-convert.

Usage: python3 shiroikuma/icon/trace-icon.py [--png OUT.png] [--size 512]
"""
import argparse
import os
import re
import subprocess

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTCollection

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "app/src/main/res/drawable/ic_launcher_foreground.xml")
OUT = os.path.join(ROOT, "shiroikuma/icon/onse-icon.svg")
FONT = "/usr/share/fonts/opentype/noto/NotoSerifCJK-Black.ttc"
MARK = "音"

YELLOW, BLACK = "#FFFF00", "#000000"
STROKE = 2.6          # outline width in viewport units (108-unit canvas)


def upstream_head():
    """The robot head path: the LAST pathData in upstream's foreground (the white shape)."""
    xml = open(SRC, encoding="utf-8").read()
    paths = re.findall(r'android:pathData="([^"]+)"', xml)
    head = paths[-1]
    # Subpath 1 = dome + antennae; subpaths 2, 3 = the eyes.
    parts = re.split(r"(?<=z)(?=M)", head)
    return parts[0], parts[1:]


def glyph_path(ch, box_x, box_y, box_size):
    """SVG path data of `ch`, fitted into a square box (y down)."""
    ttc = TTCollection(FONT)
    font = next(f for f in ttc.fonts if "JP" in f["name"].getDebugName(1))
    cmap = font.getBestCmap()
    gs = font.getGlyphSet()
    name = cmap[ord(ch)]
    upm = font["head"].unitsPerEm
    # Glyph bounds, to centre the ink rather than the em box.
    from fontTools.pens.boundsPen import BoundsPen
    bp = BoundsPen(gs)
    gs[name].draw(bp)
    x0, y0, x1, y1 = bp.bounds
    s = box_size / max(x1 - x0, y1 - y0)
    w, h = (x1 - x0) * s, (y1 - y0) * s
    dx = box_x + (box_size - w) / 2 - x0 * s
    dy = box_y + (box_size - h) / 2 + y1 * s
    pen = SVGPathPen(gs)
    gs[name].draw(TransformPen(pen, (s, 0, 0, -s, dx, dy)))
    return pen.getCommands()


def build_svg():
    dome, eyes = upstream_head()
    # Upstream's head spans x 31–77 (46 wide), y 38–63.9 (26 high). Lay out head + arcs over 音
    # and centre the whole group on the canvas.
    head_scale, gap, mark_size, arc_span = 0.80, 4.5, 30.0, 15.0
    head_w, head_h = 46 * head_scale, 25.928 * head_scale
    group_w = head_w + arc_span
    group_h = head_h + gap + mark_size
    left, top = (108 - group_w) / 2, (108 - group_h) / 2
    head_tx, head_ty = left - 31 * head_scale, top - 38 * head_scale
    head_g = f'transform="translate({head_tx:.3f},{head_ty:.3f}) scale({head_scale})"'
    sw = STROKE / head_scale
    cx = left + head_w / 2
    right = left + head_w
    base = top + head_h
    mid_y = top + head_h * 0.62
    arcs = []
    for r in (3.5, 7.5, 11.5):
        x = right - 1.5
        arcs.append(
            f'<path d="M{x + r * 0.45:.2f},{mid_y - r * 0.9:.2f} A{r},{r} 0 0 1 {x + r * 0.45:.2f},{mid_y + r * 0.9:.2f}" '
            f'fill="none" stroke="{YELLOW}" stroke-width="{STROKE}" stroke-linecap="round"/>')
    mark = glyph_path(MARK, cx - mark_size / 2, base + gap, mark_size)
    eyes_svg = "".join(f'<path d="{e}" fill="{YELLOW}"/>' for e in eyes)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">
  <rect width="108" height="108" fill="{BLACK}"/>
  <g {head_g}>
    <path d="{dome}" fill="none" stroke="{YELLOW}" stroke-width="{sw:.3f}" stroke-linejoin="round"/>
    {eyes_svg}
  </g>
  {"".join(arcs)}
  <path d="{mark}" fill="{YELLOW}"/>
</svg>
'''


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--png")
    ap.add_argument("--size", type=int, default=512)
    ap.add_argument("--round", action="store_true", help="preview PNG clipped to a launcher squircle")
    a = ap.parse_args()
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(build_svg())
    print(OUT)
    if a.png:
        subprocess.run(["rsvg-convert", "-w", str(a.size), "-h", str(a.size), OUT, "-o", a.png], check=True)
        if a.round:
            from PIL import Image, ImageDraw
            im = Image.open(a.png).convert("RGBA")
            m = Image.new("L", im.size, 0)
            ImageDraw.Draw(m).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1],
                                                radius=int(im.size[0] * 0.22), fill=255)
            im.putalpha(m)
            im.save(a.png)
        print(a.png)


if __name__ == "__main__":
    main()
