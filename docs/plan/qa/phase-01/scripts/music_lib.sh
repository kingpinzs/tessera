#!/usr/bin/env bash
# Shared by the phase 10 music rows (MUSIC6, MUSIC7). Source it AFTER lib.sh and ui.sh.

MUSIC_FIXDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../MUSIC6-fixtures" 2>/dev/null && pwd)"

# Silent by choice: these rows play real audio out of the emulator and whoever is running them is
# usually doing something else. Muting the media stream changes nothing they measure — the session
# still reports PLAYING and the position still advances.
music_mute() {
  adb shell cmd media_session volume --stream 3 --set 0 >/dev/null 2>&1
  for _ in $(seq 1 20); do adb shell input keyevent 25 >/dev/null 2>&1; done
}

# Six tagged MP3s, 90 seconds each: three albums, three artists, and a title starting with a digit so
# the "#" letter group is exercised. 90 seconds and not 4 — MUSIC7's first run ran off the end of the
# whole queue between its own steps and read "next has nowhere to go" as a product fault.
music_fixtures() {
  adb shell mkdir -p /sdcard/Music/tessera-qa >/dev/null 2>&1
  for f in "$MUSIC_FIXDIR"/*.mp3; do adb push "$f" /sdcard/Music/tessera-qa/ >/dev/null 2>&1; done
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
  command sleep 4
}

music_open() {
  adb shell am force-stop $PKG; command sleep 1
  adb shell am start -n $PKG/.music.MusicActivity >/dev/null 2>&1
  command sleep 5
}

# Tap a pivot header and WAIT FOR THE PIVOT TO SETTLE, read off the shell's own diagnostics rather
# than off a sleep. MUSIC7's first run tapped from a dump, slept two seconds and dumped again: the
# swing had not finished, so it measured the old strip, and its NEXT tap then landed on whatever had
# slid under that coordinate. Every assertion after it was about the wrong page.
goto_pivot() { # goto_pivot <name> <out.xml>
  local want="$1" out="$2" i=0 cur
  dump "$ROW_DIR/.nav.xml" || return 1
  tap_id "$ROW_DIR/.nav.xml" "music_pivot_header:$want" || return 1
  while [ "$i" -lt 16 ]; do
    cur="$(diag music | grep -o 'pivot settled on [a-z]*' | tail -1 | awk '{print $4}')"
    [ "$cur" = "$want" ] && break
    command sleep 0.5
    i=$((i + 1))
  done
  command sleep 1
  dump "$out"
  note "pivot -> $want (settled after $((i / 2))s, diagnostics say [$cur])"
  [ "$cur" = "$want" ]
}

# The PlaybackState of the SHELL's session, not of whatever the dump happens to list first.
session_state() { awk '/package=app\.tileshell/ { found = 1 } found && /state=PlaybackState/ { print; exit }' "$1"; }
