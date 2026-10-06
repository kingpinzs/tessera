#!/usr/bin/env bash
# Phase 17 development proof, build tasks 7 and 14 (brief B): the hub's chrome and its ≡ pane against E19's Movies & TV
# values, My videos (tiles, captions, the observer, the tap into the player), the settings and About pages, and the
# Videos grant from the empty state. Restores: the pushed videos removed, READ_MEDIA_VIDEO as it was found.
. "$(dirname "$0")/v17.sh"
row_begin B_HUB "the hub: chrome, pane, My videos, settings, the grant"
D="$ROW_DIR"
STATUS=28   # BarMetrics.STATUS_EPX (phase 01's drawn status bar, C-17)
CENSUS="$(video_count)"; videos_grant
push_videos qa-steps.mp4 qa-subs.mp4
ID="$(video_id qa-steps.mp4)"; ID2="$(video_id qa-subs.mp4)"
adb shell am force-stop app.tileshell; sleep 1
MARK="$(ring_mark)"
adb shell am start -W -n app.tileshell/.video.VideoActivity >/dev/null; sleep 2.5
dump_ui "$D/library.xml"; screencap "$D/library.png"
assert_eq "VideoActivity is on top" "app.tileshell/.video.VideoActivity" "$(top)"
assert_contains "[video] library: <n> (census + 2)" "[video] library: $((CENSUS + 2))" "$(vring "$MARK")"

# The chrome: phase 01's status bar and the 48-epx header as one #171717 band; ≡ at 24, the title at 60.25, search at W − 24.
assert_near "status bar 0 → $STATUS epx (its node is the row inside its 12-epx side padding)" "0 $STATUS" "$(epx "$D/library.xml" w10m_status_bar | cut -d' ' -f2,4)" 0.4
assert_near "header: 48 epx under the status bar" "0 $STATUS 360 $((STATUS + 48))" "$(epx "$D/library.xml" hub_header)" 0.4
assert_colour "status bar fill is #171717" "23 23 23" "$(pixel "$D/library.png" 540 9)" 4
assert_colour "header fill is #171717" "23 23 23" "$(pixel "$D/library.png" 600 $(( (STATUS + 10) * 3 )))" 4
assert_colour "page under the header is black" "0 0 0" "$(pixel "$D/library.png" 1040 $(( (STATUS + 48 + 6) * 3 )))" 4
cx() { python3 -c 'import sys; print("%.1f" % ((float(sys.argv[1])+float(sys.argv[3]))/2))' $1 $2 $3 $4; }
assert_near "≡ centre x 24" "24.0" "$(cx $(epx "$D/library.xml" hub_menu))" 0.4
assert_near "search centre x W − 24" "336.0" "$(cx $(epx "$D/library.xml" hub_search))" 0.4
assert_eq "the header's title, in capitals" "MY VIDEOS" "$(node_text "$D/library.xml" hub_header_title)"
python3 - "$D/library.png" $(( (STATUS) * 3 )) $(( (STATUS + 48) * 3 )) > "$D/ink.txt" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); top, bottom = int(sys.argv[2]), int(sys.argv[3])
def ink(x0, x1):
    xs = [x for x in range(x0, x1) for y in range(top, bottom) if min(im.getpixel((x, y))) > 150]
    ys = [y for x in range(x0, x1) for y in range(top, bottom) if min(im.getpixel((x, y))) > 150]
    return (min(xs) / 3, max(xs) / 3 + 1 / 3, min(ys) / 3, max(ys) / 3 + 1 / 3) if xs else (0, 0, 0, 0)
m = ink(0, 150); t = ink(160, 700); s = ink(940, 1080)
print("menu_ink %.1f %.1f" % (m[0], m[1] - m[0]))
print("title_ink_left %.2f" % t[0]); print("title_cap %.1f" % (t[3] - t[2])); print("title_cy %.1f" % ((t[2] + t[3]) / 2))
print("search_ink %.1f %.1f" % ((s[0] + s[1]) / 2, s[1] - s[0]))
PY
iv() { sed -n "s/^$1 //p" "$D/ink.txt"; }
assert_near "title ink starts at 60.25 (± 1, HIGH)" "60.25" "$(iv title_ink_left)" 1.0
record "≡ ink left and width (R11: 14 → 34, 20 wide)" "$(iv menu_ink)"
record "title cap height and centre y (R11: cap 11.0, cy header centre + 1 = $((STATUS + 25)))" "$(iv title_cap) $(iv title_cy)"
record "search ink centre and width (R11: W − 24, 18 wide)" "$(iv search_ink)"

# My videos: 112-epx tiles on a 124 pitch from x 12, the first row at chrome + 48, 2 per row; the caption, no duration.
FIRST_IDS=($(grep -o 'resource-id="video_tile:[0-9]*"' "$D/library.xml" | sed 's/.*video_tile:\([0-9]*\)"/\1/' | head -2))
assert_near "first tile: 112 × 112 at x 12, top = chrome + 48" "12 $((STATUS + 96)) 124 $((STATUS + 208))" "$(epx "$D/library.xml" "video_tile:${FIRST_IDS[0]}")" 0.4
assert_near "second tile: the 124 pitch, same row" "136 $((STATUS + 96)) 248 $((STATUS + 208))" "$(epx "$D/library.xml" "video_tile:${FIRST_IDS[1]}")" 0.4
record "group headers on the first screen" "$(grep -o 'resource-id="video_group:[^"]*"' "$D/library.xml" | sed 's/.*video_group://;s/"//' | xargs)"
scroll_to_node "$D/mine.xml" "video_caption:$ID" 10
assert_eq "the fixture's tile is listed" "yes" "$(has_node "$D/mine.xml" "video_tile:$ID")"
assert_eq "its caption is the file name without extension" "qa-steps" "$(node_text "$D/mine.xml" "video_caption:$ID")"
assert_eq "its folder's header" "QA-Video" "$(node_text "$D/mine.xml" "video_group:QA-Video")"
assert_absent "no duration anywhere on the page" "0:10" "$(grep -o 'text="[^"]*"' "$D/mine.xml")"
set -- $(epx "$D/mine.xml" "video_tile:$ID"); TL="$1"; TB="$4"
set -- $(epx "$D/mine.xml" "video_caption:$ID")
assert_near "caption: from the tile's left, clipped at tile left + 100" "$TL $(python3 -c "print($TL+100)")" "$1 $3" 0.5
record "caption box top below the tile's bottom (first baseline 24.5 − ascent 14.85 = 9.65)" "$(python3 -c "print(round($2-$TB,2))")"

# A tap on the tile: the shared player, by explicit component, and back.
MARK="$(ring_mark)"
tap_node "$D/mine.xml" "video_tile:$ID"
LINE="$(await_vline "$MARK" "[video] playing $ID")"
assert_contains "tap → [video] playing <id>" "[video] playing $ID" "$LINE"
assert_eq "the player is on top" "app.tileshell/.video.PlayerActivity" "$(top)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
assert_eq "Back returns to the hub" "app.tileshell/.video.VideoActivity" "$(top)"

# The observer: a file removed and scanned leaves the page with no restart.
MARK="$(ring_mark)"
adb shell rm "$DEVICE_DIR/qa-subs.mp4"; scan_media; sleep 2.5
assert_contains "observer: [video] library: <n − 1>" "[video] library: $((CENSUS + 1))" "$(vring "$MARK")"
dump_ui "$D/after-rm.xml"
assert_eq "observer: the removed video's tile is gone" "no" "$(has_node "$D/after-rm.xml" "video_tile:$ID2")"

# The ≡ pane: 256 epx from the chrome's bottom, #171717, no scrim, 48-epx rows, the current row's accent bar.
adb shell input swipe 540 900 540 1900 200; sleep 1    # back to the top of the list
screencap "$D/pane-before.png"
MARK="$(ring_mark)"
dump_ui "$D/pre-pane.xml"; tap_node "$D/pre-pane.xml" hub_menu; sleep 1
dump_ui "$D/pane.xml"; screencap "$D/pane.png"
NAV="$(epx "$D/pane.xml" w10m_nav_bar | cut -d' ' -f2)"
assert_near "pane: 256 wide, chrome bottom → nav top" "0 $((STATUS + 48)) 256 $NAV" "$(epx "$D/pane.xml" hub_pane)" 0.4
assert_colour "pane fill is #171717" "23 23 23" "$(pixel "$D/pane.png" 600 1500)" 4
SAME="$(python3 - "$D/pane-before.png" "$D/pane.png" $(( (STATUS + 48) * 3 )) <<'PY'
import sys
from PIL import Image, ImageChops
a = Image.open(sys.argv[1]).convert("RGB"); b = Image.open(sys.argv[2]).convert("RGB")
box = (780, int(sys.argv[3]), 1080, 2000)   # right of the pane (x 260 → 360 epx), under the header
print("same" if ImageChops.difference(a.crop(box), b.crop(box)).getbbox() is None else "different")
PY
)"
assert_eq "no scrim: the page right of the pane is unchanged, pixel for pixel" "same" "$SAME"
assert_near "row 1 (My videos): 48 epx from the chrome's bottom" "0 $((STATUS + 48)) 256 $((STATUS + 96))" "$(epx "$D/pane.xml" hub_pane:myvideos)" 0.4
assert_contains "the current row is selected" 'selected="true"' "$(grep -o '<node[^>]*resource-id="hub_pane:myvideos"[^>]*>' "$D/pane.xml")"
assert_near "the accent bar: 4 × 48 at x 0" "0 $((STATUS + 48)) 4 $((STATUS + 96))" "$(epx "$D/pane.xml" hub_pane_bar)" 0.4
assert_near "label x 48" "48.0" "$(epx "$D/pane.xml" hub_pane_label:myvideos | cut -d' ' -f1)" 0.5
assert_near "the settings row is the bottom one" "0 $(python3 -c "print($NAV-48)") 256 $NAV" "$(epx "$D/pane.xml" hub_pane:settings)" 0.4
assert_eq "the title is hidden while the pane is open" "no" "$(has_node "$D/pane.xml" hub_header_title)"
assert_eq "no Media server row while none is set up" "no" "$(has_node "$D/pane.xml" hub_pane:mediaserver)"
adb shell input tap 960 1200; sleep 0.8                 # off the pane
dump_ui "$D/pane-closed.xml"
assert_eq "a tap off the pane closes it" "no" "$(has_node "$D/pane-closed.xml" hub_pane)"
MOTION="$(vring "$MARK" | grep -F '[motion] pane_' | sed 's/.*\[motion\] //')"
record "pane motion lines" "$(echo "$MOTION" | tr '\n' '|')"
mval() { echo "$MOTION" | grep -F "$1 " | tail -1 | sed -n "s/.*$2=\([0-9.]*\).*/\1/p"; }
assert_within "pane open settled (ms; Y6 167–200 ± 33)" 183 "$(mval pane_open settle)" 33
assert_within "pane close (ms; Y6 67–100 ± 33)" 83 "$(mval pane_close settle)" 33
assert_within "pane open: largest frame gap ≤ 33.4 ms" 17 "$(mval pane_open maxGapMs)" 16.4

# Settings and About (the pane's bottom group).
dump_ui "$D/pre2.xml"; tap_node "$D/pre2.xml" hub_menu; sleep 0.8
dump_ui "$D/pane2.xml"; tap_node "$D/pane2.xml" hub_pane:settings; sleep 0.8
dump_ui "$D/settings.xml"
assert_eq "the settings page" "yes" "$(has_node "$D/settings.xml" hub_page:settings)"
assert_eq "its header title" "SETTINGS" "$(node_text "$D/settings.xml" hub_header_title)"
tap_node "$D/settings.xml" hub_settings:about; sleep 0.8
dump_ui "$D/about.xml"
assert_eq "About: TMDB's attribution, as its terms word it" "This product uses the TMDB API but is not endorsed or certified by TMDB." "$(node_text "$D/about.xml" hub_about_tmdb)"
assert_contains "About: JustWatch's credit" "JustWatch" "$(node_text "$D/about.xml" hub_about_justwatch)"
assert_eq "About: the logo is drawn" "yes" "$(has_node "$D/about.xml" hub_about_logo)"
adb shell input keyevent KEYCODE_BACK; sleep 0.8
dump_ui "$D/back.xml"
assert_eq "Back from About returns to settings" "yes" "$(has_node "$D/back.xml" hub_page:settings)"

# The Videos grant, from the empty state (the revoke restarts the process, as Android does).
for r in $RINGS; do ring_save "$r"; done
adb shell pm revoke app.tileshell android.permission.READ_MEDIA_VIDEO; sleep 1
MARK="$(ring_mark)"
adb shell am start -W -n app.tileshell/.video.VideoActivity >/dev/null; sleep 2.5
dump_ui "$D/denied.xml"
assert_contains "denied: the empty state names the Setup checklist" "Setup checklist" "$(node_text "$D/denied.xml" video_empty)"
assert_eq "denied: the grant is offered there" "yes" "$(has_node "$D/denied.xml" video_grant)"
assert_contains "denied: [video] access=DENIED" "[video] access=DENIED" "$(vring "$MARK")"
assert_contains "denied: [video] library: 0" "[video] library: 0" "$(vring "$MARK")"
# With READ_MEDIA_IMAGES held Android grants the rest of the same permission group with no dialog; otherwise its own
# dialog is up and its allow button is tapped. Either way the grant is made in place, from the app.
MARK="$(ring_mark)"
tap_node "$D/denied.xml" video_grant; sleep 2
dump_ui "$D/dialog.xml"
if grep -q 'package="com.android.permissioncontroller"' "$D/dialog.xml"; then
  ALLOW="$(grep -o 'resource-id="com.android.permissioncontroller:id/permission_allow[a-z_]*"' "$D/dialog.xml" | head -1 | sed 's/resource-id="//;s/"//')"
  record "Android's permission dialog was shown; its allow button" "$ALLOW"
  tap_node "$D/dialog.xml" "$ALLOW"; sleep 2.5
else
  record "Android's permission dialog" "not shown (the photos-and-videos group was already granted through READ_MEDIA_IMAGES)"
fi
dump_ui "$D/granted.xml"
assert_contains "the request was granted" "[video] videos permission request: granted" "$(vring "$MARK")"
assert_contains "granted in place: [video] library: <n>" "[video] library: $((CENSUS + 1))" "$(vring "$MARK")"
assert_eq "granted in place: the empty state is gone" "no" "$(has_node "$D/granted.xml" video_empty)"
assert_eq "granted in place: the tiles are back" "yes" "$(has_node "$D/granted.xml" "video_tile:${FIRST_IDS[0]}")"

for r in $RINGS; do ring_save "$r"; done
remove_videos
assert_eq "videos restored to the census" "$CENSUS" "$(video_count)"
videos_grant_restore
adb shell am force-stop app.tileshell; ensure_start
assert_eq "no crash of the shell" "" "$(no_crash)"
row_end
