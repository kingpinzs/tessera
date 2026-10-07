#!/usr/bin/env bash
# Phase 18 E11 — geometry and motion against r11/files.md (F) and r11/files-pass2.md (P2). Dump bounds and screencaps on
# the AVD (px ÷ 3 = epx on the 360-epx canvas). Tolerances per R11: ± 1 epx MEDIUM, ± 2 epx LOW, structure / order only for
# what R11 read from a camera; colours ± 4 per channel. One leg per block of the doc's row, in the doc's order:
#
#   frame       (1.1) the drawn status bar (its height = BarMetrics.STATUS_EPX, read from the source — C-17); the location
#               bar 48 ± 1 directly under it, fill (31,31,31); page background (0,0,0); the app bar 48 ± 1, fill
#               (31,31,31), directly on the nav bar.
#   location    (1.2) ≡ centre x 24 ± 1; breadcrumb left 60 ± 2, cap 11 ± 1 (the "T" of "This Device"), white; a "›"
#               between segments (ink in each gap); `/sdcard/QA-Files/sub/deep/deeper` collapses its middle to "…"; ↑
#               centre 24 from the right (± 2), (123,123,123) ± 4 at a volume root.
#   pane        (1.3) 256 ± 1 wide, fill (23,23,23), no scrim (the pixels right of it unchanged ± 2 against a screencap
#               before opening); rows 48 ± 1 from 72 + (STATUS_EPX − 24) ± 2 (Decisions 2026-10-06, build-time call 1),
#               glyph centre x 24 ± 1, label left 60 ± 1, the current row 0.6 · accent + 0.4 · (23,23,23) ± 4; order
#               Recent / This Device / the volume / Recycle Bin (the row's own public volume); no compact rail with the
#               pane closed; a cold start has the pane open with Recent current.
#   sort        (1.4) "Sort by:" left 12 ± 1 (ink RECORDED, about 13.7), cap top 16 ± 1 below the bar, (160,160,160) ± 4,
#               the value white, a ChevronDown node after it.
#   rows        (1.5) pitch 64 ± 1; icon left 20 ± 2, name left 72 ± 2; detail (165,165,165) ± 4.
#   icons       (1.6) three per row, column centres at W/3 intervals ± 2; the name centred under its icon; a long name
#               wraps to a second centred line, line pitch 20 ± 2; in a folder of thumbnails the row pitch is 172 ± 8 and
#               the app bar's third slot shows the list glyph (its label and its ink change). The pitch needs two rows
#               of thumbnails: `DCIM/Camera` holds the row's three photos (files_up media: ONE row), so the pitch is
#               asserted in the row's own `QA-Files/recent` (four PNG thumbnails) and DCIM/Camera gets the structure.
#   appbar      (1.7) glyph centres Select / New folder / Icons / Search 286 / 218 / 150 / 82 ± 1 from the right, More
#               24 ± 1, 24 ± 1 below the bar's top; expanded 60 ± 2 with labels; overflow Refresh / Select all / Clear
#               selection / Properties in that order (then the build's fifth line, Settings — Decisions call 4), Clear
#               selection (137,137,137) ± 4 with nothing selected.
#   selection   (1.8; P2 §1) "0 items selected" and the four `files_sel:` buttons disabled and (137,137,137) ± 4; "1 item
#               selected" (the singular), "2 items selected"; checkbox 20 ± 2 square, centre x 22 ± 2, centred on the
#               icon; icon left 52 ± 2, name left 104 ± 2; a selected row's fill = the accent ± 4, full width and full
#               height; the bar Delete / Move to / Copy to / Share; Share disabled once `sub` is selected; Back leaves.
#   picker      (P2 §1, LOW) "Choose a folder" at the sort line's left (12 ± 2), no `files_sort`; no checkbox; ≡, the
#               breadcrumb and ↑ present, the pane opens inside it; the bar holds ok / cancel / More at 150 / 82 / 24 ± 2
#               and nothing else; Back inside a subfolder → the picker's previous folder; Back at its first folder
#               closes it with nothing moved (r3 D12).
#   dialogs     (Y4, structure) rename, new folder, delete confirmation, conflict: `files_dialog` at the top, full
#               width, fill (74,74,74) ± 4, a title, two side-by-side buttons (three answers for the conflict).
#   hold        (1.9) a file: Delete / Move to / Copy to / Share / Rename / Properties; `sub`: the same without Share;
#               item pitch 44 ± 2, width 240.5 ± 2.
#   properties  (1.10) a page: no app bar node, the last crumb = the item's name, "Date modified:" / "File type:" /
#               "File size:" with values, a "Video" section for qa-steps.mp4.
#   recent      (P2 §1 / §4.8, LOW) no `files_sort`; a row's detail a date only; the bar Select · Icons · Search · •••
#               with no New folder, the three disabled when the list is empty (E14 owns the empty line's text and place:
#               RECORDED here).
#   progress    (P2 §1, LOW) the row's own big.bin moved ACROSS volumes (Decisions call 3: a same-volume move has no
#               progress), paced (`files_up paced big`), "mid" by the floor's meaning: `files_progress` top = the status
#               bar's bottom ± 2 (BUILD-NOTES screens builder 12), height 66 ± 6, inset 21 ± 3 each side, "Moving
#               files…" centred, no percentage, no cancel node; a pixel outside the box differs from before the move
#               (the wash); on completion the destination folder; a copy reads "Copying files…".
#   motion      (Y5; P2 §5; the floor's tolerances) on the shell's clock, the recorder NOT running: `files_folder` after
#               a row tap, ↑, a breadcrumb segment and list ↔ icons — settle 300 ± 33; `files_pane_close` 0 ms, then
#               `files_folder`; `files_select` 200 ± 40; `files_hold` first 700 ± 33 after the press, settled 233–367
#               after that; `files_pane_open` settle 250–283; `files_more` 317 ± 33; `files_deselect` 200 ± 40; each
#               maxGapMs ≤ 33.4 (C-31).
#   record      three screenrecords CORROBORATE (phase 05's frame-spacing rule: frames ≤ 18.2 ms apart during the motion,
#               else retaken — up to 4 takes): a folder change (a frame with an EMPTY list and the bars unmoved; the first
#               entrance frame ≥ 7 epx below rest; names, then detail lines, then icons), the pane's close (gone and the
#               list empty in ONE frame, rows after), the pane's open (its edge grows, the label's x does not move).
#
#   e11.sh              every leg (the graded row, folder E11)
#   E11_LEGS="…" e11.sh a driver-development run of some legs: folder E11_DEV, never the row's evidence
#
# Changes on the device: QA-Files with big.bin (files_up paced big media; files_down), the row's extra folders inside it,
# a virtual disk (pubvol_up / pubvol_down), the Recent store (restored byte for byte), the pace pref (cleared). No wipe.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/p18_a.sh"
STAMP_FILES="$STAMP_FILES $HERE/e11_frames.py"
ALL="frame location pane sort rows icons appbar selection picker dialogs hold properties recent progress motion record"
LEGS="${E11_LEGS:-$ALL}"
if [ "$LEGS" = "$ALL" ]; then ROWNAME=E11; else ROWNAME=E11_DEV; fi
leg() { case " $LEGS " in *" $1 "*) log "--- $1 ($(date '+%H:%M:%S'))"; return 0 ;; *) return 1 ;; esac; }
keep_earlier_run "$ROWNAME"
row_begin "$ROWNAME" "geometry and motion against r11/files.md and r11/files-pass2.md — legs: $LEGS"
LC0="$(lc_mark)"
LONG="a-long-file-name-that-wraps-in-the-icons-view.txt"
DEEP="$QF/sub/deep"
recent_store() { adb shell run-as app.tileshell cat files/files-recent.json 2>/dev/null | tr -d '\r'; }
from_right() { python3 -c "import sys; print('%.1f' % (360 - float(sys.argv[1])))" "$1"; }
sub2() { python3 -c "import sys; print('%.1f' % (float(sys.argv[1]) - float(sys.argv[2])))" "$1" "$2"; }
enabled() { attr "$1" "$2" enabled; }
back() { adb shell input keyevent KEYCODE_BACK; sleep "${1:-1}"; }
last_crumb() { X "$1" "files_crumb:$(ids_of "$1" files_crumb: | grep -E '^[0-9]+$' | tail -1)"; }
# The row's list mode, from an icon's width: a list row's icon box is at most 40 epx wide.
is_icons() { python3 -c "import sys; print('yes' if float(sys.argv[1] or 0) > 45 else 'no')" "$(edim "$1" "files_icon:$2" w)"; }
tap_xy() { adb shell input tap "$1" "$2"; sleep "${3:-1}"; }

baseline_start
OPTS="media"
case " $LEGS " in *" progress "*) OPTS="paced big media" ;; esac
# shellcheck disable=SC2086
files_up $OPTS || { row_end; exit 1; }
# The row's own extras inside QA-Files (removed with it by files_down): a copy for the conflict, a path four levels deep
# whose last folder is empty, and a folder of type icons with one long name.
adb shell "cp $QA_FILES/b.bin $QA_FILES/sub/b.bin && mkdir -p $QA_FILES/sub/deep/deeper && printf long > '$QA_FILES/sub/deep/$LONG' && printf x > $QA_FILES/sub/deep/x.txt && printf yy > $QA_FILES/sub/deep/y.bin"
assert_eq "the row's extras exist (sub/b.bin, sub/deep/deeper, the long name, x.txt, y.bin)" "b.bin deep|$LONG deeper x.txt y.bin" "$(q "ls $QA_FILES/sub" | xargs)|$(q "ls $QA_FILES/sub/deep" | xargs)"
pubvol_up
U="${PUBVOL_UUID:-}"
[ -n "$U" ] || { _verdict FAIL "the public volume is up" "pubvol_up gave no UUID"; files_down; row_end; exit 1; }
VLABEL="$(awk -F'\t' '$1 == "pubvol_description" { print $2 }' "$P18/BUILDSTART/records.tsv")"
c6; ensure_start
recent_store > "$ROW_DIR/recent-store-at-begin.json"
ACC="$(accent_rgb)"
record "the accent" "$ACC"
STATUS_SRC="$(sed -n 's/.*const val STATUS_EPX *= *\([0-9.]*\).*/\1/p' "$REPO/app/src/main/kotlin/app/tileshell/bars/SystemBars.kt" | head -1)"
record "BarMetrics.STATUS_EPX (the source)" "$STATUS_SRC"

# The page every geometry leg starts from: QA-Files in the list view, the pane closed; dump 00-qa, screencap 00-qa.
page_qa() { files_at "$QF"; D 00-qa; S 00-qa; }
page_qa
nodes 00-qa > "$ROW_DIR/00-qa.nodes"
if [ "$(is_icons 00-qa sub)" = yes ]; then T 00-qa files_bar:view 1.5; page_qa; fi
ST="$(edim 00-qa w10m_status_bar b)"          # the status bar's bottom, epx
LB="$(edim 00-qa files_location b)"           # the location bar's bottom, epx
AT="$(edim 00-qa files_appbar t)"             # the app bar's top, epx

# ------------------------------------------------------------------------------------------------- frame (1.1)
if leg frame; then
  assert_eq "frame: phase 01's drawn status bar is present (w10m_status_bar)" "yes" "$(H 00-qa w10m_status_bar)"
  assert_near "frame: its height = BarMetrics.STATUS_EPX ($STATUS_SRC, the source — C-17)" "$STATUS_SRC" "$ST" 0.1
  assert_near "frame: the location bar's top = the status bar's bottom" "$ST" "$(edim 00-qa files_location t)" 0.1
  assert_near "frame: the location bar is 48 ± 1 epx (F 1.1.2)" 48 "$(edim 00-qa files_location h)" 1
  assert_near "frame: …and full width" 360 "$(edim 00-qa files_location w)" 0.5
  set -- $(B 00-qa files_location)
  assert_rgb "frame: the location bar's fill (31,31,31) ± 4" "31,31,31" "$(px 00-qa 870 $(( ($2 + $4) / 2 )))" 4
  assert_rgb "frame: the page background (0,0,0) ± 4 (right of the rows, x 340 epx)" "0,0,0" "$(px 00-qa 1020 1200)" 4
  assert_near "frame: the app bar is 48 ± 1 epx (F 1.1.4)" 48 "$(edim 00-qa files_appbar h)" 1
  assert_near "frame: …directly on the nav bar (its bottom = the nav bar's top)" "$(edim 00-qa w10m_nav_bar t)" "$(edim 00-qa files_appbar b)" 0.1
  set -- $(B 00-qa files_appbar)
  assert_rgb "frame: the app bar's fill (31,31,31) ± 4" "31,31,31" "$(px 00-qa 60 $(( ($2 + $4) / 2 )))" 4
fi

# ------------------------------------------------------------------------------------------------- location bar (1.2)
if leg location; then
  I="$(node_ink 00-qa 00-qa files_menu)"
  record "location: the ≡ glyph's ink (px box, colour)" "$I"
  assert_near "location: ≡ centre x 24 ± 1 (F 1.2.2)" 24 "$(ink_dim "$I" cx)" 1
  assert_near "location: breadcrumb left 60 ± 2 (F 1.2.3; the node)" 60 "$(edim 00-qa files_crumb:0 l)" 2
  I="$(node_ink 00-qa 00-qa files_crumb:0 0 21)"          # the "T": the first 7 epx of "This Device"
  record "location: the T of This Device (px box, colour)" "$I"
  assert_near "location: …its ink's left 60 ± 2" 60 "$(ink_dim "$I" l)" 2
  assert_near "location: breadcrumb cap 11 ± 1 (F 1.2.4)" 11 "$(ink_dim "$I" h)" 1
  assert_rgb "location: breadcrumb white" "255,255,255" "$(ink_rgb "$I")" 4
  # The separators: ink between each two crumbs of QA-Files/sub (three segments, two gaps).
  files_at "$QF/sub"; D 10-sub; S 10-sub
  assert_eq "location: QA-Files/sub shows three crumbs" "This Device|QA-Files|sub" "$(X 10-sub files_crumb:0)|$(X 10-sub files_crumb:1)|$(X 10-sub files_crumb:2)"
  for g in "0 1" "1 2"; do
    set -- $g; A="$(B 10-sub "files_crumb:$1")"; Bx="$(B 10-sub "files_crumb:$2")"
    set -- $A $Bx; LBX="$(B 10-sub files_location)"
    I="$(ink 10-sub "$3" "$(echo "$LBX" | cut -d' ' -f2)" "$5" "$(echo "$LBX" | cut -d' ' -f4)")"
    record "location: the ink between two crumbs (px box, colour; F 1.2.5: a › 4.6 × 8.2 epx, (230,230,230))" "$I  = $(ink_dim "$I" w) × $(ink_dim "$I" h) epx"
    assert_ne "location: a › separator is drawn between the segments ($g)" "none" "$I"
  done
  # Four levels deep: the middle collapses to "…".
  files_at "$DEEP/deeper"; D 11-deeper; S 11-deeper; nodes 11-deeper > "$ROW_DIR/11-deeper.nodes"
  record "location: the crumbs of …/QA-Files/sub/deep/deeper, left to right" "$(python3 - "$ROW_DIR/11-deeper.xml" <<'PY'
import html, re, sys
x = open(sys.argv[1], encoding='utf-8', errors='replace').read()
out = []
for m in re.finditer(r'<node[^>]*>', x):
    s = m.group(0); rid = re.search(r'resource-id="([^"]*)"', s).group(1)
    if rid.startswith("files_crumb"):
        b = int(re.search(r'bounds="\[(-?\d+)', s).group(1)); t = re.search(r' text="([^"]*)"', s)
        out.append((b, "%s=%s" % (rid, html.unescape(t.group(1)) if t else "")))
print(" ".join(v for _, v in sorted(out)))
PY
)"
  assert_eq "location: a path four levels deep shows the … node (files_crumb_more)" "yes" "$(H 11-deeper files_crumb_more)"
  assert_eq "location: …its text is …" "…" "$(X 11-deeper files_crumb_more)"
  assert_eq "location: …the first segment and the last are still named" "This Device|deeper" "$(X 11-deeper files_crumb:0)|$(last_crumb 11-deeper)"
  assert_le "location: …fewer than the path's five segments are drawn as crumbs" 4 "$(ids_of 11-deeper files_crumb: | grep -cE '^[0-9]+$')"
  # ↑: its place (in a folder, where it is live) and its colour at a volume's root.
  I="$(node_ink 00-qa 00-qa files_up)"
  record "location: the ↑ glyph's ink in QA-Files (px box, colour)" "$I"
  assert_near "location: ↑ centre 24 from the right edge (± 2, LOW on position; F 1.2.8)" 24 "$(from_right "$(ink_dim "$I" cx)")" 2
  files_at "$SD"; D 12-root; S 12-root
  I="$(node_ink 12-root 12-root files_up)"
  assert_eq "location: at the volume's root ↑ is disabled" "false" "$(enabled 12-root files_up)"
  assert_rgb "location: …and (123,123,123) ± 4 (F 1.2.9)" "123,123,123" "$(ink_rgb "$I")" 4
  assert_near "location: …at the same place (24 from the right ± 2)" 24 "$(from_right "$(ink_dim "$I" cx)")" 2
fi

# ------------------------------------------------------------------------------------------------- the pane (1.3)
if leg pane; then
  page_qa
  assert_eq "pane: closed — no files_pane, no pane glyph nodes (no compact rail, P2 §4.3)" "no|" "$(H 00-qa files_pane)|$(ids_of 00-qa files_pane_glyph: | xargs)"
  # …and nothing drawn where a rail would be: the left 48 epx of an EMPTY folder's page, under the sort line, is black.
  files_at "$DEEP/deeper"; D 20-empty; S 20-empty
  RAIL="$(python3 - "$ROW_DIR/20-empty.png" "$(python3 -c "print(int(($LB + 40) * 3))")" "$(python3 -c "print(int($AT * 3))")" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); px = im.load()
m = max(max(px[x, y]) for y in range(int(sys.argv[2]), int(sys.argv[3]), 2) for x in range(0, 144, 2))
print(m)
PY
)"
  assert_le "pane: closed — the left 48 epx of an empty folder's page is black (brightest channel)" 4 "$RAIL"
  page_qa
  T 00-qa files_menu 1.5; D 21-pane; S 21-pane; nodes 21-pane > "$ROW_DIR/21-pane.nodes"
  assert_near "pane: files_pane is 256 ± 1 epx wide (F 1.3.5)" 256 "$(edim 21-pane files_pane w)" 1
  assert_near "pane: …from the left edge" 0 "$(edim 21-pane files_pane l)" 0.1
  assert_rgb "pane: fill (23,23,23) ± 4 (F 1.3.6)" "23,23,23" "$(px 21-pane 600 1500)" 4
  NOSCRIM="$(python3 - "$ROW_DIR/00-qa.png" "$ROW_DIR/21-pane.png" "$(python3 -c "print(int($ST * 3))")" "$(python3 -c "print(int(($AT + 48) * 3))")" <<'PY'
import sys
from PIL import Image, ImageChops
a = Image.open(sys.argv[1]).convert("RGB"); b = Image.open(sys.argv[2]).convert("RGB")
box = (771, int(sys.argv[3]), 1080, int(sys.argv[4]))      # right of the pane's 256 epx (768 px), status bar to nav bar
d = ImageChops.difference(a.crop(box), b.crop(box))
print(max(mx for _, mx in d.getextrema()))
PY
)"
  assert_le "pane: no scrim — the pixels right of it are unchanged ± 2 against the screencap before opening" 2 "$NOSCRIM"
  assert_eq "pane: order Recent / This Device / the volume / Recycle Bin" "recent device $U bin" "$(ids_of 21-pane files_pane: | xargs)"
  assert_near "pane: the first row's top = 72 + (STATUS_EPX − 24) ± 2 (F 1.3.7; Decisions call 1)" "$(python3 -c "print(72 + $ST - 24)")" "$(edim 21-pane files_pane:recent t)" 2
  for r in recent device "$U" bin; do
    assert_near "pane: row $r is 48 ± 1 epx (F 1.3.8)" 48 "$(edim 21-pane "files_pane:$r" h)" 1
    I="$(node_ink 21-pane 21-pane "files_pane_glyph:$r")"
    assert_near "pane: row $r glyph centre x 24 ± 1 (F 1.3.9; its ink)" 24 "$(ink_dim "$I" cx)" 1
    assert_near "pane: row $r label left 60 ± 1 (F 1.3.10)" 60 "$(edim 21-pane "files_pane_label:$r" l)" 1
  done
  assert_near "pane: the rows follow each other (device's top = recent's bottom)" "$(edim 21-pane files_pane:recent b)" "$(edim 21-pane files_pane:device t)" 0.1
  assert_eq "pane: the current row is This Device (QA-Files is on it)" "true" "$(attr 21-pane files_pane:device selected)"
  set -- $(B 21-pane files_pane:device)
  assert_rgb "pane: the current row = 0.6 · accent + 0.4 · (23,23,23) ± 4 (F 1.3.11)" "$(mix_rgb "$ACC" 23,23,23 0.6)" "$(px 21-pane 690 $(( ($2 + $4) / 2 )))" 4
  T 21-pane files_pane_menu 1
  c6; ensure_start
  files_open; D 22-cold; S 22-cold
  assert_eq "pane: on a cold start it is open with Recent current (E1's)" "yes true Recent" "$(H 22-cold files_pane) $(attr 22-cold files_pane:recent selected) $(X 22-cold files_crumb:0)"
fi

# ------------------------------------------------------------------------------------------------- the sort line (1.4)
if leg sort; then
  page_qa
  assert_eq "sort: the line reads" "Sort by: Name" "$(X 00-qa files_sort)"
  assert_near "sort: \"Sort by:\" left 12 ± 1 (F 1.4.3; the node)" 12 "$(edim 00-qa files_sort l)" 1
  I="$(node_ink 00-qa 00-qa files_sort 0 24)"             # the "S"
  record "sort: the S's ink (px box, colour); its left in epx (F: about 13.7)" "$I  left $(ink_dim "$I" l)"
  assert_near "sort: …the S's ink left within 12–14.7 (12 ± 1 with the side bearing F measured: 13.7 ± 1)" 13.7 "$(ink_dim "$I" l)" 1.7
  assert_near "sort: cap top 16 ± 1 below the bar (F 1.4.3)" 16 "$(sub2 "$(ink_dim "$I" t)" "$LB")" 1
  I="$(node_ink 00-qa 00-qa files_sort 0 150)"            # "Sort by:" — the first 50 epx
  assert_rgb "sort: \"Sort by:\" (160,160,160) ± 4 (F 1.4.1)" "160,160,160" "$(ink_rgb "$I")" 4
  set -- $(B 00-qa files_sort)
  I="$(ink 00-qa $(( $3 - 90 )) "$2" "$3" "$4")"          # the value: the last 30 epx
  assert_rgb "sort: the value white" "255,255,255" "$(ink_rgb "$I")" 4
  assert_eq "sort: a ChevronDown node after it (files_sort_chevron, right of the text)" "yes" "$(python3 -c "import sys; print('yes' if sys.argv[1] and float(sys.argv[1]) >= float(sys.argv[2]) else 'no')" "$(edim 00-qa files_sort_chevron l)" "$(edim 00-qa files_sort r)")"
  record "sort: the chevron's glyph / its ink colour (F: (204,204,204))" "$(X 00-qa files_sort_chevron | python3 -c 'import sys; print("U+%04X" % ord(sys.stdin.read().strip()[:1] or " "))') / $(ink_rgb "$(node_ink 00-qa 00-qa files_sort_chevron)")"
fi

# ------------------------------------------------------------------------------------------------- rows (1.5)
if leg rows; then
  page_qa
  GEO="$(python3 - "$ROW_DIR/00-qa.xml" <<'PY'
import html, re, sys
x = open(sys.argv[1], encoding='utf-8', errors='replace').read()
icons, names = [], []
for m in re.finditer(r'<node[^>]*>', x):
    s = m.group(0); rid = html.unescape(re.search(r'resource-id="([^"]*)"', s).group(1))
    b = [int(v) for v in re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s).groups()]
    if rid.startswith("files_icon:") and b[3] - b[1] == 120: icons.append(b)
    if rid.startswith("files_row:"): names.append(b)
p = sorted(set((b[1] - a[1]) / 3 for a, b in zip(icons, icons[1:])))
print(len(icons), ",".join("%.1f" % v for v in p), ",".join(sorted(set("%.1f" % (b[0] / 3) for b in icons))), ",".join(sorted(set("%.1f" % (b[0] / 3) for b in names))))
PY
)"
  set -- $GEO
  record "rows: whole icons on the page / pitches / icon lefts / name lefts (epx)" "$1 / $2 / $3 / $4"
  assert_ge "rows: at least six whole rows are measured" 6 "$1"
  for v in $(printf '%s' "$2" | tr ',' ' '); do assert_near "rows: pitch 64 ± 1 (F 1.5.2)" 64 "$v" 1; done
  for v in $(printf '%s' "$3" | tr ',' ' '); do assert_near "rows: icon left 20 ± 2 (F 1.5.4)" 20 "$v" 2; done
  for v in $(printf '%s' "$4" | tr ',' ' '); do assert_near "rows: name left 72 ± 2 (F 1.5.4)" 72 "$v" 2; done
  assert_rgb "rows: the detail line (165,165,165) ± 4 (F 1.5.7; files_detail:a.txt's ink)" "165,165,165" "$(ink_rgb "$(node_ink 00-qa 00-qa files_detail:a.txt)")" 4
  assert_rgb "rows: the name white (files_row:a.txt's ink)" "255,255,255" "$(ink_rgb "$(node_ink 00-qa 00-qa files_row:a.txt)")" 4
fi

# ------------------------------------------------------------------------------------------------- icons view (1.6)
view_icons() { # folder dump first-name — the folder in the icons view (the view button tapped when it is in the list view)
  files_at "$1"; D "$2"
  if [ "$(is_icons "$2" "$3")" = no ]; then T "$2" files_bar:view 1.8; D "$2"; fi
  S "$2"; nodes "$2" > "$ROW_DIR/$2.nodes"
}
view_list() { # folder dump first-name
  files_at "$1"; D "$2"
  if [ "$(is_icons "$2" "$3")" = yes ]; then T "$2" files_bar:view 1.8; D "$2"; fi
}
if leg icons; then
  page_qa
  set -- $(B 00-qa files_bar:view)
  LIST_GLYPH_BOX="$1 $2 $3 $4"
  python3 - "$ROW_DIR/00-qa.png" "$ROW_DIR/30-view-list-mode.png" $LIST_GLYPH_BOX <<'PY'
import sys
from PIL import Image
Image.open(sys.argv[1]).crop(tuple(int(v) for v in sys.argv[3:7])).save(sys.argv[2])
PY
  view_icons "$DEEP" 31-icons deeper
  assert_eq "icons: the folder of type icons is in the icons view (four entries)" "deeper $LONG x.txt y.bin" "$(ids_of 31-icons files_icon: | xargs)"
  CX="$(for n in deeper "$LONG" x.txt; do edim 31-icons "files_icon:$n" cx; done | xargs)"
  TOPS="$(for n in deeper "$LONG" x.txt y.bin; do edim 31-icons "files_icon:$n" t; done | xargs)"
  record "icons: the first three icons' centres x / the four icons' tops (epx)" "$CX / $TOPS"
  set -- $CX
  assert_near "icons: three per row — column 1 centre W/6 = 60 ± 2 (F 1.6.2)" 60 "$1" 2
  assert_near "icons: …column 2 centre 180 ± 2" 180 "$2" 2
  assert_near "icons: …column 3 centre 300 ± 2" 300 "$3" 2
  set -- $TOPS
  assert_eq "icons: three icons share the first row, the fourth starts the second" "yes" "$([ "$1" = "$2" ] && [ "$2" = "$3" ] && python3 -c "import sys; sys.exit(0 if float(sys.argv[2]) > float(sys.argv[1]) + 60 else 1)" "$1" "$4" && echo yes || echo no)"
  assert_near "icons: the fourth icon is back in column 1" 60 "$(edim 31-icons files_icon:y.bin cx)" 2
  for n in deeper x.txt "$LONG"; do
    I="$(node_ink 31-icons 31-icons "files_row:$n")"
    assert_near "icons: the name is centred under its icon ($n: its ink's centre x = the icon's ± 2)" "$(edim 31-icons "files_icon:$n" cx)" "$(ink_dim "$I" cx)" 2
    assert_ge "icons: …and below it ($n)" "$(edim 31-icons "files_icon:$n" b)" "$(ink_dim "$I" t)"
  done
  # The long name: two lines of ink in its label box; the pitch between the two lines' tops.
  set -- $(B 31-icons "files_row:$LONG")
  LINES="$(python3 - "$ROW_DIR/31-icons.png" "$1" "$2" "$3" "$4" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); px = im.load()
l, t, r, b = [int(v) for v in sys.argv[2:6]]
rows = [any(max(px[x, y]) > 100 for x in range(l, r)) for y in range(t, b)]
bands, start = [], None
for i, on in enumerate(rows + [False]):
    if on and start is None: start = i
    if not on and start is not None:
        if i - start >= 6: bands.append((t + start, t + i))
        start = None
# Each band's horizontal centre.
out = []
for bt, bb in bands:
    xs = [x for x in range(l, r) if any(max(px[x, y]) > 100 for y in range(bt, bb))]
    out.append("%d:%d:%.1f" % (bt, bb, (xs[0] + xs[-1] + 1) / 2 / 3))
print(len(bands), " ".join(out))
PY
)"
  record "icons: the long name's ink bands (top:bottom px : centre x epx) / its label box (epx)" "$LINES / $(ebox 31-icons "files_row:$LONG")"
  set -- $LINES
  assert_eq "icons: a long name wraps to a second line (two bands of ink)" "2" "$1"
  if [ "$1" = 2 ]; then
    T1="${2%%:*}"; T2="${3%%:*}"
    assert_near "icons: line pitch 20 ± 2 epx (P2 §1; the second line's ascender line − the first's)" 20 "$(python3 -c "print('%.1f' % (($T2 - $T1) / 3))")" 2
    assert_near "icons: the second line is centred too (its ink's centre x = the icon's ± 2)" "$(edim 31-icons "files_icon:$LONG" cx)" "${3##*:}" 2
  fi
  # The app bar's third slot in the icons view: the list glyph.
  set -- $LIST_GLYPH_BOX
  python3 - "$ROW_DIR/31-icons.png" "$ROW_DIR/30-view-icons-mode.png" "$@" <<'PY'
import sys
from PIL import Image
Image.open(sys.argv[1]).crop(tuple(int(v) for v in sys.argv[3:7])).save(sys.argv[2])
PY
  GLYPH_DIFF="$(python3 - "$ROW_DIR/30-view-list-mode.png" "$ROW_DIR/30-view-icons-mode.png" <<'PY'
import sys
from PIL import Image, ImageChops
b = ImageChops.difference(Image.open(sys.argv[1]).convert("RGB"), Image.open(sys.argv[2]).convert("RGB")).tobytes()
print(sum(1 for i in range(0, len(b), 3) if max(b[i:i + 3]) > 60))
PY
)"
  assert_ge "icons: the third slot's glyph is another one in the icons view (pixels that differ from the list view's)" 20 "$GLYPH_DIFF"
  T 31-icons files_bar:more 1.2; D 32-more-icons
  VL_ICONS="$(X 32-more-icons files_bar:view_label)"; back 1
  record "icons: the third slot's label in the icons view (the list view's reads Icons)" "$VL_ICONS"
  assert_eq "icons: …and it names the list view (its label)" "List" "$VL_ICONS"
  # A folder of thumbnails: the row's own recent/ (four PNGs: two rows), then DCIM/Camera (the row's three photos).
  view_icons "$QF/recent" 33-thumbs never.png
  ORDER="$(ids_of 33-thumbs files_icon: | xargs)"
  set -- $ORDER
  record "icons: recent/ in the icons view — the icons, their boxes (epx)" "$ORDER / $(for n in "$@"; do printf '[%s] ' "$(ebox 33-thumbs "files_icon:$n")"; done)"
  assert_eq "icons: recent/ shows its four thumbnails" "4" "$#"
  assert_near "icons: in a folder of thumbnails the row pitch is 172 ± 8 epx (P2 §4.6; the fourth icon's top − the first's)" 172 "$(sub2 "$(edim 33-thumbs "files_icon:$4" t)" "$(edim 33-thumbs "files_icon:$1" t)")" 8
  assert_near "icons: …three columns at W/3 (the second thumbnail's centre − the first's = 120 ± 2)" 120 "$(sub2 "$(edim 33-thumbs "files_icon:$2" cx)" "$(edim 33-thumbs "files_icon:$1" cx)")" 2
  record "icons: a thumbnail's box (epx; P2: about 96 square)" "$(edim 33-thumbs "files_icon:$1" w) × $(edim 33-thumbs "files_icon:$1" h)"
  view_icons "$SD/DCIM/Camera" 34-camera qa-photo-0.png
  assert_eq "icons: DCIM/Camera shows the row's three photos in one row of three" "qa-photo-0.png qa-photo-1.png qa-photo-2.png|yes" "$(ids_of 34-camera files_icon: | xargs)|$([ "$(edim 34-camera files_icon:qa-photo-0.png t)" = "$(edim 34-camera files_icon:qa-photo-2.png t)" ] && echo yes || echo no)"
  record "icons: DCIM/Camera holds one row (three photos from files_up media), so the 172 pitch is read in recent/" "tops $(for n in 0 1 2; do edim 34-camera "files_icon:qa-photo-$n.png" t; done | xargs)"
  # Back to the list view everywhere this leg changed it.
  view_list "$SD/DCIM/Camera" 35-back qa-photo-0.png
  view_list "$QF/recent" 35-back never.png
  view_list "$DEEP" 35-back deeper
  page_qa
  assert_eq "icons: the list view is back in QA-Files" "no" "$(is_icons 00-qa sub)"
fi

# ------------------------------------------------------------------------------------------------- the app bar (1.7)
if leg appbar; then
  page_qa
  set -- 286 218 150 82 24
  for b in select new_folder view search more; do
    I="$(node_ink 00-qa 00-qa "files_bar:$b")"
    assert_near "appbar: $b glyph centre $1 ± 1 epx from the right (F 1.7.2; its ink)" "$1" "$(from_right "$(ink_dim "$I" cx)")" 1
    if [ "$b" != more ]; then assert_near "appbar: $b glyph centre 24 ± 1 below the bar's top (F 1.7.3)" 24 "$(sub2 "$(ink_dim "$I" cy)" "$AT")" 1
    else record "appbar: the More dots' centre below the bar's top (epx; the dots sit high in a closed bar)" "$(sub2 "$(ink_dim "$I" cy)" "$AT")"; fi
    shift
  done
  T 00-qa files_bar:more 1.5; D 40-more; S 40-more; nodes 40-more > "$ROW_DIR/40-more.nodes"
  assert_near "appbar: ••• expanded = 60 ± 2 epx (F 1.7.4)" 60 "$(edim 40-more files_appbar h)" 2
  assert_eq "appbar: …with labels" "Select|New folder|Icons|Search" "$(X 40-more files_bar:select_label)|$(X 40-more files_bar:new_folder_label)|$(X 40-more files_bar:view_label)|$(X 40-more files_bar:search_label)"
  OVER="$(ids_of 40-more files_more: | xargs)"
  record "appbar: the overflow's lines" "$OVER"
  assert_eq "appbar: overflow Refresh / Select all / Clear selection / Properties in that order (the first four)" "refresh select_all clear properties" "$(printf '%s\n' $OVER | head -4 | xargs)"
  assert_eq "appbar: …then the build's fifth line, Settings (Decisions 2026-10-06, call 4)" "settings" "$(printf '%s\n' $OVER | sed -n 5p)"
  TEXTS="$(for i in refresh select_all clear properties; do set -- $(B 40-more "files_more:$i"); python3 - "$ROW_DIR/40-more.xml" "$2" "$4" <<'PY'
import html, re, sys
x = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for m in re.finditer(r'<node[^>]*>', x):
    s = m.group(0); t = re.search(r' text="([^"]*)"', s); b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s).groups()
    if t and t.group(1) and int(b[1]) >= int(sys.argv[2]) and int(b[3]) <= int(sys.argv[3]): print(html.unescape(t.group(1)), end="|"); break
PY
done)"
  assert_eq "appbar: …their texts" "Refresh|Select all|Clear selection|Properties|" "$TEXTS"
  assert_eq "appbar: Clear selection is disabled with nothing selected" "false" "$(enabled 40-more files_more:clear)"
  assert_rgb "appbar: …and dimmed (137,137,137) ± 4 (F 1.7.7; its text's ink)" "137,137,137" "$(ink_rgb "$(node_ink 40-more 40-more files_more:clear)")" 4
  assert_rgb "appbar: a live line's text is white (Refresh)" "255,255,255" "$(ink_rgb "$(node_ink 40-more 40-more files_more:refresh)")" 4
  back 1
fi

# ------------------------------------------------------------------------------------------------- selection (1.8)
if leg selection; then
  page_qa
  T 00-qa files_bar:select 1.2; D 50-sel0; S 50-sel0; nodes 50-sel0 > "$ROW_DIR/50-sel0.nodes"
  assert_eq "selection: Select → the sort line reads" "0 items selected" "$(X 50-sel0 files_sort)"
  assert_eq "selection: the bar is Delete / Move to / Copy to / Share, left to right (the files_sel: ids)" "delete move copy share" "$(ids_of 50-sel0 files_sel: | xargs)"
  assert_eq "selection: all four are disabled with nothing selected" "false false false false" "$(for b in delete move copy share; do enabled 50-sel0 "files_sel:$b"; done | xargs)"
  for b in delete move copy share; do
    assert_rgb "selection: files_sel:$b is dim (137,137,137) ± 4 (its glyph's ink)" "137,137,137" "$(ink_rgb "$(node_ink 50-sel0 50-sel0 "files_sel:$b")")" 4
  done
  assert_near "selection: the checkbox is 20 ± 2 epx wide (P2 §1)" 20 "$(edim 50-sel0 files_check:a.txt w)" 2
  assert_near "selection: …and 20 ± 2 tall" 20 "$(edim 50-sel0 files_check:a.txt h)" 2
  assert_near "selection: …centre x 22 ± 2" 22 "$(edim 50-sel0 files_check:a.txt cx)" 2
  assert_near "selection: …vertically centred on the icon (± 1)" "$(edim 50-sel0 files_icon:a.txt cy)" "$(edim 50-sel0 files_check:a.txt cy)" 1
  assert_near "selection: icon left 52 ± 2 (the 32-epx shift)" 52 "$(edim 50-sel0 files_icon:a.txt l)" 2
  assert_near "selection: name left 104 ± 2" 104 "$(edim 50-sel0 files_row:a.txt l)" 2
  T 50-sel0 files_row:a.txt 0.8; D 51-sel1; S 51-sel1
  assert_eq "selection: one tap → exactly (the singular)" "1 item selected" "$(X 51-sel1 files_sort)"
  assert_eq "selection: …a.txt's checkbox is checked" "true" "$(attr 51-sel1 files_check:a.txt checked)"
  assert_eq "selection: …the four buttons are live" "true true true true" "$(for b in delete move copy share; do enabled 51-sel1 "files_sel:$b"; done | xargs)"
  T 51-sel1 files_row:b.bin 0.8; D 52-sel2; S 52-sel2
  assert_eq "selection: two → " "2 items selected" "$(X 52-sel2 files_sort)"
  # A selected row's fill: the row's box is the icon's 40 epx with 12 above and below; sampled at both edges and mid-row,
  # 1 epx inside its top and its bottom, and at its centre line.
  for n in a.txt b.bin; do
    set -- $(B 52-sel2 "files_icon:$n"); RT=$(( $2 - 36 )); RB=$(( $2 + 156 ))
    for y in $(( RT + 3 )) $(( (RT + RB) / 2 )) $(( RB - 3 )); do
      for x in 2 1000 1077; do assert_rgb "selection: $n's row is the accent ± 4 at x $x px, y $y px (full width, full height)" "$ACC" "$(px 52-sel2 "$x" "$y")" 4; done
    done
  done
  set -- $(B 52-sel2 files_icon:img-0.png)
  assert_rgb "selection: an unselected row keeps the black page (img-0.png's row, its left edge)" "0,0,0" "$(px 52-sel2 2 $(( $2 + 60 )))" 4
  T 52-sel2 files_bar:more 1.2; D 53-sel-more
  assert_eq "selection: the bar reads Delete / Move to / Copy to / Share (the expanded bar's labels)" "Delete|Move to|Copy to|Share" "$(X 53-sel-more files_sel:delete_label)|$(X 53-sel-more files_sel:move_label)|$(X 53-sel-more files_sel:copy_label)|$(X 53-sel-more files_sel:share_label)"
  record "selection: the overflow in selection mode" "$(ids_of 53-sel-more files_more: | xargs)"
  back 1
  D 54-sel2b
  assert_eq "selection: still in selection mode after the overflow closed" "2 items selected" "$(X 54-sel2b files_sort)"
  T 54-sel2b files_row:sub 0.8; D 55-sel3; S 55-sel3
  assert_eq "selection: with sub in the selection" "3 items selected" "$(X 55-sel3 files_sort)"
  assert_eq "selection: …Share is disabled, the other three live" "true true true false" "$(for b in delete move copy share; do enabled 55-sel3 "files_sel:$b"; done | xargs)"
  assert_rgb "selection: …and dim (137,137,137) ± 4" "137,137,137" "$(ink_rgb "$(node_ink 55-sel3 55-sel3 files_sel:share)")" 4
  back 1.2; D 56-left
  assert_eq "selection: Back leaves selection mode — the sort line returns, no checkbox, the bar's own buttons" "Sort by: Name||yes" "$(X 56-left files_sort)|$(ids_of 56-left files_check: | xargs)|$(H 56-left files_bar:select)"
  assert_eq "selection: …and FilesActivity is still on top" "$FILES_ACTIVITY" "$(top_activity)"
fi

# ------------------------------------------------------------------------------------------------- the picker (P2 §1)
if leg picker; then
  page_qa
  LS0="$(q "ls -a $QA_FILES $QA_FILES/sub" | xargs)"
  MARK="$(ring_mark)"
  T 00-qa files_bar:select 1.2; D 60-a; T 60-a files_row:a.txt 0.8; D 60-b; T 60-b files_sel:move 1.5
  D 61-picker; S 61-picker; nodes 61-picker > "$ROW_DIR/61-picker.nodes"
  assert_eq "picker: Move to → files_pick_title reads" "Choose a folder" "$(X 61-picker files_pick_title)"
  assert_near "picker: …at the sort line's left (12 ± 2)" 12 "$(edim 61-picker files_pick_title l)" 2
  assert_eq "picker: no files_sort node, no checkbox node on any row" "no|" "$(H 61-picker files_sort)|$(ids_of 61-picker files_check: | xargs)"
  assert_eq "picker: files_menu, the breadcrumb and files_up are present" "yes yes yes" "$(H 61-picker files_menu) $(H 61-picker files_crumb:0) $(H 61-picker files_up)"
  set -- 150 82 24
  for b in files_pick_ok files_pick_cancel files_bar:more; do
    assert_near "picker: $b at $1 ± 2 epx from the right (its ink's centre)" "$1" "$(from_right "$(ink_dim "$(node_ink 61-picker 61-picker "$b")" cx)")" 2
    shift
  done
  assert_eq "picker: the bar holds nothing else (no files_bar: button but More, no files_sel:)" "more|" "$(ids_of 61-picker files_bar: | xargs)|$(ids_of 61-picker files_sel: | xargs)"
  T 61-picker files_menu 1.2; D 62-pane
  assert_eq "picker: the pane opens inside it (its rows)" "recent device $U bin" "$(ids_of 62-pane files_pane: | xargs)"
  T 62-pane files_pane_menu 1; D 63-closed
  assert_eq "picker: …and closes; the picker is still up" "no Choose a folder" "$(H 63-closed files_pane) $(X 63-closed files_pick_title)"
  T 63-closed files_row:sub 1.5; D 64-in-sub
  assert_eq "picker: a folder tapped inside the picker opens in it" "sub|Choose a folder" "$(last_crumb 64-in-sub)|$(X 64-in-sub files_pick_title)"
  back 1.5; D 65-back1
  assert_eq "picker: Back inside a subfolder → the picker's previous folder (still the picker)" "QA-Files|Choose a folder" "$(last_crumb 65-back1)|$(X 65-back1 files_pick_title)"
  back 1.5; D 66-back2
  assert_eq "picker: Back at its first folder closes the picker (no files_pick_title), Files still on top" "no|$FILES_ACTIVITY" "$(H 66-back2 files_pick_title)|$(top_activity)"
  record "picker: after it closed — the sort line / the crumb" "$(X 66-back2 files_sort) / $(last_crumb 66-back2)"
  [ "$(X 66-back2 files_sort | grep -c 'selected')" = 1 ] && back 1.2
  assert_eq "picker: nothing moved (ls -a of QA-Files and sub as before)" "$LS0" "$(q "ls -a $QA_FILES $QA_FILES/sub" | xargs)"
  SL="$(ring_since "$MARK")"
  absent_in "picker: …and no [files] move line" "[files] move " "$SL"
fi

# ------------------------------------------------------------------------------------------------- dialogs (Y4)
dialog_shape() { # dump shot label — the structure every dialog shares
  assert_eq "dialogs: $3 — files_dialog and its title are shown" "yes yes" "$(H "$1" files_dialog) $(H "$1" files_dialog_title)"
  assert_near "dialogs: $3 — anchored at the top (its top = the status bar's bottom)" "$ST" "$(edim "$1" files_dialog t)" 0.5
  assert_near "dialogs: $3 — full width" 360 "$(edim "$1" files_dialog w)" 0.5
  set -- "$1" "$2" "$3" $(B "$1" files_dialog)
  assert_rgb "dialogs: $3 — fill (74,74,74) ± 4 (its top-right corner, 5 epx in)" "74,74,74" "$(px "$2" $(( $6 - 15 )) $(( $5 + 15 )))" 4
}
side_by_side() { # dump label id…
  local d="$1" l="$2" tops="" lefts="" i; shift 2
  for i in "$@"; do tops="$tops $(edim "$d" "files_dialog:$i" t)"; lefts="$lefts $(edim "$d" "files_dialog:$i" l)"; done
  assert_eq "dialogs: $l — $# answers ($*) on one line, left to right" "1 yes" "$(printf '%s\n' $tops | sort -u | grep -c .) $(python3 -c "import sys; v = [float(x) for x in sys.argv[1:]]; print('yes' if len(v) == $# and all(a < b for a, b in zip(v, v[1:])) else 'no')" $lefts)"
}
if leg dialogs; then
  MD5A="$(fx_md5 a.txt)"
  page_qa
  hold 00-qa files_row:a.txt; D 70-hold; T 70-hold files_hold:rename 1.2; D 71-rename; S 71-rename
  dialog_shape 71-rename 71-rename rename
  record "dialogs: rename — title / the input's text / the answers" "$(X 71-rename files_dialog_title) / $(X 71-rename files_dialog_input) / $(ids_of 71-rename files_dialog: | xargs)"
  assert_eq "dialogs: rename — exactly two answers" "ok cancel" "$(ids_of 71-rename files_dialog: | xargs)"
  side_by_side 71-rename rename ok cancel
  T 71-rename files_dialog:cancel 1.2
  page_qa
  T 00-qa files_bar:new_folder 1.2; D 72-newfolder; S 72-newfolder
  dialog_shape 72-newfolder 72-newfolder "new folder"
  record "dialogs: new folder — title / the answers" "$(X 72-newfolder files_dialog_title) / $(ids_of 72-newfolder files_dialog: | xargs)"
  assert_eq "dialogs: new folder — exactly two answers" "ok cancel" "$(ids_of 72-newfolder files_dialog: | xargs)"
  side_by_side 72-newfolder "new folder" ok cancel
  T 72-newfolder files_dialog:cancel 1.2
  page_qa
  hold 00-qa files_row:a.txt; D 73-hold; T 73-hold files_hold:delete 1.2; D 74-delete; S 74-delete
  dialog_shape 74-delete 74-delete "delete confirmation"
  record "dialogs: delete — title / body / the answers" "$(X 74-delete files_dialog_title) / $(X 74-delete files_dialog_body) / $(ids_of 74-delete files_dialog: | xargs)"
  assert_eq "dialogs: delete — exactly two answers" "ok cancel" "$(ids_of 74-delete files_dialog: | xargs)"
  side_by_side 74-delete delete ok cancel
  T 74-delete files_dialog:cancel 1.2
  assert_eq "dialogs: delete cancelled — a.txt is still there with its md5" "$MD5A" "$(q "md5sum $QA_FILES/a.txt" | cut -d' ' -f1)"
  # The conflict: Copy to sub for b.bin, where the row's own sub/b.bin already is.
  page_qa
  T 00-qa files_bar:select 1.2; D 75-a; T 75-a files_row:b.bin 0.8; D 75-b; T 75-b files_sel:copy 1.5
  D 75-c; T 75-c files_row:sub 1.5; D 75-d; T 75-d files_pick_ok 1.5
  D 76-conflict; S 76-conflict
  dialog_shape 76-conflict 76-conflict conflict
  record "dialogs: conflict — title / body / the answers" "$(X 76-conflict files_dialog_title) / $(X 76-conflict files_dialog_body) / $(ids_of 76-conflict files_dialog: | xargs)"
  assert_eq "dialogs: conflict — three answers" "replace keep_both skip" "$(ids_of 76-conflict files_dialog: | xargs)"
  side_by_side 76-conflict conflict replace keep_both skip
  T 76-conflict files_dialog:skip 1.5
  assert_eq "dialogs: conflict skipped — sub still holds exactly b.bin and deep" "b.bin deep" "$(q "ls $QA_FILES/sub" | xargs)"
  D 77-after; [ "$(X 77-after files_sort | grep -c 'selected')" = 1 ] && back 1.2
fi

# ------------------------------------------------------------------------------------------------- the hold menu (1.9)
if leg hold; then
  page_qa
  hold 00-qa files_row:a.txt; D 80-hold-file; S 80-hold-file
  assert_eq "hold: on a file — Delete / Move to / Copy to / Share / Rename / Properties" "delete move copy share rename properties" "$(ids_of 80-hold-file files_hold: | xargs)"
  TOPS="$(for i in delete move copy share rename properties; do edim 80-hold-file "files_hold:$i" t; done | xargs)"
  record "hold: the items' tops (epx) / the box" "$TOPS / $(ebox 80-hold-file files_hold)"
  for p in $(python3 -c "import sys; v = [float(x) for x in sys.argv[1:]]; print(' '.join('%.1f' % (b - a) for a, b in zip(v, v[1:])))" $TOPS); do assert_near "hold: item pitch 44 ± 2 epx (F 1.9.4)" 44 "$p" 2; done
  assert_near "hold: width 240.5 ± 2 (F 1.9.3)" 240.5 "$(edim 80-hold-file files_hold w)" 2
  back 1
  page_qa
  hold 00-qa files_row:sub; D 81-hold-folder; S 81-hold-folder
  assert_eq "hold: on sub — the same without Share" "delete move copy rename properties" "$(ids_of 81-hold-folder files_hold: | xargs)"
  assert_near "hold: …the same width" 240.5 "$(edim 81-hold-folder files_hold w)" 2
  back 1
fi

# ------------------------------------------------------------------------------------------------- Properties (1.10)
if leg properties; then
  files_at "$QF"
  scroll_to_node "$ROW_DIR/90-list.xml" files_row:qa-steps.mp4 6 >/dev/null
  hold 90-list files_row:qa-steps.mp4; D 91-hold; T 91-hold files_hold:properties 2; D 92-props; S 92-props; nodes 92-props > "$ROW_DIR/92-props.nodes"
  assert_eq "properties: a page, not a dialog — files_properties shown, no app bar node, no files_dialog" "yes no no" "$(H 92-props files_properties) $(H 92-props files_appbar) $(H 92-props files_dialog)"
  assert_eq "properties: the last files_crumb: segment is the item's name" "qa-steps.mp4" "$(last_crumb 92-props)"
  PROPS="$(python3 - "$ROW_DIR/92-props.xml" <<'PY'
import html, re, sys
x = open(sys.argv[1], encoding='utf-8', errors='replace').read()
lab, val, sec = {}, {}, []
for m in re.finditer(r'<node[^>]*>', x):
    s = m.group(0); rid = html.unescape(re.search(r'resource-id="([^"]*)"', s).group(1)); t = re.search(r' text="([^"]*)"', s); t = html.unescape(t.group(1)) if t else ""
    if rid.startswith("files_prop_label:"): lab[rid[17:]] = t
    if rid.startswith("files_prop_value:"): val[rid[17:]] = t
    if rid.startswith("files_prop_section:"): sec.append(t or rid[19:])
print("|".join("%s=%s" % (lab[k], val.get(k, "")) for k in lab))
print("|".join(sec))
PY
)"
  ROWS="$(printf '%s\n' "$PROPS" | sed -n 1p)"; SECS="$(printf '%s\n' "$PROPS" | sed -n 2p)"
  record "properties: the rows (label=value)" "$ROWS"
  record "properties: the sections" "$SECS"
  for l in "Date modified:" "File type:" "File size:"; do
    assert_eq "properties: the row \"$l\" has a value" "yes" "$(printf '|%s|' "$ROWS" | grep -qE "\|$l=[^|]+\|" && echo yes || echo no)"
  done
  assert_contains "properties: File size is the table's (109 KB)" "File size:=109 KB" "$ROWS"
  assert_contains "properties: Date modified is the table's (1/10/2026)" "Date modified:=1/10/2026" "$ROWS"
  assert_contains "properties: for qa-steps.mp4 a Video section" "|Video|" "|$SECS|"
  back 1.2; D 93-back
  assert_eq "properties: Back returns to the folder" "QA-Files" "$(last_crumb 93-back)"
fi

# ------------------------------------------------------------------------------------------------- Recent (P2 §1 / §4.8)
if leg recent; then
  c6; ensure_start
  files_open --es page recent; D 95-recent-empty; S 95-recent-empty; nodes 95-recent-empty > "$ROW_DIR/95-recent-empty.nodes"
  assert_eq "recent: precondition — the list is empty (no row; the empty line shown)" "|yes" "$(ids_of 95-recent-empty files_recent_row: | xargs)|$(H 95-recent-empty files_recent_empty)"
  assert_eq "recent: no files_sort node" "no" "$(H 95-recent-empty files_sort)"
  assert_eq "recent: the bar is Select · Icons · Search · ••• with no New folder" "select view search more" "$(ids_of 95-recent-empty files_bar: | xargs)"
  assert_eq "recent: Select, Icons and Search are disabled when the list is empty" "false false false" "$(for b in select view search; do enabled 95-recent-empty "files_bar:$b"; done | xargs)"
  record "recent: their glyphs' ink when dim / More's" "$(for b in select view search; do ink_rgb "$(node_ink 95-recent-empty 95-recent-empty "files_bar:$b")"; done | xargs) / $(ink_rgb "$(node_ink 95-recent-empty 95-recent-empty files_bar:more)")"
  I="$(node_ink 95-recent-empty 95-recent-empty files_recent_empty)"
  record "recent: the empty line (E14 asserts it) — text / ink left, cap top below the status bar's top (epx) / colour" "$(X 95-recent-empty files_recent_empty) / $(ink_dim "$I" l), $(ink_dim "$I" t) / $(ink_rgb "$I")"
  files_at "$QF/recent"; D 96-folder
  MARK="$(ring_mark)"
  T 96-folder files_row:r1.png 2.5
  assert_eq "recent: the row opens its own r1.png through Files (the viewer on top)" "app.tileshell/.photos.ViewerActivity" "$(top_activity)"
  assert_contains "recent: …[files] recent add" "[files] recent add $QF/recent/r1.png" "$(ring_since "$MARK")"
  back 1.2
  files_open --es page recent; D 97-recent-row; S 97-recent-row
  TODAY="$(adb shell date +%-m/%-d/%Y | tr -d '\r')"
  assert_eq "recent: with a row — files_recent_row:r1.png, and still no files_sort node" "r1.png no" "$(ids_of 97-recent-row files_recent_row: | xargs) $(H 97-recent-row files_sort)"
  assert_eq "recent: the row's files_detail: is a date only (today's, the opened-at date)" "$TODAY" "$(X 97-recent-row files_detail:r1.png)"
  assert_eq "recent: the bar is still Select · Icons · Search · •••, now live" "select view search more|true true true" "$(ids_of 97-recent-row files_bar: | xargs)|$(for b in select view search; do enabled 97-recent-row "files_bar:$b"; done | xargs)"
  adb shell input keyevent KEYCODE_HOME; sleep 1
fi

# ------------------------------------------------------------------------------------------------- progress (P2 §1)
wait_done() { # mark op [seconds]
  local i
  for i in $(seq 1 "${3:-90}"); do ring_since "$1" | grep -qE "\[files\] $2 .*(done|cancelled|failed)" && break; sleep 1; done
  sleep 1.5
}
if leg progress; then
  MD5BIG="$(fx_md5 big.bin)"
  files_at "$QF"
  scroll_to_node "$ROW_DIR/100-list.xml" files_row:big.bin 6 >/dev/null
  S 100-before
  T 100-list files_bar:select 1.2; D 101-a; T 101-a files_row:big.bin 0.8; D 101-b
  assert_eq "progress: big.bin is selected" "1 item selected" "$(X 101-b files_sort)"
  T 101-b files_sel:move 1.5; D 102-picker
  T 102-picker files_menu 1.2; D 103-pane; T 103-pane "files_pane:$U" 1.5; D 104-volume
  assert_eq "progress: the picker is on the public volume's root" "$VLABEL|Choose a folder" "$(X 104-volume files_crumb:0)|$(X 104-volume files_pick_title)"
  MARK="$(ring_mark)"
  T 104-volume files_pick_ok 0.2
  mid_progress move "$MARK" 30 > "$ROW_DIR/105-mid.txt"
  G 105-progress; S 105-progress; nodes 105-progress > "$ROW_DIR/105-progress.nodes"
  SL="$(ring_since "$MARK")"
  assert_contains "progress: the move is paced ([files] qa pace 8388608)" "[files] qa pace $PACE_BPS" "$SL"
  absent_in "progress: …and still running when the dump was taken (no move … done line yet)" "-> /storage/$U done" "$(ring_since "$MARK")"
  assert_eq "progress: files_progress is shown" "yes" "$(H 105-progress files_progress)"
  assert_near "progress: its top = the status bar's bottom ± 2 (P2: directly under the status bar; W10M's 24 + (STATUS_EPX − 24))" "$ST" "$(edim 105-progress files_progress t)" 2
  assert_near "progress: height 66 ± 6" 66 "$(edim 105-progress files_progress h)" 6
  assert_near "progress: inset 21 ± 3 on the left" 21 "$(edim 105-progress files_progress l)" 3
  assert_near "progress: inset 21 ± 3 on the right" 21 "$(from_right "$(edim 105-progress files_progress r)")" 3
  assert_eq "progress: the text" "Moving files…" "$(X 105-progress files_progress_text)"
  assert_near "progress: …centred (its centre x = 180 ± 2)" 180 "$(edim 105-progress files_progress_text cx)" 2
  assert_eq "progress: no percentage in any text of the page" "0" "$(grep -o ' text="[^"]*"' "$ROW_DIR/105-progress.xml" | grep -c '%')"
  assert_eq "progress: no cancel node inside the app (no Cancel text, no id holding cancel)" "0 0" "$(grep -o ' text="[^"]*"' "$ROW_DIR/105-progress.xml" | grep -ci 'cancel') $(grep -o 'resource-id="[^"]*"' "$ROW_DIR/105-progress.xml" | grep -ci 'cancel')"
  P0="$(px 100-before 1020 1500)"; P1="$(px 105-progress 1020 1500)"
  record "progress: a pixel outside the box (x 340, y 500 epx) before the move / during it" "$P0 / $P1"
  assert_ne "progress: that pixel differs from before the move (the wash)" "$P0" "$P1"
  assert_eq "progress: files_wash covers the page" "yes" "$(H 105-progress files_wash)"
  wait_done "$MARK" move 90
  SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' | grep -v ' progress ' > "$ROW_DIR/106-ring.txt"
  assert_contains "progress: [files] move 1 files 209715200 -> /storage/<UUID> done" "[files] move 1 files 209715200 -> /storage/$U done" "$SL"
  D 106-done; S 106-done
  assert_eq "progress: on completion the destination folder is shown (the volume's root, no box left)" "$VLABEL||no no" "$(X 106-done files_crumb:0)|$(X 106-done files_crumb:1)|$(H 106-done files_progress) $(H 106-done files_wash)"
  # big.bin sorts after the volume's own folders, below the fold (the dev run read the first page only): scrolled to.
  scroll_to_node "$ROW_DIR/106-scrolled.xml" files_row:big.bin 6 >/dev/null
  assert_eq "progress: …and it lists big.bin (scrolled to; its detail)" "yes|200 MB" "$(H 106-scrolled files_row:big.bin)|$(X 106-scrolled files_detail:big.bin | cut -d' ' -f1-2)"
  assert_eq "progress: the file moved (gone from QA-Files, on the volume with its md5)" "|$MD5BIG" "$(q "ls $QA_FILES/big.bin 2>/dev/null")|$(q "md5sum /storage/$U/big.bin" | cut -d' ' -f1)"
  # A copy, back to QA-Files: the same box reads "Copying files…".
  [ "$(X 106-done files_sort | grep -c 'selected')" = 1 ] && back 1.2
  files_at "/storage/$U"; D 107-vol
  T 107-vol files_bar:select 1.2; scroll_to_node "$ROW_DIR/107-a.xml" files_row:big.bin 6 >/dev/null; T 107-a files_row:big.bin 0.8; D 107-b
  T 107-b files_sel:copy 1.5; D 108-picker
  T 108-picker files_menu 1.2; D 108-pane; T 108-pane files_pane:device 1.5
  scroll_to_node "$ROW_DIR/108-device.xml" files_row:QA-Files 8 >/dev/null; T 108-device files_row:QA-Files 1.5; D 108-qa
  assert_eq "progress: the copy's picker is in QA-Files" "QA-Files|Choose a folder" "$(last_crumb 108-qa)|$(X 108-qa files_pick_title)"
  MARK="$(ring_mark)"
  T 108-qa files_pick_ok 0.2
  mid_progress copy "$MARK" 30 > "$ROW_DIR/109-mid.txt"
  G 109-progress; S 109-progress
  assert_eq "progress: a copy reads (stand-in, H5)" "Copying files…" "$(X 109-progress files_progress_text)"
  assert_near "progress: …in the same box (top, height)" "$ST" "$(edim 109-progress files_progress t)" 2
  assert_near "progress: …height 66 ± 6" 66 "$(edim 109-progress files_progress h)" 6
  wait_done "$MARK" copy 90
  assert_contains "progress: [files] copy … -> …/QA-Files done" "[files] copy 1 files 209715200 -> $QF done" "$(ring_since "$MARK")"
  assert_eq "progress: the copy is whole (md5)" "$MD5BIG" "$(q "md5sum $QA_FILES/big.bin" | cut -d' ' -f1)"
  D 110-done; [ "$(X 110-done files_sort | grep -c 'selected')" = 1 ] && back 1.2
fi

# ------------------------------------------------------------------------------------------------- motion (Y5; P2 §5)
# The shell's own clock: the LAST `[motion] <name>` line in the slice from a MARK taken just before the action. No
# recorder runs in this leg.
motion() { # label name mark -> MLINE
  local i
  for i in $(seq 1 16); do MLINE="$(motion_line "$(ring_since "$3")" "$2")"; [ -n "$MLINE" ] && break; sleep 0.25; done
  record "motion: $1" "${MLINE:-(no [motion] $2 line)}"
}
mf() { printf '%s\n' "$MLINE" | tr ' ' '\n' | sed -n "s/^$1=//p" | head -1; }
gap_ok() { assert_le "motion: $1 — maxGapMs ≤ 33.4 (C-31)" 33.4 "$(mf maxGapMs)"; }
folder_motion() { # label mark
  motion "$1" files_folder "$2"
  assert_near "motion: $1 — files_folder settles in 300 ± 33 ms" 300 "$(mf settle)" 33
  gap_ok "$1"
}
if leg motion; then
  c6; ensure_start
  files_at "$QF"; sleep 1; D 120-qa
  # One of each first, unmeasured: the process's first folder change carries its own start-up work.
  T 120-qa files_row:sub 1.5; D 120-sub; T 120-sub files_up 1.5; D 120-qa
  MARK="$(ring_mark)"; T 120-qa files_row:sub 1.5; folder_motion "a row tap (QA-Files → sub)" "$MARK"
  D 121-sub
  MARK="$(ring_mark)"; T 121-sub files_up 1.5; folder_motion "↑ (sub → QA-Files)" "$MARK"
  D 122-qa; T 122-qa files_row:sub 1.5; D 122-sub
  MARK="$(ring_mark)"; T 122-sub files_crumb:1 1.5; folder_motion "a breadcrumb segment (sub → QA-Files)" "$MARK"
  D 123-qa
  MARK="$(ring_mark)"; T 123-qa files_bar:view 2; folder_motion "list → icons" "$MARK"
  D 124-icons
  MARK="$(ring_mark)"; T 124-icons files_bar:view 2; folder_motion "icons → list" "$MARK"
  D 125-list
  assert_eq "motion: the list view is back" "no" "$(is_icons 125-list sub)"
  # The pane: open by ≡, close by choosing a row.
  MARK="$(ring_mark)"; T 125-list files_menu 1.5
  motion "the pane opens" files_pane_open "$MARK"
  assert_ge "motion: files_pane_open settles within 250–283 ms (the lower bound)" 250 "$(mf settle)"
  assert_le "motion: files_pane_open settles within 250–283 ms (the upper bound)" 283 "$(mf settle)"
  gap_ok "files_pane_open"
  D 126-pane
  MARK="$(ring_mark)"; T 126-pane files_pane:device 1.5
  SL="$(ring_since "$MARK")"
  motion "the pane closes on a choice" files_pane_close "$MARK"
  assert_eq "motion: files_pane_close is 0 ms (settle), one frame" "0 1" "$(mf settle) $(mf frames)"
  gap_ok "files_pane_close"
  ORDER="$(printf '%s\n' "$SL" | grep -oE '\[motion\] files_(pane_close|folder) ' | xargs)"
  assert_eq "motion: …then files_folder's entrance (the two lines, in that order)" "[motion] files_pane_close [motion] files_folder" "$ORDER"
  folder_motion "the entrance after the pane's close (This Device)" "$MARK"
  # Selection: entering, leaving.
  files_at "$QF"; sleep 0.5; D 127-qa
  MARK="$(ring_mark)"; T 127-qa files_bar:select 1.2
  motion "entering selection" files_select "$MARK"
  assert_near "motion: files_select settles in 200 ± 40 ms" 200 "$(mf settle)" 40
  gap_ok "files_select"
  MARK="$(ring_mark)"; back 1.2
  motion "leaving selection (stand-in)" files_deselect "$MARK"
  assert_near "motion: files_deselect settles in 200 ± 40 ms" 200 "$(mf settle)" 40
  gap_ok "files_deselect"
  # The hold menu: its first frame after the press, and its settle after that.
  D 128-qa
  MARK="$(ring_mark)"; hold 128-qa files_row:a.txt 1300; sleep 0.5
  motion "the hold menu" files_hold "$MARK"
  assert_near "motion: files_hold — the menu's first frame 700 ± 33 ms after the press (first=)" 700 "$(mf first)" 33
  AFTER="$(python3 -c "import sys; print(int(sys.argv[1]) - int(sys.argv[2]))" "$(mf settle)" "$(mf first)" 2>/dev/null)"
  record "motion: files_hold — settled, ms after its first frame" "$AFTER"
  assert_ge "motion: files_hold — settled 233–367 ms after that (the lower bound)" 233 "$AFTER"
  assert_le "motion: files_hold — settled 233–367 ms after that (the upper bound)" 367 "$AFTER"
  gap_ok "files_hold"
  back 1
  # ••• expand.
  D 129-qa
  MARK="$(ring_mark)"; T 129-qa files_bar:more 1.5
  motion "••• expands (stand-in)" files_more "$MARK"
  assert_near "motion: files_more settles in 317 ± 33 ms" 317 "$(mf settle)" 33
  gap_ok "files_more"
  back 1
fi

# ------------------------------------------------------------------------------------------------- the screenrecords
# take <name> <seconds> <action…>: a recording of <seconds>, the action run 1.2 s into it; pulled to <name>.mp4.
take() {
  local name="$1" secs="$2"; shift 2
  adb shell rm -f /sdcard/Download/p18-e11.mp4
  adb shell screenrecord --bit-rate 20000000 --time-limit "$secs" /sdcard/Download/p18-e11.mp4 > "$ROW_DIR/$name.rec.out" 2>&1 &
  local rec=$!
  sleep 1.2
  "$@"
  wait "$rec"
  adb pull /sdcard/Download/p18-e11.mp4 "$ROW_DIR/$name.mp4" > "$ROW_DIR/$name.pull.out" 2>&1
  adb shell rm -f /sdcard/Download/p18-e11.mp4
}
kv() { sed -n "s/^$2=//p" "$ROW_DIR/$1" | head -1; }
if leg record; then
  PXB() { python3 -c "import sys; print(','.join(sys.argv[1:]))" $(B "$1" "$2"); }
  # (a) a folder change by a row tap: QA-Files/sub → deep (type icons only: no thumbnail loads into the entrance).
  files_at "$DEEP"; sleep 1; D 130-deep-rest; S 130-deep-rest
  set -- $(B 130-deep-rest files_appbar); BARBOX="$1,$2,$3,$4"
  set -- $(B 130-deep-rest files_icon:deeper); LISTBOX="60,$(( $2 - 36 )),1000,$(python3 -c "print(int($AT * 3))")"
  OK=""
  for k in 1 2 3 4; do
    files_at "$QF/sub"; sleep 1; D 131-sub
    take "132-folder-$k" 5 tap_node "$ROW_DIR/131-sub.xml" files_row:deep
    sleep 1
    python3 "$HERE/e11_frames.py" folder "$ROW_DIR/132-folder-$k.mp4" "$ROW_DIR/132-folder-$k" "list=$LISTBOX" "name=$(PXB 130-deep-rest files_row:deeper)" "detail=$(PXB 130-deep-rest files_detail:deeper)" "icon=$(PXB 130-deep-rest files_icon:deeper)" "bar=$BARBOX" "menu=$(PXB 130-deep-rest files_menu)" > "$ROW_DIR/132-folder-$k.txt" 2> "$ROW_DIR/132-folder-$k.err"
    note "record: folder take $k: $(tr '\n' ' ' < "$ROW_DIR/132-folder-$k.txt" | cut -c1-600)"
    if [ "$(kv "132-folder-$k.txt" spacing_ok)" = yes ]; then OK="132-folder-$k.txt"; break; fi
  done
  record "record: folder change — the take that met the frame-spacing rule (of up to 4)" "${OK:-none}"
  assert_ne "record: folder change — a take with source frames ≤ 18.2 ms apart during the entrance" "" "$OK"
  F="${OK:-132-folder-4.txt}"
  record "record: folder change — entrance frames / longest gap (ms) / its length on the recording (ms) / the empty list lasted (ms)" "$(kv "$F" motion_frames) / $(kv "$F" max_gap_ms) / $(kv "$F" entrance_ms) / $(kv "$F" load_gap_ms)"
  assert_ne "record: folder change — a frame shows an EMPTY list after frames with rows" "none" "$(kv "$F" empty_frame)"
  assert_eq "record: folder change — …with the bars unmoved (the app bar's glyphs and the ≡ in the same boxes ± 1 px as before the tap, the bar's fill in place)" "same" "$(kv "$F" bars_at_empty)"
  assert_eq "record: folder change — …and unmoved in every frame of the entrance" "0" "$(kv "$F" bars_moved_frames_after)"
  # Not a movement, and not in the doc's text: the app bar's four buttons are drawn dim while the list is empty.
  record "record: folder change — the app bar's glyphs, brightest channel before the tap / in the empty frame; frames with dim glyphs" "$(kv "$F" bar_glyph_brightest_first) / $(kv "$F" bar_glyph_brightest_at_empty); $(kv "$F" bar_dim_frames)"
  record "record: folder change — the name's offsets below rest, frame by frame (epx)" "$(kv "$F" offsets_epx)"
  assert_ge "record: folder change — the first entrance frame is ≥ 7 epx below rest" 7 "$(kv "$F" first_offset_epx)"
  record "record: folder change — the first frame with a name / a detail line / an icon (ms after the first name)" "0 / $(kv "$F" detail_first_ms) / $(kv "$F" icon_first_ms)"
  assert_eq "record: folder change — names before detail lines before icons" "names<details<icons" "$(kv "$F" order)"
  # (b) the pane's close on a choice.
  set -- $(B 130-deep-rest files_icon:deeper); LISTBOX="216,$(( $2 - 36 )),1000,$(python3 -c "print(int($AT * 3))")"
  files_at "$QF"; sleep 1; D 133-qa; T 133-qa files_menu 1.5; D 134-pane
  set -- $(B 134-pane files_pane:device); PY=$(( ($2 + $4) / 2 ))
  take 135-paneclose 5 tap_node "$ROW_DIR/134-pane.xml" files_pane:device
  sleep 1
  python3 "$HERE/e11_frames.py" paneclose "$ROW_DIR/135-paneclose.mp4" "$ROW_DIR/135-paneclose" "probe=690,$PY" "edge=755,$PY" "list=$LISTBOX" > "$ROW_DIR/135-paneclose.txt" 2> "$ROW_DIR/135-paneclose.err"
  note "record: pane close: $(tr '\n' ' ' < "$ROW_DIR/135-paneclose.txt")"
  F=135-paneclose.txt
  assert_eq "record: pane close — the recording starts with the pane open" "yes" "$(kv "$F" pane_at_start)"
  assert_ne "record: pane close — a frame without the pane follows" "none" "$(kv "$F" gone_frame)"
  assert_eq "record: pane close — the frame before it still holds the WHOLE pane (a cut, no frame in between)" "yes" "$(kv "$F" frame_before_has_whole_pane)"
  assert_eq "record: pane close — the list is empty in that same frame (ink pixels in the list)" "0" "$(kv "$F" list_ink_in_gone_frame)"
  record "record: pane close — the rows enter, ms after the cut" "$(kv "$F" rows_enter_ms_after)"
  assert_ne "record: pane close — …and the rows enter afterwards" "none" "$(kv "$F" rows_enter_ms_after)"
  assert_eq "record: pane close — the pane does not come back" "no" "$(kv "$F" pane_back_later)"
  # (c) the pane's open: a reveal.
  OK=""
  for k in 1 2 3 4; do
    files_at "$QF"; sleep 1; D 136-qa
    take "137-paneopen-$k" 4 tap_node "$ROW_DIR/136-qa.xml" files_menu
    sleep 0.8; D 138-pane
    set -- $(B 138-pane files_pane:device); PY=$(( ($2 + $4) / 2 ))
    set -- $(B 138-pane files_pane); X1="$3"
    python3 "$HERE/e11_frames.py" paneopen "$ROW_DIR/137-paneopen-$k.mp4" "$ROW_DIR/137-paneopen-$k" "label=$(PXB 138-pane files_pane_label:recent)" "row=$PY" "x1=$(( X1 - 4 ))" > "$ROW_DIR/137-paneopen-$k.txt" 2> "$ROW_DIR/137-paneopen-$k.err"
    note "record: pane open take $k: $(tr '\n' ' ' < "$ROW_DIR/137-paneopen-$k.txt" | cut -c1-600)"
    T 138-pane files_pane_menu 1
    if [ "$(kv "137-paneopen-$k.txt" spacing_ok)" = yes ]; then OK="137-paneopen-$k.txt"; break; fi
  done
  record "record: pane open — the take that met the frame-spacing rule (of up to 4)" "${OK:-none}"
  assert_ne "record: pane open — a take with source frames ≤ 18.2 ms apart during the reveal" "" "$OK"
  F="${OK:-137-paneopen-4.txt}"
  record "record: pane open — the edge frame by frame (px) / the label's left in those frames (px) / its length on the recording (ms)" "$(kv "$F" edges_px) / $(kv "$F" label_lefts_px) / $(kv "$F" open_ms)"
  assert_eq "record: pane open — the pane's edge grows frame by frame" "yes" "$(kv "$F" edge_grows)"
  assert_ge "record: pane open — the label is seen in frames where the pane is not yet whole" 2 "$(kv "$F" label_seen_while_partial)"
  assert_eq "record: pane open — the label's x is unchanged between the first and the last frame (a reveal, ± 1 px)" "yes" "$(kv "$F" label_x_unchanged)"
fi

# ------------------------------------------------------------------------------------------------- restore
log "--- restore ($(date '+%H:%M:%S'))"
assert_eq "no AndroidRuntime line names the shell since the row began" "0" "$(crash_since "$LC0")"
c6
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 0.5
adb shell am force-stop app.tileshell; sleep 0.5
adb shell "run-as app.tileshell sh -c 'cat > files/files-recent.json'" < "$ROW_DIR/recent-store-at-begin.json"
adb shell am force-stop app.tileshell; sleep 0.5
assert_eq "restore: the Recent store holds its bytes from the start (RV12)" "$(cat "$ROW_DIR/recent-store-at-begin.json")" "$(recent_store)"
ensure_start
pubvol_down
files_down
end_state
row_end
