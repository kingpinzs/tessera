#!/usr/bin/env bash
# Re-runs a dev-people row on the build now in app/build: the row's last run is kept beside it under the APK id it ran
# on (earlier runs are never deleted), then the row is run through run.sh (which waits for the device lock).
here="$(cd "$(dirname "$0")" && pwd)"; qa="$(cd "$here/.." && pwd)"
script="$1"; row="$2"
if [ -d "$qa/$row" ]; then
  id="$(sed -n 's/^apk installed *//p' "$qa/$row/$row.txt" | head -1 | cut -c1-8)"
  n=1; while [ -e "$qa/$row-build-${id:-unknown}-$n" ]; do n=$((n + 1)); done
  mv "$qa/$row" "$qa/$row-build-${id:-unknown}-$n"
fi
"$here/run.sh" "$script"
