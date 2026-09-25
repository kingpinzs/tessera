#!/usr/bin/env bash
# L13-1 minimal repro (Blocked-on ledger): inside Tess's session window, (1) opening a reminder's page — the Reminders
# page's "new" (+) button, or tapping a reminder row — crashes the launcher's process: ReminderDetailPage calls
# rememberLauncherForActivityResult, and a VoiceInteractionSession window has no ActivityResultRegistryOwner; (2) the
# confirm card's "Add a photo" does nothing: CardAction.PICK_PHOTO is left to "the session" (CortanaModel.kt:351) and no
# session code handles it. Together: no product route attaches a photo to a reminder, which phase 13's E3 fixture needs.
#
# usage: l13_1_repro.sh <apk> <label>   — installs the given APK, reproduces, and leaves it installed.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$(dirname "$0")/reminders_fixture.sh"

REPRO_APK="$1"; LABEL="$2"
row_begin "L13-1-$LABEL" "L13-1 repro on $LABEL: a reminder's page crashes inside Tess; the card's Add a photo is a no-op"
note "repro apk: $(sha256sum "$REPRO_APK" | cut -c1-16) ($REPRO_APK)"
adb install -r -g "$REPRO_APK" >/dev/null 2>&1
note "installed: $(installed_apk_id)"
adb shell am force-stop $PKG; sleep 2
show_start 5

crashes() { adb logcat -d -b crash 2>/dev/null | grep -c "No ActivityResultRegistryOwner"; }

# (1) the Reminders page's + button
BEFORE="$(crashes)"
open_reminders
assert_eq "the Reminders page is showing" "yes" "$(has_node "$ROW_DIR/reminders.xml" reminders_appbar_add)"
PID0="$(adb shell pidof $PKG | tr -d '\r')"
tap_node "$ROW_DIR/reminders.xml" reminders_appbar_add
sleep 4
AFTER="$(crashes)"
PID1="$(adb shell pidof $PKG | tr -d '\r')"
adb logcat -d -b crash 2>/dev/null | grep -B2 -A6 "No ActivityResultRegistryOwner" | tail -12 > "$ROW_DIR/crash-new.txt"
note "crash count $BEFORE -> $AFTER; pid $PID0 -> $PID1"
assert_eq "(1) + on the Reminders page: a new 'No ActivityResultRegistryOwner' crash" "$((BEFORE + 1))" "$AFTER"
assert_ne "(1) the launcher's process died (its pid changed)" "$PID0" "$PID1"
adb shell input keyevent KEYCODE_HOME; sleep 3

# (2) the card's Add a photo
ensure_start
cortana_assist
for i in 1 2 3 4 5 6; do sleep 1; dump_ui "$ROW_DIR/.tess.xml"; [ "$(has_node "$ROW_DIR/.tess.xml" cortana_text_box_field)" = yes ] && break; done
type_request "remind me to check the QA13 repro list tomorrow at 9 am" 8
dump_ui "$ROW_DIR/card.xml"
assert_eq "(2) the confirm card offers Add a photo" "yes" "$(has_node "$ROW_DIR/card.xml" cortana_card_add_photo)"
FOCUS0="$(adb shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r')"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/card.xml" cortana_card_add_photo
sleep 3
FOCUS1="$(adb shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r')"
dump_ui "$ROW_DIR/card-after.xml"
screencap "$ROW_DIR/card-after.png"
note "focus before: $FOCUS0"
note "focus after:  $FOCUS1"
assert_absent "(2) Add a photo opens no photo picker (focus stays on the session)" "photopicker" "$FOCUS1"
assert_eq "(2) the card is still showing, unchanged" "yes" "$(has_node "$ROW_DIR/card-after.xml" cortana_card_add_photo)"
tap_node "$ROW_DIR/card-after.xml" "cortana_card_button:cancel"
sleep 2
cortana_close
row_end
