#!/usr/bin/env bash
# Task 4 / 10 smoke helpers (the open-with builder's own checks; evidence under qa/phase-18/TASK4-smoke/<leg>/).
export ANDROID_SERIAL=emulator-5554
export PATH="$PATH:$HOME/Android/Sdk/platform-tools"
export TMPDIR=/tmp/claude-1000/-home-jeremyking/340515de-6745-4b8c-a7bc-95d3c74d1c3e/scratchpad/task4
mkdir -p "$TMPDIR"
T4="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$T4/../scripts/lib.sh"
. "$T4/../scripts/p18.sh"
set +u; set +o pipefail

leg() { # name — a leg's own folder and log; an earlier run's folder is kept as <name>-run<k>
  ROW="$1"; ROW_DIR="$T4/$1"
  if [ -d "$ROW_DIR" ] && [ -z "$KEEP_LEG" ]; then k=1; while [ -e "$ROW_DIR-run$k" ]; do k=$((k + 1)); done; mv "$ROW_DIR" "$ROW_DIR-run$k"; fi
  mkdir -p "$ROW_DIR"; LOG="$ROW_DIR/log.txt"; PASS=0; FAIL=0; RECORDED=0
  echo "leg $1 at $(date -Is) apk $(md5sum "$APK" | cut -c1-12) installed $(installed_apk_id) match $(apk_matches)" >> "$LOG"
  wake_device >/dev/null
  ROW_MARK="$(ring_mark)"
}
leg_end() { echo "== $ROW: $PASS passed, $FAIL failed, $RECORDED recorded; AndroidRuntime app.tileshell lines: $(crash)" | tee -a "$LOG"; }
D() { dump_ui "$ROW_DIR/$1.xml"; }
G() { gdump "$ROW_DIR/$1.xml"; }
S() { screencap "$ROW_DIR/$1.png"; }
T() { tap_node "$ROW_DIR/$1.xml" "$2"; sleep "${3:-1}"; }
B() { bounds "$ROW_DIR/$1.xml" "$2"; }
X() { node_text "$ROW_DIR/$1.xml" "$2"; }
H() { has_node "$ROW_DIR/$1.xml" "$2"; }
ids() { grep -o 'resource-id="[^"]*"' "$ROW_DIR/$1.xml" | sed 's/resource-id="//;s/"$//' | grep -v '^$' | grep -v '^android:\|^com.android' | xargs; }
hold() { # dump id — a 900-ms press on the node's centre
  local b; b="$(bounds "$ROW_DIR/$1.xml" "$2")"; set -- $b
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 900; sleep 0.8
}
epx() { python3 -c "import sys; print(' '.join('%.1f' % (int(v)/3) for v in sys.argv[1:]))" "$@"; }
files_open() { adb shell am start -W -n "$FILES_ACTIVITY" "$@" >/dev/null 2>&1; sleep 1.5; }
files_at() { files_open --es path "$1"; }
crash() { adb logcat -d -s AndroidRuntime:E | grep -c "app.tileshell" ; }
px() { python3 - "$1" "$2" "$3" <<'PY'
import sys
from PIL import Image
print("%d,%d,%d" % Image.open(sys.argv[1]).convert("RGB").getpixel((int(sys.argv[2]), int(sys.argv[3]))))
PY
}
# near "r,g,b" "r,g,b" tol -> yes/no
near() { python3 -c "import sys; a=[int(v) for v in sys.argv[1].split(',')]; b=[int(v) for v in sys.argv[2].split(',')]; print('yes' if all(abs(x-y)<=int(sys.argv[3]) for x,y in zip(a,b)) else 'no')" "$1" "$2" "$3"; }
QF=/storage/emulated/0/QA-Files
# Open the folder and tap the named row (scrolling to it); dump name = o-<tag>
tap_row() { # folder name [settle]
  files_at "$1"; scroll_to_node "$ROW_DIR/o.xml" "files_row:$2" >/dev/null; tap_node "$ROW_DIR/o.xml" "files_row:$2"; sleep "${3:-2.5}"
}
recent_rows() { # dump -> the files_recent_row names in order
  grep -o 'resource-id="files_recent_row:[^"]*"' "$ROW_DIR/$1.xml" | sed 's/resource-id="files_recent_row://;s/"$//' | xargs
}
recent_json() { adb shell run-as app.tileshell cat files/files-recent.json 2>/dev/null | tr -d '\r'; }
session_state() { # package-owned media sessions: lines "tag state description"
  adb shell dumpsys media_session | tr -d '\r' | python3 -c '
import re, sys
t = sys.stdin.read()
for blk in re.split(r"\n(?=    \S[^\n]* app\.tileshell/)", t):
    m = re.match(r"\s*(\S+) (app\.tileshell/\S+)", blk)
    if not m: continue
    st = re.search(r"state=PlaybackState \{state=(\w+)\((\d+)\)", blk) or re.search(r"state=PlaybackState \{state=(\d+)", blk)
    d = re.search(r"metadata: size=\d+, description=(.*)", blk)
    print(m.group(1), "state=" + (st.group(1) if st else "?"), "desc=[" + (d.group(1).strip() if d else "") + "]")
'
}
