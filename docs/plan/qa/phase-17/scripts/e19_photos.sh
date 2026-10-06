#!/usr/bin/env bash
# Phase 17 E19, the Photos part (row E19_PHOTOS) — geometry and motion against R11 (Y1, Y2, Y7, Y11, Y13, Y6).
# Dumps: px ÷ 3 = epx on this AVD (1080 px = 360 epx). Tolerances as the row sets them: ± 1 epx (3 px) for HIGH values,
# ± 2 epx (6 px) for MEDIUM, order and presence only for LOW, colours ± 4 per channel, measured motion bounds ± 33 ms.
#
#   bars (Y7)     on the collection, the albums, the viewer and the editor: no drawn status-bar node (w10m_status_bar;
#                 the control is w10m_nav_bar, which every page draws) and the first chrome row's top at 0.
#   collection    3 columns of 111-epx squares, 2-epx gutters, left 11 / right 12 (HIGH); the pivot titles "Collection" /
#   (Y1)          "Albums" mixed case with no header band (the pixel above and below the titles, and beside them, = page
#                 black); a video tile's disc 36 ± 2 epx.
#   albums (Y1)   tiles 60 epx tall, 162 wide, 12-epx margins and gutter (HIGH).
#   viewer (Y2)   the date header 0 → 50 epx, #171717; the app bar 48 epx, black, Share · Edit · Delete · More from left
#                 to right with no Favorite (LOW: order and presence; the centres from the right are recorded against
#                 photos-pass2's 218 / 150 / 82 / 24); the photo's vertical centre = the screen's centre; the overflow's
#                 items Slideshow / Set as / File information in that order, Slideshow → Set as at a 44-epx pitch
#                 (Change Log 2026-10-05 14:27 (9): File information sits 52.9 epx below Set as — r11/photos.md 1.6.9's
#                 rule line — so the 44-epx pitch is asserted for the first pair only; the second is recorded).
#   editor (Y11)  a 48-epx bottom tool strip with Crop · Enhance · Rotate · Save · More in that order (LOW).
#   trim (Y13)    the command bar at the top (Save a copy · Cancel · More in that order), two handle nodes on the track,
#                 a time-label node at each end of the track and no centred time-readout node (LOW).
#   motion (Y6)   viewer_open / viewer_close settle 250 ± 17 ms and the slideshow step's 250 ± 17 (the approximations,
#                 H4); the photo swipe's fling (the line's settle) <= 234 ms ± 33; every one of these lines has
#                 maxGapMs <= 33.4 (C-31); the 20-epx gap between photos is read from a screenrecord's mid-swipe frame
#                 (LOW: presence — a black run between the two photos' colours; its width is recorded).
#
# Changes on the device: media (media_down), read permissions granted if they were not (restored). No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_photos.sh"
HIGH=3; MEDIUM=6
dim() { bounds "$1" "$2" | awk -v k="$3" '{ if (k=="w") print $3-$1; else if (k=="h") print $4-$2; else if (k=="l") print $1; else if (k=="t") print $2; else if (k=="r") print $3; else if (k=="b") print $4; else if (k=="cx") print ($1+$3)/2; else if (k=="cy") print ($2+$4)/2 }'; }
from_right() { bounds "$1" "$2" | awk '{print (1080 - ($1 + $3) / 2) / 3}'; }
ascending() { python3 -c "import sys; v=[float(x) for x in sys.argv[1:] if x]; print('yes' if len(v)==len(sys.argv)-1 and all(a<b for a,b in zip(v,v[1:])) else 'no: '+' '.join(sys.argv[1:]))" "$@"; }
# No drawn status bar on a page whose dump is readable (the nav bar's node is the control), first chrome row at 0.
bars() { # label dump chrome-node
  assert_eq "$1: (control) the drawn nav bar's node is in the dump" "yes" "$(has_node "$2" w10m_nav_bar)"
  assert_eq "$1: no drawn status-bar node (Y7)" "no" "$(has_node "$2" w10m_status_bar)"
  assert_eq "$1: the first chrome row's top is at 0 ($3)" "0" "$(dim "$2" "$3" t)"
}

photos_row_begin E19_PHOTOS "Photos' geometry and motion against R11 (Y1, Y2, Y6, Y7, Y11, Y13)"
perm_set READ_MEDIA_IMAGES true
perm_set READ_MEDIA_VIDEO true
# shellcheck disable=SC2086
media_up $SIX qa-steps.mp4
six_ids
VID="$(vid_id Movies/ qa-steps.mp4)"; record "fixture video id" "$VID"

# ----------------------------------------------------------------------------------------------- the collection (Y1)
log "--- the collection"
X="$D/collection.xml"
photos_cold "$X"; screencap "$D/collection.png"
bars "collection" "$X" photos_pivots
B0="$(bounds "$X" "photos_item:$ID0")"; B1="$(bounds "$X" "photos_item:$ID1")"; B2="$(bounds "$X" "photos_item:$ID2")"; B3="$(bounds "$X" "photos_item:$ID3")"
record "tiles 0..3 (px)" "[$B0] [$B1] [$B2] [$B3]"
TOPS="$(for i in $ID0 $ID1 $ID2; do dim "$X" "photos_item:$i" t; done | sort -un | grep -c .)"
assert_eq "3 columns: the first three tiles share one top" "1" "$TOPS"
assert_ne "… and the fourth is on the next row" "$(dim "$X" "photos_item:$ID0" t)" "$(dim "$X" "photos_item:$ID3" t)"
for i in $ID0 $ID1 $ID2; do
  assert_within "tile $i: 111 epx wide (333 px ± 1 epx)" 333 "$(dim "$X" "photos_item:$i" w)" $HIGH
  assert_within "tile $i: 111 epx tall" 333 "$(dim "$X" "photos_item:$i" h)" $HIGH
done
assert_within "left margin 11 epx (33 px)" 33 "$(dim "$X" "photos_item:$ID0" l)" $HIGH
assert_within "gutter 1 → 2: 2 epx (6 px)" 6 "$(( $(dim "$X" "photos_item:$ID1" l) - $(dim "$X" "photos_item:$ID0" r) ))" $HIGH
assert_within "gutter 2 → 3: 2 epx" 6 "$(( $(dim "$X" "photos_item:$ID2" l) - $(dim "$X" "photos_item:$ID1" r) ))" $HIGH
assert_within "right margin 12 epx (36 px)" 36 "$(( 1080 - $(dim "$X" "photos_item:$ID2" r) ))" $HIGH
assert_within "row gutter 2 epx" 6 "$(( $(dim "$X" "photos_item:$ID3" t) - $(dim "$X" "photos_item:$ID0" b) ))" $HIGH
assert_eq "the pivot titles, mixed case" "Collection|Albums" "$(node_text "$X" photos_pivot:collection)|$(node_text "$X" photos_pivot:albums)"
PT="$(bounds "$X" photos_pivot:collection)"; PA="$(bounds "$X" photos_pivot:albums)"
record "the titles' boxes (px)" "[$PT] [$PA]"
# shellcheck disable=SC2086
set -- $PT; CXT=$(( (${1:-0} + ${3:-0}) / 2 )); YA=$(( ${2:-10} / 2 )); YB=$(( ${4:-160} + ( $(dim "$X" photos_pivots b) - ${4:-160} ) / 2 ))
assert_rgb "no header band: the pixel above the Collection title is page black" "0,0,0" "$(px "$D/collection.png" "$CXT" "$YA")" 4
assert_rgb "no header band: the pixel below the Collection title is page black" "0,0,0" "$(px "$D/collection.png" "$CXT" "$YB")" 4
# shellcheck disable=SC2086
set -- $PA; CXA=$(( (${1:-0} + ${3:-0}) / 2 ))
assert_rgb "no header band: the pixel above the Albums title" "0,0,0" "$(px "$D/collection.png" "$CXA" "$YA")" 4
assert_rgb "no header band: the pixel below the Albums title" "0,0,0" "$(px "$D/collection.png" "$CXA" "$YB")" 4
assert_rgb "no header band: the row right of the titles" "0,0,0" "$(px "$D/collection.png" 1000 "$(dim "$X" photos_pivot:collection cy | cut -d. -f1)")" 4
scroll_to_node "$D/video-tile.xml" "photos_video_disc:$VID" 20
assert_eq "photos_pivot:collection is selected" "true" "$(selected "$D/video-tile.xml" photos_pivot:collection)"
record "the video tile's disc (px)" "$(bounds "$D/video-tile.xml" "photos_video_disc:$VID")"
assert_within "a video tile's disc: 36 ± 2 epx wide (108 px)" 108 "$(dim "$D/video-tile.xml" "photos_video_disc:$VID" w)" $MEDIUM
assert_within "… and tall" 108 "$(dim "$D/video-tile.xml" "photos_video_disc:$VID" h)" $MEDIUM

# ----------------------------------------------------------------------------------------------- the albums (Y1)
log "--- the albums"
photos_cold "$X.2"
tap_node "$X.2" photos_pivot:albums; sleep 1
A="$D/albums.xml"; dump_ui "$A"; screencap "$D/albums.png"
assert_eq "photos_pivot:albums is selected" "true" "$(selected "$A" photos_pivot:albums)"
bars "albums" "$A" photos_pivots
ALB="$(pnodes "$A" photos_album: | awk -F'\t' '{print $2}' | sort -n -k2,2 -k1,1 | head -2)"
record "the first row's two album tiles (px)" "$(echo $ALB)"
# shellcheck disable=SC2046
set -- $(echo $ALB)
assert_eq "two album tiles share the first row" "${2:-a}" "${6:-b}"
assert_within "album tile: 162 epx wide (486 px)" 486 "$(( ${3:-0} - ${1:-0} ))" $HIGH
assert_within "album tile: 60 epx tall (180 px)" 180 "$(( ${4:-0} - ${2:-0} ))" $HIGH
assert_within "the second tile: 162 epx wide" 486 "$(( ${7:-0} - ${5:-0} ))" $HIGH
assert_within "left margin 12 epx (36 px)" 36 "${1:-}" $HIGH
assert_within "gutter 12 epx" 36 "$(( ${5:-0} - ${3:-0} ))" $HIGH
assert_within "right margin 12 epx" 36 "$(( 1080 - ${7:-0} ))" $HIGH
tap_node "$A" photos_pivot:collection; sleep 1

# ----------------------------------------------------------------------------------------------- the viewer (Y2, Y6)
log "--- the viewer"
dump_ui "$X.3"
assert_eq "photos_pivot:collection is selected" "true" "$(selected "$X.3" photos_pivot:collection)"
MARK="$(ring_mark)"
tap_node "$X.3" "photos_item:$ID0"; sleep 2
SLICE="$(ring_since "$MARK")"
V="$D/viewer.xml"; dump_ui "$V"; screencap "$D/viewer.png"
bars "viewer" "$V" viewer_header
record "viewer_open" "$(motion_line "$SLICE" viewer_open)"
assert_within "motion: viewer_open settle 250 ± 17 ms (Y6 approximation, H4)" 250 "$(motion_field "$SLICE" viewer_open settle)" 17
assert_gap "motion: viewer_open" "$SLICE" viewer_open
assert_eq "the date header spans the width from 0" "0 0 1080" "$(bounds "$V" viewer_header | cut -d' ' -f1-3)"
assert_within "the date header: 0 → 50 epx (150 px)" 150 "$(dim "$V" viewer_header b)" $HIGH
assert_rgb "the date header's fill is #171717" "23,23,23" "$(px "$D/viewer.png" 900 75)" 4
assert_within "the app bar: 48 epx (144 px)" 144 "$(dim "$V" viewer_bar h)" $HIGH
assert_eq "the app bar sits on the nav bar" "$(dim "$V" w10m_nav_bar t)" "$(dim "$V" viewer_bar b)"
BARY="$(dim "$V" viewer_bar cy | cut -d. -f1)"
assert_rgb "the app bar is black" "0,0,0" "$(px "$D/viewer.png" 100 "${BARY:-2120}")" 4
assert_eq "Share · Edit · Delete · More from left to right (LOW: order and presence)" "yes" "$(ascending "$(dim "$V" viewer_share cx)" "$(dim "$V" viewer_edit cx)" "$(dim "$V" viewer_delete cx)" "$(dim "$V" viewer_more cx)")"
record "their centres from the right, epx (photos-pass2: 218 / 150 / 82 / 24)" "$(from_right "$V" viewer_share) $(from_right "$V" viewer_edit) $(from_right "$V" viewer_delete) $(from_right "$V" viewer_more)"
assert_eq "no Favorite: no viewer_favorite node" "no" "$(has_node "$V" viewer_favorite)"
assert_absent "no Favorite: no node of the viewer reads Favorite" "avorite" "$(grep -oE '(text|content-desc)="[^"]*"' "$V" | xargs)"
assert_within "the photo's vertical centre = the screen's centre (1170 px, ± 1 epx)" 1170 "$(dim "$V" viewer_image cy)" $HIGH
tap_node "$V" viewer_more; sleep 1
M="$D/menu.xml"; dump_ui "$M"; screencap "$D/menu.png"
assert_eq "the overflow's items are present" "yes yes yes" "$(has_node "$M" viewer_menu_slideshow) $(has_node "$M" viewer_menu_setas) $(has_node "$M" viewer_menu_info)"
assert_eq "… Slideshow / Set as / File information, top to bottom" "yes" "$(ascending "$(dim "$M" viewer_menu_slideshow cy)" "$(dim "$M" viewer_menu_setas cy)" "$(dim "$M" viewer_menu_info cy)")"
assert_eq "… with those texts" "Slideshow|Set as|File information" "$(node_text "$M" viewer_menu_slideshow_text)|$(node_text "$M" viewer_menu_setas_text)|$(node_text "$M" viewer_menu_info_text)"
PITCH1="$(python3 -c "print($(dim "$M" viewer_menu_setas cy) - $(dim "$M" viewer_menu_slideshow cy))" 2>/dev/null)"
PITCH2="$(python3 -c "print($(dim "$M" viewer_menu_info cy) - $(dim "$M" viewer_menu_setas cy))" 2>/dev/null)"
assert_within "Slideshow → Set as: a 44-epx pitch (132 px, HIGH)" 132 "$PITCH1" $HIGH
record "Set as → File information, px (r11/photos.md 1.6.9's rule line: 52.9 epx = 158.7 px; Change Log (9))" "$PITCH2"
adb shell input keyevent KEYCODE_BACK; sleep 1
# The swipe: the line, its fling bound, and a screenrecord's mid-swipe frame for the gap.
adb shell rm -f /sdcard/Download/qaphotos-swipe.mp4
adb shell screenrecord --time-limit 4 /sdcard/Download/qaphotos-swipe.mp4 > "$D/screenrecord.out" 2>&1 &
REC=$!
sleep 1
MARK="$(ring_mark)"
adb shell input swipe 900 1170 180 1170 600; sleep 1.5
SLICE="$(ring_since "$MARK")"
wait "$REC"
record "photo_swipe" "$(motion_line "$SLICE" photo_swipe)"
assert_contains "motion: [motion] photo_swipe" "[motion] photo_swipe t0=" "$SLICE"
assert_within "motion: the photo swipe's whole fling <= 234 ms (± 33)" 133.5 "$(motion_field "$SLICE" photo_swipe settle)" 133.5
assert_gap "motion: photo_swipe" "$SLICE" photo_swipe
adb pull /sdcard/Download/qaphotos-swipe.mp4 "$D/swipe.mp4" > "$D/pull.out" 2>&1
adb shell rm -f /sdcard/Download/qaphotos-swipe.mp4
GAP="$(python3 - "$D/swipe.mp4" <<'PY'
import subprocess, sys
# One row of pixels through the screen's centre, every frame; a mid-swipe frame shows the outgoing photo (red), a black
# run, then the incoming one (green).
w = 1080
try:
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", sys.argv[1], "-vf", "crop=%d:2:0:1170" % w, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True).stdout
except Exception as e:
    print("unreadable"); sys.exit()
best = None
fs = w * 2 * 3   # two rows a frame (yuv420 cannot be cropped to one); the first is read
for f in range(len(raw) // fs):
    row = raw[f * fs:f * fs + w * 3]
    kinds = []
    for x in range(w):
        r, g, b = row[x * 3:x * 3 + 3]
        kinds.append("R" if r > 150 and g < 100 and b < 100 else "G" if g > 120 and r < 100 and b < 140 else "K" if max(r, g, b) < 45 else "?")
    s = "".join(kinds)
    i = s.rfind("R"); j = s.find("G")
    if i < 0 or j < 0 or j <= i: continue
    mid = s[i + 1:j]
    red, green = s.count("R"), s.count("G")
    # The encoder blurs each edge over a few pixels (neither colour nor black): the run is the black pixels between.
    black = mid.count("K")
    if black >= 20 and black * 2 >= len(mid) and min(red, green) >= 5:
        score = min(red, green)
        if best is None or score > best[0]: best = (score, f, black, len(mid))
print("frame %d: black run %d px (%d px from the last red pixel to the first green one) between the two photos" % best[1:] if best else "none")
PY
)"
record "the gap between photos in the screenrecord's mid-swipe frame (20 epx = 60 px; LOW: presence)" "$GAP"
assert_contains "a mid-swipe frame shows a black gap between the two photos" "black run" "$GAP"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_BACK; sleep 2
SLICE="$(ring_since "$MARK")"
record "viewer_close" "$(motion_line "$SLICE" viewer_close)"
assert_within "motion: viewer_close settle 250 ± 17 ms (Y6 approximation, H4)" 250 "$(motion_field "$SLICE" viewer_close settle)" 17
assert_gap "motion: viewer_close" "$SLICE" viewer_close
# One slideshow step.
dump_ui "$X.4"
assert_eq "photos_pivot:collection is selected" "true" "$(selected "$X.4" photos_pivot:collection)"
viewer_open "$X.4" "$ID1"; viewer_menu
MARK="$(ring_mark)"
tap_node "$D/menu.xml" viewer_menu_slideshow; sleep 6.5
SLICE="$(ring_since "$MARK")"
adb shell input tap 540 600; sleep 1
record "slideshow_step" "$(motion_line "$SLICE" slideshow_step)"
assert_within "motion: the slideshow step's settle 250 ± 17 ms (Y6 approximation, H4)" 250 "$(motion_field "$SLICE" slideshow_step settle)" 17
assert_gap "motion: slideshow_step" "$SLICE" slideshow_step

# ----------------------------------------------------------------------------------------------- the editor (Y11)
log "--- the editor"
rings_save
open_editor "$ID1"; screencap "$D/editor.png"
assert_eq "the editor is up" "yes" "$(has_node "$E" edit_root)"
bars "editor" "$E" edit_root
assert_within "the bottom tool strip: 48 epx (144 px)" 144 "$(dim "$E" edit_bar h)" $HIGH
assert_eq "the strip sits on the nav bar (the bottom of the page)" "$(dim "$E" w10m_nav_bar t)" "$(dim "$E" edit_bar b)"
assert_eq "Crop · Enhance · Rotate · Save · More in that order (LOW)" "yes" "$(ascending "$(dim "$E" edit_crop cx)" "$(dim "$E" edit_enhance cx)" "$(dim "$E" edit_rotate cx)" "$(dim "$E" edit_save cx)" "$(dim "$E" edit_more cx)")"
record "their centres from the right, epx (Y11: 286 / 218 / 150 / 82 / 24)" "$(from_right "$E" edit_crop) $(from_right "$E" edit_enhance) $(from_right "$E" edit_rotate) $(from_right "$E" edit_save) $(from_right "$E" edit_more)"
record "the strip's fill at its left end (Y11: 15,15,15)" "$(px "$D/editor.png" 20 "$(dim "$E" edit_bar cy | cut -d. -f1)")"

# ----------------------------------------------------------------------------------------------- the trim screen (Y13)
log "--- the trim screen"
rings_save
photos_start
scroll_to_node "$D/c.xml" "photos_item:$VID" 20
assert_eq "photos_pivot:collection is selected" "true" "$(selected "$D/c.xml" photos_pivot:collection)"
hold_node "$D/c.xml" "photos_item:$VID"
dump_ui "$D/s.xml"; tap_node "$D/s.xml" edit_sheet_trim; sleep 4
T="$D/trim.xml"; dump_ui "$T"; screencap "$D/trim.png"
assert_eq "the trim screen is up (its track)" "yes" "$(has_node "$T" trim_track)"
assert_eq "the command bar is at the top (its top at 0)" "0" "$(dim "$T" trim_bar t)"
assert_eq "… above the track" "yes" "$(ascending "$(dim "$T" trim_bar b)" "$(dim "$T" trim_track t)")"
assert_eq "Save a copy · Cancel · More in that order (LOW)" "yes" "$(ascending "$(dim "$T" trim_save cx)" "$(dim "$T" trim_cancel cx)" "$(dim "$T" trim_more cx)")"
TL="$(dim "$T" trim_track l)"; TR="$(dim "$T" trim_track r)"; TCY="$(dim "$T" trim_track cy)"
record "the track (px) / the handles / the labels" "[$(bounds "$T" trim_track)] / [$(bounds "$T" trim_handle:start)] [$(bounds "$T" trim_handle:end)] / [$(bounds "$T" trim_time:start)] [$(bounds "$T" trim_time:end)]"
assert_eq "two handle nodes" "2" "$(count_nodes "$T" trim_handle:)"
on_track() { python3 -c "import sys; l,t,r,b=[float(v) for v in sys.argv[1].split()]; tl,tr,cy=[float(v) for v in sys.argv[2:5]]; print('yes' if t <= cy <= b and tl - 60 <= (l+r)/2 <= tr + 60 else 'no')" "$1" "$TL" "$TR" "$TCY" 2>/dev/null; }
assert_eq "the start handle is on the track" "yes" "$(on_track "$(bounds "$T" trim_handle:start)")"
assert_eq "the end handle is on the track" "yes" "$(on_track "$(bounds "$T" trim_handle:end)")"
HALF=$(( (TL + TR) / 2 ))
assert_eq "a time label at the track's start end (left half)" "yes" "$([ "$(dim "$T" trim_time:start cx | cut -d. -f1)" -lt "$HALF" ] 2>/dev/null && echo yes || echo no)"
assert_eq "a time label at the track's other end (right half)" "yes" "$([ "$(dim "$T" trim_time:end cx | cut -d. -f1)" -gt "$HALF" ] 2>/dev/null && echo yes || echo no)"
TIMES="$(python3 - "$T" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
out = []
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    t = (re.search(r'text="([^"]*)"', s) or [None, ""])[1]
    if re.fullmatch(r"\d+:\d\d(:\d\d)?", t):
        rid = (re.search(r'resource-id="([^"]*)"', s) or [None, ""])[1]
        b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
        out.append("%s@%d" % (rid or "?", (int(b.group(1)) + int(b.group(3))) // 2))
print(" ".join(sorted(out)))
PY
)"
record "every node of the trim screen that reads a time (tag@centre x)" "$TIMES"
assert_eq "no centred time-readout node: the only time texts are the two end labels" "2" "$(echo $TIMES | wc -w)"
assert_eq "… and none is centred on the screen (centre x within 480..600 px)" "0" "$(echo $TIMES | tr ' ' '\n' | awk -F@ '$2 >= 480 && $2 <= 600' | grep -c .)"
assert_eq "no trim_readout node" "no" "$(has_node "$T" trim_readout)"

no_crash
c6
media_down
perm_restore
ensure_start
rings_save
row_end
