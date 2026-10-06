#!/usr/bin/env bash
# Phase 17 E19, the Movies & TV part (row E19_VIDEO) — geometry and motion against R11 (T17-16 / T17-17; r3 D5 / V18).
# Dumps (px ÷ 3 = epx) and screencaps on the AVD. Tolerances as the doc gives them: ± 1 epx for R11 HIGH values (every
# number below is asserted at ± 1, the stricter of HIGH / MEDIUM), order and presence for LOW, colours ± 4 per channel,
# motion bounds ± 33 ms, maxGapMs ≤ 33.4 ms (C-31). The doc's clauses → this driver's legs:
#
#   chrome    library pages draw phase 01's status bar (C-17) and a 48-epx #171717 header; ≡ at x 24, the title at
#             x 60.25 (its ink), search at W − 24
#   pane      256 epx wide from the chrome bottom, #171717, no scrim (the page pixels right of the pane unchanged), rows
#             48 epx, glyph cx 24 (its ink), label x 48, the current row's 4 × 48-epx accent bar at x 0
#   tiles     My videos tiles 112 × 112 on a 124-epx pitch from x 12, first row top = chrome bottom + 48, 2 per row;
#             captions ≤ 2 lines clipped at tile left + 100 (a long-named copy of the fixture shows the wrap)
#   player    no status bar and no header, the nav bar drawn; the scrim band 120 epx above the nav bar; the track 2 epx
#             at nav − 93 from x 12 to 348; the thumb an accent ring Ø 22 with a 2-epx stroke, its centre 1.2 epx
#             below the track's; the played portion accent (± 4 of the hub's accent bar), the unplayed track white at
#             25 % (RECORDED: its pixel is a blend over the picture and the scrim); time labels below the track,
#             baseline nav − 67.6, H:MM:SS, elapsed left at 12.5, the REMAINING time right-aligned at 345.5, its value
#             = duration − position ± 1 s; transport centres back 10 W/2 − 48, play / pause W/2, forward 30 W/2 + 48,
#             full screen W − 84, "•••" W − 36 on a row centred at nav − 39.8; no CC node for qa-steps.mp4 alone, CC at
#             36 once qa-steps.srt is beside it; the menu's items Cast to device / Zoom to fill / Repeat / Autoplay in
#             that order; the video letterboxed (fit) by default
#   motion    the pane open settled at 167–200 ms, its close 67–100 ms; library → player a cut (no [motion] line for
#             it; the screenrecord's frames hold at most one in-between frame); controls fade-in 167–300 ms, hold
#             3.17–3.32 s, fade-out 367–400 ms; every line's maxGapMs ≤ 33.4 ms
#
# Restores: the pushed files (media_down), the long-named copy on the host, the device's media volume.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"
STATUS=28                      # BarMetrics.STATUS_EPX: phase 01's drawn status bar
LONG="qa-a-long-caption-that-has-to-wrap-over-more-than-two-lines-of-a-tile.mp4"
# The one host file this row makes. ${VAR:?} stops the shell instead of running rm on a path built from an empty name.
long_copy_remove() { rm -f "${GEN:?}/${LONG:?}"; }
trap long_copy_remove EXIT
cxy() { python3 -c 'import sys; l, t, r, b = [float(v) for v in sys.argv[1:5]]; print("%.1f %.1f" % ((l + r) / 2, (t + b) / 2))' "$@"; }
range_ok() { python3 -c 'import sys; v, lo, hi = float(sys.argv[1]), float(sys.argv[2]), float(sys.argv[3]); print("yes" if lo <= v <= hi else "no")' "$1" "$2" "$3" 2>/dev/null; }
assert_range() { # name value lo hi
  if [ "$(range_ok "$2" "$3" "$4")" = yes ]; then _verdict PASS "$1" "$2 in [$3, $4]"; else _verdict FAIL "$1" "[$2] is not in [$3, $4]"; fi
}

video_row_begin E19_VIDEO "geometry and motion, Movies & TV: the chrome, the pane, My videos, the player, the fades"
quiet_on
videos_grant
cp "$GEN/qa-steps.mp4" "$GEN/$LONG"
media_up qa-steps.mp4 "$LONG"
ID="$(media_id video qa-steps.mp4 Movies/)"; LID="$(media_id video "$LONG" Movies/)"
assert_ne "qa-steps.mp4 is in MediaStore" "" "$ID"
assert_ne "its long-named copy is in MediaStore" "" "$LID"
adb shell am force-stop app.tileshell; sleep 1

# ------------------------------------------------------------------------------------------------ the chrome
log "--- the library page's chrome"
adb shell am start -W -n "$VIDEO_ACTIVITY" >/dev/null 2>&1; sleep 3
dump_ui "$D/library.xml"; screencap "$D/library.png"
assert_eq "the library page (hub_page:myvideos)" "yes" "$(has_node "$D/library.xml" hub_page:myvideos)"
assert_eq "the library page draws phase 01's status bar (w10m_status_bar)" "yes" "$(has_node "$D/library.xml" w10m_status_bar)"
assert_near "the header: 48 epx under the status bar, full width" "0 $STATUS 360 $((STATUS + 48))" "$(epx "$D/library.xml" hub_header)" 1
assert_rgb "the header's fill is #171717" "23,23,23" "$(px "$D/library.png" 600 $(( (STATUS + 8) * 3 )))" 4
assert_rgb "the status bar's fill is #171717 (one band with the header)" "23,23,23" "$(px "$D/library.png" 540 9)" 4
# shellcheck disable=SC2046
assert_near "≡ at x 24 (its node's centre)" "24.0" "$(cxy $(epx "$D/library.xml" hub_menu) | cut -d' ' -f1)" 1
# shellcheck disable=SC2046
assert_near "search at W − 24 (its node's centre)" "336.0" "$(cxy $(epx "$D/library.xml" hub_search) | cut -d' ' -f1)" 1
INK="$(python3 - "$D/library.png" $(( STATUS * 3 )) $(( (STATUS + 48) * 3 )) <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); top, bottom = int(sys.argv[2]), int(sys.argv[3]); px = im.load()
xs = [x for x in range(160, 700) for y in range(top, bottom) if min(px[x, y]) > 150]
print("%.2f" % (min(xs) / 3) if xs else "")
PY
)"
assert_near "the title at x 60.25 (its ink's left edge in the header)" "60.25" "$INK" 1

# ------------------------------------------------------------------------------------------------ My videos
log "--- My videos: the tiles"
# shellcheck disable=SC2207
FIRST=($(grep -o 'resource-id="video_tile:[0-9]*"' "$D/library.xml" | sed 's/.*video_tile:\([0-9]*\)"/\1/' | head -3))
assert_near "the first tile: 112 × 112 at x 12, top = chrome bottom + 48" "12 $((STATUS + 96)) 124 $((STATUS + 208))" "$(epx "$D/library.xml" "video_tile:${FIRST[0]:-0}")" 1
assert_near "the second tile: the 124-epx pitch, the same row" "136 $((STATUS + 96)) 248 $((STATUS + 208))" "$(epx "$D/library.xml" "video_tile:${FIRST[1]:-0}")" 1
ROW1="$(python3 - "$D/library.xml" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
tops = [int(m.group(2)) for m in re.finditer(r'resource-id="video_tile:\d+"[^>]*bounds="\[(\d+),(\d+)\]', x)]
print(tops.count(tops[0]) if tops else 0)
PY
)"
assert_eq "2 tiles per row (the tiles sharing the first row's top)" "2" "$ROW1"
scroll_to_node "$D/caps.xml" "video_caption:$LID" 12
[ "$(has_node "$D/caps.xml" "video_caption:$ID")" = yes ] || { adb shell input swipe 540 1500 540 1300 300; sleep 1; dump_ui "$D/caps.xml"; }
# shellcheck disable=SC2046
set -- $(epx "$D/caps.xml" "video_tile:$LID"); TL="${1:-0}"
# shellcheck disable=SC2046
set -- $(epx "$D/caps.xml" "video_caption:$LID"); CL="${1:-0}"; CR="${3:-0}"; CH="$(python3 -c "print(round(${4:-0} - ${2:-0}, 1))")"
# shellcheck disable=SC2046
set -- $(epx "$D/caps.xml" "video_caption:$ID"); H1="$(python3 -c "print(round(${4:-0} - ${2:-0}, 1))")"
record "a long caption's box (left, right, height) and a one-line caption's height (epx)" "$CL $CR $CH / $H1"
assert_near "a caption starts at its tile's left and is clipped at tile left + 100" "$TL $(python3 -c "print($TL + 100)")" "$CL $CR" 1
# Two lines are one line's box plus one line pitch (38.6 against 18.0 epx on this build); a third line would add another.
assert_eq "a long caption wraps, and to no more than 2 lines (its box is more than 1.5 and less than 2.5 one-line boxes)" "yes" "$(python3 -c "print('yes' if $H1 > 0 and $H1 * 1.5 < $CH < $H1 * 2.5 else 'no')")"

# ------------------------------------------------------------------------------------------------ the pane
log "--- the ≡ pane"
adb shell input swipe 540 900 540 2000 200; sleep 1
screencap "$D/pane-before.png"
MARKP="$(ring_mark)"
dump_ui "$D/pre-pane.xml"; tap_node "$D/pre-pane.xml" hub_menu; sleep 1.2
dump_ui "$D/pane.xml"; screencap "$D/pane.png"
NAVH="$(epx "$D/pane.xml" w10m_nav_bar | cut -d' ' -f2)"
assert_near "the pane: 256 epx wide, from the chrome bottom to the nav bar" "0 $((STATUS + 48)) 256 $NAVH" "$(epx "$D/pane.xml" hub_pane)" 1
assert_rgb "the pane's fill is #171717" "23,23,23" "$(px "$D/pane.png" 600 1500)" 4
SAME="$(python3 - "$D/pane-before.png" "$D/pane.png" $(( (STATUS + 48) * 3 )) <<'PY'
import sys
from PIL import Image, ImageChops
a = Image.open(sys.argv[1]).convert("RGB"); b = Image.open(sys.argv[2]).convert("RGB")
box = (780, int(sys.argv[3]), 1080, 2000)
print("same" if ImageChops.difference(a.crop(box), b.crop(box)).getbbox() is None else "different")
PY
)"
assert_eq "no scrim: the page pixels right of the pane are unchanged" "same" "$SAME"
assert_near "a row is 48 epx: My videos' row from the chrome bottom" "0 $((STATUS + 48)) 256 $((STATUS + 96))" "$(epx "$D/pane.xml" hub_pane:myvideos)" 1
assert_near "the current row's accent bar: 4 × 48 epx at x 0" "0 $((STATUS + 48)) 4 $((STATUS + 96))" "$(epx "$D/pane.xml" hub_pane_bar)" 1
assert_near "a row's label at x 48" "48.0" "$(epx "$D/pane.xml" hub_pane_label:myvideos | cut -d' ' -f1)" 1
GLYPH="$(python3 - "$D/pane.png" $(( (STATUS + 48) * 3 )) $(( (STATUS + 96) * 3 )) <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); top, bottom = int(sys.argv[2]), int(sys.argv[3]); px = im.load()
# The glyph's ink: whatever differs from the pane's fill, right of the 4-epx accent bar and left of the label (x 48).
xs = [x for x in range(18, 132) for y in range(top + 6, bottom - 6) if max(abs(c - 23) for c in px[x, y]) > 60]
print("%.1f" % ((min(xs) + max(xs) + 1) / 6) if xs else "")
PY
)"
assert_near "a row's glyph: its ink's centre at x 24" "24.0" "$GLYPH" 1
ACCENT="$(px "$D/pane.png" 6 $(( (STATUS + 72) * 3 )))"
record "the accent colour (the pane's bar)" "$ACCENT"
adb shell input tap 960 1200; sleep 1
PM="$(vring "$MARKP" | grep -F '[motion] pane_' | sed 's/.*\[motion\] //')"
record "the pane's motion lines" "$(printf '%s' "$PM" | tr '\n' '|')"
mval() { printf '%s\n' "$PM" | grep -F "$1 " | tail -1 | sed -n "s/.*$2=\([0-9.]*\).*/\1/p"; }
assert_range "pane open settled at 167–200 ms (± 33)" "$(mval pane_open settle)" 134 233
assert_range "pane close in 67–100 ms (± 33)" "$(mval pane_close settle)" 34 133
assert_range "pane open: maxGapMs ≤ 33.4" "$(mval pane_open maxGapMs)" 0 33.4
assert_range "pane close: maxGapMs ≤ 33.4" "$(mval pane_close maxGapMs)" 0 33.4

# ------------------------------------------------------------------------------------------------ library → player
log "--- library → player: a cut"
scroll_to_node "$D/mine.xml" "video_tile:$ID" 12
adb shell "screenrecord --time-limit 4 --bit-rate 4000000 /sdcard/Download/p17-e19-cut.mp4" >/dev/null 2>&1 &
REC=$!
sleep 1.2
MARK="$(ring_mark)"
tap_node "$D/mine.xml" "video_tile:$ID"
LINE="$(await_vline "$MARK" "[video] playing $ID" 100)"
assert_contains "the tile opens the player ([video] playing $ID)" "[video] playing $ID" "$LINE"
wait "$REC" 2>/dev/null
adb pull /sdcard/Download/p17-e19-cut.mp4 "$D/cut.mp4" >/dev/null 2>&1; adb shell rm -f /sdcard/Download/p17-e19-cut.mp4
OPEN_MOTION="$(vring "$MARK" | grep -F '[motion]' | grep -vF 'controls_fade' | sed 's/.*\[motion\] //')"
assert_eq "no [motion] line for library → player (only the controls' fade is timed)" "" "$OPEN_MOTION"
CUT="$(python3 - "$D/cut.mp4" <<'PY'
import subprocess, sys
W, H = 90, 195
raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", sys.argv[1], "-vf", "scale=%d:%d" % (W, H), "-pix_fmt", "gray", "-f", "rawvideo", "-"], capture_output=True).stdout
n = len(raw) // (W * H)
if n < 3: print("frames=%d" % n); sys.exit()
f = [raw[i * W * H:(i + 1) * W * H] for i in range(n)]
lit = [i for i in range(W * H) if f[0][i] > 60]            # the library page's own ink: tiles, captions, header
sim = [sum(1 for i in lit if abs(fr[i] - f[0][i]) <= 24) / max(1, len(lit)) for fr in f]
gone = next((k for k, s in enumerate(sim) if s < 0.15), None)
between = [k for k, s in enumerate(sim) if 0.15 <= s <= 0.85 and (gone is None or k < gone)]
print("frames=%d lit=%d first_gone=%s in_between=%d" % (n, len(lit), gone, len(between)))
PY
)"
record "the screenrecord around the tap (frames, the library's lit pixels, the first frame without them, frames in between)" "$CUT"
assert_eq "the screenrecord shows the library page going (a frame without it exists)" "yes" "$(echo "$CUT" | grep -qE 'first_gone=[0-9]+' && echo yes || echo no)"
assert_range "a cut: at most one in-between frame in the screenrecord" "$(echo "$CUT" | sed -n 's/.*in_between=\([0-9]*\).*/\1/p')" 0 1

# ------------------------------------------------------------------------------------------------ the player
log "--- the player's chrome (paused)"
show_paused "$D/paused.xml"; screencap "$D/paused.png"
assert_eq "the player's page (player_root): no status bar and no header node, the nav bar drawn" "yes no no yes" \
  "$(has_node "$D/paused.xml" player_root) $(has_node "$D/paused.xml" w10m_status_bar) $(has_node "$D/paused.xml" hub_header) $(has_node "$D/paused.xml" w10m_nav_bar)"
NAV="$(epx "$D/paused.xml" w10m_nav_bar | cut -d' ' -f2)"
record "the nav bar's top (epx)" "$NAV"
geo() { python3 - "$NAV" "$@" <<'PY'
import sys
if len(sys.argv) < 7: print(""); sys.exit()
nav = float(sys.argv[1]); what = sys.argv[2]; l, t, r, b = [float(v) for v in sys.argv[3:7]]
if what == "scrim": print("%.1f %.1f" % (nav - t, b - nav))
elif what == "track": print("%.1f %.1f %.1f %.1f" % (l, r, nav - (t + b) / 2, b - t))
elif what == "centre": print("%.1f %.1f" % ((l + r) / 2, nav - (t + b) / 2))
elif what == "size": print("%.1f %.1f" % (r - l, b - t))
PY
}
# shellcheck disable=SC2046
assert_near "the scrim band: 120 epx above the nav bar, ending at it" "120.0 0.0" "$(geo scrim $(epx "$D/paused.xml" player_scrim))" 1
# shellcheck disable=SC2046
assert_near "the track: x 12 → 348, at nav − 93, 2 epx" "12.0 348.0 93.0 2.0" "$(geo track $(epx "$D/paused.xml" player_track))" 1
# shellcheck disable=SC2046
assert_near "the thumb: Ø 22" "22.0 22.0" "$(geo size $(epx "$D/paused.xml" player_thumb))" 1
# shellcheck disable=SC2046
THUMB="$(geo centre $(epx "$D/paused.xml" player_thumb))"
assert_near "the thumb's centre 1.2 epx below the track's (nav − 91.8)" "91.8" "${THUMB#* }" 1
assert_near "the elapsed label's left at 12.5" "12.5" "$(epx "$D/paused.xml" player_elapsed | cut -d' ' -f1)" 1
assert_near "the remaining label right-aligned at 345.5" "345.5" "$(epx "$D/paused.xml" player_remaining | cut -d' ' -f3)" 1
for spec in "player_back10 132.0" "player_playpause 180.0" "player_fwd30 228.0" "player_fullscreen 276.0" "player_more 324.0"; do
  # shellcheck disable=SC2086
  set -- $spec
  # shellcheck disable=SC2046
  assert_near "$1: centre x $2 (W/2 − 48, W/2, W/2 + 48, W − 84, W − 36), on the row at nav − 39.8" "$2 39.8" "$(geo centre $(epx "$D/paused.xml" "$1"))" 1
done
assert_eq "no CC node for qa-steps.mp4 alone (no text track)" "no" "$(has_node "$D/paused.xml" player_cc)"
E="$(node_text "$D/paused.xml" player_elapsed)"; R="$(node_text "$D/paused.xml" player_remaining)"
record "the labels while paused (elapsed / remaining)" "$E / $R"
assert_eq "the labels are H:MM:SS (en-US)" "yes" "$(echo "$E $R" | grep -qE '^[0-9]:[0-9]{2}:[0-9]{2} [0-9]:[0-9]{2}:[0-9]{2}$' && echo yes || echo no)"
secs() { python3 -c 'import sys; h, m, s = [int(v) for v in sys.argv[1].split(":")]; print(h * 3600 + m * 60 + s)' "$1" 2>/dev/null || echo 0; }
assert_within "the remaining label's value = duration − position (elapsed + remaining = 10 s) ± 1 s" 10 "$(( $(secs "$E") + $(secs "$R") ))" 1
# shellcheck disable=SC2046
assert_near "the picture is letterboxed (fit): 360 × 202.5 epx" "360.0 202.5" "$(geo size $(epx "$D/paused.xml" video_surface))" 1
PIX="$(python3 - "$D/paused.png" "$D/paused.xml" "$ACCENT" <<'PY'
import re, sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); px = im.load(); x = open(sys.argv[2], encoding="utf-8", errors="replace").read()
try: acc = tuple(int(v) for v in sys.argv[3].split(","))
except ValueError: acc = (0, 0, 0)
def b(tag):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % tag, x); return [int(v) for v in m.groups()] if m else None
near = lambda p: all(abs(a - c) <= 12 for a, c in zip(p, acc))
tr, th, pl, el = b("player_track"), b("player_thumb"), b("player_played"), b("player_elapsed")
if not (tr and th and el): sys.exit()
ty = (tr[1] + tr[3]) // 2
print("played", "%d,%d,%d" % px[(pl[0] + th[0]) // 2, ty] if pl and th[0] - pl[0] > 12 else "none")
print("unplayed", "%d,%d,%d" % px[(th[2] + tr[2]) // 2, ty])
cy = (th[1] + th[3]) // 2
run = 0
for xx in range(th[0] - 2, (th[0] + th[2]) // 2):
    if near(px[xx, cy]): run += 1
    elif run: break
print("stroke", "%.2f" % (run / 3))
ys = [y for y in range(el[1], el[3]) for xx in range(el[0], el[2]) if min(px[xx, y]) > 170]
print("baseline", "%.2f" % ((max(ys) + 1) / 3) if ys else "")
PY
)"
pv() { printf '%s\n' "$PIX" | sed -n "s/^$1 //p"; }
assert_rgb "the played portion is the accent colour (the hub's accent bar) ± 4" "$ACCENT" "$(pv played)" 4
record "RECORDED — the unplayed track's pixel (white at 25 % is a blend over the scrim and the picture, so no single value is asserted)" "$(pv unplayed)"
assert_near "the thumb ring's stroke: 2 epx (the accent run at its left edge, from the screencap)" "2.0" "$(pv stroke)" 1
assert_near "the time labels' baseline at nav − 67.6 (the digits' ink bottom)" "67.6" "$(python3 -c "print(round($NAV - $(pv baseline), 2))" 2>/dev/null)" 1

log "--- the menu"
tap_node "$D/paused.xml" player_more; sleep 0.7
gdump "$D/menu.xml"
assert_eq "the menu's items in order" "cast zoom repeat autoplay" "$(grep -o 'resource-id="player_menu:[a-z]*"' "$D/menu.xml" | sed 's/.*://;s/"//' | xargs)"
assert_eq "… reading Cast to device / Zoom to fill / Repeat / Autoplay" "Cast to device / Zoom to fill / Repeat / Autoplay" "$(python3 - "$D/menu.xml" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
print(" / ".join(t for t in re.findall(r'text="([^"]+)"', x) if t in ("Cast to device", "Zoom to fill", "Repeat", "Autoplay")))
PY
)"
adb shell input tap 200 600; sleep 0.6

log "--- the controls' fade"
MARK2="$(ring_mark)"
gdump "$D/before-resume.xml"
# shellcheck disable=SC2046
set -- $(centre_px "$D/before-resume.xml" player_track); adb shell input tap 60 "${2:-1917}"; sleep 0.5
adb shell input tap 540 2077
sleep 4.6
adb shell input tap 540 600
sleep 4.4
vring "$MARK2" | grep -F '[motion] controls_fade' | sed 's/.*\[motion\] //' > "$D/fades.txt"
log "the controls_fade lines:"; tee -a "$LOG" < "$D/fades.txt"
python3 - "$D/fades.txt" > "$D/fade-check.txt" <<'PY'
import re, sys
rows = [{k: float(v) for k, v in re.findall(r'(\w+)=(-?[\d.]+)', l)} for l in open(sys.argv[1]) if 'controls_fade' in l]
outs = [r for r in rows if r["settle"] > 330]; ins = [r for r in rows if r["settle"] <= 330]
print("in_settle", int(ins[-1]["settle"]) if ins else -1)
print("out_settle", int(outs[-1]["settle"]) if outs else -1)
print("hold", int(outs[-1]["t0"] - (ins[-1]["t0"] + ins[-1]["settle"])) if ins and outs and outs[-1]["t0"] > ins[-1]["t0"] else -1)
print("max_gap", max(r["maxGapMs"] for r in rows) if rows else -1)
PY
val() { sed -n "s/^$1 //p" "$D/fade-check.txt"; }
assert_range "controls fade-in 167–300 ms (± 33)" "$(val in_settle)" 134 333
assert_range "hold ≈3.2 s: 3.17–3.32 s (± 33 ms)" "$(val hold)" 3137 3353
assert_range "fade-out 367–400 ms (± 33)" "$(val out_settle)" 334 433
assert_range "every controls_fade line's maxGapMs ≤ 33.4" "$(val max_gap)" 0 33.4
rings_save
adb shell input keyevent KEYCODE_BACK; sleep 1.2

# ------------------------------------------------------------------------------------------------ CC
log "--- CC once qa-steps.srt is beside the file"
adb push "$GEN/qa-steps.srt" /sdcard/Movies/qa-steps.srt >/dev/null; MEDIA_PUSHED+=("/sdcard/Movies/qa-steps.srt"); media_scan
MARK="$(ring_mark)"
play_from_hub "$ID" "$D/cc"
await_vline "$MARK" "[video] playing $ID" 100 >/dev/null
show_paused "$D/cc.xml"
assert_contains "the .srt beside the file is found" "subtitle file beside $ID: found" "$(vring "$MARK")"
assert_eq "the CC node is there" "yes" "$(has_node "$D/cc.xml" player_cc)"
# shellcheck disable=SC2046
assert_near "CC at x 36, on the transport row (nav − 39.8)" "36.0 39.8" "$(geo centre $(epx "$D/cc.xml" player_cc))" 1
rings_save
adb shell input keyevent KEYCODE_BACK; sleep 1

log "--- restore"
assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
media_down
long_copy_remove
videos_grant_restore
quiet_off
c6; ensure_start
row_end
