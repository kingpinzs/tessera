#!/usr/bin/env bash
# E14 — person reminders (direction ruled A, 2026-09-17: all four paths count).
#
# "Any real contact counts, in either direction: an outgoing call, an incoming call that is answered,
#  or a text sent or received. A missed or declined call doesn't count."
#
# Each of the four counting paths runs on a FRESH reminder, because a reminder that fires leaves the
# active list — reusing one would prove only that the first path works. The two non-counting paths run
# last, against a live reminder, and must fire nothing.
. "$(dirname "$0")/lib.sh"

row_begin E14 "person reminders"

CONTACT_NUMBER=5551234567
OTHER_NUMBER=5559999999

restore() {
  adb emu gsm cancel $CONTACT_NUMBER >/dev/null 2>&1
  adb emu gsm cancel $OTHER_NUMBER >/dev/null 2>&1
  adb shell input keyevent KEYCODE_ENDCALL >/dev/null 2>&1
  note "restored: no call in progress"
}
trap restore EXIT

notif_count() { adb shell dumpsys notification --noredact 2>/dev/null | grep -c 'pkg=app.tileshell'; }
reminder_count() { adb shell run-as app.tileshell cat /data/data/app.tileshell/files/cortana_reminders.json 2>/dev/null | grep -o '"id"' | wc -l; }

# The hard-restricted permissions the row depends on. adb install allow-lists them unless
# --restrict-permissions is passed, and the shell is never installed with that flag (Decisions).
adb shell dumpsys package app.tileshell > "$ROW_DIR/e14_perms.txt" 2>/dev/null
assert_contains "READ_CALL_LOG is allow-listed" "READ_CALL_LOG: granted=true" "$(cat "$ROW_DIR/e14_perms.txt")"
assert_contains "READ_SMS is allow-listed" "READ_SMS: granted=true" "$(cat "$ROW_DIR/e14_perms.txt")"

make_reminder() { # -> stores a fresh person reminder, asserts the card
  ensure_start
  cortana_assist
  sleep 4
  "$HERE/speak.sh" reminder_person 12 > /dev/null 2>&1
  dump_ui "$ROW_DIR/e14_card_$1.xml"
  screencap "$ROW_DIR/e14_card_$1.png"
  assert_eq "$1: the person card is on screen (H29)" "yes" \
    "$(has_node "$ROW_DIR/e14_card_$1.xml" "cortana_card:reminder_confirm")"
  assert_eq "$1: with a contact chip" "yes" "$(has_node "$ROW_DIR/e14_card_$1.xml" cortana_card_contact)"
  assert_eq "$1: and NO recurrence dropdown, because it fires once" "no" \
    "$(has_node "$ROW_DIR/e14_card_$1.xml" cortana_card_recurrence)"
  assert_contains "$1: the spoken wording (H29)" "next time you talk to" "$(reply_text)"
  "$HERE/speak.sh" yes 12 > /dev/null 2>&1
  dump_ui "$ROW_DIR/e14_saved_$1.xml"
  assert_contains "$1: the saved card's subline (H29)" "Next time I talk to" \
    "$(node_text "$ROW_DIR/e14_saved_$1.xml" cortana_card_saved_subline)"
}

fires() { # label
  sleep 12
  adb shell dumpsys notification --noredact > "$ROW_DIR/e14_notif_$1.txt" 2>/dev/null
  echo "$(notif_count)"
}

# ---- 1. an INCOMING call that is answered -------------------------------------------------------
make_reminder incoming_call
before="$(notif_count)"
adb emu gsm call $CONTACT_NUMBER >/dev/null 2>&1
sleep 4
adb shell input keyevent KEYCODE_CALL
sleep 4
# Telecom writes the call-log entry at DISCONNECT, which is why the row hangs up before checking.
adb emu gsm cancel $CONTACT_NUMBER >/dev/null 2>&1
assert_ne "an answered incoming call fires it" "$before" "$(fires incoming_call)"

# ---- 2. an OUTGOING call ------------------------------------------------------------------------
make_reminder outgoing_call
before="$(notif_count)"
ensure_start
cortana_assist
sleep 4
"$HERE/speak.sh" call_contact 12 > /dev/null 2>&1
"$HERE/speak.sh" yes 12 > /dev/null 2>&1
sleep 4
# The console may refuse to accept an outgoing call; the row records that rather than failing on it,
# and P8 covers a real outgoing call on the phone.
if adb emu gsm list 2>/dev/null | grep -q "$CONTACT_NUMBER"; then
  adb emu gsm accept $CONTACT_NUMBER >/dev/null 2>&1 || note "the console refused 'gsm accept' for an outgoing call; P8 covers it"
fi
adb emu gsm cancel $CONTACT_NUMBER >/dev/null 2>&1
adb shell input keyevent KEYCODE_ENDCALL
assert_ne "an outgoing call fires it" "$before" "$(fires outgoing_call)"

# ---- 3. an INCOMING text ------------------------------------------------------------------------
make_reminder incoming_text
before="$(notif_count)"
adb emu sms send $CONTACT_NUMBER hi >/dev/null 2>&1
assert_ne "a received text fires it" "$before" "$(fires incoming_text)"

# ---- 4. an OUTGOING text ------------------------------------------------------------------------
make_reminder outgoing_text
before="$(notif_count)"
ensure_start
cortana_assist
sleep 4
"$HERE/speak.sh" text_contact 12 > /dev/null 2>&1
"$HERE/speak.sh" send_it 12 > /dev/null 2>&1
assert_ne "a sent text fires it" "$before" "$(fires outgoing_text)"

# ---- and the two that must NOT count -------------------------------------------------------------
make_reminder negatives
before="$(notif_count)"
# A call from someone else, answered and ended.
adb emu gsm call $OTHER_NUMBER >/dev/null 2>&1
sleep 3
adb shell input keyevent KEYCODE_CALL
sleep 3
adb emu gsm cancel $OTHER_NUMBER >/dev/null 2>&1
assert_eq "a call from another number fires nothing" "$before" "$(fires other_number)"

# A call from the contact, never answered.
adb emu gsm call $CONTACT_NUMBER >/dev/null 2>&1
sleep 5
adb emu gsm cancel $CONTACT_NUMBER >/dev/null 2>&1
assert_eq "a missed call from the contact fires nothing" "$before" "$(fires missed)"

note "reminders left in the store: $(reminder_count)"
row_end
