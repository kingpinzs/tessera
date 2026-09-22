#!/usr/bin/env bash
# E11 — "Switch back to letters after I type an emoticon".
#
#   "With "Switch back to letters after I type an emoticon" on, inserting one emoji from the panel
#    returns to the letter keys (dump); with it off, the emoji panel stays"
#
# Both states through the real Keyboard page; the setting is left On (its default, H17) at the end.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E11 "switch back to letters after an emoji: on returns, off stays"
kb_begin

set_switch() { # true|false
  adb shell am start -W -f 0x10008000 -n app.tileshell/.settings.SettingsActivity --es page KEYBOARD >/dev/null
  sleep 1.5
  local f="$ROW_DIR/.kp.xml"
  scroll_to_node "$f" keyboard_switch_back 4 >/dev/null 2>&1
  local now
  now="$(grep -o 'resource-id="keyboard_switch_back"[^>]*' "$f" | grep -o 'checked="[a-z]*"' | cut -d'"' -f2)"
  if [ "$now" != "$1" ]; then tap_node "$f" keyboard_switch_back; sleep 0.8; dump_ui "$f"; fi
  assert_eq "Settings: switch back is $1" "$1" "$(grep -o 'resource-id="keyboard_switch_back"[^>]*' "$f" | grep -o 'checked="[a-z]*"' | cut -d'"' -f2)"
}

insert_one() { # tag
  open_field field_text
  local D="$ROW_DIR/e11_$1.xml"
  kb_dump "$D"; tap_key "$D" emoji; sleep 1
  kb_dump "$D"; tap_node "$D" kb_emoji_cat_food; sleep 1
  kb_dump "$D"; tap_node "$D" kb_emoji_cell_0; sleep 1
  kb_dump "$D"
  screencap "$ROW_DIR/e11_$1.png"
}

set_switch true
insert_one on
D="$ROW_DIR/e11_on.xml"
assert_eq "on: the emoji went into the field" "yes" "$([ "$(read_mirror len)" -ge 1 ] && echo yes || echo no)"
assert_eq "on: back on the letter keys" "yes" "$(has_node "$D" kb_key_q)"
assert_eq "on: the emoji panel is gone" "no" "$(has_node "$D" kb_emoji_panel)"
note "edge: the emoji came from the food page, not recent — the switch-back applies from any category"

set_switch false
insert_one off
D="$ROW_DIR/e11_off.xml"
assert_eq "off: the emoji went into the field" "yes" "$([ "$(read_mirror len)" -ge 1 ] && echo yes || echo no)"
assert_eq "off: the emoji panel stays" "yes" "$(has_node "$D" kb_emoji_panel)"
assert_eq "off: no letter keys" "no" "$(has_node "$D" kb_key_q)"

set_switch true   # restore the default (H17)
kb_end
row_end
