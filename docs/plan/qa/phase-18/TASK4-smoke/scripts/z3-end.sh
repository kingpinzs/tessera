#!/usr/bin/env bash
# /sdcard/qa.xml WAS on the device before the first fixture (the drivers' dump file): one dump puts it back; then the
# listing is compared with the first snapshot and the end state read.
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg z-final
ensure_start; D 03-end; S 03-end
FIRST="$(ls -d "$T4"/a-e6-run* | sort -V | head -1)/snap-sdcard-before.txt"
assert_eq "shared storage equals the snapshot taken before the first fixture" "" "$(diff "$FIRST" <(_snap_sdcard))"
assert_eq "top: Start" "app.tileshell/.StartActivity" "$(top_activity)"
assert_eq "installed = built" "yes" "$(apk_matches | cut -d' ' -f1)"
assert_contains "appop allow" "MANAGE_EXTERNAL_STORAGE: allow" "$(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE')"
assert_eq "test apps: qa-capture gone" "" "$(q "pm path $QAC")"
assert_eq "layout = the one saved at the start (order, dock, slots)" "same" "$(layout_json | python3 -c "
import json,sys
a=json.load(open(sys.argv[1])); b=json.load(sys.stdin)
k=lambda d:([o['key'] for o in d['order']], d['dock'], d.get('slots'), d.get('folders'))
print('same' if k(a)==k(b) else 'differs')" "$T4/00-begin/layout-at-begin.json")"
leg_end
