#!/usr/bin/env bash
# Phase 16 E24 — Sync rule 3, the tapped push (T16-1), and one event shown once (Q-16-2).
#
#   fixtures  Personal then Work, "Offsite" in Work five days ahead, Personal ticked on "Can sync to" (E23's steps, run
#             here), no other event in the next two days, a local "Standup" 2 h from the device's now, 1 h long, with a
#             10-minute reminder. Before the Sync: the feed's last refresh line reads faces=2 agenda=1; shell_bytes read.
#   S   the Sync        Personal holds ONE event (the copy, the local start and end), Work exactly Offsite, the marker
#                       "synced to Personal" with its account beside it, the mapping in calendar_sync.json, the `ok`
#                       line, the same rx / tx bytes as before
#   H   hidden          Agenda, Day and Week show Standup once (the local id's) and no cal_event:<copy>; the next refresh
#                       line still faces=2 agenda=1; the pod bay's pod_row:agenda:0 reads Standup and there is no
#                       pod_row:agenda:1; Tess's typed "what is on my calendar" names Standup once
#   N   one reminder    jump_clock (FORWARDS) to 5 s before start − 10 min → exactly ONE notification of the shell's,
#                       `notified` once for the local id, `skipped (synced copy)` for the copy's own alert
#   U   re-Sync         renamed → `updated`; the copy deleted on the other side → `recreated`, the notice says so; the
#                       copy's title changed on the other side → `updated`, the local title again; nothing changed → no
#                       write, `ok`
#   W   a series        COUNT=5, its third occurrence retitled, a 10-minute reminder → a master with the rrule and
#                       duration, one exception whose original_id is the COPY's master, a reminders row; shown once
#   D   delete          here / both offered; "here" keeps the copy, which then shows as Personal's event; "both" removes
#                       both; with Personal un-ticked only "here" is offered (T16-12)
#   G   calendar gone   Personal removed → Sync says "That calendar is no longer on this phone", `failed calendar gone`,
#                       the marker keeps its warning glyph
#   Work holds exactly "Offsite" after every step (cal_lists first).
#   restore   as E22, clock_restore, then pm clear → provision.sh → ensure_start (clears calendar_sync.json)
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"

row_begin E24 "Sync rule 3: the tapped push, the copy hidden everywhere, one reminder, re-Sync, a series, the deletes"
sync_line() { cline "$1" "[calendar] sync event=" | sed 's/^\[calendar\] //'; }
# Sync on an already-synced event (it pushes straight to its mapped calendar): prints the sync line; the page's dump is kept.
resync() { # event-id label [begin end]
  copen_event "$1" ${3:-} ${4:-}
  RMARK="$(ring_mark)"
  ctap cal_event_action:sync 3 || return 1
  dump_ui "$ROW_DIR/U-$2.xml"
  ring_since "$RMARK" > "$ROW_DIR/U-$2-slice.txt"
  sync_line "$(cat "$ROW_DIR/U-$2-slice.txt")"
}
# A first Sync with Personal allowed: the picker, then its one target.
sync_new() { # event-id [begin end]
  copen_event "$1" ${2:-} ${3:-}
  ctap cal_event_action:sync 2 && ctap "cal_sync_target:$PERSONAL" 3
}
# How many cal_event_title nodes of a dump read a text; and which ids they carry.
titled() { python3 -c '
import sys, xml.etree.ElementTree as ET
ids = [n.get("resource-id")[len("cal_event_title:"):] for n in ET.parse(sys.argv[1]).getroot().iter("node")
       if (n.get("resource-id") or "").startswith("cal_event_title:") and n.get("text") == sys.argv[2]]
print(len(ids), " ".join(ids))' "$1" "$2"; }

c6; ensure_start
cal_fixtures_down
copen
TESS="$(tessera_id)"
assert_ne "precondition: Tessera exists" "" "$TESS"
BEFORE="$(ctessera_count)"

# ----------------------------------------------------------------------------------------------- fixtures
log "--- fixtures"
csync_fixtures_up 5
assert_ne "fixtures: Personal exists" "" "$PERSONAL"
assert_ne "fixtures: Work exists, created after Personal" "" "$WORK"
assert_ne "fixtures: \"Offsite\" in Work five days ahead" "" "$OFFSITE"
D0="$(cday_ms 0 00:00)"
assert_eq "fixtures: no other event in the next two days (the provider's instances)" "0" "$(call_instances "$D0" $(( D0 + 2 * 86400000 )))"
NOW="$(device_ms)"; START=$(( (NOW / 60000 + 120) * 60000 )); END=$(( START + 3600000 ))
STANDUP="$(cmkevent "$TESS" Standup "$START" "$END")"
cmkreminder "$STANDUP" 10
assert_ne "fixtures: a local event \"Standup\" starting 2 hours from the device's now, 1 hour long" "" "$STANDUP"
assert_contains "fixtures: … with a 10-minute reminder" "minutes=10" "$(q "content query --uri $REMINDERS --projection event_id:minutes:method --where \"event_id=$STANDUP\"")"
note "tessera=$TESS personal=$PERSONAL work=$WORK offsite=$OFFSITE standup=$STANDUP start=$START"
# E23's steps: the first Sync routes to "Can sync to"; Personal ticked; Back lands on the picker.
copen_event "$STANDUP"
ctap cal_event_action:sync 2; dump_ui "$ROW_DIR/fix-can-sync.xml"
assert_eq "fixtures: the first Sync opens \"Can sync to\" (nothing allowed yet)" "yes" "$(has_node "$ROW_DIR/fix-can-sync.xml" cal_can_sync)"
cset_can_sync "$PERSONAL" true; dump_ui "$ROW_DIR/fix-ticked.xml"
assert_eq "fixtures: Personal ticked on \"Can sync to\"" "true" "$(cattr "$ROW_DIR/fix-ticked.xml" "cal_settings_can_sync:$PERSONAL" checked)"
assert_eq "fixtures: … Work left unticked" "false" "$(cattr "$ROW_DIR/fix-ticked.xml" "cal_settings_can_sync:$WORK" checked)"
cback 1.8; dump_ui "$ROW_DIR/fix-picker.xml"
assert_eq "fixtures: Back lands on the Sync picker, Personal its only target" "cal_sync_target:$PERSONAL" "$(cids "$ROW_DIR/fix-picker.xml" cal_sync_target:)"
sleep 1
PRE="$(ring_since "$ROW_MARK")"
log "the feed before the Sync: $(cline "$PRE" '[calendar] refresh (')"
assert_eq "before the Sync: the last [calendar] refresh line reads faces=2 agenda=1" "faces=2 agenda=1" "$(cfeed "$PRE")"
assert_contains "before the Sync: … with access=true" "access=true" "$(cline "$PRE" '[calendar] refresh (')"
BYTES0="$(shell_bytes)"; note "shell_bytes before the Sync (rx tx): $BYTES0"
cal_lists "$PERSONAL" Personal
assert_eq "before the Sync: Personal holds nothing" "" "$(ctitles "$PERSONAL")"

# ----------------------------------------------------------------------------------------------- S: the Sync
log "--- S: Sync \"Standup\""
S_MARK="$(ring_mark)"
ctap "cal_sync_target:$PERSONAL" 3; dump_ui "$ROW_DIR/S-synced.xml"; screencap "$ROW_DIR/S-synced.png"
S_SLICE="$(ring_since "$S_MARK")"; printf '%s\n' "$S_SLICE" > "$ROW_DIR/S-slice.txt"
cal_lists "$PERSONAL" Personal
assert_eq "S: Personal holds ONE event, titled Standup (the copy exists)" "Standup" "$(ctitles "$PERSONAL")"
COPY="$(cevent_ids "calendar_id=$PERSONAL AND deleted=0")"
assert_eq "S: … its dtstart / dtend equal the local event's" "$(cevents dtstart:dtend "_id=$STANDUP" | sed 's/^Row: 0 //')" "$(cevents dtstart:dtend "_id=${COPY:-0}" | sed 's/^Row: 0 //')"
cwork_offsite "S: after the Sync"
D="$ROW_DIR/S-synced.xml"
assert_eq "S: the local event's page is showing" "yes" "$(has_node "$D" "cal_event_page:$STANDUP")"
assert_eq "S: cal_synced_marker:<id> reads \"synced to Personal\"" "synced to Personal" "$(ctext "$D" "cal_synced_marker:$STANDUP")"
assert_eq "S: … with cal_account:qa.personal@example.com on the page" "yes" "$(has_node "$D" "cal_account:$PERSONAL_ACCT")"
BESIDE="$(python3 - "$D" "cal_synced_marker:$STANDUP" "cal_account:$PERSONAL_ACCT" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
def b(rid):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(rid), xml)
    return [int(x) for x in m.groups()] if m else None
m, a = b(sys.argv[2]), b(sys.argv[3])
print("yes" if m and a and a[0] >= m[2] and min(m[3], a[3]) - max(m[1], a[1]) > 0 else "no", m, a)
PY
)"; note "marker and account bounds: $BESIDE"
assert_eq "S: … beside it (to the marker's right, on its line; r3 D5)" "yes" "${BESIDE%% *}"
J="$(csync_json)"; log "calendar_sync.json: $J"
assert_contains "S: calendar_sync.json maps the local id to Personal's _ID and the copy's id" "{\"id\":$PERSONAL,\"accountName\":\"$PERSONAL_ACCT\",\"accountType\":\"com.google\",\"local\":$STANDUP,\"copy\":$COPY}" "$J"
assert_contains "S: diagnostics — sync event=<id> -> calendar <personal id>: ok" "[calendar] sync event=$STANDUP -> calendar $PERSONAL: ok" "$S_SLICE"
BYTES1="$(shell_bytes)"; note "shell_bytes after the Sync (rx tx): $BYTES1"
assert_eq "S: shell_bytes reads the same rx and tx as before the Sync" "$BYTES0" "$BYTES1"
COPY_REM="$(q "content query --uri $REMINDERS --projection event_id:minutes:method --where \"event_id=${COPY:-0}\"")"
assert_contains "S: the copy has its own reminder row (asserted from reminders)" "event_id=$COPY, minutes=10" "$COPY_REM"

# ----------------------------------------------------------------------------------------------- H: hidden everywhere
log "--- H: the copy is hidden everywhere in the shell (Q-16-2; r3 D2)"
copen; ctap cal_bar:today 1.2; cview agenda 2.5; dump_ui "$ROW_DIR/H-agenda.xml"
assert_eq "H: Agenda is showing" "true" "$(cattr "$ROW_DIR/H-agenda.xml" cal_view_mode:agenda selected)"
assert_eq "H: the Agenda dump holds exactly ONE cal_event_title: node reading \"Standup\", the local id's" "1 $STANDUP" "$(titled "$ROW_DIR/H-agenda.xml" Standup)"
assert_eq "H: … and no cal_event:<copy id> node" "no" "$(has_node "$ROW_DIR/H-agenda.xml" "cal_event:$COPY")"
copen_day "$START"; dump_ui "$ROW_DIR/H-day.xml"
assert_eq "H: Day is showing" "true" "$(cattr "$ROW_DIR/H-day.xml" cal_view_mode:day selected)"
assert_eq "H: the Day view the same — one Standup, the local id's" "1 $STANDUP" "$(titled "$ROW_DIR/H-day.xml" Standup)"
assert_eq "H: … and no cal_event:<copy id> node" "no" "$(has_node "$ROW_DIR/H-day.xml" "cal_event:$COPY")"
cview week 2.5; dump_ui "$ROW_DIR/H-week.xml"
assert_eq "H: Week is showing" "true" "$(cattr "$ROW_DIR/H-week.xml" cal_view_mode:week selected)"
assert_eq "H: the Week view the same — one Standup, the local id's" "1 $STANDUP" "$(titled "$ROW_DIR/H-week.xml" Standup)"
assert_eq "H: … and no cal_event:<copy id> node" "no" "$(has_node "$ROW_DIR/H-week.xml" "cal_event:$COPY")"
POST="$(ring_since "$S_MARK")"
log "the feed after the Sync: $(cline "$POST" '[calendar] refresh (')"
assert_contains "H: a [calendar] refresh line follows the Sync" "[calendar] refresh (" "$POST"
assert_eq "H: … it still reads faces=2 agenda=1 (the tile and the feed's agenda gained nothing)" "faces=2 agenda=1" "$(cfeed "$POST")"
c6
cpod_bay "$ROW_DIR/H-bay.xml"; screencap "$ROW_DIR/H-bay.png"
assert_eq "H: the pod bay is showing, its Agenda pod on screen" "yes yes" "$(has_node "$ROW_DIR/H-bay.xml" pod_bay) $(has_node "$ROW_DIR/H-bay.xml" pod:agenda)"
assert_contains "H: pod_row:agenda:0 reads Standup" "Standup" "$(ctexts "$ROW_DIR/H-bay.xml" pod_row:agenda:0)"
assert_eq "H: … and there is no pod_row:agenda:1" "no" "$(has_node "$ROW_DIR/H-bay.xml" pod_row:agenda:1)"
tess_ask "what is on my calendar" 5
TREPLY="$(reply_since "$TMARK")"; log "Tess: [$TREPLY]"
assert_eq "H: Tess's typed \"what is on my calendar\" names Standup once (occurrences in reply_since)" "1" "$(printf '%s' "$TREPLY" | grep -o 'Standup' | wc -l | tr -d ' ')"
ring_since "$TMARK" > "$ROW_DIR/H-tess-slice.txt"
tess_close
cwork_offsite "H: after the reads"

# ----------------------------------------------------------------------------------------------- N: one reminder
log "--- N: one event, one reminder (jump_clock forwards to 5 s before start − 10 min)"
assert_eq "N: no calendar notification of the shell's before the time" "" "$(cnotes)"
jump_clock $(( START - 600000 - 5000 )) > "$ROW_DIR/N-jump.txt"
N_MARK="$(ring_mark)"
for i in $(seq 1 10); do [ -n "$(cnotes)" ] && break; sleep 1; done
note "the shell's first calendar notification showed after $i s"
sleep 4
NOTES="$(cnotes)"; log "the shell's calendar notifications: $(echo "$NOTES" | tr '\n' ';')"
N_SLICE="$(csince "$N_MARK")"; printf '%s\n' "$N_SLICE" > "$ROW_DIR/N-slice.txt"
assert_within "N: the notification showed within 10 s" 5 "$i" 5
assert_eq "N: dumpsys notification holds exactly ONE notification of the shell's" "1" "$(printf '%s\n' "$NOTES" | grep -c 'title=')"
assert_contains "N: … titled Standup" "title=[Standup]" "$NOTES"
assert_eq "N: the slice holds reminder event=<local id> minutes=10: notified — once" "1" "$(clines "$N_SLICE" "[calendar] reminder event=$STANDUP minutes=10: notified")"
assert_contains "N: … and reminder event=<copy id> minutes=10: skipped (synced copy)" "[calendar] reminder event=$COPY minutes=10: skipped (synced copy)" "$N_SLICE"
ALR="$(q "content query --uri $ALERTS --projection event_id:state --where \"event_id IN ($STANDUP,$COPY)\"" | sed 's/^Row: [0-9]* //' | sort | tr '\n' ';')"
assert_contains "N: the copy's alert did fire (it has an alert row of its own)" "event_id=$COPY, state=" "$ALR"
record "N: the two alert rows" "$ALR"
record "N: the AOSP Calendar's own notifications at this point (another app reads the provider unfiltered)" "$(cnotes_of "$AOSP_CAL" | tr '\n' ';')"
sleep 5; adb shell cmd statusbar collapse >/dev/null 2>&1
cwork_offsite "N: after the reminder"

# ----------------------------------------------------------------------------------------------- U: re-Sync
log "--- U: a later Sync updates the copy"
copen_event "$STANDUP"; ctap cal_event_action:edit 2
ctype_field title " 2"
ctap cal_editor_save 2.5
assert_eq "U: the local event is renamed \"Standup 2\"" "title=Standup 2" "$(cevents title "_id=$STANDUP" | sed 's/^Row: 0 //')"
L="$(resync "$STANDUP" renamed)"; log "sync: $L"
assert_contains "U: Sync → updated" "sync event=$STANDUP -> calendar $PERSONAL: updated" "$L"
cal_lists "$PERSONAL" Personal
assert_eq "U: … the copy reads Standup 2, still one row" "Standup 2" "$(ctitles "$PERSONAL")"
assert_eq "U: … the same copy row" "$COPY" "$(cevent_ids "calendar_id=$PERSONAL AND deleted=0")"
cwork_offsite "U: after the renamed Sync"

cother_side "$PERSONAL_ACCT" delete "$COPY"
cal_lists "$PERSONAL" Personal
assert_eq "U: the copy deleted on the other side (sync-adapter delete on Personal)" "" "$(ctitles "$PERSONAL")"
L="$(resync "$STANDUP" recreated)"; log "sync: $L"
assert_contains "U: Sync → recreated" "sync event=$STANDUP -> calendar $PERSONAL: recreated" "$L"
cal_lists "$PERSONAL" Personal
assert_eq "U: … one copy again" "Standup 2" "$(ctitles "$PERSONAL")"
NOTICE="$(ctext "$ROW_DIR/U-recreated.xml" cal_notice)"; record "U: the recreated notice's wording (H20)" "$NOTICE"
assert_eq "U: … and the notice says so (cal_notice names the copy made again)" "yes" "$(printf '%s' "$NOTICE" | grep -Eiq 'again|recreat|new copy' && echo yes || echo no)"
COPY2="$(cevent_ids "calendar_id=$PERSONAL AND deleted=0")"
cwork_offsite "U: after the recreated Sync"

cother_side "$PERSONAL_ACCT" update "$COPY2" "--bind title:s:'Edited elsewhere'"
assert_eq "U: the copy's title changed on the other side (sync-adapter update)" "Edited elsewhere" "$(ctitles "$PERSONAL")"
L="$(resync "$STANDUP" otherside)"; log "sync: $L"
assert_contains "U: Sync → the line reads updated (r3 V13)" "sync event=$STANDUP -> calendar $PERSONAL: updated" "$L"
cal_lists "$PERSONAL" Personal
assert_eq "U: … the copy's title is the local title again" "Standup 2" "$(ctitles "$PERSONAL")"
cwork_offsite "U: after the other side's edit was overwritten"

# Nothing changed on either side. The copy's dirty flag is cleared as its account's adapter would after an upload: a
# normal app's write would set it again, so "no write" is read from the provider, not from the app's line alone.
cother_side "$PERSONAL_ACCT" update "$COPY2" "--bind dirty:i:0"
ROW0="$(cevents title:dtstart:dtend:dirty:calendar_id "_id=$COPY2" | sed 's/^Row: 0 //')"
assert_contains "U: the copy's dirty flag is 0 before a Sync with nothing changed" "dirty=0" "$ROW0"
L="$(resync "$STANDUP" same)"; log "sync: $L"
assert_contains "U: a Sync with nothing changed on either side → ok" "sync event=$STANDUP -> calendar $PERSONAL: ok" "$L"
assert_eq "U: … no write: the copy's row is as it was, dirty still 0" "$ROW0" "$(cevents title:dtstart:dtend:dirty:calendar_id "_id=$COPY2" | sed 's/^Row: 0 //')"
absent_in "U: … and no [calendar] write line in the Sync's slice" "[calendar] write " "$(cat "$ROW_DIR/U-same-slice.txt")"
cwork_offsite "U: after the unchanged Sync"

# ----------------------------------------------------------------------------------------------- W: a weekly series
log "--- W: a weekly local series (COUNT=5), its third occurrence retitled, a 10-minute reminder"
S0="$(clocal_ms "$(cdate 1)" 09:00)"; WEEK=$(( 7 * 86400000 ))
WK="$(cmkseries "$TESS" 'E24 weekly' "$S0" 'FREQ=WEEKLY;COUNT=5' PT1H)"
cmkreminder "$WK" 10
cinstance_times "$S0" $(( S0 + 6 * WEEK )) "$WK" > "$ROW_DIR/W-instances.txt"
assert_eq "W: the local series expands to five instances" "5" "$(wc -l < "$ROW_DIR/W-instances.txt" | tr -d ' ')"
read -r WB1 WE1 <<< "$(sed -n 1p "$ROW_DIR/W-instances.txt")"
read -r WB3 WE3 <<< "$(sed -n 3p "$ROW_DIR/W-instances.txt")"
copen_event "$WK" "$WB3" "$WE3"
ctap cal_event_action:edit 1.5; ctap cal_occurrence:this 2
ctype_field title "X"
ctap cal_editor_save 3
LEX="$(cevent_ids "original_id=$WK")"
assert_ne "W: the third occurrence is retitled (a local exception row)" "" "$LEX"
sync_new "$WK" "$WB1" "$WE1"
W_SLICE="$(ring_since "$ROW_MARK")"
ROWS="$(cevents _id:title:rrule:duration:original_id:originalInstanceTime "calendar_id=$PERSONAL AND deleted=0 AND title LIKE 'E24 weekly%'")"; log "Personal's rows for the series: $ROWS"
CM="$(printf '%s\n' "$ROWS" | grep 'rrule=FREQ=WEEKLY' | sed -n 's/^Row: [0-9]* _id=\([0-9]*\),.*/\1/p' | head -1)"
assert_ne "W: Personal holds a master with the rrule" "" "$CM"
MROW="$(printf '%s\n' "$ROWS" | grep -E "^Row: [0-9]+ _id=${CM:-x},")"
assert_contains "W: … the rrule as the local one (FREQ=WEEKLY;COUNT=5)" "rrule=FREQ=WEEKLY;COUNT=5" "$MROW"
assert_eq "W: … and the duration (not NULL)" "yes" "$(printf '%s' "$MROW" | grep -Eq 'duration=P[^,]+' && echo yes || echo no)"
EXROWS="$(printf '%s\n' "$ROWS" | grep -Ev "^Row: [0-9]+ _id=${CM:-x}," | grep '_id=')"
assert_eq "W: … one exception" "1" "$(printf '%s\n' "$EXROWS" | grep -c '_id=')"
assert_contains "W: … whose original_id is the copy's master" "original_id=$CM," "$EXROWS"
CEX="$(printf '%s\n' "$EXROWS" | sed -n 's/^Row: [0-9]* _id=\([0-9]*\),.*/\1/p' | head -1)"
assert_contains "W: … and a reminders row of 10 minutes on the copy" "event_id=$CM, minutes=10" "$(q "content query --uri $REMINDERS --projection event_id:minutes --where \"event_id=${CM:-0}\"")"
# The trust review's record (fix-round.md F26): what the provider's own Instances holds for the COPY in Personal over
# the series' weeks. The shell cannot give an account copy a _sync_id, and on this provider a series with an exception
# and no _sync_id expands wrongly, so another calendar app may see the copy's series short until the account's own
# adapter syncs. Recorded, not asserted: the row's assertions are about what the shell shows.
CINST="$(q "content query --uri $INSTANCES/$S0/$(( S0 + 6 * WEEK )) --projection event_id:begin:title --where \"calendar_id=$PERSONAL\"" | grep 'event_id=' | sed 's/^Row: [0-9]* //')"
printf '%s\n' "$CINST" > "$ROW_DIR/W-copy-instances.txt"
record "W: the provider's instances of the COPY in Personal over the series' weeks — count (the local series has 5)" "$(printf '%s\n' "$CINST" | grep -c 'event_id=')"
record "W: … their event ids and titles" "$(printf '%s\n' "$CINST" | grep 'E24 weekly' | sed 's/, begin=[0-9]*//' | sort | uniq -c | tr '\n' ';')"
copen; ctap cal_bar:today 1.2; cview agenda 2.5
cagenda_walk "$ROW_DIR/W-agenda.tsv"
log "the Agenda's rows titled E24 weekly…: $(awk -F'\t' '$3 ~ /^E24 weekly/ {printf "%s=%s(%s) ", $1, $3, $2}' "$ROW_DIR/W-agenda.tsv")"
ONCE=0
while read -r b e; do
  d="$(cdate_of "$b")"
  n="$(awk -F'\t' -v d="$d" '$1 == d && $3 ~ /^E24 weekly/' "$ROW_DIR/W-agenda.tsv" | wc -l | tr -d ' ')"
  [ "$n" = 1 ] && ONCE=$((ONCE + 1)) || note "the occurrence on $d shows $n time(s)"
done < "$ROW_DIR/W-instances.txt"
assert_eq "W: the views show each occurrence once (five days, one row each, in the Agenda)" "5" "$ONCE"
assert_eq "W: … the copy's master and its exception are both hidden (no row with their ids)" "0" "$(awk -F'\t' -v a="$CM" -v b="$CEX" '$2 == a || $2 == b' "$ROW_DIR/W-agenda.tsv" | wc -l | tr -d ' ')"
copen_day "$WB3"; dump_ui "$ROW_DIR/W-day3.xml"
assert_eq "W: … the third occurrence's Day view holds one event row, the local exception's" "cal_event:$LEX" "$(cids "$ROW_DIR/W-day3.xml" cal_event: | tr ' ' '\n' | grep -E '^cal_event:[0-9]+$' | paste -sd' ')"
cwork_offsite "W: after the series' Sync"

# ----------------------------------------------------------------------------------------------- D: the deletes
log "--- D: deleting a synced local event"
copen_event "$STANDUP"
ctap cal_event_action:delete 1.5; dump_ui "$ROW_DIR/D-choice.xml"; screencap "$ROW_DIR/D-choice.png"
assert_eq "D: the delete shows cal_delete_choice:here" "yes" "$(has_node "$ROW_DIR/D-choice.xml" cal_delete_choice:here)"
assert_eq "D: … and cal_delete_choice:both" "yes" "$(has_node "$ROW_DIR/D-choice.xml" cal_delete_choice:both)"
HB="$(bounds "$ROW_DIR/D-choice.xml" cal_delete_choice:here)"; BB="$(bounds "$ROW_DIR/D-choice.xml" cal_delete_choice:both)"
assert_eq "D: … \"here\" is the default: the first of the two choices" "yes" "$([ -n "$HB" ] && [ -n "$BB" ] && [ "$(echo "$HB" | cut -d' ' -f2)" -lt "$(echo "$BB" | cut -d' ' -f2)" ] && echo yes || echo no)"
record "D: the two choices' wording" "$(ctexts "$ROW_DIR/D-choice.xml" cal_delete_choice:here) / $(ctexts "$ROW_DIR/D-choice.xml" cal_delete_choice:both)"
ctap cal_delete_choice:here 2.5
assert_eq "D: \"here\" → the local event is gone" "0" "$(cevent_count "_id=$STANDUP AND deleted=0")"
cal_lists "$PERSONAL" Personal
assert_contains "D: … the copy is kept" "|Standup 2|" "|$(ctitles "$PERSONAL")|"
assert_absent "D: … its mapping is gone from calendar_sync.json" "\"local\":$STANDUP," "$(csync_json)"
copen_day "$START"; dump_ui "$ROW_DIR/D-day-after.xml"
assert_eq "D: … the copy now SHOWS in the views as Personal's event (cal_event:<copy id> present)" "yes" "$(has_node "$ROW_DIR/D-day-after.xml" "cal_event:$COPY2")"
ctap "cal_event:$COPY2" 2; dump_ui "$ROW_DIR/D-copy-page.xml"
assert_eq "D: … its cal_event_page: is open" "yes" "$(has_node "$ROW_DIR/D-copy-page.xml" "cal_event_page:$COPY2")"
assert_eq "D: … and has no cal_event_action:* (the original no longer exists)" "0" "$(ccount_prefix "$ROW_DIR/D-copy-page.xml" cal_event_action:)"
cback
cwork_offsite "D: after delete here"

SECOND="$(cmkevent "$TESS" 'E24 second' "$(clocal_ms "$(cdate 1)" 13:00)" "$(clocal_ms "$(cdate 1)" 14:00)")"
sync_new "$SECOND"
C2ND="$(cevent_ids "calendar_id=$PERSONAL AND deleted=0 AND title='E24 second'")"
assert_ne "D: a second synced event (its copy is in Personal)" "" "$C2ND"
copen_event "$SECOND"; ctap cal_event_action:delete 1.5; ctap cal_delete_choice:both 2.5
assert_eq "D: \"both\" → the local event is gone" "0" "$(cevent_count "_id=$SECOND AND deleted=0")"
assert_eq "D: … and the copy is gone" "0" "$(cevent_count "_id=${C2ND:-0} AND deleted=0")"
assert_absent "D: … and its mapping" "\"local\":$SECOND," "$(csync_json)"
cwork_offsite "D: after delete both"

THIRD="$(cmkevent "$TESS" 'E24 third' "$(clocal_ms "$(cdate 1)" 15:00)" "$(clocal_ms "$(cdate 1)" 16:00)")"
sync_new "$THIRD"
C3RD="$(cevent_ids "calendar_id=$PERSONAL AND deleted=0 AND title='E24 third'")"
assert_ne "D: a third synced event (its copy is in Personal)" "" "$C3RD"
copen; copen_can_sync; cset_can_sync "$PERSONAL" false; dump_ui "$ROW_DIR/D-unticked.xml"
assert_eq "D: Personal then un-ticked in \"Can sync to\"" "false" "$(cattr "$ROW_DIR/D-unticked.xml" "cal_settings_can_sync:$PERSONAL" checked)"
copen_event "$THIRD"; ctap cal_event_action:delete 1.5; dump_ui "$ROW_DIR/D-unticked-choice.xml"
assert_eq "D: the delete prompt is up (cal_delete)" "yes" "$(has_node "$ROW_DIR/D-unticked-choice.xml" cal_delete)"
assert_eq "D: … it offers cal_delete_choice:here" "yes" "$(has_node "$ROW_DIR/D-unticked-choice.xml" cal_delete_choice:here)"
assert_eq "D: … and NO cal_delete_choice:both (T16-12)" "no" "$(has_node "$ROW_DIR/D-unticked-choice.xml" cal_delete_choice:both)"
ctap cal_delete_cancel 1.5
assert_eq "D: … nothing was deleted by looking" "1 1" "$(cevent_count "_id=$THIRD AND deleted=0") $(cevent_count "_id=${C3RD:-0} AND deleted=0")"
copen; copen_can_sync; cset_can_sync "$PERSONAL" true; dump_ui "$ROW_DIR/D-reticked.xml"
assert_eq "D: Personal re-ticked" "true" "$(cattr "$ROW_DIR/D-reticked.xml" "cal_settings_can_sync:$PERSONAL" checked)"
cwork_offsite "D: after the un-ticked delete prompt"

# ----------------------------------------------------------------------------------------------- G: the calendar gone
log "--- G: Personal removed from the phone"
crmcal "$PERSONAL_ACCT" com.google; sleep 1.5
assert_eq "G: Personal is removed (sync-adapter delete of the calendar)" "" "$(cal_id "$PERSONAL_ACCT")"
L="$(resync "$THIRD" gone)"; log "sync: $L"
assert_eq "G: Sync on a synced event → cal_notice \"That calendar is no longer on this phone\"" "That calendar is no longer on this phone" "$(ctext "$ROW_DIR/U-gone.xml" cal_notice)"
assert_eq "G: the marker stays (cal_synced_marker:<id>)" "yes" "$(has_node "$ROW_DIR/U-gone.xml" "cal_synced_marker:$THIRD")"
assert_eq "G: … and keeps a warning glyph (cal_synced_warning)" "yes" "$(has_node "$ROW_DIR/U-gone.xml" cal_synced_warning)"
assert_contains "G: the line — failed calendar gone" "failed calendar gone" "$L"
cwork_offsite "G: at the end"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore (as E22, clock_restore, then pm clear → provision.sh → ensure_start)"
c6
cal_fixtures_down
cpurge "title IN ('Standup','Standup 2','E24 second','E24 third') OR title LIKE 'E24 weekly%'"
assert_eq "restore: the test events are deleted" "0" "$(cevent_count "title IN ('Standup','Standup 2','E24 second','E24 third') OR title LIKE 'E24 weekly%'")"
assert_eq "restore: Tessera's event count equals the count before the row" "$BEFORE" "$(ctessera_count)"
ring_save
clock_restore
ring_save
assert_eq "restore: pm clear → provision.sh rc" "0" "$(cprovision restore)"
ensure_start
assert_absent "restore: calendar_sync.json holds no mapping and nothing allowed after the clear" "accountName" "$(csync_json)"
assert_eq "restore: no calendar notification of the shell's is left" "" "$(cnotes)"
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline (the row cleared the shell)" "0" "$?"
ensure_start
row_end
