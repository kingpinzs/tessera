#!/usr/bin/env bash
# EDGE_EXACT — the `USE_EXACT_ALARM` edge-case bullet: "USE_EXACT_ALARM revoked by a system (simulated: `appops set
# app.tileshell SCHEDULE_EXACT_ALARM deny` on a build with USE_EXACT_ALARM temporarily removed from the manifest, QA build
# only): the exact_alarms row is red, alarms arm inexactly, the notice is shown and spoken, and the alarm still rings
# within Android's inexact window."
# The QA build: a `git archive` of HEAD (whose app/ is the FINAL build 61c5b610's source) built in the scratchpad with the
# one manifest line removed (scratchpad/p15/edgeC2-build.sh; its path in EDGE_EXACT_APK), signed with the same debug key,
# installed with `adb install -r` (data kept). Then:
#   - the op denied; the process restarted → its re-arm line reads exact=false;
#   - Tess's checklist row `cortana_check:exact_alarms:missing` with a red glyph (Decisions: the row is Tess's checklist's,
#     cortana/CortanaChecklist.kt:56-57, "not the Setup checklist; T15-6" — the Setup checklist's rows are recorded);
#   - an alarm set through Tess (typed, the same path as speech): the reply spoken (`reply_since`) and the session's
#     shown text carry the notice reminders give ("Exact alarms are off, so it may be a few minutes late.",
#     cortana/action/ActionLayer.kt:423-425);
#   - it arms inexactly: `rearm (store change): 1 alarms, 0 timers, exact=false`, no Next alarm clock, no
#     `exactAllowReason` and a non-zero `window=` on its dumpsys alarm entry;
#   - it rings in real time (no clock jump) inside that window: `fired <id> late=<ms>` with 0 ≤ late ≤ window + 1 s, an
#     ALARM player of the shell started.
# The owner's no-microphone rule (2026-09-25): Tess is opened only to read her checklist and to take a TYPED request, which
# opens no microphone — but a reply that asks a question makes Tess listen for the answer (CortanaModel.listenAfter), so
# RECORD_AUDIO is revoked for the Tess steps (SherpaAsr checks it before it builds its AudioRecord) and granted back right
# after, the permission line asserted as found; mic_guard.sh asserts no capture happened on the device during the row.
# Restore: the alarm deleted through the app, the op back to default, the FINAL APK reinstalled (its md5 61c5b610716197e3
# asserted installed), the home activity set, force-stop + Home, USE_EXACT_ALARM granted and exact=true again.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"; . "$(dirname "$0")/mic_guard.sh"

# The FINAL build is the worktree's own APK: its md5 is read, not pinned, so a rebuild cannot leave the restore checking an old one.
FINAL_MD5="$(md5sum "$APK" | cut -c1-16)"
QA_APK="${EDGE_EXACT_APK:-/tmp/claude-1000/-home-jeremyking/5d5ffc39-5a2a-4c33-953f-07d71da27a45/scratchpad/p15/edgeC2-noexact/app/build/outputs/apk/debug/app-debug.apk}"
AAPT2="$(ls -d "$HOME"/Android/Sdk/build-tools/*/ | tail -1)aapt2"
row_begin EDGE_EXACT "USE_EXACT_ALARM removed and SCHEDULE_EXACT_ALARM denied (QA build): red row, inexact arm, notice, rings in the window"
mic_guard_begin
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring
assert_eq "baseline: the FINAL build is installed" "$FINAL_MD5" "$(installed_apk_id)"
QA_MD5="$(md5sum "$QA_APK" 2>/dev/null | cut -c1-16)"
record "the QA build" "$QA_APK md5 $QA_MD5 $(stat -c%s "$QA_APK" 2>/dev/null) bytes"
assert_absent "the QA build requests no USE_EXACT_ALARM (aapt2 dump permissions)" "USE_EXACT_ALARM" "$("$AAPT2" dump permissions "$QA_APK" 2>&1)"
assert_contains "… while the FINAL build does (positive control)" "USE_EXACT_ALARM" "$("$AAPT2" dump permissions "$APK" 2>&1)"
wall_of() { printf '%s\n' "$1" | grep -oE 'wall=[0-9]+' | head -1 | cut -d= -f2; }
# The shell's clock alarm entry in dumpsys alarm's pending block (its lines), for the window and the exact-allow reason.
clock_alarm_block() {
  adb shell dumpsys alarm | tr -d '\r' | python3 -c '
import re, sys
inside = False; cur = []; out = []
for l in sys.stdin.read().splitlines():
    if re.match(r"^\s*\d+ pending alarms:", l): inside = True; continue
    if inside and not l.startswith("    "):
        inside = False
    if not inside: continue
    if re.match(r"^    \w+ #\d+: Alarm\{", l):
        if cur and any("app.tileshell.clock.FIRE" in x for x in cur): out += cur
        cur = [l]
    else:
        cur.append(l)
if cur and any("app.tileshell.clock.FIRE" in x for x in cur): out += cur
print("\n".join(out))'
}
dur_ms() { # "+1m30s0ms" / "0" -> ms
  python3 -c '
import re, sys
s = sys.argv[1].strip().lstrip("+")
if s in ("0", ""): print(0); sys.exit()
t = 0
for n, u in re.findall(r"(\d+)(ms|h|m|s|d)", s):
    t += int(n) * {"d": 86400000, "h": 3600000, "m": 60000, "s": 1000, "ms": 1}[u]
print(t)' "$1"
}

# ---- the QA build installed, the op denied --------------------------------------------------------------------------------------------
CRASH0="$(shell_crash_count)"; note "app.tileshell crashes in dropbox before the row's install: $CRASH0"
MARK="$(ring_mark)"
adb install -r "$QA_APK" > "$ROW_DIR/install_qa.txt" 2>&1; echo $? > "$ROW_DIR/install_qa.rc"
assert_eq "the QA build installs over the FINAL one (adb install -r rc)" 0 "$(cat "$ROW_DIR/install_qa.rc")"
assert_eq "… and is the installed build now" "$QA_MD5" "$(installed_apk_id)"
adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity >/dev/null 2>&1
adb shell appops set app.tileshell SCHEDULE_EXACT_ALARM deny
record "appops SCHEDULE_EXACT_ALARM after deny" "$(adb shell appops get app.tileshell SCHEDULE_EXACT_ALARM | tr -d '\r' | paste -sd'|')"
record "dumpsys package: exact-alarm permissions of the QA build" "[$(adb shell dumpsys package app.tileshell | tr -d '\r' | grep -E 'EXACT_ALARM' | sed 's/^ *//' | paste -sd'|')]"
# The no-microphone guard for the Tess steps (header): revoked here, before the restart below (a revoke stops the process).
ra_line() { adb shell dumpsys package app.tileshell | tr -d '\r' | grep -m1 'android.permission.RECORD_AUDIO: granted' | sed 's/^ *//'; }
RA0="$(ra_line)"; note "RECORD_AUDIO before the Tess steps: $RA0"
adb shell pm revoke app.tileshell android.permission.RECORD_AUDIO
assert_contains "no-microphone rule: RECORD_AUDIO is revoked for the Tess steps" "RECORD_AUDIO: granted=false" "$(ra_line)"
MARK="$(ring_mark)"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 4
REARM="$(wait_ring "$MARK" "[alarms] rearm (" 20 | head -1)"
note "the re-arm at the restart: $REARM"
assert_contains "the restarted process arms inexactly (its re-arm line reads exact=false)" "exact=false" "$REARM"

# ---- Tess's checklist: the exact_alarms row is red -----------------------------------------------------------------------------------
# The row glyph's colour, as E21 reads a checklist row's glyph: the most common non-background colour in the row's left
# 150 px over its whole height (red 232,17,35 for MISSING).
row_glyph_rgb() { # dump.xml tag png
  local b; b="$(bounds "$1" "$2")"; [ -n "$b" ] || { echo ""; return; }
  python3 - "$3" $b <<'PY'
import collections, sys
from PIL import Image
x0, y0, x1, y1 = (int(v) for v in sys.argv[2:6])
im = Image.open(sys.argv[1]).convert("RGB").crop((x0, y0, min(x1, x0 + 150), y1))
c = collections.Counter(p for p in im.getdata() if max(p) > 60)
print(",".join(str(v) for v in c.most_common(1)[0][0]) if c else "")
PY
}
adb shell input keyevent KEYCODE_HOME; sleep 1; cortana_assist; sleep 5
dump_ui "$ROW_DIR/tess_home.xml"; tap_node "$ROW_DIR/tess_home.xml" cortana_menu_button; sleep 2
dump_ui "$ROW_DIR/tess_pane.xml"; tap_node "$ROW_DIR/tess_pane.xml" cortana_pane_item_settings; sleep 3
D="$ROW_DIR/tess_checklist.xml"; dump_ui "$D"; i=0
while ! grep -q 'resource-id="cortana_check:exact_alarms:' "$D" && [ "$i" -lt 10 ]; do
  adb shell input swipe 540 1700 540 800 320; sleep 1; i=$((i + 1)); dump_ui "$D"
done
note "Tess's settings scrolled $i swipe(s) to: $(grep -o 'resource-id="cortana_check:exact_alarms:[a-z]*"' "$D" | head -1)"
screencap "$ROW_DIR/tess_checklist.png"
assert_eq "Tess's checklist: the exact_alarms row reads missing" yes "$(has_node "$D" cortana_check:exact_alarms:missing)"
RGB="$(row_glyph_rgb "$D" cortana_check:exact_alarms:missing "$ROW_DIR/tess_checklist.png")"
record "the exact_alarms row's glyph colour (red is 232,17,35)" "$RGB"
assert_eq "… its glyph is red (232,17,35 ± 16)" yes "$(python3 -c '
import sys
a = [int(x) for x in (sys.argv[1] or "0,0,0").split(",")]; b = [232, 17, 35]
print("yes" if all(abs(x - y) <= 16 for x, y in zip(a, b)) else "no (%s)" % sys.argv[1])' "$RGB")"
record "the row's texts" "$(python3 - "$D" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
i = xml.find('resource-id="cortana_check:exact_alarms:')
start = xml.rfind("<node", 0, i); seg = xml[start:start + 3000]
print("|".join(t for t in re.findall(r' text="([^"]+)"', seg)[:4]))
PY
)"
cortana_close; adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page CHECKLIST >/dev/null 2>&1; sleep 2.5
dump_ui "$ROW_DIR/setup_checklist.xml"
record "the Setup checklist's rows on screen (the Decisions keep exact_alarms in Tess's checklist, T15-6)" "$(grep -o 'resource-id="checklist:[^"]*"' "$ROW_DIR/setup_checklist.xml" | cut -d'"' -f2 | paste -sd' ')"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- Tess sets an alarm: the notice spoken and shown; the alarm armed inexactly -----------------------------------------------------------
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + 150000 )))"
SAY="$(python3 -c 'import sys; h, m = int(sys.argv[1]), int(sys.argv[2]); print("set an alarm for %d:%02d %s" % (h % 12 or 12, m, "am" if h < 12 else "pm"))' "$AH" "$AM_")"
note "typed request: [$SAY]"
cortana_assist; sleep 5
MARK="$(ring_mark)"
type_request "$SAY" 8
REPLY="$(reply_since "$MARK")"
dump_ui "$ROW_DIR/tess_alarm_reply.xml"; screencap "$ROW_DIR/tess_alarm_reply.png"
ring_since "$MARK" launcher > "$ROW_DIR/ring_exact_launcher.txt"
SHOWN="$(grep -o ' text="[^"]*"' "$ROW_DIR/tess_alarm_reply.xml" | cut -d'"' -f2 | paste -sd'|')"
note "reply spoken: [$REPLY]; session texts: [$SHOWN]"
AID="$(grep -oE '\[cortana\] alarm set in-process [a-z0-9]+' "$ROW_DIR/ring_exact_launcher.txt" | tail -1 | awk '{print $NF}')"
assert_ne "Tess set an alarm in-process" "" "$AID"
assert_eq "… at the typed time" "$AH:$AM_" "$(alarm_field "$AID" hour):$(alarm_field "$AID" minute)"
assert_contains "the reply confirms it" "Alarm set for" "$REPLY"
assert_contains "the notice is SPOKEN (the reply carries 'Exact alarms are off')" "Exact alarms are off" "$REPLY"
assert_contains "the notice is SHOWN (the session's text carries 'Exact alarms are off')" "Exact alarms are off" "$SHOWN"
cortana_close; adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO
assert_eq "no-microphone rule: RECORD_AUDIO granted back as found after the Tess steps" "$RA0" "$(ra_line)"
open_clock alarm; dump_ui "$ROW_DIR/alarm_tab.xml"
record "the Alarm tab's texts mentioning exact alarms or lateness" "[$(grep -o ' text="[^"]*"' "$ROW_DIR/alarm_tab.xml" | cut -d'"' -f2 | grep -iE 'exact|late' | paste -sd'|')]"
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_contains "it arms inexactly: rearm (store change): 1 alarms, 0 timers, exact=false" "[alarms] rearm (store change): 1 alarms, 0 timers, exact=false" "$(cat "$ROW_DIR/ring_exact_launcher.txt")"
BLOCK="$(clock_alarm_block)"; printf '%s\n' "$BLOCK" > "$ROW_DIR/dumpsys_alarm_entry.txt"
note "the entry: $(printf '%s' "$BLOCK" | tr -s ' ' | tr '\n' '|' | cut -c1-600)"
assert_ne "… its dumpsys alarm entry exists (RTC_WAKEUP, clock.FIRE)" "" "$(printf '%s' "$BLOCK" | grep -E '^    RTC_WAKEUP #')"
assert_eq "… with no Next alarm clock (not setAlarmClock)" "" "$(next_alarm_clock_ms)"
assert_absent "… and no exact-allow reason on the entry" "exactAllowReason" "$BLOCK"
WIN="$(dur_ms "$(printf '%s' "$BLOCK" | grep -oE 'window=[^ ]+' | head -1 | cut -d= -f2)")"
ORIG="$(printf '%s' "$BLOCK" | grep -oE 'origWhen [0-9]+' | head -1 | awk '{print $2}')"
record "the entry's window (ms) and origWhen" "window=$WIN origWhen=$ORIG"
assert_eq "… a non-zero inexact window" yes "$([ -n "$WIN" ] && [ "$WIN" -gt 0 ] && echo yes || echo "no ($WIN)")"

# ---- it rings in real time inside the window ------------------------------------------------------------------------------------------
NOW="$(device_ms)"
WAIT=$(( (ORIG - NOW + WIN) / 1000 + 60 ))
note "waiting up to $WAIT s for the fire (origWhen $ORIG, window $WIN ms)"
MARK="$(ring_mark)"
FIRED="$(wait_ring "$MARK" "[alarms] fired $AID kind=alarm" "$WAIT")"
sleep 3
ring_since "$MARK" launcher > "$ROW_DIR/ring_exact_fire_launcher.txt"
note "fired: ${FIRED:-none}"
assert_ne "the alarm rings (fired $AID)" "" "$FIRED"
LATE="$(field_of "$FIRED" late)"
assert_eq "… inside Android's inexact window (0 ≤ late ≤ window + 1000 ms)" yes "$([ -n "$LATE" ] && [ "$LATE" -ge 0 ] && [ "$LATE" -le $(( WIN + 1000 )) ] && echo yes || echo "no (late=$LATE window=$WIN)")"
assert_ne "… and sounds (an ALARM player of the shell is started)" 0 "$(alarm_player_started)"
record "the ring's surface line" "$(grep -oE '\[alarms\] surface: [a-z-]+ [^ ]+' "$ROW_DIR/ring_exact_fire_launcher.txt" | head -1)"
# A ring that never shows may be a process that died starting it: the crash entries are kept (run 1 found two, read by
# hand after the row: the ring service's systemExempted foreground start refused without an exact-alarm permission).
adb shell dumpsys dropbox --print data_app_crash 2>/dev/null | tr -d '\r' > "$ROW_DIR/dropbox_data_app_crash.txt"
assert_eq "… with no crash of app.tileshell while it was due (dropbox data_app_crash count unchanged)" "$CRASH0" "$(shell_crash_count)"
record "the newest app.tileshell crash's cause" "$(grep '^Caused by' "$ROW_DIR/dropbox_data_app_crash.txt" | tail -1 | cut -c1-400)"
d="$ROW_DIR/exact_ring.xml"; gdump_for "$d" ring_dismiss; screencap "$ROW_DIR/exact_ring.png"
M2="$(ring_mark)"
[ "$(has_node "$d" ring_dismiss)" = yes ] && gtap "$d" ring_dismiss; sleep 2.5
[ -z "$(ring_since "$M2" | grep -F 'ring ended')" ] && { note "no ring ended line after Dismiss; dismiss_any_ring"; dismiss_any_ring; }

# ---- restore ----------------------------------------------------------------------------------------------------------------------------
ring_save launcher
app_delete_all
adb shell appops set app.tileshell SCHEDULE_EXACT_ALARM default
record "appops SCHEDULE_EXACT_ALARM after restore" "$(adb shell appops get app.tileshell SCHEDULE_EXACT_ALARM | tr -d '\r' | paste -sd'|')"
adb install -r "$APK" > "$ROW_DIR/install_final.txt" 2>&1; echo $? > "$ROW_DIR/install_final.rc"
assert_eq "restore: the FINAL APK reinstalls (adb install -r rc)" 0 "$(cat "$ROW_DIR/install_final.rc")"
assert_eq "restore: the FINAL build ($FINAL_MD5) is installed again" "$FINAL_MD5" "$(installed_apk_id)"
adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity >/dev/null 2>&1
MARK="$(ring_mark)"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 4
assert_contains "restore: the home activity is the shell's Start" "app.tileshell.StartActivity" "$(adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | grep -m1 'name=')"
assert_contains "restore: USE_EXACT_ALARM is granted again" "USE_EXACT_ALARM: granted=true" "$(adb shell dumpsys package app.tileshell | tr -d '\r' | grep 'android.permission.USE_EXACT_ALARM')"
assert_contains "restore: the restarted process arms exactly again (exact=true)" "exact=true" "$(wait_ring "$MARK" "[alarms] rearm (" 20 | head -1)"
assert_clock_empty "restore"
mic_guard_end
row_end
