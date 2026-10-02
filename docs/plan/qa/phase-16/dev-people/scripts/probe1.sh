#!/usr/bin/env bash
# Read-only probe: what the device shows now (after DEV-E12 run 2 ended off Start).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
take_device_lock
echo "at $(date -Is)"
echo "top: $(top_activity)"
S dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity" | head -5
S dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | head -3
S dumpsys power | grep -m1 mWakefulness
adb exec-out screencap -p > "$QA/probe1.png"
S dumpsys telecom | grep -cE 'Call id|mCallId'
adb emu gsm list
S dumpsys telecom | grep -iE "Analytics|TC@|failed|DISCONNECT" | tail -12
