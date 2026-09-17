#!/usr/bin/env bash
# E17 badge order: notification setNumber(7) -> legacy broadcast -> badge.update -> badge.clear falls back.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E17.txt; PKG=app.tileshell.testclient.a; TILE="badge:app:$PKG/app.tileshell.testclient.VerbActivity:0"
N="python3 $(dirname "$0")/nodes.py"
badge() { # badge <label>
  sleep 3; adb shell input keyevent KEYCODE_HOME; sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"; dump "$OUT/$1.xml" 2>/dev/null
  echo "$1: dump badge node [$($N "$OUT/$1.xml" "$TILE" | sed 's/.*texts=//')]; $(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep '\[badge\]' | tail -1 | sed 's/.*\[badge\] //')" >> "$LOG"
}
verb() { adb shell "am start -n $PKG/app.tileshell.testclient.VerbActivity $*" >/dev/null 2>&1; sleep 2; }
echo "# E17 $(date -Iseconds)" > "$LOG"
echo "notification listener connected: $(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -m1 -i 'listener')" >> "$LOG"
echo '## notification with setNumber(7)' >> "$LOG"; verb '--es verb notify --ei number 7 --es title "Seven" --es text "badge test"'; badge notification_7
echo '## legacy badge broadcast (count 3)' >> "$LOG"; verb '--es verb legacyBadge --ei count 3'; badge legacy_3
echo '## badge.update 5 through the API' >> "$LOG"; verb '--es verb badge.update --ei value 5'; badge api_5
echo '## badge.clear (falls back to the legacy broadcast, then the notification)' >> "$LOG"; verb '--es verb badge.clear'; badge after_api_clear
echo '## legacy broadcast cleared (count 0)' >> "$LOG"; verb '--es verb legacyBadge --ei count 0'; badge after_legacy_clear
echo '## notification cancelled' >> "$LOG"; verb '--es verb notify.cancel'; badge after_notification_cancel
