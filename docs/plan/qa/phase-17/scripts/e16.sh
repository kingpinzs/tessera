#!/usr/bin/env bash
# Phase 17 E16 — routing (phase 10 E10 / E13 re-run on this build, under phase 15's build task 0 — C-1).
#
#   child  phase 15's e0.sh, the emulator driver of phase 10 E10–E13's tile rule, unchanged, seeded with THIS phase's
#          baseline (E0_BASELINE): the shell's Music puts the face and the growth on the MUSIC slot tile and the tile's
#          play/pause drives the session; Auxio's playback moves the face to Auxio's pinned tile. It takes the device
#          lock itself, so it runs before the row holds the device, and its row directory is kept under E16/.
#          e0.sh's step 4 taps Voice Recorder's record button. No row may use the microphone (r3 D12 / V4), so
#          RECORD_AUDIO is revoked for the child's span — the button then starts nothing — and granted back after it;
#          `dumpsys audio`'s recording-activity log is read before and after and must have gained no event.
#   M  Music     from the baseline, a track played in the shell's Music: in a gdump of Start (start_page asserted in it
#                first — r3 V7) the MUSIC slot tile has grown, carries the face (the transport strip and the track's
#                title) and its play/pause control pauses the session (E13's findings, on the device again in this row);
#                tile:slot:PHOTOS and tile:dock:slot:CAMERA have the bounds they had before, and carry no now-playing
#                tag (no tile_controls: / tile_control: node of theirs) and no text of the track.
#   A  Auxio     a track played in Auxio (a pinned tile): the face is on Auxio's tile and off the MUSIC tile (E11); the
#                PHOTOS and CAMERA tiles keep their bounds and carry no now-playing tag.
#   R  reminder  a phase 03 time reminder made through Tess ("… in 1 minute", typed, confirmed on its card) fires: the
#                shell's own notification is posted and the listener counted it (the positive controls), and neither
#                tile:slot:PHOTOS nor tile:dock:slot:CAMERA carries a badge node or the reminder's text.
#   restore      playback stopped, Auxio force-stopped, the fixtures and the volumes put back as found, the reminder
#                (completed by its firing, so it sits in Tess's History) deleted on its own page and gone from the
#                store, its notification gone from the shade, the baseline layout, Start.
#
# "Home" while something plays is a bare KEYCODE_HOME, as e0.sh presses it: c6's force-stop would end the playback the
# leg reads. Each leg starts from c6 + ensure_start, and its "before" dump is taken before anything is launched.
# Recorded, not asserted: E13's "white glyphs survive a bright cover" (E16 words E13's findings as grows / the face /
# the controls; the fixture tracks carry no cover; the screenshot is kept).
#
# E16_CHILD=0 skips the child (driver development; the gate never sets it).
# Changes on the device: the Start layout, Music playback, the Music fixtures and volumes, RECORD_AUDIO for the
# child's span, one reminder — all restored. It force-stops the shell (layout_restore, c6); no wipe.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
QAR="$(cd "$QA/.." && pwd)"
OUT="$QA/E16"; mkdir -p "$OUT"
P13="$QAR/phase-13/scripts"
TILES="$QAR/phase-15/scripts/tiles.py"
MUSIC_ID="slot:MUSIC"
PHOTOS_ID="slot:PHOTOS"
CAMERA_ID="dock:slot:CAMERA"
AUXIO_ID="app:org.oxycblt.auxio/org.oxycblt.auxio.MainActivity:0"
OURS=("$PHOTOS_ID" "$CAMERA_ID")
REMINDER_TEXT="qa17 routing"      # Tess stores a reminder's text in lower case
REMINDERS_JSON=/data/data/app.tileshell/files/cortana_reminders.json
# Functions only (the phase 10 music rows' mute and fixtures; phase 13's typed reminder and its Reminders page), sourced
# before this driver's own readers: music_lib.sh has a session_state of its own, which the one below replaces.
. "$P01S/music_lib.sh"
. "$P13/reminders_fixture.sh"

# One tile's field from tiles.py (bounds | size | controls | badge | texts) — e0.sh's reader.
tile_field() { # dump.xml id field
  python3 "$TILES" "$1" "$2" | awk -F'\t' -v f="$3" '
    { m["bounds"] = $2; m["size"] = $3; m["controls"] = $4; m["badge"] = $5; m["texts"] = $6 }
    END { print m[f] }'
}
# The PlaybackState name of a package's session whose header carries <tag> (dumpsys media_session) — e0.sh's reader.
session_state() { # pkg tag
  adb shell dumpsys media_session | tr -d '\r' | python3 -c '
import re, sys
pkg, tag = sys.argv[1], sys.argv[2]; hit = False
for l in sys.stdin:
    if re.match(r"^\s+\S+ \S+/\S+/\d+ \(userId=\d+\)", l):
        hit = (" %s/" % pkg) in l and tag in l
    elif hit:
        m = re.search(r"state=PlaybackState \{state=([A-Z_]+)", l)
        if m: print(m.group(1)); break' "$1" "$2"
}
audio_id() { # title -> MediaStore _id
  q "content query --uri content://media/external/audio/media --projection _id --where \"title='$1'\"" | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2
}
# Start, dumped through the gesture driver (it never idles while a face animates), with its tile table beside it.
start_gdump() { # out.xml
  adb shell input keyevent KEYCODE_HOME; sleep 2
  gdump "$1"
  python3 "$TILES" "$1" > "${1%.xml}.tiles.txt" 2>&1
}
# r3 V7: a no-node check first asserts its page's tag, so an absence on the wrong page cannot pass.
assert_start() { # label dump.xml
  assert_eq "$1: the dump is Start's (start_page in it, no app list, no pod bay)" "yes no no" \
    "$(has_node "$2" start_page) $(has_node "$2" app_list) $(has_node "$2" pod_bay)"
}
# The now-playing tags a tile carries: its tile_controls: node and every tile_control:<id>:<transport> node.
playing_tags() { # dump.xml id
  grep -oE "resource-id=\"tile_controls?:$2(:[A-Z_]+)?\"" "$1" | sed 's/resource-id=//; s/"//g' | sort -u | tr '\n' ' ' | sed 's/ $//'
}
bigger() { python3 -c 'import sys, re
a = [int(x) for x in re.findall(r"\d+", sys.argv[1])]; b = [int(x) for x in re.findall(r"\d+", sys.argv[2])]
print("yes" if len(a) == 2 and len(b) == 2 and b[0] * b[1] > a[0] * a[1] else "no")' "$1" "$2"; }
# Our two tiles while something else plays: the bounds they had, no now-playing tag, none of the track's text.
assert_ours_untouched() { # label before.xml now.xml track-title
  local id
  for id in "${OURS[@]}"; do
    assert_ne "$1: tile:$id is in the dump" "" "$(tile_field "$3" "$id" bounds)"
    assert_eq "$1: tile:$id has the bounds it had" "$(tile_field "$2" "$id" bounds)" "$(tile_field "$3" "$id" bounds)"
    assert_eq "$1: tile:$id carries no now-playing tag" "" "$(playing_tags "$3" "$id")"
    assert_eq "$1: … tiles.py agrees (controls=no)" "controls=no" "$(tile_field "$3" "$id" controls)"
    assert_absent "$1: tile:$id shows no text of the track" "$4" "$(tile_field "$3" "$id" texts)"
  done
}
rec_events() { adb shell dumpsys audio 2>/dev/null | tr -d '\r' | grep -E '^[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}:[0-9]{3} rec [a-z]+ riid:'; }
reminders_json() { adb shell run-as app.tileshell cat "$REMINDERS_JSON" 2>/dev/null | tr -d '\r'; }
# The shell's own posted notifications, one block each (phase 15 clock.sh's reader).
shell_notifications() {
  adb shell dumpsys notification --noredact | tr -d '\r' | python3 -c '
import re, sys
text = sys.stdin.read()
for b in re.split(r"\n(?=\s*NotificationRecord\()", text):
    if "pkg=app.tileshell" in b.split("\n")[0]: print(b.strip()); print("----")'
}

# Every reminder of this row, removed through the product's own pages, one per pass: a reminder still waiting has its row
# on the Reminders page (its hold menu's Delete); one that fired is completed and sits in History, whose rows open the
# reminder's page (its app bar's delete). Prints how many it removed.
remove_row_reminders() {
  local n=0 id pass
  for pass in 1 2 3 4 5 6; do
    reminders_json | grep -qF "$REMINDER_TEXT" || break
    open_reminders
    id="$(reminder_id "$REMINDER_TEXT")"
    if [ -n "$id" ]; then
      # shellcheck disable=SC2046
      set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row_title:$id")
      [ $# -eq 4 ] && delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    else
      tap_node "$ROW_DIR/reminders.xml" reminders_appbar_list; sleep 2
      dump_ui "$ROW_DIR/.history.xml"
      id="$(reminder_id "$REMINDER_TEXT" "$ROW_DIR/.history.xml")"
      if [ -n "$id" ]; then
        tap_node "$ROW_DIR/.history.xml" "reminder_row_title:$id"; sleep 2
        dump_ui "$ROW_DIR/.history-page.xml"
        tap_node "$ROW_DIR/.history-page.xml" reminder_appbar_delete; sleep 2
      fi
    fi
    n=$((n + 1))
    adb shell input keyevent KEYCODE_HOME; sleep 1
    adb shell am force-stop app.tileshell; sleep 1
    adb shell input keyevent KEYCODE_HOME; sleep 4
  done
  echo "$n"
}

take_device_lock
if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
  adb install -r "$APK" > "$OUT/install.out" 2>&1 || { echo "E16: adb install -r of $APK failed:" >&2; cat "$OUT/install.out" >&2; exit 4; }
fi

# =============================================================================================== the child
if [ "${E16_CHILD:-1}" = "1" ]; then
  layout_restore "$BASELINE" > "$OUT/child-seed.out" 2>&1; echo $? > "$OUT/child-seed.rc"
  adb shell pm revoke app.tileshell android.permission.RECORD_AUDIO > "$OUT/child-mic-revoke.out" 2>&1
  perm_granted RECORD_AUDIO > "$OUT/child-mic-during.txt"
  rec_events > "$OUT/child-rec-events-before.txt"
  # phase 16 e1.sh's run_row: the child's own row directory moved aside and put back, the new run kept under E16/.
  dir="$QAR/phase-15/E0"; keep="$QAR/phase-15/E0.p17-e16-aside"
  [ -e "$dir" ] && mv "$dir" "$keep"
  exec 9>&-          # the child takes the lock itself
  ( env "E0_BASELINE=$BASELINE" bash "$QAR/phase-15/scripts/e0.sh" > "$OUT/phase-15-E0.out" 2>&1; echo $? > "$OUT/phase-15-E0.rc" )
  adb shell pm grant app.tileshell android.permission.RECORD_AUDIO > "$OUT/child-mic-grant.out" 2>&1
  perm_granted RECORD_AUDIO > "$OUT/child-mic-after.txt"
  rec_events > "$OUT/child-rec-events-after.txt"
  rm -rf "$OUT/phase-15-E0.prev"; [ -e "$OUT/phase-15-E0" ] && mv "$OUT/phase-15-E0" "$OUT/phase-15-E0.prev"
  [ -e "$dir" ] && mv "$dir" "$OUT/phase-15-E0"
  [ -e "$keep" ] && mv "$keep" "$dir"
  take_device_lock
  if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
    adb install -r "$APK" > "$OUT/install-after-child.out" 2>&1 || { echo "E16: adb install -r of $APK failed:" >&2; cat "$OUT/install-after-child.out" >&2; exit 4; }
  fi
  adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 5
fi

# =============================================================================================== the row
row_begin E16 "routing: Music's face on the MUSIC tile only, Auxio's on Auxio's, no badge from the shell's own reminder"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
assert_eq "wake" "Awake" "$(wake_device)"

if [ "${E16_CHILD:-1}" = "1" ]; then
  log "--- child: phase 15 e0.sh (phase 10 E10–E13's tile rule) on this build, seeded with this phase's baseline"
  E0="$OUT/phase-15-E0/E0.txt"
  assert_eq "child: layout_restore of this phase's baseline before it" "0" "$(cat "$OUT/child-seed.rc")"
  assert_eq "child: RECORD_AUDIO was revoked for its span (no row uses the microphone)" "false" "$(cat "$OUT/child-mic-during.txt")"
  assert_eq "child: … and is granted back" "true" "$(cat "$OUT/child-mic-after.txt")"
  assert_contains "child: dumpsys audio's recording-activity log is read (its header)" "Events log: recording activity" "$(adb shell dumpsys audio 2>/dev/null | tr -d '\r')"
  assert_eq "child: no audio capture on the device during it (the log gained no event)" "" "$(grep -vxF -f "$OUT/child-rec-events-before.txt" "$OUT/child-rec-events-after.txt")"
  assert_eq "child: phase 15 e0.sh rc" "0" "$(cat "$OUT/phase-15-E0.rc")"
  assert_contains "child: phase 15 E0: 0 failed" " passed, 0 failed" "$(tail -3 "$E0" 2>/dev/null)"
  assert_contains "child: it ran on this build (its header's apk match)" "yes" "$(grep -m1 '^apk match' "$E0" 2>/dev/null)"
  assert_contains "child: it was seeded with this phase's baseline" "PASS  baseline layout restored" "$(cat "$E0" 2>/dev/null)"
  for line in "the shell's Music session is playing" "the MUSIC slot tile grew" "the MUSIC tile is drawn bigger while playing" \
              "the MUSIC tile carries the transport strip" "the tile's play/pause paused the shell's Music" \
              "the face moved to Auxio's tile" "and left the MUSIC tile"; do
    assert_eq "child: PASS  $line" "1" "$(grep -cE "^PASS  $(printf '%s' "$line" | sed 's/[][\.*^$/]/\\&/g')( |$)" "$E0" 2>/dev/null)"
  done
  record "child: the take e0.sh's step 4 asks for" "not made: RECORD_AUDIO revoked, so its record button starts nothing; its notification checks ran on the timer's notification ($(grep -m1 'badges=' "$E0" 2>/dev/null | sed 's/^ *//'))"
else
  record "child" "SKIPPED (E16_CHILD=0): a development run, not the gate's"
fi

# ----------------------------------------------------------------------------------------------- setup
layout_restore "$BASELINE" > "$OUT/baseline.out" 2>&1; assert_eq "layout_restore of the baseline" "0" "$?"
VOL3="$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
VOL5="$(adb shell cmd media_session volume --stream 5 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
HAD_FIX="$(adb shell ls /sdcard/Music/tessera-qa 2>/dev/null | grep -c mp3)"
note "media volume before: ${VOL3:-?}; notification volume before: ${VOL5:-?}; Music fixtures present before: $HAD_FIX"
music_mute; music_fixtures
adb shell cmd media_session volume --stream 5 --set 0 >/dev/null 2>&1    # the reminder's sound stays inside the emulator
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
c6; ensure_start
record "reminders of this row left by an earlier run, removed before the legs" "$(remove_row_reminders)"
ensure_start

# ----------------------------------------------------------------------------------------------- M: the shell's Music
log "--- M: a track playing in the shell's Music"
start_gdump "$OUT/M-before.xml"
assert_start "M before" "$OUT/M-before.xml"
for id in "$MUSIC_ID" "${OURS[@]}" "$AUXIO_ID"; do
  assert_ne "M: tile:$id is on Start before" "" "$(tile_field "$OUT/M-before.xml" "$id" bounds)"
done
for id in "${OURS[@]}"; do
  assert_eq "M: before, tile:$id carries no now-playing tag" "" "$(playing_tags "$OUT/M-before.xml" "$id")"
done
bloom="$(audio_id Bloom)"; note "MediaStore id of the fixture track Bloom: $bloom"
assert_ne "M: the fixture track Bloom is in MediaStore" "" "$bloom"
M_MARK="$(ring_mark)"
adb shell am start -W -n app.tileshell/.music.MusicActivity >/dev/null 2>&1; sleep 4
dump_ui "$OUT/M-music.xml"; tap_node "$OUT/M-music.xml" "music_pivot_header:songs"; sleep 3
dump_ui "$OUT/M-songs.xml"; tap_node "$OUT/M-songs.xml" "music_song:$bloom"; sleep 3
assert_eq "M: the shell's Music session is playing" "PLAYING" "$(session_state app.tileshell androidx.media3.session.id.music)"
start_gdump "$OUT/M-playing.xml"; screencap "$OUT/M-playing.png"
ring_since "$M_MARK" > "$OUT/M-slice.txt"
assert_start "M playing" "$OUT/M-playing.xml"
assert_eq "M: still playing when Start was dumped" "PLAYING" "$(session_state app.tileshell androidx.media3.session.id.music)"
assert_contains "M: the routing line names Music's component" \
  "[music] session app.tileshell id=music -> cmp:app.tileshell/app.tileshell.music.MusicActivity" "$(cat "$OUT/M-slice.txt")"
before="$(tile_field "$OUT/M-before.xml" "$MUSIC_ID" size)"; during="$(tile_field "$OUT/M-playing.xml" "$MUSIC_ID" size)"
note "MUSIC tile $before -> $during"
assert_eq "M: the MUSIC slot tile grew" "yes" "$(bigger "$before" "$during")"
assert_eq "M: the MUSIC tile carries the face's transport strip (tile_controls:$MUSIC_ID)" "controls=yes" "$(tile_field "$OUT/M-playing.xml" "$MUSIC_ID" controls)"
assert_contains "M: … and the playing track's title" "Bloom" "$(tile_field "$OUT/M-playing.xml" "$MUSIC_ID" texts)"
assert_contains "M: … with its play/pause control" "tile_control:$MUSIC_ID:PLAY_PAUSE" "$(playing_tags "$OUT/M-playing.xml" "$MUSIC_ID")"
assert_ours_untouched "M" "$OUT/M-before.xml" "$OUT/M-playing.xml" "Bloom"
record "M: E13's white glyphs over a bright cover" "not asserted: the fixture tracks carry no cover; the tile is in M-playing.png"
# E13's controls: the tile's own play/pause drives the session.
tap_node "$OUT/M-playing.xml" "tile_control:$MUSIC_ID:PLAY_PAUSE"; sleep 2
assert_eq "M: the tile's play/pause paused the shell's Music (its controls drive playback)" "PAUSED" "$(session_state app.tileshell androidx.media3.session.id.music)"
gdump "$OUT/M-paused.xml"
assert_start "M paused" "$OUT/M-paused.xml"
assert_ours_untouched "M paused" "$OUT/M-before.xml" "$OUT/M-paused.xml" "Bloom"
c6; ensure_start

# ----------------------------------------------------------------------------------------------- A: Auxio
log "--- A: a track playing in Auxio (a pinned tile)"
start_gdump "$OUT/A-before.xml"
assert_start "A before" "$OUT/A-before.xml"
zoo="$(audio_id "Zoo Station")"; note "MediaStore id of the fixture track Zoo Station: $zoo"
assert_ne "A: the fixture track Zoo Station is in MediaStore" "" "$zoo"
A_MARK="$(ring_mark)"
q "am start -W -a android.intent.action.VIEW -d content://media/external/audio/media/$zoo -t audio/mpeg -p org.oxycblt.auxio" > "$OUT/A-auxio-start.txt"
sleep 5
assert_eq "A: Auxio is playing" "PLAYING" "$(session_state org.oxycblt.auxio org.oxycblt.auxio)"
start_gdump "$OUT/A-playing.xml"; screencap "$OUT/A-playing.png"
ring_since "$A_MARK" > "$OUT/A-slice.txt"
assert_start "A playing" "$OUT/A-playing.xml"
assert_eq "A: the face is on Auxio's tile" "controls=yes" "$(tile_field "$OUT/A-playing.xml" "$AUXIO_ID" controls)"
assert_eq "A: … and not on the MUSIC tile" "controls=no" "$(tile_field "$OUT/A-playing.xml" "$MUSIC_ID" controls)"
assert_ours_untouched "A" "$OUT/A-before.xml" "$OUT/A-playing.xml" "Zoo Station"
adb shell cmd media_session dispatch pause >/dev/null 2>&1; sleep 2
adb shell am force-stop org.oxycblt.auxio
c6; ensure_start

# ----------------------------------------------------------------------------------------------- R: a reminder fires
log "--- R: a phase 03 reminder fires — no badge on the Photos tile or the Camera row tile"
assert_absent "R: no reminder of this row is stored before" "$REMINDER_TEXT" "$(reminders_json)"
start_gdump "$OUT/R-before.xml"
assert_start "R before" "$OUT/R-before.xml"
R_MARK="$(ring_mark)"
make_typed_reminder "remind me to check the $REMINDER_TEXT in 1 minute"
ring_since "$R_MARK" > "$OUT/R-made.txt"
ADDED="$(grep -F '[reminders] added ' "$OUT/R-made.txt" | grep -F "$REMINDER_TEXT" | tail -1)"
note "R: $(printf '%s' "$ADDED" | sed 's/^.*wall=[0-9]* //')"
assert_contains "R: the reminder is stored, a time reminder" "kind=TIME" "$ADDED"
RID="$(printf '%s' "$ADDED" | sed -n 's/.*\[reminders\] added \([^ ]*\) .*/\1/p')"
assert_ne "R: its id" "" "$RID"
ensure_start
FIRED=""
for i in $(seq 1 30); do
  FIRED="$(ring_since "$R_MARK" | grep -F "[reminders] fired $RID " | tail -1)"
  [ -n "$FIRED" ] && break
  sleep 5
done
note "R: $(printf '%s' "$FIRED" | sed 's/^.*wall=[0-9]* //')"
assert_contains "R: the reminder fired" "[reminders] fired $RID (alarm)" "$FIRED"
sleep 8                                   # the heads-up banner leaves
start_gdump "$OUT/R-fired.xml"; screencap "$OUT/R-fired.png"
ring_since "$R_MARK" > "$OUT/R-slice.txt"
shell_notifications > "$OUT/R-notifications.txt"
diag > "$OUT/R-listener.txt"
assert_start "R fired" "$OUT/R-fired.xml"
assert_contains "R: positive control — the shell's reminder notification is posted" "$REMINDER_TEXT" "$(cat "$OUT/R-notifications.txt")"
assert_contains "R: positive control — the listener is connected" "listener connected=true" "$(cat "$OUT/R-listener.txt")"
assert_eq "R: positive control — the listener counted the shell's notification" "yes" \
  "$(grep -m1 'badges=' "$OUT/R-listener.txt" | python3 -c 'import re, sys; m = re.search(r"app\.tileshell=(\d+)", sys.stdin.read()); print("yes" if m and int(m.group(1)) >= 1 else "no")')"
note "R: $(grep -m1 'badges=' "$OUT/R-listener.txt" | sed 's/^ *//')"
for id in "${OURS[@]}"; do
  assert_ne "R: tile:$id is in the dump" "" "$(tile_field "$OUT/R-fired.xml" "$id" bounds)"
  assert_eq "R: tile:$id shows no badge (no badge:$id node)" "no" "$(has_node "$OUT/R-fired.xml" "badge:$id")"
  assert_eq "R: … tiles.py agrees (badge=no)" "badge=no" "$(tile_field "$OUT/R-fired.xml" "$id" badge)"
  assert_absent "R: tile:$id shows none of the reminder's text" "qa17" "$(tile_field "$OUT/R-fired.xml" "$id" texts | tr 'A-Z' 'a-z')"
  assert_eq "R: tile:$id has the bounds it had" "$(tile_field "$OUT/R-before.xml" "$id" bounds)" "$(tile_field "$OUT/R-fired.xml" "$id" bounds)"
done

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
c6                                        # the force-stop takes the shell's notifications with it
# A fired one-off reminder is completed, not deleted (it moves to History): the row removes its own.
assert_eq "restore: the row's reminder removed through History (one)" "1" "$(remove_row_reminders)"
assert_absent "restore: the reminder is gone from the store" "$REMINDER_TEXT" "$(reminders_json)"
assert_absent "restore: its notification is gone" "$REMINDER_TEXT" "$(shell_notifications)"
adb shell am force-stop org.oxycblt.auxio
if [ "$HAD_FIX" = 0 ]; then
  adb shell rm -rf /sdcard/Music/tessera-qa
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
fi
[ -n "$VOL3" ] && adb shell cmd media_session volume --stream 3 --set "$VOL3" >/dev/null 2>&1
[ -n "$VOL5" ] && adb shell cmd media_session volume --stream 5 --set "$VOL5" >/dev/null 2>&1
assert_eq "restore: the media and notification volumes are as found" "${VOL3:-?} ${VOL5:-?}" \
  "$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+') $(adb shell cmd media_session volume --stream 5 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
assert_eq "restore: RECORD_AUDIO held" "true" "$(perm_granted RECORD_AUDIO)"
layout_restore "$BASELINE" > "$OUT/restore.out" 2>&1; assert_eq "restore: layout_restore of the baseline" "0" "$?"
ensure_start
row_end
