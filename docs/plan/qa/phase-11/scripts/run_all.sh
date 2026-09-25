#!/usr/bin/env bash
# The phase 11 gate, end to end on ONE APK: unit tests first, then every row in order, E14 last (it reads the
# rings the others saved on this build). Every row's exit code is recorded; the suite fails if any row fails.
# usage: run_all.sh [row ...]    (default e1 … e16, l11_1, edge, with e14 last)
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../../.." && pwd)"
OUT="$HERE/.."
SUMMARY="$OUT/SUITE.txt"
say() { echo "$*" | tee -a "$SUMMARY"; }
# --append <rows>: re-run named rows on the SAME APK into a dated section of the same SUITE.txt (a harness fix after a
# full pass), with the drivers' blobs recorded; the verdict is then recomputed from each row's LATEST line.
if [ "${1:-}" = "--append" ]; then
  shift
  say ""
  say "## re-run $(date -Iseconds): commit $(git -C "$ROOT" rev-parse --short HEAD); apk $(md5sum "$ROOT/app/build/outputs/apk/debug/app-debug.apk" | cut -c1-16); rows: $*"
  for r in "$@"; do say "   $r.sh blob $(git -C "$ROOT" hash-object "$HERE/$r.sh")"; done
  for r in "$@"; do
    R="$(case "$r" in l11_1) echo L11-1 ;; edge) echo EDGE ;; *) echo "$r" | tr 'e' 'E' ;; esac)"; rm -rf "$OUT/$R"; START=$(date +%s)
    bash "$HERE/$r.sh" > "$OUT/.$r.console" 2>&1; RC=$?
    say "$R exit $RC after $(( $(date +%s) - START ))s — $(grep -h "^$R: " "$OUT/$R/$R.txt" 2>/dev/null | tail -1)"
  done
  python3 - "$SUMMARY" <<'PY' | tee -a "$SUMMARY"
import re, sys
latest = {}
for l in open(sys.argv[1]):
    m = re.match(r"(E\d+|L11-1|EDGE) exit (\d+)", l)
    if m: latest[m.group(1)] = int(m.group(2))
bad = sorted(r for r, rc in latest.items() if rc)
print("VERDICT (latest line per row): " + ("SUITE PASSED" if not bad else "SUITE FAILED: " + " ".join(bad)))
PY
  exit 0
fi
: > "$SUMMARY"
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
# Keep this run's own unit results before any row: E14 runs a filtered test task, which leaves only its own XML in
# build/test-results (the re-judge, R2-6). The L11-1 row reads TileSourcePrecedenceTest from here.
mkdir -p "$OUT/UNIT-results"; cp -f "$ROOT"/app/build/test-results/testDebugUnitTest/TEST-*.xml "$OUT/UNIT-results/"
say "unit results kept: $(ls "$OUT/UNIT-results" | wc -l) files in UNIT-results/"
FAILED=(); [ "$URC" -eq 0 ] || FAILED+=(unit)
ROWS=${*:-"e1 e2 e3 e4 e5 e6 e7 e8 e9 e10 e11 e12 e13 e15 e16 l11_1 edge e14"}
rowid() { case "$1" in l11_1) echo L11-1 ;; edge) echo EDGE ;; *) echo "$1" | tr 'e' 'E' ;; esac; }
for r in $ROWS; do
  R="$(rowid "$r")"
  rm -rf "$OUT/$R"
  START=$(date +%s)
  bash "$HERE/$r.sh" > "$OUT/.$r.console" 2>&1
  RC=$?
  say "$R exit $RC after $(( $(date +%s) - START ))s — $(grep -h "^$R: " "$OUT/$R/$R.txt" 2>/dev/null | tail -1)"
  [ "$RC" -eq 0 ] || FAILED+=("$R")
done
if [ ${#FAILED[@]} -eq 0 ]; then say "SUITE PASSED"; else say "SUITE FAILED: ${FAILED[*]}"; fi
[ ${#FAILED[@]} -eq 0 ]
