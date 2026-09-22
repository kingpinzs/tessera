#!/usr/bin/env bash
# The phase 02 gate, end to end. Every row is a driver that exits non-zero when any of its checks fail, and
# this runner fails if any row fails (gate finding: nothing gated on an exit code, so a red row read green in
# a summary). Unit tests run first and their exit code counts.
# usage: run_all.sh [row ...]     (default: all of them, in order)
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
OUTROOT="$HERE/.."
SUMMARY=$OUTROOT/SUITE.txt
: > "$SUMMARY"
note() { echo "$*" | tee -a "$SUMMARY"; }
FAILED=()

note "# Phase 02 gate suite — $(date -Iseconds)"
note "commit $(git -C "$ROOT" rev-parse --short HEAD)  branch $(git -C "$ROOT" rev-parse --abbrev-ref HEAD)"
note ""

note "## Unit tests"
( cd "$ROOT" && ./gradlew :app:testDebugUnitTest -q ) > "$OUTROOT/UNIT.txt" 2>&1
UNIT_RC=$?
python3 - "$ROOT" >> "$OUTROOT/UNIT.txt" <<'PY'
import glob, re, sys
t = f = e = 0
for p in glob.glob(f"{sys.argv[1]}/app/build/test-results/testDebugUnitTest/TEST-*.xml"):
    m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', open(p).read())
    t += int(m.group(1)); f += int(m.group(2)); e += int(m.group(3))
print(f"TOTAL {t} tests, {f} failures, {e} errors")
PY
note "$(tail -1 "$OUTROOT/UNIT.txt")  (gradle exit $UNIT_RC)"
[ "$UNIT_RC" -eq 0 ] || FAILED+=("unit")

ROWS=${*:-"regress e1 e2e6 e3 e4 e5 e7 e8 e9 edge"}
for ROW in $ROWS; do
  case $ROW in
    regress) DIR=REGRESS; CMD=("$HERE/regress.sh" "$OUTROOT/REGRESS") ;;
    e1)      DIR=E01;     CMD=("$HERE/e1.sh" "$OUTROOT/E01") ;;
    e2e6)    DIR=E02;     CMD=("$HERE/e2e6.sh" "$OUTROOT/E02") ;;
    e3)      DIR=E03;     CMD=("$HERE/e3.sh" "$OUTROOT/E03") ;;
    e4)      DIR=E04;     CMD=("$HERE/e4.sh" "$OUTROOT/E04") ;;
    e5)      DIR=E05;     CMD=("$HERE/e5.sh" "$OUTROOT/E05") ;;
    e7)      DIR=E07;     CMD=("$HERE/e7.sh" "$OUTROOT/E07" slot:PEOPLE) ;;
    e8)      DIR=E08;     CMD=("$HERE/e8.sh" "$OUTROOT/E08") ;;
    e9)      DIR=E09;     CMD=("$HERE/e9.sh" "$OUTROOT/E09") ;;
    edge)    DIR=EDGE;    CMD=("$HERE/edge.sh" "$OUTROOT/EDGE") ;;
    *) note "unknown row $ROW"; continue ;;
  esac
  note ""
  note "## $ROW -> $DIR"
  START=$(date +%s)
  ( cd "$ROOT" && bash "${CMD[@]}" ) >/dev/null 2>&1
  RC=$?
  note "$ROW exit $RC after $(( $(date +%s) - START ))s — verdict line: $(grep -h '^---- ' "$OUTROOT/$DIR"/*.txt 2>/dev/null | tail -1)"
  [ "$RC" -eq 0 ] || FAILED+=("$ROW")
done

note ""
if [ ${#FAILED[@]} -eq 0 ]; then
  note "SUITE PASSED"
else
  note "SUITE FAILED: ${FAILED[*]}"
fi
[ ${#FAILED[@]} -eq 0 ]
