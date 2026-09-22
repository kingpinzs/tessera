#!/usr/bin/env bash
# EDGE2 — the phase doc's system and liveness edge cases.
#
#   * IME crash → system fallback keyboard, then recovery; liveness (N-01)
#   * keyguard password entry; IME crash on the keyguard and recovery (m9)
#   * landscape apps on a portrait-only shell
#   * a hardware keyboard attached
#   * keyboard raised by the space-bar drag in a field near the bottom of the screen
#   * handedness changed while the dot is held
#
# Every setting a sub-row changes is put back: the lock (RV12: "no lock set"), the rotation, the
# hardware-keyboard switch, the handedness, the raise.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin EDGE2 "edge cases in the system: crash, force-stop, keyguard, landscape, hardware keyboard"
kb_begin
D="$ROW_DIR/edge2.xml"
ime_pid() { adb shell pidof app.tileshell:ime | tr -d '\r'; }
kg_showing() { adb shell dumpsys window | grep -o "KeyguardShowing=[a-z]*" | head -1; }

# ---- IME crash → recovery ---------------------------------------------------------------------------
open_field field_text
kb_dump "$D"
p1="$(ime_pid)"
note "keyboard up, :ime pid $p1; killing it by that pid (Hard Rule 13)"
adb shell run-as app.tileshell kill -9 "$p1"
sleep 2
F="$ROW_DIR/.edge2_fix.xml"; dump_ui "$F"
tap_node "$F" "$FIX:id/field_number"; sleep 1; dump_ui "$F"; tap_node "$F" "$FIX:id/field_text"; sleep 2
p2="$(ime_pid)"
note "after the kill: :ime pid $p2"
assert_ne "Android restarted the keyboard's process" "$p1" "$p2"
kb_dump "$D"
assert_eq "the keyboard is back on the next focus" "yes" "$(has_node "$D" kb_key_q)"
tap_word "$D" "ok"; sleep 0.6
assert_eq "and types" "[ok]" "$(read_mirror text)"
assert_eq "it is still the selected keyboard" "$IME_ID" "$(adb shell settings get secure default_input_method | tr -d '\r')"

# ---- a FORCE-STOP is different: Android deselects the keyboard; the checklist is the way back --------
adb shell am force-stop app.tileshell
sleep 2
after="$(adb shell settings get secure default_input_method | tr -d '\r')"
log "after am force-stop app.tileshell the selected keyboard is: $after"
assert_ne "a force-stop makes Android select another keyboard (measured platform behaviour)" "$IME_ID" "$after"
adb shell am start -W -f 0x10008000 -n app.tileshell/.settings.SettingsActivity --es page CHECKLIST >/dev/null; sleep 2
C="$ROW_DIR/.edge2_cl.xml"; scroll_to_node "$C" "checklist:keyboard_selected:missing" 6 >/dev/null 2>&1
assert_eq "N-01: the checklist shows 'Keyboard selected' missing, which is the way back" "yes" "$(has_node "$C" checklist:keyboard_selected:missing)"
adb shell input keyevent KEYCODE_HOME
adb shell ime set "$IME_ID" >/dev/null

# ---- keyguard password entry, and a crash on the keyguard ---------------------------------------------
adb shell locksettings set-password tessa >/dev/null
lock_and_bouncer() {
  adb shell input keyevent KEYCODE_SLEEP; sleep 1.5
  adb shell input keyevent KEYCODE_WAKEUP; sleep 1.5
  # A swipe up raises the password bouncer. (Run 1 also called `wm dismiss-keyguard` first; with that,
  # the typed password never unlocked — without it, it does, measured by a probe on the same build.)
  adb shell input swipe 540 1900 540 600 250; sleep 2
}
type_on_keyguard() { # word
  local K="$ROW_DIR/.edge2_kg.xml" i
  kb_dump "$K" || return 1
  for ((i = 0; i < ${#1}; i++)); do tap_key "$K" "${1:i:1}"; done
  tap_key "$K" enter
}
lock_and_bouncer
assert_eq "keyguard: locked (the bracket)" "KeyguardShowing=true" "$(kg_showing)"
kb_dump "$D"
screencap "$ROW_DIR/edge2_keyguard.png"
assert_eq "keyguard: the shell keyboard is up on the password bouncer" "yes" "$(has_node "$D" kb_key_q)"
type_on_keyguard tessa; sleep 3
assert_eq "keyguard: typing the password with it unlocks" "KeyguardShowing=false" "$(kg_showing)"
lock_and_bouncer
p1="$(ime_pid)"; adb shell run-as app.tileshell kill -9 "$p1"; sleep 2
# Recovery: the bouncer's field re-asks for input; a tap on it brings the (restarted) keyboard back.
adb shell input tap 540 1150; sleep 2
kb_dump "$D"
screencap "$ROW_DIR/edge2_keyguard_after_crash.png"
assert_eq "keyguard: after the keyboard crashes on it, it comes back (restarted)" "yes" "$(has_node "$D" kb_key_q)"
assert_ne "keyguard: a new :ime process" "$p1" "$(ime_pid)"
type_on_keyguard tessa; sleep 3
assert_eq "keyguard: and the restarted keyboard unlocks it" "KeyguardShowing=false" "$(kg_showing)"
adb shell locksettings clear --old tessa >/dev/null
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard >/dev/null 2>&1; sleep 1
note "lock cleared: $(kg_showing)"

# ---- recovery through Android's own keyboard switcher (m9 / review m16) ----------------------------------
open_field field_text
kb_dump "$D"
adb shell ime set com.android.inputmethod.latin/.LatinIME >/dev/null 2>&1   # a second keyboard to switch to
adb shell ime set "$IME_ID" >/dev/null
adb shell input keyevent KEYCODE_BACK; sleep 1; dump_ui "$F"; tap_node "$F" "$FIX:id/field_text"; sleep 1.5
# The nav bar's keyboard-switch button (the globe) opens Android's picker; the picker lists the keyboards.
kb_dump "$D"
sw="$(python3 - "$D" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for n in re.finditer(r'<node[^>]*>', x):
    t = n.group(0)
    if 'ime_switcher' in t or re.search(r'content-desc="[^"]*(input method|keyboard)[^"]*"', t, re.I):
        m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', t)
        if m:
            l, tt, r, b = map(int, m.groups()); print((l + r) // 2, (tt + b) // 2); break
PY
)"
note "the nav bar's keyboard-switch button: ${sw:-not found}"
if [ -n "$sw" ]; then adb shell input tap $sw; else adb shell input tap 962 2272; fi
sleep 1.5
P="$ROW_DIR/.edge2_picker.xml"; dump_ui "$P"
screencap "$ROW_DIR/edge2_switcher.png"
picked="$(grep -o 'text="[^"]*"' "$P" | tr '\n' ' ')"
note "switcher texts: ${picked:0:300}"
assert_contains "Android's keyboard switcher is reachable with the shell keyboard up, and lists it" "Tessera keyboard" "$picked"
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- a hardware keyboard attached ----------------------------------------------------------------------
adb shell settings put secure show_ime_with_hard_keyboard 0
open_field field_text
kb_dump "$D" || true
assert_eq "hardware keyboard: the soft keyboard stays hidden (Android's own rule)" "no" "$(has_node "$D" kb_key_q)"
adb shell input keyevent KEYCODE_H; adb shell input keyevent KEYCODE_I; sleep 0.8
assert_eq "hardware keyboard: physical keys still type with the soft keyboard hidden (review m7: this proves the keys reach the app, not the service's part in it)" "[hi]" "$(read_mirror text)"
adb shell settings put secure show_ime_with_hard_keyboard 1

# ---- a field near the bottom with the keyboard raised --------------------------------------------------
open_field field_text
kb_dump "$D"
read -r sx0 sy0 <<< "$(node_center "$D" kb_key_space)"
drag_pts "$sx0,$sy0" "$sx0,$((sy0 - 300))" 40 > /dev/null; sleep 1
# field_filter is the last field, below the fold: the fixture focuses it itself (the intent is re-delivered
# to the running screen, so the raised keyboard stays raised). Run 1 tapped it while it was off-screen.
adb shell am start -W -n "$FIX/.MainActivity" -e focus field_filter >/dev/null; sleep 2
kb_dump "$D"
read -r _ ptop _ _ <<< "$(bounds "$D" kb_panel)"
dump_ui "$F"
read -r _ ft _ fb <<< "$(bounds "$F" "$FIX:id/field_filter")"
note "raised panel top $ptop; field_filter [$ft..$fb]"
assert_eq "raised keyboard: the app keeps the focused bottom field above the panel" "yes" "$([ "$fb" -le "$ptop" ] && echo yes || echo no)"
read -r sx0 sy0 <<< "$(node_center "$D" kb_key_space)"
drag_pts "$sx0,$sy0" "$sx0,$((sy0 + 1200))" 40 > /dev/null; sleep 1

# ---- landscape app on a portrait-only shell ------------------------------------------------------------
rot_acc="$(adb shell settings get system accelerometer_rotation | tr -d '\r')"
rot_user="$(adb shell settings get system user_rotation | tr -d '\r')"
adb shell settings put system accelerometer_rotation 0
adb shell am start -S -W -n org.fossify.notes/.activities.MainActivity >/dev/null
adb shell settings put system user_rotation 1
sleep 3
N="$ROW_DIR/.edge2_notes.xml"; dump_ui "$N"
ed="$(grep -o 'resource-id="org.fossify.notes:id/[a-z_]*text[a-z_]*"' "$N" | head -1 | sed 's/resource-id="//; s/"$//')"
[ -n "$ed" ] && tap_node "$N" "$ed" || adb shell input tap 1170 400
sleep 2
kb_dump "$D"
screencap "$ROW_DIR/edge2_landscape.png"
read -r pl pt pr pb <<< "$(bounds "$D" kb_panel)"
read -r ql qt qr qb <<< "$(bounds "$D" kb_key_q)"
note "landscape: panel [$pl,$pt][$pr,$pb]; q [$ql,$qt][$qr,$qb]"
# The IME window in landscape stops at the side nav bar and the cutout; the keys span THAT width.
usable="$(adb shell dumpsys window | grep -m1 -o 'InputMethod.*mFrame=\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | sed 's/.*mFrame=\[\([0-9]*\),[0-9]*\]\[\([0-9]*\),.*/\2 \1/' | awk '{print $1-$2}')"
[ -z "$usable" ] && usable=$((pr - pl))
note "landscape: IME window width $usable"
read -r bl _ br _ <<< "$(bounds "$D" kb_key_bksp)"
assert_eq "landscape: the whole key grid is inside the window (backspace's right edge is on screen)" "yes" "$([ "$br" -le "$pr" ] && [ "$br" -gt $((pr - 20)) ] && echo yes || echo no)"
assert_within "landscape: keys widen with the width (q = 130 phys x window width / 1440)" "$(python3 -c "print(130*$((pr - pl))/1440)")" $((qr - ql)) 2
assert_eq "landscape: the keyboard leaves most of the screen to the app (panel < 60 % of the height)" "yes" "$([ $((pb - pt)) -lt 648 ] && echo yes || echo no)"
tap_word "$D" "land"; sleep 1; dump_ui "$N"
assert_contains "landscape: typing works" "and" "$(grep -o 'text="[^"]*"' "$N" | tr '\n' ' ' | tr 'A-Z' 'a-z')"
for _ in 1 2 3 4; do tap_key "$D" bksp; done
adb shell settings put system user_rotation "${rot_user:-0}"
adb shell settings put system accelerometer_rotation "${rot_acc:-0}"
sleep 2

# ---- handedness changed while the dot is held ----------------------------------------------------------
open_field field_text
kb_dump "$D"
read -r dx dy <<< "$(node_center "$D" kb_cursor_dot)"
adb shell input swipe $dx $dy $dx $dy 4000 &
hold=$!
sleep 1.2
# Settings comes up over the held dot (the keyboard hides, and the hold is cancelled under it); the
# setting is changed once that injected hold has ended — run 1 tapped while the swipe's pointer was still
# down, and the tap never landed.
adb shell am start -W -f 0x10008000 -n app.tileshell/.settings.SettingsActivity --es page KEYBOARD >/dev/null
wait $hold; sleep 0.5
K="$ROW_DIR/.edge2_kp.xml"; scroll_to_node "$K" keyboard_cursor_left 4 >/dev/null 2>&1; tap_node "$K" keyboard_cursor_left; sleep 0.5
open_field field_text
kb_dump "$D"; screencap "$ROW_DIR/edge2_hand_changed.png"
read -r l t r b <<< "$(bounds "$D" kb_cursor_dot)"
# Review m8: the change cannot land WHILE the dot is held — opening Settings ends the hold (the keyboard
# hides). What this proves is that the interrupted hold leaves nothing behind and the change takes.
assert_within "handedness changed after an interrupted hold: the next keyboard has the dot on the left (1077.5 phys)" 1077.5 "$(python3 -c "print((($l+$r)/2)/0.75)")" 3
read -r ql qt _ _ <<< "$(bounds "$D" kb_key_q)"
set -- $(python3 "$HERE/measure.py" px "$ROW_DIR/edge2_hand_changed.png" $((ql + 6)) $((qt + 6)))
assert_within "and it is not stuck dimmed from the interrupted hold (key 48)" 48 "$1" 6
adb shell am start -W -f 0x10008000 -n app.tileshell/.settings.SettingsActivity --es page KEYBOARD >/dev/null; sleep 1.5
scroll_to_node "$K" keyboard_cursor_right 4 >/dev/null 2>&1; tap_node "$K" keyboard_cursor_right; sleep 0.5

kb_end
row_end
