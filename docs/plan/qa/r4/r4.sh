#!/usr/bin/env bash
# R4 — the on-phone helper spike (PLAN.md R4; INDEX R4 row), run from the PC with the S25 Ultra on USB.
# Read README.md next to this file first. One command gathers every R4 answer into one run folder:
#   runs/<date>-<model>/SUMMARY.txt   one line per proof (PASS / FAIL / SKIPPED / INFO) and the file that shows it
# Parts: 1 app_process as the shell uid · 2 daemonise + binder handoff · 3 each toggle as the shell uid (direct, then
# through the helper) · 4 survival (app killed, Doze, cable pulled, adbd gone: Wireless and USB debugging off) · blur
# (normal and power saving) · P5 nav-bar overlay (accessibility overlay + app overlay, both navigation modes) · PQ2
# voicemail (what the shell can see; no carrier login).
# SAFETY (review/2026-09-28-r4-kit-review.md): a toggle is flipped only when its read is a recognised state; every
# change is undone by cleanup(), which runs on ANY exit — the end, Ctrl-C, a hang-up or an error — and a toggle it
# cannot set back is a FAIL line.
# Environment: R4_YES=1 skips the "flip these toggles?" question; R4_AUTO=1 skips the hands-on steps (recorded as
# SKIPPED); R4_ALLOW_ANY=1 allows a device that is not an S25 Ultra (the emulator dry run); R4_KEEP=1 keeps the probe
# installed; R4_NO_BUILD=1 installs the APK already built instead of rebuilding it.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
PKG=app.tessera.r4probe
A11Y="$PKG/$PKG.ProbeA11yService"
APK="$REPO/r4probe/build/outputs/apk/debug/r4probe-debug.apk"
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
# adb shell forwards stdin to the device: inside a `while read` loop it swallows the rest of the list, and at a prompt
# it can eat what was typed. No adb shell here reads stdin; the loops also read their list on fd 3. Every adb call is
# bounded (60 s), so a phone that drops off cannot hang the run or its clean-up.
adb() { case " $* " in *" shell "*) timeout 60 adb "$@" < /dev/null ;; *) timeout 60 adb "$@" ;; esac; }

# ---------------------------------------------------------------- the device
# The phone is the one physical USB device: emulators (emulator-*) and wireless connections (ip:port) are ignored, since
# this PC also runs the QA emulators. ANDROID_SERIAL, when set to an attached device, wins (the emulator dry run).
if [ -n "${ANDROID_SERIAL:-}" ] && command adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -qx "$ANDROID_SERIAL"; then :
else
  mapfile -t DEVS < <(command adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ && $1 !~ /:/ {print $1}')
  if [ "${#DEVS[@]}" -ne 1 ]; then
    echo "R4 needs exactly ONE phone on USB (found ${#DEVS[@]}). Check the cable, USB debugging, and the phone's 'Allow USB debugging?' prompt."
    command adb devices; exit 2
  fi
  export ANDROID_SERIAL="${DEVS[0]}"
fi
MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  SM-S938*) ;;
  *) if [ "${R4_ALLOW_ANY:-0}" != 1 ]; then echo "This is '$MODEL', not an S25 Ultra (SM-S938*). R4 is for the phone; set R4_ALLOW_ANY=1 for a dry run."; exit 2; fi ;;
esac
exec 5>&1   # the terminal: say / result write here even from inside a block redirected to a file (R2-1)
OUT="$HERE/runs/$(date +%Y%m%d-%H%M%S)-$(echo "$MODEL" | tr -c 'A-Za-z0-9_-' '_')"
mkdir -p "$OUT"
SUM="$OUT/SUMMARY.txt"
say() { echo "$*" | tee -a "$SUM" >&5; }
result() { printf '%-9s %-50s %s\n' "$1" "$2" "$3" | tee -a "$SUM" >&5; }   # PASS|FAIL|SKIPPED|INFO  what  file
ask() { # message -> 0 when done, 1 when skipped (R4_AUTO=1 skips every one)
  if [ "${R4_AUTO:-0}" = 1 ]; then echo "  (R4_AUTO: skipped — $1)"; return 1; fi
  printf '\n>>> %s\n>>> Press Enter when done, or type s then Enter to skip: ' "$1"
  local a; read -r a; [ "$a" != s ]
}
report() { adb shell run-as $PKG cat files/report.txt 2>/dev/null | tr -d '\r'; }
wait_report() { # marker timeout_s -> 0 when the app's report shows the marker
  local end=$(( $(date +%s) + $2 ))
  while [ "$(date +%s)" -lt "$end" ]; do report | grep -qF "$1" && return 0; sleep 2; done
  return 1
}
section() { report | sed -n "/^== $1/,/^== /p" | sed '$d'; }   # one section of the app's report, without the next header
NONCE=0
# The probe obeys a `run` extra only with its own secret (RunToken): read it with run-as (debug builds only).
run_app() { NONCE="$(date +%s)$RANDOM"; adb shell am start -n $PKG/.ProbeActivity --es run "$1" --es nonce "$NONCE" --es token "$(adb shell run-as $PKG cat files/run-token | tr -d '\r')" > /dev/null 2>&1; }
wake() { adb shell input keyevent KEYCODE_WAKEUP > /dev/null 2>&1; adb shell wm dismiss-keyguard > /dev/null 2>&1; }

say "R4 run $(date -Is) on $MODEL"
{
  for p in ro.product.model ro.build.fingerprint ro.build.version.release ro.build.version.sdk ro.build.version.oneui \
           ro.build.version.security_patch ro.surface_flinger.supports_background_blur; do
    echo "$p=$(adb shell getprop $p | tr -d '\r')"
  done
  echo "shell id: $(adb shell id | tr -d '\r')"
} > "$OUT/device.txt"
result INFO "device facts" device.txt

# ---------------------------------------------------------------- what the clean-up restores
BASE_NAV="$(adb shell settings get secure navigation_mode | tr -d '\r')"
BASE_ADBWIFI="$(adb shell settings get global adb_wifi_enabled | tr -d '\r')"
BASE_A11Y="$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')"
BASE_A11Y_ON="$(adb shell settings get secure accessibility_enabled | tr -d '\r')"
BASE_LOWPOWER="$(adb shell settings get global low_power | tr -d '\r')"
# low_power reads 0 whenever the phone charges (it does, on USB); the owner's Power saving choice is low_power_sticky,
# which `cmd power set-mode` also writes. That is what the clean-up restores and checks (R2-3).
BASE_STICKY="$(adb shell settings get global low_power_sticky | tr -d '\r')"
A11Y_TOUCHED=0
HELPER_PIDS=()
TOGGLES_READY=0
{ echo "navigation_mode=$BASE_NAV"; echo "adb_wifi_enabled=$BASE_ADBWIFI"; echo "enabled_accessibility_services=$BASE_A11Y"
  echo "accessibility_enabled=$BASE_A11Y_ON"; echo "low_power=$BASE_LOWPOWER"; echo "low_power_sticky=$BASE_STICKY"; } > "$OUT/baseline-settings.txt"
restore_sticky() { # puts the owner's Power saving back exactly: on stays on, off stays off (review r3 R3-1)
  # `cmd power set-mode 0` is a manual OFF that Android keeps until a reboot, whatever low_power_sticky then says, so
  # the mode is set to the owner's own value — read while the battery is reported unplugged, where it means something.
  local want=0 lp st
  { [ "$BASE_STICKY" = 1 ] || [ "$BASE_LOWPOWER" = 1 ]; } && want=1
  adb shell dumpsys battery unplug > /dev/null 2>&1
  adb shell cmd power set-mode "$want" > /dev/null 2>&1
  sleep 1
  lp="$(adb shell settings get global low_power | tr -d '\r')"
  if [ "$BASE_STICKY" = null ]; then adb shell settings delete global low_power_sticky > /dev/null 2>&1
  else adb shell settings put global low_power_sticky "$BASE_STICKY"; fi
  st="$(adb shell settings get global low_power_sticky | tr -d '\r')"
  adb shell dumpsys battery reset > /dev/null 2>&1
  if [ "$lp" = "$want" ] && [ "$st" = "$BASE_STICKY" ]; then echo "power saving: as it was (on when unplugged: $want; low_power_sticky $st)"
  else echo "FAIL: power saving is not as it was (unplugged it reads $lp, it should be $want; low_power_sticky $st, it was $BASE_STICKY) — set Power saving by hand"; fi
}

tfield() { awk -F'\t' -v n="$1" -v k="$2" '$1==n {print $k}' "$OUT/toggles.txt"; }   # 2 read 3 on 4 off 5 onpat 6 offpat 7 probe 8 item
read_toggle() { adb shell "$(tfield "$1" 2)" 2>&1 | tr -d '\r' | tr '\n' ' ' | sed 's/ *$//'; }
state_of() { # name read -> on | off | unknown  (the same patterns the helper applies; grep -Ei, no inline flags)
  if printf '%s' "$2" | grep -Eiq -- "$(tfield "$1" 5)"; then echo on
  elif printf '%s' "$2" | grep -Eiq -- "$(tfield "$1" 6)"; then echo off
  else echo unknown; fi
}
alive() { [ -n "${1:-}" ] && [ "$(adb shell "ps -o PID -p $1 | tail -n +2" | tr -d '\r ')" = "$1" ]; }
helper_pid() { adb shell cat /data/local/tmp/r4helper-$1.pid 2>/dev/null | tr -d '\r'; }
kill_helper() { # pid label -> kills that recorded pid and checks it is gone
  [ -n "${1:-}" ] || return 0
  if alive "$1"; then adb shell kill "$1"; sleep 1; alive "$1" && adb shell kill -9 "$1"; fi
  if alive "$1"; then echo "FAIL: helper $2 pid $1 is STILL running"; else echo "helper $2 pid $1 stopped"; fi
}

cleanup() {
  # A second Ctrl-C must not kill the clean-up half-way (R2-1): every further signal is ignored from here on.
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  local c="$OUT/cleanup.txt"
  echo "Cleaning up — this takes about a minute; further Ctrl-C is ignored until it is done." >&5
  echo "--- clean-up $(date -Is)" >> "$c"
  if [ "$(timeout 20 adb get-state 2>/dev/null)" != device ]; then
    echo "FAIL: the phone is not reachable, so nothing could be checked or undone. Reboot the phone: that ends forced Doze," >> "$c"
    echo "  the unplugged-battery report and the helper; then check Wi-Fi, Bluetooth, NFC, location, mobile data, airplane" >> "$c"
    echo "  mode, automatic date & time, Power saving, the navigation type and Settings > Accessibility by hand, and" >> "$c"
    echo "  uninstall 'R4 probe'." >> "$c"
    result FAIL "clean-up: the phone was unreachable (see the file)" cleanup.txt
    say "Run folder: $OUT"; return
  fi
  # the two that outlast the run until a reboot go first
  adb shell dumpsys deviceidle unforce > /dev/null 2>&1
  adb shell dumpsys battery reset > /dev/null 2>&1
  for p in "${HELPER_PIDS[@]}"; do kill_helper "$p" recorded >> "$c"; done
  for tag in usb wifi; do p="$(helper_pid $tag)"; [ -n "$p" ] && kill_helper "$p" "$tag" >> "$c"; done
  adb shell rm -f /data/local/tmp/r4helper-usb.pid /data/local/tmp/r4helper-usb.log /data/local/tmp/r4helper-wifi.pid /data/local/tmp/r4helper-wifi.log
  if [ "$A11Y_TOUCHED" = 1 ]; then
    if [ "$BASE_A11Y" = null ]; then adb shell settings delete secure enabled_accessibility_services > /dev/null
    else adb shell settings put secure enabled_accessibility_services "'$BASE_A11Y'"; fi
    if [ "$BASE_A11Y_ON" = null ]; then adb shell settings delete secure accessibility_enabled > /dev/null
    else adb shell settings put secure accessibility_enabled "$BASE_A11Y_ON"; fi
    if [ "$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')" = "$BASE_A11Y" ]; then echo "accessibility services: as they were" >> "$c"
    else echo "FAIL: accessibility services are NOT as they were — check Settings > Accessibility > Installed apps" >> "$c"; fi
  fi
  if [ "$TOGGLES_READY" = 1 ]; then
    while IFS=$'\t' read -r name rd on off onp offp probe item <&3; do
      [ "$probe" != "-" ] && continue
      was="$(grep "^$name=" "$OUT/baseline.txt" | cut -d= -f2-)"; want="$(state_of "$name" "$was")"
      [ "$want" = unknown ] && { echo "$name: its start state was not recognised ('$was'); never flipped, left alone"; continue; }
      now="$(state_of "$name" "$(read_toggle "$name")")"
      if [ "$now" = "$want" ]; then echo "$name: as it was ($want)"; continue; fi
      if [ "$want" = on ]; then adb shell "$on" > /dev/null 2>&1; else adb shell "$off" > /dev/null 2>&1; fi
      sleep 3; now="$(state_of "$name" "$(read_toggle "$name")")"
      if [ "$now" = "$want" ]; then echo "$name: had been left $([ "$want" = on ] && echo off || echo on); set back to $want"
      else echo "FAIL: $name could NOT be set back to $want (now $now) — set it by hand"; fi
    done 3< "$OUT/toggles.txt" >> "$c"
  fi
  restore_sticky >> "$c"
  now_nav="$(adb shell settings get secure navigation_mode | tr -d '\r')"
  [ "$now_nav" != "$BASE_NAV" ] && echo "NOTE: navigation mode is $now_nav, it was $BASE_NAV — switch it back in Settings > Display > Navigation bar" >> "$c"
  now_aw="$(adb shell settings get global adb_wifi_enabled | tr -d '\r')"
  [ "$now_aw" != "$BASE_ADBWIFI" ] && echo "NOTE: Wireless debugging is $now_aw, it was $BASE_ADBWIFI" >> "$c"
  if [ "${R4_KEEP:-0}" != 1 ]; then adb uninstall $PKG > /dev/null 2>&1 && echo "probe uninstalled" >> "$c"; fi
  if grep -q '^FAIL' "$c"; then result FAIL "clean-up: something could not be set back (see the file)" cleanup.txt
  else result PASS "clean-up: every change undone" cleanup.txt; fi
  say "Remember: turn Auto Blocker back on (Settings > Security and privacy) if you use it."
  say "Run folder: $OUT"
}
trap cleanup EXIT
trap 'say "interrupted"; exit 130' INT TERM HUP

# A run stopped before this kit had its clean-up may have left these behind; undo them before taking the baseline.
adb shell dumpsys deviceidle unforce > /dev/null 2>&1
adb shell dumpsys battery reset > /dev/null 2>&1
for tag in usb wifi; do p="$(helper_pid $tag)"; [ -n "$p" ] && kill_helper "$p" "$tag (left by an earlier run)" >> "$OUT/cleanup.txt"; done
while read -r p; do [ -n "$p" ] && kill_helper "$p" "orphan (left by an earlier run)" >> "$OUT/cleanup.txt"; done \
  < <(adb shell "ps -A -o PID,ARGS" | tr -d '\r' | awk '/app\.tessera\.r4probe\.HelperMain/ && !/awk/ {print $1}')

# ---------------------------------------------------------------- the probe APK
if [ "${R4_NO_BUILD:-0}" != 1 ]; then
  ( cd "$REPO" && ./gradlew -q :r4probe:assembleDebug ) > "$OUT/build.txt" 2>&1 || { say "the probe APK did not build (build.txt)"; exit 3; }
fi
echo "apk sha256 $(sha256sum "$APK" | cut -c1-16)" >> "$OUT/device.txt"
adb install -r "$APK" > "$OUT/install.txt" 2>&1 || { say "the probe APK did not install (install.txt)"; exit 3; }
APKP="$(adb shell pm path $PKG | tr -d '\r' | sed 's/^package://')"
HELPER="CLASSPATH=$APKP app_process /system/bin app.tessera.r4probe.HelperMain"
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
# The script reads the app's report with run-as; a phone that refuses it would waste the run, so check it first.
adb shell am start -W -n $PKG/.ProbeActivity > /dev/null 2>&1; sleep 3
if ! adb shell run-as $PKG id 2>&1 | grep -q 'uid='; then say "run-as does not work on this phone, so the app's report cannot be read: stopping before any change"; exit 3; fi

# The toggle list comes from the helper itself (Toggles.kt), so the direct pass and the helper pass use the same list.
adb shell "$HELPER --list-toggles" | tr -d '\r' > "$OUT/toggles.txt"
[ "$(wc -l < "$OUT/toggles.txt")" -ge 5 ] || { say "the helper printed no toggle list (toggles.txt): stopping before any change"; exit 3; }
while IFS=$'\t' read -r name rest <&3; do echo "$name=$(read_toggle "$name")"; done 3< "$OUT/toggles.txt" > "$OUT/baseline.txt"
TOGGLES_READY=1
{ while IFS=$'\t' read -r name rest <&3; do
    was="$(grep "^$name=" "$OUT/baseline.txt" | cut -d= -f2-)"
    printf '%-14s %-8s %s\n' "$name" "$(state_of "$name" "$was")" "$was"
  done 3< "$OUT/toggles.txt"; } > "$OUT/baseline-states.txt"
result INFO "toggle baseline (clean-up restores these)" baseline-states.txt

echo
echo "R4 will flip each of these on the phone and flip it straight back (directly, then through the helper):"
awk -F'\t' '$7=="-" {printf "%s ", $1}' "$OUT/toggles.txt"; echo
echo "What it read on the phone (only on / off are flipped; 'unknown' is left alone):"
sed 's/^/    /' "$OUT/baseline-states.txt" | cut -c1-100
echo "If any on / off above is wrong for your phone, answer n and tell me which."
echo "Only a toggle whose state it recognises is flipped; the hotspot is only probed, never"
echo "started. Wi-Fi, mobile data and airplane mode drop the phone's connections for a few seconds each; USB is unaffected."
if [ "${R4_YES:-0}" != 1 ]; then printf 'Go ahead? [y/N] '; read -r go; [ "$go" = y ] || { say "stopped before any toggle was flipped"; exit 0; }; fi

# ---------------------------------------------------------------- part 1: app_process as the shell uid
adb shell "$HELPER --probe" > "$OUT/part1-app_process.txt" 2>&1
if grep -q '^uid *2000' "$OUT/part1-app_process.txt"; then result PASS "1 app_process runs as the shell uid" part1-app_process.txt
else result FAIL "1 app_process runs as the shell uid" part1-app_process.txt; fi

# ---------------------------------------------------------------- part 2: daemonise + binder handoff
ping_ok() { # tag pid -> 0 when that helper is alive and the app's ping (this run's nonce) reached it
  alive "$2" || return 1
  run_app helper_ping; sleep 1
  wait_report "HELPER_PING DONE $NONCE" 15 && section 'HELPER (R4' | grep -q "ping: helper tag=$1 uid=2000 pid=$2 "
}
adb shell "CLASSPATH=$APKP setsid nohup app_process /system/bin app.tessera.r4probe.HelperMain --daemon usb > /dev/null 2>&1 < /dev/null &"
sleep 2
HUSB="$(helper_pid usb)"; HELPER_PIDS+=("$HUSB")
adb shell am start -W -n $PKG/.ProbeActivity > /dev/null 2>&1
sleep 5
{
  echo "helper pid $HUSB"; adb shell "ps -o PID,PPID,USER,NAME -p $HUSB" | tr -d '\r'
  echo; echo "--- helper log"; adb shell cat /data/local/tmp/r4helper-usb.log | tr -d '\r'
} > "$OUT/part2-daemon.txt"
if alive "$HUSB" && [ "$(adb shell "ps -o PPID -p $HUSB | tail -1" | tr -d '\r ')" = 1 ]; then
  result PASS "2a the helper daemonises (parent = init)" part2-daemon.txt
else result FAIL "2a the helper daemonises (parent = init)" part2-daemon.txt; fi
if ping_ok usb "$HUSB"; then section 'HELPER (R4' >> "$OUT/part2-daemon.txt"; result PASS "2b binder handed to the app; the app's ping answered" part2-daemon.txt
else section 'HELPER (R4' >> "$OUT/part2-daemon.txt"; result FAIL "2b binder handed to the app; the app's ping answered" part2-daemon.txt; fi

# ---------------------------------------------------------------- part 3: each toggle as the shell uid
# Android will not hold battery saver on while charging (and USB charges the phone), so the battery is reported
# unplugged for both passes and reset after (and by cleanup()).
adb shell dumpsys battery unplug
{
  while IFS=$'\t' read -r name rd on off onp offp probe item <&3; do
    echo "  $name  [$item]"
    if [ "$probe" != "-" ]; then
      o="$(adb shell "$probe" 2>&1 | tr -d '\r' | tr '\n' ' ')"
      echo "    probe   ${o:0:220}"
      if echo "$o" | grep -q SecurityException; then echo "    PROBE REFUSED to the shell uid"; else echo "    PROBE reached (no state changed)"; fi
      continue
    fi
    before="$(read_toggle "$name")"; st="$(state_of "$name" "$before")"
    echo "    before  $before  [$st]"
    if [ "$st" = unknown ]; then echo "    UNRECOGNISED STATE: not flipped"; continue; fi
    if [ "$st" = on ]; then away="$off"; back="$on"; else away="$on"; back="$off"; fi
    o1="$(adb shell "$away" 2>&1 | tr -d '\r' | tr '\n' ' ')"
    sleep 3; mid="$(read_toggle "$name")"; smid="$(state_of "$name" "$mid")"
    o2="$(adb shell "$back" 2>&1 | tr -d '\r' | tr '\n' ' ')"
    sleep 3; after="$(read_toggle "$name")"; safter="$(state_of "$name" "$after")"
    echo "    flip away: ${o1:-(no output)}"
    echo "    read    $mid  [$smid]"
    echo "    back:   ${o2:-(no output)}"
    echo "    after   $after  [$safter]"
    echo "    CHANGED $([ "$smid" != "$st" ] && echo yes || echo NO) · RESTORED $([ "$safter" = "$st" ] && echo yes || echo NO)"
  done 3< "$OUT/toggles.txt"
} > "$OUT/part3-direct.txt" 2>&1
NFLIP="$(awk -F'\t' '$7=="-"' "$OUT/toggles.txt" | wc -l)"
result INFO "3a toggles, directly as the shell uid ($(grep -c 'CHANGED yes' "$OUT/part3-direct.txt")/$NFLIP changed)" part3-direct.txt
grep -q 'RESTORED NO' "$OUT/part3-direct.txt" && result FAIL "3a a toggle was not restored at once (clean-up retries it)" part3-direct.txt
run_app helper_toggles
if wait_report "HELPER_TOGGLES DONE $NONCE" 420; then
  section 'HELPER TOGGLES' > "$OUT/part3-helper.txt"
  result INFO "3b toggles, through the helper ($(grep -c 'CHANGED yes' "$OUT/part3-helper.txt")/$NFLIP changed)" part3-helper.txt
  grep -q 'RESTORED NO' "$OUT/part3-helper.txt" && result FAIL "3b a toggle was not restored at once (clean-up retries it)" part3-helper.txt
else echo "TIMED OUT: no 'HELPER_TOGGLES DONE $NONCE' in 420 s" > "$OUT/part3-helper.txt"; result FAIL "3b toggles through the helper (did not finish)" part3-helper.txt; fi
adb shell dumpsys battery reset

# ---------------------------------------------------------------- part 4: survival
{
  adb shell am force-stop $PKG; sleep 6
  echo "after force-stop: helper $HUSB alive: $(alive "$HUSB" && echo yes || echo NO)"
  adb shell am start -W -n $PKG/.ProbeActivity > /dev/null 2>&1; sleep 5
  echo "--- helper log"; adb shell tail -4 /data/local/tmp/r4helper-usb.log | tr -d '\r'
} > "$OUT/part4-appkill.txt"
if ping_ok usb "$HUSB"; then result PASS "4a app killed: helper lives, re-hands the binder" part4-appkill.txt
else result FAIL "4a app killed: helper lives, re-hands the binder" part4-appkill.txt; fi

adb shell dumpsys battery unplug
DOZE="$(adb shell dumpsys deviceidle force-idle 2>&1 | tr -d '\r')"
{ echo "$DOZE"; sleep 20
  echo "deep state now: $(adb shell dumpsys deviceidle get deep | tr -d '\r')"
  echo "in forced Doze: helper $HUSB alive: $(alive "$HUSB" && echo yes || echo NO)"; } > "$OUT/part4-doze.txt"
if echo "$DOZE" | grep -q 'Now forced in to deep idle mode'; then
  if ping_ok usb "$HUSB"; then result PASS "4b forced deep Doze: helper lives and answers" part4-doze.txt
  else result FAIL "4b forced deep Doze: helper lives and answers" part4-doze.txt; fi
else result INFO "4b deep Doze could NOT be forced here (not tested; see the file)" part4-doze.txt; fi
adb shell dumpsys deviceidle unforce > /dev/null 2>&1; adb shell dumpsys battery reset

if ask "Unplug the USB cable from the phone, wait 30 seconds, then plug it back in (accept the USB prompt if the phone shows one)."; then
  echo "Waiting up to 5 minutes for the phone to come back on USB…" >&5; timeout 300 adb wait-for-device; sleep 3
  { echo "after the cable was pulled: helper $HUSB alive: $(alive "$HUSB" && echo yes || echo NO)"; } > "$OUT/part4-cable.txt"
  if ping_ok usb "$HUSB"; then result PASS "4c cable pulled: helper lives and answers" part4-cable.txt
  else result FAIL "4c cable pulled: helper lives and answers" part4-cable.txt; fi
else result SKIPPED "4c cable pulled" "-"; fi

# 4d, R4's own question: a helper started over Wireless debugging, then adbd gone. adbd stops only when BOTH Wireless and
# USB debugging are off (AOSP AdbService), so the step turns both off with the cable out — the phone as it will be used.
if ask "On the phone: Settings > Developer options > Wireless debugging — turn it ON (same Wi-Fi as this PC), tap 'Pair device with pairing code', and keep that screen open."; then
  printf '>>> Type the pairing IP:port shown under the code (e.g. 192.168.1.20:37123): '; read -r PADDR
  printf '>>> Type the 6-digit pairing code: '; read -r PCODE
  if timeout 60 adb pair "$PADDR" "$PCODE" 2>&1 | grep -q 'Successfully paired'; then echo "paired: yes" > "$OUT/part4-adbd-gone.txt"
  else echo "paired: NO" > "$OUT/part4-adbd-gone.txt"; fi
  printf '>>> Now type the IP:port shown on the Wireless debugging page itself (not the pairing one): '; read -r WADDR
  timeout 60 adb connect "$WADDR" > /dev/null 2>&1; sleep 2
  timeout 60 adb -s "$WADDR" shell "CLASSPATH=$APKP setsid nohup app_process /system/bin app.tessera.r4probe.HelperMain --daemon wifi > /dev/null 2>&1 < /dev/null &" < /dev/null > /dev/null 2>&1
  sleep 5
  HWIFI="$(helper_pid wifi)"; HELPER_PIDS+=("$HWIFI")
  echo "helper started over Wireless debugging: pid ${HWIFI:-none}" >> "$OUT/part4-adbd-gone.txt"
  ping_ok wifi "$HWIFI" && echo "the app holds the Wireless-debugging helper's binder" >> "$OUT/part4-adbd-gone.txt"
  timeout 20 adb disconnect "$WADDR" > /dev/null 2>&1
  ADBD1="$(adb shell pidof adbd | tr -d '\r')"; echo "adbd pid before: ${ADBD1:-?}" >> "$OUT/part4-adbd-gone.txt"
  if ask "Now: turn Wireless debugging OFF, turn USB debugging OFF, unplug the cable, and wait 60 seconds. Then turn USB debugging back ON, plug the cable in, accept the prompt."; then
    echo "Waiting up to 5 minutes for the phone to come back on USB…" >&5; timeout 300 adb wait-for-device; sleep 3
    { echo "after adbd was gone (Wireless and USB debugging off, cable out): helper ${HWIFI:-none} alive: $(alive "$HWIFI" && echo yes || echo NO)"
      echo "--- helper log"; adb shell cat /data/local/tmp/r4helper-wifi.log | tr -d '\r'; } >> "$OUT/part4-adbd-gone.txt"
    ADBD2="$(adb shell pidof adbd | tr -d '\r')"; echo "adbd pid after: ${ADBD2:-?}" >> "$OUT/part4-adbd-gone.txt"
    if [ "$(timeout 20 adb get-state 2>/dev/null)" != device ]; then
      result INFO "4d NOT tested: the phone did not come back on USB in 5 minutes" part4-adbd-gone.txt
    elif [ -z "$ADBD1" ] || [ "$ADBD1" = "$ADBD2" ]; then
      result INFO "4d NOT tested: adbd never stopped (same pid) — was USB debugging off?" part4-adbd-gone.txt
    elif ping_ok wifi "$HWIFI"; then result PASS "4d adbd gone (new adbd pid): helper lives and answers" part4-adbd-gone.txt
    else result FAIL "4d adbd gone (new adbd pid): helper lives and answers" part4-adbd-gone.txt; fi
  else result SKIPPED "4d adbd gone" part4-adbd-gone.txt; fi
else result SKIPPED "4d adbd gone" "-"; fi

# ---------------------------------------------------------------- blur (cross-window blur probe)
blur_pass() { # label
  { echo "== $1"; adb shell getprop | grep -i blur | tr -d '\r'; adb shell dumpsys window | grep -i -m3 blur | tr -d '\r'; } >> "$OUT/blur.txt"
  wake; run_app blur; sleep 3
  adb exec-out screencap -p > "$OUT/blur-$1.png"
  if wait_report "BLUR DONE $NONCE" 20; then section 'BLUR' >> "$OUT/blur.txt"
  else echo "TIMED OUT: no 'BLUR DONE $NONCE'" >> "$OUT/blur.txt"; fi
}
blur_pass normal
adb shell dumpsys battery unplug; adb shell cmd power set-mode 1; sleep 2
blur_pass powersave
restore_sticky >> "$OUT/blur.txt"; adb shell dumpsys battery reset
result INFO "blur: platform answer + FLAG_BLUR_BEHIND overlay, normal and power saving" "blur.txt blur-*.png"

# ---------------------------------------------------------------- P5: the nav-bar overlay
# The accessibility overlay is what phase 04's action center is; the app overlay is the contrast. The probe's
# accessibility service is appended to the phone's own list for this step and the exact list restored after.
A11Y_TOUCHED=1
if [ "$BASE_A11Y" = null ] || [ -z "$BASE_A11Y" ]; then adb shell settings put secure enabled_accessibility_services "$A11Y"
else adb shell settings put secure enabled_accessibility_services "'$BASE_A11Y:$A11Y'"; fi
adb shell settings put secure accessibility_enabled 1
sleep 4
overlay_pass() { # label kind
  local wh w h
  wh="$(adb shell wm size | tr -d '\r' | awk -F': ' '/Override/ {o=$2} /Physical/ {p=$2} END {print (o ? o : p)}')"
  w="${wh%x*}"; h="${wh#*x}"
  wake; run_app "$( [ "$2" = a11y ] && echo overlay_a11y || echo overlay )"
  if wait_report "WINDOW UP $2 $NONCE" 20; then
    adb exec-out screencap -p > "$OUT/overlay-$1-$2.png"
    adb shell input tap $(( w / 2 )) $(( h - 12 )); sleep 1
    adb shell input tap $(( w / 2 )) $(( h / 2 ))
  fi
  echo "== $1 $2 (navigation_mode=$(adb shell settings get secure navigation_mode | tr -d '\r')); taps sent at y=$(( h - 12 )) and y=$(( h / 2 )) of $h" >> "$OUT/p5-overlay.txt"
  if wait_report "OVERLAY DONE $2 $NONCE" 30; then section 'P5' | sed -n "/^-- $2/,/OVERLAY DONE $2/p" >> "$OUT/p5-overlay.txt"
  else echo "TIMED OUT: no 'OVERLAY DONE $2 $NONCE'" >> "$OUT/p5-overlay.txt"; fi
}
section 'P5' | grep -m1 'accessibility service' >> "$OUT/p5-overlay.txt"
NAV1="nav-$(adb shell settings get secure navigation_mode | tr -d '\r')"
overlay_pass "$NAV1" a11y; overlay_pass "$NAV1" app
if ask "Switch the navigation type (Settings > Display > Navigation bar: Swipe gestures <-> Buttons), then come back here."; then
  NAV2="nav-$(adb shell settings get secure navigation_mode | tr -d '\r')"
  overlay_pass "$NAV2" a11y; overlay_pass "$NAV2" app
  ask "Switch the navigation type back to what you use." || true
fi
if [ "$BASE_A11Y" = null ]; then adb shell settings delete secure enabled_accessibility_services > /dev/null
else adb shell settings put secure enabled_accessibility_services "'$BASE_A11Y'"; fi
if [ "$BASE_A11Y_ON" = null ]; then adb shell settings delete secure accessibility_enabled > /dev/null
else adb shell settings put secure accessibility_enabled "$BASE_A11Y_ON"; fi
result INFO "P5 nav-bar overlay: accessibility + app overlay (shots, taps received)" "p5-overlay.txt overlay-*.png"

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
# cleanup() runs now (the EXIT trap).
