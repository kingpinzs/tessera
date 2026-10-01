#!/usr/bin/env bash
# Phase 14 E1 — the pager: the pod bay left of Start by a pan, closed by a pan, Back and the drawn Windows key; the app
# list still right of Start; Back on Start still runs phase 01 E20's history rule. Each page is also named by its
# `[start] page=<name>` line (T14-8).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E1 "pager: pod bay by pan, Back, Windows key; app list; Back history on Start"
seed_baseline

# Phase 01 E20's seeding: DeskClock resumed, then Home (delivered: Start was not the resumed activity).
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null 2>&1
sleep 3
adb shell input keyevent KEYCODE_HOME
sleep 3
dump_ui "$ROW_DIR/01-start.xml"
assert_eq "Home from DeskClock shows Start alone" "yes" "$(start_alone "$ROW_DIR/01-start.xml")"

# (1) a pan inside the page opens the pod bay.
MARK="$(ring_mark)"
swipe_right
dump_ui "$ROW_DIR/02-podbay.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/02-slice.txt"
assert_eq "swipe right: pod_bay in the dump" "yes" "$(has_node "$ROW_DIR/02-podbay.xml" pod_bay)"
assert_eq "swipe right: start_page not in the dump" "no" "$(has_node "$ROW_DIR/02-podbay.xml" start_page)"
assert_contains "swipe right: [podbay] opened by swipe" "[podbay] opened by swipe" "$s"
assert_eq "swipe right: the page lines" "POD_BAY" "$(pages_in "$s")"
screencap "$ROW_DIR/02-podbay.png"

# (2) a left pan closes it.
MARK="$(ring_mark)"
swipe_left
dump_ui "$ROW_DIR/03-start.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/03-slice.txt"
assert_eq "swipe left from the pod bay: Start alone" "yes" "$(start_alone "$ROW_DIR/03-start.xml")"
assert_contains "swipe left: [podbay] closed by swipe" "[podbay] closed by swipe" "$s"
assert_eq "swipe left: the page lines" "START" "$(pages_in "$s")"

# (3) a second left pan: the app list (phase 01 E12 unchanged).
MARK="$(ring_mark)"
swipe_left
dump_ui "$ROW_DIR/04-applist.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/04-slice.txt"
assert_eq "second left pan: app_list in the dump" "yes" "$(has_node "$ROW_DIR/04-applist.xml" app_list)"
assert_eq "second left pan: no pod_bay" "no" "$(has_node "$ROW_DIR/04-applist.xml" pod_bay)"
assert_eq "second left pan: the page lines" "APP_LIST" "$(pages_in "$s")"
assert_absent "the app list pan says nothing about the pod bay" "[podbay]" "$s"
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/05-start.xml"
assert_eq "Back from the app list: Start alone" "yes" "$(start_alone "$ROW_DIR/05-start.xml")"

# (4) Back from the pod bay is Start.
swipe_right
dump_ui "$ROW_DIR/06-podbay.xml"
assert_eq "pod bay again" "yes" "$(has_node "$ROW_DIR/06-podbay.xml" pod_bay)"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/07-start.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/07-slice.txt"
assert_eq "Back from the pod bay: Start alone" "yes" "$(start_alone "$ROW_DIR/07-start.xml")"
assert_contains "Back: [podbay] closed by back" "[podbay] closed by back" "$s"
assert_eq "Back: the page lines" "START" "$(pages_in "$s")"
assert_eq "Back from the pod bay did not leave Start" "app.tileshell/.StartActivity" "$(top_activity)"

# (5) Back on Start still runs phase 01 E20's rule: the history's DeskClock comes back.
adb shell input keyevent KEYCODE_BACK
sleep 3
assert_eq "Back on Start: DeskClock resumes (E20)" "com.android.deskclock/.DeskClock" "$(top_activity)"
c6

# (6) The drawn Windows key from the pod bay is Home: Start, scrolled to top (KEYCODE_HOME is P5 on this AVD).
swipe_right
dump_ui "$ROW_DIR/08-podbay.xml"
assert_eq "pod bay before the Windows key" "yes" "$(has_node "$ROW_DIR/08-podbay.xml" pod_bay)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/08-podbay.xml" nav_windows
sleep 2
dump_ui "$ROW_DIR/09-start.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/09-slice.txt"
assert_eq "Windows key: Start alone" "yes" "$(start_alone "$ROW_DIR/09-start.xml")"
assert_contains "Windows key: [podbay] closed by home" "[podbay] closed by home" "$s"
assert_contains "Windows key: the renamed home line" "[start] home: page START, scrolled to top" "$s"
assert_eq "Windows key: the page lines" "START" "$(pages_in "$s")"

restore_device_layout
row_end
