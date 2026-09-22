#!/usr/bin/env bash
# EDGE1 — the phase doc's edge cases that live in fields and keys.
#
#   * imeOptions action keys (Go / Search / Send / Next): label and behaviour; multi-line fields;
#     textNoSuggestions and textCapSentences / other capitalisation flags
#   * clipboard paste; very long text; fields with input filters
#   * swipe over non-dictionary words; very fast swipes
#   * long-press on a key with no alternates; caps lock then switching fields; double space after a
#     number or URL (no period); learning a misspelling then removing it from the dictionary ("– word")
#   * password fields: no suggestions, no learning (E2, E5 and E12 carry it; re-checked here once)
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin EDGE1 "edge cases in fields and keys"
kb_begin
D="$ROW_DIR/edge1.xml"
fresh() { open_field "${1:-field_text}"; kb_dump "$D"; }
strip() { ime_dump | sed -n 's/^ *strip=//p' | tr -d '\r'; }

# ---- action keys: what each draws and what it does -------------------------------------------------
for f in field_url:GO field_search:SEARCH field_send:SEND field_next:NEXT field_done:DONE; do
  field="${f%%:*}"; act="${f#*:}"
  fresh "$field"
  screencap "$ROW_DIR/edge1_enter_$field.png"
  read -r el et er eb <<< "$(bounds "$D" kb_key_enter)"
  px="$(python3 "$HERE/measure.py" px "$ROW_DIR/edge1_enter_$field.png" $((el + 5)) $((et + 5)))"
  case "$act" in
    GO|SEARCH) want="white" ;;   # R6 2.8.2 / 2.8.3, and Go per H9
    *) want="grey" ;;            # Send / Next / Done: the default grey ↵ (H9)
  esac
  set -- $px
  got="$([ "$1" -gt 200 ] && echo white || echo grey)"
  assert_eq "$field: the Enter key is $want" "$want" "$got"
  tap_key "$D" enter; sleep 1
  action="$(read_mirror action)"
  if [ "$act" = NEXT ]; then
    assert_eq "$field: Enter performs NEXT (focus moves on)" "IME_ACTION_NEXT@field_next" "$action"
  else
    assert_eq "$field: Enter performs $act" "IME_ACTION_$act@$field" "$action"
  fi
done

# ---- multi-line: Enter is a newline -------------------------------------------------------------------
fresh field_multiline
tap_word "$D" "ab"; tap_key "$D" enter; tap_word "$D" "cd"; sleep 0.8
assert_eq "multi-line: Enter inserts a newline" '[ab\ncd]' "$(read_mirror text)"

# ---- textNoSuggestions: no strip, no autocorrect ------------------------------------------------------
fresh field_nosuggest
tap_word "$D" "teh"; sleep 0.6
assert_eq "textNoSuggestions: the strip offers nothing" "" "$(strip)"
tap_key "$D" space; sleep 0.6
assert_eq "textNoSuggestions: no autocorrect ('teh ' stays)" "[teh ]" "$(read_mirror text)"

# ---- capitalisation flags -----------------------------------------------------------------------------
fresh field_capsent
tap_word "$D" "hi. yo"; sleep 0.8
assert_eq "textCapSentences: capitals at the start and after '. '" "[Hi. Yo]" "$(read_mirror text)"
fresh field_capwords
tap_word "$D" "ab cd"; sleep 0.8
assert_eq "textCapWords: every word capitalised" "[Ab Cd]" "$(read_mirror text)"
fresh field_text
tap_word "$D" "ab"; sleep 0.6
assert_eq "no caps flag: no automatic capital" "[ab]" "$(read_mirror text)"

# ---- input filters: LengthFilter(5) + lowercase only ---------------------------------------------------
fresh field_filter
tap_key "$D" shift; tap_word "$D" "abcdefgh"; sleep 0.8
assert_eq "input filters: the capital is dropped by the app and the length caps at 5" "[bcdef]" "$(read_mirror text)"
tap_key "$D" bksp; tap_word "$D" "z"; sleep 0.8
assert_eq "input filters: typing after a filtered edit still lands where the caret is" "[bcdez]" "$(read_mirror text)"

# ---- clipboard paste (the app edits the text; the keyboard keeps up) -----------------------------------
fresh field_text
tap_word "$D" "copy"; sleep 0.5
adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A; adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_C
adb shell input keyevent KEYCODE_MOVE_END; adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_V
sleep 0.8
assert_eq "paste: the app pasted" "[copycopy]" "$(read_mirror text)"
tap_word "$D" "x"; sleep 0.6
assert_eq "paste: the keyboard types at the caret after the paste" "[copycopyx]" "$(read_mirror text)"

# ---- very long text ------------------------------------------------------------------------------------
# `input text` drops characters on a long string (runs 1-2 got 481 and 555 of 1500), so the fixture puts
# the 1500 characters in itself (--ei fill) with the caret at the end; the keyboard then types after them
# and reads only the 64 characters before the caret.
adb shell am start -S -W -n "$FIX/.MainActivity" -e focus field_multiline --ei fill 1500 >/dev/null
sleep 2.5; kb_dump "$D"
assert_eq "very long text: the field holds 1500 characters" "1500" "$(read_mirror len)"
tap_key "$D" space; tap_word "$D" "end"; sleep 0.8
len="$(read_mirror len)"
assert_eq "very long text: 1500 characters, then ' end' typed by the keyboard" "1504" "$len"
tap_key "$D" bksp; tap_key "$D" bksp; tap_key "$D" bksp; sleep 0.6
assert_eq "very long text: backspace still deletes at the end" "1501" "$(read_mirror len)"

# ---- swipes: a non-dictionary path, and a very fast swipe ----------------------------------------------
fresh field_text
path() { local w="$1" i p="" ; for ((i = 0; i < ${#w}; i++)); do read -r x y <<< "$(node_center "$D" "kb_key_${w:i:1}")"; p="${p:+$p;}$x,$y"; done; echo "$p"; }
swipe_pts "$(path qzxv)" 8 > /dev/null; sleep 1
t="$(read_mirror text)"
note "non-dictionary swipe q-z-x-v committed: $t"
assert_eq "a non-dictionary swipe commits a dictionary word or nothing, never garbage letters" "no" "$([ "$t" = "[qzxv]" ] && echo yes || echo no)"
assert_eq "the keyboard is still up after it" "yes" "$(kb_dump "$D"; has_node "$D" kb_key_q)"
fresh field_text
swipe_pts "$(path helo)" 1 > /dev/null; sleep 1
t="$(read_mirror text)"
note "very fast swipe h-e-l-o (1 step per segment): $t; decoder: $(ime_log 'word flow' | tail -1)"
# Run 1 committed "[h]": the flick was taken as a TAP on h. A swipe, however fast, is a word.
assert_eq "a very fast swipe is decoded as a word, not taken as a tap" "yes" "$([ ${#t} -gt 3 ] && echo yes || echo no)"
assert_absent "no crash in the keyboard's process" "FATAL" "$(ime_dump | head -3)"

# ---- long-press on a key with no alternates ------------------------------------------------------------
fresh field_text
read -r gx gy <<< "$(node_center "$D" kb_key_g)"
adb shell input swipe $gx $gy $gx $gy 1500 &
h=$!; sleep 1.0; kb_dump "$ROW_DIR/edge1_g_hold.xml"; wait $h; sleep 0.6
assert_eq "no alternates for g: the normal press popup, nothing else" "yes no" "$(has_node "$ROW_DIR/edge1_g_hold.xml" kb_popup) $(has_node "$ROW_DIR/edge1_g_hold.xml" kb_alt_0)"
assert_eq "and lifting commits g" "[g]" "$(read_mirror text)"

# ---- caps lock then switching fields -------------------------------------------------------------------
fresh field_text
read -r shx shy <<< "$(node_center "$D" kb_key_shift)"
script "tap $shx $shy; sleep 110; tap $shx $shy" > /dev/null; sleep 0.5
assert_contains "caps lock is on" "shift=LOCKED" "$(ime_dump)"
F="$ROW_DIR/.edge1_fix.xml"; dump_ui "$F"; tap_node "$F" "$FIX:id/field_number"; sleep 1.2
dump_ui "$F"; tap_node "$F" "$FIX:id/field_text"; sleep 1.2
assert_contains "caps lock does not follow into the next field" "shift=OFF" "$(ime_dump)"

# ---- double space after a number or in a URL: no period ------------------------------------------------
fresh field_text
read -r px py <<< "$(node_center "$D" kb_key_space)"
tap_key "$D" sym; sleep 0.5; kb_dump "$D"; tap_key "$D" 5; tap_key "$D" abc; sleep 0.5; kb_dump "$D"
script "tap $px $py; sleep 400; tap $px $py" > /dev/null; sleep 0.8
assert_eq "double space after a number: two spaces, no period" "[5  ]" "$(read_mirror text)"
fresh field_url
read -r px py <<< "$(node_center "$D" kb_key_space)"
tap_word "$D" "ab"
script "tap $px $py; sleep 400; tap $px $py" > /dev/null; sleep 0.8
assert_eq "double space in a URL field: no period" "[ab  ]" "$(read_mirror text)"

# ---- learning a misspelling, then removing it ("– word", R6 2.2.7) -------------------------------------
learned() { adb shell run-as app.tileshell cat files/learned_words.txt 2>/dev/null | tr -d '\r'; }
saved="$ROW_DIR/.learned_before.txt"; learned > "$saved"
fresh field_text
for _ in 1 2; do tap_word "$D" "zqxjw"; tap_key "$D" space; sleep 0.4; done
assert_contains "the misspelling is learned after two commits" "zqxjw" "$(learned)"
# Put the caret INTO the first word with the arrow keys. (Run 2 tapped the word in the text instead, and
# Android's own spell checker — not the keyboard — answered a tap on a red-underlined word with the app's
# suggestion popup, which lies over the left of the strip and took the next tap. Recorded as a finding.)
for _ in 1 2 3 4 5 6 7 8 9; do adb shell input keyevent KEYCODE_DPAD_LEFT; done
sleep 1
note "caret after the arrow keys: $(read_mirror sel)"
note "strip with the caret in the word: $(strip)"
assert_contains "R6 2.2.7: the strip offers '– zqxjw'" "– zqxjw" "$(strip)"
kb_dump "$D"
rm_idx="$(grep -o 'resource-id="kb_sugg_[0-9]*"[^>]*content-desc="– zqxjw"' "$D" | head -1 | sed 's/.*kb_sugg_\([0-9]*\).*/\1/')"
[ -z "$rm_idx" ] && rm_idx="$(grep -o 'content-desc="– zqxjw"[^>]*resource-id="kb_sugg_[0-9]*"' "$D" | head -1 | sed 's/.*kb_sugg_\([0-9]*\).*/\1/')"
note "the remove item is kb_sugg_$rm_idx"
tap_node "$D" "kb_sugg_$rm_idx"; sleep 1
assert_contains "the tap on '– zqxjw' reached the dictionary" "dictionary - zqxjw" "$(ime_log 'dictionary' | tail -1)"
assert_absent "'– word' removes it from the learned words (and from the pending count)" "zqxjw" "$(learned)"
adb shell run-as app.tileshell sh -c "'cat > files/learned_words.txt'" < "$saved"
pid="$(adb shell pidof app.tileshell:ime | tr -d '\r')"; [ -n "$pid" ] && adb shell run-as app.tileshell kill "$pid"

# ---- password: no suggestions, no learning (once more, briefly) ----------------------------------------
fresh field_password
tap_word "$D" "teh"; sleep 0.6
assert_eq "password: no suggestions" "" "$(strip)"

kb_end
row_end
