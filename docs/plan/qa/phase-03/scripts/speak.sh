#!/usr/bin/env bash
# One spoken request, start to finish, as every audio row needs it.
#
#   speak.sh <utterance-id> [settle-seconds]
#
# The order matters and is the reason this is one script rather than three calls in each driver: the
# recogniser endpoints on trailing silence, so the utterance has to start the moment listening does. A
# driver that dumps the UI between the two loses the request to the endpoint (found the hard way: a
# probe that read the state first heard "AND" instead of the command).
#
# Cortana must already be open. It prints the transcript it produced, so a driver can assert on it.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"

utterance="${1:?usage: speak.sh <utterance-id> [settle]}"
settle="${2:-9}"
wav="$(python3 "$HERE/utterances.py" path "$utterance")"
[ -f "$wav" ] || { echo "missing utterance $utterance — run utterances.py build" >&2; exit 2; }

dump="${ROW_DIR:-/tmp}/.speak.xml"
dump_ui "$dump" || true

# Listening starts either from the mic button on the text box, or the box is already listening.
if [ "$(has_node "$dump" cortana_listening_box)" != yes ]; then
  b="$(bounds "$dump" cortana_text_box_mic)"
  if [ -z "$b" ]; then
    echo "speak.sh: Cortana is not on a page with a microphone" >&2
    exit 2
  fi
  # shellcheck disable=SC2086
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
fi

# Only a final made AFTER this point counts. Without it a driver that produced no recognition at all reads the
# PREVIOUS utterance's result and records a verdict about something it never said — which is exactly what happened
# once here ("CALL MA'AM" reported for a text command). Phase 14: matched by the lines' own wall= stamps against this
# mark (the device's clock), not by counting lines — a count stands still once the ring is full.
speak_mark="$(ring_mark)"

# AUDIO_ROUTE=emu (phase 14 Q-R3-1a (a)): the utterance goes in through the emulator's own gRPC injectAudio and the
# host's audio is never touched. A failed injection is exit 5 with its reason, never a silent "no final".
if [ "${AUDIO_ROUTE:-}" = emu ]; then
  timeout 40 python3 "$HERE/emu_audio.py" say "$wav" >&2
  rc=$?
  if [ "$rc" -ne 0 ]; then
    echo "speak.sh: emu_audio.py say failed for '$utterance' (rc $rc)" >&2
    exit 5
  fi
else
  timeout 40 "$HERE/audio.sh" say "$wav"
fi
# The final is caught DURING the settle (phase 14): a request that closes Tess — an app opened, the pod bay — unbinds
# the speech service with her, and its ring cannot be dumped any more ("No services match"), so a read after the
# settle found nothing for exactly those requests (phase 14 E6 run 1). The :speech ring as it stood at that moment is
# kept in $SPEAK_SPEECH_OUT when a driver names one (phase 14's C-30 check reads it).
deadline=$(( $(date +%s) + settle ))
caught=""
while [ "$(date +%s)" -lt "$deadline" ]; do
  now_dump="$(ring_since "$speak_mark" speech)"
  if printf '%s\n' "$now_dump" | grep -qF '[speech] asr: final'; then caught="$now_dump"; break; fi
  sleep 0.5
done
[ -n "${SPEAK_SPEECH_OUT:-}" ] && printf '%s\n' "$caught" > "$SPEAK_SPEECH_OUT"
remaining=$(( deadline - $(date +%s) ))
[ "$remaining" -gt 0 ] && sleep "$remaining"

if [ -z "$caught" ]; then
  echo "NO NEW FINAL: the recogniser produced nothing for '$utterance' (no asr: final stamped after the mark)" >&2
  exit 4
fi

printf '%s\n' "$caught" | grep -F '[speech] asr: final' | tail -1 | sed 's/.*open="/open="/'
