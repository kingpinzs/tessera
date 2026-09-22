#!/usr/bin/env bash
# ITEM 8 (INDEX Change Log 2026-09-21): the app list's two top sections — the last 5 apps run and the
# last 3 installed — verified on the device.
#
# The row is bracketed so it CAN fail: it first proves the Recent section is ABSENT while Usage access
# is denied (the state Jeremy's phone is in, `[applist] no Usage access`), then grants the permission
# and proves the section appears with the apps that were actually launched. A row that only looked at
# the granted state would pass just as happily against a section that is always drawn.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"

PKGS="org.fossify.notes net.osmand.plus com.fsck.k9"

open_list() { # Home, then swipe left off Start onto the app list, and go to the TOP of it
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell input swipe 900 1200 150 1200 250; sleep 2
  # Two things make the top of the list the only honest place to start. The list KEEPS its scroll
  # position between visits, and the recent section is read off UsageStatsManager asynchronously on
  # resume. The first run of this row did neither and recorded an empty Recent section against a list
  # that actually had five rows in it — it had simply walked away from them.
  for _ in 1 2 3 4 5 6 7 8 9 10; do adb shell input swipe 540 700 540 2000 200; done
  sleep 4
}

collect_ids() { # collect_ids <outfile> — walk the list top-down gathering every applist id
  : > "$1"
  for _ in 1 2 3 4 5 6; do
    dump "$ROW_DIR/walk.xml" || break
    grep -o 'resource-id="applist_\(section\|recent_row\|added_row\|row\|header\):[^"]*"' "$ROW_DIR/walk.xml" \
      | sed 's/resource-id="//; s/"$//' >> "$1"
    adb shell input swipe 540 1800 540 900 300; sleep 1
  done
  sort -u "$1" -o "$1"
}

row_begin ITEM8 "app list: Recent (last 5 run) and Recently added (last 3 installed)"

# ---- bracket: the section is absent while the permission is denied -------------------------------
adb shell appops set app.tileshell android:get_usage_stats default >/dev/null 2>&1
adb shell am force-stop app.tileshell; sleep 1
open_list
dump "$ROW_DIR/denied.xml"
adb exec-out screencap -p > "$ROW_DIR/denied.png"
assert_absent "Recent section is absent while Usage access is denied" \
  'resource-id="applist_section:recent"' "$(cat "$ROW_DIR/denied.xml")"
assert_contains "the shell says why" "no Usage access" "$(diag applist)"

# ---- grant, then actually run three apps so there is something recent ----------------------------
adb shell appops set app.tileshell android:get_usage_stats allow >/dev/null 2>&1
assert_contains "Usage access is granted" "allow" "$(adb shell appops get app.tileshell android:get_usage_stats)"
for p in $PKGS; do
  adb shell monkey -p "$p" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 3
  adb shell input keyevent KEYCODE_HOME; sleep 1
done
adb shell am force-stop app.tileshell; sleep 1
open_list
dump "$ROW_DIR/granted.xml"
adb exec-out screencap -p > "$ROW_DIR/granted.png"
collect_ids "$ROW_DIR/ids.txt"

# ---- the sections, and the apps that are actually in them ----------------------------------------
assert_contains "Recent section header is drawn" "applist_section:recent" "$(cat "$ROW_DIR/ids.txt")"
assert_contains "Recently added section header is drawn" "applist_section:added" "$(cat "$ROW_DIR/ids.txt")"
for p in $PKGS; do
  assert_contains "a launched app is in Recent: $p" "applist_recent_row:$p" "$(cat "$ROW_DIR/ids.txt")"
done
RECENT_N=$(grep -c '^applist_recent_row:' "$ROW_DIR/ids.txt")
ADDED_N=$(grep -c '^applist_added_row:' "$ROW_DIR/ids.txt")
note "recent rows=$RECENT_N added rows=$ADDED_N"
assert_eq "Recent holds at most 5" "ok" "$([ "$RECENT_N" -le 5 ] && echo ok || echo "$RECENT_N")"
assert_eq "Recently added holds at most 3" "ok" "$([ "$ADDED_N" -le 3 ] && echo ok || echo "$ADDED_N")"
assert_eq "Recent is not empty" "ok" "$([ "$RECENT_N" -ge 1 ] && echo ok || echo 0)"

# ---- the same app is in BOTH places, which is what the two key prefixes exist for ----------------
FIRST=$(echo "$PKGS" | cut -d' ' -f1)
assert_contains "the same app still has its A-Z row: $FIRST" "applist_row:$FIRST" "$(cat "$ROW_DIR/ids.txt")"

# ---- a section row is not searchable: one hit, not two -------------------------------------------
open_list
dump "$ROW_DIR/pre_search.xml"
tap_id "$ROW_DIR/pre_search.xml" applist_search; sleep 1
adb shell input text "osmand"; sleep 2
dump "$ROW_DIR/search.xml"
adb exec-out screencap -p > "$ROW_DIR/search.png"
HITS=$(grep -o 'resource-id="applist_[a-z_]*row:net.osmand.plus"' "$ROW_DIR/search.xml" | wc -l | tr -d ' ')
note "search hits for osmand: $HITS"
assert_eq "searching finds the app exactly once" "1" "$HITS"
assert_absent "no section row in the results" "applist_recent_row:net.osmand.plus" "$(cat "$ROW_DIR/search.xml")"

# ---- the jump grid still lands on the right letter with the sections above it --------------------
# The sections are inserted BEFORE the letter groups, so every letter's index moves. If the jump grid
# were still built from the pre-item-8 indices it would land short by exactly the height of the two
# sections, which is the defect this asserts is absent.
TARGET=$(grep '^applist_header:' "$ROW_DIR/ids.txt" | tail -1 | cut -d: -f2)
open_list
dump "$ROW_DIR/jump_pre.xml"
FIRST_HDR=$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/jump_pre.xml" | head -1 | sed 's/resource-id="//; s/"$//')
note "opening the jump grid from [$FIRST_HDR], jumping to [$TARGET]"
tap_id "$ROW_DIR/jump_pre.xml" "$FIRST_HDR"; sleep 2
dump "$ROW_DIR/jump_grid.xml"
adb exec-out screencap -p > "$ROW_DIR/jump_grid.png"
assert_contains "the jump grid opens" "jump_cell:" "$(cat "$ROW_DIR/jump_grid.xml")"
tap_id "$ROW_DIR/jump_grid.xml" "jump_cell:$TARGET"; sleep 2
dump "$ROW_DIR/jumped.xml"
adb exec-out screencap -p > "$ROW_DIR/jumped.png"
assert_contains "the jump lands on its own letter header: $TARGET" "applist_header:$TARGET" "$(cat "$ROW_DIR/jumped.xml")"

row_end
