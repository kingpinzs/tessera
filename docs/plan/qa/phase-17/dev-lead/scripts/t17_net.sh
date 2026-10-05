#!/usr/bin/env bash
# Phase 17 development proof, build task 17 (C-16): the process-start [net] lines on the installed debug build.
# Not the gate (E13 is). Installs the current debug APK over what the device holds; changes nothing else.
export ANDROID_SERIAL=emulator-5554
# lib.sh finds the repo four levels above its own folder; this folder is one deeper, so the APK is named here.
export TILESHELL_APK="$(cd "$(dirname "$0")/../../../../../.." && pwd)/app/build/outputs/apk/debug/app-debug.apk"
. "$(dirname "$0")/lib.sh"
take_device_lock
[ "$(apk_matches | cut -c1-3)" = "yes" ] || { adb install -r "$APK" >/dev/null || { echo "install failed" >&2; exit 4; }; }
row_begin T17NET "the fixed hosts read cleartext=false at process start"
assert_eq "wake" "Awake" "$(wake_device)"
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
ensure_start
sleep 2
SLICE="$(ring_since "$MARK" launcher)"
for h in api.open-meteo.com nominatim.openstreetmap.org api.themoviedb.org image.tmdb.org query.wikidata.org api.radio-browser.info musicbrainz.org coverartarchive.org archive.org; do
  assert_contains "net line $h" "[net] cleartext permitted for $h: false" "$SLICE"
done
assert_absent "no fixed host reads true" ": true" "$(echo "$SLICE" | grep '\[net\]')"
assert_eq "nine net lines" "9" "$(echo "$SLICE" | grep -c '\[net\] cleartext permitted for')"
record "uses-cleartext manifest attr" "$(adb shell dumpsys package app.tileshell | grep -c -i 'usesCleartextTraffic' | tr -d '\r')"
ring_save launcher
row_end
