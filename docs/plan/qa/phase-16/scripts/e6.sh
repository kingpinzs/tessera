#!/usr/bin/env bash
# Phase 16 E6 — reminders through the provider (Rule 16: no shell alarm; r3 D6 / V7; Q-16-4's leg (e)).
#
#   (a) the shell alone   the AOSP Calendar disabled; shell_alarms_pending proven able to count (one of the shell's own
#                         alarms armed through the AlarmClock API, counted, deleted); a local "Standup" 30 min ahead with
#                         a 10-minute reminder; the provider's alarm in dumpsys alarm, the shell's pending count
#                         unchanged; jump_clock to 5 s before T−10 min → ONE notification of the shell's (title, the
#                         event's time, the calendar channel, PRIVATE), `notified` once, the alert row FIRED; the swipe →
#                         DISMISSED and the `dismissed` line
#   (b) the race          the AOSP Calendar enabled; a second event → still exactly one notification of the shell's and
#                         `notified` once; the AOSP Calendar's own notification RECORDed
#   (c) a forged poke     the broadcast named in the row, with no alert due → no new notification, no `notified` line
#   (d) reboot            a reminder 3 min ahead, adb reboot, the boot poll, wake_device `Awake` → the provider re-armed
#                         it and it notifies at its time
#   (e) due before the shell's first start (Q-16-4, added 2026-10-01): A's alert comes due while no shell runs; after
#                         provision.sh the shell reminds for B only and logs `1 skipped (due before the shell's first
#                         start)`. (Build 686506a7 did not have the rule; it is in the fix build, 3c1ad1e0.)
#   restore               the AOSP Calendar enabled, the events and their reminders deleted, clock_restore, no notification
#                         left on the calendar channel, the baseline layout (leg (e) cleared the shell)
#
# Every clock move is jump_clock FORWARDS. One exception is forced by the emulator, not chosen: `adb reboot` puts the
# device's clock back on the host's (the guest RTC), which is BEHIND the clock legs (a) and (b) jumped. So before leg
# (d) the driver restores the clock itself with clock_restore (ring_save first; its force-stop and Home are the
# preamble's form for a backwards step) and leg (d)'s MARK is taken after the boot (clauses-open.tsv).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"
CLOCK_ACT="app.tileshell/.clock.ClockActivity"
API_ACT="app.tileshell/.clock.AlarmApiActivity"

row_begin E6 "reminders through the provider: the shell alone, the race, a forged poke, reboot, due before the first start"
# Pending alarms that name a package (the "N pending alarms:" block only), one line each.
pending_of() { adb shell dumpsys alarm < /dev/null | tr -d '\r' | python3 -c '
import re, sys
inside = False
for l in sys.stdin.read().splitlines():
    if re.match(r"^\s*\d+ pending alarms:", l): inside = True; continue
    if inside and not l.startswith("    "): inside = False
    if inside and re.search(r"Alarm\{[^}]*\b%s\}" % re.escape(sys.argv[1]), l): print(l.strip())' "$1"; }
enabled() { adb shell pm list packages -e "$1" < /dev/null | tr -d '\r' | grep -c "^package:$1$"; }
# The event's own time as the notification words it ("Thu 1 Oct, 3:44 PM – 4:44 PM"), from the host's zone database.
time_text() { # start-ms end-ms
  local z; z="$(ctz)"
  printf '%s – %s' "$(TZ="$z" date -d "@$(( $1 / 1000 ))" '+%a %-d %b, %-I:%M %p')" "$(TZ="$z" date -d "@$(( $2 / 1000 ))" '+%-I:%M %p')"
}
# Wait up to N s for the shell to post a calendar notification with a title; prints the seconds it took, or "none".
wait_note() { # title seconds
  local i
  for i in $(seq 1 "$2"); do
    if cnotes | grep -qF "title=[$1]"; then echo "$i"; return 0; fi
    sleep 1
  done
  echo none
}
# The shade, a swipe across the notification's title node at its dump bounds, the shade closed.
swipe_away() { # title out.xml
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
AOSP_WAS="$(enabled "$AOSP_CAL")"
CRASH0="$(ccrashes)"
MADE_TITLES="'Standup','Standup two','Standup three','Event A','Event B'"

# =============================================================================================== (a) the shell alone
log "--- (a) the shell alone"
adb shell pm disable-user --user 0 "$AOSP_CAL" > /dev/null 2>&1
assert_eq "(a) the AOSP Calendar is disabled (only the shell can mark the alert)" "0" "$(enabled "$AOSP_CAL")"
c6; ensure_start
copen; TESS="$(tessera_id)"; adb shell input keyevent KEYCODE_HOME; sleep 1
assert_ne "(a) precondition: Tessera exists" "" "$TESS"
cpurge "title IN ($MADE_TITLES)"
assert_eq "(a) precondition: no calendar notification of the shell's is up" "" "$(cnotes)"

# shell_alarms_pending can count: one of the shell's own alarms armed (the AlarmClock API), read, deleted through the app.
P_BASE="$(shell_alarms_pending)"
HM="$(q "date -d @\$(( \$(date +%s) + 7200 )) '+%-H %-M'")"
K_MARK="$(ring_mark)"
adb shell "am start -W -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR ${HM% *} --ei android.intent.extra.alarm.MINUTES ${HM#* } --es android.intent.extra.alarm.MESSAGE 'E6 probe' --ez android.intent.extra.alarm.SKIP_UI true -n $API_ACT" < /dev/null >/dev/null 2>&1
sleep 1.5
PROBE_ID="$(ring_since "$K_MARK" | grep -oE 'api android.intent.action.SET_ALARM from \S+ -> created [a-z0-9]+' | tail -1 | awk '{print $NF}')"
P_ARMED="$(shell_alarms_pending)"
note "shell_alarms_pending: $P_BASE before, $P_ARMED with the shell's own alarm $PROBE_ID armed for ${HM% *}:${HM#* }"
assert_eq "(a) shell_alarms_pending can count: it reads one more with one of the shell's own alarms armed" "$(( P_BASE + 1 ))" "$P_ARMED"
adb shell am start -W -n "$CLOCK_ACT" --es page alarm >/dev/null 2>&1; sleep 1.5
if scroll_to_node "$ROW_DIR/a-clock.xml" "alarm_row:$PROBE_ID" 6; then
  tap_node "$ROW_DIR/a-clock.xml" "alarm_row:$PROBE_ID"; sleep 1.2
  dump_ui "$ROW_DIR/a-clock-edit.xml"; tap_node "$ROW_DIR/a-clock-edit.xml" clock_bar:delete; sleep 1.2
fi
c6; ensure_start
PENDING0="$(shell_alarms_pending)"
assert_eq "(a) … and the same as before once that alarm is deleted (the read before the reminder insert)" "$P_BASE" "$PENDING0"

NOW="$(device_ms)"; START=$(( (NOW / 60000 + 30) * 60000 )); T10=$(( START - 600000 ))
pending_of "$CAL_PROVIDER" > "$ROW_DIR/a-provider-alarms-before.txt"
EV="$(cmkevent "$TESS" Standup "$START" $(( START + 3600000 )))"
q "content insert --uri $REMINDERS --bind event_id:i:$EV --bind minutes:i:10 --bind method:i:1" > /dev/null
sleep 2.5
assert_ne "(a) a local event \"Standup\" 30 min ahead, with its reminders row (minutes 10, method 1)" "" "$EV"
assert_contains "(a) … the reminders row as inserted" "event_id=$EV, minutes=10, method=1" "$(q "content query --uri $REMINDERS --projection event_id:minutes:method --where \"event_id=$EV\"")"
note "event $EV starts $START; T−10 min is $T10; alert rows now: $(calerts "$EV")"
pending_of "$CAL_PROVIDER" > "$ROW_DIR/a-provider-alarms-after.txt"
log "dumpsys alarm, pending under $CAL_PROVIDER: $(cat "$ROW_DIR/a-provider-alarms-after.txt" | cut -c1-200 | tr '\n' ';')"
# Tied to THIS alert (gate review B, note 11): the provider's alarm for it is an RTC alarm at the alert's own time,
# T − 10 min; any other pending alarm of the provider (its own housekeeping) does not count.
assert_eq "(a) dumpsys alarm shows the provider's alarm for this alert under com.android.providers.calendar: an RTC alarm whose origWhen is T − 10 min ($T10)" "1" "$(grep -c "type 0 origWhen $T10 " "$ROW_DIR/a-provider-alarms-after.txt")"
assert_eq "(a) … and no such alarm was pending before the reminder's insert" "0" "$(grep -c "type 0 origWhen $T10 " "$ROW_DIR/a-provider-alarms-before.txt")"
assert_eq "(a) shell_alarms_pending reads the SAME count as before the insert (Rule 16)" "$PENDING0" "$(shell_alarms_pending)"
assert_eq "(a) no calendar notification of the shell's before the time" "" "$(cnotes)"

jump_clock $(( T10 - 5000 )) > "$ROW_DIR/a-jump.txt"
A_MARK="$(ring_mark)"
W="$(wait_note Standup 12)"; sleep 3
NOTES="$(cnotes)"; log "the shell's calendar notifications: $(echo "$NOTES" | tr '\n' ';') (after $W s)"
A_SLICE="$(csince "$A_MARK")"; printf '%s\n' "$A_SLICE" > "$ROW_DIR/a-slice.txt"
assert_ne "(a) a notification of the shell's shows after the jump" "none" "$W"
assert_within "(a) … within 10 s" 5 "$W" 5
assert_eq "(a) dumpsys notification --noredact shows ONE notification of the shell's" "1" "$(printf '%s\n' "$NOTES" | grep -c 'title=')"
assert_contains "(a) … its title \"Standup\"" "title=[Standup]" "$NOTES"
assert_contains "(a) … the time (the event's own)" "text=[$(time_text "$START" $(( START + 3600000 )))]" "$NOTES"
assert_contains "(a) … on the calendar channel" "channel=calendar_reminders" "$NOTES"
assert_contains "(a) … visibility PRIVATE" "vis=PRIVATE" "$NOTES"
assert_eq "(a) the slice holds [calendar] reminder event=<id> minutes=10: notified — once" "1" "$(clines "$A_SLICE" "[calendar] reminder event=$EV minutes=10: notified")"
ST="$(q "content query --uri $ALERTS --projection event_id:state --where \"event_id=$EV\"")"; log "calendar_alerts: $ST"
assert_contains "(a) calendar_alerts shows state 1 (FIRED) — only the shell can have written it" "event_id=$EV, state=1" "$ST"
assert_eq "(a) … one alert row" "1" "$(printf '%s\n' "$ST" | grep -c 'event_id=')"
assert_eq "(a) still no alarm of the shell's own after the reminder fired" "$PENDING0" "$(shell_alarms_pending)"
A3_MARK="$(ring_mark)"
swipe_away Standup "$ROW_DIR/a-shade.xml"; assert_eq "(a) the notification's node is in the shade (the swipe is at its dump bounds)" "0" "$?"
sleep 1
assert_eq "(a) dismissing it removes it" "" "$(cnotes)"
assert_contains "(a) … → state 2 (DISMISSED)" "event_id=$EV, state=2" "$(q "content query --uri $ALERTS --projection event_id:state --where \"event_id=$EV\"")"
assert_contains "(a) … and the line reminder event=<id> minutes=10: dismissed" "[calendar] reminder event=$EV minutes=10: dismissed" "$(csince "$A3_MARK")"

# =============================================================================================== (b) the race and the double
log "--- (b) the race and the double (the AOSP Calendar enabled)"
adb shell pm enable "$AOSP_CAL" > /dev/null 2>&1
assert_eq "(b) pm enable com.android.calendar" "1" "$(enabled "$AOSP_CAL")"
sleep 2
NOW="$(device_ms)"; START2=$(( (NOW / 60000 + 30) * 60000 ))
EV2="$(cmkevent "$TESS" 'Standup two' "$START2" $(( START2 + 3600000 )))"
cmkreminder "$EV2" 10; sleep 2.5
assert_ne "(b) a second event and reminder" "" "$EV2"
jump_clock $(( START2 - 600000 - 5000 )) > "$ROW_DIR/b-jump.txt"
B_MARK="$(ring_mark)"
W="$(wait_note 'Standup two' 12)"; sleep 5
NOTES="$(cnotes)"; log "the shell's calendar notifications: $(echo "$NOTES" | tr '\n' ';') (after $W s)"
B_SLICE="$(csince "$B_MARK")"; printf '%s\n' "$B_SLICE" > "$ROW_DIR/b-slice.txt"
assert_eq "(b) the shell still posts exactly one notification for it" "1" "$(printf '%s\n' "$NOTES" | grep -cF 'title=[Standup two]')"
assert_eq "(b) … and no other notification of the shell's" "1" "$(printf '%s\n' "$NOTES" | grep -c 'title=')"
assert_eq "(b) … and logs notified once, whichever app flipped the alert row first" "1" "$(clines "$B_SLICE" "[calendar] reminder event=$EV2 minutes=10: notified")"
AOSP_NOTES="$(cnotes_of "$AOSP_CAL" | tr '\n' ';')"
record "(b) the AOSP Calendar fixture's own notification is ALSO present (Decisions; C-26)" "${AOSP_NOTES:-none}"
record "(b) the alert row after both apps handled it" "$(calerts "$EV2")"
sleep 4; adb shell cmd statusbar collapse >/dev/null 2>&1

# =============================================================================================== (c) a forged poke
log "--- (c) a forged poke with no alert due"
N_BEFORE="$(cnotes | grep -c 'title=')"
C_MARK="$(ring_mark)"
adb shell am broadcast -a android.intent.action.EVENT_REMINDER -d content://com.android.calendar/1 -n app.tileshell/.calendar.CalendarReminderReceiver > "$ROW_DIR/c-forged.txt" 2>&1
sleep 3
copen; adb shell input keyevent KEYCODE_HOME; sleep 1   # a line of the Calendar's own, so the slice is readable
C_SLICE="$(csince "$C_MARK")"; printf '%s\n' "$C_SLICE" > "$ROW_DIR/c-slice.txt"
assert_contains "(c) the broadcast reached the receiver" "Broadcast completed" "$(cat "$ROW_DIR/c-forged.txt")"
assert_eq "(c) no new notification (the shell's count is as before the poke)" "$N_BEFORE" "$(cnotes | grep -c 'title=')"
absent_in "(c) no notified line" ": notified" "$C_SLICE"

# =============================================================================================== (d) reboot
log "--- (d) reboot with a reminder 3 min ahead"
ring_save
cpurge "title IN ('Standup','Standup two')"
record "(d) the device clock before the driver's restore (jumped by legs (a) and (b))" "$(q "date '+%Y-%m-%d %H:%M:%S'") against the host's $(date '+%Y-%m-%d %H:%M:%S')"
clock_restore
ensure_start
NOW="$(device_ms)"; START3=$(( (NOW / 60000 + 14) * 60000 )); ALARM3=$(( START3 - 600000 ))
EV3="$(cmkevent "$TESS" 'Standup three' "$START3" $(( START3 + 3600000 )))"
cmkreminder "$EV3" 10; sleep 2.5
assert_ne "(d) an event whose reminder is about 3 min ahead" "" "$EV3"
note "event $EV3: the alert is due at $ALARM3, $(( (ALARM3 - NOW) / 1000 )) s from the insert; alert rows: $(calerts "$EV3")"
pending_of "$CAL_PROVIDER" > "$ROW_DIR/d-provider-alarms-before-reboot.txt"
ring_save
adb reboot; cboot_poll
assert_eq "(d) the device is awake after the reboot (wake_device; C-25)" "Awake" "$(wake_device)"
ensure_start
record "(d) the device clock after the reboot" "$(q "date '+%Y-%m-%d %H:%M:%S'") against the host's $(date '+%Y-%m-%d %H:%M:%S')"
ROW_MARK_D="$(ring_mark)"
pending_of "$CAL_PROVIDER" > "$ROW_DIR/d-provider-alarms-after-reboot.txt"
log "dumpsys alarm after boot, pending under $CAL_PROVIDER: $(cut -c1-200 "$ROW_DIR/d-provider-alarms-after-reboot.txt" | tr '\n' ';')"
LEFT=$(( (ALARM3 - $(device_ms)) / 1000 ))
assert_eq "(d) the boot finished before the reminder's time (the alert is still ahead)" "yes" "$([ "$LEFT" -gt 0 ] && echo yes || echo "no ($LEFT s)")"
assert_eq "(d) after boot the provider re-armed it (dumpsys alarm: an RTC alarm under com.android.providers.calendar whose origWhen is this alert's time, $ALARM3)" "1" "$(grep -c "type 0 origWhen $ALARM3 " "$ROW_DIR/d-provider-alarms-after-reboot.txt")"
assert_eq "(d) no notification for it before its time" "0" "$(cnotes | grep -cF 'title=[Standup three]')"
[ "$LEFT" -gt 0 ] && sleep "$LEFT"
W="$(wait_note 'Standup three' 40)"; sleep 3
NOTES="$(cnotes)"; log "the shell's calendar notifications: $(echo "$NOTES" | tr '\n' ';') ($W s after its time)"
D_SLICE="$(csince "$ROW_MARK_D")"; printf '%s\n' "$D_SLICE" > "$ROW_DIR/d-slice.txt"
assert_eq "(d) it notifies at its time: one notification of the shell's titled \"Standup three\"" "1" "$(printf '%s\n' "$NOTES" | grep -cF 'title=[Standup three]')"
assert_ne "(d) … within 40 s of the alert's time" "none" "$W"
assert_eq "(d) … notified once" "1" "$(clines "$D_SLICE" "[calendar] reminder event=$EV3 minutes=10: notified")"
sleep 4; adb shell cmd statusbar collapse >/dev/null 2>&1

# =============================================================================================== (e) due before the first start
log "--- (e) due before the shell's first start (Q-16-4)"
adb shell pm disable-user --user 0 "$AOSP_CAL" > /dev/null 2>&1
assert_eq "(e) the AOSP Calendar is disabled" "0" "$(enabled "$AOSP_CAL")"
ring_save
cpurge "title IN ($MADE_TITLES)"; sleep 2
ALL_ALERTS="$(q "content query --uri $ALERTS --projection _id:event_id:state")"
assert_eq "(e) the earlier legs' events are deleted and calendar_alerts holds no row (so the line's n can be exactly 1)" "No result found." "$ALL_ALERTS"
NOW="$(device_ms)"; A_START=$(( (NOW / 60000 + 30) * 60000 )); A_ALARM=$(( A_START - 600000 ))
EVA="$(cmkevent "$TESS" 'Event A' "$A_START" $(( A_START + 3600000 )))"
cmkreminder "$EVA" 10; sleep 2.5
assert_ne "(e) event A with a 10-minute reminder" "" "$EVA"
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 2
assert_contains "(e) Android's Settings is in front" "com.android.settings" "$(top_activity)"
adb shell pm clear app.tileshell > "$ROW_DIR/e-pm-clear.txt" 2>&1
sleep 3
PID_CLEAR="$(adb shell pidof app.tileshell | tr -d '\r')"
record "(e) the shell's pid 3 s after pm clear (empty = pm clear left no process)" "[$PID_CLEAR]"
if [ -n "$PID_CLEAR" ]; then
  # The system starts the shell again within seconds of a pm clear — it holds the HOME and ASSISTANT roles, the
  # notification listener and the keyboard — and that start would write the cut-off BEFORE A comes due, so the leg
  # would test nothing (run 1 on build 3c1ad1e0). "The shell is stopped, its store gone" is then made true the only
  # way that holds: the package is uninstalled (provision.sh installs the build under test again below).
  note "pm clear did not leave the shell stopped (pid $PID_CLEAR, store: $(csync_json | head -c 120)); uninstalling it instead (clauses-open.tsv)"
  adb uninstall app.tileshell > "$ROW_DIR/e-uninstall.txt" 2>&1
  sleep 2
  assert_contains "(e) the shell is uninstalled (pm clear alone lets the system restart it)" "Success" "$(cat "$ROW_DIR/e-uninstall.txt")"
fi
PID_E="$(adb shell pidof app.tileshell | tr -d '\r')"
assert_eq "(e) precondition — no process of the shell runs right before the jump" "" "$PID_E"
assert_eq "(e) … and its store is gone (no calendar_sync.json)" "" "$(csync_json)"
jump_clock $(( A_ALARM + 60000 )) > "$ROW_DIR/e-jump-a.txt"
sleep 6
A_ROW="$(q "content query --uri $ALERTS --projection event_id:state:alarmTime --where \"event_id=$EVA\"")"; log "A's calendar_alerts row once it came due: $A_ROW"
assert_eq "(e) A's alert came due while no shell ran: its calendar_alerts row is present, state 0 or 1" "yes" "$(printf '%s' "$A_ROW" | grep -Eq "event_id=$EVA, state=[01]," && echo yes || echo no)"
assert_eq "(e) … and still no process of the shell after the jump (A came due with no shell running)" "" "$(adb shell pidof app.tileshell | tr -d '\r')"
( bash "$P03S/provision.sh" > "$ROW_DIR/provision-e.out" 2>&1; echo $? > "$ROW_DIR/provision-e.rc" )
assert_eq "(e) provision.sh rc" "0" "$(cat "$ROW_DIR/provision-e.rc")"
ensure_start
assert_contains "(e) the device still holds the build under test" "yes" "$(apk_matches)"
E_ROWMARK="$(ring_mark)"
record "(e) the shell's own line at its first start (csince the row's MARK)" "$(cline "$(csince "$ROW_MARK")" '[calendar] reminders count from')"
SINCE="$(csync_json | python3 -c '
import json, sys
try: print(json.load(sys.stdin).get("remindersSince", ""))
except Exception: print("")')"
record "(e) remindersSince in calendar_sync.json after the first start (A's alarm time is $A_ALARM)" "[$SINCE]"
assert_eq "(e) the shell's first start is AFTER A's alert came due: remindersSince is later than A's alarm time" "yes" "$([ -n "$SINCE" ] && [ "$SINCE" -gt "$A_ALARM" ] 2>/dev/null && echo yes || echo no)"
TESS_E="$(tessera_id)"
NOW="$(device_ms)"; B_START=$(( (NOW / 60000 + 30) * 60000 ))
EVB="$(cmkevent "$TESS_E" 'Event B' "$B_START" $(( B_START + 3600000 )))"
cmkreminder "$EVB" 10; sleep 2.5
assert_ne "(e) event B with a 10-minute reminder" "" "$EVB"
A_STATE0="$(q "content query --uri $ALERTS --projection event_id:state --where \"event_id=$EVA\"" | sed 's/^Row: [0-9]* //' | tr '\n' ';')"
record "(e) the shell's calendar notifications before B's poke" "$(cnotes | tr '\n' ';')"
jump_clock $(( B_START - 600000 - 5000 )) > "$ROW_DIR/e-jump-b.txt"
E_MARK="$(ring_mark)"
W="$(wait_note 'Event B' 12)"; sleep 4
NOTES="$(cnotes)"; log "the shell's calendar notifications: $(echo "$NOTES" | tr '\n' ';') (B after $W s)"
E_SLICE="$(csince "$E_MARK")"; printf '%s\n' "$E_SLICE" > "$ROW_DIR/e-slice.txt"
csince "$E_ROWMARK" | grep -F '[calendar] reminder' > "$ROW_DIR/e-reminder-lines-since-first-start.txt"
assert_ne "(e) B's notification shows" "none" "$W"
assert_within "(e) … within 10 s" 5 "$W" 5
assert_eq "(e) exactly ONE notification of the shell's" "1" "$(printf '%s\n' "$NOTES" | grep -c 'title=')"
assert_eq "(e) … titled B" "1" "$(printf '%s\n' "$NOTES" | grep -cF 'title=[Event B]')"
assert_eq "(e) … none titled A" "0" "$(printf '%s\n' "$NOTES" | grep -cF 'title=[Event A]')"
assert_eq "(e) A's alert row's state is unchanged from its read before the poke" "$A_STATE0" "$(q "content query --uri $ALERTS --projection event_id:state --where \"event_id=$EVA\"" | sed 's/^Row: [0-9]* //' | tr '\n' ';')"
assert_contains "(e) the slice holds [calendar] reminder: 1 skipped (due before the shell's first start)" "[calendar] reminder: 1 skipped (due before the shell's first start)" "$E_SLICE"
assert_eq "(e) … and one notified line" "1" "$(clines "$E_SLICE" ": notified")"
assert_eq "(e) … B's" "1" "$(clines "$E_SLICE" "[calendar] reminder event=$EVB minutes=10: notified")"
assert_eq "(e) … and no notified line for A at any time since the shell's first start" "0" "$(grep -cF "reminder event=$EVA minutes=10: notified" "$ROW_DIR/e-reminder-lines-since-first-start.txt")"

# =============================================================================================== restore
log "--- restore (r3 V10)"
ring_save
adb shell pm enable "$AOSP_CAL" > /dev/null 2>&1
assert_eq "restore: pm enable com.android.calendar" "1" "$(enabled "$AOSP_CAL")"
cpurge "title IN ($MADE_TITLES)"
assert_eq "restore: the events are deleted" "0" "$(cevent_count "title IN ($MADE_TITLES)")"
assert_eq "restore: … and their reminders" "No result found." "$(q "content query --uri $REMINDERS --projection event_id:minutes --where \"event_id IN (${EV:-0},${EV2:-0},${EV3:-0},${EVA:-0},${EVB:-0})\"")"
clock_restore
assert_eq "restore: dumpsys notification --noredact holds none on the calendar channel" "" "$(cnotes)"
assert_eq "restore: no alarm of the shell's own is left by the row" "$P_BASE" "$(shell_alarms_pending)"
assert_eq "restore: no new crash of the shell during the row" "$CRASH0" "$(ccrashes)"
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline (leg (e) cleared the shell)" "0" "$?"
ensure_start
row_end
