#!/usr/bin/env bash
# Development proof, build task 6 (B, TRUST): the display_photo negatives. A fixture contact is inserted; qa-capture —
# which holds no permission — aims EXTRA_OUTPUT at its `display_photo` URI with its OWN ClipData, first with no grant
# flag and then WITH FLAG_GRANT_WRITE_URI_PERMISSION (the leg that shows what the platform does when the shell itself
# holds WRITE_CONTACTS). Expected where the start succeeds: RESULT_CANCELED, `[camera] refused output: no grant`, and
# the contact's photo unchanged. What the device does on the sender is RECORDED, not graded. Not the gate.
. "$(dirname "$0")/head.sh"
install_qac
row_begin B3 "capture answer: display_photo negatives"
COUNT0="$(count_rows $IMAGES)"
adb shell am force-stop app.tileshell; adb shell am force-stop $QAC
record "the shell holds WRITE_CONTACTS (so it could write the photo itself)" "$(granted WRITE_CONTACTS)"
RAW_URI="$(adb shell "content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n:" 2>&1 | tr -d '\r')"
RAW="$(adb shell "content query --uri content://com.android.contacts/raw_contacts --projection _id:contact_id --sort '_id DESC'" | head -1 | tr -d '\r')"
RID="$(echo "$RAW" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p')"; CID="$(echo "$RAW" | sed -n 's/.*contact_id=\([0-9]*\).*/\1/p')"
adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$RID --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:QaCaptureFixture" >/dev/null
record "fixture raw contact / contact" "$RID / $CID"
assert_ne "fixture contact inserted" "" "$RID"
TARGET="content://com.android.contacts/raw_contacts/$RID/display_photo"
photo_id() { adb shell "content query --uri content://com.android.contacts/contacts/$CID --projection photo_file_id" | tr -d '\r' | sed -n 's/.*photo_file_id=\(.*\)$/\1/p'; }
PHOTO0="$(photo_id)"; record "photo_file_id before" "$PHOTO0"

for LEG in clip-noflag clip-flag; do
  T0="$(qa_time)"; MARK="$(ring_mark)"
  start_leg $LEG --es uri "$TARGET"
  L="$(leg_lines "$T0" $LEG 10)"; echo "$L" | sed 's/^/      /' >> "$LOG"
  THREW="$(echo "$L" | grep -o 'start threw=[A-Za-z]*' | cut -d= -f2)"
  record "$LEG: startActivityForResult on the sender threw" "${THREW:-(no line)}"
  record "$LEG: the request as sent" "$(echo "$L" | grep -o 'start action=.*' | head -1)"
  S="$(cam_since "$MARK")"
  if [ "$THREW" = "none" ]; then
    assert_contains "$LEG: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
    assert_contains "$LEG: the guard's line" "[camera] refused output: no grant" "$S"
    assert_contains "$LEG: the request was refused before anything was shown" "capture request image from $QAC: refused" "$S"
    assert_absent "$LEG: no camera was opened" "[camera] devices=" "$S"
  else
    record "$LEG: the platform stopped the start on the sender; the shell's ring since the start" "$(echo "$S" | grep -F '[camera]' | sed 's/^[^[]*//' | tr '\n' ';')"
    assert_absent "$LEG: nothing was written by the shell" "the caller's output" "$S"
  fi
  assert_eq "$LEG: the contact's photo_file_id unchanged" "$PHOTO0" "$(photo_id)"
  assert_eq "$LEG: top activity is not the capture page" "no" "$( [ "$(top_activity)" = "app.tileshell/.camera.CaptureActivity" ] && echo yes || echo no )"
  adb shell am force-stop $QAC
done
assert_eq "images count unchanged" "$COUNT0" "$(count_rows $IMAGES)"

# --- restore: the fixture contact removed ---------------------------------------------------------------------------
adb shell "content delete --uri 'content://com.android.contacts/raw_contacts/$RID?caller_is_syncadapter=true'" >/dev/null
assert_eq "fixture contact removed" "" "$(adb shell "content query --uri content://com.android.contacts/raw_contacts --projection _id --where '_id=$RID'" | grep -o '_id=[0-9]*')"
assert_eq "no crash" "" "$(adb logcat -d -t 600 -s AndroidRuntime | grep -E 'app.tileshell' | head -3)"
ring_save "$CAM_RING"
adb shell am force-stop app.tileshell
adb uninstall $QAC >/dev/null
ensure_start
row_end
