#!/usr/bin/env bash
# Phase 17 E10 — Tess's "take a photo" (phase 03 E2 / E10 re-run with the new component; r3 V4: typed, no microphone).
#
#   A  unlocked   from the baseline (slots.CAMERA is the shell's Camera): Tess opened with the assist key, "take a photo"
#                 typed with type_request → top_activity = app.tileshell/.camera.CameraActivity, the reply "Opening the
#                 camera." in diagnostics (reply_since the MARK taken just before the typing), and the matcher's line
#                 names TakePhoto (phase 03 E2's row: the request, its reply, its real effect); then c6 + ensure_start.
#   K  keyguard   the keyguard set up as qa/phase-03/scripts/e10.sh:51-55 does (locksettings set-disabled false, set-pin
#                 1234, get-disabled asserted false), sleep + wake, Tess over the keyguard (phase 03 E10's setup),
#                 "take a photo" typed with type_request → the card cortana_card:unlock reading "Unlock to continue",
#                 no CameraActivity starts (no activity record of it, it is not the top activity, no "Opening the camera."
#                 reply), the keyguard still up.
#   restore       locksettings clear --old 1234 and locksettings set-disabled true, exactly as the row says (also from an
#                 EXIT trap, so an aborted run cannot leave a PIN on the shared device); awake, no keyguard, Start.
#
# Not asserted, and why:
#   * phase 03 E2's "the reply was audible" half (the RMS of a capture of the host's audio) — r3 V4 removed the audio
#     route from every row; the reply is read as text.
#   * C-25 says wake_device after KEYCODE_SLEEP. wake_device ends with `wm dismiss-keyguard`, which on a PIN keyguard
#     raises the bouncer over the lock screen this leg needs; the leg reads the same `mWakefulness` line wake_device
#     prints, without the dismiss, and asserts Awake before its next tap. wake_device itself runs after the restore.
#
# Changes on the device: the Start layout (the baseline, restored), the keyguard (a PIN for leg K, cleared). No wipe.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
OUT="$QA/E10"; mkdir -p "$OUT"
PIN=1234
pin_set=no
restored=no

slot_of() { layout_json | python3 -c 'import json, sys; print(json.load(sys.stdin).get("slots", {}).get(sys.argv[1], ""))' "$1"; }
keyguard() { adb shell dumpsys window | tr -d '\r' | grep -o 'isKeyguardShowing=[a-z]*' | head -1; }
wakefulness() { adb shell dumpsys power | grep -m1 'mWakefulness=' | tr -d '\r ' | sed 's/mWakefulness=//'; }
# How many activity records of the shell's Camera the system holds (0 = none started).
camera_records() { adb shell dumpsys activity activities | tr -d '\r' | grep -c 'ActivityRecord{[^}]*app\.tileshell/\.camera\.CameraActivity'; }
match_line() { ring_since "$1" | grep -F '[match]' | tail -1 | sed 's/^.*wall=[0-9]* //'; }
ntext() { # dump.xml resource-id — parsed as XML, unescaped
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    print(""); sys.exit(0)
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        print(n.get("text", "")); break
else:
    print("")
PY
}
open_tess() { # out.xml — the assist key, then phase 03 E10's fallback when the session did not come up
  cortana_assist; sleep 5
  dump_ui "$1"
  if [ "$(has_node "$1" cortana_session)" != yes ]; then
    adb shell cmd voiceinteraction show >/dev/null 2>&1; sleep 5
    dump_ui "$1"
  fi
}
restore_keyguard() {
  [ "$restored" = yes ] && return 0
  restored=yes
  if [ "$pin_set" = yes ]; then adb shell locksettings clear --old $PIN > "$OUT/restore-clear.out" 2>&1 && pin_set=no; fi
  adb shell locksettings set-disabled true > "$OUT/restore-disabled.out" 2>&1
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  adb shell wm dismiss-keyguard >/dev/null 2>&1
}
trap restore_keyguard EXIT

take_device_lock
if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
  adb install -r "$APK" > "$OUT/install.out" 2>&1 || { echo "E10: adb install -r of $APK failed:" >&2; cat "$OUT/install.out" >&2; exit 4; }
fi
row_begin E10 "Tess: \"take a photo\" typed opens the shell's Camera; over the keyguard it shows Unlock to continue"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
assert_eq "wake" "Awake" "$(wake_device)"
layout_restore "$BASELINE" > "$OUT/baseline.out" 2>&1; assert_eq "layout_restore of the baseline" "0" "$?"
assert_eq "slots.CAMERA is the shell's Camera (Tess follows the CAMERA slot)" "app.tileshell/app.tileshell.camera.CameraActivity" "$(slot_of CAMERA)"
ensure_start

# ----------------------------------------------------------------------------------------------- A: unlocked
log "--- A: unlocked — \"take a photo\" typed"
assert_eq "A: no CameraActivity record before the request" "0" "$(camera_records)"
open_tess "$OUT/A-tess.xml"
assert_eq "A: Tess is open" "yes" "$(has_node "$OUT/A-tess.xml" cortana_session)"
MARK="$(ring_mark)"
type_request "take a photo" 8; assert_eq "A: type_request found the text box" "0" "$?"
TOP="$(top_activity)"
screencap "$OUT/A-after.png"
assert_eq "A: top_activity is the shell's Camera" "$CAMERA_ACTIVITY" "$TOP"
assert_ne "A: … and the system holds its activity record (the control for leg K's zero)" "0" "$(camera_records)"
ring_since "$MARK" > "$OUT/A-slice.txt"
assert_eq "A: the reply in diagnostics" "Opening the camera." "$(reply_since "$MARK")"
M="$(match_line "$MARK")"; note "A: matcher: $M"
assert_contains "A: the matcher read the typed text as TakePhoto" "TakePhoto" "$M"
c6; ensure_start

# ----------------------------------------------------------------------------------------------- K: over the keyguard
log "--- K: over the keyguard — \"take a photo\" typed"
adb shell locksettings set-disabled false > "$OUT/K-set-disabled.out" 2>&1
adb shell locksettings set-pin $PIN > "$OUT/K-set-pin.out" 2>&1 && pin_set=yes
restored=no
assert_eq "K: a PIN is set" "yes" "$pin_set"
assert_eq "K: locksettings get-disabled is false (there is a keyguard to test against)" "false" "$(adb shell locksettings get-disabled | tr -d '\r')"
adb shell input keyevent KEYCODE_SLEEP; sleep 2
adb shell input keyevent KEYCODE_WAKEUP; sleep 3
assert_eq "K: awake after the sleep and wake (C-25, read without wake_device's dismiss)" "Awake" "$(wakefulness)"
assert_eq "K: the keyguard is showing" "isKeyguardShowing=true" "$(keyguard)"
assert_eq "K: no CameraActivity record before the request (leg A's was force-stopped)" "0" "$(camera_records)"
open_tess "$OUT/K-tess.xml"
screencap "$OUT/K-tess.png"
assert_eq "K: Tess is open over the keyguard" "yes" "$(has_node "$OUT/K-tess.xml" cortana_session)"
assert_eq "K: … her text box is drawn over it" "yes" "$(has_node "$OUT/K-tess.xml" cortana_text_box_field)"
assert_eq "K: … and the keyguard is still showing" "isKeyguardShowing=true" "$(keyguard)"
MARK="$(ring_mark)"
type_request "take a photo" 6; assert_eq "K: type_request found the text box" "0" "$?"
dump_ui "$OUT/K-typed.xml"; screencap "$OUT/K-typed.png"
TOP="$(top_activity)"
ring_since "$MARK" > "$OUT/K-slice.txt"
assert_eq "K: the Unlock card shows (cortana_card:unlock)" "yes" "$(has_node "$OUT/K-typed.xml" cortana_card:unlock)"
assert_eq "K: … reading \"Unlock to continue\"" "Unlock to continue" "$(ntext "$OUT/K-typed.xml" cortana_card_title)"
M="$(match_line "$MARK")"; note "K: matcher: $M"
assert_contains "K: the request was understood (TakePhoto), then refused" "TakePhoto" "$M"
assert_eq "K: no CameraActivity started (no activity record)" "0" "$(camera_records)"
assert_ne "K: … and it is not the top activity" "$CAMERA_ACTIVITY" "$TOP"
note "K: top activity: $TOP"
absent_in "K: no \"Opening the camera.\" reply" "Opening the camera." "$(cat "$OUT/K-slice.txt")"
record "K: what Tess said instead" "$(reply_since "$MARK")"
assert_eq "K: the keyguard never came down" "isKeyguardShowing=true" "$(keyguard)"
cortana_close

# ----------------------------------------------------------------------------------------------- restore
log "--- restore: locksettings clear --old $PIN, locksettings set-disabled true"
rings_save
restore_keyguard
note "locksettings clear: $(tr -d '\r' < "$OUT/restore-clear.out" 2>/dev/null | tail -1)"
assert_eq "restore: the PIN is cleared" "no" "$pin_set"
assert_eq "restore: locksettings get-disabled is true again" "true" "$(adb shell locksettings get-disabled | tr -d '\r')"
assert_eq "restore: awake" "Awake" "$(wake_device)"
sleep 1
assert_eq "restore: no keyguard" "isKeyguardShowing=false" "$(keyguard)"
assert_eq "restore: no CameraActivity record left" "0" "$(camera_records)"
ensure_start
row_end
