#!/usr/bin/env bash
# Phase 16 People builder: what every dev-people driver shares. Sourced AFTER lib.sh (the symlink beside this file).
# These are development proof, not the gate: the lead writes and runs E1–E28 and EDGE from the phase doc.
#
# lib.sh works its paths out from where it is sourced; this folder sits one level deeper than a phase's scripts folder,
# so the repo root and the APK are set here from git.
export ANDROID_SERIAL=emulator-5554
REPO="$(git -C "$HERE" rev-parse --show-toplevel)"
APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"
DRV_RUNNER="app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner"
PEOPLE=app.tileshell/.people.PeopleActivity

S() { adb shell "$@" | tr -d '\r'; }

# The device is shared with another builder who installs a different build of this package: install ours only when the
# device holds another one. Never with -g; a permission a session needs is granted with pm grant and said so.
ensure_build() {
  if [ "$(apk_matches | cut -c1-3)" = "yes" ]; then
    echo "device already holds this build"
  else
    adb install -r "$APK" 2>&1 | tail -1
    sleep 5
  fi
}

top_activity() { S dumpsys activity activities | grep -m1 'topResumedActivity' | sed -E 's/.* u0 ([^ ]+) .*/\1/'; }
perm() { S dumpsys package app.tileshell | grep -m1 "android.permission.$1: granted" | sed -E 's/.*granted=([a-z]+).*/\1/'; }

# The phase 05 gesture driver's dump, for a window that never idles (p15.sh's gdump, copied: it cannot be sourced
# without p15's own state).
gdump() { # out.xml
  local out="$1" i name
  : > "$out.drv"
  for i in $(seq 1 20); do
    name="people_$(date +%s%N)_$i.xml"
    adb shell am instrument -r -w -e op dump -e out "/sdcard/Download/$name" "$DRV_RUNNER" >> "$out.drv" 2>&1
    adb shell cat "/sdcard/Download/$name" > "$out" 2>/dev/null
    adb shell rm -f "/sdcard/Download/$name" >/dev/null 2>&1
    if grep -q '<node' "$out"; then return 0; fi
    echo "(attempt $i read no nodes from $name)" >> "$out.drv"
    sleep 0.25
  done
  echo "(dump failed)" > "$out"
  return 1
}

# ---------------------------------------------------------------- contact fixtures
# Inserted as provision.sh inserts Mom (content insert on raw_contacts, then data), their raw-contact ids kept in
# $ROW_DIR/people-fixtures.ids and deleted BY ID — never by account, so provision's Mom (qa / qa) is never touched.

FIX_IDS=""
_fix_file() { echo "$ROW_DIR/people-fixtures.ids"; }

# A new raw contact; prints its _id. With no arguments it is phone-only (NULL account name and type).
fx_raw() { # [account_name account_type]
  local before after
  before="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id | grep -oE '_id=[0-9]+' | cut -d= -f2 | sort -n | tail -1)"
  if [ -n "${1:-}" ]; then
    adb shell content insert --uri content://com.android.contacts/raw_contacts --bind account_name:s:"$1" --bind account_type:s:"$2" >/dev/null 2>&1
  else
    adb shell content insert --uri content://com.android.contacts/raw_contacts --bind account_name:n: --bind account_type:n: >/dev/null 2>&1
  fi
  after="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id | grep -oE '_id=[0-9]+' | cut -d= -f2 | sort -n | tail -1)"
  if [ -z "$after" ] || [ "$after" = "${before:-0}" ]; then echo "fx_raw: no new raw contact" >&2; return 1; fi
  echo "$after" >> "$(_fix_file)"
  echo "$after"
}

fx_data() { # raw mimetype data1 [data2]
  local raw="$1" mime="$2" d1="$3" d2="${4:-}"
  if [ -n "$d2" ]; then
    adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$raw --bind mimetype:s:$mime --bind data1:s:'$d1' --bind data2:i:$d2" >/dev/null 2>&1
  else
    adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$raw --bind mimetype:s:$mime --bind data1:s:'$d1'" >/dev/null 2>&1
  fi
}

fx_name() { fx_data "$1" vnd.android.cursor.item/name "$2"; }
fx_phone() { fx_data "$1" vnd.android.cursor.item/phone_v2 "$2" "${3:-2}"; }   # type 2 = mobile
fx_email() { fx_data "$1" vnd.android.cursor.item/email_v2 "$2" "${3:-1}"; }   # type 1 = home ("Personal")

# The contact a raw contact belongs to, and that contact's lookup key.
contact_of() { S content query --uri content://com.android.contacts/raw_contacts --projection contact_id --where "_id=$1" | grep -oE 'contact_id=[0-9]+' | cut -d= -f2; }
lookup_of() { S content query --uri content://com.android.contacts/contacts --projection lookup --where "_id=$1" | sed -n 's/.*lookup=//p' | head -1; }

# Every fixture this row inserted (and anything a step added to the ids file), deleted by id through the sync-adapter
# URI, so a raw contact under an account with no sync adapter is removed and not left marked deleted.
people_fixtures_down() {
  local f id
  f="$(_fix_file)"
  [ -f "$f" ] || return 0
  for id in $(sort -un "$f"); do
    adb shell "content delete --uri 'content://com.android.contacts/raw_contacts/$id?caller_is_syncadapter=true'" >/dev/null 2>&1
  done
  mv "$f" "$f.done-$(date +%s)"
}

raw_count() { S content query --uri content://com.android.contacts/raw_contacts --projection _id | grep -c '_id='; }

# Open People by component and wait for it.
open_people() { adb shell am start -W -n "$PEOPLE" "$@" >/dev/null 2>&1; sleep 2; }

# Tap the centre of a node, read from a fresh dump.
tap() { # resource-id
  local d="$ROW_DIR/.tap.xml"
  dump_ui "$d" || true
  tap_node "$d" "$1"
}

# Type into the focused field through the shell's keyboard (adb input text; %s is a space).
type_text() { adb shell input text "$(printf '%s' "$1" | sed 's/ /%s/g')"; sleep 1; }

# ---------------------------------------------------------------- measuring and waiting
INK="$REPO/docs/plan/qa/phase-15/scripts/ink.py"      # ink boxes off a screencap (phase 15's tool, read where it lives)
PXE=3                                                 # the AVD: 1080 px across a 360-epx canvas

# The layout helpers of phase 02 (layout_json, layout_save, layout_restore), for a row that points a slot somewhere.
. "$REPO/docs/plan/qa/phase-02/scripts/layout.sh"

# A pixel of a screencap as "r,g,b".
px() { python3 -c "
from PIL import Image; import sys
print('%d,%d,%d' % Image.open(sys.argv[1]).convert('RGB').getpixel((int(sys.argv[2]), int(sys.argv[3])))[:3])" "$1" "$2" "$3"; }

# The colour most pixels of a rectangle have, leaving out the page's own fill ("r,g,b"), as "r,g,b".
ink_color() { # png l t r b fill
python3 - "$@" <<'PY'
import sys
from PIL import Image
png, l, t, r, b, fill = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), tuple(int(v) for v in sys.argv[6].split(','))
import numpy as np
a = np.asarray(Image.open(png).convert('RGB').crop((l, t, r, b))).astype(int).reshape(-1, 3)
# The ink's own colour is the furthest any pixel gets from the fill (anti-aliased edges sit in between).
far = np.abs(a - np.array(fill)).sum(axis=1)
print('%d,%d,%d' % tuple(a[int(far.argmax())]) if len(a) else '')
PY
}

assert_color() { # name expected "r,g,b" actual "r,g,b" tolerance
  local ok
  ok="$(python3 -c "
import sys
e=[int(v) for v in sys.argv[1].split(',')]; a=[int(v) for v in sys.argv[2].split(',')] if sys.argv[2] else None
print('yes' if a and all(abs(e[i]-a[i])<=int(sys.argv[3]) for i in range(3)) else 'no')" "$2" "$3" "$4")"
  if [ "$ok" = yes ]; then _verdict PASS "$1" "($3) within $4 of ($2)"; else _verdict FAIL "$1" "expected ($2) ± $4, got ($3)"; fi
}

# Dump until a node is there (or the seconds run out); the dump is left in the given file.
wait_node() { # out.xml resource-id [seconds]
  local out="$1" id="$2" n="${3:-8}" i
  for i in $(seq 1 "$n"); do
    dump_ui "$out" || true
    [ "$(has_node "$out" "$id")" = yes ] && return 0
    sleep 1
  done
  return 1
}

# One field of a bounds string ("l t r b"), and a px value in epx.
bfield() { echo "$1" | cut -d' ' -f"$2"; }
epx() { python3 -c "import sys; print('%.2f' % (float(sys.argv[1]) / $PXE))" "$1"; }

# The drawn status bar's bottom edge in px (C-17: read from the dump, never written as a number).
status_bottom() { bfield "$(bounds "$1" w10m_status_bar)" 4; }

# ---------------------------------------------------------------- driving the editor
FIXTURES="${PEOPLE_FIXTURES:-/tmp/claude-1000/-home-jeremyking/94273004-6013-4029-a961-ffb26e4dcb1f/scratchpad}"

# Replace a field's text: tap it, delete what it holds (the count is read from the dump), type the new value, close the keyboard.
set_field() { # resource-id text
  local d="$ROW_DIR/.field.xml" n
  dump_ui "$d" || true
  n="$(node_text "$d" "$1")"; n="${#n}"
  tap_node "$d" "$1" || return 2
  sleep 1
  adb shell input keyevent KEYCODE_MOVE_END
  if [ "$n" -gt 0 ]; then adb shell input keyevent $(printf '67 %.0s' $(seq 1 "$n")); fi
  type_text "$2"
  adb shell input keyevent KEYCODE_BACK   # the keyboard; the editor stays
  sleep 1
}

# Push a solid-colour JPEG where Android's photo picker lists it. Prints the device path.
push_photo() { # colour
  adb shell mkdir -p /sdcard/Pictures >/dev/null 2>&1
  adb push "$FIXTURES/people-$1.jpg" "/sdcard/Pictures/people-$1.jpg" >/dev/null
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
  sleep 2
  echo "/sdcard/Pictures/people-$1.jpg"
}
remove_photos() {
  adb shell rm -f /sdcard/Pictures/people-red.jpg /sdcard/Pictures/people-green.jpg /sdcard/Pictures/people-blue.jpg
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
}

# In Android's photo picker, choose the picture whose colour is given: the picker's grid cell whose centre pixel is that colour.
pick_photo() { # "r,g,b"
  local d="$ROW_DIR/.picker.xml" p="$ROW_DIR/.picker.png" xy
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
  # A picker that asks for a confirmation after the tap: its button is tapped when it is there.
  dump_ui "$d" || true
  local add; add="$(python3 - "$d" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if re.search(r'text="(Add|Done|Select)[^"]*"', s) and 'com.android' in s:
        m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        l, t, r, b = (int(v) for v in m.groups()); print((l + r) // 2, (t + b) // 2); break
PY
)"
  # shellcheck disable=SC2086
  if [ -n "$add" ]; then adb shell input tap $add; sleep 3; fi
}
