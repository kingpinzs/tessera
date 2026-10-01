#!/usr/bin/env bash
# 2026-10-01: the host froze at about 11:11 (its disk filled) and was power-cycled; the emulator was relaunched at 12:35.
# The People builder's device session was cut off mid-run and left a fixture contact and two re-pointed slots. This puts
# the device back on this phase's baseline and reads the result back.
. "$(dirname "$0")/../scripts/lib.sh"
. "$(dirname "$0")/../scripts/p16.sh"
row_begin CRASH-CLEANUP "the device after the 2026-10-01 host crash: leftovers removed, the baseline restored"
note "raw contacts before: $(q "content query --uri $RAW --projection _id:display_name:account_name:deleted" | tr '\n' ' ')"
note "slots before: $(layout_json | python3 -c 'import json,sys; print(json.load(sys.stdin)["slots"])')"
for id in $(raw_of 'Ann Lee'); do q "content delete --uri '$RAW/$id?$SA'" >/dev/null; done
assert_eq "the leftover Ann Lee fixture is gone" "" "$(raw_of 'Ann Lee')"
assert_contains "provision's Mom is untouched" "display_name=Mom" "$(q "content query --uri $RAW --projection _id:display_name --where \"display_name='Mom' AND deleted=0\"")"
adb emu gsm list > "$ROW_DIR/gsm.txt" 2>&1; note "gsm calls: $(tr '\n' ' ' < "$ROW_DIR/gsm.txt")"
record "preferred home activity" "$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')"
layout_restore "$BASELINE"; assert_eq "layout_restore of the baseline" "0" "$?"
assert_eq "slots.MAIL is unassigned again" "" "$(layout_json | python3 -c 'import json,sys; print(json.load(sys.stdin)["slots"].get("MAIL",""))')"
for p in READ_CONTACTS WRITE_CONTACTS READ_CALENDAR WRITE_CALENDAR; do assert_eq "$p held" "true" "$(perm_granted $p)"; done
assert_eq "device clock within 2 s of the host" "yes" "$(python3 -c 'import sys; print("yes" if abs(int(sys.argv[1])-int(sys.argv[2]))<=2 else "no")' "$(adb shell date +%s | tr -d '\r')" "$(date +%s)")"
ensure_start
row_end
