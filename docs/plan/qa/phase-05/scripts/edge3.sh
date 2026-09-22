#!/usr/bin/env bash
# EDGE3 — the phase doc's voice-typing edge cases.
#
#   * microphone permission denied, or in use by a call, during voice typing
#   * voice typing invoked while Cortana is listening (one engine, one microphone: the second caller
#     waits or is refused with a notice, never a crash)
#   * the IME's voice key while another app holds the assistant role (the engine is ours, the role
#     isn't required)
#
# The busy case is the speech process's new arbitration (one microphone owner, the second client
# refused with SpeechError.MICROPHONE_BUSY): asserted from BOTH sides — the owner the speech process
# reports, the refusal Cortana records, and both processes still alive afterwards.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin EDGE3 "voice typing: permission denied, microphone busy, assistant role elsewhere, a call"
kb_begin
WAV="$REPO/docs/plan/qa/phase-03/utterances/time_query.wav"
"$HERE/audio.sh" setup >> "$LOG" 2>&1
D="$ROW_DIR/edge3.xml"
ime_pid() { adb shell pidof app.tileshell:ime | tr -d '\r'; }
main_pid() { adb shell pidof app.tileshell | tr -d '\r'; }
wait_listening() { for _ in $(seq 1 60); do [ "$(speech_status asr_listening)" = "true" ] && return 0; sleep 0.5; done; return 1; }
wait_idle() { for _ in $(seq 1 60); do [ "$(speech_status asr_listening)" = "false" ] && return 0; sleep 0.5; done; return 1; }
notice() { kb_dump "$D"; node_text "$D" kb_notice; grep -o '<node[^>]*resource-id="kb_notice"[^>]*>' "$D" | grep -o 'text="[^"]*"' | head -1; }

# ---- microphone permission denied -------------------------------------------------------------------
adb shell pm revoke app.tileshell android.permission.RECORD_AUDIO
sleep 2
open_field field_text
kb_dump "$D"; tap_node "$D" kb_mic; sleep 2.5
top="$(adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | tr -d '\r')"
log "after the voice key without the permission, the top activity is: $top"
assert_contains "permission denied: Android's own grant prompt is raised" "permissioncontroller" "$top"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
assert_contains "the keyboard recorded the refusal" "voice refused: no RECORD_AUDIO" "$(ime_log 'voice refused' | tail -1)"
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO
sleep 1

# ---- one microphone, two clients: refused, both ways (review M5) --------------------------------------
# The second client is the shell's OWN recognition service (in the launcher process), which Android starts
# when an app asks its default speech recogniser — here the fixture, on a broadcast, so the fixture's screen
# and the keyboard showing over it stay exactly as they are. No either/or branches: each direction must
# show the refusal.
adb shell pm grant "$FIX" android.permission.RECORD_AUDIO
note "default recogniser: $(adb shell settings get secure voice_recognition_service | tr -d '\r')"
recog() { read_mirror recog; }

# (a) the keyboard listening; the fixture's recogniser asks -> refused (ERROR_RECOGNIZER_BUSY = 8).
open_field field_text
kb_dump "$D"; tap_node "$D" kb_mic
wait_listening
ip="$(ime_pid)"
assert_eq "(a) the keyboard holds the microphone" "$ip" "$(speech_status mic_owner_pid)"
adb shell am broadcast -a "$FIX.RECOGNIZE" -p "$FIX" >/dev/null
for _ in $(seq 1 20); do r="$(recog)"; case "$r" in error:*|results:*) break ;; esac; sleep 0.5; done
note "(a) the fixture's recogniser answered: $r; speech: $(speech_dump | grep -E 'refused' | tail -1 | tr -s ' ' | cut -c1-200)"
assert_eq "(a) the second client is refused as busy (SpeechRecognizer.ERROR_RECOGNIZER_BUSY)" "error:8" "$r"
assert_eq "(a) and the keyboard still holds the microphone" "$ip" "$(speech_status mic_owner_pid)"
assert_contains "(a) the speech process recorded the refusal" "refused" "$(speech_dump | grep -E 'refused' | tail -1)"
assert_eq "(a) no crash: the keyboard's process is the same one" "$ip" "$(ime_pid)"
kb_dump "$D"; tap_node "$D" kb_mic   # the keyboard stops (tap the voice key again)
wait_idle || true

# (b) the fixture's recogniser listening; the keyboard's voice key asks -> refused with the notice.
open_field field_text
adb shell am broadcast -a "$FIX.RECOGNIZE" -p "$FIX" >/dev/null
for _ in $(seq 1 20); do [ "$(recog)" = "listening" ] && break; sleep 0.25; done
mp="$(main_pid)"
note "(b) fixture recogniser: $(recog); microphone owner pid $(speech_status mic_owner_pid) (launcher $mp)"
assert_eq "(b) the recognition service (launcher process) holds the microphone" "$mp" "$(speech_status mic_owner_pid)"
kb_dump "$D"; tap_node "$D" kb_mic; sleep 1.2
n="$(notice)"
note "(b) the keyboard's strip: $n"
assert_contains "(b) the keyboard is refused with a notice, not a crash" "is using the microphone" "$n"
assert_eq "(b) and the recogniser still holds the microphone" "$mp" "$(speech_status mic_owner_pid)"
assert_contains "(b) the keyboard recorded the busy error (code 7)" "voice error 7" "$(ime_log 'voice error' | tail -1)"
wait_idle || true

# (c) Cortana itself: the keyboard's window hides when Cortana's opens, so the keyboard lets go first and
# Cortana gets a free microphone — the one ordering a user can produce with the two on screen.
open_field field_text
kb_dump "$D"; tap_node "$D" kb_mic
wait_listening
ip="$(ime_pid)"
cortana_assist; sleep 4
cortana_listen 0 || true
for _ in $(seq 1 20); do [ "$(speech_status asr_listening)" = "true" ] && break; sleep 0.25; done
note "(c) after Cortana asked: owner $(speech_status mic_owner_pid) (launcher $(main_pid), keyboard $ip); keyboard: $(ime_log 'voice' | tail -2 | tr '\n' ' ' | cut -c1-200)"
assert_contains "(c) the keyboard let go when its window hid behind Cortana's" "voice: unbound" "$(ime_log 'voice: unbound' | tail -1)"
assert_eq "(c) and Cortana then holds the microphone" "$(main_pid)" "$(speech_status mic_owner_pid)"
cortana_close
assert_ne "(c) no crash: the launcher process is alive" "" "$(main_pid)"
wait_idle || true

# ---- another app holds the assistant role -------------------------------------------------------------
holders="$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
adb shell cmd role remove-role-holder android.app.role.ASSISTANT app.tileshell
sleep 2
note "assistant role holders now: [$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')]"
assert_ne "the shell no longer holds the assistant role" "app.tileshell" "$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
open_field field_text
kb_dump "$D"; tap_node "$D" kb_mic
wait_listening
"$HERE/audio.sh" say "$WAV" >> "$LOG" 2>&1
sleep 8
assert_contains "voice typing works without the assistant role" "hat time is it" "$(read_mirror text | tr 'A-Z' 'a-z')"
[ -n "$holders" ] && adb shell cmd role add-role-holder android.app.role.ASSISTANT "$holders"
sleep 1
assert_eq "restored: the assistant role holder" "$holders" "$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"

# ---- in use by a call ---------------------------------------------------------------------------------
adb emu gsm call 5550100 >/dev/null; sleep 3
adb emu gsm accept 5550100 >/dev/null; sleep 3
note "call state: $(adb shell dumpsys telephony.registry | grep -m1 -o 'mCallState=[0-9]')"
open_field field_text
kb_dump "$D"; tap_node "$D" kb_mic; sleep 1
"$HERE/audio.sh" say "$WAV" >> "$LOG" 2>&1
sleep 9
t="$(read_mirror text)"; n="$(notice)"
log "during a call: field $t; strip notice [$n]"
# Review m6: the emulator's call does not take the microphone, so this proves only that voice typing during
# a call does not crash; whether a real call blocks the microphone is the phone's (P1).
assert_eq "during a call: no crash (the microphone under a real call is a phone row)" "yes" "$([ -n "$(ime_pid)" ] && { [ "$t" != "[]" ] || [ -n "$n" ]; } && echo yes || echo no)"
adb emu gsm cancel 5550100 >/dev/null; sleep 2
note "call ended: $(adb shell dumpsys telephony.registry | grep -m1 -o 'mCallState=[0-9]')"

kb_end
row_end
