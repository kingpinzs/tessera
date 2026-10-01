#!/usr/bin/env bash
# DEV-CALLPROBE (a recorded probe, not a graded row): what the emulated network does with a call People places — to a
# number stored with spaces and a plus, and to a plain ten-digit one. Run 2 of DEV-E12 found Telecom's outgoing call
# for "+1 555 000 0001" disconnected within a quarter of a second; this reads which part of that is the number's form.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-CALLPROBE "what the emulated network does with a placed call (recorded)"
log "$(ensure_build)"
BEFORE="$(raw_count)"
D="$ROW_DIR"
A=$(fx_raw); fx_name $A "Cal Spaced"; fx_phone $A "+1 555 000 0001"
B=$(fx_raw); fx_name $B "Cal Plain"; fx_phone $B "5551230001"
probe() { # raw label
  local cid; cid="$(contact_of "$1")"
  adb shell am start -W -n "$PEOPLE" -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$cid" >/dev/null 2>&1; sleep 2
  dump_ui "$D/card-$2.xml"
  local mark; mark="$(ring_mark)"
  tap_node "$D/card-$2.xml" people_card_action:call:0
  sleep 1; record "$2: gsm list 1 s after the tap" "$(adb emu gsm list | tr -d '\r' | tr '\n' ' ')"
  sleep 3; record "$2: gsm list 4 s after the tap" "$(adb emu gsm list | tr -d '\r' | tr '\n' ' ')"
  record "$2: the action line" "$(ring_since "$mark" | grep -F '[people] action' | sed 's/.*\[people\] //')"
  record "$2: top activity" "$(top_activity)"
  adb shell dumpsys telecom > "$D/telecom-$2.txt" 2>/dev/null
  record "$2: Telecom's newest call" "$(grep -E 'CallTC@[0-9]+ \[' "$D/telecom-$2.txt" | tail -1 | tr -d '\r')"
  record "$2: its end" "$(grep -E 'SET_DISCONNECTED|callTerminationReason' "$D/telecom-$2.txt" | tail -1 | tr -d '\r' | cut -c1-200)"
  end_call
  adb shell input keyevent KEYCODE_HOME; sleep 1
}
# A live call is ended with the END CALL key — only while Telecom holds one: with no call that key puts the screen to
# sleep (DEV-E12 run 2). Run 1 of this probe found `adb emu gsm list` empty even with the in-call screen up, so the
# modem's list is not what says a call is live here.
live_call() { S dumpsys telecom | grep -cE '^ +Call TC@[0-9]+: \{|mCallId|Call id'; }
end_call() {
  if [ "$(top_activity)" = "com.android.dialer/com.android.incallui.InCallActivity" ] || S dumpsys telecom | grep -q 'mForegroundCall: \[Call'; then
    adb shell input keyevent KEYCODE_ENDCALL; sleep 2
  fi
  record "after ending: top activity, wakefulness" "$(top_activity) $(S dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*')"
}
end_call
C=$(fx_raw); fx_name $C "Cal Joined"; fx_phone $C "+15550000001"
probe "$A" spaced
probe "$B" plain
probe "$C" joined
assert_eq "no call is left on the modem" "" "$(adb emu gsm list | tr -d '\r' | grep -v '^OK' | head -1)"
people_fixtures_down
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
adb shell am force-stop com.android.dialer
ensure_start
row_end
