#!/usr/bin/env python3
"""The host oracle for acrylic-on values (T13-2, T13-11, T13-18): 0.8*T + 0.2*B, where B is a Gaussian of the
backdrop at sigma = 0.57735*r + 0.5 px (HWUI's conversion), computed here from a capture that is NOT the one under
judgement (the same screen with the surface closed, or the pulled fixture), never from the capture being graded.
The material's noise is not reproduced: every comparison is a patch mean or a column-mean profile (T13-17).

  live <closed.png> <T:r,g,b> <radius_px> <l,t,r,b of the backdrop layer L> patch <x> <y> <w> <h>
  live <closed.png> <T:r,g,b> <radius_px> <l,t,r,b of L> profile <y0> <y1> <x0> <x1>
      The live source: L holds only what its page draws (outside L's bounds the S layer reads transparent, i.e.
      black, T13-11); the blur is clamped at the window edge. patch -> mean R G B; profile -> one line per x:
      "x R G B" of the column mean over rows y0..y1.
  static <fixture.png> <T:r,g,b> <radius_px> <l,t,r,b of the page> patch|profile ...
      The static source: the fixture placed ContentScale.Crop into the page's bounds at scale 1 (T13-18), blurred
      with the clamp at the page's own edges, then tinted.
"""
import sys

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

ALPHA = 0.8


def sigma(r):
    return 0.57735 * r + 0.5


def blur(arr, s):
    out = np.empty_like(arr)
    for c in range(3):
        out[..., c] = gaussian_filter(arr[..., c], s, mode="nearest", truncate=4.0)
    return out


def live_backdrop(path, box, r):
    img = np.asarray(Image.open(path).convert("RGB"), dtype=np.float64)
    l, t, rr, b = box
    masked = np.zeros_like(img)
    masked[t:b, l:rr] = img[t:b, l:rr]
    return blur(masked, sigma(r))


def static_backdrop(path, box, r, shape):
    l, t, rr, b = box
    w, h = rr - l, b - t
    src = Image.open(path).convert("RGB")
    scale = max(w / src.width, h / src.height)
    sw, sh = w / scale, h / scale
    sl, st = (src.width - sw) / 2, (src.height - sh) / 2
    page = src.resize((w, h), Image.BILINEAR, box=(sl, st, sl + sw, st + sh))
    page = blur(np.asarray(page, dtype=np.float64), sigma(r))
    full = np.zeros(shape + (3,))
    full[t:b, l:rr] = page
    return full


def main():
    kind, path, tint, radius, box = sys.argv[1:6]
    T = np.array([float(v) for v in tint.split(",")])
    r = float(radius)
    box = [int(v) for v in box.split(",")]
    mode = sys.argv[6]
    args = [int(v) for v in sys.argv[7:11]]
    if kind == "live":
        B = live_backdrop(path, box, r)
    else:
        size = Image.open(sys.argv[11]).size if len(sys.argv) > 11 else (1080, 2340)
        B = static_backdrop(path, box, r, (size[1], size[0]))
    E = ALPHA * T + (1 - ALPHA) * B
    if mode == "patch":
        x, y, w, h = args
        m = E[y:y + h, x:x + w].reshape(-1, 3).mean(axis=0)
        print(" ".join("%.1f" % v for v in m))
    else:
        y0, y1, x0, x1 = args
        cols = E[y0:y1 + 1, x0:x1 + 1].mean(axis=0)
        for i, v in enumerate(cols):
            print(x0 + i, " ".join("%.1f" % c for c in v))


if __name__ == "__main__":
    main()
