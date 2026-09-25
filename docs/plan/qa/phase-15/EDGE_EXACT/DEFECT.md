# EDGE_EXACT — product defects (build 4b7ac321's source, QA build without USE_EXACT_ALARM; emulator-5558, 2026-09-24)

Row log: `EDGE_EXACT.txt` (25 passed, 6 failed). Run 1 (`EDGE_EXACT-run1/`) failed the same five clauses; this run adds
the crash-count assertion and keeps the crash entries. The driver is right in both: the red row, the inexact arm and
the window are all read and pass; what fails is the product.

The QA build: `git archive` of HEAD f186583f built in the scratchpad (`edgeC-build.sh`), the one line
`<uses-permission android:name="android.permission.USE_EXACT_ALARM" />` removed from `app/src/main/AndroidManifest.xml`,
md5 c4b231592b5139f3, same debug signer; `adb install -r`, then `appops set app.tileshell SCHEDULE_EXACT_ALARM deny`.

## 1. An alarm armed inexactly never rings: the ring service crashes the shell at the fire (silent alarm)

Repro: the QA build installed; an alarm set (here through Tess, "set an alarm for 10:38 pm"); wait for it in real time.

- It arms inexactly, as the Decisions say: `[alarms] rearm (store change): 1 alarms, 0 timers, exact=false`
  (`ring_exact_launcher.txt`), and its dumpsys alarm entry (`dumpsys_alarm_entry.txt`) has no `exactAllowReason`,
  `origWhen=2026-09-24 22:38:00.000 window=+1m5s240ms`.
- AlarmManager delivers it at the window's end (22:39:05.24): the shell crashes 75 ms later, and again when the home app
  restarts and re-fires it (`dropbox_data_app_crash.txt`, `adb shell dumpsys dropbox --print data_app_crash`):

```
2026-09-24 22:39:05 data_app_crash   PID: 9826   Timestamp: 2026-09-24 22:39:05.315-0600
2026-09-24 22:39:05 data_app_crash   PID: 11124  Timestamp: 2026-09-24 22:39:05.886-0600
java.lang.RuntimeException: Unable to start service app.tileshell.clock.RingService@… with Intent { act=app.tileshell.clock.RING … }:
java.lang.SecurityException: Starting FGS with type systemExempted callerApp=ProcessRecord{…:app.tileshell/u0a150} targetSDK=36
requires permissions: all of the permissions allOf=true [android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED]
any of the permissions allOf=false [android.permission.SCHEDULE_EXACT_ALARM, android.permission.USE_EXACT_ALARM, android:activate_vpn]
```

- No `[alarms] fired` line survives (the process died with its ring), no ALARM player ever starts, no toast
  (`exact_ring.png`). Run 1 shows the same two crashes at 22:19:25 / 22:19:26
  (`EDGE_EXACT-run1/dropbox_data_app_crash_after_row.txt`, read by hand after that run).

Cause: `RingService.present()` calls `startForeground(…, FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)`
(`app/src/main/kotlin/app/tileshell/clock/RingService.kt`); Android allows that type only to a holder of
SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM. The Decisions' own fallback case — the grant revoked, alarms armed with
`setAndAllowWhileIdle` — is exactly the case where that type is refused, so the fallback alarm is silent. The Decisions
named `mediaPlayback` as the other type ("else `mediaPlayback`, which the manifest holds") and "never a silent alarm".

Failing clauses: "the alarm rings", "… inside Android's inexact window", "… and sounds", "… with no crash of app.tileshell".

## 2. The exact-alarms notice is neither spoken nor shown for an alarm

Repro: the QA build installed; Tess, typed "set an alarm for 10:38 pm".

- `reply_since`: `Alarm set for 10:38 PM.` — the launcher ring's `[speech] speak[…] text="Alarm set for 10:38 PM."`.
- The session's texts (`tess_alarm_reply.xml`, `tess_alarm_reply.png`): `set an alarm for 10:38 pm | Alarm set for 10:38 PM. | Ask me anything`.
- The Alarm tab shows no notice either (RECORD: no text mentioning exact alarms or lateness).

The Decisions: alarms "fall back to `setAndAllowWhileIdle` with the same spoken and shown notice reminders give"; the edge
case: "the notice is shown and spoken". The notice exists only for reminders (`cortana/action/ActionLayer.kt:423-425`,
" Exact alarms are off, so it may be a few minutes late."); Tess's `alarm()` (`ActionLayer.kt`, "Alarm set for …")
never adds it, although `ReminderScheduler.exactAlarmsDenied` is true here (the re-arm line reads `exact=false`).

Failing clauses: "the notice is SPOKEN", "the notice is SHOWN".

## What passes (for the record)

The QA build has no USE_EXACT_ALARM (aapt2); Tess's checklist row `cortana_check:exact_alarms:missing`, glyph 232,17,35,
text "Off · Reminders will be a few minutes late"; the restart and the store change arm with `exact=false`; no Next alarm
clock; a non-zero window. Restore passed: the FINAL APK reinstalled (4b7ac321ce4d0ede), home activity set,
USE_EXACT_ALARM granted, `exact=true` again, the store empty. Note: the Setup checklist has no exact_alarms row — the
Decisions put it in Tess's checklist (T15-6); the brief's "Setup `exact_alarms` row" is read as that row.
