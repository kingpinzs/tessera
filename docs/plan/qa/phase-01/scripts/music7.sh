#!/usr/bin/env bash
# PHASE 10 BUILD TASK 7 — the now-playing screen, against R8's measured values, plus Jeremy's
# 2026-09-22 pivot-header ask ("playing is cut off and should scroll into view when swiping right
# from songs and go out of view again when swiping left").
#
# The geometry is asserted in EPX, not pixels: 1 epx is 1/360 of the screen's width, and R8's whole
# point is that the bottom trio (nav 48 / chevron 16 / transport 56) is anchored to the nav bar's top
# edge and holds across canvases. So every check here is "N epx above the nav bar I actually measured",
# which is the claim R8 proved on two different device scales.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"
source "$(dirname "$0")/music_lib.sh"

EPX=3            # 1080-px emulator / 360 epx
box() { bounds "$1" "$2"; }           # "x1 y1 x2 y2"

cy_of() { python3 -c "import sys; b=sys.argv[1].split(); print((int(b[1])+int(b[3]))//2 if b else '')" "$(box "$1" "$2")"; }
cx_of() { python3 -c "import sys; b=sys.argv[1].split(); print((int(b[0])+int(b[2]))//2 if b else '')" "$(box "$1" "$2")"; }
edge()  { python3 -c "import sys; b=sys.argv[2].split(); print(b[int(sys.argv[1])] if b else '')" "$3" "$(box "$1" "$2")"; }
above_nav_epx() { python3 -c "import sys; print(round((int(sys.argv[1])-int(sys.argv[2]))/float(sys.argv[3]), 2))" "$1" "$2" "$EPX"; }

row_begin MUSIC7 "the now-playing screen against R8's measurements, and the scrolling pivot headers"

music_mute
music_fixtures
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1

# ---- Jeremy's pivot-header ask -------------------------------------------------------------------
# On songs the strip must look exactly as it did: all four headers, "playlists" clipped at the edge.
# On playlists it must be whole. The two states are what "scroll into view / out of view again" means.
music_open
goto_pivot songs "$ROW_DIR/pivot_songs.xml"
assert_contains "the pivot reaches songs" "pivot settled on songs" "$(diag music)"
adb exec-out screencap -p > "$ROW_DIR/pivot_songs.png"
SONGS_PL_R="$(edge "$ROW_DIR/pivot_songs.xml" music_pivot_header:playlists 2)"
SCREEN_W="$(python3 -c "print(360*$EPX)")"
note "on songs, the playlists header's right edge is at ${SONGS_PL_R}px of a ${SCREEN_W}px screen"
assert_ne "the playlists header is on the strip at all" "" "$SONGS_PL_R"
assert_eq "on songs it is still CUT OFF by the screen edge, as Jeremy has it now" ok \
  "$([ -n "$SONGS_PL_R" ] && [ "$SONGS_PL_R" -ge "$SCREEN_W" ] && echo ok || echo "right edge ${SONGS_PL_R}")"

goto_pivot playlists "$ROW_DIR/pivot_playlists.xml"
assert_contains "and reaches playlists" "pivot settled on playlists" "$(diag music)"
adb exec-out screencap -p > "$ROW_DIR/pivot_playlists.png"
PL_R="$(edge "$ROW_DIR/pivot_playlists.xml" music_pivot_header:playlists 2)"
# uiautomator CLAMPS a node's bounds to the screen, so a header pushed off the left reports x1 = 0
# rather than a negative number; its RIGHT edge is what moves and is what this measures.
ALB_R_SONGS="$(edge "$ROW_DIR/pivot_songs.xml" music_pivot_header:albums 2)"
ALB_R_PL="$(edge "$ROW_DIR/pivot_playlists.xml" music_pivot_header:albums 2)"
note "on playlists its right edge is ${PL_R}px; the albums header's right edge went ${ALB_R_SONGS} -> ${ALB_R_PL}"
assert_eq "swiping to playlists scrolls it fully into view" ok \
  "$([ -n "$PL_R" ] && [ "$PL_R" -lt "$SCREEN_W" ] && echo ok || echo "right edge ${PL_R}")"
assert_eq "and the whole strip moved to do it, taking albums off the left" ok \
  "$([ -n "$ALB_R_PL" ] && [ "$ALB_R_PL" -lt "$ALB_R_SONGS" ] && echo ok || echo "${ALB_R_SONGS} -> ${ALB_R_PL}")"

goto_pivot songs "$ROW_DIR/pivot_back.xml"
BACK_PL_R="$(edge "$ROW_DIR/pivot_back.xml" music_pivot_header:playlists 2)"
note "back on songs, the playlists header's right edge is ${BACK_PL_R}px again"
assert_eq "going back to songs puts it out of view again, not fully, exactly as before" "$SONGS_PL_R" "$BACK_PL_R"

# ---- open now-playing ------------------------------------------------------------------------------
goto_pivot songs "$ROW_DIR/songs.xml"
SONG=$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')
assert_ne "there is a song to open" "" "$SONG"
[ -n "$SONG" ] && tap_id "$ROW_DIR/songs.xml" "$SONG"
command sleep 4
dump "$ROW_DIR/np.xml"
adb exec-out screencap -p > "$ROW_DIR/np.png"
assert_contains "a tap on a track opens the now-playing screen" "nowplaying_root" "$(cat "$ROW_DIR/np.xml")"

# ---- R8 section 1.2: the header row ----------------------------------------------------------------
assert_eq "the header title is R8's, all caps" "NOW PLAYING" "$(node_text "$ROW_DIR/np.xml" nowplaying_title)"
TITLE_L="$(edge "$ROW_DIR/np.xml" nowplaying_title 0)"
note "header title left edge: ${TITLE_L}px = $(python3 -c "print(round($TITLE_L/$EPX,2))") epx (R8: 61.0)"
assert_within "the title starts at R8's 61 epx" 61 "$(python3 -c "print($TITLE_L/$EPX)")" 2
BACK_CX="$(cx_of "$ROW_DIR/np.xml" nowplaying_back)"
assert_within "the hamburger's centre is at R8's 24 epx" 24 "$(python3 -c "print($BACK_CX/$EPX)")" 3
SEARCH_CX="$(cx_of "$ROW_DIR/np.xml" nowplaying_search)"
assert_within "the search glyph's centre is at R8's 336 epx (360 - 24)" 336 "$(python3 -c "print($SEARCH_CX/$EPX)")" 3

# ---- R8 section 1.3: the album art -----------------------------------------------------------------
ART_L="$(edge "$ROW_DIR/np.xml" nowplaying_art 0)"
ART_R="$(edge "$ROW_DIR/np.xml" nowplaying_art 2)"
ART_T="$(edge "$ROW_DIR/np.xml" nowplaying_art 1)"
CHROME_B="$(edge "$ROW_DIR/np.xml" nowplaying_chrome 3)"
note "art [$ART_L..$ART_R] top $ART_T, chrome bottom $CHROME_B"
assert_within "V-2016's 16-epx left margin" 16 "$(python3 -c "print($ART_L/$EPX)")" 1
assert_within "V-2016's 16-epx right margin" 16 "$(python3 -c "print((360*$EPX-$ART_R)/$EPX)")" 1
assert_eq "the art is flush under the chrome, with no seam" ok \
  "$([ "$ART_T" = "$CHROME_B" ] && echo ok || echo "art top $ART_T vs chrome bottom $CHROME_B")"

# ---- R8 section 1.4: the metadata block ------------------------------------------------------------
assert_ne "the track title is drawn" "" "$(node_text "$ROW_DIR/np.xml" nowplaying_track)"
ARTIST_LINE="$(node_text "$ROW_DIR/np.xml" nowplaying_artist)"
note "second line: [$ARTIST_LINE]"
# V-2016's own change, and the reason the phase doc ruled for it: the second line carries the album.
assert_contains "the second line is Artist - dot - Album, which is V-2016" " • " "$ARTIST_LINE"
TRACK_L="$(edge "$ROW_DIR/np.xml" nowplaying_track 0)"
assert_within "both metadata lines start at R8's 12-epx origin" 12 "$(python3 -c "print($TRACK_L/$EPX)")" 2

# ---- R8 sections 1.5 / 1.6 / 1.7: everything anchored to the nav bar's top edge ----------------------
NAV_T="$(edge "$ROW_DIR/np.xml" w10m_nav_bar 1)"
assert_ne "the shell's nav bar is drawn, which everything below is measured from" "" "$NAV_T"
SCRUB_CY="$(cy_of "$ROW_DIR/np.xml" nowplaying_scrubber)"
TRANS_CY="$(cy_of "$ROW_DIR/np.xml" nowplaying_control:play_pause)"
CHEV_CY="$(cy_of "$ROW_DIR/np.xml" nowplaying_chevron)"
note "above the nav bar, in epx — scrubber $(above_nav_epx "$NAV_T" "$SCRUB_CY") · transport $(above_nav_epx "$NAV_T" "$TRANS_CY") · chevron $(above_nav_epx "$NAV_T" "$CHEV_CY")"
assert_within "the scrubber row sits 96 epx above the nav bar (V-2016)" 96 "$(above_nav_epx "$NAV_T" "$SCRUB_CY")" 2
assert_within "the transport row sits 56 epx above it" 56 "$(above_nav_epx "$NAV_T" "$TRANS_CY")" 2
assert_within "the chevron sits 16 epx above it" 16 "$(above_nav_epx "$NAV_T" "$CHEV_CY")" 2

# R8 section 1.6: six equal cells of W/6, so the centres land on 30 / 90 / 150 / 210 / 270 / 330 epx.
i=0
for c in previous play_pause next repeat shuffle more; do
  CX="$(cx_of "$ROW_DIR/np.xml" "nowplaying_control:$c")"
  WANT=$(python3 -c "print(30 + 60*$i)")
  assert_within "the $c control's centre is at $WANT epx" "$WANT" "$(python3 -c "print($CX/$EPX)")" 2
  i=$((i + 1))
done
CHEV_CX="$(cx_of "$ROW_DIR/np.xml" nowplaying_chevron)"
assert_within "the chevron is at exactly half the width" 180 "$(python3 -c "print($CHEV_CX/$EPX)")" 2

# ---- the scrubber is live, and the right label is the TOTAL, not the remaining ----------------------
E1="$(node_text "$ROW_DIR/np.xml" nowplaying_elapsed)"; T1="$(node_text "$ROW_DIR/np.xml" nowplaying_total)"
command sleep 2
dump "$ROW_DIR/np2.xml"
E2="$(node_text "$ROW_DIR/np2.xml" nowplaying_elapsed)"; T2="$(node_text "$ROW_DIR/np2.xml" nowplaying_total)"
note "elapsed $E1 -> $E2 · total $T1 -> $T2"
assert_ne "the elapsed label advances" "$E1" "$E2"
assert_eq "the right label does NOT, because it is the total duration (R8 1.5, HIGH)" "$T1" "$T2"

# ---- the transport actually drives the session ------------------------------------------------------
tap_id "$ROW_DIR/np2.xml" nowplaying_control:play_pause; command sleep 2
adb shell dumpsys media_session > "$ROW_DIR/paused.txt" 2>&1
PAUSED="$(awk '/package=app\.tileshell/ { f = 1 } f && /state=PlaybackState/ { print; exit }' "$ROW_DIR/paused.txt")"
note "after play/pause: $PAUSED"
assert_contains "the play-pause control pauses the session" "PAUSED(2)" "$PAUSED"
dump "$ROW_DIR/paused.xml"
tap_id "$ROW_DIR/paused.xml" nowplaying_control:play_pause; command sleep 2
adb shell dumpsys media_session > "$ROW_DIR/resumed.txt" 2>&1
assert_contains "and starts it again" "PLAYING(3)" \
  "$(awk '/package=app\.tileshell/ { f = 1 } f && /state=PlaybackState/ { print; exit }' "$ROW_DIR/resumed.txt")"

dump "$ROW_DIR/before_next.xml"
BEFORE="$(node_text "$ROW_DIR/before_next.xml" nowplaying_track)"
tap_id "$ROW_DIR/before_next.xml" nowplaying_control:next; command sleep 3
dump "$ROW_DIR/after_next.xml"
AFTER="$(node_text "$ROW_DIR/after_next.xml" nowplaying_track)"
note "next: [$BEFORE] -> [$AFTER]"
assert_ne "next moves to another track" "$BEFORE" "$AFTER"

tap_id "$ROW_DIR/after_next.xml" nowplaying_control:shuffle; command sleep 1
assert_contains "the shuffle control reaches the player" "shuffle on" "$(diag music)"
dump "$ROW_DIR/shuffled.xml"
tap_id "$ROW_DIR/shuffled.xml" nowplaying_control:repeat; command sleep 1
assert_contains "and so does repeat, which is three-state" "repeat all" "$(diag music)"

# ---- R8 section 1.9: the chevron opens the queue -----------------------------------------------------
dump "$ROW_DIR/pre_queue.xml"
tap_id "$ROW_DIR/pre_queue.xml" nowplaying_chevron; command sleep 2
dump "$ROW_DIR/queue.xml"
adb exec-out screencap -p > "$ROW_DIR/queue.png"
assert_contains "the chevron opens the play queue" "nowplaying_queue" "$(cat "$ROW_DIR/queue.xml")"
# The needle is the whole resource-id: "nowplaying_art" is a prefix of "nowplaying_artist", and the
# first run of this row both failed and passed against the metadata line rather than the artwork.
assert_absent "and the art is gone, because album mode removes it entirely" 'resource-id="nowplaying_art"' "$(cat "$ROW_DIR/queue.xml")"
Q_TOP="$(edge "$ROW_DIR/queue.xml" nowplaying_track 1)"
note "expanded, the title band starts at ${Q_TOP}px = $(python3 -c "print(round($Q_TOP/$EPX,1))") epx"
assert_eq "the block moved UP to sit under the chrome" ok \
  "$([ -n "$Q_TOP" ] && [ "$Q_TOP" -lt $((200 * EPX)) ] && echo ok || echo "title top ${Q_TOP}px")"
R0="$(edge "$ROW_DIR/queue.xml" nowplaying_queue_row:0 1)"
R1="$(edge "$ROW_DIR/queue.xml" nowplaying_queue_row:1 1)"
if [ -n "$R0" ] && [ -n "$R1" ]; then
  note "queue row pitch: $((R1 - R0))px = $(python3 -c "print(round(($R1-$R0)/$EPX,1))") epx (R8: 61.5)"
  assert_within "the queue rows are at R8's 61.5-epx pitch" 61.5 "$(python3 -c "print(($R1-$R0)/$EPX)")" 1.5
else
  assert_eq "two queue rows are drawn to measure the pitch between" ok "row0=$R0 row1=$R1"
fi
assert_contains "a queue row carries Artist - dot - Album in grey" " • " "$(node_text "$ROW_DIR/queue.xml" nowplaying_queue_sub:0)"
tap_id "$ROW_DIR/queue.xml" nowplaying_chevron; command sleep 2
dump "$ROW_DIR/collapsed.xml"
assert_absent "and it closes again" "nowplaying_queue" "$(cat "$ROW_DIR/collapsed.xml")"
assert_contains "with the art back" 'resource-id="nowplaying_art"' "$(cat "$ROW_DIR/collapsed.xml")"

# ---- back returns to the collection, and the music keeps going ---------------------------------------
adb shell input keyevent KEYCODE_BACK; command sleep 2
dump "$ROW_DIR/back.xml"
assert_contains "Back returns to the collection" "music_pivot_header:albums" "$(cat "$ROW_DIR/back.xml")"
adb shell dumpsys media_session > "$ROW_DIR/still.txt" 2>&1
assert_contains "and the session is still playing behind it" "PLAYING(3)" \
  "$(awk '/package=app\.tileshell/ { f = 1 } f && /state=PlaybackState/ { print; exit }' "$ROW_DIR/still.txt")"

row_end
