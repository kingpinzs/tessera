#!/usr/bin/env bash
# J1 — Tess's text box clears itself when a request is sent (Jeremy, 2026-09-22, on the phone: "When the
# message gets sent it does not clear the input and clicking the x does not clear it and I have to hit the
# back button", then "It should auto clear when it gets auto sent").
#
# R6 §3.3.7 (a) measured the bar keeping the query with a ✕ after a send; Jeremy overrode it, so a sent
# request, typed OR spoken, must leave the empty box with its placeholder at once, the answer card on the
# page, the keyboard put away, and no query or ✕ left in the bar. Runs 1-2 (J1-run1/-run2.txt) proved the
# earlier ✕ form and are kept.
. "$(dirname "$0")/lib.sh"

row_begin J1 "Tess's text box clears itself on send (typed and spoken)"
D="$ROW_DIR/j1.xml"
ime_shown() { adb shell dumpsys input_method | grep -m1 mInputShown | tr -d '\r ' | sed 's/mInputShown=//'; }
cleared() { # label
  dump_ui "$D"
  assert_eq "$1: no query left in the bar" "no" "$(has_node "$D" cortana_text_box_query)"
  assert_eq "$1: no ✕ left in the bar" "no" "$(has_node "$D" cortana_text_box_clear)"
  assert_eq "$1: the empty text box is back" "yes" "$(has_node "$D" cortana_text_box_field)"
  assert_eq "$1: its placeholder shows" "yes" "$(has_node "$D" cortana_text_box_placeholder)"
  assert_contains "$1: the answer card is on the page" "cortana_card:" "$(grep -o 'resource-id="cortana_card:[a-z_]*"' "$D" | head -1)"
  assert_eq "$1: Tess is still open" "yes" "$(has_node "$D" cortana_session)"
}

ensure_start
cortana_assist; sleep 4

# ---- typed ---------------------------------------------------------------------------------------------
dump_ui "$D"
tap_node "$D" cortana_text_box_field; sleep 1
note "keyboard up before the send: $(ime_shown)"
adb shell input text "what%stime%sis%sit"; sleep 0.5
adb shell input keyevent KEYCODE_ENTER; sleep 3
screencap "$ROW_DIR/j1_typed.png"
cleared "typed"
assert_eq "typed: the keyboard is put away (it would cover the card)" "false" "$(ime_shown)"
# Jeremy, 2026-09-23: what was asked shows as one grey line above the answer.
assert_eq "typed: the asked line shows what was typed" "what time is it" "$(node_text "$D" cortana_asked)"

# ---- spoken --------------------------------------------------------------------------------------------
# speak.sh starts the utterance the moment listening does (the recogniser endpoints on silence; run 3
# tapped the mic, waited, and was heard as "AND").
heard="$("$(dirname "$0")/speak.sh" calendar_query 9)"
note "speak.sh: $heard"
screencap "$ROW_DIR/j1_spoken.png"
cleared "spoken"
assert_eq "spoken: the listening box is gone" "no" "$(has_node "$D" cortana_listening_box)"
# The dump writes the apostrophe as &apos;, so the text is unescaped before comparing.
assert_eq "spoken: the asked line shows what was heard, in sentence case" "What's on my calendar" \
  "$(node_text "$D" cortana_asked | python3 -c 'import html, sys; print(html.unescape(sys.stdin.read().strip()))')"
assert_contains "spoken: the request was heard" "calendar" "$(diag speech | grep 'final open=' | tail -1 | tr A-Z a-z)"

cortana_close
row_end
