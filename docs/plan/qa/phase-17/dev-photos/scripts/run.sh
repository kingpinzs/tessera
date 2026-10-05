#!/usr/bin/env bash
# Runs one dev-photos script, waiting its turn for the shared device: a script that cannot take the device lock exits 3
# at once (before it touches anything), and is tried again after a wait. Never removes or bypasses the lock.
# usage: run.sh <script> [out file]
SCRIPT="$1"; OUT="${2:-/dev/stdout}"
for try in $(seq 1 90); do
  bash "$SCRIPT" > "$OUT.tmp" 2>&1; rc=$?
  if [ "$rc" != 3 ] || ! grep -q 'another QA driver is already driving the device' "$OUT.tmp"; then
    mv "$OUT.tmp" "$OUT"; echo "$rc" > "$OUT.rc"; exit "$rc"
  fi
  sleep $((12 + RANDOM % 15))
done
mv "$OUT.tmp" "$OUT"; echo 3 > "$OUT.rc"; exit 3
