#!/usr/bin/env bash
# Phase 14 — every row on ONE final APK (the gate's precondition). Each row's earlier run directory is kept under
# <row>-apk-<its apk id>, then the row's own driver runs unchanged; exit codes go to FINAL/<row>.rc, read from the files.
# E13 is not repeated when its log already names the installed APK (it is the longest row); E16 runs last, over the
# rings these runs saved.
#   final_run.sh [row ...]      default: E1 E2 E3 E4 E5 E6 E7 E8 E9 E10 E11 E12 E14 E15 E17 E16
set -uo pipefail
export ANDROID_SERIAL=emulator-5554
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QA="$(cd "$HERE/.." && pwd)"
OUT="$QA/FINAL"; mkdir -p "$OUT"
apk_now="$(adb shell md5sum "$(adb shell pm path app.tileshell | head -1 | tr -d '\r' | sed 's/^package://')" | cut -c1-16 | tr -d '\r')"
echo "final APK on the device: $apk_now ($(date -Is))" | tee "$OUT/final-run.txt"
rows=("$@"); [ ${#rows[@]} -eq 0 ] && rows=(E1 E2 E3 E4 E5 E6 E7 E8 E9 E10 E11 E12 E14 E15 E17 E16)
for row in "${rows[@]}"; do
  driver="$HERE/$(echo "$row" | tr 'A-Z' 'a-z').sh"
  if [ -d "$QA/$row" ] && [ -f "$QA/$row/$row.txt" ]; then
    was="$(grep -m1 '^apk installed' "$QA/$row/$row.txt" | awk '{print $3}' | cut -c1-8)"
    keep="$QA/$row-apk-${was:-unknown}"
    n=1; while [ -e "$keep" ]; do n=$((n + 1)); keep="$QA/$row-apk-${was:-unknown}-$n"; done
    mv "$QA/$row" "$keep"
  fi
  bash "$driver" > "$OUT/$row.out" 2>&1; echo $? > "$OUT/$row.rc"
  echo "$row rc=$(cat "$OUT/$row.rc") $(grep -hE "^$row: [0-9]+ passed" "$QA/$row/$row.txt" 2>/dev/null | tail -1) apk=$(grep -m1 '^apk installed' "$QA/$row/$row.txt" 2>/dev/null | awk '{print $3}' | cut -c1-8)" | tee -a "$OUT/final-run.txt"
done
echo "done $(date -Is)" | tee -a "$OUT/final-run.txt"
