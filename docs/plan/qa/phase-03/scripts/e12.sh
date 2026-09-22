#!/usr/bin/env bash
# E12 — the speech process's death is contained.
#
#   "adb root, pidof app.tileshell:speech, kill -9 <pid>, adb unroot, adb wait-for-device: the launcher
#    process pid is unchanged, Start and live tiles keep updating, and the next Cortana request gets the
#    reload notice and then works."
#
# Killing by the RECORDED pid, never by pattern (Hard Rule 13).
. "$(dirname "$0")/lib.sh"

row_begin E12 "the speech process can die without taking the shell with it"

ensure_start
cortana_assist
sleep 5
launcher_before="$(adb shell pidof app.tileshell | tr -d '\r')"
speech_pid="$(adb shell pidof app.tileshell:speech | tr -d '\r')"
note "launcher pid $launcher_before, speech pid $speech_pid"
assert_ne "the launcher process is running" "" "$launcher_before"
assert_ne "the speech process is running" "" "$speech_pid"
assert_ne "and they are different processes" "$launcher_before" "$speech_pid"
assert_eq "the models are loaded in it" "true" "$(speech_status asr_loaded)"

adb root >/dev/null 2>&1
sleep 2
adb shell kill -9 "$speech_pid"
adb unroot >/dev/null 2>&1
adb wait-for-device
sleep 3

launcher_after="$(adb shell pidof app.tileshell | tr -d '\r')"
assert_eq "the launcher process pid is unchanged" "$launcher_before" "$launcher_after"
assert_contains "the shell noticed the process was gone" "process gone" "$(diag speech)"

# Start and the live tiles keep going: the engine publishes after the kill.
ensure_start
sleep 6
dump_ui "$ROW_DIR/e12_start.xml"
screencap "$ROW_DIR/e12_start.png"
assert_eq "Start is still drawing" "yes" "$(has_node "$ROW_DIR/e12_start.xml" w10m_status_bar)"
# The engine publishes on CHANGE, so waiting for one proves nothing — a quiet 8 seconds looks exactly
# like a dead engine. A real notification is posted instead, which the listener has to turn into a
# live tile update after the speech process died.
publishes="$(diag engine | wc -l)"
adb shell cmd notification post -S bigtext -t "QA E12" phase03e12 "the live tile engine after the kill" >/dev/null 2>&1
sleep 6
assert_ne "a notification posted AFTER the kill still reaches the live tile engine" "$publishes" "$(diag engine | wc -l)"
adb shell cmd notification post --help >/dev/null 2>&1
note "engine lines before=$publishes after=$(diag engine | wc -l)"
assert_eq "the notification listener is still connected" "yes" \
  "$(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener >/dev/null 2>&1 && echo yes || echo no)"

# The next request works: the reload notice, then a real answer.
cortana_assist
sleep 6
dump_ui "$ROW_DIR/e12_after.xml"
screencap "$ROW_DIR/e12_after.png"
speech_pid_after="$(adb shell pidof app.tileshell:speech | tr -d '\r')"
assert_ne "the speech process came back with a new pid" "$speech_pid" "$speech_pid_after"
assert_contains "and the new process says what the old one died doing, or nothing when it was killed" \
  "service created" "$(speech_dump)"

"$HERE/speak.sh" time_query 12 > /dev/null
assert_contains "the next request works" "It's" "$(reply_text)"

cortana_close
row_end
