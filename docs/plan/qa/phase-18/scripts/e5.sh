#!/usr/bin/env bash
# Phase 18 E5 — negatives: Android/data and Android/obb. The row is worded for either outcome of build task 0 (b); this
# driver reads qa/phase-18/BUILDSTART/records.tsv's `android_data_as_app` and runs the matching form (r3 D13 / V9).
#
#   the record  `android_data_as_app` = "null (File.list() = null …) -> E5's SECOND form". The driver asserts the record
#               says so and holds the second form; were the record ever the other outcome the row FAILS at that
#               assertion (the first form's steps would have to be written against a device that shows it — none does).
#   second form in `/sdcard/Android` the `data` entry itself carries `files_unreadable` (the node on data's own row, in
#               the place of its detail line) with the text "Android doesn't let apps see other apps' folders here";
#               tapping it shows the reason (`files_unreadable_reason`) and opens nothing (the crumbs still end at
#               "Android", no `[files] list …/Android/data` line); `[files] unreadable <path>` in the slice from a MARK
#               before the folder is opened AND in the slice from a MARK before the tap; `Android/obb` the same; the
#               third entry (`media`) is an ordinary row with a date (the control: not everything is unreadable).
#   own folder  "the shell's own `/sdcard/Android/data/app.tileshell` is readable when it exists": RECORDED whether it
#               exists (`ls -d` as the shell user; task 0 (b): it did not) — when it does, the leg opens it by path and
#               asserts a list line, when it does not the clause has nothing to assert.
#   no crash    no AndroidRuntime line names app.tileshell since the row began.
#
# Changes on the device: none (no fixtures; nothing is written). No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/p18_a.sh"
keep_earlier_run E5
row_begin E5 "negatives: Android/data and Android/obb (the second form, per build task 0 (b)'s record)"
LC0="$(lc_mark)"
TEXT="Android doesn't let apps see other apps' folders here"
baseline_start

REC="$(awk -F'\t' '$1 == "android_data_as_app" { print $2 }' "$P18/BUILDSTART/records.tsv")"
record "BUILDSTART/records.tsv android_data_as_app" "${REC:0:150}"
assert_eq "the record names the SECOND form (the list comes back null)" "null" "${REC%% *}"
[ "${REC%% *}" = null ] || { row_end; exit 1; }
# The same fact on this build, as the shell user sees the folder the app cannot list (a record, not the app's view).
record "ls /sdcard/Android (shell user)" "$(q 'ls /sdcard/Android/' | xargs)"
record "ls /sdcard/Android/data (shell user)" "$(q 'ls /sdcard/Android/data/' | xargs)"

# The files_unreadable node that sits on a named row: its top lies between that row's name and the next row's icon.
unreadable_on() { # dump name -> "text|l t r b" or empty
  python3 - "$ROW_DIR/$1.xml" "$2" <<'PY'
import html, re, sys
x = open(sys.argv[1], encoding='utf-8', errors='replace').read()
name, cands = None, []
for m in re.finditer(r'<node[^>]*>', x):
    s = m.group(0); rid = html.unescape(re.search(r'resource-id="([^"]*)"', s).group(1))
    b = [int(v) for v in re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s).groups()]
    if rid == "files_row:" + sys.argv[2]: name = b
    if rid == "files_unreadable": cands.append((b, html.unescape(re.search(r' text="([^"]*)"', s).group(1))))
if name:
    for b, t in cands:
        if name[3] <= b[1] + 6 and b[1] - name[3] < 60 and b[0] == name[0]:   # under the name, inside its 64-epx row, same left
            print("%s|%d %d %d %d" % (t, b[0], b[1], b[2], b[3])); break
PY
}
tap_box() { set -- $1; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; }

c6; ensure_start
log "--- /sdcard/Android"
MARK="$(ring_mark)"
files_at "$SD/Android"; D 01-android; S 01-android; nodes 01-android > "$ROW_DIR/01-android.nodes"
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/01-ring.txt"
assert_eq "/sdcard/Android is open: the crumbs" "This Device|Android" "$(X 01-android files_crumb:0)|$(X 01-android files_crumb:1)"
assert_eq "it lists exactly data, media, obb" "data media obb" "$(ids_of 01-android files_row: | xargs)"
for d in data obb; do
  U="$(unreadable_on 01-android "$d")"
  assert_eq "the $d entry itself carries files_unreadable with the text" "$TEXT" "${U%%|*}"
  assert_eq "…in the place of its detail line (no files_detail:$d node)" "no" "$(H 01-android "files_detail:$d")"
  assert_contains "[files] unreadable …/Android/$d in the slice from the MARK before the folder opened" "[files] unreadable $SD/Android/$d" "$SL"
done
assert_eq "the control: media is an ordinary row (a files_detail: date, no files_unreadable on it)" "yes|" "$(X 01-android files_detail:media | grep -qE '^[0-9]+/[0-9]+/[0-9]{4}$' && echo yes || echo no)|$(unreadable_on 01-android media)"
assert_eq "exactly two files_unreadable nodes on the page" "2" "$(grep -o 'resource-id="files_unreadable"' "$ROW_DIR/01-android.xml" | wc -l | xargs)"

for d in data obb; do
  log "--- tap $d"
  files_at "$SD/Android"; D "02-$d-before"
  U="$(unreadable_on "02-$d-before" "$d")"
  MARK="$(ring_mark)"
  tap_box "${U##*|}"; D "03-$d-tap"; S "03-$d-tap"
  SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/03-$d-ring.txt"
  REASON="$(X "03-$d-tap" files_unreadable_reason)"
  record "tap $d: the reason shown" "$REASON"
  assert_eq "tap $d: the reason is shown (files_unreadable_reason)" "yes" "$(H "03-$d-tap" files_unreadable_reason)"
  assert_contains "tap $d: the reason holds the entry's text" "$TEXT" "$REASON"
  assert_eq "tap $d: nothing opened — the crumbs still end at Android, FilesActivity on top" "This Device|Android||$FILES_ACTIVITY" "$(X "03-$d-tap" files_crumb:0)|$(X "03-$d-tap" files_crumb:1)|$(X "03-$d-tap" files_crumb:2)|$(top_activity)"
  assert_contains "tap $d: [files] unreadable …/Android/$d in the slice from the MARK before the tap" "[files] unreadable $SD/Android/$d" "$SL"
  absent_in "tap $d: no folder was read (no [files] list line in that slice)" "[files] list " "$SL"
  # The same through the row's NAME (the whole row is one target): tapped on files_row:<d>, from a page opened again —
  # the reason is a notice drawn at 86–144 epx, over the sort line and the first row's top, and a tap ON it only closes
  # it (run 1 tapped data's name through the notice and read no reason: a driver fault, kept as E5-run1).
  files_at "$SD/Android"; D "03-$d-again"
  assert_eq "the page opened again shows no reason" "no" "$(H "03-$d-again" files_unreadable_reason)"
  MARK="$(ring_mark)"
  T "03-$d-again" "files_row:$d" 1.5; D "04-$d-name"
  assert_eq "tap $d's name: still in Android, the reason shown" "Android|yes" "$(X "04-$d-name" files_crumb:1)|$(H "04-$d-name" files_unreadable_reason)"
  absent_in "tap $d's name: no [files] list line" "[files] list " "$(ring_since "$MARK")"
done

log "--- the path opened directly, and the shell's own folder"
MARK="$(ring_mark)"
files_at "$SD/Android/data"; D 05-direct; S 05-direct
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/05-ring.txt"
record "Files started with path=…/Android/data: its [files] lines / the crumbs / files_error" "$(sed 's/.*\[files\] //' "$ROW_DIR/05-ring.txt" | tr '\n' ';') / $(X 05-direct files_crumb:0)>$(X 05-direct files_crumb:1)>$(X 05-direct files_crumb:2) / $(X 05-direct files_error)$(X 05-direct files_unreadable_reason)"
assert_contains "…/Android/data by path: [files] unreadable (never a listing of it)" "[files] unreadable $SD/Android/data" "$SL"
absent_in "…/Android/data by path: no [files] list …/Android/data line" "[files] list $SD/Android/data" "$SL"
OWN="$(q 'ls -d /sdcard/Android/data/app.tileshell 2>/dev/null')"
record "the shell's own /sdcard/Android/data/app.tileshell exists (ls -d, shell user)" "${OWN:-no}"
if [ -n "$OWN" ]; then
  MARK="$(ring_mark)"
  files_at "$SD/Android/data/app.tileshell"; D 06-own
  assert_contains "the shell's own Android/data folder is readable: [files] list …/Android/data/app.tileshell" "[files] list $SD/Android/data/app.tileshell: " "$(ring_since "$MARK")"
fi

log "--- restore"
assert_eq "no crash: no AndroidRuntime line names the shell since the row began" "0" "$(crash_since "$LC0")"
c6; ensure_start
end_state
row_end
