#!/usr/bin/env bash
# Phase 14 row helpers, sourced after lib.sh (phase 03's floor, symlinked here). Every driver pins emulator-5554 —
# the other AVDs on this host belong to other projects.
export ANDROID_SERIAL=emulator-5554
P14="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
P03S="$(cd "$P14/../phase-03/scripts" && pwd)"
P02S="$(cd "$P14/../phase-02/scripts" && pwd)"
P01S="$(cd "$P14/../phase-01/scripts" && pwd)"
P12S="$(cd "$P14/../phase-12/scripts" && pwd)"
P13S="$(cd "$P14/../phase-13/scripts" && pwd)"

# The pan the doc's rows use: inside the page, well clear of the left gesture inset.
swipe_right() { adb shell input swipe 200 1200 950 1200 250; sleep "${1:-1.5}"; }
swipe_left() { adb shell input swipe 900 1200 150 1200 250; sleep "${1:-1.5}"; }

# "Start alone": start_page in the dump, and neither pivot neighbour.
start_alone() { # dump.xml -> yes / no
  if [ "$(has_node "$1" start_page)" = yes ] && [ "$(has_node "$1" pod_bay)" = no ] && [ "$(has_node "$1" app_list)" = no ]; then
    echo yes
  else
    echo no
  fi
}

# The `[start] page=<name>` lines in a slice, in order, space-separated.
pages_in() { printf '%s\n' "$1" | grep -oE '\[start\] page=[A-Z_]+' | sed 's/.*page=//' | tr '\n' ' ' | sed 's/ $//'; }

# C-6: after any launch — keep the ring, force-stop, Home, wait for Start.
c6() {
  ring_save
  adb shell am force-stop app.tileshell
  sleep 1
  adb shell input keyevent KEYCODE_HOME
  sleep 4
}

# The activity on top, short form (app.tileshell/.StartActivity).
top_activity() {
  adb shell dumpsys activity activities | grep -m1 'topResumedActivity' | tr -d '\r' | sed -E 's/.* u0 ([^ ]+) .*/\1/'
}

# Whether Tess's session window is SHOWING. The window object outlives a hide, so its presence says nothing; its own
# state does (probed 2026-09-29: shown isVisible=true mViewVisibility=0x0, hidden isVisible=false mViewVisibility=0x8).
session_window() {
  if adb shell dumpsys window windows | awk '/Window #[0-9]+ Window\{.*VoiceInteractionSession\}:/ {f=1; next} f && /Window #[0-9]+ Window\{/ {f=0} f' \
    | grep -q 'isVisible=true'; then echo yes; else echo no; fi
}

# The wall= value of the first line in a slice that contains a fixed string ('' when none).
wall_of_first() { # slice needle
  printf '%s\n' "$1" | grep -F -- "$2" | head -1 | grep -oE 'wall=[0-9]+' | cut -d= -f2
}

# The pod bay open from Start: ensure_start, then the pan; asserts the page and returns 0 when it is showing.
open_pod_bay() { # out.xml
  ensure_start || return 1
  swipe_right
  dump_ui "$1"
  [ "$(has_node "$1" pod_bay)" = yes ]
}

# ---- C-3 seeding (rows that read Start's grid: E1, E10, E13) ---------------------------------------------------------
. "$P02S/layout.sh"

# Save the device's own layout, seed phase 02's baseline, and assert no slot was assigned while the shell loaded it.
seed_baseline() {
  layout_save "$ROW_DIR/device-layout.json"
  ring_save
  local mark; mark="$(ring_mark)"
  layout_restore "$P02S/../baseline_layout.json"
  assert_eq "layout_restore of phase 02's baseline" "0" "$?"
  sleep 1
  local slice assigned
  slice="$(ring_since "$mark")"
  # The slice must cover the restore (its own "already run" lines), so a zero below is about this load, not an empty ring.
  assert_ne "C-3: the slice covers the restore (assignSlotOnce lines in it)" "0" "$(printf '%s\n' "$slice" | grep -cF 'assignSlotOnce')"
  assigned="$(printf '%s\n' "$slice" | grep -F 'assignSlotOnce' | grep -cF -- '-> assigned')"
  assert_eq "C-3: zero assignSlotOnce ... -> assigned lines after the restore" "0" "$assigned"
}

# RV12: back to the layout the device had before the row.
restore_device_layout() {
  ring_save
  layout_restore "$ROW_DIR/device-layout.json"
  assert_eq "restore: the device's own layout" "0" "$?"
}

# ---- voice (the Audio route: AUDIO_ROUTE=emu, the emulator's own gRPC injectAudio / streamAudio) ------------------------
# The media volume a spoken reply is captured at: phase 03's j5.sh leaves it 0 and Tess's USAGE_ASSISTANT voice follows
# it; the audible steps run at full scale and the row restores it (qa/phase-14/README.md, the spike's runs 2-5).
vol_get() { adb shell cmd media_session volume --stream "$1" --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+'; }
voice_volume_up() { VOICE_VOL0="$(vol_get 3)"; adb shell cmd audio set-volume 3 15 >/dev/null 2>&1; note "media volume ${VOICE_VOL0:-?} -> $(vol_get 3) for the spoken steps"; }
voice_volume_restore() {
  [ -n "${VOICE_VOL0:-}" ] && adb shell cmd audio set-volume 3 "$VOICE_VOL0" >/dev/null 2>&1
  assert_eq "restore: the media volume" "${VOICE_VOL0:-?}" "$(vol_get 3)"
}

# Tess open (KEYCODE_ASSIST: Home mode, the text box and its microphone), waited for.
tess_open() {
  local i
  cortana_assist
  for i in 1 2 3 4 5 6 7 8; do
    sleep 1
    dump_ui "$ROW_DIR/.tess-open.xml"
    [ "$(has_node "$ROW_DIR/.tess-open.xml" cortana_session)" = yes ] && return 0
  done
  return 1
}

# One spoken step, speak.sh's one-request form (its exit status asserted 0), with a streamAudio capture of the reply
# window running beside it, then C-30 on the :speech process's own ring. Leaves:
#   VS_MARK   the MARK taken before the step        VS_SLICE  the launcher ring since it   VS_SPEECH  the :speech ring
#   VS_REPLY  reply_since VS_MARK                     VS_RMS    the capture's RMS (dBFS)
voice_step() { # utterance-id settle label
  local id="$1" settle="$2" label="$3" rc rec
  VS_MARK="$(ring_mark)"
  ( python3 "$P03S/emu_audio.py" record "$ROW_DIR/$label-reply.wav" 28 > "$ROW_DIR/$label-record.out" 2>&1; echo "rc=$?" >> "$ROW_DIR/$label-record.out" ) &
  rec=$!
  SPEAK_SPEECH_OUT="$ROW_DIR/$label-speech-at-final.txt" "$P03S/speak.sh" "$id" "$settle" > "$ROW_DIR/$label-final.txt" 2> "$ROW_DIR/$label-speak.err"
  rc=$?
  wait "$rec"
  note "$label: speak.sh $id rc=$rc final: $(cat "$ROW_DIR/$label-final.txt"); $(tr '\n' ' ' < "$ROW_DIR/$label-speak.err")"
  assert_eq "$label: speak.sh $id exits 0" "0" "$rc"
  # The :speech ring as speak.sh caught it at the final (a request that closes Tess unbinds the speech service, and its
  # ring cannot be read afterwards), sliced from the step's MARK.
  VS_SPEECH="$(python3 -c '
import re, sys
mark = int(sys.argv[1])
for line in open(sys.argv[2], encoding="utf-8", errors="replace"):
    m = re.search(r"\bwall=(\d+)", line)
    if m and int(m.group(1)) >= mark: print(line.rstrip())' "$VS_MARK" "$ROW_DIR/$label-speech-at-final.txt" 2>/dev/null)"
  printf '%s\n' "$VS_SPEECH" > "$ROW_DIR/$label-speech.txt"
  VS_SLICE="$(ring_since "$VS_MARK")"; printf '%s\n' "$VS_SLICE" > "$ROW_DIR/$label-slice.txt"
  VS_REPLY="$(reply_since "$VS_MARK")"
  VS_RMS="$("$P03S/audio.sh" rms "$ROW_DIR/$label-reply.wav" 2>/dev/null)"
  # C-30 (r3 V16): heard, not dropped by the gate.
  assert_contains "$label: C-30 asr levels heard=true" "(heard=true)" "$(printf '%s\n' "$VS_SPEECH" | grep -F 'asr: levels')"
  assert_absent "$label: C-30 no 'no speech in the capture'" "asr: no speech in the capture" "$VS_SPEECH"
  note "$label: reply [$VS_REPLY] RMS $VS_RMS dBFS; record: $(tr '\n' ' ' < "$ROW_DIR/$label-record.out")"
}

assert_rms_audible() { # label rms
  if python3 -c 'import sys; sys.exit(0 if float(sys.argv[1]) > -40 else 1)' "$2" 2>/dev/null; then
    _verdict PASS "$1: the reply was audible (streamAudio)" "RMS $2 dBFS > -40"
  else
    _verdict FAIL "$1: the reply was audible (streamAudio)" "RMS $2 dBFS is not above -40"
  fi
}

# The [match] line in a slice.
match_line() { printf '%s\n' "$1" | grep -F '[match]' | tail -1 | sed 's/.*\[match\] //'; }
