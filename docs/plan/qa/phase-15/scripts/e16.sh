#!/usr/bin/env bash
# E16 — playback, trim, rename, delete, share of the shell's OWN takes (T15-26, T15-34, T15-57; r11/voice-recorder.md §4,
# U3, U4, U5, 1.9). Two takes are made first with the tone: A (12 s, for the playback checks — a 5-s take ends before two
# gesture-driver dumps 2 s apart can read it) and B (5 s, the trim: keep 1.0–3.0 s → a 2.0-s file, the original gone only
# after Save). A row tap plays: a started player on USAGE_MEDIA (dumpsys audio), the shell's Media3 session with id
# `recorder` (dumpsys media_session), rec_position advancing between two dumps 2 s apart and the thumb moving between two
# screencaps (a slider's position is in no node attribute). Rename through the app bar (A → Standup) and through the hold
# menu (B → Retro); delete through the app bar; share opens Android's chooser for ACTION_SEND audio/mp4 and the content
# URI reads back as an MP4 (`ftyp`).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
RINGS="launcher $REC_SVC"
PIX="$HERE/pixcmp.py"
row_begin E16 "playback, trim, rename, delete and share of the shell's own takes"

wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
audio_route "E16"

# Type into the focused rename field (its text is pre-selected, so the typed name replaces it) and confirm.
rename_in_dialog() { # dump.xml name
  assert_eq "the rename dialog is up" yes "$(has_node "$1" rec_rename_dialog)"
  adb shell input text "$2"; sleep 0.5
  gtap "$1" rec_rename_ok; sleep 2
}
# The time a trim handle stands at, from its node centre against the scrubber's track (see RecorderPlayback.Scrubber):
# the node is trackLeft - r .. trackLeft + trackW + r; the thumb centre runs from trackLeft to trackLeft + trackW.
handle_ms() { # dump.xml handle-id duration_ms
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
def b(tag):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(tag), xml)
    return [int(v) for v in m.groups()] if m else None
s, h = b("rec_scrubber"), b(sys.argv[2])
if not s or not h: print(""); sys.exit()
r = (h[2] - h[0]) / 2.0
track_left, track_w = s[0] + r, (s[2] - s[0]) - 2 * r
cx = (h[0] + h[2]) / 2.0
print(int(round((cx - track_left) / track_w * float(sys.argv[3]))))
PY
}
# The x (device px) on the scrubber for a time, by the same geometry.
track_x() { # dump.xml ms duration_ms
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
m = re.search(r'resource-id="rec_scrubber"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', xml)
s = [int(v) for v in m.groups()]
r = (s[3] - s[1]) / 2.0      # the node is THUMB tall: r = half its height
track_left, track_w = s[0] + r, (s[2] - s[0]) - 2 * r
print(int(round(track_left + track_w * float(sys.argv[2]) / float(sys.argv[3]))), (s[1] + s[3]) // 2)
PY
}

if [ "$AUDIO_OK" = yes ]; then
  rec_open record
  A="$(make_take 12)"; assert_ne "take A (12 s) is in MediaStore" "" "$A"
  B="$(make_take 5)"; assert_ne "take B (5 s) is in MediaStore" "" "$B"
  A_NAME="$(ms_field "$(row_by_id "$A")" _display_name)"; B_NAME="$(ms_field "$(row_by_id "$B")" _display_name)"
  note "A=$A ($A_NAME) B=$B ($B_NAME)"

  # ---- playback (A) -------------------------------------------------------------------------------------------------
  rec_open list; dump_ui "$ROW_DIR/list.xml"
  MARK="$(ring_mark)"
  gtap "$ROW_DIR/list.xml" "rec_row:$A"
  PLAYER="$(wait_media_player 3)"; note "dumpsys audio: ${PLAYER:-none}"
  assert_ne "a row tap starts a player of the shell's on USAGE_MEDIA" "" "$PLAYER"
  adb shell dumpsys media_session | tr -d '\r' > "$ROW_DIR/media_session.txt"
  assert_contains "dumpsys media_session shows the shell's session tagged with id recorder (T15-34)" "app.tileshell/androidx.media3.session.id.recorder" "$(cat "$ROW_DIR/media_session.txt")"
  assert_eq "and that session is PLAYING" PLAYING "$(recorder_session_state)"
  rdump "$ROW_DIR/play1.xml"; screencap "$ROW_DIR/play1.png"
  P1="$(node_text "$ROW_DIR/play1.xml" rec_position)"
  sleep 2
  rdump "$ROW_DIR/play2.xml"; screencap "$ROW_DIR/play2.png"
  P2="$(node_text "$ROW_DIR/play2.xml" rec_position)"
  note "rec_position: [$P1] then [$P2] 2 s later; rec_total [$(node_text "$ROW_DIR/play2.xml" rec_total)]"
  assert_eq "rec_position advances between two dumps 2 s apart" yes "$(python3 -c 'import sys; a,b=sys.argv[1:3]; print("yes" if a and b and int(b) > int(a) else "no (%s -> %s)" % (a, b))' "$(clock_to_s "$P1")" "$(clock_to_s "$P2")")"
  SB="$(bounds "$ROW_DIR/play1.xml" rec_scrubber)"; note "rec_scrubber bounds: $SB"
  # shellcheck disable=SC2086
  set -- $SB
  FRAC="$(python3 "$PIX" region "$ROW_DIR/play1.png" "$ROW_DIR/play2.png" "$2" "$4" 8 "0,$2,$1,$4" "$3,$2,1080,$4")"
  note "scrubber band: fraction of pixels equal between the two screencaps (labels masked): $FRAC"
  assert_eq "the thumb moved between the two screencaps (the scrubber band differs)" yes "$(python3 -c 'import sys; print("yes" if float(sys.argv[1]) < 0.995 else "no (%s)" % sys.argv[1])' "$FRAC")"
  ring_since "$MARK" > "$ROW_DIR/ring_playback.txt"
  assert_contains "the launcher ring loaded A into the recorder session" "[recorder] playback loaded $A (session id recorder)" "$(cat "$ROW_DIR/ring_playback.txt")"
  # ---- rename A through the app bar ---------------------------------------------------------------------------------
  rdump "$ROW_DIR/play3.xml"
  gtap "$ROW_DIR/play3.xml" "rec_bar:rename"; sleep 1.5
  dump_ui "$ROW_DIR/rename_bar.xml"; screencap "$ROW_DIR/rename_bar.png"
  rename_in_dialog "$ROW_DIR/rename_bar.xml" Standup
  assert_eq "rec_bar:rename → _display_name Standup.m4a in MediaStore" "Standup.m4a" "$(ms_field "$(row_by_id "$A")" _display_name)"
  # ---- share A ------------------------------------------------------------------------------------------------------
  rdump "$ROW_DIR/play4.xml"
  gtap "$ROW_DIR/play4.xml" "rec_bar:share"; sleep 3
  adb shell dumpsys activity activities | tr -d '\r' > "$ROW_DIR/activities_share.txt"
  screencap "$ROW_DIR/share.png"
  TOP="$(grep -E 'topResumedActivity|mResumedActivity' "$ROW_DIR/activities_share.txt" | head -1)"; note "top after share: $TOP"
  # Doc-vs-build: the app shares through Intent.createChooser, so the dump's intent is act=android.intent.action.CHOOSER
  # with the recording's content URI as its clip; the wrapped ACTION_SEND audio/mp4 is an extra and prints as
  # "(has extras)" on this image (no dumpsys prints it). The action and type are read from the producer's own line.
  CH="$(grep -E 'Intent \{ act=android.intent.action.CHOOSER' "$ROW_DIR/activities_share.txt" | head -1)"; note "chooser intent: ${CH:-none}"
  assert_contains "Android's chooser (com.android.intentresolver) is in front" "com.android.intentresolver" "$TOP"
  assert_contains "its intent is ACTION_CHOOSER carrying a content URI clip" "clip={text/uri-list" "$CH"
  assert_contains "the clip is a content URI" "{U(content)}" "$CH"
  ring_since "$MARK" > "$ROW_DIR/ring_share.txt"
  assert_contains "the launcher ring: share sent ACTION_SEND audio/mp4 with the take's content URI" "[recorder] share $A type=audio/mp4 uri=content://media/external/audio/media/$A (own)" "$(cat "$ROW_DIR/ring_share.txt")"
  adb shell content read --uri "$MEDIA_URI/$A" 2>/dev/null | head -c 12 > "$ROW_DIR/share_head.bin"
  note "content read first bytes: $(xxd -p "$ROW_DIR/share_head.bin" | head -1)"
  assert_eq "the shared content URI reads back as an MP4 (ftyp box at offset 4)" ftyp "$(dd if="$ROW_DIR/share_head.bin" bs=1 skip=4 count=4 2>/dev/null)"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  assert_contains "Back closes the chooser and returns to Voice Recorder" "recorder.RecorderActivity" "$(resumed)"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5

  # ---- trim B: keep 1.0–3.0 s ---------------------------------------------------------------------------------------
  dump_ui "$ROW_DIR/list2.xml"
  gtap "$ROW_DIR/list2.xml" "rec_row:$B"; sleep 1.5
  rdump "$ROW_DIR/playB.xml"
  gtap "$ROW_DIR/playB.xml" "rec_bar:trim"; sleep 1.5
  rdump "$ROW_DIR/trim0.xml"; screencap "$ROW_DIR/trim0.png"
  assert_eq "the trim page is up (rec_trim)" yes "$(has_node "$ROW_DIR/trim0.xml" rec_trim)"
  B_DUR="$(ms_field "$(row_by_id "$B")" duration)"; note "B's MediaStore duration: $B_DUR ms"
  # The end handle first (it is the nearer one to 3.0 s on a 5-s take), then the start handle.
  read -r X Y <<< "$(track_x "$ROW_DIR/trim0.xml" 3000 "$B_DUR")"; adb shell input tap "$X" "$Y"; sleep 0.7
  read -r X Y <<< "$(track_x "$ROW_DIR/trim0.xml" 1000 "$B_DUR")"; adb shell input tap "$X" "$Y"; sleep 0.7
  rdump "$ROW_DIR/trim1.xml"; screencap "$ROW_DIR/trim1.png"
  IN_MS="$(handle_ms "$ROW_DIR/trim1.xml" rec_trim_start "$B_DUR")"; OUT_MS="$(handle_ms "$ROW_DIR/trim1.xml" rec_trim_end "$B_DUR")"
  note "handles: start $IN_MS ms ([$(node_text "$ROW_DIR/trim1.xml" rec_trim_in)]), end $OUT_MS ms ([$(node_text "$ROW_DIR/trim1.xml" rec_trim_out)])"
  assert_within "the in handle stands at 1.0 s (± 0.1)" 1000 "$IN_MS" 100
  assert_within "the out handle stands at 3.0 s (± 0.1)" 3000 "$OUT_MS" 100
  N_BEFORE="$(own_count)"
  assert_eq "before Save MediaStore shows only the original (no new file yet)" 2 "$N_BEFORE"
  assert_eq "before Save no working file is in MediaStore" "" "$(ms_rows "_display_name LIKE '%trimming%'")"
  assert_ne "before Save the original is still there" "" "$(row_by_id "$B")"
  TMARK="$(ring_mark)"
  gtap "$ROW_DIR/trim1.xml" rec_trim_save; sleep 4
  ring_since "$TMARK" > "$ROW_DIR/ring_trim.txt"
  note "trim line: $(grep -F '[recorder] trim' "$ROW_DIR/ring_trim.txt" | tail -1 | cut -c40-)"
  B2="$(new_own_id "$A $B")"; note "the trimmed file's id: ${B2:-none} ($(ms_field "$(row_by_id "$B2")" _display_name))"
  assert_ne "after Save a new file is in MediaStore" "" "$B2"
  assert_eq "after Save the untrimmed file is gone from MediaStore" "" "$(row_by_id "$B")"
  assert_eq "MediaStore holds the same number of the shell's takes as before the trim" "$N_BEFORE" "$(own_count)"
  pull_take "$B2" "$ROW_DIR/trimmed.m4a"
  TD="$(file_duration "$ROW_DIR/trimmed.m4a")"; TR="$(file_rms "$ROW_DIR/trimmed.m4a")"; note "trimmed: $TD s, RMS $TR dBFS, $(file_stream "$ROW_DIR/trimmed.m4a")"
  assert_within "the saved file's ffprobe duration is 2.0 ± 0.1 s" 2.0 "$TD" 0.1
  assert_eq "and its RMS is still > -40 dBFS" yes "$(db_above "$TR" -40)"
  assert_eq "the trimmed file kept the original's name" "$B_NAME" "$(ms_field "$(row_by_id "$B2")" _display_name)"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5

  # ---- rename B through the hold menu -------------------------------------------------------------------------------
  dump_ui "$ROW_DIR/list3.xml"
  hold_node "$ROW_DIR/list3.xml" "rec_row:$B2"; sleep 1
  dump_ui "$ROW_DIR/menu_B.xml"
  gtap "$ROW_DIR/menu_B.xml" "rec_menu:rename"; sleep 1.5
  dump_ui "$ROW_DIR/rename_menu.xml"
  rename_in_dialog "$ROW_DIR/rename_menu.xml" Retro
  assert_eq "rec_menu:rename → _display_name Retro.m4a in MediaStore" "Retro.m4a" "$(ms_field "$(row_by_id "$B2")" _display_name)"

  # ---- delete B through the app bar ---------------------------------------------------------------------------------
  dump_ui "$ROW_DIR/list4.xml"
  assert_contains "the list shows Retro.m4a's row" "rec_row:$B2" "$(cat "$ROW_DIR/list4.xml")"
  N_BEFORE="$(own_count)"
  gtap "$ROW_DIR/list4.xml" "rec_row:$B2"; sleep 1.5
  rdump "$ROW_DIR/playB2.xml"
  gtap "$ROW_DIR/playB2.xml" "rec_bar:delete"; sleep 1.5
  dump_ui "$ROW_DIR/delete_dialog.xml"; screencap "$ROW_DIR/delete_dialog.png"
  assert_eq "the delete confirmation is up (U5)" yes "$(has_node "$ROW_DIR/delete_dialog.xml" rec_delete_dialog)"
  gtap "$ROW_DIR/delete_dialog.xml" rec_delete_confirm; sleep 2.5
  dump_ui "$ROW_DIR/list5.xml"
  assert_eq "rec_bar:delete → the row is gone" no "$(has_node "$ROW_DIR/list5.xml" "rec_row:$B2")"
  assert_eq "MediaStore count − 1" "$(( N_BEFORE - 1 ))" "$(own_count)"
  assert_absent "ls /sdcard/Recordings no longer lists Retro.m4a" "Retro.m4a" "$(adb shell ls /sdcard/Recordings/ | tr -d '\r')"

  # ---- restore ------------------------------------------------------------------------------------------------------
  rec_ring_save
  app_delete_take "$A"
  assert_eq "restore: every take deleted" 0 "$(own_count)"
  [ "$(own_count)" != 0 ] && note "restore: sweeping leftovers -> $(purge_own_takes) remain"
else
  _verdict FAIL "the takes for playback, trim, rename, delete and share" "NOT RUN: the audio route failed its check"
fi
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
