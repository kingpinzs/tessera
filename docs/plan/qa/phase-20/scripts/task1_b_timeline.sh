#!/usr/bin/env bash
# Phase 20 build task 1 (b), second reading: what the active network does, second by second, when the Wi-Fi network is
# set metered and when it is put back (the first reading caught a moment on CELLULAR). usage: task1_b_timeline.sh <out dir>
set -uo pipefail
export ANDROID_SERIAL=emulator-5554 PATH="$HOME/Android/Sdk/platform-tools:$PATH"
OUT="${1:?out dir}"; mkdir -p "$OUT"
state() {
  local list ifaces active nc
  list="$(adb shell cmd netpolicy list wifi-networks | tr -d '\r' | sort -u | tr '\n' ' ')"
  ifaces="$(adb shell dumpsys netpolicy | tr -d '\r' | grep -m1 'Metered ifaces')"
  active="$(adb shell dumpsys connectivity | tr -d '\r' | sed -n 's/^Active default network: //p' | head -1)"
  nc="$(adb shell dumpsys connectivity | tr -d '\r' | grep -m1 "NetworkAgentInfo{network{$active}" | grep -o 'Transports: [A-Z]* Capabilities: [A-Z_&]*' | sed -E 's/Capabilities: .*(NOT_METERED).*/\1/; t; s/Capabilities: .*/METERED/')"
  echo "list=[$list] $ifaces | default network $active: $nc"
}
watch() { local t0 i; t0=$(date +%s.%N); for i in $(seq 1 "$1"); do printf '  +%4.1f s  %s\n' "$(echo "$(date +%s.%N) - $t0" | bc)" "$(state)"; command sleep 0.6; done; }
{
  echo "start $(date)"; echo "BEFORE   $(state)"
  echo "\$ adb shell cmd netpolicy set metered-network AndroidWifi true"; adb shell cmd netpolicy set metered-network AndroidWifi true; echo "rc=$? (the command prints nothing and exits 255 even when it took effect)"
  watch 12
  echo "\$ adb shell cmd netpolicy set metered-network AndroidWifi undefined   (the value it had: the list reads 'none')"; adb shell cmd netpolicy set metered-network AndroidWifi undefined; echo "rc=$?"
  watch 12
  echo "RESTORED $(state)"
  echo "10.0.2.2 from the emulator after the restore: $(adb shell "printf 'GET /__qa/ready HTTP/1.0\r\n\r\n' | toybox timeout 6 nc -w 5 10.0.2.2 ${P20_RADIO_PORT:-8092} | head -1" | tr -d '\r')"
  echo "end $(date)"
} > "$OUT/b-netpolicy-timeline.txt" 2>&1
