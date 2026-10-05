#!/usr/bin/env bash
# Phase 17 E14 — odd files. The doc's clauses → this driver's legs:
#
#   vp9, hevc   qa-steps.webm and qa-steps-hevc.mp4 play (pixel rule as E11) — both sub-rows are RECORDED against the
#               AVD's decoder list read from /vendor/etc/media_codecs.xml at run time (C-26's record; a decoder the AVD
#               lacks is a recorded fact, not a failure)
#   rot90       qa-rot90.mp4 draws portrait: the coloured area of the screencap is taller than wide (gated)
#   audio-only  qa-audio-only.mp4 plays with a black frame and takes audio focus (gated)
#   truncated,  qa-truncated.mp4 and qa-empty.mp4 show the error state ("can't play this file") with the player still
#   empty       resumed, and `adb logcat -d -T '<MARK as s.mmm>' -s AndroidRuntime` is empty of app.tileshell (gated)
#
# How a file is opened: from its My videos tile (the shell's own launch); a file MediaStore lists as a video but the
# page has no tile for (the 0-byte file; the sound-only file, which MediaStore lists as AUDIO) is opened by a VIEW from
# the reader fixture app (testapps/qa-view holding READ_MEDIA_VIDEO / _AUDIO, its identity shared) — the shell uid's
# own `am start` of a content item is refused since the trust fixes (C-M4). Each leg records which way it was opened.
# Restores: the six files (media_down), the reader app uninstalled, the device's media volume.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"

video_row_begin E14 "odd files: vp9 and hevc (recorded), a rotated file, sound only, a truncated and an empty file"
quiet_on
videos_grant
reader_up
media_up qa-steps.webm qa-steps-hevc.mp4 qa-rot90.mp4 qa-audio-only.mp4 qa-truncated.mp4 qa-empty.mp4
q "content query --uri content://media/external/video/media --projection _id:_display_name:duration:relative_path" > "$D/video-rows.txt"
q "content query --uri content://media/external/audio/media --projection _id:_display_name:relative_path" > "$D/audio-rows.txt"
adb shell am force-stop app.tileshell; sleep 1; ensure_start
leave() { rings_save; adb shell input keyevent KEYCODE_BACK; sleep 1.2; }
# The MediaStore id of a pushed file: the video table's, else the audio table's (prefixed "audio:").
id_of() {
  local id; id="$(media_id video "$1" Movies/)"
  if [ -z "$id" ]; then id="$(grep -F "_display_name=$1, relative_path=Movies/" "$D/audio-rows.txt" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | tail -1)"; [ -n "$id" ] && id="audio:$id"; fi
  echo "$id"
}

# ------------------------------------------------------------------------------------------------ vp9, hevc (RECORDED)
log "--- vp9 and hevc: RECORDED against the AVD's decoder list"
adb shell cat /vendor/etc/media_codecs.xml 2>/dev/null | tr -d '\r' > "$D/media_codecs.xml"
for inc in $(grep -o 'Include href="[^"]*"' "$D/media_codecs.xml" | sed 's/.*href="//;s/"//'); do adb shell cat "/vendor/etc/$inc" 2>/dev/null | tr -d '\r' >> "$D/media_codecs.xml"; done
decoders() { python3 - "$D/media_codecs.xml" "$1" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
dec = " ".join(re.findall(r"<Decoders>(.*?)</Decoders>", x, re.S))
names = sorted(set(re.findall(r'<MediaCodec name="([^"]+)" type="%s"' % re.escape(sys.argv[2]), dec)))
print(" ".join(names) if names else "none")
PY
}
for spec in "qa-steps.webm|video/x-vnd.on2.vp9|vp9" "qa-steps-hevc.mp4|video/hevc|hevc"; do
  IFS='|' read -r name mime word <<<"$spec"
  record "$word: the AVD's decoders for $mime (/vendor/etc/media_codecs.xml at run time)" "$(decoders "$mime")"
  ID="$(id_of "$name")"; record "$word: $name in MediaStore" "${ID:-absent}"
  MARK="$(ring_mark)"
  open_video "$ID" "$D/$word"
  LINE="$(await_vline "$MARK" "[video] playing $ID" 100)"
  record "$word: opened by" "$OPENED_BY"
  record "$word: the playing line" "$(echo "$LINE" | sed 's/.*\[video\]/[video]/')"
  if [ -n "$LINE" ]; then
    shot_at "$(wall_of "$LINE")" 3500 "$D/$word-3.5s.png"
    record_pixel "$word at 3.5 s" 3 "$SHOT_RGB"
  else
    record "$word: it did not play; the :video lines" "$(vring "$MARK" | grep -F '[video]' | sed 's/.*\[video\]/[video]/' | tr '\n' '|')"
  fi
  record "$word: the tracks line" "$(vline "$MARK" "[video] tracks:" | sed 's/.*\[video\] //')"
  leave
done

# ------------------------------------------------------------------------------------------------ rot90
log "--- qa-rot90.mp4 draws portrait"
ID="$(id_of qa-rot90.mp4)"; assert_ne "qa-rot90.mp4 is in MediaStore" "" "$ID"
MARK="$(ring_mark)"
open_video "$ID" "$D/rot"; record "rot90: opened by" "$OPENED_BY"
LINE="$(await_vline "$MARK" "[video] playing $ID" 100)"
assert_contains "rot90: it plays" "[video] playing $ID" "$LINE"
shot_at "$(wall_of "$LINE")" 4500 "$D/rot-4.5s.png"      # 4.5 s: colour 4, with the controls faded (≈3.6 s)
AREA="$(python3 - "$D/rot-4.5s.png" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB")
w, h = im.size
px = im.load()
# The coloured area: pixels that are neither black nor the nav bar's (the bottom 48 epx = 144 px are left out).
xs = [x for x in range(0, w, 3) for y in range(0, h - 144, 3) if max(px[x, y]) > 90]
ys = [y for x in range(0, w, 3) for y in range(0, h - 144, 3) if max(px[x, y]) > 90]
print("%d %d" % ((max(xs) - min(xs) + 3, max(ys) - min(ys) + 3) if xs else (0, 0)))
PY
)"
record "rot90: the coloured area of the screencap (px: width height)" "$AREA"
assert_eq "rot90: the coloured area is taller than wide" "yes" "$(set -- $AREA; [ "${2:-0}" -gt "${1:-0}" ] && [ "${1:-0}" -gt 0 ] && echo yes || echo no)"
leave

# ------------------------------------------------------------------------------------------------ audio only
log "--- qa-audio-only.mp4: a black frame, and audio focus"
ID="$(id_of qa-audio-only.mp4)"; assert_ne "qa-audio-only.mp4 is in MediaStore (video or audio table)" "" "$ID"
record "audio-only: its MediaStore row" "$ID"
MARK="$(ring_mark)"
case "$ID" in
  audio:*) OPENED_BY="a VIEW of its AUDIO row from the reader app, its identity shared (MediaStore lists the file as audio, so My videos has no tile)"
           qaview --es uri "content://media/external/audio/media/${ID#audio:}" --ez share true ;;
  *) open_video "$ID" "$D/audio" ;;
esac
record "audio-only: opened by" "$OPENED_BY"
LINE="$(await_vline "$MARK" "[video] playing scheme=content" 100)"
assert_contains "audio-only: it plays ([video] playing scheme=content)" "[video] playing scheme=content" "$LINE"
sleep 4.2                                                 # the controls have faded: the frame alone
screencap "$D/audio-only.png"
BLACK="$(python3 - "$D/audio-only.png" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); w, h = im.size
print(max(max(im.getpixel((x, y))) for x in range(30, w - 30, 30) for y in range(30, h - 180, 30)))
PY
)"
assert_within "audio-only: the frame is black (the brightest channel of a grid over the page)" 0 "$BLACK" 4
assert_eq "audio-only: the player is on top" "$PLAYER_ACTIVITY" "$(top_activity)"
adb shell dumpsys audio | tr -d '\r' > "$D/audio-focus.txt"
FOCUS="$(sed -n '/Audio Focus stack entries/,/^$/p' "$D/audio-focus.txt")"
assert_contains "audio-only: it takes audio focus (the focus stack names app.tileshell)" "pack: app.tileshell" "$FOCUS"
assert_eq "audio-only: its session is PLAYING" "PLAYING" "$(session_state .id.video)"
record "audio-only: the tracks line" "$(vline "$MARK" "[video] tracks:" | sed 's/.*\[video\] //')"
leave

# ------------------------------------------------------------------------------------------------ truncated, empty
for name in qa-truncated.mp4 qa-empty.mp4; do
  log "--- $name: the error state"
  ID="$(id_of "$name")"; assert_ne "$name is in MediaStore" "" "$ID"
  MARK="$(ring_mark)"
  open_video "$ID" "$D/$name"; record "$name: opened by" "$OPENED_BY"
  LINE="$(await_vline "$MARK" "[video] cannot decode" 150)"
  assert_contains "$name: [video] cannot decode …" "[video] cannot decode" "$LINE"
  record "$name: the line" "$(echo "$LINE" | sed 's/.*\[video\]/[video]/')"
  gdump "$D/$name.xml"
  assert_eq "$name: the page's text" "can't play this file" "$(node_text "$D/$name.xml" player_error)"
  assert_eq "$name: the player is still resumed" "$PLAYER_ACTIVITY" "$(top_activity)"
  assert_eq "$name: logcat -T <MARK> -s AndroidRuntime is empty of app.tileshell" "" "$(crash_since "$MARK")"
  leave
done
record "the control for the AndroidRuntime read: logcat -T <the row's MARK> answers (lines of any tag since it)" "$(adb logcat -d -T "$(( ROW_MARK / 1000 )).000" 2>/dev/null | wc -l)"

log "--- restore"
adb shell am force-stop app.tileshell
media_down
reader_down
videos_grant_restore
quiet_off
c6; ensure_start
row_end
