#!/usr/bin/env bash
# Probe (EDGE_HOME_ONCE run 4's one FAIL, "no StartActivity before Home" got 1): the driver's exact setup sequence, then what
# StartActivity record exists before Home is pressed, in which task and state, and whether the process is up.
export PATH="$HOME/Android/Sdk/platform-tools:$PATH" ANDROID_SERIAL=emulator-5554
D="$(cd "$(dirname "$0")" && pwd)"; PKG=app.tileshell
echo "== $(date -Is) on $ANDROID_SERIAL"
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 1
adb shell pm clear $PKG >/dev/null
PROVISION_FINISH_WIZARD=0 bash "$D/../../../phase-03/scripts/provision.sh" > "$D/provision.txt" 2>&1; echo "provision rc=$?"
echo "after provision: pid=[$(adb shell pidof $PKG | tr -d '\r')]"; adb shell dumpsys activity activities > "$D/acts-1-after-provision.txt"
adb shell cmd role remove-role-holder android.app.role.HOME $PKG
adb shell cmd package clear-package-preferred-activities $PKG
adb shell dumpsys activity activities > "$D/acts-2-after-role-removed.txt"
adb shell am force-stop $PKG
sleep 0.5; adb shell dumpsys activity activities > "$D/acts-3-after-force-stop-0s.txt"
sleep 3;   adb shell dumpsys activity activities > "$D/acts-4-after-force-stop-3s.txt"
echo "after force-stop: pid=[$(adb shell pidof $PKG | tr -d '\r')]"
for f in "$D"/acts-*.txt; do
  echo "-- ${f##*/}"
  grep -nE "Hist .*StartActivity|topResumedActivity|\* Task\{.*(tileshell|type=home)" "$f" | tr -d '\r' | head -8
  grep -A12 -E "Hist .*StartActivity" "$f" | grep -oE "(state|finishing|visibleRequested|app)=[^ ]+" | tr -d '\r' | head -6
done
echo "-- restore"
adb shell cmd role add-role-holder android.app.role.HOME $PKG
adb shell cmd package set-home-activity $PKG/$PKG.StartActivity >/dev/null
PROVISION_FINISH_WIZARD=1 bash "$D/../../../phase-03/scripts/provision.sh" > "$D/provision-restore.txt" 2>&1; echo "restore provision rc=$?"
adb shell input keyevent KEYCODE_HOME
