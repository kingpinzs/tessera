#!/usr/bin/env bash
# PHASE 10 BUILD TASK 9 — the sleep timer and the equaliser (E16: "Sleep timer stops playback at its
# time; the equaliser changes what is heard").
#
# Neither is asserted by looking at the app. The equaliser is asserted in the AUDIO SYSTEM: AudioFlinger
# lists the effect chain on the player's own audio session, next to the track that is actually
# sounding, and whether that effect is ENABLED — which is what "changes what is heard" comes down to on
# a device that cannot be listened to from here. The sleep timer is asserted by WAITING for it: a real
# 15-minute timer, with repeat on so the queue cannot run out first, and the pause measured against the
# moment it was armed. The end-of-track timer is waited for too.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"
source "$(dirname "$0")/music_lib.sh"

more_menu() { # more_menu <out.xml> — open the now-playing ••• and dump the band
  dump "$ROW_DIR/.np.xml"
  tap_id "$ROW_DIR/.np.xml" nowplaying_control:more; command sleep 2
  dump "$1"
}
pick() { # pick <menu.xml> <id> <out.xml>
  tap_id "$1" "$2"; command sleep 2
  dump "$3"
}
menu_text() { node_text "$1" "$2"; }   # a menu item is a Box whose text child carries the label
item_label() { python3 - "$1" "$2" <<'PY'
import re, sys
s = open(sys.argv[1], encoding='utf-8', errors='replace').read()
i = s.find('resource-id="' + sys.argv[2] + '"')
if i < 0: print(""); sys.exit()
m = re.search(r'text="([^"]+)"', s[i:i + 2000])
print(m.group(1) if m else "")
PY
}
eq_enabled() { # y / n for the Equalizer effect on audio session $1, as AudioFlinger reports it
  # The row under "Session State Registered Internal Enabled Suspended" — ENABLED is the fifth column.
  adb shell dumpsys media.audio_flinger 2>/dev/null | awk -v sess="$(printf '%05d' "$1")" '
    $1 == sess && NF >= 6 { print $5; exit }'
}
session_state_now() { adb shell dumpsys media_session > "$ROW_DIR/.session.txt" 2>&1; session_state "$ROW_DIR/.session.txt"; }

row_begin MUSIC9 "the equaliser in the audio path, and a sleep timer that actually stops the music"

music_mute
music_fixtures
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
music_open
AUDIO_SESSION="$(diag music | grep -o 'audio session [0-9]*' | tail -1 | awk '{print $3}')"
note "the player's audio session: ${AUDIO_SESSION:-none}"
assert_ne "the service reports the audio session it made" "" "$AUDIO_SESSION"
assert_contains "and that the device has an equaliser" "equaliser available" "$(diag music)"

goto_pivot songs "$ROW_DIR/songs.xml"
SONG=$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')
[ -n "$SONG" ] && tap_id "$ROW_DIR/songs.xml" "$SONG"
command sleep 4
dump "$ROW_DIR/np.xml"
assert_contains "a track is playing on the now-playing screen" "nowplaying_root" "$(cat "$ROW_DIR/np.xml")"

# ---- the equaliser is IN the audio path, on the track that is sounding ---------------------------
adb shell dumpsys media.audio_flinger > "$ROW_DIR/flinger_off.txt" 2>&1
assert_contains "AudioFlinger has an effect chain on the player's session" "effects for session $AUDIO_SESSION" "$(cat "$ROW_DIR/flinger_off.txt")"
assert_contains "and it is an Equalizer" "name: Equalizer" "$(cat "$ROW_DIR/flinger_off.txt")"
ACTIVE="$(grep -E "^ +[0-9]+ +yes +[0-9]+/ +[0-9]+ +$AUDIO_SESSION " "$ROW_DIR/flinger_off.txt" | head -1)"
note "the active track on that session: [$(echo "$ACTIVE" | tr -s ' ' | cut -c1-80)]"
assert_ne "the SAME session carries the track that is actually sounding" "" "$ACTIVE"
assert_eq "with the equaliser off, the effect is not enabled" n "$(eq_enabled "$AUDIO_SESSION")"

more_menu "$ROW_DIR/more.xml"
adb exec-out screencap -p > "$ROW_DIR/more.png"
assert_contains "the ••• opens a menu with the sleep timer" "music_menu_sleep" "$(cat "$ROW_DIR/more.xml")"
assert_contains "and the equaliser" "music_menu_eq" "$(cat "$ROW_DIR/more.xml")"
assert_eq "the equaliser entry says it is off" "Equaliser: off" "$(item_label "$ROW_DIR/more.xml" music_menu_eq)"

pick "$ROW_DIR/more.xml" music_menu_eq "$ROW_DIR/eq.xml"
adb exec-out screencap -p > "$ROW_DIR/eq.png"
PRESETS=$(grep -o 'resource-id="music_menu_eq:[0-9]*"' "$ROW_DIR/eq.xml" | wc -l | tr -d ' ')
note "presets the device offers: $PRESETS · $(grep -o 'text="[^"]*"' "$ROW_DIR/eq.xml" | sed 's/text="//; s/"$//' | tr '\n' '|')"
assert_eq "it lists the device's own presets" ok "$([ "$PRESETS" -ge 1 ] && echo ok || echo "$PRESETS")"
assert_contains "and Off" "music_menu_eq:off" "$(cat "$ROW_DIR/eq.xml")"
# The last preset, so the pick is not the index-0 default a broken store would also produce.
LAST=$((PRESETS - 1))
NAME="$(item_label "$ROW_DIR/eq.xml" "music_menu_eq:$LAST")"
note "choosing preset $LAST [$NAME]"
pick "$ROW_DIR/eq.xml" "music_menu_eq:$LAST" "$ROW_DIR/eq_on.xml"
adb shell dumpsys media.audio_flinger > "$ROW_DIR/flinger_on.txt" 2>&1
assert_eq "choosing a preset ENABLES the effect in the audio path" y "$(eq_enabled "$AUDIO_SESSION")"
assert_contains "the service says which preset it applied" "equaliser: $NAME (enabled=true)" "$(diag music)"
more_menu "$ROW_DIR/more_on.xml"
assert_eq "and the ••• menu now names it" "Equaliser: $NAME" "$(item_label "$ROW_DIR/more_on.xml" music_menu_eq)"
adb shell input keyevent KEYCODE_BACK; command sleep 1

# ---- it is remembered: kill everything, play again, and it is still on ------------------------------
adb shell am force-stop $PKG; command sleep 1
music_open
AUDIO_SESSION2="$(diag music | grep -o 'audio session [0-9]*' | tail -1 | awk '{print $3}')"
goto_pivot songs "$ROW_DIR/songs2.xml"
SONG=$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs2.xml" | head -1 | sed 's/resource-id="//; s/"$//')
[ -n "$SONG" ] && tap_id "$ROW_DIR/songs2.xml" "$SONG"
command sleep 4
note "after a force-stop the player's session is $AUDIO_SESSION2"
assert_eq "after a force-stop the preset is applied again from the start" y "$(eq_enabled "$AUDIO_SESSION2")"
assert_contains "and the service says it restored it" "equaliser: $NAME (enabled=true)" "$(diag music)"

more_menu "$ROW_DIR/more2.xml"
pick "$ROW_DIR/more2.xml" music_menu_eq "$ROW_DIR/eq2.xml"
pick "$ROW_DIR/eq2.xml" music_menu_eq:off "$ROW_DIR/eq_off.xml"
assert_eq "choosing Off disables it again" n "$(eq_enabled "$AUDIO_SESSION2")"

# ---- the sleep timer arms, says so, and disarms -------------------------------------------------------
more_menu "$ROW_DIR/sleep_root.xml"
assert_eq "with no timer the entry just says Sleep timer" "Sleep timer" "$(item_label "$ROW_DIR/sleep_root.xml" music_menu_sleep)"
pick "$ROW_DIR/sleep_root.xml" music_menu_sleep "$ROW_DIR/sleep.xml"
adb exec-out screencap -p > "$ROW_DIR/sleep.png"
for c in 15 30 45 60 eot; do
  assert_contains "the timer offers $c" "music_menu_sleep:$c" "$(cat "$ROW_DIR/sleep.xml")"
done
assert_absent "and offers no Off while nothing is armed" "music_menu_sleep:off" "$(cat "$ROW_DIR/sleep.xml")"
pick "$ROW_DIR/sleep.xml" music_menu_sleep:30 "$ROW_DIR/armed30.xml"
assert_contains "arming 30 minutes reaches the service" "armed for 30 minute(s)" "$(diag music)"
A30="$(diag music | grep -o 'armed for 30 minute(s) at elapsed [0-9]*, fires at elapsed [0-9]*' | tail -1)"
note "30-minute arm: $A30"
assert_eq "and its deadline is thirty minutes after it was armed" 1800000 \
  "$(echo "$A30" | sed -n 's/.* at elapsed \([0-9]*\), fires at elapsed \([0-9]*\).*/\2 - \1/p' | bc 2>/dev/null)"
more_menu "$ROW_DIR/armed_root.xml"
assert_eq "and the entry counts it down" "Sleep timer: 30 minutes left" "$(item_label "$ROW_DIR/armed_root.xml" music_menu_sleep)"
pick "$ROW_DIR/armed_root.xml" music_menu_sleep "$ROW_DIR/armed_list.xml"
assert_contains "an armed timer can be turned off" "music_menu_sleep:off" "$(cat "$ROW_DIR/armed_list.xml")"
pick "$ROW_DIR/armed_list.xml" music_menu_sleep:off "$ROW_DIR/disarmed.xml"
assert_contains "turning it off reaches the service" "sleep timer: off" "$(diag music)"

# ---- end of track: it pauses AT the end, on the same track ---------------------------------------------
more_menu "$ROW_DIR/eot_root.xml"
pick "$ROW_DIR/eot_root.xml" music_menu_sleep "$ROW_DIR/eot_list.xml"
pick "$ROW_DIR/eot_list.xml" music_menu_sleep:eot "$ROW_DIR/eot_armed.xml"
assert_contains "end-of-track reaches the service" "armed for the end of this track" "$(diag music)"
dump "$ROW_DIR/eot_np.xml"
TRACK_BEFORE="$(node_text "$ROW_DIR/eot_np.xml" nowplaying_track)"
# Seek to 80 % of the track so the end comes within the row's patience rather than in 90 seconds.
read -r SX1 SY1 SX2 SY2 <<< "$(bounds "$ROW_DIR/eot_np.xml" nowplaying_scrubber)"
TAPX=$(python3 -c "print(int($SX1 + 27 + 0.80 * ($SX2 - $SX1 - 54)))")
adb shell input tap "$TAPX" "$(( (SY1 + SY2) / 2 ))"
note "seeked to 80 % of [$TRACK_BEFORE]; waiting for the end"
STATE=""
for _ in $(seq 1 40); do
  STATE="$(session_state_now)"
  case "$STATE" in *"PAUSED(2)"*) break ;; esac
  command sleep 1
done
note "after the wait: $STATE"
assert_contains "the music pauses at the end of the track" "PAUSED(2)" "$STATE"
assert_contains "and it was the timer that paused it" "paused at the end of the track" "$(diag music)"
dump "$ROW_DIR/eot_done.xml"
adb exec-out screencap -p > "$ROW_DIR/eot_done.png"
POS="$(echo "$STATE" | sed -n 's/.*position=\([0-9]*\).*/\1/p')"
note "paused at position ${POS} ms; track [$(node_text "$ROW_DIR/eot_done.xml" nowplaying_track)]"
assert_eq "at the END of the track, not part-way through it" ok "$([ -n "$POS" ] && [ "$POS" -ge 85000 ] && echo ok || echo "position=$POS")"
assert_eq "and on the same track, not a moment into the next one" "$TRACK_BEFORE" "$(node_text "$ROW_DIR/eot_done.xml" nowplaying_track)"
more_menu "$ROW_DIR/eot_after.xml"
assert_eq "a fired timer is disarmed, not left showing" "Sleep timer" "$(item_label "$ROW_DIR/eot_after.xml" music_menu_sleep)"
adb shell input keyevent KEYCODE_BACK; command sleep 1

# ---- a real 15-minute timer, waited for ----------------------------------------------------------------
# Repeat ALL on first, so a nine-minute queue cannot run out and stop the music before the timer does —
# which would make this pass against a timer that never fires.
dump "$ROW_DIR/pre15.xml"
tap_id "$ROW_DIR/pre15.xml" nowplaying_control:play_pause; command sleep 2
tap_id "$ROW_DIR/pre15.xml" nowplaying_control:repeat; command sleep 1
assert_contains "repeat all is on, so the queue cannot end first" "repeat all" "$(diag music)"
assert_contains "and the music is playing again" "PLAYING(3)" "$(session_state_now)"
more_menu "$ROW_DIR/t15_root.xml"
pick "$ROW_DIR/t15_root.xml" music_menu_sleep "$ROW_DIR/t15_list.xml"
pick "$ROW_DIR/t15_list.xml" music_menu_sleep:15 "$ROW_DIR/t15_armed.xml"
# The service names both moments: when it was armed, and when it will fire. Run 2 of this row took the
# second for the first and reported a 2-ms-late timer as 15 minutes early.
ARM_LINE="$(diag music | grep -o 'armed for 15 minute(s) at elapsed [0-9]*, fires at elapsed [0-9]*' | tail -1)"
ARMED_AT="$(echo "$ARM_LINE" | sed -n 's/.* at elapsed \([0-9]*\), fires.*/\1/p')"
DEADLINE="$(echo "$ARM_LINE" | sed -n 's/.*fires at elapsed \([0-9]*\).*/\1/p')"
note "armed for 15 minutes at elapsed ${ARMED_AT}, due at ${DEADLINE}; waiting"
assert_ne "the 15-minute timer armed" "" "$ARMED_AT"
assert_eq "due exactly fifteen minutes after it was armed" 900000 "$(( ${DEADLINE:-0} - ${ARMED_AT:-0} ))"
command sleep 600
MID="$(session_state_now)"
note "ten minutes in: $MID"
assert_contains "ten minutes in, it is still playing — the queue did not run out and the timer did not fire early" "PLAYING(3)" "$MID"
command sleep 330
FIRED="$(diag music | grep -o 'paused (timer reached) at elapsed [0-9]*' | tail -1 | awk '{print $NF}')"
AFTER="$(session_state_now)"
note "fired at elapsed ${FIRED}; the session: $AFTER"
assert_contains "after fifteen minutes the music has stopped" "PAUSED(2)" "$AFTER"
assert_ne "and the timer is what stopped it" "" "$FIRED"
if [ -n "$FIRED" ] && [ -n "$ARMED_AT" ]; then
  LATE=$((FIRED - ARMED_AT - 900000))
  note "it fired $((FIRED - ARMED_AT)) ms after it was armed (${LATE} ms past 15:00)"
  assert_eq "at its time: within five seconds of fifteen minutes, and not before" ok \
    "$([ "$LATE" -ge 0 ] && [ "$LATE" -le 5000 ] && echo ok || echo "${LATE} ms off")"
fi

row_end
