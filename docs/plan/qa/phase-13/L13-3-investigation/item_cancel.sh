#!/usr/bin/env bash
# L13-3 side check: the band leaves while a finger is held on its "Pin to Start" item (Back pressed mid-hold).
# Does the item run although the finger never lifted on it? (overlayItem reads the removal's synthetic up as a lift?)
#   item_cancel.sh <outdir> <back|noback>
set -uo pipefail
export ANDROID_SERIAL=emulator-5556 TMPDIR=/tmp/claude-1000/l13-3-tmp
HERE="$(cd "$(dirname "$0")" && pwd)"; . "$HERE/../scripts/lib.sh"; . "$HERE/../scripts/p13.sh"; export ANDROID_SERIAL=emulator-5556
OUT="$1"; MODE="$2"; mkdir -p "$OUT"
read -r X Y RID < "$TMPDIR/l13_3.state"
adb shell input swipe $X $Y $X $Y 850; sleep 0.8               # the band opens on the row
dump_ui "$OUT/band.xml"
set -- $(bounds "$OUT/band.xml" applist_menu_pin); PX=$(( ($1 + $3) / 2 )); PY=$(( ($2 + $4) / 2 ))
echo "Pin to Start item at ($PX,$PY)" | tee "$OUT/summary.txt"
MARK="$(ring_mark)"
if [ "$MODE" = back ]; then
  # finger down on the item for 1.2 s; Back 0.3 s in, while it is still down; the finger lifts 0.9 s after the band left
  adb shell "echo D \$(date +%s%3N); input swipe $PX $PY $PX $PY 1200 & sleep 0.3; echo K \$(date +%s%3N); input keyevent 4; wait; echo U \$(date +%s%3N)" | tr -d '\r' > "$OUT/ts.txt"
else
  # control: finger down on the item for 1.2 s, then Back after the lift
  adb shell "echo D \$(date +%s%3N); input swipe $PX $PY $PX $PY 1200; echo U \$(date +%s%3N); input keyevent 4; echo K \$(date +%s%3N)" | tr -d '\r' > "$OUT/ts.txt"
fi
sleep 1.5
ring_since "$MARK" > "$OUT/slice.txt"
dump_ui "$OUT/after.xml"
cat "$OUT/ts.txt" >> "$OUT/summary.txt"
grep -F 'pin to Start' "$OUT/slice.txt" | sed 's/^ *//' >> "$OUT/summary.txt"
echo "pin lines: $(grep -cF 'pin to Start' "$OUT/slice.txt")" | tee -a "$OUT/summary.txt"
