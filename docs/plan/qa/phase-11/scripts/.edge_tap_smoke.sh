#!/usr/bin/env bash
# EDGE — the phase doc's Edge Cases list, run as a list (the re-judge, R2-1; the skill's gate rule and Hard Rule 5).
# Each bullet the other rows do not already carry is a sub-step here; EDGE/EDGE.txt's index (written by this row, from
# edge_index.tsv) maps EVERY bullet to its evidence: a sub-step of this row, another row or JVM test by name, or a
# recorded NEEDS-HUMAN / phone row. Each sub-step starts from a plain Start and takes its MARK just before its action.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin EDGE-tapsmoke "the one-tap ruling's EDGE sub-steps only (second finger, still press, X5): the Edge Cases list: flip, fling, call, keyguard, listener restart, process death, second finger, show more tiles, folder dissolve, labels, theme, transparency, RV10"
seed_fixtures
restore baseline_layout.json

# fresh_burst [tile id] [dump name]: hold the tile (default the fixture) from a plain Start and release; the dump is the burst.
fresh_burst() {
  local id="${1:-tile:$A_KEY}" out="$ROW_DIR/${2:-B}.xml"
  qdump "$ROW_DIR/.rest.xml"
  read -r FX FY <<< "$(center "$ROW_DIR/.rest.xml" "$id")"
  hold "$FX" "$FY" 1.0
  qdump "$out"
  [ "$(has_node "$out" quick_burst)" = yes ] || note "fresh_burst: no burst on $id"
}
sat_rects() { # dump -> "id l t r b" per satellite square and label
  python3 - "$1" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
for m in re.finditer(r'resource-id="(quick_sat(?:_label)?:\d)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    print(m.group(1), *m.groups()[1:])
PY
}
epx_same() { # base.xml base_width other.xml other_width -> "PASS <max d>" or "FAIL <why>": every satellite rect in epx within 1.0
  python3 - "$@" <<'PY'
import re, sys
def load(fn, w):
    s = open(fn).read(); k = int(w) / 360.0
    return {m.group(1): tuple(int(v) / k for v in m.groups()[1:])
            for m in re.finditer(r'resource-id="(quick_sat(?:_label)?:\d)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)}
a, b = load(sys.argv[1], sys.argv[2]), load(sys.argv[3], sys.argv[4])
if not a: print("FAIL no satellites in the base"); sys.exit()
if set(a) != set(b): print("FAIL ids differ", sorted(a), sorted(b)); sys.exit()
d = max(max(abs(x - y) for x, y in zip(a[k], b[k])) for k in a)
print(("PASS" if d <= 1.0 else "FAIL") + f" max |d| = {d:.2f} epx over {len(a)} rects")
PY
}
# The emulator's touchscreen (virtio multi-touch, slotted protocol B; adb's shell is root on this AVD), for what
# `input` cannot do: a second pointer, and a press landing within milliseconds of a fling.
TS=/dev/input/event2
mt_x() { echo $(( $1 * 32767 / 1080 )); }
mt_y() { echo $(( $1 * 32767 / 2340 )); }
# The device has an ABS_MT_PRESSURE axis and no BTN_TOUCH key: a contact with pressure 0 reads as hovering, so every
# down carries a pressure and a touch-major (the first run of this row sent none, and nothing touched).
mt_down() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 57 $(( 100 + $1 )); sendevent $TS 3 53 $(mt_x "$2"); sendevent $TS 3 54 $(mt_y "$3"); sendevent $TS 3 58 512; sendevent $TS 3 48 8; sendevent $TS 0 0 0;"; }
mt_move() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 53 $(mt_x "$2"); sendevent $TS 3 54 $(mt_y "$3"); sendevent $TS 3 58 512; sendevent $TS 0 0 0;"; }
mt_up() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 57 4294967295; sendevent $TS 0 0 0;"; }

log "--- a second finger while a burst is open: ignored (as phase 02 ignores it) ---"
qdump "$ROW_DIR/.rest.xml"
read -r FX FY <<< "$(center "$ROW_DIR/.rest.xml" "tile:$A_KEY")"
read -r EX EY <<< "$(empty_point "$ROW_DIR/.rest.xml" 300 1850)"
MARK="$(ring_mark)"
adb shell "$(mt_down 0 "$FX" "$FY") sleep 1.0;"
qdump "$ROW_DIR/finger1.xml"
adb shell "$(mt_down 1 "$EX" "$EY") sleep 0.15; $(mt_up 1)"
sleep 0.5; qdump "$ROW_DIR/finger2.xml"
adb shell "$(mt_up 0 last)"
sleep 0.8; qdump "$ROW_DIR/finger-released.xml"
S="$(quick_since "$MARK")"
assert_contains "the first finger's hold opened the burst" "burst on" "$S"
assert_eq "the burst is open while the first finger holds" yes "$(has_node "$ROW_DIR/finger1.xml" quick_burst)"
assert_eq "a second finger down and up elsewhere: the burst stays" yes "$(has_node "$ROW_DIR/finger2.xml" quick_burst)"
assert_eq "…and after the first finger lifts" yes "$(has_node "$ROW_DIR/finger-released.xml" quick_burst)"
assert_absent "nothing closed it" "burst closed" "$S"
# The control (the round-2 re-judge, EV-1): the same slot-1 contact ALONE, the burst still open, is a tap elsewhere and closes
# it — so the contact reaches the app, and only its being a second pointer kept the burst open above.
MARK="$(ring_mark)"
adb shell "$(mt_down 1 "$EX" "$EY") sleep 0.15; $(mt_up 1)"; sleep 1
assert_contains "control: the same contact alone closes the burst (tap elsewhere)" "burst closed: tap elsewhere" "$(quick_since "$MARK")"
c6

log "--- in edit mode no press is a hold (T11-23): a 1.0-s still press on another tile with a burst open closes it and leaves edit mode ---"
# The round-2 re-judge, EV-4 (i): E5 ran its long press after closing the burst; here the burst is open. Then the held tile
# with no burst open: a 1.0-s still press is a tap, and exits edit mode.
fresh_burst
disc0="$(bounds "$ROW_DIR/B.xml" edit_disc:unpin)"
read -r TX TY <<< "$(center "$ROW_DIR/B.xml" tile:shell:cortana)"
assert_eq "Tess's centre is outside every satellite" none "$(python3 - "$ROW_DIR/B.xml" "$TX" "$TY" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); x, y = int(sys.argv[2]), int(sys.argv[3])
hit = [m.group(1) for m in re.finditer(r'resource-id="(quick_sat[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
       if int(m.group(2)) <= x < int(m.group(4)) and int(m.group(3)) <= y < int(m.group(5))]
print(" ".join(hit) or "none")
PY
)"
MARK="$(ring_mark)"
hold "$TX" "$TY" 1.0; sleep 0.5
qdump "$ROW_DIR/press-other.xml"
S="$(quick_since "$MARK")"
assert_contains "a 1.0-s still press on another tile closed the burst (tap elsewhere)" "burst closed: tap elsewhere" "$S"
assert_absent "…and was no hold" "[edit] hold" "$(ring_since "$MARK")"
assert_eq "…and opened no second burst" 0 "$(echo "$S" | grep -c 'burst on')"
assert_eq "…and leaves edit mode in the same tap (Jeremy 2026-09-25)" no "$(has_node "$ROW_DIR/press-other.xml" edit_disc:unpin)"
note "(the fixture's discs before: $disc0)"
# The held tile with no burst open: Weather declares no shortcuts, so its hold enters edit mode with no burst.
c6
qdump "$ROW_DIR/press-held-rest.xml"
read -r WX WY <<< "$(center "$ROW_DIR/press-held-rest.xml" tile:shell:weather)"
hold "$WX" "$WY" 1.0; sleep 0.5
qdump "$ROW_DIR/press-held-pre.xml"
assert_eq "Weather held: edit mode, no burst" "yes no" "$(has_node "$ROW_DIR/press-held-pre.xml" edit_disc:unpin) $(has_node "$ROW_DIR/press-held-pre.xml" quick_burst)"
HX=$WX; HY=$WY
MARK="$(ring_mark)"
hold "$HX" "$HY" 1.0; sleep 1
qdump "$ROW_DIR/press-held.xml"
assert_eq "a 1.0-s still press on the held tile, no burst open, exits edit mode" no "$(has_node "$ROW_DIR/press-held.xml" edit_disc:unpin)"
assert_absent "…and was no hold" "[edit] hold" "$(ring_since "$MARK")"
c6

log "--- the X5 transparency slider at 0 % and 100 %: the satellite fill follows the tile's ---"
# The slider shows only while a background picture is set (StartThemePage.kt), and tile alpha is 1 - 0.8 x transparency
# over it (StartPage.kt); a satellite draws the accent at that same alpha (QuickBurst.kt). So each satellite square must
# read alpha x accent + (1 - alpha) x whatever lies behind it — a neighbouring tile, mostly — at 0 % (the accent) and at
# 100 % (a fifth of it). The first cut compared a satellite with the held tile, whose backdrop differs (2026-09-24).
python3 -c "
from PIL import Image; Image.new('RGB', (1080, 2340), (200, 60, 40)).save('$ROW_DIR/qa-edge-bg.png')"
adb push "$ROW_DIR/qa-edge-bg.png" /sdcard/Pictures/qa-edge-bg.png >/dev/null 2>&1
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1; sleep 3
adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
scroll_to_node "$ROW_DIR/.settings.xml" theme_background_choose 10 >/dev/null
tap_node "$ROW_DIR/.settings.xml" theme_background_choose; sleep 2.5
# The picker opens as a half-height sheet whose dump bounds do not match the screen until it settles open: swipe it
# fully up first (the first run's tap, from the half sheet's dump, landed on the scrim and dismissed the picker).
adb shell input swipe 540 850 540 150 400; sleep 2
qdump "$ROW_DIR/picker.xml"; screencap "$ROW_DIR/picker.png"
pick="$(python3 - "$ROW_DIR/picker.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); best = None
for n in re.finditer(r"<node[^>]*>", s):
    # a thumbnail's description, not the "Photos" tab's
    n = n.group(0); d = re.search(r'content-desc="(Photo taken on [^"]*)"', n); b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if d and b:
        x1, y1, x2, y2 = map(int, b.groups())
        if best is None or (y1, x1) < best[0]: best = ((y1, x1), (x1 + x2) // 2, (y1 + y2) // 2, d.group(1))
print(f"{best[1]} {best[2]} {best[3]}" if best else "")
PY
)"
note "picker: the newest photo: [$pick]"
read -r PX PY _ <<< "$pick"; tap_xy "$PX" "$PY"; sleep 3
scroll_to_node "$ROW_DIR/.settings.xml" theme_transparency 10 >/dev/null
T0="$(grep -oE 'content-desc="Tile transparency [0-9]+ %"' "$ROW_DIR/.settings.xml" | grep -oE '[0-9]+' | head -1)"
assert_ne "a background picture is set: the transparency slider is there" "" "$T0"
note "transparency at the start: ${T0}%"
set_transparency() { # percent -> the value the slider then reads
  adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
  scroll_to_node "$ROW_DIR/.settings.xml" theme_transparency 10 >/dev/null
  read -r sl st sr sb <<< "$(bounds "$ROW_DIR/.settings.xml" theme_transparency)"
  local x=$(( sl + (sr - sl) * $1 / 100 )); [ "$1" -eq 0 ] && x=$(( sl + 1 )); [ "$1" -eq 100 ] && x=$(( sr - 2 ))
  tap_xy "$x" $(( (st + sb) / 2 )); sleep 1; dump_ui "$ROW_DIR/.settings-after.xml"
  grep -oE 'content-desc="Tile transparency [0-9]+ %"' "$ROW_DIR/.settings-after.xml" | grep -oE '[0-9]+' | head -1
  adb shell input keyevent KEYCODE_HOME; sleep 2.5; ensure_start_page
}
for pct in 0 100; do
  xm="$(ring_mark)"
  got="$(set_transparency "$pct" | tail -1)"
  assert_eq "the slider reads ${pct} %" "$pct" "$got"
  stored="$(ring_since "$xm" | grep -F 'start theme changed' | tail -1 | grep -oE 'transparency=[0-9.E-]+' | cut -d= -f2)"
  assert_eq "the stored transparency is at the ${pct} % end (the round-2 re-judge, EV-13: $stored)" yes \
    "$(python3 -c "import sys; v=float(sys.argv[1]); print('yes' if (v <= 0.01 if $pct == 0 else v >= 0.99) else 'no')" "${stored:-nan}")"
  fresh_burst "tile:$A_KEY" "x5-$pct"; screencap "$ROW_DIR/x5-$pct.png"
  # The same screen without the satellites: Back closes the burst and edit mode stays (E5; a tap now leaves edit mode
  # too, 2026-09-25), so what lies behind each satellite square is read from the second capture.
  adb shell input keyevent KEYCODE_BACK; sleep 1.2
  qdump "$ROW_DIR/x5-$pct-closed.xml"; screencap "$ROW_DIR/x5-$pct-closed.png"
  assert_eq "X5 ${pct}%: the backdrop capture has edit mode and no burst" "yes no" \
    "$(has_node "$ROW_DIR/x5-$pct-closed.xml" edit_disc:unpin) $(has_node "$ROW_DIR/x5-$pct-closed.xml" quick_burst)"
  r="$(python3 - "$ROW_DIR/x5-$pct.png" "$ROW_DIR/x5-$pct-closed.png" "$ROW_DIR/x5-$pct.xml" "$pct" <<'PY'
import re, sys
import numpy as np
from PIL import Image
opn = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(float)
bak = np.asarray(Image.open(sys.argv[2]).convert("RGB")).astype(float)
s = open(sys.argv[3]).read(); pct = int(sys.argv[4])
accent = np.array([0.0, 120.0, 215.0])              # this baseline's accent (a tile at 0 % draws exactly this)
alpha = 1 - 0.8 * pct / 100                          # StartPage.kt: tileAlpha with a background picture
out, worst = [], 0.0
for i in range(4):
    m = re.search(r'resource-id="quick_sat:%d"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % i, s)
    if not m: continue
    l, t, r, b = map(int, m.groups())
    f = opn[t + 3:t + 9, l + 3:l + 9].reshape(-1, 3).mean(0); k = bak[t + 3:t + 9, l + 3:l + 9].reshape(-1, 3).mean(0)
    want = alpha * accent + (1 - alpha) * k; d = np.abs(f - want).max(); worst = max(worst, d)
    out.append(f"sat{i} fill={f.round().astype(int).tolist()} behind={k.round().astype(int).tolist()} want={want.round().astype(int).tolist()}")
print(f"alpha={alpha:.2f} " + "; ".join(out) + f" | max |d|={worst:.1f} -> {'PASS' if out and worst <= 14 else 'FAIL'}")
PY
)"
  note "X5 ${pct}%: $r"
  case "$r" in *PASS) _verdict PASS "X5 ${pct}%: every satellite is the accent at the tile's alpha over what lies behind it" "${r: -40}" ;; *) _verdict FAIL "X5 ${pct}%: every satellite is the accent at the tile's alpha over what lies behind it" "${r: -60}" ;; esac
  c6
done
set_transparency "${T0:-50}" >/dev/null
adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
scroll_to_node "$ROW_DIR/.settings.xml" theme_background_remove 10 >/dev/null
tap_node "$ROW_DIR/.settings.xml" theme_background_remove; sleep 1
adb shell rm -f /sdcard/Pictures/qa-edge-bg.png
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
adb shell input keyevent KEYCODE_HOME; sleep 2
c6

row_end
