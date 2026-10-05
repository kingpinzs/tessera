#!/usr/bin/env bash
# Phase 17 E6 — editing: one host-checkable assertion per tool (Q1 C; T17-3), clause by clause.
#
#   fixtures   media_up of qa-photo-0..2 (DCIM/Camera), qa-photo-3..5, qa-line.png and qa-redeye.png (Pictures/QA-Album)
#              — both fixture folders are allowed (r3 D7). The expected values come from scripts/edit_expect.py, which
#              reads the matrices from the phase doc (r3 V12); its own `check` (every matrix moves a fixture by >= 16)
#              is asserted first.
#   every copy (the function `copy_checks`): the image count is + 1; `[photosapp] edit <tool> -> <uri>` is in the
#              :photosedit ring (r3 D8; read with the editor still up — it stays up after a save); the copy's row is in
#              the ORIGINAL's folder; its is_pending is 0, read no later than 2 s after the line's wall= stamp; the
#              original's row is unchanged (same _id, same _size) and its md5 (`adb shell md5sum`) unchanged.
#   colour     auto-enhance (qa-photo-0), light + one step (qa-photo-1), colour + one step (qa-photo-2) and each of the
#              six filters (mono, warm, fade on qa-photo-0; sepia, cool on qa-photo-1; vivid on qa-photo-2) → the copy's
#              centre pixel = the matrix result ± 4 per channel AND differs from the original's by >= 16 on a channel.
#   crop       qa-photo-1 to its centre half → the copy's width:height = 320:240 (the row and the pulled file).
#   rotate     qa-photo-1 → a copy whose width:height are swapped (480:640).
#   straighten qa-line.png by −10° → the copy's dimensions = edit_expect.py's centre crop ± 1 px, and the line runs
#              horizontal: the rows 10 px above and below the line's centre (a 20-px band) read white all the way
#              across, the centre row dark.
#   red-eye    qa-redeye.png, a tap on the disc (tap-to-fix) → inside the disc the red channel <= half its original
#              (255), a pixel 30 px outside the disc unchanged ± 1.
#   failure    r3 V12's form: enhance applied to qa-photo-2, then `adb shell rm` of its file, then Save → no new row and
#              `[photosapp] edit enhance failed: <why>` (T17-23); no pending row left.
#   restore    media_down (the copies are the shell's own rows since the MARK), the permission as found.
#
# Fourteen editor sessions: about 7 minutes with the device held. Changes: media only (restored). No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_photos.sh"
EE="$HERE/edit_expect.py"
STAMP_FILES="$STAMP_FILES $EE"

photos_row_begin E6 "the editor: every tool's copy against the doc's numbers; the failed save"
python3 "$EE" check > "$D/edit_expect-check.txt" 2>&1; assert_eq "edit_expect.py check (no doc matrix is near the identity on the fixtures)" "0" "$?"
perm_set READ_MEDIA_IMAGES true
# shellcheck disable=SC2086
media_up $SIX qa-line.png qa-redeye.png
six_ids
LINE_ID="$(img_id Pictures/QA-Album/ qa-line.png)"; RED_ID="$(img_id Pictures/QA-Album/ qa-redeye.png)"
record "fixture ids (qa-line, qa-redeye)" "$LINE_ID $RED_ID"
assert_ne "qa-line.png has a row" "" "$LINE_ID"; assert_ne "qa-redeye.png has a row" "" "$RED_ID"
CAMERA_DIR="DCIM/Camera/"; ALBUM_DIR="Pictures/QA-Album/"
# The originals as they are before any edit: id -> "name folder size md5".
declare -A ORIG
snap() { # id folder name
  ORIG[$1]="$3 $2 $(row_field "$(row_of images "$1")" _size) $(dev_md5 "/sdcard/$2$3")"
}
snap "$ID0" "$CAMERA_DIR" qa-photo-0.png; snap "$ID1" "$CAMERA_DIR" qa-photo-1.png; snap "$ID2" "$CAMERA_DIR" qa-photo-2.png
snap "$LINE_ID" "$ALBUM_DIR" qa-line.png; snap "$RED_ID" "$ALBUM_DIR" qa-redeye.png
for k in "$ID0" "$ID1" "$ID2" "$LINE_ID" "$RED_ID"; do note "original $k: ${ORIG[$k]}"; done
rings_save; adb shell am force-stop app.tileshell; sleep 1
COPIES=0

# copy_checks <label> <tool as the line names it> <original id>: the clauses every result must meet.
copy_checks() {
  local label="$1" tool="$2" oid="$3" name folder size md5
  # shellcheck disable=SC2086
  set -- ${ORIG[$oid]}; name="$1"; folder="$2"; size="$3"; md5="$4"
  assert_contains "$label: [photosapp] edit $tool -> <uri> in the :photosedit ring" "[photosapp] edit $tool -> content://media/" "$ELINE"
  assert_ne "$label: the result is a new MediaStore row" "" "$COPY_ROW"
  assert_eq "$label: the image count is + 1" "$((COUNT_BEFORE + 1))" "$(media_count images)"
  assert_ne "$label: … a NEW row, not the original's" "$oid" "$(copy_field _id)"
  assert_eq "$label: the copy is in the original's folder" "$folder" "$(copy_field relative_path)"
  assert_eq "$label: the copy's is_pending is 0" "0" "$(copy_field is_pending)"
  assert_within "$label: … read within 2 s of the op's line (ms after its wall= stamp)" 1000 "$COPY_LAG_MS" 1000
  assert_eq "$label: the original's row is unchanged (same _id, same _size)" "$oid $size" "$(img_id "$folder" "$name") $(row_field "$(row_of images "$oid")" _size)"
  assert_eq "$label: the original's md5 is unchanged" "$md5" "$(dev_md5 "/sdcard/$folder$name")"
  [ -n "$COPY_ROW" ] && COPIES=$((COPIES + 1))
}
# colour_tool <label> <tool as the line names it> <edit_expect tool> <original id> <original r,g,b> <steps…>:
# opens the editor on the original, runs the steps (each an etap target), saves, and checks the centre pixel.
colour_tool() {
  local label="$1" tool="$2" expect_tool="$3" oid="$4" orig="$5" expect f got s; shift 5
  log "--- $label"
  open_editor "$oid"
  assert_eq "$label: the editor is up (edit_root)" "yes" "$(has_node "$E" edit_root)"
  for s in "$@"; do
    case "$s" in tool:*) open_tool "${s#tool:}" ;; *) etap "$s" ;; esac
  done
  COUNT_BEFORE="$(media_count images)"
  edit_save
  copy_checks "$label" "$tool" "$oid"
  expect="$(python3 "$EE" pixel "$expect_tool" "$orig")"
  f="$(copy_pull "$label")"; got="$(fpx "$f" 320 240)"
  record "$label: expected (edit_expect.py pixel $expect_tool $orig) / got" "$expect / $got"
  assert_eq "$label: the copy keeps the original's size" "640 480" "$(copy_field width) $(copy_field height)"
  assert_rgb "$label: the copy's centre pixel = the matrix result ± 4" "$expect" "$got" 4
  assert_eq "$label: … and differs from the original's by >= 16 on a channel" "yes" "$(differs16 "$orig" "${got:-$orig}")"
}
R0="220,40,40"; R1="40,180,80"; R2="40,90,220"

colour_tool enhance enhance enhance "$ID0" "$R0" edit_enhance
assert_ne ":photosedit runs while the editor is open (r3 D8)" "" "$(adb shell pidof app.tileshell:photosedit | tr -d '\r')"
colour_tool light light light:+1 "$ID1" "$R1" tool:light edit_slider_plus edit_accept
colour_tool colour colour colour:+1 "$ID2" "$R2" tool:colour edit_slider_plus edit_accept
colour_tool mono filter:mono filter:mono "$ID0" "$R0" tool:filters edit_filter:mono edit_accept
colour_tool sepia filter:sepia filter:sepia "$ID1" "$R1" tool:filters edit_filter:sepia edit_accept
colour_tool warm filter:warm filter:warm "$ID0" "$R0" tool:filters edit_filter:warm edit_accept
colour_tool cool filter:cool filter:cool "$ID1" "$R1" tool:filters edit_filter:cool edit_accept
colour_tool vivid filter:vivid filter:vivid "$ID2" "$R2" tool:filters edit_filter:vivid edit_accept
colour_tool fade filter:fade filter:fade "$ID0" "$R0" tool:filters edit_filter:fade edit_accept

# ----------------------------------------------------------------------------------------------- crop
log "--- crop qa-photo-1 to its centre half"
open_editor "$ID1"
etap edit_crop 2; edump; screencap "$D/crop.png"
IB="$(bounds "$E" edit_image)"
# shellcheck disable=SC2086
set -- $IB; IW=$(( ${3:-0} - ${1:-0} )); IH=$(( ${4:-0} - ${2:-0} ))
record "crop page: the picture and the rectangle (px)" "[$IB] [$(bounds "$E" edit_crop_rect)]"
hc() { bounds "$E" "edit_crop_handle:$1" | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'; }
# The rectangle is brought to 25 % .. 75 % of each side whatever it opened at: each handle dragged to its target.
# shellcheck disable=SC2086
set -- $IB; TLX=$(( $1 + IW / 4 )); TLY=$(( $2 + IH / 4 )); BRX=$(( $1 + IW * 3 / 4 )); BRY=$(( $2 + IH * 3 / 4 ))
# shellcheck disable=SC2046
set -- $(hc tl); adb shell input swipe "$1" "$2" "$TLX" "$TLY" 600; sleep 1
edump
# shellcheck disable=SC2046
set -- $(hc br); adb shell input swipe "$1" "$2" "$BRX" "$BRY" 600; sleep 1
edump; screencap "$D/crop-set.png"
record "crop page: the rectangle after the drags (px; the target is $TLX $TLY $BRX $BRY)" "$(bounds "$E" edit_crop_rect)"
etap edit_accept
COUNT_BEFORE="$(media_count images)"
edit_save
copy_checks crop crop "$ID1"
WANT="$(python3 "$EE" crop 640 480 0.5)"
assert_eq "crop: edit_expect.py crop 640 480 0.5" "320 240" "$WANT"
assert_eq "crop: the copy's width:height = 320:240" "$WANT" "$(copy_field width) $(copy_field height)"
assert_eq "crop: the pulled file is 320x240" "320x240" "$(img_size "$(copy_pull crop)")"

# ----------------------------------------------------------------------------------------------- rotate
log "--- rotate qa-photo-1"
open_editor "$ID1"
etap edit_rotate 2
COUNT_BEFORE="$(media_count images)"
edit_save
copy_checks rotate rotate "$ID1"
assert_eq "rotate: the copy's width:height are swapped (480:640)" "480 640" "$(copy_field width) $(copy_field height)"
assert_eq "rotate: the pulled file is 480x640" "480x640" "$(img_size "$(copy_pull rotate)")"

# ----------------------------------------------------------------------------------------------- straighten
log "--- straighten qa-line.png by -10 degrees"
open_editor "$LINE_ID"
open_tool straighten
for _ in 1 2 3 4 5 6 7 8 9 10; do tap_node "$E" edit_slider_minus; sleep 0.4; done
edump; screencap "$D/straighten.png"
assert_eq "straighten: the slider reads -10°" "-10°" "$(node_text "$E" edit_slider_value)"
etap edit_accept
COUNT_BEFORE="$(media_count images)"
edit_save
copy_checks straighten straighten "$LINE_ID"
WANT="$(python3 "$EE" straighten 640 480 -10)"
record "straighten: expected (edit_expect.py straighten 640 480 -10) / got" "$WANT / $(copy_field width) $(copy_field height)"
# shellcheck disable=SC2086
set -- $WANT
assert_within "straighten: the copy's width = the host-computed centre crop ± 1 px" "${1:-}" "$(copy_field width)" 1
assert_within "straighten: the copy's height = the host-computed centre crop ± 1 px" "${2:-}" "$(copy_field height)" 1
SF="$(copy_pull straighten)"
BAND="$(python3 - "$SF" <<'PY'
import sys
from PIL import Image
try:
    im = Image.open(sys.argv[1]).convert("RGB")
except Exception as e:
    print("unreadable: %s" % e); sys.exit()
w, h = im.size
mid = h // 2
dark = all(im.getpixel((x, mid))[0] < 80 for x in range(w))
white = all(min(im.getpixel((x, y))) > 250 for y in (mid - 10, mid + 10) for x in range(w))
print("line-flat" if dark and white else "not flat: centre row dark=%s, rows 10 px above and below white=%s" % (dark, white))
PY
)"
assert_eq "straighten: the line runs horizontal (a 20-px band about its centre: top and bottom rows white, centre row dark)" "line-flat" "$BAND"

# ----------------------------------------------------------------------------------------------- red-eye
log "--- red-eye on qa-redeye.png (a tap on the disc)"
open_editor "$RED_ID"
open_tool redeye
# shellcheck disable=SC2046
set -- $(bounds "$E" edit_image); adb shell input tap $(( (${1:-0} + ${3:-0}) / 2 )) $(( (${2:-0} + ${4:-0}) / 2 )); sleep 2
edump
record "red-eye: the tool's hint after the tap" "$(node_text "$E" edit_redeye_hint)"
etap edit_accept
COUNT_BEFORE="$(media_count images)"
edit_save
copy_checks redeye redeye "$RED_ID"
RF="$(copy_pull redeye)"
record "red-eye: centre / 15 px from the centre / 30 px outside the disc (the original: 255,0,0 / 255,0,0 / 128,128,128)" "$(fpx "$RF" 320 240) / $(fpx "$RF" 335 240) / $(fpx "$RF" 370 240)"
half() { python3 -c "import sys; print('yes' if sys.argv[1] and int(sys.argv[1].split(',')[0]) <= 127 else 'no: ' + sys.argv[1])" "$1"; }
assert_eq "red-eye: at the disc's centre the red channel <= half its original" "yes" "$(half "$(fpx "$RF" 320 240)")"
assert_eq "red-eye: 15 px from the centre (inside the disc) the red channel <= half its original" "yes" "$(half "$(fpx "$RF" 335 240)")"
assert_rgb "red-eye: a pixel 30 px outside the disc is unchanged ± 1" "128,128,128" "$(fpx "$RF" 370 240)" 1

assert_eq "thirteen copies were made" "13" "$COPIES"
assert_eq "the image count is the census + 8 fixtures + 13 copies" "$((CENSUS_IMAGES + 8 + 13))" "$(media_count images)"

# ----------------------------------------------------------------------------------------------- the failure line
log "--- the failed save: enhance on qa-photo-2, its file removed, then Save"
open_editor "$ID2"
etap edit_enhance
adb shell rm /sdcard/DCIM/Camera/qa-photo-2.png; sleep 2
assert_eq "the original's file is gone" "" "$(adb shell ls /sdcard/DCIM/Camera/qa-photo-2.png 2>/dev/null | tr -d '\r')"
COUNT_BEFORE="$(media_count images)"
edit_save
record "the failed save's line" "${ELINE##*\[photosapp\] }"
record "the failed save's status on screen" "$ESTATUS"
assert_contains "[photosapp] edit <tool> failed: <why> (T17-23)" "[photosapp] edit enhance failed: " "$ELINE"
assert_ne "… with a reason after the colon" "" "$(printf '%s\n' "$ELINE" | sed -n 's/.*edit enhance failed: *//p')"
absent_in "no success line for this save" "[photosapp] edit enhance -> " "$ESLICE"
assert_eq "no new row" "$COUNT_BEFORE" "$(media_count images)"
assert_eq "no pending image row is left" "0" "$(pending_count images)"

# ----------------------------------------------------------------------------------------------- the originals, restore
for k in "$ID0" "$ID1" "$LINE_ID" "$RED_ID"; do
  # shellcheck disable=SC2086
  set -- ${ORIG[$k]}
  assert_eq "at the end: $1's md5 is unchanged" "$4" "$(dev_md5 "/sdcard/$2$1")"
done
no_crash
c6
media_down
perm_restore
ensure_start
rings_save
row_end
