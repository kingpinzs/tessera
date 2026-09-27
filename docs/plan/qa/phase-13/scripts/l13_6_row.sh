#!/usr/bin/env bash
# L13-6 regression row (INDEX ledger; review/2026-09-26-L13-6-fix-plan.md): a touch right after Back closed one of
# Music's menus reaches the page. Before the fix, the menu stayed hit-testable until it left the composition a frame
# later, and a touch in that frame was lost to its stale scrim (qa/phase-13/L13-6-repro-707aa55b/). The probe
# (scripts/l13_6_probe.sh) opens the menu, then sends Back and the same opening gesture together, 30 times per menu.
#   l13_6_row.sh [n]    (n trials per menu, default 30)
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
N="${1:-30}"
PROBE="$(dirname "$0")/l13_6_probe.sh"

row_begin L13_6 "a touch right after Back closes one of Music's menus reaches the page (hold menu and now playing's more, n=$N each)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
for m in hold more; do
  bash "$PROBE" setup $m > "$ROW_DIR/setup-$m.txt" 2>&1
  note "setup $m: $(tail -1 "$ROW_DIR/setup-$m.txt")"
  # A failed setup must not let the row read an earlier run's summary (review/2026-09-26-L13-6-fix-review-b.md N2).
  assert_contains "$m: setup found its target" "setup $m " "$(tail -1 "$ROW_DIR/setup-$m.txt")"
  [ -f "$ROW_DIR/$m/summary.txt" ] && mv "$ROW_DIR/$m/summary.txt" "$ROW_DIR/$m/summary-earlier-$(date +%H%M%S).txt"
  bash "$PROBE" $m "$ROW_DIR/$m" "$N" > "$ROW_DIR/$m.out" 2>&1
  R="$(tail -1 "$ROW_DIR/$m/summary.txt")"
  note "$m: $R"
  assert_eq "$m: every B sent with the Back opened the menu again" "trials $N: B opened the menu $N" "$R"
done
adb shell am force-stop $PKG; sleep 1
show_start 3
row_end
