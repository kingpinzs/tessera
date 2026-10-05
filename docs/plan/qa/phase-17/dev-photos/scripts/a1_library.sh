#!/usr/bin/env bash
# Photos dev proof A1 (build task 4): the library with pushed fixtures in two folders — counts, order, the 3-column
# grid and its measured geometry, the albums with their counts, the observer removing a row with no restart, the video
# tile's disc and the hand-off to the player. Restores the media it pushed. Not the gate (E3, E19 are).
. "$(dirname "$0")/pdev.sh"
row_begin A1 "Photos library, albums, observer, video hand-off"
media_census
perm_set READ_MEDIA_VIDEO true   # the Videos row: this device's install carries no runtime grant for it
perm_set READ_MEDIA_IMAGES true
media_push /sdcard/DCIM/Camera qa-photo-0.png qa-photo-1.png qa-photo-2.png
media_push /sdcard/Pictures/QA-Album qa-photo-3.png qa-photo-4.png qa-photo-5.png
media_push /sdcard/Movies qa-steps.mp4
scan; sleep 2
IDS=""; for i in 0 1 2; do IDS="$IDS $(img_id DCIM/Camera/ qa-photo-$i.png)"; done; for i in 3 4 5; do IDS="$IDS $(img_id Pictures/QA-Album/ qa-photo-$i.png)"; done; IDS="${IDS# }"
VID="$(vid_id Movies/ qa-steps.mp4)"
record "fixture image ids (qa-photo-0..5)" "$IDS"
record "fixture video id" "$VID"
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
START="$(adb shell am start -W -n app.tileshell/.photos.PhotosActivity | tr -d '\r')"
record "am start -W TotalTime" "$(echo "$START" | sed -n 's/TotalTime: //p')"
sleep 3
assert_eq "PhotosActivity resumed" "app.tileshell/.photos.PhotosActivity" "$(top)"
X="$ROW_DIR/collection.xml"; dump_ui "$X"
assert_contains "the library line" "[photosapp] library: images=$((CENSUS_IMG + 6)) videos=$((CENSUS_VID + 1)) access=GRANTED" "$(ring_since "$MARK" launcher)"
assert_eq "Collection is the selected pivot" "true" "$(pnodes "$X" photos_pivot:collection | cut -f3)"
assert_eq "Albums is not selected" "false" "$(pnodes "$X" photos_pivot:albums | cut -f3)"
assert_eq "pivot titles are mixed case" "Collection Albums" "$(pnodes "$X" photos_pivot: | cut -f4 | xargs)"
assert_eq "no drawn status bar node" "no" "$(has_node "$X" status_bar)"
ORDER="$(pnodes "$X" photos_item: | cut -f1 | sed 's/.*photos_item://' | head -6 | xargs)"
assert_eq "the six fixtures newest first in push order" "$IDS" "$ORDER"
# Geometry (Y1; px / 3 = epx): 3 columns of 111-epx squares, 2-epx gutters, left 11 / right 12.
set -- $IDS
B0="$(bounds "$X" "photos_item:$1")"; B1="$(bounds "$X" "photos_item:$2")"; B2="$(bounds "$X" "photos_item:$3")"; B3="$(bounds "$X" "photos_item:$4")"
record "tile bounds 0..3" "[$B0] [$B1] [$B2] [$B3]"
assert_eq "three tiles share the first row's top" "$(echo $B0 | cut -d' ' -f2) $(echo $B0 | cut -d' ' -f2)" "$(echo $B1 | cut -d' ' -f2) $(echo $B2 | cut -d' ' -f2)"
assert_ne "the fourth tile is on the next row" "$(echo $B0 | cut -d' ' -f2)" "$(echo $B3 | cut -d' ' -f2)"
assert_eq "tile 0 is 111 x 111 epx at left 11" "33 333 333" "$(echo $B0 | awk '{print $1, $3-$1, $4-$2}')"
assert_eq "gutter 2 epx, right margin 12 epx" "6 6 36" "$(echo "$B0 $B1 $B2" | awk '{print $5-$3, $9-$7, 1080-$11}')"
assert_eq "row pitch 113 epx" "339" "$(echo "$B0 $B3" | awk '{print $6-$2}')"
PT="$(bounds "$X" photos_pivot:collection)"; record "Collection title bounds" "$PT"
assert_eq "the fixtures sit under this month's header" "$(date +'%B %Y')" "$(pnodes "$X" photos_month: | cut -f4 | head -1)"
record "month / day texts" "$(pnodes "$X" photos_month: | cut -f4 | head -1) / $(pnodes "$X" photos_day: | cut -f4 | head -1) / $(pnodes "$X" photos_day_count: | cut -f4 | head -1)"
# The video tile and its disc (36 epx).
assert_eq "the video is a tile of the collection" "yes" "$(has_node "$X" "photos_item:$VID")"
DISC="$(bounds "$X" "photos_video_disc:$VID")"
assert_eq "the video tile's disc is 36 epx" "108 108" "$(echo $DISC | awk '{print $3-$1, $4-$2}')"
assert_eq "no player node in Photos" "no" "$(has_node "$X" video_surface)"
screencap "$ROW_DIR/collection.png"

# Albums: Camera and QA-Album with their counts.
tap_node "$X" photos_pivot:albums; sleep 1
A="$ROW_DIR/albums.xml"; dump_ui "$A"
assert_eq "Albums is the selected pivot" "true" "$(pnodes "$A" photos_pivot:albums | cut -f3)"
CAM="$(pnodes "$A" photos_album_name: | awk -F'\t' '$4=="Camera"{print $1}' | sed 's/.*photos_album_name://')"
QAA="$(pnodes "$A" photos_album_name: | awk -F'\t' '$4=="QA-Album"{print $1}' | sed 's/.*photos_album_name://')"
assert_ne "an album named Camera" "" "$CAM"
assert_ne "an album named QA-Album" "" "$QAA"
CAM_BASE="$(adb shell "content query --uri content://media/external/images/media --projection _id --where \"bucket_display_name='Camera'\"" | grep -c '_id=')"
CAMV_BASE="$(adb shell "content query --uri content://media/external/video/media --projection _id --where \"bucket_display_name='Camera'\"" | grep -c '_id=')"
assert_eq "Camera's count is its MediaStore rows" "$((CAM_BASE + CAMV_BASE))" "$(node_text "$A" "photos_album_count:$CAM")"
assert_eq "QA-Album's count" "3" "$(node_text "$A" "photos_album_count:$QAA")"
AB="$(bounds "$A" "photos_album:$QAA")"
assert_eq "an album tile is 162 x 60 epx" "486 180" "$(echo $AB | awk '{print $3-$1, $4-$2}')"
record "album tiles" "$(pnodes "$A" photos_album: | cut -f1,2 | tr '\t' ' ' | xargs)"
screencap "$ROW_DIR/albums.png"
tap_node "$A" photos_pivot:collection; sleep 1

# The observer: a file removed and scanned leaves the page with no restart.
PID="$(adb shell pidof app.tileshell | tr -d '\r')"
MARK2="$(ring_mark)"
adb shell rm /sdcard/Pictures/QA-Album/qa-photo-5.png; scan; sleep 3
dump_ui "$X.2"
set -- $IDS
assert_eq "the removed row left the page" "no" "$(has_node "$X.2" "photos_item:$6")"
assert_eq "its neighbour stayed" "yes" "$(has_node "$X.2" "photos_item:$5")"
assert_contains "the library line after the removal" "[photosapp] library: images=$((CENSUS_IMG + 5)) videos=$((CENSUS_VID + 1)) access=GRANTED" "$(ring_since "$MARK2" launcher)"
assert_eq "no restart (same pid)" "$PID" "$(adb shell pidof app.tileshell | tr -d '\r')"

# The video hand-off: the shell's one player, by explicit component.
MARK3="$(ring_mark)"
tap_node "$X.2" "photos_item:$VID"; sleep 3
assert_eq "the video opens PlayerActivity" "app.tileshell/.video.PlayerActivity" "$(top)"
assert_contains "the hand-off line" "[photosapp] open video $VID -> .video.PlayerActivity" "$(ring_since "$MARK3" launcher)"
adb shell input keyevent KEYCODE_BACK; sleep 2
assert_eq "Back returns to Photos" "app.tileshell/.photos.PhotosActivity" "$(top)"
dump_ui "$X.3"
assert_eq "Photos' page again" "true" "$(pnodes "$X.3" photos_pivot:collection | cut -f3)"
assert_eq "still no player node in Photos" "no" "$(has_node "$X.3" video_surface)"
no_crash 600
ring_save launcher
adb shell am force-stop app.tileshell
media_clean
perm_restore
ensure_start
row_end
