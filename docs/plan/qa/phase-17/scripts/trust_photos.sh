#!/usr/bin/env bash
# Phase 17 TRUST_PHOTOS — the device legs the viewer's trust fix owes (review/2026-10-05-phase17-trust-fixes.md, "Device
# legs these fixes owe", (h)–(n) and (p); the phase doc's Decisions line of 2026-10-05 16:32; photos/ViewerRules.kt).
# The other app is testapps/qa-photoview (added for this row: qa-capture cannot send a VIEW and holds no permission by
# design). It is installed WITHOUT -g, so it starts with no media permission; `pm grant` gives it READ_MEDIA_IMAGES for
# legs (j) and (l). Each leg reads the launcher's ring from its own MARK and the sender's TileShellQa line.
#
#   (h)  no media permission, VIEW content://media/external/images/media/<id> at ViewerActivity → the error state
#        (viewer_error), `[photosapp] viewer request from an unnamed app: refused`, `[photosapp] refused view: no grant`.
#   (i)  the same with FLAG_GRANT_READ_URI_PERMISSION → RECORDED: whether the sender's start throws, and what the
#        viewer then does.
#   (j)  the app holding READ_MEDIA_IMAGES → shown read-only: viewer_image, viewer_share and viewer_menu_info present;
#        viewer_edit, viewer_delete and viewer_menu_setas absent; the request line recorded (which answer let it in).
#        The build writes no line saying whether the platform's launch answer (ComponentCaller.checkContentUriPermission)
#        was "denied" or threw, so that half of the leg can only be read from the outcome. ON c7336aca THIS LEG FAILS:
#        the app is refused as "an unnamed app" (run 2, kept); the assertions stay as the fix file words them.
#   (k)  the app's OWN provider URI with the read flag → shown (its colour on screen), Share only.
#   (l)  with setShareIdentityEnabled(true) → the request line reads `another app`.
#   (m)  `adb shell am start` VIEW (the shell uid) → RECORDED: what the viewer does now.
#   (n)  Photos' own viewer still offers Edit, Delete and Set as.
#   (p)  NOT RUN, recorded: the Probe page on a picked Living Image needs a Living Image on the device; Living Images
#        is being built by another agent and no fixture of this row is one.
#
# Changes on the device: media (media_down), one fixture app (uninstalled). No wipe, no root.
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
  record "($leg) the viewer's request lines" "$(printf '%s\n' "$SLICE" | grep -E '\[photosapp\] (viewer request|refused view)' | sed 's/.*\[photosapp\] //' | tr '\n' '|')"
  V="$D/$leg.xml"
}
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
assert_contains "(h) [photosapp] refused view: no grant" "[photosapp] refused view: no grant" "$SLICE"
H_PX="$(px "$D/h.png" 540 1170)"; record "(h) the screen's centre pixel (qa-photo-1 is 40,180,80)" "$H_PX"
assert_ne "(h) the picture's colour is not on screen" "40,180,80" "$H_PX"

log "--- (i) no media permission, with FLAG_GRANT_READ_URI_PERMISSION (recorded)"
send i --es uri "$URI" --ez flag true
record "(i) the viewer: error state / picture; the bar" "viewer_error=$(has_node "$V" viewer_error) viewer_image=$(has_node "$V" viewer_image); $(offers "$V")"
assert_absent "(i) whatever the start did, nothing that changes a file is offered" "edit=yes" "$(offers "$V")"

log "--- (j) the app holding READ_MEDIA_IMAGES"
adb shell pm grant "$QV_PKG" android.permission.READ_MEDIA_IMAGES
assert_eq "(j) qa-photoview holds READ_MEDIA_IMAGES" "true" "$(qv_held)"
send j --es uri "$URI"
assert_contains "(j) the sender held the permission" "holdsReadMediaImages=true" "$SENT"
assert_eq "(j) ViewerActivity is resumed" "$VIEWER_ACTIVITY" "$(top_activity)"
assert_contains "(j) the request line: shown read-only" ": shown read-only" "$(printf '%s\n' "$SLICE" | grep -F '[photosapp] viewer request')"
absent_in "(j) no refusal line" "[photosapp] refused view" "$SLICE"
assert_eq "(j) the picture is shown (viewer_image)" "yes" "$(has_node "$V" viewer_image)"
assert_rgb "(j) … qa-photo-1's colour at the centre" "40,180,80" "$(px "$D/j.png" 540 1170)" 4
assert_eq "(j) the bar: Share present; Edit and Delete absent" "share=yes edit=no delete=no more=yes" "$(offers "$V")"
assert_eq "(j) the overflow: File information present, Set as absent" "setas=no info=yes" "$(menu_of j | cut -d' ' -f2-3)"

log "--- (l) the same with setShareIdentityEnabled(true)"
send l --es uri "$URI" --ez share true
assert_contains "(l) the request line reads another app" "[photosapp] viewer request from another app: " "$SLICE"
assert_contains "(l) … shown read-only" "viewer request from another app: shown read-only" "$SLICE"
assert_eq "(l) the bar: Share only of the row actions" "share=yes edit=no delete=no more=yes" "$(offers "$V")"
adb shell pm revoke "$QV_PKG" android.permission.READ_MEDIA_IMAGES
assert_eq "qa-photoview's permission is revoked again" "false" "$(qv_held)"

log "--- (k) the app's own provider URI with the read flag"
send k --ez own true --ez flag true
assert_contains "(k) the sender's start did not throw" "view start ok" "$SENT"
assert_eq "(k) ViewerActivity is resumed" "$VIEWER_ACTIVITY" "$(top_activity)"
assert_contains "(k) the request line: shown read-only" ": shown read-only" "$(printf '%s\n' "$SLICE" | grep -F '[photosapp] viewer request')"
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
record "(m) the viewer's request lines" "$(printf '%s\n' "$SLICE" | grep -E '\[photosapp\] (viewer request|refused view)' | sed 's/.*\[photosapp\] //' | tr '\n' '|')"
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

record "(p) the Probe page on a picked Living Image" "NOT RUN: it needs a Living Image on the device; none of this row's fixtures is one (Living Images is being built by another agent)"

no_crash
c6
adb uninstall "$QV_PKG" > "$D/qaview-uninstall.out" 2>&1
assert_eq "restore: qa-photoview is uninstalled" "0" "$(adb shell pm list packages "$QV_PKG" | grep -c "$QV_PKG")"
media_down
perm_restore
ensure_start
rings_save
row_end
