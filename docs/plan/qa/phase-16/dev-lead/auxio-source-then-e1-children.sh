#!/usr/bin/env bash
# One hold of the device (run under run-locked.sh):
#   1. FIXTURE, once for this AVD (tileshell_fhd / emulator-5554): Auxio's music source = System, as phase 15 set it on
#      its own AVD ("Music sources -> System -> Save", qa/phase-15/STATE.md). Without it Auxio's library is empty and a
#      VIEW of a track plays nothing (dev-lead/probe-auxio/). Taps are the dialog's own bounds, read from
#      probe-auxio/sources-dialog.xml: "System" [543,835][917,970], "Save" [736,1672][917,1807].
#   2. Proof the fixture works: a VIEW of the fixture track "Zoo Station" ends with Auxio's session PLAYING.
#   3. E1's children only (E1_PART=children), reusing the children that passed in E1's first run on this build.
set -u
export ANDROID_SERIAL=emulator-5554
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
P16="$(cd "$HERE/.." && pwd)"
D="$HERE/probe-auxio"; mkdir -p "$D"
# The centre of the node whose text is $2 in dump $1 ("x y"): the dialog re-lays itself out when the source changes
# (run 1 tapped Save where it had been and dismissed the dialog unsaved).
centre() { python3 -c '
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(r"<node[^>]*text=\"%s\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"" % re.escape(sys.argv[2]), x)
print("%d %d" % ((int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2) if m else "")' "$1" "$2"; }
state() { adb shell dumpsys media_session | tr -d '\r' | grep -A12 'org.oxycblt.auxio/' | grep -o 'state=PlaybackState {state=[A-Z_]*' | head -1; }
echo "--- 1. Auxio: Music sources -> System -> Save ($(date -Is))"
adb shell am force-stop org.oxycblt.auxio
adb shell am start -W -n org.oxycblt.auxio/.MainActivity > /dev/null 2>&1; sleep 4
adb shell input tap 540 1519; sleep 3                       # "Music sources" on the empty library page
adb shell uiautomator dump /sdcard/qa-auxio.xml > /dev/null 2>&1; adb shell cat /sdcard/qa-auxio.xml > "$D/set-1-dialog.xml"
grep -q 'text="System"' "$D/set-1-dialog.xml" || { echo "the Music sources dialog did not open; stopping before any tap"; adb shell input keyevent KEYCODE_HOME; exit 5; }
adb shell input tap $(centre "$D/set-1-dialog.xml" System); sleep 2
adb shell uiautomator dump /sdcard/qa-auxio.xml > /dev/null 2>&1; adb shell cat /sdcard/qa-auxio.xml > "$D/set-2-system.xml"
echo "System checked: $(grep -o 'text="System"[^>]*checked="[a-z]*"' "$D/set-2-system.xml" | grep -o 'checked="[a-z]*"')"
save="$(centre "$D/set-2-system.xml" Save)"; echo "Save at: $save"
[ -n "$save" ] || { echo "no Save button in the dump; stopping"; adb shell input keyevent KEYCODE_HOME; exit 5; }
adb shell input tap $save; sleep 12                         # then the library loads
adb exec-out screencap -p > "$D/set-3-after-save.png"
adb shell uiautomator dump /sdcard/qa-auxio.xml > /dev/null 2>&1; adb shell cat /sdcard/qa-auxio.xml > "$D/set-3-after-save.xml"
echo "library shows Zoo Station: $(grep -c 'Zoo Station' "$D/set-3-after-save.xml"); empty-library line still there: $(grep -c 'Your songs will show up here' "$D/set-3-after-save.xml")"
adb shell am force-stop org.oxycblt.auxio
echo "--- 2. a VIEW of Zoo Station plays"
zoo="$(adb shell content query --uri content://media/external/audio/media --projection _id --where "\"title='Zoo Station'\"" | tr -d '\r' | grep -oE '_id=[0-9]+' | head -1 | cut -d= -f2)"
adb shell am start -W -a android.intent.action.VIEW -d "content://media/external/audio/media/$zoo" -t audio/mpeg -p org.oxycblt.auxio 2>&1 | grep -E 'Status|LaunchState'
sleep 6
echo "Auxio session: $(state)"
ok="$(state | grep -c PLAYING)"
adb shell am force-stop org.oxycblt.auxio; adb shell input keyevent KEYCODE_HOME; sleep 2
[ "$ok" = 1 ] || { echo "Auxio still does not play; E1's children are NOT run"; exit 6; }
echo "--- 3. E1's children ($(date -Is))"
E1_PART=children E1_REUSE="$P16/E1-fixbuild-run1-driver-faults-and-auxio-fixture-87-6" bash "$P16/scripts/e1.sh"
rc=$?
echo "E1_CHILDREN rc=$rc"
exit $rc
