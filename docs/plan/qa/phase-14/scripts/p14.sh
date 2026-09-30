#!/usr/bin/env bash
# Phase 14 row helpers, sourced after lib.sh (phase 03's floor, symlinked here). Every driver pins emulator-5554 —
# the other AVDs on this host belong to other projects.
export ANDROID_SERIAL=emulator-5554
P14="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
P03S="$(cd "$P14/../phase-03/scripts" && pwd)"
P02S="$(cd "$P14/../phase-02/scripts" && pwd)"
P01S="$(cd "$P14/../phase-01/scripts" && pwd)"
P12S="$(cd "$P14/../phase-12/scripts" && pwd)"
P13S="$(cd "$P14/../phase-13/scripts" && pwd)"

# The pan the doc's rows use: inside the page, well clear of the left gesture inset.
swipe_right() { adb shell input swipe 200 1200 950 1200 250; sleep "${1:-1.5}"; }
swipe_left() { adb shell input swipe 900 1200 150 1200 250; sleep "${1:-1.5}"; }

# "Start alone": start_page in the dump, and neither pivot neighbour.
start_alone() { # dump.xml -> yes / no
  if [ "$(has_node "$1" start_page)" = yes ] && [ "$(has_node "$1" pod_bay)" = no ] && [ "$(has_node "$1" app_list)" = no ]; then
    echo yes
  else
    echo no
  fi
}

# The `[start] page=<name>` lines in a slice, in order, space-separated.
pages_in() { printf '%s\n' "$1" | grep -oE '\[start\] page=[A-Z_]+' | sed 's/.*page=//' | tr '\n' ' ' | sed 's/ $//'; }

# C-6: after any launch — keep the ring, force-stop, Home, wait for Start.
c6() {
  ring_save
  adb shell am force-stop app.tileshell
  sleep 1
  adb shell input keyevent KEYCODE_HOME
  sleep 4
}

# The activity on top, short form (app.tileshell/.StartActivity).
top_activity() {
  adb shell dumpsys activity activities | grep -m1 'topResumedActivity' | tr -d '\r' | sed -E 's/.* u0 ([^ ]+) .*/\1/'
}

# Whether a voice-interaction session window is showing.
session_window() {
  if adb shell dumpsys window windows | grep -q 'VoiceInteraction'; then echo yes; else echo no; fi
}

# The wall= value of the first line in a slice that contains a fixed string ('' when none).
wall_of_first() { # slice needle
  printf '%s\n' "$1" | grep -F -- "$2" | head -1 | grep -oE 'wall=[0-9]+' | cut -d= -f2
}

# The pod bay open from Start: ensure_start, then the pan; asserts the page and returns 0 when it is showing.
open_pod_bay() { # out.xml
  ensure_start || return 1
  swipe_right
  dump_ui "$1"
  [ "$(has_node "$1" pod_bay)" = yes ]
}

# ---- C-3 seeding (rows that read Start's grid: E1, E10, E13) ---------------------------------------------------------
. "$P02S/layout.sh"

# Save the device's own layout, seed phase 02's baseline, and assert no slot was assigned while the shell loaded it.
seed_baseline() {
  layout_save "$ROW_DIR/device-layout.json"
  ring_save
  local mark; mark="$(ring_mark)"
  layout_restore "$P02S/../baseline_layout.json"
  assert_eq "layout_restore of phase 02's baseline" "0" "$?"
  sleep 1
  local slice assigned
  slice="$(ring_since "$mark")"
  # The slice must cover the restore (its own "already run" lines), so a zero below is about this load, not an empty ring.
  assert_ne "C-3: the slice covers the restore (assignSlotOnce lines in it)" "0" "$(printf '%s\n' "$slice" | grep -cF 'assignSlotOnce')"
  assigned="$(printf '%s\n' "$slice" | grep -F 'assignSlotOnce' | grep -cF -- '-> assigned')"
  assert_eq "C-3: zero assignSlotOnce ... -> assigned lines after the restore" "0" "$assigned"
}

# RV12: back to the layout the device had before the row.
restore_device_layout() {
  ring_save
  layout_restore "$ROW_DIR/device-layout.json"
  assert_eq "restore: the device's own layout" "0" "$?"
}
