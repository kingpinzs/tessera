#!/usr/bin/env bash
# The phase doc's Edge cases, in the order it lists them.
#
# Hard Rule 5: edge cases are part of the spec and QA executes them. The ones that can be driven
# deterministically are here; each one that CANNOT be driven on an emulator says so and names where it
# goes instead, rather than being quietly dropped.
. "$(dirname "$0")/lib.sh"

row_begin EDGE "the edge cases"

reminder_count() { adb shell run-as app.tileshell cat /data/data/app.tileshell/files/cortana_reminders.json 2>/dev/null | grep -o '"id"' | wc -l; }
alarm_pendings() { adb shell dumpsys alarm 2>/dev/null | grep -c 'tileshell://reminder'; }

open_cortana() { ensure_start; cortana_assist; sleep 4; }

# ---- speech: silence, a very long utterance -----------------------------------------------------
open_cortana
"$HERE/speak.sh" silence 11 > /dev/null 2>&1 || note "silence produced no final at all, which is itself the answer"
silence_reply="$(reply_text)"
note "silence reply: $silence_reply"
assert_contains "silence is answered, not ignored" "didn't catch that" "$silence_reply"
assert_absent "and silence does NOT reach the not-understood handler as text" \
  "transcript=\"\"" "$(diag not_understood | tail -1)"

open_cortana
"$HERE/speak.sh" long 16 > /dev/null 2>&1
long_final="$(speech_dump | grep -F '[speech] asr: final' | tail -1)"
note "long utterance: $long_final"
assert_contains "a very long utterance still produces a final" "audioMs=" "$long_final"

# ---- an app that is not installed, and a contact that does not exist -----------------------------
open_cortana
"$HERE/speak.sh" unknown_app 12 > /dev/null 2>&1
assert_contains "a command naming an app that is not installed" "don't see an app" "$(reply_text)"

open_cortana
"$HERE/speak.sh" unknown_contact 12 > /dev/null 2>&1
unknown="$(reply_text)"
note "unknown contact reply: $unknown"
assert_contains "a contact that does not exist" "couldn't find" "$unknown"

# ---- the microphone permission, revoked and restored ----------------------------------------------
adb shell pm revoke app.tileshell android.permission.RECORD_AUDIO
adb shell am force-stop app.tileshell
sleep 3
open_cortana
dump_ui "$ROW_DIR/edge_nomic.xml"
screencap "$ROW_DIR/edge_nomic.png"
"$HERE/speak.sh" time_query 10 > /dev/null 2>&1 || true
nomic="$(reply_text)"
note "with RECORD_AUDIO revoked: $nomic"
assert_contains "a revoked microphone is reported, not silently dead" "microphone" "$nomic"
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO
adb shell am force-stop app.tileshell
sleep 3

# ---- the assistant role taken by another app ------------------------------------------------------
role_was="$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
adb shell cmd role remove-role-holder android.app.role.ASSISTANT "$role_was" >/dev/null 2>&1
sleep 3
ensure_start
dump_ui "$ROW_DIR/edge_norole_start.xml"
tap_node "$ROW_DIR/edge_norole_start.xml" nav_search
sleep 5
dump_ui "$ROW_DIR/edge_norole.xml"
screencap "$ROW_DIR/edge_norole.png"
assert_eq "with the role gone, the Search key opens the role notice (H30)" "yes" \
  "$(has_node "$ROW_DIR/edge_norole.xml" "cortana_card:role_notice")"
assert_eq "and NOT a session" "no" "$(has_node "$ROW_DIR/edge_norole.xml" cortana_session)"
adb shell cmd role add-role-holder android.app.role.ASSISTANT app.tileshell >/dev/null 2>&1
sleep 3
assert_eq "the role is restored" "app.tileshell" \
  "$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"

# ---- espeak-ng data corrupted, then re-extracted ---------------------------------------------------
stamp="$(adb shell run-as app.tileshell sh -c 'ls /data/data/app.tileshell/files/speech/*/ 2>/dev/null' | tr -d '\r')"
note "speech storage before: $stamp"
adb shell run-as app.tileshell sh -c 'rm -rf /data/data/app.tileshell/files/speech/*/espeak-ng-data' >/dev/null 2>&1
adb shell am force-stop app.tileshell
sleep 3
open_cortana
sleep 8
assert_eq "a deleted espeak-ng-data is re-extracted, and the engine still loads" "true" "$(speech_status espeak_ok)"
assert_eq "and TTS comes back with it" "true" "$(speech_status tts_loaded)"

# ---- a reminder due while the shell is force-stopped, and under Doze --------------------------------
open_cortana
"$HERE/speak.sh" reminder_time 12 > /dev/null 2>&1
"$HERE/speak.sh" yes 12 > /dev/null 2>&1
armed="$(alarm_pendings)"
assert_ne "a reminder arms an alarm" "0" "$armed"
adb shell am force-stop app.tileshell
sleep 3
assert_eq "a force-stop cancels it" "0" "$(alarm_pendings)"
ensure_start
sleep 6
assert_ne "and the process start re-arms it" "0" "$(alarm_pendings)"

# Doze: the reminder still has to fire (the per-command observable table).
adb shell dumpsys deviceidle enable deep >/dev/null 2>&1
adb shell dumpsys battery unplug >/dev/null 2>&1
adb shell input keyevent KEYCODE_SLEEP
adb shell dumpsys deviceidle force-idle >/dev/null 2>&1
sleep 5
adb shell dumpsys alarm > "$ROW_DIR/edge_doze_alarm.txt" 2>/dev/null
assert_contains "under Doze the alarm is still an allow-while-idle one" "tileshell://reminder" \
  "$(cat "$ROW_DIR/edge_doze_alarm.txt")"
adb shell dumpsys deviceidle unforce >/dev/null 2>&1
adb shell dumpsys deviceidle disable deep >/dev/null 2>&1
adb shell dumpsys battery reset >/dev/null 2>&1
adb shell input keyevent KEYCODE_WAKEUP
sleep 3

# ---- a reminder time in the past ------------------------------------------------------------------
before="$(reminder_count)"
open_cortana
# Typed, because the utterance set has no "in the past" recording and the text box runs the same path.
type_request "remind me to check the oven at 1 am" 10
"$HERE/speak.sh" yes 12 > /dev/null 2>&1 || true
note "reminders after a past-time request: $before -> $(reminder_count)"
assert_contains "a reminder time in the past is armed to fire, not dropped" "rearm" "$(diag reminders | tail -3)"

# ---- the network fully off, at all times -----------------------------------------------------------
adb shell svc wifi disable; adb shell svc data disable
sleep 3
open_cortana
"$HERE/speak.sh" time_query 12 > /dev/null 2>&1
assert_contains "Cortana answers with no network at all" "It's" "$(reply_text)"
assert_eq "and the network really was off" "0" "$(adb shell settings get global wifi_on | tr -d '\r')"
adb shell svc wifi enable; adb shell svc data enable

# ---- what cannot be driven here --------------------------------------------------------------------
note "NOT driven on this AVD, and where each goes instead:"
note "  background noise, speech interrupted mid-utterance: a real microphone, phone row P1"
note "  incoming call while listening: needs a second number, phone row P8"
note "  screen turns off mid-command: the AVD's sleep also stops the audio route"
note "  Device care optimise, 24 h idle liveness (N-01): One UI only, phone"
note "  lockdown mode: not on the AOSP image"
note "  GPS drift at the radius edge with a real fix: phone row P7"

row_end
