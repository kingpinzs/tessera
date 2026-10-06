#!/usr/bin/env bash
# Phase 17 EDGE — "Liveness (N-01): reboot and Device care leave the seeds, the checklist rows, the observers, the
# catalogue cache, the server token and the TMDB key intact" (the doc's Edge cases; the lead's sub-step: it REBOOTS the
# device, so it runs only when no other session is using it).
#
#   before   the dummy TMDB key saved through the setting (as E20 enters it); the two slots, the grants and the saved
#            entry's names read.
#   reboot   rings saved; adb reboot; the boot-completed poll; wake_device asserted Awake before the first tap (C-25).
#   after    the slots unchanged, both markers "-> already run" and no "-> assigned" line; the four grants still held
#            (what the checklist rows read); the sealed entry still opens after the reboot (the setting reads "A key is
#            saved.", no "[cred] tmdb: unreadable" line); the first-unlock [net] lines of the new process; the observer:
#            one picture pushed after the reboot -> the launcher's "[photos] refresh (mediastore change)" line.
#   restore  the key removed, the picture removed, Start.
#
# RECORDED, not run across a reboot: the catalogue cache (a plain file under files/video_catalogue, read back after a
# force-stop by E20's offline leg) and the media server's token (sealed in the same store, under the same Keystore key,
# as the TMDB key this row does carry across the reboot; E22's "persisted" leg reads it after a force-stop). Device
# care is Samsung's and is the phone's (P rows).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"

SHELL_PHOTOS="app.tileshell/app.tileshell.photos.PhotosActivity"
SHELL_CAMERA="app.tileshell/app.tileshell.camera.CameraActivity"
slot_of() { layout_json | python3 -c 'import json, sys; print(json.load(sys.stdin).get("slots", {}).get(sys.argv[1], ""))' "$1"; }
boot_poll() { adb wait-for-device; local i; for i in $(seq 1 120); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 2; done; sleep 8; }
granted() { adb shell dumpsys package app.tileshell | tr -d '\r' | grep -E "android.permission.$1: granted=true" | head -1 | grep -c . ; }
grants() { local p o=""; for p in CAMERA READ_MEDIA_IMAGES READ_MEDIA_VIDEO RECORD_AUDIO; do o="$o$p=$(granted $p) "; done; printf '%s' "${o% }"; }

video_row_begin EDGE_LIVENESS "edge: reboot leaves the seeds, the grants, the sealed key and the observer intact"
D="$ROW_DIR"
ensure_start

log "--- before"
key_enter "$D/before"
assert_contains "before: the sealed store names tmdb" "tmdb" "$(cred_names)"
P0="$(slot_of PHOTOS)"; C0="$(slot_of CAMERA)"; G0="$(grants)"
assert_eq "before: slots.PHOTOS is the shell's" "$SHELL_PHOTOS" "$P0"
assert_eq "before: slots.CAMERA is the shell's" "$SHELL_CAMERA" "$C0"
record "before: the grants" "$G0"
record "before: uptime (s)" "$(adb shell cat /proc/uptime | cut -d. -f1)"

log "--- reboot"
rings_save
adb reboot; boot_poll
assert_eq "the device is awake after the reboot (C-25)" "Awake" "$(wake_device)"
UP="$(adb shell cat /proc/uptime | cut -d. -f1)"; record "after: uptime (s)" "$UP"
if [ "${UP:-99999}" -lt 600 ]; then _verdict PASS "the device did reboot (uptime under 600 s)" "$UP"; else _verdict FAIL "the device did reboot (uptime under 600 s)" "$UP"; fi
MARK=0
ensure_start; sleep 3

log "--- after"
assert_eq "after: slots.PHOTOS unchanged" "$P0" "$(slot_of PHOTOS)"
assert_eq "after: slots.CAMERA unchanged" "$C0" "$(slot_of CAMERA)"
SEED="$(ring_since "$MARK" launcher)"; printf '%s\n' "$SEED" > "$D/after-launcher-slice.txt"
for m in slot:photos:v1 slot:camera:v1; do
  assert_contains "after: assignSlotOnce $m -> already run" "-> already run" "$(printf '%s\n' "$SEED" | grep -F "assignSlotOnce $m " | tail -1)"
done
absent_in "after: no assignSlotOnce … -> assigned line" "-> assigned" "$SEED"
assert_eq "after: the grants are the ones held before" "$G0" "$(grants)"
assert_contains "after: the new process wrote its [net] lines" "[net] cleartext permitted for api.themoviedb.org: false" "$SEED"

assert_contains "after: the sealed store still names tmdb" "tmdb" "$(cred_names)"
VMARK="$(ring_mark)"
hub settings 2; dump_ui "$D/after-settings.xml"; tap_node "$D/after-settings.xml" hub_settings:tmdbkey; sleep 1.5
dump_ui "$D/after-key.xml"
assert_eq "after: the TMDB key setting still says a key is saved" "A key is saved." "$(node_text "$D/after-key.xml" tmdb_key_status)"
VS="$(vring "$VMARK")"; printf '%s\n' "$VS" > "$D/after-video-slice.txt"
absent_in "after: no [cred] tmdb: unreadable line (the Keystore key opens the entry after a reboot)" "[cred] tmdb: unreadable" "$VS"
absent_in "after: no [video] catalogue: no TMDB key saved line" "no TMDB key saved" "$VS"
record "not run across a reboot" "the catalogue cache (E20's offline leg reads it after a force-stop) and the media server's token (the same sealed store and Keystore key as the TMDB key above; E22's persisted leg)"

log "--- the observer after the reboot"
ensure_start
OMARK="$(ring_mark)"
[ -f "$GEN/qa-line.png" ] || python3 "$P01S/make_photos.py" "$GEN" editor >/dev/null 2>&1
PIC="$GEN/qa-line.png"; [ -f "$PIC" ] || PIC=""
assert_ne "a generated picture exists on the host" "" "$PIC"
adb shell mkdir -p /sdcard/Pictures/QA-Live >/dev/null
adb push "$PIC" /sdcard/Pictures/QA-Live/qa-live.png >/dev/null 2>&1
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/QA-Live/qa-live.png >/dev/null 2>&1
adb shell content call --uri content://media --method scan_file --arg /sdcard/Pictures/QA-Live/qa-live.png >/dev/null 2>&1
sleep 4
OS="$(ring_since "$OMARK" launcher)"; printf '%s\n' "$OS" > "$D/observer-slice.txt"
assert_contains "the launcher's observer fires for a picture added after the reboot" "[photos] refresh (mediastore change)" "$OS"

log "--- restore"
adb shell rm -rf /sdcard/Pictures/QA-Live
adb shell content call --uri content://media --method scan_file --arg /sdcard/Pictures/QA-Live/qa-live.png >/dev/null 2>&1
adb shell content delete --uri content://media/external/images/media --where "_display_name=\'qa-live.png\'" >/dev/null 2>&1
assert_eq "restore: no qa-live.png row is left" "" "$(adb shell content query --uri content://media/external/images/media --projection _id --where "_display_name=\'qa-live.png\'" 2>/dev/null | tr -d '\r' | grep -o '_id=[0-9]*')"
key_remove_if_saved
assert_eq "restore: the key is removed" "" "$(cred_names)"
c6; ensure_start
leak_scan_row "$DUMMY_TOKEN"
row_end
