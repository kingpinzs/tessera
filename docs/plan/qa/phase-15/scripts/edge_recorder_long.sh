#!/usr/bin/env bash
# EDGE_RECORDER_LONG — two of the phase doc's Recorder Edge cases on ONE take, each clause asserted on its own:
#   9. "the screen off for 30 minutes (foreground service keeps the take; the file grows)": the take starts, the device is
#      unplugged (`dumpsys battery unplug`, so Android's battery-time rules — Doze included — apply as on a phone in a
#      pocket) and put to sleep (KEYCODE_SLEEP); every 5 minutes for 30 minutes the row reads the power state, the take's
#      status and the size of the take's kill-safe stream (the `:recorder` dump's `file=… bytes=`), then wakes the device
#      (wake_device, C-25). Asserted: asleep at every sample, the take recording at every sample in the same process, the
#      service still foreground with its notification after 30 minutes, the stream strictly growing, the take's clock
#      advancing with the wall clock (within 1 %), and the final file holding the tone inside the screen-off span.
#   10. "a 60-minute take (file ≈ 30 MB, listed with its duration, plays and seeks)": the same take stopped at 60:00 on
#      its own clock; MediaStore's size ≈ 30 MB (the doc's figure: 64 kbps AAC is 0.48 MB a minute, T15-27) and duration
#      60:00; the list's rec_duration reads 1:00:00; a row tap plays it (a started player, the `recorder` session
#      PLAYING); taps on the scrubber seek to 30:00 and 54:00 (rec_position) and it keeps playing from there; the pulled
#      file is 3600 ± 2 s with the tone at its start, middle and end.
# The tone loops into this emulator's microphone (the 60-s tone, restarted each minute) the whole hour.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
RINGS="launcher $REC_SVC"
row_begin EDGE_RECORDER_LONG "Voice Recorder: 30 minutes with the screen off, and a 60-minute take that lists, plays and seeks"

TONE_PID=""
# The tone looped with paplay itself as the loop's child (tone_loop_start's loop runs `audio.sh say`, and stopping it
# leaves that paplay playing out its file — up to a minute here): TERM kills the paplay by its pid, then the loop.
tone_loop_direct() { # wav -> pid
  ( trap 'kill $pp 2>/dev/null; exit 0' TERM INT
    while :; do paplay -d "$AUDIO_SINK" "$1" & pp=$!; wait $pp || break; done ) >/dev/null 2>&1 &
  echo $!
}
cleanup() { [ -n "$TONE_PID" ] && tone_loop_stop "$TONE_PID"; TONE_PID=""; }
trap cleanup EXIT
wakefulness() { adb shell dumpsys power | tr -d '\r' | grep -m1 'mWakefulness=' | sed 's/.*mWakefulness=//'; }
stream_bytes() { rec_status file | grep -oE 'bytes=[0-9]+' | cut -d= -f2; }
# The x (device px) on the scrubber for a time (e16.sh's geometry: the thumb's centre runs trackLeft .. trackLeft + trackW).
track_x() { # dump.xml ms duration_ms
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
m = re.search(r'resource-id="rec_scrubber"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', xml)
if not m: print(""); sys.exit()
s = [int(v) for v in m.groups()]
r = (s[3] - s[1]) / 2.0
track_left, track_w = s[0] + r, (s[2] - s[0]) - 2 * r
print(int(round(track_left + track_w * float(sys.argv[2]) / float(sys.argv[3]))), (s[1] + s[3]) // 2)
PY
}

# ---------------------------------------------------------------- baseline
wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
assert_eq "baseline: the device is awake" Awake "$(wakefulness)"
note "baseline battery: $(adb shell dumpsys battery | tr -d '\r' | grep -E 'AC powered|status|level' | sed 's/^ *//' | paste -sd' ')"
FREE_MB="$(( $(adb shell df -k /sdcard | awk 'NR==2 {print $4}' | tr -d '\r') / 1024 ))"; note "free on /sdcard: $FREE_MB MB"
assert_eq "baseline: room for a 30-MB take and the storage floor (> 700 MB free)" yes "$([ "$FREE_MB" -gt 700 ] && echo yes || echo no)"
audio_route "EDGE_RECORDER_LONG"

if [ "$AUDIO_OK" = yes ]; then
  BEFORE="$(own_ids | tr '\n' ' ')"
  rec_open record; dump_ui "$ROW_DIR/record.xml"
  assert_eq "the record state shows (rec_page:record)" yes "$(has_node "$ROW_DIR/record.xml" 'rec_page:record')"
  MARK="$(ring_mark)"
  gtap "$ROW_DIR/record.xml" rec_button
  TONE_PID="$(tone_loop_direct "$(tone 60)")"
  wait_elapsed 3000 20 >/dev/null
  assert_eq "the take is recording" recording "$(rec_status phase)"
  RPID="$(adb shell pidof app.tileshell:recorder | tr -d '\r')"; note "recorder pid $RPID"
  rec_ring "$MARK" > "$ROW_DIR/ring_long_start_recorder.txt"
  assert_contains "the :recorder ring holds [recorder] start Recording" "[recorder] start Recording free=" "$(cat "$ROW_DIR/ring_long_start_recorder.txt")"

  # ============================================================ 9. the screen off for 30 minutes
  log "── 9. the screen off for 30 minutes"
  adb shell dumpsys battery unplug
  SMARK="$(ring_mark)"
  adb shell input keyevent KEYCODE_SLEEP; sleep 3
  W0="$(wakefulness)"; E0="$(rec_status elapsed_ms)"; B0="$(stream_bytes)"; T0="$(date +%s.%N)"
  note "screen off: wakefulness=$W0 elapsed_ms=$E0 stream bytes=$B0"
  assert_eq "9: the screen went off (wakefulness is not Awake)" yes "$(case "$W0" in Asleep|Dozing) echo yes ;; *) echo "no ($W0)" ;; esac)"
  printf 'minute\twall_s\twakefulness\tphase\telapsed_ms\tstream_bytes\trecorder_pid\tdeviceidle\n' > "$ROW_DIR/screen_off_growth.tsv"
  printf '0\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$T0" "$W0" "$(rec_status phase)" "$E0" "$B0" "$RPID" "$(adb shell dumpsys deviceidle | tr -d '\r' | grep -E '^  mState=|^  mLightState=' | sed 's/^ *//' | paste -sd' ')" >> "$ROW_DIR/screen_off_growth.tsv"
  ASLEEP_ALL=yes; RECORDING_ALL=yes; PID_SAME=yes; GROWING=yes; PREV_B="$B0"
  for i in 1 2 3 4 5 6; do
    sleep 300
    W="$(wakefulness)"; PH="$(rec_status phase)"; E="$(rec_status elapsed_ms)"; B="$(stream_bytes)"; RP="$(adb shell pidof app.tileshell:recorder | tr -d '\r')"
    DI="$(adb shell dumpsys deviceidle | tr -d '\r' | grep -E '^  mState=|^  mLightState=' | sed 's/^ *//' | paste -sd' ')"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$((i * 5))" "$(date +%s.%N)" "$W" "$PH" "$E" "$B" "$RP" "$DI" >> "$ROW_DIR/screen_off_growth.tsv"
    case "$W" in Asleep|Dozing) ;; *) ASLEEP_ALL="no (minute $((i * 5)): $W)" ;; esac
    [ "$PH" = recording ] || RECORDING_ALL="no (minute $((i * 5)): $PH)"
    [ "$RP" = "$RPID" ] || PID_SAME="no (minute $((i * 5)): $RP)"
    if [ -z "$B" ] || [ -z "$PREV_B" ] || [ "$B" -le "$PREV_B" ] 2>/dev/null; then GROWING="no (minute $((i * 5)): $PREV_B -> $B)"; fi
    PREV_B="$B"
  done
  T30="$(date +%s.%N)"; E30="$E"; B30="$B"
  note "screen-off samples:"; while IFS= read -r l; do note "  $l"; done < "$ROW_DIR/screen_off_growth.tsv"
  assert_eq "9: the screen stayed off at every 5-minute sample" yes "$ASLEEP_ALL"
  assert_eq "9: the take was recording at every sample" yes "$RECORDING_ALL"
  assert_eq "9: in the same :recorder process throughout" yes "$PID_SAME"
  assert_eq "9: the file grew at every sample (the take's stream, strictly increasing)" yes "$GROWING"
  WALL_MS="$(python3 -c "import sys; print(int((float('$T30') - float('$T0')) * 1000))")"
  note "30 minutes: wall $WALL_MS ms; take clock $E0 -> $E30 ms; stream $B0 -> $B30 bytes"
  assert_within "9: the take's clock kept pace with the wall clock (elapsed delta, ± 1 %)" "$WALL_MS" "$(( E30 - E0 ))" "$(( WALL_MS / 100 ))"
  assert_within "9: the stream grew by 64 kbps × the span (8000 B/s, ± 10 %)" "$(( WALL_MS * 8 ))" "$(( B30 - B0 ))" "$(( WALL_MS * 8 / 10 ))"
  SB="$(service_block .recorder.RecorderService)"; printf '%s\n' "$SB" > "$ROW_DIR/service_after_30min.txt"
  assert_contains "9: after 30 minutes the recorder service is still foreground" "isForeground=true" "$SB"
  assert_contains "9: and its notification still says Recording" "android.title=String (Recording)" "$(shell_notification_block 1507)"
  rec_ring "$SMARK" > "$ROW_DIR/ring_screen_off_recorder.txt"
  assert_absent "9: no stop while the screen was off (:recorder ring)" "[recorder] stop" "$(cat "$ROW_DIR/ring_screen_off_recorder.txt")"
  assert_absent "9: no pause while the screen was off (:recorder ring)" "[recorder] paused:" "$(cat "$ROW_DIR/ring_screen_off_recorder.txt")"
  record "9: Doze while the screen was off (dumpsys deviceidle at 30 minutes)" "$DI"
  assert_eq "9: wake_device prints Awake after the screen-off span (C-25)" Awake "$(wake_device)"

  # ============================================================ 10. on to 60 minutes of take time
  log "── 10. a 60-minute take"
  while :; do
    E="$(rec_status elapsed_ms)"
    [ -n "$E" ] && [ "$E" -ge $(( 3600000 - 45000 )) ] 2>/dev/null && break
    [ "$(rec_status phase)" = recording ] || { note "the take left recording at elapsed $E: phase $(rec_status phase)"; break; }
    sleep 20
  done
  rec_open record; rdump "$ROW_DIR/record_59.xml"; screencap "$ROW_DIR/record_59.png"
  note "record state at $(rec_status elapsed_ms) ms: rec_elapsed [$(node_text "$ROW_DIR/record_59.xml" rec_elapsed)]"
  E60="$(wait_elapsed 3599750 90)"; note "stop tapped at elapsed_ms=$E60"
  SMK="$(ring_mark)"
  # Only a running take's disc is tapped: on an idle page the same disc would START a take.
  case "$(rec_status phase)" in recording|paused) gtap "$ROW_DIR/record_59.xml" rec_button ;; *) note "the take is not running at the stop ($(rec_status phase)); no tap" ;; esac
  wait_phase idle 90 >/dev/null; sleep 2
  tone_loop_stop "$TONE_PID"; TONE_PID=""
  rec_ring "$SMK" > "$ROW_DIR/ring_long_stop_recorder.txt"
  rec_ring "$MARK" > "$ROW_DIR/ring_long_take_recorder.txt"
  STOPL="$(grep -F '[recorder] stop Recording' "$ROW_DIR/ring_long_stop_recorder.txt" | tail -1)"; note "stop line: ${STOPL#*] }"
  assert_contains "10: the :recorder ring holds the take's stop line" "[recorder] stop Recording ms=" "$STOPL"
  assert_within "10: stopped at 60:00 of take time (stop ms, ± 1.5 s)" 3600000 "$(grep -oE 'ms=[0-9]+' <<< "$STOPL" | cut -d= -f2)" 1500
  assert_absent "10: the hour had no pause (:recorder ring)" "[recorder] paused:" "$(cat "$ROW_DIR/ring_long_take_recorder.txt")"
  ID="$(new_own_id "$BEFORE")"; note "the 60-minute take: id ${ID:-none}"
  assert_ne "10: the take is in MediaStore" "" "$ID"
  SZ="$(adb shell content query --uri "$MEDIA_URI" --projection _size:duration --where "\"_id=$ID\"" | tr -d '\r' | grep -oE '_size=[0-9]+' | cut -d= -f2)"
  DUR="$(ms_field "$(row_by_id "$ID")" duration)"
  record "10: the file's size (64 kbps × 3600 s = 28,800,000 bytes of audio, T15-27)" "$SZ bytes"
  assert_within "10: the file is about 30 MB (MediaStore _size, 30 MB ± 10 %)" 30000000 "${SZ:-0}" 3000000
  assert_within "10: MediaStore's duration is 60:00 (± 2 s)" 3600000 "${DUR:-0}" 2000
  # listed with its duration
  rec_open list; dump_ui "$ROW_DIR/list.xml"; screencap "$ROW_DIR/list.png"
  LD="$(node_text "$ROW_DIR/list.xml" "rec_duration:$ID")"; note "rec_duration:$ID reads [$LD]"
  assert_eq "10: listed (rec_row)" yes "$(has_node "$ROW_DIR/list.xml" "rec_row:$ID")"
  assert_eq "10: listed with its duration (rec_duration reads 1:00:00)" "1:00:00" "$LD"
  # plays and seeks
  PM="$(ring_mark)"
  gtap "$ROW_DIR/list.xml" "rec_row:$ID"
  PLAYER="$(wait_media_player 4)"; note "player: ${PLAYER:-none}"
  assert_ne "10: a row tap plays it (a started player of the shell's on USAGE_MEDIA)" "" "$PLAYER"
  sleep 1
  assert_eq "10: the recorder session is PLAYING" PLAYING "$(recorder_session_state)"
  rdump "$ROW_DIR/play0.xml"; screencap "$ROW_DIR/play0.png"
  assert_eq "10: the playback page's total reads 1:00:00 (rec_total)" "1:00:00" "$(node_text "$ROW_DIR/play0.xml" rec_total)"
  for TARGET in 1800000 3240000; do
    read -r X Y <<< "$(track_x "$ROW_DIR/play0.xml" "$TARGET" 3600000)"
    adb shell input tap "$X" "$Y"; sleep 1.2
    rdump "$ROW_DIR/seek_$TARGET.xml"; screencap "$ROW_DIR/seek_$TARGET.png"
    P1="$(node_text "$ROW_DIR/seek_$TARGET.xml" rec_position)"
    sleep 2
    rdump "$ROW_DIR/seek_${TARGET}_b.xml"
    P2="$(node_text "$ROW_DIR/seek_${TARGET}_b.xml" rec_position)"
    note "tap at x=$X for $((TARGET / 1000)) s: rec_position [$P1] then [$P2] 2 s later"
    assert_within "10: a tap on the scrubber seeks to $((TARGET / 60000)):00 (rec_position, ± 15 s)" "$((TARGET / 1000))" "$(clock_to_s "$P1")" 15
    assert_eq "10: and it plays on from there (rec_position advances)" yes "$(python3 -c 'import sys; a,b=sys.argv[1:3]; print("yes" if a and b and int(b) > int(a) else "no (%s -> %s)" % (a,b))' "$(clock_to_s "$P1")" "$(clock_to_s "$P2")")"
  done
  ring_since "$PM" > "$ROW_DIR/ring_long_playback_launcher.txt"
  assert_contains "10: the launcher ring loaded the take into the recorder session" "[recorder] playback loaded $ID (session id recorder)" "$(cat "$ROW_DIR/ring_long_playback_launcher.txt")"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  # the pulled file
  pull_take "$ID" "$ROW_DIR/long_take.m4a"
  FD="$(file_duration "$ROW_DIR/long_take.m4a")"; note "pulled: $(stat -c%s "$ROW_DIR/long_take.m4a") bytes, $FD s, $(file_stream "$ROW_DIR/long_take.m4a")"
  assert_within "10: the pulled file is 3600 ± 2 s (ffprobe)" 3600 "$FD" 2
  for W in 60 900 1790 3590; do
    R="$(file_rms "$ROW_DIR/long_take.m4a" "$W" "$((W + 5))")"; note "RMS [$W, $((W + 5))) s: $R dBFS"
    assert_eq "10: the tone is in the file at $W s (RMS > -40 dBFS$([ "$W" = 900 ] && echo ', inside the screen-off span'))" yes "$(db_above "$R" -40)"
  done
  rm -f "$ROW_DIR/long_take.m4a"   # 29 MB; its analysis is in this log
  note "long_take.m4a deleted from the evidence dir after analysis (29 MB)"
  # ---- restore
  rec_ring_save
  app_delete_take "$ID"
  assert_eq "restore: the take deleted through the app" 0 "$(own_count)"
  [ "$(own_count)" != 0 ] && note "restore: sweeping leftovers -> $(purge_own_takes) remain"
else
  _verdict FAIL "the 30-minute screen-off and the 60-minute take" "NOT RUN: the audio route failed its check"
fi
cleanup
assert_eq "restore: awake and on mains (wake_device)" Awake "$(wake_device)"
# A take still running here (a failure above left it) is stopped through the page and swept, never left recording.
rec_open record
if [ "$(rec_status phase)" = recording ] || [ "$(rec_status phase)" = paused ]; then
  rdump "$ROW_DIR/restore_record.xml"; gtap "$ROW_DIR/restore_record.xml" rec_button; wait_phase idle 90 >/dev/null
  note "restore: a take was still running and was stopped; sweeping -> $(purge_own_takes) remain"
fi
assert_eq "restore: no take running" idle "$(rec_status phase)"
assert_eq "restore: MediaStore holds no take of the shell's" 0 "$(own_count)"
assert_contains "restore: battery reset to the AVD's own (AC powered)" "AC powered: true" "$(adb shell dumpsys battery | tr -d '\r')"
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
