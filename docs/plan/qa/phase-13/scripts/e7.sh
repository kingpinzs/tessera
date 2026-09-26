#!/usr/bin/env bash
# E7 The two lights on touch (phase 13 Acceptance E7; T13-1's values and sample rule, T13-13, T13-25, T11-31). For each
# item: U (surface open, unpressed); DOWN at the touch point -> D; MOVE +60 px (0.5 r) in x, still inside -> M; MOVE off
# the item -> A; UP outside, which runs nothing. Single pixels are compared against the same pixel of U (the preamble's
# E7 exception), so the material's deterministic noise cancels. P = the item's held look without the lights: U for items
# that draw nothing while held, U + 0.15*(255 - U) for the app-list and Music menu items (ROW_PRESS_ALPHA). In A every
# sampled pixel reads U: the press ended when the pointer left the item, and with it the item's own ROW_PRESS_ALPHA fill
# (its press is cancelled on leaving, as built) — for the items whose held look is U this is the doc's "P +- 2" exactly.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$(dirname "$0")/reminders_fixture.sh"
P01="$(cd "$P13/../../phase-01/scripts" && pwd)"
P11="$(cd "$P13/../../phase-11/scripts" && pwd)"

row_begin E7 "the two lights on press: ring 0.30, radial 0.10 -> 0 at 40 epx, following the finger, gone off the item"

light() { python3 "$P13/lights.py" at "$@" | cut -d' ' -f1; }
# press_item <name> <held:u|p15> <tx> <ty> <r1x> <r1y> <r2x> <r2y> <farx> <fary> <offx> <offy>
# Captures U, D, M, A and asserts every sample (the item's surface is open; UP is at the off point).
press_item() {
  local n="$1" held="$2" tx=$3 ty=$4 r1x=$5 r1y=$6 r2x=$7 r2y=$8 fx=$9 fy=${10} ox=${11} oy=${12}
  local hx=$(( tx + 60 )) U="$ROW_DIR/$n-U.png" D="$ROW_DIR/$n-D.png" M="$ROW_DIR/$n-M.png" A="$ROW_DIR/$n-A.png"
  note "($n) touch ($tx,$ty), 0.5r ($hx,$ty), ring ($r1x,$r1y) ($r2x,$r2y), far ($fx,$fy), off ($ox,$oy), held=$held"
  screencap "$U"
  adb shell input motionevent DOWN $tx $ty; sleep 0.7
  screencap "$D"
  adb shell input motionevent MOVE $hx $ty; sleep 0.5
  screencap "$M"
  adb shell input motionevent MOVE $ox $oy; sleep 0.5
  screencap "$A"
  adb shell input motionevent UP $ox $oy; sleep 1.2
  assert_within "($n) D: ring ($r1x,$r1y) = P + 0.30(255-P)" 0 "$(light "$U" "$D" $held $r1x $r1y 0.30)" 4
  assert_within "($n) D: ring ($r2x,$r2y) = P + 0.30(255-P)" 0 "$(light "$U" "$D" $held $r2x $r2y 0.30)" 4
  assert_within "($n) D: touch point = P + 0.10(255-P)" 0 "$(light "$U" "$D" $held $tx $ty 0.10)" 4
  assert_within "($n) D: 0.5 r point = P + 0.05(255-P)" 0 "$(light "$U" "$D" $held $hx $ty 0.05)" 4
  assert_within "($n) D: far point = P" 0 "$(light "$U" "$D" $held $fx $fy 0)" 2
  assert_within "($n) M: the new touch point = P + 0.10(255-P)" 0 "$(light "$U" "$M" $held $hx $ty 0.10)" 4
  assert_within "($n) M: the old touch point = P + 0.05(255-P)" 0 "$(light "$U" "$M" $held $tx $ty 0.05)" 4
  local p
  for p in "$r1x $r1y" "$r2x $r2y" "$tx $ty" "$hx $ty" "$fx $fy"; do
    # shellcheck disable=SC2086
    assert_within "($n) A: ($p) = U (the press ended off the item)" 0 "$(light "$U" "$A" u $p 0)" 2
  done
}
# press_off <name> <held> <tx> <ty> <r1x> <r1y> <r2x> <r2y> <farx> <fary> <offx> <offy>: acrylic off, D = P +- 2 everywhere.
press_off() {
  local n="$1" held="$2" tx=$3 ty=$4 r1x=$5 r1y=$6 r2x=$7 r2y=$8 fx=$9 fy=${10} ox=${11} oy=${12}
  local hx=$(( tx + 60 )) U="$ROW_DIR/$n-off-U.png" D="$ROW_DIR/$n-off-D.png" p
  screencap "$U"
  adb shell input motionevent DOWN $tx $ty; sleep 0.7
  screencap "$D"
  adb shell input motionevent MOVE $ox $oy; sleep 0.4
  adb shell input motionevent UP $ox $oy; sleep 1.2
  for p in "$r1x $r1y" "$r2x $r2y" "$tx $ty" "$hx $ty" "$fx $fy"; do
    # shellcheck disable=SC2086
    assert_within "($n) off D: ($p) = P, no light" 0 "$(light "$U" "$D" $held $p 0)" 2
  done
}
# The geometry of a full-width band item: touch at the band's horizontal centre on the item's centre line; ring on the
# right and left edges; far point 200 px right of the touch. Prints the 12 press_item numbers (off point: $2 px above).
band_geom() { # "l t r b" off_y
  set -- $1 "$2"
  local cy=$(( ($2 + $4) / 2 )) cx=$(( ($1 + $3) / 2 ))
  echo "$cx $cy $(( $3 - 2 )) $cy $(( $1 + 1 )) $cy $(( cx + 200 )) $cy $cx $5"
}

assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
show_start 6

# ================================================================ (a) the reminder menu (E3's fixture, the tomorrow row)
reminders_setup
TB="$(bounds "$ROW_DIR/reminders.xml" "reminder_row:$REM_TOMORROW")"; set -- $TB
adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000
sleep 1.5
dump_ui "$ROW_DIR/a-menu.xml"
IB="$(bounds "$ROW_DIR/a-menu.xml" reminder_menu_complete)"
assert_ne "(a) the menu's Complete item is in the dump" "" "$IB"
set -- $IB; IL=$1; IT=$2; IR=$3; IBOT=$4
# Over the tomorrow row the menu's top lies above the window (its dump bounds are the visible part): the item's own
# centre line is its bottom minus half its 132-px (44-epx) height.
H=132; CY=$(( IBOT - H / 2 )); CX=$(( (IL + IR) / 2 ))
note "(a) Complete item visible [$IB]; centre line y=$CY"
assert_eq "(a) the centre line is on screen, inside the item" "yes" "$( [ $CY -ge 2 ] && [ $CY -lt $IBOT ] && echo yes || echo no)"
press_item a u $CX $CY $(( IR - 2 )) $CY $(( CX + 200 )) $(( IBOT - 2 )) $(( CX + 200 )) $CY 990 $CY
dump_ui "$ROW_DIR/a-after.xml"
assert_eq "(a) after UP the tomorrow reminder's row is still there (not completed or deleted)" "yes" "$(has_node "$ROW_DIR/a-after.xml" "reminder_row:$REM_TOMORROW")"
record "(a) after UP the menu is" "$( [ "$(has_node "$ROW_DIR/a-after.xml" reminder_menu)" = yes ] && echo open || echo dismissed)"
[ "$(has_node "$ROW_DIR/a-after.xml" reminder_menu)" = yes ] && { adb shell input tap 1000 1500; sleep 1.2; }

# ================================================================ (b) the ≡ pane over Home: the Home item (current)
cortana_close
ensure_start
cortana_assist
sleep 3
dump_ui "$ROW_DIR/.home.xml"
tap_node "$ROW_DIR/.home.xml" cortana_menu_button
sleep 1.5
dump_ui "$ROW_DIR/b-pane.xml"
IB="$(bounds "$ROW_DIR/b-pane.xml" cortana_pane_item_home)"
assert_ne "(b) the pane's Home item is in the dump" "" "$IB"
set -- $IB; IL=$1; IT=$2; IR=$3; IBOT=$4; CX=$(( (IL + IR) / 2 )); CY=$(( (IT + IBOT) / 2 ))
press_item b u $CX $CY $(( IR - 2 )) $CY $(( IL + 100 )) $(( IBOT - 2 )) $(( CX + 220 )) $CY $CX 700
dump_ui "$ROW_DIR/b-after.xml"
assert_eq "(b) after UP the pane is still open" "yes" "$(has_node "$ROW_DIR/b-after.xml" cortana_pane)"

# ---- (a), (b) with acrylic off
battery_saver_on
press_off b u $CX $CY $(( IR - 2 )) $CY $(( IL + 100 )) $(( IBOT - 2 )) $(( CX + 220 )) $CY $CX 700
adb shell input keyevent KEYCODE_BACK; sleep 1
cortana_close
open_reminders
set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row:$REM_TOMORROW")
adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000
sleep 1.5
dump_ui "$ROW_DIR/a-menu-off.xml"
set -- $(bounds "$ROW_DIR/a-menu-off.xml" reminder_menu_complete); IL=$1; IR=$3; IBOT=$4; CY=$(( IBOT - H / 2 )); CX=$(( (IL + IR) / 2 ))
press_off a u $CX $CY $(( IR - 2 )) $CY $(( CX + 200 )) $(( IBOT - 2 )) $(( CX + 200 )) $CY 990 $CY
dump_ui "$ROW_DIR/.a-off-after.xml"
[ "$(has_node "$ROW_DIR/.a-off-after.xml" reminder_menu)" = yes ] && { adb shell input tap 1000 1500; sleep 1.2; }
cortana_close
assert_eq "battery saver off: awake" "Awake" "$(battery_saver_off)"
assert_eq "battery saver off: low_power = 0" "0" "$(adb shell settings get global low_power | tr -d '\r')"
reminders_restore

# ================================================================ (d) the app-list band's Pin to Start
set_checker
show_start 7
to_app_list 3
dump_ui "$ROW_DIR/d-list.xml"
ROWID="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/d-list.xml" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
set -- $(bounds "$ROW_DIR/d-list.xml" "$ROWID"); HX=$(( ($1 + $3) / 2 )); HY=$(( ($2 + $4) / 2 ))
adb shell input swipe $HX $HY $HX $HY 1000
sleep 1.5
dump_ui "$ROW_DIR/d-band.xml"
IB="$(bounds "$ROW_DIR/d-band.xml" applist_menu_pin)"
assert_ne "(d) applist_menu_pin is in the dump" "" "$IB"
BT="$(bounds "$ROW_DIR/d-band.xml" applist_menu | awk '{print $2}')"
MARK="$(ring_mark)"
# shellcheck disable=SC2046
press_item d p15 $(band_geom "$IB" $(( BT - 100 )))
ring_since "$MARK" > "$ROW_DIR/slice-d.txt"
assert_absent "(d) after UP the app is not pinned (no 'pin to Start' line)" "pin to Start" "$(cat "$ROW_DIR/slice-d.txt")"
dump_ui "$ROW_DIR/.d-after.xml"
[ "$(has_node "$ROW_DIR/.d-after.xml" applist_menu)" = yes ] && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
battery_saver_on
adb shell input swipe $HX $HY $HX $HY 1000
sleep 1.5
# shellcheck disable=SC2046
press_off d p15 $(band_geom "$IB" $(( BT - 100 )))
dump_ui "$ROW_DIR/.d-after2.xml"
[ "$(has_node "$ROW_DIR/.d-after2.xml" applist_menu)" = yes ] && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
assert_eq "(d) battery saver off: awake" "Awake" "$(battery_saver_off)"
clear_background

# ================================================================ (e) the Music menu's "Add to new playlist"
source "$P01/ui.sh"
source "$P01/music_lib.sh"
music_mute
music_fixtures
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
adb shell am force-stop $PKG; sleep 1
adb shell run-as $PKG cp files/music_playlists.json files/music_playlists.json.qa13 2>/dev/null
PL0="$(adb shell run-as $PKG cat files/music_playlists.json 2>/dev/null | grep -o '"id"' | wc -l | tr -d ' ')"
music_open
goto_pivot songs "$ROW_DIR/e-songs.xml"
SONG=$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/e-songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')
set -- $(bounds "$ROW_DIR/e-songs.xml" "$SONG"); HX=$(( ($1 + $3) / 2 )); HY=$(( ($2 + $4) / 2 ))
adb shell input swipe $HX $HY $HX $HY 1200
sleep 2
dump_ui "$ROW_DIR/e-menu.xml"
IB="$(bounds "$ROW_DIR/e-menu.xml" music_menu_new)"
assert_ne "(e) music_menu_new is in the dump" "" "$IB"
BT="$(bounds "$ROW_DIR/e-menu.xml" music_menu | awk '{print $2}')"
# shellcheck disable=SC2046
press_item e p15 $(band_geom "$IB" $(( BT - 100 )))
sleep 1
assert_eq "(e) after UP no playlist was made" "$PL0" "$(adb shell run-as $PKG cat files/music_playlists.json 2>/dev/null | grep -o '"id"' | wc -l | tr -d ' ')"
dump_ui "$ROW_DIR/.e-after.xml"
assert_eq "(e) after UP no name box opened" "no" "$(has_node "$ROW_DIR/.e-after.xml" music_name_box)"
[ "$(has_node "$ROW_DIR/.e-after.xml" music_menu)" = yes ] && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
battery_saver_on
adb shell input swipe $HX $HY $HX $HY 1200
sleep 2
# shellcheck disable=SC2046
press_off e p15 $(band_geom "$IB" $(( BT - 100 )))
dump_ui "$ROW_DIR/.e-after2.xml"
[ "$(has_node "$ROW_DIR/.e-after2.xml" music_menu)" = yes ] && { adb shell input keyevent KEYCODE_BACK; sleep 1; }
assert_eq "(e) battery saver off: awake" "Awake" "$(battery_saver_off)"
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'if [ -f files/music_playlists.json.qa13 ]; then mv files/music_playlists.json.qa13 files/music_playlists.json; else rm -f files/music_playlists.json; fi'"

# ================================================================ (c) phase 11's satellite (the Seeding exception)
. "$P11/q.sh"
QA11="$QROOT/phase-11"
seed_fixtures
restore baseline_layout.json
sat_press() { # style
  local style="$1" mark
  set_press "$style"
  qdump "$ROW_DIR/.c-rest-$style.xml"
  read -r FX FY <<< "$(center "$ROW_DIR/.c-rest-$style.xml" "tile:$A_KEY")"
  mark="$(ring_mark)"
  hold "$FX" "$FY" 1.0
  sleep 1
  qdump "$ROW_DIR/c-burst-$style.xml"
  assert_eq "(c $style) the burst is open" "yes" "$(has_node "$ROW_DIR/c-burst-$style.xml" quick_burst)"
  rest_lines "$mark" > "$ROW_DIR/c-rest-$style.txt"
  read -r _ L T R B <<< "$(grep '^0 ' "$ROW_DIR/c-rest-$style.txt")"
  assert_ne "(c $style) quick_sat:0 rest= bounds" "" "${L:-}"
  note "(c $style) quick_sat:0 rest=[$L,$T,$R,$B]"
  # T13-8 / T11-31: touch (left + 10, top + 10); 0.5 r at (left + 70, top + 10); the ring on the right and bottom
  # edges; the far point 12 px inside the bottom-right corner; off: 200 px left of the satellite, clear of the burst.
  press_item "c-$style" u $(( L + 10 )) $(( T + 10 )) $(( R - 2 )) $(( (T + B) / 2 )) $(( (L + R) / 2 )) $(( B - 2 )) $(( R - 12 )) $(( B - 12 )) $(( L - 200 )) $(( T + 10 ))
  local rect eq
  for rect in "tile:$A_KEY" quick_sat:1 quick_sat:2 quick_sat:3; do
    # shellcheck disable=SC2046
    eq="$(python3 "$P11/qpix.py" equal "$ROW_DIR/c-$style-U.png" "$ROW_DIR/c-$style-D.png" $(bounds "$ROW_DIR/c-burst-$style.xml" "$rect") 1)"
    assert_eq "(c $style) $rect in D equals U +- 1 (the lights stay in the pressed satellite)" EQUAL "${eq%% *}"
  done
  qdump "$ROW_DIR/c-after-$style.xml"
  assert_eq "(c $style) after UP off the satellites nothing ran, the burst stays" "yes" "$(has_node "$ROW_DIR/c-after-$style.xml" quick_burst)"
  # Acrylic off, once, under the last style.
  if [ "$style" = p4 ]; then
    battery_saver_on
    press_off "c-$style" u $(( L + 10 )) $(( T + 10 )) $(( R - 2 )) $(( (T + B) / 2 )) $(( (L + R) / 2 )) $(( B - 2 )) $(( R - 12 )) $(( B - 12 )) $(( L - 200 )) $(( T + 10 ))
    assert_eq "(c) battery saver off: awake" "Awake" "$(battery_saver_off)"
  fi
  c6
}
sat_press tilt
sat_press p4
set_press none
assert_contains "(c) the press style is None again (RV12)" '<string name="press">NONE</string>' "$(adb shell run-as $PKG cat shared_prefs/start_theme.xml | tr -d '\n')"
# Phase 02's baseline back (the Seeding exception's restore), with C-3's check.
MARK="$(ring_mark)"
layout_restore "$QROOT/phase-02/baseline_layout.json"
assert_absent "phase 02's baseline restored: no slot re-assigned (C-3)" "-> assigned" "$(ring_since "$MARK" | grep assignSlotOnce)"

# ================================================================ controls
# A Start tile with the press style None: zero pixel change in the tile region (phase 01 E10's check).
ensure_start_page
qdump "$ROW_DIR/ctl-start.xml"
TR="$(bounds "$ROW_DIR/ctl-start.xml" tile:shell:settings)"
assert_ne "control: the Settings tile is on Start" "" "$TR"
set -- $TR
screencap "$ROW_DIR/ctl-tile-U.png"
adb shell input motionevent DOWN $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 0.5
screencap "$ROW_DIR/ctl-tile-D.png"
adb shell input motionevent CANCEL $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1
EQ="$(python3 "$P11/qpix.py" equal "$ROW_DIR/ctl-tile-U.png" "$ROW_DIR/ctl-tile-D.png" $TR 0)"
assert_eq "control: a pressed Start tile (press None) changes no pixel" EQUAL "${EQ%% *}"
# A Settings row: X19's flat 15 % white, neither light (its edge patch equals its interior).
adb shell am start -W -n $PKG/.settings.SettingsActivity >/dev/null 2>&1; sleep 2
dump_ui "$ROW_DIR/ctl-settings.xml"
SR="$(bounds "$ROW_DIR/ctl-settings.xml" settings_tile_apps)"
assert_ne "control: the Settings row is on screen" "" "$SR"
# The touch point and both patches sit in the row's text-free band below its subtitle (the first run placed the
# interior patch on the subtitle's glyphs): between the subtitle's bottom + 10 and the row's bottom - 10.
SUBB="$(python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/ctl-settings.xml" | grep -m1 '^Choose the apps' | cut -d'|' -f2 | awk '{print $4}')"
set -- $SR; SX=$(( $3 - 280 )); SY=$(( (SUBB + 10 + $4 - 10) / 2 ))
note "control: row [$SR], subtitle bottom $SUBB, touch ($SX,$SY)"
screencap "$ROW_DIR/ctl-row-U.png"
adb shell input motionevent DOWN $SX $SY; sleep 0.6
screencap "$ROW_DIR/ctl-row-D.png"
adb shell input motionevent MOVE $SX $(( $2 - 300 )); sleep 0.4
adb shell input motionevent UP $SX $(( $2 - 300 )); sleep 1
assert_within "control: the Settings row's pressed touch point = U + 0.15(255-U) (no radial light)" 0 "$(light "$ROW_DIR/ctl-row-U.png" "$ROW_DIR/ctl-row-D.png" p15 $SX $SY 0)" 2
EDGE="$(python3 "$P13/acrylic_check.py" patch "$ROW_DIR/ctl-row-D.png" $(( $3 - 10 )) $(( SY - 5 )) 10 10)"
INT="$(python3 "$P13/acrylic_check.py" patch "$ROW_DIR/ctl-row-D.png" $(( SX - 200 )) $(( SY - 5 )) 10 10)"
note "control: row edge patch ($EDGE), interior patch ($INT)"
assert_within "control: the Settings row's edge patch equals its interior (no ring)" 0 "$(python3 -c "
a=[float(v) for v in '$EDGE'.split()]; b=[float(v) for v in '$INT'.split()]; print(max(abs(x-y) for x,y in zip(a,b)))")" 2
leave_settings
show_start 3
row_end
