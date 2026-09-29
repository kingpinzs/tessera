#!/usr/bin/env bash
# Phase 12 QA helpers, sourced by every row driver after lib.sh (the phase 03 floor, symlinked here; no third floor).
#
#   . "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p12.sh"
#
# Two AVDs can be attached to this host (tileshell_fhd and an unrelated one), so every adb call is pinned to the phase's
# AVD unless the caller already chose one.
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"

# lib.sh's QA is this phase's directory (docs/plan/qa/phase-12), so the other phases are one level up.
QAROOT="$(cd "$QA/.." && pwd)"
PROVISION="$QAROOT/phase-03/scripts/provision.sh"
LAYOUT_SH="$QAROOT/phase-02/scripts/layout.sh"
LISTENER="app.tileshell/app.tileshell.feeds.TileNotificationListener"
KEYBOARD="app.tileshell/.ime.KeyboardService"

# ---------------------------------------------------------------- the Start ring read (build task 4 (i), r3 V4)

# The diagnostics ring through StartActivity's dump (build task 2), which answers while notification access is revoked;
# the header must be there — empty or unavailable output is a FAIL for the caller, absence checks included.
start_dump() {
  adb shell dumpsys activity "$PKG/.StartActivity" 2>/dev/null
}

# Ring lines stamped wall >= MARK, read from StartActivity's dump. Returns 3 (and prints RING UNAVAILABLE on stderr) when
# the dump carries no `tileshell diagnostics: <n> entries` header.
start_ring_since() { # mark
  local out
  out="$(start_dump)"
  # A here-string, not a pipe: under lib.sh's pipefail, grep -q exiting on its match can kill the writer with SIGPIPE
  # (141) and fail the pipeline — a race that read present headers as missing (E11 run 3).
  if ! grep -qE '^[[:space:]]*tileshell diagnostics: [0-9]+ entries' <<<"$out"; then
    echo "RING UNAVAILABLE: StartActivity's dump has no diagnostics header" >&2
    return 3
  fi
  printf '%s\n' "$out" | python3 -c '
import re, sys
mark = int(sys.argv[1])
for line in sys.stdin:
    m = re.search(r"\bwall=(\d+)", line)
    if m and int(m.group(1)) >= mark:
        print(line.rstrip("\r\n"))
' "$1"
}

# Asserts the Start ring is readable, then leaves its slice since MARK in $SLICE (and appends it to the row's ring file).
# A dump without the header is kept whole (start-dump-miss-<n>.txt) so the miss can be diagnosed.
start_slice() { # mark label
  local mark="$1" label="${2:-slice}"
  if SLICE="$(start_ring_since "$mark")"; then
    _verdict PASS "start ring readable ($label)" "header present"
  else
    _verdict FAIL "start ring readable ($label)" "no diagnostics header in StartActivity's dump"
    start_dump > "$ROW_DIR/start-dump-miss-$(date +%s%N).txt" 2>&1
    SLICE=""
  fi
  { echo "### $label (since $mark)"; printf '%s\n' "$SLICE"; } >> "$ROW_DIR/ring-start.txt"
}

# Phase 01's read, the notification listener's dump: the doc's primary read wherever notification access is held
# (Acceptance preamble: "Diagnostics is read with phase 01's command, or through the Start ring read where notification
# access is revoked"). Same contract as start_slice: the header must be there, the slice lands in $SLICE and is kept.
listener_slice() { # mark label
  local mark="$1" label="${2:-slice}" out
  out="$(adb shell dumpsys activity service "$PKG/.feeds.TileNotificationListener" 2>/dev/null)"
  if grep -qE '^[[:space:]]*tileshell diagnostics: [0-9]+ entries' <<<"$out"; then
    _verdict PASS "listener ring readable ($label)" "header present"
    SLICE="$(printf '%s\n' "$out" | python3 -c '
import re, sys
mark = int(sys.argv[1])
for line in sys.stdin:
    m = re.search(r"\bwall=(\d+)", line)
    if m and int(m.group(1)) >= mark:
        print(line.rstrip("\r\n"))
' "$mark")"
  else
    _verdict FAIL "listener ring readable ($label)" "no diagnostics header in the listener's dump"
    printf '%s\n' "$out" > "$ROW_DIR/listener-dump-miss-$(date +%s%N).txt"
    SLICE=""
  fi
  { echo "### $label (since $mark)"; printf '%s\n' "$SLICE"; } >> "$ROW_DIR/ring-listener-slices.txt"
}

# The eight preset items as their EFFECTIVE values, one key=value per line: a key absent from start_theme.xml reads as
# ShellSettings' default (a fresh install writes no keys at all), so "equals the control" compares what the shell uses.
effective_items() { # prefs-xml-text
  printf '%s' "$1" | python3 -c '
import sys, xml.etree.ElementTree as ET
d = {"accent": "4278221015", "theme": "DARK", "background": "(none)", "transparency": "0.5", "press": "NONE",
     "transparency_effects": "true", "tess_lens": "hal", "keyboard_palette": "DARK"}
try:
    root = ET.fromstring(sys.stdin.read())
    for el in root:
        k = el.get("name")
        if k in d:
            d[k] = el.get("value") if el.get("value") is not None else (el.text or "")
except Exception:
    pass
for k in sorted(d):
    print(f"{k}={d[k]}")
'
}

# The [wizard] and [theme] lines of the current $SLICE, one per line, the "<date> wall=<n> " prefix dropped.
wizard_lines() {
  printf '%s\n' "$SLICE" | grep -E '\[(wizard|theme)\]' | sed -E 's/^.* wall=[0-9]+ //'
}

# ---------------------------------------------------------------- dumps

wiz_dump() { # out.xml   (dump_ui, kept in the row directory)
  dump_ui "$1"
}

# The wizard_step:<ns>:<id> key the dump shows, empty on the presets page or with no wizard.
wiz_step() { # dump.xml
  grep -oE 'resource-id="wizard_step:[a-z]+:[a-z_]+"' "$1" | head -1 | sed -E 's/resource-id="wizard_step://; s/"$//'
}

# The text of a node by resource-id, parsed as XML (uiautomator quotes an attribute holding " with ' instead, which a
# text="..." regex never matches) and unescaped.
ntext() { # dump.xml resource-id
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    print(""); sys.exit(0)
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        print(n.get("text", "")); break
else:
    print("")
PY
}

# `checked` of a node by resource-id (Compose reports a selected node as checkable/checked, not `selected`: phase 01
# e13_state.py, phase 05 e10.sh — this is what the doc's selected="true" reads as on this build).
node_checked() { # dump.xml resource-id
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if f'resource-id="{sys.argv[2]}"' in s:
        m = re.search(r'checked="([a-z]+)"', s)
        print(m.group(1) if m else "")
        break
PY
}

# ---------------------------------------------------------------- states

# Android relaunches the home activity the moment its process dies while it is on top, so a pm clear or a force-stop
# with Start in front gives a fresh Start that evaluates the wizard before the row's grants or revocations land. Every
# stop in these helpers therefore happens with Android's Settings in front; Home then creates Start after the state is set.
leave_home() {
  adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1
  sleep 1
}

# The E2 state (E2-E7): pm clear, then every grant revoked explicitly except the Home role, so no row depends on what
# pm clear happens to reset (BUILD_START/pm_clear.txt records that it keeps the roles and the appops).
e2_state() {
  ring_save launcher 2>/dev/null || true
  leave_home
  adb shell pm clear "$PKG" >/dev/null
  adb shell cmd notification disallow_listener "$LISTENER"
  adb shell ime disable "$KEYBOARD" >/dev/null
  # The ASSISTANT role grants SYSTEM_ALERT_WINDOW: removing it resets that appop to `default` (which reads allowed, the
  # permission being granted at install), so the role goes BEFORE the appops are set — the doc's postcondition is both
  # appops reading ignore before Home (r3 D6 / V3). USE_FULL_SCREEN_INTENT has a uid mode that outranks the package mode
  # on this image, so both are set (provision.sh restores the uid mode to allow).
  adb shell cmd role remove-role-holder android.app.role.ASSISTANT "$PKG"
  adb shell appops set "$PKG" GET_USAGE_STATS ignore
  adb shell appops set "$PKG" USE_FULL_SCREEN_INTENT ignore
  adb shell appops set --uid "$PKG" USE_FULL_SCREEN_INTENT ignore
  adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW ignore
  local p
  for p in READ_MEDIA_IMAGES READ_MEDIA_VISUAL_USER_SELECTED READ_MEDIA_AUDIO READ_CALENDAR WRITE_CALENDAR \
           ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION ACCESS_BACKGROUND_LOCATION RECORD_AUDIO READ_CONTACTS SEND_SMS \
           CALL_PHONE READ_CALL_LOG READ_SMS; do
    adb shell pm revoke "$PKG" "android.permission.$p" 2>/dev/null
  done
  note "e2_state: fsi=$(adb shell appops get $PKG USE_FULL_SCREEN_INTENT | tr -d '\r') overlay=$(adb shell appops get $PKG SYSTEM_ALERT_WINDOW | tr -d '\r')"
  note "e2_state: home=$(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r') assistant=[$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')]"
}

# Asserts the two phase 15 appops read `ignore` before Home (r3 D6 / V3).
assert_e2_appops() {
  local fsi saw
  fsi="$(adb shell appops get $PKG USE_FULL_SCREEN_INTENT | tr -d '\r')"
  saw="$(adb shell appops get $PKG SYSTEM_ALERT_WINDOW | tr -d '\r')"
  assert_contains "E2 state: USE_FULL_SCREEN_INTENT ignore" "ignore" "$fsi"
  assert_absent "E2 state: USE_FULL_SCREEN_INTENT no mode left at allow (uid or package)" "allow" "$fsi"
  assert_contains "E2 state: SYSTEM_ALERT_WINDOW ignore" "ignore" "$saw"
  assert_absent "E2 state: SYSTEM_ALERT_WINDOW not allow/default" "default" "$saw"
}

# "`pm clear` → `provision.sh` → Home" (C-4 (d)): the fresh state every row that changed things ends on. Logged to the
# row directory; its exit code is read from a file (Hard Rule 14).
restore_fresh() { # [label]
  local label="${1:-restore}"
  ring_save launcher 2>/dev/null || true
  leave_home
  adb shell pm clear "$PKG" >/dev/null
  bash "$PROVISION" > "$ROW_DIR/provision-$label.txt" 2>&1
  echo $? > "$ROW_DIR/provision-$label.rc"
  adb shell input keyevent KEYCODE_HOME
  sleep 3
  note "restore_fresh $label: provision rc=$(cat "$ROW_DIR/provision-$label.rc")"
}

# PROVISION_FINISH_WIZARD=0 provision: every grant, no marker.
provision_no_marker() { # [label]
  local label="${1:-nomarker}"
  PROVISION_FINISH_WIZARD=0 bash "$PROVISION" > "$ROW_DIR/provision-$label.txt" 2>&1
  echo $? > "$ROW_DIR/provision-$label.rc"
  note "provision_no_marker $label: rc=$(cat "$ROW_DIR/provision-$label.rc")"
}

# A resume with no process restart: open the Settings hub over Start, then Back to it.
resume_start() {
  adb shell am start -n "$PKG/.settings.SettingsActivity" >/dev/null 2>&1
  sleep 2
  adb shell input keyevent KEYCODE_BACK
  sleep 2
}

# Tap Not now on every step until the presets page shows; prints the step keys seen, one per line.
walk_not_now() { # dump-prefix [max]
  local prefix="$1" max="${2:-40}" i=0 key
  while [ "$i" -lt "$max" ]; do
    wiz_dump "$prefix-$i.xml"
    key="$(wiz_step "$prefix-$i.xml")"
    if [ -z "$key" ]; then
      [ "$(has_node "$prefix-$i.xml" wizard_presets)" = yes ] && echo "PRESETS"
      return 0
    fi
    echo "$key"
    tap_node "$prefix-$i.xml" wizard_not_now || return 1
    sleep 1.2
    i=$((i + 1))
  done
}

# ---------------------------------------------------------------- Android's own dialogs and pages

# Tap a node of the permission controller's dialog by its resource-id suffix (allow_button, deny_button,
# allow_foreground_only_button, allow_all_button, ...), falling back to its text. Returns 2 when the dialog is not there.
perm_tap() { # dump-out.xml id-suffix [text-regex]
  local out="$1" id="com.android.permissioncontroller:id/permission_$2" re="${3:-}"
  dump_ui "$out"
  if [ "$(has_node "$out" "$id")" = yes ]; then tap_node "$out" "$id"; return $?; fi
  if [ -n "$re" ]; then
    local b
    b="$(python3 - "$out" "$re" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.get("package", "").endswith("permissioncontroller") and re.search(sys.argv[2], n.get("text", ""), re.I):
        m = re.findall(r"-?\d+", n.get("bounds", ""))
        print(" ".join(m)); break
PY
)"
    if [ -n "$b" ]; then set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); return 0; fi
  fi
  return 2
}

# The resumed activity's component, e.g. com.android.settings/.SubSettings.
resumed_activity() {
  adb shell dumpsys activity activities | grep -m1 'topResumedActivity' | grep -oE '[a-z0-9_.]+/[A-Za-z0-9_.$]+' | head -1
}

# Tess's Settings page, through her session's menu (needs the ASSISTANT role).
open_tess_settings() { # dump-prefix
  local p="$1"
  adb shell input keyevent KEYCODE_ASSIST; sleep 3
  dump_ui "$p-home.xml"
  tap_node "$p-home.xml" cortana_menu_button || return 1
  sleep 1.5
  dump_ui "$p-pane.xml"
  tap_node "$p-pane.xml" cortana_pane_item_settings || return 1
  sleep 2
}

# Not now until the step KEY shows; leaves its dump at "$prefix-at.xml". Returns 1 if it never came.
walk_to() { # key dump-prefix [max]
  local key="$1" prefix="$2" max="${3:-30}" i=0 cur
  while [ "$i" -lt "$max" ]; do
    dump_ui "$prefix-at.xml"
    cur="$(wiz_step "$prefix-at.xml")"
    [ "$cur" = "$key" ] && return 0
    [ -z "$cur" ] && return 1
    tap_node "$prefix-at.xml" wizard_not_now
    sleep 1.2
    i=$((i + 1))
  done
  return 1
}

# Settings > Setup checklist in a fresh task: a plain `am start --es page CHECKLIST` only brings an already-running
# Settings task to the front, on whatever page it was left on (found in E14 run 1), so the extra is never read.
open_checklist() {
  adb shell am start -n "$PKG/.settings.SettingsActivity" --activity-clear-task --es page CHECKLIST >/dev/null 2>&1
  sleep 3
}

# A picture pushed to /sdcard/Pictures and scanned into MediaStore; prints its content://media URI (phase 13's p13.sh
# push_picture, copied rather than sourced so none of p13.sh's other definitions land in these rows).
push_picture() { # local.png device-path
  adb push "$1" "$2" >/dev/null
  adb shell content call --uri content://media --method scan_file --arg "$2" >/dev/null 2>&1
  sleep 1
  local id
  id="$(adb shell content query --uri content://media/external/images/media --projection _id:_data 2>/dev/null \
    | tr -d '\r' | grep -F "_data=${2/#\/sdcard\//\/storage\/emulated\/0\/}" | grep -o '_id=[0-9]*' | tail -1 | sed 's/_id=//')"
  [ -n "$id" ] && echo "content://media/external/images/media/$id"
}

# The largest neighbour step in Start's right-hand gutter column (x 1060-1077, y 200-2100): a checkerboard picture shows a
# sharp edge there (>= 100 levels); a missing or undecoded picture leaves the gutter flat.
checker_edge() { # screencap.png
  python3 - "$1" <<'PY'
import sys
from PIL import Image
img = Image.open(sys.argv[1]).convert("L")
best = 0
for y in range(200, 2100, 3):
    for x in range(1060, 1077):
        best = max(best, abs(img.getpixel((x + 1, y)) - img.getpixel((x, y))), abs(img.getpixel((x, y + 1)) - img.getpixel((x, y))))
print(best)
PY
}
