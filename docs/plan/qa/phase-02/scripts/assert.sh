#!/usr/bin/env bash
# Assertions with a tally and an exit code, so a row can actually fail (gate finding: several drivers logged
# state and never compared it, and nothing gated on an exit code).
QA_PASS=0
QA_FAIL=0

say() { echo "$*" | tee -a "${LOG:-/dev/null}"; }

# check <label> <expected> <actual>
check() {
  if [ "$2" = "$3" ]; then
    QA_PASS=$((QA_PASS + 1)); say "PASS  $1 (= $3)"
  else
    QA_FAIL=$((QA_FAIL + 1)); say "FAIL  $1 (expected [$2], got [$3])"
  fi
}

# check_contains <label> <needle> <haystack>
check_contains() {
  case "$3" in
    *"$2"*) QA_PASS=$((QA_PASS + 1)); say "PASS  $1 (found [$2])" ;;
    *)      QA_FAIL=$((QA_FAIL + 1)); say "FAIL  $1 (no [$2] in [$3])" ;;
  esac
}

# check_absent <label> <needle> <haystack>
check_absent() {
  case "$3" in
    *"$2"*) QA_FAIL=$((QA_FAIL + 1)); say "FAIL  $1 ([$2] is present in [$3])" ;;
    *)      QA_PASS=$((QA_PASS + 1)); say "PASS  $1 (no [$2])" ;;
  esac
}

# check_cmd <label> <command...>: passes when the command exits 0.
check_cmd() {
  local label=$1; shift
  if "$@" >>"${LOG:-/dev/null}" 2>&1; then
    QA_PASS=$((QA_PASS + 1)); say "PASS  $label"
  else
    QA_FAIL=$((QA_FAIL + 1)); say "FAIL  $label (exit $?: $*)"
  fi
}

# qa_finish: the row's verdict and its exit code.
qa_finish() {
  say "---- $QA_PASS passed, $QA_FAIL failed ----"
  [ "$QA_FAIL" -eq 0 ]
}

# build_guard: the row must run against the APK that is actually built here, and the log records which one
# (gate finding: three rows' logs were produced by drivers and builds that had since changed).
build_guard() {
  local apk=app/build/outputs/apk/debug/app-debug.apk
  local built installed
  built=$(md5sum "$apk" 2>/dev/null | cut -d' ' -f1)
  installed=$(adb shell md5sum "$(adb shell pm path app.tileshell | head -1 | sed 's/package://' | tr -d '\r')" 2>/dev/null | cut -d' ' -f1)
  say "build guard: built=$built installed=$installed"
  if [ -n "$built" ] && [ "$built" != "$installed" ]; then
    say "build guard: installing the freshly built APK"
    adb install -r "$apk" >/dev/null 2>&1
    adb shell am force-stop app.tileshell; sleep 2
  fi
  say "driver: $(basename "$0") blob $(git hash-object "$0")"
}
