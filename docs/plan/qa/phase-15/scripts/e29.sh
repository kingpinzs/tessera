#!/usr/bin/env bash
# E29 — Date calculation (T15-16; H19): the hamburger → calc_mode:date; every `date` line of calc-cases.tsv set through
# the page's radio buttons and pickers at dump bounds (calc_ui.py) and read back from calc_date_result — equal to the
# line's expectation (host-computed from microsoft/calculator's date engine by gen_calc_cases.py, never the app); the
# launcher ring holds `[calc] date <op> <inputs> -> <result>` per case; the same pair of dates with the zone switched
# to Asia/Tokyo gives the same difference; the zone is restored. Launcher ring.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
row_begin E29 "Date calculation: every oracle date line through the pickers; ring lines; zone switch"
UI="$HERE/calc_ui.py"
D="$ROW_DIR"
node_on() { # dump rid -> yes/no/absent (Compose: selected="true" on a Tab, checked="true" elsewhere)
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    if f'resource-id="{sys.argv[2]}"' in s:
        print("yes" if ('checked="true"' in s or 'selected="true"' in s) else "no"); break
else:
    print("absent")
PY
}
result_text() { # dump -> calc_date_result with its two lines joined " | " as the tsv writes them
  python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.get("resource-id") == "calc_date_result":
        print((n.get("text") or "").replace("\n", " | ")); break
PY
}

TZ0="$(adb shell getprop persist.sys.timezone | tr -d '\r')"
note "time zone before the row: $TZ0"

# ---- 1. every date line through the UI (the driver opens the app, the pane → Date calculation, then each case)
python3 "$UI" date "$D/results-date.tsv" > "$D/drive-date.log" 2>&1
echo $? > "$D/drive-date.rc"
assert_eq "date driver ran to the end" 0 "$(cat "$D/drive-date.rc")"
dump_ui "$D/date_page.xml"
assert_eq "calc_mode:date is the page showing" yes "$(node_on "$D/date_page.xml" calc_mode:date)"
n=0
while IFS=$'\t' read -r id expected got inputs ring shown; do
  n=$((n + 1))
  assert_eq "$id [$inputs] (fields: $shown)" "$expected" "$got"
  assert_contains "$id ring: [calc] date $inputs -> $expected" "[calc] date $inputs -> $expected" "$ring"
done < "$D/results-date.tsv"
want="$(awk -F'\t' '$2 == "date"' "$QROOT/phase-15/calc-cases.tsv" | wc -l)"
assert_eq "every date oracle line was driven" "$want" "$n"
assert_eq "at least 10 date lines (the row's floor)" yes "$([ "$n" -ge 10 ] && echo yes || echo no)"

# ---- 2. the same pair of dates with the zone switched gives the same difference (dates only)
dump_ui "$D/.op.xml"
tap_node "$D/.op.xml" calc_date_op:difference; sleep 0.6
dump_ui "$D/zone_before.xml"
from1="$(node_text "$D/zone_before.xml" calc_date_from)"; to1="$(node_text "$D/zone_before.xml" calc_date_to)"; res1="$(result_text "$D/zone_before.xml")"
note "difference before the zone switch: $from1 -> $to1 = $res1"
assert_ne "a difference is showing before the zone switch" "" "$res1"
adb shell cmd alarm set-timezone Asia/Tokyo
sleep 2
assert_eq "the device's zone is Asia/Tokyo" Asia/Tokyo "$(adb shell getprop persist.sys.timezone | tr -d '\r')"
dump_ui "$D/zone_after.xml"
assert_eq "From is unchanged under Asia/Tokyo" "$from1" "$(node_text "$D/zone_after.xml" calc_date_from)"
assert_eq "To is unchanged under Asia/Tokyo" "$to1" "$(node_text "$D/zone_after.xml" calc_date_to)"
assert_eq "the difference is the same under Asia/Tokyo" "$res1" "$(result_text "$D/zone_after.xml")"
adb shell cmd alarm set-timezone "$TZ0"
sleep 1
assert_eq "the zone is restored (RV12)" "$TZ0" "$(adb shell getprop persist.sys.timezone | tr -d '\r')"

# ---- 3. recorded: how a drag moves a picker column (the doc says "drag scrolls"; H19 approximation)
record "picker drag (3 rows) on the day column" "$(python3 "$UI" probe-drag 2>&1 | tail -1)"

# ---- restore: the Standard page, Home (the date page's fields are the app's own state)
python3 "$UI" goto standard > "$D/goto.log" 2>&1
adb shell input keyevent KEYCODE_HOME
row_end
