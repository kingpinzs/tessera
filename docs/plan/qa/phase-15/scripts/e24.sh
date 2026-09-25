#!/usr/bin/env bash
# E24 — recorder geometry ([fidelity] H3; T15-15, T15-43, T15-57): the MEDIUM values of r11/voice-recorder.md asserted
# from the nodes' own bounds (3 px per epx on this AVD: the shell draws at 360 epx across 1080 px) and the LOW ones
# `record`ed — the list rows at a 56 ± 1-epx pitch with the duration right-aligned at a 12-epx inset (3.4), group headers
# in accent at x 12 with a 1-epx rule under them (3.3, the rule found in the screencap), the docked button 76 ± 3 epx on
# W/2 with its centre 63 ± 3 epx above the nav bar (3.8), rec_search 32 epx tall with 12-epx margins as the first element
# and "Showing …" under it (3.1–3.2), the record state's disc 96 ± 2 epx on W/2 (2.1) with the timer / pause / flag /
# rings recorded (2.7–2.12, LOW), "No recordings found" at R7 §3.5.9's place (2.11, recorded), the playback page's name
# and date centred, the 96-epx disc, the 24-epx thumb ring on a 2-epx track and the app bar at R7 §3.5.8's 68-epx pitch
# (4.1–4.7), the drawn bars against BarMetrics (C-17, read from the source, never a literal). Motion: the record →
# recording change is a one-frame cut graded by pixels (T15-32; E10's screenrecord form, the disc's own circle as the
# region); `[motion] rec_state` is secondary.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
RINGS="launcher $REC_SVC"
row_begin E24 "recorder geometry: the list, record and playback pages, the bars, and the one-frame cut"

PX=3   # px per epx on this AVD (1080 px = 360 epx)
STATUS_EPX="$(grep -oE 'const val STATUS_EPX = [0-9]+' "$REPO/app/src/main/kotlin/app/tileshell/bars/SystemBars.kt" | grep -oE '[0-9]+$')"
NAV_EPX="$(grep -oE 'const val NAV_EPX = [0-9]+' "$REPO/app/src/main/kotlin/app/tileshell/bars/SystemBars.kt" | grep -oE '[0-9]+$')"
note "BarMetrics from the source: STATUS_EPX=$STATUS_EPX NAV_EPX=$NAV_EPX (C-17)"
# A node's geometry: "left top right bottom width height cx cy" in device px.
nb() { # dump.xml id
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(sys.argv[2]), xml)
if not m: print(""); sys.exit()
l, t, r, b = [int(v) for v in m.groups()]
print(l, t, r, b, r - l, b - t, (l + r) / 2, (t + b) / 2)
PY
}
f() { echo "$1" | awk -v i="$2" '{print $i}'; }   # field i of an nb() line (1 l, 2 t, 3 r, 4 b, 5 w, 6 h, 7 cx, 8 cy)
bars_check() { # dump.xml label
  local sb nb_
  sb="$(nb "$1" w10m_status_bar)"; nb_="$(nb "$1" w10m_nav_bar)"
  assert_eq "$2: the drawn status bar is STATUS_EPX tall" "$(( STATUS_EPX * PX ))" "$(f "$sb" 6)"
  assert_eq "$2: the drawn status bar sits at the top" 0 "$(f "$sb" 2)"
  assert_eq "$2: the drawn nav bar is NAV_EPX tall" "$(( NAV_EPX * PX ))" "$(f "$nb_" 6)"
  assert_eq "$2: the drawn nav bar sits at the bottom" 2340 "$(f "$nb_" 4)"
}

wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
audio_route "E24"
TONE_PID=""

# ---------------------------------------------------------------- 2.11 the empty page
rec_open list
dump_ui "$ROW_DIR/empty.xml"; screencap "$ROW_DIR/empty.png"
E="$(nb "$ROW_DIR/empty.xml" rec_empty)"; note "rec_empty bounds: $E"
assert_eq "empty list: No recordings found is shown" "No recordings found" "$(node_text "$ROW_DIR/empty.xml" rec_empty)"
record "2.11 (LOW) the empty line's left edge, epx (R7 §3.5.9: 11.7)" "$(python3 -c 'import sys; print(round(float(sys.argv[1])/3, 1))' "$(f "$E" 1)")"
record "2.11 (LOW) the empty line's text-box top from the screen top, epx (R7 §3.5.9's cap top: 70.4)" "$(python3 -c 'import sys; print(round(float(sys.argv[1])/3, 1))' "$(f "$E" 2)")"
bars_check "$ROW_DIR/empty.xml" "empty list"

# ---------------------------------------------------------------- 2.1–2.12 the record state, and the cut (7.1, T15-32)
rec_open record
dump_ui "$ROW_DIR/record.xml"; screencap "$ROW_DIR/record.png"
D="$(nb "$ROW_DIR/record.xml" rec_button)"; note "record disc: $D"
assert_within "2.1 the record disc is 96 ± 2 epx wide" "$(( 96 * PX ))" "$(f "$D" 5)" "$(( 2 * PX ))"
assert_within "2.1 the record disc is as tall as it is wide" "$(f "$D" 5)" "$(f "$D" 6)" 2
assert_within "2.1 the disc is centred on W/2" 540 "$(f "$D" 7)" "$PX"
MB="$(nb "$ROW_DIR/record.xml" rec_more)"
record "2.16 (LOW) the minimal bar's dots button: width epx, right edge px, bottom px (nav top $(f "$(nb "$ROW_DIR/record.xml" w10m_nav_bar)" 2))" "$(python3 -c 'import sys; print(round(float(sys.argv[1])/3,1))' "$(f "$MB" 5)"), $(f "$MB" 3), $(f "$MB" 4)"
bars_check "$ROW_DIR/record.xml" "record state"
# the cut: screenrecord around the tap (no tone: a silent take, so the level rings stay at their floor), a still 1 s after
if [ "$AUDIO_OK" = yes ]; then
  BEFORE="$(own_ids | tr '\n' ' ')"
  MARK="$(ring_mark)"
  adb shell rm -f /sdcard/e24_cut.mp4
  adb shell screenrecord --time-limit 5 --bit-rate 20000000 --size 1080x2340 /sdcard/e24_cut.mp4 &
  REC=$!
  sleep 1.5
  gtap "$ROW_DIR/record.xml" rec_button
  sleep 1
  screencap "$ROW_DIR/cut_still.png"
  wait $REC 2>/dev/null
  adb pull /sdcard/e24_cut.mp4 "$ROW_DIR/cut.mp4" >/dev/null 2>&1; adb shell rm -f /sdcard/e24_cut.mp4
  rm -rf "$ROW_DIR/cut_frames"; mkdir -p "$ROW_DIR/cut_frames"
  ffmpeg -v error -i "$ROW_DIR/cut.mp4" -vsync 0 "$ROW_DIR/cut_frames/f_%04d.png" 2>>"$LOG"
  ffprobe -v error -select_streams v:0 -show_entries frame=best_effort_timestamp_time -of csv=p=0 "$ROW_DIR/cut.mp4" | tr -d ',' > "$ROW_DIR/cut.pts"
  note "cut capture: $(wc -l < "$ROW_DIR/cut.pts") source frames"
  read -r FIRST STOPF NFR FRAC BETWEEN GAP <<< "$(python3 - "$ROW_DIR/cut_frames" "$ROW_DIR/cut_still.png" "$ROW_DIR/cut.pts" "$(f "$D" 7)" "$(f "$D" 8)" "$(f "$D" 5)" <<'PY'
import glob, os, sys
import numpy as np
from PIL import Image
d, still, pts = sys.argv[1], sys.argv[2], sys.argv[3]
cx, cy, w = float(sys.argv[4]), float(sys.argv[5]), float(sys.argv[6])
r = w / 2 - 4                                   # the disc's own circle, 4 px inside its edge
fs = sorted(glob.glob(os.path.join(d, "f_*.png")))
ts = [float(l) for l in open(pts) if l.strip()]
yy, xx = np.mgrid[0:2340, 0:1080]
disc = (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r
def load(p): return np.asarray(Image.open(p).convert("RGB")).astype(np.int16)
base = load(fs[0]); s = load(still)
def differs(a, b): return (np.abs(a - b) > 8).any(axis=2)[disc].mean()
def equal_frac(a, b): return (np.abs(a - b) <= 8).all(axis=2)[disc].mean()
first = None
for i, fp in enumerate(fs[1:], 1):
    if differs(load(fp), base) > 0.002: first = i; break
if first is None: print("none none", len(fs), 0, 0, 0); sys.exit()
stopf = None
for i in range(first, len(fs)):
    if equal_frac(load(fs[i]), s) >= 0.99: stopf = i; break
frac = equal_frac(load(fs[first]), s)
gap = (ts[first] - ts[first - 1]) * 1000 if first < len(ts) else 0
between = (stopf - first) if stopf is not None else -1
print(first, stopf if stopf is not None else "none", len(fs), "%.4f" % frac, between, "%.1f" % gap)
PY
)"
  note "cut: first differing frame #$FIRST of $NFR (its match to the still $FRAC); first frame equal to the still #$STOPF; frames between $BETWEEN; gap before the change $GAP ms"
  assert_ne "7.1 a source frame differs from the pre-tap frame in the disc" "none" "$FIRST"
  assert_ne "7.1 a source frame equals the stop state of the still taken 1 s later (± 8 levels in >= 99 % of the disc)" "none" "$STOPF"
  record "7.1 (LOW) frames between the first change and the stop state (the grey start-up interval)" "$BETWEEN"
  record "7.1 (LOW) the source-frame gap before the change, ms" "$GAP"
  MOTION="$(ring_since "$MARK" | grep -F '[motion] rec_state' | tail -1)"; note "motion line: ${MOTION#*] }"
  assert_contains "the shell logged [motion] rec_state (secondary)" "[motion] rec_state" "$MOTION"
  # ---- while recording: the timer, pause / flag and the level rings under the tone (LOW: recorded) ------------------
  TONE_PID="$(tone_loop_start "$(tone 10)")"
  wait_elapsed 2500 15 >/dev/null
  rdump "$ROW_DIR/recording.xml"; screencap "$ROW_DIR/recording.png"
  T="$(nb "$ROW_DIR/recording.xml" rec_elapsed)"; P="$(nb "$ROW_DIR/recording.xml" rec_pause)"; FL="$(nb "$ROW_DIR/recording.xml" rec_flag)"
  SD="$(nb "$ROW_DIR/recording.xml" rec_button)"
  assert_eq "2.8 rec_elapsed shows hh:mm:ss while recording" yes "$(python3 -c 'import re,sys; print("yes" if re.fullmatch(r"\d\d:\d\d:\d\d", sys.argv[1]) else "no (%s)" % sys.argv[1])' "$(node_text "$ROW_DIR/recording.xml" rec_elapsed)")"
  assert_within "2.2 the stop disc keeps the record disc's place and size" "$(f "$D" 8)" "$(f "$SD" 8)" 2
  record "2.9 (LOW) the timer text box height, epx (digits 24 tall in a 34-epx subheader)" "$(python3 -c 'import sys; print(round(float(sys.argv[1])/3,1))' "$(f "$T" 6)")"
  record "2.10 (LOW) the timer's centre above the disc centre, epx (141.5)" "$(python3 -c 'import sys; print(round((float(sys.argv[1])-float(sys.argv[2]))/3,1))' "$(f "$SD" 8)" "$(f "$T" 8)")"
  record "2.12 (LOW) pause / flag centres from W/2, epx (± 46.7)" "$(python3 -c 'import sys; print(round((float(sys.argv[1])-540)/3,1), round((float(sys.argv[2])-540)/3,1))' "$(f "$P" 7)" "$(f "$FL" 7)")"
  record "2.12 (LOW) pause / flag row below the disc centre, epx (139)" "$(python3 -c 'import sys; print(round((float(sys.argv[1])-float(sys.argv[2]))/3,1))' "$(f "$P" 8)" "$(f "$SD" 8)")"
  RI="$(nb "$ROW_DIR/recording.xml" rec_ring_inner)"; RO="$(nb "$ROW_DIR/recording.xml" rec_ring_outer)"
  note "rings: inner [$RI] outer [$RO]; recorder level frames=$(rec_status frames)"
  assert_eq "2.7 the two level rings are drawn while recording (rec_ring_inner, rec_ring_outer)" "yes yes" "$(has_node "$ROW_DIR/recording.xml" rec_ring_inner) $(has_node "$ROW_DIR/recording.xml" rec_ring_outer)"
  record "2.7 (LOW) inner / outer ring diameters under the tone, epx (109–122 / 123–150)" "$(python3 -c 'import sys; print(round(float(sys.argv[1])/3,1), round(float(sys.argv[2])/3,1))' "$(f "$RI" 5)" "$(f "$RO" 5)")"
  assert_within "2.7 the rings are concentric with the disc (inner)" "$(f "$SD" 7)" "$(f "$RI" 7)" 2
  assert_within "2.7 the rings are concentric with the disc (outer)" "$(f "$SD" 7)" "$(f "$RO" 7)" 2
  wait_elapsed 4000 15 >/dev/null
  gtap "$ROW_DIR/recording.xml" rec_button
  tone_loop_stop "$TONE_PID"; TONE_PID=""
  wait_phase idle 15 >/dev/null; sleep 1.5
  A="$(new_own_id "$BEFORE")"
  B="$(make_take 3)"
  note "takes: $A, $B"
else
  _verdict FAIL "the record state and the cut" "NOT RUN: the audio route failed its check"
  A=""; B=""
fi

# ---------------------------------------------------------------- 3.1–3.8 the list state
if [ -n "$A" ] && [ -n "$B" ]; then
  rec_open list
  dump_ui "$ROW_DIR/list.xml"; screencap "$ROW_DIR/list.png"
  SB="$(nb "$ROW_DIR/list.xml" w10m_status_bar)"; NB="$(nb "$ROW_DIR/list.xml" w10m_nav_bar)"
  S="$(nb "$ROW_DIR/list.xml" rec_search)"; SH="$(nb "$ROW_DIR/list.xml" rec_showing)"
  note "search $S / showing $SH"
  # The text field's NODE reports its touch bounds — Compose widens an interactive node to the 48-epx minimum touch
  # target around the drawn 32-epx box (run 1: 144 px tall, centred on the box) — so the box's height and top are read
  # from its own drawn 1-epx border (white at 60 % over black, 153 grey) in the screencap, down the node's centre column
  # inside its touch bounds; the margins are the node's (the widening is vertical only).
  read -r BTOP BBOT <<< "$(python3 - "$ROW_DIR/list.png" "$(f "$S" 7)" "$(f "$S" 2)" "$(f "$S" 4)" <<'PY2'
import sys
import numpy as np
from PIL import Image
a = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(np.int16)
x, y0, y1 = int(float(sys.argv[2])), int(sys.argv[3]), int(sys.argv[4])
rows = [y for y in range(y0, y1) if (np.abs(a[y, x] - np.array([153, 153, 153])) <= 10).all()]
print(rows[0], rows[-1] + 1) if rows else print("0 0")
PY2
)"
  note "rec_search's drawn border: rows $BTOP..$BBOT (the node's touch bounds $(f "$S" 2)..$(f "$S" 4))"
  assert_within "3.1 rec_search's drawn box is 32 epx tall" "$(( 32 * PX ))" "$(( BBOT - BTOP ))" "$PX"
  assert_within "3.1 rec_search has a 12-epx left margin" "$(( 12 * PX ))" "$(f "$S" 1)" "$PX"
  assert_within "3.1 rec_search has a 12-epx right margin" "$(( 1080 - 12 * PX ))" "$(f "$S" 3)" "$PX"
  assert_within "3.1 rec_search is the first element, its box 12 epx under the status bar" "$(( $(f "$SB" 4) + 12 * PX ))" "$BTOP" "$PX"
  assert_eq "3.2 the Showing line sits under the search box" yes "$([ "$(f "$SH" 2)" -ge "$BBOT" ] && echo yes || echo "no ($(f "$SH" 2) < $BBOT)")"
  assert_contains "3.2 the Showing line reads its kind" "All recordings" "$(node_text "$ROW_DIR/list.xml" rec_filter)"
  H="$(nb "$ROW_DIR/list.xml" 'rec_group:Today')"; note "header Today: $H"
  assert_within "3.3 the group header sits at x 12 epx" "$(( 12 * PX ))" "$(f "$H" 1)" "$PX"
  # the 1-epx rule under the header: a row of the rule's colour (white 20 % over black) across the 12-epx margins
  RULE="$(python3 - "$ROW_DIR/list.png" "$(f "$H" 2)" "$(f "$H" 4)" <<'PY'
import sys
import numpy as np
from PIL import Image
a = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(np.int16)
y0, y1 = int(sys.argv[2]), int(float(sys.argv[3])) + 40
rows = []
for y in range(y0, min(y1, a.shape[0])):
    seg = a[y, 36:1044]
    if (np.abs(seg - np.array([51, 51, 51])) <= 10).all(axis=1).mean() > 0.9: rows.append(y)
print(" ".join(str(r) for r in rows))
PY
)"
  note "rule rows under the header (y px): [$RULE]"
  assert_ne "3.3 a rule is drawn under the group header (rows of white-20% across x 36..1044)" "" "$RULE"
  assert_eq "3.3 the rule is 1 epx (3 px) thick" 3 "$(echo "$RULE" | wc -w | tr -d ' ')"
  record "3.3 (derived) the rule's top below the header's text-box top, epx" "$(python3 -c 'import sys; r=sys.argv[1].split(); print(round((int(r[0])-int(sys.argv[2]))/3,1) if r else "none")' "$RULE" "$(f "$H" 2)")"
  RA="$(nb "$ROW_DIR/list.xml" "rec_row:$B")"; RB="$(nb "$ROW_DIR/list.xml" "rec_row:$A")"   # newest first: B then A
  note "rows: $RA / $RB"
  assert_within "3.4 rows are on a 56 ± 1-epx pitch" "$(( 56 * PX ))" "$(( $(f "$RB" 2) - $(f "$RA" 2) ))" "$PX"
  assert_within "3.4 a row is 56 epx tall" "$(( 56 * PX ))" "$(f "$RA" 6)" "$PX"
  N1="$(nb "$ROW_DIR/list.xml" "rec_name:$B")"; D1="$(nb "$ROW_DIR/list.xml" "rec_date:$B")"; U1="$(nb "$ROW_DIR/list.xml" "rec_duration:$B")"
  assert_eq "3.4 line 1 is the name, line 2 the date, below it" yes "$([ "$(f "$D1" 2)" -gt "$(f "$N1" 2)" ] && echo yes || echo no)"
  record "3.4 (derived) line 2's text-box top below line 1's, epx (cap tops 22 apart)" "$(python3 -c 'import sys; print(round((float(sys.argv[1])-float(sys.argv[2]))/3,1))' "$(f "$D1" 2)" "$(f "$N1" 2)")"
  assert_within "3.4 the duration is right-aligned at a 12-epx inset" "$(( 1080 - 12 * PX ))" "$(f "$U1" 3)" "$PX"
  assert_eq "3.4 the duration sits on line 2" yes "$(python3 -c 'import sys; print("yes" if abs(float(sys.argv[1])-float(sys.argv[2])) <= 6 else "no")' "$(f "$U1" 8)" "$(f "$D1" 8)")"
  DB="$(nb "$ROW_DIR/list.xml" rec_button)"; note "docked button: $DB"
  assert_within "3.8 the docked button is 76 ± 3 epx" "$(( 76 * PX ))" "$(f "$DB" 5)" "$(( 3 * PX ))"
  assert_within "3.8 the docked button is centred on W/2" 540 "$(f "$DB" 7)" "$PX"
  assert_within "3.8 its centre is 63 ± 3 epx above the nav bar's top" "$(( $(f "$NB" 2) - 63 * PX ))" "$(f "$DB" 8)" "$(( 3 * PX ))"
  bars_check "$ROW_DIR/list.xml" "list state"

  # ---------------------------------------------------------------- 4.1–4.7 the playback page
  gtap "$ROW_DIR/list.xml" "rec_row:$A"; sleep 4   # a 4-s take: let it end, then the page idles
  rdump "$ROW_DIR/playback.xml"; screencap "$ROW_DIR/playback.png"
  PN="$(nb "$ROW_DIR/playback.xml" rec_play_name)"; PD="$(nb "$ROW_DIR/playback.xml" rec_play_date)"; PL="$(nb "$ROW_DIR/playback.xml" rec_play)"
  SC="$(nb "$ROW_DIR/playback.xml" rec_scrubber)"; AB="$(nb "$ROW_DIR/playback.xml" rec_appbar)"; NB2="$(nb "$ROW_DIR/playback.xml" w10m_nav_bar)"
  note "name $PN / date $PD / disc $PL / scrubber $SC / appbar $AB"
  assert_within "4.1 the name is centred" 540 "$(f "$PN" 7)" "$PX"
  assert_within "4.1 the date is centred" 540 "$(f "$PD" 7)" "$PX"
  assert_eq "4.1 the date is under the name" yes "$([ "$(f "$PD" 2)" -gt "$(f "$PN" 4)" ] && echo yes || echo no)"
  assert_within "4.3 the play disc is 96 epx" "$(( 96 * PX ))" "$(f "$PL" 5)" "$(( 2 * PX ))"
  assert_within "4.3 the play disc is centred on W/2" 540 "$(f "$PL" 7)" "$PX"
  assert_within "4.5 the scrubber's thumb ring is 24 epx (the scrubber node's height)" "$(( 24 * PX ))" "$(f "$SC" 6)" "$PX"
  TRACK="$(python3 - "$ROW_DIR/playback.png" "$(f "$SC" 8)" "$(f "$SC" 3)" <<'PY'
import sys
import numpy as np
from PIL import Image
a = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(np.int16)
cy, right = int(float(sys.argv[2])), int(sys.argv[3])
x = right - 60                                   # in the unplayed part, left of the track's end
col = a[cy - 20:cy + 20, x]
rows = [i for i, p in enumerate(col) if (np.abs(p - np.array([51, 51, 51])) <= 10).all()]
print(len(rows))
PY
)"
  note "unplayed track thickness at x right-60 (rows of white-20%): $TRACK px"
  assert_within "4.5 the track is 2 epx thick" "$(( 2 * PX ))" "$TRACK" 1
  assert_within "4.7 the app bar is 48.2 epx tall (R7 §3.5.8)" 145 "$(f "$AB" 6)" 2
  assert_eq "4.7 the app bar sits on the drawn nav bar" "$(f "$NB2" 2)" "$(f "$AB" 4)"
  C1="$(f "$(nb "$ROW_DIR/playback.xml" 'rec_bar:share')" 7)"; C2="$(f "$(nb "$ROW_DIR/playback.xml" 'rec_bar:trim')" 7)"
  C3="$(f "$(nb "$ROW_DIR/playback.xml" 'rec_bar:delete')" 7)"; C4="$(f "$(nb "$ROW_DIR/playback.xml" 'rec_bar:rename')" 7)"
  note "app bar button centres: share $C1 trim $C2 delete $C3 rename $C4 more $(f "$(nb "$ROW_DIR/playback.xml" 'rec_bar:more')" 7)"
  assert_within "4.7 Share → Trim at the 68-epx pitch" "$(( 68 * PX ))" "$(python3 -c 'import sys; print(float(sys.argv[1])-float(sys.argv[2]))' "$C2" "$C1")" "$PX"
  assert_within "4.7 Trim → Delete at the 68-epx pitch" "$(( 68 * PX ))" "$(python3 -c 'import sys; print(float(sys.argv[1])-float(sys.argv[2]))' "$C3" "$C2")" "$PX"
  assert_within "4.7 Delete → Rename at the 68-epx pitch" "$(( 68 * PX ))" "$(python3 -c 'import sys; print(float(sys.argv[1])-float(sys.argv[2]))' "$C4" "$C3")" "$PX"
  assert_within "4.7 the dots button is 48 epx wide, flush right" "$(( 1080 - 24 * PX ))" "$(f "$(nb "$ROW_DIR/playback.xml" 'rec_bar:more')" 7)" "$PX"
  bars_check "$ROW_DIR/playback.xml" "playback page"
  record "4.4 (LOW) the flag's centre below the disc centre, epx (99)" "$(python3 -c 'import sys; print(round((float(sys.argv[1])-float(sys.argv[2]))/3,1))' "$(f "$(nb "$ROW_DIR/playback.xml" rec_flag)" 8)" "$(f "$PL" 8)")"
  record "4.6 (LOW) the track's centre above the app bar's top, epx (75)" "$(python3 -c 'import sys; print(round((float(sys.argv[1])-float(sys.argv[2]))/3,1))' "$(f "$AB" 2)" "$(f "$SC" 8)")"
  adb shell input keyevent KEYCODE_BACK; sleep 1
  rec_ring_save
  # ---------------------------------------------------------------- restore
  for id in $A $B; do app_delete_take "$id"; done
  assert_eq "restore: the takes are deleted" 0 "$(own_count)"
  [ "$(own_count)" != 0 ] && note "sweeping leftovers -> $(purge_own_takes) remain"
fi
[ -n "$TONE_PID" ] && tone_loop_stop "$TONE_PID"
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
