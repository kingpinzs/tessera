#!/usr/bin/env bash
# E2 — typing produces the expected text in every input type, and in a real third-party app.
#
#   "Typing into the fixture app's text field produces the expected text (the fixture mirrors the raw
#    text into a TextView read from adb shell uiautomator dump), across text / number / phone / email /
#    password input types; a real third-party app's field is typed into as well and its visible text
#    checked"
#
# Every key is tapped where the keyboard's OWN dump says it is (kb_dump), so the row also proves the
# layout each field opens on: number fields open on the digits page, phone fields on the keypad.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E2 "typing in text, number, phone, email, password and a third-party app"
kb_begin

type_in() { # field word-keys... (each arg a key id)
  local field="$1"; shift
  open_field "$field"
  local d="$ROW_DIR/.e2_$field.xml"
  kb_dump "$d" || { log "no keyboard dump for $field"; return 1; }
  note "$field opened on: $(ime_layer)"
  local k
  for k in "$@"; do
    case "$k" in
      +*) # a layer switch: re-dump after it so the next key is found on the new layer
        tap_key "$d" "${k#+}"; sleep 0.6; kb_dump "$d" ;;
      *) tap_key "$d" "$k" ;;
    esac
  done
  sleep 1
}

# text (no caps flag): plain letters
type_in field_text h e l l o
assert_eq "text field" "[hello]" "$(read_mirror text)"

# number: the field opens on the symbols page, digits on its top row
type_in field_number 4 2 0 7
assert_eq "number field" "[4207]" "$(read_mirror text)"
assert_eq "number field opens on the digits page" "layer=SYMBOLS_1" "$(ime_layer)"

# phone: R6 §2.8.4's keypad
type_in field_phone 5 5 5 0 1 9 9
assert_eq "phone field" "[5550199]" "$(read_mirror text)"
assert_eq "phone field opens on the keypad" "layer=PHONE" "$(ime_layer)"

# email: letters, "@" from the symbols page, back to letters, a period
type_in field_email a +sym u40 +abc b period c o
assert_eq "email field" "[a@b.co]" "$(read_mirror text)"

# password: shift for the capital, a digit from the symbols page; the mirror shows the raw text
type_in field_password shift s e c r e t +sym 1
assert_eq "password field (raw, from the mirror)" "[Secret1]" "$(read_mirror text)"
strip="$(ime_dump | sed -n 's/^ *strip=//p' | tr -d '\r')"
assert_eq "password field: the strip offers nothing" "" "$strip"
d="$ROW_DIR/.e2_pw.xml"; kb_dump "$d"
assert_eq "password field: no voice key in the strip" "no" "$(has_node "$d" kb_mic)"

# a real third-party app: Fossify Notes' editor
adb shell am start -S -W -n org.fossify.notes/.activities.MainActivity >/dev/null
sleep 3
n="$ROW_DIR/.notes.xml"
dump_ui "$n"
editor_id="$(grep -o 'resource-id="org.fossify.notes:id/[a-z_]*text[a-z_]*"' "$n" | head -1 | sed 's/resource-id="//; s/"$//')"
note "notes editor node: $editor_id"
if [ -n "$editor_id" ]; then tap_node "$n" "$editor_id"; else adb shell input tap 540 700; fi
sleep 2
d="$ROW_DIR/.e2_notes.xml"
kb_dump "$d"
assert_eq "the keyboard is up in the third-party app" "yes" "$(has_node "$d" kb_key_q)"
tap_word "$d" "tessera"
sleep 1
dump_ui "$n"
screencap "$ROW_DIR/e2_notes.png"
 # The editor asks for sentence capitals, so the first letter is a capital: the platform's own caps rule
# (getCursorCapsMode) reaching the keyboard in someone else's app.
assert_contains "Fossify Notes shows the typed word, sentence-capitalised" 'text="Tessera"' "$(grep -o 'text="[^"]*"' "$n" | tr '\n' ' ')"
# leave the note empty again so the app's state is what it was
for _ in 1 2 3 4 5 6 7 8; do tap_key "$d" bksp; done

kb_end
row_end
