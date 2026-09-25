#!/usr/bin/env bash
# E10 — Alarms & Clock geometry (phase 15 [fidelity] H1; r11/clock.md's values ± 0.9 epx — CK1's ± 1 px — unless
# stated; a value read off pixels gets one device pixel more, phase 05's rule; T15-15). 1 epx = 3 px on this AVD.
# The tab band, the four tabs, the selected tab's accent and underline; a tap JUMPS (screenrecord: the first source
# frame that differs from the pre-tap frame equals a still taken 1 s later ± 8 levels; `[motion] clock_tab` secondary,
# no `clock_swipe` for a tap); a swipe logs `[motion] clock_swipe … settle=250 ± 17, maxGapMs ≤ 33.4`; the Sound flyout
# and the Snooze dropdown log `[motion] flyout … settle=233 ± 17`; the empty Alarm tab; an alarm row; the editor, its
# snooze list, sound flyout, the Sounds page, the timer editor; Select / Delete; More → About / Notification settings
# (no Send feedback); the World Clock tab; the Timer and Stopwatch tabs; the app bar and the drawn bars on every tab;
# the timer toast (0 → 216 epx, fill (57,57,57), the 48-epx accent tile at x 9.8, one ✕ centred at x 42.0).
# Restore: the alarms, timer and cities deleted through the app, the stopwatch reset, Home.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin E10 "Alarms & Clock geometry against r11/clock.md (± 0.9 epx; pixels + 1 px)"
assert_clock_empty "baseline"
dismiss_any_ring
stopwatch_baseline "baseline"
INK="$HERE/ink.py"; PIX="$HERE/pixcmp.py"
TOL=3        # ± 0.9 epx as device px (3 px = 1 epx; the doc's ± 1 px at CK1's scale)
PTOL=4       # a pixel-read value: ± 0.9 epx + 1 device px
BAND_TOP=$(( 28 * PX )); BAND_BOT=$(( BAND_TOP + 205 ))   # the drawn status bar's bottom (BarMetrics.STATUS_EPX); 68.4 epx
centre_x() { echo "$1" | awk '{printf "%d", ($1 + $3) / 2}'; }
centre_y() { echo "$1" | awk '{printf "%d", ($2 + $4) / 2}'; }
width_of() { echo "$1" | awk '{print $3 - $1}'; }
height_of() { echo "$1" | awk '{print $4 - $2}'; }
top_of() { echo "$1" | cut -d' ' -f2; }
left_of() { echo "$1" | cut -d' ' -f1; }
scale_rgb() { python3 -c '
import sys
f = float(sys.argv[2]); print(",".join(str(int(round(int(v) * f))) for v in sys.argv[1].split(",")))' "$1" "$2"; }
rgb_close() { python3 -c '
import sys
a = [int(x) for x in sys.argv[1].split(",")]; b = [int(x) for x in sys.argv[2].split(",")]; t = int(sys.argv[3])
print("yes" if all(abs(x - y) <= t for x, y in zip(a, b)) else "no (%s vs %s)" % (sys.argv[1], sys.argv[2]))' "$1" "$2" "$3"; }
ids_matching() { grep -o "resource-id=\"\\($2\\)[^\"]*\"" "$1" | cut -d'"' -f2 | sort -u | paste -sd,; }
selected_of() { grep -o "<node[^>]*resource-id=\"$2\"[^>]*>" "$1" | grep -o 'selected="[a-z]*"' | cut -d'"' -f2; }
bars_on() { # dump label
  assert_eq "$2: the drawn status bar is on the page" yes "$(has_node "$1" w10m_status_bar)"
  assert_eq "$2: the drawn nav bar is on the page" yes "$(has_node "$1" w10m_nav_bar)"
}

# ---- 1. the tab header ------------------------------------------------------------------------------------------------------
open_clock alarm
dump_ui "$ROW_DIR/tabs.xml"; screencap "$ROW_DIR/tabs.png"
TB="$(bounds "$ROW_DIR/tabs.xml" clock_tabs)"; note "clock_tabs $TB"
assert_within "1.2 the band starts at the drawn status bar's bottom (28 epx)" "$BAND_TOP" "$(top_of "$TB")" $TOL
assert_within "1.2 the band is 68.4 epx tall" 205 "$(height_of "$TB")" $TOL
assert_eq "1.2 the band is full width" "0 1080" "$(echo "$TB" | awk '{print $1, $3}')"
ORDER="$(for t in alarm world_clock timer stopwatch; do echo "$(centre_x "$(bounds "$ROW_DIR/tabs.xml" "clock_pivot:$t")") $t"; done | sort -n | awk '{print $2}' | paste -sd' ')"
assert_eq "1.3 four tabs in the order Alarm / World Clock / Timer / Stopwatch" "alarm world_clock timer stopwatch" "$ORDER"
i=0; for t in alarm world_clock timer stopwatch; do
  exp=$(python3 -c "print(round((82.3 + $i * 64.5) * 3))")
  assert_within "1.4 clock_pivot:$t centred at $(python3 -c "print(82.3 + $i * 64.5)") epx" "$exp" "$(centre_x "$(bounds "$ROW_DIR/tabs.xml" "clock_pivot:$t")")" $TOL
  i=$((i + 1))
done
UL="$(bounds "$ROW_DIR/tabs.xml" clock_tab_underline)"; note "underline $UL"
assert_within "1.8 the underline is 64.4 epx wide" 193 "$(width_of "$UL")" $TOL
assert_within "1.8 the underline is 3.7 epx thick" 11 "$(height_of "$UL")" $TOL
assert_within "1.8 the underline sits at the band's bottom" "$(( $(top_of "$TB") + $(height_of "$TB") ))" "$(echo "$UL" | cut -d' ' -f4)" $TOL
assert_within "1.8 the underline is centred under the selected tab" "$(centre_x "$(bounds "$ROW_DIR/tabs.xml" 'clock_pivot:alarm')")" "$(centre_x "$UL")" $TOL
# shellcheck disable=SC2086
set -- $UL; ACCENT="$(python3 "$PIX" mean "$ROW_DIR/tabs.png" $(( $1 + 6 )) $(( $2 + 3 )) $(( $3 - 6 )) $(( $4 - 3 )))"; note "accent from the underline: $ACCENT"
PB="$(bounds "$ROW_DIR/tabs.xml" 'clock_pivot:alarm')"
# shellcheck disable=SC2086
set -- $PB
ICON="$(python3 "$INK" bbox "$ROW_DIR/tabs.png" $1 $BAND_TOP $3 $(( BAND_TOP + 120 )) accent "$ACCENT")"; note "selected icon ink (accent): $ICON"
assert_ne "1.7 the selected tab's icon is drawn in accent" "" "$ICON"
assert_within "1.5 the icon's centre is 27.2 epx below the band top (pixels)" $(( BAND_TOP + 82 )) "$(centre_y "$ICON")" $PTOL
# The accent underline sits in the band's last 11 px (run 1 read its bottom as the label's): the label is searched
# above the underline's top.
LABEL="$(python3 "$INK" bbox "$ROW_DIR/tabs.png" $1 $(( BAND_TOP + 120 )) $3 $(( $(top_of "$UL") - 1 )) accent "$ACCENT")"; note "selected label ink (accent): $LABEL"
assert_ne "1.7 the selected tab's label is drawn in accent" "" "$LABEL"
assert_within "1.6 the label's ink top is 42.7 epx below the band top (pixels)" $(( BAND_TOP + 128 )) "$(top_of "$LABEL")" $PTOL
# r11 1.6's 78.2-epx bottom "includes descenders" (its German labels have them); "Alarm" has none, so the bottom is
# read on the Stopwatch tab's label ("p"), below, after that tab is selected.
LABEL_TOP="$(top_of "$LABEL")"
tap_node "$ROW_DIR/tabs.xml" 'clock_pivot:stopwatch'; sleep 1.5
dump_ui "$ROW_DIR/tabs_sw.xml"; screencap "$ROW_DIR/tabs_sw.png"
PBS="$(bounds "$ROW_DIR/tabs_sw.xml" 'clock_pivot:stopwatch')"; ULS="$(bounds "$ROW_DIR/tabs_sw.xml" clock_tab_underline)"
# shellcheck disable=SC2086
set -- $PBS
LABEL_SW="$(python3 "$INK" bbox "$ROW_DIR/tabs_sw.png" $1 $(( BAND_TOP + 120 )) $3 $(( $(top_of "$ULS") - 1 )) accent "$ACCENT")"; note "Stopwatch label ink (accent): $LABEL_SW"
assert_within "1.6 the label's ink bottom (with a descender: 'Stopwatch') is 54.2 epx below the band top (pixels)" $(( BAND_TOP + 163 )) "$(echo "$LABEL_SW" | cut -d' ' -f4)" $PTOL
tap_node "$ROW_DIR/tabs_sw.xml" 'clock_pivot:alarm'; sleep 1.5
dump_ui "$ROW_DIR/tabs.xml"
PB2="$(bounds "$ROW_DIR/tabs.xml" 'clock_pivot:timer')"
# shellcheck disable=SC2086
set -- $PB2
assert_eq "1.7 an unselected tab has no accent ink" "" "$(python3 "$INK" bbox "$ROW_DIR/tabs.png" $1 $BAND_TOP $3 $(( BAND_BOT - 12 )) accent "$ACCENT")"
bars_on "$ROW_DIR/tabs.xml" "Alarm tab"

# ---- a tap jumps (screenrecord), a swipe slides -----------------------------------------------------------------------------
MARK="$(ring_mark)"
adb shell rm -f /sdcard/e10_tap.mp4
adb shell screenrecord --time-limit 5 --bit-rate 20000000 --size 1080x2340 /sdcard/e10_tap.mp4 &
REC=$!
sleep 1.5
tap_node "$ROW_DIR/tabs.xml" 'clock_pivot:world_clock'
sleep 1
screencap "$ROW_DIR/tap_still.png"
wait $REC 2>/dev/null
adb pull /sdcard/e10_tap.mp4 "$ROW_DIR/tap.mp4" >/dev/null 2>&1; adb shell rm -f /sdcard/e10_tap.mp4
rm -rf "$ROW_DIR/tap_frames"; mkdir -p "$ROW_DIR/tap_frames"
ffmpeg -v error -i "$ROW_DIR/tap.mp4" -vsync 0 "$ROW_DIR/tap_frames/f_%04d.png" 2>>"$LOG"
ffprobe -v error -select_streams v:0 -show_entries frame=best_effort_timestamp_time -of csv=p=0 "$ROW_DIR/tap.mp4" | tr -d ',' > "$ROW_DIR/tap.pts"
note "tap capture: $(wc -l < "$ROW_DIR/tap.pts") source frames"
read -r FIRST_DIFF NFRAMES FRAC GAP <<< "$(python3 - "$ROW_DIR/tap_frames" "$ROW_DIR/tap_still.png" "$ROW_DIR/tap.pts" $BAND_BOT 2052 <<'PY'
import glob, os, sys
import numpy as np
from PIL import Image
d, still, pts, y0, y1 = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])
fs = sorted(glob.glob(os.path.join(d, "f_*.png")))
ts = [float(l) for l in open(pts) if l.strip()]
def load(p): return np.asarray(Image.open(p).convert("RGB")).astype(np.int16)[y0:y1]
base = load(fs[0]); s = load(still)
first = None
for i, f in enumerate(fs[1:], 1):
    a = load(f)
    if (np.abs(a - base) > 8).any(axis=2).mean() > 0.002: first = i; break
if first is None: print("none", len(fs), 0, 0); sys.exit()
a = load(fs[first])
frac = (np.abs(a - s) <= 8).all(axis=2).mean()
gap = (ts[first] - ts[first - 1]) * 1000 if first < len(ts) else 0
print(first, len(fs), "%.4f" % frac, "%.1f" % gap)
PY
)"
note "tap: first differing source frame #$FIRST_DIFF of $NFRAMES; its match to the still (±8): $FRAC; gap before it $GAP ms"
assert_ne "M1 a source frame differs from the pre-tap frame in the content region" "none" "$FIRST_DIFF"
assert_eq "M1 the first differing frame equals the still taken 1 s later (± 8 levels in >= 99 % of the content region)" yes "$(python3 -c 'import sys; print("yes" if float(sys.argv[1]) >= 0.99 else "no (%s)" % sys.argv[1])' "$FRAC")"
record "M1 the source-frame gap before the changed frame (ms; a jump has no intermediate frame to space)" "$GAP"
TAPLINE="$(ring_since "$MARK" | grep -F '[motion] clock_tab' | tail -1)"; note "${TAPLINE#*] }"
assert_ne "[motion] clock_tab was logged for the tap" "" "$TAPLINE"
assert_eq "[motion] clock_tab settle <= 33.4 ms (secondary)" yes "$(python3 -c 'import sys; v=sys.argv[1]; print("yes" if v and float(v) <= 33.4 else "no (%s)" % v)' "$(field_of "$TAPLINE" settle)")"
assert_absent "no [motion] clock_swipe for a tap" "[motion] clock_swipe" "$(ring_since "$MARK")"
dump_ui "$ROW_DIR/world_tab.xml"
assert_eq "the tap landed on the World Clock tab" "true" "$(selected_of "$ROW_DIR/world_tab.xml" 'clock_pivot:world_clock')"
bars_on "$ROW_DIR/world_tab.xml" "World Clock tab"
MARK="$(ring_mark)"
adb shell input swipe 900 1100 150 1100 260; sleep 1.5
SWLINE="$(ring_since "$MARK" | grep -F '[motion] clock_swipe' | tail -1)"; note "${SWLINE#*] }"
assert_ne "[motion] clock_swipe was logged for the swipe" "" "$SWLINE"
assert_within "X13 clock_swipe settle = 250 ± 17 ms" 250 "$(field_of "$SWLINE" settle)" 17
assert_eq "clock_swipe maxGapMs <= 33.4" yes "$(python3 -c 'import sys; v=sys.argv[1]; print("yes" if v and float(v) <= 33.4 else "no (%s)" % v)' "$(field_of "$SWLINE" maxGapMs)")"
gdump "$ROW_DIR/timer_tab.xml"
assert_eq "the swipe settled on the Timer tab" "true" "$(selected_of "$ROW_DIR/timer_tab.xml" 'clock_pivot:timer')"

# ---- 2. the Alarm tab: empty, then rows ------------------------------------------------------------------------------------
open_clock alarm
dump_ui "$ROW_DIR/alarm_empty.xml"; screencap "$ROW_DIR/alarm_empty.png"
EB="$(bounds "$ROW_DIR/alarm_empty.xml" alarm_empty)"
# shellcheck disable=SC2086
set -- $EB
G="$(python3 "$INK" glyph "$ROW_DIR/alarm_empty.png" $1 $2 $3 $4 bright 60)"; note "'N' of No alarms: $G"
assert_eq "2.1 the empty Alarm tab reads No alarms" "No alarms" "$(node_text "$ROW_DIR/alarm_empty.xml" alarm_empty)"
assert_within "2.1 its ink starts at x 9.8 epx (pixels)" 29 "$(left_of "$G")" $PTOL
assert_within "2.1 its cap is 17.8 epx (the N's ink height, pixels)" 53 "$(height_of "$G")" $PTOL
assert_within "2.1 its cap top is 21.4 epx below the band (pixels)" $(( BAND_BOT + 64 )) "$(top_of "$G")" $PTOL
MAXLUM="$(python3 - "$ROW_DIR/alarm_empty.png" $1 $2 $3 $4 <<'PY'
import sys
import numpy as np
from PIL import Image
a = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(int)
x0, y0, x1, y1 = (int(v) for v in sys.argv[2:6]); r = a[y0:y1, x0:x1]
lum = (r[..., 0] * 299 + r[..., 1] * 587 + r[..., 2] * 114) // 1000
print(int(np.percentile(lum[lum > 30], 99)) if (lum > 30).any() else 0)
PY
)"
assert_within "2.1 it is grey (ink p99 luminance ≈ 101 ± 25: 34–40 % white, Light)" 101 "$MAXLUM" 25
ID1="$(api_alarm 7 30 "Geometry")"; ID2="$(api_alarm 9 15 "Second")"
assert_ne "two alarms seeded for the row measurements" "" "$ID1$ID2"
open_clock alarm
dump_ui "$ROW_DIR/alarm_rows.xml"; screencap "$ROW_DIR/alarm_rows.png"
R1="$(bounds "$ROW_DIR/alarm_rows.xml" "alarm_row:$ID1")"; R2="$(bounds "$ROW_DIR/alarm_rows.xml" "alarm_row:$ID2")"
assert_within "2.8 rows at an 88-epx pitch (LOW; ± 1 epx)" 264 "$(( $(top_of "$R2") - $(top_of "$R1") ))" $TOL
TIME="$(bounds "$ROW_DIR/alarm_rows.xml" "alarm_time:$ID1")"
# shellcheck disable=SC2086
set -- $TIME
G="$(python3 "$INK" glyph "$ROW_DIR/alarm_rows.png" $1 $2 $3 $4 bright 100)"; note "'7' of 7:30 AM: $G"
assert_within "2.3 the time's digits are 17.8 epx tall (pixels)" 53 "$(height_of "$G")" $PTOL
assert_within "2.3 the time's ink starts at x 9.8 epx (pixels)" 29 "$(left_of "$G")" $PTOL
assert_within "2.7 the first time's cap top is 14.3 epx below the band (pixels)" $(( BAND_BOT + 43 )) "$(top_of "$G")" $PTOL
NB="$(bounds "$ROW_DIR/alarm_rows.xml" "alarm_name:$ID1")"
# shellcheck disable=SC2086
set -- $NB
assert_ne "2.4 the name is in accent while the alarm is on" "" "$(python3 "$INK" bbox "$ROW_DIR/alarm_rows.png" $1 $2 $3 $4 accent "$ACCENT")"
RB="$(bounds "$ROW_DIR/alarm_rows.xml" "alarm_repeat:$ID1")"
# shellcheck disable=SC2086
set -- $RB
REP="$(python3 "$INK" bbox "$ROW_DIR/alarm_rows.png" $1 $2 $3 $4 bright 60)"
assert_ne "2.5 the repeat line has ink" "" "$REP"
assert_eq "2.5 the repeat line is grey (no pixel brighter than 160)" "" "$(python3 "$INK" bbox "$ROW_DIR/alarm_rows.png" $1 $2 $3 $4 bright 160)"
TG="$(bounds "$ROW_DIR/alarm_rows.xml" "alarm_toggle:$ID1")"; note "toggle $TG"
assert_within "2.9 the toggle is 43.8 epx wide" 131 "$(width_of "$TG")" $TOL
assert_within "2.9 the toggle is 19.6 epx tall" 59 "$(height_of "$TG")" $TOL
assert_within "2.9 the toggle's right edge is 53.1 epx from the screen edge" $(( 1080 - 159 )) "$(echo "$TG" | cut -d' ' -f3)" $TOL
assert_within "2.9 the toggle's centre is 41.8 epx below the row top" $(( $(top_of "$R1") + 125 )) "$(centre_y "$TG")" $TOL
bars_on "$ROW_DIR/alarm_rows.xml" "Alarm tab with rows"
AB="$(bounds "$ROW_DIR/alarm_rows.xml" clock_app_bar)"; note "app bar $AB"
assert_within "1.12 the app bar is 48.2 epx tall (± 1 epx)" 145 "$(height_of "$AB")" $TOL
assert_eq "1.12 the app bar sits on the drawn nav bar" "$(top_of "$(bounds "$ROW_DIR/alarm_rows.xml" w10m_nav_bar)")" "$(echo "$AB" | cut -d' ' -f4)"
assert_within "1.12 the buttons are on a 68-epx pitch" 204 "$(( $(centre_x "$(bounds "$ROW_DIR/alarm_rows.xml" alarm_select)") - $(centre_x "$(bounds "$ROW_DIR/alarm_rows.xml" 'clock_bar:add')") ))" $TOL
assert_eq "1.13 Alarm tab buttons: Add, Select, More" "alarm_select,clock_bar:add,clock_more" "$(ids_matching "$ROW_DIR/alarm_rows.xml" 'alarm_select\|clock_bar:\|clock_more' | tr ',' '\n' | sort | paste -sd,)"

# ---- Select / Delete ----------------------------------------------------------------------------------------------------------
tap_node "$ROW_DIR/alarm_rows.xml" alarm_select; sleep 1
dump_ui "$ROW_DIR/select.xml"; screencap "$ROW_DIR/select.png"
assert_eq "Select puts a checkbox on each row" "$(printf 'alarm_check:%s\nalarm_check:%s\n' "$ID1" "$ID2" | sort | paste -sd,)" "$(ids_matching "$ROW_DIR/select.xml" 'alarm_check:' | tr ',' '\n' | sort | paste -sd,)"
assert_eq "… and offers Delete (alarm_select_delete)" yes "$(has_node "$ROW_DIR/select.xml" alarm_select_delete)"
tap_node "$ROW_DIR/select.xml" "alarm_check:$ID2"; sleep 0.8
dump_ui "$ROW_DIR/select_checked.xml"
tap_node "$ROW_DIR/select_checked.xml" alarm_select_delete; sleep 1.5
dump_ui "$ROW_DIR/after_delete.xml"
assert_eq "Delete removed the checked alarm from the list" no "$(has_node "$ROW_DIR/after_delete.xml" "alarm_row:$ID2")"
assert_eq "… and kept the other" yes "$(has_node "$ROW_DIR/after_delete.xml" "alarm_row:$ID1")"
assert_eq "… and from dumpsys alarm (one entry left)" 1 "$(alarm_trigger_ms | wc -l)"
assert_eq "… and from the store" "$ID1" "$(alarm_ids | paste -sd,)"

# ---- More: About and Notification settings, no Send feedback --------------------------------------------------------------------
tap_node "$ROW_DIR/after_delete.xml" clock_more; sleep 1.2
dump_ui "$ROW_DIR/more.xml"; screencap "$ROW_DIR/more.png"
assert_eq "More lists About and Notification settings" "clock_more:about,clock_more:notifications" "$(ids_matching "$ROW_DIR/more.xml" 'clock_more:' | tr ',' '\n' | sort | paste -sd,)"
assert_eq "… and no Send feedback" "" "$(grep -io 'text="[^"]*feedback[^"]*"' "$ROW_DIR/more.xml" | head -1)"
tap_node "$ROW_DIR/more.xml" 'clock_more:about'; sleep 1.5
dump_ui "$ROW_DIR/about.xml"
assert_eq "About opens about_page" yes "$(has_node "$ROW_DIR/about.xml" about_page)"
assert_eq "… with no Feedback entry" "" "$(grep -io 'text="[^"]*feedback[^"]*"' "$ROW_DIR/about.xml" | head -1)"
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$ROW_DIR/back_from_about.xml"
tap_node "$ROW_DIR/back_from_about.xml" clock_more; sleep 1.2
dump_ui "$ROW_DIR/more2.xml"
tap_node "$ROW_DIR/more2.xml" 'clock_more:notifications'; sleep 2
assert_contains "Notification settings opens the shell's Setup page (SettingsActivity)" "settings.SettingsActivity" "$(resumed)"
dump_ui "$ROW_DIR/setup.xml"
assert_ne "… showing the checklist (checklist:* rows)" "" "$(ids_matching "$ROW_DIR/setup.xml" 'checklist:')"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- 3. the editor ----------------------------------------------------------------------------------------------------------------
open_clock alarm
dump_ui "$ROW_DIR/e_list.xml"
tap_node "$ROW_DIR/e_list.xml" 'clock_bar:add'; sleep 1.5
dump_ui "$ROW_DIR/editor.xml"; screencap "$ROW_DIR/editor.png"
TT="$(bounds "$ROW_DIR/editor.xml" alarm_editor_title)"
# shellcheck disable=SC2086
set -- $TT
G="$(python3 "$INK" glyph "$ROW_DIR/editor.png" $1 $2 $3 $4 bright 100)"; note "'N' of NEW ALARM: $G"
assert_within "3.3 the title's cap is 11.6 epx (pixels)" 35 "$(height_of "$G")" $PTOL
assert_within "3.3 the title's ink starts at x 10.7 epx (pixels)" 32 "$(left_of "$G")" $PTOL
MB="$(bounds "$ROW_DIR/editor.xml" 'alarm_spinner:minute')"
# shellcheck disable=SC2086
set -- $MB
ROWS="$(python3 "$INK" rows "$ROW_DIR/editor.png" $(( $1 + 60 )) $(( $2 + 8 )) $(( $3 - 60 )) $(( $4 - 8 )) bright 90)"; note "minute column ink rows: $ROWS"
PITCHES="$(python3 -c '
import sys
tops = [int(r.split("-")[0]) for r in sys.argv[1].split()]
print(" ".join(str(b - a) for a, b in zip(tops, tops[1:])))' "$ROWS")"
assert_eq "3.5 the spinner rows are on a 32.0-epx pitch (every consecutive digit-row top 96 ± 4 px)" yes "$(python3 -c '
import sys
ps = [int(p) for p in sys.argv[1].split()]
print("yes" if ps and all(abs(p - 96) <= 4 for p in ps) else "no (%s)" % sys.argv[1])' "$PITCHES")"
SP="$(bounds "$ROW_DIR/editor.xml" alarm_spinner)"
BANDC="$(python3 "$PIX" mean "$ROW_DIR/editor.png" 8 $(( $(centre_y "$SP") - 30 )) 28 $(( $(centre_y "$SP") + 30 )))"
assert_eq "3.6 the selected band is ≈ 0.6 × accent over black (± 8 levels)" yes "$(rgb_close "$BANDC" "$(scale_rgb "$ACCENT" 0.6)" 8)"
V1="$(bounds "$ROW_DIR/editor.xml" 'alarm_editor_field:name')"; V2="$(bounds "$ROW_DIR/editor.xml" 'alarm_editor_field:repeats')"
V3="$(bounds "$ROW_DIR/editor.xml" 'alarm_editor_field:sound')"; V4="$(bounds "$ROW_DIR/editor.xml" 'alarm_editor_field:snooze')"
# r11 3.9's cap tops are 298.7 · 364.4 · 428.4 · 492.4 (gaps 65.7 / 64.0 / 64.0); the doc writes "64.0-epx pitch".
assert_within "3.9 field rows 1→2 pitch (r11: 65.7 epx)" 197 "$(( $(top_of "$V2") - $(top_of "$V1") ))" $TOL
assert_within "3.9 field rows 2→3 pitch 64.0 epx" 192 "$(( $(top_of "$V3") - $(top_of "$V2") ))" $TOL
assert_within "3.9 field rows 3→4 pitch 64.0 epx" 192 "$(( $(top_of "$V4") - $(top_of "$V3") ))" $TOL
for v in name repeats sound snooze; do
  b="$(bounds "$ROW_DIR/editor.xml" "alarm_editor_field:$v")"
  # shellcheck disable=SC2086
  set -- $b
  assert_ne "3.10 the $v value is in accent" "" "$(python3 "$INK" bbox "$ROW_DIR/editor.png" $1 $2 $3 $4 accent "$ACCENT")"
done
assert_eq "4.5 a new alarm's snooze time is 10 minutes" "10 minutes" "$(node_text "$ROW_DIR/editor.xml" 'alarm_editor_field:snooze')"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/editor.xml" 'alarm_editor_field:snooze'; sleep 1.2
dump_ui "$ROW_DIR/snooze_list.xml"; screencap "$ROW_DIR/snooze_list.png"
assert_eq "4.4 the Snooze time list is exactly 5 / 10 / 20 / 30 minutes / 1 hour" "alarm_snooze:10,alarm_snooze:20,alarm_snooze:30,alarm_snooze:5,alarm_snooze:60" "$(ids_matching "$ROW_DIR/snooze_list.xml" 'alarm_snooze:' | tr ',' '\n' | sort | paste -sd,)"
SB10="$(bounds "$ROW_DIR/snooze_list.xml" 'alarm_snooze:10')"
# r11 4.4's (172,100,14) is accent (254,136,0) at 0.68 OVER the flyout fill (40,40,40) — its blue channel 14 ≈ 0.32 × 40 —
# so the expectation is that blend, not 0.68 × accent over black (run 1 measured exactly the blend).
BLEND68="$(python3 -c '
import sys
a = [int(v) for v in sys.argv[1].split(",")]; print(",".join(str(int(round(0.68 * v + 0.32 * 40))) for v in a))' "$ACCENT")"
assert_eq "4.4 10 minutes is the selected item (fill = 0.68 × accent over the (40,40,40) flyout fill, ± 8)" yes "$(rgb_close "$(python3 "$PIX" mean "$ROW_DIR/snooze_list.png" 800 $(( $(centre_y "$SB10") - 10 )) 1000 $(( $(centre_y "$SB10") + 10 )))" "$BLEND68" 8)"
FL="$(ring_since "$MARK" | grep -F '[motion] flyout' | tail -1)"; note "snooze dropdown: ${FL#*] }"
assert_within "the Snooze dropdown logs [motion] flyout settle = 233 ± 17 ms" 233 "$(field_of "$FL" settle)" 17
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$ROW_DIR/editor2.xml"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/editor2.xml" 'alarm_editor_field:sound'; sleep 1.2
dump_ui "$ROW_DIR/sound_flyout.xml"; screencap "$ROW_DIR/sound_flyout.png"
assert_eq "4.3 the Sound flyout is exactly Vibrate only / Pick from my music / Pick from ringtones" "alarm_sound:music,alarm_sound:ringtones,alarm_sound:vibrate" "$(ids_matching "$ROW_DIR/sound_flyout.xml" 'alarm_sound:' | tr ',' '\n' | sort | paste -sd,)"
FL="$(ring_since "$MARK" | grep -F '[motion] flyout' | tail -1)"; note "sound flyout: ${FL#*] }"
assert_within "the Sound flyout logs [motion] flyout settle = 233 ± 17 ms" 233 "$(field_of "$FL" settle)" 17
tap_node "$ROW_DIR/sound_flyout.xml" 'alarm_sound:ringtones'; sleep 2.5
dump_ui "$ROW_DIR/sounds.xml"; screencap "$ROW_DIR/sounds.png"
assert_eq "4.6 the Sounds page is titled Sounds" "Sounds" "$(node_text "$ROW_DIR/sounds.xml" sounds_title)"
ST="$(bounds "$ROW_DIR/sounds.xml" sounds_title)"
# shellcheck disable=SC2086
set -- $ST
assert_within "4.6 the title's ink starts at x 24.2 epx (pixels)" 73 "$(left_of "$(python3 "$INK" glyph "$ROW_DIR/sounds.png" $1 $2 $3 $4 bright 100)")" $PTOL
assert_eq "4.6 Use default is offered" yes "$(has_node "$ROW_DIR/sounds.xml" sounds_default)"
RULE="$(python3 "$INK" hband "$ROW_DIR/sounds.png" 540 $(( $(echo "$ST" | cut -d' ' -f4) )) $(( $(top_of "$(bounds "$ROW_DIR/sounds.xml" sounds_list)") + 4 )) 129,129,129 12)"; note "rule rows at x 540: $RULE"
assert_eq "4.6 a 1-epx rule (3 px) under Use default" yes "$(python3 -c '
import sys
rs = sys.argv[1].split()
print("yes" if any(2 <= int(r.split("-")[1]) - int(r.split("-")[0]) <= 4 for r in rs) else "no (%s)" % sys.argv[1])' "$RULE")"
SR="$(ids_matching "$ROW_DIR/sounds.xml" 'sounds_row:' | tr ',' '\n')"
T1="$(top_of "$(bounds "$ROW_DIR/sounds.xml" 'sounds_row:classic')")"; T2="$(top_of "$(bounds "$ROW_DIR/sounds.xml" 'sounds_row:beep')")"
assert_within "4.6 sounds_row:* at a 60.4-epx pitch (± 1 epx)" 181 "$(( T2 - T1 ))" $TOL
EXPECT_NAMES="$( (for s in "Alarm Clock" "Beep-beep" "Chime" "Rise" "Pulse"; do echo "$s"; done; adb shell content query --uri content://media/internal/audio/media --projection _id:title --where "is_alarm=1" 2>/dev/null | tr -d '\r' | grep -oE 'title=.*' | sed 's/^title=//') | sort)"
# Every row's name, across the whole list: dump, collect, swipe, until a dump adds nothing new (run 1 read the first
# and the last screen only and missed the middle rows).
NAMES_ALL=""; STALE=0
for i in 1 2 3 4 5 6 7 8; do
  dump_ui "$ROW_DIR/sounds_$i.xml"
  NEW="$(for id in $(ids_matching "$ROW_DIR/sounds_$i.xml" 'sounds_row:' | tr ',' '\n' | sed 's/sounds_row://'); do node_text "$ROW_DIR/sounds_$i.xml" "sounds_row:$id"; done)"
  MERGED="$( (printf '%s\n' "$NAMES_ALL"; printf '%s\n' "$NEW") | grep . | sort -u)"
  if [ "$MERGED" = "$NAMES_ALL" ]; then STALE=$((STALE + 1)); [ "$STALE" -ge 2 ] && break; else STALE=0; fi
  NAMES_ALL="$MERGED"
  adb shell input swipe 540 1900 540 900 320; sleep 1
done
note "sounds listed: $(printf '%s\n' "$NAMES_ALL" | paste -sd'|')"
assert_eq "4.6 the rows' names are the brand sound-alikes plus the image's TYPE_ALARM tones" "$(printf '%s\n' "$EXPECT_NAMES" | sort -u | paste -sd'|')" "$(printf '%s\n' "$NAMES_ALL" | paste -sd'|')"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- 4.8 the timer editor -------------------------------------------------------------------------------------------------------
open_clock timer
dump_ui "$ROW_DIR/t_list.xml"
tap_node "$ROW_DIR/t_list.xml" 'clock_bar:add'; sleep 1.5
dump_ui "$ROW_DIR/timer_editor.xml"; screencap "$ROW_DIR/timer_editor.png"
assert_eq "4.8 the timer editor is titled NEW TIMER" "NEW TIMER" "$(node_text "$ROW_DIR/timer_editor.xml" timer_editor_title)"
TS="$(bounds "$ROW_DIR/timer_editor.xml" timer_spinner)"; note "timer spinner $TS"
assert_within "4.8 the spinner spans from 40 epx below the status bar (64.0 − 24)" $(( BAND_TOP + 120 )) "$(top_of "$TS")" $TOL
assert_within "4.8 … to 295.1 epx below it (319.1 − 24)" $(( BAND_TOP + 885 )) "$(echo "$TS" | cut -d' ' -f4)" $TOL
H="$(bounds "$ROW_DIR/timer_editor.xml" 'timer_editor_field:hours')"; M="$(bounds "$ROW_DIR/timer_editor.xml" 'timer_editor_field:minutes')"
assert_within "4.8 the first column split is at 117.7 epx (± 1 epx)" 353 "$(( ($(echo "$H" | cut -d' ' -f3) + $(left_of "$M")) / 2 ))" $TOL
S="$(bounds "$ROW_DIR/timer_editor.xml" 'timer_editor_field:seconds')"
assert_within "4.8 the second column split is at 239.4 epx (± 1 epx)" 718 "$(( ($(echo "$M" | cut -d' ' -f3) + $(left_of "$S")) / 2 ))" $TOL
assert_eq "4.8 Timer name is offered" yes "$(has_node "$ROW_DIR/timer_editor.xml" 'timer_editor_field:name')"
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- 5. the World Clock tab -----------------------------------------------------------------------------------------------------
open_clock world_clock
dump_ui "$ROW_DIR/world.xml"; screencap "$ROW_DIR/world.png"
LR="$(bounds "$ROW_DIR/world.xml" clock_local_row)"; note "local row $LR"
assert_within "5.3 the Local time row is 84.9 epx tall" 255 "$(height_of "$LR")" $TOL
assert_eq "5.3 it sits directly under the band" "$BAND_BOT" "$(top_of "$LR")"
assert_eq "5.3 it is accent-filled at ≈ 0.6 × accent (± 8)" yes "$(rgb_close "$(python3 "$PIX" mean "$ROW_DIR/world.png" 800 $(( $(top_of "$LR") + 20 )) 1000 $(( $(top_of "$LR") + 60 )))" "$(scale_rgb "$ACCENT" 0.6)" 8)"
assert_eq "1.13 World Clock buttons: New, Compare, More" "clock_bar:add,clock_compare,clock_more" "$(ids_matching "$ROW_DIR/world.xml" 'clock_bar:\|clock_compare\|clock_more' | tr ',' '\n' | grep -v strip | sort | paste -sd,)"
for z in Tok:Asia/Tokyo Lon:Europe/London; do
  dump_ui "$ROW_DIR/w_add.xml"; tap_node "$ROW_DIR/w_add.xml" 'clock_bar:add'; sleep 1
  adb shell input text "${z%%:*}"; sleep 1
  dump_ui "$ROW_DIR/w_search.xml"; tap_node "$ROW_DIR/w_search.xml" "clock_search_result:${z#*:}"; sleep 1.2
done
dump_ui "$ROW_DIR/world_rows.xml"; screencap "$ROW_DIR/world_rows.png"
assert_within "5.5 city rows at a 96.9-epx pitch" 291 "$(( $(top_of "$(bounds "$ROW_DIR/world_rows.xml" 'clock_row:Europe/London')") - $(top_of "$(bounds "$ROW_DIR/world_rows.xml" 'clock_row:Asia/Tokyo')") ))" $TOL
bars_on "$ROW_DIR/world_rows.xml" "World Clock tab"

# ---- 6. the Timer tab -------------------------------------------------------------------------------------------------------------
TID="$(api_timer 900 "Geo")"
assert_ne "a running timer for the Timer tab measurements" "" "$TID"
open_clock timer
gdump "$ROW_DIR/timer.xml"; screencap "$ROW_DIR/timer.png"
TR="$(bounds "$ROW_DIR/timer.xml" "timer_remaining:$TID")"
# shellcheck disable=SC2086
set -- $TR
G="$(python3 "$INK" glyph "$ROW_DIR/timer.png" $1 $2 $3 $4 bright 55)"; note "timer first digit: $G"
assert_within "6.2 the timer's digits are 30.2 epx tall (pixels)" 91 "$(height_of "$G")" $PTOL
PL="$(bounds "$ROW_DIR/timer.xml" "timer_play:$TID")"
assert_within "6.4 the ring button is Ø 59.6 epx" 179 "$(width_of "$PL")" $TOL
assert_within "6.3 reset sits 104 epx left of the centre" $(( $(centre_x "$PL") - 312 )) "$(centre_x "$(bounds "$ROW_DIR/timer.xml" "timer_reset:$TID")")" $TOL
assert_within "6.3 expand sits 104 epx right of the centre" $(( $(centre_x "$PL") + 312 )) "$(centre_x "$(bounds "$ROW_DIR/timer.xml" "timer_expand:$TID")")" $TOL
assert_eq "1.13 Timer buttons: Add, Select, Pin, More" "clock_bar:add,clock_more,timer_pin:$TID,timer_select" "$(ids_matching "$ROW_DIR/timer.xml" 'clock_bar:\|clock_more\|timer_pin\|timer_select' | tr ',' '\n' | sort | paste -sd,)"
bars_on "$ROW_DIR/timer.xml" "Timer tab"

# ---- 7. the Stopwatch tab ---------------------------------------------------------------------------------------------------------
open_clock stopwatch
gdump "$ROW_DIR/stopwatch.xml"; screencap "$ROW_DIR/stopwatch.png"
SE="$(bounds "$ROW_DIR/stopwatch.xml" stopwatch_elapsed)"
# shellcheck disable=SC2086
set -- $SE
G="$(python3 "$INK" glyph "$ROW_DIR/stopwatch.png" $1 $2 $3 $4 bright 55)"; note "stopwatch first digit: $G"
assert_within "7.1 the stopwatch's digits are 32.0 epx tall (pixels)" 96 "$(height_of "$G")" $PTOL
COLS="$(python3 "$INK" cols "$ROW_DIR/stopwatch.png" $1 $2 $3 $4 bright 100)"; LASTC="${COLS##* }"
HB="$(python3 "$INK" bbox "$ROW_DIR/stopwatch.png" ${LASTC%-*} $2 ${LASTC#*-} $4 bright 100)"; note "last hundredths digit: $HB"
assert_within "7.1 the hundredths are 17.7 epx tall (pixels)" 53 "$(height_of "$HB")" $PTOL
SPL="$(bounds "$ROW_DIR/stopwatch.xml" stopwatch_play)"
assert_within "7.2 reset sits 96 epx left of the centre" $(( $(centre_x "$SPL") - 288 )) "$(centre_x "$(bounds "$ROW_DIR/stopwatch.xml" stopwatch_reset)")" $TOL
assert_within "7.2 expand sits 96 epx right of the centre" $(( $(centre_x "$SPL") + 288 )) "$(centre_x "$(bounds "$ROW_DIR/stopwatch.xml" stopwatch_expand)")" $TOL
assert_eq "1.13 Stopwatch buttons: Pin, Share, More" "clock_more,stopwatch_pin,stopwatch_share" "$(ids_matching "$ROW_DIR/stopwatch.xml" 'clock_more\|stopwatch_pin\|stopwatch_share' | tr ',' '\n' | sort | paste -sd,)"
bars_on "$ROW_DIR/stopwatch.xml" "Stopwatch tab"

# ---- 8.9–8.11 the timer toast -----------------------------------------------------------------------------------------------------
open_clock timer
MARK="$(ring_mark)"
TID2="$(api_timer 15 "Toast")"
open_clock timer
assert_ne "the 15-s timer fired" "" "$(wait_ring "$MARK" "[alarms] fired $TID2 kind=timer" 40)"
sleep 2.5
gdump "$ROW_DIR/toast.xml"; screencap "$ROW_DIR/toast.png"
RS="$(bounds "$ROW_DIR/toast.xml" ring_surface)"; note "timer toast $RS"
assert_eq "8.9 the timer toast starts at y 0 over the shell's own page" 0 "$(top_of "$RS")"
assert_within "8.9 it is 216 epx tall (MEDIUM, ± 3 %)" 648 "$(height_of "$RS")" 19
assert_eq "8.9 its fill is (57,57,57) ± 4" yes "$(rgb_close "$(python3 "$PIX" mean "$ROW_DIR/toast.png" 800 420 1000 480)" "57,57,57" 4)"
TILE="$(python3 "$INK" bbox "$ROW_DIR/toast.png" 0 60 400 320 accent "$ACCENT")"; note "accent tile: $TILE"
assert_within "8.10 the accent app tile starts at x 9.8 epx (pixels)" 29 "$(left_of "$TILE")" $PTOL
assert_within "8.10 the tile is 48 epx wide (pixels)" 144 "$(width_of "$TILE")" $PTOL
assert_within "8.10 the tile is 48 epx tall (pixels)" 144 "$(height_of "$TILE")" $PTOL
DB="$(bounds "$ROW_DIR/toast.xml" ring_dismiss)"
assert_within "8.11 the one ✕ button is centred at x 42.0 epx" 126 "$(centre_x "$DB")" $TOL
assert_eq "8.11 no Snooze on the timer toast" no "$(has_node "$ROW_DIR/toast.xml" ring_snooze)"
gtap "$ROW_DIR/toast.xml" ring_dismiss; sleep 2

# ---- restore --------------------------------------------------------------------------------------------------------------------------
app_delete_alarm "$ID1"; app_delete_timer "$TID"; app_delete_timer "$TID2"
open_clock world_clock
for z in Asia/Tokyo Europe/London; do
  dump_ui "$ROW_DIR/w_restore.xml"; hold_node "$ROW_DIR/w_restore.xml" "clock_row:$z" 1000; sleep 1
  dump_ui "$ROW_DIR/w_restore_menu.xml"; tap_node "$ROW_DIR/w_restore_menu.xml" "clock_remove:$z"; sleep 1
done
dump_ui "$ROW_DIR/w_restored.xml"
assert_eq "restore: the cities are removed" "" "$(ids_matching "$ROW_DIR/w_restored.xml" 'clock_row:')"
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_clock_empty "restore"
row_end
