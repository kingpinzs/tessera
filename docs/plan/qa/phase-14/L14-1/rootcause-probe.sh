#!/usr/bin/env bash
# L14-1 root-cause probe: the Unlock card's button, with the WHOLE device log (system_server + SystemUI), so the
# cancel of requestDismissKeyguard and the session's hide can each be traced to whoever caused them.
export ANDROID_SERIAL=emulator-5554 AUDIO_ROUTE=emu
QROOT="$(cd "$(dirname "$0")/../.." && pwd)"
. "$QROOT/phase-03/scripts/lib.sh"
OUT="$(cd "$(dirname "$0")" && pwd)/${1:-rootcause-run1}"; mkdir -p "$OUT"; ROW_DIR="$OUT"
take_device_lock
kg() { adb shell dumpsys window | grep -m1 -oE 'isKeyguardShowing=(true|false)'; }
trap 'adb shell locksettings clear --old 1234 >/dev/null 2>&1; adb shell locksettings set-disabled true >/dev/null 2>&1; adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard' EXIT
wake_device >/dev/null
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell locksettings set-disabled false; adb shell locksettings set-pin 1234
adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 3
echo "locked: $(kg)" | tee "$OUT/steps.txt"
adb shell input keyevent KEYCODE_ASSIST; sleep 5
"$QROOT/phase-03/scripts/speak.sh" pod_bay_doors 11 >/dev/null 2>&1; echo "speak rc=$?" | tee -a "$OUT/steps.txt"
for i in 1 2 3 4 5 6; do dump_ui "$OUT/card.xml"; [ "$(has_node "$OUT/card.xml" cortana_card_button:unlock)" = yes ] && break; sleep 2; done
echo "card button: $(has_node "$OUT/card.xml" cortana_card_button:unlock)" | tee -a "$OUT/steps.txt"
adb logcat -b all -c
M="$(ring_mark)"
tap_node "$OUT/card.xml" cortana_card_button:unlock
sleep 1; adb shell dumpsys activity activities > "$OUT/activities-1s.txt"; adb shell dumpsys window windows > "$OUT/windows-1s.txt"
sleep 4
echo "after Unlock: $(kg)" | tee -a "$OUT/steps.txt"
screencap "$OUT/after-unlock.png"
adb logcat -b all -d -v threadtime > "$OUT/logcat-all.txt"
ring_since "$M" > "$OUT/app-ring.txt"
echo "done: $OUT"
