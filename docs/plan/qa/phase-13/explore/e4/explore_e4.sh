#!/usr/bin/env bash
# Exploration only (not row evidence): E4's three bands, so the row places its patches from real dumps.
S="$(cd "$(dirname "$0")/../../scripts" && pwd)"
P01="$(cd "$S/../../phase-01/scripts" && pwd)"
. "$S/lib.sh"; . "$S/p13.sh"; source "$P01/ui.sh"; source "$P01/music_lib.sh"
take_device_lock
ROW_DIR="$(cd "$(dirname "$0")" && pwd)"; LOG="$ROW_DIR/explore.txt"; : > "$LOG"
wake_device
set_pref transparency_effects boolean true
set_checker
show_start 7
to_app_list 3
dump_ui "$ROW_DIR/applist.xml"; screencap "$ROW_DIR/applist.png"
grep -o 'resource-id="applist[^"]*"[^>]*bounds="[^"]*"' "$ROW_DIR/applist.xml" | head -30
ROWID="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/applist.xml" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
echo "row $ROWID $(bounds "$ROW_DIR/applist.xml" "$ROWID")"
set -- $(bounds "$ROW_DIR/applist.xml" "$ROWID"); X=$(( ($1+$3)/2 )); Y=$(( ($2+$4)/2 ))
adb shell input swipe $X $Y $X $Y 1000; sleep 1.5
dump_ui "$ROW_DIR/band.xml"; screencap "$ROW_DIR/band.png"
echo "band $(bounds "$ROW_DIR/band.xml" applist_menu) acrylic $(bounds "$ROW_DIR/band.xml" acrylic:applist_menu)"
python3 "$S/dumpq.py" text_nodes "$ROW_DIR/band.xml" | grep -E 'Pin|Uninstall'
adb shell input keyevent KEYCODE_BACK; sleep 1.5; screencap "$ROW_DIR/band-closed.png"; dump_ui "$ROW_DIR/band-closed.xml"
echo "closed has band: $(has_node "$ROW_DIR/band-closed.xml" applist_menu)"
clear_background
# ---- Music playlists
music_mute; music_fixtures
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
adb shell am force-stop $PKG; sleep 1
adb shell run-as $PKG rm -f files/music_playlists.json
music_open
goto_pivot playlists "$ROW_DIR/pl0.xml"
for n in One Two Three; do
  dump_ui "$ROW_DIR/.pl.xml"; tap_node "$ROW_DIR/.pl.xml" music_new_playlist; sleep 2
  adb shell input keyevent KEYCODE_MOVE_END; for _ in $(seq 1 14); do adb shell input keyevent 67; done
  adb shell input text "QA13%s$n"; sleep 1; adb shell input keyevent 66; sleep 2
done
dump_ui "$ROW_DIR/pl3.xml"; screencap "$ROW_DIR/pl3.png"
grep -o 'resource-id="music_playlist:[^"]*"[^>]*bounds="[^"]*"' "$ROW_DIR/pl3.xml" | awk '{print $1,$NF}'
FIRST="$(grep -o 'resource-id="music_playlist:[^"]*"' "$ROW_DIR/pl3.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
set -- $(bounds "$ROW_DIR/pl3.xml" "$FIRST"); X=$(( ($1+$3)/2 )); Y=$(( ($2+$4)/2 ))
adb shell input swipe $X $Y $X $Y 1000; sleep 1.5
dump_ui "$ROW_DIR/plmenu.xml"; screencap "$ROW_DIR/plmenu.png"
echo "music_menu $(bounds "$ROW_DIR/plmenu.xml" music_menu) acrylic $(bounds "$ROW_DIR/plmenu.xml" acrylic:music_menu)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; screencap "$ROW_DIR/plmenu-closed.png"; dump_ui "$ROW_DIR/plmenu-closed.xml"
echo "closed has menu: $(has_node "$ROW_DIR/plmenu-closed.xml" music_menu)"
# ---- now playing
goto_pivot songs "$ROW_DIR/songs.xml"
SONG=$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')
tap_node "$ROW_DIR/songs.xml" "$SONG"; sleep 4
dump_ui "$ROW_DIR/np.xml"; screencap "$ROW_DIR/np.png"
grep -o 'resource-id="nowplaying_control[^"]*"' "$ROW_DIR/np.xml"
tap_node "$ROW_DIR/np.xml" nowplaying_control:more; sleep 1.5
dump_ui "$ROW_DIR/npmenu.xml"; screencap "$ROW_DIR/npmenu.png"
echo "np music_menu $(bounds "$ROW_DIR/npmenu.xml" music_menu) acrylic $(bounds "$ROW_DIR/npmenu.xml" acrylic:music_menu)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; screencap "$ROW_DIR/npmenu-closed.png"; dump_ui "$ROW_DIR/npmenu-closed.xml"
echo "np closed has menu: $(has_node "$ROW_DIR/npmenu-closed.xml" music_menu); np root: $(has_node "$ROW_DIR/npmenu-closed.xml" nowplaying_root)"
adb shell cmd media_session dispatch pause >/dev/null 2>&1
show_start 3
