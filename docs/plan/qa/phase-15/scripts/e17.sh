#!/usr/bin/env bash
# E17 — microphone arbitration (phase 03/05's MicArbiter, third owner, named — T15-4, T15-28): with a take running and the
# tone playing, Tess asked to listen is refused and her card names the voice recorder; the `:speech` ring records
# `startListening from pid=<launcher> refused: held by recorder`; mic_owner_pid is the recorder process; the take kept the
# microphone (its RMS over that window > -40 dBFS); the keyboard's voice key is refused with the same sentence (phase 05
# EDGE3's form, kb.sh). Conversely, with the Voice Recorder page in front and the fixture's recogniser holding the
# microphone for the launcher process (edge3.sh's RECOGNIZE broadcast), a record tap is refused: the notice "Tess is
# listening", no file, `[recorder] refused: microphone busy held by cortana` in the `:recorder` ring.
# KEYCODE_ASSIST opens Tess on her home page (CortanaSession.onShow: no mode → HOME), so the listen is her text box's
# microphone button, tapped after the assist key (edge3.sh (c)'s route).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
. "$QROOT/phase-05/scripts/kb.sh"
RINGS="launcher speech $REC_SVC"
row_begin E17 "microphone arbitration: the recorder refuses Tess and the keyboard, and is refused by Tess"

BUSY="The voice recorder is using the microphone right now."
wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
audio_route "E17"
LPID="$(adb shell pidof app.tileshell | tr -d '\r')"; note "launcher pid $LPID"
kb_begin
TONE_PID=""

if [ "$AUDIO_OK" = yes ]; then
  # ---- a take with the tone ------------------------------------------------------------------------------------------
  BEFORE="$(own_ids | tr '\n' ' ')"
  rec_open record; dump_ui "$ROW_DIR/record.xml"
  MARK="$(ring_mark)"
  gtap "$ROW_DIR/record.xml" rec_button
  TONE_PID="$(tone_loop_start "$(tone 10)")"
  wait_elapsed 1500 15 >/dev/null
  assert_eq "the take is recording" recording "$(rec_status phase)"
  RPID="$(adb shell pidof app.tileshell:recorder | tr -d '\r')"; note "recorder pid $RPID"
  rec_ring_save
  adb shell input keyevent KEYCODE_HOME; sleep 1.5

  # ---- Tess asks for the microphone ----------------------------------------------------------------------------------
  cortana_assist; sleep 3.5
  E_A="$(rec_status elapsed_ms)"
  SMARK="$(ring_mark)"
  cortana_listen 3 || note "cortana_listen: no mic button found"
  dump_ui "$ROW_DIR/tess_card.xml"; screencap "$ROW_DIR/tess_card.png"
  E_B="$(rec_status elapsed_ms)"
  ring_since "$SMARK" speech > "$ROW_DIR/ring_tess_speech.txt"
  ring_since "$SMARK" > "$ROW_DIR/ring_tess_launcher.txt"
  # The NOT_UNDERSTOOD card puts the sentence in its title (CortanaModel: Card(NOT_UNDERSTOOD, shown)); read both texts.
  CARD="$(node_text "$ROW_DIR/tess_card.xml" cortana_card_title) $(node_text "$ROW_DIR/tess_card.xml" cortana_card_body)"; note "Tess's card title+body: [$CARD]"
  assert_eq "Tess's card is up" yes "$(has_node "$ROW_DIR/tess_card.xml" 'cortana_card:not_understood')"
  assert_contains "Tess's card names the voice recorder" "$BUSY" "$CARD"
  # The sentence alone: MICROPHONE_BUSY's holder token ("held by recorder") is a log detail, not card text.
  assert_eq "Tess's card title is the sentence alone" "$BUSY" "$(node_text "$ROW_DIR/tess_card.xml" cortana_card_title | python3 -c 'import html,sys; print(html.unescape(sys.stdin.read().strip()))')"
  assert_eq "speech_status mic_owner_pid is the recorder process" "$RPID" "$(speech_status mic_owner_pid)"
  assert_contains "the :speech ring holds the refusal, named" "startListening from pid=$LPID refused: held by recorder" "$(cat "$ROW_DIR/ring_tess_speech.txt")"
  assert_eq "the take is still recording" recording "$(rec_status phase)"
  # BACK closes the card, not the session (run 1: the session stayed up over the fixture); Home hides it.
  cortana_close; adb shell input keyevent KEYCODE_HOME; sleep 2
  note "after Home: $(adb shell dumpsys voiceinteraction | tr -d '\r' | grep -m1 -E 'mShowing|showing' | sed 's/^ *//')"

  # ---- the keyboard's voice key --------------------------------------------------------------------------------------
  open_field field_text
  assert_contains "the fixture's field is in front for the keyboard" "imefixture" "$(resumed)"
  KMARK="$(ring_mark)"
  kb_dump "$ROW_DIR/kb.xml" || note "kb_dump: no kb_panel in the dump"
  if [ "$(has_node "$ROW_DIR/kb.xml" kb_mic)" = yes ]; then
    tap_node "$ROW_DIR/kb.xml" kb_mic; sleep 1.5
    kb_dump "$ROW_DIR/kb_notice.xml" || true
    screencap "$ROW_DIR/kb_notice.png"
    KN="$(node_text "$ROW_DIR/kb_notice.xml" kb_notice)"; note "keyboard notice: [$KN]"
    assert_contains "the keyboard's notice names the voice recorder" "$BUSY" "$KN"
    ring_since "$KMARK" speech > "$ROW_DIR/ring_kb_speech.txt"
    assert_contains "the :speech ring refused the keyboard, naming the recorder" "refused: held by recorder" "$(cat "$ROW_DIR/ring_kb_speech.txt")"
    assert_eq "the recorder still holds the microphone" "$RPID" "$(speech_status mic_owner_pid)"
  else
    _verdict FAIL "the keyboard's voice key" "NOT RUN: the shell's keyboard (kb_mic) was not in the IME window dump"
  fi
  adb shell input keyevent KEYCODE_BACK; sleep 0.5; adb shell input keyevent KEYCODE_HOME; sleep 1

  # ---- stop the take: the recorder kept the microphone all along --------------------------------------------------
  rec_open record
  E_END="$(rec_status elapsed_ms)"
  tap_rec_button "$ROW_DIR/stop.xml"
  tone_loop_stop "$TONE_PID"; TONE_PID=""
  wait_phase idle 15 >/dev/null; sleep 1.5
  TAKE="$(new_own_id "$BEFORE")"; note "the take: ${TAKE:-none}, ${E_END:-?} ms; Tess's window ${E_A:-?}-${E_B:-?} ms"
  assert_ne "the take is in MediaStore" "" "$TAKE"
  pull_take "$TAKE" "$ROW_DIR/take.m4a"
  W="$(file_rms "$ROW_DIR/take.m4a" "$(python3 -c 'import sys; print(int(sys.argv[1])/1000)' "${E_A:-0}")" "$(python3 -c 'import sys; print(int(sys.argv[1])/1000)' "${E_B:-1000}")")"
  note "take RMS over Tess's window: $W dBFS; overall $(file_rms "$ROW_DIR/take.m4a") dBFS; $(file_duration "$ROW_DIR/take.m4a") s"
  assert_eq "the take's RMS over Tess's window is still > -40 dBFS (the recorder kept the microphone)" yes "$(db_above "$W" -40)"
  rec_ring "$MARK" > "$ROW_DIR/ring_take.txt"
  assert_contains "the :recorder ring holds the take's start" "[recorder] start Recording free=" "$(cat "$ROW_DIR/ring_take.txt")"
  assert_contains "and its stop" "[recorder] stop Recording ms=" "$(cat "$ROW_DIR/ring_take.txt")"
  [ -n "$TAKE" ] && app_delete_take "$TAKE"
  assert_eq "the take is deleted" 0 "$(own_count)"
  [ "$(own_count)" != 0 ] && note "sweeping leftovers -> $(purge_own_takes) remain"
else
  _verdict FAIL "the take under Tess's and the keyboard's requests" "NOT RUN: the audio route failed its check"
fi

# ---- conversely: Tess's recogniser holds the microphone; the record tap is refused ---------------------------------
adb shell pm grant "$FIX" android.permission.RECORD_AUDIO >/dev/null 2>&1
note "default recogniser: $(adb shell settings get secure voice_recognition_service | tr -d '\r')"
# The fixture asks from the BACKGROUND (the Voice Recorder page is in front), and its RECORD_AUDIO is a while-in-use
# grant: AppOps keeps treating it as foreground for only ~5 s after it leaves the top, after which
# RecognitionService.dispatchStartListening's data-delivery check fails, cancels at once and answers
# ERROR_INSUFFICIENT_PERMISSIONS (run 2: `[recognition] startListening` then `cancel` in the same ms, and a take started
# instead; a probe reproduced error:9 with the broadcast 9.6 s after the page opened and a held microphone at 2.6 s; the
# uid mode cannot be set to allow on this image — the permission policy keeps it `foreground`). So the record state's
# dump is taken FIRST, and the broadcast goes out the moment the reopened page is resumed, its delay logged.
rec_open record
dump_ui "$ROW_DIR/record2.xml"              # the record state's disc, for the tap below
open_field field_text                       # the fixture's receiver lives in its activity
adb shell input keyevent KEYCODE_BACK; sleep 0.5   # the keyboard down; the activity stays
N0="$(own_count)"
CMARK="$(ring_mark)"
T_LEAVE="$(date +%s%3N)"
adb shell am start -W -n "$REC_ACT" --es page record >/dev/null 2>&1
adb shell am broadcast -a "$FIX.RECOGNIZE" -p "$FIX" >/dev/null 2>&1
note "RECOGNIZE broadcast $(( $(date +%s%3N) - T_LEAVE )) ms after the recorder page was asked for (the fixture's grace ~5 s)"
OWNER=""
for _ in $(seq 1 30); do OWNER="$(speech_status mic_owner_pid)"; [ "$OWNER" = "$LPID" ] && break; sleep 0.1; done
note "microphone owner after the RECOGNIZE broadcast: [$OWNER] (launcher $LPID)"
assert_contains "the Voice Recorder page is in front" "recorder.RecorderActivity" "$(resumed)"
assert_eq "the recognition service (launcher process) holds the microphone" "$LPID" "$OWNER"
RMARK="$(ring_mark)"
gtap "$ROW_DIR/record2.xml" rec_button; sleep 1.5
rdump "$ROW_DIR/refused.xml"; screencap "$ROW_DIR/refused.png"
NOTICE="$(node_text "$ROW_DIR/refused.xml" rec_notice)"; note "recorder notice: [$NOTICE]"
assert_eq "the recorder's notice reads \"Tess is listening\"" "Tess is listening" "$NOTICE"
rec_ring "$RMARK" > "$ROW_DIR/ring_refused.txt"
assert_contains "the :recorder ring holds the named refusal" "[recorder] refused: microphone busy held by cortana" "$(cat "$ROW_DIR/ring_refused.txt")"
ring_since "$CMARK" speech > "$ROW_DIR/ring_converse_speech.txt"
assert_contains "the :speech ring refused the recorder's hold, naming Tess" "refused: held by cortana" "$(cat "$ROW_DIR/ring_converse_speech.txt")"
sleep 3
assert_eq "no file was created (MediaStore count unchanged)" "$N0" "$(own_count)"
assert_ne "no take is running" recording "$(rec_status phase)"
for _ in $(seq 1 40); do [ -z "$(speech_status mic_owner_pid)" ] || [ "$(speech_status mic_owner_pid)" = 0 ] && break; sleep 0.5; done
note "microphone owner after the recogniser finished: [$(speech_status mic_owner_pid)]"
rec_ring_save
# RV12: a take the tap started anyway (the refusal failed) is stopped and deleted through the app; the verdicts stand.
if [ "$(rec_status phase)" = recording ]; then
  B2="$(own_ids | tr '\n' ' ')"
  tap_rec_button "$ROW_DIR/stray_stop.xml"; wait_phase idle 15 >/dev/null; sleep 1.5
  STRAY="$(new_own_id "$B2")"; note "restore: the take the refused tap started anyway was stopped: ${STRAY:-none}"
  [ -n "$STRAY" ] && app_delete_take "$STRAY"
fi
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- restore ------------------------------------------------------------------------------------------------------
[ -n "$TONE_PID" ] && tone_loop_stop "$TONE_PID"
adb shell am force-stop "$FIX" >/dev/null 2>&1
kb_end
assert_eq "restore: MediaStore holds no take of the shell's" 0 "$(own_count)"
row_end
