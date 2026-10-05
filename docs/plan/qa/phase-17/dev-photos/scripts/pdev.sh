#!/usr/bin/env bash
# Phase 17, Photos' development proof: what every dev-photos script sources. It sets the device and this worktree's
# APK, sources the shared floor (lib.sh), takes the device lock FIRST, and installs this build when the device holds
# another (never with -g). The fixtures are made on the host and pushed by media_push; media_clean removes everything a
# script pushed or the shell made, scans, and asserts the counts are the census again.
export ANDROID_SERIAL=emulator-5554
PDEV_HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WT="$(cd "$PDEV_HERE/../../../../../.." && pwd)"
export TILESHELL_APK="$WT/app/build/outputs/apk/debug/app-debug.apk"
. "$PDEV_HERE/lib.sh"
take_device_lock
FIX="${PHOTOS_FIX:-/tmp/claude-1000/-home-jeremyking/b5b8c63b-5d38-49e9-84f3-de917a96acb2/scratchpad/photos-fix}"
if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
  adb install -r "$APK" >/dev/null || { echo "install failed" >&2; exit 4; }
fi

top() { adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity' | grep -oE '[a-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | tr -d '\r'; }
pnodes() { python3 "$PDEV_HERE/pnodes.py" "$1" "$2"; }
img_count() { adb shell content query --uri content://media/external/images/media --projection _id | grep -c '_id='; }
vid_count() { adb shell content query --uri content://media/external/video/media --projection _id | grep -c '_id='; }
scan() { adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null; }
# A row's id by its folder AND name ("DCIM/Camera/" "qa-photo-0.png"): the device holds other files of the same names.
img_id() { adb shell "content query --uri content://media/external/images/media --projection _id:_display_name --where \"relative_path='$1' AND _display_name='$2'\"" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1 | tr -d '\r'; }
vid_id() { adb shell "content query --uri content://media/external/video/media --projection _id:_display_name --where \"relative_path='$1' AND _display_name='$2'\"" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1 | tr -d '\r'; }
# perm_set <PERMISSION> <true|false>: grants or revokes with pm (never install -g), remembering the state it found;
# perm_restore puts every permission a script changed back and asserts it.
PERM_WAS=""
perm_set() {
  local was; was="$(grants "$1")"
  case " $PERM_WAS " in *" $1="*) ;; *) PERM_WAS="$PERM_WAS $1=$was" ;; esac
  if [ "$2" = true ]; then adb shell pm grant app.tileshell "android.permission.$1"; else adb shell pm revoke app.tileshell "android.permission.$1"; fi
}
perm_restore() {
  local e
  for e in $PERM_WAS; do
    if [ "${e#*=}" = true ]; then adb shell pm grant app.tileshell "android.permission.${e%%=*}"; else adb shell pm revoke app.tileshell "android.permission.${e%%=*}"; fi
    assert_eq "restore: ${e%%=*} as found" "${e#*=}" "$(grants "${e%%=*}")"
  done
}
grants() { adb shell dumpsys package app.tileshell | grep -E "android.permission.$1: granted=" | head -1 | sed 's/.*granted=\([a-z]*\).*/\1/' | tr -d '\r'; }
photos_start() { adb shell am start -n app.tileshell/.photos.PhotosActivity "$@" >/dev/null; sleep 2; }

PUSHED=""
# media_push <dir on device> <file>...: pushes each file; the i-th file pushed in a script is i minutes older than the first.
PUSH_N=0
media_census() { CENSUS_IMG="$(img_count)"; CENSUS_VID="$(vid_count)"; MEDIA_MARK="$(adb shell date +%s | tr -d '\r')"; }
media_push() {
  local dir="$1" f stamp; shift
  adb shell mkdir -p "$dir"
  for f in "$@"; do
    adb push "$FIX/$f" "$dir/$f" >/dev/null
    stamp="$(date -d "@$(( $(date +%s) - 600 - PUSH_N * 60 ))" +%Y%m%d%H%M.%S)"
    adb shell touch -m -t "$stamp" "$dir/$f"
    PUSHED="$PUSHED $dir/$f"
    PUSH_N=$((PUSH_N + 1))
  done
}
media_clean() {
  local f id
  for f in $PUSHED; do adb shell rm -f "$f"; done
  # Every row the shell itself made since the census (copies, trims).
  for coll in images video; do
    for id in $(adb shell "content query --uri content://media/external/$coll/media --projection _id:owner_package_name:date_added" | tr -d '\r' | sed -n 's/.*_id=\([0-9]*\), owner_package_name=app.tileshell, date_added=\([0-9]*\).*/\1:\2/p'); do
      if [ "${id#*:}" -ge "$MEDIA_MARK" ]; then adb shell content delete --uri "content://media/external/$coll/media/${id%%:*}" >/dev/null; fi
    done
  done
  scan; sleep 2
  assert_eq "restore: images back to the census" "$CENSUS_IMG" "$(img_count)"
  assert_eq "restore: videos back to the census" "$CENSUS_VID" "$(vid_count)"
}
no_crash() { assert_eq "no crash of the shell in this run" "" "$(adb logcat -d -t "$1" -s AndroidRuntime | grep -F 'app.tileshell' | head -3)"; }
px() { python3 "$PDEV_HERE/ppx.py" "$1" "$2" "$3"; }
assert_rgb() { # name expected actual tolerance
  if [ "$(python3 "$PDEV_HERE/ppx.py" near "$2" "${3:-0,0,0}" "$4")" = yes ] && [ -n "$3" ]; then _verdict PASS "$1" "$3 within $4 of $2"; else _verdict FAIL "$1" "expected $2 +/- $4 got [$3]"; fi
}
DRV_RUNNER="app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner"
# The phase 05 gesture driver's timed script ("tap x y; sleep 60; tap x y").
gesture() { adb shell am instrument -r -w -e op script -e script "\"$1\"" "$DRV_RUNNER" > "$ROW_DIR/.gesture.txt" 2>&1; grep -q 'gesture.ok=true' "$ROW_DIR/.gesture.txt"; }
motion_field() { # slice name field -> the last matching [motion] line's field value
  echo "$1" | grep -F "[motion] $2 " | tail -1 | grep -oE "$3=[0-9.]+" | cut -d= -f2
}
# The six flat-colour fixtures in the gate's two folders, and their ids in push order (qa-photo-0 the newest).
push_six() {
  media_push /sdcard/DCIM/Camera qa-photo-0.png qa-photo-1.png qa-photo-2.png
  media_push /sdcard/Pictures/QA-Album qa-photo-3.png qa-photo-4.png qa-photo-5.png
  scan; sleep 2
  IDS=""; for i in 0 1 2; do IDS="$IDS $(img_id DCIM/Camera/ qa-photo-$i.png)"; done; for i in 3 4 5; do IDS="$IDS $(img_id Pictures/QA-Album/ qa-photo-$i.png)"; done; IDS="${IDS# }"
}

# ---- the editor (build task 5, second half). The editor runs in :photosedit; its ring is read through its dump service,
# which lives while EditActivity does — so every read below happens with the editor still on screen.
EDIT_RING="app.tileshell/.photos.PhotosEditDumpService"
# The gate's expected values, computed from the phase doc's own matrices (the lead's script, read only).
EE="${EDIT_EXPECT:-/home/jeremyking/projects/metro-launcher-p17/docs/plan/qa/phase-17/scripts/edit_expect.py}"
E="" # the editor's latest dump
edump() { E="$ROW_DIR/e.xml"; dump_ui "$E"; }
etap() { edump; tap_node "$E" "$1"; sleep "${2:-1}"; }
# open_editor <image id>: from Photos' collection through the viewer's Edit and the Edit sheet.
open_editor() {
  photos_start
  # An older picture is below the fold: the collection is scrolled to its tile (run 1 of C3 tapped nothing for one).
  scroll_to_node "$ROW_DIR/c.xml" "photos_item:$1" 12; tap_node "$ROW_DIR/c.xml" "photos_item:$1"; sleep 2
  dump_ui "$ROW_DIR/v.xml"; tap_node "$ROW_DIR/v.xml" viewer_edit; sleep 1
  dump_ui "$ROW_DIR/s.xml"; tap_node "$ROW_DIR/s.xml" edit_sheet_editor; sleep 3
  edump
}
# open_tool <straighten|light|colour|filters|redeye>: from the strip's More.
open_tool() { etap edit_more; etap "edit_tool:$1"; edump; }
# edit_save: taps Save a copy and waits for the result's status; sets ESLICE (the :photosedit ring since the tap),
# COPY_URI and COPY_ROW (empty when no copy was made).
edit_save() {
  local i
  EMARK="$(ring_mark)"
  etap edit_save 1
  for i in $(seq 1 20); do edump; [ "$(has_node "$E" edit_status)" = yes ] && break; sleep 1; done
  ESTATUS="$(node_text "$E" edit_status)"
  ESLICE="$(ring_since "$EMARK" "$EDIT_RING")"
  COPY_URI="$(echo "$ESLICE" | grep -F '[photosapp] edit ' | grep -oE 'content://media/[a-z_]+/images/media/[0-9]+' | tail -1)"
  COPY_ROW=""
  [ -n "$COPY_URI" ] && COPY_ROW="$(adb shell content query --uri "$COPY_URI" --projection _display_name:relative_path:is_pending:width:height:mime_type:datetaken:owner_package_name | tr -d '\r')"
}
copy_field() { echo "$COPY_ROW" | sed -n "s/.*[ ,]$1=\([^,]*\).*/\1/p" | head -1; }
# copy_pull <local name>: pulls the copy's file; prints the local path.
copy_pull() {
  local dst="$ROW_DIR/$1.$(copy_field _display_name | sed 's/.*\.//')"
  adb pull "/sdcard/$(copy_field relative_path)$(copy_field _display_name)" "$dst" >/dev/null 2>&1
  echo "$dst"
}
img_size() { python3 -c "from PIL import Image; import sys; im = Image.open(sys.argv[1]); print('%dx%d' % im.size)" "$1"; }
differs16() { python3 -c "import sys; a=[int(v) for v in sys.argv[1].split(',')]; b=[int(v) for v in sys.argv[2].split(',')]; print('yes' if max(abs(x-y) for x,y in zip(a,b)) >= 16 else 'no')" "$1" "$2"; }
# colour_copy <label> <tool id as the line names it> <expected tool for edit_expect> <original r,g,b> <original folder>:
# the assertions every colour tool's copy must pass (E6's form).
colour_copy() {
  local label="$1" tool="$2" expect orig="$4" f got
  expect="$(python3 "$EE" pixel "$3" "$orig")"
  assert_contains "$label: the op's line in the :photosedit ring" "[photosapp] edit $tool -> content://media/" "$ESLICE"
  assert_ne "$label: a new row" "" "$COPY_ROW"
  assert_eq "$label: the copy is published (is_pending 0)" "0" "$(copy_field is_pending)"
  assert_eq "$label: the copy is in the original's folder" "$5" "$(copy_field relative_path)"
  assert_eq "$label: a PNG original gives a PNG copy, same size" "image/png 640 480" "$(copy_field mime_type) $(copy_field width) $(copy_field height)"
  assert_eq "$label: the copy is the shell's own row" "app.tileshell" "$(copy_field owner_package_name)"
  f="$(copy_pull "$label")"; got="$(px "$f" 320 240)"
  record "$label: expected (edit_expect.py pixel $3 $orig) / got" "$expect / $got"
  assert_rgb "$label: the centre pixel is the doc's matrix result" "$expect" "$got" 4
  assert_eq "$label: it differs from the original by >= 16 on a channel" "yes" "$(differs16 "$orig" "$got")"
}
orig_md5() { adb shell md5sum "$@" | awk '{print $1}' | xargs; }
