#!/usr/bin/env bash
# Phase 16 E12 — Card actions, clause by clause from the phase doc's row E12. RUN LAST of the People rows, and the map
# action goes LAST inside it (the builder's first map action into OsmAnd saw the emulator process exit; the cause is not
# established). If the emulator dies at that step the driver stops all device work and says so; it never relaunches it.
#
#   fixtures   people_fixtures_up first; an added postal row on Ann; each action from its own MARK, c6 + ensure_start
#              after each launch
#   call       on Ann Lee's card, Call → `dumpsys telecom` shows an outgoing call placed by the shell; `adb emu gsm list`
#              prints only OK on this image (Change Log 2026-10-01): its output is RECORDed, the call is asserted from
#              Telecom's own record. Her own number (+1 555 000 0001) is ended by the emulated network a quarter of a
#              second after Telecom creates the call, so a second number the network keeps up (5551230001, added to
#              her card) is called too, and Telecom is read while that call is up (clauses-open.tsv). `gsm cancel` after.
#   text       → the SMS role holder's compose (the Fossify Messages fixture) resumes with smsto: her number
#   mail       with the K-9 fixture in the Mail slot → `[people] action mail -> <K-9's component> mailto:ann@example.com`
#              and the top package is K-9's; with the slot unassigned → Android's resolver
#   VIEW       `am start -a android.intent.action.VIEW -d content://com.android.contacts/contacts/<Ann's id>` → Android's
#              resolver first (the AOSP Contacts fixture is the other handler): the shell's entry and "Always" tapped at
#              their dump bounds, RECORDed that it was needed; the shell's People card is resumed; a second am start
#              resumes the shell's card with no resolver
#   restore 1  gsm cancel; `pm clear-package-preferred-activities app.tileshell`, then
#              `cmd package set-home-activity app.tileshell/app.tileshell.StartActivity`, ensure_start asserted
#   map        (LAST) Address with OsmAnd in the Maps slot → OsmAnd resumes with a geo: query and the slice holds
#              `[people] action map -> <OsmAnd's component> geo:0,0?q=…`
#   restore 2  the Mail and Maps slots back to the baseline (layout_restore); people_fixtures_down
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"
KEPT_UP=5551230001          # a plain ten-digit number: the emulated network keeps a call to it up (the builder's DEV-CALLPROBE)
emulator_alive() { pgrep -f 'qemu-system-x86_64 -avd tileshell_fhd' >/dev/null && [ "$(adb get-state 2>/dev/null | tr -d '\r')" = device ] && echo yes || echo no; }
short() { python3 -c 'import sys; p, c = sys.argv[1].split("/"); print(p + "/" + (c[len(p):] if c.startswith(p + ".") else c))' "$1"; }
flat() { python3 -c 'import sys; p, c = sys.argv[1].split("/"); print(p + "/" + (p + c if c.startswith(".") else c))' "$1"; }
launcher_of() { adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$1" | tr -d '\r' | tail -1 | tr -d ' '; }
slots_layout() { # out.json MAIL-component-or-"" MAPS-component-or-""
  python3 - "$BASELINE" "$@" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
s = dict(d.get("slots", {}))
for k, v in (("MAIL", sys.argv[3]), ("MAPS", sys.argv[4])):
    if v: s[k] = v
    else: s.pop(k, None)
d["slots"] = s
json.dump(d, open(sys.argv[2], "w"))
PY
}
slot_of() { layout_json | python3 -c 'import json, sys; print(json.load(sys.stdin).get("slots", {}).get(sys.argv[1], ""))' "$1"; }
ann_card() { card_of "$ANN" "$1"; }
newest_call() { # the newest CallTC block of dumpsys telecom (its header and event lines)
  adb shell dumpsys telecom | tr -d '\r' | python3 -c '
import re, sys
text = sys.stdin.read()
blocks = re.split(r"\n(?=\s+CallTC@\d+ \[)", text)
calls = [b for b in blocks if re.match(r"\s+CallTC@\d+ \[", b)]
if calls:
    b = calls[-1]
    end = re.search(r"\n\s+Timings ", b)
    print(b[:end.start()] if end else b[:1800])'
}
# The text nodes of a dump: "x y|text" of the first node whose text equals the word given.
text_at() { # dump.xml exact-text
  python3 - "$1" "$2" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for m in re.finditer(r'<node[^>]*>', xml):
    s = m.group(0)
    t = re.search(r' text="([^"]*)"', s) or re.search(r" text='([^']*)'", s)     # a text holding a double quote is dumped single-quoted
    if t and html.unescape(t.group(1)) == sys.argv[2]:
        l, t, r, b = (int(v) for v in re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s).groups())
        print((l + r) // 2, (t + b) // 2); break
PY
}
is_resolver() { case "$1" in *intentresolver*|*ResolverActivity*|*ChooserActivity*) echo yes ;; *) echo no ;; esac; }

row_begin E12 "card actions: call, text, mail (slot assigned / unassigned), the VIEW intent and Always, map (last)"
require_build
D="$ROW_DIR"
layout_restore "$BASELINE"; assert_eq "layout_restore of the baseline" "0" "$?"
ensure_start
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"
HOLDER="$(sms_holder)"; K9="$(launcher_of com.fsck.k9)"; OSMAND="$(launcher_of net.osmand.plus)"
note "SMS role holder: $HOLDER; K-9's launcher: $K9; OsmAnd's launcher: $OSMAND"
assert_ne "the K-9 fixture is installed" "" "$K9"
assert_ne "OsmAnd is installed" "" "$OSMAND"
assert_eq "the Fossify Messages fixture holds the SMS role" "org.fossify.messages" "$HOLDER"

log "--- fixtures"
people_fixtures_up
people_data "$ANN" vnd.android.cursor.item/postal-address_v2 "1 Main St Springfield" 1
people_data "$ANN" vnd.android.cursor.item/phone_v2 "$KEPT_UP" 3
sleep 2
ANN_C="$(contact_of "$ANN")"; L_ANN="$(lookup_of "$ANN_C")"
data_rows "$ANN" >> "$LOG"
slots_layout "$D/layout-mail-maps.json" "$(flat "$K9")" "$(flat "$OSMAND")"
layout_restore "$D/layout-mail-maps.json"; assert_eq "the Mail slot → K-9 and the Maps slot → OsmAnd were written" "0" "$?"
assert_eq "slots.MAIL is K-9" "$(flat "$K9")" "$(slot_of MAIL)"
assert_eq "slots.MAPS is OsmAnd" "$(flat "$OSMAND")" "$(slot_of MAPS)"
ensure_start
ann_card "$D/card.xml"; screencap "$D/card.png"
assert_eq "Ann Lee's card (people_card:<lookup>)" "yes" "$(has_node "$D/card.xml" "people_card:$L_ANN")"
note "action rows: $(ids_with_prefix "$D/card.xml" people_card_action: | tr '\n' ' ')"

# ------------------------------------------------------------------------------------------------ Call
log "--- Call (her own number)"
CALLS0="$(outgoing_calls)"
MARK="$(ring_mark)"
tap_node "$D/card.xml" people_card_action:call:0; sleep 4
SLICE="$(ring_since "$MARK")"; log "$(printf '%s\n' "$SLICE" | grep -F '[people] action' | sed 's/.*\[people\]/[people]/')"
adb shell dumpsys telecom | tr -d '\r' > "$D/telecom-call-own.txt"
NEWEST="$(newest_call)"; printf '%s\n' "$NEWEST" > "$D/telecom-call-own-newest.txt"
assert_eq "dumpsys telecom: one more outgoing call than before the tap" "$((CALLS0 + 1))" "$(outgoing_calls)"
assert_contains "dumpsys telecom: the newest call is outgoing" "(MO - outgoing)" "$(printf '%s\n' "$NEWEST" | head -1)"
assert_contains "dumpsys telecom: … and was placed by the shell (CREATED (app.tileshell; …))" "CREATED (app.tileshell;" "$NEWEST"
record "Telecom's record of it" "$(printf '%s\n' "$NEWEST" | head -1 | sed 's/^ *//')"
record "how the emulated network ended it" "$(printf '%s\n' "$NEWEST" | grep -m1 'SET_DISCONNECTED' | sed 's/^ *//' | cut -c1-170)"
record "adb emu gsm list after the tap (the doc's read; this image prints only OK)" "$(adb emu gsm list 2>&1 | tr -d '\r' | tr '\n' ' ')"
assert_contains "the slice holds [people] action call -> <component> tel:<her number>" "tel:+1 555 000 0001" "$(printf '%s\n' "$SLICE" | grep -F '[people] action call -> ')"
end_call
c6; ensure_start

log "--- Call (the number the emulated network keeps up; clauses-open.tsv)"
ann_card "$D/card-b.xml"
CALLS0="$(outgoing_calls)"
MARK="$(ring_mark)"
tap_node "$D/card-b.xml" people_card_action:call:1; sleep 5
adb shell dumpsys telecom | tr -d '\r' > "$D/telecom-call-up.txt"
NEWEST="$(newest_call)"; printf '%s\n' "$NEWEST" > "$D/telecom-call-up-newest.txt"
log "$(ring_since "$MARK" | grep -F '[people] action' | sed 's/.*\[people\]/[people]/')"
assert_contains "the action line names the kept-up number" "tel:$KEPT_UP" "$(ring_since "$MARK" | grep -F '[people] action call -> ')"
assert_eq "dumpsys telecom: one more outgoing call" "$((CALLS0 + 1))" "$(outgoing_calls)"
assert_contains "dumpsys telecom: placed by the shell" "CREATED (app.tileshell;" "$NEWEST"
assert_eq "dumpsys telecom: the call is up (a foreground call exists while it is read)" "yes" "$(call_is_up && echo yes || echo no)"
assert_contains "… the foreground call is this one, dialing or active" "state=" "$(grep -A1 'Foreground call:' "$D/telecom-call-up.txt" | tail -1 | grep -oE 'Call id=TC@[0-9]+, state=(DIALING|ACTIVE|CONNECTING)')"
assert_absent "… its record holds no SET_DISCONNECTED yet" "SET_DISCONNECTED" "$NEWEST"
record "the foreground call as Telecom dumps it" "$(grep -A1 'Foreground call:' "$D/telecom-call-up.txt" | tail -1 | sed 's/^ *//' | cut -c1-120)"
record "the resumed activity while the call is up" "$(top_activity)"
record "adb emu gsm list while the call is up (the doc's read; this image prints only OK)" "$(adb emu gsm list 2>&1 | tr -d '\r' | tr '\n' ' ')"
record "adb emu gsm cancel $KEPT_UP" "$(adb emu gsm cancel "$KEPT_UP" 2>&1 | tr -d '\r' | tr '\n' ' ')"
sleep 2
end_call
sleep 2
assert_eq "no call is left up (Telecom's mCalls is empty)" "0" "$(adb shell dumpsys telecom | tr -d '\r' | grep -c '\[Call id=TC@')"
assert_eq "the device is awake after the call" "Awake" "$(wake_device)"
adb shell am force-stop com.android.dialer
c6; ensure_start

# ------------------------------------------------------------------------------------------------ Text
log "--- Text"
ann_card "$D/card-c.xml"
adb shell am force-stop "$HOLDER"; sleep 1
T_DEV="$(dev_time)"
MARK="$(ring_mark)"
tap_node "$D/card-c.xml" people_card_action:text:0; sleep 5
TOP="$(top_activity)"; log "resumed: $TOP"
adb shell dumpsys activity activities | tr -d '\r' > "$D/activities-text.txt"
assert_contains "the SMS role holder's compose is resumed (dumpsys activity activities)" "$HOLDER/" "$TOP"
# Fossify forwards the SENDTO to its thread page and finishes the activity that received it, so the dump no longer
# holds the smsto: intent seconds later; the start is read from the activity manager's own log line (clauses-open.tsv).
starts_since "$T_DEV" > "$D/starts-text.txt"
INTENT="$(grep -m1 -F 'smsto:' "$D/starts-text.txt" | sed 's/^.*START u0/START u0/' | cut -c1-300)"; log "the activity manager's start line: $INTENT"
assert_contains "… started with ACTION_SENDTO" "act=android.intent.action.SENDTO" "$INTENT"
assert_contains "… to the SMS role holder, by the shell" "cmp=$HOLDER/" "$INTENT"
assert_contains "… from the shell's uid" "from uid $(shell_uid) " "$INTENT"
SSP="$(printf '%s' "$INTENT" | sed -n 's/.*dat=smsto:\([^ ]*\).*/\1/p')"
record "the intent's data as the activity manager prints it (Android redacts an smsto: URI)" "smsto:$SSP"
assert_eq "… with smsto: data as long as her number (\"+15550000001\", 12 characters)" "12" "${#SSP}"
TEXT_LINE="$(ring_since "$MARK" | grep -F '[people] action text -> ' | tail -1 | sed 's/.*\[people\]/[people]/')"; log "$TEXT_LINE"
assert_contains "the shell's own line names smsto: her number and the role holder (V21)" "[people] action text -> $HOLDER" "$TEXT_LINE"
assert_contains "… smsto:+15550000001" "smsto:+15550000001" "$TEXT_LINE"
dump_ui "$D/compose.xml"; screencap "$D/compose.png"
assert_eq "the compose window names her (her name or her number)" "yes" "$(grep -qE 'Ann Lee|555[ -]?000[ -]?0001|5550000001' "$D/compose.xml" && echo yes || echo no)"
adb shell am force-stop "$HOLDER"
c6; ensure_start

# ------------------------------------------------------------------------------------------------ Mail, the slot on K-9
log "--- Mail with K-9 in the Mail slot"
ann_card "$D/card-d.xml"
adb shell am force-stop com.fsck.k9; sleep 1
MARK="$(ring_mark)"
tap_node "$D/card-d.xml" people_card_action:mail:0; sleep 5
SLICE="$(ring_since "$MARK")"; log "$(printf '%s\n' "$SLICE" | grep -F '[people] action' | sed 's/.*\[people\]/[people]/')"
assert_contains "the slice holds [people] action mail -> <K-9's component> mailto:ann@example.com" "[people] action mail -> $(short "$(flat "$K9")") mailto:ann@example.com" "$SLICE"
TOP="$(top_activity)"; log "resumed: $TOP"
assert_contains "the top package is K-9's" "com.fsck.k9/" "$TOP"
adb shell am force-stop com.fsck.k9
c6; ensure_start

# ------------------------------------------------------------------------------------------------ Mail, the slot unassigned
log "--- Mail with the Mail slot unassigned → Android's resolver"
ring_save
slots_layout "$D/layout-maps-only.json" "" "$(flat "$OSMAND")"
layout_restore "$D/layout-maps-only.json"; assert_eq "the Mail slot is unassigned again (the Maps slot kept on OsmAnd)" "0" "$?"
assert_eq "slots.MAIL is empty" "" "$(slot_of MAIL)"
ensure_start
ann_card "$D/card-e.xml"
MARK="$(ring_mark)"
tap_node "$D/card-e.xml" people_card_action:mail:0; sleep 4
TOP="$(top_activity)"; log "resumed: $TOP"
adb shell dumpsys activity activities | tr -d '\r' | grep -m1 -E 'Intent \{ act=android.intent.action.(CHOOSER|SENDTO)' | sed 's/^ *//' | cut -c1-240 >> "$LOG"
dump_ui "$D/mail-resolver.xml"; screencap "$D/mail-resolver.png"
# "→ Android's resolver": the shell names no component and leaves the choice to Android. Android shows its resolver
# only when two or more activities can take the intent; with ONE handler of SENDTO mailto: on the image it starts that
# one with no page of its own (clauses-open.tsv).
MAILTO_HANDLERS="$(adb shell cmd package query-activities --brief -a android.intent.action.SENDTO -d mailto:ann@example.com | tr -d '\r' | grep '/' | tr -d ' ' | tr '\n' ' ' | sed 's/ $//')"
record "the image's handlers of ACTION_SENDTO mailto:" "$MAILTO_HANDLERS"
MAIL_LINE="$(ring_since "$MARK" | grep -F '[people] action mail' | tail -1 | sed 's/.*\[people\]/[people]/')"
assert_eq "with the slot unassigned the shell names no component: it hands mailto: to Android's resolver" "[people] action mail -> resolver mailto:ann@example.com" "$MAIL_LINE"
if [ "$(echo "$MAILTO_HANDLERS" | wc -w)" -ge 2 ]; then
  assert_eq "with the slot unassigned: Android's resolver is resumed (two or more handlers)" "yes" "$(is_resolver "$TOP")"
else
  assert_eq "with the slot unassigned and ONE mailto: handler on the image, Android's resolution starts that handler" "$MAILTO_HANDLERS" "$TOP"
  assert_contains "… with the SENDTO the shell sent (dumpsys activity activities)" "act=android.intent.action.SENDTO dat=mailto:ann@example.com" "$(adb shell dumpsys activity activities | tr -d '\r' | grep -m1 -E 'Intent \{ act=android.intent.action.SENDTO')"
fi
log "$(ring_since "$MARK" | grep -F '[people] action' | sed 's/.*\[people\]/[people]/')"
back 2
adb shell am force-stop com.fsck.k9; adb shell am force-stop eu.faircode.email
c6; ensure_start

# ------------------------------------------------------------------------------------------------ the VIEW intent
log "--- am start -a VIEW -d content://com.android.contacts/contacts/<Ann's id>: the resolver, Always, then no resolver"
VIEW_HANDLERS="$(adb shell cmd package query-activities --brief -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$ANN_C" | tr -d '\r' | grep '/' | tr -d ' ' | tr '\n' ' ')"
record "the handlers of that VIEW on this image" "$VIEW_HANDLERS"
adb shell am start -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$ANN_C" > "$D/view-1.txt" 2>&1
sleep 4
TOP="$(top_activity)"; log "after the first am start: $TOP"
dump_ui "$D/resolver.xml"; screencap "$D/resolver.png"
if [ "$(is_resolver "$TOP")" = yes ]; then
  record "Android's resolver appeared first (the AOSP Contacts fixture is the other handler); the shell's entry and Always were tapped" "yes — $TOP"
  ENTRY="$(text_at "$D/resolver.xml" People)"
  assert_ne "the resolver lists the shell's entry (\"People\")" "" "$ENTRY"
  # shellcheck disable=SC2086
  [ -n "$ENTRY" ] && { adb shell input tap $ENTRY; sleep 2; }
  dump_ui "$D/resolver-2.xml"; screencap "$D/resolver-2.png"
  ALWAYS="$(text_at "$D/resolver-2.xml" Always)"; [ -z "$ALWAYS" ] && ALWAYS="$(text_at "$D/resolver-2.xml" ALWAYS)"
  assert_ne "the resolver offers Always" "" "$ALWAYS"
  # shellcheck disable=SC2086
  [ -n "$ALWAYS" ] && { adb shell input tap $ALWAYS; sleep 4; }
else
  record "Android's resolver appeared first" "NO — the first am start resumed $TOP"
fi
dump_ui "$D/view-card.xml"; screencap "$D/view-card.png"
assert_eq "the shell's People card is resumed (dumpsys activity activities)" "$PEOPLE_ACTIVITY" "$(top_activity)"
assert_eq "… on Ann's card (people_card:<lookup>)" "yes" "$(has_node "$D/view-card.xml" "people_card:$L_ANN")"
c6; ensure_start
adb shell am start -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$ANN_C" > "$D/view-2.txt" 2>&1
sleep 4
TOP2="$(top_activity)"; log "after the second am start: $TOP2"
dump_ui "$D/view-card-2.xml"
assert_eq "a second am start resumes the shell's card with no resolver" "$PEOPLE_ACTIVITY" "$TOP2"
assert_eq "… Ann's card" "yes" "$(has_node "$D/view-card-2.xml" "people_card:$L_ANN")"
c6; ensure_start

# ------------------------------------------------------------------------------------------------ restore, part 1
log "--- restore, part 1 (before the map action): gsm cancel, the Always cleared, the home activity set back"
record "adb emu gsm cancel (restore)" "$(adb emu gsm cancel "$KEPT_UP" 2>&1 | tr -d '\r' | tr '\n' ' ')"
end_call
adb shell pm clear-package-preferred-activities app.tileshell > "$D/clear-preferred.txt" 2>&1
adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity > "$D/set-home.txt" 2>&1
note "pm clear-package-preferred-activities: $(tr -d '\r' < "$D/clear-preferred.txt" | tr '\n' ' '); set-home-activity: $(tr -d '\r' < "$D/set-home.txt" | tr '\n' ' ')"
if ensure_start; then _verdict PASS "restore: ensure_start after the home activity was set back" "Start alone"; fi
assert_contains "restore: Home resolves to the shell's Start" "app.tileshell/.StartActivity" "$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
adb shell am start -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$ANN_C" > "$D/view-3.txt" 2>&1
sleep 4
assert_eq "restore: the Always is cleared (the same VIEW raises Android's resolver again)" "yes" "$(is_resolver "$(top_activity)")"
back 2
ensure_start

# ------------------------------------------------------------------------------------------------ the map action (LAST)
log "--- Address with OsmAnd in the Maps slot (the last action of the row)"
assert_eq "slots.MAPS is OsmAnd" "$(flat "$OSMAND")" "$(slot_of MAPS)"
ann_card "$D/card-f.xml"
assert_eq "the card has the address action (people_card_action:map:0)" "yes" "$(has_node "$D/card-f.xml" people_card_action:map:0)"
ring_save
record "the host's free disk before the map action" "$(df -h / | awk 'NR==2 {print $4 " free, " $5 " used"}')"
assert_eq "the emulator is alive before the map action" "yes" "$(emulator_alive)"
T_DEV="$(dev_time)"
MARK="$(ring_mark)"
tap_node "$D/card-f.xml" people_card_action:map:0
sleep 6
ALIVE="$(emulator_alive)"
assert_eq "the emulator is alive after the map action" "yes" "$ALIVE"
if [ "$ALIVE" != yes ]; then
  log "THE EMULATOR PROCESS IS GONE after the map action into OsmAnd: all device work stops here (the lead is told); nothing below ran."
  log "NOT RESTORED: the layout (slots.MAPS on OsmAnd), the row's fixture raw contacts ($(tr '\n' ' ' < "$ROW_DIR/people-fixtures.ids" 2>/dev/null))."
  {
    echo "------------------------------------------------------------------------------"
    echo "$ROW: $PASS passed, $FAIL failed, ${RECORDED:-0} recorded (ABORTED: the emulator process exited at the map action)"
  } >> "$LOG"
  exit 8
fi
TOP="$(top_activity)"; log "resumed: $TOP"
adb shell dumpsys activity activities | tr -d '\r' > "$D/activities-map.txt"
SLICE="$(ring_since "$MARK")"; MAP_LINE="$(printf '%s\n' "$SLICE" | grep -F '[people] action map -> ' | tail -1 | sed 's/.*\[people\]/[people]/')"; log "$MAP_LINE"
assert_contains "OsmAnd is resumed" "net.osmand.plus/" "$TOP"
# OsmAnd takes VIEW geo: in a trampoline (GeoIntentActivity) that hands on to its map page and finishes, so a dump
# taken seconds later holds only the map page's own intent; and Android prints a geo: URI redacted ("dat=geo:"). The
# start is read from the activity manager's own log line; the query's text is in the shell's line (clauses-open.tsv).
record "dumpsys activity activities, 6 s on: the resumed activity's own intent" "$(grep -m1 'Intent {' "$D/activities-map.txt" | sed 's/^ *//' | cut -c1-160)"
starts_since "$T_DEV" > "$D/starts-map.txt"
GEO="$(grep -m1 -F 'dat=geo:' "$D/starts-map.txt" | sed 's/^.*START u0/START u0/' | cut -c1-300)"; log "the activity manager's start line: $GEO"
assert_contains "… started with ACTION_VIEW on a geo: URI" "act=android.intent.action.VIEW dat=geo:" "$GEO"
assert_contains "… into OsmAnd" "cmp=net.osmand.plus/" "$GEO"
assert_contains "… by the shell (from uid <the shell's uid>)" "from uid $(shell_uid) " "$GEO"
assert_contains "the slice holds [people] action map -> <OsmAnd's component> geo:0,0?q=…" "[people] action map -> $(short "$(flat "$OSMAND")") geo:0,0?q=" "$MAP_LINE"
assert_contains "… the query is her address" "Main" "$MAP_LINE"
screencap "$D/osmand.png"
sleep 6
assert_eq "the emulator is still alive 12 s after the map action" "yes" "$(emulator_alive)"
adb shell am force-stop net.osmand.plus
c6; ensure_start

# ------------------------------------------------------------------------------------------------ restore, part 2
log "--- restore, part 2: the slots, the fixtures"
ring_save
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline (the Mail and Maps slots)" "0" "$?"
assert_eq "restore: slots.MAIL is the baseline's (none)" "" "$(slot_of MAIL)"
assert_eq "restore: slots.MAPS is the baseline's (none: one handler, auto-assigned)" "" "$(slot_of MAPS)"
people_fixtures_down
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: the raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
for p in com.fsck.k9 eu.faircode.email "$HOLDER" com.android.dialer net.osmand.plus com.android.contacts; do adb shell am force-stop "$p"; done
assert_eq "restore: no call is up (Telecom's mCalls is empty)" "0" "$(adb shell dumpsys telecom | tr -d '\r' | grep -c '\[Call id=TC@')"
assert_eq "restore: the device is awake" "Awake" "$(wake_device)"
ensure_start
row_end
