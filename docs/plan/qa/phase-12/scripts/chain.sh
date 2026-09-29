#!/usr/bin/env bash
# Runs the named rows one after another; each row's exit code goes to <row>.rc beside its log (Hard Rule 14).
cd "$(dirname "$0")/.."
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
for r in "$@"; do
  s="scripts/$(echo "$r" | tr 'A-Z' 'a-z').sh"
  echo "== $r $(date +%T)"
  bash "$s" > "/tmp/claude-1000/-home-jeremyking/3f8d6de8-cd6e-4f28-871d-f772ac673643/scratchpad/p12-$r.out" 2>&1
  rc=$?
  mkdir -p "$r"; echo "$rc" > "$r/$r.rc"
  echo "   rc=$rc $(tail -1 "$r/$r.txt" 2>/dev/null)"
done
