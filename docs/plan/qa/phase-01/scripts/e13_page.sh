#!/usr/bin/env bash
# e13_page.sh <out prefix>: open Settings > Start at the top, dump every screen of it while scrolling down.
source "$(dirname "$0")/ui.sh"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page HOME >/dev/null 2>&1; sleep 1
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page START_THEME >/dev/null 2>&1; sleep 3
for i in 1 2 3 4; do
  dump "$1_$i.xml"
  adb shell input swipe 540 1800 540 700 300; sleep 1.2
done
