#!/usr/bin/env bash
# E7 — the confirmation flow.
#
# The row in full (phase doc): the text read-back with R6 §3.4.1's wording and its four answers; a call
# that reads back and dials only after confirm; the R6 §3.4.2 reminder card measured, stored only after
# "Remind" or a spoken "yes" and never after "Cancel"/"no"; the R7 §3.8.1 saved card measured; the
# missing-time card answered BOTH ways (a spoken "whenever" and a tap on Remind with the fields empty);
# the calendar add / delete and reminder delete cards acting only on confirm; and no reminder or
# calendar card offering "add more".
#
# The doc names both an answer by voice and an answer by button ("after 'Remind' or a spoken 'yes'"),
# so the driver exercises both and records which path each step used.
. "$(dirname "$0")/lib.sh"

row_begin E7 "the confirmation flow"

REMINDERS=/data/data/app.tileshell/files/cortana_reminders.json
PX_PER_EPX="$(python3 -c "print(1080/360)")"

reminders_json() { adb shell run-as app.tileshell cat $REMINDERS 2>/dev/null; }
reminder_count() { reminders_json | python3 -c "
import json,sys
raw = sys.stdin.read().strip()
print(len(json.loads(raw).get('reminders', [])) if raw else 0)
" 2>/dev/null || echo 0; }
sms_sent_count() { adb shell content query --uri content://sms/sent --projection _id 2>/dev/null | grep -c '^Row:'; }
sms_sent_bodies() { adb shell content query --uri content://sms/sent --projection body 2>/dev/null; }
calls_count() { adb shell dumpsys telecom 2>/dev/null | grep -cE 'Call id|mCallId'; }
events_count() { adb shell content query --uri content://com.android.calendar/events --projection title 2>/dev/null | grep -c '^Row:'; }

open_cortana() {
  ensure_start
  cortana_assist
  sleep 4
}

# An answer spoken into the microphone. The confirm card's bar has no accent mic (R6 §3.3.7), so this
# also tells us whether a confirmation can be answered by VOICE at all — which the row requires.
answer_by_voice() { # utterance [settle]
  "$HERE/speak.sh" "$1" "${2:-11}" 2>&1
}

# An answer given by tapping the card's own button.
answer_by_tap() { # confirm|cancel
  local dump="$ROW_DIR/.answer.xml"
  dump_ui "$dump" || true
  tap_node "$dump" "cortana_card_button:$1"
  sleep 6
}

epx_between() { python3 -c "print(abs($2 - $1) / $PX_PER_EPX)"; }
epx_of() { python3 -c "print($1 / $PX_PER_EPX)"; }

# ---------------------------------------------------------------- 1. the text read-back

open_cortana
answer_by_voice text_contact > "$ROW_DIR/e7_text_final.txt"
dump_ui "$ROW_DIR/e7_readback.xml"
screencap "$ROW_DIR/e7_readback.png"
readback="$(reply_text)"
note "read-back reply: $readback"
assert_contains "R6 3.4.1 read-back names the contact" "I'll text Mom" "$readback"
assert_contains "R6 3.4.1 read-back quotes the message" "on my way" "$readback"
assert_contains "R6 3.4.1 read-back offers the three answers" "Send it, add more, or try again?" "$readback"
assert_eq "the text read-back card is on screen" "yes" "$(has_node "$ROW_DIR/e7_readback.xml" "cortana_card:text_readback")"
assert_eq "with a Send button" "yes" "$(has_node "$ROW_DIR/e7_readback.xml" "cortana_card_button:confirm")"
assert_eq "and a Cancel button" "yes" "$(has_node "$ROW_DIR/e7_readback.xml" "cortana_card_button:cancel")"

# The row's answers are spoken ones, so a confirmation has to leave a way to speak.
listening="$(has_node "$ROW_DIR/e7_readback.xml" cortana_listening_box)"
mic="$(has_node "$ROW_DIR/e7_readback.xml" cortana_text_box_mic)"
note "at the read-back: listening box=$listening, tappable mic=$mic"
if [ "$listening" = yes ] || [ "$mic" = yes ]; then
  _verdict PASS "a confirmation can be answered by voice" "listening=$listening mic=$mic"
else
  _verdict FAIL "a confirmation can be answered by voice" \
    "no listening box and no tappable mic: the card can only be answered by its buttons"
fi

# ---------------------------------------------------------------- 2. cancel sends nothing

sent_before="$(sms_sent_count)"
if [ "$listening" = yes ] || [ "$mic" = yes ]; then
  answer_by_voice cancel > /dev/null
else
  note "answering by tap, because voice was not available at the card"
  answer_by_tap cancel
fi
assert_eq "cancel sends nothing" "$sent_before" "$(sms_sent_count)"
assert_contains "and Cortana says so" "won't send it" "$(reply_text)"

# ---------------------------------------------------------------- 3. add more, then send it

open_cortana
answer_by_voice text_contact > /dev/null
answer_by_voice add_more > /dev/null
addmore_reply="$(reply_text)"
note "add more reply: $addmore_reply"
assert_contains "R6 3.4.1 add more asks for the addition" "what would you like to add" "$addmore_reply"

answer_by_voice and_bring_milk > /dev/null
whole="$(reply_text)"
note "second read-back: $whole"
assert_contains "the next read-back starts with Okay, now I've got:" "Okay, now I've got:" "$whole"
assert_contains "and carries the FIRST part of the message" "on my way" "$whole"
assert_contains "and the added part too" "milk" "$whole"

sent_before="$(sms_sent_count)"
answer_by_voice send_it > /dev/null
dump_ui "$ROW_DIR/e7_sent.xml"
screencap "$ROW_DIR/e7_sent.png"
assert_contains "send it replies Message sent." "Message sent." "$(reply_text)"
sms_sent_bodies > "$ROW_DIR/e7_sms_sent.txt" 2>&1
assert_ne "and the SMS provider gained a sent row" "$sent_before" "$(sms_sent_count)"
assert_contains "whose body is the WHOLE message" "milk" "$(cat "$ROW_DIR/e7_sms_sent.txt")"

# ---------------------------------------------------------------- 4. try again

open_cortana
answer_by_voice text_contact > /dev/null
answer_by_voice try_again > /dev/null
assert_contains "try again asks for the message again" "What do you want to say?" "$(reply_text)"

# ---------------------------------------------------------------- 5. the call reads back before dialling

open_cortana
answer_by_voice call_contact > /dev/null
dump_ui "$ROW_DIR/e7_call.xml"
screencap "$ROW_DIR/e7_call.png"
call_reply="$(reply_text)"
note "call read-back: $call_reply"
assert_contains "the call is read back" "I'll call Mom" "$call_reply"
assert_contains "and asks before dialling" "Call, or try again?" "$call_reply"
assert_eq "the call confirmation card is on screen" "yes" "$(has_node "$ROW_DIR/e7_call.xml" "cortana_card:call_confirm")"
adb shell dumpsys telecom > "$ROW_DIR/e7_telecom_before.txt" 2>/dev/null
calls_before="$(calls_count)"
assert_eq "dumpsys telecom shows no call yet" "$calls_before" "$(calls_count)"

answer_by_voice yes 12 > /dev/null
adb shell dumpsys telecom > "$ROW_DIR/e7_telecom_after.txt" 2>/dev/null
assert_ne "and the call appears only after confirm" "$calls_before" "$(calls_count)"
# Leave the device as it was found (PLAN RV12).
adb shell input keyevent KEYCODE_ENDCALL
sleep 3

# ---------------------------------------------------------------- 6. the reminder card, measured

open_cortana
answer_by_voice reminder_time > /dev/null
dump_ui "$ROW_DIR/e7_reminder_card.xml"
screencap "$ROW_DIR/e7_reminder_card.png"
card="$ROW_DIR/e7_reminder_card.xml"

assert_eq "the reminder confirm card is on screen" "yes" "$(has_node "$card" "cortana_card:reminder_confirm")"
assert_eq "R6 3.4.2 title" "Remind you about this?" "$(node_text "$card" cortana_card_title)"
assert_eq "R6 3.4.2 caption" "Reminder" "$(node_text "$card" cortana_card_caption)"
assert_eq "field: the reminder text" "yes" "$(has_node "$card" "cortana_card_field:reminder_text")"
assert_eq "field: the time" "yes" "$(has_node "$card" "cortana_card_field:reminder_time")"
assert_eq "field: the day" "yes" "$(has_node "$card" "cortana_card_field:reminder_day")"
assert_contains "R6 3.4.2 recurrence dropdown" "Only once" "$(node_text "$card" cortana_card_recurrence)"
assert_eq "R6 3.4.2 Add a photo row" "yes" "$(has_node "$card" cortana_card_add_photo)"
assert_eq "R6 3.4.2 Remind button" "yes" "$(has_node "$card" "cortana_card_button:confirm")"
assert_eq "R6 3.4.2 Cancel button" "yes" "$(has_node "$card" "cortana_card_button:cancel")"
assert_contains "R6 3.4.2 callout" "Yes, No, or Cancel" "$(node_text "$card" cortana_card_callout)"
# T-M9: add more belongs to the text read-back and nowhere else.
assert_eq "the reminder card does NOT offer add more" "no" "$(has_node "$card" "cortana_card_button:add_more")"

title_b="$(bounds "$card" cortana_card_title)"
[ -n "$title_b" ] && { set -- $title_b; assert_within "R6 3.4.2 title left edge 16 epx" 16 "$(epx_of $1)" 1.4; }

f1="$(bounds "$card" "cortana_card_field:reminder_text")"
f2="$(bounds "$card" "cortana_card_field:reminder_time")"
if [ -n "$f1" ] && [ -n "$f2" ]; then
  set -- $f1; f1_top=$2 f1_bottom=$4
  set -- $f2; f2_top=$2
  assert_within "R6 3.4.2 field height 43 epx" 43 "$(epx_between $f1_top $f1_bottom)" 1.4
  assert_within "R6 3.4.2 field pitch 53.6 epx" 53.6 "$(epx_between $f1_top $f2_top)" 1.4
else
  _verdict FAIL "the card's fields are measurable" "text=[$f1] time=[$f2]"
fi

combo="$(bounds "$card" cortana_card_recurrence)"
[ -n "$combo" ] && { set -- $combo; assert_within "R6 3.4.2 recurrence combo 32 epx tall" 32 "$(epx_between $2 $4)" 1.4; }

b1="$(bounds "$card" "cortana_card_button:confirm")"
b2="$(bounds "$card" "cortana_card_button:cancel")"
if [ -n "$b1" ] && [ -n "$b2" ]; then
  set -- $b1; b1_l=$1 b1_t=$2 b1_r=$3 b1_b=$4
  set -- $b2; b2_l=$1 b2_r=$3
  assert_within "R6 3.4.2 buttons 32.2 epx tall" 32.2 "$(epx_between $b1_t $b1_b)" 1.4
  assert_within "R6 3.4.2 Remind 164.6 epx wide" 164.6 "$(epx_between $b1_l $b1_r)" 1.4
  assert_within "R6 3.4.2 Cancel 160.0 epx wide" 160.0 "$(epx_between $b2_l $b2_r)" 1.4
  assert_within "R6 3.4.2 gap 3.8 epx" 3.8 "$(epx_between $b1_r $b2_l)" 1.4
  assert_within "R6 3.4.2 left margin 16.3 epx" 16.3 "$(epx_of $b1_l)" 1.4
  assert_within "R6 3.4.2 right margin 15.7 epx" 15.7 "$(python3 -c "print((1080 - $b2_r) / $PX_PER_EPX)")" 1.4
else
  _verdict FAIL "the card's buttons are measurable" "remind=[$b1] cancel=[$b2]"
fi

# ---------------------------------------------------------------- 7. never stored on a refusal

before="$(reminder_count)"
answer_by_voice no > /dev/null
assert_eq "no stores nothing" "$before" "$(reminder_count)"
assert_contains "and Cortana says so" "nothing saved" "$(reply_text)"

# ---------------------------------------------------------------- 8. stored after a spoken yes, and the saved card measured

open_cortana
answer_by_voice reminder_time > /dev/null
before="$(reminder_count)"
answer_by_voice yes > /dev/null
dump_ui "$ROW_DIR/e7_saved.xml"
screencap "$ROW_DIR/e7_saved.png"
reminders_json > "$ROW_DIR/e7_reminders.json" 2>&1
assert_ne "a spoken yes stores the reminder" "$before" "$(reminder_count)"

saved="$ROW_DIR/e7_saved.xml"
assert_eq "R7 3.8.1 saved card is on screen" "yes" "$(has_node "$saved" "cortana_card:reminder_saved")"
assert_eq "R7 3.8.1 title" "I'll remind you." "$(node_text "$saved" cortana_card_title)"
assert_eq "R7 3.8.1 caption" "Reminder" "$(node_text "$saved" cortana_card_caption)"
assert_contains "R7 3.8.1 the saved row carries the reminder text" "bins" "$(node_text "$saved" cortana_card_saved_text)"
subline="$(node_text "$saved" cortana_card_saved_subline)"
note "saved subline: $subline"
assert_contains "R7 3.8.1 subline is written with a hyphen, not 'at'" " - " "$subline"

row_b="$(bounds "$saved" cortana_card_saved_text)"
[ -n "$row_b" ] && { set -- $row_b; assert_within "R7 3.8.1 saved row text left edge 62.9 epx" 62.9 "$(epx_of $1)" 1.4; }

# ---------------------------------------------------------------- 9. the missing-time card, both answers

open_cortana
answer_by_voice reminder_no_time > /dev/null
dump_ui "$ROW_DIR/e7_missing_time.xml"
screencap "$ROW_DIR/e7_missing_time.png"
assert_eq "R6 3.4.3 missing-time title (H16)" "When would you like to be reminded?" \
  "$(node_text "$ROW_DIR/e7_missing_time.xml" cortana_card_title)"

before="$(reminder_count)"
answer_by_voice whenever > /dev/null
dump_ui "$ROW_DIR/e7_whenever_voice.xml"
screencap "$ROW_DIR/e7_whenever_voice.png"
assert_ne "a spoken whenever stores a Whenever reminder" "$before" "$(reminder_count)"
assert_eq "whose saved card has the lightbulb row" "yes" "$(has_node "$ROW_DIR/e7_whenever_voice.xml" cortana_card_saved_row)"
assert_eq "and NO subline (H16)" "no" "$(has_node "$ROW_DIR/e7_whenever_voice.xml" cortana_card_saved_subline)"

# ...and separately, a TAP on Remind with the time and day fields empty.
open_cortana
answer_by_voice reminder_no_time > /dev/null
before="$(reminder_count)"
answer_by_tap confirm
dump_ui "$ROW_DIR/e7_whenever_tap.xml"
screencap "$ROW_DIR/e7_whenever_tap.png"
assert_ne "a TAP on Remind with the fields empty also stores a Whenever reminder" "$before" "$(reminder_count)"
assert_eq "and its saved card has no subline either" "no" "$(has_node "$ROW_DIR/e7_whenever_tap.xml" cortana_card_saved_subline)"
reminders_json > "$ROW_DIR/e7_reminders_whenever.json" 2>&1
assert_contains "the stored reminder really has no time" '"timeMs":null' \
  "$(cat "$ROW_DIR/e7_reminders_whenever.json" | tr -d ' ')"

# ---------------------------------------------------------------- 10. calendar add acts only on confirm

open_cortana
answer_by_voice calendar_add > /dev/null
dump_ui "$ROW_DIR/e7_calendar_card.xml"
screencap "$ROW_DIR/e7_calendar_card.png"
assert_eq "the calendar card is on screen (H17)" "yes" "$(has_node "$ROW_DIR/e7_calendar_card.xml" "cortana_card:calendar_confirm")"
assert_eq "the calendar card does NOT offer add more" "no" "$(has_node "$ROW_DIR/e7_calendar_card.xml" "cortana_card_button:add_more")"
events_before="$(events_count)"
answer_by_voice no > /dev/null
assert_eq "no adds nothing to the calendar" "$events_before" "$(events_count)"

open_cortana
answer_by_voice calendar_add > /dev/null
events_before="$(events_count)"
answer_by_voice yes > /dev/null
assert_ne "yes adds the event" "$events_before" "$(events_count)"
adb shell content query --uri content://com.android.calendar/events --projection title > "$ROW_DIR/e7_events.txt" 2>&1

# ---------------------------------------------------------------- 11. the two delete cards

open_cortana
answer_by_voice calendar_delete > /dev/null
dump_ui "$ROW_DIR/e7_calendar_delete.xml"
screencap "$ROW_DIR/e7_calendar_delete.png"
assert_eq "the delete card is on screen (H18)" "yes" "$(has_node "$ROW_DIR/e7_calendar_delete.xml" "cortana_card:delete_confirm")"
assert_eq "the delete card does NOT offer add more" "no" "$(has_node "$ROW_DIR/e7_calendar_delete.xml" "cortana_card_button:add_more")"
events_before="$(events_count)"
answer_by_voice yes > /dev/null
assert_ne "confirming deletes the event" "$events_before" "$(events_count)"

open_cortana
answer_by_voice delete_reminder > /dev/null
dump_ui "$ROW_DIR/e7_reminder_delete.xml"
screencap "$ROW_DIR/e7_reminder_delete.png"
assert_eq "the reminder delete card is on screen (H18)" "yes" "$(has_node "$ROW_DIR/e7_reminder_delete.xml" "cortana_card:delete_confirm")"
before="$(reminder_count)"
answer_by_voice yes > /dev/null
assert_ne "confirming deletes the reminder" "$before" "$(reminder_count)"

reminders_json > "$ROW_DIR/e7_reminders_final.json" 2>&1
note "reminders left at the end: $(reminder_count)"

cortana_close
row_end
