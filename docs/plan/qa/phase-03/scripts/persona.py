#!/usr/bin/env python3
"""E4: measure Cortana's persona out of screenrecord frames, to sub-pixel precision.

    persona.py <frames-dir>

Prints `key=value` lines for the driver to assert on, and exits non-zero when the persona could not be
found at all — a measurement that silently returns a default is worse than no measurement.

Method (PLAN RV11, and the correction phase 02's harness made): the disc and its halo are found by
their COLOUR, not by a brightness threshold, and each edge is read at the half-intensity crossing
between the two neighbouring pixels. A hard threshold on an antialiased edge saturates about three
pixels early and quietly hides the tail of an ease-out.

The page is black (R6 §3.1.14) and the persona is the only coloured thing on it, which is what makes a
colour search safe here.

2026-09-21: the persona is painted as HAL's lens rather than a flat accent fill (INDEX Change Log), so
the hue searched for is the lens red instead of the accent blue. The METHOD is unchanged, and so is
every number it measures: the lens is a gradient, but all of its tones sit on one hue line and the
outermost one is opaque to the edge, so the half-intensity crossings land exactly where the flat disc's
did. The specular core reads white and scores 0; that is fine, because the disc is measured from the
first and last pixel above the cut, not from a contiguous run.
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


def lens_score(pixel):
    """How lens-like a pixel is, 0..1, independent of how bright it is."""
    r, g, b = pixel[:3]
    if r < 24:
        return 0.0
    # Every lens tone is strongly red-dominant with a low green and a lower blue.
    if not (r > g > b):
        return 0.0
    scale = r / LENS[0]
    expected_g = LENS[1] * scale
    expected_b = LENS[2] * scale
    if abs(g - expected_g) > 26 or abs(b - expected_b) > 26:
        return 0.0
    return min(1.0, r / 255.0 * 4)


def row_extent(image, y, width):
    """The sub-pixel left and right edges of the lens run on row [y], or None."""
    scores = [lens_score(image.getpixel((x, y))) for x in range(width)]
    peak = max(scores)
    if peak < 0.12:
        return None
    half = peak / 2.0
    first = next((x for x, s in enumerate(scores) if s >= half), None)
    last = next((x for x in range(width - 1, -1, -1) if scores[x] >= half), None)
    if first is None or last is None or last <= first:
        return None

    def cross(inner, outer):
        a, b = scores[outer], scores[inner]
        if b == a:
            return float(inner)
        return outer + (half - a) / (b - a) * (inner - outer)

    left = cross(first, first - 1) if first > 0 else float(first)
    right = cross(last, last + 1) if last < width - 1 else float(last)
    return left, right


def measure(path):
    """(centre_y_px, halo_diameter_px, disc_diameter_px) for one frame, or None."""
    with Image.open(path) as raw:
        image = raw.convert("RGB")
        width, height = image.size
        # Scan the upper two thirds: the persona sits well above the text box.
        rows = {}
        for y in range(0, int(height * 0.7), 2):
            extent = row_extent(image, y, width)
            if extent:
                rows[y] = extent
        if not rows:
            return None
        widest_y = max(rows, key=lambda y: rows[y][1] - rows[y][0])
        left, right = rows[widest_y]
        halo = right - left
        # The disc is the SOLID core: on the widest row it is the run whose score is near the peak.
        scores = [lens_score(image.getpixel((x, widest_y))) for x in range(width)]
        peak = max(scores)
        core = [x for x, s in enumerate(scores) if s >= peak * 0.85]
        disc = (core[-1] - core[0]) if len(core) > 1 else 0.0
        ys = sorted(rows)
        centre_y = (ys[0] + ys[-1]) / 2.0
        return centre_y, halo, float(disc)


def main():
    frames = sorted(glob.glob(os.path.join(sys.argv[1], "f_*.png")))
    if not frames:
        print("no frames to measure")
        sys.exit(2)

    series = []
    for path in frames:
        m = measure(path)
        if m:
            series.append((path, m))
    if len(series) < 10:
        print(f"the persona was found in only {len(series)} of {len(frames)} frames")
        sys.exit(2)

    print(f"persona found in {len(series)} of {len(frames)} frames")

    halos = [m[1] for _, m in series]
    discs = [m[2] for _, m in series]
    centres = [m[0] for _, m in series]

    # The idle ring is the steady stretch before listening begins: the frames whose halo barely moves.
    idle = [(c, h, d) for c, h, d in (m for _, m in series[: len(series) // 3])]
    idle_outer = sum(h for _, h, _ in idle) / len(idle)
    idle_centre = sum(c for c, _, _ in idle) / len(idle)
    print(f"idle_outer_epx={idle_outer / PX_PER_EPX:.2f}")
    print(f"idle_centre_epx={idle_centre / PX_PER_EPX:.2f}")

    print(f"listen_centre_epx={sum(centres) / len(centres) / PX_PER_EPX:.2f}")
    print(f"listen_halo_min_epx={min(halos) / PX_PER_EPX:.2f}")
    print(f"listen_halo_max_epx={max(halos) / PX_PER_EPX:.2f}")
    print(f"listen_disc_min_epx={min(discs) / PX_PER_EPX:.2f}")
    print(f"listen_disc_max_epx={max(discs) / PX_PER_EPX:.2f}")

    # The period: the mean gap between successive halo maxima.
    peaks = [
        i for i in range(1, len(halos) - 1)
        if halos[i] >= halos[i - 1] and halos[i] > halos[i + 1] and halos[i] > (min(halos) + max(halos)) / 2
    ]
    if len(peaks) >= 2:
        gaps = [(peaks[i + 1] - peaks[i]) / FPS * 1000 for i in range(len(peaks) - 1)]
        print(f"listen_period_ms={sum(gaps) / len(gaps):.0f}")
    else:
        print("listen_period_ms=")

    # Antiphase: the correlation between halo and disc across the capture should be strongly negative.
    n = len(halos)
    mean_h = sum(halos) / n
    mean_d = sum(discs) / n
    cov = sum((halos[i] - mean_h) * (discs[i] - mean_d) for i in range(n))
    var_h = sum((h - mean_h) ** 2 for h in halos) ** 0.5
    var_d = sum((d - mean_d) ** 2 for d in discs) ** 0.5
    print(f"listen_antiphase_correlation={cov / (var_h * var_d):.3f}" if var_h and var_d
          else "listen_antiphase_correlation=")


if __name__ == "__main__":
    main()
