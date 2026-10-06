#!/usr/bin/env bash
# The provider from outside: content read fails; through qa-capture's send probe with a real grant the granted URI
# reads, and the same URI with its path rewritten to a private file is refused.
. "$(dirname "$0")/t4.sh"; take_device_lock; leg c-provider
adb logcat -c
PRIVATE=/data/data/app.tileshell/files/files-recent.json
assert_ne "precondition: the private file exists" "" "$(recent_json)"
# ---- content read, from the shell uid
for u in "content://app.tileshell.files/root$PRIVATE" "content://app.tileshell.files/root/storage/emulated/0/QA-Files/a.txt"; do
  n="$(echo "$u" | md5sum | cut -c1-6)"; M=$(ring_mark)
  adb shell "content read --uri $u" > "$ROW_DIR/content-read-$n.out" 2>&1; echo $? > "$ROW_DIR/content-read-$n.rc"
  echo "content read $u -> rc $(cat "$ROW_DIR/content-read-$n.rc"): $(head -c 300 "$ROW_DIR/content-read-$n.out" | tr '\n' ' ')"
  assert_contains "content read $u fails" "Exception" "$(cat "$ROW_DIR/content-read-$n.out")"
  absent_in "…and prints none of the file" "recent" "wall= $(grep -v Exception "$ROW_DIR/content-read-$n.out" | grep -v '^\s*at ')"
  record "ring [files] lines for that read" "$(ring_since $M | grep -F '[files]' | xargs)"
done
# ---- the probe, with a real grant
adb install -r "$QAC_APK" 2>&1 | tail -1
adb shell "run-as $QAC sh -c 'mkdir -p files && printf %s $PRIVATE > files/rewrite_path.txt && cat files/rewrite_path.txt'"; echo
WANT="$(q "md5sum /sdcard/QA-Files/a.txt" | cut -d' ' -f1)"
ensure_start; files_at $QF; D 01; hold 01 files_row:a.txt; D 02; M=$(ring_mark); T 02 files_hold:share 3; D 03-chooser; S 03-chooser
assert_eq "share: the chooser" "com.android.intentresolver" "$(top_activity | cut -d/ -f1)"
xy="$(python3 - "$ROW_DIR/03-chooser.xml" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for m in re.finditer(r"<node[^>]*>", x):
    s = m.group(0)
    if 'text="QA Capture"' in s:
        a = [int(v) for v in re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s).groups()]
        print((a[0] + a[2]) // 2, a[1] - 120); break
PY
)"
echo "QA Capture at: [$xy] (chooser texts: $(grep -o 'text="[^"]\+"' "$ROW_DIR/03-chooser.xml" | xargs | cut -c1-300))"
adb shell input tap $xy; sleep 3
adb logcat -d -s "$QAC_TAG" | grep -F "send " | tee "$ROW_DIR/04-probe.txt"
SL="$(ring_since $M)"; echo "$SL" | grep -F "[files]" | tee "$ROW_DIR/04-ring.txt"
assert_contains "the granted URI reads: md5 = a.txt's ($WANT)" "uri=content://app.tileshell.files/root/storage/emulated/0/QA-Files/a.txt md5=$WANT" "$(cat "$ROW_DIR/04-probe.txt")"
assert_contains "the rewritten path is refused" "send rewrite uri=content://app.tileshell.files/root$PRIVATE outcome=refused" "$(cat "$ROW_DIR/04-probe.txt")"
record "the ring's refusal lines in the slice" "$(echo "$SL" | grep -c 'share refused: outside shared storage')"
adb logcat -d | grep -iE "Permission Denial.*app.tileshell.files|SecurityException.*tileshell.files" | tail -3 | tee "$ROW_DIR/04-logcat-denial.txt"
adb shell "run-as $QAC rm -f files/rewrite_path.txt"
ensure_start
leg_end
