#!/usr/bin/env bash
# Phase 18 E1 — grant and checklist. The doc's clauses → this driver's legs.
#
#   granted    appop MANAGE_EXTERNAL_STORAGE allow → the Setup checklist's Files row is green (`checklist:files:granted`);
#              `c6`, a cold start of Files → `files_pane` present, `files_pane:recent` the selected row, its fill
#              0.6 · accent + 0.4 · (23,23,23) ± 4 (the accent is the theme pref's, or the out-of-box one when no pref is
#              set — Palette.DEFAULT_ACCENT), the Recent page behind it (`files_crumb:0` "Recent"), `[files] access=granted`;
#              tap `files_pane:device` → the pane closes, `files_crumb:0` reads "This Device", and the rows on screen are
#              the first rows of `/sdcard` in the listing's order (folders first, name A → Z ignoring case; `ls -p`).
#   revoked    `appops set … default` (the pid before and after RECORDED — r3 V10), a FRESH MARK, a relaunch → the
#              checklist row is red (`checklist:files:missing`), Files shows `files_ungranted` "Files can't see this
#              phone's storage" with `files_grant_link`, `[files] access=denied` in the slice from that MARK, and the
#              link STARTS the app's own all-files page: the logcat line `START u0 {act=…MANAGE_APP_ALL_FILES_ACCESS_
#              PERMISSION … cmp=com.android.settings/.Settings$AppManageExternalStorageActivity}` (Q-18-4 (a); the
#              Decisions entry below it: the START line, not top-resumed — Settings forwards to .spa.SpaActivity), the
#              resumed package com.android.settings. logcat redacts the line's data to `dat=package:` (BUILD-NOTES,
#              open-with builder 4), so the package is proved by the page's own texts (the shell's label, then the
#              all-files switch).
#              The checklist row's own tap is asserted the same way (BUILD-NOTES, task 1: "one device check that the
#              link, the checklist row and the wizard step start …" — the wizard step is E15's).
#   restore    appop allow (RV12), `[files] access=granted` after a relaunch, no crash line since the row began.
#
# Changes on the device: the appop (restored). No fixtures, no wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/p18_a.sh"
keep_earlier_run E1
row_begin E1 "grant and checklist: the green row, the cold start on Recent, the revoke, the link"
trap 'adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow' EXIT
LC0="$(lc_mark)"
WANT='com.android.settings/.Settings$AppManageExternalStorageActivity'
OWN_PAGE='text=Tessera text=0.1.0 text=Allow access to manage all files'
appop() { q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE' | cut -d';' -f1; }
checklist() { # dump state — the checklist page, scrolled to the Files row
  adb shell am start -W -n app.tileshell/.settings.SettingsActivity --activity-clear-task --es page CHECKLIST >/dev/null 2>&1; sleep 3
  scroll_to_node "$ROW_DIR/$1.xml" "checklist:files:$2" 10 >/dev/null || true
  S "$1"
}
baseline_start

# ------------------------------------------------------------------------------------------------- granted
log "--- granted"
adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow
assert_eq "appops set … allow: the appop reads allow" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop)"
checklist 01-checklist granted
assert_eq "the checklist's Files row is green (checklist:files:granted)" "yes no" "$(H 01-checklist checklist:files:granted) $(H 01-checklist checklist:files:missing)"
record "the checklist row's texts" "$(python3 - "$ROW_DIR/01-checklist.xml" <<'PY'
import html, re, sys
x = open(sys.argv[1], encoding='utf-8', errors='replace').read()
i = x.find('checklist:files:')
print(" | ".join(html.unescape(t) for t in re.findall(r'text="([^"]+)"', x[i:i + 1500])[:2]))
PY
)"
c6; ensure_start
MARK="$(ring_mark)"
files_open; D 02-cold; S 02-cold; nodes 02-cold > "$ROW_DIR/02-cold.nodes"
SL="$(ring_since "$MARK")"
assert_eq "cold start: FilesActivity is on top" "$FILES_ACTIVITY" "$(top_activity)"
assert_eq "cold start: files_pane is present" "yes" "$(H 02-cold files_pane)"
assert_eq "cold start: files_pane:recent is the selected row (and device / bin are not)" "true false false" "$(attr 02-cold files_pane:recent selected) $(attr 02-cold files_pane:device selected) $(attr 02-cold files_pane:bin selected)"
ACC_PREF="$(_pace_file | tr -d '\r' | sed -n 's/.*name="accent"[^0-9-]*\(-\?[0-9]*\).*/\1/p' | head -1)"
ACC="$(python3 -c "
import sys
v = int(sys.argv[1]) & 0xFFFFFF if sys.argv[1] else 0x0078D7
print('%d,%d,%d' % (v >> 16, (v >> 8) & 255, v & 255))" "$ACC_PREF")"
WANT_FILL="$(python3 -c "
import sys
a = [int(v) for v in sys.argv[1].split(',')]
print(','.join(str(round(0.6 * c + 0.4 * 23)) for c in a))" "$ACC")"
record "the accent (the theme pref, or Palette.DEFAULT_ACCENT with no pref: [$ACC_PREF])" "$ACC"
# The row's fill, read at x 200 epx on the row's centre line: right of the label (it ends near 105 epx), inside the pane.
set -- $(B 02-cold files_pane:recent)
GOT_FILL="$(px 02-cold 600 $(( ($2 + $4) / 2 )))"
assert_rgb "cold start: the selected row's fill = 0.6 · accent + 0.4 · (23,23,23) ± 4" "$WANT_FILL" "$GOT_FILL" 4
assert_eq "cold start: the Recent page is behind the pane (files_crumb:0)" "Recent" "$(X 02-cold files_crumb:0)"
assert_contains "cold start: [files] access=granted" "[files] access=granted" "$SL"
MARK="$(ring_mark)"
T 02-cold files_pane:device 1.5; D 03-device; S 03-device
assert_eq "tap files_pane:device: the pane closes" "no" "$(H 03-device files_pane)"
assert_eq "…files_crumb:0 reads This Device" "This Device" "$(X 03-device files_crumb:0)"
# /sdcard as the listing orders it: folders first, then files, each A → Z ignoring case, dot-names hidden.
WANT_ROWS="$(q "ls -p /sdcard/" | python3 -c "
import sys
names = [l.rstrip('\n') for l in sys.stdin if l.strip()]
dirs = sorted([n[:-1] for n in names if n.endswith('/')], key=str.lower); files = sorted([n for n in names if not n.endswith('/')], key=str.lower)
print('\n'.join(dirs + files))")"
GOT_ROWS="$(ids_of 03-device files_row:)"
N="$(printf '%s\n' "$GOT_ROWS" | grep -c .)"
assert_ge "…the page lists rows (files_row: nodes on screen)" 5 "$N"
assert_eq "…and they are the first $N entries of /sdcard, in order (ls -p: folders first, A → Z)" "$(printf '%s\n' "$WANT_ROWS" | head -n "$N" | xargs)" "$(printf '%s\n' "$GOT_ROWS" | xargs)"
assert_contains "…[files] list /storage/emulated/0: <n> entries" "[files] list /storage/emulated/0: $(printf '%s\n' "$WANT_ROWS" | grep -c .) entries" "$(ring_since "$MARK")"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ------------------------------------------------------------------------------------------------- revoked
log "--- revoked"
rings_save
PID0="$(q 'pidof app.tileshell' | xargs)"
adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE default; sleep 2
PID1="$(q 'pidof app.tileshell' | xargs)"
record "the shell's pid before / 2 s after appops set … default (r3 V10)" "[$PID0] / [$PID1]"
assert_eq "appops set … default: the appop reads default" "MANAGE_EXTERNAL_STORAGE: default" "$(appop)"
ensure_start
MARK="$(ring_mark)"          # the fresh MARK (the floor, 4): nothing before it is asserted on
files_open; D 04-ungranted; S 04-ungranted
SL="$(ring_since "$MARK")"
assert_eq "revoked: files_ungranted reads" "Files can't see this phone's storage" "$(X 04-ungranted files_ungranted)"
assert_eq "revoked: files_grant_link is present" "yes" "$(H 04-ungranted files_grant_link)"
record "revoked: the link's text" "$(X 04-ungranted files_grant_link)"
assert_contains "revoked: [files] access=denied in the slice from the fresh MARK" "[files] access=denied" "$SL"
absent_in "revoked: no [files] access=granted in that slice" "[files] access=granted" "$SL"
absent_in "revoked: nothing was listed" "[files] list " "$SL"
LC="$(lc_mark)"; sleep 1.1
T 04-ungranted files_grant_link 3; S 05-link
starts_since "$LC" | grep 'com.android.settings' > "$ROW_DIR/05-start.txt"
L="$(head -1 "$ROW_DIR/05-start.txt")"
note "the link's START lines: $(tr '\n' ';' < "$ROW_DIR/05-start.txt")"
assert_contains "the link: the START line names the per-app page (Q-18-4 (a))" "cmp=$WANT" "$L"
assert_contains "the link: …with the per-app action" "act=android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION" "$L"
assert_contains "the link: …and a package: URI (logcat redacts the rest)" "dat=package:" "$L"
record "the link: what Settings then forwards to" "$(sed -n 2p "$ROW_DIR/05-start.txt" | grep -o 'cmp=[^ }]*')"
dump_ui "$ROW_DIR/05-settings.xml"
TEXTS="$(grep -o 'text="[^"]\+"' "$ROW_DIR/05-settings.xml" | xargs)"
assert_contains "the link: the page is the shell's OWN all-files page (its label, version, the switch)" "$OWN_PAGE" "$TEXTS"
assert_eq "the link: the resumed package is Settings" "com.android.settings" "$(top_activity | cut -d/ -f1)"
record "the link: top resumed" "$(top_activity)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; adb shell input keyevent KEYCODE_HOME; sleep 1
MARK="$(ring_mark)"
checklist 06-checklist missing
assert_eq "revoked: the checklist's Files row is red (checklist:files:missing)" "yes no" "$(H 06-checklist checklist:files:missing) $(H 06-checklist checklist:files:granted)"
record "revoked: the [checklist] line's files= field" "$(ring_since "$MARK" | grep -F '[checklist]' | tail -1 | grep -o 'files=[A-Z]*')"
LC="$(lc_mark)"; sleep 1.1
T 06-checklist checklist:files:missing 3; S 07-row
starts_since "$LC" | grep 'com.android.settings' > "$ROW_DIR/07-start.txt"
L="$(head -1 "$ROW_DIR/07-start.txt")"
assert_contains "the checklist row's tap: the START line names the per-app page" "cmp=$WANT" "$L"
dump_ui "$ROW_DIR/07-settings.xml"
assert_contains "the checklist row's tap: the shell's OWN all-files page" "$OWN_PAGE" "$(grep -o 'text="[^"]\+"' "$ROW_DIR/07-settings.xml" | xargs)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5

# ------------------------------------------------------------------------------------------------- restore
log "--- restore"
adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow; sleep 1
assert_eq "restore: the appop reads allow (RV12)" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop)"
c6; ensure_start
MARK="$(ring_mark)"
files_open; D 08-granted
assert_contains "restore: [files] access=granted" "[files] access=granted" "$(ring_since "$MARK")"
assert_eq "restore: no ungranted page" "no" "$(H 08-granted files_ungranted)"
assert_eq "no AndroidRuntime line names the shell since the row began" "0" "$(crash_since "$LC0")"
c6; ensure_start
trap - EXIT
end_state
row_end
