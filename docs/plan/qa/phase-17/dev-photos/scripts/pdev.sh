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
