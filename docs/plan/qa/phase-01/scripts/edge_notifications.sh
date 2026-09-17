#!/usr/bin/env bash
# Edge cases: notification-derived previews and counts (Launcher3 eligibility rules).
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/edge_notifications.txt; PKG=app.tileshell.testclient.a
TILE="tile:app:$PKG/app.tileshell.testclient.VerbActivity:0"; BADGE="badge:app:$PKG/app.tileshell.testclient.VerbActivity:0"
notify() { adb shell "am start -n $PKG/app.tileshell.testclient.VerbActivity --es verb notify $*" >/dev/null 2>&1; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 3; }
state() { # state <label>
  adb exec-out screencap -p > "$OUT/notif_$1.png"
  local badge; badge=$(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep '\[badge\]' | tail -1 | sed 's/.*\[badge\] //')
  local content; content=$(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep '\[engine\] publish pkg:'$PKG | tail -1 | sed 's/.*publish //')
  echo "$1: $badge | $content" >> "$LOG"
}
echo "# Edge cases: notifications $(date -Iseconds)" > "$LOG"
echo '## a notification with no usable content (no title, no text)' >> "$LOG"
notify '--ez empty true --ei number 4 --ei id 21'; state empty
adb shell "am start -n $PKG/app.tileshell.testclient.VerbActivity --es verb notify.cancel --ei id 21" >/dev/null 2>&1; sleep 2
echo '## a group summary plus two children (the summary must not be counted)' >> "$LOG"
notify '--es group g1 --ez summary true --ei number 9 --ei id 31 --es title "Summary" --es text "two messages"'
notify '--es group g1 --ei number 1 --ei id 32 --es title "First" --es text "hello"'
notify '--es group g1 --ei number 1 --ei id 33 --es title "Second" --es text "there"'; state grouped
echo '## updated in place (same id, new text)' >> "$LOG"
notify '--es group g1 --ei number 1 --ei id 32 --es title "First" --es text "updated in place"'; state updated
echo '## dismissed (children cancelled)' >> "$LOG"
for id in 31 32 33; do adb shell "am start -n $PKG/app.tileshell.testclient.VerbActivity --es verb notify.cancel --ei id $id" >/dev/null 2>&1; sleep 1; done
adb shell input keyevent KEYCODE_HOME; sleep 3; state dismissed
echo '## ongoing (media / download style)' >> "$LOG"
notify '--ez ongoing true --ei number 5 --ei id 41 --es title "Ongoing" --es text "downloading"'; state ongoing
adb shell "am start -n $PKG/app.tileshell.testclient.VerbActivity --es verb notify.cancel --ei id 41" >/dev/null 2>&1; sleep 2
echo '## a channel with badges turned off' >> "$LOG"
notify '--ez badgeOff true --ei number 6 --ei id 51 --es title "No badge channel" --es text "quiet"'; state badge_off
adb shell "am start -n $PKG/app.tileshell.testclient.VerbActivity --es verb notify.cancel --ei id 51" >/dev/null 2>&1; sleep 2
echo '## sensitive (visibility secret) and private' >> "$LOG"
notify '--es visibility secret --ei number 2 --ei id 61 --es title "Secret" --es text "hidden content"'; state secret
notify '--es visibility private --ei number 2 --ei id 62 --es title "Private" --es text "private content"'; state private
for id in 61 62; do adb shell "am start -n $PKG/app.tileshell.testclient.VerbActivity --es verb notify.cancel --ei id $id" >/dev/null 2>&1; sleep 1; done
adb shell input keyevent KEYCODE_HOME; sleep 3; state none_left
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[notif\]" | tail -12 >> "$LOG"
