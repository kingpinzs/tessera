#!/usr/bin/env bash
# E9 — Tess sets alarms and timers in the shell, unlocked and over the keyguard (Q1 A; re-cut of phase 03 E2's
# alarm/timer row, E10's locked alarm and timer, and P6). Through the audio route: `say alarm` / `say timer` land in
# the shell's own stores (Alarm tab, `dumpsys alarm` Next alarm clock for app.tileshell), with Tess's replies, NO chooser
# and no DeskClock activity or alarm at any point; over the keyguard both land in-process. A spoken step whose audio path
# fails `audio.sh check` is a FAIL marked NOT RUN with the check's output.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"
AUDIO="$QROOT/phase-03/scripts/audio.sh"
SPEAK="$QROOT/phase-03/scripts/speak.sh"
RINGS="launcher speech"
row_begin E9 "Tess sets alarms and timers in the shell, unlocked and locked"

deskclock_alarms() { adb shell dumpsys alarm | tr -d '\r' | grep -c com.android.deskclock; }
no_foreign() { # label
  local acts
  acts="$(adb shell dumpsys activity activities | tr -d '\r' | grep -E 'Hist #|topResumedActivity')"
  assert_absent "$1: no DeskClock activity" "com.android.deskclock" "$acts"
  assert_absent "$1: no chooser" "ChooserActivity" "$acts"
}
# spoken <utterance> -> 0 when spoken, 1 when the audio route is not up (the step is then a NOT RUN fail).
spoken() {
  local check crc
  check="$("$AUDIO" check 2>&1)"; crc=$?
  if [ "$crc" -ne 0 ]; then
    _verdict FAIL "$1 spoken" "NOT RUN: audio.sh check failed ($check)"
    return 1
  fi
  note "$1 final: $("$SPEAK" "$1" 11)"
  ring_since "$MARK" speech > "$ROW_DIR/ring_${1}_speech.txt"
  assert_absent "$1: the capture was heard (C-30)" "asr: no speech" "$(grep -F 'asr:' "$ROW_DIR/ring_${1}_speech.txt" | tail -1)"
}

# Tess speaks on USAGE_ASSISTANT, which follows the media volume on this AVD; the pass rule's RMS needs it audible
# (E26 run 3: media volume 0, every reply -115 dBFS). Raised for the spoken steps, put back as found at the end (RV12).
MEDIA_VOL0="$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
note "media volume before: ${MEDIA_VOL0:-?}"
adb shell cmd media_session volume --stream 3 --set 10 >/dev/null 2>&1
assert_clock_empty "baseline"
dk0="$(deskclock_alarms)"
note "DeskClock alarms before: $dk0"

# ---------------------------------------------------------------- unlocked
wake_device; ensure_start; cortana_assist; sleep 4
MARK="$(ring_mark)"
if spoken alarm; then
  ring_since "$MARK" > "$ROW_DIR/ring_alarm.txt"
  assert_eq "alarm: Tess's reply" "Alarm set for 7:20 AM." "$(reply_since "$MARK")"
  no_foreign "alarm"
  open_clock alarm
  dump_ui "$ROW_DIR/alarm_tab.xml"
  assert_contains "the Alarm tab lists 7:20 AM" "7:20" "$(grep -o 'resource-id="alarm_time:[^"]*"[^>]*' "$ROW_DIR/alarm_tab.xml"; grep -o 'text="[^"]*"[^>]*resource-id="alarm_time:[^"]*"' "$ROW_DIR/alarm_tab.xml")"
  adb shell dumpsys alarm | tr -d '\r' > "$ROW_DIR/dumpsys_alarm.txt"
  assert_contains "dumpsys alarm's Next alarm clock names app.tileshell" "app.tileshell" "$(grep -A3 -i 'next alarm clock' "$ROW_DIR/dumpsys_alarm.txt")"
fi
adb shell input keyevent KEYCODE_HOME; sleep 1; cortana_assist; sleep 4
MARK="$(ring_mark)"
if spoken timer; then
  ring_since "$MARK" > "$ROW_DIR/ring_timer.txt"
  assert_eq "timer: Tess's reply" "Timer set for 5 minutes." "$(reply_since "$MARK")"
  no_foreign "timer"
  open_clock timer
  gdump "$ROW_DIR/timer_tab.xml"
  assert_contains "the Timer tab shows a timer counting from 5:00" "4:5" "$(grep -o 'resource-id="timer_remaining:[^"]*"[^>]*' "$ROW_DIR/timer_tab.xml" | head -1; grep -o 'text="[^"]*"[^>]*resource-id="timer_remaining:[^"]*"' "$ROW_DIR/timer_tab.xml" | head -1)"
fi
assert_eq "DeskClock armed nothing" "$dk0" "$(deskclock_alarms)"
app_delete_all

# ---------------------------------------------------------------- over the keyguard (phase 03 E10's route)
set_pin
adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 2
assert_eq "the keyguard is showing" true "$(keyguard_showing)"
for u in alarm timer; do
  adb shell input keyevent KEYCODE_ASSIST; sleep 4
  MARK="$(ring_mark)"
  if spoken "$u"; then
    ring_since "$MARK" > "$ROW_DIR/ring_locked_$u.txt"
    assert_eq "locked $u: still behind the keyguard" true "$(keyguard_showing)"
    assert_absent "locked $u: in-process (no startVoiceActivity)" "startVoiceActivity" "$(cat "$ROW_DIR/ring_locked_$u.txt")"
    assert_absent "locked $u: no Unlock card" "cortana_card:unlock" "$(dump_ui "$ROW_DIR/locked_$u.xml"; cat "$ROW_DIR/locked_$u.xml")"
    no_foreign "locked $u"
  fi
  adb shell input keyevent KEYCODE_BACK; sleep 1
done
note "locked stores: alarms=[$(alarm_ids | tr '\n' ' ')] timers=[$(timer_ids | tr '\n' ' ')]"
lg="$(grep -A14 'fun allowedWhileLocked' "$REPO/app/src/main/kotlin/app/tileshell/cortana/action/LockGate.kt")"
assert_eq "LockGate.allowedWhileLocked lists SetAlarm as allowed" yes "$(printf '%s\n' "$lg" | grep -E 'Request\.SetAlarm[^>]*-> true' >/dev/null && echo yes || echo no)"
assert_eq "LockGate.allowedWhileLocked lists SetTimer as allowed" yes "$(printf '%s\n' "$lg" | grep -E 'Request\.SetTimer[^>]*-> true' >/dev/null && echo yes || echo no)"

# ---------------------------------------------------------------- restore
clear_pin; wake_device
app_delete_all
assert_clock_empty "restore"
[ -n "${MEDIA_VOL0:-}" ] && adb shell cmd media_session volume --stream 3 --set "$MEDIA_VOL0" >/dev/null 2>&1
row_end
