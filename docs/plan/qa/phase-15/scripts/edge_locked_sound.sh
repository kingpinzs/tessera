#!/usr/bin/env bash
# EDGE_LOCKED_SOUND — two clauses no row drives:
#   T15-22 (Decisions "One exact-alarm scheduler", Direct boot): an alarm whose sound is "Pick from my music" (the fixture
#   song.m4a in Music/, a MediaStore file, chosen through the editor as E31 does) rings after a reboot BEFORE the first
#   unlock with the DEFAULT sound, and `[alarms] sound <uri> locked -> default` is logged. E6c's method: a PIN set, `adb
#   reboot`, the boot poll and NO unlock (keyguard showing, user 0 RUNNING_LOCKED), the Next alarm clock holds the alarm,
#   the clock jumped to 10 s before it → an ALARM player of the shell starts with the ring activity over the keyguard;
#   the locked toast's Dismiss; then the PIN unlocks and the launcher ring (unreadable before the unlock) holds the locked
#   boot's re-arm, the fired line, the `locked -> default` line and NO `ring <id> sound=<uri>` line for the song.
#   Liveness (N-01) force-stop: `am force-stop app.tileshell` with that alarm armed → it is re-armed at the next start,
#   as reminders are (the `[alarms] rearm (…): 1 alarms, 0 timers` line and the same instant in dumpsys alarm). E6
#   asserts this for a running TIMER only (e6.sh:140-153) and E21 not at all, so the alarm case is driven here.
# Restore: RV12's clock restore, the PIN cleared, wake_device, the alarm deleted through the app,
# remove_fixture_recordings, force-stop + Home, the baseline re-asserted.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"; . "$(dirname "$0")/mic_guard.sh"

row_begin EDGE_LOCKED_SOUND "a Pick-from-my-music alarm before the first unlock rings the default; force-stop re-arms an armed alarm"
mic_guard_begin
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring
record "keyguard before the row (locksettings get-disabled)" "$(adb shell locksettings get-disabled | tr -d '\r')"

# ---- the alarm, its sound "Pick from my music" chosen through the editor (E31's route) ----------------------------------------
push_fixture_recordings
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
SONG_ID="$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"_display_name='song.m4a'\"" | tr -d '\r' | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)"
note "song.m4a MediaStore id: $SONG_ID"
assert_ne "the fixture song is in MediaStore" "" "$SONG_ID"
SONG_URI="content://media/external/audio/media/$SONG_ID"
NOW="$(device_ms)"
read -r AH AM_ <<< "$(device_hm $(( NOW + 300000 )))"
AID="$(api_alarm "$AH" "$AM_" "Locked song")"
assert_ne "the alarm was created" "" "$AID"
open_clock alarm
dump_ui "$ROW_DIR/list.xml"
tap_node "$ROW_DIR/list.xml" "alarm_row:$AID"; sleep 1.5
dump_ui "$ROW_DIR/editor.xml"
tap_node "$ROW_DIR/editor.xml" 'alarm_editor_field:sound'; sleep 1.2
dump_ui "$ROW_DIR/flyout.xml"
tap_node "$ROW_DIR/flyout.xml" 'alarm_sound:music'; sleep 2
dump_ui "$ROW_DIR/picker.xml"; screencap "$ROW_DIR/picker.png"
if [ "$(has_node "$ROW_DIR/picker.xml" alarm_sound_grant)" = yes ]; then
  note "the picker asks for READ_MEDIA_AUDIO; granting through its own button"
  tap_node "$ROW_DIR/picker.xml" alarm_sound_grant; sleep 2
  dump_ui "$ROW_DIR/perm.xml"
  tap_node "$ROW_DIR/perm.xml" com.android.permissioncontroller:id/permission_allow_button 2>/dev/null; sleep 2
  dump_ui "$ROW_DIR/picker.xml"
fi
assert_eq "the picker lists the song (alarm_sound_pick:$SONG_ID)" yes "$(has_node "$ROW_DIR/picker.xml" "alarm_sound_pick:$SONG_ID")"
tap_node "$ROW_DIR/picker.xml" "alarm_sound_pick:$SONG_ID"; sleep 1.5
dump_ui "$ROW_DIR/chosen.xml"
assert_eq "the Sound value names the song" "Fixture song" "$(node_text "$ROW_DIR/chosen.xml" 'alarm_editor_field:sound')"
tap_node "$ROW_DIR/chosen.xml" "clock_bar:save"; sleep 1.5
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_eq "the store holds the song's URI as the alarm's sound" "$SONG_URI" "$(store_alarms | python3 -c 'import json,sys; a=[x for x in json.load(sys.stdin) if x["id"]==sys.argv[1]]; print(a[0]["sound"]["uri"] if a else "")' "$AID")"
AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"
assert_ne "… and it is armed" 0 "$AT"

# ---- Liveness: force-stop with the alarm armed → re-armed at the next start ---------------------------------------------------------
# The shell is the HOME app: Android relaunches it the instant it is force-stopped (e6.sh's note), so the cancelled gap
# lasts milliseconds; it is polled and recorded, and the re-arm at the restart is the assertion.
MARK="$(ring_mark)"
adb shell am force-stop app.tileshell
GAP="present"
for _ in 1 2 3 4 5 6 7 8 9 10; do [ -z "$(alarm_trigger_ms)" ] && { GAP="absent"; break; }; sleep 0.2; done
record "the alarm's entry right after the force-stop (polled 2 s; the home app restarts at once)" "$GAP"
adb shell input keyevent KEYCODE_HOME
REARM="$(wait_ring "$MARK" "1 alarms, 0 timers, exact=true" 20 | grep -F '[alarms] rearm (' | head -1)"
ring_since "$MARK" launcher > "$ROW_DIR/ring_forcestop_launcher.txt"
note "re-arm after the force-stop: ${REARM:-none}"
assert_ne "force-stop: the next start re-arms the alarm ([alarms] rearm (…): 1 alarms, 0 timers, exact=true)" "" "$REARM"
record "… the re-arm's reason" "$(printf '%s' "$REARM" | grep -oE 'rearm \([^)]*\)')"
assert_eq "… at the same instant in dumpsys alarm" "$AT" "$(alarm_trigger_ms | paste -sd,)"
assert_eq "… and the Next alarm clock line holds it again" "$AT" "$(next_alarm_clock_ms)"

# ---- reboot with a PIN, no unlock ----------------------------------------------------------------------------------------------------
set_pin
ring_save launcher; cp "$ROW_DIR/ring-launcher.txt" "$ROW_DIR/ring-launcher-prekill.txt"
BMARK="$(ring_mark)"
BOOT="$(reboot_and_wait)"; note "booted at $BOOT (mark before the reboot $BMARK)"
sleep 5
assert_eq "after the boot the keyguard is showing" "true" "$(keyguard_showing)"
USER0="$(adb shell dumpsys user | tr -d '\r' | sed -n '/UserInfo{0:/,/UserInfo{[1-9]/p' | grep -m1 -E 'State:|RUNNING_LOCKED|RUNNING_UNLOCKED')"
note "dumpsys user, user 0: $USER0"
assert_contains "user 0 is still locked (RUNNING_LOCKED)" "RUNNING_LOCKED" "$USER0"
record "getprop sys.user.0.ce_available before the unlock" "[$(adb shell getprop sys.user.0.ce_available | tr -d '\r')]"
assert_eq "dumpsys alarm's Next alarm clock holds the alarm (re-armed at the locked boot)" "$AT" "$(next_alarm_clock_ms)"
[ "$AT" -gt 0 ] || AT="$(device_ms)"
jump_clock $(( AT - 10000 )) >/dev/null
STARTED=0
for _ in $(seq 1 60); do STARTED="$(alarm_player_started)"; [ "$STARTED" != 0 ] && break; sleep 0.5; done
assert_ne "locked: an ALARM player of the shell started within 30 s (the ring sounds before the first unlock)" 0 "$STARTED"
sleep 2
adb shell dumpsys audio | tr -d '\r' > "$ROW_DIR/audio_locked_ring.txt"
assert_contains "locked: the ring activity is on top" "clock.RingActivity" "$(current_focus)"
assert_eq "locked: … with the keyguard showing" "true" "$(keyguard_showing)"
USER0B="$(adb shell dumpsys user | tr -d '\r' | sed -n '/UserInfo{0:/,/UserInfo{[1-9]/p' | grep -m1 -E 'RUNNING_LOCKED|RUNNING_UNLOCKED')"
assert_contains "locked: user 0 is still locked while it rings" "RUNNING_LOCKED" "$USER0B"
# /sdcard is not mounted before the first unlock: the dump goes to /data/local/tmp (e6c.sh's dump_locked).
dump_locked() { adb shell uiautomator dump /data/local/tmp/qa_locked.xml >/dev/null 2>&1; adb shell cat /data/local/tmp/qa_locked.xml > "$1" 2>/dev/null; grep -q '<node' "$1"; }
dump_locked "$ROW_DIR/ring_locked.xml" || note "the locked dump failed: $(head -c 120 "$ROW_DIR/ring_locked.xml")"
screencap "$ROW_DIR/ring_locked.png"
assert_eq "locked: the toast is up (ring_dismiss in the dump)" yes "$(has_node "$ROW_DIR/ring_locked.xml" ring_dismiss)"
tap_node "$ROW_DIR/ring_locked.xml" ring_dismiss; sleep 3
assert_eq "locked: Dismiss stopped the player" 0 "$(alarm_player_started)"

# ---- the unlock: the launcher ring of the locked boot ------------------------------------------------------------------------------------
unlock_with_pin
assert_eq "the PIN unlocked the phone" "false" "$(keyguard_showing)"
sleep 4
ring_since "$BMARK" launcher > "$ROW_DIR/ring_locked_sound_launcher.txt"
SL="$(cat "$ROW_DIR/ring_locked_sound_launcher.txt")"
assert_contains "the ring holds rearm (locked boot): 1 alarms, 0 timers" "[alarms] rearm (locked boot): 1 alarms, 0 timers" "$SL"
assert_contains "… fired $AID from the locked boot" "[alarms] fired $AID kind=alarm" "$SL"
assert_contains "… [alarms] sound $SONG_URI locked -> default (the song cannot be read before the unlock)" "[alarms] sound $SONG_URI locked -> default" "$SL"
assert_absent "… and NOT ring $AID sound=$SONG_URI (the song did not play)" "[alarms] ring $AID sound=$SONG_URI" "$SL"
assert_contains "… and ring ended $AID: dismiss" "[alarms] ring ended $AID: dismiss" "$SL"
note "E31 proves the unlocked counterpart: the same Pick-from-my-music alarm rings the song ([alarms] ring <id> sound=content://…)"

# ---- restore -------------------------------------------------------------------------------------------------------------------------------
ring_save launcher
clock_restore
clear_pin
assert_eq "restore: wake_device printed Awake (C-25)" "Awake" "$(wake_device)"
app_delete_alarm "$AID"
remove_fixture_recordings
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 3
record "restore: locksettings get-disabled" "$(adb shell locksettings get-disabled | tr -d '\r')"
assert_eq "restore: the fixture song is gone from MediaStore" "" "$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"_display_name='song.m4a'\"" | tr -d '\r' | grep -oE '_id=[0-9]+')"
assert_clock_empty "restore"
mic_guard_end
row_end
