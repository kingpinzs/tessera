#!/usr/bin/env bash
# Phase 20 A4 — Lock the screen.
#
#   1  station playing; KEYCODE_SLEEP; wait 60 s; wake_device
#   2  the same with a local MP3 (music_fixtures: phase 01's MUSIC6 files, 90 s each)
#
# Pass: (a) the player's AudioFlinger track is active at 60 s both times (music17.sh's awk: a track of the shell's pid in
# state A); (b) for the local track nowplaying_scrubber and nowplaying_total are back.
# The device's wakefulness is RECORDED at every sample (it must not be Awake for the wait to mean anything) and asserted
# once at 60 s as part of (a).
#
# Changes on the device: the six MUSIC6 MP3s in /sdcard/Music/tessera-qa (shared by the MUSIC rows and J5; left there).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p20.sh"
trap 'adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1; adb shell wm dismiss-keyguard >/dev/null 2>&1; fixtures_down' EXIT

screen_off_60() { # label — prints the samples; sets AT60_TRACKS, AT60_WAKE, AT60_STATE
  log "      [$1] before: tracks=$(ours) $(wakeful) $(sboth)"
  adb shell input keyevent KEYCODE_SLEEP; local t0; t0=$(date +%s); sleep 2
  log "      [$1] after KEYCODE_SLEEP: $(wakeful)"
  local t
  for t in 15 30 45 60; do
    while [ $(( $(date +%s) - t0 )) -lt "$t" ]; do sleep 0.5; done
    AT60_TRACKS="$(ours)"; AT60_WAKE="$(wakeful)"; AT60_STATE="$(sboth)"
    log "      [$1] off+$t s: tracks=$AT60_TRACKS $AT60_WAKE $AT60_STATE"
  done
  adb shell dumpsys media.audio_flinger > "$ROW_DIR/flinger-$1-60s.txt" 2>&1
  session_save "session-$1-60s"
  log "      [$1] wake_device: $(wake_device)"
  sleep 2
}

p20_begin A4 "lock the screen: a station, then a local MP3, 60 s each"
prefs_guard a b
fixtures_up
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
music_fixtures
baseline_start

# ----------------------------------------------------------------------------------------------- 1: the station
log "--- 1: station playing; KEYCODE_SLEEP; wait 60 s; wake_device"
letter a
play_station "$J1" 25; assert_eq "(a) station: QA Jazz One is PLAYING before the screen goes off" "0 PLAYING" "$? $(sstate)"
sleep 5; shot 00-station-before
assert_ne "(a) station: the player has an active AudioFlinger track before the screen goes off (the reader finds one)" "0" "$(ours | awk '{ print $1 }')"
screen_off_60 station
record "(a) station at 60 s: active tracks (count, sessions) / wakefulness / session" "$AT60_TRACKS / $AT60_WAKE / $AT60_STATE"
assert_ne "(a) station: the device was not awake at 60 s (the screen was off)" "Awake" "$AT60_WAKE"
assert_yes "(a) station: the player's AudioFlinger track is active at 60 s" "$([ "$(echo "$AT60_TRACKS" | awk '{ print $1 }')" -ge 1 ] 2>/dev/null && echo yes || echo no)"
d station-after; shot 01-station-after-wake

# ----------------------------------------------------------------------------------------------- 2: a local MP3
log "--- 2: the same with a local MP3"
to_pivot songs; assert_eq "Music is on the songs pivot" "0" "$?"
SONG="$(ids songs music_song: | head -1)"
record "the local song tapped" "$SONG ($(nt songs "music_pri:$SONG"))"
assert_ne "(a) local: the songs pivot lists a local song (music_fixtures)" "" "$SONG"
[ -n "$SONG" ] && tap songs "$SONG"
for _ in $(seq 1 12); do sleep 1; [ "$(sstate)" = PLAYING ] && break; done
sleep 3; d local-before; shot 02-local-before
LOCAL_META="$(smeta)"
assert_eq "(a) local: the local track is PLAYING before the screen goes off" "PLAYING" "$(sstate)"
assert_absent "(a) local: …and it is not the station any more" "QA Jazz One" "$LOCAL_META"
screen_off_60 local
record "(a) local at 60 s: active tracks (count, sessions) / wakefulness / session" "$AT60_TRACKS / $AT60_WAKE / $AT60_STATE"
assert_ne "(a) local: the device was not awake at 60 s (the screen was off)" "Awake" "$AT60_WAKE"
assert_yes "(a) local: the player's AudioFlinger track is active at 60 s" "$([ "$(echo "$AT60_TRACKS" | awk '{ print $1 }')" -ge 1 ] 2>/dev/null && echo yes || echo no)"

letter b
adb shell am start -W -n $MUSIC_ACTIVITY >/dev/null 2>&1; sleep 2.5
d local-after; shot 03-local-after-wake
assert_yes "(b) the now-playing page is showing a local track" "$(has local-after nowplaying_root)"
record "(b) track / elapsed / total" "$(nt local-after nowplaying_track) / $(nt local-after nowplaying_elapsed) / $(nt local-after nowplaying_total)"
assert_eq "(b) nowplaying_scrubber and nowplaying_total are back" "yes yes" "$(has local-after nowplaying_scrubber) $(has local-after nowplaying_total)"
assert_yes "(b) …the total reads a length (m:ss)" "$(nt local-after nowplaying_total | grep -qE '^[0-9]+:[0-9]{2}$' && echo yes || echo no)"
assert_eq "(b) …and the live caption is gone" "no" "$(has local-after nowplaying_live_caption)"

rings_save
adb shell am force-stop app.tileshell; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 3
fixtures_down
ensure_start
p20_end a b
