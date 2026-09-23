#!/usr/bin/env bash
# J2 — the now-playing screen shows the total and the scrubber seeks, for EVERY kind of MP3 (Jeremy,
# 2026-09-22, on the phone: "The media player does not show the total time and scrolling the playing song
# does not move the cursor").
#
# MUSIC7 passed on fixtures that all carry a Xing/Info header, and its total check only compared the
# label with itself two seconds later — "0:00" twice would have passed. This row plays three 90-second
# files: the MUSIC6 fixture (Xing header, the control), a CBR file written WITHOUT a Xing header, and a
# VBR file without one. For each: the right label must read the real total (1:29-1:30), a drag of the
# thumb to the middle must land the elapsed label near 0:45, and a tap at three quarters near 1:07.
# The generated files are removed and the library rescanned at the end (RV12).
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"
source "$(dirname "$0")/music_lib.sh"

row_begin J2 "now playing: the total and the scrubber, with and without an MP3 length header"
FIX="$ROW_DIR/fixtures"; mkdir -p "$FIX"
gen() { # out title extra-ffmpeg-args...
  local out="$1" title="$2"; shift 2
  ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=330:duration=90" -ac 2 -ar 44100 "$@" \
    -metadata title="$title" -metadata artist="J2 Artist" -metadata album="J2 Album" "$out"
}
gen "$FIX/j2_cbr_noheader.mp3" "J2 CBR no header" -c:a libmp3lame -b:a 128k -write_xing 0
gen "$FIX/j2_vbr_noheader.mp3" "J2 VBR no header" -c:a libmp3lame -q:a 4 -write_xing 0
for f in "$FIX"/*.mp3; do note "$(basename "$f"): Xing/Info frames $(head -c 20000 "$f" | grep -c -a -E 'Xing|Info'), $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$f") s"; done

music_mute
music_fixtures
adb shell mkdir -p /sdcard/Music/tessera-j2 >/dev/null 2>&1
for f in "$FIX"/*.mp3; do adb push "$f" /sdcard/Music/tessera-j2/ >/dev/null 2>&1; done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
command sleep 4

song_id() { # songs.xml title -> music_song:<id> whose row holds that title
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    rid = n.get("resource-id", "")
    if rid.startswith("music_song:") and any(c.get("text") == sys.argv[2] for c in n.iter("node")):
        print(rid); break
PY
}
secs() { echo "$1" | awk -F: '{print $1*60+$2}'; }

check_track() { # label title
  local label="$1" title="$2" id x1 y1 x2 y2 cy e t
  music_open
  goto_pivot songs "$ROW_DIR/songs_$label.xml"
  scroll_to_id "$ROW_DIR/songs_$label.xml" "$(song_id "$ROW_DIR/songs_$label.xml" "$title")" >/dev/null 2>&1 || true
  dump "$ROW_DIR/songs_$label.xml"
  id="$(song_id "$ROW_DIR/songs_$label.xml" "$title")"
  assert_ne "$label: the track is in the Songs pivot" "" "$id"
  [ -n "$id" ] || return
  tap_id "$ROW_DIR/songs_$label.xml" "$id"; command sleep 4
  dump "$ROW_DIR/np_$label.xml"; screencap "$ROW_DIR/np_$label.png"
  t="$(node_text "$ROW_DIR/np_$label.xml" nowplaying_total)"
  note "$label: total label [$t], elapsed [$(node_text "$ROW_DIR/np_$label.xml" nowplaying_elapsed)]"
  assert_within "$label: the right label is the real total (90 s)" 90 "$(secs "${t:-0:00}")" 1
  read -r x1 y1 x2 y2 <<< "$(bounds "$ROW_DIR/np_$label.xml" nowplaying_scrubber)"
  cy=$(( (y1 + y2) / 2 ))
  # Drag from the left end stop to the middle of the track.
  adb shell input swipe $((x1 + 20)) $cy $(( (x1 + x2) / 2 )) $cy 700; command sleep 1.5
  dump "$ROW_DIR/np_${label}_drag.xml"
  e="$(node_text "$ROW_DIR/np_${label}_drag.xml" nowplaying_elapsed)"
  note "$label: after a drag to the middle, elapsed [$e]"
  assert_within "$label: a drag to the middle seeks to about 0:45" 46 "$(secs "${e:-0:00}")" 5
  # A tap at three quarters jumps.
  adb shell input tap $(( x1 + (x2 - x1) * 3 / 4 )) $cy; command sleep 1.5
  dump "$ROW_DIR/np_${label}_tap.xml"
  e="$(node_text "$ROW_DIR/np_${label}_tap.xml" nowplaying_elapsed)"
  note "$label: after a tap at three quarters, elapsed [$e]"
  assert_within "$label: a tap at three quarters seeks to about 1:07" 68 "$(secs "${e:-0:00}")" 5
}

check_track control "$(ffprobe -v error -show_entries format_tags=title -of csv=p=0 "$MUSIC_FIXDIR/01.mp3")"
check_track cbr "J2 CBR no header"
check_track vbr "J2 VBR no header"

adb shell am force-stop $PKG
adb shell rm -r /sdcard/Music/tessera-j2 >/dev/null 2>&1
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
note "restored: the J2 files removed and the library rescanned"
row_end
