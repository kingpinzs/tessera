#!/usr/bin/env bash
# E1 — the keyboard is an input method Android lists, enables and selects, and the checklist says so.
#
#   "adb shell ime list -a -s (all input methods; without -a the list holds only enabled ones)
#    includes the shell IME; adb shell ime enable <id> (the checklist's "IME enabled" row is this
#    step), after which adb shell ime list -s includes it, then adb shell ime set <id> selects it;
#    the checklist shows both rows granted"
#
# Bracketed so the row can fail: the keyboard is DISABLED first, and the checklist must show both rows
# missing; enabling flips one row; selecting flips the other.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E1 "the keyboard is listed, enabled, selected, and the checklist agrees"

prior="$(adb shell settings get secure default_input_method | tr -d '\r')"
note "prior default ime: $prior"

checklist() { # tag-prefix -> the state the page shows for that row
  # NOT `am start -S`: that force-stops the whole package, and Android answers a force-stopped input
  # method by selecting another one (runs 1 and 2 recorded exactly that — the checklist was right).
  # NEW_TASK | CLEAR_TASK recreates the page without stopping the :ime process.
  adb shell am start -W -f 0x10008000 -n app.tileshell/.settings.SettingsActivity --es page CHECKLIST >/dev/null
  sleep 2
  local f="$ROW_DIR/.cl.xml"
  scroll_to_node "$f" "checklist:$1:granted" 6 >/dev/null 2>&1 || true
  if [ "$(has_node "$f" "checklist:$1:granted")" = yes ]; then echo granted
  elif [ "$(has_node "$f" "checklist:$1:missing")" = yes ]; then echo missing
  else echo absent; fi
}

# ---- bracket: disabled ------------------------------------------------------------------------
adb shell ime disable "$IME_ID" >/dev/null
all="$(adb shell ime list -a -s | tr -d '\r')"
enabled="$(adb shell ime list -s | tr -d '\r')"
log "ime list -a -s: $(echo $all)"
log "ime list -s (after disable): $(echo $enabled)"
assert_contains "ime list -a -s lists the keyboard" "$IME_ID" "$all"
assert_absent "ime list -s does not list it while disabled" "$IME_ID" "$enabled"
assert_eq "checklist: enabled row while disabled" "missing" "$(checklist keyboard_enabled)"
assert_eq "checklist: selected row while disabled" "missing" "$(checklist keyboard_selected)"

# ---- enable ---------------------------------------------------------------------------------------
out="$(adb shell ime enable "$IME_ID" | tr -d '\r')"
log "ime enable: $out"
enabled="$(adb shell ime list -s | tr -d '\r')"
assert_contains "ime list -s lists it after enable" "$IME_ID" "$enabled"
assert_eq "checklist: enabled row after enable" "granted" "$(checklist keyboard_enabled)"
assert_eq "checklist: selected row before set" "missing" "$(checklist keyboard_selected)"
screencap "$ROW_DIR/e1_enabled_not_selected.png"

# ---- select ---------------------------------------------------------------------------------------
out="$(adb shell ime set "$IME_ID" | tr -d '\r')"
log "ime set: $out"
assert_eq "default_input_method is the keyboard" "$IME_ID" "$(adb shell settings get secure default_input_method | tr -d '\r')"
assert_eq "checklist: enabled row after set" "granted" "$(checklist keyboard_enabled)"
assert_eq "checklist: selected row after set" "granted" "$(checklist keyboard_selected)"
screencap "$ROW_DIR/e1_both_granted.png"

adb shell input keyevent KEYCODE_HOME
if [ -n "$prior" ] && [ "$prior" != "null" ] && [ "$prior" != "$IME_ID" ]; then adb shell ime set "$prior" >/dev/null; fi
note "restored default ime: $(adb shell settings get secure default_input_method | tr -d '\r')"
row_end
