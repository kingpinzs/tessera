#!/usr/bin/env bash
# Phase 20 build task 1 (b): the id form `cmd netpolicy set metered-network <id> true` takes on the AVD (row A3).
# usage: task1_b.sh <out dir>
# Reads the saved Wi-Fi networks, sets the connected one metered, reads back that the ACTIVE network is metered, then
# restores it (false, then the value it had — `undefined` reads back as "none") and proves the restore.
set -uo pipefail
export ANDROID_SERIAL=emulator-5554 PATH="$HOME/Android/Sdk/platform-tools:$PATH"
OUT="${1:?out dir}"; mkdir -p "$OUT"
state() { # one line: the list, the policy's metered ifaces, and the active default network's metered capability
  local list ifaces active nc
  list="$(adb shell cmd netpolicy list wifi-networks | tr -d '\r' | sort -u | tr '\n' ' ')"
  ifaces="$(adb shell dumpsys netpolicy | tr -d '\r' | grep -m1 'Metered ifaces')"
  active="$(adb shell dumpsys connectivity | tr -d '\r' | sed -n 's/^Active default network: //p' | head -1)"
  nc="$(adb shell dumpsys connectivity | tr -d '\r' | grep -m1 "NetworkAgentInfo{network{$active}" | grep -o 'Transports: [A-Z]* Capabilities: [A-Z_&]*' | sed -E 's/Capabilities: .*(NOT_METERED).*/\1/; t; s/Capabilities: .*/(no NOT_METERED capability = METERED)/')"
  echo "list=[$list] $ifaces | active default network $active: $nc"
}
wait_for() { # wait_for <grep pattern on state> <seconds>
  local i; for i in $(seq 1 "$2"); do state | grep -q -- "$1" && return 0; command sleep 1; done; return 1
}
{
  echo "start $(date)"
  echo "ssid: $(adb shell cmd wifi status | tr -d '\r' | grep -m1 -o 'Wifi is connected to "[^"]*"')"
  echo "BEFORE            $(state)"
  ID="$(adb shell cmd netpolicy list wifi-networks | tr -d '\r' | head -1 | cut -d';' -f1)"; WAS="$(adb shell cmd netpolicy list wifi-networks | tr -d '\r' | head -1 | cut -d';' -f2)"
  echo "the id as the list prints it: [$ID]; its value before: [$WAS]"
  echo "\$ adb shell cmd netpolicy set metered-network $ID true"; adb shell cmd netpolicy set metered-network "$ID" true; echo "rc=$?"
  wait_for '(no NOT_METERED capability = METERED)' 20; echo "metered within 20 s: rc=$?"
  echo "AFTER true        $(state)"
  echo "isActiveNetworkMetered by the framework: $(adb shell dumpsys connectivity | tr -d '\r' | grep -m1 -i 'metered' | cut -c1-160)"
  echo "\$ adb shell cmd netpolicy set metered-network $ID false"; adb shell cmd netpolicy set metered-network "$ID" false; echo "rc=$?"
  wait_for ': Transports: WIFI NOT_METERED' 20; echo "unmetered within 20 s: rc=$?"
  echo "AFTER false       $(state)"
  echo "\$ adb shell cmd netpolicy set metered-network $ID undefined"; adb shell cmd netpolicy set metered-network "$ID" undefined; echo "rc=$?"
  command sleep 3
  echo "AFTER undefined   $(state)"
  echo "other id forms, for the record (each read back, then put back to undefined):"
  for form in "\"$ID\"" "'\"$ID\"'" 0; do
    echo "\$ adb shell cmd netpolicy set metered-network $form true -> $(adb shell "cmd netpolicy set metered-network $form true" 2>&1 | tr -d '\r' | tr '\n' ' ') rc=$?"
    command sleep 4; echo "    $(state)"
    adb shell "cmd netpolicy set metered-network $form undefined" >/dev/null 2>&1; adb shell cmd netpolicy set metered-network "$ID" undefined >/dev/null 2>&1; command sleep 3
  done
  wait_for ': Transports: WIFI NOT_METERED' 20
  echo "RESTORED          $(state)"
  echo "wifi: $(adb shell cmd wifi status | tr -d '\r' | grep -m1 -o 'Wifi is connected to "[^"]*"')"
  echo "end $(date)"
} > "$OUT/b-netpolicy.txt" 2>&1
