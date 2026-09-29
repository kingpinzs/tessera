#!/usr/bin/env bash
# Review r4-phone r2: B4 — a run stopped at the pairing prompt (Wireless debugging off, so the saved key cannot connect)
# changes nothing and ends 'done'; Power saving untouched. B5 — after an unplanned kill mid-run, opening the app shows the
# unfinished-run offer (read from the screen), and Restore now puts it back.
export PATH="$HOME/Android/Sdk/platform-tools:$PATH" ANDROID_SERIAL=emulator-5554
P=app.tessera.r4probe; S=/tmp/claude-1000/-home-jeremyking/9dace4c7-c342-4879-961a-84511afd4f90/scratchpad
tok() { adb shell run-as $P cat files/run-token | tr -d '\r'; }
st() { adb shell run-as $P cat files/run-state.properties 2>/dev/null | tr -d '\r' | grep ^stage= | cut -d= -f2; }
screen() { adb shell uiautomator dump /sdcard/r4ui.xml > /dev/null 2>&1; adb pull -q /sdcard/r4ui.xml $S/r4ui.xml > /dev/null; grep -o 'text="[^"]*"' $S/r4ui.xml | grep -i -E 'did not finish|Restore now|Carry on|could not put' ; }
echo "=== B4"
adb shell am start -W -n $P/.ProbeActivity > /dev/null; sleep 2; T="$(tok)"
echo "sticky before $(adb shell settings get global low_power_sticky | tr -d '\r')"
adb shell settings put global adb_wifi_enabled 0; sleep 2
adb shell am start -n $P/.ProbeActivity --es run start --es token "$T" > /dev/null
for i in $(seq 1 30); do sleep 2; adb shell run-as $P cat files/report.txt 2>/dev/null | grep -q 'notification from R4 probe appears' && break; done
echo "at the pairing prompt: stage $(st)"
adb shell am start -n $P/.ProbeActivity --es run answer --es answer Stop --es token "$T" > /dev/null
for i in $(seq 1 20); do sleep 2; [ "$(st)" = done ] && break; done
echo "stage after Stop: $(st)"; adb shell run-as $P cat files/run-details.txt | tr -d '\r' | tail -3
echo "sticky after $(adb shell settings get global low_power_sticky | tr -d '\r'); low_power $(adb shell settings get global low_power | tr -d '\r')"
adb shell settings put global adb_wifi_enabled 1; sleep 3
echo "=== B5"
adb shell am start -n $P/.ProbeActivity --es run start --ez auto true --es token "$T" > /dev/null
until adb shell run-as $P cat files/run-details.txt 2>/dev/null | grep -q 'flip away'; do sleep 1; done; sleep 3
echo "killed at stage $(st)"; adb shell am force-stop $P; sleep 3
adb shell am start -W -n $P/.ProbeActivity > /dev/null; sleep 4
echo "screen after a plain open:"; screen
X=$(python3 - "$S/r4ui.xml" <<'PY'
import re,sys
s=open(sys.argv[1]).read()
m=re.search(r'text="Restore now"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
print(f"{(int(m[1])+int(m[3]))//2} {(int(m[2])+int(m[4]))//2}" if m else "")
PY
)
[ -n "$X" ] && adb shell input tap $X && echo "tapped Restore now"
for i in $(seq 1 60); do sleep 3; [ "$(st)" = done ] && break; done
echo "stage after Restore now: $(st)"; adb shell run-as $P cat files/run-details.txt | tr -d '\r' | sed -n '/== clean-up/,$p' | head -14
echo "=== after: helpers $(adb shell 'ps -A -o PID,ARGS' | grep -c 'HelperMain --daemon'); wifi $(adb shell cmd wifi status | head -1 | tr -d '\r'); bt $(adb shell settings get global bluetooth_on | tr -d '\r'); low_power $(adb shell settings get global low_power | tr -d '\r')"
