#!/usr/bin/env bash
# E4 H21's band and the Music menus (phase 13 Acceptance E4, T13-2, T13-12, T13-14, T13-19): each band is T = (0,0,0)
# acrylic, so under it the patch means and column-mean profiles equal 0.2*B, B the host oracle over the same screen
# captured with the band closed; the band's own text stays sharp; acrylic off (battery saver) draws the theme
# background. The Music squares have no dump node, so their bounds are read from the closed capture (accent pixels).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
P01="$(cd "$P13/../../phase-01/scripts" && pwd)"
source "$P01/ui.sh"
source "$P01/music_lib.sh"

row_begin E4 "the app-list hold band and the Music menus: 0.2 x the blurred page under them, text sharp, solid when off"
R=90
ACCENT=0,120,215

# The pixel values of a band with acrylic off: the theme background (0,0,0) at opacity 1, worst px over the region.
off_worst() { python3 "$P13/acrylic_check.py" opaque "$1" "$2" "$3" "$4" "$5" 0,0,0; }

assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true

# ================================================================ (1) the app-list hold band (H21)
set_checker
show_start 7
to_app_list 3
dump_ui "$ROW_DIR/applist.xml"
LB="$(bounds "$ROW_DIR/applist.xml" app_list)"
assert_ne "(1) app_list is in the dump" "" "$LB"
L1="$(echo "$LB" | tr ' ' ',')"
ROWID="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/applist.xml" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
assert_ne "(1) an app row to hold" "" "$ROWID"
set -- $(bounds "$ROW_DIR/applist.xml" "$ROWID"); X=$(( ($1 + $3) / 2 )); Y=$(( ($2 + $4) / 2 ))
note "(1) holding $ROWID at ($X,$Y)"
MARK="$(ring_mark)"
adb shell input swipe $X $Y $X $Y 1000
sleep 1.5
dump_ui "$ROW_DIR/band.xml"
screencap "$ROW_DIR/band.png"
ring_since "$MARK" > "$ROW_DIR/slice-applist-menu.txt"
BB="$(bounds "$ROW_DIR/band.xml" applist_menu)"
assert_ne "(1) applist_menu is in the dump" "" "$BB"
assert_eq "(1) acrylic:applist_menu has the band's bounds" "$BB" "$(bounds "$ROW_DIR/band.xml" acrylic:applist_menu)"
assert_contains "(1) the show line" "[fluent] applist_menu source=live tint=(0,0,0) alpha=0.8 blur=30epx" "$(cat "$ROW_DIR/slice-applist-menu.txt")"
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/band-closed.xml"
screencap "$ROW_DIR/band-closed.png"
assert_eq "(1) Back closed the band" "no" "$(has_node "$ROW_DIR/band-closed.xml" applist_menu)"
set -- $BB; BL=$1; BT=$2; BR=$3; BBOT=$4
# The item labels sit on the left (x < 300); right of them every row of the band is clear of text.
TXR=0
while read -r l t r b; do [ "$r" -gt "$TXR" ] && TXR=$r; done < <(python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/band.xml" | grep -E '^(Pin to Start|Uninstall)\|' | cut -d'|' -f2)
assert_eq "(1) the band's labels end left of x = 400" "yes" "$( [ "$TXR" -gt 0 ] && [ "$TXR" -lt 400 ] && echo yes || echo no)"
PY0=$(( BT + 10 )); PY1=$(( BBOT - 10 ))
read -r WORST AT JUD EXP <<< "$(python3 "$P13/acrylic_check.py" profile "$ROW_DIR/band.png" "$ROW_DIR/band-closed.png" 0,0,0 $R "$L1" $PY0 $PY1 400 $(( BR - 10 )) 2> "$ROW_DIR/profile-band.txt")"
note "(1) profile rows $PY0..$PY1, x 400..$(( BR - 10 )): worst at x=$AT judged=($JUD) expected=($EXP)"
assert_within "(1) band profile = 0.2*B (worst column-mean |diff|)" 0 "$WORST" 4
MID=$(( (BT + BBOT) / 2 ))
read -r WORST AT JUD EXP <<< "$(python3 "$P13/acrylic_check.py" patches "$ROW_DIR/band.png" "$ROW_DIR/band-closed.png" 0,0,0 $R "$L1" 10 10 \
  450,$PY0 650,$PY0 850,$PY0 1000,$PY0 450,$MID 650,$MID 850,$MID 1000,$MID 450,$(( PY1 - 10 )) 650,$(( PY1 - 10 )) 850,$(( PY1 - 10 )) 1000,$(( PY1 - 10 )) 2> "$ROW_DIR/patches-band.txt")"
note "(1) patches: worst at $AT judged=($JUD) expected=($EXP)"
assert_within "(1) band patch means = 0.2*B (worst |diff|, 12 patches)" 0 "$WORST" 4
# Discriminator: 0.2*B here is 0..10 levels, within reach of the solid (0,0,0) at +-4, so the band must also carry
# the material's noise (std >= 1; over near-black its negative half clips at 0), which the solid form (std 0) lacks.
assert_within "(1) the band carries the material's noise (std of 40x40, 1..4; solid = 0)" 2.5 "$(python3 "$P13/edge.py" std "$ROW_DIR/band.png" 650 $(( MID - 20 )) 40 40)" 1.5
read -r l t r b <<< "$(python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/band.xml" | grep -m1 '^Pin to Start|' | cut -d'|' -f2)"
read -r TW _ <<< "$(python3 "$P13/edge.py" sharpest "$ROW_DIR/band.png" $(( (t + b) / 2 )) $(( l > 6 ? l - 6 : 0 )) $(( r + 6 )))"
assert_within "(1) the band's 'Pin to Start' text edge is sharp" 1 "$TW" 1
# Acrylic off: the band as built, (0,0,0).
battery_saver_on
adb shell input swipe $X $Y $X $Y 1000
sleep 1.5
dump_ui "$ROW_DIR/band-off.xml"
screencap "$ROW_DIR/band-off.png"
ring_since "$BS_MARK" > "$ROW_DIR/slice-applist-menu-off.txt"
assert_eq "(1) off: the band opened at the same place" "$BB" "$(bounds "$ROW_DIR/band-off.xml" applist_menu)"
assert_within "(1) off: the band reads (0,0,0) right of its labels (worst px)" 0 "$(off_worst "$ROW_DIR/band-off.png" 400 $PY0 $(( BR - 410 )) $(( PY1 - PY0 )))" 1
adb shell input keyevent KEYCODE_BACK
sleep 1
assert_eq "(1) battery saver off: awake" "Awake" "$(battery_saver_off)"
assert_eq "(1) battery saver off: low_power = 0" "0" "$(adb shell settings get global low_power | tr -d '\r')"
ring_save
clear_background

# ================================================================ (2) the playlists pivot's hold menu (T13-14)
music_mute
music_fixtures
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
adb shell am force-stop $PKG; sleep 1
adb shell run-as $PKG cp files/music_playlists.json files/music_playlists.json.qa13 2>/dev/null
adb shell run-as $PKG rm -f files/music_playlists.json
music_open
goto_pivot playlists "$ROW_DIR/pl0.xml"
assert_eq "(2) the playlists pivot starts with no playlists" 0 "$(grep -c 'resource-id="music_playlist:' "$ROW_DIR/pl0.xml")"
for n in One Two Three; do   # MUSIC8's steps: new playlist, clear the name, type it, Done
  dump_ui "$ROW_DIR/.pl.xml"; tap_node "$ROW_DIR/.pl.xml" music_new_playlist; sleep 2
  adb shell input keyevent KEYCODE_MOVE_END; for _ in $(seq 1 14); do adb shell input keyevent 67; done
  adb shell input text "QA13%s$n"; sleep 1; adb shell input keyevent 66; sleep 2
done
dump_ui "$ROW_DIR/pl3.xml"
screencap "$ROW_DIR/pl3.png"
assert_eq "(2) three playlists made" 3 "$(grep -o 'resource-id="music_playlist:[^"]*"' "$ROW_DIR/pl3.xml" | wc -l | tr -d ' ')"
L2="$(bounds "$ROW_DIR/pl3.xml" music_root | tr ' ' ',')"
assert_ne "(2) music_root (the backdrop child's bounds) is in the dump" "" "$L2"
FIRST="$(grep -o 'resource-id="music_playlist:[^"]*"' "$ROW_DIR/pl3.xml" | sed -n 1p | sed 's/resource-id="//; s/"$//')"
NEXT="$(grep -o 'resource-id="music_playlist:[^"]*"' "$ROW_DIR/pl3.xml" | sed -n 2p | sed 's/resource-id="//; s/"$//')"
set -- $(bounds "$ROW_DIR/pl3.xml" "$FIRST"); F1=$1; F2=$2; F3=$3; F4=$4; X=$(( (F1 + F3) / 2 )); Y=$(( (F2 + F4) / 2 ))
MARK="$(ring_mark)"
adb shell input swipe $X $Y $X $Y 1000
sleep 1.5
dump_ui "$ROW_DIR/plmenu.xml"
screencap "$ROW_DIR/plmenu.png"
ring_since "$MARK" > "$ROW_DIR/slice-music-menu-pivot.txt"
MB="$(bounds "$ROW_DIR/plmenu.xml" music_menu)"
assert_ne "(2) music_menu is in the dump" "" "$MB"
assert_eq "(2) acrylic:music_menu has the band's bounds" "$MB" "$(bounds "$ROW_DIR/plmenu.xml" acrylic:music_menu)"
assert_contains "(2) the show line" "[fluent] music_menu source=live tint=(0,0,0) alpha=0.8 blur=30epx" "$(cat "$ROW_DIR/slice-music-menu-pivot.txt")"
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/plmenu-closed.xml"
screencap "$ROW_DIR/plmenu-closed.png"
assert_eq "(2) Back closed the menu" "no" "$(has_node "$ROW_DIR/plmenu-closed.xml" music_menu)"
set -- $MB; ML=$1; MT=$2; MR=$3; MBOT=$4
SQ_N="$(python3 "$P13/acrylic_check.py" find_color "$ROW_DIR/plmenu-closed.png" $ACCENT "$(bounds "$ROW_DIR/pl3.xml" "$NEXT" | tr ' ' ',')")"
SQ_F="$(python3 "$P13/acrylic_check.py" find_color "$ROW_DIR/plmenu-closed.png" $ACCENT "$F1,$F2,$F3,$F4")"
note "(2) squares from the closed capture: held row [$SQ_F], next row [$SQ_N]; menu [$MB]"
set -- $SQ_N; SL=$1; ST=$2; SR=$3; SB=$4
assert_eq "(2) the next row's square intersects the menu" "yes" "$( [ -n "$SQ_N" ] && [ $SL -lt $MR ] && [ $SR -gt $ML ] && [ $ST -lt $MBOT ] && [ $SB -gt $MT ] && echo yes || echo no)"
# Rows inside the square and the menu, clear of the menu's labels (+-6 px): the longest run.
RUN="$(python3 - "$ROW_DIR/plmenu.xml" $ST $SB $MT $MBOT <<'PY'
import re, sys
xml = open(sys.argv[1]).read(); st, sb, mt, mb = map(int, sys.argv[2:6])
bad = set()
for m in re.finditer(r'text="(Rename|Delete)"[^>]*bounds="\[\d+,(\d+)\]\[\d+,(\d+)\]"', xml):
    bad.update(range(int(m.group(2)) - 6, int(m.group(3)) + 7))
best, cur = (0, 0, -1), None
for y in range(max(st, mt) + 2, min(sb, mb) - 2):
    if y in bad:
        cur = None; continue
    cur = (cur[0], y) if cur else (y, y)
    if cur[1] - cur[0] > best[2] - best[1]: best = (0, cur[0], cur[1])
print(best[1], best[2])
PY
)"
read -r Y0 Y1 <<< "$RUN"
assert_eq "(2) >= 8 rows inside the square under the menu, clear of its labels" "yes" "$( [ $(( Y1 - Y0 + 1 )) -ge 8 ] && echo yes || echo no)"
read -r WORST AT JUD EXP <<< "$(python3 "$P13/acrylic_check.py" profile "$ROW_DIR/plmenu.png" "$ROW_DIR/plmenu-closed.png" 0,0,0 $R "$L2" $Y0 $Y1 0 $(( SR + 160 )) 2> "$ROW_DIR/profile-square.txt")"
note "(2) profile rows $Y0..$Y1 across the square's right edge x=$SR (x 0..$(( SR + 160 ))): worst at x=$AT judged=($JUD) expected=($EXP)"
assert_within "(2) the square's edge under the menu = 0.2*B (worst column-mean |diff|)" 0 "$WORST" 4
set -- $SQ_F
read -r SW SX <<< "$(python3 "$P13/edge.py" sharpest "$ROW_DIR/plmenu.png" $(( ($2 + $4) / 2 )) $(( $3 - 40 )) $(( $3 + 40 )))"
note "(2) held row's square: steepest step at x=$SX (its right edge is $3)"
assert_within "(2) the held row's own square, outside the menu, is sharp" 1 "$SW" 1
battery_saver_on
adb shell input swipe $X $Y $X $Y 1000
sleep 1.5
dump_ui "$ROW_DIR/plmenu-off.xml"
screencap "$ROW_DIR/plmenu-off.png"
assert_eq "(2) off: the menu opened at the same place" "$MB" "$(bounds "$ROW_DIR/plmenu-off.xml" music_menu)"
assert_within "(2) off: over the square, the theme background (worst px)" 0 "$(off_worst "$ROW_DIR/plmenu-off.png" 0 $Y0 $(( SR + 160 )) $(( Y1 - Y0 + 1 )))" 1
assert_within "(2) off: right of the labels, the theme background (worst px)" 0 "$(off_worst "$ROW_DIR/plmenu-off.png" 400 $(( MT + 10 )) $(( MR - 410 )) $(( MBOT - MT - 20 )))" 1
adb shell input keyevent KEYCODE_BACK
sleep 1
assert_eq "(2) battery saver off: awake" "Awake" "$(battery_saver_off)"
assert_eq "(2) battery saver off: low_power = 0" "0" "$(adb shell settings get global low_power | tr -d '\r')"
# Delete the three playlists through their own hold menu.
for _ in 1 2 3; do
  dump_ui "$ROW_DIR/.pl.xml"
  P="$(grep -o 'resource-id="music_playlist:[^"]*"' "$ROW_DIR/.pl.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
  [ -n "$P" ] || break
  set -- $(bounds "$ROW_DIR/.pl.xml" "$P"); adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000
  sleep 1.5
  dump_ui "$ROW_DIR/.plm.xml"; tap_node "$ROW_DIR/.plm.xml" music_menu_delete; sleep 2
done
dump_ui "$ROW_DIR/pl-deleted.xml"
assert_eq "(2) restore: the three playlists are deleted" 0 "$(grep -c 'resource-id="music_playlist:' "$ROW_DIR/pl-deleted.xml")"

# ================================================================ (3) now-playing's ••• band (T13-19; MUSIC7's setup)
goto_pivot songs "$ROW_DIR/songs.xml"
SONG=$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')
assert_ne "(3) there is a song to open" "" "$SONG"
tap_node "$ROW_DIR/songs.xml" "$SONG"
sleep 4
dump_ui "$ROW_DIR/np.xml"
assert_eq "(3) the now-playing screen is open" "yes" "$(has_node "$ROW_DIR/np.xml" nowplaying_root)"
L3="$(bounds "$ROW_DIR/np.xml" nowplaying_root | tr ' ' ',')"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/np.xml" nowplaying_control:more
sleep 1.5
dump_ui "$ROW_DIR/npmenu.xml"
screencap "$ROW_DIR/npmenu.png"
ring_since "$MARK" > "$ROW_DIR/slice-music-menu-nowplaying.txt"
NB="$(bounds "$ROW_DIR/npmenu.xml" music_menu)"
assert_ne "(3) music_menu is in the dump" "" "$NB"
assert_eq "(3) acrylic:music_menu has music_menu's bounds" "$NB" "$(bounds "$ROW_DIR/npmenu.xml" acrylic:music_menu)"
assert_contains "(3) the show line" "[fluent] music_menu source=live tint=(0,0,0) alpha=0.8 blur=30epx" "$(cat "$ROW_DIR/slice-music-menu-nowplaying.txt")"
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/npmenu-closed.xml"
screencap "$ROW_DIR/npmenu-closed.png"
assert_eq "(3) Back closed the band, now-playing stays" "no yes" "$(has_node "$ROW_DIR/npmenu-closed.xml" music_menu) $(has_node "$ROW_DIR/npmenu-closed.xml" nowplaying_root)"
set -- $NB; NT=$2; NR=$3; NBOT=$4
# The band's labels are on the left; patches right of x = 450 at five heights.
PTS=""
for f in 1 3 5 7 9; do PTS="$PTS 500,$(( NT + (NBOT - NT) * f / 10 - 5 )) 800,$(( NT + (NBOT - NT) * f / 10 - 5 ))"; done
# shellcheck disable=SC2086
read -r WORST AT JUD EXP <<< "$(python3 "$P13/acrylic_check.py" patches "$ROW_DIR/npmenu.png" "$ROW_DIR/npmenu-closed.png" 0,0,0 $R "$L3" 10 10 $PTS 2> "$ROW_DIR/patches-nowplaying.txt")"
note "(3) patches: worst at $AT judged=($JUD) expected=($EXP)"
assert_within "(3) band patch means = 0.2*B (worst |diff|, 10 patches)" 0 "$WORST" 4
# Discriminator, as in (1): over this black page 0.2*B is 0..5 levels, so the band must carry the material's noise.
assert_within "(3) the band carries the material's noise (std of 40x40, 1..4; solid = 0)" 2.5 "$(python3 "$P13/edge.py" std "$ROW_DIR/npmenu.png" 800 $(( (NT + NBOT) / 2 - 20 )) 40 40)" 1.5
battery_saver_on
tap_node "$ROW_DIR/np.xml" nowplaying_control:more
sleep 1.5
dump_ui "$ROW_DIR/npmenu-off.xml"
screencap "$ROW_DIR/npmenu-off.png"
assert_eq "(3) off: the band opened at the same place" "$NB" "$(bounds "$ROW_DIR/npmenu-off.xml" music_menu)"
assert_within "(3) off: the band reads the theme background right of its labels (worst px)" 0 "$(off_worst "$ROW_DIR/npmenu-off.png" 450 $(( NT + 10 )) $(( NR - 460 )) $(( NBOT - NT - 20 )))" 1
adb shell input keyevent KEYCODE_BACK
sleep 1
assert_eq "(3) battery saver off: awake" "Awake" "$(battery_saver_off)"
assert_eq "(3) battery saver off: low_power = 0" "0" "$(adb shell settings get global low_power | tr -d '\r')"

# ---- restore: stop playback, the playlists store as it was, Start
adb shell cmd media_session dispatch pause >/dev/null 2>&1
ring_save
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'if [ -f files/music_playlists.json.qa13 ]; then mv files/music_playlists.json.qa13 files/music_playlists.json; else rm -f files/music_playlists.json; fi'"
show_start 5
row_end
