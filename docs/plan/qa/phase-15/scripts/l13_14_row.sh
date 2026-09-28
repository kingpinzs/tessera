#!/usr/bin/env bash
# L13-14 regression row (INDEX ledger; found by review/2026-09-27-L13-13-fix-r2-a.md): the Clock's app bar and World
# Clock's compare strip took touches only on their buttons, and a list runs under them, so a tap or hold on their blank
# parts reached the row underneath (an alarm's editor opened; a hold menu opened hidden under the bar). The fix makes
# the bar and the strip hit targets that consume nothing. Rows are seeded until one lies under the touch point, and the
# row asserts that it does, so the checks cannot pass for want of a row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

centre() { set -- $(bounds "$1" "$2"); [ $# -eq 4 ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
row_under() { # dump x y prefix -> the id of the row whose bounds hold (x, y), or nothing
  python3 - "$1" "$2" "$3" "$4" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read(); x, y, pre = int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
for m in re.finditer(r'resource-id="%s([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(pre), s):
    l, t, r, b = map(int, m.groups()[1:])
    if l <= x < r and t <= y < b: print(m.group(1)); break
PY
}

row_begin L13_14 "a touch on the Clock's bar or compare strip does not reach the list under it"
assert_clock_empty "baseline"
dismiss_any_ring
WSAVED="$(adb shell "run-as $PKG sh -c 'if [ -f files/world_clock.json ]; then cp files/world_clock.json files/world_clock.json.l1314 && echo yes || echo cp-failed; else echo no; fi'" | tr -d '\r')"
note "world store existed before: $WSAVED"
[ "$WSAVED" = cp-failed ] && { assert_eq "the World Clock store was copied aside before the seed" yes no; row_end; exit 1; }
# Eight alarms 3 h ahead (no ring during the row) and twelve cities: both lists run under the bar.
NOW="$(device_ms)"
for i in 1 2 3 4 5 6 7 8; do read -r H M <<< "$(device_hm $(( NOW + 3 * 3600000 + i * 60000 )))"; api_alarm "$H" "$M" "L1314 $i" > /dev/null; done
note "alarms seeded: $(alarm_ids | wc -w)"
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'echo [\\\"Europe/London\\\",\\\"Europe/Paris\\\",\\\"Europe/Berlin\\\",\\\"Europe/Madrid\\\",\\\"Europe/Rome\\\",\\\"Asia/Tokyo\\\",\\\"Asia/Dubai\\\",\\\"Asia/Kolkata\\\",\\\"Australia/Sydney\\\",\\\"America/New_York\\\",\\\"America/Chicago\\\",\\\"America/Los_Angeles\\\"] > files/world_clock.json'"

# ---- (a) the bar's blank part, on the Alarm tab
open_clock alarm
gdump "$ROW_DIR/a-0.xml" > /dev/null
set -- $(bounds "$ROW_DIR/a-0.xml" clock_app_bar); PX=$(( $1 + 60 )); PY=$(( ($2 + $4) / 2 ))
UNDER="$(row_under "$ROW_DIR/a-0.xml" $PX $PY alarm_row:)"
note "(a) the bar's blank point ($PX,$PY); the alarm row under it: ${UNDER:-none}"
assert_ne "(a): an alarm row lies under the bar's blank point (the check is not vacuous)" "" "$UNDER"
adb shell input tap $PX $PY; sleep 1.5
gdump "$ROW_DIR/a-1-tap.xml" > /dev/null
assert_eq "(a): a tap on the bar's blank part opened no alarm's editor" no "$(has_node "$ROW_DIR/a-1-tap.xml" 'alarm_editor_field:snooze')"
[ "$(has_node "$ROW_DIR/a-1-tap.xml" 'alarm_editor_field:snooze')" = yes ] && { adb shell input keyevent 4; sleep 1; }
adb shell input swipe $PX $PY $PX $PY 1200; sleep 1
gdump "$ROW_DIR/a-2-hold.xml" > /dev/null
assert_eq "(a): a hold on the bar's blank part opened no row's hold menu" no "$(has_node "$ROW_DIR/a-2-hold.xml" alarm_row_menu)"

# Each case starts on a fresh Clock: open_clock alone reuses the running activity, and a menu the case before left open
# (on the old build) would take the next case's touches (review/2026-09-28-L13-1415-fix-review-b.md).
# ---- (a2) the bar expanded: a tap on its blank part closes the "…" menu (through its scrim, as before the fix)
adb shell am force-stop $PKG; sleep 1
open_clock alarm
gdump "$ROW_DIR/a2-0.xml" > /dev/null
read -r MX MY <<< "$(centre "$ROW_DIR/a2-0.xml" clock_more)"
adb shell input tap $MX $MY; sleep 1
gdump "$ROW_DIR/a2-1-open.xml" > /dev/null
assert_eq "(a2): \"…\" opened the bar's menu" yes "$(has_node "$ROW_DIR/a2-1-open.xml" clock_more_menu)"
set -- $(bounds "$ROW_DIR/a2-1-open.xml" clock_app_bar); BX=$(( $1 + 60 )); BY=$(( ($2 + $4) / 2 ))
adb shell input tap $BX $BY; sleep 1
gdump "$ROW_DIR/a2-2-tap.xml" > /dev/null
assert_eq "(a2): a tap on the expanded bar's blank part closed the menu" no "$(has_node "$ROW_DIR/a2-2-tap.xml" clock_more_menu)"
assert_eq "(a2): ... and opened no alarm's editor" no "$(has_node "$ROW_DIR/a2-2-tap.xml" 'alarm_editor_field:snooze')"

# ---- (a3) Back and a tap on the bar's blank part together (review/2026-09-28-L13-1415-fix-r2-a.md finding 1): Back
# unplaces the "…" scrim at once, before the bar recomposes; a tap in that frame must still land on the bar, not on the
# alarm row under it. Sent from one device shell at 0-20 ms offsets, the form l13_11_row.sh uses.
N="${N:-30}"
state_of() { # dump -> editor | menu | tabs | other | nodump
  [ -s "$1" ] || { echo nodump; return; }
  if [ "$(has_node "$1" 'alarm_editor_field:snooze')" = yes ]; then echo editor
  elif [ "$(has_node "$1" clock_more_menu)" = yes ]; then echo menu
  elif [ "$(has_node "$1" clock_app_bar)" = yes ]; then echo tabs
  else echo other; fi
}
adb shell am force-stop $PKG; sleep 1
open_clock alarm
gdump "$ROW_DIR/a3-0.xml" > /dev/null
read -r MX MY <<< "$(centre "$ROW_DIR/a3-0.xml" clock_more)"
set -- $(bounds "$ROW_DIR/a3-0.xml" clock_app_bar); PX=$(( $1 + 60 )); PY=$(( ($2 + $4) / 2 ))
UNDER="$(row_under "$ROW_DIR/a3-0.xml" $PX $PY alarm_row:)"
note "(a3) the bar's blank point ($PX,$PY); the alarm row under it: ${UNDER:-none}"
assert_ne "(a3): an alarm row lies under the bar's blank point (the check is not vacuous)" "" "$UNDER"
mkdir -p "$ROW_DIR/a3"; : > "$ROW_DIR/a3/trials.txt"
for i in $(seq 1 "$N"); do
  off=$(( (i % 5) * 5 ))
  gdump "$ROW_DIR/a3/t$i-pre.xml" > /dev/null
  [ "$(state_of "$ROW_DIR/a3/t$i-pre.xml")" = tabs ] || { open_clock alarm; gdump "$ROW_DIR/a3/t$i-pre.xml" > /dev/null; }
  [ "$(state_of "$ROW_DIR/a3/t$i-pre.xml")" = tabs ] || { echo "t$i invalid: not on the tabs" >> "$ROW_DIR/a3/trials.txt"; continue; }
  adb shell input tap $MX $MY; sleep 0.8
  gdump "$ROW_DIR/a3/t$i-open.xml" > /dev/null
  if [ "$(state_of "$ROW_DIR/a3/t$i-open.xml")" != menu ]; then
    echo "t$i invalid: the bar did not open" >> "$ROW_DIR/a3/trials.txt"; adb shell input keyevent 4; sleep 1; continue
  fi
  adb shell "input keyevent 4 & sleep 0.0$(printf %02d $off); input tap $PX $PY; wait"
  sleep 1.5
  gdump "$ROW_DIR/a3/t$i.xml" > /dev/null
  st="$(state_of "$ROW_DIR/a3/t$i.xml")"
  [ "$st" = nodump ] && { echo "t$i invalid: the dump after failed" >> "$ROW_DIR/a3/trials.txt"; open_clock alarm; continue; }
  echo "t$i offset=${off}ms after: $st" >> "$ROW_DIR/a3/trials.txt"
  case "$st" in
    editor|menu) adb shell input keyevent 4; sleep 1 ;;
    tabs) ;;
    *) open_clock alarm ;;
  esac
done
VALID="$(grep -c 'after:' "$ROW_DIR/a3/trials.txt")"; LEAK="$(grep -c 'after: editor' "$ROW_DIR/a3/trials.txt")"
HELD="$(grep -c 'after: tabs' "$ROW_DIR/a3/trials.txt")"; FIRST="$(grep -cE 'after: (other|menu)' "$ROW_DIR/a3/trials.txt")"
note "(a3) valid trials $VALID of $N: the tap reached the alarm row $LEAK, the bar took it $HELD, the tap came before the Back (Back then closed the Clock) $FIRST"
assert_eq "(a3): every trial was valid (on the tabs, the bar open)" "$N" "$VALID"
assert_eq "(a3): no tap on the bar's blank part reached the alarm row under it" 0 "$LEAK"

# ---- (b) the compare strip's centre, on World Clock
adb shell am force-stop $PKG; sleep 1
open_clock world_clock
gdump "$ROW_DIR/b-0.xml" > /dev/null
read -r CX CY <<< "$(centre "$ROW_DIR/b-0.xml" clock_compare)"
adb shell input tap $CX $CY; sleep 1.5
gdump "$ROW_DIR/b-1-compare.xml" > /dev/null
assert_eq "(b): Compare showed the strip" yes "$(has_node "$ROW_DIR/b-1-compare.xml" clock_compare_strip)"
set -- $(bounds "$ROW_DIR/b-1-compare.xml" clock_compare_strip); SX=$(( ($1 + $3) / 2 )); SY=$(( ($2 + $4) / 2 ))
UNDER="$(row_under "$ROW_DIR/b-1-compare.xml" $SX $SY clock_row:)"
note "(b) the strip's centre ($SX,$SY); the city row under it: ${UNDER:-none}"
assert_ne "(b): a city row lies under the strip's centre (the check is not vacuous)" "" "$UNDER"
adb shell input swipe $SX $SY $SX $SY 1200; sleep 1
gdump "$ROW_DIR/b-2-hold.xml" > /dev/null
assert_eq "(b): a hold on the strip's centre opened no city's hold menu" no "$(has_node "$ROW_DIR/b-2-hold.xml" clock_row_menu)"

adb shell am force-stop $PKG; sleep 1
if [ "$WSAVED" = yes ]; then
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json.l1314 files/world_clock.json'"
else
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json files/world_clock.json.l1314-seeded'"
fi
app_delete_all
assert_clock_empty "restore"
row_end
