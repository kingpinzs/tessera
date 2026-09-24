#!/usr/bin/env bash
# E13 — regression on the same build: phase 02's E1-style hold-and-drag in one gesture (no burst during the drag,
# `burst closed: drag`), phase 02 E7's bracket and geometry sub-rows (its motion sub-rows stay open until C-5's
# clock reaches phase 02 — T11-6) with the 830-ms hold now also asserting the burst (slot:PEOPLE is the AVD's
# Contacts, which publishes ONE manifest shortcut — T11-48), phase 02 E8 and regress.sh, C-3 after each of their
# restores, phase 01 E10's press styles (a short press shows the style and opens no burst), and phase 03 E5's
# exported allow-list. Phase 02's drivers write into E13/ here, never into phase 02's own evidence.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E13 "regression: phase 02 hold+drag, E7 bracket+geometry, E8, regress.sh; phase 01 press styles; phase 03 exports"
P02="$QROOT/phase-02/scripts"
P02_BASE="$QROOT/phase-02/baseline_layout.json"
seed_fixtures

c3_after() { # the ring slice since a mark, after a phase 02 driver's own restores
  assert_absent "$1: no slot re-assigned on load (C-3)" "-> assigned" "$(ring_since "$2" | grep assignSlotOnce)"
  assert_eq "$1: addedOnce as phase 02's file (C-3)" \
    "$(python3 -c 'import json,sys; print(",".join(sorted(json.load(open(sys.argv[1]))["addedOnce"])))' "$P02_BASE")" \
    "$(layout_json | python3 -c 'import json,sys; print(",".join(sorted(json.load(sys.stdin)["addedOnce"])))')"
}

log "--- phase 02 drivers on this build (exit codes gate) ---"
for d in regress e1 e7 e8; do
  mark="$(ring_mark)"; ring_save
  case $d in
    regress) bash "$P02/regress.sh" "$ROW_DIR/p02-REGRESS" > "$ROW_DIR/p02-$d.out" 2>&1; rc=$? ;;
    e1) bash "$P02/e1.sh" "$ROW_DIR/p02-E01" > "$ROW_DIR/p02-$d.out" 2>&1; rc=$? ;;
    e7) bash "$P02/e7.sh" "$ROW_DIR/p02-E07" slot:PEOPLE > "$ROW_DIR/p02-$d.out" 2>&1; rc=$? ;;
    e8) bash "$P02/e8.sh" "$ROW_DIR/p02-E08" > "$ROW_DIR/p02-$d.out" 2>&1; rc=$? ;;
  esac
  note "phase 02 $d exit $rc; verdict: $(grep -h '^---- ' "$ROW_DIR"/p02-*/*.txt 2>/dev/null | tail -1)"
  if [ "$d" = e7 ]; then
    # Only the bracket and geometry sub-rows count here (T11-6); read their own check lines.
    for sub in "geometry (dark)" "hold bracket (dark)" "geometry (light)" "hold bracket (light)"; do
      line="$(grep -h -- "$sub" "$ROW_DIR"/p02-E07/*.txt 2>/dev/null | grep -E '^(PASS|FAIL)' | tail -1)"
      case "$line" in PASS*) r=PASS ;; *) r=FAIL ;; esac
      _verdict "$r" "phase 02 E7 $sub" "${line:0:120}"
    done
  else
    assert_eq "phase 02 $d exits 0" 0 "$rc"
  fi
  c3_after "phase 02 $d" "$mark"
done
cat "$ROW_DIR"/p02-REGRESS/*.txt 2>/dev/null | grep -E 'passed|failed' | tail -2 | while read -r l; do note "regress: $l"; done

log "--- phase 02's hold and drag as one gesture: no burst during the drag, burst closed: drag ---"
restore baseline_layout-pre-11.json
qdump "$ROW_DIR/p02-rest.xml"
read -r X Y <<< "$(center "$ROW_DIR/p02-rest.xml" tile:slot:PEOPLE)"
MARK="$(ring_mark)"
hold_down "$X" "$Y"; sleep 0.9
for dy in 60 120 180 240 300; do hold_move "$X" $((Y + dy)); sleep 0.1; done
sleep 0.4; qdump "$ROW_DIR/p02-dragging.xml"
assert_eq "no quick_burst during the drag" no "$(has_node "$ROW_DIR/p02-dragging.xml" quick_burst)"
hold_up "$X" $((Y + 300)); sleep 1.5
assert_contains "ring: burst closed: drag" "[quick] burst closed: drag" "$(quick_since "$MARK")"
c6

log "--- phase 02 E7's 830-ms line with the burst: slot:PEOPLE (Contacts, one manifest shortcut) ---"
restore baseline_layout-pre-11.json
# Contacts' own section of the dump (`dumpsys shortcut <pkg>` prints every package; the re-judge, R2-15), as E8 filters b's.
adb shell dumpsys shortcut | awk '/Package: com.android.contacts /{f=1; print; next} f && /Package: /{f=0} f' > "$ROW_DIR/contacts-shortcuts.txt" 2>&1
note "Contacts' shortcuts: $(grep -oE 'ShortcutInfo \{id=[^,]*' "$ROW_DIR/contacts-shortcuts.txt" | sort -u | tr '\n' ' ')"
qdump "$ROW_DIR/p830-rest.xml"
read -r X Y <<< "$(center "$ROW_DIR/p830-rest.xml" tile:slot:PEOPLE)"
adb shell input swipe "$X" "$Y" "$X" "$Y" 830; sleep 1.5
qdump "$ROW_DIR/p830.xml"
assert_eq "830 ms: quick_burst present" yes "$(has_node "$ROW_DIR/p830.xml" quick_burst)"
assert_eq "830 ms: quick_sat:0 only" "yes no no no" "$(for i in 0 1 2 3; do has_node "$ROW_DIR/p830.xml" "quick_sat:$i"; done | tr '\n' ' ' | sed 's/ $//')"
c6

log "--- phase 01 E10 press styles: a short press shows the style and opens no burst ---"
# The press is SHORT by construction and measured (pass 6 found the old one racing the hold: DOWN, 0.3 s, then a PNG
# capture that took long enough on a loaded host to pass 783 ms). One device shell does it all: a raw touch down (the
# emulator's multi-touch device, with a pressure), 0.3 s, a raw capture to /data/local/tmp (no PNG encoding inside the
# press), the press's own duration read on the device clock, then the finger slides 400 px down and lifts — a scroll,
# never a tap, so nothing launches. A press that reached 700 ms is retaken (up to 3), and its duration is asserted.
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
restore baseline_layout.json
for style in none tilt p4; do
  set_press "$style"
  qdump "$ROW_DIR/press-$style.xml"
  read -r l t r b <<< "$(bounds "$ROW_DIR/press-$style.xml" tile:slot:BROWSER)"
  px=$((l + 40)); py=$((t + 40))
  adb shell screencap /data/local/tmp/qa-rest.raw; raw_png /data/local/tmp/qa-rest.raw "$ROW_DIR/press-$style-rest.png"
  ms=""
  for attempt in 1 2 3; do
    MARK="$(ring_mark)"
    ms="$(adb shell "t0=\$(date +%s%N); $(mt 100 $px $py) sleep 0.3; screencap /data/local/tmp/qa-held.raw; t1=\$(date +%s%N); $(mt 100 $px $((py + 400))) sendevent $TS 3 47 0; sendevent $TS 3 57 4294967295; sendevent $TS 0 0 0; echo \$(( (t1 - t0) / 1000000 ))" | tr -d '\r' | tail -1)"
    sleep 1
    note "press_$style attempt $attempt: pressed ${ms} ms up to the end of the capture"
    [ -n "$ms" ] && [ "$ms" -lt 700 ] && break
    c6; ensure_start_page
  done
  raw_png /data/local/tmp/qa-held.raw "$ROW_DIR/press-$style-held.png"
  assert_eq "press_$style: the press was short (under 700 ms on the device clock, capture included)" yes "$([ -n "$ms" ] && [ "$ms" -lt 700 ] && echo yes || echo "no ($ms ms)")"
  # shellcheck disable=SC2086
  eq="$(python3 "$(dirname "$0")/qpix.py" equal "$ROW_DIR/press-$style-rest.png" "$ROW_DIR/press-$style-held.png" $((l + 4)) $((t + 4)) $((r - 4)) $((b - 4)) 2)"
  if [ "$style" = none ]; then want=EQUAL; else want=DIFF; fi
  assert_eq "press_$style: the held tile $( [ "$want" = EQUAL ] && echo 'looks as at rest' || echo 'shows the press style')" "$want" "${eq%% *}"
  S="$(ring_since "$MARK")"
  assert_absent "press_$style: no burst" "[quick] burst on" "$S"
  assert_absent "press_$style: no hold" "[edit] hold" "$S"
  assert_absent "press_$style: nothing launched (the press ended in a scroll)" "[launch]" "$S"
  c6
done
set_press none

log "--- phase 03 E5's exported allow-list, on this APK ---"
python3 "$QROOT/phase-03/scripts/exported.py" "$APK" "$QROOT/phase-03/exported-allowlist.txt" > "$ROW_DIR/exported.txt" 2>&1
note "exported.py exit $?: $(head -1 "$ROW_DIR/exported.txt")"
# Phase 11 exports nothing new: the manifest diff since the last commit before phase 11's build adds no exported
# component (meta-data only), and what the allow-list lacks is exactly phase 10's Music activity and service —
# a pre-existing gap in phase 03's allow-list, not this phase's (build finding 2026-09-23, reported to Jeremy).
git -C "$REPO" diff 24469b3 -- app/src/main/AndroidManifest.xml > "$ROW_DIR/manifest-diff-since-24469b3.txt"
assert_absent "phase 11's manifest diff adds no exported component" "android:exported" "$(grep '^+' "$ROW_DIR/manifest-diff-since-24469b3.txt")"
assert_eq "outside the allow-list: exactly phase 10's Music activity and service (pre-existing)" \
  "app.tileshell.music.MusicActivity app.tileshell.music.MusicService" \
  "$(sed -n '/EXPORTED BUT NOT ALLOWED:/,/^$/p' "$ROW_DIR/exported.txt" | grep -oE 'app\.[A-Za-z0-9_.]+' | sort | tr '\n' ' ' | sed 's/ $//')"
assert_absent "nothing on the allow-list went missing" "ON THE LIST BUT NOT EXPORTED" "$(cat "$ROW_DIR/exported.txt")"
restore baseline_layout.json
row_end
