#!/usr/bin/env bash
# Phase 12's edge cases (phase doc, "Edge cases"), one row per case so any one re-runs alone:
#   edge.sh <case>   ->  qa/phase-12/EDGE_<CASE>/
# Cases here: install_r, lmk, rotation, dismiss, double_tap, partial_photos, partial_bgloc, partial_calendar, location_off,
# kb_dismiss, home_once, light, custom_before, battery_saver, no_picture, profile, keyguard.
# Covered by the acceptance rows as the doc words them (README maps each): revoked / granted mid-run = E7; a grant made
# while the wizard is not showing, process death, reboot = E4; Tess over the wizard and the pending pin band = E8; a
# force-stop that deselects the keyboard with the marker set = E1 (a); Transparency effects after a preset = E11; a core
# row added to a finished install = E5's revoked-later case. A pending pod-bay request: phase 14 is not built (T12-8 /
# T14-7 — there is no request to hold back yet); a burst cannot be open under the wizard (edit mode is under it).
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
CASE="${1:?usage: edge.sh <case>}"
C_UP="$(echo "$CASE" | tr 'a-z' 'A-Z')"
pid() { adb shell pidof $PKG | tr -d '\r' | awk '{print $1}'; }
start_records() { adb shell dumpsys activity activities | grep -oE 'Hist .*ActivityRecord\{[0-9a-f]+ u0 app\.tileshell/\.StartActivity' | grep -oE '\{[0-9a-f]+'; }
cur_step() { dump_ui "$ROW_DIR/$1.xml"; wiz_step "$ROW_DIR/$1.xml"; }
start_e2() { e2_state; assert_e2_appops; adb shell input keyevent KEYCODE_HOME; sleep 5; }
# a no-marker provision with the given revocations, so the ASSISTANT role (and everything else) is held
start_small() { # revoke-commands...
  leave_home
  adb shell pm clear "$PKG" >/dev/null
  provision_no_marker small
  local c; for c in "$@"; do eval "$c"; done
  adb shell input keyevent KEYCODE_HOME; sleep 5
}
end_row() { restore_fresh end; assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"; row_end; }

case "$CASE" in

install_r)
  row_begin EDGE_INSTALL_R "adb install -r mid-run: no marker, so the run resumes (as E4)"
  start_e2
  walk_to setup:photos "$ROW_DIR/w" >/dev/null
  tap_node "$ROW_DIR/w-at.xml" wizard_action; sleep 2.5
  perm_tap "$ROW_DIR/dialog.xml" allow_all_button "allow all"; sleep 3
  adb install -r "$APK" > "$ROW_DIR/install.txt" 2>&1
  assert_contains "install -r succeeded" "Success" "$(cat "$ROW_DIR/install.txt")"
  adb shell input keyevent KEYCODE_HOME; sleep 5
  assert_eq "after the update install: the wizard on setup:notifications" "setup:notifications" "$(cur_step after)"
  assert_eq "one fewer N: Step 1 of 19" "Step 1 of 19" "$(ntext "$ROW_DIR/after.xml" wizard_progress)"
  assert_absent "no marker was written" "setup_wizard" "$(adb shell run-as $PKG ls shared_prefs | tr -d '\r')"
  seen="$(walk_not_now "$ROW_DIR/walk" | tr '\n' ' ')"; note "steps: $seen"
  assert_absent "setup:photos nowhere in the run" "setup:photos" "$seen"
  end_row ;;

lmk)
  row_begin EDGE_LMK "a low-memory kill while a permission dialog is up"
  start_e2
  walk_to setup:photos "$ROW_DIR/w" >/dev/null
  p0="$(pid)"
  tap_node "$ROW_DIR/w-at.xml" wizard_action; sleep 2.5
  assert_contains "the dialog is up" "permissioncontroller" "$(resumed_activity)"
  adb shell am kill "$PKG"; sleep 2
  record "am kill with the dialog up (the doc's form): pid before / after" "$p0 / $(pid)"
  # am kill only kills a process Android already counts as killable, and with the dialog over it the shell is not (run 1:
  # the same pid survived). A low-memory kill is simulated for real: the process kills itself as its own uid (run-as, a
  # debug build), while the dialog stays up.
  adb shell run-as "$PKG" kill -9 "$p0"; sleep 2
  assert_ne "the shell's process died with the dialog up" "$p0" "$(pid)"
  assert_contains "the dialog is still up" "permissioncontroller" "$(resumed_activity)"
  perm_tap "$ROW_DIR/dialog.xml" allow_all_button "allow all"; sleep 3
  adb shell input keyevent KEYCODE_HOME; sleep 5
  p2="$(pid)"
  assert_ne "a new process after returning" "$p0" "$p2"
  assert_absent "no finished marker" "setup_wizard" "$(adb shell run-as $PKG ls shared_prefs | tr -d '\r')"
  k="$(cur_step after)"
  assert_eq "the wizard re-derives from live state: setup:notifications first" "setup:notifications" "$k"
  assert_eq "one fewer N: Step 1 of 19" "Step 1 of 19" "$(ntext "$ROW_DIR/after.xml" wizard_progress)"
  assert_contains "the dialog's answer is read from live state: READ_MEDIA_IMAGES granted" "READ_MEDIA_IMAGES: granted=true" "$(adb shell dumpsys package $PKG | grep 'READ_MEDIA_IMAGES: granted' | head -1)"
  seen="$(walk_not_now "$ROW_DIR/walk" | tr '\n' ' ')"; note "steps: $k $seen"
  assert_absent "setup:photos is not a step any more" "setup:photos" "$k $seen"
  end_row ;;

rotation)
  row_begin EDGE_ROTATION "rotation while a step shows: portrait-locked, not recreated"
  start_e2
  dump_ui "$ROW_DIR/before.xml"; tap_node "$ROW_DIR/before.xml" wizard_not_now; sleep 1.5
  k0="$(cur_step before2)"; p0="$(pid)"
  MARK="$(ring_mark)"
  r0="$(adb shell settings get system accelerometer_rotation | tr -d '\r')"; u0="$(adb shell settings get system user_rotation | tr -d '\r')"
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1; sleep 3
  assert_eq "the same step after the rotation request" "$k0" "$(cur_step rotated)"
  assert_eq "the same process" "$p0" "$(pid)"
  start_slice "$MARK" "rotation"
  assert_absent "StartActivity not recreated" "[start] StartActivity created" "$SLICE"
  adb shell settings put system user_rotation "${u0/null/0}"; adb shell settings put system accelerometer_rotation "${r0/null/0}"
  end_row ;;

dismiss)
  row_begin EDGE_DISMISS "the runtime dialog dismissed by Back or a tap outside: the step stays; Not now advances"
  # Each dismissal starts from its own fresh E2 state: a dismissed first request reads to Android's API like "will no
  # longer ask" (no rationale), so after one the button is "Open app info" (phase 03's rule, recorded) and a second
  # request would no longer raise the dialog (run 1).
  for how in back outside; do
    start_e2
    walk_to setup:photos "$ROW_DIR/w-$how" >/dev/null
    tap_node "$ROW_DIR/w-$how-at.xml" wizard_action; sleep 2.5
    assert_contains "($how) the dialog is up" "permissioncontroller" "$(resumed_activity)"
    if [ "$how" = back ]; then adb shell input keyevent KEYCODE_BACK; else adb shell input tap 540 150; fi
    sleep 2.5
    record "($how) resumed after the dismissal" "$(resumed_activity)"
    if [ "$(resumed_activity | grep -c permissioncontroller)" = 1 ]; then
      # Codex gate review B6: a Back after a failed outside tap proves Back, not the outside case; no PASS line for it.
      record "($how) NOT PROVEN" "the dialog does not dismiss on an outside tap on this image; left to P1 on the phone"
      adb shell input keyevent KEYCODE_BACK; sleep 2
      continue
    fi
    assert_eq "($how) the Photos step stays" "setup:photos" "$(cur_step "d-$how")"
    record "($how) the button after the dismissal" "$(ntext "$ROW_DIR/d-$how.xml" wizard_action)"
    tap_node "$ROW_DIR/d-$how.xml" wizard_not_now; sleep 1.5
    assert_eq "($how) Not now advances" "setup:music" "$(cur_step "n-$how")"
  done
  end_row ;;

double_tap)
  row_begin EDGE_DOUBLE_TAP "two quick taps on the step's button: one dialog, no crash"
  start_e2
  walk_to setup:photos "$ROW_DIR/w" >/dev/null
  p0="$(pid)"
  read -r l t r b <<<"$(bounds "$ROW_DIR/w-at.xml" wizard_action)"
  x=$(( (l + r) / 2 )); y=$(( (t + b) / 2 ))
  adb shell "input tap $x $y; sleep 0.1; input tap $x $y"; sleep 3
  tasks="$(adb shell dumpsys activity activities | grep -c 'GrantPermissionsActivity')"
  note "GrantPermissionsActivity records: $tasks"
  adb shell dumpsys activity activities | grep -E 'Task\{|GrantPermissions' > "$ROW_DIR/activities.txt"
  # The dialog opens in the caller's task, so the check is one GrantPermissionsActivity record (run 1 counted tasks).
  assert_eq "one permission dialog" "1" "$(adb shell dumpsys activity activities | grep -cE 'Hist .*GrantPermissionsActivity')"
  assert_eq "the launcher's pid unchanged (no crash)" "$p0" "$(pid)"
  perm_tap "$ROW_DIR/dialog.xml" deny_button "don.t allow"; sleep 2
  end_row ;;

partial_photos)
  row_begin EDGE_PARTIAL_PHOTOS "Photos answered Select photos: PARTIAL advances, the checklist row reads Partial"
  start_e2
  walk_to setup:photos "$ROW_DIR/w" >/dev/null
  MARK="$(ring_mark)"
  tap_node "$ROW_DIR/w-at.xml" wizard_action; sleep 2.5
  perm_tap "$ROW_DIR/dialog.xml" allow_selected_button "limited access|select photos"; sleep 3
  dump_ui "$ROW_DIR/picker.xml"; screencap "$ROW_DIR/picker.png"
  # the photo picker: choose the first item, then its Done / Allow button
  python3 - "$ROW_DIR/picker.xml" > "$ROW_DIR/picker-taps.txt" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
def c(n):
    x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", n.get("bounds") or "0 0 0 0")); return f"{(x1+x2)//2} {(y1+y2)//2}"
items = [n for n in root.iter("node") if re.search(r"^(Photo|Image|Video)", n.get("content-desc", ""))]
done = [n for n in root.iter("node") if re.search(r"^(done|allow|add)$", n.get("text", ""), re.I)]
print(c(items[0]) if items else "")
print(c(done[0]) if done else "")
PY
  it="$(sed -n 1p "$ROW_DIR/picker-taps.txt")"
  [ -n "$it" ] && { adb shell input tap $it; sleep 1.5; }
  dump_ui "$ROW_DIR/picker2.xml"
  dn="$(python3 - "$ROW_DIR/picker2.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if re.search(r"^(done|allow|add|allow access)", n.get("text", ""), re.I):
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", n.get("bounds") or "0 0 0 0")); print((x1+x2)//2, (y1+y2)//2); break
PY
)"
  [ -n "$dn" ] && { adb shell input tap $dn; sleep 3; }
  note "picker item tap [$it], done tap [$dn]"
  k="$(cur_step after)"
  assert_eq "the Photos step advanced" "setup:music" "$k"
  start_slice "$MARK" "select photos"
  assert_contains "[wizard] step setup:photos: partial" "[wizard] step setup:photos: partial" "$SLICE"
  open_checklist; dump_ui "$ROW_DIR/checklist.xml"
  assert_eq "checklist:photos:partial" "yes" "$(has_node "$ROW_DIR/checklist.xml" checklist:photos:partial)"
  end_row ;;

partial_bgloc)
  row_begin EDGE_PARTIAL_BGLOC "background location answered While using the app: PARTIAL stays; Not now advances"
  start_e2
  walk_to tess:background_location "$ROW_DIR/w" >/dev/null
  MARK="$(ring_mark)"
  tap_node "$ROW_DIR/w-at.xml" wizard_action; sleep 2.5
  perm_tap "$ROW_DIR/dialog.xml" allow_foreground_only_button "while using"; sleep 3
  assert_eq "the step stays" "tess:background_location" "$(cur_step after)"
  start_slice "$MARK" "while using"
  assert_contains "[wizard] step tess:background_location: partial" "[wizard] step tess:background_location: partial" "$SLICE"
  tap_node "$ROW_DIR/after.xml" wizard_not_now; sleep 1.5
  assert_eq "Not now advances" "tess:call_log" "$(cur_step next)"
  end_row ;;

partial_calendar)
  row_begin EDGE_PARTIAL_CALENDAR "tess:calendar with read only: PARTIAL stays with its partial line; Not now advances"
  # Read only cannot be answered in Android's dialog (WRITE auto-grants once READ is held), so it is set up the way a
  # user who once said "Don't allow" to the write would leave it: READ granted, WRITE denied and user-fixed.
  start_e2
  adb shell pm grant $PKG android.permission.READ_CALENDAR
  adb shell pm set-permission-flags $PKG android.permission.WRITE_CALENDAR user-set user-fixed
  resume_start
  walk_to tess:calendar "$ROW_DIR/w" >/dev/null
  MARK="$(ring_mark)"
  tap_node "$ROW_DIR/w-at.xml" wizard_action; sleep 3
  record "after the request" "$(resumed_activity)"
  # WRITE is denied for good, so Tess's permission page opens app info at once (phase 03's rule): Back returns.
  [ "$(resumed_activity | grep -c 'com.android.settings')" = 1 ] && { adb shell input keyevent KEYCODE_BACK; sleep 2.5; }
  assert_eq "the step stays" "tess:calendar" "$(cur_step after)"
  start_slice "$MARK" "read only"
  assert_contains "[wizard] step tess:calendar: partial" "[wizard] step tess:calendar: partial" "$SLICE"
  tap_node "$ROW_DIR/after.xml" wizard_not_now; sleep 1.5
  assert_eq "Not now advances" "tess:sms_send" "$(cur_step next)"
  end_row ;;

location_off)
  row_begin EDGE_LOCATION_OFF "both location permissions held, device location off"
  leave_home; adb shell pm clear "$PKG" >/dev/null; provision_no_marker lo
  adb shell cmd location set-location-enabled false; sleep 1
  MARK="$(ring_mark)"; resume_start
  start_slice "$MARK" "location off, nothing else missing"
  assert_contains "PARTIAL never summons the wizard: not shown: core held" "[wizard] not shown: core held" "$SLICE"
  adb shell appops set "$PKG" GET_USAGE_STATS ignore
  adb shell input keyevent KEYCODE_HOME; sleep 4
  walk_to tess:background_location "$ROW_DIR/w" >/dev/null
  assert_eq "inside a run the PARTIAL row is a step" "tess:background_location" "$(wiz_step "$ROW_DIR/w-at.xml")"
  MARK="$(ring_mark)"
  tap_node "$ROW_DIR/w-at.xml" wizard_action; sleep 3
  for i in 1 2 3; do [ "$(resumed_activity | grep -c app.tileshell/.StartActivity)" = 1 ] && break; adb shell input keyevent KEYCODE_BACK; sleep 2; done
  assert_eq "after Allow all the time the step stays" "tess:background_location" "$(cur_step after)"
  start_slice "$MARK" "all the time with location off"
  assert_contains "[wizard] step tess:background_location: partial" "[wizard] step tess:background_location: partial" "$SLICE"
  MARK="$(ring_mark)"
  tap_node "$ROW_DIR/after.xml" wizard_not_now; sleep 1.5
  start_slice "$MARK" "not now"
  assert_contains "[wizard] step tess:background_location: not now" "[wizard] step tess:background_location: not now" "$SLICE"
  adb shell cmd location set-location-enabled true
  end_row ;;

kb_dismiss)
  row_begin EDGE_KB_DISMISS "the keyboard picker dismissed without a choice: the step stays"
  leave_home; adb shell pm clear "$PKG" >/dev/null; provision_no_marker kb
  leave_home; adb shell am force-stop "$PKG"; adb shell input keyevent KEYCODE_HOME; sleep 5
  assert_eq "the keyboard step (the stop deselected it)" "setup:keyboard_selected" "$(cur_step first)"
  tap_node "$ROW_DIR/first.xml" wizard_action; sleep 2
  dump_ui "$ROW_DIR/picker.xml"
  assert_contains "the input-method picker is up" "Tessera keyboard" "$(cat "$ROW_DIR/picker.xml")"
  adb shell input keyevent KEYCODE_BACK; sleep 2
  assert_eq "dismissed: the step stays" "setup:keyboard_selected" "$(cur_step after)"
  assert_ne "the keyboard is not selected" "$KEYBOARD" "$(adb shell settings get secure default_input_method | tr -d '\r')"
  end_row ;;

home_once)
  row_begin EDGE_HOME_ONCE "the Default Home step when the shell was opened Just once from the chooser"
  # Codex gate review B7: entered through Android's own chooser (Home pressed with no default), and the return from the
  # role sheet is read as it lands — no explicit launch and no Home press until the step's last check.
  leave_home; adb shell pm clear "$PKG" >/dev/null; provision_no_marker home
  adb shell cmd role remove-role-holder android.app.role.HOME "$PKG"
  # With no holder, the preferred mapping set-home-activity left still resolves Home to the shell
  # (BUILD_START/home-chooser-probe); clearing it is what makes Home ask.
  adb shell cmd package clear-package-preferred-activities "$PKG"
  adb shell am force-stop "$PKG"
  record "HOME role holder after removing the shell" "$(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"
  assert_contains "Home resolves to the chooser" "ResolverActivity" "$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
  # The process itself may be up again (the notification listener rebinds after the force-stop, run 3); no activity is. The
  # force-stop removes the record asynchronously (run 4 read it mid-removal; BUILD_START/home-prestate-probe: gone by +0.5 s),
  # so the check waits up to 5 s for it.
  n=1; for i in 1 2 3 4 5 6 7 8 9 10; do n="$(start_records | grep -c .)"; [ "$n" = 0 ] && break; sleep 0.5; done
  assert_eq "no StartActivity before Home (the force-stop's removal waited for, up to 5 s)" "0" "$n"
  pick() { python3 - "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if re.fullmatch(sys.argv[2], n.get("text", ""), re.I):
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", n.get("bounds") or "0 0 0 0")); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
  }
  MARK="$(ring_mark)"
  adb shell input keyevent KEYCODE_HOME; sleep 3
  assert_contains "Home: Android's chooser" "ResolverActivity" "$(resumed_activity)"
  dump_ui "$ROW_DIR/chooser.xml"; screencap "$ROW_DIR/chooser.png"
  assert_eq "the chooser's title" "Select a Home app" "$(ntext "$ROW_DIR/chooser.xml" android:id/title)"
  xy="$(pick "$ROW_DIR/chooser.xml" Tessera)"; [ -n "$xy" ] && adb shell input tap $xy; sleep 1
  dump_ui "$ROW_DIR/chooser2.xml"; xy="$(pick "$ROW_DIR/chooser2.xml" 'just once')"; [ -n "$xy" ] && adb shell input tap $xy; sleep 4
  assert_eq "Just once: Start resumed" "app.tileshell/.StartActivity" "$(resumed_activity)"
  p0="$(pid)"; assert_ne "Just once: the shell is running" "" "$p0"
  record "HOME role holder after Just once" "$(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"
  k="$(cur_step first)"
  assert_eq "Default Home is step 1" "setup:home" "$k"
  assert_eq "its button: Set as default" "Set as default" "$(ntext "$ROW_DIR/first.xml" wizard_action)"
  prog0="$(ntext "$ROW_DIR/first.xml" wizard_progress)"; rec0="$(start_records)"
  record "the step's progress / StartActivity's record" "$prog0 / $rec0"
  start_slice "$MARK" "Just once"
  assert_contains "the run begins with the Home step" "[wizard] shown: missing=setup:home" "$SLICE"
  tap_node "$ROW_DIR/first.xml" wizard_action; sleep 3
  dump_ui "$ROW_DIR/sheet.xml"; screencap "$ROW_DIR/sheet.png"
  record "the role sheet on the AVD" "$(resumed_activity)"
  xy="$(pick "$ROW_DIR/sheet.xml" cancel)"; [ -n "$xy" ] && adb shell input tap $xy; sleep 3
  assert_eq "cancelled: Start is back in front" "app.tileshell/.StartActivity" "$(resumed_activity)"
  assert_eq "cancelled: the step stays" "setup:home" "$(cur_step after)"
  tap_node "$ROW_DIR/after.xml" wizard_action; sleep 3; dump_ui "$ROW_DIR/sheet2.xml"
  other_name="$(python3 - "$ROW_DIR/sheet2.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
t = [n.get("text", "") for n in ET.parse(sys.argv[1]).getroot().iter("node") if n.get("text")]
print(next((x for x in t if x not in ("Tessera", "CANCEL", "SET AS DEFAULT") and not x.startswith("Set ")), ""))
PY
)"
  record "another launcher offered" "${other_name:-none}"
  xy="$(pick "$ROW_DIR/sheet2.xml" "$other_name")"; [ -n "$xy" ] && adb shell input tap $xy; sleep 1
  dump_ui "$ROW_DIR/sheet3.xml"; xy="$(pick "$ROW_DIR/sheet3.xml" 'set as default')"; [ -n "$xy" ] && adb shell input tap $xy; sleep 3
  record "HOME role holder after choosing another launcher" "$(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"
  # The immediate return, read before any launch or Home press. The permission controller launches the newly chosen Home
  # itself (BUILD_START/home-return-probe: choosing Tessera under Settings > Default apps > Home app brings Tessera forward the
  # same way), so on the AVD the other launcher is in front at once (run 3); the wizard must still be alive behind it.
  record "another launcher chosen: in front on return" "$(resumed_activity)"
  assert_eq "another launcher chosen: StartActivity still alive behind it (the same activity record)" "$rec0" "$(start_records)"
  assert_eq "another launcher chosen: the same process" "$p0" "$(pid)"
  start_slice "$MARK" "the return"
  assert_eq "one StartActivity created since Home" "1" "$(grep -c '\[start\] StartActivity created' <<<"$SLICE")"
  assert_eq "one [wizard] shown line since Home (the run was not restarted)" "1" "$(grep -c '\[wizard\] shown' <<<"$SLICE")"
  assert_absent "no finished marker line" "[wizard] finished" "$SLICE"
  assert_absent "no skip line" "[wizard] skip" "$SLICE"
  # Back to Tessera: the HOME intent to the shell's own activity reaches the live one (onNewIntent; the wizard ignores Home). A
  # product that had ended the run on the return would show no wizard, or a fresh run with a second "[wizard] shown" line.
  adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n "$PKG/.StartActivity" >/dev/null; sleep 3
  assert_eq "reopened: Start in front" "app.tileshell/.StartActivity" "$(resumed_activity)"
  assert_eq "reopened: the same activity record (not recreated)" "$rec0" "$(start_records)"
  assert_eq "reopened: the step stays" "setup:home" "$(cur_step after2)"
  assert_eq "reopened: the same progress caption" "$prog0" "$(ntext "$ROW_DIR/after2.xml" wizard_progress)"
  assert_eq "reopened: the same process" "$p0" "$(pid)"
  start_slice "$MARK" "reopened"
  assert_eq "still one StartActivity created" "1" "$(grep -c '\[start\] StartActivity created' <<<"$SLICE")"
  assert_eq "still one [wizard] shown line" "1" "$(grep -c '\[wizard\] shown' <<<"$SLICE")"
  adb shell input keyevent KEYCODE_HOME; sleep 3
  record "Home pressed afterwards: resumed" "$(resumed_activity)"
  adb shell cmd role add-role-holder android.app.role.HOME "$PKG"
  adb shell cmd package set-home-activity "$PKG/$PKG.StartActivity" >/dev/null
  end_row ;;

light)
  row_begin EDGE_LIGHT "Light theme and a Start background set before the run: the wizard follows the theme"
  e2_state
  adb shell run-as $PKG cat shared_prefs/start_theme.xml > "$ROW_DIR/p-in.xml" 2>/dev/null
  [ -s "$ROW_DIR/p-in.xml" ] || printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map />\n' > "$ROW_DIR/p-in.xml"
  python3 "$QAROOT/phase-01/scripts/prefs_edit.py" "$ROW_DIR/p-in.xml" theme string LIGHT > "$ROW_DIR/p-1.xml"
  python3 "$QAROOT/phase-01/scripts/prefs_edit.py" "$ROW_DIR/p-1.xml" background string android.resource://app.tileshell/drawable/preset_lumia > "$ROW_DIR/p-2.xml"
  adb shell am force-stop $PKG
  adb shell "run-as $PKG sh -c 'cat > shared_prefs/start_theme.xml'" < "$ROW_DIR/p-2.xml"
  adb shell input keyevent KEYCODE_HOME; sleep 5
  assert_eq "the wizard shows" "setup:notifications" "$(cur_step first)"
  screencap "$ROW_DIR/wizard-light.png"
  bg="$(python3 -c "from PIL import Image; im=Image.open('$ROW_DIR/wizard-light.png').convert('RGB'); print(*im.getpixel((540,1600)))")"
  note "page background at (540,1600): $bg"
  assert_eq "the page background is the Light theme's white" "255 255 255" "$bg"
  end_row ;;

custom_before)
  row_begin EDGE_CUSTOM_BEFORE "the presets page on an install whose items were changed in Settings: Custom selected, Done leaves them"
  leave_home; adb shell pm clear "$PKG" >/dev/null; provision_no_marker cb
  adb shell am start -n "$PKG/.settings.SettingsActivity" --activity-clear-task --es page START_THEME >/dev/null 2>&1; sleep 3
  scroll_to_node "$ROW_DIR/theme.xml" "accent:Seafoam" 8
  tap_node "$ROW_DIR/theme.xml" "accent:Seafoam"; sleep 2
  before="$(adb shell run-as $PKG cat shared_prefs/start_theme.xml | tr -d '\r')"
  assert_contains "a user change reads custom" '<string name="theme_preset">custom</string>' "$before"
  adb shell appops set "$PKG" GET_USAGE_STATS ignore
  adb shell input keyevent KEYCODE_HOME; sleep 4
  walk_not_now "$ROW_DIR/walk" > "$ROW_DIR/walk.txt"
  last="$(ls -t "$ROW_DIR"/walk-*.xml | head -1)"
  assert_eq "preset:Custom selected on the presets page" "true" "$(node_checked "$last" preset:Custom)"
  tap_node "$last" wizard_done; sleep 3
  after="$(adb shell run-as $PKG cat shared_prefs/start_theme.xml | tr -d '\r')"
  assert_eq "Done leaves every item as it was" "$(echo "$before" | sort)" "$(echo "$after" | sort)"
  end_row ;;

battery_saver)
  row_begin EDGE_BATTERY_SAVER "a preset applied under battery saver: phase 13's rule wins"
  restore_fresh start
  adb shell am start -n "$PKG/.settings.SettingsActivity" --activity-clear-task --es page START_THEME >/dev/null 2>&1; sleep 3
  dump_ui "$ROW_DIR/t0.xml"; tap_node "$ROW_DIR/t0.xml" "theme_preset:Default"; sleep 2   # acrylic on first
  # Phase 13's order checks battery saver before the setting, so its line is written when saver comes on (run 1 marked
  # after that); the preset is then applied under it.
  MARK="$(ring_mark)"
  battery_saver_on
  dump_ui "$ROW_DIR/t1.xml"; tap_node "$ROW_DIR/t1.xml" "theme_preset:HAL"; sleep 3
  listener_slice "$MARK" "HAL under battery saver"
  assert_contains "[fluent] acrylic=off reason=battery-saver" "[fluent] acrylic=off reason=battery-saver" "$SLICE"
  assert_absent "acrylic stays off under saver after HAL (no acrylic=on)" "[fluent] acrylic=on" "$SLICE"
  assert_contains "transparency_effects still written as the preset says (true)" '<boolean name="transparency_effects" value="true" />' "$(adb shell run-as $PKG cat shared_prefs/start_theme.xml | tr -d '\r')"
  MARK="$(ring_mark)"
  battery_saver_off; sleep 3
  listener_slice "$MARK" "battery saver off"
  assert_contains "battery saver off: acrylic follows the preset (on)" "[fluent] acrylic=on" "$SLICE"
  end_row ;;

no_picture)
  row_begin EDGE_NO_PICTURE "fault injection: a QA build without the preset pictures; HAL applies with no picture"
  NP="${NOPICS_APK:?NOPICS_APK: the same commit built with the six WebP removed}"
  assert_eq "the fault build carries no preset picture (removal verified)" "0" "$(unzip -l "$NP" | grep -c 'drawable-nodpi[^ ]*/preset_')"
  adb install -r "$NP" > "$ROW_DIR/install-np.txt" 2>&1
  assert_contains "fault build installed" "Success" "$(cat "$ROW_DIR/install-np.txt")"
  adb shell ime set "$KEYBOARD" >/dev/null
  adb shell input keyevent KEYCODE_HOME; sleep 4
  adb shell am start -n "$PKG/.settings.SettingsActivity" --activity-clear-task --es page START_THEME >/dev/null 2>&1; sleep 3
  MARK="$(ring_mark)"
  dump_ui "$ROW_DIR/t.xml"; tap_node "$ROW_DIR/t.xml" "theme_preset:HAL"; sleep 2
  start_slice "$MARK" "HAL without its picture"
  assert_contains "[theme] preset HAL applied: no picture" "[theme] preset HAL applied: no picture" "$SLICE"
  assert_absent "background absent" 'name="background"' "$(adb shell run-as $PKG cat shared_prefs/start_theme.xml | tr -d '\r')"
  adb install -r "$APK" > "$ROW_DIR/install-normal.txt" 2>&1
  assert_contains "the normal APK restored" "Success" "$(cat "$ROW_DIR/install-normal.txt")"
  end_row ;;

profile)
  row_begin EDGE_PROFILE "a work profile present: no profile grant is a checklist row, the step list is E2's"
  out="$(adb shell pm create-user --profileOf 0 --managed work 2>&1 | tr -d '\r')"; note "create-user: $out"
  uid="$(echo "$out" | grep -oE 'id [0-9]+' | grep -oE '[0-9]+')"
  [ -n "$uid" ] && adb shell am start-user "$uid" >/dev/null
  record "work profile user id" "${uid:-none}"
  e2_state
  MARK="$(ring_mark)"
  adb shell input keyevent KEYCODE_HOME; sleep 5
  start_slice "$MARK" "shown with a profile"
  shown="$(printf '%s\n' "$SLICE" | grep -oE '\[wizard\] shown: missing=[^ ]*' | sed 's/.*missing=//')"
  assert_eq "the step list equals E2's" "setup:notifications,setup:photos,setup:music,setup:calendar,setup:location,setup:usage,setup:keyboard_enabled,setup:keyboard_selected,setup:full_screen_alarms,setup:overlay,tess:assistant,tess:microphone,tess:contacts,tess:calendar,tess:sms_send,tess:call_phone,tess:background_location,tess:call_log,tess:sms_read" "$shown"
  [ -n "$uid" ] && adb shell pm remove-user "$uid" >/dev/null
  end_row ;;

keyguard)
  row_begin EDGE_KEYGUARD "the keyguard enabled: the wizard never shows over it; after unlock it does"
  e2_state
  adb shell locksettings set-disabled false
  adb shell input keyevent KEYCODE_HOME; sleep 3
  adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 2
  dump_ui "$ROW_DIR/locked.xml"; screencap "$ROW_DIR/locked.png"
  assert_eq "locked: no wizard_page over the keyguard" "no" "$(has_node "$ROW_DIR/locked.xml" wizard_page)"
  assert_contains "locked: the keyguard is what shows" "systemui" "$(cat "$ROW_DIR/locked.xml")"
  adb shell wm dismiss-keyguard; sleep 1
  adb shell input swipe 540 1900 540 600 300; sleep 3
  dump_ui "$ROW_DIR/unlocked.xml"
  assert_eq "after unlock: the wizard shows" "yes" "$(has_node "$ROW_DIR/unlocked.xml" wizard_page)"
  adb shell locksettings set-disabled true
  assert_eq "wake_device" "Awake" "$(wake_device)"
  end_row ;;

*) echo "unknown case $CASE" >&2; exit 2 ;;
esac
