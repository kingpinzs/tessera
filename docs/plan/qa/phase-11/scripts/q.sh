#!/usr/bin/env bash
# Phase 11 QA: the burst's helpers, on phase 03's audited floor (lib.sh, symlinked here as phase 01 does, so the
# evidence header still records a real blob), phase 02's verified layout restore, and phase 05's dump route.
#
# Source AFTER lib.sh:
#   . "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"; row_begin E1 "..."; ...; row_end
#
# What this adds (phase doc, Acceptance preamble):
#   * restore <baseline>: phase 02's layout_restore, then C-3's two checks — no `assignSlotOnce … -> assigned`
#     line in the ring after it, and the restored addedOnce equal to the file's
#   * c6: ring_save, force-stop the shell, Home (C-6: RecentApp is in memory only, so a launched app would be
#     promoted and "the fixture tile" would no longer be the grid tile)
#   * qdump: the gesture driver's UiDevice.dumpWindowHierarchy with no idle wait (C-10: a default Start never
#     idles, its tiles flip); falls back to nothing — a failed dump is a failed dump
#   * the fixture verbs, the burst's readers and the geometry helpers
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
# lib.sh resolves QA through the symlink to THIS phase's directory; the other phases' tools sit beside it.
QROOT="$(cd "$QA/.." && pwd)"
. "$QROOT/phase-02/scripts/layout.sh"

QA11="$QA"
RINGS="${RINGS:-launcher}"
A_PKG=app.tileshell.testclient.a
B_PKG=app.tileshell.testclient.b
A_KEY="app:$A_PKG/app.tileshell.testclient.VerbActivity:0"
B_KEY="app:$B_PKG/app.tileshell.testclient.VerbActivity:0"
A_APK="$REPO/testapps/tileclient-a/build/outputs/apk/debug/tileclient-a-debug.apk"
B_APK="$REPO/testapps/tileclient-b/build/outputs/apk/debug/tileclient-b-debug.apk"
DRV_RUNNER="app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner"
FIX_APK="$REPO/testapps/ime-fixture/build/outputs/apk/debug/ime-fixture-debug.apk"
DRV_APK="$REPO/testapps/ime-fixture/build/outputs/apk/androidTest/debug/ime-fixture-debug-androidTest.apk"

# No --no-restart (unlike phase 05, which kept its fixture app open): that flag needs the fixture's process alive,
# a force-stop leaves "setActiveInstrumentation on a null object", and a run started under a held pointer with it
# hung and blocked every later run (2026-09-23). Without it the runner starts the fixture's process itself.
drv() { adb shell am instrument -r -w "$@" "$DRV_RUNNER" 2>&1; }

# ------------------------------------------------------------------ seeding (Acceptance preamble)

# Install the two fixture APKs and the dump driver, and publish tileclient-b's qa_dyn (a dynamic shortcut exists
# only after its app has run — T11-30).
seed_fixtures() {
  adb install -r -t "$A_APK" >/dev/null 2>&1 || { log "seed: tileclient-a install failed"; return 1; }
  adb install -r -t "$B_APK" >/dev/null 2>&1 || { log "seed: tileclient-b install failed"; return 1; }
  adb shell pm path app.tileshell.qa.imefixture >/dev/null 2>&1 || adb install -r -t "$FIX_APK" >/dev/null
  adb shell pm path app.tileshell.qa.imefixture.test >/dev/null 2>&1 || adb install -r -t "$DRV_APK" >/dev/null
  verb_b_start reset
}

# restore <baseline file name under qa/phase-11/>: verified restore, plus C-3's checks on the ring slice after it.
restore() {
  local file="$QA11/$1" mark rs
  ring_save
  mark="$(ring_mark)"
  layout_restore "$file" || { _verdict FAIL "restore $1" "layout_restore failed"; return 1; }
  rs="$(ring_since "$mark")"
  assert_absent "restore $1: no slot re-assigned on load (C-3)" "-> assigned" "$(echo "$rs" | grep 'assignSlotOnce')"
  local want got
  want="$(python3 -c 'import json,sys; print(",".join(sorted(json.load(open(sys.argv[1]))["addedOnce"])))' "$file")"
  got="$(layout_json | python3 -c 'import json,sys; print(",".join(sorted(json.load(sys.stdin)["addedOnce"])))')"
  assert_eq "restore $1: addedOnce as the file's (C-3)" "$want" "$got"
  ensure_start_page
}

# C-6: after anything that launched an app. The ring is saved first: the force-stop wipes it (T11-21).
c6() {
  ring_save
  adb shell am force-stop app.tileshell
  sleep 0.5
  adb shell input keyevent KEYCODE_HOME
  sleep 4
  ensure_start_page
}

# Start's page with nothing over it (phase 02's ensure_start: KEYCODE_HOME is not re-delivered to a resumed Start).
ensure_start_page() {
  local i d="$ROW_DIR/.start.xml"
  for i in 1 2 3; do
    qdump "$d" >/dev/null 2>&1 || true
    if grep -q 'resource-id="start_page"' "$d" && ! grep -q 'resource-id="app_list"' "$d"; then return 0; fi
    adb shell input keyevent KEYCODE_BACK; sleep 0.6
    adb shell input swipe 200 1200 950 1200 250; sleep 1.5
  done
  note "ensure_start_page: could not get back to Start"
  return 1
}

# ------------------------------------------------------------------ the dump route (C-10)

# The accessibility root comes back null (roots=0, in ~3 ms) on a fair share of runs while the page is changing —
# on a plain Start with its tiles flipping 4 of 8 runs failed, and in the first seconds of a burst 3 of 8
# (2026-09-23, probe in README) — so a dump is retried until it has nodes, and every retry is kept in the .drv log.
qdump() { # out.xml
  local out="$1"
  : > "$out.drv"
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    drv -e op dump -e out /sdcard/Download/q11.xml >> "$out.drv" 2>&1
    adb shell cat /sdcard/Download/q11.xml > "$out" 2>/dev/null
    grep -q '<node' "$out" && return 0
    sleep 0.7
  done
  echo "(dump failed)" > "$out"
  return 1
}

center() { # dump.xml resource-id -> "x y"
  local b
  b="$(bounds "$1" "$2")"
  [ -n "$b" ] || return 1
  python3 -c 'import sys; l,t,r,b=map(int,sys.argv[1].split()); print((l+r)//2, (t+b)//2)' "$b"
}

# ------------------------------------------------------------------ gestures

hold_down() { adb shell input motionevent DOWN "$1" "$2"; }
hold_move() { adb shell input motionevent MOVE "$1" "$2"; }
hold_up() { adb shell input motionevent UP "$1" "$2"; }

# hold <x> <y> [seconds]: DOWN, wait with the finger still down, UP.
hold() { hold_down "$1" "$2"; sleep "${3:-1.0}"; hold_up "$1" "$2"; sleep 0.6; }

tap_xy() { adb shell input tap "$1" "$2"; }

# ------------------------------------------------------------------ the fixture verbs

# A verb with NO window (ShortcutVerbReceiver, tileclient-b only): safe while a burst is open (T11-15).
verb_b() { adb shell am broadcast -f 0x20 -n "$B_PKG/app.tileshell.testclient.ShortcutVerbReceiver" --es verb "$1" 2>&1 | tr -d '\r' | tail -1; sleep 1; }
# The same verb through the activity; it comes to the front, so C-6 follows.
verb_b_start() { adb shell am start -W -n "$B_PKG/app.tileshell.testclient.VerbActivity" --es verb "$1" >/dev/null 2>&1; sleep 1.5; c6; }

# ------------------------------------------------------------------ readers

quick_since() { ring_since "$1" | grep -F '[quick]'; }

# The `[quick] satellite i rest=[l,t,r,b]` lines since a mark, as "i l t r b" rows (last value per satellite).
rest_lines() { # mark
  ring_since "$1" | python3 -c '
import re, sys
last = {}
for line in sys.stdin:
    m = re.search(r"\[quick\] satellite (\d) rest=\[(-?\d+),(-?\d+),(-?\d+),(-?\d+)\]", line)
    if m: last[int(m.group(1))] = m.groups()[1:]
for i in sorted(last): print(i, *last[i])'
}

# All node rects of a dump whose id starts with a prefix: "id l t r b" per line.
rects() { # dump.xml id-prefix
  python3 - "$1" "$2" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for node in re.finditer(r"<node[^>]*>", s):
    n = node.group(0)
    m = re.search(r'resource-id="(%s[^"]*)"' % re.escape(sys.argv[2]), n)
    b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', n)
    if m and b: print(m.group(1), *b.groups())
PY
}

# The text of node `id` or, if it has none, of its first descendant with text (T11-37: page_header).
subtree_text() { # dump.xml id
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        for d in n.iter("node"):
            if d.get("text"): print(d.get("text")); sys.exit(0)
        print(""); sys.exit(0)
PY
}

# Whether the activity on top is the given component ("pkg/cls"), from dumpsys (resumed activity line).
resumed() { adb shell dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -1 | tr -d '\r'; }
