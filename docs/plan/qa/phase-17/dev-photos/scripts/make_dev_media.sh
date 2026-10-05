#!/usr/bin/env bash
# Photos' development fixtures, made on the host: the six flat-colour PNGs (phase 01's make_photos.py) and a 10-s video
# whose frame is one solid colour per second, with a sine audio track (so a trim has an audio stream to keep).
# usage: make_dev_media.sh <dir>
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$1"; mkdir -p "$OUT"
python3 "$HERE/../../../phase-01/scripts/make_photos.py" "$OUT" >/dev/null
COLOURS=(DC2828 28B450 285ADC E6C828 A03CC8 F0F0F0 FF8000 00C8C8 804020 202020)
INPUTS=(); FILTER=""
for k in $(seq 0 9); do INPUTS+=(-f lavfi -i "color=c=0x${COLOURS[$k]}:s=640x360:r=25:d=1"); FILTER="$FILTER[$k:v]"; done
[ -f "$OUT/qa-steps.mp4" ] || ffmpeg -loglevel error -y "${INPUTS[@]}" -f lavfi -i "sine=frequency=440:duration=10" \
  -filter_complex "${FILTER}concat=n=10:v=1:a=0[v]" -map "[v]" -map 10:a -c:v libx264 -g 25 -pix_fmt yuv420p -c:a aac -shortest "$OUT/qa-steps.mp4"
head -c 100000 "$OUT/qa-steps.mp4" > "$OUT/qa-truncated.mp4"
ls "$OUT"
