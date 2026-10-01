#!/usr/bin/env bash
# Runs one development session, waiting for the shared emulator's lock: a session that finds the lock held exits 3
# ("another QA driver is already driving the device"), and this waits 20 s and runs it again — never removing or
# bypassing the lock. Usage: run.sh <session.sh> [max-tries]
here="$(cd "$(dirname "$0")" && pwd)"
tries="${2:-80}"
for i in $(seq 1 "$tries"); do
  bash "$here/$1"; rc=$?
  [ "$rc" != 3 ] && exit "$rc"
  echo "(the device lock is held; try $i of $tries, waiting 20 s)"
  sleep 20
done
echo "the device lock was held for all $tries tries"; exit 3
