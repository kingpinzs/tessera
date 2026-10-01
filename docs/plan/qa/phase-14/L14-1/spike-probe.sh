#!/usr/bin/env bash
# L14-1 spike: the Unlock card's button -> is the PIN pad up and reachable? -> PIN -> does the request run (doors line,
# pod bay opens)? Whole device log kept. Not a gate row: it tests a premise for the fix plan.
export ANDROID_SERIAL=emulator-5554 AUDIO_ROUTE=emu
QROOT="$(cd "$(dirname "$0")/../.." && pwd)"
. "$QROOT/phase-03/scripts/lib.sh"
OUT="$(cd "$(dirname "$0")" && pwd)/${1:?run name}"; mkdir -p "$OUT"; ROW_DIR="$OUT"
take_device_lock
kg() { adb shell dumpsys window | grep -m1 -oE 'isKeyguardShowing=(true|false)'; }
focus() { adb shell dumpsys window | grep -m1 'mCurrentFocus' | sed 's/^ *//'; }
trap 'adb shell locksettings clear --old 1234 >/dev/null 2>&1; adb shell locksettings set-disabled true >/dev/null 2>&1; adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard' EXIT
{ echo "apk installed $(installed_apk_id)"; echo "apk match $(apk_matches)"; } | tee "$OUT/steps.txt"
wake_device >/dev/null
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell locksettings set-disabled false; adb shell locksettings set-pin 1234
adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 3
echo "locked: $(kg)" | tee -a "$OUT/steps.txt"
adb shell input keyevent KEYCODE_ASSIST; sleep 5
"$QROOT/phase-03/scripts/speak.sh" pod_bay_doors 11 >/dev/null 2>&1; echo "speak rc=$?" | tee -a "$OUT/steps.txt"
for i in 1 2 3 4 5 6; do dump_ui "$OUT/card.xml"; [ "$(has_node "$OUT/card.xml" cortana_card_button:unlock)" = yes ] && break; sleep 2; done
echo "card button: $(has_node "$OUT/card.xml" cortana_card_button:unlock)" | tee -a "$OUT/steps.txt"
adb logcat -b all -c
M="$(ring_mark)"
tap_node "$OUT/card.xml" cortana_card_button:unlock
sleep 3
echo "after Unlock: $(kg) focus: $(focus)" | tee -a "$OUT/steps.txt"
screencap "$OUT/1-after-unlock-button.png"; dump_ui "$OUT/1-after-unlock-button.xml"
if [ "${2:-}" = cancel ]; then
  adb shell input keyevent KEYCODE_BACK; sleep 3
  echo "after Back on the PIN pad: $(kg) focus: $(focus)" | tee -a "$OUT/steps.txt"
  screencap "$OUT/1b-after-back.png"; dump_ui "$OUT/1b-after-back.xml"
  echo "card still up: $(has_node "$OUT/1b-after-back.xml" cortana_card_button:unlock) session: $(has_node "$OUT/1b-after-back.xml" cortana_session)" | tee -a "$OUT/steps.txt"
  tap_node "$OUT/1b-after-back.xml" cortana_card_button:unlock; sleep 3
  echo "after the second Unlock: $(kg) focus: $(focus)" | tee -a "$OUT/steps.txt"
  dump_ui "$OUT/1c-second-unlock.xml"
fi
adb shell input text 1234; sleep 1
screencap "$OUT/2-pin-typed.png"
adb shell input keyevent KEYCODE_ENTER; sleep 14
echo "after PIN: $(kg) focus: $(focus)" | tee -a "$OUT/steps.txt"
screencap "$OUT/3-after-pin.png"; dump_ui "$OUT/3-after-pin.xml"
adb logcat -b all -d -v threadtime > "$OUT/logcat-all.txt"
ring_since "$M" > "$OUT/app-ring.txt"
echo "reply since tap: [$(reply_since "$M")]" | tee -a "$OUT/steps.txt"
echo "pod bay opened: $(grep -c '\[podbay\] opened' "$OUT/app-ring.txt")" | tee -a "$OUT/steps.txt"
echo "done: $OUT"
