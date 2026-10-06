#!/usr/bin/env bash
# Phase 17 E4 — the viewer, clause by clause.
#
#   open     media_up of the six images; the collection nudged off its top (so "the same scroll position" is not the
#            trivial one, where the list is long enough to move); tap qa-photo-0 (DCIM/Camera, by id) → the viewer;
#            `[motion] viewer_open t0=<uptime> … settle=<ms>` with settle = 250 ± 17 ms and maxGapMs <= 33.4 (C-31);
#            the screencap's centre pixel = (220,40,40) ± 4.
#            RECORDED, not asserted: "expanding from its thumbnail" — the form is Y6's approximation, judged by H4; the
#            `[motion]` line is the clock and carries no geometry, and a screencap cannot be timed inside 250 ms.
#   swipe    `adb shell input swipe 900 1170 180 1170 200` → the next image: centre pixel (40,180,80) ± 4,
#            `[motion] photo_swipe …` (maxGapMs <= 33.4), `[photosapp] viewer show <qa-photo-1's id>`.
#   zoom     pinch is P5 / H9. Double-tap with phase 05's gesture driver `script` op at the screen centre (r3 V22) →
#            `[photosapp] zoom <id> x2.00 width=<px>` with px > the screen's 1080 (Change Log 2026-10-05 14:27 (9): the
#            dump clips the image node to the screen, so the line is the reading; the gdump's node is recorded); a
#            second double-tap returns to the fit, so Back below is the viewer's own.
#   Back     → the collection (photos_pivot:collection asserted) with the tapped tile's bounds as before the open,
#            the viewer's node gone, `[motion] viewer_close …` (settle 250 ± 17 — E19's Y6 value — and maxGapMs).
#   VIEW     RECORDED: ViewerActivity started as the shell uid on a MediaStore image — which actions its bar offers
#            (the lead's order of 2026-10-05, ahead of a fix to that exported activity).
#   restore  media_down; the shell stopped; Start.
#
# Changes on the device: media (media_down), READ_MEDIA_IMAGES granted if it was not (restored). No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_photos.sh"

photos_row_begin E4 "the viewer: open, pixel, swipe, double-tap, Back"
perm_set READ_MEDIA_IMAGES true
# shellcheck disable=SC2086
media_up $SIX
six_ids
X="$D/collection.xml"
photos_cold "$X"
B_TOP="$(bounds "$X" "photos_item:$ID0")"
adb shell input swipe 540 1500 540 1320 800; sleep 1
dump_ui "$X"
assert_eq "photos_pivot:collection is selected" "true" "$(selected "$X" photos_pivot:collection)"
BEFORE="$(bounds "$X" "photos_item:$ID0")"
record "qa-photo-0's tile at the list's top / after the nudge (px)" "[$B_TOP] / [$BEFORE]"
assert_ne "qa-photo-0's tile is on the page" "" "$BEFORE"

log "--- open"
MARK="$(ring_mark)"
tap_node "$X" "photos_item:$ID0"; sleep 2
SLICE="$(ring_since "$MARK")"
screencap "$D/viewer0.png"; dump_ui "$D/viewer.xml"
assert_eq "the viewer is up (its node)" "yes" "$(has_node "$D/viewer.xml" viewer)"
assert_eq "… full screen (the viewer covers the page from 0,0 down to the nav bar's top)" "0 0 1080 $(bounds "$D/viewer.xml" w10m_nav_bar | cut -d' ' -f2)" "$(bounds "$D/viewer.xml" viewer)"
record "the viewer_open line" "$(motion_line "$SLICE" viewer_open)"
assert_contains "[motion] viewer_open t0=<uptime> …" "[motion] viewer_open t0=" "$SLICE"
assert_within "viewer_open settle = 250 ± 17 ms (Y6)" 250 "$(motion_field "$SLICE" viewer_open settle)" 17
assert_gap "viewer_open" "$SLICE" viewer_open
assert_contains "the viewer opened on qa-photo-0" "[photosapp] viewer open $ID0" "$SLICE"
assert_rgb "the centre pixel is qa-photo-0's colour" "220,40,40" "$(px "$D/viewer0.png" 540 1170)" 4
record "expanding from its thumbnail (Y6, r3 D4)" "not asserted: the form is an approximation judged by H4; the line carries no geometry"

log "--- swipe"
MARK="$(ring_mark)"
adb shell input swipe 900 1170 180 1170 200; sleep 2
SLICE="$(ring_since "$MARK")"
screencap "$D/viewer1.png"
record "the photo_swipe line" "$(motion_line "$SLICE" photo_swipe)"
assert_contains "[motion] photo_swipe …" "[motion] photo_swipe t0=" "$SLICE"
assert_gap "photo_swipe" "$SLICE" photo_swipe
assert_contains "the viewer shows the next image (qa-photo-1)" "[photosapp] viewer show $ID1" "$SLICE"
assert_rgb "the centre pixel is qa-photo-1's colour" "40,180,80" "$(px "$D/viewer1.png" 540 1170)" 4

log "--- double-tap (the gesture driver's script op)"
MARK="$(ring_mark)"
gesture "tap 540 1170; sleep 60; tap 540 1170"; assert_eq "the gesture driver ran the script" "0" "$?"
sleep 2
SLICE="$(ring_since "$MARK")"
ZLINE="$(printf '%s\n' "$SLICE" | grep -F "[photosapp] zoom $ID1 " | tail -1)"
record "the zoom line" "${ZLINE##*\[photosapp\] }"
assert_contains "[photosapp] zoom <id> x2.00 width=<px>" "[photosapp] zoom $ID1 x2.00 width=" "$ZLINE"
ZW="$(printf '%s\n' "$ZLINE" | grep -oE 'width=[0-9]+' | cut -d= -f2)"
assert_eq "the image is wider than the screen (width > 1080 px)" "yes" "$([ "${ZW:-0}" -gt 1080 ] && echo yes || echo "no: ${ZW:-none}")"
gdump "$D/zoomed.xml" || true; screencap "$D/zoomed.png"
record "viewer_image's bounds in the gdump while zoomed (clipped to the screen)" "$(bounds "$D/zoomed.xml" viewer_image)"
MARK="$(ring_mark)"
gesture "tap 540 1170; sleep 60; tap 540 1170"; sleep 2
assert_contains "a second double-tap returns to the fit" "[photosapp] zoom $ID1 x1.00 width=1080px" "$(ring_since "$MARK")"

log "--- Back"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_BACK; sleep 2
SLICE="$(ring_since "$MARK")"
dump_ui "$X.back"
assert_eq "Back: photos_pivot:collection is selected" "true" "$(selected "$X.back" photos_pivot:collection)"
assert_eq "Back: the viewer is gone" "no" "$(has_node "$X.back" viewer)"
assert_eq "Back: the collection is at the same scroll position (qa-photo-0's tile where it was)" "$BEFORE" "$(bounds "$X.back" "photos_item:$ID0")"
record "the viewer_close line" "$(motion_line "$SLICE" viewer_close)"
assert_contains "[motion] viewer_close …" "[motion] viewer_close t0=" "$SLICE"
assert_within "viewer_close settle = 250 ± 17 ms (Y6)" 250 "$(motion_field "$SLICE" viewer_close settle)" 17
assert_gap "viewer_close" "$SLICE" viewer_close
record "pinch zoom" "not driven: adb's input is one pointer — P5 / H9"

# ----------------------------------------------------------------------------------------------- the VIEW helper
# RECORDED on today's build (the lead's order, 2026-10-05): a fix to the exported ViewerActivity is being made — a
# foreign caller's content URI shown only when that caller may read it, and Edit / Delete / Set as not offered to a
# foreign caller. This leg starts it as the shell uid (a foreign caller) on a MediaStore image and writes down what the
# bar offers; on the fixed build the records become the assertions.
log "--- ViewerActivity on another caller's VIEW (recorded)"
rings_save
MARK="$(ring_mark)"
adb shell am start -a android.intent.action.VIEW -d "content://media/external/images/media/$ID1" -t image/png -n "$VIEWER_ACTIVITY" > "$D/view-start.out" 2>&1; sleep 3
record "VIEW as the shell uid: am start" "$(tr -d '\r' < "$D/view-start.out" | xargs)"
record "VIEW: what is resumed" "$(top_activity)"
dump_ui "$D/view.xml"; screencap "$D/view.png"
record "VIEW: the picture is shown (viewer_image / viewer_error; the centre pixel, qa-photo-1 is 40,180,80)" "$(has_node "$D/view.xml" viewer_image) / $(has_node "$D/view.xml" viewer_error); $(px "$D/view.png" 540 1170)"
record "VIEW: the bar offers Share / Edit / Delete / More" "$(has_node "$D/view.xml" viewer_share) / $(has_node "$D/view.xml" viewer_edit) / $(has_node "$D/view.xml" viewer_delete) / $(has_node "$D/view.xml" viewer_more)"
if [ "$(has_node "$D/view.xml" viewer_more)" = yes ]; then
  tap_node "$D/view.xml" viewer_more; sleep 1; dump_ui "$D/view-menu.xml"
  record "VIEW: the overflow offers Slideshow / Set as / File information" "$(has_node "$D/view-menu.xml" viewer_menu_slideshow) / $(has_node "$D/view-menu.xml" viewer_menu_setas) / $(has_node "$D/view-menu.xml" viewer_menu_info)"
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi
record "VIEW: the shell's lines" "$(ring_since "$MARK" | grep -F '[photosapp] ' | sed 's/.*\[photosapp\] //' | tr '\n' '|' | cut -c1-300)"
adb shell input keyevent KEYCODE_BACK; sleep 1

no_crash
c6
media_down
perm_restore
ensure_start
rings_save
row_end
