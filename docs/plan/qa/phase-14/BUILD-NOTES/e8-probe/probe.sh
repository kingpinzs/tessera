#!/usr/bin/env bash
# E8 probe: the Unlock card's button -> the bouncer -> the PIN. Where does the PIN go? Screens at each step.
export ANDROID_SERIAL=emulator-5554 AUDIO_ROUTE=emu
QROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
. "$QROOT/phase-03/scripts/lib.sh"
OUT="$(cd "$(dirname "$0")" && pwd)"; ROW_DIR="$OUT"
kg() { adb shell dumpsys window | grep -m1 -oE 'isKeyguardShowing=(true|false)'; }
focus() { adb shell dumpsys window | grep -m1 'mCurrentFocus' | sed 's/^ *//'; }
trap 'adb shell locksettings clear --old 1234 >/dev/null 2>&1; adb shell locksettings set-disabled true >/dev/null 2>&1; adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard' EXIT
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell locksettings set-disabled false; adb shell locksettings set-pin 1234
adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 3
echo "locked: $(kg)"
adb shell input keyevent KEYCODE_ASSIST; sleep 5
dump_ui "$OUT/tess.xml"; echo "tess: $(has_node "$OUT/tess.xml" cortana_session)"
"$QROOT/phase-03/scripts/speak.sh" pod_bay_doors 11 >/dev/null 2>&1; echo "speak rc=$?"
for i in 1 2 3 4 5 6; do dump_ui "$OUT/card.xml"; [ "$(has_node "$OUT/card.xml" cortana_card:unlock)" = yes ] && break; sleep 2; done
echo "card: $(has_node "$OUT/card.xml" cortana_card:unlock); card nodes: $(grep -oE 'resource-id="cortana_card[^"]*"' "$OUT/card.xml" | tr '\n' ' ')"
screencap "$OUT/0-card.png"
tap_node "$OUT/card.xml" cortana_card_button:unlock; sleep 3
screencap "$OUT/1-bouncer.png"; echo "after Unlock: $(kg) focus: $(focus)"
adb shell input text 1234; sleep 1
screencap "$OUT/2-pin-typed.png"; echo "after PIN: focus: $(focus)"
adb shell input keyevent KEYCODE_ENTER; sleep 4
screencap "$OUT/3-after-enter.png"; echo "after Enter: $(kg) focus: $(focus)"
