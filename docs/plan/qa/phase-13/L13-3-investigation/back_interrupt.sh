#!/usr/bin/env bash
# L13-3 side finding: a touch during the Back-driven pivot (app list -> Start) and later Backs.
#   back_interrupt.sh <outdir> <interrupt|control> [KEY]
# interrupt: KEY (default 4 = Back; 3 = Home) on the app list (no band), then 15 ms later a still 300-ms press
# mid-pivot; control: KEY alone. Then, twice: onto the app list, KEY, and read which page shows (app_list's left edge
# is 0 when the app list shows; the node is absent from the dump when Start shows).
set -uo pipefail
export ANDROID_SERIAL=emulator-5556 TMPDIR=/tmp/claude-1000/l13-3-tmp
HERE="$(cd "$(dirname "$0")" && pwd)"; . "$HERE/../scripts/lib.sh"; . "$HERE/../scripts/p13.sh"; export ANDROID_SERIAL=emulator-5556
OUT="$1"; MODE="$2"; KEY="${3:-4}"; mkdir -p "$OUT"
page() { dump_ui "$OUT/$1.xml"; grep -o 'resource-id="app_list"[^>]*bounds="\[-\?[0-9]*' "$OUT/$1.xml" | grep -o '\[-\?[0-9]*$' | tr -d '['; }
adb shell am force-stop $PKG; sleep 1; show_start 5          # a fresh StartActivity: its Back collector starts alive
to_app_list 2
MARK="$(ring_mark)"
if [ "$MODE" = interrupt ]; then
  adb shell "input keyevent $KEY; sleep 0.015; input swipe 540 1206 540 1206 300"
else
  adb shell "input keyevent $KEY"
fi
sleep 2
echo "key $KEY, $MODE. after the first key: app_list x = $(page 1-after-first-key) (0 = app list shown, empty = Start shown)" | tee "$OUT/summary.txt"
for k in 2 3; do
  [ "$(page $k-pre)" = 0 ] || to_app_list 2
  echo "  before key $k: app_list x = $(page $k-before)" | tee -a "$OUT/summary.txt"
  adb shell input keyevent $KEY; sleep 2
  echo "  after key $k: app_list x = $(page $k-after)" | tee -a "$OUT/summary.txt"
done
ring_since "$MARK" > "$OUT/slice.txt"
echo "back lines in the ring: $(grep -c 'back on app list\|back on Start' "$OUT/slice.txt") (diagnostic build only); home lines: $(grep -c '\[start\] home: page 0' "$OUT/slice.txt")" | tee -a "$OUT/summary.txt"
