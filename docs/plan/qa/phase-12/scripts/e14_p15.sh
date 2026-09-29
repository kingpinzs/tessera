#!/usr/bin/env bash
# Phase 12 E14 for phase 15's two steps, run HERE at phase 12's build because phase 15 was built before the wizard
# existed (C-34; phase 15 E28 points here): setup:full_screen_alarms (USE_FULL_SCREEN_INTENT — its uid mode outranks the
# package mode on this image, so revoke and grant set both) and setup:overlay (SYSTEM_ALERT_WINDOW). Each is the e14.sh
# template, all three parts, into its own row directory.
HERE="$(cd "$(dirname "$0")" && pwd)"
rc=0
E14_ROW=E14_FULL_SCREEN_ALARMS E14_STEP=setup:full_screen_alarms \
  E14_REVOKE="adb shell appops set app.tileshell USE_FULL_SCREEN_INTENT ignore; adb shell appops set --uid app.tileshell USE_FULL_SCREEN_INTENT ignore" \
  E14_GRANT="adb shell appops set app.tileshell USE_FULL_SCREEN_INTENT allow; adb shell appops set --uid app.tileshell USE_FULL_SCREEN_INTENT allow" \
  E14_CHECK=checklist:full_screen_alarms:missing bash "$HERE/e14.sh" || rc=1
E14_ROW=E14_OVERLAY E14_STEP=setup:overlay \
  E14_REVOKE="adb shell appops set app.tileshell SYSTEM_ALERT_WINDOW ignore" \
  E14_GRANT="adb shell appops set app.tileshell SYSTEM_ALERT_WINDOW allow" \
  E14_CHECK=checklist:overlay:missing bash "$HERE/e14.sh" || rc=1
exit $rc
