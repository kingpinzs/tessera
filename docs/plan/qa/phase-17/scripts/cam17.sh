#!/usr/bin/env bash
# Phase 17 — the Camera rows' own include, sourced AFTER lib.sh and p17.sh by e7.sh, e8.sh, e9.sh, e15.sh,
# e19_camera.sh, e23_camera.sh and edge_camera.sh. Everything here drives the real shell on emulator-5554; the working
# steps are the Camera builder's (qa/phase-17/dev-camera/scripts/cam.sh), re-cut for the gate's form: every ring read is
# from a MARK, every dump of the viewfinder goes through the gesture driver, and nothing is graded here — the rows assert.
STAMP_FILES="${STAMP_FILES:-} $P17/scripts/cam17.sh"
CAM_DEV="$P17/dev-camera/scripts"        # derive_modes.py, exif_read.py, zoom_ratio.py, dial_fit.py, motion_check.py
IMAGES="content://media/external/images/media"
VIDEOS="content://media/external/video/media"
TILES_PY="$P17/../phase-15/scripts/tiles.py"
# The gate build: the CLEAN build of phase-17 at bb154e06 — the three rounds of trust fixes, Living Images and the
# Camera's toast fix merged (355,589,093 bytes; the lead's word of 2026-10-05). Earlier rows carry c7336aca6b63d61b
# (e5e30678) or e8c26851363882da (25921fd7) in their logs: a kept run is re-read with GATE_APK_ID set to the id it ran on.
GATE_APK_ID="${GATE_APK_ID:-95b543037345b851}"
QAC=app.tileshell.testclient.qacapture
QAC_APK="$REPO/testapps/qa-capture/build/outputs/apk/debug/qa-capture-debug.apk"
# The second app of the forwarded-result leg (testapps/qa-capture-fwd).
QAF=app.tileshell.testclient.qacapturefwd
QAF_APK="$REPO/testapps/qa-capture-fwd/build/outputs/apk/debug/qa-capture-fwd-debug.apk"

# The lock is taken ONCE per process (as p17_photos.sh does): lib.sh's take_device_lock re-opens the lock file each time
# it is called, and row_begin calls it again after cam_install — which lets go of the lock for a moment, so a driver
# waiting in the lock's queue can take the device between two rows of edge_camera.sh.
take_device_lock() {
  [ -n "${CAM_LOCK_HELD:-}" ] && return 0
  exec 9>"$DEVICE_LOCK"
  if ! flock -n 9; then
    echo "another QA driver is already driving the device (lock $DEVICE_LOCK); refusing to start" >&2
    exit 3
  fi
  CAM_LOCK_HELD=1
}

# The lock, then this worktree's APK when the device holds another build (never -g).
cam_install() { # row-name
  take_device_lock
  mkdir -p "$QA/$1"
  if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
    adb install -r "$APK" > "$QA/$1/install.out" 2>&1 || { echo "$1: adb install -r of $APK failed:" >&2; cat "$QA/$1/install.out" >&2; exit 4; }
  fi
}
# The first assertions of every row: the build, and an awake device (C-25).
cam_preamble() {
  CRASH_T0="$(adb shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
  record "the installed APK id" "$(installed_apk_id) (built $(md5sum "$APK" | cut -c1-16), $(stat -c%s "$APK") bytes, $(git -C "$REPO" rev-parse --short HEAD))"
  assert_contains "the device holds this worktree's build" "yes" "$(apk_matches)"
  assert_eq "… and it is the gate build" "$GATE_APK_ID" "$(installed_apk_id)"
  assert_eq "wake" "Awake" "$(wake_device)"
}

# A dump of a page that never idles, which must hold a node of the package asked for: the driver sometimes reports
# only the system's own windows while the app's window is not yet in the accessibility tree (the builder's finding).
cgdump() { # out.xml [package]
  local out="$1" want="${2:-app.tileshell}" i
  for i in 1 2 3 4 5 6; do
    gdump "$out" || true
    grep -q "package=\"$want\"" "$out" && return 0
    sleep 0.4
  done
  return 1
}
node_attr() { # dump.xml resource-id attribute
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if 'resource-id="%s"' % sys.argv[2] in s:
        m = re.search(r'\b%s="([^"]*)"' % re.escape(sys.argv[3]), s)
        print(m.group(1) if m else ""); break
PY
}
ids_with() { # dump.xml prefix -> the ids' suffixes in dump order, space-separated
  grep -o "resource-id=\"$2[^\"]*\"" "$1" | sed "s/resource-id=\"$2//; s/\"\$//" | xargs
}
centre_px() { set -- $(bounds "$1" "$2"); [ -n "${4:-}" ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
centre_epx() { set -- $(bounds "$1" "$2"); [ -n "${4:-}" ] && python3 -c "print('%.1f %.1f' % (($1+$3)/6, ($2+$4)/6))"; }
size_epx() { set -- $(bounds "$1" "$2"); [ -n "${4:-}" ] && python3 -c "print('%.1f %.1f' % (($3-$1)/3, ($4-$2)/3))"; }
# name node expected-x expected-y tolerance: the node's centre in epx (px / 3 on this 1080-px screen).
chk_centre() {
  local got; got="$(centre_epx "$CHK_DUMP" "$2")"
  # shellcheck disable=SC2086
  set -- "$1" "$2" "$3" "$4" "$5" $got
  assert_within "$1 x" "$3" "${6:-none}" "$5"; assert_within "$1 y" "$4" "${7:-none}" "$5"
}

cam_since() { ring_since "$1" "$CAMERA_RING"; }
# The first line of the :camera slice that holds the needle, with its wall= stamp.
cam_line() { cam_since "$1" | grep -F -- "$2" | head -1; }
wall_of() { printf '%s\n' "$1" | grep -o 'wall=[0-9]*' | head -1 | cut -d= -f2; }
open_camera() { # [mode]
  if [ -n "${1:-}" ]; then adb shell am start -n "$CAMERA_ACTIVITY" --es mode "$1" >/dev/null; else adb shell am start -n "$CAMERA_ACTIVITY" >/dev/null; fi
  sleep 4
}
# The camera is open once the process's ring holds its `devices=` line since the mark (up to 20 s); prints the seconds.
wait_camera() { # mark
  local i
  for i in $(seq 1 20); do
    if cam_since "$1" | grep -q '\[camera\] devices='; then sleep 1; echo "$i"; return 0; fi
    sleep 1
  done
  echo timeout; return 1
}
# A fresh :camera process on the viewfinder: every ring kept, the shell stopped, Start, then the Camera. Sets OPEN_MARK.
fresh_camera() { # [mode]
  c6; ensure_start
  OPEN_MARK="$(ring_mark)"
  open_camera "${1:-}"
  record "camera ready after (s)" "$(wait_camera "$OPEN_MARK")"
}
media_camera() { adb shell dumpsys media.camera | tr -d '\r'; }

# Rows the shell owns, newest first: "_id|display_name|width|height|is_pending|size|relative_path|date_added|duration".
shell_rows() { # images | video
  local extra=""; [ "$1" = video ] && extra=":duration"
  q "content query --uri content://media/external/$1/media --projection _id:_display_name:width:height:is_pending:_size:relative_path:date_added$extra:owner_package_name --sort '_id DESC'" \
    | grep 'owner_package_name=app.tileshell' \
    | python3 -c '
import re, sys
for line in sys.stdin:
    f = dict(re.findall(r"(\w+)=([^,\n]*)", line))
    print("|".join(f.get(k, "") for k in ("_id", "_display_name", "width", "height", "is_pending", "_size", "relative_path", "date_added", "duration")))'
}
row_field() { echo "$1" | cut -d'|' -f"$2"; }   # 1 id 2 name 3 width 4 height 5 is_pending 6 size 7 relative_path 8 date_added 9 duration
# The shell's pending rows, any kind (the builder's read).
pending_rows() { q "content query --uri content://media/external/file --projection _id:is_pending:owner_package_name --where 'is_pending=1'" | grep -c 'owner_package_name=app.tileshell'; }
pull_dcim() { adb shell "cat '/sdcard/DCIM/Camera/$1'" > "$2"; }
streams() { ffprobe -v error -show_entries stream=codec_type -of csv=p=0 "$1" 2>/dev/null | grep -v '^$' | sort | uniq -c | xargs; }
stream_facts() { ffprobe -v error -show_streams "$1" 2>/dev/null | grep -E '^(codec_name|codec_type|width|height|r_frame_rate|rotation|duration|nb_frames)=' | xargs; }
# A crash of any of the shell's processes since CRASH_T0 (set by cam_preamble at the row's start, and by a sub-step at
# its own): read from logcat's CRASH buffer by time. (The builder's `logcat -t 800 -s AndroidRuntime` reads only the
# last 800 lines of the log, and missed the :camera crashes of 2026-10-05 16:26 and 16:35.)
crash_lines() {
  adb logcat -d -b crash -v threadtime -T "${CRASH_T0:-01-01 00:00:00.000}" 2>/dev/null | tr -d '\r' \
    | grep -A1 -F 'Process: app.tileshell' | grep -v '^--' | sed 's/^.*AndroidRuntime: //' | head -6 | tr '\n' ' ' | sed 's/ *$//'
}
no_crash() { assert_eq "${1:-no crash of the shell in logcat}" "" "$(crash_lines)"; }

# ---- the settings page ---------------------------------------------------------------------------------------------
# From the viewfinder: the settings page, dumped to <prefix>-set.xml (scrolled once when the node asked for is below).
open_settings() { # prefix [node that must be on screen]
  cgdump "$ROW_DIR/$1-vf.xml"; tap_node "$ROW_DIR/$1-vf.xml" camera_settings; sleep 1.5
  cgdump "$ROW_DIR/$1-set.xml"
  if [ -n "${2:-}" ] && [ "$(has_node "$ROW_DIR/$1-set.xml" "$2")" = no ]; then
    adb shell input swipe 540 1700 540 900 300; sleep 1; cgdump "$ROW_DIR/$1-set.xml"
  fi
}
close_settings() { adb shell input keyevent KEYCODE_BACK; sleep 2; }
# A toggle row set to on / off from the viewfinder (a tap only when it is not already there); ends on the viewfinder.
set_toggle() { # id on|off prefix
  local want; [ "$2" = on ] && want=true || want=false
  open_settings "$3" "camera_set:$1"
  if [ "$(node_attr "$ROW_DIR/$3-set.xml" "camera_set:$1" checked)" != "$want" ]; then
    tap_node "$ROW_DIR/$3-set.xml" "camera_set:$1"; sleep 1; cgdump "$ROW_DIR/$3-set.xml"
  fi
  TOGGLE_NOW="$(node_attr "$ROW_DIR/$3-set.xml" "camera_set:$1" checked)"
  close_settings
}
# A combo box's item chosen from the viewfinder; ends on the viewfinder. COMBO_NOW is the value shown after.
set_combo() { # id item-index prefix
  open_settings "$3" "camera_set:$1"
  tap_node "$ROW_DIR/$3-set.xml" "camera_set:$1"; sleep 1; cgdump "$ROW_DIR/$3-list.xml"
  tap_node "$ROW_DIR/$3-list.xml" "camera_set_item:$1:$2"; sleep 1; cgdump "$ROW_DIR/$3-after.xml"
  COMBO_NOW="$(node_text "$ROW_DIR/$3-after.xml" "camera_set_value:$1")"
  close_settings
}

# ---- the capture answer's caller (testapps/qa-capture) ------------------------------------------------------------
install_qac() {
  [ -f "$QAC_APK" ] || { _verdict FAIL "qa-capture is built" "missing $QAC_APK (./gradlew :testapps:qa-capture:assembleDebug --offline)"; return 1; }
  adb install -r "$QAC_APK" > "$ROW_DIR/qa-capture-install.out" 2>&1
  assert_contains "qa-capture installed for the row" "$QAC" "$(adb shell pm list packages "$QAC" | tr -d '\r' | grep -x "package:$QAC")"
}
uninstall_qac() {
  adb shell am force-stop "$QAC" >/dev/null 2>&1
  adb uninstall "$QAC" > /dev/null 2>&1
  assert_eq "qa-capture uninstalled" "" "$(adb shell pm list packages "$QAC" | tr -d '\r' | grep -x "package:$QAC")"
}
install_qaf() {
  [ -f "$QAF_APK" ] || { _verdict FAIL "qa-capture-fwd is built" "missing $QAF_APK (./gradlew :testapps:qa-capture-fwd:assembleDebug --offline)"; return 1; }
  adb install -r "$QAF_APK" > "$ROW_DIR/qa-capture-fwd-install.out" 2>&1
  assert_contains "qa-capture-fwd installed for the row" "$QAF" "$(adb shell pm list packages "$QAF" | tr -d '\r' | grep -x "package:$QAF")"
}
uninstall_qaf() {
  adb shell am force-stop "$QAF" >/dev/null 2>&1
  adb uninstall "$QAF" > /dev/null 2>&1
  assert_eq "qa-capture-fwd uninstalled" "" "$(adb shell pm list packages "$QAF" | tr -d '\r' | grep -x "package:$QAF")"
}
# The fixture's TileShellQa lines since a device time ("MM-DD HH:MM:SS.mmm").
qa_time() { adb shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
qa_log() { adb logcat -d -v brief -s TileShellQa -T "$1" 2>/dev/null | tr -d '\r' | grep -F 'TileShellQa' | sed 's/^[^:]*: //'; }
start_leg() { # leg [extra am args...]
  local leg="$1"; shift
  adb shell am start -n "$QAC/.CaptureProbeActivity" --es leg "$leg" "$@" >/dev/null
}
# Waits until the fixture has logged "done" for the leg (up to N s); prints the leg's lines.
leg_lines() { # since leg [seconds]
  local i out=""
  for i in $(seq 1 "${3:-12}"); do
    out="$(qa_log "$1" | grep -F "leg=$2 ")"
    if printf '%s\n' "$out" | grep -q "leg=$2 done"; then printf '%s\n' "$out"; return 0; fi
    sleep 1
  done
  printf '%s\n' "$out"; return 1
}

# ---- Start's tiles -------------------------------------------------------------------------------------------------
tile_field() { # dump.xml id field(bounds|size|controls|badge|texts)
  python3 "$TILES_PY" "$1" "$2" | awk -F'\t' -v f="$3" '{ m["bounds"] = $2; m["size"] = $3; m["controls"] = $4; m["badge"] = $5; m["texts"] = $6 } END { print m[f] }'
}
# Phase 11 E3's hold (qa/phase-16/scripts/e25.sh): the tile's centre held 1.0 s with the finger still down, the dump
# taken while it is down, then released; the burst stays open for the tap on a satellite.
burst_on() { # tile-node-id tag
  local b x y
  gdump "$ROW_DIR/$2-rest.xml" || true
  b="$(bounds "$ROW_DIR/$2-rest.xml" "$1")"
  [ -n "$b" ] || { note "burst_on: no $1 on Start"; return 1; }
  # shellcheck disable=SC2086
  set -- "$1" "$2" $b
  x=$(( ($3 + $5) / 2 )); y=$(( ($4 + $6) / 2 ))
  adb shell input motionevent DOWN "$x" "$y"; sleep 1.0
  gdump "$ROW_DIR/$2.xml" || true; screencap "$ROW_DIR/$2.png"
  adb shell input motionevent UP "$x" "$y"; sleep 0.8
}
sat_labels() { # dump -> the quick_sat_label texts in index order, comma-separated
  local i out=""
  for i in 0 1 2 3; do [ "$(has_node "$1" "quick_sat_label:$i")" = yes ] && out="$out$(node_text "$1" "quick_sat_label:$i"),"; done
  echo "${out%,}"
}
slot_of() { layout_json | python3 -c 'import json, sys; print(json.load(sys.stdin).get("slots", {}).get(sys.argv[1], ""))' "$1"; }
