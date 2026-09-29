#!/usr/bin/env python3
"""make_lens.py — synthesises Tess's idle lens ring (or listening disc) + a dump for lens_check.py's self-test.

    make_lens.py --png out.png --xml out.xml --tones hal|accent|hal_dim [--accent 3E65FF] [--form ring|disc]

Draws on a black 1080 x 2340 canvas at the persona's place (centre 540, 732 — IDLE_CENTRE_Y_EPX 244 x 3; the box
cortana_persona_large_idle [398,590][682,874]) with the app's own maths re-implemented here:
  * tones: LensTones.of — `hal` = Brand.LENS_*; `accent` = rim / iris / glow re-hued to the accent's HSV hue with
    S and V kept; `hal_dim` = R, G, B x 0.5; the core never changes (cortana/ui/Lens.kt:73-90)
  * ring: a stroke of radius (210 - 33) / 2 px, 33 px wide, so paint on r in [72, 105); colour lerp_sRGB(glow, rim,
    r / 105) (Lens.kt:106-126 drawLensRing with IDLE_OUTER_EPX 70, IDLE_STROKE_EPX 11 at 3 px/epx)
  * disc: the listening disc at its largest (LISTEN_DISC_MAX_EPX 41.1 -> radius 61.65 px), stops core 0-.18, glow
    .34, iris .70, rim 1 (Lens.kt:89-104), inside the 25 %-alpha iris halo (LISTEN_HALO_MAX_EPX 94.7 -> radius
    142 px; drawLensHalo Lens.kt:128-131)
rendered at 4x and box-filtered down, so the edges are anti-aliased.
"""
import argparse
import colorsys
import sys

from PIL import Image

import numpy as np

W, H = 1080, 2340
CX, CY = 540.0, 732.0
BOX = (398, 590, 682, 874)
HAL = {"rim": (0x8A, 0x10, 0x08), "iris": (0xD8, 0x18, 0x10), "glow": (0xFF, 0x2D, 0x1C), "core": (0xFF, 0xE9, 0xC8)}


def tones_of(kind, accent_hex):
    out = {"core": np.array(HAL["core"], dtype=np.float64)}
    ah = colorsys.rgb_to_hsv(*[int(accent_hex[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])[0]
    for name in ("rim", "iris", "glow"):
        r, g, b = [c / 255.0 for c in HAL[name]]
        if kind == "accent":
            _, s, v = colorsys.rgb_to_hsv(r, g, b)
            r, g, b = colorsys.hsv_to_rgb(ah, s, v)
        elif kind == "hal_dim":
            r, g, b = r * 0.5, g * 0.5, b * 0.5
        out[name] = np.array([r * 255.0, g * 255.0, b * 255.0])
    return out


def gradient(stops, tones, t):
    """Piecewise-linear sRGB over [(t, name)], vectorised over a t array -> (..., 3)."""
    out = np.zeros(t.shape + (3,), dtype=np.float64)
    tt = np.clip(t, 0.0, 1.0)
    for (t0, n0), (t1, n1) in zip(stops, stops[1:]):
        sel = (tt >= t0) & (tt <= t1)
        f = np.zeros_like(tt) if t1 == t0 else (tt - t0) / (t1 - t0)
        seg = tones[n0][None, :] + (tones[n1] - tones[n0])[None, :] * f[..., None]
        out[sel] = seg[sel]
    return out


def node(rid, b, text=""):
    return '<node index="0" text="%s" resource-id="%s" class="android.view.View" package="app.tileshell" ' \
           'content-desc="" bounds="[%d,%d][%d,%d]" />' % (text, rid, b[0], b[1], b[2], b[3])


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--png", required=True)
    ap.add_argument("--xml", required=True)
    ap.add_argument("--tones", choices=("hal", "accent", "hal_dim"), default="hal")
    ap.add_argument("--accent", default="3E65FF", help="the accent whose hue `accent` re-hues to (Cobalt)")
    ap.add_argument("--form", choices=("ring", "disc"), default="ring")
    a = ap.parse_args(argv)
    tones = tones_of(a.tones, a.accent)

    ss = 4
    size = 320   # the region drawn, in device px, centred on (CX, CY)
    n = size * ss
    ys, xs = np.mgrid[0:n, 0:n]
    px = (xs + 0.5) / ss - size / 2.0     # device-px coords relative to the centre
    py = (ys + 0.5) / ss - size / 2.0
    r = np.hypot(px, py)
    img = np.zeros((n, n, 3), dtype=np.float64)
    if a.form == "ring":
        outer, stroke = 70.0 * 3, 11.0 * 3
        mid = (outer - stroke) / 2.0
        band = (r >= mid - stroke / 2) & (r < mid + stroke / 2)
        col = gradient([(0.0, "glow"), (1.0, "rim")], tones, r / (outer / 2.0))
        img[band] = col[band]
    else:
        disc_r = 41.1 * 3 / 2.0
        halo_r = 94.7 * 3 / 2.0
        halo = (r >= disc_r) & (r < halo_r)
        img[halo] = 0.25 * tones["iris"]      # 25 % iris over black
        inside = r < disc_r
        col = gradient([(0.0, "core"), (0.18, "core"), (0.34, "glow"), (0.70, "iris"), (1.0, "rim")], tones, r / disc_r)
        img[inside] = col[inside]
    small = Image.fromarray(np.round(img).clip(0, 255).astype(np.uint8)).resize((size, size), Image.BOX)
    canvas = Image.new("RGB", (W, H), (0, 0, 0))
    canvas.paste(small, (int(CX - size / 2), int(CY - size / 2)))
    canvas.save(a.png)

    persona = "cortana_persona_large_idle" if a.form == "ring" else "cortana_persona_large_listening"
    nodes = [node("android:id/content", (0, 0, W, H)), node("cortana_session", (0, 0, W, H)),
             node("cortana_menu_button", (0, 84, 144, 228)), node(persona, BOX),
             node("cortana_greeting", (251, 946, 830, 1018), "What's on your mind?"),
             node("w10m_nav_bar", (0, 2196, W, H)), node("w10m_status_bar", (36, 0, 1044, 84))]
    with open(a.xml, "w") as f:
        f.write('<?xml version="1.0" encoding="UTF-8" standalone="yes" ?>\n<hierarchy rotation="0">\n')
        f.write("\n".join(nodes))
        f.write("\n</hierarchy>\n")
    print("wrote %s and %s (%s, tones %s: %s)" % (a.png, a.xml, a.form, a.tones,
                                                 ", ".join("%s=%s" % (k, tuple(int(round(c)) for c in v)) for k, v in tones.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
