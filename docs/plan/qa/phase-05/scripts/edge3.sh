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

# ---- microphone busy: the keyboard listening, then Cortana asks ---------------------------------------
open_field field_text
kb_dump "$D"; tap_node "$D" kb_mic
wait_listening
ip="$(ime_pid)"
assert_eq "the keyboard holds the microphone" "$ip" "$(speech_status mic_owner_pid)"
cortana_assist; sleep 4
cortana_listen 0 || true
# Read the owner WHILE Cortana's capture runs: with no speech it endpoints on silence within seconds
# (run 1 read it 4 s later and found nobody holding the microphone).
for _ in $(seq 1 20); do [ "$(speech_status asr_listening)" = "true" ] && break; sleep 0.25; done
owner="$(speech_status mic_owner_pid)"
note "after Cortana asked: microphone owner pid $owner (keyboard $ip, launcher $(main_pid)); cortana: $(diag cortana | tail -3 | tr '\n' ' ' | cut -c1-240)"
note "keyboard: $(ime_log 'voice' | tail -3 | tr '\n' ' ' | cut -c1-300)"
speech_dump | grep -E "refused|listening for|released" | tail -4 > "$ROW_DIR/edge3_busy_speech.txt"
note "speech process: $(tr '\n' ' ' < "$ROW_DIR/edge3_busy_speech.txt")"
# One microphone, one owner, whichever way the platform orders it: either the keyboard still owns it
# and Cortana was REFUSED with the busy code (7), or the keyboard's window hid when Cortana's opened, the
# keyboard stopped listening and let go, and Cortana then got a free microphone. Never both, never a crash.
if [ "$owner" = "$ip" ]; then
  assert_contains "the keyboard kept the microphone and Cortana was refused with the busy code (7)" "speech error 7" "$(diag cortana | tail -20)"
else
  assert_contains "the keyboard let go when its window hid" "voice: unbound" "$(ime_log 'voice: unbound' | tail -1)"
  assert_eq "and Cortana then owns the microphone" "$(main_pid)" "$owner"
fi
cortana_close
assert_ne "no crash: the launcher process is alive" "" "$(main_pid)"
assert_ne "no crash: a keyboard process is alive" "" "$(ime_pid)"
wait_idle || true

# ---- microphone busy the other way: Cortana listening, then the keyboard asks -------------------------
open_field field_text
cortana_assist; sleep 4
cortana_listen 2 || true
wait_listening
mp="$(main_pid)"
note "Cortana listening: owner pid $(speech_status mic_owner_pid), launcher pid $mp"
assert_eq "Cortana holds the microphone" "$mp" "$(speech_status mic_owner_pid)"
# The keyboard's voice key from inside Cortana's own text box, while Cortana still listens.
C="$ROW_DIR/.edge3_c.xml"; dump_ui "$C"
tap_node "$C" cortana_text_box_field 2>/dev/null; sleep 1.5
if kb_dump "$D" && [ "$(has_node "$D" kb_mic)" = yes ]; then
  tap_node "$D" kb_mic; sleep 1.5
  n="$(notice)"
  note "the keyboard's strip: $n (owner now $(speech_status mic_owner_pid))"
  if [ "$(speech_status mic_owner_pid)" = "$mp" ]; then
    assert_contains "the keyboard is refused with a notice" "using the microphone" "$n"
  else
    note "focusing the text box ended Cortana's listening first, so the keyboard got the free microphone"
    assert_eq "no crash either way: the keyboard's process is alive" "yes" "$([ -n "$(ime_pid)" ] && echo yes || echo no)"
  fi
else
  note "Cortana's text box did not bring up the keyboard while listening; the refusal side is carried by the first direction"
fi
cortana_close
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
assert_eq "during a call: the keyboard either types or explains, and never crashes" "yes" "$([ -n "$(ime_pid)" ] && { [ "$t" != "[]" ] || [ -n "$n" ]; } && echo yes || echo no)"
adb emu gsm cancel 5550100 >/dev/null; sleep 2
note "call ended: $(adb shell dumpsys telephony.registry | grep -m1 -o 'mCallState=[0-9]')"

kb_end
row_end
