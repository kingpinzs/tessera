#!/usr/bin/env bash
# E3 stop probe: the shell's Music playing Bloom, then `cmd media_session dispatch stop`: which session does the key
# reach, what does the shell's session become, and what does the Now playing pod show?
export ANDROID_SERIAL=emulator-5554
QROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
. "$QROOT/phase-01/scripts/ui.sh"; . "$QROOT/phase-03/scripts/lib.sh"; . "$QROOT/phase-01/scripts/music_lib.sh"
OUT="$(cd "$(dirname "$0")" && pwd)"; ROW_DIR="$OUT"
shell_session() { adb shell dumpsys media_session | awk '/package=app\.tileshell/ {f=1} f && /state=PlaybackState|metadata:/ {print; n++} n==2 {exit}' | sed 's/^ *//' | cut -c1-160; }
music_open
goto_pivot songs "$OUT/songs.xml" >/dev/null
XY="$(python3 -c '
import re,sys
s=open(sys.argv[1]).read()
for n in re.finditer(r"<node[^>]*>", s):
    n=n.group(0)
    if "text=\"Bloom\"" in n:
        x1,y1,x2,y2=map(int,re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"",n).groups()); print((x1+x2)//2,(y1+y2)//2); break' "$OUT/songs.xml")"
adb shell input tap $XY; sleep 4
echo "playing: $(shell_session | tr '\n' ' ')"
echo "media button session: $(adb shell dumpsys media_session | grep -iE 'media button session|mediaButtonSession' | head -2 | tr -s ' ')"
adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell input swipe 200 1200 950 1200 250; sleep 2
MARK="$(ring_mark)"
adb shell cmd media_session dispatch stop; sleep 3
echo "after dispatch stop: $(shell_session | tr '\n' ' ')"
echo "ring: $(ring_since "$MARK" | grep -E '\[music\]|\[podbay\]' | sed 's/.*\] //' | tr '\n' ';' | cut -c1-500)"
dump_ui "$OUT/pod.xml"; echo "pod: $(grep -oE 'resource-id="pod_(row|empty):nowplaying[^"]*"[^>]*text="[^"]*"' "$OUT/pod.xml" | grep -oE 'text="[^"]*"' | tr '\n' ' ')"
screencap "$OUT/pod.png"
adb shell am force-stop app.tileshell; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 3
