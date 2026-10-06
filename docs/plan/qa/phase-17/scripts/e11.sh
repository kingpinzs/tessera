#!/usr/bin/env bash
# Phase 17 E11 — My videos and the player. The doc's clauses → this driver's legs:
#
#   tile      qa-steps.mp4 on the My videos page (hub_pane:myvideos selected) as video_tile:<id>; its caption node reads
#             "qa-steps" and carries no duration (Y5; T17-16)
#   play      a MARK, a tap on the tile → app.tileshell/.video.PlayerActivity on top (top_activity; r3 D6)
#   3.5 s     a screencap 3.5 s after the `[video] playing` line: the centre pixel = colour 3 under the pixel rule as
#             RULED FOR THE EMULATOR (INDEX Change Log 2026-10-06): nearest of the ten colours AND within ± 20 per
#             channel; the doc's ± 8 (r3 V9) is kept as a record line (p17_video.sh, assert_pixel_rule)
#   routing   while it plays, the launcher's slice from the MARK holds `[music] session app.tileshell id=video -> none`
#             and no `id=video -> cmp:` (absent_in) (r3 V5, D16)
#   session   `dumpsys media_session` shows an active session of app.tileshell — the video's
#   seek      a tap on the scrubber at 70 % of the MEASURED track (x 12 → 348 epx, so 247.2 epx = 741.6 px) seeks to
#             colour 7 ± 1 s (the picture, and the elapsed label 0:00:06 … 0:00:08)
#   pause     pause holds the pixel for 2 s
#   fade      the controls fade after ≈3.2 s, fading out over 367–400 ms (`[motion] controls_fade …`; Y6, r3 D5), each
#             line's maxGapMs ≤ 33.4 ms (C-31)
#   colours   RECORDED, for the lead's ruling on the ± 8: the centre pixel at k.5 s for every k of the fixture, from a
#             second play (make_videos.sh's colours; the brief's "measure with the fixture")
#
# The player is opened from the My videos tile, as the row words it (the shell's own launch).
# Changes on the device, all restored: one video pushed (media_down), the device's media volume, the Videos grant when
# it was missing. No root. Sound: the fixture's own tone at device volume 0; no microphone.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"

video_row_begin E11 "My videos and the player: tile, play, the pixel at 3.5 s, seek, pause, fade, session, routing"
quiet_on
videos_grant
media_up qa-steps.mp4
ID="$(media_id video qa-steps.mp4 Movies/)"
assert_ne "qa-steps.mp4 is in MediaStore (Movies/)" "" "$ID"
adb shell am force-stop app.tileshell; sleep 1
ensure_start

# ------------------------------------------------------------------------------------------------ the tile
log "--- the tile on My videos"
MARK0="$(ring_mark)"
hub myvideos 2.5
assert_contains "the :video slice holds [video] library: <the census + this row's one fixture>" "[video] library: $((CENSUS_VIDEO + 1))" "$(vring "$MARK0")"
assert_eq "the pane's current row is hub_pane:myvideos (selected)" "myvideos" "$(pane_current "$D/pane")"
scroll_to_node "$D/mine.xml" "video_tile:$ID" 12
assert_eq "the My videos page is the one read" "yes" "$(has_node "$D/mine.xml" hub_page:myvideos)"
assert_eq "qa-steps.mp4 is shown as video_tile:$ID" "yes" "$(has_node "$D/mine.xml" "video_tile:$ID")"
assert_eq "its caption node reads the file name without extension" "qa-steps" "$(node_text "$D/mine.xml" "video_caption:$ID")"
assert_eq "no text of the page is a duration (m:ss / h:mm:ss) — the caption carries none" "0" \
  "$(grep -o 'text="[^"]*"' "$D/mine.xml" | grep -cE 'text="[0-9]+:[0-9]{2}(:[0-9]{2})?"')"

# ------------------------------------------------------------------------------------------------ play, 3.5 s
log "--- a MARK, a tap on the tile"
MARK="$(ring_mark)"
tap_node "$D/mine.xml" "video_tile:$ID"
LINE="$(await_vline "$MARK" "[video] playing $ID" 100)"
assert_contains "the :video slice holds [video] playing $ID" "[video] playing $ID" "$LINE"
T0="$(wall_of "$LINE")"
shot_at "${T0:-0}" 3500 "$D/at-3.5s.png"
record "the screencap for t = 3.5 s returned (ms after the [video] playing line)" "$SHOT_DONE"
assert_eq "it plays in app.tileshell/.video.PlayerActivity (top_activity)" "$PLAYER_ACTIVITY" "$(top_activity)"
assert_pixel_rule "t = 3.5 s" 3 "$SHOT_RGB"
assert_contains "the :video slice holds [video] playing scheme=content" "[video] playing scheme=content" "$(vring "$MARK")"

# ------------------------------------------------------------------------------------------------ routing, session
LL="$(await_lline "$MARK" "[music] session app.tileshell id=video" 60)"
LSLICE="$(ring_since "$MARK" launcher)"
assert_contains "while it plays: [music] session app.tileshell id=video -> none (launcher slice from the MARK)" "[music] session app.tileshell id=video -> none" "$LSLICE"
absent_in "while it plays: no id=video -> cmp: line" "id=video -> cmp:" "$LSLICE"
adb shell dumpsys media_session | tr -d '\r' > "$D/media_session.txt"
assert_eq "dumpsys media_session: the shell's video session is PLAYING" "PLAYING" "$(session_state .id.video)"
assert_contains "… and it is an active session of app.tileshell" "active=true" \
  "$(grep -A12 -E '^\s+\S+ app\.tileshell/\S*\.id\.video/[0-9]+ \(userId' "$D/media_session.txt" | grep -m1 -o 'active=[a-z]*')"
record "the session's header line" "$(grep -m1 -E '^\s+\S+ app\.tileshell/\S*\.id\.video/[0-9]+ \(userId' "$D/media_session.txt" | xargs)"

# ------------------------------------------------------------------------------------------------ seek, pause
log "--- a tap on the scrubber at 70 % of the measured track"
SHOWN_AT="$(device_ms)"
adb shell input tap 540 600; sleep 0.4               # the controls (they have faded by now)
gdump "$D/shown.xml"
TRACK="$(epx "$D/shown.xml" player_track)"
record "the measured track (epx: left top right bottom)" "$TRACK"
assert_near "the track runs x 12 → 348 epx" "12.0 348.0" "$(echo "$TRACK" | cut -d' ' -f1,3)" 0.4
# shellcheck disable=SC2046
set -- $(centre_px "$D/shown.xml" player_track); TY="${2:-1917}"
# 70 % of the measured track, in px.
SEEK_X="$(python3 -c 'import sys; l, r = float(sys.argv[1]), float(sys.argv[2]); print(round((l + 0.7 * (r - l)) * 3))' $(echo "${TRACK:-12 0 348 0}" | cut -d' ' -f1,3))"
record "the tap's x (70 % of the measured track; the doc's 741.6 px)" "$SEEK_X"
if [ $(( $(device_ms) - SHOWN_AT )) -gt 2700 ]; then sleep 1.5; adb shell input tap 540 600; sleep 0.3; note "the controls had faded during the dump: shown again before the seek"; fi
adb shell input tap "$SEEK_X" "$TY"
sleep 0.7
screencap "$D/seek-70.png"
adb shell input tap 540 2077                          # play / pause at W/2, nav − 39.8
sleep 0.8
screencap "$D/paused-0s.png"
sleep 2
screencap "$D/paused-2s.png"
gdump "$D/sought.xml"
SEEK_RGB="$(px "$D/seek-70.png" "$CX" "$CY")"
assert_pixel_rule "after the 70 % tap" 7 "$SEEK_RGB"
ELAPSED="$(node_text "$D/sought.xml" player_elapsed)"
record "the elapsed label after the seek (paused)" "$ELAPSED"
assert_eq "the seek landed at 7 s ± 1 s (the elapsed label is 0:00:06 … 0:00:08)" "yes" "$(echo "$ELAPSED" | grep -qE '^0:00:0[678]$' && echo yes || echo no)"
P0="$(px "$D/paused-0s.png" "$CX" "$CY")"; P2="$(px "$D/paused-2s.png" "$CX" "$CY")"
assert_eq "pause holds the pixel for 2 s (the centre pixel 0 s and 2 s after the pause)" "$P0" "$P2"
assert_eq "paused: the session's state" "PAUSED" "$(session_state .id.video)"

# ------------------------------------------------------------------------------------------------ the fade
log "--- the controls' fade on the shell's clock"
MARK2="$(ring_mark)"
adb shell input tap 60 "$TY"; sleep 0.5               # back near the start while paused: ten seconds to run
adb shell input tap 540 2077                          # resume: the controls hold, then fade (out)
sleep 4.6
adb shell input tap 540 600                           # show (in), hold, fade (out)
sleep 4.4
FADES="$(vring "$MARK2" | grep -F '[motion] controls_fade' | sed 's/.*\[motion\] //')"
printf '%s\n' "$FADES" > "$D/fades.txt"
log "the controls_fade lines:"; printf '%s\n' "$FADES" | tee -a "$LOG"
python3 - "$D/fades.txt" > "$D/fade-check.txt" <<'PY'
import re, sys
rows = [dict(re.findall(r'(\w+)=(-?[\d.]+)', l)) for l in open(sys.argv[1]) if 'controls_fade' in l]
rows = [{k: float(v) for k, v in r.items()} for r in rows]
outs = [r for r in rows if r["settle"] > 330]; ins = [r for r in rows if r["settle"] <= 330]
print("lines", len(rows))
print("in_settle", int(ins[-1]["settle"]) if ins else -1)
print("out_settle", int(outs[-1]["settle"]) if outs else -1)
hold = -1
if ins and outs and outs[-1]["t0"] > ins[-1]["t0"]: hold = int(outs[-1]["t0"] - (ins[-1]["t0"] + ins[-1]["settle"]))
print("hold", hold)
print("max_gap", max(r["maxGapMs"] for r in rows) if rows else -1)
PY
val() { sed -n "s/^$1 //p" "$D/fade-check.txt"; }
assert_ne "the slice holds [motion] controls_fade lines" "0" "$(val lines)"
assert_within "the controls fade after ≈3.2 s (ms from the fade-in's settle to the fade-out's t0; Y6 3.17–3.32 s)" 3245 "$(val hold)" 75
assert_within "fading out over 367–400 ms (the fade-out line's settle)" 383.5 "$(val out_settle)" 16.5
assert_within "every controls_fade line's maxGapMs ≤ 33.4 ms (C-31)" 16.7 "$(val max_gap)" 16.7
record "the fade-in's settle (ms; E19 grades it)" "$(val in_settle)"
rings_save
adb shell input keyevent KEYCODE_BACK; sleep 1.5

# ------------------------------------------------------------------------------------------------ the ten colours
log "--- RECORDED: the centre pixel at k.5 s for every k (a second play, for the ruling on ± 8)"
dump_ui "$D/mine2.xml"
[ "$(has_node "$D/mine2.xml" "video_tile:$ID")" = yes ] || scroll_to_node "$D/mine2.xml" "video_tile:$ID" 12
MARK3="$(ring_mark)"
tap_node "$D/mine2.xml" "video_tile:$ID"
LINE="$(await_vline "$MARK3" "[video] playing $ID" 100)"
T1="$(wall_of "$LINE")"
DONE=()
for k in 0 1 2 3 4 5 6 7 8 9; do
  while [ "$(device_ms)" -lt $(( ${T1:-0} + k * 1000 + 500 - 300 )) ]; do sleep 0.03; done
  screencap "$D/k$k.png"; DONE+=("$(( $(device_ms) - ${T1:-0} ))")
done
sleep 1.2; screencap "$D/kend.png"; DONE+=("$(( $(device_ms) - ${T1:-0} ))")      # the last frame, held after the end
for k in 0 1 2 3 4 5 6 7 8 9 end; do
  i="$k"; [ "$k" = end ] && i=10
  RGB="$(px "$D/k$k.png" "$CX" "$CY")"; NEAR="$(nearest_colour "$RGB")"
  record "k=$k: capture returned ${DONE[$i]} ms after the playing line (nominal colour $([ "$k" = end ] && echo 9 || echo "$k") = $(colour "$([ "$k" = end ] && echo 9 || echo "$k")"))" "$RGB — nearest colour ${NEAR%% *} ($(colour "${NEAR%% *}")), largest channel difference ${NEAR##* }"
done
rings_save
adb shell input keyevent KEYCODE_BACK; sleep 1

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
media_down
videos_grant_restore
quiet_off
c6; ensure_start
row_end
