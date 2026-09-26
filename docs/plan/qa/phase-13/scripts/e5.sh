#!/usr/bin/env bash
# E5 The ≡ pane (phase 13 Acceptance E5, T13-2, T13-17): over Cortana's Home page it reads its measured (14,19,13); over
# the Reminders page a row's white title shows through (0.8*(18,24,16) + 0.2*B, never (14,19,13)); over that page's empty
# area it reads (17,23,15); acrylic off (battery saver) it is (14,19,13) everywhere. B is the host oracle over the same
# page captured with the pane closed. Fixture: two typed reminders (the second row lies below the pane's accent item).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$(dirname "$0")/reminders_fixture.sh"

row_begin E5 "the ≡ pane: (14,19,13) over Home, a reminder's title showing through, (17,23,15) over empty Reminders"
R=90
TINT=18,24,16
PANE_BTN=""

# Opens the pane over whatever Tess page is showing; leaves its dump in $1.
open_pane() { # out.xml
  dump_ui "$ROW_DIR/.page.xml"
  PANE_BTN=cortana_menu_button
  [ "$(has_node "$ROW_DIR/.page.xml" cortana_menu_button)" = yes ] || PANE_BTN=cortana_header_menu
  tap_node "$ROW_DIR/.page.xml" $PANE_BTN
  sleep 1.5
  dump_ui "$1"
}
# Boxes the pane draws its own content in (items with a label or an icon, the header), as exclusions: l,t,r,b each.
pane_excl() { # pane.xml
  python3 "$P13/dumpq.py" bounds_of "$1" cortana_pane_item_ | while read -r l t r b; do echo "$l,$t,$r,$b"; done
  python3 "$P13/dumpq.py" bounds_of "$1" cortana_pane_menu | while read -r l t r b; do echo "$l,$t,$r,$(( b + 20 ))"; done
}

assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
show_start 6

# ================================================================ (a) over Cortana's Home page (E15's setup)
ensure_start
cortana_assist
sleep 3
dump_ui "$ROW_DIR/home.xml"
assert_eq "(a) Tess's Home page is showing" "yes" "$(has_node "$ROW_DIR/home.xml" cortana_menu_button)"
L="$(bounds "$ROW_DIR/home.xml" cortana_session | tr ' ' ',')"
assert_eq "(a) the session's page (the backdrop child) fills the window" "0,0,1080,2340" "$L"
screencap "$ROW_DIR/home-closed.png"
MARK="$(ring_mark)"
open_pane "$ROW_DIR/pane-home.xml"
screencap "$ROW_DIR/pane-home.png"
ring_since "$MARK" > "$ROW_DIR/slice-pane-home.txt"
PB="$(bounds "$ROW_DIR/pane-home.xml" cortana_pane)"
assert_ne "(a) cortana_pane is in the dump" "" "$PB"
assert_eq "(a) acrylic:cortana_pane has the pane's bounds" "$PB" "$(bounds "$ROW_DIR/pane-home.xml" acrylic:cortana_pane)"
assert_contains "(a) the show line" "[fluent] cortana_pane source=live tint=($TINT) alpha=0.8 blur=30epx" "$(cat "$ROW_DIR/slice-pane-home.txt")"
set -- $PB; PR=$3; PBOT=$4
# shellcheck disable=SC2046
read -r PX PY PDEV <<< "$(python3 "$P13/acrylic_check.py" flat "$ROW_DIR/home-closed.png" 0,0,0 $R "$L" "10,450,$(( PR - 10 )),1750" $(pane_excl "$ROW_DIR/pane-home.xml"))"
assert_ne "(a) precondition: a 10x10 patch whose oracle B is (0,0,0) +- 1 at every pixel" "" "${PX:-}"
note "(a) patch ($PX,$PY), oracle max |B - Bm| = $PDEV"
read -r A1 A2 A3 <<< "$(python3 "$P13/acrylic_check.py" patch "$ROW_DIR/pane-home.png" "$PX" "$PY" 10 10)"
assert_within "(a) over Home: patch mean R = 14 +- 2" 14 "$A1" 2
assert_within "(a) over Home: patch mean G = 19 +- 2" 19 "$A2" 2
assert_within "(a) over Home: patch mean B = 13 +- 2" 13 "$A3" 2
adb shell input keyevent KEYCODE_BACK
sleep 1
cortana_close

# ================================================================ (b), (c) over the Reminders page
make_typed_reminder "remind me to check the QA13 pane list tomorrow at 9 am"
make_typed_reminder "remind me to check the QA13 second pane list tomorrow at 10 am"
open_reminders
screencap "$ROW_DIR/reminders-closed.png"
cp "$ROW_DIR/reminders.xml" "$ROW_DIR/reminders-closed.xml"
L2="0,0,1080,$(bounds "$ROW_DIR/reminders.xml" reminders_appbar | awk '{print $4}')"
assert_contains "(b) the Reminders page's backdrop child is in the dump" "bounds=\"[0,0][1080,${L2##*,}]\"" "$(cat "$ROW_DIR/reminders.xml")"
ID2="$(reminder_id "QA13 second pane")"
assert_ne "(b) the second reminder's row" "" "$ID2"
MARK="$(ring_mark)"
open_pane "$ROW_DIR/pane-rem.xml"
screencap "$ROW_DIR/pane-rem.png"
ring_since "$MARK" > "$ROW_DIR/slice-pane-reminders.txt"
assert_contains "(b) the show line" "[fluent] cortana_pane source=live tint=($TINT) alpha=0.8 blur=30epx" "$(cat "$ROW_DIR/slice-pane-reminders.txt")"
set -- $(bounds "$ROW_DIR/pane-rem.xml" cortana_pane); PR=$3
set -- $(bounds "$ROW_DIR/reminders-closed.xml" "reminder_row:$ID2"); RT=$2; RB=$4
note "(b) second row [$(bounds "$ROW_DIR/reminders-closed.xml" "reminder_row:$ID2")], pane right edge $PR"
# The patch over the title: inside the row, under the pane, clear of the pane's own content, where the closed page is
# brightest (the title's white text).
# shellcheck disable=SC2046
TP="$(python3 - "$ROW_DIR/reminders-closed.png" 150 $RT $(( PR - 20 )) $(( RT + (RB - RT) / 2 )) $(pane_excl "$ROW_DIR/pane-rem.xml") <<'PY'
import sys
import numpy as np
from PIL import Image
img = np.asarray(Image.open(sys.argv[1]).convert("L"), dtype=float)
l, t, r, b = map(int, sys.argv[2:6])
ex = [list(map(int, a.split(","))) for a in sys.argv[6:]]
best = None
for y in range(t, b - 10, 2):
    for x in range(l, r - 10, 2):
        if any(not (x + 10 <= e[0] or x >= e[2] or y + 10 <= e[1] or y >= e[3]) for e in ex):
            continue
        m = img[y:y + 10, x:x + 10].mean()
        if best is None or m > best[0]:
            best = (m, x, y)
print(best[1], best[2], round(best[0], 1))
PY
)"
read -r TX TY TLUM <<< "$TP"
note "(b) title patch ($TX,$TY) 10x10: the closed page's mean luma there is $TLUM (white text on (14,19,13))"
assert_eq "(b) the patch lies on the title's white text (closed-page luma >= 60)" "yes" "$(python3 -c "print('yes' if $TLUM >= 60 else 'no')")"
read -r WORST AT JUD EXP <<< "$(python3 "$P13/acrylic_check.py" patches "$ROW_DIR/pane-rem.png" "$ROW_DIR/reminders-closed.png" $TINT $R "$L2" 10 10 "$TX,$TY" 2> "$ROW_DIR/patch-title.txt")"
note "(b) judged=($JUD) expected=($EXP)"
assert_within "(b) over the title: 0.8*(18,24,16) + 0.2*B (worst channel |diff|)" 0 "$WORST" 4
assert_eq "(b) over the title: never (14,19,13) (some channel differs by > 2)" "yes" "$(python3 -c "
j=[float(v) for v in '$JUD'.split(',')]; print('yes' if max(abs(a-b) for a,b in zip(j,(14,19,13)))>2 else 'no')")"
# (c) the empty area below the rows: B within +-1 of the page's (14,19,13) (precondition), reads (17,23,15).
set -- $(bounds "$ROW_DIR/reminders-closed.xml" "reminder_row:$ID2"); LASTB=$4
# shellcheck disable=SC2046
read -r CX CY CDEV <<< "$(python3 "$P13/acrylic_check.py" flat "$ROW_DIR/reminders-closed.png" 14,19,13 $R "$L2" "10,$(( LASTB + 10 )),$(( PR - 10 )),1740" $(pane_excl "$ROW_DIR/pane-rem.xml"))"
assert_ne "(c) precondition: a 10x10 patch whose oracle B is (14,19,13) +- 1 at every pixel" "" "${CX:-}"
note "(c) patch ($CX,$CY), oracle max |B - Bm| = $CDEV"
read -r C1 C2 C3 <<< "$(python3 "$P13/acrylic_check.py" patch "$ROW_DIR/pane-rem.png" "$CX" "$CY" 10 10)"
assert_within "(c) over empty Reminders: patch mean R = 17 +- 2" 17 "$C1" 2
assert_within "(c) over empty Reminders: patch mean G = 23 +- 2" 23 "$C2" 2
assert_within "(c) over empty Reminders: patch mean B = 15 +- 2" 15 "$C3" 2
adb shell input keyevent KEYCODE_BACK
sleep 1

# ================================================================ (d) acrylic off: (14,19,13) everywhere
battery_saver_on
open_pane "$ROW_DIR/pane-rem-off.xml"
screencap "$ROW_DIR/pane-rem-off.png"
ring_since "$BS_MARK" > "$ROW_DIR/slice-saver.txt"
assert_contains "(d) acrylic=off reason=battery-saver" "[fluent] acrylic=off reason=battery-saver" "$(cat "$ROW_DIR/slice-saver.txt")"
assert_within "(d) off, over Reminders: the title patch reads (14,19,13) (worst px)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/pane-rem-off.png" "$TX" "$TY" 10 10 14,19,13)" 1
assert_within "(d) off, over Reminders: under the rows and below them, x 20..$(( PR - 20 )) (worst px)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/pane-rem-off.png" 20 460 $(( PR - 40 )) 1280 14,19,13)" 1
adb shell input keyevent KEYCODE_BACK
sleep 1
cortana_close
ensure_start
cortana_assist
sleep 3
open_pane "$ROW_DIR/pane-home-off.xml"
screencap "$ROW_DIR/pane-home-off.png"
assert_within "(d) off, over Home: persona, greeting and black page alike, x 20..$(( PR - 20 )) y 460..1740 (worst px)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/pane-home-off.png" 20 460 $(( PR - 40 )) 1280 14,19,13)" 1
adb shell input keyevent KEYCODE_BACK
sleep 1
cortana_close
assert_eq "(d) battery saver off: awake" "Awake" "$(battery_saver_off)"
assert_eq "(d) battery saver off: low_power = 0" "0" "$(adb shell settings get global low_power | tr -d '\r')"

# ---- restore: both reminders, through their menus' Delete
for title in "QA13 second pane" "QA13 pane list"; do
  open_reminders
  id="$(reminder_id "$title")"
  [ -n "$id" ] || continue
  set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row:$id")
  delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  cortana_close
done
open_reminders
assert_eq "restore: no QA13 reminder left" "" "$(reminder_id "QA13")"
cortana_close
show_start 3
row_end
