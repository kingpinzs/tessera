#!/usr/bin/env bash
# Camera's development-proof helpers, sourced AFTER lib.sh by every dev-camera script. Not the gate: the lead writes
# and runs E7 / E8 / E9 / E15 / E19 / E23 from the phase doc. Everything here drives the real shell on emulator-5554.
CAM_RING="app.tileshell/.camera.CameraDumpService"
DRV_RUNNER="app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner"
IMAGES="content://media/external/images/media"
VIDEOS="content://media/external/video/media"

# The viewfinder never idles, so it is dumped with the phase 05 gesture driver (qa/phase-15/scripts/p15.sh's gdump).
gdump() { # out.xml [package the dump must hold; default the shell's]
  local out="$1" want="${2:-app.tileshell}" i name
  : > "$out.drv"
  for i in $(seq 1 12); do
    name="cam_$(date +%s%N)_$i.xml"
    adb shell am instrument -r -w -e op dump -e out "/sdcard/Download/$name" "$DRV_RUNNER" >> "$out.drv" 2>&1
    adb shell cat "/sdcard/Download/$name" > "$out" 2>/dev/null
    adb shell rm -f "/sdcard/Download/$name" >/dev/null 2>&1
    # The driver sometimes reports only the system's own windows (the app's window not yet in the accessibility
    # tree): a dump counts only when it holds a node of the package asked for.
    if grep -q "package=\"$want\"" "$out"; then return 0; fi
    sleep 0.25
  done
  echo "(dump failed)" > "$out"
  return 1
}

top_activity() { adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity' | grep -oE '[a-z][A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | head -1 | tr -d '\r'; }
cam_since() { ring_since "$1" "$CAM_RING"; }
count_rows() { adb shell "content query --uri $1 --projection _id" | grep -c '_id=' ; }
# Rows the shell owns under DCIM/Camera, newest first: "_id|display_name|width|height|is_pending|size|relative_path"
shell_rows() { # collection uri
  adb shell "content query --uri $1 --projection _id:_display_name:width:height:is_pending:_size:relative_path:owner_package_name --sort '_id DESC'" \
    | tr -d '\r' | grep 'owner_package_name=app.tileshell' \
    | sed -E 's/^Row: [0-9]+ _id=([0-9]+), _display_name=([^,]*), width=([^,]*), height=([^,]*), is_pending=([^,]*), _size=([^,]*), relative_path=([^,]*), .*/\1|\2|\3|\4|\5|\6|\7/'
}
max_id() { adb shell "content query --uri $1 --projection _id --sort '_id DESC'" | head -1 | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | tr -d '\r'; }
# Remove every row of the shell's with _id above the given one (what this script captured), by id, one at a time.
remove_rows_above() { # collection uri, id
  local id
  for id in $(shell_rows "$1" | cut -d'|' -f1); do
    [ "$id" -gt "${2:-999999999}" ] && adb shell "content delete --uri $1/$id" >/dev/null
  done
}
open_camera() { # [mode]
  if [ -n "${1:-}" ]; then adb shell am start -n app.tileshell/.camera.CameraActivity --es mode "$1" >/dev/null; else adb shell am start -n app.tileshell/.camera.CameraActivity >/dev/null; fi
  sleep 4
}
# Centre of a node in px ("x y"), and in epx with one decimal ("x y") — px / 3 on this 1080-px-wide screen.
centre_px() { set -- $(bounds "$1" "$2"); [ -n "${4:-}" ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
centre_epx() { set -- $(bounds "$1" "$2"); [ -n "${4:-}" ] && python3 -c "print('%.1f %.1f' % (($1+$3)/6, ($2+$4)/6))"; }
size_epx() { set -- $(bounds "$1" "$2"); [ -n "${4:-}" ] && python3 -c "print('%.1f %.1f' % (($3-$1)/3, ($4-$2)/3))"; }
install_mine() { [ "$(apk_matches | cut -c1-3)" = "yes" ] || adb install -r "$APK" >/dev/null || { echo "install failed" >&2; exit 4; }; }
# CAMERA is a runtime permission `adb install -r` does not grant; the baseline device holds it (provision.sh grants it).
granted() { adb shell dumpsys package app.tileshell | grep -c "android.permission.$1: granted=true"; }
ensure_camera_grant() { [ "$(granted CAMERA)" -ge 1 ] || { adb shell pm grant app.tileshell android.permission.CAMERA; echo "(pm grant CAMERA: the device did not hold it)"; }; }
# The camera is open once the process's ring holds its `devices=` line (CameraX and its extensions take a few seconds
# on the emulator). Waits up to 20 s from the given mark; prints the seconds it took, or "timeout".
wait_camera() { # mark
  local i
  for i in $(seq 1 20); do
    if cam_since "$1" | grep -q '\[camera\] devices='; then sleep 1; echo "$i"; return 0; fi
    sleep 1
  done
  echo timeout; return 1
}

# ---- the capture answer (qa-capture) -------------------------------------------------------------------------------
QAC=app.tileshell.testclient.qacapture
QAC_APK="${WT:-}/testapps/qa-capture/build/outputs/apk/debug/qa-capture-debug.apk"
install_qac() { adb install -r "$QAC_APK" >/dev/null || { echo "qa-capture install failed" >&2; exit 4; }; }
# The fixture's TileShellQa lines since a device time ("MM-DD HH:MM:SS.mmm"), CRs removed.
qa_time() { adb shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
qa_log() { adb logcat -d -v brief -s TileShellQa -T "$1" 2>/dev/null | tr -d '\r' | grep -F 'TileShellQa' | sed 's/^[^:]*: //'; }
start_leg() { # leg [extra am args...]
  local leg="$1"; shift
  adb shell am start -n "$QAC/.CaptureProbeActivity" --es leg "$leg" "$@" >/dev/null
}
# Waits until the fixture has logged "done" for the leg (up to N s); prints the leg's lines.
leg_lines() { # since leg [seconds]
  local i out
  for i in $(seq 1 "${3:-12}"); do
    out="$(qa_log "$1" | grep -F "leg=$2 ")"
    if echo "$out" | grep -q "leg=$2 done"; then echo "$out"; return 0; fi
    sleep 1
  done
  echo "$out"; return 1
}
pending_rows() { adb shell "content query --uri content://media/external/file --projection _id:is_pending:owner_package_name --where 'is_pending=1'" | tr -d '\r' | grep -c 'owner_package_name=app.tileshell'; }

# ---- video ---------------------------------------------------------------------------------------------------------
# No microphone anywhere: a step that records revokes RECORD_AUDIO first and grants it back at its end (r3 D12 / V4).
# Revoking a runtime permission restarts the app's processes, so it is done before the camera is opened.
mic_off() { adb shell pm revoke app.tileshell android.permission.RECORD_AUDIO; sleep 1; }
mic_on() { adb shell pm grant app.tileshell android.permission.RECORD_AUDIO; }
streams() { ffprobe -v error -show_entries stream=codec_type -of csv=p=0 "$1" 2>/dev/null | grep -v '^$' | sort | uniq -c | xargs; }
stream_facts() { ffprobe -v error -show_streams "$1" 2>/dev/null | grep -E '^(codec_name|codec_type|width|height|r_frame_rate|rotation|duration|nb_frames)=' | xargs; }
