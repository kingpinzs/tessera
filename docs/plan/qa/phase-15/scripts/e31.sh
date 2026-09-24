#!/usr/bin/env bash
# E31 — Clock extras (phase 15 T15-16; H22–H24).
# Compare: World Clock with Tokyo, `clock_compare` → `clock_compare_strip` 48 epx tall in accent where the app bar
#   was, the app bar gone; one step right → every `clock_time:<zone>` one hour later (host TZ=<zone> date +1 h ± 1
#   min); Back → the app bar returns.
# Expanded: a running timer's `timer_expand:<id>` → `timer_expanded:<id>` fills the screen in accent with no tab band
#   or app bar and its `[timer]` lines keep falling; collapse returns; the same for `stopwatch_expand`.
# Pin: `timer_pin:<id>` → Start's pin band shows the request; accept → start_layout.json holds
#   `secondary:app.tileshell:timer.<id>`; its tile shows the timer's name and remaining time (recorded, U10); a tap
#   resumes the Alarms & Clock component itself on the Timer tab with that timer on screen; `stopwatch_pin` the same
#   with `secondary:app.tileshell:stopwatch`; unpin both (Start's edit mode, the unpin disc).
# Share: three laps, `stopwatch_share` → the resolver for ACTION_SEND text/plain; Fossify Messages at its dump bounds
#   → its compose body holds the three lap lines exactly as `stopwatch_lap:<n>` shows them.
# Pick from my music: after push_fixture_recordings, an alarm's `alarm_sound:music` → `alarm_sound_pick:<song id>` →
#   the Sound value names it; fired over E4b's in-use route → `[alarms] ring <id> sound=content://media/…<id>` and an
#   ALARM player; rm the file + rescan → fired again → `[alarms] sound <uri> missing -> default` and the default plays.
# Restore: timers deleted, laps reset, the alarm deleted, Tokyo removed, remove_fixture_recordings, RV12's clock
#   restore, force-stop + Home.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

DESK=com.android.deskclock
row_begin E31 "clock extras: compare strip, expanded views, pinned tiles, share laps, pick from my music"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring
layout_json() { adb shell "run-as app.tileshell cat files/start_layout.json" < /dev/null | tr -d '\r'; }
mean_rgb() { python3 "$HERE/pixcmp.py" mean "$@"; }
rgb_close() { python3 -c '
import sys
a = [int(x) for x in sys.argv[1].split(",")]; b = [int(x) for x in sys.argv[2].split(",")]; t = int(sys.argv[3])
print("yes" if all(abs(x - y) <= t for x, y in zip(a, b)) else "no (%s vs %s)" % (sys.argv[1], sys.argv[2]))' "$1" "$2" "$3"; }
selected_of() { grep -o "<node[^>]*resource-id=\"$2\"[^>]*>" "$1" | grep -o 'selected="[a-z]*"' | cut -d'"' -f2; }

# ---- compare ------------------------------------------------------------------------------------------------------------
open_clock world_clock
dump_ui "$ROW_DIR/world0.xml"
tap_node "$ROW_DIR/world0.xml" "clock_bar:add"; sleep 1
adb shell input text Tok; sleep 1.2
dump_ui "$ROW_DIR/world_search.xml"
tap_node "$ROW_DIR/world_search.xml" 'clock_search_result:Asia/Tokyo'; sleep 1.5
dump_ui "$ROW_DIR/world_tokyo.xml"; screencap "$ROW_DIR/world_tokyo.png"
assert_eq "Tokyo is listed" yes "$(has_node "$ROW_DIR/world_tokyo.xml" 'clock_row:Asia/Tokyo')"
UB="$(bounds "$ROW_DIR/world_tokyo.xml" clock_tab_underline)"
# shellcheck disable=SC2086
set -- $UB; ACCENT="$(mean_rgb "$ROW_DIR/world_tokyo.png" $(( $1 + 4 )) $(( $2 + 2 )) $(( $3 - 4 )) $(( $4 - 2 )))"; note "accent (from the tab underline): $ACCENT"
APPBAR="$(bounds "$ROW_DIR/world_tokyo.xml" clock_app_bar)"
tap_node "$ROW_DIR/world_tokyo.xml" clock_compare; sleep 1.5
dump_ui "$ROW_DIR/compare.xml"; screencap "$ROW_DIR/compare.png"
SB="$(bounds "$ROW_DIR/compare.xml" clock_compare_strip)"; note "compare strip: $SB (the app bar was $APPBAR)"
assert_ne "compare: clock_compare_strip is up" "" "$SB"
assert_eq "compare: the app bar is gone" no "$(has_node "$ROW_DIR/compare.xml" clock_app_bar)"
assert_within "compare: the strip is 48 epx tall (± 1 epx)" $(( 48 * PX )) "$(( $(echo "$SB" | cut -d' ' -f4) - $(echo "$SB" | cut -d' ' -f2) ))" $PX
assert_eq "compare: the strip sits where the app bar was (same bottom edge)" "$(echo "$APPBAR" | cut -d' ' -f4)" "$(echo "$SB" | cut -d' ' -f4)"
# shellcheck disable=SC2086
set -- $SB
assert_eq "compare: the strip is filled with the accent (± 8 levels, sampled between the hour labels)" yes "$(rgb_close "$(mean_rgb "$ROW_DIR/compare.png" $(( $1 + 60 )) $(( $2 + 8 )) $(( $1 + 130 )) $(( $2 + 20 )))" "$ACCENT" 8)"
T0="$(node_text "$ROW_DIR/compare.xml" 'clock_time:Asia/Tokyo')"; L0="$(node_text "$ROW_DIR/compare.xml" clock_local_time)"
tap_node "$ROW_DIR/compare.xml" clock_compare_next; sleep 1.2
dump_ui "$ROW_DIR/compare_next.xml"; screencap "$ROW_DIR/compare_next.png"
T1="$(node_text "$ROW_DIR/compare_next.xml" 'clock_time:Asia/Tokyo')"; L1="$(node_text "$ROW_DIR/compare_next.xml" clock_local_time)"
assert_contains "one step right: Tokyo's time is one hour later than the host's Tokyo time ± 1 min" "|$T1|" "|$(world_expect time Asia/Tokyo 59)|$(world_expect time Asia/Tokyo 60)|$(world_expect time Asia/Tokyo 61)|"
assert_contains "one step right: the local row is one hour later too" "|$L1|" "|$(world_expect time America/Boise 59)|$(world_expect time America/Boise 60)|$(world_expect time America/Boise 61)|"
assert_ne "… and both moved from the compare start" "$T0|$L0" "$T1|$L1"
adb shell input keyevent KEYCODE_BACK; sleep 1.2
dump_ui "$ROW_DIR/compare_back.xml"
assert_eq "Back: the app bar returns" yes "$(has_node "$ROW_DIR/compare_back.xml" clock_app_bar)"
assert_eq "Back: the strip is gone" no "$(has_node "$ROW_DIR/compare_back.xml" clock_compare_strip)"

# ---- expanded timer ---------------------------------------------------------------------------------------------------------
TID="$(api_timer 600 "Ten")"
assert_ne "a running timer" "" "$TID"
open_clock timer
gdump "$ROW_DIR/timer_tab.xml"
gtap "$ROW_DIR/timer_tab.xml" "timer_expand:$TID"; sleep 1.5
MARK="$(ring_mark)"
gdump "$ROW_DIR/timer_expanded.xml"; screencap "$ROW_DIR/timer_expanded.png"
EB="$(bounds "$ROW_DIR/timer_expanded.xml" "timer_expanded:$TID")"; note "timer_expanded: $EB"
assert_ne "expand: timer_expanded:$TID is up" "" "$EB"
assert_eq "expand: no tab band" no "$(has_node "$ROW_DIR/timer_expanded.xml" clock_tabs)"
assert_eq "expand: no app bar" no "$(has_node "$ROW_DIR/timer_expanded.xml" clock_app_bar)"
assert_eq "expand: it fills the width" "0 1080" "$(echo "$EB" | awk '{print $1, $3}')"
assert_eq "expand: it spans from the status bar's bottom to the nav bar's top" "$(( 28 * PX )) $(( 2340 - 48 * PX ))" "$(echo "$EB" | awk '{print $2, $4}')"
assert_eq "expand: the page is filled with the accent (± 8, sampled at x 60–200, y 500–600)" yes "$(rgb_close "$(mean_rgb "$ROW_DIR/timer_expanded.png" 60 500 200 600)" "$ACCENT" 8)"
sleep 11
TL="$(timer_lines "$(ring_since "$MARK")" "$TID" 2)"; note "expanded [timer] lines: $(printf '%s\n' "$TL" | sed 's/.*\] //' | paste -sd'|')"
assert_eq "expand: two [timer] lines fell while expanded, remaining decreasing" yes "$(printf '%s\n' "$TL" | python3 -c '
import re, sys
ls = [l for l in sys.stdin.read().splitlines() if "remaining=" in l]
if len(ls) < 2: print("no (%d lines)" % len(ls)); sys.exit()
r = [int(re.search(r"remaining=(\d+)", l).group(1)) for l in ls]
print("yes" if r[1] < r[0] else "no (%s)" % r)')"
gtap "$ROW_DIR/timer_expanded.xml" timer_collapse; sleep 1.5
gdump "$ROW_DIR/timer_collapsed.xml"
assert_eq "collapse: the tab band is back" yes "$(has_node "$ROW_DIR/timer_collapsed.xml" clock_tabs)"
assert_eq "collapse: timer_expanded gone" no "$(has_node "$ROW_DIR/timer_collapsed.xml" "timer_expanded:$TID")"

# ---- expanded stopwatch (started here; its laps feed Share) -----------------------------------------------------------------------
open_clock stopwatch
gdump "$ROW_DIR/sw0.xml"
gtap "$ROW_DIR/sw0.xml" stopwatch_play; sleep 1
gdump "$ROW_DIR/sw_running.xml"
gtap "$ROW_DIR/sw_running.xml" stopwatch_expand; sleep 1.5
gdump "$ROW_DIR/sw_expanded.xml"; screencap "$ROW_DIR/sw_expanded.png"
assert_eq "stopwatch expand: stopwatch_expanded is up" yes "$(has_node "$ROW_DIR/sw_expanded.xml" stopwatch_expanded)"
assert_eq "stopwatch expand: no tab band" no "$(has_node "$ROW_DIR/sw_expanded.xml" clock_tabs)"
assert_eq "stopwatch expand: no app bar" no "$(has_node "$ROW_DIR/sw_expanded.xml" clock_app_bar)"
assert_eq "stopwatch expand: the page is filled with the accent (± 8)" yes "$(rgb_close "$(mean_rgb "$ROW_DIR/sw_expanded.png" 60 500 200 600)" "$ACCENT" 8)"
gtap "$ROW_DIR/sw_expanded.xml" stopwatch_collapse; sleep 1.5
gdump "$ROW_DIR/sw_collapsed.xml"
assert_eq "stopwatch collapse: the tab band is back" yes "$(has_node "$ROW_DIR/sw_collapsed.xml" clock_tabs)"

# ---- pin the timer and the stopwatch ------------------------------------------------------------------------------------------------
pin_check() { # label tag-on-clock-tab page tile-key expected-pivot expected-node
  local label="$1" tag="$2" page="$3" key="$4" pivot="$5" node="$6" mark d
  open_clock "$page"
  gdump "$ROW_DIR/${label}_tab.xml"
  mark="$(ring_mark)"
  gtap "$ROW_DIR/${label}_tab.xml" "$tag"; sleep 1.5
  assert_contains "$label: the pin request is queued for Start" "-> queued" "$(ring_since "$mark" | grep -F "[clock] pin")"
  adb shell input keyevent KEYCODE_HOME; sleep 2.5
  d="$ROW_DIR/${label}_prompt.xml"; dump_ui "$d"; screencap "$ROW_DIR/${label}_prompt.png"
  assert_eq "$label: Start's pin band shows the request" yes "$(has_node "$d" secondary_pin_prompt)"
  record "$label: the prompt names" "$(node_text "$d" secondary_pin_app) / $(node_text "$d" secondary_pin_name)"
  tap_node "$d" secondary_pin_accept; sleep 2
  assert_contains "$label: start_layout.json holds $key" "$key" "$(layout_json)"
  d="$ROW_DIR/${label}_pinned.xml"; dump_ui "$d"; screencap "$ROW_DIR/${label}_pinned.png"
  scroll_to_node "$d" "tile:$key" 6
  assert_eq "$label: the tile is on Start (tile:$key)" yes "$(has_node "$d" "tile:$key")"
  record "$label: the tile face's texts (U10's approximation; screencap ${label}_pinned.png)" "$(node_child_text "$d" "tile:$key") | $(grep -o "<node[^>]*resource-id=\"tile:$key\"[^>]*>" "$d" | grep -o ' text="[^"]*"' | head -1)"
  tap_node "$d" "tile:$key"; sleep 2.5
  assert_contains "$label: a tap resumes the Alarms & Clock component itself" "app.tileshell/.clock.ClockActivity" "$(resumed)"
  gdump "$ROW_DIR/${label}_opened.xml"
  assert_eq "$label: … on clock_pivot:$pivot" "true" "$(selected_of "$ROW_DIR/${label}_opened.xml" "clock_pivot:$pivot")"
  assert_eq "$label: … with $node on screen" yes "$(has_node "$ROW_DIR/${label}_opened.xml" "$node")"
}
pin_check "pin_timer" "timer_pin:$TID" timer "secondary:app.tileshell:timer.$TID" timer "timer_block:$TID"
pin_check "pin_stopwatch" stopwatch_pin stopwatch "secondary:app.tileshell:stopwatch" stopwatch stopwatch_elapsed
unpin() { # key
  local d="$ROW_DIR/unpin_$(printf '%s' "$1" | tr -c 'A-Za-z0-9' '_').xml"
  adb shell input keyevent KEYCODE_HOME; sleep 2
  dump_ui "$d"; scroll_to_node "$d" "tile:$1" 6 || return 1
  hold_node "$d" "tile:$1" 1000; sleep 1.5
  dump_ui "$d"
  [ "$(has_node "$d" 'edit_disc:unpin')" = yes ] || { note "unpin $1: no unpin disc"; adb shell input keyevent KEYCODE_BACK; return 1; }
  tap_node "$d" 'edit_disc:unpin'; sleep 1.5
  adb shell input keyevent KEYCODE_BACK; sleep 1
}
unpin "secondary:app.tileshell:timer.$TID"
unpin "secondary:app.tileshell:stopwatch"
assert_absent "unpin: the timer tile is out of start_layout.json" "secondary:app.tileshell:timer.$TID" "$(layout_json)"
assert_absent "unpin: the stopwatch tile is out of start_layout.json" "secondary:app.tileshell:stopwatch" "$(layout_json)"

# ---- share three laps --------------------------------------------------------------------------------------------------------------
open_clock stopwatch
gdump "$ROW_DIR/share0.xml"
CRASHES0="$(shell_crash_count)"
gtap "$ROW_DIR/share0.xml" stopwatch_lap; sleep 1.5
if [ "$(shell_crash_count)" != "$CRASHES0" ]; then
  # PRODUCT DEFECT (E7/DEFECT.md): the first lap crashes the app; the Share sub-row stops here and the store is
  # cleared as the baseline restore so the music sub-row below can run.
  close_crash_dialog
  assert_eq "Share: the first lap did not crash the app (E7/DEFECT.md: $(shell_last_crash))" "$CRASHES0" "$(shell_crash_count)"
  log "      SUB-ROW STOPPED (Share, three laps) at the lap-row crash; see E7/DEFECT.md"
  stopwatch_store_reset
  SHARE_STOPPED=1
else
for _ in 1 2; do gtap "$ROW_DIR/share0.xml" stopwatch_lap; sleep 1.2; done
gdump "$ROW_DIR/share_laps.xml"; screencap "$ROW_DIR/share_laps.png"
LAPS="$(for n in 1 2 3; do node_text "$ROW_DIR/share_laps.xml" "stopwatch_lap:$n"; done)"
note "laps: $(printf '%s\n' "$LAPS" | paste -sd'|')"
assert_eq "three laps are listed" 3 "$(printf '%s\n' "$LAPS" | grep -c .)"
gtap "$ROW_DIR/share_laps.xml" stopwatch_share; sleep 2.5
RES="$(adb shell dumpsys activity activities | tr -d '\r' | grep -m1 -E 'topResumedActivity|ResumedActivity')"; note "resumed: $RES"
assert_contains "Share opens the system resolver (intent resolver / chooser)" "resolver" "$(printf '%s' "$RES" | tr 'A-Z' 'a-z')"
record "the chooser's intent (dumpsys activity activities)" "$(adb shell dumpsys activity activities | tr -d '\r' | grep -m1 -oE 'act=android.intent.action.(SEND|CHOOSER)[^}]{0,120}')"
dump_ui "$ROW_DIR/chooser.xml"; screencap "$ROW_DIR/chooser.png"
CH_PKG="$(grep -o 'package="com.android.intentresolver"' "$ROW_DIR/chooser.xml" | head -1 | cut -d'"' -f2)"; CH_PKG="${CH_PKG:-android}"
MB="$(bounds_by_text "$ROW_DIR/chooser.xml" "$CH_PKG" Messages)"; note "Messages in the chooser: [$MB] (package $CH_PKG)"
assert_ne "the chooser lists Messages (the Fossify fixture)" "" "$MB"
tap_by_text "$ROW_DIR/chooser.xml" "$CH_PKG" Messages; sleep 3
dump_ui "$ROW_DIR/messages.xml"; screencap "$ROW_DIR/messages.png"
if ! grep -q 'package="org.fossify.messages"' "$ROW_DIR/messages.xml"; then note "Messages did not come up on the first tap; dumping again"; sleep 2; dump_ui "$ROW_DIR/messages.xml"; fi
assert_contains "Fossify Messages is in front" "org.fossify.messages" "$(resumed)"
LAP1="$(printf '%s\n' "$LAPS" | head -1)"
BODY="$(python3 - "$ROW_DIR/messages.xml" "$LAP1" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for n in re.finditer(r'<node[^>]*>', xml):
    s = n.group(0)
    if 'package="org.fossify.messages"' not in s: continue
    t = re.search(r' text="([^"]*)"', s)
    if t and sys.argv[2] in html.unescape(t.group(1)): print(html.unescape(t.group(1))); break
PY
)"
if [ -z "$BODY" ] && [ -n "$(bounds_by_text "$ROW_DIR/messages.xml" org.fossify.messages Mom)" ]; then
  note "Messages opened its recipient picker first (a share with no thread); choosing Mom"
  tap_by_text "$ROW_DIR/messages.xml" org.fossify.messages Mom; sleep 2.5
  dump_ui "$ROW_DIR/messages_thread.xml"; screencap "$ROW_DIR/messages_thread.png"
  BODY="$(python3 - "$ROW_DIR/messages_thread.xml" "$LAP1" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for n in re.finditer(r'<node[^>]*>', xml):
    s = n.group(0)
    if 'package="org.fossify.messages"' not in s: continue
    t = re.search(r' text="([^"]*)"', s)
    if t and sys.argv[2] in html.unescape(t.group(1)): print(html.unescape(t.group(1))); break
PY
)"
fi
note "compose body: $(printf '%s' "$BODY" | tr '\n' '|')"
[ -n "$LAPS" ] || LAPS="(no laps were listed)"   # so an empty body can never equal an empty expectation
assert_eq "the compose body holds the three lap lines exactly as stopwatch_lap:1..3 show them" "$LAPS" "$BODY"
adb shell am force-stop org.fossify.messages; adb shell input keyevent KEYCODE_HOME; sleep 1
fi

# ---- pick from my music -----------------------------------------------------------------------------------------------------------
push_fixture_recordings
SONG_ID="$(adb shell content query --uri content://media/external/audio/media --projection _id:title --where "\"title='Fixture song'\"" 2>/dev/null | tr -d '\r' | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)"
note "song.m4a MediaStore id: $SONG_ID"
assert_ne "the fixture song is in MediaStore" "" "$SONG_ID"
adb shell pm grant $DESK android.permission.POST_NOTIFICATIONS 2>/dev/null
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + 180000 )))"
AID="$(api_alarm "$AH" "$AM_" "Song")"
assert_ne "the alarm was created" "" "$AID"
open_clock alarm
dump_ui "$ROW_DIR/music_list.xml"
tap_node "$ROW_DIR/music_list.xml" "alarm_row:$AID"; sleep 1.5
dump_ui "$ROW_DIR/music_editor.xml"
tap_node "$ROW_DIR/music_editor.xml" 'alarm_editor_field:sound'; sleep 1.2
dump_ui "$ROW_DIR/music_flyout.xml"; screencap "$ROW_DIR/music_flyout.png"
assert_eq "the Sound flyout offers Pick from my music" yes "$(has_node "$ROW_DIR/music_flyout.xml" 'alarm_sound:music')"
tap_node "$ROW_DIR/music_flyout.xml" 'alarm_sound:music'; sleep 2
dump_ui "$ROW_DIR/music_picker.xml"; screencap "$ROW_DIR/music_picker.png"
if [ "$(has_node "$ROW_DIR/music_picker.xml" alarm_sound_grant)" = yes ]; then
  note "the picker asks for READ_MEDIA_AUDIO; granting through its own button"
  tap_node "$ROW_DIR/music_picker.xml" alarm_sound_grant; sleep 2
  dump_ui "$ROW_DIR/music_perm.xml"
  tap_node "$ROW_DIR/music_perm.xml" com.android.permissioncontroller:id/permission_allow_button 2>/dev/null; sleep 2
  dump_ui "$ROW_DIR/music_picker.xml"
fi
assert_eq "the picker lists the song (alarm_sound_pick:$SONG_ID)" yes "$(has_node "$ROW_DIR/music_picker.xml" "alarm_sound_pick:$SONG_ID")"
tap_node "$ROW_DIR/music_picker.xml" "alarm_sound_pick:$SONG_ID"; sleep 1.5
dump_ui "$ROW_DIR/music_chosen.xml"; screencap "$ROW_DIR/music_chosen.png"
assert_eq "the Sound value names the song" "Fixture song" "$(node_text "$ROW_DIR/music_chosen.xml" 'alarm_editor_field:sound')"
tap_node "$ROW_DIR/music_chosen.xml" "clock_bar:save"; sleep 1.5
SONG_URI="content://media/external/audio/media/$SONG_ID"
assert_eq "the store holds the song's URI" "$SONG_URI" "$(store_alarms | python3 -c 'import json,sys; a=[x for x in json.load(sys.stdin) if x["id"]==sys.argv[1]]; print(a[0]["sound"]["uri"] if a else "")' "$AID")"
adb shell am start -W -n $DESK/.DeskClock >/dev/null 2>&1; sleep 2
AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"; [ "$AT" -gt 0 ] || AT="$(device_ms)"
jump_clock $(( AT - 10000 )) >/dev/null
MARK="$(ring_mark)"
assert_ne "song: the alarm fired" "" "$(wait_ring "$MARK" "[alarms] fired $AID kind=alarm" 30)"
sleep 3
assert_contains "song: ring $AID sound=$SONG_URI" "[alarms] ring $AID sound=$SONG_URI" "$(ring_since "$MARK")"
assert_ne "song: an ALARM player is started" 0 "$(alarm_player_started)"
gdump "$ROW_DIR/song_ring.xml"; gtap "$ROW_DIR/song_ring.xml" ring_dismiss; sleep 2
# The file disappears: the default sound rings and the ring says so.
adb shell rm -f /sdcard/Music/song.m4a
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1; sleep 2
assert_eq "after rm + rescan the song is gone from MediaStore" "" "$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"_id=$SONG_ID\"" 2>/dev/null | tr -d '\r' | grep -oE '_id=[0-9]+')"
open_clock alarm
dump_ui "$ROW_DIR/song_relist.xml"
tap_node "$ROW_DIR/song_relist.xml" "alarm_toggle:$AID"; sleep 1.5   # the dismissed one-shot is off; on again arms it for tomorrow
AT2="$(alarm_trigger_ms | head -1)"; AT2="${AT2:-0}"
assert_ne "the alarm is armed again" 0 "$AT2"
adb shell am start -W -n $DESK/.DeskClock >/dev/null 2>&1; sleep 1
[ "$AT2" -gt 0 ] || AT2="$(device_ms)"
jump_clock $(( AT2 - 10000 )) >/dev/null
MARK="$(ring_mark)"
assert_ne "missing: the alarm fired" "" "$(wait_ring "$MARK" "[alarms] fired $AID kind=alarm" 30)"
sleep 3
assert_contains "missing: sound $SONG_URI missing -> default" "[alarms] sound $SONG_URI missing -> default" "$(ring_since "$MARK")"
assert_ne "missing: the default sound plays (an ALARM player is started)" 0 "$(alarm_player_started)"
gdump "$ROW_DIR/missing_ring.xml"; gtap "$ROW_DIR/missing_ring.xml" ring_dismiss; sleep 2

# ---- restore -----------------------------------------------------------------------------------------------------------------------
ring_save launcher
clock_restore
if [ "${SHARE_STOPPED:-0}" = 1 ]; then
  stopwatch_store_reset
else
  open_clock stopwatch
  gdump "$ROW_DIR/restore_sw.xml"
  [ "$(has_node "$ROW_DIR/restore_sw.xml" stopwatch_lap)" = yes ] && { gtap "$ROW_DIR/restore_sw.xml" stopwatch_play; sleep 1; gdump "$ROW_DIR/restore_sw.xml"; }
  [ "$(has_node "$ROW_DIR/restore_sw.xml" stopwatch_reset)" = yes ] && { gtap "$ROW_DIR/restore_sw.xml" stopwatch_reset; sleep 1; }
fi
open_clock world_clock
dump_ui "$ROW_DIR/restore_world.xml"
hold_node "$ROW_DIR/restore_world.xml" 'clock_row:Asia/Tokyo' 1000; sleep 1
dump_ui "$ROW_DIR/restore_world_menu.xml"
tap_node "$ROW_DIR/restore_world_menu.xml" 'clock_remove:Asia/Tokyo'; sleep 1
app_delete_alarm "$AID"; app_delete_timer "$TID"
remove_fixture_recordings
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am force-stop $DESK; adb shell pm revoke $DESK android.permission.POST_NOTIFICATIONS 2>/dev/null
# A missing stopwatch.json IS the reset state (ClockStore.loadStopwatch -> Stopwatch.RESET): after the baseline
# restore around the lap-row crash the file is gone (run 2 read "" and failed on it).
assert_eq "restore: the stopwatch is reset (not running, no laps; or no file)" "false []" "$(store_stopwatch | python3 -c 'import json,sys
t = sys.stdin.read().strip()
if not t: print("false []"); sys.exit()
s = json.loads(t); print(str(s["running"]).lower(), s["laps"])' 2>/dev/null)"
assert_clock_empty "restore"
row_end
