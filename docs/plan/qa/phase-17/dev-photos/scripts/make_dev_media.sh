#!/usr/bin/env bash
# Photos' development fixtures, made on the host: the six flat-colour PNGs (phase 01's make_photos.py) and a 10-s video
# whose frame is one solid colour per second, with a sine audio track (so a trim has an audio stream to keep).
# usage: make_dev_media.sh <dir>
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$1"; mkdir -p "$OUT"
python3 "$HERE/../../../phase-01/scripts/make_photos.py" "$OUT" >/dev/null
# The editor fixtures (qa-redeye.png: a 40-px pure-red disc at 320,240 on grey; qa-line.png: a black line rising to the
# right at 10 degrees on white) come from the same generator's "editor" mode, which the lead added on phase-17: this
# worktree's copy is used when it has the mode, else the lead's tree's (read only).
GEN="$HERE/../../../phase-01/scripts/make_photos.py"
grep -q '"editor"' "$GEN" || GEN="$HERE/../../../../../../../../../docs/plan/qa/phase-01/scripts/make_photos.py"
python3 "$GEN" "$OUT" editor >/dev/null
# A camera-like JPEG: stored sideways (800 x 600, EXIF Orientation 6) with a capture time, blue on its upright top half
# and red on its bottom half (PIL's exif_transpose and the platform's decoder agree: run 1 of C5 had this backwards) — for the saved copy's "upright, Orientation 1, capture time copied" (r3 D7).
python3 - "$OUT/qa-exif.jpg" <<'PY'
import sys
from PIL import Image
im = Image.new("RGB", (800, 600))
# Raw pixels: orientation 6 turns the picture 90 degrees clockwise to view, so the raw LEFT half is the upright TOP.
im.paste((40, 90, 220), (0, 0, 400, 600))
im.paste((220, 40, 40), (400, 0, 800, 600))
exif = Image.Exif()
exif[0x0112] = 6
ifd = exif.get_ifd(0x8769)
ifd[0x9003] = "2024:07:04 09:08:07"
ifd[0x9011] = "-06:00"
im.save(sys.argv[1], quality=95, exif=exif)
PY
COLOURS=(DC2828 28B450 285ADC E6C828 A03CC8 F0F0F0 FF8000 00C8C8 804020 202020)
INPUTS=(); FILTER=""
for k in $(seq 0 9); do INPUTS+=(-f lavfi -i "color=c=0x${COLOURS[$k]}:s=640x360:r=25:d=1"); FILTER="$FILTER[$k:v]"; done
[ -f "$OUT/qa-steps.mp4" ] || ffmpeg -loglevel error -y "${INPUTS[@]}" -f lavfi -i "sine=frequency=440:duration=10" \
  -filter_complex "${FILTER}concat=n=10:v=1:a=0[v]" -map "[v]" -map 10:a -c:v libx264 -g 25 -pix_fmt yuv420p -c:a aac -shortest "$OUT/qa-steps.mp4"
head -c 100000 "$OUT/qa-steps.mp4" > "$OUT/qa-truncated.mp4"
ls "$OUT"
