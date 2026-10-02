#!/usr/bin/env bash
# Phase 16 E14 — Link and unlink, clause by clause from the phase doc's row E14.
#
#   fixtures   "Sam Reed" (number only) and "Sam Reed" (e-mail only), both phone-only raw contacts — one account for
#              both, so the non-merge does not rest on the fixtures' accounts (r3 V15)
#   provider   asserted BEFORE the app is read: two raw_contacts rows with two different contact_ids
#   link       Link from the first card picks the second → aggregation_exceptions shows type 1 (KEEP_TOGETHER) for
#              the pair; the list shows ONE Sam Reed; the card carries both the number and the e-mail
#   unlink     Unlink → type 2 (KEEP_SEPARATE) and two rows again
#   ring       `[people] link <a>+<b>: ok` (and the build's added `[people] unlink …: ok`, Change Log 2026-10-01 P2)
#   restore    people_fixtures_down; asserted: the aggregation_exceptions query lists neither id
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"
sams() { q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name --where \"display_name='Sam Reed' AND deleted=0\""; }
sam_rows_in_list() { # prefix -> how many rows of the walked list read "Sam Reed"
  open_people -a android.intent.action.MAIN; list_top
  list_walk "$1" > "$1-merged.tsv"
  awk -F'\t' '$1=="row" && $3=="Sam Reed"' "$1-merged.tsv" | grep -c .
}

row_begin E14 "Link and unlink"
require_build
D="$ROW_DIR"
ensure_start
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"
exceptions > "$D/exceptions-before.txt"
note "aggregation_exceptions before the row: $(tr '\n' ';' < "$D/exceptions-before.txt")"

log "--- fixtures: two phone-only Sam Reeds, checked in the provider before the app is read"
: > "$ROW_DIR/people-fixtures.ids"
RAW_BEFORE="$(raw_count)"
S1="$(people_add 'Sam Reed' '+1 555 000 0011')"
S2="$(people_add 'Sam Reed' '' 'sam@example.com')"
sleep 4          # the provider's aggregation pass has run by now: a pair it would merge is merged
ROWS="$(sams)"; printf '%s\n' "$ROWS" >> "$LOG"
assert_eq "provider: two raw_contacts rows named Sam Reed" "2" "$(printf '%s\n' "$ROWS" | grep -c '_id=')"
assert_eq "provider: both are phone-only (one account for both: NULL / NULL)" "2" "$(printf '%s\n' "$ROWS" | grep -c 'account_name=NULL, account_type=NULL')"
TWO="$(printf '%s\n' "$ROWS" | grep -oE 'contact_id=[0-9]+' | sort -u | wc -l)"
assert_eq "provider: two DIFFERENT contact_ids before the link" "2" "$TWO"
assert_contains "the first holds only the number" "data1=+1 555 000 0011" "$(data_rows "$S1")"
assert_absent "… and no e-mail" "email_v2" "$(data_rows "$S1")"
assert_contains "the second holds only the e-mail" "data1=sam@example.com" "$(data_rows "$S2")"
assert_absent "… and no number" "phone_v2" "$(data_rows "$S2")"
if [ "$TWO" != 2 ]; then
  log "the fixtures merged on their own: the row fails here, on its fixture, and the app is not read (r3 V15)"
  people_fixtures_down; ensure_start; row_end; exit 1
fi
C1="$(contact_of "$S1")"; C2="$(contact_of "$S2")"; L1="$(lookup_of "$C1")"; L2="$(lookup_of "$C2")"
LO=$(( S1 < S2 ? S1 : S2 )); HI=$(( S1 < S2 ? S2 : S1 ))
note "raw $S1 → contact $C1 ($L1); raw $S2 → contact $C2 ($L2)"
assert_absent "no aggregation exception names the pair before the link" "raw_contact_id1=$LO, raw_contact_id2=$HI" "$(exceptions)"
assert_eq "the list shows two Sam Reed rows before the link" "2" "$(sam_rows_in_list "$D/walk-before")"

# ------------------------------------------------------------------------------------------------ Link
log "--- Link from the first card picks the second"
card_of "$S1" "$D/card1.xml"; screencap "$D/card1.png"
assert_eq "the first Sam Reed's card (people_card:<lookup>)" "yes" "$(has_node "$D/card1.xml" "people_card:$L1")"
assert_contains "… it carries the number" "+1 555 000 0011" "$(cat "$D/card1.xml")"
assert_absent "… and not the e-mail" "sam@example.com" "$(cat "$D/card1.xml")"
tap_node "$D/card1.xml" people_card_link; sleep 2
dump_ui "$D/link.xml"; screencap "$D/link.png"
assert_contains "the linked-profiles page is on show" 'selected="true"' "$(node_tag "$D/link.xml" people_page:link)"
assert_eq "it lists the contact's one raw contact (people_link_row:<raw>)" "people_link_row:$S1" "$(ids_with_prefix "$D/link.xml" people_link_row: | tr '\n' ' ' | sed 's/ $//')"
tap_node "$D/link.xml" people_link_add; sleep 2
MARK="$(ring_mark)"
tap_row_clear "people_row:$L2" "$D/picker.xml"; sleep 1
screencap "$D/after-pick.png"
assert_contains "the picker was the page the pick was made on" 'selected="true"' "$(node_tag "$D/picker.xml" people_page:link_picker)"
# The doc's query, whole.
EX="$(q "content query --uri $AGGEX --projection type:raw_contact_id1:raw_contact_id2")"; printf '%s\n' "$EX" > "$D/exceptions-linked.txt"; printf '%s\n' "$EX" >> "$LOG"
assert_contains "aggregation_exceptions: type 1 (KEEP_TOGETHER) for the pair" "type=1, raw_contact_id1=$LO, raw_contact_id2=$HI" "$EX"
LINK_LINE="$(ring_since "$MARK" | grep -F '[people] link' | tail -1 | sed 's/.*\[people\]/[people]/')"; log "$LINK_LINE"
assert_eq "diagnostics: [people] link <a>+<b>: ok" "[people] link $S1+$S2: ok" "$LINK_LINE"
ROWS="$(sams)"; printf '%s\n' "$ROWS" >> "$LOG"
assert_eq "provider: one contact behind both raw contacts" "1" "$(printf '%s\n' "$ROWS" | grep -oE 'contact_id=[0-9]+' | sort -u | wc -l)"
assert_eq "the list shows ONE Sam Reed" "1" "$(sam_rows_in_list "$D/walk-linked")"
card_of "$S1" "$D/joined.xml"; screencap "$D/joined.png"
LJ="$(lookup_of "$(contact_of "$S1")")"
assert_eq "the joined card (people_card:<its lookup>)" "yes" "$(has_node "$D/joined.xml" "people_card:$LJ")"
DETAILS="$(python3 - "$D/joined.xml" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
print("|".join(html.unescape(re.search(r'text="([^"]*)"', m.group(0)).group(1)) for m in re.finditer(r'<node[^>]*resource-id="people_action_detail"[^>]*>', xml)))
PY
)"; log "the joined card's action details: $DETAILS"
assert_contains "the card carries the number" "+1 555 000 0011" "$DETAILS"
assert_contains "the card carries the e-mail" "sam@example.com" "$DETAILS"
assert_eq "… as a call action" "yes" "$(has_node "$D/joined.xml" people_card_action:call:0)"
assert_eq "… and a mail action" "yes" "$(has_node "$D/joined.xml" people_card_action:mail:0)"

# ------------------------------------------------------------------------------------------------ Unlink
log "--- Unlink"
tap_node "$D/joined.xml" people_card_link; sleep 2
dump_ui "$D/link2.xml"; screencap "$D/link2.png"
assert_eq "the linked-profiles page lists both raw contacts" "people_link_row:$LO people_link_row:$HI" "$(ids_with_prefix "$D/link2.xml" people_link_row: | sort -t: -k2 -n | tr '\n' ' ' | sed 's/ $//')"
MARK="$(ring_mark)"
tap_node "$D/link2.xml" "people_link_unlink:$S2"; sleep 3
EX="$(q "content query --uri $AGGEX --projection type:raw_contact_id1:raw_contact_id2")"; printf '%s\n' "$EX" > "$D/exceptions-unlinked.txt"; printf '%s\n' "$EX" >> "$LOG"
assert_contains "aggregation_exceptions: type 2 (KEEP_SEPARATE) for the pair" "type=2, raw_contact_id1=$LO, raw_contact_id2=$HI" "$EX"
assert_absent "… and no type 1 row left for it" "type=1, raw_contact_id1=$LO, raw_contact_id2=$HI" "$EX"
UNLINK_LINE="$(ring_since "$MARK" | grep -F '[people] unlink' | tail -1 | sed 's/.*\[people\]/[people]/')"; log "$UNLINK_LINE"
assert_contains "diagnostics: the build's unlink line, ok (Change Log 2026-10-01, P2)" "[people] unlink " "$UNLINK_LINE"
assert_contains "… ok" ": ok" "$UNLINK_LINE"
ROWS="$(sams)"; printf '%s\n' "$ROWS" >> "$LOG"
assert_eq "provider: two contacts again" "2" "$(printf '%s\n' "$ROWS" | grep -oE 'contact_id=[0-9]+' | sort -u | wc -l)"
assert_eq "the list shows two Sam Reed rows again" "2" "$(sam_rows_in_list "$D/walk-unlinked")"

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
back 1
people_fixtures_down
EX="$(exceptions)"; printf '%s\n' "$EX" > "$D/exceptions-after.txt"; log "aggregation_exceptions after the restore: $(echo "$EX" | tr '\n' ';')"
assert_absent "restore: the aggregation_exceptions query lists no row with the first id" "raw_contact_id1=$LO," "$EX"
assert_absent "restore: … nor with the second" "raw_contact_id2=$HI" "$EX"
assert_eq "restore: aggregation_exceptions equals its read before the row" "$(cat "$D/exceptions-before.txt")" "$EX"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: the raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
c6; ensure_start
row_end
