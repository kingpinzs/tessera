#!/usr/bin/env bash
# E14 — the recorder records real audio, both directions (R10 testability 21; T15-27, T15-30, T15-48), and lists other
# apps' recordings read-only (T15-3, T15-15). A 5-s 440 Hz tone at -12 dBFS is played into THIS emulator's microphone
# sink while a take runs; the take is judged from the file the AVD recorded (pulled, ffprobe / ffmpeg astats), from
# MediaStore's row and from the `:recorder` ring slice. A second take with nothing played must be silent. Then the
# fixture other.m4a (another app's IS_RECORDING file) lists with Share only, plays with no consent dialog, and song.m4a
# (not a recording) is not listed. Every microphone step first passes `audio.sh check` (T15-30; `audio.sh setup` is run
# by audio_route only when the check fails and AUDIO_SINK names this emulator's own sink).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
RINGS="launcher $REC_SVC"
row_begin E14 "the recorder records real audio both ways; other apps' recordings list read-only"

# ---------------------------------------------------------------- baseline
wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
audio_route "E14"
TONE5="$(tone 5)"
note "tone: $TONE5 rms=$("$AUDIO" rms "$TONE5") dBFS, $(file_duration "$TONE5") s"

# ---------------------------------------------------------------- 1. a take with the tone
rec_open record
dump_ui "$ROW_DIR/record.xml"
assert_eq "the record state is showing (rec_page:record)" yes "$(has_node "$ROW_DIR/record.xml" 'rec_page:record')"
BEFORE="$(own_ids | tr '\n' ' ')"
if [ "$AUDIO_OK" = yes ]; then
  MARK="$(ring_mark)"
  gtap "$ROW_DIR/record.xml" rec_button
  ( "$AUDIO" say "$TONE5" ) >/dev/null 2>&1 &
  TONE_PID=$!
  # The recording state, dumped while the take runs (the disc does not move); then the take's own clock says when to
  # stop — at 5 s, which the tone started with the take covers.
  sleep 1; rdump "$ROW_DIR/recording1.xml"; screencap "$ROW_DIR/recording1.png"
  E="$(wait_elapsed 4750 20)"; note "take 1: elapsed_ms=$E at the stop tap"
  gtap "$ROW_DIR/recording1.xml" rec_button
  wait "$TONE_PID" 2>/dev/null
  assert_eq "the take ran: the record state showed rec_elapsed" yes "$(has_node "$ROW_DIR/recording1.xml" rec_elapsed)"
  wait_phase idle 15 >/dev/null; sleep 1.5
  rec_ring "$MARK" > "$ROW_DIR/ring_take1.txt"
  TAKE1="$(new_own_id "$BEFORE")"
  note "take 1 MediaStore id: ${TAKE1:-none}"
  assert_ne "a new take of the shell's is in MediaStore" "" "$TAKE1"
  dump_ui "$ROW_DIR/list1.xml"; screencap "$ROW_DIR/list1.png"
  assert_eq "after stop the page shows the list (rec_page:list)" yes "$(has_node "$ROW_DIR/list1.xml" 'rec_page:list')"
  assert_eq "the new rec_row is listed" yes "$(has_node "$ROW_DIR/list1.xml" "rec_row:$TAKE1")"
  DUR_TXT="$(node_text "$ROW_DIR/list1.xml" "rec_duration:$TAKE1")"; note "rec_duration:$TAKE1 reads [$DUR_TXT]"
  assert_within "rec_duration shows 5 ± 0.5 s" 5 "$(clock_to_s "$DUR_TXT")" 0.5
  ROW1="$(row_by_id "$TAKE1")"; note "MediaStore row: $ROW1"
  assert_eq "MediaStore: relative_path Recordings/" "Recordings/" "$(ms_field "$ROW1" relative_path)"
  assert_eq "MediaStore: owner_package_name app.tileshell" "app.tileshell" "$(ms_field "$ROW1" owner_package_name)"
  assert_eq "MediaStore: is_recording 1" 1 "$(ms_field "$ROW1" is_recording)"
  assert_within "MediaStore: duration 5000 ± 500 ms" 5000 "$(ms_field "$ROW1" duration)" 500
  pull_take "$TAKE1" "$ROW_DIR/take1.m4a"
  note "take 1 pulled: $(stat -c%s "$ROW_DIR/take1.m4a" 2>/dev/null) bytes; stream $(file_stream "$ROW_DIR/take1.m4a")"
  assert_within "ffprobe: duration 5 ± 0.5 s" 5 "$(file_duration "$ROW_DIR/take1.m4a")" 0.5
  assert_eq "ffprobe: AAC, 44100 Hz, 1 channel (T15-27)" "aac,44100,1" "$(file_stream "$ROW_DIR/take1.m4a")"
  RMS1="$(file_rms "$ROW_DIR/take1.m4a")"; note "take 1 overall RMS: $RMS1 dBFS"
  assert_eq "astats: the tone was recorded (RMS > -40 dBFS)" yes "$(db_above "$RMS1" -40)"
  assert_contains "the :recorder ring holds [recorder] start" "[recorder] start Recording free=" "$(cat "$ROW_DIR/ring_take1.txt")"
  STOP_MS="$(grep -F '[recorder] stop Recording ms=' "$ROW_DIR/ring_take1.txt" | tail -1 | grep -oE 'ms=[0-9]+' | cut -d= -f2)"
  assert_within "the :recorder ring holds [recorder] stop … ms=5000 ± 500" 5000 "$STOP_MS" 500
else
  _verdict FAIL "take 1 with the tone" "NOT RUN: the audio route failed its check"
fi

# ---------------------------------------------------------------- 2. a take with nothing played
BEFORE="$(own_ids | tr '\n' ' ')"
MARK="$(ring_mark)"
dump_ui "$ROW_DIR/list_before2.xml"
gtap "$ROW_DIR/list_before2.xml" rec_button
sleep 1; rdump "$ROW_DIR/recording2.xml"
E="$(wait_elapsed 4750 20)"; note "take 2: elapsed_ms=$E at the stop tap"
gtap "$ROW_DIR/recording2.xml" rec_button
wait_phase idle 15 >/dev/null; sleep 1.5
rec_ring "$MARK" > "$ROW_DIR/ring_take2.txt"
TAKE2="$(new_own_id "$BEFORE")"; note "take 2 MediaStore id: ${TAKE2:-none}"
assert_ne "the silent take is in MediaStore" "" "$TAKE2"
pull_take "$TAKE2" "$ROW_DIR/take2.m4a"
RMS2="$(file_rms "$ROW_DIR/take2.m4a")"; note "take 2 overall RMS: $RMS2 dBFS ($(file_duration "$ROW_DIR/take2.m4a") s)"
assert_eq "nothing played: RMS < -60 dBFS" yes "$(db_below "$RMS2" -60)"

# ---------------------------------------------------------------- 3. other apps' recordings (T15-3)
# The MARK comes BEFORE the push: the list is showing, so its ContentObserver refreshes (and logs the count) on the scan.
LMARK="$(ring_mark)"
push_fixture_recordings
OTHER="$(id_by_name other.m4a)"; SONG="$(id_by_name song.m4a)"
note "fixtures: other.m4a id=${OTHER:-none} is_recording=$(ms_field "$(row_by_id "$OTHER")" is_recording); song.m4a id=${SONG:-none} is_recording=$(ms_field "$(row_by_id "$SONG")" is_recording)"
if [ "$(ms_field "$(row_by_id "$OTHER")" is_recording)" != 1 ]; then
  adb shell content update --uri "$MEDIA_URI" --bind is_recording:i:1 --where "\"_display_name='other.m4a'\"" >/dev/null 2>&1
  record "other.m4a needed content update is_recording:i:1 (the scan did not mark Recordings/ IS_RECORDING)" "now $(ms_field "$(row_by_id "$OTHER")" is_recording)"
else
  record "other.m4a marked IS_RECORDING by the scan itself" "is_recording=1"
fi
rec_open list
dump_ui "$ROW_DIR/list_others.xml"; screencap "$ROW_DIR/list_others.png"
assert_eq "other.m4a is listed (rec_row)" yes "$(has_node "$ROW_DIR/list_others.xml" "rec_row:$OTHER")"
assert_within "other.m4a rec_duration 3 ± 0.5 s" 3 "$(clock_to_s "$(node_text "$ROW_DIR/list_others.xml" "rec_duration:$OTHER")")" 0.5
assert_eq "song.m4a (is_recording 0) is NOT listed" no "$(has_node "$ROW_DIR/list_others.xml" "rec_row:$SONG")"
ring_since "$LMARK" > "$ROW_DIR/ring_list_others.txt"
N=$(( $(own_count) + 1 ))
assert_contains "the launcher ring counts the foreign file" "[recorder] list: $N recordings (1 by other apps)" "$(cat "$ROW_DIR/ring_list_others.txt")"
# the foreign row's hold menu: Share alone
hold_node "$ROW_DIR/list_others.xml" "rec_row:$OTHER"; sleep 1
dump_ui "$ROW_DIR/menu_other.xml"; screencap "$ROW_DIR/menu_other.png"
note "other.m4a hold menu: $(ids_with_prefix "$ROW_DIR/menu_other.xml" 'rec_menu' | paste -sd' ')"
assert_eq "other.m4a hold menu offers rec_menu:share" yes "$(has_node "$ROW_DIR/menu_other.xml" 'rec_menu:share')"
assert_eq "other.m4a hold menu has NO rec_menu:delete" no "$(has_node "$ROW_DIR/menu_other.xml" 'rec_menu:delete')"
assert_eq "other.m4a hold menu has NO rec_menu:rename" no "$(has_node "$ROW_DIR/menu_other.xml" 'rec_menu:rename')"
adb shell input keyevent KEYCODE_BACK; sleep 1
# the take's hold menu: all three
dump_ui "$ROW_DIR/list_others2.xml"
hold_node "$ROW_DIR/list_others2.xml" "rec_row:$TAKE1"; sleep 1
dump_ui "$ROW_DIR/menu_take.xml"
note "take 1 hold menu: $(ids_with_prefix "$ROW_DIR/menu_take.xml" 'rec_menu' | paste -sd' ')"
for m in share delete rename; do
  assert_eq "the take's hold menu offers rec_menu:$m" yes "$(has_node "$ROW_DIR/menu_take.xml" "rec_menu:$m")"
done
adb shell input keyevent KEYCODE_BACK; sleep 1
# a row tap on other.m4a plays it, with no consent dialog, and its app bar holds Share alone
dump_ui "$ROW_DIR/list_others3.xml"
PMARK="$(ring_mark)"
gtap "$ROW_DIR/list_others3.xml" "rec_row:$OTHER"
PLAYER="$(wait_media_player 3)"; note "dumpsys audio player after the tap: ${PLAYER:-none}"
assert_ne "a row tap on other.m4a starts a player of the shell's on USAGE_MEDIA (dumpsys audio)" "" "$PLAYER"
rdump "$ROW_DIR/play_other.xml"; screencap "$ROW_DIR/play_other.png"
adb shell dumpsys activity activities | tr -d '\r' > "$ROW_DIR/activities_other.txt"
assert_absent "no consent dialog: no providers.media activity" "providers.media" "$(grep -E 'ActivityRecord|topResumedActivity|mResumedActivity' "$ROW_DIR/activities_other.txt")"
note "other.m4a app bar: $(ids_with_prefix "$ROW_DIR/play_other.xml" 'rec_bar' | paste -sd' ')"
assert_eq "other.m4a's app bar holds rec_bar:share" yes "$(has_node "$ROW_DIR/play_other.xml" 'rec_bar:share')"
for m in trim delete rename; do
  assert_eq "other.m4a's app bar has NO rec_bar:$m" no "$(has_node "$ROW_DIR/play_other.xml" "rec_bar:$m")"
done
sleep 3
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$ROW_DIR/list_others4.xml"
gtap "$ROW_DIR/list_others4.xml" "rec_row:$TAKE1"; sleep 1
rdump "$ROW_DIR/play_take.xml"; screencap "$ROW_DIR/play_take.png"
note "take 1 app bar: $(ids_with_prefix "$ROW_DIR/play_take.xml" 'rec_bar' | paste -sd' ')"
for m in share trim delete rename; do
  assert_eq "the take's app bar holds rec_bar:$m" yes "$(has_node "$ROW_DIR/play_take.xml" "rec_bar:$m")"
done
adb shell dumpsys activity activities | tr -d '\r' > "$ROW_DIR/activities_take.txt"
assert_absent "still no consent dialog after the steps" "providers.media" "$(grep -E 'ActivityRecord|topResumedActivity|mResumedActivity' "$ROW_DIR/activities_take.txt")"
sleep 5
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---------------------------------------------------------------- restore
rec_ring_save
for id in $TAKE1 $TAKE2; do
  [ -n "$id" ] && app_delete_take "$id"
done
assert_eq "restore: both takes deleted in the app (MediaStore holds none of the shell's)" 0 "$(own_count)"
[ "$(own_count)" != 0 ] && note "restore: sweeping leftovers -> $(purge_own_takes) remain"
remove_fixture_recordings
assert_eq "restore: the fixtures are gone from MediaStore" "" "$(id_by_name other.m4a)$(id_by_name song.m4a)"
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
