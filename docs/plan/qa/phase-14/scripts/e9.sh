#!/usr/bin/env bash
# Phase 14 E9 — from inside another app: DeskClock on top, Tess over it, "open the pod bay doors" spoken on the
# emulator's gRPC route → after the reply (the open line's wall= after the reply's `speaking done`, E6's order) Start is
# the resumed activity and the pod bay is the page; Back → Start; a second Back → DeskClock resumes (phase 01 E20: the
# Back history survived the detour); then `am force-stop app.tileshell` + Home (C-6).
set -uo pipefail
export AUDIO_ROUTE=emu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E9 "from inside another app: DeskClock, the doors line, Start with the pod bay, Back twice to DeskClock"
require_emu_audio || { row_end; exit 1; }
voice_volume_up
DOORS="I'm afraid I can't do that, Dave."

ensure_start
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null 2>&1
sleep 3
assert_contains "DeskClock is on top" "com.android.deskclock/" "$(top_activity)"
tess_open
assert_eq "Tess open over DeskClock" "yes" "$(has_node "$ROW_DIR/.tess-open.xml" cortana_session)"

voice_step pod_bay_doors 11 doors
assert_contains "doors: the match" "-> OpenPodBay(doors=true)" "$(match_line "$VS_SLICE")"
assert_eq "doors: the reply" "$DOORS" "$VS_REPLY"
assert_rms_audible "doors" "$VS_RMS"
uid="$(printf '%s\n' "$VS_SLICE" | grep -F '[speech] speak[' | grep -F "text=\"$DOORS\"" | head -1 | sed -E 's/.*speak\[([^]]+)\].*/\1/')"
done_wall="$(wall_of_first "$VS_SLICE" "[speech] speaking done $uid cancelled=false")"
open_wall="$(wall_of_first "$VS_SLICE" "[podbay] opened by voice (doors)")"
note "doors: utterance $uid speaking done wall=$done_wall; opened by voice (doors) wall=$open_wall"
assert_ne "doors: the reply finished (speaking done ... cancelled=false)" "" "$done_wall"
assert_ne "doors: [podbay] opened by voice (doors)" "" "$open_wall"
assert_eq "doors: the pane opened AFTER the reply was spoken" "yes" "$([ -n "$done_wall" ] && [ -n "$open_wall" ] && [ "$open_wall" -gt "$done_wall" ] && echo yes || echo no)"
assert_eq "doors: no session window" "no" "$(session_window)"
assert_eq "doors: StartActivity is resumed" "app.tileshell/.StartActivity" "$(top_activity)"
dump_ui "$ROW_DIR/doors-after.xml"
assert_eq "doors: the dump shows pod_bay" "yes" "$(has_node "$ROW_DIR/doors-after.xml" pod_bay)"
screencap "$ROW_DIR/doors-after.png"

MB="$(ring_mark)"
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/back1.xml"
assert_eq "Back: Start alone" "yes" "$(start_alone "$ROW_DIR/back1.xml")"
assert_contains "Back: [podbay] closed by back" "[podbay] closed by back" "$(ring_since "$MB")"
assert_eq "Back: still Start's activity" "app.tileshell/.StartActivity" "$(top_activity)"

adb shell input keyevent KEYCODE_BACK
sleep 2
assert_contains "second Back: DeskClock resumes" "com.android.deskclock/" "$(top_activity)"
screencap "$ROW_DIR/back2.png"

ring_save launcher
adb shell am force-stop app.tileshell
adb shell input keyevent KEYCODE_HOME
sleep 3
ensure_start
voice_volume_restore
RINGS="launcher speech" row_end
