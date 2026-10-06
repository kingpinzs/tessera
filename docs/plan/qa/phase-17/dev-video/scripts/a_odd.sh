#!/usr/bin/env bash
# Phase 17 development proof, build task 7 (brief A.4 and A.1): the "•••" menu's four items doing what they say — Zoom
# to fill, Repeat, Autoplay (the next video of the same My videos group), Cast to device — a rotated file drawn
# upright, the full-screen button, and an APK update while a video plays leaving no session behind. Restores: the
# three settings off, the pushed videos removed, the orientation back to portrait.
. "$(dirname "$0")/v17.sh"
row_begin A_ODD "the menu's items, rotation, full screen, an update mid-play"
D="$ROW_DIR"
CENSUS="$(video_count)"; videos_grant
push_videos qa-steps.mp4 qa-subs.mp4 qa-rot90.mp4
ID="$(video_id qa-steps.mp4)"; NEXT="$(video_id qa-subs.mp4)"; ROT="$(video_id qa-rot90.mp4)"
adb shell am force-stop app.tileshell; ensure_start
show_paused() { sleep 4.4; adb shell input tap 540 600; sleep 0.5; adb shell input tap 540 2077; sleep 0.6; gdump "$1"; }
menu_tap() { # dump item
  tap_node "$1" player_more; sleep 0.6; gdump "$D/.menu.xml"; tap_node "$D/.menu.xml" "player_menu:$2"; sleep 0.8
}
wh() { set -- $(epx "$1" video_surface); python3 -c "print(round($3-$1,1), round($4-$2,1))"; }

# A rotated file is drawn upright: its picture is taller than wide.
MARK="$(ring_mark)"
adb shell am start -n app.tileshell/.video.PlayerActivity -a android.intent.action.VIEW -d "content://media/external/video/media/$ROT" -t video/mp4 >/dev/null
await_vline "$MARK" "[video] playing $ROT" >/dev/null; sleep 1
gdump "$D/rot.xml"
record "rotated file: the picture's size (epx)" "$(wh "$D/rot.xml")"
assert_eq "rotated file: taller than wide" "yes" "$(set -- $(wh "$D/rot.xml"); python3 -c "print('yes' if $2 > $1 else 'no')")"
adb shell input keyevent KEYCODE_BACK; sleep 1

# Opened from My videos, so the player knows the group (Autoplay's queue).
hub myvideos 2.5
scroll_to_node "$D/mine.xml" "video_tile:$ID" 10
MARK="$(ring_mark)"
tap_node "$D/mine.xml" "video_tile:$ID"
await_vline "$MARK" "[video] playing $ID" >/dev/null
show_paused "$D/p1.xml"
assert_near "letterboxed by default: 360 × 202.5" "360 202.5" "$(wh "$D/p1.xml")" 0.5

# Zoom to fill: the picture covers the page (its height is the page's, its width overflows); again: fitted.
menu_tap "$D/p1.xml" zoom; gdump "$D/zoom.xml"
NAV="$(epx "$D/zoom.xml" w10m_nav_bar | cut -d' ' -f2)"
assert_near "Zoom to fill: the picture is as tall as the page" "$NAV" "$(wh "$D/zoom.xml" | cut -d' ' -f2)" 0.5
tap_node "$D/zoom.xml" player_more; sleep 0.6; gdump "$D/zoom-menu.xml"
assert_contains "Zoom to fill is marked on in the menu" 'checked="true"' "$(grep -o '<node[^>]*resource-id="player_menu:zoom"[^>]*>' "$D/zoom-menu.xml")"
tap_node "$D/zoom-menu.xml" player_menu:zoom; sleep 0.8; gdump "$D/unzoom.xml"
assert_near "Zoom to fill off: fitted again" "360 202.5" "$(wh "$D/unzoom.xml")" 0.5

# Cast to device: Android's own cast settings, resolved at the tap.
MARK2="$(ring_mark)"
menu_tap "$D/unzoom.xml" cast; sleep 1.5
record "Cast to device: the activity on top" "$(top)"
assert_contains "Cast to device: the line" "[video] cast: " "$(vring "$MARK2")"
record "Cast to device: what the line says" "$(vline "$MARK2" "[video] cast: " | sed 's/.*\[video\] //')"
[ "$(top)" = "app.tileshell/.video.PlayerActivity" ] || { adb shell input keyevent KEYCODE_BACK; sleep 1.2; }
assert_eq "back in the player" "app.tileshell/.video.PlayerActivity" "$(top)"

# Full screen: the page turns to landscape, and back.
gdump "$D/fs0.xml"
[ "$(has_node "$D/fs0.xml" player_fullscreen)" = yes ] || { adb shell input tap 540 600; sleep 0.6; gdump "$D/fs0.xml"; }
rot() { adb shell dumpsys window displays | grep -m1 -oE 'mCurrentRotation=ROTATION_[0-9]+|rotation=[0-9]' | head -1 | tr -d '\r'; }
R0="$(rot)"; tap_node "$D/fs0.xml" player_fullscreen; sleep 2
R1="$(rot)"; record "full screen: rotation before → after" "$R0 → $R1"
assert_ne "full screen: the page turned" "$R0" "$R1"
gdump "$D/fs1.xml"
assert_eq "full screen: the nav bar is still drawn" "yes" "$(has_node "$D/fs1.xml" w10m_nav_bar)"
[ "$(has_node "$D/fs1.xml" player_fullscreen)" = yes ] || { adb shell input tap 800 300; sleep 0.6; gdump "$D/fs1.xml"; }
tap_node "$D/fs1.xml" player_fullscreen; sleep 2
assert_eq "full screen again: back to portrait" "$R0" "$(rot)"

# Autoplay: on, the video run to its end → the next video of the group starts by itself.
gdump "$D/a0.xml"
[ "$(has_node "$D/a0.xml" player_more)" = yes ] || { adb shell input tap 540 600; sleep 0.6; gdump "$D/a0.xml"; }
menu_tap "$D/a0.xml" autoplay
gdump "$D/a1.xml"
MARK3="$(ring_mark)"
set -- $(centre_px "$D/a1.xml" player_track); adb shell input tap 1000 "$2"; sleep 0.5     # near the end
tap_node "$D/a1.xml" player_playpause
LINE="$(await_vline "$MARK3" "[video] playing $NEXT" 120)"
assert_contains "Autoplay: [video] autoplay next <id>" "[video] autoplay next $NEXT" "$(vring "$MARK3")"
assert_contains "Autoplay: the next video of the group plays" "[video] playing $NEXT" "$LINE"

# Repeat: on, the video run to its end → it starts again (no "next", the position back near the start).
show_paused "$D/r0.xml"
menu_tap "$D/r0.xml" autoplay                       # off again
gdump "$D/r1.xml"; menu_tap "$D/r1.xml" repeat
gdump "$D/r2.xml"
set -- $(centre_px "$D/r2.xml" player_track); adb shell input tap 1000 "$2"; sleep 0.5
MARK4="$(ring_mark)"
tap_node "$D/r2.xml" player_playpause; sleep 3.5
adb shell input tap 540 600; sleep 0.5; gdump "$D/r3.xml"
[ "$(has_node "$D/r3.xml" player_elapsed)" = yes ] || { adb shell input tap 540 600; sleep 0.6; gdump "$D/r3.xml"; }
record "Repeat: the elapsed label 3.5 s after playing from 0:00:09" "$(node_text "$D/r3.xml" player_elapsed)"
assert_contains "Repeat: it wrapped to the start (0:00:00 … 0:00:03)" "0:00:0" "$(node_text "$D/r3.xml" player_elapsed | grep -E '^0:00:0[0-3]$')"
assert_absent "Repeat: no autoplay" "autoplay next" "$(vring "$MARK4")"
gdump "$D/r4.xml"; [ "$(has_node "$D/r4.xml" player_more)" = yes ] || { adb shell input tap 540 600; sleep 0.6; gdump "$D/r4.xml"; }
menu_tap "$D/r4.xml" repeat                         # off again: the settings are left as found
for r in $RINGS; do ring_save "$r"; done

# An APK update while a video plays: the processes go, and no session of the shell is left.
assert_contains "a session of the shell while it plays" "app.tileshell" "$(adb shell dumpsys media_session | grep -E 'package=app\.tileshell' | head -2)"
adb install -r "$APK" >/dev/null 2>&1; sleep 3
assert_absent "after install -r: no session of the shell is left" "app.tileshell" "$(adb shell dumpsys media_session | grep -E 'package=app\.tileshell' | head -2)"
assert_eq "after install -r: no crash of the shell" "" "$(no_crash)"

videos_grant; remove_videos
assert_eq "videos restored to the census" "$CENSUS" "$(video_count)"
PREFS="$(adb shell run-as app.tileshell cat shared_prefs/video_player.xml 2>/dev/null | tr -d '\r')"
assert_absent "the three settings are off again" 'value="true"' "$PREFS"
videos_grant_restore
adb shell am force-stop app.tileshell; ensure_start
row_end
