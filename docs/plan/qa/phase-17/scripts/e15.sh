#!/usr/bin/env bash
# Phase 17 E15 — permissions, checklist and processes (the whole row: the Videos half and the Camera half).
#
#   V  Videos   `pm revoke app.tileshell android.permission.READ_MEDIA_VIDEO` → Movies & TV's empty state (video_empty)
#               names the Setup checklist, and the checklist's "Videos" row is red (`checklist:videos:missing`, r3 V22).
#   C  Camera   `pm revoke … CAMERA` → the Camera shows the grant page (camera_grant, no shutter, its text naming the
#               Setup checklist) and the "Camera" row is red (`checklist:camera:missing`).
#   G  grants   through Android's REAL dialog, from each app (phase 10 MUSIC10's method):
#               Camera — camera_grant_button raises com.android.permissioncontroller's dialog; its allow button is
#               tapped; CAMERA is granted and the viewfinder opens.
#               Videos — video_grant is tapped. While READ_MEDIA_IMAGES is held Android grants the rest of the
#               photos-and-videos group WITHOUT a dialog (the Video builder's finding, dev-video/scripts/b_hub.sh); the
#               row records which happened. So that the clause "through Android's real dialog" is seen for Videos too,
#               when no dialog came the leg is run a second time with READ_MEDIA_IMAGES (and the partial-access
#               permission) revoked as well for its span: the dialog then comes, its allow-all button is tapped, and
#               READ_MEDIA_IMAGES is asserted back.
#               Both rows green: `checklist:videos:granted`, `checklist:camera:granted`.
#   K  :camera  `pidof app.tileshell:camera` exists while the viewfinder is open; `adb root; kill -9 <pid>; adb unroot`
#               (the row's own clause; undone at once, the lock held): that pid is gone, the launcher's pid is
#               unchanged, and Start keeps updating (phase 03 E12's method: Start draws, and a notification posted
#               AFTER the kill still reaches the live tile engine).
#   K2 :video   the same for `app.tileshell:video` with Movies & TV open.
#   restore     CAMERA, READ_MEDIA_VIDEO, READ_MEDIA_IMAGES held (asserted); adb unrooted (asserted); the test
#               notifications cancelled; Start.
#
# A kill is by the recorded pid only. Changes on the device: three permissions (granted back), two notifications
# (cancelled). It force-stops the shell (c6) between legs.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/cam17.sh"
PC="com.android.permissioncontroller"

open_checklist() { adb shell am start -n "$PKG/.settings.SettingsActivity" --activity-clear-task --es page CHECKLIST >/dev/null 2>&1; sleep 3; }
# A Setup checklist row: scrolled to and dumped; prints yes / no for the tag `checklist:<id>:<state>`.
checklist_has() { # id state label
  open_checklist
  scroll_to_node "$D/checklist-$1-$2-$3.xml" "checklist:$1:$2" 8 >/dev/null 2>&1 || true
  has_node "$D/checklist-$1-$2-$3.xml" "checklist:$1:$2"
}
# Android's own permission dialog: prints its allow button's id (allow-all first, then allow, then while-in-use).
dialog_allow() { # dump.xml
  local id
  for id in permission_allow_all_button permission_allow_button permission_allow_foreground_only_button; do
    if [ "$(has_node "$1" "$PC:id/$id")" = yes ]; then echo "$PC:id/$id"; return 0; fi
  done
  return 1
}
vring() { ring_since "$1" "$VIDEO_RING"; }
restore_grants() {
  local p
  for p in CAMERA READ_MEDIA_VIDEO READ_MEDIA_IMAGES; do adb shell pm grant app.tileshell "android.permission.$p" >/dev/null 2>&1; done
  adb unroot >/dev/null 2>&1; adb wait-for-device
}
# The kill of one of the shell's own processes by its recorded pid, as root, root undone at once (the row's clause).
kill_as_root() { # pid
  adb root >/dev/null 2>&1; sleep 2; adb wait-for-device
  adb shell kill -9 "$1"; sleep 2
  adb unroot >/dev/null 2>&1; sleep 2; adb wait-for-device; sleep 1
  assert_eq "adb is unrooted again" "shell" "$(adb shell whoami | tr -d '\r')"
}
# Phase 03 E12's method: Start draws, and a notification posted after the kill reaches the live tile engine.
start_keeps_updating() { # label tag
  local before after
  ensure_start; sleep 3
  dump_ui "$D/$2-start.xml"; screencap "$D/$2-start.png"
  assert_eq "$1: Start is still drawing (start_page and the status bar in the dump)" "yes yes" "$(has_node "$D/$2-start.xml" start_page) $(has_node "$D/$2-start.xml" w10m_status_bar)"
  before="$(diag engine | wc -l)"
  adb shell cmd notification post -S bigtext -t "QA E15" "p17e15$2" "the live tile engine after the kill" >/dev/null 2>&1
  sleep 6
  after="$(diag engine | wc -l)"
  note "$1: engine lines before=$before after=$after"
  assert_ne "$1: a notification posted AFTER the kill still reaches the live tile engine" "$before" "$after"
  assert_eq "$1: the notification listener is still connected" "yes" "$(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener >/dev/null 2>&1 && echo yes || echo no)"
  adb shell cmd notification cancel "p17e15$2" >/dev/null 2>&1
}

cam_install E15
row_begin E15 "permissions, checklist and processes: Videos and Camera revoked, the real dialog, :camera and :video killed"
cam_preamble
D="$ROW_DIR"
trap restore_grants EXIT
layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "start: layout_restore of the baseline" "0" "$?"
ensure_start
record "start: grants before the row" "CAMERA=$(perm_granted CAMERA) READ_MEDIA_VIDEO=$(perm_granted READ_MEDIA_VIDEO) READ_MEDIA_IMAGES=$(perm_granted READ_MEDIA_IMAGES) READ_MEDIA_VISUAL_USER_SELECTED=$(perm_granted READ_MEDIA_VISUAL_USER_SELECTED)"
assert_eq "start: the baseline device holds CAMERA and READ_MEDIA_VIDEO" "true true" "$(perm_granted CAMERA) $(perm_granted READ_MEDIA_VIDEO)"

# ----------------------------------------------------------------------------------------------- V: Videos revoked
log "--- V: READ_MEDIA_VIDEO revoked"
rings_save
adb shell pm revoke app.tileshell android.permission.READ_MEDIA_VIDEO; sleep 1
assert_eq "V: READ_MEDIA_VIDEO is revoked" "false" "$(perm_granted READ_MEDIA_VIDEO)"
MARK="$(ring_mark)"
adb shell am start -W -n "$VIDEO_ACTIVITY" > "$D/V-start.out" 2>&1; sleep 3
dump_ui "$D/V-denied.xml"; screencap "$D/V-denied.png"
assert_eq "V: Movies & TV is in front" "$VIDEO_ACTIVITY" "$(top_activity)"
assert_eq "V: the empty state is shown (video_empty)" "yes" "$(has_node "$D/V-denied.xml" video_empty)"
EMPTY="$(node_text "$D/V-denied.xml" video_empty)"; record "V: the empty state's text" "$EMPTY"
assert_contains "V: the video app's empty state names the checklist" "Setup checklist" "$EMPTY"
vring "$MARK" > "$D/V-slice.txt"
record "V: the :video lines of the denied open" "$(grep -o '\[video\] [^w]*' "$D/V-slice.txt" | head -4 | tr '\n' ';')"
assert_eq "V: the checklist's Videos row is red (checklist:videos:missing)" "yes" "$(checklist_has videos missing V)"
assert_eq "V: … and not green" "no" "$(has_node "$D/checklist-videos-missing-V.xml" checklist:videos:granted)"

# ----------------------------------------------------------------------------------------------- C: Camera revoked
log "--- C: CAMERA revoked"
rings_save
adb shell pm revoke app.tileshell android.permission.CAMERA; sleep 1
assert_eq "C: CAMERA is revoked" "false" "$(perm_granted CAMERA)"
CAM_MARK="$(ring_mark)"
open_camera
cgdump "$D/C-grant.xml"; screencap "$D/C-grant.png"
assert_eq "C: the Camera is in front" "$CAMERA_ACTIVITY" "$(top_activity)"
assert_eq "C: the Camera app shows the grant page (camera_grant), no shutter" "yes no" "$(has_node "$D/C-grant.xml" camera_grant) $(has_node "$D/C-grant.xml" camera_shutter)"
GTEXT="$(node_text "$D/C-grant.xml" camera_grant_text)"; record "C: the grant page's text" "$GTEXT"
assert_contains "C: … it names the Setup checklist" "Setup checklist" "$GTEXT"
assert_contains "C: [camera] no camera permission: grant page" "[camera] no camera permission: grant page" "$(cam_since "$CAM_MARK")"
absent_in "C: no camera was opened without the permission" "[camera] devices=" "$(cam_since "$CAM_MARK")"
assert_eq "C: the checklist's Camera row is red (checklist:camera:missing)" "yes" "$(checklist_has camera missing C)"
assert_eq "C: … and not green" "no" "$(has_node "$D/checklist-camera-missing-C.xml" checklist:camera:granted)"

# ----------------------------------------------------------------------------------------------- G: the real dialog
log "--- G: Camera — Android's real dialog, from the app"
MARK="$(ring_mark)"
open_camera
cgdump "$D/G-cam-grant.xml"
assert_eq "G (Camera): the grant page again" "yes" "$(has_node "$D/G-cam-grant.xml" camera_grant_button)"
tap_node "$D/G-cam-grant.xml" camera_grant_button; sleep 2.5
dump_ui "$D/G-cam-dialog.xml"; screencap "$D/G-cam-dialog.png"
assert_contains "G (Camera): Android's own permission dialog is up" "package=\"$PC\"" "$(grep -o "package=\"$PC\"" "$D/G-cam-dialog.xml" | head -1)"
ALLOW="$(dialog_allow "$D/G-cam-dialog.xml")"; record "G (Camera): the dialog's allow button" "${ALLOW:-none}"
assert_ne "G (Camera): the dialog offers an allow button" "" "${ALLOW:-}"
tap_node "$D/G-cam-dialog.xml" "${ALLOW:-none}"; sleep 1
record "G (Camera): camera ready after the grant (s)" "$(wait_camera "$MARK")"
assert_eq "G (Camera): CAMERA is granted through the dialog" "true" "$(perm_granted CAMERA)"
S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/G-cam-slice.txt"
assert_contains "G (Camera): [camera] camera permission request: granted" "[camera] camera permission request: granted" "$S"
assert_contains "G (Camera): the camera opened" "[camera] devices=1 front=absent" "$S"
cgdump "$D/G-cam-vf.xml"
assert_eq "G (Camera): the viewfinder, in place" "yes no" "$(has_node "$D/G-cam-vf.xml" camera_shutter) $(has_node "$D/G-cam-vf.xml" camera_grant)"

log "--- G: Videos — from the app's empty state"
videos_grant_leg() { # tag -> DIALOG=yes|no
  MARK="$(ring_mark)"
  adb shell am start -W -n "$VIDEO_ACTIVITY" >/dev/null 2>&1; sleep 3
  dump_ui "$D/$1-denied.xml"
  assert_eq "$1: the grant is offered in the empty state (video_grant)" "yes" "$(has_node "$D/$1-denied.xml" video_grant)"
  tap_node "$D/$1-denied.xml" video_grant; sleep 2.5
  dump_ui "$D/$1-dialog.xml"; screencap "$D/$1-dialog.png"
  if grep -q "package=\"$PC\"" "$D/$1-dialog.xml"; then
    DIALOG=yes
    ALLOW="$(dialog_allow "$D/$1-dialog.xml")"; record "$1: Android's dialog was shown; its allow button" "${ALLOW:-none}"
    assert_ne "$1: the dialog offers an allow button" "" "${ALLOW:-}"
    tap_node "$D/$1-dialog.xml" "${ALLOW:-none}"; sleep 2.5
  else
    DIALOG=no
  fi
  dump_ui "$D/$1-granted.xml"
  assert_eq "$1: READ_MEDIA_VIDEO is granted from the app" "true" "$(perm_granted READ_MEDIA_VIDEO)"
  assert_contains "$1: [video] videos permission request: granted" "[video] videos permission request: granted" "$(vring "$MARK")"
  assert_eq "$1: granted in place — the empty state's grant is gone" "no" "$(has_node "$D/$1-granted.xml" video_grant)"
}
videos_grant_leg G-vid
record "G (Videos): Android's dialog on the first request (READ_MEDIA_IMAGES held: $(perm_granted READ_MEDIA_IMAGES))" "$DIALOG"
if [ "$DIALOG" = no ]; then
  # The platform granted the rest of the photos-and-videos group with no dialog. The clause names the real dialog, so
  # the leg runs again with the group's other grants revoked for its span.
  log "--- G: Videos again, with READ_MEDIA_IMAGES revoked for the span, so the dialog comes"
  rings_save
  adb shell pm revoke app.tileshell android.permission.READ_MEDIA_VIDEO
  adb shell pm revoke app.tileshell android.permission.READ_MEDIA_IMAGES
  adb shell pm revoke app.tileshell android.permission.READ_MEDIA_VISUAL_USER_SELECTED >/dev/null 2>&1
  sleep 1
  assert_eq "G (Videos, 2): the photos-and-videos grants are revoked" "false false" "$(perm_granted READ_MEDIA_VIDEO) $(perm_granted READ_MEDIA_IMAGES)"
  videos_grant_leg G-vid2
  assert_eq "G (Videos, 2): granting went through Android's real dialog" "yes" "$DIALOG"
  adb shell pm grant app.tileshell android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1
fi
assert_eq "G: READ_MEDIA_IMAGES is held again" "true" "$(perm_granted READ_MEDIA_IMAGES)"
assert_eq "G: both rows green — checklist:videos:granted" "yes" "$(checklist_has videos granted G)"
assert_eq "G: both rows green — checklist:camera:granted" "yes" "$(checklist_has camera granted G)"
assert_eq "G: … neither is red" "no no" "$(has_node "$D/checklist-videos-granted-G.xml" checklist:videos:missing) $(has_node "$D/checklist-camera-granted-G.xml" checklist:camera:missing)"

# ----------------------------------------------------------------------------------------------- K: :camera killed
log "--- K: :camera killed, the launcher lives"
c6; ensure_start
MARK="$(ring_mark)"
open_camera; record "K: camera ready after (s)" "$(wait_camera "$MARK")"
cgdump "$D/K-vf.xml"
assert_eq "K: the viewfinder is open" "yes" "$(has_node "$D/K-vf.xml" camera_shutter)"
LPID="$(adb shell pidof app.tileshell | tr -d '\r')"; CPID="$(adb shell pidof app.tileshell:camera | tr -d '\r')"
record "K: launcher pid / :camera pid" "$LPID / $CPID"
assert_ne "K: the launcher runs" "" "$LPID"
assert_ne "K: pidof app.tileshell:camera exists while the viewfinder is open" "" "$CPID"
rings_save
case "$CPID" in ''|*[!0-9]*) _verdict FAIL "K: the kill" "no single numeric pid to kill: [$CPID]" ;; *) kill_as_root "$CPID" ;; esac
assert_ne "K: the :camera process that was killed is gone" "$CPID" "$(adb shell pidof app.tileshell:camera | tr -d '\r')"
assert_eq "K: the launcher's pid is unchanged" "$LPID" "$(adb shell pidof app.tileshell | tr -d '\r')"
start_keeps_updating "K" K
assert_eq "K: … and the launcher's pid is still unchanged" "$LPID" "$(adb shell pidof app.tileshell | tr -d '\r')"
MARK="$(ring_mark)"; open_camera; record "K: camera ready after the kill (s)" "$(wait_camera "$MARK")"
assert_contains "K: the next open is a fresh :camera process that opens the camera" "[camera] devices=1 front=absent" "$(cam_since "$MARK")"
assert_eq "K: no pending row of the shell's" "0" "$(pending_rows)"

# ----------------------------------------------------------------------------------------------- K2: :video killed
log "--- K2: :video killed, the launcher lives"
c6; ensure_start
adb shell am start -W -n "$VIDEO_ACTIVITY" >/dev/null 2>&1; sleep 3
dump_ui "$D/K2-video.xml"
assert_eq "K2: Movies & TV is open" "$VIDEO_ACTIVITY" "$(top_activity)"
LPID="$(adb shell pidof app.tileshell | tr -d '\r')"; VPID="$(adb shell pidof app.tileshell:video | tr -d '\r')"
record "K2: launcher pid / :video pid" "$LPID / $VPID"
assert_ne "K2: pidof app.tileshell:video exists while Movies & TV is open" "" "$VPID"
rings_save
case "$VPID" in ''|*[!0-9]*) _verdict FAIL "K2: the kill" "no single numeric pid to kill: [$VPID]" ;; *) kill_as_root "$VPID" ;; esac
assert_ne "K2: the :video process that was killed is gone" "$VPID" "$(adb shell pidof app.tileshell:video | tr -d '\r')"
assert_eq "K2: the launcher's pid is unchanged" "$LPID" "$(adb shell pidof app.tileshell | tr -d '\r')"
start_keeps_updating "K2" K2
assert_eq "K2: … and the launcher's pid is still unchanged" "$LPID" "$(adb shell pidof app.tileshell | tr -d '\r')"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
no_crash
restore_grants
trap - EXIT
assert_eq "restore: CAMERA, READ_MEDIA_VIDEO, READ_MEDIA_IMAGES held" "true true true" "$(perm_granted CAMERA) $(perm_granted READ_MEDIA_VIDEO) $(perm_granted READ_MEDIA_IMAGES)"
assert_eq "restore: adb is not root" "shell" "$(adb shell whoami | tr -d '\r')"
c6; ensure_start
row_end
