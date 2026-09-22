#!/usr/bin/env bash
# E10 — the locked commands (PQ3, Jeremy: "(a)").
#
# Every ALLOWED command runs directly with the keyguard up; every GATED one shows "Unlock to continue"
# and changes nothing; a new request replaces the card and drops the earlier one; and after an unlock
# only the request that was on the card runs — none of the earlier gated ones.
#
# The gate lives in the action layer, not the matcher, so a gated command is UNDERSTOOD and then
# refused. That is what this row proves: the matcher line says what it was, and nothing happened.
. "$(dirname "$0")/lib.sh"

row_begin E10 "the locked commands"

PIN=1234
pin_set=no
restore() {
  [ "$pin_set" = yes ] && adb shell locksettings clear --old $PIN >/dev/null 2>&1
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  note "restored: PIN cleared, device awake"
}
trap restore EXIT

sms_sent_count() { adb shell content query --uri content://sms/sent --projection _id 2>/dev/null | grep -c '^Row:'; }
calls_count() { adb shell dumpsys telecom 2>/dev/null | grep -cE 'Call id|mCallId'; }
events_count() { adb shell content query --uri content://com.android.calendar/events --projection title 2>/dev/null | grep -c '^Row:'; }
reminder_count() { adb shell run-as app.tileshell cat /data/data/app.tileshell/files/cortana_reminders.json 2>/dev/null | grep -o '"id"' | wc -l; }
alarms_count() { adb shell dumpsys alarm 2>/dev/null | grep -c 'com.android.deskclock'; }

lock_and_open() {
  adb shell input keyevent KEYCODE_SLEEP
  sleep 2
  adb shell input keyevent KEYCODE_WAKEUP
  sleep 3
  cortana_assist
  sleep 5
  if [ "$(has_node "$ROW_DIR/.lo.xml" cortana_session 2>/dev/null)" != yes ]; then
    dump_ui "$ROW_DIR/.lo.xml" || true
  fi
  if [ "$(has_node "$ROW_DIR/.lo.xml" cortana_session)" != yes ]; then
    adb shell cmd voiceinteraction show >/dev/null 2>&1
    sleep 5
  fi
}

adb shell locksettings set-pin $PIN >/dev/null 2>&1 && pin_set=yes
assert_eq "a PIN is set" "yes" "$pin_set"

# ---- the allowed commands run directly ----------------------------------------------------------
lock_and_open
dump_ui "$ROW_DIR/e10_locked.xml"
screencap "$ROW_DIR/e10_locked.png"
assert_eq "Cortana is open over the keyguard" "yes" "$(has_node "$ROW_DIR/e10_locked.xml" cortana_session)"

allowed() { # utterance expected-reply-substring
  "$HERE/speak.sh" "$1" 11 > /dev/null 2>&1
  assert_contains "locked: $1 replies directly" "$2" "$(reply_text)"
  assert_absent "locked: $1 shows no Unlock card" "cortana_card:unlock" \
    "$(dump_ui "$ROW_DIR/e10_$1.xml"; cat "$ROW_DIR/e10_$1.xml")"
}

allowed time_query "It's"
allowed date_query "Today is"
allowed weather "degrees"

alarms_before="$(alarms_count)"
"$HERE/speak.sh" alarm 11 > /dev/null 2>&1
assert_contains "locked: an alarm is set directly" "Alarm set for" "$(reply_text)"
adb shell dumpsys alarm > "$ROW_DIR/e10_alarm.txt" 2>/dev/null
assert_ne "and it reaches DeskClock with the keyguard still up" "$alarms_before" "$(alarms_count)"
adb shell dumpsys window > "$ROW_DIR/e10_keyguard_after_alarm.txt" 2>/dev/null
assert_contains "the keyguard never came down for it" "isKeyguardShowing=true" \
  "$(cat "$ROW_DIR/e10_keyguard_after_alarm.txt")"

"$HERE/speak.sh" play_music 11 > /dev/null 2>&1
adb shell dumpsys media_session > "$ROW_DIR/e10_media.txt" 2>/dev/null
note "media_session after 'play music': $(grep -c 'state=PlaybackState' "$ROW_DIR/e10_media.txt" 2>/dev/null)"

# ---- the gated commands change nothing ----------------------------------------------------------
sent_before="$(sms_sent_count)"; calls_before="$(calls_count)"
events_before="$(events_count)"; reminders_before="$(reminder_count)"
activity_before="$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity)"

gated() { # utterance
  "$HERE/speak.sh" "$1" 11 > /dev/null 2>&1
  dump_ui "$ROW_DIR/e10_gated_$1.xml"
  assert_eq "locked: $1 shows Unlock to continue (H12)" "yes" \
    "$(has_node "$ROW_DIR/e10_gated_$1.xml" "cortana_card:unlock")"
  assert_contains "locked: $1 was UNDERSTOOD, then refused" "$1" "$(diag match | tail -1)"
}

gated text_contact
gated call_contact
gated calendar_query
gated reminder_time
gated directions
gated take_photo
gated take_note
gated calendar_add
# Last, so its card is the one on screen for the unlock step.
"$HERE/speak.sh" open_clock 11 > /dev/null 2>&1
dump_ui "$ROW_DIR/e10_gated_open.xml"
screencap "$ROW_DIR/e10_gated_open.png"
assert_eq "locked: open an app shows Unlock to continue" "yes" \
  "$(has_node "$ROW_DIR/e10_gated_open.xml" "cortana_card:unlock")"

assert_eq "nothing was sent" "$sent_before" "$(sms_sent_count)"
assert_eq "nothing was dialled" "$calls_before" "$(calls_count)"
assert_eq "nothing was added to the calendar" "$events_before" "$(events_count)"
assert_eq "no reminder was stored" "$reminders_before" "$(reminder_count)"
assert_eq "and no app was opened" "$activity_before" \
  "$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity)"

# ---- the unlock runs ONLY the request on the card ------------------------------------------------
tap_node "$ROW_DIR/e10_gated_open.xml" "cortana_card_button:unlock"
sleep 3
adb shell dumpsys window > "$ROW_DIR/e10_bouncer.txt" 2>/dev/null
assert_contains "the Unlock button raises the bouncer" "isKeyguardShowing=true" "$(cat "$ROW_DIR/e10_bouncer.txt")"
adb shell input text $PIN
adb shell input keyevent KEYCODE_ENTER
sleep 6
screencap "$ROW_DIR/e10_after_unlock.png"
resumed="$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity)"
note "after unlock: $resumed"
assert_contains "the request that was on the card runs" "deskclock" "$(printf '%s' "$resumed" | tr 'A-Z' 'a-z')"

# Decisions: only the card on screen is pending, so none of the earlier gated requests may have run.
assert_eq "and none of the earlier gated requests ran: nothing sent" "$sent_before" "$(sms_sent_count)"
assert_eq "nothing dialled" "$calls_before" "$(calls_count)"
assert_eq "nothing added to the calendar" "$events_before" "$(events_count)"
assert_eq "no reminder stored" "$reminders_before" "$(reminder_count)"

row_end
