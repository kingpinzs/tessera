#!/usr/bin/env bash
# Put a freshly wiped AVD into the state every phase 03 row assumes.
#
#   provision.sh
#
# This exists because the gate's rows each start "from the baseline state" (PLAN RV12) and that
# baseline was, until now, whatever the emulator happened to be carrying. A wiped AVD is the only way
# to be sure, and after this session there is a second reason: the AVD's input can wedge — taps stop
# activating anything, including Android's OWN chooser dialog, while scroll gestures still work — and
# nothing short of a wipe reliably clears it.
#
# What it sets up:
#   * the shell installed with -g, which is what allow-lists READ_CALL_LOG and READ_SMS
#   * the HOME and ASSISTANT roles, AND the preferred home activity, so pressing Home does not raise
#     the app chooser (which absorbs every tap and hides Start behind it)
#   * the E2 fixture apps in their slots, and a contact for the call / text / person-reminder rows
#   * the AVD's host microphone, for the audio route
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"

say() { printf '\n== %s\n' "$*"; }

say "waiting for the device"
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
sleep 15
echo "$(adb shell getprop ro.build.fingerprint | tr -d '\r')"

say "installing the shell"
[ -f "$APK" ] || { echo "no APK at $APK - run ./gradlew :app:assembleDebug first" >&2; exit 2; }
# -g is load-bearing: it allow-lists the two hard-restricted permissions person reminders need. The
# shell is never installed with --restrict-permissions (phase 03 Decisions).
adb install -r -g "$APK"

say "roles"
adb shell cmd role add-role-holder android.app.role.HOME app.tileshell
adb shell cmd role add-role-holder android.app.role.ASSISTANT app.tileshell
# The role alone is not enough: without a preferred home ACTIVITY, pressing Home raises the chooser,
# which sits in front of Start and swallows every tap the drivers make.
adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity
echo "home=$(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"
echo "assistant=$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"

say "fixture apps"
# The E2 table names these; they are installed for tests only and never bundled.
for pkg in org.fossify.messages app.organicmaps org.oxycblt.auxio org.fossify.camera org.fossify.notes; do
  if adb shell pm list packages "$pkg" | grep -q "$pkg"; then
    echo "have $pkg"
  else
    echo "MISSING $pkg - install it before running E2"
  fi
done
adb shell cmd role add-role-holder android.app.role.SMS org.fossify.messages >/dev/null 2>&1

say "a contact for the call, text and person-reminder rows"
existing="$(adb shell content query --uri content://com.android.contacts/data/phones --projection display_name 2>/dev/null | grep -c 'display_name=Mom')"
if [ "$existing" -gt 0 ]; then
  echo "Mom already exists"
else
  adb shell content insert --uri content://com.android.contacts/raw_contacts \
    --bind account_name:s:qa --bind account_type:s:qa >/dev/null 2>&1
  rid="$(adb shell content query --uri content://com.android.contacts/raw_contacts --projection _id 2>/dev/null \
    | tail -1 | grep -oE '_id=[0-9]+' | cut -d= -f2)"
  adb shell content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:"$rid" \
    --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:Mom >/dev/null 2>&1
  adb shell content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:"$rid" \
    --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:5551234567 --bind data2:i:2 >/dev/null 2>&1
  echo "created Mom"
fi
adb shell content query --uri content://com.android.contacts/data/phones --projection display_name:data1 2>/dev/null | head -3

say "the audio route"
"$HERE/audio.sh" setup

say "checking that a tap actually activates something"
# The check that would have saved this session a great deal of time. If a tap on Start's own Search key
# does not open Cortana, the AVD's input is wedged and NO tap-driven row can be trusted — wipe and
# start again rather than reading the failures as product defects.
adb shell input keyevent KEYCODE_HOME
sleep 5
dump_ui "${TMPDIR:-/tmp}/provision.xml" || true
b="$(bounds "${TMPDIR:-/tmp}/provision.xml" nav_search)"
if [ -z "$b" ]; then
  echo "FAIL: Start is not showing (is the chooser up?)"
  exit 3
fi
set -- $b
adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
sleep 5
dump_ui "${TMPDIR:-/tmp}/provision2.xml" || true
if [ "$(has_node "${TMPDIR:-/tmp}/provision2.xml" cortana_session)" = yes ]; then
  echo "OK: a tap opens Cortana. The device is ready."
  adb shell input keyevent KEYCODE_BACK
else
  echo "FAIL: a tap on the Search key did nothing."
  echo "      The AVD's input is wedged. Wipe it (emulator -avd <name> -wipe-data) and run this again."
  echo "      Do NOT read tap-driven row failures as product defects until this passes."
  exit 4
fi
