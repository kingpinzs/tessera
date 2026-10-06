#!/usr/bin/env bash
# Phase 17 E2 — the app list: Photos, Camera and Movies & TV.
#
#   child    phase 02's regress.sh pattern on this build, seeded with phase 02's baseline carrying every marker and slot
#            the shell has seeded since (REGRESS_BASELINE = qa/phase-17/baseline_layout-p02.json): a grid-tile tap still
#            launches, the pivot still swipes, with zero assignSlotOnce `-> assigned` lines across its restores. It takes
#            no device lock itself, so it runs while this driver holds the device, before the row for a clean ring.
#   list     the walk of the whole list (dumps of applist_header: / applist_row: / applist_name: / applist_new: tags):
#            "Photos" under P, "Camera" under C, "Movies & TV" under M, each inside a row tagged applist_row:app.tileshell.
#   menus    each of the three rows held: the menu (applist_menu, and the shell's own line `context menu on <component>`)
#            offers Pin to Start and NOT Uninstall; and qa/phase-01/UNINSTALL's other half, the bracket that proves the
#            absence can fail: an ordinary app's row (Notes) held the same way DOES offer Uninstall. The item is read,
#            never tapped (phase 01's row owns what Android does next).
#   caption  no "New" caption: no applist_new:app.tileshell node anywhere in the walk, and phase 01 E12's caption
#            geometry rule — a captioned row's name sits 7 epx above the icon's centre, an uncaptioned one 2.75 epx
#            below — read as: each of the three names sits where an uncaptioned row's name in the same dump sits
#            (the name's centre against its row's centre, within E12's ± 1.4 epx).
#   groups   "A-Z groups and jump grid unchanged apart from the three rows":
#              * the list's rows are exactly the launcher activities the device reports (cmd package query-activities
#                MAIN / LAUNCHER) — so the phase added no other row (the viewer, the editor, the player and the capture
#                helper are not apps) and removed none;
#              * the shell's own rows are the eight it had before this phase plus the three;
#              * every row sits under the header of its name's first letter, the headers run # A–Z with no empty one,
#                and they equal the groups of the shell's own `[applist] model:` line;
#              * P, C and M each hold other rows too, so the three added no header;
#              * the jump grid's cells are # and A–Z; its P, C and M cells jump (the shell's `jump to <letter>` line) and
#                land with the row on screen; a letter with no apps is dimmed — a tap on its cell jumps nowhere
#                (the control for "a tap jumps"; what that tap does instead is recorded).
#            RECORDED, not asserted: the list as the pre-17 build drew it. Re-reading it needs that build installed,
#            which this row does not do (E1 owns the upgrade); what the phase may change is asserted above.
#   pivot    the row's own swipe to the list and Back to Start (the child proves the same from phase 02's baseline).
#
# E2_CHILD=0 skips the child (driver development; the gate never sets it).
# Changes on the device: the Start layout (the child's baselines, then this phase's baseline, restored). The child
# launches other apps and force-stops the shell through layout_restore; nothing is wiped or uninstalled.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
OUT="$QA/E2"; mkdir -p "$OUT"
P02_BASELINE="$P17/baseline_layout-p02.json"
EPX=3            # 1080 px / 360 epx
NOTES="org.fossify.notes/.activities.SplashActivity.Green"
SHELL_BEFORE="app.tileshell/.calculator.CalculatorActivity app.tileshell/.calendar.CalendarActivity app.tileshell/.clock.ClockActivity app.tileshell/.music.MusicActivity app.tileshell/.people.PeopleActivity app.tileshell/.recorder.RecorderActivity app.tileshell/.settings.SettingsActivity app.tileshell/.weather.WeatherActivity"
declare -A NAME=( [photos]="$PHOTOS_ACTIVITY" [camera]="$CAMERA_ACTIVITY" [video]="$VIDEO_ACTIVITY" )
declare -A LABEL=( [photos]="Photos" [camera]="Camera" [video]="Movies & TV" )
declare -A LETTER=( [photos]=P [camera]=C [video]=M )
KEYS="photos camera video"

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
# A node dumped twice in the same place has stopped moving (the jump scrolls smoothly, and a touch while the list
# still moves only stops it). Leaves the settled dump in out.xml and prints the node's bounds.
settled() { # out.xml resource-id
  local b="" prevb="" i
  for i in 1 2 3 4 5 6; do
    b="$(bounds "$1" "$2")"
    [ -n "$b" ] && [ "$b" = "$prevb" ] && break
    prevb="$b"; sleep 0.7; dump_ui "$1"
  done
  echo "$b"
}
hold_at() { # "x1 y1 x2 y2"
  local x1 y1 x2 y2
  read -r x1 y1 x2 y2 <<< "$1"
  adb shell input swipe $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) 1000; sleep 1.5
}
# One step of the walk: every list node of this dump not seen yet, top to bottom, appended to the order file as
# kind <TAB> id <TAB> text <TAB> the tag of the row that holds it.
walk_step() { # dump.xml order.txt
  python3 - "$1" "$2" <<'PY'
import html, re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
seen = set(tuple(l.split("\t")[:2] + l.split("\t")[3:4]) for l in open(sys.argv[2], encoding="utf-8").read().split("\n") if l)
rows, items = [], []
for n in re.finditer(r"<node[^>]*>", s):
    n = n.group(0)
    rid = re.search(r'resource-id="([^"]*)"', n); b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', n)
    if not rid or not b: continue
    rid = rid.group(1); x1, y1, x2, y2 = map(int, b.groups())
    t = re.search(r'text="([^"]*)"', n); text = html.unescape(t.group(1)) if t else ""
    if re.match(r"applist_(?:[a-z]+_)?row:", rid): rows.append((rid, x1, y1, x2, y2))
    m = re.match(r"applist_(header|name|new|section):(.*)$", rid)
    if m: items.append((y1, m.group(1), m.group(2), text, (x1 + x2) // 2, (y1 + y2) // 2))
with open(sys.argv[2], "a", encoding="utf-8") as o:
    for y, kind, ident, text, cx, cy in sorted(items):
        row = next((r[0] for r in rows if r[1] <= cx <= r[3] and r[2] <= cy <= r[4]), "")
        if (kind, ident, row) not in seen:
            seen.add((kind, ident, row)); o.write("\t".join([kind, ident, text, row]) + "\n")
PY
}
# What the walk says, as key=value lines.
walk_facts() { # order.txt
  python3 - "$1" <<'PY'
import sys, unicodedata
JUMP = ["#"] + [chr(c) for c in range(ord("A"), ord("Z") + 1)]
def letter_of(label):
    t = unicodedata.normalize("NFKD", label.lstrip().lower()); t = "".join(c for c in t if not unicodedata.combining(c))
    return t[0].upper() if t[:1] and "a" <= t[0] <= "z" else "#"
headers, letter, az, wrong, news, count = [], None, [], [], [], {}
for l in open(sys.argv[1], encoding="utf-8").read().split("\n"):
    if not l: continue
    kind, ident, text, row = (l.split("\t") + ["", "", ""])[:4]
    if kind == "header": headers.append(ident); letter = ident; count.setdefault(ident, 0)
    elif kind == "new": news.append(row or ident)
    elif kind == "name" and row.startswith("applist_row:"):
        az.append(ident); count[letter] = count.get(letter, 0) + 1
        print("group:%s=%s" % (ident, letter)); print("label:%s=%s" % (ident, text)); print("rowtag:%s=%s" % (ident, row))
        if letter_of(text) != letter: wrong.append("%s (%s) under %s" % (ident, text, letter))
print("headers=" + " ".join(headers))
print("headers_in_jump_order=" + ("yes" if headers == [h for h in JUMP if h in headers] and len(set(headers)) == len(headers) else "no"))
print("empty_headers=" + " ".join(h for h in headers if count.get(h, 0) == 0))
print("misgrouped=" + "; ".join(wrong))
print("az_rows=%d" % len(az))
print("az_components=" + " ".join(sorted(az)))
print("new_rows=" + " ".join(news))
for h in headers: print("count:%s=%d" % (h, count.get(h, 0)))
PY
}
fact() { awk -v k="$1=" 'index($0, k) == 1 { print substr($0, length(k) + 1); exit }' "$ROW_DIR/walk_facts.txt"; }
# The tag of the row node that holds a node (by the node's centre), and a name's centre minus its row's centre in px.
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
control_offset() { # dump.xml the-three's-name-tag
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
launchers() { adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | tr -d '\r' | grep '/' | tr -d ' ' | LC_ALL=C sort -u | tr '\n' ' ' | sed 's/ $//'; }

take_device_lock
if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
  adb install -r "$APK" > "$OUT/install.out" 2>&1 || { echo "E2: adb install -r of $APK failed:" >&2; cat "$OUT/install.out" >&2; exit 4; }
fi

# =============================================================================================== the child
if [ "${E2_CHILD:-1}" = "1" ]; then
  adb shell input keyevent KEYCODE_HOME; sleep 2
  R_MARK="$(ring_mark)"
  rm -rf "$OUT/p02-REGRESS.prev"; [ -e "$OUT/p02-REGRESS" ] && mv "$OUT/p02-REGRESS" "$OUT/p02-REGRESS.prev"
  ( REGRESS_BASELINE="$P02_BASELINE" bash "$P02S/regress.sh" "$OUT/p02-REGRESS" > "$OUT/p02-regress.out" 2>&1; echo $? > "$OUT/p02-regress.rc" )
  ring_since "$R_MARK" > "$OUT/p02-regress.ring.txt" 2>/dev/null
  adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 5
fi

# =============================================================================================== the row
row_begin E2 "the app list: Photos under P, Camera under C, Movies & TV under M; menus, captions, groups, the jump grid"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
assert_eq "wake" "Awake" "$(wake_device)"

if [ "${E2_CHILD:-1}" = "1" ]; then
  log "--- child: phase 02's regress.sh on this build (phase 02's baseline + every marker and slot seeded since)"
  R="$OUT/p02-REGRESS/REGRESS.txt"
  assert_eq "child: regress.sh exits 0" "0" "$(cat "$OUT/p02-regress.rc")"
  R_SUM="$(grep -E '^[0-9]+ passed, [0-9]+ failed' "$R" 2>/dev/null | tail -1)"; note "regress.sh: $R_SUM"
  assert_contains "child: regress.sh: 0 failed" " passed, 0 failed" "$R_SUM"
  assert_contains "child: a grid-tile tap still launches" "PASS  a tile tap launched another app" "$(cat "$R" 2>/dev/null)"
  assert_contains "child: the pivot still swipes to the app list" "PASS  swipe opened the app list" "$(cat "$R" 2>/dev/null)"
  assert_contains "child: … and Back returns to Start" "PASS  Back returned to Start" "$(cat "$R" 2>/dev/null)"
  assert_ne "child: its slice covers a restore (assignSlotOnce lines in it)" "0" "$(grep -cF 'assignSlotOnce' "$OUT/p02-regress.ring.txt")"
  assert_eq "child: zero assignSlotOnce -> assigned after its restores (C-3)" "0" "$(grep -F 'assignSlotOnce' "$OUT/p02-regress.ring.txt" | grep -cF -- '-> assigned')"
else
  record "child" "SKIPPED (E2_CHILD=0): a development run, not the gate's"
fi

layout_restore "$BASELINE" > "$OUT/baseline.out" 2>&1; assert_eq "layout_restore of the baseline" "0" "$?"

# ----------------------------------------------------------------------------------------------- the pivot
log "--- the pivot: swipe to the app list, Back to Start"
open_applist
dump_ui "$ROW_DIR/pivot-list.xml"
assert_eq "the swipe opened the app list (app_list)" "yes" "$(has_node "$ROW_DIR/pivot-list.xml" app_list)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
dump_ui "$ROW_DIR/pivot-back.xml"
assert_eq "Back returned to Start alone" "yes no" "$(has_node "$ROW_DIR/pivot-back.xml" start_page) $(has_node "$ROW_DIR/pivot-back.xml" app_list)"

# ----------------------------------------------------------------------------------------------- the walk
log "--- the walk of the list"
open_applist
: > "$ROW_DIR/walk_order.txt"
prev=""
for i in $(seq 1 40); do
  dump_ui "$ROW_DIR/walk.xml"
  walk_step "$ROW_DIR/walk.xml" "$ROW_DIR/walk_order.txt"
  last="$(grep -o 'resource-id="applist_name:[^"]*"' "$ROW_DIR/walk.xml" | tail -1)"
  [ "$last" = "$prev" ] && break; prev="$last"
  adb shell input swipe 540 1900 540 700 1500; sleep 1.2   # slow: no fling past a row
done
cp "$ROW_DIR/walk.xml" "$ROW_DIR/walk-last.xml"; rm -f "$ROW_DIR/walk.xml"
walk_facts "$ROW_DIR/walk_order.txt" > "$ROW_DIR/walk_facts.txt"
note "headers: $(fact headers); rows in the A-Z list: $(fact az_rows)"
assert_eq "the walk ended on the list (app_list in its last dump)" "yes" "$(has_node "$ROW_DIR/walk-last.xml" app_list)"

for k in $KEYS; do
  assert_eq "${LABEL[$k]} sits under ${LETTER[$k]}" "${LETTER[$k]}" "$(fact "group:${NAME[$k]}")"
  assert_eq "… its name reads \"${LABEL[$k]}\"" "${LABEL[$k]}" "$(fact "label:${NAME[$k]}")"
  assert_eq "… in a row tagged applist_row:app.tileshell" "applist_row:app.tileshell" "$(fact "rowtag:${NAME[$k]}")"
done
record "New captions in the walk (rows of other apps)" "$(fact new_rows | tr ' ' '\n' | grep -c . ) [$(fact new_rows)]"
assert_eq "no New caption on any in-APK row (no applist_new:app.tileshell node in the walk)" "0" "$(cut -f1,2 "$ROW_DIR/walk_order.txt" | grep -cx "new	app.tileshell")"

# ----------------------------------------------------------------------------------------------- the groups
log "--- the groups: unchanged apart from the three rows"
DEV="$(launchers)"; printf '%s\n' "$DEV" | tr ' ' '\n' > "$ROW_DIR/launcher-activities.txt"
assert_ne "the device reports its launcher activities" "" "$DEV"
assert_eq "the list's rows are exactly the device's launcher activities ($(printf '%s\n' "$DEV" | wc -w))" "$DEV" "$(fact az_components)"
SHELL_NOW="$(fact az_components | tr ' ' '\n' | grep '^app\.tileshell/' | LC_ALL=C sort | tr '\n' ' ' | sed 's/ $//')"
SHELL_WANT="$(printf '%s\n' $SHELL_BEFORE "$PHOTOS_ACTIVITY" "$CAMERA_ACTIVITY" "$VIDEO_ACTIVITY" | LC_ALL=C sort | tr '\n' ' ' | sed 's/ $//')"
assert_eq "the shell's own rows are the eight it had plus the three" "$SHELL_WANT" "$SHELL_NOW"
assert_eq "every row sits under the header of its name's first letter" "" "$(fact misgrouped)"
assert_eq "the headers run # A–Z, each once" "yes" "$(fact headers_in_jump_order)"
assert_eq "no header is empty" "" "$(fact empty_headers)"
MODEL="$(ring_since "$ROW_MARK" | grep -F '[applist] model:' | tail -1 | sed 's/^.*\[applist\] model: //')"
note "the shell's own line: [applist] model: $MODEL"
assert_eq "the shell's model line names the same groups" "$(fact headers | tr ' ' ',')" "$(printf '%s' "$MODEL" | sed -n 's/.*groups=\([^ ]*\).*/\1/p')"
assert_eq "… and the same number of rows" "$(fact az_rows)" "$(printf '%s' "$MODEL" | sed -n 's/^\([0-9]*\) rows.*/\1/p')"
for L in P C M; do
  n="$(fact "count:$L")"
  assert_eq "$L holds other rows beside this phase's (so the three added no header): $n rows" "yes" "$([ "${n:-0}" -ge 2 ] && echo yes || echo no)"
done
record "the list as the pre-17 build drew it" "not re-read: it needs that build installed (E1 owns the upgrade); the rows, the shell's own rows, the grouping and the grid are asserted above"

# ----------------------------------------------------------------------------------------------- captions and menus
log "--- each row: where its name sits (E12's caption rule), and its hold menu"
for k in $KEYS; do
  goto_letter "${LETTER[$k]}"
  scroll_to_node "$ROW_DIR/row_$k.xml" "applist_name:${NAME[$k]}" 3
  b="$(settled "$ROW_DIR/row_$k.xml" "applist_name:${NAME[$k]}")"
  assert_ne "${LABEL[$k]}: its row is on screen after the jump to ${LETTER[$k]}" "" "$b"
  IFS=$'\t' read -r rtag off rh <<< "$(row_of "$ROW_DIR/row_$k.xml" "applist_name:${NAME[$k]}")"
  IFS=$'\t' read -r ctag coff <<< "$(control_offset "$ROW_DIR/row_$k.xml" "applist_name:${NAME[$k]}")"
  note "${LABEL[$k]}: row $rtag ${rh:-?} px high, name centre ${off:-?} px from the row's centre; control ${ctag:-none} ${coff:-?} px"
  assert_eq "${LABEL[$k]}: the row is applist_row:app.tileshell" "applist_row:app.tileshell" "${rtag:-}"
  assert_ne "${LABEL[$k]}: an uncaptioned row of another app is in the same dump (the control)" "" "${ctag:-}"
  assert_within "${LABEL[$k]}: its name sits where an uncaptioned row's does (± 1.4 epx; a caption moves it 9.75 epx)" \
    "${coff:-x}" "${off:-x}" "$(python3 -c "print(1.4 * $EPX)")"
  assert_eq "${LABEL[$k]}: no applist_new node in its row's dump for the shell" "no" "$(has_node "$ROW_DIR/row_$k.xml" applist_new:app.tileshell)"
  MARK="$(ring_mark)"
  hold_at "$b"
  dump_ui "$ROW_DIR/menu_$k.xml"; screencap "$ROW_DIR/menu_$k.png"
  assert_eq "${LABEL[$k]}: holding the row opens the menu (applist_menu)" "yes" "$(has_node "$ROW_DIR/menu_$k.xml" applist_menu)"
  assert_contains "${LABEL[$k]}: … on this app" "[applist] context menu on ${NAME[$k]}" "$(ring_since "$MARK")"
  assert_eq "${LABEL[$k]}: the menu offers Pin to Start" "yes" "$(has_node "$ROW_DIR/menu_$k.xml" applist_menu_pin)"
  assert_eq "${LABEL[$k]}: the menu does NOT offer Uninstall" "no" "$(has_node "$ROW_DIR/menu_$k.xml" applist_menu_uninstall)"
  adb shell input keyevent KEYCODE_BACK; sleep 1
done
# The other half of the bracket: an ordinary app's menu, read the same way, does carry the Uninstall item.
assert_contains "the control app (Notes) is installed" "package:org.fossify.notes" "$(adb shell pm list packages org.fossify.notes | tr -d '\r')"
goto_letter N
scroll_to_node "$ROW_DIR/row_notes.xml" "applist_name:$NOTES" 3
b="$(settled "$ROW_DIR/row_notes.xml" "applist_name:$NOTES")"
assert_ne "control: Notes' row is on screen after the jump to N" "" "$b"
MARK="$(ring_mark)"
hold_at "$b"
dump_ui "$ROW_DIR/menu_notes.xml"; screencap "$ROW_DIR/menu_notes.png"
assert_eq "control: holding Notes' row opens the menu" "yes" "$(has_node "$ROW_DIR/menu_notes.xml" applist_menu)"
assert_contains "control: … on Notes" "[applist] context menu on $NOTES" "$(ring_since "$MARK")"
assert_eq "control: an ordinary app's menu offers Pin to Start" "yes" "$(has_node "$ROW_DIR/menu_notes.xml" applist_menu_pin)"
assert_eq "control: … and DOES offer Uninstall (so its absence above is read, not missed)" "yes" "$(has_node "$ROW_DIR/menu_notes.xml" applist_menu_uninstall)"
adb shell input keyevent KEYCODE_BACK; sleep 1
assert_contains "control: Notes is still installed (the item was never tapped)" "package:org.fossify.notes" "$(adb shell pm list packages org.fossify.notes | tr -d '\r')"

# ----------------------------------------------------------------------------------------------- the jump grid
log "--- the jump grid"
open_jump_grid "$ROW_DIR/jump_grid.xml"; screencap "$ROW_DIR/jump_grid.png"
assert_eq "tapping a header opens the jump grid (jump_grid)" "yes" "$(has_node "$ROW_DIR/jump_grid.xml" jump_grid)"
CELLS="$(grep -o 'resource-id="jump_cell:[^"]*"' "$ROW_DIR/jump_grid.xml" | sed 's/resource-id="jump_cell://; s/"$//' | tr '\n' ' ' | sed 's/ $//')"
assert_eq "the grid's cells are # and A–Z" "# A B C D E F G H I J K L M N O P Q R S T U V W X Y Z" "$CELLS"
adb shell input keyevent KEYCODE_BACK; sleep 1
for k in $KEYS; do
  open_jump_grid "$ROW_DIR/jump_grid_$k.xml"
  JM="$(ring_mark)"
  tap_node "$ROW_DIR/jump_grid_$k.xml" "jump_cell:${LETTER[$k]}"; sleep 2
  assert_contains "the grid's ${LETTER[$k]} cell jumps" "[applist] jump to ${LETTER[$k]} " "$(ring_since "$JM")"
  scroll_to_node "$ROW_DIR/after_jump_$k.xml" "applist_name:${NAME[$k]}" 2 >/dev/null 2>&1 || true
  assert_eq "after the jump, ${LABEL[$k]}'s row is on screen" "yes" "$(has_node "$ROW_DIR/after_jump_$k.xml" "applist_name:${NAME[$k]}")"
done
# The control: a letter with no apps is dimmed, and its cell jumps nowhere.
DIM="$(for L in B E H J Q U X Y Z; do case " $(fact headers) " in *" $L "*) ;; *) echo "$L"; break ;; esac; done)"
assert_ne "a letter with no apps exists for the control" "" "$DIM"
if [ -n "$DIM" ]; then
  open_jump_grid "$ROW_DIR/jump_grid_dim.xml"
  JM="$(ring_mark)"
  tap_node "$ROW_DIR/jump_grid_dim.xml" "jump_cell:$DIM"; sleep 2
  dump_ui "$ROW_DIR/jump_grid_dim_after.xml"
  assert_eq "control: the dimmed $DIM cell jumps nowhere (no jump line)" "0" "$(ring_since "$JM" | grep -cF '[applist] jump to ')"
  record "control: what the tap on the dimmed cell did instead" "jump_grid still up: $(has_node "$ROW_DIR/jump_grid_dim_after.xml" jump_grid); app_list: $(has_node "$ROW_DIR/jump_grid_dim_after.xml" app_list) (a dimmed cell is not a button: the tap falls to the grid, which a tap off its cells dismisses)"
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi

# ----------------------------------------------------------------------------------------------- restore
ensure_start
layout_restore "$BASELINE" > "$OUT/restore.out" 2>&1; assert_eq "restore: layout_restore of the baseline" "0" "$?"
ensure_start
row_end
