#!/usr/bin/env bash
# Phase 17 E24 — baseline and seeds (C-3; r3 V7).
#
#   P  the baseline   layout_restore of qa/phase-17/baseline_layout.json, then Home (ensure_start): the launcher's ring,
#                     read from a MARK taken just before the restore, holds assignSlotOnce lines (so the read covers
#                     this load), ZERO `assignSlotOnce … -> assigned` lines (absent_in), and an `-> already run` line for
#                     every assignSlotOnce marker the baseline FILE holds — five as the doc lists them today
#                     (slot:music:v1, slot:calendar:v1, slot:people:v1, slot:photos:v1, slot:camera:v1; the list is read
#                     from the file and asserted equal to the doc's, so a drift between the two shows); the restored
#                     file's addedOnce equals the baseline file's exactly; slots holds PHOTOS → PhotosActivity and
#                     CAMERA → CameraActivity.
#   N  the negative   the same run against qa/phase-17/baseline_layout-pre-17.json: exactly two `-> assigned` lines
#                     (slot:photos:v1 and slot:camera:v1), the three older markers still `-> already run`. This is the
#                     leg that proves P's zero can fail. layout_restore's own verification refuses this file (the shell
#                     writes the two slots on load, so what is on disk no longer equals it): its exit code is RECORDED,
#                     the doc does not grade it.
#   restore           layout_restore of the baseline again (rc 0), slots and addedOnce read back, Start.
#
# Changes on the device: the Start layout only (restored). It force-stops the shell (layout_restore does); no wipe.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
OUT="$QA/E24"; mkdir -p "$OUT"
PRE17="$P17/baseline_layout-pre-17.json"
SHELL_PHOTOS="app.tileshell/app.tileshell.photos.PhotosActivity"
SHELL_CAMERA="app.tileshell/app.tileshell.camera.CameraActivity"
DOC_SLOT_MARKERS="slot:calendar:v1 slot:camera:v1 slot:music:v1 slot:people:v1 slot:photos:v1"

slot_of() { layout_json | python3 -c 'import json, sys; print(json.load(sys.stdin).get("slots", {}).get(sys.argv[1], ""))' "$1"; }
once_of() { python3 -c 'import json, sys; print(" ".join(sorted(json.load(open(sys.argv[1])).get("addedOnce", []))))' "$1"; }
slot_markers_of() { python3 -c 'import json, sys; print(" ".join(sorted(m for m in json.load(open(sys.argv[1])).get("addedOnce", []) if m.startswith("slot:"))))' "$1"; }
# The seed's lines of the load that followed MARK: the listener re-binds a moment after Home, so the read waits (at most
# 20 s) until the slice holds an assignSlotOnce line, and is kept beside the row.
seed_lines() { # mark out.txt
  local i
  for i in $(seq 1 20); do
    ring_since "$1" > "$2"
    grep -qF 'assignSlotOnce' "$2" && break
    sleep 1
  done
  grep -F 'assignSlotOnce' "$2"
}

take_device_lock
if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
  adb install -r "$APK" > "$OUT/install.out" 2>&1 || { echo "E24: adb install -r of $APK failed:" >&2; cat "$OUT/install.out" >&2; exit 4; }
fi
row_begin E24 "baseline and seeds: zero -> assigned after the baseline, exactly two after the pre-17 file (C-3)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
assert_eq "wake" "Awake" "$(wake_device)"

# ----------------------------------------------------------------------------------------------- P: the baseline
log "--- P: layout_restore of qa/phase-17/baseline_layout.json, then Home"
FILE_MARKERS="$(slot_markers_of "$BASELINE")"
assert_eq "the baseline file's assignSlotOnce markers are the doc's five" "$DOC_SLOT_MARKERS" "$FILE_MARKERS"
rings_save
MARK="$(ring_mark)"
layout_restore "$BASELINE" > "$OUT/P-restore.out" 2>&1; echo $? > "$OUT/P-restore.rc"
assert_eq "P: layout_restore of the baseline" "0" "$(cat "$OUT/P-restore.rc")"
ensure_start
SEED="$(seed_lines "$MARK" "$OUT/P-slice.txt")"
log "the seed's lines after the baseline:"; printf '%s\n' "$SEED" | tee -a "$LOG"
assert_ne "P: the slice holds assignSlotOnce lines (it covers this load)" "0" "$(printf '%s\n' "$SEED" | grep -cF 'assignSlotOnce')"
absent_in "P: ZERO assignSlotOnce … -> assigned lines" "-> assigned" "$SEED"
for m in $FILE_MARKERS; do
  assert_contains "P: $m … -> already run" "-> already run" "$(printf '%s\n' "$SEED" | grep -F "assignSlotOnce $m " | tail -1)"
done
layout_json | tr -d '\r' > "$OUT/P-layout.json"
record "the baseline file's addedOnce" "$(once_of "$BASELINE")"
assert_eq "P: the restored file's addedOnce equals the baseline file's exactly" "$(once_of "$BASELINE")" "$(once_of "$OUT/P-layout.json")"
for m in phase03:cortana folder:games:v1 folder:office:v1 slot:music:v1 slot:calendar:v1 slot:people:v1 slot:photos:v1 slot:camera:v1; do
  assert_contains "P: addedOnce holds $m (the doc's list as written today)" " $m " " $(once_of "$OUT/P-layout.json") "
done
assert_eq "P: slots.PHOTOS is PhotosActivity" "$SHELL_PHOTOS" "$(slot_of PHOTOS)"
assert_eq "P: slots.CAMERA is CameraActivity" "$SHELL_CAMERA" "$(slot_of CAMERA)"

# ----------------------------------------------------------------------------------------------- N: the negative
log "--- N: the same run against qa/phase-17/baseline_layout-pre-17.json (the negative)"
assert_eq "N: the pre-17 file holds neither marker" "slot:calendar:v1 slot:music:v1 slot:people:v1" "$(slot_markers_of "$PRE17")"
rings_save
MARK="$(ring_mark)"
layout_restore "$PRE17" > "$OUT/N-restore.out" 2>&1; echo $? > "$OUT/N-restore.rc"
record "N: layout_restore of the pre-17 file, rc (its own check refuses a file the shell re-seeds on load)" "$(cat "$OUT/N-restore.rc"): $(tail -1 "$OUT/N-restore.out")"
ensure_start
SEED="$(seed_lines "$MARK" "$OUT/N-slice.txt")"
log "the seed's lines after the pre-17 file:"; printf '%s\n' "$SEED" | tee -a "$LOG"
assert_eq "N: exactly two assignSlotOnce … -> assigned lines" "2" "$(printf '%s\n' "$SEED" | grep -cF -- '-> assigned')"
assert_contains "N: one is slot:photos:v1's" "assignSlotOnce slot:photos:v1 PHOTOS -> $PHOTOS_ACTIVITY -> assigned" "$SEED"
assert_contains "N: the other is slot:camera:v1's" "assignSlotOnce slot:camera:v1 CAMERA -> $CAMERA_ACTIVITY -> assigned" "$SEED"
for m in slot:music:v1 slot:calendar:v1 slot:people:v1; do
  assert_contains "N: $m … -> already run" "-> already run" "$(printf '%s\n' "$SEED" | grep -F "assignSlotOnce $m " | tail -1)"
done
layout_json | tr -d '\r' > "$OUT/N-layout.json"
assert_eq "N: the load wrote slots.PHOTOS" "$SHELL_PHOTOS" "$(slot_of PHOTOS)"
assert_eq "N: … and slots.CAMERA" "$SHELL_CAMERA" "$(slot_of CAMERA)"
assert_eq "N: … and both markers into addedOnce" "$(once_of "$BASELINE")" "$(once_of "$OUT/N-layout.json")"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore: the baseline"
rings_save
MARK="$(ring_mark)"
layout_restore "$BASELINE" > "$OUT/restore.out" 2>&1; echo $? > "$OUT/restore.rc"
assert_eq "restore: layout_restore of the baseline" "0" "$(cat "$OUT/restore.rc")"
ensure_start
SEED="$(seed_lines "$MARK" "$OUT/restore-slice.txt")"
absent_in "restore: zero -> assigned lines again" "-> assigned" "$SEED"
assert_eq "restore: slots.PHOTOS is PhotosActivity" "$SHELL_PHOTOS" "$(slot_of PHOTOS)"
assert_eq "restore: slots.CAMERA is CameraActivity" "$SHELL_CAMERA" "$(slot_of CAMERA)"
layout_json | tr -d '\r' > "$OUT/restore-layout.json"
assert_eq "restore: addedOnce equals the baseline file's" "$(once_of "$BASELINE")" "$(once_of "$OUT/restore-layout.json")"
row_end
