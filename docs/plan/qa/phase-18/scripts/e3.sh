#!/usr/bin/env bash
# Phase 18 E3 — listing, sort and the location bar. The doc's clauses → this driver's legs.
#
#   listing    QA-Files (the row's own, `files_up`) lists every fixture of the table that is not hidden — `a.txt`,
#              `b.bin`, `sub`, the six images, the video, the MP3 (and the table's other two folders) — read through
#              scrolling (`list_all`: the page shows about eight rows); rows at a 64 ± 1 epx pitch, two lines each
#              (`files_row:` over `files_detail:`), each with its `files_icon:` node (a type icon 32 wide, a thumbnail 40
#              wide — RECORDED per kind); EVERY `files_detail:` string equals the fixture table's literal (`fx_detail`:
#              a folder a date only, a file its size then its date — `files_detail:b.bin` = "300 KB 1/2/2026").
#   sort       the sort line reads "Sort by: Name"; the flyout offers exactly `files_sort_item:name|date|size` (no
#              Type); each key gives the order the table dictates (`fx_order name|date|size`: folders first in every
#              sort, Name A → Z ignoring case, Date newest first, Size largest first with the folders by name) — three
#              full orders, exact; the sort line follows the key; Name is chosen again at the end (the device as found).
#   location   open `sub` → `files_crumb:` texts "This Device", "QA-Files", "sub"; tap `files_crumb:1` → QA-Files
#              listed; `files_up` → `/sdcard` listed; at This Device's root `files_up` is disabled (enabled=false in the
#              dump), dimmed ((123,123,123) ± 4, the glyph's ink) and a tap changes nothing (the same rows, no `list`
#              line in the slice from a MARK before the tap).
#   E12        (the roots line is the process's own, read from a MARK before the c6 that restarts the shell)
#              the row is E12's producer of `[files] roots: <n> (<names>)` and `[files] list <path>: <n> entries <ms>
#              ms`: both asserted in the row's slices.
#
# Changes on the device: QA-Files (files_up / files_down, which asserts both snapshots). No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/p18_a.sh"
keep_earlier_run E3
row_begin E3 "listing, sort and the location bar"
LC0="$(lc_mark)"
baseline_start
files_up || { row_end; exit 1; }
# The roots line is written once per process, at its start (FileVolumes, from ShellApp): the MARK for it is taken just
# before c6's force-stop, so every line after it is the new process's (run 1 took it after and read no roots line).
PMARK="$(ring_mark)"
c6; ensure_start

# ------------------------------------------------------------------------------------------------- listing
log "--- listing"
MARK="$(ring_mark)"
files_at "$QF"; D 01-qa; S 01-qa; nodes 01-qa > "$ROW_DIR/01-qa.nodes"
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -E '\[(files|motion)\]' > "$ROW_DIR/01-ring.txt"
assert_eq "QA-Files is open: the crumbs" "This Device|QA-Files" "$(X 01-qa files_crumb:0)|$(X 01-qa files_crumb:1)"
assert_eq "the sort line reads" "Sort by: Name" "$(X 01-qa files_sort)"
WANT_N="$(fx_order name | grep -c .)"
assert_contains "[files] list <path>: <n> entries <ms> ms (E12's producer)" "[files] list $QF: $WANT_N entries " "$SL"
record "the list line" "$(printf '%s\n' "$SL" | grep -o '\[files\] list .*' | tail -1)"
ROOTS="$(ring_since "$PMARK" | grep -o '\[files\] roots: .*' | tail -1)"
record "the roots line" "$ROOTS"
assert_eq "[files] roots: <n> (<names>) (E12's producer): one root, named" "yes" "$(printf '%s\n' "$ROOTS" | grep -qE '^\[files\] roots: 1 \([^)]+\)' && echo yes || echo no)"
# The pitch and the two lines, on the first page: consecutive icon tops, and the detail under its name.
PITCH="$(python3 - "$ROW_DIR/01-qa.xml" <<'PY'
import html, re, sys
x = open(sys.argv[1], encoding='utf-8', errors='replace').read()
tops, lines = [], []
for m in re.finditer(r'<node[^>]*>', x):
    s = m.group(0); rid = html.unescape(re.search(r'resource-id="([^"]*)"', s).group(1))
    b = [int(v) for v in re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s).groups()]
    if rid.startswith("files_icon:") and b[3] - b[1] == 120: tops.append(b[1])
    if rid.startswith("files_row:"): lines.append((rid[10:], "name", b))
    if rid.startswith("files_detail:"): lines.append((rid[13:], "detail", b))
gaps = sorted(set((b - a) / 3 for a, b in zip(tops, tops[1:])))
names = {n: b for n, k, b in lines if k == "name"}; details = {n: b for n, k, b in lines if k == "detail"}
under = all(details[n][1] > names[n][1] and details[n][0] == names[n][0] for n in names if n in details)
print("%d %s %s %d" % (len(tops), ",".join("%.1f" % g for g in gaps), "yes" if under else "no", len([n for n in names if n in details])))
PY
)"
set -- $PITCH
record "the first page: whole icons / the pitches between them (epx) / rows with both lines" "$1 / $2 / $4"
assert_ge "the first page shows at least six whole rows" 6 "$1"
for g in $(printf '%s' "$2" | tr ',' ' '); do assert_near "row pitch 64 ± 1 epx (r11/files.md 1.5.2)" 64 "$g" 1; done
assert_eq "each row has two lines: files_detail: under files_row:, at the same left" "yes" "$3"
list_all 02-name
assert_eq "QA-Files lists the table's entries, in Name order (exact; folders first, A → Z ignoring case)" "$(fx_order name | xargs)" "$(xargs < "$ROW_DIR/02-name.order")"
for n in a.txt b.bin sub img-0.png img-1.png img-2.png img-3.png img-4.png img-5.png qa-steps.mp4 03.mp3; do
  assert_contains "the listing holds $n" "|$n|" "|$(tr '\n' '|' < "$ROW_DIR/02-name.order")"
done
absent_in_list() { case "|$(tr '\n' '|' < "$ROW_DIR/02-name.order")" in *"|$1|"*) echo listed ;; *) echo absent ;; esac; }
assert_eq "the dot-file .hidden.txt is not listed (hidden files are off)" "absent" "$(absent_in_list .hidden.txt)"
while IFS= read -r n; do
  case "$n" in sub|hidden|recent) key="$n/" ;; *) key="$n" ;; esac
  assert_eq "files_detail:$n is the table's literal" "$(fx_detail "$key")" "$(detail_of 02-name "$n")"
done < <(fx_order name)
assert_eq "files_detail:sub is a date only" "yes" "$(detail_of 02-name sub | grep -qE '^[0-9]+/[0-9]+/[0-9]{4}$' && echo yes || echo no)"
# Icons: every row has its files_icon: node; a type icon and a thumbnail differ in width (recorded).
MISSING=""; for n in $(fx_order name); do grep -q "resource-id=\"files_icon:$n\"" "$ROW_DIR"/02-name-*.xml || MISSING="$MISSING $n"; done
assert_eq "every row has its files_icon: node (type icon or thumbnail)" "" "$MISSING"
icon_w() { local f; for f in "$ROW_DIR"/02-name-*.xml; do if grep -q "resource-id=\"files_icon:$1\"" "$f"; then python3 -c "import sys; b=[int(v) for v in sys.argv[1:]]; print('%.0fx%.0f' % ((b[2]-b[0])/3, (b[3]-b[1])/3))" $(bounds "$f" "files_icon:$1"); return; fi; done; }
record "icon boxes (epx): sub / a.txt / b.bin / img-0.png / qa-steps.mp4 / 03.mp3" "$(icon_w sub) / $(icon_w a.txt) / $(icon_w b.bin) / $(icon_w img-0.png) / $(icon_w qa-steps.mp4) / $(icon_w 03.mp3)"

# ------------------------------------------------------------------------------------------------- sort
log "--- sort"
sort_by() { # key name-of-dumps
  files_at "$QF"; D "$2-a"
  T "$2-a" files_sort 1.2; D "$2-fly"; S "$2-fly"
  FLY="$(ids_of "$2-fly" files_sort_item: | xargs)"
  T "$2-fly" "files_sort_item:$1" 1.5; D "$2-b"
}
sort_by date 03-date
assert_eq "the sort flyout offers exactly Name, Date, Size (no Type)" "name date size" "$FLY"
record "the flyout's texts" "$(grep -o 'text="[^"]\+"' "$ROW_DIR/03-date-fly.xml" | sed 's/text=//' | xargs | cut -c1-200)"
assert_eq "sort by date: the sort line reads" "Sort by: Date" "$(X 03-date-b files_sort)"
list_all 03-date-list
assert_eq "sort by date: the order the touch -d dates dictate (exact; folders first, newest first)" "$(fx_order date | xargs)" "$(xargs < "$ROW_DIR/03-date-list.order")"
sort_by size 04-size
assert_eq "sort by size: the sort line reads" "Sort by: Size" "$(X 04-size-b files_sort)"
list_all 04-size-list
assert_eq "sort by size: the order the sizes dictate (exact; folders first by name, largest first)" "$(fx_order size | xargs)" "$(xargs < "$ROW_DIR/04-size-list.order")"
sort_by name 05-name
assert_eq "sort by name again: the sort line reads" "Sort by: Name" "$(X 05-name-b files_sort)"
list_all 05-name-list
assert_eq "sort by name: the order the names dictate (exact)" "$(fx_order name | xargs)" "$(xargs < "$ROW_DIR/05-name-list.order")"
assert_ne "the three orders differ from each other (the fixtures tell the keys apart)" "$(xargs < "$ROW_DIR/03-date-list.order")" "$(xargs < "$ROW_DIR/04-size-list.order")"
assert_ne "…and date differs from name" "$(xargs < "$ROW_DIR/03-date-list.order")" "$(xargs < "$ROW_DIR/05-name-list.order")"

# ------------------------------------------------------------------------------------------------- the location bar
log "--- the location bar"
files_at "$QF"; D 06-qa
MARK="$(ring_mark)"
T 06-qa files_row:sub 1.5; D 07-sub; S 07-sub
assert_eq "open sub: the crumbs in order" "This Device|QA-Files|sub" "$(X 07-sub files_crumb:0)|$(X 07-sub files_crumb:1)|$(X 07-sub files_crumb:2)"
assert_eq "open sub: exactly three crumbs" "0 1 2" "$(ids_of 07-sub files_crumb: | xargs)"
assert_contains "open sub: [files] list …/QA-Files/sub" "[files] list $QF/sub: 0 entries" "$(ring_since "$MARK")"
MARK="$(ring_mark)"
T 07-sub files_crumb:1 1.5; D 08-crumb
assert_eq "tap files_crumb:1: QA-Files is listed (its crumbs, its first rows)" "This Device|QA-Files| $(fx_order name | head -3 | xargs)" "$(X 08-crumb files_crumb:0)|$(X 08-crumb files_crumb:1)|$(X 08-crumb files_crumb:2) $(ids_of 08-crumb files_row: | head -3 | xargs)"
assert_contains "tap files_crumb:1: [files] list …/QA-Files" "[files] list $QF: $WANT_N entries" "$(ring_since "$MARK")"
assert_eq "in QA-Files files_up is enabled" "true" "$(attr 08-crumb files_up enabled)"
MARK="$(ring_mark)"
T 08-crumb files_up 1.5; D 09-root; S 09-root
WANT_ROWS="$(q "ls -p /sdcard/" | python3 -c "
import sys
names = [l.rstrip('\n') for l in sys.stdin if l.strip()]
dirs = sorted([n[:-1] for n in names if n.endswith('/')], key=str.lower); files = sorted([n for n in names if not n.endswith('/')], key=str.lower)
print('\n'.join(dirs + files))")"
GOT_ROWS="$(ids_of 09-root files_row:)"; N="$(printf '%s\n' "$GOT_ROWS" | grep -c .)"
assert_eq "tap files_up: /sdcard is listed (one crumb, This Device)" "This Device|0" "$(X 09-root files_crumb:0)|$(ids_of 09-root files_crumb: | xargs)"
assert_eq "…its rows are the first $N of /sdcard in order (QA-Files among the folders)" "$(printf '%s\n' "$WANT_ROWS" | head -n "$N" | xargs)" "$(printf '%s\n' "$GOT_ROWS" | xargs)"
assert_contains "…[files] list /storage/emulated/0" "[files] list $SD: $(printf '%s\n' "$WANT_ROWS" | grep -c .) entries" "$(ring_since "$MARK")"
assert_eq "at This Device's root files_up is disabled (the dump's enabled)" "false" "$(attr 09-root files_up enabled)"
set -- $(B 09-root files_up)
INK="$(ink 09-root "$1" "$2" "$3" "$4")"
record "the ↑ glyph's ink at the root (px box, colour)" "$INK"
assert_rgb "…and dimmed: the glyph's ink (123,123,123) ± 4" "123,123,123" "${INK##* }" 4
MARK="$(ring_mark)"
T 09-root files_up 1.5; D 10-root-again
assert_eq "a tap on the disabled files_up changes nothing: the same crumb and rows" "$(X 09-root files_crumb:0) $(ids_of 09-root files_row: | xargs)" "$(X 10-root-again files_crumb:0) $(ids_of 10-root-again files_row: | xargs)"
SL="$(ring_since "$MARK")"
if [ -n "$SL" ]; then absent_in "…and no folder was read (no [files] list line from the MARK before the tap)" "[files] list " "$SL"
else assert_eq "…and no folder was read: the ring holds no line at all since the MARK before the tap" "" "$(printf '%s' "$SL" | grep -F '[files] list ')"; fi

# ------------------------------------------------------------------------------------------------- restore
log "--- restore"
assert_eq "no AndroidRuntime line names the shell since the row began" "0" "$(crash_since "$LC0")"
c6; ensure_start
files_down
end_state
row_end
