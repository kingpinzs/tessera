#!/usr/bin/env bash
# Puts the copy made by the unpaced run in the bin (through the app), then restarts the shell so it reads the pace pref.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg f-big
bin_it $QF/sub big.bin; q "ls -a /sdcard/QA-Files/sub"
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 0.5; adb shell am force-stop app.tileshell; sleep 1; ensure_start; echo "pace=$(pace_now)"
