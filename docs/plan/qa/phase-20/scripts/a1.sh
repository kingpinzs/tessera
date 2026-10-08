#!/usr/bin/env bash
# Phase 20 A1 — Browse radio, favourite a station, come back.
#
#   1  fresh data and prefs: `pm clear app.tileshell` → qa/phase-03/scripts/provision.sh → Home, the fixture prefs written.
#      The whole step runs with AIRPLANE MODE ON (a cleared shell has no qa_* prefs, so its radio would ask the LIVE
#      directory); the prefs go in by the safe order (p20.sh qa_prefs_safe) and only then does the network come back.
#   2  open Music; go to radio      3  search "jazz"      4  hold QA Jazz One → add to favourites
#   5  force-stop; reopen radio (the network stays on)
#
# Pass (a)–(e) as the phase doc lists them, with INDEX Change Log 2026-10-07's amendments: the fixture is at :8092 and
# serves ONE page of four, so "the pages asked in order until the short page" is one /json/stations request, offset 0.
# How (a) is read: uiautomator lists only the headers that are on screen (the strip is clipped and scrolls no further
# than needed — phase 10's rule), so the five header nodes are the union of the dump on the first pivot and the dump on
# the radio pivot, ordered by their left edge within each dump; radio's own bounds are read on the radio pivot.
#
# Changes on the device: the shell's data is CLEARED and provisioned again; the Start layout (this phase's baseline);
# QA Jazz One becomes a radio favourite (A2–A5 use it). Fixtures: up at the start, down at the end.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p20.sh"
KEEP_AIRPLANE=0   # set when the row STOPS for safety: then the network stays off
trap 'fixtures_down; [ "$KEEP_AIRPLANE" = 1 ] || adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1' EXIT

p20_begin A1 "browse radio, favourite a station, come back"
fixtures_up

# ----------------------------------------------------------------------------------------------- 1: fresh data, prefs
log "--- 1: pm clear → provision.sh → the fixture prefs (airplane mode ON throughout) → Home"
adb shell cmd connectivity airplane-mode enable; sleep 2
assert_eq "airplane mode is ON before the shell's data is cleared" "1" "$(airplane)"
rings_save
adb shell pm clear app.tileshell > "$ROW_DIR/pm-clear.out" 2>&1
assert_contains "pm clear app.tileshell" "Success" "$(cat "$ROW_DIR/pm-clear.out")"
bash "$QAROOT/phase-03/scripts/provision.sh" > "$ROW_DIR/provision.out" 2>&1; echo $? > "$ROW_DIR/provision.rc"
assert_eq "provision.sh (rc)" "0" "$(cat "$ROW_DIR/provision.rc")"
assert_contains "the device holds the build under test after provisioning (apk_matches)" "yes" "$(apk_matches)"
assert_eq "the cleared shell holds no qa_* pref yet (so the write below is what points it at the fixtures)" "0" "$(prefs_count)"
qa_prefs_safe || { KEEP_AIRPLANE=1; p20_end a b c d e; exit 1; }
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
adb shell cmd media_session volume --stream 3 --set 0 >/dev/null 2>&1
baseline_start
record "the prefs in the file before Music is opened" "$(adb shell run-as app.tileshell cat shared_prefs/start_theme.xml | tr -d '\r' | grep -o 'name="qa_[a-z_]*">[^<]*' | tr '\n' ' ')"
assert_eq "…all three still read back after the baseline restore" "3" "$(prefs_count)"

# ----------------------------------------------------------------------------------------------- 2: Music → radio
log "--- 2: open Music; go to radio"
letter a
MARK="$(ring_mark)"
adb shell am start -W -n $MUSIC_ACTIVITY >/dev/null 2>&1; sleep 4
d open; shot 01-music-open
to_radio; RADIO_OK=$?
for _ in $(seq 1 15); do mring "$MARK" | grep -q 'radio: directory fetched' && break; sleep 1; done
sleep 1.5; d radio; shot 02-radio-pivot
mring "$MARK" > "$ROW_DIR/slice-open.txt"
# THE CONFIRMATION that comes before anything else: the fixture answered, not a live directory.
letter setup
FETCHED="$(grep -o 'radio: directory fetched [0-9]* stations' "$ROW_DIR/slice-open.txt" | tail -1)"
assert_eq "FIRST: the ring says the directory came from the fixture" "radio: directory fetched 4 stations" "$FETCHED"
assert_ne "FIRST: …and the fixture's own log holds the stations request" "" "$(flog "$RLOG.jsonl" 'r["path"] == "/json/stations"')"
if [ "$FETCHED" != "radio: directory fetched 4 stations" ]; then
  adb shell cmd connectivity airplane-mode enable; adb shell am force-stop app.tileshell
  log "STOP: the directory did not come from the fixture — airplane mode ON, shell stopped"; KEEP_AIRPLANE=1
  p20_end a b c d e; exit 1
fi

letter a
assert_eq "Music is on the radio pivot" "0 yes" "$RADIO_OK $(has radio music_list:radio)"
HEADERS="$(python3 - "$ROW_DIR/open.xml" "$ROW_DIR/radio.xml" <<'PY'
import re, sys
import xml.etree.ElementTree as ET
order = []
def heads(path):
    out = []
    for n in ET.parse(path).iter('node'):
        r = n.get('resource-id') or ''
        if r.startswith('music_pivot_header:'):
            b = list(map(int, re.findall(r'-?\d+', n.get('bounds'))))
            out.append((b[0], r.split(':', 1)[1], b))
    return [(name, b) for _, name, b in sorted(out)]
first, radio = heads(sys.argv[1]), heads(sys.argv[2])
for name, _ in first + radio:
    if name not in order: order.append(name)
rb = dict(radio).get('radio')
print("%d|%s|%s|%s|%s" % (len(order), " ".join(order), " ".join(n for n, _ in first), " ".join(n for n, _ in radio),
                          "%d %d %d %d" % tuple(rb) if rb else ""))
PY
)"
IFS='|' read -r H_N H_ORDER H_FIRST H_RADIO H_RB <<< "$HEADERS"
record "(a) headers on the first pivot / on the radio pivot / radio's bounds there" "[$H_FIRST] / [$H_RADIO] / [$H_RB]"
assert_eq "(a) five music_pivot_header:* nodes (the two dumps' union)" "5" "$H_N"
assert_eq "(a) …in this order, radio last" "albums artists songs playlists radio" "$H_ORDER"
assert_eq "(a) …and radio is the last header in the radio pivot's own dump" "radio" "${H_RADIO##* }"
# shellcheck disable=SC2086
set -- $H_RB
assert_yes "(a) radio's bounds are inside 0–1080 ([$H_RB])" "$([ $# -eq 4 ] && [ "$1" -ge 0 ] && [ "$3" -le 1080 ] && [ "$3" -gt "$1" ] && echo yes || echo no)"

letter b
ROWS="$(ids radio radio_row: | grep -v '^music_' | sort | tr '\n' ' ')"
assert_eq "(b) the four fixture stations are listed (radio_row:<id>)" "radio_row:$J1 radio_row:$J2 radio_row:$N1 radio_row:$F1 " "$ROWS"
record "(b) their names" "$(nt radio "music_pri:radio_row:$J1") / $(nt radio "music_pri:radio_row:$J2") / $(nt radio "music_pri:radio_row:$N1") / $(nt radio "music_pri:radio_row:$F1")"
assert_contains "(b) the ring slice: [music] radio: directory fetched 4 stations" "[music] radio: directory fetched 4 stations" "$(cat "$ROW_DIR/slice-open.txt")"
record "(b) the line" "$(grep -m1 'directory fetched' "$ROW_DIR/slice-open.txt" | stripped)"
flog "$RLOG.jsonl" 'r["path"] == "/json/stations"' > "$ROW_DIR/stations-requests.txt"
OFFSETS="$(sed -n 's/.*[?&]offset=\([0-9]*\).*/\1/p' "$ROW_DIR/stations-requests.txt" | tr '\n' ' ')"
record "(b) the fixture's stations requests" "$(cut -d'|' -f1 "$ROW_DIR/stations-requests.txt" | tr '\n' ';')"
assert_eq "(b) the fixture's log: the pages asked in order until the short page (one page of four: offset 0, once)" "0 " "$OFFSETS"

# ----------------------------------------------------------------------------------------------- 3: search "jazz"
log "--- 3: search \"jazz\""
tap radio radio_search; sleep 2; d search0
assert_eq "(b) the search page opened (radio_page:search, its box)" "yes yes" "$(has search0 radio_page:search) $(has search0 radio_search_box)"
tap search0 radio_search_box; sleep 1
adb shell input text "jazz"; sleep 1
# The search runs when the keyboard's Search key is pressed (MusicRadioPages.kt RadioSearch: imeAction = Search). The
# first run typed and never pressed it — a DRIVER fault; the row was re-run with this line (A1-rerun1).
adb shell input keyevent KEYCODE_ENTER; sleep 2.5
d search; shot 03-search-jazz
SROWS="$(ids search radio_ | grep -E '^radio_(row|fav):' | sort | tr '\n' ' ')"
assert_eq "(b) the search shows only the two jazz rows, each once" "radio_row:$J1 radio_row:$J2 " "$SROWS"
record "(b) the search rows' names" "$(nt search "music_pri:radio_row:$J1") / $(nt search "music_pri:radio_row:$J2")"

# ----------------------------------------------------------------------------------------------- 4: hold → favourite
log "--- 4: hold QA Jazz One → add to favourites"
letter c
hold search "radio_row:$J1"; d menu; shot 04-hold-menu
assert_yes "(c) the hold menu opened (music_menu)" "$(has menu music_menu)"
assert_yes "(c) it has add to favourites (music_menu_fav_add)" "$(has menu music_menu_fav_add)"
ENTRIES="$(ids menu music_menu_ | grep -v '^music_menu_scrim$' | tr '\n' ' ')"
assert_eq "(c) …and that is its only entry" "music_menu_fav_add " "$ENTRIES"
MENU_TEXTS="$(texts menu | awk -F' [|] ' '$2 != "" { print $2 }' | tr '\n' '|')"
record "(c) every text on screen with the menu open" "$MENU_TEXTS"
assert_eq "(c) no pin entry (no text on screen mentions pin)" "0" "$(printf '%s' "$MENU_TEXTS" | tr '|' '\n' | grep -ci 'pin')"
tap menu music_menu_fav_add; sleep 2
d after-fav; shot 05-after-fav
adb shell input keyevent KEYCODE_BACK; sleep 1

# ----------------------------------------------------------------------------------------------- 5: force-stop, reopen
log "--- 5: force-stop; reopen radio (the network stays on)"
letter d
rings_save
adb shell am force-stop app.tileshell; sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 4
assert_eq "(d) airplane mode is off for the reopen (no offline leg, Q-20-1)" "0" "$(airplane)"
MARK2="$(ring_mark)"
to_radio; assert_eq "(d) Music reopened on the radio pivot" "0" "$?"
sleep 2; d reopen; shot 06-reopen-radio
ORDER="$(ids reopen radio_ | grep -E '^radio_(group|fav|row):' | tr '\n' ' ')"
record "(d) the radio list after the reopen, top to bottom" "$ORDER"
assert_yes "(d) a favourites group is drawn (radio_group:favourites)" "$(has reopen radio_group:favourites)"
assert_eq "(d) QA Jazz One is first under favourites (radio_fav:<id>)" "radio_fav:$J1" "$(ids reopen radio_fav: | head -1)"
assert_eq "(d) …reading QA Jazz One" "QA Jazz One" "$(nt reopen "music_pri:radio_fav:$J1")"
assert_eq "(d) …and the favourites group comes before every station row" "radio_group:favourites radio_fav:$J1" "$(echo "$ORDER" | tr ' ' '\n' | grep -E '^radio_(group:favourites|fav:|row:)' | head -2 | tr '\n' ' ' | sed 's/ $//')"
adb shell input swipe 540 1800 540 1000 400; sleep 1.5; d reopen2; shot 07-reopen-stations
STATIONS="$( { ids reopen radio_row:; ids reopen2 radio_row:; } | sort -u | tr '\n' ' ')"
record "(d) station rows listed after the reopen (two dumps, the list scrolled once)" "$STATIONS"
for u in $J2 $N1 $F1; do assert_contains "(d) the stations are listed: radio_row:$u" "radio_row:$u " "$STATIONS"; done
mring "$MARK2" > "$ROW_DIR/slice-reopen.txt"

# ----------------------------------------------------------------------------------------------- (e) host side
letter e
rings_save
adb shell am force-stop app.tileshell; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 3
fixtures_down
TOTAL="$(flog "$RLOG.jsonl" 'True' | wc -l | xargs)"; CTOTAL="$(flog "$CLOG.jsonl" 'True' | wc -l | xargs)"
NOT_T="$( { flog "$RLOG.jsonl" 'not h.get("user-agent", "").startswith("Tessera/")'; flog "$CLOG.jsonl" 'not h.get("user-agent", "").startswith("Tessera/")'; } | wc -l | xargs)"
flog "$RLOG.jsonl" 'True' > "$ROW_DIR/radio-requests.txt"
record "(e) the radio fixture's requests in this row / the catalogue's" "$TOTAL / $CTOTAL"
assert_ne "(e) the radio fixture logged requests (the check has something to read)" "0" "$TOTAL"
assert_eq "(e) every fixture request carries User-Agent: Tessera/ (requests without it)" "0" "$NOT_T"
record "(e) one of them" "$(head -1 "$ROW_DIR/radio-requests.txt")"

ensure_start
p20_end a b c d e
