#!/usr/bin/env bash
# R4 — the on-phone helper spike (PLAN.md R4; INDEX R4 row), run from the PC with the S25 Ultra on USB.
# Read README.md next to this file first. One command gathers every R4 answer into one run folder:
#   runs/<date>-<model>/SUMMARY.txt   one line per proof (PASS / FAIL / SKIPPED) and the file that shows it
# Parts: 1 app_process as the shell uid · 2 daemonise + binder handoff · 3 each toggle as the shell uid (direct, then
# through the helper) · 4 survival (app killed, Doze, cable pulled, Wireless debugging off) · blur (normal and power
# saving) · P5 nav-bar overlay (gesture and 3-button) · PQ2 voicemail (what the shell can see; no carrier login).
# Every toggle it flips is restored, and the end re-reads them all against the baseline taken at the start.
# Environment: R4_YES=1 skips the "flip these toggles?" question; R4_AUTO=1 skips the hands-on steps (they are
# recorded as SKIPPED); R4_ALLOW_ANY=1 allows a device that is not an S25 Ultra (the emulator dry run); R4_KEEP=1 keeps
# the probe installed at the end.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
PKG=app.tessera.r4probe
APK="$REPO/r4probe/build/outputs/apk/debug/r4probe-debug.apk"
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
# adb shell forwards stdin to the device: inside a `while read` loop it swallows the rest of the list (the first dry
# run baselined, flipped and checked ONE toggle of nine), and at a prompt it can eat what was typed. No adb shell here
# reads stdin; the loops also read their list on fd 3.
adb() { case " $* " in *" shell "*) command adb "$@" < /dev/null ;; *) command adb "$@" ;; esac; }

# ---------------------------------------------------------------- the device
# The phone is the one physical USB device: emulators (emulator-*) and wireless connections (ip:port) are ignored, since
# this PC also runs the QA emulators. ANDROID_SERIAL, when set to an attached device, wins (the emulator dry run).
if [ -n "${ANDROID_SERIAL:-}" ] && adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -qx "$ANDROID_SERIAL"; then :
else
  mapfile -t DEVS < <(adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ && $1 !~ /:/ {print $1}')
  if [ "${#DEVS[@]}" -ne 1 ]; then
    echo "R4 needs exactly ONE phone on USB (found ${#DEVS[@]}: ${DEVS[*]:-none}). Check the cable, USB debugging, and the phone's 'Allow USB debugging?' prompt."
    adb devices; exit 2
  fi
  export ANDROID_SERIAL="${DEVS[0]}"
fi
MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM-S938*) ;;
  *) if [ "${R4_ALLOW_ANY:-0}" != 1 ]; then echo "This is '$MODEL', not an S25 Ultra (SM-S938*). R4 is for the phone; set R4_ALLOW_ANY=1 for a dry run."; exit 2; fi ;;
esac
OUT="$HERE/runs/$(date +%Y%m%d-%H%M%S)-$(echo "$MODEL" | tr -c 'A-Za-z0-9_-' '_')"
mkdir -p "$OUT"
SUM="$OUT/SUMMARY.txt"
say() { echo "$*" | tee -a "$SUM"; }
result() { printf '%-9s %-44s %s\n' "$1" "$2" "$3" | tee -a "$SUM"; }   # PASS|FAIL|SKIPPED|INFO  what  file
ask() { # message -> 0 when done, 1 when skipped (R4_AUTO=1 skips every one)
  if [ "${R4_AUTO:-0}" = 1 ]; then echo "  (R4_AUTO: skipped — $1)"; return 1; fi
  printf '\n>>> %s\n>>> Press Enter when done, or type s then Enter to skip: ' "$1"
  local a; read -r a; [ "$a" != s ]
}
report() { adb shell run-as $PKG cat files/report.txt 2>/dev/null | tr -d '\r'; }
wait_report() { # marker timeout_s -> 0 when the app's report shows the marker
  local end=$(( $(date +%s) + $2 ))
  while [ "$(date +%s)" -lt "$end" ]; do report | grep -q "$1" && return 0; sleep 2; done
  return 1
}
section() { report | sed -n "/^== $1/,/^== /p" | sed '$d'; }   # one section of the app's report, without the next header
NONCE=0
run_app() { NONCE="$(date +%s)$RANDOM"; adb shell am start -n $PKG/.ProbeActivity --es run "$1" --es nonce "$NONCE" > /dev/null 2>&1; }

say "R4 run $(date -Is) on $MODEL ($ANDROID_SERIAL)"
{
  for p in ro.product.model ro.build.fingerprint ro.build.version.release ro.build.version.sdk ro.build.version.oneui \
           ro.build.version.security_patch ro.surface_flinger.supports_background_blur; do
    echo "$p=$(adb shell getprop $p | tr -d '\r')"
  done
  echo "shell id: $(adb shell id | tr -d '\r')"
  echo "navigation_mode=$(adb shell settings get secure navigation_mode | tr -d '\r')"
} > "$OUT/device.txt"
result INFO "device facts" device.txt

# ---------------------------------------------------------------- the probe APK
if [ ! -f "$APK" ] || [ "${R4_BUILD:-0}" = 1 ]; then
  ( cd "$REPO" && ./gradlew -q :r4probe:assembleDebug ) > "$OUT/build.txt" 2>&1 || { say "the probe APK did not build (build.txt)"; exit 3; }
fi
echo "apk sha256 $(sha256sum "$APK" | cut -c1-16)" >> "$OUT/device.txt"
adb install -r "$APK" > "$OUT/install.txt" 2>&1 || { say "the probe APK did not install (install.txt)"; exit 3; }
APKP="$(adb shell pm path $PKG | tr -d '\r' | sed 's/^package://')"
HELPER="CLASSPATH=$APKP app_process /system/bin app.tessera.r4probe.HelperMain"
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow

# The toggle list comes from the helper itself (Toggles.kt), so the direct pass and the helper pass flip the same things.
adb shell "$HELPER --list-toggles" | tr -d '\r' > "$OUT/toggles.txt"
# toggles.txt is tab-separated (the commands and patterns contain "|").
read_toggle() { adb shell "$(awk -F'\t' -v n="$1" '$1==n {print $2}' "$OUT/toggles.txt")" 2>&1 | tr -d '\r' | tr '\n' ' ' | sed 's/ *$//'; }
while IFS=$'\t' read -r name rd on off pat <&3; do echo "$name=$(read_toggle "$name")"; done 3< "$OUT/toggles.txt" > "$OUT/baseline.txt"
result INFO "toggle baseline (restored to this at the end)" baseline.txt

echo
echo "R4 will flip each of these on the phone and flip it straight back (twice: directly, then through the helper):"
cut -f1 "$OUT/toggles.txt" | tr '\n' ' '; echo
echo "Wi-Fi, mobile data and airplane mode drop the phone's connections for a few seconds each; USB is unaffected."
if [ "${R4_YES:-0}" != 1 ]; then printf 'Go ahead? [y/N] '; read -r go; [ "$go" = y ] || { say "stopped before any toggle was flipped"; exit 0; }; fi

# ---------------------------------------------------------------- part 1: app_process as the shell uid
adb shell "$HELPER --probe" > "$OUT/part1-app_process.txt" 2>&1
if grep -q '^uid *2000' "$OUT/part1-app_process.txt"; then result PASS "1 app_process runs as the shell uid" part1-app_process.txt
else result FAIL "1 app_process runs as the shell uid" part1-app_process.txt; fi

# ---------------------------------------------------------------- part 2: daemonise + binder handoff
helper_pid() { adb shell cat /data/local/tmp/r4helper-$1.pid 2>/dev/null | tr -d '\r'; }
alive() { [ -n "$1" ] && [ "$(adb shell "ps -o PID -p $1 | tail -n +2" | tr -d '\r ' )" = "$1" ]; }
ping_ok() { # tag -> 0 when the app's ping reached the helper with that tag
  run_app helper_ping; sleep 1
  wait_report "HELPER_PING DONE $NONCE" 15 && section 'HELPER (R4' | grep -q "ping: helper tag=$1 uid=2000"
}
adb shell "rm -f /data/local/tmp/r4helper-usb.* /data/local/tmp/r4helper-wifi.*"
adb shell "CLASSPATH=$APKP setsid nohup app_process /system/bin app.tessera.r4probe.HelperMain --daemon usb > /dev/null 2>&1 < /dev/null &"
sleep 2
HUSB="$(helper_pid usb)"
adb shell am start -W -n $PKG/.ProbeActivity > /dev/null 2>&1
sleep 5
{
  echo "helper pid $HUSB"; adb shell "ps -o PID,PPID,USER,NAME -p $HUSB" | tr -d '\r'
  echo; echo "--- helper log"; adb shell cat /data/local/tmp/r4helper-usb.log | tr -d '\r'
} > "$OUT/part2-daemon.txt"
if alive "$HUSB" && [ "$(adb shell "ps -o PPID -p $HUSB | tail -1" | tr -d '\r ')" = 1 ]; then
  result PASS "2a the helper daemonises (parent = init)" part2-daemon.txt
else result FAIL "2a the helper daemonises (parent = init)" part2-daemon.txt; fi
if ping_ok usb; then section 'HELPER (R4' >> "$OUT/part2-daemon.txt"; result PASS "2b binder handed to the app; the app's ping answered" part2-daemon.txt
else section 'HELPER (R4' >> "$OUT/part2-daemon.txt"; result FAIL "2b binder handed to the app; the app's ping answered" part2-daemon.txt; fi

# ---------------------------------------------------------------- part 3: each toggle as the shell uid
# Android will not hold battery saver on while charging (and USB charges the phone), so the battery is reported
# unplugged for both passes and reset after.
adb shell dumpsys battery unplug
{
  while IFS=$'\t' read -r name rd on off pat <&3; do
    before="$(read_toggle "$name")"
    if echo "$before" | grep -Eq "$pat"; then first="$off"; second="$on"; dir="off"; else first="$on"; second="$off"; dir="on"; fi
    o1="$(adb shell "$first" 2>&1 | tr -d '\r' | tr '\n' ' ')"
    sleep 3; mid="$(read_toggle "$name")"
    o2="$(adb shell "$second" 2>&1 | tr -d '\r' | tr '\n' ' ')"
    sleep 3; after="$(read_toggle "$name")"
    echo "  $name"
    echo "    before  $before"
    echo "    flip $dir: ${o1:-(no output)}"
    echo "    read    $mid"
    echo "    back:   ${o2:-(no output)}"
    echo "    after   $after"
    echo "    CHANGED $([ "$mid" != "$before" ] && echo yes || echo NO) · RESTORED $([ "$after" = "$before" ] && echo yes || echo NO)"
  done 3< "$OUT/toggles.txt"
} > "$OUT/part3-direct.txt" 2>&1
result INFO "3a toggles, directly as the shell uid ($(grep -c 'CHANGED yes' "$OUT/part3-direct.txt")/$(wc -l < "$OUT/toggles.txt") changed)" part3-direct.txt
run_app helper_toggles
if wait_report "HELPER_TOGGLES DONE $NONCE" 420; then
  section 'HELPER TOGGLES' > "$OUT/part3-helper.txt"
  result INFO "3b toggles, through the helper ($(grep -c 'CHANGED yes' "$OUT/part3-helper.txt")/$(wc -l < "$OUT/toggles.txt") changed)" part3-helper.txt
else section 'HELPER TOGGLES' > "$OUT/part3-helper.txt"; result FAIL "3b toggles through the helper (did not finish)" part3-helper.txt; fi
adb shell dumpsys battery reset

# ---------------------------------------------------------------- part 4: survival
{
  adb shell am force-stop $PKG; sleep 6
  echo "after force-stop: helper $HUSB alive: $(alive "$HUSB" && echo yes || echo NO)"
  adb shell am start -W -n $PKG/.ProbeActivity > /dev/null 2>&1; sleep 5
  echo "--- helper log"; adb shell tail -4 /data/local/tmp/r4helper-usb.log | tr -d '\r'
} > "$OUT/part4-appkill.txt"
if alive "$HUSB" && ping_ok usb; then result PASS "4a app killed: helper lives, re-hands the binder" part4-appkill.txt
else result FAIL "4a app killed: helper lives, re-hands the binder" part4-appkill.txt; fi

{
  adb shell dumpsys deviceidle force-idle 2>&1 | tr -d '\r'; sleep 20
  echo "in forced Doze: helper $HUSB alive: $(alive "$HUSB" && echo yes || echo NO)"
  adb shell dumpsys deviceidle unforce 2>&1 | tr -d '\r'
} > "$OUT/part4-doze.txt"
if alive "$HUSB" && ping_ok usb; then result PASS "4b forced Doze: helper lives and answers" part4-doze.txt
else result FAIL "4b forced Doze: helper lives and answers" part4-doze.txt; fi

if ask "Unplug the USB cable from the phone, wait 30 seconds, then plug it back in (accept the USB prompt if the phone shows one)."; then
  adb wait-for-device
  sleep 3
  { echo "after the cable was pulled: helper $HUSB alive: $(alive "$HUSB" && echo yes || echo NO)"; } > "$OUT/part4-cable.txt"
  if alive "$HUSB" && ping_ok usb; then result PASS "4c cable pulled: helper lives and answers" part4-cable.txt
  else result FAIL "4c cable pulled: helper lives and answers" part4-cable.txt; fi
else result SKIPPED "4c cable pulled" "-"; fi

# 4d: R4's own question — the helper started over Wireless debugging, then Wireless debugging turned off.
WADDR=""
if ask "On the phone: Settings > Developer options > Wireless debugging — turn it ON (same Wi-Fi as this PC), tap 'Pair device with pairing code', and keep that screen open."; then
  printf '>>> Type the pairing IP:port shown under the code (e.g. 192.168.1.20:37123): '; read -r PADDR
  printf '>>> Type the 6-digit pairing code: '; read -r PCODE
  adb pair "$PADDR" "$PCODE" > "$OUT/part4-wireless.txt" 2>&1
  printf '>>> Now type the IP:port shown on the Wireless debugging page itself (not the pairing one): '; read -r WADDR
  adb connect "$WADDR" >> "$OUT/part4-wireless.txt" 2>&1
  sleep 2
  adb -s "$WADDR" shell "CLASSPATH=$APKP setsid nohup app_process /system/bin app.tessera.r4probe.HelperMain --daemon wifi > /dev/null 2>&1 < /dev/null &" >> "$OUT/part4-wireless.txt" 2>&1
  sleep 5
  HWIFI="$(helper_pid wifi)"
  echo "helper started over Wireless debugging: pid ${HWIFI:-none}" >> "$OUT/part4-wireless.txt"
  if ping_ok wifi; then echo "the app holds the Wireless-debugging helper's binder" >> "$OUT/part4-wireless.txt"; fi
  if ask "Now turn Wireless debugging OFF on the phone (leave the cable in)."; then
    sleep 10
    { echo "after Wireless debugging went off: helper ${HWIFI:-none} alive: $(alive "$HWIFI" && echo yes || echo NO)"
      echo "--- helper log"; adb shell cat /data/local/tmp/r4helper-wifi.log | tr -d '\r'; } >> "$OUT/part4-wireless.txt"
    if alive "$HWIFI" && ping_ok wifi; then result PASS "4d Wireless debugging off: helper lives and answers" part4-wireless.txt
    else result FAIL "4d Wireless debugging off: helper lives and answers" part4-wireless.txt; fi
  else result SKIPPED "4d Wireless debugging off" part4-wireless.txt; fi
  adb disconnect "$WADDR" > /dev/null 2>&1
else result SKIPPED "4d Wireless debugging off" "-"; fi

# ---------------------------------------------------------------- blur (cross-window blur probe)
blur_pass() { # label
  {
    echo "== $1"
    adb shell getprop | grep -i blur | tr -d '\r'
    adb shell dumpsys window | grep -i -m3 blur | tr -d '\r'
  } >> "$OUT/blur.txt"
  run_app blur; sleep 3
  adb exec-out screencap -p > "$OUT/blur-$1.png"
  wait_report "BLUR DONE $NONCE" 20; section 'BLUR' >> "$OUT/blur.txt"
}
blur_pass normal
adb shell dumpsys battery unplug; adb shell cmd power set-mode 1; sleep 2
blur_pass powersave
adb shell cmd power set-mode 0; adb shell dumpsys battery reset
result INFO "blur: platform answer + FLAG_BLUR_BEHIND overlay, normal and power saving" "blur.txt blur-*.png"

# ---------------------------------------------------------------- P5: the nav-bar overlay
overlay_pass() { # label
  local wh w h
  wh="$(adb shell wm size | tr -d '\r' | awk -F': ' '/Override/ {o=$2} /Physical/ {p=$2} END {print (o ? o : p)}')"
  w="${wh%x*}"; h="${wh#*x}"
  run_app overlay; sleep 4
  adb exec-out screencap -p > "$OUT/overlay-$1.png"
  adb shell input tap $(( w / 2 )) $(( h - 12 )); sleep 1
  adb shell input tap $(( w / 2 )) $(( h / 2 ))
  wait_report "OVERLAY DONE $NONCE" 30
  { echo "== $1 (navigation_mode=$(adb shell settings get secure navigation_mode | tr -d '\r')); taps sent at y=$(( h - 12 )) and y=$(( h / 2 )) of $h"
    section 'P5'; } >> "$OUT/p5-overlay.txt"
}
overlay_pass "nav-$(adb shell settings get secure navigation_mode | tr -d '\r')"
if ask "Switch the navigation type (Settings > Display > Navigation bar: Swipe gestures <-> Buttons), then come back here."; then
  overlay_pass "nav-$(adb shell settings get secure navigation_mode | tr -d '\r')"
  ask "Switch the navigation type back to what you use." || true
fi
result INFO "P5 nav-bar overlay (screenshots + the taps the overlay received)" "p5-overlay.txt overlay-*.png"

# ---------------------------------------------------------------- PQ2: visual voicemail (what the shell can see)
{
  echo "== carrier (operator names only)"
  echo "sim operator: $(adb shell getprop gsm.sim.operator.alpha | tr -d '\r') · network: $(adb shell getprop gsm.operator.alpha | tr -d '\r')"
  echo; echo "== carrier config: voicemail / VVM / GBA keys"
  adb shell dumpsys carrier_config 2>&1 | tr -d '\r' | grep -i -E 'vvm|visual_voicemail|voicemail|gba' | sed 's/^ *//' | sort -u | head -60
  echo; echo "== apps that could handle visual voicemail"
  adb shell pm list packages 2>&1 | tr -d '\r' | grep -i -E 'vvm|voicemail|visualvoice|tmobile|t-mobile|digits'
  echo; echo "== the default dialer (role)"
  adb shell cmd role get-role-holders android.app.role.DIALER 2>&1 | tr -d '\r'
  echo; echo "== the system voicemail provider: rows per source app (counts only, no content)"
  VQ="$(adb shell content query --uri content://com.android.voicemail/voicemail --projection source_package 2>&1 | tr -d '\r')"
  echo "$VQ" | sed -n 's/.*source_package=\([^,]*\).*/\1/p' | sort | uniq -c
  echo "$VQ" | grep -q '^Row:' || echo "  no rows: $(echo "$VQ" | head -1)"
  echo; echo "== what the shell uid holds (the helper would run as it)"
  adb shell dumpsys package com.android.shell 2>&1 | tr -d '\r' | grep -E 'MODIFY_PHONE_STATE|READ_VOICEMAIL|ADD_VOICEMAIL|READ_PRIVILEGED_PHONE_STATE' | sed 's/^ *//' | sort -u
} > "$OUT/pq2-voicemail.txt" 2>&1
result INFO "PQ2 visual voicemail: carrier config, handler app, provider counts (no login)" pq2-voicemail.txt

# ---------------------------------------------------------------- clean up and check nothing was left changed
for tag in usb wifi; do
  p="$(helper_pid $tag)"
  if [ -n "$p" ] && alive "$p"; then adb shell kill "$p"; echo "stopped helper $tag pid $p" >> "$OUT/cleanup.txt"; fi
done
adb shell dumpsys battery reset
while IFS=$'\t' read -r name rd on off pat <&3; do
  now="$(read_toggle "$name")"; was="$(grep "^$name=" "$OUT/baseline.txt" | cut -d= -f2-)"
  if [ "$now" = "$was" ]; then echo "$name: as it was ($now)"; else
    if echo "$was" | grep -Eq "$pat"; then adb shell "$on" > /dev/null 2>&1; else adb shell "$off" > /dev/null 2>&1; fi
    sleep 3; echo "$name: WAS '$was', found '$now', set back -> '$(read_toggle "$name")'"
  fi
done 3< "$OUT/toggles.txt" >> "$OUT/cleanup.txt"
if grep -q 'WAS ' "$OUT/cleanup.txt"; then result INFO "clean-up: some toggles had to be set back (see the file)" cleanup.txt
else result PASS "clean-up: every toggle as it was at the start" cleanup.txt; fi
if [ "${R4_KEEP:-0}" != 1 ]; then adb uninstall $PKG > /dev/null 2>&1 && echo "probe uninstalled" >> "$OUT/cleanup.txt"; fi
say "Run folder: $OUT"
