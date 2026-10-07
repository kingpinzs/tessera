#!/usr/bin/env bash
# Phase 20 — is a fixture reachable FROM THE EMULATOR at 10.0.2.2:<port>? (toybox nc on the AVD; no app involved)
# usage: emu_reach.sh <out dir> <port> <path> [bytes of the answer to keep=600]
set -uo pipefail
export ANDROID_SERIAL=emulator-5554 PATH="$HOME/Android/Sdk/platform-tools:$PATH"
OUT="${1:?out dir}"; PORT="${2:?port}"; WHAT="${3:?path}"; KEEP="${4:-600}"; mkdir -p "$OUT"
stem="$OUT/emu-$PORT$(echo "$WHAT" | tr -c 'A-Za-z0-9\n' '_' | cut -c1-40)"
ESC="${WHAT//%/%%}"   # the path goes through the device's printf: a literal % must be doubled
adb shell "printf 'GET $ESC HTTP/1.0\r\nHost: 10.0.2.2:$PORT\r\nUser-Agent: Tessera/0-smoke (emulator nc; not the app)\r\n\r\n' | toybox timeout 6 nc -w 5 10.0.2.2 $PORT | head -c $KEEP" > "$stem.txt" 2> "$stem.err"
echo $? > "$stem.rc"
echo "10.0.2.2:$PORT$WHAT from the emulator: adb rc $(cat "$stem.rc"), $(wc -c < "$stem.txt") B; first line: $(head -1 "$stem.txt" | tr -d '\r')"
