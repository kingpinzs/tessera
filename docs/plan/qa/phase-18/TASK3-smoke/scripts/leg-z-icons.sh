#!/usr/bin/env bash
. "$(dirname "$0")/t3.sh"; take_device_lock; leg z-final
files_at $QF; D 20; T 20 files_bar:view 1.5; D 21; T 21 files_bar:select 1.2; D 22; T 22 files_row:a.txt 0.6; D 23-icons-sel; S 23-icons-sel
echo "icons check a.txt $(epx $(B 23-icons-sel files_check:a.txt)) icon $(epx $(B 23-icons-sel files_icon:a.txt))"
adb shell input keyevent KEYCODE_BACK; sleep 1; D 24; T 24 files_bar:view 1.2; echo crash=$(crash)
