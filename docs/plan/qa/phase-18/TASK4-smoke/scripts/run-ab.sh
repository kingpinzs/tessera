#!/usr/bin/env bash
# Legs a and b in full on the installed build: fixtures down (the earlier run's) and up, E6, E14, fixtures down.
H="$(dirname "$0")"; O="$H/.."
bash "$H/down.sh" a-e6 > "$O/run-ab-00-down.out" 2>&1
bash "$H/a-up.sh" > "$O/run-ab-01-up.out" 2>&1
for s in a1-view a2-music a3-chooser a4-pick b1-empty b2-recent b3-volume; do bash "$H/$s.sh" > "$O/run-ab-$s.out" 2>&1; echo "$s rc=$?"; done
ROWD=a-e6 bash "$H/down.sh" a-e6 > "$O/run-ab-99-down.out" 2>&1
grep -h "^== \|files_down\|files_up:" "$O"/run-ab-*.out
