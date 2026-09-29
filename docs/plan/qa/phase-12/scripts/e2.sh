#!/usr/bin/env bash
# Phase 12 E2 — appearance, order and why, against a hand-written list (T12-1, T12-4).
# The E2 state, Home: the wizard shows on setup:notifications (Home is held, so it is not a step), Start's pager is not
# composed, "Step 1 of 20", and the [wizard] shown line names exactly the nineteen ids written below — never read from
# either checklist's dump. Not now through the run records the ids in that order, then the presets page; every step's
# why line equals the doc's table; a swipe on the wizard moves nothing.
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
row_begin E2 "appearance, order and why lines in the E2 state"

EXPECTED="setup:notifications setup:photos setup:music setup:calendar setup:location setup:usage setup:keyboard_enabled setup:keyboard_selected setup:full_screen_alarms setup:overlay tess:assistant tess:microphone tess:contacts tess:calendar tess:sms_send tess:call_phone tess:background_location tess:call_log tess:sms_read"

# Every text read from a dump is XML-unescaped first (uiautomator writes " as &quot;).
wtext() { ntext "$1" "$2"; }
why_for() { awk -F'\t' -v k="$1" '$1==k {print $2}' "$HERE/why_lines.tsv"; }

e2_state
assert_e2_appops
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/first.xml"
screencap "$ROW_DIR/first.png"
assert_eq "wizard_page" "yes" "$(has_node "$ROW_DIR/first.xml" wizard_page)"
assert_eq "wizard_step:setup:notifications first" "yes" "$(has_node "$ROW_DIR/first.xml" wizard_step:setup:notifications)"
assert_eq "no wizard_step:setup:home (Home held)" "no" "$(has_node "$ROW_DIR/first.xml" wizard_step:setup:home)"
assert_eq "no start_page node (pager not composed)" "no" "$(has_node "$ROW_DIR/first.xml" start_page)"
assert_absent "no applist_* node" 'resource-id="applist_' "$(cat "$ROW_DIR/first.xml")"
assert_eq "wizard_progress" "Step 1 of 20" "$(wtext "$ROW_DIR/first.xml" wizard_progress)"
start_slice "$MARK" "shown"
shown="$(printf '%s\n' "$SLICE" | grep -oE '\[wizard\] shown: missing=[^ ]*' | head -1 | sed 's/.*missing=//')"
assert_eq "[wizard] shown: missing= names exactly the nineteen, in order" "$(echo $EXPECTED | tr ' ' ',')" "$shown"
assert_eq "[wizard] shown written once" "1" "$(printf '%s\n' "$SLICE" | grep -c '\[wizard\] shown: ')"

log "a swipe left on the wizard page changes nothing"
adb shell input swipe 950 1200 200 1200 250; sleep 2
dump_ui "$ROW_DIR/after-swipe.xml"
strip() { grep -oE '<node [^>]*resource-id="(wizard|preset)[^"]*"[^>]*>' "$1" | sed -E 's/ (focused|selected)="[a-z]*"//g'; }
assert_eq "dump's wizard nodes identical after the swipe" "$(strip "$ROW_DIR/first.xml" | sha256sum | cut -c1-16)" "$(strip "$ROW_DIR/after-swipe.xml" | sha256sum | cut -c1-16)"

log "Not now through the run: order and every step's why line"
seen=""
i=0
while [ "$i" -lt 30 ]; do
  f="$ROW_DIR/step-$i.xml"
  dump_ui "$f"
  key="$(wiz_step "$f")"
  if [ -z "$key" ]; then break; fi
  seen="$seen $key"
  assert_eq "why line on $key" "$(why_for "$key")" "$(wtext "$f" wizard_why)"
  assert_eq "progress on $key" "Step $((i + 1)) of 20" "$(wtext "$f" wizard_progress)"
  tap_node "$f" wizard_not_now
  sleep 1.3
  i=$((i + 1))
done
assert_eq "steps seen in exactly the hand-written order" "$EXPECTED" "$(echo $seen)"
assert_eq "then wizard_presets" "yes" "$(has_node "$f" wizard_presets)"
assert_eq "presets page progress" "Step 20 of 20" "$(wtext "$f" wizard_progress)"
screencap "$ROW_DIR/presets.png"
start_slice "$MARK" "walk"
assert_eq "one not-now line per step" "19" "$(printf '%s\n' "$SLICE" | grep -c '\[wizard\] step [a-z]*:[a-z_]*: not now')"

log "restore: pm clear -> provision.sh -> Home"
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
