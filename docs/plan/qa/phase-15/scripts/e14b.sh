#!/usr/bin/env bash
# E14b — an uninstall keeps recordings and orphans them (T15-7, Q3, T15-26). Runs LAST among the recorder rows: it changes
# ownership. A throwaway take renamed "Orphan"; `adb uninstall app.tileshell` → the file is still in /sdcard/Recordings and
# MediaStore still finds it; the shell reinstalled through qa/phase-03/scripts/provision.sh (RV12) — ONLY with AUDIO_SINK
# naming this emulator's own sink (p15.sh exports vmic<port>; provision's `audio.sh setup` would otherwise move the desktop's
# microphone) — → the same query shows owner_package_name NULL, Voice Recorder lists it as another app's (Share alone in the
# hold menu and the app bar) and counts it `(1 by other apps)`. Restore: the file removed + rescan; the notification
# listener re-allowed, the layout saved before the uninstall put back, the shell's IME re-enabled if it was, the home
# activity re-asserted; the desktop's default source asserted (and put back if provision moved it).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
. "$QROOT/phase-02/scripts/layout.sh"
RINGS="launcher $REC_SVC"
row_begin E14b "an uninstall keeps recordings; reinstalled, the shell lists its old take as another app's"

BRIO="alsa_input.usb-046d_Logitech_BRIO_9130DEA5-03.analog-stereo"
LISTENER="app.tileshell/app.tileshell.feeds.TileNotificationListener"
wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
audio_route "E14b"
# What the uninstall will take with it, kept for the restore.
layout_save "$ROW_DIR/layout_before.json"
note "layout saved: $(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(len(d.get("order",[])), "tiles,", len(d.get("dock",[])), "docked")' "$ROW_DIR/layout_before.json")"
LISTENERS0="$(adb shell settings get secure enabled_notification_listeners | tr -d '\r')"
IMES0="$(adb shell ime list -s | tr -d '\r' | paste -sd' ')"
IME0="$(adb shell settings get secure default_input_method | tr -d '\r')"
ASSIST0="$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
SRC0="$(pactl get-default-source)"
note "before: listeners=[$LISTENERS0] imes=[$IMES0] default ime=[$IME0] assistant=[$ASSIST0] default source=[$SRC0]"
assert_eq "precondition: AUDIO_SINK names this emulator's own sink, so provision.sh's audio setup is safe" "vmic${ANDROID_SERIAL#emulator-}" "${AUDIO_SINK:-}"

if [ "$AUDIO_OK" = yes ] && [ "${AUDIO_SINK:-}" = "vmic${ANDROID_SERIAL#emulator-}" ]; then
  rec_open record
  TAKE="$(make_take 3)"; assert_ne "the throwaway take is in MediaStore" "" "$TAKE"
  dump_ui "$ROW_DIR/list.xml"
  hold_node "$ROW_DIR/list.xml" "rec_row:$TAKE"; sleep 1
  dump_ui "$ROW_DIR/menu.xml"; gtap "$ROW_DIR/menu.xml" "rec_menu:rename"; sleep 1.5
  dump_ui "$ROW_DIR/rename.xml"
  adb shell input text Orphan; sleep 0.5; gtap "$ROW_DIR/rename.xml" rec_rename_ok; sleep 2
  assert_eq "the take is named Orphan.m4a" Orphan.m4a "$(ms_field "$(row_by_id "$TAKE")" _display_name)"
  assert_eq "and owned by the shell" app.tileshell "$(ms_field "$(row_by_id "$TAKE")" owner_package_name)"
  rec_ring_save
  adb shell input keyevent KEYCODE_HOME; sleep 1
  # ---- the uninstall -------------------------------------------------------------------------------------------------
  U="$(adb uninstall app.tileshell 2>&1 | tr -d '\r')"; note "adb uninstall: $U"
  assert_contains "the shell is uninstalled" Success "$U"
  sleep 3
  assert_eq "the package is gone" "" "$(adb shell pm path app.tileshell 2>/dev/null | tr -d '\r')"
  LS="$(adb shell ls /sdcard/Recordings 2>/dev/null | tr -d '\r' | paste -sd' ')"; note "ls /sdcard/Recordings after the uninstall: [$LS]"
  assert_contains "ls /sdcard/Recordings still lists Orphan.m4a" "Orphan.m4a" "$LS"
  Q="$(adb shell content query --uri "$MEDIA_URI" --projection _id:_display_name:owner_package_name --where "\"_display_name='Orphan.m4a'\"" 2>/dev/null | tr -d '\r' | grep '^Row:')"
  note "query after the uninstall: [$Q]"
  assert_contains "MediaStore still finds Orphan.m4a" "_display_name=Orphan.m4a" "$Q"
  record "owner_package_name right after the uninstall (before any reinstall)" "$(ms_field "$Q" owner_package_name)"
  # ---- reinstall through provision.sh (RV12), with this emulator's own sink exported -----------------------------------
  note "provision.sh with AUDIO_SINK=$AUDIO_SINK (output in provision.txt)"
  "$QROOT/phase-03/scripts/provision.sh" > "$ROW_DIR/provision.txt" 2>&1; PRC=$?
  note "provision.sh rc=$PRC; last lines: $(tail -3 "$ROW_DIR/provision.txt" | paste -sd'|' | cut -c1-200)"
  assert_eq "provision.sh completed (rc 0: the shell installed with -g, roles, home activity, audio route, a tap works)" 0 "$PRC"
  assert_eq "the shell is back" yes "$([ -n "$(adb shell pm path app.tileshell 2>/dev/null | tr -d '\r')" ] && echo yes || echo no)"
  assert_eq "the installed APK is the built one" "$(md5sum "$APK" | cut -c1-16)" "$(installed_apk_id)"
  SRC1="$(pactl get-default-source)"
  if [ "$SRC1" != "$BRIO" ]; then
    note "the desktop's default source is [$SRC1]; putting it back to the BRIO (REPORTED)"
    pactl set-default-source "$BRIO"
  fi
  assert_eq "the desktop's default source is still the BRIO after provision.sh" "$BRIO" "$(pactl get-default-source)"
  audio_route "after provision.sh"
  adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity >/dev/null 2>&1
  adb shell cmd notification allow_listener "$LISTENER" >/dev/null 2>&1
  wake_device >/dev/null
  # ---- the orphan -------------------------------------------------------------------------------------------------------
  Q2="$(adb shell content query --uri "$MEDIA_URI" --projection _id:_display_name:owner_package_name:is_recording --where "\"_display_name='Orphan.m4a'\"" 2>/dev/null | tr -d '\r' | grep '^Row:')"
  note "query after the reinstall: [$Q2]"
  ORPHAN="$(ms_field "$Q2" _id)"
  assert_eq "after the reinstall owner_package_name is NULL (T15-26)" NULL "$(ms_field "$Q2" owner_package_name)"
  assert_eq "and it is still IS_RECORDING" 1 "$(ms_field "$Q2" is_recording)"
  LMARK="$(ring_mark)"
  rec_open list
  dump_ui "$ROW_DIR/list_orphan.xml"; screencap "$ROW_DIR/list_orphan.png"
  assert_eq "Voice Recorder lists the orphan" yes "$(has_node "$ROW_DIR/list_orphan.xml" "rec_row:$ORPHAN")"
  ring_since "$LMARK" > "$ROW_DIR/ring_list.txt"
  assert_contains "the launcher ring counts it as another app's" "[recorder] list: 1 recordings (1 by other apps)" "$(cat "$ROW_DIR/ring_list.txt")"
  hold_node "$ROW_DIR/list_orphan.xml" "rec_row:$ORPHAN"; sleep 1
  dump_ui "$ROW_DIR/menu_orphan.xml"; screencap "$ROW_DIR/menu_orphan.png"
  note "orphan hold menu: $(ids_with_prefix "$ROW_DIR/menu_orphan.xml" 'rec_menu' | paste -sd' ')"
  assert_eq "its hold menu offers rec_menu:share" yes "$(has_node "$ROW_DIR/menu_orphan.xml" 'rec_menu:share')"
  assert_eq "and NO rec_menu:delete" no "$(has_node "$ROW_DIR/menu_orphan.xml" 'rec_menu:delete')"
  assert_eq "and NO rec_menu:rename" no "$(has_node "$ROW_DIR/menu_orphan.xml" 'rec_menu:rename')"
  adb shell input keyevent KEYCODE_BACK; sleep 1
  dump_ui "$ROW_DIR/list_orphan2.xml"
  gtap "$ROW_DIR/list_orphan2.xml" "rec_row:$ORPHAN"; sleep 1.5
  rdump "$ROW_DIR/play_orphan.xml"; screencap "$ROW_DIR/play_orphan.png"
  note "orphan app bar: $(ids_with_prefix "$ROW_DIR/play_orphan.xml" 'rec_bar' | paste -sd' ')"
  assert_eq "its playback app bar holds rec_bar:share" yes "$(has_node "$ROW_DIR/play_orphan.xml" 'rec_bar:share')"
  for m in trim delete rename; do
    assert_eq "and NO rec_bar:$m" no "$(has_node "$ROW_DIR/play_orphan.xml" "rec_bar:$m")"
  done
  sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 1
  rec_ring_save
  adb shell input keyevent KEYCODE_HOME; sleep 1
  # ---- restore ----------------------------------------------------------------------------------------------------------
  adb shell rm -f /sdcard/Recordings/Orphan.m4a
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1; sleep 1
  assert_eq "restore: Orphan.m4a is gone from MediaStore" "" "$(id_by_name Orphan.m4a)"
  assert_contains "restore: the notification listener is allowed again" "$LISTENER" "$(adb shell settings get secure enabled_notification_listeners | tr -d '\r')"
  case "$IMES0" in *app.tileshell/.ime.KeyboardService*) adb shell ime enable app.tileshell/.ime.KeyboardService >/dev/null 2>&1; note "restore: the shell's IME re-enabled (it was enabled before)" ;; esac
  [ -n "$IME0" ] && [ "$IME0" != null ] && adb shell ime set "$IME0" >/dev/null 2>&1
  layout_restore "$ROW_DIR/layout_before.json"; LRC=$?
  assert_eq "restore: the layout saved before the uninstall is back (layout_restore rc)" 0 "$LRC"
  assert_eq "restore: the assistant role holder as before" "$ASSIST0" "$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
  assert_eq "restore: the home activity is the shell's" "app.tileshell/.StartActivity" "$(adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1 | tr -d '\r')"
  note "after: listeners=[$(adb shell settings get secure enabled_notification_listeners | tr -d '\r')] imes=[$(adb shell ime list -s | tr -d '\r' | paste -sd' ')] default ime=[$(adb shell settings get secure default_input_method | tr -d '\r')]"
  note "NOT restored by design: the shell's app data the uninstall wiped (recordings.json, any checklist/settings state); provision.sh set the baseline the rows assume"
else
  _verdict FAIL "the uninstall and reinstall" "NOT RUN: $([ "$AUDIO_OK" = yes ] || echo 'the audio route failed its check;') $([ "${AUDIO_SINK:-}" = "vmic${ANDROID_SERIAL#emulator-}" ] || echo "AUDIO_SINK is [${AUDIO_SINK:-}], not this emulator's own sink: provision.sh must not run")"
fi
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
