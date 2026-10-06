#!/usr/bin/env bash
# Phase 17 E9 — the capture-intent contract and its guards (T17-4, T17-6; r3 D1, V3; Q-17-2 (b); the Decisions lines of
# 2026-10-05 10:59 and 16:32: the guard has FOUR conditions), with the device legs the trust fixes owe
# (docs/plan/review/2026-10-05-phase17-trust-fixes.md, "Device legs these fixes owe", (a)–(g), as its "Round 3" section
# REPLACES them) under the ONE access rule (the Decisions line of 2026-10-05 19:09; media/UriAccess.kt):
# condition (d) is (d1) the app the result goes to may write the URI — its own provider, a write grant it holds, or
# MediaStore's own answer — AND (d2) the platform's launch answer says the app that STARTED the capture could write it.
# The guard's line as built (media/CaptureRequest.kt):
#   [camera] capture guard inputs: scheme=<s> authority=<a> startedForResult=<b> clipHoldsOutput=<b> writeGrantFlag=<b>
#            ownAuthority=<b> callerMayWrite=<(d) as a whole> recipientMayWrite=<(d1)> starterAtLaunch=<granted|denied|
#            threw X|not available|not asked>          ("not asked": (a)–(c) or (d1) had already refused)
#
#   start     media_up with no names (the census); `adb emu geo fix -122.08 37.42` for the whole row; RECORD_AUDIO
#             revoked for the row (the floor; the video legs); qa-capture and qa-capture-fwd installed (no -g).
#   R         RECORDED: what `cmd package query-activities -a …IMAGE_CAPTURE` and `… VIDEO_CAPTURE` list, and whether
#             app.tileshell/.camera.CaptureActivity is among them (the doc expects it; this device lists the
#             preinstalled camera only); asserted: the same query naming the shell resolves to CaptureActivity.
#   G   (f)   the GPS control FIRST: a capture from the Camera's own shutter (camera_shutter in CameraActivity) carries
#             GPSLatitude in its pulled DCIM/Camera file — so I1's no-GPS check, on the same build, can fail.
#   I1  (f)   qa-capture (it names the shell, setPackage) starts IMAGE_CAPTURE with a content:// EXTRA_OUTPUT at its own
#             cache and no ClipData of its own: top_activity = CaptureActivity; shutter, then Done (capture_accept) →
#             RESULT_OK, `exists=true size=<n>` for its own path; the file pulled (run-as on the debuggable fixture), its
#             md5 FIRST asserted equal to the logged one, then: a decodable JPEG with NO GPS tag (exif_read.py:
#             gps_tags=0; no exiftool on this host); the images count UNCHANGED; the guard's inputs show (a)–(d):
#             round 3's own-FileProvider leg — `recipientMayWrite=true starterAtLaunch=granted`, RESULT_OK.
#   T   (g)   truncation: the caller's output pre-filled with 3,000,000 bytes; after Done its size equals the capture's
#             (the shell's own `… -> the caller's output, <n> bytes`) and the file is a JPEG from its first byte to its end.
#   V1        the same with VIDEO_CAPTURE → RESULT_OK, an mp4 ffprobe decodes at that URI; the videos count unchanged.
#   I2        no EXTRA_OUTPUT, IMAGE_CAPTURE → RESULT_OK, the `data` Bitmap's width × height; the images count unchanged.
#   V2        no EXTRA_OUTPUT, VIDEO_CAPTURE → RESULT_OK, videos +1 in DCIM/Camera/, the returned URI read through its
#             grant (`exists=true size=<n>`).
#   B         Back on either capture form → RESULT_CANCELED and no is_pending row.
#   F         a file:// output → RESULT_CANCELED at once, `[camera] refused output scheme=file`, `exists=false`.
#   S   (d)   EXTRA_OUTPUT as a String extra → RESULT_CANCELED, `[camera] capture request: EXTRA_OUTPUT is not a Uri`,
#             `[camera] refused output: no grant`, no camera opened.
#   M   (a)   the caller's OWN MediaStore row as EXTRA_OUTPUT (its ClipData, the write flag): REFUSED since round 3 (the
#             platform limit: the launch answer is "denied" for a MediaStore item unless the starter holds a grant) —
#             `… callerMayWrite=false recipientMayWrite=true starterAtLaunch=denied`, `refused output: no grant`,
#             RESULT_CANCELED, no camera opened, the row left empty.
#   W   (b)   a URI the caller holds only a write grant for: a MediaStore row the SHELL USER (adb) inserts and grants
#             with `am start --grant-write-uri-permission` — the grantor is another uid, which is what "a second app
#             grants it" asks. What the rule says (UriAccessRules.captureMayWrite, and the fixes file's "unless the
#             starter holds a grant"): (d1) passes — the receiver holds a write grant — and (d2), the launch answer for
#             the starter, which is that same app holding that same grant, is PREDICTED `granted`: accepted, RESULT_OK,
#             size > 0. Asserted as predicted; a device that disagrees fails the leg and is reported.
#   N1  (c)   the display_photo negative: a fixture contact inserted (removed at the end); qa-capture, holding no
#             contacts permission, sets its OWN ClipData with NO grant flag → RESULT_CANCELED, `[camera] refused output:
#             no grant`, photo_file_id unchanged.
#   N2  (c)   the same WITH FLAG_GRANT_WRITE_URI_PERMISSION → whether the start threw on the sender is RECORDED; where it
#             did not: RESULT_CANCELED, the refusal line, photo_file_id unchanged, (a)–(c) holding and (d) refusing.
#   N3  (c)   N2 again with WRITE_CONTACTS granted to the caller (`pm grant`, revoked after): REFUSED since round 3 — a
#             permission-wide access with no grant is never enough: `recipientMayWrite=false`, the refusal line,
#             RESULT_CANCELED, no camera opened, the photo unchanged.
#   X   (e)   a forwarded result, as round 3 words it. The fixtures as they are: V = qa-capture-fwd (the receiver; it
#             declares NO permission and always names its own provider URI), T = qa-capture (the go-between).
#             X contacts  NOT RUN, recorded: "receiver V holding WRITE_CONTACTS, a contact's display_photo" needs a V
#                         that can hold WRITE_CONTACTS and can name a URI the row gives it; qa-capture-fwd can do
#                         neither, and this row's writer may not change a fixture app.
#             X own       V's OWN FileProvider URI (V starts T for a result with it and a write grant — without one T's
#                         start would throw; T forwards with FLAG_ACTIVITY_FORWARD_RESULT): T's start does not throw;
#                         `recipientMayWrite=true starterAtLaunch=denied`, refused, RESULT_CANCELED, no camera opened,
#                         nothing in V's file — asserted as the fixes file words it.
#             X share     the same with setShareIdentityEnabled(true): the platform names the starter and it is not the
#                         receiver → refused, `[camera] capture request forwarded: started by <T>, result to <V>`.
#   restore   media_down (removes the control's row and V2's video); the fixtures' MediaStore rows and the contact
#             removed; both fixture apps uninstalled; RECORD_AUDIO granted back; Start.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/cam17.sh"
CAPTURE_SHORT="app.tileshell/.camera.CaptureActivity"

on_capture_page() { # tag
  cgdump "$D/$1-vf.xml"
  assert_eq "$1: top_activity is the capture page" "$CAPTURE_ACTIVITY" "$(top_activity)"
}
shoot_done() { # tag
  on_capture_page "$1"
  tap_node "$D/$1-vf.xml" camera_shutter; sleep 3
  cgdump "$D/$1-review.xml"; screencap "$D/$1-review.png"
  assert_eq "$1: the accept / retake page after the shutter" "yes" "$(has_node "$D/$1-review.xml" capture_review)"
  tap_node "$D/$1-review.xml" capture_accept
}
record_done() { # tag
  on_capture_page "$1"
  tap_node "$D/$1-vf.xml" camera_record; sleep 3.5
  tap_node "$D/$1-vf.xml" camera_record; sleep 4
  cgdump "$D/$1-review.xml"
  assert_eq "$1: the accept / retake page after the take" "yes" "$(has_node "$D/$1-review.xml" capture_review)"
  tap_node "$D/$1-review.xml" capture_accept
}
# One leg from a stopped fixture: T0 (logcat) and MARK (the ring) are taken immediately before the start.
begin_leg() { # leg [extra am args]
  adb shell am force-stop "$QAC" >/dev/null 2>&1
  T0="$(qa_time)"; MARK="$(ring_mark)"
  start_leg "$@"
}
end_leg() { # leg tag [seconds] -> L (the fixture's lines), S (the :camera slice), INPUTS (the guard's inputs line)
  L="$(leg_lines "$T0" "$1" "${3:-15}")"; printf '%s\n' "$L" > "$D/$2-fixture.txt"; printf '%s\n' "$L" | sed 's/^/      /' >> "$LOG"
  sleep 1
  S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/$2-slice.txt"
  INPUTS="$(printf '%s\n' "$S" | grep -o 'capture guard inputs: .*' | head -1 | sed 's/ *wall=.*//')"
}
decision_of() { printf '%s\n' "$S" | grep -o 'capture request [a-z]* from [^:]*: [a-z ]*' | head -1; }
photo_id() { q "content query --uri content://com.android.contacts/contacts --projection _id:photo_file_id" | grep -E "_id=$CID, " | sed -n 's/.*photo_file_id=\(.*\)$/\1/p'; }
restore_all() {
  adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
  adb uninstall app.tileshell.testclient.qacapture >/dev/null 2>&1
  adb uninstall app.tileshell.testclient.qacapturefwd >/dev/null 2>&1
}

cam_install E9
row_begin E9 "the capture answer: content / no output / Back / file://, the four-condition guard's legs, the GPS control"
cam_preamble
D="$ROW_DIR"
layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "start: layout_restore of the baseline" "0" "$?"
ensure_start
media_up
adb emu geo fix -122.08 37.42 > "$D/geo.out" 2>&1; record "adb emu geo fix -122.08 37.42" "$(tr '\n' ' ' < "$D/geo.out")"
record "the device's API level" "$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
rings_save
mic_off
trap restore_all EXIT
install_qac
install_qaf
record "the fixture's contacts permission at install (declared, never granted)" "$(adb shell dumpsys package "$QAC" | tr -d '\r' | grep -m1 'android.permission.WRITE_CONTACTS: granted' | xargs)"

# ----------------------------------------------------------------------------------------------- R: the resolver
log "--- R: who answers the capture actions (recorded)"
# The row's word is `record`, with the shell's activity "expected among them". On this device the unqualified query
# lists the preinstalled camera ONLY (run 1 of this driver, kept): since Android 11 the platform answers an implicit
# capture intent with preinstalled cameras alone — the very rule r3 V3 (a) re-cut the clause for — and it filters the
# shell user's query the same way. So the list is RECORDED with whether the expectation is met, and what IS asserted
# is the route the fixture takes: the same query naming the shell (`-p app.tileshell`) resolves to CaptureActivity.
for A in IMAGE_CAPTURE VIDEO_CAPTURE; do
  LIST="$(adb shell cmd package query-activities --brief -a "android.media.action.$A" | tr -d '\r' | grep / | xargs)"
  record "R: cmd package query-activities -a android.media.action.$A" "$LIST"
  case " $LIST " in *" $CAPTURE_SHORT "*) AMONG=yes ;; *) AMONG=no ;; esac
  record "R: $CAPTURE_SHORT among them, as the doc expects ($A)" "$AMONG"
  NAMED="$(adb shell cmd package query-activities --brief -a "android.media.action.$A" -p app.tileshell | tr -d '\r' | grep / | xargs)"
  assert_eq "R: the same query naming the shell resolves to the capture page ($A)" "$CAPTURE_SHORT" "$NAMED"
done

# ----------------------------------------------------------------------------------------------- G: the GPS control, FIRST
log "--- G (f): the GPS control first — the Camera's own shutter under adb emu geo fix"
record "G: the shell's location grant" "coarse=$(perm_granted ACCESS_COARSE_LOCATION) fine=$(perm_granted ACCESS_FINE_LOCATION)"
fresh_camera
sleep 4                                   # the fix reaches the viewfinder
cgdump "$D/G-vf.xml"
assert_eq "G: CameraActivity's own viewfinder" "$CAMERA_ACTIVITY yes" "$(top_activity) $(has_node "$D/G-vf.xml" camera_shutter)"
tap_node "$D/G-vf.xml" camera_shutter; sleep 4
ROWG="$(shell_rows images | head -1)"; record "G: the control's row" "$ROWG"
assert_eq "G: the control is a new DCIM/Camera row" "$((CENSUS_IMAGES + 1)) DCIM/Camera/" "$(media_count images) $(row_field "$ROWG" 7)"
pull_dcim "$(row_field "$ROWG" 2)" "$D/G-control.jpg"
assert_eq "G: the pulled file is the row's size" "$(row_field "$ROWG" 6)" "$(stat -c%s "$D/G-control.jpg")"
EXIFG="$(python3 "$CAM_DEV/exif_read.py" "$D/G-control.jpg" 2>&1)"; record "G: the control's EXIF" "$EXIFG"
assert_contains "G: the Camera's own shot carries GPSLatitude" "GPS:GPSLatitude=" "$EXIFG"
assert_absent "G: … its GPS block is not empty" "gps_tags=0" "$EXIFG"
IMG_BASE=$((CENSUS_IMAGES + 1))            # the images count from here on: the census and the control
c6

# ----------------------------------------------------------------------------------------------- I1: image, content output
log "--- I1 (f): IMAGE_CAPTURE with a content:// EXTRA_OUTPUT at the caller's own cache — and no GPS"
begin_leg image-content; sleep 2; record "I1: camera ready after (s)" "$(wait_camera "$MARK")"
sleep 4                                   # as long under the same fix as the control had
shoot_done I1
end_leg image-content I1
assert_contains "I1: the request named the shell and did not throw on the sender" "start threw=none" "$L"
assert_contains "I1: RESULT_OK" "result=RESULT_OK" "$L"
assert_contains "I1: exists=true for the caller's own output path" "output exists=true" "$L"
SIZE="$(printf '%s\n' "$L" | grep -o 'output exists=true size=[0-9]*' | head -1 | sed 's/.*size=//')"; MD5="$(printf '%s\n' "$L" | grep -o 'md5=[0-9a-f]*' | head -1 | cut -d= -f2)"
record "I1: the fixture's size and md5" "$SIZE $MD5"
assert_ne "I1: size > 0" "0" "${SIZE:-0}"
adb exec-out run-as "$QAC" cat cache/out.jpg > "$D/I1-out.jpg"
assert_eq "I1: the pulled file's md5 = the logged one (an unread file cannot pass)" "$MD5" "$(md5sum "$D/I1-out.jpg" | cut -d' ' -f1)"
EXIF="$(python3 "$CAM_DEV/exif_read.py" "$D/I1-out.jpg" 2>&1)"; record "I1: the caller's JPEG (Python's read; no exiftool here)" "$EXIF"
assert_contains "I1: a decodable JPEG" "size=" "$EXIF"
assert_contains "I1: the capture for a caller carries no GPS tag" "gps_tags=0" "$EXIF"
assert_absent "I1: … no GPSLatitude" "GPS:GPSLatitude" "$EXIF"
assert_eq "I1: … and no GPS IFD pointer is left in the file's EXIF" "0" "$(python3 -c "
from PIL import Image
print(1 if 0x8825 in Image.open('$D/I1-out.jpg').getexif() else 0)")"
assert_eq "I1: the images count is UNCHANGED (no DCIM copy)" "$IMG_BASE" "$(media_count images)"
assert_contains "I1: the guard accepted" "capture request image from $QAC: output accepted" "$S"
record "I1: what the guard weighed" "$INPUTS"
assert_contains "I1: (a)–(d) all hold for the caller's own provider — recipientMayWrite=true starterAtLaunch=granted" "capture guard inputs: scheme=content authority=$QAC.output startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false callerMayWrite=true recipientMayWrite=true starterAtLaunch=granted" "$INPUTS"
absent_in "I1: nothing saved to MediaStore" "[camera] saved " "$S"

# ----------------------------------------------------------------------------------------------- T: truncation
log "--- T (g): the caller's output pre-filled with a file larger than a capture"
begin_leg image-content --ez prefill true; sleep 2; wait_camera "$MARK" >/dev/null
shoot_done T
end_leg image-content T
PRE="$(printf '%s\n' "$L" | grep -o 'prefill size=[0-9]*' | cut -d= -f2)"; record "T: the output file before the request (bytes)" "$PRE"
assert_eq "T: the fixture pre-filled its output with 3,000,000 bytes" "3000000" "$PRE"
assert_contains "T: RESULT_OK" "result=RESULT_OK" "$L"
SIZE="$(printf '%s\n' "$L" | grep -o 'output exists=true size=[0-9]*' | head -1 | sed 's/.*size=//')"
WROTE="$(printf '%s\n' "$S" | grep -o "capture image -> the caller's output, [0-9]* bytes" | head -1 | grep -o '[0-9]* bytes' | cut -d' ' -f1)"
record "T: the capture's size as the shell wrote it / the file's size after" "${WROTE:-none} / ${SIZE:-none}"
assert_ne "T: the shell logged the bytes it wrote" "" "${WROTE:-}"
assert_eq "T: after Done the output's size equals the capture's (truncated, not overwritten in place)" "${WROTE:-x}" "${SIZE:-y}"
adb exec-out run-as "$QAC" cat cache/out.jpg > "$D/T-out.jpg"
assert_eq "T: the pulled file is that size" "${SIZE:-x}" "$(stat -c%s "$D/T-out.jpg")"
assert_eq "T: … and it ends with the JPEG's end marker (none of the pre-fill after it)" "ffd9" "$(tail -c 2 "$D/T-out.jpg" | od -An -tx1 | tr -d ' \n')"

# ----------------------------------------------------------------------------------------------- V1: video, content output
log "--- V1: VIDEO_CAPTURE with a content:// EXTRA_OUTPUT (RECORD_AUDIO revoked)"
begin_leg video-content; sleep 2; wait_camera "$MARK" >/dev/null
record_done V1
end_leg video-content V1 20
assert_contains "V1: RESULT_OK" "result=RESULT_OK" "$L"
assert_contains "V1: exists=true for the caller's own output path" "output exists=true" "$L"
MD5="$(printf '%s\n' "$L" | grep -o 'md5=[0-9a-f]*' | head -1 | cut -d= -f2)"
adb exec-out run-as "$QAC" cat cache/out.mp4 > "$D/V1-out.mp4"
assert_eq "V1: the pulled file's md5 = the logged one" "$MD5" "$(md5sum "$D/V1-out.mp4" | cut -d' ' -f1)"
assert_eq "V1: ffprobe decodes the mp4 (one video stream, no audio)" "1 video" "$(streams "$D/V1-out.mp4")"
record "V1: ffprobe" "$(stream_facts "$D/V1-out.mp4")"
assert_eq "V1: the videos count is UNCHANGED (no DCIM copy)" "$CENSUS_VIDEO" "$(media_count video)"
assert_contains "V1: the guard accepted" "capture request video from $QAC: output accepted" "$S"
assert_contains "V1: silent, and said so" "[camera] video sound: off (no microphone permission)" "$S"

# ----------------------------------------------------------------------------------------------- I2: image, no output
log "--- I2: IMAGE_CAPTURE with no EXTRA_OUTPUT"
begin_leg image-none; sleep 2; wait_camera "$MARK" >/dev/null
shoot_done I2
end_leg image-none I2
assert_contains "I2: RESULT_OK" "result=RESULT_OK" "$L"
BITMAP="$(printf '%s\n' "$L" | grep -o 'data bitmap=[0-9x]*' | cut -d= -f2)"; record "I2: the data Bitmap's width × height" "$BITMAP"
assert_ne "I2: a Bitmap came back in data" "" "$BITMAP"
assert_contains "I2: no URI came back" "returned uri=none" "$L"
assert_eq "I2: the images count is unchanged (no file)" "$IMG_BASE" "$(media_count images)"
absent_in "I2: nothing saved to MediaStore" "[camera] saved " "$S"

# ----------------------------------------------------------------------------------------------- V2: video, no output
log "--- V2: VIDEO_CAPTURE with no EXTRA_OUTPUT"
begin_leg video-none; sleep 2; wait_camera "$MARK" >/dev/null
record_done V2
end_leg video-none V2 20
assert_contains "V2: RESULT_OK" "result=RESULT_OK" "$L"
RLINE="$(printf '%s\n' "$L" | grep -F 'returned uri=' | head -1)"; RET="$(echo "$RLINE" | grep -o 'returned uri=[^ ]*' | cut -d= -f2)"
record "V2: the fixture's line for the returned URI" "$RLINE"
assert_eq "V2: the videos count +1" "$((CENSUS_VIDEO + 1))" "$(media_count video)"
ROWV="$(shell_rows video | head -1)"; record "V2: the new row" "$ROWV"
assert_eq "V2: relative_path DCIM/Camera/, published" "DCIM/Camera/ 0" "$(row_field "$ROWV" 7) $(row_field "$ROWV" 5)"
assert_contains "V2: the returned URI is that row's" "/video/media/$(row_field "$ROWV" 1)" "$RET"
assert_contains "V2: exists=true read through the grant" "exists=true" "$RLINE"
RSIZE="$(echo "$RLINE" | grep -o ' size=[0-9]*' | cut -d= -f2)"
assert_eq "V2: size=<n> read through the grant = the row's size" "$(row_field "$ROWV" 6)" "$RSIZE"

# ----------------------------------------------------------------------------------------------- B: Back on either form
log "--- B: Back on either capture form"
for form in image-content video-content; do
  begin_leg "$form"; sleep 2; wait_camera "$MARK" >/dev/null
  on_capture_page "B-$form"
  adb shell input keyevent KEYCODE_BACK
  end_leg "$form" "B-$form"
  assert_contains "B ($form): RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
  assert_contains "B ($form): nothing written to the caller" "output exists=false" "$L"
  assert_eq "B ($form): no is_pending row of the shell's in MediaStore" "0" "$(pending_rows)"
done
assert_eq "B: the images count is unchanged" "$IMG_BASE" "$(media_count images)"
assert_eq "B: the videos count is still census + 1 (V2's)" "$((CENSUS_VIDEO + 1))" "$(media_count video)"

# ----------------------------------------------------------------------------------------------- F: file:// output
log "--- F: a file:// EXTRA_OUTPUT (the fixture's own StrictMode VmPolicy relaxed first)"
begin_leg image-file
end_leg image-file F 8
assert_contains "F: the sender did not throw" "start threw=none" "$L"
assert_contains "F: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
assert_contains "F: the fixture logs exists=false for that path" "output exists=false" "$L"
assert_contains "F: [camera] refused output scheme=file" "[camera] refused output scheme=file" "$S"
absent_in "F: at once — no camera was opened for it" "[camera] devices=" "$S"
record "F: the refusal's wall − MARK (ms)" "$(( $(wall_of "$(printf '%s\n' "$S" | grep -F 'refused output scheme=file' | head -1)") - MARK ))"

# ----------------------------------------------------------------------------------------------- S: EXTRA_OUTPUT as a String
log "--- S (d): EXTRA_OUTPUT as a String extra"
begin_leg string-output
end_leg string-output S 8
record "S: the request as sent" "$(printf '%s\n' "$L" | grep -o 'start action=.*' | head -1)"
assert_contains "S: the sender did not throw" "start threw=none" "$L"
assert_contains "S: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
assert_contains "S: [camera] capture request: EXTRA_OUTPUT is not a Uri" "[camera] capture request: EXTRA_OUTPUT is not a Uri" "$S"
assert_contains "S: [camera] refused output: no grant" "[camera] refused output: no grant" "$S"
absent_in "S: no camera was opened" "[camera] devices=" "$S"
assert_contains "S: nothing at the caller's path" "output exists=false" "$L"
assert_eq "S: the images count is unchanged (never the no-output contract by accident)" "$IMG_BASE" "$(media_count images)"

# ----------------------------------------------------------------------------------------------- M: the caller's own MediaStore row
log "--- M (a): the caller's OWN MediaStore row as EXTRA_OUTPUT (round 3: refused — the platform limit)"
begin_leg own-media; sleep 3
MURI="$(qa_log "$T0" | grep -o 'media inserted uri=[^ ]*' | head -1 | cut -d= -f2)"; record "M: the row the fixture inserted" "${MURI:-none}"
assert_contains "M: the fixture inserted its own MediaStore image row" "content://media/" "${MURI:-}"
TOPM="$(top_activity)"; record "M: what was in front 3 s after the start" "$TOPM"
[ "$TOPM" = "$CAPTURE_ACTIVITY" ] && { screencap "$D/M-capture-page.png"; adb shell input keyevent KEYCODE_BACK; }   # never Done
end_leg own-media M 10
record "M: the request as sent" "$(printf '%s\n' "$L" | grep -o 'start action=.*' | head -1)"
record "M: startActivityForResult on the sender threw" "$(printf '%s\n' "$L" | grep -o 'start threw=[A-Za-z]*' | cut -d= -f2)"
record "M: what the guard weighed" "$INPUTS"
record "M: the decision" "$(decision_of)"
assert_contains "M: the sender's start did not throw" "start threw=none" "$L"
assert_contains "M: capture guard inputs: … starterAtLaunch=denied" "starterAtLaunch=denied" "$INPUTS"
assert_contains "M: (a)–(c) and (d1) hold — MediaStore says the row's owner may write it — and (d2), the launch answer, refuses" "capture guard inputs: scheme=content authority=media startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false callerMayWrite=false recipientMayWrite=true starterAtLaunch=denied" "$INPUTS"
assert_contains "M: refused" "capture request image from $QAC: refused" "$S"
assert_contains "M: [camera] refused output: no grant" "[camera] refused output: no grant" "$S"
absent_in "M: no camera was opened for it" "[camera] devices=" "$S"
assert_contains "M: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
MSIZE="$(printf '%s\n' "$L" | grep -o 'media uri=[^ ]* size=[0-9]*' | head -1 | sed 's/.*size=//')"; record "M: the row's bytes, read by its owner after the result" "${MSIZE:-none}"
assert_eq "M: nothing was written into the row (0 bytes)" "0" "${MSIZE:-none}"
assert_ne "M: the capture page is not left on top" "$CAPTURE_ACTIVITY" "$(top_activity)"
[ -n "${MURI:-}" ] && q "content delete --uri $MURI" >/dev/null
adb shell rmdir /sdcard/Pictures/QaCapture >/dev/null 2>&1
assert_eq "M: restore — the fixture's row is removed (the images count is back)" "$IMG_BASE" "$(media_count images)"

# ----------------------------------------------------------------------------------------------- W: a grant-only URI
# What the rule says for this leg (media/UriAccess.kt, captureMayWrite): (d1) the receiver holds an explicit write grant
# for the URI -> recipientMayWrite=true; (d2) the launch answer is asked for the STARTER, which here is that same app,
# holding that same grant — and the fixes file's platform limit reads "denied for any MediaStore item UNLESS the
# starter holds a grant". Predicted: starterAtLaunch=granted, accepted. Asserted as predicted.
log "--- W (b): a URI the caller holds only a write grant for (granted by the shell user's own start)"
q "content insert --uri content://media/external/images/media --bind _display_name:s:qa-grant-only.jpg --bind mime_type:s:image/jpeg --bind relative_path:s:Pictures/QaCapture/" > "$D/W-insert.out"
WID="$(media_id images qa-grant-only.jpg Pictures/QaCapture/)"; WURI="content://media/external/images/media/$WID"
record "W: the row the shell user inserted (owner: $(q "content query --uri $WURI --projection owner_package_name" | sed -n 's/.*owner_package_name=//p'))" "$WURI"
assert_ne "W: the shell user's row exists" "" "$WID"
adb shell am force-stop "$QAC" >/dev/null 2>&1
T0="$(qa_time)"; MARK="$(ring_mark)"
adb shell am start -n "$QAC/.CaptureProbeActivity" -d "$WURI" --grant-write-uri-permission --grant-read-uri-permission --es leg grant-only > "$D/W-start.out" 2>&1
record "W: am start with the grant flags answered" "$(tr '\n' ' ' < "$D/W-start.out" | cut -c1-200)"
sleep 3
if [ "$(top_activity)" = "$CAPTURE_ACTIVITY" ]; then
  wait_camera "$MARK" >/dev/null
  cgdump "$D/W-vf.xml"; tap_node "$D/W-vf.xml" camera_shutter; sleep 3
  cgdump "$D/W-review.xml"; tap_node "$D/W-review.xml" capture_accept
fi
end_leg grant-only W
record "W: what the fixture was started with" "$(printf '%s\n' "$L" | grep -o 'started with data=.*' | head -1)"
record "W: the grants the platform holds for the fixture" "$(adb shell dumpsys activity permissions | tr -d '\r' | grep -A3 "$QAC" | grep -i 'media' | head -3 | xargs)"
record "W: startActivityForResult on the sender threw" "$(printf '%s\n' "$L" | grep -o 'start threw=[A-Za-z]*' | cut -d= -f2)"
record "W: what the guard weighed" "$INPUTS"
record "W: the decision" "$(decision_of)"
assert_contains "W: (d1) passes — the receiver holds a write grant (recipientMayWrite=true)" "recipientMayWrite=true" "$INPUTS"
assert_contains "W: (d2) as the rule predicts for a starter that holds the grant — starterAtLaunch=granted" "starterAtLaunch=granted" "$INPUTS"
assert_contains "W: the whole line" "capture guard inputs: scheme=content authority=media startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false callerMayWrite=true recipientMayWrite=true starterAtLaunch=granted" "$INPUTS"
assert_contains "W: the guard accepted" "capture request image from $QAC: output accepted" "$S"
assert_contains "W: RESULT_OK" "result=RESULT_OK" "$L"
WSIZE="$(q "content query --uri $WURI --projection _size" | sed -n 's/.*_size=\([0-9]*\).*/\1/p')"; record "W: the row's _size after" "${WSIZE:-none}"
if [ "${WSIZE:-0}" -gt 0 ] 2>/dev/null; then _verdict PASS "W: the capture was written into the granted row (size > 0)" "$WSIZE"; else _verdict FAIL "W: the capture was written into the granted row (size > 0)" "${WSIZE:-none}"; fi
q "content delete --uri $WURI" >/dev/null
adb shell rmdir /sdcard/Pictures/QaCapture >/dev/null 2>&1
assert_eq "W: restore — the row is removed (the images count is back)" "$IMG_BASE" "$(media_count images)"

# ----------------------------------------------------------------------------------------------- N: display_photo
log "--- N (c): the display_photo negatives"
record "N: the shell holds WRITE_CONTACTS (it could write the photo itself)" "$(perm_granted WRITE_CONTACTS)"
q "content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n:" > "$D/N-insert.out"
RAW="$(q "content query --uri content://com.android.contacts/raw_contacts --projection _id:contact_id --sort '_id DESC'" | head -1)"
RID="$(echo "$RAW" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p')"; CID="$(echo "$RAW" | sed -n 's/.*contact_id=\([0-9]*\).*/\1/p')"
q "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$RID --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:QaCaptureFixture" >/dev/null
record "N: the fixture raw contact / contact" "$RID / $CID"
assert_ne "N: the fixture contact was inserted" "" "$RID"
TARGET="content://com.android.contacts/raw_contacts/$RID/display_photo"
PHOTO0="$(photo_id)"; record "N: photo_file_id before (content query … contacts --projection _id:photo_file_id)" "$PHOTO0"
assert_ne "N: the query returned the contact's row" "" "$PHOTO0"
caller_contacts() { adb shell dumpsys package "$QAC" | tr -d '\r' | grep -m1 'android.permission.WRITE_CONTACTS: granted' | sed -E 's/.*granted=([a-z]+).*/\1/'; }
for T in N1 N2 N3; do
  case "$T" in N1) leg=clip-noflag ;; *) leg=clip-flag ;; esac
  if [ "$T" = N3 ]; then
    adb shell pm grant "$QAC" android.permission.WRITE_CONTACTS
    assert_eq "N3: the caller now holds WRITE_CONTACTS" "true" "$(caller_contacts)"
  else
    assert_ne "$T: the caller holds no contacts permission" "true" "$(caller_contacts)"
  fi
  begin_leg "$leg" --es uri "$TARGET"
  sleep 2; TOPN="$(top_activity)"
  [ "$TOPN" = "$CAPTURE_ACTIVITY" ] && { screencap "$D/$T-capture-page.png"; adb shell input keyevent KEYCODE_BACK; }   # never Done: nothing may be written
  end_leg "$leg" "$T" 10
  THREW="$(printf '%s\n' "$L" | grep -o 'start threw=[A-Za-z]*' | cut -d= -f2)"
  record "$T ($leg): startActivityForResult on the sender threw" "${THREW:-(no line)}"
  record "$T ($leg): the request as sent" "$(printf '%s\n' "$L" | grep -o 'start action=.*' | head -1)"
  record "$T ($leg): what the guard weighed" "$INPUTS"
  record "$T ($leg): the decision" "$(decision_of)"
  case "$T" in
    N1)
      assert_eq "N1: the start did not throw" "none" "$THREW"
      assert_contains "N1: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
      assert_contains "N1: [camera] refused output: no grant" "[camera] refused output: no grant" "$S"
      assert_contains "N1: the guard saw no write-grant flag" "writeGrantFlag=false" "$INPUTS"
      absent_in "N1: no camera was opened for it" "[camera] devices=" "$S" ;;
    N2)
      if [ "$THREW" = none ]; then
        assert_contains "N2: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
        assert_contains "N2: [camera] refused output: no grant" "[camera] refused output: no grant" "$S"
        assert_contains "N2: (a), (b), (c) hold — condition (d) alone refuses, at (d1)" "startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false callerMayWrite=false recipientMayWrite=false starterAtLaunch=not asked" "$INPUTS"
        absent_in "N2: no camera was opened for it" "[camera] devices=" "$S"
      else
        record "N2: the platform stopped the start on the sender; the :camera lines since" "$(printf '%s\n' "$S" | grep -F '[camera]' | sed 's/^[^[]*//' | tr '\n' ';')"
        assert_absent "N2: nothing was written by the shell" "the caller's output" "$S"
      fi ;;
    N3)
      record "N3: what was in front 2 s after the start" "$TOPN"
      assert_eq "N3: the start did not throw" "none" "$THREW"
      assert_contains "N3: recipientMayWrite=false — WRITE_CONTACTS with no grant is not enough" "recipientMayWrite=false" "$INPUTS"
      assert_contains "N3: (a), (b), (c) hold — condition (d) refuses at (d1), the launch answer never asked" "startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false callerMayWrite=false recipientMayWrite=false starterAtLaunch=not asked" "$INPUTS"
      assert_contains "N3: refused" "capture request image from $QAC: refused" "$S"
      assert_contains "N3: [camera] refused output: no grant" "[camera] refused output: no grant" "$S"
      absent_in "N3: no camera was opened for it" "[camera] devices=" "$S"
      assert_contains "N3: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
      assert_absent "N3: nothing was written" "the caller's output" "$S" ;;
  esac
  assert_eq "$T: the contact's photo_file_id is unchanged" "$PHOTO0" "$(photo_id)"
  assert_ne "$T: the capture page is not left on top" "$CAPTURE_ACTIVITY" "$(top_activity)"
done
adb shell pm revoke "$QAC" android.permission.WRITE_CONTACTS >/dev/null 2>&1
assert_eq "N: the images count is unchanged" "$IMG_BASE" "$(media_count images)"
q "content delete --uri 'content://com.android.contacts/raw_contacts/$RID?caller_is_syncadapter=true'" >/dev/null
assert_eq "N: restore — the fixture contact is removed" "" "$(q "content query --uri content://com.android.contacts/raw_contacts --projection _id --where '_id=$RID'" | grep -o '_id=[0-9]*')"

# ----------------------------------------------------------------------------------------------- X: a forwarded result
log "--- X (e): a forwarded result — V (qa-capture-fwd) starts the go-between T (qa-capture), T forwards the result"
record "X (contacts): go-between with no permission, receiver V holding WRITE_CONTACTS, a contact's display_photo" "NOT RUN: qa-capture-fwd (V) declares no permission, so pm cannot grant it WRITE_CONTACTS, and it names only its own provider URI; the leg needs a fixture change (a declared WRITE_CONTACTS and a URI extra in qa-capture-fwd), which this row's writer may not make"
record "X: pm grant $QAF WRITE_CONTACTS answers" "$(adb shell pm grant "$QAF" android.permission.WRITE_CONTACTS 2>&1 | tr -d '\r' | head -2 | tr '\n' ' ' | cut -c1-220)"
record "X: T ($QAC) holds WRITE_CONTACTS" "$(caller_contacts)"
for mode in own share; do
  adb shell am force-stop "$QAC" >/dev/null 2>&1; adb shell am force-stop "$QAF" >/dev/null 2>&1
  T0="$(qa_time)"; MARK="$(ring_mark)"
  if [ "$mode" = share ]; then adb shell am start -n "$QAF/.ForwardStartActivity" --ez share true >/dev/null; else adb shell am start -n "$QAF/.ForwardStartActivity" >/dev/null; fi
  sleep 3; TOPX="$(top_activity)"
  [ "$TOPX" = "$CAPTURE_ACTIVITY" ] && { screencap "$D/X-$mode-capture-page.png"; adb shell input keyevent KEYCODE_BACK; }
  LV="$(leg_lines "$T0" fwd-v 10)"; LT="$(qa_log "$T0" | grep -F 'leg=forward ')"
  printf '%s\n%s\n' "$LV" "$LT" > "$D/X-$mode-fixture.txt"; printf '%s\n%s\n' "$LV" "$LT" | sed 's/^/      /' >> "$LOG"
  sleep 1; S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/X-$mode-slice.txt"
  INPUTS="$(printf '%s\n' "$S" | grep -o 'capture guard inputs: .*' | head -1 | sed 's/ *wall=.*//')"
  record "X ($mode): V's start of the go-between threw" "$(printf '%s\n' "$LV" | grep -o 'start threw=[A-Za-z]*' | cut -d= -f2)"
  record "X ($mode): the go-between's forwarded start threw" "$(printf '%s\n' "$LT" | grep -o 'forward start threw=[A-Za-z]*' | cut -d= -f2)"
  record "X ($mode): who the go-between was started for" "$(printf '%s\n' "$LT" | grep -o 'forward for=[^ ]*' | head -1)"
  record "X ($mode): what the guard weighed" "${INPUTS:-(no guard inputs line)}"
  record "X ($mode): the uids" "T=$(adb shell cmd package list packages -U "$QAC" | tr -d '\r' | grep -x "package:$QAC uid:[0-9]*" | sed 's/.*uid://'), V=$(adb shell cmd package list packages -U "$QAF" | tr -d '\r' | sed -n "s/^package:$QAF uid://p")"
  record "X ($mode): the grants the platform holds for T on V's provider" "$(adb shell dumpsys activity permissions | tr -d '\r' | grep -B2 -A2 "qacapturefwd.output" | xargs | cut -c1-300)"
  record "X ($mode): the decision" "$(decision_of)"
  record "X ($mode): what was in front 3 s after the start" "$TOPX"
  record "X ($mode): V's result" "$(printf '%s\n' "$LV" | grep -o 'result=[A-Z_]*' | head -1) $(printf '%s\n' "$LV" | grep -o 'output exists=[a-z]* size=[0-9]*' | head -1)"
  assert_contains "X ($mode): V's start of the go-between did not throw" "start threw=none" "$LV"
  assert_contains "X ($mode): the go-between's forwarded start did not throw" "forward start threw=none" "$LT"
  assert_contains "X ($mode): the result goes to V — the capture page names V as the caller" "from $QAF: " "$(decision_of)"
  assert_contains "X ($mode): refused" "capture request image from $QAF: refused" "$S"
  assert_contains "X ($mode): [camera] refused output: no grant" "[camera] refused output: no grant" "$S"
  absent_in "X ($mode): no camera was opened" "[camera] devices=" "$S"
  assert_contains "X ($mode): V receives RESULT_CANCELED" "result=RESULT_CANCELED" "$LV"
  if [ "$mode" = own ]; then
    assert_contains "X (own): V owns the provider and the starter, T, could not write it at launch — recipientMayWrite=true starterAtLaunch=denied" "capture guard inputs: scheme=content authority=$QAF.output startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false callerMayWrite=false recipientMayWrite=true starterAtLaunch=denied" "$INPUTS"
    absent_in "X (own): the platform names no starter — no forwarded line" "capture request forwarded" "$S"
  else
    assert_contains "X (share): [camera] capture request forwarded: started by <T>, result to <V>" "[camera] capture request forwarded: started by $QAC, result to $QAF" "$S"
    assert_contains "X (share): refused before (d1) and (d2) are asked" "callerMayWrite=false recipientMayWrite=false starterAtLaunch=not asked" "$INPUTS"
  fi
  assert_contains "X ($mode): nothing was written to V's file (the row never presses Done)" "output exists=false" "$LV"
  assert_ne "X ($mode): the capture page is not left on top" "$CAPTURE_ACTIVITY" "$(top_activity)"
done

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
no_crash
assert_eq "restore: no pending row of the shell's" "0" "$(pending_rows)"
c6
uninstall_qac
uninstall_qaf
mic_on
trap - EXIT
ensure_start
media_down
row_end
