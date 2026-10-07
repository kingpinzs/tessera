#!/usr/bin/env bash
# Phase 18 build task 1 smoke: the grant, the checklist row, the ungranted page and its link.
export ANDROID_SERIAL=emulator-5554
REPO=/home/jeremyking/projects/metro-launcher-p18
. "$REPO/docs/plan/qa/phase-03/scripts/lib.sh"
set +e
E="$REPO/docs/plan/qa/phase-18/TASK1-smoke"
op() { adb shell appops get app.tileshell MANAGE_EXTERNAL_STORAGE | tr -d '\r'; }
pid() { adb shell pidof app.tileshell | tr -d '\r'; }
checklist() { # out.xml
  adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page CHECKLIST >/dev/null
  sleep 3
  scroll_to_node "$1" "checklist:files:$2" 8 || dump_ui "$1"
  grep -o 'resource-id="checklist:files:[a-z]*"' "$1"
}
echo "wake: $(wake_device)"
echo "apk on device: $(installed_apk_id 2>/dev/null)"

echo "=== (a) appop allow"
adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow
echo "appop: $(op)"
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"; echo "MARK=$MARK"
echo "checklist row: $(checklist "$E/a-checklist.xml" granted)"
echo "row text: $(python3 - "$E/a-checklist.xml" <<'PY'
import re,sys
x=open(sys.argv[1],encoding='utf-8',errors='replace').read()
i=x.find('checklist:files:')
print(re.findall(r'text="([^"]+)"', x[i:i+1500])[:2])
PY
)"
adb shell am start -W -n app.tileshell/.files.FilesActivity | tr -d '\r' | grep -E 'Status|Activity|LaunchState'
sleep 3
dump_ui "$E/a-files.xml"; screencap "$E/a-files.png"
echo "files_root=$(has_node "$E/a-files.xml" files_root) files_ungranted=$(has_node "$E/a-files.xml" files_ungranted) files_grant_link=$(has_node "$E/a-files.xml" files_grant_link) status_bar=$(has_node "$E/a-files.xml" w10m_status_bar) nav_bar=$(has_node "$E/a-files.xml" w10m_nav_bar)"
adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|ResumedActivity' | tr -d '\r'
ring_since "$MARK" > "$E/a-ring.txt"
grep -F '[files]' "$E/a-ring.txt"
grep -F '[checklist]' "$E/a-ring.txt" | tail -1 | grep -o 'files=[A-Z]*'

echo "=== (b) appop default"
echo "pid before revoke: $(pid)"
adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE default
sleep 2
echo "appop: $(op)"
echo "pid after revoke: $(pid)"
MARK="$(ring_mark)"; echo "MARK=$MARK"
adb shell am start -W -n app.tileshell/.files.FilesActivity | tr -d '\r' | grep -E 'Status|Activity|LaunchState'
sleep 3
dump_ui "$E/b-files.xml"; screencap "$E/b-files.png"
echo "files_ungranted=$(has_node "$E/b-files.xml" files_ungranted) text=[$(node_text "$E/b-files.xml" files_ungranted)]"
echo "files_grant_link=$(has_node "$E/b-files.xml" files_grant_link) text=[$(node_text "$E/b-files.xml" files_grant_link)]"
grep -o 'text="[^"]*checklist[^"]*"' "$E/b-files.xml"
ring_since "$MARK" > "$E/b-ring.txt"
grep -F '[files]' "$E/b-ring.txt"
tap_node "$E/b-files.xml" files_grant_link
sleep 3
adb shell dumpsys activity activities > "$E/b-activities.txt"
grep -m2 -E 'topResumedActivity|mResumedActivity|ResumedActivity' "$E/b-activities.txt" | tr -d '\r'
screencap "$E/b-settings.png"
ring_since "$MARK" | grep -F '[files]' > "$E/b-ring-after-link.txt"; cat "$E/b-ring-after-link.txt"
adb shell input keyevent KEYCODE_BACK; sleep 1
MARK2="$(ring_mark)"
echo "checklist row: $(checklist "$E/b-checklist.xml" missing)"
screencap "$E/b-checklist.png"
ring_since "$MARK2" | grep -F '[checklist]' | tail -1 | grep -o 'files=[A-Z]*'

echo "=== grant while running (no kill): the page follows on resume"
adb shell am start -W -n app.tileshell/.files.FilesActivity >/dev/null; sleep 2
MARK3="$(ring_mark)"; P1="$(pid)"
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow
adb shell am start -W -n app.tileshell/.files.FilesActivity >/dev/null; sleep 3
dump_ui "$E/b2-files.xml"
echo "pid $P1 -> $(pid); appop: $(op); files_ungranted=$(has_node "$E/b2-files.xml" files_ungranted) files_root=$(has_node "$E/b2-files.xml" files_root)"
ring_since "$MARK3" | grep -F '[files]' | tee "$E/b2-ring.txt"

echo "=== (d) crashes"
adb logcat -d -s AndroidRuntime > "$E/d-logcat.txt"
echo "AndroidRuntime lines naming app.tileshell: $(grep -c 'app.tileshell' "$E/d-logcat.txt")"
grep -c 'FATAL EXCEPTION' "$E/d-logcat.txt"
