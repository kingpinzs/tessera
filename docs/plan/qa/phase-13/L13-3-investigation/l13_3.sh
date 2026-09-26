#!/usr/bin/env bash
# L13-3 investigation (diagnosis only; no product change). Drives emulator-5556 (tileshell_fhd2) only.
#
#   l13_3.sh setup  <apk> <checker|none>          install, Home role, acrylic on, picture set or cleared, onto the app list
#   l13_3.sh trials <outdir> <gap_ms> <n> [life_ms] n isolated trials: hold A (band opens) -> life -> Back -> gap -> hold B
#   l13_3.sh loop   <outdir> <gap_ms> <n>          EDGE_RAPID's cadence on the device: n x (hold; Back; gap)
#   l13_3.sh hostloop <outdir> <n>                 EDGE_RAPID run2/3/4's exact loop: host-side adb, no sleeps
#
# Every hold is `input swipe X Y X Y 850` on the app list's 3rd row (EDGE_RAPID's row), every Back `input keyevent 4`.
# Device timestamps (date +%s%3N, the clock Diagnostics stamps wall= with) bracket each command, so each
# "[applist] context menu" line is attributed to the hold whose window holds it.
set -uo pipefail
export ANDROID_SERIAL=emulator-5556
export TMPDIR=/tmp/claude-1000/l13-3-tmp
HERE="$(cd "$(dirname "$0")" && pwd)"
S="$HERE/../scripts"
. "$S/lib.sh"
. "$S/p13.sh"
export ANDROID_SERIAL=emulator-5556
STATE="$TMPDIR/l13_3.state"

row_xy() { # dump -> "X Y" of the 3rd applist_row (EDGE_RAPID's pick)
  local rid
  rid="$(grep -o 'resource-id="applist_row:[^"]*"' "$1" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
  set -- $(bounds "$1" "$rid")
  echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $rid"
}

on_app_list() { # dump -> yes/no: app_list fully on screen, no band
  python3 - "$1" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(r'resource-id="app_list"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
band = 'resource-id="applist_menu"' in s
print("yes" if m and int(m.group(1)) == 0 and not band else "no")
PY
}

recover() { # back onto the app list, no band, list at its top
  show_start 3
  to_app_list 2
  dump_ui "$TMPDIR/rec.xml"
  [ "$(on_app_list "$TMPDIR/rec.xml")" = yes ]
}

case "${1:?}" in
sweep) # <outdir> <n per gap> [gaps...]: trials at each gap, then EDGE_RAPID's host loop HL times (default 3)
  OUT="$2"; N="$3"; shift 3
  GAPS="${*:-0 50 100 150 200 300}"
  mkdir -p "$OUT"
  echo "sweep $(date -Is) apk=$(installed_apk_id) load: $(cut -d' ' -f1-3 /proc/loadavg)" >> "$OUT/SUMMARY.txt"
  for g in $GAPS; do
    d="$OUT/g$g"; [ -e "$d" ] && d="$OUT/g$g-$(date +%H%M%S)"
    bash "$0" trials "$d" "$g" "$N" 100 | grep '^RESULT' | sed "s/^/  /" >> "$OUT/SUMMARY.txt"
  done
  for k in $(seq 1 "${HL:-3}"); do
    bash "$0" hostloop "$OUT/hostloop-$(date +%H%M%S)" 10 | sed "s/^/  /" >> "$OUT/SUMMARY.txt"
  done
  echo "  end $(date -Is) load: $(cut -d' ' -f1-3 /proc/loadavg)" >> "$OUT/SUMMARY.txt"
  cat "$OUT/SUMMARY.txt"
  ;;

setup)
  APKF="$2"; BG="$3"
  ROW_DIR="$TMPDIR"
  adb install -r -g "$APKF" | tail -1
  adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity >/dev/null
  set_pref transparency_effects boolean true
  if [ "$BG" = checker ]; then set_checker; else clear_background; fi
  show_start 7
  show_start 3; to_app_list 2; to_start 2; sleep 2
  to_app_list 3
  dump_ui "$TMPDIR/applist.xml"
  row_xy "$TMPDIR/applist.xml" > "$STATE"
  echo "apk $(sha256sum "$APKF" | cut -c1-16) installed $(installed_apk_id) bg=$BG row=$(cat "$STATE")"
  ;;

trials)
  OUT="$2"; GAP="$3"; N="$4"; LIFE="${5:-150}"
  mkdir -p "$OUT"
  read -r X Y RID < "$STATE"
  BX="${BX:-$X}"; BY="${BY:-$Y}"   # hold B's point (default: the same row as hold A)
  G="$(python3 -c "print($GAP/1000)")"; L="$(python3 -c "print($LIFE/1000)")"
  echo "trials gap=${GAP}ms life=${LIFE}ms n=$N row=$RID A=($X,$Y) B=($BX,$BY) apk=$(installed_apk_id) at $(date -Is)" > "$OUT/summary.txt"
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  MARK0="$(ring_mark)"
  ok=0; miss=0; bad=0
  for i in $(seq 1 "$N"); do
    sleep 1.5
    MARK="$(ring_mark)"
    SLP=""; [ "$GAP" != 0 ] && SLP="sleep $G;"
    adb shell "echo A0 \$(date +%s%3N); input swipe $X $Y $X $Y 850; echo A1 \$(date +%s%3N); sleep $L; echo K0 \$(date +%s%3N); input keyevent 4; echo K1 \$(date +%s%3N); $SLP echo B0 \$(date +%s%3N); input swipe $BX $BY $BX $BY 850; echo B1 \$(date +%s%3N)" | tr -d '\r' > "$OUT/t$i.ts"
    ring_since "$MARK" > "$OUT/t$i.ring"
    verdict="$(python3 - "$OUT/t$i.ts" "$OUT/t$i.ring" <<'PY'
import re, sys
ts = dict(l.split() for l in open(sys.argv[1]) if l.strip())
ts = {k: int(v) for k, v in ts.items()}
menus = [int(m.group(1)) for l in open(sys.argv[2]) if "[applist] context menu" in l for m in [re.search(r"wall=(\d+)", l)]]
a = any(ts["A0"] <= w <= ts["A1"] + 50 for w in menus)
b = any(ts["B0"] <= w <= ts["B1"] + 50 for w in menus)
pivots = sum("[motion] pivot" in l for l in open(sys.argv[2]))
pins = sum("pin to Start" in l for l in open(sys.argv[2]))
gap = ts["B0"] - ts["K1"]
print(("OK" if a and b else "MISS" if a else "BADSETUP"), f"back->B0={gap}ms", f"K0->K1={ts['K1']-ts['K0']}ms", f"pivots={pivots}", f"pins={pins}")
PY
)"
    echo "t$i $verdict" | tee -a "$OUT/summary.txt"
    case "$verdict" in
      OK*) ok=$((ok + 1)); adb shell input keyevent 4; sleep 0.5 ;;
      MISS*) miss=$((miss + 1))
             dump_ui "$OUT/t$i-after.xml"; screencap "$OUT/t$i-after.png"
             [ "$(on_app_list "$OUT/t$i-after.xml")" = yes ] || { echo "   t$i: not on the app list after the miss; recovering" | tee -a "$OUT/summary.txt"; recover; } ;;
      *) bad=$((bad + 1)); dump_ui "$OUT/t$i-bad.xml"; recover ;;
    esac
  done
  ring_since "$MARK0" > "$OUT/slice.txt"
  adb shell dumpsys gfxinfo $PKG > "$OUT/gfxinfo.txt"
  echo "RESULT gap=${GAP}ms life=${LIFE}ms: B opened $ok / $((ok + miss)) (bad setups $bad); pivot lines $(grep -c '\[motion\] pivot' "$OUT/slice.txt")" | tee -a "$OUT/summary.txt"
  ;;

loop)
  OUT="$2"; GAP="$3"; N="$4"
  mkdir -p "$OUT"
  read -r X Y RID < "$STATE"
  G="$(python3 -c "print($GAP/1000)")"
  SLP=""; [ "$GAP" != 0 ] && SLP="sleep $G;"
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  sleep 1.5
  MARK="$(ring_mark)"
  adb shell "for i in \$(seq 1 $N); do echo H\$i \$(date +%s%3N); input swipe $X $Y $X $Y 850; echo K\$i \$(date +%s%3N); input keyevent 4; echo E\$i \$(date +%s%3N); $SLP done" | tr -d '\r' > "$OUT/ts.txt"
  sleep 1
  ring_since "$MARK" > "$OUT/slice.txt"
  dump_ui "$OUT/after.xml"
  adb shell dumpsys gfxinfo $PKG > "$OUT/gfxinfo.txt"
  echo "loop gap=${GAP}ms n=$N: bands $(grep -c '\[applist\] context menu' "$OUT/slice.txt") / $N; pivot lines $(grep -c '\[motion\] pivot' "$OUT/slice.txt"); edit lines $(grep -c '\[edit\]' "$OUT/slice.txt"); on app list after: $(on_app_list "$OUT/after.xml")" | tee "$OUT/summary.txt"
  [ "$(on_app_list "$OUT/after.xml")" = yes ] || recover
  ;;

hostloop)
  OUT="$2"; N="$3"; PRE="${4:-}"
  mkdir -p "$OUT"
  read -r X Y RID < "$STATE"
  if [ -n "$PRE" ]; then
    # EDGE_RAPID run3/run4's steps between the dump and the holds: to Start, PSS x3 (meminfo), [gfxinfo], to the app list
    to_start 2
    for _ in 1 2 3; do adb shell dumpsys meminfo "$(adb shell pidof $PKG | tr -d '\r')" | grep -m1 'TOTAL PSS:' >> "$OUT/pre.txt"; sleep 1; done
    [ "$PRE" = gfx ] && adb shell dumpsys gfxinfo $PKG | grep -A1 -m1 'Total GPU memory usage' >> "$OUT/pre.txt"
    to_app_list 2
  fi
  sleep 1.5
  MARK="$(ring_mark)"
  for _ in $(seq 1 "$N"); do
    adb shell input swipe $X $Y $X $Y 850
    adb shell input keyevent KEYCODE_BACK
  done
  sleep 1
  ring_since "$MARK" > "$OUT/slice.txt"
  dump_ui "$OUT/after.xml"
  echo "hostloop n=$N: bands $(grep -c '\[applist\] context menu' "$OUT/slice.txt") / $N; pivot lines $(grep -c '\[motion\] pivot' "$OUT/slice.txt"); edit lines $(grep -c '\[edit\]' "$OUT/slice.txt"); on app list after: $(on_app_list "$OUT/after.xml")" | tee "$OUT/summary.txt"
  [ "$(on_app_list "$OUT/after.xml")" = yes ] || recover
  ;;
esac
