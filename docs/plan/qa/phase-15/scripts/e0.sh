#!/usr/bin/env bash
# E0 — live-tile routing for the shell's own apps (build task 0; phase 10 E10–E13's tile rule, first emulator driver;
# phase 17 E16's rows cut to this phase's tiles; T15-35). The shell's Music puts its face and growth on the MUSIC slot
# tile only; Auxio's playback moves the face to Auxio's pinned tile; a recording played in Voice Recorder goes to no
# tile; and the shell's own notifications (a running timer, a take recording) reach the listener (positive control) but
# give no in-APK tile a count or content. The routing line names the content key (INDEX Change Log 2026-09-23, task 0).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"
. "$QROOT/phase-02/scripts/layout.sh"
MUSIC_FIXDIR="$QROOT/phase-01/MUSIC6-fixtures"
. "$QROOT/phase-01/scripts/music_lib.sh"
row_begin E0 "live-tile routing: Music to its slot tile, Auxio to its tile, recordings and shell notifications to none"

TILES="$(dirname "$0")/tiles.py"
MUSIC_ID="slot:MUSIC"
CLOCK_ID="app:app.tileshell/app.tileshell.clock.ClockActivity:0"
CALC_ID="app:app.tileshell/app.tileshell.calculator.CalculatorActivity:0"
REC_ID="app:app.tileshell/app.tileshell.recorder.RecorderActivity:0"
AUXIO_ID="app:org.oxycblt.auxio/org.oxycblt.auxio.MainActivity:0"
IN_APK=("$CLOCK_ID" "$CALC_ID" "$REC_ID")

# One tile's field from tiles.py (bounds | size | controls | badge | texts).
tile_field() { # dump.xml id field
  python3 "$TILES" "$1" "$2" | awk -F'\t' -v f="$3" '
    { m["bounds"] = $2; m["size"] = $3; m["controls"] = $4; m["badge"] = $5; m["texts"] = $6 }
    END { print m[f] }'
}
# The PlaybackState name of a package's session whose header carries <tag> (dumpsys media_session).
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
start_dump() { # out.xml
  adb shell input keyevent KEYCODE_HOME; sleep 2
  gdump "$1"
  python3 "$TILES" "$1" > "${1%.xml}.tiles.txt"
}
media_id() { # title -> MediaStore _id
  adb shell content query --uri content://media/external/audio/media --projection _id --where "\"title='$1'\"" \
    | tr -d '\r' | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2
}

# ---------------------------------------------------------------- baseline
assert_clock_empty "baseline"
layout_restore "$P15/baseline_layout.json"
assert_eq "baseline layout restored (the three apps and Auxio pinned)" 0 $?
# RV12: the media volume and the Music fixtures are put back as found (music_lib's mute and its six 90-s tracks).
VOL0="$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
HAD_FIX="$(adb shell ls /sdcard/Music/tessera-qa 2>/dev/null | grep -c mp3)"
note "media volume before: ${VOL0:-?}; Music fixtures present before: $HAD_FIX"
music_mute
music_fixtures
push_fixture_recordings
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
start_dump "$ROW_DIR/start_before.xml"
for id in "$MUSIC_ID" "${IN_APK[@]}" "$AUXIO_ID"; do
  assert_ne "tile $id is on Start before" "" "$(tile_field "$ROW_DIR/start_before.xml" "$id" bounds)"
done

# ---------------------------------------------------------------- 1. the shell's Music
MARK="$(ring_mark)"
bloom="$(media_id Bloom)"
note "MediaStore id of the fixture track Bloom: $bloom"
adb shell am start -W -n app.tileshell/.music.MusicActivity >/dev/null 2>&1; sleep 4
dump_ui "$ROW_DIR/music_open.xml"
tap_node "$ROW_DIR/music_open.xml" "music_pivot_header:songs"; sleep 3
dump_ui "$ROW_DIR/music_songs.xml"
tap_node "$ROW_DIR/music_songs.xml" "music_song:$bloom"; sleep 3
assert_eq "the shell's Music session is playing" PLAYING "$(session_state app.tileshell androidx.media3.session.id.music)"
start_dump "$ROW_DIR/start_music.xml"
ring_since "$MARK" > "$ROW_DIR/ring_music.txt"
assert_contains "routing line names Music's component" \
  "[music] session app.tileshell id=music -> cmp:app.tileshell/app.tileshell.music.MusicActivity" "$(cat "$ROW_DIR/ring_music.txt")"
assert_contains "the MUSIC slot tile grew" "tiles for cmp:app.tileshell/app.tileshell.music.MusicActivity grew" "$(cat "$ROW_DIR/ring_music.txt")"
before="$(tile_field "$ROW_DIR/start_before.xml" "$MUSIC_ID" size)"; during="$(tile_field "$ROW_DIR/start_music.xml" "$MUSIC_ID" size)"
note "MUSIC tile $before -> $during"
assert_eq "the MUSIC tile is drawn bigger while playing" yes \
  "$(python3 -c 'import sys,re; a=[int(x) for x in re.findall(r"\d+",sys.argv[1])]; b=[int(x) for x in re.findall(r"\d+",sys.argv[2])]; print("yes" if len(a)==2 and len(b)==2 and b[0]*b[1] > a[0]*a[1] else "no")' "$before" "$during")"
assert_eq "the MUSIC tile carries the transport strip" "controls=yes" "$(tile_field "$ROW_DIR/start_music.xml" "$MUSIC_ID" controls)"
assert_contains "the MUSIC tile shows the playing track" "Bloom" "$(tile_field "$ROW_DIR/start_music.xml" "$MUSIC_ID" texts)"
# "Keep their bounds": the in-APK tiles are not grown (their size); where the MUSIC tile's growth reflows the grid they
# may move, which ActiveTiles' "drawn one size bigger" does by design — their position is recorded, not asserted.
for id in "${IN_APK[@]}"; do
  assert_eq "$id keeps its size while Music plays" "$(tile_field "$ROW_DIR/start_before.xml" "$id" size)" "$(tile_field "$ROW_DIR/start_music.xml" "$id" size)"
  assert_eq "$id carries no transport" "controls=no" "$(tile_field "$ROW_DIR/start_music.xml" "$id" controls)"
  assert_absent "$id shows no now-playing text" "Bloom" "$(tile_field "$ROW_DIR/start_music.xml" "$id" texts)"
  note "$id bounds $(tile_field "$ROW_DIR/start_before.xml" "$id" bounds) -> $(tile_field "$ROW_DIR/start_music.xml" "$id" bounds)"
done
adb exec-out screencap -p > "$ROW_DIR/start_music.png"
# E13's controls: the tile's own play/pause pauses the session. A paused session then gets phase 10's paused plan
# (MusicRules.plan: no transport, no growth; a tap opens the player), so the tile is back at its stored size.
gtap "$ROW_DIR/start_music.xml" "tile_control:$MUSIC_ID:PLAY_PAUSE"; sleep 2
assert_eq "the tile's play/pause paused the shell's Music" PAUSED "$(session_state app.tileshell androidx.media3.session.id.music)"
gdump "$ROW_DIR/start_music_paused.xml"
assert_eq "paused: the MUSIC tile carries no transport (phase 10's paused plan)" "controls=no" "$(tile_field "$ROW_DIR/start_music_paused.xml" "$MUSIC_ID" controls)"
assert_eq "paused: the MUSIC tile is back at its stored size" "$before" "$(tile_field "$ROW_DIR/start_music_paused.xml" "$MUSIC_ID" size)"

# ---------------------------------------------------------------- 2. Auxio
MARK="$(ring_mark)"
zoo="$(media_id "Zoo Station")"
adb shell am start -W -a android.intent.action.VIEW -d "content://media/external/audio/media/$zoo" -t audio/mpeg \
  -p org.oxycblt.auxio > "$ROW_DIR/auxio_start.txt" 2>&1
sleep 5
assert_eq "Auxio is playing" PLAYING "$(session_state org.oxycblt.auxio org.oxycblt.auxio)"
start_dump "$ROW_DIR/start_auxio.xml"
ring_since "$MARK" > "$ROW_DIR/ring_auxio.txt"
assert_eq "the face moved to Auxio's tile" "controls=yes" "$(tile_field "$ROW_DIR/start_auxio.xml" "$AUXIO_ID" controls)"
assert_eq "and left the MUSIC tile" "controls=no" "$(tile_field "$ROW_DIR/start_auxio.xml" "$MUSIC_ID" controls)"
for id in "${IN_APK[@]}"; do
  assert_eq "$id carries no transport while Auxio plays" "controls=no" "$(tile_field "$ROW_DIR/start_auxio.xml" "$id" controls)"
done
gtap "$ROW_DIR/start_auxio.xml" "tile_control:$AUXIO_ID:PLAY_PAUSE"; sleep 2
assert_eq "Auxio paused from its tile" PAUSED "$(session_state org.oxycblt.auxio org.oxycblt.auxio)"
# Setup for step 3, not a verdict: Auxio stopped either way, so the capture before the recorder step is settled.
if [ "$(session_state org.oxycblt.auxio org.oxycblt.auxio)" = PLAYING ]; then
  note "Auxio still playing (no control on its tile): paused through its session for step 3's capture"
  adb shell cmd media_session dispatch pause >/dev/null 2>&1; sleep 3
fi

# ---------------------------------------------------------------- 3. a recording in Voice Recorder
start_dump "$ROW_DIR/start_before_rec.xml"
other="$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"_display_name='other.m4a'\"" | tr -d '\r' | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)"
note "MediaStore id of other.m4a: $other"
adb shell am start -W -n app.tileshell/.recorder.RecorderActivity --es page list >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/rec_list.xml"
MARK="$(ring_mark)"
# other.m4a is 3 s (E14 pins it), shorter than a Start dump, so the take is paused as soon as it plays: the session stays
# alive (PAUSED) for the dump. What happened WHILE it played is read from the ring slice (the producer's own lines): its
# routing line and no tile growth. A misrouted paused session would still put a flip face on a tile (phase 10's paused
# plan), which the dump against the capture before would show.
tap_node "$ROW_DIR/rec_list.xml" "rec_row:$other"; sleep 0.4
s0="$(session_state app.tileshell androidx.media3.session.id.recorder)"
dump_ui "$ROW_DIR/rec_playing.xml"
tap_node "$ROW_DIR/rec_playing.xml" rec_play; sleep 0.5
note "recorder session right after the tap: $s0"
assert_eq "the recorder session played" PLAYING "$s0"
adb shell input keyevent KEYCODE_HOME; sleep 1
s1="$(session_state app.tileshell androidx.media3.session.id.recorder)"
gdump "$ROW_DIR/start_rec.xml"
s2="$(session_state app.tileshell androidx.media3.session.id.recorder)"
python3 "$TILES" "$ROW_DIR/start_rec.xml" > "$ROW_DIR/start_rec.tiles.txt"
assert_eq "the recorder session is alive (paused) before and after the Start dump" "PAUSED PAUSED" "$s1 $s2"
ring_since "$MARK" > "$ROW_DIR/ring_rec.txt"
assert_contains "the recorder session routes to no tile" "[music] session app.tileshell id=recorder -> none" "$(cat "$ROW_DIR/ring_rec.txt")"
assert_absent "no tile grew for it" " grew " "$(grep -F '[tile_size]' "$ROW_DIR/ring_rec.txt")"
assert_absent "nothing was published for it" "source=music:app.tileshell" "$(grep -F '[engine] publish' "$ROW_DIR/ring_rec.txt")"
assert_eq "every tile's bounds and tags equal the capture before" "$(cut -f1-5 "$ROW_DIR/start_before_rec.tiles.txt")" "$(cut -f1-5 "$ROW_DIR/start_rec.tiles.txt")"
sleep 4

# ---------------------------------------------------------------- 4. the shell's own notifications
music_pause() { adb shell cmd media_session dispatch pause >/dev/null 2>&1; }
[ "$(session_state app.tileshell androidx.media3.session.id.music)" = PLAYING ] && music_pause
timer="$(api_timer 1800 E0timer)"
assert_ne "a timer is running" "" "$timer"
adb shell am start -W -n app.tileshell/.recorder.RecorderActivity --es page record >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/rec_record.xml"
tap_node "$ROW_DIR/rec_record.xml" rec_button; sleep 3
adb shell dumpsys notification --noredact | tr -d '\r' > "$ROW_DIR/notifications.txt"
shell_notifications > "$ROW_DIR/shell_notifications.txt"
assert_ne "the shell has notifications posted (timer, take)" 0 "$(grep -c 'NotificationRecord' "$ROW_DIR/shell_notifications.txt")"
start_dump "$ROW_DIR/start_notif.xml"
adb exec-out screencap -p > "$ROW_DIR/start_notif.png"
diag > "$ROW_DIR/listener.txt"
assert_contains "positive control: the listener is connected" "listener connected=true" "$(cat "$ROW_DIR/listener.txt")"
assert_eq "positive control: the listener counted the shell's notifications" yes \
  "$(grep -m1 'badges=' "$ROW_DIR/listener.txt" | python3 -c 'import re,sys; m=re.search(r"app\.tileshell=(\d+)", sys.stdin.read()); print("yes" if m and int(m.group(1)) >= 1 else "no")')"
note "$(grep -m1 'badges=' "$ROW_DIR/listener.txt")"
for id in "$MUSIC_ID" "${IN_APK[@]}"; do
  assert_eq "$id shows no count" "badge=no" "$(tile_field "$ROW_DIR/start_notif.xml" "$id" badge)"
  assert_absent "$id shows no timer content" "E0timer" "$(tile_field "$ROW_DIR/start_notif.xml" "$id" texts)"
done

# ---------------------------------------------------------------- restore
adb shell am start -W -n app.tileshell/.recorder.RecorderActivity --es page record >/dev/null 2>&1; sleep 2
dump_ui "$ROW_DIR/rec_stop.xml"
tap_node "$ROW_DIR/rec_stop.xml" rec_button; sleep 3
take="$(adb shell content query --uri content://media/external/audio/media --projection _id:_display_name:owner_package_name \
  --where "\"owner_package_name='app.tileshell'\"" | tr -d '\r')"
note "the take(s): $take"
for p in $(adb shell content query --uri content://media/external/audio/media --projection _data --where "\"owner_package_name='app.tileshell'\"" | tr -d '\r' | grep -oE '_data=[^,]+' | cut -d= -f2); do
  adb shell rm -f "$p"
done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
[ -n "$timer" ] && app_delete_timer "$timer"
adb shell am force-stop org.oxycblt.auxio
remove_fixture_recordings
if [ "$HAD_FIX" = 0 ]; then
  adb shell rm -rf /sdcard/Music/tessera-qa
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
fi
[ -n "$VOL0" ] && adb shell cmd media_session volume --stream 3 --set "$VOL0" >/dev/null 2>&1
ring_save
layout_restore "$P15/baseline_layout.json"
assert_clock_empty "restore"
row_end
