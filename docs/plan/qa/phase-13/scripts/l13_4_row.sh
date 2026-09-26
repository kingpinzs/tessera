#!/usr/bin/env bash
# L13-4 regression row (INDEX ledger; review/2026-09-26-L13-345-fix-plan.md): Back and Home keep working after a touch
# lands during the pivot they started. Before the fix, StartActivity ran the pivot inside the Back / Home collectors; a
# drag that cancelled it ended the collector, and every later Back and Home did nothing until the activity was recreated
# (qa/phase-13/L13-3-investigation/I-back-interrupt/). Each case starts a fresh StartActivity (its collectors alive),
# goes to the app list, sends the key (with or without a still press 15 ms after it), then twice: back onto the app list,
# the key again, and Start must show. The controls (no press) show the key's plain behaviour.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin L13_4 "Back and Home survive a touch during the pivot they start"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
clear_background

# "applist" when the app list is the page on screen (app_list's left edge at 0), "start" otherwise.
page() {
  dump_ui "$ROW_DIR/$1.xml"
  python3 - "$ROW_DIR/$1.xml" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(r'resource-id="app_list"[^>]*bounds="\[(-?\d+),', s)
print("applist" if m and int(m.group(1)) == 0 else "start")
PY
}

one() { # tag key(4 Back | 3 Home | drawn) interrupt(yes|no)
  local tag="$1" key="$2" hit="$3" k send
  adb shell am force-stop $PKG; sleep 1
  show_start 5
  to_app_list 2
  assert_eq "$tag: on the app list before the first key" "applist" "$(page "$tag-0")"
  if [ "$key" = drawn ]; then
    set -- $(bounds "$ROW_DIR/$tag-0.xml" nav_back)
    local nx=$(( ($1 + $3) / 2 )) ny=$(( ($2 + $4) / 2 ))
    send="input tap $nx $ny"
    # the investigator's drawn-Back case: a tap on the list 150 ms after the drawn Back
    [ "$hit" = yes ] && adb shell "input tap $nx $ny; sleep 0.15; input tap 540 1206" || adb shell "input tap $nx $ny"
  else
    send="input keyevent $key"
    [ "$hit" = yes ] && adb shell "input keyevent $key; sleep 0.015; input swipe 540 1206 540 1206 300" || adb shell "input keyevent $key"
  fi
  sleep 2
  record "$tag: the page after the first key" "$(page "$tag-1")"
  for k in 2 3; do
    [ "$(page "$tag-$k-pre")" = applist ] || { show_start 3; to_app_list 2; }
    adb shell "$send"
    sleep 2
    assert_eq "$tag: key $k takes the app list to Start" "start" "$(page "$tag-$k")"
  done
}

# The order: two Backs at once from the app list. The second waits for the pivot the first started, then runs as Back
# on Start (backOnStart always writes a [back] line); it never cuts the pivot short. The fix's first form (b2d44b7d) ran
# each pivot in a child of the collector, so the second Back came in mid-pivot, restarted it, and Back on Start never
# ran (review/2026-09-26-L13-345-fix-review-a.md, note 3).
twice() {
  adb shell am force-stop $PKG; sleep 1
  show_start 5
  to_app_list 2
  assert_eq "twice: on the app list before the keys" "applist" "$(page twice-0)"
  local mark; mark="$(ring_mark)"
  adb shell "input keyevent 4 4"
  sleep 2
  ring_since "$mark" > "$ROW_DIR/twice-slice.txt"
  assert_contains "twice: the second Back ran as Back on Start, after the pivot" "[back] " "$(cat "$ROW_DIR/twice-slice.txt")"
  assert_absent "twice: no pivot was cut short" "interrupted" "$(cat "$ROW_DIR/twice-slice.txt")"
  show_start 3
}

one back-control 4 no
one back-press 4 yes
one home-control 3 no
one home-press 3 yes
one drawn-control drawn no
one drawn-tap drawn yes
twice
ring_save
show_start 3
row_end
