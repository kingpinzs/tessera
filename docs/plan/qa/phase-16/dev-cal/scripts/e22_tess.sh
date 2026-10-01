#!/usr/bin/env bash
# Development proof of E22 / E9's core: no write reaches an account calendar by any path. Tess's delete is refused for
# an event that is only on an account calendar ("That event isn't in your Tessera calendar.", no Delete card) and
# allowed on a Tessera event (through the write layer); her add lands in Tessera; the exported EDIT on an account
# event opens it read-only and the exported INSERT fills the editor in, saves nothing without the tap and goes to
# Tessera whatever calendar_id it carried; hiding a calendar in the ≡ pane writes nothing to the provider. Work holds
# exactly Offsite throughout. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E22_TESS "Tess, the exported intents and the pane never write an account calendar"
c6; cal_fixtures_down; sleep 1
sync_fixtures_up
open_cal -a android.intent.action.MAIN; sleep 2
TESS="$(tessera_id)"; BEFORE="$(event_count "calendar_id=$TESS AND deleted=0")"
DENTIST="$(mkevent "$TESS" dentist "$(day_ms 1 14:00)" "$(day_ms 1 15:00)")"
note "personal=$PERSONAL work=$WORK offsite=$OFFSITE dentist=$DENTIST"
OFFSITE_ROW="$(event_rows title:dtstart:dtend:calendar_id "_id=$OFFSITE")"
c6

ask() { # request -> Tess open with the request typed; TMARK is the MARK before it
  ensure_start; cortana_assist; sleep 4
  TMARK="$(ring_mark)"
  type_request "$1" 1
}
card() { # out.xml: polls for the dump after a request
  for _ in 1 2 3 4 5 6 7 8; do dump_ui "$1"; grep -q 'resource-id="cortana_card' "$1" && break; sleep 0.5; done
}

# ---- Tess's delete, the refused case: the title is only on an account calendar
ask "delete the event Offsite"; sleep 4; dump_ui "$ROW_DIR/tess_offsite.xml"
assert_eq "the reply" "That event isn't in your Tessera calendar." "$(reply_since "$TMARK")"
assert_eq "no Delete card" no "$(has_node "$ROW_DIR/tess_offsite.xml" cortana_card:delete_confirm)"
assert_eq "and no confirm button" no "$(has_node "$ROW_DIR/tess_offsite.xml" cortana_card_button:confirm)"
work_holds_offsite "after Tess's refused delete"
cortana_close

# ---- a title found nowhere
ask "delete the event nonesuch"; sleep 4
assert_eq "a title on no calendar keeps its reply" "I don't see nonesuch on your calendar." "$(reply_since "$TMARK")"
cortana_close

# ---- Tess's delete, the allowed case: a Tessera event
ask "delete the event dentist"; card "$ROW_DIR/tess_dentist.xml"
assert_eq "the Delete card" yes "$(has_node "$ROW_DIR/tess_dentist.xml" cortana_card_button:confirm)"
tap_node "$ROW_DIR/tess_dentist.xml" cortana_card_button:confirm; sleep 3
assert_eq "the reply" "Deleted." "$(ring_since "$TMARK" | grep -F '[speech]' | grep -oE 'text="[^"]*"' | tail -1 | sed 's/^text="//; s/"$//')"
assert_eq "the events row is gone" 0 "$(event_count "_id=$DENTIST")"
assert_contains "through the write layer, naming a Tessera event" "[calendar] write delete event=$DENTIST: ok" "$(ring_since "$TMARK")"
cortana_close

# ---- Tess's add: Tessera, never an account calendar (J6's request)
ask "add a meeting called standup to my calendar at ten AM"; card "$ROW_DIR/tess_add.xml"
assert_eq "the confirm card" yes "$(has_node "$ROW_DIR/tess_add.xml" cortana_card_button:confirm)"
tap_node "$ROW_DIR/tess_add.xml" cortana_card_button:confirm; sleep 3
assert_eq "the reply" "Added to your calendar." "$(ring_since "$TMARK" | grep -F '[speech]' | grep -oE 'text="[^"]*"' | tail -1 | sed 's/^text="//; s/"$//')"
STANDUP="$(event_ids "title='standup' AND calendar_id=$TESS" | tr -d ' ')"
assert_ne "standup is in Tessera" "" "$STANDUP"
assert_contains "the write line names it" "[calendar] write insert event=$STANDUP: ok" "$(ring_since "$TMARK")"
assert_contains "J6's line still names the local calendar" "calendar event inserted into the shell's local calendar ($TESS)" "$(ring_since "$TMARK")"
assert_eq "Personal holds nothing" "" "$(titles_in "$PERSONAL")"
work_holds_offsite "after Tess's add"
cortana_close

# Tess's session window sits over every app until it is closed: Back, then Home, before the Calendar is read again.
adb shell input keyevent KEYCODE_BACK; sleep 1; ensure_start

# ---- the exported EDIT on an account calendar's event: read-only
adb shell am start -W -n "$CAL_ACT" -a android.intent.action.EDIT -d "content://com.android.calendar/events/$OFFSITE" > /dev/null 2>&1; sleep 2.5
dump_ui "$ROW_DIR/edit_offsite.xml"
assert_eq "EDIT on Offsite opens its page, not the editor" "yes no" "$(has_node "$ROW_DIR/edit_offsite.xml" "cal_event_page:$OFFSITE") $(has_node "$ROW_DIR/edit_offsite.xml" cal_editor)"
assert_eq "with no edit, delete or sync action" 0 "$(count_prefix "$ROW_DIR/edit_offsite.xml" cal_event_action:)"
assert_eq "Offsite's row is unchanged" "$OFFSITE_ROW" "$(event_rows title:dtstart:dtend:calendar_id "_id=$OFFSITE")"

# ---- the exported INSERT naming the Work calendar
adb shell am start -W -n "$CAL_ACT" -a android.intent.action.INSERT -t vnd.android.cursor.dir/event --el calendar_id "$WORK" --es title Intruder > /dev/null 2>&1; sleep 2.5
dump_ui "$ROW_DIR/insert.xml"
assert_eq "INSERT opens the editor with the title prefilled" "yes Intruder" "$(has_node "$ROW_DIR/insert.xml" cal_editor) $(node_text "$ROW_DIR/insert.xml" cal_editor_field:title)"
assert_eq "its calendar field reads Tessera" "Tessera" "$(field_text "$ROW_DIR/insert.xml" calendar)"
assert_eq "before Save, no Intruder anywhere (a prefill saves nothing)" 0 "$(event_count "title='Intruder'")"
tap cal_editor_save 2.5
assert_eq "after Save, Intruder is in Tessera" "$TESS" "$(event_rows calendar_id "title='Intruder'" | sed -n 's/.*calendar_id=\([0-9]*\).*/\1/p')"
work_holds_offsite "after the INSERT"

# ---- hide is not a write (r3 D4)
open_day "$(day_ms 5 12:00)"; dump_ui "$ROW_DIR/offsite_day.xml"
assert_eq "Offsite shows in the Day view" yes "$(has_node "$ROW_DIR/offsite_day.xml" "cal_event:$OFFSITE")"
tap cal_menu 1.5; dump_ui "$ROW_DIR/pane.xml"
assert_eq "both account calendars list in the pane" "yes yes" "$(has_node "$ROW_DIR/pane.xml" "cal_calendar_row:$PERSONAL") $(has_node "$ROW_DIR/pane.xml" "cal_calendar_row:$WORK")"
tap "cal_calendar_row:$WORK" 1.2; adb shell input keyevent KEYCODE_BACK; sleep 1.5; dump_ui "$ROW_DIR/offsite_hidden.xml"
assert_eq "Work un-ticked: Offsite leaves the view" no "$(has_node "$ROW_DIR/offsite_hidden.xml" "cal_event:$OFFSITE")"
assert_contains "and Work's visible column is unchanged" "visible=1" "$(S "content query --uri $CALS --projection _id:visible --where \"_id=$WORK\"")"
tap cal_menu 1.5; tap "cal_calendar_row:$WORK" 1.2; adb shell input keyevent KEYCODE_BACK; sleep 1.5; dump_ui "$ROW_DIR/offsite_back.xml"
assert_eq "Work re-ticked: Offsite is back" yes "$(has_node "$ROW_DIR/offsite_back.xml" "cal_event:$OFFSITE")"
WRITES="$(ring_since "$ROW_MARK" | grep -F '[calendar] write ' | grep -F ': ok' | sed 's/.*\[calendar\] //' | tr '\n' ';')"
log "every write line of the session: $WRITES"
work_holds_offsite "at the end"

# ---- restore
c6; cal_fixtures_down
purge_tessera_events "title IN ('standup','Intruder','dentist')"
assert_eq "Tessera holds what it held before the session" "$BEFORE" "$(event_count "calendar_id=$TESS AND deleted=0")"
ensure_start
session_end
