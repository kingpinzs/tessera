#!/usr/bin/env bash
# Phase 17, the Photos rows' own floor (E3, E4, E5, E6, E6b, E19_PHOTOS, E23_PHOTOS, the Photos edge sub-steps): sourced
# AFTER lib.sh and p17.sh. The working steps are the Photos builder's (dev-photos/scripts/pdev.sh), re-cut to the gate's
# form: every ring read is ring_since from a MARK, nothing here asserts on its own except where its name says so.
# Nothing is simulated; no microphone, no host audio.
STAMP_FILES="$STAMP_FILES $P17/scripts/p17_photos.sh"
# The gate build (the lead's rulings of 2026-10-05): the CLEAN build of phase-17 at bb154e06 — the three rounds of trust
# fixes, Living Images and the Camera's toast fix merged (355,589,093 bytes). Before it: c7336aca6b63d61b (e5e30678, round
# 1 of the fixes) and e8c26851363882da (25921fd7): rows_photos.md says which build each row's evidence is from.
GATE_APK_ID="95b543037345b851"
PHOTOS_TILE="tile:slot:PHOTOS"
VIEWER_ACTIVITY="app.tileshell/.photos.ViewerActivity"
EDIT_ACTIVITY="app.tileshell/.photos.EditActivity"

# The lock is taken ONCE per process. lib.sh's take_device_lock re-opens the lock file each time it is called (row_begin
# calls it again), which lets go of the lock for a moment: a script with several rows (edge_photos.sh) lost the device
# to another driver between two of its rows on 2026-10-05 and exited 3 after its first.
take_device_lock() {
  [ -n "${PHOTOS_LOCK_HELD:-}" ] && return 0
  exec 9>"$DEVICE_LOCK"
  if ! flock -n 9; then
    echo "another QA driver is already driving the device (lock $DEVICE_LOCK); refusing to start" >&2
    exit 3
  fi
  PHOTOS_LOCK_HELD=1
}

# photos_row_begin <ID> <what>: the lock, this worktree's APK on a mismatch (never -g), the row's stamp, the build and
# the wake asserted.
photos_row_begin() {
  take_device_lock
  mkdir -p "$QA/$1"
  if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
    adb install -r "$APK" > "$QA/$1/install.out" 2>&1 || { echo "$1: adb install -r of $APK failed:" >&2; cat "$QA/$1/install.out" >&2; exit 4; }
  fi
  row_begin "$1" "$2"
  D="$ROW_DIR"
  record "the installed APK id" "$(installed_apk_id)"
  assert_contains "the device holds this worktree's build" "yes" "$(apk_matches)"
  assert_eq "… and it is the gate build" "$GATE_APK_ID" "$(installed_apk_id | cut -c1-16)"
  assert_eq "wake" "Awake" "$(wake_device)"
  LOGCAT_T0="$(adb shell date +'%m-%d\ %H:%M:%S.000' | tr -d '\r')"
  [ -f "$GEN/qa-line.png" ] || python3 "$P01S/make_photos.py" "$GEN" editor >/dev/null
  [ -f "$GEN/qa-steps.mp4" ] || bash "$P17/scripts/make_videos.sh" "$GEN" >/dev/null
}

# ---------------------------------------------------------------- dumps
# Nodes whose resource-id starts with a prefix, one per line: id<TAB>l t r b<TAB>selected<TAB>text<TAB>content-desc.
pnodes() { python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    rid = re.search(r'resource-id="([^"]*)"', s)
    if not rid or not rid.group(1).startswith(sys.argv[2]):
        continue
    b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
    g = lambda k: (re.search(k + r'="([^"]*)"', s) or [None, ""])[1]
    print("%s\t%s\t%s\t%s\t%s" % (rid.group(1), " ".join(b.groups()) if b else "", g("selected"), g("text"), g("content-desc")))
PY
}
selected() { pnodes "$1" "$2" | awk -F'\t' -v id="$2" '$1==id {print $3; exit}'; }
node_enabled() { grep -o "resource-id=\"$2\"[^>]*enabled=\"[a-z]*\"" "$1" | head -1 | sed 's/.*enabled="//;s/"//'; }
count_nodes() { pnodes "$1" "$2" | grep -c . ; }
# The ids of the photos_item: nodes of a dump in reading order (top, then left).
item_ids() { pnodes "$1" photos_item: | grep -v 'photos_item_missing' | awk -F'\t' '{split($2,b," "); print b[2], b[1], $1}' | sort -n -k1,1 -k2,2 | sed 's/.*photos_item://' | xargs; }
# A node of ANOTHER app by its visible text (exact) or content-desc: prints "l t r b" of the first match.
text_bounds() { python3 - "$1" "$2" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
want = sys.argv[2].lower()
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    t = html.unescape((re.search(r'text="([^"]*)"', s) or [None, ""])[1]).lower()
    c = html.unescape((re.search(r'content-desc="([^"]*)"', s) or [None, ""])[1]).lower()
    if t == want or c == want:
        b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
        print(" ".join(b.groups())); break
PY
}
tap_bounds() { # "l t r b"
  # shellcheck disable=SC2086
  set -- $1
  [ $# -eq 4 ] || { echo "tap_bounds: no bounds" >&2; return 2; }
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
hold_node() { # dump id [ms]
  local b; b="$(bounds "$1" "$2")"; [ -n "$b" ] || { echo "hold_node: no node $2" >&2; return 2; }
  # shellcheck disable=SC2086
  set -- $b "${3:-900}"
  local x=$(( ($1 + $3) / 2 )) y=$(( ($2 + $4) / 2 ))
  adb shell input swipe "$x" "$y" "$x" "$y" "$5"; sleep 1
}
# A pulled file's pixel through PIL (PNG or JPEG), as "r,g,b"; and its size as "WxH".
fpx() { python3 -c "import sys; from PIL import Image; print('%d,%d,%d' % Image.open(sys.argv[1]).convert('RGB').getpixel((int(sys.argv[2]), int(sys.argv[3]))))" "$1" "$2" "$3" 2>/dev/null; }
img_size() { python3 -c "from PIL import Image; import sys; im = Image.open(sys.argv[1]); print('%dx%d' % im.size)" "$1" 2>/dev/null; }
differs16() { python3 -c "import sys; a=[int(v) for v in sys.argv[1].split(',')]; b=[int(v) for v in sys.argv[2].split(',')]; print('yes' if max(abs(x-y) for x,y in zip(a,b)) >= 16 else 'no')" "$1" "$2" 2>/dev/null; }
epx() { python3 -c "import sys; print(('%.2f' % (float(sys.argv[1]) / 3)).rstrip('0').rstrip('.'))" "$1"; }

# ---------------------------------------------------------------- MediaStore
# A row's id by its folder AND name ("DCIM/Camera/" "qa-photo-0.png"): the census holds other files of the same names.
img_id() { q "content query --uri content://media/external/images/media --projection _id:_display_name --where \"relative_path='$1' AND _display_name='$2'\"" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }
vid_id() { q "content query --uri content://media/external/video/media --projection _id:_display_name --where \"relative_path='$1' AND _display_name='$2'\"" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }
row_of() { q "content query --uri content://media/external/$1/media/$2 --projection _id:_display_name:relative_path:_size:is_pending:width:height:mime_type:owner_package_name"; }
row_field() { printf '%s\n' "$1" | sed -n "s/.*[ ,]$2=\([^,]*\).*/\1/p" | head -1; }
bucket_count() { # bucket display name -> its image rows + its video rows
  local i v
  i="$(q "content query --uri content://media/external/images/media --projection _id --where \"bucket_display_name='$1'\"" | grep -c '_id=')"
  v="$(q "content query --uri content://media/external/video/media --projection _id --where \"bucket_display_name='$1'\"" | grep -c '_id=')"
  echo $((i + v))
}
pending_count() { q "content query --uri content://media/external/$1/media --projection _id --where 'is_pending=1'" | grep -c '_id='; }
dev_md5() { adb shell md5sum "$@" | tr -d '\r' | awk '{print $1}' | xargs; }
SIX="qa-photo-0.png qa-photo-1.png qa-photo-2.png qa-photo-3.png qa-photo-4.png qa-photo-5.png"
# After media_up of the six: their ids in media_up order (qa-photo-0 the newest), as IDS and ID0..ID5.
six_ids() {
  local i
  IDS=""
  for i in 0 1 2; do IDS="$IDS $(img_id DCIM/Camera/ "qa-photo-$i.png")"; done
  for i in 3 4 5; do IDS="$IDS $(img_id Pictures/QA-Album/ "qa-photo-$i.png")"; done
  IDS="${IDS# }"
  # shellcheck disable=SC2086
  set -- $IDS
  ID0="${1:-}"; ID1="${2:-}"; ID2="${3:-}"; ID3="${4:-}"; ID4="${5:-}"; ID5="${6:-}"
  record "fixture image ids (qa-photo-0..5; 0–2 in DCIM/Camera, 3–5 in Pictures/QA-Album)" "$IDS"
  assert_eq "the six fixtures have six ids" "6" "$(echo $IDS | wc -w)"
}

# ---------------------------------------------------------------- permissions (pm only, never install -g)
PERM_WAS=""
perm_set() { # NAME true|false — remembers the state found the first time
  local was; was="$(perm_granted "$1")"
  case " $PERM_WAS " in *" $1="*) ;; *) PERM_WAS="$PERM_WAS $1=$was" ;; esac
  if [ "$2" = true ]; then adb shell pm grant app.tileshell "android.permission.$1"; else adb shell pm revoke app.tileshell "android.permission.$1"; fi
}
perm_restore() {
  local e
  for e in $PERM_WAS; do
    if [ "${e#*=}" = true ]; then adb shell pm grant app.tileshell "android.permission.${e%%=*}"; else adb shell pm revoke app.tileshell "android.permission.${e%%=*}"; fi
    assert_eq "restore: ${e%%=*} as found" "${e#*=}" "$(perm_granted "${e%%=*}")"
  done
  PERM_WAS=""
}

# ---------------------------------------------------------------- driving Photos
photos_start() { adb shell am start -n "$PHOTOS_ACTIVITY" "$@" >/dev/null 2>&1; sleep 2; }
# The collection, cold: rings saved, the shell stopped, Photos started, its page's tag asserted in the dump it leaves.
photos_cold() { # out.xml
  rings_save
  adb shell am force-stop app.tileshell; sleep 1
  photos_start; sleep 1
  dump_ui "$1"
  assert_eq "Photos' page: photos_pivot:collection is selected" "true" "$(selected "$1" photos_pivot:collection)"
}
# The phase 05 gesture driver's timed script ("tap x y; sleep 60; tap x y") — r3 V22.
gesture() { adb shell am instrument -r -w -e op script -e script "\"$1\"" "$DRV_RUNNER" > "$ROW_DIR/.gesture.txt" 2>&1; grep -q 'gesture.ok=true' "$ROW_DIR/.gesture.txt"; }
motion_line() { printf '%s\n' "$1" | grep -F "[motion] $2 " | tail -1 | sed 's/.*\[motion\] //'; }
motion_field() { printf '%s\n' "$1" | grep -F "[motion] $2 " | tail -1 | grep -oE "$3=[0-9.]+" | cut -d= -f2; }
# C-31: maxGapMs <= 33.4 ms, read as 16.7 ± 16.7.
assert_gap() { assert_within "$1: maxGapMs <= 33.4 ms (C-31)" 16.7 "$(motion_field "$2" "$3" maxGapMs)" 16.7; }
# No crash of the shell since the row began (the offline-preferred bullet's AndroidRuntime check; every row keeps it).
no_crash() {
  adb logcat -d -T "$LOGCAT_T0" -s AndroidRuntime:E 2>/dev/null | tr -d '\r' > "$ROW_DIR/androidruntime.txt"
  assert_eq "no crash of the shell in this row (AndroidRuntime)" "" "$(grep -F 'app.tileshell' "$ROW_DIR/androidruntime.txt" | head -3)"
}
viewer_open() { # collection dump, item id -> $D/viewer.xml
  tap_node "$1" "photos_item:$2"; sleep 2
  dump_ui "$D/viewer.xml"
}
viewer_menu() { # taps "…" in the viewer -> $D/menu.xml
  dump_ui "$D/viewer.xml"; tap_node "$D/viewer.xml" viewer_more; sleep 1; dump_ui "$D/menu.xml"
}

# ---------------------------------------------------------------- the editor (:photosedit; its ring lives while EditActivity
# does, and the editor and the trim screen STAY UP after a save — every ring read below is taken before any Back)
E="" # the editor's latest dump
edump() { E="$ROW_DIR/e.xml"; dump_ui "$E"; }
etap() { edump; tap_node "$E" "$1"; sleep "${2:-1}"; }
open_editor() { # image id: the collection (scrolled to the tile) -> the viewer's Edit -> the Edit sheet -> the editor
  photos_start
  scroll_to_node "$ROW_DIR/c.xml" "photos_item:$1" 12; tap_node "$ROW_DIR/c.xml" "photos_item:$1"; sleep 2
  dump_ui "$ROW_DIR/v.xml"; tap_node "$ROW_DIR/v.xml" viewer_edit; sleep 1
  dump_ui "$ROW_DIR/s.xml"; tap_node "$ROW_DIR/s.xml" edit_sheet_editor; sleep 3
  edump
}
open_tool() { etap edit_more; etap "edit_tool:$1"; edump; }
# edit_save: taps Save a copy and polls the :photosedit ring (4 reads a second, 25 s at most) for the op's line.
# Sets ESLICE, ELINE (the `[photosapp] edit …` line), COPY_URI, COPY_ROW (empty when no copy was made), COPY_LAG_MS (the
# device clock when is_pending was read, minus the line's wall stamp) and ESTATUS (the status on screen).
edit_save() {
  local i wall
  EMARK="$(ring_mark)"
  etap edit_save 0
  ELINE=""; COPY_URI=""; COPY_ROW=""; COPY_LAG_MS=""
  for i in $(seq 1 100); do
    ESLICE="$(ring_since "$EMARK" "$EDIT_RING")"
    ELINE="$(printf '%s\n' "$ESLICE" | grep -E '\[photosapp\] edit [a-z:]+ (->|failed:)' | tail -1)"
    [ -n "$ELINE" ] && break
    sleep 0.25
  done
  COPY_URI="$(printf '%s\n' "$ELINE" | grep -oE 'content://media/[a-z_]+/images/media/[0-9]+' | tail -1)"
  if [ -n "$COPY_URI" ]; then
    COPY_ROW="$(q "content query --uri $COPY_URI --projection _id:_display_name:relative_path:_size:is_pending:width:height:mime_type:datetaken:owner_package_name")"
    wall="$(printf '%s\n' "$ELINE" | grep -oE 'wall=[0-9]+' | cut -d= -f2)"
    COPY_LAG_MS=$(( $(device_ms) - wall ))
  fi
  sleep 1; edump
  ESTATUS="$(node_text "$E" edit_status)"
  note "edit_save: ${ELINE##*\[photosapp\] } | status on screen: $ESTATUS"
}
copy_field() { row_field "$COPY_ROW" "$1"; }
copy_pull() { # local name -> prints the local path
  local dst="$ROW_DIR/$1.$(copy_field _display_name | sed 's/.*\.//')"
  adb pull "/sdcard/$(copy_field relative_path)$(copy_field _display_name)" "$dst" >/dev/null 2>&1
  echo "$dst"
}

# ---------------------------------------------------------------- the shell's prefs and the wallpaper (E5, the frame edge)
# The whole shared_prefs folder kept as a tar and put back. A backup that is not a readable archive is never restored
# (the host's disk ran full on 2026-10-05: an empty backup "restored" would wipe the shell's settings).
prefs_backup() { # out.tar
  adb exec-out run-as app.tileshell tar cf - shared_prefs > "$1"
  PREFS_FILES="$(tar tf "$1" 2>/dev/null | grep -c 'shared_prefs/.')"
  assert_ne "the prefs backup is a readable archive with files in it" "0" "${PREFS_FILES:-0}"
}
prefs_restore() { # in.tar — with the shell stopped
  if [ "$(tar tf "$1" 2>/dev/null | grep -c 'shared_prefs/.')" = "0" ]; then
    _verdict FAIL "restore: the shell's prefs" "the backup $1 is not a readable archive; NOT restored — the prefs are as this row left them"
    return 1
  fi
  adb exec-in run-as app.tileshell tar xf - < "$1"
  assert_eq "restore: the shell's prefs are the backup's files" "$(tar tf "$1" | grep 'shared_prefs/.' | sort | xargs)" "$(adb shell run-as app.tileshell ls shared_prefs | tr -d '\r' | sed 's|^|shared_prefs/|' | sort | xargs)"
}
backgrounds() { adb shell run-as app.tileshell ls files/backgrounds 2>/dev/null | tr -d '\r' | grep -v 'No such' | xargs; }
# The lock wallpaper's entry in `dumpsys wallpaper`, or "(no lock wallpaper)". The dump says "no lock wallpaper" two
# ways — a `(null entry)` line under the header, or no line at all before the next header (seen after lock_clear on
# 2026-10-06: E5's restore compared "(null entry)" with the next section's title and failed on a cleared wallpaper).
lock_state() {
  local l; l="$(adb shell dumpsys wallpaper | tr -d '\r' | grep -A1 'Lock wallpaper state' | tail -1 | sed 's/^ *//')"
  case "$l" in "User "*) echo "$l" ;; *) echo "(no lock wallpaper)" ;; esac
}
# IWallpaperManager.clearWallpaper(callingPackage, which = FLAG_LOCK, userId): transaction 15 on this image (API 36; the
# Photos builder read it from the device's framework.jar). `cmd wallpaper` has no clear.
lock_clear() { adb shell service call wallpaper 15 s16 com.android.shell i32 2 i32 0 | tr -d '\r'; }
# Start's tile-free points (an 8-px lattice between the bars, outside every tile: node) that read a colour ± 4:
# prints "<count> <x>,<y>" (the first such point), or "0 -".
start_points() { # shot.png dump.xml r,g,b
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB")
xml = open(sys.argv[2], encoding="utf-8", errors="replace").read()
want = [int(v) for v in sys.argv[3].split(",")]
tiles = []
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    if re.search(r'resource-id="tile:', s):
        b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
        if b: tiles.append([int(v) for v in b.groups()])
n, first = 0, "-"
for y in range(200, 2040, 8):
    for x in range(4, im.size[0], 8):
        if any(l - 2 <= x <= r + 2 and t - 2 <= y <= bt + 2 for l, t, r, bt in tiles):
            continue
        if all(abs(a - b) <= 4 for a, b in zip(im.getpixel((x, y)), want)):
            n += 1
            if first == "-": first = "%d,%d" % (x, y)
print(n, first)
PY
}

# ---------------------------------------------------------------- Start (E23; phase 11 E3's method, qa/phase-16/scripts/e25.sh)
burst_on() { # tile-id tag: the tile's centre held 1.0 s, the dump taken with the finger down, then released
  local b x y
  gdump "$D/$2-rest.xml" || true
  b="$(bounds "$D/$2-rest.xml" "$1")"
  [ -n "$b" ] || { note "burst_on: no $1 on Start"; return 1; }
  # shellcheck disable=SC2086
  set -- "$1" "$2" $b
  x=$(( ($3 + $5) / 2 )); y=$(( ($4 + $6) / 2 ))
  adb shell input motionevent DOWN "$x" "$y"; sleep 1.0
  gdump "$D/$2.xml" || true; screencap "$D/$2.png"
  adb shell input motionevent UP "$x" "$y"; sleep 0.8
}
sat_labels() { local i out=""; for i in 0 1 2 3; do [ "$(has_node "$1" "quick_sat_label:$i")" = yes ] && out="$out$(node_text "$1" "quick_sat_label:$i"),"; done; echo "${out%,}"; }
quick_line() { ring_since "$1" | grep -F '[quick] shortcuts for' | tail -1 | sed 's/.*\[quick\]/[quick]/'; }
