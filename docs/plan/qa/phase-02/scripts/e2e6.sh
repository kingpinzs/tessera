#!/usr/bin/env bash
# E2 + E6 re-run on the FINAL build (build task 3 and 5 proved them on their own build; this re-runs the parts
# that the lead's LayoutStore fix touched), including E2c — the defect that fix was for: pinning an app that is
# already on Start must not make a second tile, not even after the shell restarts.
# usage: e2e6.sh <out dir>
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/gestures.sh"
source "$HERE/layout.sh"
source "$HERE/assert.sh"
OUT=$1; LOG=$OUT/E02-E06.txt
mkdir -p "$OUT"; : > "$LOG"
APK=$HERE/../../../../testapps/tileclient-a/build/outputs/apk/debug/tileclient-a-debug.apk
PKG=app.tileshell.testclient.a
tiles_for() { layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
keys=[o['key'] for o in d['order']] + [m['key'] for f in d['folders'] for m in f['members']] + d['dock']
print(sum(1 for k in keys if '$1' in k))"; }

say "# E2 + E6 re-run $(date -Iseconds)"
build_guard
layout_save "$OUT/layout_before.json"
layout_restore "$HERE/../baseline_layout.json"
adb install -r -g "$APK" 2>&1 | tail -1 | tee -a "$LOG"
sleep 2

pin_from_app_list() {
  ensure_start
  adb shell input swipe 900 1200 150 1200 250; sleep 2      # pivot to the app list
  dump "$OUT/e2_applist.xml"
  local row
  row=$(center "$OUT/e2_applist.xml" "applist_row:$PKG" || true)
  if [ -z "$row" ]; then
    scroll_to_id "$OUT/e2_applist.xml" "applist_row:$PKG" || { say "FAIL the fixture is not in the app list"; return 1; }
    row=$(center "$OUT/e2_applist.xml" "applist_row:$PKG")
  fi
  say "holding the app list row at $row"
  down ${row% *} ${row#* }; sleep 1.1; up ${row% *} ${row#* }; sleep 1.2
  dump "$OUT/e2_menu.xml"
  adb exec-out screencap -p > "$OUT/e2_menu.png"
  local item
  item=$(center "$OUT/e2_menu.xml" "applist_menu_pin" 2>/dev/null || true)
  if [ -z "$item" ]; then
    item=$(python3 - "$OUT/e2_menu.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
for m in re.finditer(r'<node[^>]*>', s):
    n = m.group(0)
    t = re.search(r'text="([^"]*)"', n); b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if t and b and t.group(1).strip().lower().startswith("pin to start"):
        x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  fi
  [ -n "$item" ] || { say "FAIL no Pin to Start item in the menu"; return 1; }
  say "tapping Pin to Start at $item"
  adb shell input tap ${item% *} ${item#* }; sleep 2
}

say "--- E2a: a freshly installed app shows the New caption ---"
ensure_start
adb shell input swipe 900 1200 150 1200 250; sleep 2
dump "$OUT/e2_applist_new.xml"
if ! grep -q "applist_row:$PKG" "$OUT/e2_applist_new.xml"; then
  scroll_to_id "$OUT/e2_applist_new.xml" "applist_row:$PKG" || true
fi
NEW_BEFORE=$(python3 - "$OUT/e2_applist_new.xml" "$PKG" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
m = re.search(r'resource-id="applist_row:' + re.escape(sys.argv[2]) + r'"(.*?)</node>', s, re.S)
block = m.group(1) if m else ""
print("yes" if re.search(r'text="[Nn]ew"', block) else "no")
PY
)
say "New caption on the fixture's row before pinning: $NEW_BEFORE"
check "a freshly installed app is marked New" "yes" "$NEW_BEFORE"
adb exec-out screencap -p > "$OUT/e2_new_caption.png"

say "--- E2: pin the fixture from the app list ---"
pin_from_app_list || true
ensure_start
adb exec-out screencap -p > "$OUT/e2_pinned.png"
check "pinning added exactly one tile" 1 "$(tiles_for $PKG)"
say "order:"; layout_order | head -1 | tee -a "$LOG"
# and the caption is gone, persistently
adb shell input swipe 900 1200 150 1200 250; sleep 2
dump "$OUT/e2_applist_after_pin.xml"
if ! grep -q "applist_row:$PKG" "$OUT/e2_applist_after_pin.xml"; then
  scroll_to_id "$OUT/e2_applist_after_pin.xml" "applist_row:$PKG" || true
fi
NEW_AFTER=$(python3 - "$OUT/e2_applist_after_pin.xml" "$PKG" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
m = re.search(r'resource-id="applist_row:' + re.escape(sys.argv[2]) + r'"(.*?)</node>', s, re.S)
block = m.group(1) if m else ""
print("yes" if re.search(r'text="[Nn]ew"', block) else "no")
PY
)
check "pinning cleared the New caption" "no" "$NEW_AFTER"
adb exec-out screencap -p > "$OUT/e2_caption_cleared.png"
ensure_start

say "--- E2c: pin the same app again, then restart the shell (the defect the store fix was for) ---"
pin_from_app_list || true
check "the second pin added nothing (live)" 1 "$(tiles_for $PKG)"
adb shell am force-stop app.tileshell; sleep 2; ensure_start
check "and still nothing after a restart (E2c)" 1 "$(tiles_for $PKG)"
adb exec-out screencap -p > "$OUT/e2c_after_restart.png"

say "--- E6: an update keeps the tile, an uninstall drops it ---"
adb install -r -g "$APK" 2>&1 | tail -1 | tee -a "$LOG"
sleep 3; ensure_start
check "an update keeps the tile" 1 "$(tiles_for $PKG)"
adb exec-out screencap -p > "$OUT/e6_after_update.png"
adb uninstall "$PKG" 2>&1 | tail -1 | tee -a "$LOG"
sleep 3; ensure_start
check "an uninstall drops it" 0 "$(tiles_for $PKG)"
adb exec-out screencap -p > "$OUT/e6_after_uninstall.png"

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[layout\]|\[apps\]" | tail -30 > "$OUT/e2e6_diag.txt"
layout_restore "$OUT/layout_before.json" || { QA_FAIL=$((QA_FAIL+1)); say "FAIL the layout did not restore"; }
say "baseline layout restored"
qa_finish
