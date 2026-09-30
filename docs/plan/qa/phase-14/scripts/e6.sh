#!/usr/bin/env bash
# Phase 14 E6 — voice, the easter egg, on the emulator's own gRPC audio (AUDIO_ROUTE=emu): "open the pod bay doors" is
# OpenPodBay(doors=true), Tess says the film's line, the session closes, THEN the pod bay opens (the open line's wall=
# after the reply's `speaking done` wall=, both from the launcher ring); "open the pod bay" / "close the pod bay doors"
# plainly; "open the pod" is phase 03's OpenApp; "open pod bay doors" (no article) is still doors. Every spoken step
# passes C-30.
set -uo pipefail
export AUDIO_ROUTE=emu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E6 "voice: the doors line, plain open and close, the negatives"
require_emu_audio || { row_end; exit 1; }
voice_volume_up
DOORS="I'm afraid I can't do that, Dave."
pod_matches=""

# (1) The doors line, from Start.
ensure_start
tess_open
assert_eq "Tess open over Start" "yes" "$(has_node "$ROW_DIR/.tess-open.xml" cortana_session)"
voice_step pod_bay_doors 11 doors
m="$(match_line "$VS_SLICE")"; pod_matches+="$m"$'\n'
assert_contains "doors: the match" "-> OpenPodBay(doors=true)" "$m"
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
dump_ui "$ROW_DIR/doors-after.xml"
assert_eq "doors: the dump shows pod_bay" "yes" "$(has_node "$ROW_DIR/doors-after.xml" pod_bay)"
screencap "$ROW_DIR/doors-after.png"

# (2) Plain open, from Start.
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/open-before.xml"
assert_eq "open: from Start" "yes" "$(start_alone "$ROW_DIR/open-before.xml")"
tess_open
voice_step pod_bay_open 11 open
m="$(match_line "$VS_SLICE")"; pod_matches+="$m"$'\n'
assert_contains "open: the match" "-> OpenPodBay(doors=false)" "$m"
assert_eq "open: the reply" "Opening the pod bay." "$VS_REPLY"
assert_contains "open: [podbay] opened by voice" "[podbay] opened by voice" "$VS_SLICE"
assert_absent "open: not the doors line" "[podbay] opened by voice (doors)" "$VS_SLICE"
dump_ui "$ROW_DIR/open-after.xml"
assert_eq "open: pod_bay" "yes" "$(has_node "$ROW_DIR/open-after.xml" pod_bay)"
note "open: RMS $VS_RMS"

# (3) Close, from the pod bay.
tess_open
voice_step pod_bay_close 11 close
m="$(match_line "$VS_SLICE")"; pod_matches+="$m"$'\n'
assert_contains "close: the match" "-> ClosePodBay" "$m"
assert_eq "close: the reply" "Closing the pod bay." "$VS_REPLY"
assert_contains "close: [podbay] closed by voice" "[podbay] closed by voice" "$VS_SLICE"
dump_ui "$ROW_DIR/close-after.xml"
assert_eq "close: Start alone" "yes" "$(start_alone "$ROW_DIR/close-after.xml")"

# (4) Negative: "open the pod" is phase 03's open-app rule.
ensure_start
tess_open
voice_step pod_bay_neg1 11 neg1
m="$(match_line "$VS_SLICE")"
assert_contains "neg1: OpenApp(name=the pod)" "-> OpenApp(name=the pod)" "$m"
assert_eq "neg1: the reply" "I don't see an app called the pod." "$VS_REPLY"
assert_absent "neg1: no [podbay] opened" "[podbay] opened" "$VS_SLICE"
dump_ui "$ROW_DIR/neg1-after.xml"
assert_eq "neg1: no pod_bay" "no" "$(has_node "$ROW_DIR/neg1-after.xml" pod_bay)"
cortana_close

# (5) No article: still the doors.
ensure_start
tess_open
voice_step pod_bay_doors_noart 11 noart
m="$(match_line "$VS_SLICE")"; pod_matches+="$m"$'\n'
assert_contains "noart: doors=true" "-> OpenPodBay(doors=true)" "$m"
assert_eq "noart: the reply" "$DOORS" "$VS_REPLY"
dump_ui "$ROW_DIR/noart-after.xml"
assert_eq "noart: pod_bay" "yes" "$(has_node "$ROW_DIR/noart-after.xml" pod_bay)"
printf '%s' "$pod_matches" > "$ROW_DIR/pod-bay-matches.txt"
assert_absent "no match line of the four pod-bay utterances is OpenApp" "OpenApp" "$pod_matches"

adb shell input keyevent KEYCODE_BACK
sleep 1.5
ensure_start
voice_volume_restore
RINGS="launcher speech" row_end
