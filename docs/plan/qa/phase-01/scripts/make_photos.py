#!/usr/bin/env python3
"""Six flat-colour PNGs for the Photos tile, written with zlib so QA needs no image library.

usage: make_photos.py <dir> [editor]

With `editor` (phase 17 build task 16, r3 V9) it also writes the two editor fixtures:
  qa-redeye.png  a grey field (128,128,128) with one pure-red disc (255,0,0), 40 px across, centred at (320, 240)
  qa-line.png    a black line 5 px thick through (320, 240), rising 10 degrees to the right, on white
"""
import math
import pathlib
import struct
import sys
import zlib

out = pathlib.Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)


def png(path, rgb, w=640, h=480):
    raw = b"".join(b"\x00" + bytes(rgb) * w for _ in range(h))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    )


colours = [(220, 40, 40), (40, 180, 80), (40, 90, 220), (230, 200, 40), (160, 60, 200), (240, 240, 240)]
for i, rgb in enumerate(colours):
    png(out / f"qa-photo-{i}.png", rgb)
print(f"{len(colours)} photos")


def png_pixels(path, pixel, w=640, h=480):
    """A PNG whose pixel (x, y) is pixel(x, y) -> (r, g, b)."""
    raw = b"".join(b"\x00" + b"".join(bytes(pixel(x, y)) for x in range(w)) for y in range(h))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    )


if len(sys.argv) > 2 and sys.argv[2] == "editor":
    png_pixels(out / "qa-redeye.png", lambda x, y: (255, 0, 0) if (x - 320) ** 2 + (y - 240) ** 2 <= 20 ** 2 else (128, 128, 128))
    # The line y = 240 - tan(10 deg) * (x - 320): screen y grows downwards, so it rises to the right.
    slope, norm = math.tan(math.radians(10)), math.cos(math.radians(10))
    png_pixels(out / "qa-line.png", lambda x, y: (0, 0, 0) if abs((y - 240) + slope * (x - 320)) * norm <= 2.5 else (255, 255, 255))
    print("2 editor fixtures")

