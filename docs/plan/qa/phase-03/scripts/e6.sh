#!/usr/bin/env bash
# E6 — the Cortana tile's one-shot ADD, and the reminder re-registration a force-stop and a reboot need.
#
#   "The Cortana tile is present in the default layout after this phase even when a persisted phase
#    01/02 layout exists (dump), and tapping it opens Cortana; after the tile is unpinned (phase 02
#    edit mode), `am force-stop` and `adb reboot` do not bring it back. A reminder stored before the
#    force-stop is listed again in `dumpsys alarm` once the shell restarts, and again after the reboot."
#
# The tile half is about the ADD running ONCE. The reminder half is about the platform throwing alarms
# away and the store putting them back.
. "$(dirname "$0")/lib.sh"

row_begin E6 "the Cortana tile ADD runs once, and reminders survive a force-stop and a reboot"

LAYOUT=/data/data/app.tileshell/files/start_layout.json

layout_json() { adb shell run-as app.tileshell cat $LAYOUT 2>/dev/null; }
alarm_holds() { # reminder text
  adb shell dumpsys alarm 2>/dev/null | grep -c "tileshell://reminder"
}

# ---- the tile is on Start over a persisted layout ----------------------------------------------
ensure_start
sleep 2
adb shell run-as app.tileshell cat $LAYOUT > "$ROW_DIR/e6_layout_before.json" 2>/dev/null
assert_contains "the layout store recorded the one-shot ADD" "phase03:cortana" "$(cat "$ROW_DIR/e6_layout_before.json")"
assert_contains "and the Cortana tile is in the order" "shell:cortana" "$(cat "$ROW_DIR/e6_layout_before.json")"

dump_ui "$ROW_DIR/e6_start.xml"
screencap "$ROW_DIR/e6_start.png"
assert_eq "the Cortana tile is drawn on Start" "yes" "$(has_node "$ROW_DIR/e6_start.xml" cortana_tile_face)"

# Tapping it opens the same session every other entry point opens.
tap_node "$ROW_DIR/e6_start.xml" cortana_tile_face
sleep 4
dump_ui "$ROW_DIR/e6_from_tile.xml"
assert_eq "tapping the tile opens Cortana" "yes" "$(has_node "$ROW_DIR/e6_from_tile.xml" cortana_session)"
cortana_close
ensure_start

# ---- a reminder, then a force-stop -------------------------------------------------------------
cortana_assist
sleep 4
"$HERE/speak.sh" reminder_time 11 > /dev/null
sleep 1
dump_ui "$ROW_DIR/e6_card.xml"
screencap "$ROW_DIR/e6_card.png"
"$HERE/speak.sh" yes 10 > /dev/null
sleep 2
adb shell run-as app.tileshell cat /data/data/app.tileshell/files/cortana_reminders.json > "$ROW_DIR/e6_reminders.json" 2>/dev/null
assert_contains "the reminder is in the store" "bins" "$(cat "$ROW_DIR/e6_reminders.json")"
armed_before="$(alarm_holds)"
assert_ne "and its alarm is armed" "0" "$armed_before"

ensure_start
adb shell am force-stop app.tileshell
sleep 3
assert_eq "a force-stop cancels the alarm" "0" "$(alarm_holds)"
# Home restarts the shell's process, which re-arms from the store.
ensure_start
sleep 5
assert_ne "the shell re-arms it on process start" "0" "$(alarm_holds)"
note "alarms after the force-stop and restart: $(alarm_holds)"

# ---- unpin, then prove the ADD does not run again -----------------------------------------------
# The unpin is done through the store rather than the edit-mode gesture: this row is about the ADD's
# once-only marker, and phase 02's gate owns the gesture. The marker is what E6 is checking.
adb shell run-as app.tileshell cat $LAYOUT | python3 -c "
import json,sys
layout = json.load(sys.stdin)
layout['order'] = [t for t in layout['order'] if t['key'] != 'shell:cortana']
print(json.dumps(layout))
" > "$ROW_DIR/e6_unpinned.json"
adb push "$ROW_DIR/e6_unpinned.json" /data/local/tmp/unpinned.json >/dev/null 2>&1
adb shell "run-as app.tileshell cp /data/local/tmp/unpinned.json $LAYOUT"
adb shell am force-stop app.tileshell
ensure_start
sleep 5
adb shell run-as app.tileshell cat $LAYOUT > "$ROW_DIR/e6_layout_after_restart.json" 2>/dev/null
assert_absent "a restart does not bring the tile back" "shell:cortana" "$(cat "$ROW_DIR/e6_layout_after_restart.json")"
assert_contains "because the marker is still recorded" "phase03:cortana" "$(cat "$ROW_DIR/e6_layout_after_restart.json")"

# ---- and across a reboot -------------------------------------------------------------------------
adb reboot
adb wait-for-device
# The boot is not finished when the device answers adb.
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
sleep 12
ensure_start
sleep 8
adb shell run-as app.tileshell cat $LAYOUT > "$ROW_DIR/e6_layout_after_reboot.json" 2>/dev/null
assert_absent "a reboot does not bring the tile back" "shell:cortana" "$(cat "$ROW_DIR/e6_layout_after_reboot.json")"
assert_ne "and the reminder's alarm is armed again after the reboot" "0" "$(alarm_holds)"
adb shell dumpsys alarm > "$ROW_DIR/e6_alarm_after_reboot.txt" 2>/dev/null

# Put the tile back so the rest of the gate sees the shipped layout.
adb shell run-as app.tileshell cp "$LAYOUT" "$LAYOUT.e6backup" 2>/dev/null
adb push "$ROW_DIR/e6_layout_before.json" /data/local/tmp/restore.json >/dev/null 2>&1
adb shell "run-as app.tileshell cp /data/local/tmp/restore.json $LAYOUT"
adb shell am force-stop app.tileshell
ensure_start
note "layout restored to the pre-row state"

row_end
