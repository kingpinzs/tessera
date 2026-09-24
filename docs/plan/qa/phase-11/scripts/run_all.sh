#!/usr/bin/env bash
# The phase 11 gate, end to end on ONE APK: unit tests first, then every row in order, E14 last (it reads the
# rings the others saved on this build). Every row's exit code is recorded; the suite fails if any row fails.
# usage: run_all.sh [row ...]    (default e1 … e16 with e14 last)
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../../.." && pwd)"
OUT="$HERE/.."
SUMMARY="$OUT/SUITE.txt"
: > "$SUMMARY"
say() { echo "$*" | tee -a "$SUMMARY"; }
say "# Phase 11 gate suite — $(date -Iseconds)"
say "commit $(git -C "$ROOT" rev-parse --short HEAD); uncommitted files under app/: $(git -C "$ROOT" status --porcelain app/ | wc -l)"
say "apk $(md5sum "$ROOT/app/build/outputs/apk/debug/app-debug.apk" | cut -c1-16); device $ANDROID_SERIAL"
( cd "$ROOT" && ./gradlew :app:testDebugUnitTest -q ) > "$OUT/UNIT.txt" 2>&1
URC=$?
python3 - "$ROOT" >> "$OUT/UNIT.txt" <<'PY'
import glob, re, sys
t = f = e = 0
for p in glob.glob(f"{sys.argv[1]}/app/build/test-results/testDebugUnitTest/TEST-*.xml"):
    m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', open(p).read())
    t += int(m.group(1)); f += int(m.group(2)); e += int(m.group(3))
print(f"TOTAL {t} tests, {f} failures, {e} errors")
PY
say "unit: $(tail -1 "$OUT/UNIT.txt") (gradle exit $URC)"
FAILED=(); [ "$URC" -eq 0 ] || FAILED+=(unit)
ROWS=${*:-"e1 e2 e3 e4 e5 e6 e7 e8 e9 e10 e11 e12 e13 e15 e16 e14"}
for r in $ROWS; do
  R="$(echo "$r" | tr 'e' 'E')"
  rm -rf "$OUT/$R"
  START=$(date +%s)
  bash "$HERE/$r.sh" > "$OUT/.$r.console" 2>&1
  RC=$?
  say "$R exit $RC after $(( $(date +%s) - START ))s — $(grep -h "^$R: " "$OUT/$R/$R.txt" 2>/dev/null | tail -1)"
  [ "$RC" -eq 0 ] || FAILED+=("$R")
done
if [ ${#FAILED[@]} -eq 0 ]; then say "SUITE PASSED"; else say "SUITE FAILED: ${FAILED[*]}"; fi
[ ${#FAILED[@]} -eq 0 ]
