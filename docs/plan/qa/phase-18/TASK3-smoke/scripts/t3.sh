#!/usr/bin/env bash
# Task 3 / 8 / 9 smoke helpers (the UI builder's own checks; evidence under qa/phase-18/TASK3-smoke/<leg>/).
# Sourced by a bash shell: . t3.sh; leg <name>; …
export ANDROID_SERIAL=emulator-5554
export PATH="$PATH:$HOME/Android/Sdk/platform-tools"
T3="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$T3/../scripts/lib.sh"
. "$T3/../scripts/p18.sh"
set +u; set +o pipefail

leg() { # name — a leg's own folder and log
  ROW="$1"; ROW_DIR="$T3/$1"; mkdir -p "$ROW_DIR"; LOG="$ROW_DIR/log.txt"
  [ -f "$LOG" ] || echo "leg $1 at $(date -Is) apk $(md5sum "$APK" | cut -c1-12) installed $(installed_apk_id)" > "$LOG"
  echo "--- $(date -Is) apk $(md5sum "$APK" | cut -c1-12)" >> "$LOG"
}
D() { dump_ui "$ROW_DIR/$1.xml"; }                       # D name
G() { gdump "$ROW_DIR/$1.xml"; }
S() { screencap "$ROW_DIR/$1.png"; }
T() { tap_node "$ROW_DIR/$1.xml" "$2"; sleep "${3:-1}"; } # T dump id [settle]
DT() { D "$1" && T "$1" "$2" "${3:-1}"; }                 # dump then tap
B() { bounds "$ROW_DIR/$1.xml" "$2"; }
X() { node_text "$ROW_DIR/$1.xml" "$2"; }
H() { has_node "$ROW_DIR/$1.xml" "$2"; }
ids() { grep -o 'resource-id="[^"]*"' "$ROW_DIR/$1.xml" | sed 's/resource-id="//;s/"$//' | grep -v '^$' | grep -v '^android:\|^com.android' | xargs; }
hold() { # dump id — a 900-ms press on the node's centre
  local b; b="$(bounds "$ROW_DIR/$1.xml" "$2")"; set -- $b
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 900; sleep 0.6
}
epx() { python3 -c "import sys; print(' '.join('%.1f' % (int(v)/3) for v in sys.argv[1:]))" "$@"; }
files_open() { adb shell am start -W -n "$FILES_ACTIVITY" "$@" >/dev/null 2>&1; sleep 1.5; }
files_at() { files_open --es path "$1"; }
ring() { ring_since "$1" | grep -E "\[(files|motion)\]" ; }
say_() { echo "$*" | tee -a "$LOG"; }
crash() { adb logcat -d -s AndroidRuntime:E | grep -c "app.tileshell" ; }
# Delete one row through the app: hold → Delete → the confirmation's Delete.
bin_it() { files_at "$1"; scroll_to_node "$ROW_DIR/d0.xml" "files_row:$2" >/dev/null; hold d0 "files_row:$2"; D d1; T d1 files_hold:delete 1; D d2; T d2 files_dialog:ok 1.5; }
# The bin page with the named rows selected (dump b2).
binsel() { files_open --es page bin; D b0; T b0 files_bar:select 1; for n in "$@"; do D b1; T b1 "files_bin_row:$n" 0.6; done; D b2; }
QF=/storage/emulated/0/QA-Files
# Select one row, choose Move to / Copy to, descend the named folders in the picker, confirm.
pick_op() { # verb name dest-row…
  local verb=$1 name=$2; shift 2
  D p1; T p1 files_bar:select 1; scroll_to_node "$ROW_DIR/p2.xml" "files_row:$name" >/dev/null; T p2 "files_row:$name" 0.6; D p3; T p3 files_sel:$verb 1.5
  for r in "$@"; do scroll_to_node "$ROW_DIR/p4.xml" "files_row:$r" >/dev/null; T p4 "files_row:$r" 1.3; done
  D p5; T p5 files_pick_ok 0.2
}
px() { python3 - "$1" "$2" "$3" <<'PY'
import sys
from PIL import Image
print(Image.open(sys.argv[1]).convert("RGB").getpixel((int(sys.argv[2]), int(sys.argv[3]))))
PY
}
ZD=/storage/emulated/0/QA-Files/zips
zopen() { files_at $ZD; scroll_to_node "$ROW_DIR/z0.xml" "files_row:$1" >/dev/null; T z0 "files_row:$1" "${2:-2}"; }
rows() { python3 - "$ROW_DIR/$1.xml" <<'PY'
import re,sys
x=open(sys.argv[1],encoding='utf-8',errors='replace').read()
out=[]
for m in re.finditer(r"<node[^>]*>",x):
    s=m.group(0); r=re.search(r'resource-id="(files_(?:row|detail):[^"]*)"',s); t=re.search(r'text="([^"]*)"',s)
    if r: out.append("%s=[%s]"%(r.group(1),t.group(1) if t else ""))
print(" ".join(out))
PY
}
wait_op() { for i in $(seq 1 "${2:-60}"); do ring_since "$1" | grep -qE "(zip extract|zip create|copy|move) .*(done|cancelled|failed)" && break; sleep 1; done; sleep 1.5; }
