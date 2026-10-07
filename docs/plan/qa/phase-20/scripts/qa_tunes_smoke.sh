#!/usr/bin/env bash
# Phase 20 — the QA Tunes stub's own smoke (build task 11): install it, open it each way the shell can, read its
# TileShellQa lines, uninstall it, go Home. Not row A6: no shell code is involved. usage: qa_tunes_smoke.sh <out dir>
set -uo pipefail
export ANDROID_SERIAL=emulator-5554 PATH="$HOME/Android/Sdk/platform-tools:$PATH"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; REPO="$(cd "$HERE/../../../../.." && pwd)"
OUT="${1:?out dir}"; mkdir -p "$OUT"
PKG=app.tileshell.testclient.qatunes; APK="$REPO/testapps/qa-tunes/build/outputs/apk/debug/qa-tunes-debug.apk"
{
  echo "start $(date)"; echo "apk $(stat -c%s "$APK") B sha256 $(sha256sum "$APK" | cut -c1-16)"
  echo "installed before: $(adb shell pm list packages $PKG | tr -d '\r')"
  adb install -r "$APK"; echo "install rc=$?"
  echo "label: $(adb shell dumpsys package $PKG | tr -d '\r' | grep -m1 -E 'versionName')  $(aapt2 dump badging "$APK" 2>/dev/null | grep -m1 application-label || "$HOME"/Android/Sdk/build-tools/*/aapt2 dump badging "$APK" 2>/dev/null | grep -m1 application-label)"
  echo "--- MAIN + APP_MUSIC resolves to:"; adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.APP_MUSIC | tr -d '\r' | grep -E "/|found"
  echo "--- VIEW https://qa-tunes.test/search?q=… scoped to the package resolves to: $(adb shell "cmd package resolve-activity --brief -a android.intent.action.VIEW -d 'https://qa-tunes.test/search?q=x' $PKG" | tr -d '\r' | tail -1)"
  adb logcat -c
  echo "--- 1. the search form, as the table sends it (VIEW, the URI, the package)"
  adb shell "am start -W -a android.intent.action.VIEW -d 'https://qa-tunes.test/search?q=QA+Song+A+QA+Artist' -p $PKG" | tr -d '\r' | grep -E "Status|Activity|Error"; command sleep 1
  echo "resumed: $(adb shell dumpsys activity activities | tr -d '\r' | grep -m1 -E 'topResumedActivity|mResumedActivity' | grep -o '[a-z.]*/[A-Za-z.]*')"
  echo "--- 2. Android's play-from-search (no data; the query and focus extras)"
  adb shell "am start -W -a android.media.action.MEDIA_PLAY_FROM_SEARCH -p $PKG --es query 'QA Song A QA Artist' --es android.intent.extra.focus 'vnd.android.cursor.item/*'" | tr -d '\r' | grep -E "Status|Error"; command sleep 1
  echo "--- 3. the plain open (MAIN + APP_MUSIC)"
  adb shell "am start -W -a android.intent.action.MAIN -c android.intent.category.APP_MUSIC -n $PKG/.MainActivity" | tr -d '\r' | grep -E "Status|Error"; command sleep 1
  echo "--- its TileShellQa lines"
  adb logcat -d -s TileShellQa:I | tr -d '\r' | grep "qa-tunes:"
  adb shell input keyevent KEYCODE_HOME; adb shell am force-stop $PKG
  adb uninstall $PKG; echo "uninstall rc=$?"
  echo "installed after: [$(adb shell pm list packages $PKG | tr -d '\r')]"
  command sleep 1
  echo "resumed after Home: $(adb shell dumpsys activity activities | tr -d '\r' | grep -m1 -E 'topResumedActivity|mResumedActivity' | grep -o '[a-z.]*/[A-Za-z.]*')"
  echo "end $(date)"
} > "$OUT/qa-tunes-smoke.txt" 2>&1
