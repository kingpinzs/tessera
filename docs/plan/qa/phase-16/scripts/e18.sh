#!/usr/bin/env bash
# Phase 16 E18 — permission states (T16-15, T16-16; r3 D7, V10, V12).
#
#   A  READ and WRITE_CALENDAR revoked   Calendar's page says it cannot read and offers the grant in place; the
#                                        checklist's `calendar` row is red; no query runs — `calendars: denied
#                                        (READ_CALENDAR)` and no `calendars: none`
#   B  pm grant                          the views load without a restart; a `calendars: n (…)` line and no `denied`
#   C  WRITE alone revoked, Tessera gone the views load, `calendars: n (local missing: WRITE_CALENDAR)`, no Tessera row;
#                                        New → a title → Save → cal_notice cannot save, the grant offered, nothing in
#                                        events; Tess's add → "I don't have a calendar to add that to." and `local
#                                        calendar could not be created:`; pm grant WRITE, Save again → one Tessera row
#                                        (`local created`) holding the event
#   D  READ alone revoked                with that one Tessera row: Calendar opened and closed three times, Tess's add →
#                                        the same reply, `local calendar lookup failed:` and no `local calendar created`;
#                                        pm grant READ → EXACTLY one Tessera row and no "standup"
#   E  READ_CONTACTS alone revoked       WRITE_CONTACTS still held (asserted); People says so and offers the grant;
#                                        `checklist:people:missing`; Tess's `contacts` row is red
#   G  the grant made IN PLACE           READ and WRITE_CALENDAR revoked (with WRITE still held Android grants READ
#                                        at the request, with no dialog), Calendar's cal_grant tapped and Android's dialog
#                                        allowed: the views load, `permission request READ_CALENDAR: granted` and
#                                        `[app] feeds started (calendar grant)`; an event the driver then inserts
#                                        reaches the Calendar tile (`refresh (provider change)`, the tile's text) with
#                                        no restart of the shell since before the grant (gate review A, finding 10)
#   restore                              pm grant ×4, each asserted; the row's events deleted; ensure_start
# E18_LEGS=G runs leg G and the restore alone — a narrow run; the log's first RECORD says so.
# `pm revoke` ends the shell's process and empties its ring, so every slice here is the row's saved ring plus the live one.
set -uo pipefail
LEGS="${E18_LEGS:-all}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"
PXPY="$HERE/cal_px.py"

row_begin E18 "permission states: READ / WRITE_CALENDAR together and alone, READ_CONTACTS alone"
if [ "$LEGS" != all ]; then
  [ "$LEGS" = G ] || { _verdict FAIL "E18_LEGS" "only E18_LEGS=G is a narrow run of this row (got $LEGS)"; row_end; exit 1; }
  record "legs run" "G ONLY (a READ_CALENDAR grant made in place starts the tile's observer) — a narrow run; legs A–E stand on the row's earlier run"
fi
revoke() { ring_save; adb shell pm revoke app.tileshell "android.permission.$1"; }
grant() { adb shell pm grant app.tileshell "android.permission.$1"; }
tess_rows() { cals | grep -cF 'account_name=Tessera,'; }
settle_home() { sleep 4; adb shell input keyevent KEYCODE_HOME; sleep 3; }
# How many red-dominant pixels a node's drawn box holds ("the row is red").
red_in() { # dump.xml resource-id shot.png
  local b; b="$(bounds "$1" "$2")"
  [ -n "$b" ] || { echo "(no node)"; return; }
  # shellcheck disable=SC2086
  python3 "$PXPY" red "$3" $b
}
open_checklist() { adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-clear-task --es page CHECKLIST >/dev/null 2>&1; sleep 3; }
for p in READ_CALENDAR WRITE_CALENDAR READ_CONTACTS WRITE_CONTACTS; do
  assert_eq "precondition: $p is held at the start" "true" "$(perm_granted "$p")"
done
c6; ensure_start
cpurge "title IN ('E18 save','standup','E18 grant')"
CRASH0="$(ccrashes)"

if [ "$LEGS" = all ]; then
# ----------------------------------------------------------------------------------------------- A: READ and WRITE revoked
log "--- A: READ_CALENDAR and WRITE_CALENDAR revoked"
A_MARK="$(ring_mark)"
revoke READ_CALENDAR; revoke WRITE_CALENDAR
settle_home
assert_eq "A: both are revoked" "false false" "$(perm_granted READ_CALENDAR) $(perm_granted WRITE_CALENDAR)"
copen; dump_ui "$ROW_DIR/A-denied.xml"; screencap "$ROW_DIR/A-denied.png"
NOTICE="$(ctext "$ROW_DIR/A-denied.xml" cal_notice)"; record "A: the cannot-read notice's wording (H20)" "$NOTICE"
assert_eq "A: Calendar's page says it cannot read the calendar (cal_notice)" "yes" "$(printf '%s' "$NOTICE" | grep -Eiq "(can't|cannot) read.*calendar" && echo yes || echo no)"
assert_eq "A: … and offers the grant in place (cal_grant)" "yes" "$(has_node "$ROW_DIR/A-denied.xml" cal_grant)"
assert_eq "A: … with no view drawn behind it (no week strip)" "no" "$(has_node "$ROW_DIR/A-denied.xml" cal_strip)"
A_SLICE="$(csince "$A_MARK")"; printf '%s\n' "$A_SLICE" > "$ROW_DIR/A-slice.txt"
assert_contains "A: the slice from the revoke's MARK holds [calendar] calendars: denied (READ_CALENDAR)" "[calendar] calendars: denied (READ_CALENDAR)" "$A_SLICE"
absent_in "A: … and no calendars: none" "calendars: none" "$A_SLICE"
absent_in "A: … no query runs: no [calendar] view line" "[calendar] view " "$A_SLICE"
open_checklist
scroll_to_node "$ROW_DIR/A-checklist.xml" "checklist:calendar:missing" 8 >/dev/null 2>&1 || true
dump_ui "$ROW_DIR/A-checklist.xml"; screencap "$ROW_DIR/A-checklist.png"
assert_eq "A: the checklist's calendar row (phase 01's) reads missing (checklist:calendar:missing)" "yes" "$(has_node "$ROW_DIR/A-checklist.xml" checklist:calendar:missing)"
RED="$(red_in "$ROW_DIR/A-checklist.xml" checklist:calendar:missing "$ROW_DIR/A-checklist.png")"
assert_ne "A: … and is red (red pixels inside the row's drawn box)" "0" "$RED"
note "red pixels in the calendar row: $RED; rows on this screen: $(cids "$ROW_DIR/A-checklist.xml" checklist:)"

# ----------------------------------------------------------------------------------------------- B: the grant
log "--- B: pm grant restores it, with no restart"
copen; dump_ui "$ROW_DIR/B-before.xml"
assert_eq "B: Calendar is in front, still on its cannot-read notice" "yes" "$(has_node "$ROW_DIR/B-before.xml" cal_grant)"
PID0="$(adb shell pidof app.tileshell | tr -d '\r')"
B_MARK="$(ring_mark)"
grant READ_CALENDAR; grant WRITE_CALENDAR
sleep 4; dump_ui "$ROW_DIR/B-granted.xml"
assert_eq "B: pm grant restores it — both held" "true true" "$(perm_granted READ_CALENDAR) $(perm_granted WRITE_CALENDAR)"
assert_eq "B: the views load: the notice is gone" "no" "$(has_node "$ROW_DIR/B-granted.xml" cal_notice)"
assert_eq "B: … and the week strip is drawn (cal_strip inside cal_view)" "yes" "$(cunder "$ROW_DIR/B-granted.xml" cal_view cal_strip)"
assert_eq "B: … without a restart (the shell's pid is the same)" "$PID0" "$(adb shell pidof app.tileshell | tr -d '\r')"
B_SLICE="$(ring_since "$B_MARK")"; printf '%s\n' "$B_SLICE" > "$ROW_DIR/B-slice.txt"
log "after the grant: $(cline "$B_SLICE" '[calendar] calendars:')"
assert_eq "B: the slice from the grant's MARK holds a [calendar] calendars: n (…) line" "yes" "$(printf '%s\n' "$B_SLICE" | grep -Eq '\[calendar\] calendars: [0-9]+ \(.+\)' && echo yes || echo no)"
absent_in "B: … and no denied line" "denied" "$B_SLICE"

# ----------------------------------------------------------------------------------------------- C: WRITE alone revoked
log "--- C: WRITE_CALENDAR alone revoked, Tessera deleted first (r3 D7)"
c6
tessera_down
assert_eq "C: Tessera is deleted (the preamble's command)" "0" "$(tess_rows)"
revoke WRITE_CALENDAR
settle_home
assert_eq "C: READ held, WRITE revoked" "true false" "$(perm_granted READ_CALENDAR) $(perm_granted WRITE_CALENDAR)"
C_MARK="$(ring_mark)"
copen; dump_ui "$ROW_DIR/C-views.xml"
NCAL="$(cals | grep -c '_id=')"
C_SLICE="$(csince "$C_MARK")"; printf '%s\n' "$C_SLICE" > "$ROW_DIR/C-slice.txt"
assert_eq "C: Calendar opened → the views load (the strip, no notice)" "yes no" "$(has_node "$ROW_DIR/C-views.xml" cal_strip) $(has_node "$ROW_DIR/C-views.xml" cal_notice)"
assert_contains "C: the slice holds [calendar] calendars: n (local missing: WRITE_CALENDAR)" "[calendar] calendars: $NCAL (local missing: WRITE_CALENDAR)" "$C_SLICE"
assert_eq "C: the calendars query lists no Tessera row (nothing was created)" "0" "$(tess_rows)"
ctap cal_bar:new 2
ctype_field title "E18 save"
ctap cal_editor_save 2.5; dump_ui "$ROW_DIR/C-cannot-save.xml"; screencap "$ROW_DIR/C-cannot-save.png"
NOTICE="$(ctext "$ROW_DIR/C-cannot-save.xml" cal_notice)"; record "C: the cannot-save notice's wording (H20)" "$NOTICE"
assert_eq "C: New → a title → Save → cal_notice says it cannot save" "yes" "$(printf '%s' "$NOTICE" | grep -Eiq "(can't|cannot) save" && echo yes || echo no)"
assert_eq "C: … and offers the grant in place (cal_grant)" "yes" "$(has_node "$ROW_DIR/C-cannot-save.xml" cal_grant)"
assert_eq "C: … events holds no such title" "0" "$(cevent_count "title='E18 save'")"
assert_eq "C: … and still no Tessera row" "0" "$(tess_rows)"
tess_ask "add a meeting called standup to my calendar at ten AM"
tess_card "$ROW_DIR/C-card.xml"
assert_eq "C: Tess shows the confirm card" "yes" "$(has_node "$ROW_DIR/C-card.xml" cortana_card_button:confirm)"
tess_confirm "$ROW_DIR/C-card.xml"
assert_eq "C: Tess's add, confirm tapped → \"I don't have a calendar to add that to.\"" "I don't have a calendar to add that to." "$(reply_since "$CMARK" | sed "s/&apos;/'/g")"
CT_SLICE="$(csince "$TMARK")"; printf '%s\n' "$CT_SLICE" > "$ROW_DIR/C-tess-slice.txt"
assert_contains "C: … and [calendar] local calendar could not be created: (T16-2 line 3)" "[calendar] local calendar could not be created:" "$CT_SLICE"
assert_eq "C: … still no Tessera row" "0" "$(tess_rows)"
assert_eq "C: … and no standup event anywhere" "0" "$(cevent_count "title='standup'")"
tess_close
C2_MARK="$(ring_mark)"
grant WRITE_CALENDAR; sleep 2
assert_eq "C: pm grant WRITE_CALENDAR" "true" "$(perm_granted WRITE_CALENDAR)"
# Calendar back in front. Its editor is where it was left unless the shell re-routed the launch; then the title is typed again.
copen; dump_ui "$ROW_DIR/C-back.xml"
if [ "$(has_node "$ROW_DIR/C-back.xml" cal_editor)" != yes ] || [ "$(ctext "$ROW_DIR/C-back.xml" cal_editor_field:title)" != "E18 save" ]; then
  note "the editor was not left open with its title; New and the title again"
  [ "$(has_node "$ROW_DIR/C-back.xml" cal_editor)" = yes ] && ctap cal_editor_cancel 1.5
  ctap cal_bar:new 2; ctype_field title "E18 save"
fi
ctap cal_editor_save 3
assert_eq "C: Save again → one Tessera row" "1" "$(tess_rows)"
TESS="$(tessera_id)"
C2_SLICE="$(csince "$C2_MARK")"; printf '%s\n' "$C2_SLICE" > "$ROW_DIR/C2-slice.txt"
assert_contains "C: … (local created)" "(local created id=$TESS)" "$C2_SLICE"
assert_eq "C: … holding the event" "1" "$(cevent_count "title='E18 save' AND calendar_id=${TESS:-0} AND deleted=0")"

# ----------------------------------------------------------------------------------------------- D: READ alone revoked
log "--- D: READ_CALENDAR alone revoked — never a second Tessera (r3 D7)"
c6
assert_eq "D: precondition — that one Tessera row" "1" "$(tess_rows)"
revoke READ_CALENDAR
settle_home
assert_eq "D: READ revoked, WRITE held" "false true" "$(perm_granted READ_CALENDAR) $(perm_granted WRITE_CALENDAR)"
D_MARK="$(ring_mark)"
for k in 1 2 3; do
  copen
  [ "$k" = 1 ] && dump_ui "$ROW_DIR/D-open1.xml"
  c6; ensure_start
done
tess_ask "add a meeting called standup to my calendar at ten AM"
tess_card "$ROW_DIR/D-card.xml"
assert_eq "D: Tess shows the confirm card" "yes" "$(has_node "$ROW_DIR/D-card.xml" cortana_card_button:confirm)"
tess_confirm "$ROW_DIR/D-card.xml"
assert_eq "D: Calendar opened and closed three times, then Tess's add → the reply" "I don't have a calendar to add that to." "$(reply_since "$CMARK" | sed "s/&apos;/'/g")"
D_SLICE="$(csince "$D_MARK")"; printf '%s\n' "$D_SLICE" > "$ROW_DIR/D-slice.txt"
assert_contains "D: the slice holds [calendar] local calendar lookup failed:" "[calendar] local calendar lookup failed:" "$D_SLICE"
absent_in "D: … and no local calendar created" "local calendar created" "$D_SLICE"
record "D: what Calendar's own start logged each time while READ was denied" "$(printf '%s\n' "$D_SLICE" | grep -F '[calendar] calendars:' | sed 's/^.*\[calendar\] //' | sort | uniq -c | tr '\n' ';')"
tess_close
grant READ_CALENDAR; sleep 2
assert_eq "D: pm grant READ_CALENDAR" "true" "$(perm_granted READ_CALENDAR)"
assert_eq "D: the calendars query lists EXACTLY one account_name=Tessera row" "1" "$(tess_rows)"
assert_eq "D: … and no \"standup\" event" "0" "$(cevent_count "title='standup'")"

# ----------------------------------------------------------------------------------------------- E: contacts
log "--- E: READ_CONTACTS alone revoked (WRITE_CONTACTS stays held — r3 V12)"
revoke READ_CONTACTS
settle_home
assert_eq "E: READ_CONTACTS is revoked" "false" "$(perm_granted READ_CONTACTS)"
assert_eq "E: WRITE_CONTACTS stays held (dumpsys package: granted=true)" "true" "$(perm_granted WRITE_CONTACTS)"
adb shell am start -W -n "$PEOPLE_ACTIVITY" >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/E-people.xml"; screencap "$ROW_DIR/E-people.png"
NOTICE="$(ctext "$ROW_DIR/E-people.xml" people_notice)"; record "E: People's cannot-read notice's wording (H20)" "$NOTICE"
assert_eq "E: People says so (people_notice: it cannot read contacts)" "yes" "$(printf '%s' "$NOTICE" | grep -Eiq "(can't|cannot) (read|see).*contacts" && echo yes || echo no)"
assert_eq "E: … and offers the grant (people_notice_action)" "yes" "$(has_node "$ROW_DIR/E-people.xml" people_notice_action)"
open_checklist
scroll_to_node "$ROW_DIR/E-checklist.xml" "checklist:people:missing" 8 >/dev/null 2>&1 || true
dump_ui "$ROW_DIR/E-checklist.xml"; screencap "$ROW_DIR/E-checklist.png"
assert_eq "E: the Setup checklist shows checklist:people:missing" "yes" "$(has_node "$ROW_DIR/E-checklist.xml" checklist:people:missing)"
note "people rows on this screen: $(cids "$ROW_DIR/E-checklist.xml" checklist:people)"
ensure_start
adb shell input keyevent KEYCODE_ASSIST; sleep 3
dump_ui "$ROW_DIR/E-tess-home.xml"; tap_node "$ROW_DIR/E-tess-home.xml" cortana_menu_button; sleep 1.5
dump_ui "$ROW_DIR/E-tess-pane.xml"; tap_node "$ROW_DIR/E-tess-pane.xml" cortana_pane_item_settings; sleep 2
scroll_to_node "$ROW_DIR/E-tess-contacts.xml" "cortana_check:contacts:missing" 8 >/dev/null 2>&1 || true
dump_ui "$ROW_DIR/E-tess-contacts.xml"; screencap "$ROW_DIR/E-tess-contacts.png"
assert_eq "E: Tess's contacts row reads missing (cortana_check:contacts:missing)" "yes" "$(has_node "$ROW_DIR/E-tess-contacts.xml" cortana_check:contacts:missing)"
RED="$(red_in "$ROW_DIR/E-tess-contacts.xml" cortana_check:contacts:missing "$ROW_DIR/E-tess-contacts.png")"
assert_ne "E: … and is red (red pixels inside the row's drawn box)" "0" "$RED"
note "red pixels in Tess's contacts row: $RED; her rows on this screen: $(cids "$ROW_DIR/E-tess-contacts.xml" cortana_check:)"
cortana_close; adb shell input keyevent KEYCODE_BACK; sleep 1
fi   # legs A to E

# ----------------------------------------------------------------------------------------------- G: the grant in place
log "--- G: READ_CALENDAR granted in place (cal_grant, Android's dialog) starts the Calendar tile's observer"
ALLOW="com.android.permissioncontroller:id/permission_allow_button"
c6; ensure_start
copen; TESS="$(tessera_id)"; adb shell input keyevent KEYCODE_HOME; sleep 1
assert_ne "G: precondition — Tessera exists" "" "$TESS"
assert_eq "G: precondition — READ and WRITE_CALENDAR are held" "true true" "$(perm_granted READ_CALENDAR) $(perm_granted WRITE_CALENDAR)"
G0_MARK="$(ring_mark)"
# Both, so that Android asks: with WRITE_CALENDAR still held it grants READ_CALENDAR at the request without a dialog
# (one permission group) — the kept run E18-legs-G-run1-… shows that path: granted, feeds started, no dialog.
revoke READ_CALENDAR; revoke WRITE_CALENDAR
settle_home
assert_eq "G: READ_CALENDAR and WRITE_CALENDAR revoked" "false false" "$(perm_granted READ_CALENDAR) $(perm_granted WRITE_CALENDAR)"
record "G: the feed's refresh line at the shell's start without the read" "$(cline "$(csince "$G0_MARK")" '[calendar] refresh (')"
copen; dump_ui "$ROW_DIR/G-denied.xml"
assert_eq "G: Calendar's page offers the grant in place (cal_grant)" "yes" "$(has_node "$ROW_DIR/G-denied.xml" cal_grant)"
PIDG="$(adb shell pidof app.tileshell < /dev/null | tr -d '\r')"
G_MARK="$(ring_mark)"
ctap cal_grant 2; dump_ui "$ROW_DIR/G-dialog.xml"; screencap "$ROW_DIR/G-dialog.png"
assert_eq "G: the tap on cal_grant brings Android's own permission dialog (its Allow button)" "yes" "$(has_node "$ROW_DIR/G-dialog.xml" "$ALLOW")"
tap_node "$ROW_DIR/G-dialog.xml" "$ALLOW"; sleep 3.5
dump_ui "$ROW_DIR/G-granted.xml"
assert_eq "G: Allow → READ_CALENDAR is held" "true" "$(perm_granted READ_CALENDAR)"
assert_eq "G: the views load in place: the notice is gone, the week strip is drawn" "no yes" "$(has_node "$ROW_DIR/G-granted.xml" cal_notice) $(cunder "$ROW_DIR/G-granted.xml" cal_view cal_strip)"
G_SLICE="$(ring_since "$G_MARK")"; printf '%s\n' "$G_SLICE" > "$ROW_DIR/G-slice.txt"
assert_contains "G: the slice from the MARK before the tap holds [calendar] permission request READ_CALENDAR: granted" "[calendar] permission request READ_CALENDAR: granted" "$G_SLICE"
assert_contains "G: … and [app] feeds started (calendar grant)" "[app] feeds started (calendar grant)" "$G_SLICE"
assert_eq "G: … with no restart (the shell's pid is the same as before the tap)" "$PIDG" "$(adb shell pidof app.tileshell < /dev/null | tr -d '\r')"
# A provider change then reaches the tile: an event the driver inserts, one hour from now.
ensure_start
GT_MARK="$(ring_mark)"
GS=$(( ($(device_ms) / 60000 + 60) * 60000 ))
GEV="$(cmkevent "$TESS" 'E18 grant' "$GS" $(( GS + 1800000 )))"
assert_ne "G: the driver inserts an event one hour ahead (a provider change)" "" "$GEV"
for i in 1 2 3 4 5 6 7 8 9 10; do sleep 1; ring_since "$GT_MARK" | grep -qF '[calendar] refresh (provider change)' && break; done
GT_SLICE="$(ring_since "$GT_MARK")"; printf '%s\n' "$GT_SLICE" > "$ROW_DIR/G-tile-slice.txt"
log "the feed after the insert: $(cline "$GT_SLICE" '[calendar] refresh (')"
assert_contains "G: the insert reaches the feed through its observer: [calendar] refresh (provider change) …" "[calendar] refresh (provider change): access=true" "$GT_SLICE"
SEEN=0; : > "$ROW_DIR/G-tile-texts.txt"
for i in $(seq 1 14); do
  gdump "$ROW_DIR/G-start.xml"
  T="$(ctile_texts "$ROW_DIR/G-start.xml" slot:CALENDAR)"; echo "$T" >> "$ROW_DIR/G-tile-texts.txt"
  case "$T" in *"E18 grant"*) SEEN=$((SEEN + 1)); cp "$ROW_DIR/G-start.xml" "$ROW_DIR/G-start-event.xml";; esac
  sleep 1
done
log "the CALENDAR tile's texts over 14 dumps: $(sort "$ROW_DIR/G-tile-texts.txt" | uniq -c | tr '\n' ';')"
assert_ne "G: … and the Calendar tile shows it (dumps of 14 in which the tile reads E18 grant)" "0" "$SEEN"
assert_eq "G: … all without a process restart since before the grant (the same pid)" "$PIDG" "$(adb shell pidof app.tileshell < /dev/null | tr -d '\r')"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore (r3 V10)"
ring_save
for p in READ_CALENDAR WRITE_CALENDAR READ_CONTACTS WRITE_CONTACTS; do
  grant "$p"
  assert_eq "restore: pm grant $p — granted=true" "true" "$(perm_granted "$p")"
done
cpurge "title IN ('E18 save','standup','E18 grant')"
assert_eq "restore: the row's events are deleted" "0" "$(cevent_count "title IN ('E18 save','standup','E18 grant')")"
assert_eq "restore: exactly one Tessera calendar is left" "1" "$(tess_rows)"
assert_eq "restore: no new crash of the shell during the row" "$CRASH0" "$(ccrashes)"
c6; ensure_start
row_end
