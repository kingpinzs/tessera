#!/usr/bin/env bash
# Probe (round 1, EDGE_HOME_ONCE run 3's FAILs): after "Set as default" -> another launcher in the role sheet, who brings that
# launcher to the front, and does the wizard stay behind it? Then a control through Settings > Default apps > Home app.
export PATH="$HOME/Android/Sdk/platform-tools:$PATH" ANDROID_SERIAL=emulator-5554
D="$(cd "$(dirname "$0")" && pwd)"; PKG=app.tileshell
pick() { python3 - "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if re.fullmatch(sys.argv[2], n.get("text", "") or "", re.I) or re.fullmatch(sys.argv[2], n.get("content-desc", "") or "", re.I) or n.get("resource-id") == sys.argv[2]:
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", n.get("bounds") or "0 0 0 0")); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
}
dump() { adb shell uiautomator dump /sdcard/p.xml >/dev/null; adb pull /sdcard/p.xml "$D/$1.xml" >/dev/null; adb exec-out screencap -p > "$D/$1.png"; }
resumed() { adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r'; }
records() { adb shell dumpsys activity activities | grep -E "Hist .*(StartActivity|QuickstepLauncher)" | tr -d '\r'; }
step() { python3 -c "
import xml.etree.ElementTree as ET,sys
for n in ET.parse('$D/$1.xml').getroot().iter('node'):
  r=n.get('resource-id','')
  if r.startswith('wizard_step:'): print(r[12:]); break"; }
echo "== $(date -Is) on $ANDROID_SERIAL"
echo "-- 0. a no-marker state (pm clear, provision without the finished marker)"
adb shell am start -W -n com.android.settings/.Settings >/dev/null; sleep 1
adb shell pm clear $PKG >/dev/null
PROVISION_FINISH_WIZARD=0 bash "$D/../../../phase-03/scripts/provision.sh" > "$D/provision.txt" 2>&1; echo "provision rc=$?"
adb shell am force-stop $PKG
echo "-- 1. into the wizard through the chooser"
adb shell cmd role remove-role-holder android.app.role.HOME $PKG
adb shell cmd package clear-package-preferred-activities $PKG
adb shell input keyevent KEYCODE_HOME; sleep 3; dump c1
xy=$(pick "$D/c1.xml" Tessera); adb shell input tap $xy; sleep 1; dump c2
xy=$(pick "$D/c2.xml" 'just once'); adb shell input tap $xy; sleep 4; dump w1
echo "resumed: $(resumed)"; echo "step: $(step w1)"; p0=$(adb shell pidof $PKG | tr -d '\r'); echo "pid: $p0"
echo "-- 2. Set as default -> Quickstep -> SET AS DEFAULT"
xy=$(pick "$D/w1.xml" wizard_action); adb shell input tap $xy; sleep 3; dump s1
xy=$(pick "$D/s1.xml" Quickstep); adb shell input tap $xy; sleep 1; dump s2
xy=$(pick "$D/s2.xml" 'set as default'); adb shell input tap $xy; sleep 0.5
for t in 1 2 3; do echo "t+${t}: $(resumed)"; sleep 1; done
echo "records:"; records
echo "pid: $(adb shell pidof $PKG | tr -d '\r')  holders: $(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"
echo "-- 3. back to Tessera through Recents (no launch intent)"
adb shell input keyevent KEYCODE_APP_SWITCH; sleep 2; dump r1
echo "recents resumed: $(resumed)"
xy=$(pick "$D/r1.xml" '.*Tessera.*'); echo "Tessera card at: [$xy]"
if [ -n "$xy" ]; then adb shell input tap $xy; sleep 3; fi
dump w2; echo "resumed: $(resumed)"; echo "step: $(step w2)"; echo "pid: $(adb shell pidof $PKG | tr -d '\r') (was $p0)"
echo "-- 4. control: Settings > Default apps > Home app, choose Tessera"
adb shell am start -W -a android.settings.MANAGE_DEFAULT_APPS_SETTINGS >/dev/null; sleep 2; dump d1
xy=$(pick "$D/d1.xml" 'Home app'); adb shell input tap $xy; sleep 2; dump d2
xy=$(pick "$D/d2.xml" Tessera); adb shell input tap $xy; sleep 0.5
for t in 1 2 3; do echo "t+${t}: $(resumed)"; sleep 1; done
dump d3
echo "holders: $(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"
echo "-- restore"
adb shell cmd role add-role-holder android.app.role.HOME $PKG
adb shell cmd package set-home-activity $PKG/$PKG.StartActivity >/dev/null
adb shell input keyevent KEYCODE_HOME; sleep 2
echo "holders: $(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')  resumed: $(resumed)"
