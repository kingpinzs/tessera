#!/usr/bin/env bash
# E2 — every command in the ruled list, spoken, with its real effect and its spoken reply.
#
#   "With `adb shell svc wifi disable` and `svc data disable`, each command in the list produces its
#    real effect per the observable table below and a spoken reply that passes the spoken reply pass
#    rule in Decisions"
#
# The network is off for the whole row: that is what makes it an OFFLINE row, not just a command row.
# Weather is asked LAST and deliberately reads stored data, because A11 gives the Weather part its own
# internet use and Cortana has none.
#
# The spoken-reply pass rule (Decisions, review T-m8): the reply text in the diagnostics dump equals
# the expected string, and the capture over the reply window has an RMS above -40 dBFS. No ASR grading.
. "$(dirname "$0")/lib.sh"

row_begin E2 "the ruled command list, spoken, offline"

WIFI_WAS="$(adb shell settings get global wifi_on | tr -d '\r')"
restore() {
  adb shell svc wifi enable >/dev/null 2>&1
  adb shell svc data enable >/dev/null 2>&1
  note "restored: wifi and data enabled (wifi_on was $WIFI_WAS)"
}
trap restore EXIT

adb shell svc wifi disable
adb shell svc data disable
sleep 3
note "network: wifi_on=$(adb shell settings get global wifi_on | tr -d '\r')"

# One command: open Cortana, speak it, capture the reply audio, assert the transcript, the spoken
# reply and the real effect.
command_row() { # utterance expected-request expected-reply-substring
  local utterance="$1" expected_request="$2" expected_reply="$3"
  ensure_start
  cortana_assist
  sleep 4
  # The reply is captured from the AVD's own output while Cortana speaks it.
  ( "$HERE/audio.sh" record "$ROW_DIR/${utterance}_reply.wav" 14 >/dev/null 2>&1 ) &
  local recorder=$!
  local final
  final="$("$HERE/speak.sh" "$utterance" 11)"
  wait $recorder 2>/dev/null
  local matched reply rms
  matched="$(diag match | tail -1 | sed 's/.*\[match\] //')"
  reply="$(reply_text)"
  rms="$("$HERE/audio.sh" rms "$ROW_DIR/${utterance}_reply.wav" 2>/dev/null)"
  note "$utterance final: $final"
  note "$utterance match: $matched"
  assert_contains "$utterance -> $expected_request" "$expected_request" "$matched"
  assert_contains "$utterance reply text" "$expected_reply" "$reply"
  # The pass rule's second half: the reply was really spoken, not just recorded as text.
  python3 - "$rms" <<'PY'
import sys
try:
    sys.exit(0 if float(sys.argv[1]) > -40 else 1)
except ValueError:
    sys.exit(1)
PY
  if [ $? -eq 0 ]; then
    _verdict PASS "$utterance reply was audible" "RMS $rms dBFS > -40"
  else
    _verdict FAIL "$utterance reply was audible" "RMS $rms dBFS is not above -40"
  fi
  screencap "$ROW_DIR/${utterance}.png"
}

# ---- the per-command observable table ----------------------------------------------------------

command_row open_clock "OpenApp" "Opening"
assert_contains "open an app: the activity resumed" "com.android.deskclock" \
  "$(adb shell dumpsys activity activities | grep -m1 'ResumedActivity' )"

command_row alarm "SetAlarm" "Alarm set for"
adb shell dumpsys alarm > "$ROW_DIR/alarm_dumpsys.txt" 2>/dev/null
assert_contains "alarm: DeskClock holds a pending alarm" "com.android.deskclock" "$(cat "$ROW_DIR/alarm_dumpsys.txt")"

command_row timer "SetTimer" "Timer set for"

command_row time_query "TimeQuery" "It's"
command_row date_query "DateQuery" "Today is"

command_row take_photo "TakePhoto" "camera"
assert_contains "take a photo: the Camera slot app resumed" "camera" \
  "$(adb shell dumpsys activity activities | grep -m1 'ResumedActivity' | tr 'A-Z' 'a-z')"

command_row take_note "TakeNote" "notes"
command_row play_music "PlayMusic" "Playing"
command_row directions "Directions" "directions"

command_row calendar_add "AddCalendarEvent" "Add this to your calendar?"
# The card is a confirmation: nothing is inserted until it is confirmed.
before_events="$(adb shell content query --uri content://com.android.calendar/events --projection title 2>/dev/null | wc -l)"
"$HERE/speak.sh" yes 10 > /dev/null
after_events="$(adb shell content query --uri content://com.android.calendar/events --projection title 2>/dev/null | wc -l)"
assert_ne "add a calendar event: the provider gained a row after confirm" "$before_events" "$after_events"
adb shell content query --uri content://com.android.calendar/events --projection title > "$ROW_DIR/calendar_events.txt" 2>&1

command_row calendar_query "WhatsOnMyCalendar" "calendar"

# Weather is last: it reads the Weather app's stored data, with the network still off.
command_row weather "Weather" "degrees"

# The whole row ran with no network.
assert_eq "the network stayed off for the whole row" "0" "$(adb shell settings get global wifi_on | tr -d '\r')"

row_end
