#!/usr/bin/env bash
# Phase 12 E11 — the presets on both surfaces (T12-2), Custom (T12-12, r3 D8), its snapshot and grant (r3 V10), and
# persistence. (a) the wizard's presets page, one wizard-side fixture per preset (r3 V6); (b) Settings > Start + theme after
# a run, seeded once. Every ring read-back from a MARK taken just before that tap on that surface (C-20). Start is captured
# live after Done (a) or after Back / Home to the running Start (b) — same PID, no force-stop; then Tess and the keyboard.
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"; . "$HERE/presets_lib.sh"; . "$HERE/grants.sh"
P13="$QAROOT/phase-13/scripts"
row_begin E11 "presets on both surfaces, Custom, snapshot, grant, persistence"
IDS="default w10m hal soft lumia midnight"
settle_motion() { sleep 1.5; }   # capture after the [motion] wizard_page trace has landed (the X7 page motion)

log "control: pm clear -> provision.sh -> Home, provisioning never applies a preset"
restore_fresh control
prefs="$(prefs_now)"; echo "$prefs" > "$ROW_DIR/control-start_theme.xml"
case "$prefs" in *'name="theme_preset"'*) assert_contains "control: theme_preset absent or default" '<string name="theme_preset">default</string>' "$prefs" ;;
  *) _verdict PASS "control: theme_preset absent or default" "absent" ;; esac

# ============================================================ (a) the wizard's presets page, one fixture per preset
for id in $IDS; do
  name="$(preset_field "$id" 2)"
  log "(a) $name"
  wizard_fixture "a-$id"
  pid0="$(adb shell pidof $PKG | tr -d '\r')"
  MARK="$(ring_mark)"
  tap_node "$FIX_LAST" "preset:$name"; sleep 2
  prefs="$(prefs_now)"; echo "$prefs" > "$ROW_DIR/a-$id-start_theme.xml"
  listener_slice "$MARK" "(a) $name tap"
  assert_contains "(a) $name: [wizard] preset $name" "[wizard] preset $name" "$SLICE"
  assert_contains "(a) $name: [theme] preset $name applied" "[theme] preset $name applied" "$SLICE"
  check_keys "$id" "$prefs" "(a) $name"
  [ "$id" = w10m ] && assert_contains "(a) $name: theme_preset_variant = hero" '<string name="theme_preset_variant">hero</string>' "$prefs"
  dump_ui "$ROW_DIR/a-$id-presets.xml"
  tap_node "$ROW_DIR/a-$id-presets.xml" wizard_done; settle_motion
  dump_ui "$ROW_DIR/a-$id-start.xml"; screencap "$ROW_DIR/a-$id-start.png"
  assert_eq "(a) $name: Start after Done" "yes" "$(has_node "$ROW_DIR/a-$id-start.xml" start_page)"
  check_start "$id" "(a) $name" "$ROW_DIR/a-$id-start.png" "$ROW_DIR/a-$id-start.xml"
  check_tess "$id" "(a) $name" "$ROW_DIR/a-$id-tess"
  check_keyboard "$id" "(a) $name" "$ROW_DIR/a-$id-kb"
  assert_eq "(a) $name: Start's PID unchanged across the capture (no restart)" "$pid0" "$(adb shell pidof $PKG | tr -d '\r')"
done

log "(a) Custom: Lumia, then accent Red among the items"
wizard_fixture a-custom
tap_node "$FIX_LAST" "preset:Lumia"; sleep 2
lumia="$(prefs_now)"
scroll_to_node "$ROW_DIR/a-custom-red.xml" "accent:Red" 8
tap_node "$ROW_DIR/a-custom-red.xml" "accent:Red"; sleep 2
adb shell input swipe 540 900 540 1900 300; sleep 1; adb shell input swipe 540 900 540 1900 300; sleep 1; adb shell input swipe 540 900 540 1900 300; sleep 1
dump_ui "$ROW_DIR/a-custom.xml"
assert_eq "(a) Custom: preset:Custom selected" "true" "$(node_checked "$ROW_DIR/a-custom.xml" preset:Custom)"
custom="$(prefs_now)"; echo "$custom" > "$ROW_DIR/a-custom-start_theme.xml"
assert_contains "(a) Custom: theme_preset = custom" '<string name="theme_preset">custom</string>' "$custom"
assert_contains "(a) Custom: accent = Red" '<long name="accent" value="4293398819" />' "$custom"
others() { echo "$1" | grep -E 'name="(theme|background|transparency|press|tess_lens|keyboard_palette|transparency_effects)"' | sort; }
assert_eq "(a) Custom: every OTHER item key unchanged from Lumia's" "$(others "$lumia")" "$(others "$custom")"
# All eight items as effective values, less one key (codex review B3: a change reads Custom AND changes only its item).
except() { effective_items "$1" | grep -v "^$2="; }
eff() { effective_items "$1" | grep "^$2=" | cut -d= -f2; }
log "(a) Transparency effects after re-tapping a preset: Custom the same way"
for i in 1 2 3; do adb shell input swipe 540 700 540 2000 300; sleep 0.6; done
dump_ui "$ROW_DIR/a-fx0.xml"; tap_node "$ROW_DIR/a-fx0.xml" "preset:Lumia"; sleep 2
lumia="$(prefs_now)"
scroll_to_node "$ROW_DIR/a-fx1.xml" "theme_transparency_effects" 12
tap_node "$ROW_DIR/a-fx1.xml" "theme_transparency_effects"; sleep 2
fx="$(prefs_now)"
assert_contains "(a) effects toggled: theme_preset = custom" '<string name="theme_preset">custom</string>' "$fx"
assert_eq "(a) effects toggled: transparency_effects flipped (Lumia's true -> false)" "false" "$(eff "$fx" transparency_effects)"
assert_eq "(a) effects toggled: every other item unchanged from Lumia's" "$(except "$lumia" transparency_effects)" "$(except "$fx" transparency_effects)"
for i in 1 2 3; do adb shell input swipe 540 700 540 2000 300; sleep 0.6; done
dump_ui "$ROW_DIR/a-fx2.xml"
assert_eq "(a) effects toggled: preset:Custom selected" "true" "$(node_checked "$ROW_DIR/a-fx2.xml" preset:Custom)"

# ============================================================ (b) Settings > Start + theme after a run, seeded once
log "(b) seed once: pm clear -> provision.sh -> layout_restore"
restore_fresh b
SEEDMARK="$(ring_mark)"
layout_restore "$QAROOT/phase-02/baseline_layout.json" > "$ROW_DIR/layout-b.txt" 2>&1
assert_seeded "$SEEDMARK" "(b)"
adb shell ime set "$KEYBOARD" >/dev/null
adb shell input keyevent KEYCODE_HOME; sleep 3
pid0="$(adb shell pidof $PKG | tr -d '\r')"
for id in $IDS; do
  name="$(preset_field "$id" 2)"
  log "(b) $name"
  open_theme "$ROW_DIR/b-$id-theme.xml"
  MARK="$(ring_mark)"
  tap_node "$ROW_DIR/b-$id-theme.xml" "theme_preset:$name"; sleep 2
  prefs="$(prefs_now)"; echo "$prefs" > "$ROW_DIR/b-$id-start_theme.xml"
  listener_slice "$MARK" "(b) $name tap"
  assert_contains "(b) $name: [theme] preset $name applied" "[theme] preset $name applied" "$SLICE"
  assert_absent "(b) $name: no [wizard] line on Start + theme" "[wizard] preset" "$SLICE"
  check_keys "$id" "$prefs" "(b) $name"
  adb shell input keyevent KEYCODE_HOME; sleep 2.5
  dump_ui "$ROW_DIR/b-$id-start.xml"; screencap "$ROW_DIR/b-$id-start.png"
  check_start "$id" "(b) $name" "$ROW_DIR/b-$id-start.png" "$ROW_DIR/b-$id-start.xml"
  check_tess "$id" "(b) $name" "$ROW_DIR/b-$id-tess"
  check_keyboard "$id" "(b) $name" "$ROW_DIR/b-$id-kb"
done
assert_eq "(b) Start's PID unchanged through the pass (no restart)" "$pid0" "$(adb shell pidof $PKG | tr -d '\r')"

log "(b) Custom: Lumia -> accent Red; Transparency effects; the show-more-tiles control"
open_theme "$ROW_DIR/b-c0.xml"
tap_node "$ROW_DIR/b-c0.xml" "theme_preset:Lumia"; sleep 2
lumia="$(prefs_now)"
scroll_to_node "$ROW_DIR/b-c1.xml" "accent:Red" 8
tap_node "$ROW_DIR/b-c1.xml" "accent:Red"; sleep 2
custom="$(prefs_now)"
assert_contains "(b) Custom: theme_preset = custom" '<string name="theme_preset">custom</string>' "$custom"
assert_contains "(b) Custom: accent = Red" '<long name="accent" value="4293398819" />' "$custom"
assert_eq "(b) Custom: every OTHER item key unchanged from Lumia's" "$(others "$lumia")" "$(others "$custom")"
open_theme "$ROW_DIR/b-c2.xml"
assert_eq "(b) Custom: theme_preset:Custom selected" "true" "$(node_checked "$ROW_DIR/b-c2.xml" theme_preset:Custom)"
tap_node "$ROW_DIR/b-c2.xml" "theme_preset:HAL"; sleep 2
scroll_to_node "$ROW_DIR/b-c3.xml" "theme_transparency_effects" 10
hal="$(prefs_now)"
tap_node "$ROW_DIR/b-c3.xml" "theme_transparency_effects"; sleep 2
fx="$(prefs_now)"
assert_contains "(b) Transparency effects toggled after a preset: custom" '<string name="theme_preset">custom</string>' "$fx"
assert_eq "(b) effects toggled: transparency_effects flipped (HAL's true -> false)" "false" "$(eff "$fx" transparency_effects)"
assert_eq "(b) effects toggled: every other item unchanged from HAL's" "$(except "$hal" transparency_effects)" "$(except "$fx" transparency_effects)"
log "(b) Remove picture right after a preset: Custom, the picture gone, the rest unchanged"
open_theme "$ROW_DIR/b-rm0.xml"; tap_node "$ROW_DIR/b-rm0.xml" "theme_preset:HAL"; sleep 2
hal="$(prefs_now)"
scroll_to_node "$ROW_DIR/b-rm1.xml" "theme_background_remove" 8
tap_node "$ROW_DIR/b-rm1.xml" "theme_background_remove"; sleep 2
rm="$(prefs_now)"
assert_contains "(b) Remove picture: theme_preset = custom" '<string name="theme_preset">custom</string>' "$rm"
assert_eq "(b) Remove picture: background gone" "(none)" "$(eff "$rm" background)"
assert_eq "(b) Remove picture: every other item unchanged from HAL's" "$(except "$hal" background)" "$(except "$rm" background)"
open_theme "$ROW_DIR/b-c4.xml"
tap_node "$ROW_DIR/b-c4.xml" "theme_preset:HAL"; sleep 2
scroll_to_node "$ROW_DIR/b-c5.xml" "theme_show_more_tiles" 12
tap_node "$ROW_DIR/b-c5.xml" "theme_show_more_tiles"; sleep 2
assert_contains "(b) control: show more tiles leaves theme_preset hal" '<string name="theme_preset">hal</string>' "$(prefs_now)"
dump_ui "$ROW_DIR/b-c6.xml"; tap_node "$ROW_DIR/b-c6.xml" "theme_show_more_tiles"; sleep 2
assert_contains "(b) control: toggled back, columns 3" '<int name="columns" value="3" />' "$(prefs_now)"

log "(b) Custom snapshot: a picture of the user's, HAL over it, Custom brings it back"
# Its own fresh state: the Custom sub-row above already made a snapshot (Lumia -> Red, then HAL), and this sub-row starts
# from "no theme_custom.xml" (run 1 read the older snapshot's file).
restore_fresh snap
SEEDMARK="$(ring_mark)"
layout_restore "$QAROOT/phase-02/baseline_layout.json" > "$ROW_DIR/layout-snap.txt" 2>&1
assert_seeded "$SEEDMARK" "(snapshot)"
adb shell ime set "$KEYBOARD" >/dev/null
adb shell input keyevent KEYCODE_HOME; sleep 3
CHECKER_PATH="/sdcard/Pictures/p12_checker.png"
python3 "$P13/make_fixtures.py" checker "$ROW_DIR/checker.png" 1080 2340 >/dev/null
CURI="$(push_picture "$ROW_DIR/checker.png" "$CHECKER_PATH")"
note "checker MediaStore URI: $CURI"
assert_contains "the checker is in MediaStore" "content://media/" "$CURI"
adb shell run-as $PKG cat shared_prefs/start_theme.xml > "$ROW_DIR/snap-prefs-in.xml"
python3 "$QAROOT/phase-01/scripts/prefs_edit.py" "$ROW_DIR/snap-prefs-in.xml" background string "$CURI" > "$ROW_DIR/snap-1.xml"
python3 "$QAROOT/phase-01/scripts/prefs_edit.py" "$ROW_DIR/snap-1.xml" theme_preset string custom > "$ROW_DIR/snap-2.xml"
leave_home
adb shell am force-stop $PKG
adb shell "run-as $PKG sh -c 'cat > shared_prefs/start_theme.xml'" < "$ROW_DIR/snap-2.xml"
adb shell ime set "$KEYBOARD" >/dev/null
adb shell input keyevent KEYCODE_HOME; sleep 4
pre="$(prefs_now)"; echo "$pre" > "$ROW_DIR/snap-pre-hal.xml"
assert_absent "no theme_custom.xml before the HAL tap" "theme_custom.xml" "$(adb shell run-as $PKG ls shared_prefs | tr -d '\r')"
open_theme "$ROW_DIR/snap-theme.xml"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/snap-theme.xml" "theme_preset:HAL"; sleep 2
assert_contains "HAL over it: background = preset_hal" "android.resource://app.tileshell/drawable/preset_hal" "$(prefs_now)"
assert_contains "theme_custom.xml now exists" "theme_custom.xml" "$(adb shell run-as $PKG ls shared_prefs | tr -d '\r')"
open_theme "$ROW_DIR/snap-theme2.xml"
MARK2="$(ring_mark)"
tap_node "$ROW_DIR/snap-theme2.xml" "theme_preset:Custom"; sleep 2
listener_slice "$MARK2" "Custom restored"
assert_contains "[theme] preset Custom restored" "[theme] preset Custom restored" "$SLICE"
post="$(prefs_now)"; echo "$post" > "$ROW_DIR/snap-post-custom.xml"
assert_contains "Custom: background = the checker again" "<string name=\"background\">$CURI</string>" "$post"
items() { effective_items "$1"; echo "$1" | grep -oE '<string name="theme_preset">[^<]*'; }
assert_eq "Custom: every item key equals the pre-HAL state (effective values)" "$(items "$pre")" "$(items "$post")"
adb shell input keyevent KEYCODE_HOME; sleep 3
screencap "$ROW_DIR/snap-start.png"; dump_ui "$ROW_DIR/snap-start.xml"
edge="$(checker_edge "$ROW_DIR/snap-start.png")"
note "checker edge step in the gutter column: $edge"
assert_eq "the checker shows on Start: a sharp edge in a gutter column (step >= 100 levels)" "yes" "$([ "${edge:-0}" -ge 100 ] && echo yes || echo no)"
open_theme "$ROW_DIR/snap-theme3.xml"
tap_node "$ROW_DIR/snap-theme3.xml" "theme_preset:Default"; sleep 2
ctl="$(cat "$ROW_DIR/control-start_theme.xml")"
dflt="$(prefs_now)"
assert_eq "Default: every item key equals the control's (effective values; a fresh install writes no keys)" "$(effective_items "$ctl")" "$(effective_items "$dflt")"
assert_contains "Default: tess_lens hal" '<string name="tess_lens">hal</string>' "$dflt"
assert_contains "Default: keyboard_palette DARK" '<string name="keyboard_palette">DARK</string>' "$dflt"
adb shell rm -f "$CHECKER_PATH"

log "(b) a set stored before phase 12 (no theme_preset key): it reads Custom, and a preset over it keeps it (review B1)"
restore_fresh pre12
python3 "$P13/make_fixtures.py" checker "$ROW_DIR/checker12.png" 1080 2340 >/dev/null
C12="$(push_picture "$ROW_DIR/checker12.png" /sdcard/Pictures/p12_pre12.png)"
note "pre-12 picture: $C12"
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map />\n' > "$ROW_DIR/pre12-in.xml"
python3 "$QAROOT/phase-01/scripts/prefs_edit.py" "$ROW_DIR/pre12-in.xml" background string "$C12" > "$ROW_DIR/pre12.xml"
leave_home; adb shell am force-stop $PKG
adb shell "run-as $PKG sh -c 'cat > shared_prefs/start_theme.xml'" < "$ROW_DIR/pre12.xml"
adb shell ime set "$KEYBOARD" >/dev/null
adb shell input keyevent KEYCODE_HOME; sleep 4
assert_absent "the stored set has no theme_preset key" 'theme_preset' "$(prefs_now)"
open_theme "$ROW_DIR/pre12-theme.xml"
assert_eq "it reads Custom (theme_preset:Custom selected)" "true" "$(node_checked "$ROW_DIR/pre12-theme.xml" theme_preset:Custom)"
tap_node "$ROW_DIR/pre12-theme.xml" "theme_preset:HAL"; sleep 2
assert_contains "HAL over it: theme_custom.xml exists" "theme_custom.xml" "$(adb shell run-as $PKG ls shared_prefs | tr -d '\r')"
open_theme "$ROW_DIR/pre12-theme2.xml"; tap_node "$ROW_DIR/pre12-theme2.xml" "theme_preset:Custom"; sleep 2
assert_contains "Custom brings the user's picture back" "<string name=\"background\">$C12</string>" "$(prefs_now)"
adb shell rm -f /sdcard/Pictures/p12_pre12.png

log "(b) Custom grant: the picker's persisted grant kept while the snapshot references it, released after"
for p in READ_MEDIA_IMAGES READ_MEDIA_VISUAL_USER_SELECTED; do adb shell pm revoke $PKG android.permission.$p 2>/dev/null; done
sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 3
python3 "$P13/make_fixtures.py" checker "$ROW_DIR/fixA.png" 720 1560 >/dev/null
python3 - "$ROW_DIR/fixB.png" <<'PY'
import sys
from PIL import Image
Image.new("RGB", (720, 1560), (30, 140, 60)).save(sys.argv[1])
PY
AURI="$(push_picture "$ROW_DIR/fixA.png" /sdcard/Pictures/p12_fixA.png)"
choose_picture() { # label
  open_theme "$ROW_DIR/g-$1-theme.xml"
  tap_node "$ROW_DIR/g-$1-theme.xml" theme_background_choose; sleep 3
  dump_ui "$ROW_DIR/g-$1-picker.xml"; screencap "$ROW_DIR/g-$1-picker.png"
  # the photo picker's newest item is first in its grid
  local b; b="$(python3 - "$ROW_DIR/g-$1-picker.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
cands = [n for n in root.iter("node") if re.search(r"photo|image|media_item|thumbnail", (n.get("content-desc", "") + n.get("resource-id", "")).lower()) and n.get("clickable") == "true"]
if not cands:
    cands = [n for n in root.iter("node") if re.search(r"^Photo taken", n.get("content-desc", ""))]
if cands:
    print(" ".join(re.findall(r"-?\d+", cands[0].get("bounds"))))
PY
)"
  note "$1 picker item: [$b]"
  if [ -n "$b" ]; then set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 3; fi
}
open_theme "$ROW_DIR/g-mid.xml"; tap_node "$ROW_DIR/g-mid.xml" "theme_preset:Midnight"; sleep 2
midnight="$(prefs_now)"
choose_picture A
gA="$(prefs_now | grep -oE 'name="background">[^<]*' | sed 's/name="background">//')"
assert_contains "Choose picture right after a preset (Midnight): theme_preset = custom" '<string name="theme_preset">custom</string>' "$(prefs_now)"
assert_eq "Choose picture right after a preset: every other item unchanged from Midnight's" "$(except "$midnight" background)" "$(except "$(prefs_now)" background)"
assert_contains "the picker gave a content URI for A" "content://" "$gA"
note "A's picker URI: $gA"
persisted_grants > "$ROW_DIR/g-grants-1.txt"
assert_contains "A's persisted read grant is held" "$gA" "$(cat "$ROW_DIR/g-grants-1.txt")"
open_theme "$ROW_DIR/g-hal.xml"; tap_node "$ROW_DIR/g-hal.xml" "theme_preset:HAL"; sleep 2
persisted_grants > "$ROW_DIR/g-grants-2.txt"
assert_contains "after HAL: A's grant still held (the snapshot references it)" "$gA" "$(cat "$ROW_DIR/g-grants-2.txt")"
leave_home; adb shell am force-stop $PKG; adb shell ime set "$KEYBOARD" >/dev/null; adb shell input keyevent KEYCODE_HOME; sleep 4
open_theme "$ROW_DIR/g-custom.xml"; tap_node "$ROW_DIR/g-custom.xml" "theme_preset:Custom"; sleep 2
assert_contains "after a force-stop, Custom restores A" "<string name=\"background\">$gA</string>" "$(prefs_now)"
adb shell input keyevent KEYCODE_HOME; sleep 3
screencap "$ROW_DIR/g-start-A.png"
edgeA="$(checker_edge "$ROW_DIR/g-start-A.png")"
note "A on Start: checker edge step $edgeA (no broad media permission: only the persisted grant can read it)"
assert_eq "A decodes and renders on Start (a sharp checker edge in a gutter, step >= 100)" "yes" "$([ "${edgeA:-0}" -ge 100 ] && echo yes || echo no)"
persisted_grants > "$ROW_DIR/g-grants-3.txt"
assert_contains "A's grant still held after Custom" "$gA" "$(cat "$ROW_DIR/g-grants-3.txt")"
BURI="$(push_picture "$ROW_DIR/fixB.png" /sdcard/Pictures/p12_fixB.png)"
choose_picture B
gB="$(prefs_now | grep -oE 'name="background">[^<]*' | sed 's/name="background">//')"
assert_contains "the picker gave a content URI for B" "content://" "$gB"
note "B's picker URI: $gB"
assert_ne "B is a different picture" "$gA" "$gB"
open_theme "$ROW_DIR/g-lumia.xml"; tap_node "$ROW_DIR/g-lumia.xml" "theme_preset:Lumia"; sleep 2
persisted_grants > "$ROW_DIR/g-grants-4.txt"
assert_absent "a preset over B: A's grant released" "$gA" "$(cat "$ROW_DIR/g-grants-4.txt")"
assert_contains "... and B's retained" "$gB" "$(cat "$ROW_DIR/g-grants-4.txt")"
adb shell rm -f /sdcard/Pictures/p12_fixA.png /sdcard/Pictures/p12_fixB.png

log "(b) persistence: force-stop, reopen Start + theme"
open_theme "$ROW_DIR/p-before.xml"; tap_node "$ROW_DIR/p-before.xml" "theme_preset:Soft"; sleep 2
before="$(prefs_now)"
leave_home; adb shell am force-stop $PKG; adb shell ime set "$KEYBOARD" >/dev/null; adb shell input keyevent KEYCODE_HOME; sleep 4
open_theme "$ROW_DIR/p-after.xml"
assert_eq "persistence: the keys unchanged" "$(items "$before")" "$(items "$(prefs_now)")"
assert_eq "persistence: theme_preset:Soft still selected" "true" "$(node_checked "$ROW_DIR/p-after.xml" theme_preset:Soft)"

log "restore: Default, then pm clear -> provision.sh -> Home"
tap_node "$ROW_DIR/p-after.xml" "theme_preset:Default"; sleep 2
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
