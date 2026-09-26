#!/usr/bin/env bash
# E12 Diagnostics (phase 13 Acceptance E12, T13-22): from the SAVED, action-scoped `ring_since MARK` slices of E1-E5's
# runs, each surface's show action holds its own `[fluent] <surface> source=... tint=... alpha=0.8 blur=30epx` line, and
# E1's reasons appear in its saved step order. A missing slice fails the row; nothing is read from an unsliced ring.
# Builds (INDEX Change Log 2026-09-26, the specific-tests ruling): E1, E2 and E4 ran on apk 0eb36905 (the build of
# 9c4d049f); E3 and E5 on apk 15aa7f5f (the build of c73c32cf), counted because the diff between those two commits does
# not touch what E12 counts (L13-2's five files and the static layer's StaticBackdrop.kt: no reminder-menu or pane code,
# no [fluent] show line) — the row asserts that diff's file list itself, so a wider diff fails it.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin E12 "diagnostics: every surface's show line in its own saved slice, E1's reasons in step order"
CUR=0eb36905; OLD=15aa7f5f; CUR_C=9c4d049f; OLD_C=c73c32cf

built() { grep -m1 '^apk built' "$QA/$1/$1.txt" 2>/dev/null | awk '{print substr($3,1,8)}'; }
failed_of() { tail -1 "$QA/$1/$1.txt" 2>/dev/null | grep -oE '[0-9]+ failed' | cut -d' ' -f1; }
for r in E1 E2 E4; do
  assert_eq "$r ran on the current build" "$CUR" "$(built $r)"
  assert_eq "$r's own run had no FAIL" "0" "$(failed_of $r)"
done
for r in E3 E5; do
  assert_eq "$r ran on $OLD (the diff below does not touch it)" "$OLD" "$(built $r)"
  assert_eq "$r's own run had no FAIL" "0" "$(failed_of $r)"
done
DIFF="$(git -C "$REPO" diff --name-only "$OLD_C..$CUR_C" -- app | sort | tr '\n' ' ')"
note "app files changed $OLD_C..$CUR_C: $DIFF"
assert_eq "the $OLD -> $CUR diff is L13-2's five files, StaticBackdrop.kt and the shared decode only (no pane or reminder-menu code)" \
  "app/src/main/kotlin/app/tileshell/applist/AppListMenu.kt app/src/main/kotlin/app/tileshell/applist/AppListPage.kt app/src/main/kotlin/app/tileshell/music/MusicCollectionPage.kt app/src/main/kotlin/app/tileshell/start/BackgroundDecoder.kt app/src/main/kotlin/app/tileshell/start/SingleFlight.kt app/src/main/kotlin/app/tileshell/ui/components/ModalOverlay.kt app/src/main/kotlin/app/tileshell/ui/fluent/StaticBackdrop.kt app/src/test/kotlin/app/tileshell/start/SingleFlightTest.kt app/src/test/kotlin/app/tileshell/ui/components/ModalOverlayTest.kt app/src/test/kotlin/app/tileshell/ui/fluent/StaticBackdropTest.kt " \
  "$DIFF"

# <row> <slice file> <surface> <tint>: the slice exists and holds the surface's show line.
show() {
  local f="$QA/$1/$2"
  assert_eq "$1 $2 is saved" "yes" "$([ -s "$f" ] && echo yes || echo no)"
  assert_contains "$1 $2: the $3 show line" "[fluent] $3 source=$4 tint=($5) alpha=0.8 blur=30epx" "$(cat "$f" 2>/dev/null)"
}
show E1 slice-1b-applist-show.txt applist static 0,0,0
show E2 slice-applist-show-fhd.txt applist static 0,0,0
show E2 slice-applist-show-hd.txt applist static 0,0,0
show E3 slice-menu-tomorrow.txt reminder_menu live 47,45,47
show E3 slice-menu-photo.txt reminder_menu live 47,45,47
show E4 slice-applist-menu.txt applist_menu live 0,0,0
show E4 slice-music-menu-pivot.txt music_menu live 0,0,0
show E4 slice-music-menu-nowplaying.txt music_menu live 0,0,0
show E5 slice-pane-home.txt cortana_pane live 18,24,16
show E5 slice-pane-reminders.txt cortana_pane live 18,24,16

# E1's reasons, one saved slice per step, in the order the steps ran (walls strictly increasing).
PREV=0
step() { # file expected-line
  local f="$QA/E1/slice-$1.txt" w
  assert_eq "E1 slice-$1 is saved" "yes" "$([ -s "$f" ] && echo yes || echo no)"
  assert_contains "E1 step $1: $2" "[fluent] $2" "$(cat "$f" 2>/dev/null)"
  w="$(grep -F "[fluent] $2" "$f" 2>/dev/null | head -1 | grep -oE 'wall=[0-9]+' | cut -d= -f2)"
  assert_eq "E1 step $1 comes after the step before it" "yes" "$([ -n "$w" ] && [ "$w" -gt "$PREV" ] && echo yes || echo no)"
  PREV="${w:-$PREV}"
}
step 1-start "acrylic=on reason=none"
step 2-battery-saver "acrylic=off reason=battery-saver"
step 3-saver-off "acrylic=on reason=none"
step 4-switch-off "acrylic=off reason=setting"
step 5-switch-on "acrylic=on reason=none"
F6="$QA/E1/slice-6-disable-window-blurs.txt"
assert_eq "E1 slice-6-disable-window-blurs is saved" "yes" "$([ -f "$F6" ] && echo yes || echo no)"
assert_absent "E1 step 6 (disable_window_blurs): no acrylic=off line" "acrylic=off" "$(cat "$F6" 2>/dev/null)"
row_end
