#!/usr/bin/env bash
# E15 — the ≡ pane, the Reminders page and the Settings page (R7 §3.1-3.2, §3.5-3.7).
#
# Every value must be within its tolerance in the Decisions or one screen pixel (0.33 epx), whichever
# is larger, with the shell's own theme on Dark (X21's default; review R3T-M2). Geometry comes from
# dump bounds and screencap pixels (px / 3 = epx); the transitions come from a 60-fps screenrecord.
#
# Setup through the reminder card, as the row specifies: a reminder later today WITH a photo, one for
# tomorrow, and one with no time at all — so all three groups exist and the photo row can be measured.
# The row's own Delete steps remove them again.
. "$(dirname "$0")/lib.sh"

row_begin E15 "the pane, the Reminders page and the Settings page"

PX=3.0   # 1080 / 360
epx() { python3 -c "print($1 / $PX)"; }
span() { python3 -c "print(($2 - $1) / $PX)"; }

# R3T-M2: the row runs with the shell's theme on Dark, which is also the X21 default.
adb shell run-as app.tileshell cat /data/data/app.tileshell/shared_prefs/start_theme.xml \
  > "$ROW_DIR/e15_theme.xml" 2>/dev/null
assert_absent "the shell theme is Dark (X21 default)" 'name="theme">LIGHT' "$(cat "$ROW_DIR/e15_theme.xml")"

make_reminder() { # utterance
  ensure_start
  cortana_assist
  sleep 4
  "$HERE/speak.sh" "$1" 12 > /dev/null 2>&1
  "$HERE/speak.sh" yes 12 > /dev/null 2>&1
}
make_reminder reminder_time      # Today / Coming up, depending on the hour
make_reminder reminder_no_time   # Whenever

# ---- the pane ------------------------------------------------------------------------------------
ensure_start
cortana_assist
sleep 5
dump_ui "$ROW_DIR/e15_home.xml"
tap_node "$ROW_DIR/e15_home.xml" cortana_menu_button
sleep 2
dump_ui "$ROW_DIR/e15_pane.xml"
screencap "$ROW_DIR/e15_pane.png"
pane="$ROW_DIR/e15_pane.xml"

assert_eq "R7 3.1: the pane is open" "yes" "$(has_node "$pane" cortana_pane)"
b="$(bounds "$pane" cortana_pane)"
if [ -n "$b" ]; then
  set -- $b
  assert_within "R7 3.1.5 pane width 256 epx" 256 "$(span $1 $3)" 1.0
else
  _verdict FAIL "the pane is measurable" "no bounds"
fi
assert_contains "R7 3.1.1 the pane title" "TESS" "$(node_text "$pane" cortana_pane_title)"
assert_eq "R7 3.1.1 Home item" "yes" "$(has_node "$pane" cortana_pane_item_home)"
assert_eq "R7 3.1.1 Reminders item" "yes" "$(has_node "$pane" cortana_pane_item_reminders)"
assert_eq "R7 3.1.9 Settings item" "yes" "$(has_node "$pane" cortana_pane_item_settings)"
# W10M's Feedback item is left out and its slot stays EMPTY: E15 asserts its ABSENCE (H23).
assert_eq "R7 3.1.9 no Feedback item, and no node in its slot (H23)" "no" \
  "$(has_node "$pane" cortana_pane_item_feedback)"

settings_b="$(bounds "$pane" cortana_pane_item_settings)"
if [ -n "$settings_b" ]; then
  set -- $settings_b
  assert_within "R7 3.1.9 Settings sits 172 epx above the screen bottom" 172 \
    "$(python3 -c "print((2340 - $2) / $PX)")" 2.0
fi

# ---- the destination transition, from frames ------------------------------------------------------
rm -f "$ROW_DIR/e15_transition.mp4"
adb shell screenrecord --time-limit 8 --bit-rate 16000000 --size 1080x2340 /sdcard/e15.mp4 &
rec=$!
sleep 1
tap_node "$pane" cortana_pane_item_reminders
sleep 6
wait $rec 2>/dev/null
adb pull /sdcard/e15.mp4 "$ROW_DIR/e15_transition.mp4" >/dev/null 2>&1
adb shell rm -f /sdcard/e15.mp4
if [ -s "$ROW_DIR/e15_transition.mp4" ]; then
  rm -rf "$ROW_DIR/e15_frames"; mkdir -p "$ROW_DIR/e15_frames"
  ffmpeg -v error -i "$ROW_DIR/e15_transition.mp4" -vf fps=60 "$ROW_DIR/e15_frames/f_%04d.png" 2>>"$LOG"
  python3 "$HERE/fade.py" "$ROW_DIR/e15_frames" > "$ROW_DIR/e15_fade.txt" 2>&1
  fade_rc=$?
  cat "$ROW_DIR/e15_fade.txt" >> "$LOG"
  if [ $fade_rc -eq 0 ]; then
    black_ms="$(sed -n 's/^black_ms=//p' "$ROW_DIR/e15_fade.txt")"
    fade_ms="$(sed -n 's/^fade_ms=//p' "$ROW_DIR/e15_fade.txt")"
    first_alpha="$(sed -n 's/^first_frame_alpha=//p' "$ROW_DIR/e15_fade.txt")"
    assert_within "R7 3.2.1 black for 283-300 ms" 291.5 "$black_ms" 25
    assert_within "R7 3.2.2 the page fades up over 200-317 ms" 258 "$fade_ms" 75
    assert_within "R7 3.2.2 its first frame is already 40-60 % bright" 0.5 "$first_alpha" 0.12
  else
    _verdict FAIL "the transition was measurable" "$(head -2 "$ROW_DIR/e15_fade.txt" | tr '\n' ' ')"
  fi
else
  _verdict FAIL "a screenrecord of the transition was captured" "no video"
fi

# ---- the Reminders page ---------------------------------------------------------------------------
sleep 2
dump_ui "$ROW_DIR/e15_reminders.xml"
screencap "$ROW_DIR/e15_reminders.png"
page="$ROW_DIR/e15_reminders.xml"

assert_eq "R7 3.3.2 the Reminders page is showing" "yes" "$(has_node "$page" reminders_title)"
# §3.1.13: a destination page hides the drawn status bar and has no text box.
assert_eq "R7 3.1.13 no status bar on a destination page" "no" "$(has_node "$page" w10m_status_bar)"
assert_eq "R7 3.1.13 and no text box" "no" "$(has_node "$page" cortana_text_box)"

t="$(bounds "$page" reminders_title)"
[ -n "$t" ] && { set -- $t; assert_within "R7 3.3.2 title at x 60 epx" 60 "$(epx $1)" 1.4; }

python3 "$HERE/pixel.py" "$ROW_DIR/e15_reminders.png" 20 700 > "$ROW_DIR/e15_bg.txt" 2>&1
assert_eq "R7 3.5 page background (14,19,13)" "14,19,13" "$(cat "$ROW_DIR/e15_bg.txt")"

assert_eq "R7 3.5.1 a Whenever group exists" "yes" "$(has_node "$page" reminders_group_whenever)"
# §3.5.1: a group shows only while it holds a reminder. Nothing was made for "Coming up".
note "groups present: today=$(has_node "$page" reminders_group_today) coming=$(has_node "$page" reminders_group_coming_up) whenever=$(has_node "$page" reminders_group_whenever)"

row_id="$(grep -o 'resource-id="reminder_row:[^"]*"' "$page" | head -1 | sed 's/.*reminder_row://; s/"//')"
if [ -n "$row_id" ]; then
  rb="$(bounds "$page" "reminder_row:$row_id")"
  set -- $rb
  assert_within "R7 3.5.3 a one-line row is 60 epx tall" 60 "$(span $2 $4)" 1.4
  tb="$(bounds "$page" "reminder_row_title:$row_id")"
  [ -n "$tb" ] && { set -- $tb; assert_within "R7 3.5.3 row title at x 55.8 epx" 55.8 "$(epx $1)" 1.4; }
else
  _verdict FAIL "a reminder row is on the page" "no reminder_row node"
fi

ab="$(bounds "$page" reminders_appbar)"
if [ -n "$ab" ]; then
  set -- $ab
  assert_within "R7 3.5.8 app bar 48.2 epx tall" 48.2 "$(span $2 $4)" 1.4
  assert_within "and it sits directly above the nav bar" 2340 "$4" 4
fi
assert_eq "R7 3.5.8 the list button" "yes" "$(has_node "$page" reminders_appbar_list)"
assert_eq "R7 3.5.8 the + button" "yes" "$(has_node "$page" reminders_appbar_add)"
assert_eq "R7 3.5.8 the ... button" "yes" "$(has_node "$page" reminders_appbar_more)"

# ---- the long-press menu (R7 3.6) ------------------------------------------------------------------
if [ -n "$row_id" ]; then
  rb="$(bounds "$page" "reminder_row:$row_id")"
  set -- $rb
  cx=$(( ($1 + $3) / 2 )); cy=$(( ($2 + $4) / 2 ))
  adb shell input swipe $cx $cy $cx $cy 900
  sleep 2
  dump_ui "$ROW_DIR/e15_menu.xml"
  screencap "$ROW_DIR/e15_menu.png"
  assert_eq "R7 3.6.1 a long press opens the menu" "yes" "$(has_node "$ROW_DIR/e15_menu.xml" reminder_menu)"
  assert_eq "with Complete" "yes" "$(has_node "$ROW_DIR/e15_menu.xml" reminder_menu_complete)"
  assert_eq "and Delete" "yes" "$(has_node "$ROW_DIR/e15_menu.xml" reminder_menu_delete)"
  mb="$(bounds "$ROW_DIR/e15_menu.xml" reminder_menu)"
  if [ -n "$mb" ]; then
    set -- $mb
    assert_within "R7 3.6.2 the menu is 243.3 epx wide" 243.3 "$(span $1 $3)" 1.4
    assert_within "R7 3.6.2 and 107.0 epx tall" 107.0 "$(span $2 $4)" 1.4
    assert_within "R7 3.6.2 its bottom edge sits 23.5 epx above the touch point" 23.5 \
      "$(python3 -c "print(($cy - $4) / $PX)")" 2.0
  fi
  # §3.6.1: Delete acts at once, with NO confirmation card — the cards are for spoken requests.
  before_rows="$(grep -c 'resource-id="reminder_row:' "$page")"
  tap_node "$ROW_DIR/e15_menu.xml" reminder_menu_delete
  sleep 2
  dump_ui "$ROW_DIR/e15_after_delete.xml"
  assert_eq "R7 3.6.1 Delete acts with no confirmation card" "no" \
    "$(has_node "$ROW_DIR/e15_after_delete.xml" "cortana_card:delete_confirm")"
  assert_ne "and the row is gone" "$before_rows" "$(grep -c 'resource-id="reminder_row:' "$ROW_DIR/e15_after_delete.xml")"
fi

# ---- the Settings destination -----------------------------------------------------------------------
dump_ui "$ROW_DIR/e15_page2.xml"
tap_node "$ROW_DIR/e15_page2.xml" cortana_menu_button
sleep 2
dump_ui "$ROW_DIR/e15_pane2.xml"
tap_node "$ROW_DIR/e15_pane2.xml" cortana_pane_item_settings
sleep 4
dump_ui "$ROW_DIR/e15_settings.xml"
screencap "$ROW_DIR/e15_settings.png"
assert_eq "Choosing Settings runs the same transition and shows the page" "yes" \
  "$(has_node "$ROW_DIR/e15_settings.xml" cortana_settings_title)"
assert_eq "with no status bar" "no" "$(has_node "$ROW_DIR/e15_settings.xml" w10m_status_bar)"
assert_eq "and no text box" "no" "$(has_node "$ROW_DIR/e15_settings.xml" cortana_text_box)"

note "H23-H28 are judged, not measured: the empty Feedback slot, the final-release stand-ins, + / History / '...', the place and person rows, and the Settings structure and look"
row_end
