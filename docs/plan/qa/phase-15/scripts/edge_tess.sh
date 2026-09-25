#!/usr/bin/env bash
# EDGE_TESS — the Tess arithmetic edge cases (phase doc "Edge cases", the "Tess arithmetic (T15-2)" bullet) that E26 does
# not already prove. E26 proves "point five times four" (tess-008), "negative three times four" (tess-009) and an
# overflow (tess-010, "Overflow."); they are cited in the log, not repeated. This row:
#   1. a 1,000-digit result spoken in the engine's e-notation (H17): "what is 9 to the power of 1047" (9^1047 has exactly
#      1,000 digits), typed and spoken. (Base 9, not 2^3321: in the trial the recogniser heard the prompt voice's "two
#      to the power of" as "TRUE TO THE POWER OF" — a recognition miss, not the arithmetic under test.)
#   2. a unit the Converter lacks: "5 parsecs in miles" is not understood and logs no `[calc] tess` line;
#   3. a conversion across categories: "5 miles in kilograms" is not understood; with a control — the same bare form
#      with two Length units ("5 miles in kilometers") IS converted, so 2 and 3 cannot pass merely because a bare
#      conversion never matches;
#   4. an arithmetic request while the Calculator shows another value: Tess answers over the Calculator and the app's
#      calc_display and calc_expr read the same before and after, its history store unchanged.
# Every expectation is the host oracle's (gen_calc_cases.tess_row / run_keys), never the app's. Each step takes its own
# MARK (C-20) and saves its ring slice as ring_<what>_<ring>.txt right after the action (E22 reads them). Launcher and
# speech rings. Restore: the Calculator's display (as found) and history (same sha), Tess idle, Home.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
AUDIO="$QROOT/phase-03/scripts/audio.sh"
UTTER_PY="$QROOT/phase-03/scripts/utterances.py"
# A python with sherpa-onnx (phase 03's host venv) to synthesise the spoken prompt; the step is NOT RUN without one.
TTS_PY="${TTS_PY:-python3}"
RINGS="launcher speech"
row_begin EDGE_TESS "Tess arithmetic edge cases: a 1,000-digit result, a unit the Converter lacks, units across categories, the Calculator's display untouched"
D="$ROW_DIR"
CALC="app.tileshell/.calculator.CalculatorActivity"
SHELL_UID="$(adb shell pm list packages -U app.tileshell | tr -d '\r' | sed -n 's/^package:app\.tileshell uid:\([0-9]*\)$/\1/p')"

# ---------------------------------------------------------------- helpers

# The oracle's reply for a Tess request: gen_calc_cases.tess_row, printed as display<US>reply. The payload is one of
# this driver's own literal tuples below (eval'd with only the oracle's K in scope), never outside input.
tess_oracle() { # utterance kind payload(python literal, K() allowed)
  python3 - "$HERE" "$@" <<'PY'
import sys
sys.path.insert(0, sys.argv[1])
import gen_calc_cases as g
d, r = g.tess_row(sys.argv[2], sys.argv[3], eval(sys.argv[4], {"K": g.K}))
print(d + "\x1f" + r)
PY
}
oracle_keys() { python3 -c "import sys; sys.path.insert(0, '$HERE'); import gen_calc_cases as g; print(g.run_keys('$1', g.K('$2')))"; }

# Every node's text for one resource-id, in dump order, joined by " | ".
node_texts() { # dump.xml resource-id
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
out = []
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    if f'resource-id="{sys.argv[2]}"' in s:
        m = re.search(r'text="([^"]*)"', s)
        out.append(m.group(1) if m else "")
print(" | ".join(out))
PY
}

tess_home_open() { cortana_close; adb shell input keyevent KEYCODE_HOME; sleep 1; cortana_assist; sleep 3; }

# Back until no Tess session is on screen (the answer card's Back returns to Tess's idle page first). Prints yes/no:
# whether a session is still showing afterwards.
tess_close_all() {
  local i
  for i in 1 2 3 4; do
    dump_ui "$D/.close.xml" || true
    [ "$(has_node "$D/.close.xml" cortana_session)" = yes ] || { echo no; return 0; }
    adb shell input keyevent KEYCODE_BACK; sleep 1.2
  done
  dump_ui "$D/.close.xml" || true
  has_node "$D/.close.xml" cortana_session
}

# Waits (≤ timeout s) until the reply started after MARK has finished speaking ("speaking done" in the launcher ring),
# sampling dumpsys audio's players of the shell's uid on USAGE_ASSISTANT once a second into <players file>. A long
# e-notation reply takes ~40 s on this AVD (~24 s of synthesis, then ~17 s of audio; trial 2026-09-24).
# It waits for THIS reply's own id (the slice's first speak line): run 2 returned at once on a "speaking done" of an
# older reply whose synthesis was cancelled only when its generate() call returned, inside the new slice.
wait_reply() { # mark players_file [timeout]
  local mark="$1" out="$2" limit="${3:-150}" t0 slice id
  t0="$(date +%s)"
  : > "$out"
  while :; do
    adb shell dumpsys audio 2>/dev/null | tr -d '\r' | grep 'AudioPlaybackConfiguration' | grep "u/pid:$SHELL_UID/" \
      | grep 'usage=USAGE_ASSISTANT' | sed "s/^ */+$(( $(date +%s) - t0 ))s /" >> "$out"
    # Captured, not grep -q'd: under pipefail an early-exiting grep can SIGPIPE ring_since and read as a miss.
    slice="$(ring_since "$mark")"
    id="$(printf '%s\n' "$slice" | grep -oE '\[speech\] speak\[[0-9a-f-]+\]' | head -1 | sed 's/.*speak\[//; s/\]$//')"
    [ -n "$id" ] && [ -n "$(printf '%s\n' "$slice" | grep -F "[speech] speaking done $id ")" ] && return 0
    [ $(( $(date +%s) - t0 )) -ge "$limit" ] && { echo "+${limit}s TIMEOUT: no speaking done" >> "$out"; return 1; }
    sleep 1
  done
}

# The reply's own speak id (the first speak line of the slice) and whether it finished uncancelled.
speak_id() { grep -oE '\[speech\] speak\[[0-9a-f-]+\]' "$1" | head -1 | sed 's/.*speak\[//; s/\]$//'; }

# This emulator's qemu output stream on the host (audio.sh's emulator_sink, which cannot be sourced): "<id> <mute>".
emu_output_stream() {
  local name pid
  name="$(adb emu avd name 2>/dev/null | head -1 | tr -d '\r')"
  [ -n "$name" ] || return 0
  pid="$(pgrep -f "qemu-system.*-avd $name( |\$)" | head -1)"
  [ -n "$pid" ] || return 0
  pactl list sink-inputs | awk -v pid="$pid" '/^Sink Input #/ { id = substr($3, 2); mute = "" } /^[ \t]*Mute: / { mute = $2 } /application.process.id = / { gsub(/"/, "", $3); if ($3 == pid) { print id " " mute; exit } }'
}

# One Tess reply's checks, from a slice file: the reply text, the card, [match], [calc] tess, the speak finished, and a
# started player on USAGE_ASSISTANT.
check_reply() { # label slice players want_reply want_calc_line
  local label="$1" slice="$2" players="$3" id
  assert_eq "$label: the reply" "$4" "$(grep -F '[speech] speak[' "$slice" | grep -oE 'text="[^"]*"' | sed -n '1{s/^text="//;s/"$//;p;q;}')"
  assert_contains "$label: [match] -> Arithmetic" "-> Arithmetic(" "$(grep -F '[match]' "$slice")"
  assert_contains "$label: its [calc] tess line" "$5" "$(grep -F '[calc] tess' "$slice")"
  id="$(speak_id "$slice")"
  assert_contains "$label: the reply was spoken to the end (speaking done, not cancelled)" \
    "speaking done $id cancelled=false" "$(grep -F "speaking done ${id:-none}" "$slice")"
  assert_contains "$label: a player of the shell's on USAGE_ASSISTANT started (dumpsys audio)" "state:started" \
    "$(grep -F 'state:started' "$players" | head -1)"
}

# ---------------------------------------------------------------- baseline
assert_eq "baseline: the installed APK is the final build" 4b7ac321ce4d0ede "$(installed_apk_id)"
assert_ne "baseline: the shell's uid is known" "" "$SHELL_UID"
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_eq "baseline: no Tess session on screen" no "$(tess_close_all)"
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_contains "baseline: Home is the resumed activity" "app.tileshell/.StartActivity" "$(resumed)"
note "cited from E26 on this build (4b7ac321, E26/E26.txt): tess-008 \"what is point five times four\" -> \"0.5 times 4 is 2.\"; tess-009 \"what is negative three times four\" -> \"-3 times 4 is -12.\"; tess-010 \"what is 10 to the power of 10000\" -> \"Overflow.\" (not repeated here)"

# ---------------------------------------------------------------- 1. a 1,000-digit result, typed
POW_UTT="what is 9 to the power of 1047"
IFS=$'\x1f' read -r POW_DISP POW_REPLY <<< "$(tess_oracle "$POW_UTT" binary '("9", "to the power of", K("1047"), K("9 pow 1047 equals"))')"
note "oracle: $POW_UTT -> display [$POW_DISP] reply [$POW_REPLY]"
assert_eq "1: the exact result 9^1047 has 1,000 digits (host integer)" 1000 "$(python3 -c 'print(len(str(9 ** 1047)))')"
assert_contains "1: the oracle's display is the engine's e-notation (e+999)" "e+999" "$POW_DISP"
tess_home_open
MARK="$(ring_mark)"
type_request "$POW_UTT" 1
wait_reply "$MARK" "$D/players_pow_typed.txt"
ring_since "$MARK" > "$D/ring_tess1000typed_launcher.txt"
ring_since "$MARK" speech > "$D/ring_tess1000typed_speech.txt"
dump_ui "$D/card_pow_typed.xml"
check_reply "1 typed" "$D/ring_tess1000typed_launcher.txt" "$D/players_pow_typed.txt" "$POW_REPLY" "[calc] tess \"9 ^ 1047\" -> $POW_DISP"
assert_eq "1 typed: the answer card is up" yes "$(has_node "$D/card_pow_typed.xml" cortana_card:answer)"
assert_eq "1 typed: the card's title is the reply" "$POW_REPLY" "$(node_text "$D/card_pow_typed.xml" cortana_card_title)"
assert_eq "1 typed: the card's caption is Calculator" Calculator "$(node_text "$D/card_pow_typed.xml" cortana_card_caption)"
assert_eq "1 typed: the card's body is the expression and = the e-notation result" "${POW_REPLY% is *} | = $POW_DISP" \
  "$(node_texts "$D/card_pow_typed.xml" cortana_card_body)"
record "1 typed: the reply's synthesised length (speech ring tts line)" \
  "$(grep -oE 'frames=[0-9]+ \([0-9]+ ms\) in [0-9]+ ms cancelled=[a-z]+' "$D/ring_tess1000typed_speech.txt" | tail -1)"

# ---------------------------------------------------------------- 1. the same, spoken
POW_SAY="What is nine to the power of one thousand forty seven?"
POW_WAV="$D/utt_pow1047.wav"
"$TTS_PY" - "$(dirname "$UTTER_PY")" "$POW_SAY" "$POW_WAV" > "$D/utt_pow1047.log" 2>&1 <<'PY'
import sys, wave
sys.path.insert(0, sys.argv[1])
import utterances as u  # phase 03's synthesis: the same Kokoro prompt voice, padding and 16 kHz mono as every utterance
tts = u.make_tts()
a = tts.generate(sys.argv[2], sid=u.PROMPT_SPEAKER, speed=0.95)
u.write_wav(sys.argv[3], a.samples, tts.sample_rate, pad=True)
if tts.sample_rate != 16000:
    u.resample_to_16k(sys.argv[3])
with wave.open(sys.argv[3]) as w:
    print("%s: %.2f s" % (sys.argv[3], w.getnframes() / w.getframerate()))
PY
WAV_RC=$?
AUDIO_STATE="$("$AUDIO" check 2>&1)"; AUDIO_RC=$?
note "audio.sh check: $AUDIO_STATE (rc=$AUDIO_RC); prompt synthesis rc=$WAV_RC: $(tail -1 "$D/utt_pow1047.log")"
if [ "$WAV_RC" -ne 0 ] || [ ! -s "$POW_WAV" ]; then
  _verdict FAIL "1 spoken" "NOT RUN: the prompt could not be synthesised with TTS_PY=$TTS_PY (utt_pow1047.log)"
elif [ "$AUDIO_RC" -ne 0 ]; then
  _verdict FAIL "1 spoken" "NOT RUN: audio.sh check failed ($AUDIO_STATE)"
else
  read -r OUT_STREAM OUT_MUTE <<< "$(emu_output_stream)"
  note "this emulator's host output stream: ${OUT_STREAM:-none} Mute: ${OUT_MUTE:-?}"
  REC=""
  if [ "${OUT_MUTE:-}" = no ]; then
    MEDIA_VOL0="$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
    adb shell cmd media_session volume --stream 3 --set 10 >/dev/null 2>&1
  fi
  # Up to 3 attempts. An attempt whose capture the recogniser reports as empty ("heard=false": nothing reached this
  # emulator's microphone before the no-speech endpoint, ~2 s) is a host delivery miss, not a reply — run 1 lost one
  # that way (peak -92 dBFS, background -120: the null sink's own silence) while the same WAV was heard on every manual
  # replay. Each attempt keeps its own slices (ring_tess1000spoken_try<n>_*); the checks read the last one.
  for TRY in 1 2 3; do
    tess_home_open
    dump_ui "$D/.say.xml" || true
    FIN0="$(speech_dump | grep -cF '[speech] asr: final')"
    MARK="$(ring_mark)"
    REC=""
    if [ "${OUT_MUTE:-}" = no ]; then ( "$AUDIO" record "$D/pow_spoken_reply.wav" 80 >/dev/null 2>&1 ) & REC=$!; fi
    if [ "$(has_node "$D/.say.xml" cortana_listening_box)" != yes ]; then tap_node "$D/.say.xml" cortana_text_box_mic; fi
    T0="$(date +%s%3N)"
    timeout 40 "$AUDIO" say "$POW_WAV" > "$D/say_try$TRY.log" 2>&1; SAY_RC=$?
    note "1 spoken attempt $TRY: audio.sh say rc=$SAY_RC in $(( $(date +%s%3N) - T0 )) ms after the mic tap $(tr '\n' ' ' < "$D/say_try$TRY.log")"
    for _ in $(seq 1 15); do [ "$(speech_dump | grep -cF '[speech] asr: final')" -gt "$FIN0" ] && break; sleep 1; done
    FIN1="$(speech_dump | grep -cF '[speech] asr: final')"
    wait_reply "$MARK" "$D/players_pow_spoken.txt"
    ring_since "$MARK" > "$D/ring_tess1000spoken_try${TRY}_launcher.txt"
    ring_since "$MARK" speech > "$D/ring_tess1000spoken_try${TRY}_speech.txt"
    note "1 spoken attempt $TRY: $(grep -oE 'asr: levels .*' "$D/ring_tess1000spoken_try${TRY}_speech.txt" | tail -1); final $(grep -F '[speech] final' "$D/ring_tess1000spoken_try${TRY}_launcher.txt" | tail -1 | sed 's/.*final //')"
    [ -n "$REC" ] && wait "$REC" 2>/dev/null
    [ -z "$(grep -F 'heard=false' "$D/ring_tess1000spoken_try${TRY}_speech.txt")" ] && break
  done
  cp "$D/ring_tess1000spoken_try${TRY}_launcher.txt" "$D/ring_tess1000spoken_launcher.txt"
  cp "$D/ring_tess1000spoken_try${TRY}_speech.txt" "$D/ring_tess1000spoken_speech.txt"
  assert_ne "1 spoken: the recogniser produced a new final" "$FIN0" "$FIN1"
  # The capture's levels line, not the slice's last asr: line — "no speech" is logged before the final that follows it.
  assert_contains "1 spoken: the capture was heard (C-30)" "heard=true" "$(grep -oE 'asr: levels .*' "$D/ring_tess1000spoken_speech.txt" | tail -1)"
  check_reply "1 spoken" "$D/ring_tess1000spoken_launcher.txt" "$D/players_pow_spoken.txt" "$POW_REPLY" "[calc] tess \"9 ^ 1047\" -> $POW_DISP"
  record "1 spoken: the reply's synthesised length (speech ring tts line)" \
    "$(grep -oE 'frames=[0-9]+ \([0-9]+ ms\) in [0-9]+ ms cancelled=[a-z]+' "$D/ring_tess1000spoken_speech.txt" | tail -1)"
  if [ -n "$REC" ]; then
    RMS="$("$AUDIO" rms "$D/pow_spoken_reply.wav" 2>/dev/null)"
    if python3 -c 'import sys; sys.exit(0 if float(sys.argv[1]) > -40 else 1)' "$RMS" 2>/dev/null; then
      _verdict PASS "1 spoken: the reply was audible" "RMS $RMS dBFS > -40"
    else
      _verdict FAIL "1 spoken: the reply was audible" "RMS ${RMS:-?} dBFS is not above -40"
    fi
    [ -n "${MEDIA_VOL0:-}" ] && adb shell cmd media_session volume --stream 3 --set "$MEDIA_VOL0" >/dev/null 2>&1
  else
    record "NOT RUN 1 spoken: reply audibility (phase 03's RMS > -40 dBFS)" \
      "the host mutes this emulator's output stream (pactl sink-input #${OUT_STREAM:-?} Mute: ${OUT_MUTE:-?}; Jeremy 2026-09-24 \"mute the emulators\"), so a capture reads silence — E26's same 8 checks are accepted on that reason (H17). The reply's playback is asserted from dumpsys audio (a started USAGE_ASSISTANT player) and the speak line finishing uncancelled."
  fi
fi

# ---------------------------------------------------------------- 2. / 3. not understood, with the bare-form control
not_understood() { # what utterance
  local what="$1" utt="$2" slice="$D/ring_$1_launcher.txt"
  tess_home_open
  MARK="$(ring_mark)"
  type_request "$utt" 1
  wait_reply "$MARK" "$D/players_$what.txt" 60
  ring_since "$MARK" > "$slice"
  assert_contains "$what: \"$utt\" matched nothing" "\"$utt\" -> NotUnderstood(" "$(grep -F '[match]' "$slice")"
  assert_contains "$what: it reached the not-understood handler" "[not_understood] transcript=\"$utt\"" "$(grep -F '[not_understood]' "$slice")"
  assert_absent "$what: no [calc] tess line in its slice" "[calc] tess" "$(cat "$slice")"
  record "$what: the reply" "$(reply_since "$MARK")"
}
not_understood parsecs "5 parsecs in miles"
not_understood crosscat "5 miles in kilograms"
CTL_UTT="5 miles in kilometers"
IFS=$'\x1f' read -r CTL_DISP CTL_REPLY <<< "$(tess_oracle "$CTL_UTT" convert '("5", "Length", "Miles", "Kilometers")')"
tess_home_open
MARK="$(ring_mark)"
type_request "$CTL_UTT" 1
wait_reply "$MARK" "$D/players_convcontrol.txt" 60
ring_since "$MARK" > "$D/ring_convcontrol_launcher.txt"
check_reply "control (same bare form, one category)" "$D/ring_convcontrol_launcher.txt" "$D/players_convcontrol.txt" "$CTL_REPLY" \
  "[calc] tess \"5 Miles -> Kilometers\" -> $CTL_DISP Kilometers"
assert_eq "restore: Tess closed after the typed cases" no "$(tess_close_all)"

# ---------------------------------------------------------------- 4. a request while the Calculator shows another value
adb shell input keyevent KEYCODE_HOME; sleep 1
hist_sha() { adb shell "run-as app.tileshell sh -c 'sha256sum files/calc_history.txt 2>/dev/null || echo none'" < /dev/null | tr -d '\r' | cut -d' ' -f1; }
HIST0="$(hist_sha)"
note "the Calculator's history store as found: sha256 $HIST0"
adb shell am start -W -n "$CALC" >/dev/null 2>&1; sleep 2
dump_ui "$D/calc_found.xml"
CALC_PAGE0="$(grep -oE 'resource-id="calc_mode:[a-z]+"[^>]*checked="true"' "$D/calc_found.xml" | grep -oE 'calc_mode:[a-z]+' | head -1)"
DISP_FOUND="$(node_text "$D/calc_found.xml" calc_display)"
note "the Calculator as found: page ${CALC_PAGE0:-?}, calc_display [$DISP_FOUND], calc_expr [$(node_text "$D/calc_found.xml" calc_expr)]"
assert_contains "4: the Calculator is the resumed activity" "app.tileshell/.calculator.CalculatorActivity" "$(resumed)"
assert_eq "4 baseline: the Calculator shows 0 (RV12)" "$(oracle_keys standard clear)" "$DISP_FOUND"
for k in clear 1 2 3 add 4 5 6; do tap_node "$D/calc_found.xml" "calc_key:$k"; done; sleep 0.8
dump_ui "$D/calc_before.xml"; screencap "$D/calc_before.png"
DISP_BEFORE="$(node_text "$D/calc_before.xml" calc_display)"
EXPR_BEFORE="$(node_text "$D/calc_before.xml" calc_expr)"
assert_eq "4: the Calculator shows another value (123 + 456 pending)" "$(oracle_keys standard '1 2 3 add 4 5 6')" "$DISP_BEFORE"
assert_eq "4: its expression line shows the pending 123 +" "123 +" "$EXPR_BEFORE"
CALC_UTT="what is 6 times 7 minus 5"
IFS=$'\x1f' read -r CALC_DISP CALC_REPLY <<< "$(tess_oracle "$CALC_UTT" expr '("6 times 7 minus 5", K("6 multiply 7 subtract 5 equals"))')"
cortana_assist; sleep 3
dump_ui "$D/calc_tess_open.xml"
assert_eq "4: Tess's session is up over the Calculator" yes "$(has_node "$D/calc_tess_open.xml" cortana_session)"
assert_contains "4: the Calculator is still the resumed activity beneath Tess" "app.tileshell/.calculator.CalculatorActivity" "$(resumed)"
MARK="$(ring_mark)"
type_request "$CALC_UTT" 1
wait_reply "$MARK" "$D/players_calcapp.txt" 60
ring_since "$MARK" > "$D/ring_calcapp_launcher.txt"
check_reply "4" "$D/ring_calcapp_launcher.txt" "$D/players_calcapp.txt" "$CALC_REPLY" "[calc] tess \"6 * 7 - 5\" -> $CALC_DISP"
assert_eq "4: Tess closed with Back" no "$(tess_close_all)"
assert_contains "4: the Calculator is resumed again" "app.tileshell/.calculator.CalculatorActivity" "$(resumed)"
dump_ui "$D/calc_after.xml"; screencap "$D/calc_after.png"
assert_eq "4: calc_display unchanged by Tess's request" "$DISP_BEFORE" "$(node_text "$D/calc_after.xml" calc_display)"
assert_eq "4: calc_expr unchanged by Tess's request" "$EXPR_BEFORE" "$(node_text "$D/calc_after.xml" calc_expr)"
assert_eq "4: the Calculator's history store unchanged by Tess's request" "$HIST0" "$(hist_sha)"

# ---------------------------------------------------------------- restore (RV12)
tap_node "$D/calc_after.xml" calc_key:clear; sleep 0.8
dump_ui "$D/calc_restored.xml"
assert_eq "restore: the Calculator's display as found" "$DISP_FOUND" "$(node_text "$D/calc_restored.xml" calc_display)"
assert_eq "restore: the Calculator's expression line empty" "" "$(node_text "$D/calc_restored.xml" calc_expr)"
assert_eq "restore: the Calculator's page as found" "${CALC_PAGE0:-?}" \
  "$(grep -oE 'resource-id="calc_mode:[a-z]+"[^>]*checked="true"' "$D/calc_restored.xml" | grep -oE 'calc_mode:[a-z]+' | head -1)"
assert_eq "restore: the Calculator's history as found (sha256)" "$HIST0" "$(hist_sha)"
adb shell input keyevent KEYCODE_HOME; sleep 2
dump_ui "$D/restored_home.xml"
assert_eq "restore: no Tess session on screen" no "$(has_node "$D/restored_home.xml" cortana_session)"
assert_contains "restore: Home is the resumed activity" "app.tileshell/.StartActivity" "$(resumed)"
assert_eq "restore: Tess idle (no started USAGE_ASSISTANT player of the shell's)" 0 \
  "$(adb shell dumpsys audio | tr -d '\r' | grep 'AudioPlaybackConfiguration' | grep "u/pid:$SHELL_UID/" | grep 'usage=USAGE_ASSISTANT' | grep -c 'state:started')"
row_end
