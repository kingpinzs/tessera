#!/usr/bin/env bash
# E26 — Tess arithmetic (Q5, P6; T15-2, T15-52): every `tess` line of calc-cases.tsv typed through the real text box,
# its reply equal to the host-computed tess_reply column; the spoken requests (calc_percent, calc_divzero, calc_convert,
# calc_root) under phase 03's spoken reply pass rule; calc_percent over the keyguard; and the negatives (weather_like,
# unmatched, calc_life) reaching no arithmetic. Every step takes its own MARK first (C-20). A spoken step whose audio
# path fails `audio.sh check` is a FAIL marked NOT RUN with the check's output — never skipped silently.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
AUDIO="$QROOT/phase-03/scripts/audio.sh"
SPEAK="$QROOT/phase-03/scripts/speak.sh"
RINGS="launcher speech"
row_begin E26 "Tess answers arithmetic and conversions, typed and spoken, locked and not"

tess_open() { cortana_close; adb shell input keyevent KEYCODE_HOME; sleep 1; cortana_assist; sleep 3; }

# ---------------------------------------------------------------- typed: every tess line
wake_device
# The tess lines as id / utterance / reply joined by \x1f: tab is IFS whitespace, so empty tsv fields (setup, keys)
# would collapse and shift every column (run 1 typed the SOURCE column). Read on fd 3 so adb cannot swallow the list.
python3 - "$P15/calc-cases.tsv" > "$ROW_DIR/tess-cases.txt" <<'PY'
import sys
for l in open(sys.argv[1], encoding="utf-8"):
    f = l.rstrip("\n").split("\t")
    if len(f) > 6 and f[1] == "tess":
        print("\x1f".join([f[0], f[5], f[6]]))
PY
assert_eq "every tess line of the oracle is listed" "$(awk -F'\t' '$2 == "tess"' "$P15/calc-cases.tsv" | wc -l)" "$(wc -l < "$ROW_DIR/tess-cases.txt")"
while IFS=$'\x1f' read -r id utt reply <&3; do
  assert_ne "$id has an utterance and a reply in the oracle" "" "$utt$reply"
  tess_open
  MARK="$(ring_mark)"
  type_request "$utt" 8
  ring_since "$MARK" > "$ROW_DIR/ring_$id.txt"
  assert_eq "$id typed \"$utt\"" "$reply" "$(reply_since "$MARK")"
  assert_contains "$id matched Arithmetic" "-> Arithmetic(" "$(grep -F '[match]' "$ROW_DIR/ring_$id.txt")"
  assert_contains "$id logged its [calc] tess line" "[calc] tess \"" "$(cat "$ROW_DIR/ring_$id.txt")"
  if [ "$id" = tess-001 ]; then
    dump_ui "$ROW_DIR/card_tess-001.xml"
    assert_eq "the answer card is up" yes "$(has_node "$ROW_DIR/card_tess-001.xml" cortana_card:answer)"
    assert_eq "its caption is Calculator" Calculator "$(node_text "$ROW_DIR/card_tess-001.xml" cortana_card_caption)"
    assert_contains "its body shows the expression and the result" "= 12" "$(grep -o 'resource-id="cortana_card_body"[^>]*' "$ROW_DIR/card_tess-001.xml"; grep -o 'text="[^"]*"' "$ROW_DIR/card_tess-001.xml" | tr '\n' ' ')"
    assert_contains "the log carries the canonical expression" '[calc] tess "15 % of 80" -> 12' "$(cat "$ROW_DIR/ring_$id.txt")"
  fi
  if [ "$id" = tess-002 ]; then
    assert_contains "division by zero is logged as an error" '[calc] tess "1 / 0" -> error' "$(cat "$ROW_DIR/ring_$id.txt")"
  fi
done 3< "$ROW_DIR/tess-cases.txt"
cortana_close

# ---------------------------------------------------------------- spoken
# Tess speaks on USAGE_ASSISTANT, which follows the media volume on this AVD; the pass rule's RMS needs it audible
# (E26 run 3: media volume 0, every reply -115 dBFS). Raised for the spoken steps, put back as found at the end (RV12).
MEDIA_VOL0="$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
note "media volume before: ${MEDIA_VOL0:-?}"
adb shell cmd media_session volume --stream 3 --set 10 >/dev/null 2>&1
AUDIO_STATE="$("$AUDIO" check 2>&1)"; AUDIO_RC=$?
note "audio.sh check: $AUDIO_STATE (rc=$AUDIO_RC)"
# spoken <utterance> <expected reply> <expected launcher-ring needle>
spoken() {
  local u="$1" want="$2" needle="$3" final rms check crc
  check="$("$AUDIO" check 2>&1)"; crc=$?
  if [ "$crc" -ne 0 ]; then
    _verdict FAIL "$u spoken" "NOT RUN: audio.sh check failed ($check)"
    return 1
  fi
  MARK="$(ring_mark)"
  ( "$AUDIO" record "$ROW_DIR/${u}_reply.wav" 28 >/dev/null 2>&1 ) &
  local rec=$!
  final="$("$SPEAK" "$u" 11)"
  wait "$rec" 2>/dev/null
  ring_since "$MARK" > "$ROW_DIR/ring_$u.txt"
  ring_since "$MARK" speech > "$ROW_DIR/ring_${u}_speech.txt"
  note "$u final: $final"
  assert_absent "$u: the capture was heard (C-30)" "asr: no speech" "$(grep -F 'asr:' "$ROW_DIR/ring_${u}_speech.txt" | tail -1)"
  [ -n "$want" ] && assert_eq "$u reply" "$want" "$(reply_since "$MARK")"
  [ -n "$needle" ] && assert_contains "$u launcher ring" "$needle" "$(cat "$ROW_DIR/ring_$u.txt")"
  rms="$("$AUDIO" rms "$ROW_DIR/${u}_reply.wav" 2>/dev/null)"
  if python3 -c 'import sys; sys.exit(0 if float(sys.argv[1]) > -40 else 1)' "$rms" 2>/dev/null; then
    _verdict PASS "$u reply was audible" "RMS $rms dBFS > -40"
  else
    _verdict FAIL "$u reply was audible" "RMS ${rms:-?} dBFS is not above -40"
  fi
}

tess_open
spoken calc_percent "15 % of 80 is 12." '[calc] tess "15 % of 80" -> 12'
[ -s "$ROW_DIR/ring_calc_percent.txt" ] && assert_contains "calc_percent matched Arithmetic" "-> Arithmetic(" "$(grep -F '[match]' "$ROW_DIR/ring_calc_percent.txt")"
dump_ui "$ROW_DIR/card_calc_percent.xml"
tess_open
spoken calc_divzero "Cannot divide by zero." '[calc] tess "1 / 0" -> error'
tess_open
spoken calc_convert "5 miles is 8.04672 kilometers." '[calc] tess '
tess_open
spoken calc_root "√81 is 9." '[calc] tess '

# Over the keyguard (E9's route): PIN, sleep, wake, KEYCODE_ASSIST.
adb shell locksettings set-disabled false >/dev/null 2>&1
adb shell locksettings set-pin 1234 >/dev/null 2>&1
adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 2
adb shell input keyevent KEYCODE_ASSIST; sleep 4
if spoken calc_percent "15 % of 80 is 12." '[calc] tess "15 % of 80" -> 12'; then
  adb shell dumpsys window | tr -d '\r' > "$ROW_DIR/window_locked.txt"
  assert_contains "the keyguard was still showing" "isKeyguardShowing=true" "$(grep -o 'isKeyguardShowing=[a-z]*' "$ROW_DIR/window_locked.txt" | head -1)"
  dump_ui "$ROW_DIR/locked_card.xml"
  assert_eq "no Unlock to continue card" no "$(has_node "$ROW_DIR/locked_card.xml" cortana_card:unlock)"
fi
adb shell locksettings clear --old 1234 >/dev/null 2>&1
adb shell locksettings set-disabled true >/dev/null 2>&1
wake_device

# Negatives, each from its own MARK: nothing reaches the arithmetic.
# (Their replies are judged by where the request went, not by text: the weather reply depends on the forecast.)
# Each negative's checks run only when its capture happened (a NOT RUN is already its own FAIL).
for u in weather_like unmatched calc_life; do
  tess_open
  spoken "$u" "" "" || continue
  if [ "$u" = weather_like ]; then
    assert_contains "weather_like reached the weather action" "-> Weather" "$(grep -F '[match]' "$ROW_DIR/ring_$u.txt")"
  else
    assert_contains "$u matched nothing" "-> NotUnderstood(" "$(grep -F '[match]' "$ROW_DIR/ring_$u.txt")"
    assert_contains "$u reached the not-understood handler (phase 03 E3's line)" "transcript=" "$(grep -F '[not_understood]' "$ROW_DIR/ring_$u.txt")"
  fi
  assert_absent "$u: no [calc] tess line in its slice" "[calc] tess" "$(cat "$ROW_DIR/ring_$u.txt")"
done
cortana_close
[ -n "${MEDIA_VOL0:-}" ] && adb shell cmd media_session volume --stream 3 --set "$MEDIA_VOL0" >/dev/null 2>&1
row_end
