#!/usr/bin/env bash
# Phase 14 E13 — regression on the same build, after the harness fix (T14-5, r3 V1 / V22):
#   (0) `utterances.py build` succeeds with the five new ids;
#   (1) phase 02's regress.sh, every assertion passing, its home-line grep `home: page START` (its own out dir, here);
#   (2) the fixed helpers from the pod bay and from the app list — phase 02 gestures.sh ensure_start, phase 03 lib.sh
#       ensure_start, phase 11 q.sh ensure_start_page — each → start_page alone;
#   (3) phase 01 E2 (Home shows Start), E12 (swipe left → app list, search, jump grid), E19 (bars on Start, the app list,
#       a Settings page, Weather; the drawn Back on a Settings page → the hub), E20 (Back on Start: DeskClock's Timer
#       tab comes back), replayed in what they check (they ran inside combined scripts, no standalone driver);
#   (4) with AUDIO_ROUTE=emu, phase 03 E3, E5 and E10 run UNCHANGED — their row directories are moved aside first and
#       put back after (phase 12 E9's pattern), so phase 03's evidence stays as it was and the new runs are kept here;
#   after every re-run that opened an app, `am force-stop app.tileshell` + Home (C-6); after every layout restore, zero
#   `assignSlotOnce … -> assigned` lines (C-3).
set -uo pipefail
export AUDIO_ROUTE=emu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"
QAR="$(cd "$QA/.." && pwd)"
P02="$QAR/phase-02/scripts"; P03S="$QAR/phase-03/scripts"; P11="$QAR/phase-11/scripts"
OUT="$QA/E13"; mkdir -p "$OUT"

between() {
  adb shell input keyevent KEYCODE_BACK; sleep 0.5; adb shell input keyevent KEYCODE_BACK; sleep 0.5
  adb shell input keyevent KEYCODE_HOME; sleep 2
}
c6() { adb shell am force-stop "$PKG"; adb shell input keyevent KEYCODE_HOME; sleep 5; }
# A phase 03 lib.sh row, unchanged: its own row directory moved aside first and put back after; the new run kept here.
run_p03_row() { # row script
  local row="$1" script="$2" dir="$QAR/phase-03/$1" keep="$QAR/phase-03/$1.p14-e13-aside" m
  [ -e "$dir" ] && mv "$dir" "$keep"
  between
  m="$(ring_mark)"
  bash "$P03S/$script" > "$OUT/p03-$row.out" 2>&1; echo $? > "$OUT/p03-$row.rc"
  ring_since "$m" > "$OUT/p03-$row.ring.txt" 2>/dev/null
  rm -rf "$OUT/p03-$row"; [ -e "$dir" ] && mv "$dir" "$OUT/p03-$row"
  [ -e "$keep" ] && mv "$keep" "$dir"
  c6
}

# ---- children first (each takes the device lock itself): phase 02 regress.sh, phase 03 E3 / E5 / E10
between
M_REG="$(ring_mark)"
bash "$P02/regress.sh" "$OUT/p02-REGRESS" > "$OUT/p02-regress.out" 2>&1; echo $? > "$OUT/p02-regress.rc"
ring_since "$M_REG" > "$OUT/p02-regress.ring.txt" 2>/dev/null
c6
run_p03_row E3 e3.sh
run_p03_row E5 e5.sh
run_p03_row E10 e10.sh
between

# ---- the row itself
row_begin E13 "regression: utterances, phase 02 regress.sh, the fixed helpers, phase 01 E2/E12/E19/E20, phase 03 E3/E5/E10"

log "(0) utterances.py build with the five new ids"
# The host python has no sherpa-onnx (run 1: ModuleNotFoundError), so the build runs under uv with the three packages the
# script names. The synthesis is not byte-stable, and every spoken row ran on the committed WAVs, so those are put back
# after the build has proved itself.
NEW_IDS="pod_bay_doors pod_bay_doors_noart pod_bay_open pod_bay_close pod_bay_neg1"
( cd "$REPO" && timeout 500 uv run --quiet --with sherpa-onnx --with soundfile --with numpy python3 "$P03S/utterances.py" build $NEW_IDS ) > "$ROW_DIR/utterances-build.out" 2>&1; echo $? > "$ROW_DIR/utterances-build.rc"
assert_eq "utterances.py build rc (the five new ids, under uv)" "0" "$(cat "$ROW_DIR/utterances-build.rc")"
for id in $NEW_IDS; do assert_contains "the build wrote $id" "$id.wav" "$(cat "$ROW_DIR/utterances-build.out")"; done
git -C "$REPO" checkout -- docs/plan/qa/phase-03/utterances/
assert_eq "the committed WAVs are back" "0" "$(git -C "$REPO" status --short docs/plan/qa/phase-03/utterances | wc -l | tr -d ' ')"
for id in $NEW_IDS; do
  assert_eq "utterance $id built" "yes" "$([ -s "$(python3 "$P03S/utterances.py" path "$id")" ] && echo yes || echo no)"
done

log "(1) phase 02 regress.sh"
assert_eq "regress.sh exits 0" "0" "$(cat "$OUT/p02-regress.rc")"
note "regress.sh: $(grep -E '^[0-9]+ passed, [0-9]+ failed' "$OUT/p02-REGRESS/REGRESS.txt" | tail -1)"
assert_contains "regress.sh: 0 failed" " passed, 0 failed" "$(grep -E '^[0-9]+ passed, [0-9]+ failed' "$OUT/p02-REGRESS/REGRESS.txt" | tail -1)"
assert_contains "regress.sh: the home-line grep passed (home: page START)" "PASS  the shell logged the home event" "$(cat "$OUT/p02-REGRESS/REGRESS.txt")"
reg_assigned="$(grep -F 'assignSlotOnce' "$OUT/p02-regress.ring.txt" | grep -cF -- '-> assigned')"
assert_eq "regress.sh: zero assignSlotOnce -> assigned after its restores (C-3)" "0" "$reg_assigned"

log "(2) the fixed helpers, from the pod bay and from the app list"
helper_from() { # page helper-label command...
  local page="$1" label="$2"; shift 2
  ensure_start
  if [ "$page" = pod_bay ]; then swipe_right 2; else swipe_left 2; fi
  dump_ui "$ROW_DIR/h-$label-$page-before.xml"
  assert_eq "$label from $page: on $page first" "yes" "$(has_node "$ROW_DIR/h-$label-$page-before.xml" "$page")"
  "$@" > "$ROW_DIR/h-$label-$page.out" 2>&1; echo $? > "$ROW_DIR/h-$label-$page.rc"
  dump_ui "$ROW_DIR/h-$label-$page-after.xml"
  assert_eq "$label from $page: the helper returns 0" "0" "$(cat "$ROW_DIR/h-$label-$page.rc")"
  assert_eq "$label from $page: start_page alone" "yes" "$(start_alone "$ROW_DIR/h-$label-$page-after.xml")"
}
p02_ensure() { ( . "$P02/gestures.sh"; ensure_start ); }
p03_ensure() { ( ensure_start ); }
p11_ensure() { ( . "$P11/q.sh"; ensure_start_page ); }
for page in pod_bay app_list; do
  helper_from "$page" p02-gestures p02_ensure
  helper_from "$page" p03-lib p03_ensure
  helper_from "$page" p11-q p11_ensure
done

log "(3) phase 01 E2: the HOME role, and Home shows Start"
assert_eq "phase 01 E2: HOME role holder" "$PKG" "$(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"
adb shell input keyevent KEYCODE_HOME; sleep 3
screencap "$ROW_DIR/p01-e2-start.png"; dump_ui "$ROW_DIR/p01-e2-start.xml"
assert_eq "phase 01 E2: Home shows Start" "yes" "$(has_node "$ROW_DIR/p01-e2-start.xml" start_page)"

log "(3) phase 01 E12: swipe left → the app list; search filters; a jump-grid letter jumps"
ensure_start
swipe_left 2
dump_ui "$ROW_DIR/p01-e12-applist.xml"
assert_eq "phase 01 E12: swipe left shows the app list" "yes" "$(has_node "$ROW_DIR/p01-e12-applist.xml" app_list)"
tap_node "$ROW_DIR/p01-e12-applist.xml" applist_search; sleep 1
all_rows="$(grep -oE 'resource-id="applist_row:[^"]*"' "$ROW_DIR/p01-e12-applist.xml" | wc -l)"
# "clock": run 1 searched "fossify" as phase 01's own run did, and like it found nothing — the Fossify apps' labels are
# "Messages", "Contacts", ... (qa/phase-01/E12/E12.txt: "results for 'fossify': "), so that proves no filtering.
adb shell input text clock; sleep 3
dump_ui "$ROW_DIR/p01-e12-search.xml"; screencap "$ROW_DIR/p01-e12-search.png"
rows="$(grep -oE 'resource-id="applist_row:[^"]*"' "$ROW_DIR/p01-e12-search.xml" | sed 's/resource-id="applist_row://; s/"$//')"
note "search 'clock': $(echo "$rows" | tr '\n' ' ') (the list showed $all_rows rows before)"
assert_eq "phase 01 E12: the results list is showing" "yes" "$(has_node "$ROW_DIR/p01-e12-search.xml" applist_results)"
assert_contains "phase 01 E12: search 'clock' finds DeskClock" "com.android.deskclock" "$rows"
assert_eq "phase 01 E12: and filters the list (fewer rows than before)" "yes" "$([ -n "$rows" ] && [ "$(echo "$rows" | wc -l)" -lt "$all_rows" ] && echo yes || echo no)"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
ensure_start
swipe_left 2
dump_ui "$ROW_DIR/p01-e12-applist2.xml"
hdr="$(grep -oE 'resource-id="applist_header:[^"]*"' "$ROW_DIR/p01-e12-applist2.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
assert_ne "phase 01 E12: a letter header on the list" "" "$hdr"
tap_node "$ROW_DIR/p01-e12-applist2.xml" "$hdr"; sleep 2
dump_ui "$ROW_DIR/p01-e12-grid.xml"
assert_eq "phase 01 E12: the header opens the jump grid" "yes" "$(has_node "$ROW_DIR/p01-e12-grid.xml" jump_grid)"
tap_node "$ROW_DIR/p01-e12-grid.xml" jump_cell:O; sleep 2
dump_ui "$ROW_DIR/p01-e12-jumped.xml"; screencap "$ROW_DIR/p01-e12-jumped.png"
assert_eq "phase 01 E12: the grid closes on a letter" "no" "$(has_node "$ROW_DIR/p01-e12-jumped.xml" jump_grid)"
first="$(grep -oE 'resource-id="applist_header:[^"]*"' "$ROW_DIR/p01-e12-jumped.xml" | head -1 | sed 's/resource-id="applist_header://; s/"$//')"
assert_eq "phase 01 E12: O is the first header on screen" "O" "$first"
ensure_start

log "(3) phase 01 E19: bars on Start, the app list, a Settings page and Weather"
bars_on() { # label
  adb shell dumpsys window > "$ROW_DIR/p01-e19-$1-window.txt"
  dump_ui "$ROW_DIR/p01-e19-$1.xml"; screencap "$ROW_DIR/p01-e19-$1.png"
  # The focused app window's own inset sources: the first statusBars / navigationBars source in the dump.
  assert_eq "phase 01 E19 $1: system status bar not visible" "visible=false" "$(grep -m1 'InsetsSource id=[0-9a-f]* type=statusBars' "$ROW_DIR/p01-e19-$1-window.txt" | grep -oE 'visible=[a-z]+')"
  assert_eq "phase 01 E19 $1: system nav bar not visible" "visible=false" "$(grep -m1 'InsetsSource id=[0-9a-f]* type=navigationBars' "$ROW_DIR/p01-e19-$1-window.txt" | grep -oE 'visible=[a-z]+')"
  assert_eq "phase 01 E19 $1: the drawn status bar" "yes" "$(has_node "$ROW_DIR/p01-e19-$1.xml" w10m_status_bar)"
  assert_eq "phase 01 E19 $1: the drawn nav bar" "yes" "$(has_node "$ROW_DIR/p01-e19-$1.xml" w10m_nav_bar)"
}
ensure_start; sleep 1; bars_on start
swipe_left 3; bars_on applist
ensure_start
adb shell am start -n "$PKG/.settings.SettingsActivity" >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/p01-e19-hub.xml"
# The hub is the page that lists the other pages (run 1 read page_header's text, which is on a child node: empty).
is_hub() { grep -q 'text="Tile apps"' "$1" && grep -q 'text="Diagnostics"' "$1" && echo yes || echo no; }
assert_eq "phase 01 E19: Settings opens on the hub" "yes" "$(is_hub "$ROW_DIR/p01-e19-hub.xml")"
adb shell am start -n "$PKG/.settings.SettingsActivity" --activity-single-top --es page START_THEME >/dev/null 2>&1; sleep 3
bars_on settings
assert_eq "phase 01 E19: the Settings page is not the hub" "no" "$(is_hub "$ROW_DIR/p01-e19-settings.xml")"
tap_node "$ROW_DIR/p01-e19-settings.xml" nav_back; sleep 2
dump_ui "$ROW_DIR/p01-e19-back-to-hub.xml"
assert_eq "phase 01 E19: the drawn Back on a Settings page returns to the hub" "yes" "$(is_hub "$ROW_DIR/p01-e19-back-to-hub.xml")"
adb shell am start -n "$PKG/.weather.WeatherActivity" >/dev/null 2>&1; sleep 5
bars_on weather
ring_save launcher
c6

log "(3) phase 01 E20: Back on Start brings DeskClock's Timer tab back"
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/p01-e20-clock.xml"
timer="$(grep -oE '<node [^>]*(text="Timer"|content-desc="Timer")[^>]*>' "$ROW_DIR/p01-e20-clock.xml" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')"
assert_ne "phase 01 E20: DeskClock's Timer tab is on screen" "" "$timer"
set -- $timer
adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2
task0="$(adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r')"
dump_ui "$ROW_DIR/p01-e20-timer.xml"
note "before Home: $task0"
adb shell input keyevent KEYCODE_HOME; sleep 3
dump_ui "$ROW_DIR/p01-e20-start.xml"
assert_eq "phase 01 E20: Home shows Start" "yes" "$(has_node "$ROW_DIR/p01-e20-start.xml" start_page)"
M20="$(ring_mark)"
tap_node "$ROW_DIR/p01-e20-start.xml" nav_back; sleep 3
ring_since "$M20" > "$ROW_DIR/p01-e20-back-slice.txt"
note "the drawn Back's slice: $(grep -vE 'tile_anim' "$ROW_DIR/p01-e20-back-slice.txt" | sed 's/.*wall=[0-9]* //' | tr '\n' ';' | cut -c1-300)"
task1="$(adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r')"
dump_ui "$ROW_DIR/p01-e20-after-back.xml"; screencap "$ROW_DIR/p01-e20-after-back.png"
note "after the drawn Back: $task1"
assert_eq "phase 01 E20: the same task and activity resumed" "$(echo "$task0" | grep -oE 't[0-9]+\}' )" "$(echo "$task1" | grep -oE 't[0-9]+\}')"
assert_contains "phase 01 E20: DeskClock resumed" "com.android.deskclock/" "$task1"
assert_eq "phase 01 E20: the Timer tab is still selected" "yes" "$(grep -qE '<node [^>]*(text="Timer"|content-desc="Timer")[^>]*selected="true"' "$ROW_DIR/p01-e20-after-back.xml" && echo yes || echo no)"
ring_save launcher
c6

log "(4) phase 03 E3, E5, E10 — run unchanged, spoken through the emulator's route"
for r in E3 E5 E10; do
  assert_eq "phase 03 $r passes unchanged (rc)" "0" "$(cat "$OUT/p03-$r.rc")"
  note "phase 03 $r: $(grep -hE "^$r: [0-9]+ passed" "$OUT/p03-$r/"*.txt 2>/dev/null | tail -1)"
done

RINGS="launcher speech" row_end
