#!/usr/bin/env bash
# Photos dev proof C5 (build task 5, the editor; r3 D7): a sideways camera JPEG (800 x 600, EXIF Orientation 6, a capture
# time) enhanced and saved — the copy is a JPEG, upright (600 x 800) with EXIF Orientation 1, DateTimeOriginal and its
# offset copied, MediaStore's DATE_TAKEN equal to the original's, and the colours the doc's enhance matrix gives.
# Restores the media. Not the gate (E6 and the HEIC edge case are).
. "$(dirname "$0")/pdev.sh"
row_begin C5 "Photos editor: the JPEG copy — upright, EXIF, DATE_TAKEN"
media_census
perm_set READ_MEDIA_IMAGES true
media_push /sdcard/Pictures/QA-Album qa-exif.jpg
scan; sleep 2
EXIF="$(img_id Pictures/QA-Album/ qa-exif.jpg)"
record "fixture id" "$EXIF"
FILES="/sdcard/Pictures/QA-Album/qa-exif.jpg"
MD5_BEFORE="$(orig_md5 $FILES)"
adb shell am force-stop app.tileshell
# A sideways camera JPEG: the copy is upright, Orientation 1, the capture time copied (r3 D7).
ORIG_TAKEN="$(adb shell content query --uri "content://media/external/images/media/$EXIF" --projection datetaken | sed -n 's/.*datetaken=\([0-9]*\).*/\1/p' | tr -d '\r')"
open_editor "$EXIF"
screencap "$ROW_DIR/exif-editor.png"
etap edit_enhance; edit_save
assert_contains "jpeg: the op's line names a .jpg" ".jpg" "$(echo "$ESLICE" | grep -F '[photosapp] edit enhance -> ')"
assert_eq "jpeg: a JPEG copy, upright 600 x 800" "image/jpeg 600 800" "$(copy_field mime_type) $(copy_field width) $(copy_field height)"
assert_eq "jpeg: DATE_TAKEN is the original's" "$ORIG_TAKEN" "$(copy_field datetaken)"
JF="$(copy_pull exif)"
META="$(python3 - "$JF" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1])
ex = im.getexif()
print("%dx%d orientation=%s original=%s offset=%s" % (im.size[0], im.size[1], ex.get(0x0112), ex.get_ifd(0x8769).get(0x9003), ex.get_ifd(0x8769).get(0x9011)))
PY
)"
assert_eq "jpeg: the file is upright with Orientation 1 and the capture time" "600x800 orientation=1 original=2024:07:04 09:08:07 offset=-06:00" "$META"
record "jpeg: upright top (was blue) / bottom (was red) after enhance" "$(px "$JF" 300 200) / $(px "$JF" 300 600)"
assert_rgb "jpeg: the upright top half is the enhanced blue" "$(python3 "$EE" pixel enhance 40,90,220)" "$(px "$JF" 300 200)" 8
assert_rgb "jpeg: the upright bottom half is the enhanced red" "$(python3 "$EE" pixel enhance 220,40,40)" "$(px "$JF" 300 600)" 8
assert_eq "the original's md5 is unchanged" "$MD5_BEFORE" "$(orig_md5 $FILES)"
no_crash 1500
ring_save launcher; ring_save "$EDIT_RING"
adb shell am force-stop app.tileshell
media_clean
perm_restore
ensure_start
row_end
