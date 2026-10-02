#!/usr/bin/env bash
# The lead's own rows on the fix build, one after the other, each waiting its turn for the device (run-locked.sh).
# Starts once TRUST run 4 has ended. Outputs and exit codes: dev-lead/fix-<row>.out / .rc.
#   lead-rows.sh [driver ...]     default: e15_apk t2_smoke e2 e26 e1
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
S="$HERE/../scripts"
until [ -f "$HERE/trust-run4.rc" ]; do sleep 5; done
drivers=("$@"); [ ${#drivers[@]} -eq 0 ] && drivers=(e15_apk t2_smoke e2 e26 e1)
for d in "${drivers[@]}"; do
  bash "$HERE/run-locked.sh" "$HERE/fix-$d.out" "$HERE/fix-$d.rc" "$S/$d.sh"
  echo "$d rc=$(cat "$HERE/fix-$d.rc") $(tail -1 "$HERE/fix-$d.out")"
  adb -s emulator-5554 get-state > /dev/null 2>&1 || { echo "STOPPED after $d: emulator-5554 is not answering"; exit 4; }
done
