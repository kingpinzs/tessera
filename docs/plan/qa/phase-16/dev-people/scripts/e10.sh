#!/usr/bin/env bash
# DEV-E10 (development proof, E10's core): the A–Z list over phone-only fixtures, the "#" bucket, non-Latin buckets,
# the aggregated pair, People's jump grid and search by name and by number — and the list's measured geometry.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-E10 "People list, # bucket, jump grid, search (E10's core)"
log "$(ensure_build)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
BEFORE="$(raw_count)"
D="$ROW_DIR"

# How many contacts the list reads before the fixtures (the list is lazy: a dump holds only the rows on screen).
MARK="$(ring_mark)"; open_people -a android.intent.action.MAIN
N0="$(ring_since "$MARK" | grep -F '[people] list:' | tail -1 | sed -E 's/.*list: ([0-9]+) contacts.*/\1/')"
log "contacts listed before the fixtures: $N0"
adb shell input keyevent KEYCODE_BACK; sleep 1

ANN=$(fx_raw); fx_name $ANN "Ann Lee"; fx_phone $ANN "+1 555 000 0001"; fx_email $ANN "ann@example.com"
BOB=$(fx_raw); fx_name $BOB "Bob Stone"
ZOE=$(fx_raw); fx_name $ZOE "Zoë Ǻrén"
WEI=$(fx_raw); fx_name $WEI "张伟"
NUM=$(fx_raw); fx_phone $NUM "+1 555 000 0009"
C1=$(fx_raw); fx_name $C1 "Cara Diaz"; fx_phone $C1 "+1 555 000 0003"
C2=$(fx_raw); fx_name $C2 "Cara Diaz"; fx_phone $C2 "+1 555 000 0003"

# The fixture is checked in the provider before the app is read (V15).
CARA="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id:contact_id:display_name --where "\"display_name='Cara Diaz'\"")"
log "$CARA"
assert_eq "Cara Diaz: two raw contacts" "2" "$(echo "$CARA" | grep -c 'display_name=Cara Diaz')"
assert_eq "Cara Diaz: one contact id" "1" "$(echo "$CARA" | grep -oE 'contact_id=[0-9]+' | sort -u | wc -l)"
lk() { lookup_of "$(contact_of "$1")"; }
L_ANN=$(lk $ANN); L_ZOE=$(lk $ZOE); L_WEI=$(lk $WEI); L_NUM=$(lk $NUM); L_CARA=$(lk $C1); L_BOB=$(lk $BOB)
WEI_LABEL="$(S content query --uri content://com.android.contacts/contacts --projection phonebook_label --where "_id=$(contact_of $WEI)" | sed -n 's/.*phonebook_label=//p')"
log "lookups ann=$L_ANN zoe=$L_ZOE wei=$L_WEI num=$L_NUM cara=$L_CARA; the provider's label for 张伟 is [$WEI_LABEL]"

MARK="$(ring_mark)"; open_people -a android.intent.action.MAIN
dump_ui "$D/list.xml"; screencap "$D/list.png"
LINE="$(ring_since "$MARK" | grep -F '[people] list:' | tail -1 | sed 's/.*\[people\]/[people]/')"
log "$LINE"
assert_eq "the list line: the fixtures are six more contacts, read and write held" "[people] list: $((N0 + 6)) contacts read=true write=true" "$LINE"
assert_eq "one Cara Diaz row" "1" "$(grep -o "resource-id=\"people_row:$L_CARA\"" "$D/list.xml" | wc -l)"
assert_eq "the number-only contact reads its number" "+1 555 000 0009" "$(node_text "$D/list.xml" "people_name:$L_NUM")"
# It files under "#": below the # header and above the A header.
TOP() { bfield "$(bounds "$D/list.xml" "$1")" 2; }
assert_eq "the number-only row is between # and A" "yes" "$([ "$(TOP people_letter:#)" -lt "$(TOP people_row:$L_NUM)" ] && [ "$(TOP people_row:$L_NUM)" -lt "$(TOP people_letter:A)" ] && echo yes || echo no)"
assert_eq "Ann is between A and B" "yes" "$([ "$(TOP people_letter:A)" -lt "$(TOP people_row:$L_ANN)" ] && [ "$(TOP people_row:$L_ANN)" -lt "$(TOP people_letter:B)" ] && echo yes || echo no)"

# ---- geometry (r11/people.md P1.5–P1.10, ± 1 epx; values below the DRAWN status bar)
SB="$(status_bottom "$D/list.xml")"
FIRST="$(S content query --uri content://com.android.contacts/contacts --projection lookup --where "\"phonebook_label='#'\"" | sed -n 's/.*lookup=//p' | head -1)"
R1="$(bounds "$D/list.xml" "people_row:$FIRST")"; R2="$(bounds "$D/list.xml" "people_row:$L_NUM")"
log "the two # rows: [$R1] [$R2]"
PITCH=$(( $(bfield "$R2" 2) - $(bfield "$R1" 2) )); [ "$PITCH" -lt 0 ] && PITCH=$(( -PITCH ))
assert_within "row pitch 50 epx (P1.10)" "50" "$(epx $PITCH)" 1
AV="$(bounds "$D/list.xml" "people_avatar:$L_ANN")"
assert_within "avatar width 32 epx (P1.7)" "32" "$(epx $(( $(bfield "$AV" 3) - $(bfield "$AV" 1) )))" 1
assert_within "avatar height 32 epx (P1.7)" "32" "$(epx $(( $(bfield "$AV" 4) - $(bfield "$AV" 2) )))" 1
assert_within "avatar left edge at x 12 (P1.7)" "12" "$(epx $(bfield "$AV" 1))" 1
# The avatar is a circle: its corner is the page's fill, its centre the disc.
assert_color "avatar corner is the page (a circle)" "0,0,0" "$(px "$D/list.png" $(( $(bfield "$AV" 1) + 3 )) $(( $(bfield "$AV" 2) + 3 )))" 4
assert_color "avatar without a photo is a grey disc" "85,85,85" "$(px "$D/list.png" $(( $(bfield "$AV" 1) + 14 )) $(( ($(bfield "$AV" 2) + $(bfield "$AV" 4)) / 2 )))" 6
NB="$(bounds "$D/list.xml" "people_name:$L_ANN")"
NI="$(python3 "$INK" glyph "$D/list.png" $NB bright 100)"
log "Ann's name box [$NB], its first glyph's ink [$NI] (ink.py prints screen pixels)"
assert_within "the name's ink left edge at x 57.75 (P1.9)" "57.75" "$(epx $(bfield "$NI" 1))" 1
LB="$(bounds "$D/list.xml" people_letter:A)"
LI="$(python3 "$INK" glyph "$D/list.png" $LB bright 60)"
log "the A header's box [$LB], ink [$LI]"
assert_within "letter header ink at x 14.75 (P1.5)" "14.75" "$(epx $(bfield "$LI" 1))" 1
assert_within "letter cap 22.25 epx (P1.5)" "22.25" "$(epx $(( $(bfield "$LI" 4) - $(bfield "$LI" 2) )))" 1
assert_within "letter cap top 43.5 epx above its first avatar's top (P1.6)" "43.5" "$(epx $(( $(bfield "$AV" 2) - $(bfield "$LI" 2) )))" 1
assert_color "letter header in accent" "0,120,215" "$(ink_color "$D/list.png" $LB 0,0,0)" 12
PB="$(bounds "$D/list.xml" people_pivot:contacts)"
PI="$(python3 "$INK" glyph "$D/list.png" $PB bright 100)"
log "the CONTACTS header's box [$PB], ink [$PI]; the status bar ends at $SB px"
assert_within "pivot header cap top 19.5 epx below the status bar (P1.1)" "19.5" "$(epx $(( $(bfield "$PI" 2) - SB )))" 1
assert_within "pivot header ink from x 12.5 (P1.1)" "12.5" "$(epx $(bfield "$PI" 1))" 1
assert_within "pivot header cap 11.0 epx (P1.1)" "11" "$(epx $(( $(bfield "$PI" 4) - $(bfield "$PI" 2) )))" 1
assert_contains "CONTACTS is the pivot on show" 'selected="true"' "$(grep -o '<node[^>]*resource-id="people_pivot:contacts"[^>]*>' "$D/list.xml")"
SBX="$(bounds "$D/list.xml" people_search_box)"
assert_within "search box top 48 epx below the status bar (P1.3)" "48" "$(epx $(( $(bfield "$SBX" 2) - SB )))" 1
assert_within "search box 36 epx tall (P1.3)" "36" "$(epx $(( $(bfield "$SBX" 4) - $(bfield "$SBX" 2) )))" 1
assert_within "search box from x 12 (P1.3)" "12" "$(epx $(bfield "$SBX" 1))" 1
assert_within "search box to W − 12 (P1.3)" "348" "$(epx $(bfield "$SBX" 3))" 1
assert_color "search box border (133,133,133)" "133,133,133" "$(px "$D/list.png" $(( $(bfield "$SBX" 1) + 2 )) $(( ($(bfield "$SBX" 2) + $(bfield "$SBX" 4)) / 2 )))" 4

# ---- the jump grid (P2.2–P2.5)
tap_node "$D/list.xml" people_letter:A; sleep 2
dump_ui "$D/grid.xml"; screencap "$D/grid.png"
assert_eq "a letter header opens People's jump grid" "yes" "$(has_node "$D/grid.xml" people_jump_grid)"
assert_eq "28 cells: #, A–Z, the globe" "28" "$(grep -o 'resource-id="people_jump_cell:[^"]*"' "$D/grid.xml" | wc -l)"
cellcol() { ink_color "$D/grid.png" $(bounds "$D/grid.xml" "people_jump_cell:$1") 0,0,0; }
for c in '#' A B C M Z globe; do assert_color "cell $c is in accent (contacts file there)" "0,120,215" "$(cellcol "$c")" 12; done
for c in D E K Q Y; do assert_color "cell $c is (52,52,52) (none file there)" "52,52,52" "$(cellcol "$c")" 4; done
CH="$(bounds "$D/grid.xml" 'people_jump_cell:#')"; CA="$(bounds "$D/grid.xml" people_jump_cell:A)"; CD="$(bounds "$D/grid.xml" people_jump_cell:D)"; CG="$(bounds "$D/grid.xml" people_jump_cell:globe)"; CZ="$(bounds "$D/grid.xml" people_jump_cell:Z)"
assert_within "cell pitch across 72 epx (P2.2)" "72" "$(epx $(( $(bfield "$CA" 1) - $(bfield "$CH" 1) )))" 1
assert_within "cell pitch down 72 epx (P2.2)" "72" "$(epx $(( $(bfield "$CD" 2) - $(bfield "$CH" 2) )))" 1
assert_eq "four columns at 360 epx: D starts the second row under #" "$(bfield "$CH" 1)" "$(bfield "$CD" 1)"
assert_eq "# is the first cell (top-left)" "yes" "$([ "$(bfield "$CH" 1)" -le "$(bfield "$CA" 1)" ] && [ "$(bfield "$CH" 2)" -le "$(bfield "$CA" 2)" ] && echo yes || echo no)"
assert_eq "the globe is the last cell (after Z, on the last row)" "yes" "$([ "$(bfield "$CG" 2)" -eq "$(bfield "$CZ" 2)" ] && [ "$(bfield "$CG" 1)" -gt "$(bfield "$CZ" 1)" ] && echo yes || echo no)"
GI="$(python3 "$INK" glyph "$D/grid.png" $CA bright 40)"
assert_within "first row's cap top 119.5 epx below the status bar (P2.3)" "119.5" "$(epx $(( $(bfield "$GI" 2) - SB )))" 1
assert_within "grid letters cap 14.4 epx (P2.4)" "14.4" "$(epx $(( $(bfield "$GI" 4) - $(bfield "$GI" 2) )))" 1
assert_eq "the pivot header stays above the grid (P2.1)" "yes" "$(has_node "$D/grid.xml" people_pivot:contacts)"
assert_eq "the search box stays above the grid (P2.1)" "yes" "$(has_node "$D/grid.xml" people_search_box)"
MARK="$(ring_mark)"
tap_node "$D/grid.xml" people_jump_cell:Z; sleep 2
dump_ui "$D/at_z.xml"; screencap "$D/at_z.png"
assert_eq "the grid closed" "no" "$(has_node "$D/at_z.xml" people_jump_grid)"
assert_eq "the list landed on Z: Zoë's row is on screen" "yes" "$(has_node "$D/at_z.xml" "people_row:$L_ZOE")"
assert_contains "the jump is logged" "[people] jump to Z" "$(ring_since "$MARK")"
assert_eq "Zoë files under Z" "yes" "$([ "$(bfield "$(bounds "$D/at_z.xml" people_letter:Z)" 2)" -lt "$(bfield "$(bounds "$D/at_z.xml" "people_row:$L_ZOE")" 2)" ] && echo yes || echo no)"
assert_eq "张伟's header reads the provider's phonebook label" "$WEI_LABEL" "$(node_text "$D/at_z.xml" "people_letter:$WEI_LABEL")"
assert_eq "张伟 is under that header" "yes" "$([ "$(bfield "$(bounds "$D/at_z.xml" "people_letter:$WEI_LABEL")" 2)" -lt "$(bfield "$(bounds "$D/at_z.xml" "people_row:$L_WEI")" 2)" ] && echo yes || echo no)"

# ---- search: by number typed with spaces (the provider's phone lookup), and by name
search() { # query -> the rows it lists and the search line
  local q="$1" tag="$2" d="$D/search-$2.xml"
  dump_ui "$D/.s.xml"; tap_node "$D/.s.xml" people_search; sleep 1
  local mark; mark="$(ring_mark)"
  type_text "$q"; sleep 2
  dump_ui "$d"
  assert_eq "search [$q]: one row" "1" "$(grep -o 'resource-id="people_row:[^"]*"' "$d" | wc -l)"
  assert_eq "search [$q]: it is Ann Lee" "yes" "$(has_node "$d" "people_row:$L_ANN")"
  assert_contains "search [$q]: the line" "[people] search \"$q\": 1 (+0 enterprise)" "$(ring_since "$mark")"
  # Clear the box for the next query.
  for _ in $(seq 1 ${#q}); do adb shell input keyevent KEYCODE_DEL; done; sleep 1
}
search "555 000 0001" number
search "ann" name
MARK="$(ring_mark)"
type_text "5550000001"; sleep 2; dump_ui "$D/search-nospace.xml"
assert_eq "a number typed without spaces matches the one stored with them" "yes" "$(has_node "$D/search-nospace.xml" "people_row:$L_ANN")"

# ---- restore
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
people_fixtures_down
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
row_end
