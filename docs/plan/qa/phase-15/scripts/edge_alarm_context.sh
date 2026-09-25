#!/usr/bin/env bash
# EDGE_ALARM_CONTEXT — the phase doc's "Alarm firing while …" edge-case bullet: an alarm firing
#   1. while Tess is listening → NOT RUN (2026-09-25): the owner's no-microphone rule. Tess listening IS an open
#      microphone, and these AVDs run with -allow-host-audio (a guest capture opens the host's). Runs 1-3 (4b7ac321,
#      emulator-5558; EDGE_ALARM_CONTEXT-run1..3, the method in git da848a06) drove it; run 3's DEFECT.md holds what they
#      found. Recorded, not driven;
#   2. while Start is in edit mode (a tile held, `edit_disc:unpin` on screen) → edit mode ends (`[edit] exit first
#      frame` after the fired line, no edit disc in the dump);
#   3. while the shell's Music plays (phase 10's player, the MUSIC6 fixtures) → the player pauses on the transient focus
#      loss (its Media3 session PAUSED, no started USAGE_MEDIA player) and, per phase 10's E9, does not resume by itself
#      after the ring ends — RECORDED, not asserted;
#   4. while the recorder runs → NOT RUN (2026-09-25): the owner's no-microphone rule (a take IS a microphone capture).
#      Runs 2-3 drove it (all its clauses passed on 4b7ac321); recorded, not driven;
#   5. while glance (phase 07) shows → NOT RUN: phase 07 is not built (INDEX row 07 pending), the build has no glance
#      component — both recorded.
# The row ASSERTS the no-microphone rule itself (mic_guard.sh: dumpsys audio's recording-activity log gains no event).
# Every driven section: an alarm 2 min ahead through the AlarmClock API, the ring's slices saved as ring_<what>_<ring>.txt
# right after the fire, the toast dismissed, RV12's clock restore, the alarm deleted through the app and the store asserted
# empty. Restore: the Music fixtures / volume as found, the overlay grant allow.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"; . "$(dirname "$0")/rec.sh"; . "$(dirname "$0")/mic_guard.sh"
MUSIC_FIXDIR="$QROOT/phase-01/MUSIC6-fixtures"
. "$QROOT/phase-01/scripts/music_lib.sh"

row_begin EDGE_ALARM_CONTEXT "an alarm firing while Tess listens, Start is in edit mode, Music plays, the recorder runs; glance"
mic_guard_begin
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
# Not driven: the owner's no-microphone rule (2026-09-25). Nothing in this row opens Tess.
record "1. an alarm firing while Tess is listening" "NOT RUN: the owner's no-microphone rule (2026-09-25)"

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
# Not driven: the owner's no-microphone rule (2026-09-25). Nothing in this row opens the recorder.
record "4. an alarm firing while the recorder runs" "NOT RUN: the owner's no-microphone rule (2026-09-25)"

# ================================================================== 5. while glance shows =================================================
GL="$(adb shell dumpsys package app.tileshell | tr -d '\r' | grep -ci glance)"
record "glance (phase 07): NOT RUN" "phase 07 is not built — INDEX row 07: $(grep -E '^\| 07 ' "$REPO/docs/plan/INDEX.md" | awk -F'|' '{print $4}' | tr -s ' '); components of app.tileshell naming glance (dumpsys package): $GL"

# ================================================================== restore ================================================================
adb shell cmd notification cancel-all >/dev/null 2>&1
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
assert_contains "restore: the overlay grant is allow" "allow" "$(adb shell appops get app.tileshell SYSTEM_ALERT_WINDOW | tr -d '\r' | head -1)"
assert_clock_empty "restore"
mic_guard_end
row_end
