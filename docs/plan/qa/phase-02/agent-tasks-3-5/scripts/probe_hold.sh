#!/usr/bin/env bash
# Probe: does a hold past Edit.HOLD_MS open the context menu? Installs this worktree's build first (the
# emulator is shared), then holds the first app row in the list and reports what the dump and the ring show.
source "$(dirname "$0")/p2.sh"
set -u
OUT=/tmp/claude-1000/-home-jeremyking/277bd9d2-7817-47e9-bc8f-0d5611a2abef/scratchpad/a35
REPO=/home/jeremyking/projects/metro-launcher/.claude/worktrees/agent-a00901a5ef0b39ec2
adb install -r "$REPO/app/build/outputs/apk/debug/app-debug.apk" | tail -1
bash "$(dirname "$0")/guard.sh"
sleep 3
open_applist "$OUT/probe_list.xml" || exit 1
ROW=$(grep -o 'resource-id="applist_row:[^"]*"' "$OUT/probe_list.xml" | head -1 | sed 's/resource-id="//; s/"$//')
echo "row under test: $ROW  bounds=$(bounds "$OUT/probe_list.xml" "$ROW")"
echo "-- hold 900 ms --"
hold_id "$OUT/probe_list.xml" "$ROW" 900
sleep 1
dump "$OUT/probe_after_hold.xml"
echo "app_list on screen: $(grep -c 'resource-id="app_list"' "$OUT/probe_after_hold.xml"); menu: $(grep -c 'resource-id=\"applist_menu\"' "$OUT/probe_after_hold.xml"); foreground package: $(grep -o 'package="[^"]*"' "$OUT/probe_after_hold.xml" | sort -u | tr '\n' ' ')"
echo "-- diagnostics --"
diag | grep -E "\[applist\]|\[launch\]" | tail -6
