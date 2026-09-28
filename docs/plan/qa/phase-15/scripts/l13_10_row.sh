#!/usr/bin/env bash
# L13-10 regression row (INDEX ledger; phase 15 Edge cases: "Alarm firing while Tess is listening (the session hides)";
# qa/phase-15/EDGE_ALARM_CONTEXT-run3/DEFECT.md §1; investigation review/2026-09-27-alarm-under-tess-investigation.md).
# Every ring surface sits below Tess's voice-interaction window, so the toast was drawn UNDER her and its Dismiss could
# not be reached. The fix: the session hides itself when a new ring starts. Two cases, each an alarm 2 min ahead through
# the AlarmClock API and the clock jumped to it (EDGE_ALARM_CONTEXT's method, git da848a06, section 1):
#   (1) Tess LISTENING when it fires — the mic tapped by a device-side loop 1 s before; the precondition (listen start <
#       fired < listen end) asserted, a void attempt retried (up to three);
#   (2) Tess OPEN BUT IDLE when it fires.
# Each asserts: the session hides within 1000 ms of the fired line (and says why), no cortana_session in the all-windows
# dump, the ring's Dismiss on screen, an ALARM player started; (1) also: the listen is stopped and Tess says nothing after.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin L13_10 "an alarm firing while Tess is open hides her, listening or idle; the ring's Dismiss is reachable"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring
wall_of() { printf '%s\n' "$1" | grep -oE 'wall=[0-9]+' | head -1 | cut -d= -f2; }
# An alarm 2 min ahead (the AlarmClock API, the brief's seeding route); sets ID and AT.
seed() { # name
  local now h m
  now="$(device_ms)"; read -r h m <<< "$(device_hm $(( now + 120000 )))"
  ID="$(api_alarm "$h" "$m" "$1")"
  AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"
  assert_ne "$1: the alarm was created and armed" "" "$ID"
}
# The toast's Dismiss (the gesture driver's dump, C-10); a ring left up is ended by dismiss_any_ring and the log says so.
end_ring() { # label
  local d="$ROW_DIR/${1}_ring.xml" m
  gdump_for "$d" ring_dismiss; screencap "$ROW_DIR/${1}_ring.png"
  m="$(ring_mark)"
  [ "$(has_node "$d" ring_dismiss)" = yes ] && gtap "$d" ring_dismiss
  sleep 2.5
  if [ -z "$(ring_since "$m" | grep -F 'ring ended')" ]; then
    note "end_ring $1: no ring ended line after the Dismiss tap; the ring is ended by dismiss_any_ring"
    dismiss_any_ring
  fi
}
section_clear() { # label
  app_delete_all
  assert_eq "$1: the section left no alarm behind" "" "$(alarm_ids | paste -sd,)"
}
section_restore() { # label
  ring_save launcher
  clock_restore
  section_clear "$1"
}


hidden_within() { # fired_wall launcher_slice -> "yes" or "no (...)"
  local hid; hid="$(grep -F '[cortana] session hidden' "$2" | head -1)"
  if [ -n "$hid" ] && [ "$(wall_of "$hid")" -ge "$1" ] && [ "$(( $(wall_of "$hid") - $1 ))" -le 1000 ]; then echo yes
  else echo "no (${hid:-no hidden line})"; fi
}
after_ring() { # label fired_wall — the checks both cases share
  local l="$1" fw="$2"
  gdump_windows "$ROW_DIR/${l}_ring.xml"; screencap "$ROW_DIR/${l}_ring.png"
  note "$l: windows at the ring: $(gwindows "$ROW_DIR/${l}_ring.xml")"
  assert_eq "$l: the session hid within 1000 ms of the fired line" yes "$(hidden_within "$fw" "$ROW_DIR/ring_${l}_launcher.txt")"
  assert_contains "$l: ... because a ring started" "a ring started" "$(grep -F '[cortana]' "$ROW_DIR/ring_${l}_launcher.txt")"
  assert_eq "$l: no cortana_session node in the all-windows dump" no "$(has_node "$ROW_DIR/${l}_ring.xml" cortana_session)"
  assert_eq "$l: the ring's Dismiss is on screen" yes "$(has_node "$ROW_DIR/${l}_ring.xml" ring_dismiss)"
  assert_ne "$l: the alarm rings (an ALARM player of the shell is started)" 0 "$(alarm_player_started)"
  record "$l: the ring's surface line" "$(grep -oE '\[alarms\] surface: [a-z-]+ [^ ]+' "$ROW_DIR/ring_${l}_launcher.txt" | head -1)"
  end_ring "$l"
  dump_ui "$ROW_DIR/${l}_after.xml"
  [ "$(has_node "$ROW_DIR/${l}_after.xml" cortana_session)" = yes ] && { note "$l: the session was still up after the ring; closed with Home"; adb shell input keyevent KEYCODE_HOME; sleep 2; }
}

# ================================================================== (1) Tess listening ==================================================
TESS_OK=no
for attempt in 1 2 3; do
  seed "Tess"
  adb shell input keyevent KEYCODE_HOME; sleep 1.5
  cortana_assist; sleep 4
  dump_ui "$ROW_DIR/listen_open_$attempt.xml"
  MB="$(bounds "$ROW_DIR/listen_open_$attempt.xml" cortana_text_box_mic)"
  note "attempt $attempt: session open=$(has_node "$ROW_DIR/listen_open_$attempt.xml" cortana_session), mic button [$MB]"
  [ -n "$MB" ] || { note "attempt $attempt: no mic button"; cortana_close; adb shell input keyevent KEYCODE_HOME; section_restore "listen attempt $attempt"; continue; }
  read -r x1 y1 x2 y2 <<< "$MB"
  jump_clock $(( AT - 8000 )) >/dev/null
  MARK="$(ring_mark)"
  adb shell "while [ \$(date +%s%3N) -lt $(( AT - 1000 )) ]; do :; done; input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))" >/dev/null 2>&1
  FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 20)"
  sleep 3
  ring_since "$MARK" speech > "$ROW_DIR/ring_listen_speech.txt"
  ring_since "$MARK" launcher > "$ROW_DIR/ring_listen_launcher.txt"
  # The listen's window comes from Tess's own (launcher) ring: "[speech] final ... audioMs=N" at wall T means it ran from
  # T - N to T. The :speech ring is read only through its bound service, and a hidden session unbinds it, so on a
  # fixed build that ring is empty by the time it is read (L13_10-592a3efd, first run); it is kept as a record.
  LFINAL="$(grep -F '[speech] final open=' "$ROW_DIR/ring_listen_launcher.txt" | head -1)"
  AMS="$(printf '%s\n' "$LFINAL" | grep -oE 'audioMs=[0-9]+' | cut -d= -f2)"
  UNB="$(grep -F '[speech] unbound from :speech' "$ROW_DIR/ring_listen_launcher.txt" | head -1)"
  FW="$(wall_of "$FIRED")"; EW="$(wall_of "$LFINAL")"; SW="$([ -n "$EW" ] && [ -n "$AMS" ] && echo $(( EW - AMS )))"; UW="$(wall_of "$UNB")"
  note "attempt $attempt: listen start wall=${SW:-none} (final - audioMs), fired wall=${FW:-none}, listen end wall=${EW:-none}, unbound wall=${UW:-none}; :speech ring lines: $(wc -l < "$ROW_DIR/ring_listen_speech.txt")"
  if [ -n "$FW" ] && [ -n "$SW" ] && [ "$SW" -lt "$FW" ] && [ "$EW" -gt "$FW" ]; then
    TESS_OK=yes; break
  fi
  record "listen attempt $attempt void" "the alarm did not fire inside the listen (start ${SW:-none}, fired ${FW:-none}, end ${END:-none}); re-seeded"
  end_ring "listen_void_$attempt"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  section_restore "listen attempt $attempt"
done
assert_eq "(1): the alarm fired while Tess was listening (listen start < fired < listen end)" yes "$TESS_OK"
if [ "$TESS_OK" = yes ]; then
  # Hiding stops the listen and releases the speech service: the listen ends within 1000 ms of the fire and Tess unbinds
  # from :speech at or after it (the old build let the endpoint end it, about 2 s later, and stayed bound).
  assert_eq "(1): the listen ended within 1000 ms of the fire" yes "$([ $(( EW - FW )) -le 1000 ] && echo yes || echo "no (ended $(( EW - FW )) ms after)")"
  assert_eq "(1): Tess released the speech service at or after the fire" yes "$([ -n "$UW" ] && [ "$UW" -ge "$FW" ] && echo yes || echo "no (unbound ${UW:-never})")"
  # SpeechClient logs speak[...] in Tess's own (the launcher's) process; both rings are read.
  assert_absent "(1): Tess says nothing after the fire (no speak line after it, launcher or :speech ring)" "[speech] speak[" "$(cat "$ROW_DIR/ring_listen_launcher.txt" "$ROW_DIR/ring_listen_speech.txt" | awk -v fw="$FW" 'match($0, /wall=[0-9]+/) { if (substr($0, RSTART + 5, RLENGTH - 5) + 0 >= fw) print }')"
  after_ring listen "$FW"
fi
adb shell input keyevent KEYCODE_HOME; sleep 1
section_restore "listen"

# ================================================================== (2) Tess open, idle =================================================
seed "Idle"
adb shell input keyevent KEYCODE_HOME; sleep 1.5
cortana_assist; sleep 4
dump_ui "$ROW_DIR/idle_open.xml"
assert_eq "(2): Tess is open" yes "$(has_node "$ROW_DIR/idle_open.xml" cortana_session)"
jump_clock $(( AT - 5000 )) >/dev/null
MARK="$(ring_mark)"
FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 20)"
sleep 3
ring_since "$MARK" launcher > "$ROW_DIR/ring_idle_launcher.txt"
FW="$(wall_of "$FIRED")"
assert_ne "(2): the alarm fired" "" "$FW"
[ -n "$FW" ] && after_ring idle "$FW"
adb shell input keyevent KEYCODE_HOME; sleep 1
section_restore "idle"

# ================================================================== (3) the same session, shown again =====================================
# Review round 1 (B N5): the watch is started on every show and stopped on every hide, and the session object outlives a
# hide. Tess opened, closed with Back (her own hide, for its own reason), opened again; then an alarm: she hides again.
seed "Reuse"
adb shell input keyevent KEYCODE_HOME; sleep 1.5
MARK="$(ring_mark)"
cortana_assist; sleep 3
cortana_close; sleep 1
cortana_assist; sleep 3
dump_ui "$ROW_DIR/reuse_open.xml"
assert_eq "(3): Tess is open again" yes "$(has_node "$ROW_DIR/reuse_open.xml" cortana_session)"
ring_since "$MARK" launcher > "$ROW_DIR/ring_reuse_before.txt"
assert_eq "(3): the first hide was Back's, not a ring's" "1 0" "$(grep -c '\[cortana\] session hidden' "$ROW_DIR/ring_reuse_before.txt") $(grep -c 'a ring started' "$ROW_DIR/ring_reuse_before.txt")"
jump_clock $(( AT - 5000 )) >/dev/null
MARK="$(ring_mark)"
FIRED="$(wait_ring "$MARK" "[alarms] fired $ID kind=alarm" 20)"
sleep 3
ring_since "$MARK" launcher > "$ROW_DIR/ring_reuse_launcher.txt"
FW="$(wall_of "$FIRED")"
assert_ne "(3): the alarm fired" "" "$FW"
[ -n "$FW" ] && after_ring reuse "$FW"
adb shell input keyevent KEYCODE_HOME; sleep 1
section_restore "reuse"

row_end
