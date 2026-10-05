#!/usr/bin/env bash
# Phase 17, Movies & TV: what every development-proof script of this folder sources FIRST. Development proof, not the
# gate (the lead writes the gate's rows). It takes the device lock, installs this worktree's debug APK when the shared
# emulator holds another build, and defines the helpers the scripts share. Evidence lands in ../<ROW>/ (never committed).
export ANDROID_SERIAL=emulator-5554
V17_HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
V17_TREE="$(cd "$V17_HERE/../../../../../.." && pwd)"
export TILESHELL_APK="$V17_TREE/app/build/outputs/apk/debug/app-debug.apk"
. "$V17_HERE/lib.sh"
take_device_lock
[ -f "$APK" ] || { echo "no APK at $APK" >&2; exit 4; }
[ "$(apk_matches | cut -c1-3)" = "yes" ] || { adb install -r "$APK" >/dev/null || { echo "install failed" >&2; exit 4; }; }

VSVC=app.tileshell/.video.VideoDumpService
RINGS="launcher $VSVC"
DRV_RUNNER="app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner"
# Where make_fixtures.sh writes (a scratch folder, never the repo).
FIX="${V17_FIX:-/tmp/claude-1000/-home-jeremyking/b5b8c63b-5d38-49e9-84f3-de917a96acb2/scratchpad/video-fixtures}"
DEVICE_DIR=/sdcard/Movies/QA-Video

top() { adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity' | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | tr -d '\r'; }
vring() { ring_since "$1" "$VSVC"; }                      # the :video ring's lines since a mark
vline() { vring "$1" | grep -F -- "$2" | tail -1; }        # the last such line holding a text
wall_of() { sed -n 's/.*wall=\([0-9]*\).*/\1/p' <<<"$1" | tail -1; }
device_ms() { adb shell date +%s%3N | tr -d '\r'; }

# A dump of a page that never idles (the player): phase 05's gesture driver with no idle wait (phase 15's gdump form).
gdump() { # out.xml
  local out="$1" i name
  for i in 1 2 3 4 5 6; do
    name="v17_$(date +%s%N)_$i.xml"
    adb shell am instrument -r -w -e op dump -e out "/sdcard/Download/$name" "$DRV_RUNNER" >/dev/null 2>&1
    adb shell cat "/sdcard/Download/$name" > "$out" 2>/dev/null
    adb shell rm -f "/sdcard/Download/$name" >/dev/null 2>&1
    grep -q '<node' "$out" && return 0
    sleep 0.3
  done
  echo "(dump failed)" > "$out"; return 1
}

# The colour of one pixel of a screenshot, "r g b".
pixel() { # png x y
  python3 - "$1" "$2" "$3" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB")
print(*im.getpixel((int(sys.argv[2]), int(sys.argv[3]))))
PY
}
# PASS when every channel is within tol of the expected colour.
assert_colour() { # name "r g b" "r g b" tol
  local ok
  ok="$(python3 -c 'import sys
e=[int(v) for v in sys.argv[1].split()]; a=[int(v) for v in sys.argv[2].split()] if sys.argv[2].strip() else []
print("yes" if len(a)==3 and all(abs(x-y)<=int(sys.argv[3]) for x,y in zip(e,a)) else "no")' "$2" "$3" "$4")"
  if [ "$ok" = yes ]; then _verdict PASS "$1" "($3) within $4 of ($2)"; else _verdict FAIL "$1" "expected ($2) ± $4, got ($3)"; fi
}
# A node's bounds in epx (px ÷ 3 on this AVD): "left top right bottom", one decimal.
epx() { # dump.xml resource-id
  local b; b="$(bounds "$1" "$2")"; [ -n "$b" ] || { echo ""; return; }
  # shellcheck disable=SC2086
  python3 -c 'import sys; print(" ".join("%.1f" % (int(v)/3) for v in sys.argv[1:]))' $b
}
centre_px() { # dump.xml resource-id -> "x y"
  local b; b="$(bounds "$1" "$2")"; [ -n "$b" ] || { echo ""; return; }
  # shellcheck disable=SC2086
  set -- $b; echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}

scan_media() { adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null 2>&1; }
video_id() { # display name -> MediaStore id (empty when absent)
  adb shell "content query --uri content://media/external/video/media --projection _id --where \"_display_name='$1'\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r'
}
push_videos() { # names… (from $FIX) into $DEVICE_DIR, each with its own minute, then a scan
  local n i=0
  adb shell mkdir -p "$DEVICE_DIR"
  for n in "$@"; do
    adb push "$FIX/$n" "$DEVICE_DIR/$n" >/dev/null || { echo "push $n failed" >&2; return 1; }
    i=$((i + 1))
  done
  scan_media; sleep 2
}
remove_videos() { # everything this folder's scripts pushed
  adb shell rm -rf "$DEVICE_DIR"
  scan_media; sleep 2
}
# PASS when each number of the actual list is within tol of the expected one (epx read from px: 1 px = 0.33 epx).
assert_near() { # name "expected…" "actual…" tol
  local ok
  ok="$(python3 -c 'import sys
e=sys.argv[1].split(); a=sys.argv[2].split()
print("yes" if len(e)==len(a) and len(a)>0 and all(abs(float(x)-float(y))<=float(sys.argv[3]) for x,y in zip(e,a)) else "no")' "$2" "$3" "$4")"
  if [ "$ok" = yes ]; then _verdict PASS "$1" "[$3] within $4 of [$2]"; else _verdict FAIL "$1" "expected [$2] ± $4, got [$3]"; fi
}
# The Videos grant: the proof scripts need it (provision.sh grants it on a provisioned device). Granted here when it is
# missing, and put back as it was by videos_grant_restore.
VIDEOS_WAS=""
videos_grant() {
  VIDEOS_WAS="$(adb shell dumpsys package app.tileshell | grep -m1 'android.permission.READ_MEDIA_VIDEO: granted=' | sed 's/.*granted=\([a-z]*\).*/\1/' | tr -d '\r')"
  [ "$VIDEOS_WAS" = true ] || adb shell pm grant app.tileshell android.permission.READ_MEDIA_VIDEO
}
videos_grant_restore() {
  [ "$VIDEOS_WAS" = false ] && adb shell pm revoke app.tileshell android.permission.READ_MEDIA_VIDEO
  return 0
}
video_count() { adb shell content query --uri content://media/external/video/media --projection _id | grep -c '_id=' ; }

# The catalogue fixture (../../scripts/catalogue_server.py). The doc fixes port 8090, but on this PC 8090 is held by
# another service of the owner's (a container publishing 0.0.0.0:8090), so these development scripts run the fixture on
# V17_PORT (8091) and every line that names the fixture's address reads 10.0.2.2:8091 here.
V17_PORT="${V17_PORT:-8091}"
FIXTURE_URL="http://10.0.2.2:$V17_PORT"
fixture_up() {
  FIXTURE_LOG="$ROW_DIR/fixture.log"; : > "$FIXTURE_LOG"
  python3 "$V17_HERE/../../scripts/catalogue_server.py" --port "$V17_PORT" --video "$FIX/qa-steps.mp4" --log "$FIXTURE_LOG" >/dev/null 2>"$ROW_DIR/fixture.err" &
  echo $! > "$ROW_DIR/fixture.pid"
  local i; for i in $(seq 1 30); do curl -s -o /dev/null "http://127.0.0.1:$V17_PORT/ready" && return 0; sleep 0.2; done
  echo "the fixture did not start: $(cat "$ROW_DIR/fixture.err")" >&2; return 1
}
fixture_down() { [ -f "$ROW_DIR/fixture.pid" ] && kill "$(cat "$ROW_DIR/fixture.pid")" 2>/dev/null; rm -f "$ROW_DIR/fixture.pid"; return 0; }
fixture_lines() { wc -l < "$FIXTURE_LOG" | tr -d ' '; }             # the log's length, as an offset
fixture_since() { tail -n +"$(( $1 + 1 ))" "$FIXTURE_LOG"; }        # the lines after an offset

airplane() { # enable|disable — and, on disable, wait until the host answers again
  adb shell cmd connectivity airplane-mode "$1" >/dev/null
  if [ "$1" = disable ]; then
    local i; for i in $(seq 1 40); do adb shell ping -c 1 -W 1 10.0.2.2 >/dev/null 2>&1 && return 0; sleep 0.5; done
    echo "the network did not come back after airplane mode" >&2; return 1
  fi
  sleep 1.5
}
# Wait for a line in the :video ring since a mark (up to n tenths of a second); prints it.
await_vline() { # mark text [tenths]
  local i line=""
  for i in $(seq 1 "${3:-80}"); do line="$(vline "$1" "$2")"; [ -n "$line" ] && break; sleep 0.1; done
  printf '%s' "$line"
}
no_crash() { adb logcat -d -t 1500 -s AndroidRuntime | grep -F 'app.tileshell' | head -3; }
