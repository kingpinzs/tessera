#!/usr/bin/env bash
# E30 — pause, markers, search and filter (T15-16; H20, H21): a take with the tone throughout, paused by rec_pause at 3 s of
# take time (`[recorder] paused: user`; rec_elapsed equal in two dumps 3 s apart), resumed after 4 s, flagged 1 s and 2 s
# after the resume (rec_marker:1..2 on the page; `[recorder] marker <name> at=<ms>` at 4000 ± 300 and 5000 ± 300 — take
# time, the pause left out), stopped 3 s after the resume (ffprobe 6 ± 0.5 s, RMS > -40 dBFS). Its playback page lists
# both markers and puts rec_track_marker:1..2 at at/duration ± 2 % of the track; recordings.json holds them under the
# take's id; a marker added on playback leaves the file's bytes unchanged (sha256). Search ("Stand" → Standup's row alone)
# and the "Showing" filter (mine / others / all) over three takes and the pushed other.m4a. Every tap on the take's own
# clock (the service dump's elapsed_ms), never a sleep.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
RINGS="launcher $REC_SVC"
row_begin E30 "pause, markers, search and filter"

wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
audio_route "E30"
TONE_PID=""
# A track marker's time from its dot's centre against the scrubber (RecorderPlayback.Scrubber's geometry).
dot_ms() { # dump.xml dot-id duration_ms
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
def b(tag):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(tag), xml)
    return [int(v) for v in m.groups()] if m else None
s, d = b("rec_scrubber"), b(sys.argv[2])
if not s or not d: print(""); sys.exit()
r = (s[3] - s[1]) / 2.0
track_left, track_w = s[0] + r, (s[2] - s[0]) - 2 * r
print(int(round(((d[0] + d[2]) / 2.0 - track_left) / track_w * float(sys.argv[3]))))
PY
}
json_markers() { # id -> the markers recordings.json holds for id, comma-separated (or "absent")
  adb shell "run-as app.tileshell cat files/recordings.json" < /dev/null 2>/dev/null | tr -d '\r' | python3 -c '
import json, sys
try: d = json.load(sys.stdin)
except Exception as e: print("unreadable"); sys.exit()
r = d.get("recordings", {}).get(sys.argv[1])
print(",".join(str(m) for m in r["markers"]) if r else "absent")' "$1"
}
type_search() { # text
  dump_ui "$ROW_DIR/.search.xml"
  gtap "$ROW_DIR/.search.xml" rec_search; sleep 0.7
  adb shell input text "$1"; sleep 1.5
}
clear_search() { # chars
  local i
  adb shell input keyevent KEYCODE_MOVE_END
  for i in $(seq 1 "$1"); do adb shell input keyevent KEYCODE_DEL; done
  sleep 1.5
}
pick_filter() { # all|mine|others out.xml
  dump_ui "$ROW_DIR/.filter.xml"
  gtap "$ROW_DIR/.filter.xml" rec_filter; sleep 1
  dump_ui "$ROW_DIR/.filter_menu.xml"
  assert_eq "the Showing flyout offers rec_filter_choice:$1" yes "$(has_node "$ROW_DIR/.filter_menu.xml" "rec_filter_choice:$1")"
  gtap "$ROW_DIR/.filter_menu.xml" "rec_filter_choice:$1"; sleep 1.5
  dump_ui "$2"
}

if [ "$AUDIO_OK" = yes ]; then
  BEFORE="$(own_ids | tr '\n' ' ')"
  rec_open record; dump_ui "$ROW_DIR/record.xml"
  MARK="$(ring_mark)"
  gtap "$ROW_DIR/record.xml" rec_button
  TONE_PID="$(tone_loop_start "$(tone 10)")"
  sleep 1; rdump "$ROW_DIR/recording.xml"; screencap "$ROW_DIR/recording.png"
  assert_eq "the record state shows rec_pause and rec_flag" "yes yes" "$(has_node "$ROW_DIR/recording.xml" rec_pause) $(has_node "$ROW_DIR/recording.xml" rec_flag)"
  # ---- pause at 3 s of take time ----------------------------------------------------------------------------------------
  E="$(wait_elapsed 2750 15)"
  gtap "$ROW_DIR/recording.xml" rec_pause; sleep 0.4
  P_AT="$(rec_status elapsed_ms)"; note "pause tapped at elapsed_ms=$E; paused at $P_AT ms (phase $(rec_status phase), paused=$(rec_status paused))"
  assert_eq "the take is paused" paused "$(rec_status phase)"
  assert_within "paused at 3 s of take time (± 300 ms)" 3000 "$P_AT" 300
  rdump "$ROW_DIR/paused1.xml"; screencap "$ROW_DIR/paused1.png"
  T1="$(node_text "$ROW_DIR/paused1.xml" rec_elapsed)"
  sleep 3
  rdump "$ROW_DIR/paused2.xml"
  T2="$(node_text "$ROW_DIR/paused2.xml" rec_elapsed)"; note "rec_elapsed while paused: [$T1] then [$T2] 3 s later"
  assert_eq "rec_elapsed is equal in two dumps 3 s apart (T15-57)" "$T1" "$T2"
  assert_ne "rec_elapsed reads something" "" "$T1"
  rec_ring "$MARK" > "$ROW_DIR/ring_pause.txt"
  assert_contains "the :recorder ring holds [recorder] paused: user" "[recorder] paused: user" "$(cat "$ROW_DIR/ring_pause.txt")"
  assert_eq "the take clock did not move while paused" "$P_AT" "$(rec_status elapsed_ms)"
  # ---- resume after 4 s paused, then two flags at +1 s and +2 s -----------------------------------------------------------
  gtap "$ROW_DIR/paused2.xml" rec_pause; sleep 0.3
  assert_eq "the take is recording again" recording "$(rec_status phase)"
  E="$(wait_elapsed $(( P_AT + 750 )) 10)"; gtap "$ROW_DIR/recording.xml" rec_flag; note "flag 1 tapped at elapsed_ms=$E"
  E="$(wait_elapsed $(( P_AT + 1750 )) 10)"; gtap "$ROW_DIR/recording.xml" rec_flag; note "flag 2 tapped at elapsed_ms=$E"
  sleep 0.5
  rdump "$ROW_DIR/flagged.xml"; screencap "$ROW_DIR/flagged.png"
  assert_eq "rec_marker:1 is on the record page" yes "$(has_node "$ROW_DIR/flagged.xml" 'rec_marker:1')"
  assert_eq "rec_marker:2 is on the record page" yes "$(has_node "$ROW_DIR/flagged.xml" 'rec_marker:2')"
  note "record-page markers: [$(node_text "$ROW_DIR/flagged.xml" 'rec_marker:1')] [$(node_text "$ROW_DIR/flagged.xml" 'rec_marker:2')]"
  rec_ring "$MARK" > "$ROW_DIR/ring_markers.txt"
  M1="$(grep -F '[recorder] marker Recording at=' "$ROW_DIR/ring_markers.txt" | sed -n 1p | grep -oE 'at=[0-9]+' | cut -d= -f2)"
  M2="$(grep -F '[recorder] marker Recording at=' "$ROW_DIR/ring_markers.txt" | sed -n 2p | grep -oE 'at=[0-9]+' | cut -d= -f2)"
  note "marker lines: at=$M1, at=$M2"
  assert_within "marker 1 at 4000 ± 300 ms of take time (the pause excluded)" 4000 "$M1" 300
  assert_within "marker 2 at 5000 ± 300 ms" 5000 "$M2" 300
  # ---- stop 3 s after the resume ------------------------------------------------------------------------------------------
  E="$(wait_elapsed $(( P_AT + 2750 )) 10)"; gtap "$ROW_DIR/recording.xml" rec_button; note "stop tapped at elapsed_ms=$E"
  tone_loop_stop "$TONE_PID"; TONE_PID=""
  wait_phase idle 15 >/dev/null; sleep 1.5
  rec_ring "$MARK" > "$ROW_DIR/ring_take.txt"
  TAKE="$(new_own_id "$BEFORE")"; note "the take: ${TAKE:-none}"
  assert_ne "the take is in MediaStore" "" "$TAKE"
  pull_take "$TAKE" "$ROW_DIR/take.m4a"
  D="$(file_duration "$ROW_DIR/take.m4a")"; R="$(file_rms "$ROW_DIR/take.m4a")"; note "file: $D s, RMS $R dBFS"
  assert_within "ffprobe: 6 ± 0.5 s (the 4 s paused are not in it)" 6 "$D" 0.5
  assert_eq "RMS > -40 dBFS" yes "$(db_above "$R" -40)"
  SHA1="$(sha256sum "$ROW_DIR/take.m4a" | cut -c1-64)"
  DUR_MS="$(ms_field "$(row_by_id "$TAKE")" duration)"
  assert_eq "recordings.json holds both markers under the take's id" "$M1,$M2" "$(json_markers "$TAKE")"
  # ---- the playback page: both markers, the dots on the track -------------------------------------------------------------
  dump_ui "$ROW_DIR/list.xml"
  gtap "$ROW_DIR/list.xml" "rec_row:$TAKE"; sleep 1.5
  rdump "$ROW_DIR/playback.xml"; screencap "$ROW_DIR/playback.png"
  assert_eq "the playback page lists rec_marker:1 and :2" "yes yes" "$(has_node "$ROW_DIR/playback.xml" 'rec_marker:1') $(has_node "$ROW_DIR/playback.xml" 'rec_marker:2')"
  note "playback markers: [$(node_text "$ROW_DIR/playback.xml" 'rec_marker:1')] [$(node_text "$ROW_DIR/playback.xml" 'rec_marker:2')]; rec_total [$(node_text "$ROW_DIR/playback.xml" rec_total)]"
  D1="$(dot_ms "$ROW_DIR/playback.xml" 'rec_track_marker:1' "$DUR_MS")"; D2="$(dot_ms "$ROW_DIR/playback.xml" 'rec_track_marker:2' "$DUR_MS")"
  note "track dots read as $D1 ms and $D2 ms of $DUR_MS"
  TOL=$(( DUR_MS * 2 / 100 ))
  assert_within "rec_track_marker:1 sits at at/duration ± 2 % of the track" "$M1" "$D1" "$TOL"
  assert_within "rec_track_marker:2 sits at at/duration ± 2 % of the track" "$M2" "$D2" "$TOL"
  # a marker added on playback: recordings.json gains it, the file's bytes do not change
  sleep 1
  gtap "$ROW_DIR/playback.xml" rec_flag; sleep 1.5
  pull_take "$TAKE" "$ROW_DIR/take_after_flag.m4a"
  SHA2="$(sha256sum "$ROW_DIR/take_after_flag.m4a" | cut -c1-64)"; note "sha256 before $SHA1 / after $SHA2"
  assert_eq "a marker added on playback leaves the file's bytes unchanged (sha256 before = after)" "$SHA1" "$SHA2"
  JM="$(json_markers "$TAKE")"; note "recordings.json markers now: $JM"
  assert_eq "recordings.json now holds three markers" 3 "$(python3 -c 'import sys; print(len([m for m in sys.argv[1].split(",") if m]))' "$JM")"
  rec_ring_save
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  # ---- search: the take renamed Standup, two more takes --------------------------------------------------------------------
  dump_ui "$ROW_DIR/list2.xml"
  hold_node "$ROW_DIR/list2.xml" "rec_row:$TAKE"; sleep 1
  dump_ui "$ROW_DIR/menu.xml"; gtap "$ROW_DIR/menu.xml" "rec_menu:rename"; sleep 1.5
  dump_ui "$ROW_DIR/rename.xml"
  assert_eq "the rename dialog is up" yes "$(has_node "$ROW_DIR/rename.xml" rec_rename_dialog)"
  adb shell input text Standup; sleep 0.5; gtap "$ROW_DIR/rename.xml" rec_rename_ok; sleep 2
  assert_eq "the take is now Standup.m4a" Standup.m4a "$(ms_field "$(row_by_id "$TAKE")" _display_name)"
  T2="$(make_take 3)"; T3="$(make_take 3)"
  assert_ne "two more takes are in MediaStore" "" "$T2$T3"
  note "takes: Standup $TAKE, $T2, $T3"
  type_search Stand
  dump_ui "$ROW_DIR/search.xml"; screencap "$ROW_DIR/search.png"
  note "rows with 'Stand': $(ids_with_prefix "$ROW_DIR/search.xml" 'rec_row:' | paste -sd' ')"
  assert_eq "search Stand: Standup's row is listed" yes "$(has_node "$ROW_DIR/search.xml" "rec_row:$TAKE")"
  assert_eq "search Stand: only that row" 1 "$(ids_with_prefix "$ROW_DIR/search.xml" 'rec_row:' | wc -l | tr -d ' ')"
  clear_search 5
  dump_ui "$ROW_DIR/search_cleared.xml"
  assert_eq "search cleared: all three takes listed" 3 "$(ids_with_prefix "$ROW_DIR/search_cleared.xml" 'rec_row:' | wc -l | tr -d ' ')"
  assert_eq "the search box is empty again" "" "$(node_text "$ROW_DIR/search_cleared.xml" rec_search)"
  # ---- filter over the takes and other.m4a ---------------------------------------------------------------------------------
  push_fixture_recordings; sleep 1.5
  OTHER="$(id_by_name other.m4a)"
  dump_ui "$ROW_DIR/all0.xml"
  assert_eq "other.m4a joined the list (the ContentObserver)" yes "$(has_node "$ROW_DIR/all0.xml" "rec_row:$OTHER")"
  pick_filter mine "$ROW_DIR/mine.xml"; screencap "$ROW_DIR/mine.png"
  assert_eq "mine: other.m4a absent" no "$(has_node "$ROW_DIR/mine.xml" "rec_row:$OTHER")"
  assert_eq "mine: the three takes present" "yes yes yes" "$(has_node "$ROW_DIR/mine.xml" "rec_row:$TAKE") $(has_node "$ROW_DIR/mine.xml" "rec_row:$T2") $(has_node "$ROW_DIR/mine.xml" "rec_row:$T3")"
  assert_eq "the Showing link reads the kind" "My recordings" "$(node_text "$ROW_DIR/mine.xml" rec_filter)"
  pick_filter others "$ROW_DIR/others.xml"
  assert_eq "others: only other.m4a" "rec_row:$OTHER" "$(ids_with_prefix "$ROW_DIR/others.xml" 'rec_row:' | paste -sd' ')"
  pick_filter all "$ROW_DIR/all.xml"
  assert_eq "all: everything (four rows)" 4 "$(ids_with_prefix "$ROW_DIR/all.xml" 'rec_row:' | wc -l | tr -d ' ')"
  assert_eq "all: the Showing link reads All recordings" "All recordings" "$(node_text "$ROW_DIR/all.xml" rec_filter)"
  # ---- restore ------------------------------------------------------------------------------------------------------------
  for id in $TAKE $T2 $T3; do [ -n "$id" ] && app_delete_take "$id"; done
  assert_eq "restore: the takes are deleted" 0 "$(own_count)"
  [ "$(own_count)" != 0 ] && note "sweeping leftovers -> $(purge_own_takes) remain"
else
  _verdict FAIL "the paused, flagged take" "NOT RUN: the audio route failed its check"
fi
[ -n "$TONE_PID" ] && tone_loop_stop "$TONE_PID"
remove_fixture_recordings
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
