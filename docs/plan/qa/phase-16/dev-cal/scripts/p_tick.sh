#!/usr/bin/env bash
# Probe (a record): ticking a calendar on "Can sync to" reached from Settings, step by step, after E24_SYNC2's first run
# read it un-ticked.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin P_TICK "probe: the Can sync to tick from the settings page"
c6; cal_fixtures_down; sync_fixtures_up
open_cal -a android.intent.action.MAIN; sleep 2
record "json at the start" "$(sync_json)"
tap cal_bar:more 1.2; tap cal_more:settings 1.5; tap cal_settings_open_can_sync 1.5
dump_ui "$ROW_DIR/before.xml"
record "before the tick" "$(node_attr "$ROW_DIR/before.xml" "cal_settings_can_sync:$PERSONAL" checked) $(bounds "$ROW_DIR/before.xml" "cal_settings_can_sync:$PERSONAL")"
MARK="$(ring_mark)"
tap "cal_settings_can_sync:$PERSONAL" 0.3
dump_ui "$ROW_DIR/after1.xml"; record "right after the tick" "$(node_attr "$ROW_DIR/after1.xml" "cal_settings_can_sync:$PERSONAL" checked) json: $(sync_json)"
sleep 2
dump_ui "$ROW_DIR/after2.xml"; record "two seconds later" "$(node_attr "$ROW_DIR/after2.xml" "cal_settings_can_sync:$PERSONAL" checked) json: $(sync_json)"
record "ring since the tick" "$(ring_since "$MARK" | grep -F '[calendar]' | cut -c36-200 | tr '\n' ';')"
c6; cal_fixtures_down; ensure_start
session_end
