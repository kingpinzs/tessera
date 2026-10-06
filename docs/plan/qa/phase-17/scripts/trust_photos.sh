#!/usr/bin/env bash
# Phase 17 TRUST_PHOTOS — the device legs the viewer's trust fix owes (review/2026-10-05-phase17-trust-fixes.md, "Device
# legs these fixes owe", (h)–(n) and (p), as its "Round 3" section re-words them; the phase doc's Decisions line of
# 2026-10-05 19:09 — the ONE access rule and the platform limit; media/UriAccess.kt, photos/ViewerRules.kt).
# Round 3 writes `[photosapp] launch answer read: <granted|denied|threw X|not available>` whenever the rule asks the
# platform's launch answer: every leg that asks it asserts its line, and a leg the rule decides BEFORE asking (a named
# starter the provider allows — (l); the shell's own launch — (n)) asserts that no such line is written.
# The other app is testapps/qa-photoview (added for this row: qa-capture cannot send a VIEW and holds no permission by
# design). It is installed WITHOUT -g, so it starts with no media permission; `pm grant` gives it READ_MEDIA_IMAGES for
# legs (j) and (l). Each leg reads the launcher's ring from its own MARK and the sender's TileShellQa line.
#
#   (h)  no media permission, VIEW content://media/external/images/media/<id> at ViewerActivity → the error state
#        (viewer_error), `[photosapp] viewer request from an unnamed app: refused`, `[photosapp] launch answer read:
#        denied`, `[photosapp] refused view: no grant`.
#   (i)  the same with FLAG_GRANT_READ_URI_PERMISSION → RECORDED: whether the sender's start throws, and what the
#        viewer then does.
#   (j)  the app holding READ_MEDIA_IMAGES → REFUSED (round 3: the platform limit — the launch answer is "denied" for a
#        MediaStore item unless the starter holds a grant): `[photosapp] launch answer read: denied`, `… from an unnamed
#        app: refused`, `refused view: no grant`, the error state, no picture, nothing offered. RECORDED right after the
#        leg: `adb logcat -d | grep -c "holding WM lock"` (and the count before it, and the last such line).
#   (k)  the app's OWN provider URI with the read flag → `launch answer read: granted`, shown (its colour on screen),
#        Share only.
#   (l)  with setShareIdentityEnabled(true) → the request line reads `another app`, shown read-only; the rule decides
#        for a named starter before the launch answer is asked, so no `launch answer` line is written.
#   (m)  `adb shell am start` VIEW (the shell uid) → RECORDED: what the viewer does now and what the shell uid's
#        launch answer reads.
#   (n)  Photos' own viewer still offers Edit, Delete and Set as (no request line and no launch answer: it is not
#        ViewerActivity).
#   (p)  the Probe page on a picked Living Image: the Camera takes one with "Capture living images" on (as
#        dev-living/scripts/l1_living.sh does; RECORD_AUDIO revoked for the span; the setting put back off), the file
#        pulled and read on the host (dev-camera/scripts/motion_check.py), then Settings → Diagnostics → Probe → pick:
#        `motionPhoto: MotionPhoto=1 offset=<the JPEG's bytes> length=<the trailing MP4's bytes> …`.
#
# Changes on the device: media (media_down — the Living Image too), one fixture app (uninstalled), RECORD_AUDIO revoked
# and granted back, the Camera's Living Images setting on and off again. No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_photos.sh"
QV_APK="$REPO/testapps/qa-photoview/build/outputs/apk/debug/qa-photoview-debug.apk"
QV_PKG="app.tileshell.testclient.qaphotoview"
# send <leg> <am extras…>: the viewer closed, a MARK, the sender started; leaves $D/<leg>.xml / .png, SLICE and SENT.
send() {
  local leg="$1"; shift
  adb shell am force-stop "$QV_PKG"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  MARK="$(ring_mark)"; LT="$(adb shell date +'%m-%d\ %H:%M:%S.000' | tr -d '\r')"
  adb shell am start --activity-clear-task -n "$QV_PKG/.ViewProbeActivity" "$@" > "$D/$leg-start.out" 2>&1
  sleep 4
  dump_ui "$D/$leg.xml"; screencap "$D/$leg.png"
  SLICE="$(ring_since "$MARK")"
  SENT="$(adb logcat -d -T "$LT" -s TileShellQa:I 2>/dev/null | tr -d '\r' | grep -F 'view start' | tail -1 | sed 's/.*TileShellQa: //')"
  record "($leg) the sender's line" "$SENT"
  record "($leg) what is resumed" "$(top_activity)"
  record "($leg) the viewer's request lines" "$(printf '%s\n' "$SLICE" | grep -E '\[photosapp\] (viewer request|launch answer|refused view)' | sed 's/.*\[photosapp\] //; s/ *wall=.*//' | tr '\n' '|')"
  V="$D/$leg.xml"
}
wm_lock_lines() { adb logcat -d 2>/dev/null | grep -c "holding WM lock"; }
offers() { echo "share=$(has_node "$1" viewer_share) edit=$(has_node "$1" viewer_edit) delete=$(has_node "$1" viewer_delete) more=$(has_node "$1" viewer_more)"; }
menu_of() { # leg -> $D/<leg>-menu.xml when the bar has More
  M="$D/$1-menu.xml"; : > "$M"
  if [ "$(has_node "$D/$1.xml" viewer_more)" = yes ]; then tap_node "$D/$1.xml" viewer_more; sleep 1; dump_ui "$M"; adb shell input keyevent KEYCODE_BACK; sleep 1; fi
  echo "slideshow=$(has_node "$M" viewer_menu_slideshow) setas=$(has_node "$M" viewer_menu_setas) info=$(has_node "$M" viewer_menu_info)"
}

photos_row_begin TRUST_PHOTOS "the viewer's trust fix: who may have a picture shown, and what the viewer then offers"
perm_set READ_MEDIA_IMAGES true
assert_eq "the qa-photoview APK is built (./gradlew :testapps:qa-photoview:assembleDebug --offline)" "yes" "$([ -f "$QV_APK" ] && echo yes || echo no)"
adb install -r "$QV_APK" > "$D/qaview-install.out" 2>&1; assert_eq "qa-photoview installed (never with -g)" "0" "$?"
qv_held() { local d; d="$(adb shell dumpsys package "$QV_PKG" | tr -d '\r')"; case "$d" in *"android.permission.READ_MEDIA_IMAGES: granted=true"*) echo true ;; *) echo false ;; esac; }
assert_eq "qa-photoview holds no media permission" "false" "$(qv_held)"
# shellcheck disable=SC2086
media_up $SIX
six_ids
URI="content://media/external/images/media/$ID1"
rings_save; adb shell am force-stop app.tileshell; sleep 1; ensure_start

log "--- (h) no media permission, no flag"
send h --es uri "$URI"
assert_contains "(h) the sender's start did not throw (the activity is exported)" "view start ok" "$SENT"
assert_contains "(h) the sender held no permission" "holdsReadMediaImages=false" "$SENT"
assert_eq "(h) ViewerActivity is resumed" "$VIEWER_ACTIVITY" "$(top_activity)"
assert_eq "(h) the viewer shows its error state (viewer_error)" "yes" "$(has_node "$V" viewer_error)"
assert_eq "(h) … and no picture (viewer_image)" "no" "$(has_node "$V" viewer_image)"
assert_contains "(h) [photosapp] viewer request from an unnamed app: refused" "[photosapp] viewer request from an unnamed app: refused" "$SLICE"
assert_contains "(h) [photosapp] launch answer read: denied" "[photosapp] launch answer read: denied" "$SLICE"
assert_contains "(h) [photosapp] refused view: no grant" "[photosapp] refused view: no grant" "$SLICE"
H_PX="$(px "$D/h.png" 540 1170)"; record "(h) the screen's centre pixel (qa-photo-1 is 40,180,80)" "$H_PX"
assert_ne "(h) the picture's colour is not on screen" "40,180,80" "$H_PX"

log "--- (i) no media permission, with FLAG_GRANT_READ_URI_PERMISSION (recorded)"
send i --es uri "$URI" --ez flag true
record "(i) the viewer: error state / picture; the bar" "viewer_error=$(has_node "$V" viewer_error) viewer_image=$(has_node "$V" viewer_image); $(offers "$V")"
assert_absent "(i) whatever the start did, nothing that changes a file is offered" "edit=yes" "$(offers "$V")"

# Added 2026-10-06 after the gate review (reviewer A, finding 6): a NAMED starter with no access is a no on the device
# too - (l), the only shared-identity leg until now, is a positive.
log "--- (h-shared) (h) with setShareIdentityEnabled(true), the app still holding no READ_MEDIA_IMAGES"
assert_eq "(h-shared) qa-photoview does not hold READ_MEDIA_IMAGES" "false" "$(qv_held)"
send hshared --es uri "$URI" --ez share true
assert_contains "(h-shared) the request line reads another app … refused" "[photosapp] viewer request from another app: refused" "$SLICE"
assert_contains "(h-shared) [photosapp] refused view: no grant" "[photosapp] refused view: no grant" "$SLICE"
absent_in "(h-shared) not shown" "shown read-only" "$SLICE"
assert_eq "(h-shared) the viewer shows its error state (viewer_error)" "yes" "$(has_node "$V" viewer_error)"
assert_eq "(h-shared) … and no picture (viewer_image)" "no" "$(has_node "$V" viewer_image)"

log "--- (j) the app holding READ_MEDIA_IMAGES (round 3: refused — the platform limit)"
adb shell pm grant "$QV_PKG" android.permission.READ_MEDIA_IMAGES
assert_eq "(j) qa-photoview holds READ_MEDIA_IMAGES" "true" "$(qv_held)"
WM_BEFORE="$(wm_lock_lines)"
send j --es uri "$URI"
WM_AFTER="$(wm_lock_lines)"
record "(j) adb logcat -d | grep -c \"holding WM lock\", taken right after the leg (before the leg: $WM_BEFORE)" "$WM_AFTER"
record "(j) the last such logcat line" "$(adb logcat -d 2>/dev/null | tr -d '\r' | grep "holding WM lock" | tail -1 | cut -c1-300)"
assert_contains "(j) the sender's start did not throw" "view start ok" "$SENT"
assert_contains "(j) the sender held the permission" "holdsReadMediaImages=true" "$SENT"
assert_eq "(j) ViewerActivity is resumed" "$VIEWER_ACTIVITY" "$(top_activity)"
assert_contains "(j) [photosapp] launch answer read: denied" "[photosapp] launch answer read: denied" "$SLICE"
assert_contains "(j) [photosapp] viewer request from an unnamed app: refused" "[photosapp] viewer request from an unnamed app: refused" "$SLICE"
assert_contains "(j) [photosapp] refused view: no grant" "[photosapp] refused view: no grant" "$SLICE"
assert_eq "(j) the viewer shows its error state (viewer_error)" "yes" "$(has_node "$V" viewer_error)"
assert_eq "(j) … and no picture (viewer_image)" "no" "$(has_node "$V" viewer_image)"
J_PX="$(px "$D/j.png" 540 1170)"; record "(j) the screen's centre pixel (qa-photo-1 is 40,180,80)" "$J_PX"
assert_ne "(j) the picture's colour is not on screen" "40,180,80" "$J_PX"
assert_eq "(j) nothing is offered: no Share, Edit, Delete or More" "share=no edit=no delete=no more=no" "$(offers "$V")"

log "--- (l) the same with setShareIdentityEnabled(true)"
send l --es uri "$URI" --ez share true
assert_contains "(l) the request line reads another app" "[photosapp] viewer request from another app: " "$SLICE"
assert_contains "(l) … shown read-only" "viewer request from another app: shown read-only" "$SLICE"
absent_in "(l) no refusal line" "[photosapp] refused view" "$SLICE"
absent_in "(l) the launch answer is not asked: the rule decided for a named starter first (UriAccessRules.starterMayRead)" "[photosapp] launch answer" "$SLICE"
assert_eq "(l) the picture is shown (viewer_image)" "yes" "$(has_node "$V" viewer_image)"
assert_rgb "(l) … qa-photo-1's colour at the centre" "40,180,80" "$(px "$D/l.png" 540 1170)" 4
assert_eq "(l) the bar: Share only of the row actions" "share=yes edit=no delete=no more=yes" "$(offers "$V")"
assert_eq "(l) the overflow: File information present, Set as absent" "setas=no info=yes" "$(menu_of l | cut -d' ' -f2-3)"
adb shell pm revoke "$QV_PKG" android.permission.READ_MEDIA_IMAGES
assert_eq "qa-photoview's permission is revoked again" "false" "$(qv_held)"

log "--- (k) the app's own provider URI with the read flag"
send k --ez own true --ez flag true
assert_contains "(k) the sender's start did not throw" "view start ok" "$SENT"
assert_eq "(k) ViewerActivity is resumed" "$VIEWER_ACTIVITY" "$(top_activity)"
assert_contains "(k) the request line: shown read-only" ": shown read-only" "$(printf '%s\n' "$SLICE" | grep -F '[photosapp] viewer request')"
assert_contains "(k) [photosapp] launch answer read: granted (the starter owns the provider)" "[photosapp] launch answer read: granted" "$SLICE"
absent_in "(k) no refusal line" "[photosapp] refused view" "$SLICE"
assert_eq "(k) the picture is shown" "yes" "$(has_node "$V" viewer_image)"
assert_rgb "(k) … the app's own picture (30,160,60) at the centre" "30,160,60" "$(px "$D/k.png" 540 1170)" 4
assert_eq "(k) Share is offered; Edit and Delete are not" "share=yes edit=no delete=no" "$(offers "$V" | cut -d' ' -f1-3)"
K_MENU="$(menu_of k)"; record "(k) the overflow (More present: $(has_node "$V" viewer_more))" "$K_MENU"
assert_absent "(k) Share only: no Set as" "setas=yes" "$K_MENU"
assert_absent "(k) Share only: no File information (the picture is no row of the shell's)" "info=yes" "$K_MENU"

log "--- (m) adb shell am start VIEW (the shell uid; recorded)"
adb shell input keyevent KEYCODE_HOME; sleep 1
MARK="$(ring_mark)"
adb shell am start -a android.intent.action.VIEW -d "$URI" -t image/png -n "$VIEWER_ACTIVITY" > "$D/m-start.out" 2>&1; sleep 4
dump_ui "$D/m.xml"; screencap "$D/m.png"
SLICE="$(ring_since "$MARK")"
record "(m) am start" "$(tr -d '\r' < "$D/m-start.out" | xargs)"
record "(m) what is resumed" "$(top_activity)"
record "(m) the viewer's request lines" "$(printf '%s\n' "$SLICE" | grep -E '\[photosapp\] (viewer request|launch answer|refused view)' | sed 's/.*\[photosapp\] //; s/ *wall=.*//' | tr '\n' '|')"
record "(m) what the shell uid's launch answer reads" "$(printf '%s\n' "$SLICE" | grep -o 'launch answer read: .*' | sed 's/ *wall=.*//' | head -1)"
record "(m) the viewer: error state / picture; the bar; the overflow" "viewer_error=$(has_node "$D/m.xml" viewer_error) viewer_image=$(has_node "$D/m.xml" viewer_image); $(offers "$D/m.xml"); $(menu_of m)"
adb shell input keyevent KEYCODE_BACK; sleep 1

log "--- (n) Photos' own viewer"
photos_cold "$D/collection.xml"
MARK="$(ring_mark)"
viewer_open "$D/collection.xml" "$ID1"
assert_eq "(n) Photos' own viewer is up" "yes" "$(has_node "$D/viewer.xml" viewer)"
assert_eq "(n) the bar still offers Share, Edit, Delete, More" "share=yes edit=yes delete=yes more=yes" "$(offers "$D/viewer.xml")"
cp "$D/viewer.xml" "$D/n.xml"
assert_eq "(n) the overflow still offers Slideshow, Set as, File information" "slideshow=yes setas=yes info=yes" "$(menu_of n)"
absent_in "(n) no refusal line for the shell's own viewer" "refused view" "$(ring_since "$MARK")"
absent_in "(n) … and no launch answer is asked for it" "[photosapp] launch answer" "$(ring_since "$MARK")"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 1

log "--- (p) the Probe page on a picked Living Image"
# The capture is dev-living/scripts/l1_living.sh's, step for step: the Camera's own shutter with "Capture living images"
# on (no microphone: RECORD_AUDIO is revoked for the span — the revoke restarts the shell's processes, so every ring
# is kept first), the setting put back off, the file's own facts read on the host.
cam_dump() { local i; for i in 1 2 3 4 5 6; do gdump "$1" || true; grep -q 'package="app.tileshell"' "$1" && return 0; sleep 0.4; done; return 1; }
cam_wait() { local i; for i in $(seq 1 20); do ring_since "$1" "$CAMERA_RING" | grep -q '\[camera\] devices=' && { sleep 1; echo "$i"; return 0; }; sleep 1; done; echo timeout; return 1; }
living_toggle() { # prefix: the viewfinder -> settings -> the Living Images row tapped -> back on the viewfinder
  cam_dump "$D/p-$1-a.xml"; tap_node "$D/p-$1-a.xml" camera_settings; sleep 1.5
  adb shell input swipe 540 1700 540 900 300; sleep 1
  cam_dump "$D/p-$1-b.xml"; tap_node "$D/p-$1-b.xml" "camera_set:living"; sleep 1
  cam_dump "$D/p-$1-c.xml"
  adb shell input keyevent KEYCODE_BACK; sleep 3
}
living_mode() { grep -o '<node[^>]*camera_mode:livingimages"[^>]*>' "$1" | grep -o 'selected="[a-z]*"' | head -1; }
P_IMAGES="$(media_count images)"; P_VIDEOS="$(media_count video)"
rings_save
mic_off
adb shell am force-stop app.tileshell; ensure_start; sleep 2
MARK="$(ring_mark)"
adb shell am start -n "$CAMERA_ACTIVITY" >/dev/null 2>&1; sleep 4
record "(p) camera ready after (s)" "$(cam_wait "$MARK")"
cam_dump "$D/p-vf.xml"
assert_eq "(p) the Camera's viewfinder, Living Images off as found" "$CAMERA_ACTIVITY selected=\"false\"" "$(top_activity) $(living_mode "$D/p-vf.xml")"
living_toggle on
assert_contains "(p) \"Capture living images\" is on" 'checked="true"' "$(grep -o '<node[^>]*camera_set:living"[^>]*>' "$D/p-on-c.xml")"
cam_dump "$D/p-vf2.xml"
sleep 2                                   # a second of viewfinder before the shutter: the clip's content
MARK="$(ring_mark)"
tap_node "$D/p-vf2.xml" camera_shutter; sleep 6
P_SAVED="$(ring_since "$MARK" "$CAMERA_RING" | grep -o 'saved content://.*' | head -1 | sed 's/ *wall=.*//')"; record "(p) the Living Image's saved line" "$P_SAVED"
assert_contains "(p) saved as a living image" "living image clip=" "$P_SAVED"
assert_eq "(p) one new image row and no video row (no sidecar)" "$((P_IMAGES + 1)) $P_VIDEOS" "$(media_count images) $(media_count video)"
P_ID="$(printf '%s\n' "$P_SAVED" | grep -oE 'images/media/[0-9]+' | head -1 | sed 's|.*/||')"
P_ROW="$(row_of images "${P_ID:-0}")"; record "(p) the Living Image's row" "$P_ROW"
P_NAME="$(row_field "$P_ROW" _display_name)"; P_SIZE="$(row_field "$P_ROW" _size)"
assert_eq "(p) the row is the shell's, in DCIM/Camera/, published" "app.tileshell DCIM/Camera/ 0" "$(row_field "$P_ROW" owner_package_name) $(row_field "$P_ROW" relative_path) $(row_field "$P_ROW" is_pending)"
living_toggle off
cam_dump "$D/p-vf3.xml"
assert_eq "(p) restore: Living Images is back off" 'selected="false"' "$(living_mode "$D/p-vf3.xml")"
adb shell "cat '/sdcard/DCIM/Camera/$P_NAME'" > "$D/p-living.jpg"
assert_eq "(p) the pulled file is the row's size" "$P_SIZE" "$(stat -c%s "$D/p-living.jpg")"
P_FACTS="$(python3 "$P17/dev-camera/scripts/motion_check.py" "$D/p-living.jpg" 2>&1)"; record "(p) the container's facts, read on the host (motion_check.py)" "$P_FACTS"
P_LEN="$(echo "$P_FACTS" | grep -o 'trailing_mp4=[0-9]*' | cut -d= -f2)"; P_AT="$(echo "$P_FACTS" | grep -o 'jpeg_bytes=[0-9]*' | cut -d= -f2)"; P_STAMP="$(echo "$P_FACTS" | grep -o 'timestamp_us=[0-9]*' | cut -d= -f2)"
assert_contains "(p) the file is a Motion Photo whose directory length is the trailing MP4's size" "length_matches=yes" "$P_FACTS"
rings_save
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am start -n app.tileshell/.settings.SettingsActivity --es page DIAGNOSTICS >/dev/null 2>&1; sleep 4
dump_ui "$D/p-diag.xml"
assert_eq "(p) Settings → Diagnostics (diag_probe)" "yes" "$(has_node "$D/p-diag.xml" diag_probe)"
tap_node "$D/p-diag.xml" diag_probe; sleep 3
dump_ui "$D/p-probe.xml"
assert_eq "(p) the Probe page (probe_pick)" "yes" "$(has_node "$D/p-probe.xml" probe_pick)"
tap_node "$D/p-probe.xml" probe_pick; sleep 3
record "(p) the picker's activity" "$(top_activity)"
adb shell uiautomator dump /sdcard/Download/p17_picker.xml >/dev/null 2>&1; adb shell cat /sdcard/Download/p17_picker.xml > "$D/p-picker.xml"; adb shell rm -f /sdcard/Download/p17_picker.xml
# Android's own picker lists the newest first: its first tile is the Living Image (the capture of a moment ago; the
# six fixtures' mtimes are an hour and more back).
P_XY="$(python3 -c '
import re, sys
x = open(sys.argv[1], errors="replace").read()
m = re.search(r"resource-id=\"[^\"]*(?:icon_thumbnail|media_item|image_view)[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", x)
print("%d %d" % ((int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2) if m else "")
' "$D/p-picker.xml")"
record "(p) the picker's first item at" "${P_XY:-not found}"
assert_ne "(p) the picker shows a first item" "" "$P_XY"
if [ -n "$P_XY" ]; then
  # shellcheck disable=SC2086
  adb shell input tap $P_XY; sleep 4
  dump_ui "$D/p-picked.xml"; screencap "$D/p-picked.png"
  P_FILE="$(node_text "$D/p-picked.xml" probe_file | sed 's/&#10;/\n/g')"
  record "(p) the probe's file text" "$(echo "$P_FILE" | tr '\n' '|' | cut -c1-600)"
  assert_contains "(p) the picked file is the Living Image (its size)" " sizeBytes=$P_SIZE" "$P_FILE"
  P_MP="$(echo "$P_FILE" | grep -F 'motionPhoto:' | head -1)"; record "(p) the probe's Motion Photo line" "$P_MP"
  assert_contains "(p) motionPhoto: MotionPhoto=1 offset=<n> length=<n> …" "motionPhoto: MotionPhoto=1 offset=" "$P_MP"
  assert_eq "(p) … the clip's offset (the JPEG's bytes), a length equal to the trailing MP4's size, the timestamp" \
    "motionPhoto: MotionPhoto=1 offset=$P_AT length=$P_LEN timestampUs=$P_STAMP" "$P_MP"
else
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 1
mic_on

no_crash
c6
adb uninstall "$QV_PKG" > "$D/qaview-uninstall.out" 2>&1
assert_eq "restore: qa-photoview is uninstalled" "0" "$(adb shell pm list packages "$QV_PKG" | grep -c "$QV_PKG")"
media_down
perm_restore
ensure_start
rings_save
row_end
