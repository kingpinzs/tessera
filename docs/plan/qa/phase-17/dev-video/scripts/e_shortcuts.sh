#!/usr/bin/env bash
# Phase 17 development proof, build task 15 (brief E; E23's Movies & TV half without the tile burst, which is the
# launcher's): the two static shortcuts are manifest shortcuts of VideoActivity in rank order, each opens its page with
# its pane row current, and with no server set up there is no video_mediaserver and its page falls back to My videos.
. "$(dirname "$0")/v17.sh"
row_begin E_SHORTCUTS "App Shortcuts: My videos and Browse static, Media server only while a server is set up"
D="$ROW_DIR"
adb shell am force-stop app.tileshell
assert_absent "no server is set up for this row" "jellyfin" "$(cred_names)"
SC="$(shortcut_dump)"; echo "$SC" > "$D/shortcuts.txt"
record "the shell's video shortcut ids in dumpsys shortcut" "$(echo "$SC" | grep -oE 'id=video_[a-z]+' | sort -u | xargs)"
assert_contains "video_myvideos is listed" "id=video_myvideos" "$SC"
assert_contains "video_browse is listed" "id=video_browse" "$SC"
assert_absent "no video_mediaserver with no server" "id=video_mediaserver" "$SC"
detail() { echo "$SC" | grep -A16 "id=$1" | tr '\n' ' '; }
assert_contains "video_myvideos: rank 0, of VideoActivity" "rank=0" "$(detail video_myvideos | grep -o 'rank=[0-9]*' | head -1)"
assert_contains "video_browse: rank 1" "rank=1" "$(detail video_browse | grep -o 'rank=[0-9]*' | head -1)"
assert_contains "video_myvideos belongs to VideoActivity" "app.tileshell.video.VideoActivity" "$(detail video_myvideos)"
record "video_myvideos flags (Man = a manifest shortcut)" "$(detail video_myvideos | grep -oE 'flags=[^ ,]*( \[[^]]*\])?' | head -1)"
pane_current() { # page -> the pane row that is selected
  hub "$1" 2.5; dump_ui "$D/$1.xml"; tap_node "$D/$1.xml" hub_menu; sleep 0.8; dump_ui "$D/$1-pane.xml"
  grep -o '<node[^>]*resource-id="hub_pane:[a-z]*"[^>]*>' "$D/$1-pane.xml" | grep 'selected="true"' | sed 's/.*resource-id="hub_pane:\([a-z]*\)".*/\1/' | xargs
  adb shell input tap 960 1200; sleep 0.5
}
assert_eq "the My videos shortcut's intent: its pane row is current" "myvideos" "$(pane_current myvideos)"
assert_eq "…and its page is shown" "yes" "$(has_node "$D/myvideos.xml" hub_page:myvideos)"
assert_eq "the Browse shortcut's intent: its pane row is current" "browse" "$(pane_current browse)"
assert_eq "…and its page is shown" "yes" "$(has_node "$D/browse.xml" hub_page:browse)"
assert_eq "the Media server page with no server falls back to My videos" "myvideos" "$(pane_current mediaserver)"
adb shell am force-stop app.tileshell; ensure_start
row_end
