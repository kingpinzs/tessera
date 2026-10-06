# The first lines of every dev-living device script (sourced): the device, this worktree's APK, the shared floor
# (lib.sh), the Camera's helpers (dev-camera's cam.sh: gdump, the row and count readers, the camera's open and wait, the
# microphone's off and on), the lock FIRST, then this build when the device holds another (never with -g).
export ANDROID_SERIAL=emulator-5554
LIVING_HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WT="$(cd "$LIVING_HERE/../../../../../.." && pwd)"
export TILESHELL_APK="$WT/app/build/outputs/apk/debug/app-debug.apk"
. "$LIVING_HERE/lib.sh"; . "$LIVING_HERE/../../dev-camera/scripts/cam.sh"
STAMP_FILES="$LIVING_HERE/head.sh $LIVING_HERE/../../dev-camera/scripts/cam.sh $LIVING_HERE/frame_diff.py $LIVING_HERE/make_living_fixtures.py"
take_device_lock
install_mine; ensure_camera_grant
# An earlier run's folder is kept under its own name (evidence is renamed, never deleted).
keep_earlier() { # row id
  local d="$QA/$1" n=1
  [ -d "$d" ] || return 0
  while [ -e "$d.run$n" ]; do n=$((n + 1)); done
  mv "$d" "$d.run$n"
}
top() { adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity' | grep -oE '[a-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | tr -d '\r'; }
grants() { adb shell dumpsys package app.tileshell | grep -E "android.permission.$1: granted=" | head -1 | sed 's/.*granted=\([a-z]*\).*/\1/' | tr -d '\r'; }
# The ids of the nodes whose resource-id starts with a prefix, space-separated ("photos_living:" -> "31").
ids_of() { # dump.xml prefix
  grep -o "resource-id=\"$2[^\"]*\"" "$1" | sed "s/resource-id=\"$2//; s/\"//" | sort -u | xargs
}
launcher_pid() { adb shell pidof app.tileshell | tr -d '\r'; }
# The [photosapp] living lines of the launcher's ring since a mark.
living_lines() { ring_since "$1" launcher | grep -F '[photosapp] living ' | sed 's/.*\[photosapp\] //'; }
# A hold at a point for a time (ms), in the background: "x y ms". The caller waits for it (HOLD_PID).
hold_start() { adb shell input swipe "$1" "$2" "$1" "$2" "$3" & HOLD_PID=$!; }
frame_diff() { python3 "$LIVING_HERE/frame_diff.py" "$@"; }
scan() { adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null; }
# A row's id by its folder AND name ("Pictures/living-qa/" "living-broken.jpg").
img_id() { adb shell "content query --uri content://media/external/images/media --projection _id:_display_name --where \"relative_path='$1' AND _display_name='$2'\"" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1 | tr -d '\r'; }
