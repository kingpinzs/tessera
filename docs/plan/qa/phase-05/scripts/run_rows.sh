#!/usr/bin/env bash
# Run phase 05 rows one after another (one emulator, one driver at a time) and print each row's summary.
#   run_rows.sh e7 e10 e11
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for r in "$@"; do
  row="$(echo "$r" | tr a-z A-Z)"
  [ "$r" = "e3motion" ] && row=E3M
  [ -f "$HERE/../$row/$row.txt" ] && mv "$HERE/../$row/$row.txt" "$HERE/../$row/$row-prev-$(date +%H%M%S).txt"
  bash "$HERE/$r.sh" > "$HERE/../.run_$r.out" 2>&1
  echo "$r rc=$? $(tail -1 "$HERE/../$row/$row.txt" 2>/dev/null)"
done
