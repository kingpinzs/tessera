#!/usr/bin/env bash
# Phase 17, Movies & TV development fixtures (the gate's own are the lead's make_videos.sh): a 10-s video that is one
# flat colour per second — colour k on [k, k + 1) s, 0-based, h264, a keyframe every 25 frames (this AVD draws
# it up to 16 per channel off the host's decode, tagged BT.709 or not: the scripts allow 20) — with a truncated copy,
# an empty file, a copy with a subtitle beside it and a rotated copy. Written to a scratch folder, never the repo.
set -euo pipefail
OUT="${1:?usage: make_fixtures.sh <out dir>}"
mkdir -p "$OUT"
# colour k, as ffmpeg takes it and as colours.txt lists it ("k r g b")
COLOURS=(c81e1e 1ec81e 1e1ec8 c8c81e c81ec8 1ec8c8 f0f0f0 ff8000 8000ff 5a5a5a)
: > "$OUT/colours.txt"
inputs=(); filter=""
for k in "${!COLOURS[@]}"; do
  c="${COLOURS[$k]}"
  echo "$k $((16#${c:0:2})) $((16#${c:2:2})) $((16#${c:4:2}))" >> "$OUT/colours.txt"
  inputs+=(-f lavfi -i "color=c=0x$c:s=640x360:r=25:d=1")
  filter+="[$k:v]"
done
filter+="concat=n=${#COLOURS[@]}:v=1:a=0[v]"
ffmpeg -v error -y "${inputs[@]}" -f lavfi -i "anullsrc=r=44100:cl=stereo" -filter_complex "$filter" -map "[v]" -map "${#COLOURS[@]}:a" -pix_fmt yuv420p \
  -t 10 -c:v libx264 -g 25 -c:a aac -b:a 64k -movflags +faststart "$OUT/qa-steps.mp4"
# The file is smaller than the doc's 100,000-byte cut, and faststart puts the index first: the cut is inside the index.
python3 - "$OUT/qa-steps.mp4" "$OUT/qa-truncated.mp4" <<'PY'
import sys
data = open(sys.argv[1], "rb").read()
open(sys.argv[2], "wb").write(data[:2000])
PY
: > "$OUT/qa-empty.mp4"
cp "$OUT/qa-steps.mp4" "$OUT/qa-subs.mp4"
printf '1\n00:00:00,500 --> 00:00:09,500\nQA subtitle line\n' > "$OUT/qa-subs.srt"
ffmpeg -v error -y -i "$OUT/qa-steps.mp4" -c copy -metadata:s:v:0 rotate=90 "$OUT/qa-rot90.mp4"
ls -l "$OUT"
