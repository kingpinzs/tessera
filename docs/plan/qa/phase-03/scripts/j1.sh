#!/usr/bin/env bash
# J1 — Tess's text box clears (Jeremy, 2026-09-22, on the phone: "When the message gets sent it does not
# clear the input and clicking the x does not clear it and I have to hit the back button").
#
# R6 §3.3.7: after a typed request is sent the grey bar SHOWS the query with a ✕ at its right; the ✕ is
# how it is cleared. So the row proves both halves: the sent query is shown (W10M), and one tap on the ✕
# returns the bar to the empty text box with its placeholder and takes the answer off the page — the same
# end state Back reaches. Bracketed by Back, which is proven to clear it too.
. "$(dirname "$0")/lib.sh"

row_begin J1 "Tess's text box: the ✕ clears a sent request"
D="$ROW_DIR/j1.xml"

send_typed() { # text
  dump_ui "$D"
  tap_node "$D" cortana_text_box_field; sleep 1
  adb shell input text "$1"; sleep 0.5
  adb shell input keyevent KEYCODE_ENTER; sleep 3
}

ensure_start
cortana_assist; sleep 4
send_typed "what%stime%sis%sit"
dump_ui "$D"; screencap "$ROW_DIR/j1_sent.png"
assert_eq "sent: the bar shows the query (R6 §3.3.7)" "yes" "$(has_node "$D" cortana_text_box_query)"
assert_eq "sent: the ✕ is on the bar" "yes" "$(has_node "$D" cortana_text_box_clear)"

tap_node "$D" cortana_text_box_clear; sleep 1.5
dump_ui "$D"; screencap "$ROW_DIR/j1_cleared.png"
assert_eq "✕: the query is gone from the bar" "no" "$(has_node "$D" cortana_text_box_query)"
assert_eq "✕: the empty text box is back" "yes" "$(has_node "$D" cortana_text_box_field)"
assert_eq "✕: its placeholder shows" "yes" "$(has_node "$D" cortana_text_box_placeholder)"
assert_eq "✕: Tess is still open (the ✕ clears, it does not close)" "yes" "$(has_node "$D" cortana_session)"

# Bracket: a second request, cleared by Back instead, reaches the same state.
send_typed "what%stime%sis%sit"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
dump_ui "$D"
assert_eq "Back: the query is gone from the bar" "no" "$(has_node "$D" cortana_text_box_query)"
assert_eq "Back: the empty text box is back" "yes" "$(has_node "$D" cortana_text_box_field)"
cortana_close
row_end
