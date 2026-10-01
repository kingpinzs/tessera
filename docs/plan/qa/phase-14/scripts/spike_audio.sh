#!/usr/bin/env bash
# Phase 14 build start: the audio spike (Acceptance criteria, "Audio spike"; Jeremy's Q-R3-1a (a), 2026-09-29).
#
# Proves the emulator's own gRPC audio route before any voice row relies on it:
#   (1) emu_audio.py takes its endpoint only from the discovery file whose port.serial=5554, and refuses any other
#       device (emulator-5556 is another project's AVD: the refusal happens before any connection is made)
#   (2) getMicrophoneState reads host microphone access off (injectAudio fails FAILED_PRECONDITION otherwise)
#   (3) with Tess listening, injectAudio of pod_bay_doors -> a new `[speech] asr: final` holding "pod bay",
#       with the C-30 voice verdict (heard=true, no "no speech in the capture")
#   (4) a spoken reply captured through streamAudio has an RMS above -40 dBFS (phase 03's spoken-reply pass rule)
# The host's audio is never touched: no audio.sh setup / say / record, no pactl / paplay / parecord, no hostmicon.
# The spike runs on the build installed when it runs; it tests the route, not the pod bay (the reply heard is
# whatever that build answers to the words).
set -uo pipefail
export ANDROID_SERIAL=emulator-5554
export AUDIO_ROUTE=emu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
P03="$(cd "$HERE/../../phase-03/scripts" && pwd)"
. "$HERE/lib.sh"

row_begin SPIKE-audio "the emulator's own gRPC audio route (injectAudio / streamAudio)"
require_emu_audio || { row_end; exit 1; }

# ---- (1) the endpoint -----------------------------------------------------------------------------------------
ep="$(python3 "$P03/emu_audio.py" endpoint 2>&1)"; ep_rc=$?
note "emu_audio.py endpoint (rc $ep_rc):"; printf '%s\n' "$ep" | sed 's/^/        /' >> "$LOG"
assert_eq "(1) endpoint resolves" "0" "$ep_rc"
assert_contains "(1) the discovery file is tileshell_fhd's" "avd.name=tileshell_fhd port.serial=5554" "$ep"
disc="$(printf '%s\n' "$ep" | sed -n 's/^discovery //p')"
assert_eq "(1) that file really says port.serial=5554" "port.serial=5554" "$(grep -x 'port.serial=5554' "$disc")"
assert_eq "(1) its grpc.port is the one the client uses" \
  "grpc.port=$(grep '^grpc.port=' "$disc" | cut -d= -f2)" "$(printf '%s\n' "$ep" | grep -o 'grpc.port=[0-9]*')"
refuse="$(ANDROID_SERIAL=emulator-5556 python3 "$P03/emu_audio.py" endpoint 2>&1)"; refuse_rc=$?
note "ANDROID_SERIAL=emulator-5556 emu_audio.py endpoint (rc $refuse_rc): $refuse"
assert_eq "(1) another device is refused before any connection" "2" "$refuse_rc"
assert_contains "(1) and says why" "only ever talks to emulator-5554" "$refuse"

# ---- (2) host microphone access -------------------------------------------------------------------------------
mic="$(python3 "$P03/emu_audio.py" mic-state 2>&1)"; mic_rc=$?
note "emu_audio.py mic-state (rc $mic_rc): $mic"
assert_eq "(2) getMicrophoneState answers" "0" "$mic_rc"
assert_eq "(2) host microphone access is off" "realAudioEnabled=false" "$mic"

# ---- (3) + (4) inject an utterance while Tess listens; capture the reply ------------------------------------------
# The AVD's media volume (which Tess's USAGE_ASSISTANT voice follows) is whatever the last row left: phase 03's j5.sh
# sets it to 0 and never restores it. A reply played at volume 0 is captured faithfully as silence (spike run 2), so
# the audible step sets it to full scale and restores it after — phase 15's e9.sh / e26.sh form (RV12). The emulator runs on
# the `none` audio backend, so nothing reaches the desktop's speakers at any volume.
vol_get() { adb shell cmd media_session volume --stream "$1" --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+'; }
MEDIA_VOL0="$(vol_get 3)"
note "media volume before: ${MEDIA_VOL0:-?}; assistant stream before: $(vol_get 11)"
# Full scale (15): at 10 the 28-s window read -42.97 dBFS with 5.8 s of speech in it (run 4); the check tells spoken
# from silent (volume 0 read -116), so the device plays its reply at its own full volume.
adb shell cmd audio set-volume 3 15 >/dev/null 2>&1   # media_session volume --set is a silent no-op on this image (spike run 3)
note "media volume set: $(vol_get 3); assistant stream: $(vol_get 11)"
ensure_start
cortana_assist
sleep 4
dump_ui "$ROW_DIR/tess-open.xml"
assert_eq "Tess is open" "yes" "$(has_node "$ROW_DIR/tess-open.xml" cortana_session)"

MARK="$(ring_mark)"
( python3 "$P03/emu_audio.py" record "$ROW_DIR/reply.wav" 28 > "$ROW_DIR/record.out" 2>&1; echo "rc=$?" >> "$ROW_DIR/record.out" ) &
recorder=$!
final="$("$P03/speak.sh" pod_bay_doors 11 2> "$ROW_DIR/speak.err")"; speak_rc=$?
wait "$recorder"
note "speak.sh pod_bay_doors (rc $speak_rc) stdout: $final"
note "speak.sh stderr: $(tr '\n' ' ' < "$ROW_DIR/speak.err")"
note "record: $(tr '\n' ' ' < "$ROW_DIR/record.out")"
assert_eq "(3) speak.sh exit status" "0" "$speak_rc"
assert_contains "(3) the injection went through injectAudio" "injected pod_bay_doors.wav" "$(cat "$ROW_DIR/speak.err")"

speech="$(ring_since "$MARK" speech)"
printf '%s\n' "$speech" > "$ROW_DIR/speech-slice.txt"
finals="$(printf '%s\n' "$speech" | grep -F '[speech] asr: final')"
note "asr finals since MARK: $finals"
assert_contains "(3) a new asr: final since the MARK" "[speech] asr: final" "$finals"
assert_contains "(3) holding \"pod bay\"" "pod bay" "$(printf '%s' "$finals" | tr '[:upper:]' '[:lower:]')"
# C-30: the capture was heard as speech, not dropped by the gate.
assert_contains "(3) C-30 levels line heard=true" "(heard=true)" "$(printf '%s\n' "$speech" | grep -F 'asr: levels')"
assert_absent "(3) C-30 no 'no speech in the capture'" "asr: no speech in the capture" "$speech"

reply="$(reply_since "$MARK")"
note "reply_since MARK: $reply"
assert_ne "(4) Tess replied (reply text since the MARK)" "" "$reply"
assert_contains "(4) streamAudio capture finished" "rc=0" "$(cat "$ROW_DIR/record.out")"
rms="$("$P03/audio.sh" rms "$ROW_DIR/reply.wav" 2>/dev/null)"
note "reply window RMS: $rms dBFS (audio.sh rms: a file computation, no host audio)"
if python3 -c 'import sys; sys.exit(0 if float(sys.argv[1]) > -40 else 1)' "$rms" 2>/dev/null; then
  _verdict PASS "(4) the reply was audible through streamAudio" "RMS $rms dBFS > -40"
else
  _verdict FAIL "(4) the reply was audible through streamAudio" "RMS $rms dBFS is not above -40"
fi

screencap "$ROW_DIR/after.png"
cortana_close
ensure_start
[ -n "${MEDIA_VOL0:-}" ] && adb shell cmd audio set-volume 3 "$MEDIA_VOL0" >/dev/null 2>&1
assert_eq "restore: the media volume is back" "${MEDIA_VOL0:-?}" "$(vol_get 3)"
RINGS="launcher speech" row_end
