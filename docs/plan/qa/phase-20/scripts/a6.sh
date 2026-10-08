#!/usr/bin/env bash
# Phase 20 A6 — Find a song and listen elsewhere.
#
#   1  in Music's catalogue, search "qa artist"; open QA Song A
#   2  install testapps/qa-tunes; redraw the title page
#   3  tap "Listen on QA Tunes"; then `am force-stop app.tileshell` + Home (C-6)
#   4  type_request "listen to qa artist on qa tunes"
#   5  uninstall the stub
#
# Pass (a)–(d) as the phase doc lists them (`:8081` reads `:8093`).
# How (a)'s "artwork drawn" is read: each row carries a `catalogue_art:<id>` node (the page tags a row without art
# `catalogue_placeholder:<id>` instead), AND the screenshot's pixels inside that node's bounds are the fixture cover's own flat
# colour, AND the catalogue fixture logged the cover requests.
# (b) asserts only the QA Tunes entry (`handoff_service:qa-tunes`): none before the install, one after. Whatever else is
# listed is recorded, never asserted.
#
# Changes on the device: the QA Tunes stub is installed for steps 2–4 and uninstalled (also from the EXIT trap).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p20.sh"
trap 'adb uninstall $QATUNES >/dev/null 2>&1; fixtures_down' EXIT
stub_installed() { adb shell pm list packages $QATUNES </dev/null | tr -d '\r' | grep -c "^package:$QATUNES$"; }
stub_lines() { adb logcat -d -s TileShellQa:I </dev/null | tr -d '\r' | grep 'qa-tunes:'; }
urldecode() { python3 -c 'import sys, urllib.parse; print(urllib.parse.unquote_plus(sys.stdin.read()))'; }
# The fixture's two covers are FLAT colours (catalogue_server.py COVERS: (200, 60, 40) and (40, 90, 200)), so a drawn
# cover is one colour — the first A6 run asked for "more than 8 distinct colours" and failed a picture that was drawn
# (a DRIVER fault; that row re-run). Now: the colour that fills the art node is one of the fixture's two.
art_colour() { # screenshot name, "l t r b" -> the most common colour inside that rectangle, as "r g b"
  python3 - "$ROW_DIR/$1.png" $2 <<'PY'
import sys
from PIL import Image
l, t, r, b = map(int, sys.argv[2:6])
im = Image.open(sys.argv[1]).convert('RGB').crop((l + 8, t + 8, r - 8, b - 8))
print("%d %d %d" % max(im.getcolors(1 << 20))[1])
PY
}

p20_begin A6 "find a song in the catalogue and listen on QA Tunes"
prefs_guard a b c d
fixtures_up
[ "$(stub_installed)" = 0 ] || adb uninstall $QATUNES >/dev/null 2>&1
assert_eq "the QA Tunes stub is NOT installed at the start" "0" "$(stub_installed)"
assert_yes "the stub's APK is built" "$([ -f "$QATUNES_APK" ] && echo yes || echo no)"
baseline_start

# ----------------------------------------------------------------------------------------------- 1: the catalogue
log "--- 1: in Music's catalogue, search \"qa artist\"; open QA Song A"
letter a
to_radio; assert_eq "(a) Music is on the radio pivot (the catalogue's entry is there)" "0" "$?"
radio_reach radio-cat music_catalogue_entry
MARK="$(ring_mark)"
tap radio-cat music_catalogue_entry; sleep 2; d cat0
assert_eq "(a) the catalogue's search page opened" "yes yes" "$(has cat0 catalogue_page:search) $(has cat0 catalogue_search_box)"
tap cat0 catalogue_search_box; sleep 1
adb shell input text "qa%sartist"; sleep 0.8
adb shell input keyevent KEYCODE_ENTER
for _ in $(seq 1 12); do sleep 1; d results; [ "$(ids results catalogue_row: | wc -l)" -ge 3 ] && break; done
sleep 4; d results; shot 01-catalogue-results
CROWS="$(ids results catalogue_row: | tr '\n' ' ')"
record "(a) the rows" "$CROWS"
assert_eq "(a) three catalogue_row: rows" "3" "$(ids results catalogue_row: | wc -l | xargs)"
TITLES=""; for r in $CROWS; do TITLES+="$(nt results "music_pri:$r") / "; done
assert_eq "(a) …with the three titles" "QA Song A / QA Song B / QA Song C / " "$TITLES"
ARTN=0; ARTDRAWN=0; ARTREC=""
for r in $CROWS; do
  a="catalogue_art:${r#catalogue_row:}"
  if [ "$(has results "$a")" = yes ]; then
    ARTN=$((ARTN + 1)); c="$(art_colour 01-catalogue-results "$(bounds "$ROW_DIR/results.xml" "$a")")"; ARTREC+="($c) "
    case "$c" in "200 60 40"|"40 90 200") ARTDRAWN=$((ARTDRAWN + 1)) ;; esac
  else ARTREC+="(placeholder) "; fi
done
record "(a) the colour filling each row's art node (the fixture's covers: 200 60 40 and 40 90 200)" "$ARTREC"
assert_eq "(a) their artwork is drawn: catalogue_art:<id> nodes / of them filled with a fixture cover's colour in the screenshot" "3 / 3" "$ARTN / $ARTDRAWN"
SONG_A="$(for r in $CROWS; do [ "$(nt results "music_pri:$r")" = "QA Song A" ] && echo "$r"; done | head -1)"
tap results "$SONG_A"; sleep 3; d title-before; shot 02-title-before-install
assert_eq "(a) QA Song A's title page opened" "yes QA Song A" "$(has title-before catalogue_page:title) $(nt title-before catalogue_title)"

letter b
record "(b) the hand-off entries before the install (recorded, not asserted)" "$(ids title-before handoff_service: | tr '\n' ' ')"
assert_eq "(b) no QA Tunes entry (handoff_service:qa-tunes) before the install" "0" "$(ids title-before handoff_service:qa-tunes | wc -l | xargs)"

# ----------------------------------------------------------------------------------------------- 2: install, redraw
log "--- 2: install testapps/qa-tunes; redraw the title page"
adb install -r "$QATUNES_APK" > "$ROW_DIR/stub-install.out" 2>&1
assert_eq "(b) the stub is installed" "1" "$(stub_installed)"
adb shell input keyevent KEYCODE_BACK; sleep 2; d results2
[ "$(has results2 "$SONG_A")" = yes ] || { sleep 2; d results2; }
tap results2 "$SONG_A"; sleep 3; d title-after; shot 03-title-after-install
assert_eq "(b) the title page is drawn again" "yes QA Song A" "$(has title-after catalogue_page:title) $(nt title-after catalogue_title)"
record "(b) the hand-off entries after the install (recorded)" "$(ids title-after handoff_service: | tr '\n' ' ')"
assert_eq "(b) one QA Tunes entry after the install" "1" "$(ids title-after handoff_service:qa-tunes | wc -l | xargs)"
record "(b) its label" "$(nt title-after handoff_label:qa-tunes)"

# the catalogue fixture's requests are complete now (the search and the covers)
letter a
flog "$CLOG.jsonl" 'True' > "$ROW_DIR/catalogue-requests.txt"
CN="$(grep -c . "$ROW_DIR/catalogue-requests.txt")"
record "(a) the catalogue fixture's requests" "$(cut -d'|' -f1 "$ROW_DIR/catalogue-requests.txt" | cut -c1-90 | tr '\n' ';')"
assert_ne "(a) the catalogue fixture logged requests (the check has something to read)" "0" "$CN"
assert_eq "(a) User-Agent: Tessera/ on every catalogue request (requests without it)" "0" "$(flog "$CLOG.jsonl" 'not h.get("user-agent", "").startswith("Tessera/")' | wc -l | xargs)"
assert_ne "(a) …the covers among them (the artwork came from the fixture)" "0" "$(grep -c '/coverart/' "$ROW_DIR/catalogue-requests.txt")"

# ----------------------------------------------------------------------------------------------- 3: Listen on QA Tunes
log "--- 3: tap \"Listen on QA Tunes\"; then am force-stop app.tileshell + Home (C-6)"
letter c
BEFORE="$(sboth)"; session_save session-before-handoff
adb logcat -c; MARK3="$(ring_mark)"
tap title-after handoff_service:qa-tunes; sleep 3.5
TOP="$(top)"; shot 04-stub-resumed
stub_lines > "$ROW_DIR/stub-lines-tap.txt"
AFTER="$(sboth)"; session_save session-after-handoff
mring "$MARK3" > "$ROW_DIR/slice-handoff.txt"
record "(c) the resumed activity" "$TOP"
assert_contains "(c) the stub is resumed" "$QATUNES/" "$TOP"
SL="$(tail -1 "$ROW_DIR/stub-lines-tap.txt" | sed 's/^.*TileShellQa: //')"
record "(c) the stub's TileShellQa line" "$SL"
DATA="$(printf '%s' "$SL" | sed -n 's/.* data=\([^ ]*\).*/\1/p')"
assert_yes "(c) its line shows a URI (data=…)" "$(printf '%s' "$DATA" | grep -qE '^[a-z][a-z0-9+.-]*:' && echo yes || echo no)"
DEC="$(printf '%s' "$DATA" | urldecode)"
assert_contains "(c) …with the title" "QA Song A" "$DEC"
assert_contains "(c) …and the artist" "QA Artist" "$DEC"
HL="$(grep -F '[music] handoff:' "$ROW_DIR/slice-handoff.txt" | head -1 | stripped)"
record "(c) the line" "$HL"
assert_contains "(c) [music] handoff: qa-tunes \"QA Song A\" -> is in the slice" '[music] handoff: qa-tunes "QA Song A" ->' "$HL"
record "(c) the shell's session before / after" "[$BEFORE] / [$AFTER]"
assert_eq "(c) the shell's session is unchanged" "$BEFORE" "$AFTER"
adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell am force-stop $QATUNES
c6; ensure_start; ime_baseline

# ----------------------------------------------------------------------------------------------- 4: typed
log "--- 4: type_request \"listen to qa artist on qa tunes\""
letter d
assert_eq "(d) the phone is unlocked" "isKeyguardShowing=false" "$(keyguard)"
assert_not_top="$(top)"; record "(d) the top activity before the request" "$assert_not_top"
adb logcat -c
open_tess tess-listen; assert_eq "(d) Tess is open" "0" "$?"
MARK4="$(ring_mark)"
type_request "listen to qa artist on qa tunes" 8; assert_eq "(d) type_request found the text box" "0" "$?"
TOP="$(top)"; shot 05-stub-from-tess
stub_lines > "$ROW_DIR/stub-lines-typed.txt"
ring_since "$MARK4" launcher > "$ROW_DIR/slice-typed.txt"
record "(d) Tess's replies / the resumed activity" "$(replies_from "$MARK4" | tr '\n' '|') / $TOP"
assert_contains "(d) the typed phrase opens the same stub" "$QATUNES/" "$TOP"
SL2="$(tail -1 "$ROW_DIR/stub-lines-typed.txt" | sed 's/^.*TileShellQa: //')"
record "(d) the stub's TileShellQa line" "$SL2"
assert_contains "(d) …which logged the request it was opened with (qa artist)" "qa artist" "$(printf '%s' "$SL2" | urldecode | tr 'A-Z' 'a-z')"
record "(d) the [music] handoff line" "$(grep -F '[music] handoff:' "$ROW_DIR/slice-typed.txt" | head -1 | stripped)"
adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell am force-stop $QATUNES
c6; ensure_start; ime_baseline

# ----------------------------------------------------------------------------------------------- 5: uninstall
log "--- 5: uninstall the stub"
letter restore
adb uninstall $QATUNES > "$ROW_DIR/stub-uninstall.out" 2>&1
assert_eq "the stub is uninstalled" "0" "$(stub_installed)"
fixtures_down
ensure_start
p20_end a b c d
