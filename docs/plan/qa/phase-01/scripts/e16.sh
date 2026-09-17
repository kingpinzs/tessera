#!/usr/bin/env bash
# E16: calls that must be rejected - the shell uid through content call, one app targeting another app's tile, and a shared-uid pair.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E16.txt
echo "# E16 $(date -Iseconds)" > "$LOG"
echo '## adb shell (shell uid) calling the provider directly' >> "$LOG"
echo '$ adb shell content call --uri content://app.tileshell.livetile --method tile.update --extra xml:s:...' >> "$LOG"
adb shell 'content call --uri content://app.tileshell.livetile --method tile.update --extra xml:s:"<tile><visual><binding template=\"TileMedium\"><text>from the shell</text></binding></visual></tile>"' >> "$LOG" 2>&1
echo '$ adb shell content call ... --method badge.update --extra value:i:9' >> "$LOG"
adb shell 'content call --uri content://app.tileshell.livetile --method badge.update --extra value:i:9' >> "$LOG" 2>&1
echo '## tileclient-b targeting tileclient-a (owner extra)' >> "$LOG"
adb shell 'am start -n app.tileshell.testclient.b/app.tileshell.testclient.VerbActivity --es verb owner.attack --es owner app.tileshell.testclient.a' >/dev/null 2>&1
sleep 3; adb shell logcat -d -s TileClient | tail -3 >> "$LOG"
echo '## shared-uid pair (tileclient-b and tileclient-b2 share app.tileshell.testclient.shared)' >> "$LOG"
for p in b b2; do
  adb shell "am start -n app.tileshell.testclient.$p/app.tileshell.testclient.VerbActivity --es verb tile.update --es text \"shared uid $p\"" >/dev/null 2>&1
  sleep 3; adb shell logcat -d -s TileClient | tail -1 >> "$LOG"
done
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[livetile\] (reject|accept)" | tail -8 >> "$LOG"
