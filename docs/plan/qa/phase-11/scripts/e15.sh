#!/usr/bin/env bash
# E15 — the shell's own tiles (Q1 A; T11-12, T11-22, T11-37): Music bursts with its four pivots and a satellite
# lands on that pivot; Start settings bursts with its four pages and a satellite opens that page; Weather and Tess
# declare none. Each tile's four are its own, never the other's (the per-activity query).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E15 "the shell's own tiles: Music's pivots, Start settings' pages; Weather and Tess none"
seed_fixtures
restore baseline_layout.json
held_dump() { # tile id tag
  qdump "$ROW_DIR/$2-rest.xml"
  read -r X Y <<< "$(center "$ROW_DIR/$2-rest.xml" "$1")"
  hold_down "$X" "$Y"; sleep 1.0; qdump "$ROW_DIR/$2.xml"; hold_up "$X" "$Y"; sleep 0.8
}
labels() { for i in 0 1 2 3; do node_text "$1" "quick_sat_label:$i"; done | tr '\n' ',' ; }

log "--- Music ---"
MARK="$(ring_mark)"; held_dump tile:slot:MUSIC music
assert_eq "Music's labels (ranks 0-3)" "Songs,Albums,Artists,Playlists," "$(labels "$ROW_DIR/music.xml")"
S="$(quick_since "$MARK")"
assert_contains "Music's line (T11-12)" "[quick] shortcuts for app.tileshell/.music.MusicActivity/0: 4 (4 shown: songs,albums,artists,playlists)" "$S"
assert_absent "no line naming Start settings" "app.tileshell/.settings.SettingsActivity" "$S"
tap_node "$ROW_DIR/music.xml" quick_sat:1; sleep 4
assert_contains "MusicActivity resumed" "app.tileshell/.music.MusicActivity" "$(resumed)"
qdump "$ROW_DIR/music-landed.xml"
sel="$(for p in albums artists songs playlists; do grep -oE "resource-id=\"music_pivot_header:$p\"[^>]*" "$ROW_DIR/music-landed.xml" | grep -oE 'selected="(true|false)"' | head -1; done | tr '\n' ' ')"
note "selected: albums artists songs playlists = $sel"
assert_eq "the albums pivot is the selected one" 'selected="true" selected="false" selected="false" selected="false" ' "$sel"
c6

log "--- Start settings ---"
MARK="$(ring_mark)"; held_dump tile:shell:settings settings
assert_eq "Start settings' labels" "Start + theme,Tile apps,Setup,Diagnostics," "$(labels "$ROW_DIR/settings.xml")"
S="$(quick_since "$MARK")"
assert_contains "Start settings' line (T11-12)" "[quick] shortcuts for app.tileshell/.settings.SettingsActivity/0: 4 (4 shown: start_theme,tile_apps,checklist,diagnostics)" "$S"
assert_absent "no line naming Music" "app.tileshell/.music.MusicActivity" "$S"
tap_node "$ROW_DIR/settings.xml" quick_sat:3; sleep 3.5
assert_contains "SettingsActivity resumed" "app.tileshell/.settings.SettingsActivity" "$(resumed)"
qdump "$ROW_DIR/settings-landed.xml"
assert_eq "the page header reads DIAGNOSTICS (T11-37)" DIAGNOSTICS "$(subtree_text "$ROW_DIR/settings-landed.xml" page_header)"
assert_eq "the hub's own Diagnostics row is absent (the extra was honoured)" no "$(has_node "$ROW_DIR/settings-landed.xml" settings_diagnostics)"
c6

log "--- Weather and Tess: none ---"
for t in weather cortana; do
  MARK="$(ring_mark)"; held_dump "tile:shell:$t" "$t"
  assert_eq "$t: edit mode on" yes "$(has_node "$ROW_DIR/$t.xml" edit_disc:unpin)"
  assert_eq "$t: no quick_burst" no "$(has_node "$ROW_DIR/$t.xml" quick_burst)"
  assert_contains "$t: no shortcuts" "[quick] no burst on shell:$t: no shortcuts" "$(quick_since "$MARK")"
  c6
done
row_end
