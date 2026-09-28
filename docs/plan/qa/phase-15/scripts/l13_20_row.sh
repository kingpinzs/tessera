#!/usr/bin/env bash
# L13-20 regression row (INDEX ledger; found by review/2026-09-28-L13-1819-fix-review-a.md note 1): in both editors the
# name field's edit mode outlived the keyboard (Back hides the keyboard, the field stays in edit mode) and opening "…"
# did not end it, so the editor's Back handler took the next Back — ending the edit — and the menu stayed up. The fix
# ends the edit when "…" opens. For each editor: tap the name, type, Back (the keyboard hides; the field is still in edit
# mode — the precondition on both builds), "…" (the edit must end), Back (the menu must close, the editor still up, the
# typed name kept). Each case starts from a force-stopped Clock; nothing is saved, so the stores stay empty.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

centre() { set -- $(bounds "$1" "$2"); [ $# -eq 4 ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
ime_shown() { adb shell dumpsys input_method | tr -d '\r' | grep -c 'mInputShown=true'; }
has_text() { grep -q "text=\"$2\"" "$1" && echo yes || echo no; }
tap_tag() { # dump tag [sleep]
  local x y; read -r x y <<< "$(centre "$1" "$2")"
  [ -n "${x:-}" ] && adb shell input tap $x $y
  sleep "${3:-1.2}"
}
check_editor() { # case tab editor-probe name-row name-field typed
  local c="$1" tab="$2" probe="$3" row="$4" field="$5" typed="$6" d="$ROW_DIR/$1"
  adb shell am force-stop $PKG; sleep 1
  open_clock "$tab"
  gdump "$d-0.xml" > /dev/null
  tap_tag "$d-0.xml" clock_bar:add 1.5
  gdump "$d-1.xml" > /dev/null
  assert_eq "($c): Add opened the editor" yes "$(has_node "$d-1.xml" "$probe")"
  tap_tag "$d-1.xml" "$row" 1.5
  adb shell input text "$typed"; sleep 1
  gdump "$d-2.xml" > /dev/null
  assert_eq "($c): the name field is in edit mode with the keyboard up" "yes 1" "$(has_node "$d-2.xml" "$field") $(ime_shown)"
  adb shell input keyevent 4; sleep 1.2
  gdump "$d-3.xml" > /dev/null
  assert_eq "($c): Back hid the keyboard, the field still in edit mode (the precondition)" "0 yes" "$(ime_shown) $(has_node "$d-3.xml" "$field")"
  tap_tag "$d-3.xml" clock_more 1
  gdump "$d-4.xml" > /dev/null
  assert_eq "($c): \"…\" opened the bar's menu" yes "$(has_node "$d-4.xml" clock_more_menu)"
  assert_eq "($c): ... and ended the name edit" no "$(has_node "$d-4.xml" "$field")"
  adb shell input keyevent 4; sleep 1.2
  gdump "$d-5.xml" > /dev/null
  assert_eq "($c): one Back closed the menu, the editor still up" "no yes" "$(has_node "$d-5.xml" clock_more_menu) $(has_node "$d-5.xml" "$probe")"
  assert_eq "($c): ... with the typed name kept" yes "$(has_text "$d-5.xml" "$typed")"
}

row_begin L13_20 "opening the \"…\" menu ends an editor's name edit, so the next Back closes the menu"
assert_clock_empty "baseline"
dismiss_any_ring

check_editor a alarm 'alarm_editor_field:snooze' 'alarm_editor_field:name' alarm_editor_name_field L1320a
check_editor b timer 'timer_editor_title' 'timer_editor_field:name' timer_editor_name_field L1320b

adb shell am force-stop $PKG; sleep 1
assert_clock_empty "restore"
row_end
