#!/usr/bin/env bash
# PHASE 10, E17 — "Gapless / crossfade behaves as set between two tracks."
#
# Asserted in the AUDIO SYSTEM, not on the screen. AudioFlinger lists every AudioTrack that is sounding,
# with the process and the audio session it belongs to. With crossfade off, a transition is gapless and
# there is never more than one track of ours; with it on, for the length of the fade there are TWO, both on
# the player's own audio session (so the equaliser covers both), and then one again. The row can fail
# either way: a crossfade that never overlaps, or an "off" that overlaps, both show up in the counts.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"
source "$(dirname "$0")/music_lib.sh"

more_menu() { dump "$ROW_DIR/.np.xml"; tap_id "$ROW_DIR/.np.xml" nowplaying_control:more; command sleep 2; dump "$1"; }
pick() { tap_id "$1" "$2"; command sleep 2; dump "$3"; }
item_label() { python3 - "$1" "$2" <<'PY'
import re, sys
s = open(sys.argv[1], encoding='utf-8', errors='replace').read()
i = s.find('resource-id="' + sys.argv[2] + '"')
if i < 0: print(""); sys.exit()
m = re.search(r'text="([^"]+)"', s[i:i + 2000])
print(m.group(1) if m else "")
PY
}
set_crossfade() { # set_crossfade <off|2|5|8|12>
  more_menu "$ROW_DIR/.more.xml"
  pick "$ROW_DIR/.more.xml" music_menu_crossfade "$ROW_DIR/.xf.xml"
  pick "$ROW_DIR/.xf.xml" "music_menu_crossfade:$1" "$ROW_DIR/.after.xml"
}
# SOUNDING AudioTracks of OUR process: "<count> <session> <session> ...". A track row reads
# [Type] Id Active Client(pid/uid) Session PortId S Flags ... — the Type column is blank for ordinary
# tracks, and S is the track's state. Only state A (playing) counts: run 1 counted every track the mixer
# lists as active, and a plain seek makes ExoPlayer replace its AudioTrack — the old one sits there in
# state P (paused) while it is torn down, which read as "two tracks" with crossfade OFF.
ours() {
  adb shell dumpsys media.audio_flinger 2>/dev/null | awk -v pid="$PID/" '
    { for (i = 1; i <= 3; i++) if ($i == "yes" && $(i+1) == pid && $(i+5) == "A") { n++; s = s " " $(i+3) } }
    END { print n + 0 s }'
}
state_now() { adb shell dumpsys media_session > "$ROW_DIR/.s.txt" 2>&1; session_state "$ROW_DIR/.s.txt" | sed -n 's/.*state=\([A-Z]*\).*/\1/p'; }
seek_to() { # seek_to <fraction>
  dump "$ROW_DIR/.seek.xml"
  read -r SX1 SY1 SX2 SY2 <<< "$(bounds "$ROW_DIR/.seek.xml" nowplaying_scrubber)"
  adb shell input tap "$(python3 -c "print(int($SX1 + 27 + $1 * ($SX2 - $SX1 - 54)))")" "$(( (SY1 + SY2) / 2 ))"
}
title_now() { dump "$ROW_DIR/.t.xml"; node_text "$ROW_DIR/.t.xml" nowplaying_track; }
# After the row's OWN seek the session reports BUFFERING for a moment, which is the row's doing, not the
# crossfade's: wait for PLAYING before judging anything (run 1 counted that sample against the handback).
settle() {
  for _ in $(seq 1 30); do
    [ "$(state_now)" = PLAYING ] && break
    command sleep 0.1
  done
}
sample() { # sample <seconds> <file> — one line per sample: "<count> <sessions> | <session state>"
  : > "$2"
  local end=$(( $(date +%s) + $1 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    echo "$(ours) | $(state_now)" >> "$2"
  done
}

row_begin MUSIC17 "crossfade: off is one track across a transition; on is two, on one session, then one again"

music_mute
music_fixtures
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
music_open
PID="$(adb shell pidof $PKG | tr -d '\r')"
AUDIO_SESSION="$(diag music | grep -o 'audio session [0-9]*' | tail -1 | awk '{print $3}')"
note "shell pid $PID, the player's audio session $AUDIO_SESSION"
assert_ne "the player's audio session is known" "" "$AUDIO_SESSION"
goto_pivot songs "$ROW_DIR/songs.xml"
SONG=$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')
[ -n "$SONG" ] && tap_id "$ROW_DIR/songs.xml" "$SONG"
command sleep 4

# ---- off: gapless, never two tracks -------------------------------------------------------------------
set_crossfade off
more_menu "$ROW_DIR/off_menu.xml"
assert_eq "the ••• menu says crossfade is off" "Crossfade: off" "$(item_label "$ROW_DIR/off_menu.xml" music_menu_crossfade)"
adb shell input keyevent KEYCODE_BACK; command sleep 1
T0="$(title_now)"
seek_to 0.94
settle
sample 10 "$ROW_DIR/off_samples.txt"
T1="$(title_now)"
MAX_OFF=$(awk '{ if ($1 > m) m = $1 } END { print m + 0 }' "$ROW_DIR/off_samples.txt")
NOT_PLAYING_OFF=$(grep -vc '| PLAYING$' "$ROW_DIR/off_samples.txt")
note "off: $(wc -l < "$ROW_DIR/off_samples.txt") samples, most tracks of ours at once $MAX_OFF, samples not PLAYING $NOT_PLAYING_OFF; [$T0] -> [$T1]"
assert_eq "with crossfade off there is never more than one track of ours" ok "$([ "$MAX_OFF" -le 1 ] && echo ok || echo "$MAX_OFF")"
assert_ne "and the next track did arrive" "$T0" "$T1"
assert_eq "without the session ever leaving PLAYING" 0 "$NOT_PLAYING_OFF"

# ---- on: two tracks on one session for the length of the fade -------------------------------------------
set_crossfade 5
assert_contains "setting 5 seconds reaches the service" "crossfade: 5000 ms (set)" "$(diag music)"
more_menu "$ROW_DIR/on_menu.xml"
assert_eq "and the ••• menu says so" "Crossfade: 5 seconds" "$(item_label "$ROW_DIR/on_menu.xml" music_menu_crossfade)"
adb shell input keyevent KEYCODE_BACK; command sleep 1
T2="$(title_now)"
seek_to 0.86
settle
sample 16 "$ROW_DIR/on_samples.txt"
adb shell dumpsys media.audio_flinger > "$ROW_DIR/flinger_after.txt" 2>&1
T3="$(title_now)"
TWO=$(awk '$1 == 2' "$ROW_DIR/on_samples.txt" | wc -l | tr -d ' ')
TWO_SAME=$(awk -v s="$AUDIO_SESSION" '$1 == 2 && $2 == s && $3 == s' "$ROW_DIR/on_samples.txt" | wc -l | tr -d ' ')
LAST=$(grep -v '^$' "$ROW_DIR/on_samples.txt" | tail -1 | awk '{print $1}')
NOT_PLAYING_ON=$(grep -vc '| PLAYING$' "$ROW_DIR/on_samples.txt")
note "on: $(wc -l < "$ROW_DIR/on_samples.txt") samples; with two tracks of ours: $TWO (both on session $AUDIO_SESSION: $TWO_SAME); last sample $LAST; not PLAYING: $NOT_PLAYING_ON; [$T2] -> [$T3]"
assert_eq "with crossfade on, the two tracks overlap" ok "$([ "$TWO" -ge 1 ] && echo ok || echo "no sample had two")"
assert_eq "both on the player's own audio session, every time" "$TWO" "$TWO_SAME"
assert_contains "the equaliser's effect chain is on that session" "effects for session $AUDIO_SESSION" "$(cat "$ROW_DIR/flinger_after.txt")"
assert_eq "and afterwards there is one track again, not a fader left behind" 1 "$LAST"
assert_eq "the session never left PLAYING through the fade and the handback" 0 "$NOT_PLAYING_ON"
assert_ne "and the next track arrived" "$T2" "$T3"
RAN="$(diag music | grep -o 'fade ran [0-9]* ms' | tail -1 | awk '{print $3}')"
HANDED="$(diag music | grep -o 'handed back at elapsed [0-9]*, drift -\{0,1\}[0-9]* ms after [0-9]* seek(s)' | tail -1)"
DRIFT="$(echo "$HANDED" | sed -n 's/.*drift \(-\{0,1\}[0-9]*\) ms.*/\1/p')"
note "the fade ran ${RAN} ms; the handback: [$HANDED]"
assert_within "the fade lasted the five seconds it was set to" 5000 "${RAN:-0}" 400
assert_ne "the incoming track was handed back to the session's player" "" "$HANDED"
assert_eq "with the two copies within 40 ms of each other when they swapped" ok \
  "$([ -n "$DRIFT" ] && [ "${DRIFT#-}" -le 40 ] && echo ok || echo "drift=${DRIFT:-none}")"

# ---- pausing mid-fade stops both, and resuming brings back one ---------------------------------------------
seek_to 0.90
for _ in $(seq 1 40); do
  diag music | tail -3 | grep -q 'fade started' && break
  command sleep 0.25
done
dump "$ROW_DIR/midfade.xml"
tap_id "$ROW_DIR/midfade.xml" nowplaying_control:play_pause
command sleep 1.5
P1="$(ours)"; S1="$(state_now)"
note "paused mid-fade: ours [$P1], session $S1"
assert_eq "pausing mid-fade silences both players" 0 "$(echo "$P1" | awk '{print $1}')"
assert_eq "and the session is paused" PAUSED "$S1"
assert_contains "because the fade was aborted, not left running" "aborted in fading (paused)" "$(diag music)"
dump "$ROW_DIR/resume.xml"
tap_id "$ROW_DIR/resume.xml" nowplaying_control:play_pause
command sleep 1.5
assert_eq "resuming brings back exactly one track" 1 "$(ours | awk '{print $1}')"

# ---- an armed end-of-track sleep timer means no fade ---------------------------------------------------------
more_menu "$ROW_DIR/eot_root.xml"
pick "$ROW_DIR/eot_root.xml" music_menu_sleep "$ROW_DIR/eot_list.xml"
pick "$ROW_DIR/eot_list.xml" music_menu_sleep:eot "$ROW_DIR/eot_armed.xml"
seek_to 0.90
settle
sample 12 "$ROW_DIR/eot_samples.txt"
MAX_EOT=$(awk '{ if ($1 > m) m = $1 } END { print m + 0 }' "$ROW_DIR/eot_samples.txt")
note "end-of-track armed: most tracks of ours at once $MAX_EOT; last state $(tail -1 "$ROW_DIR/eot_samples.txt")"
assert_eq "with the end-of-track sleep timer armed, nothing fades in" ok "$([ "$MAX_EOT" -le 1 ] && echo ok || echo "$MAX_EOT")"
assert_contains "and the service says why" "end-of-track sleep armed" "$(diag music)"
assert_contains "and the music pauses at the end, as the timer promised" "PAUSED" "$(tail -1 "$ROW_DIR/eot_samples.txt")"

# ---- it is remembered ---------------------------------------------------------------------------------------
music_open
assert_contains "after a force-stop the service restores 5 seconds" "crossfade: 5000 ms (restored)" "$(diag music)"
goto_pivot songs "$ROW_DIR/songs2.xml"
SONG=$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs2.xml" | head -1 | sed 's/resource-id="//; s/"$//')
[ -n "$SONG" ] && tap_id "$ROW_DIR/songs2.xml" "$SONG"
command sleep 4
more_menu "$ROW_DIR/restored_menu.xml"
assert_eq "and the ••• menu shows it" "Crossfade: 5 seconds" "$(item_label "$ROW_DIR/restored_menu.xml" music_menu_crossfade)"
adb shell input keyevent KEYCODE_BACK; command sleep 1
# Leave the device as the other rows expect it: gapless.
set_crossfade off
assert_contains "and it can be put back to off" "crossfade: 0 ms (set)" "$(diag music)"

row_end
