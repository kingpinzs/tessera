#!/usr/bin/env bash
# EDGE — the phase doc's Edge Cases list, run as a list (the re-judge, R2-1; the skill's gate rule and Hard Rule 5).
# Each bullet the other rows do not already carry is a sub-step here; EDGE/EDGE.txt's index (written by this row, from
# edge_index.tsv) maps EVERY bullet to its evidence: a sub-step of this row, another row or JVM test by name, or a
# recorded NEEDS-HUMAN / phone row. Each sub-step starts from a plain Start and takes its MARK just before its action.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin EDGE "the Edge Cases list: flip, fling, call, keyguard, listener restart, process death, second finger, show more tiles, folder dissolve, labels, theme, transparency, RV10"
seed_fixtures
restore baseline_layout.json

# fresh_burst [tile id] [dump name]: hold the tile (default the fixture) from a plain Start and release; the dump is the burst.
fresh_burst() {
  local id="${1:-tile:$A_KEY}" out="$ROW_DIR/${2:-B}.xml"
  qdump "$ROW_DIR/.rest.xml"
  read -r FX FY <<< "$(center "$ROW_DIR/.rest.xml" "$id")"
  hold "$FX" "$FY" 1.0
  qdump "$out"
  [ "$(has_node "$out" quick_burst)" = yes ] || note "fresh_burst: no burst on $id"
}
sat_rects() { # dump -> "id l t r b" per satellite square and label
  python3 - "$1" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
for m in re.finditer(r'resource-id="(quick_sat(?:_label)?:\d)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    print(m.group(1), *m.groups()[1:])
PY
}
epx_same() { # base.xml base_width other.xml other_width -> "PASS <max d>" or "FAIL <why>": every satellite rect in epx within 1.0
  python3 - "$@" <<'PY'
import re, sys
def load(fn, w):
    s = open(fn).read(); k = int(w) / 360.0
    return {m.group(1): tuple(int(v) / k for v in m.groups()[1:])
            for m in re.finditer(r'resource-id="(quick_sat(?:_label)?:\d)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)}
a, b = load(sys.argv[1], sys.argv[2]), load(sys.argv[3], sys.argv[4])
if not a: print("FAIL no satellites in the base"); sys.exit()
if set(a) != set(b): print("FAIL ids differ", sorted(a), sorted(b)); sys.exit()
d = max(max(abs(x - y) for x, y in zip(a[k], b[k])) for k in a)
print(("PASS" if d <= 1.0 else "FAIL") + f" max |d| = {d:.2f} epx over {len(a)} rects")
PY
}
# The emulator's touchscreen (virtio multi-touch, slotted protocol B; adb's shell is root on this AVD), for what
# `input` cannot do: a second pointer, and a press landing within milliseconds of a fling.
TS=/dev/input/event2
mt_x() { echo $(( $1 * 32767 / 1080 )); }
mt_y() { echo $(( $1 * 32767 / 2340 )); }
# The device has an ABS_MT_PRESSURE axis and no BTN_TOUCH key: a contact with pressure 0 reads as hovering, so every
# down carries a pressure and a touch-major (the first run of this row sent none, and nothing touched).
mt_down() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 57 $(( 100 + $1 )); sendevent $TS 3 53 $(mt_x "$2"); sendevent $TS 3 54 $(mt_y "$3"); sendevent $TS 3 58 512; sendevent $TS 3 48 8; sendevent $TS 0 0 0;"; }
mt_move() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 53 $(mt_x "$2"); sendevent $TS 3 54 $(mt_y "$3"); sendevent $TS 3 58 512; sendevent $TS 0 0 0;"; }
mt_up() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 57 4294967295; sendevent $TS 0 0 0;"; }

log "--- a hold during a live flip: the satellites stay still, the flip continues (T11-41) ---"
verb_a() { adb shell am start -W -n "$A_PKG/app.tileshell.testclient.VerbActivity" "$@" >/dev/null 2>&1; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 3; }
verb_a --es verb tile.update --es text FlipFace
ensure_start_page
MARK="$(ring_mark)"
for i in $(seq 1 20); do ring_since "$MARK" | grep -q "\[tile_anim\] tile=$A_KEY kind=FLIP" && break; sleep 1; done
assert_contains "the fixture's tile flips on its own (the precondition)" "[tile_anim] tile=$A_KEY kind=FLIP" "$(ring_since "$MARK")"
HMARK="$(ring_mark)"
fresh_burst "tile:$A_KEY" flip-1
sat_rects "$ROW_DIR/flip-1.xml" > "$ROW_DIR/flip-1.rects"
for i in 1 2 3 4 5 6; do [ "$(rest_lines "$HMARK" | grep -c .)" -ge 4 ] && break; sleep 0.5; done   # the open's own rest= lines first
MARK="$(ring_mark)"
for i in $(seq 1 25); do ring_since "$MARK" | grep -q "\[tile_anim\] tile=$A_KEY kind=FLIP" && break; sleep 1; done
sleep 1; qdump "$ROW_DIR/flip-2.xml"; sat_rects "$ROW_DIR/flip-2.xml" > "$ROW_DIR/flip-2.rects"
assert_contains "the tile flipped under the open burst" "[tile_anim] tile=$A_KEY kind=FLIP" "$(ring_since "$MARK")"
assert_eq "the burst is still open after the flip" yes "$(has_node "$ROW_DIR/flip-2.xml" quick_burst)"
assert_absent "…and nothing closed it" "burst closed" "$(quick_since "$MARK")"
assert_eq "the satellites stayed still (every square and label, before and after the flip)" "$(cat "$ROW_DIR/flip-1.rects")" "$(cat "$ROW_DIR/flip-2.rects")"
assert_ne "(there were satellites to compare)" "" "$(cat "$ROW_DIR/flip-1.rects")"
# The ring's own check (the round-2 re-judge, D2-2 / EV-7): a tracked-bounds change re-logs `satellite i rest=`, so none may
# appear across the flip — the two dumps above are both taken outside a flip, which lasts 108 ms.
assert_eq "no satellite re-rested across the flip (the tracked slot never moved, T11-41)" "" "$(rest_lines "$MARK")"
verb_a --es verb tile.clear
c6

log "--- a hold while Start is flinging: the scroll consumes the press — no hold, no burst (phase 02's rule) ---"
# The first cut's raw drag never flung (the page stopped at the drag minus the touch slop; the round-2 re-judge, D2-1 /
# EV-1). Now: a control swipe with no press must carry the page past the finger's 400 px (FLING-probe: 849) without
# reaching the end; the same swipe with a raw press ~50 ms after its release, held 1.0 s, must leave the page past the
# finger (it was still flinging when the press landed) and short of the control (the press stopped it); then the same
# press with the page at rest is a hold (the positive control: the press reaches a tile).
tile_under() { python3 - "$1" "$2" "$3" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); x, y = int(sys.argv[2]), int(sys.argv[3])
for m in re.finditer(r'resource-id="(tile:[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    l, t, r, b = map(int, m.groups()[1:])
    if l <= x < r and t <= y < b: print(m.group(1)); break
PY
}
fy() { bounds "$1" tile:folder:qa | awk '{print $2}'; }
restore baseline_layout-tall.json
qdump "$ROW_DIR/fling-control-before.xml"
adb shell input swipe 540 1900 540 1500 150; sleep 2.5
qdump "$ROW_DIR/fling-control-after.xml"
CTRL=$(( $(fy "$ROW_DIR/fling-control-before.xml") - $(fy "$ROW_DIR/fling-control-after.xml") ))
note "control (no press): the page travelled $CTRL px for the finger's 400"
assert_eq "control: the swipe flings (travel past the finger's 400 px + 100)" yes "$([ "$CTRL" -gt 500 ] && echo yes || echo "no ($CTRL px)")"
restore baseline_layout-tall.json
qdump "$ROW_DIR/fling-before.xml"
MARK="$(ring_mark)"
adb shell "input swipe 540 1900 540 1500 150; $(mt_down 0 540 900) sleep 1.0; $(mt_up 0)"
sleep 1.5; qdump "$ROW_DIR/fling-after.xml"
D=$(( $(fy "$ROW_DIR/fling-before.xml") - $(fy "$ROW_DIR/fling-after.xml") ))
note "with the press: the page travelled $D px (control $CTRL, the finger 400)"
assert_eq "the press landed while Start was flinging and stopped it (400 + 40 < travel < control − 30)" yes \
  "$([ "$D" -gt 440 ] && [ "$D" -lt $(( CTRL - 30 )) ] && echo yes || echo "no ($D px, control $CTRL)")"
S="$(ring_since "$MARK")"
assert_absent "no hold" "[edit] hold" "$S"
assert_absent "no burst" "[quick] burst on" "$S"
assert_absent "nothing launched" "[launch]" "$S"
assert_eq "no edit mode, no burst on the page" "no no" "$(has_node "$ROW_DIR/fling-after.xml" edit_disc:unpin) $(has_node "$ROW_DIR/fling-after.xml" quick_burst)"
UNDER="$(tile_under "$ROW_DIR/fling-after.xml" 540 900)"
note "the press point (540,900) is on ${UNDER:-no tile} once the page stopped"
MARK="$(ring_mark)"
adb shell "$(mt_down 0 540 900) sleep 1.0; $(mt_up 0)"; sleep 1
assert_contains "positive control: the same press with the page at rest is a hold (${UNDER#tile:})" "[edit] hold 783ms on ${UNDER#tile:}" "$(ring_since "$MARK")"
c6
restore baseline_layout.json

log "--- a second finger while a burst is open: ignored (as phase 02 ignores it) ---"
qdump "$ROW_DIR/.rest.xml"
read -r FX FY <<< "$(center "$ROW_DIR/.rest.xml" "tile:$A_KEY")"
read -r EX EY <<< "$(empty_point "$ROW_DIR/.rest.xml" 300 1850)"
MARK="$(ring_mark)"
adb shell "$(mt_down 0 "$FX" "$FY") sleep 1.0;"
qdump "$ROW_DIR/finger1.xml"
adb shell "$(mt_down 1 "$EX" "$EY") sleep 0.15; $(mt_up 1)"
sleep 0.5; qdump "$ROW_DIR/finger2.xml"
adb shell "$(mt_up 0 last)"
sleep 0.8; qdump "$ROW_DIR/finger-released.xml"
S="$(quick_since "$MARK")"
assert_contains "the first finger's hold opened the burst" "burst on" "$S"
assert_eq "the burst is open while the first finger holds" yes "$(has_node "$ROW_DIR/finger1.xml" quick_burst)"
assert_eq "a second finger down and up elsewhere: the burst stays" yes "$(has_node "$ROW_DIR/finger2.xml" quick_burst)"
assert_eq "…and after the first finger lifts" yes "$(has_node "$ROW_DIR/finger-released.xml" quick_burst)"
assert_absent "nothing closed it" "burst closed" "$S"
# The control (the round-2 re-judge, EV-1): the same slot-1 contact ALONE, the burst still open, is a tap elsewhere and closes
# it — so the contact reaches the app, and only its being a second pointer kept the burst open above.
MARK="$(ring_mark)"
adb shell "$(mt_down 1 "$EX" "$EY") sleep 0.15; $(mt_up 1)"; sleep 1
assert_contains "control: the same contact alone closes the burst (tap elsewhere)" "burst closed: tap elsewhere" "$(quick_since "$MARK")"
c6

log "--- in edit mode no press is a hold (T11-23): a 1.0-s still press on another tile with a burst open only closes it ---"
# The round-2 re-judge, EV-4 (i): E5 ran its long press after closing the burst; here the burst is open. Then the held tile
# with no burst open: a 1.0-s still press is a tap, and exits edit mode.
fresh_burst
disc0="$(bounds "$ROW_DIR/B.xml" edit_disc:unpin)"
read -r TX TY <<< "$(center "$ROW_DIR/B.xml" tile:shell:cortana)"
assert_eq "Tess's centre is outside every satellite" none "$(python3 - "$ROW_DIR/B.xml" "$TX" "$TY" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); x, y = int(sys.argv[2]), int(sys.argv[3])
hit = [m.group(1) for m in re.finditer(r'resource-id="(quick_sat[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
       if int(m.group(2)) <= x < int(m.group(4)) and int(m.group(3)) <= y < int(m.group(5))]
print(" ".join(hit) or "none")
PY
)"
MARK="$(ring_mark)"
hold "$TX" "$TY" 1.0; sleep 0.5
qdump "$ROW_DIR/press-other.xml"
S="$(quick_since "$MARK")"
assert_contains "a 1.0-s still press on another tile closed the burst (tap elsewhere)" "burst closed: tap elsewhere" "$S"
assert_absent "…and was no hold" "[edit] hold" "$(ring_since "$MARK")"
assert_eq "…and opened no second burst" 0 "$(echo "$S" | grep -c 'burst on')"
assert_eq "…and the selection stays on the fixture (its discs unchanged)" "$disc0" "$(bounds "$ROW_DIR/press-other.xml" edit_disc:unpin)"
read -r HX HY <<< "$(center "$ROW_DIR/press-other.xml" "tile:$A_KEY")"
MARK="$(ring_mark)"
hold "$HX" "$HY" 1.0; sleep 1
qdump "$ROW_DIR/press-held.xml"
assert_eq "a 1.0-s still press on the held tile, no burst open, exits edit mode" no "$(has_node "$ROW_DIR/press-held.xml" edit_disc:unpin)"
assert_absent "…and was no hold" "[edit] hold" "$(ring_since "$MARK")"
c6

log "--- the burst open at an incoming call: ringing is a heads-up over Start; answering brings the call in front (stop) ---"
# The Decision's rule is "Start stopping closes the burst (stop)". An incoming call on an unlocked phone rings as a
# heads-up: Start stays resumed and the burst stays, as under any notification; the call's own window in front (answer
# it, or a full-screen ring on a locked phone) stops Start and closes it (first run of this sub-step, 2026-09-24).
fresh_burst
MARK="$(ring_mark)"
adb emu gsm call 5551234 >/dev/null 2>&1; sleep 4
qdump "$ROW_DIR/call-ringing.xml"; screencap "$ROW_DIR/call-ringing.png"
assert_contains "ringing: a heads-up (its Answer button is on screen)" "ANSWER" "$(grep -o 'text="ANSWER"' "$ROW_DIR/call-ringing.xml")"
assert_contains "ringing: Start is still the resumed activity" "app.tileshell/.StartActivity" "$(resumed)"
assert_absent "ringing: nothing closed the burst" "burst closed" "$(quick_since "$MARK")"
# Answer from the heads-up (the modem's accept and KEYCODE_CALL answer without bringing the call's window up): a fresh dump
# before each tap, up to 3, until the call's window is in front (the round-2 smoke's first tap left the call ringing out).
for try in 1 2 3; do
  qdump "$ROW_DIR/call-answer-$try.xml"
  read -r AX AY <<< "$(text_xy "$ROW_DIR/call-answer-$try.xml" ANSWER)"
  [ -n "$AX" ] || { note "answer try $try: no ANSWER on screen"; break; }
  tap_xy "$AX" "$AY"; sleep 3
  note "answer try $try at ($AX,$AY): top $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //;s/ .*//')"
  resumed | grep -q InCallActivity && break
done
sleep 1
screencap "$ROW_DIR/call-answered.png"
note "answered: top activity $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //;s/ .*//')"
assert_absent "answered: the call's window is in front of Start" "app.tileshell/.StartActivity" "$(resumed)"
assert_contains "answered: Start stopped, the burst closed (stop)" "burst closed: stop" "$(quick_since "$MARK")"
adb emu gsm cancel 5551234 >/dev/null 2>&1; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 2
c6

log "--- the burst open at the keyguard: a PIN for this sub-step, screen off, wake onto the keyguard, unlock ---"
# This AVD has no lock screen, so the first cut's wake went straight back to Start (the round-2 re-judge, EV-1): a PIN is
# set for this sub-step only, the keyguard is asserted showing after the wake, the PIN unlocks, and the PIN is cleared.
kg() { adb shell dumpsys window | grep -m1 -oE 'isKeyguardShowing=(true|false)' | tr -d '\r'; }
adb shell locksettings set-pin 1111 >/dev/null 2>&1
fresh_burst
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_SLEEP; sleep 2
adb shell input keyevent KEYCODE_WAKEUP; sleep 2
screencap "$ROW_DIR/keyguard.png"
KG1="$(kg)"
adb shell wm dismiss-keyguard; sleep 1.5; adb shell input text 1111; adb shell input keyevent KEYCODE_ENTER; sleep 2.5
KG2="$(kg)"
adb shell locksettings clear --old 1111 >/dev/null 2>&1; adb shell wm dismiss-keyguard >/dev/null 2>&1
note "lock screen after clearing: disabled=$(adb shell locksettings get-disabled | tr -d '\r')"
assert_eq "the keyguard is showing after the wake" "isKeyguardShowing=true" "$KG1"
assert_eq "the PIN unlocked it" "isKeyguardShowing=false" "$KG2"
ensure_start_page
qdump "$ROW_DIR/keyguard-after.xml"
assert_contains "the burst closed (stop)" "burst closed: stop" "$(quick_since "$MARK")"
assert_eq "Start after the keyguard: no burst" no "$(has_node "$ROW_DIR/keyguard-after.xml" quick_burst)"
note "edit mode after the keyguard (phase 02's): $(has_node "$ROW_DIR/keyguard-after.xml" edit_disc:unpin)"
c6

log "--- the burst open at a notification-listener restart ---"
# The user's path to a listener restart is Settings' notification access: another activity, so Start stops first.
fresh_burst
MARK="$(ring_mark)"
adb shell am start -a android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS >/dev/null 2>&1; sleep 2
adb shell cmd notification disallow_listener app.tileshell/.feeds.TileNotificationListener; sleep 2
adb shell cmd notification allow_listener app.tileshell/.feeds.TileNotificationListener; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 3; ensure_start_page
qdump "$ROW_DIR/listener-after.xml"
S="$(ring_since "$MARK")"
assert_contains "Settings in front stopped Start: the burst closed (stop)" "burst closed: stop" "$S"
assert_contains "the listener restarted (connected again)" "[notif] listener connected" "$S"
assert_eq "Start after the restart: no burst" no "$(has_node "$ROW_DIR/listener-after.xml" quick_burst)"
c6
# The same restart with Start in front (only adb can do this): recorded as seen.
fresh_burst
MARK="$(ring_mark)"
adb shell cmd notification disallow_listener app.tileshell/.feeds.TileNotificationListener; sleep 2
adb shell cmd notification allow_listener app.tileshell/.feeds.TileNotificationListener; sleep 3
qdump "$ROW_DIR/listener-inplace.xml"
note "a rebind with Start in front: listener connected $(ring_since "$MARK" | grep -c 'listener connected'); burst still open: $(has_node "$ROW_DIR/listener-inplace.xml" quick_burst); close lines: $(quick_since "$MARK" | grep -c 'burst closed')"
c6

log "--- process death with a burst open: no state persists; Start restarts plain ---"
fresh_burst "tile:$A_KEY" death-before
assert_eq "a burst is open before the kill" yes "$(has_node "$ROW_DIR/death-before.xml" quick_burst)"
PID0="$(adb shell pidof app.tileshell | tr -d '\r')"
ring_save
adb shell kill -9 "$PID0"; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 5; ensure_start_page
qdump "$ROW_DIR/death-after.xml"
PID1="$(adb shell pidof app.tileshell | tr -d '\r')"
note "pid $PID0 -> $PID1"
assert_ne "a new shell process" "$PID0" "$PID1"
assert_eq "Start restarted plain: page, no burst, no edit mode" "yes no no" \
  "$(has_node "$ROW_DIR/death-after.xml" start_page) $(has_node "$ROW_DIR/death-after.xml" quick_burst) $(has_node "$ROW_DIR/death-after.xml" edit_disc:unpin)"
c6

log "--- Show more tiles toggled with a burst open: Start stops first (the burst closes, stop) ---"
adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
scroll_to_node "$ROW_DIR/.settings.xml" theme_show_more_tiles 10 >/dev/null
MORE0="$(grep -oE 'resource-id="theme_show_more_tiles"[^>]*' "$ROW_DIR/.settings.xml" | grep -oE 'checked="(true|false)"' | head -1)"
note "show more tiles at the start: $MORE0"
adb shell input keyevent KEYCODE_HOME; sleep 2.5; ensure_start_page
fresh_burst
MARK="$(ring_mark)"
if [ "$MORE0" = 'checked="true"' ]; then set_more_tiles off; else set_more_tiles on; fi
qdump "$ROW_DIR/more-after.xml"
assert_contains "the burst closed when Start stopped (stop)" "burst closed: stop" "$(quick_since "$MARK")"
assert_eq "Start after the toggle: no burst" no "$(has_node "$ROW_DIR/more-after.xml" quick_burst)"
note "edit mode after the toggle (phase 02's): $(has_node "$ROW_DIR/more-after.xml" edit_disc:unpin)"
if [ "$MORE0" = 'checked="true"' ]; then set_more_tiles on; else set_more_tiles off; fi
c6

log "--- unpin of the last tile in a two-tile folder, from its burst: the folder dissolves (phase 02 H19), the burst closes ---"
restore baseline_layout-folder2.json
qdump "$ROW_DIR/folder2-rest.xml"
tap_node "$ROW_DIR/folder2-rest.xml" tile:folder:qa; sleep 1.5
fresh_burst "tile:member:$A_KEY" folder2-burst
assert_eq "the fixture bursts inside the expanded band" yes "$(has_node "$ROW_DIR/folder2-burst.xml" quick_burst)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/folder2-burst.xml" edit_disc:unpin; sleep 1.5
qdump "$ROW_DIR/folder2-after.xml"
assert_contains "the burst closed (unpin)" "burst closed: unpin" "$(quick_since "$MARK")"
L="$(layout_json)"
assert_eq "the folder dissolved" "[]" "$(echo "$L" | python3 -c 'import json,sys; print([f["id"] for f in json.load(sys.stdin)["folders"]])')"
assert_contains "its other tile is on Start" "slot:MAPS" "$(echo "$L" | python3 -c 'import json,sys; print(" ".join(o["key"] for o in json.load(sys.stdin)["order"]))')"
assert_absent "the fixture is unpinned" "$A_KEY" "$(echo "$L" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(" ".join([o["key"] for o in d["order"]] + [m["key"] for f in d["folders"] for m in f["members"]]))')"
c6
restore baseline_layout.json

log "--- a shortcut with a blank short label, and a very long one (tileclient-b's labels verb) ---"
verb_b_start labels
note "tileclient-b: $(adb shell dumpsys shortcut | awk '/Package: app.tileshell.testclient.b /{f=1; next} f && /Package: /{f=0} f' | grep -oE 'ShortcutInfo \{id=[a-z_]*' | sort -u | tr '\n' ' ')"
fresh_burst "tile:$B_KEY" labels
lbl() { node_text "$ROW_DIR/labels.xml" "quick_sat_label:$1"; }
note "labels: [$(lbl 0)] [$(lbl 1)] [$(lbl 2)]"
assert_eq "three satellites (Dyn, the blank one, the long one)" "yes yes yes no" "$(for i in 0 1 2 3; do has_node "$ROW_DIR/labels.xml" "quick_sat:$i"; done | tr '\n' ' ' | sed 's/ $//')"
assert_eq "the blank label's line shows nothing (never the id)" "" "$(lbl 1 | tr -d ' ')"
assert_absent "…the id is never shown" "qa_blank" "$(cat "$ROW_DIR/labels.xml")"
# The label box is fixed-size by construction (QuickBurst.kt), so its size proves nothing about the text: the ellipsis is
# labels.png, judged at H9 (the round-2 re-judge, EV-6).
screencap "$ROW_DIR/labels.png"
note "the long label's node carries the full text; its drawn line is labels.png (H9)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/labels.xml" quick_sat:1; sleep 3
assert_contains "the blank-labelled satellite still runs" "qa_blank: startShortcut ok" "$(ring_since "$MARK" | grep -F '[quick]')"
c6
verb_b_start reset

log "--- theme Light / Dark: the satellite fill is the accent (as on a tile); the label takes the theme's text colour ---"
adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
scroll_to_node "$ROW_DIR/.settings.xml" theme_mode_light 10 >/dev/null
MODE0="$(grep -oE 'resource-id="theme_mode_light"[^>]*' "$ROW_DIR/.settings.xml" | grep -oE 'selected="(true|false)"' | head -1)"
note "light mode selected at the start: $MODE0"
adb shell input keyevent KEYCODE_HOME; sleep 2
theme_check() { # mode
  local tm; tm="$(ring_mark)"
  settings_tap "theme_mode_$1"; adb shell input keyevent KEYCODE_HOME; sleep 2.5; ensure_start_page
  local want; want="$(echo "$1" | tr a-z A-Z)"
  echo "$(ring_since "$tm" | grep -F 'start theme changed' | grep -c "theme=$want,")" > "$ROW_DIR/theme-$1.ring"
  fresh_burst "tile:$A_KEY" "theme-$1"; screencap "$ROW_DIR/theme-$1.png"
  read -r GX GY <<< "$(empty_point "$ROW_DIR/theme-$1.xml" 300 1850)"
  python3 - "$ROW_DIR/theme-$1.png" "$ROW_DIR/theme-$1.xml" "$1" "$A_KEY" "$GX" "$GY" <<'PY'
import re, sys
import numpy as np
from PIL import Image
img = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(int); s = open(sys.argv[2]).read(); mode = sys.argv[3]
def rect(i):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(i), s); return tuple(map(int, m.groups())) if m else None
sq, lab, tile = rect("quick_sat:0"), rect("quick_sat_label:0"), rect("tile:" + sys.argv[4])
# the fill: a patch inside the square's corner (clear of the glyph) against the same patch inside the held tile's corner
f = img[sq[1] + 3:sq[1] + 9, sq[0] + 3:sq[0] + 9].reshape(-1, 3).mean(0); t = img[tile[1] + 3:tile[1] + 9, tile[0] + 3:tile[0] + 9].reshape(-1, 3).mean(0)
Y = [0.299, 0.587, 0.114]
luma = (img[lab[1]:lab[3], lab[0]:lab[2]] * Y).sum(2)
dark_ink, light_ink = luma.min(), luma.max(); bg = np.median(luma)   # the label's own ground (a page or a tile under it)
gx, gy = int(sys.argv[5]), int(sys.argv[6]); page = (img[gy - 5:gy + 5, gx - 5:gx + 5] * Y).sum(2).mean()
fill_ok = np.abs(f - t).max() <= 12
# The round-2 re-judge, EV-3: the page ground from a clear patch, and the ink against the label's own ground.
page_ok = page > 200 if mode == "light" else page < 30
ink_ok = dark_ink < bg - 60 if mode == "light" else light_ink > bg + 120
print(f"page={round(page)} fill={f.round().astype(int).tolist()} tile={t.round().astype(int).tolist()} label ground={round(bg)} ink {'min' if mode == 'light' else 'max'}={round(dark_ink if mode == 'light' else light_ink)} -> {'PASS' if fill_ok and ink_ok and page_ok else 'FAIL'}")
PY
}
for m in light dark; do
  r="$(theme_check $m)"; note "$m: $r"
  assert_eq "theme $m: the ring shows the change to $(echo $m | tr a-z A-Z)" 1 "$(cat "$ROW_DIR/theme-$m.ring")"
  case "$r" in *PASS) _verdict PASS "theme $m: satellite fill = the tile's accent; label in the theme's text colour" "${r:0:110}" ;; *) _verdict FAIL "theme $m: satellite fill = the tile's accent; label in the theme's text colour" "${r:0:110}" ;; esac
  c6
done
if [ "$MODE0" = 'selected="true"' ]; then settings_tap theme_mode_light; else settings_tap theme_mode_dark; fi
adb shell input keyevent KEYCODE_HOME; sleep 2; c6

log "--- the X5 transparency slider at 0 % and 100 %: the satellite fill follows the tile's ---"
# The slider shows only while a background picture is set (StartThemePage.kt), and tile alpha is 1 - 0.8 x transparency
# over it (StartPage.kt); a satellite draws the accent at that same alpha (QuickBurst.kt). So each satellite square must
# read alpha x accent + (1 - alpha) x whatever lies behind it — a neighbouring tile, mostly — at 0 % (the accent) and at
# 100 % (a fifth of it). The first cut compared a satellite with the held tile, whose backdrop differs (2026-09-24).
python3 -c "
from PIL import Image; Image.new('RGB', (1080, 2340), (200, 60, 40)).save('$ROW_DIR/qa-edge-bg.png')"
adb push "$ROW_DIR/qa-edge-bg.png" /sdcard/Pictures/qa-edge-bg.png >/dev/null 2>&1
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1; sleep 3
adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
scroll_to_node "$ROW_DIR/.settings.xml" theme_background_choose 10 >/dev/null
tap_node "$ROW_DIR/.settings.xml" theme_background_choose; sleep 2.5
# The picker opens as a half-height sheet whose dump bounds do not match the screen until it settles open: swipe it
# fully up first (the first run's tap, from the half sheet's dump, landed on the scrim and dismissed the picker).
adb shell input swipe 540 850 540 150 400; sleep 2
qdump "$ROW_DIR/picker.xml"; screencap "$ROW_DIR/picker.png"
pick="$(python3 - "$ROW_DIR/picker.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); best = None
for n in re.finditer(r"<node[^>]*>", s):
    # a thumbnail's description, not the "Photos" tab's
    n = n.group(0); d = re.search(r'content-desc="(Photo taken on [^"]*)"', n); b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if d and b:
        x1, y1, x2, y2 = map(int, b.groups())
        if best is None or (y1, x1) < best[0]: best = ((y1, x1), (x1 + x2) // 2, (y1 + y2) // 2, d.group(1))
print(f"{best[1]} {best[2]} {best[3]}" if best else "")
PY
)"
note "picker: the newest photo: [$pick]"
read -r PX PY _ <<< "$pick"; tap_xy "$PX" "$PY"; sleep 3
scroll_to_node "$ROW_DIR/.settings.xml" theme_transparency 10 >/dev/null
T0="$(grep -oE 'content-desc="Tile transparency [0-9]+ %"' "$ROW_DIR/.settings.xml" | grep -oE '[0-9]+' | head -1)"
assert_ne "a background picture is set: the transparency slider is there" "" "$T0"
note "transparency at the start: ${T0}%"
set_transparency() { # percent -> the value the slider then reads
  adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
  scroll_to_node "$ROW_DIR/.settings.xml" theme_transparency 10 >/dev/null
  read -r sl st sr sb <<< "$(bounds "$ROW_DIR/.settings.xml" theme_transparency)"
  local x=$(( sl + (sr - sl) * $1 / 100 )); [ "$1" -eq 0 ] && x=$(( sl + 1 )); [ "$1" -eq 100 ] && x=$(( sr - 2 ))
  tap_xy "$x" $(( (st + sb) / 2 )); sleep 1; dump_ui "$ROW_DIR/.settings-after.xml"
  grep -oE 'content-desc="Tile transparency [0-9]+ %"' "$ROW_DIR/.settings-after.xml" | grep -oE '[0-9]+' | head -1
  adb shell input keyevent KEYCODE_HOME; sleep 2.5; ensure_start_page
}
for pct in 0 100; do
  xm="$(ring_mark)"
  got="$(set_transparency "$pct" | tail -1)"
  assert_eq "the slider reads ${pct} %" "$pct" "$got"
  stored="$(ring_since "$xm" | grep -F 'start theme changed' | tail -1 | grep -oE 'transparency=[0-9.E-]+' | cut -d= -f2)"
  assert_eq "the stored transparency is at the ${pct} % end (the round-2 re-judge, EV-13: $stored)" yes \
    "$(python3 -c "import sys; v=float(sys.argv[1]); print('yes' if (v <= 0.01 if $pct == 0 else v >= 0.99) else 'no')" "${stored:-nan}")"
  fresh_burst "tile:$A_KEY" "x5-$pct"; screencap "$ROW_DIR/x5-$pct.png"
  # The same screen without the satellites: a tap on empty space closes the burst and edit mode stays (E5), so what lies
  # behind each satellite square (a neighbouring tile, or the picture) is read from the second capture.
  read -r EX EY <<< "$(empty_point "$ROW_DIR/x5-$pct.xml" 300 1850)"
  tap_xy "$EX" "$EY"; sleep 1.2
  qdump "$ROW_DIR/x5-$pct-closed.xml"; screencap "$ROW_DIR/x5-$pct-closed.png"
  assert_eq "X5 ${pct}%: the backdrop capture has edit mode and no burst" "yes no" \
    "$(has_node "$ROW_DIR/x5-$pct-closed.xml" edit_disc:unpin) $(has_node "$ROW_DIR/x5-$pct-closed.xml" quick_burst)"
  r="$(python3 - "$ROW_DIR/x5-$pct.png" "$ROW_DIR/x5-$pct-closed.png" "$ROW_DIR/x5-$pct.xml" "$pct" <<'PY'
import re, sys
import numpy as np
from PIL import Image
opn = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(float)
bak = np.asarray(Image.open(sys.argv[2]).convert("RGB")).astype(float)
s = open(sys.argv[3]).read(); pct = int(sys.argv[4])
accent = np.array([0.0, 120.0, 215.0])              # this baseline's accent (a tile at 0 % draws exactly this)
alpha = 1 - 0.8 * pct / 100                          # StartPage.kt: tileAlpha with a background picture
out, worst = [], 0.0
for i in range(4):
    m = re.search(r'resource-id="quick_sat:%d"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % i, s)
    if not m: continue
    l, t, r, b = map(int, m.groups())
    f = opn[t + 3:t + 9, l + 3:l + 9].reshape(-1, 3).mean(0); k = bak[t + 3:t + 9, l + 3:l + 9].reshape(-1, 3).mean(0)
    want = alpha * accent + (1 - alpha) * k; d = np.abs(f - want).max(); worst = max(worst, d)
    out.append(f"sat{i} fill={f.round().astype(int).tolist()} behind={k.round().astype(int).tolist()} want={want.round().astype(int).tolist()}")
print(f"alpha={alpha:.2f} " + "; ".join(out) + f" | max |d|={worst:.1f} -> {'PASS' if out and worst <= 14 else 'FAIL'}")
PY
)"
  note "X5 ${pct}%: $r"
  case "$r" in *PASS) _verdict PASS "X5 ${pct}%: every satellite is the accent at the tile's alpha over what lies behind it" "${r: -40}" ;; *) _verdict FAIL "X5 ${pct}%: every satellite is the accent at the tile's alpha over what lies behind it" "${r: -60}" ;; esac
  c6
done
set_transparency "${T0:-50}" >/dev/null
adb shell am start -W -n app.tileshell/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
scroll_to_node "$ROW_DIR/.settings.xml" theme_background_remove 10 >/dev/null
tap_node "$ROW_DIR/.settings.xml" theme_background_remove; sleep 1
adb shell rm -f /sdcard/Pictures/qa-edge-bg.png
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
adb shell input keyevent KEYCODE_HOME; sleep 2
c6

log "--- RV10: wm size / wm density / font_scale leave the satellites' bounds in epx unchanged (phase 01 E3's method) ---"
fresh_burst "tile:$A_KEY" rv10-base
c6
cfg() { adb shell dumpsys window | grep -m1 "mGlobalConfiguration" | grep -oE "\{[0-9.]+ |[0-9]+dpi" | tr -d '{ ' | tr '\n' ' ' | sed 's/ $//'; }
note "global configuration at the start: $(cfg) (font scale, density)"
rv10() { # tag width want-readback -- apply command
  local tag="$1" w="$2" want="$3"; shift 3
  adb shell "$@" >/dev/null 2>&1; sleep 6
  # The round-2 re-judge, EV-5: the change is read back, and the global configuration (font scale, density) or the size
  # proves it applied system-wide — the satellites staying put in epx is then the product's doing, not a no-op.
  local rb; case "$tag" in size*) rb="$(adb shell wm size | tr -d '\r' | tr '\n' ' ')" ;; d*) rb="$(adb shell wm density | tr -d '\r' | tr '\n' ' ')" ;; font*) rb="font_scale=$(adb shell settings get system font_scale | tr -d '\r')" ;; esac
  note "RV10 $tag read back: $rb · global configuration: $(cfg)"
  assert_contains "RV10 $tag: the change applied" "$want" "$rb $(cfg)"
  adb shell input keyevent KEYCODE_HOME; sleep 5; ensure_start_page
  fresh_burst "tile:$A_KEY" "rv10-$tag"
  local r; r="$(epx_same "$ROW_DIR/rv10-base.xml" 1080 "$ROW_DIR/rv10-$tag.xml" "$w")"
  note "RV10 $tag: $r"
  case "$r" in PASS*) _verdict PASS "RV10 $tag: satellites unchanged in epx" "$r" ;; *) _verdict FAIL "RV10 $tag: satellites unchanged in epx" "$r" ;; esac
  c6
}
rv10 size720 720 "Override size: 720x1560" wm size 720x1560
adb shell wm size reset; sleep 5
rv10 d560 1080 "560dpi" wm density 560
adb shell wm density reset; sleep 5
rv10 font13 1080 "1.3 " settings put system font_scale 1.3
adb shell settings put system font_scale 1.0; sleep 4
c6

log "--- the index: every Edge Cases bullet and where it is proved ---"
python3 - "$(dirname "$0")/edge_index.tsv" "$ROW_DIR/EDGE.txt" <<'PY' | tee -a "$ROW_DIR/EDGE.txt" >/dev/null
import sys
rows = [l.rstrip("\n").split("\t") for l in open(sys.argv[1]) if l.strip() and not l.startswith("#")]
print("\n## Edge Cases index (phase-11 doc :627-658) — bullet · case · where it is proved")
for r in rows: print(" · ".join(r))
print(f"({len(rows)} cases)")
PY
note "the index is appended to EDGE.txt from scripts/edge_index.tsv"
row_end
