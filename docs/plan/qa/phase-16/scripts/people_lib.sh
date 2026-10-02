#!/usr/bin/env bash
# Phase 16 QA — the People rows' include (the QA row-driver writer for E10–E16, E20, E25, E27, E28 and the EDGE
# sub-steps P01–P10). Sourced AFTER lib.sh and p16.sh:
#
#   . "$HERE/lib.sh"; . "$HERE/p16.sh"; . "$HERE/people_lib.sh"; row_begin E10 "…"; require_build; …; row_end
#
# Everything here drives the real shell on the emulator and reads back from the provider, the device or the drawn
# pixels. Nothing is simulated; no microphone, no host audio. The helpers only NAVIGATE and MEASURE: what a row asserts
# is written in its own driver from the phase doc's row text.
PEOPLE_PKG_ACTIVITY="app.tileshell/.people.PeopleActivity"
PL_FIX="$P16/fixtures"                                  # solid-{red,green,blue}.jpg, TestDPC (fixtures/README.md)
PL_INK="$P16/../phase-15/scripts/ink.py"                # phase 15's ink boxes off a screencap, read where it lives
PXE=3                                                   # the AVD: 1080 px across a 360-epx canvas
CONTACTS=content://com.android.contacts/contacts
AGGEX=content://com.android.contacts/aggregation_exceptions
GROUPS_URI=content://com.android.contacts/groups

# ---------------------------------------------------------------- the build under test
# A row whose header reads `apk match NO` proves nothing about this build (the device is shared and another build may
# have been left on it). The row then installs the lead's APK inside its lock, FAILS itself and stops: it is run again.
require_build() {
  local m; m="$(apk_matches)"
  if [ "${m%% *}" = yes ]; then
    _verdict PASS "the device holds the build under test" "$m"
    return 0
  fi
  note "the device held another build ($m); installing $APK"
  adb install -r "$APK" 2>&1 | tail -1 | tr -d '\r' >> "$LOG"
  _verdict FAIL "the device holds the build under test" "$m — the lead's APK was installed; run the row again"
  row_end
  exit 4
}

# ---------------------------------------------------------------- small readers
bfield() { echo "$1" | cut -d' ' -f"$2"; }
epx() { python3 -c "import sys; print('%.2f' % (float(sys.argv[1]) / $PXE))" "$1"; }
status_bottom() { bfield "$(bounds "$1" w10m_status_bar)" 4; }      # C-17: the DRAWN bar, read from the dump
nav_top() { bfield "$(bounds "$1" w10m_nav_bar)" 2; }
node_tag() { # dump.xml resource-id -> the whole <node …> tag (first match)
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for m in re.finditer(r'<node[^>]*>', xml):
    if 'resource-id="%s"' % sys.argv[2] in m.group(0):
        print(m.group(0)); break
PY
}
node_attr() { # dump.xml resource-id attribute
  node_tag "$1" "$2" | sed -n "s/.* $3=\"\([^\"]*\)\".*/\1/p"
}
ids_with_prefix() { # dump.xml prefix -> every resource-id starting with it, one per line, in dump order
  grep -o "resource-id=\"$2[^\"]*\"" "$1" | sed 's/^resource-id="//; s/"$//'
}
count_ids() { ids_with_prefix "$1" "$2" | grep -c . ; }
xml_text() { # dump.xml resource-id -> its text, parsed as XML (entities unescaped)
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1]).getroot()
except Exception: print(""); sys.exit(0)
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        print(n.get("text", "")); break
else: print("")
PY
}
# The shell's accent as "r,g,b": start_theme.xml's `accent` (an ARGB int) or ShellSettings' default when the key is
# absent (a provisioned install writes none) — read, never assumed.
accent_rgb() {
  adb shell run-as app.tileshell cat shared_prefs/start_theme.xml 2>/dev/null | tr -d '\r' | python3 -c '
import re, sys
s = sys.stdin.read(); m = re.search(r"name=\"accent\"[^>]*value=\"(-?\d+)\"", s)
v = int(m.group(1)) & 0xFFFFFFFF if m else 4278221015
print("%d,%d,%d" % ((v >> 16) & 255, (v >> 8) & 255, v & 255))'
}

# Verdict lines printed by a measuring script ("PASS|name|detail", "FAIL|…", "RECORD|name|value", "NOTE|text") become
# the row's verdicts. The script ends with "END|<n>": a script that died half-way is a FAIL, never a silent pass.
# Use as:  emit_verdicts "what" < <(python3 …)
emit_verdicts() { # label
  local kind name detail ended=no
  while IFS='|' read -r kind name detail; do
    case "$kind" in
      PASS|FAIL) _verdict "$kind" "$name" "$detail" ;;
      RECORD) record "$name" "$detail" ;;
      NOTE) note "$name $detail" ;;
      END) ended=yes ;;
    esac
  done
  [ "$ended" = yes ] || _verdict FAIL "${1:-measurement} ran to its end" "the measuring script printed no END line (it died; see the row's stderr)"
}

# ---------------------------------------------------------------- pixels
px() { python3 -c "
from PIL import Image; import sys
print('%d,%d,%d' % Image.open(sys.argv[1]).convert('RGB').getpixel((int(sys.argv[2]), int(sys.argv[3])))[:3])" "$1" "$2" "$3"; }
# The colour furthest from the fill inside a rectangle (the ink's own colour; anti-aliased edges sit in between).
ink_color() { # png l t r b "fill r,g,b"
  python3 - "$@" <<'PY'
import sys
import numpy as np
from PIL import Image
png, l, t, r, b = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
fill = np.array([int(v) for v in sys.argv[6].split(',')])
a = np.asarray(Image.open(png).convert('RGB').crop((l, t, r, b))).astype(int).reshape(-1, 3)
if len(a) == 0: print(''); sys.exit(0)
far = np.abs(a - fill).sum(axis=1)
print('%d,%d,%d' % tuple(a[int(far.argmax())]))
PY
}
assert_color() { # name "expected r,g,b" "actual r,g,b" tolerance
  local ok
  ok="$(python3 -c "
import sys
try:
    e=[int(v) for v in sys.argv[1].split(',')]; a=[int(v) for v in sys.argv[2].split(',')]
    print('yes' if len(a)==3 and all(abs(e[i]-a[i])<=int(sys.argv[3]) for i in range(3)) else 'no')
except Exception: print('no')" "$2" "$3" "$4")"
  if [ "$ok" = yes ]; then _verdict PASS "$1" "($3) within $4 of ($2)"; else _verdict FAIL "$1" "expected ($2) ± $4, got ($3)"; fi
}
centre_px() { # png "l t r b" -> the centre pixel's "r,g,b"
  # shellcheck disable=SC2086
  set -- "$1" $2
  px "$1" $(( ($2 + $4) / 2 )) $(( ($3 + $5) / 2 ))
}

# ---------------------------------------------------------------- driving People
open_people() { adb shell am start -W -n "$PEOPLE_PKG_ACTIVITY" "$@" >/dev/null 2>&1; sleep 2; }
back() { adb shell input keyevent KEYCODE_BACK; sleep "${1:-1}"; }
tapid() { # resource-id [settle]  — a fresh dump, then the node's centre
  local d="$ROW_DIR/.tap.xml"
  dump_ui "$d" || true
  tap_node "$d" "$1" || return 2
  sleep "${2:-2}"
}
type_text() { adb shell input text "$(printf '%s' "$1" | sed 's/ /%s/g')"; sleep 1; }
# Replace a field's text: tap it, delete what it holds (counted from the dump), type, close the keyboard.
set_field() { # resource-id text
  local d="$ROW_DIR/.field.xml" n
  dump_ui "$d" || true
  n="$(xml_text "$d" "$1")"; n="${#n}"
  tap_node "$d" "$1" || return 2
  sleep 1
  adb shell input keyevent KEYCODE_MOVE_END
  # shellcheck disable=SC2046
  if [ "$n" -gt 0 ]; then adb shell input keyevent $(printf '67 %.0s' $(seq 1 "$n")); fi
  type_text "$2"
  adb shell input keyevent KEYCODE_BACK   # the keyboard; the page stays
  sleep 1
}
wait_node() { # out.xml resource-id [seconds] — dump until the node is there
  local out="$1" id="$2" n="${3:-8}" i
  for i in $(seq 1 "$n"); do
    dump_ui "$out" || true
    [ "$(has_node "$out" "$id")" = yes ] && return 0
    sleep 1
  done
  return 1
}
# The last `[people] list: n contacts …` line since a mark (without its time stamps), and its n.
people_list_line() { ring_since "$1" | grep -F '[people] list:' | tail -1 | sed 's/.*\[people\]/[people]/'; }
people_list_n() { people_list_line "$1" | sed -E 's/.*list: ([0-9]+) contacts.*/\1/'; }
# A contact's card by an explicit VIEW on the component (no chooser on a shell path), dumped.
card_of() { # raw-id out.xml
  adb shell am start -W -n "$PEOPLE_PKG_ACTIVITY" -a android.intent.action.VIEW -d "$CONTACTS/$(contact_of "$1")" >/dev/null 2>&1
  sleep 2; dump_ui "$2"
}
# People's settings page, then one of its three pages (can_edit | filter | sim), dumped.
open_people_settings() { # page out.xml
  open_people -a android.intent.action.MAIN
  tapid people_more 2 || return 2
  tapid people_more:settings 2 || return 2
  tapid "people_settings:$1" 2 || return 2
  dump_ui "$2"
}
people_data() { # raw mimetype data1 [data2 int]
  if [ -n "${4:-}" ]; then
    q "content insert --uri $DATA --bind raw_contact_id:i:$1 --bind mimetype:s:$2 --bind data1:s:'$3' --bind data2:i:$4" >/dev/null
  else
    q "content insert --uri $DATA --bind raw_contact_id:i:$1 --bind mimetype:s:$2 --bind data1:s:'$3'" >/dev/null
  fi
}
raw_row() { q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted --where \"_id=$1\""; }
data_rows() { q "content query --uri $DATA --projection _id:raw_contact_id:mimetype:data1 --where \"raw_contact_id=$1\""; }
contacts_count() { q "content query --uri $CONTACTS --projection _id" | grep -c '_id='; }
people_edit_json() { adb shell run-as app.tileshell cat files/people_edit.json 2>/dev/null | tr -d '\r'; }
people_allowed() { # "type:name,type:name" of people_edit.json's allowed list ("" when none, or no file)
  people_edit_json | python3 -c '
import json, sys
try: d = json.load(sys.stdin)
except Exception: d = {}
print(",".join(sorted(a["type"] + ":" + a["name"] for a in d.get("allowed", []))))'
}
exceptions() { q "content query --uri $AGGEX --projection type:raw_contact_id1:raw_contact_id2"; }
sms_holder() { adb shell cmd role get-role-holders android.app.role.SMS | tr -d '\r'; }

# ---------------------------------------------------------------- the list, walked
# The list is lazy: a dump holds only what is laid out. list_walk dumps the list from its top, swiping up until two
# dumps in a row add nothing, and prints the merged sequence — "letter<TAB><label>" and "row<TAB><lookup><TAB><name>"
# lines in list order (each id once). The dumps are kept as <prefix>-<i>.xml. It leaves the list scrolled to its end.
list_walk() { # prefix
  local prefix="$1" i=0 n=-1 m
  while [ "$i" -lt 14 ]; do
    dump_ui "$prefix-$i.xml" || true
    m="$(_list_merge "$prefix" "$i" | grep -c .)"
    if [ "$m" = "$n" ] && [ "$i" -gt 0 ]; then break; fi
    n="$m"; i=$((i + 1))
    adb shell input swipe 540 1700 540 900 600; sleep 1.5
  done
  _list_merge "$prefix" "$i"
}
_list_merge() { # prefix last-index
  python3 - "$1" "$2" <<'PY'
import html, os, re, sys
seen, out = set(), []
for i in range(int(sys.argv[2]) + 1):
    p = "%s-%d.xml" % (sys.argv[1], i)
    if not os.path.exists(p): continue
    xml = open(p, encoding='utf-8', errors='replace').read()
    names = {}
    for m in re.finditer(r'<node[^>]*>', xml):
        s = m.group(0)
        rid = re.search(r'resource-id="([^"]*)"', s).group(1)
        if rid.startswith("people_name:"):
            names[rid[len("people_name:"):]] = html.unescape(re.search(r'text="([^"]*)"', s).group(1))
    items = []
    for m in re.finditer(r'<node[^>]*>', xml):
        s = m.group(0)
        rid = re.search(r'resource-id="([^"]*)"', s).group(1)
        top = int(re.search(r'bounds="\[-?\d+,(-?\d+)\]', s).group(1))
        if rid.startswith("people_letter:"): items.append((top, "letter", rid[len("people_letter:"):], ""))
        elif rid.startswith("people_row:"): items.append((top, "row", rid[len("people_row:"):], names.get(rid[len("people_row:"):], "")))
    for top, kind, key, name in sorted(items):
        if (kind, key) in seen: continue
        seen.add((kind, key)); out.append("%s\t%s\t%s" % (kind, key, name))
print("\n".join(out))
PY
}
list_top() { local i; for i in 1 2 3 4 5 6; do adb shell input swipe 540 900 540 1900 250; sleep 0.6; done; sleep 1; }
# From a merged walk: the letter header a row files under.
header_of() { # merged.tsv lookup
  awk -F'\t' -v k="$2" '$1=="letter" {h=$2} $1=="row" && $2==k {print h; exit}' "$1"
}

# ---------------------------------------------------------------- the list's drawn geometry (E10; E20 "the list per E10")
# Measured on the DRAWN pixels of a screencap, the dump giving only where to look. Edges are half-level crossings
# (r11/people.md §0.2's method): a pixel is ink when it is at least half way from the fill to the ink's colour.
# Prints verdict lines (emit_verdicts). mode e10: the row's worded values. mode e20: those again, plus P1.6's letter
# cap top → first avatar top and the cited cap heights.
list_geometry() { # dump.xml png "accent r,g,b" mode
  python3 - "$@" <<'PY'
import html, re, sys
import numpy as np
from PIL import Image
dump, png, accent, mode = sys.argv[1], sys.argv[2], np.array([int(v) for v in sys.argv[3].split(',')]), sys.argv[4]
PX = 3.0
n_out = 0
def out(ok, name, detail):
    global n_out; n_out += 1
    print("%s|%s|%s" % ("PASS" if ok else "FAIL", name, detail))
def within(name, want, got_px, tol):
    got = got_px / PX
    out(abs(got - want) <= tol + 1e-9, name, "%.2f epx vs %.2f ± %s" % (got, want, tol))
xml = open(dump, encoding='utf-8', errors='replace').read()
img = np.asarray(Image.open(png).convert('RGB')).astype(np.int32)
nodes = []
for m in re.finditer(r'<node[^>]*>', xml):
    s = m.group(0)
    rid = re.search(r'resource-id="([^"]*)"', s).group(1)
    text = html.unescape(re.search(r'text="([^"]*)"', s).group(1))
    b = [int(v) for v in re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s).groups()]
    nodes.append((rid, text, b))
by = {rid: (text, b) for rid, text, b in nodes if rid}
if "people_list" not in by or "people_app_bar" not in by:
    out(False, "the list page is in the dump", "no people_list / people_app_bar node"); print("END|%d" % n_out); sys.exit(0)
top, bottom = by["people_list"][1][1], by["people_app_bar"][1][1]
def ink_box(l, t, r, b, ink, fill=(0, 0, 0)):
    """bbox (l, t, r, b exclusive) of the pixels at least half way from fill TOWARDS ink (the projection on the
    fill→ink direction, so a pixel that differs from the fill the other way — the black page beside a grey disc when
    the ink is white — is not ink), or None"""
    reg = img[t:b, l:r]
    fill = np.array(fill); v = np.array(ink) - fill
    vv = int((v * v).sum())
    if vv == 0: return None
    proj = ((reg - fill) * v).sum(axis=2)
    ys, xs = np.where(proj * 2 >= vv)
    if len(ys) == 0: return None
    return (int(l + xs.min()), int(t + ys.min()), int(l + xs.max() + 1), int(t + ys.max() + 1))
def far_colour(l, t, r, b, fill=(0, 0, 0)):
    reg = img[t:b, l:r].reshape(-1, 3)
    return reg[int(np.abs(reg - np.array(fill)).sum(axis=1).argmax())]
seq = []   # ("letter", label, bounds) / ("row", lookup, bounds), in dump order, fully visible only
for rid, text, b in nodes:
    if rid.startswith("people_letter:") or rid.startswith("people_row:"):
        if b[1] >= top and b[3] <= bottom:
            seq.append(("letter" if rid.startswith("people_letter:") else "row", rid.split(":", 1)[1], b))
rows = [x for x in seq if x[0] == "row"]
letters = [x for x in seq if x[0] == "letter"]
out(len(rows) >= 4, "the list's first screen holds rows to measure", "%d rows, %d letter headers fully on screen" % (len(rows), len(letters)))
# the avatar's child text node (the initial), by containment
def initial_in(avb):
    for rid, text, b in nodes:
        if not rid and text and b[0] >= avb[0] and b[1] >= avb[1] and b[2] <= avb[2] and b[3] <= avb[3]:
            return text
    return ""
avatars = {}
prev = None
for kind, key, b in seq:
    if kind == "letter":
        prev = None
        continue
    name = by.get("people_name:" + key, ("", None))[0]
    label = "row '%s'" % name
    # the row node itself (not a touch target: rows fill their pitch)
    within("%s: row height 50 epx (P1.10)" % label, 50, b[3] - b[1], 1)
    # the avatar as DRAWN: everything left of x 50 epx inside the row that is at least half as bright as the disc
    probe = img[(b[1] + b[3]) // 2, 36 + 12]          # 4 epx inside the avatar's left edge, at mid height
    disc = [int(v) for v in probe]
    box = ink_box(0, b[1], 150, b[3], disc) if sum(disc) > 60 else None
    if box is None:
        out(False, "%s: a drawn avatar disc left of the name" % label, "no disc found; pixel at x 16 epx mid-row is %s" % (disc,))
        prev = None; continue
    l, t, r, btm = box
    avatars[key] = box
    within("%s: avatar width 32 epx (P1.7)" % label, 32, r - l, 1)
    within("%s: avatar height 32 epx (P1.7)" % label, 32, btm - t, 1)
    within("%s: avatar left edge at x 12 (P1.7)" % label, 12, l, 1)
    # circular: the four corners of its box are the page, the four edge mid-points are the disc
    corners = [img[t + 1, l + 1], img[t + 1, r - 2], img[btm - 2, l + 1], img[btm - 2, r - 2]]
    mids = [img[(t + btm) // 2, l + 3], img[(t + btm) // 2, r - 4], img[t + 3, (l + r) // 2], img[btm - 4, (l + r) // 2]]
    circ = all(int(c.sum()) <= 30 for c in corners) and all(np.abs(m_ - np.array(disc)).max() <= 6 for m_ in mids)
    out(circ, "%s: the avatar is a circle (corners are the page, edge mid-points the disc)" % label,
        "corners %s mids %s" % ([tuple(int(v) for v in c) for c in corners], [tuple(int(v) for v in m_) for m_ in mids]))
    # no photo: a GREY disc (r = g = b, neither black nor white) with the initial drawn on it
    grey = max(disc) - min(disc) <= 3 and 40 <= disc[0] <= 200
    out(grey, "%s: no photo → a grey disc (P1.8)" % label, "disc colour (%d,%d,%d)" % tuple(disc))
    ini = initial_in(by.get("people_avatar:" + key, ("", [0, 0, 0, 0]))[1])
    glyph = ink_box(l + 6, t + 6, r - 6, btm - 6, (255, 255, 255), disc)
    out(bool(ini) and glyph is not None, "%s: the initial is drawn on the disc" % label, "initial node text [%s], bright ink box %s" % (ini, glyph))
    if name and name[0].isascii() and name[0].isalpha():
        out(ini == name[0].upper(), "%s: the initial is the name's first letter" % label, "[%s]" % ini)
    else:
        print("RECORD|%s: the initial drawn for a name with no Latin first letter|[%s]" % (label, ini))
    # the name's ink
    nb = by.get("people_name:" + key, ("", None))[1]
    if nb is None:
        out(False, "%s: a people_name node" % label, "none")
    else:
        nbox = ink_box(max(nb[0] - 6, r + 3), nb[1], min(nb[2] + 6, 1080), nb[3], (255, 255, 255))
        if nbox is None: out(False, "%s: the name's ink" % label, "no white ink in %s" % (nb,))
        else:
            within("%s: the name's left edge at x 57.75 (P1.9)" % label, 57.75, nbox[0], 1)
            if mode == "e20" and name and name[0].isascii() and name[0].isupper():
                # the first glyph's cap height: the ink rows of the first column run
                reg = img[nbox[1]:nbox[3], nbox[0]:nbox[2]]
                cols = (reg.sum(axis=2) * 2 >= 765).any(axis=0)
                end = 0
                while end < len(cols) and (cols[end] or (end + 1 < len(cols) and cols[end + 1])): end += 1
                g = ink_box(nbox[0], nb[1], nbox[0] + max(end, 1), nb[3], (255, 255, 255))
                print("RECORD|%s: the name's cap (P1.9 gives 12.75; an r11 value the row does not word)|%.2f epx (%s)" % (label, (g[3] - g[1]) / PX, "within 1 epx" if abs((g[3] - g[1]) / PX - 12.75) <= 1 else "DIFFERS"))
            # vertically centred on the avatar (P1.9)
    # pitch: avatar top to avatar top between consecutive rows of one group
    if prev is not None:
        within("pitch %s → the row above: 50 epx (P1.10)" % label, 50, t - prev[1], 1)
    prev = box
if not any(k == "row" and i > 0 and seq[i - 1][0] == "row" for i, (k, _, _) in enumerate(seq)):
    out(False, "two consecutive rows of one group on screen (the pitch needs a pair)", "none")
# letter headers
for i, (kind, key, b) in enumerate(seq):
    if kind != "letter": continue
    hb = ink_box(b[0] - 9, b[1], b[2] + 9, b[3], accent)
    col = far_colour(b[0], b[1], b[2], b[3])
    out(np.abs(col - accent).max() <= 4, "letter header '%s' is drawn in the accent" % key, "(%d,%d,%d) vs accent (%d,%d,%d) ± 4" % (tuple(col) + tuple(accent)))
    if hb is None:
        out(False, "letter header '%s': accent ink" % key, "none in %s" % (b,)); continue
    within("letter header '%s': ink left edge at x 14.75 (P1.5)" % key, 14.75, hb[0], 1)
    if mode == "e20" and len(key) == 1 and key.isascii() and key.isupper():
        print("RECORD|letter header '%s': cap (P1.5 gives 22.25; an r11 value the row does not word)|%.2f epx (%s)" % (key, (hb[3] - hb[1]) / PX, "within 1 epx" if abs((hb[3] - hb[1]) / PX - 22.25) <= 1 else "DIFFERS"))
        nxt = seq[i + 1] if i + 1 < len(seq) else None
        if nxt and nxt[0] == "row" and nxt[1] in avatars:
            within("letter '%s': cap top → its group's first avatar top 43.5 epx (P1.6)" % key, 43.5, avatars[nxt[1]][1] - hb[1], 0.5)
out(len(letters) >= 2, "letter headers on screen to measure", "%d" % len(letters))
print("END|%d" % n_out)
PY
}

# ---------------------------------------------------------------- photos (E11, E13)
# The three solid-colour JPEGs of qa/phase-16/fixtures, pushed where Android's photo picker lists them.
declare -A PHOTO_RGB=( [red]="254,0,0" [green]="0,255,1" [blue]="0,0,254" )     # their decoded centre pixels (README)
push_photo() { # colour -> the device path
  adb shell mkdir -p /sdcard/Pictures >/dev/null 2>&1
  adb push "$PL_FIX/solid-$1.jpg" "/sdcard/Pictures/qa-solid-$1.jpg" >/dev/null
  adb shell content call --uri content://media --method scan_file --arg "/sdcard/Pictures/qa-solid-$1.jpg" >/dev/null 2>&1
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
  sleep 2
  echo "/sdcard/Pictures/qa-solid-$1.jpg"
}
remove_photos() {
  adb shell rm -f /sdcard/Pictures/qa-solid-red.jpg /sdcard/Pictures/qa-solid-green.jpg /sdcard/Pictures/qa-solid-blue.jpg
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
  sleep 1
}
pushed_photos_left() { adb shell ls /sdcard/Pictures 2>/dev/null | tr -d '\r' | grep -c '^qa-solid-' ; }
# In Android's photo picker: the grid cell whose centre pixel is the colour asked for, then its confirmation if it asks.
pick_photo() { # "r,g,b"
  local d="$ROW_DIR/.picker.xml" p="$ROW_DIR/.picker.png" xy add
  sleep 2
  dump_ui "$d" || true; screencap "$p"
  xy="$(python3 - "$d" "$p" "$1" <<'PY'
import re, sys
from PIL import Image
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
im = Image.open(sys.argv[2]).convert('RGB')
want = [int(v) for v in sys.argv[3].split(',')]
for m in re.finditer(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    l, t, r, b = (int(v) for v in m.groups())
    w, h = r - l, b - t
    if not (150 <= w <= 600 and 150 <= h <= 600): continue
    cx, cy = (l + r) // 2, (t + b) // 2
    if cx >= im.width or cy >= im.height: continue
    p = im.getpixel((cx, cy))
    if all(abs(p[i] - want[i]) <= 12 for i in range(3)):
        print(cx, cy); break
PY
)"
  if [ -z "$xy" ]; then echo "pick_photo: no picker cell of colour $1" >&2; return 2; fi
  # shellcheck disable=SC2086
  adb shell input tap $xy
  sleep 3
  dump_ui "$d" || true
  add="$(python3 - "$d" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if re.search(r'text="(Add|Done|Select)[^"]*"', s) and 'app.tileshell' not in s:
        m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        l, t, r, b = (int(v) for v in m.groups()); print((l + r) // 2, (t + b) // 2); break
PY
)"
  # shellcheck disable=SC2086
  if [ -n "$add" ]; then adb shell input tap $add; sleep 3; fi
}
photo_rows() { # raw -> the number of vnd.android.cursor.item/photo data rows it has
  q "content query --uri $DATA --projection _id:mimetype --where \"raw_contact_id=$1 AND mimetype='vnd.android.cursor.item/photo'\"" | grep -c 'mimetype=vnd.android.cursor.item/photo'
}

# ---------------------------------------------------------------- calls (E12)
# Telecom's own record of the calls it was handed: each is a "CallTC@<n> [<time>](MO - outgoing)" entry in dumpsys.
outgoing_calls() { adb shell dumpsys telecom | tr -d '\r' | grep -cE 'CallTC@[0-9]+ \[.*\]\(MO - outgoing\)'; }
call_is_up() { adb shell dumpsys telecom | tr -d '\r' | grep -q 'mForegroundCall: \[Call'; }
# The END CALL key only while a call is up: with none it puts the screen to sleep (the builder's DEV-E12 run 2).
end_call() {
  if call_is_up; then adb shell input keyevent KEYCODE_ENDCALL; sleep 2; fi
  adb emu gsm cancel 5551230001 >/dev/null 2>&1 || true
}

# ---------------------------------------------------------------- permissions
perm_ensure() { # short-name...  (a restore: grant, and say what it read)
  local p
  for p in "$@"; do adb shell pm grant app.tileshell "android.permission.$p" >/dev/null 2>&1; done
}

# ---------------------------------------------------------------- the two checklists (E13)
# Settings > Setup checklist in a fresh task (phase 12's open_checklist: a plain start only fronts a running task),
# scrolled until a `checklist:people:*` row is laid out; the dump is left in the file given. Prints the people row's
# state tag(s) found ("checklist:people:partial").
setup_people_row() { # out.xml
  local out="$1" i
  adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-clear-task --es page CHECKLIST >/dev/null 2>&1
  sleep 3
  for i in 0 1 2 3 4 5 6 7 8 9; do
    dump_ui "$out" || true
    if grep -q 'resource-id="checklist:people:' "$out"; then break; fi
    adb shell input swipe 540 1700 540 800 320; sleep 1
  done
  ids_with_prefix "$out" checklist:people: | tr '\n' ' ' | sed 's/ $//'
}
# Tess's settings page through her session's menu (phase 12's open_tess_settings; e26.sh reaches it the same way),
# scrolled until a `cortana_check:contacts:*` row is laid out. Prints the tag(s) found.
tess_contacts_row() { # out.xml
  local out="$1" i
  adb shell input keyevent KEYCODE_ASSIST; sleep 3
  dump_ui "$out.home.xml" || true
  tap_node "$out.home.xml" cortana_menu_button || { echo "(no cortana_menu_button)"; return 1; }
  sleep 1.5
  dump_ui "$out.pane.xml" || true
  tap_node "$out.pane.xml" cortana_pane_item_settings || { echo "(no cortana_pane_item_settings)"; return 1; }
  sleep 2
  for i in 0 1 2 3 4 5 6 7 8 9; do
    dump_ui "$out" || true
    if grep -q 'resource-id="cortana_check:contacts:' "$out"; then break; fi
    adb shell input swipe 540 1700 540 800 320; sleep 1
  done
  ids_with_prefix "$out" cortana_check:contacts: | tr '\n' ' ' | sed 's/ $//'
}

# Tap a row of a (lazy) People list by its id: the row must be laid out AND clear of the app bar that overlays the
# list's bottom (a node under the bar is in the dump, and a tap at its centre would land on the bar). Swipes up until
# it is. Leaves the dump it tapped from in the file given.
tap_row_clear() { # resource-id out.xml [max-swipes]
  local id="$1" out="$2" max="${3:-10}" i=0 b bar cy
  while [ "$i" -le "$max" ]; do
    dump_ui "$out" || true
    b="$(bounds "$out" "$id")"; bar="$(bfield "$(bounds "$out" people_app_bar)" 2)"
    if [ -n "$b" ]; then
      cy=$(( ($(bfield "$b" 2) + $(bfield "$b" 4)) / 2 ))
      if [ -z "$bar" ] || [ "$cy" -lt $(( bar - 12 )) ]; then
        tap_node "$out" "$id"; sleep 2; return 0
      fi
    fi
    adb shell input swipe 540 1500 540 1000 500; sleep 1.2
    i=$((i + 1))
  done
  echo "tap_row_clear: $id never came clear of the app bar" >&2
  return 2
}
# The lead's last unit-test result for one class (the QA writers never run ./gradlew): "tests=<n> failures=<n>
# errors=<n> skipped=<n> at <file mtime>", empty when there is no result file.
jvm_result() { # fully-qualified test class
  local xml="$REPO/app/build/test-results/testDebugUnitTest/TEST-$1.xml"
  [ -f "$xml" ] || return 0
  echo "$(grep -o '<testsuite[^>]*>' "$xml" | sed -E 's/.*tests="([0-9]+)" skipped="([0-9]+)" failures="([0-9]+)" errors="([0-9]+)".*/tests=\1 failures=\3 errors=\4 skipped=\2/') at $(date -r "$xml" '+%F %T')"
}
jvm_cases() { grep -o 'testcase name="[^"]*"' "$REPO/app/build/test-results/testDebugUnitTest/TEST-$1.xml" 2>/dev/null | sed 's/testcase name=/  /'; }
# yes when the result file is newer than every file named (the test's source, the code it tests).
jvm_fresh() { # class file...
  local xml="$REPO/app/build/test-results/testDebugUnitTest/TEST-$1.xml" f; shift
  [ -f "$xml" ] || { echo no; return; }
  for f in "$@"; do [ "$xml" -nt "$f" ] || { echo "no ($f is newer)"; return; }; done
  echo yes
}

# ---------------------------------------------------------------- the People tile (E11, E25, EDGE P10)
PEOPLE_SLOT_TILE="tile:slot:PEOPLE"
PEOPLE_PIN_KEY="app:app.tileshell/app.tileshell.people.PeopleActivity:0"
# A photo given the way a user gives one (E13's steps): the card → Edit → the photo → Android's photo picker → the
# pushed solid-colour JPEG → Save. Asserts the picker and the photo data row.
give_photo() { # raw colour label
  local raw="$1" colour="$2" label="$3" d="$ROW_DIR/photo-$2"
  card_of "$raw" "$d-card.xml"
  tap_node "$d-card.xml" people_card_edit; sleep 2
  dump_ui "$d-edit.xml"; tap_node "$d-edit.xml" people_editor_photo; sleep 3
  assert_contains "$label: Android's photo picker opened" "photopicker" "$(top_activity | tr 'A-Z' 'a-z')"
  pick_photo "${PHOTO_RGB[$colour]}"
  dump_ui "$d-picked.xml"; tap_node "$d-picked.xml" people_editor_save; sleep 4
  assert_eq "$label: a vnd.android.cursor.item/photo data row exists (the $colour JPEG, given through the editor)" "1" "$(photo_rows "$raw")"
}
# The first node with a given resource-id INSIDE another node's subtree: its bounds and content-desc ("l t r b|desc").
node_inside() { # dump.xml outer-id inner-id
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1]).getroot()
except Exception: sys.exit(0)
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        for c in n.iter("node"):
            if c.get("resource-id") == sys.argv[3]:
                print(" ".join(re.findall(r"-?\d+", c.get("bounds", ""))) + "|" + c.get("content-desc", "")); sys.exit(0)
PY
}
# The tile-event lines of a slice, graded (R3 A9 with RV11's tolerance + one frame; C-31). Prints verdict lines.
#   every event of the window is whole (the caller waits for the last in-slide before slicing)
tile_times() { # slice-file min-events "lookup lookup …" (the lookups a line may name)
  python3 - "$@" <<'PY'
import re, sys
FRAME = 16.7
lines = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
need = int(sys.argv[2]); allowed = set(sys.argv[3].split())
events, outs, ins = [], [], []
for l in lines:
    m = re.search(r"\[people\] tile event (\d+) t0=(\d+) lookup=(\S+)", l)
    if m: events.append((int(m.group(1)), int(m.group(2)), m.group(3)))
    m = re.search(r"\[motion\] people_bubble_(out|in) t0=(\d+) peak=(\d+) overshoot=(\d+) settle=(\d+) frames=(\d+) maxGapMs=(\d+(?:\.\d+)?)", l)
    if m:
        rec = dict(t0=int(m.group(2)), settle=int(m.group(5)), frames=int(m.group(6)), gap=float(m.group(7)))
        (outs if m.group(1) == "out" else ins).append(rec)
n = 0
def v(ok, name, detail):
    global n; n += 1
    print("%s|%s|%s" % ("PASS" if ok else "FAIL", name, detail))
v(len(events) >= need, "the ring holds at least %d `[people] tile event <n> t0=<uptime> lookup=<key>` lines" % need, "%d events: %s" % (len(events), [e[0] for e in events]))
nums = [e[0] for e in events]
v(nums == list(range(nums[0], nums[0] + len(nums))) if nums else False, "the events are numbered consecutively", str(nums))
gaps = [events[i + 1][1] - events[i][1] for i in range(len(events) - 1)]
v(bool(gaps) and all(abs(g - 7700) <= 200 + FRAME for g in gaps), "consecutive events are 7.7 s apart (± 0.2 s + one frame)", "t0 differences ms: %s" % gaps)
v(len({e[2] for e in events}) >= 2, "at least two different lookups appear", str(sorted({e[2] for e in events})))
v(all(e[2] in allowed for e in events), "every event names one of the three fixtures", str([e[2] for e in events]))
for num, t0, lookup in events:
    o = next((x for x in outs if 0 <= x["t0"] - t0 <= 100), None)
    i = next((x for x in ins if 0 < x["t0"] - t0 <= 2500), None) if o else None
    if not o or not i:
        v(False, "event %d has its `[motion] people_bubble_out` and `people_bubble_in` lines" % num, "out=%s in=%s" % (o, i)); continue
    v(abs(o["settle"] - 333) <= 42 + FRAME, "event %d: people_bubble_out settle ≈ 333 ms (± 42 ms + one frame)" % num, "%d ms over %d frames" % (o["settle"], o["frames"]))
    v(abs(i["settle"] - 583) <= 42 + FRAME, "event %d: people_bubble_in settle ≈ 583 ms (± 42 ms + one frame)" % num, "%d ms over %d frames" % (i["settle"], i["frames"]))
    whole = i["t0"] + i["settle"] - o["t0"]
    v(abs(whole - 1880) <= 40 + FRAME, "event %d: 1.88 s from the out's t0 to the in's settle (± 0.04 s + one frame)" % num, "%d ms" % whole)
    v(o["gap"] <= 33.4, "event %d: people_bubble_out maxGapMs ≤ 33.4 (C-31)" % num, "%s" % o["gap"])
    v(i["gap"] <= 33.4, "event %d: people_bubble_in maxGapMs ≤ 33.4 (C-31)" % num, "%s" % i["gap"])
print("END|%d" % n)
PY
}
# A screenrecord's motion bursts inside a region (phase 13's motion_frames.py method, for many motions in one capture):
# every SOURCE frame with its presentation time; a frame moves when its region differs from the frame before by more
# than the threshold; moving frames more than 100 ms apart are separate bursts. One line per burst:
#   "<start s> <window ms> <frames> <max source-frame gap ms inside the window>"
record_bursts() { # mp4 "x0 y0 x1 y1" (capture pixels) [threshold]
  python3 - "$@" <<'PY'
import subprocess, sys
import numpy as np
mp4 = sys.argv[1]; x0, y0, x1, y1 = (int(v) for v in sys.argv[2].split()); thr = float(sys.argv[3]) if len(sys.argv) > 3 else 0.2
w, h = x1 - x0, y1 - y0
pts = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "frame=best_effort_timestamp_time",
                      "-of", "csv=p=0", mp4], capture_output=True, text=True, check=True).stdout.split()
pts = [float(p.strip(",")) for p in pts if p.strip(",")]
p = subprocess.Popen(["ffmpeg", "-v", "error", "-i", mp4, "-vsync", "passthrough", "-vf", "crop=%d:%d:%d:%d,format=gray" % (w, h, x0, y0),
                      "-f", "rawvideo", "-"], stdout=subprocess.PIPE)
prev = None; moving = []; i = 0
while True:
    buf = p.stdout.read(w * h)
    if len(buf) < w * h: break
    a = np.frombuffer(buf, dtype=np.uint8).astype(np.float32)
    if prev is not None and float(np.abs(a - prev).mean()) > thr: moving.append(i)
    prev = a; i += 1
p.wait()
print("# frames=%d pts=%d moving=%d" % (i, len(pts), len(moving)))
moving = [m for m in moving if m < len(pts)]
bursts = []
for m in moving:
    if bursts and (pts[m] - pts[bursts[-1][-1]]) * 1000 <= 100: bursts[-1].append(m)
    else: bursts.append([m])
for b in bursts:
    gaps = [(pts[k] - pts[k - 1]) * 1000 for k in range(b[0] + 1, b[-1] + 1)]
    print("%.3f %.1f %d %.1f" % (pts[b[0]], (pts[b[-1]] - pts[b[0]]) * 1000, len(b), max(gaps) if gaps else 0))
PY
}
# Phase 02's Pin to Start from the app list (the hold menu's applist_menu_pin), for the shell's People entry.
pin_people_from_app_list() { # dump-prefix
  local p="$1" i h b prevb=""
  ensure_start
  adb shell input swipe 900 1200 150 1200 250; sleep 2          # to the app list
  for i in 1 2 3 4 5; do
    dump_ui "$p-nav.xml"
    h="$(grep -o 'resource-id="applist_header:[^"]*"' "$p-nav.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
    [ -n "$h" ] && break
    adb shell input swipe 540 800 540 1700 300; sleep 1
  done
  tap_node "$p-nav.xml" "$h"; sleep 1.5
  dump_ui "$p-grid.xml"; tap_node "$p-grid.xml" "jump_cell:P"; sleep 1.5
  scroll_to_node "$p-applist.xml" "applist_name:$PEOPLE_ACTIVITY" 4 >/dev/null 2>&1 || true
  for i in 1 2 3 4 5 6; do
    b="$(bounds "$p-applist.xml" "applist_name:$PEOPLE_ACTIVITY")"
    [ -n "$b" ] && [ "$b" = "$prevb" ] && break
    prevb="$b"; sleep 0.7; dump_ui "$p-applist.xml"
  done
  [ -n "$b" ] || return 2
  # shellcheck disable=SC2086
  set -- $b
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000; sleep 1.5
  dump_ui "$p-menu.xml"
  tap_node "$p-menu.xml" applist_menu_pin || return 2
  sleep 2.5
  ensure_start
}

# ---------------------------------------------------------------- page geometry on the drawn pixels (E20)
# people_geo <kind> <dump.xml> <screencap.png> "<accent r,g,b>" — prints verdict lines (emit_verdicts). The dump only
# says WHERE to look (a tappable node's bounds are Android's 48-epx touch target, not the drawn box); every length is
# read off the screencap at half-level crossings and given in epx (1080 px = 360 epx), relative to the DRAWN status
# bar's bottom edge where r11/people.md measured under its 24-epx bar (C-17). Values are r11/people.md's; a value the
# row words is graded (± 1 epx unless the row says otherwise), a value r11 gives that the row does not word is RECORDed.
#   kinds: pivots (pivot header + search box, on the list)   grid (the jump grid)   card   editor   appbar   bars
people_geo() { # kind dump png accent [extra]
  python3 - "$@" <<'PY'
import html, re, sys
import numpy as np
from PIL import Image
kind, dump, png = sys.argv[1], sys.argv[2], sys.argv[3]
ACC = np.array([int(v) for v in sys.argv[4].split(',')])
extra = sys.argv[5] if len(sys.argv) > 5 else ""
PX = 3.0
BLACK = (0, 0, 0); WHITE = (255, 255, 255)
n_out = 0
def out(ok, name, detail):
    global n_out; n_out += 1
    print("%s|%s|%s" % ("PASS" if ok else "FAIL", name, str(detail).replace("|", "/")))
def rec(name, value): print("RECORD|%s|%s" % (name, str(value).replace("|", "/")))
def within(name, want, got_px, tol=1.0):
    if got_px is None: out(False, name, "not measurable"); return
    got = got_px / PX
    out(abs(got - want) <= tol + 1e-9, name, "%.2f epx vs %.2f ± %s" % (got, want, tol))
def cited(name, want, got_px):
    if got_px is None: rec(name, "not measurable"); return
    got = got_px / PX
    rec(name + " (r11 value, not worded in the row)", "%.2f epx vs r11 %.2f (%s)" % (got, want, "within 1 epx" if abs(got - want) <= 1 else "DIFFERS by %.2f" % (got - want)))
def close(c, want, tol): return all(abs(int(c[i]) - int(want[i])) <= tol for i in range(3))
def colour(name, c, want, tol=4):
    out(c is not None and close(c, want, tol), name, "(%s) vs (%d,%d,%d) ± %d" % (",".join(str(int(v)) for v in c) if c is not None else "none", want[0], want[1], want[2], tol))
xml = open(dump, encoding='utf-8', errors='replace').read()
img = np.asarray(Image.open(png).convert('RGB')).astype(np.int32)
H, W = img.shape[0], img.shape[1]
nodes = []
for m in re.finditer(r'<node[^>]*>', xml):
    s = m.group(0)
    b = [int(v) for v in re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s).groups()]
    nodes.append(dict(id=re.search(r'resource-id="([^"]*)"', s).group(1), text=html.unescape(re.search(r'text="([^"]*)"', s).group(1)),
                      b=b, sel=re.search(r'selected="([^"]*)"', s).group(1) == "true", en=re.search(r'enabled="([^"]*)"', s).group(1) == "true"))
def node(i): return next((x for x in nodes if x["id"] == i), None)
def all_nodes(prefix): return [x for x in nodes if x["id"].startswith(prefix)]
def inside(a, b): return a[0] >= b[0] and a[1] >= b[1] and a[2] <= b[2] and a[3] <= b[3]
def clip(l, t, r, b): return max(0, l), max(0, t), min(W, r), min(H, b)
def proj_mask(l, t, r, b, ink, fill):
    l, t, r, b = clip(l, t, r, b)
    reg = img[t:b, l:r]; fill = np.array(fill); v = np.array(ink) - fill; vv = int((v * v).sum())
    if vv == 0 or reg.size == 0: return np.zeros(reg.shape[:2], dtype=bool), l, t
    return ((reg - fill) * v).sum(axis=2) * 2 >= vv, l, t
def ink_box(l, t, r, b, ink, fill=BLACK):
    m, l, t = proj_mask(l, t, r, b, ink, fill)
    ys, xs = np.where(m)
    if len(ys) == 0: return None
    return (int(l + xs.min()), int(t + ys.min()), int(l + xs.max() + 1), int(t + ys.max() + 1))
def far(l, t, r, b, fill=BLACK):
    l, t, r, b = clip(l, t, r, b)
    reg = img[t:b, l:r].reshape(-1, 3)
    if len(reg) == 0: return None
    return reg[int(np.abs(reg - np.array(fill)).sum(axis=1).argmax())]
def first_glyph(l, t, r, b, ink, fill=BLACK):
    """the first glyph's ink box: the leftmost run of ink columns (gaps < 2 px merged), then its ink rows"""
    m, l, t = proj_mask(l, t, r, b, ink, fill)
    cols = m.any(axis=0); xs = np.where(cols)[0]
    if len(xs) == 0: return None
    c0 = int(xs[0]); c1 = c0
    while c1 + 1 < len(cols) and (cols[c1 + 1] or (c1 + 2 < len(cols) and cols[c1 + 2])): c1 += 1
    rows = np.where(m[:, c0:c1 + 1].any(axis=1))[0]
    return (l + c0, int(t + rows.min()), l + c1 + 1, int(t + rows.max() + 1))
def stem_ratio(l, t, r, b, ink, fill=BLACK):
    """median horizontal ink run in the middle of the first glyph's cap, over the cap height: heavier type → larger"""
    g = first_glyph(l, t, r, b, ink, fill)
    if g is None: return None
    cap = g[3] - g[1]
    m, l0, t0 = proj_mask(l, g[1] + cap // 4, r, g[3] - cap // 4, ink, fill)
    runs = []
    for row in m:
        k = 0
        for vflag in list(row) + [False]:
            if vflag: k += 1
            elif k: runs.append(k); k = 0
    if not runs or cap == 0: return None
    return float(np.median(runs)) / cap
sbn, nvn = node("w10m_status_bar"), node("w10m_nav_bar")
SB = sbn["b"][3] if sbn else None; NT = nvn["b"][1] if nvn else None

if kind == "bars":
    status_epx, nav_epx = (int(v) for v in extra.split(","))
    out(sbn is not None, "the drawn status bar is in the dump (w10m_status_bar)", sbn["b"] if sbn else "none")
    out(nvn is not None, "the drawn nav bar is in the dump (w10m_nav_bar)", nvn["b"] if nvn else "none")
    if sbn and nvn:
        within("the drawn status bar's height = BarMetrics.STATUS_EPX (%d)" % status_epx, status_epx, sbn["b"][3] - sbn["b"][1], 0.34)
        within("the drawn nav bar's height = BarMetrics.NAV_EPX (%d)" % nav_epx, nav_epx, nvn["b"][3] - nvn["b"][1], 0.34)
        out(sbn["b"][1] == 0, "the status bar starts at the screen's top", sbn["b"])
        out(nvn["b"][3] == H, "the nav bar ends at the screen's bottom", "%s, screen %d" % (nvn["b"], H))
        colour("pixels: the status bar's ground is black (left edge, mid height)", img[SB // 2, 4], BLACK, 2)
        colour("pixels: … and at its right edge", img[SB // 2, W - 5], BLACK, 2)
        colour("pixels: the nav bar's ground is black (between its keys)", img[NT + 12, 6], BLACK, 2)
        colour("pixels: … and at its bottom-right corner", img[H - 6, W - 6], BLACK, 2)
        white = int(((img[NT:H] > 200).all(axis=2)).sum())
        out(white > 200, "pixels: the nav bar's keys are drawn (white ink on it)", "%d white pixels" % white)
    print("END|%d" % n_out); sys.exit(0)

if SB is None or NT is None:
    out(False, "the drawn bars are in the dump", "no w10m_status_bar / w10m_nav_bar"); print("END|%d" % n_out); sys.exit(0)

if kind == "pivots":
    ref = None
    for x in nodes:
        if x["id"].startswith("people_name:") and x["text"][:1].isascii() and x["text"][:1].isupper(): ref = x; break
    caps = {}
    for key, word in (("contacts", "CONTACTS"), ("groups", "GROUPS")):
        p = node("people_pivot:" + key)
        if p is None: out(False, "pivot header %s is in the dump" % word, "none"); continue
        out(p["text"] == word, "the pivot header reads %s in caps" % word, "[%s]" % p["text"])
        b = list(p["b"]); b[3] = min(b[3], SB + 46 * 3)      # never into the search box below (its grey border is half-way to white)
        c = far(*b)
        if p["sel"]: colour("the selected pivot (%s) is white (P1.2)" % word, c, WHITE)
        else: colour("the other pivot (%s) is (156,156,156) ± 4 (P1.2)" % word, c, (156, 156, 156))
        g = first_glyph(b[0] - 6, b[1], b[2] + 6, b[3], c)
        if g is None: out(False, "%s: ink" % word, "none"); continue
        caps[key] = g
        within("%s: cap 11.0 epx (P1.1)" % word, 11.0, g[3] - g[1])
        within("%s: cap top 19.5 epx below the status bar (P1.1)" % word, 19.5, g[1] - SB)
        sr = stem_ratio(b[0] - 6, b[1], b[2] + 6, b[3], c)
        if ref is not None and sr is not None:
            rr = stem_ratio(ref["b"][0] - 6, ref["b"][1], ref["b"][2] + 6, ref["b"][3], WHITE)
            out(rr is not None and sr >= 1.2 * rr, "%s is semibold: its stems are heavier than the regular-weight name text's (stem / cap ≥ 1.2 ×)" % word,
                "header %.3f vs name '%s' %.3f (× %.2f)" % (sr, ref["text"], rr or 0, sr / rr if rr else 0))
        else: out(False, "%s is semibold (stem / cap against the regular name text)" % word, "no reference row or no ink")
    if "contacts" in caps: cited("CONTACTS: ink from x 12.5 (P1.1)", 12.5, caps["contacts"][0])
    out(sum(1 for x in all_nodes("people_pivot:") if x["sel"]) == 1, "exactly one pivot is selected", [x["id"] for x in all_nodes("people_pivot:") if x["sel"]])
    # the search box, as drawn: its (133,133,133) border
    sb = node("people_search_box")
    if sb is None: out(False, "the search box is in the dump", "none")
    else:
        b = sb["b"]; l, t, r, bb = clip(b[0] - 12, b[1] - 12, b[2] + 12, b[3] + 12)
        reg = img[t:bb, l:r]
        m = (np.abs(reg - np.array([133, 133, 133])) <= 4).all(axis=2)
        ys, xs = np.where(m)
        if len(ys) == 0: out(False, "the search box's (133,133,133) border is drawn", "no such pixel near %s" % (b,))
        else:
            box = (l + xs.min(), t + ys.min(), l + xs.max() + 1, t + ys.max() + 1)
            within("search box: top 48 epx below the status bar (P1.3)", 48, box[1] - SB)
            within("search box: 36 epx tall (P1.3)", 36, box[3] - box[1])
            within("search box: from x 12 (P1.3)", 12, box[0])
            within("search box: to W − 12 (348 at 360 epx; P1.3)", 348, box[2])
            midx = (box[0] + box[2]) // 2; midy = (box[1] + box[3]) // 2
            def run(seq):
                k = 0
                for p in seq:
                    if (np.abs(p - np.array([133, 133, 133])) <= 4).all(): k += 1
                    else: break
                return k
            sides = dict(top=run(img[box[1]:box[1] + 30, midx]), bottom=run(img[box[3] - 1:box[3] - 31:-1, midx]),
                         left=run(img[midy, box[0]:box[0] + 30]), right=run(img[midy, box[2] - 1:box[2] - 31:-1]))
            out(all(abs(v - 6) <= 1 for v in sides.values()), "search box: a 2-epx border on all four sides (6 px ± 1 px)", sides)
            colour("search box: the border is (133,133,133)", img[midy, box[0] + 2], (133, 133, 133))
            colour("search box: black fill inside the border", img[box[1] + 10, box[2] - 14], BLACK, 2)
            hint = far(box[0] + 9, box[1] + 9, box[2] - 9, box[3] - 9)
            hb = ink_box(box[0] + 9, box[1] + 9, box[2] - 9, box[3] - 9, hint) if hint is not None and int(hint.sum()) > 90 else None
            hn = node("people_search_hint")
            out(hn is not None and hn["text"] == "Search", "search box: the placeholder reads \"Search\"", hn["text"] if hn else "none")
            within("search box: \"Search\" at x 25 (P1.3)", 25, hb[0] if hb else None)
            rec("search box: the placeholder's colour (r11: grey 142)", tuple(int(v) for v in hint) if hint is not None else "none")

elif kind == "grid":
    cells = sorted(all_nodes("people_jump_cell:"), key=lambda x: (x["b"][1], x["b"][0]))
    out(len(cells) == 28, "the grid holds 28 cells (#, A–Z, the globe)", len(cells))
    if cells:
        tops = sorted({c["b"][1] for c in cells})
        first = [c for c in cells if c["b"][1] == tops[0]]
        out(len(first) == 4, "4 columns at the AVD's 360 epx (P2.2)", [c["id"].split(":", 1)[1] for c in first])
        ink = {}
        for c in cells:
            key = c["id"].split(":", 1)[1]; b = c["b"]; col = far(*b)
            g = ink_box(b[0], b[1], b[2], b[3], col) if col is not None and int(col.sum()) > 60 else None
            if g: ink[key] = (g, b)
        def cx(k): g = ink[k][0]; return (g[0] + g[2]) / 2.0
        # across: the letters' ink centres, a full row of letters; down: cap tops in one column
        # across: the ink centres of letters that are symmetric about their own centre (H, I, M, O, T, U, V, W, X, Y), so a
        # glyph's side bearings do not enter; the pairs are one column apart (M → O: two)
        for a, b2, k in (("H", "I", 1), ("T", "U", 1), ("U", "V", 1), ("V", "W", 1), ("X", "Y", 1), ("M", "O", 2)):
            if a in ink and b2 in ink and ink[a][1][1] == ink[b2][1][1]:
                within("cell pitch across, %s → %s: %d × 72 epx (the letters' ink centres; P2.2)" % (a, b2, k), 72 * k, cx(b2) - cx(a))
        lefts = sorted({c["b"][0] for c in cells})
        out(len(lefts) == 4 and all(lefts[i + 1] - lefts[i] == 216 for i in range(3)), "the four columns' cells start 72 epx apart", lefts)
        for a, b2 in (("A", "E"), ("E", "I"), ("I", "M"), ("B", "F"), ("D", "H"), ("H", "L")):
            if a in ink and b2 in ink and ink[a][1][0] == ink[b2][1][0]:
                within("cell pitch down, %s → %s: 72 epx (cap tops; P2.2)" % (a, b2), 72, ink[b2][0][1] - ink[a][0][1])
        for c in cells:   # the nodes themselves
            if c["b"][2] - c["b"][0] != 216 or c["b"][3] - c["b"][1] != 216:
                out(False, "cell %s is a 72-epx square" % c["id"], c["b"]); break
        else: out(True, "every cell node is a 72 × 72 epx square", "28 cells of 216 × 216 px")
        for k in ("A", "B"):
            if k in ink: within("first row: cap top of %s 119.5 epx below the status bar (P2.3)" % k, 119.5, ink[k][0][1] - SB)
        for k in ("A", "B", "D", "E", "F", "H", "K", "L", "M", "N", "T", "Z"):
            if k in ink: within("letter %s: cap 14.4 epx (P2.4)" % k, 14.4, ink[k][0][3] - ink[k][0][1])
        block = (min(c["b"][0] for c in cells), max(c["b"][2] for c in cells))
        cited("the grid block's centre against the screen's (P2.3: about 3 epx left of centre)", W / 2 / PX - 3, (block[0] + block[1]) / 2)
        out(node("people_pivot:contacts") is not None and node("people_search_box") is not None, "the pivot header and the search box stay above the grid (P2.1)", "present")

elif kind == "card":
    A = tuple(int(v) for v in ACC)
    colA = img[SB:NT, W - 18]; colB = img[SB:NT, 18]
    out(bool((np.abs(colA - ACC) <= 4).all()), "the card is an accent page from the status bar's bottom to the nav bar's top (column x = W − 6 epx; P3.1)",
        "%d of %d pixels are the accent ± 4" % (int((np.abs(colA - ACC) <= 4).all(axis=1).sum()), len(colA)))
    out(bool((np.abs(colB - ACC) <= 4).all()), "… and down the left edge (column x = 6 epx)", "%d of %d" % (int((np.abs(colB - ACC) <= 4).all(axis=1).sum()), len(colB)))
    colour("the status bar above it is black", img[SB - 2, W - 18], BLACK, 2)
    colour("the nav bar below it is black", img[NT + 2, W - 18], BLACK, 2)
    nm = node("people_card_name")
    if nm is None: out(False, "the card has people_card_name", "none")
    else:
        out(nm["text"] == nm["text"].upper() and nm["text"] != "", "the name is in caps", "[%s]" % nm["text"])
        b = nm["b"]; g = first_glyph(b[0] - 9, b[1], b[2] + 9, b[3], WHITE, A)
        within("the name's left edge at x 13.25 (P3.2)", 13.25, g[0] if g else None)
        within("the name's cap top 26 epx below the status bar (P3.2)", 26, (g[1] - SB) if g else None)
        cited("the name's cap 10.4 (P3.2)", 10.4, (g[3] - g[1]) if g else None)
        colour("the name is white", far(*b, fill=A), WHITE)
    ph = node("people_card_photo")
    if ph is None: out(False, "the card has people_card_photo", "none")
    else:
        b = ph["b"]; l, t, r, bb = clip(b[0] - 15, b[1] - 15, b[2] + 15, b[3] + 15)
        m = np.abs(img[t:bb, l:r] - ACC).sum(axis=2) > 60
        ys, xs = np.where(m)
        if len(ys) == 0: out(False, "people_card_photo is drawn", "nothing but the accent near %s" % (b,))
        else:
            box = (l + xs.min(), t + ys.min(), l + xs.max() + 1, t + ys.max() + 1)
            within("people_card_photo: 124 epx wide (P3.4)", 124, box[2] - box[0])
            within("people_card_photo: 124 epx tall (P3.4)", 124, box[3] - box[1])
            within("people_card_photo: at x 12 (P3.4)", 12, box[0])
            within("people_card_photo: its top 96 epx below the status bar (P3.4)", 96, box[1] - SB)
            cs = [img[box[1] + 2, box[0] + 2], img[box[1] + 2, box[2] - 3], img[box[3] - 3, box[0] + 2], img[box[3] - 3, box[2] - 3]]
            ms = [img[(box[1] + box[3]) // 2, box[0] + 4], img[(box[1] + box[3]) // 2, box[2] - 5], img[box[1] + 4, (box[0] + box[2]) // 2], img[box[3] - 5, (box[0] + box[2]) // 2]]
            out(all(close(c, A, 6) for c in cs) and not any(close(mm, A, 6) for mm in ms), "people_card_photo is a circle (its box's corners are the page, its edge mid-points are not)",
                "corners %s mids %s" % ([tuple(int(v) for v in c) for c in cs], [tuple(int(v) for v in mm) for mm in ms]))
    rows = sorted(all_nodes("people_card_action:"), key=lambda x: x["b"][1])
    bar = node("people_app_bar"); bar_top = bar["b"][1] if bar else NT
    meas = []
    for rw in rows:
        title = next((x for x in nodes if x["id"] == "people_action_title" and inside(x["b"], rw["b"])), None)
        two = any(x["id"] == "people_action_detail" and inside(x["b"], rw["b"]) for x in nodes)
        if title is None or rw["b"][3] > bar_top: continue
        tb = title["b"]; g = first_glyph(tb[0] - 9, tb[1], tb[2] + 9, tb[3], WHITE, A)
        meas.append((rw["id"], title["text"], two, g))
    out(len(meas) >= 3, "the card holds action rows to measure (a one-line row and two-line rows)", [(m_[1], "two lines" if m_[2] else "one line") for m_ in meas])
    for i, (rid, text, two, g) in enumerate(meas):
        within("action row \"%s\": the label's left edge at x 13 ± 1 (P3.6)" % text, 13, g[0] if g else None)
        if i + 1 < len(meas) and g and meas[i + 1][3]:
            nxt = meas[i + 1]
            if two: within("action pitch \"%s\" (two lines) → \"%s\": 65.5 ± 1 epx (P3.7)" % (text, nxt[1]), 65.5, nxt[3][1] - g[1])
            else: within("action pitch \"%s\" (one line) → \"%s\": 48 epx (P3.7)" % (text, nxt[1]), 48, nxt[3][1] - g[1])
    out(any(not m_[2] for m_ in meas[:-1]), "a one-line row's pitch was measured", "")
    out(any(m_[2] for m_ in meas[:-1]), "a two-line row's pitch was measured", "")

elif kind == "editor":
    hd = node("people_editor_header")
    scrolled = extra == "scrolled"          # a second screen of the same editor, scrolled: only what is on it
    if scrolled and (hd is None or hd["b"][1] < SB): pass
    elif hd is None: out(False, "the editor has its header", "none")
    else:
        out(bool(re.fullmatch(r"EDIT .+ CONTACT", hd["text"])) and hd["text"] == hd["text"].upper(), "the header reads \"EDIT <ACCOUNT> CONTACT\" in caps (P4.1)", "[%s]" % hd["text"])
        b = hd["b"]; g = first_glyph(b[0] - 9, b[1], b[2] + 9, b[3], WHITE)
        within("the header's left edge at x 13 (P4.1)", 13, g[0] if g else None)
        cited("the header's cap top below the status bar (P4.1: 45.5 under a 24-epx bar = 21.5)", 21.5, (g[1] - SB) if g else None)
        cited("the header's cap 11.0 (P4.1)", 11.0, (g[3] - g[1]) if g else None)
    bar = node("people_app_bar"); bar_top = bar["b"][1] if bar else NT
    G = np.array([133, 133, 133])
    measured = 0
    for f in all_nodes("people_field:"):
        b = f["b"]
        if b[3] > bar_top or b[1] < SB: continue
        l, t, r, bb = clip(0, b[1] - 6, W, b[3] + 6)
        m = (np.abs(img[t:bb, l:r] - G) <= 4).all(axis=2)
        rowsum = m.sum(axis=1); long_rows = np.where(rowsum >= 0.6 * W)[0]
        name = f["id"].split(":", 1)[1]
        if len(long_rows) == 0: out(False, "field %s: a drawn (133,133,133) border" % name, "none near %s" % (b,)); continue
        top, bot = t + int(long_rows.min()), t + int(long_rows.max()) + 1
        xs = np.where(m[long_rows.min()])[0]; left, right = l + int(xs.min()), l + int(xs.max()) + 1
        measured += 1
        within("field %s: 32 epx tall (P4.4)" % name, 32, bot - top)
        within("field %s: from x 12 (P4.4)" % name, 12, left)
        within("field %s: to W − 12 (348; P4.4)" % name, 348, right)
        midx = (left + right) // 2 + 150; midy = (top + bot) // 2
        def run(seq):
            k = 0
            for p in seq:
                if (np.abs(p - G) <= 4).all(): k += 1
                else: break
            return k
        sides = dict(top=run(img[top:top + 30, midx]), bottom=run(img[bot - 1:bot - 31:-1, midx]), left=run(img[midy, left:left + 30]), right=run(img[midy, right - 1:right - 31:-1]))
        out(all(abs(v - 6) <= 1 for v in sides.values()), "field %s: a 2-epx (133,133,133) border on all four sides (6 px ± 1 px; P4.4)" % name, sides)
        colour("field %s: black fill" % name, img[top + 9, right - 14], BLACK, 2)
        base = name.split(":")[0]; suffix = name[len(base):]
        lab = node("people_field_label:" + name) or node("people_field_type:" + name)
        if lab is None: out(False, "field %s: its label node" % name, "none"); continue
        lb = lab["b"]; lc = far(*lb)
        g = first_glyph(lb[0] - 6, lb[1], lb[2] + 6, lb[3], lc)
        within("field %s: label \"%s\" cap top → box top 22.75 epx (P4.5)" % (name, lab["text"]), 22.75, (top - g[1]) if g else None)
        if lab["id"].startswith("people_field_type:"): rec("field %s: its type label's colour (P4.3: the type in accent)" % name, "(%d,%d,%d) vs accent (%d,%d,%d)" % (tuple(int(v) for v in lc) + tuple(int(v) for v in ACC)))
        # an edit (pencil) button right of the box (P4.4's 34-epx form): any node drawn right of the box at its height
        btn = [x["id"] for x in nodes if x["id"] and x["b"][0] >= right - 3 and x["b"][1] < bot and x["b"][3] > top and x["b"][2] - x["b"][0] < 200]
        rec("field %s: an edit (pencil) button beside the box (P4.4's 34-epx form)" % name, btn or "none drawn — the box is the 32-epx form")
    if not scrolled: out(measured >= 2, "at least two field boxes were measured on this screen", measured)
    # "+ field" rows: every pair that follows one another in the editor's flow
    adds = sorted([x for x in all_nodes("people_add_field:") if x["b"][3] <= bar_top], key=lambda x: x["b"][1])
    plus = []
    for a in adds:
        ab = a["b"]; g = ink_box(ab[0], ab[1], min(ab[2], 100), ab[3], WHITE)   # the "+" glyph
        between = None
        plus.append((a["id"].split(":", 1)[1], g, ab))
    rec("\"+ field\" rows on this screen", [(p[0], "+ glyph y %s" % (p[1][1] if p[1] else None), "node %d epx tall" % round((p[2][3] - p[2][1]) / PX)) for p in plus])
    for i in range(len(plus) - 1):
        a, b2 = plus[i], plus[i + 1]
        fields_between = [x["id"] for x in all_nodes("people_field:") if a[2][3] <= x["b"][1] < b2[2][1]]
        rules_between = [x for x in all_nodes("people_rule") if a[2][3] <= x["b"][1] < b2[2][1]]
        if fields_between: continue      # a field sits between them: not consecutive rows
        if a[1] and b2[1]:
            within("\"+ %s\" → \"+ %s\" (consecutive \"+ field\" rows): 44-epx pitch (P4.6)" % (a[0], b2[0]), 44, b2[1][1] - a[1][1])
            if rules_between: rec("between \"+ %s\" and \"+ %s\"" % (a[0], b2[0]), "%d group rule(s) drawn" % len(rules_between))
    for a in plus:
        within("\"+ %s\": the row is 44 epx tall" % a[0], 44, a[2][3] - a[2][1])
        cited("\"+ %s\": the + glyph at x 12 (P4.6)" % a[0], 12, a[1][0] if a[1] else None)
    R = np.array([103, 103, 103]); rules = 0
    for x in all_nodes("people_rule"):
        b = x["b"]
        if b[3] > bar_top or b[1] < SB: continue
        colx = (b[0] + b[2]) // 2
        seg = img[b[1] - 6:b[3] + 6, colx]
        hit = np.where((np.abs(seg - R) <= 4).all(axis=1))[0]
        rules += 1
        out(len(hit) > 0 and abs(len(hit) - 3) <= 1 and hit.max() - hit.min() + 1 == len(hit), "group rule at y %d: 1 epx (3 px ± 1 px) of (103,103,103) ± 4 (P4.7)" % b[1],
            "%d px of that colour; column %s" % (len(hit), [tuple(int(v) for v in p) for p in seg[4:10]]))
        rowpx = img[b[1] + 1]
        xs = np.where((np.abs(rowpx - R) <= 4).all(axis=1))[0]
        if len(xs): cited("group rule at y %d: from x 12 (P4.7)" % b[1], 12, int(xs.min())); cited("group rule at y %d: to W − 12 (P4.7)" % b[1], 348, int(xs.max()) + 1)
    if not scrolled: out(rules >= 1, "at least one group rule was measured on this screen", rules)

elif kind == "appbar":
    bar = node("people_app_bar")
    if bar is None: out(False, "%s: the page has an app bar (people_app_bar)" % extra, "none")
    else:
        bb = bar["b"]
        start = nodes.index(bar); stop = next((i for i in range(start + 1, len(nodes)) if nodes[i]["id"] == "w10m_nav_bar"), len(nodes))
        btns = sorted([x for x in nodes[start + 1:stop] if x["id"] and inside(x["b"], bb) and x["b"][3] - x["b"][1] == bb[3] - bb[1] and x["b"][2] - x["b"][0] < 400], key=lambda x: x["b"][0])
        fill = tuple(int(v) for v in img[bb[1] + 6, bb[0] + 6])
        cen = []
        for x in btns:
            b = x["b"]; c = far(b[0], b[1], b[2], b[3], fill)
            g = ink_box(b[0], b[1], b[2], b[3], c, fill) if c is not None and int(np.abs(c - np.array(fill)).sum()) > 40 else None
            cen.append((x["id"], (g[0] + g[2]) / 2.0 if g else None))
        rec("%s: the app bar's buttons, left to right (glyph centres in epx)" % extra, [(i, round(c / PX, 2) if c else None) for i, c in cen])
        out(len(cen) >= 2 and all(c is not None for _, c in cen), "%s: every button's glyph is drawn" % extra, len(cen))
        # r11 P0.4: the buttons sit on a 68-epx pitch (… 142, 210, 278) and "…" is centred 24 epx from the right edge
        # (336 at 360 epx — 58 from the last button, not on the pitch).
        plain = [(i, c) for i, c in cen if i != "people_more"]
        for i in range(len(plain) - 1):
            if plain[i][1] is not None and plain[i + 1][1] is not None:
                within("%s: %s → %s at a 68-epx pitch (P0.4)" % (extra, plain[i][0], plain[i + 1][0]), 68, plain[i + 1][1] - plain[i][1])
        if len(plain) < 2: rec("%s: buttons beside \"…\"" % extra, "%d — no pair to take a pitch from on this bar" % len(plain))
        if plain and plain[-1][1] is not None: cited("%s: the button next to \"…\" centred at x 278 (P0.4)" % extra, 278, plain[-1][1])
        more = next((c for i, c in cen if i == "people_more"), None)
        if any(i == "people_more" for i, _ in cen): within("%s: \"…\" centred 24 epx from the right edge (P0.4)" % extra, 24, (W - more) if more else None)
        else: rec("%s: \"…\"" % extra, "this app bar has no \"…\" button")
        cited("%s: the app bar is 48 epx tall above the nav bar (P0.4)" % extra, 48, bb[3] - bb[1])
print("END|%d" % n_out)
PY
}
# The system's own bars are not visible (phase 01 E19's form): both insets sources read visible=false.
system_bars_hidden() {
  adb shell dumpsys window | tr -d '\r' | grep -E 'InsetsSource id=[0-9a-f]+ type=(statusBars|navigationBars)' | grep -v 'mSource=' | sed -E 's/.*type=([a-zA-Z]+).*visible=([a-z]+).*/\1=\2/' | sort -u | tr '\n' ' ' | sed 's/ $//'
}
bar_metrics() { # "STATUS_EPX,NAV_EPX" read from the code (C-17: never a literal in a row)
  local f="$REPO/app/src/main/kotlin/app/tileshell/bars/SystemBars.kt"
  echo "$(sed -n 's/.*const val STATUS_EPX = \([0-9]*\).*/\1/p' "$f" | head -1),$(sed -n 's/.*const val NAV_EPX = \([0-9]*\).*/\1/p' "$f" | head -1)"
}

# ================================================================================================= EDGE sub-steps
# The People bullets of the phase doc's "Edge cases", by scripts/edge_index.tsv: P01 … P10. The lead's scripts/edge.sh
# sources this file and calls edge_<ID> inside its EDGE row (`EDGE_ONLY=P03 bash scripts/edge.sh` runs one). Each
# sub-step takes its own MARKs, makes its own fixtures and restores them: the raw contacts it inserted are deleted by
# id (those people_add appended to the row's ids file after the sub-step began) and the raw_contacts count asserted.
GROUPS_SA="$GROUPS_URI?caller_is_syncadapter=true"
_pe_begin() { # id
  PE_ID="$1"; PE_D="$ROW_DIR/$1"; mkdir -p "$PE_D"
  touch "$ROW_DIR/people-fixtures.ids"
  PE_LINES="$(wc -l < "$ROW_DIR/people-fixtures.ids")"
  PE_RAW0="$(raw_count)"
  PE_GROUPS0="$(q "content query --uri $GROUPS_URI --projection _id:title:account_type:account_name")"
  perm_ensure READ_CONTACTS WRITE_CONTACTS
  ensure_start
}
_pe_end() {
  local id g
  tail -n +$((PE_LINES + 1)) "$ROW_DIR/people-fixtures.ids" | while read -r id; do
    [ -n "$id" ] && q "content delete --uri '$RAW/$id?$SA'" >/dev/null
  done
  head -n "$PE_LINES" "$ROW_DIR/people-fixtures.ids" > "$ROW_DIR/.ids.tmp"; mv "$ROW_DIR/.ids.tmp" "$ROW_DIR/people-fixtures.ids"
  for g in $(q "content query --uri $GROUPS_URI --projection _id:title" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p'); do
    printf '%s\n' "$PE_GROUPS0" | grep -q "_id=$g," || q "content delete --uri '$GROUPS_URI/$g?$SA'" >/dev/null
  done
  assert_eq "$PE_ID restore: the raw_contacts count is the count before the sub-step" "$PE_RAW0" "$(raw_count)"
  assert_eq "$PE_ID restore: the groups equal the groups before the sub-step" "$PE_GROUPS0" "$(q "content query --uri $GROUPS_URI --projection _id:title:account_type:account_name")"
  assert_eq "$PE_ID restore: WRITE_CONTACTS and READ_CONTACTS held" "true true" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"
  c6; ensure_start
}
_pe_id_of() { # display name -> newest raw id with it
  q "content query --uri $RAW --projection _id --where \"display_name='$1' AND deleted=0\" --sort '_id DESC'" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1
}
shell_pid() { adb shell pidof app.tileshell | tr -d '\r'; }
selected_page() { grep -o '<node[^>]*resource-id="people_page:[^"]*"[^>]*>' "$1" | grep 'selected="true"' | sed 's/.*resource-id="\(people_page:[^"]*\)".*/\1/' | tr '\n' ' ' | sed 's/ $//'; }

# ---------------------------------------------------------------- P01: 5,000 contacts
# B14.1 — People with 5,000 contacts: the list's top rows are there within 3 s, and the jump grid still lands on its
# letter. A `content insert` loop of 10,000 device-shell calls would hold the shared emulator for the better part of an
# hour, so the 5,000 are made the way a user gets that many: one vCard file imported by the image's own Contacts app
# (the phone's local account), timed and recorded. They are removed by their name pattern in one sync-adapter delete.
edge_P01() {
  _pe_begin P01
  local d="$PE_D" vcf="$PE_D/qa-e5k.vcf" t0 t1 n uri mark line top0 i c0 words
  c0="$(contacts_count)"
  python3 - "$vcf" <<'PY'
import sys
words = ["Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliett", "Kilo", "Lima", "Mike",
         "November", "Oscar", "Papa", "Quebec", "Romeo", "Sierra", "Tango", "Uniform", "Victor", "Whiskey", "Xray", "Yankee", "Zulu"]
with open(sys.argv[1], "w", encoding="utf-8", newline="\r\n") as f:
    for i in range(5000):
        w = words[i % 26]
        f.write("BEGIN:VCARD\nVERSION:3.0\nN:E5K%04d;%s;;;\nFN:%s E5K%04d\nTEL;TYPE=CELL:+1556%07d\nEND:VCARD\n" % (i, w, w, i, i))
PY
  adb shell mkdir -p /sdcard/Download >/dev/null 2>&1
  adb push "$vcf" /sdcard/Download/qa-e5k.vcf >/dev/null
  adb shell content call --uri content://media --method scan_file --arg /sdcard/Download/qa-e5k.vcf >/dev/null 2>&1; sleep 2
  uri="content://media/external/file/$(q "content query --uri content://media/external/file --projection _id:_data --where \"_data LIKE '%qa-e5k.vcf'\"" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
  note "P01: the vCard file's content URI: $uri"
  local imp; imp="$(adb shell cmd package query-activities --brief -a android.intent.action.VIEW -t text/x-vcard | tr -d '\r' | grep '^ *com.android.contacts/' | head -1 | tr -d ' ')"
  t0="$(date +%s)"
  adb shell am start -W -n "$imp" -a android.intent.action.VIEW -d "$uri" -t text/x-vcard --grant-read-uri-permission > "$d/import-am.txt" 2>&1
  sleep 3; dump_ui "$d/import.xml"; record "P01: what the image's Contacts import shows" "$(grep -o 'text="[^"]\+"' "$d/import.xml" | tr '\n' ' ' | cut -c1-200)"
  for i in $(seq 1 120); do
    n="$(q "content query --uri $RAW --projection _id --where \"display_name LIKE '% E5K%' AND deleted=0\"" | grep -c '_id=')"
    [ "$n" -ge 5000 ] && break
    sleep 5
  done
  t1="$(date +%s)"
  record "P01: the import of 5,000 contacts took (the driver loop's run time)" "$((t1 - t0)) s; $n raw contacts named '… E5K…'"
  assert_eq "P01: the provider holds the 5,000 fixture contacts" "5000" "$n"
  adb shell am force-stop com.android.contacts
  # the list: a cold start of the shell, then People; its read of the whole book and its first rows
  c6; ensure_start
  mark="$(ring_mark)"
  adb shell am start -W -n "$PEOPLE_PKG_ACTIVITY" -a android.intent.action.MAIN > "$d/am-start.txt" 2>&1
  for i in $(seq 1 40); do line="$(ring_since "$mark" | grep -F '[people] list:' | tail -1)"; [ -n "$line" ] && break; sleep 0.25; done
  dump_ui "$d/list.xml"; screencap "$d/list.png"
  log "P01: $(printf '%s' "$line" | sed 's/.*\[people\]/[people]/')"
  assert_eq "P01: the list read every contact (its line's n = the provider's count)" "$(contacts_count)" "$(printf '%s' "$line" | sed -E 's/.*list: ([0-9]+) contacts.*/\1/')"
  local wall; wall="$(printf '%s' "$line" | sed -n 's/.*wall=\([0-9]*\).*/\1/p')"
  assert_eq "P01: the list line is written within 3 s of the launch's MARK (wall − MARK ≤ 3000 ms)" "yes" "$([ -n "$wall" ] && [ $((wall - mark)) -le 3000 ] && echo yes || echo no) "
  record "P01: wall − MARK of the list line" "$([ -n "$wall" ] && echo $((wall - mark)) || echo none) ms"
  assert_ne "P01: the dump taken then holds the list's top rows (people_row: nodes)" "0" "$(count_ids "$d/list.xml" people_row:)"
  assert_contains "P01: am start -W reports the launch complete" "Status: ok" "$(cat "$d/am-start.txt")"
  record "P01: am start -W's own times" "$(grep -E 'TotalTime|WaitTime' "$d/am-start.txt" | tr -d '\r' | tr '\n' ' ')"
  # the jump grid still lands on its letter
  top0="$(ids_with_prefix "$d/list.xml" people_letter: | head -1)"
  tap_node "$d/list.xml" "$top0"; sleep 2
  dump_ui "$d/grid.xml"
  assert_eq "P01: a letter header still opens the jump grid" "yes" "$(has_node "$d/grid.xml" people_jump_grid)"
  mark="$(ring_mark)"
  tap_node "$d/grid.xml" people_jump_cell:M; sleep 3
  dump_ui "$d/at-m.xml"; screencap "$d/at-m.png"
  assert_eq "P01: tapping M closes the grid" "no" "$(has_node "$d/at-m.xml" people_jump_grid)"
  assert_eq "P01: … and the list lands on M: the M header is laid out" "yes" "$(has_node "$d/at-m.xml" people_letter:M)"
  local firstname; firstname="$(python3 - "$d/at-m.xml" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
mtop = None; rows = []
for m in re.finditer(r'<node[^>]*>', xml):
    s = m.group(0); rid = re.search(r'resource-id="([^"]*)"', s).group(1)
    top = int(re.search(r'bounds="\[-?\d+,(-?\d+)\]', s).group(1))
    if rid == "people_letter:M": mtop = top
    if rid.startswith("people_name:"): rows.append((top, html.unescape(re.search(r'text="([^"]*)"', s).group(1))))
after = sorted(r for r in rows if mtop is not None and r[0] > mtop)
print(after[0][1] if after else "")
PY
)"
  assert_contains "P01: the first row under the M header is an M name" "Mike E5K" "$firstname"
  note "P01: ring after the jump: $(ring_since "$mark" | grep -F '[people] jump' | sed 's/.*\[people\]/[people]/' | tr '\n' ';')"
  back 1
  # restore: the 5,000, by their name pattern, in one sync-adapter delete; the pushed file
  ring_save
  q "content delete --uri '$RAW?$SA' --where \"display_name LIKE '% E5K%'\"" >/dev/null
  for i in $(seq 1 30); do [ "$(raw_count)" = "$PE_RAW0" ] && break; sleep 2; done
  adb shell rm -f /sdcard/Download/qa-e5k.vcf; adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
  rm -f "$vcf"
  assert_eq "P01 restore: the contacts count is the count before" "$c0" "$(contacts_count)"
  _pe_end
}

# ---------------------------------------------------------------- P02: 20 numbers; a 20 MB photo
edge_P02() {
  _pe_begin P02
  local d="$PE_D" raw lk i types big seen pid0 b
  raw="$(people_add 'Twenty Numbers')"
  types=(2 1 3 7 2 1 3 7 2 1 3 7 2 1 3 7 2 1 3 7)      # mobile, home, work, other
  for i in $(seq 0 19); do people_data "$raw" vnd.android.cursor.item/phone_v2 "$(printf '+1 555 020 %04d' "$i")" "${types[$i]}"; done
  sleep 2
  lk="$(lookup_of "$(contact_of "$raw")")"
  assert_eq "P02: the provider holds her 20 numbers" "20" "$(q "content query --uri $DATA --projection _id --where \"raw_contact_id=$raw AND mimetype='vnd.android.cursor.item/phone_v2'\"" | grep -c '_id=')"
  card_of "$raw" "$d/card-0.xml"; screencap "$d/card-0.png"
  assert_eq "P02: her card (people_card:<lookup>)" "yes" "$(has_node "$d/card-0.xml" "people_card:$lk")"
  for i in 1 2 3 4 5 6 7 8; do adb shell input swipe 540 1700 540 900 600; sleep 1.2; dump_ui "$d/card-$i.xml"; done
  seen="$(python3 - "$d" <<'PY'
import glob, html, re, sys
found = {}
for f in sorted(glob.glob(sys.argv[1] + "/card-*.xml")):
    xml = open(f, encoding='utf-8', errors='replace').read()
    nodes = []
    for m in re.finditer(r'<node[^>]*>', xml):
        s = m.group(0)
        nodes.append((re.search(r'resource-id="([^"]*)"', s).group(1), html.unescape(re.search(r'text="([^"]*)"', s).group(1)),
                      [int(v) for v in re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s).groups()]))
    for rid, _, b in nodes:
        if rid.startswith("people_card_action:call:"):
            t = next((x[1] for x in nodes if x[0] == "people_action_title" and x[2][1] >= b[1] and x[2][3] <= b[3]), "")
            dt = next((x[1] for x in nodes if x[0] == "people_action_detail" and x[2][1] >= b[1] and x[2][3] <= b[3]), "")
            if t and dt: found[rid] = (t, dt)
for k in sorted(found, key=lambda x: int(x.rsplit(":", 1)[1])): print("%s\t%s\t%s" % (k, found[k][0], found[k][1]))
PY
)"
  printf '%s\n' "$seen" > "$d/calls.tsv"; printf '%s\n' "$seen" >> "$LOG"
  assert_eq "P02: all 20 numbers are listed on the card (people_card_action:call:0..19, each with its number)" "20" "$(printf '%s\n' "$seen" | awk -F'\t' '$3 ~ /555 020/' | cut -f3 | sort -u | grep -c .)"
  assert_eq "P02: … each with its label (a \"Call <type>\" title)" "20" "$(printf '%s\n' "$seen" | awk -F'\t' '$2 ~ /^Call ./' | grep -c .)"
  record "P02: the labels drawn" "$(printf '%s\n' "$seen" | cut -f2 | sort | uniq -c | tr '\n' ';' | tr -s ' ')"
  assert_eq "P02: the four types read four different labels" "4" "$(printf '%s\n' "$seen" | cut -f2 | sort -u | grep -c .)"
  back 1
  # a 20 MB contact photo, given through the editor and Android's photo picker
  big="$PL_FIX/big-photo-20mb.jpg"
  [ -s "$big" ] || python3 - "$big" <<'PY'
import sys
import numpy as np
from PIL import Image
rng = np.random.default_rng(16); n = 3100
a = rng.integers(0, 256, size=(n, n, 3), dtype=np.uint8)
a[n // 4:3 * n // 4, n // 4:3 * n // 4] = (254, 0, 0)       # a solid red centre the card's circle shows; noise to make it 20 MB
Image.fromarray(a).save(sys.argv[1], quality=97, subsampling=0)
PY
  record "P02: the photo fixture's size" "$(stat -c%s "$big") bytes (3100 × 3100, a solid (254,0,0) centre)"
  assert_eq "P02: the photo fixture is at least 20 MB" "yes" "$([ "$(stat -c%s "$big")" -ge 20000000 ] && echo yes || echo no)"
  adb shell mkdir -p /sdcard/Pictures >/dev/null 2>&1
  adb push "$big" /sdcard/Pictures/qa-big-photo.jpg >/dev/null
  adb shell content call --uri content://media --method scan_file --arg /sdcard/Pictures/qa-big-photo.jpg >/dev/null 2>&1; sleep 3
  pid0="$(shell_pid)"
  card_of "$raw" "$d/p-card.xml"; tap_node "$d/p-card.xml" people_card_edit; sleep 2
  dump_ui "$d/p-edit.xml"; tap_node "$d/p-edit.xml" people_editor_photo; sleep 3
  assert_contains "P02: Android's photo picker opened" "photopicker" "$(top_activity | tr 'A-Z' 'a-z')"
  pick_photo "254,0,0"
  sleep 2; dump_ui "$d/p-picked.xml"; screencap "$d/p-picked.png"
  tap_node "$d/p-picked.xml" people_editor_save; sleep 6
  assert_eq "P02: a photo data row exists (the 20 MB picture was taken)" "1" "$(photo_rows "$raw")"
  card_of "$raw" "$d/p-card2.xml"; screencap "$d/p-card2.png"
  b="$(bounds "$d/p-card2.xml" people_card_photo)"
  assert_color "P02: the card draws the full photo (people_card_photo's centre pixel is the picture's centre colour ± 8)" "254,0,0" "$(centre_px "$d/p-card2.png" "${b:-0 0 2 2}")" 8
  open_people -a android.intent.action.MAIN
  tapid people_search 1; type_text "Twenty"; sleep 2
  dump_ui "$d/p-list.xml"; screencap "$d/p-list.png"
  b="$(bounds "$d/p-list.xml" "people_avatar:$(lookup_of "$(contact_of "$raw")")")"
  assert_ne "P02: her row has an avatar in the list" "" "$b"
  assert_color "P02: the list draws the provider's thumbnail (the avatar's centre pixel is the picture's centre colour ± 12)" "254,0,0" "$(centre_px "$d/p-list.png" "${b:-0 0 2 2}")" 12
  assert_eq "P02: decoded at bounds — the shell's process is the one it was before the picture was picked (no crash)" "$pid0" "$(shell_pid)"
  assert_eq "P02: … and logcat holds no fatal exception of the shell's" "0" "$(adb logcat -d -t 400 AndroidRuntime:E '*:S' 2>/dev/null | tr -d '\r' | grep -c 'Process: app.tileshell')"
  adb shell input keyevent 67 67 67 67 67 67; back 1; back 1
  adb shell rm -f /sdcard/Pictures/qa-big-photo.jpg; adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
  _pe_end
}

# ---------------------------------------------------------------- P03: things vanish under an open page
edge_P03() {
  _pe_begin P03
  local d="$PE_D" raw lk a b la mark slice v l1 l2 lk2 note_t
  # (a) B14.4: a contact deleted while its card is open → the card closes with a notice
  raw="$(people_add 'Gone Card' '+1 555 030 0001')"; sleep 2
  lk="$(lookup_of "$(contact_of "$raw")")"
  card_of "$raw" "$d/a-card.xml"
  assert_eq "P03a: the card is open (people_card:<lookup>)" "yes" "$(has_node "$d/a-card.xml" "people_card:$lk")"
  mark="$(ring_mark)"
  q "content delete --uri '$RAW/$raw?$SA'" >/dev/null
  sleep 4
  dump_ui "$d/a-after.xml"; screencap "$d/a-after.png"
  assert_eq "P03a: the contact deleted elsewhere: the card closes (no people_card: on show)" "0" "$(count_ids "$d/a-after.xml" people_card:)"
  note_t="$(xml_text "$d/a-after.xml" people_notice)"; log "P03a: page on show: $(selected_page "$d/a-after.xml"); people_notice: [$note_t]"
  assert_eq "P03a: … with a notice (people_notice)" "yes" "$(has_node "$d/a-after.xml" people_notice)"
  assert_eq "P03a: no crash: People is still the resumed activity" "$PEOPLE_ACTIVITY" "$(top_activity)"
  # (b) B14.5: a contact linked across two accounts, then one account removed → the remaining raw contact stays a contact
  a="$(people_add 'Twin Acct' '+1 555 030 0002' '' pa@example.com com.example)"
  b="$(people_add 'Twin Other' '' 'twin@example.com' pb@example.com com.example)"; sleep 3
  la="$(lookup_of "$(contact_of "$a")")"; lk2="$(lookup_of "$(contact_of "$b")")"
  card_of "$a" "$d/b-card.xml"; tap_node "$d/b-card.xml" people_card_link; sleep 2
  tapid people_link_add 2
  tap_row_clear "people_row:$lk2" "$d/b-picker.xml"; sleep 1
  assert_eq "P03b: linked across the two accounts (one contact behind both raw contacts)" "$(contact_of "$a")" "$(contact_of "$b")"
  q "content delete --uri '$RAW?$SA' --where \"account_name='pb@example.com' AND account_type='com.example'\"" >/dev/null     # the account goes: its raw contacts go with it
  sleep 3
  assert_eq "P03b: the removed account's raw contact is gone" "0" "$(q "content query --uri $RAW --projection _id --where \"_id=$b\"" | grep -c '_id=')"
  assert_ne "P03b: the remaining raw contact still stands behind a contact" "" "$(contact_of "$a")"
  assert_eq "P03b: … a contacts row named for it" "1" "$(q "content query --uri $CONTACTS --projection _id:display_name --where \"_id=$(contact_of "$a")\"" | grep -c 'display_name=Twin Acct')"
  card_of "$a" "$d/b-card2.xml"; screencap "$d/b-card2.png"
  assert_eq "P03b: People shows its card" "TWIN ACCT" "$(xml_text "$d/b-card2.xml" people_card_name)"
  assert_contains "P03b: … with its own number" "+1 555 030 0002" "$(cat "$d/b-card2.xml")"
  assert_absent "P03b: … and without the removed account's e-mail" "twin@example.com" "$(cat "$d/b-card2.xml")"
  back 1; back 1
  # (c) the EDGE row's producer: [people] write update raw=<id>: failed <err> — the raw contact deleted by the driver
  #     while its editor is open, then Save
  v="$(people_add 'Vanish Edit' '+1 555 030 0003')"; sleep 2
  adb shell am start -W -n "$PEOPLE_PKG_ACTIVITY" -a android.intent.action.EDIT -d "$CONTACTS/lookup/$(lookup_of "$(contact_of "$v")")/$(contact_of "$v")" >/dev/null 2>&1; sleep 3
  set_field people_field:phone "+1 555 030 0033"
  q "content delete --uri '$RAW/$v?$SA'" >/dev/null; sleep 2
  dump_ui "$d/c-before-save.xml"
  record "P03c: the page on show after the raw contact was deleted under the editor" "$(selected_page "$d/c-before-save.xml")"
  mark="$(ring_mark)"
  if tap_node "$d/c-before-save.xml" people_editor_save; then sleep 3; fi
  dump_ui "$d/c-after-save.xml"; screencap "$d/c-after-save.png"
  slice="$(ring_since "$mark")"; log "P03c: $(printf '%s\n' "$slice" | grep -F '[people] write' | sed 's/.*\[people\]/[people]/')"
  assert_contains "P03c: Save on a vanished raw contact: [people] write update raw=<id>: failed <err>" "[people] write update raw=$v: failed " "$slice"
  assert_eq "P03c: … with a notice, no crash" "yes" "$(has_node "$d/c-after-save.xml" people_notice)"
  assert_eq "P03c: … nothing was written: no phone row holds the unsaved number" "0" "$(q "content query --uri $DATA --projection _id --where \"data1='+1 555 030 0033'\"" | grep -c '_id=')"
  back 1; back 1
  # (d) the EDGE row's producer: [people] link …: failed — the second raw contact deleted while the Link picker is open
  l1="$(people_add 'Link First' '+1 555 030 0004')"; l2="$(people_add 'Link Second' '+1 555 030 0005')"; sleep 2
  lk2="$(lookup_of "$(contact_of "$l2")")"
  card_of "$l1" "$d/d-card.xml"; tap_node "$d/d-card.xml" people_card_link; sleep 2
  tapid people_link_add 2
  dump_ui "$d/d-picker.xml"
  assert_eq "P03d: the picker lists the second contact" "yes" "$(has_node "$d/d-picker.xml" "people_row:$lk2")"
  q "content delete --uri '$RAW/$l2?$SA'" >/dev/null; sleep 2
  mark="$(ring_mark)"
  dump_ui "$d/d-picker2.xml"
  if [ "$(has_node "$d/d-picker2.xml" "people_row:$lk2")" = yes ]; then tap_row_clear "people_row:$lk2" "$d/d-picker3.xml"; else tap_node "$d/d-picker.xml" "people_row:$lk2"; sleep 2; fi
  sleep 1; dump_ui "$d/d-after.xml"; screencap "$d/d-after.png"
  slice="$(ring_since "$mark")"; log "P03d: $(printf '%s\n' "$slice" | grep -F '[people] link' | sed 's/.*\[people\]/[people]/')"
  assert_contains "P03d: the link of a vanished contact: [people] link <a>+<b>: failed" ": failed" "$(printf '%s\n' "$slice" | grep -F '[people] link ')"
  assert_eq "P03d: … with a notice" "yes" "$(has_node "$d/d-after.xml" people_notice)"
  assert_absent "P03d: … and no aggregation exception was written for the first contact" "raw_contact_id1=$l1," "$(exceptions)"
  back 1; back 1; back 1
  _pe_end
}

# ---------------------------------------------------------------- P04: "Can edit" when an account goes, and when the file goes
edge_P04() {
  _pe_begin P04
  local d="$PE_D" r lou acct=qa.edge@example.com typ=com.example
  r="$(people_add 'Edge Acct' '+1 555 040 0001' '' "$acct" "$typ")"; lou="$(people_add 'Edge Local' '+1 555 040 0002')"; sleep 2
  open_people_settings can_edit "$d/a.xml"
  assert_contains "P04: the account is listed on Can edit, unticked" 'checked="false"' "$(node_tag "$d/a.xml" "people_can_edit:$typ:$acct")"
  tap_node "$d/a.xml" "people_can_edit:$typ:$acct"; sleep 2
  assert_eq "P04: ticked: people_edit.json lists it" "$typ:$acct" "$(people_allowed)"
  # B15.1: the account removed from the phone (its raw contacts go): its row leaves the list and people_edit.json
  q "content delete --uri '$RAW/$r?$SA'" >/dev/null; sleep 3
  c6; ensure_start
  open_people_settings can_edit "$d/b.xml"; screencap "$d/b.png"
  assert_eq "P04: the removed account's row leaves the list" "no" "$(has_node "$d/b.xml" "people_can_edit:$typ:$acct")"
  assert_eq "P04: … and people_edit.json" "" "$(people_allowed)"
  record "P04: people_edit.json after the account went" "[$(people_edit_json)]"
  # re-added: it starts unticked
  r="$(people_add 'Edge Acct' '+1 555 040 0001' '' "$acct" "$typ")"; sleep 3
  c6; ensure_start
  open_people_settings can_edit "$d/c.xml"
  assert_contains "P04: re-added, the account starts unticked" 'checked="false"' "$(node_tag "$d/c.xml" "people_can_edit:$typ:$acct")"
  assert_eq "P04: … and is not in people_edit.json" "" "$(people_allowed)"
  card_of "$r" "$d/c-card.xml"
  assert_eq "P04: … so its contact is read-only (no people_card_edit)" "no" "$(has_node "$d/c-card.xml" people_card_edit)"
  # B15.2: people_edit.json lost: nothing is allowed again, phone-only contacts still editable
  open_people_settings can_edit "$d/d.xml"; tap_node "$d/d.xml" "people_can_edit:$typ:$acct"; sleep 2
  assert_eq "P04: ticked again before the file is lost" "$typ:$acct" "$(people_allowed)"
  ring_save
  adb shell am force-stop app.tileshell
  adb shell run-as app.tileshell rm -f files/people_edit.json
  adb shell am force-stop app.tileshell; sleep 1
  ensure_start
  open_people_settings can_edit "$d/e.xml"; screencap "$d/e.png"
  assert_contains "P04: people_edit.json lost: the account is unticked again" 'checked="false"' "$(node_tag "$d/e.xml" "people_can_edit:$typ:$acct")"
  assert_eq "P04: … nothing is allowed" "" "$(people_allowed)"
  card_of "$r" "$d/e-card.xml"
  assert_eq "P04: … its contact is read-only" "no" "$(has_node "$d/e-card.xml" people_card_edit)"
  card_of "$lou" "$d/e-local.xml"
  assert_eq "P04: … and a phone-only contact is still editable (people_card_edit)" "yes" "$(has_node "$d/e-local.xml" people_card_edit)"
  assert_eq "P04: … and deletable (people_card_delete)" "yes" "$(has_node "$d/e-local.xml" people_card_delete)"
  back 1; back 1
  _pe_end
  assert_eq "P04 restore: nothing is on Can edit" "" "$(people_allowed)"
}

# ---------------------------------------------------------------- P05: names and their buckets; a number typed plainly
edge_P05() {
  _pe_begin P05
  local d="$PE_D" ae fam org num lab_ae lab_fam lab_org
  ae="$(people_add 'Ærøskøbing')"
  q "content insert --uri $RAW --bind account_type:n: --bind account_name:n:" >/dev/null
  fam="$(q "content query --uri $RAW --projection _id --sort '_id DESC'" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)"; echo "$fam" >> "$ROW_DIR/people-fixtures.ids"
  q "content insert --uri $DATA --bind raw_contact_id:i:$fam --bind mimetype:s:vnd.android.cursor.item/name --bind data3:s:Solo" >/dev/null      # a family name only
  org="$(people_add '')"
  q "content insert --uri $DATA --bind raw_contact_id:i:$org --bind mimetype:s:vnd.android.cursor.item/organization --bind data1:s:'Quill Works'" >/dev/null   # company only
  num="$(people_add 'Dash Number' '+1 555-050 00-77')"
  sleep 3
  lab() { q "content query --uri $CONTACTS --projection display_name:phonebook_label --where \"_id=$(contact_of "$1")\""; }
  lab_ae="$(lab "$ae")"; lab_fam="$(lab "$fam")"; lab_org="$(lab "$org")"
  log "P05: provider: [$lab_ae] [$lab_fam] [$lab_org]"
  open_people -a android.intent.action.MAIN; list_top
  list_walk "$d/walk" > "$d/merged.tsv"; cat "$d/merged.tsv" >> "$LOG"
  hdr() { header_of "$d/merged.tsv" "$(lookup_of "$(contact_of "$1")")"; }
  nm() { awk -F'\t' -v k="$(lookup_of "$(contact_of "$1")")" '$1=="row" && $2==k {print $3}' "$d/merged.tsv"; }
  assert_eq "P05: \"Ærøskøbing\" files wherever PHONEBOOK_LABEL puts it" "$(printf '%s' "$lab_ae" | sed -n 's/.*phonebook_label=//p')" "$(hdr "$ae")"
  assert_eq "P05: … and reads its name" "Ærøskøbing" "$(nm "$ae")"
  assert_eq "P05: a name with only a family name is listed by it" "Solo" "$(nm "$fam")"
  assert_eq "P05: … under the provider's label for it" "$(printf '%s' "$lab_fam" | sed -n 's/.*phonebook_label=//p')" "$(hdr "$fam")"
  assert_eq "P05: a company-only contact shows its organisation as the name" "Quill Works" "$(nm "$org")"
  assert_eq "P05: … under the provider's label for it" "$(printf '%s' "$lab_org" | sed -n 's/.*phonebook_label=//p')" "$(hdr "$org")"
  # a number stored with spaces and dashes matches a search typed without them
  list_top
  tapid people_search 1
  local mark; mark="$(ring_mark)"
  type_text "15550500077"; sleep 2
  dump_ui "$d/search.xml"; screencap "$d/search.png"
  assert_eq "P05: a number stored \"+1 555-050 00-77\" matches the search \"15550500077\"" "yes" "$(has_node "$d/search.xml" "people_row:$(lookup_of "$(contact_of "$num")")")"
  assert_eq "P05: … and only it" "1" "$(count_ids "$d/search.xml" people_row:)"
  log "P05: $(ring_since "$mark" | grep -F '[people] search' | tail -1 | sed 's/.*\[people\]/[people]/')"
  back 1; back 1
  _pe_end
}

# ---------------------------------------------------------------- P06: READ_CONTACTS revoked mid-edit
edge_P06() {
  _pe_begin P06
  local d="$PE_D" raw before mark line
  raw="$(people_add 'Mid Edit' '+1 555 060 0001')"; sleep 2
  before="$(data_rows "$raw")"
  card_of "$raw" "$d/card.xml"; tap_node "$d/card.xml" people_card_edit; sleep 2
  set_field people_field:phone "+1 555 060 0099"
  dump_ui "$d/edit.xml"
  assert_eq "P06: the editor holds an unsaved edit" "+1 555 060 0099" "$(xml_text "$d/edit.xml" people_field:phone)"
  ring_save
  local pid0; pid0="$(shell_pid)"
  adb shell pm revoke app.tileshell android.permission.READ_CONTACTS; sleep 3      # ends the shell's process
  assert_eq "P06: READ_CONTACTS revoked, WRITE_CONTACTS still held" "false true" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"
  assert_ne "P06: the revoke ended the shell's process (the edit is lost)" "$pid0" "$(shell_pid)"
  assert_eq "P06: nothing half-written is in the provider (the data rows equal their read before the edit)" "$before" "$(data_rows "$raw")"
  ensure_start
  mark="$(ring_mark)"
  open_people -a android.intent.action.MAIN; sleep 1
  dump_ui "$d/reopen.xml"; screencap "$d/reopen.png"
  log "P06: people_notice: [$(xml_text "$d/reopen.xml" people_notice)] action: [$(xml_text "$d/reopen.xml" people_notice_action)]"
  assert_eq "P06: People reopens on its cannot-read notice (people_notice)" "yes" "$(has_node "$d/reopen.xml" people_notice)"
  assert_eq "P06: … offering the grant (people_notice_action)" "yes" "$(has_node "$d/reopen.xml" people_notice_action)"
  assert_eq "P06: … and lists no contact" "0" "$(count_ids "$d/reopen.xml" people_row:)"
  line="$(people_list_line "$mark")"; log "P06: $line"
  assert_eq "P06: [people] list: 0 contacts read=false write=true" "[people] list: 0 contacts read=false write=true" "$line"
  ring_save
  adb shell pm grant app.tileshell android.permission.READ_CONTACTS; sleep 1
  assert_eq "P06 restore: READ_CONTACTS held" "true" "$(perm_granted READ_CONTACTS)"
  _pe_end
}

# ---------------------------------------------------------------- P07: SIM entries with no number, and already imported
edge_P07() {
  _pe_begin P07
  local d="$PE_D" adn=content://icc/adn before simq mark line n1 n2
  before="$(q "content query --uri $adn")"
  q "content insert --uri $adn --bind tag:s:'Sim Nonum' --bind number:s:" > "$d/insert-nonum.txt" 2>&1
  q "content insert --uri $adn --bind tag:s:'Sim Dup' --bind number:s:5550703" > "$d/insert-dup.txt" 2>&1
  simq="$(q "content query --uri $adn")"; log "P07: icc/adn: $(echo "$simq" | tr '\n' ';' | cut -c1-300)"
  record "P07: the emulated SIM took the entry with no number" "$(printf '%s' "$simq" | grep -q 'Sim Nonum' && echo accepted || echo "refused: $(head -c 120 "$d/insert-nonum.txt" | tr '\n' ' ')")"
  record "P07: the emulated SIM took \"Sim Dup\"" "$(printf '%s' "$simq" | grep -q 'Sim Dup' && echo accepted || echo "refused: $(head -c 120 "$d/insert-dup.txt" | tr '\n' ' ')")"
  if printf '%s' "$simq" | grep -q 'Sim Dup'; then
    for pass in first second; do
      open_people_settings sim "$d/sim-$pass.xml"; screencap "$d/sim-$pass.png"
      mark="$(ring_mark)"
      tap_node "$d/sim-$pass.xml" people_sim_import; sleep 5
      line="$(ring_since "$mark" | grep -F '[people] sim import' | tail -1 | sed 's/.*\[people\] //')"
      record "P07: the $pass import's line" "$line"
      assert_contains "P07: the $pass import writes its sim import line" "sim import: " "$line"
      assert_eq "P07: no crash on the $pass import: People is still resumed" "$PEOPLE_ACTIVITY" "$(top_activity)"
      back 1; back 1; back 1
    done
    n2="$(q "content query --uri $RAW --projection _id:contact_id --where \"display_name='Sim Dup' AND deleted=0\"")"
    record "P07: \"Sim Dup\" imported twice: raw contacts / contacts" "$(printf '%s\n' "$n2" | grep -c '_id=') raw, $(printf '%s\n' "$n2" | grep -oE 'contact_id=[0-9]+' | sort -u | grep -c .) contact(s) (the provider aggregated them or not: either is recorded)"
    assert_eq "P07: the second import made a second raw contact" "2" "$(printf '%s\n' "$n2" | grep -c '_id=')"
    assert_eq "P07: both are phone-only" "2" "$(q "content query --uri $RAW --projection _id:account_name:account_type --where \"display_name='Sim Dup' AND deleted=0\"" | grep -c 'account_name=NULL, account_type=NULL')"
    record "P07: the entry with no number" "$(q "content query --uri $RAW --projection _id --where \"display_name='Sim Nonum' AND deleted=0\"" | grep -c '_id=') raw contact(s) were made from it"
    q "content query --uri $RAW --projection _id --where \"display_name IN ('Sim Dup','Sim Nonum') AND deleted=0\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' >> "$ROW_DIR/people-fixtures.ids"
  else
    record "P07: the SIM contact that already exists" "not drivable here: the emulated SIM refused the insert (P4 on the phone)"
  fi
  q "content delete --uri $adn --where \"tag='Sim Dup' AND number='5550703'\"" >/dev/null 2>&1
  q "content delete --uri $adn --where \"tag='Sim Nonum' AND number=''\"" >/dev/null 2>&1
  record "P07 restore: icc/adn" "$(q "content query --uri $adn" | tr '\n' ';' | cut -c1-200)"
  assert_eq "P07 restore: icc/adn equals its read before the sub-step" "$before" "$(q "content query --uri $adn")"
  _pe_end
}

# ---------------------------------------------------------------- P08: groups at their edges
edge_P08() {
  _pe_begin P08
  local d="$PE_D" mob home gid_e gid_m gid_n gid_w gid_d mark line holder lm lh
  holder="$(sms_holder)"
  mob="$(people_add 'Edge Mobile' '+1 555 080 0001')"
  home="$(people_add 'Edge Home')"; people_data "$home" vnd.android.cursor.item/phone_v2 '+1 555 080 0002' 1     # a HOME number, no mobile
  sleep 2
  lm="$(lookup_of "$(contact_of "$mob")")"; lh="$(lookup_of "$(contact_of "$home")")"
  mkgroup() { q "content insert --uri '$GROUPS_SA' --bind title:s:'$1' ${2:+--bind account_name:s:$2 --bind account_type:s:$3}" >/dev/null; q "content query --uri $GROUPS_URI --projection _id:title" | grep -F "title=$1" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }
  member() { q "content insert --uri $DATA --bind raw_contact_id:i:$2 --bind mimetype:s:vnd.android.cursor.item/group_membership --bind data1:i:$1" >/dev/null; }
  gid_e="$(mkgroup 'Empty Edge')"; gid_m="$(mkgroup 'Mixed Edge')"; gid_n="$(mkgroup 'Nomobile Edge')"; gid_d="$(mkgroup 'Doomed Edge')"
  gid_w="$(mkgroup 'Work Edge' qa.work@example.com com.example)"
  member "$gid_m" "$mob"; member "$gid_m" "$home"; member "$gid_n" "$home"
  sleep 2
  note "P08: groups empty=$gid_e mixed=$gid_m nomobile=$gid_n work=$gid_w doomed=$gid_d"
  group_page() { # id out.xml
    open_people -a android.intent.action.MAIN
    tapid people_pivot:groups 2
    tap_row_clear "people_group:$1" "$2.pivot.xml"
    dump_ui "$2"
  }
  # B19.1: a group with no members: "Text the group" shows a notice, no compose
  mark="$(ring_mark)"            # before the page is opened: the slice then holds People's own open lines, so it is proven readable
  group_page "$gid_e" "$d/empty.xml"
  if tap_node "$d/empty.xml" people_group_action:text; then sleep 3; else record "P08: an empty group's page" "offers no people_group_action:text"; fi
  dump_ui "$d/empty-after.xml"; screencap "$d/empty-after.png"
  assert_eq "P08: empty group: no compose is opened (People stays resumed)" "$PEOPLE_ACTIVITY" "$(top_activity)"
  log "P08: empty group: people_notice [$(xml_text "$d/empty-after.xml" people_notice)]"
  assert_eq "P08: empty group: a notice instead (people_notice)" "yes" "$(has_node "$d/empty-after.xml" people_notice)"
  absent_in "P08: empty group: no [people] action text line" "[people] action text ->" "$(ring_since "$mark")"
  # B19.2: a member with no mobile number is left out of the smsto: list
  adb shell am force-stop "$holder"
  group_page "$gid_m" "$d/mixed.xml"
  mark="$(ring_mark)"
  tap_node "$d/mixed.xml" people_group_action:text; sleep 4
  line="$(ring_since "$mark" | grep -F '[people] action text' | tail -1 | sed 's/.*\[people\]/[people]/')"; log "P08: $line"
  assert_contains "P08: mixed group: the compose opens for the member with a mobile number" "smsto:+15550800001" "$line"
  assert_absent "P08: mixed group: the member with no mobile number is left out" "5550800002" "$line"
  assert_contains "P08: mixed group: the SMS role holder is resumed" "$holder/" "$(top_activity)"
  adb shell am force-stop "$holder"
  # … none left → the notice
  group_page "$gid_n" "$d/nomobile.xml"
  mark="$(ring_mark)"
  tap_node "$d/nomobile.xml" people_group_action:text; sleep 3
  dump_ui "$d/nomobile-after.xml"
  assert_eq "P08: no member with a mobile number: no compose" "$PEOPLE_ACTIVITY" "$(top_activity)"
  assert_eq "P08: … the notice" "yes" "$(has_node "$d/nomobile-after.xml" people_notice)"
  # B19.3: a group under an account not on "Can edit": no rename or delete action, and the page says why
  group_page "$gid_w" "$d/work.xml"; screencap "$d/work.png"
  assert_eq "P08: the read-only group's page is on show" "yes" "$(has_node "$d/work.xml" "people_group:$gid_w")"
  assert_eq "P08: … no rename action" "no" "$(has_node "$d/work.xml" people_group_action:rename)"
  assert_eq "P08: … no delete action" "no" "$(has_node "$d/work.xml" people_group_action:delete)"
  log "P08: people_group_readonly: [$(xml_text "$d/work.xml" people_group_readonly)]"
  assert_eq "P08: … and the page says why (people_group_readonly)" "yes" "$(has_node "$d/work.xml" people_group_readonly)"
  assert_contains "P08: … naming the account" "qa.work@example.com" "$(xml_text "$d/work.xml" people_group_readonly)"
  # B19.4: a group deleted by another app while its page is open: the page closes with a notice
  group_page "$gid_d" "$d/doomed.xml"
  assert_eq "P08: the group's page is open" "yes" "$(has_node "$d/doomed.xml" "people_group:$gid_d")"
  q "content delete --uri '$GROUPS_URI/$gid_d?$SA'" >/dev/null; sleep 4
  dump_ui "$d/doomed-after.xml"; screencap "$d/doomed-after.png"
  log "P08: after the delete: page $(selected_page "$d/doomed-after.xml"); people_notice [$(xml_text "$d/doomed-after.xml" people_notice)]"
  assert_ne "P08: the deleted group's page closes (people_page:group is no longer the page on show)" "people_page:group" "$(selected_page "$d/doomed-after.xml")"
  assert_eq "P08: … with a notice" "yes" "$(has_node "$d/doomed-after.xml" people_notice)"
  assert_eq "P08: … and the list no longer shows it" "no" "$(has_node "$d/doomed-after.xml" "people_group:$gid_d")"
  back 1; back 1
  for g in $gid_e $gid_m $gid_n $gid_w; do [ -n "$g" ] && q "content delete --uri '$GROUPS_URI/$g?$SA'" >/dev/null; done
  _pe_end
}

# ---------------------------------------------------------------- P09: Share at its edges
edge_P09() {
  _pe_begin P09
  local d="$PE_D" raw lk mark uri imp copy handlers
  handlers="$(adb shell cmd package query-activities --brief -a android.intent.action.SEND -t text/x-vcard | tr -d '\r' | grep '/' | tr -d ' ' | tr '\n' ' ')"
  record "P09: B20.1 Share when no app receives text/x-vcard" "not produced on this AVD: it has receivers ($handlers); the empty state is Android's resolver's own"
  raw="$(people_add 'Photo Share' '+1 555 090 0001')"; sleep 2
  push_photo red >/dev/null
  give_photo "$raw" red "P09: Photo Share"
  lk="$(lookup_of "$(contact_of "$raw")")"
  card_of "$raw" "$d/card.xml"; tapid people_more 2
  mark="$(ring_mark)"
  tapid people_card_share 4
  uri="$(ring_since "$mark" | grep -F '[people] share' | tail -1 | sed -n 's/.*share [^ ]*: \(content:[^ ]*\).*/\1/p')"
  assert_eq "P09: the share line names the provider's vCard stream for her" "content://com.android.contacts/contacts/as_vcard/$lk" "$uri"
  back 2
  record "P09: adb shell content read on it (refused on this image; clauses-open.tsv, E15)" "$(adb shell content read --uri "$uri" 2>&1 | tr -d '\r' | head -2 | tr '\n' ' ' | cut -c1-160)"
  # the stream read back through the image's Contacts import: the contact it makes carries what the stream carried
  imp="$(adb shell cmd package query-activities --brief -a android.intent.action.VIEW -t text/x-vcard | tr -d '\r' | grep '^ *com.android.contacts/' | head -1 | tr -d ' ')"
  adb shell am start -W -n "$imp" -a android.intent.action.VIEW -d "$uri" -t text/x-vcard --grant-read-uri-permission > "$d/import-am.txt" 2>&1
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    copy="$(q "content query --uri $RAW --projection _id --where \"display_name='Photo Share' AND deleted=0 AND _id!=$raw\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n | tail -1)"
    [ -n "$copy" ] && break; sleep 2
  done
  assert_ne "P09: the stream was imported by the image's Contacts app" "" "$copy"
  [ -n "$copy" ] && echo "$copy" >> "$ROW_DIR/people-fixtures.ids"
  sleep 3
  assert_eq "P09: the vCard of a contact with a photo carries the photo (the imported contact holds a photo data row: PHOTO;ENCODING=b was in the stream)" "1" "$(photo_rows "${copy:-0}")"
  adb shell am force-stop com.android.contacts
  remove_photos
  _pe_end
}

# ---------------------------------------------------------------- P10: the People tile at its edges
edge_P10() {
  _pe_begin P10
  local d="$PE_D" ann bob la lb mark slice sizes s nb b tb frac k cur other n line
  declare -A col
  layout_restore "$BASELINE" >/dev/null 2>&1; ensure_start
  ann="$(people_add 'Tile Ann')"; bob="$(people_add 'Tile Bob')"; sleep 2
  push_photo red >/dev/null; push_photo green >/dev/null
  give_photo "$ann" red "P10: Tile Ann"; give_photo "$bob" green "P10: Tile Bob"
  la="$(lookup_of "$(contact_of "$ann")")"; lb="$(lookup_of "$(contact_of "$bob")")"; col[$la]="${PHOTO_RGB[red]}"; col[$lb]="${PHOTO_RGB[green]}"
  # B23.1: the People feed restarts with the shell after a force-stop
  ring_save; mark="$(ring_mark)"
  adb shell am force-stop app.tileshell; sleep 1; ensure_start
  for _ in $(seq 1 30); do slice="$(ring_since "$mark")"; printf '%s' "$slice" | grep -qF '[people] tile event' && break; sleep 0.5; done
  assert_contains "P10: after a force-stop the feed is back with the shell ([people] tile: 2 photos)" "[people] tile: 2 photos" "$slice"
  assert_contains "P10: … and the tile cycles again (a tile event)" "[people] tile event" "$slice"
  # B21.4: the tile small / medium / wide — the face scales with the tile
  for s in SMALL MEDIUM WIDE; do
    python3 - "$BASELINE" "$d/layout-$s.json" "$s" <<'PY'
import json, sys
dd = json.load(open(sys.argv[1]))
for o in dd["order"]:
    if o["key"] == "slot:PEOPLE": o["size"] = sys.argv[3]
json.dump(dd, open(sys.argv[2], "w"))
PY
    ring_save
    layout_restore "$d/layout-$s.json" >/dev/null 2>&1; ensure_start; sleep 3
    for _ in 1 2 3 4 5 6 7 8; do gdump "$d/tile-$s.xml" || true; nb="$(node_inside "$d/tile-$s.xml" "$PEOPLE_SLOT_TILE" people_tile_bubble)"; [ -n "$nb" ] && break; sleep 1; done
    screencap "$d/tile-$s.png"
    tb="$(bounds "$d/tile-$s.xml" "$PEOPLE_SLOT_TILE")"; b="${nb%%|*}"
    assert_ne "P10: $s tile: the People face is drawn (people_tile_face)" "" "$(node_inside "$d/tile-$s.xml" "$PEOPLE_SLOT_TILE" people_tile_face)"
    frac="$(python3 -c "
import sys
t=[int(v) for v in sys.argv[1].split()]; b=[int(v) for v in sys.argv[2].split()] if sys.argv[2] else None
if not b or len(t)!=4: print('none'); sys.exit()
tw,th=t[2]-t[0],t[3]-t[1]; bw,bh=b[2]-b[0],b[3]-b[1]
print('tile %dx%d bubble %dx%d fraction-of-short-side %.3f centred-x %s' % (tw,th,bw,bh,bw/min(tw,th),'yes' if abs((b[0]+b[2])-(t[0]+t[2]))<=2 else 'no'))" "$tb" "$b")"
    record "P10: $s tile" "$frac"
    assert_contains "P10: $s tile: a settled bubble, centred across the tile" "centred-x yes" "$frac"
    assert_eq "P10: $s tile: the bubble is a circle's box (as wide as tall, ± 1 px)" "yes" "$(python3 -c "
import sys
b=[int(v) for v in sys.argv[1].split()] if sys.argv[1] else [0,0,9,0]
print('yes' if abs((b[2]-b[0])-(b[3]-b[1]))<=1 else 'no')" "$b")"
    eval "FR_$s=\"$(printf '%s' "$frac" | sed -n 's/.*fraction-of-short-side \([0-9.]*\).*/\1/p')\""
  done
  assert_eq "P10: the bubble is the same fraction of the tile's short side on a medium and a wide tile (the face scales with the tile)" "$FR_MEDIUM" "$FR_WIDE"
  assert_eq "P10: … and a larger fraction on a small tile, which carries no label" "yes" "$(python3 -c "import sys; print('yes' if float(sys.argv[1]) > float(sys.argv[2]) else 'no')" "${FR_SMALL:-0}" "${FR_MEDIUM:-1}")"
  ring_save
  layout_restore "$BASELINE" >/dev/null 2>&1; ensure_start; sleep 2
  # B21.1: a photo removed while its bubble is on screen — the current bubble finishes, the next event uses another photo
  mark="$(ring_mark)"
  for _ in $(seq 1 30); do [ "$(ring_since "$mark" | grep -c 'people_bubble_in')" -ge 1 ] && break; sleep 0.5; done
  cur="$(ring_since "$mark" | grep -F '[people] tile event' | tail -1 | sed -E 's/.*lookup=([^ ]+).*/\1/')"
  if [ "$cur" = "$la" ]; then k="$ann"; other="$lb"; else k="$bob"; other="$la"; fi
  note "P10: the bubble on screen is lookup=$cur (raw $k); the other contact is $other"
  mark="$(ring_mark)"
  q "content delete --uri $DATA --where \"mimetype='vnd.android.cursor.item/photo' AND raw_contact_id=$k\"" >/dev/null
  sleep 1.5; gdump "$d/removed-now.xml" || true; screencap "$d/removed-now.png"
  nb="$(node_inside "$d/removed-now.xml" "$PEOPLE_SLOT_TILE" people_tile_bubble)"
  assert_eq "P10: its photo removed while its bubble is on screen: the current bubble finishes its turn (still drawn)" "$cur" "${nb#*|}"
  for _ in $(seq 1 30); do [ "$(ring_since "$mark" | grep -c 'people_bubble_in')" -ge 1 ] && break; sleep 0.5; done
  line="$(ring_since "$mark" | grep -F '[people] tile event' | head -1 | sed 's/.*\[people\]/[people]/')"; log "P10: $line"
  assert_contains "P10: … and the next event uses the other contact's photo" "lookup=$other" "$line"
  screencap "$d/next.png"; gdump "$d/next.xml" || true
  nb="$(node_inside "$d/next.xml" "$PEOPLE_SLOT_TILE" people_tile_bubble)"
  assert_color "P10: … whose colour the settled bubble shows" "${col[$other]}" "$(centre_px "$d/next.png" "${nb%%|*}")" 8
  # B21.2: every photo removed mid-cycle: the static pattern after the current event
  mark="$(ring_mark)"
  q "content delete --uri $DATA --where \"mimetype='vnd.android.cursor.item/photo' AND raw_contact_id IN ($ann,$bob)\"" >/dev/null
  for _ in $(seq 1 24); do ring_since "$mark" | grep -qF '[people] tile: 0 photos' && break; sleep 0.5; done
  assert_contains "P10: every photo removed: [people] tile: 0 photos" "[people] tile: 0 photos" "$(ring_since "$mark")"
  sleep 3; gdump "$d/pattern.xml" || true; screencap "$d/pattern.png"
  assert_ne "P10: … the static pattern is drawn (people_tile_pattern)" "" "$(node_inside "$d/pattern.xml" "$PEOPLE_SLOT_TILE" people_tile_pattern)"
  sleep 9
  absent_in "P10: … and no tile event follows the 0-photos read in the next 12 s (more than one period)" "[people] tile event" "$(ring_since "$mark" | sed -n '/tile: 0 photos/,$p')"
  remove_photos
  ring_save
  layout_restore "$BASELINE" >/dev/null 2>&1
  _pe_end
}

# ---------------------------------------------------------------- the system's record of an activity start
# `dumpsys activity activities` shows an intent only while the activity it started is alive. The Fossify Messages
# fixture receives ACTION_SENDTO in a trampoline activity (NewConversationActivity) that forwards to its thread page and
# finishes, so a dump taken seconds later no longer holds the smsto: intent (E27 run 1). The system's own log line of
# the start — ActivityTaskManager's "START u0 {act=… dat=… cmp=…} … from uid <uid>" — is the same fact from the same
# source (the activity manager), with the same redaction of an smsto: URI, and it does not expire with the trampoline.
dev_time() { adb shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r'; }
starts_since() { # "MM-DD HH:MM:SS.mmm" (dev_time, taken before the action)
  adb logcat -d -t "$1" ActivityTaskManager:I '*:S' 2>/dev/null | tr -d '\r' | grep -F 'START u0'
}
shell_uid() { adb shell cmd package list packages -U app.tileshell 2>/dev/null | tr -d '\r' | sed -n 's/^package:app.tileshell uid://p' | head -1; }
