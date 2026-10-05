#!/usr/bin/env bash
# Photos dev proof B3 (build task 5): Set as Start background (the copy under files/backgrounds/, Start's pixels, and
# still there with READ_MEDIA_IMAGES revoked — r3 D9), Set as lock screen (the lock wallpaper id changes; cleared again),
# File information, a row whose file is gone (placeholder tile, the viewer's error state), and ViewerActivity on a VIEW.
# Restores the theme's prefs, the wallpaper, the permissions and the media. Not the gate (E5 and the edge cases are).
. "$(dirname "$0")/pdev.sh"
row_begin B3 "Photos: set as, file information, a missing file, the VIEW helper"
media_census
perm_set READ_MEDIA_IMAGES true
push_six
set -- $IDS; ID0="$1"; ID1="$2"; ID2="$3"
adb shell am force-stop app.tileshell
adb exec-out run-as app.tileshell tar cf - shared_prefs > "$ROW_DIR/prefs-before.tar"
BG_BEFORE="$(adb shell run-as app.tileshell ls files/backgrounds 2>/dev/null | tr -d '\r' | xargs)"
lock_state() { adb shell dumpsys wallpaper | grep -A1 'Lock wallpaper state' | tail -1 | tr -d '\r' | sed 's/^ *//'; }
LOCK_BEFORE="$(lock_state)"
record "lock wallpaper before" "${LOCK_BEFORE:-none}"
photos_start; sleep 1
X="$ROW_DIR/collection.xml"; dump_ui "$X"
tap_node "$X" "photos_item:$ID0"; sleep 2
V="$ROW_DIR/viewer.xml"; dump_ui "$V"
# File information.
tap_node "$V" viewer_more; sleep 1; dump_ui "$V.menu"
tap_node "$V.menu" viewer_menu_info; sleep 1
I="$ROW_DIR/info.xml"; dump_ui "$I"; screencap "$ROW_DIR/info.png"
assert_eq "File information: the name" "qa-photo-0.png" "$(node_text "$I" viewer_info_name)"
assert_contains "File information: the size and dimensions" "640 x 480" "$(node_text "$I" viewer_info_size)"
assert_eq "File information: the folder" "DCIM/Camera" "$(node_text "$I" viewer_info_folder)"
assert_eq "the panel is the lower half of the screen (top at 1170 px)" "1170" "$(bounds "$I" viewer_info | cut -d' ' -f2)"
tap_node "$I" viewer_info_close; sleep 1
# Set as Start background.
dump_ui "$V"; tap_node "$V" viewer_more; sleep 1; dump_ui "$V.menu"
tap_node "$V.menu" viewer_menu_setas; sleep 1
S1="$ROW_DIR/setas.xml"; dump_ui "$S1"
assert_eq "Set as offers the Start background and the lock screen" "Start background Lock screen" "$(node_text "$S1" viewer_setas_background_text) $(node_text "$S1" viewer_setas_lock_text)"
MARK="$(ring_mark)"
tap_node "$S1" viewer_setas_background; sleep 3
assert_contains "the background's line" "[photosapp] set as background $ID0 -> files/backgrounds/$ID0.jpg" "$(ring_since "$MARK" launcher)"
assert_contains "the copy is under files/backgrounds/" "$ID0.jpg" "$(adb shell run-as app.tileshell ls files/backgrounds | tr -d '\r')"
adb shell input keyevent KEYCODE_BACK; sleep 1
ring_save launcher
adb shell am force-stop app.tileshell; ensure_start; sleep 3
screencap "$ROW_DIR/start-bg.png"
red_points() { python3 - "$1" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB")
n = sum(1 for x in range(4, 1080, 8) for y in range(200, 2100, 8) if all(abs(a - b) <= 4 for a, b in zip(im.getpixel((x, y)), (220, 40, 40))))
print(n)
PY
}
N1="$(red_points "$ROW_DIR/start-bg.png")"
record "Start's sampled points in the fixture's colour (220,40,40 +/- 4)" "$N1"
assert_ne "Start shows the picture where no tile is" "0" "$N1"
# The copy needs no photo grant (r3 D9).
adb shell pm revoke app.tileshell android.permission.READ_MEDIA_IMAGES
adb shell am force-stop app.tileshell; ensure_start; sleep 3
screencap "$ROW_DIR/start-bg-revoked.png"
assert_eq "with READ_MEDIA_IMAGES revoked Start's picture is unchanged" "$N1" "$(red_points "$ROW_DIR/start-bg-revoked.png")"
adb shell pm grant app.tileshell android.permission.READ_MEDIA_IMAGES
# Set as lock screen.
photos_start; sleep 1
dump_ui "$X"; tap_node "$X" "photos_item:$ID1"; sleep 2
dump_ui "$V"; tap_node "$V" viewer_more; sleep 1; dump_ui "$V.menu"
tap_node "$V.menu" viewer_menu_setas; sleep 1; dump_ui "$S1"
MARK="$(ring_mark)"
tap_node "$S1" viewer_setas_lock; sleep 4
assert_contains "the lock screen's line" "[photosapp] set as lock screen $ID1 -> wallpaper " "$(ring_since "$MARK" launcher)"
LOCK_AFTER="$(lock_state)"
record "lock wallpaper after" "${LOCK_AFTER:-none}"
assert_ne "the lock wallpaper id changed" "$LOCK_BEFORE" "$LOCK_AFTER"
adb shell input keyevent KEYCODE_BACK; sleep 1
# A row whose file is gone (rm without a scan): a placeholder tile, the viewer's error state, no crash.
ring_save launcher
adb shell am force-stop app.tileshell
# Through /sdcard MediaProvider sees the removal and drops the row at once (run 1), so the file is removed under it, as root.
adb root >/dev/null; sleep 2; adb wait-for-device
adb shell rm /data/media/0/DCIM/Camera/qa-photo-2.png
adb unroot >/dev/null; sleep 2; adb wait-for-device
assert_eq "the row is still in MediaStore after the removal" "$ID2" "$(img_id DCIM/Camera/ qa-photo-2.png)"
MARK="$(ring_mark)"
photos_start; sleep 2
dump_ui "$X.missing"
assert_eq "the row is still a tile" "yes" "$(has_node "$X.missing" "photos_item:$ID2")"
assert_eq "its tile is the placeholder" "yes" "$(has_node "$X.missing" "photos_item_missing:$ID2")"
assert_contains "the placeholder's line" "[photosapp] thumbnail $ID2: unreadable" "$(ring_since "$MARK" launcher)"
tap_node "$X.missing" "photos_item:$ID2"; sleep 2
dump_ui "$V.missing"
assert_eq "the viewer shows its error state" "This picture can't be shown." "$(node_text "$V.missing" viewer_error_text)"
assert_contains "the error's line" "[photosapp] viewer $ID2: cannot be shown" "$(ring_since "$MARK" launcher)"
assert_eq "Photos is still up" "app.tileshell/.photos.PhotosActivity" "$(top)"
adb shell input keyevent KEYCODE_BACK; sleep 1
# ViewerActivity: another app's VIEW on one image.
MARK="$(ring_mark)"
adb shell am start -a android.intent.action.VIEW -d "content://media/external/images/media/$ID1" -t image/png -n app.tileshell/.photos.ViewerActivity >/dev/null; sleep 3
assert_eq "ViewerActivity resumed" "app.tileshell/.photos.ViewerActivity" "$(top)"
screencap "$ROW_DIR/view.png"
assert_rgb "it shows the picture the intent names" "40,180,80" "$(px "$ROW_DIR/view.png" 540 1170)" 4
dump_ui "$ROW_DIR/view.xml"
assert_eq "a MediaStore image the shell reads by itself has its row actions" "yes yes" "$(has_node "$ROW_DIR/view.xml" viewer_edit) $(has_node "$ROW_DIR/view.xml" viewer_delete)"
adb shell input keyevent KEYCODE_BACK; sleep 2
no_crash 1200
ring_save launcher
# Restore: the theme's prefs, the background copies this run made, the lock wallpaper, the media, the permissions.
adb shell am force-stop app.tileshell
adb exec-in run-as app.tileshell tar xf - < "$ROW_DIR/prefs-before.tar"
for f in $(adb shell run-as app.tileshell ls files/backgrounds | tr -d '\r'); do case " $BG_BEFORE " in *" $f "*) ;; *) adb shell run-as app.tileshell rm "files/backgrounds/$f" ;; esac; done
assert_eq "restore: files/backgrounds as found" "$BG_BEFORE" "$(adb shell run-as app.tileshell ls files/backgrounds 2>/dev/null | tr -d '\r' | xargs)"
# IWallpaperManager.clearWallpaper(callingPackage, which = FLAG_LOCK, userId): transaction 15 on this image (API 36;
# read from the device's framework.jar with dexdump). `cmd wallpaper` has no clear.
record "wallpaper clear (service call)" "$(adb shell service call wallpaper 15 s16 com.android.shell i32 2 i32 0 | tr -d '\r')"
sleep 1
record "lock wallpaper at the end" "$(lock_state)"
assert_ne "restore: the lock wallpaper this run set is gone" "$LOCK_AFTER" "$(lock_state)"
media_clean
perm_restore
ensure_start; sleep 2
screencap "$ROW_DIR/start-restored.png"
assert_eq "restore: Start no longer shows the fixture" "0" "$(red_points "$ROW_DIR/start-restored.png")"
row_end
