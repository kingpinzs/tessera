#!/usr/bin/env bash
# Phase 18 rows E1 E2 E3 E5 E8 E10 E11 E15 E16 E17: the helpers those drivers share, sourced AFTER lib.sh and p18.sh
# (row_begin stamps this file's blob: it is added to STAMP_FILES). Nothing here asserts on its own except assert_rgb /
# assert_near / assert_le / assert_ge / assert_gap, which are plain comparisons through lib.sh's _verdict.
STAMP_FILES="$STAMP_FILES $P18/scripts/p18_a.sh"
QF=/storage/emulated/0/QA-Files
SD=/storage/emulated/0

# ---- dumps, taps, readers — by a short name inside the row's folder
D() { dump_ui "$ROW_DIR/$1.xml"; }
G() { gdump "$ROW_DIR/$1.xml"; }
S() { screencap "$ROW_DIR/$1.png"; }
T() { tap_node "$ROW_DIR/$1.xml" "$2"; sleep "${3:-1}"; }
B() { bounds "$ROW_DIR/$1.xml" "$2"; }
X() { node_text "$ROW_DIR/$1.xml" "$2" | sed "s/&apos;/'/g; s/&amp;/\&/g; s/&quot;/\"/g; s/&gt;/>/g; s/&lt;/</g"; }
H() { has_node "$ROW_DIR/$1.xml" "$2"; }
# One attribute of a node (selected, enabled, checked, …).
attr() { # dump id attribute
  python3 - "$ROW_DIR/$1.xml" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if 'resource-id="%s"' % sys.argv[2] in s:
        m = re.search(r' %s="([^"]*)"' % re.escape(sys.argv[3]), s)
        print(m.group(1) if m else "")
        break
PY
}
# The ids of a dump that start with a prefix, in document order, the prefix cut: `ids_of d files_row:` -> a.txt b.bin …
ids_of() { # dump prefix
  python3 - "$ROW_DIR/$1.xml" "$2" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
out = []
for m in re.finditer(r'resource-id="([^"]*)"', xml):
    rid = html.unescape(m.group(1))
    if rid.startswith(sys.argv[2]): out.append(rid[len(sys.argv[2]):])
print("\n".join(out))
PY
}
# Every files_* / quick_* / rec_* / wizard_* / checklist id of a dump with its text and its box in epx (a debugging aid
# written beside the dump; nothing asserts on it).
nodes() { # dump
  python3 - "$ROW_DIR/$1.xml" <<'PY'
import html, re, sys
x = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for m in re.finditer(r'<node [^>]*>', x):
    n = m.group(0)
    rid = re.search(r'resource-id="([^"]*)"', n).group(1)
    if not rid or rid.startswith('android:') or rid.startswith('com.android'): continue
    mt = re.search(r' text="([^"]*)"', n); t = html.unescape(mt.group(1)) if mt else ""
    b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', n).groups()
    me = re.search(r'enabled="([^"]*)"', n); ms = re.search(r'selected="([^"]*)"', n)
    en = me.group(1) if me else "true"; sel = ms.group(1) if ms else "false"
    l, t0, r, bm = [int(v) / 3 for v in b]
    print("%s\t%r\tx %.1f-%.1f y %.1f-%.1f\tw %.1f h %.1f%s%s" % (html.unescape(rid), t, l, r, t0, bm, r - l, bm - t0, "" if en == "true" else " DISABLED", " SELECTED" if sel == "true" else ""))
PY
}
# A node's box in epx: "left top right bottom" (px / 3, one decimal); empty when the node is not there.
ebox() { # dump id
  local b; b="$(B "$1" "$2")"; [ -n "$b" ] || return 0
  # shellcheck disable=SC2086
  python3 -c "import sys; print(' '.join('%.1f' % (int(v) / 3) for v in sys.argv[1:]))" $b
}
# One number of a node's box in epx: l t r b w h cx cy.
edim() { # dump id which
  local b; b="$(B "$1" "$2")"; [ -n "$b" ] || return 0
  # shellcheck disable=SC2086
  python3 -c "
import sys
l, t, r, b = [int(v) / 3 for v in sys.argv[2:6]]
print('%.1f' % {'l': l, 't': t, 'r': r, 'b': b, 'w': r - l, 'h': b - t, 'cx': (l + r) / 2, 'cy': (t + b) / 2}[sys.argv[1]])" "$3" $b
}
hold() { # dump id [ms] — a press on the node's centre (900 ms: past the 700-ms hold)
  local b ms="${3:-900}"; b="$(B "$1" "$2")"
  [ -n "$b" ] || { echo "hold: no node $2" >&2; return 2; }
  # shellcheck disable=SC2086
  set -- $b
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) "$ms"; sleep 0.8
}
files_open() { adb shell am start -W -n "$FILES_ACTIVITY" "$@" >/dev/null 2>&1; sleep 1.5; }
files_at() { files_open --es path "$1"; }

# ---- pixels
px() { # shot x y -> r,g,b
  python3 - "$ROW_DIR/$1.png" "$2" "$3" <<'PY'
import sys
from PIL import Image
print("%d,%d,%d" % Image.open(sys.argv[1]).convert("RGB").getpixel((int(sys.argv[2]), int(sys.argv[3]))))
PY
}
assert_rgb() { # name want got tolerance
  local ok
  ok="$(python3 -c "
import sys
try:
    a = [int(v) for v in sys.argv[1].split(',')]; b = [int(v) for v in sys.argv[2].split(',')]
    print('yes' if len(a) == 3 and len(b) == 3 and all(abs(x - y) <= int(sys.argv[3]) for x, y in zip(a, b)) else 'no')
except Exception: print('no')" "$2" "$3" "$4" 2>/dev/null)"
  if [ "$ok" = yes ]; then _verdict PASS "$1" "($3) within ± $4 of ($2)"; else _verdict FAIL "$1" "expected ($2) ± $4 got ($3)"; fi
}
# The ink inside a box of a screencap (px): "left top right bottom r,g,b" — the pixels that differ from the box's own
# background (its top-left pixel) by more than 40 on some channel; r,g,b is the ink pixel farthest from the background.
ink() { # shot l t r b
  python3 - "$ROW_DIR/$1.png" "$2" "$3" "$4" "$5" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB")
l, t, r, b = [int(float(v)) for v in sys.argv[2:6]]
bg = im.getpixel((l, t))
xs, ys, best, bd = [], [], bg, 0
for y in range(t, b):
    for x in range(l, r):
        p = im.getpixel((x, y))
        d = max(abs(p[i] - bg[i]) for i in range(3))
        if d > 40:
            xs.append(x); ys.append(y)
            if d > bd: bd, best = d, p
print("%d %d %d %d %d,%d,%d" % ((min(xs), min(ys), max(xs), max(ys)) + best) if xs else "none")
PY
}
assert_near() { assert_within "$1" "$2" "$3" "$4"; }   # name want got tolerance (numbers)
assert_le() { # name limit actual
  if python3 -c "import sys; sys.exit(0 if float(sys.argv[2]) <= float(sys.argv[1]) else 1)" "$2" "$3" 2>/dev/null; then _verdict PASS "$1" "$3 <= $2"; else _verdict FAIL "$1" "expected <= $2 got [$3]"; fi
}
assert_ge() { # name limit actual
  if python3 -c "import sys; sys.exit(0 if float(sys.argv[2]) >= float(sys.argv[1]) else 1)" "$2" "$3" 2>/dev/null; then _verdict PASS "$1" "$3 >= $2"; else _verdict FAIL "$1" "expected >= $2 got [$3]"; fi
}

# ---- the ring
fring() { ring_since "$1" | grep -E "\[(files|motion|quick|wizard|checklist|music)\]"; }
# Wait until the slice from a MARK holds a needle (up to n × 0.25 s); prints the first line that holds it.
await_line() { # mark needle [tries]
  local i line=""
  for i in $(seq 1 "${3:-40}"); do
    line="$(ring_since "$1" | grep -F -- "$2" | head -1)"
    [ -n "$line" ] && break
    sleep 0.25
  done
  printf '%s\n' "$line"
}
# The LAST `[motion] <name> …` line of a slice, and one field of it (t0 peak overshoot settle frames maxGapMs first).
motion_line() { printf '%s\n' "$1" | grep -F "[motion] $2 " | tail -1 | sed 's/.*\[motion\]/[motion]/'; }
motion_field() { motion_line "$1" "$2" | tr ' ' '\n' | sed -n "s/^$3=//p" | head -1; }
assert_gap() { # name slice motion — C-31: maxGapMs <= 33.4
  assert_le "$1: maxGapMs <= 33.4 (C-31)" 33.4 "$(motion_field "$2" "$3" maxGapMs)"
}

# ---- logcat from a point in time (the device's clock): crashes and START lines
lc_mark() { adb shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
crash_since() { adb logcat -d -T "$1" -s AndroidRuntime:E 2>/dev/null | tr -d '\r' | grep -c 'app\.tileshell'; }
starts_since() { adb logcat -d -T "$1" -s ActivityTaskManager:I 2>/dev/null | tr -d '\r' | grep 'START u0'; }

# ---- dumpsys shortcut: app.tileshell's shortcuts, one per line: "<id> rank=<n> flags=[…] <activity>"
shortcuts() {
  adb shell dumpsys shortcut | tr -d '\r' | python3 -c '
import re, sys
t = sys.stdin.read()
m = re.search(r"Package: app\.tileshell\b.*?(?=\n\s*Package: |\Z)", t, re.S)
for b in re.split(r"\n\s*ShortcutInfo \{", m.group(0) if m else "")[1:]:
    i = re.search(r"id=([^,]+),", b); r = re.search(r"rank=(\d+)", b); f = re.search(r"flags=0x[0-9a-f]+ \[([^\]]*)\]", b); a = re.search(r"activity=ComponentInfo\{([^}]*)\}", b)
    print(i.group(1), "rank=" + (r.group(1) if r else "?"), "flags=[" + (f.group(1) if f else "?") + "]", a.group(1) if a else "")
'
}
# ---- a tile's burst: Start, the tile scrolled to, a 1-s hold, the dump <name>.xml and screencap; BMARK = the MARK
# taken just before the hold.
BMARK=""
burst() { # tile-id name
  local b
  ensure_start >/dev/null
  scroll_to_node "$ROW_DIR/$2-rest.xml" "$1" >/dev/null || dump_ui "$ROW_DIR/$2-rest.xml"
  b="$(bounds "$ROW_DIR/$2-rest.xml" "$1")"
  [ -n "$b" ] || { _verdict FAIL "burst: the tile $1 is on Start" "no such node ($2-rest.xml)"; return 1; }
  # shellcheck disable=SC2086
  set -- $b "$2"
  BMARK="$(ring_mark)"
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000; sleep 1
  dump_ui "$ROW_DIR/$5.xml"; screencap "$ROW_DIR/$5.png"
}
sat_labels() { local i; for i in 0 1 2 3 4; do [ "$(H "$1" "quick_sat_label:$i")" = yes ] && printf '%s|' "$(X "$1" "quick_sat_label:$i")"; done; echo; }
sat_count() { grep -o 'resource-id="quick_sat:[0-9]*"' "$ROW_DIR/$1.xml" | wc -l | xargs; }
qline() { ring_since "$1" | grep -o '\[quick\] shortcuts for .*' | tail -1; }

# ---- the end of a row: the state the brief names, asserted
end_state() {
  assert_eq "end: the installed APK is the gate candidate (md5 of base.apk = the built APK's)" "$(md5sum "$APK" | cut -c1-16)" "$(installed_apk_id)"
  assert_contains "end: appop MANAGE_EXTERNAL_STORAGE allow" "MANAGE_EXTERNAL_STORAGE: allow" "$(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE')"
  assert_eq "end: no disk and no public volume" "" "$(q 'sm list-disks' | xargs)$(q 'sm list-volumes public' | xargs)"
  assert_eq "end: no QA-Files, QA-Big or .Tessera on /sdcard" "" "$(q "ls -d $QA_FILES $QA_BIG /sdcard/.Tessera 2>/dev/null" | xargs)"
  assert_eq "end: no pace pref" "" "$(pace_now)$(search_pace_now)"
  assert_eq "end: Start is on top" "app.tileshell/.StartActivity" "$(top_activity)"
}

# ---- a whole list, read through scrolling: the dumps <name>-<k>.xml, the names in order in <name>.order and
# "name|detail" lines in <name>.details. The list is left scrolled; a sort change or a folder change puts it back on top.
list_all() { # name [prefix] [detail-prefix]
  local name="$1" pre="${2:-files_row:}" dpre="${3:-files_detail:}" k=0 before after
  : > "$ROW_DIR/$name.order"; : > "$ROW_DIR/$name.details"
  while [ "$k" -lt 12 ]; do
    dump_ui "$ROW_DIR/$name-$k.xml"
    before="$(grep -c . "$ROW_DIR/$name.order")"
    python3 - "$ROW_DIR/$name-$k.xml" "$pre" "$dpre" "$ROW_DIR/$name.order" "$ROW_DIR/$name.details" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
pre, dpre = sys.argv[2], sys.argv[3]
seen = [l.rstrip("\n") for l in open(sys.argv[4])]
det = dict(l.rstrip("\n").split("|", 1) for l in open(sys.argv[5]) if "|" in l)
for m in re.finditer(r'<node[^>]*>', xml):
    s = m.group(0)
    rid = html.unescape(re.search(r'resource-id="([^"]*)"', s).group(1))
    text = html.unescape(re.search(r' text="([^"]*)"', s).group(1))
    if rid.startswith(pre) and rid[len(pre):] not in seen: seen.append(rid[len(pre):])
    if rid.startswith(dpre): det[rid[len(dpre):]] = text
open(sys.argv[4], "w").write("".join(n + "\n" for n in seen))
open(sys.argv[5], "w").write("".join("%s|%s\n" % (n, det[n]) for n in seen if n in det))
PY
    after="$(grep -c . "$ROW_DIR/$name.order")"
    [ "$k" -gt 0 ] && [ "$after" = "$before" ] && break
    adb shell input swipe 540 1700 540 1100 700; sleep 1
    k=$((k + 1))
  done
}
detail_of() { awk -F'|' -v n="$2" '$1 == n { print $2 }' "$ROW_DIR/$1.details"; }

# ---- the accent: the theme pref's when one is set, else the out-of-box one (Palette.DEFAULT_ACCENT, read from the
# source); "r,g,b". mix_rgb <a> <b> <share of a> -> the two blended.
accent_rgb() {
  local pref def
  pref="$(_pace_file | tr -d '\r' | sed -n 's/.*name="accent"[^0-9-]*\(-\?[0-9]*\).*/\1/p' | head -1)"
  def="$(sed -n 's/.*const val DEFAULT_ACCENT = 0xFF\([0-9A-Fa-f]\{6\}\).*/\1/p' "$REPO/app/src/main/kotlin/app/tileshell/ui/tokens/Palette.kt" | head -1)"
  python3 -c "
import sys
v = int(sys.argv[1]) & 0xFFFFFF if sys.argv[1] else int(sys.argv[2], 16)
print('%d,%d,%d' % (v >> 16, (v >> 8) & 255, v & 255))" "$pref" "$def"
}
mix_rgb() { python3 -c "
import sys
a = [int(v) for v in sys.argv[1].split(',')]; b = [int(v) for v in sys.argv[2].split(',')]; k = float(sys.argv[3])
print(','.join(str(round(k * x + (1 - k) * y)) for x, y in zip(a, b)))" "$1" "$2" "$3"; }
# The ink of a node's box (the whole box, or a slice of it: from <dx0> to <dx1> px from its left): "l t r b r,g,b".
node_ink() { # shot dump id [dx0 dx1]
  local b; b="$(B "$2" "$3")"; [ -n "$b" ] || { echo none; return; }
  # shellcheck disable=SC2086
  set -- "$1" $b "${4:-}" "${5:-}"
  if [ -n "$6" ]; then ink "$1" $(( $2 + $6 )) "$3" $(( $2 + $7 )) "$5"; else ink "$1" "$2" "$3" "$4" "$5"; fi
}
# One number out of an ink line, in epx: l t r b w h cx cy.
ink_dim() { # "l t r b rgb" which
  python3 -c "
import sys
p = sys.argv[1].split()
if len(p) < 5: print(''); sys.exit()
l, t, r, b = [int(v) for v in p[:4]]; r += 1; b += 1
print('%.1f' % ({'l': l, 't': t, 'r': r, 'b': b, 'w': r - l, 'h': b - t, 'cx': (l + r) / 2, 'cy': (t + b) / 2}[sys.argv[2]] / 3))" "$1" "$2"
}
ink_rgb() { printf '%s\n' "${1##* }"; }
