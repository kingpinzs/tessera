#!/usr/bin/env bash
# Phase 16 E25 — App Shortcuts of BOTH apps (build task 9; phase 11 Q1's standing rule, C-8), clause by clause.
#
#   start      layout_restore of the baseline (the CALENDAR and PEOPLE slot tiles resolve to the shell's apps),
#              ensure_start; every dump of Start is gdump (the People tile may be cycling — r3 V9)
#   CALENDAR   the tile held by phase 11 E3's method → quick_sat_label:0..3 = Agenda / Day / Month / New event
#   PEOPLE     the tile held → quick_sat_label:0..2 = Contacts / New contact / Groups, no quick_sat_label:3
#   ring       each burst's slice holds phase 11's activity-keyed line naming only that activity's ids:
#              `[quick] shortcuts for app.tileshell/.calendar.CalendarActivity/0: 4 (4 shown: agenda,day,month,new_event)`
#              and `… /.people.PeopleActivity/0: 3 (3 shown: contacts,new_contact,groups)`
#   taps       tap_node quick_sat:<i> for each → the app resumed (dumpsys activity activities) with
#              cal_view_mode:agenda / cal_view_mode:day selected; for Month cal_view_mode:agenda selected AND
#              cal_month_dropdown shown; cal_editor for New event (Back discards it; Tessera's event count unchanged);
#              people_page:list; people_page:editor for New contact (Back discards it; raw_contacts count unchanged);
#              people_pivot:groups selected for Groups; c6 + ensure_start after each launch (C-6)
#   dumpsys    `adb shell dumpsys shortcut` lists the seven ids for app.tileshell with ranks 0–3 per activity
#   restore    layout_restore of the baseline
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"
CAL_TILE="tile:slot:CALENDAR"; PPL_TILE="tile:slot:PEOPLE"
# Phase 11 E3's method (q.sh's hold_at): the tile's centre held 1.0 s with the finger still down, the dump taken while
# it is down, then released; the burst stays open for the tap on a satellite.
burst_on() { # tile-id tag
  local b x y
  gdump "$D/$2-rest.xml" || true
  b="$(bounds "$D/$2-rest.xml" "$1")"
  [ -n "$b" ] || { note "burst_on: no $1 on Start"; return 1; }
  # shellcheck disable=SC2086
  set -- "$1" "$2" $b
  x=$(( ($3 + $5) / 2 )); y=$(( ($4 + $6) / 2 ))
  adb shell input motionevent DOWN "$x" "$y"; sleep 1.0
  gdump "$D/$2.xml" || true; screencap "$D/$2.png"
  adb shell input motionevent UP "$x" "$y"; sleep 0.8
}
labels() { # dump -> the quick_sat_label texts in index order, comma-separated
  local i out=""
  for i in 0 1 2 3; do [ "$(has_node "$1" "quick_sat_label:$i")" = yes ] && out="$out$(node_text "$1" "quick_sat_label:$i"),"; done
  echo "${out%,}"
}
quick_line() { ring_since "$1" | grep -F '[quick] shortcuts for' | tail -1 | sed 's/.*\[quick\]/[quick]/'; }
tess_events() { cal_events "$1" | grep -c '|'; }
# One satellite: open the burst, assert its labels and its ring line, tap satellite i, wait for the app.
launch() { # tile tag index expected-labels expected-line
  local mark
  mark="$(ring_mark)"
  burst_on "$1" "$2" || { _verdict FAIL "$2: the burst opened" "the tile was not found on Start"; return 1; }
  assert_eq "$2: the burst's labels in rank order" "$4" "$(labels "$D/$2.xml")"
  assert_eq "$2: the burst's ring line names only this activity's ids (T11-12)" "$5" "$(quick_line "$mark")"
  assert_eq "$2: quick_sat:$3 is in the burst" "yes" "$(has_node "$D/$2.xml" "quick_sat:$3")"
  tap_node "$D/$2.xml" "quick_sat:$3"; sleep 4
  adb shell dumpsys activity activities | tr -d '\r' | grep -m1 'topResumedActivity' > "$D/$2-resumed.txt"
  dump_ui "$D/$2-app.xml"; screencap "$D/$2-app.png"
}
selected() { node_attr "$1" "$2" selected; }

row_begin E25 "App Shortcuts: the Calendar and People tiles' bursts, each satellite's page, dumpsys shortcut"
require_build
D="$ROW_DIR"
layout_restore "$BASELINE"; assert_eq "layout_restore of the baseline" "0" "$?"
ensure_start
assert_eq "the baseline's CALENDAR slot is the shell's Calendar" "app.tileshell/app.tileshell.calendar.CalendarActivity" "$(layout_json | python3 -c 'import json,sys; print(json.load(sys.stdin).get("slots",{}).get("CALENDAR",""))')"
assert_eq "the baseline's PEOPLE slot is the shell's People" "app.tileshell/app.tileshell.people.PeopleActivity" "$(layout_json | python3 -c 'import json,sys; print(json.load(sys.stdin).get("slots",{}).get("PEOPLE",""))')"
RAW0="$(raw_count)"
CAL_LABELS="Agenda,Day,Month,New event"; PPL_LABELS="Contacts,New contact,Groups"
CAL_LINE="[quick] shortcuts for $CALENDAR_ACTIVITY/0: 4 (4 shown: agenda,day,month,new_event)"
PPL_LINE="[quick] shortcuts for $PEOPLE_ACTIVITY/0: 3 (3 shown: contacts,new_contact,groups)"

# ------------------------------------------------------------------------------------------------ Calendar
log "--- the CALENDAR tile's burst: Agenda, Day, Month, New event"
launch "$CAL_TILE" cal-0-agenda 0 "$CAL_LABELS" "$CAL_LINE"
assert_eq "Agenda: the shell's Calendar is resumed (dumpsys activity activities)" "$CALENDAR_ACTIVITY" "$(top_activity)"
assert_eq "Agenda: cal_view_mode:agenda is selected" "true" "$(selected "$D/cal-0-agenda-app.xml" cal_view_mode:agenda)"
assert_eq "Agenda: … and the month drop-down is not shown" "no" "$(has_node "$D/cal-0-agenda-app.xml" cal_month_dropdown)"
assert_eq "Agenda: no fourth-and-a-half satellite: the burst holds exactly quick_sat_label:0..3" "4" "$(count_ids "$D/cal-0-agenda.xml" quick_sat_label:)"
c6; ensure_start

launch "$CAL_TILE" cal-1-day 1 "$CAL_LABELS" "$CAL_LINE"
assert_eq "Day: the shell's Calendar is resumed" "$CALENDAR_ACTIVITY" "$(top_activity)"
assert_eq "Day: cal_view_mode:day is selected" "true" "$(selected "$D/cal-1-day-app.xml" cal_view_mode:day)"
c6; ensure_start

launch "$CAL_TILE" cal-2-month 2 "$CAL_LABELS" "$CAL_LINE"
assert_eq "Month: the shell's Calendar is resumed" "$CALENDAR_ACTIVITY" "$(top_activity)"
assert_eq "Month: cal_view_mode:agenda is selected (T16-13: no Month page)" "true" "$(selected "$D/cal-2-month-app.xml" cal_view_mode:agenda)"
assert_eq "Month: … AND cal_month_dropdown is shown" "yes" "$(has_node "$D/cal-2-month-app.xml" cal_month_dropdown)"
c6; ensure_start

TESS="$(tessera_id)"
assert_ne "Tessera exists (the Calendar launches above ran LocalCalendar.id)" "" "$TESS"
cal_lists "${TESS:-0}" Tessera
EV0="$(tess_events "${TESS:-0}")"; note "Tessera (_id=$TESS) holds $EV0 events before New event"
launch "$CAL_TILE" cal-3-new-event 3 "$CAL_LABELS" "$CAL_LINE"
assert_eq "New event: the shell's Calendar is resumed" "$CALENDAR_ACTIVITY" "$(top_activity)"
assert_eq "New event: cal_editor is shown" "yes" "$(has_node "$D/cal-3-new-event-app.xml" cal_editor)"
back 2
dump_ui "$D/cal-3-after-back.xml"
if [ "$(has_node "$D/cal-3-after-back.xml" cal_editor)" = yes ]; then back 2; dump_ui "$D/cal-3-after-back.xml"; note "a second Back was needed (the first closed the keyboard)"; fi
assert_eq "New event: Back discards it (cal_editor gone)" "no" "$(has_node "$D/cal-3-after-back.xml" cal_editor)"
cal_lists "${TESS:-0}" Tessera
assert_eq "New event: Tessera's event count is unchanged" "$EV0" "$(tess_events "${TESS:-0}")"
c6; ensure_start

# ------------------------------------------------------------------------------------------------ People
log "--- the PEOPLE tile's burst: Contacts, New contact, Groups"
launch "$PPL_TILE" ppl-0-contacts 0 "$PPL_LABELS" "$PPL_LINE"
assert_eq "PEOPLE: no quick_sat_label:3" "no" "$(has_node "$D/ppl-0-contacts.xml" quick_sat_label:3)"
assert_eq "PEOPLE: no quick_sat:3" "no" "$(has_node "$D/ppl-0-contacts.xml" quick_sat:3)"
assert_eq "Contacts: the shell's People is resumed (dumpsys activity activities)" "$PEOPLE_ACTIVITY" "$(top_activity)"
assert_eq "Contacts: people_page:list is selected" "true" "$(selected "$D/ppl-0-contacts-app.xml" people_page:list)"
assert_eq "Contacts: … on the CONTACTS pivot" "true" "$(selected "$D/ppl-0-contacts-app.xml" people_pivot:contacts)"
c6; ensure_start

launch "$PPL_TILE" ppl-1-new-contact 1 "$PPL_LABELS" "$PPL_LINE"
assert_eq "New contact: the shell's People is resumed" "$PEOPLE_ACTIVITY" "$(top_activity)"
assert_eq "New contact: people_page:editor is selected" "true" "$(selected "$D/ppl-1-new-contact-app.xml" people_page:editor)"
back 2
dump_ui "$D/ppl-1-after-back.xml"
if [ "$(selected "$D/ppl-1-after-back.xml" people_page:editor)" = true ]; then back 2; dump_ui "$D/ppl-1-after-back.xml"; note "a second Back was needed (the first closed the keyboard)"; fi
assert_ne "New contact: Back discards it (the editor is no longer the page on show)" "true" "$(selected "$D/ppl-1-after-back.xml" people_page:editor)"
assert_eq "New contact: the raw_contacts count is unchanged" "$RAW0" "$(raw_count)"
c6; ensure_start

launch "$PPL_TILE" ppl-2-groups 2 "$PPL_LABELS" "$PPL_LINE"
assert_eq "Groups: the shell's People is resumed" "$PEOPLE_ACTIVITY" "$(top_activity)"
assert_eq "Groups: people_pivot:groups is selected" "true" "$(selected "$D/ppl-2-groups-app.xml" people_pivot:groups)"
c6; ensure_start

# ------------------------------------------------------------------------------------------------ dumpsys shortcut
log "--- adb shell dumpsys shortcut"
adb shell dumpsys shortcut | tr -d '\r' > "$D/dumpsys-shortcut.txt"
python3 - "$D/dumpsys-shortcut.txt" > "$D/shortcuts.tsv" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(r"\n\s+Package: app\.tileshell\s+UID:.*?(?=\n\s+Package: |\Z)", text, re.S)
block = m.group(0) if m else ""
for s in re.finditer(r"ShortcutInfo \{id=([^,]+),.*?activity=ComponentInfo\{([^}]+)\}.*?rank=(\d+)", block, re.S):
    print("%s\t%s\t%s" % (s.group(2), s.group(3), s.group(1)))
PY
sort "$D/shortcuts.tsv" | grep -E 'calendar\.CalendarActivity|people\.PeopleActivity' | tee -a "$LOG" >/dev/null
of() { awk -F'\t' -v a="$1" '$1==a {print $2 "=" $3}' "$D/shortcuts.tsv" | sort | tr '\n' ' ' | sed 's/ $//'; }
assert_eq "dumpsys shortcut: the Calendar activity's ids with ranks 0–3" "0=agenda 1=day 2=month 3=new_event" "$(of app.tileshell/app.tileshell.calendar.CalendarActivity)"
assert_eq "dumpsys shortcut: the People activity's ids with ranks 0–2" "0=contacts 1=new_contact 2=groups" "$(of app.tileshell/app.tileshell.people.PeopleActivity)"
assert_eq "dumpsys shortcut: the seven ids, each once, on those two activities" "agenda contacts day groups month new_contact new_event" \
  "$(awk -F'\t' '$1 ~ /calendar\.CalendarActivity|people\.PeopleActivity/ {print $3}' "$D/shortcuts.tsv" | sort | tr '\n' ' ' | sed 's/ $//')"
assert_eq "dumpsys shortcut: none of the seven ids is declared on another activity" "0" \
  "$(awk -F'\t' '$1 !~ /calendar\.CalendarActivity|people\.PeopleActivity/ && $3 ~ /^(agenda|day|month|new_event|contacts|new_contact|groups)$/' "$D/shortcuts.tsv" | grep -c .)"

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
ring_save
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline" "0" "$?"
assert_eq "restore: the raw_contacts count is the count before the row" "$RAW0" "$(raw_count)"
ensure_start
row_end
