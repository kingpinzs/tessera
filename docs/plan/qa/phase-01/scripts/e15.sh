#!/usr/bin/env bash
# E15: the test APK updates its own tile (text, queue of 5, badge), clear, and an expired item.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E15.txt; PKG=app.tileshell.testclient.a; TILE="tile:app:$PKG/app.tileshell.testclient.VerbActivity:0"
N="python3 $(dirname "$0")/nodes.py"
verb() { adb shell "am start -n $PKG/app.tileshell.testclient.VerbActivity $*" >/dev/null 2>&1; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell logcat -d -s TileClient | tail -1 >> "$LOG"; }
faces() { # faces <label> <samples>
  local seen=""
  for i in $(seq 1 $2); do
    dump "$OUT/$1_$i.xml" 2>/dev/null
    seen="$seen$($N "$OUT/$1_$i.xml" "$TILE" | sed 's/.*texts=//')\n"
    sleep 2.2
  done
  echo -e "$1 faces seen:\n$(echo -e "$seen" | sort -u | grep -v '^$' | sed 's/^/    /')" >> "$LOG"
}
echo "# E15 $(date -Iseconds)" > "$LOG"
echo '## tile.update with text' >> "$LOG"
verb '--es verb tile.update --es text "Meeting at 4|Room 12|With the team"'
faces update 5
echo '## badge.update 3' >> "$LOG"
verb '--es verb badge.update --ei value 3'
dump "$OUT/badge.xml"; echo "badge node: $($N "$OUT/badge.xml" "badge:app:$PKG/app.tileshell.testclient.VerbActivity:0")" >> "$LOG"
echo '## queue of 5 (demo.queue: enableQueue then q1..q5)' >> "$LOG"
verb '--es verb demo.queue'
faces queue 12
echo '## tile.clear' >> "$LOG"
verb '--es verb tile.clear'
faces cleared 3
echo '## badge.clear' >> "$LOG"
verb '--es verb badge.clear'
dump "$OUT/badge_cleared.xml"; echo "badge node after clear: $($N "$OUT/badge_cleared.xml" "badge:app:$PKG/app.tileshell.testclient.VerbActivity:0")" >> "$LOG"
echo '## expiry: tile.update with expiresInMs 8000' >> "$LOG"
verb '--es verb tile.update --es text "Expires in 8 s" --el expiresInMs 8000'
faces expiring 2
sleep 12
faces expired 3
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[livetile\]|\[api\]" | tail -8 >> "$LOG"
