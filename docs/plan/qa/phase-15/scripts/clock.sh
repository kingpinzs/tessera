#!/usr/bin/env bash
# Phase 15's Alarms & Clock row helpers, sourced AFTER lib.sh and p15.sh by E3–E8, E10, E21, E23, E31–E33 and the
# clock edge-case drivers. Everything reads the device the row runs on (ANDROID_SERIAL); nothing is simulated.
#
# What lives here: the stores (alarms.json / timers.json / stopwatch.json in device-protected storage), the
# AlarmClock-API seeding the brief allows for rows whose subject is not the editor, `dumpsys alarm`'s pending
# clock entries and its "Next alarm clock" line, the ring's device state (the ALARM player, the vibration, the
# keyguard, the overlay window, the ring notification), the in-app deletes every row's restore uses, and the
# LoopSpinner drive E3's editor needs.

CLOCK_ACT="app.tileshell/.clock.ClockActivity"
API_ACT="app.tileshell/.clock.AlarmApiActivity"
STORE_DIR=/data/user_de/0/app.tileshell/files
CLOCK_TAG='tag=*walarm*:app.tileshell.clock.FIRE'
# 1080 px / 360 epx on this AVD (Acceptance preamble: both devices are 360 epx wide).
PX=3
SHELL_UID="$(adb shell cmd package list packages -U app.tileshell 2>/dev/null | tr -d '\r' | sed -n 's/^package:app.tileshell uid://p' | head -1)"

# ---------------------------------------------------------------- the stores

store_file() { adb shell "run-as app.tileshell cat $STORE_DIR/$1" < /dev/null 2>/dev/null | tr -d '\r'; }
store_alarms() { store_file alarms.json; }
store_timers() { store_file timers.json; }
store_stopwatch() { store_file stopwatch.json; }
alarm_ids() { store_alarms | python3 -c 'import json,sys
try:
    [print(a["id"]) for a in json.load(sys.stdin)]
except Exception: pass'; }
timer_ids() { store_timers | python3 -c 'import json,sys
try:
    [print(t["id"]) for t in json.load(sys.stdin)]
except Exception: pass'; }
# One field of one alarm / timer, as JSON would print it (null -> "null").
alarm_field() { store_alarms | python3 -c 'import json,sys
try:
    a=[x for x in json.load(sys.stdin) if x["id"]==sys.argv[1]]; print(json.dumps(a[0][sys.argv[2]]).strip("\"") if a else "")
except Exception: print("")' "$1" "$2"; }
timer_field() { store_timers | python3 -c 'import json,sys
try:
    a=[x for x in json.load(sys.stdin) if x["id"]==sys.argv[1]]; print(json.dumps(a[0][sys.argv[2]]).strip("\"") if a else "")
except Exception: print("")' "$1" "$2"; }

# ---------------------------------------------------------------- opening and seeding

open_clock() { # page (alarm | world_clock | timer | stopwatch)
  adb shell am start -W -n "$CLOCK_ACT" --es page "$1" >/dev/null 2>&1
  sleep 1.5
}

# The AlarmClock API (the brief's seeding route; T15-37): the shell uid holds SET_ALARM. Prints the created id,
# read from the ring's own `[alarms] api … -> created <id>` line since a mark taken just before.
api_alarm() { # HOUR MINUTE NAME [days as Calendar values "2,3,4"]
  local mark days=""
  mark="$(ring_mark)"
  [ -n "${4:-}" ] && days="--eial android.intent.extra.alarm.DAYS $4"
  # ONE string to adb shell (it re-joins its arguments): a name with a space would otherwise split into a second
  # word that am reads as the package (the lead, 2026-09-24: "probe two" -> pkg=two). The name is single-quoted.
  adb shell "am start -W -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR $1 --ei android.intent.extra.alarm.MINUTES $2 --es android.intent.extra.alarm.MESSAGE '$3' $days --ez android.intent.extra.alarm.SKIP_UI true -n $API_ACT" < /dev/null >/dev/null 2>&1
  sleep 1
  ring_since "$mark" | grep -oE 'api android.intent.action.SET_ALARM from \S+ -> created [a-z0-9]+' | tail -1 | awk '{print $NF}'
}
api_timer() { # SECONDS NAME
  local mark
  mark="$(ring_mark)"
  adb shell "am start -W -a android.intent.action.SET_TIMER --ei android.intent.extra.alarm.LENGTH $1 --es android.intent.extra.alarm.MESSAGE '$2' --ez android.intent.extra.alarm.SKIP_UI true -n $API_ACT" < /dev/null >/dev/null 2>&1
  sleep 1
  ring_since "$mark" | grep -oE 'api android.intent.action.SET_TIMER from \S+ -> created [a-z0-9]+' | tail -1 | awk '{print $NF}'
}

# ---------------------------------------------------------------- dumpsys alarm

# The shell's clock entries in the "N pending alarms:" block only (the same tag also appears in the history
# statistics), one per line: "<TYPE> <origWhen> <whenElapsed> <triggerTime|->" — origWhen is an RTC ms for an
# alarm (RTC_WAKEUP) and an elapsed ms for a timer (ELAPSED_WAKEUP); triggerTime is the "Alarm clock:" block's
# (setAlarmClock) and "-" for a timer, which has none.
clock_entries() {
  adb shell dumpsys alarm | tr -d '\r' | python3 -c '
import re, sys
inside = False; cur = None; out = []
def flush():
    if cur and cur.get("ours"): out.append("%s %s %s %s" % (cur["type"], cur["orig"], cur["elapsed"], cur.get("trigger", "-")))
for l in sys.stdin.read().splitlines():
    if re.match(r"^\s*\d+ pending alarms:", l): inside = True; continue
    if inside and not l.startswith("    "):
        flush(); cur = None; inside = False; continue
    if not inside: continue
    m = re.match(r"^    (\w+) #\d+: Alarm\{\S+ type \d+ origWhen (\d+) whenElapsed (\d+) app\.tileshell\}", l)
    if m:
        flush(); cur = {"type": m.group(1), "orig": m.group(2), "elapsed": m.group(3)}; continue
    if cur is None: continue
    if "tag=*walarm*:app.tileshell.clock.FIRE" in l: cur["ours"] = True
    m = re.search(r"triggerTime=(\S+ \S+)", l)
    if m: cur["trigger"] = m.group(1)
flush()
print("\n".join(out))'
}
# The RTC ms of the shell's armed alarm(s) (setAlarmClock entries), one per line.
alarm_trigger_ms() { clock_entries | awk '$1 == "RTC_WAKEUP" {print $2}'; }
# The elapsed ms of the shell's armed timer(s), one per line.
timer_trigger_elapsed() { clock_entries | awk '$1 == "ELAPSED_WAKEUP" {print $2}'; }
# The "Next alarm clock information" line for user 0: its RTC ms, empty when none.
next_alarm_clock_ms() {
  adb shell dumpsys alarm | tr -d '\r' | sed -n '/Next alarm clock information/,/^ *$/p' | sed -n 's/.*user:0 pendingSend:[a-z]* time:\([0-9]*\).*/\1/p' | head -1
}
device_elapsed_ms() { adb shell cat /proc/uptime | tr -d '\r' | awk '{printf "%d", $1 * 1000}'; }

# ---------------------------------------------------------------- the ring's device state

# The shell's players on usage ALARM (dumpsys audio, PlaybackActivityMonitor), one line each.
alarm_players() { adb shell dumpsys audio | tr -d '\r' | grep 'AudioPlaybackConfiguration' | grep "u/pid:$SHELL_UID/" | grep 'usage=USAGE_ALARM'; }
alarm_player_started() { alarm_players | grep -c 'state:started'; }
keyguard_showing() { adb shell dumpsys window | tr -d '\r' | grep -oE 'isKeyguardShowing=[a-z]+' | head -1 | cut -d= -f2; }
keyguard_occluded() { adb shell dumpsys window | tr -d '\r' | grep -oE 'mKeyguardOccluded=[a-z]+' | head -1 | cut -d= -f2; }
wakefulness() { adb shell dumpsys power | tr -d '\r' | grep -m1 'mWakefulness=' | sed 's/.*mWakefulness=//; s/ .*//'; }
current_focus() { adb shell dumpsys window | tr -d '\r' | grep -m1 'mCurrentFocus=' | sed 's/^ *//'; }
# The shell's overlay window (title TesseraRing) with its type, from `dumpsys window windows`; empty when none.
overlay_window() {
  adb shell dumpsys window windows | tr -d '\r' | python3 -c '
import re, sys
text = sys.stdin.read()
blocks = re.split(r"\n(?=  Window #\d+ )", text)
for b in blocks:
    if "TesseraRing" not in b.split("\n")[0]: continue
    ty = re.search(r"ty=(\w+)", b); pkg = re.search(r"package=(\S+)", b)
    print("%s type=%s package=%s" % (b.split("\n")[0].strip(), ty.group(1) if ty else "?", pkg.group(1) if pkg else "?"))'
}
# The shell's notifications (dumpsys notification --noredact), one block each; grep the one you need.
shell_notifications() {
  adb shell dumpsys notification --noredact | tr -d '\r' | python3 -c '
import re, sys
text = sys.stdin.read()
for b in re.split(r"\n(?=\s*NotificationRecord\()", text):
    if "pkg=app.tileshell" in b.split("\n")[0]: print(b.strip()); print("----")'
}
# One notification block by channel id; empty when none is posted.
# (exact: "channel=clock_ringing " must not match clock_ringing_quiet — E4b run 1)
notification_on_channel() { shell_notifications | python3 -c '
import re, sys
for b in sys.stdin.read().split("----"):
    if re.search(r"channel=%s(\s|\))" % re.escape(sys.argv[1]), b): print(b.strip()); break' "$1"; }
# The vibrator's current vibration lines naming the shell's uid.
current_vibration() { adb shell dumpsys vibrator_manager | tr -d '\r' | sed -n '/CurrentVibration\|mCurrentVibration\|Current vibration/,/^$/p' | head -20; }
vibrations_from_shell() { adb shell dumpsys vibrator_manager | tr -d '\r' | grep -E "uid=? *$SHELL_UID|app.tileshell" | head -5; }

# The one number in a ring line's field: field_of "$line" late -> the value after "late=".
field_of() { printf '%s\n' "$1" | grep -oE "$2=-?[0-9.]+" | head -1 | cut -d= -f2; }

# Poll the launcher ring since MARK for a line holding NEEDLE, up to TIMEOUT s. Prints the matching lines.
wait_ring() { # mark needle timeout_s
  local i found
  for i in $(seq 1 $(( ${3:-30} * 2 ))); do
    found="$(ring_since "$1" | grep -F -- "$2")"
    if [ -n "$found" ]; then printf '%s\n' "$found"; return 0; fi
    sleep 0.5
  done
  return 1
}

# ---------------------------------------------------------------- the keyguard

set_pin() { adb shell locksettings set-disabled false >/dev/null 2>&1; adb shell locksettings set-pin 1234 >/dev/null 2>&1; }
clear_pin() { adb shell locksettings clear --old 1234 >/dev/null 2>&1; adb shell locksettings set-disabled true >/dev/null 2>&1; }
# Sleep then wake: the lock screen is up (E4's setup). Prints isKeyguardShowing.
lock_and_wake() { adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 2; keyguard_showing; }
# Type the PIN into the bouncer and confirm.
unlock_with_pin() { adb shell wm dismiss-keyguard >/dev/null 2>&1; sleep 1.5; adb shell input text 1234; adb shell input keyevent KEYCODE_ENTER; sleep 3; }

# ---------------------------------------------------------------- dumps

node_desc() { # dump.xml resource-id -> the node's content-desc
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if f'resource-id="{sys.argv[2]}"' in s:
        m = re.search(r'content-desc="([^"]*)"', s)
        print(m.group(1) if m else "")
        break
PY
}
# The gesture driver's window list (kept beside a gdump as <out>.windows): "TYPE:package:bounds:focused:active;…".
gwindows() { cat "$1.windows" 2>/dev/null | sed 's/^gesture.dump.windows=//; s/^gesture.windows=//'; }
# Scroll a gesture-driver dump (a never-idle list) until a node is in it. The drag starts in the left margin (x 60),
# clear of the timer blocks' buttons, and when a plain `input swipe` moves nothing (EDGE_TIMERS run 1: six swipes
# from the ring button's centre, the same four blocks every time) the gesture driver's own UiDevice.swipe is tried;
# the log says which moved the list.
gscroll_to_node() { # out.xml resource-id [max-swipes]
  local out="$1" id="$2" max="${3:-8}" i=0 before after r
  gdump "$out" || return 1
  # The list keeps its scroll position across re-opens (the tab stays composed), so a node ABOVE the viewport is
  # never reached by downward searching (EDGE_TIMERS run 2: the first timers, after the list had been scrolled
  # down): rewind to the top first, until a drag down moves nothing.
  if [ "$(has_node "$out" "$id")" = no ]; then
    for r in 1 2 3 4 5 6; do
      before="$(grep -o 'resource-id="[^"]*"[^>]*bounds="[^"]*"' "$out" | md5sum)"
      adb shell input swipe 60 900 60 1900 400; sleep 0.8
      gdump "$out" || return 1
      after="$(grep -o 'resource-id="[^"]*"[^>]*bounds="[^"]*"' "$out" | md5sum)"
      [ "$before" = "$after" ] && break
    done
    note "gscroll_to_node: rewound to the top in $r drag(s)"
  fi
  while [ "$(has_node "$out" "$id")" = no ] && [ "$i" -lt "$max" ]; do
    before="$(grep -o 'resource-id="[^"]*"[^>]*bounds="[^"]*"' "$out" | md5sum)"
    adb shell input swipe 60 1900 60 900 400
    sleep 1
    i=$((i + 1))
    gdump "$out" || return 1
    after="$(grep -o 'resource-id="[^"]*"[^>]*bounds="[^"]*"' "$out" | md5sum)"
    if [ "$before" = "$after" ]; then
      adb shell am instrument -r -w -e op swipe -e points "60,1900;60,900" -e steps 30 "$DRV_RUNNER" >/dev/null 2>&1
      sleep 1
      gdump "$out" || return 1
      after="$(grep -o 'resource-id="[^"]*"[^>]*bounds="[^"]*"' "$out" | md5sum)"
      note "gscroll_to_node: input swipe moved nothing; UiDevice.swipe $([ "$before" = "$after" ] && echo 'moved nothing either' || echo 'moved the list')"
    fi
  done
  note "gscroll_to_node $id: $i swipe(s), found=$(has_node "$out" "$id")"
  [ "$(has_node "$out" "$id")" = yes ]
}

# Roll a LoopSpinner to VALUE by swipes inside its own bounds (E3: "the time picker driven by swipes at its loop
# bounds"), re-reading the spinner's content-desc (its selected value) after every swipe. A swipe of k rows is
# k × 32 epx; up = later values (LoopSpinner: offset -= dy / rowPx). The fling carry can add a row, so the loop
# corrects until the value reads back. Returns 1 when it never does.
spin_to() { # tag value values-csv
  local tag="$1" target="$2" csv="$3" d="$ROW_DIR/.spin_${1//[^a-z0-9]/_}.xml" i cur delta b cx cy dy dur steps
  for i in $(seq 0 16); do
    dump_ui "$d" || return 1
    cur="$(node_desc "$d" "$tag")"
    if [ "$cur" = "$target" ]; then note "spin $tag -> [$cur] after $i swipe(s)"; return 0; fi
    delta="$(python3 -c '
import sys
vals = sys.argv[1].split(","); n = len(vals)
try: ci, ti = vals.index(sys.argv[2]), vals.index(sys.argv[3])
except ValueError: print(0); sys.exit()
d = (ti - ci) % n
if d > n // 2: d -= n
print(max(-3, min(3, d)))' "$csv" "$cur" "$target")"
    [ "$delta" != 0 ] || { note "spin $tag: [$cur] and [$target] not both in the values"; return 1; }
    b="$(bounds "$d" "$tag")"
    # shellcheck disable=SC2086
    set -- $b
    cx=$(( ($1 + $3) / 2 )); cy=$(( ($2 + $4) / 2 ))
    steps=${delta#-}
    dy=$(( -delta * 32 * PX ))
    dur=$(( 400 * steps ))
    adb shell input swipe "$cx" "$cy" "$cx" $(( cy + dy )) "$dur"
    sleep 0.9
  done
  note "spin $tag: gave up at [$cur], wanted [$target]"
  return 1
}

# ---------------------------------------------------------------- deleting through the app (RV12 / T15-45)

# An alarm's row opens its editor; the editor's Delete removes it (AlarmTab.kt:213).
app_delete_alarm() { # id
  local d="$ROW_DIR/.del_$1.xml"
  open_clock alarm
  scroll_to_node "$d" "alarm_row:$1" 6 || { note "app_delete_alarm: no row alarm_row:$1"; return 1; }
  tap_node "$d" "alarm_row:$1"; sleep 1.2
  dump_ui "$d"
  tap_node "$d" "clock_bar:delete"; sleep 1.2
}
# A timer's name under its block opens its editor (TimerTab.kt:171); the editor's Delete removes it. The list
# never idles while a timer runs, so it is dumped through the gesture driver.
app_delete_timer() { # id
  local d="$ROW_DIR/.del_$1.xml"
  open_clock timer
  gscroll_to_node "$d" "timer_name:$1" 6 || { note "app_delete_timer: no timer_name:$1"; return 1; }
  gtap "$d" "timer_name:$1"; sleep 1.2
  dump_ui "$d"
  tap_node "$d" "clock_bar:delete"; sleep 1.2
}
# Everything the stores hold, deleted through the app; then Home.
app_delete_all() {
  local id
  for id in $(alarm_ids); do app_delete_alarm "$id"; done
  for id in $(timer_ids); do app_delete_timer "$id"; done
  adb shell input keyevent KEYCODE_HOME; sleep 1
}
# A ring left up (a row that failed mid-ring) is ended so the restore can run: its Dismiss at the dump's bounds,
# else a force-stop (the ring service is not exported, so the shell cannot send it DISMISS).
dismiss_any_ring() {
  if [ "$(alarm_player_started)" != 0 ] || [ -n "$(overlay_window)" ]; then
    local d="$ROW_DIR/.dismiss.xml"
    gdump "$d" >/dev/null 2>&1
    if [ "$(has_node "$d" ring_dismiss)" = yes ]; then gtap "$d" ring_dismiss; sleep 2; fi
    if [ "$(alarm_player_started)" != 0 ]; then adb shell am force-stop app.tileshell; sleep 2; fi
    note "dismiss_any_ring: a ring was up and was ended"
  fi
}

# (clock.sh's own gdump, with its new-file-per-dump and fallback fixes, moved into p15.sh's gdump, 2026-09-24.)

# The full-screen-intent facts every alarm row records at its top (Acceptance preamble, V24).
record_fsi() {
  record "appops USE_FULL_SCREEN_INTENT" "$(adb shell appops get app.tileshell USE_FULL_SCREEN_INTENT | tr -d '\r' | tr '\n' ' ')"
  record "dumpsys package USE_FULL_SCREEN_INTENT grant" "$(adb shell dumpsys package app.tileshell | tr -d '\r' | grep -m1 'android.permission.USE_FULL_SCREEN_INTENT: granted' | sed 's/^ *//')"
}

# The device's HH MM (24-hour) and h12 / AM-PM for an RTC ms in the device's zone.
device_hm() { # epoch_ms -> "H M"
  local tz; tz="$(adb shell getprop persist.sys.timezone | tr -d '\r')"
  python3 -c '
import sys, datetime, zoneinfo
t = datetime.datetime.fromtimestamp(int(sys.argv[2]) / 1000, zoneinfo.ZoneInfo(sys.argv[1] or "UTC"))
print(t.hour, t.minute)' "$tz" "$1"
}
# "h:mm AM" as ClockText.time prints it under the 12-hour setting.
time_12h() { # H M
  python3 -c '
import sys
h, m = int(sys.argv[1]), int(sys.argv[2])
print("%d:%02d %s" % (h % 12 or 12, m, "AM" if h < 12 else "PM"))' "$1" "$2"
}

# ---------------------------------------------------------------- dump readers for other packages' windows

# The bounds ("l t r b") of the first node of PACKAGE whose text is TEXT (E4b: SystemUI's heads-up "Dismiss").
bounds_by_text() { # dump.xml package text
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    # SystemUI draws action labels in capitals ("DISMISS"): the text match ignores case (E4b run 1).
    if f'package="{sys.argv[2]}"' in s and f' text="{sys.argv[3]}"'.lower() in s.lower():
        m = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
        print(" ".join(m.groups())); break
PY
}
# One line per node of PACKAGE: "resource-id|text" — a window's state, to compare before and after a step.
pkg_nodes() { # dump.xml package
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if f'package="{sys.argv[2]}"' in s:
        rid = re.search(r'resource-id="([^"]*)"', s); t = re.search(r' text="([^"]*)"', s)
        print("%s|%s" % (rid.group(1) if rid else "", t.group(1) if t else ""))
PY
}
# Tap a node found by package + text in a gesture-driver dump.
tap_by_text() { # dump.xml package text
  local b
  b="$(bounds_by_text "$1" "$2" "$3")"
  [ -n "$b" ] || { note "tap_by_text: no [$3] of $2 in $1"; return 1; }
  # shellcheck disable=SC2086
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

# ---------------------------------------------------------------- kills and reboots (T15-56)

# "hh:mm:ss" (a timer's digits) -> ms; "hh:mm:ss.cc" (the stopwatch's) -> ms.
hms_to_ms() { python3 -c '
import sys
t = sys.argv[1].strip(); cc = 0
if "." in t: t, c = t.split("."); cc = int(c) * 10
h, m, s = (int(x) for x in t.split(":"))
print(((h * 60 + m) * 60 + s) * 1000 + cc)' "$1" 2>/dev/null; }

# kill -9 the launcher process as root (phase 03 E12's form), root left off.
kill9_shell() {
  local pid; pid="$(adb shell pidof app.tileshell | tr -d '\r')"
  adb root >/dev/null 2>&1; adb wait-for-device
  adb shell kill -9 "$pid"
  adb unroot >/dev/null 2>&1; adb wait-for-device
  sleep 2
  echo "$pid"
}
# adb reboot, then the boot poll (sys.boot_completed = 1, up to 4 min). Prints the boot's wall-clock ms.
reboot_and_wait() {
  adb reboot; sleep 8; adb wait-for-device
  local i
  for i in $(seq 1 120); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
    sleep 2
  done
  sleep 4
  device_ms
}
# The last N "[timer] <id> remaining=… uptime=…" lines of a slice.
timer_lines() { # slice id [n]
  printf '%s\n' "$1" | grep -F "[timer] $2 remaining=" | tail -n "${3:-2}"
}

# The first text among a tagged node's descendants. The ring toast's `ring_snooze_for` is the ComboBox's clickable
# box; its label ("10 minutes") is an untagged child TextView (RingToast.kt:92-98), so the box's own text is "" —
# a deviation from the harness contract (the text node should carry the tag), reported by E4. This reads the label
# the box holds, never a sibling's or a parent's text.
node_child_text() { # dump.xml resource-id
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
i = xml.find('resource-id="%s"' % sys.argv[2])
if i < 0: print(""); sys.exit()
start = xml.rfind('<node', 0, i)
rest = xml[start:]
# the tagged node's own element, then its subtree up to the matching close
depth = 0; j = 0
for m in re.finditer(r'<node\b[^>]*?(/?)>|</node>', rest):
    if m.group(0).startswith('</node>'): depth -= 1
    elif m.group(1) == '/': pass
    else: depth += 1
    if depth == 0: j = m.end(); break
sub = rest[:j] if j else rest[:4000]
for n in re.finditer(r'<node[^>]*>', sub[sub.find('>') + 1:]):
    t = re.search(r' text="([^"]*)"', n.group(0))
    if t and t.group(1): print(t.group(1)); sys.exit()
print("")
PY
}

# ---------------------------------------------------------------- network (E8, phase 06 E18's form, T15-29)

# The shell uid's byte counters summed over every ident / set / tag bucket of `dumpsys netstats --uid`, as "rx tx".
# Read right after `dumpsys netstats --poll` printed "Forced poll".
shell_bytes() {
  adb shell dumpsys netstats --poll >/dev/null 2>&1
  adb shell dumpsys netstats --uid 2>/dev/null | tr -d '\r' | python3 -c '
import re, sys
uid = sys.argv[1]; rx = tx = 0; inside = False
for l in sys.stdin:
    if l.startswith("  ident="):
        inside = ("uid=%s " % uid) in l
        continue
    if inside:
        m = re.search(r"\brb=(\d+) rp=\d+ tb=(\d+)", l)
        if m: rx += int(m.group(1)); tx += int(m.group(2))
print(rx, tx)' "$SHELL_UID"
}
# A hold (the shell's one long press) on a node's centre.
hold_node() { # dump.xml resource-id [ms]
  local b
  b="$(bounds "$1" "$2")"
  [ -n "$b" ] || { note "hold_node: no $2 in $1"; return 1; }
  # shellcheck disable=SC2086
  set -- $b "${3:-900}"
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) "$5"
}
# Host-side world-clock expectations (WorldClockRules' forms): "time" -> the 12-hour time of a zone now (± the
# minutes given), "diff" -> W10M's difference line for ZONE against LOCAL at the host's now.
world_expect() { # time ZONE [minute-offset] | diff ZONE LOCAL [hour-offset]
  python3 - "$@" <<'PY'
import sys, datetime, zoneinfo
cmd = sys.argv[1]
if cmd == "time":
    z = zoneinfo.ZoneInfo(sys.argv[2]); off = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    t = datetime.datetime.now(z) + datetime.timedelta(minutes=off)
    print("%d:%02d %s" % (t.hour % 12 or 12, t.minute, "AM" if t.hour < 12 else "PM"))
else:
    zone, local = zoneinfo.ZoneInfo(sys.argv[2]), zoneinfo.ZoneInfo(sys.argv[3])
    now = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=int(sys.argv[4]) if len(sys.argv) > 4 else 0)
    there, here = now.astimezone(zone), now.astimezone(local)
    day = "Today" if there.date() == here.date() else there.strftime("%A")
    minutes = int((there.utcoffset() - here.utcoffset()).total_seconds() // 60)
    if minutes == 0: words = "same time"
    else:
        a = abs(minutes); h, m = a // 60, a % 60
        hours = None if h == 0 else ("1 hour" if h == 1 else "%d hours" % h)
        mins = None if m == 0 else ("1 minute" if m == 1 else "%d minutes" % m)
        words = " ".join(x for x in (hours, mins) if x) + (" ahead" if minutes > 0 else " behind")
    print("%s, %s" % (day, words))
PY
}

# ---------------------------------------------------------------- crashes (E7 / E31 run 1: the lap-row crash)

# The number of app.tileshell crashes dropbox holds (data_app_crash entries naming the process).
shell_crash_count() { adb shell dumpsys dropbox --print data_app_crash 2>/dev/null | tr -d '\r' | grep -c '^Process: app.tileshell$'; }
# The first exception line of the newest app.tileshell crash.
shell_last_crash() { adb shell dumpsys dropbox --print data_app_crash 2>/dev/null | tr -d '\r' | awk '/^Process: app.tileshell$/ {p=1} p && /Exception/ {l=$0} END {print l}'; }
# Android's "keeps stopping" dialog (package android) is closed when it is up, so the next open can proceed.
close_crash_dialog() {
  local d="$ROW_DIR/.crash.xml" b
  dump_ui "$d" >/dev/null 2>&1 || return 0
  for t in "Close app" "OK" "Open app again"; do
    b="$(bounds_by_text "$d" android "$t")"
    if [ -n "$b" ]; then
      # shellcheck disable=SC2086
      set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1; return 0
    fi
  done
}
# The Stopwatch tab crashes on every open while the store holds a lap (E7's DEFECT.md), so it cannot reset itself:
# the BASELINE restore for the rows after it removes stopwatch.json and restarts the process. Never used to pass a
# clause; every caller says so in its log.
stopwatch_store_reset() {
  adb shell "run-as app.tileshell rm -f $STORE_DIR/stopwatch.json" < /dev/null >/dev/null 2>&1
  adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 2
  note "stopwatch.json removed and the process restarted: the baseline restore around the lap-row crash (E7/DEFECT.md)"
}

# lib.sh's dump_ui accepts any non-empty file; on this image `uiautomator dump` sometimes returns an EMPTY hierarchy
# (one root node, no resource-ids — E21 run 2: three checklist dumps in a row, minutes apart) when the window list
# is not delivered in time. This override (clock.sh is sourced after lib.sh) retries until the dump holds nodes.
dump_ui() { # out.xml
  local out="$1" i
  for i in 1 2 3 4 5 6; do
    if adb shell uiautomator dump /sdcard/qa.xml >/dev/null 2>&1; then
      adb shell cat /sdcard/qa.xml > "$out" 2>/dev/null
      # occurrences, not lines: the dump is ONE line (the first form of this check counted 1 for every dump and made
      # every dump_ui take its six retries — EDGE_ALARMS run 3 ran for an hour on that)
      [ "$(grep -o 'resource-id="[^"]' "$out" | wc -l)" -ge 2 ] && return 0
    fi
    # After three plain dumps without a tagged node the gesture driver's dump is tried (it polls for the window list).
    if [ "$i" -ge 3 ] && gdump "$out.g" >/dev/null 2>&1 && [ "$(grep -o 'resource-id="[^"]' "$out.g" | wc -l)" -ge 2 ]; then
      mv "$out.g" "$out"; rm -f "$out.g.drv" "$out.g.windows"; return 0
    fi
    sleep 1
  done
  [ -s "$out" ] && return 0
  echo "(dump failed)" > "$out"
  return 1
}

# ---------------------------------------------------------------- the stopwatch and pinned-tile baselines (RV12)

# The stored stopwatch as "reset" (no file, or RESET: not running, nothing accumulated, no laps) or
# "running=<bool> accumulated=<ms> laps=<n>".
stopwatch_state() { store_stopwatch | python3 -c '
import json, sys
t = sys.stdin.read().strip()
if not t: print("reset"); sys.exit()
try: s = json.loads(t)
except Exception: print("unreadable"); sys.exit()
if not s.get("running") and not s.get("accumulatedMs") and not s.get("laps"): print("reset")
else: print("running=%s accumulated=%s laps=%d" % (str(s.get("running")).lower(), s.get("accumulatedMs"), len(s.get("laps") or [])))'; }

# The stopwatch baseline (T15-44: no running stopwatch; a row that reads the digits needs them at 00:00:00.00). The
# gate pass on 5558 found E7 starting on a stopwatch an earlier row had stopped at 00:09:13.40 without resetting: a
# leftover (running or stopped) is brought to RESET through the app — Stop, then Reset — the log says so, and the state
# the row then starts from is ASSERTED.
stopwatch_baseline() { # label
  local st d="$ROW_DIR/.sw_baseline.xml"
  st="$(stopwatch_state)"
  if [ "$st" != reset ]; then
    note "$1: a leftover stopwatch ($st) is brought to RESET through the app"
    open_clock stopwatch
    gdump "$d"
    case "$st" in running=true*) gtap "$d" stopwatch_play; sleep 1; gdump "$d" ;; esac
    gtap "$d" stopwatch_reset; sleep 1.5
    adb shell input keyevent KEYCODE_HOME; sleep 1
  fi
  assert_eq "$1: the stopwatch is at RESET (not running, 0 elapsed, no laps)" reset "$(stopwatch_state)"
}

# The shell's own secondary tiles (timer / stopwatch pins) in start_layout.json, one key per line.
clock_tile_keys() { adb shell "run-as app.tileshell cat files/start_layout.json" < /dev/null 2>/dev/null | tr -d '\r' | grep -oE 'secondary:app\.tileshell:(timer\.[a-z0-9]+|stopwatch)' | sort -u; }

# A gesture-driver dump of a screen the row EXPECTS to hold NODE (a ringing toast): p15.sh's gdump falls back to a plain
# `uiautomator dump` — which never sees the ring's overlay window — after the gesture driver returns 20 empty
# hierarchies in a row, and it does so in bursts: UiDevice reads getWindows() ~2 ms after its UiAutomation connects
# and logs "Active window root not found" when the window list is not delivered yet (probe on 5558, 2026-09-24:
# attempts 7–12 of 12 empty, then all 30 of the next probe fine). EDGE_ALARMS run 5's seven3 toast went undismissed
# that way and the next section's taps landed on it. Up to TRIES gdumps, 3 s apart; returns 1 when NODE never shows,
# so an absent toast still fails the clause that reads the dump. Never used where the row asserts an ABSENCE.
gdump_for() { # out.xml node [tries]
  local i n="${3:-3}"
  for i in $(seq 1 "$n"); do
    gdump "$1"
    if [ "$(has_node "$1" "$2")" = yes ]; then [ "$i" -gt 1 ] && note "gdump_for $2: in dump $i of $n"; return 0; fi
    [ "$i" -lt "$n" ] && sleep 3
  done
  note "gdump_for $2: not in any of $n dumps"; return 1
}
# The same for a screen whose OTHER windows matter (SystemUI's heads-up or shade): up to TRIES gdumps until one came
# from the gesture driver itself (its window report beside the dump is non-empty; the plain-dump fallback leaves it empty
# and holds the focused window only).
gdump_windows() { # out.xml [tries]
  local i n="${2:-3}"
  for i in $(seq 1 "$n"); do
    gdump "$1"
    if [ -s "$1.windows" ]; then [ "$i" -gt 1 ] && note "gdump_windows $(basename "$1"): gesture dump $i of $n"; return 0; fi
    [ "$i" -lt "$n" ] && sleep 3
  done
  note "gdump_windows $(basename "$1"): no gesture-driver dump in $n tries"; return 1
}
