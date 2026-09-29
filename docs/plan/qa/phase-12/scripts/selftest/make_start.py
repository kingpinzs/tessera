#!/usr/bin/env python3
"""make_start.py — synthesises a Start "screenshot" + uiautomator-style dump for picture_oracle.py's self-test.

    make_start.py --picture preset_hal.webp --png out.png --xml out.xml [--alpha 0.72] [--accent E81123]
                  [--opaque] [--no-picture] [--shift-y 30] [--layer-scale 1.3]

The picture is mapped FORWARD through the chain the app draws (ContentScale.Crop into the 1080 x 2196 page, then
the graphicsLayer's centred 1.3x scale and translationY) with Pillow's own affine transform (BILINEAR, pixel-centre
convention) — an implementation independent of the oracle's inverse-mapping code. Then: black nav bar, white
status-bar text drawn over the page top, and tiles laid out as phase 11 E1's dump had them: the accent plate
composited at --alpha over the page (or opaque with --opaque), a white icon square at the centre and a white
caption label bottom-left. --shift-y / --layer-scale corrupt the geometry for the negative controls.

Only proves internal consistency (the oracle and this share the same understanding of the chain); the device
validation is the gutter check on a real screencap.
"""
import argparse
import sys

from PIL import Image, ImageDraw, ImageFont

import numpy as np

W, H = 1080, 2340
PAGE_H = 2196
STATUS_PX = 84
TILES = [   # (id, x1, y1, x2, y2, label) — phase 11 E1 .start.xml
    ("tile:slot:PEOPLE", 9, 84, 352, 427, "People · Tap to choose"),
    ("tile:slot:BROWSER", 365, 84, 708, 427, "Browser"),
    ("tile:slot:MAIL", 721, 84, 1064, 427, "Mail · Tap to choose"),
    ("tile:slot:PHOTOS", 9, 439, 352, 782, "Photos"),
    ("tile:app:app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity:0", 365, 439, 708, 782, "Verb A"),
    ("tile:app:app.tileshell.testclient.b/app.tileshell.testclient.VerbActivity:0", 721, 439, 886, 604, ""),
    ("tile:slot:CALENDAR", 9, 795, 708, 1138, "Calendar"),
    ("tile:shell:weather", 9, 1151, 708, 1494, "Weather"),
    ("tile:folder:qa", 721, 617, 1064, 960, "qa"),
    ("tile:slot:MUSIC", 899, 439, 1064, 604, ""),
    ("tile:shell:settings", 721, 973, 886, 1138, ""),
    ("tile:shell:cortana", 721, 1151, 1064, 1494, "Tess"),
    ("tile:dock:slot:PHONE", 9, 1935, 352, 2182, ""),
    ("tile:dock:slot:MESSAGING", 365, 1935, 708, 2182, ""),
    ("tile:dock:slot:CAMERA", 721, 1935, 1064, 2182, ""),
]


def node(rid, b, text=""):
    return '<node index="0" text="%s" resource-id="%s" class="android.view.View" package="app.tileshell" ' \
           'content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" ' \
           'focused="false" scrollable="false" long-clickable="false" password="false" selected="false" ' \
           'bounds="[%d,%d][%d,%d]" />' % (text.replace("&", "&amp;"), rid, b[0], b[1], b[2], b[3])


def render_page(picture, layer_scale, shift_y):
    pic = Image.open(picture).convert("RGB")
    pw, ph = pic.size
    s0 = max(W / pw, PAGE_H / ph)                      # ContentScale.Crop
    dstw, dsth = int(round(pw * s0)), int(round(ph * s0))
    offx, offy = int(round((W - dstw) * 0.5)), int(round((PAGE_H - dsth) * 0.5))   # Alignment.Center
    S = layer_scale
    cx, cy = W / 2.0, PAGE_H / 2.0                     # TransformOrigin.Center of the 1080 x 2196 layer
    ty = shift_y                                       # translationY (-scroll * 0.25 at rest = 0, plus the corruption)
    # screen (X, Y) -> layer: lx = cx + (X - cx) / S ; ly = cy + (Y - ty - cy) / S ; -> picture: (lx - offx) / s0 ...
    a = 1.0 / (S * s0)
    c = (cx - cx / S - offx) / s0
    e = 1.0 / (S * s0)
    f = (cy - cy / S - ty / S - offy) / s0
    return pic.transform((W, PAGE_H), Image.AFFINE, (a, 0.0, c, 0.0, e, f), resample=Image.BILINEAR)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--picture")
    ap.add_argument("--png", required=True)
    ap.add_argument("--xml", required=True)
    ap.add_argument("--alpha", type=float, default=0.72)
    ap.add_argument("--accent", default="E81123")
    ap.add_argument("--opaque", action="store_true", help="tiles drawn as the opaque accent (alpha 1)")
    ap.add_argument("--no-picture", action="store_true", help="a black page (Default preset)")
    ap.add_argument("--shift-y", type=float, default=0.0, help="corrupt: extra translationY in px")
    ap.add_argument("--layer-scale", type=float, default=1.3, help="corrupt: draw at this layer scale")
    a = ap.parse_args(argv)
    accent = np.array([int(a.accent[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)
    alpha = 1.0 if a.opaque else a.alpha

    canvas = Image.new("RGB", (W, H), (0, 0, 0))
    if not a.no_picture:
        if not a.picture:
            ap.error("--picture or --no-picture")
        canvas.paste(render_page(a.picture, a.layer_scale, a.shift_y), (0, 0))
    arr = np.asarray(canvas, dtype=np.float64).copy()

    # Tiles: the accent plate composited over whatever the page shows (sRGB, like Compose's background()).
    for _, x1, y1, x2, y2, _ in TILES:
        region = arr[y1:y2, x1:x2, :]
        arr[y1:y2, x1:x2, :] = np.round(alpha * accent + (1.0 - alpha) * region)
    canvas = Image.fromarray(arr.clip(0, 255).astype(np.uint8))
    draw = ImageDraw.Draw(canvas)
    label_font = ImageFont.load_default(size=36)
    clock_font = ImageFont.load_default(size=40)
    text_nodes = []
    for rid, x1, y1, x2, y2, label in TILES:
        w, h = x2 - x1, y2 - y1
        icon = (min(w, h) * 0.52 if min(w, h) < 200 else (w if w <= h else h) * 0.42) * 0.84
        icx, icy = x1 + w / 2.0, y1 + h / 2.0
        draw.rectangle([icx - icon / 2, icy - icon / 2, icx + icon / 2, icy + icon / 2], fill=(255, 255, 255))
        if label:
            tx, ty = x1 + 24, y2 - 15 - 44
            draw.text((tx, ty), label, fill=(255, 255, 255), font=label_font)
            bb = draw.textbbox((tx, ty), label, font=label_font)
            text_nodes.append(node("", (int(bb[0]), int(bb[1]), int(bb[2]) + 1, int(bb[3]) + 1), label))
    # The drawn status bar over the page top, and the black nav bar.
    draw.text((914, 20), "8:03 PM", fill=(255, 255, 255), font=clock_font)
    draw.rectangle([40, 24, 80, 60], fill=(255, 255, 255))
    draw.rectangle([0, PAGE_H, W, H], fill=(0, 0, 0))
    draw.rectangle([432, 2246, 486, 2300], fill=(255, 255, 255))
    canvas.save(a.png)

    nodes = [node("android:id/content", (0, 0, W, H)), node("start_page", (0, 0, W, PAGE_H))]
    nodes += [node(rid, (x1, y1, x2, y2)) for rid, x1, y1, x2, y2, _ in TILES]
    nodes += text_nodes
    nodes += [node("bottom_tile_row", (0, 0, W, PAGE_H)), node("w10m_nav_bar", (0, PAGE_H, W, H)),
              node("nav_back", (0, PAGE_H, 360, H)), node("nav_windows", (360, PAGE_H, 720, H)),
              node("nav_search", (720, PAGE_H, W, H)), node("w10m_status_bar", (36, 0, 1044, STATUS_PX)),
              node("", (914, 17, 1047, 67), "8:03 PM")]
    with open(a.xml, "w") as f:
        f.write('<?xml version="1.0" encoding="UTF-8" standalone="yes" ?>\n<hierarchy rotation="0">\n')
        f.write("\n".join(nodes))
        f.write("\n</hierarchy>\n")
    print("wrote %s and %s (alpha %.2f%s, layer scale %.2f, shift %.0f px%s)" % (
        a.png, a.xml, alpha, " opaque" if a.opaque else "", a.layer_scale, a.shift_y, ", no picture" if a.no_picture else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
