#!/usr/bin/env bash
# Probe for the round-2 fixes: a PIN keyguard on the AVD; wm density / font_scale read-back; a raw press during a fling.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin EDGE2-probe "keyguard with a PIN; density / font read-back; a press during a fling"
TS=/dev/input/event2
mt_down() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 57 $(( 100 + $1 )); sendevent $TS 3 53 $(( $2 * 32767 / 1080 )); sendevent $TS 3 54 $(( $3 * 32767 / 2340 )); sendevent $TS 3 58 512; sendevent $TS 3 48 8; sendevent $TS 0 0 0;"; }
mt_up() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 57 4294967295; sendevent $TS 0 0 0;"; }
kg() { adb shell dumpsys activity activities | grep -m1 -oE 'mKeyguardShowing=(true|false)|KeyguardShowing=(true|false)' | tr -d '\r'; adb shell dumpsys window | grep -m1 -oE 'isKeyguardShowing=(true|false)|mKeyguardShowing=(true|false)' | tr -d '\r'; }
note "keyguard flags at rest: $(kg | tr '\n' ' ')"
adb shell locksettings set-pin 1111 2>&1 | tr -d '\r' | head -2 | while read -r l; do note "set-pin: $l"; done
adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 2
screencap "$ROW_DIR/kg-woken.png"; note "after wake: $(kg | tr '\n' ' ')"
adb shell wm dismiss-keyguard; sleep 1.5; screencap "$ROW_DIR/kg-bouncer.png"
adb shell input text 1111; adb shell input keyevent KEYCODE_ENTER; sleep 2
screencap "$ROW_DIR/kg-unlocked.png"; note "after the PIN: $(kg | tr '\n' ' '); top: $(resumed)"
adb shell locksettings clear --old 1111 2>&1 | tr -d '\r' | head -2 | while read -r l; do note "clear: $l"; done
adb shell locksettings get-disabled 2>&1 | tr -d '\r' | while read -r l; do note "get-disabled: $l"; done
ensure_start_page
adb shell wm density 560; sleep 5; note "wm density: $(adb shell wm density | tr '\r\n' '  ')"
adb shell input keyevent KEYCODE_HOME; sleep 4; qdump "$ROW_DIR/d560.xml"; note "d560 PEOPLE: $(bounds "$ROW_DIR/d560.xml" tile:slot:PEOPLE)"
adb shell wm density reset; sleep 5; qdump "$ROW_DIR/dreset.xml"; note "reset PEOPLE: $(bounds "$ROW_DIR/dreset.xml" tile:slot:PEOPLE); wm density: $(adb shell wm density | tr '\r\n' '  ')"
adb shell settings put system font_scale 1.3; sleep 4; note "font_scale: $(adb shell settings get system font_scale | tr -d '\r')"
adb shell settings put system font_scale 1.0; sleep 3
restore baseline_layout-tall.json
qdump "$ROW_DIR/f0.xml"; y0=$(bounds "$ROW_DIR/f0.xml" tile:folder:qa | awk '{print $2}')
adb shell "input swipe 540 1900 540 1500 150; $(mt_down 0 540 900) sleep 1.0; $(mt_up 0)"
sleep 1.5; qdump "$ROW_DIR/f1.xml"; y1=$(bounds "$ROW_DIR/f1.xml" tile:folder:qa | awk '{print $2}')
note "fling + press: folder:qa $y0 -> $y1 (moved $((y0 - y1)); the control flung 849)"
row_end
