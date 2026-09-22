#!/usr/bin/env bash
# E11 — the session's drawn bars.
#
#   "While a Cortana session shows, `dumpsys window` shows the system status and nav bars not visible
#    and screencap shows the drawn status bar and the Back / Windows / Search nav bar; the text box's
#    bottom edge sits on the drawn nav bar's top."
#
# The phase doc wrote this with hiding the system bars over a voice-interaction window UNVERIFIED, and
# a fallback if it could not be done. It can: the row records which of the two happened, so H1 reads a
# fact rather than an intention.
. "$(dirname "$0")/lib.sh"

row_begin E11 "the session hides the system bars and draws W10M's"

ensure_start
cortana_assist
sleep 5
dump_ui "$ROW_DIR/e11.xml"
screencap "$ROW_DIR/e11.png"
adb shell dumpsys window > "$ROW_DIR/e11_window.txt" 2>/dev/null

assert_eq "the session is showing" "yes" "$(has_node "$ROW_DIR/e11.xml" cortana_session)"

# The shell records what actually happened when it asked for the bars to go.
assert_contains "the session reports the system bars hidden" "system bars hidden=true" "$(diag cortana)"

# Phase 01 E19's form: the system bars' own windows are not visible.
python3 "$HERE/barvis.py" "$ROW_DIR/e11_window.txt" > "$ROW_DIR/e11_barvis.txt" 2>&1
bar_rc=$?
cat "$ROW_DIR/e11_barvis.txt" >> "$LOG"
if [ $bar_rc -eq 0 ]; then
  _verdict PASS "dumpsys window: the system bars are not visible" "$(head -1 "$ROW_DIR/e11_barvis.txt")"
else
  _verdict FAIL "dumpsys window: the system bars are not visible" "$(head -2 "$ROW_DIR/e11_barvis.txt" | tr '\n' ' ')"
fi

# The drawn bars are the shell's own, with all three nav slots filled.
assert_eq "the drawn W10M status bar is on screen" "yes" "$(has_node "$ROW_DIR/e11.xml" w10m_status_bar)"
assert_eq "the drawn W10M nav bar is on screen" "yes" "$(has_node "$ROW_DIR/e11.xml" w10m_nav_bar)"
assert_eq "Back" "yes" "$(has_node "$ROW_DIR/e11.xml" nav_back)"
assert_eq "Windows" "yes" "$(has_node "$ROW_DIR/e11.xml" nav_windows)"
# Phase 01 left the right slot EMPTY; phase 03 ADDs Search into it (Rule 16, no placeholder).
assert_eq "Search, which phase 03 ADDed into phase 01's empty slot" "yes" "$(has_node "$ROW_DIR/e11.xml" nav_search)"
assert_eq "and the empty slot is gone" "no" "$(has_node "$ROW_DIR/e11.xml" nav_search_slot)"

px_per_epx="$(python3 -c "print(1080/360)")"
box="$(bounds "$ROW_DIR/e11.xml" cortana_text_box)"
nav="$(bounds "$ROW_DIR/e11.xml" w10m_nav_bar)"
status="$(bounds "$ROW_DIR/e11.xml" w10m_status_bar)"
if [ -n "$box" ] && [ -n "$nav" ] && [ -n "$status" ]; then
  set -- $box; box_bottom=$4
  set -- $nav; nav_top=$2 nav_bottom=$4
  set -- $status; status_top=$2 status_bottom=$4
  assert_within "the text box's bottom edge sits on the drawn nav bar's top" 0 \
    "$(python3 -c "print(($box_bottom - $nav_top) / $px_per_epx)")" 1.0
  assert_within "the drawn nav bar is 48 epx (phase 01 X6)" 48 \
    "$(python3 -c "print(($nav_bottom - $nav_top) / $px_per_epx)")" 1.0
  assert_within "the drawn status bar is 28 epx (R3 C4)" 28 \
    "$(python3 -c "print(($status_bottom - $status_top) / $px_per_epx)")" 1.0
  assert_within "and it starts at the screen top" 0 \
    "$(python3 -c "print($status_top / $px_per_epx)")" 1.0
  assert_within "the drawn nav bar reaches the screen bottom" 2340 "$nav_bottom" 2
else
  _verdict FAIL "the drawn bars and the text box are all on screen" "box=[$box] nav=[$nav] status=[$status]"
fi

cortana_close
row_end
