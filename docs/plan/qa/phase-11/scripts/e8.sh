#!/usr/bin/env bash
# E8 — no-burst and close cases the fixtures drive (T11-15, T11-21, T11-24): (a) the shell not the HOME holder;
# (b) a disabled shortcut — Android deletes it, so the hold finds none, and a disable sent through the UI-less
# receiver while a burst is open closes it with `shortcuts changed`; (c) an uninstall with the burst open closes it
# with `removed`; (d) a shortcut that cannot start: nothing launches, Start comes back plain.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E8 "no burst when not HOME; disabled / removed / unstartable shortcuts"
seed_fixtures
restore baseline_layout.json

hold_b() { # tag -> dump <tag>.xml taken while held
  qdump "$ROW_DIR/$1-rest.xml"
  read -r X Y <<< "$(center "$ROW_DIR/$1-rest.xml" "tile:$B_KEY")"
  hold_down "$X" "$Y"; sleep 1.0; qdump "$ROW_DIR/$1.xml"; hold_up "$X" "$Y"; sleep 0.8
}

log "--- (a) the shell is not a shortcut host: edit mode, no burst ---"
# The HOME role alone is not enough on this AVD: the shell also holds ASSISTANT (phase 03's provision.sh), and
# Android makes the voice-interaction holder a shortcut host too, so with HOME removed the query still succeeds
# (first run of this row, 2026-09-23). Both roles are removed for the step and both restored.
adb shell cmd role remove-role-holder android.app.role.HOME app.tileshell >/dev/null 2>&1
adb shell cmd role remove-role-holder android.app.role.ASSISTANT app.tileshell >/dev/null 2>&1; sleep 1
note "HOME holders now: [$(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')] ASSISTANT holders now: [$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')]"
qdump "$ROW_DIR/nothome-rest.xml"
if [ "$(has_node "$ROW_DIR/nothome-rest.xml" start_page)" = no ]; then
  adb shell am start -n app.tileshell/.StartActivity >/dev/null 2>&1; sleep 3; qdump "$ROW_DIR/nothome-rest.xml"
fi
read -r X Y <<< "$(center "$ROW_DIR/nothome-rest.xml" "tile:$A_KEY")"
MARK="$(ring_mark)"; hold_down "$X" "$Y"; sleep 1.0; qdump "$ROW_DIR/nothome.xml"; hold_up "$X" "$Y"; sleep 0.8
assert_eq "(a) edit mode on" yes "$(has_node "$ROW_DIR/nothome.xml" edit_disc:unpin)"
assert_eq "(a) no quick_burst" no "$(has_node "$ROW_DIR/nothome.xml" quick_burst)"
assert_contains "(a) reason" "[quick] no burst on $A_KEY: not the shortcut host" "$(quick_since "$MARK")"
adb shell cmd role add-role-holder android.app.role.HOME app.tileshell >/dev/null 2>&1
adb shell cmd role add-role-holder android.app.role.ASSISTANT app.tileshell >/dev/null 2>&1
adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity >/dev/null 2>&1; sleep 1
assert_contains "(a) restored: the shell is HOME again" "app.tileshell" "$(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"
assert_contains "(a) restored: the shell is the assistant again" "app.tileshell" "$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
c6

log "--- (b) disabled: Android deletes an unpinned dynamic shortcut, so the hold finds none ---"
# tileclient-b's own section of dumpsys shortcut (`cmd shortcut get-shortcuts` printed only "Success" here, even with
# qa_dyn published — pass 3's positive control caught it).
b_shortcuts() { adb shell dumpsys shortcut | awk '/Package: app.tileshell.testclient.b /{f=1; print; next} f && /Package: /{f=0} f' | grep -E 'Package:|ShortcutInfo \{id='; }
verb_b_start reset
b_shortcuts > "$ROW_DIR/b-after-reset-shortcuts.txt" 2>&1
assert_contains "(b) positive control: after reset tileclient-b's section lists qa_dyn" "ShortcutInfo {id=qa_dyn" "$(cat "$ROW_DIR/b-after-reset-shortcuts.txt")"
note "disable (receiver): $(verb_b disable)"
b_shortcuts > "$ROW_DIR/b-after-disable-shortcuts.txt" 2>&1
MARK="$(ring_mark)"
hold_b disabled
assert_eq "(b) no quick_burst" no "$(has_node "$ROW_DIR/disabled.xml" quick_burst)"
S="$(quick_since "$MARK")"
assert_contains "(b) the query found none" "[quick] shortcuts for $B_PKG/app.tileshell.testclient.VerbActivity/0: 0 (0 shown)" "$S"
assert_contains "(b) reason" "[quick] no burst on $B_KEY: no shortcuts" "$S"
assert_eq "(b) tileclient-b's section lists no shortcut after the disable" 0 "$(grep -c 'ShortcutInfo' "$ROW_DIR/b-after-disable-shortcuts.txt")"
c6

log "--- (b) disable while a burst is open (no window comes to front): shortcuts changed ---"
verb_b_start reset
hold_b open-b
assert_eq "(b) burst open on tileclient-b" yes "$(has_node "$ROW_DIR/open-b.xml" quick_burst)"
MARK="$(ring_mark)"
note "disable (receiver): $(verb_b disable)"
sleep 1
qdump "$ROW_DIR/changed.xml"
assert_contains "(b) StartActivity still resumed" "app.tileshell/.StartActivity" "$(resumed)"
assert_eq "(b) no quick_burst" no "$(has_node "$ROW_DIR/changed.xml" quick_burst)"
assert_eq "(b) edit mode stays" yes "$(has_node "$ROW_DIR/changed.xml" edit_disc:unpin)"
assert_contains "(b) ring: shortcuts changed" "[quick] burst closed: shortcuts changed" "$(quick_since "$MARK")"
c6
verb_b_start reset

log "--- (c) uninstall tileclient-b with its burst open: the tile and the burst go (removed) ---"
hold_b open-c
assert_eq "(c) burst open" yes "$(has_node "$ROW_DIR/open-c.xml" quick_burst)"
MARK="$(ring_mark)"
adb uninstall "$B_PKG" >/dev/null 2>&1; sleep 2.5
qdump "$ROW_DIR/uninstalled.xml"
assert_eq "(c) the tile is gone" no "$(has_node "$ROW_DIR/uninstalled.xml" "tile:$B_KEY")"
assert_eq "(c) no quick_burst" no "$(has_node "$ROW_DIR/uninstalled.xml" quick_burst)"
assert_eq "(c) exactly one close line, removed" "1 [quick] burst closed: removed" \
  "$(quick_since "$MARK" | grep -F '[quick] burst closed:' | sed 's/.*\[quick\]/[quick]/' | sort | uniq -c | sed 's/^ *//')"
adb install -r -t "$B_APK" >/dev/null 2>&1
verb_b_start reset
restore baseline_layout.json

log "--- (d) a shortcut whose target cannot start: nothing launches, Start plays its entrance, no edit mode ---"
note "deadtarget (receiver): $(verb_b deadtarget)"
hold_b dead
assert_eq "(d) two satellites" yes "$(has_node "$ROW_DIR/dead.xml" quick_sat:1)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/dead.xml" quick_sat:1; sleep 3
qdump "$ROW_DIR/dead-after.xml"
assert_contains "(d) nothing else resumed" "app.tileshell/.StartActivity" "$(resumed)"
assert_eq "(d) back on start_page" yes "$(has_node "$ROW_DIR/dead-after.xml" start_page)"
assert_eq "(d) no edit mode" no "$(grep -q 'resource-id="edit_disc' "$ROW_DIR/dead-after.xml" && echo yes || echo no)"
S="$(ring_since "$MARK")"
assert_contains "(d) startShortcut failed, ActivityNotFoundException" "[quick] tap satellite 1 $B_PKG/qa_dead: startShortcut failed android.content.ActivityNotFoundException" "$S"
assert_contains "(d) Start played its entrance" "[motion] start entrance" "$S"
screencap "$ROW_DIR/dead-after.png"
verb_b_start reset
row_end
