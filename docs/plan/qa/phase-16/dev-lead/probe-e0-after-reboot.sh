#!/usr/bin/env bash
# Probe (2026-10-01): E1's child, phase 15's e0.sh, failed its three Auxio lines ("Auxio is playing" got nothing) when
# it ran after phase 01 E4b, which reboots the device. Is the reboot the cause?
#   run A: e0.sh as the device is now (no reboot since Auxio last ran)
#   adb reboot, boot poll
#   run B: e0.sh at once
# The phase's own E0 directory is moved aside and put back; each run is kept under dev-lead/probe-e0/<A|B>.
set -u
export ANDROID_SERIAL=emulator-5554
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
P16="$(cd "$HERE/.." && pwd)"; QAR="$(cd "$P16/.." && pwd)"
OUT="$HERE/probe-e0"; mkdir -p "$OUT"
one() { # A|B
  local dir="$QAR/phase-15/E0" keep="$QAR/phase-15/E0.p16-probe-aside"
  [ -e "$dir" ] && mv "$dir" "$keep"
  ( E0_BASELINE="$P16/baseline_layout.json" bash "$QAR/phase-15/scripts/e0.sh" > "$OUT/$1.out" 2>&1; echo $? > "$OUT/$1.rc" )
  rm -rf "$OUT/$1"; [ -e "$dir" ] && mv "$dir" "$OUT/$1"
  [ -e "$keep" ] && mv "$keep" "$dir"
  echo "run $1: rc=$(cat "$OUT/$1.rc") $(tail -1 "$OUT/$1/E0.txt" 2>/dev/null) | $(grep -c '^FAIL' "$OUT/$1/E0.txt" 2>/dev/null) FAIL lines: $(grep '^FAIL' "$OUT/$1/E0.txt" 2>/dev/null | cut -c1-60 | tr '\n' ';')"
}
one A
echo "reboot at $(date -Is)"
adb reboot; adb wait-for-device
for i in $(seq 1 120); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 2; done; sleep 15
echo "booted at $(date -Is)"
one B
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME
