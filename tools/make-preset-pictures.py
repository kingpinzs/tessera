#!/usr/bin/env python3
"""Phase 12 build task 5: the theme presets' pictures, cropped and scaled for the S25 Ultra.

Each source is scaled to fill 4056 px of height (the S25 Ultra's 3120 x 1.3 parallax headroom, T12-2), the width
trimmed equally on both sides to 1872 (1440 x 1.3), and written as lossy WebP at quality 90 with no metadata — the
re-encode drops the C2PA content-credential chunks Jeremy's generated files carry (r3 D10). The outputs are committed
under app/src/main/res/drawable-nodpi/, so the CI runner builds them in without running this (r3 D11); re-run it by
hand when a source changes.

    tools/make-preset-pictures.py [OUT_DIR]      # default: app/src/main/res/drawable-nodpi

E12 runs it into a temp directory and compares each output's sha256 with the committed file.
"""
import hashlib
import os
import sys

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
W, H = 1872, 4056
QUALITY = 90

# Jeremy's picks (phase 12 Decisions 2026-09-28) and R12's two img0 files (Q8 C). soft-c is JPEG data under a .png
# name: Pillow reads a file by its content, not its name, and the log says which format it found.
SOURCES = [
    ("preset_hal", "art/themes/hal-a.png"),
    ("preset_soft", "art/themes/soft-c.png"),
    ("preset_lumia", "art/themes/lumia-b.png"),
    ("preset_midnight", "art/themes/midnight-b.png"),
    ("preset_w10m_hero", "docs/plan/r12/img0_w10m_1607-1709.jpg"),
    ("preset_w10m_streaks", "docs/plan/r12/img0_w10m_1507-1511.jpg"),
]


def crop(src: Image.Image) -> tuple[Image.Image, float]:
    """Scale to fill the height, then trim the width equally on both sides (centre crop)."""
    scale = H / src.height
    scaled_w = round(src.width * scale)
    if scaled_w < W:  # a source too narrow to fill the width at full height: fill the width instead
        scale = W / src.width
        scaled_h = round(src.height * scale)
        img = src.resize((W, scaled_h), Image.Resampling.LANCZOS)
        top = (scaled_h - H) // 2
        return img.crop((0, top, W, top + H)), scale
    img = src.resize((scaled_w, H), Image.Resampling.LANCZOS)
    left = (scaled_w - W) // 2
    return img.crop((left, 0, left + W, H)), scale


def main() -> int:
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "app/src/main/res/drawable-nodpi")
    os.makedirs(out_dir, exist_ok=True)
    for name, rel in SOURCES:
        path = os.path.join(REPO, rel)
        with Image.open(path) as src:
            fmt = src.format
            rgb = src.convert("RGB")
        img, scale = crop(rgb)
        out = os.path.join(out_dir, f"{name}.webp")
        # No exif / icc / xmp is passed, so none is written.
        img.save(out, "WEBP", quality=QUALITY, method=4)
        digest = hashlib.sha256(open(out, "rb").read()).hexdigest()
        print(f"{name}: {rel} ({fmt} {rgb.width}x{rgb.height}) scale={scale:.3f} -> {img.width}x{img.height} "
              f"{os.path.getsize(out)} bytes sha256={digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
