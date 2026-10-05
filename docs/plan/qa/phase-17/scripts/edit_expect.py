#!/usr/bin/env python3
"""Phase 17 E6: what each editor tool must produce, computed on the host from the PHASE DOC's own numbers (T17-3, r3 V12).

The 4 x 5 matrices are read from the `matrix` lines of the doc's Decisions block, never from the app, so a build cannot
grade itself. Rules, as the doc states them: Android ColorMatrix order (rows R' G' B' A'; columns r g b a and a constant
on the 0-255 scale), applied to 8-bit sRGB values as they are, each result rounded to the nearest integer and clamped.
Light step k is the identity with the constant 20*k; colour step k is the saturation matrix for s = 1 + 0.25*k with luma
weights 0.213 / 0.715 / 0.072. Tools compose in the fixed order filter -> light -> colour -> enhance.

usage:
  edit_expect.py list                         the tool ids the doc names
  edit_expect.py pixel <tool> <r,g,b>         the result for one tool (enhance | light:+1 | colour:-2 | filter:sepia ...)
  edit_expect.py chain <r,g,b> <tool>...      the result for several tools, composed in the doc's fixed order
  edit_expect.py crop <w> <h> <fraction>      the centre crop's size (fraction of each side, e.g. 0.5)
  edit_expect.py straighten <w> <h> <deg>     the largest axis-aligned rectangle inside the rotated picture (w h)
  edit_expect.py check                        every doc matrix moves qa-photo-0..2 by >= 16 on some channel (exit 1 if not)
"""
import math
import pathlib
import re
import sys

DOC = pathlib.Path(__file__).resolve().parents[3] / "phase-17-inbox-photos-camera-video.md"
LUMA = (0.213, 0.715, 0.072)
FIXTURES = {"qa-photo-0": (220, 40, 40), "qa-photo-1": (40, 180, 80), "qa-photo-2": (40, 90, 220)}
ORDER = ("filter", "light", "colour", "enhance")


def doc_matrices():
    out = {}
    for line in DOC.read_text().splitlines():
        m = re.match(r"^  matrix (\S+)\s+(.*)$", line)
        if not m:
            continue
        nums = [float(x) for x in m.group(2).split()]
        if len(nums) != 20:
            sys.exit("edit_expect: the doc's matrix %s has %d numbers, not 20" % (m.group(1), len(nums)))
        if m.group(1) in out:
            sys.exit("edit_expect: the doc names matrix %s twice" % m.group(1))
        out[m.group(1)] = nums
    if not out:
        sys.exit("edit_expect: no matrix line found in %s" % DOC)
    return out


def saturation(s):
    r, g, b = LUMA
    return [r * (1 - s) + s, g * (1 - s), b * (1 - s), 0, 0,
            r * (1 - s), g * (1 - s) + s, b * (1 - s), 0, 0,
            r * (1 - s), g * (1 - s), b * (1 - s) + s, 0, 0,
            0, 0, 0, 1, 0]


def matrix_for(tool, doc):
    if tool in doc:
        return doc[tool]
    kind, _, arg = tool.partition(":")
    if kind == "light" and re.fullmatch(r"[+-]?[0-5]", arg):
        k = int(arg)
        return [1, 0, 0, 0, 20 * k, 0, 1, 0, 0, 20 * k, 0, 0, 1, 0, 20 * k, 0, 0, 0, 1, 0]
    if kind == "colour" and re.fullmatch(r"[+-]?[0-4]", arg):
        return saturation(1 + 0.25 * int(arg))
    sys.exit("edit_expect: the doc names no tool %r" % tool)


def apply(m, rgb):
    r, g, b = rgb
    out = []
    for row in range(3):
        v = m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * 255 + m[row * 5 + 4]
        out.append(max(0, min(255, int(math.floor(v + 0.5)))))
    return tuple(out)


def rgb_arg(text):
    parts = text.split(",")
    if len(parts) != 3:
        sys.exit("edit_expect: a colour is r,g,b")
    return tuple(int(p) for p in parts)


def largest_rect(w, h, degrees):
    """The largest axis-aligned rectangle (same aspect family) inside a w x h picture rotated by `degrees`."""
    a = abs(math.radians(degrees)) % math.pi
    if a > math.pi / 2:
        a = math.pi - a
    sin_a, cos_a = math.sin(a), math.cos(a)
    side_long, side_short = max(w, h), min(w, h)
    if side_short <= 2.0 * sin_a * cos_a * side_long or abs(sin_a - cos_a) < 1e-10:
        x = 0.5 * side_short
        wr, hr = (x / sin_a, x / cos_a) if w >= h else (x / cos_a, x / sin_a)
    else:
        cos_2a = cos_a * cos_a - sin_a * sin_a
        wr, hr = (w * cos_a - h * sin_a) / cos_2a, (h * cos_a - w * sin_a) / cos_2a
    return int(math.floor(wr)), int(math.floor(hr))


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    cmd = argv[1]
    if cmd == "crop":
        w, h, f = int(argv[2]), int(argv[3]), float(argv[4])
        print(int(round(w * f)), int(round(h * f)))
        return
    if cmd == "straighten":
        print(*largest_rect(int(argv[2]), int(argv[3]), float(argv[4])))
        return
    doc = doc_matrices()
    if cmd == "list":
        print("\n".join(sorted(doc)))
    elif cmd == "pixel":
        print("%d,%d,%d" % apply(matrix_for(argv[2], doc), rgb_arg(argv[3])))
    elif cmd == "chain":
        rgb = rgb_arg(argv[2])
        tools = sorted(argv[3:], key=lambda t: ORDER.index(t.partition(":")[0]))
        for t in tools:
            rgb = apply(matrix_for(t, doc), rgb)
        print("%d,%d,%d" % rgb)
    elif cmd == "check":
        bad = 0
        for name, m in sorted(doc.items()):
            for fx, rgb in FIXTURES.items():
                out = apply(m, rgb)
                moved = max(abs(a - b) for a, b in zip(out, rgb))
                if moved < 16:
                    bad += 1
                    print("FAIL %s on %s: %s -> %s moves only %d" % (name, fx, rgb, out, moved))
        print("%d matrices x %d fixtures checked, %d too close to the original" % (len(doc), len(FIXTURES), bad))
        sys.exit(1 if bad else 0)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv)
