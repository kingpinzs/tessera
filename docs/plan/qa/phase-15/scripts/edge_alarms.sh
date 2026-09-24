#!/usr/bin/env bash
# EDGE_ALARMS — the phase 15 Alarms & Clock edge cases that ring: DST (America/Denver: 02:30 on 2027-03-14 rings at
# 03:00 MDT; a daily 01:30 on 2027-11-07 rings at its first 01:30 only; a daily 07:00 across each night keeps 07:00
# with a 23- and a 25-hour gap in dumpsys alarm); a time-zone change with the app closed (kill -9 first) and while
# the ring surface shows; the clock moved BACK two days past an armed one-shot (stays armed for its date, never rings
# twice); the clock moved FORWARD past three alarms (fired in order, the earlier ones superseded and missed, one
# surface); a LATER alarm firing while one rings (superseded); two alarms in one minute (one toast, both names, Snooze
# and Dismiss act on both); an alarm edited while it rings (heads-up route: the ring continues, the store takes the
# edit); an alarm during a call (the overlay over the in-call UI, MODE_IN_CALL, the track ≈ 1/8, vibration); a reboot
# straddling an alarm (rings on boot inside the timeout) and the "Missed alarm" path past it; an update
# (`adb install -r`) with an alarm 1 min ahead (MY_PACKAGE_REPLACED re-arms it and it rings). The USE_EXACT_ALARM-
# removed QA build is NOT RUN (this pass may build nothing; recorded). Restore: RV12's clock restore, the zone, every
# alarm deleted through the app, force-stop + Home, gsm cancel, the overlay grant.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin EDGE_ALARMS "alarm edge cases: DST, zone changes, clock back / forward, supersede, same minute, edit while ringing, in-call, reboot straddle, update"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring
TZ0="$(adb shell getprop persist.sys.timezone | tr -d '\r')"
ms_of() { python3 -c 'import sys, datetime, zoneinfo; print(int(datetime.datetime.fromisoformat(sys.argv[1]).replace(tzinfo=zoneinfo.ZoneInfo(sys.argv[2])).timestamp() * 1000))' "$1" "$2"; }
utc_ms() { python3 -c 'import sys, datetime; print(int(datetime.datetime.fromisoformat(sys.argv[1] + "+00:00").timestamp() * 1000))' "$1"; }
# Jump to 10 s before AT, wait for the fired line, and hand back the slice mark.
ring_at() { # id at -> prints the mark
  local m
  jump_clock $(( $2 - 10000 )) >/dev/null
  m="$(ring_mark)"
  wait_ring "$m" "[alarms] fired $1 kind=alarm" 30 >/dev/null || note "ring_at: $1 did not fire within 30 s of the jump to $2"
  sleep 2.5
  echo "$m"
}
dismiss_toast() { # label
  local d="$ROW_DIR/${1}_ring.xml" m
  gdump "$d"; screencap "$ROW_DIR/${1}_ring.png"
  m="$(ring_mark)"
  gtap "$d" ring_dismiss; sleep 2.5
  [ -n "$(ring_since "$m" | grep -F 'ring ended')" ] || note "dismiss_toast $1: no ring ended line"
}
# Every section ends here: everything deleted through the app and the store ASSERTED empty (run 1: a daily alarm one
# section failed to delete was armed under every later section's reads).
section_clear() { # label
  app_delete_all
  assert_eq "$1: the section left no alarm behind" "" "$(alarm_ids | paste -sd,)"
}
record "USE_EXACT_ALARM-removed QA build (SCHEDULE_EXACT_ALARM denied on a manifest without USE_EXACT_ALARM)" "NOT RUN: this pass builds nothing (the brief), so the QA build does not exist"

# ================================================================== DST (America/Denver) ==============================================
adb shell cmd alarm set-timezone America/Denver; sleep 2
assert_eq "the zone is America/Denver" "America/Denver" "$(adb shell getprop persist.sys.timezone | tr -d '\r')"
# ---- spring forward: 02:30 on 2027-03-14 does not exist → 03:00:00 MDT ----------------------------------------------------------
jump_clock "$(utc_ms 2027-03-14T08:00:00)" >/dev/null       # 01:00 MST
ID="$(api_alarm 2 30 "Spring")"
assert_ne "the 02:30 alarm was created" "" "$ID"
assert_eq "it is armed for 2027-03-14" "2027-03-14" "$(alarm_field "$ID" date)"
assert_eq "dumpsys alarm arms it at 03:00:00 MDT = 09:00:00Z (the skipped hour)" "$(utc_ms 2027-03-14T09:00:00)" "$(alarm_trigger_ms | paste -sd,)"
M="$(ring_at "$ID" "$(utc_ms 2027-03-14T09:00:00)")"
FIRED="$(ring_since "$M" | grep -F "[alarms] fired $ID")"
assert_ne "it rang" "" "$FIRED"
assert_eq "… no earlier than 09:00:00Z" yes "$([ "$(field_of "$FIRED" wall)" -ge "$(utc_ms 2027-03-14T09:00:00)" ] && echo yes || echo no)"
gdump "$ROW_DIR/spring.xml"
assert_eq "the toast's time reads 3:00 AM (diagnostics of the skipped hour)" "3:00 AM" "$(node_text "$ROW_DIR/spring.xml" ring_time)"
dismiss_toast spring
section_clear "section"
# ---- fall back: a daily 01:30 on 2027-11-07 rings at its FIRST 01:30 (MDT, 07:30Z) only ----------------------------------------------
jump_clock "$(utc_ms 2027-11-07T06:30:00)" >/dev/null       # 00:30 MDT
ID="$(api_alarm 1 30 "Fall" "1,2,3,4,5,6,7")"
assert_ne "the daily 01:30 alarm was created" "" "$ID"
assert_eq "it is armed at the first 01:30 (MDT, 07:30Z), not the second (MST, 08:30Z)" "$(utc_ms 2027-11-07T07:30:00)" "$(alarm_trigger_ms | paste -sd,)"
M="$(ring_at "$ID" "$(utc_ms 2027-11-07T07:30:00)")"
assert_ne "it rang at the first 01:30" "" "$(ring_since "$M" | grep -F "[alarms] fired $ID")"
dismiss_toast fall
assert_eq "after Dismiss it re-arms for the NEXT day's 01:30 (MST, 2027-11-08 08:30Z), not the same night's second 01:30" "$(utc_ms 2027-11-08T08:30:00)" "$(alarm_trigger_ms | paste -sd,)"
section_clear "section"
# ---- a daily 07:00 across each night: 23- and 25-hour gaps ---------------------------------------------------------------------------------
jump_clock "$(utc_ms 2027-03-13T13:00:00)" >/dev/null       # 06:00 MST, the day before the spring change
ID="$(api_alarm 7 0 "Seven" "1,2,3,4,5,6,7")"
assert_ne "the daily 07:00 alarm was created" "" "$ID"
T1="$(alarm_trigger_ms | head -1)"
assert_eq "armed for 2027-03-13 07:00 MST (14:00Z)" "$(utc_ms 2027-03-13T14:00:00)" "$T1"
M="$(ring_at "$ID" "$T1")"; assert_ne "it rang on the 13th" "" "$(ring_since "$M" | grep -F "[alarms] fired $ID")"; dismiss_toast seven1
T2="$(alarm_trigger_ms | head -1)"
assert_eq "next: 2027-03-14 07:00 MDT (13:00Z) — a 23-hour gap" "$(utc_ms 2027-03-14T13:00:00)" "$T2"
assert_eq "… the gap in dumpsys alarm is 23 h" 23 "$(( (T2 - T1) / 3600000 ))"
M="$(ring_at "$ID" "$T2")"; assert_ne "it rang on the 14th at 07:00 wall-clock" "" "$(ring_since "$M" | grep -F "[alarms] fired $ID")"
gdump "$ROW_DIR/seven2.xml"; assert_eq "… the toast reads 7:00 AM" "7:00 AM" "$(node_text "$ROW_DIR/seven2.xml" ring_time)"; dismiss_toast seven2
jump_clock "$(utc_ms 2027-11-06T12:00:00)" >/dev/null       # 06:00 MDT, the day before the fall change
sleep 2
T3="$(alarm_trigger_ms | head -1)"
assert_eq "on 2027-11-06 the daily is armed at 07:00 MDT (13:00Z)" "$(utc_ms 2027-11-06T13:00:00)" "$T3"
M="$(ring_at "$ID" "$T3")"; assert_ne "it rang on the 6th" "" "$(ring_since "$M" | grep -F "[alarms] fired $ID")"; dismiss_toast seven3
T4="$(alarm_trigger_ms | head -1)"
assert_eq "next: 2027-11-07 07:00 MST (14:00Z) — a 25-hour gap" "$(utc_ms 2027-11-07T14:00:00)" "$T4"
assert_eq "… the gap in dumpsys alarm is 25 h" 25 "$(( (T4 - T3) / 3600000 ))"
section_clear "section"
ring_save launcher
clock_restore
adb shell cmd alarm set-timezone "$TZ0"; sleep 2

# ================================================================== zone change: app closed; while ringing ===========================
ID="$(api_alarm 7 0 "Zone")"
AT0="$(alarm_trigger_ms | head -1)"; ADATE="$(alarm_field "$ID" date)"
adb shell input keyevent KEYCODE_HOME; sleep 1
kill9_shell >/dev/null
MARK="$(ring_mark)"
adb shell cmd alarm set-timezone Europe/London; sleep 5
assert_ne "zone change with the app closed: the receiver re-armed in a fresh process" "" "$(wait_ring "$MARK" "[alarms] rearm (time zone changed)" 15)"
EXP="$(ms_of "${ADATE}T07:00:00" Europe/London)"
assert_eq "… the alarm's instant moved to London's 07:00" "$EXP" "$(alarm_trigger_ms | paste -sd,)"
open_clock alarm; dump_ui "$ROW_DIR/zone_row.xml"
assert_eq "… and the row still reads 7:00 AM" "7:00 AM" "$(node_text "$ROW_DIR/zone_row.xml" "alarm_time:$ID")"
adb shell cmd alarm set-timezone "$TZ0"; sleep 2
section_clear "section"
NOW="$(device_ms)"; read -r H Mi <<< "$(device_hm $(( NOW + 120000 )))"
ID="$(api_alarm "$H" "$Mi" "Ringing")"
AT="$(alarm_trigger_ms | head -1)"
M="$(ring_at "$ID" "$AT")"
assert_ne "an alarm is ringing" 0 "$(alarm_player_started)"
MARK="$(ring_mark)"
adb shell cmd alarm set-timezone Asia/Tokyo; sleep 4
assert_ne "zone change while ringing: the re-arm line" "" "$(ring_since "$MARK" | grep -F '[alarms] rearm (time zone changed)')"
assert_ne "… the ring continues (player started)" 0 "$(alarm_player_started)"
gdump "$ROW_DIR/zone_ringing.xml"
assert_eq "… the toast is still up" yes "$(has_node "$ROW_DIR/zone_ringing.xml" ring_surface)"
assert_absent "… and the ring was not ended by the zone change" "ring ended $ID" "$(ring_since "$MARK")"
dismiss_toast zone_ringing
adb shell cmd alarm set-timezone "$TZ0"; sleep 2
clock_restore
section_clear "section"

# ================================================================== clock moved BACK past an armed one-shot ===========================
NOW="$(device_ms)"; read -r H Mi <<< "$(device_hm $(( NOW + 180000 )))"
ID="$(api_alarm "$H" "$Mi" "Back")"
T="$(alarm_trigger_ms | head -1)"; DATE0="$(alarm_field "$ID" date)"
jump_clock $(( NOW - 2 * 86400000 )) >/dev/null; sleep 2
assert_eq "clock back two days: the one-shot stays armed for its stored date" "$DATE0" "$(alarm_field "$ID" date)"
assert_eq "… at the same instant in dumpsys alarm" "$T" "$(alarm_trigger_ms | paste -sd,)"
M="$(ring_at "$ID" "$T")"
assert_ne "it rings once, at its instant" "" "$(ring_since "$M" | grep -F "[alarms] fired $ID")"
dismiss_toast back
jump_clock $(( T - 2 * 86400000 )) >/dev/null; sleep 2
assert_eq "clock back again: a rung one-shot is not re-armed (it never rings twice)" "" "$(alarm_trigger_ms | paste -sd,)"
assert_eq "… it is off in the store" false "$(alarm_field "$ID" enabled)"
clock_restore
section_clear "section"

# ================================================================== clock moved FORWARD past several alarms ==========================
NOW="$(device_ms)"
read -r H1 M1 <<< "$(device_hm $(( NOW + 120000 )))"; read -r H2 M2 <<< "$(device_hm $(( NOW + 180000 )))"; read -r H3 M3 <<< "$(device_hm $(( NOW + 240000 )))"
A1="$(api_alarm "$H1" "$M1" "First")"; A2="$(api_alarm "$H2" "$M2" "Second")"; A3="$(api_alarm "$H3" "$M3" "Third")"
assert_eq "three one-shots armed a minute apart" 3 "$(alarm_trigger_ms | wc -l)"
adb shell input keyevent KEYCODE_HOME; sleep 1
# The three fire within the jump's own second, so the slice starts a second before the target instant (run 1's
# mark, taken after jump_clock's sleep, missed every fired line).
MARK=$(( NOW + 300000 - 1000 ))
jump_clock $(( NOW + 300000 )) >/dev/null
wait_ring "$MARK" "[alarms] fired $A3 kind=alarm" 40 >/dev/null; sleep 3
SLICE="$(ring_since "$MARK")"
ORDER="$(printf '%s\n' "$SLICE" | grep -oE 'fired a[0-9a-f]+ kind=alarm' | awk '{print $2}' | paste -sd' ')"
assert_eq "forward past three: they fired in order" "$A1 $A2 $A3" "$ORDER"
assert_contains "the first was superseded" "[alarms] ring ended $A1: superseded" "$SLICE"
assert_contains "the second was superseded" "[alarms] ring ended $A2: superseded" "$SLICE"
assert_contains "the first counts as missed" "[alarms] ring ended $A1: missed" "$SLICE"
gdump "$ROW_DIR/forward.xml"; screencap "$ROW_DIR/forward.png"
assert_eq "one ring surface" 1 "$(grep -c 'resource-id="ring_surface"' "$ROW_DIR/forward.xml")"
assert_eq "… showing the last alarm" "Third" "$(node_text "$ROW_DIR/forward.xml" ring_name)"
MN="$(notification_on_channel clock_missed)"; printf '%s\n' "$(shell_notifications)" > "$ROW_DIR/notifications_forward.txt"
assert_contains "a Missed alarm notification for the first" "Missed alarm $(time_12h "$H1" "$M1")" "$(shell_notifications)"
assert_contains "… and for the second" "Missed alarm $(time_12h "$H2" "$M2")" "$(shell_notifications)"
dismiss_toast forward
clock_restore
section_clear "forward"

# ================================================================== a LATER alarm fires while one rings =================================
NOW="$(device_ms)"
read -r H1 M1 <<< "$(device_hm $(( NOW + 120000 )))"; read -r H2 M2 <<< "$(device_hm $(( NOW + 180000 )))"
A1="$(api_alarm "$H1" "$M1" "Early")"; A2="$(api_alarm "$H2" "$M2" "Later")"
T1="$(alarm_trigger_ms | sort -n | head -1)"; T2="$(alarm_trigger_ms | sort -n | tail -1)"
adb shell input keyevent KEYCODE_HOME; sleep 1
M="$(ring_at "$A1" "$T1")"
assert_ne "the early alarm rings" 0 "$(alarm_player_started)"
MARK="$(ring_mark)"
jump_clock $(( T2 - 10000 )) >/dev/null
wait_ring "$MARK" "[alarms] fired $A2 kind=alarm" 30 >/dev/null; sleep 3
SLICE="$(ring_since "$MARK")"
assert_contains "the later alarm takes the surface: ring ended $A1: superseded" "[alarms] ring ended $A1: superseded" "$SLICE"
assert_contains "… and the first is missed" "[alarms] ring ended $A1: missed" "$SLICE"
assert_contains "… with a Missed alarm notification" "Missed alarm $(time_12h "$H1" "$M1")" "$(shell_notifications)"
gdump "$ROW_DIR/later.xml"
assert_eq "the toast now shows the later alarm" "Later" "$(node_text "$ROW_DIR/later.xml" ring_name)"
dismiss_toast later
clock_restore
section_clear "section"

# ================================================================== two alarms in the same minute ========================================
NOW="$(device_ms)"; read -r H Mi <<< "$(device_hm $(( NOW + 120000 )))"
A1="$(api_alarm "$H" "$Mi" "Ann")"; A2="$(api_alarm "$H" "$Mi" "Bob")"
T="$(alarm_trigger_ms | head -1)"
adb shell input keyevent KEYCODE_HOME; sleep 1
jump_clock $(( T - 10000 )) >/dev/null
MARK="$(ring_mark)"
wait_ring "$MARK" "[alarms] fired $A2 kind=alarm" 30 >/dev/null; wait_ring "$MARK" "[alarms] fired $A1 kind=alarm" 10 >/dev/null; sleep 3
gdump "$ROW_DIR/same_minute.xml"; screencap "$ROW_DIR/same_minute.png"
assert_eq "same minute: one toast" 1 "$(grep -c 'resource-id="ring_surface"' "$ROW_DIR/same_minute.xml")"
RN="$(node_text "$ROW_DIR/same_minute.xml" ring_name)"; note "ring_name: $RN"
assert_contains "… ring_name lists Ann" "Ann" "$RN"; assert_contains "… and Bob" "Bob" "$RN"
assert_absent "… neither was superseded" "superseded" "$(ring_since "$MARK")"
MARK="$(ring_mark)"
gtap "$ROW_DIR/same_minute.xml" ring_snooze; sleep 3
assert_contains "Snooze acts on Ann" "[alarms] ring ended $A1: snooze" "$(ring_since "$MARK")"
assert_contains "… and on Bob" "[alarms] ring ended $A2: snooze" "$(ring_since "$MARK")"
S1="$(alarm_field "$A1" snoozedUntilMs)"; S2="$(alarm_field "$A2" snoozedUntilMs)"
assert_ne "both hold a snooze instant" "null" "$S1$S2"
jump_clock $(( S1 - 10000 )) >/dev/null
MARK="$(ring_mark)"
wait_ring "$MARK" "[alarms] fired $A2 kind=alarm" 30 >/dev/null; wait_ring "$MARK" "[alarms] fired $A1 kind=alarm" 10 >/dev/null; sleep 3
gdump "$ROW_DIR/same_minute2.xml"
assert_eq "both ring together again after the snooze (one toast)" 1 "$(grep -c 'resource-id="ring_surface"' "$ROW_DIR/same_minute2.xml")"
MARK="$(ring_mark)"
gtap "$ROW_DIR/same_minute2.xml" ring_dismiss; sleep 3
assert_contains "Dismiss acts on Ann" "[alarms] ring ended $A1: dismiss" "$(ring_since "$MARK")"
assert_contains "… and on Bob" "[alarms] ring ended $A2: dismiss" "$(ring_since "$MARK")"
clock_restore
section_clear "section"

# ================================================================== an alarm edited while it rings (heads-up route) =======================
adb shell appops set app.tileshell SYSTEM_ALERT_WINDOW deny
NOW="$(device_ms)"; read -r H Mi <<< "$(device_hm $(( NOW + 120000 )))"
ID="$(api_alarm "$H" "$Mi" "Edit")"
T="$(alarm_trigger_ms | head -1)"
adb shell input keyevent KEYCODE_HOME; sleep 1
M="$(ring_at "$ID" "$T")"
assert_contains "it rings as a heads-up (no overlay grant)" "[alarms] surface: heads-up $ID" "$(ring_since "$M")"
sleep 8   # the heads-up retracts into the status bar; a tap meant for the alarm row must not land on it
open_clock alarm; dump_ui "$ROW_DIR/edit_list.xml"
tap_node "$ROW_DIR/edit_list.xml" "alarm_row:$ID"; sleep 1.5
dump_ui "$ROW_DIR/edit_editor.xml"
tap_node "$ROW_DIR/edit_editor.xml" 'alarm_editor_field:snooze'; sleep 1.2
dump_ui "$ROW_DIR/edit_snooze.xml"
tap_node "$ROW_DIR/edit_snooze.xml" 'alarm_snooze:20'; sleep 1
dump_ui "$ROW_DIR/edit_set.xml"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/edit_set.xml" 'clock_bar:save'; sleep 2
assert_eq "the store took the edit (snooze 20)" 20 "$(alarm_field "$ID" snoozeMinutes)"
assert_ne "the ring continues (player still started)" 0 "$(alarm_player_started)"
assert_absent "… and the edit did not end the ring" "ring ended $ID" "$(ring_since "$MARK")"
assert_contains "… the edit re-armed the store" "[alarms] store: alarm $ID edited in the editor" "$(ring_since "$MARK")"
adb shell cmd statusbar expand-notifications; sleep 2
gdump "$ROW_DIR/edit_shade.xml"; screencap "$ROW_DIR/edit_shade.png"
MARK="$(ring_mark)"
tap_by_text "$ROW_DIR/edit_shade.xml" com.android.systemui Dismiss && sleep 3
assert_contains "the notification's Dismiss ends it" "[alarms] ring ended $ID: dismiss" "$(ring_since "$MARK")"
adb shell cmd statusbar collapse; sleep 1
dismiss_any_ring
adb shell appops set app.tileshell SYSTEM_ALERT_WINDOW allow
clock_restore
section_clear "section"

# ================================================================== an alarm during a call ================================================
# The out-of-call reference: a ring's ALARM track line in audio_flinger (columns after Active/Client: … Usg CT G L R VS).
# The shell's track lines of a dump (the file is kept). On this image the Client column prints as two tokens,
# "<pid>/" and "<uid>" (sample: `89 yes 13923/ 10150 401 49 S 0x601 … 44100 4 4 4 -4.8 0 0 0 -4.8`), so the columns
# after Active are: pid/ uid Session PortId S Flags Format Chn SRate ST Usg CT G L R VS PortVol.
track_line() { # label -> the shell's active track lines; the whole dump kept as flinger_<label>.txt
  adb shell dumpsys media.audio_flinger | tr -d '\r' > "$ROW_DIR/flinger_$1.txt"
  grep -E "(^| )yes +[0-9]+/ +$SHELL_UID( |$)" "$ROW_DIR/flinger_$1.txt"
}
track_cols() { # lines -> "G L R VS PortVol" of the first line whose Usg is 4 (ALARM), else of the first shell line
  printf '%s\n' "$1" | awk -v uid="$SHELL_UID" '
    { for (i = 1; i <= 4; i++) if ($i == "yes" && $(i+1) ~ /\/$/ && $(i+2) == uid) {
        l = $(i+13) " " $(i+14) " " $(i+15) " " $(i+16) " " $(i+17)
        if ($(i+11) == 4) { print l; found = 1; exit }
        if (!seen) { first = l; seen = 1 } } }
    END { if (!found && seen) print first }'
}
NOW="$(device_ms)"; read -r H Mi <<< "$(device_hm $(( NOW + 120000 )))"
ID="$(api_alarm "$H" "$Mi" "Quiet")"
T="$(alarm_trigger_ms | head -1)"
adb shell input keyevent KEYCODE_HOME; sleep 1
M="$(ring_at "$ID" "$T")"
REF="$(track_line ref)"; note "out-of-call track: $(echo "$REF" | tr -s ' ' | cut -c1-200)"
REFC="$(track_cols "$REF")"; record "out-of-call G / L / R / VS / PortVol (dB)" "$REFC"
assert_contains "out of call the ring level is full" "[alarms] ring level full" "$(ring_since "$M")"
dismiss_toast quiet_ref
clock_restore
adb emu gsm call 5551234 >/dev/null 2>&1; sleep 3
adb shell input keyevent KEYCODE_CALL; sleep 4
MODE="$(adb shell dumpsys audio | tr -d '\r' | grep -oE 'MODE_IN_CALL|MODE_IN_COMMUNICATION' | head -1)"
record "dumpsys audio mode during the call" "${MODE:-$(adb shell dumpsys audio | tr -d '\r' | grep -m1 -iE 'mode' | sed 's/^ *//')}"
assert_contains "the audio mode reads IN_CALL" "MODE_IN_CALL" "$MODE"
NOW="$(device_ms)"; read -r H Mi <<< "$(device_hm $(( NOW + 120000 )))"
ID2="$(api_alarm "$H" "$Mi" "Incall")"
T2="$(alarm_trigger_ms | head -1)"
M="$(ring_at "$ID2" "$T2")"
assert_ne "during the call an ALARM player of the shell exists" "" "$(alarm_players)"
assert_contains "the ring level line says 1/8 (in call)" "[alarms] ring level 1/8 (in call" "$(ring_since "$M")"
INCALL="$(track_line incall)"; note "in-call track: $(echo "$INCALL" | tr -s ' ' | cut -c1-200)"
CALLC="$(track_cols "$INCALL")"; record "in-call G / L / R / VS / PortVol (dB)" "$CALLC"
assert_eq "the track's gain is ≈ 1/8 of the out-of-call ring's (−18.1 dB ± 2 on one of G / L / R / VS / PortVol)" yes "$(python3 -c '
import sys
def f(v):
    try: return float(v)
    except ValueError: return float("-inf")
a = [f(x) for x in sys.argv[1].split()]; b = [f(x) for x in sys.argv[2].split()]
d = [y - x for x, y in zip(a, b)]
print("yes" if any(abs(v + 18.06) <= 2 for v in d) else "no (%s)" % d)' "$REFC" "$CALLC" 2>/dev/null || echo "no (unreadable)")"
assert_contains "the toast shows over the in-call UI (overlay)" "[alarms] surface: toast-overlay $ID2" "$(ring_since "$M")"
OVL="$(overlay_window)"; assert_contains "… an APPLICATION_OVERLAY window" "type=APPLICATION_OVERLAY" "$OVL"
record "the resumed activity under the toast" "$(resumed | sed 's/^ *//')"
assert_ne "vibration runs (a vibration of uid $SHELL_UID in vibrator_manager)" "" "$(adb shell dumpsys vibrator_manager | tr -d '\r' | grep -m1 "uid=$SHELL_UID")"
dismiss_toast incall
adb emu gsm cancel 5551234 >/dev/null 2>&1; sleep 2
# (no KEYCODE_ENDCALL: with no call left it is the sleep key — run 3's screen went dark here and stayed so)
assert_eq "after gsm cancel wake_device prints Awake (C-25)" "Awake" "$(wake_device)"
clock_restore
section_clear "in-call"

# ================================================================== reboot straddling an alarm; the Missed path =======================
NOW="$(device_ms)"; read -r H Mi <<< "$(device_hm $(( NOW + 150000 )))"
ID="$(api_alarm "$H" "$Mi" "Straddle")"
T="$(alarm_trigger_ms | head -1)"
adb shell input keyevent KEYCODE_HOME; sleep 1
# The AVD boots in ~20 s (run 2: rebooted at T − 30 s, up again 10 s BEFORE the alarm — the pass void), so the reboot
# starts 8 s before the alarm; a void pass is re-seeded and tried once more at T − 4 s.
straddle_once() { # lead_ms -> sets BOOT, T
  while [ "$(device_ms)" -lt $(( T - $1 )) ]; do sleep 1; done
  ring_save launcher; cp "$ROW_DIR/ring-launcher.txt" "$ROW_DIR/ring-launcher-prekill.txt"
  BOOT="$(reboot_and_wait)"
}
straddle_once 8000
if [ "$BOOT" -le "$T" ]; then
  record "straddle pass 1 void: the boot finished before the alarm ($BOOT vs $T); re-seeding" "re-run once"
  wake_device >/dev/null; dismiss_any_ring; section_clear "straddle void"
  NOW="$(device_ms)"; read -r H Mi <<< "$(device_hm $(( NOW + 150000 )))"
  ID="$(api_alarm "$H" "$Mi" "Straddle")"; T="$(alarm_trigger_ms | head -1)"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  straddle_once 4000
fi
assert_eq "the boot finished past the alarm's instant (precondition)" yes "$([ "$BOOT" -gt "$T" ] && echo yes || echo "no ($BOOT vs $T)")"
assert_eq "wake_device prints Awake (C-25)" "Awake" "$(wake_device)"
FIRED="$(wait_ring 0 "[alarms] fired $ID kind=alarm" 30)"
assert_ne "inside the timeout it rings on boot" "" "$FIRED"
sleep 2
assert_ne "… the player is started" 0 "$(alarm_player_started)"
dismiss_toast straddle
section_clear "section"
NOW="$(device_ms)"; read -r H Mi <<< "$(device_hm $(( NOW + 120000 )))"
ID="$(api_alarm "$H" "$Mi" "Missed")"
T="$(alarm_trigger_ms | head -1)"
adb shell input keyevent KEYCODE_HOME; sleep 1
kill9_shell >/dev/null
MARK="$(ring_mark)"
jump_clock $(( T + 15 * 60000 )) >/dev/null
L="$(wait_ring "$MARK" "passed while not running: missed" 20)"
assert_contains "past the timeout (15 min later at the re-arm) it is missed" "alarm $ID due at $T passed while not running: missed" "$L"
assert_contains "… with a Missed alarm notification" "Missed alarm $(time_12h "$H" "$Mi")" "$(shell_notifications)"
assert_eq "… and no ring" 0 "$(alarm_player_started)"
clock_restore
section_clear "section"

# ================================================================== an update with an alarm 1 min ahead =================================
NOW="$(device_ms)"; read -r H Mi <<< "$(device_hm $(( NOW + 75000 )))"
ID="$(api_alarm "$H" "$Mi" "Update")"
T="$(alarm_trigger_ms | head -1)"
adb shell input keyevent KEYCODE_HOME; sleep 1
MARK="$(ring_mark)"
adb install -r "$APK" >/dev/null 2>&1
assert_ne "MY_PACKAGE_REPLACED re-arms it: rearm (package replaced): 1 alarms" "" "$(wait_ring "$MARK" "[alarms] rearm (package replaced): 1 alarms" 30)"
assert_eq "… at the same instant" "$T" "$(alarm_trigger_ms | paste -sd,)"
FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 120)"
assert_ne "… and it rings at its time" "" "$FIRED"
assert_within "… on time (late <= 1000 ms)" 0 "$(field_of "$FIRED" late)" 1000
sleep 2
dismiss_toast update
section_clear "section"

# ================================================================== restore ================================================================
adb shell cmd notification cancel-all >/dev/null 2>&1
adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity >/dev/null 2>&1
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
assert_eq "restore: the zone is $TZ0" "$TZ0" "$(adb shell getprop persist.sys.timezone | tr -d '\r')"
assert_contains "restore: the overlay grant is allow" "allow" "$(adb shell appops get app.tileshell SYSTEM_ALERT_WINDOW | tr -d '\r' | head -1)"
assert_clock_empty "restore"
row_end
