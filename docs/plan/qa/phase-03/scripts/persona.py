#!/usr/bin/env python3
"""E4: measure Cortana's persona out of screenrecord frames, to sub-pixel precision.

    persona.py <frames-dir>

Prints `key=value` lines for the driver to assert on, and exits non-zero when the persona could not be
found at all — a measurement that silently returns a default is worse than no measurement.

Method (PLAN RV11, and the correction phase 02's harness made): the accent disc and its halo are found
by their COLOUR, not by a brightness threshold, and each edge is read at the half-intensity crossing
between the two neighbouring pixels. A hard threshold on an antialiased edge saturates about three
pixels early and quietly hides the tail of an ease-out.

The page is black (R6 §3.1.14) and the persona is the only accent-coloured thing on it, which is what
makes a colour search safe here.
"""
import glob
import os
import sys

from PIL import Image

PX_PER_EPX = 1080 / 360.0
FPS = 60.0

# Windows "Default Blue" 0078D7, the out-of-box accent (phase 01 X26). The halo is the same hue at 25 %
# over black, so both are found by hue rather than by brightness.
ACCENT = (0x00, 0x78, 0xD7)


def accent_score(pixel):
    """How accent-like a pixel is, 0..1, independent of how bright it is."""
    r, g, b = pixel[:3]
    if b < 24:
        return 0.0
    # The accent is strongly blue-dominant with a mid green and almost no red.
    if not (b > g > r):
        return 0.0
    scale = b / ACCENT[2]
    expected_g = ACCENT[1] * scale
    expected_r = ACCENT[0] * scale
    if abs(g - expected_g) > 26 or abs(r - expected_r) > 26:
        return 0.0
    return min(1.0, b / 255.0 * 4)


def row_extent(image, y, width):
    """The sub-pixel left and right edges of the accent run on row [y], or None."""
    scores = [accent_score(image.getpixel((x, y))) for x in range(width)]
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
        scores = [accent_score(image.getpixel((x, widest_y))) for x in range(width)]
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
