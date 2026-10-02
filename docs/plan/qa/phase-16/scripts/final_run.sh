#!/usr/bin/env bash
# NOT USED for phase 16's gate (the owner's ruling, 2026-10-01 22:14: "Only test the fixes not EVERY THING" — INDEX
# Change Log). Each row's one passing run on the fix build is its evidence; nothing runs every row again. Kept as a
# runner for a NAMED list of rows (final_run.sh E5 E17), never the default list.
# Phase 16 — every row on ONE final APK (the gate's precondition; phase 14's form). Each row's earlier run directory is
# kept under <row>-apk-<its apk id>, then the row's own driver runs unchanged; exit codes go to FINAL/<row>.rc and are
# read from the files. E12 runs last (its map action once coincided with an emulator exit). E21 is not a driver: its
# tables are checked over the rings these runs saved (scripts/e21.sh, when the row writers' tables are in).
#   final_run.sh [row ...]      default: every row below, in this order
# TRUST's directory also holds the two trust reviewers' raw outputs (trustA-*, trustB-*, pick-probe-assemble.*), which
# the review files name by path: only the row's own files are moved aside for it.
set -uo pipefail
export ANDROID_SERIAL=emulator-5554
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QA="$(cd "$HERE/.." && pwd)"
OUT="$QA/FINAL"; mkdir -p "$OUT"
apk_now="$(adb shell md5sum "$(adb shell pm path app.tileshell | head -1 | tr -d '\r' | sed 's/^package://')" | cut -c1-16 | tr -d '\r')"
echo "final APK on the device: $apk_now ($(date -Is))" | tee "$OUT/final-run.txt"
rows=("$@")
[ ${#rows[@]} -eq 0 ] && rows=(E1 E2 E3 E4 E5 E6 E7 E8 E9 E10 E11 E13 E14 E15 E16 E17 E18 E19 E20 E22 E23 E24 E25 E26 E27 E28 E15_APK TRUST EDGE E12)
for row in "${rows[@]}"; do
  driver="$HERE/$(echo "$row" | tr 'A-Z' 'a-z').sh"
  if [ ! -f "$driver" ]; then echo "$row rc=none NO DRIVER ($driver)" | tee -a "$OUT/final-run.txt"; continue; fi
  if [ -d "$QA/$row" ] && [ -f "$QA/$row/$row.txt" ]; then
    was="$(grep -m1 '^apk installed' "$QA/$row/$row.txt" | awk '{print $3}' | cut -c1-8)"
    keep="$QA/$row-apk-${was:-unknown}"
    n=1; while [ -e "$keep" ]; do n=$((n + 1)); keep="$QA/$row-apk-${was:-unknown}-$n"; done
    if [ "$row" = TRUST ]; then
      mkdir -p "$keep"
      find "$QA/$row" -mindepth 1 -maxdepth 1 ! -name 'trustA-*' ! -name 'trustB-*' ! -name 'pick-probe-assemble.*' -exec mv {} "$keep/" \;
    else
      mv "$QA/$row" "$keep"
    fi
  fi
  bash "$driver" > "$OUT/$row.out" 2>&1; echo $? > "$OUT/$row.rc"
  echo "$row rc=$(cat "$OUT/$row.rc") $(grep -hE "^ *$row: [0-9]+ passed" "$QA/$row/$row.txt" 2>/dev/null | tail -1 | sed 's/^ *//') apk=$(grep -m1 '^apk installed' "$QA/$row/$row.txt" 2>/dev/null | awk '{print $3}' | cut -c1-8)" | tee -a "$OUT/final-run.txt"
  # A dead emulator ends the run: the standing rule is to stop and tell the owner, never to relaunch from a script.
  if ! adb get-state > /dev/null 2>&1; then echo "STOPPED after $row: emulator-5554 is not answering ($(date -Is))" | tee -a "$OUT/final-run.txt"; exit 4; fi
done
echo "done $(date -Is)" | tee -a "$OUT/final-run.txt"
