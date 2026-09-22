#!/usr/bin/env bash
# E5: secondary tiles through the client library (build task 6, wired into Start by the lead).
#   a test APK calls secondary.requestCreate -> the confirmation band appears -> accepting pins a tile whose
#   tap launches the owner with the request's arguments (read out of the APK's own TextView, because
#   `dumpsys activity` prints only "(has extras)") -> declining pins nothing -> uninstalling the owner removes
#   every one of its secondary tiles, including one inside a folder.
# usage: e5.sh <out dir>
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/gestures.sh"
source "$HERE/layout.sh"
source "$HERE/assert.sh"
OUT=$1; LOG=$OUT/E05.txt
mkdir -p "$OUT"; : > "$LOG"
APK=$HERE/../../../../testapps/tileclient-a/build/outputs/apk/debug/tileclient-a-debug.apk
OWNER=app.tileshell.testclient.a
ACT=$OWNER/app.tileshell.testclient.VerbActivity

say "# E5 $(date -Iseconds)"
build_guard
layout_save "$OUT/layout_before.json"
layout_restore "$HERE/../baseline_layout.json"
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
check_absent "nothing is drawn over the requesting app" "secondary_pin_prompt" \
  "$(grep -o 'secondary_pin_prompt' "$OUT/e5_requester_front.xml" | head -1)"
check_contains "the requester is what is on screen" "app.tileshell.testclient" \
  "$(adb shell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //;s/ .*//')"

say "--- 2. the band appears the next time Start is shown, and accepting pins the tile ---"
ensure_start
dump "$OUT/e5_prompt.xml"
adb exec-out screencap -p > "$OUT/e5_prompt.png"
if grep -q 'resource-id="secondary_pin_prompt"' "$OUT/e5_prompt.xml"; then
  PROMPT_APP=$(python3 "$HERE/nodetext.py" "$OUT/e5_prompt.xml" secondary_pin_app)
  PROMPT_NAME=$(python3 "$HERE/nodetext.py" "$OUT/e5_prompt.xml" secondary_pin_name)
  check "the band names the requesting app (H22)" "Tile client A" "$PROMPT_APP"
  check "and the tile it wants to pin" "Jen" "$PROMPT_NAME"
  check_contains "and shows a preview at the requested size" "secondary_pin_preview" \
    "$(grep -o 'secondary_pin_preview' "$OUT/e5_prompt.xml" | head -1)"
fi
check_contains "the confirmation band is showing on Start" "secondary_pin_prompt" \
  "$(grep -o 'secondary_pin_prompt' "$OUT/e5_prompt.xml" | head -1)"
ACC=$(button "$OUT/e5_prompt.xml" accept)
[ -n "$ACC" ] && { say "tapping Pin at $ACC"; adb shell input tap ${ACC% *} ${ACC#* }; sleep 2; }
adb exec-out screencap -p > "$OUT/e5_pinned.png"
say "order after accepting:"; layout_order | tee -a "$LOG"
check_contains "accepting pinned the tile" "secondary:$OWNER:st1" "$(layout_json | python3 -c "
import json,sys; print(' '.join(o['key'] for o in json.load(sys.stdin)['order']))")"

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
  LAUNCH_LINE=$(python3 "$HERE/nodetext.py" "$OUT/e5_launched.xml" "" | grep -m1 "TILE_ID=")
  say "$LAUNCH_LINE"
  check_contains "the owner was launched with the tile's id" "TILE_ID=st1" "$LAUNCH_LINE"
  check_contains "and with the arguments the request carried" "ARGS=chat=42" "$LAUNCH_LINE"
else
  QA_FAIL=$((QA_FAIL+1)); say "FAIL no secondary tile on Start to tap"
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
  QA_FAIL=$((QA_FAIL+1)); say "FAIL no Cancel button in the band"
fi
adb exec-out screencap -p > "$OUT/e5_declined.png"
say "order after declining:"; layout_order | tee -a "$LOG"
check_absent "declining pinned nothing" "secondary:$OWNER:st2" "$(layout_json | python3 -c "
import json,sys; print(' '.join(o['key'] for o in json.load(sys.stdin)['order']))")"

say "--- 5. a duplicate tileId updates the tile instead of making a second one ---"
ensure_start
request st1 "JenRenamed" "chat=99" medium
ensure_start
sleep 1
say "order after the duplicate request:"; layout_order | tee -a "$LOG"
check "a duplicate tileId updated the tile instead of adding one" 1 "$(layout_json | python3 -c "
import json,sys
print(sum(1 for o in json.load(sys.stdin)['order'] if o['key'].startswith('secondary:')))")"
# "an owner update that changes a tile's arguments is reflected on the next launch" (phase doc edge case)
ensure_start
dump "$OUT/e5_after_update.xml"
T2=$(center "$OUT/e5_after_update.xml" "tile:secondary:$OWNER:st1" || true)
if [ -n "$T2" ]; then
  adb shell input tap ${T2% *} ${T2#* }; sleep 3
  dump "$OUT/e5_relaunched.xml"
  adb exec-out screencap -p > "$OUT/e5_relaunched.png"
  RELAUNCH=$(python3 "$HERE/nodetext.py" "$OUT/e5_relaunched.xml" "" | grep -m1 "TILE_ID=")
  say "$RELAUNCH"
  check_contains "the updated arguments reach the next launch" "ARGS=chat=99" "$RELAUNCH"
fi

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
  check_contains "the secondary tile is inside a folder" "secondary:$OWNER:st1" "$(layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin); print(' '.join(m['key'] for f in d['folders'] for m in f['members']))")"
fi
adb uninstall "$OWNER" 2>&1 | tail -1 | tee -a "$LOG"
sleep 3
ensure_start
adb exec-out screencap -p > "$OUT/e5_after_uninstall.png"
say "order after uninstalling the owner:"; layout_order | tee -a "$LOG"
check_absent "uninstalling the owner took its tile out of the folder too" "secondary:$OWNER" "$(layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(' '.join([o['key'] for o in d['order']] + [m['key'] for f in d['folders'] for m in f['members']] + d['dock']))")"

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[livetile\]|\[layout\]|\[secondary\]" | tail -40 > "$OUT/e5_diag.txt"
layout_restore "$OUT/layout_before.json" || { QA_FAIL=$((QA_FAIL+1)); say "FAIL the layout did not restore"; }
say "baseline layout restored"
qa_finish
