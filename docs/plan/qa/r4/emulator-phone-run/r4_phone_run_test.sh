#!/usr/bin/env bash
# Emulator end-to-end test of the phone-only R4 run (auto mode). Pairs through PairTestReceiver if the run asks.
export PATH="$HOME/Android/Sdk/platform-tools:$PATH" ANDROID_SERIAL=emulator-5554
S=/tmp/claude-1000/-home-jeremyking/9dace4c7-c342-4879-961a-84511afd4f90/scratchpad
cd /home/jeremyking/projects/metro-launcher
adb install -r r4probe/build/outputs/apk/debug/r4probe-debug.apk | tail -1
adb shell pm grant app.tessera.r4probe android.permission.POST_NOTIFICATIONS
adb shell settings put global adb_wifi_enabled 1; sleep 2
adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -W -n app.tessera.r4probe/.ProbeActivity > /dev/null; sleep 2
TOKEN="$(adb shell run-as app.tessera.r4probe cat files/run-token | tr -d '\r')"
adb shell am start -n app.tessera.r4probe/.ProbeActivity --es run start --ez auto true --es token "$TOKEN" > /dev/null
paired=0
for i in $(seq 1 240); do
  sleep 5
  SUMM="$(adb shell run-as app.tessera.r4probe cat files/run-summary.txt 2>/dev/null | tr -d '\r')"
  STAGE="$(adb shell run-as app.tessera.r4probe cat files/run-state.properties 2>/dev/null | tr -d '\r' | grep '^stage=' | cut -d= -f2)"
  echo "$(date +%T) stage=$STAGE lines=$(echo "$SUMM" | grep -c .)"
  if [ "$STAGE" = pair ] && [ $paired = 0 ] && [ $i -ge 4 ] && ! echo "$SUMM" | grep -q 'PASS.*pair\|already paired'; then
    OUTP="$($S/wd_pair_dialog.sh)"; read -r ADDR CODE <<< "$(echo "$OUTP" | head -1)"
    if [ -n "$CODE" ]; then
      adb shell am broadcast -n app.tessera.r4probe/.PairTestReceiver --es host ${ADDR%:*} --ei port ${ADDR#*:} --es code $CODE > /dev/null; paired=1
      echo "sent pairing code for $ADDR"
      sleep 3; adb shell input keyevent KEYCODE_BACK; adb shell am start -n app.tessera.r4probe/.ProbeActivity > /dev/null
    fi
  fi
  [ "$STAGE" = done ] && break
done
echo "=== SUMMARY"; adb shell run-as app.tessera.r4probe cat files/run-summary.txt | tr -d '\r'
adb shell run-as app.tessera.r4probe cat files/run-details.txt | tr -d '\r' > $S/r4-phone-run-details.txt
echo "details: $(wc -l < $S/r4-phone-run-details.txt) lines"
echo "=== after: wifi $(adb shell cmd wifi status | head -1 | tr -d '\r'); airplane $(adb shell cmd connectivity airplane-mode | tr -d '\r'); low_power $(adb shell settings get global low_power | tr -d '\r'); deep $(adb shell dumpsys deviceidle get deep | tr -d '\r'); helpers $(adb shell 'ps -A -o ARGS' | grep -c 'r4probe.HelperMain --daemon')"
