#!/usr/bin/env bash
# "I should be able to long hold app icon to bring up a quick menu to uninstall it" (Jeremy 2026-09-22).
#
# Bracketed on the shell's OWN row, which must bring up the same menu WITHOUT the uninstall item: it is
# the Home app drawing the menu and removing it from inside itself leaves the phone with no launcher. A
# row that only checked the happy case would pass just as well against an item that is always drawn.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"

VICTIM=org.fossify.notes   # ordinary, uninstallable
VICTIM_LABEL=Notes         # as both the app list and Android's dialog name it
SELF=app.tileshell         # the shell itself: menu yes, uninstall no

open_list() {
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell input swipe 900 1200 150 1200 250; sleep 2
  for _ in 1 2 3 4 5 6 7 8 9 10; do adb shell input swipe 540 700 540 2000 200; done
  sleep 3
}

hold_row() { # hold_row <pkg> <xml out> — a hold is longer than Edit.HOLD_MS (783 ms)
  local xy
  scroll_to_id "$ROW_DIR/find.xml" "applist_row:$1" || return 1
  xy=$(center "$ROW_DIR/find.xml" "applist_row:$1") || return 1
  adb shell "input swipe $xy $xy 1400"
  sleep 2
  dump "$2"
}

row_begin UNINSTALL "the app list's hold menu offers Uninstall"

# ---- an ordinary app: the menu carries both items --------------------------------------------------
open_list
hold_row "$VICTIM" "$ROW_DIR/menu.xml"
adb exec-out screencap -p > "$ROW_DIR/menu.png"
assert_contains "holding a row opens the menu" 'resource-id="applist_menu"' "$(cat "$ROW_DIR/menu.xml")"
assert_contains "the menu still offers Pin to Start" 'applist_menu_pin' "$(cat "$ROW_DIR/menu.xml")"
assert_contains "and now offers Uninstall" 'applist_menu_uninstall' "$(cat "$ROW_DIR/menu.xml")"

# ---- picking it hands the uninstall to Android, which asks ----------------------------------------
tap_id "$ROW_DIR/menu.xml" applist_menu_uninstall
sleep 3
dump "$ROW_DIR/confirm.xml"
adb exec-out screencap -p > "$ROW_DIR/confirm.png"
OWNER="$(python3 - "$ROW_DIR/confirm.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
m = re.search(r'package="([^"]+)"', s)
print(m.group(1) if m else "")
PY
)"
note "the screen now belongs to: $OWNER"
# The shell does not remove anything itself: Android's own package installer asks. What must hold is
# that the screen is no longer the shell's, and that it names the app being removed.
assert_ne "Android's uninstaller is on screen, not the shell" "$PKG" "$OWNER"
assert_contains "Android is the one asking" "Do you want to uninstall this app?" "$(cat "$ROW_DIR/confirm.xml")"
# The label the dialog shows is the app's own, which is what the list shows too: "Notes", not the package.
assert_contains "and it names the app that was held" "$VICTIM_LABEL" "$(cat "$ROW_DIR/confirm.xml")"
adb shell input keyevent KEYCODE_BACK; sleep 2
assert_contains "the app survived the row cancelling it" "package:$VICTIM" \
  "$(adb shell pm list packages "$VICTIM")"

# ---- the shell's own row: same menu, no uninstall ---------------------------------------------------
open_list
hold_row "$SELF" "$ROW_DIR/self.xml"
adb exec-out screencap -p > "$ROW_DIR/self.png"
assert_contains "the shell's own row opens the menu too" 'resource-id="applist_menu"' "$(cat "$ROW_DIR/self.xml")"
assert_contains "and still offers Pin to Start" 'applist_menu_pin' "$(cat "$ROW_DIR/self.xml")"
assert_absent "but never offers to uninstall the launcher itself" 'applist_menu_uninstall' "$(cat "$ROW_DIR/self.xml")"
adb shell input keyevent KEYCODE_BACK

row_end
