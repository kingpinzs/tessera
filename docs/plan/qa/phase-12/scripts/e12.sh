#!/usr/bin/env bash
# Phase 12 E12 — the pictures (T12-2, ruling (a), r3 D1 / D2 / D10 / D11 / V2 / V9).
# Host: the four picked sources are what the doc says; the host script logs each file's scale factor; each committed
# WebP's sha256 equals the script's output on the same sources. APK: all six preset pictures present at 1872 x 4056 (an APK
# lacking any one FAILs), none >= 2.5 MB, their sum <= 8 MB, and the APK <= 8 MB larger than the same commit built without
# them. Device, surface (b): HAL, Soft and Lumia rebuild phase 13's static backdrop for their picture once the app list
# composes; the original and Midnight rebuild nothing and turn acrylic off by the setting.
#   NOPICS_APK=<the same commit built with the six WebP removed> (the orchestrator builds it from a git worktree)
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
row_begin E12 "the preset pictures: sources, script, APK, backdrop"
RES="$REPO/app/src/main/res/drawable-nodpi"
NAMES="hal soft lumia midnight w10m_hero w10m_streaks"

log "host: the four picked sources (identify)"
for f in hal-a lumia-b midnight-b; do
  assert_eq "art/themes/$f.png is PNG 941 x 1672" "PNG 941x1672" "$(identify -format '%m %wx%h' "$REPO/art/themes/$f.png")"
done
assert_eq "art/themes/soft-c.png is JPEG 1536 x 2752 (JPEG data under a .png name)" "JPEG 1536x2752" "$(identify -format '%m %wx%h' "$REPO/art/themes/soft-c.png")"
record "the brief's 1024-px floor" "waived for hal-a, lumia-b, midnight-b (941 px) by Jeremy's pick, Decisions 2026-09-28"

log "host: the script's scale factors, and its output equals the committed WebP"
TMPOUT="$(mktemp -d "${TMPDIR:-/tmp}/p12-e12-XXXX")"
python3 "$REPO/tools/make-preset-pictures.py" "$TMPOUT" > "$ROW_DIR/host-script.txt" 2>&1
echo $? > "$ROW_DIR/host-script.rc"
assert_eq "host script rc" "0" "$(cat "$ROW_DIR/host-script.rc")"
scale() { grep "^preset_$1:" "$ROW_DIR/host-script.txt" | grep -oE 'scale=[0-9.]+' | cut -d= -f2; }
for n in hal lumia midnight; do assert_within "scale factor preset_$n ~ 2.43" 2.43 "$(scale $n)" 0.01; done
assert_within "scale factor preset_soft ~ 1.47" 1.47 "$(scale soft)" 0.01
assert_within "scale factor preset_w10m_hero ~ 2.64" 2.64 "$(scale w10m_hero)" 0.01
assert_within "scale factor preset_w10m_streaks ~ 2.11" 2.11 "$(scale w10m_streaks)" 0.01
for n in $NAMES; do
  assert_eq "committed preset_$n.webp sha256 = the script's output" "$(sha256sum "$TMPOUT/preset_$n.webp" | cut -c1-64)" "$(sha256sum "$RES/preset_$n.webp" | cut -c1-64)"
done
rm -rf "$TMPOUT"

log "APK: the six pictures, their size, the size against the same commit built without them"
unzip -l "$APK" > "$ROW_DIR/apk-list.txt"
sha256sum "$APK" > "$ROW_DIR/with.apk.sha256"
total=0
for n in $NAMES; do
  entry="$(grep -oE "res/drawable-nodpi[^ ]*/preset_$n\.webp" "$ROW_DIR/apk-list.txt" | head -1)"
  assert_ne "APK carries preset_$n.webp" "" "$entry"
  [ -n "$entry" ] || continue
  assert_eq "preset_$n in the APK is 1872 x 4056" "1872x4056" "$(unzip -p "$APK" "$entry" | identify -format '%wx%h' -)"
  size="$(grep -E " $entry\$" "$ROW_DIR/apk-list.txt" | awk '{print $1}')"
  note "preset_$n: $entry $size bytes"
  assert_eq "preset_$n under 2.5 MB" "yes" "$([ "$size" -lt 2621440 ] && echo yes || echo no)"
  total=$((total + size))
done
record "the six pictures in the APK, bytes" "$total"
assert_eq "their sum <= 8 MB" "yes" "$([ "$total" -le 8388608 ] && echo yes || echo no)"
if [ -n "${NOPICS_APK:-}" ] && [ -f "$NOPICS_APK" ]; then
  sha256sum "$NOPICS_APK" > "$ROW_DIR/without.apk.sha256"
  unzip -l "$NOPICS_APK" > "$ROW_DIR/apk-list-without.txt"
  assert_eq "the no-picture build carries none of them" "0" "$(grep -c 'drawable-nodpi[^ ]*/preset_' "$ROW_DIR/apk-list-without.txt")"
  with="$(stat -c%s "$APK")"; without="$(stat -c%s "$NOPICS_APK")"
  record "APK bytes with / without the pictures" "$with / $without (difference $((with - without)))"
  assert_eq "the APK is <= 8 MB larger than without them" "yes" "$([ $((with - without)) -le 8388608 ] && echo yes || echo no)"
else
  _verdict FAIL "the APK is <= 8 MB larger than without them" "NOPICS_APK not given"
fi

log "device, surface (b): the static backdrop per preset"
restore_fresh start
assert_eq "fresh provision rc" "0" "$(cat "$ROW_DIR/provision-start.rc")"
open_theme() {
  adb shell am start -n "$PKG/.settings.SettingsActivity" --activity-clear-task --es page START_THEME >/dev/null 2>&1
  sleep 3
  dump_ui "$ROW_DIR/theme-$1.xml"
}
to_app_list() {
  adb shell input keyevent KEYCODE_HOME; sleep 2.5
  adb shell input swipe 900 1200 150 1200 250; sleep 3
  dump_ui "$ROW_DIR/applist-$1.xml"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
}
tap_preset() { # label name
  open_theme "$1"
  tap_node "$ROW_DIR/theme-$1.xml" "theme_preset:$2"; sleep 2
}
tap_preset default Default
to_app_list default
for p in "hal:HAL" "soft:Soft" "lumia:Lumia"; do
  id="${p%%:*}"; name="${p#*:}"
  MARK="$(ring_mark)"
  tap_preset "$id" "$name"
  to_app_list "$id"
  listener_slice "$MARK" "$name"
  assert_eq "$name: app list composed" "yes" "$(has_node "$ROW_DIR/applist-$id.xml" app_list)"
  assert_contains "$name: [fluent] static backdrop rebuilt for its picture" "[fluent] static backdrop rebuilt for android.resource://app.tileshell/drawable/preset_$id in " "$SLICE"
done
for p in "w10m:Windows 10 Mobile (original)" "midnight:Midnight"; do
  id="${p%%:*}"; name="${p#*:}"
  tap_preset "pre-$id" Default   # from acrylic on, so the setting's change is logged
  to_app_list "pre-$id"
  MARK="$(ring_mark)"
  tap_preset "$id" "$name"
  to_app_list "$id"
  listener_slice "$MARK" "$name"
  assert_absent "$name: no static backdrop rebuilt" "static backdrop rebuilt" "$SLICE"
  assert_contains "$name: [fluent] acrylic=off reason=setting" "[fluent] acrylic=off reason=setting" "$SLICE"
done

log "restore: Default, then pm clear -> provision.sh -> Home"
tap_preset end Default
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
