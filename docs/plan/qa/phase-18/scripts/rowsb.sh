#!/usr/bin/env bash
# Phase 18 rows, writer B's include (E4, E4b, E6, E7, E9, E12, E13, E14, E18, E19, E20's bodies): the screen-driving
# helpers the builders' smoke drivers proved (TASK3-smoke/scripts/t3.sh, TASK4-smoke/scripts/t4.sh), under the names
# they had there, plus a JVM-only row frame. Sourced AFTER lib.sh and p18.sh; it is stamped into every log (STAMP_FILES).
# Nothing here touches the host's audio.
STAMP_FILES="$P18/scripts/p18.sh $P18/scripts/rowsb.sh"
GATE_APK_MD5=87f6eac1b1ca2a02b263da89713be01f      # the gate candidate (ROWS-BRIEF "The device"); commit 80810722
GATE_COMMIT=80810722
# The lead's change of plan (2026-10-06): the adversarial review's fixes make a SECOND gate build; its md5 and commit are
# in the file below (the first 32-hex word is the APK's md5, the first other 7-40-hex word its commit) and replace the
# brief's for everything run from then on.
# 2026-10-06 23:30: a THIRD gate build (the case-only rename fix, EDGE_NAMES' defect) — the pointer below names its file.
GATE2_FILE=/tmp/claude-1000/-home-jeremyking/340515de-6745-4b8c-a7bc-95d3c74d1c3e/scratchpad/gate-build-3
if [ -s "$GATE2_FILE" ]; then
  _g="$(grep -oE '\b[0-9a-f]{32}\b' "$GATE2_FILE" | head -1)"; [ -n "$_g" ] && GATE_APK_MD5="$_g"
  _g="$(grep -oE '\b[0-9a-f]{7,40}\b' "$GATE2_FILE" | grep -vE '^[0-9a-f]{32}$' | grep -E '[a-f]' | head -1)"; [ -n "$_g" ] && GATE_COMMIT="$_g"
fi
# The gate builds BEFORE the current one whose rows still stand (the lead's ruling for build 3: the rows run on build 2
# stand unless FileOps.rename is their subject). E12 and E20 read a row's latest run when it is stamped with the current
# build or one of these, and say which. The brief's first build (87f6eac1) is NOT one: build 2 replaced it.
GATE_PRIOR_FILES="/tmp/claude-1000/-home-jeremyking/340515de-6745-4b8c-a7bc-95d3c74d1c3e/scratchpad/gate-build-2"
GATE_PRIOR_MD5S=""
for _f in $GATE_PRIOR_FILES; do
  [ -s "$_f" ] && GATE_PRIOR_MD5S="$GATE_PRIOR_MD5S $(grep -oE '\b[0-9a-f]{32}\b' "$_f" | head -1)"
done
GATE_PRIOR_MD5S="$(echo $GATE_PRIOR_MD5S)"
# "<current id> <prior id>…" — the 16-character ids the logs are stamped with, the current build first.
gate_ids() { local m out="${GATE_APK_MD5:0:16}"; for m in $GATE_PRIOR_MD5S; do [ "$m" != "$GATE_APK_MD5" ] && out="$out ${m:0:16}"; done; echo "$out"; }
QF=/storage/emulated/0/QA-Files                     # the real path the app logs (BUILD-NOTES pure layer 2)
ZD=$QF/zips
PROVISION="$P18/../phase-03/scripts/provision.sh"
BINP=/sdcard/.Tessera/bin

# ---------------------------------------------------------------- a JVM-only row (E18, E19 (a), E7's scope negative)
# lib.sh's row_begin takes the device lock, wakes the device and reads the installed APK — all adb. A row that only
# runs gradle must not touch a device another driver is using, so its frame runs NO adb command: the log is stamped
# with the driver / harness / include blobs, the gate APK FILE's md5, and the proof that the tests compile from the
# gate build's source (app/ at HEAD equals app/ at the gate commit, and app/ has no uncommitted change).
jvm_row_begin() { # id description
  ROW="$1"; ROW_DIR="$QA/$ROW"; mkdir -p "$ROW_DIR"; LOG="$ROW_DIR/$ROW.txt"; PASS=0; FAIL=0; RECORDED=0; ROW_MARK=""
  {
    echo "=============================================================================="
    echo "ROW $ROW — ${2:-}"
    echo "at            $(date -Is)"
    echo "driver        $(basename "$0") blob $(git -C "$REPO" hash-object "$HERE/$(basename "$0")")"
    echo "harness       lib.sh blob $(git -C "$REPO" hash-object "$HERE/lib.sh")"
    for _f in ${STAMP_FILES:-}; do [ -f "$_f" ] && echo "include       $(basename "$_f") blob $(git -C "$REPO" hash-object "$_f")"; done
    echo "apk built     md5 $(md5sum "$APK" 2>/dev/null | cut -d' ' -f1) $(stat -c%s "$APK" 2>/dev/null) bytes"
    echo "apk installed (not read: a JVM row runs no device command)"
    echo "source        HEAD $(git -C "$REPO" rev-parse --short=8 HEAD); app/ against $GATE_COMMIT: $(git -C "$REPO" diff --stat "$GATE_COMMIT" HEAD -- app | tail -1 | xargs)[end]; uncommitted under app/: $(git -C "$REPO" status --porcelain -- app | grep -c .)"
    echo "=============================================================================="
  } > "$LOG"
  echo "── $ROW ${2:-}"
  assert_eq "the APK file is the gate candidate (md5)" "$GATE_APK_MD5" "$(md5sum "$APK" | cut -d' ' -f1)"
  assert_eq "app/ at HEAD is app/ at the gate commit $GATE_COMMIT (git diff --quiet)" "0" "$(git -C "$REPO" diff --quiet "$GATE_COMMIT" HEAD -- app; echo $?)"
  assert_eq "app/ has no uncommitted change (git status --porcelain -- app)" "0" "$(git -C "$REPO" status --porcelain -- app | grep -c .)"
}
# row_end's ring_save returns before any adb when ROW_MARK is empty (lib.sh ring_save); its message is dropped.
jvm_row_end() { ROW_MARK=""; row_end 2>/dev/null; }

# jvm_gate (p18.sh) keeps one gradle.out / gradle.rc / test-reports per row. A row with several gates gives each its
# own sub-folder: jvm_gate_in <name> <tests pattern> <report glob> <case>…
jvm_gate_in() { # name pattern glob case…
  local name="$1" keep="$ROW_DIR"; shift
  mkdir -p "$keep/$name"
  ROW_DIR="$keep/$name"
  jvm_gate "$@"
  ROW_DIR="$keep"
}

# ---------------------------------------------------------------- the device's state, asserted
gate_apk_installed() { # the full md5 of the installed base.apk
  local p; p="$(adb shell pm path $PKG 2>/dev/null | head -1 | tr -d '\r' | sed 's/^package://')"
  [ -n "$p" ] && adb shell md5sum "$p" 2>/dev/null | cut -d' ' -f1 | tr -d '\r'
}
assert_gate_apk() { assert_eq "${1:-the installed APK is the gate candidate} (md5)" "$GATE_APK_MD5" "$(gate_apk_installed)"; }
appop_now() { q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE' | head -1 | cut -d';' -f1; }   # without its "; time=…" tail

# ---------------------------------------------------------------- dumps, taps, reads (t3.sh / t4.sh)
D() { dump_ui "$ROW_DIR/$1.xml"; }                        # D name
G() { gdump "$ROW_DIR/$1.xml"; }                          # a screen that never idles (RV13)
S() { screencap "$ROW_DIR/$1.png"; }
T() { tap_node "$ROW_DIR/$1.xml" "$2"; sleep "${3:-1}"; } # T dump id [settle]
B() { bounds "$ROW_DIR/$1.xml" "$2"; }
X() { node_text "$ROW_DIR/$1.xml" "$2" | sed "s/&apos;/'/g; s/&quot;/\"/g; s/&gt;/>/g; s/&lt;/</g; s/&amp;/\&/g"; }   # the dump's XML entities undone
H() { has_node "$ROW_DIR/$1.xml" "$2"; }
ids() { grep -o 'resource-id="[^"]*"' "$ROW_DIR/$1.xml" | sed 's/resource-id="//;s/"$//' | grep -v '^$' | grep -v '^android:\|^com.android' | xargs; }
EN() { grep -o "<node[^>]*resource-id=\"$2\"[^>]*>" "$ROW_DIR/$1.xml" | grep -o 'enabled="[a-z]*"' | head -1 | sed 's/enabled="//;s/"//'; }
texts() { grep -o 'text="[^"]\+"' "$ROW_DIR/$1.xml" | sed 's/^text="//;s/"$//' | tr '\n' '|'; }
hold() { # dump id — a 900-ms press on the node's centre
  local b; b="$(bounds "$ROW_DIR/$1.xml" "$2")"
  [ -n "$b" ] || { echo "hold: no node $2 in $1.xml" >&2; return 2; }
  # shellcheck disable=SC2086
  set -- $b
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 900; sleep 0.8
}
epx() { python3 -c "import sys; print(' '.join('%.1f' % (int(v)/3) for v in sys.argv[1:]))" "$@"; }
files_open() { adb shell am start -W -n "$FILES_ACTIVITY" "$@" >/dev/null 2>&1; sleep 1.5; }
files_at() { files_open --es path "$1"; }
crash() { adb logcat -d -s AndroidRuntime:E | grep -c "app.tileshell"; }
anr() { adb logcat -d | grep -c 'ANR in app.tileshell'; }
px() { python3 - "$1" "$2" "$3" <<'PY'
import sys
from PIL import Image
print("%d,%d,%d" % Image.open(sys.argv[1]).convert("RGB").getpixel((int(sys.argv[2]), int(sys.argv[3]))))
PY
}
near() { python3 -c "import sys; a=[int(v) for v in sys.argv[1].split(',')]; b=[int(v) for v in sys.argv[2].split(',')]; print('yes' if all(abs(x-y)<=int(sys.argv[3]) for x,y in zip(a,b)) else 'no')" "$1" "$2" "$3"; }
md5dev() { q "md5sum '$1' 2>/dev/null" | cut -d' ' -f1; }
lsdev() { q "ls -d '$1' 2>/dev/null"; }

# Open the folder and tap the named row (scrolling to it).
tap_row() { # folder name [settle]
  files_at "$1"; scroll_to_node "$ROW_DIR/o.xml" "files_row:$2" >/dev/null; tap_node "$ROW_DIR/o.xml" "files_row:$2"; sleep "${3:-2.5}"
}
# The files_row / files_detail nodes of a dump, in order: id=[text] …
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
row_names() { grep -o 'resource-id="files_row:[^"]*"' "$ROW_DIR/$1.xml" | sed 's/resource-id="files_row://;s/"$//' | tr '\n' '|'; }
bin_rows() { grep -o 'resource-id="files_bin_row:[^"]*"' "$ROW_DIR/$1.xml" | sed 's/resource-id="files_bin_row://;s/"$//' | tr '\n' '|'; }
recent_rows() { grep -o 'resource-id="files_recent_row:[^"]*"' "$ROW_DIR/$1.xml" | sed 's/resource-id="files_recent_row://;s/"$//' | xargs; }
recent_json() { adb shell run-as app.tileshell cat files/files-recent.json 2>/dev/null | tr -d '\r'; }
session_state() { # the shell's media sessions: lines "tag state=<S> desc=[…]"
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

# Delete one row through the app: hold → Delete → the confirmation's Delete. BIN_MARK = the MARK before the confirm.
bin_it() { # folder name
  files_at "$1"; scroll_to_node "$ROW_DIR/d0.xml" "files_row:$2" >/dev/null; hold d0 "files_row:$2"; D d1; T d1 files_hold:delete 1; D d2
  BIN_MARK="$(ring_mark)"; T d2 files_dialog:ok 1.5
}
# The bin page with the named rows selected (dump b2).
binsel() { files_open --es page bin; D b0; T b0 files_bar:select 1; for n in "$@"; do scroll_to_node "$ROW_DIR/b1.xml" "files_bin_row:$n" >/dev/null; T b1 "files_bin_row:$n" 0.6; done; D b2; }
bin_empty() { files_open --es page bin; D e0; T e0 files_bar:more 1; D e1; T e1 files_bin_empty 1; D e2; T e2 files_dialog:ok 1.5; }
# Select one row, choose Move to / Copy to, descend the named folders in the picker, confirm.
pick_op() { # verb name dest-row…
  local verb=$1 name=$2 r; shift 2
  D p1; T p1 files_bar:select 1; scroll_to_node "$ROW_DIR/p2.xml" "files_row:$name" >/dev/null; T p2 "files_row:$name" 0.6; D p3; T p3 "files_sel:$verb" 1.5
  _pick_walk "$@"
  D p5; T p5 files_pick_ok 0.2
}
# Inside the picker: each argument is a folder row to enter, or `..` for the picker's ↑.
_pick_walk() {
  local r
  for r in "$@"; do
    if [ "$r" = .. ]; then D p4; T p4 files_up 1.3
    else scroll_to_node "$ROW_DIR/p4.xml" "files_row:$r" >/dev/null; T p4 "files_row:$r" 1.3; fi
  done
}
# The same through the row's hold menu (files_hold:copy | move), which opens the picker at once.
hold_op() { # verb name dest…
  local verb=$1 name=$2; shift 2
  scroll_to_node "$ROW_DIR/h1.xml" "files_row:$name" >/dev/null; hold h1 "files_row:$name"; D h2; T h2 "files_hold:$verb" 1.5
  _pick_walk "$@"
  D p5; T p5 files_pick_ok 0.2
}
# The same, with the destination chosen from the picker's ≡ pane (a volume row), then the named folders.
pick_op_pane() { # verb name pane-row dest-row…
  local verb=$1 name=$2 pane=$3 r; shift 3
  D p1; T p1 files_bar:select 1; scroll_to_node "$ROW_DIR/p2.xml" "files_row:$name" >/dev/null; T p2 "files_row:$name" 0.6; D p3; T p3 "files_sel:$verb" 1.5
  D p4; T p4 files_menu 1; D p4b; T p4b "files_pane:$pane" 1.5
  _pick_walk "$@"
  D p5; T p5 files_pick_ok 0.2
}
zopen() { files_at "$ZD"; scroll_to_node "$ROW_DIR/z0.xml" "files_row:$1" >/dev/null; T z0 "files_row:$1" "${2:-2}"; }
# Wait for an operation's end line in the slice from a MARK (done | cancelled | failed); prints it.
wait_op() { # mark [seconds]
  local i l=""
  for i in $(seq 1 "${2:-60}"); do
    l="$(ring_since "$1" | grep -E "\[files\] (zip extract|zip create|copy|move) .*(done|cancelled|failed)" | grep -v ' progress ' | tail -1)"
    [ -n "$l" ] && break; sleep 1
  done
  sleep 1.5; printf '%s\n' "$l"
}
# Wait until the slice from a MARK holds a needle (fixed string); prints the first such line.
wait_line() { # mark needle [seconds] [ring]
  local i l=""
  for i in $(seq 1 $(( ${3:-10} * 4 ))); do
    l="$(ring_since "$1" ${4:+"$4"} | grep -F -- "$2" | head -1)"; [ -n "$l" ] && break; sleep 0.25
  done
  printf '%s\n' "$l"
}
# The tap point of the operation notification's expander, or of its Cancel action (TASK3-smoke/scripts/shade_xy.py).
shade_xy() { python3 - "$1" "$2" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
def nodes(pred):
    for m in re.finditer(r"<node[^>]*>", x):
        s = m.group(0)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        if b and pred(s):
            l, t, r, bt = map(int, b.groups())
            yield (l + r) // 2, (t + bt) // 2
if sys.argv[2] == "cancel":
    for p in nodes(lambda s: re.search(r'text="Cancel"', s, re.I) or 'content-desc="Cancel"' in s): print(*p); break
else:
    title = next(nodes(lambda s: 'text="Copying files' in s or 'text="Moving files' in s or 'text="Extracting' in s or 'text="Creating' in s), None)
    if title:
        best = min(nodes(lambda s: 'expand_button' in s), key=lambda p: abs(p[1] - title[1]), default=None)
        if best: print(*best)
PY
}
# Cancel the running operation from its notification (H5: Cancel lives only there): Home, the shade, expand, Cancel.
notif_cancel() { # dump-prefix
  local xy
  local i
  adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell cmd statusbar expand-notifications; sleep 2; gdump "$ROW_DIR/$1-shade0.xml"
  xy="$(shade_xy "$ROW_DIR/$1-shade0.xml" cancel)"
  cp "$ROW_DIR/$1-shade0.xml" "$ROW_DIR/$1-shade.xml"
  # The notification comes up collapsed: its expander is tapped until the Cancel action is in the dump (the first tap
  # can land while the shade is still settling — E4's run 1).
  for i in 1 2 3; do
    [ -n "$xy" ] && break
    xy="$(shade_xy "$ROW_DIR/$1-shade.xml" expand)"; note "notif_cancel: expander at [$xy] (try $i)"
    # shellcheck disable=SC2086
    [ -n "$xy" ] && adb shell input tap $xy; sleep 1.5; gdump "$ROW_DIR/$1-shade.xml"
    xy="$(shade_xy "$ROW_DIR/$1-shade.xml" cancel)"
  done
  note "notif_cancel: Cancel at [$xy]"
  # shellcheck disable=SC2086
  [ -n "$xy" ] && adb shell input tap $xy
  sleep 2; adb shell cmd statusbar collapse; sleep 1
  [ -n "$xy" ]
}
# The centre of a chooser target by its label (the label sits under its icon): "x y".
chooser_xy() { # dump.xml label [dy]
  python3 - "$1" "$2" "${3:-120}" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for m in re.finditer(r"<node[^>]*>", x):
    s = m.group(0)
    if 'text="%s"' % sys.argv[2] in s:
        a = [int(v) for v in re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s).groups()]
        print((a[0] + a[2]) // 2, a[1] - int(sys.argv[3])); break
PY
}
# Type into the dialog's text box, replacing what is there (rename selects the whole name, so typing replaces it).
dialog_type() { adb shell input text "$1"; sleep 0.8; }

# ---------------------------------------------------------------- the Recycle Bin's index (RecycleBin.kt: version, records[bin, path, deletedAt, size])
# bin_index [bin folder] -> one line per record: <bin name>|<original path>; "UNREADABLE" when it is not that JSON.
bin_index() {
  adb shell "cat '${1:-$BINP}/.index.json'" 2>/dev/null | python3 -c '
import json, sys
try:
    d = json.load(sys.stdin)
    for r in d["records"]: print("%s|%s" % (r["bin"], r["path"]))
except Exception as e:
    print("UNREADABLE (%s)" % type(e).__name__)'
}
bin_index_count() { bin_index "${1:-$BINP}" | grep -c '|'; }
bin_ls() { q "ls -a '${1:-$BINP}' 2>&1" | grep -v '^\.\.\?$' | LC_ALL=C sort | xargs; }
# The bin name (<ms>-<seq>-<name>) the index records for an original path.
bin_name_of() { bin_index "${2:-$BINP}" | awk -F'|' -v p="$1" '$2 == p { print $1 }' | tail -1; }

# The n-th node (1-based) with a resource-id: its bounds, or a tap on its centre.
nth_bounds() { # dump id n
  python3 - "$ROW_DIR/$1.xml" "$2" "$3" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
hits = [m.group(0) for m in re.finditer(r"<node[^>]*>", x) if 'resource-id="%s"' % sys.argv[2] in m.group(0)]
n = int(sys.argv[3])
if len(hits) >= n:
    print(" ".join(re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', hits[n - 1]).groups()))
PY
}
tap_nth() { # dump id n [settle]
  local b; b="$(nth_bounds "$1" "$2" "$3")"
  [ -n "$b" ] || { echo "tap_nth: no node $2 #$3 in $1.xml" >&2; return 2; }
  # shellcheck disable=SC2086
  set -- $b "${4:-0.6}"
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep "$5"
}
count_nodes() { grep -o "resource-id=\"$2\"" "$ROW_DIR/$1.xml" | wc -l | xargs; }
# The texts of every node with a resource-id, in order, joined with |.
node_texts() { python3 - "$ROW_DIR/$1.xml" "$2" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
out = []
for m in re.finditer(r"<node[^>]*>", x):
    s = m.group(0)
    if 'resource-id="%s"' % sys.argv[2] in s:
        out.append(re.search(r'text="([^"]*)"', s).group(1))
print("|".join(out))
PY
}
# Search from the folder on screen: the bar's Search, the term typed into files_search_box.
search_for() { # term dump-name
  D s0; T s0 files_bar:search 1; D s1; T s1 files_search_box 0.6
  adb shell input text "$1"; sleep 2; D "$2"
  if [ "$(X "$2" files_sort)" != "Sort by: Relevance" ]; then adb shell input keyevent KEYCODE_ENTER; sleep 2; D "$2"; fi
  # The keyboard covers the app bar while the box has it: a tap at the bar's dumped bounds would land on a key (E4b's
  # run 1). Back puts the keyboard away — only when it is showing, since Back with no keyboard leaves search.
  local ime; ime="$(adb shell dumpsys input_method | tr -d '\r' | grep -c 'mInputShown=true')"     # counted, not grep -q: pipefail
  if [ "${ime:-0}" != 0 ]; then adb shell input keyevent KEYCODE_BACK; sleep 1; D "$2"; fi
}

# The operation notification as dumpsys notification holds it (channel files_ops): title=[…] actions=[…] flags=…
ops_notification() { # out.txt
  adb shell dumpsys notification --noredact > "$1"
  python3 - "$1" <<'PY'
import re, sys
t = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for r in re.split(r"\n(?=\s+NotificationRecord\()", t):
    if "pkg=app.tileshell" in r and "channel=files_ops" in r:
        title = re.search(r"android\.title=String \(([^)]*)\)", r)
        acts = re.findall(r'\[\d+\] "([^"]*)" -> PendingIntent', r)
        print("title=[%s] actions=[%s] flags=%s" % (title.group(1) if title else "", ",".join(acts), (re.search(r"flags=([A-Z_|]+)", r) or [None, ""])[1]))
        break
PY
}

# ---------------------------------------------------------------- lists, flings, temps, the checklist (the edge sub-steps)
wall_of() { sed -n 's/.*wall=\([0-9]*\).*/\1/p' | head -1; }              # the wall= stamp of a ring line (stdin)
first_row() { grep -o 'resource-id="files_row:[^"]*"' "$ROW_DIR/$1.xml" | head -1 | sed 's/resource-id="files_row://;s/"$//'; }
last_row() { grep -o 'resource-id="files_row:[^"]*"' "$ROW_DIR/$1.xml" | tail -1 | sed 's/resource-id="files_row://;s/"$//'; }
# Fling the list towards its end until the named row is in the dump: <rounds> rounds of <swipes> back-to-back swipes
# (one device-side loop per round — a touch-down stops the running fling, so the list keeps close to its top speed).
fling_end() { # row-id dump-name [rounds] [swipes per round]
  local i
  for i in $(seq 1 "${3:-20}"); do
    adb shell "i=0; while [ \$i -lt ${4:-40} ]; do input swipe 540 1900 540 400 40; i=\$((i + 1)); done"
    sleep 2.5; D "$2"
    if [ "$(H "$2" "$1")" = yes ]; then note "fling_end: $1 reached after $i round(s) of ${4:-40} swipes"; return 0; fi
  done
  note "fling_end: $1 NOT reached after ${3:-20} round(s) of ${4:-40} swipes; the dump's last row is $(last_row "$2")"
  return 1
}
# Every temp the write layer can leave (r3 D4's names) under a tree.
temps_in() { q "find $1 \( -name '.*.part' -o -name '.*.extract' \) 2>/dev/null" | xargs; }
# Settings > Setup checklist, scrolled to the Files row (E1's way in).
checklist_files() { # dump-name -> granted | missing | absent
  adb shell am start -W -n app.tileshell/.settings.SettingsActivity --activity-clear-task --es page CHECKLIST >/dev/null 2>&1; sleep 3
  scroll_to_node "$ROW_DIR/$1.xml" "checklist:files:granted" 10 >/dev/null 2>&1 || true
  if [ "$(H "$1" checklist:files:granted)" = yes ]; then echo granted
  elif grep -q 'resource-id="checklist:files:missing"' "$ROW_DIR/$1.xml"; then echo missing
  else echo absent; fi
}
# Run a few device-shell lines from a pushed script file (a name with spaces or non-ASCII survives; nothing is inline).
dev_script() { # name  (the lines on stdin) -> its output
  cat > "$ROW_DIR/$1.sh"
  adb push "$ROW_DIR/$1.sh" /data/local/tmp/p18-rowsb.sh >/dev/null 2>&1
  q "sh /data/local/tmp/p18-rowsb.sh 2>&1; rm -f /data/local/tmp/p18-rowsb.sh"
}
journal_json() { adb shell run-as app.tileshell cat files/files-ops.json 2>/dev/null | tr -d '\r'; }
boot_wait() { # after adb reboot: the device back, boot completed
  local i
  adb wait-for-device
  for i in $(seq 1 120); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 2; done
  sleep 12
}
