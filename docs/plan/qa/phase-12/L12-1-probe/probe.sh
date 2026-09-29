#!/usr/bin/env bash
# L12-1 root-cause probe. Hypothesis: phase 02 E1 step 2 fails because the drop brings the quick-action burst back
# (548082c5) and, with a burst open, a tap on another tile closes it and leaves edit mode by Jeremy's one-tap ruling
# (2026-09-25, b3fe189). Phase 02's H8 (a tap on a different tile moves the selection) should still hold with no burst.
# Trial A = E1's own sequence (hold, drag, drop, tap another tile). Trial B = the same, with Back after the drop.
# Uses phase 02's own helpers; emulator-5554 only; no audio.
set -u
export PATH="$HOME/Android/Sdk/platform-tools:$PATH" ANDROID_SERIAL=emulator-5554
P2="$(cd "$(dirname "$0")/../../phase-02/scripts" && pwd)"; D="$(cd "$(dirname "$0")" && pwd)"
source "$P2/gestures.sh"; source "$P2/layout.sh"; source "$P2/assert.sh"
LOG=$D/RUN.txt; : > "$LOG"
ring() { adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[quick" | tail -8; }
has() { grep -c "resource-id=\"$2" "$1"; }
say "# L12-1 probe $(date -Iseconds) on $ANDROID_SERIAL"
layout_save "$D/layout_before.json"
for trial in A B; do
  say "== trial $trial"
  layout_restore "$P2/../baseline_layout.json" >/dev/null || { say "seed failed"; exit 1; }
  ensure_start; dump "$D/$trial-start.xml"
  FROM=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
  TO=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][0]['key'])")
  A=$(tile_center "$D/$trial-start.xml" "$FROM"); B=$(edit_point "$D/$trial-start.xml" "$TO")
  down ${A% *} ${A#* }; sleep 1.1; glide ${A% *} ${A#* } ${B% *} ${B#* } 8; sleep 2.6; up ${B% *} ${B#* }; sleep 1.4
  dump "$D/$trial-dropped.xml"
  say "after the drop: quick_burst nodes=$(has "$D/$trial-dropped.xml" quick_burst) quick_sat nodes=$(has "$D/$trial-dropped.xml" quick_sat:) edit_disc:unpin=$(has "$D/$trial-dropped.xml" edit_disc:unpin)"
  if [ "$trial" = B ]; then
    adb shell input keyevent KEYCODE_BACK; sleep 1.2; dump "$D/$trial-back.xml"
    say "after Back: quick_burst nodes=$(has "$D/$trial-back.xml" quick_burst) edit_disc:unpin=$(has "$D/$trial-back.xml" edit_disc:unpin)"
    SEL="$D/$trial-back.xml"
  else
    SEL="$D/$trial-dropped.xml"
  fi
  D1=$(bounds "$SEL" "edit_disc:unpin")
  OTHER=$(python3 -c "
import re
s = open('$SEL').read()
ids = [m.group(1) for m in re.finditer(r'resource-id=\"tile:([^\"]+)\"', s) if not m.group(1).startswith('dock:')]
print(ids[2] if len(ids) > 2 else ids[-1])")
  OXY=$(center "$SEL" "tile:$OTHER")
  say "unpin disc before the tap: [$D1]; tapping $OTHER at $OXY"
  adb shell input tap ${OXY% *} ${OXY#* }; sleep 1.0
  dump "$D/$trial-tapped.xml"; adb exec-out screencap -p > "$D/$trial-tapped.png"
  D2=$(bounds "$D/$trial-tapped.xml" "edit_disc:unpin")
  say "after the tap: edit_disc:unpin=$(has "$D/$trial-tapped.xml" edit_disc:unpin) at [$D2]"
  say "ring (last [edit]/[quick] lines):"; ring | tee -a "$LOG"
  adb shell input keyevent KEYCODE_BACK; sleep 1.2; ensure_start
done
layout_restore "$D/layout_before.json" >/dev/null && say "layout restored"
