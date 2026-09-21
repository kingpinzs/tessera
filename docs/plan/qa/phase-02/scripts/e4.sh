#!/usr/bin/env bash
# E4: the layout survives process death and a reboot, byte for byte (the store is the layout's truth).
# usage: e4.sh <out dir>
set -u
source "$(dirname "$0")/gestures.sh"
source "$(dirname "$0")/layout.sh"
OUT=$1; LOG=$OUT/E04.txt
mkdir -p "$OUT"; : > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
say "# E4 $(date -Iseconds)"
ensure_start
dump "$OUT/e4_before.xml"
layout_save "$OUT/e4_before.json"
adb exec-out screencap -p > "$OUT/e4_before.png"
say "--- force-stop then Home ---"
adb shell am force-stop app.tileshell
ensure_start
dump "$OUT/e4_after_forcestop.xml"
layout_save "$OUT/e4_after_forcestop.json"
adb exec-out screencap -p > "$OUT/e4_after_forcestop.png"
diff "$OUT/e4_before.json" "$OUT/e4_after_forcestop.json" > "$OUT/e4_forcestop_store.diff" && say "store identical after force-stop: PASS" || say "store CHANGED after force-stop: FAIL (see e4_forcestop_store.diff)"
python3 "$(dirname "$0")/dumpdiff.py" "$OUT/e4_before.xml" "$OUT/e4_after_forcestop.xml" | tee -a "$LOG"
say "--- reboot ---"
adb reboot
adb wait-for-device
for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 4; done
sleep 8
ensure_start
dump "$OUT/e4_after_reboot.xml"
layout_save "$OUT/e4_after_reboot.json"
adb exec-out screencap -p > "$OUT/e4_after_reboot.png"
diff "$OUT/e4_before.json" "$OUT/e4_after_reboot.json" > "$OUT/e4_reboot_store.diff" && say "store identical after reboot: PASS" || say "store CHANGED after reboot: FAIL (see e4_reboot_store.diff)"
python3 "$(dirname "$0")/dumpdiff.py" "$OUT/e4_before.xml" "$OUT/e4_after_reboot.xml" | tee -a "$LOG"
