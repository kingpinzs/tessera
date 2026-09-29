#!/usr/bin/env bash
# Phase 12 E13 — the "Windows 10 Mobile (original)" preset against R12 (T12-9, T12-10; Q7 A, Q8 C, Q9 A), on (a) the
# wizard's presets page (the wizard-side fixture, one per trial) and (b) Start + theme after the run (seeded once).
# R12's values (Cobalt, Dark, alpha 0.40, no press style, acrylic off, the dark keyboard), Cobalt the 49th swatch and
# selected, six presets (the variant chips are not a seventh; `preset:Custom` is the Custom entry, not a preset, and is
# counted apart), Tess's lens on Cobalt's hue, both pictures in the APK (a missing one FAILs), the Hero by the picture
# oracle, and the variant: streaks, remembered across a preset change, hero again.
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"; . "$HERE/presets_lib.sh"
row_begin E13 "the original W10M preset and its two pictures"
W="Windows 10 Mobile (original)"
settle_motion() { sleep 1.5; }

log "pictures, asserted present in the APK"
lst="$(unzip -l "$APK")"
assert_contains "APK lists preset_w10m_hero.webp" "preset_w10m_hero.webp" "$lst"
assert_contains "APK lists preset_w10m_streaks.webp" "preset_w10m_streaks.webp" "$lst"

accent_nodes() { # dump-prefix -> the distinct accent:* tags over the dumps that lay out the whole grid
  local p="$1" i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    dump_ui "$p-$i.xml"
    grep -oE 'resource-id="accent:[^"]*"' "$p-$i.xml"
    grep -q 'resource-id="accent:Cobalt"' "$p-$i.xml" && break
    adb shell input swipe 540 1700 540 1000 300; sleep 1
  done | sort -u
}

surface_checks() { # surface(a|b) tag-prefix dump-with-presets label
  local s="$1" tp="$2" d="$3" l="$4" prefs cnt
  prefs="$(prefs_now)"; echo "$prefs" > "$ROW_DIR/$s-$l-start_theme.xml"
  assert_contains "($s) theme_preset = w10m" '<string name="theme_preset">w10m</string>' "$prefs"
  assert_contains "($s) theme_preset_variant = hero" '<string name="theme_preset_variant">hero</string>' "$prefs"
  assert_contains "($s) accent = Cobalt 4282279423" '<long name="accent" value="4282279423" />' "$prefs"
  assert_contains "($s) theme DARK" '<string name="theme">DARK</string>' "$prefs"
  assert_contains "($s) transparency 0.75" '<float name="transparency" value="0.75" />' "$prefs"
  assert_contains "($s) press NONE" '<string name="press">NONE</string>' "$prefs"
  assert_contains "($s) transparency_effects false" '<boolean name="transparency_effects" value="false" />' "$prefs"
  assert_contains "($s) keyboard_palette DARK" '<string name="keyboard_palette">DARK</string>' "$prefs"
  assert_contains "($s) tess_lens accent" '<string name="tess_lens">accent</string>' "$prefs"
  assert_contains "($s) background = the Hero" '<string name="background">android.resource://app.tileshell/drawable/preset_w10m_hero</string>' "$prefs"
  assert_eq "($s) the hero chip" "yes" "$(has_node "$d" "${tp}_variant:hero")"
  assert_eq "($s) the streaks chip" "yes" "$(has_node "$d" "${tp}_variant:streaks")"
  if [ "$s" = a ]; then
    cnt="$(grep -oE 'resource-id="preset:[^"]*"' "$d" | sort -u | grep -vc 'preset:Custom"')"
    assert_eq "(a) wizard_presets holds exactly six presets (the variant is not a seventh)" "6" "$cnt"
    record "(a) the Custom entry, counted apart" "$(grep -c 'resource-id="preset:Custom"' "$d") preset:Custom node(s)"
  fi
  local tags; tags="$(accent_nodes "$ROW_DIR/$s-$l-grid")"
  assert_eq "($s) the accent grid holds exactly 49 accent:* nodes" "49" "$(echo "$tags" | grep -c 'accent:')"
  local last; last="$(ls -t "$ROW_DIR/$s-$l-grid-"*.xml | head -1)"
  assert_eq "($s) accent:Cobalt present and selected" "true" "$(node_checked "$last" accent:Cobalt)"
}

# ============================================================ (a) the wizard's page
log "(a) trial 1: the original preset, the Hero"
wizard_fixture a-hero
MARK="$(ring_mark)"
tap_node "$FIX_LAST" "preset:$W"; sleep 2
listener_slice "$MARK" "(a) original"
assert_contains "(a) [theme] preset $W applied" "[theme] preset $W applied" "$SLICE"
assert_contains "(a) [wizard] preset $W" "[wizard] preset $W" "$SLICE"
dump_ui "$ROW_DIR/a-hero-presets.xml"
surface_checks a preset "$ROW_DIR/a-hero-presets.xml" hero
log "(a) Default, then the original again: the last-chosen variant (hero); streaks is its own trial"
adb shell input swipe 540 700 540 2000 300; sleep 1; adb shell input swipe 540 700 540 2000 300; sleep 1; adb shell input swipe 540 700 540 2000 300; sleep 1
dump_ui "$ROW_DIR/a-top.xml"
tap_node "$ROW_DIR/a-top.xml" "preset:Default"; sleep 2
dump_ui "$ROW_DIR/a-top2.xml"; tap_node "$ROW_DIR/a-top2.xml" "preset:$W"; sleep 2
assert_contains "(a) re-tapped: variant still hero" '<string name="theme_preset_variant">hero</string>' "$(prefs_now)"
dump_ui "$ROW_DIR/a-hero-done.xml"
tap_node "$ROW_DIR/a-hero-done.xml" wizard_done; settle_motion
dump_ui "$ROW_DIR/a-hero-start.xml"; screencap "$ROW_DIR/a-hero-start.png"
check_start w10m "(a) the Hero on Start" "$ROW_DIR/a-hero-start.png" "$ROW_DIR/a-hero-start.xml" preset_w10m_hero
check_tess w10m "(a) Tess on Cobalt" "$ROW_DIR/a-hero-tess"

log "(a) trial 2: the streaks chip, tapped before Done"
wizard_fixture a-streaks
tap_node "$FIX_LAST" "preset:$W"; sleep 2
dump_ui "$ROW_DIR/a-streaks-presets.xml"
before="$(prefs_now)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/a-streaks-presets.xml" "preset_variant:streaks"; sleep 2
after="$(prefs_now)"; echo "$after" > "$ROW_DIR/a-streaks-start_theme.xml"
listener_slice "$MARK" "(a) streaks"
assert_contains "(a) [theme] preset w10m variant streaks applied" "[theme] preset w10m variant streaks applied" "$SLICE"
assert_contains "(a) streaks: background" '<string name="background">android.resource://app.tileshell/drawable/preset_w10m_streaks</string>' "$after"
assert_contains "(a) streaks: theme_preset_variant" '<string name="theme_preset_variant">streaks</string>' "$after"
assert_contains "(a) streaks: still w10m (a variant is not Custom)" '<string name="theme_preset">w10m</string>' "$after"
rest() { echo "$1" | grep -E 'name="(accent|theme|transparency|press|tess_lens|keyboard_palette|transparency_effects)"' | sort; }
assert_eq "(a) streaks: every other item key unchanged" "$(rest "$before")" "$(rest "$after")"
dump_ui "$ROW_DIR/a-streaks-done.xml"
tap_node "$ROW_DIR/a-streaks-done.xml" wizard_done; settle_motion
dump_ui "$ROW_DIR/a-streaks-start.xml"; screencap "$ROW_DIR/a-streaks-start.png"
check_start w10m "(a) the streaks picture on Start" "$ROW_DIR/a-streaks-start.png" "$ROW_DIR/a-streaks-start.xml" preset_w10m_streaks

# ============================================================ (b) Start + theme, seeded once
log "(b) seed once"
restore_fresh b
layout_restore "$QAROOT/phase-02/baseline_layout.json" > "$ROW_DIR/layout-b.txt" 2>&1
adb shell ime set "$KEYBOARD" >/dev/null
adb shell input keyevent KEYCODE_HOME; sleep 3
open_theme "$ROW_DIR/b-theme.xml"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/b-theme.xml" "theme_preset:$W"; sleep 2
listener_slice "$MARK" "(b) original"
assert_contains "(b) [theme] preset $W applied" "[theme] preset $W applied" "$SLICE"
dump_ui "$ROW_DIR/b-presets.xml"
surface_checks b theme_preset "$ROW_DIR/b-presets.xml" hero
adb shell input keyevent KEYCODE_HOME; sleep 2.5
dump_ui "$ROW_DIR/b-hero-start.xml"; screencap "$ROW_DIR/b-hero-start.png"
check_start w10m "(b) the Hero on Start" "$ROW_DIR/b-hero-start.png" "$ROW_DIR/b-hero-start.xml" preset_w10m_hero
check_tess w10m "(b) Tess on Cobalt" "$ROW_DIR/b-hero-tess"
log "(b) the variant"
open_theme "$ROW_DIR/b-v1.xml"
before="$(prefs_now)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/b-v1.xml" "theme_preset_variant:streaks"; sleep 2
after="$(prefs_now)"
listener_slice "$MARK" "(b) streaks"
assert_contains "(b) [theme] preset w10m variant streaks applied" "[theme] preset w10m variant streaks applied" "$SLICE"
assert_contains "(b) streaks: background" "preset_w10m_streaks</string>" "$after"
assert_contains "(b) streaks: still w10m" '<string name="theme_preset">w10m</string>' "$after"
assert_eq "(b) streaks: every other item key unchanged" "$(rest "$before")" "$(rest "$after")"
adb shell input keyevent KEYCODE_HOME; sleep 2.5
dump_ui "$ROW_DIR/b-streaks-start.xml"; screencap "$ROW_DIR/b-streaks-start.png"
check_start w10m "(b) the streaks picture on Start" "$ROW_DIR/b-streaks-start.png" "$ROW_DIR/b-streaks-start.xml" preset_w10m_streaks
open_theme "$ROW_DIR/b-v2.xml"; tap_node "$ROW_DIR/b-v2.xml" "theme_preset:Default"; sleep 2
dump_ui "$ROW_DIR/b-v3.xml"; tap_node "$ROW_DIR/b-v3.xml" "theme_preset:$W"; sleep 2
assert_contains "(b) Default, then the original: the last-chosen variant, streaks" '<string name="theme_preset_variant">streaks</string>' "$(prefs_now)"
assert_contains "(b) ... and its picture" "preset_w10m_streaks</string>" "$(prefs_now)"
dump_ui "$ROW_DIR/b-v4.xml"; tap_node "$ROW_DIR/b-v4.xml" "theme_preset_variant:hero"; sleep 2
assert_contains "(b) the hero chip: the Hero again" "preset_w10m_hero</string>" "$(prefs_now)"

log "restore: Default, then pm clear -> provision.sh -> Home"
open_theme "$ROW_DIR/b-end.xml"; tap_node "$ROW_DIR/b-end.xml" "theme_preset:Default"; sleep 2
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
