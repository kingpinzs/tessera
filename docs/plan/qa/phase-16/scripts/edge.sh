#!/usr/bin/env bash
# Phase 16 EDGE — the phase doc's "Edge cases", every bullet (r3 V6; the form of phases 11, 14 and 15).
#
# scripts/edge_index.tsv maps EVERY bullet's clause to where it is run: a sub-step here (`edge.sh:<ID>`), another row,
# a phone row, or a JVM test. This driver (1) checks the index itself — every clause has an entry, every `edge.sh:<ID>`
# names a function that exists, every other row or JVM test it names exists — and (2) runs each sub-step, a bash
# function `edge_<ID>` from scripts/cal_lib.sh (C..) or scripts/people_lib.sh (P..), each from its own MARK with its
# own fixtures and its own restore. A sub-step that is named and not written FAILS the row.
#
#   EDGE_ONLY=C01,P03 bash edge.sh    runs only those sub-steps (development; the gate run never sets it)
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
[ -f "$HERE/cal_lib.sh" ] && . "$HERE/cal_lib.sh"
[ -f "$HERE/people_lib.sh" ] && . "$HERE/people_lib.sh"
INDEX="$HERE/edge_index.tsv"

row_begin EDGE "the Edge cases: every bullet, by the index"
TZ0="$(adb shell getprop persist.sys.timezone | tr -d '\r')"
T1224="$(adb shell settings get system time_12_24 | tr -d '\r')"
note "time zone before: $TZ0; time_12_24 before: $T1224"

# ---------------------------------------------------------------- the index itself
BULLETS="$(grep -v '^#' "$INDEX" | awk -F'\t' 'NF>=3 {print $1}' | sed 's/\..*//' | sort -u | tr '\n' ' ' | sed 's/ $//')"
assert_eq "the index covers the doc's 23 Edge-case bullets" \
  "B01 B02 B03 B04 B05 B06 B07 B08 B09 B10 B11 B12 B13 B14 B15 B16 B17 B18 B19 B20 B21 B22 B23" "$BULLETS"
DOC_BULLETS="$(awk '/^## Edge cases/{f=1; next} /^## /{f=0} f && /^- /{n++} END{print n}' "$REPO/docs/plan/phase-16-inbox-calendar-people.md")"
assert_eq "… and the doc's \"Edge cases\" has that many bullets" "23" "$DOC_BULLETS"
assert_eq "no index line is without a place it is run" "0" "$(grep -v '^#' "$INDEX" | awk -F'\t' 'NF>=1 && $3=="" {n++} END{print n+0}')"
# Every other row the index names has a driver; every JVM test it names exists.
for r in $(grep -v '^#' "$INDEX" | awk -F'\t' '{print $3}' | grep -oE '^E[0-9]+' | sort -u); do
  n="$(echo "$r" | tr 'E' 'e')"
  assert_eq "the index names row $r: its driver exists" "yes" "$([ -f "$HERE/$n.sh" ] && echo yes || echo no)"
done
for t in $(grep -v '^#' "$INDEX" | awk -F'\t' '{print $3}' | grep -oE '^JVM: [A-Za-z]+Test' | sed 's/JVM: //' | sort -u); do
  assert_ne "the index names the JVM test $t: it exists" "" "$(find "$REPO/app/src/test" -name "$t.kt" | head -1)"
done

# ---------------------------------------------------------------- the sub-steps
ALL="$(grep -v '^#' "$INDEX" | awk -F'\t' '{print $3}' | grep -oE 'edge\.sh:[A-Z][0-9]+' | sed 's/edge.sh://' | sort -u)"
if [ -n "${EDGE_ONLY:-}" ]; then
  RUN="$(echo "$EDGE_ONLY" | tr ',' '\n')"
  record "sub-steps" "EDGE_ONLY=$EDGE_ONLY: a development run, not the gate's"
else
  RUN="$ALL"
fi
: > "$ROW_DIR/substeps.txt"
for id in $RUN; do
  if ! declare -F "edge_$id" >/dev/null; then
    _verdict FAIL "sub-step $id" "the index names edge.sh:$id and no function edge_$id is written"
    continue
  fi
  log "--- $id: $(grep -v '^#' "$INDEX" | awk -F'\t' -v w="edge.sh:$id" '$3==w {printf "%s%s", s, $1; s=", "}')"
  before_fail=$FAIL
  ring_save
  "edge_$id"
  ring_save
  echo "$id $(( FAIL - before_fail )) failed" >> "$ROW_DIR/substeps.txt"
done
if [ -z "${EDGE_ONLY:-}" ]; then
  assert_eq "every sub-step the index names ran" "$(echo $ALL)" "$(awk '{print $1}' "$ROW_DIR/substeps.txt" | tr '\n' ' ' | sed 's/ $//')"
fi

# ---------------------------------------------------------------- restore (each sub-step restores its own; this is the floor)
adb shell pm enable com.android.providers.calendar >/dev/null 2>&1
assert_absent "restore: the calendar provider is enabled" "com.android.providers.calendar" "$(adb shell pm list packages -d | tr -d '\r')"
adb shell pm enable com.android.calendar >/dev/null 2>&1
for p in READ_CALENDAR WRITE_CALENDAR READ_CONTACTS WRITE_CONTACTS; do
  adb shell pm grant app.tileshell "android.permission.$p" >/dev/null 2>&1
  assert_eq "restore: $p held" "true" "$(perm_granted "$p")"
done
cal_fixtures_down
[ -f "$ROW_DIR/people-fixtures.ids" ] && people_fixtures_down
adb shell cmd alarm set-timezone "$TZ0" >/dev/null 2>&1
assert_eq "restore: the time zone" "$TZ0" "$(adb shell getprop persist.sys.timezone | tr -d '\r')"
if [ "$T1224" = "null" ]; then adb shell settings delete system time_12_24 >/dev/null 2>&1; else adb shell settings put system time_12_24 "$T1224" >/dev/null 2>&1; fi
assert_eq "restore: time_12_24" "$T1224" "$(adb shell settings get system time_12_24 | tr -d '\r')"
clock_restore
ensure_start
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline" "0" "$?"
assert_eq "restore: user 0 only" "1" "$(adb shell pm list users | grep -c 'UserInfo{')"
row_end
