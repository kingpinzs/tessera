#!/usr/bin/env bash
# E18 — recorder process death (T15-39, T15-4, T15-17, T15-56): a take with the tone, Home (the page unbound, so nothing
# re-creates the service), `kill -9` of app.tileshell:recorder at 8 s of take time → no restarted recorder service
# (START_NOT_STICKY); the `:speech` ring frees the hold within 2 s (`a client died`, `microphone released: owner died`);
# Start's process is unchanged; reopening Voice Recorder binds a new recorder process whose onCreate recovers the take —
# a `rec_row:` of 8 ± 1 s, `[recorder] recovered <name> ms=<n>` in the new ring, the pulled file 8 ± 1 s with the tone —
# and Tess's listen is then accepted (no busy card, no refusal in the `:speech` ring).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
RINGS="launcher speech $REC_SVC"
row_begin E18 "recorder process death: the take is recovered, the microphone freed, nothing restarted"

wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
audio_route "E18"
TONE_PID=""
wall_of() { grep -oE 'wall=[0-9]+' <<< "$1" | head -1 | cut -d= -f2; }

if [ "$AUDIO_OK" = yes ]; then
  BEFORE="$(own_ids | tr '\n' ' ')"
  LPID0="$(adb shell pidof app.tileshell | tr -d '\r')"
  rec_open record; dump_ui "$ROW_DIR/record.xml"
  MARK="$(ring_mark)"
  gtap "$ROW_DIR/record.xml" rec_button
  TONE_PID="$(tone_loop_start "$(tone 10)")"
  wait_elapsed 1500 15 >/dev/null
  assert_eq "the take is recording" recording "$(rec_status phase)"
  RPID="$(adb shell pidof app.tileshell:recorder | tr -d '\r')"; note "recorder pid $RPID, launcher pid $LPID0"
  adb shell input keyevent KEYCODE_HOME; sleep 1.5
  assert_contains "Start is in front (the page unbound)" "app.tileshell/.StartActivity" "$(resumed)"
  # ---- the kill at 8 s of take time ------------------------------------------------------------------------------------
  E="$(wait_elapsed 7900 20)"; note "take elapsed_ms at the kill: $E"
  rec_ring "$MARK" > "$ROW_DIR/ring-recorder-prekill.txt"
  ring_since "$ROW_MARK" > "$ROW_DIR/ring-launcher-prekill.txt"
  KMARK="$(ring_mark)"
  adb root >/dev/null 2>&1; adb wait-for-device
  adb shell kill -9 "$RPID"
  adb unroot >/dev/null 2>&1; adb wait-for-device
  sleep 2.5
  tone_loop_stop "$TONE_PID"; TONE_PID=""
  service_block .recorder.RecorderService > "$ROW_DIR/services_after_kill.txt"
  assert_eq "no restarted recorder service (START_NOT_STICKY)" "" "$(cat "$ROW_DIR/services_after_kill.txt")"
  assert_eq "no app.tileshell:recorder process" "" "$(adb shell pidof app.tileshell:recorder | tr -d '\r')"
  assert_eq "the ongoing notification went with the process" "" "$(shell_notification_block 1507)"
  # ---- the microphone is freed (T15-4) --------------------------------------------------------------------------------
  ring_since "$KMARK" speech > "$ROW_DIR/ring_kill_speech.txt"
  DIED="$(grep -F '[speech] a client died' "$ROW_DIR/ring_kill_speech.txt" | head -1)"
  FREED="$(grep -F '[speech] microphone released: owner died' "$ROW_DIR/ring_kill_speech.txt" | head -1)"
  note "speech: ${DIED:-no 'a client died'} / ${FREED:-no 'microphone released: owner died'}"
  assert_ne "the :speech ring holds [speech] a client died" "" "$DIED"
  assert_ne "then [speech] microphone released: owner died" "" "$FREED"
  [ -n "$DIED" ] && assert_eq "a client died within 2000 ms of the kill (wall - MARK)" yes "$(python3 -c 'import sys; d=int(sys.argv[1])-int(sys.argv[2]); print("yes" if 0 <= d <= 2000 else "no (%d ms)" % d)' "$(wall_of "$DIED")" "$KMARK")"
  [ -n "$FREED" ] && assert_eq "microphone released within 2000 ms of the kill (wall - MARK)" yes "$(python3 -c 'import sys; d=int(sys.argv[1])-int(sys.argv[2]); print("yes" if 0 <= d <= 2000 else "no (%d ms)" % d)' "$(wall_of "$FREED")" "$KMARK")"
  assert_eq "the arbiter shows no owner" yes "$(python3 -c 'import sys; v=sys.argv[1].strip(); print("yes" if v in ("", "0", "-1", "none") else "no (%s)" % v)' "$(speech_status mic_owner_pid)")"
  assert_eq "Start and the live tiles never restarted (pidof app.tileshell unchanged)" "$LPID0" "$(adb shell pidof app.tileshell | tr -d '\r')"
  # ---- reopen: the new process recovers the take -----------------------------------------------------------------------
  OMARK="$(ring_mark)"
  rec_open list; sleep 2.5
  dump_ui "$ROW_DIR/list.xml"; screencap "$ROW_DIR/list.png"
  rec_ring "$OMARK" > "$ROW_DIR/ring_recover.txt"
  RPID2="$(adb shell pidof app.tileshell:recorder | tr -d '\r')"; note "new recorder pid ${RPID2:-none}"
  assert_ne "a new recorder process is bound by the page" "" "$RPID2"
  assert_ne "and it is not the killed one" "$RPID" "$RPID2"
  TAKE="$(new_own_id "$BEFORE")"; note "the recovered take: ${TAKE:-none}"
  assert_ne "the recovered take is in MediaStore" "" "$TAKE"
  assert_eq "the reopened list shows the recovered rec_row" yes "$(has_node "$ROW_DIR/list.xml" "rec_row:$TAKE")"
  assert_within "its rec_duration reads 8 ± 1 s" 8 "$(clock_to_s "$(node_text "$ROW_DIR/list.xml" "rec_duration:$TAKE")")" 1
  REC="$(grep -F '[recorder] recovered ' "$ROW_DIR/ring_recover.txt" | head -1)"; note "recovery line: ${REC#*] }"
  assert_contains "the new process's ring holds [recorder] recovered <name> ms=<n>" "[recorder] recovered Recording ms=" "$REC"
  assert_within "recovered ms is 8000 ± 1000" 8000 "$(grep -oE 'ms=[0-9]+' <<< "$REC" | head -1 | cut -d= -f2)" 1000
  pull_take "$TAKE" "$ROW_DIR/recovered.m4a"
  D="$(file_duration "$ROW_DIR/recovered.m4a")"; R="$(file_rms "$ROW_DIR/recovered.m4a")"; note "recovered file: $D s, RMS $R dBFS, $(file_stream "$ROW_DIR/recovered.m4a")"
  assert_within "the pulled file plays: ffprobe duration 8 ± 1 s" 8 "$D" 1
  assert_eq "and holds the tone (RMS > -40 dBFS)" yes "$(db_above "$R" -40)"
  rec_ring_save
  adb shell input keyevent KEYCODE_HOME; sleep 1
  # ---- Tess's listen is accepted -------------------------------------------------------------------------------------
  cortana_assist; sleep 3.5
  TMARK="$(ring_mark)"
  cortana_listen 3 || note "cortana_listen: no mic button found"
  dump_ui "$ROW_DIR/tess.xml"; screencap "$ROW_DIR/tess.png"
  ring_since "$TMARK" speech > "$ROW_DIR/ring_tess_speech.txt"
  note "Tess: mic_owner_pid=$(speech_status mic_owner_pid) asr_listening=$(speech_status asr_listening); card body [$(node_text "$ROW_DIR/tess.xml" cortana_card_body)]"
  assert_absent "no busy notice on Tess's card" "using the microphone" "$(node_text "$ROW_DIR/tess.xml" cortana_card_body)"
  assert_absent "no MICROPHONE_BUSY refusal in the :speech ring" "refused: held by" "$(cat "$ROW_DIR/ring_tess_speech.txt")"
  assert_eq "Tess's listen was accepted: the launcher process holds the microphone" "$LPID0" "$(speech_status mic_owner_pid)"
  cortana_close; sleep 4
  # ---- restore -----------------------------------------------------------------------------------------------------
  [ -n "$TAKE" ] && app_delete_take "$TAKE"
  assert_eq "restore: the recovered take is deleted" 0 "$(own_count)"
  [ "$(own_count)" != 0 ] && note "sweeping leftovers -> $(purge_own_takes) remain"
  note "leftover take files in the recorder's files dir: [$(adb shell "run-as app.tileshell ls files/recorder" < /dev/null 2>&1 | tr -d '\r' | paste -sd' ')]"
else
  _verdict FAIL "the take, the kill and the recovery" "NOT RUN: the audio route failed its check"
fi
[ -n "$TONE_PID" ] && tone_loop_stop "$TONE_PID"
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
