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
LOG=""
PASS=0
FAIL=0

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

row_begin() { # id description
  take_device_lock
  ROW="$1"
  ROW_DIR="$QA/$ROW"
  mkdir -p "$ROW_DIR"
  LOG="$ROW_DIR/$ROW.txt"
  PASS=0
  FAIL=0
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
    echo "=============================================================================="
  } > "$LOG"
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
  {
    echo "------------------------------------------------------------------------------"
    echo "$ROW: $PASS passed, $FAIL failed"
  } >> "$LOG"
  echo "   $ROW: $PASS passed, $FAIL failed"
  # A row that asserted nothing is a row that tested nothing.
  if [ $((PASS + FAIL)) -eq 0 ]; then
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
