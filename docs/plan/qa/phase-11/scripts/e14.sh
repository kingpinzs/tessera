#!/usr/bin/env bash
# E14 — coverage: every diagnostics line Decisions lists, every `no burst` reason and every `burst closed` reason
# appears at least once in the union of qa/phase-11/*/ring-*.txt saved by THIS build's run (rows whose log records
# the installed APK = the built one), never one final ring (C-20, T11-21). `query failed` alone cannot be provoked
# on the AVD and is proved by the shortcut source's JVM test, run and quoted here.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E14 "every [quick] line, reason and close appears in this build's saved rings; query failed by its JVM test"
BUILT="$(md5sum "$APK" | cut -c1-16)"
FILES=""
# Only the gate rows run_all names (the round-2 re-judge, EV-10: smoke directories whose logs showed the same APK were counted).
for r in E1 E2 E3 E4 E5 E6 E7 E8 E9 E10 E11 E12 E13 E15 E16 L11-1 EDGE DRAG; do
  d="$QA11/$r/"
  log="$d$r.txt"; [ -f "$log" ] || continue
  if grep -q "apk installed $BUILT" "$log"; then FILES="$FILES $(ls "$d"ring-*.txt 2>/dev/null | tr '\n' ' ')"; note "counted $r"; else note "skipped $r (another APK)"; fi
done
# shellcheck disable=SC2086
UNION="$(cat $FILES 2>/dev/null)"
note "union: $(echo "$UNION" | wc -l) lines from $(echo $FILES | wc -w) files"
check() { # name regex
  local n; n="$(echo "$UNION" | grep -cE "$2")"
  if [ "$n" -ge 1 ]; then _verdict PASS "$1" "$n line(s)"; else _verdict FAIL "$1" "no line matches /$2/"; fi
}
check "shortcuts for <pkg>/<activity>/<user>: n (k shown …)" '\[quick\] shortcuts for [^ /]+/[^ /]+/[0-9]+: [0-9]+ \([0-9]+ shown'
check "burst on …: k satellites"               '\[quick\] burst on .*: [1-4] satellites'
check "burst hidden: drag <id> (2026-09-25)"    '\[quick\] burst hidden: drag [^ ]+$'
check "burst back after the drop: <id>"         '\[quick\] burst back after the drop: [^ ]+$'
check "satellite i rest=[…]"                   '\[quick\] satellite [0-3] rest=\[-?[0-9]+,-?[0-9]+,-?[0-9]+,-?[0-9]+\]'
check "satellite i icon failed"                '\[quick\] satellite [0-3] icon failed [^ ]+: '
check "tap satellite … startShortcut ok"       '\[quick\] tap satellite [0-3] [^ ]+: startShortcut ok'
check "tap satellite … startShortcut failed"   '\[quick\] tap satellite [0-3] [^ ]+: startShortcut failed '
check "motion open (frames, maxGapMs)"         '\[quick\] motion open .*: t0=[0-9]+ peak=[0-9]+ overshoot=[-0-9.]+ settle=[0-9]+ frames=[0-9]+ maxGapMs=[0-9.]+'
check "motion close (frames, maxGapMs)"        '\[quick\] motion close .*: t0=[0-9]+ alpha0=[0-9]+ settle=[0-9]+ frames=[0-9]+ maxGapMs=[0-9.]+'
for r in "folder" "no app" "secondary tile" "off screen" "not the shortcut host" "profile quiet" "no shortcuts"; do
  check "no burst: $r" "\[quick\] no burst on .*: $r\$"
done
for r in "tap elsewhere" "drag" "back" "home" "stop" "unpin" "resize" "removed" "launch" "shortcuts changed"; do
  check "burst closed: $r" "\[quick\] burst closed: $r\$"
done
log "--- no burst: query failed — the JVM test (the one reason the AVD cannot provoke) ---"
( cd "$REPO" && ./gradlew :app:testDebugUnitTest --tests 'app.tileshell.start.QuickRulesTest' -q ) > "$ROW_DIR/jvm.out" 2>&1
rc=$?
X="$REPO/app/build/test-results/testDebugUnitTest/TEST-app.tileshell.start.QuickRulesTest.xml"
cp "$X" "$ROW_DIR/" 2>/dev/null
note "$(grep -oE '<testsuite [^>]*' "$X" | head -1)"
assert_eq "QuickRulesTest passes (gradle exit)" 0 "$rc"
assert_contains "the query-failed test ran and passed" 'testcase name="aThrowingQueryIsCaughtAndBecomesTheReason"' "$(cat "$X")"
assert_absent "…with no failure recorded against it" '<failure' "$(grep -A2 'aThrowingQueryIsCaughtAndBecomesTheReason' "$X")"
row_end
