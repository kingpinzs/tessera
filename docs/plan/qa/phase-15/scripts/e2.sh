#!/usr/bin/env bash
# E2 — exported components (T15-24): the APK's whole exported surface equals phase 03's allow-list after this phase's
# ADDs — the three launcher activities and the AlarmClock handler (guarded by SET_ALARM) — plus phase 10's two owed
# lines; the ring activity, ring service, recorder service and the receivers are NOT exported. Phase 03's E5 checker.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
row_begin E2 "exported surface = the allow-list; ring and recorder components not exported"

python3 "$QROOT/phase-03/scripts/exported.py" "$APK" "$QROOT/phase-03/exported-allowlist.txt" > "$ROW_DIR/exported.txt" 2>&1
echo $? > "$ROW_DIR/exported.rc"
assert_eq "exported.py: the APK's exported set equals the allow-list exactly" 0 "$(cat "$ROW_DIR/exported.rc")"
note "$(head -1 "$ROW_DIR/exported.txt")"

for c in app.tileshell.clock.ClockActivity app.tileshell.calculator.CalculatorActivity \
         app.tileshell.recorder.RecorderActivity app.tileshell.clock.AlarmApiActivity; do
  assert_contains "$c is exported and listed" "$c" "$(grep -A2 " $c\$" "$ROW_DIR/exported.txt")"
done
assert_contains "the AlarmClock handler is guarded by SET_ALARM" "manifest permission: com.android.alarm.permission.SET_ALARM" \
  "$(grep -A1 ' app.tileshell.clock.AlarmApiActivity$' "$ROW_DIR/exported.txt")"
for c in app.tileshell.clock.RingActivity app.tileshell.clock.RingService app.tileshell.recorder.RecorderService \
         app.tileshell.cortana.reminders.ReminderReceiver app.tileshell.cortana.speech.SpeechService; do
  assert_absent "$c is not exported" " $c" "$(sed -n '/exported in/,/^$/p' "$ROW_DIR/exported.txt")"
done

# The device agrees with the APK: dumpsys package lists the handler's permission and the ring activity unexported.
adb shell dumpsys package app.tileshell > "$ROW_DIR/dumpsys-package.txt"
assert_contains "installed APK matches the built one" "yes (" "$(apk_matches)"
row_end
