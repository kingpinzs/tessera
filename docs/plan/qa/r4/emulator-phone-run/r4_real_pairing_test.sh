#!/usr/bin/env bash
# The phone's own pairing path, as Jeremy does it (found on the S25 Ultra 2026-09-29: the probe never declared
# POST_NOTIFICATIONS, so the code notification never appeared; the other harnesses pair through PairTestReceiver).
# Fresh install (no key), notifications denied first, then allowed; the code is typed into the notification's reply.
export PATH="$HOME/Android/Sdk/platform-tools:$PATH" ANDROID_SERIAL=emulator-5554
P=app.tessera.r4probe; S=/tmp/claude-1000/-home-jeremyking/9dace4c7-c342-4879-961a-84511afd4f90/scratchpad
D=$(cd "$(dirname "$0")" && pwd)
dump() { adb shell uiautomator dump /sdcard/rp.xml > /dev/null 2>&1; adb pull -q /sdcard/rp.xml $S/rp.xml > /dev/null; }
center() { python3 - "$S/rp.xml" "$1" <<'PY'
import re,sys
s=open(sys.argv[1]).read()
m=re.search(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % sys.argv[2], s, re.I)
if m: a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2)
PY
}
prompt() { dump; python3 - "$S/rp.xml" <<'PY'
import re,sys
t=[x for x in re.findall(r'text="([^"]*)"', open(sys.argv[1]).read()) if 'notification' in x.lower() or 'Wireless debugging:' in x]
print(t[0][:110] if t else '(no prompt)')
PY
}
st() { adb shell run-as $P cat files/run-state.properties 2>/dev/null | tr -d '\r' | grep ^stage= | cut -d= -f2; }
adb uninstall $P > /dev/null; adb install ../../../../../r4probe/build/outputs/apk/debug/r4probe-debug.apk | tail -1
echo "manifest asks for: $(adb shell dumpsys package $P | sed -n '/requested permissions:/,/install permissions:/p' | grep -c POST_NOTIFICATIONS) POST_NOTIFICATIONS"
adb shell settings put global adb_wifi_enabled 1; adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -W -n $P/.ProbeActivity > /dev/null; sleep 3
dump; xy=$(center "Don.t allow"); echo "permission dialog: ${xy:+shown}"; [ -n "$xy" ] && adb shell input tap $xy; sleep 1
T=$(adb shell run-as $P cat files/run-token | tr -d '\r')
dump; adb shell input tap $(center "Run R4 on this phone"); sleep 4
echo "stage $(st); prompt with notifications denied: $(prompt)"
adb shell pm grant $P android.permission.POST_NOTIFICATIONS   # = allowing them in the notification settings
adb shell am start -n $P/.ProbeActivity --es run answer --es answer Done --es token "$T" > /dev/null; sleep 3
echo "prompt after allowing: $(prompt)"
read -r ADDR CODE <<< "$(bash $D/wd_pair_dialog.sh | head -1)"; echo "pairing dialog open: $ADDR code ${CODE:+(6 digits)}"
for i in $(seq 1 20); do adb shell dumpsys notification --noredact | grep -q 'pkg=app.tessera.r4probe.*id=7' && break; sleep 2; done
echo "notification id 7 posted: $(adb shell dumpsys notification --noredact | grep -c 'pkg=app.tessera.r4probe.*id=7')"
adb shell cmd statusbar expand-notifications; sleep 2
dump; xy=$(center "Enter code"); [ -z "$xy" ] && { dump; xy=$(center "Enter code"); }
echo "Enter code action: ${xy:-NOT FOUND}"; adb shell input tap $xy; sleep 1.5
adb shell input text "$CODE"; sleep 0.5; adb shell input keyevent KEYCODE_ENTER; sleep 1
dump; xy=$(center "Send|Reply"); [ -n "$xy" ] && adb shell input tap $xy
for i in $(seq 1 30); do sleep 2; adb shell run-as $P cat files/run-summary.txt | grep -q 'paired\|pairing did not' && break; done
adb shell cmd statusbar collapse; adb shell input keyevent KEYCODE_BACK
echo "=== summary"; adb shell run-as $P cat files/run-summary.txt | tr -d '\r'
echo "=== pairing details"; adb shell run-as $P cat files/run-details.txt | tr -d '\r' | grep -i 'pairing\|adb id'
for i in $(seq 1 30); do sleep 2; adb shell run-as $P cat files/report.txt 2>/dev/null | grep -q 'flip each of these' && break; done
echo "at Go ahead: stage $(st); answering Stop"
adb shell am start -n $P/.ProbeActivity --es run answer --es answer Stop --es token "$T" > /dev/null
for i in $(seq 1 20); do sleep 2; [ "$(st)" = done ] && break; done; echo "stage after Stop: $(st)"
