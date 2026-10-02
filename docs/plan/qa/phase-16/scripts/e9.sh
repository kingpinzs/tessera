#!/usr/bin/env bash
# Phase 16 E9 — Tess on this AVD (the re-cut of phase 03 E2's calendar rows; r3 D1, V10, V20).
#
#   children  (each takes the device lock itself, so they run BEFORE this row holds it; the phase 03 row folder is moved
#             aside and put back, the new run kept under E9/ — e1.sh's run_row form):
#               J6   qa/phase-03/scripts/j6.sh, unchanged: Tess's event lands in Tessera and in neither QA account calendar
#               J6b  the same again with a Birthdays calendar present (the row's own fixture contact given a birthday,
#                    under the device lock, before the child starts)
#   B  Birthdays present   `Tessera Birthdays` lists; a typed add lands in Tessera, never in Birthdays (T16-2 line 2)
#   A  the typed add       "add a calendar event called dentist tomorrow at 2 pm" → the confirmation card (phase 03 E7's
#                          form) → confirm tapped → events lists "dentist" with Tessera's id; the reply "Added to your
#                          calendar."
#   W  the read            the typed "what is on my calendar" names the events inserted for the test
#   V  the app             the Calendar app's Day view lists the same event
#   D  Tess's delete       "delete the event dentist" → the Delete card → confirm → "Deleted.", the events row gone,
#                          `[calendar] write delete event=<id>: ok` naming a Tessera event
#   G  phase 03 E10's gated calendar commands, with the keyguard up: "Unlock to continue", nothing added
#   N  phase 03 E7 (text / call by name) and E14 (person reminder) on the same fixtures — Contacts.byName unchanged
#   restore   the row's events deleted, the birthday row removed, the Birthdays calendar deleted, people_fixtures_down
#
# clauses-open.tsv: phase 03's e10.sh, e7.sh and e14.sh SPEAK every request (speak.sh: the microphone), and no row of this
# phase uses the microphone (V20), so they cannot be re-run "unchanged". Legs G and N assert the same behaviour with
# TYPED requests: the gated calendar commands over a real keyguard; text and call by name resolving Mom; a person
# reminder made by a typed request and fired by an incoming text from Mom's number.
#
# The spec's "tomorrow at 2 pm" dentist is inside Tess's 24-hour read only when the row runs after 14:00 device time; the
# driver also inserts "E9 checkup" one hour ahead so "the events inserted for the test" always has one in the window.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"
QAR="$(cd "$QA/.." && pwd)"
OUT="$QA/E9"; mkdir -p "$OUT"
PIN=1234
# E9_LEGS=A,W runs only those legs (a narrow re-run after a failed one, as the owner's ruling of 2026-10-01 allows); the
# children (J6, J6b) are then not run again either. The log says which legs ran. Unset = the whole row.
LEGS="${E9_LEGS:-all}"
[ "$LEGS" = all ] || E9_CHILDREN=0
leg() { [ "$LEGS" = all ] || case ",$LEGS," in *",$1,"*) return 0;; *) return 1;; esac; }

# =============================================================================================== children
run_row() { # phase row script label
  local phase="$1" row="$2" script="$3" label="$4"
  local dir="$QAR/$phase/$row" keep="$QAR/$phase/$row.p16-e9-aside"
  [ -e "$dir" ] && mv "$dir" "$keep"
  local i rc
  for i in $(seq 1 40); do
    ( bash "$QAR/$phase/scripts/$script" > "$OUT/$label.out" 2>&1; echo $? > "$OUT/$label.rc" )
    rc="$(cat "$OUT/$label.rc")"; [ "$rc" != 3 ] && break
    sleep 20   # another QA driver holds the device
  done
  rm -rf "$OUT/$label"; [ -e "$dir" ] && mv "$dir" "$OUT/$label"
  [ -e "$keep" ] && mv "$keep" "$dir"
  adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 5
}
# j6.sh's own restore deletes nothing (its `content delete … --where "title='standup'"` reaches the device shell as
# separate words), so the event of one run is still there for the next: every "standup" is purged here, under the
# device lock, before each child and before the row.
purge_standup() {
  (
    exec 9>"$DEVICE_LOCK"
    for i in $(seq 1 60); do flock -n 9 && break; sleep 10; done
    cpurge "title='standup'"
    echo "standup events after the purge ($1): $(cevent_count "title='standup'")" >> "$OUT/children-purge.txt"
  )
}
if [ "${E9_CHILDREN:-1}" = "1" ]; then
  : > "$OUT/children-purge.txt"
  purge_standup "before J6"
  run_row phase-03 J6 j6.sh J6
  purge_standup "after J6"
  # The Birthdays calendar for the second run: the row's own contacts, Ann given a birthday (E17's insert). Under the
  # device lock, which is released before the child takes it.
  (
    exec 9>"$DEVICE_LOCK"
    for i in $(seq 1 60); do flock -n 9 && break; sleep 10; done
    ROW_DIR="$OUT"; LOG="$OUT/children-fixtures.txt"; : > "$LOG"
    people_fixtures_up
    cbirthday "$ANN" "1990-$(adb shell date +%m-%d | tr -d '\r')"
    for i in 1 2 3 4 5 6; do sleep 1; [ -n "$(cbirthdays_cal)" ] && break; done
    echo "ANN=$ANN" > "$OUT/children-fixtures.env"; echo "RAW_BEFORE=$RAW_BEFORE" >> "$OUT/children-fixtures.env"
    echo "birthdays calendar before J6b: [$(cbirthdays_cal)]" >> "$LOG"
    cals >> "$LOG"
  )
  run_row phase-03 J6 j6.sh J6b
  purge_standup "after J6b"
fi

# =============================================================================================== the row
row_begin E9 "Tess on this AVD: J6 twice, the typed add / read / delete, the gated commands, text / call / reminder by name"
pin_set=no
e9_exit() {
  [ "$pin_set" = yes ] && adb shell locksettings clear --old $PIN >/dev/null 2>&1
  adb shell locksettings set-disabled true >/dev/null 2>&1
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  adb shell wm dismiss-keyguard >/dev/null 2>&1
}
trap e9_exit EXIT
bevents() { q "content query --uri $EVENTS --projection _id:title --where \"calendar_id=$1 AND deleted=0\"" | sed 's/^Row: [0-9]* //'; }
sms_sent() { q "content query --uri content://sms/sent --projection _id" | grep -c '^Row:'; }
calls() { adb shell dumpsys telecom 2>/dev/null | grep -cE 'Call id|mCallId'; }
reminders_json() { adb shell run-as app.tileshell cat /data/data/app.tileshell/files/cortana_reminders.json 2>/dev/null | tr -d '\r'; }
reminder_count() { reminders_json | grep -o '"id"' | wc -l | tr -d ' '; }
shell_notifs() { adb shell dumpsys notification --noredact 2>/dev/null | grep -c 'pkg=app.tileshell'; }
CRASH0="$(ccrashes)"

if [ "${E9_CHILDREN:-1}" = "1" ]; then
  log "--- children: J6 on this build, and again with a Birthdays calendar present"
  for c in J6 J6b; do
    assert_eq "$c: j6.sh rc" "0" "$(cat "$OUT/$c.rc" 2>/dev/null)"
    L="$OUT/$c/J6.txt"
    assert_contains "$c: the run is on the build under test" "apk match     yes" "$(grep -m1 '^apk match' "$L" 2>/dev/null)"
    assert_contains "$c: 0 failed" " passed, 0 failed" "$(tail -2 "$L" 2>/dev/null)"
    assert_contains "$c: nothing in the account calendar marked primary (QA Work)" "PASS  nothing was written to the account calendar marked primary (QA Work)" "$(cat "$L" 2>/dev/null)"
    assert_contains "$c: nothing in the other account calendar (QA Personal)" "PASS  nothing was written to the other account calendar (QA Personal)" "$(cat "$L" 2>/dev/null)"
    assert_contains "$c: Tess's event lands in Tessera (its lookup keyed on account_name=Tessera)" "PASS  the event is in the shell's local calendar" "$(cat "$L" 2>/dev/null)"
  done
  assert_contains "J6b: the Birthdays calendar was present before the child ran (the fixture's log)" "account_name=Tessera Birthdays," "$(cat "$OUT/children-fixtures.txt" 2>/dev/null)"
  # shellcheck disable=SC1091
  . "$OUT/children-fixtures.env"
elif [ "$LEGS" = all ]; then
  record "children" "SKIPPED (E9_CHILDREN=0): a development run, not the gate's"
  people_fixtures_up
  cbirthday "$ANN" "1990-$(adb shell date +%m-%d | tr -d '\r')"; sleep 5
else
  record "legs run" "$LEGS ONLY — a narrow re-run; the children and the other legs stand on the row's earlier run"
fi
c6; ensure_start
TESS="$(tessera_id)"
assert_ne "precondition: Tessera exists" "" "$TESS"
assert_eq "precondition: no standup event is left by the children (purged; j6.sh's own restore deletes nothing)" "0" "$(cevent_count "title='standup'")"
BEFORE="$(ctessera_count)"
REM0="$(reminders_json)"

# ----------------------------------------------------------------------------------------------- B: Birthdays present
if leg B; then
log "--- B: with a Birthdays calendar present, Tess's add still lands in Tessera"
BDAY="$(cbirthdays_cal)"
assert_ne "B: the row's own fixture contact has a birthday and Tessera Birthdays lists" "" "$BDAY"
B0="$(bevents "${BDAY:-0}" | grep -c '_id=')"
tess_ask "add a meeting called standup to my calendar at ten AM"
tess_card "$ROW_DIR/B-card.xml"
assert_eq "B: Tess shows the confirm card" "yes" "$(has_node "$ROW_DIR/B-card.xml" cortana_card_button:confirm)"
tess_confirm "$ROW_DIR/B-card.xml"
tess_close
assert_eq "B: the event is in Tessera" "calendar_id=$TESS" "$(cevents calendar_id "title='standup' AND deleted=0" | sed -n 's/^Row: [0-9]* //p' | paste -sd';')"
assert_eq "B: … never in Birthdays (its events are as before)" "$B0" "$(bevents "${BDAY:-0}" | grep -c '_id=')"
assert_absent "B: … Birthdays holds no standup" "standup" "$(bevents "${BDAY:-0}")"
cpurge "title='standup'"
# "Then, with Tessera and no fixture calendar": the birthday goes, and the Birthdays calendar with it.
q "content delete --uri $DATA --where \"raw_contact_id=$ANN AND mimetype='vnd.android.cursor.item/contact_event'\"" >/dev/null
sleep 3
cal_fixtures_down
assert_eq "B: then Tessera is the only calendar (no fixture calendar)" "1" "$(cals | grep -c '_id=')"
fi

# ----------------------------------------------------------------------------------------------- A: the typed add
if leg A; then
log "--- A: the typed \"add a calendar event called dentist tomorrow at 2 pm\""
record "the device's time at this leg (the dentist is in Tess's 24-hour read after 14:00 only)" "$(q "date '+%Y-%m-%d %H:%M %Z'")"
NOW="$(device_ms)"; CK=$(( (NOW / 60000 + 60) * 60000 ))
CHECKUP="$(cmkevent "$TESS" 'E9 checkup' "$CK" $(( CK + 1800000 )))"
tess_ask "add a calendar event called dentist tomorrow at 2 pm"
tess_card "$ROW_DIR/A-card.xml"; screencap "$ROW_DIR/A-card.png"
assert_eq "A: the confirmation card (phase 03 E7's form: cortana_card:calendar_confirm)" "yes" "$(has_node "$ROW_DIR/A-card.xml" cortana_card:calendar_confirm)"
assert_eq "A: … with its confirm button" "yes" "$(has_node "$ROW_DIR/A-card.xml" cortana_card_button:confirm)"
assert_eq "A: … and nothing is added before the tap" "0" "$(cevent_count "title='dentist'")"
record "A: the card's texts" "$(ctexts "$ROW_DIR/A-card.xml" cortana_card:calendar_confirm)"
tess_confirm "$ROW_DIR/A-card.xml"
A_REPLY="$(reply_since "$CMARK")"
DROW="$(cevents _id:calendar_id:title:dtstart "title='dentist' AND deleted=0")"; log "events: $DROW"
assert_eq "A: after the tap on cortana_card_button:confirm, events lists \"dentist\" — one row" "1" "$(printf '%s\n' "$DROW" | grep -c 'title=dentist')"
assert_contains "A: … with Tessera's id (read from the account_name=Tessera query)" "calendar_id=$TESS," "$DROW"
assert_contains "A: … tomorrow at 2 pm" "dtstart=$(clocal_ms "$(cdate 1)" 14:00)" "$DROW"
assert_eq "A: the reply (reply_since the MARK) \"Added to your calendar.\"" "Added to your calendar." "$A_REPLY"
DENTIST="$(event_id dentist "$TESS")"
if [ -z "$DENTIST" ]; then
  # The title clause above has failed; the legs below go on with the row Tess did write, so they test their own clauses.
  DENTIST="$(q "content query --uri $EVENTS --projection _id:title --where \"title LIKE '%dentist%' AND calendar_id=$TESS AND deleted=0\" --sort '_id DESC'" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
  record "A: no event is titled \"dentist\"; the row Tess wrote, used by the legs below" "$(cevents _id:title "_id=${DENTIST:-0}" | sed 's/^Row: 0 //')"
fi
assert_ne "A: Tess wrote an event (its id is known to the legs below)" "" "$DENTIST"
ring_since "$TMARK" > "$ROW_DIR/A-slice.txt"
tess_close
fi

# ----------------------------------------------------------------------------------------------- W: the read
if leg W; then
log "--- W: the typed \"what is on my calendar\""
tess_ask "what is on my calendar" 5
W_REPLY="$(reply_since "$TMARK")"; log "Tess: [$W_REPLY]"
assert_contains "W: it names the events inserted for the test — the driver's \"E9 checkup\"" "E9 checkup" "$W_REPLY"
# Tess reads the next 24 hours (ActionLayer.calendarToday). "Tomorrow at 2 pm" is inside them only when the request is
# made after 14:00; before that hour the dentist is rightly not named (clauses-open.tsv).
DSTART="$(cevents dtstart "_id=${DENTIST:-0}" | sed -n 's/.*dtstart=\([0-9]*\).*/\1/p')"
if [ -n "$DSTART" ] && [ $(( DSTART - $(device_ms) )) -lt 86400000 ]; then
  assert_contains "W: … and \"dentist\" (it starts inside Tess's 24 hours)" "dentist" "$W_REPLY"
else
  record "W: the dentist starts $(( (${DSTART:-0} - $(device_ms)) / 3600000 )) h from now — outside the 24 hours Tess reads" "tomorrow 14:00, asked at $(q "date '+%H:%M'")"
  assert_absent "W: … and not \"dentist\", which starts outside Tess's 24 hours at this hour" "dentist" "$W_REPLY"
fi
tess_close
fi

# ----------------------------------------------------------------------------------------------- V: the Calendar app
if leg V; then
log "--- V: the Calendar app's Day view lists the same event"
copen_day "$(clocal_ms "$(cdate 1)" 12:00)"; dump_ui "$ROW_DIR/V-day.xml"
assert_eq "V: the Day view is showing" "true" "$(cattr "$ROW_DIR/V-day.xml" cal_view_mode:day selected)"
assert_eq "V: it lists the same event (cal_event:<its id>)" "yes" "$(has_node "$ROW_DIR/V-day.xml" "cal_event:${DENTIST:-none}")"
assert_eq "V: … under the title the provider holds" "$(cevents title "_id=${DENTIST:-0}" | sed 's/^Row: 0 title=//')" "$(ctext "$ROW_DIR/V-day.xml" "cal_event_title:${DENTIST:-none}")"
c6
fi

# ----------------------------------------------------------------------------------------------- D: Tess's delete
if leg D; then
log "--- D: Tess's delete, the allowed case (r3 D1)"
tess_ask "delete the event dentist"
tess_card "$ROW_DIR/D-card.xml"; screencap "$ROW_DIR/D-card.png"
assert_eq "D: the Delete card (cortana_card:delete_confirm)" "yes" "$(has_node "$ROW_DIR/D-card.xml" cortana_card:delete_confirm)"
assert_eq "D: … nothing is deleted before the tap" "1" "$(cevent_count "_id=${DENTIST:-0} AND deleted=0")"
tess_confirm "$ROW_DIR/D-card.xml"
assert_eq "D: cortana_card_button:confirm → the reply \"Deleted.\"" "Deleted." "$(reply_since "$CMARK")"
assert_eq "D: the events row is gone" "0 known" "$(cevent_count "_id=${DENTIST:-0}") $([ -n "$DENTIST" ] && echo known || echo unknown)"
D_SLICE="$(ring_since "$TMARK")"; printf '%s\n' "$D_SLICE" > "$ROW_DIR/D-slice.txt"
assert_contains "D: [calendar] write delete event=<id>: ok — naming a Tessera event (the dentist's id)" "[calendar] write delete event=$DENTIST: ok" "$D_SLICE"
tess_close
fi

# ----------------------------------------------------------------------------------------------- G: the gated commands
if leg G; then
log "--- G: phase 03 E10's gated calendar commands, typed, over the keyguard"
EV0="$(cevent_count "deleted=0")"
adb shell locksettings set-disabled false >/dev/null 2>&1
adb shell locksettings set-pin $PIN >/dev/null 2>&1 && pin_set=yes
assert_eq "G: a PIN is set and the lock screen enabled" "yes false" "$pin_set $(adb shell locksettings get-disabled | tr -d '\r')"
adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 3
cortana_assist; sleep 5
dump_ui "$ROW_DIR/G-locked.xml"
if [ "$(has_node "$ROW_DIR/G-locked.xml" cortana_session)" != yes ]; then adb shell cmd voiceinteraction show >/dev/null 2>&1; sleep 5; dump_ui "$ROW_DIR/G-locked.xml"; fi
assert_eq "G: Tess is open over the keyguard" "yes" "$(has_node "$ROW_DIR/G-locked.xml" cortana_session)"
assert_contains "G: … the keyguard is showing" "isKeyguardShowing=true" "$(adb shell dumpsys window | tr -d '\r' | grep -o 'isKeyguardShowing=[a-z]*' | head -1)"
gated() { # label request
  local m; m="$(ring_mark)"
  type_request "$2" 5
  dump_ui "$ROW_DIR/G-$1.xml"; screencap "$ROW_DIR/G-$1.png"
  assert_eq "G: locked, \"$2\" shows the Unlock card (cortana_card:unlock)" "yes" "$(has_node "$ROW_DIR/G-$1.xml" cortana_card:unlock)"
  assert_contains "G: … reading \"Unlock to continue\"" "Unlock to continue" "$(ctexts "$ROW_DIR/G-$1.xml" cortana_card:unlock)"
  note "matcher: $(ring_since "$m" | grep -F '[match]' | tail -1 | sed 's/^.*wall=[0-9]* //')"
}
gated calendar_query "what is on my calendar"
gated calendar_add "add a meeting called standup to my calendar at ten AM"
assert_eq "G: nothing was added to the calendar" "$EV0" "$(cevent_count "deleted=0")"
assert_contains "G: … the keyguard never came down" "isKeyguardShowing=true" "$(adb shell dumpsys window | tr -d '\r' | grep -o 'isKeyguardShowing=[a-z]*' | head -1)"
cortana_close
adb shell locksettings clear --old $PIN >/dev/null 2>&1 && pin_set=no
adb shell locksettings set-disabled true >/dev/null 2>&1
assert_eq "G: restored — the PIN cleared and the lock screen disabled again" "no true" "$pin_set $(adb shell locksettings get-disabled | tr -d '\r')"
assert_eq "G: … the device awake" "Awake" "$(wake_device)"
adb shell wm dismiss-keyguard >/dev/null 2>&1; sleep 1
ensure_start
fi

# ----------------------------------------------------------------------------------------------- N: by name
if leg N; then
log "--- N: phase 03 E7 (text / call by name) and E14 (person reminder), typed, on provision.sh's Mom"
assert_contains "N: the fixture contact Mom (5551234567) exists" "display_name=Mom" "$(q "content query --uri content://com.android.contacts/data/phones --projection display_name:data1")"
SENT0="$(sms_sent)"; CALLS0="$(calls)"
tess_ask "text Mom running late" 6
dump_ui "$ROW_DIR/N-text.xml"; screencap "$ROW_DIR/N-text.png"
assert_eq "N: \"text Mom running late\" → the read-back card (cortana_card:text_readback)" "yes" "$(has_node "$ROW_DIR/N-text.xml" cortana_card:text_readback)"
NT="$(ctexts "$ROW_DIR/N-text.xml" cortana_card:text_readback) / $(reply_since "$TMARK")"; note "the read-back: $NT"
assert_contains "N: … the contact resolved by name: Mom" "Mom" "$NT"
if [ "$(has_node "$ROW_DIR/N-text.xml" cortana_card_button:cancel)" = yes ]; then tap_node "$ROW_DIR/N-text.xml" cortana_card_button:cancel; sleep 3; fi
tess_close
tess_ask "call Mom" 6
dump_ui "$ROW_DIR/N-call.xml"; screencap "$ROW_DIR/N-call.png"
assert_eq "N: \"call Mom\" → the call card (cortana_card:call_confirm)" "yes" "$(has_node "$ROW_DIR/N-call.xml" cortana_card:call_confirm)"
NC="$(ctexts "$ROW_DIR/N-call.xml" cortana_card:call_confirm) / $(reply_since "$TMARK")"; note "the call card: $NC"
assert_contains "N: … the contact resolved by name: Mom" "Mom" "$NC"
if [ "$(has_node "$ROW_DIR/N-call.xml" cortana_card_button:cancel)" = yes ]; then tap_node "$ROW_DIR/N-call.xml" cortana_card_button:cancel; sleep 3; fi
tess_close
assert_eq "N: nothing was sent and nothing dialled (both cancelled)" "$SENT0 $CALLS0" "$(sms_sent) $(calls)"
R0="$(reminder_count)"
tess_ask "remind me to ask about dinner next time I talk to Mom" 6
dump_ui "$ROW_DIR/N-reminder.xml"; screencap "$ROW_DIR/N-reminder.png"
assert_eq "N: the person reminder card (cortana_card:reminder_confirm)" "yes" "$(has_node "$ROW_DIR/N-reminder.xml" cortana_card:reminder_confirm)"
assert_eq "N: … with a contact chip (cortana_card_contact)" "yes" "$(has_node "$ROW_DIR/N-reminder.xml" cortana_card_contact)"
assert_contains "N: … for Mom" "Mom" "$(ctexts "$ROW_DIR/N-reminder.xml" cortana_card:reminder_confirm)"
tess_confirm "$ROW_DIR/N-reminder.xml" 5
dump_ui "$ROW_DIR/N-saved.xml"
assert_contains "N: the saved card's subline" "Next time I talk to" "$(ctext "$ROW_DIR/N-saved.xml" cortana_card_saved_subline)"
assert_eq "N: one reminder stored" "$(( R0 + 1 ))" "$(reminder_count)"
tess_close
NB="$(shell_notifs)"
adb emu sms send 5551234567 hi >/dev/null 2>&1
sleep 14
adb shell dumpsys notification --noredact > "$ROW_DIR/N-notifications.txt" 2>/dev/null
assert_ne "N: a text received from Mom's number fires the person reminder (the shell's notifications grew)" "$NB" "$(shell_notifs)"
assert_eq "N: … and it is done with: the reminder reads completed in Tess's store" "true" "$(reminders_json | python3 -c '
import json, sys
try: print(str(next(r["completed"] for r in reversed(json.load(sys.stdin)["reminders"]) if r["text"] == "ask about dinner")).lower())
except Exception as e: print("unreadable")')"
record "N: Tess's reminder store before the row / after (a fired person reminder stays, completed — as phase 03 E14 leaves its own)" "$(printf '%s' "$REM0" | grep -o '"id"' | wc -l | tr -d ' ') / $(reminder_count) reminder(s)"
sleep 3; adb shell cmd statusbar collapse >/dev/null 2>&1
fi   # leg N

# ----------------------------------------------------------------------------------------------- restore
# Every run ends here, a narrow one too: the restore, then row_end's summary line.
log "--- restore (r3 V10)"
c6
cpurge "title IN ('dentist','standup','E9 checkup') OR title LIKE '%dentist%'"
assert_eq "restore: the row's events are deleted" "0" "$(cevent_count "title IN ('dentist','standup','E9 checkup') OR title LIKE '%dentist%'")"
assert_eq "restore: the birthday row is removed" "0" "$(cbirthdays_on_phone)"
cal_fixtures_down
people_fixtures_down
assert_eq "restore: Tessera's event count equals the count before the row" "$BEFORE" "$(ctessera_count)"
assert_eq "restore: no new crash of the shell during the row" "$CRASH0" "$(ccrashes)"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 4   # clears the fired reminder's notification
ensure_start
row_end
