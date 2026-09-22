#!/usr/bin/env bash
# Phase 05 QA: the keyboard's helpers, on top of phase 03's audited driver floor (lib.sh, symlinked
# here as phase 01 does, so the evidence header still records a real blob).
#
# Source AFTER lib.sh:
#   . "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/kb.sh"; row_begin E2 "..."; kb_begin; ...; kb_end; row_end
#
# What this adds:
#   * kb_begin / kb_end: select the shell's keyboard for the row and put back whatever was selected
#     before, plus the AVD's show_ime_with_hard_keyboard switch (PLAN RV12: rows restore what they change)
#   * kb_dump: the IME window's nodes. Plain `uiautomator dump` carries NONE of them (TOOLING.md §2);
#     only the instrumentation's UiDevice.dumpWindowHierarchy reaches the input-method window
#   * the gesture driver (drv, script, swipe_pts, drag_pts) and the fixture's mirror readers
#   * ime_dump / ime_log: the :ime process's own diagnostics ring (the launcher's dump cannot see it)

IME_ID="app.tileshell/.ime.KeyboardService"
FIX="app.tileshell.qa.imefixture"
RUNNER="$FIX.test/androidx.test.runner.AndroidJUnitRunner"
FIX_APK="$REPO/testapps/ime-fixture/build/outputs/apk/debug/ime-fixture-debug.apk"
DRV_APK="$REPO/testapps/ime-fixture/build/outputs/apk/androidTest/debug/ime-fixture-debug-androidTest.apk"

kb_begin() {
  local prior show
  prior="$(adb shell settings get secure default_input_method | tr -d '\r')"
  show="$(adb shell settings get secure show_ime_with_hard_keyboard | tr -d '\r')"
  printf '%s\n%s\n' "$prior" "$show" > "$ROW_DIR/.kb_restore"
  note "kb_begin: prior ime=$prior show_ime_with_hard_keyboard=$show"
  adb shell pm path "$FIX" >/dev/null 2>&1 || adb install -r -t "$FIX_APK" >/dev/null
  adb shell pm path "$FIX.test" >/dev/null 2>&1 || adb install -r -t "$DRV_APK" >/dev/null
  adb shell settings put secure show_ime_with_hard_keyboard 1
  adb shell ime enable "$IME_ID" >/dev/null
  adb shell ime set "$IME_ID" >/dev/null
}

kb_end() {
  [ -f "$ROW_DIR/.kb_restore" ] || return 0
  local prior show
  prior="$(sed -n 1p "$ROW_DIR/.kb_restore")"
  show="$(sed -n 2p "$ROW_DIR/.kb_restore")"
  adb shell input keyevent KEYCODE_HOME
  if [ -n "$prior" ] && [ "$prior" != "null" ]; then adb shell ime set "$prior" >/dev/null; fi
  if [ -n "$show" ] && [ "$show" != "null" ]; then adb shell settings put secure show_ime_with_hard_keyboard "$show"; fi
  note "kb_end: restored ime=$prior show_ime_with_hard_keyboard=$show"
  rm -f "$ROW_DIR/.kb_restore"
}

# Open the fixture with FIELD focused and the keyboard requested (-S: a fresh instance, empty fields).
open_field() { # field [activity]
  adb shell am start -S -W -n "$FIX/.${2:-MainActivity}" -e focus "$1" >/dev/null
  sleep 2.5
}

drv() { adb shell am instrument --no-restart -r -w "$@" "$RUNNER" 2>&1; }

# The device shell splits on ';' — the inner single quotes carry the list through to the driver.
script() { drv -e op script -e script "'$1'"; }
swipe_pts() { drv -e op swipe -e points "'$1'" -e steps "${2:-10}"; }
drag_pts() { drv -e op drag -e from "$1" -e to "$2" -e steps "${3:-40}"; }

kb_dump() { # out.xml
  local out="$1"
  for _ in 1 2 3; do
    drv -e op dump -e out /sdcard/Download/kbq.xml > "$out.drv" 2>&1
    adb shell cat /sdcard/Download/kbq.xml > "$out" 2>/dev/null
    grep -q 'resource-id="kb_panel"' "$out" && return 0
    sleep 1
  done
  return 1
}

node_center() { # dump.xml resource-id -> "x y"
  local b
  b="$(bounds "$1" "$2")"
  [ -n "$b" ] || return 1
  # shellcheck disable=SC2086
  set -- $b
  echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}

tap_key() { # dump.xml key-id (without the kb_key_ prefix)
  local c
  c="$(node_center "$1" "kb_key_$2")" || { echo "tap_key: no key $2" >&2; return 2; }
  # shellcheck disable=SC2086
  adb shell input tap $c
}

# Type a string of letters by tapping keys, one input process per key (not for timing rows).
tap_word() { # dump.xml word
  local w="$2" i
  for ((i = 0; i < ${#w}; i++)); do
    case "${w:i:1}" in
      " ") tap_key "$1" space ;;
      ".") tap_key "$1" period ;;
      ",") tap_key "$1" comma ;;
      *) tap_key "$1" "${w:i:1}" ;;
    esac
  done
}

# The fixture's mirror: "text" -> "[abc]", "sel" -> "3,3", "focus", "action", "len", "inputtype".
mirror() { # dump.xml name
  node_text "$1" "$FIX:id/mirror_$2"
}

read_mirror() { # name  (fresh plain dump: the fixture's own views are in it)
  local f="$ROW_DIR/.mirror.xml"
  dump_ui "$f" >/dev/null
  mirror "$f" "$1"
}

ime_dump() { adb shell dumpsys activity service "$IME_ID" 2>/dev/null; }
ime_log() { ime_dump | grep -F "[ime]" | grep -E "$1"; }
# dumpsys indents every line of a service's dump, so these never anchor on ^.
ime_layer() { ime_dump | grep -o 'layer=[A-Z_0-9]*' | head -1; }

# Clear the focused fixture field: select all + delete through key events the field understands.
clear_field() {
  adb shell input keyevent KEYCODE_MOVE_END
  adb shell input keyevent --longpress KEYCODE_DEL >/dev/null 2>&1
  for _ in $(seq 1 40); do adb shell input keyevent KEYCODE_DEL; done
}

# The phys→px factor on this panel (display width / 1440) and px per epx (portrait width / 360).
sx() { python3 -c "print($(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x.*/\1/p' | tail -1)/1440)"; }
