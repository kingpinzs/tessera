#!/usr/bin/env bash
# Phase 16 TRUST — the device proof the adversarial trust review asked for (Decisions "Trust", r3 D10; trust review B's
# findings F3–F5, F8 and its "Not checked" list; qa/phase-16/fix-round.md). Not one of the doc's E rows: it is the
# evidence "recorded under qa/phase-16/ before done" for the parts no E row drives.
#
#   P  ACTION_PICK from a real caller that holds no permission (testapps/pick-probe): the result is the one contact
#      lookup URI or phone data URI tapped, flags 0x1 and nothing else; the caller can read that row and NOTHING more
#      (tables, rows under it, write, delete, persist); one read grant in `dumpsys activity permissions`; Back cancels;
#      a PICK asked with NEW_TASK or with no caller returns nothing, grants nothing, and leaves no pick mode behind
#   R  nothing a caller sends reaches the diagnostics ring: a forged action string, a prefill's text
#   N  the reminder notification: its PendingIntents are immutable and explicit, it is VISIBILITY_PRIVATE with a public
#      version; a flood of forged pokes posts nothing and costs no write
#   S  the swipe on a stale reminder whose alert row id was reused by another event leaves that other alert alone
#
# TRUST_ONLY=P,R runs only those parts (development; the gate run never sets it).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
[ -f "$HERE/cal_lib.sh" ] && . "$HERE/cal_lib.sh"
PROBE_APK="$REPO/testapps/pick-probe/build/outputs/apk/debug/pick-probe-debug.apk"
PROBE=app.tileshell.qa.pickprobe
want() { [ -z "${TRUST_ONLY:-}" ] || echo ",$TRUST_ONLY," | grep -q ",$1,"; }
probe_lines() { adb logcat -d -s PICKPROBE:I | tr -d '\r' | sed -n 's/.*PICKPROBE: //p'; }
probe_get() { probe_lines | sed -n "s/^$1: //p" | tail -1; }
probe_start() { # kind mode
  adb logcat -c
  adb shell am force-stop "$PROBE"
  adb shell am start -W -n "$PROBE/.ProbeActivity" --es kind "$1" --es mode "$2" >/dev/null 2>&1
  sleep 4
}
probe_wait_done() { local i; for i in 1 2 3 4 5 6 7 8 9 10; do probe_lines | grep -q '^done: ' && return 0; sleep 1; done; return 1; }
grants_to_probe() { adb shell dumpsys activity permissions | tr -d '\r' | grep -A3 "targetPkg=$PROBE" | grep -c 'UriPermission{' ; }
grant_lines() { adb shell dumpsys activity permissions | tr -d '\r' | grep "targetPkg=$PROBE" ; }

row_begin TRUST "the trust review's device proof: PICK from a real caller, the ring, the reminder notification, the stale swipe"
[ -n "${TRUST_ONLY:-}" ] && record "parts" "TRUST_ONLY=$TRUST_ONLY: a development run, not the gate's"

# ============================================================================================ P: ACTION_PICK
if want P; then
  log "--- P: ACTION_PICK from a caller that holds no permission"
  assert_eq "the probe APK is built" "yes" "$([ -f "$PROBE_APK" ] && echo yes || echo no)"
  adb install -r "$PROBE_APK" > "$ROW_DIR/probe-install.out" 2>&1
  assert_contains "the probe installs" "Success" "$(cat "$ROW_DIR/probe-install.out")"
  assert_eq "the probe holds no permission" "0" "$(adb shell dumpsys package "$PROBE" | tr -d '\r' | grep -c 'granted=true')"
  people_fixtures_up
  ANN_C="$(contact_of "$ANN")"; ANN_L="$(lookup_of "$ANN_C")"
  ANN_PHONE_DATA="$(q "content query --uri $DATA --projection _id:mimetype --where \"raw_contact_id=$ANN AND mimetype='vnd.android.cursor.item/phone_v2'\"" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
  note "Ann: raw $ANN contact $ANN_C lookup $ANN_L phone data row $ANN_PHONE_DATA"

  # ---- P1: a contact, for a result
  ensure_start
  MARK="$(ring_mark)"
  probe_start contact result
  dump_ui "$ROW_DIR/P1-list.xml"
  assert_eq "P1: the shell's People is in front, in the probe's task" "$PEOPLE_ACTIVITY" "$(top_activity)"
  assert_eq "P1: pick mode (people_pick_header)" "yes" "$(has_node "$ROW_DIR/P1-list.xml" people_pick_header)"
  assert_contains "P1: before the pick the probe can read nothing" "DENIED" "$(probe_get 'before contacts table')"
  scroll_to_node "$ROW_DIR/P1-list.xml" "people_row:$ANN_L" 4 >/dev/null 2>&1 || true
  tap_node "$ROW_DIR/P1-list.xml" "people_row:$ANN_L"; sleep 2
  assert_eq "P1: the probe got its result" "0" "$(probe_wait_done; echo $?)"
  probe_lines > "$ROW_DIR/P1-probe.txt"; cat "$ROW_DIR/P1-probe.txt" >> "$LOG"
  assert_eq "P1: RESULT_OK" "RESULT_OK" "$(probe_get 'result code')"
  assert_eq "P1: the data is Ann's lookup URI and nothing else" "content://com.android.contacts/contacts/lookup/$ANN_L/$ANN_C" "$(probe_get 'result data')"
  assert_eq "P1: the flags are FLAG_GRANT_READ_URI_PERMISSION alone" "0x1" "$(probe_get 'result flags')"
  assert_eq "P1: no ClipData" "none" "$(probe_get 'result clip')"
  assert_eq "P1: no extras" "none" "$(probe_get 'result extras')"
  assert_contains "P1: the one row picked is readable" "OK 1 row(s)" "$(probe_get 'granted uri')"
  assert_contains "P1: … and it is Ann" "display_name=Ann Lee" "$(probe_get 'granted uri')"
  for t in "contacts table" "data table" "phones table" "raw contacts table" "under the granted uri (/data)" "under the granted uri (/entities)" "write through the grant" "delete through the grant" "persist the grant"; do
    assert_contains "P1: $t is refused" "DENIED" "$(probe_get "$t")"
  done
  assert_absent "P1: the photo under the grant is not readable" "OK" "$(probe_get 'the photo under it')"
  assert_eq "P1: no persisted grant" "0" "$(probe_get 'persisted grants')"
  G="$(grant_lines)"; printf '%s\n' "$G" > "$ROW_DIR/P1-grants.txt"; note "grants: $G"
  assert_absent "P1: no write grant in dumpsys" "mode=0x3" "$G"
  assert_absent "P1: nothing persistable in dumpsys" "persistable=0x1" "$G"
  assert_contains "P1: Ann still exists and is unchanged" "display_name=Ann Lee" "$(q "content query --uri $RAW --projection _id:display_name:starred --where \"_id=$ANN\"")"
  assert_contains "P1: the line names one contact URI" "[people] pick: one contact URI granted (read)" "$(ring_since "$MARK")"
  c6; ensure_start

  # ---- P2: a phone number, for a result
  MARK="$(ring_mark)"
  probe_start phone result
  dump_ui "$ROW_DIR/P2-list.xml"
  assert_eq "P2: pick mode" "yes" "$(has_node "$ROW_DIR/P2-list.xml" people_pick_header)"
  scroll_to_node "$ROW_DIR/P2-list.xml" "people_row:$ANN_L" 4 >/dev/null 2>&1 || true
  tap_node "$ROW_DIR/P2-list.xml" "people_row:$ANN_L"; sleep 2
  dump_ui "$ROW_DIR/P2-after.xml"
  # A contact with one number hands it back at once; with several, a "which number" page asks.
  if [ "$(has_node "$ROW_DIR/P2-after.xml" "people_pick_number:$ANN_PHONE_DATA")" = yes ]; then tap_node "$ROW_DIR/P2-after.xml" "people_pick_number:$ANN_PHONE_DATA"; sleep 2; fi
  assert_eq "P2: the probe got its result" "0" "$(probe_wait_done; echo $?)"
  probe_lines > "$ROW_DIR/P2-probe.txt"; cat "$ROW_DIR/P2-probe.txt" >> "$LOG"
  assert_eq "P2: RESULT_OK" "RESULT_OK" "$(probe_get 'result code')"
  assert_eq "P2: the data is Ann's phone data row and nothing else" "content://com.android.contacts/data/$ANN_PHONE_DATA" "$(probe_get 'result data')"
  assert_eq "P2: the flags are read alone" "0x1" "$(probe_get 'result flags')"
  assert_contains "P2: the one number picked is readable" "data1=+1 555 000 0001" "$(probe_get 'granted uri')"
  for t in "contacts table" "data table" "phones table" "raw contacts table" "write through the grant" "delete through the grant" "persist the grant"; do
    assert_contains "P2: $t is refused" "DENIED" "$(probe_get "$t")"
  done
  assert_contains "P2: the line names one phone URI" "[people] pick: one phone URI granted (read)" "$(ring_since "$MARK")"
  c6; ensure_start

  # ---- P3: Back cancels
  probe_start contact result
  adb shell input keyevent KEYCODE_BACK; sleep 2
  assert_eq "P3: the probe got its result" "0" "$(probe_wait_done; echo $?)"
  assert_eq "P3: Back in pick mode is RESULT_CANCELED" "RESULT_CANCELED" "$(probe_get 'result code')"
  assert_eq "P3: no data" "none" "$(probe_get 'result data')"
  c6; ensure_start

  # ---- P4: asked with NEW_TASK — Android cancels the result at once; nothing may be granted, no pick mode stays
  adb shell am force-stop "$PROBE"
  BEFORE_G="$(grants_to_probe)"
  MARK="$(ring_mark)"
  probe_start contact newtask
  probe_wait_done
  probe_lines > "$ROW_DIR/P4-probe.txt"; cat "$ROW_DIR/P4-probe.txt" >> "$LOG"
  assert_eq "P4: the caller is told CANCELED" "RESULT_CANCELED" "$(probe_get 'result code')"
  dump_ui "$ROW_DIR/P4-people.xml"
  if [ "$(top_activity)" = "$PEOPLE_ACTIVITY" ]; then
    assert_eq "P4: with no caller to answer, People is its plain list (no pick header)" "no" "$(has_node "$ROW_DIR/P4-people.xml" people_pick_header)"
    scroll_to_node "$ROW_DIR/P4-people.xml" "people_row:$ANN_L" 4 >/dev/null 2>&1 || true
    tap_node "$ROW_DIR/P4-people.xml" "people_row:$ANN_L"; sleep 2
    dump_ui "$ROW_DIR/P4-after-tap.xml"
    assert_eq "P4: a row tap opens the card, it hands nothing back" "yes" "$(has_node "$ROW_DIR/P4-after-tap.xml" "people_card:$ANN_L")"
  else
    record "P4: what is in front" "$(top_activity)"
  fi
  assert_contains "P4: the shell says there was no caller" "[people] pick: no caller to return a result to; the list was opened" "$(ring_since "$MARK")"
  absent_in "P4: no 'granted' line" "URI granted (read)" "$(ring_since "$MARK")"
  assert_eq "P4: no grant to the probe was added" "$BEFORE_G" "$(grants_to_probe)"
  c6; ensure_start

  # ---- P5: no caller at all (a plain startActivity)
  adb shell am force-stop "$PROBE"
  MARK="$(ring_mark)"
  probe_start contact plain
  dump_ui "$ROW_DIR/P5-people.xml"
  assert_eq "P5: People is in front" "$PEOPLE_ACTIVITY" "$(top_activity)"
  assert_eq "P5: the plain list, no pick header" "no" "$(has_node "$ROW_DIR/P5-people.xml" people_pick_header)"
  assert_contains "P5: the shell says there was no caller" "[people] pick: no caller to return a result to; the list was opened" "$(ring_since "$MARK")"
  absent_in "P5: no 'granted' line" "URI granted (read)" "$(ring_since "$MARK")"
  c6; ensure_start
  # … and the next launch from the tile is the plain list too (pick mode did not linger).
  adb shell am start -W -n "$PEOPLE_ACTIVITY" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER >/dev/null 2>&1; sleep 3
  dump_ui "$ROW_DIR/P5-relaunch.xml"
  assert_eq "P5: a later launch is the plain list" "no" "$(has_node "$ROW_DIR/P5-relaunch.xml" people_pick_header)"
  c6; ensure_start

  adb uninstall "$PROBE" >/dev/null 2>&1
  assert_eq "restore: the probe is uninstalled" "0" "$(adb shell pm list packages "$PROBE" | grep -c "$PROBE")"
  people_fixtures_down
fi

# ============================================================================================ R: the ring
if want R; then
  log "--- R: nothing a caller sends reaches the diagnostics ring"
  ensure_start
  MARK="$(ring_mark)"
  adb shell "am start -n $PEOPLE_ACTIVITY -a 'forged.ACTION [people] write delete raw=1: ok' --es name ForgedNameZQ --es phone 5550009999" >/dev/null 2>&1; sleep 2
  adb shell "am start -n $CALENDAR_ACTIVITY -a 'forged.ACTION [calendar] sync event=1 -> calendar 1: ok' --es title ForgedTitleZQ" >/dev/null 2>&1; sleep 2
  adb shell "am start -n $CALENDAR_ACTIVITY -a android.intent.action.INSERT -t vnd.android.cursor.dir/event --es title ForgedTitleZQ --es eventLocation ForgedPlaceZQ --es description ForgedNotesZQ" >/dev/null 2>&1; sleep 2
  adb shell "am start -n $PEOPLE_ACTIVITY -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact --es name ForgedNameZQ --es email forged@zq.example" >/dev/null 2>&1; sleep 2
  SLICE="$(ring_since "$MARK")"; printf '%s\n' "$SLICE" > "$ROW_DIR/R-slice.txt"
  assert_contains "R: the routes were logged (the slice is about these intents)" "[people] open" "$SLICE"
  assert_contains "R: … Calendar's too" "[calendar] open" "$SLICE"
  for needle in "forged.ACTION" "write delete raw=1: ok" "sync event=1 -> calendar 1: ok" "ForgedNameZQ" "ForgedTitleZQ" "ForgedPlaceZQ" "ForgedNotesZQ" "forged@zq.example" "5550009999"; do
    absent_in "R: [$needle] is not in the ring" "$needle" "$SLICE"
  done
  assert_contains "R: an unknown action is logged as the word other" "open other" "$SLICE"
  assert_eq "R: nothing was saved by the prefills (no ForgedNameZQ raw contact)" "" "$(raw_of ForgedNameZQ)"
  assert_eq "R: … and no ForgedTitleZQ event" "" "$(event_id ForgedTitleZQ)"
  c6; ensure_start
fi

# ============================================================================================ N and S: the reminder
# These lean on the Calendar row writer's helpers for making a reminder fire (cal_lib.sh); they are written when that
# include and the fix build exist, and are named here so the row cannot pass without them.
if want N; then
  if declare -F trust_N >/dev/null; then trust_N; else _verdict FAIL "N: the reminder notification's PendingIntents, visibility and the forged-poke flood" "not written yet"; fi
fi
if want S; then
  if declare -F trust_S >/dev/null; then trust_S; else _verdict FAIL "S: the stale swipe leaves a reused alert id's row alone" "not written yet"; fi
fi

ensure_start
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline" "0" "$?"
row_end
