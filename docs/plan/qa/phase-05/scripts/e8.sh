#!/usr/bin/env bash
# E8 — voice typing works with the network off.
#
#   "Voice typing with the network off (phase 01 E9's command and dumpsys connectivity proof; restore:
#    adb shell cmd connectivity airplane-mode disable) inserts the spoken text (phone-only if the
#    emulator audio route can't drive it)"
#
# The route is phase 03's: real speech (the shipped Kokoro voice, synthesised on the host) played into
# a PulseAudio null sink that is the AVD's microphone. The keyboard's voice key binds phase 03's
# app.tileshell:speech process — so this is also the proof that the keyboard is a second, working
# client of that process, running in its own :ime process.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E8 "voice typing, network off, inserts the spoken text"
kb_begin
WAV="$REPO/docs/plan/qa/phase-03/utterances/time_query.wav"
[ -f "$WAV" ] || { _verdict FAIL "the utterance exists" "$WAV missing"; kb_end; row_end; exit 1; }
"$HERE/audio.sh" setup >> "$LOG" 2>&1

adb shell cmd connectivity airplane-mode enable
sleep 4
conn="$(adb shell dumpsys connectivity | grep -iE "^Active default network|Active default network" | head -1 | tr -d '\r')"
log "dumpsys connectivity: $conn"
assert_contains "the network is off (no active default network)" "none" "$conn"

open_field field_text
D="$ROW_DIR/e8.xml"; kb_dump "$D"
assert_eq "the voice key leads the strip" "yes" "$(has_node "$D" kb_mic)"
tap_node "$D" kb_mic
# Play the moment the recogniser really listens: the speech process's own status says so.
for _ in $(seq 1 60); do
  [ "$(speech_status asr_listening)" = "true" ] && break
  sleep 0.5
done
note "speech process: asr_listening=$(speech_status asr_listening) mic_owner_pid=$(speech_status mic_owner_pid) clients=$(speech_status clients)"
ime_pid="$(adb shell pidof app.tileshell:ime | tr -d '\r')"
assert_eq "the microphone is listening for the keyboard's own process" "$ime_pid" "$(speech_status mic_owner_pid)"
"$HERE/audio.sh" say "$WAV" >> "$LOG" 2>&1
sleep 8
got="$(read_mirror text)"
log "field after speaking \"What time is it?\": $got"
assert_contains "the spoken text went into the field" "hat time is it" "$(echo "$got" | tr 'A-Z' 'a-z')"
assert_contains "the keyboard recorded the final transcript" "voice final" "$(ime_log 'voice final' | tail -1)"
screencap "$ROW_DIR/e8_after.png"

adb shell cmd connectivity airplane-mode disable
sleep 3
note "restored: $(adb shell dumpsys connectivity | grep -iE 'Active default network' | head -1 | tr -d '\r')"
kb_end
row_end
