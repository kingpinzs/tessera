#!/usr/bin/env bash
# PHASE 10 BUILD TASK 10 — E18: "With READ_MEDIA_AUDIO denied, the app says so and the checklist row
# offers the grant."
#
# Three ways in. The checklist's Music row and the music app's own empty state are driven through
# Android's REAL permission dialog — the row taps Allow on it, never `pm grant`. The third is the case
# Android will not show a dialog for at all ("don't ask again"), where the only honest thing left is the
# app's own settings page; the switch on that page is Android's UI, not this app's, so the row's LAST
# step stands `pm grant` in for it and asserts only what this app owns — that coming back picks it up.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"
source "$(dirname "$0")/music_lib.sh"

PERM=android.permission.READ_MEDIA_AUDIO
deny() {
  adb shell pm clear-permission-flags $PKG $PERM user-set user-fixed >/dev/null 2>&1
  adb shell pm revoke $PKG $PERM >/dev/null 2>&1
  command sleep 1
}
granted() { adb shell dumpsys package $PKG 2>/dev/null | grep -m1 "$PERM: granted=" | sed 's/.*granted=\([a-z]*\).*/\1/'; }
allow_in_dialog() { # tap Android's own Allow, however this image lays the dialog out
  local i=0
  while [ "$i" -lt 8 ]; do
    dump "$ROW_DIR/.dialog.xml"
    if grep -q 'permission_allow_button' "$ROW_DIR/.dialog.xml"; then
      tap_id "$ROW_DIR/.dialog.xml" com.android.permissioncontroller:id/permission_allow_button
      command sleep 2
      return 0
    fi
    command sleep 1
    i=$((i + 1))
  done
  return 1
}
resumed() { adb shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' ; }

row_begin MUSIC10 "music access denied: the app says so, and both the checklist and the app offer the grant"

music_mute
music_fixtures

# ---- the checklist row ----------------------------------------------------------------------------
deny
assert_eq "the permission starts denied" false "$(granted)"
adb shell am force-stop $PKG; command sleep 1
adb shell am start -n $PKG/.settings.SettingsActivity --es page CHECKLIST >/dev/null 2>&1
command sleep 4
dump "$ROW_DIR/checklist_denied.xml"
adb exec-out screencap -p > "$ROW_DIR/checklist_denied.png"
assert_contains "the setup checklist has a Music row, marked off" "checklist:music:missing" "$(cat "$ROW_DIR/checklist_denied.xml")"
tap_id "$ROW_DIR/checklist_denied.xml" checklist:music:missing; command sleep 2
dump "$ROW_DIR/checklist_dialog.xml"
adb exec-out screencap -p > "$ROW_DIR/checklist_dialog.png"
assert_contains "tapping it raises Android's own permission dialog" "permissioncontroller" "$(cat "$ROW_DIR/checklist_dialog.xml")"
allow_in_dialog
assert_eq "allowing it there grants the permission" true "$(granted)"
dump "$ROW_DIR/checklist_granted.xml"
adb exec-out screencap -p > "$ROW_DIR/checklist_granted.png"
assert_contains "and the row turns on" "checklist:music:granted" "$(cat "$ROW_DIR/checklist_granted.xml")"

# ---- the music app says so, and offers the grant itself ---------------------------------------------
deny
music_open
dump "$ROW_DIR/app_denied.xml"
adb exec-out screencap -p > "$ROW_DIR/app_denied.png"
EMPTY="$(node_text "$ROW_DIR/app_denied.xml" music_empty:albums)"
note "the empty state reads: [$EMPTY]"
assert_contains "the app says it cannot read the music, rather than that there is none" "can't read the music" "$EMPTY"
assert_contains "and names where else it can be turned on" "Setup checklist" "$EMPTY"
assert_contains "and offers the grant right there" "music_grant" "$(cat "$ROW_DIR/app_denied.xml")"
assert_eq "no album is drawn" 0 "$(grep -c 'resource-id="music_album:' "$ROW_DIR/app_denied.xml")"
tap_id "$ROW_DIR/app_denied.xml" music_grant; command sleep 2
dump "$ROW_DIR/app_dialog.xml"
assert_contains "tapping it raises the same dialog" "permissioncontroller" "$(cat "$ROW_DIR/app_dialog.xml")"
allow_in_dialog
assert_eq "allowing it grants the permission" true "$(granted)"
command sleep 2
dump "$ROW_DIR/app_granted.xml"
adb exec-out screencap -p > "$ROW_DIR/app_granted.png"
ALBUMS=$(grep -c 'resource-id="music_album:' "$ROW_DIR/app_granted.xml")
note "albums drawn after the grant, without leaving the app: $ALBUMS"
assert_eq "and the library appears at once, without restarting the app" ok "$([ "$ALBUMS" -ge 1 ] && echo ok || echo "$ALBUMS")"
assert_absent "with the grant button gone" "music_grant" "$(cat "$ROW_DIR/app_granted.xml")"
assert_contains "the app says why it refreshed" "permission granted" "$(diag music)"

# ---- 'don't ask again': Android shows nothing, so the tap goes to the app's settings ------------------
deny
adb shell pm set-permission-flags $PKG $PERM user-set user-fixed >/dev/null 2>&1
music_open
dump "$ROW_DIR/fixed_denied.xml"
assert_contains "denied for good, the app still offers the grant" "music_grant" "$(cat "$ROW_DIR/fixed_denied.xml")"
tap_id "$ROW_DIR/fixed_denied.xml" music_grant; command sleep 3
R="$(resumed)"
note "after the tap: $R"
adb exec-out screencap -p > "$ROW_DIR/fixed_settings.png"
assert_absent "Android shows no dialog, because it will not ask again" "permissioncontroller" "$R"
assert_contains "so the tap opens the app's own settings page, the only place left to allow it" "com.android.settings" "$R"
assert_contains "and the app says why" "will not be asked again" "$(diag music)"

# ---- coming back after allowing it in Settings picks the library up ----------------------------------
adb shell pm clear-permission-flags $PKG $PERM user-set user-fixed >/dev/null 2>&1
adb shell pm grant $PKG $PERM >/dev/null 2>&1   # standing in for the switch on the settings page, which is Android's own UI
adb shell input keyevent KEYCODE_BACK; command sleep 3
dump "$ROW_DIR/returned.xml"
adb exec-out screencap -p > "$ROW_DIR/returned.png"
RET=$(grep -c 'resource-id="music_album:' "$ROW_DIR/returned.xml")
note "albums drawn after coming back: $RET"
assert_eq "coming back to the app shows the library without a restart" ok "$([ "$RET" -ge 1 ] && echo ok || echo "$RET")"
assert_contains "because the app re-reads access on resume" "access changed while away" "$(diag music)"

row_end
