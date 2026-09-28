#!/usr/bin/env bash
# L13-8 regression row (INDEX ledger): Music's drawn Back and the system Back disagreed. With Music's jump grid open the
# drawn Back closed Music (its own chain did not know the grid); with Now Playing's more menu open it left Now Playing
# instead of closing the menu. The fix routes the drawn Back through the activity's back dispatcher, as the Clock does,
# so each case must end where the system Back ends. Each case runs with the drawn Back and, as the reference, the
# system Back; the controls (nothing open) show that leaving still works.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
P01="$(cd "$(dirname "$0")/../../phase-01/scripts" && pwd)"
. "$P01/ui.sh"
. "$P01/music_lib.sh"

where() { # dump -> grid | menu | nowplaying | collection | gone
  python3 - "$1" <<'PY'
import sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
if 'resource-id="music_jump_grid"' in s: print("grid")
elif 'resource-id="music_menu"' in s: print("menu")
elif 'resource-id="nowplaying_root"' in s: print("nowplaying")
elif 'resource-id="music_root"' in s: print("collection")
else: print("gone")
PY
}
tap_tag() { local b; b="$(bounds "$1" "$2")"; [ -n "$b" ] || { note "tap_tag: $2 not in $(basename "$1")"; return 1; }
  set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }
back() { # how(drawn|system) dump — the drawn Back is the Music nav bar's nav_back
  if [ "$1" = drawn ]; then tap_tag "$2" nav_back; else adb shell input keyevent KEYCODE_BACK; fi
}

row_begin L13_8 "Music's drawn Back ends where the system Back ends (jump grid, now playing's more menu, and nothing open)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
music_mute; music_fixtures
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO > /dev/null 2>&1

for how in system drawn; do
  # (1) the jump grid open: Back closes the grid, Music stays
  music_open; goto_pivot songs "$ROW_DIR/$how-1-songs.xml" > /dev/null 2>&1
  H="$(grep -o 'resource-id="music_header:[^"]*"' "$ROW_DIR/$how-1-songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
  tap_tag "$ROW_DIR/$how-1-songs.xml" "$H"; sleep 1.2
  dump_ui "$ROW_DIR/$how-1-grid.xml"
  assert_eq "$how (1): the jump grid is open" "grid" "$(where "$ROW_DIR/$how-1-grid.xml")"
  back $how "$ROW_DIR/$how-1-grid.xml"; sleep 1.5
  dump_ui "$ROW_DIR/$how-1-after.xml"
  assert_eq "$how (1): Back closed the grid and Music stayed" "collection" "$(where "$ROW_DIR/$how-1-after.xml")"

  # (2) now playing's more menu open: Back closes the menu, now playing stays. It starts from a fresh Music, so it
  # does not depend on how (1) ended.
  music_open; goto_pivot songs "$ROW_DIR/$how-2-songs.xml" > /dev/null 2>&1
  S1="$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/$how-2-songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
  tap_tag "$ROW_DIR/$how-2-songs.xml" "$S1"; sleep 3
  dump_ui "$ROW_DIR/$how-2-np.xml"
  tap_tag "$ROW_DIR/$how-2-np.xml" nowplaying_control:more; sleep 1.2
  dump_ui "$ROW_DIR/$how-2-menu.xml"
  assert_eq "$how (2): now playing's more menu is open" "menu" "$(where "$ROW_DIR/$how-2-menu.xml")"
  back $how "$ROW_DIR/$how-2-menu.xml"; sleep 1.5
  dump_ui "$ROW_DIR/$how-2-after.xml"
  assert_eq "$how (2): Back closed the menu and now playing stayed" "nowplaying" "$(where "$ROW_DIR/$how-2-after.xml")"

  # (3) control, nothing open on now playing: Back returns to the collection
  back $how "$ROW_DIR/$how-2-after.xml"; sleep 1.5
  dump_ui "$ROW_DIR/$how-3-after.xml"
  assert_eq "$how (3): Back from now playing returns to the collection" "collection" "$(where "$ROW_DIR/$how-3-after.xml")"

  # (4) control, nothing open on the collection: Back leaves Music
  back $how "$ROW_DIR/$how-3-after.xml"; sleep 1.5
  dump_ui "$ROW_DIR/$how-4-after.xml"
  assert_eq "$how (4): Back from the collection leaves Music" "gone" "$(where "$ROW_DIR/$how-4-after.xml")"
done
adb shell am force-stop $PKG; sleep 1
show_start 3
row_end
