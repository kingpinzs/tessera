#!/usr/bin/env bash
# Phase 14 E7 — the typed form (fidelity A4): "open the pod bay doors" typed into the real text box runs the same
# matcher and reply path; the reply is the first after the MARK (C-20: E6's identical line is before it, so an empty
# reply fails); the pod bay opens. Then phase 03 E5's exported-components check against its allow-list: unchanged.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E7 "typed form; the exported components unchanged"
DOORS="I'm afraid I can't do that, Dave."

ensure_start
dump_ui "$ROW_DIR/01-start.xml"
assert_eq "from Start alone (so the open can fail)" "yes" "$(start_alone "$ROW_DIR/01-start.xml")"
assert_eq "no pod_bay before" "no" "$(has_node "$ROW_DIR/01-start.xml" pod_bay)"
cortana_assist
sleep 4
dump_ui "$ROW_DIR/02-tess.xml"
assert_eq "cortana_session in the dump" "yes" "$(has_node "$ROW_DIR/02-tess.xml" cortana_session)"
MARK="$(ring_mark)"
type_request "open the pod bay doors" 8
sleep 4
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/03-slice.txt"
assert_contains "typed: the match" "\"open the pod bay doors\" -> OpenPodBay(doors=true)" "$(printf '%s\n' "$s" | grep -F '[match]')"
assert_eq "typed: reply_since MARK is the doors line" "$DOORS" "$(reply_since "$MARK")"
assert_contains "typed: [podbay] opened by voice (doors)" "[podbay] opened by voice (doors)" "$s"
dump_ui "$ROW_DIR/04-after.xml"
assert_eq "typed: the dump shows pod_bay" "yes" "$(has_node "$ROW_DIR/04-after.xml" pod_bay)"
assert_eq "typed: no session window" "no" "$(session_window)"
screencap "$ROW_DIR/04-after.png"

APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"
python3 "$P03S/exported.py" "$APK" "$P03S/../exported-allowlist.txt" > "$ROW_DIR/exported.txt" 2>&1
exported_rc=$?
cat "$ROW_DIR/exported.txt" >> "$LOG"
assert_eq "phase 03 E5: the exported components equal the allow-list (unchanged)" "0" "$exported_rc"
assert_eq "the allow-list file is phase 03's, unedited by this phase" "" "$(git -C "$REPO" diff --stat bfb7b8f4 -- docs/plan/qa/phase-03/exported-allowlist.txt)"

adb shell input keyevent KEYCODE_BACK
sleep 1.5
ensure_start
row_end
