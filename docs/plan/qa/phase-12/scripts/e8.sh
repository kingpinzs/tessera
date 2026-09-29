#!/usr/bin/env bash
# Phase 12 E8 — the keys and the pivot while the wizard shows, and the pin band waiting under it (T12-14).
#   Keys: a no-marker provision with notification access and Usage access revoked gives a two-step run whose ASSISTANT
#   role is held, so the Search key really opens Tess (the E2 state removes the role, and then Search shows her role
#   notice instead). Back does nothing on step 1 and returns from step 2; the Windows key and Home do nothing; Search
#   opens Tess and Back returns to the same step; Android's bars hidden, the drawn bars at their measured heights.
#   Pin band: from the E2 state with the wizard showing, a secondary-tile request waits; Skip -> the band over Start.
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
row_begin E8 "keys, bars and the pin band while the wizard shows"
strip() { grep -oE '<node [^>]*resource-id="(wizard|preset)[^"]*"[^>]*>' "$1" | sed -E 's/ (focused|selected)="[a-z]*"//g'; }
tap_id() { tap_node "$1" "$2"; }

leave_home
adb shell pm clear "$PKG" >/dev/null
provision_no_marker keys
assert_eq "no-marker provision rc" "0" "$(cat "$ROW_DIR/provision-keys.rc")"
adb shell cmd notification disallow_listener "$LISTENER"
adb shell appops set "$PKG" GET_USAGE_STATS ignore
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/k1.xml"
assert_eq "step 1: notifications" "setup:notifications" "$(wiz_step "$ROW_DIR/k1.xml")"
assert_eq "Step 1 of 3" "Step 1 of 3" "$(ntext "$ROW_DIR/k1.xml" wizard_progress)"

log "drawn Back on step 1 changes nothing"
tap_id "$ROW_DIR/k1.xml" nav_back; sleep 1.5
dump_ui "$ROW_DIR/k1-back.xml"
assert_eq "Back on step 1: wizard nodes identical" "$(strip "$ROW_DIR/k1.xml" | sha256sum | cut -c1-16)" "$(strip "$ROW_DIR/k1-back.xml" | sha256sum | cut -c1-16)"

log "Back on step 2 returns to step 1"
tap_id "$ROW_DIR/k1-back.xml" wizard_not_now; sleep 1.5
dump_ui "$ROW_DIR/k2.xml"
assert_eq "step 2: usage" "setup:usage" "$(wiz_step "$ROW_DIR/k2.xml")"
tap_id "$ROW_DIR/k2.xml" nav_back; sleep 1.5
dump_ui "$ROW_DIR/k2-back.xml"
assert_eq "drawn Back on step 2: step 1 again" "setup:notifications" "$(wiz_step "$ROW_DIR/k2-back.xml")"
assert_eq "Step 1 of 3 again" "Step 1 of 3" "$(ntext "$ROW_DIR/k2-back.xml" wizard_progress)"
tap_id "$ROW_DIR/k2-back.xml" wizard_not_now; sleep 1.5
adb shell input keyevent KEYCODE_BACK; sleep 1.5
dump_ui "$ROW_DIR/k2-sysback.xml"
assert_eq "system Back on step 2: step 1 again" "setup:notifications" "$(wiz_step "$ROW_DIR/k2-sysback.xml")"

log "the Windows key and Home do nothing"
tap_id "$ROW_DIR/k2-sysback.xml" nav_windows; sleep 1.5
dump_ui "$ROW_DIR/k-windows.xml"
assert_eq "Windows key: wizard nodes identical" "$(strip "$ROW_DIR/k2-sysback.xml" | sha256sum | cut -c1-16)" "$(strip "$ROW_DIR/k-windows.xml" | sha256sum | cut -c1-16)"
adb shell input keyevent KEYCODE_HOME; sleep 2
dump_ui "$ROW_DIR/k-home.xml"
assert_eq "Home: the wizard stays, same step" "setup:notifications" "$(wiz_step "$ROW_DIR/k-home.xml")"
assert_eq "Home: no start_page" "no" "$(has_node "$ROW_DIR/k-home.xml" start_page)"

log "the Search key opens Tess; Back returns to the same step"
tap_id "$ROW_DIR/k-home.xml" nav_search; sleep 3
dump_ui "$ROW_DIR/k-tess.xml"
assert_eq "Search: cortana_session" "yes" "$(has_node "$ROW_DIR/k-tess.xml" cortana_session)"
adb shell input keyevent KEYCODE_BACK; sleep 2.5
dump_ui "$ROW_DIR/k-after-tess.xml"
assert_eq "Back from Tess: the wizard at the same step" "setup:notifications" "$(wiz_step "$ROW_DIR/k-after-tess.xml")"

log "Android's bars hidden; the drawn bars measured"
ins="$(adb shell dumpsys window | grep -m2 -E 'type=statusBars|type=navigationBars' | tr -s ' ' | tr '\n' ' ')"
note "insets: $ins"
assert_contains "system status bar not visible" "type=statusBars frame=[0,0][1080,136] visible=false" "$ins"
assert_contains "system nav bar not visible" "visible=false" "$(echo "$ins" | grep -o 'type=navigationBars[^I]*')"
read -r sl st sr sb <<<"$(bounds "$ROW_DIR/k-after-tess.xml" w10m_status_bar)"
read -r nl nt nr nb <<<"$(bounds "$ROW_DIR/k-after-tess.xml" w10m_nav_bar)"
STATUS_EPX="$(grep -oE 'const val STATUS_EPX = [0-9]+' "$REPO/app/src/main/kotlin/app/tileshell/bars/SystemBars.kt" | grep -oE '[0-9]+$')"
assert_eq "drawn status bar height = BarMetrics.STATUS_EPX ($STATUS_EPX epx x 3 px)" "$((STATUS_EPX * 3))" "$((sb - st))"
assert_eq "drawn nav bar height = 48 epx (144 px)" "144" "$((nb - nt))"
assert_eq "drawn nav bar at the bottom" "2340" "$nb"
for k in nav_back nav_windows nav_search; do
  read -r l t r b <<<"$(bounds "$ROW_DIR/k-after-tess.xml" $k)"
  assert_eq "X17: $k is one third of the width" "360" "$((r - l))"
done

log "pin band under the wizard (T12-14)"
TAPK="$REPO/testapps/tileclient-a/build/outputs/apk/debug/tileclient-a-debug.apk"
adb install -r -g "$TAPK" > "$ROW_DIR/tileclient-install.txt" 2>&1
assert_contains "tileclient-a installed" "Success" "$(cat "$ROW_DIR/tileclient-install.txt")"
e2_state
adb shell input keyevent KEYCODE_HOME; sleep 5
adb shell am start -n app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity --es verb secondary.requestCreate \
  --es tileId st1 --es displayName Jen --es arguments chat=42 --es size medium --ez logo true >/dev/null
sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 3
dump_ui "$ROW_DIR/pin-wizard.xml"
assert_eq "request pending: wizard_page" "yes" "$(has_node "$ROW_DIR/pin-wizard.xml" wizard_page)"
assert_eq "request pending: no secondary_pin_prompt behind the wizard" "no" "$(has_node "$ROW_DIR/pin-wizard.xml" secondary_pin_prompt)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/pin-wizard.xml" wizard_skip; sleep 3
dump_ui "$ROW_DIR/pin-start.xml"
screencap "$ROW_DIR/pin-start.png"
start_slice "$MARK" "skip with a pin pending"
assert_contains "[wizard] skip" "[wizard] skip" "$SLICE"
assert_eq "the band shows once Start is visible" "yes" "$(has_node "$ROW_DIR/pin-start.xml" secondary_pin_prompt)"
assert_eq "over start_page" "yes" "$(has_node "$ROW_DIR/pin-start.xml" start_page)"
tap_node "$ROW_DIR/pin-start.xml" secondary_pin_cancel; sleep 2
dump_ui "$ROW_DIR/pin-cancelled.xml"
assert_eq "band cancelled" "no" "$(has_node "$ROW_DIR/pin-cancelled.xml" secondary_pin_prompt)"

log "restore: pm clear -> provision.sh -> Home"
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
