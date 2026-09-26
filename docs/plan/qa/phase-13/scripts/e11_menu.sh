#!/usr/bin/env bash
# E11, phase 03 E15's reminder-menu sub-steps (geometry and selection), with E15's own numbers, on this build. E15's
# driver makes its reminders by speaking, and on both its runs (the pre-phase-13 apk a437acd5 and this one) that setup
# made no reminder row, so its menu steps never ran (qa/phase-03/E15*). Here the rows come from phase 13's typed
# fixture (reminders_fixture.sh, the product path E3 uses) and E15's menu clauses run as E15 words them
# (qa/phase-03/scripts/e15.sh, "the long-press menu (R7 3.6)"). The menu's fill is E3's.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$(dirname "$0")/reminders_fixture.sh"

row_begin E11_MENU "phase 03 E15's reminder-menu geometry and selection, with E15's numbers"
PX=3.0
span() { python3 -c "print(($2 - $1) / $PX)"; }
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
show_start 6
# Three rows, and the LOWEST is held: the menu opens above the touch point, and over a row near the top of the page
# its top lies above the window (E7's note), which would clip the height E15 measures.
for t in 9 10 11; do make_typed_reminder "remind me to check the QA13 menu $t list tomorrow at $t am"; done
open_reminders
page="$ROW_DIR/reminders.xml"
ID="$(python3 - "$page" <<'PY'
import re, sys
xml = open(sys.argv[1]).read(); best = None
for m in re.finditer(r'<node[^>]*>', xml):
    n = m.group(0)
    rid = re.search(r'resource-id="reminder_row:([^"]+)"', n); b = re.search(r'bounds="\[\d+,(\d+)\]', n)
    if rid and b and (best is None or int(b.group(1)) > best[0]): best = (int(b.group(1)), rid.group(1))
print(best[1] if best else "")
PY
)"
assert_ne "a reminder row to hold (the lowest)" "" "$ID"
set -- $(bounds "$page" "reminder_row:$ID")
cx=$(( ($1 + $3) / 2 )); cy=$(( ($2 + $4) / 2 ))
note "row [$1 $2 $3 $4], touch ($cx,$cy)"
# E15's own long press: 900 ms at the row's centre
adb shell input swipe $cx $cy $cx $cy 900
sleep 2
dump_ui "$ROW_DIR/menu.xml"
screencap "$ROW_DIR/menu.png"
assert_eq "R7 3.6.1 a long press opens the menu" "yes" "$(has_node "$ROW_DIR/menu.xml" reminder_menu)"
assert_eq "with Complete" "yes" "$(has_node "$ROW_DIR/menu.xml" reminder_menu_complete)"
assert_eq "and Delete" "yes" "$(has_node "$ROW_DIR/menu.xml" reminder_menu_delete)"
mb="$(bounds "$ROW_DIR/menu.xml" reminder_menu)"
note "menu [$mb]"
set -- $mb
assert_eq "precondition: the whole menu is on screen (its top below the window's top)" yes "$([ "$2" -gt 0 ] && echo yes || echo no)"
assert_within "R7 3.6.2 the menu is 243.3 epx wide" 243.3 "$(span $1 $3)" 1.4
assert_within "R7 3.6.2 and 107.0 epx tall" 107.0 "$(span $2 $4)" 1.4
assert_within "R7 3.6.2 its bottom edge sits 23.5 epx above the touch point" 23.5 "$(python3 -c "print(($cy - $4) / $PX)")" 2.0
# Rows are counted by match, not by line: a uiautomator dump is ONE line, so `grep -c` (e15.sh's count, copied into the
# first two runs) reads 1 before and after and the clause cannot pass (E11_MENU-run2-grep-c/).
rows() { grep -o 'resource-id="reminder_row:' "$1" | wc -l | tr -d ' '; }
cp "$page" "$ROW_DIR/before-delete.xml"
before_rows="$(rows "$ROW_DIR/before-delete.xml")"
note "rows before Delete: $before_rows"
tap_node "$ROW_DIR/menu.xml" reminder_menu_delete
sleep 2
dump_ui "$ROW_DIR/after-delete.xml"
assert_eq "R7 3.6.1 Delete acts with no confirmation card" "no" "$(has_node "$ROW_DIR/after-delete.xml" "cortana_card:delete_confirm")"
assert_eq "and the row is gone (one row fewer)" "$(( before_rows - 1 ))" "$(rows "$ROW_DIR/after-delete.xml")"
assert_eq "the held reminder's row is the one gone" "no" "$(has_node "$ROW_DIR/after-delete.xml" "reminder_row:$ID")"
for _ in 1 2; do
  [ -f "$ROW_DIR/restore.xml" ] || cp "$ROW_DIR/after-delete.xml" "$ROW_DIR/restore.xml"
  R="$(reminder_id "QA13 menu" "$ROW_DIR/restore.xml")"
  [ -n "$R" ] || break
  set -- $(bounds "$ROW_DIR/restore.xml" "reminder_row:$R")
  delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  open_reminders
  cp "$ROW_DIR/reminders.xml" "$ROW_DIR/restore.xml"
done
assert_eq "restore: no QA13 reminder left" "" "$(reminder_id "QA13" "$ROW_DIR/restore.xml")"
cortana_close
show_start 3
row_end
