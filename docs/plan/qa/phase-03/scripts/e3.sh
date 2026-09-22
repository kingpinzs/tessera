#!/usr/bin/env bash
# E3 — an utterance outside the list reaches the not-understood handler with the transcript logged.
#
# The row is the phase 08 seam: the handler is a permanent part, not a stand-in (Rule 16), and the
# OPEN-pass transcript is what it is handed. So it checks the transcript that reaches diagnostics is
# the open pass's, not the grammar pass's, and that Cortana says so rather than failing silently.
. "$(dirname "$0")/lib.sh"

row_begin E3 "an unmatched utterance reaches the not-understood handler"

ensure_start
cortana_assist
sleep 4
final="$("$HERE/speak.sh" unmatched 11)"
note "final: $final"
dump_ui "$ROW_DIR/e3.xml"
screencap "$ROW_DIR/e3.png"

open_text="$(printf '%s' "$final" | sed 's/.*open="\([^"]*\)".*/\1/')"
note "open pass: [$open_text]"
assert_ne "the recogniser produced an open-vocabulary transcript" "" "$open_text"

handler="$(diag not_understood | tail -1)"
note "handler line: $handler"
assert_contains "the not-understood handler ran" "transcript=" "$handler"
assert_contains "with no phase 08 handler registered yet" "handler=none" "$handler"
# The transcript in the log is the OPEN pass's text, verbatim — that is what phase 08 will answer.
assert_contains "and it logged the transcript the recogniser produced" "$open_text" "$handler"

assert_contains "Cortana said it cannot do that yet" "can't do that yet" "$(reply_text)"
assert_eq "a not-understood card is on screen" "yes" "$(has_node "$ROW_DIR/e3.xml" "cortana_card:not_understood")"

# A command that IS in the list must not reach the handler, or the row proves nothing.
before="$(diag not_understood | wc -l)"
ensure_start
cortana_assist
sleep 4
"$HERE/speak.sh" time_query 11 > /dev/null
after="$(diag not_understood | wc -l)"
assert_eq "a matched command does NOT reach the handler" "$before" "$after"

cortana_close
row_end
