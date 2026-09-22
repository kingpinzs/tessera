#!/usr/bin/env bash
# E5 — a typed request, the text box's geometry, and the exported-components allow-list.
#
# The row's real claim is that there is exactly ONE way to put a request into Cortana: the text box the
# user can see. A test-only injection path would make every other row meaningless, so the components
# the APK exports are compared against the allow-list in the phase doc's Decisions — exactly.
. "$(dirname "$0")/lib.sh"

row_begin E5 "a typed request, the text box, and the exported components"

ensure_start
cortana_assist
sleep 5
dump_ui "$ROW_DIR/e5_home.xml"
screencap "$ROW_DIR/e5_home.png"

# ---- the text box (R6 §3.3) -------------------------------------------------------------------
px_per_epx="$(python3 -c "print(1080/360)")"
box="$(bounds "$ROW_DIR/e5_home.xml" cortana_text_box)"
nav="$(bounds "$ROW_DIR/e5_home.xml" w10m_nav_bar)"
if [ -n "$box" ] && [ -n "$nav" ]; then
  set -- $box
  box_top=$2 box_bottom=$4 box_left=$1 box_right=$3
  set -- $nav
  nav_top=$2
  assert_within "R6 3.3.1 text box height (48 epx)" 48 \
    "$(python3 -c "print(($box_bottom - $box_top) / $px_per_epx)")" 1.1
  assert_within "R6 3.3.1 text box full width (360 epx)" 360 \
    "$(python3 -c "print(($box_right - $box_left) / $px_per_epx)")" 1.0
  # §3.3.1-3.3.2: docked directly on the drawn nav bar.
  assert_within "R6 3.3.2 docked on the drawn nav bar" 0 \
    "$(python3 -c "print(abs($box_bottom - $nav_top) / $px_per_epx)")" 1.0
else
  _verdict FAIL "the text box and the nav bar are on screen" "box=[$box] nav=[$nav]"
fi

mic="$(bounds "$ROW_DIR/e5_home.xml" cortana_text_box_mic)"
if [ -n "$mic" ]; then
  set -- $mic
  assert_within "R6 3.3.4 mic button 48 x 48 epx" 48 "$(python3 -c "print(($3 - $1) / $px_per_epx)")" 1.1
  assert_within "R6 3.3.4 mic button flush right" 360 "$(python3 -c "print($3 / $px_per_epx)")" 1.0
else
  _verdict FAIL "the mic button is on screen" "no cortana_text_box_mic"
fi

assert_eq "R6 3.3.5 placeholder (H13)" "Ask me anything" \
  "$(node_text "$ROW_DIR/e5_home.xml" cortana_text_box_placeholder)"

# §3.1.14: the page background is black (15063), not 14393's dark grey.
python3 "$HERE/pixel.py" "$ROW_DIR/e5_home.png" 540 700 > "$ROW_DIR/e5_bg.txt"
assert_eq "R6 3.1.14 page background is black" "0,0,0" "$(cat "$ROW_DIR/e5_bg.txt")"

# ---- the typed request runs the same path as speech (fidelity A4) ------------------------------
before="$(diag match | wc -l)"
type_request "what time is it" 10
dump_ui "$ROW_DIR/e5_typed.xml"
screencap "$ROW_DIR/e5_typed.png"
after="$(diag match | wc -l)"
assert_ne "the typed request reached the matcher" "$before" "$after"
assert_contains "the matcher saw the typed text" "TimeQuery" "$(diag match | tail -1)"
assert_contains "and Cortana spoke a reply" "It's" "$(reply_text)"
assert_eq "a response card is on screen" "yes" "$(has_node "$ROW_DIR/e5_typed.xml" cortana_card_title)"

# ---- the exported components allow-list --------------------------------------------------------
adb shell dumpsys package app.tileshell > "$ROW_DIR/e5_package.txt" 2>/dev/null
python3 "$HERE/exported.py" "$ROW_DIR/e5_package.txt" "$HERE/../exported-allowlist.txt" > "$ROW_DIR/e5_exported.txt" 2>&1
exported_rc=$?
cat "$ROW_DIR/e5_exported.txt" >> "$LOG"
if [ $exported_rc -eq 0 ]; then
  _verdict PASS "exported components match the allow-list exactly" "$(head -1 "$ROW_DIR/e5_exported.txt")"
else
  _verdict FAIL "exported components match the allow-list exactly" "$(head -3 "$ROW_DIR/e5_exported.txt" | tr '\n' ' ')"
fi

cortana_close
row_end
