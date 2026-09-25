#!/usr/bin/env bash
# E15 — the recorder in the background (T15-39, T15-50, C-25): a take started with the tone looping into this emulator's
# microphone, Home pressed → the recorder service is a foreground service of type microphone (`types=0x00000080`;
# dumpsys prints no foregroundServiceType text) in the process app.tileshell:recorder, with an ongoing notification
# carrying Stop; the screen off for 20 s and woken (wake_device must print Awake); the take stopped FROM THE NOTIFICATION
# → the file is ≥ 25 s long with the tone all through it (overall RMS > -40 dBFS, and a 5-s window at 22 s measured
# separately). The `:recorder` ring slice is saved before Home; a service stopped from the notification with no page
# bound is gone from dumpsys, so its `stop` line cannot be read afterwards and the file is the evidence.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
RINGS="launcher $REC_SVC"
row_begin E15 "the recorder keeps recording in the background and stops from its notification"

wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
audio_route "E15"
TONE_PID=""
if [ "$AUDIO_OK" = yes ]; then
  TONE="$(tone 10)"
  BEFORE="$(own_ids | tr '\n' ' ')"
  rec_open record
  dump_ui "$ROW_DIR/record.xml"
  MARK="$(ring_mark)"
  T0="$(date +%s)"
  gtap "$ROW_DIR/record.xml" rec_button
  TONE_PID="$(tone_loop_start "$TONE")"; note "tone loop pid $TONE_PID"
  E="$(wait_elapsed 1500 15)"; note "the take runs: elapsed_ms=$E"
  assert_eq "the take is recording" recording "$(rec_status phase)"
  rec_ring "$MARK" > "$ROW_DIR/ring_start.txt"
  assert_contains "the :recorder ring holds [recorder] start" "[recorder] start Recording free=" "$(cat "$ROW_DIR/ring_start.txt")"
  rec_ring_save
  # ---- Home: the take goes on as a foreground service -------------------------------------------------------------
  adb shell input keyevent KEYCODE_HOME; sleep 2
  assert_contains "Start is in front" "app.tileshell/.StartActivity" "$(resumed)"
  service_block .recorder.RecorderService > "$ROW_DIR/service.txt"
  note "service block: $(grep -E 'isForeground|processName' "$ROW_DIR/service.txt" | tr -s ' ' | cut -c1-160 | paste -sd'|')"
  assert_contains "the recorder service is foreground" "isForeground=true" "$(cat "$ROW_DIR/service.txt")"
  assert_contains "its types carry the microphone bit 0x00000080" "types=0x00000080" "$(cat "$ROW_DIR/service.txt")"
  assert_contains "its process is app.tileshell:recorder" "processName=app.tileshell:recorder" "$(cat "$ROW_DIR/service.txt")"
  RPID="$(adb shell pidof app.tileshell:recorder | tr -d '\r')"; note "pidof app.tileshell:recorder = $RPID"
  assert_ne "pidof app.tileshell:recorder finds the process" "" "$RPID"
  shell_notification_block 1507 > "$ROW_DIR/notification.txt"
  assert_contains "the ongoing notification is posted (ONGOING_EVENT)" "ONGOING_EVENT" "$(cat "$ROW_DIR/notification.txt")"
  assert_contains "it carries a Stop action" '"Stop" -> PendingIntent' "$(cat "$ROW_DIR/notification.txt")"
  assert_contains "its title is Recording" "android.title=String (Recording)" "$(cat "$ROW_DIR/notification.txt")"
  # ---- the screen off for 20 s, then woken (C-25) -------------------------------------------------------------------
  adb shell input keyevent KEYCODE_SLEEP; sleep 1
  note "after KEYCODE_SLEEP: $(adb shell dumpsys power | grep -m1 'mWakefulness=' | tr -d '\r ')"
  sleep 19
  assert_eq "the take is still recording after 20 s asleep" recording "$(rec_status phase)"
  assert_eq "wake_device prints Awake (C-25)" Awake "$(wake_device)"
  sleep 1
  # ---- stop from the notification ----------------------------------------------------------------------------------
  E="$(rec_status elapsed_ms)"; note "elapsed_ms before the shade: $E"
  TAPPED="$(shade_tap_action Stop "$ROW_DIR/shade.xml")"
  screencap "$ROW_DIR/after_shade.png"
  assert_eq "the notification's Stop was found and tapped in the shade" yes "$TAPPED"
  sleep 3
  if [ "$(rec_status phase)" = recording ]; then
    # RV12: the row restores what it changed even when the step failed — the take is stopped through the app (the
    # verdict above already stands).
    note "the take is still recording after the shade step; stopping it through the app for the restore"
    rec_open record; tap_rec_button "$ROW_DIR/stop_fallback.xml"; wait_phase idle 15 >/dev/null; sleep 1.5
    adb shell input keyevent KEYCODE_HOME; sleep 1
  fi
  tone_loop_stop "$TONE_PID"; TONE_PID=""
  assert_eq "after Stop the notification is gone" "" "$(shell_notification_block 1507)"
  assert_eq "after Stop no recorder service is running" "" "$(service_block .recorder.RecorderService | grep -F isForeground)"
  TAKE="$(new_own_id "$BEFORE")"; note "the take's MediaStore id: ${TAKE:-none}; wall time since the record tap: $(( $(date +%s) - T0 )) s"
  assert_ne "the take is in MediaStore" "" "$TAKE"
  pull_take "$TAKE" "$ROW_DIR/take.m4a"
  DUR="$(file_duration "$ROW_DIR/take.m4a")"; note "take: $DUR s, $(file_stream "$ROW_DIR/take.m4a")"
  assert_eq "the file is at least 25 s long" yes "$(python3 -c 'import sys; print("yes" if float(sys.argv[1]) >= 25 else "no (%s)" % sys.argv[1])' "${DUR:-0}")"
  RMS="$(file_rms "$ROW_DIR/take.m4a")"; note "overall RMS: $RMS dBFS"
  assert_eq "the tone is in the whole file (overall RMS > -40 dBFS)" yes "$(db_above "$RMS" -40)"
  W="$(file_rms "$ROW_DIR/take.m4a" 22 27)"; note "RMS over 22-27 s: $W dBFS"
  assert_eq "the 5-s window at 22 s (recorded while the screen was off) has the tone (RMS > -40 dBFS)" yes "$(db_above "$W" -40)"
  # ---- back to the page: the take saved while it was stopped must be on its list ------------------------------------
  rec_open list
  dump_ui "$ROW_DIR/list_after.xml"; screencap "$ROW_DIR/list_after.png"
  note "the reopened page: $(ids_with_prefix "$ROW_DIR/list_after.xml" 'rec_' | paste -sd' ' | cut -c1-200)"
  assert_eq "reopened after the notification stop, the list shows the take's row" yes "$(has_node "$ROW_DIR/list_after.xml" "rec_row:$TAKE")"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  # ---- restore -----------------------------------------------------------------------------------------------------
  [ -n "$TAKE" ] && app_delete_take "$TAKE"
  if [ "$(own_count)" != 0 ]; then
    note "restore: the app did not list the take, so it is removed with adb (rm + scan) -> $(purge_own_takes) remain"
  fi
  assert_eq "restore: the take is gone from MediaStore" 0 "$(own_count)"
else
  _verdict FAIL "the background take" "NOT RUN: the audio route failed its check"
fi
[ -n "$TONE_PID" ] && tone_loop_stop "$TONE_PID"
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
