#!/usr/bin/env bash
# Layout helpers: read, save and restore the shell's layout store (RV12 — every row restores what it changes).
export PATH=$HOME/Android/Sdk/platform-tools:$PATH
layout_json() { adb shell run-as app.tileshell cat files/start_layout.json; }
layout_order() { layout_json | python3 -c "import json,sys; d=json.load(sys.stdin); print('order:', [o['key']+'/'+o['size'] for o in d['order']]); print('dock:', d['dock']); print('folders:', d.get('folders'))"; }
layout_save() { layout_json > "$1"; }

# layout_restore <file>: write the layout and PROVE the shell came up on it.
#
# The shell is the home app, and Android restarts the home app the instant it is force-stopped — so a shell
# can come back with the OLD layout in memory before the new file lands, and then save the old one over it.
# The first version of this helper wrote under that race, read the file straight back (which always matched)
# and returned; rows then ran on whatever Start happened to be showing (found 2026-09-22 by J3: E9 part 7
# could not find the row tile it was told to drag). Now: stop, write, stop AGAIN, bring Home back, wait for
# Start, and compare what is on disk AFTER the shell has loaded and saved. A file without the shell's
# one-time markers (addedOnce) makes the shell re-add Tess, a category folder or the Music slot on load, so
# the comparison also catches a baseline that has gone stale.
layout_restore() {
  local want=$1 i
  for i in 1 2 3; do
    adb shell am force-stop app.tileshell
    adb push "$want" /data/local/tmp/restore_layout.json >/dev/null
    adb shell 'run-as app.tileshell sh -c "cat /data/local/tmp/restore_layout.json > files/start_layout.json"'
    adb shell am force-stop app.tileshell
    sleep 1
    adb shell input keyevent KEYCODE_HOME
    sleep 4
    if layout_json | python3 -c '
import json, sys
want = json.load(open(sys.argv[1]))
got = json.load(sys.stdin)
# Sizes are compared only for hand-set tiles: the use-based auto-sizer may legitimately resize any other tile
# as the shell loads (a real device layout; the QA baselines mark every size hand-set, so they stay exact).
manual = set(want.get("manualSizes", []))
keys = lambda d, k: [(o["key"], o["size"] if o["key"] in manual else "*") for o in d.get(k, [])]
same = (keys(want, "order") == keys(got, "order") and want.get("dock", []) == got.get("dock", [])
        and want.get("folders", []) == got.get("folders", []) and want.get("slots", {}) == got.get("slots", {}))
sys.exit(0 if same else 1)' "$want"
    then
      return 0
    fi
    echo "layout_restore: the shell did not come up on $want (attempt $i)" >&2
  done
  echo "layout_restore: FAILED to restore $want" >&2
  return 1
}
