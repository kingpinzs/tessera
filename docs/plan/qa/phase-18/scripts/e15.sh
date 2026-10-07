#!/usr/bin/env bash
# Phase 18 E15 — the wizard step added: phase 12 E14's three-part template (qa/phase-12/scripts/e14.sh; C-15), run for
# `setup:files` with this phase's revoke / grant (appops MANAGE_EXTERNAL_STORAGE default / allow) and its checklist row.
# The template's own helpers are used as they are (qa/phase-12/scripts/p12.sh: leave_home, provision_no_marker,
# restore_fresh, start_slice, ntext, open_checklist), sourced beside this phase's floor; the evidence lands here (E15/).
#
#   (a) `pm clear app.tileshell` → `PROVISION_FINISH_WIZARD=0 qa/phase-03/scripts/provision.sh` → `appops set … default`
#       → Home: `wizard_step:setup:files` present, its `wizard_why` equal to Decisions' line ("Files can browse
#       everything on this phone. Without it Files sees nothing."), `wizard_progress` "Step 1 of 2" (the step and the
#       presets page), `[wizard] shown: missing=setup:files`; its action (`wizard_action`) STARTS the app's own all-files
#       page — the logcat START line naming `com.android.settings/.Settings$AppManageExternalStorageActivity` with the
#       per-app action (Q-18-4 (a); the START line, not top-resumed — the Decisions entry below it), the page's own
#       texts naming the shell (logcat redacts the line's data); `appops set … allow` and the resume (Back from that
#       page) → the step gone, `wizard_presets` shows, `[wizard] step setup:files: granted`.
#   (b) `pm clear` → `provision.sh` (marker written) → Home → no `wizard_page`; a resume logs `[wizard] not shown: core
#       held` (phase 12 E1 on this build).
#   (c) the finished-install rule: from (b), `appops set … default` → a resume → no `wizard_page`, `[wizard] not shown:
#       finished`, the checklist row `checklist:files:missing`; `appops set … allow` (RV12).
#
# THE WIPE AND THE RESTORE. `pm clear` wipes the shell's private data. Before the first wipe the row saves it whole (a
# tar of files/ and shared_prefs/ through run-as, taken with the shell stopped, with every file's md5), the package
# list, the shell's granted permissions, its appops, the role holders and the listeners. Nothing is reinstalled by the
# row itself: provision.sh's own `adb install -r -g` installs the SAME gate APK file ($APK), and the installed base.apk's
# md5 is asserted against it afterwards. At the end, on (b)'s provisioned state: the shell stopped, the saved private
# data written back and every file's md5 asserted equal to the saved one; the package list, the granted permissions,
# the appops, the roles and the listeners asserted equal to the start's (a permission the wipe's re-provision granted
# that was not granted before is revoked again, and said so); then the phase baseline through layout_restore.
#
# Run LAST among this writer's rows. Changes on the device: everything above, restored. No root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/p18_a.sh"
P12S="$(cd "$P18/../phase-12/scripts" && pwd)"
. "$P12S/p12.sh"
STAMP_FILES="$STAMP_FILES $P12S/p12.sh $P12S/e14.sh $P18/../phase-03/scripts/provision.sh"
STEP="setup:files"
WHY="Files can browse everything on this phone. Without it Files sees nothing."
REVOKE="adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE default"
GRANT="adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow"
CHECK_ROW="checklist:files:missing"
WANT='com.android.settings/.Settings$AppManageExternalStorageActivity'
OWN_PAGE='text=Tessera text=0.1.0 text=Allow access to manage all files'
keep_earlier_run E15
row_begin E15 "the wizard step added: $STEP (phase 12 E14's three-part template)"
trap 'adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow' EXIT
LC0="$(lc_mark)"
APK_MD5="$(md5sum "$APK" | cut -c1-16)"
assert_eq "the doc's why line is the one in the phase doc's Decisions (read from the doc)" "1" "$(grep -cF "why line \"$WHY\"" "$REPO/docs/plan/phase-18-files.md")"

# ---- the device as found, saved
state_of() { # prefix — the facts the restore is held to
  q "pm list packages" | LC_ALL=C sort > "$ROW_DIR/$1-packages.txt"
  adb shell dumpsys package app.tileshell | tr -d '\r' | sed -n 's/^ *\(android\.permission\.[A-Z_]*\): granted=true.*/\1/p' | LC_ALL=C sort -u > "$ROW_DIR/$1-granted.txt"
  q "appops get app.tileshell" | sed 's/;.*//' | LC_ALL=C sort > "$ROW_DIR/$1-appops.txt"
  {
    for r in HOME ASSISTANT SMS; do echo "role $r: $(q "cmd role get-role-holders android.app.role.$r" | xargs)"; done
    echo "home activity: $(q "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN" | tail -1)"
    echo "notification listeners: $(q "settings get secure enabled_notification_listeners")"
    echo "enabled input methods: $(q "settings get secure enabled_input_methods")"
  } > "$ROW_DIR/$1-roles.txt"
  q "settings get secure default_input_method" > "$ROW_DIR/$1-ime.txt"
}
# Every private file's md5, by find's own -exec: a name with a space in it (the speech data's "voices/!v/Mr serious")
# split in two under `| xargs` and was listed as two errors instead of one md5 (run 1, kept as E15-run1).
private_md5s() { q "run-as app.tileshell sh -c 'find files shared_prefs -type f -exec md5sum {} +'" | LC_ALL=C sort -k2; }
log "--- the device as found"
rings_save
leave_home
adb shell am force-stop app.tileshell; sleep 1
state_of before
private_md5s > "$ROW_DIR/before-private.md5"
adb exec-out "run-as app.tileshell tar cf - files shared_prefs" > "$ROW_DIR/private-before.tar" 2> "$ROW_DIR/private-before.tar.err"
TAR_N="$(tar tf "$ROW_DIR/private-before.tar" 2>/dev/null | grep -vc '/$')"
record "saved: the shell's private data (files/ + shared_prefs/)" "$(stat -c%s "$ROW_DIR/private-before.tar") bytes, $TAR_N files; $(grep -c . "$ROW_DIR/before-private.md5") md5s"
assert_eq "the saved tar holds every private file the md5 list names" "$(grep -c . "$ROW_DIR/before-private.md5")" "$TAR_N"
assert_ge "…and is not empty (the layout, the wizard's marker and the Recent store are in it)" 3 "$(tar tf "$ROW_DIR/private-before.tar" | grep -cE 'files/start_layout.json|shared_prefs/setup_wizard.xml|files/files-recent.json')"
record "saved: packages / granted permissions / appops lines / default keyboard" "$(grep -c . "$ROW_DIR/before-packages.txt") / $(grep -c . "$ROW_DIR/before-granted.txt") / $(grep -c . "$ROW_DIR/before-appops.txt") / $(cat "$ROW_DIR/before-ime.txt")"
assert_eq "at the start the installed APK is the gate candidate" "$APK_MD5" "$(installed_apk_id)"
assert_contains "at the start the appop reads allow" "MANAGE_EXTERNAL_STORAGE: allow" "$(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE')"

# ------------------------------------------------------------------------------------------------- (a)
log "(a) pm clear -> PROVISION_FINISH_WIZARD=0 provision.sh -> revoke -> Home ($(date '+%H:%M:%S'))"
leave_home
adb shell pm clear "$PKG" >/dev/null
assert_eq "(a) pm clear emptied the private data (no start_layout.json, no marker)" "" "$(q "run-as app.tileshell sh -c 'ls files/start_layout.json shared_prefs/setup_wizard.xml 2>/dev/null'" | xargs)"
record "(a) the appop after pm clear (BUILD-NOTES: pm clear does not reset it)" "$(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE' | cut -d';' -f1)"
provision_no_marker a
assert_eq "(a) no-marker provision rc" "0" "$(cat "$ROW_DIR/provision-a.rc")"
assert_contains "(a) provision wrote no marker" "PROVISION_FINISH_WIZARD=0: no marker written" "$(cat "$ROW_DIR/provision-a.txt")"
assert_contains "(a) provision granted all-files access (its new line)" "all-files access: MANAGE_EXTERNAL_STORAGE: allow" "$(cat "$ROW_DIR/provision-a.txt")"
assert_eq "(a) the APK provision.sh installed is the gate candidate (md5 of base.apk)" "$APK_MD5" "$(installed_apk_id)"
eval "$REVOKE"
assert_contains "(a) the appop reads default" "MANAGE_EXTERNAL_STORAGE: default" "$(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE')"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_HOME; sleep 4
dump_ui "$ROW_DIR/a-step.xml"; screencap "$ROW_DIR/a-step.png"
assert_eq "(a) wizard_step:$STEP is present" "yes" "$(has_node "$ROW_DIR/a-step.xml" "wizard_step:$STEP")"
assert_eq "(a) its why line = Decisions' line" "$WHY" "$(ntext "$ROW_DIR/a-step.xml" wizard_why)"
assert_eq "(a) wizard_progress" "Step 1 of 2" "$(ntext "$ROW_DIR/a-step.xml" wizard_progress)"
record "(a) the step's title / its action's text" "$(ntext "$ROW_DIR/a-step.xml" wizard_title) / $(ntext "$ROW_DIR/a-step.xml" wizard_action)"
start_slice "$MARK" "(a) shown"
assert_contains "(a) shown with that step alone" "[wizard] shown: missing=$STEP" "$SLICE"
assert_eq "(a) …and no other step is named in that line" "[wizard] shown: missing=$STEP" "$(printf '%s\n' "$SLICE" | grep -oE '\[wizard\] shown: missing=[^ ]*' | head -1)"
# The step's action: it starts the app's own all-files page.
LC="$(lc_mark)"; sleep 1.1
tap_node "$ROW_DIR/a-step.xml" wizard_action; sleep 3
screencap "$ROW_DIR/a-action.png"
starts_since "$LC" | grep 'com.android.settings' > "$ROW_DIR/a-start.txt"
L="$(head -1 "$ROW_DIR/a-start.txt")"
note "(a) the action's START lines: $(tr '\n' ';' < "$ROW_DIR/a-start.txt")"
assert_contains "(a) the action: the START line names the per-app page (Q-18-4 (a))" "cmp=$WANT" "$L"
assert_contains "(a) the action: …with the per-app action" "act=android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION" "$L"
assert_contains "(a) the action: …and a package: URI (logcat redacts the rest)" "dat=package:" "$L"
dump_ui "$ROW_DIR/a-settings.xml"
assert_contains "(a) the action: the page is the shell's OWN all-files page (its label, version, the switch)" "$OWN_PAGE" "$(grep -o 'text="[^"]\+"' "$ROW_DIR/a-settings.xml" | xargs)"
assert_eq "(a) the action: the resumed package is Settings" "com.android.settings" "$(top_activity | cut -d/ -f1)"
eval "$GRANT"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_BACK; sleep 2.5
dump_ui "$ROW_DIR/a-granted.xml"; screencap "$ROW_DIR/a-granted.png"
assert_eq "(a) granted and resumed: Start is on top again" "app.tileshell/.StartActivity" "$(top_activity)"
assert_eq "(a) the step is gone" "no" "$(has_node "$ROW_DIR/a-granted.xml" "wizard_step:$STEP")"
assert_eq "(a) wizard_presets shows" "yes" "$(has_node "$ROW_DIR/a-granted.xml" wizard_presets)"
record "(a) the presets page's progress" "$(ntext "$ROW_DIR/a-granted.xml" wizard_progress)"
start_slice "$MARK" "(a) granted"
assert_contains "(a) [wizard] step $STEP: granted" "[wizard] step $STEP: granted" "$SLICE"

# ------------------------------------------------------------------------------------------------- (b)
log "(b) pm clear -> provision.sh (carries the grant, writes the marker) -> Home ($(date '+%H:%M:%S'))"
restore_fresh b
assert_eq "(b) provision rc" "0" "$(cat "$ROW_DIR/provision-b.rc")"
assert_contains "(b) provision wrote the marker" "marker: " "$(cat "$ROW_DIR/provision-b.txt")"
assert_contains "(b) provision's own check: a tap opens Cortana, the device is ready" "OK: a tap opens Cortana" "$(cat "$ROW_DIR/provision-b.txt")"
assert_eq "(b) the APK provision.sh installed is the gate candidate (md5 of base.apk)" "$APK_MD5" "$(installed_apk_id)"
dump_ui "$ROW_DIR/b-home.xml"
assert_eq "(b) no wizard_page; Start is shown" "no yes" "$(has_node "$ROW_DIR/b-home.xml" wizard_page) $(has_node "$ROW_DIR/b-home.xml" start_page)"
MARK="$(ring_mark)"
adb shell am start -n "$PKG/.settings.SettingsActivity" >/dev/null 2>&1; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 3
start_slice "$MARK" "(b) resume"
assert_contains "(b) [wizard] not shown: core held" "[wizard] not shown: core held" "$SLICE"
absent_in "(b) …and no [wizard] shown line" "[wizard] shown:" "$SLICE"

# ------------------------------------------------------------------------------------------------- (c)
log "(c) the finished-install rule: revoke -> a resume ($(date '+%H:%M:%S'))"
record "(c) the shell's pid before the revoke" "$(q 'pidof app.tileshell' | xargs)"
eval "$REVOKE"; sleep 2
record "(c) the shell's pid 2 s after it (the revoke kills the process)" "$(q 'pidof app.tileshell' | xargs)"
MARK="$(ring_mark)"          # a fresh MARK after the appop change (the floor, 4)
adb shell am start -n "$PKG/.settings.SettingsActivity" >/dev/null 2>&1; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 3
dump_ui "$ROW_DIR/c-home.xml"
assert_eq "(c) no wizard_page; Start is shown" "no yes" "$(has_node "$ROW_DIR/c-home.xml" wizard_page) $(has_node "$ROW_DIR/c-home.xml" start_page)"
start_slice "$MARK" "(c) resume"
assert_contains "(c) [wizard] not shown: finished" "[wizard] not shown: finished" "$SLICE"
absent_in "(c) …and no [wizard] shown line" "[wizard] shown:" "$SLICE"
open_checklist
scroll_to_node "$ROW_DIR/c-checklist.xml" "$CHECK_ROW" 10
assert_eq "(c) the checklist row Files reads missing ($CHECK_ROW)" "yes no" "$(has_node "$ROW_DIR/c-checklist.xml" "$CHECK_ROW") $(has_node "$ROW_DIR/c-checklist.xml" checklist:files:granted)"
eval "$GRANT"
trap - EXIT
assert_contains "(c) restore: the appop reads allow (RV12)" "MANAGE_EXTERNAL_STORAGE: allow" "$(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE')"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ------------------------------------------------------------------------------------------------- the restore
log "--- the restore: the saved private data back, the device's state against the start's ($(date '+%H:%M:%S'))"
assert_eq "no AndroidRuntime line names the shell since the row began" "0" "$(crash_since "$LC0")"
rings_save
leave_home
adb shell am force-stop app.tileshell; sleep 1
adb push "$ROW_DIR/private-before.tar" /data/local/tmp/p18-e15-private.tar > "$ROW_DIR/restore-push.out" 2>&1
adb shell chmod 644 /data/local/tmp/p18-e15-private.tar
cat > "$ROW_DIR/restore-private.sh" <<'SH'
cd /data/data/app.tileshell || exit 1
for d in files shared_prefs; do
  find $d -type f -exec rm -f {} + 2>/dev/null
  find $d -depth -mindepth 1 -type d -exec rmdir {} + 2>/dev/null
done
tar xf /data/local/tmp/p18-e15-private.tar && echo untarred
SH
adb push "$ROW_DIR/restore-private.sh" /data/local/tmp/p18-e15-restore.sh >/dev/null 2>&1
q "run-as app.tileshell sh /data/local/tmp/p18-e15-restore.sh" > "$ROW_DIR/restore-private.out"
adb shell am force-stop app.tileshell; sleep 0.5
private_md5s > "$ROW_DIR/after-private.md5"
q "rm -f /data/local/tmp/p18-e15-private.tar /data/local/tmp/p18-e15-restore.sh"
assert_contains "restore: the saved private data was written back (tar through run-as)" "untarred" "$(cat "$ROW_DIR/restore-private.out")"
if cmp -s "$ROW_DIR/before-private.md5" "$ROW_DIR/after-private.md5"; then
  _verdict PASS "restore: every private file is back, byte for byte (md5s, the shell stopped)" "$(grep -c . "$ROW_DIR/after-private.md5") files, no difference"
else
  _verdict FAIL "restore: every private file is back, byte for byte (md5s, the shell stopped)" "$(diff "$ROW_DIR/before-private.md5" "$ROW_DIR/after-private.md5" | grep -c '^[<>]') lines differ: $(diff "$ROW_DIR/before-private.md5" "$ROW_DIR/after-private.md5" | grep '^[<>]' | head -4 | xargs)"
fi
# A permission the re-provision granted that the shell did not hold at the start is revoked again.
state_of mid
EXTRA="$(comm -13 "$ROW_DIR/before-granted.txt" "$ROW_DIR/mid-granted.txt" | xargs)"
record "restore: permissions granted now that were not at the start (revoked again)" "${EXTRA:-none}"
for p in $EXTRA; do adb shell pm revoke app.tileshell "$p"; done
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 0.5
adb shell am force-stop app.tileshell; sleep 0.5
state_of after
for f in packages granted appops roles; do
  if cmp -s "$ROW_DIR/before-$f.txt" "$ROW_DIR/after-$f.txt"; then
    _verdict PASS "restore: the device's $f equal the start's" "$(grep -c . "$ROW_DIR/after-$f.txt") lines, no difference"
  else
    _verdict FAIL "restore: the device's $f equal the start's" "$(diff "$ROW_DIR/before-$f.txt" "$ROW_DIR/after-$f.txt" | grep '^[<>]' | head -6 | xargs)"
  fi
done
record "restore: the default keyboard at the start / now (a force-stop deselects the shell's keyboard, qa/phase-05/README.md)" "$(cat "$ROW_DIR/before-ime.txt") / $(cat "$ROW_DIR/after-ime.txt")"
assert_eq "restore: the installed APK is the gate candidate (md5 of base.apk)" "$APK_MD5" "$(installed_apk_id)"
if layout_restore "$BASELINE" > "$ROW_DIR/restore-layout.out" 2>&1; then _verdict PASS "restore: layout_restore of the phase baseline" "the shell came up on it"; else _verdict FAIL "restore: layout_restore of the phase baseline" "$(tail -1 "$ROW_DIR/restore-layout.out")"; fi
ensure_start
MARK="$(ring_mark)"
adb shell am start -n "$PKG/.settings.SettingsActivity" >/dev/null 2>&1; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 3
dump_ui "$ROW_DIR/end-home.xml"
assert_eq "restore: Start, no wizard" "yes no" "$(has_node "$ROW_DIR/end-home.xml" start_page) $(has_node "$ROW_DIR/end-home.xml" wizard_page)"
start_slice "$MARK" "the end"
assert_contains "restore: a resume says [wizard] not shown" "[wizard] not shown: " "$SLICE"
ensure_start
end_state
row_end
