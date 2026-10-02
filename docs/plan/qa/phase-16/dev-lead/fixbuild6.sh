#!/usr/bin/env bash
# The sixth fix build (People no longer crashes with neither Contacts permission, D-E13-1), over the fifth (2026-10-02), packaged AFRESH: the gate round 1 fixes (the ledger F39 to F44, the contact take-back, the Day view blocks) — over the third (D-E19-2, the plus glyph, the Agenda items; and D-E16-1, D-E9-1
# (33944662), D-E19-1 (dacca941). Run under run-locked.sh so no row is mid-run while the APK file and the installed
# app change: assemble, the exported-surface check, install over the build on the device, read back.
# Everything is written to qa/phase-16/fixbuild6/.
set -u
export ANDROID_SERIAL=emulator-5554
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
P16="$(cd "$HERE/.." && pwd)"; REPO="$(cd "$P16/../../../.." && pwd)"
OUT="$P16/fixbuild6"; mkdir -p "$OUT"
APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"
cd "$REPO" || exit 9
echo "before: APK file $(md5sum "$APK" | cut -c1-16) $(stat -c%s "$APK") bytes; HEAD $(git rev-parse --short HEAD)"
# Packaged afresh: an incremental debug package keeps dead space in the APK file (the second fix build's file was 6.05 MB
# over the before-APK with the same content — INDEX Change Log, Q-16-5). The old APK and the packager's incremental
# state go first, so the file holds its entries and little else.
rm -f "$APK"; rm -rf "$REPO/app/build/intermediates/incremental/packageDebug"
./gradlew :app:assembleDebug --console=plain > "$OUT/assemble.out" 2>&1; echo $? > "$OUT/assemble.rc"
echo "assemble rc=$(cat "$OUT/assemble.rc")"
[ "$(cat "$OUT/assemble.rc")" = 0 ] || { echo "assemble failed; nothing installed"; exit 1; }
{
  date '+%Y-%m-%d %H:%M:%S %Z'
  echo "commit $(git rev-parse --short HEAD) (the sixth fix build, packaged afresh)"
  echo "bytes $(stat -c%s "$APK")"
  echo "md5 $(md5sum "$APK" | cut -c1-16)"
  echo "delta over the pre-task-2 APK ($(sed -n 's/^bytes //p' "$P16/apk-size-before.txt")): $(( $(stat -c%s "$APK") - $(sed -n 's/^bytes //p' "$P16/apk-size-before.txt") ))"
} > "$OUT/apk.txt"
cat "$OUT/apk.txt"
echo "packaging overhead (file size - the entries' stored bytes): $(( $(stat -c%s "$APK") - $(unzip -lv "$APK" | tail -1 | awk '{print $2}') )) bytes" | tee -a "$OUT/apk.txt"
python3 "$P16/../phase-03/scripts/exported.py" "$APK" "$P16/../phase-03/exported-allowlist.txt" > "$OUT/exported.out" 2>&1; echo $? > "$OUT/exported.rc"
echo "exported rc=$(cat "$OUT/exported.rc"): $(tail -1 "$OUT/exported.out")"
[ "$(cat "$OUT/exported.rc")" = 0 ] || { echo "the exported surface changed; nothing installed"; exit 1; }
bash "$HERE/install-fixbuild.sh" > "$OUT/install.out" 2>&1; echo $? > "$OUT/install.rc"
cat "$OUT/install.out"
echo "install rc=$(cat "$OUT/install.rc")"
