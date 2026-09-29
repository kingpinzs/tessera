#!/usr/bin/env bash
# Review r4-phone B1: an unplanned kill mid-run, then a NEW run: it must put the old run back first (its own baseline)
# and stop the old helper before its own baseline.
export PATH="$HOME/Android/Sdk/platform-tools:$PATH" ANDROID_SERIAL=emulator-5554
P=app.tessera.r4probe
adb shell am start -W -n $P/.ProbeActivity > /dev/null; sleep 2
T="$(adb shell run-as $P cat files/run-token | tr -d '\r')"
adb shell am start -n $P/.ProbeActivity --es run start --ez auto true --es token "$T" > /dev/null
until adb shell run-as $P cat files/run-details.txt 2>/dev/null | grep -q 'flip away'; do sleep 1; done
sleep 4
echo "killing mid-toggle at $(date +%T): stage $(adb shell run-as $P cat files/run-state.properties | tr -d '\r' | grep ^stage=)"
echo "helpers before: $(adb shell 'ps -A -o PID,ARGS' | grep -c 'HelperMain --daemon')"
adb shell am force-stop $P; sleep 3
adb shell am start -W -n $P/.ProbeActivity > /dev/null; sleep 2
adb shell am start -n $P/.ProbeActivity --es run start --ez auto true --es token "$T" > /dev/null
for i in $(seq 1 200); do sleep 5; st="$(adb shell run-as $P cat files/run-state.properties 2>/dev/null | tr -d '\r' | grep ^stage= | cut -d= -f2)"; [ "$st" = done ] && break; done
echo "=== second run SUMMARY"; adb shell run-as $P cat files/run-summary.txt | tr -d '\r'
echo "=== its details: the restore of the first run and the leftovers"
adb shell run-as $P cat files/run-details.txt | tr -d '\r' | grep -E 'earlier run|left by an earlier|earlier helper' | head
echo "=== after: helpers $(adb shell 'ps -A -o PID,ARGS' | grep -c 'HelperMain --daemon'); wifi $(adb shell cmd wifi status | head -1 | tr -d '\r'); bt $(adb shell settings get global bluetooth_on | tr -d '\r'); location $(adb shell cmd location is-location-enabled | tr -d '\r'); auto_time $(adb shell settings get global auto_time | tr -d '\r'); low_power $(adb shell settings get global low_power | tr -d '\r')"
