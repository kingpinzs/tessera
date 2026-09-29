#!/usr/bin/env bash
# Runs the named edge cases one after another (edge.sh <case> each); each case's exit code goes to EDGE_<CASE>/EDGE_<CASE>.rc.
cd "$(dirname "$0")/.."
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
for c in "$@"; do
  C="EDGE_$(echo "$c" | tr 'a-z' 'A-Z')"
  echo "== $C $(date +%T)"
  bash scripts/edge.sh "$c" > "/tmp/claude-1000/-home-jeremyking/3f8d6de8-cd6e-4f28-871d-f772ac673643/scratchpad/p12-$C.out" 2>&1
  rc=$?
  mkdir -p "$C"; echo "$rc" > "$C/$C.rc"
  echo "   rc=$rc $(tail -1 "$C/$C.txt" 2>/dev/null)"
done
