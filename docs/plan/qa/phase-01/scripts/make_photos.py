#!/usr/bin/env python3
"""Six flat-colour PNGs for the Photos tile, written with zlib so QA needs no image library.

usage: make_photos.py <dir>
"""
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
