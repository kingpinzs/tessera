#!/usr/bin/env bash
# E10 probe: phase 02's edit mode alone — do the dump's bounds change with no input, and how long does Back take?
export ANDROID_SERIAL=emulator-5554
QA="$(cd "$(dirname "$0")/../../.." && pwd)"
. "$QA/phase-01/scripts/ui.sh"
. "$QA/phase-02/scripts/gestures.sh" 2>/dev/null
. "$QA/phase-03/scripts/lib.sh"
OUT="$(cd "$(dirname "$0")" && pwd)"
ids() { python3 -c '
import re,sys
x=open(sys.argv[1]).read()
print("\n".join(sorted(f"{a} {b}" for a,b in re.findall(r"resource-id=\"((?:tile|dim|edit_disc)[^\"]*)\"[^>]*bounds=\"([^\"]*)\"", x))))' "$1"; }
ensure_start
dump_ui "$OUT/a0.xml"
XY="$(center "$OUT/a0.xml" tile:slot:PEOPLE)"
enter_edit ${XY% *} ${XY#* }
dump_ui "$OUT/a1.xml"; sleep 2; dump_ui "$OUT/a2.xml"; sleep 2; dump_ui "$OUT/a3.xml"
echo "== a1 vs a2 (no input, 2 s apart)"; diff <(ids "$OUT/a1.xml") <(ids "$OUT/a2.xml")
echo "== a2 vs a3"; diff <(ids "$OUT/a2.xml") <(ids "$OUT/a3.xml")
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_BACK
for t in 1 2 3; do sleep 1; dump_ui "$OUT/b$t.xml"; echo "after Back +${t}s: edit discs $(grep -c 'resource-id="edit_disc:' "$OUT/b$t.xml")"; done
ring_since "$MARK" | grep -E "\[edit\]|\[start\]|\[quick\]|\[motion\]" | cut -c1-170
