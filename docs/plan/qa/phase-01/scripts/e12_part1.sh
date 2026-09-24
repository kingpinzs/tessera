#!/usr/bin/env bash
# E12 part 1: caption baseline (fixtures present when the shell first became Home), app list, no captions, search, jump grid.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E12.txt
echo "# E12 $(date -Iseconds)" > "$LOG"
cat >> "$LOG" <<'TXT'
## method: the fixtures were installed after this AVD's shell first became Home, so the row's precondition
## ("fixture APKs installed before onboarding") is recreated by deleting only the caption store (shared_prefs/applist_new.xml,
## run-as on the debug build) with the shell stopped: its next start is the shell first seeing itself as Home with every fixture present.
TXT
adb shell am force-stop app.tileshell
echo "\$ run-as app.tileshell ls shared_prefs: $(adb shell run-as app.tileshell ls shared_prefs | tr '\n' ' ')" >> "$LOG"
adb shell run-as app.tileshell rm shared_prefs/applist_new.xml
adb shell input keyevent KEYCODE_HOME; sleep 5
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep "baseline taken" | tail -1 >> "$LOG"
echo '## swipe left on Start' >> "$LOG"
adb shell input swipe 900 1200 150 1200 250; sleep 2
dump "$OUT/applist_top.xml"; adb exec-out screencap -p > "$OUT/applist_top.png"
echo "app_list=$(grep -c 'resource-id="app_list"' "$OUT/applist_top.xml") applist_search=$(grep -c 'resource-id="applist_search"' "$OUT/applist_top.xml")" >> "$LOG"
# walk the whole list
: > "$OUT/rows_all.txt"
for i in $(seq 1 30); do
  dump "$OUT/walk.xml"
  grep -o 'resource-id="applist_\(row\|new\|header\):[^"]*"' "$OUT/walk.xml" | sed 's/resource-id="//; s/"$//' >> "$OUT/rows_all.txt"
  last=$(grep -o 'resource-id="applist_row:[^"]*"' "$OUT/walk.xml" | tail -1)
  [ "$last" = "$prev" ] && break; prev=$last
  # A slow swipe: 1200 px in 300 ms flings on (1562 px measured, 2026-09-24), leaving one row of overlap between dumps,
  # so a slightly longer fling skipped org.fossify.contacts (phase 15 E1 run 3). At 1.5 s it scrolls ~1220 px, 4 rows overlap.
  adb shell input swipe 540 1900 540 700 1500; sleep 1.2
done
rm -f "$OUT/walk.xml"
sort -u "$OUT/rows_all.txt" -o "$OUT/rows_all.txt"
echo "rows seen: $(grep -c '^applist_row:' "$OUT/rows_all.txt"); headers: $(grep '^applist_header:' "$OUT/rows_all.txt" | cut -d: -f2 | tr '\n' ' '); New captions: $(grep -c '^applist_new:' "$OUT/rows_all.txt")" >> "$LOG"
echo "fixture rows present: $(for p in com.fsck.k9 eu.faircode.email org.oxycblt.auxio org.akanework.gramophone org.fossify.musicplayer net.osmand.plus app.organicmaps org.fdroid.fdroid org.fdroid.basic com.looker.droidify deckers.thibault.aves.libre org.fossify.gallery org.fossify.contacts opencontacts.open.com.opencontacts net.sourceforge.opencamera org.fossify.camera org.fossify.messages; do grep -q "^applist_row:$p$" "$OUT/rows_all.txt" && printf '%s ' "$p"; done)" >> "$LOG"
echo '## search' >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell input swipe 900 1200 150 1200 250; sleep 2
dump "$OUT/applist_top2.xml"; tap_id "$OUT/applist_top2.xml" applist_search; sleep 1
adb shell input text fossify; sleep 2
dump "$OUT/search_fossify.xml"; adb exec-out screencap -p > "$OUT/search_fossify.png"
echo "results for 'fossify': $(grep -o 'resource-id="applist_row:[^"]*"' "$OUT/search_fossify.xml" | sed 's/resource-id="applist_row://; s/"$//' | tr '\n' ' ')" >> "$LOG"
echo "installed labels containing Fossify (package org.fossify.*): $(adb shell pm list packages org.fossify | sed 's/package://' | sort | tr '\n' ' ')" >> "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
echo '## jump grid' >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell input swipe 900 1200 150 1200 250; sleep 2
dump "$OUT/applist_top3.xml"
H=$(grep -o 'resource-id="applist_header:[^"]*"' "$OUT/applist_top3.xml" | head -1 | sed 's/resource-id="//; s/"$//')
echo "tap first header: $H" >> "$LOG"; tap_id "$OUT/applist_top3.xml" "$H"; sleep 2
dump "$OUT/jump_grid.xml"; adb exec-out screencap -p > "$OUT/jump_grid.png"
echo "jump_grid=$(grep -c 'resource-id="jump_grid"' "$OUT/jump_grid.xml") cells: $(grep -o 'resource-id="jump_cell:[^"]*"' "$OUT/jump_grid.xml" | sed 's/resource-id="jump_cell://; s/"$//' | tr '\n' ' ')" >> "$LOG"
tap_id "$OUT/jump_grid.xml" jump_cell:O; sleep 2
dump "$OUT/after_jump_O.xml"; adb exec-out screencap -p > "$OUT/after_jump_O.png"
echo "after jump to O: jump_grid=$(grep -c 'resource-id="jump_grid"' "$OUT/after_jump_O.xml"); first header on screen: $(grep -o 'resource-id="applist_header:[^"]*"[^>]*bounds="[^"]*"' "$OUT/after_jump_O.xml" | head -1)" >> "$LOG"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep "jump" | tail -2 >> "$LOG"
