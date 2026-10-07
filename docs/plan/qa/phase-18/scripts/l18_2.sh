#!/usr/bin/env bash
# L18-2 — a ringtone that arrives through the AlarmClock API is never a file the caller could not have read itself (the
# INDEX Blocked-on ledger's row L18-2; the adversarial GATE review's H2 and its device experiment 2).
#
# `am start -a android.intent.action.SET_ALARM … --ez android.intent.extra.alarm.SKIP_UI true --es
# android.intent.extra.alarm.RINGTONE <form>` from adb (the shell uid holds SET_ALARM), once per FORBIDDEN form:
#   file      file:///storage/emulated/0/QA-Files/hidden/qa-hidden.mp3            (a real file the shell can read)
#   path      /storage/emulated/0/QA-Files/hidden/qa-hidden.mp3
#   provider  content://app.tileshell.files/root/storage/emulated/0/QA-Files/hidden/qa-hidden.mp3   (the shell's own)
#   http      http://127.0.0.1/a.mp3
#   https     https://example.com/a.mp3
#   other     content://com.android.contacts/contacts/1/display_photo             (another app's provider)
# The safe outcome for each (the fix's guarantee — AlarmRingtoneRules): the alarm IS made (an app that passes an odd
# ringtone still gets its alarm), the store holds the DEFAULT sound for it and the forbidden text nowhere, and the ring
# holds `[alarms] api ringtone not kept (<why>) -> default sound` — the why, never the caller's text.
# Without SKIP_UI (the editor filled in, then Save): the saved alarm's sound is the default too.
# The exported editor handed a bundle directly (no AlarmClock API at all): Save stores the default as well.
# Positive controls: a real system ringtone (a row of MediaStore's internal volume, read from the device) and the
# phone's default alarm sound (content://settings/system/alarm_alert) are KEPT as a TONE with that very URI.
# Recorded, not asserted: a row of the user's own music volume from adb — the rule keeps it only when the platform says
# the starter could read it, and adb's shell uid is a starter the platform neither names nor answers for in a fixed way.
# Every alarm the row made is deleted through the app at the end; the stores end empty.
#
# The ring's own guard (RingService never opens a file: URI or a shell provider whatever the store holds) is held by
# the JVM gate at the end — the rule and the service's wiring read from its source; this row does not ring an alarm.
# Needs the fixed build installed (on the phase's gate candidate 87f6eac1 it must FAIL). The alarm stores are read
# and the alarms deleted with phase 15's own row helpers (qa/phase-15/scripts/clock.sh). No host audio is touched.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
P15S="$(cd "$P18/../phase-15/scripts" && pwd)"
. "$P15S/clock.sh"       # store_alarms, alarm_ids, alarm_field, open_clock, app_delete_alarm, API_ACT, CLOCK_ACT
STAMP_FILES="$P18/scripts/p18.sh $P15S/clock.sh"
QFP=/storage/emulated/0/QA-Files
EX=android.intent.extra.alarm

keep_earlier_run L18_2
row_begin L18_2 "the AlarmClock API's ringtone: a forbidden form becomes the default sound, a system ringtone is kept"
record "the installed APK (md5; this row needs the build that holds the L18-2 fix)" "$(q "md5sum \$(pm path app.tileshell | head -1 | sed 's/^package://')" | cut -d' ' -f1)"
baseline_start
assert_eq "precondition: the alarm store is empty" "" "$(alarm_ids | xargs)"
files_up || { row_end; exit 1; }
assert_contains "precondition: the hidden MP3 the forbidden forms name is a real file" "$QFP/hidden/qa-hidden.mp3" "$(q "ls $QFP/hidden/qa-hidden.mp3")"

# One alarm's sound as the store holds it: "<kind> <uri|null>".
alarm_sound() { store_alarms | python3 -c 'import json,sys
try:
    a=[x for x in json.load(sys.stdin) if x["id"]==sys.argv[1]]; s=a[0]["sound"]; print(s["kind"], s.get("uri") if s.get("uri") is not None else "null")
except Exception: print("")' "$1"; }
MINUTE=10
MADE=""
# The API call: ONE string to adb shell, the ringtone single-quoted. Prints the created id (the ring's own line).
set_alarm() { # name ringtone [skip_ui=true]
  local skip="--ez $EX.SKIP_UI true"
  [ "${3:-true}" = true ] || skip=""
  MINUTE=$((MINUTE + 1))
  adb shell "am start -W -a android.intent.action.SET_ALARM --ei $EX.HOUR 6 --ei $EX.MINUTES $MINUTE --es $EX.MESSAGE 'L18 $1' $skip --es $EX.RINGTONE '$2' -n $API_ACT" < /dev/null > "$ROW_DIR/$1.start.txt" 2>&1
  sleep 1.5
}

forbidden() { # name ringtone why
  local name="$1" tone="$2" why="$3" m sl id
  m="$(ring_mark)"
  set_alarm "$name" "$tone"
  sl="$(ring_since "$m")"; printf '%s\n' "$sl" | grep -F '[alarms]' > "$ROW_DIR/$name.ring.txt"
  assert_contains "$name: the alarm is still made (api SET_ALARM … -> created <id>)" "api android.intent.action.SET_ALARM from com.android.shell -> created " "$sl"
  id="$(printf '%s\n' "$sl" | grep -oE 'api android.intent.action.SET_ALARM from \S+ -> created [a-z0-9]+' | tail -1 | awk '{print $NF}')"
  [ -n "$id" ] && MADE="$MADE $id"
  assert_contains "$name: the ring says the ringtone was not kept, and why" "[alarms] api ringtone not kept ($why) -> default sound" "$sl"
  assert_eq "$name: the stored alarm's sound is the default" "DEFAULT null" "$(alarm_sound "$id")"
  assert_eq "$name: the alarm is the one asked for (6:$MINUTE)" "6 $MINUTE" "$(alarm_field "$id" hour) $(alarm_field "$id" minute)"
  # The store is JSON, which writes "/" as "\/": un-escaped first, so the search can find what it looks for.
  assert_eq "$name: the forbidden text is nowhere in the alarm store" "0" "$(store_alarms | sed 's#\\/#/#g' | grep -cF -- "$tone")"
  assert_ne "$name: …and that search reads a real store (it holds this alarm's name)" "0" "$(store_alarms | sed 's#\\/#/#g' | grep -cF -- "L18 $name")"
  absent_in "$name: …and nowhere in the ring's lines" "$tone" "$sl"
}
forbidden file "file://$QFP/hidden/qa-hidden.mp3" "not a content uri"
forbidden path "$QFP/hidden/qa-hidden.mp3" "not a content uri"
forbidden provider "content://app.tileshell.files/root$QFP/hidden/qa-hidden.mp3" "the shell's own provider"
forbidden http "http://127.0.0.1/a.mp3" "not a content uri"
forbidden https "https://example.com/a.mp3" "not a content uri"
forbidden other "content://com.android.contacts/contacts/1/display_photo" "not a system sound"

# ------------------------------------------------------------------------------------------------ without SKIP_UI
log "--- without SKIP_UI: the editor opens filled in; Save stores the default sound"
BEFORE="$(alarm_ids | xargs)"
M="$(ring_mark)"
set_alarm editor "file://$QFP/hidden/qa-hidden.mp3" false
SL="$(ring_since "$M")"
assert_contains "editor: the request opened the editor" "api android.intent.action.SET_ALARM from com.android.shell -> opened" "$SL"
assert_contains "editor: the ring says the ringtone was not kept" "[alarms] api ringtone not kept (not a content uri) -> default sound" "$SL"
dump_ui "$ROW_DIR/editor.xml"; screencap "$ROW_DIR/editor.png"
assert_eq "editor: the editor is open on the request (name L18 editor)" "L18 editor" "$(node_text "$ROW_DIR/editor.xml" 'alarm_editor_field:name')"
assert_eq "editor: nothing is stored before Save" "$BEFORE" "$(alarm_ids | xargs)"
tap_node "$ROW_DIR/editor.xml" "clock_bar:save"; sleep 2
EID="$(alarm_ids | grep -vxF -f <(printf '%s\n' $BEFORE) | head -1)"
assert_ne "editor: Save stored the alarm" "" "$EID"
[ -n "$EID" ] && MADE="$MADE $EID"
assert_eq "editor: its sound is the default" "DEFAULT null" "$(alarm_sound "$EID")"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ------------------------------------------------------------------------------------------------ the exported editor, directly
# ClockActivity is exported and takes the handler's bundle; `am start` cannot build a Bundle extra, so this leg is the
# JVM gate's (AlarmRingtoneRulesTest "what the editor is handed from the API …", AlarmSoundWiringScanTest) — recorded.
record "the exported editor handed a forged bundle directly" "not drivable from adb (am start carries no Bundle extra): held by the JVM gate below"

# ------------------------------------------------------------------------------------------------ positive controls
log "--- positive controls: a real system ringtone, and the phone's default alarm sound, are kept"
RID="$(q "content query --uri content://media/internal/audio/media --projection _id:is_alarm" | sed -n 's/.*_id=\([0-9]*\), is_alarm=1.*/\1/p' | head -1)"
[ -n "$RID" ] || RID="$(q "content query --uri content://media/internal/audio/media --projection _id" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)"
assert_ne "the device has a system sound (a row of MediaStore's internal volume)" "" "$RID"
kept() { # name ringtone
  local name="$1" tone="$2" m sl id
  m="$(ring_mark)"
  set_alarm "$name" "$tone"
  sl="$(ring_since "$m")"; printf '%s\n' "$sl" | grep -F '[alarms]' > "$ROW_DIR/$name.ring.txt"
  id="$(printf '%s\n' "$sl" | grep -oE 'api android.intent.action.SET_ALARM from \S+ -> created [a-z0-9]+' | tail -1 | awk '{print $NF}')"
  assert_ne "$name: the alarm is made" "" "$id"
  [ -n "$id" ] && MADE="$MADE $id"
  absent_in "$name: no 'ringtone not kept' line" "api ringtone not kept" "$sl"
  assert_eq "$name: the stored sound is that very URI, as a TONE" "TONE $tone" "$(alarm_sound "$id")"
}
kept system "content://media/internal/audio/media/$RID"
kept setting "content://settings/system/alarm_alert"

# A row of the user's own music volume: kept only when the platform says the starter could read it. From adb the
# starter is the shell uid, which the platform does not name to the activity — recorded with what the platform answered.
AUD="$(q "content query --uri content://media/external/audio/media --projection _id:_data" | grep -F "_data=$QFP/03.mp3" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
if [ -n "$AUD" ]; then
  M="$(ring_mark)"
  set_alarm music "content://media/external/audio/media/$AUD"
  SL="$(ring_since "$M")"
  UID_="$(printf '%s\n' "$SL" | grep -oE 'api android.intent.action.SET_ALARM from \S+ -> created [a-z0-9]+' | tail -1 | awk '{print $NF}')"
  [ -n "$UID_" ] && MADE="$MADE $UID_"
  assert_ne "music row: the alarm is made either way" "" "$UID_"
  record "music row (content://media/external/audio/media/$AUD) from adb: the stored sound / the ring's line" \
    "$(alarm_sound "$UID_") / $(printf '%s\n' "$SL" | grep -o 'api ringtone not kept ([^)]*)' | tail -1)"
  assert_contains "music row: the stored sound is the default, or that very row (never anything else)" "|$(alarm_sound "$UID_")|" "|DEFAULT null|TONE content://media/external/audio/media/$AUD|"
else
  record "music row" "03.mp3 has no audio row on this run: leg not run"
fi

# ------------------------------------------------------------------------------------------------ restore
log "--- restore: every alarm the row made, deleted through the app"
record "the alarms the row made" "$(echo $MADE | wc -w): $MADE"
for id in $MADE; do app_delete_alarm "$id"; done
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_eq "restore: the alarm store is empty again" "" "$(alarm_ids | xargs)"
assert_eq "restore: none of the shell's alarms is pending (dumpsys alarm)" "0" "$(alarm_trigger_ms | grep -c .)"
files_down
ensure_start

# ------------------------------------------------------------------------------------------------ the JVM side
log "--- the rules, the ring's guard and the wiring on the JVM"
jvm_gate 'app.tileshell.clock.Alarm*' 'app.tileshell.clock.Alarm*' \
  ringtoneMapsToTheSound \
  aRingtoneTheCallerCouldNotHaveReadBecomesTheDefaultSound \
  aRingtoneOnTheUsersOwnVolumeIsKeptOnlyWhenThePlatformSaysTheCallerCouldReadIt \
  "the ring never opens a file URI, a path, a web address or anything but a content URI" \
  "the ring never opens one of the shell's own providers, however the authority is written" \
  "every sound the shell's own editor offers still rings" \
  "what the editor is handed from the API is weighed as for a caller nobody can be asked about" \
  "L18-2 a ringtone from the API is the rule's sound, and the ring asks before it opens a stored URI"
row_end
