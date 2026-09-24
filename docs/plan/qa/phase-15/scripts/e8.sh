#!/usr/bin/env bash
# E8 — World clock, offline and online (phase 15 T15-20, T15-29; phase 06 E18's netstats form).
# Offline pass (airplane mode on throughout; the shell uid's byte counters read after a forced poll, unchanged):
#   search "Tok" lists Tokyo (ICU's exemplar name); adding it shows `clock_time:Asia/Tokyo` equal to the host's
#   Tokyo time ± 1 min and `clock_diff:Asia/Tokyo` in W10M's form ("Today, N hours ahead" / the weekday's name),
#   computed on the host; `clock_local_row` ("Local time") sits above the city rows; `cmd alarm set-timezone
#   Europe/London` → the local row and the difference line change; an alarm set at 07:00 before the change still
#   reads 07:00 while its dumpsys alarm instant moved to London's 07:00, and a running timer's instant did not move.
# Online pass: airplane mode off and an active default network (asserted first); POSITIVE CONTROL: the counters
#   before / after a Weather refresh MOVE — the refresh is the one WeatherFeed runs at every process start
#   (force-stop + Home; the tile's own tap only refreshes when its 30-min interval is due, WeatherFeed.kt:99-115 —
#   reported); then search "Par", add Paris, remove it, all inside 20 s → the counters are unchanged; a `[weather]`
#   line in that window voids the pass (re-run once, the void recorded).
# Restore: the zone, airplane mode, the alarm and timer deleted through the app, Tokyo removed.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin E8 "world clock: offline (Tokyo, zone change, no bytes) and online (positive control, no bytes)"
assert_clock_empty "baseline"
TZ0="$(adb shell getprop persist.sys.timezone | tr -d '\r')"; note "zone at start: $TZ0"
assert_eq "precondition: the device's zone is America/Boise (the host expectations below use it)" "America/Boise" "$TZ0"

# The alarm at 07:00 and the running timer the zone-change clause reads.
AID="$(api_alarm 7 0 "Seven")"; ADATE="$(alarm_field "$AID" date)"
AT0="$(alarm_trigger_ms | head -1)"
TID="$(api_timer 1800 "Half")"
TT0="$(timer_trigger_elapsed | head -1)"
assert_ne "an alarm at 07:00 is armed" "" "$AT0"
assert_ne "a running timer is armed" "" "$TT0"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- offline pass ---------------------------------------------------------------------------------------------------------
adb shell cmd connectivity airplane-mode enable; sleep 3
assert_eq "airplane mode is on" 1 "$(adb shell settings get global airplane_mode_on | tr -d '\r')"
B0="$(shell_bytes)"; note "shell bytes at the start of the offline pass: $B0"
open_clock world_clock
dump_ui "$ROW_DIR/world_empty.xml"
assert_eq "the World Clock tab shows the Local time row" "Local time" "$(node_text "$ROW_DIR/world_empty.xml" clock_local_label)"
tap_node "$ROW_DIR/world_empty.xml" "clock_bar:add"; sleep 1.5
adb shell input text Tok; sleep 1.5
dump_ui "$ROW_DIR/search_tok.xml"; screencap "$ROW_DIR/search_tok.png"
assert_eq "search Tok lists Tokyo (clock_search_result:Asia/Tokyo)" "Tokyo" "$(node_text "$ROW_DIR/search_tok.xml" 'clock_search_result:Asia/Tokyo')"
tap_node "$ROW_DIR/search_tok.xml" 'clock_search_result:Asia/Tokyo'; sleep 1.5
dump_ui "$ROW_DIR/tokyo.xml"; screencap "$ROW_DIR/tokyo.png"
TT="$(node_text "$ROW_DIR/tokyo.xml" 'clock_time:Asia/Tokyo')"
assert_contains "clock_time:Asia/Tokyo equals the host's Tokyo time ± 1 min" "|$TT|" "|$(world_expect time Asia/Tokyo -1)|$(world_expect time Asia/Tokyo 0)|$(world_expect time Asia/Tokyo 1)|"
assert_eq "clock_diff:Asia/Tokyo is W10M's line, host-computed against $TZ0" "$(world_expect diff Asia/Tokyo "$TZ0")" "$(node_text "$ROW_DIR/tokyo.xml" 'clock_diff:Asia/Tokyo')"
LT="$(bounds "$ROW_DIR/tokyo.xml" clock_local_row | cut -d' ' -f2)"; RT="$(bounds "$ROW_DIR/tokyo.xml" 'clock_row:Asia/Tokyo' | cut -d' ' -f2)"
assert_eq "the Local time row sits above the city row" yes "$([ -n "$LT" ] && [ -n "$RT" ] && [ "$LT" -lt "$RT" ] && echo yes || echo "no ($LT vs $RT)")"
LOCAL0="$(node_text "$ROW_DIR/tokyo.xml" clock_local_time)"

# The zone change: London.
MARK="$(ring_mark)"
adb shell cmd alarm set-timezone Europe/London; sleep 4
assert_eq "the zone is Europe/London" "Europe/London" "$(adb shell getprop persist.sys.timezone | tr -d '\r')"
assert_contains "the ring re-armed on the zone change" "[alarms] rearm (time zone changed)" "$(ring_since "$MARK")"
dump_ui "$ROW_DIR/london.xml"; screencap "$ROW_DIR/london.png"
LT1="$(node_text "$ROW_DIR/london.xml" clock_local_time)"
assert_contains "the local row now shows London's time ± 1 min" "|$LT1|" "|$(world_expect time Europe/London -1)|$(world_expect time Europe/London 0)|$(world_expect time Europe/London 1)|"
assert_ne "… and it changed from before" "$LOCAL0" "$LT1"
assert_eq "Tokyo's difference line changed to the London form" "$(world_expect diff Asia/Tokyo Europe/London)" "$(node_text "$ROW_DIR/london.xml" 'clock_diff:Asia/Tokyo')"
open_clock alarm
dump_ui "$ROW_DIR/alarm_london.xml"
assert_eq "the 07:00 alarm still reads 7:00 AM after the zone change" "7:00 AM" "$(node_text "$ROW_DIR/alarm_london.xml" "alarm_time:$AID")"
EXP_LONDON="$(python3 -c '
import sys, datetime, zoneinfo
d = datetime.date.fromisoformat(sys.argv[1]); z = zoneinfo.ZoneInfo("Europe/London")
print(int(datetime.datetime(d.year, d.month, d.day, 7, 0, tzinfo=z).timestamp() * 1000))' "$ADATE")"
AT1="$(alarm_trigger_ms | head -1)"
assert_eq "… while its dumpsys alarm instant moved to London's 07:00 on its date" "$EXP_LONDON" "$AT1"
record "the alarm's instant moved by (ms)" "$(( AT0 - AT1 ))"
assert_eq "the running timer's instant did not move" "$TT0" "$(timer_trigger_elapsed | head -1)"
B1="$(shell_bytes)"
assert_eq "offline: the shell uid's byte counters are unchanged" "$B0" "$B1"
adb shell cmd alarm set-timezone "$TZ0"; sleep 2
assert_eq "the zone is restored" "$TZ0" "$(adb shell getprop persist.sys.timezone | tr -d '\r')"

# ---- online pass ----------------------------------------------------------------------------------------------------------
adb shell cmd connectivity airplane-mode disable; sleep 6
assert_eq "airplane mode is off" 0 "$(adb shell settings get global airplane_mode_on | tr -d '\r')"
ACTIVE="$(adb shell dumpsys connectivity | tr -d '\r' | grep -m1 -E 'Active default network: [0-9]+')"
assert_ne "dumpsys connectivity shows an active default network" "" "$ACTIVE"
adb shell input keyevent KEYCODE_HOME; sleep 1
C0="$(shell_bytes)"
MARK="$(ring_mark)"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 2
W="$(wait_ring "$MARK" "[weather] fetch" 40)"; note "weather: $(printf '%s\n' "$W" | sed 's/.*\] //' | cut -c1-80 | paste -sd'|')"
assert_contains "positive control: the process start ran a Weather fetch" "[weather] fetch" "$W"
sleep 2
C1="$(shell_bytes)"
assert_ne "positive control: the counters MOVED across the Weather fetch ($C0 -> $C1)" "$C0" "$C1"

# The positive control's connection lingers (keep-alive, then FIN packets on the shell's uid — run 1 read +104 / +80
# bytes with no [weather] line), so the window opens only once two counter reads 10 s apart are equal (up to 2 min).
settle_bytes() {
  local a b i
  a="$(shell_bytes)"
  for i in $(seq 1 12); do sleep 10; b="$(shell_bytes)"; [ "$a" = "$b" ] && { note "counters settled at $b after $((i * 10)) s"; return 0; }; a="$b"; done
  note "counters never settled: $a"; return 1
}
online_pass() { # -> 0 when the window was clean
  local d0 d1 mark w
  settle_bytes
  d0="$(shell_bytes)"
  mark="$(ring_mark)"
  local t0; t0="$(date +%s)"
  open_clock world_clock
  dump_ui "$ROW_DIR/online_world.xml"
  tap_node "$ROW_DIR/online_world.xml" "clock_bar:add"; sleep 1
  adb shell input text Par; sleep 1
  dump_ui "$ROW_DIR/search_par.xml"
  assert_eq "online: search Par lists Paris" "Paris" "$(node_text "$ROW_DIR/search_par.xml" 'clock_search_result:Europe/Paris')"
  tap_node "$ROW_DIR/search_par.xml" 'clock_search_result:Europe/Paris'; sleep 1
  dump_ui "$ROW_DIR/paris.xml"
  assert_eq "online: Paris was added" yes "$(has_node "$ROW_DIR/paris.xml" 'clock_row:Europe/Paris')"
  hold_node "$ROW_DIR/paris.xml" 'clock_row:Europe/Paris'; sleep 1
  dump_ui "$ROW_DIR/paris_menu.xml"
  assert_eq "online: the hold menu offers Remove" yes "$(has_node "$ROW_DIR/paris_menu.xml" 'clock_remove:Europe/Paris')"
  tap_node "$ROW_DIR/paris_menu.xml" 'clock_remove:Europe/Paris'; sleep 1
  dump_ui "$ROW_DIR/paris_removed.xml"
  assert_eq "online: Paris was removed" no "$(has_node "$ROW_DIR/paris_removed.xml" 'clock_row:Europe/Paris')"
  local took=$(( $(date +%s) - t0 ))
  assert_eq "online: search, add and remove took <= 20 s ($took s)" yes "$([ "$took" -le 20 ] && echo yes || echo no)"
  d1="$(shell_bytes)"
  w="$(ring_since "$mark" | grep -F '[weather]')"
  if [ -n "$w" ]; then record "online pass VOID: a [weather] line fell in the window" "$(printf '%s\n' "$w" | head -1 | sed 's/.*\] //')"; return 1; fi
  assert_eq "online: the shell uid's byte counters are unchanged across the world-clock steps ($d0 -> $d1)" "$d0" "$d1"
}
online_pass || { note "re-running the online pass once (the first was void)"; online_pass || record "online pass" "void twice"; }

# ---- restore ----------------------------------------------------------------------------------------------------------------
open_clock world_clock
dump_ui "$ROW_DIR/restore_world.xml"
hold_node "$ROW_DIR/restore_world.xml" 'clock_row:Asia/Tokyo'; sleep 1
dump_ui "$ROW_DIR/restore_menu.xml"
tap_node "$ROW_DIR/restore_menu.xml" 'clock_remove:Asia/Tokyo'; sleep 1
dump_ui "$ROW_DIR/restore_done.xml"
assert_eq "restore: Tokyo removed" no "$(has_node "$ROW_DIR/restore_done.xml" 'clock_row:Asia/Tokyo')"
app_delete_alarm "$AID"; app_delete_timer "$TID"
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_clock_empty "restore"
row_end
