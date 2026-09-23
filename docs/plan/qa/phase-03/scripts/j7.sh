#!/usr/bin/env bash
# J7 — silence after a confirm card is not a request (found 2026-09-23 reproducing J6: Tess asked "Add this to
# your calendar?", listened for yes / no, the recogniser turned 1.75 s of silence into "AND", and that replaced
# the card with "Sorry, I can't do that yet.").
#
# The AVD's microphone is the silent vmic sink, which is exactly the failing input. After a typed calendar
# request the card must survive the listening window: the speech process reports no speech-level audio, and
# Tess keeps the card and its buttons. The card is then cancelled so nothing is written.
. "$(dirname "$0")/lib.sh"

row_begin J7 "silence after a confirm card leaves the card (no phantom \"AND\")"
ensure_start
cortana_assist; sleep 4
# The ring is a fixed-size buffer: once full, its line count stops growing, so "lines after N" is empty (run 3).
# Slice by the ring's own wall-clock stamps instead.
MARK_MS="$(adb shell date +%s%3N | tr -d '\r')"
type_request "add a meeting called standup to my calendar at ten AM" 12
dump_ui "$ROW_DIR/after_silence.xml"; screencap "$ROW_DIR/after_silence.png"
RING="$(diag | python3 -c "
import re, sys
t = int(sys.argv[1])
for line in sys.stdin:
    m = re.search(r'wall=(\d+)', line)
    if m and int(m.group(1)) >= t: sys.stdout.write(line)
" "$MARK_MS")"
note "ring since the request: $(echo "$RING" | grep -E '\[cortana\] (question spoken|silence at|a new request)|\[speech\] (asr: no speech|asr: final|final open)' | sed 's/^ *//' | cut -c40-170 | tr '\n' '|')"
assert_contains "Tess listened for the answer" "question spoken; listening for the answer" "$RING"
assert_absent "silence did not replace the card" "a new request replaced the pending" "$RING"
# Either the model decoded nothing or the silence gate dropped what it decoded; both leave the capture EMPTY.
# The launcher's own ring records what the speech process delivered ("[speech] final open=..."); the speech
# process's "asr: final" line lives in its own ring, which run 4 wrongly grepped here.
LAST="$(echo "$RING" | grep '\[speech\] final open=' | tail -1)"
note "the capture after the question: ${LAST#*\] }"
assert_contains "the capture after the question came back empty" 'open=""' "$LAST"
assert_contains "Tess kept the card" "silence at a pending card" "$RING"
assert_eq "the card's Add button is still on screen" yes "$(has_node "$ROW_DIR/after_silence.xml" cortana_card_button:confirm)"
tap_node "$ROW_DIR/after_silence.xml" cortana_card_button:cancel; sleep 2
cortana_close
note "restored: the card was cancelled, nothing was written"
row_end
