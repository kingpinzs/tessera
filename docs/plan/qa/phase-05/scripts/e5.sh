#!/usr/bin/env bash
# E5 — autocorrect, suggestions per keystroke, and nothing learned in a password field.
#
#   "Typing a misspelling commits the autocorrected word; suggestions update per keystroke; nothing is
#    learned in a password field"
#
# The learning half is BRACKETED: the same unknown word committed twice in a password field must leave
# no trace in the keyboard's learned-words file, and committed twice in a text field must be learned —
# so a keyboard that learned nothing at all could not pass. The learned-words file is the :ime
# process's own (files/learned_words.txt); it is saved first and put back at the end, and the :ime
# process is restarted by its recorded pid so its memory matches the file again (Hard Rule 13).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E5 "autocorrect, per-keystroke suggestions, no learning in password fields"
kb_begin

learned() { adb shell run-as app.tileshell cat files/learned_words.txt 2>/dev/null | tr -d '\r'; }
saved="$ROW_DIR/.learned_before.txt"
learned > "$saved"
note "learned words before: $(wc -l < "$saved") line(s)"

strip() { ime_dump | sed -n 's/^ *strip=//p' | tr -d '\r'; }

# ---- suggestions update per keystroke ----------------------------------------------------------------
open_field field_text
D="$ROW_DIR/e5.xml"; kb_dump "$D"
prev=""
for k in b e c a s e; do
  tap_key "$D" "$k"
  sleep 0.8
  s="$(strip)"
  note "after '$k': $s"
  assert_ne "the strip changed on '$k'" "$prev" "$s"
  prev="$s"
done
assert_eq "R6 2.2.6 the autocorrect candidate for 'becase' is bold and first" "*because" "$(strip | cut -d'|' -f1 | tr -d ' ')"
assert_contains "the word as typed is still offered" "becase" "$(strip)"
# R6 2.2.6 says DRAWN bold (review m1): the first item's ink is denser than a regular item's, off pixels.
kb_dump "$D"; screencap "$ROW_DIR/e5_bold.png"
dens() { read -r l t r b <<< "$(bounds "$D" "kb_sugg_$1")"; python3 - "$ROW_DIR/e5_bold.png" "$l" "$t" "$r" "$b" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("L"); l, t, r, b = map(int, sys.argv[2:6])
px = [im.getpixel((x, y)) for y in range(t, b) for x in range(l, r)]
ink = [p for p in px if p > 150]
# ink pixels per inked column: a heavier stroke puts more ink in each column it touches
cols = sum(1 for x in range(l, r) if any(im.getpixel((x, y)) > 150 for y in range(t, b)))
print(round(len(ink) / max(cols, 1), 2))
PY
}
d0="$(dens 0)"; d1="$(dens 1)"
note "ink per inked column: item 0 (bold 'because') $d0, item 1 (regular) $d1"
assert_eq "R6 2.2.6 the autocorrect candidate is DRAWN bold (denser ink than a regular item)" "yes" "$(python3 -c "print('yes' if $d0 > 1.15 * $d1 else 'no')")"

# ---- a misspelling commits the autocorrected word ----------------------------------------------------
tap_key "$D" space; sleep 0.8
assert_eq "space commits the correction" "[because ]" "$(read_mirror text)"
tap_word "$D" "teh"; sleep 0.6
tap_key "$D" space; sleep 0.8
assert_eq "a second misspelling" "[because the ]" "$(read_mirror text)"
tap_word "$D" "recieve"; sleep 0.6
tap_key "$D" period; sleep 0.8
assert_eq "punctuation also commits the correction" "[because the receive.]" "$(read_mirror text)"
assert_contains "the diagnostics record the replacements" "autocorrect" "$(ime_log 'autocorrect' | tail -1)"

# ---- nothing learned in a password field (bracket: the text field DOES learn) ------------------------
open_field field_password
kb_dump "$D"
for _ in 1 2; do tap_word "$D" "zqxjv"; tap_key "$D" space; sleep 0.5; done
sleep 1
assert_eq "password field: the strip offers nothing while typing" "" "$(strip)"
assert_absent "password field: two commits leave no trace in the learned words" "zqxjv" "$(learned)"

open_field field_text
kb_dump "$D"
for _ in 1 2; do tap_word "$D" "zqxjv"; tap_key "$D" space; sleep 0.5; done
sleep 1
assert_contains "text field: two commits learn the word (the bracket)" "zqxjv" "$(learned)"
tap_word "$D" "zqx"; sleep 0.8
assert_contains "text field: the learned word is offered afterwards" "zqxjv" "$(strip)"

# ---- restore the learned words as they were ----------------------------------------------------------
adb shell run-as app.tileshell sh -c "'cat > files/learned_words.txt'" < "$saved"
pid="$(adb shell pidof app.tileshell:ime | tr -d '\r')"
note "restarting the :ime process (pid $pid) so it reloads the restored file"
[ -n "$pid" ] && adb shell run-as app.tileshell kill "$pid"
sleep 1
assert_absent "restored: the test word is gone again" "zqxjv" "$(learned)"

kb_end
row_end
