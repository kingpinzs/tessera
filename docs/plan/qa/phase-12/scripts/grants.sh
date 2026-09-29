#!/usr/bin/env bash
# grants.sh — phase 12 build task 4 (iv), r3 V10: the shell's persisted URI grants, for E11's snapshot-grant sub-row.
#
# Sourced by a row driver after lib.sh / p12.sh:
#     . "$(dirname "$0")/grants.sh"
#     before="$(persisted_grants)"; ...; after="$(persisted_grants)"
#
# persisted_grants — prints, one URI per line, every URI the system holds a PERSISTED READ grant for whose target
# package is app.tileshell (the shell's own takePersistableUriPermission grants: the Start background picked in the
# photo picker, the Custom snapshot's picture). Nothing else. Prints nothing when there are none.
#
# Source of truth: the persisted-grant table itself, /data/system/urigrants.xml, which UriGrantsManagerService writes
# (as Android binary XML) a few seconds after any grant is taken or released. Read as root through the emulator image's
# `su` (a userdebug AVD; QA only — never on the phone) and decoded with the platform's own `abx2xml`:
#     <uri-grant sourceUserId="0" targetUserId="0" sourcePkg="…" targetPkg="app.tileshell" uri="content://…"
#                prefix="false" modeFlags="1" createdTime="…" />
# modeFlags bit 1 = read. Why not `dumpsys activity permissions`: on API 36 it lists only the grants the activity manager
# holds in memory for live components (the downloads provider's, here), not the photo picker's persisted grants — found
# at phase 12's build (E11 run 1, where it printed nothing while the shell was drawing picture A through its grant).
#
# The write is deferred (UriGrantsManagerService.schedulePersistUriGrants, ~10 s), so the function first waits
# GRANTS_SETTLE seconds (default 12) after the change it follows.
#
# Verify by hand: take a grant through the picker (Settings > Start + theme > Choose a picture), wait 12 s, run
#     adb shell su 0 abx2xml /data/system/urigrants.xml -
# and find the picker URI with targetPkg="app.tileshell"; then Remove picture / replace the snapshot and see it go.

persisted_grants() {
  sleep "${GRANTS_SETTLE:-12}"
  adb shell su 0 abx2xml /data/system/urigrants.xml - 2>/dev/null | python3 -c '
import sys, xml.etree.ElementTree as ET
pkg = sys.argv[1]
try:
    root = ET.fromstring(sys.stdin.read())
except Exception:
    sys.exit(0)
for g in root.iter("uri-grant"):
    if g.get("targetPkg") == pkg and int(g.get("modeFlags", "0")) & 1:
        print(g.get("uri"))
' "${PKG:-app.tileshell}"
}
