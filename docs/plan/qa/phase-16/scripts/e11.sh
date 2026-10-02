#!/usr/bin/env bash
# Phase 16 E11 — the People tile (R3 A9; [fidelity] H3), clause by clause from the phase doc's row E11.
#
#   fixtures   people_fixtures_up; Ann, Bob and Zoë each get a photo through People's editor and Android's photo
#              picker (E13's steps, run here) from the three solid-colour JPEGs of qa/phase-16/fixtures (red, green,
#              blue), each asserted as a vnd.android.cursor.item/photo data row BEFORE Start is read
#   40 s       on Start the ring holds ≥ 4 `[people] tile event <n> t0=<uptime> lookup=<key>` lines 7.7 ± 0.2 s + one
#              frame apart; per event `[motion] people_bubble_out … settle` ≈ 333 ms and `people_bubble_in … settle`
#              ≈ 583 ms (each ± 42 ms + one frame), 1.88 ± 0.04 s + one frame from the out's t0 to the in's settle,
#              every one of those lines with maxGapMs ≤ 33.4 (C-31). (The pause between the slides is 964 ms as
#              built — Change Log 2026-10-01, P1 — and is not a clause.)
#   pixels     for each event a screencap after its people_bubble_in settle: the settled bubble's centre pixel equals,
#              ± 8 levels per channel, the fixture colour of the contact its lookup=<key> names; ≥ 2 lookups in the 40 s
#   record     a 60-fps screenrecord of the same 40 s corroborates under phase 05's frame-spacing rule (never the clock)
#   dumps      every dump of Start is gdump (phase 05's gesture driver; Start never idles while the tile cycles)
#   tap        the tile's tap resumes the shell's People
#   pinned     a People app tile pinned from the app list shows the same bubble events (r3 D12)
#   no photo   with every photo removed, 40 s of screencaps at 1-s intervals show no change on the tile (the static
#              pattern) and diagnostics `[people] tile: 0 photos`
#   restore    people_fixtures_down, the pushed JPEGs removed, layout_restore of the baseline (the pin)
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"
declare -A COLOUR
on_screen() { # dump.xml tile-id -> yes when the tile's bounds lie wholly between the drawn bars
  local b sb nt; b="$(bounds "$1" "$2")"; sb="$(status_bottom "$1")"; nt="$(nav_top "$1")"
  [ -n "$b" ] && [ "$(bfield "$b" 2)" -ge "${sb:-0}" ] && [ "$(bfield "$b" 4)" -le "${nt:-2340}" ] && echo yes || echo no
}
# One event, read after its in-slide settled: a screencap, then a gdump; for each tile named, its settled bubble names
# the event's contact and its centre pixel is that contact's fixture colour ± 8.
check_event() { # label lookup file-prefix tile-id...
  local label="$1" lookup="$2" p="$3" t nb b desc; shift 3
  screencap "$p.png"
  gdump "$p.xml" || true
  for t in "$@"; do
    nb="$(node_inside "$p.xml" "$t" people_tile_bubble)"
    b="${nb%%|*}"; desc="${nb#*|}"
    if [ -z "$b" ]; then _verdict FAIL "$label: a settled bubble (people_tile_bubble) in $t" "none in the dump taken after the in-slide settled"; continue; fi
    assert_eq "$label: the settled bubble in $t is the contact the event names (its content-desc)" "$lookup" "$desc"
    assert_color "$label: its centre pixel is the fixture colour of lookup=$lookup, ± 8 ($t)" "${COLOUR[$lookup]:-?}" "$(centre_px "$p.png" "$b")" 8
  done
}
# Wait (up to n seconds) until the slice from a mark holds k people_bubble_in lines.
wait_ins() { # mark k seconds
  local i
  for i in $(seq 1 $(( $3 * 2 ))); do
    [ "$(ring_since "$1" | grep -c 'people_bubble_in')" -ge "$2" ] && return 0
    sleep 0.5
  done
  return 1
}

row_begin E11 "the People tile: bubble events, their colours, a pinned app tile, the static pattern"
require_build
D="$ROW_DIR"
layout_restore "$BASELINE"; assert_eq "layout_restore of the baseline (the row reads Start's layout)" "0" "$?"
ensure_start
perm_ensure READ_CONTACTS WRITE_CONTACTS
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"

# ------------------------------------------------------------------------------------------------ fixtures and photos
log "--- fixtures; three photos through People's editor and Android's photo picker"
people_fixtures_up
for c in red green blue; do push_photo "$c" >/dev/null; done
assert_eq "the three solid-colour JPEGs are pushed" "3" "$(pushed_photos_left)"
give_photo "$ANN" red "Ann (red)"
give_photo "$BOB" green "Bob (green)"
give_photo "$ZOE" blue "Zoë (blue)"
L_ANN="$(lookup_of "$(contact_of "$ANN")")"; L_BOB="$(lookup_of "$(contact_of "$BOB")")"; L_ZOE="$(lookup_of "$(contact_of "$ZOE")")"
COLOUR[$L_ANN]="${PHOTO_RGB[red]}"; COLOUR[$L_BOB]="${PHOTO_RGB[green]}"; COLOUR[$L_ZOE]="${PHOTO_RGB[blue]}"
note "lookups: ann=$L_ANN (red) bob=$L_BOB (green) zoe=$L_ZOE (blue)"
assert_eq "before Start is read: exactly three contacts hold a photo data row" "3" "$(q "content query --uri $DATA --projection raw_contact_id --where \"mimetype='vnd.android.cursor.item/photo' AND raw_contact_id IN ($ANN,$BOB,$ZOE)\"" | grep -c 'raw_contact_id=')"

# ------------------------------------------------------------------------------------------------ Start
log "--- Start: the feed, the tile"
# The MARK is taken BEFORE the stop: the shell is the home app, Android restarts it the moment it is stopped, and the
# feed publishes as the process starts.
ring_save
F_MARK="$(ring_mark)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
sleep 3
gdump "$D/start.xml"; screencap "$D/start.png"
F_SLICE="$(ring_since "$F_MARK")"
TB="$(bounds "$D/start.xml" "$PEOPLE_SLOT_TILE")"; note "the PEOPLE slot tile: [$TB]"
assert_ne "the PEOPLE slot tile is on Start (gdump)" "" "$TB"
assert_eq "… wholly on screen" "yes" "$(on_screen "$D/start.xml" "$PEOPLE_SLOT_TILE")"
assert_ne "its face is the People face (people_tile_face inside the tile)" "" "$(node_inside "$D/start.xml" "$PEOPLE_SLOT_TILE" people_tile_face)"
log "$(printf '%s\n' "$F_SLICE" | grep -F '[people] tile:' | tail -1 | sed 's/.*\[people\]/[people]/')"
assert_contains "the feed read the three photos ([people] tile: 3 photos)" "[people] tile: 3 photos" "$F_SLICE"

# ------------------------------------------------------------------------------------------------ the 40 s
log "--- 40 s on Start: the events, their motion lines, a screencap per event, the screenrecord"
W_MARK="$(ring_mark)"
wait_ins "$W_MARK" 1 14 || note "no in-slide settled within 14 s of the warm-up"
# The window opens while a bubble rests (an in-slide has just settled), so its first event line is a whole event.
adb shell rm -f /sdcard/Download/qa-e11.mp4
adb shell screenrecord --size 540x1170 --bit-rate 6000000 --time-limit 47 /sdcard/Download/qa-e11.mp4 &
REC=$!
sleep 1
E_MARK="$(ring_mark)"; T0="$(date +%s)"
SEEN=0
while [ $(( $(date +%s) - T0 )) -lt 40 ]; do
  SL="$(ring_since "$E_MARK")"
  N="$(printf '%s\n' "$SL" | grep -c 'people_bubble_in')"
  if [ "$N" -gt "$SEEN" ]; then
    SEEN="$N"
    LK="$(printf '%s\n' "$SL" | grep -F '[people] tile event' | sed -n "${N}p" | sed -E 's/.*lookup=([^ ]+).*/\1/')"
    check_event "event $N of the window" "$LK" "$D/event-$N" "$PEOPLE_SLOT_TILE"
  fi
  sleep 0.5
done
# An event that began inside the 40 s finishes before the slice is cut (its in-slide logs at its settle).
for _ in 1 2 3 4 5 6 7 8; do
  SL="$(ring_since "$E_MARK")"
  [ "$(printf '%s\n' "$SL" | grep -c '\[people\] tile event')" -le "$(printf '%s\n' "$SL" | grep -c 'people_bubble_in')" ] && break
  sleep 0.5
done
ring_since "$E_MARK" | grep -E '\[people\] tile event|\[motion\] people_bubble' > "$D/events.txt"
sed 's/^.*wall=[0-9]* //' "$D/events.txt" >> "$LOG"
wait "$REC" 2>/dev/null
emit_verdicts "tile timing" < <(tile_times "$D/events.txt" 4 "$L_ANN $L_BOB $L_ZOE")
EVENTS="$(grep -c '\[people\] tile event' "$D/events.txt")"
assert_eq "a screencap was graded for every event whose in-slide settled inside the 40 s" "yes" "$([ "$SEEN" -ge 4 ] && [ "$SEEN" -ge $(( EVENTS - 1 )) ] && echo yes || echo no)"

# The screenrecord of those 40 s (540 x 1170; the emulator's capture is variable-rate): its motion bursts inside the
# tile, in order — out, in, out, in, … Phase 05's rule: a slide whose source frames lie more than 18.2 ms apart is
# REJECTED (not used); an accepted one must span the slide the shell logged (phase 13 E8's band: window = settle − 1
# frame ± 2 frames). The capture is never the clock.
adb pull /sdcard/Download/qa-e11.mp4 "$D/tile-40s.mp4" >/dev/null 2>&1
adb shell rm -f /sdcard/Download/qa-e11.mp4
assert_eq "the screenrecord of the 40 s was captured" "yes" "$([ -s "$D/tile-40s.mp4" ] && echo yes || echo no)"
# shellcheck disable=SC2086
set -- $TB
ROI="$(( $1 / 2 )) $(( $2 / 2 )) $(( ($3 + 1) / 2 )) $(( ($4 + 1) / 2 ))"
# A frame "moves" when the tile's region differs at all from the frame before (mean difference > 0.002 levels, above
# the codec's noise on a still region): the emulator's capture holds a frame only when the screen changed, so the
# motion's span is its first to its last changed frame. (Run 1 used phase 13's 0.2-level threshold and cut each slide
# where its ease moves the bubble by less than a capture pixel a frame — the in-slide's last third; that reading is
# kept below as a RECORD, since it is what an eye or a camera would call the settle.)
record_bursts "$D/tile-40s.mp4" "$ROI" 0.002 > "$D/bursts.txt" 2> "$D/bursts.err"
cat "$D/bursts.txt" >> "$LOG"
record_bursts "$D/tile-40s.mp4" "$ROI" 0.2 > "$D/bursts-visible.txt" 2>> "$D/bursts.err"
record "for H3: the slides as far as they move the bubble by more than 0.2 levels a frame (window ms of each burst ≥ 120 ms, in order out, in, out, …)" "$(awk '!/^#/ && $2 >= 120 {printf "%s ", $2}' "$D/bursts-visible.txt")— the in-slide's ease-out spends its last third inside the last capture pixel"
emit_verdicts "screenrecord corroboration" < <(python3 - "$D/bursts.txt" "$D/events.txt" <<'PY'
import re, sys
bursts = [tuple(float(v) for v in l.split()) for l in open(sys.argv[1]) if l.strip() and not l.startswith("#")]
slides = []
for l in open(sys.argv[2], encoding="utf-8", errors="replace"):
    m = re.search(r"\[motion\] people_bubble_(out|in) t0=(\d+) .*settle=(\d+)", l)
    if m: slides.append((m.group(1), int(m.group(2)), int(m.group(3))))
n = 0
def v(ok, name, detail):
    global n; n += 1
    print("%s|%s|%s" % ("PASS" if ok else "FAIL", name, detail))
big = [b for b in bursts if b[1] >= 120]          # a slide, not a one-frame blip
print("NOTE|bursts in the tile's region ≥ 120 ms: %d; slides logged in the window: %d|" % (len(big), len(slides)))
v(len(slides) >= 8, "the window holds at least four events' slides to corroborate", "%d slides" % len(slides))
# Pair by order and by spacing: slide k's burst starts (t0_k − t0_0) after the first slide's burst.
acc = {"out": 0, "in": 0}; rej = {"out": 0, "in": 0}; bad = []; unmatched = 0
if big and slides:
    base_b, base_s = big[0][0], slides[0][1]
    for kind, t0, settle in slides:
        want = base_b + (t0 - base_s) / 1000.0
        b = min(big, key=lambda x: abs(x[0] - want))
        if abs(b[0] - want) > 0.25: unmatched += 1; continue
        if b[3] > 18.2: rej[kind] += 1; continue
        if abs(b[1] - (settle - 16.7)) <= 33.4: acc[kind] += 1
        else: bad.append("%s settle=%d window=%.1f gap=%.1f" % (kind, settle, b[1], b[3]))
print("RECORD|slides corroborated by an accepted capture (out / in)|%d / %d|" % (acc["out"], acc["in"]))
print("RECORD|slides whose capture phase 05's rule rejects, source frames > 18.2 ms apart (out / in)|%d / %d|" % (rej["out"], rej["in"]))
print("RECORD|slides with no burst at their time in the capture|%d|" % unmatched)
v(acc["out"] >= 1, "the screenrecord corroborates an out-slide under phase 05's rule (window = settle − 1 frame ± 2 frames)", "%d accepted" % acc["out"])
v(acc["in"] >= 1, "the screenrecord corroborates an in-slide under phase 05's rule", "%d accepted" % acc["in"])
v(not bad, "no accepted capture contradicts the shell's clock", "; ".join(bad) or "none")
print("END|%d" % n)
PY
)

# ------------------------------------------------------------------------------------------------ the tile's tap
log "--- the tile's tap resumes the shell's People"
gdump "$D/tap.xml"
tap_node "$D/tap.xml" "$PEOPLE_SLOT_TILE"; sleep 4
assert_eq "the PEOPLE tile's tap resumes the shell's People" "$PEOPLE_ACTIVITY" "$(top_activity)"
c6; ensure_start

# ------------------------------------------------------------------------------------------------ a pinned app tile
log "--- a People app tile pinned from the app list shows the same bubble events (r3 D12)"
pin_people_from_app_list "$D/pin" || note "pin_people_from_app_list failed"
layout_keys() { python3 -c 'import json, sys; print(" ".join(o["key"] for o in json.load(open(sys.argv[1])).get("order", [])))' "$1" 2>/dev/null; }   # parsed: the file writes "/" as "\/"
for _ in 1 2 3 4 5 6 7 8; do layout_json | tr -d '\r' > "$D/layout-pinned.json"; case " $(layout_keys "$D/layout-pinned.json") " in *" $PEOPLE_PIN_KEY "*) break ;; esac; sleep 1; done
assert_contains "the layout holds the pinned People app tile" " $PEOPLE_PIN_KEY " " $(layout_keys "$D/layout-pinned.json") "
PIN_TILE="tile:$PEOPLE_PIN_KEY"
for i in 0 1 2 3 4 5 6 7; do
  gdump "$D/pinned-start.xml" || true
  [ "$(on_screen "$D/pinned-start.xml" "$PIN_TILE")" = yes ] && break
  adb shell input swipe 540 1500 540 1100 500; sleep 1.5
done
assert_eq "the pinned People app tile is on screen (gdump)" "yes" "$(on_screen "$D/pinned-start.xml" "$PIN_TILE")"
assert_ne "its face is the People face" "" "$(node_inside "$D/pinned-start.xml" "$PIN_TILE" people_tile_face)"
BOTH="$(on_screen "$D/pinned-start.xml" "$PEOPLE_SLOT_TILE")"
record "the PEOPLE slot tile is on screen beside the pinned tile" "$BOTH"
P_MARK="$(ring_mark)"; T0="$(date +%s)"; SEEN=0
while [ $(( $(date +%s) - T0 )) -lt 26 ] && [ "$SEEN" -lt 3 ]; do
  SL="$(ring_since "$P_MARK")"
  N="$(printf '%s\n' "$SL" | grep -c 'people_bubble_in')"; NE="$(printf '%s\n' "$SL" | grep -c '\[people\] tile event')"
  if [ "$N" -gt "$SEEN" ] && [ "$N" = "$NE" ]; then
    SEEN="$N"
    LK="$(printf '%s\n' "$SL" | grep -F '[people] tile event' | sed -n "${N}p" | sed -E 's/.*lookup=([^ ]+).*/\1/')"
    if [ "$BOTH" = yes ]; then check_event "pinned leg, event $N" "$LK" "$D/pinned-event-$N" "$PIN_TILE" "$PEOPLE_SLOT_TILE"
    else check_event "pinned leg, event $N" "$LK" "$D/pinned-event-$N" "$PIN_TILE"; fi
  elif [ "$N" -gt "$SEEN" ]; then SEEN="$N"     # an in-slide whose event began before the mark: not graded
  fi
  sleep 0.5
done
ring_since "$P_MARK" | grep -E '\[people\] tile event|\[motion\] people_bubble' | sed 's/^.*wall=[0-9]* //' >> "$LOG"
assert_eq "at least two bubble events were read on the pinned tile" "yes" "$([ "$(ls "$D"/pinned-event-*.png 2>/dev/null | wc -l)" -ge 2 ] && echo yes || echo no)"

# ------------------------------------------------------------------------------------------------ no photo
log "--- every photo removed: the static pattern, 40 s"
for i in 1 2 3 4; do adb shell input swipe 540 900 540 1900 250; sleep 0.6; done
N_MARK="$(ring_mark)"
for raw in $ANN $BOB $ZOE; do
  q "content delete --uri $DATA --where \"mimetype='vnd.android.cursor.item/photo' AND raw_contact_id=$raw\"" >/dev/null
done
assert_eq "no fixture holds a photo data row any more" "0" "$(q "content query --uri $DATA --projection raw_contact_id --where \"mimetype='vnd.android.cursor.item/photo' AND raw_contact_id IN ($ANN,$BOB,$ZOE)\"" | grep -c 'raw_contact_id=')"
for _ in $(seq 1 24); do ring_since "$N_MARK" | grep -qF '[people] tile: 0 photos' && break; sleep 0.5; done
assert_contains "diagnostics [people] tile: 0 photos" "[people] tile: 0 photos" "$(ring_since "$N_MARK")"
sleep 3      # an event that was under way when the last photo went finishes (the current bubble finishes its turn)
gdump "$D/pattern.xml" || true
TBN="$(bounds "$D/pattern.xml" "$PEOPLE_SLOT_TILE")"
assert_eq "the PEOPLE slot tile is on screen for the 40 s" "yes" "$(on_screen "$D/pattern.xml" "$PEOPLE_SLOT_TILE")"
assert_ne "the tile shows the static pattern (people_tile_pattern inside the tile)" "" "$(node_inside "$D/pattern.xml" "$PEOPLE_SLOT_TILE" people_tile_pattern)"
assert_eq "… and no bubble" "" "$(node_inside "$D/pattern.xml" "$PEOPLE_SLOT_TILE" people_tile_bubble)"
S_MARK="$(ring_mark)"; T0="$(date +%s.%N)"
for i in $(seq 1 40); do
  screencap "$D/static-$(printf '%02d' "$i").png"
  python3 -c "import sys, time; t = float(sys.argv[1]) + int(sys.argv[2]) - time.time(); time.sleep(t if t > 0 else 0)" "$T0" "$i"
done
SAME="$(python3 - "$D" $TBN <<'PY'
import glob, sys
from PIL import Image, ImageChops
d, l, t, r, b = sys.argv[1], *[int(v) for v in sys.argv[2:6]]
files = sorted(glob.glob(d + "/static-*.png"))
first = Image.open(files[0]).convert("RGB").crop((l, t, r, b))
diff = [f.split("/")[-1] for f in files[1:] if ImageChops.difference(first, Image.open(f).convert("RGB").crop((l, t, r, b))).getbbox() is not None]
print("%d screencaps, %d differ from the first on the tile%s" % (len(files), len(diff), (": " + " ".join(diff[:6])) if diff else ""))
PY
)"
assert_eq "40 screencaps at 1-s intervals show no change on the tile" "40 screencaps, 0 differ from the first on the tile" "$SAME"
absent_in "no tile event is logged while there is no photo" "[people] tile event" "$(ring_since "$N_MARK" | sed -n '/tile: 0 photos/,$p')"
# keep two of the forty stills, drop the rest (evidence stays small)
for f in "$D"/static-*.png; do case "$f" in *static-01.png|*static-40.png) ;; *) rm -f "$f" ;; esac; done

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
people_fixtures_down
remove_photos
assert_eq "restore: the pushed JPEGs are removed" "0" "$(pushed_photos_left)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: the raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
ring_save
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline (removes the pin)" "0" "$?"
layout_json | tr -d '\r' > "$D/layout-restored.json"
assert_absent "restore: the layout no longer holds the pinned People tile" " $PEOPLE_PIN_KEY " " $(layout_keys "$D/layout-restored.json") "
assert_contains "restore: … and holds the PEOPLE slot tile (the read is a real one)" " slot:PEOPLE " " $(layout_keys "$D/layout-restored.json") "
ensure_start
row_end
