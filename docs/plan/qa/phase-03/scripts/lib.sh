#!/usr/bin/env bash
# Phase 03 QA: the shared driver floor.
#
# Phase 02's gate was returned by both reviewers for evidence integrity — rows whose logs predated
# their drivers, brackets that could not fail, verdicts that never reached the log. This file exists so
# none of that is possible here:
#
#   * every row stamps its log with the driver blob and the installed APK before it asserts anything
#   * every assertion is a real comparison with a PASS/FAIL line AND an exit code
#   * a row with zero assertions fails, because a driver that asserts nothing has not tested anything
#   * the summary is computed from the recorded verdicts, never written by hand
#
# Source it from a row driver:
#   . "$(dirname "$0")/lib.sh"; row_begin E1 "the assistant role"
#   assert_eq "role holder" "app.tileshell" "$(adb shell cmd role get-role-holders ...)"
#   row_end
set -uo pipefail

export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
PKG=app.tileshell
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QA="$(cd "$HERE/.." && pwd)"
REPO="$(cd "$QA/../../../.." && pwd)"
APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"

ROW=""
ROW_DIR=""
ROW_MARK=""   # the device's wall clock (ms) when the row began: ring_save keeps the ring lines since it
LOG=""
PASS=0
FAIL=0
RECORDED=0

# Only one driver may drive the device at a time. Two E2 runs overlapped once and wrote to the same
# log: the second row read the first's diagnostics and recorded a verdict about an utterance it never
# spoke. A row that cannot get the lock refuses to start rather than producing evidence about nothing.
DEVICE_LOCK="${TMPDIR:-/tmp}/tileshell-qa-device.lock"
take_device_lock() {
  exec 9>"$DEVICE_LOCK"
  if ! flock -n 9; then
    echo "another QA driver is already driving the device (lock $DEVICE_LOCK); refusing to start" >&2
    exit 3
  fi
}

# Every row starts on an awake, unlocked, mains-powered device (RV12's baseline). A row that taps a sleeping
# screen records verdicts about nothing: on 2026-09-23 the AVD came back from a restart on simulated battery
# (stay-on-while-plugged does nothing then) and asleep, and E7 / E8 / MUSIC7 failed as if the product had.
wake_device() {
  adb shell dumpsys battery reset >/dev/null 2>&1
  adb emu power ac on >/dev/null 2>&1          # the AVD's power source is set from its console, not the shell
  adb emu power status charging >/dev/null 2>&1
  adb shell svc power stayon true >/dev/null 2>&1
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  adb shell wm dismiss-keyguard >/dev/null 2>&1
  sleep 0.5
  adb shell dumpsys power | grep -m1 'mWakefulness=' | tr -d '\r ' | sed 's/mWakefulness=//'
}

row_begin() { # id description
  take_device_lock
  ROW="$1"
  ROW_DIR="$QA/$ROW"
  mkdir -p "$ROW_DIR"
  LOG="$ROW_DIR/$ROW.txt"
  PASS=0
  FAIL=0
  RECORDED=0
  {
    echo "=============================================================================="
    echo "ROW $ROW — ${2:-}"
    echo "at            $(date -Is)"
    # The build guard phase 02's rework added: a log that cannot say which driver and which APK
    # produced it is not evidence of anything.
    echo "driver        $(basename "$0") blob $(git -C "$REPO" hash-object "$HERE/$(basename "$0")")"
    echo "harness       lib.sh blob $(git -C "$REPO" hash-object "$HERE/lib.sh")"
    echo "apk built     $(sha256sum "$APK" 2>/dev/null | cut -c1-16) $(stat -c%s "$APK" 2>/dev/null) bytes"
    echo "apk installed $(installed_apk_id)"
    echo "apk match     $(apk_matches)"
    echo "device        $(adb shell getprop ro.build.fingerprint)"
    echo "awake         $(wake_device)"
    echo "=============================================================================="
  } > "$LOG"
  # Taken after the lock and the wake, so the row's ring slice starts on the clock the row runs on (C-20).
  ROW_MARK="$(ring_mark)"
  echo "── $ROW ${2:-}"
}

log() { echo "$*" | tee -a "$LOG"; }
note() { echo "      $*" >> "$LOG"; }

_verdict() { # PASS|FAIL name detail
  local verdict="$1" name="$2" detail="$3"
  printf '%-4s  %-58s  %s\n' "$verdict" "$name" "$detail" | tee -a "$LOG"
  if [ "$verdict" = PASS ]; then PASS=$((PASS + 1)); else FAIL=$((FAIL + 1)); fi
}

assert_eq() { # name expected actual
  if [ "$2" = "$3" ]; then _verdict PASS "$1" "= $2"; else _verdict FAIL "$1" "expected [$2] got [$3]"; fi
}

assert_ne() { # name notexpected actual
  if [ "$2" != "$3" ]; then _verdict PASS "$1" "!= $2"; else _verdict FAIL "$1" "should not be [$2]"; fi
}

assert_contains() { # name needle haystack
  case "$3" in
    *"$2"*) _verdict PASS "$1" "contains [$2]" ;;
    *) _verdict FAIL "$1" "does not contain [$2]: ${3:0:240}" ;;
  esac
}

assert_absent() { # name needle haystack
  case "$3" in
    *"$2"*) _verdict FAIL "$1" "should NOT contain [$2]" ;;
    *) _verdict PASS "$1" "does not contain [$2]" ;;
  esac
}

assert_within() { # name expected actual tolerance
  local line
  line="$(python3 "$HERE/within.py" "$1" "$2" "$3" "$4")"
  if [ -z "$line" ]; then
    _verdict FAIL "$1" "could not be measured (actual=[$3])"
    return
  fi
  _verdict "${line%%|*}" "$1" "${line##*|}"
}

row_end() {
  # Keep the row's ring lines first, before anything can reset the ring: one file per ring the row names in
  # RINGS (default the launcher's), appended to whatever the row's own ring_save calls already kept (T11-21).
  local ring
  for ring in ${RINGS:-launcher}; do ring_save "$ring"; done
  {
    echo "------------------------------------------------------------------------------"
    echo "$ROW: $PASS passed, $FAIL failed, ${RECORDED:-0} recorded"
  } >> "$LOG"
  echo "   $ROW: $PASS passed, $FAIL failed, ${RECORDED:-0} recorded"
  # A row that asserted nothing is a row that tested nothing — unless it exists to RECORD facts (C-26): then it
  # says so and passes, and its facts are in the log as RECORD lines, never counted as passes.
  if [ $((PASS + FAIL)) -eq 0 ]; then
    if [ "${RECORDED:-0}" -gt 0 ]; then
      echo "$ROW: recorded only (${RECORDED} facts)" | tee -a "$LOG"
      return 0
    fi
    echo "FAIL  $ROW made no assertions at all" | tee -a "$LOG"
    return 1
  fi
  [ $FAIL -eq 0 ]
}

# ---------------------------------------------------------------- device helpers

diag() { # [tag]
  local out
  out="$(adb shell dumpsys activity service $PKG/.feeds.TileNotificationListener 2>/dev/null)"
  if [ -n "${1:-}" ]; then echo "$out" | grep -F "[$1]"; else echo "$out"; fi
}

speech_dump() {
  adb shell dumpsys activity service $PKG/.cortana.speech.SpeechService 2>/dev/null
}

speech_status() { # key
  speech_dump | sed -n "s/^ *$1=//p" | head -1 | tr -d '\r'
}

# ---------------------------------------------------------------- ring slices (phase 11 build task 7, C-20)
# The rings are fixed-size buffers, so "the lines after line N" goes empty once one is full (j7.sh, run 3), and
# every `am force-stop app.tileshell`, `pm clear` or layout_restore empties the launcher's. A row therefore slices
# a ring by the wall= stamp each line carries, from a MARK taken on the device's own clock just before the step:
#   MARK="$(ring_mark)"; <the step>; assert_contains "..." "[quick] burst on" "$(ring_since "$MARK")"
# and keeps what it read with ring_save before anything resets the ring (row_end saves the rings named in RINGS).

# The device's wall clock in ms: the clock Diagnostics stamps wall= with.
ring_mark() {
  adb shell date +%s%3N | tr -d '\r'
}

# The ring lines stamped wall >= mark, in ring order. The ring: launcher (default: the diag() dump), speech (the
# :speech process's own ring, speech_dump()), or any service component whose dump carries Diagnostics lines.
ring_since() { # mark [launcher|speech|<service component>]
  local mark="$1" ring="${2:-launcher}" out
  case "$ring" in
    launcher) out="$(diag)" ;;
    speech) out="$(speech_dump)" ;;
    *) out="$(adb shell dumpsys activity service "$ring" 2>/dev/null)" ;;
  esac
  printf '%s\n' "$out" | python3 -c '
import re, sys
mark = sys.argv[1]
if not mark.isdigit():
    sys.exit("ring_since: mark must be the ms value ring_mark printed, got [%s]" % mark)
for line in sys.stdin:
    m = re.search(r"\bwall=(\d+)", line)
    if m and int(m.group(1)) >= int(mark):
        print(line.rstrip("\r\n"))
' "$mark"
}

# The first reply Tess spoke after the mark (text only), empty if none. Replies are the [speech] lines of the
# LAUNCHER's ring, where SpeechClient records each utterance it hands the engine — the lines reply_text reads —
# because SpeechClient runs in the launcher's process; the :speech process's own ring carries no reply text.
reply_since() { # mark
  ring_since "$1" launcher | grep -F '[speech]' | grep -oE 'text="[^"]*"' | sed -n '1{s/^text="//;s/"$//;p;q;}'
}

# Append the ring's lines since ROW_MARK to $ROW_DIR/ring-<name>.txt (name: launcher, speech, or the component with
# every character outside [A-Za-z0-9._-] made _). Called before every in-row force-stop, pm clear or layout_restore,
# and by row_end; a line kept twice is harmless (E14 reads the union of these files).
ring_save() { # [launcher|speech|<service component>]
  local ring="${1:-launcher}" name
  if [ -z "$ROW_MARK" ] || [ -z "$ROW_DIR" ]; then
    echo "ring_save: no ROW_MARK / ROW_DIR (row_begin has not run); nothing saved" >&2
    return 1
  fi
  name="$(printf '%s' "$ring" | tr -c 'A-Za-z0-9._-' '_')"
  ring_since "$ROW_MARK" "$ring" >> "$ROW_DIR/ring-$name.txt"
}

# uiautomator only dumps what is LAID OUT. On a scrolling page every row below the fold is simply
# absent from the dump, which reads exactly like a missing feature: E9's first real run recorded "no
# Lock screen options section" for a section that was there, three swipes down, under eleven voice
# rows. A driver that wants a row on a scrolling page scrolls to it and says so in the log.
scroll_to_node() { # out.xml resource-id [max-swipes]
  local out="$1" id="$2" max="${3:-8}" i=0
  dump_ui "$out" || return 1
  while [ "$(has_node "$out" "$id")" = no ] && [ "$i" -lt "$max" ]; do
    adb shell input swipe 540 1700 540 800 320
    sleep 1
    i=$((i + 1))
    dump_ui "$out" || return 1
  done
  note "scroll_to_node $id: $i swipe(s), found=$(has_node "$out" "$id")"
  [ "$(has_node "$out" "$id")" = yes ]
}

dump_ui() { # out.xml
  local out="$1"
  for _ in 1 2 3; do
    if adb shell uiautomator dump /sdcard/qa.xml >/dev/null 2>&1; then
      adb shell cat /sdcard/qa.xml > "$out" 2>/dev/null
      [ -s "$out" ] && return 0
    fi
    sleep 1
  done
  echo "(dump failed)" > "$out"
  return 1
}

screencap() { adb exec-out screencap -p > "$1"; }

# The bounds of a node by resource-id, as "left top right bottom" in device pixels.
bounds() { # dump.xml resource-id
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(sys.argv[2]), xml)
if not m:
    m = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"[^>]*resource-id="%s"' % re.escape(sys.argv[2]), xml)
print(" ".join(m.groups()) if m else "")
PY
}

node_text() { # dump.xml resource-id
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if f'resource-id="{sys.argv[2]}"' in s:
        m = re.search(r'text="([^"]*)"', s)
        print(m.group(1) if m else "")
        break
PY
}

has_node() { # dump.xml resource-id  -> prints yes/no
  if grep -q "resource-id=\"$2\"" "$1"; then echo yes; else echo no; fi
}

# Open Cortana the way the phase doc says each entry point does.
cortana_assist() { adb shell input keyevent KEYCODE_ASSIST; }
cortana_close() { adb shell input keyevent KEYCODE_BACK; sleep 1; }

ensure_start() {
  adb shell input keyevent KEYCODE_HOME
  sleep 1
}

# Speak an utterance into the AVD's microphone and wait for Cortana to finish with it.
say() { # utterance-id [settle-seconds]
  local wav
  wav="$(python3 "$HERE/utterances.py" path "$1")"
  [ -f "$wav" ] || { echo "missing utterance $1 — run utterances.py build" >&2; return 2; }
  "$HERE/audio.sh" say "$wav"
  sleep "${2:-6}"
}

# Type a request into the real text box (fidelity A4: the same matcher and reply path as speech).
type_request() { # text [settle-seconds]
  local text="$1" settle="${2:-8}"
  local dump="$ROW_DIR/.typing.xml"
  dump_ui "$dump" || true
  local b
  b="$(bounds "$dump" cortana_text_box_field)"
  if [ -z "$b" ]; then
    echo "no text box on screen" >&2
    return 2
  fi
  # shellcheck disable=SC2086
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  sleep 1
  # "input text" takes %s for a space and cannot carry an apostrophe, so a request that needs one is
  # spoken rather than typed; E5 needs one typed request to prove the shared path, not every request.
  adb shell input text "$(printf '%s' "$text" | sed 's/ /%s/g')"
  sleep 1
  adb shell input keyevent KEYCODE_ENTER
  sleep "$settle"
}

# What Cortana last SAID. The spoken-reply pass rule reads the reply text out of the diagnostics dump
# (Decisions, review T-m8); SpeechClient records every utterance it hands the engine.
reply_text() {
  diag speech | grep -oE 'text="[^"]*"' | tail -1 | sed 's/^text="//; s/"$//'
}

# Every reply since a marker, oldest first — for a row that checks a sequence of replies.
replies_since() { # marker-line-count
  diag speech | grep -oE 'text="[^"]*"' | tail -n "${1:-5}" | sed 's/^text="//; s/"$//'
}

# The APK the device actually has, by the hash of its installed base.apk.
installed_apk_id() {
  local path
  path="$(adb shell pm path $PKG 2>/dev/null | head -1 | tr -d '\r' | sed 's/^package://')"
  [ -n "$path" ] || { echo "(not installed)"; return; }
  adb shell md5sum "$path" 2>/dev/null | cut -c1-16 | tr -d '\r'
}

# The local APK's md5, so the two can be compared without pulling 300 MB off the device.
apk_matches() {
  local device local_md5
  device="$(installed_apk_id)"
  local_md5="$(md5sum "$APK" 2>/dev/null | cut -c1-16)"
  if [ "$device" = "$local_md5" ]; then echo "yes ($device)"; else echo "NO (device $device, built $local_md5)"; fi
}

# Open Cortana and start listening: the mic button on the real text box, which is the only way in.
cortana_listen() { # [settle]
  local settle="${1:-2}"
  local dump="${ROW_DIR:-/tmp}/.listen.xml"
  dump_ui "$dump" || true
  if [ "$(has_node "$dump" cortana_session)" != yes ]; then
    cortana_assist
    sleep 4
    dump_ui "$dump" || true
  fi
  local b
  b="$(bounds "$dump" cortana_text_box_mic)"
  if [ -z "$b" ]; then
    echo "no mic button on screen" >&2
    return 2
  fi
  # shellcheck disable=SC2086
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  sleep "$settle"
}

# Tap a node by resource-id. Fails loudly rather than tapping the middle of the screen.
tap_node() { # dump.xml resource-id
  local b
  b="$(bounds "$1" "$2")"
  if [ -z "$b" ]; then
    echo "tap_node: no node $2" >&2
    return 2
  fi
  # shellcheck disable=SC2086
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

# ---------------------------------------------------------------- phase 15 (build task 8) helpers

# A recorded clause (C-13, C-26): a fact the row writes down and does NOT grade — an image's behaviour, a phone's
# setting, a value the doc says to record. Printed as RECORD, counted apart from PASS / FAIL (phase 15 owns this
# helper, T15-21 / C-33: it is the first phase in time to need it).
record() { # name value
  local name="$1" value="$2"
  printf '%-6s  %-58s  %s\n' "RECORD" "$name" "$value" | tee -a "$LOG"
  RECORDED=$(( ${RECORDED:-0} + 1 ))
}

# Fill the shared volume until LEAVE bytes are free as df reports them (C-27; phase 15 E19, phase 17's storage-full
# edge case, phase 18 E4b / E13). As root, one fallocate'd file under /data/media/0, the shell's view of /sdcard.
# It ASSERTS its own precondition — free space at most LEAVE + 5 MB — and fails loudly otherwise: a row that thinks it
# filled the disk and did not would record a pass about nothing. A caller that tests an app-side floor built on
# StorageManager.getAllocatableBytes adds the low-storage reserve itself (dumpsys devicestoragemonitor lowBytes;
# T15-27). Leaves root off (RV12).
fill_volume() { # leave_bytes
  local leave="$1" avail fill after
  # Measured as the SHELL user on /sdcard — the FUSE view the app itself sees. As root /sdcard resolves to the raw
  # /data/media view, which on this AVD reports ~170 MB more free (reserved blocks), and the app's StorageManager
  # answers from its own view; so only the fallocate itself runs as root.
  avail=$(( $(adb shell df -k /sdcard | awk 'NR==2 {print $4}' | tr -d '\r') * 1024 ))
  fill=$(( avail - leave ))
  if [ "$fill" -gt 0 ]; then
    adb root >/dev/null 2>&1; adb wait-for-device
    adb shell fallocate -l "$fill" /data/media/0/fill.bin
    adb unroot >/dev/null 2>&1; adb wait-for-device
  fi
  after=$(( $(adb shell df -k /sdcard | awk 'NR==2 {print $4}' | tr -d '\r') * 1024 ))
  if [ "$after" -gt $(( leave + 5 * 1024 * 1024 )) ]; then
    echo "FAIL  fill_volume: $after bytes free after filling, wanted <= $((leave + 5*1024*1024))" | tee -a "${LOG:-/dev/null}"
    return 1
  fi
  echo "      fill_volume: $after bytes free on /sdcard (asked for $leave)" >> "${LOG:-/dev/null}"
}

unfill_volume() {
  adb root >/dev/null 2>&1; adb wait-for-device
  adb shell rm -f /data/media/0/fill.bin
  adb unroot >/dev/null 2>&1; adb wait-for-device
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
}

# Two host-made files for the recorder rows (T15-46): another app's recording in Recordings/ (MediaStore marks files
# there IS_RECORDING) and a plain song in Music/. Every row that needs them pushes at its start and removes at its
# restore, so no row depends on another having run.
FIXTURE_RECORDINGS_DIR="$REPO/docs/plan/qa/phase-15/fixtures"
push_fixture_recordings() {
  mkdir -p "$FIXTURE_RECORDINGS_DIR"
  [ -f "$FIXTURE_RECORDINGS_DIR/other.m4a" ] || ffmpeg -loglevel error -f lavfi -i "sine=frequency=330:duration=3" \
    -ac 1 -c:a aac -b:a 64k -metadata title="Other recording" "$FIXTURE_RECORDINGS_DIR/other.m4a"
  [ -f "$FIXTURE_RECORDINGS_DIR/song.m4a" ] || ffmpeg -loglevel error -f lavfi -i "sine=frequency=550:duration=3" \
    -ac 1 -c:a aac -b:a 64k -metadata title="Fixture song" -metadata artist="Fixture" "$FIXTURE_RECORDINGS_DIR/song.m4a"
  adb shell mkdir -p /sdcard/Recordings /sdcard/Music >/dev/null 2>&1
  adb push "$FIXTURE_RECORDINGS_DIR/other.m4a" /sdcard/Recordings/other.m4a >/dev/null
  adb push "$FIXTURE_RECORDINGS_DIR/song.m4a" /sdcard/Music/song.m4a >/dev/null
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
}

remove_fixture_recordings() {
  adb shell rm -f /sdcard/Recordings/other.m4a /sdcard/Music/song.m4a
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
}

# ---------------------------------------------------------------- phase 13 (build task 7) helpers

# Battery saver ON for a row (C-18). wake_device forces AC power, and AOSP refuses low-power mode while powered, so
# this unplugs the simulated battery first; with the phone on battery, stay-on-while-plugged stops, so the screen
# timeout is raised (and restored by battery_saver_off). BS_MARK is the device clock just before set-mode 1: a row
# reads the saver's effects from ring_since "$BS_MARK". The low_power read-back is the row's precondition and fails
# loudly: a row that thinks battery saver is on and it is not would grade the product on nothing.
battery_saver_on() {
  adb shell dumpsys battery unplug >/dev/null 2>&1
  BS_TIMEOUT_SAVED="$(adb shell settings get system screen_off_timeout | tr -d '\r')"
  adb shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1
  BS_MARK="$(ring_mark)"
  export BS_MARK
  adb shell cmd power set-mode 1 >/dev/null 2>&1
  sleep 0.5
  assert_eq "battery saver precondition: low_power" "1" "$(adb shell settings get global low_power | tr -d '\r')"
}

# Battery saver OFF and the device back to RV12's baseline: set-mode 0, the saved screen timeout, then wake_device
# (which resets the battery to AC). Prints wake_device's wakefulness; the row asserts it and low_power = 0.
battery_saver_off() {
  adb shell cmd power set-mode 0 >/dev/null 2>&1
  if [ -n "${BS_TIMEOUT_SAVED:-}" ] && [ "$BS_TIMEOUT_SAVED" != null ]; then
    adb shell settings put system screen_off_timeout "$BS_TIMEOUT_SAVED" >/dev/null 2>&1
  fi
  wake_device
}
