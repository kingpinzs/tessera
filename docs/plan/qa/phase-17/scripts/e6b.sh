#!/usr/bin/env bash
# Phase 17 E6b — video trim (Q1 C), clause by clause. No microphone: a trim copies the source's own (generated) sound.
#
#   trim       media_up qa-steps.mp4 qa-truncated.mp4. The trim screen is reached by a HOLD on the video's tile → the
#              Edit sheet → Trim (Change Log 2026-10-05 14:27 (10)). The handles set to 2 s and 5 s, Save a copy → a new
#              video row (count + 1, published); `adb pull` it: `ffprobe -show_format` duration 3.0 ± 0.1 s; the first
#              frame (`ffmpeg -frames:v 1`) = colour 2 of gen/media/qa-steps.colours (0-based, colour k on [k, k + 1) s
#              — r3 V9) ± 8 per channel (E11's pixel rule); one video and one audio stream; the original's md5
#              unchanged; `[photosapp] trim <id> 2000..5000 -> <uri>` in the :photosedit ring (read with the trim
#              screen still up — it stays up after a save).
#   truncated  trimming qa-truncated.mp4 (no index: undecodable) → no new row and `[photosapp] trim <id> failed: <why>`
#              (T17-23); no pending row left.
#   restore    media_down, the permissions as found.
#
# Changes on the device: media only (restored), two read permissions granted if they were not (restored).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_photos.sh"

photos_row_begin E6b "video trim: 2–5 s of qa-steps.mp4; the undecodable source"
perm_set READ_MEDIA_IMAGES true
perm_set READ_MEDIA_VIDEO true
media_up qa-steps.mp4 qa-truncated.mp4
VID="$(vid_id Movies/ qa-steps.mp4)"; BAD="$(vid_id Movies/ qa-truncated.mp4)"
record "fixture video ids (qa-steps.mp4, qa-truncated.mp4)" "$VID $BAD"
assert_ne "qa-steps.mp4 has a video row" "" "$VID"
assert_ne "qa-truncated.mp4 has a video row" "" "$BAD"
MD5_BEFORE="$(dev_md5 /sdcard/Movies/qa-steps.mp4)"
assert_eq "the pushed qa-steps.mp4 is the host's file" "$(md5sum "$GEN/qa-steps.mp4" | cut -d' ' -f1)" "$MD5_BEFORE"
COLOUR2="$(sed -n 3p "$GEN/qa-steps.colours")"
record "colour 2 of qa-steps.colours (on screen on [2, 3) s)" "$COLOUR2"
rings_save; adb shell am force-stop app.tileshell; sleep 1
open_trim() { # video id: the collection scrolled to the tile, the tile held, the Edit sheet's Trim
  photos_start
  scroll_to_node "$D/c.xml" "photos_item:$1" 12
  assert_eq "photos_pivot:collection is selected" "true" "$(selected "$D/c.xml" photos_pivot:collection)"
  hold_node "$D/c.xml" "photos_item:$1"
  dump_ui "$D/s.xml"
  assert_eq "a hold on the video tile raises the Edit sheet with Trim" "yes" "$(has_node "$D/s.xml" edit_sheet_trim)"
  tap_node "$D/s.xml" edit_sheet_trim; sleep 4
  edump
}

log "--- trim qa-steps.mp4 to 2–5 s"
COUNT_BEFORE="$(media_count video)"
open_trim "$VID"
screencap "$D/trim.png"
assert_eq "the trim screen is up (its track)" "yes" "$(has_node "$E" trim_track)"
assert_ne ":photosedit runs while the trim screen is open (r3 D8)" "" "$(adb shell pidof app.tileshell:photosedit | tr -d '\r')"
TB="$(bounds "$E" trim_track)"; record "the track (px)" "$TB"
assert_eq "the labels read the whole clip before the drags" "0:00 0:10" "$(node_text "$E" trim_time:start) $(node_text "$E" trim_time:end)"
# shellcheck disable=SC2086
set -- $TB; TL="${1:-0}"; TR="${3:-0}"; TY=$(( (${2:-0} + ${4:-0}) / 2 ))
X2=$(( TL + (TR - TL) * 2 / 10 )); X5=$(( TL + (TR - TL) * 5 / 10 ))
adb shell input swipe "$TL" "$TY" "$X2" "$TY" 800; sleep 1
adb shell input swipe "$TR" "$TY" "$X5" "$TY" 800; sleep 1
edump; screencap "$D/trim-set.png"
assert_eq "the labels read the range's ends" "0:02 0:05" "$(node_text "$E" trim_time:start) $(node_text "$E" trim_time:end)"
EMARK="$(ring_mark)"
etap trim_save 0
TLINE=""
for _ in $(seq 1 160); do
  ESLICE="$(ring_since "$EMARK" "$EDIT_RING")"
  TLINE="$(printf '%s\n' "$ESLICE" | grep -E "\[photosapp\] trim $VID ([0-9]+\.\.[0-9]+ ->|failed:)" | tail -1)"
  [ -n "$TLINE" ] && break
  sleep 0.25
done
sleep 1; edump
record "the trim's line" "${TLINE##*\[photosapp\] }"
record "the trim screen's status" "$(node_text "$E" trim_status)"
assert_contains "[photosapp] trim <id> 2000..5000 -> <uri> (:photosedit ring)" "[photosapp] trim $VID 2000..5000 -> content://media/" "$TLINE"
TURI="$(printf '%s\n' "$TLINE" | grep -oE 'content://media/[a-z_]+/video/media/[0-9]+')"
TROW=""; [ -n "$TURI" ] && TROW="$(q "content query --uri $TURI --projection _id:_display_name:relative_path:is_pending:mime_type:duration:owner_package_name")"
record "the new row" "$TROW"
assert_ne "a new video row" "" "$TROW"
assert_eq "the video count is + 1" "$((COUNT_BEFORE + 1))" "$(media_count video)"
assert_ne "… a NEW row, not the original's" "$VID" "$(row_field "$TROW" _id)"
assert_eq "the new row is published (is_pending 0)" "0" "$(row_field "$TROW" is_pending)"
NAME="$(row_field "$TROW" _display_name)"; REL="$(row_field "$TROW" relative_path)"
adb pull "/sdcard/$REL$NAME" "$D/trim.mp4" > "$D/pull.out" 2>&1; assert_eq "adb pull of the trimmed file" "0" "$?"
ffprobe -v error -show_format -show_streams "$D/trim.mp4" > "$D/ffprobe.txt" 2>&1
DUR="$(sed -n 's/^duration=//p' "$D/ffprobe.txt" | tail -1)"
assert_within "ffprobe -show_format duration = 3.0 ± 0.1 s" 3.0 "$DUR" 0.1
STREAMS="$(ffprobe -v error -show_entries stream=codec_type,codec_name -of csv=p=0 "$D/trim.mp4" 2>/dev/null | sort | xargs)"
record "the streams (codec,type)" "$STREAMS"
assert_eq "one video stream" "1" "$(grep -c '^codec_type=video' "$D/ffprobe.txt")"
assert_eq "one audio stream" "1" "$(grep -c '^codec_type=audio' "$D/ffprobe.txt")"
ffmpeg -loglevel error -y -i "$D/trim.mp4" -frames:v 1 "$D/trim-first.png" > "$D/ffmpeg.out" 2>&1
FIRST="$(fpx "$D/trim-first.png" 320 180)"
record "the first frame's centre pixel" "$FIRST"
assert_rgb "the first frame = colour 2 ± 8 per channel" "$COLOUR2" "$FIRST" 8
assert_eq "the original's md5 is unchanged" "$MD5_BEFORE" "$(dev_md5 /sdcard/Movies/qa-steps.mp4)"
assert_eq "the original's row is still there (same id)" "$VID" "$(vid_id Movies/ qa-steps.mp4)"

log "--- trimming qa-truncated.mp4"
rings_save
COUNT_NOW="$(media_count video)"
EMARK="$(ring_mark)"
open_trim "$BAD"
if [ "$(has_node "$E" trim_save)" = yes ] && [ -z "$(ring_since "$EMARK" "$EDIT_RING" | grep -F "[photosapp] trim $BAD failed: ")" ]; then
  note "the trim screen opened on the truncated file with no failure yet: Save a copy is tapped"
  etap trim_save 0
fi
BLINE=""
for _ in $(seq 1 80); do
  ESLICE="$(ring_since "$EMARK" "$EDIT_RING")"
  BLINE="$(printf '%s\n' "$ESLICE" | grep -F "[photosapp] trim $BAD " | grep -E 'failed:| -> ' | tail -1)"
  [ -n "$BLINE" ] && break
  sleep 0.25
done
edump; screencap "$D/trim-truncated.png"
record "the truncated source's line" "${BLINE##*\[photosapp\] }"
record "the trim screen's status" "$(node_text "$E" trim_status)"
assert_contains "[photosapp] trim <id> failed: <why> (T17-23)" "[photosapp] trim $BAD failed: " "$BLINE"
assert_ne "… with a reason after the colon" "" "$(printf '%s\n' "$BLINE" | sed -n 's/.* failed: *//p')"
absent_in "no success line for the truncated file" "[photosapp] trim $BAD 0" "$ESLICE"
assert_eq "no new row" "$COUNT_NOW" "$(media_count video)"
assert_eq "no pending video row is left" "0" "$(pending_count video)"

no_crash
c6
media_down
perm_restore
ensure_start
rings_save
row_end
