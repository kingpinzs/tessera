#!/usr/bin/env bash
# Phase 16 E10 — People list, search, no-name and duplicates (the phase doc's row E10, clause by clause).
#
#   fixtures   people_fixtures_up: Ann Lee (+1 555 000 0001, ann@example.com), Bob Stone, Zoë Ǻrén, 张伟, a number-only
#              raw contact (+1 555 000 0009), two raw contacts "Cara Diaz" sharing +1 555 000 0003 — all phone-only
#   provider   asserted BEFORE the app is read (r3 V15): Cara Diaz is two raw contacts with one contact_id
#   list       one Cara Diaz row; the number-only contact under "#" reading its number; 张伟 under the provider's
#              phonebook label; Zoë under Z
#   jump grid  a letter header opens it; exactly the labels that exist are in accent, the others (52,52,52) ± 4;
#              "#" first, the globe last; people_jump_cell:Z lands the list on Z
#   search     "555 000 0001" and "ann" each return Ann Lee only, with the `[people] search …: 1` line
#   geometry   rows at a 50 ± 1 epx pitch, a 32 ± 1 epx circular avatar at x 12 ± 1, the name at x 57.75 ± 1, the initial
#              on a grey disc, accent letter headers at x 14.75 (r11/people.md P1.5–P1.10) — on the drawn pixels
#   restore    people_fixtures_down; the raw_contacts count equals the count before the row
#
# The AVD holds two contacts that are not this row's (provision.sh's Mom, qa / qa, and a nameless phone-only raw
# contact, _id 2): every count is against a BEFORE read from the provider, never against an empty book.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"

row_begin E10 "People list, search, no-name and duplicates"
require_build
D="$ROW_DIR"
ACCENT="$(accent_rgb)"; note "the shell's accent: $ACCENT"
note "people_filter.json at the start: $(adb shell run-as app.tileshell cat files/people_filter.json 2>/dev/null | tr -d '\r')"
ensure_start
C0="$(contacts_count)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"
note "contacts (aggregates) in the provider before the fixtures: $C0; raw contacts: $(cat "$D/raw-before.txt" | tr '\n' ';')"

# ------------------------------------------------------------------------------------------------ fixtures
log "--- fixtures (people_fixtures_up), checked in the provider before the app is read"
people_fixtures_up
FIX_IDS="$ANN,$BOB,$ZOE,$ZHANG,$NUMBER_ONLY,$CARA1,$CARA2"
FIX_ROWS="$(q "content query --uri $RAW --projection _id:account_name:account_type:display_name --where \"_id IN ($FIX_IDS)\"")"
printf '%s\n' "$FIX_ROWS" > "$D/fixture-raw-rows.txt"; printf '%s\n' "$FIX_ROWS" >> "$LOG"
assert_eq "seven fixture raw contacts were inserted" "7" "$(printf '%s\n' "$FIX_ROWS" | grep -c '_id=')"
assert_eq "all seven are phone-only (NULL account name and type)" "7" "$(printf '%s\n' "$FIX_ROWS" | grep -c 'account_name=NULL, account_type=NULL')"
assert_contains "Ann Lee's number is in the provider" "data1=+1 555 000 0001" "$(data_rows "$ANN")"
assert_contains "Ann Lee's e-mail is in the provider" "data1=ann@example.com" "$(data_rows "$ANN")"
assert_contains "the number-only raw contact holds only its number" "data1=+1 555 000 0009" "$(data_rows "$NUMBER_ONLY")"
assert_absent "… and no name row" "vnd.android.cursor.item/name" "$(data_rows "$NUMBER_ONLY")"

# r3 V15: the doc's own query. The provider aggregates on a delay: poll up to 12 s before grading.
for _ in 1 2 3 4 5 6; do
  CARA="$(q "content query --uri $RAW --projection _id:contact_id:display_name --where \"display_name='Cara Diaz'\"")"
  [ "$(printf '%s\n' "$CARA" | grep -oE 'contact_id=[0-9]+' | sort -u | wc -l)" = 1 ] && break
  sleep 2
done
printf '%s\n' "$CARA" >> "$LOG"
assert_eq "provider: 'Cara Diaz' lists TWO raw contacts" "2" "$(printf '%s\n' "$CARA" | grep -c 'display_name=Cara Diaz')"
CARA_CONTACTS="$(printf '%s\n' "$CARA" | grep -oE 'contact_id=[0-9]+' | sort -u | wc -l)"
assert_eq "provider: … with ONE contact_id (the provider aggregated them)" "1" "$CARA_CONTACTS"
if [ "$CARA_CONTACTS" != 1 ]; then
  log "the fixture did not aggregate: the row fails here, on its fixture, and the app is not read (r3 V15)"
  people_fixtures_down; ensure_start; row_end; exit 1
fi
lk() { lookup_of "$(contact_of "$1")"; }
L_ANN="$(lk "$ANN")"; L_BOB="$(lk "$BOB")"; L_ZOE="$(lk "$ZOE")"; L_ZHANG="$(lk "$ZHANG")"; L_NUM="$(lk "$NUMBER_ONLY")"; L_CARA="$(lk "$CARA1")"
note "lookups: ann=$L_ANN bob=$L_BOB zoe=$L_ZOE zhang=$L_ZHANG number-only=$L_NUM cara=$L_CARA"
# The doc's label query (display_name:phonebook_label), kept whole; 张伟's label and every label that exists.
q "content query --uri $CONTACTS --projection _id:display_name:phonebook_label" > "$D/provider-labels.txt"
cat "$D/provider-labels.txt" >> "$LOG"
ZHANG_LABEL="$(q "content query --uri $CONTACTS --projection phonebook_label --where \"_id=$(contact_of "$ZHANG")\"" | sed -n 's/.*phonebook_label=//p' | head -1)"
ZOE_LABEL="$(q "content query --uri $CONTACTS --projection phonebook_label --where \"_id=$(contact_of "$ZOE")\"" | sed -n 's/.*phonebook_label=//p' | head -1)"
note "the provider's PHONEBOOK_LABEL: 张伟 → [$ZHANG_LABEL], Zoë Ǻrén → [$ZOE_LABEL]"
assert_ne "the provider gives 张伟 a phonebook label" "" "$ZHANG_LABEL"
C1="$(contacts_count)"
assert_eq "provider: the fixtures are six more contacts than before (seven raw contacts, Cara's two as one)" "$((C0 + 6))" "$C1"

# ------------------------------------------------------------------------------------------------ the list
log "--- the list"
MARK="$(ring_mark)"
open_people -a android.intent.action.MAIN
wait_node "$D/list-top.xml" people_page:list 8 || true
screencap "$D/list-top.png"
assert_contains "People's list page is on show (people_page:list selected)" 'selected="true"' "$(node_tag "$D/list-top.xml" people_page:list)"
LINE="$(people_list_line "$MARK")"; log "$LINE"
assert_eq "the list line counts what the provider holds, read and write held" "[people] list: $C1 contacts read=true write=true" "$LINE"
assert_eq "Zoë's row is not laid out on the list's first screen (so the jump below is what brings it)" "no" "$(has_node "$D/list-top.xml" "people_row:$L_ZOE")"
list_walk "$D/walk" > "$D/list-merged.tsv"
cat "$D/list-merged.tsv" >> "$LOG"
assert_eq "the walked list holds one people_row per contact in the provider" "$C1" "$(grep -c '^row' "$D/list-merged.tsv")"
assert_eq "one Cara Diaz row (people_row: for her aggregate)" "1" "$(awk -F'\t' -v k="$L_CARA" '$1=="row" && $2==k' "$D/list-merged.tsv" | grep -c .)"
assert_eq "… and one row reading Cara Diaz in the whole list" "1" "$(awk -F'\t' '$1=="row" && $3=="Cara Diaz"' "$D/list-merged.tsv" | grep -c .)"
assert_eq "the number-only contact files under \"#\"" "#" "$(header_of "$D/list-merged.tsv" "$L_NUM")"
assert_eq "… and reads its number (people_name: text)" "+1 555 000 0009" "$(awk -F'\t' -v k="$L_NUM" '$1=="row" && $2==k {print $3}' "$D/list-merged.tsv")"
assert_eq "张伟 files under the provider's phonebook label" "$ZHANG_LABEL" "$(header_of "$D/list-merged.tsv" "$L_ZHANG")"
assert_eq "Zoë files under Z" "Z" "$(header_of "$D/list-merged.tsv" "$L_ZOE")"
assert_eq "… the label the provider gives her" "Z" "$ZOE_LABEL"
assert_eq "Ann Lee files under A (a control on the walk)" "A" "$(header_of "$D/list-merged.tsv" "$L_ANN")"
# The letter header's own text equals PHONEBOOK_LABEL: read from the dump that holds 张伟's header.
ZH_DUMP=""
for f in "$D"/walk-*.xml; do [ "$(has_node "$f" "people_letter:$ZHANG_LABEL")" = yes ] && ZH_DUMP="$f"; done
assert_ne "a dump holds 张伟's letter header" "" "$ZH_DUMP"
[ -n "$ZH_DUMP" ] && assert_eq "the letter header's text equals PHONEBOOK_LABEL" "$ZHANG_LABEL" "$(xml_text "$ZH_DUMP" "people_letter:$ZHANG_LABEL")"
[ -n "$ZH_DUMP" ] && assert_eq "… and 张伟's row reads 张伟" "张伟" "$(xml_text "$ZH_DUMP" "people_name:$L_ZHANG")"

# ------------------------------------------------------------------------------------------------ geometry
log "--- list geometry on the drawn pixels (r11/people.md P1.5–P1.10)"
list_top
dump_ui "$D/geo.xml"; screencap "$D/geo.png"
emit_verdicts "list geometry" < <(list_geometry "$D/geo.xml" "$D/geo.png" "$ACCENT" e10 2>>"$D/geometry.err")

# ------------------------------------------------------------------------------------------------ the jump grid
log "--- the jump grid"
assert_eq "before the jump Zoë's row is not laid out" "no" "$(has_node "$D/geo.xml" "people_row:$L_ZOE")"
tap_node "$D/geo.xml" people_letter:A; sleep 2
dump_ui "$D/grid.xml"; screencap "$D/grid.png"
assert_eq "a letter header's tap opens People's jump grid (people_jump_grid)" "yes" "$(has_node "$D/grid.xml" people_jump_grid)"
assert_ne "… with people_jump_cell:<label> cells" "0" "$(count_ids "$D/grid.xml" people_jump_cell:)"
# Which labels exist: the provider's phonebook labels, each mapped to its cell (# → #, A–Z → the letter, anything
# else → the globe, r11/people.md P2.5 "a globe glyph (other scripts)").
emit_verdicts "jump grid" < <(python3 - "$D/grid.xml" "$D/grid.png" "$D/provider-labels.txt" "$ACCENT" <<'PY'
import re, sys
import numpy as np
from PIL import Image
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
img = np.asarray(Image.open(sys.argv[2]).convert('RGB')).astype(np.int32)
accent = np.array([int(v) for v in sys.argv[4].split(',')])
labels = set()
for l in open(sys.argv[3], encoding='utf-8'):
    m = re.search(r'phonebook_label=(.*)$', l.rstrip('\n'))
    if m: labels.add(m.group(1))
def cell_of(label):
    if label == '#': return '#'
    if len(label) == 1 and 'A' <= label <= 'Z': return label
    return 'globe'
live = {cell_of(l) for l in labels}
cells = []
for m in re.finditer(r'<node[^>]*>', xml):
    s = m.group(0)
    r = re.search(r'resource-id="people_jump_cell:([^"]*)"', s)
    if not r: continue
    b = [int(v) for v in re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s).groups()]
    cells.append((r.group(1), b))
n = 0
def out(ok, name, detail):
    global n; n += 1
    print("%s|%s|%s" % ("PASS" if ok else "FAIL", name, detail))
order = [c for c, b in sorted(cells, key=lambda x: (x[1][1], x[1][0]))]
want = ['#'] + [chr(c) for c in range(ord('A'), ord('Z') + 1)] + ['globe']
out(order == want, 'the grid\'s cells in reading order: "#" first, A–Z, the globe last (P2.5)', " ".join(order))
print("NOTE|labels the provider holds: %s → live cells %s|" % (sorted(labels), sorted(live)))
for c, b in sorted(cells, key=lambda x: (x[1][1], x[1][0])):
    reg = img[b[1]:b[3], b[0]:b[2]].reshape(-1, 3)
    if len(reg) == 0: out(False, "cell %s is drawn" % c, "empty bounds %s" % (b,)); continue
    col = reg[int(np.abs(reg).sum(axis=1).argmax())]
    if c in live:
        out(np.abs(col - accent).max() <= 4, "cell %s (contacts file there) is in the accent" % c, "(%d,%d,%d) vs accent (%d,%d,%d) ± 4" % (tuple(col) + tuple(accent)))
    else:
        out(np.abs(col - np.array([52, 52, 52])).max() <= 4, "cell %s (no contact files there) is (52,52,52) ± 4" % c, "(%d,%d,%d)" % tuple(col))
print("END|%d" % n)
PY
)
J_MARK="$(ring_mark)"
tap_node "$D/grid.xml" people_jump_cell:Z; sleep 2
dump_ui "$D/at-z.xml"; screencap "$D/at-z.png"
assert_eq "tapping people_jump_cell:Z closes the grid" "no" "$(has_node "$D/at-z.xml" people_jump_grid)"
assert_eq "… and lands the list on Z: the Z header is laid out" "yes" "$(has_node "$D/at-z.xml" people_letter:Z)"
assert_eq "… with Zoë's row (not laid out before the jump)" "yes" "$(has_node "$D/at-z.xml" "people_row:$L_ZOE")"
ZT="$(bfield "$(bounds "$D/at-z.xml" people_letter:Z)" 2)"; RT="$(bfield "$(bounds "$D/at-z.xml" "people_row:$L_ZOE")" 2)"; BARTOP="$(bfield "$(bounds "$D/at-z.xml" people_app_bar)" 2)"
assert_eq "… the Z header above her row, both above the app bar (on screen, not under it)" "yes" "$([ -n "$ZT" ] && [ -n "$RT" ] && [ "$ZT" -lt "$RT" ] && [ "$RT" -lt "$BARTOP" ] && echo yes || echo no)"
note "ring after the jump: $(ring_since "$J_MARK" | grep -F '[people]' | sed 's/.*\[people\]/[people]/' | tr '\n' ';')"

# ------------------------------------------------------------------------------------------------ search
log "--- search by number and by name"
search() { # query tag
  local qy="$1" d="$D/search-$2.xml" mark line
  dump_ui "$D/.s.xml"; tap_node "$D/.s.xml" people_search; sleep 1
  mark="$(ring_mark)"
  type_text "$qy"; sleep 2
  dump_ui "$d"; screencap "$D/search-$2.png"
  assert_eq "search \"$qy\": exactly one row" "1" "$(count_ids "$d" people_row:)"
  assert_eq "search \"$qy\": it is Ann Lee's" "yes" "$(has_node "$d" "people_row:$L_ANN")"
  assert_eq "search \"$qy\": the row reads Ann Lee" "Ann Lee" "$(xml_text "$d" "people_name:$L_ANN")"
  line="$(ring_since "$mark" | grep -F "[people] search \"$qy\":" | tail -1 | sed 's/.*\[people\]/[people]/')"
  log "$line"
  assert_contains "search \"$qy\": the ring line counts 1" "[people] search \"$qy\": 1 " "$line "
  # shellcheck disable=SC2046
  adb shell input keyevent $(printf '67 %.0s' $(seq 1 ${#qy})); sleep 1
}
search "555 000 0001" number
search "ann" name

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
back; back
people_fixtures_down
assert_eq "restore: the contacts (aggregates) count is the count before the row" "$C0" "$(contacts_count)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: the raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
c6; ensure_start
row_end
