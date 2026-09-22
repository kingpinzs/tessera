#!/usr/bin/env bash
# Phase 03 QA: the emulator audio route (phase doc Decisions, review F2-M16).
#
# The AVD's microphone is fed from a PulseAudio/PipeWire null sink, and the AVD's output is captured
# from its own sink monitor. Host audio input is blocked by default since emulator 28.0.3, so the route
# also needs `adb emu avd hostmicon` on the running emulator (or `-allow-host-audio` at launch).
#
#   audio.sh setup                 create the null sink and turn the AVD's host mic on
#   audio.sh say <wav>             play <wav> into the AVD's microphone, blocking until it ends
#   audio.sh record <out> <secs>   capture the AVD's output for <secs> seconds
#   audio.sh rms <wav>             print the RMS of <wav> in dBFS
#   audio.sh teardown              remove the null sink and restore the previous default source
#
# The spoken-reply pass rule (Decisions): a reply passes when its reply text in the diagnostics dump
# equals the expected string AND the capture over the reply window has an RMS above -40 dBFS. There is
# no ASR grading of Cortana's own voice anywhere in this gate.
set -uo pipefail

export PATH="$HOME/Android/Sdk/platform-tools:$PATH"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
state="$here/../.audio-state"
SINK=vmic

setup() {
  mkdir -p "$(dirname "$state")"
  if ! pactl list short sources | grep -q "^[0-9]*[[:space:]]*$SINK\.monitor"; then
    pactl list short sinks | grep -q "[[:space:]]$SINK[[:space:]]" || \
      pactl load-module module-null-sink sink_name=$SINK \
        rate=16000 channels=1 format=s16le \
        sink_properties=device.description=tileshell-mic > "$state.module"
  fi
  # Remember what the default source was, so teardown really restores it.
  [ -f "$state.prevsource" ] || pactl get-default-source > "$state.prevsource"
  pactl set-default-source $SINK.monitor
  adb emu avd hostmicon
  echo "default source: $(pactl get-default-source)"
  echo "emulator sink:  $(emulator_sink)"
}

teardown() {
  if [ -f "$state.prevsource" ]; then
    pactl set-default-source "$(cat "$state.prevsource")" || true
    rm -f "$state.prevsource"
  fi
  if [ -f "$state.module" ]; then
    pactl unload-module "$(cat "$state.module")" || true
    rm -f "$state.module"
  fi
  echo "default source: $(pactl get-default-source)"
}

# The sink is created at 16 kHz MONO on purpose: the AVD's microphone path is 16 kHz mono, and a
# 48 kHz stereo null sink puts a resample and a downmix between the utterance and the recogniser. The
# first run through a 48 kHz stereo sink turned "What time is it?" into "BUT TIME IS IT NOT"; at the
# microphone's own rate there is nothing in between.

# The emulator's own output sink input, so a reply is captured from what the AVD plays, not from the
# whole desktop.
emulator_sink() {
  pactl list short sink-inputs | grep -i qemu | awk '{print $1}' | head -1
}

say() {
  local wav="$1"
  [ -f "$wav" ] || { echo "no such wav: $wav" >&2; return 2; }
  paplay -d $SINK "$wav"
}

record() {
  local out="$1" secs="$2"
  local monitor
  monitor="$(pactl get-default-sink).monitor"
  timeout "$secs" parecord --device="$monitor" --file-format=wav --rate=16000 --channels=1 "$out"
  # parecord is killed by the timeout, which is the intended end of the capture, not a failure.
  return 0
}

rms() {
  python3 - "$1" <<'PY'
import sys, wave, array, math
with wave.open(sys.argv[1], 'rb') as w:
    frames = w.readframes(w.getnframes())
    width = w.getsampwidth()
samples = array.array({1: 'b', 2: 'h', 4: 'i'}[width])
samples.frombytes(frames)
if not samples:
    print("-inf"); raise SystemExit
peak = float(1 << (8 * width - 1))
mean = sum((s / peak) ** 2 for s in samples) / len(samples)
print(f"{20 * math.log10(math.sqrt(mean)):.2f}" if mean > 0 else "-inf")
PY
}

case "${1:-}" in
  setup) setup ;;
  teardown) teardown ;;
  say) say "$2" ;;
  record) record "$2" "${3:-5}" ;;
  rms) rms "$2" ;;
  *) sed -n '2,20p' "$0"; exit 2 ;;
esac
