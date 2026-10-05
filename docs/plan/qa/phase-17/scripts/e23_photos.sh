#!/usr/bin/env bash
# Phase 17 E23, the Photos half (row E23_PHOTOS) — App Shortcuts (phase 11 Q1's standing rule; C-8 / C-9).
#
#   start     layout_restore of qa/phase-17/baseline_layout.json (the PHOTOS slot tile resolves to the shell's Photos),
#             ensure_start; every dump of Start is gdump (the Photos tile cycles its pictures).
#   hold      the Photos tile held by phase 11 E3's method (qa/phase-16/scripts/e25.sh: the finger down 1.0 s, the dump
#             taken while it is down) → quick_sat_label:0..1 = "Collection", "Albums" in rank order; quick_sat:2..3 and
#             quick_sat_label:2..3 absent (start_page asserted in the same dump first).
#   ring      the burst's slice holds `[quick] shortcuts for app.tileshell/.photos.PhotosActivity/0: 2 (2 shown:
#             photos_collection,photos_albums)` (the activity-keyed line, T11-12).
#   taps      tap each satellite → PhotosActivity resumed with that pivot's header selected="true"
#             (photos_pivot:collection / photos_pivot:albums; the other one "false"), and Photos' own `open page=<id>`
#             line; `c6` + ensure_start between the holds (C-6; r3 V7).
#   dumpsys   `adb shell dumpsys shortcut` lists photos_collection rank 0 and photos_albums rank 1 as MANIFEST
#             shortcuts of PhotosActivity, and no other shortcut on that activity.
#   restore   layout_restore of the baseline.
#
# Changes on the device: the Start layout only (restored). Force-stops the shell (c6, layout_restore). No wipe.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_photos.sh"
LABELS="Collection,Albums"
QLINE="[quick] shortcuts for $PHOTOS_ACTIVITY/0: 2 (2 shown: photos_collection,photos_albums)"

# One satellite: the burst opened, its labels and its ring line asserted, satellite i tapped, the app's dump taken.
launch() { # tag index
  local mark
  mark="$(ring_mark)"
  burst_on "$PHOTOS_TILE" "$1" || { _verdict FAIL "$1: the burst opened" "$PHOTOS_TILE was not found on Start"; return 1; }
  assert_eq "$1: Start's page under the burst (start_page)" "yes" "$(has_node "$D/$1.xml" start_page)"
  assert_eq "$1: quick_sat_label:0..1 in rank order" "$LABELS" "$(sat_labels "$D/$1.xml")"
  assert_eq "$1: quick_sat:0 and quick_sat:1 are in the burst" "yes yes" "$(has_node "$D/$1.xml" quick_sat:0) $(has_node "$D/$1.xml" quick_sat:1)"
  assert_eq "$1: quick_sat:2..3 absent" "no no" "$(has_node "$D/$1.xml" quick_sat:2) $(has_node "$D/$1.xml" quick_sat:3)"
  assert_eq "$1: quick_sat_label:2..3 absent" "no no" "$(has_node "$D/$1.xml" quick_sat_label:2) $(has_node "$D/$1.xml" quick_sat_label:3)"
  assert_eq "$1: the burst's ring line (the activity-keyed line, T11-12)" "$QLINE" "$(quick_line "$mark")"
  PMARK="$(ring_mark)"
  tap_node "$D/$1.xml" "quick_sat:$2"; sleep 4
  dump_ui "$D/$1-app.xml"; screencap "$D/$1-app.png"
  PSLICE="$(ring_since "$PMARK")"
}

photos_row_begin E23_PHOTOS "App Shortcuts: the Photos tile's burst, each satellite's pivot, dumpsys shortcut"
layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "layout_restore of the baseline" "0" "$?"
ensure_start
assert_eq "the baseline's PHOTOS slot is the shell's Photos" "app.tileshell/app.tileshell.photos.PhotosActivity" "$(layout_json | python3 -c 'import json,sys; print(json.load(sys.stdin).get("slots",{}).get("PHOTOS",""))')"

log "--- the PHOTOS tile's burst: Collection"
launch sat-0-collection 0
assert_eq "Collection: PhotosActivity is resumed" "$PHOTOS_ACTIVITY" "$(top_activity)"
assert_eq "Collection: photos_pivot:collection is selected" "true" "$(selected "$D/sat-0-collection-app.xml" photos_pivot:collection)"
assert_eq "Collection: … and photos_pivot:albums is not" "false" "$(selected "$D/sat-0-collection-app.xml" photos_pivot:albums)"
assert_contains "Collection: Photos' own line names the page the shortcut asked for" "[photosapp] open page=collection" "$PSLICE"
c6; ensure_start

log "--- the PHOTOS tile's burst: Albums"
launch sat-1-albums 1
assert_eq "Albums: PhotosActivity is resumed" "$PHOTOS_ACTIVITY" "$(top_activity)"
assert_eq "Albums: photos_pivot:albums is selected" "true" "$(selected "$D/sat-1-albums-app.xml" photos_pivot:albums)"
assert_eq "Albums: … and photos_pivot:collection is not" "false" "$(selected "$D/sat-1-albums-app.xml" photos_pivot:collection)"
assert_contains "Albums: Photos' own line names the page" "[photosapp] open page=albums" "$PSLICE"
c6; ensure_start

log "--- adb shell dumpsys shortcut"
adb shell dumpsys shortcut | tr -d '\r' > "$D/dumpsys-shortcut.txt"
python3 - "$D/dumpsys-shortcut.txt" > "$D/shortcuts.tsv" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(r"\n\s+Package: app\.tileshell\s+UID:.*?(?=\n\s+Package: |\Z)", text, re.S)
block = m.group(0) if m else ""
for s in re.finditer(r"ShortcutInfo \{id=([^,]+),\s*flags=(0x[0-9a-f]+) \[([^\]]*)\].*?activity=ComponentInfo\{([^}]+)\}.*?rank=(\d+)", block, re.S):
    print("%s\t%s\t%s\t%s" % (s.group(4), s.group(5), s.group(1), s.group(3)))
PY
grep -F 'photos.PhotosActivity' "$D/shortcuts.tsv" | tee -a "$LOG" >/dev/null
PA="app.tileshell/app.tileshell.photos.PhotosActivity"
assert_ne "dumpsys shortcut was parsed (the shell's block holds shortcuts)" "0" "$(grep -c . "$D/shortcuts.tsv")"
assert_eq "dumpsys shortcut: PhotosActivity's ids with their ranks — photos_collection 0, photos_albums 1, nothing else" "0=photos_collection 1=photos_albums" \
  "$(awk -F'\t' -v a="$PA" '$1==a {print $2 "=" $3}' "$D/shortcuts.tsv" | sort | xargs)"
for id in photos_collection photos_albums; do
  FLAGS="$(awk -F'\t' -v i="$id" '$3==i {print $4}' "$D/shortcuts.tsv" | head -1)"
  record "$id's flags" "$FLAGS"
  assert_contains "dumpsys shortcut: $id is a manifest shortcut" "Man" "$FLAGS"
  assert_absent "dumpsys shortcut: … not a dynamic one" "Dyn" "$FLAGS"
  assert_eq "dumpsys shortcut: $id is declared once, on PhotosActivity" "$PA" "$(awk -F'\t' -v i="$id" '$3==i {print $1}' "$D/shortcuts.tsv" | xargs)"
done

no_crash
layout_restore "$BASELINE" > "$D/restore1.out" 2>&1; assert_eq "restore: layout_restore of the baseline" "0" "$?"
ensure_start
rings_save
row_end
