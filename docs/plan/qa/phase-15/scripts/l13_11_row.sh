#!/usr/bin/env bash
# L13-11 regression row (INDEX ledger): the Clock's "…" app bar scrim and World Clock's city search page closed on Back
# only by a state write (ClockNav.back()) and stayed hit-testable until they left the composition a frame later, so a
# touch in that frame was lost to them (L13-3's shape, phase 15's part). The fix draws both in an OverlayLayer and has
# the activity's Back apply its write at once. Two probes, each A = open the thing, then Back and B sent together from
# one device shell, B offset by 0-20 ms, and whether B reached the page read from the dump (the gesture driver's, since
# a Clock page never idles):
#   (a) the "…" bar expanded; B = a tap on a seeded alarm's row -> the alarm editor opens;
#   (b) the city search open (its keyboard hidden first by one Back); B = a hold on a seeded city -> its hold menu opens.
#   l13_11_row.sh [n]    (n trials per probe, default 30)
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"
N="${1:-30}"

where() { # dump -> editor | bar | search | menu | tabs | closed | other
  python3 - "$1" <<'PY'
import sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
# A dump with no nodes is a failed dump, not an outcome (review r2-b N2).
if '<node' not in s: print("nodump"); sys.exit()
# The Clock gone with Start on screen: in (a) this means B came BEFORE the Back — it closed the bar (a tap on its scrim)
# and the Back then closed the Clock (drv2, t25 on 05e543d7). A B lost to a stale overlay leaves the Clock on its tabs.
if 'resource-id="clock_root"' not in s:
    print("closed" if 'resource-id="start_page"' in s else "other"); sys.exit()
for tag, name in (("alarm_editor_field:snooze", "editor"), ("clock_more_menu", "bar"), ("clock_search_page", "search"),
                  ("clock_row_menu", "menu"), ("clock_tabs", "tabs")):
    if 'resource-id="%s"' % tag in s: print(name); break
else: print("other")
PY
}
centre() { set -- $(bounds "$1" "$2"); [ $# -eq 4 ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
ime_shown() { adb shell dumpsys input_method | tr -d '\r' | grep -c 'mInputShown=true'; }

row_begin L13_11 "a touch right after Back closes the Clock's \"…\" bar or city search reaches the page (n=$N each)"
assert_clock_empty "baseline"
dismiss_any_ring
WSAVED="$(adb shell "run-as $PKG sh -c 'if [ -f files/world_clock.json ]; then cp files/world_clock.json files/world_clock.json.l1311 && echo yes || echo cp-failed; else echo no; fi'" | tr -d '\r')"
note "world store existed before: $WSAVED"
# The seed overwrites the World Clock store; never without its copy (review r2-b N5).
[ "$WSAVED" = cp-failed ] && { assert_eq "the World Clock store was copied aside before the seed" yes no; row_end; exit 1; }
NOW="$(device_ms)"; read -r H M <<< "$(device_hm $(( NOW + 3 * 3600000 )))"
AID="$(api_alarm "$H" "$M" "L1311")"; assert_ne "seed: an alarm" "" "$AID"
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'echo [\\\"Europe/London\\\"] > files/world_clock.json'"

# ---- (a) the "…" bar
open_clock alarm
gdump "$ROW_DIR/a-0.xml" > /dev/null
read -r MX MY <<< "$(centre "$ROW_DIR/a-0.xml" clock_more)"
read -r RX RY <<< "$(centre "$ROW_DIR/a-0.xml" "alarm_row:$AID")"
assert_ne "(a): the \"…\" button is on screen" "" "${MX:-}"
assert_ne "(a): the alarm row is on screen" "" "${RX:-}"
mkdir -p "$ROW_DIR/a"; : > "$ROW_DIR/a/trials.txt"
for i in $(seq 1 "$N"); do
  off=$(( (i % 5) * 5 ))
  # Each trial starts on the tabs and counts only if the bar really opened (review/2026-09-27-L13-1112-fix-review-b.md B1).
  gdump "$ROW_DIR/a/t$i-pre.xml" > /dev/null
  [ "$(where "$ROW_DIR/a/t$i-pre.xml")" = tabs ] || { open_clock alarm; gdump "$ROW_DIR/a/t$i-pre.xml" > /dev/null; }
  [ "$(where "$ROW_DIR/a/t$i-pre.xml")" = tabs ] || { echo "t$i invalid: not on the tabs" >> "$ROW_DIR/a/trials.txt"; continue; }
  adb shell input tap $MX $MY; sleep 0.8
  gdump "$ROW_DIR/a/t$i-open.xml" > /dev/null
  if [ "$(has_node "$ROW_DIR/a/t$i-open.xml" clock_more_menu)" != yes ]; then
    echo "t$i invalid: the bar did not open" >> "$ROW_DIR/a/trials.txt"; adb shell input keyevent 4; sleep 1; continue
  fi
  [ -z "${MENU_BOUNDS:-}" ] && MENU_BOUNDS="$(bounds "$ROW_DIR/a/t$i-open.xml" clock_more_menu)"
  adb shell "input keyevent 4 & sleep 0.0$(printf %02d $off); input tap $RX $RY; wait"
  sleep 1.5
  gdump "$ROW_DIR/a/t$i.xml" > /dev/null
  st="$(where "$ROW_DIR/a/t$i.xml")"
  [ "$st" = nodump ] && { echo "t$i invalid: the dump after failed" >> "$ROW_DIR/a/trials.txt"; open_clock alarm; continue; }
  echo "t$i offset=${off}ms after: $st" >> "$ROW_DIR/a/trials.txt"
  case "$st" in
    editor|bar) adb shell input keyevent 4; sleep 1 ;;
    tabs) ;;
    *) open_clock alarm ;;
  esac
done
VALID="$(grep -c 'after:' "$ROW_DIR/a/trials.txt")"; OK="$(grep -c 'after: editor' "$ROW_DIR/a/trials.txt")"
LOST="$(grep -cE 'after: (tabs|other)' "$ROW_DIR/a/trials.txt")"; FLIP="$(grep -c 'after: closed' "$ROW_DIR/a/trials.txt")"
note "(a) the \"…\" menu's bounds while open: $MENU_BOUNDS (compare across builds: its layout is unchanged)"
note "(a) valid trials $VALID of $N: the row tap opened the editor $OK"
assert_eq "(a): every trial was valid (on the tabs, the bar open)" "$N" "$VALID"
note "(a) outcomes: reached $OK, lost to the stale bar $LOST, B before the Back $FLIP"
assert_eq "(a): no tap was lost to the stale bar (the Clock left on its tabs)" 0 "$LOST"
assert_eq "(a): every tap that came after the Back reached the page" "$(( VALID - FLIP ))" "$OK"
assert_eq "(a): B came before the Back in few trials (<= 3)" yes "$([ "$FLIP" -le 3 ] && echo yes || echo "no ($FLIP)")"

# ---- (b) the city search
open_clock world_clock
gdump "$ROW_DIR/b-0.xml" > /dev/null
read -r AX AY <<< "$(centre "$ROW_DIR/b-0.xml" 'clock_bar:add')"
read -r CX CY <<< "$(centre "$ROW_DIR/b-0.xml" 'clock_row:Europe/London')"
assert_ne "(b): the add button is on screen" "" "${AX:-}"
assert_ne "(b): the city row is on screen" "" "${CX:-}"
mkdir -p "$ROW_DIR/b"; : > "$ROW_DIR/b/trials.txt"
for i in $(seq 1 "$N"); do
  off=$(( (i % 5) * 5 ))
  gdump "$ROW_DIR/b/t$i-pre.xml" > /dev/null
  [ "$(where "$ROW_DIR/b/t$i-pre.xml")" = tabs ] || { open_clock world_clock; gdump "$ROW_DIR/b/t$i-pre.xml" > /dev/null; }
  [ "$(where "$ROW_DIR/b/t$i-pre.xml")" = tabs ] || { echo "t$i invalid: not on the tabs" >> "$ROW_DIR/b/trials.txt"; continue; }
  adb shell input tap $AX $AY; sleep 1.5
  # The search focuses its field; its keyboard takes the first Back.
  [ "$(ime_shown)" != 0 ] && { adb shell input keyevent 4; sleep 0.8; }
  gdump "$ROW_DIR/b/t$i-open.xml" > /dev/null
  if [ "$(has_node "$ROW_DIR/b/t$i-open.xml" clock_search_page)" != yes ]; then
    echo "t$i invalid: the search did not open" >> "$ROW_DIR/b/trials.txt"; open_clock world_clock; continue
  fi
  [ -z "${SEARCH_BOUNDS:-}" ] && SEARCH_BOUNDS="$(bounds "$ROW_DIR/b/t$i-open.xml" clock_search_page)"
  adb shell "input keyevent 4 & sleep 0.0$(printf %02d $off); input swipe $CX $CY $CX $CY 1200; wait"
  sleep 1.2
  gdump "$ROW_DIR/b/t$i.xml" > /dev/null
  st="$(where "$ROW_DIR/b/t$i.xml")"
  [ "$st" = nodump ] && { echo "t$i invalid: the dump after failed" >> "$ROW_DIR/b/trials.txt"; open_clock world_clock; continue; }
  echo "t$i offset=${off}ms after: $st" >> "$ROW_DIR/b/trials.txt"
  case "$st" in
    # The menu is closed by a tap on its scrim, not by Back: on a build with L13-12's defect, Back closes the Clock and
    # the next trial would start outside it (B2).
    menu) adb shell input tap $CX $CY; sleep 1 ;;
    search) adb shell input keyevent 4; sleep 0.8; [ "$(ime_shown)" != 0 ] && adb shell input keyevent 4; sleep 0.8 ;;
    tabs) ;;
    *) open_clock world_clock ;;
  esac
done
VALID="$(grep -c 'after:' "$ROW_DIR/b/trials.txt")"; OK="$(grep -c 'after: menu' "$ROW_DIR/b/trials.txt")"
# In (b) a closed Clock is never a touch that beat the Back (that hold is lost with the closing search on either
# build), so it counts as a loss (review r2-b N1).
LOST="$(grep -cE 'after: (tabs|closed|other)' "$ROW_DIR/b/trials.txt")"; FLIP=0
note "(b) the search page's bounds while open: $SEARCH_BOUNDS"
note "(b) valid trials $VALID of $N: the city hold opened its menu $OK"
assert_eq "(b): every trial was valid (on the tabs, the search open)" "$N" "$VALID"
note "(b) outcomes: reached $OK, lost to the stale search $LOST, B before the Back $FLIP"
assert_eq "(b): no hold was lost to the stale search (the Clock left on its tabs)" 0 "$LOST"
assert_eq "(b): every hold that came after the Back reached the page" "$(( VALID - FLIP ))" "$OK"

adb shell am force-stop $PKG; sleep 1
if [ "$WSAVED" = yes ]; then
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json.l1311 files/world_clock.json'"
else
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json files/world_clock.json.l1311-seeded'"
fi
app_delete_all
assert_clock_empty "restore"
row_end
