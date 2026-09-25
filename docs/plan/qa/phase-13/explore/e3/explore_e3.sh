#!/usr/bin/env bash
# Exploration only (not row evidence): the E3 fixture's geometry, so the row's patches are placed from real dumps.
S="$(cd "$(dirname "$0")/../../scripts" && pwd)"
. "$S/lib.sh"; . "$S/p13.sh"; . "$S/reminders_fixture.sh"
take_device_lock
ROW_DIR="$(cd "$(dirname "$0")" && pwd)"; LOG="$ROW_DIR/explore.txt"; : > "$LOG"
wake_device
adb shell am force-stop $PKG; sleep 2; show_start 5
reminders_setup
echo "tomorrow=$REM_TOMORROW photo=$REM_PHOTO"
cp "$ROW_DIR/reminders.xml" "$ROW_DIR/page.xml"
screencap "$ROW_DIR/page.png"
b="$(bounds "$ROW_DIR/page.xml" "reminder_row_title:$REM_TOMORROW")"; echo "tomorrow title $b"
set -- $b; adb shell input swipe $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) 1000
sleep 1.5; dump_ui "$ROW_DIR/menu1.xml"; screencap "$ROW_DIR/menu1.png"
echo "menu1 acrylic $(bounds "$ROW_DIR/menu1.xml" acrylic:reminder_menu) menu $(bounds "$ROW_DIR/menu1.xml" reminder_menu)"
adb shell input tap 1000 2000; sleep 1.5; screencap "$ROW_DIR/closed1.png"; dump_ui "$ROW_DIR/closed1.xml"
pb="$(bounds "$ROW_DIR/page.xml" "reminder_row_photo:$REM_PHOTO")"; echo "photo $pb"
set -- $pb; adb shell input swipe $(( ($1+$3)/2 )) $(( $4-10 )) $(( ($1+$3)/2 )) $(( $4-10 )) 1000
sleep 1.5; dump_ui "$ROW_DIR/menu2.xml"; screencap "$ROW_DIR/menu2.png"
echo "menu2 acrylic $(bounds "$ROW_DIR/menu2.xml" acrylic:reminder_menu)"
adb shell input tap 1000 2000; sleep 1.5; screencap "$ROW_DIR/closed2.png"
cortana_close
