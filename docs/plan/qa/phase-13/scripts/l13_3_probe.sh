#!/usr/bin/env bash
# L13-3 probe (review/2026-09-26-L13-345-fix-triage.md; round 2 note N-A): the discriminating half of l13_3_row.sh. Each
# trial: hold A opens the app list's band; then Back and hold B go out together from one device shell, B offset by
# 0-20 ms, so B's down lands around the Back — often inside the window between the Back's write and the band leaving
# the composition, where the old build lost it to the stale scrim. After each trial the state is read and restored
# (band open -> Back; on Start -> back to the app list). Runs on any build; on the timing-diagnostic build its slice
# also carries the [l13] lines diag_window.py reads. First run as L13-3-diag3-window-e8a00dc6/window_probe.sh
# (ee4bf960 26/40, 707aa55b 40/40 in one session).
#   l13_3_probe.sh <outdir> <n>        (row coordinates from l13_3_race.sh setup's state file)
set -uo pipefail
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
export TMPDIR="${L13_TMP:-/tmp/claude-1000/l13-fix-tmp}"
S="$(cd "$(dirname "$0")" && pwd)"
. "$S/lib.sh"
. "$S/p13.sh"
OUT="$1"; N="$2"
mkdir -p "$OUT"
read -r X Y RID < "$TMPDIR/l13_3.state"
state() { # dump -> band | applist | start
  python3 - "$1" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
if 'applist_menu_pin' in s: print("band")
else:
    m = re.search(r'resource-id="app_list"[^>]*bounds="\[(-?\d+),', s)
    print("applist" if m and int(m.group(1)) == 0 else "start")
PY
}
echo "window probe n=$N row=$RID A=B=($X,$Y) apk=$(installed_apk_id) at $(date -Is)" > "$OUT/summary.txt"
: > "$OUT/slice.txt"
for i in $(seq 1 "$N"); do
  off=$(( (i % 5) * 5 ))
  MARK="$(ring_mark)"
  adb shell input swipe $X $Y $X $Y 850
  sleep 0.3
  adb shell "input keyevent 4 & sleep 0.0$(printf %02d $off); input swipe $X $Y $X $Y 850; wait"
  sleep 1.2
  dump_ui "$OUT/t$i.xml"
  st="$(state "$OUT/t$i.xml")"
  echo "t$i offset=${off}ms after: $st" >> "$OUT/summary.txt"
  ring_since "$MARK" >> "$OUT/slice.txt"
  case "$st" in
    band) adb shell input keyevent 4; sleep 0.8 ;;
    start) to_app_list 2 ;;
  esac
done
echo "trials $N: B opened the band $(grep -c 'after: band' "$OUT/summary.txt")" | tee -a "$OUT/summary.txt"
