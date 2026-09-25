#!/usr/bin/env bash
# Phase 13's reminders fixture (E3, E7(a); T13-21) — sourced, not run. Not phase 03's e15.sh, which makes two spoken
# reminders and no photo. Everything goes through the product: two typed requests confirmed with a typed "yes", then
# the Whenever reminder's page, its camera button, the system photo picker and the newest picture — a generated black
# / white split pushed and scanned just before — and Save.
#
#   reminders_setup     makes the two reminders and attaches the photo; asserts both rows and the photo node
#   reminders_restore   deletes both reminders (their long-press menu's Delete) and the pushed picture
#   open_reminders      Tess -> the ≡ pane -> Reminders; leaves the page's dump in $ROW_DIR/reminders.xml

SPLIT_PATH="/sdcard/Pictures/qa13-split.png"

open_reminders() {
  ensure_start
  cortana_assist
  local i
  for i in 1 2 3 4 5 6; do
    sleep 1
    dump_ui "$ROW_DIR/.tess.xml"
    [ "$(has_node "$ROW_DIR/.tess.xml" cortana_menu_button)" = yes ] && break
  done
  tap_node "$ROW_DIR/.tess.xml" cortana_menu_button
  sleep 1.2
  dump_ui "$ROW_DIR/.pane.xml"
  tap_node "$ROW_DIR/.pane.xml" cortana_pane_item_reminders
  sleep 2
  dump_ui "$ROW_DIR/reminders.xml"
}

# The id of the reminder row whose title is $1 (from the Reminders page dump).
reminder_id() { # title [dump]
  python3 "$P13/dumpq.py" text_nodes "${2:-$ROW_DIR/reminders.xml}" >/dev/null
  python3 - "${2:-$ROW_DIR/reminders.xml}" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for m in re.finditer(r"<node[^>]*>", xml):
    s = m.group(0)
    rid = re.search(r'resource-id="reminder_row_title:([^"]+)"', s)
    t = re.search(r'text="([^"]*)"', s)
    if rid and t and sys.argv[2].lower() in t.group(1).lower():
        print(rid.group(1)); break
PY
}

# A typed request, confirmed on its card: the confirm card replaces the text box, so a typed "yes" has nowhere to go
# (observed 2026-09-25); the card's own Confirm button is the product's path.
make_typed_reminder() { # request
  ensure_start
  cortana_assist
  local i
  for i in 1 2 3 4 5 6; do
    sleep 1
    dump_ui "$ROW_DIR/.tess.xml"
    [ "$(has_node "$ROW_DIR/.tess.xml" cortana_text_box_field)" = yes ] && break
  done
  type_request "$1" 8
  dump_ui "$ROW_DIR/.card.xml"
  tap_node "$ROW_DIR/.card.xml" "cortana_card_button:confirm"
  sleep 3
  cortana_close
}

reminders_setup() {
  make_typed_reminder "remind me to check the QA13 tomorrow list tomorrow at 9 am"
  # The split picture, newest in MediaStore, so the picker shows it first.
  python3 "$P13/make_fixtures.py" split "$ROW_DIR/qa13-split.png" 1080 1080 >/dev/null
  adb push "$ROW_DIR/qa13-split.png" "$SPLIT_PATH" >/dev/null
  adb shell content call --uri content://media --method scan_file --arg "$SPLIT_PATH" >/dev/null 2>&1
  sleep 2
  # The Whenever reminder with the photo, on the Reminders page's own "new" page: a typed request with no time gets
  # the "When would you like to be reminded?" card, whose answer can only be spoken, because the card hides the text
  # box (observed 2026-09-25). The new page is the product's no-time route: text, the camera button, Save.
  open_reminders
  tap_node "$ROW_DIR/reminders.xml" reminders_appbar_add
  sleep 2
  dump_ui "$ROW_DIR/.reminder-new.xml"
  tap_node "$ROW_DIR/.reminder-new.xml" reminder_page_text
  sleep 1
  adb shell input text "check%sthe%sQA13%sphoto"
  sleep 1
  adb shell input keyevent KEYCODE_BACK   # the keyboard down; the page stays
  sleep 1
  dump_ui "$ROW_DIR/.reminder-page.xml"
  tap_node "$ROW_DIR/.reminder-page.xml" reminder_appbar_camera
  sleep 2.5
  # The picker opens as a half-height sheet: swipe it fully up before reading it (phase 11 EDGE's X5 route).
  adb shell input swipe 540 850 540 150 400
  sleep 2
  dump_ui "$ROW_DIR/.picker.xml"
  local pick
  pick="$(python3 - "$ROW_DIR/.picker.xml" <<'PY'
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
  note "picker: newest photo at [$pick]"
  # shellcheck disable=SC2086
  [ -n "$pick" ] && adb shell input tap $pick
  sleep 3
  dump_ui "$ROW_DIR/.reminder-page2.xml"
  tap_node "$ROW_DIR/.reminder-page2.xml" reminder_appbar_save
  sleep 2
  cortana_close
  open_reminders
  REM_TOMORROW="$(reminder_id "QA13 tomorrow")"
  REM_PHOTO="$(reminder_id "QA13 photo")"
  assert_ne "fixture: the tomorrow reminder's row" "" "$REM_TOMORROW"
  assert_ne "fixture: the photo reminder's row" "" "$REM_PHOTO"
  assert_eq "fixture: the photo reminder shows its photo" "yes" "$(has_node "$ROW_DIR/reminders.xml" "reminder_row_photo:$REM_PHOTO")"
  export REM_TOMORROW REM_PHOTO
}

# Long-press a reminder row at (x, y) and pick the menu's Delete.
delete_reminder_at() { # x y
  adb shell input swipe "$1" "$2" "$1" "$2" 1000
  sleep 1
  dump_ui "$ROW_DIR/.menu-del.xml"
  tap_node "$ROW_DIR/.menu-del.xml" reminder_menu_delete
  sleep 1.5
}

reminders_restore() {
  local id b
  for title in "QA13 tomorrow" "QA13 photo"; do
    open_reminders
    id="$(reminder_id "$title")"
    [ -n "$id" ] || continue
    b="$(bounds "$ROW_DIR/reminders.xml" "reminder_row_title:$id")"
    # shellcheck disable=SC2086
    set -- $b
    delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  done
  open_reminders
  assert_eq "restore: no QA13 reminder left" "" "$(reminder_id "QA13")"
  cortana_close
  adb shell rm -f "$SPLIT_PATH"
  adb shell content call --uri content://media --method scan_file --arg "$SPLIT_PATH" >/dev/null 2>&1
}
