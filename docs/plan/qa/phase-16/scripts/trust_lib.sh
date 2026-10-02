#!/usr/bin/env bash
# The reminder parts of the TRUST row (scripts/trust.sh sources this after lib.sh, p16.sh and cal_lib.sh):
#   trust_N  the reminder notification — PRIVATE with a public version, immutable and explicit PendingIntents — and a
#            flood of forged pokes that posts nothing, marks nothing and writes nothing
#   trust_S  the swipe on a STALE reminder whose alert row id the provider has handed to another event's alert leaves
#            that other alert alone (trust review B-F2 / A-F12; ledger F3)
# wait_note and swipe_away are the Calendar row writer's (scripts/e6.sh), copied because that row's file cannot be sourced.

trust_wait_note() { # title seconds -> the seconds it took, or "none"
  local i
  for i in $(seq 1 "$2"); do
    if cnotes | grep -qF "title=[$1]"; then echo "$i"; return 0; fi
    sleep 1
  done
  echo none
}
trust_swipe_away() { # title out.xml
  adb shell cmd statusbar expand-notifications; sleep 2
  adb shell uiautomator dump /sdcard/qa-shade.xml > /dev/null 2>&1; adb shell cat /sdcard/qa-shade.xml > "$2"
  local b
  b="$(python3 - "$2" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
m = re.search(r'text="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(sys.argv[2]), xml)
print(" ".join(m.groups()) if m else "")
PY
)"
  note "the notification's title node in the shade: [$b]"
  if [ -n "$b" ]; then
    # shellcheck disable=SC2086
    set -- $b
    adb shell input swipe $(( $1 + 20 )) $(( ($2 + $4) / 2 )) 1050 $(( ($2 + $4) / 2 )) 200
  fi
  sleep 2
  adb shell cmd statusbar collapse; sleep 1
  [ -n "$b" ]
}
trust_alert() { # event-id field(_id|state) -> that field of the event's first alert row
  q "content query --uri content://com.android.calendar/calendar_alerts --projection _id:event_id:state:alarmTime --where \"event_id=$1\"" \
    | sed -n "s/.*[ ,]$2=\([0-9]*\).*/\1/p" | head -1
}
trust_shell_pid() { adb shell pidof app.tileshell | tr -d '\r' | awk '{print $1}'; }
trust_store_stamp() { adb shell "run-as app.tileshell stat -c '%Y %s' files/calendar_sync.json" < /dev/null 2>/dev/null | tr -d '\r'; }
# A Tessera event 30 minutes ahead with a 10-minute reminder, and the clock moved to 5 s before its alert. Sets EV, T10.
trust_due_event() { # title
  local now start
  now="$(device_ms)"; start=$(( (now / 60000 + 30) * 60000 )); T10=$(( start - 600000 ))
  EV="$(cmkevent "$TESS" "$1" "$start" $(( start + 3600000 )))"
  q "content insert --uri content://com.android.calendar/reminders --bind event_id:i:$EV --bind minutes:i:10 --bind method:i:1" > /dev/null
  sleep 2.5
  jump_clock $(( T10 - 5000 )) > /dev/null
}

trust_N() {
  log "--- N: the reminder notification, and a flood of forged pokes"
  local aosp=com.android.calendar
  adb shell pm disable-user --user 0 "$aosp" > /dev/null 2>&1   # only the shell can post or mark here
  c6; ensure_start
  copen; TESS="$(tessera_id)"; adb shell input keyevent KEYCODE_HOME; sleep 1
  assert_ne "N: Tessera exists" "" "$TESS"
  cpurge "title IN ('Trust N')"
  trust_due_event "Trust N"
  assert_ne "N: the test event exists" "" "$EV"
  local mark w notes
  mark="$(ring_mark)"
  w="$(trust_wait_note "Trust N" 30)"
  notes="$(cnotes)"; note "the shell's calendar notifications: $(echo "$notes" | tr '\n' ';') (after $w s)"
  assert_ne "N: the reminder notified" "none" "$w"
  assert_contains "N: VISIBILITY_PRIVATE" "vis=PRIVATE" "$notes"
  adb shell dumpsys notification --noredact < /dev/null | tr -d '\r' > "$ROW_DIR/N-notification.txt"
  # The record's own block: its public version exists and does not carry the event's title.
  python3 - "$ROW_DIR/N-notification.txt" > "$ROW_DIR/N-record.txt" <<'PY'
import re, sys
text = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for block in re.split(r"(?=\n\s*NotificationRecord\()", text):
    if "pkg=app.tileshell" in block and "calendar_reminders" in block:
        print(block)
PY
  assert_contains "N: the record has a public version" "publicVersion=Notification" "$(grep -o 'publicVersion=[A-Za-z]*' "$ROW_DIR/N-record.txt" | head -1)"
  record "N: the public version as dumpsys prints it" "$(grep -m1 'publicVersion=' "$ROW_DIR/N-record.txt" | sed 's/^ *//' | cut -c1-200)"
  # What the public version SAYS (gate review B, note 2: its existence alone was asserted): the block dumpsys prints
  # under "publicNotification=" holds the generic title and nothing of the event's.
  sed -n '/publicNotification=/,$p' "$ROW_DIR/N-record.txt" > "$ROW_DIR/N-public.txt"
  assert_contains "N: the public version's title is the generic one" "android.title=String (Calendar reminder)" "$(cat "$ROW_DIR/N-public.txt")"
  assert_absent "N: … and it holds nothing of the event's title" "Trust N" "$(cat "$ROW_DIR/N-public.txt")"
  assert_contains "N: (the private version does hold the title: the read can tell them apart)" "Trust N" "$(sed '/publicNotification=/,$d' "$ROW_DIR/N-record.txt")"
  # The PendingIntents the shell holds for its calendar parts: each immutable (0x04000000) and naming its component.
  adb shell dumpsys activity intents < /dev/null | tr -d '\r' > "$ROW_DIR/N-intents.txt"
  python3 - "$ROW_DIR/N-intents.txt" > "$ROW_DIR/N-pending.txt" <<'PY'
import re, sys
text = open(sys.argv[1], encoding='utf-8', errors='replace').read()
n = bad = 0
# dumpsys lists a package's records as "#0: PendingIntentRecord{…}" under "* <package>: n items" (run 1 split on "* ").
for block in re.split(r"(?=\n\s*#\d+: PendingIntentRecord\{)", text):
    # The record's OWN package line (run 3 took the calendar provider's last record: its block ends with the next
    # package's "* app.tileshell: n items" header, and its component holds ".calendar.").
    block = re.split(r"\n\s*\* \S+: \d+ items", block)[0]
    if not re.search(r"packageName=app\.tileshell\b", block): continue
    req = re.search(r"requestIntent=(.*)", block)
    if not req or ".calendar." not in req.group(1): continue
    flags = re.search(r"flags=0x([0-9a-fA-F]+)", block)
    f = int(flags.group(1), 16) if flags else 0
    immutable = bool(f & 0x04000000)
    explicit = "cmp=app.tileshell/" in req.group(1)
    n += 1
    if not (immutable and explicit): bad += 1
    print("pending flags=0x%x immutable=%s explicit=%s %s" % (f, immutable, explicit, req.group(1)[:140]))
print("TOTAL %d BAD %d" % (n, bad))
PY
  cat "$ROW_DIR/N-pending.txt" >> "$LOG"
  assert_ne "N: the shell holds PendingIntents for the reminder (the read found them)" "TOTAL 0 BAD 0" "$(tail -1 "$ROW_DIR/N-pending.txt")"
  assert_contains "N: every one is immutable and names its component" "BAD 0" "$(tail -1 "$ROW_DIR/N-pending.txt")"
  # Read from the BUILD (gate review B, note 12: the allow-list file was read): exported.py lists the APK's exported
  # components; the row's header has tied that APK file to the one installed (apk match yes).
  python3 "$REPO/docs/plan/qa/phase-03/scripts/exported.py" "$APK" "$REPO/docs/plan/qa/phase-03/exported-allowlist.txt" > "$ROW_DIR/N-exported.txt" 2>&1; echo $? > "$ROW_DIR/N-exported.rc"
  assert_eq "N: exported.py passes on this APK" "0" "$(cat "$ROW_DIR/N-exported.rc")"
  assert_contains "N: (the read lists receivers: the reminder receiver, which is exported, is in it)" "app.tileshell.calendar.CalendarReminderReceiver" "$(cat "$ROW_DIR/N-exported.txt")"
  assert_absent "N: the swipe's receiver is not among the APK's exported components" "CalendarReminderDismissReceiver" "$(cat "$ROW_DIR/N-exported.txt")"

  # The flood: 100 forged pokes with nothing new due.
  local pid0 stamp0 state0 count0 t0 t1 slice crash0
  crash0="$(ccrashes)"
  pid0="$(trust_shell_pid)"; stamp0="$(trust_store_stamp)"; state0="$(trust_alert "$EV" state)"; count0="$(cnotes | grep -c 'title=')"
  mark="$(ring_mark)"; t0="$(date +%s)"
  # Each broadcast's own answer is kept: "Broadcast completed" is the activity manager saying the receiver ran
  # (gate review B, note 12: nothing showed the pokes were delivered).
  adb shell 'i=0; while [ $i -lt 100 ]; do am broadcast -a android.intent.action.EVENT_REMINDER -d content://com.android.calendar/1 -n app.tileshell/.calendar.CalendarReminderReceiver 2>&1; i=$((i+1)); done' < /dev/null | tr -d '\r' > "$ROW_DIR/N-flood-broadcasts.txt"
  t1="$(date +%s)"; sleep 3
  assert_eq "N: all 100 forged pokes were delivered (Broadcast completed)" "100" "$(grep -c 'Broadcast completed' "$ROW_DIR/N-flood-broadcasts.txt")"
  slice="$(ring_since "$mark")"; printf '%s\n' "$slice" > "$ROW_DIR/N-flood-slice.txt"
  record "N: 100 forged pokes took" "$(( t1 - t0 )) s; ring lines since: $(printf '%s\n' "$slice" | grep -c 'wall=')"
  assert_eq "N: the flood posts no new notification" "$count0" "$(cnotes | grep -c 'title=')"
  assert_eq "N: … logs no notified line" "0" "$(printf '%s\n' "$slice" | grep -cF ': notified')"
  assert_eq "N: … leaves the alert's state as it was" "$state0" "$(trust_alert "$EV" state)"
  assert_eq "N: … writes nothing to the shell's store (same stamp and size)" "$stamp0" "$(trust_store_stamp)"
  assert_eq "N: … and the shell's process is the same one (no crash)" "$pid0" "$(trust_shell_pid)"
  assert_eq "N: no new crash of the shell in the dropbox" "$crash0" "$(ccrashes)"

  cpurge "title IN ('Trust N')"
  adb shell pm enable "$aosp" > /dev/null 2>&1
  clock_restore
  assert_eq "N restore: no calendar notification of the shell's is left" "" "$(cnotes)"
  ensure_start
}

trust_S() {
  log "--- S: a stale reminder's swipe and a reused alert row id"
  local aosp=com.android.calendar
  adb shell pm disable-user --user 0 "$aosp" > /dev/null 2>&1
  c6; ensure_start
  copen; TESS="$(tessera_id)"; adb shell input keyevent KEYCODE_HOME; sleep 1
  cpurge "title IN ('Trust A','Trust B')"
  assert_eq "S: no alert row before the leg (so the ids below are this leg's)" "" "$(q "content query --uri content://com.android.calendar/calendar_alerts --projection _id" | grep '_id=' || true)"
  trust_due_event "Trust A"
  local a="$EV" w a_alert b b_alert b_state now start mark slice
  w="$(trust_wait_note "Trust A" 30)"
  assert_ne "S: A's reminder notified" "none" "$w"
  a_alert="$(trust_alert "$a" _id)"
  assert_ne "S: A's alert row id" "" "$a_alert"
  # A goes (its alert row with it); its notification stays up, stale.
  cpurge "title IN ('Trust A')"; sleep 2
  assert_eq "S: A's alert row is gone with the event" "" "$(trust_alert "$a" _id)"
  assert_eq "S: A's notification is still up (stale)" "1" "$(cnotes | grep -cF 'title=[Trust A]')"
  # B: an event whose alert is still in the future; the provider hands its alert row the id A's had.
  now="$(device_ms)"; start=$(( (now / 60000 + 180) * 60000 ))
  b="$(cmkevent "$TESS" "Trust B" "$start" $(( start + 3600000 )))"
  q "content insert --uri content://com.android.calendar/reminders --bind event_id:i:$b --bind minutes:i:10 --bind method:i:1" > /dev/null
  # The provider writes the alert row a few seconds after the reminder (run 1 read the id before the row was there).
  local i
  for i in $(seq 1 20); do b_alert="$(trust_alert "$b" _id)"; [ -n "$b_alert" ] && break; sleep 1; done
  b_state="$(trust_alert "$b" state)"
  note "A's alert row id was $a_alert; B's is ${b_alert:-none} (state ${b_state:-none})"
  assert_eq "S: precondition — B's alert row reuses A's id" "$a_alert" "$b_alert"
  assert_eq "S: precondition — B's alert is SCHEDULED (0)" "0" "$b_state"
  mark="$(ring_mark)"
  trust_swipe_away "Trust A" "$ROW_DIR/S-shade.xml"; assert_eq "S: A's stale notification was in the shade and swiped" "0" "$?"
  sleep 2
  slice="$(ring_since "$mark")"; printf '%s\n' "$slice" > "$ROW_DIR/S-slice.txt"
  assert_eq "S: A's notification is gone" "0" "$(cnotes | grep -cF 'title=[Trust A]')"
  assert_eq "S: B's alert row is STILL SCHEDULED (the swipe did not dismiss another event's alert)" "0" "$(trust_alert "$b" state)"
  assert_contains "S: the shell says the swipe matched no alert" "swipe ignored (no such alert now)" "$slice"
  absent_in "S: no dismissed line for B" "reminder event=$b minutes=10: dismissed" "$slice"
  cpurge "title IN ('Trust A','Trust B')"
  adb shell pm enable "$aosp" > /dev/null 2>&1
  clock_restore
  assert_eq "S restore: no calendar notification of the shell's is left" "" "$(cnotes)"
  ensure_start
}
