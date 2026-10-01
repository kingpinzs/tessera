#!/usr/bin/env bash
# Runs a dev-people driver on the shared emulator: when another driver holds the device lock (the driver exits 3,
# lib.sh's take_device_lock) it waits and tries again — the lock is never removed or bypassed.
script="$1"; shift
for i in $(seq 1 30); do
  "$(dirname "$0")/$script" "$@"; rc=$?
  [ "$rc" -ne 3 ] && exit "$rc"
  echo "(the device is in use; waiting, attempt $i)" >&2
  sleep 40
done
exit 3
