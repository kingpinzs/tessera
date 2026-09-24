#!/usr/bin/env bash
# Phase 15's own driver helpers, sourced AFTER lib.sh (the shared floor: row_begin / row_end, the asserts, ring_mark /
# ring_since / reply_since / ring_save, record, fill_volume, the fixture recordings). Everything here drives the real
# shell on the emulator named by ANDROID_SERIAL; nothing is simulated.

QROOT="$REPO/docs/plan/qa"
P15="$QROOT/phase-15"
DRV_RUNNER="app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner"

# The phase 05 gesture driver's dump (C-10): windows that never idle — a running timer or stopwatch, the ring toast,
# the in-use overlay (a separate, unfocused window) — are dumped with setWaitForIdleTimeout(0). The window report
# (dump.windows) is kept beside the dump as <out>.windows.
gdump() { # out.xml
  local out="$1" i
  : > "$out.drv"
  for i in 1 2 3 4 5 6 7 8; do
    adb shell am instrument --no-restart -r -w -e op dump -e out /sdcard/Download/p15.xml "$DRV_RUNNER" >> "$out.drv" 2>&1
    adb shell cat /sdcard/Download/p15.xml > "$out" 2>/dev/null
    if grep -q '<node' "$out"; then
      grep -o 'gesture.windows=.*' "$out.drv" | tail -1 | tr -d '\r' > "$out.windows"
      return 0
    fi
    sleep 0.7
  done
  echo "(dump failed)" > "$out"
  return 1
}

# The resumed activity line (dumpsys), for "X is still the resumed activity" checks.
resumed() { adb shell dumpsys activity activities | grep -E 'topResumedActivity' | head -1 | tr -d '\r'; }

# The device's wall clock (ms) and the host's, for RV12's clock restore.
device_ms() { adb shell date +%s%3N | tr -d '\r'; }

# Jump the emulator's clock (AVD limits in the preamble): automatic time off, then `cmd alarm set-time`, which the shell
# user may call. Prints the device's ms afterwards.
jump_clock() { # epoch_ms
  adb shell settings put global auto_time 0
  adb shell cmd alarm set-time "$1" >/dev/null
  sleep 1
  device_ms
}

# RV12's clock restore: the host's UTC time while root, automatic time back on, unroot, and a RECORD of the device-host
# difference within 2 s. A row that moved the clock then force-stops the shell and goes Home (T15-44), so no ring line
# stamped on the jumped clock outlives it.
clock_restore() {
  adb root >/dev/null 2>&1; adb wait-for-device
  adb shell date -u "$(date -u +%m%d%H%M%Y.%S)" >/dev/null
  adb shell settings put global auto_time 1
  adb unroot >/dev/null 2>&1; adb wait-for-device
  local d h
  d="$(adb shell date +%s | tr -d '\r')"; h="$(date +%s)"
  assert_within "device clock back within 2 s of the host (RV12)" "$h" "$d" 2
  adb shell am force-stop app.tileshell
  adb shell input keyevent KEYCODE_HOME
  sleep 3
}

# How many of the shell's clock alarms (alarms and timers: tag app.tileshell.clock.FIRE) are PENDING in AlarmManager —
# read from the "N pending alarms:" block only, since the same tag also appears in dumpsys alarm's history statistics.
clock_pending() {
  adb shell dumpsys alarm | tr -d '\r' | python3 -c '
import re, sys
lines = sys.stdin.read().splitlines(); n = 0; inside = False
for l in lines:
    if re.match(r"^\s*\d+ pending alarms:", l): inside = True; continue
    if inside and not l.startswith("    "): inside = False
    if inside and "tag=*walarm*:app.tileshell.clock.FIRE" in l: n += 1
print(n)'
}

# The alarm and timer baseline (T15-45): nothing of the clock's pending in AlarmManager, and empty stores in
# device-protected storage.
assert_clock_empty() { # label
  local stores
  # ONE string to adb shell: it re-joins its arguments, and "sh -c cat a b" would run a bare cat reading stdin.
  stores="$(adb shell "run-as app.tileshell sh -c 'cat /data/user_de/0/app.tileshell/files/alarms.json /data/user_de/0/app.tileshell/files/timers.json 2>/dev/null'" < /dev/null | tr -d '\r\n ')"
  case "$stores" in ""|"[]"|"[][]") stores=empty ;; esac
  assert_eq "$1: no shell alarm or timer pending" 0 "$(clock_pending)"
  assert_eq "$1: alarm and timer stores empty" empty "$stores"
}

# The ms instant of the next wall-clock HH:MM in the device's time zone, strictly after the device's now.
next_wall_ms() { # HH MM [days_ahead]
  local tz now
  tz="$(adb shell getprop persist.sys.timezone | tr -d '\r')"; now="$(device_ms)"
  python3 - "$tz" "$now" "$1" "$2" "${3:-0}" <<'PY'
import sys, datetime, zoneinfo
tz = zoneinfo.ZoneInfo(sys.argv[1] or "UTC"); now = int(sys.argv[2]); h, m, ahead = int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
t = datetime.datetime.fromtimestamp(now / 1000, tz)
c = t.replace(hour=h, minute=m, second=0, microsecond=0) + datetime.timedelta(days=ahead)
if c.timestamp() * 1000 <= now: c += datetime.timedelta(days=1)
print(int(c.timestamp() * 1000))
PY
}

# Tap a node's centre in a gesture-driver dump.
gtap() { # dump.xml resource-id
  local b
  b="$(bounds "$1" "$2")"
  [ -n "$b" ] || { note "gtap: no $2 in $1"; return 1; }
  # shellcheck disable=SC2086
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
