#!/usr/bin/env bash
# Development proof of Calendar's half of build task 9 (E25's core): the four static App Shortcuts are published for
# CalendarActivity in rank order, and each one's intent (the `page` extra) opens its page — `month` Agenda with the
# month drop-down open, `new_event` the editor, which Back discards. The burst on Start is the gate's. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E25_SHORTCUTS "Calendar's App Shortcuts: agenda, day, month, new_event"
c6
# What the last cut-off session may have left (an event of the first E4 run).
rmevents "title IN ('Dentist','Standup') AND calendar_id=$(tessera_id)"
S dumpsys shortcut > "$ROW_DIR/dumpsys_shortcut.txt"
python3 - "$ROW_DIR/dumpsys_shortcut.txt" > "$ROW_DIR/shortcuts.txt" <<'PY'
import re, sys
text = open(sys.argv[1], encoding='utf-8', errors='replace').read()
# The manifest shortcuts of the shell, in the order dumpsys prints them: id, activity and rank of each.
out = []
for block in re.split(r'(?=ShortcutInfo \{id=)', text):
    m = re.match(r'ShortcutInfo \{id=([a-z_]+),', block)
    if not m or 'activity=ComponentInfo{app.tileshell/app.tileshell.calendar.CalendarActivity}' not in block.split('ShortcutInfo {id=', 2)[1 if block.startswith('ShortcutInfo') else 0][:400]:
        continue
    rank = re.search(r'rank=(\d+)', block)
    out.append((int(rank.group(1)) if rank else -1, m.group(1)))
for rank, sid in sorted(set(out)):
    print(rank, sid)
PY
log "Calendar's shortcuts in dumpsys shortcut (rank id):"; tee -a "$LOG" < "$ROW_DIR/shortcuts.txt"
assert_eq "four shortcuts in rank order" "0 agenda|1 day|2 month|3 new_event" "$(paste -sd'|' "$ROW_DIR/shortcuts.txt")"
TESS="$(tessera_id)"; BEFORE="$(event_count "calendar_id=$TESS")"

page() { adb shell am start -W -n "$CAL_ACT" -a android.intent.action.VIEW --es page "$1" < /dev/null >/dev/null 2>&1; sleep 2.5; dump_ui "$ROW_DIR/page_$1.xml"; }
page agenda
assert_eq "agenda: resumed" "app.tileshell/.calendar.CalendarActivity" "$(top_activity)"
assert_eq "agenda: cal_view_mode:agenda selected, no drop-down" "true no" "$(node_attr "$ROW_DIR/page_agenda.xml" cal_view_mode:agenda selected) $(has_node "$ROW_DIR/page_agenda.xml" cal_month_dropdown)"
page day
assert_eq "day: cal_view_mode:day selected" "true" "$(node_attr "$ROW_DIR/page_day.xml" cal_view_mode:day selected)"
page month
assert_eq "month: cal_view_mode:agenda selected AND cal_month_dropdown shown" "true yes" "$(node_attr "$ROW_DIR/page_month.xml" cal_view_mode:agenda selected) $(has_node "$ROW_DIR/page_month.xml" cal_month_dropdown)"
page new_event
assert_eq "new_event: cal_editor" yes "$(has_node "$ROW_DIR/page_new_event.xml" cal_editor)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; dump_ui "$ROW_DIR/after_back.xml"
assert_eq "Back discards the editor" no "$(has_node "$ROW_DIR/after_back.xml" cal_editor)"
assert_eq "Tessera's event count is unchanged" "$BEFORE" "$(event_count "calendar_id=$TESS")"
# The same intent again while the app runs (singleTask, onNewIntent) re-routes it.
page day; page agenda
assert_eq "a second intent re-routes the running app" "true" "$(node_attr "$ROW_DIR/page_agenda.xml" cal_view_mode:agenda selected)"
c6; ensure_start
session_end
