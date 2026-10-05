#!/usr/bin/env bash
# Phase 17's driver floor (build task 16 (a); the Acceptance preamble's "Round 3 floor"), sourced by every driver AFTER
# lib.sh (phase 03's floor, symlinked here: row_begin / row_end, the asserts, ring_mark / ring_since / ring_save, record,
# ensure_start, type_request, wake_device, apk_matches). Everything here drives the real shell on the emulator; nothing
# is simulated. No row uses the microphone or the host's audio: a step that records video revokes RECORD_AUDIO for its
# span (mic_off / mic_on).
#
# Copies, because the files they live in cannot be sourced without their rows' own state (phase 16's reason):
#   gdump, q, top_activity, perm_granted, device_ms, absent_in     qa/phase-16/scripts/p16.sh
# New here: RINGS and a c6 that saves every ring, ring_of, media_census / media_up / media_down, mic_off / mic_on,
# px (a screencap's pixel), egress_guard_on / egress_guard_off (C-29).
export ANDROID_SERIAL=emulator-5554

P17="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
P01S="$(cd "$P17/../phase-01/scripts" && pwd)"
P02S="$(cd "$P17/../phase-02/scripts" && pwd)"
P03S="$(cd "$P17/../phase-03/scripts" && pwd)"
BASELINE="$P17/baseline_layout.json"
GEN="$P17/gen/media"          # generated fixtures (git-ignored): make_photos.py <dir> editor; make_videos.sh <dir>
STAMP_FILES="$P17/scripts/p17.sh"
DRV_RUNNER="app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner"

PHOTOS_ACTIVITY="app.tileshell/.photos.PhotosActivity"
CAMERA_ACTIVITY="app.tileshell/.camera.CameraActivity"
VIDEO_ACTIVITY="app.tileshell/.video.VideoActivity"
PLAYER_ACTIVITY="app.tileshell/.video.PlayerActivity"
CAPTURE_ACTIVITY="app.tileshell/.camera.CaptureActivity"
# The four rings (Decisions "processes"; r3 D8, D16): the launcher's, and each other process's through its dump service.
CAMERA_RING="app.tileshell/.camera.CameraDumpService"
VIDEO_RING="app.tileshell/.video.VideoDumpService"
EDIT_RING="app.tileshell/.photos.PhotosEditDumpService"
RINGS=(launcher "$CAMERA_RING" "$VIDEO_RING" "$EDIT_RING")

# layout_json, layout_save, layout_restore (the verified restore, C-3).
. "$P02S/layout.sh"

# ---------------------------------------------------------------- the device shell

# One command, one string (a URI with `&` or a quoted where-clause must reach the device shell as ONE quoted string);
# stdin is /dev/null so a `while read` loop around it keeps its input.
q() { adb shell "$1" 2>&1 </dev/null | tr -d '\r'; }

top_activity() {
  adb shell dumpsys activity activities | grep -m1 'topResumedActivity' | tr -d '\r' | sed -E 's/.* u0 ([^ ]+) .*/\1/'
}

perm_granted() { # permission short name -> true / false
  adb shell dumpsys package app.tileshell | tr -d '\r' | grep -m1 "android.permission.$1: granted" | sed -E 's/.*granted=([a-z]+).*/\1/'
}

device_ms() { adb shell date +%s%3N | tr -d '\r'; }

# A screen that never idles — the viewfinder, a playing video — is dumped through phase 05's gesture driver with
# setWaitForIdleTimeout(0) (C-10). A new file per attempt; after 20 tries the plain uiautomator dump is the fallback.
gdump() { # out.xml
  local out="$1" i name
  : > "$out.drv"
  for i in $(seq 1 20); do
    name="p17_$(date +%s%N)_$i.xml"
    adb shell am instrument -r -w -e op dump -e out "/sdcard/Download/$name" "$DRV_RUNNER" >> "$out.drv" 2>&1
    adb shell cat "/sdcard/Download/$name" > "$out" 2>/dev/null
    adb shell rm -f "/sdcard/Download/$name" >/dev/null 2>&1
    if grep -q '<node' "$out"; then
      grep -o 'gesture.dump.windows=.*' "$out.drv" | tail -1 | tr -d '\r' > "$out.windows"
      return 0
    fi
    echo "(attempt $i read no nodes from $name)" >> "$out.drv"
    sleep 0.25
  done
  echo "(falling back to uiautomator dump)" >> "$out.drv"
  if adb shell uiautomator dump /sdcard/Download/p17_fallback.xml >/dev/null 2>&1; then
    adb shell cat /sdcard/Download/p17_fallback.xml > "$out" 2>/dev/null
    adb shell rm -f /sdcard/Download/p17_fallback.xml >/dev/null 2>&1
    if grep -q '<node' "$out"; then : > "$out.windows"; return 0; fi
  fi
  echo "(dump failed)" > "$out"
  return 1
}

# An absence check that cannot pass on an unreadable ring: the slice must hold a ring line (a `wall=` stamp) first.
absent_in() { # name needle slice
  if printf '%s\n' "$3" | grep -q 'wall='; then
    assert_absent "$1" "$2" "$3"
  else
    _verdict FAIL "$1" "the ring slice is empty or unreadable, so the absence proves nothing"
  fi
}

# Every ring this phase has, saved (r3 V7): a ring whose process is not running prints nothing and saves nothing.
rings_save() {
  local r
  for r in "${RINGS[@]}"; do ring_save "$r"; done
}

# C-6 since r3 V7: after any launch — keep EVERY ring (the :camera, :video and :photosedit rings die with the
# force-stop), force-stop, Home, wait for Start. Follow it with ensure_start.
c6() {
  rings_save
  adb shell am force-stop app.tileshell
  sleep 1
  adb shell input keyevent KEYCODE_HOME
  sleep 4
}

# The pixel of a saved screencap as "r,g,b" (px shot.png x y), and whether two "r,g,b" values are within a tolerance.
px() { python3 - "$1" "$2" "$3" <<'PY'
import sys, zlib, struct
path, x, y = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
d = open(path, 'rb').read()
assert d[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
pos, idat, w = 8, b'', 0
while pos < len(d):
    n, tag = struct.unpack('>I4s', d[pos:pos + 8]); body = d[pos + 8:pos + 8 + n]; pos += 12 + n
    if tag == b'IHDR': w, h, depth, ctype = struct.unpack('>IIBB', body[:10])
    elif tag == b'IDAT': idat += body
bpp = {2: 3, 6: 4}[ctype]
raw = zlib.decompress(idat); stride = w * bpp + 1
prev = bytearray(w * bpp)
for row in range(y + 1):
    line = bytearray(raw[row * stride + 1:(row + 1) * stride]); f = raw[row * stride]
    for i in range(len(line)):
        a = line[i - bpp] if i >= bpp else 0; b = prev[i]; c = prev[i - bpp] if i >= bpp else 0
        if f == 1: line[i] = (line[i] + a) & 255
        elif f == 2: line[i] = (line[i] + b) & 255
        elif f == 3: line[i] = (line[i] + ((a + b) >> 1)) & 255
        elif f == 4:
            p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
    prev = line
print('%d,%d,%d' % tuple(prev[x * bpp:x * bpp + 3]))
PY
}
assert_rgb() { # name expected(r,g,b) actual(r,g,b) tolerance
  local ok
  ok="$(python3 -c 'import sys
e=[int(v) for v in sys.argv[1].split(",")]; a=[int(v) for v in sys.argv[2].split(",")] if sys.argv[2].count(",")==2 else None
print("yes" if a and all(abs(x-y)<=int(sys.argv[3]) for x,y in zip(e,a)) else "no")' "$2" "$3" "$4")"
  if [ "$ok" = yes ]; then _verdict PASS "$1" "= $3 (expected $2 ± $4)"; else _verdict FAIL "$1" "expected [$2 ± $4] got [$3]"; fi
}

# ---------------------------------------------------------------- the microphone (r3 D12 / V4)

mic_off() { adb shell pm revoke app.tileshell android.permission.RECORD_AUDIO; assert_eq "RECORD_AUDIO revoked for this span" "false" "$(perm_granted RECORD_AUDIO)"; }
mic_on() { adb shell pm grant app.tileshell android.permission.RECORD_AUDIO; assert_eq "RECORD_AUDIO granted back" "true" "$(perm_granted RECORD_AUDIO)"; }

# ---------------------------------------------------------------- media fixtures (r3 V9; RV12)

media_scan() { adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null 2>&1; sleep 2; }
media_ids() { # images | video  -> the ids, one per line, sorted
  q "content query --uri content://media/external/$1/media --projection _id" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n
}
media_count() { media_ids "$1" | grep -c . ; }
# The id of a file by its display name (the newest row of that name).
media_id() { # images|video name [relative_path]
  q "content query --uri content://media/external/$1/media --projection _id:_display_name:relative_path --sort '_id DESC'" | grep -F "_display_name=$2, relative_path=${3:-}" | head -1 | sed -n 's/.*_id=\([0-9]*\),.*/\1/p'
}

# Where each fixture goes: qa-photo-0..2 into DCIM/Camera, qa-photo-3..5 and the editor fixtures into Pictures/QA-Album
# (the two folders of the Fixtures paragraph), every video and its .srt into Movies.
_media_dest() {
  case "$1" in
    qa-photo-[0-2].png) echo /sdcard/DCIM/Camera ;;
    *.png|*.jpg|*.heic|*.dng) echo /sdcard/Pictures/QA-Album ;;
    *) echo /sdcard/Movies ;;
  esac
}

# The emulator already holds other phases' media (phase 01's qa-photo-0..5 in Pictures/, earlier rows' recordings): the
# census is that, and a row names its own fixtures by file AND folder, or by id.
# media_up <names…>: a census of the image and video ids, then ONLY the named files pushed from $GEN, each given its
# own minute of mtime — the i-th name is i minutes older than the first, so qa-photo-i (named in order) is i minutes
# older than qa-photo-0 and "newest first" / "next" are defined — then a scan. With no names it is the census alone.
MEDIA_PUSHED=()
MEDIA_MARK=""
media_up() {
  local name dest i=0 base stamp
  [ -n "${ROW_DIR:-}" ] || { echo "media_up: row_begin has not run" >&2; return 1; }
  media_ids images > "$ROW_DIR/census-images.txt"; media_ids video > "$ROW_DIR/census-video.txt"
  CENSUS_IMAGES="$(grep -c . "$ROW_DIR/census-images.txt")"; CENSUS_VIDEO="$(grep -c . "$ROW_DIR/census-video.txt")"
  MEDIA_MARK="$(device_ms)"
  MEDIA_PUSHED=()
  base="$(adb shell date +%s | tr -d '\r')"
  for name in "$@"; do
    [ -f "$GEN/$name" ] || { _verdict FAIL "media_up fixture $name" "missing in $GEN (run make_photos.py <dir> editor and make_videos.sh <dir>)"; continue; }
    dest="$(_media_dest "$name")"
    adb shell mkdir -p "$dest"
    adb push "$GEN/$name" "$dest/$name" >/dev/null
    stamp="$(date -d "@$((base - 3600 - i * 60))" +%Y%m%d%H%M.%S)"
    adb shell touch -m -t "$stamp" "$dest/$name"
    MEDIA_PUSHED+=("$dest/$name")
    i=$((i + 1))
  done
  media_scan
  record "census" "images=$CENSUS_IMAGES video=$CENSUS_VIDEO pushed=${#MEDIA_PUSHED[@]}"
}

# media_down: everything the row pushed, plus every row the SHELL made since the MARK (captures, edits, trims), removed;
# a scan; and the counts asserted equal to the census (RV12).
media_down() {
  local f since
  [ -n "$MEDIA_MARK" ] || { echo "media_down: media_up has not run" >&2; return 1; }
  for f in "${MEDIA_PUSHED[@]}"; do adb shell rm -f "$f"; done
  since="$((MEDIA_MARK / 1000))"
  for kind in images video; do
    q "content delete --uri content://media/external/$kind/media --where \"owner_package_name='app.tileshell' AND date_added>=$since\"" >/dev/null
  done
  adb shell rmdir /sdcard/Pictures/QA-Album >/dev/null 2>&1
  media_scan
  # The same ids as the census, not only the same count: a row swapped for another would keep the count.
  assert_eq "media_down: the images are the census's ($CENSUS_IMAGES)" "$(tr '\n' ' ' < "$ROW_DIR/census-images.txt")" "$(media_ids images | tr '\n' ' ')"
  assert_eq "media_down: the videos are the census's ($CENSUS_VIDEO)" "$(tr '\n' ' ' < "$ROW_DIR/census-video.txt")" "$(media_ids video | tr '\n' ' ')"
  MEDIA_PUSHED=(); MEDIA_MARK=""
}

# ---------------------------------------------------------------- the egress guard (C-29; r3 V1, V2)

# Inside the guard the shell's uid can reach 10.0.2.2 (the host's fixtures) and nothing else: any other packet is
# rejected AND counted, and egress_guard_off fails the row if the count is not zero. The launcher's own weather refresh
# on every start would be such a packet, so the location grants are revoked for the span (it then ends before any request).
APP_UID=""
egress_guard_on() {
  adb shell pm revoke app.tileshell android.permission.ACCESS_COARSE_LOCATION
  adb shell pm revoke app.tileshell android.permission.ACCESS_FINE_LOCATION 2>/dev/null
  adb root >/dev/null 2>&1; adb wait-for-device
  APP_UID="$(adb shell cmd package list packages -U app.tileshell | tr -d '\r' | sed -n 's/^package:app.tileshell uid://p' | head -1)"
  case "$APP_UID" in ''|*[!0-9]*) _verdict FAIL "egress guard: the shell's uid" "not numeric: [$APP_UID]"; return 1 ;; esac
  if [ "$APP_UID" -lt 10000 ]; then _verdict FAIL "egress guard: the shell's uid" "$APP_UID is not an app uid"; return 1; fi
  adb shell iptables -I OUTPUT -m owner --uid-owner "$APP_UID" ! -d 10.0.2.2 -j REJECT
  assert_contains "egress guard on for uid $APP_UID" "owner UID match $APP_UID" "$(adb shell iptables -L OUTPUT -v -n | tr -d '\r')"
}
egress_guard_count() { # the packets the guard's rule has rejected
  adb shell iptables -L OUTPUT -v -n | tr -d '\r' | awk -v u="owner UID match $APP_UID" 'index($0, u) && /REJECT/ && /!10\.0\.2\.2/ { print $1; exit }'
}
egress_guard_off() {
  local n
  n="$(egress_guard_count)"
  assert_eq "egress guard: 0 packets from the shell to anything but 10.0.2.2" "0" "$n"
  adb shell iptables -D OUTPUT -m owner --uid-owner "$APP_UID" ! -d 10.0.2.2 -j REJECT
  assert_absent "egress guard rule removed" "owner UID match $APP_UID" "$(adb shell iptables -L OUTPUT -v -n | tr -d '\r')"
  adb unroot >/dev/null 2>&1; adb wait-for-device
  adb shell pm grant app.tileshell android.permission.ACCESS_COARSE_LOCATION
  adb shell pm grant app.tileshell android.permission.ACCESS_FINE_LOCATION 2>/dev/null
  assert_eq "location granted back" "true" "$(perm_granted ACCESS_COARSE_LOCATION)"
}
