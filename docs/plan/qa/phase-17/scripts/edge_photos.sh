#!/usr/bin/env bash
# Phase 17, the Edge-cases bullets that name Photos, pictures or :photosedit — one function edge_<ID> per bullet, each
# its own row (folder EDGE_<ID>, its own MARKs, its own restore), runnable alone or all together:
#
#   edge_photos.sh                 every sub-step below, in this order
#   edge_photos.sh <ID> [<ID>…]    only the named ones
#
#   THOUSANDS        "Thousands of photos": 3,000 flat-colour PNGs made with make_photos.py's method in a loop (about
#                    5 MB), made in a temp folder, pushed into Pictures/QA-3000 and scanned
#                    (both removed at the sub-step's end); `am start -W` of PhotosActivity reports
#                    TotalTime < 2000 ms; the collection scroll (phase 01 P4's script: 20 swipes from 75 % to 25 % of
#                    the height, 300 ms each, after `dumpsys gfxinfo … reset`, the display mode the same before and
#                    after) meets P4's thresholds — janky frames <= 5 % and the 99th percentile <= 2 vsync periods
#                    (34 ms at 60 Hz); PhotosFeed's tile line (`[photos] refresh (start): …`) reads the same after the
#                    push as before it.
#   HEIC_RAW         "HEIC and RAW": qa-still.heic and qa-still.dng (scripts/make_stills.sh; what could and could not be
#                    made is in gen/media/qa-stills.source and copied into the log). Each is a tile or a placeholder
#                    and its viewer shows the picture or the error state — never a crash; the HEIC, edited
#                    (auto-enhance), is saved as a JPEG copy and `[photosapp] edit … -> <uri>` says so (a .jpg name,
#                    T17-3). NOT shown here: a HEIC "from the S25U" and a DNG's EMBEDDED PREVIEW (the hand-made DNG has
#                    none) — both are P3.
#   FILE_GONE        "A MediaStore row whose file is gone": on this image `adb shell rm /sdcard/…` drops the row at once
#                    (Change Log 2026-10-05 14:27 (9)), so the file is removed under /data/media/0 as root (root on for
#                    that one command, off again at once) → the thumbnail is a placeholder, the viewer shows the error
#                    state, and the next scan removes the row.
#   REVOKE_VIEWER    "Permission revoked mid-session", its Photos half: `pm revoke … READ_MEDIA_IMAGES` with the viewer
#                    open → the platform restarts the process (the pid is gone or new); on return the page is the
#                    checklist state (its text names the Setup checklist). The CAMERA half is the Camera rows'.
#   KILL_PHOTOSEDIT  "A … :photosedit process killed by the system mid-write": a 24-megapixel PNG is being saved (so the
#                    write lasts seconds) and :photosedit is killed by its recorded pid while its row is pending; on
#                    the next start of the process the row is cleaned — `content query --projection _id:is_pending`
#                    shows none. The :camera and :video halves are those rows'.
#   FRAME_DELETED    "The picture-frame photo deleted in Photos": the main photo (the `photo_frame` pref, phase 01 item
#                    4) set to a fixture, the fixture deleted in Photos' viewer → `[photos] main photo unreadable …` and
#                    the tile falls back to the normal tile's line.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_photos.sh"
ALL_IDS="FILE_GONE REVOKE_VIEWER KILL_PHOTOSEDIT FRAME_DELETED HEIC_RAW THOUSANDS"
FAILED_ROWS=""
edge_end() { no_crash; rings_save; row_end || FAILED_ROWS="$FAILED_ROWS $ROW"; }
consent_tap() { # button1 (allow) | button2 (deny): MediaProvider's consent dialog, asserted first
  dump_ui "$D/consent.xml"
  assert_contains "MediaProvider's consent dialog is on top" "com.android.providers.media.module" "$(cat "$D/consent.xml")"
  tap_node "$D/consent.xml" "android:id/$1"; sleep 3
}

# ----------------------------------------------------------------------------------------------- THOUSANDS
edge_THOUSANDS() {
  photos_row_begin EDGE_THOUSANDS "edge: 3,000 photos — the cold start, the collection scroll's frames, the tile's line"
  local big n mode0 mode1 total janky p99 pct before after i
  # The 3,000 files live in a temp folder for this sub-step only: removed from the device AND the host at its end.
  big="$(mktemp -d /dev/shm/qaphotos-3000.XXXXXX)"
  perm_set READ_MEDIA_IMAGES true
  # The six fixtures come first, so the tile already reads as many photos as it ever does BEFORE the 3,000 arrive (the
  # census alone is fewer than the tile shows: run 2 of this sub-step read photos=6 before and photos=8 after).
  # shellcheck disable=SC2086
  media_up $SIX
  python3 - "$big" <<'PY'
import pathlib, struct, sys, zlib
out = pathlib.Path(sys.argv[1])
def chunk(tag, data): return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
head = chunk(b"IHDR", struct.pack(">IIBBBBB", 640, 480, 8, 2, 0, 0, 0))
for i in range(3000):
    rgb = bytes(((i * 37) % 256, (i * 91) % 256, (i * 151) % 256))
    raw = (b"\x00" + rgb * 640) * 480
    (out / ("qa-k-%04d.png" % i)).write_bytes(b"\x89PNG\r\n\x1a\n" + head + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))
PY
  assert_eq "3,000 fixtures generated (make_photos.py's method in a loop)" "3000" "$(ls "$big" | grep -c '\.png$')"
  record "the fixtures' size on the host" "$(du -sh "$big" | cut -f1)"
  # The tile's line before the push.
  # (Each MARK is taken before the stop: the shell is the home app and starts again by itself at once.)
  rings_save; MARK="$(ring_mark)"; adb shell am force-stop app.tileshell; sleep 1
  ensure_start; sleep 4
  before="$(ring_since "$MARK" | grep -F '[photos] refresh (start)' | tail -1 | sed 's/.*\[photos\] //')"
  record "PhotosFeed's line before the push" "$before"
  adb shell mkdir -p /sdcard/Pictures/QA-3000
  adb push "$big/." /sdcard/Pictures/QA-3000/ > "$D/push.out" 2>&1; assert_eq "adb push of the 3,000" "0" "$?"
  media_scan
  for i in $(seq 1 90); do n="$(media_count images)"; [ "$n" -ge $((CENSUS_IMAGES + 3006)) ] && break; sleep 2; done
  assert_eq "MediaStore holds the census + the six + 3,000 images" "$((CENSUS_IMAGES + 3006))" "$(media_count images)"
  rings_save; adb shell am force-stop app.tileshell; sleep 2
  MARK="$(ring_mark)"
  adb shell am start -W -n "$PHOTOS_ACTIVITY" | tr -d '\r' > "$D/am-start.txt"
  total="$(sed -n 's/^TotalTime: //p' "$D/am-start.txt")"
  record "am start -W (the shell is the home app: its process is already up again after the stop, so the launch state is the platform's to name)" "$(xargs < "$D/am-start.txt")"
  assert_eq "am start -W of PhotosActivity: TotalTime < 2000 ms" "yes" "$([ -n "$total" ] && [ "$total" -lt 2000 ] && echo yes || echo "no: ${total:-none}")"
  sleep 5
  dump_ui "$D/collection.xml"
  assert_eq "photos_pivot:collection is selected" "true" "$(selected "$D/collection.xml" photos_pivot:collection)"
  assert_contains "the library line counts them" "[photosapp] library: images=$((CENSUS_IMAGES + 3006)) " "$(ring_since "$MARK")"
  mode0="$(adb shell dumpsys display | tr -d '\r' | grep -m1 -oE 'mActiveModeId=[0-9]+|activeModeId=[0-9]+')"
  adb shell dumpsys gfxinfo app.tileshell reset >/dev/null 2>&1
  for i in $(seq 1 20); do adb shell input swipe 540 1755 540 585 300; done
  adb shell dumpsys gfxinfo app.tileshell </dev/null | tr -d '\r' > "$D/gfxinfo.txt"
  mode1="$(adb shell dumpsys display | tr -d '\r' | grep -m1 -oE 'mActiveModeId=[0-9]+|activeModeId=[0-9]+')"
  assert_eq "the display mode is the same before and after the scroll" "$mode0" "$mode1"
  total="$(sed -n 's/^Total frames rendered: //p' "$D/gfxinfo.txt" | head -1)"
  janky="$(sed -n 's/^Janky frames: \([0-9]*\).*/\1/p' "$D/gfxinfo.txt" | head -1)"
  p99="$(sed -n 's/^99th percentile: \([0-9]*\)ms/\1/p' "$D/gfxinfo.txt" | head -1)"
  pct="$(python3 -c "import sys; t,j=int(sys.argv[1] or 0),int(sys.argv[2] or 0); print('%.2f' % (100.0*j/t) if t else '')" "${total:-0}" "${janky:-0}")"
  record "gfxinfo over the 20 swipes: frames / janky / janky % / 99th percentile" "${total:-?} / ${janky:-?} / ${pct:-?} % / ${p99:-?} ms"
  assert_eq "the scroll rendered frames (the read is not empty)" "yes" "$([ "${total:-0}" -gt 100 ] && echo yes || echo "no: ${total:-0}")"
  assert_within "phase 01 P4: janky frames <= 5 %" 2.5 "$pct" 2.5
  assert_within "phase 01 P4: 99th percentile <= 2 vsync (34 ms at 60 Hz)" 17 "$p99" 17
  # The tile's line after the push.
  rings_save; MARK="$(ring_mark)"; adb shell am force-stop app.tileshell; sleep 1
  ensure_start; sleep 5
  after="$(ring_since "$MARK" | grep -F '[photos] refresh (start)' | tail -1 | sed 's/.*\[photos\] //')"
  record "PhotosFeed's line after the push" "$after"
  assert_ne "PhotosFeed wrote its tile line with the 3,000 present" "" "$after"
  assert_ne "PhotosFeed wrote its tile line before the push too" "" "$before"
  assert_eq "PhotosFeed's tile line is unaffected" "$before" "$after"
  # restore
  c6
  adb shell rm -rf /sdcard/Pictures/QA-3000
  rm -rf "$big"
  assert_eq "restore: the 3,000 are gone from the host's temp folder and from the device's folder" "no 0" "$([ -e "$big" ] && echo yes || echo no) $(adb shell 'ls /sdcard/Pictures/QA-3000 2>/dev/null' | grep -c .)"
  media_scan
  for i in $(seq 1 90); do [ "$(media_count images)" -le "$((CENSUS_IMAGES + 6))" ] && break; sleep 2; done
  media_down
  perm_restore
  ensure_start
  edge_end
}

# ----------------------------------------------------------------------------------------------- HEIC_RAW
edge_HEIC_RAW() {
  photos_row_begin EDGE_HEIC_RAW "edge: a HEIC and a DNG still — a tile or a placeholder, the viewer, the edited HEIC's JPEG copy"
  local hid did f state
  [ -f "$GEN/qa-stills.source" ] || bash "$P17/scripts/make_stills.sh" "$GEN" >/dev/null 2>&1
  log "how the two stills were made (gen/media/qa-stills.source):"; sed 's/^/        /' "$GEN/qa-stills.source" | tee -a "$LOG" >/dev/null
  assert_eq "qa-still.heic was made on this host" "yes" "$([ -s "$GEN/qa-still.heic" ] && echo yes || echo no)"
  assert_eq "qa-still.dng was made on this host" "yes" "$([ -s "$GEN/qa-still.dng" ] && echo yes || echo no)"
  record "NOT shown on the AVD — mapped to P3" "a HEIC from the S25U; a camera DNG's embedded preview (the hand-made DNG holds none)"
  perm_set READ_MEDIA_IMAGES true
  media_up qa-still.heic qa-still.dng
  hid="$(img_id Pictures/QA-Album/ qa-still.heic)"; did="$(img_id Pictures/QA-Album/ qa-still.dng)"
  record "the HEIC's row" "$(row_of images "${hid:-0}")"
  record "the DNG's row" "$(row_of images "${did:-0}")"
  assert_ne "the HEIC has an image row" "" "$hid"
  assert_ne "the DNG has an image row" "" "$did"
  MARK="$(ring_mark)"
  photos_cold "$D/collection.xml"; screencap "$D/collection.png"
  for f in "heic:$hid" "dng:$did"; do
    local kind="${f%%:*}" id="${f#*:}"
    [ -n "$id" ] || continue
    scroll_to_node "$D/c-$kind.xml" "photos_item:$id" 12
    assert_eq "$kind: photos_pivot:collection is selected" "true" "$(selected "$D/c-$kind.xml" photos_pivot:collection)"
    assert_eq "$kind: the row is a tile of the collection" "yes" "$(has_node "$D/c-$kind.xml" "photos_item:$id")"
    record "$kind: its tile is the placeholder (photos_item_missing)" "$(has_node "$D/c-$kind.xml" "photos_item_missing:$id")"
    tap_node "$D/c-$kind.xml" "photos_item:$id"; sleep 3
    dump_ui "$D/v-$kind.xml"; screencap "$D/v-$kind.png"
    assert_eq "$kind: Photos is still up after the tap (never a crash)" "$PHOTOS_ACTIVITY" "$(top_activity)"
    assert_eq "$kind: the viewer is up" "yes" "$(has_node "$D/v-$kind.xml" viewer)"
    if [ "$(has_node "$D/v-$kind.xml" viewer_error)" = yes ]; then state="error state: $(node_text "$D/v-$kind.xml" viewer_error_text)"; else state="picture, centre pixel $(px "$D/v-$kind.png" 540 1170)"; fi
    record "$kind: the viewer shows" "$state"
    assert_eq "$kind: the viewer shows the picture or its error state" "yes" "$([ "$(has_node "$D/v-$kind.xml" viewer_error)" = yes ] || [ "$(has_node "$D/v-$kind.xml" viewer_image)" = yes ] && echo yes || echo no)"
    eval "STATE_$kind=\$state"
    adb shell input keyevent KEYCODE_BACK; sleep 2
  done
  record "the launcher's lines about the two" "$(ring_since "$MARK" | grep -E "\[photosapp\] (thumbnail|viewer) (${hid:-x}|${did:-x})" | sed 's/.*\[photosapp\] //' | tr '\n' '|')"
  # The edited HEIC: saved as a JPEG copy (T17-3).
  case "${STATE_heic:-}" in
    picture*)
      assert_rgb "heic: the platform HEIF decoder drew the fixture's colour (± 8: one HEVC still)" "220,40,40" "$(px "$D/v-heic.png" 540 1170)" 8
      open_editor "$hid"
      assert_eq "heic: the editor is up" "yes" "$(has_node "$E" edit_root)"
      etap edit_enhance
      COUNT_BEFORE="$(media_count images)"
      edit_save
      record "heic: the edit's line" "${ELINE##*\[photosapp\] }"
      assert_contains "heic: [photosapp] edit enhance -> <uri>" "[photosapp] edit enhance -> content://media/" "$ELINE"
      assert_contains "heic: … the line says the copy is a JPEG (a .jpg name)" ".jpg" "$ELINE"
      assert_eq "heic: the copy's row is a JPEG" "image/jpeg" "$(copy_field mime_type)"
      assert_contains "heic: … named .jpg" ".jpg" "$(copy_field _display_name)"
      assert_eq "heic: one new row" "$((COUNT_BEFORE + 1))" "$(media_count images)"
      assert_eq "heic: the original is still a HEIC row (same id)" "$hid" "$(img_id Pictures/QA-Album/ qa-still.heic)"
      ;;
    *)
      record "heic: the edited-HEIC clause" "NOT exercised: the AVD's viewer did not decode the HEIC (${STATE_heic:-no state}); a HEIC cannot be edited where it cannot be decoded — P3 / P16 on the phone"
      ;;
  esac
  c6
  media_down
  perm_restore
  ensure_start
  edge_end
}

# ----------------------------------------------------------------------------------------------- FILE_GONE
edge_FILE_GONE() {
  photos_row_begin EDGE_FILE_GONE "edge: a row whose file is gone — placeholder tile, the viewer's error state, the next scan removes the row"
  perm_set READ_MEDIA_IMAGES true
  # shellcheck disable=SC2086
  media_up $SIX
  six_ids
  rings_save; adb shell am force-stop app.tileshell; sleep 1
  # Root for this one command (the row survives only when MediaProvider does not see the removal), off again at once.
  adb root >/dev/null 2>&1; adb wait-for-device; sleep 1
  adb shell rm /data/media/0/DCIM/Camera/qa-photo-2.png; record "rm under /data/media/0 as root, rc" "$?"
  adb unroot >/dev/null 2>&1; adb wait-for-device; sleep 2
  assert_absent "root is off again" "uid=0" "$(adb shell id | tr -d '\r')"
  # `ls` through /sdcard still names it (the FUSE view lists what MediaStore holds — run 1 of this sub-step): the file
  # being gone is read by opening it.
  record "ls /sdcard/…/qa-photo-2.png (the FUSE view, from MediaStore's row)" "$(adb shell ls /sdcard/DCIM/Camera/qa-photo-2.png 2>&1 | tr -d '\r')"
  assert_ne "the file is gone (it cannot be opened)" "0" "$(adb shell 'cat /sdcard/DCIM/Camera/qa-photo-2.png >/dev/null 2>&1; echo $?' | tr -d '\r')"
  assert_eq "(control) its neighbour can be opened" "0" "$(adb shell 'cat /sdcard/DCIM/Camera/qa-photo-1.png >/dev/null 2>&1; echo $?' | tr -d '\r')"
  assert_eq "its MediaStore row is still there (no scan has run)" "$ID2" "$(img_id DCIM/Camera/ qa-photo-2.png)"
  assert_eq "wake (after the adbd restarts)" "Awake" "$(wake_device)"
  MARK="$(ring_mark)"
  photos_start; sleep 2
  dump_ui "$D/collection.xml"; screencap "$D/collection.png"
  assert_eq "photos_pivot:collection is selected" "true" "$(selected "$D/collection.xml" photos_pivot:collection)"
  assert_eq "the row is still a tile" "yes" "$(has_node "$D/collection.xml" "photos_item:$ID2")"
  assert_eq "its thumbnail is the placeholder (photos_item_missing)" "yes" "$(has_node "$D/collection.xml" "photos_item_missing:$ID2")"
  assert_eq "(control) a row whose file exists has no placeholder" "no" "$(has_node "$D/collection.xml" "photos_item_missing:$ID1")"
  record "Photos' line for the placeholder" "$(ring_since "$MARK" | grep -F "[photosapp] thumbnail $ID2" | tail -1 | sed 's/.*\[photosapp\] //')"
  tap_node "$D/collection.xml" "photos_item:$ID2"; sleep 2
  dump_ui "$D/viewer.xml"; screencap "$D/viewer.png"
  assert_eq "the viewer is up" "yes" "$(has_node "$D/viewer.xml" viewer)"
  assert_eq "the viewer shows the error state (viewer_error)" "yes" "$(has_node "$D/viewer.xml" viewer_error)"
  record "the error state's text" "$(node_text "$D/viewer.xml" viewer_error_text)"
  assert_eq "Photos is still up" "$PHOTOS_ACTIVITY" "$(top_activity)"
  adb shell input keyevent KEYCODE_BACK; sleep 1
  media_scan; sleep 2
  assert_eq "the next scan removes the row" "" "$(img_id DCIM/Camera/ qa-photo-2.png)"
  dump_ui "$D/after-scan.xml"
  assert_eq "photos_pivot:collection is selected" "true" "$(selected "$D/after-scan.xml" photos_pivot:collection)"
  assert_eq "… and its tile leaves the page" "no" "$(has_node "$D/after-scan.xml" "photos_item:$ID2")"
  assert_eq "its neighbour stays" "yes" "$(has_node "$D/after-scan.xml" "photos_item:$ID1")"
  c6
  media_down
  perm_restore
  ensure_start
  edge_end
}

# ----------------------------------------------------------------------------------------------- REVOKE_VIEWER
edge_REVOKE_VIEWER() {
  photos_row_begin EDGE_REVOKE_VIEWER "edge: READ_MEDIA_IMAGES revoked with the viewer open — the restart, the checklist state on return"
  local pid pid2 text
  perm_set READ_MEDIA_IMAGES true
  perm_set READ_MEDIA_VISUAL_USER_SELECTED "$(perm_granted READ_MEDIA_VISUAL_USER_SELECTED)"
  # shellcheck disable=SC2086
  media_up $SIX
  six_ids
  photos_cold "$D/collection.xml"
  viewer_open "$D/collection.xml" "$ID0"
  assert_eq "the viewer is open" "yes" "$(has_node "$D/viewer.xml" viewer)"
  pid="$(adb shell pidof app.tileshell | tr -d '\r')"
  assert_ne "the shell is running (pid)" "" "$pid"
  rings_save
  MARK="$(ring_mark)"
  adb shell pm revoke app.tileshell android.permission.READ_MEDIA_IMAGES; sleep 3
  assert_eq "READ_MEDIA_IMAGES is revoked" "false" "$(perm_granted READ_MEDIA_IMAGES)"
  pid2="$(adb shell pidof app.tileshell | tr -d '\r')"
  record "the shell's pid before / after the revoke" "$pid / ${pid2:-none}"
  assert_ne "the platform restarted the process (the old pid is gone)" "$pid" "${pid2:-gone}"
  record "what is on top after the revoke" "$(top_activity)"
  assert_eq "wake" "Awake" "$(wake_device)"
  photos_start; sleep 1
  dump_ui "$D/return.xml"; screencap "$D/return.png"
  assert_eq "on return: Photos' page (photos_pivot:collection selected)" "true" "$(selected "$D/return.xml" photos_pivot:collection)"
  assert_eq "on return: the viewer is not up" "no" "$(has_node "$D/return.xml" viewer)"
  text="$(node_text "$D/return.xml" photos_denied)$(node_text "$D/return.xml" photos_partial)"
  record "on return: the state's text (denied where VISUAL_USER_SELECTED is not held, partial where it is: $(perm_granted READ_MEDIA_VISUAL_USER_SELECTED))" "$text"
  assert_contains "on return: the checklist state — its text names the Setup checklist" "Setup checklist" "$text"
  assert_eq "on return: the grant is offered (photos_grant)" "yes" "$(has_node "$D/return.xml" photos_grant)"
  assert_contains "on return: Photos' access line is not GRANTED" "[photosapp] access=" "$(ring_since "$MARK")"
  rings_save
  adb shell pm grant app.tileshell android.permission.READ_MEDIA_IMAGES
  assert_eq "restore: READ_MEDIA_IMAGES granted back" "true" "$(perm_granted READ_MEDIA_IMAGES)"
  c6
  media_down
  perm_restore
  ensure_start
  edge_end
}

# ----------------------------------------------------------------------------------------------- KILL_PHOTOSEDIT
edge_KILL_PHOTOSEDIT() {
  photos_row_begin EDGE_KILL_PHOTOSEDIT "edge: :photosedit killed mid-write — the pending row is cleaned on the next start"
  local big="$GEN/qa-big.png" bid epid seen=no i sb pend files
  if [ ! -s "$big" ]; then
    python3 - "$big" <<'PY'
import struct, sys, zlib
w, h = 6000, 4000   # 24 megapixels of one colour: a small file whose save takes seconds
def chunk(tag, data): return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
c = zlib.compressobj(6); row = b"\x00" + bytes((40, 90, 220)) * w
idat = b"".join(c.compress(row) for _ in range(h)) + c.flush()
open(sys.argv[1], "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", idat) + chunk(b"IEND", b""))
PY
  fi
  record "the 24-megapixel fixture" "$(stat -c%s "$big") bytes"
  pend_rows() { q "content query --uri content://media/external/images/media --projection _id:is_pending:owner_package_name --where 'is_pending=1'" | grep -c 'is_pending=1'; }
  pend_files() { adb shell 'ls -a /sdcard/Pictures/QA-Album/ 2>/dev/null' | tr -d '\r' | grep -c '^\.pending-'; }
  perm_set READ_MEDIA_IMAGES true
  media_up qa-big.png
  bid="$(img_id Pictures/QA-Album/ qa-big.png)"
  assert_ne "the fixture has a row" "" "$bid"
  assert_eq "no pending row before the step" "0 0" "$(pend_rows) $(pend_files)"
  rings_save; adb shell am force-stop app.tileshell; sleep 1
  open_editor "$bid"
  assert_eq "the editor is up" "yes" "$(has_node "$E" edit_root)"
  etap edit_enhance 2; edump
  epid="$(adb shell pidof app.tileshell:photosedit | tr -d '\r')"
  record ":photosedit's pid" "$epid"
  case "$epid" in ''|*[!0-9]*) _verdict FAIL ":photosedit is running, one pid" "got [$epid]"; epid="" ;; esac
  sb="$(bounds "$E" edit_save)"
  rings_save
  tap_bounds "$sb"
  for i in $(seq 1 150); do
    pend="$(pend_rows)"; files="$(pend_files)"
    if [ "$pend" != 0 ] || [ "$files" != 0 ]; then seen=yes; break; fi
    sleep 0.1
  done
  [ -n "$epid" ] && adb shell run-as app.tileshell kill -9 "$epid"
  sleep 1
  record "when the kill was sent: pending rows / .pending files seen" "$pend / $files (poll $i)"
  assert_eq "the kill landed mid-write (a pending row or file of the save was seen first)" "yes" "$seen"
  assert_ne ":photosedit's recorded pid is gone" "$epid" "$(adb shell pidof app.tileshell:photosedit | tr -d '\r')"
  record "left behind by the kill: pending rows / .pending files" "$(pend_rows) / $(pend_files)"
  # The next start of the process.
  adb shell input keyevent KEYCODE_BACK; sleep 1
  MARK="$(ring_mark)"
  open_editor "$bid"
  assert_ne ":photosedit runs again" "" "$(adb shell pidof app.tileshell:photosedit | tr -d '\r')"
  sleep 2
  record "the new process's cleanup line" "$(ring_since "$MARK" "$EDIT_RING" | grep -F '[photosapp] pending cleanup:' | tail -1 | sed 's/.*\[photosapp\] //')"
  assert_contains "the next start ran its pending cleanup" "[photosapp] pending cleanup: " "$(ring_since "$MARK" "$EDIT_RING")"
  q "content query --uri content://media/external/images/media --projection _id:is_pending" > "$D/is_pending.txt"
  assert_ne "(the query read rows)" "0" "$(grep -c '_id=' "$D/is_pending.txt")"
  assert_eq "content query --projection _id:is_pending shows none pending" "0" "$(grep -c 'is_pending=1' "$D/is_pending.txt")"
  assert_eq "… nor does the query that asks for pending rows, nor the folder (.pending- files)" "0 0" "$(pend_rows) $(pend_files)"
  assert_eq "the original is untouched (its row)" "$bid" "$(img_id Pictures/QA-Album/ qa-big.png)"
  c6
  media_down
  perm_restore
  ensure_start
  edge_end
}

# ----------------------------------------------------------------------------------------------- FRAME_DELETED
edge_FRAME_DELETED() {
  photos_row_begin EDGE_FRAME_DELETED "edge: the picture-frame photo deleted in Photos — main photo unreadable, the tile falls back"
  local uri line after
  layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "layout_restore of the baseline" "0" "$?"
  perm_set READ_MEDIA_IMAGES true
  # shellcheck disable=SC2086
  media_up $SIX
  six_ids
  uri="content://media/external/images/media/$ID0"
  rings_save; adb shell am force-stop app.tileshell; sleep 1
  prefs_backup "$D/prefs-before.tar"
  # The main photo, through phase 01 item 4's route (one key of shared_prefs/start_theme.xml, the shell stopped).
  adb shell run-as app.tileshell cat shared_prefs/start_theme.xml > "$D/prefs-in.xml" 2>/dev/null
  assert_ne "start_theme.xml was read" "0" "$(stat -c%s "$D/prefs-in.xml" 2>/dev/null || echo 0)"
  python3 "$P01S/prefs_edit.py" "$D/prefs-in.xml" photo_frame string "$uri" > "$D/prefs-out.xml"
  if [ -s "$D/prefs-in.xml" ] && [ -s "$D/prefs-out.xml" ]; then adb shell "run-as app.tileshell sh -c 'cat > shared_prefs/start_theme.xml'" < "$D/prefs-out.xml"; fi
  # The MARK comes BEFORE the stop: the shell is the home app and starts again by itself at once (run 1 of this
  # sub-step took it after, and the start's own line was already behind it).
  MARK="$(ring_mark)"
  adb shell am force-stop app.tileshell; sleep 1
  ensure_start; sleep 4
  assert_contains "(precondition) the tile is the picture frame on the fixture" "[photos] picture frame (" "$(ring_since "$MARK")"
  photos_start; sleep 1
  dump_ui "$D/collection.xml"
  assert_eq "photos_pivot:collection is selected" "true" "$(selected "$D/collection.xml" photos_pivot:collection)"
  viewer_open "$D/collection.xml" "$ID0"
  MARK="$(ring_mark)"
  tap_node "$D/viewer.xml" viewer_delete; sleep 3
  consent_tap button1
  assert_eq "the frame's photo is deleted (no row)" "" "$(img_id DCIM/Camera/ qa-photo-0.png)"
  sleep 3
  ring_since "$MARK" | grep -F '[photos] ' > "$D/photos-lines.txt"
  sed 's/^/        /' "$D/photos-lines.txt" >> "$LOG"
  line="$(grep -nF '[photos] main photo unreadable' "$D/photos-lines.txt" | head -1)"
  assert_contains "PhotosFeed logs main photo unreadable" "[photos] main photo unreadable, falling back to the normal tile" "$line"
  after="$(tail -n +"$(( ${line%%:*} + 0 ))" "$D/photos-lines.txt" 2>/dev/null | tail -n +2)"
  record "PhotosFeed's lines after it" "$(printf '%s\n' "$after" | sed 's/.*\[photos\] //' | tr '\n' '|')"
  assert_contains "the tile falls back: the normal tile's line follows" "[photos] refresh (" "$after"
  absent_in "… and no picture-frame line follows it" "[photos] picture frame (" "$after"
  # restore
  c6
  adb shell am force-stop app.tileshell; sleep 1
  prefs_restore "$D/prefs-before.tar"
  assert_absent "restore: the main photo is no longer the fixture" "$uri" "$(adb shell run-as app.tileshell cat shared_prefs/start_theme.xml | tr -d '\r')"
  media_down
  perm_restore
  ensure_start
  edge_end
}

# ----------------------------------------------------------------------------------------------- the runner
[ $# -gt 0 ] || set -- $ALL_IDS
for id in "$@"; do
  if declare -F "edge_$id" >/dev/null; then "edge_$id"; else echo "edge_photos.sh: no sub-step edge_$id (have: $ALL_IDS)" >&2; FAILED_ROWS="$FAILED_ROWS $id"; fi
done
[ -z "$FAILED_ROWS" ] || { echo "edge_photos.sh: failed:$FAILED_ROWS" >&2; exit 1; }
