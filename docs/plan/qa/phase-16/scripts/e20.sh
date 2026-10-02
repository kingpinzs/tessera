#!/usr/bin/env bash
# Phase 16 E20 — People geometry ([fidelity] H2: r11/people.md's values, ± 1 epx unless stated, relative to the drawn
# status bar's bottom) and the pivot's motion line, clause by clause from the phase doc's row E20.
#
#   list       per E10 (rows, avatars, names, letter headers — on the drawn pixels), plus the letter cap top 43.5 ± 0.5
#              epx above its group's first avatar top (P1.6)
#   pivots     CONTACTS / GROUPS in semibold caps, cap 11.0, cap top 19.5 below the status bar, the selected pivot
#              white and the other (156,156,156) ± 4 (P1.1–P1.2)
#   search     the box 36 epx tall from 48 epx below the status bar, x 12 → W − 12, a 2-epx (133,133,133) border,
#              "Search" at x 25 (P1.3)
#   grid       72-epx cells in 4 columns, the first row's cap top 119.5 below the status bar, letters cap 14.4 (P2.2–P2.4)
#   card       an accent page from the status bar's bottom to the nav bar's top, the name in caps at x 13.25 with its
#              cap top 26 below the status bar, people_card_photo a 124-epx circle at x 12 with its top 96 below the
#              status bar, action labels at x 13 ± 1 on a 48-epx pitch (one line) / 65.5 ± 1 (two lines) (P3.1–P3.7)
#   editor     "EDIT <ACCOUNT> CONTACT" in caps at x 13, fields 32 epx with a 2-epx (133,133,133) border from x 12 to
#              W − 12, label cap top → box top 22.75, "+ field" rows at a 44-epx pitch, 1-epx (103,103,103) group rules
#              (P4.1–P4.7). The 34-epx "with an edit button" variant is NOT built (Change Log 2026-10-01, P3): what is
#              drawn is asserted — every box 32 epx, no edit button beside Name — and the clause is in clauses-open.tsv
#   app bars   every app bar's buttons at a 68-epx pitch with "…" 24 epx from the right edge (P0.4)
#   bars       as E19: the drawn bars at BarMetrics.STATUS_EPX / NAV_EPX (read from the code, C-17) on every page and the
#              editor, `dumpsys window` showing the system bars not visible (phase 01 E19's form)
#   motion     from a MARK, an input swipe from CONTACTS to GROUPS → people_pivot:groups selected, and
#              `[motion] people_pivot … settle=<ms>` 250 ± 17 ms with maxGapMs ≤ 33.4 (C-31)
#   fixtures   the row's own (people_fixtures_up / people_fixtures_down)
#
# Lengths are read off screencaps (a tappable node's dump bounds are Android's 48-epx touch target); the dump only says
# where to look. An r11 value the row does not word is RECORDed with its measurement, never graded.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"
groups_q() { q "content query --uri $GROUPS_URI --projection _id:title:account_type:account_name"; }
# One page, as it stands on screen: dumped and captured; its drawn bars; the system's bars hidden; its app bar if it has one.
page() { # label [no-appbar]
  local label="$1" f="$D/page-$(echo "$1" | tr ' /' '--')"
  dump_ui "$f.xml"; screencap "$f.png"
  emit_verdicts "bars on $label" < <(people_geo bars "$f.xml" "$f.png" "$ACCENT" "$BARS" 2>>"$D/geometry.err" | sed "s/^\(PASS\|FAIL\)|/\1|$label: /")
  assert_eq "$label: dumpsys window shows the system's status and nav bars not visible" "navigationBars=false statusBars=false" "$(system_bars_hidden)"
  if [ "${2:-}" != no-appbar ] && [ "$(has_node "$f.xml" people_app_bar)" = yes ]; then
    emit_verdicts "app bar on $label" < <(people_geo appbar "$f.xml" "$f.png" "$ACCENT" "$label" 2>>"$D/geometry.err")
    APPBARS=$((APPBARS + 1))
  fi
  PAGES=$((PAGES + 1))
  LAST="$f"
}

row_begin E20 "People geometry on the drawn pixels, the bars on every page, the pivot's motion"
require_build
D="$ROW_DIR"; PAGES=0; APPBARS=0
ACCENT="$(accent_rgb)"; BARS="$(bar_metrics)"
note "accent $ACCENT; BarMetrics STATUS_EPX,NAV_EPX = $BARS (read from bars/SystemBars.kt)"
assert_ne "BarMetrics.STATUS_EPX and NAV_EPX were read from the code" "," "$BARS"
ensure_start
perm_ensure READ_CONTACTS WRITE_CONTACTS
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"
groups_q > "$D/groups-before.txt"
people_fixtures_up
L_ANN="$(lookup_of "$(contact_of "$ANN")")"

# ------------------------------------------------------------------------------------------------ the list
log "--- the list: per E10, P1.6, the pivot header, the search box"
open_people -a android.intent.action.MAIN
wait_node "$D/.w.xml" people_page:list 8 || true
list_top
page "list"
emit_verdicts "list geometry" < <(list_geometry "$LAST.xml" "$LAST.png" "$ACCENT" e20 2>>"$D/geometry.err")
emit_verdicts "pivots and search" < <(people_geo pivots "$LAST.xml" "$LAST.png" "$ACCENT" 2>>"$D/geometry.err")

# ------------------------------------------------------------------------------------------------ the jump grid
log "--- the jump grid"
tap_node "$LAST.xml" people_letter:A; sleep 2
page "jump grid"
assert_eq "the jump grid is on show" "yes" "$(has_node "$LAST.xml" people_jump_grid)"
emit_verdicts "jump grid" < <(people_geo grid "$LAST.xml" "$LAST.png" "$ACCENT" 2>>"$D/geometry.err")
tap_node "$LAST.xml" people_jump_cell:A; sleep 2

# ------------------------------------------------------------------------------------------------ search results
# The results are read with the keyboard closed: a c6 before this row deselects the shell's own keyboard (a force-stop
# does — qa/phase-05/README.md), the image's keyboard then comes up over the app bar with the SYSTEM's nav bar beside
# it, and that is the keyboard's state, not this page's (run 1 read the bars with it up).
tapid people_search 1; type_text "ann"; sleep 2
back 1.5
dump_ui "$D/.search.xml"
assert_eq "search results: the page shows Ann's row with the keyboard closed" "yes" "$(has_node "$D/.search.xml" "people_row:$L_ANN")"
record "search results: the input method selected while the row ran" "$(adb shell settings get secure default_input_method | tr -d '\r')"
page "search results"
tapid people_search 1; adb shell input keyevent 67 67 67; sleep 1; back 1.5; back 1

# ------------------------------------------------------------------------------------------------ the pivot's motion
log "--- motion: CONTACTS → GROUPS"
open_people -a android.intent.action.MAIN
dump_ui "$D/motion-before.xml"
assert_contains "before the swipe CONTACTS is the pivot on show" 'selected="true"' "$(node_tag "$D/motion-before.xml" people_pivot:contacts)"
M_MARK="$(ring_mark)"
adb shell input swipe 900 1300 180 1300 250
sleep 2.5
dump_ui "$D/motion-after.xml"
assert_contains "after the swipe people_pivot:groups is selected" 'selected="true"' "$(node_tag "$D/motion-after.xml" people_pivot:groups)"
M_LINE="$(ring_since "$M_MARK" | grep -F '[motion] people_pivot' | tail -1 | sed 's/.*\[motion\]/[motion]/')"; log "$M_LINE"
assert_contains "the slice holds [motion] people_pivot … with every field of the motion clock" "[motion] people_pivot t0=" "$M_LINE"
for fld in peak overshoot settle frames maxGapMs; do assert_contains "… its $fld field" " $fld=" "$M_LINE"; done
SETTLE="$(printf '%s' "$M_LINE" | sed -n 's/.* settle=\([0-9.]*\).*/\1/p')"; GAP="$(printf '%s' "$M_LINE" | sed -n 's/.* maxGapMs=\([0-9.]*\).*/\1/p')"
assert_within "people_pivot settle 250 ± 17 ms (X13; a tagged approximation, H17)" 250 "${SETTLE:-x}" 17
assert_eq "people_pivot maxGapMs ≤ 33.4 (C-31)" "yes" "$(python3 -c "import sys; print('yes' if float(sys.argv[1]) <= 33.4 else 'no')" "${GAP:-999}" 2>/dev/null || echo no)"
page "GROUPS pivot"

# ------------------------------------------------------------------------------------------------ a group's pages
log "--- the group editor and a group's page (their bars and app bars)"
tapid people_group_new 2
page "group editor"
set_field people_group_name "Geo"
tapid people_group_save 3
GID="$(groups_q | grep -F 'title=Geo,' | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
note "the row's own group: Geo, _id=${GID:-none}"
page "group page"
back 1; back 1

# ------------------------------------------------------------------------------------------------ the card
log "--- the card"
card_of "$ANN" "$D/.card.xml"
page "card"
assert_eq "the card on show is Ann's (people_card:<lookup>)" "yes" "$(has_node "$LAST.xml" "people_card:$L_ANN")"
emit_verdicts "card geometry" < <(people_geo card "$LAST.xml" "$LAST.png" "$ACCENT" 2>>"$D/geometry.err")
CARD="$LAST"
tap_node "$CARD.xml" people_card_link; sleep 2
page "linked profiles"
back 1

# ------------------------------------------------------------------------------------------------ the editor
log "--- the editor"
card_of "$ANN" "$D/.card2.xml"
tap_node "$D/.card2.xml" people_card_edit; sleep 2
page "editor"
assert_contains "the editor is the page on show" 'selected="true"' "$(node_tag "$LAST.xml" people_page:editor)"
emit_verdicts "editor geometry" < <(people_geo editor "$LAST.xml" "$LAST.png" "$ACCENT" 2>>"$D/geometry.err")
# The 34-epx variant "with an edit button" (P4.4) is not built: what is drawn is asserted (clauses-open.tsv, E20).
assert_eq "as built: no edit (pencil) button node beside the Name field (the 34-epx variant is not built; clauses-open.tsv)" "0" "$(grep -o 'resource-id="people_field_edit[^"]*"' "$LAST.xml" | grep -c . )"
# the rest of the editor, scrolled: every "+ field" row and group rule further down
PREV_SIG="$(grep -o 'resource-id="people_[^"]*"[^>]*bounds="[^"]*"' "$LAST.xml" | sed 's/ .*bounds=/ /' | tr '\n' ';')"
for i in 1 2 3; do
  adb shell input swipe 540 1700 540 900 600; sleep 1.5
  dump_ui "$D/editor-scroll-$i.xml"; screencap "$D/editor-scroll-$i.png"
  SIG="$(grep -o 'resource-id="people_[^"]*"[^>]*bounds="[^"]*"' "$D/editor-scroll-$i.xml" | sed 's/ .*bounds=/ /' | tr '\n' ';')"
  if [ "$SIG" = "$PREV_SIG" ]; then note "the editor is at its end after $((i - 1)) swipe(s)"; break; fi   # the same screen again: nothing new to measure
  PREV_SIG="$SIG"
  emit_verdicts "editor geometry (scrolled $i)" < <(people_geo editor "$D/editor-scroll-$i.xml" "$D/editor-scroll-$i.png" "$ACCENT" scrolled 2>>"$D/geometry.err" | sed "s/^\(PASS\|FAIL\|RECORD\)|/\1|scrolled $i: /")
done
back 1; back 1
assert_ne "the editor of a contact WITH a number and an e-mail: \"+ Email\" → \"+ Address\" was measured across its rule" "0" "$(grep -c '"+ email" → "+ address" across their group rule' "$LOG")"

# The 44-epx pitch (re-cut with the fix build, fix-round F31 / D-E20-1): r11's G1 shows it between "+ Phone" and "+ Email"
# of a contact that has neither a number nor an e-mail, so the row's own name-only fixture (Bob Stone gets no data but
# his name and number in the standing set; a contact with only a name is made here) is opened in the editor.
log "--- the editor of a name-only contact: \"+ Phone\" → \"+ Email\" in one group"
NAMEONLY="$(people_add 'Nomi Only')"; sleep 2
card_of "$NAMEONLY" "$D/.card3.xml"
tap_node "$D/.card3.xml" people_card_edit; sleep 2
dump_ui "$D/editor-nameonly.xml"; screencap "$D/editor-nameonly.png"
assert_contains "the name-only contact's editor is on show" 'selected="true"' "$(node_tag "$D/editor-nameonly.xml" people_page:editor)"
assert_eq "… it holds her name and nothing else of hers" "Nomi Only" "$(xml_text "$D/editor-nameonly.xml" people_field:name)"
emit_verdicts "editor geometry (name-only)" < <(people_geo editor "$D/editor-nameonly.xml" "$D/editor-nameonly.png" "$ACCENT" nameonly 2>>"$D/geometry.err" | sed "s/^\(PASS\|FAIL\|RECORD\)|/\1|name-only: /")
back 1; back 1

# ------------------------------------------------------------------------------------------------ the settings pages
log "--- People's settings pages (their bars)"
open_people -a android.intent.action.MAIN
tapid people_more 2; tapid people_more:settings 2
page "settings"
for p in can_edit filter sim; do
  tapid "people_settings:$p" 2
  page "settings $p"
  back 1
done
back 1

assert_eq "the bars were asserted on every People page reached (list, grid, search, GROUPS, group editor, group page, card, linked profiles, editor, settings and its three pages)" "13" "$PAGES"
record "app bars measured" "$APPBARS"

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
[ -n "${GID:-}" ] && q "content delete --uri '$GROUPS_URI/$GID?$SA'" >/dev/null
assert_eq "restore: the groups query equals its read before the row" "$(cat "$D/groups-before.txt")" "$(groups_q)"
people_fixtures_down
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: the raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
[ -s "$D/geometry.err" ] && { note "measuring-script stderr:"; cat "$D/geometry.err" >> "$LOG"; }
c6; ensure_start
row_end
