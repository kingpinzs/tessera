#!/usr/bin/env bash
# Phase 16 E4 — events both ways (the ContentObserver, no restart), and the pinned Calendar app tile (r3 D12).
#
#   A  the editor      "Standup", tomorrow 09:00–10:00, "Room 2" → the provider row (Tessera, the device zone, the
#                      location); phase 01's Calendar tile shows its face within its next flip (phase 01 E7's form: the
#                      title seen on the tile in a series of Start dumps), driven by CalendarFeed's observer — the shell's
#                      process is the same one before and after
#   B  a driver insert "Dentist" today 14:00 → the Day view lists it with no restart; the first `view day` line after the
#                      MARK with n one more than the line before the MARK is within 2000 ms of the MARK
#   C  app delete      delete from the app → the provider row is gone
#   D  a driver delete `content delete` of Standup → its cal_event node is gone; the next `view` line with n one fewer is
#                      within 2000 ms of the MARK
#   E  pinned tile     "Calendar" pinned from the app list → the pinned app tile shows the same face text as the CALENDAR
#                      slot tile, with a "Dentist" inside the feed's 24 hours. Two clauses-open.tsv lines: the row's own
#                      Dentist is at 14:00 today and deleted by leg C, so this leg inserts a second one 2 h from the
#                      device's now; and the same-face comparison is made with the pinned tile at the slot tile's size
#                      (phase 01 draws event lines on a WIDE tile only; Pin to Start gives a MEDIUM one)
#   restore            the test events deleted, layout_restore of the baseline (removes the pin)
#
# The spec's "tomorrow 09:00" Standup is inside the feed's 24 hours only when the row runs after 09:00 device time.
#
#   W  the tile's window  (added for the fourth fix build, whose tile read starts at the local start of today and keeps
#                      an instance by `InstanceWindow`) three driver events with times relative to the device's now:
#                      one that ENDED earlier today, one starting in 2 hours, one starting in 25 hours → the tile shows
#                      the second only (the feed's faces = the day face and that one), and Tess's typed "what is on my
#                      calendar" names the second only. Time-proof: it reads the same at any hour after 00:30.
# E4_LEGS=W runs leg W and the restore alone — a narrow run; the log's first RECORD says so.
set -uo pipefail
LEGS="${E4_LEGS:-all}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"
TILES="$P16/../phase-15/scripts/tiles.py"
PIN_KEY="app:app.tileshell/app.tileshell.calendar.CalendarActivity:0"
SLOT_KEY="slot:CALENDAR"

row_begin E4 "events both ways: the editor, the observer with no restart, app and driver deletes, the pinned app tile"
if [ "$LEGS" != all ]; then
  [ "$LEGS" = W ] || { _verdict FAIL "E4_LEGS" "only E4_LEGS=W is a narrow run of this row (got $LEGS)"; row_end; exit 1; }
  record "legs run" "W ONLY (the tile's and Tess's window: an event that ended today, one in 2 hours, one in 25 hours) — a narrow run; legs A–E stand on the row's earlier run"
fi
tile_texts() { python3 "$TILES" "$1" "$2" | awk -F'\t' '{ print $6 }' | sed 's/^texts=//'; }   # dump.xml tile-id
# The first `view <mode>` line of a slice whose n equals `want`: prints "<wall − mark> <the line>", or "none".
view_delta() { # slice-file mark mode want-n
  python3 - "$1" "$2" "$3" "$4" <<'PY'
import re, sys
mark, mode, want = int(sys.argv[2]), sys.argv[3], int(sys.argv[4])
for line in open(sys.argv[1], encoding='utf-8', errors='replace'):
    m = re.search(r'wall=(\d+) \[calendar\] view %s [^:]*: (\d+) instances in (\d+) ms' % re.escape(mode), line)
    if m and int(m.group(1)) >= mark and int(m.group(2)) == want:
        print(int(m.group(1)) - mark, line.strip()); break
else:
    print("none")
PY
}
last_n() { printf '%s\n' "$1" | grep -F "[calendar] view $2 " | tail -1 | sed -n 's/.*: \([0-9]*\) instances.*/\1/p'; }   # slice mode
# Scroll Start (already showing) until a tile is in the dump; the dump is left in out.xml.
start_find() { # out.xml tile-id
  local i
  for i in 1 2 3 4 5 6; do
    gdump "$1"
    [ "$(has_node "$1" "tile:$2")" = yes ] && return 0
    adb shell input swipe 540 1700 540 900 400; sleep 1.2
  done
  return 1
}

c6; ensure_start
TZ_ID="$(ctz)"; TODAY="$(cdate 0)"; TOMORROW="$(cdate 1)"
record "the device's time at the row's start (the spec's tomorrow-09:00 event is in the feed's 24 h after 09:00 only)" "$(q "date '+%Y-%m-%d %H:%M %Z'")"
copen
TESS="$(tessera_id)"
assert_ne "precondition: Tessera exists once Calendar has opened" "" "$TESS"
BEFORE="$(ctessera_count)"
note "Tessera id $TESS holds $BEFORE event(s) before the row; zone $TZ_ID; today $TODAY, tomorrow $TOMORROW"

if [ "$LEGS" = all ]; then
# ----------------------------------------------------------------------------------------------- A: the editor
log "--- A: an event created in the editor (Standup, tomorrow 09:00–10:00, Room 2)"
ctap "cal_strip_day:$TOMORROW" 1.5
ctap cal_bar:new 2
dump_ui "$ROW_DIR/A-editor-new.xml"
assert_eq "A: the editor opened (cal_editor)" "yes" "$(has_node "$ROW_DIR/A-editor-new.xml" cal_editor)"
ctype_field title "Standup"
ctype_field location "Room 2"
cset_time start_time 9 00 AM
dump_ui "$ROW_DIR/A-editor-filled.xml"
if [ "$(cfield "$ROW_DIR/A-editor-filled.xml" end_time)" != "10:00 AM" ]; then cset_time end_time 10 00 AM; dump_ui "$ROW_DIR/A-editor-filled.xml"; fi
screencap "$ROW_DIR/A-editor-filled.png"
F="$ROW_DIR/A-editor-filled.xml"
note "the editor before Save: title [$(ctext "$F" cal_editor_field:title)] location [$(ctext "$F" cal_editor_field:location)] start [$(cfield "$F" start_date) $(cfield "$F" start_time)] end [$(cfield "$F" end_date) $(cfield "$F" end_time)]"
assert_eq "A: the driver's own step — nothing is in the provider before Save" "0" "$(cevent_count "title='Standup'")"
PID_A="$(adb shell pidof app.tileshell | tr -d '\r')"
A_MARK="$(ring_mark)"
ctap cal_editor_save 2.5
ROWS="$(cevents title:dtstart:dtend:calendar_id:eventTimezone:eventLocation "title='Standup'")"; log "provider: $ROWS"
START="$(clocal_ms "$TOMORROW" 09:00)"; END="$(clocal_ms "$TOMORROW" 10:00)"
assert_eq "A: the events query lists it — exactly one row titled Standup" "1" "$(printf '%s\n' "$ROWS" | grep -c 'title=Standup,')"
assert_contains "A: … dtstart is tomorrow 09:00" "dtstart=$START," "$ROWS"
assert_contains "A: … dtend is tomorrow 10:00" "dtend=$END," "$ROWS"
assert_contains "A: … in the local calendar (calendar_id = Tessera's)" "calendar_id=$TESS," "$ROWS"
assert_contains "A: … with the device zone" "eventTimezone=$TZ_ID," "$ROWS"
assert_contains "A: … and its location" "eventLocation=Room 2" "$ROWS"
STANDUP="$(event_id Standup "$TESS")"
sleep 1.5
A_SLICE="$(ring_since "$A_MARK")"; printf '%s\n' "$A_SLICE" > "$ROW_DIR/A-slice.txt"
# The tile, phase 01 E7's form: Home (no force-stop — the clause is the observer's, in the process that was running),
# then the title seen on the tile in a series of Start dumps (the tile flips between its day face and its event faces).
ensure_start
SEEN=0; : > "$ROW_DIR/A-tile-texts.txt"
for i in $(seq 1 14); do
  gdump "$ROW_DIR/A-start.xml"
  T="$(tile_texts "$ROW_DIR/A-start.xml" "$SLOT_KEY")"; echo "$T" >> "$ROW_DIR/A-tile-texts.txt"
  case "$T" in *Standup*) SEEN=$((SEEN + 1)); cp "$ROW_DIR/A-start.xml" "$ROW_DIR/A-start-standup.xml";; esac
  sleep 1
done
log "the CALENDAR tile's texts over 14 dumps: $(sort "$ROW_DIR/A-tile-texts.txt" | uniq -c | tr '\n' ';')"
screencap "$ROW_DIR/A-start.png"
assert_ne "A: phase 01's Calendar tile shows the event's face within its next flip (dumps holding Standup, of 14)" "0" "$SEEN"
assert_contains "A: … from CalendarFeed's observer: a refresh (provider change) line after the Save" "[calendar] refresh (provider change): access=true faces=" "$A_SLICE"
FACES="$(printf '%s\n' "$A_SLICE" | grep -F '[calendar] refresh (provider change)' | tail -1 | sed -n 's/.*faces=\([0-9]*\).*/\1/p')"
assert_eq "A: … the feed holds the day face and Standup's (faces=2)" "2" "$FACES"
assert_eq "A: … with no restart of the shell (same pid as before the Save)" "$PID_A" "$(adb shell pidof app.tileshell | tr -d '\r')"
c6; ensure_start

# ----------------------------------------------------------------------------------------------- B: a driver insert
log "--- B: a driver content insert of Dentist today 14:00 → the Day view, no restart"
copen; ctap cal_bar:today 1.2; cview day 2.5
dump_ui "$ROW_DIR/B-day-before.xml"
assert_eq "B: the Day view is showing (cal_view_mode:day selected)" "true" "$(cattr "$ROW_DIR/B-day-before.xml" cal_view_mode:day selected)"
sleep 1
PRE="$(ring_since "$ROW_MARK")"
N0="$(last_n "$PRE" day)"
note "the last view day line before the MARK: $(cline "$PRE" '[calendar] view day ')"
assert_ne "B: a view day line exists before the MARK (its n is the base)" "" "$N0"
PID_B="$(adb shell pidof app.tileshell | tr -d '\r')"
B_MARK="$(ring_mark)"
q "content insert --uri $EVENTS --bind calendar_id:i:$TESS --bind title:s:Dentist --bind dtstart:l:$(clocal_ms "$TODAY" 14:00) --bind dtend:l:$(clocal_ms "$TODAY" 15:00) --bind eventTimezone:s:$TZ_ID" >/dev/null
sleep 2.5
ring_since "$B_MARK" > "$ROW_DIR/B-slice.txt"
DENTIST="$(event_id Dentist "$TESS")"
assert_ne "B: the driver's insert landed (Dentist, today 14:00)" "" "$DENTIST"
dump_ui "$ROW_DIR/B-day-dentist.xml"
assert_eq "B: the day view lists cal_event_title: \"Dentist\"" "Dentist" "$(ctext "$ROW_DIR/B-day-dentist.xml" "cal_event_title:$DENTIST")"
assert_eq "B: … with no restart (same pid)" "$PID_B" "$(adb shell pidof app.tileshell | tr -d '\r')"
VD="$(view_delta "$ROW_DIR/B-slice.txt" "$B_MARK" day $(( ${N0:-0} + 1 )))"; log "observer: $VD"
assert_ne "B: a view day line with n one more than before the MARK ($N0 → $(( ${N0:-0} + 1 ))) follows the insert" "none" "$VD"
assert_within "B: … its wall − MARK ≤ 2000 ms (T16-17)" 1000 "${VD%% *}" 1000

# ----------------------------------------------------------------------------------------------- C: delete from the app
log "--- C: delete from the app → the provider row is gone"
ctap "cal_event:$DENTIST" 2; dump_ui "$ROW_DIR/C-page.xml"
assert_eq "C: Dentist's page is open (cal_event_page:<id>)" "yes" "$(has_node "$ROW_DIR/C-page.xml" "cal_event_page:$DENTIST")"
C_MARK="$(ring_mark)"
ctap cal_event_action:delete 2.5
dump_ui "$ROW_DIR/C-after.xml"
if [ "$(has_node "$ROW_DIR/C-after.xml" cal_delete)" = yes ]; then note "a delete prompt showed: $(cids "$ROW_DIR/C-after.xml" cal_delete)"; fi
assert_eq "C: the provider row is gone (no events row with its _id, deleted or not)" "0" "$(cevent_count "_id=$DENTIST")"
assert_contains "C: … through the write layer" "[calendar] write delete event=$DENTIST: ok" "$(ring_since "$C_MARK")"

# ----------------------------------------------------------------------------------------------- D: a driver delete
log "--- D: a driver content delete of Standup → its node is gone, the view line follows"
copen_day "$START"
dump_ui "$ROW_DIR/D-day-before.xml"
assert_eq "D: precondition — tomorrow's Day view shows Standup's cal_event node" "yes" "$(has_node "$ROW_DIR/D-day-before.xml" "cal_event:$STANDUP")"
sleep 1
PRE="$(ring_since "$ROW_MARK")"
N1="$(last_n "$PRE" day)"
note "the last view day line before the MARK: $(cline "$PRE" '[calendar] view day ')"
PID_D="$(adb shell pidof app.tileshell | tr -d '\r')"
D_MARK="$(ring_mark)"
q "content delete --uri $EVENTS/$STANDUP" >/dev/null
sleep 2.5
ring_since "$D_MARK" > "$ROW_DIR/D-slice.txt"
dump_ui "$ROW_DIR/D-day-after.xml"
assert_eq "D: Standup's cal_event node is gone" "no" "$(has_node "$ROW_DIR/D-day-after.xml" "cal_event:$STANDUP")"
VD="$(view_delta "$ROW_DIR/D-slice.txt" "$D_MARK" day $(( ${N1:-1} - 1 )))"; log "observer: $VD"
assert_ne "D: the next view line with n one fewer ($N1 → $(( ${N1:-1} - 1 )))" "none" "$VD"
assert_within "D: … its wall − MARK ≤ 2000 ms" 1000 "${VD%% *}" 1000
assert_eq "D: … with no restart (same pid)" "$PID_D" "$(adb shell pidof app.tileshell | tr -d '\r')"

# ----------------------------------------------------------------------------------------------- E: the pinned app tile
log "--- E: Calendar pinned from the app list — the pinned tile's face is the slot tile's (r3 D12)"
NOW="$(device_ms)"; D2S=$(( (NOW / 60000 + 120) * 60000 ))
DENTIST2="$(cmkevent "$TESS" Dentist "$D2S" $(( D2S + 3600000 )))"
assert_ne "E: a Dentist inside the feed's 24 hours (2 h from the device's now; clauses-open.tsv)" "" "$DENTIST2"
c6; ensure_start
# Phase 02's Pin to Start, from the app list: the jump grid to C, a hold on Calendar's row, the menu's Pin to Start.
adb shell input swipe 900 1200 150 1200 250; sleep 2
for i in 1 2 3 4 5; do
  dump_ui "$ROW_DIR/.nav.xml"
  H="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/.nav.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
  [ -n "$H" ] && break
  adb shell input swipe 540 800 540 1700 300; sleep 1
done
tap_node "$ROW_DIR/.nav.xml" "$H"; sleep 1.5
dump_ui "$ROW_DIR/.grid.xml"; tap_node "$ROW_DIR/.grid.xml" "jump_cell:C"; sleep 1.5
ROWTAG="applist_name:$CALENDAR_ACTIVITY"
scroll_to_node "$ROW_DIR/E-applist.xml" "$ROWTAG" 3 >/dev/null 2>&1 || true
prevb=""
for i in 1 2 3 4 5 6; do
  b="$(bounds "$ROW_DIR/E-applist.xml" "$ROWTAG")"
  [ -n "$b" ] && [ "$b" = "$prevb" ] && break
  prevb="$b"; sleep 0.7; dump_ui "$ROW_DIR/E-applist.xml"
done
assert_eq "E: the app list's row reads Calendar" "Calendar" "$(ctext "$ROW_DIR/E-applist.xml" "$ROWTAG")"
read -r x1 y1 x2 y2 <<< "$b"
adb shell input swipe $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) 1000; sleep 1.5
dump_ui "$ROW_DIR/E-menu.xml"
assert_eq "E: its hold menu offers Pin to Start" "yes" "$(has_node "$ROW_DIR/E-menu.xml" applist_menu_pin)"
tap_node "$ROW_DIR/E-menu.xml" applist_menu_pin; sleep 2.5
ensure_start
for i in $(seq 1 10); do layout_json | tr -d '\r' > "$ROW_DIR/E-layout-pinned.json"; grep -qF "\"$PIN_KEY\"" "$ROW_DIR/E-layout-pinned.json" && break; sleep 1; done
PIN_SIZE="$(python3 -c 'import json, sys; print(next((o["size"] for o in json.load(open(sys.argv[1]))["order"] if o["key"] == sys.argv[2]), ""))' "$ROW_DIR/E-layout-pinned.json" "$PIN_KEY")"
assert_ne "E: the layout holds the pinned Calendar app tile (its size, as pinned)" "" "$PIN_SIZE"
SLOT_SIZE="$(python3 -c 'import json, sys; print(next((o["size"] for o in json.load(open(sys.argv[1]))["order"] if o["key"] == sys.argv[2]), ""))' "$ROW_DIR/E-layout-pinned.json" "$SLOT_KEY")"
note "sizes: the slot tile $SLOT_SIZE, the pinned tile $PIN_SIZE"
# Both tiles' face texts over one series of dumps (the faces flip): each line of out-<tile>.txt is one dump's texts.
series() { # label
  local i; : > "$ROW_DIR/E-$1-slot.txt"; : > "$ROW_DIR/E-$1-pin.txt"
  start_find "$ROW_DIR/E-start.xml" "$PIN_KEY" >/dev/null
  for i in $(seq 1 16); do
    gdump "$ROW_DIR/E-start.xml"
    tile_texts "$ROW_DIR/E-start.xml" "$SLOT_KEY" >> "$ROW_DIR/E-$1-slot.txt"
    tile_texts "$ROW_DIR/E-start.xml" "$PIN_KEY" >> "$ROW_DIR/E-$1-pin.txt"
    case "$(tail -1 "$ROW_DIR/E-$1-pin.txt")" in *Dentist*) cp "$ROW_DIR/E-start.xml" "$ROW_DIR/E-$1-start-dentist.xml";; esac
    sleep 1
  done
  cp "$ROW_DIR/E-start.xml" "$ROW_DIR/E-$1-start-last.xml"; screencap "$ROW_DIR/E-$1-start.png"
}
faces() { grep -v '^$' "$1" | sort -u | paste -sd';'; }   # the distinct texts a series saw
DAYNAME="$(adb shell date +%A | tr -d '\r')"; DAYNUM="$(adb shell date +%-d | tr -d '\r')"

# At the size Pin to Start gives. Phase 01's tile rule draws a calendar event's lines on a WIDE tile only
# (start/TileView.kt: `face.eventLines.isNotEmpty() && model.size == TileSize.WIDE`), so a tile at another size shows
# the feed's day face on every face: what is asserted here is that the pinned tile is LIVE — it shows CalendarFeed's
# day face, which only content under its component key can give it — and what each tile showed is recorded.
series pinned-size
assert_eq "E: the pinned tile is on Start (tile:$PIN_KEY)" "yes" "$(has_node "$ROW_DIR/E-pinned-size-start-last.xml" "tile:$PIN_KEY")"
record "E: at the pinned size ($PIN_SIZE) — the slot tile's faces ($SLOT_SIZE)" "$(faces "$ROW_DIR/E-pinned-size-slot.txt")"
record "E: at the pinned size ($PIN_SIZE) — the pinned tile's faces" "$(faces "$ROW_DIR/E-pinned-size-pin.txt")"
assert_contains "E: the pinned tile is live: it shows CalendarFeed's day face (the day name and number)" "Calendar|$DAYNAME|$DAYNUM" "$(faces "$ROW_DIR/E-pinned-size-pin.txt")"

# At the slot tile's size, so the two tiles are drawn by one rule: the pinned tile set to the slot tile's size in the
# layout file, written with the verified layout_restore (clauses-open.tsv).
python3 -c '
import json, sys
d = json.load(open(sys.argv[1]))
for o in d["order"]:
    if o["key"] == sys.argv[3]: o["size"] = sys.argv[4]
d["manualSizes"] = sorted(set(d.get("manualSizes", [])) | {sys.argv[3]})
json.dump(d, open(sys.argv[2], "w"))' "$ROW_DIR/E-layout-pinned.json" "$ROW_DIR/E-layout-same-size.json" "$PIN_KEY" "$SLOT_SIZE"
ring_save
layout_restore "$ROW_DIR/E-layout-same-size.json"; assert_eq "E: the pinned tile set to the slot tile's size ($SLOT_SIZE) — layout_restore" "0" "$?"
ensure_start
series same-size
SLOT_SET="$(faces "$ROW_DIR/E-same-size-slot.txt")"; PIN_SET="$(faces "$ROW_DIR/E-same-size-pin.txt")"
log "the slot tile's faces:   $SLOT_SET"
log "the pinned tile's faces: $PIN_SET"
assert_contains "E: with \"Dentist\" inside the feed's 24 hours, the CALENDAR slot tile shows Dentist's face" "Dentist" "$SLOT_SET"
assert_contains "E: the pinned tile's dump holds Dentist's face" "Dentist" "$PIN_SET"
assert_eq "E: the pinned tile's dump holds the same face text as the CALENDAR slot tile (every face seen in 16 dumps)" "$SLOT_SET" "$PIN_SET"
PUB="$(csince "$ROW_MARK")"
assert_contains "E: … both published by CalendarFeed: under the slot key" "[engine] publish feed:calendar faces=2" "$PUB"
assert_contains "E: … and under the component key" "[engine] publish cmp:app.tileshell/app.tileshell.calendar.CalendarActivity faces=2" "$PUB"

cpurge "title IN ('Standup','Dentist')"
ring_save
layout_restore "$BASELINE"; assert_eq "E: the baseline layout again before leg W (the pin removed)" "0" "$?"
fi   # legs A to E

# ----------------------------------------------------------------------------------------------- W: the tile's window
log "--- W: what the tile and Tess keep — an event that ended today, one in 2 hours, one in 25 hours"
c6; ensure_start
cpurge "title IN ('E4 ended','E4 soon','E4 later')"
WNOW="$(device_ms)"; WMID="$(clocal_ms "$(cdate 0)" 00:00)"
record "W: the device's time" "$(q "date '+%Y-%m-%d %H:%M %Z'")"
assert_eq "W: precondition — it is at least 30 minutes after local midnight (an event can have ended today)" "yes" "$([ $(( WNOW - WMID )) -ge 1800000 ] && echo yes || echo no)"
WE_S=$(( WNOW - 7200000 )); [ "$WE_S" -lt $(( WMID + 60000 )) ] && WE_S=$(( WMID + 60000 ))
WE_E=$(( WNOW - 600000 ))
WS_S=$(( (WNOW / 60000 + 120) * 60000 )); WL_S=$(( (WNOW / 60000 + 1500) * 60000 ))
W_MARK="$(ring_mark)"
W_ENDED="$(cmkevent "$TESS" 'E4 ended' "$WE_S" "$WE_E")"
W_SOON="$(cmkevent "$TESS" 'E4 soon' "$WS_S" $(( WS_S + 3600000 )))"
W_LATER="$(cmkevent "$TESS" 'E4 later' "$WL_S" $(( WL_S + 3600000 )))"
assert_eq "W: fixtures — three events in Tessera: ended today ($(( (WNOW - WE_E) / 60000 )) min ago), starting in 2 hours, starting in 25 hours" "3" "$(cevent_count "_id IN (${W_ENDED:-0},${W_SOON:-0},${W_LATER:-0}) AND deleted=0")"
assert_eq "W: fixtures — the ended one began after the local start of today" "yes" "$([ "$WE_S" -ge "$WMID" ] && echo yes || echo no)"
for i in 1 2 3 4 5 6 7 8; do sleep 1; ring_since "$W_MARK" | grep -F '[calendar] refresh (' | tail -1 | grep -q 'faces=' && [ "$(ring_since "$W_MARK" | grep -cF '[calendar] refresh (')" -ge 3 ] && break; done
sleep 2
W_SLICE="$(ring_since "$W_MARK")"; printf '%s\n' "$W_SLICE" > "$ROW_DIR/W-slice.txt"
log "the feed after the three inserts: $(cline "$W_SLICE" '[calendar] refresh (')"
assert_eq "W: the feed's last refresh counts two faces — the day face and the event in 2 hours" "faces=2" "$(cline "$W_SLICE" '[calendar] refresh (' | grep -oE 'faces=[0-9]+')"
: > "$ROW_DIR/W-tile-texts.txt"
for i in $(seq 1 16); do
  gdump "$ROW_DIR/W-start.xml"
  tile_texts "$ROW_DIR/W-start.xml" "$SLOT_KEY" >> "$ROW_DIR/W-tile-texts.txt"
  sleep 1
done
screencap "$ROW_DIR/W-start.png"
log "the CALENDAR tile's texts over 16 dumps: $(sort "$ROW_DIR/W-tile-texts.txt" | uniq -c | tr '\n' ';')"
assert_ne "W: the tile shows the timed event inside the next 24 hours (dumps of 16 reading E4 soon)" "0" "$(grep -cF 'E4 soon' "$ROW_DIR/W-tile-texts.txt")"
assert_eq "W: … the one that ended earlier today does not come back (dumps reading E4 ended)" "0" "$(grep -cF 'E4 ended' "$ROW_DIR/W-tile-texts.txt")"
assert_eq "W: … and the one starting in 25 hours is not shown (dumps reading E4 later)" "0" "$(grep -cF 'E4 later' "$ROW_DIR/W-tile-texts.txt")"
tess_ask "what is on my calendar" 5
W_REPLY="$(reply_since "$TMARK" | sed "s/&apos;/'/g")"; log "Tess: [$W_REPLY]"
assert_contains "W: Tess's typed \"what is on my calendar\" names the event in 2 hours" "E4 soon" "$W_REPLY"
assert_absent "W: … not the one that ended earlier today" "E4 ended" "$W_REPLY"
assert_absent "W: … and not the one starting in 25 hours" "E4 later" "$W_REPLY"
ring_since "$TMARK" > "$ROW_DIR/W-tess-slice.txt"
tess_close

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
ring_save
cpurge "title IN ('Standup','Dentist','E4 ended','E4 soon','E4 later')"
assert_eq "restore: the test events are deleted" "0" "$(cevent_count "title IN ('Standup','Dentist','E4 ended','E4 soon','E4 later')")"
assert_eq "restore: Tessera holds what it held before the row" "$BEFORE" "$(ctessera_count)"
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline (removes the pin)" "0" "$?"
assert_absent "restore: … the pinned tile is gone from the layout" "\"$PIN_KEY\"" "$(layout_json | tr -d ' \r\n')"
ensure_start
row_end
