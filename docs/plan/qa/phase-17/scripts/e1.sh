#!/usr/bin/env bash
# Phase 17 E1 — slot seeding with take-over (the re-cut of phase 01 E4; the owner's ruling Q-17-3 (a)) and the upgrade.
#
#   children  phase 01 E4 / E4b (e4_part1.sh, e4_part2.sh) on this build, run before the row holds the device: its
#             2+-handler negative is read on Mail and Store only — Photos and Camera stopped being negatives.
#   W  wiped state   pm clear → provision.sh → Start: both seed lines read "-> assigned", the PHOTOS tile and the dock's
#                    CAMERA tile open the shell's apps, with two APP_GALLERY and three STILL_IMAGE_CAMERA handlers of
#                    other apps still installed (so the seed did it, not a one-handler auto-assignment).
#   U  upgrade       uninstall; provision the last pre-17 build; PHOTOS pointed at Aves by hand, CAMERA untouched;
#                    adb install -r this build: PHOTOS taken over once ("replaced user's …Aves…"), CAMERA assigned with
#                    no "replaced"; both tiles open the shell's apps.
#   B  point back    PHOTOS pointed at Aves again, a second adb install -r of the same APK: "-> already run" for both,
#                    no "-> assigned" line, and the PHOTOS tile opens Aves (the marker has run).
#   restore          pm clear → provision.sh → Start → layout_restore of the baseline.
#
# THIS ROW WIPES AND REINSTALLS THE SHELL: it runs only when no other session is using the device.
# E1_CHILDREN=0 skips the children (driver development); the gate run never sets it.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
QAR="$(cd "$QA/.." && pwd)"
OUT="$QA/E1"; mkdir -p "$OUT"
OLD_APK="$QA/upgrade/phase-16-585b457f.apk"
NEW_APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"
SHELL_PHOTOS="app.tileshell/app.tileshell.photos.PhotosActivity"
SHELL_CAMERA="app.tileshell/app.tileshell.camera.CameraActivity"
AVES_SHORT="deckers.thibault.aves.libre/deckers.thibault.aves.MainActivity"

slot_of() { layout_json | python3 -c 'import json, sys; print(json.load(sys.stdin).get("slots", {}).get(sys.argv[1], ""))' "$1"; }
added_once() { layout_json | python3 -c 'import json, sys; print(" ".join(sorted(json.load(sys.stdin).get("addedOnce", []))))'; }
short() { python3 -c 'import sys; p, c = sys.argv[1].split("/"); print(p + "/" + (c[len(p):] if c.startswith(p + ".") else c))' "$1"; }
flat() { python3 -c 'import sys; p, c = sys.argv[1].split("/"); print(p + "/" + (p + c if c.startswith(".") else c))' "$1"; }
handlers() { adb shell cmd package query-activities --brief "$@" | tr -d '\r' | grep '/' | tr -d ' '; }
start_dump() { ensure_start; gdump "$1"; }
wizard_absent() { [ "$(has_node "$1" start_page)" = yes ] && [ "$(has_node "$1" wizard_page)" = no ] && echo yes || echo no; }
candidates() { grep -o 'resource-id="slot_candidate:[^"]*"' "$1" | sed 's/resource-id="slot_candidate://; s/"$//' | sort | tr '\n' ' ' | sed 's/ $//'; }
open_picker() { # slot out.xml — Settings > Tile apps → the slot's picker, dumped
  adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page TILE_APPS >/dev/null 2>&1; sleep 3
  dump_ui "$OUT/.tileapps.xml"
  scroll_to_node "$OUT/.tileapps.xml" "tile_app_slot:$1" 6 >/dev/null 2>&1 || true
  tap_node "$OUT/.tileapps.xml" "tile_app_slot:$1"; sleep 2
  dump_ui "$2"
}
provision() { # label [env...]
  local label="$1"; shift
  ( env "$@" bash "$P03S/provision.sh" > "$OUT/provision-$label.out" 2>&1; echo $? > "$OUT/provision-$label.rc" )
  cat "$OUT/provision-$label.rc"
}
tap_tile() { tap_node "$1" "$2"; sleep 4; }
# The tap on a tile and what it resumed, then C-6 (the promoted tile lives in memory only) and Start again.
opens() { # name dump tile-id expected-activity
  tap_tile "$2" "$3"
  assert_eq "$1" "$4" "$(top_activity)"
  c6; ensure_start
}

# The lock is taken before the children: phase 01's E4 drivers do not take it themselves.
take_device_lock

# =============================================================================================== children
if [ "${E1_CHILDREN:-1}" = "1" ]; then
  layout_restore "$BASELINE" > "$OUT/children-seed.out" 2>&1; echo $? > "$OUT/children-seed.rc"
  rm -rf "$OUT/phase-01-E04.prev"; [ -e "$OUT/phase-01-E04" ] && mv "$OUT/phase-01-E04" "$OUT/phase-01-E04.prev"
  mkdir -p "$OUT/phase-01-E04"
  ( bash "$P01S/e4_part1.sh" "$OUT/phase-01-E04" > "$OUT/phase-01-E04-part1.out" 2>&1; echo $? > "$OUT/phase-01-E04-part1.rc" )
  ( bash "$P01S/e4_part2.sh" "$OUT/phase-01-E04" > "$OUT/phase-01-E04-part2.out" 2>&1; echo $? > "$OUT/phase-01-E04-part2.rc" )
  layout_restore "$BASELINE" > "$OUT/children-seed2.out" 2>&1; echo $? > "$OUT/children-seed2.rc"
fi

# =============================================================================================== the row
row_begin E1 "slot seeding with take-over: wiped, upgrade, point back (Q-17-3 (a))"
record "this build" "$(md5sum "$NEW_APK" | cut -c1-16) $(stat -c%s "$NEW_APK") bytes ($(git -C "$REPO" rev-parse --short HEAD))"
record "the pre-17 build for the upgrade leg" "$(md5sum "$OLD_APK" 2>/dev/null | cut -c1-16) $(stat -c%s "$OLD_APK" 2>/dev/null) bytes — phase 16's gate build, commit e226bc68"
assert_eq "the pre-17 APK is the kept one (md5 585b457ffc29878c)" "585b457ffc29878c" "$(md5sum "$OLD_APK" | cut -c1-16)"
assert_eq "wake" "Awake" "$(wake_device)"

if [ "${E1_CHILDREN:-1}" = "1" ]; then
  log "--- children: phase 01 E4 / E4b on this build (the 2+-handler negative on Mail and Store only)"
  for f in children-seed children-seed2; do assert_eq "$f: layout_restore of the baseline" "0" "$(cat "$OUT/$f.rc")"; done
  E04="$OUT/phase-01-E04/E04.txt"
  assert_eq "phase 01 e4_part1.sh rc" "0" "$(cat "$OUT/phase-01-E04-part1.rc")"
  record "phase 01 e4_part2.sh rc (its last command is grep -c: 1 = it counted no crash)" "$(cat "$OUT/phase-01-E04-part2.rc")"
  assert_eq "E4b ran to its end: the crash-buffer count it logged is 0" "0" "$(grep -A1 -F '$ crash buffer since this run' "$E04" | tail -1)"
  assert_eq "E4: APP_MAPS has one handler" "1" "$(grep -m1 '^APP_MAPS:' "$E04" | tr ' ' '\n' | grep -c '/')"
  assert_contains "E4: the Maps tile is auto-assigned (no Tap to choose)" "tile:slot:MAPS" "$(grep 'tile:slot:MAPS' "$E04" | grep -v 'Tap to choose' | head -1)"
  assert_contains "E4: the Mail tile is unassigned (the 2+-handler negative)" "Tap to choose" "$(grep -m1 'tile:slot:MAIL' "$E04")"
  assert_contains "E4: … and its Tile apps row says so" "not chosen yet" "$(grep -m1 'tile_app_slot:MAIL' "$E04")"
  assert_contains "E4: the Store tile is unassigned (the 2+-handler negative)" "Store / None · not chosen yet" "$(grep -m1 'tile_app_slot:STORE' "$E04")"
  # Photos and Camera are no longer the negative: in a state a user can reach the seed has pointed both at the shell's
  # apps (legs W and U). What E4's emptied layout (a harness-only state: slots {} with the markers kept) shows for them
  # is recorded, as phase 16 recorded Calendar and People.
  record "E4's emptied layout: APP_GALLERY handlers" "$(grep -m1 '^APP_GALLERY:' "$E04")"
  record "E4's emptied layout: STILL_IMAGE_CAMERA handlers" "$(grep -m1 '^STILL_IMAGE_CAMERA:' "$E04")"
  record "E4's emptied layout: the Photos tile" "$(grep -m1 'tile:slot:PHOTOS' "$E04")"
  record "E4b's Camera picker (the emptied layout)" "$(grep -m1 '^camera picker candidates:' "$E04")"
  assert_contains "E4b: the Mail picker lists K-9 by component" "com.fsck.k9/" "$(grep -m1 '^picker candidates:' "$E04")"
  assert_contains "E4b: choosing K-9 assigned the slot" "com.fsck.k9" "$(grep -m1 '^layout slots:' "$E04")"
else
  record "children" "SKIPPED (E1_CHILDREN=0): a development run, not the gate's"
fi

# ----------------------------------------------------------------------------------------------- W: wiped state
log "--- W: wiped state (pm clear → provision.sh → Start)"
rings_save
adb shell pm clear app.tileshell >/dev/null
W_MARK="$(ring_mark)"
# The seed runs in the FIRST process after the wipe, and provision.sh ends on a force-stop, which empties that
# process's ring (run 1 on 95b54303, kept, read only "-> already run" after it). So the wiped shell is started once
# and its seed lines are read BEFORE provision.sh; the doc's order (pm clear -> provision.sh -> Start) holds for
# everything else in this leg. Lead's re-cut, 2026-10-06 (INDEX Change Log).
# The launcher's ring is read through its notification listener (lib.sh diag), which Android binds only once the
# listener is allowed - one of provision.sh's own steps, done here first so the wiped process's ring can be read
# (run 2, kept: without it the slice was empty).
adb shell cmd notification allow_listener app.tileshell/app.tileshell.feeds.TileNotificationListener >/dev/null 2>&1
adb shell am start -W -n app.tileshell/app.tileshell.StartActivity >/dev/null 2>&1; sleep 6
W_SLICE="$(ring_since "$W_MARK")"; printf '%s\n' "$W_SLICE" > "$OUT/W-slice.txt"
rings_save
assert_eq "W: provision.sh rc" "0" "$(provision wiped)"
start_dump "$OUT/W-start.xml"
assert_eq "W: Start, no wizard" "yes" "$(wizard_absent "$OUT/W-start.xml")"
log "the seed's lines on the wiped install:"; grep -F 'assignSlotOnce slot:photos:v1' "$OUT/W-slice.txt" | tee -a "$LOG"; grep -F 'assignSlotOnce slot:camera:v1' "$OUT/W-slice.txt" | tee -a "$LOG"
assert_contains "W: the PHOTOS seed line" "[layout] assignSlotOnce slot:photos:v1 PHOTOS -> $PHOTOS_ACTIVITY -> assigned" "$W_SLICE"
assert_contains "W: the CAMERA seed line" "[layout] assignSlotOnce slot:camera:v1 CAMERA -> $CAMERA_ACTIVITY -> assigned" "$W_SLICE"
absent_in "W: neither line says replaced (nothing was picked by hand)" "replaced user's" "$(printf '%s\n' "$W_SLICE" | grep -E 'assignSlotOnce slot:(photos|camera):v1')"
assert_eq "W: slots.PHOTOS is the shell's Photos" "$SHELL_PHOTOS" "$(slot_of PHOTOS)"
assert_eq "W: slots.CAMERA is the shell's Camera" "$SHELL_CAMERA" "$(slot_of CAMERA)"
L="$(added_once)"
assert_contains "W: addedOnce holds slot:photos:v1" "slot:photos:v1" "$L"
assert_contains "W: addedOnce holds slot:camera:v1" "slot:camera:v1" "$L"
# The seed did it, not a one-handler auto-assignment: other apps still answer both categories.
G="$(handlers -a android.intent.action.MAIN -c android.intent.category.APP_GALLERY)"; note "APP_GALLERY handlers: $(echo $G)"
C="$(handlers -a android.media.action.STILL_IMAGE_CAMERA)"; note "STILL_IMAGE_CAMERA handlers: $(echo $C)"
assert_eq "W: two APP_GALLERY handlers of other apps are installed" "2" "$(printf '%s\n' "$G" | grep -v '^app.tileshell/' | grep -c '/')"
assert_eq "W: three STILL_IMAGE_CAMERA handlers of other apps are installed" "3" "$(printf '%s\n' "$C" | grep -v '^app.tileshell/' | grep -c '/')"
assert_contains "W: the shell's Photos is an APP_GALLERY handler too" "$PHOTOS_ACTIVITY" "$G"
assert_contains "W: the shell's Camera is a STILL_IMAGE_CAMERA handler too" "$CAMERA_ACTIVITY" "$C"
assert_ne "W: the PHOTOS tile is on Start" "" "$(bounds "$OUT/W-start.xml" tile:slot:PHOTOS)"
assert_ne "W: the CAMERA tile is in the bottom row" "" "$(bounds "$OUT/W-start.xml" tile:dock:slot:CAMERA)"
opens "W: the PHOTOS tile's tap resumes the shell's Photos" "$OUT/W-start.xml" tile:slot:PHOTOS "$PHOTOS_ACTIVITY"
start_dump "$OUT/W-start2.xml"
opens "W: the bottom row's CAMERA tile resumes the shell's Camera" "$OUT/W-start2.xml" tile:dock:slot:CAMERA "$CAMERA_ACTIVITY"

# ----------------------------------------------------------------------------------------------- U: the upgrade
log "--- U: upgrade (uninstall; provision the pre-17 build; PHOTOS pointed at Aves by hand; adb install -r this build)"
rings_save
adb uninstall app.tileshell > "$OUT/U-uninstall.out" 2>&1
assert_eq "U: provision.sh with the pre-17 APK rc" "0" "$(provision old "TILESHELL_APK=$OLD_APK")"
assert_eq "U: the device holds the pre-17 build (apk match: NO against this build is expected here)" "585b457ffc29878c" "$(installed_apk_id | cut -c1-16)"
start_dump "$OUT/U-old-start.xml"
assert_eq "U: the old build shows Start and no wizard" "yes" "$(wizard_absent "$OUT/U-old-start.xml")"
assert_eq "U: on the old build there is no slots.PHOTOS yet" "" "$(slot_of PHOTOS)"
assert_eq "U: … and no slots.CAMERA" "" "$(slot_of CAMERA)"
open_picker PHOTOS "$OUT/U-old-picker-photos.xml"
note "the old build's PHOTOS picker: $(candidates "$OUT/U-old-picker-photos.xml")"
tap_node "$OUT/U-old-picker-photos.xml" "slot_candidate:$AVES_SHORT"; sleep 2
OLD_PHOTOS="$(slot_of PHOTOS)"
assert_eq "U: the hand pick is explicit before the update: slots.PHOTOS is Aves" "$(flat "$AVES_SHORT")" "$OLD_PHOTOS"
assert_eq "U: CAMERA is left untouched (no slots.CAMERA)" "" "$(slot_of CAMERA)"
adb shell input keyevent KEYCODE_HOME; sleep 2
U_MARK="$(ring_mark)"
adb install -r "$NEW_APK" > "$OUT/U-install.out" 2>&1; echo $? > "$OUT/U-install.rc"
note "adb install -r: $(tail -1 "$OUT/U-install.out")"
assert_eq "U: adb install -r of this build succeeds (the same debug key)" "0" "$(cat "$OUT/U-install.rc")"
assert_absent "U: no INSTALL_FAILED_UPDATE_INCOMPATIBLE" "INSTALL_FAILED" "$(cat "$OUT/U-install.out")"
assert_contains "U: the device now holds this build" "yes" "$(apk_matches)"
sleep 6
start_dump "$OUT/U-new-start.xml"
U_SLICE="$(ring_since "$U_MARK")"; printf '%s\n' "$U_SLICE" > "$OUT/U-slice.txt"; ring_save
log "the seed's lines after the update:"; grep -F 'assignSlotOnce' "$OUT/U-slice.txt" | tee -a "$LOG"
assert_contains "U: PHOTOS taken over once, the line naming what it replaced (Q-17-3 (a))" \
  "assignSlotOnce slot:photos:v1 PHOTOS -> $PHOTOS_ACTIVITY -> assigned, replaced user's $(short "$OLD_PHOTOS")" "$U_SLICE"
CAM_LINE="$(grep -F 'assignSlotOnce slot:camera:v1' "$OUT/U-slice.txt" | head -1)"
assert_contains "U: CAMERA, never touched by hand, is assigned" "assignSlotOnce slot:camera:v1 CAMERA -> $CAMERA_ACTIVITY -> assigned" "$CAM_LINE"
assert_absent "U: … with no 'replaced'" "replaced" "$CAM_LINE"
absent_in "U: no 'kept user's' line from these two markers" "kept user's" "$(printf '%s\n' "$U_SLICE" | grep -E 'assignSlotOnce slot:(photos|camera):v1')"
assert_eq "U: slots.PHOTOS is the shell's Photos" "$SHELL_PHOTOS" "$(slot_of PHOTOS)"
assert_eq "U: slots.CAMERA is the shell's Camera" "$SHELL_CAMERA" "$(slot_of CAMERA)"
opens "U: the PHOTOS tile's tap resumes the shell's Photos" "$OUT/U-new-start.xml" tile:slot:PHOTOS "$PHOTOS_ACTIVITY"
start_dump "$OUT/U-new-start2.xml"
opens "U: the bottom row's CAMERA tile resumes the shell's Camera" "$OUT/U-new-start2.xml" tile:dock:slot:CAMERA "$CAMERA_ACTIVITY"

# ----------------------------------------------------------------------------------------------- B: pointing it back
log "--- B: pointing it back sticks (a second adb install -r of the same APK)"
open_picker PHOTOS "$OUT/B-picker-photos.xml"
note "this build's PHOTOS picker: $(candidates "$OUT/B-picker-photos.xml")"
tap_node "$OUT/B-picker-photos.xml" "slot_candidate:$AVES_SHORT"; sleep 2
assert_eq "B: pointed back: slots.PHOTOS is Aves" "$OLD_PHOTOS" "$(slot_of PHOTOS)"
adb shell input keyevent KEYCODE_HOME; sleep 2
ring_save
B_MARK="$(ring_mark)"
adb install -r "$NEW_APK" > "$OUT/B-install.out" 2>&1; echo $? > "$OUT/B-install.rc"
assert_eq "B: the second adb install -r succeeds" "0" "$(cat "$OUT/B-install.rc")"
sleep 6
start_dump "$OUT/B-start.xml"
B_SLICE="$(ring_since "$B_MARK")"; printf '%s\n' "$B_SLICE" > "$OUT/B-slice.txt"
log "the seed's lines after the second install:"; grep -F 'assignSlotOnce' "$OUT/B-slice.txt" | tee -a "$LOG"
assert_contains "B: slot:photos:v1 … -> already run" "assignSlotOnce slot:photos:v1 PHOTOS -> $PHOTOS_ACTIVITY -> already run" "$B_SLICE"
assert_contains "B: slot:camera:v1 … -> already run" "assignSlotOnce slot:camera:v1 CAMERA -> $CAMERA_ACTIVITY -> already run" "$B_SLICE"
absent_in "B: no assignSlotOnce … -> assigned line after the second install" "-> assigned" "$(printf '%s\n' "$B_SLICE" | grep -F 'assignSlotOnce')"
assert_eq "B: slots.PHOTOS is still Aves" "$OLD_PHOTOS" "$(slot_of PHOTOS)"
opens "B: the PHOTOS tile's tap resumes Aves (the marker has run)" "$OUT/B-start.xml" tile:slot:PHOTOS "$AVES_SHORT"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore: pm clear → provision.sh → Start → the baseline"
rings_save
adb shell pm clear app.tileshell >/dev/null
assert_eq "restore: provision.sh rc" "0" "$(provision restore)"
ensure_start
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline" "0" "$?"
assert_eq "restore: slots.PHOTOS is the shell's Photos" "$SHELL_PHOTOS" "$(slot_of PHOTOS)"
assert_eq "restore: slots.CAMERA is the shell's Camera" "$SHELL_CAMERA" "$(slot_of CAMERA)"
assert_eq "restore: CAMERA held" "true" "$(perm_granted CAMERA)"
assert_eq "restore: READ_MEDIA_VIDEO held" "true" "$(perm_granted READ_MEDIA_VIDEO)"
ensure_start
row_end
