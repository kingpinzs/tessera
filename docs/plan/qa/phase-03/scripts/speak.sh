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

timeout 40 "$HERE/audio.sh" say "$wav"
sleep "$settle"

speech_dump | grep -F '[speech] asr: final' | tail -1 | sed 's/.*open="/open="/'
