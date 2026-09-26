#!/usr/bin/env bash
# E5 The ≡ pane (phase 13 Acceptance E5, T13-2, T13-17, T13-24): over Cortana's Home page it reads its measured (14,19,13); over
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

# ================================================================ (e) live: the pane over Tess listening (T13-24)
# Jeremy allowed the microphone for this (2026-09-25, "You can use the mic now"). Listening with nothing said lasts about
# 2 s before Tess ends it as silence, so each sequence runs on the device in ONE shell, every step stamped with the device
# clock, and the row asserts the captures ended before the ring's `[speech] final` line (she was still listening).
PERSONA_C=""
tl() { grep -m1 "^$2=" "$ROW_DIR/live-$1-timeline.txt" | cut -d= -f2 | tr -d '\r'; }
final_wall() { grep -F '[speech] final' "$ROW_DIR/slice-live-$1.txt" | grep -oE 'wall=[0-9]+' | head -1 | cut -d= -f2; }
fetch_raw() { # device name -> png
  adb pull "/sdcard/$1.raw" "$ROW_DIR/.$1.raw" >/dev/null 2>&1
  python3 "$P13/acrylic_check.py" raw2png "$ROW_DIR/.$1.raw" "$2"
  rm -f "$ROW_DIR/.$1.raw"; adb shell rm -f "/sdcard/$1.raw"
}
tess_home() { # out.xml; sets MX MY BX BY
  local out="$1"
  ensure_start; cortana_assist; sleep 3
  dump_ui "$out"
  set -- $(bounds "$out" cortana_text_box_mic); MX=$(( ($1 + $3) / 2 )); MY=$(( ($2 + $4) / 2 ))
  set -- $(bounds "$out" cortana_menu_button); BX=$(( ($1 + $3) / 2 )); BY=$(( ($2 + $4) / 2 ))
}
# (e1) the persona pulses while she listens, and its bounds lie under the pane. Two listens: a dump 0.3 s after the mic
# tap (a dump takes ~2 s, so it must start first), then two raw captures ~200 ms apart without the pane.
tess_home "$ROW_DIR/live-home.xml"
LMARK="$(ring_mark)"
adb shell "echo mic=\$(date +%s%3N); input tap $MX $MY; sleep 0.3; echo dump=\$(date +%s%3N); uiautomator dump /sdcard/qa13-listen.xml >/dev/null; echo dumped=\$(date +%s%3N)" > "$ROW_DIR/live-dump-timeline.txt"
adb shell cat /sdcard/qa13-listen.xml > "$ROW_DIR/live-persona.xml"
sleep 4
cortana_close
PERS="$(bounds "$ROW_DIR/live-persona.xml" cortana_persona_large_listening)"
note "(e1) dump timeline: $(tr '\n' ' ' < "$ROW_DIR/live-dump-timeline.txt"); listening persona [$PERS]"
assert_ne "(e1) the dump caught the listening persona" "" "$PERS"
set -- $(bounds "$ROW_DIR/pane-home.xml" cortana_pane); PANE_R=$3
set -- ${PERS:-0 0 0 0}
assert_eq "(e1) the listening persona's bounds [$PERS] lie under the pane (x 0..$PANE_R)" yes "$( [ -n "$PERS" ] && [ $1 -ge 0 ] && [ $3 -le $PANE_R ] && echo yes || echo no)"
PERSONA_C="$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
PBOX="$1 $2 $3 $4"
tess_home "$ROW_DIR/live-home2.xml"
LMARK="$(ring_mark)"
adb shell "echo mic=\$(date +%s%3N); input tap $MX $MY; sleep 0.3; echo c1=\$(date +%s%3N); screencap /sdcard/qa13-p1.raw; echo c1done=\$(date +%s%3N); sleep 0.2; echo c2=\$(date +%s%3N); screencap /sdcard/qa13-p2.raw; echo c2done=\$(date +%s%3N)" > "$ROW_DIR/live-persona-timeline.txt"
sleep 3
ring_since "$LMARK" > "$ROW_DIR/slice-live-persona.txt"
fetch_raw qa13-p1 "$ROW_DIR/live-persona-1.png"; fetch_raw qa13-p2 "$ROW_DIR/live-persona-2.png"
cortana_close
F="$(final_wall persona)"
note "(e1) capture timeline: $(tr '\n' ' ' < "$ROW_DIR/live-persona-timeline.txt"); final at ${F:-none}"
assert_eq "(e1) the captures ended while she listened (c2done < the [speech] final line)" yes "$( [ -n "$F" ] && [ "$(tl persona c2done)" -lt "$F" ] && echo yes || echo no)"
PMAX="$(python3 -c "
import numpy as np; from PIL import Image
a=np.asarray(Image.open('$ROW_DIR/live-persona-1.png').convert('RGB'),float); b=np.asarray(Image.open('$ROW_DIR/live-persona-2.png').convert('RGB'),float)
l,t,r,bb=map(int,'$PBOX'.split()); print(int(np.abs(a[t:bb,l:r]-b[t:bb,l:r]).max()) if r > l else -1)")"
assert_eq "(e1) the persona pulses: its box differs between the two captures (max per-pixel |d| = $PMAX >= 20)" yes "$( [ "$PMAX" -ge 20 ] && echo yes || echo no)"

# (e2) acrylic on: the pane opened over her while she listens; two captures ~200 ms apart differ inside the pane over the
# persona (patch-mean |d| >= 2 levels in some channel, the 10x10 patch at the persona's centre, fixed before the run).
live_pane() { # tag — up to 3 attempts; an attempt whose second capture ends after listening ended (a host stall) is
  # recorded and retaken (the method's timing, not the product's); the first attempt inside the window is the one judged.
  local tag="$1" try f
  LIVE_OK=""
  for try in 1 2 3; do
    tess_home "$ROW_DIR/live-$tag-home.xml"
    LMARK="$(ring_mark)"
    adb shell "echo mic=\$(date +%s%3N); input tap $MX $MY; input tap $BX $BY; echo tapped=\$(date +%s%3N); sleep 0.35; echo c1=\$(date +%s%3N); screencap /sdcard/qa13-l1.raw; echo c1done=\$(date +%s%3N); sleep 0.2; echo c2=\$(date +%s%3N); screencap /sdcard/qa13-l2.raw; echo c2done=\$(date +%s%3N)" > "$ROW_DIR/live-$tag-timeline.txt"
    dump_ui "$ROW_DIR/live-$tag-after.xml"
    sleep 3
    ring_since "$LMARK" > "$ROW_DIR/slice-live-$tag.txt"
    fetch_raw qa13-l1 "$ROW_DIR/live-$tag-1.png"; fetch_raw qa13-l2 "$ROW_DIR/live-$tag-2.png"
    adb shell input keyevent KEYCODE_BACK; sleep 1; cortana_close
    f="$(final_wall "$tag")"
    note "($tag try $try) timeline: $(tr '\n' ' ' < "$ROW_DIR/live-$tag-timeline.txt"); final at ${f:-none}"
    if [ -n "$f" ] && [ "$(tl "$tag" c2done)" -lt "$f" ]; then LIVE_OK=yes; break; fi
    record "($tag try $try) retaken: the second capture ended after listening ended (host stall)" "c2done $(tl "$tag" c2done) vs final ${f:-none}"
    mkdir -p "$ROW_DIR/live-$tag-try$try" && mv "$ROW_DIR"/live-$tag-*.png "$ROW_DIR"/live-$tag-*.txt "$ROW_DIR"/live-$tag-*.xml "$ROW_DIR/slice-live-$tag.txt" "$ROW_DIR/live-$tag-try$try/" 2>/dev/null
  done
  assert_eq "($tag) both captures ended while she listened (c2done < the [speech] final line)" yes "$LIVE_OK"
  assert_eq "($tag) the pane opened over her (cortana_pane in the dump after)" yes "$(has_node "$ROW_DIR/live-$tag-after.xml" cortana_pane)"
  local mw
  mw="$(grep -F '[motion] cortana_pane' "$ROW_DIR/slice-live-$tag.txt" | grep -oE 'wall=[0-9]+' | head -1 | cut -d= -f2)"
  assert_eq "($tag) the pane's slide logged its settle (wall ${mw:-none}) before the first capture (c1 $(tl "$tag" c1))" yes "$( [ -n "$mw" ] && [ "$mw" -lt "$(tl "$tag" c1)" ] && echo yes || echo no)"
}
# inside the pane over the persona's box: the largest per-pixel |d| between the two captures
pane_persona_max() { # tag
  python3 -c "
import numpy as np; from PIL import Image
a=np.asarray(Image.open('$ROW_DIR/live-$1-1.png').convert('RGB'),int); b=np.asarray(Image.open('$ROW_DIR/live-$1-2.png').convert('RGB'),int)
l,t,r,bb=map(int,'$PBOX'.split()); r=min(r,$PANE_R); print(int(np.abs(a[t:bb,l:r]-b[t:bb,l:r]).max()))"
}
live_pane e2
read -r CX CY <<< "$PERSONA_C"
record "(e2) the 10x10 patch at the persona's centre ($CX,$CY), capture 1 -> 2" "$(python3 -c "
import numpy as np; from PIL import Image
a=np.asarray(Image.open('$ROW_DIR/live-e2-1.png').convert('RGB'),float)[$CY-5:$CY+5,$CX-5:$CX+5].reshape(-1,3).mean(0)
b=np.asarray(Image.open('$ROW_DIR/live-e2-2.png').convert('RGB'),float)[$CY-5:$CY+5,$CX-5:$CX+5].reshape(-1,3).mean(0)
print('(%.1f,%.1f,%.1f) -> (%.1f,%.1f,%.1f), max |d| %.1f' % (*a, *b, np.abs(a-b).max()))")"
# Re-cut 2026-09-25 (Change Log): the material's noise is deterministic (E6: a static backdrop is pixel-identical 1 s
# apart; (e3): the opaque pane is exactly 0), so ANY changed pixel inside the pane over the persona is the backdrop being
# re-recorded; a copy frozen at open would read 0. The doc's patch-mean >= 2 exceeds what the pulse shows through the blur
# (the host oracle over (e1)'s two unpaned captures predicts 0.14 levels; the device shows 1-2).
M2="$(pane_persona_max e2)"
assert_eq "(e2) on: the backdrop is re-recorded under the open pane (max per-pixel |d| over the persona = $M2 >= 1)" yes "$( [ "$M2" -ge 1 ] && echo yes || echo no)"
assert_eq "(e2) nothing right of the pane moved in the same captures (the difference is the persona under the pane)" 0 \
  "$(python3 -c "
import numpy as np; from PIL import Image
a=np.asarray(Image.open('$ROW_DIR/live-e2-1.png').convert('RGB'),int); b=np.asarray(Image.open('$ROW_DIR/live-e2-2.png').convert('RGB'),int)
print(int(np.abs(a[900:1700,$PANE_R+120:]-b[900:1700,$PANE_R+120:]).max()))")"

# (e3) acrylic off (battery saver): the same two captures are identical inside the pane (+- 0).
battery_saver_on
live_pane e3
assert_contains "(e3) acrylic=off reason=battery-saver" "[fluent] acrylic=off reason=battery-saver" "$(ring_since "$BS_MARK")"
assert_eq "(e3) off: the two captures are identical inside the pane (max per-pixel |d|)" 0 "$(python3 -c "
import numpy as np; from PIL import Image
a=np.asarray(Image.open('$ROW_DIR/live-e3-1.png').convert('RGB'),int); b=np.asarray(Image.open('$ROW_DIR/live-e3-2.png').convert('RGB'),int)
print(int(np.abs(a[:, :$PANE_R]-b[:, :$PANE_R]).max()))")"
assert_eq "(e3) battery saver off: awake" "Awake" "$(battery_saver_off)"
assert_eq "(e3) battery saver off: low_power = 0" "0" "$(adb shell settings get global low_power | tr -d '\r')"

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
