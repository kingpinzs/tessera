#!/usr/bin/env bash
# E5: secondary tiles through the client library (build task 6, wired into Start by the lead).
#   a test APK calls secondary.requestCreate -> the confirmation band appears -> accepting pins a tile whose
#   tap launches the owner with the request's arguments (read out of the APK's own TextView, because
#   `dumpsys activity` prints only "(has extras)") -> declining pins nothing -> uninstalling the owner removes
#   every one of its secondary tiles, including one inside a folder.
# usage: e5.sh <out dir>
set -u
source "$(dirname "$0")/gestures.sh"
source "$(dirname "$0")/layout.sh"
OUT=$1; LOG=$OUT/E05.txt
mkdir -p "$OUT"; : > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
APK=app/../testapps/tileclient-a/build/outputs/apk/debug/tileclient-a-debug.apk
OWNER=app.tileshell.testclient.a
ACT=$OWNER/app.tileshell.testclient.VerbActivity

say "# E5 $(date -Iseconds)"
layout_save "$OUT/layout_before.json"
layout_restore "$(dirname "$0")/../baseline_layout.json"
adb install -r -g "$APK" 2>&1 | tail -1 | tee -a "$LOG"

# request <tileId> <displayName> <arguments> <size>: the test APK calls secondary.requestCreate through the
# client library. The call is driven by the activity's `verb` extra rather than by tapping its PIN TILE button:
# synthetic taps do not reach that Button on this AVD (verified by hand — the button's own log line never
# appears), and the extra runs exactly the same client-library code path.
request() {
  adb shell am start -n "$ACT" --es verb secondary.requestCreate --es tileId "$1" --es displayName "$2" \
      --es arguments "$3" --es size "$4" --ez logo true >/dev/null
  sleep 2.5
  dump "$OUT/e5_client_$1.xml"
  say "the client's own read-back: $(python3 "$(dirname "$0")/nodetext.py" "$OUT/e5_client_$1.xml" | grep -m1 requestCreate)"
}

# button <dump> <accept|cancel>: the band's button, by test tag when it is exported and by its label otherwise.
button() {
  center "$1" "secondary_pin_prompt_$2" 2>/dev/null && return 0
  python3 - "$1" "$2" <<'PY'
import re, sys
label = "pin" if sys.argv[2] == "accept" else "cancel"
s = open(sys.argv[1]).read()
for m in re.finditer(r'<node[^>]*>', s):
    n = m.group(0)
    t = re.search(r'text="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if t and b and t.group(1).strip().lower() == label:
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
}

say "--- 1. the request is HELD while the requester is in front (never drawn over another app) ---"
request st1 "Jen" "chat=42" medium
adb exec-out screencap -p > "$OUT/e5_requester_front.png"
dump "$OUT/e5_requester_front.xml"
grep -q 'resource-id="secondary_pin_prompt"' "$OUT/e5_requester_front.xml" \
  && say "FAIL the confirmation band is drawn over the requesting app" \
  || say "PASS nothing is drawn over the requester"

say "--- 2. the band appears the next time Start is shown, and accepting pins the tile ---"
ensure_start
dump "$OUT/e5_prompt.xml"
adb exec-out screencap -p > "$OUT/e5_prompt.png"
if grep -q 'resource-id="secondary_pin_prompt"' "$OUT/e5_prompt.xml"; then
  say "PASS the confirmation band is showing"
  say "  app: $(python3 "$(dirname "$0")/nodetext.py" "$OUT/e5_prompt.xml" secondary_pin_prompt_app)"
  say "  tile name: $(python3 "$(dirname "$0")/nodetext.py" "$OUT/e5_prompt.xml" secondary_pin_prompt_name)"
else
  say "FAIL no confirmation band on Start"
fi
ACC=$(button "$OUT/e5_prompt.xml" accept)
[ -n "$ACC" ] && { say "tapping Pin at $ACC"; adb shell input tap ${ACC% *} ${ACC#* }; sleep 2; }
adb exec-out screencap -p > "$OUT/e5_pinned.png"
say "order after accepting:"; layout_order | tee -a "$LOG"

say "--- 3. tapping the pinned tile launches the owner with the request's arguments ---"
ensure_start
dump "$OUT/e5_start_with_tile.xml"
T=$(center "$OUT/e5_start_with_tile.xml" "tile:secondary:$OWNER:st1" || true)
if [ -n "$T" ]; then
  adb shell input tap ${T% *} ${T#* }; sleep 3
  dump "$OUT/e5_launched.xml"
  adb exec-out screencap -p > "$OUT/e5_launched.png"
  say "top activity: $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //;s/ .*//')"
  say "the app's own read-back of what it was launched with:"
  python3 "$(dirname "$0")/nodetext.py" "$OUT/e5_launched.xml" "" | grep -E "TILE_ID|ARGS|ACTIVATED" | tee -a "$LOG"
else
  say "FAIL no secondary tile on Start to tap"
fi

say "--- 4. declining pins nothing ---"
ensure_start
request st2 "Sam" "chat=7" small
ensure_start
dump "$OUT/e5_prompt2.xml"
DEC=$(button "$OUT/e5_prompt2.xml" cancel)
if [ -n "$DEC" ]; then
  say "tapping Cancel at $DEC"
  adb shell input tap ${DEC% *} ${DEC#* }; sleep 2
else
  say "FAIL no Cancel button in the band"
fi
adb exec-out screencap -p > "$OUT/e5_declined.png"
say "order after declining (st2 must NOT be there):"; layout_order | tee -a "$LOG"

say "--- 5. a duplicate tileId updates the tile instead of making a second one ---"
ensure_start
request st1 "JenRenamed" "chat=99" medium
ensure_start
sleep 1
say "order after the duplicate request:"; layout_order | tee -a "$LOG"

say "--- 6. put the tile inside a folder, then uninstall the owner ---"
ensure_start
dump "$OUT/e5_before_folder.xml"
FROM=$(tile_center "$OUT/e5_before_folder.xml" "secondary:$OWNER:st1" || true)
TO=$(edit_point "$OUT/e5_before_folder.xml" "slot:PEOPLE" || true)
if [ -n "$FROM" ] && [ -n "$TO" ]; then
  down ${FROM% *} ${FROM#* }; sleep 1.1
  glide ${FROM% *} ${FROM#* } ${TO% *} ${TO#* } 8
  sleep 0.8
  up ${TO% *} ${TO#* }; sleep 1.5
  adb shell input keyevent KEYCODE_BACK; sleep 1
  say "layout with the secondary tile in a folder:"; layout_order | tee -a "$LOG"
fi
adb uninstall "$OWNER" 2>&1 | tail -1 | tee -a "$LOG"
sleep 3
ensure_start
adb exec-out screencap -p > "$OUT/e5_after_uninstall.png"
say "order after uninstalling the owner (no secondary tile anywhere):"; layout_order | tee -a "$LOG"

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[livetile\]|\[layout\]|\[secondary\]" | tail -40 > "$OUT/e5_diag.txt"
layout_restore "$OUT/layout_before.json"
say "baseline layout restored"
