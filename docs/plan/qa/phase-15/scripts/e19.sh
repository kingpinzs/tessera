#!/usr/bin/env bash
# E19 — the storage floor (T15-27, C-27): the shared volume filled until LOW + 54 MB are free as df reports them (LOW =
# devicestoragemonitor's lowBytes, which getAllocatableBytes already excludes, so the recorder's 50-MB floor is about 4 MB
# of writing away); a take with the tone stops by itself — within 4 MB ÷ 8 KB/s + 30 s ≈ 9 min 30 s of its
# `[recorder] start … free=<MB>` line — with the notice "Not enough space" on the page (a gesture-driver dump inside the
# notice's 8 s), `[recorder] storage floor` in the `:recorder` ring, and the partial take listed and playing. The page
# stays in front for the whole take (the notice is the page's). Restore: unfill_volume, the take deleted.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
RINGS="launcher $REC_SVC"
row_begin E19 "the storage floor stops a take by itself with Not enough space"

wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
audio_route "E19"
df_free() { adb shell df -k /sdcard | awk 'NR==2 {print $4 * 1024}' | tr -d '\r'; }
LOW="$(adb shell dumpsys devicestoragemonitor | tr -d '\r' | sed -n 's/.*lowBytes=\([0-9]*\).*/\1/p' | head -1)"
record "devicestoragemonitor lowBytes (the reserve getAllocatableBytes excludes)" "${LOW:-none}"
FREE0="$(df_free)"; note "free on /sdcard before: $FREE0 bytes"
assert_ne "lowBytes was read" "" "$LOW"
TONE_PID=""
FILLED=no
if [ "$AUDIO_OK" = yes ] && [ -n "$LOW" ]; then
  fill_volume $(( LOW + 54 * 1024 * 1024 )); FRC=$?
  FREE1="$(df_free)"
  assert_eq "fill_volume left LOW + 54 MB (<= LOW + 59 MB) free on /sdcard (its own precondition, rc)" 0 "$FRC"
  record "free on /sdcard after the fill (bytes)" "$FREE1"
  FILLED=yes
  BEFORE="$(own_ids | tr '\n' ' ')"
  rec_open record; dump_ui "$ROW_DIR/record.xml"
  MARK="$(ring_mark)"
  T0="$(date +%s)"
  gtap "$ROW_DIR/record.xml" rec_button
  TONE_PID="$(tone_loop_start "$(tone 10)")"
  wait_elapsed 1500 15 >/dev/null
  START="$(rec_ring "$MARK" | grep -F '[recorder] start Recording free=' | head -1)"
  FREE_MB="$(grep -oE 'free=[0-9]+' <<< "$START" | cut -d= -f2)"
  note "start line: ${START#*] }"
  assert_ne "the take started ([recorder] start … free=<MB>)" "" "$START"
  record "getAllocatableBytes at the start, MB (the floor is 50)" "${FREE_MB:-none}"
  assert_eq "the take is recording" recording "$(rec_status phase)"
  # The bound the doc gives (≈ 9 min 30 s), widened by what the start line says is really free above the floor.
  LIMIT=$(( ( ${FREE_MB:-54} - 50 ) * 1024 * 1024 / 8192 + 60 ))
  [ "$LIMIT" -lt 600 ] && LIMIT=600
  note "waiting up to $LIMIT s for the floor"
  PHASE=recording; LAST_E=0
  while [ $(( $(date +%s) - T0 )) -lt "$LIMIT" ]; do
    PHASE="$(rec_status phase)"
    E="$(rec_status elapsed_ms)"; [ -n "$E" ] && LAST_E="$E"
    case "$PHASE" in recording|paused|saving) ;; *) break ;; esac
    sleep 2
  done
  STOPPED_AT=$(( $(date +%s) - T0 ))
  note "the take's phase after ${STOPPED_AT}s of wall time: [${PHASE:-none}] (last elapsed_ms $LAST_E)"
  rdump "$ROW_DIR/floor.xml"; screencap "$ROW_DIR/floor.png"
  tone_loop_stop "$TONE_PID"; TONE_PID=""
  assert_eq "the take stopped by itself within the bound" yes "$([ "$STOPPED_AT" -lt "$LIMIT" ] && echo yes || echo "no ($STOPPED_AT s, phase $PHASE)")"
  assert_eq "the notice Not enough space shows (rec_notice)" "Not enough space" "$(node_text "$ROW_DIR/floor.xml" rec_notice)"
  sleep 2
  rec_ring "$MARK" > "$ROW_DIR/ring_floor.txt"
  assert_contains "the :recorder ring holds [recorder] storage floor" "[recorder] storage floor" "$(cat "$ROW_DIR/ring_floor.txt")"
  assert_contains "and the take's stop line" "[recorder] stop Recording ms=" "$(cat "$ROW_DIR/ring_floor.txt")"
  note "stop line: $(grep -F '[recorder] stop ' "$ROW_DIR/ring_floor.txt" | tail -1 | cut -c40-)"
  TAKE="$(new_own_id "$BEFORE")"; note "the partial take: ${TAKE:-none} ($(ms_field "$(row_by_id "$TAKE")" duration) ms, $(ms_field "$(row_by_id "$TAKE")" _size) bytes)"
  assert_ne "the partial take is in MediaStore" "" "$TAKE"
  dump_ui "$ROW_DIR/list.xml"
  assert_eq "the partial take is listed" yes "$(has_node "$ROW_DIR/list.xml" "rec_row:$TAKE")"
  gtap "$ROW_DIR/list.xml" "rec_row:$TAKE"
  PLAYER="$(wait_media_player 3)"; note "player: ${PLAYER:-none}"
  assert_ne "and it plays (a started player on USAGE_MEDIA)" "" "$PLAYER"
  sleep 1.5; adb shell input keyevent KEYCODE_BACK; sleep 1
  rec_ring_save
  # ---- restore ----------------------------------------------------------------------------------------------------
  unfill_volume
  FILLED=no
  FREE2="$(df_free)"; note "free on /sdcard after unfill: $FREE2 bytes (before the row: $FREE0)"
  assert_eq "restore: the fill file is gone (free space back above LOW + 1 GB)" yes "$([ "$FREE2" -gt $(( LOW + 1024 * 1024 * 1024 )) ] && echo yes || echo "no ($FREE2)")"
  [ -n "$TAKE" ] && app_delete_take "$TAKE"
  assert_eq "restore: the partial take is deleted" 0 "$(own_count)"
  [ "$(own_count)" != 0 ] && note "sweeping leftovers -> $(purge_own_takes) remain"
else
  _verdict FAIL "the take against the storage floor" "NOT RUN: $([ "$AUDIO_OK" = yes ] || echo 'the audio route failed its check;') $([ -n "$LOW" ] || echo 'lowBytes unreadable')"
fi
[ -n "$TONE_PID" ] && tone_loop_stop "$TONE_PID"
[ "$FILLED" = yes ] && unfill_volume
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
