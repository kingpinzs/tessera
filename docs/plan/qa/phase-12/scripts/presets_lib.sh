#!/usr/bin/env bash
# Phase 12 E11 / E13 helpers: the preset table as the phase doc writes it (literals, never read from the app), the
# wizard-side fixture, and the Start / Tess / keyboard captures with their checkers. Source after lib.sh and p12.sh.
. "$LAYOUT_SH"
. "$QAROOT/phase-05/scripts/kb.sh"
RES="$REPO/app/src/main/res/drawable-nodpi"

# id|display name|accent hex|theme|picture|transparency|press|tess_lens|keyboard|effects|alpha over the picture
PRESETS="default|Default|0078D7|DARK||0.5|NONE|hal|DARK|true|
w10m|Windows 10 Mobile (original)|3E65FF|DARK|preset_w10m_hero|0.75|NONE|accent|DARK|false|0.40
hal|HAL|E81123|DARK|preset_hal|0.35|NONE|hal|DARK|true|0.72
soft|Soft|8E8CD8|LIGHT|preset_soft|0.6|P4_PRESS|accent|LIGHT|true|0.52
lumia|Lumia|00B7C3|DARK|preset_lumia|0.5|WP8_TILT|accent|DARK|true|0.60
midnight|Midnight|6B69D6|DARK|preset_midnight|0.0|NONE|hal_dim|DARK|false|1.0"

preset_field() { # id field-number(1-based)
  echo "$PRESETS" | awk -F'|' -v id="$1" -v f="$2" '$1==id {print $f}'
}
hex_long() { python3 -c "print(0xFF000000 | 0x$1)"; }
hex_hue() { python3 -c "import colorsys; h='$1'; r,g,b=(int(h[i:i+2],16)/255 for i in (0,2,4)); print(round(colorsys.rgb_to_hsv(r,g,b)[0]*360,2))"; }

# start_theme.xml holds the preset's row (E11, E13).
check_keys() { # id prefs-text label
  local id="$1" p="$2" l="$3" pic
  assert_contains "$l: theme_preset = $id" "<string name=\"theme_preset\">$id</string>" "$p"
  assert_contains "$l: accent" "<long name=\"accent\" value=\"$(hex_long "$(preset_field "$id" 3)")\" />" "$p"
  assert_contains "$l: theme" "<string name=\"theme\">$(preset_field "$id" 4)</string>" "$p"
  pic="$(preset_field "$id" 5)"
  if [ -z "$pic" ]; then
    assert_absent "$l: background absent" 'name="background"' "$p"
  else
    assert_contains "$l: background = the preset's picture" "<string name=\"background\">android.resource://app.tileshell/drawable/$pic</string>" "$p"
  fi
  assert_contains "$l: transparency" "<float name=\"transparency\" value=\"$(preset_field "$id" 6)\" />" "$p"
  assert_contains "$l: press" "<string name=\"press\">$(preset_field "$id" 7)</string>" "$p"
  assert_contains "$l: tess_lens" "<string name=\"tess_lens\">$(preset_field "$id" 8)</string>" "$p"
  assert_absent "$l: no tess_ring key" 'tess_ring' "$p"
  assert_contains "$l: keyboard_palette" "<string name=\"keyboard_palette\">$(preset_field "$id" 9)</string>" "$p"
  assert_contains "$l: transparency_effects" "<boolean name=\"transparency_effects\" value=\"$(preset_field "$id" 10)\" />" "$p"
}

prefs_now() { adb shell run-as "$PKG" cat shared_prefs/start_theme.xml | tr -d '\r'; }

# Start's tile band over the picture, by the picture oracle (T12-16, r3 V7): Default no picture (T = accent, black gutter);
# Midnight opaque (T = accent); the rest T = alpha accent + (1 - alpha) B +-4 and T != accent.
check_start() { # id label png xml [picture override]
  local id="$1" l="$2" png="$3" xml="$4" pic="${5:-$(preset_field "$1" 5)}" accent alpha mode rc
  accent="$(preset_field "$id" 3)"; alpha="$(preset_field "$id" 11)"
  case "$id" in
    default) mode="--no-picture" ;;
    midnight) mode="--expect-opaque" ;;
    *) mode="" ;;
  esac
  python3 "$HERE/picture_oracle.py" ${pic:+--picture "$RES/$pic.webp"} --screenshot "$png" --dump "$xml" --tile tile:slot:PEOPLE \
    --accent "$accent" ${alpha:+--alpha "$alpha"} $mode --out "${png%.png}-oracle.json" > "${png%.png}-oracle.txt" 2>&1
  rc=$?
  note "$l oracle: $(tail -1 "${png%.png}-oracle.txt")"
  assert_eq "$l: Start's tile band by the picture oracle ($(tail -1 "${png%.png}-oracle.txt" | cut -c1-60))" "0" "$rc"
}

# Tess over the running Start: the persona idle at reveal 0, one screencap after the entrance settles, the lens colours by
# the preset-colour checker with the expected hue AND brightness as literals.
check_tess() { # id label out-prefix [accent-hex override]
  local id="$1" l="$2" o="$3" lens hue bright rc accent="${4:-$(preset_field "$1" 3)}"
  lens="$(preset_field "$id" 8)"
  # `--hue hal` keeps each Brand tone's own hue (rim, glow and the rest sit a few degrees apart); a number re-hues all
  # three the way LensTones.of("accent") does. The brightness factor is the other literal (hal_dim = 0.5).
  case "$lens" in
    hal) hue=hal; bright=1.0 ;;
    accent) hue="$(hex_hue "$accent")"; bright=1.0 ;;
    hal_dim) hue=hal; bright=0.5 ;;
  esac
  adb shell input keyevent KEYCODE_ASSIST; sleep 3
  dump_ui "$o.xml"; screencap "$o.png"
  assert_eq "$l: Tess's session opens (ASSISTANT held)" "yes" "$(has_node "$o.xml" cortana_session)"
  python3 "$HERE/lens_check.py" --screenshot "$o.png" --dump "$o.xml" --hue "$hue" --brightness "$bright" --out "$o-lens.json" > "$o-lens.txt" 2>&1
  rc=$?
  note "$l lens ($lens, hue $hue, brightness $bright): $(tail -1 "$o-lens.txt")"
  assert_eq "$l: Tess's lens is $lens (hue $hue, brightness $bright)" "0" "$rc"
  adb shell input keyevent KEYCODE_BACK; sleep 2
}

# The keyboard's letter key fill: DARK (48,48,48) +-4 (phase 05 E3) or LIGHT #E6E6E6 = (230,230,230) +-4.
check_keyboard() { # id label out-prefix
  local id="$1" l="$2" o="$3" exp rc
  [ "$(preset_field "$id" 9)" = LIGHT ] && exp="230,230,230" || exp="48,48,48"
  kb_begin
  open_field field_text
  kb_dump "$o.xml"
  screencap "$o.png"
  python3 "$HERE/key_fill.py" "$o.png" "$o.xml" kb_key_q "$exp" 4 > "$o-key.txt" 2>&1
  rc=$?
  note "$l keyboard: $(tail -1 "$o-key.txt")"
  assert_eq "$l: letter key fill $(preset_field "$id" 9) ($exp +-4)" "0" "$rc"
  kb_end
}

# The wizard-side fixture (Acceptance "Visual measurements after a preset", r3 V6): every grant, no marker, the layout
# seeded BEFORE any preset action, Usage access revoked, Not now through the steps to the presets page.
wizard_fixture() { # label
  local label="$1"
  leave_home
  adb shell pm clear "$PKG" >/dev/null
  provision_no_marker "fixture-$label"
  layout_restore "$QAROOT/phase-02/baseline_layout.json" > "$ROW_DIR/layout-$label.txt" 2>&1
  adb shell ime set "$KEYBOARD" >/dev/null
  adb shell appops set "$PKG" GET_USAGE_STATS ignore
  adb shell input keyevent KEYCODE_HOME; sleep 4
  walk_not_now "$ROW_DIR/fix-$label" > "$ROW_DIR/fix-$label.steps" 2>&1
  FIX_LAST="$(ls -t "$ROW_DIR"/fix-$label-*.xml | head -1)"
  assert_eq "($label) fixture reaches wizard_presets" "yes" "$(has_node "$FIX_LAST" wizard_presets)"
  assert_eq "($label) ASSISTANT role held" "$PKG" "$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
}

# Start + theme in a fresh task (tags theme_preset:<name>).
open_theme() { # out.xml
  adb shell am start -n "$PKG/.settings.SettingsActivity" --activity-clear-task --es page START_THEME >/dev/null 2>&1
  sleep 3
  dump_ui "$1"
}
