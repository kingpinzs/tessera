#!/usr/bin/env bash
# Host load, confined to this investigation's emulator: its qemu threads and stress-ng share logical CPUs 0-5
# (emulator-5554, the other session's, stays unpinned). Restores the emulator's affinity afterwards.
# run_load.sh <outdir> <n per gap> [gaps...]
E="$(cd "$(dirname "$0")" && pwd)"
EPID="$(cat /tmp/claude-1000/l13-3-tmp/emulator.pid)"
taskset -a -p -c 0-5 "$EPID" >/dev/null
taskset -c 0-5 stress-ng --cpu 6 --timeout 1500s >/dev/null 2>&1 &
SP=$!
sleep 5
OUT="$1"; N="$2"; shift 2
mkdir -p "$OUT"
echo "load: emulator pid $EPID pinned to CPUs 0-5; stress-ng --cpu 6 pinned to 0-5 (pid $SP)" >> "$OUT/SUMMARY.txt"
HL="${HL:-3}" bash "$E/l13_3.sh" sweep "$OUT" "$N" "$@"
kill "$SP" 2>/dev/null; wait "$SP" 2>/dev/null
taskset -a -p -c 0-31 "$EPID" >/dev/null
echo "load removed; emulator affinity restored to 0-31" >> "$OUT/SUMMARY.txt"
echo LOAD DONE
