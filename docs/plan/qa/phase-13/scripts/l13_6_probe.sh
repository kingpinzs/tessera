#!/usr/bin/env bash
# L13-6 probe (INDEX ledger L13-6): Music's MusicMenu, like the app list's band before L13-3, closed on Back only by
# writing state, and stayed hit-testable until it left the composition a frame later; a touch in that frame landed on
# its stale full-screen scrim and was lost. Each trial opens the menu (A), then sends Back and the same opening gesture
# (B) together from one device shell, B offset by 0-20 ms, and reads whether B opened the menu again. After each trial
# the state is read and restored. Modelled on l13_3_probe.sh.
#   l13_6_probe.sh setup <hold|more>           Music open on the songs pivot (hold) or on now playing (more)
#   l13_6_probe.sh <hold|more> <outdir> <n>    n trials; the last line of <outdir>/summary.txt is the count
set -uo pipefail
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
export TMPDIR="${L13_TMP:-/tmp/claude-1000/l13-fix-tmp}"; mkdir -p "$TMPDIR"
S="$(cd "$(dirname "$0")" && pwd)"
. "$S/lib.sh"
. "$S/p13.sh"
P01="$(cd "$S/../../phase-01/scripts" && pwd)"
ROW_DIR="${ROW_DIR:-$TMPDIR}"
LOG="${LOG:-$TMPDIR/l13_6-notes.log}"
. "$P01/ui.sh"
. "$P01/music_lib.sh"
STATE="$TMPDIR/l13_6.state"

where() { # dump -> menu | songs | nowplaying | other
  python3 - "$1" <<'PY'
import sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
if 'resource-id="music_menu"' in s: print("menu")
elif 'resource-id="nowplaying_root"' in s: print("nowplaying")
elif 'resource-id="music_song:' in s: print("songs")
else: print("other")
PY
}

to_songs() { music_open; goto_pivot songs "$TMPDIR/l13_6-songs.xml" > /dev/null 2>&1; }

if [ "$1" = setup ]; then
  music_mute
  music_fixtures
  adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
  to_songs
  SONG="$(grep -o 'resource-id="music_song:[0-9]*"' "$TMPDIR/l13_6-songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
  set -- "$2" $(bounds "$TMPDIR/l13_6-songs.xml" "$SONG")
  SX=$(( ($2 + $4) / 2 )); SY=$(( ($3 + $5) / 2 ))
  if [ "$1" = more ]; then
    adb shell input tap $SX $SY; sleep 3
    dump_ui "$TMPDIR/l13_6-np.xml"
    set -- more $(bounds "$TMPDIR/l13_6-np.xml" nowplaying_control:more)
    echo "more $(( ($2 + $4) / 2 )) $(( ($3 + $5) / 2 ))" > "$STATE"
  else
    echo "hold $SX $SY $SONG" > "$STATE"
  fi
  echo "setup $(cat "$STATE") apk=$(installed_apk_id)"
  exit 0
fi

MODE="$1"; OUT="$2"; N="$3"
mkdir -p "$OUT"
read -r M X Y _ < "$STATE"
[ "$M" = "$MODE" ] || { echo "state is for $M, not $MODE; run setup $MODE" >&2; exit 2; }
if [ "$MODE" = hold ]; then open_a="input swipe $X $Y $X $Y 1200"; home=songs; else open_a="input tap $X $Y"; home=nowplaying; fi
echo "l13_6 probe mode=$MODE n=$N at=($X,$Y) apk=$(installed_apk_id) at $(date -Is)" > "$OUT/summary.txt"
: > "$OUT/slice.txt"
for i in $(seq 1 "$N"); do
  off=$(( (i % 5) * 5 ))
  MARK="$(ring_mark)"
  adb shell "$open_a"
  sleep 0.8
  adb shell "input keyevent 4 & sleep 0.0$(printf %02d $off); $open_a; wait"
  sleep 1.2
  dump_ui "$OUT/t$i.xml"
  st="$(where "$OUT/t$i.xml")"
  echo "t$i offset=${off}ms after: $st" >> "$OUT/summary.txt"
  ring_since "$MARK" >> "$OUT/slice.txt"
  case "$st" in
    menu) adb shell input keyevent 4; sleep 0.8 ;;
    "$home") ;;
    *) if [ "$MODE" = hold ]; then to_songs; else echo "t$i: left now playing; stopping" >> "$OUT/summary.txt"; break; fi ;;
  esac
done
echo "trials $N: B opened the menu $(grep -c 'after: menu' "$OUT/summary.txt")" | tee -a "$OUT/summary.txt"
