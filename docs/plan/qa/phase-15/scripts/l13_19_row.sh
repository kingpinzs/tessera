#!/usr/bin/env bash
# L13-19 regression row (INDEX ledger; found by review/2026-09-28-L13-1617-fix-review-a.md note 7): an alarm editor
# flyout (Repeats, Sound, Snooze) stayed open under the "…" menu's scrim — the editor had no close-on-bar step, as the
# tabs' hold menus have (L13-13) — and was still open when the menu closed. The fix closes the flyout in the same write
# that opens the menu. For each flyout: open it, tap "…" (the menu must be up and the flyout gone), tap "…" again (the
# menu closed, the flyout still gone, the editor still up). Each case starts from a force-stopped Clock; nothing is
# saved, so the stores stay empty.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

centre() { set -- $(bounds "$1" "$2"); [ $# -eq 4 ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
tap_tag() { # dump tag [sleep]
  local x y; read -r x y <<< "$(centre "$1" "$2")"
  [ -n "${x:-}" ] && adb shell input tap $x $y
  sleep "${3:-1.2}"
}
check_flyout() { # field flyout-tag
  local c="$1" f="$2" d="$ROW_DIR/$1"
  adb shell am force-stop $PKG; sleep 1
  open_clock alarm
  gdump "$d-0.xml" > /dev/null
  tap_tag "$d-0.xml" clock_bar:add 1.5
  gdump "$d-1.xml" > /dev/null
  assert_eq "($c): Add opened the alarm editor" yes "$(has_node "$d-1.xml" 'alarm_editor_field:snooze')"
  tap_tag "$d-1.xml" "alarm_editor_field:$c" 1
  gdump "$d-2.xml" > /dev/null
  assert_eq "($c): the $c flyout opened" yes "$(has_node "$d-2.xml" "$f")"
  tap_tag "$d-2.xml" clock_more 1
  gdump "$d-3.xml" > /dev/null
  assert_eq "($c): \"…\" opened the bar's menu" yes "$(has_node "$d-3.xml" clock_more_menu)"
  assert_eq "($c): ... and the $c flyout closed" no "$(has_node "$d-3.xml" "$f")"
  tap_tag "$d-3.xml" clock_more 1
  gdump "$d-4.xml" > /dev/null
  assert_eq "($c): \"…\" again closed the menu, the editor still up" "no yes" \
    "$(has_node "$d-4.xml" clock_more_menu) $(has_node "$d-4.xml" 'alarm_editor_field:snooze')"
  assert_eq "($c): ... and the $c flyout is not left open" no "$(has_node "$d-4.xml" "$f")"
}

row_begin L13_19 "opening the \"…\" menu in the alarm editor closes an open flyout"
assert_clock_empty "baseline"
dismiss_any_ring

check_flyout repeats alarm_days_flyout
check_flyout sound alarm_sound_flyout
check_flyout snooze alarm_snooze_flyout

adb shell am force-stop $PKG; sleep 1
assert_clock_empty "restore"
row_end
