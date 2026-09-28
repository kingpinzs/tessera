#!/usr/bin/env bash
# L13-16 regression row (INDEX ledger; found by review/2026-09-28-L13-1415-fix-review-a.md note 3): a row's hold menu
# opened at the row's top + 60 dp whatever room was left, so a row held near the bottom opened its menu under the app
# bar (wholly, when 60 dp or less of the row showed above it), where its verb cannot be reached and its scrim takes the
# next tap. The fix keeps the flyout above the bar. On each tab (Alarm, Timer, World Clock), the lowest row that shows
# above the bar is held on its visible part: its menu must lie wholly above the bar, and its verb, tapped where the dump
# shows it, must act. The row asserts the old placement (row top + 60 dp, 60 dp tall) would reach under the bar, so the
# check is not vacuous. Control: the first row's menu still opens at its top + 60 dp.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

centre() { set -- $(bounds "$1" "$2"); [ $# -eq 4 ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
rows_by_top() { # dump prefix -> "id top bottom" per row, top first
  python3 - "$1" "$2" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read(); pre = sys.argv[2]
rows = [(int(m.group(3)), int(m.group(5)), m.group(1)) for m in
        re.finditer(r'resource-id="%s([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(pre), s)]
for t, b, i in sorted(rows): print(i, t, b)
PY
}

# tab prefix menu verb-prefix x -> checks the lowest visible row's menu, then the first row's (the control)
check_tab() {
  local tab="$1" pre="$2" menu="$3" verb="$4" hx="$5" d="$ROW_DIR/$1"
  adb shell am force-stop $PKG; sleep 1
  open_clock "$tab"
  gdump "$d-0.xml" > /dev/null
  set -- $(bounds "$d-0.xml" clock_app_bar); local bartop="${2:-}"
  assert_ne "($tab): the app bar is on screen" "" "$bartop"
  # dp in px from the collapsed bar itself (ClockMetrics.APP_BAR, 48 dp): the shell lays out in W10M effective
  # pixels, not at the system's density.
  local bh=$(( ${4:-0} - ${2:-0} )); PX60=$(( bh * 60 / 48 )); local px50=$(( bh * 50 / 48 )) px30=$(( bh * 30 / 48 ))
  local low lt lb
  read -r low lt lb <<< "$(rows_by_top "$d-0.xml" "$pre" | awk -v bt="$bartop" '$2 < bt - 30' | tail -1)"
  note "($tab) bar $bh px tall (48 dp), so 60 dp = $PX60 px; bar top $bartop; the lowest row showing above it: ${low:-none} top ${lt:-?} ($(( bartop - ${lt:-0} )) px above the bar)"
  assert_ne "($tab): a row shows above the bar" "" "$low"
  assert_eq "($tab): the old placement (top + 60 dp, 60 dp tall) would reach under the bar (the check is not vacuous)" yes \
    "$([ $(( lt + 2 * PX60 )) -gt "$bartop" ] && echo yes || echo no)"
  local vis=$(( bartop - lt )); local hy=$(( lt + (vis / 2 < px50 ? vis / 2 : px50) ))
  adb shell input swipe $hx $hy $hx $hy 1200; sleep 1
  gdump "$d-1-hold.xml" > /dev/null
  assert_eq "($tab): the hold opened the row's menu" yes "$(has_node "$d-1-hold.xml" "$menu")"
  set -- $(bounds "$d-1-hold.xml" "$menu")
  note "($tab) held ($hx,$hy) on $low; menu bounds ${*:-none}"
  assert_eq "($tab): the menu lies wholly above the bar" yes "$([ $# -eq 4 ] && [ "$4" -le "$bartop" ] && [ "$2" -ge 0 ] && echo yes || echo "no (bottom ${4:-?} vs bar top $bartop)")"
  local vx vy; read -r vx vy <<< "$(centre "$d-1-hold.xml" "$verb$low")"
  assert_eq "($tab): the verb shows above the bar" yes "$([ -n "${vy:-}" ] && [ "$vy" -lt "$bartop" ] && echo yes || echo "no (${vy:-absent})")"
  if [ -n "${vx:-}" ]; then adb shell input tap $vx $vy; sleep 1.5; fi
  gdump "$d-2-verb.xml" > /dev/null
  assert_eq "($tab): the verb acted (the row is gone)" no "$(has_node "$d-2-verb.xml" "$pre$low")"
  # Control: the first row, near the top, keeps its menu at its top + 60 dp.
  adb shell am force-stop $PKG; sleep 1
  open_clock "$tab"
  gdump "$d-3-0.xml" > /dev/null
  local first ft fb; read -r first ft fb <<< "$(rows_by_top "$d-3-0.xml" "$pre" | head -1)"
  adb shell input swipe $hx $(( ft + px30 )) $hx $(( ft + px30 )) 1200; sleep 1
  gdump "$d-4-hold.xml" > /dev/null
  set -- $(bounds "$d-4-hold.xml" "$menu")
  note "($tab) control: first row $first top $ft; its menu ${*:-none}"
  assert_eq "(${tab}) control: the first row's menu opens at its top + 60 dp (within 3 px)" yes \
    "$([ $# -eq 4 ] && [ $(( $2 - ft - PX60 )) -le 3 ] && [ $(( ft + PX60 - $2 )) -le 3 ] && echo yes || echo "no (${2:-absent} vs $(( ft + PX60 )))")"
}

row_begin L13_16 "a row held near the bottom opens its menu above the app bar, where its verb works"
assert_clock_empty "baseline"
dismiss_any_ring
WSAVED="$(adb shell "run-as $PKG sh -c 'if [ -f files/world_clock.json ]; then cp files/world_clock.json files/world_clock.json.l1316 && echo yes || echo cp-failed; else echo no; fi'" | tr -d '\r')"
note "world store existed before: $WSAVED"
[ "$WSAVED" = cp-failed ] && { assert_eq "the World Clock store was copied aside before the seed" yes no; row_end; exit 1; }
# Eight alarms 3 h ahead and five 3-hour timers (nothing rings during the row), and twelve cities: every list runs
# under the bar.
NOW="$(device_ms)"
for i in 1 2 3 4 5 6 7 8; do read -r H M <<< "$(device_hm $(( NOW + 3 * 3600000 + i * 60000 )))"; api_alarm "$H" "$M" "L1316 $i" > /dev/null; done
for i in 1 2 3 4 5; do api_timer 10800 "L1316 $i" > /dev/null; done
note "alarms seeded: $(alarm_ids | wc -w); timers seeded: $(timer_ids | wc -w)"
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'echo [\\\"Europe/London\\\",\\\"Europe/Paris\\\",\\\"Europe/Berlin\\\",\\\"Europe/Madrid\\\",\\\"Europe/Rome\\\",\\\"Asia/Tokyo\\\",\\\"Asia/Dubai\\\",\\\"Asia/Kolkata\\\",\\\"Australia/Sydney\\\",\\\"America/New_York\\\",\\\"America/Chicago\\\",\\\"America/Los_Angeles\\\"] > files/world_clock.json'"

check_tab alarm alarm_row: alarm_row_menu alarm_delete: 200
check_tab timer timer_block: timer_row_menu timer_delete: 540
check_tab world_clock clock_row: clock_row_menu clock_remove: 540

adb shell am force-stop $PKG; sleep 1
if [ "$WSAVED" = yes ]; then
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json.l1316 files/world_clock.json'"
else
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json files/world_clock.json.l1316-seeded'"
fi
app_delete_all
assert_clock_empty "restore"
row_end
