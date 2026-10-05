#!/usr/bin/env bash
# Phase 17 E9 — the capture-intent contract and its guards (T17-4, T17-6; r3 D1, V3; Q-17-2 (b); the Decisions line of
# 2026-10-05 10:59: the guard's fourth condition (d), seen on the device — INDEX Change Log 2026-10-05 14:27 (1)).
#
#   start     media_up with no names (the census); `adb emu geo fix -122.08 37.42` for the whole row, so the no-GPS
#             check runs with a fix on the device; RECORD_AUDIO revoked for the row (the floor; the video legs).
#   R         RECORDED: what `cmd package query-activities -a …IMAGE_CAPTURE` and `… VIDEO_CAPTURE` list; asserted:
#             app.tileshell/.camera.CaptureActivity is among each.
#   I1        qa-capture (it names the shell, setPackage) starts IMAGE_CAPTURE with a content:// EXTRA_OUTPUT at its own
#             cache and no ClipData of its own: top_activity = CaptureActivity; shutter, then Done (capture_accept) →
#             RESULT_OK, `exists=true size=<n>` for its own path; the file pulled (run-as on the debuggable fixture), its
#             md5 FIRST asserted equal to the logged one, then: a decodable JPEG, no GPS EXIF (exif_read.py: gps_tags=0;
#             no exiftool on this host); the images count UNCHANGED. The guard's inputs line shows all four conditions.
#   V1        the same with VIDEO_CAPTURE → RESULT_OK, an mp4 ffprobe decodes at that URI; the videos count unchanged.
#   I2        no EXTRA_OUTPUT, IMAGE_CAPTURE → RESULT_OK, the fixture logs the `data` Bitmap's width × height; the images
#             count unchanged (no file).
#   V2        no EXTRA_OUTPUT, VIDEO_CAPTURE → RESULT_OK, the videos count +1 with relative_path DCIM/Camera/, and the
#             fixture logs the returned URI with `exists=true size=<n>` read through its grant.
#   B         Back on either capture form (the image form, the video form) → RESULT_CANCELED and no is_pending row.
#   F         the fixture's file:// output form → RESULT_CANCELED at once, `[camera] refused output scheme=file`, the
#             fixture logs `exists=false` for that path.
#   N1        the display_photo negative: a fixture contact inserted (removed at the end); its photo_file_id read with
#             the row's query; qa-capture sets its OWN ClipData with NO grant flag, EXTRA_OUTPUT at the contact's
#             display_photo URI → RESULT_CANCELED, `[camera] refused output: no grant`, photo_file_id unchanged.
#   N2        the leg the 10:59 Decisions line adds: its own ClipData WITH FLAG_GRANT_WRITE_URI_PERMISSION on that URI →
#             whether the start threw on the sender is RECORDED; where it did not: RESULT_CANCELED, `[camera] refused
#             output: no grant`, photo_file_id unchanged — and the guard's inputs show (a)–(c) holding and (d) refusing.
#   G         the GPS control: a capture from the Camera's own shutter (camera_shutter in CameraActivity) carries
#             GPSLatitude in its pulled DCIM/Camera file — so I1's no-GPS check can fail.
#   restore   media_down (removes the control's row and V2's video); the contact removed; qa-capture uninstalled;
#             RECORD_AUDIO granted back; Start.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/cam17.sh"
CAPTURE_SHORT="app.tileshell/.camera.CaptureActivity"

# The capture page on top, then: shutter + Done | record 3.5 s + Done | Back.
on_capture_page() { # leg
  cgdump "$D/$1-vf.xml"
  assert_eq "$1: top_activity is the capture page" "$CAPTURE_ACTIVITY" "$(top_activity)"
}
shoot_done() { # leg
  on_capture_page "$1"
  tap_node "$D/$1-vf.xml" camera_shutter; sleep 3
  cgdump "$D/$1-review.xml"; screencap "$D/$1-review.png"
  assert_eq "$1: the accept / retake page after the shutter" "yes" "$(has_node "$D/$1-review.xml" capture_review)"
  tap_node "$D/$1-review.xml" capture_accept
}
record_done() { # leg
  on_capture_page "$1"
  tap_node "$D/$1-vf.xml" camera_record; sleep 3.5
  tap_node "$D/$1-vf.xml" camera_record; sleep 4
  cgdump "$D/$1-review.xml"
  assert_eq "$1: the accept / retake page after the take" "yes" "$(has_node "$D/$1-review.xml" capture_review)"
  tap_node "$D/$1-review.xml" capture_accept
}
# One leg started from a fresh fixture: T0 (logcat) and MARK (the ring) are taken immediately before the start.
begin_leg() { # leg [extra am args]
  adb shell am force-stop "$QAC" >/dev/null 2>&1
  T0="$(qa_time)"; MARK="$(ring_mark)"
  start_leg "$@"
}
end_leg() { # leg tag [seconds] -> L (the fixture's lines), S (the :camera slice)
  L="$(leg_lines "$T0" "$1" "${3:-15}")"; printf '%s\n' "$L" > "$D/$2-fixture.txt"; printf '%s\n' "$L" | sed 's/^/      /' >> "$LOG"
  sleep 1
  S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/$2-slice.txt"
}
photo_id() { q "content query --uri content://com.android.contacts/contacts --projection _id:photo_file_id" | grep -E "_id=$CID, " | sed -n 's/.*photo_file_id=\(.*\)$/\1/p'; }

cam_install E9
row_begin E9 "the capture answer: content / no output / Back / file://, the display_photo negatives, the GPS control"
cam_preamble
D="$ROW_DIR"
layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "start: layout_restore of the baseline" "0" "$?"
ensure_start
media_up
adb emu geo fix -122.08 37.42 > "$D/geo.out" 2>&1; record "adb emu geo fix -122.08 37.42" "$(tr '\n' ' ' < "$D/geo.out")"
rings_save
mic_off
trap 'adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1; adb uninstall app.tileshell.testclient.qacapture >/dev/null 2>&1' EXIT
install_qac
record "the fixture's own permissions (it holds none of contacts)" "$(adb shell dumpsys package "$QAC" | tr -d '\r' | grep -c 'android.permission.WRITE_CONTACTS') WRITE_CONTACTS lines"

# ----------------------------------------------------------------------------------------------- R: the resolver
log "--- R: who answers the capture actions (recorded)"
for A in IMAGE_CAPTURE VIDEO_CAPTURE; do
  LIST="$(adb shell cmd package query-activities --brief -a "android.media.action.$A" | tr -d '\r' | grep / | xargs)"
  record "R: cmd package query-activities -a android.media.action.$A" "$LIST"
  assert_contains "R: $CAPTURE_SHORT is among them ($A)" " $CAPTURE_SHORT " " $LIST "
done

# ----------------------------------------------------------------------------------------------- I1: image, content output
log "--- I1: IMAGE_CAPTURE with a content:// EXTRA_OUTPUT at the caller's own cache"
begin_leg image-content; sleep 2; record "I1: camera ready after (s)" "$(wait_camera "$MARK")"
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
assert_contains "I1: the JPEG carries no GPS EXIF" "gps_tags=0" "$EXIF"
assert_absent "I1: … no GPSLatitude tag" "GPS:GPSLatitude" "$EXIF"
assert_eq "I1: the images count is UNCHANGED (no DCIM copy)" "$CENSUS_IMAGES" "$(media_count images)"
assert_contains "I1: the guard accepted" "capture request image from $QAC: output accepted" "$S"
INPUTS="$(printf '%s\n' "$S" | grep -o 'capture guard inputs: .*' | head -1 | sed 's/ *wall=.*//')"; record "I1: what the guard weighed" "$INPUTS"
assert_contains "I1: (a)–(d) all hold for the caller's own provider" "startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false callerMayWrite=true" "$INPUTS"
absent_in "I1: nothing saved to MediaStore" "[camera] saved " "$S"

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
assert_eq "I2: the images count is unchanged (no file)" "$CENSUS_IMAGES" "$(media_count images)"
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
assert_eq "B: the images count is still the census's" "$CENSUS_IMAGES" "$(media_count images)"
assert_eq "B: the videos count is still census + 1 (V2's)" "$((CENSUS_VIDEO + 1))" "$(media_count video)"

# ----------------------------------------------------------------------------------------------- F: file:// output
log "--- F: a file:// EXTRA_OUTPUT (the fixture's own StrictMode VmPolicy relaxed first)"
begin_leg image-file
T_F="$(device_ms)"
end_leg image-file F 8
record "F: ms from the start to the fixture's lines being read" "$(( $(device_ms) - T_F ))"
assert_contains "F: the sender did not throw" "start threw=none" "$L"
assert_contains "F: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
assert_contains "F: the fixture logs exists=false for that path" "output exists=false" "$L"
assert_contains "F: [camera] refused output scheme=file" "[camera] refused output scheme=file" "$S"
absent_in "F: at once — no camera was opened for it" "[camera] devices=" "$S"
REFUSE_WALL="$(wall_of "$(printf '%s\n' "$S" | grep -F 'refused output scheme=file' | head -1)")"
record "F: the refusal's wall − MARK (ms)" "$(( ${REFUSE_WALL:-0} - MARK ))"

# ----------------------------------------------------------------------------------------------- N: display_photo
log "--- N: the display_photo negatives"
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
for leg in clip-noflag clip-flag; do
  [ "$leg" = clip-noflag ] && T=N1 || T=N2
  begin_leg "$leg" --es uri "$TARGET"
  end_leg "$leg" "$T" 10
  THREW="$(printf '%s\n' "$L" | grep -o 'start threw=[A-Za-z]*' | cut -d= -f2)"
  record "$T ($leg): startActivityForResult on the sender threw" "${THREW:-(no line)}"
  record "$T ($leg): the request as sent" "$(printf '%s\n' "$L" | grep -o 'start action=.*' | head -1)"
  INPUTS="$(printf '%s\n' "$S" | grep -o 'capture guard inputs: .*' | head -1 | sed 's/ *wall=.*//')"; record "$T ($leg): what the guard weighed" "$INPUTS"
  if [ "$leg" = clip-noflag ]; then
    # The row's first form: asserted outright.
    assert_eq "$T: the start did not throw" "none" "$THREW"
    assert_contains "$T: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
    assert_contains "$T: [camera] refused output: no grant" "[camera] refused output: no grant" "$S"
    assert_contains "$T: the guard saw no write-grant flag" "writeGrantFlag=false" "$INPUTS"
  elif [ "$THREW" = none ]; then
    # The 10:59 leg, where the start succeeded (seen on this device at the build, Change Log (1)).
    assert_contains "$T: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
    assert_contains "$T: [camera] refused output: no grant" "[camera] refused output: no grant" "$S"
    assert_contains "$T: (a), (b), (c) hold — condition (d) alone refuses" "startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false callerMayWrite=false" "$INPUTS"
  else
    record "$T: the platform stopped the start on the sender; the :camera lines since" "$(printf '%s\n' "$S" | grep -F '[camera]' | sed 's/^[^[]*//' | tr '\n' ';')"
    assert_absent "$T: nothing was written by the shell" "the caller's output" "$S"
  fi
  [ "$THREW" = none ] && absent_in "$T: no camera was opened for it" "[camera] devices=" "$S"
  assert_eq "$T: the contact's photo_file_id is unchanged" "$PHOTO0" "$(photo_id)"
  assert_ne "$T: the capture page is not on top" "$CAPTURE_ACTIVITY" "$(top_activity)"
done
assert_eq "N: the images count is unchanged" "$CENSUS_IMAGES" "$(media_count images)"
q "content delete --uri 'content://com.android.contacts/raw_contacts/$RID?caller_is_syncadapter=true'" >/dev/null
assert_eq "N: restore — the fixture contact is removed" "" "$(q "content query --uri content://com.android.contacts/raw_contacts --projection _id --where '_id=$RID'" | grep -o '_id=[0-9]*')"

# ----------------------------------------------------------------------------------------------- G: the GPS control
log "--- G: the GPS control — the Camera's own shutter under adb emu geo fix"
adb shell am force-stop "$QAC" >/dev/null 2>&1
adb emu geo fix -122.08 37.42 >/dev/null 2>&1
record "G: the shell's location grant" "coarse=$(perm_granted ACCESS_COARSE_LOCATION) fine=$(perm_granted ACCESS_FINE_LOCATION)"
fresh_camera
sleep 4                                   # the fix reaches the viewfinder
cgdump "$D/G-vf.xml"
assert_eq "G: CameraActivity's own viewfinder" "$CAMERA_ACTIVITY yes" "$(top_activity) $(has_node "$D/G-vf.xml" camera_shutter)"
MARK="$(ring_mark)"
tap_node "$D/G-vf.xml" camera_shutter; sleep 4
ROWG="$(shell_rows images | head -1)"; record "G: the control's row" "$ROWG"
assert_eq "G: the control is a new DCIM/Camera row" "$((CENSUS_IMAGES + 1)) DCIM/Camera/" "$(media_count images) $(row_field "$ROWG" 7)"
pull_dcim "$(row_field "$ROWG" 2)" "$D/G-control.jpg"
assert_eq "G: the pulled file is the row's size" "$(row_field "$ROWG" 6)" "$(stat -c%s "$D/G-control.jpg")"
EXIFG="$(python3 "$CAM_DEV/exif_read.py" "$D/G-control.jpg" 2>&1)"; record "G: the control's EXIF" "$EXIFG"
assert_contains "G: the control carries GPSLatitude (so I1's no-GPS check can fail)" "GPS:GPSLatitude=" "$EXIFG"
assert_absent "G: … and its GPS block is not empty" "gps_tags=0" "$EXIFG"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
no_crash
assert_eq "restore: no pending row of the shell's" "0" "$(pending_rows)"
c6
uninstall_qac
mic_on
trap - EXIT
ensure_start
media_down
row_end
