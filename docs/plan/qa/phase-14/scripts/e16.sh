#!/usr/bin/env bash
# Phase 14 E16 — every diagnostics line in Decisions is asserted by at least one row: grep the union of
# qa/phase-14/*/ring-*.txt saved by THIS build's run (the rows whose log's installed APK id is the one on the device now),
# each pattern at least once; never one final ring, which every `am force-stop` / `layout_restore` resets (C-20). The
# patterns are the doc's, literally (r3 V13). `launch <id> failed: ActivityNotFoundException` is written and exercised by
# no row — recorded (C-26), not asserted.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E16 "diagnostics coverage: every Decisions line in this build's saved rings"
APK_NOW="$(installed_apk_id)"
note "device apk: $APK_NOW"
: > "$ROW_DIR/rows-used.txt"; : > "$ROW_DIR/union.txt"
for log in "$QA"/E*/E*.txt; do
  d="$(dirname "$log")"; r="$(basename "$d")"
  [ "$r" = E16 ] && continue
  [ "$(basename "$log" .txt)" = "$r" ] || continue          # the row's own log (E3/E3.txt), not a kept run's
  id="$(grep -m1 '^apk installed' "$log" | awk '{print $3}')"
  if [ "$id" = "$APK_NOW" ]; then
    echo "$r $id $(ls "$d"/ring-*.txt 2>/dev/null | wc -l) ring files" >> "$ROW_DIR/rows-used.txt"
    cat "$d"/ring-*.txt >> "$ROW_DIR/union.txt" 2>/dev/null
  else
    echo "SKIPPED $r (apk $id, not this build)" >> "$ROW_DIR/rows-used.txt"
  fi
done
note "rows: $(grep -vc '^SKIPPED' "$ROW_DIR/rows-used.txt") on this build, $(grep -c '^SKIPPED' "$ROW_DIR/rows-used.txt") skipped; union $(wc -l < "$ROW_DIR/union.txt") lines"
assert_ne "at least one row ran on this build" "0" "$(grep -vc '^SKIPPED' "$ROW_DIR/rows-used.txt")"
U="$(cat "$ROW_DIR/union.txt")"
for p in "opened by swipe" "opened by voice" "opened by voice (doors)" "closed by back" "closed by home" "closed by swipe" \
         "closed by voice" "pods enabled: " \
         "launch agenda -> " "launch weather -> " "launch nowplaying -> " "launch reminders -> " \
         "launch nowplaying failed: slot unassigned" "launch reminders failed: no session" \
         "[start] page=POD_BAY" "[start] page=START" "[start] page=APP_LIST" "[start] home: page START" \
         "[fluent] pod_bay source=static"; do
  assert_contains "the union holds [$p]" "$p" "$U"
done
for pod in agenda weather nowplaying reminders; do
  assert_ne "pod $pod: a rows line" "" "$(printf '%s\n' "$U" | grep -F "pod $pod: " | grep -F ' rows' | head -1)"
  assert_ne "pod $pod: an empty line" "" "$(printf '%s\n' "$U" | grep -F "pod $pod: empty: " | head -1)"
done
record "launch <id> failed: ActivityNotFoundException (C-26: no row exercises it)" "$(printf '%s\n' "$U" | grep -c 'failed: ActivityNotFoundException') lines"
row_end
