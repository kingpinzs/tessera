#!/usr/bin/env bash
# E14 "Empty": pm clear → provision.sh (its phase-18 appop line's first run) → Start → Files: the Recent page's empty state.
. "$(dirname "$0")/t4.sh"; take_device_lock; leg b-e14
adb logcat -c
rings_save
adb shell pm clear app.tileshell | tee "$ROW_DIR/pm-clear.txt"
echo "appop after pm clear: $(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE')" | tee -a "$ROW_DIR/pm-clear.txt"
bash "$T4/../../phase-03/scripts/provision.sh" > "$ROW_DIR/provision.out" 2>&1; echo $? > "$ROW_DIR/provision.rc"
assert_eq "provision.sh exit code" "0" "$(cat "$ROW_DIR/provision.rc")"
assert_contains "provision.sh: its all-files line" "all-files access: MANAGE_EXTERNAL_STORAGE: allow" "$(cat "$ROW_DIR/provision.out")"
grep -n "all-files access\|OK: a tap\|FAIL" "$ROW_DIR/provision.out"
ensure_start; D 00-start; assert_eq "after provision: Start, no wizard page" "no" "$(H 00-start wizard_page)"
ROW_MARK="$(ring_mark)"
# ---- cold start from the tile-less launch: Recent with the pane open
M=$(ring_mark); files_open; D 01-cold; S 01-cold
assert_eq "cold start: the pane is open" "yes" "$(H 01-cold files_pane)"
assert_eq "cold start: top" "$FILES_ACTIVITY" "$(top_activity)"
SL="$(ring_since $M)"; echo "$SL" | grep -F "[files]" | tee "$ROW_DIR/01-ring.txt"
assert_contains "empty: [files] recent: 0" "[files] recent: 0" "$SL"
# ---- the page itself, pane closed (the Recent shortcut's form)
files_open --es page recent; D 02-empty; S 02-empty
assert_eq "empty: files_recent_empty text" "You haven't opened any files recently." "$(X 02-empty files_recent_empty)"
assert_eq "empty: no files_recent_row node" "0" "$(grep -c 'files_recent_row:' "$ROW_DIR/02-empty.xml")"
assert_eq "empty: no files_sort node" "no" "$(H 02-empty files_sort)"
assert_eq "empty: no New folder button" "no" "$(H 02-empty files_bar:new_folder)"
en() { grep -o "<node[^>]*resource-id=\"$2\"[^>]*>" "$ROW_DIR/$1.xml" | grep -o 'enabled="[a-z]*"' | head -1; }
for b in select view search; do assert_eq "empty: files_bar:$b is dim" 'enabled="false"' "$(en 02-empty files_bar:$b)"; done
record "empty: files_bar:more (•••)" "$(en 02-empty files_bar:more) — the overflow keeps Refresh and Settings"
echo "bar: $(ids 02-empty | tr ' ' '\n' | grep -E 'files_bar|files_more' | xargs)"
INK="$(python3 "$(dirname "$0")/ink.py" "$ROW_DIR/02-empty.png" $(B 02-empty files_recent_empty))"; echo "ink: $INK  node: $(B 02-empty files_recent_empty)"
set -- $INK
assert_within "empty: the line's left edge, epx (14.5 ± 2)" "14.5" "$(python3 -c "print('%.1f' % ($1/3))")" 2
assert_within "empty: the cap top, epx (88.8 + (STATUS_EPX 28 - 24) = 92.8 ± 2)" "92.8" "$(python3 -c "print('%.1f' % ($2/3))")" 2
assert_eq "empty: the text's colour $5 = 160,160,160 ± 4" "yes" "$(near "$5" 160,160,160 4)"
adb shell input keyevent KEYCODE_BACK; sleep 1
leg_end
