#!/usr/bin/env bash
# L13-7 regression row (INDEX ledger; Jeremy's report 2026-09-26: "I click from home screen then press the back
# button" and Start does not show). The Tess tile plays Start's exit and then opens Tess's session, a window over
# Start that never pauses it, so onResume never played the entrance and Start stayed on the exit's last frame: black,
# no tiles (qa/phase-13/L13-7-tess-tile-repro-ad784939/). Each case taps the Tess tile, leaves Tess one way, and
# requires every tile back plus one entrance. The control launches Settings from its tile: one entrance, not two.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$QA/../phase-02/scripts/layout.sh"

focus() { adb shell dumpsys window | grep -m1 mCurrentFocus | sed 's/.*Window{[0-9a-f]* u0 //; s/}//' | tr -d '\r'; }
tiles() { grep -o 'resource-id="tile:[^"]*"' "$1" | wc -l | tr -d ' '; }
tap_tag() { # xml tag — a missing tag is a failed step, not an abort (the run on 7f00cb9e died on one)
  local b; b="$(bounds "$1" "$2")"
  [ -n "$b" ] || { note "tap_tag: $2 not in $(basename "$1")"; return 1; }
  set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

row_begin L13_7 "leaving Tess opened from her tile brings Start back (system Back, Tess's Back, Tess's Windows key; control)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
layout_restore "$(dirname "$0")/l13_7_layout.json" > "$ROW_DIR/layout-restore.txt" 2>&1
# The restore's Home can land on the app list (the page Start was last on); every case starts on Start's own page.
show_start 3
dump_ui "$ROW_DIR/0-start.xml"
N0="$(tiles "$ROW_DIR/0-start.xml")"
assert_eq "the Tess tile is on Start" "yes" "$(has_node "$ROW_DIR/0-start.xml" tile:shell:cortana)"
TOP0="$(bounds "$ROW_DIR/0-start.xml" tile:shell:cortana | awk '{print $2}')"
note "tiles on Start before: $N0"

one() { # tag how(back|drawn_back|drawn_windows)
  local tag="$1" how="$2" mark
  show_start 3
  dump_ui "$ROW_DIR/$tag-0.xml"
  mark="$(ring_mark)"
  tap_tag "$ROW_DIR/$tag-0.xml" tile:shell:cortana; sleep 3
  assert_eq "$tag: Tess opened over Start" "VoiceInteractionSession" "$(focus)"
  case "$how" in
    back) adb shell input keyevent KEYCODE_BACK ;;
    drawn_back) dump_ui "$ROW_DIR/$tag-tess.xml"; tap_tag "$ROW_DIR/$tag-tess.xml" nav_back ;;
    drawn_windows) dump_ui "$ROW_DIR/$tag-tess.xml"; tap_tag "$ROW_DIR/$tag-tess.xml" nav_windows ;;
  esac
  sleep 3
  dump_ui "$ROW_DIR/$tag-1.xml"; screencap "$ROW_DIR/$tag-1.png"
  ring_since "$mark" > "$ROW_DIR/$tag-slice.txt"
  assert_eq "$tag: Start has focus again" "app.tileshell/app.tileshell.StartActivity" "$(focus)"
  assert_eq "$tag: every tile is back" "$N0" "$(tiles "$ROW_DIR/$tag-1.xml")"
  assert_eq "$tag: Start played its entrance once" "1" "$(grep -cE '\[motion\] start entrance( \(focus back\))?$' "$ROW_DIR/$tag-slice.txt")"
  # ... and not under Tess (review/2026-09-27-L13-7-fix-review-b.md note 1): it starts no earlier than one frame (16 ms)
  # before the session hid. Tess's Windows key starts Home and then hides, so there the two lines can land a couple of
  # ms apart in either order (drv3 on 7f00cb9e: entrance 2 ms first); line order alone is not the claim.
  assert_eq "$tag: the entrance did not start under Tess" "yes" "$(python3 - "$ROW_DIR/$tag-slice.txt" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
wall = lambda l: int(re.search(r'wall=(\d+)', l).group(1))
h = next((wall(l) for l in s if '[cortana] session hidden' in l), None)
e = next((wall(l) for l in s if re.search(r'\[motion\] start entrance( \(focus back\))?$', l)), None)
print("yes" if h is not None and e is not None and e >= h - 16 else f"no (hidden {h}, entrance {e})")
PY
)"
  # Coming back also applies the recent row and the tile sizes, before the entrance (review/2026-09-27-L13-7-fix-review-a.md
  # A1): the first case promotes the Tess tile then; every case finds it in that promoted place.
  TOP="$(bounds "$ROW_DIR/$tag-1.xml" tile:shell:cortana | awk '{print $2}')"
  if [ "$tag" = back ]; then
    assert_eq "$tag: the Tess tile was promoted to the recent row, before the entrance" "yes" "$(python3 - "$ROW_DIR/$tag-slice.txt" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
p = next((i for i, l in enumerate(s) if 'last open app: shell:cortana promoted' in l), None)
e = next((i for i, l in enumerate(s) if re.search(r'\[motion\] start entrance( \(focus back\))?$', l)), None)
print("yes" if p is not None and e is not None and p < e else f"no (promoted line {p}, entrance line {e})")
PY
)"
    PROMOTED_TOP="$TOP"
    assert_ne "$tag: the Tess tile moved (grid top $TOP0)" "$TOP0" "$TOP"
  else
    assert_eq "$tag: the Tess tile is still in the recent row" "$PROMOTED_TOP" "$TOP"
  fi
}
one back back
one drawn-back drawn_back
one drawn-windows drawn_windows

# Control: a tile that launches an activity (Settings) pauses Start; it comes back in onResume, and the focus that
# returns after it must not play a second entrance.
adb shell input keyevent KEYCODE_HOME; sleep 2
dump_ui "$ROW_DIR/ctl-0.xml"
MARK="$(ring_mark)"
tap_tag "$ROW_DIR/ctl-0.xml" tile:shell:settings; sleep 3
assert_contains "control: Settings opened" "Settings" "$(focus)"
adb shell input keyevent KEYCODE_BACK; sleep 3
dump_ui "$ROW_DIR/ctl-1.xml"
ring_since "$MARK" > "$ROW_DIR/ctl-slice.txt"
assert_eq "control: every tile is back" "$N0" "$(tiles "$ROW_DIR/ctl-1.xml")"
assert_eq "control: Start played its entrance once" "1" "$(grep -cE '\[motion\] start entrance( \(focus back\))?$' "$ROW_DIR/ctl-slice.txt")"

MARK="$(ring_mark)"
layout_restore "$QA/../phase-02/baseline_layout.json" > "$ROW_DIR/layout-baseline.txt" 2>&1
assert_absent "restore: phase 02's baseline, no slot re-assigned (C-3)" "-> assigned" "$(ring_since "$MARK" | grep assignSlotOnce)"
show_start 3
row_end
