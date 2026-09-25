#!/usr/bin/env bash
# E6 — Timer across process death and reboot (phase 15 T15-9, T15-53, T15-55, T15-56).
# Pass 1: a 90-s timer (AlarmClock API, running) → `dumpsys alarm` holds an ELAPSED_WAKEUP alarm of the shell due
#   90 s ± 1 s after the start and NOT in the Next alarm clock line; the Timer tab in front (its `[timer]` lines every
#   5 s); the pre-kill slice saved; `kill -9` as root; reopen → `timer_remaining:<id>` (gesture-driver dump) reads
#   90 − elapsed ± 2 s on the host's clock, the alarm still armed at the same instant, two `[timer]` lines 5 s apart
#   satisfy Δremaining = −Δuptime ± 100 ms, and the first post-kill line's remaining is the last pre-kill line's minus
#   their wall= difference ± 1 s; at the deadline it rings on the in-use overlay timer toast (kind=timer; E4's audio
#   and notification checks; `ring_dismiss`, no `ring_snooze`) and Dismiss stops it.
# Pass 2: a 10-min timer, `adb reboot`, the boot poll, `wake_device` = Awake → re-armed for its original wall-clock
#   deadline, the boot's rearm line holds "0 alarms, 1 timers", and its remaining is right.
# Pass 3 (T15-53): a 1-min timer; when its `[timer]` line reads ≤ 10 000, `adb reboot`; the same poll; precondition:
#   the first post-boot clock is past start + 60 s; it fires at once with the "ended while the phone was off" text.
#   Then force-stop + Home: a running timer is re-armed at process start. Restore: every timer deleted through the app.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin E6 "timer across kill -9 and reboot; rings on the elapsed clock; ended-while-off"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring

# ---- pass 1: 90 s, kill -9 --------------------------------------------------------------------------------------------
open_clock timer
START_HOST="$(date +%s%3N)"; START_EL="$(device_elapsed_ms)"
TID="$(api_timer 90 "Ninety")"
assert_ne "a 90-s timer was created" "" "$TID"
TRIG="$(timer_trigger_elapsed | head -1)"; TRIG="${TRIG:-0}"
assert_within "dumpsys alarm: an ELAPSED_WAKEUP alarm of the shell due 90 s ± 1 s after the start" $(( START_EL + 90000 )) "$TRIG" 1000
assert_eq "… and NOT in the Next alarm clock line" "" "$(next_alarm_clock_ms)"
open_clock timer
sleep 7
PRE="$(ring_since "$ROW_MARK")"
printf '%s\n' "$PRE" > "$ROW_DIR/ring-launcher-prekill.txt"
LAST_PRE="$(timer_lines "$PRE" "$TID" 1)"; note "last pre-kill: ${LAST_PRE#*] }"
assert_ne "the Timer tab logs [timer] $TID lines before the kill" "" "$LAST_PRE"
PID0="$(kill9_shell)"; note "killed pid $PID0"
assert_ne "the process is gone after kill -9" "$PID0" "$(adb shell pidof app.tileshell | tr -d '\r')"
MARK="$(ring_mark)"
open_clock timer
sleep 1
HOST_NOW="$(date +%s%3N)"
gdump "$ROW_DIR/after_kill.xml"; screencap "$ROW_DIR/after_kill.png"
REM_TXT="$(node_text "$ROW_DIR/after_kill.xml" "timer_remaining:$TID")"; note "timer_remaining after the kill: [$REM_TXT] at host +$(( HOST_NOW - START_HOST )) ms"
assert_within "reopen: timer_remaining reads 90 − elapsed ± 2 s (host clock)" $(( 90000 - (HOST_NOW - START_HOST) )) "$(hms_to_ms "$REM_TXT")" 2000
assert_eq "reopen: the alarm is still armed at the same elapsed instant" "$TRIG" "$(timer_trigger_elapsed | head -1)"
sleep 7
POST="$(ring_since "$MARK")"
FIRST_POST="$(timer_lines "$POST" "$TID" 99 | head -1)"; note "first post-kill: ${FIRST_POST#*] }"
TWO="$(timer_lines "$POST" "$TID" 2)"
note "two lines 5 s apart: $(printf '%s\n' "$TWO" | sed 's/.*\] //' | paste -sd'|')"
read -r DREM DUP <<< "$(printf '%s\n' "$TWO" | python3 -c '
import re, sys
ls = [l for l in sys.stdin.read().splitlines() if l.strip()]
if len(ls) < 2: print("x x"); sys.exit()
def f(l, k): return int(re.search(k + r"=(-?\d+)", l).group(1))
print(f(ls[1], "remaining") - f(ls[0], "remaining"), f(ls[1], "uptime") - f(ls[0], "uptime"))')"
assert_within "two [timer] lines 5 s apart: Δremaining = −Δuptime ± 100 ms" "$(( -${DUP/x/0} ))" "${DREM/x/999999}" 100
assert_within "the first post-kill remaining = the last pre-kill remaining − their wall difference ± 1 s" \
  "$(( $(field_of "$LAST_PRE" remaining) - ($(field_of "$FIRST_POST" wall) - $(field_of "$LAST_PRE" wall)) ))" "$(field_of "$FIRST_POST" remaining)" 1000
# The deadline: it rings on the overlay timer toast (the Timer tab is in front, unlocked).
FIRED="$(wait_ring "$MARK" "[alarms] fired $TID kind=timer" 100)"
assert_ne "the timer fired at its deadline" "" "$FIRED"
assert_within "timer: late <= 1000 ms" 0 "$(field_of "$FIRED" late)" 1000
sleep 3
assert_ne "timer: an ALARM player of the shell is started" 0 "$(alarm_player_started)"
assert_contains "timer: surface: toast-overlay $TID" "[alarms] surface: toast-overlay $TID" "$(ring_since "$MARK")"
TN="$(notification_on_channel clock_ringing_quiet)"; printf '%s\n' "$TN" > "$ROW_DIR/notification_timer.txt"
assert_contains "timer: the ring notification (quiet channel, in use) has category alarm" "category=alarm" "$TN"
assert_contains "timer: … a Dismiss action" "Dismiss" "$TN"
assert_absent "timer: … and no Snooze action" "Snooze" "$TN"
gdump "$ROW_DIR/timer_toast.xml"; screencap "$ROW_DIR/timer_toast.png"
assert_eq "timer toast: ring_dismiss" yes "$(has_node "$ROW_DIR/timer_toast.xml" ring_dismiss)"
assert_eq "timer toast: no ring_snooze" no "$(has_node "$ROW_DIR/timer_toast.xml" ring_snooze)"
MARK="$(ring_mark)"
gtap "$ROW_DIR/timer_toast.xml" ring_dismiss; sleep 3
assert_contains "timer dismiss: ring ended $TID: dismiss" "[alarms] ring ended $TID: dismiss" "$(ring_since "$MARK")"
assert_eq "timer dismiss: the player stopped" 0 "$(alarm_player_started)"
app_delete_timer "$TID"
assert_eq "pass 1 cleared: no timer in the store" "" "$(timer_ids | paste -sd,)"

# ---- pass 2: 10 min, adb reboot ----------------------------------------------------------------------------------------
TID2="$(api_timer 600 "Ten")"
assert_ne "a 10-min timer was created" "" "$TID2"
DL_WALL="$(timer_field "$TID2" deadlineWallMs)"; note "wall-clock deadline $DL_WALL"
ring_save launcher; cp "$ROW_DIR/ring-launcher.txt" "$ROW_DIR/ring-launcher-prekill.txt" 2>/dev/null
BOOT_MS="$(reboot_and_wait)"; note "booted at $BOOT_MS"
assert_eq "after the reboot wake_device prints Awake (C-25)" "Awake" "$(wake_device)"
sleep 3
BOOTRING="$(diag)"
printf '%s\n' "$BOOTRING" | grep -F '[alarms] rearm (' > "$ROW_DIR/boot_rearm_lines.txt"
FIRSTREARM="$(printf '%s\n' "$BOOTRING" | grep -F '[alarms] rearm (' | head -1)"; note "first rearm after boot: ${FIRSTREARM#*] }"
# The doc names "(locked boot) or (boot), whichever the no-PIN boot sends first"; on this AVD Android's TIME_SET
# broadcast reaches the receiver first, so the boot's FIRST line is "rearm (time set)" (run 1) — recorded, and the
# two lines the doc names are both asserted present with the timer counted.
record "the boot's first rearm line (doc: locked boot or boot)" "${FIRSTREARM#*] }"
assert_contains "the boot's ring holds rearm (locked boot): 0 alarms, 1 timers" "[alarms] rearm (locked boot): 0 alarms, 1 timers" "$BOOTRING"
assert_contains "… and rearm (boot): 0 alarms, 1 timers" "[alarms] rearm (boot): 0 alarms, 1 timers" "$BOOTRING"
TRIG2="$(timer_trigger_elapsed | head -1)"; TRIG2="${TRIG2:-0}"
NOW_EL="$(device_elapsed_ms)"; NOW_MS="$(device_ms)"
assert_within "re-armed for its original wall-clock deadline (elapsed trigger − now = deadline − now ± 3 s)" $(( DL_WALL - NOW_MS )) $(( TRIG2 - NOW_EL )) 3000
open_clock timer
NOW_MS="$(device_ms)"
gdump "$ROW_DIR/after_reboot.xml"; screencap "$ROW_DIR/after_reboot.png"
assert_within "its remaining is right after the reboot (± 3 s)" $(( DL_WALL - NOW_MS )) "$(hms_to_ms "$(node_text "$ROW_DIR/after_reboot.xml" "timer_remaining:$TID2")")" 3000
app_delete_timer "$TID2"

# ---- pass 3: 1 min, reboot inside the last 10 s (T15-53) ---------------------------------------------------------------
open_clock timer
TID3="$(api_timer 60 "Minute")"
assert_ne "a 1-min timer was created" "" "$TID3"
START3="$(timer_field "$TID3" deadlineWallMs)"; START3=$(( START3 - 60000 ))
open_clock timer
REM=999999
for _ in $(seq 1 70); do
  L="$(timer_lines "$(ring_since "$ROW_MARK")" "$TID3" 1)"
  REM="$(field_of "$L" remaining)"; REM="${REM:-999999}"
  [ "$REM" -le 10000 ] && break
  sleep 1
done
note "rebooting at remaining=$REM"
assert_eq "the [timer] line read remaining <= 10 000 before the reboot" yes "$([ "$REM" -le 10000 ] && echo yes || echo no)"
ring_since "$ROW_MARK" >> "$ROW_DIR/ring-launcher-prekill.txt"
BOOT3="$(reboot_and_wait)"
assert_eq "the first post-boot clock is later than start + 60 s (precondition, else void)" yes "$([ "$BOOT3" -gt $(( START3 + 60000 )) ] && echo yes || echo no)"
assert_eq "after the reboot wake_device prints Awake (C-25)" "Awake" "$(wake_device)"
sleep 3
BOOTRING="$(diag)"
FIRED3="$(printf '%s\n' "$BOOTRING" | grep -F "[alarms] fired $TID3 kind=timer" | head -1)"
assert_ne "it fired at once after the boot" "" "$FIRED3"
assert_contains "the ring says the timer ended while the phone was off" "timer $TID3 ended while the phone was off" "$BOOTRING"
TN3="$(notification_on_channel clock_ringing)"; [ -n "$TN3" ] || TN3="$(notification_on_channel clock_ringing_quiet)"
printf '%s\n' "$TN3" > "$ROW_DIR/notification_ended_off.txt"
assert_contains "the notification text says it ended while the phone was off (Decisions 'Ringing'; T15-53)" "ended while the phone was off" "$TN3"
record "the ended-while-off notification's title / text" "$(printf '%s\n' "$TN3" | grep -oE '(android.title|android.text)=[^ ]*( [^=]*)?' | head -2 | paste -sd'|')"
sleep 1
gdump "$ROW_DIR/ended_off.xml"; screencap "$ROW_DIR/ended_off.png"
if [ "$(has_node "$ROW_DIR/ended_off.xml" ring_dismiss)" = yes ]; then gtap "$ROW_DIR/ended_off.xml" ring_dismiss; sleep 2; fi
dismiss_any_ring

# ---- force-stop: a running timer is re-armed at process start (phase 03 E6's rule) ---------------------------------------
TID4="$(api_timer 300 "Five")"
assert_ne "a running 5-min timer for the force-stop check" "" "$TID4"
T4="$(timer_trigger_elapsed | paste -sd,)"
# The shell is the HOME app: the system relaunches it the instant it is force-stopped, so the "cancelled until the
# next start" state lasts milliseconds (run 1 found the process already re-armed 2 s later). The MARK is taken
# BEFORE the force-stop, the entry is polled through the gap, and the re-arm at the restart is the assertion.
MARK="$(ring_mark)"
adb shell am force-stop app.tileshell
GAP="present"
for _ in 1 2 3 4 5 6 7 8 9 10; do [ -z "$(timer_trigger_elapsed)" ] && { GAP="absent"; break; }; sleep 0.2; done
record "the timer's alarm right after the force-stop (polled 2 s; the home app restarts at once)" "$GAP"
adb shell input keyevent KEYCODE_HOME
assert_ne "the restart re-arms it: rearm (process start): 0 alarms, 1 timers" "" "$(wait_ring "$MARK" "[alarms] rearm (process start): 0 alarms, 1 timers" 20)"
assert_eq "… and the ELAPSED_WAKEUP entry is back at the same instant" "$T4" "$(timer_trigger_elapsed | paste -sd,)"

# ---- restore ----------------------------------------------------------------------------------------------------------------
app_delete_all
assert_clock_empty "restore"
row_end
