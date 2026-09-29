#!/usr/bin/env python3
"""lens_check.py — phase 12 build task 4 (iii), r3 V8 / D3: a still-image checker of Tess's lens colours.

Separate from phase 03's persona.py (which hard-codes HAL red, filters r > g > b and reads a frame directory —
its interface is untouched). This checker takes the expected lens HUE and BRIGHTNESS as two independent literals
from the phase 12 preset table, so Midnight's half-brightness red (`hal_dim`) is told apart from HAL's full red
(`hal`), and the accent-hued lens (`accent`, e.g. Cobalt) from both.

    lens_check.py --screenshot tess.png --dump tess.xml --hue hal --brightness 1.0            (Default, HAL: `hal`)
    lens_check.py --screenshot tess.png --dump tess.xml --hue 227.9 --brightness 1.0          (original: Cobalt `accent`)
    lens_check.py --screenshot tess.png --dump tess.xml --hue hal --brightness 0.5            (Midnight: `hal_dim`)
        [--form ring|disc] [--node cortana_persona_large_idle] [--expect-tones 8A1008,D81810,FF2D1C]
        [--hue-tol 8] [--v-tol 0.05] [--min-samples 40] [--out r.json]

Exit 0 PASS, 1 FAIL, 2 UNUSABLE. Last stdout line: `RESULT PASS|FAIL|UNUSABLE <summary>`. Nothing imports the app.

The tone model (cortana/ui/Lens.kt LensTones.of, brand/Brand.kt:56-59):
    HAL     rim #8A1008 (h 3.7, s .942, v .541)  iris #D81810 (h 2.3, s .926, v .847)  glow #FF2D1C (h 4.5, s .890, v 1.0)
            core #FFE9C8 — the specular, NEVER re-hued or dimmed (Lens.kt:79-83)
    accent  rim/iris/glow re-hued to the accent's HSV hue, each keeping its own S and V (Lens.kt:80-83, rehue)
    hal_dim rim/iris/glow with R, G, B each x 0.5 = same hue, same S, V x 0.5 (Lens.kt:84-86, half)
So the predicted tone i = fromHsv(H, S_i, V_i x brightness) with H = --hue (a number re-hues all three like
`accent`; `hal` keeps each tone's own built hue) and brightness = --brightness (1.0 or 0.5); the core is Brand's.

The idle form (--form ring, the default) — what the persona actually draws at reveal 0 (Persona.kt:233-243
drawIdleRing -> Lens.kt:106-126 drawLensRing):
    outer diameter IDLE_OUTER_EPX 70 -> 210 px, stroke IDLE_STROKE_EPX 11 -> 33 px (1 epx = 3 px; Scale.kt:21)
    a stroked circle of radius (210 - 33) / 2 = 88.5 px, so the paint covers r in [72, 105] px, filled with a
    radialGradient(0 -> glow, 1 -> rim) of radius outer / 2 = 105 px about the same centre: the colour at radius r
    is lerp_sRGB(glow, rim, t = r / 105), t running 0.686 .. 1.0 across the stroke.
  REGION DESIGN (honest to that geometry). The stroke shows ONLY the glow -> rim line from t = 0.686 to 1: there
  is no iris stop in the ring's gradient at all (the iris tone is painted by drawLensDisc's 0.70 stop and by the
  halo — listening / speaking forms, Lens.kt:89-104, 128-131). Three radial bands cover the stroke, 2 px in from
  each anti-aliased edge (r in [74, 103]):
    glow  (inner band, the glow side)  r 74 .. 83   t 0.705 .. 0.790   the brightest tones the ring shows
    mid                                r 83 .. 94   t 0.790 .. 0.895
    rim   (outer band, the rim side)   r 94 .. 103  t 0.895 .. 0.981   within a few levels of the rim tone itself
    iris  NOT DRAWN by the idle ring — reported as such, no samples, not a failure (unless --require iris).
  Each band predicts every pixel's colour from its own radius (lerp at that pixel's t), so a band's verdict is the
  median of per-pixel hue and V errors, not a comparison with one nominal tone. A required band with fewer than
  --min-samples pixels FAILs (missing region). Verified against a real idle capture (phase 15 L13_10 idle_ring.png):
  the +x ray reads (174,24,14) at r 72.5 and (138,16,8) at r 104.5 — lerp(glow, rim, 0.690) = (174.2, 25.0, 14.2)
  and lerp(glow, rim, 0.995) = (138.6, 16.1, 8.1).

--form disc (listening / speaking, best effort; the disc breathes so its radius is MEASURED from the image, the
sharpest V drop along 16 rays): stops core 0 .. 0.18, glow 0.34, iris 0.70, rim 1.0 (Lens.kt:28-32 LensValues,
:89-104 drawLensDisc); bands core t .02-.15, glow .30-.40, iris .64-.76, rim .90-.97 (2 px inside the edge).

Checks per region: hue — circular distance between each pixel's hue and its predicted colour's hue, median <=
--hue-tol (8 deg), only where the predicted S >= 0.3 (the core is near-white, its hue is not asserted);
brightness — median (V_sampled - V_predicted) within +---v-tol (0.05): at brightness 1.0 the rim band predicts
V ~ 0.57, at 0.5 it predicts ~ 0.29, so the two factors cannot both pass one image. S is reported, not asserted.
Geometry gate (ring): the lit annulus's inner / outer radii must be 72 / 105 +- 2.5 px (median over 16 rays),
else UNUSABLE — the pop-in (Persona.kt:235-241: the stroke thins to 11 epx over 650 ms) or a different form.
"""
import argparse
import colorsys
import json
import math
import re
import sys

from PIL import Image

PX_PER_EPX = 1080 / 360.0                     # ui/tokens/Scale.kt:21
IDLE_OUTER_EPX, IDLE_STROKE_EPX = 70.0, 11.0  # cortana/ui/Persona.kt:50,52
BOX_EPX = 94.7                                # LISTEN_HALO_MAX_EPX, the persona box (Persona.kt:71,167)
RING_OUTER_R = IDLE_OUTER_EPX * PX_PER_EPX / 2.0                          # 105
RING_INNER_R = (IDLE_OUTER_EPX - 2 * IDLE_STROKE_EPX) * PX_PER_EPX / 2.0  # 72 (= IDLE_INNER_EPX 48 / 2 * 3)
CORE_RATIO, GLOW_RATIO, IRIS_RATIO = 0.18, 0.34, 0.70                     # Lens.kt:28-32
AA_PX = 2.0
LIT_V = 0.06

HAL = {"rim": (0x8A, 0x10, 0x08), "iris": (0xD8, 0x18, 0x10), "glow": (0xFF, 0x2D, 0x1C), "core": (0xFF, 0xE9, 0xC8)}

RING_BANDS = [   # (name, t_lo, t_hi) over t = r / RING_OUTER_R, AA-trimmed 2 px from each edge
    ("glow", 74.0 / RING_OUTER_R, 83.0 / RING_OUTER_R),
    ("mid", 83.0 / RING_OUTER_R, 94.0 / RING_OUTER_R),
    ("rim", 94.0 / RING_OUTER_R, 103.0 / RING_OUTER_R),
]
RING_STOPS = [(0.0, "glow"), (1.0, "rim")]
DISC_BANDS = [("core", 0.02, 0.15), ("glow", 0.30, 0.40), ("iris", 0.64, 0.76), ("rim", 0.90, 0.97)]
DISC_STOPS = [(0.0, "core"), (CORE_RATIO, "core"), (GLOW_RATIO, "glow"), (IRIS_RATIO, "iris"), (1.0, "rim")]
NOT_DRAWN = {"ring": ["iris"], "disc": []}


def log(msg=""):
    print(msg)


# ---------------------------------------------------------------- colour maths (mirrors Lens.kt hsv / fromHsv)
def hsv(rgb):
    r, g, b = [c / 255.0 for c in rgb]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    return h * 360.0, s, v


def from_hsv(h, s, v):
    r, g, b = colorsys.hsv_to_rgb((h % 360.0) / 360.0, s, v)
    return (r * 255.0, g * 255.0, b * 255.0)


def hue_dist(a, b):
    d = abs((a - b) % 360.0)
    return min(d, 360.0 - d)


def predicted_tones(hue_arg, brightness, expect):
    """{'rim','iris','glow','core'} -> float RGB, derived like LensTones.of, or the caller's --expect-tones."""
    tones = {"core": tuple(float(c) for c in HAL["core"])}
    if expect:
        for name, hx in zip(("rim", "iris", "glow"), expect):
            tones[name] = tuple(float(int(hx[i:i + 2], 16)) for i in (0, 2, 4))
        return tones
    for name in ("rim", "iris", "glow"):
        h, s, v = hsv(HAL[name])
        if hue_arg != "hal":
            h = float(hue_arg)
        tones[name] = from_hsv(h, s, v * brightness)
    return tones


def gradient_colour(stops, tones, t):
    """Piecewise-linear sRGB interpolation over [(t, tone name)] — how Android's gradient shader fills it."""
    t = min(max(t, 0.0), 1.0)
    for (t0, n0), (t1, n1) in zip(stops, stops[1:]):
        if t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            a, b = tones[n0], tones[n1]
            return tuple(a[c] + (b[c] - a[c]) * f for c in range(3))
    return tones[stops[-1][1]]


# ---------------------------------------------------------------- the dump
NODE_RE = re.compile(r'<node\b([^>]*)/?>')
ATTR_RE = re.compile(r'(\w[\w:-]*)="([^"]*)"')
BOUNDS_RE = re.compile(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]')


def find_node(path, rid):
    s = open(path, encoding="utf-8", errors="replace").read()
    for m in NODE_RE.finditer(s):
        attrs = dict(ATTR_RE.findall(m.group(1)))
        if attrs.get("resource-id") == rid:
            b = BOUNDS_RE.search(attrs.get("bounds", ""))
            if b:
                return tuple(int(v) for v in b.groups())
    return None


def median(xs):
    xs = sorted(xs)
    n = len(xs)
    if n == 0:
        return None
    return xs[n // 2] if n % 2 else 0.5 * (xs[n // 2 - 1] + xs[n // 2])


# ---------------------------------------------------------------- geometry from the image
def lit_centroid(px, box, margin=8):
    x1, y1, x2, y2 = box
    sx = sy = n = 0.0
    for y in range(max(0, y1 - margin), y2 + margin):
        for x in range(max(0, x1 - margin), x2 + margin):
            r, g, b = px[x, y][:3]
            if max(r, g, b) / 255.0 > LIT_V:
                sx += x + 0.5
                sy += y + 0.5
                n += 1
    if n == 0:
        return None, 0
    return (sx / n, sy / n), int(n)


def ray_profile(px, cx, cy, angle, rmax, size):
    """V along one ray from the centre, 1 px steps (nearest pixel)."""
    out = []
    w, h = size
    for i in range(int(rmax)):
        r = i + 0.5
        x = int(cx + r * math.cos(angle))
        y = int(cy + r * math.sin(angle))
        if 0 <= x < w and 0 <= y < h:
            out.append(max(px[x, y][:3]) / 255.0)
        else:
            out.append(0.0)
    return out


def ring_radii(px, cx, cy, size, rays=16, rmax=150):
    inner, outer = [], []
    for k in range(rays):
        prof = ray_profile(px, cx, cy, 2 * math.pi * k / rays, rmax, size)
        lit = [i for i, v in enumerate(prof) if v > LIT_V]
        if not lit:
            continue
        inner.append(lit[0])          # first lit pixel: its inner edge sits at r = i
        outer.append(lit[-1] + 1)     # last lit pixel: its outer edge at r = i + 1
    return median(inner), median(outer), len(inner)


def disc_radius(px, cx, cy, size, rays=16, rmax=160, skip=15):
    edges = []
    for k in range(rays):
        prof = ray_profile(px, cx, cy, 2 * math.pi * k / rays, rmax, size)
        best, best_i = 0.0, None
        for i in range(skip, len(prof) - 1):
            drop = prof[i - 1] - prof[i + 1]
            if drop > best:
                best, best_i = drop, i
        if best_i is not None and best > 0.08:
            edges.append(best_i + 0.5)
    return median(edges), len(edges)


# ---------------------------------------------------------------- the measurement
def measure_bands(px, size, cx, cy, radius, bands, stops, tones, hue_tol, v_tol, min_samples, required):
    """One record per band with the per-pixel hue / V error medians."""
    w, h = size
    rmax = radius * (max(b[2] for b in bands) + 0.02) + 2
    acc = {name: {"h": [], "v": [], "s": [], "rgb": [], "pred": []} for name, _, _ in bands}
    for y in range(max(0, int(cy - rmax)), min(h, int(cy + rmax) + 1)):
        for x in range(max(0, int(cx - rmax)), min(w, int(cx + rmax) + 1)):
            t = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / radius
            for name, lo, hi in bands:
                if lo <= t < hi:
                    rgb = px[x, y][:3]
                    pred = gradient_colour(stops, tones, t)
                    hs, ss, vs = hsv(rgb)
                    hp, sp, vp = hsv(pred)
                    a = acc[name]
                    a["v"].append(vs - vp)
                    a["s"].append(ss)
                    if sp >= 0.3:
                        a["h"].append(hue_dist(hs, hp))
                    a["rgb"].append(rgb)
                    a["pred"].append(pred)
                    break
    records = []
    for name, lo, hi in bands:
        a = acc[name]
        n = len(a["rgb"])
        rec = {"region": name, "t": [lo, hi], "r_px": [lo * radius, hi * radius], "samples": n, "required": name in required}
        if n:
            rec["sampled_median_rgb"] = [median([c[i] for c in a["rgb"]]) for i in range(3)]
            rec["predicted_median_rgb"] = [round(median([c[i] for c in a["pred"]]), 1) for i in range(3)]
            rec["hue_err_median_deg"] = round(median(a["h"]), 2) if a["h"] else None
            rec["v_err_median"] = round(median(a["v"]), 4)
            rec["s_median"] = round(median(a["s"]), 3)
            rec["hue_ok"] = (rec["hue_err_median_deg"] is None) or rec["hue_err_median_deg"] <= hue_tol
            rec["v_ok"] = abs(rec["v_err_median"]) <= v_tol
        rec["present"] = n >= min_samples
        rec["pass"] = rec["present"] and rec.get("hue_ok", False) and rec.get("v_ok", False)
        records.append(rec)
    return records


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0], formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--screenshot", required=True)
    ap.add_argument("--dump", required=True, help="uiautomator dump taken with the screenshot (finds the persona node)")
    ap.add_argument("--hue", required=True, help="expected lens hue in degrees, or `hal` = each Brand tone's own hue")
    ap.add_argument("--brightness", type=float, required=True, help="1.0 (hal / accent) or 0.5 (hal_dim)")
    ap.add_argument("--form", choices=("ring", "disc"), default="ring", help="ring = the idle persona (default)")
    ap.add_argument("--node", help="persona node id; default cortana_persona_large_idle (ring) / _listening (disc)")
    ap.add_argument("--expect-tones", help="rim,iris,glow as RRGGBB hex — overrides the (hue, brightness) derivation")
    ap.add_argument("--require", default="", help="comma list of extra regions that must be present (e.g. iris)")
    ap.add_argument("--hue-tol", type=float, default=8.0)
    ap.add_argument("--v-tol", type=float, default=0.05)
    ap.add_argument("--min-samples", type=int, default=40)
    ap.add_argument("--radius-tol", type=float, default=2.5, help="ring: allowed |measured - expected| radius (px)")
    ap.add_argument("--out")
    a = ap.parse_args(argv)

    result = {"args": vars(a)}

    def finish(verdict, summary, code):
        result.update({"verdict": verdict, "summary": summary, "exit": code})
        if a.out:
            with open(a.out, "w") as f:
                json.dump(result, f, indent=1)
        log("RESULT %s %s" % (verdict, summary))
        return code

    if a.hue != "hal":
        try:
            float(a.hue)
        except ValueError:
            ap.error("--hue wants degrees or `hal`")
    expect = a.expect_tones.split(",") if a.expect_tones else None
    if expect and (len(expect) != 3 or any(len(x.strip("#")) != 6 for x in expect)):
        ap.error("--expect-tones wants rim,iris,glow as three RRGGBB values")
    expect = [x.strip("#") for x in expect] if expect else None
    tones = predicted_tones(a.hue, a.brightness, expect)
    result["tones"] = {k: [round(c, 1) for c in v] for k, v in tones.items()}
    log("lens_check: form=%s hue=%s brightness=%.2f%s" % (a.form, a.hue, a.brightness,
                                                          " expect-tones=%s" % ",".join(expect) if expect else ""))
    for name in ("glow", "iris", "rim", "core"):
        h, s, v = hsv(tones[name])
        log("  predicted %-4s rgb (%5.1f,%5.1f,%5.1f)  h %6.1f  s %.3f  v %.3f%s" % (
            (name,) + tuple(tones[name]) + (h, s, v, "  (Brand, unchanged)" if name == "core" else "")))

    node = a.node or ("cortana_persona_large_idle" if a.form == "ring" else "cortana_persona_large_listening")
    box = find_node(a.dump, node)
    if box is None:
        return finish("UNUSABLE", "node %s not in the dump" % node, 2)
    im = Image.open(a.screenshot).convert("RGB")
    px = im.load()
    size = im.size
    ncx, ncy = (box[0] + box[2]) / 2.0, (box[1] + box[3]) / 2.0
    log("  node %s bounds %s -> centre (%.1f, %.1f), box %d x %d px (expected %.1f = %.1f epx)" % (
        node, list(box), ncx, ncy, box[2] - box[0], box[3] - box[1], BOX_EPX * PX_PER_EPX, BOX_EPX))
    centroid, lit = lit_centroid(px, box)
    if centroid is None:
        return finish("UNUSABLE", "no lit pixel (V > %.2f) inside the persona box: nothing drawn" % LIT_V, 2)
    cx, cy = centroid
    log("  lit pixels %d, centroid (%.2f, %.2f), %.2f px from the node centre%s" % (
        lit, cx, cy, math.hypot(cx - ncx, cy - ncy), " (WARNING > 3 px)" if math.hypot(cx - ncx, cy - ncy) > 3 else ""))
    result["geometry"] = {"node": node, "box": list(box), "node_centre": [ncx, ncy], "centroid": [cx, cy], "lit": lit}

    if a.form == "ring":
        inner, outer, rays = ring_radii(px, cx, cy, size)
        log("  ring radii (median over %d rays): inner %.1f (expected %.1f), outer %.1f (expected %.1f)" % (
            rays, inner or -1, RING_INNER_R, outer or -1, RING_OUTER_R))
        result["geometry"].update({"inner_r": inner, "outer_r": outer, "rays": rays})
        if inner is None or rays < 12 or abs(inner - RING_INNER_R) > a.radius_tol or abs(outer - RING_OUTER_R) > a.radius_tol:
            return finish("UNUSABLE", "the lit form is not the settled idle ring (inner %s / outer %s px vs %d / %d)" % (
                "%.1f" % inner if inner else "?", "%.1f" % outer if outer else "?", RING_INNER_R, RING_OUTER_R), 2)
        radius, bands, stops = RING_OUTER_R, RING_BANDS, RING_STOPS
        log("  gradient: radialGradient(0 -> glow, 1 -> rim) of radius %.0f px; the stroke shows t %.3f .. 1.0" % (
            RING_OUTER_R, RING_INNER_R / RING_OUTER_R))
    else:
        radius, edges = disc_radius(px, cx, cy, size)
        log("  disc radius (sharpest V drop, median over %d rays): %s px" % (edges, "%.1f" % radius if radius else "none"))
        result["geometry"].update({"disc_r": radius, "rays": edges})
        if radius is None or edges < 12 or radius < 20:
            return finish("UNUSABLE", "no opaque disc edge found around the centre", 2)
        bands, stops = [(n, lo, min(hi, (radius - AA_PX) / radius)) for n, lo, hi in DISC_BANDS], DISC_STOPS

    required = {n for n, _, _ in bands} | {r for r in a.require.split(",") if r}
    records = measure_bands(px, size, cx, cy, radius, bands, stops, tones, a.hue_tol, a.v_tol, a.min_samples, required)
    for name in NOT_DRAWN[a.form]:
        if name in required and name not in {r["region"] for r in records}:
            records.append({"region": name, "samples": 0, "required": True, "present": False, "pass": False,
                            "note": "required by the caller but the %s form never draws it" % a.form})
        elif name not in {r["region"] for r in records}:
            records.append({"region": name, "samples": 0, "required": False, "present": False, "pass": True,
                            "note": "not drawn by the %s form (by design: %s)" % (
                                a.form, "drawLensRing's gradient has only glow and rim stops, Lens.kt:117-119")})
    result["regions"] = records

    log("REGIONS (hue tol +-%.0f deg where predicted S >= 0.3; V tol +-%.2f; min %d samples)" % (a.hue_tol, a.v_tol, a.min_samples))
    log("  %-5s %-12s %-8s %-22s %-22s %-9s %-9s %-7s %s" % ("band", "r px", "samples", "sampled median", "predicted median",
                                                          "hue err", "V err", "S", "verdict"))
    for r in records:
        if r["samples"] == 0:
            log("  %-5s %-12s %-8d %s" % (r["region"], "-", 0, r.get("note", "MISSING" if r["required"] else "")))
            continue
        verdict = "ok" if r["pass"] else ("MISSING" if not r["present"] else
                                          " ".join(w for w, ok in (("HUE", r["hue_ok"]), ("BRIGHTNESS", r["v_ok"])) if not ok))
        log("  %-5s %-12s %-8d %-22s %-22s %-9s %-9s %-7s %s" % (
            r["region"], "%.0f-%.0f" % tuple(r["r_px"]), r["samples"],
            "(%d,%d,%d)" % tuple(int(c) for c in r["sampled_median_rgb"]),
            "(%.0f,%.0f,%.0f)" % tuple(r["predicted_median_rgb"]),
            "%.1f" % r["hue_err_median_deg"] if r["hue_err_median_deg"] is not None else "n/a",
            "%+.3f" % r["v_err_median"], "%.3f" % r["s_median"], verdict))

    missing = [r["region"] for r in records if r["required"] and not r["present"]]
    bad_hue = [r["region"] for r in records if r["present"] and not r.get("hue_ok", True)]
    bad_v = [r["region"] for r in records if r["present"] and not r.get("v_ok", True)]
    if missing or bad_hue or bad_v:
        parts = []
        if missing:
            parts.append("missing region(s) %s" % ",".join(missing))
        if bad_hue:
            parts.append("hue off in %s" % ",".join(bad_hue))
        if bad_v:
            parts.append("brightness off in %s" % ",".join(bad_v))
        return finish("FAIL", "; ".join(parts), 1)
    measured = [r for r in records if r["samples"]]
    return finish("PASS", "%d regions (%s) on hue %s at brightness %.2f, %d samples" % (
        len(measured), ",".join(r["region"] for r in measured), a.hue, a.brightness, sum(r["samples"] for r in measured)), 0)


if __name__ == "__main__":
    sys.exit(main())
