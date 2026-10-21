#!/usr/bin/env bash
# L18-3 — phase 15's Clock, the exported activities under hostile extras (the INDEX Blocked-on ledger's row L18-3; the
# adversarial review pass 2's N1 / N4 and N11 / N12, and pass 3's P3-4). Driven from testapps/qa-capture (an app that
# is NOT the shell's uid). Legs are selectable: l18_3.sh [a b c d e]; the default run is `a b`.
#
#   a (N1)   qa-capture starts the exported ClockActivity with api_edit = Bundle{kind=alarm, days=[99]}. SAFE: no
#            crash (the launcher's pid is unchanged, no FATAL for app.tileshell) and — qa-capture is not the shell's
#            uid — the bundle is NOT read at all (`[clock] api edit ignored: not the shell`; the editor never opens
#            prefilled). Gate build.
#   b (N4)   qa-capture sends SET_ALARM to AlarmApiActivity with EXTRA_DAYS holding a Parcelable of its OWN class.
#            SAFE: the request is refused ("an extra could not be read"), no crash, the pid unchanged, nothing stored.
#            Gate build.
#   c (N11)  an alarm whose sound is a ZERO-BYTE .mp3 rings → the DEFAULT sound plays. The fixed build's lines (gate
#            build 23fe18a8), asserted IN THIS ORDER: `ring <id> sound=content://media/…/<n>`, `ring <id> sound could
#            not play (<Class>) -> default sound`, `ring <id> sound=default (fallback)`, `ring level full`; FAIL if
#            `… default sound could not play … -> vibrate only`. Also: an ALARM player of the shell is started (dumpsys
#            audio). Positive control: an alarm whose sound is a REAL playable MUSIC6 mp3 rings `ring <id>
#            sound=content://…/<n>` with NO could-not-play and NO fallback line. Selectable on its own.
#   d (N12)  SET_ALARM with EXTRA_RINGTONE = a MediaStore audio row qa-capture inserted and can read. RECORD which ONE
#            handler line appears, and ASSERT it is exactly one of `api ringtone not kept (the caller could not read it;
#            launch answer read: threw <Class> | denied) -> default sound` / `api ringtone kept (it had access at launch;
#            launch answer read: granted)`; then ASSERT the editor (no SKIP_UI) shows the same sound SKIP_UI stored.
#            Selectable on its own.
#   e (P3-4) the shell launches ClockActivity (its Start tile); qa-capture starts it again with api_edit =
#            Bundle{kind=alarm, hour=6, minute=45} → `[clock] api edit ignored: not the shell` and `[clock] new intent
#            not kept for a relaunch: its edit bundle is not the shell's`; then a font-weight config change recreates
#            it. SAFE: no editor, and the `[clock] open` line after the relaunch is `open page=none tile=none api=none ->
#            tab unchanged` (no api=alarm). Failed on b1a22a1d; must pass on 23fe18a8. Selectable on its own.
#
# No host audio is ever touched: the emulator runs with QEMU_AUDIO_DRV=none, so "the default sound plays" is read from
# the app's own `[alarms]` diagnostics line and from `dumpsys audio` (a shell ALARM player started), never from the host.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
P15S="$(cd "$P18/../phase-15/scripts" && pwd)"
. "$P15S/clock.sh"      # store_alarms, alarm_ids, alarm_field, open_clock, app_delete_alarm, wait_ring, alarm_player_started, API_ACT, CLOCK_ACT
STAMP_FILES="$P18/scripts/p18.sh $P18/scripts/rowsb.sh $P15S/clock.sh"
LEGS="${*:-a b}"
leg() { case " $LEGS " in *" $1 "*) log "--- leg $1 ($(date '+%H:%M:%S'))"; return 0 ;; *) return 1 ;; esac; }
CLOCK_TILE="tile:app:app.tileshell/app.tileshell.clock.ClockActivity:0"
CE="$QAC/.ClockEditActivity"
SA="$QAC/.SetAlarmActivity"
pid_now() { q 'pidof app.tileshell'; }
clock_ring() { ring_since "$1" | grep -F '[clock]'; }
alarm_ring() { ring_since "$1" | grep -F '[alarms]'; }
alarm_sound() { store_alarms | python3 -c 'import json,sys
try:
    a=[x for x in json.load(sys.stdin) if x["id"]==sys.argv[1]]; s=a[0]["sound"]; print(s["kind"], s.get("uri") if s.get("uri") is not None else "null")
except Exception: print("")' "$1"; }
MADE=""        # alarm ids made in the row, deleted at the end
AUDIO_ROWS=""  # MediaStore audio uris qa-capture inserted, deleted at the end

keep_earlier_run L18_3
row_begin L18_3 "Clock's exported activities under hostile extras: ignored / refused / the N11 fallback / N12 agreement"
record "the installed APK (md5); every leg asserts the gate build (L18-3's round 3 merged into 23fe18a8)" "$(gate_apk_installed)"
baseline_start
assert_eq "precondition: the alarm store is empty" "" "$(alarm_ids | xargs)"

# ------------------------------------------------------------------------------------------------ qa-capture
QAC_AT_BEGIN="$(q "pm path $QAC")"
if [ ! -f "$QAC_APK" ]; then
  _verdict FAIL "qa-capture is built" "missing $QAC_APK (./gradlew :testapps:qa-capture:assembleDebug --offline)"
  row_end; exit 1
fi
adb install -r "$QAC_APK" > "$ROW_DIR/qa-capture-install.out" 2>&1
assert_contains "qa-capture installed" "Success" "$(cat "$ROW_DIR/qa-capture-install.out")"
QAC_UID="$(q "cmd package list packages -U $QAC" | sed -n "s/^package:$QAC uid://p" | head -1)"
SHELL_APP_UID="$(q "cmd package list packages -U app.tileshell" | sed -n 's/^package:app.tileshell uid://p' | head -1)"
assert_ne "the sender's uid is known and is not the shell's ($QAC_UID / $SHELL_APP_UID)" "$SHELL_APP_UID" "${QAC_UID:-$SHELL_APP_UID}"
record "qa-capture's granted permissions (SET_ALARM is a normal one)" "$(q "dumpsys package $QAC" | grep -oE '[a-zA-Z.]*SET_ALARM[^ ]*: granted=true|WRITE_CONTACTS: granted=true' | xargs)"

# ------------------------------------------------------------------------------------------------ a (N1)
if leg a; then
  c6; ensure_start
  P0="$(pid_now)"; adb logcat -c; M="$(ring_mark)"
  adb shell "am start -n $CE --es kind alarm --ei badday 99" > "$ROW_DIR/a-start.out" 2>&1
  sleep 3; D a-1; S a-1
  SL="$(clock_ring "$M")"
  record "a: the [clock] lines for the hostile api_edit" "$(printf '%s\n' "$SL" | grep -oE '\[clock\] (api edit|open) .*' | sed 's/ *wall=.*//' | tr '\n' '|')"
  assert_contains "a: SAFE — the bundle is not read (api edit ignored: not the shell)" "[clock] api edit ignored: not the shell" "$SL"
  assert_contains "a: SAFE — the open line routes api=ignored, not the shell" "[clock] open page=alarm tile=none api=ignored: not the shell" "$SL"
  assert_eq "a: SAFE — the editor is NOT opened prefilled (no alarm editor field on screen)" "no" "$(H a-1 alarm_editor_field:name)"
  assert_eq "a: SAFE — no crash, the launcher's pid is unchanged, AndroidRuntime is empty of the shell" "$P0 0" "$(pid_now) $(crash)"
  assert_eq "a: SAFE — nothing was stored" "" "$(alarm_ids | xargs)"
  adb shell input keyevent KEYCODE_HOME; sleep 1; rings_save
fi

# ------------------------------------------------------------------------------------------------ b (N4)
if leg b; then
  c6; ensure_start
  P0="$(pid_now)"; adb logcat -c; M="$(ring_mark)"
  adb shell "am start -n $SA --es mode days --ei hour 6 --ei minute 20 --es message 'L18 N4'" > "$ROW_DIR/b-start.out" 2>&1
  sleep 3
  SL="$(alarm_ring "$M")"
  record "b: the [alarms] lines for the foreign Parcelable in EXTRA_DAYS" "$(printf '%s\n' "$SL" | grep -oE '\[alarms\] api .*' | sed 's/ *wall=.*//' | tr '\n' '|')"
  assert_contains "b: SAFE — an extra that could not be read refuses the request" "-> refused: an extra could not be read" "$SL"
  assert_contains "b: …and the refused extra is noted (never the caller's value)" "[alarms] api extra " "$SL"
  assert_eq "b: SAFE — no crash, the launcher's pid is unchanged" "$P0 0" "$(pid_now) $(crash)"
  assert_eq "b: SAFE — nothing was stored" "" "$(alarm_ids | xargs)"
  adb shell input keyevent KEYCODE_HOME; sleep 1; rings_save
fi

# ------------------------------------------------------------------------------------------------ c (N11) — needs the NEW clock build
if leg c; then
  record "c: the installed APK (N11 fixed in the gate build)" "$(gate_apk_installed)"
  # An alarm's sound set through the editor's music picker (EDGE_LOCKED_SOUND's route); dumps <prefix>-*.xml.
  set_sound_ui() { # alarm-id mediastore-id prefix
    open_clock alarm; dump_ui "$ROW_DIR/$3-list.xml"; tap_node "$ROW_DIR/$3-list.xml" "alarm_row:$1"; sleep 1.5
    dump_ui "$ROW_DIR/$3-editor.xml"; tap_node "$ROW_DIR/$3-editor.xml" 'alarm_editor_field:sound'; sleep 1.2
    dump_ui "$ROW_DIR/$3-flyout.xml"; tap_node "$ROW_DIR/$3-flyout.xml" 'alarm_sound:music'; sleep 2
    dump_ui "$ROW_DIR/$3-picker.xml"
    if [ "$(has_node "$ROW_DIR/$3-picker.xml" alarm_sound_grant)" = yes ]; then tap_node "$ROW_DIR/$3-picker.xml" alarm_sound_grant; sleep 2; dump_ui "$ROW_DIR/$3-picker.xml"; fi
    scroll_to_node "$ROW_DIR/$3-picker.xml" "alarm_sound_pick:$2" 10 >/dev/null || true
    assert_eq "$3: the picker lists the track ($2)" "yes" "$(has_node "$ROW_DIR/$3-picker.xml" "alarm_sound_pick:$2")"
    tap_node "$ROW_DIR/$3-picker.xml" "alarm_sound_pick:$2"; sleep 1.5; dump_ui "$ROW_DIR/$3-chosen.xml"
    tap_node "$ROW_DIR/$3-chosen.xml" "clock_bar:save"; sleep 2
    adb shell input keyevent KEYCODE_HOME; sleep 1
  }
  # The lines of a ring slice, asserted in the order given (each needle found on a line after the previous one's).
  in_order() { # slice needle…
    python3 - "$@" <<'PY'
import sys
lines, needles, at, got = sys.argv[1].splitlines(), sys.argv[2:], 0, []
for n in needles:
    hit = next((i for i in range(at, len(lines)) if n in lines[i]), None)
    if hit is None: print("missing after line %d: [%s] (found in order: %s)" % (at, n, got)); sys.exit()
    got.append(hit); at = hit + 1
print("in order")
PY
  }
  adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
  # A zero-byte .mp3 that MediaStore indexes as music, so the editor's music picker lists it.
  adb shell "mkdir -p /sdcard/Music/tessera-qa && : > /sdcard/Music/tessera-qa/qa-zero.mp3" >/dev/null 2>&1
  adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1; sleep 2
  ZID="$(q "content query --uri content://media/external/audio/media --projection _id:_data" | grep -F "/Music/tessera-qa/qa-zero.mp3" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
  record "c: the zero-byte mp3's MediaStore id" "${ZID:-none}"
  if [ -z "$ZID" ]; then
    _verdict FAIL "c: the zero-byte mp3 has a MediaStore audio row" "no id (is READ_MEDIA_AUDIO granted and the scan done?)"
  else
    NOW="$(device_ms)"; read -r AH AM_ <<< "$(device_hm $(( NOW + 300000 )))"
    AID="$(api_alarm "$AH" "$AM_" "L18 N11")"; assert_ne "c: the alarm was created" "" "$AID"; MADE="$MADE $AID"
    # Set its sound to the zero-byte track through the editor (EDGE_LOCKED_SOUND's route).
    set_sound_ui "$AID" "$ZID" c
    assert_contains "c: the store holds the zero-byte track as the alarm's sound" "content://media/external/audio/media/$ZID" "$(alarm_sound "$AID")"
    AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"; assert_ne "c: the alarm is armed" "0" "$AT"
    # Fire it: jump the clock to just before it, read the ring (no sound reaches the host — QEMU_AUDIO_DRV=none).
    adb shell settings put global auto_time 0 >/dev/null 2>&1; M="$(ring_mark)"; adb shell cmd alarm set-time "$(( AT - 8000 ))" >/dev/null 2>&1
    FIRED="$(wait_ring "$M" "[alarms] fired $AID kind=alarm" 30)"
    assert_ne "c: the alarm fired within 30 s of the jump" "" "$FIRED"
    sleep 3; SL="$(alarm_ring "$M")"; printf '%s\n' "$SL" > "$ROW_DIR/c-ring.txt"
    record "c: the ring's sound lines" "$(printf '%s\n' "$SL" | grep -oE '\[alarms\] (ring|sound).*' | sed 's/ *wall=.*//' | tr '\n' '|')"
    assert_contains "c: SAFE — the chosen sound opened (ring $AID sound=content://media/…)" "ring $AID sound=content://media/external/audio/media/$ZID" "$SL"
    assert_contains "c: SAFE — it could not play, so the DEFAULT sound is chosen" "ring $AID sound could not play (" "$SL"
    assert_contains "c: SAFE — …-> default sound" "-> default sound" "$SL"
    assert_contains "c: SAFE — the default sound then plays (fallback)" "ring $AID sound=default (fallback)" "$SL"
    assert_contains "c: SAFE — the ring plays at full level" "[alarms] ring level full" "$SL"
    assert_absent "c: SAFE — it did NOT fall through to vibrate only" "default sound could not play" "$SL"
    assert_eq "c: SAFE — the four lines come IN ORDER: sound=content… / could not play (…) -> default sound / sound=default (fallback) / ring level full" "in order" \
      "$(in_order "$SL" "[alarms] ring $AID sound=content://media/external/audio/media/$ZID" "[alarms] ring $AID sound could not play (" "[alarms] ring $AID sound=default (fallback)" "[alarms] ring level full")"
    assert_ne "c: SAFE — a shell ALARM player is started (dumpsys audio; proves playback, not the host)" "0" "$(alarm_player_started)"
    dismiss_any_ring
  fi
  for id in $MADE; do app_delete_alarm "$id"; done; MADE=""
  # Positive control: a REAL playable MUSIC6 mp3 as the sound rings that sound, with no fallback.
  REAL_MP3="$(ls "$MUSIC6"/*.mp3 | head -1)"
  adb push "$REAL_MP3" /sdcard/Music/tessera-qa/qa-real.mp3 >/dev/null 2>&1
  adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1; sleep 2
  for _ in 1 2 3 4 5; do
    RID="$(q "content query --uri content://media/external/audio/media --projection _id:_data" | grep -F "/Music/tessera-qa/qa-real.mp3" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
    [ -n "$RID" ] && break; sleep 2
  done
  record "c (control): the real MUSIC6 mp3 ($(basename "$REAL_MP3")) / its MediaStore id" "qa-real.mp3 / ${RID:-none}"
  if [ -z "$RID" ]; then
    _verdict FAIL "c (control): the real mp3 has a MediaStore audio row" "no id"
  else
    NOW="$(device_ms)"; read -r AH AM_ <<< "$(device_hm $(( NOW + 300000 )))"
    AID2="$(api_alarm "$AH" "$AM_" "L18 N11 control")"; assert_ne "c (control): the alarm was created" "" "$AID2"; MADE="$MADE $AID2"
    set_sound_ui "$AID2" "$RID" c2
    assert_contains "c (control): the store holds the real track as the alarm's sound" "content://media/external/audio/media/$RID" "$(alarm_sound "$AID2")"
    AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"; assert_ne "c (control): the alarm is armed" "0" "$AT"
    M="$(ring_mark)"; adb shell cmd alarm set-time "$(( AT - 8000 ))" >/dev/null 2>&1
    FIRED="$(wait_ring "$M" "[alarms] fired $AID2 kind=alarm" 30)"
    assert_ne "c (control): the alarm fired within 30 s of the jump" "" "$FIRED"
    sleep 3; SL="$(alarm_ring "$M")"; printf '%s\n' "$SL" > "$ROW_DIR/c2-ring.txt"
    record "c (control): the ring's sound lines" "$(printf '%s\n' "$SL" | grep -oE '\[alarms\] (ring|sound).*' | sed 's/ *wall=.*//' | tr '\n' '|')"
    assert_contains "c (control): the real sound plays (ring $AID2 sound=content://media/…/$RID)" "ring $AID2 sound=content://media/external/audio/media/$RID" "$SL"
    absent_in "c (control): NO could-not-play line" "ring $AID2 sound could not play" "$SL"
    absent_in "c (control): NO fallback line" "ring $AID2 sound=default (fallback)" "$SL"
    assert_ne "c (control): a shell ALARM player is started (dumpsys audio)" "0" "$(alarm_player_started)"
    dismiss_any_ring
  fi
  for id in $MADE; do app_delete_alarm "$id"; done; MADE=""
  adb shell "rm -f /sdcard/Music/tessera-qa/qa-zero.mp3 /sdcard/Music/tessera-qa/qa-real.mp3; rmdir /sdcard/Music/tessera-qa" >/dev/null 2>&1
  adb shell "content delete --uri content://media/external/audio/media --where \"_data LIKE '%/Music/tessera-qa/qa-zero.mp3'\"" >/dev/null 2>&1
  adb shell "content delete --uri content://media/external/audio/media --where \"_data LIKE '%/Music/tessera-qa/qa-real.mp3'\"" >/dev/null 2>&1
  adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1
  # Put the clock back (as phase 15's restore_clock does): the host wall time, then automatic time on again.
  adb shell date -u "$(date -u +%m%d%H%M%Y.%S)" >/dev/null 2>&1; adb shell settings put global auto_time 1 >/dev/null 2>&1
  adb shell input keyevent KEYCODE_HOME; sleep 1; rings_save
fi

# ------------------------------------------------------------------------------------------------ d (N12) — needs the NEW clock build
if leg d; then
  record "d: the installed APK (N12 fixed in the gate build)" "$(gate_apk_installed)"
  before_ids() { alarm_ids | xargs; }
  new_id() { alarm_ids | grep -vxF -f <(printf '%s\n' $1 | tr ' ' '\n') | head -1; }
  # The default's editor label, as a reference: a request whose ringtone is always dropped (a file: URI) opens the editor.
  BEFORE="$(before_ids)"; adb logcat -c
  adb shell "am start -W -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR 6 --ei android.intent.extra.alarm.MINUTES 51 --es android.intent.extra.alarm.MESSAGE 'L18 default' --es android.intent.extra.alarm.RINGTONE 'file:///nope.mp3' -n $API_ACT" >/dev/null 2>&1
  sleep 2; dump_ui "$ROW_DIR/d-default-editor.xml"
  DEFAULT_LABEL="$(node_text "$ROW_DIR/d-default-editor.xml" 'alarm_editor_field:sound')"
  record "d: the editor's sound label for a dropped (default) ringtone" "[$DEFAULT_LABEL]"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  for id in $(before_ids); do case " $BEFORE " in *" $id "*) ;; *) app_delete_alarm "$id" ;; esac; done

  # SKIP_UI: qa-capture inserts a readable MediaStore row and sends it; RECORD the ONE handler line + the stored sound.
  BEFORE="$(before_ids)"; adb logcat -c; M="$(ring_mark)"
  adb shell "am start -n $SA --es mode ringtone --ez skip true --ei hour 6 --ei minute 52 --es message 'L18 N12'" > "$ROW_DIR/d-skip.out" 2>&1
  sleep 3
  RURI="$(adb logcat -d -s "$QAC_TAG:I" 2>/dev/null | tr -d '\r' | sed -n 's/.*setalarm mode=ringtone uri=\([^ ]*\).*/\1/p' | tail -1)"
  record "d: the MediaStore ringtone URI qa-capture inserted and granted" "${RURI:-none}"
  [ -n "$RURI" ] && AUDIO_ROWS="$AUDIO_ROWS $RURI"
  SL="$(alarm_ring "$M")"
  RLINE="$(printf '%s\n' "$SL" | grep -oE '\[alarms\] api ringtone (not kept|kept) .*' | sed 's/ *wall=.*//' | head -1)"
  record "d (N12): which handler ringtone line appeared" "[$RLINE]"
  assert_ne "d: a ringtone line was written (kept or not kept), so the launch-answer path ran" "" "$RLINE"
  # Exactly one handler ringtone line, and it is one of N12's three forms (the Change Log's (9)/(10), T15-37).
  assert_eq "d: exactly ONE api ringtone line for the request" "1" "$(printf '%s\n' "$SL" | grep -cE '\[alarms\] api ringtone (not kept|kept) ')"
  N12_FORM="$(printf '%s\n' "$RLINE" | python3 -c '
import re, sys
l = sys.stdin.read().strip()
forms = [("not kept, launch answer threw", r"\[alarms\] api ringtone not kept \(the caller could not read it; launch answer read: threw [A-Za-z0-9_$.]+\) -> default sound"),
         ("not kept, launch answer denied", r"\[alarms\] api ringtone not kept \(the caller could not read it; launch answer read: denied\) -> default sound"),
         ("kept, launch answer granted", r"\[alarms\] api ringtone kept \(it had access at launch; launch answer read: granted\)")]
print(next((n for n, p in forms if re.fullmatch(p, l)), "none of the three forms"))')"
  record "d (N12): the line's form" "$N12_FORM"
  assert_ne "d: the line is one of the three N12 forms (threw <Class> / denied / granted)" "none of the three forms" "$N12_FORM"
  SKIP_ID="$(new_id "$BEFORE")"; assert_ne "d: the SKIP_UI alarm was created" "" "$SKIP_ID"; [ -n "$SKIP_ID" ] && MADE="$MADE $SKIP_ID"
  STORED="$(alarm_sound "$SKIP_ID")"
  record "d: the stored sound (SKIP_UI)" "[$STORED]"

  # No SKIP_UI: the same request opens the editor; its shown sound must AGREE with what SKIP_UI stored.
  adb logcat -c
  adb shell "am start -n $SA --es mode ringtone --ez skip false --ei hour 6 --ei minute 53 --es message 'L18 N12 editor'" >/dev/null 2>&1
  sleep 3; dump_ui "$ROW_DIR/d-editor.xml"
  # This request inserts a second MediaStore row of qa-capture's; it is deleted in the restore with the first.
  RURI2="$(adb logcat -d -s "$QAC_TAG:I" 2>/dev/null | tr -d '\r' | sed -n 's/.*setalarm mode=ringtone uri=\([^ ]*\).*/\1/p' | tail -1)"
  [ -n "$RURI2" ] && AUDIO_ROWS="$AUDIO_ROWS $RURI2"
  EDLABEL="$(node_text "$ROW_DIR/d-editor.xml" 'alarm_editor_field:sound')"
  record "d: the editor's sound label (no SKIP_UI)" "[$EDLABEL]"
  case "$STORED" in
    "DEFAULT null") assert_eq "d: AGREEMENT — a dropped ringtone shows the default in the editor too" "$DEFAULT_LABEL" "$EDLABEL" ;;
    "TONE "*|"MUSIC "*) assert_ne "d: AGREEMENT — a kept ringtone shows a tone (not the default label) in the editor too" "$DEFAULT_LABEL" "$EDLABEL" ;;
    *)              _verdict FAIL "d: AGREEMENT — the stored sound is readable (DEFAULT or TONE)" "stored=[$STORED]" ;;
  esac
  adb shell input keyevent KEYCODE_HOME; sleep 1
  for id in $(before_ids); do case " $BEFORE $SKIP_ID " in *" $id "*) ;; *) app_delete_alarm "$id" ;; esac; done
  rings_save
fi

# ------------------------------------------------------------------------------------------------ e (P3-4)
if leg e; then
  # The configuration's fontWeightAdjustment, NOT the setting, is what a relaunch follows: Android copies a defined value
  # into the global configuration and never resets it to undefined (`settings delete` leaves 300 in place — found
  # 2026-10-20: after b1a22a1d's run the setting read null and mGlobalConfig held fontWeightAdjustment=300, so this run's
  # `put 300` changed nothing and nothing relaunched). So the trigger is a value the configuration does NOT hold, and
  # the restore puts 0 (no adjustment: text drawn at its own weight) before it deletes a setting that was absent.
  fwa_cfg() { adb shell dumpsys activity activities | tr -d '\r' | grep -m1 'mGlobalConfig=' | grep -o 'fontWeightAdjustment=-\?[0-9]*' | cut -d= -f2; }
  FWA_OLD="$(adb shell settings get secure font_weight_adjustment | tr -d '\r')"
  FWA_CFG_OLD="$(fwa_cfg)"
  record "e: font_weight_adjustment before the row: the setting / the configuration's (restored in the trap)" "$FWA_OLD / ${FWA_CFG_OLD:-undefined}"
  restore_fwa() {
    if [ -z "$FWA_OLD" ] || [ "$FWA_OLD" = null ]; then
      adb shell settings put secure font_weight_adjustment 0 >/dev/null 2>&1; sleep 1
      adb shell settings delete secure font_weight_adjustment >/dev/null 2>&1
    else adb shell settings put secure font_weight_adjustment "$FWA_OLD" >/dev/null 2>&1; fi
  }
  _p18_trap_add restore_fwa
  E_ALARMS="$(alarm_ids | xargs)"   # leg d's SKIP_UI alarm stays until the restore; leg e must add none
  c6; ensure_start
  # The shell launches Clock from its Start tile (launchedFromUid = the shell app).
  dump_ui "$ROW_DIR/e-start.xml"; scroll_to_node "$ROW_DIR/e-start.xml" "$CLOCK_TILE" 6 >/dev/null || true
  if [ "$(has_node "$ROW_DIR/e-start.xml" "$CLOCK_TILE")" = yes ]; then tap_node "$ROW_DIR/e-start.xml" "$CLOCK_TILE"; sleep 3
  else adb shell "am start -n $CLOCK_ACT" >/dev/null 2>&1; sleep 3; note "e: the Clock tile was not on Start; fell back to am start (launchedFromUid is then the shell STUB, not the app)"; fi
  assert_eq "e: Clock is open (top activity)" "$CLOCK_ACT" "$(top_activity)"
  # qa-capture sends api_edit to the running Clock → a new intent, ignored (not the shell), but the intent is retained.
  adb logcat -c; M="$(ring_mark)"
  adb shell "am start -n $CE --es kind alarm --ei hour 6 --ei minute 45" >/dev/null 2>&1
  sleep 3; D e-1
  SL="$(clock_ring "$M")"; printf '%s\n' "$SL" > "$ROW_DIR/e-newintent-ring.txt"
  assert_contains "e: the hostile new intent is ignored (not the shell)" "[clock] api edit ignored: not the shell" "$SL"
  assert_contains "e: …and not kept for a relaunch (the fix's line)" "[clock] new intent not kept for a relaunch: its edit bundle is not the shell's" "$SL"
  assert_eq "e: the editor is NOT open after the new intent" "no" "$(H e-1 alarm_editor_field:name)"
  # The font-weight config change recreates ClockActivity, which re-reads the RETAINED hostile intent.
  FWA_T=300; [ "$(fwa_cfg)" = 300 ] && FWA_T=200
  M2="$(ring_mark)"; adb shell settings put secure font_weight_adjustment "$FWA_T" >/dev/null 2>&1; sleep 3; D e-2; S e-2
  record "e: the configuration's fontWeightAdjustment after the put (the trigger)" "$(fwa_cfg) (put $FWA_T)"
  assert_contains "e: the config change relaunched ClockActivity ([clock] ClockActivity created after the put)" "[clock] ClockActivity created" "$(clock_ring "$M2")"
  SL="$(clock_ring "$M2")"
  record "e: the [clock] open line(s) after the config-change relaunch" "$(printf '%s\n' "$SL" | grep -oE '\[clock\] open .*' | sed 's/ *wall=.*//' | tr '\n' '|')"
  OPEN="$(printf '%s\n' "$SL" | grep -oE '\[clock\] open .*' | sed 's/ *wall=.*//' | tail -1)"
  assert_absent "e: SAFE — the relaunch does NOT route the retained bundle as the shell's (no api=alarm)" "api=alarm" "$OPEN"
  assert_eq "e: SAFE — the relaunch's open line routes nothing (open page=none tile=none api=none -> tab unchanged)" "[clock] open page=none tile=none api=none -> tab unchanged" "$OPEN"
  assert_eq "e: SAFE — no editor appears after the relaunch" "no" "$(H e-2 alarm_editor_field:name)"
  assert_eq "e: SAFE — no crash, nothing stored (the store as the leg found it)" "0 $E_ALARMS" "$(crash) $(alarm_ids | xargs)"
  restore_fwa
  assert_eq "e: restore — the configuration draws text at its own weight again (fontWeightAdjustment 0, or the row's old value)" "${FWA_OLD/null/0}" "$(fwa_cfg)"
  adb shell input keyevent KEYCODE_HOME; sleep 1; rings_save
fi

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
for id in $MADE; do app_delete_alarm "$id"; done
for u in $AUDIO_ROWS; do adb shell "content delete --uri '$u'" >/dev/null 2>&1; done
adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_eq "restore: the alarm store is empty again" "" "$(alarm_ids | xargs)"
if [ -z "$QAC_AT_BEGIN" ]; then adb uninstall "$QAC" >/dev/null 2>&1; assert_eq "restore: qa-capture (not installed at the start) is uninstalled" "" "$(q "pm path $QAC")"; fi
ensure_start
assert_eq "no crash of the shell in the row (AndroidRuntime)" "0" "$(crash)"
row_end
