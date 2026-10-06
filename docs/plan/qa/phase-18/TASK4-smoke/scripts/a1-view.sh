#!/usr/bin/env bash
# E6: indexed and unindexed image → Photos' viewer; indexed and unindexed video → the player.
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg a-e6
VIDEO_RING="app.tileshell/.video.VideoDumpService"; RINGS=(launcher "$VIDEO_RING")
adb logcat -c
row_id() { grep -F "_data=$2" "$ROW_DIR/rows-$1-before.txt" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p'; }
IMG=$(row_id images $QF/img-0.png); VID=$(row_id video $QF/qa-steps.mp4)
echo "ids: img-0.png=$IMG qa-steps.mp4=$VID"
assert_ne "precondition: img-0.png has an images row" "" "$IMG"
assert_ne "precondition: qa-steps.mp4 has a video row" "" "$VID"
norow() { q "content query --uri content://media/external/$1/media --projection _data:_display_name" | grep -c "$2"; }
assert_eq "precondition: no images row named qa-hidden.png (content query)" "0" "$(norow images qa-hidden.png)"
assert_eq "precondition: no video row named qa-hidden.mp4 (content query)" "0" "$(norow video qa-hidden.mp4)"

# ---- indexed image
ensure_start; M=$(ring_mark); tap_row $QF img-0.png 3; S 10-viewer; D 10-viewer
assert_eq "indexed image: top activity" "app.tileshell/.photos.ViewerActivity" "$(top_activity)"
SL="$(ring_since $M)"; echo "$SL" | grep -E "\[(files|photosapp)\]" | tee "$ROW_DIR/10-ring.txt"
assert_contains "indexed image: [photosapp] viewer open $IMG (phase 17 writes open at an open; show is its swipe line)" "[photosapp] viewer open $IMG" "$SL"
assert_contains "indexed image: [files] recent add" "[files] recent add $QF/img-0.png" "$SL"
absent_in "indexed image: no 'via provider' line" "via provider" "$SL"
C="$(px "$ROW_DIR/10-viewer.png" 540 1170)"; assert_eq "indexed image: centre pixel $C = 220,40,40 ± 4" "yes" "$(near "$C" 220,40,40 4)"
echo "viewer nodes: $(ids 10-viewer)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; assert_eq "Back from the viewer returns to Files" "$FILES_ACTIVITY" "$(top_activity)"

# ---- unindexed image
M=$(ring_mark); tap_row $QF/hidden qa-hidden.png 3; S 11-viewer-hidden; D 11-viewer-hidden
assert_eq "unindexed image: top activity" "app.tileshell/.photos.ViewerActivity" "$(top_activity)"
SL="$(ring_since $M)"; echo "$SL" | grep -E "\[(files|photosapp)\]" | tee "$ROW_DIR/11-ring.txt"
assert_contains "unindexed image: [files] open … via provider" "[files] open $QF/hidden/qa-hidden.png via provider" "$SL"
assert_contains "unindexed image: [files] recent add" "[files] recent add $QF/hidden/qa-hidden.png" "$SL"
assert_contains "unindexed image: the viewer shows it for the shell" "viewer request from the shell: shown" "$SL"
C="$(px "$ROW_DIR/11-viewer-hidden.png" 540 1170)"; assert_eq "unindexed image: centre pixel $C = 160,60,200 ± 4" "yes" "$(near "$C" 160,60,200 4)"
echo "viewer nodes (hidden): $(ids 11-viewer-hidden)"
assert_eq "unindexed image: the bar offers Share" "yes" "$(H 11-viewer-hidden viewer_share)"
assert_eq "unindexed image: no Edit node" "no" "$(H 11-viewer-hidden viewer_edit)"
assert_eq "unindexed image: no Delete node" "no" "$(H 11-viewer-hidden viewer_delete)"
assert_eq "(the indexed image's bar does offer Edit and Delete, so the absence is the rule's)" "yes yes" "$(H 10-viewer viewer_edit) $(H 10-viewer viewer_delete)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5

# ---- indexed video
M=$(ring_mark); tap_row $QF qa-steps.mp4 4; S 12-player
assert_eq "indexed video: top activity" "app.tileshell/.video.PlayerActivity" "$(top_activity)"
SL="$(ring_since $M)"; VL="$(ring_since $M "$VIDEO_RING")"; { echo "$SL" | grep -E "\[files\]"; echo "$VL" | grep -F "[video]"; } | tee "$ROW_DIR/12-ring.txt"
assert_contains "indexed video: [video] playing $VID (the :video ring)" "[video] playing $VID" "$VL"
assert_contains "indexed video: [files] recent add" "[files] recent add $QF/qa-steps.mp4" "$SL"
session_state | tee "$ROW_DIR/12-session.txt"; adb shell dumpsys media_session > "$ROW_DIR/12-media_session.txt"
assert_contains "indexed video: dumpsys media_session PLAYING" "session.id.video state=PLAYING" "$(cat "$ROW_DIR/12-session.txt")"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; echo "after Back: $(top_activity)"

# ---- unindexed video
M=$(ring_mark); tap_row $QF/hidden qa-hidden.mp4 4; S 13-player-hidden
assert_eq "unindexed video: top activity" "app.tileshell/.video.PlayerActivity" "$(top_activity)"
SL="$(ring_since $M)"; VL="$(ring_since $M "$VIDEO_RING")"; { echo "$SL" | grep -E "\[files\]"; echo "$VL" | grep -F "[video]"; } | tee "$ROW_DIR/13-ring.txt"
assert_contains "unindexed video: [files] open … via provider" "[files] open $QF/hidden/qa-hidden.mp4 via provider" "$SL"
assert_contains "unindexed video: [files] recent add" "[files] recent add $QF/hidden/qa-hidden.mp4" "$SL"
assert_contains "unindexed video: [video] playing scheme=content" "[video] playing scheme=content" "$VL"
session_state | tee "$ROW_DIR/13-session.txt"; adb shell dumpsys media_session > "$ROW_DIR/13-media_session.txt"
assert_contains "unindexed video: dumpsys media_session PLAYING" "session.id.video state=PLAYING" "$(cat "$ROW_DIR/13-session.txt")"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
assert_eq "after: still no images row named qa-hidden.png" "0" "$(norow images qa-hidden.png)"
assert_eq "after: still no video row named qa-hidden.mp4" "0" "$(norow video qa-hidden.mp4)"
c6; ensure_start
leg_end
