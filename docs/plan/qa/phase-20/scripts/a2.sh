#!/usr/bin/env bash
# Phase 20 A2 — Play a station.
#
#   1  tap QA Jazz One; wait 30 s      2  open ••• → sleep; Back; Home      3  pause from the Music tile
#   4  back in radio, tap QA File
#
# Pass (a)–(e) as the phase doc lists them; `:8080` reads `:8092` (INDEX Change Log 2026-10-07).
# How (a)'s titles are read: the session's metadata is sampled every 2 s from the tap (a timeline file, recorded), and
# asserted twice — "QA Song 1" was seen before the switch, and the ONE read at 30 s is "QA Song 2" (no ± timing).
# How (c)'s control is read: the doc writes `tile_control:<id>:PAUSE`; the built tag is `tile_control:<tile id>:<transport
# name>` (start/TileView.kt:589) and the transport that pauses is PLAY_PAUSE, described "Pause" while playing. The driver
# taps the Music tile's control whose content-desc is "Pause" and records the tag it found.
#
# Needs: A1's state (the fixture prefs; QA Jazz One may be a favourite — either row is tapped). Fixtures up / down here.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p20.sh"
trap 'fixtures_down' EXIT

tile_bounds() { echo "$(bounds "$ROW_DIR/$1.xml" tile:slot:PHOTOS) / $(bounds "$ROW_DIR/$1.xml" tile:dock:slot:CAMERA)"; }
tile_texts() { # dump name, tile id -> the texts drawn inside that tile, joined with " | "
  python3 - "$ROW_DIR/$1.xml" "$2" <<'PY'
import sys
import xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter('node'):
    if n.get('resource-id') == sys.argv[2]:
        print(" | ".join(c.get('text') for c in n.iter('node') if c.get('text'))); break
PY
}
pause_control() { # dump name -> the resource-id of the Music tile's control described "Pause"
  python3 - "$ROW_DIR/$1.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter('node'):
    r = n.get('resource-id') or ''
    if r.startswith('tile_control:slot:MUSIC:') and n.get('content-desc') == 'Pause': print(r); break
PY
}

p20_begin A2 "play a station"
prefs_guard a b c d e
fixtures_up
baseline_start
d start-before; shot 00-start-before
B0="$(tile_bounds start-before)"
record "Photos / Camera tile bounds before anything plays" "$B0"
assert_ne "the Photos and Camera tiles are on Start before the row plays anything" " / " "$B0"

# ----------------------------------------------------------------------------------------------- 1: tap, wait 30 s
log "--- 1: tap QA Jazz One; wait 30 s"
letter a
to_radio; assert_eq "Music is on the radio pivot" "0" "$?"
ID="radio_row:$J1"; [ "$(has radio "radio_fav:$J1")" = yes ] && ID="radio_fav:$J1"
radio_reach radio "$ID"; record "the row tapped" "$ID ($(nt radio "music_pri:$ID"))"
MARK="$(ring_mark)"; T0=$(date +%s)
tap radio "$ID"
: > "$ROW_DIR/title-timeline.txt"
while :; do
  t=$(( $(date +%s) - T0 )); [ "$t" -ge 28 ] && break
  echo "t+$t s: $(sboth)" >> "$ROW_DIR/title-timeline.txt"; sleep 1.5
done
while [ $(( $(date +%s) - T0 )) -lt 30 ]; do sleep 0.3; done
AT30="$(sboth)"; session_save session-30s
echo "t+$(( $(date +%s) - T0 )) s (THE READ): $AT30" >> "$ROW_DIR/title-timeline.txt"
d np30; shot 01-nowplaying-30s
mring "$MARK" > "$ROW_DIR/slice-play.txt"
record "(a) the session at 30 s (state | title, artist, album)" "$AT30"
assert_eq "(a) the session is PLAYING at 30 s" "PLAYING" "${AT30%% |*}"
META="${AT30#* | }"
# dumpsys prints the session's MediaDescription (title, then the next non-empty of artist / album / …), not the album key
# by name: the station's name must be one of the fields after the title.
assert_yes "(a) the album is \"QA Jazz One\" (a field after the title in the session's description)" "$(printf '%s' "$META" | awk -F', ' '{ for (i = 2; i <= NF; i++) if ($i == "QA Jazz One") f = 1 } END { print f ? "yes" : "no" }')"
assert_yes "(a) the title was \"QA Song 1\" first (a sample before the switch)" "$(grep -v 'THE READ' "$ROW_DIR/title-timeline.txt" | grep -q '| QA Song 1,' && echo yes || echo no)"
assert_eq "(a) …then \"QA Song 2\" (the one read at 30 s)" "QA Song 2" "$(printf '%s' "$META" | awk -F', ' '{ print $1 }')"
CONNECTED="$(grep -F 'stream: connected' "$ROW_DIR/slice-play.txt" | head -1 | stripped)"
record "(a) the line" "$CONNECTED"
assert_yes "(a) [music] stream: connected http://10.0.2.2:$RADIO_PORT/… codec=mp3" "$(printf '%s' "$CONNECTED" | grep -qE "^\[music\] stream: connected http://10\.0\.2\.2:$RADIO_PORT/[^ ]* codec=mp3" && echo yes || echo no)"

letter b
assert_yes "(b) the now-playing page is showing" "$(has np30 nowplaying_root)"
assert_eq "(b) nowplaying_live_caption = \"LIVE\"" "LIVE" "$(nt np30 nowplaying_live_caption)"
assert_eq "(b) nowplaying_scrubber and nowplaying_total are absent" "no no" "$(has np30 nowplaying_scrubber) $(has np30 nowplaying_total)"

# ----------------------------------------------------------------------------------------------- 2: ••• → sleep; Back; Home
log "--- 2: open ••• → sleep; Back; Home"
tap np30 nowplaying_control:more; sleep 1.5; d more; shot 02-more-menu
assert_yes "(b) the ••• menu opened with its sleep entry (music_menu_sleep)" "$(has more music_menu_sleep)"
tap more music_menu_sleep; sleep 1.5; d sleep; shot 03-sleep-list
SLEEPS="$(ids sleep music_menu_sleep: | tr '\n' ' ')"
record "(b) the sleep list's entries" "$SLEEPS"
assert_yes "(b) the sleep list is showing (the minute choices are there, so the absence is read)" "$(has sleep music_menu_sleep:15)"
assert_eq "(b) music_menu_sleep:eot is absent" "no" "$(has sleep music_menu_sleep:eot)"
adb shell input keyevent KEYCODE_BACK; sleep 1.2
ensure_start; sleep 2

# ----------------------------------------------------------------------------------------------- 3: the Music tile
log "--- 3: pause from the Music tile"
letter c
scroll_to_node "$ROW_DIR/start-playing.xml" tile:slot:MUSIC 3 >/dev/null; d start-playing; shot 04-start-playing
TT="$(tile_texts start-playing tile:slot:MUSIC)"
record "(c) the Music tile's texts while it plays" "$TT"
assert_contains "(c) the Music tile shows \"QA Song 2\"" "QA Song 2" "$TT"
B1="$(tile_bounds start-playing)"
assert_eq "(c) the Photos and Camera tile bounds are unchanged while the tile shows a station" "$B0" "$B1"
PC="$(pause_control start-playing)"
record "(c) the control found (the doc's tile_control:<id>:PAUSE)" "${PC:-none} — content-desc Pause"
assert_ne "(c) the Music tile has a control described Pause" "" "$PC"
[ -n "$PC" ] && tap start-playing "$PC"; sleep 3
PAUSED="$(sboth)"; session_save session-paused
d start-paused; shot 05-start-paused
record "(c) the session after the tap" "$PAUSED"
assert_eq "(c) the tile's control pauses it (the session's state)" "PAUSED" "${PAUSED%% |*}"
assert_eq "(c) the Photos and Camera tile bounds are unchanged after the pause" "$B0" "$(tile_bounds start-paused)"

# ----------------------------------------------------------------------------------------------- (d) the click call
letter d
flog "$RLOG.jsonl" 'r["path"].startswith("/json/url/")' > "$ROW_DIR/click-calls.txt"
record "(d) the fixture's click calls so far" "$(cut -d'|' -f1 "$ROW_DIR/click-calls.txt" | tr '\n' ';')"
assert_eq "(d) the fixture logged exactly one GET /json/url/<uuid>" "1" "$(grep -c . "$ROW_DIR/click-calls.txt")"
assert_contains "(d) …for QA Jazz One" "GET /json/url/$J1" "$(cat "$ROW_DIR/click-calls.txt")"

# ----------------------------------------------------------------------------------------------- 4: QA File
log "--- 4: back in radio, tap QA File"
letter e
to_radio; assert_eq "(e) Music is back on the radio pivot" "0" "$?"
radio_reach file0 "radio_row:$F1"; assert_eq "(e) QA File's row is on screen" "0" "$?"
assert_eq "(e) the row reads QA File" "QA File" "$(nt file0 "music_pri:radio_row:$F1")"
BEFORE="$(sboth)"; session_save session-before-file
MARK2="$(ring_mark)"
tap file0 "radio_row:$F1"; sleep 2
d file1; shot 06-qa-file-refused
sleep 2
AFTER="$(sboth)"; session_save session-after-file
mring "$MARK2" > "$ROW_DIR/slice-file.txt"
record "(e) the row's second line after the tap" "$(nt file1 "music_sub:radio_row:$F1")"
assert_eq "(e) \"can't play this station\"" "can't play this station" "$(nt file1 "music_sub:radio_row:$F1")"
record "(e) the session before / after" "[$BEFORE] / [$AFTER]"
assert_ne "(e) there is a session to compare (the paused station)" " | " "$BEFORE"
assert_eq "(e) the session's metadata is unchanged" "$BEFORE" "$AFTER"
UNSUP="$(grep -F 'stream: unsupported' "$ROW_DIR/slice-file.txt" | head -1 | stripped)"
record "(e) the line" "$UNSUP"
assert_contains "(e) [music] stream: unsupported scheme=file" "[music] stream: unsupported scheme=file" "$UNSUP"
flog "$RLOG.jsonl" 'r["path"].startswith("/json/url/")' > "$ROW_DIR/click-calls-end.txt"
letter d
assert_eq "(d) …and still exactly one at the end of the row (the refused station made no click call)" "1" "$(grep -c . "$ROW_DIR/click-calls-end.txt")"

rings_save
adb shell am force-stop app.tileshell; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 3
fixtures_down
flog "$RLOG.jsonl" 'True' > "$ROW_DIR/radio-requests.txt"
ensure_start
p20_end a b c d e
