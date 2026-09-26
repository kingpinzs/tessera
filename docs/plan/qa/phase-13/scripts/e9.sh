#!/usr/bin/env bash
# E9 Frame cost on the AVD, recorded not gated (phase 13 Acceptance E9, C-13, C-26 — never an assert; the row ends
# `E9: recorded only (<n> facts)`): gfxinfo reset; the reminder menu opened and closed 20 times; Start <-> app list swiped
# 20 times with live tiles flipping; janky-frame % and the 99th percentile recorded (host GPU; the phone's P2 is the bound).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$(dirname "$0")/reminders_fixture.sh"

row_begin E9 "frame cost: the live source (reminder menu x20) and the static source (pivot x20), recorded"
wake_device >/dev/null
set_pref transparency_effects boolean true
set_checker
show_start 7
make_typed_reminder "remind me to check the QA13 cost list tomorrow at 9 am"
open_reminders
ID="$(reminder_id "QA13 cost")"
set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row:$ID"); RX=$(( ($1 + $3) / 2 )); RY=$(( ($2 + $4) / 2 ))
adb shell dumpsys gfxinfo $PKG reset >/dev/null
for _ in $(seq 1 20); do
  adb shell input swipe $RX $RY $RX $RY 1000; sleep 1
  adb shell input tap 1000 1500; sleep 0.8
done
cortana_close
show_start 4
for _ in $(seq 1 10); do to_app_list 1.2; to_start 1.2; done
adb shell dumpsys gfxinfo $PKG > "$ROW_DIR/gfxinfo.txt"
record "total frames rendered" "$(grep -m1 'Total frames rendered' "$ROW_DIR/gfxinfo.txt" | awk -F': ' '{print $2}' | tr -d '\r')"
record "janky frames" "$(grep -m1 'Janky frames:' "$ROW_DIR/gfxinfo.txt" | awk -F': ' '{print $2}' | tr -d '\r')"
record "99th percentile" "$(grep -m1 '99th percentile' "$ROW_DIR/gfxinfo.txt" | awk -F': ' '{print $2}' | tr -d '\r')"
record "90th percentile" "$(grep -m1 '90th percentile' "$ROW_DIR/gfxinfo.txt" | awk -F': ' '{print $2}' | tr -d '\r')"
# restore (not graded)
open_reminders
set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row:$ID")
[ -n "$ID" ] && delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
cortana_close
clear_background
show_start 3
row_end
