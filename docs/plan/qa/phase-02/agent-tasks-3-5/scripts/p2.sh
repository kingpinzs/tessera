#!/usr/bin/env bash
# Phase 02 QA helpers for build tasks 3 and 5. Phase 01's ui.sh (dump / bounds / center / tap_id / scroll_to_id)
# is used as it stands; these only add what this pass needs.
source "$(dirname "${BASH_SOURCE[0]}")/../../../phase-01/scripts/ui.sh"

NODES="$(dirname "${BASH_SOURCE[0]}")/../../../phase-01/scripts/nodes.py"

go_start() { # Home, settled
  adb shell input keyevent KEYCODE_HOME; sleep 3
}

open_applist() { # open_applist <xml out>: Start -> the app list pivot, confirmed in the dump (retried)
  go_start
  for i in 1 2 3; do
    adb shell input swipe 900 1200 150 1200 250; sleep 2
    dump "$1" && grep -q 'resource-id="app_list"' "$1" && return 0
  done
  echo "app list did not open" >&2; return 1
}

hold_id() { # hold_id <xml> <resource-id> [ms]: a press longer than Edit.HOLD_MS (783 ms), RV11's swipe form
  local xy ms; xy=$(center "$1" "$2") || { echo "no node $2" >&2; return 1; }
  ms=${3:-900}
  adb shell "input swipe $xy $xy $ms"
}

diag() { # the shell's diagnostics ring (phase 01: read through the notification listener's dump)
  adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener
}

tiles_on_start() { # tiles_on_start <xml out>: every tile id Start currently shows (the grid scrolled to the top)
  dump "$1" && grep -o 'resource-id="tile:[^"]*"' "$1" | sed 's/resource-id="//; s/"$//'
}

pin_app() { # pin_app <package> <xml scratch>: the real gesture path - app list, hold the row, tap "Pin to Start"
  open_applist "$2" || return 1
  scroll_to_id "$2" "applist_row:$1" || return 1
  hold_id "$2" "applist_row:$1" 900 || return 1
  sleep 2
  dump "$2" || return 1
  grep -q 'resource-id="applist_menu_pin"' "$2" || { echo "no context menu for $1" >&2; return 1; }
  tap_id "$2" applist_menu_pin
  sleep 2
}
