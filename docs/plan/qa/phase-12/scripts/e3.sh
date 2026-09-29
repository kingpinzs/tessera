#!/usr/bin/env bash
# Phase 12 E3 — the real grants through the real pages and dialogs, then a preset.
# The E2 state (after layout_restore of phase 02's baseline); every step walked with wizard_action through Android's own
# page or dialog (screen_act.py decides each tap from the dump, and logs it), each advancing with its granted line;
# tess:background_location in its two legs (precise first -> partial, then "Allow all the time" on the app's location
# page -> granted), no pm grant anywhere on this path. Then the presets page: six presets in order, Default selected;
# HAL + Done -> Start with theme_preset hal, accent Red, the tile band over the picture by the picture oracle, the ring
# lines in order, and both checklists green.
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
row_begin E3 "real grants through Android's pages, then HAL"
why_for() { awk -F'\t' -v k="$1" '$1==k {print $2}' "$HERE/why_lines.tsv"; }
. "$LAYOUT_SH"

e2_state
assert_e2_appops
MARK_SEED="$(ring_mark)"
layout_restore "$QAROOT/phase-02/baseline_layout.json" > "$ROW_DIR/layout-restore.txt" 2>&1
echo $? > "$ROW_DIR/layout-restore.rc"
assert_eq "layout_restore of phase 02's baseline" "0" "$(cat "$ROW_DIR/layout-restore.rc")"
adb shell input keyevent KEYCODE_HOME; sleep 4

walk_step() { # key
  local key="$1" s="$ROW_DIR/st-$1" i act first="" actions="" state="$ROW_DIR/.state-$1.json"
  rm -f "$state"
  adb shell input keyevent KEYCODE_WAKEUP
  dump_ui "$s-page.xml"
  tap_node "$s-page.xml" wizard_action
  sleep 2.5
  first="$(resumed_activity)"
  for i in $(seq 1 14); do
    dump_ui "$s-$i.xml"
    act="$(python3 "$HERE/screen_act.py" "$s-$i.xml" "$key" "$state")"
    actions="$actions | $act"
    case "$act" in
      wizard*) break ;;
      tap*) set -- $act; adb shell input tap "$2" "$3"; sleep 2 ;;
      back*) adb shell input keyevent KEYCODE_BACK; sleep 2 ;;
      *) screencap "$s-$i-unknown.png"; break ;;
    esac
  done
  note "$key: first resumed [$first]; actions:$actions"
  record "$key: Android's page" "$first"
  STEP_LAST="$act"
}

steps_done=""
prev=""
for n in $(seq 1 30); do
  dump_ui "$ROW_DIR/cur-$n.xml"
  key="$(wiz_step "$ROW_DIR/cur-$n.xml")"
  [ -z "$key" ] && break
  if [ "$key" = "$prev" ]; then
    _verdict FAIL "$key advanced after its grant" "the same step is still showing; stopping the walk"
    break
  fi
  prev="$key"
  assert_eq "why line on $key" "$(why_for "$key")" "$(ntext "$ROW_DIR/cur-$n.xml" wizard_why)"
  MARK="$(ring_mark)"
  if [ "$key" = "tess:background_location" ]; then
    pk="$(adb shell dumpsys package $PKG)"
    assert_contains "before: ACCESS_FINE_LOCATION not granted" "ACCESS_FINE_LOCATION: granted=false" "$(echo "$pk" | grep 'ACCESS_FINE_LOCATION: granted' | head -1)"
    assert_contains "before: ACCESS_BACKGROUND_LOCATION not granted" "ACCESS_BACKGROUND_LOCATION: granted=false" "$(echo "$pk" | grep 'ACCESS_BACKGROUND_LOCATION: granted' | head -1)"
    walk_step "$key"
    dump_ui "$ROW_DIR/bgloc-leg1.xml"
    pk="$(adb shell dumpsys package $PKG)"
    assert_contains "leg 1: FINE granted" "ACCESS_FINE_LOCATION: granted=true" "$(echo "$pk" | grep 'ACCESS_FINE_LOCATION: granted' | head -1)"
    assert_contains "leg 1: BACKGROUND still absent" "ACCESS_BACKGROUND_LOCATION: granted=false" "$(echo "$pk" | grep 'ACCESS_BACKGROUND_LOCATION: granted' | head -1)"
    assert_eq "leg 1: the same step still shows" "tess:background_location" "$(wiz_step "$ROW_DIR/bgloc-leg1.xml")"
    start_slice "$MARK" "bgloc leg 1"
    assert_contains "leg 1: [wizard] step tess:background_location: partial" "[wizard] step tess:background_location: partial" "$SLICE"
    MARK="$(ring_mark)"
    walk_step "$key"
    pk="$(adb shell dumpsys package $PKG)"
    assert_contains "leg 2: BACKGROUND granted" "android.permission.ACCESS_BACKGROUND_LOCATION: granted=true" "$(echo "$pk" | grep 'ACCESS_BACKGROUND_LOCATION: granted' | head -1)"
    assert_contains "leg 2: appops FINE_LOCATION allow (not foreground)" "allow" "$(adb shell appops get $PKG FINE_LOCATION | tr -d '\r')"
    assert_absent "leg 2: appops FINE_LOCATION not foreground" "foreground" "$(adb shell appops get $PKG FINE_LOCATION | tr -d '\r')"
  else
    walk_step "$key"
  fi
  start_slice "$MARK" "$key"
  assert_contains "[wizard] step $key: granted" "[wizard] step $key: granted" "$SLICE"
  case "$key" in
    setup:full_screen_alarms) assert_contains "appops USE_FULL_SCREEN_INTENT allow" "allow" "$(adb shell appops get $PKG USE_FULL_SCREEN_INTENT | tr -d '\r')" ;;
    setup:overlay) assert_contains "appops SYSTEM_ALERT_WINDOW allow" "allow" "$(adb shell appops get $PKG SYSTEM_ALERT_WINDOW | tr -d '\r')" ;;
    tess:assistant) assert_eq "ASSISTANT role holder" "$PKG" "$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')" ;;
  esac
  steps_done="$steps_done $key"
done
note "steps walked:$steps_done"
# Granting the ASSISTANT role grants SEND_SMS, READ_SMS and READ_CALL_LOG on this image (BUILD_START/
# assistant-role-grants.txt), so once tess:assistant is granted those three rows are GRANTED and the re-derived walk never
# shows them — the wizard reads live state (T12-1). Every step of the nineteen is either walked or granted that way.
EXPECTED="setup:notifications setup:photos setup:music setup:calendar setup:location setup:usage setup:keyboard_enabled setup:keyboard_selected setup:full_screen_alarms setup:overlay tess:assistant tess:microphone tess:contacts tess:calendar tess:sms_send tess:call_phone tess:background_location tess:call_log tess:sms_read"
auto=""
for k in $EXPECTED; do case " $steps_done " in *" $k "*) ;; *) auto="$auto $k" ;; esac; done
record "steps granted by the ASSISTANT role, never shown" "${auto:-none}"
assert_eq "the steps not walked are exactly Tess's SMS and call-log rows" "tess:sms_send tess:call_log tess:sms_read" "$(echo $auto)"
pk="$(adb shell dumpsys package $PKG)"
for perm in SEND_SMS READ_CALL_LOG READ_SMS; do
  assert_contains "$perm granted (by the role, no dialog)" "android.permission.$perm: granted=true" "$(echo "$pk" | grep "android.permission.$perm: granted" | head -1)"
done
assert_eq "walked + role-granted = the nineteen" "19" "$(( $(echo $steps_done | wc -w) + $(echo $auto | wc -w) ))"
walk_slice="$(start_ring_since "$MARK_SEED")"
assert_eq "no step was declined on the way" "0" "$(printf '%s\n' "$walk_slice" | grep -c ': not now')"

log "the presets page"
dump_ui "$ROW_DIR/presets.xml"
screencap "$ROW_DIR/presets.png"
assert_eq "wizard_presets" "yes" "$(has_node "$ROW_DIR/presets.xml" wizard_presets)"
order="$(grep -oE 'resource-id="preset:[^"]*"' "$ROW_DIR/presets.xml" | sed -E 's/resource-id="preset://; s/"$//' | grep -v '^Custom$' | tr '\n' '|')"
assert_eq "the six presets in order" "Default|Windows 10 Mobile (original)|HAL|Soft|Lumia|Midnight|" "$order"
assert_eq "preset:Default selected (Compose reports it checked)" "true" "$(node_checked "$ROW_DIR/presets.xml" preset:Default)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/presets.xml" "preset:HAL"; sleep 2
dump_ui "$ROW_DIR/presets-hal.xml"
tap_node "$ROW_DIR/presets-hal.xml" wizard_done; sleep 4
dump_ui "$ROW_DIR/start.xml"
screencap "$ROW_DIR/start.png"
assert_eq "start_page is back" "yes" "$(has_node "$ROW_DIR/start.xml" start_page)"
prefs="$(adb shell run-as $PKG cat shared_prefs/start_theme.xml | tr -d '\r')"
echo "$prefs" > "$ROW_DIR/start_theme.xml"
assert_contains "theme_preset = hal" '<string name="theme_preset">hal</string>' "$prefs"
assert_contains "accent = Red 4293398819" '<long name="accent" value="4293398819" />' "$prefs"
start_slice "$MARK" "HAL + Done"
order="$(printf '%s\n' "$SLICE" | grep -oE '\[wizard\] preset HAL|\[theme\] preset HAL applied|\[wizard\] finished' | tr '\n' '|')"
assert_eq "ring order: [wizard] preset HAL, [theme] preset HAL applied, [wizard] finished" "[wizard] preset HAL|[theme] preset HAL applied|[wizard] finished|" "$order"
python3 "$HERE/picture_oracle.py" --picture "$REPO/app/src/main/res/drawable-nodpi/preset_hal.webp" --screenshot "$ROW_DIR/start.png" \
  --dump "$ROW_DIR/start.xml" --tile "${ORACLE_TILE:-tile:slot:PEOPLE}" --accent E81123 --alpha 0.72 --out "$ROW_DIR/oracle-hal.json" > "$ROW_DIR/oracle-hal.txt" 2>&1
echo $? > "$ROW_DIR/oracle-hal.rc"
assert_eq "picture oracle: T = 0.72 Red + 0.28 B +-4 and T != Red (exit 0)" "0" "$(cat "$ROW_DIR/oracle-hal.rc")"
note "oracle: $(tail -1 "$ROW_DIR/oracle-hal.txt")"
slice_seed="$(start_ring_since "$MARK_SEED")"
assert_eq "zero assignSlotOnce -> assigned after layout_restore (C-3)" "0" "$(printf '%s\n' "$slice_seed" | grep -c 'assignSlotOnce.*-> assigned')"

log "both checklists green"
open_checklist
all=""
for i in 1 2 3; do dump_ui "$ROW_DIR/checklist-$i.xml"; all="$all $(grep -oE 'checklist:[a-z_]+:[a-z]+' "$ROW_DIR/checklist-$i.xml" | tr '\n' ' ')"; adb shell input swipe 540 1700 540 800 300; sleep 1; done
for id in home notifications photos music calendar location usage keyboard_enabled keyboard_selected full_screen_alarms overlay; do
  assert_contains "checklist:$id:granted" "checklist:$id:granted" "$all"
done
adb shell input keyevent KEYCODE_HOME; sleep 2
open_tess_settings "$ROW_DIR/tess"
tall=""
for i in 1 2 3 4 5 6; do dump_ui "$ROW_DIR/tess-$i.xml"; tall="$tall $(grep -oE 'cortana_check:[a-z_]+:[a-z]+' "$ROW_DIR/tess-$i.xml" | tr '\n' ' ')"; adb shell input swipe 540 1700 540 800 300; sleep 1; done
for id in assistant microphone contacts calendar sms_send call_phone background_location call_log sms_read; do
  assert_contains "cortana_check:$id:granted" "cortana_check:$id:granted" "$tall"
done
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1

log "restore: Start + theme -> Default, then pm clear -> provision.sh -> Home"
adb shell am start -n "$PKG/.settings.SettingsActivity" --activity-clear-task --es page START_THEME >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/theme.xml"
tap_node "$ROW_DIR/theme.xml" "theme_preset:Default"; sleep 2
dump_ui "$ROW_DIR/theme-default.xml"
assert_eq "theme_preset:Default selected" "true" "$(node_checked "$ROW_DIR/theme-default.xml" theme_preset:Default)"
assert_contains "theme_preset = default" '<string name="theme_preset">default</string>' "$(adb shell run-as $PKG cat shared_prefs/start_theme.xml | tr -d '\r')"
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
