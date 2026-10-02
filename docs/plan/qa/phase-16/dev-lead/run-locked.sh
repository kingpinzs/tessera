#!/usr/bin/env bash
# The lead's runner while the two row writers share the emulator: WAIT for the device lock instead of refusing.
#   run-locked.sh <out-file> <rc-file> <driver> [args…]
# lib.sh's take_device_lock refuses when the lock is held (flock -n). This holds the REAL lock, blocking, on fd 8 for
# the driver's whole run, and points the driver's own non-blocking lock at a private file (TMPDIR), so one driver
# at a time still drives the device. The adb server must already be running (a server started under this would
# inherit fd 8 and keep the lock).
set -u
OUT="$1"; RC="$2"; shift 2
adb start-server > /dev/null 2>&1 8>&-
exec 8> /tmp/tileshell-qa-device.lock
flock 8
PRIV="$(mktemp -d "${LEAD_TMP:-/tmp}/tileshell-lead-lock.XXXXXX")"
echo "lock taken at $(date -Is); private lock dir $PRIV" > "$OUT"
TMPDIR="$PRIV" bash "$@" >> "$OUT" 2>&1
echo $? > "$RC"
