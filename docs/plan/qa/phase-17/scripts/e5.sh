#!/usr/bin/env bash
# Phase 17 E5 — Photos actions, clause by clause.
#
#   share      Share on qa-photo-0 (DCIM/Camera) → top_activity is the system chooser (com.android.intentresolver;
#              r3 V10); `testapps/qa-capture` ("QA Capture") picked in it → its `TileShellQa` line reads action
#              android.intent.action.SEND, a content://media/… URI and the stream's md5 = the host md5 of the fixture.
#   delete     Delete → MediaProvider's consent dialog on top (the dump's package and `dumpsys window`'s focus name
#              com.android.providers.media.module); DENY first (the button tapped by its dump bounds) → the content
#              query count unchanged, the row still in the collection (photos_pivot:collection asserted first),
#              `[photosapp] delete <id>: refused by user` (T17-23); delete again, ALLOW → the count drops by one and the
#              row leaves the collection.
#   background Set as → Start background on qa-photo-1 → the launcher slice holds `[photosapp] set as background <id> ->
#              <file>` with the file under files/backgrounds/ (and there on disk); after c6 + ensure_start Start's
#              screencap at a tile-free point (outside every tile: node of the dump) equals the fixture colour
#              (40,180,80) ± 4 — the control: before the step NO tile-free point reads it; `pm revoke …
#              READ_MEDIA_IMAGES`, c6 + ensure_start → the same point's pixel unchanged (r3 D9); `pm grant` back.
#   lock       Set as → Lock screen on qa-photo-2 → `dumpsys wallpaper`'s lock wallpaper state changed; the row's
#              restore clears it (asserted back to the state found).
#   slideshow  from qa-photo-1's viewer: three consecutive `[photosapp] slideshow next <id>` lines whose wall= gaps are
#              5000 ± 100 ms (Y6) and whose ids follow the collection order (qa-photo-2, -3, -4), each followed by its
#              `[motion] slideshow_step …` with settle = 250 ± 17 ms and maxGapMs <= 33.4 (C-31); a screencap taken
#              after the second line reads that image's colour (qa-photo-3: 230,200,40) (T17-8, C-5).
#   restore    the shell's prefs (the tar taken at the start), files/backgrounds as found, the lock wallpaper cleared,
#              qa-capture uninstalled, media_down, the permission as found, Start without the picture.
#
# Changes on the device: media, one fixture APK (uninstalled), the shell's theme prefs and files/backgrounds, the lock
# wallpaper, READ_MEDIA_IMAGES — all restored and asserted. Force-stops the shell. No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_photos.sh"
QACAP_APK="$REPO/testapps/qa-capture/build/outputs/apk/debug/qa-capture-debug.apk"
QACAP_PKG="app.tileshell.testclient.qacapture"
GREEN="40,180,80"

photos_row_begin E5 "Photos actions: share, delete (deny / accept), set as background / lock screen, slideshow"
layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "layout_restore of the baseline" "0" "$?"
ensure_start
perm_set READ_MEDIA_IMAGES true
assert_eq "the qa-capture APK is built (./gradlew :testapps:qa-capture:assembleDebug --offline)" "yes" "$([ -f "$QACAP_APK" ] && echo yes || echo no)"
adb install -r "$QACAP_APK" > "$D/qacapture-install.out" 2>&1; assert_eq "qa-capture installed" "0" "$?"
# shellcheck disable=SC2086
media_up $SIX
six_ids
HOST_MD5="$(md5sum "$GEN/qa-photo-0.png" | cut -d' ' -f1)"
record "host md5 of qa-photo-0.png" "$HOST_MD5"
rings_save; adb shell am force-stop app.tileshell; sleep 1
prefs_backup "$D/prefs-before.tar"
BG_BEFORE="$(backgrounds)"; LOCK_BEFORE="$(lock_state)"
record "files/backgrounds before / lock wallpaper state before" "[${BG_BEFORE}] / [${LOCK_BEFORE:-none}]"
ensure_start; sleep 2
gdump "$D/start-before.xml" || true; screencap "$D/start-before.png"
assert_eq "Start's page" "yes" "$(has_node "$D/start-before.xml" start_page)"
P0="$(start_points "$D/start-before.png" "$D/start-before.xml" "$GREEN")"
assert_eq "(control) before the step no tile-free point of Start reads the fixture colour" "0 -" "$P0"

# ----------------------------------------------------------------------------------------------- share
log "--- share"
X="$D/collection.xml"
photos_cold "$X"
viewer_open "$X" "$ID0"
MARK="$(ring_mark)"; LT="$(adb shell date +'%m-%d\ %H:%M:%S.000' | tr -d '\r')"
tap_node "$D/viewer.xml" viewer_share; sleep 3
CH_TOP="$(top_activity)"
record "top_activity after Share" "$CH_TOP"
assert_contains "the system chooser is on top (com.android.intentresolver)" "com.android.intentresolver" "$CH_TOP"
assert_contains "Photos' share line" "[photosapp] share $ID0 -> chooser" "$(ring_since "$MARK")"
dump_ui "$D/chooser.xml"; screencap "$D/chooser.png"
QB="$(text_bounds "$D/chooser.xml" "QA Capture")"
if [ -z "$QB" ]; then adb shell input swipe 540 1900 540 900 400; sleep 1; dump_ui "$D/chooser.xml"; QB="$(text_bounds "$D/chooser.xml" "QA Capture")"; note "the chooser was swiped up to reach QA Capture"; fi
assert_ne "the chooser lists QA Capture" "" "$QB"
[ -n "$QB" ] && tap_bounds "$QB"
sleep 4
adb logcat -d -T "$LT" -s TileShellQa:I 2>/dev/null | tr -d '\r' > "$D/tileshellqa.txt"
SEND="$(grep -F 'send action=' "$D/tileshellqa.txt" | tail -1)"
record "qa-capture's line" "${SEND##*TileShellQa: }"
assert_contains "qa-capture: action android.intent.action.SEND" "send action=android.intent.action.SEND " "$SEND"
assert_contains "qa-capture: a content://media/… URI" " uri=content://media/" "$SEND"
assert_contains "qa-capture: … of this row (its id)" "/$ID0 " "$SEND"
assert_contains "qa-capture: the stream's md5 is the host md5 of the fixture" " md5=$HOST_MD5" "$SEND"
[ "$(top_activity)" = "$PHOTOS_ACTIVITY" ] || { adb shell input keyevent KEYCODE_BACK; sleep 2; }
assert_eq "after the share Photos is resumed" "$PHOTOS_ACTIVITY" "$(top_activity)"

# ----------------------------------------------------------------------------------------------- delete
log "--- delete: Deny first"
COUNT0="$(media_count images)"
dump_ui "$D/viewer.xml"
MARK="$(ring_mark)"
tap_node "$D/viewer.xml" viewer_delete; sleep 3
C="$D/consent.xml"; dump_ui "$C"; screencap "$D/consent.png"
adb shell dumpsys window | tr -d '\r' | grep -E 'mCurrentFocus|mFocusedApp' > "$D/consent-window.txt"
record "dumpsys window while the dialog is up" "$(xargs < "$D/consent-window.txt")"
assert_contains "MediaProvider's consent dialog is on top (the dump)" "com.android.providers.media.module" "$(cat "$C")"
assert_contains "… and dumpsys window's focus" "com.android.providers.media.module" "$(cat "$D/consent-window.txt")"
DENY_TEXT="$(node_text "$C" android:id/button2)"; DENY_B="$(bounds "$C" android:id/button2)"
record "the dialog's buttons (button2 / button1)" "$DENY_TEXT / $(node_text "$C" android:id/button1)"
assert_eq "the dialog's Deny button" "deny" "$(printf '%s' "$DENY_TEXT" | tr 'A-Z' 'a-z')"
tap_bounds "$DENY_B"; sleep 2
assert_eq "Deny: the content query count is unchanged" "$COUNT0" "$(media_count images)"
assert_contains "Deny: [photosapp] delete <id>: refused by user (T17-23)" "[photosapp] delete $ID0: refused by user" "$(ring_since "$MARK")"
adb shell input keyevent KEYCODE_BACK; sleep 2
dump_ui "$X.deny"
assert_eq "Deny: photos_pivot:collection is selected" "true" "$(selected "$X.deny" photos_pivot:collection)"
assert_eq "Deny: the row is still in the collection" "yes" "$(has_node "$X.deny" "photos_item:$ID0")"
log "--- delete: again, accepted"
viewer_open "$X.deny" "$ID0"
MARK="$(ring_mark)"
tap_node "$D/viewer.xml" viewer_delete; sleep 3
dump_ui "$C.2"
assert_contains "the consent dialog again" "com.android.providers.media.module" "$(cat "$C.2")"
tap_node "$C.2" android:id/button1; sleep 3
assert_eq "Accept: the count drops by one" "$((COUNT0 - 1))" "$(media_count images)"
assert_contains "Accept: Photos' line" "[photosapp] delete $ID0: deleted" "$(ring_since "$MARK")"
[ "$(top_activity)" = "$PHOTOS_ACTIVITY" ] || sleep 2
dump_ui "$D/after-delete.xml"
[ "$(has_node "$D/after-delete.xml" viewer)" = yes ] && { adb shell input keyevent KEYCODE_BACK; sleep 2; }
dump_ui "$X.gone"
assert_eq "Accept: photos_pivot:collection is selected" "true" "$(selected "$X.gone" photos_pivot:collection)"
assert_eq "Accept: the row has left the collection" "no" "$(has_node "$X.gone" "photos_item:$ID0")"
assert_eq "Accept: its neighbour is still there" "yes" "$(has_node "$X.gone" "photos_item:$ID1")"

# ----------------------------------------------------------------------------------------------- Start background
log "--- set as Start background"
viewer_open "$X.gone" "$ID1"
viewer_menu
tap_node "$D/menu.xml" viewer_menu_setas; sleep 1
dump_ui "$D/setas.xml"
MARK="$(ring_mark)"
tap_node "$D/setas.xml" viewer_setas_background; sleep 3
BGLINE="$(ring_since "$MARK" | grep -F "[photosapp] set as background $ID1 -> " | tail -1)"
record "the background's line" "${BGLINE##*\[photosapp\] }"
BGFILE="$(printf '%s\n' "$BGLINE" | sed -n 's/.* -> \([^ ]*\).*/\1/p')"
assert_contains "[photosapp] set as background <id> -> <file>, the file under files/backgrounds/" "files/backgrounds/" "$BGFILE"
assert_contains "the copy is on disk under files/backgrounds/" " $(basename "${BGFILE:-none}") " " $(backgrounds) "
c6; ensure_start; sleep 3
gdump "$D/start-bg.xml" || true; screencap "$D/start-bg.png"
assert_eq "Start's page" "yes" "$(has_node "$D/start-bg.xml" start_page)"
P1="$(start_points "$D/start-bg.png" "$D/start-bg.xml" "$GREEN")"
record "Start's tile-free points in the fixture's colour (count, the first x,y)" "$P1"
PT="${P1#* }"
assert_ne "Start's screencap at a tile-free point equals the fixture colour" "-" "$PT"
PX="${PT%,*}"; PY="${PT#*,}"
[ "$PT" != "-" ] && assert_rgb "… that point ($PT) reads (40,180,80) ± 4" "$GREEN" "$(px "$D/start-bg.png" "$PX" "$PY")" 4
adb shell pm revoke app.tileshell android.permission.READ_MEDIA_IMAGES
assert_eq "READ_MEDIA_IMAGES revoked" "false" "$(perm_granted READ_MEDIA_IMAGES)"
c6; ensure_start; sleep 3
screencap "$D/start-bg-revoked.png"
[ "$PT" != "-" ] && assert_eq "with the grant revoked, Start's pixel at $PT is unchanged (r3 D9)" "$(px "$D/start-bg.png" "$PX" "$PY")" "$(px "$D/start-bg-revoked.png" "$PX" "$PY")"
adb shell pm grant app.tileshell android.permission.READ_MEDIA_IMAGES
assert_eq "READ_MEDIA_IMAGES granted back" "true" "$(perm_granted READ_MEDIA_IMAGES)"

# ----------------------------------------------------------------------------------------------- lock screen
log "--- set as lock screen"
photos_cold "$X.lock"
viewer_open "$X.lock" "$ID2"
viewer_menu
tap_node "$D/menu.xml" viewer_menu_setas; sleep 1
dump_ui "$D/setas.xml"
MARK="$(ring_mark)"
tap_node "$D/setas.xml" viewer_setas_lock; sleep 4
assert_contains "Photos' line" "[photosapp] set as lock screen $ID2 -> wallpaper " "$(ring_since "$MARK")"
adb shell dumpsys wallpaper | tr -d '\r' > "$D/wallpaper-after.txt"
LOCK_AFTER="$(lock_state)"
record "lock wallpaper state after" "${LOCK_AFTER:-none}"
assert_ne "dumpsys wallpaper: the lock wallpaper id changed" "$LOCK_BEFORE" "$LOCK_AFTER"
adb shell input keyevent KEYCODE_BACK; sleep 1

# ----------------------------------------------------------------------------------------------- slideshow
log "--- slideshow"
photos_cold "$X.show"
viewer_open "$X.show" "$ID1"
viewer_menu
MARK="$(ring_mark)"
tap_node "$D/menu.xml" viewer_menu_slideshow
# No ring read and no dump while the steps run: a `dumpsys` of the shell is answered on its main thread, and run 1 of
# this row (kept) read a 50-ms frame gap in a step that the driver's own half-second polling overlapped. The steps are
# 5 s apart from the tap: the screencap is taken between the second (10 s) and the third (15 s), the ring read after it.
sleep 11.6
screencap "$D/slide2.png"; SHOT=yes
sleep 4.9
SLICE="$(ring_since "$MARK")"
printf '%s\n' "$SLICE" | grep -E '\[photosapp\] slideshow|\[motion\] slideshow_step' > "$D/slideshow-lines.txt"
sed 's/^/        /' "$D/slideshow-lines.txt" >> "$LOG"
NEXT="$(grep -F '[photosapp] slideshow next ' "$D/slideshow-lines.txt" | head -3)"
assert_eq "three consecutive slideshow next lines, their ids in collection order (qa-photo-2, -3, -4)" "$ID2 $ID3 $ID4" "$(printf '%s\n' "$NEXT" | sed 's/.*slideshow next //' | xargs)"
# shellcheck disable=SC2046
set -- $(printf '%s\n' "$NEXT" | grep -oE 'wall=[0-9]+' | cut -d= -f2)
record "the three lines' wall= stamps" "${1:-} ${2:-} ${3:-}"
assert_within "the gap line 1 → 2 is the Y6 interval (5 s ± 100 ms)" 5000 "$(( ${2:-0} - ${1:-0} ))" 100
assert_within "the gap line 2 → 3 is the Y6 interval (5 s ± 100 ms)" 5000 "$(( ${3:-0} - ${2:-0} ))" 100
# Each next line is followed by its own [motion] slideshow_step (before the following next line).
python3 - "$D/slideshow-lines.txt" > "$D/slideshow-steps.txt" <<'PY'
import re, sys
lines = [l.rstrip("\n") for l in open(sys.argv[1])]
out, n = [], 0
for i, l in enumerate(lines):
    if "slideshow next " in l and n < 3:
        n += 1
        nxt = lines[i + 1] if i + 1 < len(lines) else ""
        m = re.search(r"\[motion\] slideshow_step .*", nxt)
        out.append(m.group(0) if m else "MISSING")
print("\n".join(out))
PY
k=0
while read -r STEP; do
  k=$((k + 1))
  record "step $k's motion line" "$STEP"
  assert_contains "slideshow next line $k is followed by [motion] slideshow_step" "[motion] slideshow_step " "$STEP"
  assert_within "step $k: settle = 250 ± 17 ms" 250 "$(printf '%s' "$STEP" | grep -oE 'settle=[0-9.]+' | cut -d= -f2)" 17
  assert_within "step $k: maxGapMs <= 33.4 ms (C-31)" 16.7 "$(printf '%s' "$STEP" | grep -oE 'maxGapMs=[0-9.]+' | cut -d= -f2)" 16.7
done < "$D/slideshow-steps.txt"
assert_eq "three steps were read" "3" "$k"
assert_eq "a screencap was taken after the second line" "yes" "$SHOT"
[ "$SHOT" = yes ] && assert_rgb "the screencap after the second line reads that image's colour (qa-photo-3)" "230,200,40" "$(px "$D/slide2.png" 540 1170)" 4
adb shell input tap 540 600; sleep 1
record "a tap stops the slideshow" "$(ring_since "$MARK" | grep -cF '[photosapp] slideshow stop') stop line(s)"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
no_crash
c6
adb shell am force-stop app.tileshell; sleep 1
prefs_restore "$D/prefs-before.tar"
for f in $(backgrounds); do case " $BG_BEFORE " in *" $f "*) ;; *) adb shell run-as app.tileshell rm "files/backgrounds/$f" ;; esac; done
assert_eq "restore: files/backgrounds as found" "$BG_BEFORE" "$(backgrounds)"
record "restore: the lock wallpaper cleared (service call wallpaper 15 … FLAG_LOCK)" "$(lock_clear)"
sleep 1
assert_eq "restore: dumpsys wallpaper's lock state is as found (the id this row set is cleared)" "$LOCK_BEFORE" "$(lock_state)"
adb uninstall "$QACAP_PKG" > "$D/qacapture-uninstall.out" 2>&1
assert_eq "restore: qa-capture is uninstalled" "0" "$(adb shell pm list packages "$QACAP_PKG" | grep -c "$QACAP_PKG")"
media_down
perm_restore
layout_restore "$BASELINE" > "$D/restore1.out" 2>&1; assert_eq "restore: layout_restore of the baseline" "0" "$?"
ensure_start; sleep 2
gdump "$D/start-restored.xml" || true; screencap "$D/start-restored.png"
assert_eq "restore: no tile-free point of Start reads the fixture colour any more" "0 -" "$(start_points "$D/start-restored.png" "$D/start-restored.xml" "$GREEN")"
rings_save
row_end
