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
# Per emulator (phase 15, T15-30): two emulators share this host's PipeWire. Each names its own null sink with
# AUDIO_SINK (default vmic, the phase 03 route; phase 15's emulator-5556 uses vmic5556), so one session's utterance is
# never heard by the other's AVD.
SINK="${AUDIO_SINK:-vmic}"
if [ "$SINK" = vmic ]; then state="$here/../.audio-state"; else state="$here/../.audio-state-$SINK"; fi

setup() {
  mkdir -p "$(dirname "$state")"
  if ! pactl list short sources | grep -q "^[0-9]*[[:space:]]*$SINK\.monitor"; then
    pactl list short sinks | grep -q "[[:space:]]$SINK[[:space:]]" || \
      pactl load-module module-null-sink sink_name=$SINK \
        rate=16000 channels=1 format=s16le \
        sink_properties=device.description=tileshell-mic > "$state.module"
  fi
  if [ "$SINK" = vmic ]; then
    # The phase 03 route: the host default source. Remember what it was, so teardown really restores it.
    [ -f "$state.prevsource" ] || pactl get-default-source > "$state.prevsource"
    pactl set-default-source $SINK.monitor
  fi
  adb emu avd hostmicon
  # The default source alone does not move a capture stream the emulator already opened (on this host both AVDs'
  # streams were found on the desk microphone chain): move THIS emulator's stream onto the sink's monitor.
  local so
  so="$(emulator_source_output)"
  if [ -n "$so" ]; then pactl move-source-output "$so" "$SINK.monitor"; fi
  echo "default source: $(pactl get-default-source)"
  echo "capture stream: ${so:-none} -> $(emulator_capture_source)"
  echo "emulator sink:  $(emulator_sink)"
}

# The emulator this script drives: ANDROID_SERIAL (emulator-<port>) -> its AVD name -> its qemu process id.
emulator_pid() {
  local name
  name="$(adb emu avd name 2>/dev/null | head -1 | tr -d '\r')"
  [ -n "$name" ] || return 1
  pgrep -f "qemu-system.*-avd $name( |\$)" | head -1
}

# The PipeWire stream id of this emulator's microphone capture (a source-output) / audio output (a sink-input).
emulator_source_output() {
  local pid; pid="$(emulator_pid)" || return 0
  pactl list source-outputs | awk -v pid="$pid" '/^Source Output #/ { id = substr($3, 2) } /application.process.id = / { gsub(/"/, "", $3); if ($3 == pid) print id }' | head -1
}
emulator_capture_source() {
  local pid so src; so="$(emulator_source_output)"; [ -n "$so" ] || { echo none; return; }
  src="$(pactl list short source-outputs | awk -v id="$so" '$1 == id { print $2 }')"
  pactl list short sources | awk -v id="$src" '$1 == id { print $2 }'
}

# Asserts the route before a microphone or reply-audio step: this emulator's capture stream is on the sink's monitor
# and its output stream exists. Exit 1 (with the reason) otherwise — a precondition, never a silent pass.
check() {
  local cap out
  cap="$(emulator_capture_source)"
  out="$(emulator_sink)"
  echo "capture source: $cap (want $SINK.monitor); output stream: ${out:-none}"
  [ "$cap" = "$SINK.monitor" ] && [ -n "$out" ]
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
  local pid; pid="$(emulator_pid)" || return 0
  pactl list sink-inputs | awk -v pid="$pid" '/^Sink Input #/ { id = substr($3, 2) } /application.process.id = / { gsub(/"/, "", $3); if ($3 == pid) print id }' | head -1
}

say() {
  local wav="$1"
  [ -f "$wav" ] || { echo "no such wav: $wav" >&2; return 2; }
  paplay -d $SINK "$wav"
}

record() {
  local out="$1" secs="$2" stream
  # THIS emulator's own output stream, never the default sink's monitor (which carries both AVDs and the desktop).
  stream="$(emulator_sink)"
  if [ -z "$stream" ]; then echo "no output stream for this emulator" >&2; return 1; fi
  timeout "$secs" parecord --monitor-stream="$stream" --file-format=wav --rate=16000 --channels=1 "$out"
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
  check) check ;;
  teardown) teardown ;;
  say) say "$2" ;;
  record) record "$2" "${3:-5}" ;;
  rms) rms "$2" ;;
  *) sed -n '2,20p' "$0"; exit 2 ;;
esac
