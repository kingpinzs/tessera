#!/usr/bin/env bash
. "$(dirname "$0")/t3.sh"; take_device_lock; leg f-big
df -h / | tail -1
wake_device; ensure_start; files_up paced big zips; echo "PASS=$PASS FAIL=$FAIL pace=$(pace_now)"; q "ls -la /sdcard/QA-Files/zips"
