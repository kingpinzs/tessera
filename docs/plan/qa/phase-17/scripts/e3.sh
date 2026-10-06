#!/usr/bin/env bash
# Phase 17 E3 — Photos library and the video hand-off, clause by clause.
#
#   A  collection   media_up of the six images in two folders (DCIM/Camera, Pictures/QA-Album) and the two qa-steps videos
#                   (qa-steps.mp4 and qa-steps.webm — the row's "two `qa-steps` videos"; the doc names no second file, this
#                   is the driver's reading). The six are the first six photos_item:<id> nodes in media_up order under
#                   ONE month header; `[photosapp] library: images=<census + 6> videos=<census + 2> access=GRANTED`; the
#                   grid is 3 columns (three nodes share a row's top ± 1 px, the fourth is on the next row); both videos
#                   are tiles with the video disc (its 36-epx size is E19_PHOTOS's).
#   A2 the whole    the collection scrolled end to end: the union of its photos_item: ids is exactly MediaStore's image
#      list         and video ids (the census's plus the row's own — "lists the census's images plus the six").
#   B  albums       `Camera` and `QA-Album` with counts census + 3 each (the census of each bucket read before media_up).
#   C  observer     `adb shell rm /sdcard/Pictures/QA-Album/qa-photo-5.png` + the scan → the row leaves the page with no
#                   restart (same pid), `[photosapp] library: images=<census + 5> …`. (Change Log 2026-10-05 14:27 (9):
#                   on this image the rm drops the MediaStore row at once.)
#   D  hand-off     Photos' own dump before the tap (photos_pivot:collection asserted first) holds no video_surface; tap
#                   the qa-steps.mp4 tile → topResumedActivity = app.tileshell/.video.PlayerActivity (r3 D6), `[video]
#                   playing <id>` in the :video ring (read before Back), the player's own dump DOES hold video_surface
#                   (the control); Back → Photos' dump again, pivot asserted, no video_surface (T17-7).
#   E  Back on      Home (ensure_start), then Back on Start → the launcher slice holds
#      Start        `[back] … -> app.tileshell/.photos.PhotosActivity` (r3 D6).
#   F  denied       `pm revoke … READ_MEDIA_IMAGES` → the page says it cannot read the pictures, names the Setup
#                   checklist, offers the grant; `[photosapp] access=DENIED`. READING: the doc's next step GRANTS
#                   READ_MEDIA_VISUAL_USER_SELECTED, so its sequence presupposes that permission is not held; Android
#                   grants it together with "Allow all", so where the device holds it the driver revokes it too, says
#                   so in the log, and puts it back at the end.
#   G  partial      `pm grant … READ_MEDIA_VISUAL_USER_SELECTED` with IMAGES still revoked → `[photosapp] library:
#                   images=0 … access=PARTIAL` (r3 V11), the partial state's line with its link; the link → Android's
#                   dialog → "Allow limited access" → the selected-photos picker; qa-photo-1 and qa-photo-2 tapped (the
#                   newest green and the newest blue thumbnail), Allow → `images=2`, and exactly those two photos_item:
#                   nodes. Asserted twice: of the IMAGE tiles (they are exactly the two), and as the doc words it (the
#                   page's photos_item: nodes are exactly the two). READING: READ_MEDIA_VIDEO is revoked for this leg —
#                   held, it makes Android grant the link's request in full with no dialog and no picker (one
#                   permission group; run 1 of this row, kept, shows it), and the state Android's own "Allow limited
#                   access" leaves has both denied. The doc's sequence revokes IMAGES only: the lead rules.
#   restore         READ_MEDIA_IMAGES granted back (asserted), every permission as found, media_down, the baseline.
#
# Changes on the device: the Start layout (baseline, restored), media (media_down), three permissions (restored), the
# selected-photos set of the app (cleared by the full grant). Force-stops the shell. No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_photos.sh"

photos_row_begin E3 "Photos library, albums, the observer, the access states, the video hand-off"
layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "layout_restore of the baseline" "0" "$?"
ensure_start
record "permissions as found (IMAGES / VIDEO / VISUAL_USER_SELECTED)" "$(perm_granted READ_MEDIA_IMAGES) / $(perm_granted READ_MEDIA_VIDEO) / $(perm_granted READ_MEDIA_VISUAL_USER_SELECTED)"
perm_set READ_MEDIA_IMAGES true
perm_set READ_MEDIA_VIDEO true
CAM0="$(bucket_count Camera)"; QAA0="$(bucket_count QA-Album)"
record "bucket census (Camera / QA-Album)" "$CAM0 / $QAA0"
# shellcheck disable=SC2086
media_up $SIX qa-steps.mp4 qa-steps.webm
six_ids
VID="$(vid_id Movies/ qa-steps.mp4)"; VID2="$(vid_id Movies/ qa-steps.webm)"
record "fixture video ids (qa-steps.mp4, qa-steps.webm)" "$VID $VID2"
assert_ne "qa-steps.mp4 has a MediaStore row" "" "$VID"
assert_ne "qa-steps.webm has a MediaStore row" "" "$VID2"

# ----------------------------------------------------------------------------------------------- A: the collection
log "--- A: the collection"
rings_save; adb shell am force-stop app.tileshell; sleep 1
MARK="$(ring_mark)"
photos_start; sleep 2
X="$D/collection.xml"; dump_ui "$X"; screencap "$D/collection.png"
assert_eq "A: PhotosActivity is resumed" "$PHOTOS_ACTIVITY" "$(top_activity)"
assert_eq "A: photos_pivot:collection is selected" "true" "$(selected "$X" photos_pivot:collection)"
SLICE="$(ring_since "$MARK")"
assert_contains "A: the library line: the census plus the six images and the two videos" "[photosapp] library: images=$((CENSUS_IMAGES + 6)) videos=$((CENSUS_VIDEO + 2)) access=GRANTED" "$SLICE"
assert_eq "A: the six fixtures are the first six photos_item: nodes, in media_up order (newest first)" "$IDS" "$(item_ids "$X" | cut -d' ' -f1-6)"
# One month header: the first header is above the first tile, it reads this month, and none sits between the six.
T0="$(bounds "$X" "photos_item:$ID0" | cut -d' ' -f2)"; B5="$(bounds "$X" "photos_item:$ID5" | cut -d' ' -f4)"
MONTHS="$(pnodes "$X" photos_month: | awk -F'\t' '{split($2,b," "); print b[2] "|" $4}')"
record "A: month headers in the dump (top|text)" "$(echo $MONTHS)"
assert_eq "A: the header above the six reads this month" "$(date +'%B %Y')" "$(printf '%s\n' "$MONTHS" | awk -F'|' -v t="${T0:-0}" '$1 < t {print $2}' | tail -1)"
assert_eq "A: no second month header between the first and the sixth fixture" "0" "$(printf '%s\n' "$MONTHS" | awk -F'|' -v t="${T0:-0}" -v b="${B5:-0}" '$1 >= t && $1 <= b' | grep -c .)"
# 3 columns (Y1): three nodes share a row's top ± 1 px.
TOPS="$(for i in $ID0 $ID1 $ID2 $ID3; do bounds "$X" "photos_item:$i" | cut -d' ' -f2; done | xargs)"
record "A: the tops of fixtures 0..3 (px)" "$TOPS"
# shellcheck disable=SC2086
set -- $TOPS
assert_within "A: the second tile shares the first's top (± 1 px)" "${1:-}" "${2:-}" 1
assert_within "A: the third tile shares the first's top (± 1 px)" "${1:-}" "${3:-}" 1
assert_ne "A: the fourth tile is on the next row" "${1:-}" "${4:-}"
LEFTS="$(for i in $ID0 $ID1 $ID2; do bounds "$X" "photos_item:$i" | cut -d' ' -f1; done | sort -un | grep -c .)"
assert_eq "A: … at three different lefts (3 columns)" "3" "$LEFTS"

# ----------------------------------------------------------------------------------------------- A2: the whole list
log "--- A2: the collection end to end"
: > "$D/union.txt"
LAST=-1; SAME=0
for i in $(seq 1 60); do
  dump_ui "$D/scroll.xml"
  item_ids "$D/scroll.xml" | tr ' ' '\n' >> "$D/union.txt"
  N="$(sort -u "$D/union.txt" | grep -c .)"
  if [ "$N" = "$LAST" ]; then SAME=$((SAME + 1)); else SAME=0; fi
  [ "$SAME" -ge 2 ] && break
  LAST="$N"
  [ "$(has_node "$D/scroll.xml" "photos_video_disc:$VID")" = yes ] && cp "$D/scroll.xml" "$D/video-tile.xml"
  [ "$(has_node "$D/scroll.xml" "photos_video_disc:$VID2")" = yes ] && cp "$D/scroll.xml" "$D/video-tile2.xml"
  adb shell input swipe 540 1700 540 900 400; sleep 1
done
note "A2: $i dumps, $(sort -u "$D/union.txt" | grep -c .) distinct ids"
assert_eq "A2: photos_pivot:collection is still the page" "true" "$(selected "$D/scroll.xml" photos_pivot:collection)"
{ media_ids images; media_ids video; } | sort -n > "$D/mediastore-ids.txt"
assert_eq "A2: the collection's ids are MediaStore's images and videos (census + the row's eight)" "$(xargs < "$D/mediastore-ids.txt")" "$(sort -un "$D/union.txt" | xargs)"
assert_eq "A2: … which is the census + 8 rows" "$((CENSUS_IMAGES + CENSUS_VIDEO + 8))" "$(grep -c . "$D/mediastore-ids.txt")"
assert_eq "A: qa-steps.mp4 is a tile of the collection with the video disc" "yes" "$([ -f "$D/video-tile.xml" ] && has_node "$D/video-tile.xml" "photos_item:$VID" || echo no)"
assert_eq "A: qa-steps.webm is a tile of the collection with the video disc" "yes" "$([ -f "$D/video-tile2.xml" ] && has_node "$D/video-tile2.xml" "photos_item:$VID2" || echo no)"
[ -f "$D/video-tile.xml" ] && record "A: qa-steps.mp4's disc (px; its size is E19_PHOTOS's)" "$(bounds "$D/video-tile.xml" "photos_video_disc:$VID")"
assert_eq "A: an image tile carries no video disc" "no" "$(has_node "$X" "photos_video_disc:$ID0")"

# ----------------------------------------------------------------------------------------------- B: albums
log "--- B: the albums pivot"
photos_cold "$X.b"
tap_node "$X.b" photos_pivot:albums; sleep 1
A="$D/albums.xml"; dump_ui "$A"; screencap "$D/albums.png"
assert_eq "B: photos_pivot:albums is selected" "true" "$(selected "$A" photos_pivot:albums)"
album_of() { pnodes "$A" photos_album_name: | awk -F'\t' -v n="$1" '$4==n {print $1}' | sed 's/.*photos_album_name://' | head -1; }
CAM="$(album_of Camera)"; QAA="$(album_of QA-Album)"
if [ -z "$CAM" ] || [ -z "$QAA" ]; then   # below the fold: one swipe, and both dumps are read together
  adb shell input swipe 540 1700 540 900 400; sleep 1
  dump_ui "$D/albums-2.xml"; cat "$D/albums.xml" "$D/albums-2.xml" > "$D/albums-all.xml"; A="$D/albums-all.xml"
  CAM="$(album_of Camera)"; QAA="$(album_of QA-Album)"
fi
record "B: albums on the page (name=count)" "$(pnodes "$A" photos_album_name: | awk -F'\t' '{print $4}' | xargs)"
assert_ne "B: an album named Camera" "" "$CAM"
assert_ne "B: an album named QA-Album" "" "$QAA"
assert_eq "B: Camera's count is its census + 3" "$((CAM0 + 3))" "$(node_text "$A" "photos_album_count:$CAM")"
assert_eq "B: QA-Album's count is its census + 3" "$((QAA0 + 3))" "$(node_text "$A" "photos_album_count:$QAA")"
tap_node "$D/albums.xml" photos_pivot:collection; sleep 1

# ----------------------------------------------------------------------------------------------- C: the observer
log "--- C: rm + the scan → the row leaves with no restart"
dump_ui "$X.c0"
assert_eq "C: photos_pivot:collection is selected" "true" "$(selected "$X.c0" photos_pivot:collection)"
assert_eq "C: qa-photo-5's tile is on the page before the rm" "yes" "$(has_node "$X.c0" "photos_item:$ID5")"
PID="$(adb shell pidof app.tileshell | tr -d '\r')"
MARK="$(ring_mark)"
adb shell rm /sdcard/Pictures/QA-Album/qa-photo-5.png; media_scan; sleep 2
dump_ui "$X.c1"
assert_eq "C: photos_pivot:collection is still the page" "true" "$(selected "$X.c1" photos_pivot:collection)"
assert_eq "C: the row has left the page" "no" "$(has_node "$X.c1" "photos_item:$ID5")"
assert_eq "C: its neighbour stayed" "yes" "$(has_node "$X.c1" "photos_item:$ID4")"
assert_contains "C: the library line after the removal" "[photosapp] library: images=$((CENSUS_IMAGES + 5)) " "$(ring_since "$MARK")"
assert_ne "C: the shell was running" "" "$PID"
assert_eq "C: no restart (the same pid)" "$PID" "$(adb shell pidof app.tileshell | tr -d '\r')"

# ----------------------------------------------------------------------------------------------- D: the hand-off
log "--- D: a video tile opens the shell's one player"
scroll_to_node "$D/pre-tap.xml" "photos_item:$VID" 20
assert_eq "D: before the tap, photos_pivot:collection is selected" "true" "$(selected "$D/pre-tap.xml" photos_pivot:collection)"
assert_eq "D: … and Photos' dump holds no video_surface" "no" "$(has_node "$D/pre-tap.xml" video_surface)"
MARK="$(ring_mark)"
tap_node "$D/pre-tap.xml" "photos_item:$VID"; sleep 3
assert_eq "D: topResumedActivity is the player (r3 D6)" "$PLAYER_ACTIVITY" "$(top_activity)"
VSLICE="$(ring_since "$MARK" "$VIDEO_RING")"
assert_contains "D: [video] playing <id> in the :video ring" "[video] playing $VID" "$VSLICE"
assert_contains "D: Photos' own line of the hand-off (launcher ring)" "[photosapp] open video $VID -> .video.PlayerActivity" "$(ring_since "$MARK")"
gdump "$D/player.xml" || true
assert_eq "D: (control) the player's dump holds video_surface" "yes" "$(has_node "$D/player.xml" video_surface)"
rings_save
adb shell input keyevent KEYCODE_BACK; sleep 2
assert_eq "D: Back returns to Photos" "$PHOTOS_ACTIVITY" "$(top_activity)"
dump_ui "$D/post-back.xml"
assert_eq "D: after Back, photos_pivot:collection is selected" "true" "$(selected "$D/post-back.xml" photos_pivot:collection)"
assert_eq "D: … and Photos' dump still holds no video_surface (one player surface, T17-7)" "no" "$(has_node "$D/post-back.xml" video_surface)"

# ----------------------------------------------------------------------------------------------- E: Back on Start
log "--- E: Home, then Back on Start"
ensure_start
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_BACK; sleep 3
BSLICE="$(ring_since "$MARK")"
BLINE="$(printf '%s\n' "$BSLICE" | grep -F '[back] ' | tail -1)"
record "E: the [back] line" "${BLINE##*\[back\] }"
assert_contains "E: [back] … -> app.tileshell/.photos.PhotosActivity (the player is a helper, not a catalog app)" "-> $PHOTOS_ACTIVITY" "$BLINE"
absent_in "E: no [back] line names the player" "PlayerActivity" "$BSLICE"
record "E: what is resumed after Back on Start" "$(top_activity)"

# ----------------------------------------------------------------------------------------------- F: denied
log "--- F: READ_MEDIA_IMAGES revoked"
rings_save
perm_set READ_MEDIA_VISUAL_USER_SELECTED "$(perm_granted READ_MEDIA_VISUAL_USER_SELECTED)"   # remembered for the restore
adb shell pm revoke app.tileshell android.permission.READ_MEDIA_IMAGES
assert_eq "F: READ_MEDIA_IMAGES is revoked" "false" "$(perm_granted READ_MEDIA_IMAGES)"
if [ "$(perm_granted READ_MEDIA_VISUAL_USER_SELECTED)" = true ]; then
  record "F: READING — the device held READ_MEDIA_VISUAL_USER_SELECTED (Android grants it with Allow all); the doc's next step grants it, so it is revoked here too" "revoked"
  adb shell pm revoke app.tileshell android.permission.READ_MEDIA_VISUAL_USER_SELECTED
else
  record "F: READ_MEDIA_VISUAL_USER_SELECTED after the revoke of IMAGES" "not held (the doc's sequence as written)"
fi
adb shell am force-stop app.tileshell; sleep 1
MARK="$(ring_mark)"
photos_start; sleep 1
Y="$D/denied.xml"; dump_ui "$Y"; screencap "$D/denied.png"
assert_eq "F: Photos' page (photos_pivot:collection selected)" "true" "$(selected "$Y" photos_pivot:collection)"
DTEXT="$(node_text "$Y" photos_denied)"
record "F: the denied text" "$DTEXT"
assert_contains "F: the page says it cannot read the pictures" "can't read the pictures" "$DTEXT"
assert_contains "F: … and names the Setup checklist" "Setup checklist" "$DTEXT"
assert_eq "F: … and offers the grant (photos_grant)" "Allow access" "$(node_text "$Y" photos_grant)"
assert_eq "F: no tile is shown" "0" "$(count_nodes "$Y" photos_item:)"
assert_contains "F: [photosapp] access=DENIED" "[photosapp] access=DENIED" "$(ring_since "$MARK")"

# ----------------------------------------------------------------------------------------------- G: partial
log "--- G: READ_MEDIA_VISUAL_USER_SELECTED granted, IMAGES still revoked"
rings_save
# READING (run 1 of this row is the evidence, kept as E3-run1-…): with READ_MEDIA_VIDEO still held, Android answers the
# link's permission request at once and in full, with no dialog and no picker (the two permissions are one group:
# `[photosapp] permission request: access=GRANTED`). The state Android's own "Allow limited access" leaves is IMAGES and
# VIDEO both denied with VISUAL_USER_SELECTED held, so READ_MEDIA_VIDEO is revoked for this leg and granted back by
# the restore. The doc's sequence revokes IMAGES only; the lead rules on the clause.
record "G: READING — READ_MEDIA_VIDEO revoked for the partial leg (held, it makes Android grant the link's request in full with no picker: run 1)" "$(perm_granted READ_MEDIA_VIDEO) -> false"
perm_set READ_MEDIA_VIDEO false
adb shell pm grant app.tileshell android.permission.READ_MEDIA_VISUAL_USER_SELECTED
assert_eq "G: READ_MEDIA_IMAGES is still revoked" "false" "$(perm_granted READ_MEDIA_IMAGES)"
adb shell am force-stop app.tileshell; sleep 1
MARK="$(ring_mark)"
photos_start; sleep 1
Z="$D/partial.xml"; dump_ui "$Z"; screencap "$D/partial.png"
assert_eq "G: Photos' page" "true" "$(selected "$Z" photos_pivot:collection)"
SLICE="$(ring_since "$MARK")"
LIB="$(printf '%s\n' "$SLICE" | grep -F '[photosapp] library:' | tail -1)"
record "G: the library line" "${LIB##*\[photosapp\] }"
assert_contains "G: library: images=0 …" "[photosapp] library: images=0 " "$LIB"
assert_contains "G: … access=PARTIAL (r3 V11)" "access=PARTIAL" "$LIB"
PTEXT="$(node_text "$Z" photos_partial)"
record "G: the partial state's line" "$PTEXT"
assert_ne "G: the partial state's line is on the page (photos_partial)" "" "$PTEXT"
assert_ne "G: … with its link (photos_grant)" "" "$(node_text "$Z" photos_grant)"
tap_node "$Z" photos_grant; sleep 3
dump_ui "$D/dialog.xml"; screencap "$D/dialog.png"
record "G: what the link opens first" "$(top_activity)"
if [ "$(has_node "$D/dialog.xml" com.android.permissioncontroller:id/permission_allow_selected_button)" = yes ]; then
  note "Android's dialog stands before the picker; its limited-access button is tapped"
  tap_node "$D/dialog.xml" com.android.permissioncontroller:id/permission_allow_selected_button; sleep 4
fi
PICKER_TOP="$(top_activity)"
record "G: the picker's activity" "$PICKER_TOP"
dump_ui "$D/picker.xml"; screencap "$D/picker.png"
PICKER_PKG="$(grep -oE 'package="[^"]*"' "$D/picker.xml" | sort | uniq -c | sort -rn | head -1 | sed 's/.*package="//;s/"//')"
record "G: the picker's package" "$PICKER_PKG"
assert_eq "G: the link opens Android's selected-photos picker (a photo picker of the system's, over Photos)" "yes" \
  "$(case "$PICKER_TOP $PICKER_PKG" in *hotopicker*|*hotoPicker*|*providers.media*) echo yes ;; *) echo "no: $PICKER_TOP / $PICKER_PKG" ;; esac)"
# The picker's thumbnails in reading order with each one's centre colour; qa-photo-1 is the newest green one and
# qa-photo-2 the newest blue one (the census's older copies of the same colours come after them).
python3 - "$D/picker.xml" "$D/picker.png" > "$D/picker-cells.txt" <<'PY'
import re, sys
from PIL import Image
im = Image.open(sys.argv[2]).convert("RGB")
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
cells = []
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    rid = (re.search(r'resource-id="([^"]*)"', s) or [None, ""])[1]
    desc = (re.search(r'content-desc="([^"]*)"', s) or [None, ""])[1]
    if not (rid.endswith("icon_thumbnail") or re.search(r"(?i)(photo|image|media|picture).*(taken|from)", desc) or re.search(r"(?i)^(photo|image)\b", desc)):
        continue
    b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
    l, t, r, bt = [int(v) for v in b.groups()]
    if r - l < 120 or bt - t < 120 or bt > im.size[1]:
        continue
    cells.append((t, l, r, bt, desc))
for t, l, r, bt, desc in sorted(set(cells)):
    cx, cy = (l + r) // 2, (t + bt) // 2
    print("%d %d %d,%d,%d %s" % (cx, cy, *im.getpixel((cx, cy)), desc))
PY
note "the picker's cells (x y r,g,b description):"; sed 's/^/        /' "$D/picker-cells.txt" >> "$LOG"
cell_of() { python3 - "$D/picker-cells.txt" "$1" <<'PY'
import sys
want = [int(v) for v in sys.argv[2].split(",")]
for line in open(sys.argv[1]):
    p = line.split()
    if len(p) >= 3 and all(abs(int(a) - b) <= 12 for a, b in zip(p[2].split(","), want)):
        print(p[0], p[1]); break
PY
}
C1="$(cell_of 40,180,80)"; C2="$(cell_of 40,90,220)"
assert_ne "G: the picker shows a green thumbnail (qa-photo-1)" "" "$C1"
assert_ne "G: the picker shows a blue thumbnail (qa-photo-2)" "" "$C2"
# shellcheck disable=SC2086
[ -n "$C1" ] && { adb shell input tap $C1; sleep 1; }
# shellcheck disable=SC2086
[ -n "$C2" ] && { adb shell input tap $C2; sleep 1; }
dump_ui "$D/picker-picked.xml"; screencap "$D/picker-picked.png"
ALLOW="$(python3 - "$D/picker-picked.xml" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
best = ""
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    t = html.unescape((re.search(r'text="([^"]*)"', s) or [None, ""])[1])
    rid = (re.search(r'resource-id="([^"]*)"', s) or [None, ""])[1]
    if re.match(r"(?i)^allow\b", t) or rid.endswith(":id/button_add"):
        b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
        best = " ".join(b.groups()) + "|" + t
print(best)
PY
)"
record "G: the picker's Allow button (bounds|text)" "$ALLOW"
assert_ne "G: the picker offers Allow" "" "$ALLOW"
MARK="$(ring_mark)"
tap_bounds "${ALLOW%%|*}"; sleep 4
assert_eq "G: after Allow, Photos is resumed" "$PHOTOS_ACTIVITY" "$(top_activity)"
dump_ui "$D/selected.xml"; screencap "$D/selected.png"
assert_eq "G: Photos' page (photos_pivot:collection selected)" "true" "$(selected "$D/selected.xml" photos_pivot:collection)"
SLICE="$(ring_since "$MARK")"
LIB="$(printf '%s\n' "$SLICE" | grep -F '[photosapp] library:' | tail -1)"
record "G: the library line after Allow" "${LIB##*\[photosapp\] }"
assert_contains "G: images=2" "[photosapp] library: images=2 " "$LIB"
SHOWN="$(item_ids "$D/selected.xml")"
record "G: the page's photos_item: ids" "$SHOWN"
IMG_SHOWN="$(for i in $SHOWN; do grep -qx "$i" "$D/census-video.txt" || [ "$i" = "$VID" ] || [ "$i" = "$VID2" ] || echo "$i"; done | sort -n | xargs)"
assert_eq "G: the IMAGE tiles are exactly qa-photo-1 and qa-photo-2 (DCIM/Camera)" "$(printf '%s\n' "$ID1" "$ID2" | sort -n | xargs)" "$IMG_SHOWN"
assert_eq "G: as the doc words it — exactly those two photos_item: nodes on the page" "$(printf '%s\n' "$ID1" "$ID2" | sort -n | xargs)" "$(echo "$SHOWN" | tr ' ' '\n' | sort -n | xargs)"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
rings_save
adb shell pm grant app.tileshell android.permission.READ_MEDIA_IMAGES
assert_eq "restore: pm grant … READ_MEDIA_IMAGES" "true" "$(perm_granted READ_MEDIA_IMAGES)"
adb shell am force-stop app.tileshell; sleep 1
MARK="$(ring_mark)"
photos_start; sleep 1
assert_contains "restore: Photos reads the whole library again" "access=GRANTED" "$(ring_since "$MARK" | grep -F '[photosapp] library:' | tail -1)"
no_crash
c6
media_down
perm_restore
layout_restore "$BASELINE" > "$D/restore1.out" 2>&1; assert_eq "restore: layout_restore of the baseline" "0" "$?"
ensure_start
rings_save
row_end
