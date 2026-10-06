#!/usr/bin/env bash
# Phase 17 E19, the Camera's part and the Pro dial's (row E19_CAMERA) — geometry and motion against R11.
# Dumps (px ÷ 3 = epx; 360 × 780 epx, the nav bar's top at 732, H/2 = 390) and screencaps on the AVD. Tolerances are
# the row's: the Camera's Y3 values ± 2 epx (MEDIUM, "all ± 2"), the dial's ± 1 epx, colours ± 4 per channel, motion
# bounds ± 33 ms, and every [motion] line's maxGapMs ≤ 33.4 ms (C-31).
#
#   Y3 photo   no drawn status-bar node; the nav bar 48 epx; the photo preview 4:3 fitted to the width, its centre at
#              (W/2, H/2); the shutter disc 72 epx at W/2, its centre 56 above the nav top, white fill with the #2B2B2B
#              ring at r 32–34 (pixels); the mode disc 32 epx at W/2 + 60, centre 36 above the nav top — INDEX Change Log
#              2026-10-05 14:27 (8): with two capture modes ONE disc shows at a time, right from photo and left from
#              video, and the photo capsule has three items on the AVD (no HDR); the capsule on the right edge, 2 →
#              45.5 epx from it, its glyph centres 24 from the right at H/2 − 43.7 / + 0.5 / + 45.1 (three items; the
#              four-item form − 66 / − 22 / + 22 / + 66 when the list derived in E7 holds HDR), in Y3's order; the
#              settings disc 24 from the right, 27.9 above the nav top; the camera roll 24.7 from the left, 28 above;
#              no camera switch on the AVD (its place is P1's) and no horizontal mode-strip node.
#   Y3 video   after the mode switch: the previous-mode disc at W/2 − 60; the video capsule's items RECORDED (the doc
#              gives centres for four and three items; the AVD's video capsule has two — Change Log (8)), order and
#              presence asserted.
#   panorama   "no capsule node in panorama": panorama is never on x86_64 — RECORDED as the phone's (P9 / P10).
#   settings   the header's cap 16.5–16.75 epx at x 12; combo boxes 32 epx tall with a 2-epx border, the chevron 22.25
#              from the right; label → label pitch 80; an open list's items at a 44-epx pitch, the current one
#              accent-filled; "Framing grid" and "Capture living images" present; no Lenses, OneDrive or "Related
#              settings" row.
#   Y4 dial    where E7's derived list shows Pro (read here by the same derivation, and logged): five arcs fitted from
#              the screencap — centre (W/2, nav top), radii 130.5 / 195.4 / 260.3 / 325.3 / 390.2 ± 1 — inner → outer
#              exposure / shutter / ISO / focus / WB, each ring's icon node on its ring; value labels centred at W/2,
#              30.3 ± 1 above each ring's top; the shutter 74.75 above the nav top in the five-ring view; one control
#              alone = one arc of r 130.25 ± 1. When the camera admits no control the sub-row is the phone's (P9) and
#              the row logs that.
#   motion     the mode switch a slide of 300 ms (± 33); capture feedback black for 167 ms (± 33) with the chrome
#              unchanged; the dial-open sweep 333 ms (± 33); each line's maxGapMs ≤ 33.4. A screenrecord of the
#              capture and of the mode switch corroborates and is never the clock: the frames in which the preview is
#              black are counted (RECORDED), and at least one of the capture's black frames must show the shutter's
#              chrome unchanged.
#
# Changes on the device: two stills (removed by media_down). No setting is left changed (the grid list is opened and
# closed on its current item).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/cam17.sh"
NAV_TOP=732; W=360; HALF=390

# The pixel of a screencap at an epx point, as r,g,b.
px_epx() { px "$1" "$(python3 -c "print(int(round($2*3)))")" "$(python3 -c "print(int(round($3*3)))")"; }
motion_line() { cam_since "$1" | grep -o "\[motion\] $2 .*" | head -1 | sed 's/ *wall=.*//'; }
motion_field() { echo "$1" | grep -o "$2=[0-9.]*" | head -1 | cut -d= -f2; }
assert_motion() { # label line settle-ms
  record "$1: the line" "${2:-(none)}"
  assert_ne "$1: its [motion] line is logged" "" "$2"
  assert_within "$1: settle $3 ms ± 33" "$3" "$(motion_field "$2" settle)" 33
  local gap; gap="$(motion_field "$2" maxGapMs)"
  if [ -n "$gap" ] && python3 -c "import sys; sys.exit(0 if float('$gap') <= 33.4 else 1)"; then _verdict PASS "$1: maxGapMs ≤ 33.4 (C-31)" "$gap"; else _verdict FAIL "$1: maxGapMs ≤ 33.4 (C-31)" "${gap:-none}"; fi
}
# A screenrecord around one action: started, 1.2 s of lead, the action, the rest of the 5 s; pulled to <out>.mp4.
record_around() { # out-prefix command...
  local out="$1" dev="/sdcard/Download/p17cam_$(date +%s%N).mp4"; shift
  adb shell screenrecord --time-limit 5 "$dev" > /dev/null 2>&1 &
  local rec=$!
  sleep 1.2
  "$@"
  wait "$rec"
  adb shell cat "$dev" > "$out.mp4" 2>/dev/null; adb shell rm -f "$dev" >/dev/null 2>&1
}
# The frames (resampled to 60 a second, never the clock) in which the preview's centre is black, and how many of
# those still show the shutter's white fill: "black=<n> black_ms=<n> black_with_chrome=<n> frames=<n>".
black_frames() { # video.mp4 chrome-x-epx chrome-y-epx
  ffmpeg -v error -i "$1" -vf "fps=60,scale=360:780" -f rawvideo -pix_fmt rgb24 - 2>/dev/null | python3 -c '
import sys
w, h = 360, 780
cx, cy = int(float(sys.argv[1])), int(float(sys.argv[2]))
data = sys.stdin.buffer.read(); n = len(data) // (w * h * 3)
black = chrome = 0
for i in range(n):
    f = data[i * w * h * 3:(i + 1) * w * h * 3]
    def at(x, y): o = (y * w + x) * 3; return f[o], f[o + 1], f[o + 2]
    centre = [at(x, y) for x in (150, 180, 210) for y in (330, 390, 450)]
    if all(max(p) < 24 for p in centre):
        black += 1
        if min(at(cx, cy)) > 200: chrome += 1
print("black=%d black_ms=%d black_with_chrome=%d frames=%d" % (black, round(black * 1000 / 60), chrome, n))' "$2" "$3"
}

cam_install E19_CAMERA
row_begin E19_CAMERA "E19, Camera and the Pro dial: Y3 / Y4 / settings geometry, the camera's motions"
cam_preamble
D="$ROW_DIR"
layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "start: layout_restore of the baseline" "0" "$?"
ensure_start
media_up
fresh_camera
SVC="$(media_camera)"; printf '%s\n' "$SVC" > "$D/media_camera.txt"
ABI="$(adb shell getprop ro.product.cpu.abi < /dev/null | tr -d '\r')"
[ "$(adb shell pm list libraries | tr -d '\r' | grep -c 'androidx.camera.extensions.impl')" -ge 1 ] && HDR=yes || HDR=no
EXPECTED="$(printf '%s\n' "$SVC" | python3 "$CAM_DEV/derive_modes.py" "$HDR" "$ABI")"
PRO_WANT="$(printf '%s\n' "$SVC" | python3 "$CAM_DEV/derive_modes.py" --pro)"
record "the mode list derived as E7 derives it (ABI $ABI, HDR $HDR)" "$EXPECTED"
record "the dial controls r3 D10 admits" "$PRO_WANT"

# ----------------------------------------------------------------------------------------------- Y3: photo
log "--- Y3: the viewfinder in Photo"
cgdump "$D/vf.xml"; screencap "$D/vf.png"; CHK_DUMP="$D/vf.xml"
assert_eq "Y3: the viewfinder is the page (camera_root, camera_shutter)" "yes yes" "$(has_node "$D/vf.xml" camera_root) $(has_node "$D/vf.xml" camera_shutter)"
assert_eq "Y3: the dumped list equals the derived one" "$EXPECTED" "$(ids_with "$D/vf.xml" camera_mode:)"
assert_eq "Y3: no drawn status-bar node" "no" "$(has_node "$D/vf.xml" w10m_status_bar)"
assert_eq "Y3: the nav bar is drawn" "yes" "$(has_node "$D/vf.xml" w10m_nav_bar)"
# shellcheck disable=SC2046
set -- $(bounds "$D/vf.xml" w10m_nav_bar)
assert_eq "Y3: the nav bar is 48 epx, its top at $NAV_TOP epx" "144 $((NAV_TOP * 3))" "$(( ${4:-0} - ${2:-0} )) ${2:-0}"
assert_eq "Y3: the photo preview is 4:3 fitted to the width (360 × 480 epx)" "360.0 480.0" "$(size_epx "$D/vf.xml" camera_preview)"
chk_centre "Y3: the preview's centre (W/2, H/2)" camera_preview 180 $HALF 2
chk_centre "Y3: the shutter's centre (W/2, nav top − 56)" camera_shutter 180 $((NAV_TOP - 56)) 2
assert_eq "Y3: the shutter disc is 72 epx" "72.0 72.0" "$(size_epx "$D/vf.xml" camera_shutter)"
SY=$((NAV_TOP - 56))
assert_rgb "Y3: the shutter's fill is white (r 20)" "255,255,255" "$(px_epx "$D/vf.png" 200 $SY)" 4
assert_rgb "Y3: the #2B2B2B ring at r 33 (32–34)" "43,43,43" "$(px_epx "$D/vf.png" 213 $SY)" 4
assert_rgb "Y3: white again outside the ring (r 35.3)" "255,255,255" "$(px_epx "$D/vf.png" 215.33 $SY)" 4
record "Y3: shutter pixels at r 20 / 31 / 33 / 35.3 / 40" "$(for r in 20 31 33 35.33 40; do px_epx "$D/vf.png" "$(python3 -c "print(180+$r)")" $SY; done | xargs)"
assert_eq "Y3: one mode disc from Photo — the next mode's, on the right (Change Log (8))" "video" "$(ids_with "$D/vf.xml" camera_disc:)"
chk_centre "Y3: the mode disc (W/2 + 60, nav top − 36)" "camera_disc:video" 240 $((NAV_TOP - 36)) 2
assert_eq "Y3: the mode disc is 32 epx" "32.0 32.0" "$(size_epx "$D/vf.xml" "camera_disc:video")"
chk_centre "Y3: the settings disc (W − 24, nav top − 27.9)" camera_settings 336 704.1 2
chk_centre "Y3: the camera roll (24.7, nav top − 28)" camera_roll 24.7 704 2
ITEMS="$(ids_with "$D/vf.xml" camera_capsule: | tr ' ' '\n' | grep -v ':' | xargs)"; record "Y3: the photo capsule's items" "$ITEMS"
# shellcheck disable=SC2046
set -- $(bounds "$D/vf.xml" camera_capsule)
assert_within "Y3: the capsule's right edge 2 epx from the screen's" "358" "$(python3 -c "print(${3:-0}/3)")" 2
assert_within "Y3: the capsule's left edge 45.5 epx from the screen's right" "314.5" "$(python3 -c "print(${1:-0}/3)")" 2
if [ "$HDR" = yes ]; then
  assert_eq "Y3: four capsule items in Y3's order" "flash hdr timer more" "$ITEMS"
  i=0; for off in -66 -22 22 66; do i=$((i + 1)); chk_centre "Y3: capsule glyph $i (W − 24, H/2 $off)" "camera_capsule:$(echo "$ITEMS" | cut -d' ' -f$i)" 336 "$(python3 -c "print($HALF+$off)")" 2; done
else
  assert_eq "Y3: three capsule items in Y3's order (no HDR on this camera)" "flash timer more" "$ITEMS"
  i=0; for off in -43.7 0.5 45.1; do i=$((i + 1)); chk_centre "Y3: capsule glyph $i (W − 24, H/2 $off)" "camera_capsule:$(echo "$ITEMS" | cut -d' ' -f$i)" 336 "$(python3 -c "print($HALF+$off)")" 2; done
fi
assert_eq "Y3: no camera switch on the AVD (no front camera; its place is P1's)" "no" "$(has_node "$D/vf.xml" camera_switch)"
assert_eq "Y3: no horizontal mode-strip node" "0" "$(grep -c 'camera_mode_strip' "$D/vf.xml")"
record "Y3: panorama's \"no capsule node\"" "not on this AVD — panorama is never on x86_64 (list: $EXPECTED); the phone's row (P9 / P10)"

# ----------------------------------------------------------------------------------------------- motion: capture feedback
log "--- motion: capture feedback"
XY="$(centre_px "$D/vf.xml" camera_shutter)"
MARK="$(ring_mark)"
# shellcheck disable=SC2086
record_around "$D/capture" adb shell input tap $XY
sleep 3
M="$(motion_line "$MARK" capture_feedback)"
assert_motion "capture feedback" "$M" 167
BF="$(black_frames "$D/capture.mp4" 200 $SY)"; record "capture feedback in the screenrecord (60-fps resample; corroborates only)" "$BF"
if [ "$(echo "$BF" | sed -n 's/.*black_with_chrome=\([0-9]*\).*/\1/p')" -ge 1 ] 2>/dev/null; then
  _verdict PASS "capture feedback: the preview goes black with the chrome unchanged (the shutter's fill in a black frame)" "$BF"
else
  _verdict FAIL "capture feedback: the preview goes black with the chrome unchanged (the shutter's fill in a black frame)" "$BF"
fi
cgdump "$D/after-capture.xml"
assert_eq "capture feedback: the chrome's nodes are where they were" "$(bounds "$D/vf.xml" camera_shutter) $(bounds "$D/vf.xml" camera_settings) $(bounds "$D/vf.xml" camera_capsule)" "$(bounds "$D/after-capture.xml" camera_shutter) $(bounds "$D/after-capture.xml" camera_settings) $(bounds "$D/after-capture.xml" camera_capsule)"

# ----------------------------------------------------------------------------------------------- settings
log "--- the settings page"
tap_node "$D/after-capture.xml" camera_settings; sleep 1.5
cgdump "$D/set.xml"; screencap "$D/set.png"
assert_eq "settings: the page" "yes" "$(has_node "$D/set.xml" camera_settings_page)"
assert_eq "settings: the header's text" "SETTINGS" "$(node_text "$D/set.xml" camera_settings_title)"
adb shell input swipe 540 1700 540 900 300; sleep 1; cgdump "$D/set-lower.xml"; adb shell input swipe 540 900 540 1700 300; sleep 1
TEXTS="$(cat "$D/set.xml" "$D/set-lower.xml" | grep -o 'text="[^"]*"' | sed 's/text="//;s/"$//' | grep -v '^$' | sort -u | tr '\n' '|')"
record "settings: the page's texts (both screens)" "$TEXTS"
for t in "Framing grid" "Capture living images"; do assert_contains "settings: the \"$t\" row is present" "|$t|" "|$TEXTS"; done
for t in "Lenses" "OneDrive" "Related settings"; do assert_absent "settings: no $t row" "$t" "$TEXTS"; done
# shellcheck disable=SC2046
INK="$(python3 - "$D/set.png" $(bounds "$D/set.xml" camera_settings_title) <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('L'); l, t, r, b = map(int, sys.argv[2:6])
box = im.crop((l, t, r, b)).point(lambda v: 255 if v > 128 else 0).getbbox()
print('%.2f %.2f %.2f' % ((l + box[0]) / 3, (t + box[1]) / 3, (box[3] - box[1]) / 3))
PY
)"; record "settings: the header's ink — left, cap top, cap height (epx)" "$INK"
# shellcheck disable=SC2086
set -- $INK
assert_within "settings: the header at x 12" 12 "${1:-none}" 1
assert_within "settings: the header's cap 16.5–16.75 epx" 16.625 "${3:-none}" 1
# shellcheck disable=SC2046
set -- $(bounds "$D/set.xml" "camera_set:aspect")
assert_eq "settings: a combo box is 32 epx tall" "96" "$(( ${4:-0} - ${2:-0} ))"
BORDER="$(python3 -c "
from PIL import Image
im=Image.open('$D/set.png').convert('RGB'); l,t=${1:-0},${2:-0}
print(im.getpixel((l+300,t+1)), im.getpixel((l+300,t+4)), im.getpixel((l+300,t+5)) == im.getpixel((l+300,t+1)), im.getpixel((l+300,t+7)) == im.getpixel((l+300,t+1)))")"
record "settings: the box's top edge at +1 and +4 px, and whether +5 px / +7 px are still the border" "$BORDER"
assert_contains "settings: a 2-epx border (6 px of one colour, the page inside it)" ") True False" "$BORDER"
A_Y="$(bounds "$D/set.xml" "camera_set_label:aspect" | cut -d' ' -f2)"; G_Y="$(bounds "$D/set.xml" "camera_set_label:grid" | cut -d' ' -f2)"; S_Y="$(bounds "$D/set.xml" "camera_set_label:size" | cut -d' ' -f2)"
assert_within "settings: label → label pitch 80 epx (aspect → grid)" 80 "$(python3 -c "print((${G_Y:-0}-${A_Y:-0})/3)")" 1
assert_within "settings: label → label pitch 80 epx (grid → size)" 80 "$(python3 -c "print((${S_Y:-0}-${G_Y:-0})/3)")" 1
# shellcheck disable=SC2046
set -- $(centre_epx "$D/set.xml" "camera_set_chevron:aspect")
assert_within "settings: the chevron 22.25 epx from the right" 337.75 "${1:-none}" 1
GRID_NOW="$(node_text "$D/set.xml" camera_set_value:grid)"
tap_node "$D/set.xml" "camera_set:grid"; sleep 1
cgdump "$D/list.xml"; screencap "$D/list.png"
I0="$(bounds "$D/list.xml" "camera_set_item:grid:0")"; I1="$(bounds "$D/list.xml" "camera_set_item:grid:1")"
assert_within "settings: an open list's items at a 44-epx pitch" 44 "$(python3 -c "print(($(echo "${I1:-0 0}" | cut -d' ' -f2)-$(echo "${I0:-0 0}" | cut -d' ' -f2))/3)")" 1
CUR=0; [ "$(node_attr "$D/list.xml" camera_set_item:grid:1 selected)" = true ] && CUR=1
assert_eq "settings: the current item is the selected one" "true" "$(node_attr "$D/list.xml" "camera_set_item:grid:$CUR" selected)"
FILL="$(python3 -c "
from PIL import Image
im=Image.open('$D/list.png').convert('RGB')
a=[int(v) for v in '$I0'.split()]; b=[int(v) for v in '$I1'.split()]
f=lambda q: '%d,%d,%d' % im.getpixel((q[2]-30,(q[1]+q[3])//2))
print(f(a), f(b))")"
record "settings: the fills of item 0 and item 1 (the current one is item $CUR)" "$FILL"
OTHER="$(echo "$FILL" | cut -d' ' -f$((2 - CUR)))"; MINE="$(echo "$FILL" | cut -d' ' -f$((CUR + 1)))"
assert_rgb "settings: the other item on the #2B2B2B list" "43,43,43" "$OTHER" 4
assert_ne "settings: the current item is accent-filled (not the list's colour)" "$OTHER" "$MINE"
tap_node "$D/list.xml" "camera_set_item:grid:$CUR"; sleep 1; cgdump "$D/set-after.xml"
assert_eq "settings: the grid setting is left as it was" "$GRID_NOW" "$(node_text "$D/set-after.xml" camera_set_value:grid)"
close_settings

# ----------------------------------------------------------------------------------------------- Y4: the Pro dial
log "--- Y4: the Pro dial"
case " $EXPECTED " in
  *" pro "*)
    record "Y4: where the sub-row runs" "on the AVD — the derived list shows Pro (controls: $PRO_WANT)"
    cgdump "$D/pre-dial.xml"
    # shellcheck disable=SC2046
    set -- $(centre_px "$D/pre-dial.xml" camera_shutter)
    MARK="$(ring_mark)"
    adb shell input swipe "$1" "$2" $(( $1 - 240 )) "$2" 120
    sleep 2
    cgdump "$D/dial.xml"; screencap "$D/dial.png"; CHK_DUMP="$D/dial.xml"
    assert_eq "Y4: the dial is open" "yes" "$(has_node "$D/dial.xml" camera_dial)"
    assert_motion "the dial-open sweep" "$(motion_line "$MARK" dial_open)" 333
    assert_eq "Y4: rings inner → outer = the admitted controls" "$PRO_WANT" "$(ids_with "$D/dial.xml" camera_dial_icon:)"
    if [ "$PRO_WANT" = "exposure shutter iso focus wb" ]; then
      FIT="$(python3 "$CAM_DEV/dial_fit.py" "$D/dial.png")"; record "Y4: ring radii fitted from the screencap about (W/2, nav top), epx" "$FIT"
      assert_eq "Y4: five arcs are found" "5" "$(echo "$FIT" | wc -w)"
      k=0
      for c in exposure shutter iso focus wb; do
        R="$(python3 -c "print([130.5,195.4,260.3,325.3,390.2][$k])")"; k=$((k + 1))
        assert_within "Y4: ring $k ($c) radius $R ± 1 about (W/2, nav top)" "$R" "$(echo "$FIT" | cut -d' ' -f$k)" 1
        # shellcheck disable=SC2046
        set -- $(centre_epx "$D/dial.xml" "camera_dial_icon:$c")
        assert_within "Y4: the $c icon node lies on its ring" "$R" "$(python3 -c "import math; print(math.hypot(${1:-0}-180, ${2:-0}-$NAV_TOP))")" 1
        # shellcheck disable=SC2046
        set -- $(centre_epx "$D/dial.xml" "camera_dial_value:$c")
        assert_within "Y4: the $c value label is centred at W/2" 180 "${1:-none}" 1
        assert_within "Y4: the $c value label is 30.3 above its ring's top" "$(python3 -c "print($NAV_TOP-$R-30.3)")" "${2:-none}" 1
      done
      # shellcheck disable=SC2046
      set -- $(centre_epx "$D/dial.xml" camera_shutter)
      assert_within "Y4: the shutter stays at W/2 in the five-ring view" 180 "${1:-none}" 1
      assert_within "Y4: the shutter is 74.75 epx above the nav top in the five-ring view" "$(python3 -c "print($NAV_TOP-74.75)")" "${2:-none}" 1
    else
      record "Y4: the five-ring geometry" "this camera admits [$PRO_WANT], not all five controls — the five-ring values are the phone's (P9)"
    fi
    adb shell input keyevent KEYCODE_BACK; sleep 1; cgdump "$D/c0.xml"
    assert_eq "Y4: Back closes the dial" "no" "$(has_node "$D/c0.xml" camera_dial)"
    # One control alone: the capsule's chevron, then one control.
    ONE="$(echo "$PRO_WANT" | tr ' ' '\n' | grep -m1 -x focus || echo "$PRO_WANT" | cut -d' ' -f1)"
    tap_node "$D/c0.xml" "camera_capsule:more"; sleep 1; cgdump "$D/c1.xml"
    record "Y4: the expanded capsule" "$(ids_with "$D/c1.xml" camera_capsule:)"
    tap_node "$D/c1.xml" "camera_capsule:pro:$ONE"; sleep 1.5; cgdump "$D/c2.xml"; screencap "$D/single.png"
    assert_eq "Y4: one control alone shows one ring" "$ONE" "$(ids_with "$D/c2.xml" camera_dial_icon:)"
    assert_within "Y4: one control alone = one arc of r 130.25 ± 1 (fitted from the screencap)" 130.25 "$(python3 "$CAM_DEV/dial_fit.py" "$D/single.png" | cut -d' ' -f1)" 1
    # shellcheck disable=SC2046
    set -- $(centre_epx "$D/c2.xml" "camera_dial_icon:$ONE")
    assert_within "Y4: … its icon node on that arc" 130.25 "$(python3 -c "import math; print(math.hypot(${1:-0}-180, ${2:-0}-$NAV_TOP))")" 1
    adb shell input keyevent KEYCODE_BACK; sleep 1 ;;
  *)
    record "Y4: where the sub-row runs" "on the PHONE with P9 — this AVD's camera admits no dial control (derived list: $EXPECTED)"
    assert_eq "Y4: no Pro node on a camera that admits no control" "no" "$(has_node "$D/vf.xml" camera_mode:pro)" ;;
esac

# ----------------------------------------------------------------------------------------------- motion + Y3: video
log "--- motion: the mode switch; Y3 in Video"
fresh_camera
cgdump "$D/m-photo.xml"
XY="$(centre_px "$D/m-photo.xml" "camera_disc:video")"
MARK="$(ring_mark)"
# shellcheck disable=SC2086
record_around "$D/mode-switch" adb shell input tap $XY
sleep 2
M="$(motion_line "$MARK" mode_switch)"
assert_motion "the mode switch (a slide)" "$M" 300
record "the mode switch in the screenrecord (60-fps resample; out ≈ 150 ms, a black gap, in ≈ 150 ms — corroborates only)" "$(black_frames "$D/mode-switch.mp4" 200 $SY)"
cgdump "$D/video.xml"; screencap "$D/video.png"; CHK_DUMP="$D/video.xml"
assert_eq "Y3 video: Video is the selected mode" "true" "$(node_attr "$D/video.xml" camera_mode:video selected)"
assert_eq "Y3 video: one mode disc — the previous mode's, on the left (Change Log (8))" "photo" "$(ids_with "$D/video.xml" camera_disc:)"
chk_centre "Y3 video: the mode disc (W/2 − 60, nav top − 36)" "camera_disc:photo" 120 $((NAV_TOP - 36)) 2
assert_eq "Y3 video: the mode disc is 32 epx" "32.0 32.0" "$(size_epx "$D/video.xml" "camera_disc:photo")"
chk_centre "Y3 video: the record disc (W/2, nav top − 56)" camera_record 180 $((NAV_TOP - 56)) 2
VITEMS="$(ids_with "$D/video.xml" camera_capsule: | tr ' ' '\n' | grep -v ':' | xargs)"
record "Y3 video: the video capsule's items and centres (two on the AVD; the doc gives centres for four and three)" "$VITEMS — $(for it in $VITEMS; do echo "$it@$(centre_epx "$D/video.xml" "camera_capsule:$it" | tr ' ' ',')"; done | xargs)"
assert_eq "Y3 video: the capsule node is present" "yes" "$(has_node "$D/video.xml" camera_capsule)"
# Y3's order for video: video light, slow motion, chevron — slow motion only where the derived list holds it.
case " $EXPECTED " in *" slowmo "*) VWANT="light slowmo more" ;; *) VWANT="light more" ;; esac
assert_eq "Y3 video: the capsule's items in Y3's order" "$VWANT" "$VITEMS"
if [ "$VWANT" = "light slowmo more" ]; then
  i=0; for off in -43.7 0.5 45.1; do i=$((i + 1)); chk_centre "Y3 video: capsule glyph $i (W − 24, H/2 $off)" "camera_capsule:$(echo "$VITEMS" | cut -d' ' -f$i)" 336 "$(python3 -c "print($HALF+$off)")" 2; done
fi
for it in $VITEMS; do
  # shellcheck disable=SC2046
  set -- $(centre_epx "$D/video.xml" "camera_capsule:$it")
  assert_within "Y3 video: capsule glyph $it is 24 epx from the right" 336 "${1:-none}" 2
done
chk_centre "Y3 video: the settings disc stays (W − 24, nav top − 27.9)" camera_settings 336 704.1 2

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
no_crash
c6; ensure_start
media_down
row_end
