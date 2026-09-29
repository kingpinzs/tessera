#!/usr/bin/env bash
# run.sh — the one-command self-test of picture_oracle.py and lens_check.py (phase 12 build task 4 (ii) / (iii)).
#
#     bash docs/plan/qa/phase-12/scripts/selftest/run.sh [out-dir]
#
# Synthesises screenshots + dumps with make_start.py / make_lens.py, runs each checker against them with the
# expected exit code (0 PASS / 1 FAIL / 2 UNUSABLE), and also runs the two checkers on the REAL captures the repo
# already holds (phase 15 E1's Default-preset Start, phase 15 L13_10's HAL idle ring). Host-only: no adb.
# Exits 0 only when every case ends with the exit code it expects. Logs land in <out-dir> (default
# ${TMPDIR:-/tmp}/tileshell-p12-selftest).
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS="$(cd "$HERE/.." && pwd)"
QA="$(cd "$SCRIPTS/../.." && pwd)"
REPO="$(cd "$QA/../../.." && pwd)"
OUT="${1:-${TMPDIR:-/tmp}/tileshell-p12-selftest}"
mkdir -p "$OUT"
PIC="$REPO/app/src/main/res/drawable-nodpi/preset_hal.webp"
ORACLE="$SCRIPTS/picture_oracle.py"
LENS="$SCRIPTS/lens_check.py"
REAL_START="$QA/phase-15/E1/start_playing"
REAL_IDLE="$QA/phase-15/L13_10-drv3-before-8384ebd3/idle_ring"
BAD=0
N=0

case_run() { # name expected-rc command...
  local name="$1" want="$2"; shift 2
  N=$((N + 1))
  "$@" > "$OUT/$name.log" 2>&1
  local rc=$?
  local last; last="$(grep '^RESULT' "$OUT/$name.log" | tail -n1)"
  local mark="ok "
  if [ "$rc" != "$want" ]; then mark="BAD"; BAD=$((BAD + 1)); fi
  printf '%s %-34s want %s got %s  %s\n' "$mark" "$name" "$want" "$rc" "${last:-$(tail -n1 "$OUT/$name.log")}"
}

gen() { python3 "$HERE/make_start.py" --picture "$PIC" "$@" > /dev/null || { echo "make_start failed: $*" >&2; exit 3; }; }
genl() { python3 "$HERE/make_lens.py" "$@" > /dev/null || { echo "make_lens failed: $*" >&2; exit 3; }; }

echo "== synthetic Start (preset_hal.webp, accent E81123, alpha 0.72) -> $OUT"
gen --png "$OUT/start_ok.png" --xml "$OUT/start_ok.xml" --alpha 0.72
gen --png "$OUT/start_opaque.png" --xml "$OUT/start_opaque.xml" --opaque
gen --png "$OUT/start_shift30.png" --xml "$OUT/start_shift30.xml" --alpha 0.72 --shift-y 30
gen --png "$OUT/start_scale1.png" --xml "$OUT/start_scale1.xml" --alpha 0.72 --layer-scale 1.0
gen --png "$OUT/start_nopic.png" --xml "$OUT/start_nopic.xml" --no-picture --opaque

O="python3 $ORACLE --tile slot:PEOPLE --accent E81123"
case_run oracle-A-correct-alpha-0.72     0 $O --picture "$PIC" --alpha 0.72 --screenshot "$OUT/start_ok.png" --dump "$OUT/start_ok.xml" --out "$OUT/oracle-A.json"
case_run oracle-B-opaque-missing-picture 1 $O --picture "$PIC" --alpha 0.72 --screenshot "$OUT/start_opaque.png" --dump "$OUT/start_opaque.xml"
case_run oracle-C1-picture-shifted-30px  1 $O --picture "$PIC" --alpha 0.72 --screenshot "$OUT/start_shift30.png" --dump "$OUT/start_shift30.xml"
case_run oracle-C2-layer-scale-1.0       1 $O --picture "$PIC" --alpha 0.72 --screenshot "$OUT/start_scale1.png" --dump "$OUT/start_scale1.xml"
case_run oracle-D-wrong-alpha-literal    1 $O --picture "$PIC" --alpha 0.40 --screenshot "$OUT/start_ok.png" --dump "$OUT/start_ok.xml"
case_run oracle-E-midnight-expect-opaque 0 $O --picture "$PIC" --expect-opaque --screenshot "$OUT/start_opaque.png" --dump "$OUT/start_opaque.xml"
case_run oracle-F-translucent-vs-opaque  1 $O --picture "$PIC" --expect-opaque --screenshot "$OUT/start_ok.png" --dump "$OUT/start_ok.xml"
case_run oracle-G-default-no-picture     0 $O --no-picture --screenshot "$OUT/start_nopic.png" --dump "$OUT/start_nopic.xml"
case_run oracle-H-picture-but-no-picture 1 $O --no-picture --screenshot "$OUT/start_ok.png" --dump "$OUT/start_ok.xml"
if [ -f "$REAL_START.png" ] && [ -f "$REAL_START.xml" ]; then
  case_run oracle-R-real-default-capture 0 python3 "$ORACLE" --no-picture --tile slot:PEOPLE --accent 0078D7 --screenshot "$REAL_START.png" --dump "$REAL_START.xml"
fi

echo "== synthetic lens rings (HAL, Cobalt-rehued accent, hal_dim) and a listening disc"
genl --png "$OUT/lens_hal.png" --xml "$OUT/lens_hal.xml" --tones hal
genl --png "$OUT/lens_cobalt.png" --xml "$OUT/lens_cobalt.xml" --tones accent --accent 3E65FF
genl --png "$OUT/lens_dim.png" --xml "$OUT/lens_dim.xml" --tones hal_dim
genl --png "$OUT/disc_hal.png" --xml "$OUT/disc_hal.xml" --tones hal --form disc
L="python3 $LENS"
COBALT_HUE=227.9   # #3E65FF: 60 * ((62 - 101) / 193 + 4) = 227.88
case_run lens-A-hal-ring-hue-hal-b1.0    0 $L --screenshot "$OUT/lens_hal.png" --dump "$OUT/lens_hal.xml" --hue hal --brightness 1.0 --out "$OUT/lens-A.json"
case_run lens-B-cobalt-ring-hue-228-b1.0 0 $L --screenshot "$OUT/lens_cobalt.png" --dump "$OUT/lens_cobalt.xml" --hue $COBALT_HUE --brightness 1.0
case_run lens-C-dim-ring-hue-hal-b0.5    0 $L --screenshot "$OUT/lens_dim.png" --dump "$OUT/lens_dim.xml" --hue hal --brightness 0.5
case_run lens-N1-cobalt-vs-hal-red       1 $L --screenshot "$OUT/lens_cobalt.png" --dump "$OUT/lens_cobalt.xml" --hue hal --brightness 1.0
case_run lens-N2-midnight-vs-undimmed    1 $L --screenshot "$OUT/lens_dim.png" --dump "$OUT/lens_dim.xml" --hue hal --brightness 1.0
case_run lens-N3-hal-vs-dimmed           1 $L --screenshot "$OUT/lens_hal.png" --dump "$OUT/lens_hal.xml" --hue hal --brightness 0.5
case_run lens-N4-iris-required-ring      1 $L --screenshot "$OUT/lens_hal.png" --dump "$OUT/lens_hal.xml" --hue hal --brightness 1.0 --require iris
case_run lens-D-disc-hal                 0 $L --form disc --screenshot "$OUT/disc_hal.png" --dump "$OUT/disc_hal.xml" --hue hal --brightness 1.0
if [ -f "$REAL_IDLE.png" ] && [ -f "$REAL_IDLE.xml" ]; then
  case_run lens-R1-real-idle-hal-b1.0    0 $L --screenshot "$REAL_IDLE.png" --dump "$REAL_IDLE.xml" --hue hal --brightness 1.0
  case_run lens-R2-real-idle-vs-dimmed   1 $L --screenshot "$REAL_IDLE.png" --dump "$REAL_IDLE.xml" --hue hal --brightness 0.5
  case_run lens-R3-real-idle-vs-cobalt   1 $L --screenshot "$REAL_IDLE.png" --dump "$REAL_IDLE.xml" --hue $COBALT_HUE --brightness 1.0
fi

echo "== $((N - BAD)) of $N cases ended with their expected exit code; logs in $OUT"
[ "$BAD" = 0 ]
