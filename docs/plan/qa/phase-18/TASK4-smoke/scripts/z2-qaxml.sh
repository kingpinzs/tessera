#!/usr/bin/env bash
# The drivers' own dump file, made again by z-final's last dumps: removed, and the listing compared once more.
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg z-final
adb shell rm -f /sdcard/qa.xml
FIRST="$(ls -d "$T4"/a-e6-run* | sort -V | head -1)/snap-sdcard-before.txt"
assert_eq "shared storage equals the first snapshot" "" "$(diff "$FIRST" <(_snap_sdcard))"
assert_eq "top: Start" "app.tileshell/.StartActivity" "$(top_activity)"
leg_end
