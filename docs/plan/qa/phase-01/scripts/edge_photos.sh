#!/usr/bin/env bash
# Edge cases: the Photos tile (no images, a very large image, an image deleted while cycling, permission revoked, partial access).
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/edge_photos.txt; S=$2
scan() { adb shell content call --uri content://media/external --method scan_volume --arg external >/dev/null 2>&1; sleep 3; }
faces() { adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep '\[engine\] publish feed:photos' | tail -1 | sed 's/.*publish //'; }
photos_log() { adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep '\[photos\]' | tail -1 | sed 's/.*\[photos\] //'; }
echo "# Edge cases: photos $(date -Iseconds)" > "$LOG"
echo "start: $(faces) | $(photos_log)" >> "$LOG"
echo '## no images at all' >> "$LOG"
adb shell mkdir -p /sdcard/QAHidden
adb shell "mv /sdcard/DCIM/Camera/*.jpg /sdcard/QAHidden/"
scan; adb shell input keyevent KEYCODE_HOME; sleep 6
echo "with no images: $(faces) | $(photos_log)" >> "$LOG"
adb exec-out screencap -p > "$OUT/photos_none.png"
echo '## a very large image (8000x6000, 38 MB)' >> "$LOG"
adb push "$S/huge.jpg" /sdcard/DCIM/Camera/huge.jpg >/dev/null
scan; adb shell input keyevent KEYCODE_HOME; sleep 8
echo "with one very large image: $(faces) | $(photos_log)" >> "$LOG"
adb exec-out screencap -p > "$OUT/photos_huge.png"
echo "crashes since: $(adb logcat -d -b crash | grep -c 'app.tileshell')" >> "$LOG"
echo '## the QA photos back, then one deleted while the tile cycles' >> "$LOG"
adb shell "mv /sdcard/QAHidden/*.jpg /sdcard/DCIM/Camera/"
scan; adb shell input keyevent KEYCODE_HOME; sleep 6
echo "restored: $(faces)" >> "$LOG"
adb shell rm /sdcard/DCIM/Camera/qa_photo_3.jpg; scan; sleep 5
echo "after deleting one while cycling: $(faces) | $(photos_log); crashes $(adb logcat -d -b crash | grep -c 'app.tileshell')" >> "$LOG"
echo '## permission revoked' >> "$LOG"
adb shell pm revoke app.tileshell android.permission.READ_MEDIA_IMAGES
adb shell pm revoke app.tileshell android.permission.READ_MEDIA_VISUAL_USER_SELECTED
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 7
echo "with the permission revoked: $(faces) | $(photos_log)" >> "$LOG"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page CHECKLIST >/dev/null 2>&1; sleep 3
dump "$OUT/checklist_photos_revoked.xml"
echo "checklist photos row: $(grep -o 'checklist:photos:[a-z_]*' "$OUT/checklist_photos_revoked.xml" | head -1)" >> "$LOG"
echo '## partial access (READ_MEDIA_VISUAL_USER_SELECTED only)' >> "$LOG"
adb shell pm grant app.tileshell android.permission.READ_MEDIA_VISUAL_USER_SELECTED
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 6
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page CHECKLIST >/dev/null 2>&1; sleep 3
dump "$OUT/checklist_photos_partial.xml"
echo "checklist photos row: $(grep -o 'checklist:photos:[a-z_]*' "$OUT/checklist_photos_partial.xml" | head -1); tile: $(faces) | $(photos_log)" >> "$LOG"
echo '## restore' >> "$LOG"
adb shell pm grant app.tileshell android.permission.READ_MEDIA_IMAGES
adb shell rm /sdcard/DCIM/Camera/huge.jpg; scan
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 7
echo "restored: $(faces) | $(photos_log); photos on the device: $(adb shell ls /sdcard/DCIM/Camera | tr '\n' ' ')" >> "$LOG"
