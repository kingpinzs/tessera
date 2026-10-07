#!/usr/bin/env bash
# Phase 18 E8 — search. The doc's clauses (with the 2026-10-06 entry, Q-18-5 (a)) → this driver's legs.
#
#   pace       `search_pace_set 1000` FIRST (the debug-only pref qa_files_search_eps: entries examined per second), so the
#              walk over the row's own 10,000-entry folder lasts about 10 s; every search of the row logs `[files] qa
#              search pace 1000` (asserted in each slice — a paced run can never be read as an unpaced one).
#   "b"        from the QA-Files root, the term typed into `files_search_box`: the hits are the files and folders whose
#              NAME contains the term (Q-18-5's entry; BUILD-NOTES 6) — exactly `b.bin`, `sub/b.bin` and `sub`, each
#              with its path relative to the searched folder as its detail (BUILD-NOTES, UI builder 5). `sub/b.bin` is
#              the row's own: a copy of `b.bin` made with `adb shell cp` after `files_up` (the table's `sub` is empty; the
#              doc's older chain got it from E4). The sort line reads "Sort by: Relevance" (r3 V7). The order on screen is
#              RECORDED (the doc names no order).
#   no match   a term nothing matches → `files_search_empty` (its text RECORDED) and no row.
#   running    in `/sdcard/QA-Big` (files_up tenk: f1 … f10000) the term "f1" → while the walk runs the dump (gdump: the
#              page does not idle) holds `files_search_progress` and `files_search_cancel`; tap Cancel → `[files] search
#              cancelled`, both nodes gone, the hits found so far stay, the term still in the box, and the walk was cut
#              short: from the `qa search pace` line to the `search cancelled` line is under the 10 s a whole walk takes
#              at this pace (RECORDED, and asserted < 9000 ms).
#
# The row is E12's producer of `[files] search cancelled` and `[files] qa search pace <eps>`.
# Changes on the device: QA-Files and QA-Big (files_up tenk / files_down — its proven rm of the 10,000 files), the search
# pace pref (cleared by files_down; asserted gone at the end). No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/p18_a.sh"
keep_earlier_run E8
row_begin E8 "search: the hits, the empty line, a running walk cancelled (paced at 1000 entries/s, Q-18-5)"
LC0="$(lc_mark)"
PACE_LINE="[files] qa search pace 1000"
baseline_start
files_up tenk || { row_end; exit 1; }
adb shell cp "$QA_FILES/b.bin" "$QA_FILES/sub/b.bin"
assert_eq "the row's own sub/b.bin (a copy of b.bin): same md5" "$(fx_md5 b.bin)" "$(q "md5sum $QA_FILES/sub/b.bin" | cut -d' ' -f1)"
search_pace_set 1000
ensure_start
# This row types: the pace write above force-stopped the shell, which deselects the shell's keyboard (p18.sh
# ime_baseline). It is selected again here, so the search box is typed into with the phase baseline's keyboard up —
# the earlier run (kept, build 87f6eac1) typed with LatinIME selected.
ime_baseline
assert_eq "the keyboard while the row types is the baseline's (settings get secure default_input_method)" "$IME_BASELINE" "$(ime_now)"

# The hits of a dump, in order: "name=detail" per row (two hits may share a name; the detail tells them apart).
hits() { # dump
  python3 - "$ROW_DIR/$1.xml" <<'PY'
import html, re, sys
x = open(sys.argv[1], encoding='utf-8', errors='replace').read()
names, details = [], []
for m in re.finditer(r'<node[^>]*>', x):
    s = m.group(0); rid = html.unescape(re.search(r'resource-id="([^"]*)"', s).group(1)); t = html.unescape(re.search(r' text="([^"]*)"', s).group(1))
    if rid.startswith("files_row:"): names.append(rid[10:])
    if rid.startswith("files_detail:"): details.append(t)
print("\n".join("%s=%s" % (n, d) for n, d in zip(names, details)))
PY
}
search_in() { # folder term prefix — the folder opened by path, Search tapped, the term typed; MARK = before the typing
  files_at "$1"; D "$3-a"
  T "$3-a" files_bar:search 1.2; D "$3-box"
  assert_eq "$3: the search box is shown (files_search_box), empty" "yes|" "$(H "$3-box" files_search_box)|$(X "$3-box" files_search_box)"
  T "$3-box" files_search_box 0.6
  MARK="$(ring_mark)"
  adb shell input text "$2"
}

# ------------------------------------------------------------------------------------------------- "b"
log "--- \"b\" from the QA-Files root"
search_in "$QF" b 01
sleep 2.5; D 01-hits; S 01-hits
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/01-ring.txt"
assert_eq "the term is in files_search_box" "b" "$(X 01-hits files_search_box)"
assert_eq "the sort line reads" "Sort by: Relevance" "$(X 01-hits files_sort)"
record "the hits in the order shown (name=path)" "$(hits 01-hits | xargs)"
assert_eq "the hits are exactly b.bin, sub/b.bin and sub, each with its path (sorted)" "b.bin=b.bin b.bin=sub/b.bin sub=sub" "$(hits 01-hits | LC_ALL=C sort | xargs)"
assert_eq "no progress line and no Cancel are left when the walk has ended" "no no" "$(H 01-hits files_search_progress) $(H 01-hits files_search_cancel)"
assert_contains "the slice holds $PACE_LINE" "$PACE_LINE" "$SL"

# ------------------------------------------------------------------------------------------------- no match
log "--- a term with no match"
search_in "$QF" zzqx 02
sleep 2.5; D 02-none; S 02-none
SL="$(ring_since "$MARK")"
assert_eq "no match: files_search_empty is shown" "yes" "$(H 02-none files_search_empty)"
record "no match: the empty line's text" "$(X 02-none files_search_empty)"
assert_ne "…with a text" "" "$(X 02-none files_search_empty)"
assert_eq "…and no row" "" "$(hits 02-none | xargs)"
assert_eq "…the sort line reads" "Sort by: Relevance" "$(X 02-none files_sort)"
assert_contains "…the slice holds $PACE_LINE" "$PACE_LINE" "$SL"

# ------------------------------------------------------------------------------------------------- a running walk
log "--- a walk over 10,000 entries, cancelled while it runs"
search_in "$SD/QA-Big" f1 03
PL="$(await_line "$MARK" "$PACE_LINE" 20)"
assert_contains "QA-Big: the walk started paced ($PACE_LINE)" "$PACE_LINE" "$PL"
sleep 1.5
G 03-running; S 03-running
assert_eq "while the walk runs: files_search_progress and files_search_cancel are shown" "yes yes" "$(H 03-running files_search_progress) $(H 03-running files_search_cancel)"
record "the progress line's text / hits so far on screen" "$(X 03-running files_search_progress) / $(hits 03-running | grep -c .)"
assert_eq "…the term is in the box, the sort line reads Relevance" "f1|Sort by: Relevance" "$(X 03-running files_search_box)|$(X 03-running files_sort)"
absent_in "…and the walk has not been cancelled yet" "[files] search cancelled" "$(ring_since "$MARK")"
T 03-running files_search_cancel 1
CL="$(await_line "$MARK" "[files] search cancelled" 20)"
G 04-cancelled; S 04-cancelled
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/04-ring.txt"
assert_contains "tap files_search_cancel: [files] search cancelled" "[files] search cancelled" "$CL"
assert_eq "…the progress line and Cancel are gone" "no no" "$(H 04-cancelled files_search_progress) $(H 04-cancelled files_search_cancel)"
assert_eq "…the term stays in the box" "f1" "$(X 04-cancelled files_search_box)"
assert_ge "…the hits found so far stay on screen" 1 "$(hits 04-cancelled | grep -c .)"
wall() { printf '%s\n' "$1" | sed -n 's/.*wall=\([0-9]*\).*/\1/p' | head -1; }
RAN="$(( $(wall "$CL") - $(wall "$PL") ))"
record "from the pace line to the cancelled line (ms; a whole walk of 10,000 entries at 1000/s is about 10,000)" "$RAN"
assert_le "…the walk was cut short: it ran under 9000 ms" 9000 "$RAN"
assert_eq "…exactly one search cancelled line" "1" "$(printf '%s\n' "$SL" | grep -c 'search cancelled')"

# ------------------------------------------------------------------------------------------------- restore
log "--- restore"
assert_eq "the keyboard is still the baseline's after the last typing (nothing in the row's legs deselected it)" "$IME_BASELINE" "$(ime_now)"
record "the keyboard's window while the walk ran / after the cancel (dumpsys input_method mInputShown at this point)" "$(adb shell dumpsys input_method | tr -d '\r' | grep -o 'mInputShown=[a-z]*' | head -1)"
assert_eq "no AndroidRuntime line names the shell since the row began" "0" "$(crash_since "$LC0")"
c6; ensure_start
files_down
end_state
row_end
