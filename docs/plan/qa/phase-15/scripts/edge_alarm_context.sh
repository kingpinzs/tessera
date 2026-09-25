#!/usr/bin/env bash
# EDGE_ALARM_CONTEXT — the phase doc's "Alarm firing while …" edge-case bullet: an alarm firing
#   1. while Tess is listening → the session hides (`[cortana] session hidden` after the fired line, no cortana_session
#      node in the gesture driver's all-windows dump). The fire is timed INTO the listen: the mic tap and the clock jump
#      go in one `adb shell` string, and the precondition — the :speech ring's `listening for pid=… (cortana)` before
#      the fired line and its `asr: final` after it — is ASSERTED; a void attempt is retried (up to three), recorded;
#   2. while Start is in edit mode (a tile held, `edit_disc:unpin` on screen) → edit mode ends (`[edit] exit first
#      frame` after the fired line, no edit disc in the dump);
#   3. while the shell's Music plays (phase 10's player, the MUSIC6 fixtures) → the player pauses on the transient focus
#      loss (its Media3 session PAUSED, no started USAGE_MEDIA player) and, per phase 10's E9, does not resume by itself
#      after the ring ends — RECORDED, not asserted;
#   4. while the recorder runs → the take continues (phase recording, elapsed_ms advancing across the ring, no
#      `[recorder] paused` line in the :recorder ring, the saved take as long as its clock), and the alarm sound is on
#      another stream (the shell's player on USAGE_ALARM while the take captures);
#   5. while glance (phase 07) shows → NOT RUN: phase 07 is not built (INDEX row 07 pending), the build has no glance
#      component — both recorded.
# Every section: an alarm 2 min ahead through the AlarmClock API, the ring's slices saved as ring_<what>_<ring>.txt right
# after the fire, the toast dismissed, RV12's clock restore, the alarm deleted through the app and the store asserted
# empty. Restore: the Music fixtures / volume as found, the take deleted through the app, the overlay grant allow.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"; . "$(dirname "$0")/rec.sh"
MUSIC_FIXDIR="$QROOT/phase-01/MUSIC6-fixtures"
. "$QROOT/phase-01/scripts/music_lib.sh"

row_begin EDGE_ALARM_CONTEXT "an alarm firing while Tess listens, Start is in edit mode, Music plays, the recorder runs; glance"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring
assert_contains "baseline: the overlay grant is allow (the in-use toast is the overlay)" "allow" "$(adb shell appops get app.tileshell SYSTEM_ALERT_WINDOW | tr -d '\r' | head -1)"

wall_of() { printf '%s\n' "$1" | grep -oE 'wall=[0-9]+' | head -1 | cut -d= -f2; }
# An alarm 2 min ahead (the AlarmClock API, the brief's seeding route); sets ID and AT.
seed() { # name
  local now h m
  now="$(device_ms)"; read -r h m <<< "$(device_hm $(( now + 120000 )))"
  ID="$(api_alarm "$h" "$m" "$1")"
  AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"
  assert_ne "$1: the alarm was created and armed" "" "$ID"
}
# The toast's Dismiss (the gesture driver's dump, C-10); a ring left up is ended by dismiss_any_ring and the log says so.
end_ring() { # label
  local d="$ROW_DIR/${1}_ring.xml" m
  gdump_for "$d" ring_dismiss; screencap "$ROW_DIR/${1}_ring.png"
  m="$(ring_mark)"
  [ "$(has_node "$d" ring_dismiss)" = yes ] && gtap "$d" ring_dismiss
  sleep 2.5
  if [ -z "$(ring_since "$m" | grep -F 'ring ended')" ]; then
    note "end_ring $1: no ring ended line after the Dismiss tap; the ring is ended by dismiss_any_ring"
    dismiss_any_ring
  fi
}
section_clear() { # label
  app_delete_all
  assert_eq "$1: the section left no alarm behind" "" "$(alarm_ids | paste -sd,)"
}
section_restore() { # label
  ring_save launcher
  clock_restore
  section_clear "$1"
}

# ================================================================== 1. while Tess is listening ==========================================
TESS_OK=no
for attempt in 1 2 3; do
  seed "Tess"
  adb shell input keyevent KEYCODE_HOME; sleep 1.5
  cortana_assist; sleep 4
  dump_ui "$ROW_DIR/tess_open_$attempt.xml"
  MB="$(bounds "$ROW_DIR/tess_open_$attempt.xml" cortana_text_box_mic)"
  note "attempt $attempt: session open=$(has_node "$ROW_DIR/tess_open_$attempt.xml" cortana_session), mic button [$MB]"
  [ -n "$MB" ] || { note "attempt $attempt: no mic button in the dump"; cortana_close; adb shell input keyevent KEYCODE_HOME; section_restore "tess attempt $attempt"; continue; }
  read -r x1 y1 x2 y2 <<< "$MB"
  # The clock goes to 8 s before the alarm first, then a loop ON THE DEVICE waits for its own clock to reach 1 s before
  # it and taps the mic — so the listen, which with nothing said ends by endpoint ~2.2 s after it starts (probe on 5558:
  # 45.586 → 47.829), is open when the alarm fires. (Run 1 tapped and set the clock in one string: the alarm was
  # delivered 4.2 s after the clock change, 2.8 s after the listen had ended — AlarmManager's re-batch after a time
  # change, so the change now comes first.)
  jump_clock $(( AT - 8000 )) >/dev/null
  MARK="$(ring_mark)"
  adb shell "while [ \$(date +%s%3N) -lt $(( AT - 1000 )) ]; do :; done; input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))" >/dev/null 2>&1
  FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 20)"
  sleep 3
  ring_since "$MARK" speech > "$ROW_DIR/ring_tess_speech.txt"
  ring_since "$MARK" launcher > "$ROW_DIR/ring_tess_launcher.txt"
  LSTART="$(grep -F "[speech] listening for pid=" "$ROW_DIR/ring_tess_speech.txt" | grep -F '(cortana)' | head -1)"
  LEND="$(grep -F '[speech] asr: final gen=' "$ROW_DIR/ring_tess_speech.txt" | head -1)"
  FW="$(wall_of "$FIRED")"; SW="$(wall_of "$LSTART")"; EW="$(wall_of "$LEND")"
  note "attempt $attempt: listen start wall=${SW:-none}, fired wall=${FW:-none}, listen end wall=${EW:-none (still open)}"
  if [ -n "$FW" ] && [ -n "$SW" ] && [ "$SW" -lt "$FW" ] && { [ -z "$EW" ] || [ "$EW" -gt "$FW" ]; }; then
    TESS_OK=yes; break
  fi
  record "Tess attempt $attempt void" "the alarm did not fire inside the listen (start ${SW:-none}, fired ${FW:-none}, end ${EW:-none}); re-seeded"
  end_ring "tess_void_$attempt"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  section_restore "tess attempt $attempt"
done
assert_eq "Tess: the alarm fired while Tess was listening (listen start < fired < listen end, :speech + launcher rings)" yes "$TESS_OK"
if [ "$TESS_OK" = yes ]; then
  gdump_windows "$ROW_DIR/tess_ring.xml"; screencap "$ROW_DIR/tess_ring.png"
  note "windows at the ring: $(gwindows "$ROW_DIR/tess_ring.xml")"
  HID="$(grep -F '[cortana] session hidden' "$ROW_DIR/ring_tess_launcher.txt" | head -1)"
  note "session hidden line: ${HID:-none}"
  assert_eq "Tess: the session hides — [cortana] session hidden at or after the fired line" yes "$([ -n "$HID" ] && [ "$(wall_of "$HID")" -ge "$FW" ] && echo yes || echo "no (${HID:-no line})")"
  assert_eq "Tess: … and no cortana_session node is in the all-windows dump" no "$(has_node "$ROW_DIR/tess_ring.xml" cortana_session)"
  assert_ne "Tess: the alarm rings (an ALARM player of the shell is started)" 0 "$(alarm_player_started)"
  record "Tess: the ring's surface line" "$(grep -oE '\[alarms\] surface: [a-z-]+ [^ ]+' "$ROW_DIR/ring_tess_launcher.txt" | head -1)"
  end_ring tess
  # A session that did not hide is closed so the next section starts on Start.
  dump_ui "$ROW_DIR/tess_after.xml"
  [ "$(has_node "$ROW_DIR/tess_after.xml" cortana_session)" = yes ] && { note "the session was still up after the ring; closed with Home"; adb shell input keyevent KEYCODE_HOME; sleep 2; }
fi
adb shell input keyevent KEYCODE_HOME; sleep 1
section_restore "tess"

# ================================================================== 2. while Start is in edit mode ======================================
seed "Edit"
adb shell input keyevent KEYCODE_HOME; sleep 2.5
dump_ui "$ROW_DIR/edit_start.xml"
TILE="$(grep -o 'resource-id="tile:[^"]*"' "$ROW_DIR/edit_start.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
note "the tile held: $TILE"
EMARK="$(ring_mark)"
hold_node "$ROW_DIR/edit_start.xml" "$TILE"; sleep 1.5
dump_ui "$ROW_DIR/edit_on.xml"; screencap "$ROW_DIR/edit_on.png"
assert_eq "Edit: Start is in edit mode before the alarm (edit_disc:unpin on screen)" yes "$(has_node "$ROW_DIR/edit_on.xml" 'edit_disc:unpin')"
assert_contains "Edit: … and the ring says edit mode on" "edit mode on" "$(ring_since "$EMARK" | grep -F '[edit] hold')"
MARK="$(ring_mark)"
jump_clock $(( AT - 3000 )) >/dev/null
FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 20)"
sleep 3
ring_since "$MARK" launcher > "$ROW_DIR/ring_edit_launcher.txt"
FW="$(wall_of "$FIRED")"
assert_ne "Edit: the alarm fired" "" "$FIRED"
gdump_windows "$ROW_DIR/edit_ring.xml"; screencap "$ROW_DIR/edit_ring.png"
note "windows at the ring: $(gwindows "$ROW_DIR/edit_ring.xml")"
assert_eq "Edit: the toast is up (ring_surface in the all-windows dump)" yes "$(has_node "$ROW_DIR/edit_ring.xml" ring_surface)"
EXIT="$(grep -F '[edit] exit first frame' "$ROW_DIR/ring_edit_launcher.txt" | head -1)"
note "edit exit line: ${EXIT:-none}"
assert_eq "Edit: edit mode ends — [edit] exit first frame at or after the fired line" yes "$([ -n "$EXIT" ] && [ -n "$FW" ] && [ "$(wall_of "$EXIT")" -ge "$FW" ] && echo yes || echo "no (${EXIT:-no line})")"
assert_eq "Edit: … and no edit disc is on Start in the all-windows dump" no "$(has_node "$ROW_DIR/edit_ring.xml" 'edit_disc:unpin')"
end_ring edit
dump_ui "$ROW_DIR/edit_after.xml"
if [ "$(has_node "$ROW_DIR/edit_after.xml" 'edit_disc:unpin')" = yes ]; then
  note "Start was still in edit mode after the ring; left with Back"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
fi
adb shell input keyevent KEYCODE_HOME; sleep 1
section_restore "edit"

# ================================================================== 3. while Music plays ================================================
VOL0="$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
HAD_FIX="$(adb shell ls /sdcard/Music/tessera-qa 2>/dev/null | grep -c mp3)"
note "media volume before: ${VOL0:-?}; Music fixtures present before: $HAD_FIX"
music_mute
music_fixtures
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
music_state() {
  adb shell dumpsys media_session | tr -d '\r' | python3 -c '
import re, sys
hit = False
for l in sys.stdin:
    if re.match(r"^\s+\S+ \S+/\S+/\d+ \(userId=\d+\)", l):
        hit = " app.tileshell/" in l and "androidx.media3.session.id.music" in l
    elif hit:
        m = re.search(r"state=PlaybackState \{state=([A-Z_]+)", l)
        if m: print(m.group(1)); break'
}
seed "Music"
bloom="$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"title='Bloom'\"" | tr -d '\r' | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)"
note "MediaStore id of the fixture track Bloom: ${bloom:-none}"
adb shell am start -W -n app.tileshell/.music.MusicActivity >/dev/null 2>&1; sleep 4
dump_ui "$ROW_DIR/music_open.xml"
tap_node "$ROW_DIR/music_open.xml" "music_pivot_header:songs"; sleep 3
dump_ui "$ROW_DIR/music_songs.xml"
tap_node "$ROW_DIR/music_songs.xml" "music_song:$bloom"; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 2
assert_eq "Music: the shell's Music session is playing before the alarm" PLAYING "$(music_state)"
assert_ne "Music: … a USAGE_MEDIA player of the shell is started" "" "$(wait_media_player 3)"
MARK="$(ring_mark)"
jump_clock $(( AT - 3000 )) >/dev/null
FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 20)"
assert_ne "Music: the alarm fired" "" "$FIRED"
ST=""; for _ in 1 2 3 4 5 6 7 8 9 10; do ST="$(music_state)"; [ "$ST" = PAUSED ] && break; sleep 0.5; done
ring_since "$MARK" launcher > "$ROW_DIR/ring_music_launcher.txt"
adb shell dumpsys audio | tr -d '\r' > "$ROW_DIR/audio_music_ring.txt"
assert_eq "Music: the player pauses on the transient focus loss (session PAUSED)" PAUSED "$ST"
assert_eq "Music: … no USAGE_MEDIA player of the shell is started" "" "$(media_player_started)"
assert_ne "Music: … while the alarm plays (an ALARM player of the shell is started)" 0 "$(alarm_player_started)"
record "Music: the focus events in dumpsys audio" "$(grep -E 'requestAudioFocus|abandonAudioFocus|dispatching onAudioFocusChange' "$ROW_DIR/audio_music_ring.txt" | tail -4 | sed 's/^ *//' | paste -sd'|' | cut -c1-400)"
end_ring music
sleep 6
record "Music: 6 s after the ring ended the player is (phase 10 E9: it does not resume by itself)" "$(music_state); a USAGE_MEDIA player started: $([ -n "$(media_player_started)" ] && echo yes || echo no)"
[ "$(music_state)" = PLAYING ] && adb shell cmd media_session dispatch pause >/dev/null 2>&1
adb shell cmd media_session dispatch pause >/dev/null 2>&1; sleep 1
section_restore "music"
if [ "$HAD_FIX" = 0 ]; then
  adb shell rm -rf /sdcard/Music/tessera-qa
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
fi
[ -n "$VOL0" ] && adb shell cmd media_session volume --stream 3 --set "$VOL0" >/dev/null 2>&1
note "restore: media volume $(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+'); fixtures present: $(adb shell ls /sdcard/Music/tessera-qa 2>/dev/null | grep -c mp3)"

# ================================================================== 4. while the recorder runs ==========================================
OWN0="$(own_count)"; note "the shell's takes before: $OWN0"
seed "Rec"
adb shell input keyevent KEYCODE_HOME; sleep 1
BEFORE="$(own_ids | tr '\n' ' ')"
rec_open record
rdump "$ROW_DIR/rec_open.xml"
# The :recorder slice runs from BEFORE the take's start, so its own `[recorder] start` line is the positive control that
# the ring was read (run 2's slice from the fire alone was empty, and "no paused line" passed on nothing).
RMARK="$(ring_mark)"
gtap "$ROW_DIR/rec_open.xml" rec_button
assert_eq "Rec: the take is recording before the alarm" recording "$(wait_phase recording 15)"
wait_elapsed 3000 20 >/dev/null
E0="$(rec_status elapsed_ms)"
MARK="$(ring_mark)"
jump_clock $(( AT - 3000 )) >/dev/null
FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 20)"
assert_ne "Rec: the alarm fired" "" "$FIRED"
sleep 3
rec_ring "$RMARK" > "$ROW_DIR/ring_rec_recorder.txt"
ring_since "$MARK" launcher > "$ROW_DIR/ring_rec_launcher.txt"
adb shell dumpsys audio | tr -d '\r' > "$ROW_DIR/audio_rec_ring.txt"
E1="$(rec_status elapsed_ms)"; sleep 2; E2="$(rec_status elapsed_ms)"
note "elapsed_ms: before the fire $E0, 3 s after it $E1, 2 s later $E2"
assert_eq "Rec: the take continues through the ring (phase recording)" recording "$(rec_status phase)"
assert_eq "Rec: … its clock advances during the ring (Δelapsed over 2 s ≥ 1500 ms)" yes "$([ -n "$E1" ] && [ -n "$E2" ] && [ $(( E2 - E1 )) -ge 1500 ] && echo yes || echo "no ($E1 -> $E2)")"
assert_contains "Rec: the :recorder slice was read (positive control: its [recorder] start line)" "[recorder] start " "$(cat "$ROW_DIR/ring_rec_recorder.txt")"
assert_absent "Rec: … and it holds no paused line from the take's start through the ring" "[recorder] paused" "$(cat "$ROW_DIR/ring_rec_recorder.txt")"
AP="$(alarm_players | grep 'state:started' | head -1)"
note "the shell's started ALARM player: $AP"
assert_contains "Rec: the alarm sound is on another stream — the shell's player is on USAGE_ALARM" "usage=USAGE_ALARM" "$AP"
CAP="$(grep -E 'source client=MIC' "$ROW_DIR/audio_rec_ring.txt" | grep -F 'pack:app.tileshell' | head -1 | sed 's/^ *//')"
note "the shell's active capture while the alarm plays: $CAP"
assert_contains "Rec: … while the take's microphone capture stays active and unsilenced (dumpsys audio's recording configuration)" "silenced:false" "$CAP"
end_ring rec
E3="$(rec_status elapsed_ms)"
tap_rec_button "$ROW_DIR/rec_stop.xml"
wait_phase idle 15 >/dev/null; sleep 1.5
rec_ring "$RMARK" > "$ROW_DIR/ring_rec_stop_recorder.txt"
TAKE="$(new_own_id "$BEFORE")"
DUR="$(ms_field "$(row_by_id "$TAKE")" duration)"
note "the take: id ${TAKE:-none}, MediaStore duration ${DUR:-?} ms; elapsed at the ring's end $E3"
assert_ne "Rec: the take was saved" "" "$TAKE"
assert_contains "Rec: … with its stop line in the :recorder ring" "[recorder] stop " "$(cat "$ROW_DIR/ring_rec_stop_recorder.txt")"
assert_eq "Rec: … and it is as long as its clock through the ring (MediaStore duration ≥ elapsed at the ring's end − 1 s)" yes "$([ -n "$DUR" ] && [ -n "$E3" ] && [ "$DUR" -ge $(( E3 - 1000 )) ] && echo yes || echo "no ($DUR vs $E3)")"
adb shell input keyevent KEYCODE_HOME; sleep 1
[ -n "$TAKE" ] && { app_delete_take "$TAKE" || note "restore: the take $TAKE could not be deleted in the app"; }
[ "$(own_count)" != "$OWN0" ] && note "sweeping leftovers -> $(purge_own_takes) remain"
assert_eq "Rec restore: the shell's takes are back to $OWN0" "$OWN0" "$(own_count)"
adb shell input keyevent KEYCODE_HOME; sleep 1
section_restore "rec"

# ================================================================== 5. while glance shows =================================================
GL="$(adb shell dumpsys package app.tileshell | tr -d '\r' | grep -ci glance)"
record "glance (phase 07): NOT RUN" "phase 07 is not built — INDEX row 07: $(grep -E '^\| 07 ' "$REPO/docs/plan/INDEX.md" | awk -F'|' '{print $4}' | tr -s ' '); components of app.tileshell naming glance (dumpsys package): $GL"

# ================================================================== restore ================================================================
adb shell cmd notification cancel-all >/dev/null 2>&1
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
assert_contains "restore: the overlay grant is allow" "allow" "$(adb shell appops get app.tileshell SYSTEM_ALERT_WINDOW | tr -d '\r' | head -1)"
assert_clock_empty "restore"
row_end
