#!/usr/bin/env bash
# Phase 18 E10 — app list, surface, budget; the row also carries build task 6's regressions (the app-list regression in
# phase 02's regress.sh pattern, exported.py against the allow-list, the APK's size, the baseline assertion).
#
#   child      phase 02's regress.sh on this build, seeded with THE PHASE BASELINE (REGRESS_BASELINE =
#              qa/phase-18/baseline_layout.json — the doc: "passes from the phase baseline"): a grid-tile tap and a
#              bottom-row tap still launch, the pivot still swipes, Start still scrolls, a short press is a tap, Home
#              and the drawn Windows key still work — `<n> passed, 0 failed`, rc 0 — with zero assignSlotOnce `->
#              assigned` lines in the ring slice across its restores (C-3). It takes no device lock itself, so it runs
#              while this driver holds the device, before row_begin (phase 17 E2's form).
#   baseline   `baseline_start` (layout_restore of the phase baseline, zero `-> assigned` lines), and the shell's own
#              file after it came up: `addedOnce` equal to the baseline file's (C-3).
#   list       "Files" under F: from the jump grid's F cell the list is walked to the Files row — the last header above it
#              is F, its name reads "Files", its row is tagged applist_row:app.tileshell; no "New" caption (no
#              applist_new:app.tileshell node in the row's dump, and phase 01 E12's geometry rule: the name sits where
#              an uncaptioned row's name in the same dump sits, ± 1.4 epx).
#   menu       the row held: `applist_menu`, the shell's `[applist] context menu on app.tileshell/.files.FilesActivity`,
#              Pin to Start offered and NO Uninstall; qa/phase-01/UNINSTALL's other half — an ordinary app's row (Notes)
#              held the same way DOES offer Uninstall (the bracket: the absence can fail). The item is read, never
#              tapped. Then `c6` (C-6).
#   exported   qa/phase-03/scripts/exported.py <apk> qa/phase-03/exported-allowlist.txt (r3 V20): rc 0, nothing
#              exported that is not allowed, nothing on the list that is not exported; the list carries this phase's
#              ADD, app.tileshell.files.FilesActivity; read from the APK's own merged manifest (aapt2): the copy service
#              FileOpsService is exported=false with foregroundServiceType dataSync; FilesProvider is a provider with
#              exported=false and grantUriPermissions=true and is NOT an allow-list entry (T18-5); the device holds the
#              APK that was checked (md5), which makes the APK's surface the device's.
#   size       stat -c%s app-debug.apk ≤ 629,145,600 bytes.
#
# E10_CHILD=0 skips the child (driver development; the gate never sets it).
# Changes on the device: the Start layout (the child's restores, then the phase baseline). The child launches other
# apps and force-stops the shell through layout_restore; nothing is wiped or uninstalled.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/p18_a.sh"
P03S="$(cd "$P18/../phase-03/scripts" && pwd)"
ALLOW="$P18/../phase-03/exported-allowlist.txt"
AAPT2="$(ls "$HOME"/Android/Sdk/build-tools/*/aapt2 | sort | tail -1)"
MAX_APK=629145600
EPX=3
FILES_NAME="app.tileshell/.files.FilesActivity"
NOTES="org.fossify.notes/.activities.SplashActivity.Green"
keep_earlier_run E10
OUT="$P18/E10"; mkdir -p "$OUT"

# ---- helpers (phase 17 E2's, cut to what this row reads)
open_applist() { ensure_start; adb shell input swipe 900 1200 150 1200 250; sleep 2; }
open_jump_grid() { # out.xml — the list, its first header on screen tapped
  local h i
  open_applist
  for i in 1 2 3 4 5; do
    dump_ui "$ROW_DIR/.nav.xml"
    h="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/.nav.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
    [ -n "$h" ] && break
    adb shell input swipe 540 800 540 1700 300; sleep 1
  done
  tap_node "$ROW_DIR/.nav.xml" "$h"; sleep 1.5
  dump_ui "$1"
}
goto_letter() { open_jump_grid "$ROW_DIR/.grid.xml"; tap_node "$ROW_DIR/.grid.xml" "jump_cell:$1"; sleep 1.5; }
settled() { # out.xml resource-id — dumped twice in the same place = it has stopped moving; prints its bounds
  local b="" prevb="" i
  for i in 1 2 3 4 5 6; do
    b="$(bounds "$1" "$2")"
    [ -n "$b" ] && [ "$b" = "$prevb" ] && break
    prevb="$b"; sleep 0.7; dump_ui "$1"
  done
  echo "$b"
}
hold_at() { local x1 y1 x2 y2; read -r x1 y1 x2 y2 <<< "$1"; adb shell input swipe $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) 1000; sleep 1.5; }
# One step of a walk: the list's headers and names of this dump not seen yet, top to bottom: kind<TAB>id<TAB>text<TAB>row tag.
walk_step() { # dump.xml order.txt
  python3 - "$1" "$2" <<'PY'
import html, re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
seen = set(tuple(l.split("\t")[:2]) for l in open(sys.argv[2], encoding="utf-8").read().split("\n") if l)
rows, items = [], []
for n in re.finditer(r"<node[^>]*>", s):
    n = n.group(0)
    rid = re.search(r'resource-id="([^"]*)"', n); b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', n)
    if not rid or not b: continue
    rid = rid.group(1); x1, y1, x2, y2 = map(int, b.groups())
    t = re.search(r'text="([^"]*)"', n); text = html.unescape(t.group(1)) if t else ""
    if re.match(r"applist_(?:[a-z]+_)?row:", rid): rows.append((rid, x1, y1, x2, y2))
    m = re.match(r"applist_(header|name|new):(.*)$", rid)
    if m: items.append((y1, m.group(1), m.group(2), text, (x1 + x2) // 2, (y1 + y2) // 2))
with open(sys.argv[2], "a", encoding="utf-8") as o:
    for y, kind, ident, text, cx, cy in sorted(items):
        row = next((r[0] for r in rows if r[1] <= cx <= r[3] and r[2] <= cy <= r[4]), "")
        if (kind, ident) not in seen:
            seen.add((kind, ident)); o.write("\t".join([kind, ident, text, row]) + "\n")
PY
}
# The tag of the row node holding a node, the node's centre minus the row's centre (px), the row's height.
row_of() { # dump.xml resource-id
  python3 - "$1" "$2" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
def nodes():
    for n in re.finditer(r"<node[^>]*>", s):
        n = n.group(0)
        rid = re.search(r'resource-id="([^"]*)"', n); b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', n)
        if rid and b: yield rid.group(1), tuple(map(int, b.groups()))
t = next((b for r, b in nodes() if r == sys.argv[2]), None)
if t:
    cx, cy = (t[0] + t[2]) // 2, (t[1] + t[3]) // 2
    for r, b in nodes():
        if re.match(r"applist_(?:[a-z]+_)?row:", r) and b[0] <= cx <= b[2] and b[1] <= cy <= b[3]:
            print("%s\t%.1f\t%d" % (r, (t[1] + t[3]) / 2 - (b[1] + b[3]) / 2, b[3] - b[1])); break
PY
}
# The same offset for an uncaptioned row of ANOTHER app in the dump, whole on screen: "<name tag>\t<offset px>".
control_offset() { # dump.xml the-row's-name-tag
  python3 - "$1" "$2" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
rows, names, news = [], [], []
for n in re.finditer(r"<node[^>]*>", s):
    n = n.group(0)
    rid = re.search(r'resource-id="([^"]*)"', n); b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', n)
    if not rid or not b: continue
    rid = rid.group(1); b = tuple(map(int, b.groups()))
    if rid.startswith("applist_row:"): rows.append((rid, b))
    elif rid.startswith("applist_name:"): names.append((rid, b))
    elif rid.startswith("applist_new:"): news.append(b)
full = max((b[3] - b[1] for _, b in rows), default=0)
for rid, b in rows:
    if rid == "applist_row:app.tileshell" or b[3] - b[1] != full: continue
    inside = lambda q: b[0] <= (q[0] + q[2]) // 2 <= b[2] and b[1] <= (q[1] + q[3]) // 2 <= b[3]
    if any(inside(q) for q in news): continue
    for nrid, nb in names:
        if nrid != sys.argv[2] and inside(nb):
            print("%s\t%.1f" % (nrid, (nb[1] + nb[3]) / 2 - (b[1] + b[3]) / 2)); sys.exit(0)
PY
}
# One component of the APK's merged manifest: "<element>|exported=<v>|grantUriPermissions=<v>|foregroundServiceType=<v>".
component() { # xmltree.txt fully.qualified.Name
  python3 - "$1" "$2" <<'PY'
import re, sys
kind, indent, attrs, out = None, -1, {}, None
def flush():
    global out
    if kind and attrs.get("name") == sys.argv[2]:
        out = "%s|exported=%s|grantUriPermissions=%s|foregroundServiceType=%s" % (kind, attrs.get("exported", "unset"), attrs.get("grantUriPermissions", "unset"), attrs.get("foregroundServiceType", "unset"))
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    ind = len(line) - len(line.lstrip(" ")); text = line.strip()
    if text.startswith("E: "):
        name = text[3:].split(" ", 1)[0]
        if name in ("activity", "activity-alias", "service", "receiver", "provider"):
            flush(); kind, indent, attrs = name, ind, {}
        elif ind <= indent:
            flush(); kind, indent, attrs = None, -1, {}
    elif text.startswith("A: ") and kind and ind == indent + 2:
        m = re.match(r'A: (?:http://schemas\.android\.com/apk/res/android:)?([A-Za-z_]+)(?:\(0x[0-9a-f]+\))?=(?:\(type 0x[0-9a-f]+\))?(?:"([^"]*)"|(\S+))', text)
        if m: attrs[m.group(1)] = m.group(2) if m.group(2) is not None else m.group(3)
flush()
print(out or "")
PY
}

take_device_lock
# =============================================================================================== the child
if [ "${E10_CHILD:-1}" = "1" ]; then
  adb shell input keyevent KEYCODE_HOME; sleep 2
  R_MARK="$(ring_mark)"
  ( REGRESS_BASELINE="$BASELINE" bash "$P02S/regress.sh" "$OUT/p02-REGRESS" > "$OUT/p02-regress.out" 2>&1; echo $? > "$OUT/p02-regress.rc" )
  ring_since "$R_MARK" > "$OUT/p02-regress.ring.txt" 2>/dev/null
  adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 5
fi

# =============================================================================================== the row
flock -u 9 2>/dev/null; exec 9>&-          # row_begin takes the lock itself
row_begin E10 "app list, surface, budget — with build task 6's regressions"
LC0="$(lc_mark)"
record "this build (debug)" "$(md5sum "$APK" | cut -c1-16) $(stat -c%s "$APK") bytes ($(git -C "$REPO" rev-parse --short HEAD))"
assert_contains "the device holds this build" "yes" "$(apk_matches)"

if [ "${E10_CHILD:-1}" = "1" ]; then
  log "--- child: phase 02's regress.sh on this build, from the phase baseline"
  R="$OUT/p02-REGRESS/REGRESS.txt"
  assert_eq "child: regress.sh exits 0" "0" "$(cat "$OUT/p02-regress.rc")"
  R_SUM="$(grep -E '^[0-9]+ passed, [0-9]+ failed' "$R" 2>/dev/null | tail -1)"
  record "child: regress.sh's own summary" "$R_SUM"
  assert_contains "child: regress.sh: 0 failed" " passed, 0 failed" "$R_SUM"
  for l in "a tile tap launched another app" "a row tile tap launched another app" "swipe opened the app list" "Back returned to Start" "a swipe scrolled the grid" "the Windows key brought the pivot back to Start"; do
    assert_contains "child: $l" "PASS  $l" "$(cat "$R" 2>/dev/null)"
  done
  assert_ne "child: its slice covers a restore (assignSlotOnce lines in it)" "0" "$(grep -cF 'assignSlotOnce' "$OUT/p02-regress.ring.txt")"
  assert_eq "child: zero assignSlotOnce -> assigned after its restores (C-3)" "0" "$(grep -F 'assignSlotOnce' "$OUT/p02-regress.ring.txt" | grep -cF -- '-> assigned')"
else
  record "child" "SKIPPED (E10_CHILD=0): a development run, not the gate's"
fi

# ----------------------------------------------------------------------------------------------- the baseline
log "--- the baseline assertion (C-3)"
baseline_start
layout_save "$ROW_DIR/layout-after-restore.json"
ONCE() { python3 -c "import json, sys; print(' '.join(sorted(json.load(open(sys.argv[1])).get('addedOnce', []))))" "$1"; }
record "the baseline file's addedOnce" "$(ONCE "$BASELINE")"
assert_ne "the baseline file carries addedOnce markers" "" "$(ONCE "$BASELINE")"
assert_eq "after layout_restore the shell's own file holds addedOnce equal to the baseline file's" "$(ONCE "$BASELINE")" "$(ONCE "$ROW_DIR/layout-after-restore.json")"
assert_eq "…and the Files tile is on Start (the baseline pins it)" "yes" "$(dump_ui "$ROW_DIR/start.xml"; scroll_to_node "$ROW_DIR/start.xml" "$FILES_TILE" 4 >/dev/null; has_node "$ROW_DIR/start.xml" "$FILES_TILE")"

# ----------------------------------------------------------------------------------------------- the app list
log "--- the app list: Files under F"
goto_letter F
: > "$ROW_DIR/walk_order.txt"
for i in 1 2 3 4 5; do
  dump_ui "$ROW_DIR/walk-$i.xml"
  walk_step "$ROW_DIR/walk-$i.xml" "$ROW_DIR/walk_order.txt"
  grep -q "^name	$FILES_NAME	" "$ROW_DIR/walk_order.txt" && break
  adb shell input swipe 540 1700 540 1100 1200; sleep 1.2
done
note "the walk from the jump to F: $(cut -f1-3 "$ROW_DIR/walk_order.txt" | tr '\t' ':' | tr '\n' ' ' | cut -c1-500)"
GROUP="$(awk -F'\t' -v n="$FILES_NAME" '$1 == "header" { h = $2 } $1 == "name" && $2 == n { print h; exit }' "$ROW_DIR/walk_order.txt")"
assert_eq "the walk starts at the F header (the jump landed on it)" "header	F" "$(head -1 "$ROW_DIR/walk_order.txt" | cut -f1,2)"
assert_eq "Files sits under F (the last header above its row)" "F" "$GROUP"
assert_eq "…its name reads" "Files" "$(awk -F'\t' -v n="$FILES_NAME" '$1 == "name" && $2 == n { print $3; exit }' "$ROW_DIR/walk_order.txt")"
assert_eq "…in a row tagged applist_row:app.tileshell" "applist_row:app.tileshell" "$(awk -F'\t' -v n="$FILES_NAME" '$1 == "name" && $2 == n { print $4; exit }' "$ROW_DIR/walk_order.txt")"
record "the F group as walked (names)" "$(awk -F'\t' '$1 == "header" { h = $2 } $1 == "name" && h == "F" { printf "%s; ", $3 }' "$ROW_DIR/walk_order.txt")"
goto_letter F
scroll_to_node "$ROW_DIR/row_files.xml" "applist_name:$FILES_NAME" 3
BF="$(settled "$ROW_DIR/row_files.xml" "applist_name:$FILES_NAME")"
screencap "$ROW_DIR/row_files.png"
assert_ne "Files' row is on screen after the jump to F" "" "$BF"
IFS=$'\t' read -r rtag off rh <<< "$(row_of "$ROW_DIR/row_files.xml" "applist_name:$FILES_NAME")"
IFS=$'\t' read -r ctag coff <<< "$(control_offset "$ROW_DIR/row_files.xml" "applist_name:$FILES_NAME")"
note "Files: row $rtag ${rh:-?} px high, name centre ${off:-?} px from the row's centre; control ${ctag:-none} ${coff:-?} px"
assert_eq "no New caption: no applist_new:app.tileshell node in the row's dump" "no" "$(has_node "$ROW_DIR/row_files.xml" applist_new:app.tileshell)"
assert_ne "no New caption: an uncaptioned row of another app is in the same dump (the control)" "" "${ctag:-}"
assert_within "no New caption: Files' name sits where an uncaptioned row's does (± 1.4 epx; a caption moves it 9.75 epx)" "${coff:-x}" "${off:-x}" "$(python3 -c "print(1.4 * $EPX)")"
record "New captions in this dump (rows of other apps)" "$(grep -o 'resource-id="applist_new:[^"]*"' "$ROW_DIR/row_files.xml" | sed 's/resource-id="applist_new://; s/"$//' | xargs)"

log "--- the hold menu: Pin to Start and no Uninstall"
MARK="$(ring_mark)"
hold_at "$BF"
dump_ui "$ROW_DIR/menu_files.xml"; screencap "$ROW_DIR/menu_files.png"
assert_eq "holding Files' row opens the menu (applist_menu)" "yes" "$(has_node "$ROW_DIR/menu_files.xml" applist_menu)"
assert_contains "…on this app ([applist] context menu on …)" "[applist] context menu on $FILES_NAME" "$(ring_since "$MARK")"
assert_eq "the menu offers Pin to Start" "yes" "$(has_node "$ROW_DIR/menu_files.xml" applist_menu_pin)"
assert_eq "the menu does NOT offer Uninstall" "no" "$(has_node "$ROW_DIR/menu_files.xml" applist_menu_uninstall)"
record "the menu's items" "$(grep -o 'resource-id="applist_menu[^"]*"' "$ROW_DIR/menu_files.xml" | sed 's/resource-id="//; s/"$//' | xargs)"
adb shell input keyevent KEYCODE_BACK; sleep 1
# The other half of the bracket (qa/phase-01/UNINSTALL's method): an ordinary app's menu does carry the item.
assert_contains "the control app (Notes) is installed" "package:org.fossify.notes" "$(adb shell pm list packages org.fossify.notes | tr -d '\r')"
goto_letter N
scroll_to_node "$ROW_DIR/row_notes.xml" "applist_name:$NOTES" 3
BN="$(settled "$ROW_DIR/row_notes.xml" "applist_name:$NOTES")"
assert_ne "control: Notes' row is on screen after the jump to N" "" "$BN"
MARK="$(ring_mark)"
hold_at "$BN"
dump_ui "$ROW_DIR/menu_notes.xml"; screencap "$ROW_DIR/menu_notes.png"
assert_eq "control: holding Notes' row opens the menu" "yes" "$(has_node "$ROW_DIR/menu_notes.xml" applist_menu)"
assert_contains "control: …on Notes" "[applist] context menu on $NOTES" "$(ring_since "$MARK")"
assert_eq "control: an ordinary app's menu offers Pin to Start AND Uninstall (so the absence above is read, not missed)" "yes yes" "$(has_node "$ROW_DIR/menu_notes.xml" applist_menu_pin) $(has_node "$ROW_DIR/menu_notes.xml" applist_menu_uninstall)"
adb shell input keyevent KEYCODE_BACK; sleep 1
assert_contains "control: Notes is still installed (the item was never tapped)" "package:org.fossify.notes" "$(adb shell pm list packages org.fossify.notes | tr -d '\r')"
c6; ensure_start      # C-6: after the hold menu, before anything reads Start's grid again
dump_ui "$ROW_DIR/start-after.xml"
assert_eq "after c6: Start alone, the Files tile still pinned once" "yes 1" "$(has_node "$ROW_DIR/start-after.xml" start_page) $(scroll_to_node "$ROW_DIR/start-after.xml" "$FILES_TILE" 4 >/dev/null; grep -o "resource-id=\"$FILES_TILE\"" "$ROW_DIR/start-after.xml" | wc -l | xargs)"

# ----------------------------------------------------------------------------------------------- the exported surface
log "--- the exported surface against qa/phase-03/exported-allowlist.txt (the project's own reader, r3 V20)"
python3 "$P03S/exported.py" "$APK" "$ALLOW" > "$ROW_DIR/exported.txt" 2>&1; echo $? > "$ROW_DIR/exported.rc"
cat "$ROW_DIR/exported.txt" >> "$LOG"
assert_eq "exported.py: the APK's exported components equal the allow-list exactly (rc)" "0" "$(cat "$ROW_DIR/exported.rc")"
assert_absent "…nothing exported that is not allowed" "EXPORTED BUT NOT ALLOWED" "$(cat "$ROW_DIR/exported.txt")"
assert_absent "…nothing on the list that is not exported" "ON THE LIST BUT NOT EXPORTED" "$(cat "$ROW_DIR/exported.txt")"
assert_eq "the allow-list carries this phase's ADD app.tileshell.files.FilesActivity (once)" "1" "$(grep -c "^app.tileshell.files.FilesActivity	" "$ALLOW")"
assert_eq "…and it is this phase's ONLY entry under app.tileshell.files" "1" "$(grep -c "^app\.tileshell\.files\." "$ALLOW")"
"$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" > "$ROW_DIR/manifest-xmltree.txt" 2> "$ROW_DIR/manifest-xmltree.err"; echo $? > "$ROW_DIR/manifest-xmltree.rc"
assert_eq "aapt2 dump xmltree of the APK's merged manifest (rc)" "0" "$(cat "$ROW_DIR/manifest-xmltree.rc")"
C_ACT="$(component "$ROW_DIR/manifest-xmltree.txt" app.tileshell.files.FilesActivity)"
C_SVC="$(component "$ROW_DIR/manifest-xmltree.txt" app.tileshell.files.FileOpsService)"
C_PRO="$(component "$ROW_DIR/manifest-xmltree.txt" app.tileshell.files.FilesProvider)"
C_RCV="$(component "$ROW_DIR/manifest-xmltree.txt" app.tileshell.files.FilesOpenReceiver)"
record "the manifest: FilesActivity / FileOpsService / FilesProvider / FilesOpenReceiver" "$C_ACT / $C_SVC / $C_PRO / $C_RCV"
assert_contains "FilesActivity is an exported activity (the control: the reader finds what IS exported)" "activity|exported=true" "$C_ACT"
assert_contains "the copy service FileOpsService is exported=false" "service|exported=false" "$C_SVC"
FGS="$(printf '%s' "$C_SVC" | sed -n 's/.*foregroundServiceType=\(.*\)$/\1/p')"
record "FileOpsService's foregroundServiceType as the manifest holds it (dataSync = 0x1)" "$FGS"
assert_eq "…and its foreground service type is dataSync (0x00000001)" "yes" "$(python3 -c "
import sys
v = sys.argv[1]
print('yes' if v == 'dataSync' or (v.startswith('0x') and int(v, 16) == 1) else 'no')" "$FGS")"
assert_contains "FilesProvider is listed under providers with exported=false" "provider|exported=false" "$C_PRO"
assert_contains "…and grantUriPermissions=true" "grantUriPermissions=true" "$C_PRO"
assert_eq "…and is NOT an allow-list entry (T18-5)" "0" "$(grep -c "FilesProvider" "$ALLOW")"
assert_contains "the device holds the APK that was checked" "yes" "$(apk_matches)"
# The same three facts as the device's package manager states them (a record beside the manifest's: dumpsys is the
# reader the doc's older text named).
record "dumpsys package: the provider's line" "$(adb shell dumpsys package app.tileshell | tr -d '\r' | grep -m1 -A2 'app.tileshell.files/' | xargs | cut -c1-200)"

# ----------------------------------------------------------------------------------------------- size
log "--- the APK's size"
SIZE="$(stat -c%s "$APK")"
record "stat -c%s app/build/outputs/apk/debug/app-debug.apk" "$SIZE"
assert_le "app-debug.apk is at most 629,145,600 bytes" "$MAX_APK" "$SIZE"

log "--- restore"
assert_eq "no AndroidRuntime line names the shell since the row began" "0" "$(crash_since "$LC0")"
layout_restore "$BASELINE" > "$ROW_DIR/restore.out" 2>&1; assert_eq "restore: layout_restore of the phase baseline" "0" "$?"
ensure_start
end_state
row_end
