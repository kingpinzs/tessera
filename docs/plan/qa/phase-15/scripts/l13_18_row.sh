#!/usr/bin/env bash
# L13-18 regression row (INDEX ledger; found by review/2026-09-28-L13-1617-fix-review-a.md note 7): Back from About
# went to the tabs whatever page About was opened from, so "…" → About from an alarm or timer editor, then Back, left
# the editor and its unsaved edits behind. The fix has About remember the page it was opened from. (a) The alarm editor
# with an edit (snooze 10 → 30 minutes), About, Back: the editor must come back with the edit. (b) The timer editor,
# About, Back: the timer editor must come back. (c) Control: About from the tabs, Back: the tabs; a second Back leaves
# the Clock. Nothing is saved, so the stores stay empty. Each case starts from a force-stopped Clock.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

centre() { set -- $(bounds "$1" "$2"); [ $# -eq 4 ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
has_text() { grep -q "text=\"$2\"" "$1" && echo yes || echo no; }
tap_tag() { # dump tag [sleep]
  local x y; read -r x y <<< "$(centre "$1" "$2")"
  [ -n "${x:-}" ] && adb shell input tap $x $y
  sleep "${3:-1.2}"
}
about_and_back() { # case (from the page on screen: "…", About, then Back)
  gdump "$ROW_DIR/$1-m0.xml" > /dev/null
  tap_tag "$ROW_DIR/$1-m0.xml" clock_more 1
  gdump "$ROW_DIR/$1-m1.xml" > /dev/null
  assert_eq "($1): \"…\" offers About" yes "$(has_node "$ROW_DIR/$1-m1.xml" clock_more:about)"
  tap_tag "$ROW_DIR/$1-m1.xml" clock_more:about 1.5
  gdump "$ROW_DIR/$1-about.xml" > /dev/null
  assert_eq "($1): About opened" yes "$(has_node "$ROW_DIR/$1-about.xml" about_page)"
  adb shell input keyevent 4; sleep 1.5
  gdump "$ROW_DIR/$1-back.xml" > /dev/null
}

row_begin L13_18 "Back from About returns to the page it was opened from, an editor's edits kept"
assert_clock_empty "baseline"
dismiss_any_ring

# ---- (a) the alarm editor, with an edit
adb shell am force-stop $PKG; sleep 1
open_clock alarm
gdump "$ROW_DIR/a-0.xml" > /dev/null
tap_tag "$ROW_DIR/a-0.xml" clock_bar:add 1.5
gdump "$ROW_DIR/a-1.xml" > /dev/null
assert_eq "(a): Add opened the alarm editor, snooze 10 minutes" "yes yes" \
  "$(has_node "$ROW_DIR/a-1.xml" 'alarm_editor_field:snooze') $(has_text "$ROW_DIR/a-1.xml" '10 minutes')"
tap_tag "$ROW_DIR/a-1.xml" 'alarm_editor_field:snooze' 1
gdump "$ROW_DIR/a-2.xml" > /dev/null
tap_tag "$ROW_DIR/a-2.xml" 'alarm_snooze:30' 1
gdump "$ROW_DIR/a-3.xml" > /dev/null
assert_eq "(a): the edit took (snooze 30 minutes)" yes "$(has_text "$ROW_DIR/a-3.xml" '30 minutes')"
about_and_back a
assert_eq "(a): Back from About returned to the alarm editor" "yes no" \
  "$(has_node "$ROW_DIR/a-back.xml" 'alarm_editor_field:snooze') $(has_node "$ROW_DIR/a-back.xml" about_page)"
assert_eq "(a): ... with the edit kept (snooze 30 minutes)" yes "$(has_text "$ROW_DIR/a-back.xml" '30 minutes')"

# ---- (b) the timer editor
adb shell am force-stop $PKG; sleep 1
open_clock timer
gdump "$ROW_DIR/b-0.xml" > /dev/null
tap_tag "$ROW_DIR/b-0.xml" clock_bar:add 1.5
gdump "$ROW_DIR/b-1.xml" > /dev/null
assert_eq "(b): Add opened the timer editor" yes "$(has_node "$ROW_DIR/b-1.xml" 'timer_editor_field:name')"
about_and_back b
assert_eq "(b): Back from About returned to the timer editor" "yes no" \
  "$(has_node "$ROW_DIR/b-back.xml" 'timer_editor_field:name') $(has_node "$ROW_DIR/b-back.xml" about_page)"

# ---- (c) control: About from the tabs
adb shell am force-stop $PKG; sleep 1
open_clock alarm
about_and_back c
assert_eq "(c) control: Back from About returned to the tabs" "yes no no" \
  "$(has_node "$ROW_DIR/c-back.xml" clock_app_bar) $(has_node "$ROW_DIR/c-back.xml" about_page) $(has_node "$ROW_DIR/c-back.xml" 'alarm_editor_field:snooze')"
adb shell input keyevent 4; sleep 1.5
gdump "$ROW_DIR/c-back2.xml" > /dev/null
assert_eq "(c) control: a second Back left the Clock" no "$(has_node "$ROW_DIR/c-back2.xml" clock_root)"

adb shell am force-stop $PKG; sleep 1
assert_clock_empty "restore"
row_end
