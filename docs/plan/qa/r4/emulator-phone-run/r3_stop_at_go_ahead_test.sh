#!/usr/bin/env bash
# Review r4-phone r3 note: a run stopped at "Go ahead" (adb reachable, baseline read) changes nothing: baseline_taken is
# never set, so the clean-up is skipped, and every toggle, Power saving and the battery report read the same after.
export PATH="$HOME/Android/Sdk/platform-tools:$PATH" ANDROID_SERIAL=emulator-5554
P=app.tessera.r4probe
tok() { adb shell run-as $P cat files/run-token | tr -d '\r'; }
st() { adb shell run-as $P cat files/run-state.properties 2>/dev/null | tr -d '\r' | grep ^stage= | cut -d= -f2; }
snap() { echo "wifi=$(adb shell settings get global wifi_on | tr -d '\r') bt=$(adb shell settings get global bluetooth_on | tr -d '\r') loc=$(adb shell settings get secure location_mode | tr -d '\r') time=$(adb shell settings get global auto_time | tr -d '\r') low_power=$(adb shell settings get global low_power | tr -d '\r') sticky=$(adb shell settings get global low_power_sticky | tr -d '\r') usb=$(adb shell settings get global adb_enabled | tr -d '\r') battery_ac=$(adb shell dumpsys battery | grep 'AC powered' | tr -d '\r ')"; }
adb shell settings put global adb_wifi_enabled 1; sleep 2
adb shell am start -W -n $P/.ProbeActivity > /dev/null; sleep 2; T="$(tok)"
B="$(snap)"; echo "before: $B"
adb shell am start -n $P/.ProbeActivity --es run start --es token "$T" > /dev/null
for i in $(seq 1 90); do sleep 2; adb shell run-as $P cat files/report.txt 2>/dev/null | grep -q 'flip each of these' && break; done
echo "at the Go ahead prompt: stage $(st)"
adb shell run-as $P cat files/run-state.properties | tr -d '\r' | grep -c '^baseline_taken=' | sed 's/^/baseline_taken lines while asking: /'
adb shell am start -n $P/.ProbeActivity --es run answer --es answer Stop --es token "$T" > /dev/null
for i in $(seq 1 20); do sleep 2; [ "$(st)" = done ] && break; done
echo "stage after Stop: $(st)"; adb shell run-as $P cat files/run-details.txt | tr -d '\r' | tail -3
A="$(snap)"; echo "after:  $A"
[ "$A" = "$B" ] && echo "SAME: nothing changed" || echo "DIFFERENT"
echo "helpers left: $(adb shell 'ps -A -o PID,ARGS' | grep -c 'HelperMain --daemon')"
