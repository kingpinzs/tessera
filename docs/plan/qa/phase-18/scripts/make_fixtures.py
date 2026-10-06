#!/usr/bin/env python3
"""Phase 18's host-made fixtures (the Acceptance "Fixtures" paragraph; build task 12), written for p18.sh's files_up.

usage: make_fixtures.py <gen-dir>

  <gen-dir>/media/       make_photos.py's own six images, untouched (qa-photo-0..5.png) — what media_up pushes
  <gen-dir>/QA-Files/    the pushed part of /sdcard/QA-Files: img-0..5.png, hidden/qa-hidden.png, hidden/.nomedia,
                         recent/r1.png r2.png r3.png never.png

Every image here is written by make_photos.py's own png() (its flat-colour, zlib-only writer), with that script's six
colours. make_photos.py's six files are 1,949 / 1,949 / 1,949 / 1,950 / 1,949 / 1,945 bytes, and the Fixtures paragraph
(r3 V13 / V12) wants no two fixtures to share a size — so each image here is the same colour field at its own
dimensions, which gives each its own size. The colours are make_photos.py's, in its order:
  0 (220,40,40)  1 (40,180,80)  2 (40,90,220)  3 (230,200,40)  4 (160,60,200)  5 (240,240,240)
The video and the MP3s are made by p18.sh (ffmpeg and a copy); the device-made files (a.txt, b.bin, .hidden.txt,
big.bin) by adb shell.
"""
import pathlib
import runpy
import sys

gen = pathlib.Path(sys.argv[1])
here = pathlib.Path(__file__).resolve().parent
make_photos = here.parent.parent / "phase-01" / "scripts" / "make_photos.py"

media = gen / "media"
argv = sys.argv
sys.argv = [str(make_photos), str(media)]
ns = runpy.run_path(str(make_photos))  # writes qa-photo-0..5.png into <gen>/media and hands back png()
sys.argv = argv
png, colours = ns["png"], ns["colours"]

root = gen / "QA-Files"
(root / "hidden").mkdir(parents=True, exist_ok=True)
(root / "recent").mkdir(parents=True, exist_ok=True)
(root / "hidden" / ".nomedia").write_bytes(b"")

# name, colour index, width, height
IMAGES = [
    ("img-0.png", 0, 640, 480),
    ("img-1.png", 1, 640, 960),
    ("img-2.png", 2, 640, 1440),
    ("img-3.png", 3, 640, 1920),
    ("img-4.png", 4, 640, 2400),
    ("img-5.png", 5, 640, 2880),
    ("hidden/qa-hidden.png", 4, 800, 600),
    ("recent/r1.png", 0, 320, 240),
    ("recent/r2.png", 1, 320, 720),
    ("recent/r3.png", 2, 320, 1200),
    ("recent/never.png", 3, 320, 1680),
]
for name, c, w, h in IMAGES:
    png(root / name, colours[c], w, h)
    print(f"{name}\t{(root / name).stat().st_size}\t{colours[c]}\t{w}x{h}")
