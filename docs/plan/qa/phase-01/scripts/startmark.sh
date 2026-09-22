#!/usr/bin/env bash
# The Start key's mark (Jeremy 2026-09-22: "go with logo B"; R10 §1). The Windows logo was the one
# Microsoft mark the shell drew; it is replaced by three tiles and one empty place. Measured off a real
# screenshot of the drawn nav bar, not read from the code: the top-right quadrant of the ink must be
# dark and the other three lit, at the same 20-epx size the old glyph had.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"

row_begin STARTMARK "the Start key draws three tiles and an empty place, not the Windows logo"

adb shell am force-stop $PKG; command sleep 1
adb shell input keyevent KEYCODE_HOME; command sleep 5
dump "$ROW_DIR/start.xml"
adb exec-out screencap -p > "$ROW_DIR/start.png"
read -r X1 Y1 X2 Y2 <<< "$(bounds "$ROW_DIR/start.xml" nav_windows)"
note "the Start key's slot: [$X1 $Y1 $X2 $Y2]"
assert_ne "the Start key is on the drawn nav bar" "" "$X1"
read -r MW MH TL TR BL BR <<< "$(python3 "$HERE/startmark_pixels.py" "$ROW_DIR/start.png" "$X1" "$Y1" "$X2" "$Y2")"
note "ink ${MW}x${MH}px; lit fraction per quadrant: top-left $TL, top-right $TR, bottom-left $BL, bottom-right $BR"
python3 - "$ROW_DIR/start.png" "$X1" "$Y1" "$X2" "$Y2" "$ROW_DIR/mark.png" <<'PY'
import sys
from PIL import Image
x1, y1, x2, y2 = map(int, sys.argv[2:6])
Image.open(sys.argv[1]).crop((x1, y1, x2, y2)).resize(((x2 - x1) * 3, (y2 - y1) * 3), Image.NEAREST).save(sys.argv[6])
PY
assert_within "the mark keeps the old glyph's 20-epx box (60 px wide here)" 60 "$MW" 3
assert_within "and its height" 60 "$MH" 3
assert_eq "the top-right place is EMPTY — which is what makes it not the four-pane logo" ok \
  "$(python3 -c "print('ok' if $TR < 0.05 else '$TR')")"
for q in "top-left:$TL" "bottom-left:$BL" "bottom-right:$BR"; do
  assert_eq "the ${q%%:*} tile is drawn" ok "$(python3 -c "print('ok' if ${q##*:} > 0.85 else '${q##*:}')")"
done
tap_id "$ROW_DIR/start.xml" nav_windows; command sleep 2
assert_contains "and the key still does its job: Start is in front" "StartActivity" "$(adb shell dumpsys activity activities | grep -m1 topResumedActivity)"

row_end
