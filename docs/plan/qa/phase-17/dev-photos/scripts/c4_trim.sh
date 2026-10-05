#!/usr/bin/env bash
# Photos dev proof C4 (build task 5, trim): a 10-s fixture trimmed to 2-5 s — a new video row whose file ffprobe reads
# as 3.0 s with one video and one audio stream and whose first frame is second 2's colour, the original untouched; an
# undecodable source leaves no row and writes its line; the trim screen's structure (Y13). No microphone is involved:
# a trim copies the source's own audio. Restores the media. Not the gate (E6b, E19 are).
. "$(dirname "$0")/pdev.sh"
row_begin C4 "Photos video trim"
media_census
perm_set READ_MEDIA_IMAGES true
perm_set READ_MEDIA_VIDEO true
media_push /sdcard/Movies qa-steps.mp4 qa-truncated.mp4
scan; sleep 2
VID="$(vid_id Movies/ qa-steps.mp4)"; BAD="$(vid_id Movies/ qa-truncated.mp4)"
record "fixture video ids (qa-steps, qa-truncated)" "$VID $BAD"
MD5_BEFORE="$(orig_md5 /sdcard/Movies/qa-steps.mp4)"
adb shell am force-stop app.tileshell
BEFORE="$(vid_count)"
photos_start
LPID="$(adb shell pidof app.tileshell | tr -d '\r')"
hold_node() { set -- $(bounds "$1" "$2"); local x=$(( ($1 + $3) / 2 )) y=$(( ($2 + $4) / 2 )); adb shell input swipe "$x" "$y" "$x" "$y" 900; sleep 1; }
open_trim() { # video id
  photos_start
  scroll_to_node "$ROW_DIR/c.xml" "photos_item:$1" 6
  hold_node "$ROW_DIR/c.xml" "photos_item:$1"
  dump_ui "$ROW_DIR/s.xml"; tap_node "$ROW_DIR/s.xml" edit_sheet_trim; sleep 4
  edump
}
open_trim "$VID"
assert_ne ":photosedit runs while the trim screen is open" "" "$(adb shell pidof app.tileshell:photosedit | tr -d '\r')"
screencap "$ROW_DIR/trim.png"
# Y13 (px / 3 = epx; LOW: order and presence).
assert_eq "the command bar is at the top, 48 epx" "0 0 1080 144" "$(bounds "$E" trim_bar)"
cx() { bounds "$E" "$1" | awk '{print (1080 - ($1 + $3) / 2) / 3}'; }
assert_eq "Save a copy · Cancel · More from the right (epx)" "150 82 24" "$(cx trim_save) $(cx trim_cancel) $(cx trim_more)"
assert_eq "two handles on the track" "2" "$(pnodes "$E" trim_handle: | wc -l)"
assert_eq "a time label at each end of the track" "0:00 0:10" "$(node_text "$E" trim_time:start) $(node_text "$E" trim_time:end)"
assert_eq "no centred time readout" "no" "$(has_node "$E" trim_readout)"
assert_eq "a frame of the video shows" "yes" "$(has_node "$E" trim_frame)"
TB="$(bounds "$E" trim_track)"; record "track bounds" "$TB"
assert_eq "the track runs 67 epx in from each side" "201 879" "$(echo $TB | awk '{print $1, $3}')"
assert_eq "the handles are 18.4-epx discs" "55" "$(bounds "$E" trim_handle:start | awk '{print $3-$1}')"
# The handles to 2 s and 5 s.
set -- $TB; TY=$(( ($2 + $4) / 2 ))
adb shell input swipe 201 "$TY" 337 "$TY" 800; sleep 1
adb shell input swipe 879 "$TY" 540 "$TY" 800; sleep 1
edump
assert_eq "the labels read the range's ends" "0:02 0:05" "$(node_text "$E" trim_time:start) $(node_text "$E" trim_time:end)"
EMARK="$(ring_mark)"
etap trim_save 1
for _ in $(seq 1 40); do edump; [ "$(has_node "$E" trim_status)" = yes ] && break; sleep 1; done
ESLICE="$(ring_since "$EMARK" "$EDIT_RING")"
record "the trim's status on screen" "$(node_text "$E" trim_status)"
LINE="$(echo "$ESLICE" | grep -F "[photosapp] trim $VID " | tail -1 | sed 's/.*\[photosapp\] //')"
record "the trim's line" "$LINE"
assert_contains "the trim's line" "[photosapp] trim $VID 2000..5000 -> content://media/" "$ESLICE"
TURI="$(echo "$LINE" | grep -oE 'content://media/[a-z_]+/video/media/[0-9]+')"
TROW="$(adb shell content query --uri "$TURI" --projection _display_name:relative_path:is_pending:mime_type:duration:owner_package_name | tr -d '\r')"
record "the new row" "$TROW"
assert_eq "a new video row" "$((BEFORE + 1))" "$(vid_count)"
assert_contains "published, an MP4, in the original's folder" "relative_path=Movies/, is_pending=0, mime_type=video/mp4" "$TROW"
NAME="$(echo "$TROW" | sed -n 's/.*_display_name=\([^,]*\),.*/\1/p')"
adb pull "/sdcard/Movies/$NAME" "$ROW_DIR/trim.mp4" >/dev/null 2>&1
DUR="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$ROW_DIR/trim.mp4")"
assert_within "ffprobe duration is 3.0 s" 3.0 "$DUR" 0.1
STREAMS="$(ffprobe -v error -show_entries stream=codec_type,codec_name -of csv=p=0 "$ROW_DIR/trim.mp4" | sort | xargs)"
assert_eq "one H.264 video stream and one AAC audio stream" "aac,audio h264,video" "$STREAMS"
ffmpeg -loglevel error -y -i "$ROW_DIR/trim.mp4" -frames:v 1 "$ROW_DIR/trim-first.png"
record "the first frame's centre pixel" "$(px "$ROW_DIR/trim-first.png" 320 180)"
assert_rgb "the first frame is second 2's colour (0x285ADC)" "40,90,220" "$(px "$ROW_DIR/trim-first.png" 320 180)" 12
assert_eq "the original's md5 is unchanged" "$MD5_BEFORE" "$(orig_md5 /sdcard/Movies/qa-steps.mp4)"
assert_eq "the launcher's pid is unchanged" "$LPID" "$(adb shell pidof app.tileshell | tr -d '\r')"
# An undecodable source.
NOW="$(vid_count)"
EMARK="$(ring_mark)"
open_trim "$BAD"
ESLICE="$(ring_since "$EMARK" "$EDIT_RING")"
record "the undecodable source's line" "$(echo "$ESLICE" | grep -F "[photosapp] trim $BAD" | tail -1 | sed 's/.*\[photosapp\] //')"
assert_contains "the failure's line" "[photosapp] trim $BAD failed: " "$ESLICE"
assert_eq "no new row" "$NOW" "$(vid_count)"
assert_contains "the screen says so" "can't be trimmed" "$(node_text "$E" trim_status)"
assert_eq "no pending row is left" "0" "$(adb shell "content query --uri content://media/external/video/media --projection _id --where 'is_pending=1'" | grep -c '_id=')"
no_crash 1500
ring_save launcher; ring_save "$EDIT_RING"
adb shell am force-stop app.tileshell
media_clean
perm_restore
ensure_start
row_end
