#!/usr/bin/env python3
"""E4: measure the persona out of screenrecord frames, to sub-pixel precision.

    persona.py <frames-dir>

Prints `key=value` lines for the driver to assert on, and exits non-zero when the persona could not be
found at all — a measurement that silently returns a default is worse than no measurement.

Method (PLAN RV11, and the correction phase 02's harness made): the persona is found by its COLOUR,
not by a brightness threshold, and every edge is read at the half-intensity crossing between the two
neighbouring pixels. A hard threshold on an antialiased edge saturates about three pixels early and
quietly hides the tail of an ease-out.

2026-09-21, the lens: the persona is painted as HAL's lens rather than a flat accent fill (INDEX Change
Log), so the hue searched for is the lens red instead of the accent blue. The method is unchanged and
so is everything it measures: the lens tones all sit on one hue line and its outermost tone is opaque
to the edge, so the crossings land where the flat disc's did.

2026-09-21, the first real run: two things this file got wrong before it had ever been run on a capture.

  1. It separated the opaque disc from its 25 %-alpha halo with a cut at 0.85 of the peak SCORE, where
     the halo's own score is 0.847 — a margin of three thousandths, on a saturating score. It now
     separates them by the raw channel, where the halo plateau sits at about a quarter of the disc's
     rim and a cut at twice the halo's own level clears both by a factor of two. Measured on a real
     listening frame: halo plateau r = 50-53, disc rim r = 141-145, core r = 255.
  2. It measured `listen_*` over EVERY frame of the capture and `idle_*` over the first third. A
     capture holds four different things — Start with the Cortana tile before the session opens, the
     idle ring, the listening persona, then the small persona on the response card — so the minima and
     maxima were taken across all four. It now segments the capture and measures each form inside its
     own segment. The segmentation uses only what the frames say (is the page black, does the frame
     have a halo, where is the centre), never the values under test.
"""
import glob
import os
import sys

from PIL import Image

PX_PER_EPX = 1080 / 360.0
FPS = 60.0

# Brand.LENS_RIM 8A1008, the deepest tone of the lens and the reference the others are scaled from
# (Brand.kt). The halo is the iris at 25 % over black, so both are found by hue, not by brightness.
LENS = (0x8A, 0x10, 0x08)

# A pixel is on the lens hue line if its green and blue are within this of the rim's, scaled by red.
HUE_TOLERANCE = 26

# Anything at more than twice the outer plateau's level is opaque, not the 25 % halo.
OPAQUE_FACTOR = 2.0

# Where on the run's own histogram each plateau is read. The halo is the wider of the two runs, so
# the lower quartile lands on it; the disc's rim is the dimmest tenth of what is left.
OUTER_QUANTILE = 0.25
RIM_QUANTILE = 0.10

# The page is black (R6 §3.1.14); Start is not. Two columns well outside the widest persona.
PAGE_COLUMNS = (60, 1020)
PAGE_BLACK = 24

# A22: the ring pops in over 650 ms. E4 does not measure the pop-in, so the idle segment's first
# 650 ms are not part of its settled diameter.
POP_MS = 650

# Two segments are different places on the page if their centres differ by more than this.
CENTRE_BREAK_EPX = 20.0


def lens_red(pixel):
    """The red channel if the pixel is on the lens hue line, else 0."""
    r, g, b = pixel[:3]
    if r < 24 or not (r > g > b):
        return 0
    scale = r / LENS[0]
    if abs(g - LENS[1] * scale) > HUE_TOLERANCE or abs(b - LENS[2] * scale) > HUE_TOLERANCE:
        return 0
    return r


def crossing(values, inner, outer, level):
    """The sub-pixel x where [values] crosses [level] between two neighbouring pixels."""
    a, b = values[outer], values[inner]
    if b == a:
        return float(inner)
    return outer + (level - a) / (b - a) * (inner - outer)


def extent(values, level):
    """The sub-pixel left and right edges of the run at or above [level], or None."""
    inside = [x for x, v in enumerate(values) if v >= level]
    if len(inside) < 2:
        return None
    first, last = inside[0], inside[-1]
    left = crossing(values, first, first - 1, level) if first > 0 else float(first)
    right = crossing(values, last, last + 1, level) if last < len(values) - 1 else float(last)
    return left, right


def page_is_black(image, y):
    """R6 §3.1.14: the Cortana page is black. Start, with its tiles, is not."""
    for x in PAGE_COLUMNS:
        p = image.getpixel((x, y))
        if max(p[:3]) > PAGE_BLACK:
            return False
    return True


def percentile(values, q):
    """The q-th percentile of [values], 0..1. Used to read a PLATEAU level, never an edge pixel."""
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(q * len(ordered)))]


def measure_row(values):
    """(outer_width, inner_width or None) for one row of lens-red values."""
    peak = max(values)
    if peak < 24:
        return None
    rough = extent(values, max(4.0, peak * 0.06))
    if not rough:
        return None
    lo, hi = int(rough[0]), int(rough[1]) + 1
    # The outer plateau's level is read as a PERCENTILE of the run's non-zero pixels, not from a pixel
    # one step inside the end. That pixel is on the antialiased edge, so it reads low, and with the
    # opaque cut at twice it the cut could fall INSIDE the halo — which is exactly what happened on 37
    # of 212 listening frames, where the halo's own width came back as the disc's. The persona is two
    # plateaus (a 25 % halo and an opaque disc) and the halo is always the wider run of the two, so the
    # lower quartile of the non-zero pixels sits squarely on the halo.
    body = [v for v in values[lo:hi] if v > 0]
    if len(body) < 8:
        return None
    outer_level = percentile(body, OUTER_QUANTILE)
    if outer_level < 8:
        return None
    outer = extent(values, outer_level / 2.0)
    if not outer:
        return None
    # A halo is present when something inside is at more than twice the outer plateau's level. Its
    # opaque edge is then read at the half-crossing BETWEEN the two plateaus — not at a fraction of the
    # peak, because the specular core is far brighter than the rim and a fraction of it reads late.
    opaque_cut = outer_level * OPAQUE_FACTOR
    if peak <= opaque_cut:
        return (outer[1] - outer[0], None)
    inner_rough = extent(values, opaque_cut)
    if not inner_rough:
        return (outer[1] - outer[0], None)
    a, b = int(inner_rough[0]), int(inner_rough[1]) + 1
    core = [v for v in values[a:b] if v >= opaque_cut]
    if len(core) < 4:
        return (outer[1] - outer[0], None)
    rim_level = percentile(core, RIM_QUANTILE)
    inner = extent(values, (rim_level + outer_level) / 2.0)
    if not inner:
        return (outer[1] - outer[0], None)
    return (outer[1] - outer[0], inner[1] - inner[0])


def measure(path):
    """(centre_y_px, outer_px, inner_px or None) for one frame, or None."""
    with Image.open(path) as raw:
        image = raw.convert("RGB")
        width, height = image.size
        px = image.load()
        rows = {}
        for y in range(0, int(height * 0.7), 2):
            values = [lens_red(px[x, y]) for x in range(width)]
            m = measure_row(values)
            if m:
                rows[y] = m
        if not rows:
            return None
        widest_y = max(rows, key=lambda y: rows[y][0])
        if not page_is_black(image, widest_y):
            return None
        outer, inner = rows[widest_y]
        ys = sorted(rows)
        return (ys[0] + ys[-1]) / 2.0, outer, inner


def segment(series):
    """Split consecutive frames into runs of the same form at the same place on the page."""
    segments = []
    for index, (centre, outer, inner) in series:
        haloed = inner is not None
        if (segments and segments[-1]["haloed"] == haloed
                and abs(segments[-1]["frames"][-1][1] - centre) / PX_PER_EPX <= CENTRE_BREAK_EPX
                and index == segments[-1]["frames"][-1][0] + 1):
            segments[-1]["frames"].append((index, centre, outer, inner))
        else:
            segments.append({"haloed": haloed, "frames": [(index, centre, outer, inner)]})
    return segments


def mean(values):
    return sum(values) / len(values)


def main():
    frames = sorted(glob.glob(os.path.join(sys.argv[1], "f_*.png")))
    if not frames:
        print("no frames to measure")
        sys.exit(2)

    series = []
    for index, path in enumerate(frames):
        m = measure(path)
        if m:
            series.append((index, m))
    if len(series) < 10:
        print(f"the persona was found in only {len(series)} of {len(frames)} frames")
        sys.exit(2)
    print(f"persona found in {len(series)} of {len(frames)} frames")

    segments = segment(series)
    for s in segments:
        f = s["frames"]
        print(f"# segment frames {f[0][0]}-{f[-1][0]} haloed={s['haloed']} "
              f"centre={mean([c for _, c, _, _ in f]) / PX_PER_EPX:.1f} "
              f"outer={mean([o for _, _, o, _ in f]) / PX_PER_EPX:.1f}")

    # The idle ring: the longest segment with no halo. Its first 650 ms are the pop-in, which this row
    # does not measure, so they are not part of the settled diameter.
    plain = [s for s in segments if not s["haloed"]]
    if not plain:
        print("no un-haloed segment: the idle ring was never captured")
        sys.exit(2)
    idle = max(plain, key=lambda s: len(s["frames"]))["frames"]
    settled = idle[int(POP_MS / 1000.0 * FPS):] or idle
    print(f"idle_outer_epx={mean([o for _, _, o, _ in settled]) / PX_PER_EPX:.2f}")
    print(f"idle_centre_epx={mean([c for _, c, _, _ in settled]) / PX_PER_EPX:.2f}")
    idle_centre = mean([c for _, c, _, _ in settled])

    # Listening replaces the idle ring IN PLACE, so it is the haloed segment at the ring's own centre.
    # The response card's small persona is haloed too, but it sits at the top of the page.
    haloed = [s for s in segments if s["haloed"]]
    in_place = [s for s in haloed
                if abs(mean([c for _, c, _, _ in s["frames"]]) - idle_centre) / PX_PER_EPX
                <= CENTRE_BREAK_EPX]
    if not in_place:
        print("no haloed segment at the idle ring's centre: listening was never captured")
        sys.exit(2)
    listening = max(in_place, key=lambda s: len(s["frames"]))["frames"]
    print(f"listen_frames={len(listening)}")

    halos = [o for _, _, o, _ in listening]
    discs = [i for _, _, _, i in listening]
    centres = [c for c, in [(c,) for _, c, _, _ in listening]]

    print(f"listen_centre_epx={mean(centres) / PX_PER_EPX:.2f}")
    print(f"listen_halo_min_epx={min(halos) / PX_PER_EPX:.2f}")
    print(f"listen_halo_max_epx={max(halos) / PX_PER_EPX:.2f}")
    print(f"listen_disc_min_epx={min(discs) / PX_PER_EPX:.2f}")
    print(f"listen_disc_max_epx={max(discs) / PX_PER_EPX:.2f}")

    # The period, read as UPWARD CROSSINGS of the halfway level rather than as maxima.
    #
    # R6 §3.1.8's cycle is rise, a 200 ms hold at the top, fall, a hold at the bottom — so the maximum
    # is a PLATEAU, not a point. Measurement noise of a tenth of a pixel across that plateau gives a
    # maxima-finder several "peaks" inside one cycle, and the mean gap collapses: this capture read
    # 871 ms against a real 1040 ms, and a second capture of the same build read 1042, which is how a
    # method that depends on noise looks. A crossing happens once per cycle whatever the plateau does.
    mid = (min(halos) + max(halos)) / 2
    crossings = [i for i in range(1, len(halos)) if halos[i - 1] < mid <= halos[i]]
    if len(crossings) >= 2:
        gaps = [(crossings[i + 1] - crossings[i]) / FPS * 1000 for i in range(len(crossings) - 1)]
        print(f"listen_period_ms={mean(gaps):.0f}")
        print(f"listen_period_cycles={len(gaps)}")
    else:
        print("listen_period_ms=")

    # Antiphase: the correlation between halo and disc through the listening segment.
    n = len(halos)
    mean_h, mean_d = mean(halos), mean(discs)
    cov = sum((halos[i] - mean_h) * (discs[i] - mean_d) for i in range(n))
    var_h = sum((h - mean_h) ** 2 for h in halos) ** 0.5
    var_d = sum((d - mean_d) ** 2 for d in discs) ** 0.5
    print(f"listen_antiphase_correlation={cov / (var_h * var_d):.3f}" if var_h and var_d
          else "listen_antiphase_correlation=")


if __name__ == "__main__":
    main()
