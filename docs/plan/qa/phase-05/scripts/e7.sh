#!/usr/bin/env bash
# E7 — an emoji from the panel lands as the right Unicode code points.
#
#   "An emoji from the panel lands as the right Unicode code point"
#
# Three cells, chosen to cover what can go wrong: a single code point (grinning face, the first
# smiley), a ZWJ sequence from People (several code points joined by U+200D), and a symbol carrying a
# variation selector. For each, the code points that reach the fixture's field are compared with the
# code points the panel's own index says that cell inserts (assets/keyboard/emoji/index.tsv) — the
# artwork is Fluent's, what goes into the field is plain Unicode (Decisions).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E7 "an emoji from the panel lands as the right code points"
kb_begin
INDEX="$REPO/app/src/main/assets/keyboard/emoji/index.tsv"
cps_of() { python3 -c "import sys; print(' '.join('%X' % ord(c) for c in sys.argv[1]))" "$1"; }

pick() { # category cell-index
  local D="$ROW_DIR/e7.xml"
  open_field field_text
  kb_dump "$D"; tap_key "$D" emoji; sleep 1
  kb_dump "$D"; tap_node "$D" "kb_emoji_cat_$1"; sleep 1
  kb_dump "$D"
  # The grid is lazy: only the cells on screen exist. Swipe it left until the wanted cell is laid out.
  local n=0 gy
  gy="$(node_center "$D" kb_emoji_panel | cut -d' ' -f2)"
  while [ "$(has_node "$D" "kb_emoji_cell_$2")" = no ] && [ $n -lt 15 ]; do
    adb shell input swipe 900 "$gy" 250 "$gy" 250; sleep 0.8; kb_dump "$D"; n=$((n + 1))
  done
  note "$1 cell $2: $n swipe(s) to reach it"
  local desc
  desc="$(grep -o "resource-id=\"kb_emoji_cell_$2\"[^>]*" "$D" | head -1)"
  [ -n "$desc" ] || desc="$(grep -o "<node[^>]*resource-id=\"kb_emoji_cell_$2\"" "$D" | head -1)"
  local text
  text="$(python3 - "$D" "$2" <<'PY'
import re, sys, html
x = open(sys.argv[1], encoding="utf-8").read()
for n in re.finditer(r'<node[^>]*>', x):
    s = n.group(0)
    if 'resource-id="kb_emoji_cell_%s"' % sys.argv[2] in s:
        m = re.search(r'content-desc="([^"]*)"', s)
        print(html.unescape(m.group(1)) if m else "")
        break
PY
)"
  tap_node "$D" "kb_emoji_cell_$2"; sleep 1
  local got
  got="$(read_mirror text)"; got="${got#[}"; got="${got%]}"
  local want
  want="$(awk -F'\t' -v t="$text" '$2 == t { print $1; exit }' "$INDEX")"
  note "$1 cell $2: panel cell says [$text] = index [$want]; field got [$(cps_of "$got")]"
  assert_ne "$1 cell $2 is an emoji the index knows" "" "$want"
  assert_eq "$1 cell $2 lands as its index code points" "$want" "$(cps_of "$got")"
}

pick smileys 0
assert_eq "the first smiley is U+1F600 grinning face" "1F600" "$(awk -F'\t' '$3=="Smileys & Emotion" {print $1; exit}' "$INDEX")"
# The People page's first ZWJ sequence, found by its position in the index's People list.
zwj="$(awk -F'\t' '$3=="People & Body" {i++; if ($1 ~ / 200D /) {print i-1; exit}}' "$INDEX")"
note "first ZWJ sequence on the People page is cell $zwj"
pick people "$zwj"
vs="$(awk -F'\t' '($3=="Symbols"||$3=="Flags") {i++; if ($1 ~ / FE0F$/) {print i-1; exit}}' "$INDEX")"
note "first variation-selector emoji on the Symbols page is cell $vs"
pick symbols "$vs"
assert_contains "the diagnostics name the inserted code points" "emoji inserted U+" "$(ime_log 'emoji inserted' | tail -1)"

kb_end
row_end
