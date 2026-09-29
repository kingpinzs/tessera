#!/usr/bin/env bash
# Emulator test helper: opens Developer options > Wireless debugging > "Pair device with pairing code" and prints
# "<ip:port> <code>" read from the dialog. For the R4 phone-only pairing spike on emulator-5554 only.
export PATH="$HOME/Android/Sdk/platform-tools:$PATH" ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}
S=/tmp/claude-1000/-home-jeremyking/9dace4c7-c342-4879-961a-84511afd4f90/scratchpad
dump() { adb shell uiautomator dump /sdcard/wd.xml > /dev/null 2>&1; adb pull -q /sdcard/wd.xml $S/wd.xml > /dev/null; }
center() { python3 - "$S/wd.xml" "$1" <<'PY'
import re,sys
s=open(sys.argv[1]).read()
m=re.search(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(sys.argv[2]), s)
if m: a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2)
PY
}
adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS > /dev/null 2>&1; sleep 3
dump; xy="$(center "Pair device with pairing code")"
if [ -z "$xy" ]; then
  for i in $(seq 1 15); do
    dump; xy="$(center "Wireless debugging")"
    [ -n "$xy" ] && break
    adb shell input swipe 540 1800 540 700 300; sleep 0.8
  done
  [ -z "$xy" ] && { echo "no Wireless debugging row" >&2; exit 1; }
  adb shell input tap $xy; sleep 2.5
  dump; xy="$(center "Pair device with pairing code")"
fi
CONNECT="$(python3 - "$S/wd.xml" <<'PY'
import re,sys
t=re.findall(r'text="([^"]*)"', open(sys.argv[1]).read())
print(next((x for x in t if re.fullmatch(r'[\d.]+:\d+', x)), ''))
PY
)"
[ -z "$xy" ] && { echo "no pair row" >&2; grep -o 'text="[^"]*"' $S/wd.xml | grep -v '""' | head >&2; exit 1; }
adb shell input tap $xy; sleep 2.5
dump
python3 - "$S/wd.xml" <<'PY'
import re,sys
s=open(sys.argv[1]).read()
texts=re.findall(r'text="([^"]*)"', s)
code=next((t for t in texts if re.fullmatch(r'\d{6}', t)), '')
addr=next((t for t in texts if re.fullmatch(r'[\d.]+:\d+', t)), '')
print(addr, code)
PY
echo "$CONNECT"
