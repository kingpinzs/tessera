#!/usr/bin/env bash
# Phase 17 E17 — budget and surface. Every clause reads a build output; the device is used only to say which APK it holds.
#
#   size       stat -c%s of app-debug.apk ≤ 629,145,600 bytes (600 MB, phase 03 Decisions).
#   opencv     unzip -l app-debug.apk 'lib/arm64-v8a/libopencv*': ≥ 1 entry, their sum ≤ 20,971,520 bytes (20 MB, T17-2),
#              and no lib/x86_64/libopencv* entry (r3 V19: an APK without OpenCV cannot pass the bound; BS-1: one JNI
#              library, arm64 only). The listing is proven to read that folder by the other libraries it shows there.
#   delta      the whole-phase delta against qa/phase-17/upgrade/phase-16-585b457f.apk — RECORDED (the row says so).
#   exported   phase 03 E5's method (exported.py over the APK's own merged manifest): the exported components equal
#              qa/phase-03/exported-allowlist.txt exactly; the list carries this phase's six ADDs; the device holds the
#              APK that was checked (md5), which is what makes the APK's surface the device's.
#   perms      aapt2 dump permissions lists android.permission.SET_WALLPAPER and no
#              android.permission.FOREGROUND_SERVICE_CAMERA (T17-18, T17-19).
#   network    ./gradlew :app:assembleRelease (run before the row takes the device), then aapt2 dump xmltree of the
#              RELEASE APK's compiled network security config: no 10.0.2.2, and every FixedEndpoints host (read from
#              net/FixedEndpoints.kt) under a domain-config with cleartextTrafficPermitted=false and under none that
#              permits it; the same dump of the DEBUG APK holds the 10.0.2.2 exception (the control: the right file is read).
#              THE DOC'S COMMAND, `--file res/xml/network_security_config.xml`, DOES NOT FIND THE FILE IN A RELEASE APK:
#              the release build shortens resource paths (the file is res/<two letters>.xml there; aapt2 answers
#              "failed to find file", run 1). So the file is found the way Android finds it: the manifest's
#              android:networkSecurityConfig names a resource id, `aapt2 dump resources` gives that id's name — asserted
#              to be xml/network_security_config — and its path in the APK; that path is dumped. Both APKs go through
#              the same lookup; the doc's literal path is RECORDED for each.
#   no token   app/build.gradle.kts: grep -c 'tmdb\.' = 0 and it holds `buildConfig = true`; every generated BuildConfig
#              (app/build/generated/source/buildConfig/, debug and release) holds DEBUG and no TMDB field; the debug and
#              the release APK are unzipped and leak_scan.sh over both finds none of the tmdb.* values local.properties
#              still holds, nor the QA dummy token. Control: the same scan finds a string the APK is known to hold.
#
# The Gradle flags are --offline unless E17_GRADLE_FLAGS says otherwise (the doc's command has none; this machine builds
# offline). E17_SKIP_RELEASE_BUILD=1 reuses the release APK already built (driver development; the gate never sets it).
# Changes on the device: none beyond installing this build when it holds another. No wipe, no force-stop.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
QAR="$(cd "$QA/.." && pwd)"
OUT="$QA/E17"; mkdir -p "$OUT"
AAPT2="$HOME/Android/Sdk/build-tools/36.0.0/aapt2"
OLD_APK="$QA/upgrade/phase-16-585b457f.apk"
ALLOW="$QAR/phase-03/exported-allowlist.txt"
GRADLE_KTS="$REPO/app/build.gradle.kts"
ENDPOINTS_KT="$REPO/app/src/main/kotlin/app/tileshell/net/FixedEndpoints.kt"
NSC="res/xml/network_security_config.xml"
MAX_APK=629145600
MAX_OPENCV=20971520
le() { [ "$1" -le "$2" ] && echo yes || echo "no ($1 > $2)"; }

# One line per <domain> of a compiled network security config: "<cleartextTrafficPermitted of its domain-config>\t<host>".
nsc_domains() { # xmltree.txt
  python3 - "$1" <<'PY'
import re, sys
permitted = None; in_config = -1
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    indent = len(line) - len(line.lstrip(" "))
    text = line.strip()
    if text.startswith("E: "):
        name = text[3:].split(" ", 1)[0]
        if name == "domain-config": in_config = indent; permitted = "unset"
        elif indent <= in_config: in_config = -1; permitted = None
    elif text.startswith("A: ") and in_config >= 0 and indent == in_config + 2:
        m = re.search(r"cleartextTrafficPermitted(?:\(0x[0-9a-f]+\))?=(\S+)", text)
        if m: permitted = m.group(1)
    elif text.startswith("T: ") and in_config >= 0:
        print("%s\t%s" % (permitted, text[3:].strip().strip("'\"")))
PY
}

# The path, inside an APK, of the file its manifest names as android:networkSecurityConfig: "<resource name>\t<path>".
nsc_file() { # apk
  local id
  id="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$1" 2>/dev/null | sed -n 's/.*:networkSecurityConfig([^)]*)=@\(0x[0-9a-f]*\).*/\1/p' | head -1)"
  [ -n "$id" ] || return 1
  "$AAPT2" dump resources "$1" 2>/dev/null | awk -v id="$id" '
    $1 == "resource" && $2 == id { name = $3; want = 1; next }
    want && /\(file\)/ { for (i = 1; i <= NF; i++) if ($i == "(file)") { print name "\t" $(i + 1); exit } }
    want && $1 == "resource" { exit }'
}

# ---- the release build first, before the row holds the device (a session does not hold the lock while it builds)
if [ "${E17_SKIP_RELEASE_BUILD:-0}" = "1" ]; then
  echo "skipped (E17_SKIP_RELEASE_BUILD=1)" > "$OUT/assembleRelease.out"; echo skipped > "$OUT/assembleRelease.rc"
else
  # shellcheck disable=SC2086
  ( cd "$REPO" && ./gradlew :app:assembleRelease ${E17_GRADLE_FLAGS:---offline} > "$OUT/assembleRelease.out" 2>&1; echo $? > "$OUT/assembleRelease.rc" )
fi
REL_APK=""
for f in "$REPO/app/build/outputs/apk/release/app-release.apk" "$REPO/app/build/outputs/apk/release/app-release-unsigned.apk"; do
  [ -f "$f" ] && { REL_APK="$f"; break; }
done

take_device_lock
if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
  adb install -r "$APK" > "$OUT/install.out" 2>&1 || { echo "E17: adb install -r of $APK failed:" >&2; cat "$OUT/install.out" >&2; exit 4; }
fi
row_begin E17 "budget and surface: APK size, OpenCV, exported components, permissions, network config, no TMDB token"
record "this build (debug)" "$(md5sum "$APK" | cut -c1-16) $(stat -c%s "$APK") bytes ($(git -C "$REPO" rev-parse --short HEAD))"
record "the release APK" "${REL_APK:+$(md5sum "$REL_APK" | cut -c1-16) $(stat -c%s "$REL_APK") bytes, $(basename "$REL_APK")}"
record "the pre-17 APK" "$(md5sum "$OLD_APK" 2>/dev/null | cut -c1-16) $(stat -c%s "$OLD_APK" 2>/dev/null) bytes"

# ----------------------------------------------------------------------------------------------- size
log "--- the APK's size"
SIZE="$(stat -c%s "$APK")"
assert_eq "app-debug.apk is at most 629,145,600 bytes (600 MB): $SIZE" "yes" "$(le "$SIZE" "$MAX_APK")"

# ----------------------------------------------------------------------------------------------- OpenCV
log "--- the OpenCV library (T17-2; r3 V19; BS-1)"
unzip -l "$APK" 'lib/*' > "$OUT/lib-listing.txt" 2>&1
unzip -l "$APK" 'lib/arm64-v8a/libopencv*' > "$OUT/opencv-arm64.txt" 2>&1
unzip -l "$APK" 'lib/x86_64/libopencv*' > "$OUT/opencv-x86_64.txt" 2>&1
ARM_ALL="$(awk '$4 ~ /^lib\/arm64-v8a\/.*\.so$/' "$OUT/lib-listing.txt" | wc -l)"
assert_ne "the listing reads lib/arm64-v8a (it shows the other libraries there: $ARM_ALL)" "0" "$ARM_ALL"
CV_N="$(awk '$4 ~ /^lib\/arm64-v8a\/libopencv[^\/]*\.so$/' "$OUT/opencv-arm64.txt" | wc -l)"
CV_SUM="$(awk '$4 ~ /^lib\/arm64-v8a\/libopencv/ { s += $1 } END { print s + 0 }' "$OUT/opencv-arm64.txt")"
CV_X86="$(awk '$4 ~ /^lib\/x86_64\/libopencv/' "$OUT/opencv-x86_64.txt" | wc -l)"
note "lib/arm64-v8a/libopencv*: $(awk '$4 ~ /^lib\/arm64-v8a\/libopencv/ { printf "%s %s; ", $4, $1 }' "$OUT/opencv-arm64.txt")"
assert_eq "at least one lib/arm64-v8a/libopencv*.so entry (found $CV_N)" "yes" "$([ "$CV_N" -ge 1 ] && echo yes || echo "no ($CV_N)")"
assert_eq "the OpenCV entries sum to at most 20,971,520 bytes (20 MB): $CV_SUM" "yes" "$([ "$CV_N" -ge 1 ] && le "$CV_SUM" "$MAX_OPENCV" || echo "no (nothing to sum: an APK without OpenCV cannot pass the bound)")"
assert_eq "no libopencv* under lib/x86_64" "0" "$CV_X86"

# ----------------------------------------------------------------------------------------------- the whole-phase delta
log "--- the whole-phase delta against the pre-17 APK (recorded)"
assert_eq "the pre-17 APK is the kept one (md5 585b457ffc29878c)" "585b457ffc29878c" "$(md5sum "$OLD_APK" 2>/dev/null | cut -c1-16)"
OLD_SIZE="$(stat -c%s "$OLD_APK" 2>/dev/null || echo 0)"
record "whole-phase delta, file size: pre-17 / now / delta" "$OLD_SIZE / $SIZE / $(( SIZE - OLD_SIZE )) bytes"
OLD_TOTAL="$(unzip -l "$OLD_APK" 2>/dev/null | tail -1 | awk '{ print $1 }')"; NOW_TOTAL="$(unzip -l "$APK" | tail -1 | awk '{ print $1 }')"
record "whole-phase delta, the entries' own bytes: pre-17 / now / delta" "$OLD_TOTAL / $NOW_TOTAL / $(( NOW_TOTAL - ${OLD_TOTAL:-0} )) bytes"
record "of it, OpenCV (lib/arm64-v8a/libopencv*)" "$CV_SUM bytes in $CV_N file(s)"

# ----------------------------------------------------------------------------------------------- the exported surface
log "--- the exported surface against qa/phase-03/exported-allowlist.txt (phase 03 E5's method)"
python3 "$P03S/exported.py" "$APK" "$ALLOW" > "$OUT/exported.txt" 2>&1; echo $? > "$OUT/exported.rc"
cat "$OUT/exported.txt" >> "$LOG"
assert_eq "exported.py: the APK's exported components equal the allow-list exactly (rc)" "0" "$(cat "$OUT/exported.rc")"
assert_absent "… nothing exported that is not allowed" "EXPORTED BUT NOT ALLOWED" "$(cat "$OUT/exported.txt")"
assert_absent "… nothing on the list that is not exported" "ON THE LIST BUT NOT EXPORTED" "$(cat "$OUT/exported.txt")"
for c in app.tileshell.photos.PhotosActivity app.tileshell.photos.ViewerActivity app.tileshell.camera.CameraActivity \
         app.tileshell.camera.CaptureActivity app.tileshell.video.VideoActivity app.tileshell.video.PlayerActivity; do
  assert_eq "the allow-list carries this phase's ADD $c" "1" "$(grep -c "^$c	" "$ALLOW")"
done
assert_contains "the device holds the APK that was checked" "yes" "$(apk_matches)"

# ----------------------------------------------------------------------------------------------- permissions
log "--- aapt2 dump permissions (T17-18, T17-19)"
"$AAPT2" dump permissions "$APK" > "$OUT/permissions.txt" 2>&1; echo $? > "$OUT/permissions.rc"
assert_eq "aapt2 dump permissions rc" "0" "$(cat "$OUT/permissions.rc")"
assert_contains "the dump is this package's" "package: app.tileshell" "$(cat "$OUT/permissions.txt")"
assert_eq "it lists android.permission.SET_WALLPAPER" "1" "$(grep -cx "uses-permission: name='android.permission.SET_WALLPAPER'" "$OUT/permissions.txt")"
assert_absent "it lists no android.permission.FOREGROUND_SERVICE_CAMERA" "android.permission.FOREGROUND_SERVICE_CAMERA" "$(cat "$OUT/permissions.txt")"

# ----------------------------------------------------------------------------------------------- the network config
log "--- the network security config (C-16 (3)): the release APK, with the debug APK as the control"
assert_eq "./gradlew :app:assembleRelease rc" "0" "$(cat "$OUT/assembleRelease.rc")"
assert_ne "the release APK exists" "" "$REL_APK"
HOSTS="$(python3 -c '
import re, sys
urls = re.findall(r"const val \w+ = \"(https?://[^\"]+)\"", open(sys.argv[1]).read())
print(" ".join(u.split("://", 1)[1].split("/", 1)[0].split(":", 1)[0] for u in urls))' "$ENDPOINTS_KT")"
record "FixedEndpoints hosts (net/FixedEndpoints.kt)" "$HOSTS"
assert_eq "FixedEndpoints names at least the eight hosts of C-16 (1) and the redirect host" "yes" "$([ "$(echo $HOSTS | wc -w)" -ge 9 ] && echo yes || echo "no ($(echo $HOSTS | wc -w))")"
for variant in release debug; do
  [ "$variant" = release ] && A="$REL_APK" || A="$APK"
  : > "$OUT/nsc-$variant.txt"; echo "no $variant APK" > "$OUT/nsc-$variant.rc"
  [ -n "$A" ] || continue
  F="$(nsc_file "$A")"
  assert_eq "$variant: the manifest's android:networkSecurityConfig is the resource xml/network_security_config" "xml/network_security_config" "${F%%	*}"
  record "$variant: that resource's file in the APK" "${F##*	}"
  record "$variant: the doc's literal path, aapt2 dump xmltree --file $NSC" "$("$AAPT2" dump xmltree --file "$NSC" "$A" > /dev/null 2>"$OUT/nsc-$variant-literal.err"; echo "rc $? $(head -1 "$OUT/nsc-$variant-literal.err")")"
  [ -n "${F##*	}" ] || continue
  "$AAPT2" dump xmltree --file "${F##*	}" "$A" > "$OUT/nsc-$variant.txt" 2>&1; echo $? > "$OUT/nsc-$variant.rc"
done
assert_eq "aapt2 dump xmltree of the release APK's network security config rc" "0" "$(cat "$OUT/nsc-release.rc")"
assert_eq "aapt2 dump xmltree of the debug APK's network security config rc" "0" "$(cat "$OUT/nsc-debug.rc")"
assert_contains "the release dump is a network-security-config" "E: network-security-config" "$(cat "$OUT/nsc-release.txt")"
assert_absent "the release APK's config holds no 10.0.2.2" "10.0.2.2" "$(cat "$OUT/nsc-release.txt")"
nsc_domains "$OUT/nsc-release.txt" > "$OUT/nsc-release-domains.txt"; nsc_domains "$OUT/nsc-debug.txt" > "$OUT/nsc-debug-domains.txt"
cat "$OUT/nsc-release-domains.txt" >> "$LOG"
for h in $HOSTS; do
  assert_eq "release: $h is under cleartextTrafficPermitted=false" "1" "$(grep -cxF "false	$h" "$OUT/nsc-release-domains.txt")"
  assert_eq "release: $h is under no domain-config that permits cleartext" "0" "$(grep -cxF "true	$h" "$OUT/nsc-release-domains.txt")"
done
assert_eq "release: no domain-config permits cleartext to any host" "0" "$(grep -c '^true	' "$OUT/nsc-release-domains.txt")"
assert_contains "control: the debug APK's config holds the 10.0.2.2 exception" "true	10.0.2.2" "$(cat "$OUT/nsc-debug-domains.txt")"

# ----------------------------------------------------------------------------------------------- no TMDB token
log "--- no TMDB token in any build (Q-17-1 (a); r3 D14)"
assert_eq "app/build.gradle.kts reads no tmdb. property (grep -c 'tmdb\\.')" "0" "$(grep -c 'tmdb\.' "$GRADLE_KTS")"
assert_ne "app/build.gradle.kts holds buildConfig = true" "0" "$(grep -cE '^[[:space:]]*buildConfig = true' "$GRADLE_KTS")"
find "$REPO/app/build/generated/source/buildConfig" -name BuildConfig.java 2>/dev/null | sort > "$OUT/buildconfig-files.txt"
for variant in debug release; do
  BC="$(grep -m1 "/buildConfig/$variant/" "$OUT/buildconfig-files.txt")"
  assert_ne "the generated BuildConfig of the $variant build exists" "" "$BC"
  [ -n "$BC" ] || continue
  cp "$BC" "$OUT/BuildConfig-$variant.java"
  assert_ne "BuildConfig ($variant) holds DEBUG" "0" "$(grep -c 'boolean DEBUG' "$BC")"
  assert_eq "BuildConfig ($variant) holds no TMDB field" "0" "$(grep -ci 'tmdb' "$BC")"
done
record "tmdb.* values local.properties still holds (their count only)" "$(grep -c '^tmdb\.' "$REPO/local.properties" 2>/dev/null)"
UNZ="$OUT/unzipped"
mkdir -p "$UNZ/debug" "$UNZ/release"
unzip -q -o "$APK" -d "$UNZ/debug" > "$OUT/unzip-debug.out" 2>&1; echo $? > "$OUT/unzip-debug.rc"
if [ -n "$REL_APK" ]; then unzip -q -o "$REL_APK" -d "$UNZ/release" > "$OUT/unzip-release.out" 2>&1; echo $? > "$OUT/unzip-release.rc"; else echo "no release APK" > "$OUT/unzip-release.rc"; fi
assert_eq "the debug APK unzipped" "0" "$(cat "$OUT/unzip-debug.rc")"
assert_eq "the release APK unzipped" "0" "$(cat "$OUT/unzip-release.rc")"
for variant in debug release; do
  bash "$HERE/leak_scan.sh" --path "$UNZ/$variant" -- qa-dummy-token > "$OUT/leak-$variant.txt" 2>&1; echo $? > "$OUT/leak-$variant.rc"
  cat "$OUT/leak-$variant.txt" >> "$LOG"
  assert_eq "leak_scan.sh over the unzipped $variant APK: clean (rc 0; 2 would mean nothing to look for)" "0" "$(cat "$OUT/leak-$variant.rc")"
  assert_absent "… no MATCH line ($variant)" "MATCH" "$(cat "$OUT/leak-$variant.txt")"
  # The control: the same scan over the same folder finds a string the APK holds (a fixed host's name in its code).
  bash "$HERE/leak_scan.sh" --path "$UNZ/$variant" -- api.themoviedb.org > "$OUT/leak-control-$variant.txt" 2>&1; echo $? > "$OUT/leak-control-$variant.rc"
  assert_eq "control: the scan finds a string the $variant APK does hold (rc 1)" "1" "$(cat "$OUT/leak-control-$variant.rc")"
done
# The unzipped trees are two copies of the APKs' contents, not evidence: their listings are kept, the trees removed.
unzip -l "$APK" > "$OUT/listing-debug.txt" 2>&1
[ -n "$REL_APK" ] && unzip -l "$REL_APK" > "$OUT/listing-release.txt" 2>&1
case "$UNZ" in "$QA"/E17/unzipped) rm -rf "$UNZ" ;; esac
row_end
