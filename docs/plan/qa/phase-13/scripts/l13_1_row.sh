#!/usr/bin/env bash
# L13-1 regression row (review/2026-09-25-L13-1-fix-plan.md, "The gate"): a photo picked from inside Tess's window.
# Fails on a build with the defect, passes on the fix's build. Everything goes through the product: the Reminders
# page's + and a row tap, the reminder page's camera, the confirm card's Add a photo, the system photo picker, Save,
# Remind, Cancel, the long-press Delete. The private copies (Q2) are counted in the app's own files/reminder_photos.
#
# usage: l13_1_row.sh <label>   — runs on the APK already installed (install it first; the header records which).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$(dirname "$0")/reminders_fixture.sh"

LABEL="${1:?label}"
PHOTO_PATH="/sdcard/Pictures/l13-1-photo.png"
COPY_DIR="files/reminder_photos"

row_begin "L13-1-row-$LABEL" "L13-1 regression row on $LABEL: a photo picked from inside Tess reaches the page and the card"

# Every FATAL EXCEPTION the launcher's process has logged (the crash buffer keeps them; the pid is not a reliable
# signal — the repro's 365bd248 run logged the crash and kept its pid).
crashes() { adb logcat -d -b crash 2>/dev/null | grep -c "Process: $PKG,"; }
copies() { adb shell "run-as $PKG sh -c 'ls $COPY_DIR 2>/dev/null'" | tr -d '\r' | grep -c .; }
focus() { adb shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r'; }
# The stored reminder whose text contains $1: prints its photoUri ("-" when none, "" when there is no such reminder).
stored_photo() { # text
  adb shell run-as $PKG cat files/cortana_reminders.json 2>/dev/null | python3 -c '
import json, sys
needle = sys.argv[1].lower()
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
items = data.get("reminders", data) if isinstance(data, dict) else data
for r in items:
    if isinstance(r, dict) and needle in str(r.get("text", "")).lower():
        print(r.get("photoUri") or "-"); break
' "$1"
}
# Is the stored photo a private copy that exists? prints yes/no.
is_private_copy() { # uri
  local uri="$1" name
  case "$uri" in
    file:///data/user/0/$PKG/$COPY_DIR/*|file:///data/data/$PKG/$COPY_DIR/*) ;;
    *) echo no; return ;;
  esac
  name="${uri##*/}"
  if adb shell "run-as $PKG sh -c 'test -f $COPY_DIR/$name && echo yes || echo no'" | tr -d '\r' | grep -q yes; then echo yes; else echo no; fi
}
# The picker opens as a half-height sheet: swipe it fully up, then tap the newest photo (reminders_setup's route).
pick_newest() { # tag
  adb shell input swipe 540 850 540 150 400
  sleep 2
  dump_ui "$ROW_DIR/.picker-$1.xml"
  local pick
  pick="$(python3 - "$ROW_DIR/.picker-$1.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); best = None
for n in re.finditer(r"<node[^>]*>", s):
    n = n.group(0); d = re.search(r'content-desc="(Photo taken on [^"]*)"', n); b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if d and b:
        x1, y1, x2, y2 = map(int, b.groups())
        if best is None or (y1, x1) < best[0]: best = ((y1, x1), (x1 + x2) // 2, (y1 + y2) // 2)
print(f"{best[1]} {best[2]}" if best else "")
PY
)"
  note "picker ($1): newest photo at [$pick]"
  # shellcheck disable=SC2086
  [ -n "$pick" ] && adb shell input tap $pick
  sleep 3
}
open_tess() {
  ensure_start
  cortana_assist
  local i
  for i in 1 2 3 4 5 6; do
    sleep 1
    dump_ui "$ROW_DIR/.tess.xml"
    [ "$(has_node "$ROW_DIR/.tess.xml" cortana_text_box_field)" = yes ] && break
  done
}
# Long-press Delete on every reminder whose title contains "L13 " (this row's own reminders only).
delete_row_reminders() {
  local id b n
  for n in 1 2 3 4; do
    open_reminders
    id="$(reminder_id "L13 ")"
    [ -n "$id" ] || break
    b="$(bounds "$ROW_DIR/reminders.xml" "reminder_row_title:$id")"
    # shellcheck disable=SC2086
    set -- $b
    delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    cortana_close
  done
}

# ---- fixture: a picture newest in MediaStore, so the picker shows it first
python3 "$P13/make_fixtures.py" split "$ROW_DIR/l13-1-photo.png" 1080 1080 >/dev/null
adb push "$ROW_DIR/l13-1-photo.png" "$PHOTO_PATH" >/dev/null
adb shell content call --uri content://media --method scan_file --arg "$PHOTO_PATH" >/dev/null 2>&1
sleep 2
adb shell am force-stop $PKG; sleep 2
show_start 5
CRASH0="$(crashes)"
COPIES0="$(copies)"
note "crash-buffer entries for $PKG at the start: $CRASH0; private copies: $COPIES0"

# ---- (1) the Reminders page's + opens the empty reminder page, no crash
open_reminders
C="$(crashes)"
tap_node "$ROW_DIR/reminders.xml" reminders_appbar_add
sleep 3
dump_ui "$ROW_DIR/page-new.xml"
assert_eq "(1) + opens the reminder page (its text field is on screen)" "yes" "$(has_node "$ROW_DIR/page-new.xml" reminder_page_text)"
assert_eq "(1) + crashes nothing" "$C" "$(crashes)"

# ---- (2) the page's camera: the picker shows, the photo lands on the page with the typed text kept, Save stores a copy
tap_node "$ROW_DIR/page-new.xml" reminder_page_text
sleep 1
adb shell input text "L13%spage%sphoto"
sleep 1
adb shell input keyevent KEYCODE_BACK   # the keyboard down; the page stays
sleep 1
dump_ui "$ROW_DIR/page-typed.xml"
tap_node "$ROW_DIR/page-typed.xml" reminder_appbar_camera
sleep 2.5
F="$(focus)"
note "focus after the camera: $F"
assert_contains "(2) the page's camera shows the system photo picker" "photopicker" "$F"
pick_newest page
dump_ui "$ROW_DIR/page-picked.xml"
screencap "$ROW_DIR/page-picked.png"
note "focus after the pick: $(focus)"
assert_eq "(2) the picked photo shows on the page" "yes" "$(has_node "$ROW_DIR/page-picked.xml" reminder_page_photo)"
assert_contains "(2) the typed text survived the picker (Tess stepped aside, not closed)" 'text="L13 page photo"' "$(cat "$ROW_DIR/page-picked.xml")"
tap_node "$ROW_DIR/page-picked.xml" reminder_appbar_save
sleep 2
cortana_close
P="$(stored_photo "L13 page photo")"
note "stored photoUri (page): $P"
assert_eq "(2) the saved reminder's photo is a private copy on disk" "yes" "$(is_private_copy "$P")"
assert_eq "(2) one private copy more" "$((COPIES0 + 1))" "$(copies)"

# ---- (3) a row tap opens the reminder page with its photo, no crash
open_reminders
ID_PAGE="$(reminder_id "L13 page photo")"
assert_eq "(3) the Reminders row shows the photo" "yes" "$(has_node "$ROW_DIR/reminders.xml" "reminder_row_photo:$ID_PAGE")"
C="$(crashes)"
tap_node "$ROW_DIR/reminders.xml" "reminder_row_title:$ID_PAGE"
sleep 3
dump_ui "$ROW_DIR/page-row.xml"
assert_eq "(3) a row tap opens the reminder page with its photo" "yes" "$(has_node "$ROW_DIR/page-row.xml" reminder_page_photo)"
assert_eq "(3) the row tap crashes nothing" "$C" "$(crashes)"
cortana_close; cortana_close

# ---- (4) the card's Add a photo shows the picker; Back returns Tess to the same card, unchanged
open_tess
type_request "remind me to check the L13 card photo tomorrow at 9 am" 8
dump_ui "$ROW_DIR/card.xml"
assert_eq "(4) the confirm card offers Add a photo" "yes" "$(has_node "$ROW_DIR/card.xml" cortana_card_add_photo)"
tap_node "$ROW_DIR/card.xml" cortana_card_add_photo
sleep 2.5
F="$(focus)"
note "focus after Add a photo: $F"
assert_contains "(4) Add a photo shows the system photo picker" "photopicker" "$F"
adb shell input keyevent KEYCODE_BACK
sleep 3
dump_ui "$ROW_DIR/card-back.xml"
note "focus after Back: $(focus)"
assert_eq "(4) Back from the picker: the same card, Add a photo still there" "yes" "$(has_node "$ROW_DIR/card-back.xml" cortana_card_add_photo)"
assert_contains "(4) Back from the picker: the card keeps its reminder text" 'check the l13 card photo' "$(tr 'A-Z' 'a-z' < "$ROW_DIR/card-back.xml")"
assert_eq "(4) Back from the picker: no photo on the card" "no" "$(has_node "$ROW_DIR/card-back.xml" cortana_card_photo)"

# ---- (5) Add a photo -> pick: the photo takes the row's place (Q3); Remind saves it with the photo
tap_node "$ROW_DIR/card-back.xml" cortana_card_add_photo
sleep 2.5
F="$(focus)"
note "focus after the second Add a photo: $F"
assert_contains "(5) Add a photo shows the picker again after a Back" "photopicker" "$F"
pick_newest card
dump_ui "$ROW_DIR/card-picked.xml"
screencap "$ROW_DIR/card-picked.png"
assert_eq "(5) the picked photo shows on the card" "yes" "$(has_node "$ROW_DIR/card-picked.xml" cortana_card_photo)"
assert_eq "(5) the photo takes the Add a photo row's place" "no" "$(has_node "$ROW_DIR/card-picked.xml" cortana_card_add_photo)"
assert_eq "(5) one private copy more on the card" "$((COPIES0 + 2))" "$(copies)"
tap_node "$ROW_DIR/card-picked.xml" "cortana_card_button:confirm"
sleep 3
dump_ui "$ROW_DIR/card-saved.xml"
assert_eq "(5) Remind shows the saved card" "yes" "$(has_node "$ROW_DIR/card-saved.xml" cortana_card_saved_row)"
cortana_close
P="$(stored_photo "L13 card photo")"
note "stored photoUri (card): $P"
assert_eq "(5) the card's reminder was saved with a private copy on disk" "yes" "$(is_private_copy "$P")"
open_reminders
ID_CARD="$(reminder_id "L13 card photo")"
assert_eq "(5) its Reminders row shows the photo" "yes" "$(has_node "$ROW_DIR/reminders.xml" "reminder_row_photo:$ID_CARD")"
cortana_close

# ---- (6) Cancel after a pick deletes the copy and stores nothing
open_tess
type_request "remind me to check the L13 cancel photo tomorrow at 9 am" 8
dump_ui "$ROW_DIR/cancel-card.xml"
tap_node "$ROW_DIR/cancel-card.xml" cortana_card_add_photo
sleep 2.5
pick_newest cancel
dump_ui "$ROW_DIR/cancel-picked.xml"
assert_eq "(6) the photo is on the card before Cancel" "yes" "$(has_node "$ROW_DIR/cancel-picked.xml" cortana_card_photo)"
assert_eq "(6) its copy exists before Cancel" "$((COPIES0 + 3))" "$(copies)"
tap_node "$ROW_DIR/cancel-picked.xml" "cortana_card_button:cancel"
sleep 2
assert_eq "(6) Cancel deletes the unsaved copy" "$((COPIES0 + 2))" "$(copies)"
assert_eq "(6) Cancel stores no reminder" "" "$(stored_photo "L13 cancel photo")"
cortana_close

# ---- (7) deleting the photo reminders deletes their copies
delete_row_reminders
assert_eq "(7) no L13 reminder is left" "" "$(stored_photo "L13 ")"
assert_eq "(7) deleting the reminders deleted their copies" "$COPIES0" "$(copies)"

# ---- (8) the pass-through activity is not exported: another uid cannot start it. adbd runs as root on this emulator
# and root may start anything, so the start is made as the shell uid (2000), which Android refuses for a non-exported
# activity exactly as it refuses CortanaPermissionActivity (probed 2026-09-25: "not exported from uid").
OUT="$(adb shell "su 2000 am start -n $PKG/.cortana.CortanaResultActivity" 2>&1 | tr -d '\r')"
note "am start as the shell uid 2000: $OUT"
assert_contains "(8) the pass-through refuses a start from another uid (not exported)" "not exported" "$OUT"

# ---- the whole row
assert_eq "no crash anywhere in the row" "$CRASH0" "$(crashes)"
adb logcat -d -b crash 2>/dev/null | grep -A8 "Process: $PKG," | tail -40 > "$ROW_DIR/crash-buffer-tail.txt"
adb shell rm -f "$PHOTO_PATH"
adb shell content call --uri content://media --method scan_file --arg "$PHOTO_PATH" >/dev/null 2>&1
show_start 3
row_end
