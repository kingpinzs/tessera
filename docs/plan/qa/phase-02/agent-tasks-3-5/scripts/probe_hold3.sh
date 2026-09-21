#!/usr/bin/env bash
# Probe 3: the same hold at several durations, each from a fresh app list, to find where the menu stops sticking.
source "$(dirname "$0")/p2.sh"
set -u
OUT=/tmp/claude-1000/-home-jeremyking/277bd9d2-7817-47e9-bc8f-0d5611a2abef/scratchpad/a35
for MS in 900 1200 2000 900; do
  open_applist "$OUT/p3_list.xml" || exit 1
  ROW=$(grep -o 'resource-id="applist_row:[^"]*"' "$OUT/p3_list.xml" | head -1 | sed 's/resource-id="//; s/"$//')
  hold_id "$OUT/p3_list.xml" "$ROW" "$MS"
  sleep 1
  dump "$OUT/p3_${MS}.xml"
  echo "hold ${MS} ms -> menu on screen: $(grep -c 'resource-id="applist_menu"' "$OUT/p3_${MS}.xml"); foreground: $(grep -o 'package="[^"]*"' "$OUT/p3_${MS}.xml" | sort -u | tr '\n' ' ')"
  diag | grep -E "\[applist\] context menu|\[launch\]" | tail -1
done
