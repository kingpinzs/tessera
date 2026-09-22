#!/usr/bin/env bash
# ITEM 2 (INDEX Change Log 2026-09-21): the Weather tile's main face IS the day's weather, animated —
# "sunny, raining, snowing, rainbow ect. something fun".
#
# Every scene is driven from a fixture report rather than from the sky outside. WeatherFeed caches its
# last report and loads it at start, so a report written into the cache is the weather as far as the
# shell is concerned; SkyRules.scene is a pure function of it. That is what makes the RAINBOW testable
# at all — SkyRules' own comment says it "cannot be summoned on demand in QA".
#
# This row also carries the 2026-09-22 fix that put the detail line back on the MEDIUM tile.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"

FIX="$(dirname "$0")/weather_fixture.py"

push_report() { # push_report <local.json>
  adb shell run-as $PKG mkdir -p files/weather
  adb shell "run-as $PKG sh -c 'cat > files/weather/last-report.json'" < "$1"
  # android.util.AtomicFile restores the base file from .bak when it reads, so a leftover .bak from a
  # real fetch would quietly undo the fixture and the row would measure the real sky.
  adb shell run-as $PKG rm -f files/weather/last-report.json.bak
}

show_start() {
  adb shell am force-stop $PKG; sleep 1
  adb shell input keyevent KEYCODE_HOME; sleep 5
}

# The weather tile FLIPS between its front (the animated sky) and the 3-day face on R3 A8's own timer,
# so a single dump catches the sky only about half the time. The first run of this row asserted against
# whichever face happened to be up and recorded 8 failures against scenes that were all drawing
# correctly — including a "the tile shows the day's high and low" that passed off the 3-day face while
# the precipitation assertion failed in the same dump. Waiting for the front is the honest read; a scene
# that never comes up still fails, after 14 s.
dump_until() { # dump_until <needle> <xml out>
  for _ in $(seq 1 14); do
    dump "$2" || return 1
    grep -q "$1" "$2" && return 0
    command sleep 1
  done
  return 1
}

row_begin ITEM2 "Weather tile: one animated sky per condition, and the medium tile's detail line"

# A live fetch overwrites the fixture within seconds of the shell starting, so the device goes off the
# network for this row. Nothing is being hidden: the mapping under test is a pure function of the
# report, and the fixture is the report.
adb shell cmd connectivity airplane-mode enable >/dev/null 2>&1
sleep 3
assert_eq "the device is off the network" "1" "$(adb shell settings get global airplane_mode_on | tr -d '\r')"

for spec in 0:day:clear 2:day:partly 3:day:cloudy 45:day:fog 63:day:rain 73:day:snow 95:day:thunder 81:day:rainbow 0:night:clear; do
  code="${spec%%:*}"; rest="${spec#*:}"; when="${rest%%:*}"; scene="${rest##*:}"
  f="$ROW_DIR/report-$scene-$when.json"
  python3 "$FIX" "$code" "$when" "$f" >/dev/null
  push_report "$f"
  show_start
  dump_until "weather_sky:$scene" "$ROW_DIR/sky-$scene-$when.xml"
  adb exec-out screencap -p > "$ROW_DIR/sky-$scene-$when.png"
  assert_contains "WMO $code by $when draws the $scene sky" "weather_sky:$scene" "$(cat "$ROW_DIR/sky-$scene-$when.xml")"
done

# ---- the detail line the medium tile lost and got back (2026-09-22) ------------------------------
python3 "$FIX" 0 day "$ROW_DIR/report-detail.json" >/dev/null
push_report "$ROW_DIR/report-detail.json"
show_start
dump_until "weather_sky:clear" "$ROW_DIR/detail.xml"
adb exec-out screencap -p > "$ROW_DIR/detail.png"
note "weather tile bounds: $(bounds "$ROW_DIR/detail.xml" 'tile:shell:weather')"
assert_contains "the tile shows the day's high and low" "72" "$(cat "$ROW_DIR/detail.xml")"
assert_contains "the tile shows the precipitation" "Precip" "$(cat "$ROW_DIR/detail.xml")"

# ---- and specifically on the MEDIUM tile, which is where the line had gone missing ---------------
# The tile ships WIDE, so the assertions above ran against a wide tile and would have passed before the
# 2026-09-22 fix too. This sets it to MEDIUM and asserts the difference the fix actually makes: the
# high/low and precipitation are there, and the wind — the third item, which only the wide tile has
# room for — is not.
adb shell am force-stop $PKG; sleep 1
adb shell run-as $PKG cat files/start_layout.json > "$ROW_DIR/layout-before.json"
python3 "$HERE/layout_size.py" "$ROW_DIR/layout-before.json" shell:weather MEDIUM > "$ROW_DIR/layout-medium.json"
adb shell "run-as $PKG sh -c 'cat > files/start_layout.json'" < "$ROW_DIR/layout-medium.json"
show_start
dump_until "weather_sky:clear" "$ROW_DIR/medium.xml"
adb exec-out screencap -p > "$ROW_DIR/medium.png"
note "medium weather tile bounds: $(bounds "$ROW_DIR/medium.xml" 'tile:shell:weather')"
assert_contains "the MEDIUM tile shows the high and low" "72" "$(cat "$ROW_DIR/medium.xml")"
assert_contains "the MEDIUM tile shows the precipitation" "Precip" "$(cat "$ROW_DIR/medium.xml")"
assert_absent "the MEDIUM tile leaves the wind to the wide tile" "Wind" "$(cat "$ROW_DIR/medium.xml")"

# Put the layout back the way it was found.
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'cat > files/start_layout.json'" < "$ROW_DIR/layout-before.json"

adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1
row_end
