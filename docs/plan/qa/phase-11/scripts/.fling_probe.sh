#!/usr/bin/env bash
# Probe (the round-2 re-judge's D2-1): does Start fling, and how far, after swipes of different speeds?
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin FLING-probe "does Start fling: folder:qa's travel after swipes of different speeds (tall baseline)"
restore baseline_layout-tall.json
for sw in "540 1900 540 1500 40" "540 1900 540 1500 80" "540 1900 540 1500 150" "540 1900 540 1500 400"; do
  ensure_start_page; qdump "$ROW_DIR/a.xml"; y0=$(bounds "$ROW_DIR/a.xml" tile:folder:qa | awk '{print $2}')
  adb shell input swipe $sw; sleep 2.5
  qdump "$ROW_DIR/b.xml"; y1=$(bounds "$ROW_DIR/b.xml" tile:folder:qa | awk '{print $2}')
  note "input swipe $sw: folder:qa top $y0 -> $y1 (moved $((y0 - y1)); the finger travelled 400)"
  restore baseline_layout-tall.json
done
row_end
