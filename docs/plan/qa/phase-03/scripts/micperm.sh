#!/usr/bin/env bash
# MICPERM — the microphone prompt actually appears (phase 03 edge case "Microphone permission denied or
# revoked"; Jeremy, 2026-09-22 on the phone: "it is saying it needs permission to use the microphone but
# it never popped up the prompt to grant it").
#
# Bracketed both ways it could fail:
#   1. revoked (Android will still ask): a tap on Tess's microphone must put Android's prompt ON SCREEN,
#      above Tess; granting it and tapping again must start listening;
#   2. denied for good (Android will not ask again): the same tap must open Tessera's app-info page,
#      because the prompt cannot appear — never "nothing happens";
#   3. the keyboard's voice key in state 2 does the same;
#   4. revoked again AFTER parts 2-3 left Settings' app-info page inside the permission page's task: the
#      tap must still put the prompt on screen. A plain NEW_TASK start only brought that stale task to
#      the front and the request never ran (START_TASK_TO_FRONT) — the second way "it never popped up".
# Parts 3 and 4 also require a NEW "requesting" line in the ring after the tap, so a stale Settings page
# that merely came back to the front cannot pass for the page having run.
# Restores the grant at the end.
. "$(dirname "$0")/lib.sh"

row_begin MICPERM "the microphone prompt appears (revoked, and denied for good)"
PERM=android.permission.RECORD_AUDIO
top() { adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r'; }
asked() { diag cortana | grep -c "requesting $PERM"; }

# ---- 1. revoked --------------------------------------------------------------------------------------
adb shell pm revoke $PKG $PERM; adb shell pm clear-permission-flags $PKG $PERM user-set user-fixed 2>/dev/null; sleep 2
ensure_start
cortana_listen 2
sleep 2
screencap "$ROW_DIR/micperm_prompt.png"
t="$(top)"; log "after the tap (revoked): $t"
assert_contains "revoked: Android's permission prompt is the resumed activity" "permissioncontroller" "$t"
dump_ui "$ROW_DIR/micperm_prompt.xml"
allow="$(grep -o 'resource-id="com.android.permissioncontroller:id/permission_allow_foreground_only_button"' "$ROW_DIR/micperm_prompt.xml")"
assert_ne "revoked: the prompt's allow button is on screen (not hidden under Tess)" "" "$allow"
assert_eq "revoked: Tess's session stepped aside for it" "no" "$(has_node "$ROW_DIR/micperm_prompt.xml" cortana_session)"
assert_contains "revoked: Tess recorded that she asked" "listen: no RECORD_AUDIO; asking" "$(diag cortana | tail -8)"
tap_node "$ROW_DIR/micperm_prompt.xml" com.android.permissioncontroller:id/permission_allow_foreground_only_button; sleep 2
assert_contains "revoked: allowing it grants it" "granted=true" "$(adb shell dumpsys package $PKG | grep -m1 "$PERM: granted" | tr -d '\r')"
# Tess stepped aside for the prompt (run 1 found the prompt resumed but hidden UNDER her window); once the
# microphone is allowed she comes back already listening, with no second tap.
for _ in $(seq 1 30); do [ "$(speech_status asr_listening)" = "true" ] && break; sleep 0.5; done
assert_eq "revoked, then allowed: Tess comes back listening by herself" "true" "$(speech_status asr_listening)"
assert_contains "and says so in the ring" "reopening Tess listening (shown=true)" "$(diag cortana | tail -10)"
dump_ui "$ROW_DIR/micperm_back.xml"; screencap "$ROW_DIR/micperm_back.png"
assert_eq "Tess's session is on screen again" "yes" "$(has_node "$ROW_DIR/micperm_back.xml" cortana_session)"
cortana_close

# ---- 2. denied for good ---------------------------------------------------------------------------------
adb shell pm revoke $PKG $PERM; adb shell pm set-permission-flags $PKG $PERM user-set user-fixed; sleep 2
ensure_start
cortana_listen 2
sleep 2.5
screencap "$ROW_DIR/micperm_blocked.png"
t="$(top)"; log "after the tap (denied for good): $t"
assert_contains "denied for good: Tessera's app-info page opens (the prompt cannot appear)" "com.android.settings" "$t"
assert_contains "denied for good: the permission page recorded why" "permission blocked by Android" "$(diag cortana | tail -8)"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- 3. the keyboard's voice key, denied for good -----------------------------------------------------------
FIX=app.tileshell.qa.imefixture
if adb shell pm path $FIX >/dev/null 2>&1; then
  prior="$(adb shell settings get secure default_input_method | tr -d '\r')"
  adb shell settings put secure show_ime_with_hard_keyboard 1
  adb shell ime enable app.tileshell/.ime.KeyboardService >/dev/null; adb shell ime set app.tileshell/.ime.KeyboardService >/dev/null
  adb shell am start -S -W -n $FIX/.MainActivity -e focus field_text >/dev/null; sleep 2.5
  adb shell am instrument --no-restart -r -w -e op dump -e out /sdcard/Download/mp.xml $FIX.test/androidx.test.runner.AndroidJUnitRunner >/dev/null 2>&1
  adb shell cat /sdcard/Download/mp.xml > "$ROW_DIR/micperm_kb.xml"
  n0="$(asked)"
  tap_node "$ROW_DIR/micperm_kb.xml" kb_mic; sleep 2.5
  t="$(top)"; log "keyboard voice key (denied for good): $t"
  assert_contains "keyboard, denied for good: Tessera's app-info page opens" "com.android.settings" "$t"
  assert_eq "keyboard, denied for good: the permission page really ran (a new request, not a stale task)" "$((n0 + 1))" "$(asked)"
  adb shell input keyevent KEYCODE_HOME; adb shell ime set "$prior" >/dev/null
else
  note "fixture not installed: keyboard sub-row skipped"
fi

# ---- 4. revoked again, with Settings left in the permission page's task ------------------------------------
adb shell pm clear-permission-flags $PKG $PERM user-set user-fixed; adb shell pm revoke $PKG $PERM; sleep 2
log "permission page's task before the tap: $(adb shell dumpsys activity activities | grep -m1 'A=[0-9]*:app.tileshell.cortana.permission' | tr -d '\r' | sed 's/^ *//')"
ensure_start
n0="$(asked)"
cortana_listen 2
sleep 2
t="$(top)"; log "after the tap (revoked, stale task): $t"
assert_contains "stale task: Android's permission prompt is the resumed activity" "permissioncontroller" "$t"
assert_eq "stale task: the permission page really ran (a new request)" "$((n0 + 1))" "$(asked)"
dump_ui "$ROW_DIR/micperm_stale.xml"
tap_node "$ROW_DIR/micperm_stale.xml" com.android.permissioncontroller:id/permission_allow_foreground_only_button; sleep 2
for _ in $(seq 1 30); do [ "$(speech_status asr_listening)" = "true" ] && break; sleep 0.5; done
assert_eq "stale task, then allowed: Tess comes back listening by herself" "true" "$(speech_status asr_listening)"
cortana_close

adb shell pm clear-permission-flags $PKG $PERM user-set user-fixed; adb shell pm grant $PKG $PERM
note "restored: $(adb shell dumpsys package $PKG | grep -m1 "$PERM: granted" | tr -d '\r' | sed 's/^ *//')"
row_end
