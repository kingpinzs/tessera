. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E13-presssmoke "E13 press styles only (smoke of the short-press fix)"
seed_fixtures
log "--- phase 01 E10 press styles: a short press shows the style and opens no burst ---"
# The press is SHORT by construction and measured. Pass 6 found the old one racing the hold (DOWN, 0.3 s, then a PNG
# capture inside the press that took long enough on a loaded host to pass 783 ms); its first fix waited for the whole
# raw write (~0.5 s) inside the press and measured ~800 ms. One device shell now: a raw touch down (the emulator's
# multi-touch device, with a pressure), 0.3 s, a raw screencap started in the background (it grabs the frame as it
# starts; the rest is the 10-MB write), 0.15 s, then the finger slides 400 px down and lifts — a scroll, never a tap,
# so nothing launches. The press's duration is read on the device clock and asserted (under 700 ms; retaken up to 3).
# show_touches is off for this sub-step (a real touch draws its dot into the capture; restored after, as phase 05 does).
TS=/dev/input/event2
mt() { echo "sendevent $TS 3 47 0; sendevent $TS 3 57 ${1}; sendevent $TS 3 53 $(( $2 * 32767 / 1080 )); sendevent $TS 3 54 $(( $3 * 32767 / 2340 )); sendevent $TS 3 58 512; sendevent $TS 3 48 8; sendevent $TS 0 0 0;"; }
raw_png() { # device raw capture -> png
  adb pull "$1" "$2.raw" >/dev/null 2>&1
  python3 - "$2.raw" "$2" <<'PY'
import struct, sys
from PIL import Image
b = open(sys.argv[1], "rb").read(); w, h, _ = struct.unpack("<III", b[:12]); hdr = len(b) - w * h * 4
Image.frombytes("RGBA", (w, h), b[hdr:]).convert("RGB").save(sys.argv[2])
PY
}
TOUCHES0="$(adb shell settings get system show_touches | tr -d '\r')"
adb shell settings put system show_touches 0
note "show_touches was ${TOUCHES0}; 0 for this sub-step"
restore baseline_layout.json
for style in none tilt p4; do
  set_press "$style"
  qdump "$ROW_DIR/press-$style.xml"
  read -r l t r b <<< "$(bounds "$ROW_DIR/press-$style.xml" tile:slot:BROWSER)"
  px=$((l + 40)); py=$((t + 40))
  ms=""
  for attempt in 1 2 3; do
    adb shell screencap /data/local/tmp/qa-rest.raw
    MARK="$(ring_mark)"
    ms="$(adb shell "t0=\$(date +%s%N); $(mt 100 $px $py) sleep 0.3; screencap /data/local/tmp/qa-held.raw & sleep 0.15; t1=\$(date +%s%N); $(mt 100 $px $((py + 400))) sendevent $TS 3 47 0; sendevent $TS 3 57 4294967295; sendevent $TS 0 0 0; wait; echo \$(( (t1 - t0) / 1000000 ))" | tr -d '\r' | tail -1)"
    sleep 1
    note "press_$style attempt $attempt: pressed ${ms} ms (the capture started at 300 ms)"
    [ -n "$ms" ] && [ "$ms" -lt 700 ] && break
    c6; ensure_start_page
  done
  raw_png /data/local/tmp/qa-rest.raw "$ROW_DIR/press-$style-rest.png"
  raw_png /data/local/tmp/qa-held.raw "$ROW_DIR/press-$style-held.png"
  assert_eq "press_$style: the press was short (under 700 ms on the device clock)" yes "$([ -n "$ms" ] && [ "$ms" -lt 700 ] && echo yes || echo "no ($ms ms)")"
  # shellcheck disable=SC2086
  eq="$(python3 "$(dirname "$0")/qpix.py" equal "$ROW_DIR/press-$style-rest.png" "$ROW_DIR/press-$style-held.png" $((l + 4)) $((t + 4)) $((r - 4)) $((b - 4)) 2)"
  note "press_$style: rest vs held over the tile: $eq"
  if [ "$style" = none ]; then want=EQUAL; else want=DIFF; fi
  assert_eq "press_$style: the held tile $( [ "$want" = EQUAL ] && echo 'looks as at rest' || echo 'shows the press style')" "$want" "${eq%% *}"
  S="$(ring_since "$MARK")"
  assert_absent "press_$style: no burst" "[quick] burst on" "$S"
  assert_absent "press_$style: no hold" "[edit] hold" "$S"
  assert_absent "press_$style: nothing launched (the press ended in a scroll)" "[launch]" "$S"
  c6
done
adb shell settings put system show_touches "${TOUCHES0:-0}"
set_press none
row_end
