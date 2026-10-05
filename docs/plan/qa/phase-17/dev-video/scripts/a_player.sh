#!/usr/bin/env bash
# Phase 17 development proof, build task 7 (brief A): the player on a MediaStore video — the picture at 3.5 s, a 70 %
# seek, pause, the chrome's places (Y5), the controls' fade (Y6), the "•••" menu, the `video` session and where Start
# routes it, the conditional CC, and the end of the session on Back. Restores: the pushed videos are removed.
. "$(dirname "$0")/v17.sh"
row_begin A_PLAYER "the player: picture, seek, pause, chrome, fade, menu, session"
D="$ROW_DIR"
colour() { sed -n "s/^$1 //p" "$FIX/colours.txt"; }
CENSUS="$(video_count)"
videos_grant; record "READ_MEDIA_VIDEO before the row (granted for it, restored after)" "$VIDEOS_WAS"
push_videos qa-steps.mp4 qa-subs.mp4 qa-subs.srt
ID="$(video_id qa-steps.mp4)"; SUBS="$(video_id qa-subs.mp4)"
assert_ne "qa-steps.mp4 is in MediaStore" "" "$ID"
adb shell am force-stop app.tileshell; ensure_start; sleep 1
LAUNCHER_PID="$(adb shell pidof app.tileshell | tr -d '\r')"

MARK="$(ring_mark)"
adb shell am start -n app.tileshell/.video.PlayerActivity -a android.intent.action.VIEW -d "content://media/external/video/media/$ID" -t video/mp4 >/dev/null
LINE=""; for _ in $(seq 1 40); do LINE="$(vline "$MARK" "[video] playing $ID")"; [ -n "$LINE" ] && break; sleep 0.1; done
assert_contains "[video] playing <id>" "[video] playing $ID" "$LINE"
T0="$(wall_of "$LINE")"
while [ "$(device_ms)" -lt $((T0 + 3200)) ]; do sleep 0.05; done
screencap "$D/at-3.5s.png"; SHOT_AT=$(( $(device_ms) - T0 ))
record "screencap taken (ms after the playing line, the capture itself ends here)" "$SHOT_AT"
record "centre pixel at ≈3.5 s (nominal $(colour 3); the AVD's own colour error is recorded, E11 allows 8)" "$(pixel "$D/at-3.5s.png" 540 1098)"
assert_colour "centre pixel at ≈3.5 s is colour 3" "$(colour 3)" "$(pixel "$D/at-3.5s.png" 540 1098)" 20
assert_eq "PlayerActivity is on top" "app.tileshell/.video.PlayerActivity" "$(top)"
assert_contains "[video] playing scheme=content" "[video] playing scheme=content" "$(vring "$MARK")"

# The session while it plays, and where Start's routing sends it.
SESS="$(adb shell dumpsys media_session)"
assert_contains "a media session of the shell tagged video" "video" "$(echo "$SESS" | grep -E 'app.tileshell' | grep -iE 'video' | head -3)"
record "session lines" "$(echo "$SESS" | grep -E 'app\.tileshell' | head -4 | tr -s ' ' | tr '\n' '|')"
LSLICE="$(ring_since "$MARK" launcher)"
assert_contains "launcher: the video session lands on no tile" "[music] session app.tileshell id=video -> none" "$LSLICE"
assert_absent "launcher: no tile takes it" "id=video -> cmp:" "$LSLICE"

# Show the controls (they auto-hide after ≈3.2 s), pause, and read the chrome.
gdump "$D/hidden.xml"
if [ "$(has_node "$D/hidden.xml" player_scrim)" = yes ]; then sleep 1.5; gdump "$D/hidden.xml"; fi
assert_eq "controls are gone after the hold" "no" "$(has_node "$D/hidden.xml" player_scrim)"
assert_eq "the picture's node is there" "yes" "$(has_node "$D/hidden.xml" video_surface)"
record "video_surface epx (letterboxed: 360 x 202.5 centred in 732)" "$(epx "$D/hidden.xml" video_surface)"
adb shell input tap 540 600; sleep 0.5          # show
adb shell input tap 540 2077; sleep 0.6         # play / pause at W/2, nav − 39.8
gdump "$D/paused.xml"
for t in player_scrim player_track player_played player_thumb player_seek player_elapsed player_remaining player_back10 player_playpause player_fwd30 player_fullscreen player_more; do
  assert_eq "node $t" "yes" "$(has_node "$D/paused.xml" "$t")"
  record "$t epx" "$(epx "$D/paused.xml" "$t")"
done
assert_eq "no CC node for a file with no text track" "no" "$(has_node "$D/paused.xml" player_cc)"
NAV="$(epx "$D/paused.xml" w10m_nav_bar | cut -d' ' -f2)"
record "nav bar top (epx)" "$NAV"
geo() { python3 - "$NAV" "$@" <<'PY'
import sys
nav=float(sys.argv[1]); what=sys.argv[2]; l,t,r,b=[float(v) for v in sys.argv[3:7]]
if what=="scrim": print("%.1f %.1f" % (nav-t, b-nav))                  # height above nav, bottom gap
elif what=="track": print("%.1f %.1f %.1f %.1f" % (l, r, nav-(t+b)/2, b-t))  # left right centre-above-nav thickness
elif what=="centre": print("%.1f %.1f" % ((l+r)/2, nav-(t+b)/2))
elif what=="size": print("%.1f %.1f" % (r-l, b-t))
PY
}
assert_near "scrim: 120 epx above the nav bar, ending at it" "120.0 0.0" "$(geo scrim $(epx "$D/paused.xml" player_scrim))" 0.4
assert_near "track: x 12 → 348, centre nav − 93, 2 epx" "12.0 348.0 93.0 2.0" "$(geo track $(epx "$D/paused.xml" player_track))" 0.4
assert_near "thumb: Ø 22" "22.0 22.0" "$(geo size $(epx "$D/paused.xml" player_thumb))" 0.4
THUMB="$(geo centre $(epx "$D/paused.xml" player_thumb))"
assert_near "thumb centre 1.2 below the track centre (nav − 91.8)" "91.8" "${THUMB#* }" 0.4
record "thumb centre x while paused" "${THUMB% *}"
assert_near "elapsed label starts at 12.5" "12.5" "$(epx "$D/paused.xml" player_elapsed | cut -d' ' -f1)" 0.5
assert_near "remaining label ends at 345.5" "345.5" "$(epx "$D/paused.xml" player_remaining | cut -d' ' -f3)" 0.5
for spec in "player_back10 132.0" "player_playpause 180.0" "player_fwd30 228.0" "player_fullscreen 276.0" "player_more 324.0"; do
  set -- $spec
  assert_near "$1 centre (x, above nav)" "$2 39.8" "$(geo centre $(epx "$D/paused.xml" "$1"))" 0.4
done
record "elapsed label epx" "$(epx "$D/paused.xml" player_elapsed)"
record "remaining label epx (ends at 345.5)" "$(epx "$D/paused.xml" player_remaining)"
E1="$(node_text "$D/paused.xml" player_elapsed)"; R1="$(node_text "$D/paused.xml" player_remaining)"
record "labels while paused" "$E1 / $R1"

# A tap at 70 % of the track (247.2 epx = 741.6 px) seeks to colour 7; paused, the picture holds for 2 s.
set -- $(centre_px "$D/paused.xml" player_track)
adb shell input tap 742 "$2"; sleep 1.2
screencap "$D/seek-70.png"
assert_colour "after the 70 % seek the picture is colour 7" "$(colour 7)" "$(pixel "$D/seek-70.png" 540 1098)" 20
sleep 2
screencap "$D/seek-70-held.png"
assert_colour "paused: the pixel holds for 2 s" "$(colour 7)" "$(pixel "$D/seek-70-held.png" 540 1098)" 20
gdump "$D/sought.xml"
assert_eq "elapsed label after the seek" "0:00:07" "$(node_text "$D/sought.xml" player_elapsed)"
assert_eq "remaining label counts down (10 − 7)" "0:00:03" "$(node_text "$D/sought.xml" player_remaining)"
assert_eq "still paused: controls stay" "yes" "$(has_node "$D/sought.xml" player_scrim)"

# The "•••" menu.
tap_node "$D/sought.xml" player_more; sleep 0.6
gdump "$D/menu.xml"
ORDER="$(grep -o 'resource-id="player_menu:[a-z]*"' "$D/menu.xml" | sed 's/.*://;s/"//' | xargs)"
assert_eq "menu items in order" "cast zoom repeat autoplay" "$ORDER"
TEXTS="$(python3 - "$D/menu.xml" <<'PY'
import re,sys
x=open(sys.argv[1],encoding="utf-8",errors="replace").read()
print(" / ".join(t for t in re.findall(r'text="([^"]+)"',x) if t in ("Cast to device","Zoom to fill","Repeat","Autoplay")))
PY
)"
assert_eq "menu texts" "Cast to device / Zoom to fill / Repeat / Autoplay" "$TEXTS"
record "menu epx (flush right, 171.2 wide, bottom nav − 60)" "$(epx "$D/menu.xml" player_menu)"
adb shell input tap 200 600; sleep 0.5          # off the menu: it closes

# The fade on the shell's clock: resume, let the controls go, show them again, let them go again.
MARK2="$(ring_mark)"
gdump "$D/before-resume.xml"
set -- $(centre_px "$D/before-resume.xml" player_track); adb shell input tap 60 "$2"; sleep 0.5   # back near the start: 10 s to run
tap_node "$D/before-resume.xml" player_playpause
sleep 4.6
adb shell input tap 540 600                      # show: a fade-in line
sleep 4.8
FADES="$(vring "$MARK2" | grep -F '[motion] controls_fade' | sed 's/.*\[motion\] //')"
echo "$FADES" > "$D/fades.txt"
record "controls_fade lines" "$(echo "$FADES" | tr '\n' '|')"
python3 - "$D/fades.txt" > "$D/fade-check.txt" <<'PY'
import re,sys
rows=[dict(re.findall(r'(\w+)=(-?[\d.]+)',l)) for l in open(sys.argv[1]) if l.strip()]
rows=[{k:float(v) for k,v in r.items()} for r in rows]
outs=[r for r in rows if r["settle"]>300]; ins=[r for r in rows if r["settle"]<=300]
print("in_settle", int(ins[-1]["settle"]) if ins else -1)
print("out_settle", int(outs[-1]["settle"]) if outs else -1)
hold=-1
if ins and outs and outs[-1]["t0"]>ins[-1]["t0"]: hold=int(outs[-1]["t0"]-(ins[-1]["t0"]+ins[-1]["settle"]))
print("hold", hold)
print("max_gap", max(r["maxGapMs"] for r in rows) if rows else -1)
PY
val() { sed -n "s/^$1 //p" "$D/fade-check.txt"; }
assert_within "fade-in settle (ms; Y6 167–300)" 233 "$(val in_settle)" 67
assert_within "fade-out settle (ms; Y6 367–400)" 383 "$(val out_settle)" 17
assert_within "hold (ms; Y6 ≈3.2 s, 3.17–3.32)" 3245 "$(val hold)" 80
record "largest frame gap in the fades (ms)" "$(val max_gap)"

# The conditional CC: the same video with a .srt of its name beside it.
for r in $RINGS; do ring_save "$r"; done
adb shell input keyevent KEYCODE_BACK; sleep 1
assert_ne "Back leaves the player" "app.tileshell/.video.PlayerActivity" "$(top)"
assert_absent "no session of the shell is left after Back" "app.tileshell" "$(adb shell dumpsys media_session | grep -E 'package=app\.tileshell|app\.tileshell/video' | head -3)"
MARK3="$(ring_mark)"
adb shell am start -n app.tileshell/.video.PlayerActivity -a android.intent.action.VIEW -d "content://media/external/video/media/$SUBS" -t video/mp4 >/dev/null
sleep 4.4; adb shell input tap 540 600; sleep 0.5; adb shell input tap 540 2077; sleep 0.6   # hidden by now: show, then pause
gdump "$D/subs.xml"
assert_contains "the .srt of the same name is found" "subtitle file beside $SUBS: found" "$(vring "$MARK3")"
assert_eq "CC node with a .srt beside the file" "yes" "$(has_node "$D/subs.xml" player_cc)"
assert_near "CC centre (x 36, above nav 39.8)" "36.0 39.8" "$(geo centre $(epx "$D/subs.xml" player_cc))" 0.4
tap_node "$D/subs.xml" player_cc; sleep 0.4
tap_node "$D/subs.xml" player_playpause; sleep 1.5
gdump "$D/subs-on.xml"
assert_eq "the subtitle line shows once captions are on" "QA subtitle line" "$(node_text "$D/subs-on.xml" player_subtitle)"
record "tracks line" "$(vline "$MARK3" "[video] tracks:" | sed 's/.*\[video\] //')"
for r in $RINGS; do ring_save "$r"; done
adb shell input keyevent KEYCODE_BACK; sleep 1

assert_eq "launcher pid unchanged" "$LAUNCHER_PID" "$(adb shell pidof app.tileshell | tr -d '\r')"
assert_eq "no crash of the shell" "" "$(adb logcat -d -t 600 -s AndroidRuntime | grep -F 'app.tileshell' | head -3)"
remove_videos
assert_eq "videos restored to the census" "$CENSUS" "$(video_count)"
for r in $RINGS; do ring_save "$r"; done
videos_grant_restore
adb shell am force-stop app.tileshell; ensure_start
row_end
