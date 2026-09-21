#!/usr/bin/env bash
# grep -c counts LINES and a uiautomator dump is one line, so the counts in E2b.txt that needed occurrences
# (not presence) are recounted here from the dumps E2b already saved. Read-only: no device commands.
set -u
OUT=$1; LOG=$OUT/E2b.txt
A=app.tileshell.testclient.a
TILE_A="tile:app:$A/app.tileshell.testclient.VerbActivity:0"
{
  echo ''
  echo '## occurrence counts re-read from the saved dumps (grep -o, not grep -c)'
  echo "tiles for A drawn on Start (e2b_start.xml): $(grep -o "resource-id=\"$TILE_A\"" "$OUT/e2b_start.xml" | wc -l)"
  echo "rows listed after Back (e2b_after_back.xml): $(grep -o 'resource-id="applist_row:' "$OUT/e2b_after_back.xml" | wc -l)"
  echo "jump grid cells (e2b_grid.xml): $(grep -o 'resource-id="jump_cell:' "$OUT/e2b_grid.xml" | wc -l)"
  echo "menu bands in the after-Back dump: $(grep -o 'resource-id="applist_menu"' "$OUT/e2b_after_back.xml" | wc -l)"
  echo "menu bands in the after-tap-outside dump: $(grep -o 'resource-id="applist_menu"' "$OUT/e2b_after_outside.xml" | wc -l)"
} >> "$LOG"
tail -7 "$LOG"
