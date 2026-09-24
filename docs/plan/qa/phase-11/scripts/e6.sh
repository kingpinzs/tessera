#!/usr/bin/env bash
# E6 — the motion, on the shell's own clock (C-5, T11-5, T11-29, C-31). Every warm open is asserted as it is taken:
# peak, overshoot, maxGapMs, and settle ≤ 249.1 ms (the spring's own settle for this travel) + that open's largest
# frame gap — the settle frame is the first frame at or after 249.1 ms, so its bound follows the spacing C-31 already
# bounds (gate review G-E6-2; INDEX Change Log). The alignment is on the same clock: the burst's t0 against the edit
# entry's first frame (`[edit] entry first frame at uptime=`), within one frame (C-5; replaces a pixel detector that
# could not fail, G-E6-4). The recording is corroboration of frame spacing only (phase 05's rule, ≤ 18.2 ms, else
# retaken). Measured WARM: the first hold after the shell starts janks in phase 02's own entry on this debug build (a
# cold hold on a folder, no burst, does too — cold_jank.sh); that cold line is recorded, not asserted; P2 reads it cold
# on the phone. Before every attempt Start must be in front: an "isn't responding" dialog fails the attempt and its
# trace is saved (G-E6-3; L11-2).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E6 "motion on the shell's clock, every warm open asserted; alignment on the same clock; recording spacing"
seed_fixtures
restore baseline_layout.json
qdump "$ROW_DIR/rest.xml"
read -r X Y <<< "$(center "$ROW_DIR/rest.xml" "tile:$A_KEY")"
read -r EX EY <<< "$(empty_point "$ROW_DIR/rest.xml" 1300 1850)"
num() { sed -n "s/.*[ :]$1=\([-0-9.]*\).*/\1/p" <<< "$2" | head -1; }
PROBE="$(bounds "$ROW_DIR/rest.xml" tile:dock:slot:MESSAGING | awk '{printf "%d,%d,%d,%d", $1+60, $2+60, $3-60, $4-60}')"
exit_edit() { tap_xy "$EX" "$EY"; sleep 0.8; tap_xy "$EX" "$EY"; sleep 1.5; }

# front_ok <tag>: Start in front and no app-error dialog, else the attempt fails and the ANR trace is kept.
front_ok() {
  qdump "$ROW_DIR/front-$1.xml"
  if grep -q 'aerr_' "$ROW_DIR/front-$1.xml"; then
    adb root >/dev/null 2>&1; sleep 1
    for f in $(adb shell ls /data/anr/ 2>/dev/null | tr -d '\r'); do adb shell cat "/data/anr/$f" > "$ROW_DIR/anr-$1-$f.txt" 2>/dev/null; done
    adb logcat -d | grep -E 'ANR in|Input dispatching' > "$ROW_DIR/anr-$1-logcat.txt"
    _verdict FAIL "$1: Start in front (an app-error dialog is up; trace kept)" "$(grep -o 'text="[^"]*responding[^"]*"' "$ROW_DIR/front-$1.xml" | head -1)"
    adb shell input keyevent KEYCODE_BACK; sleep 1
    return 1
  fi
  [ "$(has_node "$ROW_DIR/front-$1.xml" start_page)" = yes ] && return 0
  _verdict FAIL "$1: Start in front" "no start_page in the dump"
  return 1
}

# check_open <tag> <line> <entry-uptime>: the open line's numbers and its alignment, asserted.
check_open() {
  local tag="$1" line="$2" entry="$3" gap settle t0 bound
  gap="$(num maxGapMs "$line")"; settle="$(num settle "$line")"; t0="$(num t0 "$line")"
  assert_within "$tag: peak 107 ± 17 ms" 107 "$(num peak "$line")" 17
  assert_within "$tag: overshoot 6.8 ± 2 %" 6.8 "$(num overshoot "$line")" 2
  assert_within "$tag: maxGapMs ≤ 33.4 (C-31)" 16.7 "$gap" 16.7
  bound="$(python3 -c 'import sys; print(round(249.1 + float(sys.argv[1] or 99) + 1, 1))' "$gap")"
  assert_within "$tag: settle ≤ 249.1 + maxGapMs + 1 = $bound ms (T11-29, G-E6-2)" "$(python3 -c "print($bound/2)")" "$settle" "$(python3 -c "print($bound/2)")"
  assert_within "$tag: burst t0 within one frame of the entry's first frame (C-5)" 0 "$(( ${t0:-0} - ${entry:-0} ))" 17
}

log "--- cold (recorded, not asserted): the first hold after the shell started ---"
MARK="$(ring_mark)"; hold "$X" "$Y" 1.0; sleep 0.8
note "cold: $(quick_since "$MARK" | grep 'motion open' | sed 's/.*\[quick\]/[quick]/')"
exit_edit

log "--- warm opens: every one asserted; the recording retaken (up to 10) until its spacing passes ---"
ACCEPT=""
for attempt in 1 2 3 4 5 6 7 8 9 10; do
  front_ok "attempt$attempt" || { exit_edit; continue; }
  adb shell screenrecord --bit-rate 4000000 --time-limit 4 /sdcard/Download/e6.mp4 & REC=$!
  sleep 1.0
  MARK="$(ring_mark)"; hold "$X" "$Y" 1.0
  wait "$REC"; adb pull /sdcard/Download/e6.mp4 "$ROW_DIR/open-$attempt.mp4" >/dev/null 2>&1
  sleep 0.5
  S="$(ring_since "$MARK")"
  OPEN="$(echo "$S" | grep "\[quick\] motion open $A_KEY")"
  ENTRY="$(echo "$S" | sed -n 's/.*\[edit\] entry first frame at uptime=\([0-9]*\).*/\1/p' | head -1)"
  note "attempt $attempt: ${OPEN#*\[quick\] } | entry first frame $ENTRY"
  assert_eq "attempt $attempt: one open line" 1 "$(printf '%s\n' "$OPEN" | grep -c 'motion open')"
  check_open "attempt $attempt" "$OPEN" "$ENTRY"
  qdump "$ROW_DIR/burst-$attempt.xml"
  TILE="$(bounds "$ROW_DIR/burst-$attempt.xml" "tile:$A_KEY" | tr ' ' ',')"
  ffprobe -v error -select_streams v:0 -show_entries frame=best_effort_timestamp_time -of csv=p=0 "$ROW_DIR/open-$attempt.mp4" | tr -d ',' > "$ROW_DIR/open-$attempt.pts"
  rm -rf "$ROW_DIR/frames"; mkdir -p "$ROW_DIR/frames"
  ffmpeg -v error -i "$ROW_DIR/open-$attempt.mp4" -vsync 0 "$ROW_DIR/frames/f_%05d.png"
  python3 "$(dirname "$0")/e6_frames.py" "$ROW_DIR/frames" "$ROW_DIR/open-$attempt.pts" "$TILE" "$PROBE" > "$ROW_DIR/recording-$attempt.txt" 2>&1
  rm -rf "$ROW_DIR/frames"
  gap="$(awk '/max_gap_ms/{print $2}' "$ROW_DIR/recording-$attempt.txt")"
  note "attempt $attempt recording: $(tr '\n' ' ' < "$ROW_DIR/recording-$attempt.txt")"
  if python3 -c "import sys; sys.exit(0 if float(sys.argv[1]) <= 18.2 else 1)" "$gap" 2>/dev/null; then ACCEPT="$attempt"; break; fi
  exit_edit
done
assert_ne "a recording within phase 05's spacing rule (source frames ≤ 18.2 ms apart during the motion)" "" "$ACCEPT"
note "accepted recording: attempt ${ACCEPT:-none} (its satellite-pixel lag is printed, not asserted: G-E6-4)"

log "--- the close: tap elsewhere ---"
MARK="$(ring_mark)"; tap_xy "$EX" "$EY"; sleep 1.2
CLOSE="$(quick_since "$MARK" | grep "motion close $A_KEY")"
note "close line: ${CLOSE#*\[quick\] }"
a0="$(num alpha0 "$CLOSE")"
assert_within "close alpha0 36 ± 17 ms (the halfway at 36.0 ms)" 36 "$a0" 17
assert_within "close settle = alpha0 ± 17 ms (gone at alpha 0)" "${a0:-0}" "$(num settle "$CLOSE")" 17
assert_within "close maxGapMs ≤ 33.4 (C-31)" 16.7 "$(num maxGapMs "$CLOSE")" 16.7
c6
row_end
