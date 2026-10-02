#!/usr/bin/env bash
# Phase 16 E17 — Birthdays (Q3; r3 D14, V4, V10, V15; the 29 February form re-cut 2026-10-01).
#
#   A  the first birthday   people_fixtures_up, no Birthdays calendar, the Calendar app NOT opened since the last
#                           ensure_start; a MARK; a birthday on Ann (the DEVICE's own month-day, 1990) → within 5 s a
#                           "Birthdays" LOCAL calendar under `Tessera Birthdays` (never Tessera; access 200) with a yearly
#                           all-day "Ann Lee's birthday"; `[calendar] birthdays: 1 synced`; no reminder rows
#   B  shown everywhere     the tile shows it today, phase 14's Agenda pod lists it, Tess's typed "what is on my
#                           calendar" names it, the Calendar app shows it on today's date and refuses to edit it
#   C  removed              removing the birthday row removes the event
#   D  the date forms       a no-year birthday on Bob → yearly from this year's date (tomorrow); 1992-02-29 on a third
#                           fixture → FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1, 28 February 2027 and 29 February 2028
#   E  Tess's add           made while Birthdays exists, it lands in Tessera
#   F  creation refused     Birthdays deleted and the calendar provider disabled, a birthday added to Bob →
#                           `birthdays calendar could not be created: <err>`, no crash; provider enabled → the next
#                           contacts change creates `Tessera Birthdays` again
#   restore                 the provider enabled (asserted), the birthday rows removed, the Birthdays calendar deleted,
#                           Tess's event deleted, people_fixtures_down
# The row makes no clock jump (r3 V4).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"
TILES="$P16/../phase-15/scripts/tiles.py"

row_begin E17 "Birthdays: a contact's birthday becomes a read-only calendar event with the Calendar app never opened"
bevents() { q "content query --uri $EVENTS --projection _id:title:dtstart:allDay:rrule:duration --where \"calendar_id=$1 AND deleted=0\"" | sed 's/^Row: [0-9]* //'; }
bev_id() { bevents "$1" | grep -F "title=$2," | sed -n 's/^_id=\([0-9]*\),.*/\1/p' | head -1; }   # calendar title
rm_birthday() { q "content delete --uri $DATA --where \"raw_contact_id=$1 AND mimetype='vnd.android.cursor.item/contact_event'\"" >/dev/null; }
provider_enabled() { adb shell pm list packages -e "$CAL_PROVIDER" < /dev/null | tr -d '\r' | grep -c "^package:$CAL_PROVIDER$"; }
tile_texts() { python3 "$TILES" "$1" "$2" | awk -F'\t' '{ print $6 }' | sed 's/^texts=//'; }
utc_ms() { echo $(( $(date -u -d "$1" +%s) * 1000 )); }   # yyyy-mm-dd -> UTC midnight ms
CRASH0="$(ccrashes)"

# ----------------------------------------------------------------------------------------------- A: the first birthday
log "--- A: the first birthday, with the Calendar app not opened"
c6
cal_fixtures_down
rm_events_titled standup
ensure_start
assert_eq "A: precondition — no contact carries a birthday yet" "0" "$(cbirthdays_on_phone)"
TESS="$(tessera_id)"
assert_ne "A: precondition — Tessera exists (the shell's own calendar)" "" "$TESS"
TESS_ROW0="$(cals | grep "_id=$TESS,")"
people_fixtures_up
assert_ne "A: people_fixtures_up — the row's own Ann" "" "$ANN"
assert_ne "A: people_fixtures_up — the row's own Bob" "" "$BOB"
assert_eq "A: no Birthdays calendar (asserted)" "" "$(cbirthdays_cal)"
assert_eq "A: the Calendar app is NOT open (Start is on top since the last ensure_start)" "app.tileshell/.StartActivity" "$(top_activity)"
PID0="$(adb shell pidof app.tileshell | tr -d '\r')"
MD="$(adb shell date +%m-%d | tr -d '\r')"; TODAY="$(cdate 0)"; TOMORROW="$(cdate 1)"
record "the device's time and zone at the row's start" "$(q "date '+%Y-%m-%d %H:%M %Z (%z)'")"
A_MARK="$(ring_mark)"
cbirthday "$ANN" "1990-$MD"
for i in 1 2 3 4 5; do sleep 1; [ -n "$(cbirthdays_cal)" ] && break; done
B="$(cbirthdays_cal)"; note "the Birthdays calendar's id [$B] after $i s"
assert_ne "A: within 5 s the provider has a \"Birthdays\" local calendar" "" "$B"
ROWB="$(cals | grep "_id=${B:-x},")"; log "its row: $ROWB"
assert_contains "A: … account_name Tessera Birthdays" "account_name=Tessera Birthdays," "$ROWB"
assert_contains "A: … a LOCAL calendar" "account_type=LOCAL," "$ROWB"
assert_contains "A: … named Birthdays" "calendar_displayName=Birthdays," "$ROWB"
assert_contains "A: … access 200" "calendar_access_level=200," "$ROWB"
assert_eq "A: … never Tessera: Tessera's own row is untouched, still the only one of its account" "$TESS_ROW0" "$(cals | grep -F 'account_name=Tessera,')"
sleep 1
EV="$(bevents "$B")"; log "its events: $EV"
ANN_EV="$(bev_id "$B" "Ann Lee's birthday")"
assert_ne "A: an event \"Ann Lee's birthday\" in it" "" "$ANN_EV"
AROW="$(printf '%s\n' "$EV" | grep "^_id=$ANN_EV,")"
assert_contains "A: … all-day" "allDay=1," "$AROW"
assert_contains "A: … yearly: the events query shows the rrule FREQ=YEARLY" "rrule=FREQ=YEARLY" "$AROW"
assert_contains "A: … from the birthday's own date (1990-$MD, UTC midnight)" "dtstart=$(utc_ms "1990-$MD")," "$AROW"
assert_eq "A: … with an instance today" "1" "$(cinstances "$(cutc_day_ms 0)" $(( $(cutc_day_ms 1) - 1 )) "$ANN_EV")"
A_SLICE="$(ring_since "$A_MARK")"; printf '%s\n' "$A_SLICE" > "$ROW_DIR/A-slice.txt"
assert_contains "A: the slice from the MARK holds [calendar] birthdays: 1 synced" "[calendar] birthdays: 1 synced" "$A_SLICE"
assert_eq "A: the birthday event has no reminder rows (T16-4)" "No result found." "$(q "content query --uri $REMINDERS --where \"event_id=$ANN_EV\"")"
assert_eq "A: all of it with the Calendar app never opened and the shell never restarted (same pid, Start on top)" "$PID0 app.tileshell/.StartActivity" "$(adb shell pidof app.tileshell | tr -d '\r') $(top_activity)"

# ----------------------------------------------------------------------------------------------- B: shown everywhere
log "--- B: the tile, the Agenda pod, Tess, the Calendar app"
log "the feed after the birthday: $(cline "$(ring_since "$A_MARK")" '[calendar] refresh (')"
SEEN=0; : > "$ROW_DIR/B-tile-texts.txt"
for i in $(seq 1 16); do
  gdump "$ROW_DIR/B-start.xml"
  T="$(tile_texts "$ROW_DIR/B-start.xml" slot:CALENDAR)"; echo "$T" >> "$ROW_DIR/B-tile-texts.txt"
  case "$T" in *"Ann Lee"*) SEEN=$((SEEN + 1)); cp "$ROW_DIR/B-start.xml" "$ROW_DIR/B-start-birthday.xml";; esac
  sleep 1
done
screencap "$ROW_DIR/B-start.png"
log "the CALENDAR tile's texts over 16 dumps: $(sort "$ROW_DIR/B-tile-texts.txt" | uniq -c | tr '\n' ';')"
assert_ne "B: the tile shows it today (dumps of 16 in which the CALENDAR tile reads Ann Lee's birthday)" "0" "$SEEN"
cpod_bay "$ROW_DIR/B-bay.xml"; screencap "$ROW_DIR/B-bay.png"
assert_eq "B: the pod bay is showing, its Agenda pod on screen" "yes yes" "$(has_node "$ROW_DIR/B-bay.xml" pod_bay) $(has_node "$ROW_DIR/B-bay.xml" pod:agenda)"
PODROWS="$(for n in 0 1 2 3 4 5; do ctexts "$ROW_DIR/B-bay.xml" "pod_row:agenda:$n"; done | grep -v '^$' | paste -sd';')"; log "the Agenda pod's rows: $PODROWS"
assert_contains "B: phase 14's Agenda pod lists it (a pod_row:agenda: text)" "Ann Lee's birthday" "$PODROWS"
tess_ask "what is on my calendar" 5
TREPLY="$(reply_since "$TMARK")"; log "Tess: [$TREPLY]"
assert_contains "B: Tess's typed \"what is on my calendar\" names it" "Ann Lee's birthday" "$(printf '%s' "$TREPLY" | sed "s/&apos;/'/g")"
ring_since "$TMARK" > "$ROW_DIR/B-tess-slice.txt"
tess_close
copen_day "$(clocal_ms "$TODAY" 12:00)"; dump_ui "$ROW_DIR/B-day.xml"; screencap "$ROW_DIR/B-day.png"
assert_eq "B: the Calendar app shows it on today's date — today's Day view holds its cal_event node" "yes" "$(has_node "$ROW_DIR/B-day.xml" "cal_event:$ANN_EV")"
assert_eq "B: … reading its title" "Ann Lee's birthday" "$(ctext "$ROW_DIR/B-day.xml" "cal_event_title:$ANN_EV")"
W="$(cwithin "$ROW_DIR/B-day.xml" "cal_allday:$TODAY" "cal_event:$ANN_EV")"
assert_eq "B: … in today's all-day band (cal_allday:$TODAY)" "yes" "${W%% *}"
cview agenda 2.5; dump_ui "$ROW_DIR/B-agenda.xml"
assert_contains "B: … and the Agenda lists it under today's heading" "$TODAY	$ANN_EV	" "$(cagenda_dump "$ROW_DIR/B-agenda.xml")"
ctap "cal_event:$ANN_EV" 2; dump_ui "$ROW_DIR/B-page.xml"
assert_eq "B: the editor refuses to edit it — cal_event_page:<id> present" "yes" "$(has_node "$ROW_DIR/B-page.xml" "cal_event_page:$ANN_EV")"
assert_eq "B: … and no cal_event_action:edit (a read-only calendar)" "no" "$(has_node "$ROW_DIR/B-page.xml" cal_event_action:edit)"
record "B: every cal_event_action node on the birthday's page" "[$(cids "$ROW_DIR/B-page.xml" cal_event_action:)]"
cback
ctap cal_menu 1.5; dump_ui "$ROW_DIR/B-pane.xml"
record "B: the pane lists Birthdays under its own account (row / header)" "$(cunder "$ROW_DIR/B-pane.xml" cal_pane "cal_calendar_row:$B") / $(cheader_above "$ROW_DIR/B-pane.xml" "cal_calendar_row:$B")"
cback
c6; ensure_start

# ----------------------------------------------------------------------------------------------- C: the birthday removed
log "--- C: removing the birthday row removes the event"
C_MARK="$(ring_mark)"
rm_birthday "$ANN"
for i in 1 2 3 4 5 6; do sleep 1; [ -z "$(bev_id "$B" "Ann Lee's birthday")" ] && break; done
assert_eq "C: Ann's event is gone from the Birthdays calendar" "" "$(bev_id "$B" "Ann Lee's birthday")"
assert_eq "C: … no events row of it is left" "0" "$(cevent_count "_id=$ANN_EV AND deleted=0")"
record "C: the birthdays line after the removal, and the calendar (kept, empty, when the last one goes — r3 D14)" "$(cline "$(ring_since "$C_MARK")" '[calendar] birthdays') / calendar id [$(cbirthdays_cal)]"

# ----------------------------------------------------------------------------------------------- D: the date forms
log "--- D: the date forms (r3 D14)"
D_MARK="$(ring_mark)"
cbirthday "$BOB" "--$(echo "$TOMORROW" | cut -c6-10)"
cbirthday "$ZOE" "1992-02-29"
sleep 5
B="$(cbirthdays_cal)"
EV="$(bevents "$B")"; log "events after the date forms: $EV"
BOB_EV="$(bev_id "$B" "Bob Stone's birthday")"
BROW="$(printf '%s\n' "$EV" | grep "^_id=${BOB_EV:-x},")"
assert_ne "D: a no-year birthday on Bob (--MM-dd of tomorrow) → an event" "" "$BOB_EV"
assert_contains "D: … yearly" "rrule=FREQ=YEARLY" "$BROW"
assert_contains "D: … all-day" "allDay=1," "$BROW"
assert_contains "D: … on tomorrow's date (this year's: dtstart = tomorrow's UTC midnight)" "dtstart=$(cutc_day_ms 1)," "$BROW"
assert_eq "D: … with an instance tomorrow" "1" "$(cinstances "$(cutc_day_ms 1)" $(( $(cutc_day_ms 2) - 1 )) "${BOB_EV:-0}")"
LEAP_EV="$(printf '%s\n' "$EV" | grep "dtstart=$(utc_ms 1992-02-29)," | sed -n 's/^_id=\([0-9]*\),.*/\1/p' | head -1)"
LROW="$(printf '%s\n' "$EV" | grep "^_id=${LEAP_EV:-x},")"
assert_ne "D: a 29 February birthday (1992-02-29 on a third fixture) → an event from that date" "" "$LEAP_EV"
assert_contains "D: … its rrule is FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1" "rrule=FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1" "$LROW"
feb() { q "content query --uri $INSTANCES/$(utc_ms "$1-02-01")/$(( $(utc_ms "$1-03-01") - 1 )) --projection event_id:begin --where \"event_id=${LEAP_EV:-0}\"" | sed -n 's/.*begin=\([0-9]*\).*/\1/p' | while read -r b; do date -u -d "@$(( b / 1000 ))" +%Y-%m-%d; done | paste -sd' '; }
assert_eq "D: … instances over February 2027: one, on 28 February" "2027-02-28" "$(feb 2027)"
assert_eq "D: … instances over February 2028: one, on 29 February" "2028-02-29" "$(feb 2028)"
assert_contains "D: the line counts both" "[calendar] birthdays: 2 synced" "$(ring_since "$D_MARK")"
assert_eq "D: no birthday event has a reminder row" "No result found." "$(q "content query --uri $REMINDERS --where \"event_id IN (${BOB_EV:-0},${LEAP_EV:-0})\"")"

# ----------------------------------------------------------------------------------------------- E: Tess's add
log "--- E: a Tess \"add\" made while Birthdays exists lands in Tessera (T16-2 line 2)"
assert_ne "E: Birthdays exists" "" "$(cbirthdays_cal)"
tess_ask "add a meeting called standup to my calendar at ten AM"
tess_card "$ROW_DIR/E-card.xml"
assert_eq "E: Tess shows the confirm card" "yes" "$(has_node "$ROW_DIR/E-card.xml" cortana_card_button:confirm)"
tess_confirm "$ROW_DIR/E-card.xml"
note "Tess said after the tap: [$(reply_since "$CMARK")]"
tess_close
assert_eq "E: standup lands in Tessera" "calendar_id=$TESS" "$(cevents calendar_id "title='standup' AND deleted=0" | sed -n 's/^Row: [0-9]* //p' | paste -sd';')"
assert_eq "E: … and the Birthdays calendar holds only its birthdays" "2" "$(bevents "$B" | grep -c 'birthday,')"
assert_eq "E: … nothing else" "2" "$(bevents "$B" | grep -c '_id=')"

# ----------------------------------------------------------------------------------------------- F: creation refused
log "--- F: creation refused (T16-19)"
# Race-free: the birthdays go first (the calendar is kept, empty), then the calendar — with no birthday on the phone the
# writer has nothing to recreate it for — then the provider is disabled, and only then a birthday is added.
rm_birthday "$BOB"; rm_birthday "$ZOE"; sleep 3
q "content delete --uri '$CAL?$SA&account_name=Tessera%20Birthdays&account_type=LOCAL' --where \"account_name='Tessera Birthdays'\"" >/dev/null
sleep 2
assert_eq "F: Birthdays is deleted" "" "$(cbirthdays_cal)"
ring_save
PIDF="$(adb shell pidof app.tileshell | tr -d '\r')"
adb shell pm disable-user --user 0 "$CAL_PROVIDER" > /dev/null 2>&1; sleep 2
assert_eq "F: pm disable-user com.android.providers.calendar" "0" "$(provider_enabled)"
F_MARK="$(ring_mark)"
cbirthday "$BOB" "1985-$MD"
sleep 6
F_SLICE="$(csince "$F_MARK")"; printf '%s\n' "$F_SLICE" > "$ROW_DIR/F-slice.txt"
log "$(cline "$F_SLICE" '[calendar] birthdays')"
assert_contains "F: a birthday added to Bob → [calendar] birthdays calendar could not be created: <err>" "[calendar] birthdays calendar could not be created: " "$F_SLICE"
assert_eq "F: … and no crash (no new crash entry)" "$CRASH0" "$(ccrashes)"
assert_eq "F: … the shell's process is the same one" "$PIDF" "$(adb shell pidof app.tileshell | tr -d '\r')"
adb shell pm enable "$CAL_PROVIDER" > /dev/null 2>&1; sleep 3
assert_eq "F: pm enable com.android.providers.calendar" "1" "$(provider_enabled)"
record "F: Birthdays right after the enable, before any contacts change" "[$(cbirthdays_cal)]"
F2_MARK="$(ring_mark)"
cbirthday "$ANN" "1990-$MD"
for i in 1 2 3 4 5 6 7 8; do sleep 1; [ -n "$(cbirthdays_cal)" ] && break; done
B2="$(cbirthdays_cal)"
assert_ne "F: the next contacts change creates Tessera Birthdays again" "" "$B2"
assert_contains "F: … under its own account, access 200" "account_name=Tessera Birthdays, account_type=LOCAL, calendar_displayName=Birthdays, calendar_access_level=200" "$(cals | grep "_id=${B2:-x},")"
sleep 2
assert_eq "F: … holding the birthdays the contacts carry now (Ann's and Bob's)" "2" "$(bevents "${B2:-0}" | grep -c 'birthday,')"
assert_eq "F: Tessera is still the one calendar of its account (a LOCAL calendar survives the provider's restart)" "1" "$(cals | grep -cF 'account_name=Tessera,')"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore (r3 V10)"
adb shell pm enable "$CAL_PROVIDER" > /dev/null 2>&1
assert_eq "restore: pm enable com.android.providers.calendar (asserted enabled)" "1" "$(provider_enabled)"
for r in "$ANN" "$BOB" "$ZOE"; do rm_birthday "$r"; done
assert_eq "restore: the birthday data rows are removed" "0" "$(cbirthdays_on_phone)"
sleep 2
c6
cal_fixtures_down
cpurge "title='standup'"
assert_eq "restore: Tess's added event is deleted" "0" "$(cevent_count "title='standup'")"
people_fixtures_down
assert_eq "restore: no new crash of the shell during the row" "$CRASH0" "$(ccrashes)"
ensure_start
row_end
