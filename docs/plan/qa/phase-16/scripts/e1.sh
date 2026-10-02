#!/usr/bin/env bash
# Phase 16 E1 — slot takeover and the guard (the re-cut of phase 01 E4 / E4b; Q-16-1; r3 D3, D8, V2, V5).
#
#   children  (each takes the device lock itself, so they run before the row holds it; their own row directories are
#             moved aside first and put back after, the new runs kept under E1/):
#               phase 01 E4 / E4b      e4_part1.sh, e4_part2.sh (re-cut, task 8): the remaining proofs on this build
#               phase 14 E3            e3.sh with its Agenda-launch precondition re-cut (task 8)
#               phase 15 E0            the emulator driver of phase 10 E10–E13's tile rule ("the same build passes phase
#                                      10's E10 and E13"), seeded with THIS phase's baseline (E0_BASELINE)
#   W  wiped state     pm clear → provision.sh → ensure_start: the two slot tiles, their taps, layout_json, two handlers
#                      per category, exactly two candidates in each picker, a re-point that sticks across force-stop
#                      and reboot
#   U  upgrade         uninstall; provision the last pre-16 build; a hand pick on PEOPLE; adb install -r this build:
#                      PEOPLE taken over once ("replaced user's …"), CALENDAR assigned; pointing it back sticks
#   G  guard           slot:music:v1 un-run over a hand pick (Auxio): kept
#   A  Agenda pod      the pod's header opens the shell's Calendar (the component read from the baseline FILE)
#   M  Music negative  the shell's Music playing: the CALENDAR and PEOPLE tiles keep their own faces and sizes
#   restore            pm clear → provision.sh → ensure_start → layout_restore of the baseline
#
# E1_CHILDREN=0 skips the children (driver development); the gate run never sets it.
# E1_PART=children judges ONLY the children, as row E1_CHILDREN (the owner's ruling of 2026-10-01, "only test the
#   fixes": a part that failed is run again alone, the legs that passed are not). With E1_REUSE=<an earlier E1 run's
#   folder> the children that passed there (phase 01 E4 / E4b, phase 14 E3) are read from that folder and only
#   phase 15's E0 runs again; the log says which is which.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
QAR="$(cd "$QA/.." && pwd)"
PART="${E1_PART:-all}"
ROWID=E1; [ "$PART" = children ] && ROWID=E1_CHILDREN
OUT="$QA/$ROWID"; mkdir -p "$OUT"
TILES="$QAR/phase-15/scripts/tiles.py"
OLD_APK="$QA/upgrade/phase-14-aaf9a1d8.apk"
NEW_APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"
SHELL_CAL="app.tileshell/app.tileshell.calendar.CalendarActivity"
SHELL_PEOPLE="app.tileshell/app.tileshell.people.PeopleActivity"

slot_of() { layout_json | python3 -c 'import json, sys; print(json.load(sys.stdin).get("slots", {}).get(sys.argv[1], ""))' "$1"; }
added_once() { layout_json | python3 -c 'import json, sys; print(" ".join(sorted(json.load(sys.stdin).get("addedOnce", []))))'; }
short() { python3 -c 'import sys; p, c = sys.argv[1].split("/"); print(p + "/" + (c[len(p):] if c.startswith(p + ".") else c))' "$1"; }
flat() { python3 -c 'import sys; p, c = sys.argv[1].split("/"); print(p + "/" + (p + c if c.startswith(".") else c))' "$1"; }
handlers() { adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c "android.intent.category.$1" | tr -d '\r' | grep '/' | tr -d ' '; }
tile_field() { # dump.xml id field
  python3 "$TILES" "$1" "$2" | awk -F'\t' -v f="$3" '{ m["bounds"] = $2; m["size"] = $3; m["controls"] = $4; m["badge"] = $5; m["texts"] = $6 } END { print m[f] }'
}
tile_desc() { # dump.xml tile-id -> the tile node's own content-desc (the app's label when assigned)
  python3 -c '
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if n.get("resource-id") == "tile:" + sys.argv[2]:
        print(n.get("content-desc", "")); break' "$1" "$2"
}
start_dump() { ensure_start; gdump "$1"; }
wizard_absent() { [ "$(has_node "$1" start_page)" = yes ] && [ "$(has_node "$1" wizard_page)" = no ] && echo yes || echo no; }
candidates() { grep -o 'resource-id="slot_candidate:[^"]*"' "$1" | sed 's/resource-id="slot_candidate://; s/"$//' | sort | tr '\n' ' ' | sed 's/ $//'; }
# Settings > Tile apps → the slot's picker, dumped.
open_picker() { # slot out.xml
  adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page TILE_APPS >/dev/null 2>&1; sleep 3
  dump_ui "$OUT/.tileapps.xml"
  scroll_to_node "$OUT/.tileapps.xml" "tile_app_slot:$1" 6 >/dev/null 2>&1 || true
  tap_node "$OUT/.tileapps.xml" "tile_app_slot:$1"; sleep 2
  dump_ui "$2"
}
provision() { # label [env...]  -> rc file under the row
  local label="$1"; shift
  ( env "$@" bash "$P03S/provision.sh" > "$OUT/provision-$label.out" 2>&1; echo $? > "$OUT/provision-$label.rc" )
  cat "$OUT/provision-$label.rc"
}
boot_poll() { adb wait-for-device; local i; for i in $(seq 1 120); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 2; done; sleep 15; }

# =============================================================================================== children
# A lib.sh row of another phase, unchanged but for its named re-cut: its own row directory moved aside and put back, the
# new run kept under E1/.
run_row() { # phase row script [env...]
  local phase="$1" row="$2" script="$3"; shift 3
  local dir="$QAR/$phase/$row" keep="$QAR/$phase/$row.p16-e1-aside"
  [ -e "$dir" ] && mv "$dir" "$keep"
  ( env "$@" bash "$QAR/$phase/scripts/$script" > "$OUT/$phase-$row.out" 2>&1; echo $? > "$OUT/$phase-$row.rc" )
  rm -rf "$OUT/$phase-$row"; [ -e "$dir" ] && mv "$dir" "$OUT/$phase-$row"
  [ -e "$keep" ] && mv "$keep" "$dir"
  adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 5
}
REUSED=""
if [ "${E1_CHILDREN:-1}" = "1" ] && [ "$PART" = children ] && [ -n "${E1_REUSE:-}" ]; then
  # Only phase 15's E0 runs; the other children's evidence is the earlier run's, copied as it was.
  REUSED="$E1_REUSE"
  for f in children-seed.out children-seed.rc children-seed2.out children-seed2.rc phase-01-E04 phase-01-E04-part1.out phase-01-E04-part1.rc \
           phase-01-E04-part2.out phase-01-E04-part2.rc phase-14-E3 phase-14-E3.out phase-14-E3.rc; do
    rm -rf "$OUT/$f"; cp -a "$E1_REUSE/$f" "$OUT/$f"
  done
  layout_restore "$BASELINE" > "$OUT/children-seed3.out" 2>&1; echo $? > "$OUT/children-seed3.rc"
  run_row phase-15 E0 e0.sh "E0_BASELINE=$BASELINE"
elif [ "${E1_CHILDREN:-1}" = "1" ]; then
  # The children read Start's grid: they start from this phase's baseline (an older baseline lacks the two markers).
  layout_restore "$BASELINE" > "$OUT/children-seed.out" 2>&1; echo $? > "$OUT/children-seed.rc"
  # Phase 01 E4 / E4b: its drivers log to <dir>/E04.txt and restore the layout they found (e4_part2.sh ends with it).
  rm -rf "$OUT/phase-01-E04.prev"; [ -e "$OUT/phase-01-E04" ] && mv "$OUT/phase-01-E04" "$OUT/phase-01-E04.prev"
  mkdir -p "$OUT/phase-01-E04"
  ( bash "$P01S/e4_part1.sh" "$OUT/phase-01-E04" > "$OUT/phase-01-E04-part1.out" 2>&1; echo $? > "$OUT/phase-01-E04-part1.rc" )
  ( bash "$P01S/e4_part2.sh" "$OUT/phase-01-E04" > "$OUT/phase-01-E04-part2.out" 2>&1; echo $? > "$OUT/phase-01-E04-part2.rc" )
  layout_restore "$BASELINE" > "$OUT/children-seed2.out" 2>&1; echo $? > "$OUT/children-seed2.rc"
  run_row phase-14 E3 e3.sh
  layout_restore "$BASELINE" > "$OUT/children-seed3.out" 2>&1; echo $? > "$OUT/children-seed3.rc"
  run_row phase-15 E0 e0.sh "E0_BASELINE=$BASELINE"
fi

# =============================================================================================== the row
if [ "$PART" = children ]; then
  row_begin "$ROWID" "E1's children only: phase 01 E4 / E4b, phase 14 E3, phase 15 E0 on this build"
  [ -n "$REUSED" ] && record "children read from an earlier run, not run again" "phase 01 E4 / E4b and phase 14 E3, from ${REUSED#"$QA"/} (its log names the same APK: $(grep -m1 '^apk installed' "$REUSED/E1.txt" | awk '{print $3}')); phase 15 E0 ran now"
  [ -n "$REUSED" ] && assert_eq "the reused children ran on the APK installed now" "$(grep -m1 '^apk installed' "$LOG" | awk '{print $3}')" "$(grep -m1 '^apk installed' "$REUSED/E1.txt" | awk '{print $3}')"
else
  row_begin E1 "slot takeover and the guard: wiped, upgrade, point back, guard, Agenda pod, Music negative"
fi
record "this build" "$(md5sum "$NEW_APK" | cut -c1-16) $(stat -c%s "$NEW_APK") bytes ($(git -C "$REPO" rev-parse --short HEAD))"
record "the pre-16 build for the upgrade leg" "$(md5sum "$OLD_APK" 2>/dev/null | cut -c1-16) $(stat -c%s "$OLD_APK" 2>/dev/null) bytes — phase-14's c0d094ad app tree (T16-7)"
assert_eq "the pre-16 APK is the kept one (md5 aaf9a1d8ed536d25)" "aaf9a1d8ed536d25" "$(md5sum "$OLD_APK" | cut -c1-16)"
CONTACTS_SHORT="$(handlers APP_CONTACTS | grep -v '^app.tileshell/' | head -1)"
AOSP_CAL_SHORT="$(handlers APP_CALENDAR | grep -v '^app.tileshell/' | head -1)"
note "the image's Contacts: $CONTACTS_SHORT; the AOSP Calendar: $AOSP_CAL_SHORT"

if [ "${E1_CHILDREN:-1}" = "1" ]; then
  log "--- children: phase 01 E4 / E4b, phase 14 E3, phase 15 E0 (phase 10 E10–E13's tile rule) on this build"
  for f in children-seed children-seed2 children-seed3; do assert_eq "$f: layout_restore of the baseline" "0" "$(cat "$OUT/$f.rc")"; done
  E04="$OUT/phase-01-E04/E04.txt"
  assert_eq "phase 01 e4_part1.sh rc" "0" "$(cat "$OUT/phase-01-E04-part1.rc")"
  # e4_part2.sh's exit code is its last command's — `grep -c FATAL` over the crash buffer, which exits 1 when it counts
  # 0 — so the verdict is the count it logged, and the code is recorded (the fix build's first run asserted the code).
  record "phase 01 e4_part2.sh rc (its last command is grep -c: 1 = it counted no crash)" "$(cat "$OUT/phase-01-E04-part2.rc")"
  assert_eq "E4b ran to its end: the crash-buffer count it logged is 0" "0" "$(grep -A1 -F '$ crash buffer since this run' "$E04" | tail -1)"
  # E4's remaining proofs (Decisions "Phase 01 E4 / E4b re-cut"): the one-handler category auto-assigns (Maps); the
  # 2+-handler categories stay unassigned (Mail, Store) — and, with the shell's own apps declared, Calendar and People
  # are 2-handler categories too. A SMALL tile draws no label, so "unassigned" is read where E4 reads it for every size:
  # the tile's content-desc is the slot's label and the Tile apps row says "not chosen yet".
  assert_eq "E4: APP_MAPS has one handler" "1" "$(grep -m1 '^APP_MAPS:' "$E04" | tr ' ' '\n' | grep -c '/')"
  assert_contains "E4: the Maps tile is auto-assigned (no Tap to choose)" "tile:slot:MAPS" "$(grep 'tile:slot:MAPS' "$E04" | grep -v 'Tap to choose' | head -1)"
  assert_contains "E4: … and its Tile apps row names Android's default (the one handler)" "Android's default" "$(grep -m1 'tile_app_slot:MAPS' "$E04")"
  assert_contains "E4: the Mail tile is unassigned" "Tap to choose" "$(grep -m1 'tile:slot:MAIL' "$E04")"
  assert_contains "E4: … and its Tile apps row says so" "not chosen yet" "$(grep -m1 'tile_app_slot:MAIL' "$E04")"
  assert_contains "E4: the Store tile is unassigned (its desc is the slot's label)" "desc=Store" "$(grep -m1 'tile:slot:STORE' "$E04")"
  assert_contains "E4: … and its Tile apps row says so" "Store / None · not chosen yet" "$(grep -m1 'tile_app_slot:STORE' "$E04")"
  assert_eq "E4: APP_CALENDAR has two handlers on this build" "2" "$(grep -m1 '^APP_CALENDAR:' "$E04" | tr ' ' '\n' | grep -c '/')"
  assert_eq "E4: APP_CONTACTS has two handlers on this build" "2" "$(grep -m1 '^APP_CONTACTS:' "$E04" | tr ' ' '\n' | grep -c '/')"
  # RECORDED, not asserted (INDEX Change Log 2026-10-01, "E1's children"): the doc's "two handlers each, so neither
  # auto-assigns" cannot be seen on a device. In the state a user can reach, the seed has pointed both slots at the
  # shell's apps (legs W and U assert that). In E4's emptied layout — `slots` {} with the markers kept, a state only the
  # harness can make — phase 01's resolver asks Android for a default (`resolveActivity`, MATCH_DEFAULT_ONLY) and gets
  # the image's app, whose filter alone carries CATEGORY_DEFAULT: the row reads "Android's default", not "Tap to choose".
  record "E4's emptied layout: the Calendar tile" "$(grep -m1 'tile:slot:CALENDAR' "$E04")"
  record "E4's emptied layout: the People tile and its Tile apps row" "$(grep -m1 'tile:slot:PEOPLE' "$E04") ; $(grep -m1 'tile_app_slot:PEOPLE' "$E04")"
  # E4b: the picker lists the category's handlers by component, and the re-cut taps chose K-9 and FairEmail.
  assert_contains "E4b: the Mail picker lists K-9 by component" "com.fsck.k9/" "$(grep -m1 '^picker candidates:' "$E04")"
  assert_contains "E4b: choosing K-9 assigned the slot" "com.fsck.k9" "$(grep -m1 '^layout slots:' "$E04")"
  note "E04.txt: $(grep -c . "$E04") lines; kept at $OUT/phase-01-E04/"
  assert_eq "phase 14 e3.sh (precondition re-cut) rc" "0" "$(cat "$OUT/phase-14-E3.rc")"
  assert_contains "phase 14 E3: 0 failed" " passed, 0 failed" "$(tail -3 "$OUT/phase-14-E3/E3.txt" 2>/dev/null)"
  assert_contains "phase 14 E3 asserted the re-cut precondition" "PASS  precondition: the baseline's slots.CALENDAR is the shell's Calendar" "$(cat "$OUT/phase-14-E3/E3.txt" 2>/dev/null)"
  assert_eq "phase 15 e0.sh (phase 10 E10–E13's tile rule) rc" "0" "$(cat "$OUT/phase-15-E0.rc")"
  assert_contains "phase 15 E0: 0 failed" " passed, 0 failed" "$(tail -3 "$OUT/phase-15-E0/E0.txt" 2>/dev/null)"
else
  record "children" "SKIPPED (E1_CHILDREN=0): a development run, not the gate's"
fi
if [ "$PART" = children ]; then row_end; exit $?; fi

# ----------------------------------------------------------------------------------------------- W: wiped state
log "--- W: wiped state (pm clear → provision.sh → ensure_start)"
adb shell pm clear app.tileshell >/dev/null
assert_eq "W: provision.sh rc" "0" "$(provision wiped)"
start_dump "$OUT/W-start.xml"
assert_eq "W: Start, no wizard" "yes" "$(wizard_absent "$OUT/W-start.xml")"
assert_eq "W: the CALENDAR slot tile reads Calendar" "Calendar" "$(tile_desc "$OUT/W-start.xml" slot:CALENDAR)"
assert_eq "W: the PEOPLE slot tile reads People" "People" "$(tile_desc "$OUT/W-start.xml" slot:PEOPLE)"
assert_absent "W: the CALENDAR tile is assigned (no Tap to choose)" "Tap to choose" "$(tile_field "$OUT/W-start.xml" slot:CALENDAR texts)"
assert_absent "W: the PEOPLE tile is assigned (no Tap to choose)" "Tap to choose" "$(tile_field "$OUT/W-start.xml" slot:PEOPLE texts)"
L="$(added_once)"
assert_contains "W: addedOnce holds slot:calendar:v1" "slot:calendar:v1" "$L"
assert_contains "W: addedOnce holds slot:people:v1" "slot:people:v1" "$L"
assert_eq "W: slots.CALENDAR is the shell's Calendar" "$SHELL_CAL" "$(slot_of CALENDAR)"
assert_eq "W: slots.PEOPLE is the shell's People" "$SHELL_PEOPLE" "$(slot_of PEOPLE)"
for c in APP_CALENDAR APP_CONTACTS; do
  H="$(handlers "$c")"; note "$c handlers: $(echo $H)"
  assert_eq "W: $c lists two handlers (so neither auto-assigns)" "2" "$(printf '%s\n' "$H" | grep -c '/')"
  assert_contains "W: … one of them the shell" "app.tileshell/" "$H"
done
gtap_tile() { tap_node "$1" "tile:$2"; sleep 4; }
gtap_tile "$OUT/W-start.xml" slot:CALENDAR
assert_eq "W: the CALENDAR tile's tap resumes the shell's Calendar" "$CALENDAR_ACTIVITY" "$(top_activity)"
c6; start_dump "$OUT/W-start2.xml"
gtap_tile "$OUT/W-start2.xml" slot:PEOPLE
assert_eq "W: the PEOPLE tile's tap resumes the shell's People" "$PEOPLE_ACTIVITY" "$(top_activity)"
c6; ensure_start

open_picker CALENDAR "$OUT/W-picker-calendar.xml"
assert_eq "W: the CALENDAR picker holds exactly the shell's Calendar and the AOSP Calendar (r3 D3)" \
  "$(printf '%s\n%s\n' "$CALENDAR_ACTIVITY" "$AOSP_CAL_SHORT" | sort | tr '\n' ' ' | sed 's/ $//')" "$(candidates "$OUT/W-picker-calendar.xml")"
adb shell input keyevent KEYCODE_BACK; sleep 1
open_picker PEOPLE "$OUT/W-picker-people.xml"
assert_eq "W: the PEOPLE picker holds exactly the shell's People and the image's Contacts (r3 D3)" \
  "$(printf '%s\n%s\n' "$PEOPLE_ACTIVITY" "$CONTACTS_SHORT" | sort | tr '\n' ' ' | sed 's/ $//')" "$(candidates "$OUT/W-picker-people.xml")"
adb shell input keyevent KEYCODE_BACK; sleep 1

# A re-point sticks (E4b's form): the AOSP Calendar's candidate, then force-stop, then reboot.
open_picker CALENDAR "$OUT/W-picker-calendar2.xml"
tap_node "$OUT/W-picker-calendar2.xml" "slot_candidate:$AOSP_CAL_SHORT"; sleep 2
AOSP_CAL_FLAT="$(flat "$AOSP_CAL_SHORT")"
assert_eq "W: the tap re-points CALENDAR at the AOSP Calendar" "$AOSP_CAL_FLAT" "$(slot_of CALENDAR)"
ring_save; adb shell am force-stop app.tileshell; sleep 1; ensure_start
assert_eq "W: … and it sticks across am force-stop" "$AOSP_CAL_FLAT" "$(slot_of CALENDAR)"
ring_save; adb reboot; boot_poll
assert_eq "W: the device is awake after the reboot (C-25)" "Awake" "$(wake_device)"
ensure_start
assert_eq "W: … and across adb reboot" "$AOSP_CAL_FLAT" "$(slot_of CALENDAR)"
start_dump "$OUT/W-start-repointed.xml"
gtap_tile "$OUT/W-start-repointed.xml" slot:CALENDAR
assert_eq "W: … the tile's tap resumes the AOSP Calendar" "$AOSP_CAL_SHORT" "$(top_activity)"
c6; ensure_start

# ----------------------------------------------------------------------------------------------- U: the upgrade pass
log "--- U: upgrade (uninstall; provision the pre-16 build; a hand pick; adb install -r this build)"
ring_save
adb uninstall app.tileshell > "$OUT/U-uninstall.out" 2>&1
assert_eq "U: provision.sh with the pre-16 APK rc" "0" "$(provision old "TILESHELL_APK=$OLD_APK")"
assert_eq "U: the device holds the pre-16 build" "aaf9a1d8ed536d25" "$(installed_apk_id)"
start_dump "$OUT/U-old-start.xml"
assert_eq "U: the old build shows Start and no wizard (the leg starts where a user's phone would)" "yes" "$(wizard_absent "$OUT/U-old-start.xml")"
# The hand pick, on the old build: its picker's tag is the PACKAGE (the tag became the component in build task 1).
open_picker PEOPLE "$OUT/U-old-picker-people.xml"
note "the old build's PEOPLE picker: $(candidates "$OUT/U-old-picker-people.xml")"
CONTACTS_PKG="${CONTACTS_SHORT%%/*}"
tap_node "$OUT/U-old-picker-people.xml" "slot_candidate:$CONTACTS_PKG"; sleep 2
OLD_PEOPLE="$(slot_of PEOPLE)"
assert_eq "U: the hand pick is explicit before the update: slots.PEOPLE is the image's Contacts" "$(flat "$CONTACTS_SHORT")" "$OLD_PEOPLE"
assert_eq "U: … and there is no slots.CALENDAR" "" "$(slot_of CALENDAR)"
adb shell input keyevent KEYCODE_HOME; sleep 2
U_MARK="$(ring_mark)"
adb install -r "$NEW_APK" > "$OUT/U-install.out" 2>&1; echo $? > "$OUT/U-install.rc"
note "adb install -r: $(tail -1 "$OUT/U-install.out")"
assert_eq "U: adb install -r of this build succeeds (the same debug key)" "0" "$(cat "$OUT/U-install.rc")"
assert_absent "U: no INSTALL_FAILED_UPDATE_INCOMPATIBLE" "INSTALL_FAILED" "$(cat "$OUT/U-install.out")"
assert_contains "U: the device now holds this build" "yes" "$(apk_matches)"
sleep 6
start_dump "$OUT/U-new-start.xml"
U_SLICE="$(ring_since "$U_MARK")"; printf '%s\n' "$U_SLICE" > "$OUT/U-slice.txt"; ring_save
log "the seed's lines after the update:"; grep -F 'assignSlotOnce' "$OUT/U-slice.txt" | tee -a "$LOG"
assert_contains "U: PEOPLE taken over once, the line naming what it replaced (Q-16-1)" \
  "assignSlotOnce slot:people:v1 PEOPLE -> $PEOPLE_ACTIVITY -> assigned, replaced user's $(short "$OLD_PEOPLE")" "$U_SLICE"
CAL_LINE="$(grep -F 'assignSlotOnce slot:calendar:v1' "$OUT/U-slice.txt" | head -1)"
assert_contains "U: CALENDAR, never touched by hand, is assigned" "assignSlotOnce slot:calendar:v1 CALENDAR -> $CALENDAR_ACTIVITY -> assigned" "$CAL_LINE"
assert_absent "U: … with no 'replaced'" "replaced" "$CAL_LINE"
assert_eq "U: slots.PEOPLE is the shell's People" "$SHELL_PEOPLE" "$(slot_of PEOPLE)"
assert_eq "U: slots.CALENDAR is the shell's Calendar" "$SHELL_CAL" "$(slot_of CALENDAR)"
L="$(added_once)"
assert_contains "U: both markers recorded (calendar)" "slot:calendar:v1" "$L"
assert_contains "U: both markers recorded (people)" "slot:people:v1" "$L"
assert_eq "U: the PEOPLE tile reads People" "People" "$(tile_desc "$OUT/U-new-start.xml" slot:PEOPLE)"
gtap_tile "$OUT/U-new-start.xml" slot:PEOPLE
assert_eq "U: … and its tap resumes the shell's People" "$PEOPLE_ACTIVITY" "$(top_activity)"
c6; ensure_start

log "--- U: pointing it back sticks"
open_picker PEOPLE "$OUT/U-picker-people.xml"
tap_node "$OUT/U-picker-people.xml" "slot_candidate:$CONTACTS_SHORT"; sleep 2
assert_eq "U: pointed back: slots.PEOPLE is the image's Contacts" "$OLD_PEOPLE" "$(slot_of PEOPLE)"
ring_save
B_MARK="$(ring_mark)"
adb shell am force-stop app.tileshell; sleep 1
start_dump "$OUT/U-back-start.xml"
B_SLICE="$(ring_since "$B_MARK")"; printf '%s\n' "$B_SLICE" > "$OUT/U-back-slice.txt"
assert_eq "U: after a restart slots.PEOPLE is still the image's Contacts" "$OLD_PEOPLE" "$(slot_of PEOPLE)"
# "The PEOPLE tile reads the image's Contacts": a slot tile's LABEL is the slot's own by the owner's ruling of 2026-09-22
# (start/StartPage.kt:171-175 — "People", whatever app holds the slot), so what the tile reads is asserted as the app it
# stands for: its tap resumes the image's Contacts (an agent reading of the clause, INDEX Change Log 2026-10-01).
record "U: the PEOPLE tile's label (the slot's own, whichever app holds it)" "$(tile_desc "$OUT/U-back-start.xml" slot:PEOPLE)"
assert_contains "U: the marker has run: slot:people:v1 … -> already run" "assignSlotOnce slot:people:v1 PEOPLE -> $PEOPLE_ACTIVITY -> already run" "$B_SLICE"
absent_in "U: no assignSlotOnce … -> assigned line after the restart" "-> assigned" "$(printf '%s\n' "$B_SLICE" | grep -F 'assignSlotOnce')"
gtap_tile "$OUT/U-back-start.xml" slot:PEOPLE
assert_eq "U: the PEOPLE tile now stands for the image's Contacts: its tap resumes it" "$CONTACTS_SHORT" "$(top_activity)"
c6; ensure_start

# ----------------------------------------------------------------------------------------------- G: the guard leg
log "--- G: the guard on the one other marker (slot:music:v1 over a hand pick)"
AUXIO_FLAT="org.oxycblt.auxio/org.oxycblt.auxio.MainActivity"
python3 -c '
import json, sys
d = json.load(open(sys.argv[1]))
d["addedOnce"] = [m for m in d["addedOnce"] if m != "slot:music:v1"]
d["slots"] = dict(d["slots"], MUSIC=sys.argv[3])
json.dump(d, open(sys.argv[2], "w"))' "$BASELINE" "$OUT/G-layout.json" "$AUXIO_FLAT"
# Written the way layout_restore writes a file — not layout_restore itself, whose read-back would reject the marker the
# shell adds back.
ring_save
adb shell am force-stop app.tileshell
adb push "$OUT/G-layout.json" /data/local/tmp/restore_layout.json >/dev/null
adb shell 'run-as app.tileshell sh -c "cat /data/local/tmp/restore_layout.json > files/start_layout.json"'
adb shell am force-stop app.tileshell
G_MARK="$(ring_mark)"
ensure_start
G_SLICE="$(ring_since "$G_MARK")"; printf '%s\n' "$G_SLICE" > "$OUT/G-slice.txt"
assert_eq "G: slots.MUSIC is still Auxio's" "$AUXIO_FLAT" "$(slot_of MUSIC)"
assert_contains "G: addedOnce holds slot:music:v1 again" "slot:music:v1" "$(added_once)"
assert_contains "G: the guard's line" "assignSlotOnce slot:music:v1 MUSIC -> kept user's org.oxycblt.auxio/.MainActivity" "$G_SLICE"

# ----------------------------------------------------------------------------------------------- A: the Agenda pod
log "--- A: phase 14's Agenda pod opens the shell's Calendar (r3 D8)"
ring_save
layout_restore "$BASELINE"; assert_eq "A: layout_restore of the baseline" "0" "$?"
FILE_CAL="$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["slots"]["CALENDAR"])' "$BASELINE")"
ensure_start; swipe_right
dump_ui "$OUT/A-bay.xml"
assert_eq "A: the pod bay is showing" "yes" "$(has_node "$OUT/A-bay.xml" pod_bay)"
scroll_to_node "$OUT/A-bay.xml" pod:agenda 6 >/dev/null 2>&1 || true
dump_ui "$OUT/A-bay.xml"
# No event in the pod's window on a wiped, re-provisioned device: phase 14's empty line, unchanged.
assert_eq "A: with no event in its window the pod reads phase 14's empty line" "Nothing on your calendar today" "$(node_text "$OUT/A-bay.xml" pod_empty:agenda)"
A_MARK="$(ring_mark)"
tap_node "$OUT/A-bay.xml" pod_header:agenda; sleep 4
assert_eq "A: the header's tap resumes the component the baseline FILE's slots.CALENDAR names" "$(short "$FILE_CAL")" "$(top_activity)"
assert_contains "A: the pod's line" "[podbay] launch agenda -> $CALENDAR_ACTIVITY" "$(ring_since "$A_MARK")"
c6; ensure_start

# ----------------------------------------------------------------------------------------------- M: the Music negative
log "--- M: the shell's Music playing puts nothing on the Calendar and People tiles (phase 15 task 0's routing)"
MUSIC_FIXDIR="$QAR/phase-01/MUSIC6-fixtures"
. "$P01S/music_lib.sh"
VOL0="$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
HAD_FIX="$(adb shell ls /sdcard/Music/tessera-qa 2>/dev/null | grep -c mp3)"
note "media volume before: ${VOL0:-?}; Music fixtures present before: $HAD_FIX"
music_mute; music_fixtures
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
start_dump "$OUT/M-before.xml"
for id in slot:MUSIC slot:CALENDAR slot:PEOPLE; do assert_ne "M: tile $id is on Start" "" "$(tile_field "$OUT/M-before.xml" "$id" bounds)"; done
note "before: CALENDAR [$(tile_field "$OUT/M-before.xml" slot:CALENDAR texts)] PEOPLE [$(tile_field "$OUT/M-before.xml" slot:PEOPLE texts)]"
bloom="$(q "content query --uri content://media/external/audio/media --projection _id --where \"title='Bloom'\"" | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)"
M_MARK="$(ring_mark)"
adb shell am start -W -n app.tileshell/.music.MusicActivity >/dev/null 2>&1; sleep 4
dump_ui "$OUT/M-music.xml"; tap_node "$OUT/M-music.xml" "music_pivot_header:songs"; sleep 3
dump_ui "$OUT/M-songs.xml"; tap_node "$OUT/M-songs.xml" "music_song:$bloom"; sleep 3
playing="$(adb shell dumpsys media_session | tr -d '\r' | python3 -c '
import re, sys
hit = False
for l in sys.stdin:
    if re.match(r"^\s+\S+ \S+/\S+/\d+ \(userId=\d+\)", l): hit = " app.tileshell/" in l and "androidx.media3.session.id.music" in l
    elif hit:
        m = re.search(r"state=PlaybackState \{state=([A-Z_]+)", l)
        if m: print(m.group(1)); break')"
assert_eq "M: the shell's Music session is playing" "PLAYING" "$playing"
adb shell input keyevent KEYCODE_HOME; sleep 2
gdump "$OUT/M-playing.xml"; screencap "$OUT/M-playing.png"
printf '%s\n' "$(ring_since "$M_MARK")" > "$OUT/M-slice.txt"
assert_eq "M: only the MUSIC tile carries the now-playing transport" "controls=yes" "$(tile_field "$OUT/M-playing.xml" slot:MUSIC controls)"
assert_contains "M: the MUSIC tile shows the playing track" "Bloom" "$(tile_field "$OUT/M-playing.xml" slot:MUSIC texts)"
for id in slot:CALENDAR slot:PEOPLE; do
  assert_eq "M: $id carries no transport" "controls=no" "$(tile_field "$OUT/M-playing.xml" "$id" controls)"
  assert_absent "M: $id shows no now-playing text" "Bloom" "$(tile_field "$OUT/M-playing.xml" "$id" texts)"
  assert_eq "M: $id has not grown (ActiveTiles: its size unchanged)" "$(tile_field "$OUT/M-before.xml" "$id" size)" "$(tile_field "$OUT/M-playing.xml" "$id" size)"
done
# The tiles' own faces are still theirs: the Calendar tile's CalendarFeed day face (the day number), the People tile's
# People face (its tag) — read from the dump taken while Music plays.
DAYNUM="$(adb shell date +%-d | tr -d '\r')"
assert_contains "M: the CALENDAR tile still shows its day face (today's number, $DAYNUM)" "$DAYNUM" "$(tile_field "$OUT/M-playing.xml" slot:CALENDAR texts)"
assert_eq "M: the PEOPLE tile still shows its People face" "yes" "$(grep -q 'resource-id="people_tile_face' "$OUT/M-playing.xml" && echo yes || echo no)"
note "playing: CALENDAR [$(tile_field "$OUT/M-playing.xml" slot:CALENDAR texts)] PEOPLE [$(tile_field "$OUT/M-playing.xml" slot:PEOPLE texts)]"
adb shell cmd media_session dispatch pause >/dev/null 2>&1; sleep 2
c6
[ "${HAD_FIX:-0}" = "0" ] && adb shell rm -rf /sdcard/Music/tessera-qa >/dev/null 2>&1
[ -n "${VOL0:-}" ] && adb shell cmd media_session volume --stream 3 --set "$VOL0" >/dev/null 2>&1

# ----------------------------------------------------------------------------------------------- restore
log "--- restore: pm clear → provision.sh → ensure_start → the baseline (undoes the three legs' re-pointed slots)"
ring_save
adb shell pm clear app.tileshell >/dev/null
assert_eq "restore: provision.sh rc" "0" "$(provision restore)"
ensure_start
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline" "0" "$?"
assert_eq "restore: slots.PEOPLE is the shell's People" "$SHELL_PEOPLE" "$(slot_of PEOPLE)"
assert_eq "restore: slots.CALENDAR is the shell's Calendar" "$SHELL_CAL" "$(slot_of CALENDAR)"
assert_eq "restore: WRITE_CONTACTS held" "true" "$(perm_granted WRITE_CONTACTS)"
ensure_start
row_end
