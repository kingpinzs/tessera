#!/usr/bin/env bash
# Phase 17 E23, the Movies & TV half (row E23_VIDEO) — App Shortcuts (phase 11 Q1's standing rule; C-8 / C-9), with the
# Music-tile negative and the server set-up INSIDE the row (r3 V22). The doc's clauses → this driver's legs:
#
#   start       layout_restore of qa/phase-17/baseline_layout.json: the Movies & TV tile is pinned there
#   no server   the tile held (phase 11 E3's method: phase 16 e25.sh's burst) → "My videos", "Browse" (2 satellites,
#               no quick_sat:2); `[quick] shortcuts for app.tileshell/.video.VideoActivity/0: 2 (2 shown:
#               video_myvideos,video_browse)`; `dumpsys shortcut`: video_myvideos rank 0, video_browse rank 1, manifest
#               shortcuts of VideoActivity, no video_mediaserver; a tap on each → VideoActivity with that pane row
#               current (hub_pane:myvideos / :browse)
#   server      this row's own set-up — E22's container and "Add a server" — inside the egress guard → 3 satellites,
#               "Media server" last; `… VideoActivity/0: 3 (3 shown: video_myvideos,video_browse,video_mediaserver)`;
#               `dumpsys shortcut` lists video_mediaserver as a DYNAMIC shortcut whose activity is .video.VideoActivity;
#               `[video] shortcut mediaserver published`
#   Music       then the Music tile held → `[quick] shortcuts for app.tileshell/.music.MusicActivity/0: 4 (4 shown:
#               songs,albums,artists,playlists)` with no dynamic id in it (C-21)
#   remove      the server removed → back to 2 and `[video] shortcut mediaserver removed`
#   C-6         c6 + ensure_start between holds (r3 V7)
#
# Restores: the server removed, the container and its volumes, the guard, the layout (layout_restore).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"
trap video_cleanup EXIT
VTILE="tile:app:app.tileshell/app.tileshell.video.VideoActivity:0"; MTILE="tile:slot:MUSIC"
LINE2="[quick] shortcuts for $VIDEO_ACTIVITY/0: 2 (2 shown: video_myvideos,video_browse)"
LINE3="[quick] shortcuts for $VIDEO_ACTIVITY/0: 3 (3 shown: video_myvideos,video_browse,video_mediaserver)"
MLINE="[quick] shortcuts for app.tileshell/.music.MusicActivity/0: 4 (4 shown: songs,albums,artists,playlists)"
VA_FLAT="app.tileshell/app.tileshell.video.VideoActivity"

# Phase 11 E3's method (phase 16 e25.sh): the tile's centre held 1.0 s with the finger still down, the dump taken
# while it is down, then released; the burst stays open for the tap on a satellite.
burst_on() { # tile-id tag
  local b x y
  gdump "$D/$2-rest.xml" || true
  [ "$(has_node "$D/$2-rest.xml" "$1")" = yes ] || { adb shell input swipe 540 1700 540 900 400; sleep 1; gdump "$D/$2-rest.xml" || true; }
  b="$(bounds "$D/$2-rest.xml" "$1")"
  [ -n "$b" ] || { note "burst_on: no $1 on Start"; return 1; }
  # shellcheck disable=SC2086
  set -- "$1" "$2" $b
  x=$(( ($3 + $5) / 2 )); y=$(( ($4 + $6) / 2 ))
  adb shell input motionevent DOWN "$x" "$y"; sleep 1.0
  gdump "$D/$2.xml" || true; screencap "$D/$2.png"
  adb shell input motionevent UP "$x" "$y"; sleep 0.8
}
labels() { local i out=""; for i in 0 1 2 3; do [ "$(has_node "$1" "quick_sat_label:$i")" = yes ] && out="$out$(node_text "$1" "quick_sat_label:$i"),"; done; echo "${out%,}"; }
sats() { grep -o 'resource-id="quick_sat:[0-9]*"' "$1" | sort -u | grep -c .; }
quick_line() { ring_since "$1" | grep -F '[quick] shortcuts for' | tail -1 | sed 's/.*\[quick\]/[quick]/'; }
# One hold of the Movies & TV tile: the labels, the satellite count, the ring line; optionally a tap on satellite i.
hold_video() { # tag expected-labels expected-count expected-line [satellite]
  local mark; mark="$(ring_mark)"
  burst_on "$VTILE" "$1" || { _verdict FAIL "$1: the burst opened" "the Movies & TV tile was not found on Start"; return 1; }
  assert_eq "$1: the start page is the one read" "yes" "$(has_node "$D/$1-rest.xml" start_page)"
  assert_eq "$1: the burst's labels in rank order" "$2" "$(labels "$D/$1.xml")"
  assert_eq "$1: the satellites (quick_sat:<i>) in the burst" "$3" "$(sats "$D/$1.xml")"
  assert_eq "$1: no quick_sat:$3 (none past the last)" "no" "$(has_node "$D/$1.xml" "quick_sat:$3")"
  assert_eq "$1: the burst's ring line" "$4" "$(quick_line "$mark")"
  if [ -n "${5:-}" ]; then tap_node "$D/$1.xml" "quick_sat:$5"; sleep 4; else adb shell input keyevent KEYCODE_BACK; sleep 1; fi
}

video_row_begin E23_VIDEO "App Shortcuts, Movies & TV: two static, Media server dynamic while a server is set up, the Music tile untouched"
layout_restore "$BASELINE" > "$D/restore-0.out" 2>&1; assert_eq "layout_restore of the baseline" "0" "$?"
ensure_start
assert_absent "precondition: no server is set up" "jellyfin" "$(cred_names)"

# ------------------------------------------------------------------------------------------------ no server
log "--- no server: My videos, Browse"
hold_video v2-myvideos "My videos,Browse" 2 "$LINE2" 0
assert_eq "My videos: VideoActivity is resumed" "$VIDEO_ACTIVITY" "$(top_activity)"
assert_eq "My videos: its pane row is current (hub_pane:myvideos)" "myvideos" "$(pane_current "$D/v2-myvideos-app")"
c6; ensure_start
hold_video v2-browse "My videos,Browse" 2 "$LINE2" 1
assert_eq "Browse: VideoActivity is resumed" "$VIDEO_ACTIVITY" "$(top_activity)"
assert_eq "Browse: its pane row is current (hub_pane:browse)" "browse" "$(pane_current "$D/v2-browse-app")"
c6; ensure_start
shortcut_table "$D/shortcuts-none.tsv"
of() { awk -F'\t' -v a="$VA_FLAT" '$1==a {print $2 "=" $3}' "$1" | sort | tr '\n' ' ' | sed 's/ $//'; }
flags_of() { awk -F'\t' -v i="$2" '$3==i {print $4}' "$1"; }
assert_eq "dumpsys shortcut: video_myvideos rank 0, video_browse rank 1, and no video_mediaserver" "0=video_myvideos 1=video_browse" "$(of "$D/shortcuts-none.tsv")"
assert_contains "dumpsys shortcut: video_myvideos is a manifest shortcut" "Man" "$(flags_of "$D/shortcuts-none.tsv" video_myvideos)"
assert_contains "dumpsys shortcut: video_browse is a manifest shortcut" "Man" "$(flags_of "$D/shortcuts-none.tsv" video_browse)"
assert_eq "dumpsys shortcut: no video_ id on another activity" "0" "$(awk -F'\t' -v a="$VA_FLAT" '$1!=a && $3 ~ /^video_/' "$D/shortcuts-none.tsv" | grep -c .)"

# ------------------------------------------------------------------------------------------------ the server
log "--- this row's own server set-up, inside the egress guard"
jf_up || { _verdict FAIL "the Jellyfin fixture" "did not come up"; }
guard_on
guard_restart "start"
server_add "$D/add"
assert_contains "[video] shortcut mediaserver published" "[video] shortcut mediaserver published" "$SERVER_SLICE"
c6; ensure_start
hold_video v3 "My videos,Browse,Media server" 3 "$LINE3" 2
record "the third satellite's tap: on top, and the pane's current row" "$(top_activity) $(pane_current "$D/v3-app")"
c6; ensure_start
shortcut_table "$D/shortcuts-server.tsv"
assert_eq "dumpsys shortcut: the three ids on VideoActivity" "0=video_myvideos 1=video_browse 2=video_mediaserver" "$(of "$D/shortcuts-server.tsv" | sed 's/[0-9]*=video_mediaserver/2=video_mediaserver/')"
record "dumpsys shortcut: video_mediaserver's rank and flags" "$(awk -F'\t' '$3=="video_mediaserver" {print $2 " [" $4 "]"}' "$D/shortcuts-server.tsv")"
assert_contains "dumpsys shortcut: video_mediaserver is a dynamic shortcut" "Dyn" "$(flags_of "$D/shortcuts-server.tsv" video_mediaserver)"
assert_eq "… whose activity is .video.VideoActivity" "$VA_FLAT" "$(awk -F'\t' '$3=="video_mediaserver" {print $1}' "$D/shortcuts-server.tsv")"

# ------------------------------------------------------------------------------------------------ the Music tile
log "--- the Music tile, while the dynamic shortcut exists"
MARK="$(ring_mark)"
burst_on "$MTILE" music || _verdict FAIL "music: the burst opened" "the Music tile was not found on Start"
ML="$(quick_line "$MARK")"
assert_eq "the Music tile's ring line: its four, no dynamic id (C-21)" "$MLINE" "$ML"
assert_absent "… no video_ id in it" "video_" "$ML"
assert_eq "the Music tile's burst holds no Media server label" "Songs,Albums,Artists,Playlists" "$(labels "$D/music.xml")"
adb shell input keyevent KEYCODE_BACK; sleep 1
c6; ensure_start

# ------------------------------------------------------------------------------------------------ remove
log "--- the server removed"
server_page "$D/rm"; dump_ui "$D/rm.xml"
MARK="$(ring_mark)"
tap_node "$D/rm.xml" server_remove; sleep 1.8
assert_contains "removed: [video] shortcut mediaserver removed" "[video] shortcut mediaserver removed" "$(vring "$MARK")"
c6; ensure_start
hold_video v2-again "My videos,Browse" 2 "$LINE2"
shortcut_table "$D/shortcuts-after.tsv"
assert_eq "dumpsys shortcut: back to the two static ones" "0=video_myvideos 1=video_browse" "$(of "$D/shortcuts-after.tsv")"

log "--- restore"
assert_absent "the server is removed from the store" "jellyfin" "$(cred_names)"
assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
guard_off
jf_down
rings_save
layout_restore "$BASELINE" > "$D/restore-1.out" 2>&1; assert_eq "restore: layout_restore of the baseline" "0" "$?"
ensure_start
leak_scan_row "$SERVER_PW"
row_end
