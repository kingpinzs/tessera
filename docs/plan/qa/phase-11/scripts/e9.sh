#!/usr/bin/env bash
# E9 — a managed profile and a private space (T11-20): the work tile bursts with ITS profile's shortcuts and runs
# them as that user; locked through the shell's own group action (phase 01 E18's route — `pm set-quiet-mode` does
# not exist here) it opens no burst (`profile quiet`) and bursts again once unlocked; a locked private space gives
# the same reason. Both profiles are removed at the end.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E9 "work profile bursts as its own user; quiet work and locked private space give profile quiet"
seed_fixtures
restore baseline_layout.json

to_applist() { ensure_start_page; adb shell input swipe 900 1200 150 1200 250; sleep 2; }
# The group's rows follow its profile_group node; the fixture's row there is the one below it.
profile_row() { # dump group(work|private) -> "x y" of the fixture's row in that group, or nothing
  python3 - "$1" "$2" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
def nodes(rid):
    out = []
    for n in re.finditer(r"<node[^>]*>", s):
        n = n.group(0)
        if 'resource-id="%s"' % rid in n:
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
            out.append(tuple(map(int, b.groups())))
    return out
g = nodes("profile_group:" + sys.argv[2])
rows = [r for r in nodes("applist_row:app.tileshell.testclient.a") if g and r[1] > g[0][1]]
if rows: print((rows[0][0] + rows[0][2]) // 2, (rows[0][1] + rows[0][3]) // 2)
PY
}
scroll_to_group_row() { # group -> leaves the app list scrolled so the row is on screen; prints "x y"
  local i xy
  for i in 1 2 3 4 5 6 7 8 9 10; do
    qdump "$ROW_DIR/.applist.xml"
    xy="$(profile_row "$ROW_DIR/.applist.xml" "$1")"
    [ -n "$xy" ] && { echo "$xy"; return 0; }
    adb shell input swipe 540 1800 540 700 300; sleep 1.2
  done
  return 1
}
pin_row() { # "x y": hold the row, tap Pin to Start
  read -r PX PY <<< "$1"
  hold "$PX" "$PY" 1.1; sleep 0.8
  qdump "$ROW_DIR/.menu.xml"
  tap_node "$ROW_DIR/.menu.xml" applist_menu_pin; sleep 2
}
hold_tile() { # tile id tag
  qdump "$ROW_DIR/$2-rest.xml"
  if [ "$(has_node "$ROW_DIR/$2-rest.xml" "$1")" = no ]; then adb shell input swipe 540 1600 540 600 400; sleep 1.5; qdump "$ROW_DIR/$2-rest.xml"; fi
  read -r X Y <<< "$(center "$ROW_DIR/$2-rest.xml" "$1")"
  hold_down "$X" "$Y"; sleep 1.0; qdump "$ROW_DIR/$2.xml"; hold_up "$X" "$Y"; sleep 0.8
}

log "--- a managed profile with tileclient-a in it ---"
W="$(adb shell pm create-user --profileOf 0 --managed qa_work | tr -d '\r' | grep -oE '[0-9]+$')"
note "work user $W"
assert_ne "the AVD created a managed profile" "" "$W"
adb shell am start-user "$W" >/dev/null 2>&1; sleep 2
adb shell pm install-existing --user "$W" "$A_PKG" >/dev/null 2>&1; sleep 2
WKEY="app:$A_PKG/app.tileshell.testclient.VerbActivity:$W"
to_applist
xy="$(scroll_to_group_row work)"; note "work row at [$xy]"
pin_row "$xy"
c6
MARK="$(ring_mark)"
hold_tile "tile:$WKEY" work
assert_eq "four satellites on the work tile" "yes yes yes yes" "$(for i in 0 1 2 3; do has_node "$ROW_DIR/work.xml" "quick_sat:$i"; done | tr '\n' ' ' | sed 's/ $//')"
assert_contains "shortcuts line names the work user (T11-12)" "[quick] shortcuts for $A_PKG/app.tileshell.testclient.VerbActivity/$W: 5 (4 shown: qa_one,qa_two,qa_three,qa_four)" "$(quick_since "$MARK")"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/work.xml" quick_sat:0; sleep 3.5
act="$(adb shell dumpsys activity activities | grep -E 'topResumedActivity' | head -1 | tr -d '\r')"
note "resumed: $act"
assert_contains "ShortcutActivity resumed as u$W" "u$W $A_PKG/app.tileshell.testclient.ShortcutActivity" "$act"
qdump "$ROW_DIR/work-launched.xml"
assert_eq "shortcut_id = qa_one" qa_one "$(node_text "$ROW_DIR/work-launched.xml" "$A_PKG:id/shortcut_id")"
c6

log "--- the work profile locked through the shell's own group action: profile quiet ---"
to_applist
scroll_to_group_row work >/dev/null || true
qdump "$ROW_DIR/applist-lock.xml"; tap_node "$ROW_DIR/applist-lock.xml" profile_lock:work; sleep 3
assert_contains "dumpsys user: the work profile is QUIET_MODE" "QUIET_MODE" "$(adb shell dumpsys user | grep -A3 "UserInfo{$W:" | tr -d '\r')"
adb shell input keyevent KEYCODE_HOME; sleep 2.5; ensure_start_page
MARK="$(ring_mark)"
hold_tile "tile:$WKEY" work-quiet
assert_eq "the work tile is still on Start" yes "$(has_node "$ROW_DIR/work-quiet-rest.xml" "tile:$WKEY")"
assert_eq "quiet: edit mode on" yes "$(has_node "$ROW_DIR/work-quiet.xml" edit_disc:unpin)"
assert_eq "quiet: no quick_burst" no "$(has_node "$ROW_DIR/work-quiet.xml" quick_burst)"
assert_contains "quiet: reason" "[quick] no burst on $WKEY: profile quiet" "$(quick_since "$MARK")"
c6
to_applist
for _ in 1 2 3 4 5; do qdump "$ROW_DIR/applist-unlock.xml"; [ "$(has_node "$ROW_DIR/applist-unlock.xml" profile_unlock:work)" = yes ] && break; adb shell input swipe 540 1800 540 700 300; sleep 1.2; done
tap_node "$ROW_DIR/applist-unlock.xml" profile_unlock:work; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 2.5; ensure_start_page
MARK="$(ring_mark)"
hold_tile "tile:$WKEY" work-unquiet
assert_eq "unlocked: the burst is back" yes "$(has_node "$ROW_DIR/work-unquiet.xml" quick_burst)"
c6

log "--- a private space, locked: profile quiet ---"
P="$(adb shell pm create-user --profileOf 0 --user-type android.os.usertype.profile.PRIVATE qa_private | tr -d '\r' | grep -oE '[0-9]+$')"
note "private user $P"
assert_ne "the AVD created a private space" "" "$P"
adb shell am start-user "$P" >/dev/null 2>&1; sleep 2
adb shell pm install-existing --user "$P" "$A_PKG" >/dev/null 2>&1; sleep 2
PKEY="app:$A_PKG/app.tileshell.testclient.VerbActivity:$P"
to_applist
xy="$(scroll_to_group_row private)"; note "private row at [$xy]"
pin_row "$xy"
to_applist
for _ in 1 2 3 4 5; do qdump "$ROW_DIR/applist-plock.xml"; [ "$(has_node "$ROW_DIR/applist-plock.xml" profile_lock:private)" = yes ] && break; adb shell input swipe 540 1800 540 700 300; sleep 1.2; done
tap_node "$ROW_DIR/applist-plock.xml" profile_lock:private; sleep 3
assert_contains "dumpsys user: the private space is QUIET_MODE" "QUIET_MODE" "$(adb shell dumpsys user | grep -A3 "UserInfo{$P:" | tr -d '\r')"
adb shell input keyevent KEYCODE_HOME; sleep 2.5; ensure_start_page
MARK="$(ring_mark)"
hold_tile "tile:$PKEY" private
assert_eq "private: no quick_burst" no "$(has_node "$ROW_DIR/private.xml" quick_burst)"
assert_contains "private: reason" "[quick] no burst on $PKEY: profile quiet" "$(quick_since "$MARK")"
c6

log "--- restore: remove both profiles, the baseline back ---"
adb shell pm remove-user "$W" >/dev/null 2>&1; adb shell pm remove-user "$P" >/dev/null 2>&1; sleep 2
assert_eq "only the owner is left" 1 "$(adb shell pm list users | grep -c 'UserInfo')"
restore baseline_layout.json
row_end
