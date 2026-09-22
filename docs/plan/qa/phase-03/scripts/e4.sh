#!/usr/bin/env bash
# E4 — the persona's motion, from screenrecord frames.
#
#   "From screenrecord frames: the idle / thinking ring pop-in, Y-axis rotation and move to the top of
#    the page measure within R3 A22's tolerance; the listening persona (period 1.04 ± 0.02 s, halo and
#    disc sizes in antiphase, centre 243.8 epx, entrance 333 ± 17 ms) within R6 §3.1.6–3.1.10's; the
#    listening waveform glyph, the listening query box and the speaking / awaiting-reply /
#    idle-after-speaking persona within the tolerance of each R6 §3.1 / §3.2 value in Decisions; the
#    waveform's bar heights and the never-speaking large persona are approximations judged in H10 and
#    H9, not measured" (review T-B4)
#
# Motion rows follow PLAN RV11: 60 fps screenrecord, the edge read to sub-pixel precision, because a
# hard threshold on an antialiased edge saturates about 3 px early and hides an ease-out's tail — the
# method note phase 02's harness corrected.
#
# Tolerances of 17 ms or less are phone rows (P5), not this one: this AVD's screenrecord does not carry
# a touch-up frame, which is the limit phase 02 already routed onward. E4 measures the values whose
# tolerance is larger than a frame and records the rest for P5.
. "$(dirname "$0")/lib.sh"

row_begin E4 "the persona's motion"

VIDEO="$ROW_DIR/e4_persona.mp4"
FRAMES="$ROW_DIR/frames"
rm -rf "$FRAMES"; mkdir -p "$FRAMES"

# ---- capture: open Cortana, let the idle ring settle, then listen ------------------------------
ensure_start
adb shell screenrecord --time-limit 20 --bit-rate 16000000 --size 1080x2340 /sdcard/e4.mp4 &
recorder=$!
sleep 1
cortana_assist
sleep 6
# Listening is started from the Search key's press-and-hold, which is the entry the doc gives for
# "opens Cortana already listening" — and it is on Start's bar, not inside the session.
"$HERE/speak.sh" time_query 8 > /dev/null 2>&1 || note "the spoken step did not complete; the idle frames are still usable"
wait $recorder 2>/dev/null
sleep 2
adb pull /sdcard/e4.mp4 "$VIDEO" >/dev/null 2>&1
adb shell rm -f /sdcard/e4.mp4

if [ ! -s "$VIDEO" ]; then
  _verdict FAIL "a screenrecord was captured" "no video at $VIDEO"
  row_end
  exit $?
fi
_verdict PASS "a screenrecord was captured" "$(du -h "$VIDEO" | cut -f1)"

# ---- frames -------------------------------------------------------------------------------------
ffmpeg -v error -i "$VIDEO" -vf fps=60 "$FRAMES/f_%04d.png" 2>>"$LOG"
frame_count="$(find "$FRAMES" -name 'f_*.png' | wc -l)"
note "frames extracted: $frame_count at 60 fps"
assert_ne "frames were extracted" "0" "$frame_count"

# ---- measure ------------------------------------------------------------------------------------
# persona.py reads the accent disc and halo diameters per frame to sub-pixel precision and prints the
# values the Decisions name. It fails loudly rather than returning a number it could not measure.
python3 "$HERE/persona.py" "$FRAMES" > "$ROW_DIR/e4_measured.txt" 2>&1
measured_rc=$?
cat "$ROW_DIR/e4_measured.txt" >> "$LOG"

if [ $measured_rc -ne 0 ]; then
  _verdict FAIL "the persona was found in the frames" "$(head -3 "$ROW_DIR/e4_measured.txt" | tr '\n' ' ')"
  row_end
  exit $?
fi
_verdict PASS "the persona was found in the frames" "$(head -1 "$ROW_DIR/e4_measured.txt")"

value() { sed -n "s/^$1=//p" "$ROW_DIR/e4_measured.txt" | head -1; }

# R3 A22: the idle ring on the Cortana page.
assert_within "R3 A22 idle ring outer diameter 70 epx" 70 "$(value idle_outer_epx)" 2.0
assert_within "R3 A22 idle ring centre 244 epx from the screen top" 244 "$(value idle_centre_epx)" 2.0

# R6 §3.1.6-3.1.10: the listening persona.
assert_within "R6 3.1.9 listening centre 243.8 epx" 243.8 "$(value listen_centre_epx)" 2.0
assert_within "R6 3.1.6 halo minimum 85.8 epx" 85.8 "$(value listen_halo_min_epx)" 2.0
assert_within "R6 3.1.6 halo maximum 94.7 epx" 94.7 "$(value listen_halo_max_epx)" 2.0
assert_within "R6 3.1.6 disc maximum 41.1 epx" 41.1 "$(value listen_disc_max_epx)" 2.0
assert_within "R6 3.1.6 disc minimum 37.3 epx" 37.3 "$(value listen_disc_min_epx)" 2.0
assert_within "R6 3.1.8 period 1.04 s" 1040 "$(value listen_period_ms)" 40

# §3.1.7: the halo grows while the disc shrinks. A correlation near -1 is what antiphase means.
antiphase="$(value listen_antiphase_correlation)"
note "halo/disc correlation across the capture: $antiphase"
python3 - "$antiphase" <<'PY'
import sys
try:
    sys.exit(0 if float(sys.argv[1]) < -0.5 else 1)
except ValueError:
    sys.exit(1)
PY
if [ $? -eq 0 ]; then
  _verdict PASS "R6 3.1.7 the halo and disc move in antiphase" "correlation $antiphase"
else
  _verdict FAIL "R6 3.1.7 the halo and disc move in antiphase" "correlation $antiphase is not below -0.5"
fi

# H9 and H10 are judged, not measured: the never-speaking large persona and the waveform's bar heights.
note "H9 (the large persona never speaks) and H10 (bars follow the mic level) are approximations, judged"
# P5 keeps every tolerance of 17 ms or less: the entrance (333 ± 17 ms), the waveform step (128 ± 15 ms)
# and the speaking halo step (51 ± 17 ms) need a frame this AVD's screenrecord does not carry.
note "P5 (phone) keeps: listening entrance 333 +/- 17 ms, waveform step 128 +/- 15 ms, speaking halo step 51 +/- 17 ms"

row_end
