#!/usr/bin/env bash
# Phase 13's edge cases (phase doc, "Edge cases"), one row per case so any one of them re-runs alone:
#   edge.sh <case>   ->  qa/phase-13/EDGE_<CASE>/
# Cases run here: saver_menu, bg_change, huge, no_image, light, edit_mode, screen_off, process_death, rv10, a11y,
# rapid, many_apps. The others are executed by the acceptance rows, exactly as the doc words them (qa/phase-13/README.md
# maps each): the picture's file deleted = E13; the toggle changed while the app list shows = E1; a surface over another
# acrylic surface = E4 (1); disable_window_blurs = E1; low-RAM = the JVM test; the burst = dropped (T11-8).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$(dirname "$0")/reminders_fixture.sh"
CASE="${1:?usage: edge.sh <case>}"
C_UP="$(echo "$CASE" | tr 'a-z' 'A-Z')"
BASE_RADIUS=90   # 30 epx at 3 px/epx

# ---------------------------------------------------------------- helpers shared by the cases
pid() { adb shell pidof $PKG | tr -d '\r' | awk '{print $1}'; }
pss() { # median of three total-PSS samples (KB), 1 s apart (E10's method)
  local p s=""
  p="$(pid)"
  for _ in 1 2 3; do
    s="$s $(adb shell dumpsys meminfo "$p" | grep -m1 'TOTAL PSS:' | awk '{print $3}' | tr -d '\r')"
    sleep 1
  done
  python3 -c "import statistics,sys; print(int(statistics.median(map(int, sys.argv[1:]))))" $s
}
warm() { show_start 3; to_app_list 2; to_start 2; sleep 2; }
toggle_to() { # true|false through Settings > Start + theme (in-process)
  open_transparency
  local now
  now="$(grep -oE 'resource-id="theme_transparency_effects"[^>]*checked="(true|false)"' "$SETTINGS_DUMP" | grep -oE 'checked="[a-z]*"' | cut -d'"' -f2)"
  [ "$now" = "$1" ] || tap_transparency
  leave_settings
}
theme_tap() { # tag on Start + theme
  adb shell am start -n $PKG/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
  scroll_to_node "$ROW_DIR/.theme.xml" "$1" 8
  tap_node "$ROW_DIR/.theme.xml" "$1"
}
# The system photo picker opened by theme_background_choose: tap the newest picture (E10's route).
pick_newest() {
  sleep 2.5
  adb shell input swipe 540 850 540 150 400; sleep 2
  dump_ui "$ROW_DIR/.picker.xml"
  local at
  at="$(python3 - "$ROW_DIR/.picker.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); best = None
for n in re.finditer(r"<node[^>]*>", s):
    n = n.group(0); d = re.search(r'content-desc="(Photo taken on [^"]*)"', n); b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if d and b:
        x1, y1, x2, y2 = map(int, b.groups())
        if best is None or (y1, x1) < best[0]: best = ((y1, x1), (x1 + x2) // 2, (y1 + y2) // 2)
print(f"{best[1]} {best[2]}" if best else "")
PY
)"
  assert_ne "the picker shows the pushed picture (newest)" "" "$at"
  # shellcheck disable=SC2086
  [ -n "$at" ] && adb shell input tap $at
  sleep 3
}
media_id() { echo "${1##*/}"; }
drop_picture() { # device path
  adb shell rm -f "$1"
  adb shell content call --uri content://media --method scan_file --arg "$1" >/dev/null 2>&1
}
# A flat picture, or a two-tone 4-across checker, at W x H (PIL rectangles, so 8000 x 8000 is quick).
make_png() { # out W H kind(flat:<v>|checker:<a>:<b>)
  python3 - "$@" <<'PY'
import sys
from PIL import Image, ImageDraw
out, W, H, kind = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4].split(":")
if kind[0] == "flat":
    Image.new("RGB", (W, H), (int(kind[1]),) * 3).save(out)
else:
    a, b = int(kind[1]), int(kind[2]); s = W // 4
    img = Image.new("RGB", (W, H), (a, a, a)); d = ImageDraw.Draw(img)
    for y in range(0, H, s):
        for x in range(0, W, s):
            if ((x // s) + (y // s)) % 2:
                d.rectangle([x, y, x + s - 1, y + s - 1], fill=(b, b, b))
    img.save(out)
PY
}
patch_mean() { python3 "$P13/edge.py" patch "$1" "$2" "$3" "$4" "$5" | cut -d' ' -f1; }   # png x y w h
strip_y() { applist_strip "$1"; }

row_begin "EDGE_$C_UP" "phase 13 edge case: $CASE"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"

case "$CASE" in
# ================================================================ battery saver toggled while a menu is open
saver_menu)
  set_pref transparency_effects boolean true
  show_start 6
  make_typed_reminder "remind me to check the QA13 edge list tomorrow at 9 am"
  open_reminders
  ID="$(reminder_id "QA13 edge")"
  assert_ne "a reminder row to hold" "" "$ID"
  set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row:$ID")
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000
  sleep 1.5
  dump_ui "$ROW_DIR/menu.xml"
  assert_eq "the reminder menu is open (acrylic:reminder_menu)" "yes" "$(has_node "$ROW_DIR/menu.xml" acrylic:reminder_menu)"
  set -- $(bounds "$ROW_DIR/menu.xml" reminder_menu_complete); IR=$3; CY=$(( $4 - 66 ))
  screencap "$ROW_DIR/menu-on.png"
  battery_saver_on
  sleep 1.5
  ring_since "$BS_MARK" > "$ROW_DIR/slice-saver-on.txt"
  A="$(wall_of "[fluent] acrylic=off reason=battery-saver" < "$ROW_DIR/slice-saver-on.txt")"
  B="$(wall_of "[fluent] reminder_menu form=fallback" < "$ROW_DIR/slice-saver-on.txt")"
  note "acrylic=off wall=$A, reminder_menu form=fallback wall=$B"
  assert_ne "the acrylic=off reason=battery-saver line" "" "$A"
  assert_ne "the reminder_menu form=fallback line" "" "$B"
  assert_within "the menu's fallback line within 2 frames of acrylic=off (0..34 ms)" 17 "$(( ${B:-0} - ${A:-99999} ))" 17
  screencap "$ROW_DIR/menu-saver.png"
  record "corroboration: the menu's interior under battery saver (10x10 mean; (40,40,40) expected)" "$(patch_mean "$ROW_DIR/menu-saver.png" $(( IR - 60 )) $(( CY - 5 )) 10 10)"
  dump_ui "$ROW_DIR/menu-saver.xml"
  assert_eq "the menu stayed open under battery saver" "yes" "$(has_node "$ROW_DIR/menu-saver.xml" acrylic:reminder_menu)"
  MARK="$(ring_mark)"
  assert_eq "battery_saver_off: awake" "Awake" "$(battery_saver_off)"
  sleep 1.5
  ring_since "$MARK" > "$ROW_DIR/slice-saver-off.txt"
  assert_contains "toggled back: [fluent] reminder_menu form=acrylic" "[fluent] reminder_menu form=acrylic" "$(cat "$ROW_DIR/slice-saver-off.txt")"
  dump_ui "$ROW_DIR/menu-back.xml"
  assert_eq "toggled back: the menu is still open (acrylic:reminder_menu)" "yes" "$(has_node "$ROW_DIR/menu-back.xml" acrylic:reminder_menu)"
  screencap "$ROW_DIR/menu-back.png"
  adb shell input tap 1000 1500; sleep 1.2
  open_reminders
  set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row:$ID")
  delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  open_reminders
  assert_eq "restore: no QA13 reminder left" "" "$(reminder_id "QA13")"
  cortana_close
  ;;

# ================================================================ the Start background changed, then removed, while the app list shows
bg_change)
  set_pref transparency_effects boolean true
  set_checker
  show_start 7
  to_app_list 3
  dump_ui "$ROW_DIR/applist.xml"
  Y="$(strip_y "$ROW_DIR/applist.xml")"
  assert_ne "a text-free strip on a square-centre row" "" "$Y"
  screencap "$ROW_DIR/checker.png"
  read -r W _ _ <<< "$(applist_edge "$ROW_DIR/checker.png" "$Y")"
  assert_within "control: the checker shows blurred on the app list" 134.5 "$W" 26.9
  make_png "$ROW_DIR/grey.png" 1080 2340 flat:128
  GREY="$(push_picture "$ROW_DIR/grey.png" /sdcard/Pictures/qa13-grey.png)"
  assert_ne "the grey picture is in MediaStore" "" "$GREY"
  # changed: Settings opened over the app list, the grey picture chosen, Back
  MARK="$(ring_mark)"
  theme_tap theme_background_choose
  pick_newest
  leave_settings
  sleep 2
  ring_since "$MARK" > "$ROW_DIR/slice-changed.txt"
  dump_ui "$ROW_DIR/applist-grey.xml"
  assert_eq "changed: back on the app list" "yes" "$(has_node "$ROW_DIR/applist-grey.xml" app_list)"
  # The picker hands the shell its own URI for the picture (content://media/picker/.../media/<id>), not the MediaStore
  # one push_picture printed: the media id is what they share (the first run's FAIL, EDGE_BG_CHANGE-run1-uri-form/).
  assert_contains "changed: the static layer is rebuilt for the new picture (media $(media_id "$GREY"))" "/media/$(media_id "$GREY") in " "$(grep -F 'static backdrop rebuilt for' "$ROW_DIR/slice-changed.txt")"
  screencap "$ROW_DIR/grey-applist.png"
  # Patches inside E2's strip (x 675..945), either side of the old checker's x = 810 edge: x 60 lay on the rows' icons.
  for x in 685 760 850 925; do
    assert_within "changed: patch at x=$x reads 0.2 x 128 (no stale checker)" 25.6 "$(patch_mean "$ROW_DIR/grey-applist.png" $x $(( Y - 5 )) 10 10)" 3
  done
  read -r W A B <<< "$(applist_edge "$ROW_DIR/grey-applist.png" "$Y")"
  note "changed: probe across x=810: width=$W plateaus=$A/$B"
  assert_eq "changed: no checker edge left at x=810 (plateaus within 1 level)" yes "$(python3 -c "print('yes' if abs($A-$B) <= 1 else 'no')")"
  # removed
  MARK="$(ring_mark)"
  theme_tap theme_background_remove
  sleep 1.5
  leave_settings
  sleep 2
  ring_since "$MARK" > "$ROW_DIR/slice-removed.txt"
  dump_ui "$ROW_DIR/applist-none.xml"
  assert_eq "removed: back on the app list" "yes" "$(has_node "$ROW_DIR/applist-none.xml" app_list)"
  assert_absent "removed: no static backdrop rebuilt line" "static backdrop rebuilt" "$(cat "$ROW_DIR/slice-removed.txt")"
  assert_absent "removed: the background key is gone" 'name="background"' "$(adb shell run-as $PKG cat shared_prefs/start_theme.xml)"
  screencap "$ROW_DIR/none-applist.png"
  assert_within "removed: the strip reads the theme background (0,0,0) (worst px, E2's strip x 675..945, 9 rows)" 0 \
    "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/none-applist.png" 675 $(( Y - 4 )) 271 9 0,0,0)" 2
  to_start 2
  drop_picture /sdcard/Pictures/qa13-grey.png
  clear_background
  ;;

# ================================================================ an 8000 x 8000 background
huge)
  set_pref transparency_effects boolean true
  clear_background
  show_start 7
  P0="$(pid)"
  toggle_to false
  make_png "$ROW_DIR/huge.png" 8000 8000 checker:0:255
  HUGE="$(push_picture "$ROW_DIR/huge.png" /sdcard/Pictures/qa13-huge.png)"
  assert_ne "the 8000 x 8000 picture is in MediaStore" "" "$HUGE"
  theme_tap theme_background_choose
  pick_newest
  leave_settings
  assert_contains "the Start background is the huge picture" "$(media_id "$HUGE")" "$(adb shell run-as $PKG cat shared_prefs/start_theme.xml | grep 'name="background"')"
  warm
  B="$(pss)"; record "B: the huge picture, acrylic off (KB)" "$B"
  MARK="$(ring_mark)"
  toggle_to true
  for _ in $(seq 1 40); do ring_since "$MARK" | grep -q 'static backdrop rebuilt' && break; sleep 0.5; done
  ring_since "$MARK" > "$ROW_DIR/slice-rebuilt.txt"
  assert_contains "the pre-blur ran: static backdrop rebuilt for the huge picture (media $(media_id "$HUGE"), the picker's URI)" "/media/$(media_id "$HUGE") in " "$(grep -F 'static backdrop rebuilt for' "$ROW_DIR/slice-rebuilt.txt")"
  record "rebuild time" "$(grep -F 'static backdrop rebuilt' "$ROW_DIR/slice-rebuilt.txt" | sed 's/.* in //' | head -1)"
  warm
  C="$(pss)"; record "C: acrylic on (KB)" "$C"
  assert_eq "C - B <= 14 MB (E10's C bound): $(( C - B )) KB" yes "$( [ $(( C - B )) -le 14336 ] && echo yes || echo no)"
  assert_eq "the launcher's pid is unchanged (no crash, no restart)" "$P0" "$(pid)"
  to_app_list 3
  screencap "$ROW_DIR/huge-applist.png"
  to_start 2
  theme_tap theme_background_remove; sleep 1.5; leave_settings
  drop_picture /sdcard/Pictures/qa13-huge.png
  ;;

# ================================================================ no background image set
no_image)
  set_pref transparency_effects boolean true
  # The MARK goes before clear_background: its force-stop restarts the Home app, whose start is what the absence
  # clauses are about (gate review B, N6).
  MARK="$(ring_mark)"
  clear_background
  show_start 7
  to_app_list 3
  ring_since "$MARK" > "$ROW_DIR/slice.txt"
  dump_ui "$ROW_DIR/applist.xml"
  assert_eq "the app list shows" "yes" "$(has_node "$ROW_DIR/applist.xml" app_list)"
  assert_absent "no static layer: no static backdrop rebuilt line" "static backdrop rebuilt" "$(cat "$ROW_DIR/slice.txt")"
  assert_absent "and no failure line (there is no picture to fail)" "static backdrop failed" "$(cat "$ROW_DIR/slice.txt")"
  Y="$(strip_y "$ROW_DIR/applist.xml")"
  screencap "$ROW_DIR/applist.png"
  assert_within "the solid theme background (0,0,0) (worst px, E2's strip x 675..945, 9 rows)" 0 \
    "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/applist.png" 675 $(( Y - 4 )) 271 9 0,0,0)" 2
  record "E10's memory figure for this state is its A (qa/phase-13/E10)" "see E10"
  to_start 2
  ;;

# ================================================================ Light theme over a bright wallpaper
light)
  set_pref transparency_effects boolean true
  THEME0="$(adb shell run-as $PKG cat shared_prefs/start_theme.xml | grep -o 'name="theme">[A-Z]*' | sed 's/.*>//')"
  note "theme before: ${THEME0:-(unset, DARK)}"
  make_png "$ROW_DIR/bright.png" 1080 2340 checker:190:255
  BRIGHT="$(push_picture "$ROW_DIR/bright.png" /sdcard/Pictures/qa13-bright.png)"
  set_pref background string "$BRIGHT"
  set_pref theme string LIGHT
  MARK="$(ring_mark)"
  show_start 7
  to_app_list 3
  ring_since "$MARK" > "$ROW_DIR/slice.txt"
  SHOW="$(grep -F '[fluent] applist source=' "$ROW_DIR/slice.txt" | tail -1)"
  note "show line: ${SHOW#*\[fluent\] }"
  assert_contains "the app list is acrylic (static, alpha 0.8, 30 epx)" "alpha=0.8 blur=30epx" "$SHOW"
  assert_contains "the light theme's tint is its background, white (Palette.lightBackground; T = F for the app list)" "tint=(255,255,255)" "$SHOW"
  dump_ui "$ROW_DIR/applist.xml"
  screencap "$ROW_DIR/applist.png"
  Y="$(strip_y "$ROW_DIR/applist.xml")"
  for x in 685 760 850 925; do
    assert_eq "the backdrop is light at x=$x (patch mean >= 200)" yes "$(python3 -c "print('yes' if $(patch_mean "$ROW_DIR/applist.png" $x $(( Y - 5 )) 10 10) >= 200 else 'no')")"
  done
  ROWB="$(python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/applist.xml" | grep -m1 '^Auxio|' | cut -d'|' -f2)"
  read -r l t r b <<< "$ROWB"
  DARK="$(python3 -c "
from PIL import Image
im = Image.open('$ROW_DIR/applist.png').convert('L')
print(min(im.getpixel((x, y)) for x in range($l, $r) for y in range($t, $b)))")"
  assert_eq "an app name's text is dark on it (darkest text px <= 80, got $DARK)" yes "$(python3 -c "print('yes' if $DARK <= 80 else 'no')")"
  note "H1: the light-theme capture is applist.png"
  to_start 2
  if [ -n "$THEME0" ]; then set_pref theme string "$THEME0"; else set_pref theme string --remove; fi
  clear_background
  drop_picture /sdcard/Pictures/qa13-bright.png
  ;;

# ================================================================ edit mode: the pivot is locked, the static layer is not rebuilt
edit_mode)
  . "$P13/../../phase-11/scripts/q.sh"
  set_pref transparency_effects boolean true
  set_checker
  show_start 7
  layout_save "$ROW_DIR/layout-before.json"
  ensure_start_page
  qdump "$ROW_DIR/start.xml"
  read -r TX TY <<< "$(center "$ROW_DIR/start.xml" tile:shell:settings)"
  assert_ne "a tile to hold" "" "${TX:-}"
  MARK="$(ring_mark)"
  hold "$TX" "$TY" 1.2
  sleep 1
  assert_contains "edit mode on" "edit mode on" "$(ring_since "$MARK" | grep -F '[edit]')"
  to_app_list 2
  qdump "$ROW_DIR/edit-swiped.xml"
  assert_eq "the pivot is locked in edit mode: the app list does not show" "no" "$(has_node "$ROW_DIR/edit-swiped.xml" app_list)"
  # Leave the way the product leaves (EditGestures: "tap elsewhere with a burst open: exit"): a tap in Start's empty
  # gap between the grid and the bottom row. Back only closes the burst and keeps edit mode (the first run, kept in
  # EDGE_EDIT_MODE-run1-back-kept-edit/).
  GAP="$(python3 "$P13/dumpq.py" start_gap "$ROW_DIR/start.xml" | cut -d' ' -f1)"
  assert_ne "an empty gap on Start to tap" "" "$GAP"
  MARK2="$(ring_mark)"
  adb shell input tap 540 "$GAP"; sleep 1.5
  assert_contains "edit mode exits on the tap elsewhere" ": exit" "$(ring_since "$MARK2" | grep -F '[edit]')"
  qdump "$ROW_DIR/after.xml"
  assert_eq "edit mode left (no edit discs)" "0" "$(grep -c 'resource-id="edit_disc:' "$ROW_DIR/after.xml")"
  to_app_list 3
  dump_ui "$ROW_DIR/applist.xml"
  assert_eq "after leaving, the app list shows" "yes" "$(has_node "$ROW_DIR/applist.xml" app_list)"
  ring_since "$MARK" > "$ROW_DIR/slice.txt"
  assert_absent "entering and leaving edit mode did not rebuild the static layer" "static backdrop rebuilt" "$(cat "$ROW_DIR/slice.txt")"
  to_start 2
  layout_save "$ROW_DIR/layout-after.json"
  assert_eq "the layout is unchanged" "$(md5sum < "$ROW_DIR/layout-before.json")" "$(md5sum < "$ROW_DIR/layout-after.json")"
  clear_background
  ;;

# ================================================================ screen off while a menu shows, then wake
screen_off)
  set_pref transparency_effects boolean true
  show_start 6
  reminders_setup
  PH="$(bounds "$ROW_DIR/reminders.xml" "reminder_row_photo:$REM_PHOTO")"
  assert_ne "the photo row's photo is on the page" "" "$PH"
  # No photo -> no place to hold: restore and stop here rather than let an empty bound abort the row before its
  # restore (the first run, EDGE_SCREEN_OFF-run1-photo-tag/, left the fixture on the device that way).
  if [ -z "$PH" ]; then reminders_restore; show_start 3; row_end; exit 1; fi
  set -- $PH; HX=$(( ($1 + $3) / 2 )); HY=$(( $4 - 10 ))
  adb shell input swipe $HX $HY $HX $HY 1000
  sleep 1.5
  dump_ui "$ROW_DIR/menu.xml"
  MB="$(bounds "$ROW_DIR/menu.xml" acrylic:reminder_menu)"
  assert_ne "the reminder menu is open over the photo" "" "$MB"
  screencap "$ROW_DIR/before.png"
  set -- $MB; ML=$1; MT=$2; MR=$3; MBOT=$4
  PTS="$(( MR - 60 )),$(( MBOT - 30 )) $(( (ML + MR) / 2 + 60 )),$(( MBOT - 30 )) $(( MR - 60 )),$(( (MT + MBOT) / 2 ))"
  adb shell input keyevent KEYCODE_SLEEP; sleep 3
  record "screen state after KEYCODE_SLEEP" "$(adb shell dumpsys power | grep -m1 'mWakefulness=' | tr -d '\r ')"
  assert_eq "wake_device: awake again (C-25)" "Awake" "$(wake_device)"
  sleep 2
  dump_ui "$ROW_DIR/after.xml"
  screencap "$ROW_DIR/after.png"
  assert_eq "the menu is still open after wake" "yes" "$(has_node "$ROW_DIR/after.xml" acrylic:reminder_menu)"
  for p in $PTS; do
    x="${p%,*}"; y="${p#*,}"
    bv="$(patch_mean "$ROW_DIR/before.png" $x $y 10 10)"; av="$(patch_mean "$ROW_DIR/after.png" $x $y 10 10)"
    note "patch ($x,$y): before $bv after $av"
    assert_within "the menu draws as before sleep at ($x,$y) (patch mean, not black)" "$bv" "$av" 4
  done
  assert_within "the menu carries the material's noise after wake (std of 40x40, 1..4)" 2.5 "$(python3 "$P13/edge.py" std "$ROW_DIR/after.png" $(( MR - 80 )) $(( (MT + MBOT) / 2 - 20 )) 40 40)" 1.5
  adb shell input tap 1000 1500; sleep 1.2
  reminders_restore
  ;;

# ================================================================ process death while a menu shows
process_death)
  set_pref transparency_effects boolean true
  show_start 6
  toggle_to false
  assert_eq "the switch is saved Off" "false" "$(transparency_state)"
  show_start 4
  to_app_list 3
  dump_ui "$ROW_DIR/applist.xml"
  ROWID="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/applist.xml" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
  set -- $(bounds "$ROW_DIR/applist.xml" "$ROWID")
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000
  sleep 1.5
  dump_ui "$ROW_DIR/band.xml"
  assert_eq "the band is open" "yes" "$(has_node "$ROW_DIR/band.xml" applist_menu)"
  P0="$(pid)"
  ring_save
  adb shell run-as $PKG kill -9 "$P0"
  sleep 2
  adb shell input keyevent KEYCODE_HOME; sleep 6
  P1="$(pid)"
  note "pid before $P0, after $P1"
  assert_ne "the process died and a new one runs" "$P0" "$P1"
  assert_ne "a launcher process is running" "" "$P1"
  dump_ui "$ROW_DIR/after.xml"
  assert_eq "no menu is restored" "no" "$(has_node "$ROW_DIR/after.xml" applist_menu)"
  assert_eq "the toggle survived: the prefs hold false" "false" "$(transparency_state)"
  open_transparency
  cp "$SETTINGS_DUMP" "$ROW_DIR/settings-after.xml"
  assert_contains "the toggle survived: the switch reads Off" 'checked="false"' "$(grep -oE 'resource-id="theme_transparency_effects"[^>]*' "$SETTINGS_DUMP")"
  leave_settings
  toggle_to true
  assert_eq "restore: the switch is On" "true" "$(transparency_state)"
  ;;

# ================================================================ RV10: display size, density and font scale
rv10)
  set_pref transparency_effects boolean true
  base_band=""
  one() { # tag W H radius_px
    local tag="$1" W="$2" H="$3" R="$4" exp rows s xa xb y mark ab
    exp="$(python3 "$P13/edge.py" expected_width "$R")"
    set_checker "$W" "$H"
    show_start 7
    mark="$(ring_mark)"
    to_app_list 3
    ring_since "$mark" > "$ROW_DIR/slice-$tag.txt"
    assert_contains "$tag: the show line (30 epx whatever the display)" "[fluent] applist source=static tint=(0,0,0) alpha=0.8 blur=30epx" "$(cat "$ROW_DIR/slice-$tag.txt")"
    dump_ui "$ROW_DIR/applist-$tag.xml"
    screencap "$ROW_DIR/applist-$tag.png"
    rows="$(python3 "$P13/dumpq.py" checker_rows "$ROW_DIR/applist-$tag.xml" "$W" "$H")"
    s=$(( W / 4 )); xa=$(( 2 * s + s / 2 )); xb=$(( 3 * s + s / 2 ))
    y="$(python3 "$P13/dumpq.py" clear_rows "$ROW_DIR/applist-$tag.xml" "$xa" "$xb" $rows)"
    assert_ne "$tag: a text-free strip" "" "$y"
    read -r EW _ _ <<< "$(python3 "$P13/edge.py" edge "$ROW_DIR/applist-$tag.png" $(( y - 4 )) $(( y + 4 )) "$xa" "$xb")"
    assert_within "$tag: the edge spread follows px/epx (r = $R px)" "$exp" "$EW" "$(python3 -c "print(round($exp*0.2,1))")"
    # a live surface's bounds follow phase 01 E3's rule (epx): the band's height in epx
    local rid
    rid="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/applist-$tag.xml" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
    set -- $(bounds "$ROW_DIR/applist-$tag.xml" "$rid")
    adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000
    sleep 1.5
    dump_ui "$ROW_DIR/band-$tag.xml"
    ab="$(bounds "$ROW_DIR/band-$tag.xml" applist_menu)"
    assert_ne "$tag: the band opens" "" "$ab"
    set -- $ab
    local hepx
    hepx="$(python3 -c "print(round(($4 - $2) / ($W / 360), 2))")"
    note "$tag: band [$ab] = $hepx epx tall"
    if [ -z "$base_band" ]; then base_band="$hepx"; else
      assert_within "$tag: the band's height in epx equals the base's ($base_band)" "$base_band" "$hepx" 1.4
    fi
    adb shell input keyevent KEYCODE_BACK; sleep 1
    to_start 2
    ring_save
  }
  one base 1080 2340 $BASE_RADIUS
  adb shell wm size 1440x3120; sleep 4
  one size1440 1440 3120 120
  adb shell wm size reset; sleep 4
  adb shell wm density 420; sleep 4
  one d420 1080 2340 $BASE_RADIUS
  adb shell wm density 560; sleep 4
  one d560 1080 2340 $BASE_RADIUS
  adb shell wm density reset; sleep 4
  adb shell settings put system font_scale 1.3; sleep 4
  one font13 1080 2340 $BASE_RADIUS
  adb shell settings put system font_scale 1.0; sleep 3
  assert_contains "restore: wm size" "Physical size: 1080x2340" "$(adb shell wm size)"
  assert_absent "restore: no size override" "Override size" "$(adb shell wm size)"
  assert_absent "restore: no density override" "Override density" "$(adb shell wm density)"
  assert_eq "restore: font_scale 1.0" "1.0" "$(adb shell settings get system font_scale | tr -d '\r')"
  clear_background
  ;;

# ================================================================ accessibility settings
a11y)
  set_pref transparency_effects boolean true
  set_checker
  show_start 7
  to_app_list 3
  dump_ui "$ROW_DIR/applist.xml"
  Y="$(strip_y "$ROW_DIR/applist.xml")"
  to_start 2
  a11y_one() { # tag setting-namespace key on-value off-value
    local tag="$1" ns="$2" key="$3" on="$4" off="$5" W
    adb shell settings put "$ns" "$key" "$on"; sleep 3
    to_app_list 3
    dump_ui "$ROW_DIR/applist-$tag.xml"
    screencap "$ROW_DIR/applist-$tag.png"
    assert_eq "$tag: the app list shows" "yes" "$(has_node "$ROW_DIR/applist-$tag.xml" app_list)"
    assert_eq "$tag: acrylic:applist is there" "yes" "$(has_node "$ROW_DIR/applist-$tag.xml" acrylic:applist)"
    read -r W _ _ <<< "$(applist_edge "$ROW_DIR/applist-$tag.png" "$Y")"
    record "$tag: edge spread in the capture (the blur, as screencap sees it)" "$W"
    to_start 2
    adb shell settings put "$ns" "$key" "$off"; sleep 2
    assert_eq "$tag: restored" "$off" "$(adb shell settings get "$ns" "$key" | tr -d '\r')"
  }
  A0="$(adb shell settings get global animator_duration_scale | tr -d '\r')"; [ "$A0" = null ] && A0=1.0
  MARK="$(ring_mark)"
  adb shell settings put global animator_duration_scale 0; sleep 2
  assert_absent "Remove animations: acrylic stays on (no acrylic=off line)" "acrylic=off" "$(ring_since "$MARK")"
  to_app_list 3
  screencap "$ROW_DIR/applist-noanim.png"
  read -r W _ _ <<< "$(applist_edge "$ROW_DIR/applist-noanim.png" "$Y")"
  assert_within "Remove animations: the app list is still blurred" 134.5 "$W" 26.9
  to_start 2
  adb shell settings put global animator_duration_scale "$A0"; sleep 2
  a11y_one hightext secure high_text_contrast_enabled 1 0
  a11y_one inversion secure accessibility_display_inversion_enabled 1 0
  note "H1: applist-hightext.png and applist-inversion.png are the screencaps the doc asks for"
  clear_background
  ;;

# ================================================================ rapid open / close: no leaked live layers
rapid)
  set_pref transparency_effects boolean true
  set_checker
  show_start 7
  P0="$(pid)"
  warm
  to_app_list 3
  dump_ui "$ROW_DIR/applist.xml"
  ROWID="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/applist.xml" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
  set -- $(bounds "$ROW_DIR/applist.xml" "$ROWID"); HX=$(( ($1 + $3) / 2 )); HY=$(( ($2 + $4) / 2 ))
  to_start 2
  C="$(pss)"; record "C: the checker, acrylic on (KB)" "$C"
  to_app_list 2
  # Each hold must be shown to open the band (gate review B, B1: the first cut asserted only time and PSS, which pass
  # just as well if no live layer was ever allocated). The slice is saved before anything force-stops the shell.
  MARK="$(ring_mark)"
  T0="$(date +%s%3N)"
  for _ in $(seq 1 10); do
    adb shell input swipe $HX $HY $HX $HY 850
    adb shell input keyevent KEYCODE_BACK
  done
  T1="$(date +%s%3N)"
  ring_since "$MARK" > "$ROW_DIR/slice-holds.txt"
  assert_eq "each of the 10 holds opened the band (10 applist_menu show lines)" "10" "$(grep -cF '[fluent] applist_menu source=live' "$ROW_DIR/slice-holds.txt")"
  record "10 holds took (ms, host clock)" "$(( T1 - T0 ))"
  assert_eq "10 holds in <= 15 s" yes "$( [ $(( T1 - T0 )) -le 15000 ] && echo yes || echo no)"
  sleep 3
  to_start 2
  sleep 2
  A="$(pss)"; record "after the 10 holds (KB)" "$A"
  assert_within "PSS returns within 2 MB of C (KB)" 0 "$(( A - C ))" 2048
  assert_eq "one process throughout" "$P0" "$(pid)"
  clear_background
  ;;

# ================================================================ 300+ packages
many_apps)
  BULK="$ROW_DIR/bulk"; mkdir -p "$BULK"
  AAPT2="$(ls -d "$HOME"/Android/Sdk/build-tools/*/ | sort -V | tail -1)aapt2"
  SIGNER="$(ls -d "$HOME"/Android/Sdk/build-tools/*/ | sort -V | tail -1)apksigner"
  JAR="$(ls -d "$HOME"/Android/Sdk/platforms/android-*/ | sort -V | tail -1)android.jar"
  KS="$HOME/.android/debug.keystore"
  N0="$(adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --brief | grep -c '/')"
  note "launcher entries before: $N0"
  ok=0
  for i in $(seq -w 1 300); do
    d="$BULK/p$i"; mkdir -p "$d"
    cat > "$d/AndroidManifest.xml" <<XML
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="qa.bulk13.p$i">
  <application android:label="Bulk Test $i" android:hasCode="false">
    <activity android:name="android.app.Activity" android:exported="true">
      <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity>
  </application>
</manifest>
XML
    # API 36 refuses a package that targets below SDK 24 (INSTALL_FAILED_DEPRECATED_SDK_VERSION: the first run
    # installed 0 of 300, EDGE_MANY_APPS-run1-sdk0/).
    "$AAPT2" link -o "$d/u.apk" -I "$JAR" --manifest "$d/AndroidManifest.xml" --min-sdk-version 24 --target-sdk-version 34 >/dev/null 2>&1 \
      && "$SIGNER" sign --ks "$KS" --ks-pass pass:android --out "$d/s.apk" "$d/u.apk" >/dev/null 2>&1 \
      && adb install -r "$d/s.apk" >/dev/null 2>&1 && ok=$((ok + 1))
    rm -f "$d/u.apk"
  done
  record "bulk APKs installed" "$ok / 300"
  N1="$(adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --brief | grep -c '/')"
  assert_eq "300+ launcher entries" yes "$( [ "$N1" -ge 300 ] && echo yes || echo no)"
  record "launcher entries now" "$N1"
  set_pref transparency_effects boolean true
  # The MARK goes before set_checker: its force-stop restarts the Home app, which builds the layer at once (BS_STEP's
  # lesson; the first run read 0 rebuilt lines this way).
  MARK="$(ring_mark)"
  set_checker
  show_start 7
  to_app_list 4
  dump_ui "$ROW_DIR/top.xml"
  screencap "$ROW_DIR/top.png"
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  for _ in $(seq 1 8); do adb shell input swipe 540 1900 540 700 400; sleep 0.8; done
  adb shell dumpsys gfxinfo $PKG > "$ROW_DIR/gfxinfo-scroll.txt"
  screencap "$ROW_DIR/scrolled.png"
  ring_since "$MARK" > "$ROW_DIR/slice.txt"
  assert_eq "one static layer whatever the list's length: exactly one rebuilt line" "1" "$(grep -c 'static backdrop rebuilt' "$ROW_DIR/slice.txt")"
  # The backdrop is the page's, not the list's: the right margin (x 1050..1075, no row text) is the same after
  # scrolling, column means over the page height.
  D="$(python3 - "$ROW_DIR/top.png" "$ROW_DIR/scrolled.png" <<'PY'
import sys
from PIL import Image
a, b = (Image.open(p).convert("L") for p in sys.argv[1:3])
worst = 0
for y in range(200, 2100, 20):
    ma = sum(a.getpixel((x, yy)) for x in range(1050, 1076) for yy in range(y, y + 10)) / 260
    mb = sum(b.getpixel((x, yy)) for x in range(1050, 1076) for yy in range(y, y + 10)) / 260
    worst = max(worst, abs(ma - mb))
print(round(worst, 2))
PY
)"
  assert_within "the backdrop did not move with the scroll (right margin, worst patch |diff|)" 0 "$D" 3
  record "scroll janky frames" "$(grep -m1 'Janky frames:' "$ROW_DIR/gfxinfo-scroll.txt" | awk -F': ' '{print $2}' | tr -d '\r')"
  record "scroll 99th percentile" "$(grep -m1 '99th percentile' "$ROW_DIR/gfxinfo-scroll.txt" | awk -F': ' '{print $2}' | tr -d '\r')"
  to_start 2
  gone=0
  for i in $(seq -w 1 300); do adb uninstall "qa.bulk13.p$i" >/dev/null 2>&1 && gone=$((gone + 1)); done
  record "restore: bulk APKs uninstalled" "$gone"
  assert_eq "restore: launcher entries back to $N0" "$N0" "$(adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --brief | grep -c '/')"
  clear_background
  ;;

*) echo "edge.sh: unknown case $CASE" >&2; exit 2 ;;
esac
show_start 3
row_end
