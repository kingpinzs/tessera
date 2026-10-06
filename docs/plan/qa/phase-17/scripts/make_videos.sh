#!/usr/bin/env bash
# Phase 17 fixtures (build task 16; the Acceptance "Fixtures" paragraph, r3 V9 / V14): the test videos, made on the
# host with ffmpeg from generated sources. Nothing here records anything: the pictures are lavfi colour sources and the
# sound is a lavfi sine tone, so no microphone and no host audio device is involved.
#
# usage: make_videos.sh <dir>
#
# qa-steps.mp4 is ONE SOLID COLOUR PER SECOND for 10 s: colour k is on screen on [k, k + 1) s, 0-based, with a key
# frame every 25 frames (-g 25, 25 fps), so a frame at k.5 s reads colour k and a seek lands on a second's boundary.
# The ten colours (R,G,B), colour 0 … colour 9 — a row reads its expected pixel from qa-steps.colours beside the file:
set -euo pipefail
OUT="${1:?usage: make_videos.sh <dir>}"
mkdir -p "$OUT"
COLOURS=("200,30,30" "30,160,60" "30,60,200" "220,200,30" "160,40,180" "30,180,190" "240,130,20" "110,110,110" "250,250,250" "20,20,20")
printf '%s\n' "${COLOURS[@]}" > "$OUT/qa-steps.colours"
FF=(ffmpeg -hide_banner -loglevel error -y)

hex() { local IFS=,; read -r r g b <<< "$1"; printf '0x%02X%02X%02X' "$r" "$g" "$b"; }

# The ten one-second colour clips joined in order; `size` and the output's codec options vary per file.
steps() { # out size extra-ffmpeg-args...
  local out="$1" size="$2"; shift 2
  local inputs=() filter="" i
  for i in "${!COLOURS[@]}"; do
    inputs+=(-f lavfi -i "color=c=$(hex "${COLOURS[$i]}"):s=$size:r=25:d=1")
    filter+="[$i:v]"
  done
  filter+="concat=n=${#COLOURS[@]}:v=1:a=0,format=yuv420p[v]"
  "${FF[@]}" "${inputs[@]}" -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=10" \
    -filter_complex "$filter" -map "[v]" -map "${#COLOURS[@]}:a" "$@" "$out"
}

H264=(-c:v libx264 -preset veryfast -g 25 -keyint_min 25 -sc_threshold 0 -color_range tv -c:a aac -b:a 96k -movflags +faststart)

steps "$OUT/qa-steps.mp4" 640x360 "${H264[@]}"
steps "$OUT/qa-steps.webm" 640x360 -c:v libvpx-vp9 -b:v 400k -g 25 -c:a libopus -b:a 64k
steps "$OUT/qa-steps-hevc.mp4" 640x360 -c:v libx265 -preset veryfast -x265-params "keyint=25:min-keyint=25:log-level=error" -tag:v hvc1 -c:a aac -b:a 96k -movflags +faststart
# A 90-degree rotation tag: the stored frames are 640x360 landscape, a player that honours the tag draws portrait.
# (ffmpeg 4.4 writes the tag from the stream metadata key `rotate`; newer builds also take -display_rotation.)
"${FF[@]}" -i "$OUT/qa-steps.mp4" -c copy -metadata:s:v:0 rotate=90 -movflags +faststart "$OUT/qa-rot90.mp4"
# Sound only (no video stream).
"${FF[@]}" -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=10" -c:a aac -b:a 96k "$OUT/qa-audio-only.mp4"
# Undecodable past its first bytes, and empty. The truncated file is cut from a copy WITHOUT faststart, so its index
# (the moov box, at the end) is gone and no player can open it.
"${FF[@]}" -i "$OUT/qa-steps.mp4" -c copy -movflags -faststart "$OUT/.qa-steps-tail-index.mp4"
head -c 100000 "$OUT/.qa-steps-tail-index.mp4" > "$OUT/qa-truncated.mp4"
rm -f "$OUT/.qa-steps-tail-index.mp4"
: > "$OUT/qa-empty.mp4"
# Edge-case fixtures (r3 V14): 4K (above the emulator decoder's capability), two audio tracks, subtitles beside the file.
steps "$OUT/qa-4k.mp4" 3840x2160 -c:v libx264 -preset ultrafast -g 25 -c:a aac -b:a 96k -movflags +faststart
"${FF[@]}" -i "$OUT/qa-steps.mp4" -f lavfi -i "sine=frequency=880:sample_rate=48000:duration=10" \
  -map 0:v -map 0:a -map 1:a -c:v copy -c:a aac -b:a 96k -movflags +faststart "$OUT/qa-two-audio.mp4"
{
  for k in 0 1 2 3 4 5 6 7 8 9; do
    printf '%d\n00:00:%02d,000 --> 00:00:%02d,900\nsecond %d\n\n' "$((k + 1))" "$k" "$k" "$k"
  done
} > "$OUT/qa-steps.srt"
# A source with no sound at all (the edge case "a file with no audio track still takes audio focus").
"${FF[@]}" -i "$OUT/qa-steps.mp4" -an -c:v copy -movflags +faststart "$OUT/qa-silent.mp4"

for f in qa-steps.mp4 qa-steps.webm qa-steps-hevc.mp4 qa-rot90.mp4 qa-audio-only.mp4 qa-4k.mp4 qa-two-audio.mp4 qa-silent.mp4; do
  printf '%-20s %s\n' "$f" "$(ffprobe -v error -show_entries stream=codec_type,codec_name,width,height:format=duration -of compact=p=0:nk=1 "$OUT/$f" | tr '\n' ' ')"
done
printf '%-20s %s bytes\n' qa-truncated.mp4 "$(stat -c%s "$OUT/qa-truncated.mp4")" qa-empty.mp4 "$(stat -c%s "$OUT/qa-empty.mp4")"
