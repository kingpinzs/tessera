#!/usr/bin/env bash
# E6 neg1 probe: "Open the pod." spoken three times through the emulator route; what does the recogniser return each time,
# and with which hotwords? (Three runs in a row since 21:20 heard a tail: "AND THEN" / "AND".)
export ANDROID_SERIAL=emulator-5554 AUDIO_ROUTE=emu
OUT="$(cd "$(dirname "$0")" && pwd)"
. "$OUT/../../scripts/lib.sh"; . "$QA/scripts/p14.sh"
ROW_DIR="$OUT"; take_device_lock
for i in 1 2 3; do
  ensure_start >/dev/null 2>&1; tess_open
  M="$(ring_mark)"
  SPEAK_SPEECH_OUT="$OUT/speech-$i.txt" "$P03S/speak.sh" pod_bay_neg1 9 > "$OUT/final-$i.txt" 2> "$OUT/err-$i.txt"; echo "run $i rc=$? $(cat "$OUT/final-$i.txt")"
  grep -E "asr: (levels|listen|hotwords|start)" "$OUT/speech-$i.txt" | sed 's/.*\[speech\] //' | cut -c1-200
  cortana_close; sleep 1
done
md5sum "$(python3 "$P03S/utterances.py" path pod_bay_neg1)" | cut -c1-12
git -C "$REPO" ls-files -s docs/plan/qa/phase-03/utterances/pod_bay_neg1.wav | cut -c1-60
git -C "$REPO" hash-object "$(python3 "$P03S/utterances.py" path pod_bay_neg1)"
ensure_start >/dev/null 2>&1
